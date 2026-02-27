package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * A pure, serializable specification for transforming [[DynamicValue]]
 * instances. Contains no user functions or closures—only data describing the
 * transformation.
 */
sealed trait DynamicValueTransform {

  /**
   * Applies this transformation to a [[DynamicValue]].
   *
   * @return
   *   Right with the transformed value, or Left with an error
   */
  def apply(input: DynamicValue): Either[String, DynamicValue]
}

object DynamicValueTransform {

  /** Replaces the input with a constant value, ignoring the input entirely. */
  final case class Constant(value: DynamicValue) extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = Right(value)
  }

  /** Returns the input unchanged (identity transformation). */
  case object Identity extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = Right(input)
  }

  /**
   * Converts a numeric primitive to a string.
   */
  case object NumericToString extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = input match {
      case p: DynamicValue.Primitive =>
        val str = p.value match {
          case v: PrimitiveValue.Byte       => v.value.toString
          case v: PrimitiveValue.Short      => v.value.toString
          case v: PrimitiveValue.Int        => v.value.toString
          case v: PrimitiveValue.Long       => v.value.toString
          case v: PrimitiveValue.Float      => v.value.toString
          case v: PrimitiveValue.Double     => v.value.toString
          case v: PrimitiveValue.BigDecimal => v.value.toString
          case v: PrimitiveValue.String     => v.value
          case other                        => other.toString
        }
        Right(new DynamicValue.Primitive(new PrimitiveValue.String(str)))
      case _ => Left(s"NumericToString: expected Primitive, got ${input.valueType}")
    }
  }

  /**
   * Parses a string primitive into an integer.
   */
  case object StringToInt extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = input match {
      case p: DynamicValue.Primitive =>
        p.value match {
          case s: PrimitiveValue.String =>
            try Right(new DynamicValue.Primitive(new PrimitiveValue.Int(s.value.toInt)))
            catch { case _: NumberFormatException => Left(s"StringToInt: cannot parse '${s.value}' as Int") }
          case other => Left(s"StringToInt: expected String primitive, got $other")
        }
      case _ => Left(s"StringToInt: expected Primitive, got ${input.valueType}")
    }
  }

  /**
   * Parses a string primitive into a long.
   */
  case object StringToLong extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = input match {
      case p: DynamicValue.Primitive =>
        p.value match {
          case s: PrimitiveValue.String =>
            try Right(new DynamicValue.Primitive(new PrimitiveValue.Long(s.value.toLong)))
            catch { case _: NumberFormatException => Left(s"StringToLong: cannot parse '${s.value}' as Long") }
          case other => Left(s"StringToLong: expected String primitive, got $other")
        }
      case _ => Left(s"StringToLong: expected Primitive, got ${input.valueType}")
    }
  }

  /**
   * Converts an integer primitive to a long.
   */
  case object IntToLong extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = input match {
      case p: DynamicValue.Primitive =>
        p.value match {
          case i: PrimitiveValue.Int => Right(new DynamicValue.Primitive(new PrimitiveValue.Long(i.value.toLong)))
          case other                 => Left(s"IntToLong: expected Int primitive, got $other")
        }
      case _ => Left(s"IntToLong: expected Primitive, got ${input.valueType}")
    }
  }

  /**
   * Converts a long primitive to an integer (may lose precision).
   */
  case object LongToInt extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = input match {
      case p: DynamicValue.Primitive =>
        p.value match {
          case l: PrimitiveValue.Long => Right(new DynamicValue.Primitive(new PrimitiveValue.Int(l.value.toInt)))
          case other                  => Left(s"LongToInt: expected Long primitive, got $other")
        }
      case _ => Left(s"LongToInt: expected Primitive, got ${input.valueType}")
    }
  }

  /**
   * Concatenates string values from multiple record fields.
   *
   * @param fieldNames
   *   the field names to concatenate
   * @param separator
   *   the separator between field values
   */
  final case class ConcatFields(fieldNames: Vector[String], separator: String) extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = input match {
      case r: DynamicValue.Record =>
        val fieldMap = r.fields.toMap
        val parts    = fieldNames.flatMap { name =>
          fieldMap.get(name).flatMap {
            case p: DynamicValue.Primitive =>
              p.value match {
                case s: PrimitiveValue.String => Some(s.value)
                case other                    => Some(other.toString)
              }
            case _ => None
          }
        }
        Right(new DynamicValue.Primitive(new PrimitiveValue.String(parts.mkString(separator))))
      case _ => Left(s"ConcatFields: expected Record, got ${input.valueType}")
    }
  }

  /**
   * Splits a string primitive by a separator into named fields, producing a
   * [[DynamicValue.Record]].
   *
   * @param separator
   *   the separator to split on
   * @param fieldNames
   *   the names for the resulting fields (in order of split parts)
   */
  final case class SplitString(separator: String, fieldNames: Vector[String]) extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] = input match {
      case p: DynamicValue.Primitive =>
        p.value match {
          case s: PrimitiveValue.String =>
            val parts   = s.value.split(java.util.regex.Pattern.quote(separator), fieldNames.length)
            val builder = Vector.newBuilder[(String, DynamicValue)]
            builder.sizeHint(fieldNames.length)
            var i = 0
            while (i < fieldNames.length) {
              val value = if (i < parts.length) parts(i) else ""
              builder.addOne((fieldNames(i), new DynamicValue.Primitive(new PrimitiveValue.String(value))))
              i += 1
            }
            Right(new DynamicValue.Record(Chunk.fromIterable(builder.result())))
          case other => Left(s"SplitString: expected String primitive, got $other")
        }
      case _ => Left(s"SplitString: expected Primitive, got ${input.valueType}")
    }
  }

  /**
   * Applies a sequence of transformations in order.
   */
  final case class Compose(transforms: Vector[DynamicValueTransform]) extends DynamicValueTransform {
    def apply(input: DynamicValue): Either[String, DynamicValue] =
      transforms.foldLeft[Either[String, DynamicValue]](Right(input)) {
        case (Right(v), t) => t(v)
        case (left, _)     => left
      }
  }
}
