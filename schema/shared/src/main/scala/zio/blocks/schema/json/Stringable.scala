package zio.blocks.schema.json

import java.time._
import java.util.{Currency, UUID}

/**
 * A typeclass for types that have canonical string representations suitable for
 * JSON key positions and string literal interpolation.
 *
 * These are the "stringable" types corresponding to PrimitiveType in the schema.
 */
trait Stringable[A] {
  def stringify(a: A): String
}

object Stringable {

  def apply[A](implicit instance: Stringable[A]): Stringable[A] = instance

  private def instance[A](f: A => String): Stringable[A] = new Stringable[A] {
    def stringify(a: A): String = f(a)
  }

  // Scala primitives
  implicit val stringStringable: Stringable[String] = instance(identity)

  implicit val booleanStringable: Stringable[Boolean] = instance(_.toString)

  implicit val byteStringable: Stringable[Byte] = instance(_.toString)

  implicit val shortStringable: Stringable[Short] = instance(_.toString)

  implicit val intStringable: Stringable[Int] = instance(_.toString)

  implicit val longStringable: Stringable[Long] = instance(_.toString)

  implicit val floatStringable: Stringable[Float] = instance(_.toString)

  implicit val doubleStringable: Stringable[Double] = instance(_.toString)

  implicit val charStringable: Stringable[Char] = instance(_.toString)

  implicit val unitStringable: Stringable[Unit] = instance(_ => "()")

  // Arbitrary precision
  implicit val bigIntStringable: Stringable[BigInt] = instance(_.toString)

  implicit val bigDecimalStringable: Stringable[BigDecimal] = instance(_.toString)

  // Java Time types
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

  // Java Util types
  implicit val uuidStringable: Stringable[UUID] = instance(_.toString)

  implicit val currencyStringable: Stringable[Currency] = instance(_.toString)
}
