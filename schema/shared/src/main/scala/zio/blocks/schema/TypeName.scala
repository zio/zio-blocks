package zio.blocks.schema

import scala.collection.immutable.ArraySeq

final case class TypeName[A](namespace: Namespace, name: String) {
  lazy val fullName: String = namespace.elements.mkString("", ".", "." + name)
}

object TypeName {
  val unit: TypeName[Unit] = new TypeName(Namespace.scala, "Unit")

  val boolean: TypeName[Boolean] = new TypeName(Namespace.scala, "Boolean")

  val byte: TypeName[Byte] = new TypeName(Namespace.scala, "Byte")

  val short: TypeName[Short] = new TypeName(Namespace.scala, "Short")

  val int: TypeName[Int] = new TypeName(Namespace.scala, "Int")

  val long: TypeName[Long] = new TypeName(Namespace.scala, "Long")

  val float: TypeName[Float] = new TypeName(Namespace.scala, "Float")

  val double: TypeName[Double] = new TypeName(Namespace.scala, "Double")

  val char: TypeName[Char] = new TypeName(Namespace.scala, "Char")

  val string: TypeName[String] = new TypeName(Namespace.javaLang, "String")

  val bigInt: TypeName[BigInt] = new TypeName(Namespace.scala, "BigInt")

  val bigDecimal: TypeName[BigDecimal] = new TypeName(Namespace.scala, "BigDecimal")

  val dayOfWeek: TypeName[java.time.DayOfWeek] = new TypeName(Namespace.javaTime, "DayOfWeek")

  val duration: TypeName[java.time.Duration] = new TypeName(Namespace.javaTime, "Duration")

  val instant: TypeName[java.time.Instant] = new TypeName(Namespace.javaTime, "Instant")

  val localDate: TypeName[java.time.LocalDate] = new TypeName(Namespace.javaTime, "LocalDate")

  val localDateTime: TypeName[java.time.LocalDateTime] = new TypeName(Namespace.javaTime, "LocalDateTime")

  val localTime: TypeName[java.time.LocalTime] = new TypeName(Namespace.javaTime, "LocalTime")

  val month: TypeName[java.time.Month] = new TypeName(Namespace.javaTime, "Month")

  val monthDay: TypeName[java.time.MonthDay] = new TypeName(Namespace.javaTime, "MonthDay")

  val offsetDateTime: TypeName[java.time.OffsetDateTime] = new TypeName(Namespace.javaTime, "OffsetDateTime")

  val offsetTime: TypeName[java.time.OffsetTime] = new TypeName(Namespace.javaTime, "OffsetTime")

  val period: TypeName[java.time.Period] = new TypeName(Namespace.javaTime, "Period")

  val year: TypeName[java.time.Year] = new TypeName(Namespace.javaTime, "Year")

  val yearMonth: TypeName[java.time.YearMonth] = new TypeName(Namespace.javaTime, "YearMonth")

  val zoneId: TypeName[java.time.ZoneId] = new TypeName(Namespace.javaTime, "ZoneId")

  val zoneOffset: TypeName[java.time.ZoneOffset] = new TypeName(Namespace.javaTime, "ZoneOffset")

  val zonedDateTime: TypeName[java.time.ZonedDateTime] = new TypeName(Namespace.javaTime, "ZonedDateTime")

  val currency: TypeName[java.util.Currency] = new TypeName(Namespace.javaUtil, "Currency")

  val uuid: TypeName[java.util.UUID] = new TypeName(Namespace.javaUtil, "UUID")

  val none: TypeName[None.type] = new TypeName(Namespace.scala, "None")

  def some[A]: TypeName[Some[A]] = _some.asInstanceOf[TypeName[Some[A]]]

  def option[A]: TypeName[Option[A]] = _option.asInstanceOf[TypeName[Option[A]]]

  def list[A]: TypeName[List[A]] = _list.asInstanceOf[TypeName[List[A]]]

  def map[K, V]: TypeName[Map[K, V]] = _map.asInstanceOf[TypeName[Map[K, V]]]

  def set[A]: TypeName[Set[A]] = _set.asInstanceOf[TypeName[Set[A]]]

  def vector[A]: TypeName[Vector[A]] = _vector.asInstanceOf[TypeName[Vector[A]]]

  def arraySeq[A]: TypeName[ArraySeq[A]] = _arraySeq.asInstanceOf[TypeName[ArraySeq[A]]]

  def array[A]: TypeName[Array[A]] = _array.asInstanceOf[TypeName[Array[A]]]

  private[this] val _some     = new TypeName(Namespace.scala, "Some")
  private[this] val _option   = new TypeName(Namespace.scala, "Option")
  private[this] val _list     = new TypeName(Namespace.scala, "List")
  private[this] val _map      = new TypeName(Namespace.scalaCollectionImmutable, "Map")
  private[this] val _set      = new TypeName(Namespace.scalaCollectionImmutable, "Set")
  private[this] val _vector   = new TypeName(Namespace.scala, "Vector")
  private[this] val _arraySeq = new TypeName(Namespace.scalaCollectionImmutable, "ArraySeq")
  private[this] val _array    = new TypeName(Namespace.scala, "Array")
}
