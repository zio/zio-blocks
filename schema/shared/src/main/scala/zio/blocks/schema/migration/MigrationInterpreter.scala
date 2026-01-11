package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue
import zio.blocks.schema.DynamicValue._
import zio.blocks.schema.DynamicOptic

object MigrationInterpreter {
  def run(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    import MigrationAction._
    
    val clientOptic = action.at
    val nodes = clientOptic.nodes.toVector

    action match {
      case r: Rename =>
        PathNavigator.transform(value, nodes) {
          case Record(fields) =>
            val index = fields.indexWhere(_._1 == r.from)
            if (index == -1) Left(MigrationError.FieldNotFound(clientOptic, r.from))
            else Right(Record(fields.map { case (k, v) => if (k == r.from) (r.to, v) else (k, v) }))
          case other => Left(MigrationError.TypeMismatch(clientOptic, "Record", other.getClass.getSimpleName))
        }

      case a: AddField =>
        PathNavigator.transform(value, nodes) {
          case Record(fields) => Right(Record(fields :+ (a.fieldName -> a.defaultValue)))
          case other => Left(MigrationError.TypeMismatch(clientOptic, "Record", other.getClass.getSimpleName))
        }
        
      case _: DropField =>
        val fieldName = clientOptic.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(n)) => n
          case _ => ""
        }
        val parentPath = DynamicOptic(clientOptic.nodes.dropRight(1))
        PathNavigator.transform(value, parentPath.nodes.toVector) {
          case Record(fields) => 
            if (fields.exists(_._1 == fieldName)) Right(Record(fields.filterNot(_._1 == fieldName)))
            else Left(MigrationError.FieldNotFound(parentPath, fieldName))
          case other => Left(MigrationError.TypeMismatch(parentPath, "Record", other.getClass.getSimpleName))
        }

      case m: Mandate =>
        val fieldName = clientOptic.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(n)) => n
          case _ => ""
        }
        val parentPath = DynamicOptic(clientOptic.nodes.dropRight(1))
        PathNavigator.transform(value, parentPath.nodes.toVector) {
          case Record(fields) =>
            if (fields.exists(_._1 == fieldName)) Right(Record(fields))
            else Right(Record(fields :+ (fieldName -> m.defaultValue)))
          case other => Left(MigrationError.TypeMismatch(parentPath, "Record", other.getClass.getSimpleName))
        }

      case rc: RenameCase =>
        PathNavigator.transform(value, nodes) {
          case Variant(caseName, v) if caseName == rc.from => Right(Variant(rc.to, v))
          case other @ Variant(_, _) => Right(other)
          case other => Left(MigrationError.TypeMismatch(clientOptic, "Variant", other.getClass.getSimpleName))
        }

      case _ => Right(value)
    }
  }
}