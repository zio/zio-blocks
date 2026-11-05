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

package zio.blocks.sql

import java.time._
import java.util.UUID
import scala.language.implicitConversions
import zio.blocks.maybe.Maybe

/**
 * Turns a Scala value into the [[DbValue]] that `sql"..."` binds to a `?`
 * placeholder. The interpolator looks up a `DbParam` for the type of each
 * interpolated value at compile time; the companion has instances for the
 * primitives, `String`, `BigDecimal`, byte arrays, the `java.time` types,
 * `UUID`, `Option`, `Maybe`, and any type with a one-column [[DbCodec]]. Give
 * your own type an instance to interpolate it:
 *
 * {{{
 * final case class Sku(code: String)
 * given DbParam[Sku] with {
 *   def toDbValue(sku: Sku): DbValue = DbValue.DbString(sku.code.toUpperCase)
 * }
 *
 * val sku = Sku("kb-01")
 * sql"SELECT price FROM product WHERE sku = \$sku".params // Vector(DbString(KB-01))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-param/ DbParam]]
 */
trait DbParam[A] {

  /** Returns the value to bind for `value`. */
  def toDbValue(value: A): DbValue
}

/**
 * The `DbParam` instances the `sql"..."` interpolator finds without an import.
 */
object DbParam {

  /** Returns the `DbParam[A]` in scope. */
  def apply[A](implicit p: DbParam[A]): DbParam[A] = p

  /** Binds an `Int` as `DbInt`. */
  given DbParam[Int] with {
    def toDbValue(v: Int): DbValue = DbValue.DbInt(v)
  }

  /** Binds a `Long` as `DbLong`. */
  given DbParam[Long] with {
    def toDbValue(v: Long): DbValue = DbValue.DbLong(v)
  }

  /** Binds a `Double` as `DbDouble`. */
  given DbParam[Double] with {
    def toDbValue(v: Double): DbValue = DbValue.DbDouble(v)
  }

  /** Binds a `Float` as `DbFloat`. */
  given DbParam[Float] with {
    def toDbValue(v: Float): DbValue = DbValue.DbFloat(v)
  }

  /** Binds a `Boolean` as `DbBoolean`. */
  given DbParam[Boolean] with {
    def toDbValue(v: Boolean): DbValue = DbValue.DbBoolean(v)
  }

  /** Binds a `String` as `DbString`. */
  given DbParam[String] with {
    def toDbValue(v: String): DbValue = DbValue.DbString(v)
  }

  /** Binds a `Short` as `DbShort`. */
  given DbParam[Short] with {
    def toDbValue(v: Short): DbValue = DbValue.DbShort(v)
  }

  /** Binds a `Byte` as `DbByte`. */
  given DbParam[Byte] with {
    def toDbValue(v: Byte): DbValue = DbValue.DbByte(v)
  }

  /** Binds a `BigDecimal` as `DbBigDecimal`. */
  given DbParam[BigDecimal] with {
    def toDbValue(v: BigDecimal): DbValue = DbValue.DbBigDecimal(v)
  }

  /** Binds a byte array as `DbBytes`. */
  given dbParamBytes: DbParam[Array[Byte]] with {
    def toDbValue(v: Array[Byte]): DbValue = DbValue.DbBytes(v)
  }

  /** Binds a `LocalDate` as `DbLocalDate`. */
  given DbParam[LocalDate] with {
    def toDbValue(v: LocalDate): DbValue = DbValue.DbLocalDate(v)
  }

  /** Binds a `LocalDateTime` as `DbLocalDateTime`. */
  given DbParam[LocalDateTime] with {
    def toDbValue(v: LocalDateTime): DbValue = DbValue.DbLocalDateTime(v)
  }

  /** Binds a `LocalTime` as `DbLocalTime`. */
  given DbParam[LocalTime] with {
    def toDbValue(v: LocalTime): DbValue = DbValue.DbLocalTime(v)
  }

  /** Binds an `Instant` as `DbInstant`. */
  given DbParam[Instant] with {
    def toDbValue(v: Instant): DbValue = DbValue.DbInstant(v)
  }

  /**
   * Binds a `java.time.Duration` as `DbDuration`, which JDBC receives as its
   * ISO-8601 text.
   */
  given DbParam[Duration] with {
    def toDbValue(v: Duration): DbValue = DbValue.DbDuration(v)
  }

  /** Binds a `UUID` as `DbUUID`. */
  given DbParam[UUID] with {
    def toDbValue(v: UUID): DbValue = DbValue.DbUUID(v)
  }

  /** Binds a [[DbValue]] as it is. */
  given DbParam[DbValue] with {
    def toDbValue(v: DbValue): DbValue = v
  }

  /** Binds `Some(a)` as `a` would be bound and `None` as SQL `NULL`. */
  given [A](using inner: DbParam[A]): DbParam[Option[A]] with {
    def toDbValue(v: Option[A]): DbValue = v match {
      case Some(a) => inner.toDbValue(a)
      case None    => DbValue.DbNull
    }
  }

  /**
   * Binds a present value as it would be bound and an absent one as SQL `NULL`.
   */
  given [A](using inner: DbParam[A]): DbParam[Maybe[A]] with {
    def toDbValue(v: Maybe[A]): DbValue =
      if (v.isAbsent) DbValue.DbNull
      else inner.toDbValue(v.asInstanceOf[A])
  }

  /**
   * Binds a value of any type whose [[DbCodec]] writes exactly one column, such
   * as a single-field case class. A `DbCodec` is derived on the spot when none
   * is in scope, which has two known bugs in 0.0.56: a `List` or `Set` becomes
   * one JSON string (so `IN (\$values)` compiles and matches nothing), and a
   * newtype with a private constructor finds no codec although its `Schema` is
   * in scope.
   *
   * @throws java.lang.IllegalArgumentException
   *   when the fragment is built, if the codec does not write exactly one
   *   column: a case class with several fields, or `Object` or a class without
   *   constructor parameters, whose derived codec writes none
   */
  given fromDbCodec[A](using codec: DbCodec[A]): DbParam[A] with {
    def toDbValue(v: A): DbValue = {
      val values = codec.toDbValues(v)
      if (values.size == 1) values.head
      else
        throw new IllegalArgumentException(
          s"Cannot use multi-column type as a single SQL parameter (got ${values.size} columns). " +
            "Expand the value into multiple placeholders instead."
        )
    }
  }
}

extension (inline sc: StringContext) {

  /**
   * Builds a [[Frag]] from SQL text with interpolated values. Each value is
   * turned into a bound `?` parameter through its [[DbParam]] (a [[DbValue]] is
   * bound as it is), while an interpolated [[SqlLiteral]] is spliced into the
   * text as written and an interpolated [[Frag]] is spliced in with its own
   * parameters. A value with none of these fails to compile with `No
   * DbParam/Frag/SqlLiteral/DbValue instance found for type ...`.
   *
   * {{{
   * val country   = "NO"
   * val minOrders = 3
   * val email: Option[String] = None
   * val frag = sql"SELECT name FROM customer WHERE country = \$country AND orders >= \$minOrders AND email IS \$email"
   *
   * frag.sql(SqlDialect.SQLite) // SELECT name FROM customer WHERE country = ? AND orders >= ? AND email IS ?
   * frag.params                 // Vector(DbString(NO), DbInt(3), DbNull)
   * }}}
   *
   * At compile time it rejects empty SQL (`sql interpolator requires a
   * non-empty SQL string`), an unclosed quote (`Unclosed single quote in SQL
   * template`) and unbalanced parentheses (`Unbalanced parentheses in SQL:
   * unclosed '('`). It does not check table or column names; the overload that
   * takes a [[Table]] does. The text is used raw, without processing escape
   * sequences, so `\t` reaches the database as a backslash and a `t`: a known
   * bug in 0.0.56.
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/frag/ Frag]]
   */
  inline def sql(inline args: Any*): Frag =
    ${ SqlMacros.sqlImpl('sc, '{ Seq.empty[Table[?]] }, 'args) }

  /**
   * Builds a [[Frag]] like `sql"..."` and also checks, at compile time, every
   * identifier in the SQL text against the given tables' names and columns and
   * an allow-list of SQL keywords and functions. Each unknown word is a compile
   * error such as `Unknown identifier 'nme' at position 7; did you mean
   * 'name'?`; at most five are reported. Pass at least one table; the
   * interpolated values come in the second argument list.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Customer(id: Int, name: String, city: String)
   * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
   *
   * val customers = Table.derived[Customer]
   * val city      = "Lima"
   * val frag      = StringContext("SELECT id, name FROM customer WHERE city = ", "").sql(customers)(city)
   *
   * frag.sql(SqlDialect.SQLite) // SELECT id, name FROM customer WHERE city = ?
   * }}}
   *
   * Text inside single quotes and interpolated values are not checked, so an
   * interpolated [[SqlLiteral]] or [[Frag]] is the way to write SQL the check
   * rejects; a double-quoted identifier is checked like a bare word. An alias
   * is known only when it is introduced with `AS` (`FROM customer AS c`). The
   * known names are taken from the table's type when the code compiles, which
   * has three known bugs in 0.0.56: a column renamed with `@Modifier.rename` is
   * rejected under its real name and accepted under the field's name, a table
   * named by `TableNamingPolicy.Plural` is known only by its type's singular
   * name, and a misspelled column can be offered an SQL keyword as its
   * suggestion.
   *
   * @see
   *   [[https://zioblocks.com/guides/sql-checked-interpolation/ sql(table) Checked Interpolation]]
   */
  inline def sql(inline first: Table[?], inline rest: Table[?]*)(inline args: Any*): Frag =
    ${ SqlMacros.sqlImplChecked('sc, 'first, 'rest, 'args) }

}
