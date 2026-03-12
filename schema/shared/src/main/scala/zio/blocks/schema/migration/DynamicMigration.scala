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
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaError}

final case class DynamicMigration(actions: Vector[MigrationAction]) {

  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicMigration.execute(actions, value)

  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  def isEmpty: Boolean = actions.isEmpty

  def size: Int = actions.size
}

object DynamicMigration {

  val empty: DynamicMigration = new DynamicMigration(Vector.empty)

  def single(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  def apply(actions: MigrationAction*): DynamicMigration =
    new DynamicMigration(actions.toVector)

  private[migration] def execute(
    actions: Vector[MigrationAction],
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] = {
    var current: DynamicValue = value
    var idx                   = 0
    val len                   = actions.length

    while (idx < len) {
      ActionExecutor.execute(actions(idx), current) match {
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

private[migration] object ActionExecutor {
  import MigrationAction._

  private val MaxPathDepth: Int = 64

  def execute(action: MigrationAction, value: DynamicValue): Either[SchemaError, DynamicValue] =
    action match {
      case a: AddField          => executeAddField(a, value)
      case a: DropField         => executeDropField(a, value)
      case a: Rename            => executeRename(a, value)
      case a: TransformValue    => evalAndApply(a.transform, a.at, value)
      case a: Mandate           => executeMandate(a, value)
      case a: Optionalize       => executeOptionalize(a, value)
      case a: Join              => executeJoin(a, value)
      case a: Split             => executeSplit(a, value)
      case a: ChangeType        => evalAndApply(a.converter, a.at, value)
      case a: RenameCase        => executeRenameCase(a, value)
      case a: TransformCase     => executeTransformCase(a, value)
      case a: ApplyMigration    => executeApplyMigration(a, value)
      case a: TransformElements => executeTransformElements(a, value)
      case a: TransformKeys     => executeTransformKeys(a, value)
      case a: TransformValues   => executeTransformValues(a, value)
      case i: Irreversible      =>
        Left(SchemaError.message(s"Irreversible action: ${i.originalAction}", i.at))
    }

  private def executeAddField(action: AddField, value: DynamicValue): Either[SchemaError, DynamicValue] = {
    val fieldName  = action.fieldName
    val parentPath = DynamicOptic(action.at.nodes.dropRight(1))

    evalExpr(action.default, value).flatMap { defaultValue =>
      modifyAt(parentPath, value, action.at) {
        case DynamicValue.Record(fields) =>
          if (fields.exists(_._1 == fieldName))
            Left(SchemaError.message(s"Field '$fieldName' already exists", action.at))
          else
            Right(DynamicValue.Record(fields :+ (fieldName -> defaultValue)))
        case other =>
          Left(SchemaError.message(s"Expected Record, got ${other.getClass.getSimpleName}", action.at))
      }
    }
  }

  private def executeDropField(action: DropField, value: DynamicValue): Either[SchemaError, DynamicValue] = {
    val fieldName  = action.fieldName
    val parentPath = DynamicOptic(action.at.nodes.dropRight(1))

    modifyAt(parentPath, value, action.at) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == fieldName)
        if (idx < 0)
          Left(SchemaError.message(s"Field '$fieldName' not found", action.at))
        else
          Right(DynamicValue.Record(fields.patch(idx, Nil, 1)))
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.getClass.getSimpleName}", action.at))
    }
  }

  private def executeRename(action: Rename, value: DynamicValue): Either[SchemaError, DynamicValue] = {
    val from       = action.from
    val to         = action.to
    val parentPath = DynamicOptic(action.at.nodes.dropRight(1))

    modifyAt(parentPath, value, action.at) {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == from)
        if (idx < 0)
          Left(SchemaError.message(s"Field '$from' not found", action.at))
        else if (fields.exists(_._1 == to))
          Left(SchemaError.message(s"Field '$to' already exists", action.at))
        else {
          val (_, v) = fields(idx)
          Right(DynamicValue.Record(fields.updated(idx, (to, v))))
        }
      case other =>
        Left(SchemaError.message(s"Expected Record, got ${other.getClass.getSimpleName}", action.at))
    }
  }

  private def executeMandate(action: Mandate, value: DynamicValue): Either[SchemaError, DynamicValue] =
    evalExpr(action.default, value).flatMap { defaultValue =>
      modifyAt(action.at, value, action.at) {
        case DynamicValue.Variant("None", _)     => Right(defaultValue)
        case DynamicValue.Variant("Some", inner) => Right(inner)
        case other                               => Right(other)
      }
    }

  private def executeOptionalize(action: Optionalize, value: DynamicValue): Either[SchemaError, DynamicValue] =
    modifyAt(action.at, value, action.at) { v =>
      Right(DynamicValue.Variant("Some", v))
    }

  private def executeJoin(action: Join, value: DynamicValue): Either[SchemaError, DynamicValue] = {
    val parentPath = DynamicOptic(action.at.nodes.dropRight(1))
    val fieldName  = action.at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => return Left(SchemaError.message("Join target path must end with a Field node", action.at))
    }

    evalExpr(action.combiner, value).flatMap { combinedValue =>
      modifyAt(parentPath, value, action.at) {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields :+ (fieldName -> combinedValue)))
        case other =>
          Left(SchemaError.message(s"Expected Record, got ${other.getClass.getSimpleName}", action.at))
      }
    }
  }

  private def executeSplit(action: Split, value: DynamicValue): Either[SchemaError, DynamicValue] =
    evalExpr(action.splitter, value).flatMap { splitValue =>
      action.targetPaths.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) {
        case (Right(current), targetPath) =>
          val parentPath = DynamicOptic(targetPath.nodes.dropRight(1))
          targetPath.nodes.lastOption match {
            case Some(DynamicOptic.Node.Field(fieldName)) =>
              modifyAt(parentPath, current, targetPath) {
                case DynamicValue.Record(fields) =>
                  Right(DynamicValue.Record(fields :+ (fieldName -> splitValue)))
                case other =>
                  Left(SchemaError.message(s"Expected Record, got ${other.getClass.getSimpleName}", targetPath))
              }
            case _ =>
              Left(SchemaError.message("Split target path must end with a Field node", targetPath))
          }
        case (left, _) => left
      }
    }

  private def executeRenameCase(action: RenameCase, value: DynamicValue): Either[SchemaError, DynamicValue] =
    modifyAt(action.at, value, action.at) {
      case DynamicValue.Variant(caseName, inner) if caseName == action.from =>
        Right(DynamicValue.Variant(action.to, inner))
      case v @ DynamicValue.Variant(_, _) => Right(v)
      case other                          =>
        Left(SchemaError.message(s"Expected Variant, got ${other.getClass.getSimpleName}", action.at))
    }

  private def executeTransformCase(action: TransformCase, value: DynamicValue): Either[SchemaError, DynamicValue] =
    modifyAt(action.at, value, action.at) {
      case DynamicValue.Variant(name, inner) =>
        DynamicMigration.execute(action.actions, inner).map(DynamicValue.Variant(name, _))
      case other =>
        Left(SchemaError.message(s"Expected Variant, got ${other.getClass.getSimpleName}", action.at))
    }

  private def executeApplyMigration(
    action: ApplyMigration,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(action.at, value, action.at) { nestedValue =>
      action.migration(nestedValue)
    }

  private def executeTransformElements(
    action: TransformElements,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(action.at, value, action.at) {
      case DynamicValue.Sequence(elements) =>
        val results = elements.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
          case (Right(acc), elem) =>
            evalExpr(action.transform, elem).map(acc :+ _)
          case (left, _) => left
        }
        results.map(DynamicValue.Sequence(_))
      case other =>
        Left(SchemaError.message(s"Expected Sequence, got ${other.getClass.getSimpleName}", action.at))
    }

  private def executeTransformKeys(action: TransformKeys, value: DynamicValue): Either[SchemaError, DynamicValue] =
    modifyAt(action.at, value, action.at) {
      case DynamicValue.Map(entries) =>
        val results =
          entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
            case (Right(acc), (k, v)) =>
              evalExpr(action.transform, k).map(newK => acc :+ (newK -> v))
            case (left, _) => left
          }
        results.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.message(s"Expected Map, got ${other.getClass.getSimpleName}", action.at))
    }

  private def executeTransformValues(
    action: TransformValues,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(action.at, value, action.at) {
      case DynamicValue.Map(entries) =>
        val results =
          entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
            case (Right(acc), (k, v)) =>
              evalExpr(action.transform, v).map(newV => acc :+ (k -> newV))
            case (left, _) => left
          }
        results.map(DynamicValue.Map(_))
      case other =>
        Left(SchemaError.message(s"Expected Map, got ${other.getClass.getSimpleName}", action.at))
    }

  private def evalAndApply(
    expr: DynamicSchemaExpr,
    at: DynamicOptic,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    modifyAt(at, value, at) { fieldValue =>
      evalExpr(expr, fieldValue)
    }

  private def evalExpr(expr: DynamicSchemaExpr, input: DynamicValue): Either[SchemaError, DynamicValue] =
    expr.eval(input).flatMap { results =>
      results.headOption match {
        case Some(v) => Right(v)
        case None    => Left(SchemaError.message("Expression evaluation returned no values"))
      }
    }

  private def modifyAt(
    path: DynamicOptic,
    value: DynamicValue,
    fullPath: DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) f(value)
    else modifyAtPath(nodes, 0, value, fullPath)(f)
  }

  private def modifyAtPath(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: DynamicValue,
    fullPath: DynamicOptic
  )(f: DynamicValue => Either[SchemaError, DynamicValue]): Either[SchemaError, DynamicValue] = {
    import DynamicOptic.Node

    if (idx >= nodes.length) f(value)
    else if (idx >= MaxPathDepth)
      Left(SchemaError.message(s"Maximum path depth ($MaxPathDepth) exceeded", fullPath))
    else
      nodes(idx) match {
        case Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) Left(SchemaError.message(s"Field '$name' not found", fullPath))
              else {
                val (fieldName, fieldValue) = fields(fieldIdx)
                modifyAtPath(nodes, idx + 1, fieldValue, fullPath)(f).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            case other =>
              Left(
                SchemaError.message(s"Expected Record at field '$name', got ${other.getClass.getSimpleName}", fullPath)
              )
          }

        case Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, inner) if name == caseName =>
              modifyAtPath(nodes, idx + 1, inner, fullPath)(f).map(DynamicValue.Variant(name, _))
            case DynamicValue.Variant(name, _) =>
              Left(SchemaError.message(s"Expected case '$caseName', got case '$name'", fullPath))
            case other =>
              Left(SchemaError.message(s"Expected Variant, got ${other.getClass.getSimpleName}", fullPath))
          }

        case Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results = elements.foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
                case (Right(acc), elem) =>
                  modifyAtPath(nodes, idx + 1, elem, fullPath)(f).map(acc :+ _)
                case (left, _) => left
              }
              results.map(DynamicValue.Sequence(_))
            case other =>
              Left(SchemaError.message(s"Expected Sequence, got ${other.getClass.getSimpleName}", fullPath))
          }

        case Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, k, fullPath)(f).map(newK => acc :+ (newK -> v))
                  case (left, _) => left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.message(s"Expected Map, got ${other.getClass.getSimpleName}", fullPath))
          }

        case Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results =
                entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (Right(acc), (k, v)) =>
                    modifyAtPath(nodes, idx + 1, v, fullPath)(f).map(newV => acc :+ (k -> newV))
                  case (left, _) => left
                }
              results.map(DynamicValue.Map(_))
            case other =>
              Left(SchemaError.message(s"Expected Map, got ${other.getClass.getSimpleName}", fullPath))
          }

        case Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length)
                Left(SchemaError.message(s"Index $index out of bounds (size: ${elements.length})", fullPath))
              else
                modifyAtPath(nodes, idx + 1, elements(index), fullPath)(f).map { newElem =>
                  DynamicValue.Sequence(elements.updated(index, newElem))
                }
            case other =>
              Left(SchemaError.message(s"Expected Sequence, got ${other.getClass.getSimpleName}", fullPath))
          }

        case Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val entryIdx = entries.indexWhere(_._1 == key)
              if (entryIdx < 0) Left(SchemaError.message(s"Map key not found", fullPath))
              else {
                val (k, v) = entries(entryIdx)
                modifyAtPath(nodes, idx + 1, v, fullPath)(f).map { newV =>
                  DynamicValue.Map(entries.updated(entryIdx, (k, newV)))
                }
              }
            case other =>
              Left(SchemaError.message(s"Expected Map, got ${other.getClass.getSimpleName}", fullPath))
          }

        case Node.Wrapped =>
          modifyAtPath(nodes, idx + 1, value, fullPath)(f)

        case other =>
          Left(SchemaError.message(s"Unsupported path node: $other", fullPath))
      }
  }
}
