package migration

import migration.DynamicValue._

sealed trait MigrationAction {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  def reverse: MigrationAction
}

case class AddField(
  path: DynamicOptic,
  field: String,
  value: DynamicValue
) extends MigrationAction {

  def apply(input: DynamicValue): Either[MigrationError, DynamicValue] =
    path.modify(input, {
      case Obj(fields) =>
        Right(Obj(fields + (field -> value)))
      case _ =>
        Left(MigrationError("AddField expects object", Some(path)))
    })

  def reverse: MigrationAction =
    DropField(path, field)
}

case class DropField(
  path: DynamicOptic,
  field: String
) extends MigrationAction {

  def apply(input: DynamicValue): Either[MigrationError, DynamicValue] =
    path.modify(input, {
      case Obj(fields) =>
        Right(Obj(fields - field))
      case _ =>
        Left(MigrationError("DropField expects object", Some(path)))
    })

  def reverse: MigrationAction =
    AddField(path, field, Null)
}

case class RenameField(
  path: DynamicOptic,
  from: String,
  to: String
) extends MigrationAction {

  def apply(input: DynamicValue): Either[MigrationError, DynamicValue] =
    path.modify(input, {
      case Obj(fields) =>
        fields.get(from) match {
          case Some(v) =>
            Right(Obj(fields - from + (to -> v)))
          case None =>
            Left(MigrationError(s"Missing field: $from", Some(path)))
        }
      case _ =>
        Left(MigrationError("RenameField expects object", Some(path)))
    })

  def reverse: MigrationAction =
    RenameField(path, to, from)
}