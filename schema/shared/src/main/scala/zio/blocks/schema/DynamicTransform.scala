/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * A pure, serializable transformation from DynamicValue to DynamicValue.
 *
 * DynamicTransform enables migrations to express value-level transformations
 * without closures or user functions. All transformations are represented as
 * data that can be serialized, stored in registries, and applied dynamically.
 */
sealed trait DynamicTransform extends Product with Serializable {

  /**
   * Applies this transformation to a DynamicValue.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  /**
   * Returns the reverse transformation.
   */
  def reverse: DynamicTransform
}

object DynamicTransform extends MigrationSchemaInstances {

  /**
   * A transformation that always returns a constant value.
   */
  final case class Constant(value: DynamicValue) extends DynamicTransform {
    def apply(input: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
    def reverse: DynamicTransform                                        = this
  }

  /**
   * A transformation that returns the default value for a schema.
   */
  case object DefaultValue extends DynamicTransform {
    def apply(input: DynamicValue): Either[MigrationError, DynamicValue] =
      Left(MigrationError.defaultFailed("DefaultValue transform requires schema context"))
    def reverse: DynamicTransform = this
  }

  /**
   * String concatenation with no delimiter.
   */
  case object StringConcat extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Sequence(elements) =>
        elements.find {
          case DynamicValue.Primitive(PrimitiveValue.String(_)) => false
          case _                                                => true
        } match {
          case Some(nonString) =>
            Left(
              MigrationError.transformFailed(
                s"StringConcat requires String elements, found ${nonString.valueType}"
              )
            )
          case None =>
            val sb = new StringBuilder
            elements.foreach {
              case DynamicValue.Primitive(PrimitiveValue.String(s)) => sb.append(s)
              case _                                                => // already validated
            }
            Right(DynamicValue.Primitive(PrimitiveValue.String(sb.toString)))
        }
      case other =>
        Left(MigrationError.typeMismatch("Sequence of String", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringSplit("")
  }

  /**
   * String split transformation.
   */
  final case class StringSplit(delimiter: String) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        val parts =
          if (delimiter.isEmpty) s.map(_.toString).toVector
          else s.split(java.util.regex.Pattern.quote(delimiter)).toVector
        Right(
          DynamicValue.Sequence(
            Chunk.from(parts.map(part => DynamicValue.Primitive(PrimitiveValue.String(part))))
          )
        )
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringConcatWith(delimiter)
  }

  /**
   * String concatenation with a delimiter.
   */
  final case class StringConcatWith(delimiter: String) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Sequence(elements) =>
        elements.find {
          case DynamicValue.Primitive(PrimitiveValue.String(_)) => false
          case _                                                => true
        } match {
          case Some(nonString) =>
            Left(
              MigrationError.transformFailed(
                s"StringConcatWith requires String elements, found ${nonString.valueType}"
              )
            )
          case None =>
            val strings = elements.collect { case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
              s
            }
            Right(DynamicValue.Primitive(PrimitiveValue.String(strings.mkString(delimiter))))
        }
      case other =>
        Left(MigrationError.typeMismatch("Sequence of String", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringSplit(delimiter)
  }

  /**
   * Numeric addition transformation.
   */
  final case class NumericAdd(amount: DynamicValue) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      (value, amount) match {
        case (DynamicValue.Primitive(a: PrimitiveValue.Int), DynamicValue.Primitive(b: PrimitiveValue.Int)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(a.value + b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Long), DynamicValue.Primitive(b: PrimitiveValue.Long)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(a.value + b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Double), DynamicValue.Primitive(b: PrimitiveValue.Double)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(a.value + b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Float), DynamicValue.Primitive(b: PrimitiveValue.Float)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(a.value + b.value)))
        case (
              DynamicValue.Primitive(a: PrimitiveValue.BigDecimal),
              DynamicValue.Primitive(b: PrimitiveValue.BigDecimal)
            ) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(a.value + b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.BigInt), DynamicValue.Primitive(b: PrimitiveValue.BigInt)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigInt(a.value + b.value)))
        case _ =>
          Left(MigrationError.transformFailed("NumericAdd requires matching numeric types"))
      }
    def reverse: DynamicTransform = NumericSubtract(amount)
  }

  /**
   * Numeric subtraction transformation.
   */
  final case class NumericSubtract(amount: DynamicValue) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      (value, amount) match {
        case (DynamicValue.Primitive(a: PrimitiveValue.Int), DynamicValue.Primitive(b: PrimitiveValue.Int)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(a.value - b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Long), DynamicValue.Primitive(b: PrimitiveValue.Long)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(a.value - b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Double), DynamicValue.Primitive(b: PrimitiveValue.Double)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(a.value - b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Float), DynamicValue.Primitive(b: PrimitiveValue.Float)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(a.value - b.value)))
        case (
              DynamicValue.Primitive(a: PrimitiveValue.BigDecimal),
              DynamicValue.Primitive(b: PrimitiveValue.BigDecimal)
            ) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(a.value - b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.BigInt), DynamicValue.Primitive(b: PrimitiveValue.BigInt)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigInt(a.value - b.value)))
        case _ =>
          Left(MigrationError.transformFailed("NumericSubtract requires matching numeric types"))
      }
    def reverse: DynamicTransform = NumericAdd(amount)
  }

  /**
   * Numeric multiplication transformation.
   */
  final case class NumericMultiply(factor: DynamicValue) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      (value, factor) match {
        case (DynamicValue.Primitive(a: PrimitiveValue.Int), DynamicValue.Primitive(b: PrimitiveValue.Int)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(a.value * b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Long), DynamicValue.Primitive(b: PrimitiveValue.Long)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(a.value * b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Double), DynamicValue.Primitive(b: PrimitiveValue.Double)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(a.value * b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Float), DynamicValue.Primitive(b: PrimitiveValue.Float)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(a.value * b.value)))
        case (
              DynamicValue.Primitive(a: PrimitiveValue.BigDecimal),
              DynamicValue.Primitive(b: PrimitiveValue.BigDecimal)
            ) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(a.value * b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.BigInt), DynamicValue.Primitive(b: PrimitiveValue.BigInt)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigInt(a.value * b.value)))
        case _ =>
          Left(MigrationError.transformFailed("NumericMultiply requires matching numeric types"))
      }
    def reverse: DynamicTransform = NumericDivide(factor)
  }

  /**
   * Numeric division transformation.
   */
  final case class NumericDivide(divisor: DynamicValue) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      (value, divisor) match {
        case (DynamicValue.Primitive(a: PrimitiveValue.Int), DynamicValue.Primitive(b: PrimitiveValue.Int)) =>
          if (b.value == 0) Left(MigrationError.transformFailed("Division by zero"))
          else Right(DynamicValue.Primitive(PrimitiveValue.Int(a.value / b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Long), DynamicValue.Primitive(b: PrimitiveValue.Long)) =>
          if (b.value == 0L) Left(MigrationError.transformFailed("Division by zero"))
          else Right(DynamicValue.Primitive(PrimitiveValue.Long(a.value / b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Double), DynamicValue.Primitive(b: PrimitiveValue.Double)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(a.value / b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.Float), DynamicValue.Primitive(b: PrimitiveValue.Float)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(a.value / b.value)))
        case (
              DynamicValue.Primitive(a: PrimitiveValue.BigDecimal),
              DynamicValue.Primitive(b: PrimitiveValue.BigDecimal)
            ) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(a.value / b.value)))
        case (DynamicValue.Primitive(a: PrimitiveValue.BigInt), DynamicValue.Primitive(b: PrimitiveValue.BigInt)) =>
          if (b.value == 0) Left(MigrationError.transformFailed("Division by zero"))
          else Right(DynamicValue.Primitive(PrimitiveValue.BigInt(a.value / b.value)))
        case _ =>
          Left(MigrationError.transformFailed("NumericDivide requires matching numeric types"))
      }
    def reverse: DynamicTransform = NumericMultiply(divisor)
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Type Conversions
  // ═══════════════════════════════════════════════════════════════════════════════

  case object StringToInt extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Int(s.toInt)))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.transformFailed(s"Cannot convert '$s' to Int"))
        }
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = IntToString
  }

  case object IntToString extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(i.toString)))
      case other =>
        Left(MigrationError.typeMismatch("Int", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringToInt
  }

  case object StringToLong extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Long(s.toLong)))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.transformFailed(s"Cannot convert '$s' to Long"))
        }
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = LongToString
  }

  case object LongToString extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(l)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(l.toString)))
      case other =>
        Left(MigrationError.typeMismatch("Long", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringToLong
  }

  case object StringToDouble extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Double(s.toDouble)))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.transformFailed(s"Cannot convert '$s' to Double"))
        }
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = DoubleToString
  }

  case object DoubleToString extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(d.toString)))
      case other =>
        Left(MigrationError.typeMismatch("Double", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringToDouble
  }

  case object StringToBoolean extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toLowerCase match {
          case "true" | "yes" | "1" | "on"  => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          case "false" | "no" | "0" | "off" => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
          case _                            => Left(MigrationError.transformFailed(s"Cannot convert '$s' to Boolean"))
        }
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = BooleanToString
  }

  case object BooleanToString extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(b.toString)))
      case other =>
        Left(MigrationError.typeMismatch("Boolean", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringToBoolean
  }

  case object IntToLong extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(i.toLong)))
      case other =>
        Left(MigrationError.typeMismatch("Int", other.valueType.toString))
    }
    def reverse: DynamicTransform = LongToInt
  }

  case object LongToInt extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(l)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(l.toInt)))
      case other =>
        Left(MigrationError.typeMismatch("Long", other.valueType.toString))
    }
    def reverse: DynamicTransform = IntToLong
  }

  case object FloatToDouble extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Float(f)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(f.toDouble)))
      case other =>
        Left(MigrationError.typeMismatch("Float", other.valueType.toString))
    }
    def reverse: DynamicTransform = DoubleToFloat
  }

  case object DoubleToFloat extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Float(d.toFloat)))
      case other =>
        Left(MigrationError.typeMismatch("Double", other.valueType.toString))
    }
    def reverse: DynamicTransform = FloatToDouble
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // String Operations
  // ═══════════════════════════════════════════════════════════════════════════════

  case object StringUpperCase extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s.toUpperCase)))
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringLowerCase
  }

  case object StringLowerCase extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s.toLowerCase)))
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = StringUpperCase
  }

  case object StringTrim extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s.trim)))
      case other =>
        Left(MigrationError.typeMismatch("String", other.valueType.toString))
    }
    def reverse: DynamicTransform = this
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Option / Either Wrapping
  // ═══════════════════════════════════════════════════════════════════════════════

  case object WrapSome extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(DynamicValue.Variant("Some", DynamicValue.Record(Chunk("value" -> value))))
    def reverse: DynamicTransform = UnwrapOption(DynamicValue.Null)
  }

  final case class UnwrapOption(default: DynamicValue) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Variant("Some", DynamicValue.Record(fields)) =>
        fields.find(_._1 == "value") match {
          case Some((_, v)) => Right(v)
          case None         => Left(MigrationError.transformFailed("Malformed Some: missing 'value' field"))
        }
      case DynamicValue.Variant("None", _) => Right(default)
      case other                           =>
        Left(MigrationError.typeMismatch("Option", other.valueType.toString))
    }
    def reverse: DynamicTransform = WrapSome
  }

  case object WrapLeft extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(DynamicValue.Variant("Left", DynamicValue.Record(Chunk("value" -> value))))
    def reverse: DynamicTransform = this
  }

  case object WrapRight extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(DynamicValue.Variant("Right", DynamicValue.Record(Chunk("value" -> value))))
    def reverse: DynamicTransform = this
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Structural
  // ═══════════════════════════════════════════════════════════════════════════════

  case object Identity extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
    def reverse: DynamicTransform                                        = this
  }

  final case class Compose(first: DynamicTransform, second: DynamicTransform) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      first(value) match {
        case Right(v) => second(v)
        case Left(e)  => Left(e)
      }
    def reverse: DynamicTransform = Compose(second.reverse, first.reverse)
  }

  final case class MapElements(transform: DynamicTransform) extends DynamicTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Sequence(elements) =>
        val results = elements.map(transform(_))
        val errors  = results.collect { case Left(e) => e }
        if (errors.nonEmpty) Left(MigrationError.multiple(errors))
        else Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
      case other =>
        Left(MigrationError.typeMismatch("Sequence", other.valueType.toString))
    }
    def reverse: DynamicTransform = MapElements(transform.reverse)
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Schema Instance
  // ═══════════════════════════════════════════════════════════════════════════════

  implicit lazy val schema: Schema[DynamicTransform] = dynamicTransformSchema
}
