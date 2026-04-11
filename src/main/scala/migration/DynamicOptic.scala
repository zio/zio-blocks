package migration

import migration.DynamicValue._

sealed trait PathSegment
case class Field(name: String) extends PathSegment

case class DynamicOptic(path: List[PathSegment]) {

  def modify(
    value: DynamicValue,
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {

    def loop(current: DynamicValue, remaining: List[PathSegment]): Either[MigrationError, DynamicValue] =
      remaining match {

        case Nil =>
          f(current)

        case Field(name) :: rest =>
          current match {

            case Obj(fields) =>
              fields.get(name) match {
                case Some(next) =>
                  loop(next, rest).map(updated =>
                    Obj(fields.updated(name, updated))
                  )

                case None =>
                  Left(MigrationError(s"Missing field: $name", Some(this)))
              }

            case _ =>
              Left(MigrationError("Expected object during path traversal", Some(this)))
          }
      }

    loop(value, path)
  }
}