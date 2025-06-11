package zio.blocks.schema

import scala.collection.immutable.ArraySeq

final case class TypeName[A](namespace: Namespace, name: String)

object TypeName {
  val unit: TypeName[Unit] = new TypeName(new Namespace("scala" :: Nil, Nil), "Unit")

  val boolean: TypeName[Boolean] = new TypeName(new Namespace("scala" :: Nil, Nil), "Boolean")

  val byte: TypeName[Byte] = new TypeName(new Namespace("scala" :: Nil, Nil), "Byte")

  val short: TypeName[Short] = new TypeName(new Namespace("scala" :: Nil, Nil), "Short")

  val int: TypeName[Int] = new TypeName(new Namespace("scala" :: Nil, Nil), "Int")

  val long: TypeName[Long] = new TypeName(new Namespace("scala" :: Nil, Nil), "Long")

  val float: TypeName[Float] = new TypeName(new Namespace("scala" :: Nil, Nil), "Float")

  val double: TypeName[Double] = new TypeName(new Namespace("scala" :: Nil, Nil), "Double")

  val char: TypeName[Char] = new TypeName(new Namespace("scala" :: Nil, Nil), "Char")

  val string: TypeName[String] = new TypeName(new Namespace("scala" :: Nil, Nil), "String")

  val bigInt: TypeName[BigInt] = new TypeName(new Namespace("scala" :: Nil, Nil), "BigInt")

  val bigDecimal: TypeName[BigDecimal] = new TypeName(new Namespace("scala" :: Nil, Nil), "BigDecimal")

  val dayOfWeek: TypeName[java.time.DayOfWeek] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "DayOfWeek")

  val duration: TypeName[java.time.Duration] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "Duration")

  val instant: TypeName[java.time.Instant] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "Instant")

  val localDate: TypeName[java.time.LocalDate] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "LocalDate")

  val localDateTime: TypeName[java.time.LocalDateTime] =
    new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "LocalDateTime")

  val localTime: TypeName[java.time.LocalTime] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "LocalTime")

  val month: TypeName[java.time.Month] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "Month")

  val monthDay: TypeName[java.time.MonthDay] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "MonthDay")

  val offsetDateTime: TypeName[java.time.OffsetDateTime] =
    new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "OffsetDateTime")

  val offsetTime: TypeName[java.time.OffsetTime] =
    new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "OffsetTime")

  val period: TypeName[java.time.Period] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "Period")

  val year: TypeName[java.time.Year] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "Year")

  val yearMonth: TypeName[java.time.YearMonth] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "YearMonth")

  val zoneId: TypeName[java.time.ZoneId] = new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "ZoneId")

  val zoneOffset: TypeName[java.time.ZoneOffset] =
    new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "ZoneOffset")

  val zonedDateTime: TypeName[java.time.ZonedDateTime] =
    new TypeName(new Namespace("java" :: "time" :: Nil, Nil), "ZonedDateTime")

  val currency: TypeName[java.util.Currency] = new TypeName(new Namespace("java" :: "util" :: Nil, Nil), "Currency")

  val uuid: TypeName[java.util.UUID] = new TypeName(new Namespace("java" :: "util" :: Nil, Nil), "UUID")

  val none: TypeName[None.type] = TypeName(Namespace("scala" :: Nil, Nil), "None")

  def some[A]: TypeName[Some[A]] = _some.asInstanceOf[TypeName[Some[A]]]

  def option[A]: TypeName[Option[A]] = _option.asInstanceOf[TypeName[Option[A]]]

  def list[A]: TypeName[List[A]] = _list.asInstanceOf[TypeName[List[A]]]

  def map[K, V]: TypeName[Map[K, V]] = _map.asInstanceOf[TypeName[Map[K, V]]]

  def set[A]: TypeName[Set[A]] = _set.asInstanceOf[TypeName[Set[A]]]

  def vector[A]: TypeName[Vector[A]] = _vector.asInstanceOf[TypeName[Vector[A]]]

  def arraySeq[A]: TypeName[ArraySeq[A]] = _arraySeq.asInstanceOf[TypeName[ArraySeq[A]]]

  def array[A]: TypeName[Array[A]] = _array.asInstanceOf[TypeName[Array[A]]]

  private[this] val _some     = TypeName(Namespace("scala" :: Nil, Nil), "Some")
  private[this] val _option   = TypeName(Namespace("scala" :: Nil, Nil), "Option")
  private[this] val _list     = new TypeName(new Namespace(List("scala"), Nil), "List")
  private[this] val _map      = new TypeName(new Namespace(List("scala", "collection", "immutable"), Nil), "Map")
  private[this] val _set      = new TypeName(new Namespace(List("scala", "collection", "immutable"), Nil), "Set")
  private[this] val _vector   = new TypeName(new Namespace(List("scala"), Nil), "Vector")
  private[this] val _arraySeq = new TypeName(new Namespace(List("scala", "collection", "immutable"), Nil), "ArraySeq")
  private[this] val _array    = new TypeName(new Namespace(List("scala"), Nil), "Array")
}
