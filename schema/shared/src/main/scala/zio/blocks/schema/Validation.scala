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

/**
 * A `Validation` is a declarative constraint on a primitive value, such as
 * "positive" or "matches a pattern". It is stored in the [[PrimitiveType]] of a
 * primitive schema.
 *
 * Only `DynamicSchema#check` and `DynamicSchema#conforms` enforce it. The JSON
 * codec, `Schema#fromDynamicValue` and `Schema#toJsonSchema` ignore it, and
 * `Schema.derived` never adds one. To reject values while decoding, check them
 * in the `to` function of `Schema#transform` instead.
 *
 * {{{
 * import zio.blocks.schema.binding.Binding
 *
 * val pageCountType = PrimitiveType.Int(Validation.Numeric.Positive)
 * val pageCount: Schema[Int] =
 *   new Schema(new Reflect.Primitive[Binding, Int](pageCountType, pageCountType.typeId, Binding.Primitive.int))
 *
 * val checked = pageCount.toDynamicSchema.check(DynamicValue.int(-5))
 * // Some(zio.blocks.schema.SchemaError: Validation failed: value must be positive)
 * val decoded = pageCount.fromDynamicValue(DynamicValue.int(-5)) // Right(-5)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/validation/ Validation]]
 */
sealed trait Validation[+A]

/** The built-in constraints. */
object Validation {

  /** No constraint. */
  case object None extends Validation[Nothing]

  /**
   * A constraint on a `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`,
   * `BigInt` or `BigDecimal` value.
   */
  sealed trait Numeric[+A] extends Validation[A]

  /** The numeric constraints. */
  object Numeric {

    /** The value is greater than zero. */
    case object Positive extends Numeric[Nothing]

    /** The value is less than zero. */
    case object Negative extends Numeric[Nothing]

    /** The value is zero or less. */
    case object NonPositive extends Numeric[Nothing]

    /** The value is zero or greater. */
    case object NonNegative extends Numeric[Nothing]

    /**
     * The value is at least `min` and at most `max`; `None` leaves that side
     * open.
     */
    case class Range[A](min: Option[A], max: Option[A]) extends Numeric[A]

    /** The value is one of `values`. */
    case class Set[A](values: scala.collection.immutable.Set[A]) extends Numeric[A]
  }

  /** A constraint on a `String` value. */
  sealed trait String extends Validation[Predef.String]

  /** The string constraints. */
  object String {

    /** The string has at least one character. */
    case object NonEmpty extends String

    /** The string has no characters. */
    case object Empty extends String

    /**
     * The string is empty or holds only whitespace (it is empty after `trim`).
     */
    case object Blank extends String

    /**
     * The string holds a character other than whitespace (it is not empty after
     * `trim`).
     */
    case object NonBlank extends String

    /**
     * The length of the string is at least `min` and at most `max`; `None`
     * leaves that side open.
     */
    case class Length(min: Option[scala.Int], max: Option[scala.Int]) extends String

    /**
     * The whole string matches the regular expression `regex`. It is checked
     * with `String#matches`, so the pattern needs no `^` or `$`.
     */
    case class Pattern(regex: Predef.String) extends String
  }
}
