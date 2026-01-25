package zio.blocks.schema.json

import java.time._
import java.util.{Currency, UUID}

/**
 * A type class that witnesses a type `A` can be converted to a `String`
 * representation suitable for use in JSON contexts (keys and string literals).
 *
 * The "stringable" types are those defined in `PrimitiveType`:
 *   - Scala primitives: Unit, Boolean, Byte, Short, Int, Long, Float, Double,
 *     Char, String
 *   - Arbitrary precision: BigInt, BigDecimal
 *   - Java Time: DayOfWeek, Duration, Instant, LocalDate, LocalDateTime,
 *     LocalTime, Month, MonthDay, OffsetDateTime, OffsetTime, Period, Year,
 *     YearMonth, ZoneId, ZoneOffset, ZonedDateTime
 *   - Java Util: Currency, UUID
 *
 * This type class is used for compile-time validation in the JSON string
 * interpolator to ensure only appropriate types are used in key positions and
 * inside string literals.
 */
trait Stringable[-A] {

  /**
   * Converts the value to its string representation.
   */
  def stringify(a: A): String
}

object Stringable {

  /**
   * Summons a Stringable instance for type A.
   */
  def apply[A](implicit ev: Stringable[A]): Stringable[A] = ev

  /**
   * Creates a Stringable instance from a function.
   */
  def instance[A](f: A => String): Stringable[A] = new Stringable[A] {
    def stringify(a: A): String = f(a)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Scala Primitives
  // ─────────────────────────────────────────────────────────────────────────

  implicit val stringStringable: Stringable[String] = instance(identity)

  implicit val booleanStringable: Stringable[Boolean] = instance(_.toString)

  implicit val byteStringable: Stringable[Byte] = instance(_.toString)

  implicit val shortStringable: Stringable[Short] = instance(_.toString)

  implicit val intStringable: Stringable[Int] = instance(_.toString)

  implicit val longStringable: Stringable[Long] = instance(_.toString)

  implicit val floatStringable: Stringable[Float] = instance(java.lang.Float.toString)

  implicit val doubleStringable: Stringable[Double] = instance(java.lang.Double.toString)

  implicit val charStringable: Stringable[Char] = instance(_.toString)

  implicit val unitStringable: Stringable[Unit] = instance(_ => "()")

  // ─────────────────────────────────────────────────────────────────────────
  // Arbitrary Precision
  // ─────────────────────────────────────────────────────────────────────────

  implicit val bigIntStringable: Stringable[BigInt] = instance(_.toString)

  implicit val bigDecimalStringable: Stringable[BigDecimal] = instance(_.toString)

  // ─────────────────────────────────────────────────────────────────────────
  // Java Time
  // ─────────────────────────────────────────────────────────────────────────

  implicit val dayOfWeekStringable: Stringable[DayOfWeek] = instance(_.toString)

  implicit val durationStringable: Stringable[Duration] = instance(_.toString)

  implicit val instantStringable: Stringable[Instant] = instance(_.toString)

  implicit val localDateStringable: Stringable[LocalDate] = instance(_.toString)

  implicit val localDateTimeStringable: Stringable[LocalDateTime] = instance(_.toString)

  implicit val localTimeStringable: Stringable[LocalTime] = instance(_.toString)

  implicit val monthStringable: Stringable[Month] = instance(_.toString)

  implicit val monthDayStringable: Stringable[MonthDay] = instance(_.toString)

  implicit val offsetDateTimeStringable: Stringable[OffsetDateTime] = instance(_.toString)

  implicit val offsetTimeStringable: Stringable[OffsetTime] = instance(_.toString)

  implicit val periodStringable: Stringable[Period] = instance(_.toString)

  implicit val yearStringable: Stringable[Year] = instance(_.toString)

  implicit val yearMonthStringable: Stringable[YearMonth] = instance(_.toString)

  implicit val zoneIdStringable: Stringable[ZoneId] = instance(_.toString)

  implicit val zoneOffsetStringable: Stringable[ZoneOffset] = instance(_.toString)

  implicit val zonedDateTimeStringable: Stringable[ZonedDateTime] = instance(_.toString)

  // ─────────────────────────────────────────────────────────────────────────
  // Java Util
  // ─────────────────────────────────────────────────────────────────────────

  implicit val currencyStringable: Stringable[Currency] = instance(_.getCurrencyCode)

  implicit val uuidStringable: Stringable[UUID] = instance(_.toString)
}
