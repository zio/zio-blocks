package zio.schema.migration

import zio.schema._

/**
 * A serializable representation of data transformations. Unlike arbitrary
 * functions, these can be serialized and stored.
 */
sealed trait SerializableTransformation {
  def apply(value: DynamicValue): Either[String, DynamicValue]
}

object SerializableTransformation {

  /** Convert string to uppercase */
  case object Uppercase extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(s: String, _) =>
        Right(DynamicValue.Primitive(s.toUpperCase, StandardType.StringType))
      case _ =>
        Left("Expected String value for Uppercase transformation")
    }
  }

  /** Convert string to lowercase */
  case object Lowercase extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(s: String, _) =>
        Right(DynamicValue.Primitive(s.toLowerCase, StandardType.StringType))
      case _ =>
        Left("Expected String value for Lowercase transformation")
    }
  }

  /** Add a constant to a numeric value */
  case class AddConstant(n: Int) extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(i: Int, _) =>
        Right(DynamicValue.Primitive(i + n, StandardType.IntType))
      case DynamicValue.Primitive(l: Long, _) =>
        Right(DynamicValue.Primitive(l + n, StandardType.LongType))
      case DynamicValue.Primitive(d: Double, _) =>
        Right(DynamicValue.Primitive(d + n, StandardType.DoubleType))
      case _ =>
        Left(s"Expected numeric value for AddConstant transformation, got ${value.getClass.getSimpleName}")
    }
  }

  /** Multiply a numeric value by a constant */
  case class MultiplyBy(n: Double) extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(i: Int, _) =>
        Right(DynamicValue.Primitive((i * n).toInt, StandardType.IntType))
      case DynamicValue.Primitive(l: Long, _) =>
        Right(DynamicValue.Primitive((l * n).toLong, StandardType.LongType))
      case DynamicValue.Primitive(d: Double, _) =>
        Right(DynamicValue.Primitive(d * n, StandardType.DoubleType))
      case _ =>
        Left(s"Expected numeric value for MultiplyBy transformation")
    }
  }

  /** Convert Int to String */
  case object IntToString extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(i: Int, _) =>
        Right(DynamicValue.Primitive(i.toString, StandardType.StringType))
      case _ =>
        Left("Expected Int value for IntToString transformation")
    }
  }

  /** Convert String to Int (with validation) */
  case object StringToInt extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(s: String, _) =>
        s.toIntOption match {
          case Some(i) => Right(DynamicValue.Primitive(i, StandardType.IntType))
          case None    => Left(s"Cannot convert '$s' to Int")
        }
      case _ =>
        Left("Expected String value for StringToInt transformation")
    }
  }

  /** Replace empty strings with a default value */
  case class ReplaceEmptyString(default: String) extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive("", _) =>
        Right(DynamicValue.Primitive(default, StandardType.StringType))
      case other => Right(other)
    }
  }

  /** Negate a boolean */
  case object Negate extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(b: Boolean, _) =>
        Right(DynamicValue.Primitive(!b, StandardType.BoolType))
      case _ =>
        Left("Expected Boolean value for Negate transformation")
    }
  }

  /** Chain multiple transformations */
  case class Chain(transformations: List[SerializableTransformation]) extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] =
      transformations.foldLeft[Either[String, DynamicValue]](Right(value)) {
        case (Right(v), transformation) => transformation(v)
        case (left, _)                  => left
      }
  }

  /** Identity transformation (no-op) */
  case object Identity extends SerializableTransformation {
    def apply(value: DynamicValue): Either[String, DynamicValue] = Right(value)
  }

  // Schema for serialization
  implicit val schema: Schema[SerializableTransformation] = DeriveSchema.gen[SerializableTransformation]
}
