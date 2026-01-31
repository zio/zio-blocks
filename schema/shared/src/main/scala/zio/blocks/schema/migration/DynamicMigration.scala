/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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
import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * An untyped migration that operates on DynamicValue.
 *
 * DynamicMigration is:
 *   - Fully serializable (no functions or closures)
 *   - Introspectable (can examine the action tree)
 *   - Composable (can be combined with other migrations)
 *   - Reversible (every migration has a structural reverse)
 *
 * This is the pure data core of the migration system.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a DynamicValue.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    applyAt(value, DynamicOptic.root)

  /**
   * Apply this migration at a specific path.
   */
  def applyAt(value: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] = {
    var current = value
    var idx     = 0
    while (idx < actions.length) {
      DynamicMigration.applyAction(current, actions(idx), path) match {
        case Right(updated) => current = updated
        case Left(error)    => return Left(error)
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Compose two migrations. The result applies this migration first, then that migration.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * Alias for ++
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the structural reverse of this migration.
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  /**
   * Check if this migration is empty (no actions).
   */
  def isEmpty: Boolean = actions.isEmpty

  override def toString: String =
    if (actions.isEmpty) "DynamicMigration {}"
    else s"DynamicMigration { ${actions.mkString(", ")} }"
}

object DynamicMigration {

  /**
   * Empty migration - identity element for composition.
   */
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  /**
   * Identity migration.
   */
  val identity: DynamicMigration = DynamicMigration(Vector(MigrationAction.Identity))

  /**
   * Create a migration from a single action.
   */
  def apply(action: MigrationAction): DynamicMigration =
    DynamicMigration(Vector(action))

  /**
   * Create a migration from multiple actions.
   */
  def apply(actions: MigrationAction*): DynamicMigration =
    DynamicMigration(actions.toVector)

  /**
   * Apply a single migration action to a DynamicValue.
   */
  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.Identity =>
        Right(value)

      case MigrationAction.Sequence(actions) =>
        var current = value
        var idx     = 0
        while (idx < actions.length) {
          applyAction(current, actions(idx), path) match {
            case Right(updated) => current = updated
            case Left(error)    => return Left(error)
          }
          idx += 1
        }
        Right(current)

      case MigrationAction.AddField(fieldName, default) =>
        value match {
          case DynamicValue.Record(fields) =>
            default.eval(value, path).map { defaultValue =>
              DynamicValue.Record(fields :+ (fieldName, defaultValue))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.DropField(fieldName, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.RenameField(from, to) =>
        value match {
          case DynamicValue.Record(fields) =>
            val updatedFields = fields.map {
              case (name, v) if name == from => (to, v)
              case other                     => other
            }
            Right(DynamicValue.Record(updatedFields))
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformField(fieldName, transform, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              Left(MigrationError.missingField(path, fieldName))
            } else {
              val (name, fieldValue) = fields(fieldIdx)
              val fieldPath          = path.field(fieldName)
              transform.eval(fieldValue, fieldPath).map { newValue =>
                DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
              }
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.MandateField(fieldName, default) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              Left(MigrationError.missingField(path, fieldName))
            } else {
              val (name, fieldValue) = fields(fieldIdx)
              val unwrapped = fieldValue match {
                case DynamicValue.Variant("Some", inner) =>
                  inner match {
                    case DynamicValue.Record(innerFields) =>
                      innerFields.find(_._1 == "value").map(_._2).getOrElse(inner)
                    case _ => inner
                  }
                case DynamicValue.Variant("None", _) | DynamicValue.Null =>
                  default.eval(value, path.field(fieldName)) match {
                    case Right(v) => v
                    case Left(_)  => DynamicValue.Null
                  }
                case other => other
              }
              Right(DynamicValue.Record(fields.updated(fieldIdx, (name, unwrapped))))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.OptionalizeField(fieldName) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              Left(MigrationError.missingField(path, fieldName))
            } else {
              val (name, fieldValue) = fields(fieldIdx)
              val wrapped = fieldValue match {
                case DynamicValue.Null =>
                  DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
                case v =>
                  DynamicValue.Variant("Some", DynamicValue.Record(Chunk(("value", v))))
              }
              Right(DynamicValue.Record(fields.updated(fieldIdx, (name, wrapped))))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.ChangeFieldType(fieldName, converter) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              Left(MigrationError.missingField(path, fieldName))
            } else {
              val (name, fieldValue) = fields(fieldIdx)
              val fieldPath          = path.field(fieldName)
              converter.eval(fieldValue, fieldPath).map { newValue =>
                DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
              }
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.KeepField(_) =>
        Right(value) // No-op

      // ===========================================================================
      // Hierarchical Nesting Operations - THE KEY INNOVATION
      // ===========================================================================

      case MigrationAction.AtField(fieldName, nestedActions) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              Left(MigrationError.missingField(path, fieldName))
            } else {
              val (name, fieldValue) = fields(fieldIdx)
              val nestedPath         = path.field(fieldName)
              val nestedMigration    = DynamicMigration(nestedActions)
              nestedMigration.applyAt(fieldValue, nestedPath).map { newValue =>
                DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
              }
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.AtCase(caseName, nestedActions) =>
        value match {
          case DynamicValue.Variant(name, inner) if name == caseName =>
            val nestedPath      = path.caseOf(caseName)
            val nestedMigration = DynamicMigration(nestedActions)
            nestedMigration.applyAt(inner, nestedPath).map { newInner =>
              DynamicValue.Variant(name, newInner)
            }
          case DynamicValue.Variant(_, _) =>
            // Case doesn't match, return unchanged
            Right(value)
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Variant", other.getClass.getSimpleName))
        }

      case MigrationAction.AtElements(nestedActions) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            val nestedMigration = DynamicMigration(nestedActions)
            val results = elements.zipWithIndex.map { case (elem, idx) =>
              nestedMigration.applyAt(elem, path.at(idx))
            }
            val errors = results.collect { case Left(e) => e }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Sequence", other.getClass.getSimpleName))
        }

      case MigrationAction.AtMapKeys(nestedActions) =>
        value match {
          case DynamicValue.Map(entries) =>
            val nestedMigration = DynamicMigration(nestedActions)
            val results = entries.map { case (k, v) =>
              nestedMigration.applyAt(k, path).map(newK => (newK, v))
            }
            val errors = results.collect { case Left(e) => e }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Map", other.getClass.getSimpleName))
        }

      case MigrationAction.AtMapValues(nestedActions) =>
        value match {
          case DynamicValue.Map(entries) =>
            val nestedMigration = DynamicMigration(nestedActions)
            val results = entries.map { case (k, v) =>
              nestedMigration.applyAt(v, path).map(newV => (k, newV))
            }
            val errors = results.collect { case Left(e) => e }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Map", other.getClass.getSimpleName))
        }

      // ===========================================================================
      // Enum Operations
      // ===========================================================================

      case MigrationAction.RenameCase(from, to) =>
        value match {
          case DynamicValue.Variant(name, inner) if name == from =>
            Right(DynamicValue.Variant(to, inner))
          case _ =>
            Right(value) // Case doesn't match, return unchanged
        }

      case MigrationAction.TransformCase(caseName, nestedActions) =>
        value match {
          case DynamicValue.Variant(name, inner) if name == caseName =>
            val nestedPath      = path.caseOf(caseName)
            val nestedMigration = DynamicMigration(nestedActions)
            nestedMigration.applyAt(inner, nestedPath).map { newInner =>
              DynamicValue.Variant(name, newInner)
            }
          case _ =>
            Right(value) // Case doesn't match, return unchanged
        }

      // ===========================================================================
      // Collection Operations
      // ===========================================================================

      case MigrationAction.TransformElements(transform, _) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            val results = elements.zipWithIndex.map { case (elem, idx) =>
              transform.eval(elem, path.at(idx))
            }
            val errors = results.collect { case Left(e) => e }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Sequence", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformKeys(transform, _) =>
        value match {
          case DynamicValue.Map(entries) =>
            val results = entries.map { case (k, v) =>
              transform.eval(k, path).map(newK => (newK, v))
            }
            val errors = results.collect { case Left(e) => e }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Map", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformValues(transform, _) =>
        value match {
          case DynamicValue.Map(entries) =>
            val results = entries.map { case (k, v) =>
              transform.eval(v, path).map(newV => (k, newV))
            }
            val errors = results.collect { case Left(e) => e }
            if (errors.nonEmpty) {
              Left(errors.head)
            } else {
              Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Map", other.getClass.getSimpleName))
        }

      // ===========================================================================
      // Join/Split Operations
      // ===========================================================================

      case MigrationAction.JoinFields(sourceFields, targetField, combiner, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            // Get source field values
            val sourceValues = sourceFields.flatMap { name =>
              fields.find(_._1 == name).map(_._2)
            }
            if (sourceValues.length != sourceFields.length) {
              val missing = sourceFields.filterNot(name => fields.exists(_._1 == name))
              Left(MigrationError.missingField(path, missing.mkString(", ")))
            } else {
              // Create a record with source values for combiner to access
              val sourceRecord = DynamicValue.Record(Chunk.fromIterable(sourceFields.zip(sourceValues)))
              combiner.eval(sourceRecord, path).map { combinedValue =>
                val filteredFields = fields.filterNot(f => sourceFields.contains(f._1))
                DynamicValue.Record(filteredFields :+ (targetField, combinedValue))
              }
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.SplitField(sourceField, targetFields, splitter, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == sourceField) match {
              case Some((_, sourceValue)) =>
                splitter.eval(sourceValue, path.field(sourceField)).flatMap {
                  case DynamicValue.Record(splitFields) =>
                    // Extract values for target fields
                    val newFields = targetFields.flatMap { name =>
                      splitFields.find(_._1 == name)
                    }
                    if (newFields.length != targetFields.length) {
                      Left(MigrationError.expressionFailed(path, "splitter", "Splitter did not produce all target fields"))
                    } else {
                      val filteredFields = fields.filterNot(_._1 == sourceField)
                      Right(DynamicValue.Record(filteredFields ++ Chunk.fromIterable(newFields)))
                    }
                  case DynamicValue.Sequence(values) if values.length == targetFields.length =>
                    val newFields      = Chunk.fromIterable(targetFields).zip(values)
                    val filteredFields = fields.filterNot(_._1 == sourceField)
                    Right(DynamicValue.Record(filteredFields ++ newFields))
                  case other =>
                    Left(MigrationError.unexpectedStructure(path, "Record or Sequence", other.getClass.getSimpleName))
                }
              case None =>
                Left(MigrationError.missingField(path, sourceField))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }
    }
}
