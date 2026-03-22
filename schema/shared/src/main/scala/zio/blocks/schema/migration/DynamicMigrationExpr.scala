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

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * A serializable, purely algebraic expression that transforms a `DynamicValue`.
 *
 * `DynamicMigrationExpr` is the value-level transformation language for
 * `DynamicMigration`. It contains only data (no functions, no closures, no
 * reflection), enabling full serializability and code generation.
 *
 * For the current version, only primitive-to-primitive conversions are
 * supported, consistent with the ticket's constraint that transforms must
 * produce primitives.
 */
sealed trait DynamicMigrationExpr {

  /**
   * Apply this expression to a `DynamicValue`, producing a transformed value or
   * a `MigrationError` if the input type is incompatible.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  /**
   * The structural reverse of this expression. For conversions, this is the
   * inverse conversion. For `Constant`, this is `Identity` (best-effort).
   */
  def reverse: DynamicMigrationExpr

  /**
   * Compose this expression with another, applying `this` first, then `that`.
   */
  final def andThen(that: DynamicMigrationExpr): DynamicMigrationExpr =
    DynamicMigrationExpr.Compose(this, that)
}

object DynamicMigrationExpr {

  /** Identity: passes the value through unchanged. */
  case object Identity extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = new Right(value)
    def reverse: DynamicMigrationExpr                                    = Identity
  }

  /**
   * Replace any input with a constant literal value. Reverse is `Identity`
   * (best-effort; the original value is not stored).
   */
  final case class Constant(value: DynamicValue) extends DynamicMigrationExpr {
    def apply(in: DynamicValue): Either[MigrationError, DynamicValue] = new Right(value)
    def reverse: DynamicMigrationExpr                                 = Identity
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Primitive-to-primitive conversions
  // ─────────────────────────────────────────────────────────────────────────

  /** Convert Int to Long. Reverse: LongToInt. */
  case object IntToLong extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Int) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Long(v.value.toLong)))
      case _ => new Left(typeError("Int", value))
    }
    def reverse: DynamicMigrationExpr = LongToInt
  }

  /** Convert Long to Int (may truncate). Reverse: IntToLong. */
  case object LongToInt extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Long) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Int(v.value.toInt)))
      case _ => new Left(typeError("Long", value))
    }
    def reverse: DynamicMigrationExpr = IntToLong
  }

  /** Convert Int to String. Reverse: StringToInt. */
  case object IntToString extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Int) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.String(v.value.toString)))
      case _ => new Left(typeError("Int", value))
    }
    def reverse: DynamicMigrationExpr = StringToInt
  }

  /** Convert String to Int. Reverse: IntToString. */
  case object StringToInt extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.String) =>
        try new Right(new DynamicValue.Primitive(new PrimitiveValue.Int(v.value.toInt)))
        catch { case _: NumberFormatException => new Left(MigrationError(s"Cannot parse '${v.value}' as Int")) }
      case _ => new Left(typeError("String", value))
    }
    def reverse: DynamicMigrationExpr = IntToString
  }

  /** Convert Long to String. Reverse: StringToLong. */
  case object LongToString extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Long) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.String(v.value.toString)))
      case _ => new Left(typeError("Long", value))
    }
    def reverse: DynamicMigrationExpr = StringToLong
  }

  /** Convert String to Long. Reverse: LongToString. */
  case object StringToLong extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.String) =>
        try new Right(new DynamicValue.Primitive(new PrimitiveValue.Long(v.value.toLong)))
        catch { case _: NumberFormatException => new Left(MigrationError(s"Cannot parse '${v.value}' as Long")) }
      case _ => new Left(typeError("String", value))
    }
    def reverse: DynamicMigrationExpr = LongToString
  }

  /** Convert Double to String. Reverse: StringToDouble. */
  case object DoubleToString extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Double) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.String(v.value.toString)))
      case _ => new Left(typeError("Double", value))
    }
    def reverse: DynamicMigrationExpr = StringToDouble
  }

  /** Convert String to Double. Reverse: DoubleToString. */
  case object StringToDouble extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.String) =>
        try new Right(new DynamicValue.Primitive(new PrimitiveValue.Double(v.value.toDouble)))
        catch { case _: NumberFormatException => new Left(MigrationError(s"Cannot parse '${v.value}' as Double")) }
      case _ => new Left(typeError("String", value))
    }
    def reverse: DynamicMigrationExpr = DoubleToString
  }

  /** Convert Float to Double. Reverse: DoubleToFloat. */
  case object FloatToDouble extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Float) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Double(v.value.toDouble)))
      case _ => new Left(typeError("Float", value))
    }
    def reverse: DynamicMigrationExpr = DoubleToFloat
  }

  /** Convert Double to Float (may lose precision). Reverse: FloatToDouble. */
  case object DoubleToFloat extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Double) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.Float(v.value.toFloat)))
      case _ => new Left(typeError("Double", value))
    }
    def reverse: DynamicMigrationExpr = FloatToDouble
  }

  /** Convert Boolean to String ("true"/"false"). Reverse: StringToBoolean. */
  case object BooleanToString extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.Boolean) =>
        new Right(new DynamicValue.Primitive(new PrimitiveValue.String(v.value.toString)))
      case _ => new Left(typeError("Boolean", value))
    }
    def reverse: DynamicMigrationExpr = StringToBoolean
  }

  /** Convert String ("true"/"false") to Boolean. Reverse: BooleanToString. */
  case object StringToBoolean extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(v: PrimitiveValue.String) =>
        v.value.toLowerCase match {
          case "true"  => new Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(true)))
          case "false" => new Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))
          case s       => new Left(MigrationError(s"Cannot parse '$s' as Boolean (expected 'true' or 'false')"))
        }
      case _ => new Left(typeError("String", value))
    }
    def reverse: DynamicMigrationExpr = BooleanToString
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Structural expressions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Concatenate multiple string fields from the parent record into a single
   * string. This expression is applied to the parent Record and extracts the
   * named fields, joining them with the given separator.
   *
   * Example: `ConcatFields(Vector("firstName", "lastName"), " ")` on a record
   * `{firstName: "John", lastName: "Doe"}` produces `"John Doe"`.
   */
  final case class ConcatFields(fields: Vector[String], separator: String) extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case rec: DynamicValue.Record =>
        val parts = new Array[String](fields.length)
        var idx   = 0
        while (idx < fields.length) {
          val fieldName = fields(idx)
          rec.fields.indexWhere(_._1 == fieldName) match {
            case -1 =>
              return new Left(MigrationError(s"ConcatFields: field '$fieldName' not found in record"))
            case fi =>
              rec.fields(fi)._2 match {
                case DynamicValue.Primitive(v: PrimitiveValue.String) =>
                  parts(idx) = v.value
                case other =>
                  return new Left(
                    MigrationError(
                      s"ConcatFields: field '$fieldName' is not a String but ${other.getClass.getSimpleName}"
                    )
                  )
              }
          }
          idx += 1
        }
        new Right(new DynamicValue.Primitive(new PrimitiveValue.String(parts.mkString(separator))))
      case _ =>
        new Left(MigrationError(s"ConcatFields requires a Record but got ${value.getClass.getSimpleName}"))
    }

    /** Reverse is Identity (best-effort: cannot split a joined string back). */
    def reverse: DynamicMigrationExpr = Identity
  }

  /**
   * Compose two expressions sequentially: apply `first`, then `second` to the
   * result.
   */
  final case class Compose(first: DynamicMigrationExpr, second: DynamicMigrationExpr) extends DynamicMigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      first(value).flatMap(second.apply)
    def reverse: DynamicMigrationExpr = Compose(second.reverse, first.reverse)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private[this] def typeError(expected: String, value: DynamicValue): MigrationError =
    MigrationError(s"Expected $expected primitive but got ${value.getClass.getSimpleName}")
}
