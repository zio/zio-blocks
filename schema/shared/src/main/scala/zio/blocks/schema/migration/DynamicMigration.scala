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
import zio.blocks.schema.{DynamicValue, DynamicSchemaExpr, SchemaError}
import scala.util.control.NonFatal

/**
 * An untyped migration: a list of [[MigrationAction]]s applied in order to a
 * [[zio.blocks.schema.DynamicValue]]. [[Migration]] wraps one together with a
 * source and a target schema.
 *
 * The actions are data, with paths as [[zio.blocks.schema.DynamicOptic]]s and
 * values computed by [[zio.blocks.schema.DynamicSchemaExpr]]s, so a migration
 * can be inspected, compared, reversed and composed. ZIO Blocks provides no
 * `Schema` for `DynamicMigration`, though, and a literal expression holds the
 * `Schema` of its value, so a migration cannot be encoded with a codec the way
 * a `DynamicPatch` can (see `docs/dev/issues.md`).
 *
 * @param actions
 *   the actions, applied in order
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Applies the actions to `value` in order, each to the result of the previous
   * one, and stops at the first that fails. The errors are
   * [[zio.blocks.schema.SchemaError.MigrationError]]s at the action's path: a
   * path through a missing field or case fails with `FieldNotFound` or
   * `CaseNotFound`, an index or map key that is not there with `PathNotFound`,
   * a value of the wrong kind with `TypeMismatch`, and adding or renaming to a
   * field that exists with `FieldAlreadyExists`. An exception thrown while an
   * action runs becomes a `TransformFailed` error.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicMigration.execute(actions, value)

  /**
   * Returns a migration that applies the actions of this migration, then those
   * of `that`.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /** Same as `++`. */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the migration with every action reversed (see
   * [[MigrationAction#reverse]]), in reverse order. Running it fails at the
   * first action that has no inverse, and a dropped field comes back with its
   * `defaultForReverse`, not its old value. A reversed `MandateField` is an
   * `OptionalizeField`, which builds a `Some` that the schema of `Option`
   * cannot decode (see `docs/dev/issues.md`).
   */
  def reverse: DynamicMigration =
    new DynamicMigration(Chunk.fromIterator(actions.reverseIterator.map(_.reverse)))

  /**
   * Returns `true` when this migration has no actions; applying it returns the
   * value unchanged.
   */
  def isEmpty: Boolean = actions.isEmpty

  /** Returns the number of actions. */
  def size: Int = actions.size
}

/** Constructors of [[DynamicMigration]]. */
object DynamicMigration {

  /** The migration with no actions. */
  val empty: DynamicMigration = new DynamicMigration(Chunk.empty)

  /** Creates a migration with the one action `action`. */
  def single(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Chunk(action))

  /** Creates a migration that runs `actions` in order. */
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

      case TransformField(at, transform) =>
        executeTransformField(at, transform, value)

      case MandateField(at, default) =>
        evalExpr(default, value, at).flatMap { defaultValue =>
          executeMandate(at, defaultValue, value)
        }

      case OptionalizeField(at) =>
        executeOptionalize(at, value)

      case ChangeFieldType(at, converter) =>
        executeChangeFieldType(at, converter, value)

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
    at: zio.blocks.schema.DynamicOptic
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
    at: zio.blocks.schema.DynamicOptic,
    transform: DynamicSchemaExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { currentValue =>
      evalExpr(transform, currentValue, at)
    }

  private def executeChangeFieldType(
    at: zio.blocks.schema.DynamicOptic,
    converter: DynamicSchemaExpr,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { currentValue =>
      evalExpr(converter, currentValue, at)
    }

  // ==================== Collection/Map Action Execution ====================

  private def executeTransformElements(
    at: zio.blocks.schema.DynamicOptic,
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
    at: zio.blocks.schema.DynamicOptic,
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
    at: zio.blocks.schema.DynamicOptic,
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
    at: zio.blocks.schema.DynamicOptic,
    default: DynamicValue,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) {
      // Option is represented as a Variant with cases "None" and "Some"
      case DynamicValue.Variant("None", _) =>
        Right(default)
      case DynamicValue.Variant("Some", inner) =>
        Right(inner)
      case other =>
        // If it's not an Option variant, assume it's already the value
        Right(other)
    }

  private def executeOptionalize(
    at: zio.blocks.schema.DynamicOptic,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value) { v =>
      // Wrap the value in Some
      Right(DynamicValue.Variant("Some", v))
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
    val sourceCaseName = at.nodes.lastOption.collect { case zio.blocks.schema.DynamicOptic.Node.Case(name) => name }
      .getOrElse(targetCaseName)
    // Drop only a trailing Case node (the transformed case itself, e.g. builder-emitted
    // root.caseOf(name)). Intermediate Case nodes are kept so modifyAtPath can navigate
    // through nested variants to the target case value.
    val parentNodes = at.nodes.lastOption match {
      case Some(_: zio.blocks.schema.DynamicOptic.Node.Case) => at.nodes.dropRight(1)
      case _                                                 => at.nodes
    }
    val parentPath = zio.blocks.schema.DynamicOptic(parentNodes)

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
    path: zio.blocks.schema.DynamicOptic,
    value: DynamicValue
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) {
      f(value)
    } else {
      modifyAtPath(nodes, 0, value, path)(f)
    }
  }

  private def modifyAtPath(
    nodes: IndexedSeq[zio.blocks.schema.DynamicOptic.Node],
    idx: Int,
    value: DynamicValue,
    fullPath: zio.blocks.schema.DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    import zio.blocks.schema.DynamicOptic.Node

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
