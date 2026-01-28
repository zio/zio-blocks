package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}

sealed trait SchemaExpr {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
  def reverse: Option[SchemaExpr]
}

object SchemaExpr {

  case object Identity extends SchemaExpr {
    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(value)

    override def reverse: Option[SchemaExpr] = Some(Identity)
  }

  final case class Constant(value: DynamicValue) extends SchemaExpr {
    override def apply(v: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(value)

    override def reverse: Option[SchemaExpr] = None
  }

  final case class Compose(outer: SchemaExpr, inner: SchemaExpr) extends SchemaExpr {
    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      inner(value).flatMap(outer.apply)

    override def reverse: Option[SchemaExpr] =
      for {
        innerRev <- inner.reverse
        outerRev <- outer.reverse
      } yield Compose(innerRev, outerRev)
  }

  final case class PrimitiveConvert(from: PrimitiveType, to: PrimitiveType) extends SchemaExpr {
    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(pv) =>
        convertPrimitive(pv, from, to) match {
          case Right(converted) => Right(DynamicValue.Primitive(converted))
          case Left(err)        => Left(err)
        }
      case _ =>
        Left(MigrationError.typeMismatch(
          zio.blocks.schema.DynamicOptic.root,
          s"Primitive($from)",
          value.getClass.getSimpleName
        ))
    }

    override def reverse: Option[SchemaExpr] =
      if (canReverse(from, to)) Some(PrimitiveConvert(to, from))
      else None

    private def canReverse(f: PrimitiveType, t: PrimitiveType): Boolean =
      (f, t) match {
        case (PrimitiveType.String, _) => true
        case (_, PrimitiveType.String) => true
        case _                         => true
      }
  }

  case object WrapOption extends SchemaExpr {
    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(DynamicValue.Variant("Some", value))

    override def reverse: Option[SchemaExpr] = Some(UnwrapOption)
  }

  case object UnwrapOption extends SchemaExpr {
    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Variant("Some", inner) => Right(inner)
      case DynamicValue.Variant("None", _) =>
        Left(MigrationError.transformFailed(
          zio.blocks.schema.DynamicOptic.root,
          "Cannot unwrap None"
        ))
      case _ =>
        Left(MigrationError.typeMismatch(
          zio.blocks.schema.DynamicOptic.root,
          "Option (Variant)",
          value.getClass.getSimpleName
        ))
    }

    override def reverse: Option[SchemaExpr] = Some(WrapOption)
  }

  final case class GetField(name: String) extends SchemaExpr {
    override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        fields.find(_._1 == name) match {
          case Some((_, v)) => Right(v)
          case None =>
            Left(MigrationError.missingField(
              zio.blocks.schema.DynamicOptic.root,
              name
            ))
        }
      case _ =>
        Left(MigrationError.typeMismatch(
          zio.blocks.schema.DynamicOptic.root,
          "Record",
          value.getClass.getSimpleName
        ))
    }

    override def reverse: Option[SchemaExpr] = None
  }

  sealed trait StringExpr extends SchemaExpr

  object StringExpr {
    final case class Concat(separator: String, fields: Vector[String]) extends StringExpr {
      override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
        case DynamicValue.Record(recordFields) =>
          val values = fields.flatMap { name =>
            recordFields.find(_._1 == name).map(_._2)
          }
          val strings = values.collect {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
          }
          if (strings.length != fields.length)
            Left(MigrationError.transformFailed(
              zio.blocks.schema.DynamicOptic.root,
              s"Could not find all fields: $fields"
            ))
          else
            Right(DynamicValue.Primitive(PrimitiveValue.String(strings.mkString(separator))))
        case _ =>
          Left(MigrationError.typeMismatch(
            zio.blocks.schema.DynamicOptic.root,
            "Record",
            value.getClass.getSimpleName
          ))
      }

      override def reverse: Option[SchemaExpr] = Some(Split(separator, fields))
    }

    final case class Split(separator: String, targetFields: Vector[String]) extends StringExpr {
      override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val parts = s.split(java.util.regex.Pattern.quote(separator), targetFields.length).toVector
          if (parts.length != targetFields.length)
            Left(MigrationError.transformFailed(
              zio.blocks.schema.DynamicOptic.root,
              s"Split result has ${parts.length} parts but expected ${targetFields.length}"
            ))
          else {
            val fields = targetFields.zip(parts).map { case (name, part) =>
              (name, DynamicValue.Primitive(PrimitiveValue.String(part)))
            }
            Right(DynamicValue.Record(fields))
          }
        case _ =>
          Left(MigrationError.typeMismatch(
            zio.blocks.schema.DynamicOptic.root,
            "Primitive(String)",
            value.getClass.getSimpleName
          ))
      }

      override def reverse: Option[SchemaExpr] = Some(Concat(separator, targetFields))
    }

    case object ToUpperCase extends StringExpr {
      override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(s.toUpperCase)))
        case _ =>
          Left(MigrationError.typeMismatch(
            zio.blocks.schema.DynamicOptic.root,
            "Primitive(String)",
            value.getClass.getSimpleName
          ))
      }

      override def reverse: Option[SchemaExpr] = None
    }

    case object ToLowerCase extends StringExpr {
      override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(s.toLowerCase)))
        case _ =>
          Left(MigrationError.typeMismatch(
            zio.blocks.schema.DynamicOptic.root,
            "Primitive(String)",
            value.getClass.getSimpleName
          ))
      }

      override def reverse: Option[SchemaExpr] = None
    }

    final case class Trim(chars: Option[String]) extends StringExpr {
      override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val trimmed = chars match {
            case Some(c) => s.dropWhile(c.contains(_)).reverse.dropWhile(c.contains(_)).reverse
            case None    => s.trim
          }
          Right(DynamicValue.Primitive(PrimitiveValue.String(trimmed)))
        case _ =>
          Left(MigrationError.typeMismatch(
            zio.blocks.schema.DynamicOptic.root,
            "Primitive(String)",
            value.getClass.getSimpleName
          ))
      }

      override def reverse: Option[SchemaExpr] = None
    }
  }

  sealed trait NumericExpr extends SchemaExpr

  object NumericExpr {
    final case class Add(amount: Long) extends NumericExpr {
      override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int((n + amount).toInt)))
        case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(n + amount)))
        case _ =>
          Left(MigrationError.typeMismatch(
            zio.blocks.schema.DynamicOptic.root,
            "Primitive(Int/Long)",
            value.getClass.getSimpleName
          ))
      }

      override def reverse: Option[SchemaExpr] = Some(Add(-amount))
    }

    final case class Multiply(factor: Double) extends NumericExpr {
      override def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(n * factor)))
        case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(n * factor)))
        case DynamicValue.Primitive(PrimitiveValue.Float(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(n * factor)))
        case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(n * factor)))
        case _ =>
          Left(MigrationError.typeMismatch(
            zio.blocks.schema.DynamicOptic.root,
            "Primitive(numeric)",
            value.getClass.getSimpleName
          ))
      }

      override def reverse: Option[SchemaExpr] =
        if (factor != 0.0) Some(Multiply(1.0 / factor))
        else None
    }
  }

  sealed trait PrimitiveType

  object PrimitiveType {
    case object Boolean    extends PrimitiveType
    case object Byte       extends PrimitiveType
    case object Short      extends PrimitiveType
    case object Int        extends PrimitiveType
    case object Long       extends PrimitiveType
    case object Float      extends PrimitiveType
    case object Double     extends PrimitiveType
    case object Char       extends PrimitiveType
    case object String     extends PrimitiveType
    case object BigInt     extends PrimitiveType
    case object BigDecimal extends PrimitiveType
  }

  private def convertPrimitive(
    value: PrimitiveValue,
    from: PrimitiveType,
    to: PrimitiveType
  ): Either[MigrationError, PrimitiveValue] = {
    import PrimitiveType._

    def stringToNum(s: java.lang.String, target: PrimitiveType): Either[MigrationError, PrimitiveValue] =
      try target match {
        case Int     => Right(PrimitiveValue.Int(s.toInt))
        case Long    => Right(PrimitiveValue.Long(s.toLong))
        case Float   => Right(PrimitiveValue.Float(s.toFloat))
        case Double  => Right(PrimitiveValue.Double(s.toDouble))
        case Boolean => Right(PrimitiveValue.Boolean(s.toBoolean))
        case _ =>
          Left(MigrationError.transformFailed(
            zio.blocks.schema.DynamicOptic.root,
            s"Cannot convert String to $target"
          ))
      } catch {
        case _: NumberFormatException =>
          Left(MigrationError.transformFailed(
            zio.blocks.schema.DynamicOptic.root,
            s"Cannot parse '$s' as $target"
          ))
      }

    (value, to) match {
      case (PrimitiveValue.Boolean(b), String)    => Right(PrimitiveValue.String(b.toString))
      case (PrimitiveValue.Byte(n), String)       => Right(PrimitiveValue.String(n.toString))
      case (PrimitiveValue.Short(n), String)      => Right(PrimitiveValue.String(n.toString))
      case (PrimitiveValue.Int(n), String)        => Right(PrimitiveValue.String(n.toString))
      case (PrimitiveValue.Long(n), String)       => Right(PrimitiveValue.String(n.toString))
      case (PrimitiveValue.Float(n), String)      => Right(PrimitiveValue.String(n.toString))
      case (PrimitiveValue.Double(n), String)     => Right(PrimitiveValue.String(n.toString))
      case (PrimitiveValue.Char(c), String)       => Right(PrimitiveValue.String(c.toString))
      case (PrimitiveValue.String(s), String)     => Right(PrimitiveValue.String(s))
      case (PrimitiveValue.BigInt(n), String)     => Right(PrimitiveValue.String(n.toString))
      case (PrimitiveValue.BigDecimal(n), String) => Right(PrimitiveValue.String(n.toString))

      case (PrimitiveValue.String(s), target) => stringToNum(s, target)

      case (PrimitiveValue.Int(n), Long)   => Right(PrimitiveValue.Long(n.toLong))
      case (PrimitiveValue.Int(n), Double) => Right(PrimitiveValue.Double(n.toDouble))
      case (PrimitiveValue.Int(n), Float)  => Right(PrimitiveValue.Float(n.toFloat))
      case (PrimitiveValue.Long(n), Double)  => Right(PrimitiveValue.Double(n.toDouble))
      case (PrimitiveValue.Float(n), Double) => Right(PrimitiveValue.Double(n.toDouble))

      case (PrimitiveValue.Long(n), Int) if n >= scala.Int.MinValue && n <= scala.Int.MaxValue =>
        Right(PrimitiveValue.Int(n.toInt))

      case (PrimitiveValue.Double(n), Float) => Right(PrimitiveValue.Float(n.toFloat))

      case (PrimitiveValue.Double(n), Int) if n >= scala.Int.MinValue && n <= scala.Int.MaxValue && n == n.toInt.toDouble =>
        Right(PrimitiveValue.Int(n.toInt))

      case (PrimitiveValue.Double(n), Long) if n >= scala.Long.MinValue && n <= scala.Long.MaxValue && n == n.toLong.toDouble =>
        Right(PrimitiveValue.Long(n.toLong))

      case (PrimitiveValue.Boolean(b), Int) => Right(PrimitiveValue.Int(if (b) 1 else 0))
      case (PrimitiveValue.Int(n), Boolean) => Right(PrimitiveValue.Boolean(n != 0))

      case _ =>
        Left(MigrationError.transformFailed(zio.blocks.schema.DynamicOptic.root, s"Cannot convert $from to $to"))
    }
  }

  def identity: SchemaExpr = Identity

  def const[A](value: A)(implicit schema: Schema[A]): SchemaExpr =
    Constant(schema.toDynamicValue(value))

  def constDynamic(value: DynamicValue): SchemaExpr =
    Constant(value)

  def convert(from: PrimitiveType, to: PrimitiveType): SchemaExpr =
    PrimitiveConvert(from, to)

  def compose(outer: SchemaExpr, inner: SchemaExpr): SchemaExpr =
    Compose(outer, inner)

  def concat(separator: String, fields: String*): SchemaExpr =
    StringExpr.Concat(separator, fields.toVector)

  def split(separator: String, targetFields: String*): SchemaExpr =
    StringExpr.Split(separator, targetFields.toVector)

  def add(amount: Long): SchemaExpr =
    NumericExpr.Add(amount)

  def multiply(factor: Double): SchemaExpr =
    NumericExpr.Multiply(factor)
}
