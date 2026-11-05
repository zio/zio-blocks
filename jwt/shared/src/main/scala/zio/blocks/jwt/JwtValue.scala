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

package zio.blocks.jwt

import zio.blocks.chunk.Chunk

/**
 * Public JSON value ADT for JWT custom claims.
 *
 * Mirrors JSON value kinds: string, number, boolean, null, array, object.
 * Numbers preserve their original JSON text to avoid loss of precision or
 * formatting. Nested objects and arrays are fully supported; no values are
 * silently dropped during parsing.
 */
sealed trait JwtValue extends Product with Serializable

/**
 * The cases of [[JwtValue]]: `Str`, `Num`, `Bool`, `Null`, `Arr` and `Obj`.
 * `Arr` and `Obj` nest, so a claim can hold any JSON structure.
 */
object JwtValue {

  /** JSON string value. */
  final case class Str(value: String) extends JwtValue

  /**
   * JSON number value, preserved as the original JSON text (e.g. "1.5", "1e9",
   * "-42"). Use `toLong`, `toDouble`, or `bigDecimal` to interpret numerically.
   */
  final case class Num(raw: String) extends JwtValue {

    /** Parses the raw text as a BigDecimal. */
    def bigDecimal: scala.BigDecimal = scala.BigDecimal(raw)

    /** Parses the raw text as a Double (may be Infinity if out of range). */
    def toDouble: Double = raw.toDouble
  }

  /**
   * Factory methods that build a [[JwtValue.Num]] from a Scala number. They
   * store the number's `toString` text, so `fromDouble(1e21)` stores `1.0E21`.
   * The `Num(raw)` constructor stores its argument as given, without checking
   * that it is a JSON number.
   */
  object Num {

    /** Creates a Num from a Long. */
    def fromLong(value: Long): Num = Num(value.toString)

    /** Creates a Num from a Double (finite values only). */
    def fromDouble(value: Double): Num = {
      require(!value.isNaN && !value.isInfinite, "NaN and Infinity are not valid JSON numbers")
      Num(value.toString)
    }

    /** Creates a Num from a BigDecimal. */
    def fromBigDecimal(value: scala.BigDecimal): Num = Num(value.toString)
  }

  /** JSON boolean value. */
  final case class Bool(value: Boolean) extends JwtValue

  /** JSON null value. */
  case object Null extends JwtValue

  /** JSON array value. */
  final case class Arr(items: Chunk[JwtValue]) extends JwtValue

  /** Constructors for [[JwtValue.Arr]]. */
  object Arr {

    /** An array with no items. */
    val empty: Arr = Arr(Chunk.empty)

    /** Creates an array that holds `items` in the given order. */
    def apply(items: JwtValue*): Arr = new Arr(Chunk.from(items))
  }

  /** JSON object value (unordered map of fields). */
  final case class Obj(fields: Map[String, JwtValue]) extends JwtValue

  /** Constructors for [[JwtValue.Obj]]. */
  object Obj {

    /** An object with no fields. */
    val empty: Obj = Obj(Map.empty)
  }
}
