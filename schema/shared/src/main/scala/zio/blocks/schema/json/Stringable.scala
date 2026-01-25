package zio.blocks.schema.json

import java.time._
import java.util.{Currency, UUID}

/**
 * A type class for types that have a canonical string representation suitable
 * for use as JSON keys or inside JSON string literals.
 *
 * Types with a `Stringable` instance correspond exactly to the types defined in
 * `PrimitiveType`:
 *   - Scala primitives: Unit, Boolean, Byte, Short, Int, Long, Float, Double,
 *     Char, String
 *   - Arbitrary precision: BigInt, BigDecimal
 *   - Java Time: DayOfWeek, Duration, Instant, LocalDate, LocalDateTime,
 *     LocalTime, Month, MonthDay, OffsetDateTime, OffsetTime, Period, Year,
 *     YearMonth, ZoneId, ZoneOffset, ZonedDateTime
 *   - Java Util: UUID, Currency
 */
trait Stringable[A] {

  /**
   * Converts a value to its canonical string representation.
   */
  def asString(a: A): String
}

object Stringable {

  /**
   * Summons a Stringable instance for type A.
   */
  def apply[A](implicit stringable: Stringable[A]): Stringable[A] = stringable

  /**
   * Creates a Stringable instance from a function.
   */
  def instance[A](f: A => String): Stringable[A] = new Stringable[A] {
    def asString(a: A): String = f(a)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Scala Primitives
  // ─────────────────────────────────────────────────────────────────────────

  implicit val unitStringable: Stringable[Unit] = instance(_ => "()")

  implicit val booleanStringable: Stringable[Boolean] = instance(_.toString)

  implicit val byteStringable: Stringable[Byte] = instance(_.toString)

  implicit val shortStringable: Stringable[Short] = instance(_.toString)

  implicit val intStringable: Stringable[Int] = instance(_.toString)

  implicit val longStringable: Stringable[Long] = instance(_.toString)

  implicit val floatStringable: Stringable[Float] = instance(_.toString)

  implicit val doubleStringable: Stringable[Double] = instance(_.toString)

  implicit val charStringable: Stringable[Char] = instance(_.toString)

  implicit val stringStringable: Stringable[String] = instance(identity)

  // ─────────────────────────────────────────────────────────────────────────
  // Arbitrary Precision Numbers
  // ─────────────────────────────────────────────────────────────────────────

  implicit val bigIntStringable: Stringable[BigInt] = instance(_.toString)

  implicit val bigDecimalStringable: Stringable[BigDecimal] = instance(_.toString)

  // ─────────────────────────────────────────────────────────────────────────
  // Java Time Types
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
  // Java Util Types
  // ─────────────────────────────────────────────────────────────────────────

  implicit val uuidStringable: Stringable[UUID] = instance(_.toString)

  implicit val currencyStringable: Stringable[Currency] = instance(_.getCurrencyCode)
}
