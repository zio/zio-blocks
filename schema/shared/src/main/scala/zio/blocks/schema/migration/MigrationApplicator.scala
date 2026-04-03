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

import zio.blocks.schema.{DynamicValue, DynamicOptic}

object MigrationApplicator {

  def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.AddField(at, default) =>
        default.evalDynamic(null) match {
          case Right(seq) if seq.nonEmpty =>
            value.insertOrFail(at, seq.head).fold(e => Left(MigrationError.Other(e.message)), Right(_))
          case Right(_) =>
            Left(MigrationError.EvaluationError("Default schema expression returned empty sequence"))
          case Left(check) =>
            Left(MigrationError.EvaluationError(s"Failed to evaluate default: ${check.message}"))
        }

      case MigrationAction.DropField(at, _) =>
        value.deleteOrFail(at).fold(e => Left(MigrationError.Other(e.message)), Right(_))

      case MigrationAction.Rename(at, to) =>
        value.get(at) match {
          case selection if selection.isEmpty =>
            Left(MigrationError.PathNotFound(at))
          case selection =>
            val nodeVal = selection.toChunk.head
            value.deleteOrFail(at) match {
              case Left(e)        => Left(MigrationError.Other(e.message))
              case Right(deleted) =>
                val atParent = if (at.nodes.isEmpty) at else DynamicOptic(at.nodes.init)
                // Rename implies replacing the last optic node. For records, it's a Field node.
                deleted.insertOrFail(atParent.field(to), nodeVal) match {
                  case Left(e)         => Left(MigrationError.Other(e.message))
                  case Right(inserted) => Right(inserted)
                }
            }
        }

      case MigrationAction.TransformValue(at, transform) =>
        // transform expects actual value. Since we are operating dynamically,
        // we might not evaluate SchemaExpr purely via evalDynamic without casting,
        // so we leave it as unimplemented unless we build a dynamic evaluator for SchemaExpr.
        Left(MigrationError.EvaluationError("TransformValue runtime evaluation not yet supported"))

      case MigrationAction.Mandate(at, default) =>
        // In DynamicValue, Option fields are usually un-boxed when Some, or absent/Null.
        // If absent or Null, we need to apply default.
        value.get(at) match {
          case selection if selection.isEmpty || selection.values.head == DynamicValue.Null =>
            default.evalDynamic(null) match {
              case Right(seq) if seq.nonEmpty =>
                value.setOrFail(at, seq.head).fold(e => Left(MigrationError.Other(e.message)), Right(_))
              case _ =>
                Left(MigrationError.EvaluationError("Mandate default schema expression failed"))
            }
          case _ => Right(value) // already present
        }

      case MigrationAction.Optionalize(at) =>
        // Structural change only. At runtime, the value remains structurally unchanged as DynamicValue
        // doesn't explicitly wrap in Option dynamically (or it can just be kept as is).
        Right(value)

      case MigrationAction.RenameCase(at, from, to) =>
        value
          .modifyOrFail(at) { case DynamicValue.Variant(`from`, internalVal) =>
            DynamicValue.Variant(to, internalVal)
          }
          .fold(e => Left(MigrationError.Other(e.message)), Right(_))

      case MigrationAction.TransformCase(at, actions) =>
        value.get(at) match {
          case selection if selection.nonEmpty =>
            // apply nested migration actions sequentially
            actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, innerAction) =>
              acc.flatMap(applyAction(innerAction, _))
            }
          case _ => Right(value)
        }

      case _ =>
        Left(MigrationError.EvaluationError(s"Unimplemented or unsupported runtime evaluation for action: $action"))
    }

}
