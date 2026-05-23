/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr, DynamicValue, SchemaError}
import scala.util.control.NonFatal

/**
 * A pure, serializable migration that operates on `DynamicValue`. This is the
 * untyped core of the migration system, containing a sequence of
 * `MigrationAction`s that are applied in order.
 *
 * `DynamicMigration` is fully serializable because:
 *   - It contains no closures or functions
 *   - All transformations are represented as `DynamicSchemaExpr`
 *   - All paths are represented as data (`DynamicOptic`)
 *
 * This enables migrations to be:
 *   - Stored in registries
 *   - Transmitted over the network
 *   - Applied dynamically
 *   - Inspected and transformed
 *   - Used to generate SQL DDL, upgraders, etc.
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Apply this migration to a `DynamicValue`.
   *
   * @param value
   *   The input value to migrate
   * @return
   *   Either a `SchemaError` or the migrated value
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicMigration.execute(actions, value)

  /**
   * Compose this migration with another, applying this migration first, then
   * the other.
   *
   * @param that
   *   The migration to apply after this one
   * @return
   *   A new migration that applies both in sequence
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /**
   * Alias for `++`.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the structural reverse of this migration. The reverse migration has
   * all actions reversed and in reverse order.
   *
   * Note: Runtime execution of the reverse migration is best-effort. It may
   * fail if information was lost during the forward migration (e.g., dropping a
   * field without capturing its value).
   */
  def reverse: DynamicMigration =
    new DynamicMigration(Chunk.fromIterator(actions.reverseIterator.map(_.reverse)))

  /**
   * Returns true if this migration has no actions (identity migration).
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Returns the number of actions in this migration.
   */
  def size: Int = actions.size
}

object DynamicMigration {

  /**
   * An empty migration that performs no transformations.
   */
  val empty: DynamicMigration = new DynamicMigration(Chunk.empty)

  /**
   * Create a migration from a single action.
   */
  def single(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Chunk(action))

  /**
   * Create a migration from multiple actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration =
    new DynamicMigration(Chunk.fromIterable(actions))

  /**
   * Execute a sequence of migration actions on a value. Actions are applied in
   * order, with the output of each action becoming the input to the next.
   */
  private[migration] def execute(
    actions: Chunk[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    var current: DynamicValue = value
    var idx                   = 0
    val len                   = actions.length

    while (idx < len) {
      val result =
        try ActionExecutor.execute(actions(idx), current)
        catch {
          case NonFatal(ex) =>
            Left(
              SchemaError.transformFailed(
                actions(idx).at,
                s"Unexpected error during migration: ${ex.getClass.getSimpleName}: ${ex.getMessage}"
              )
            )
        }
      result match {
        case Right(newValue) =>
          current = newValue
          idx += 1
        case left @ Left(_) =>
          return left
      }
    }

    Right(current)
  }
}

/**
 * Internal executor for migration actions. This executor uses DynamicSchemaExpr
 * directly for all expression evaluation.
 */
private[migration] object ActionExecutor {
  import MigrationAction._

  private val MaxPathDepth: Int = 64

  def execute(action: MigrationAction, value: DynamicValue): Either[SchemaError, DynamicValue] =
    action match {
      case a @ AddField(at, default) =>
        evalExpr(default, value, at).flatMap { defaultValue =>
          a.fieldName match {
            case Some(fieldName) => executeAddField(at, fieldName, defaultValue, value)
            case None            => Left(SchemaError.transformFailed(at, "AddField path must end with a Field node"))
          }
        }

      case a @ DropField(at, _) =>
        a.fieldName match {
          case Some(fieldName) => executeDropField(at, fieldName, value)
          case None            => Left(SchemaError.transformFailed(at, "DropField path must end with a Field node"))
        }

      case RenameField(at, to) =>
        executeRename(at, to, value)

      case TransformField(at, transform, to) =>
        executeTransformField(at, to.getOrElse(at), transform, value)

      case MandateField(at, default, to) =>
        evalExpr(default, value, at).flatMap { defaultValue =>
          executeMandate(at, to.getOrElse(at), defaultValue, value)
        }

      case OptionalizeField(at, to) =>
        executeOptionalize(at, to.getOrElse(at), value)

      case ChangeFieldType(at, converter, to) =>
        executeChangeFieldType(at, to.getOrElse(at), converter, value)

      case RenameCase(at, from, to) =>
        executeRenameCase(at, from, to, value)

      case TransformCase(at, targetCaseName, actions) =>
        executeTransformCase(at, targetCaseName, actions, value)

      case MigrateField(at, migration) =>
        executeApplyMigration(at, migration, value)

      case TransformElements(at, transform) =>
        executeTransformElements(at, transform, value)

      case TransformKeys(at, transform) =>
        executeTransformKeys(at, transform, value)

      case TransformValues(at, transform) =>
        executeTransformValues(at, transform, value)

      case i: Irreversible =>
        Left(SchemaError.transformFailed(i.at, s"Cannot execute reverse of irreversible action: ${i.originalAction}"))
    }

  /**
   * Evaluate a DynamicSchemaExpr and extract a single DynamicValue. For now, we
   * only support expressions that return a single value (first value in the
   * sequence).
   */
  private def evalExpr(
    expr: DynamicSchemaExpr,
    input: DynamicValue,
    at: DynamicOptic
  ): Either[SchemaError, DynamicValue] =
    expr.eval(input).flatMap { results =>
      results match {
        case Seq(value) => Right(value)
        case Seq()      =>
          Left(
            SchemaError.transformFailed(
              at,
              s"Expression evaluation returned no values"
            )
          )
        case _ =>
          Left(
            SchemaError.transformFailed(
              at,
              s"Expression evaluation must return exactly one value, got ${results.size}"
            )
          )
      }
    }

  // ==================== Record Action Execution ====================

  private def executeAddField(
    at: zio.blocks.schema.DynamicOptic,
    fieldName: String,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // The path includes the field name, so we navigate to the parent
    val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))
    modifyAt(parentPath, value) {
      case DynamicValue.Record(fields) =>
        if (fields.exists(_._1 == fieldName)) {
          Left(SchemaError.fieldAlreadyExists(at, fieldName))
        } else {
          Right(DynamicValue.Record(fields :+ (fieldName -> default)))
        }
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }
  }

  private def executeDropField(
    at: zio.blocks.schema.DynamicOptic,
    fieldName: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // The path includes the field name, so we navigate to the parent
    val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))
    modifyAt(parentPath, value) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == fieldName)
        if (idx < 0) {
          Left(SchemaError.fieldNotFound(at, fieldName))
        } else {
          Right(DynamicValue.Record(fields.patch(idx, Nil, 1)))
        }
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }
  }

  private def executeRename(
    at: zio.blocks.schema.DynamicOptic,
    to: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    // Extract from name and parent path
    val from = at.nodes.lastOption match {
      case Some(zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
      case _                                                     => return Left(SchemaError.transformFailed(at, "Rename path must end with a Field node"))
    }
    val parentPath = zio.blocks.schema.DynamicOptic(at.nodes.dropRight(1))

    modifyAt(parentPath, value) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == from)
        if (idx < 0) {
          Left(SchemaError.fieldNotFound(at, from))
        } else if (fields.exists(_._1 == to)) {
          Left(SchemaError.fieldAlreadyExists(at, to))
        } else {
          val (_, v) = fields(idx)
          Right(DynamicValue.Record(fields.updated(idx, (to, v))))
        }
      case other =>
        Left(SchemaError.typeMismatch(at, "Record", other.getClass.getSimpleName))
    }
  }

  private def executeTransformField(
    at: DynamicOptic,
    to: DynamicOptic,
    transform: DynamicSchemaExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    transformInto(at, to, value)(evalExpr(transform, _, at))

  private def executeChangeFieldType(
    at: DynamicOptic,
    to: DynamicOptic,
    converter: DynamicSchemaExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    transformInto(at, to, value)(evalExpr(converter, _, at))

  // ==================== Collection/Map Action Execution ====================

  private def executeTransformElements(
    at: DynamicOptic,
    transform: DynamicSchemaExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Sequence(elements) =>
        val transformed = elements.zipWithIndex.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
          case (Right(acc), (element, idx)) =>
            val elemPath = zio.blocks.schema.DynamicOptic(at.nodes :+ zio.blocks.schema.DynamicOptic.Node.AtIndex(idx))
            evalExpr(transform, element, elemPath).map(acc :+ _)
          case (left, _) =>
            left
        }
        transformed.map(DynamicValue.Sequence(_))
      case other =>
        Left(SchemaError.typeMismatch(at, "Sequence", other.getClass.getSimpleName))
    }

  private def executeTransformKeys(
    at: DynamicOptic,
    transform: DynamicSchemaExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Map(entries) =>
        val transformed =
          entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
            case (Right(acc), (key, currentValue)) =>
              val keyPath =
                zio.blocks.schema.DynamicOptic(at.nodes :+ zio.blocks.schema.DynamicOptic.Node.AtMapKey(key))
              evalExpr(transform, key, keyPath).map(newKey => acc :+ (newKey -> currentValue))
            case (left, _) =>
              left
          }
        transformed.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
    }

  private def executeTransformValues(
    at: DynamicOptic,
    transform: DynamicSchemaExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      case DynamicValue.Map(entries) =>
        val transformed =
          entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
            case (Right(acc), (key, currentValue)) =>
              val valuePath =
                zio.blocks.schema.DynamicOptic(at.nodes :+ zio.blocks.schema.DynamicOptic.Node.AtMapKey(key))
              evalExpr(transform, currentValue, valuePath).map(newValue => acc :+ (key -> newValue))
            case (left, _) =>
              left
          }
        transformed.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.typeMismatch(at, "Map", other.getClass.getSimpleName))
    }

  private def executeMandate(
    at: DynamicOptic,
    to: DynamicOptic,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    transformInto(at, to, value) {
      // Option is represented as a Variant with cases "None" and "Some"
      case DynamicValue.Variant("None", _) =>
        Right(default)
      case DynamicValue.Variant("Some", DynamicValue.Record(fields))
          if fields.length == 1 && fields.head._1 == "value" =>
        Right(fields.head._2)
      case DynamicValue.Variant("Some", inner) =>
        Right(inner)
      case other =>
        // If it's not an Option variant, assume it's already the value
        Right(other)
    }

  private def executeOptionalize(
    at: DynamicOptic,
    to: DynamicOptic,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    transformInto(at, to, value) { v =>
      // Wrap the value in Some
      Right(DynamicValue.Variant("Some", DynamicValue.Record("value" -> v)))
    }

  // ==================== Enum Action Execution ====================

  private def executeRenameCase(
    at: zio.blocks.schema.DynamicOptic,
    from: String,
    to: String,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    if (from.isEmpty || to.isEmpty)
      return Left(
        SchemaError.transformFailed(at, s"renameCase requires non-empty case names, got from='$from', to='$to'")
      )
    modifyAt(at, value) {
      case DynamicValue.Variant(caseName, inner) if caseName == from =>
        Right(DynamicValue.Variant(to, inner))
      case v @ DynamicValue.Variant(_, _) =>
        // Different case, no change needed
        Right(v)
      case other =>
        Left(SchemaError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
    }
  }

  private def executeTransformCase(
    at: zio.blocks.schema.DynamicOptic,
    targetCaseName: String,
    actions: Chunk[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    val sourceCaseName =
      at.nodes.lastOption.collect { case DynamicOptic.Node.Case(name) => name }.getOrElse(targetCaseName)
    val parentPath = at.nodes.lastOption match {
      case Some(_: DynamicOptic.Node.Case) => DynamicOptic(at.nodes.dropRight(1))
      case _                               => at
    }

    modifyAt(parentPath, value) {
      case DynamicValue.Variant(name, inner) if name == sourceCaseName =>
        DynamicMigration.execute(actions, inner).map(DynamicValue.Variant(targetCaseName, _))
      case v: DynamicValue.Variant =>
        Right(v) // non-matching case, pass through unchanged
      case other =>
        Left(SchemaError.typeMismatch(at, "Variant", other.getClass.getSimpleName))
    }
  }

  private def executeApplyMigration(
    at: zio.blocks.schema.DynamicOptic,
    migration: DynamicMigration,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { nestedValue =>
      migration(nestedValue)
    }

  // ==================== Helper Methods ====================

  /**
   * Navigate to a path and apply a modification function. If the path is empty
   * (root), apply directly to the value.
   */
  private def modifyAt(
    path: DynamicOptic,
    value: DynamicValue
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) {
      f(value)
    } else {
      modifyAtPath(nodes, 0, value, path)(f)
    }
  }

  private def transformInto(
    sourcePath: DynamicOptic,
    targetPath: DynamicOptic,
    value: DynamicValue
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] =
    if (sourcePath == targetPath) {
      modifyAt(sourcePath, value)(f)
    } else {
      for {
        sourceValue <- getAt(sourcePath, value)
        targetValue <- f(sourceValue)
        migrated    <- setAt(targetPath, targetValue, value)
      } yield migrated
    }

  private def getAt(path: DynamicOptic, value: DynamicValue): Either[SchemaError, DynamicValue] =
    getAtPath(path.nodes, 0, value, path)

  private def getAtPath(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: DynamicValue,
    fullPath: DynamicOptic
  ): Either[SchemaError, DynamicValue] = {
    import DynamicOptic.Node

    if (idx >= nodes.length) {
      Right(value)
    } else if (idx >= MaxPathDepth) {
      Left(SchemaError.transformFailed(fullPath, s"Maximum path depth ($MaxPathDepth) exceeded"))
    } else {
      nodes(idx) match {
        case Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              fields.find(_._1 == name) match {
                case Some((_, fieldValue)) => getAtPath(nodes, idx + 1, fieldValue, fullPath)
                case None                  => Left(SchemaError.fieldNotFound(fullPath, name))
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Record", other.getClass.getSimpleName))
          }

        case Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, inner) if name == caseName =>
              getAtPath(nodes, idx + 1, inner, fullPath)
            case DynamicValue.Variant(_, _) =>
              Left(SchemaError.caseNotFound(fullPath, caseName))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Variant", other.getClass.getSimpleName))
          }

        case Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results = elements.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
                case (Right(acc), elem) =>
                  getAtPath(nodes, idx + 1, elem, fullPath).map(acc :+ _)
                case (left, _) => left
              }
              results.map(DynamicValue.Sequence(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Sequence", other.getClass.getSimpleName))
          }

        case Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val keys = entries.map(_._1)
              Right(DynamicValue.Sequence(keys))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results = entries.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
                case (Right(acc), (_, v)) =>
                  getAtPath(nodes, idx + 1, v, fullPath).map(acc :+ _)
                case (left, _) => left
              }
              results.map(DynamicValue.Sequence(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) Left(SchemaError.pathNotFound(fullPath))
              else getAtPath(nodes, idx + 1, elements(index), fullPath)
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Sequence", other.getClass.getSimpleName))
          }

        case Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              entries.find(_._1 == key) match {
                case Some((_, mapValue)) => getAtPath(nodes, idx + 1, mapValue, fullPath)
                case None                => Left(SchemaError.pathNotFound(fullPath))
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.Wrapped =>
          getAtPath(nodes, idx + 1, value, fullPath)

        case _ =>
          Left(SchemaError.transformFailed(fullPath, s"Unsupported path node: ${nodes(idx)}"))
      }
    }
  }

  private def setAt(
    path: DynamicOptic,
    newValue: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    if (path.nodes.isEmpty) Right(newValue)
    else setAtPath(path.nodes, 0, value, path, newValue)

  private def setAtPath(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: DynamicValue,
    fullPath: DynamicOptic,
    newValue: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    import DynamicOptic.Node

    if (idx >= nodes.length) {
      Right(newValue)
    } else if (idx >= MaxPathDepth) {
      Left(SchemaError.transformFailed(fullPath, s"Maximum path depth ($MaxPathDepth) exceeded"))
    } else {
      nodes(idx) match {
        case Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (idx == nodes.length - 1) {
                if (fieldIdx < 0) Right(DynamicValue.Record(fields :+ (name -> newValue)))
                else Right(DynamicValue.Record(fields.updated(fieldIdx, (name, newValue))))
              } else if (fieldIdx < 0) {
                Left(SchemaError.fieldNotFound(fullPath, name))
              } else {
                val (fieldName, fieldValue) = fields(fieldIdx)
                setAtPath(nodes, idx + 1, fieldValue, fullPath, newValue).map { updated =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, updated)))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Record", other.getClass.getSimpleName))
          }

        case Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, inner) if name == caseName =>
              setAtPath(nodes, idx + 1, inner, fullPath, newValue).map(DynamicValue.Variant(name, _))
            case DynamicValue.Variant(_, _) =>
              Left(SchemaError.caseNotFound(fullPath, caseName))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Variant", other.getClass.getSimpleName))
          }

        case Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) {
                Left(SchemaError.pathNotFound(fullPath))
              } else {
                setAtPath(nodes, idx + 1, elements(index), fullPath, newValue).map { updated =>
                  DynamicValue.Sequence(elements.updated(index, updated))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Sequence", other.getClass.getSimpleName))
          }

        case Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val entryIdx = entries.indexWhere(_._1 == key)
              if (entryIdx < 0) {
                Left(SchemaError.pathNotFound(fullPath))
              } else {
                val (k, v) = entries(entryIdx)
                setAtPath(nodes, idx + 1, v, fullPath, newValue).map { updated =>
                  DynamicValue.Map(entries.updated(entryIdx, (k, updated)))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.Wrapped =>
          setAtPath(nodes, idx + 1, value, fullPath, newValue)

        case _ =>
          Left(SchemaError.transformFailed(fullPath, s"Unsupported target path node: ${nodes(idx)}"))
      }
    }
  }

  private def modifyAtPath(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: DynamicValue,
    fullPath: DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    import DynamicOptic.Node

    if (idx >= nodes.length) {
      f(value)
    } else if (idx >= MaxPathDepth) {
      Left(SchemaError.transformFailed(fullPath, s"Maximum path depth ($MaxPathDepth) exceeded"))
    } else {
      nodes(idx) match {
        case Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) {
                Left(SchemaError.fieldNotFound(fullPath, name))
              } else {
                val (fieldName, fieldValue) = fields(fieldIdx)
                modifyAtPath(nodes, idx + 1, fieldValue, fullPath)(f).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Record", other.getClass.getSimpleName))
          }

        case Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, inner) if name == caseName =>
              modifyAtPath(nodes, idx + 1, inner, fullPath)(f).map { newInner =>
                DynamicValue.Variant(name, newInner)
              }
            case DynamicValue.Variant(_, _) =>
              Left(SchemaError.caseNotFound(fullPath, caseName))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Variant", other.getClass.getSimpleName))
          }

        case Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results = elements.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
                case (Right(acc), elem) =>
                  modifyAtPath(nodes, idx + 1, elem, fullPath)(f).map(acc :+ _)
                case (left, _) =>
                  left
              }
              results.map(DynamicValue.Sequence(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Sequence", other.getClass.getSimpleName))
          }

        case Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, k, fullPath)(f).map(newK => acc :+ (newK -> v))
                  case (left, _) =>
                    left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, v, fullPath)(f).map(newV => acc :+ (k -> newV))
                  case (left, _) =>
                    left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) {
                Left(SchemaError.pathNotFound(fullPath))
              } else {
                modifyAtPath(nodes, idx + 1, elements(index), fullPath)(f).map { newElem =>
                  DynamicValue.Sequence(elements.updated(index, newElem))
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Sequence", other.getClass.getSimpleName))
          }

        case Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              if (key == null) {
                Left(
                  SchemaError.invalidValue(
                    fullPath,
                    "Expected DynamicValue map key, got: null"
                  )
                )
              } else {
                val keyValue = key
                val entryIdx = entries.indexWhere(_._1 == keyValue)
                if (entryIdx < 0) {
                  Left(SchemaError.pathNotFound(fullPath))
                } else {
                  val (k, v) = entries(entryIdx)
                  modifyAtPath(nodes, idx + 1, v, fullPath)(f).map { newV =>
                    DynamicValue.Map(entries.updated(entryIdx, (k, newV)))
                  }
                }
              }
            case other =>
              Left(SchemaError.typeMismatch(fullPath, "Map", other.getClass.getSimpleName))
          }

        case Node.Wrapped =>
          // For wrapper types, just continue to the inner value
          modifyAtPath(nodes, idx + 1, value, fullPath)(f)

        case Node.AtIndices(_) | Node.AtMapKeys(_) =>
          Left(
            SchemaError.transformFailed(
              fullPath,
              s"Batch path nodes (AtIndices/AtMapKeys) are not supported in migration paths"
            )
          )
        case Node.TypeSearch(_) | Node.SchemaSearch(_) =>
          Left(SchemaError.transformFailed(fullPath, s"Type/Schema search nodes are not supported in migration paths"))
      }
    }
  }
}
