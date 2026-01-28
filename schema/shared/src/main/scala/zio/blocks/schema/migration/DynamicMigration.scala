package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.MigrationError._

final case class DynamicMigration(actions: Vector[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(currentValue), action) => applyAction(action, currentValue)
      case (Left(error), _)              => Left(error)
    }

  private def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] = {
    action match {
      case AddField(at, default) =>
        DynamicSchemaExpr.eval(default, value).flatMap { defaultValue =>
          DynamicValue.insertAtPathOrFail(value, at, defaultValue)
            .left.map(e => EvaluationError(at, e.message))
        }

      case DropField(at, _) =>
        DynamicValue.deleteAtPathOrFail(value, at)
          .left.map(e => EvaluationError(at, e.message))

      case Rename(at, to) =>
        rename(value, at, to)

      case TransformValue(at, transform, _) =>
        DynamicSchemaExpr.eval(transform, value).flatMap { newValue =>
          value.setOrFail(at, newValue)
            .left.map(e => EvaluationError(at, e.message))
        }
      
      case Mandate(at, default) =>
         val selection = value.get(at)
         selection.values.flatMap(_.headOption) match {
            case Some(DynamicValue.Null) | None => 
               DynamicSchemaExpr.eval(default, value).flatMap { defaultValue =>
                  value.setOrFail(at, defaultValue)
                     .left.map(e => EvaluationError(at, e.message))
               }
            case Some(_) => Right(value)
         }
      
      case Optionalize(_) => Right(value)
    }
  }

  private def rename(value: DynamicValue, at: DynamicOptic, to: String): Either[MigrationError, DynamicValue] = {
     val nodes = at.nodes
     if (nodes.isEmpty) return Left(ValidationError("Cannot rename root"))

     val parentPath = new DynamicOptic(nodes.dropRight(1))
     val leafNode = nodes.last
     
     leafNode match {
       case DynamicOptic.Node.Field(oldName) =>
         DynamicValue.modifyAtPathOrFail(value, parentPath, {
            case record: DynamicValue.Record =>
               val index = record.fields.indexWhere(_._1 == oldName)
               if (index < 0) record 
               else {
                  val (_, v) = record.fields(index)
                  val newFields = record.fields.updated(index, (to, v))
                  DynamicValue.Record(newFields)
               }
            case other => other
         }).left.map(e => EvaluationError(at, e.message))
       
       case _ => Left(ValidationError("Can only rename fields"))
     }
  }
}
