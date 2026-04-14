package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue
import zio.blocks.schema.DynamicValue.{Record, Primitive}

final case class DynamicMigration(actions: Vector[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      (acc, action) => acc.flatMap(applyAction(_, action))
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  private def applyAction(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] =
    action match {
      case AddField(at, default) =>
        eval(default).map(dv => setPath(value, at.segments.toList, dv))

      case DropField(at, _) =>
        Right(removePath(value, at.segments.toList))

      case Rename(at, to) =>
        val path = at.segments.toList
        getPath(value, path) match {
          case Some(v) => Right(setPath(removePath(value, path), path.init :+ to, v))
          case None => Right(value)
        }

      case TransformValue(at, transform) =>
        getPath(value, at.segments.toList) match {
          case Some(_) => eval(transform).map(dv => setPath(value, at.segments.toList, dv))
          case None => Right(value)
        }

      case ChangeType(at, _, _) =>
        // Primitive -> primitive: keep value as-is (DynamicValue handles type)
        getPath(value, at.segments.toList) match {
          case Some(v) => Right(setPath(value, at.segments.toList, v))
          case None => Right(value)
        }

      case Mandate(at, default) =>
        getPath(value, at.segments.toList) match {
          case Some(DynamicValue.NoneValue) | None => eval(default).map(dv => setPath(value, at.segments.toList, dv))
          case Some(_) => Right(value)
        }

      case Optionalize(at) =>
        getPath(value, at.segments.toList) match {
          case Some(v) => Right(setPath(value, at.segments.toList, DynamicValue.SomeValue(v)))
          case None => Right(value)
        }

      case _ => Right(value)
    }

  private def eval(expr: SchemaExpr[?,?]): Either[MigrationError, DynamicValue] = expr match {
    case SchemaExpr.DefaultValue => Right(Primitive(null))
    case SchemaExpr.Const(v) => Right(Primitive(v))
  }

  private def getPath(v: DynamicValue, path: List[String]): Option[DynamicValue] = (v, path) match {
    case (_, Nil) => Some(v)
    case (r: Record, h :: t) => r.values.get(h).flatMap(getPath(_, t))
    case _ => None
  }

  private def setPath(v: DynamicValue, path: List[String], newV: DynamicValue): DynamicValue = (v, path) match {
    case (_, Nil) => newV
    case (r: Record, h :: Nil) => Record(r.values.updated(h, newV))
    case (r: Record, h :: t) =>
      val child = r.values.getOrElse(h, Record(Map.empty))
      Record(r.values.updated(h, setPath(child, t, newV)))
    case other => other
  }

  private def removePath(v: DynamicValue, path: List[String]): DynamicValue = (v, path) match {
    case (r: Record, h :: Nil) => Record(r.values - h)
    case (r: Record, h :: t) =>
      r.values.get(h).fold(v)(ch => Record(r.values.updated(h, removePath(ch, t))))
    case other => other
  }
}
object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)
}
