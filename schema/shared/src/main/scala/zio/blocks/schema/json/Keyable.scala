package zio.blocks.schema.json

import java.time._
import java.util.{Currency, UUID}

/**
 * A type class for types that can be used as JSON object keys or interpolated
 * inside JSON string literals.
 *
 * Types with a `Keyable` instance correspond exactly to the types defined in
 * `PrimitiveType`:
 *   - Scala primitives: Unit, Boolean, Byte, Short, Int, Long, Float, Double,
 *     Char, String
 *   - Arbitrary precision: BigInt, BigDecimal
 *   - Java Time: DayOfWeek, Duration, Instant, LocalDate, LocalDateTime,
 *     LocalTime, Month, MonthDay, OffsetDateTime, OffsetTime, Period, Year,
 *     YearMonth, ZoneId, ZoneOffset, ZonedDateTime
 *   - Java Util: UUID, Currency
 */
trait Keyable[A] {

  /**
   * Converts a value to its string representation for use as a JSON key.
   */
  def asKey(a: A): String
}

object Keyable {

  /**
   * Summons a Keyable instance for type A.
   */
  def apply[A](implicit keyable: Keyable[A]): Keyable[A] = keyable

  /**
   * Creates a Keyable instance from a function.
   */
  def instance[A](f: A => String): Keyable[A] = new Keyable[A] {
    def asKey(a: A): String = f(a)
  }

  // Scala Primitives

  implicit val unitKeyable: Keyable[Unit] = instance(_ => "{}")

  implicit val booleanKeyable: Keyable[Boolean] = instance(_.toString)

  implicit val byteKeyable: Keyable[Byte] = instance(_.toString)

  implicit val shortKeyable: Keyable[Short] = instance(_.toString)

  implicit val intKeyable: Keyable[Int] = instance(_.toString)

  implicit val longKeyable: Keyable[Long] = instance(_.toString)

  implicit val floatKeyable: Keyable[Float] = instance(JsonBinaryCodec.floatCodec.encodeToString)

  implicit val doubleKeyable: Keyable[Double] = instance(JsonBinaryCodec.doubleCodec.encodeToString)

  implicit val charKeyable: Keyable[Char] = instance(_.toString)

  implicit val stringKeyable: Keyable[String] = instance(identity)

  // Arbitrary Precision Numbers

  implicit val bigIntKeyable: Keyable[BigInt] = instance(JsonBinaryCodec.bigIntCodec.encodeToString)

  implicit val bigDecimalKeyable: Keyable[BigDecimal] = instance(JsonBinaryCodec.bigDecimalCodec.encodeToString)

  // Java Time Types

  implicit val dayOfWeekKeyable: Keyable[DayOfWeek] = instance(_.toString)

  implicit val durationKeyable: Keyable[Duration] = instance(_.toString)

  implicit val instantKeyable: Keyable[Instant] = instance(_.toString)

  implicit val localDateKeyable: Keyable[LocalDate] = instance(_.toString)

  implicit val localDateTimeKeyable: Keyable[LocalDateTime] = instance(_.toString)

  implicit val localTimeKeyable: Keyable[LocalTime] = instance(_.toString)

  implicit val monthKeyable: Keyable[Month] = instance(_.toString)

  implicit val monthDayKeyable: Keyable[MonthDay] = instance(_.toString)

  implicit val offsetDateTimeKeyable: Keyable[OffsetDateTime] = instance(_.toString)

  implicit val offsetTimeKeyable: Keyable[OffsetTime] = instance(_.toString)

  implicit val periodKeyable: Keyable[Period] = instance(_.toString)

  implicit val yearKeyable: Keyable[Year] = instance(_.toString)

  implicit val yearMonthKeyable: Keyable[YearMonth] = instance(_.toString)

  implicit val zoneIdKeyable: Keyable[ZoneId] = instance(_.toString)

  implicit val zoneOffsetKeyable: Keyable[ZoneOffset] = instance(_.toString)

  implicit val zonedDateTimeKeyable: Keyable[ZonedDateTime] = instance(_.toString)

  // Java Util Types

  implicit val uuidKeyable: Keyable[UUID] = instance(_.toString)
  // In Currency use getCurrencyCode
  implicit val currencyKeyable: Keyable[Currency] = instance(_.getCurrencyCode)
}
