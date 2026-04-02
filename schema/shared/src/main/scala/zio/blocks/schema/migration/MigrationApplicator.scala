package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, DynamicOptic}
import zio.blocks.schema.SchemaError

object MigrationApplicator {

  def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] = {
    action match {
      case MigrationAction.AddField(at, default) =>
        default.evalDynamic(null) match {
          case Right(seq) if seq.nonEmpty =>
            value.insertOrFail(at, seq.head).left.map(e => MigrationError.Other(e.message))
          case Right(_) =>
            Left(MigrationError.EvaluationError("Default schema expression returned empty sequence"))
          case Left(check) =>
            Left(MigrationError.EvaluationError(s"Failed to evaluate default: ${check.message}"))
        }

      case MigrationAction.DropField(at, _) =>
        value.deleteOrFail(at).left.map(e => MigrationError.Other(e.message))

      case MigrationAction.Rename(at, to) =>
        value.get(at) match {
          case selection if selection.isEmpty =>
            Left(MigrationError.PathNotFound(at))
          case selection =>
            val nodeVal = selection.values.head
            for {
              deleted <- value.deleteOrFail(at).left.map(e => MigrationError.Other(e.message))
              atParent = if (at.nodes.isEmpty) at else DynamicOptic(at.nodes.init)
              // Rename implies replacing the last optic node. For records, it's a Field node.
              inserted <- deleted.insertOrFail(atParent.append(DynamicOptic.Node.Field(to)), nodeVal)
                .left.map(e => MigrationError.Other(e.message))
            } yield inserted
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
                value.setOrFail(at, seq.head).left.map(e => MigrationError.Other(e.message))
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
        value.modifyOrFail(at) {
          case DynamicValue.Variant(`from`, internalVal) => DynamicValue.Variant(to, internalVal)
        }.left.map(e => MigrationError.Other(e.message))

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

}
