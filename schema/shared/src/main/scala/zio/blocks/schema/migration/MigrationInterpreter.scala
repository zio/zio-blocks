package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.DynamicValue.{Record, Variant}
import zio.blocks.schema.migration.MigrationAction._

object MigrationInterpreter {

  def run(data: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {

    val nodes = action.at.nodes

    if (nodes.headOption.contains(DynamicOptic.Node.Elements)) {
      data match {
        case DynamicValue.Sequence(values) =>
          val subAction = popPath(action)
          val results   = values.map(v => run(v, subAction))

          results.find(_.isLeft) match {
            case Some(Left(err)) => Left(prependErrorPath(err, DynamicOptic.Node.Elements))
            case _               => Right(DynamicValue.Sequence(results.map(_.toOption.get)))
          }
        case _ => Left(MigrationError.TypeMismatch(action.at, "Sequence", data.getClass.getSimpleName))
      }
    } else if (nodes.length > 1 && nodes.head.isInstanceOf[DynamicOptic.Node.Field]) {
      val fieldNode = nodes.head.asInstanceOf[DynamicOptic.Node.Field]
      val fieldName = fieldNode.name

      data match {
        case Record(values) =>
          val index = values.indexWhere(_._1 == fieldName)
          if (index == -1) Left(MigrationError.FieldNotFound(action.at, fieldName))
          else {
            val (key, value) = values(index)
            val subAction    = popPath(action)

            run(value, subAction) match {
              case Right(newValue) =>
                Right(Record(values.updated(index, (key, newValue))))

              case Left(error) =>
                Left(prependErrorPath(error, fieldNode))
            }
          }
        case _ => Left(MigrationError.TypeMismatch(action.at, "Record", data.getClass.getSimpleName))
      }
    } else {
      executeLocal(data, action)
    }
  }

  private def popPath(action: MigrationAction): MigrationAction = {
    def pop(optic: DynamicOptic): DynamicOptic = DynamicOptic(optic.nodes.tail)

    action match {
      case a: Rename            => a.copy(at = pop(a.at))
      case a: AddField          => a.copy(at = pop(a.at))
      case a: DropField         => a.copy(at = pop(a.at))
      case a: TransformValue    => a.copy(at = pop(a.at))
      case a: Mandate           => a.copy(at = pop(a.at))
      case a: Optionalize       => a.copy(at = pop(a.at))
      case a: ChangeType        => a.copy(at = pop(a.at))
      case a: RenameCase        => a.copy(at = pop(a.at))
      case a: TransformElements => a.copy(at = pop(a.at))
      case a: TransformKeys     => a.copy(at = pop(a.at))
      case a: TransformValues   => a.copy(at = pop(a.at))
      case a: Join              => a.copy(at = pop(a.at))
      case a: Split             => a.copy(at = pop(a.at))
      case a: TransformCase     => a.copy(at = pop(a.at))
    }
  }

  private def prependErrorPath(error: MigrationError, node: DynamicOptic.Node): MigrationError = {
    def prepend(optic: DynamicOptic): DynamicOptic = DynamicOptic(node +: optic.nodes)

    error match {
      case e: MigrationError.FieldNotFound => e.copy(path = prepend(e.path))
      case e: MigrationError.TypeMismatch  => e.copy(path = prepend(e.path))
      case other                           => other
    }
  }

  private def executeLocal(data: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] =
    action match {
      case r: Rename =>
        val oldName = r.at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(n)) => n
          case _                                => return Left(MigrationError.FieldNotFound(r.at, "unknown"))
        }
        data match {
          case Record(fields) =>
            val index = fields.indexWhere(_._1 == oldName)
            if (index == -1) Left(MigrationError.FieldNotFound(r.at, oldName))
            else Right(Record(fields.map { case (k, v) => if (k == oldName) (r.to, v) else (k, v) }))
          case _ => Left(MigrationError.TypeMismatch(r.at, "Record", data.getClass.getSimpleName))
        }

      case a: AddField =>
        val fieldName = a.at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(n)) => n
          case _                                => return Left(MigrationError.FieldNotFound(a.at, "unknown"))
        }
        val defaultValue = evaluateExpr(a.default)
        data match {
          case Record(fields) => Right(Record(fields :+ (fieldName -> defaultValue)))
          case _              => Left(MigrationError.TypeMismatch(a.at, "Record", data.getClass.getSimpleName))
        }

      case d: DropField =>
        val fieldName = d.at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(n)) => n
          case _                                => return Left(MigrationError.FieldNotFound(d.at, "unknown"))
        }
        data match {
          case Record(fields) =>
            val newFields = fields.filterNot(_._1 == fieldName)
            if (newFields.size == fields.size) Left(MigrationError.FieldNotFound(d.at, fieldName))
            else Right(Record(newFields))
          case _ => Left(MigrationError.TypeMismatch(d.at, "Record", data.getClass.getSimpleName))
        }

      case m: Mandate =>
        val defaultValue = evaluateExpr(m.default)
        data match {
          case DynamicValue.Primitive(PrimitiveValue.Unit) => Right(defaultValue)
          case other                                       => Right(other)
        }

      case _: Optionalize => Right(data)

      case rc: RenameCase =>
        data match {
          case Variant(caseName, v) if caseName == rc.from => Right(Variant(rc.to, v))
          case other                                       => Right(other)
        }

      case tv: TransformValue =>
        tv.transform match {
          case SchemaExpr.Identity() => Right(data)
          case _                     => Right(evaluateExpr(tv.transform))
        }

      case ct: ChangeType => Right(evaluateExpr(ct.converter))

      case _ => Right(data)
    }

  private def evaluateExpr(expr: SchemaExpr[_]): DynamicValue = expr match {
    case SchemaExpr.Constant(v)              => v
    case SchemaExpr.DefaultValue(_)          => DynamicValue.Primitive(PrimitiveValue.Unit)
    case SchemaExpr.Identity()               => DynamicValue.Primitive(PrimitiveValue.Unit)
    case SchemaExpr.Converted(operand, _, _) => evaluateExpr(operand)
  }
}
