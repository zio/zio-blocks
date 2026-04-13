package zio.schema.migration

import zio.blocks.schema.DynamicValue
import scala.collection.immutable.ListMap

/**
 * The pure data representation of a schema migration capable of serializing 
 * identically across memory nodes, executing completely abstract of `A` and `B` types.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /** Evaluates the purely declarative algebraic tree of structural steps */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(applyAction(_, action.at, action))
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  /** Core recursive interpreter applying an optic translation AST */
  private def applyAction(
    value: DynamicValue,
    optic: DynamicOptic,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    import DynamicOptic._
    optic match {
      case End =>
        action match {
          case MigrationAction.TransformValue(_, transform, _) =>
            Right(transform(value))
          case MigrationAction.Optionalize(_) =>
            if (value == DynamicValue.NoneValue) Right(value)
            else Right(DynamicValue.SomeValue(value))
          case _ => Left(MigrationError.UnrecoverableParseError("Action incompatible with leaf structural execution"))
        }

      case RecordField(fieldName, next) =>
        value match {
          case DynamicValue.Record(id, fields) =>
            if (next == End) {
              action match {
                case MigrationAction.AddField(_, default) =>
                  Right(DynamicValue.Record(id, fields + (fieldName -> default)))
                case MigrationAction.DropField(_, _) =>
                  Right(DynamicValue.Record(id, fields - fieldName))
                case MigrationAction.Rename(_, to) =>
                  fields.get(fieldName) match {
                    case Some(v) => Right(DynamicValue.Record(id, (fields - fieldName) + (to -> v)))
                    case None    => Left(MigrationError.PathNotFound(optic, value))
                  }
                case MigrationAction.Mandate(_, default) =>
                  fields.get(fieldName) match {
                    case Some(DynamicValue.SomeValue(v)) => Right(DynamicValue.Record(id, fields + (fieldName -> v)))
                    case Some(DynamicValue.NoneValue)    => Right(DynamicValue.Record(id, fields + (fieldName -> default)))
                    case None                            => Left(MigrationError.PathNotFound(optic, value))
                    case _                               => Left(MigrationError.InvalidTypeCorrection("Optional", "Value", optic))
                  }
                case _ =>
                  fields.get(fieldName).map(applyAction(_, next, action)) match {
                    case Some(Right(newValue)) => Right(DynamicValue.Record(id, fields + (fieldName -> newValue)))
                    case Some(Left(e))         => Left(e)
                    case None                  => Left(MigrationError.PathNotFound(optic, value))
                  }
              }
            } else {
              fields.get(fieldName).map(applyAction(_, next, action)) match {
                case Some(Right(newValue)) => Right(DynamicValue.Record(id, fields + (fieldName -> newValue)))
                case Some(Left(e))         => Left(e)
                case None                  => Left(MigrationError.PathNotFound(optic, value))
              }
            }
          case _ => Left(MigrationError.InvalidTypeCorrection("Record", value.getClass.getSimpleName, optic))
        }

      case SequenceElement(next) =>
        value match {
          case seq: DynamicValue.Sequence =>
            action match {
              case MigrationAction.TransformElements(_, transform, _) if next == End =>
                Right(DynamicValue.Sequence(seq.values.map(transform)))
              case _ =>
                val transformed = seq.values.map(item => applyAction(item, next, action))
                val err = transformed.collectFirst { case Left(e) => e }
                err.toLeft(DynamicValue.Sequence(transformed.collect { case Right(v) => v }))
            }
          case _ => Left(MigrationError.InvalidTypeCorrection("Sequence", value.getClass.getSimpleName, optic))
        }

      case MapKey(next) =>
        value match {
          case dict: DynamicValue.Dictionary =>
            action match {
              case MigrationAction.TransformKeys(_, transform, _) if next == End =>
                Right(DynamicValue.Dictionary(dict.entries.map { case (k, v) => (transform(k), v) }))
              case _ =>
                val transformed = dict.entries.map { case (k, v) => applyAction(k, next, action).map(nk => (nk, v)) }
                val err = transformed.collectFirst { case Left(e) => e }
                err.toLeft(DynamicValue.Dictionary(transformed.collect { case Right(v) => v }))
            }
          case _ => Left(MigrationError.InvalidTypeCorrection("Dictionary", value.getClass.getSimpleName, optic))
        }

      case MapValue(next) =>
        value match {
          case dict: DynamicValue.Dictionary =>
            action match {
               case MigrationAction.TransformValues(_, transform, _) if next == End =>
                 Right(DynamicValue.Dictionary(dict.entries.map { case (k, v) => (k, transform(v)) }))
               case _ =>
                 val transformed = dict.entries.map { case (k, v) => applyAction(v, next, action).map(nv => (k, nv)) }
                 val err = transformed.collectFirst { case Left(e) => e }
                 err.toLeft(DynamicValue.Dictionary(transformed.collect { case Right(v) => v }))
            }
          case _ => Left(MigrationError.InvalidTypeCorrection("Dictionary", value.getClass.getSimpleName, optic))
        }

      case EnumCase(tag, next) =>
        value match {
          case DynamicValue.Enumeration(id, (caseTag, caseValue)) =>
            if (caseTag != tag) Right(value) // Safe passthrough for non-matching union cases
            else if (next == End) {
              action match {
                case MigrationAction.RenameCase(_, _, to) =>
                  Right(DynamicValue.Enumeration(id, (to, caseValue)))
                case MigrationAction.TransformCase(_, caseActions) =>
                  val caseMig = DynamicMigration(caseActions)
                  caseMig(caseValue).map(nv => DynamicValue.Enumeration(id, (caseTag, nv)))
                case _ => Left(MigrationError.UnrecoverableParseError("Unsupported Enum Case mutation executed"))
              }
            } else {
              applyAction(caseValue, next, action).map(nv => DynamicValue.Enumeration(id, (caseTag, nv)))
            }
          case _ => Left(MigrationError.InvalidTypeCorrection("Enumeration", value.getClass.getSimpleName, optic))
        }

      case Optional(next) =>
        value match {
          case DynamicValue.SomeValue(inner) =>
            applyAction(inner, next, action).map(DynamicValue.SomeValue(_))
          case DynamicValue.NoneValue => Right(DynamicValue.NoneValue)
          case _ => Left(MigrationError.InvalidTypeCorrection("Optional", value.getClass.getSimpleName, optic))
        }
    }
  }
}
