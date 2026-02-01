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
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current = value
    var idx     = 0
    while (idx < actions.length) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(updated) => current = updated
        case Left(error)    => return Left(error)
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Compose two migrations. The result applies this migration first, then that
   * migration.
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
  val identity: DynamicMigration = DynamicMigration(Vector(MigrationAction.Identity(DynamicOptic.root)))

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
   *
   * The action's `at` path determines where the action is applied.
   */
  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val path = action.at

    // If path is root, apply directly; otherwise navigate to path and apply
    if (path.nodes.isEmpty) {
      applyActionAtRoot(value, action, path)
    } else {
      applyActionAtPath(value, action, path)
    }
  }

  /**
   * Apply action at the root level (when path is empty).
   */
  private def applyActionAtRoot(
    value: DynamicValue,
    action: MigrationAction,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.Identity(_) | MigrationAction.Identity =>
        Right(value)

      case MigrationAction.Sequence(_, actions) =>
        var current = value
        var idx     = 0
        while (idx < actions.length) {
          applyAction(current, actions(idx)) match {
            case Right(updated) => current = updated
            case Left(error)    => return Left(error)
          }
          idx += 1
        }
        Right(current)

      case MigrationAction.AddField(_, fieldName, default) =>
        value match {
          case DynamicValue.Record(fields) =>
            default.eval(value, path).map { defaultValue =>
              DynamicValue.Record(fields :+ (fieldName, defaultValue))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.DropField(_, fieldName, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Rename(at, to) =>
        // For rename, the path includes the field name
        // We need to extract it from the path
        at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(from)) =>
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
          case _ =>
            Left(MigrationError.unexpectedStructure(path, "Field path for rename", "non-field path"))
        }

      case MigrationAction.TransformValue(at, transform, _) =>
        at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(fieldName)) =>
            value match {
              case DynamicValue.Record(fields) =>
                val fieldIdx = fields.indexWhere(_._1 == fieldName)
                if (fieldIdx < 0) {
                  Left(MigrationError.missingField(path, fieldName))
                } else {
                  val (name, fieldValue) = fields(fieldIdx)
                  transform.eval(fieldValue, path).map { newValue =>
                    DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                  }
                }
              case other =>
                Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
            }
          case _ =>
            // Transform on root value
            transform.eval(value, path)
        }

      case MigrationAction.Mandate(at, default) =>
        at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(fieldName)) =>
            value match {
              case DynamicValue.Record(fields) =>
                val fieldIdx = fields.indexWhere(_._1 == fieldName)
                if (fieldIdx < 0) {
                  Left(MigrationError.missingField(path, fieldName))
                } else {
                  val (name, fieldValue) = fields(fieldIdx)
                  val unwrapped          = fieldValue match {
                    case DynamicValue.Variant("Some", inner) =>
                      inner match {
                        case DynamicValue.Record(innerFields) =>
                          innerFields.find(_._1 == "value").map(_._2).getOrElse(inner)
                        case _ => inner
                      }
                    case DynamicValue.Variant("None", _) | DynamicValue.Null =>
                      default.eval(value, path) match {
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
          case _ =>
            Left(MigrationError.unexpectedStructure(path, "Field path for mandate", "non-field path"))
        }

      case MigrationAction.Optionalize(at) =>
        at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(fieldName)) =>
            value match {
              case DynamicValue.Record(fields) =>
                val fieldIdx = fields.indexWhere(_._1 == fieldName)
                if (fieldIdx < 0) {
                  Left(MigrationError.missingField(path, fieldName))
                } else {
                  val (name, fieldValue) = fields(fieldIdx)
                  val wrapped            = fieldValue match {
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
          case _ =>
            Left(MigrationError.unexpectedStructure(path, "Field path for optionalize", "non-field path"))
        }

      case MigrationAction.ChangeType(at, converter) =>
        at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(fieldName)) =>
            value match {
              case DynamicValue.Record(fields) =>
                val fieldIdx = fields.indexWhere(_._1 == fieldName)
                if (fieldIdx < 0) {
                  Left(MigrationError.missingField(path, fieldName))
                } else {
                  val (name, fieldValue) = fields(fieldIdx)
                  converter.eval(fieldValue, path).map { newValue =>
                    DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                  }
                }
              case other =>
                Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
            }
          case _ =>
            converter.eval(value, path)
        }

      case MigrationAction.Keep(_) =>
        Right(value) // No-op

      case MigrationAction.RenameCase(_, from, to) =>
        value match {
          case DynamicValue.Variant(name, inner) if name == from =>
            Right(DynamicValue.Variant(to, inner))
          case _ =>
            Right(value) // Case doesn't match, return unchanged
        }

      case MigrationAction.TransformCase(_, caseName, caseActions) =>
        value match {
          case DynamicValue.Variant(name, inner) if name == caseName =>
            val nested = DynamicMigration(caseActions)
            nested(inner).map(newInner => DynamicValue.Variant(name, newInner))
          case _ =>
            Right(value) // Case doesn't match, return unchanged
        }

      case MigrationAction.TransformElements(_, transform, _) =>
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

      case MigrationAction.TransformKeys(_, transform, _) =>
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

      case MigrationAction.TransformValues(_, transform, _) =>
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

      case MigrationAction.Join(_, sourcePaths, targetFieldName, combiner, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            // Get source field values from paths
            val sourceValues = sourcePaths.flatMap { srcPath =>
              srcPath.nodes.lastOption match {
                case Some(DynamicOptic.Node.Field(name)) => fields.find(_._1 == name).map(_._2)
                case _                                   => None
              }
            }
            val sourceFieldNames = sourcePaths.flatMap { srcPath =>
              srcPath.nodes.lastOption match {
                case Some(DynamicOptic.Node.Field(name)) => Some(name)
                case _                                   => None
              }
            }
            if (sourceValues.length != sourcePaths.length) {
              val missing = sourceFieldNames.filterNot(name => fields.exists(_._1 == name))
              Left(MigrationError.missingField(path, missing.mkString(", ")))
            } else {
              val sourceRecord = DynamicValue.Record(Chunk.fromIterable(sourceFieldNames.zip(sourceValues)))
              combiner.eval(sourceRecord, path).map { combinedValue =>
                val filteredFields = fields.filterNot(f => sourceFieldNames.contains(f._1))
                DynamicValue.Record(filteredFields :+ (targetFieldName, combinedValue))
              }
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Split(_, sourcePath, targetPaths, splitter, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            val sourceFieldName = sourcePath.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(name)) => name
              case _                                   => return Left(MigrationError.unexpectedStructure(path, "Field path", "non-field path"))
            }
            fields.find(_._1 == sourceFieldName) match {
              case Some((_, sourceValue)) =>
                val targetFieldNames = targetPaths.flatMap { tp =>
                  tp.nodes.lastOption match {
                    case Some(DynamicOptic.Node.Field(name)) => Some(name)
                    case _                                   => None
                  }
                }
                splitter.eval(sourceValue, path).flatMap {
                  case DynamicValue.Record(splitFields) =>
                    val newFields = targetFieldNames.flatMap { name =>
                      splitFields.find(_._1 == name)
                    }
                    if (newFields.length != targetFieldNames.length) {
                      Left(
                        MigrationError.expressionFailed(path, "splitter", "Splitter did not produce all target fields")
                      )
                    } else {
                      val filteredFields = fields.filterNot(_._1 == sourceFieldName)
                      Right(DynamicValue.Record(filteredFields ++ Chunk.fromIterable(newFields)))
                    }
                  case DynamicValue.Sequence(values) if values.length == targetFieldNames.length =>
                    val newFields      = Chunk.fromIterable(targetFieldNames).zip(values)
                    val filteredFields = fields.filterNot(_._1 == sourceFieldName)
                    Right(DynamicValue.Record(filteredFields ++ newFields))
                  case other =>
                    Left(MigrationError.unexpectedStructure(path, "Record or Sequence", other.getClass.getSimpleName))
                }
              case None =>
                Left(MigrationError.missingField(path, sourceFieldName))
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      // ===========================================================================
      // Legacy Action Support - Backward compatible handling for old API
      // ===========================================================================

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
              transform.eval(fieldValue, path).map { newValue =>
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
              val unwrapped          = fieldValue match {
                case DynamicValue.Variant("Some", inner) =>
                  inner match {
                    case DynamicValue.Record(innerFields) =>
                      innerFields.find(_._1 == "value").map(_._2).getOrElse(inner)
                    case _ => inner
                  }
                case DynamicValue.Variant("None", _) | DynamicValue.Null =>
                  default.eval(value, path) match {
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
              val wrapped            = fieldValue match {
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
              converter.eval(fieldValue, path).map { newValue =>
                DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
              }
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.KeepField(_) =>
        Right(value) // No-op

      case MigrationAction.AtField(fieldName, nestedActions) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              Left(MigrationError.missingField(path, fieldName))
            } else {
              val (name, fieldValue) = fields(fieldIdx)
              val nestedMigration    = DynamicMigration(nestedActions)
              nestedMigration(fieldValue).map { newValue =>
                DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
              }
            }
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
        }

      case MigrationAction.AtCase(caseName, nestedActions) =>
        value match {
          case DynamicValue.Variant(name, inner) if name == caseName =>
            val nestedMigration = DynamicMigration(nestedActions)
            nestedMigration(inner).map { newInner =>
              DynamicValue.Variant(name, newInner)
            }
          case DynamicValue.Variant(_, _) =>
            Right(value) // Case doesn't match, return unchanged
          case other =>
            Left(MigrationError.unexpectedStructure(path, "Variant", other.getClass.getSimpleName))
        }

      case MigrationAction.AtElements(nestedActions) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            val nestedMigration = DynamicMigration(nestedActions)
            val results         = elements.map(nestedMigration.apply)
            val errors          = results.collect { case Left(e) => e }
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
            val results         = entries.map { case (k, v) =>
              nestedMigration(k).map(newK => (newK, v))
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
            val results         = entries.map { case (k, v) =>
              nestedMigration(v).map(newV => (k, newV))
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

      case MigrationAction.JoinFields(sourceFields, targetField, combiner, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            val sourceValues = sourceFields.flatMap { name =>
              fields.find(_._1 == name).map(_._2)
            }
            if (sourceValues.length != sourceFields.length) {
              val missing = sourceFields.filterNot(name => fields.exists(_._1 == name))
              Left(MigrationError.missingField(path, missing.mkString(", ")))
            } else {
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
                splitter.eval(sourceValue, path).flatMap {
                  case DynamicValue.Record(splitFields) =>
                    val newFields = targetFields.flatMap { name =>
                      splitFields.find(_._1 == name)
                    }
                    if (newFields.length != targetFields.length) {
                      Left(
                        MigrationError.expressionFailed(path, "splitter", "Splitter did not produce all target fields")
                      )
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

  /**
   * Apply action at a non-root path by navigating to the target location.
   */
  private def applyActionAtPath(
    value: DynamicValue,
    action: MigrationAction,
    fullPath: DynamicOptic
  ): Either[MigrationError, DynamicValue] = {
    // Navigate to parent of the target and apply the action there
    val nodes = fullPath.nodes

    def navigate(
      current: DynamicValue,
      remainingPath: IndexedSeq[DynamicOptic.Node],
      currentPath: DynamicOptic
    ): Either[MigrationError, DynamicValue] = {
      if (remainingPath.isEmpty) {
        // We've reached the target location, apply the action with root path
        val actionAtRoot = withRootPath(action)
        applyActionAtRoot(current, actionAtRoot, currentPath)
      } else {
        val nextNode     = remainingPath.head
        val restPath     = remainingPath.tail
        val nextPathPart = DynamicOptic(Vector(nextNode))

        nextNode match {
          case DynamicOptic.Node.Field(fieldName) =>
            current match {
              case DynamicValue.Record(fields) =>
                val fieldIdx = fields.indexWhere(_._1 == fieldName)
                if (fieldIdx < 0) {
                  Left(MigrationError.missingField(currentPath, fieldName))
                } else {
                  val (name, fieldValue) = fields(fieldIdx)
                  navigate(fieldValue, restPath, currentPath(nextPathPart)).map { newValue =>
                    DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                  }
                }
              case other =>
                Left(MigrationError.unexpectedStructure(currentPath, "Record", other.getClass.getSimpleName))
            }

          case DynamicOptic.Node.Case(caseName) =>
            current match {
              case DynamicValue.Variant(name, inner) if name == caseName =>
                navigate(inner, restPath, currentPath(nextPathPart)).map { newInner =>
                  DynamicValue.Variant(name, newInner)
                }
              case DynamicValue.Variant(_, _) =>
                Right(current) // Case doesn't match, return unchanged
              case other =>
                Left(MigrationError.unexpectedStructure(currentPath, "Variant", other.getClass.getSimpleName))
            }

          case DynamicOptic.Node.Elements =>
            current match {
              case DynamicValue.Sequence(elements) =>
                val results = elements.zipWithIndex.map { case (elem, idx) =>
                  navigate(elem, restPath, currentPath.at(idx))
                }
                val errors = results.collect { case Left(e) => e }
                if (errors.nonEmpty) {
                  Left(errors.head)
                } else {
                  Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
                }
              case other =>
                Left(MigrationError.unexpectedStructure(currentPath, "Sequence", other.getClass.getSimpleName))
            }

          case DynamicOptic.Node.AtIndex(idx) =>
            current match {
              case DynamicValue.Sequence(elements) =>
                if (idx < 0 || idx >= elements.length) {
                  Left(MigrationError.unexpectedStructure(currentPath, s"index $idx", s"length ${elements.length}"))
                } else {
                  navigate(elements(idx), restPath, currentPath.at(idx)).map { newValue =>
                    DynamicValue.Sequence(elements.updated(idx, newValue))
                  }
                }
              case other =>
                Left(MigrationError.unexpectedStructure(currentPath, "Sequence", other.getClass.getSimpleName))
            }

          case DynamicOptic.Node.MapKeys =>
            current match {
              case DynamicValue.Map(entries) =>
                val results = entries.map { case (k, v) =>
                  navigate(k, restPath, currentPath).map(newK => (newK, v))
                }
                val errors = results.collect { case Left(e) => e }
                if (errors.nonEmpty) {
                  Left(errors.head)
                } else {
                  Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
                }
              case other =>
                Left(MigrationError.unexpectedStructure(currentPath, "Map", other.getClass.getSimpleName))
            }

          case DynamicOptic.Node.MapValues =>
            current match {
              case DynamicValue.Map(entries) =>
                val results = entries.map { case (k, v) =>
                  navigate(v, restPath, currentPath).map(newV => (k, newV))
                }
                val errors = results.collect { case Left(e) => e }
                if (errors.nonEmpty) {
                  Left(errors.head)
                } else {
                  Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
                }
              case other =>
                Left(MigrationError.unexpectedStructure(currentPath, "Map", other.getClass.getSimpleName))
            }

          case DynamicOptic.Node.Wrapped =>
            // For wrapped values, navigate into the wrapper
            current match {
              case DynamicValue.Variant("Some", inner) =>
                inner match {
                  case DynamicValue.Record(innerFields) =>
                    innerFields.find(_._1 == "value").map(_._2) match {
                      case Some(wrapped) =>
                        navigate(wrapped, restPath, currentPath(nextPathPart)).map { newValue =>
                          DynamicValue.Variant("Some", DynamicValue.Record(Chunk(("value", newValue))))
                        }
                      case None =>
                        navigate(inner, restPath, currentPath(nextPathPart)).map { newInner =>
                          DynamicValue.Variant("Some", newInner)
                        }
                    }
                  case _ =>
                    navigate(inner, restPath, currentPath(nextPathPart)).map { newInner =>
                      DynamicValue.Variant("Some", newInner)
                    }
                }
              case other =>
                navigate(other, restPath, currentPath(nextPathPart))
            }

          case _ =>
            Left(MigrationError.unexpectedStructure(currentPath, "supported path node", nextNode.toString))
        }
      }
    }

    navigate(value, nodes, DynamicOptic.root)
  }

  /**
   * Create a copy of the action with root path.
   */
  private def withRootPath(action: MigrationAction): MigrationAction = {
    val root = DynamicOptic.root
    action match {
      case MigrationAction.AddField(_, fn, d)  => MigrationAction.AddField(root, fn, d)
      case MigrationAction.DropField(_, fn, d) => MigrationAction.DropField(root, fn, d)
      case MigrationAction.Rename(at, to)      =>
        // For rename, keep the last field node
        at.nodes.lastOption match {
          case Some(node @ DynamicOptic.Node.Field(_)) => MigrationAction.Rename(DynamicOptic(Vector(node)), to)
          case _                                       => MigrationAction.Rename(root, to)
        }
      case MigrationAction.TransformValue(at, t, r) =>
        at.nodes.lastOption match {
          case Some(node @ DynamicOptic.Node.Field(_)) =>
            MigrationAction.TransformValue(DynamicOptic(Vector(node)), t, r)
          case _ => MigrationAction.TransformValue(root, t, r)
        }
      case MigrationAction.Mandate(at, d) =>
        at.nodes.lastOption match {
          case Some(node @ DynamicOptic.Node.Field(_)) => MigrationAction.Mandate(DynamicOptic(Vector(node)), d)
          case _                                       => MigrationAction.Mandate(root, d)
        }
      case MigrationAction.Optionalize(at) =>
        at.nodes.lastOption match {
          case Some(node @ DynamicOptic.Node.Field(_)) => MigrationAction.Optionalize(DynamicOptic(Vector(node)))
          case _                                       => MigrationAction.Optionalize(root)
        }
      case MigrationAction.ChangeType(at, c) =>
        at.nodes.lastOption match {
          case Some(node @ DynamicOptic.Node.Field(_)) => MigrationAction.ChangeType(DynamicOptic(Vector(node)), c)
          case _                                       => MigrationAction.ChangeType(root, c)
        }
      case MigrationAction.Keep(_)                                => MigrationAction.Keep(root)
      case MigrationAction.RenameCase(_, f, t)                    => MigrationAction.RenameCase(root, f, t)
      case MigrationAction.TransformCase(_, cn, a)                => MigrationAction.TransformCase(root, cn, a)
      case MigrationAction.TransformElements(_, t, r)             => MigrationAction.TransformElements(root, t, r)
      case MigrationAction.TransformKeys(_, t, r)                 => MigrationAction.TransformKeys(root, t, r)
      case MigrationAction.TransformValues(_, t, r)               => MigrationAction.TransformValues(root, t, r)
      case MigrationAction.Join(_, sp, tf, c, s)                  => MigrationAction.Join(root, sp, tf, c, s)
      case MigrationAction.Split(_, sour, tp, s, c)               => MigrationAction.Split(root, sour, tp, s, c)
      case MigrationAction.Sequence(_, a)                         => MigrationAction.Sequence(root, a)
      case MigrationAction.Identity(_) | MigrationAction.Identity => MigrationAction.Identity(root)

      // Legacy types - these are already at root level by design
      case legacy: MigrationAction.RenameField      => legacy
      case legacy: MigrationAction.TransformField   => legacy
      case legacy: MigrationAction.MandateField     => legacy
      case legacy: MigrationAction.OptionalizeField => legacy
      case legacy: MigrationAction.ChangeFieldType  => legacy
      case legacy: MigrationAction.KeepField        => legacy
      case legacy: MigrationAction.AtField          => legacy
      case legacy: MigrationAction.AtCase           => legacy
      case legacy: MigrationAction.AtElements       => legacy
      case legacy: MigrationAction.AtMapKeys        => legacy
      case legacy: MigrationAction.AtMapValues      => legacy
      case legacy: MigrationAction.JoinFields       => legacy
      case legacy: MigrationAction.SplitField       => legacy
    }
  }
}
