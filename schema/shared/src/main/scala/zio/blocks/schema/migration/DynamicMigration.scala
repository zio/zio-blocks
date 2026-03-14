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
import zio.blocks.schema.DynamicValue

/**
 * Single migration step at a path. Each action has an algebraic [[reverse]] so
 * that [[DynamicMigration.reverse]] is computed without runtime code
 * generation.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  // --- Record actions ---
  final case class AddField(at: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }
  final case class DropField(at: DynamicOptic, defaultForReverse: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val oldName = DynamicOptic.terminalName(at)
      val newPath = DynamicOptic.replaceTerminal(at, to)
      Rename(newPath, oldName)
    }
  }
  final case class TransformValue(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform.inverse)
  }
  final case class Mandate(at: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction =
      throw new UnsupportedOperationException(
        "Optionalize.reverse requires an explicit default from the builder; it is mathematically lossy otherwise."
      )
  }
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, combiner.inverse)
  }
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicSchemaExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter.inverse)
  }
  final case class ChangeType(at: DynamicOptic, converter: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter.inverse)
  }

  // --- Enum actions ---
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }
  final case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
  }

  // --- Collection / map actions ---
  final case class TransformElements(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.inverse)
  }
  final case class TransformKeys(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.inverse)
  }
  final case class TransformValues(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.inverse)
  }
}

/**
 * Fully serializable migration: a sequence of actions to apply to a
 * [[DynamicValue]]. [[apply]] is the interpreter (Phase 4); [[reverse]] is
 * computed algebraically from each action's [[MigrationAction.reverse]].
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Pure interpreter: evaluates the sequence of actions against the untyped
   * DynamicValue tree.
   */
  def apply(value: DynamicValue): Either[String, DynamicValue] =
    actions.foldLeft[Either[String, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => applyAction(v, action.at, action))
    }

  private def applyAction(
    current: DynamicValue,
    optic: DynamicOptic,
    action: MigrationAction
  ): Either[String, DynamicValue] =
    optic match {
      case DynamicOptic.Field(name, Some(nextOptic)) =>
        current match {
          case r: DynamicValue.Record =>
            recordField(r, name) match {
              case Some(child) =>
                applyAction(child, nextOptic, action).map { newChild =>
                  DynamicValue.Record(recordUpdated(r.fields, name, newChild))
                }
              case None =>
                Left(s"Path failure: Field '$name' not found in record.")
            }
          case _ =>
            Left(
              s"Path failure: Expected Record at field '$name', but found ${current.valueType}"
            )
        }

      case DynamicOptic.Field(name, None) =>
        current match {
          case r: DynamicValue.Record =>
            applyTerminalRecordAction(r.fields, name, action)
          case _ =>
            Left(s"Action failure: Expected Record to apply action at field '$name'")
        }

      case _ =>
        Left(s"Unsupported optic traversal: $optic")
    }

  private def applyTerminalRecordAction(
    fields: Chunk[(String, DynamicValue)],
    fieldName: String,
    action: MigrationAction
  ): Either[String, DynamicValue] =
    action match {
      case MigrationAction.AddField(_, default) =>
        evalExpr(default, None).map { v =>
          DynamicValue.Record(recordSetField(fields, fieldName, v))
        }

      case MigrationAction.DropField(_, _) =>
        Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))

      case MigrationAction.Rename(_, toName) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            Right(
              DynamicValue.Record(
                recordSetField(fields.filterNot(_._1 == fieldName), toName, v)
              )
            )
          case None =>
            Left(s"Rename failed: Field '$fieldName' not found.")
        }

      case MigrationAction.TransformValue(_, expr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            evalExpr(expr, Some(v)).map { newV =>
              DynamicValue.Record(recordUpdated(fields, fieldName, newV))
            }
          case None =>
            Left(s"TransformValue failed: Field '$fieldName' not found.")
        }

      case MigrationAction.ChangeType(_, expr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            evalExpr(expr, Some(v)).map { newV =>
              DynamicValue.Record(recordUpdated(fields, fieldName, newV))
            }
          case None =>
            Left(s"ChangeType failed: Field '$fieldName' not found.")
        }

      case MigrationAction.Mandate(_, defaultExpr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(DynamicValue.Variant("Some", v)) =>
            Right(DynamicValue.Record(recordUpdated(fields, fieldName, v)))
          case Some(DynamicValue.Variant("None", _)) =>
            evalExpr(defaultExpr, None).map { v =>
              DynamicValue.Record(recordUpdated(fields, fieldName, v))
            }
          case Some(_) =>
            Left(s"Mandate failed: Field '$fieldName' is not an Option.")
          case None =>
            Left(s"Mandate failed: Field '$fieldName' not found.")
        }

      case MigrationAction.Optionalize(_) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            Right(
              DynamicValue.Record(
                recordUpdated(
                  fields,
                  fieldName,
                  DynamicValue.Variant("Some", v)
                )
              )
            )
          case None =>
            Left(s"Optionalize failed: Field '$fieldName' not found.")
        }

      case _ =>
        Left(s"Unsupported terminal action on Record: $action")
    }

  private def recordField(r: DynamicValue.Record, name: String): Option[DynamicValue] =
    recordFieldFromChunk(r.fields, name)

  private def recordFieldFromChunk(
    fields: Chunk[(String, DynamicValue)],
    name: String
  ): Option[DynamicValue] =
    fields.find(_._1 == name).map(_._2)

  private def recordUpdated(
    fields: Chunk[(String, DynamicValue)],
    name: String,
    newValue: DynamicValue
  ): Chunk[(String, DynamicValue)] = {
    val idx = fields.indexWhere(_._1 == name)
    if (idx >= 0) fields.updated(idx, (name, newValue))
    else fields :+ (name, newValue)
  }

  private def recordSetField(
    fields: Chunk[(String, DynamicValue)],
    name: String,
    value: DynamicValue
  ): Chunk[(String, DynamicValue)] = {
    val idx = fields.indexWhere(_._1 == name)
    if (idx >= 0) fields.updated(idx, (name, value))
    else fields :+ (name, value)
  }

  private def evalExpr(
    expr: DynamicSchemaExpr,
    context: Option[DynamicValue]
  ): Either[String, DynamicValue] =
    expr match {
      case DynamicSchemaExpr.Literal(v) =>
        Right(v)
      case DynamicSchemaExpr.BiTransform(forward, _) =>
        evalExpr(forward, context)
      case DynamicSchemaExpr.Fail(reason) =>
        Left(reason)
      case DynamicSchemaExpr.DefaultValue =>
        Left(
          "DefaultValue must be resolved at builder time or requires schema context."
        )
      case DynamicSchemaExpr.ConvertPrimitive(from, to) =>
        Left(s"ConvertPrimitive unimplemented for MVP: $from to $to")
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(this.actions.reverse.map(_.reverse))
}
