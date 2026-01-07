package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import zio.blocks.schema.binding.Binding

final case class TypeName[A](namespace: Namespace, name: String, params: Seq[TypeName[?]] = Nil) {
  def wrap[B: Schema](wrap: B => Either[String, A], unwrap: A => B): Schema[A] = new Schema(
    new Reflect.Wrapper[Binding, A, B](
      Schema[B].reflect,
      this,
      new Binding.Wrapper(wrap, unwrap)
    )
  )

  def wrapTotal[B: Schema](wrap: B => A, unwrap: A => B): Schema[A] = new Schema(
    new Reflect.Wrapper[Binding, A, B](
      Schema[B].reflect,
      this,
      new Binding.Wrapper(x => new Right(wrap(x)), unwrap)
    )
  )

  def primitiveType: Option[PrimitiveType[A]] = (this match {
    case TypeName.unit           => Some(PrimitiveType.Unit)
    case TypeName.boolean        => Some(new PrimitiveType.Boolean(Validation.None))
    case TypeName.byte           => Some(new PrimitiveType.Byte(Validation.None))
    case TypeName.short          => Some(new PrimitiveType.Short(Validation.None))
    case TypeName.int            => Some(new PrimitiveType.Int(Validation.None))
    case TypeName.long           => Some(new PrimitiveType.Long(Validation.None))
    case TypeName.float          => Some(new PrimitiveType.Float(Validation.None))
    case TypeName.double         => Some(new PrimitiveType.Double(Validation.None))
    case TypeName.char           => Some(new PrimitiveType.Char(Validation.None))
    case TypeName.string         => Some(new PrimitiveType.String(Validation.None))
    case TypeName.bigInt         => Some(new PrimitiveType.BigInt(Validation.None))
    case TypeName.bigDecimal     => Some(new PrimitiveType.BigDecimal(Validation.None))
    case TypeName.dayOfWeek      => Some(new PrimitiveType.DayOfWeek(Validation.None))
    case TypeName.duration       => Some(new PrimitiveType.Duration(Validation.None))
    case TypeName.instant        => Some(new PrimitiveType.Instant(Validation.None))
    case TypeName.localDate      => Some(new PrimitiveType.LocalDate(Validation.None))
    case TypeName.localDateTime  => Some(new PrimitiveType.LocalDateTime(Validation.None))
    case TypeName.localTime      => Some(new PrimitiveType.LocalTime(Validation.None))
    case TypeName.month          => Some(new PrimitiveType.Month(Validation.None))
    case TypeName.monthDay       => Some(new PrimitiveType.MonthDay(Validation.None))
    case TypeName.offsetDateTime => Some(new PrimitiveType.OffsetDateTime(Validation.None))
    case TypeName.offsetTime     => Some(new PrimitiveType.OffsetTime(Validation.None))
    case TypeName.period         => Some(new PrimitiveType.Period(Validation.None))
    case TypeName.year           => Some(new PrimitiveType.Year(Validation.None))
    case TypeName.yearMonth      => Some(new PrimitiveType.YearMonth(Validation.None))
    case TypeName.zoneId         => Some(new PrimitiveType.ZoneId(Validation.None))
    case TypeName.zoneOffset     => Some(new PrimitiveType.ZoneOffset(Validation.None))
    case TypeName.zonedDateTime  => Some(new PrimitiveType.ZonedDateTime(Validation.None))
    case TypeName.currency       => Some(new PrimitiveType.Currency(Validation.None))
    case TypeName.uuid           => Some(new PrimitiveType.UUID(Validation.None))
    case _                       => None
  }).asInstanceOf[Option[PrimitiveType[A]]]
}

object TypeName {
  implicit val unit: TypeName[Unit] = new TypeName(Namespace.scala, "Unit")

  implicit val boolean: TypeName[Boolean] = new TypeName(Namespace.scala, "Boolean")

  implicit val byte: TypeName[Byte] = new TypeName(Namespace.scala, "Byte")

  implicit val short: TypeName[Short] = new TypeName(Namespace.scala, "Short")

  implicit val int: TypeName[Int] = new TypeName(Namespace.scala, "Int")

  implicit val long: TypeName[Long] = new TypeName(Namespace.scala, "Long")

  implicit val float: TypeName[Float] = new TypeName(Namespace.scala, "Float")

  implicit val double: TypeName[Double] = new TypeName(Namespace.scala, "Double")

  implicit val char: TypeName[Char] = new TypeName(Namespace.scala, "Char")

  implicit val string: TypeName[String] = new TypeName(Namespace.scala, "String")

  implicit val bigInt: TypeName[BigInt] = new TypeName(Namespace.scala, "BigInt")

  implicit val bigDecimal: TypeName[BigDecimal] = new TypeName(Namespace.scala, "BigDecimal")

  implicit val dayOfWeek: TypeName[java.time.DayOfWeek] = new TypeName(Namespace.javaTime, "DayOfWeek")

  implicit val duration: TypeName[java.time.Duration] = new TypeName(Namespace.javaTime, "Duration")

  implicit val instant: TypeName[java.time.Instant] = new TypeName(Namespace.javaTime, "Instant")

  implicit val localDate: TypeName[java.time.LocalDate] = new TypeName(Namespace.javaTime, "LocalDate")

  implicit val localDateTime: TypeName[java.time.LocalDateTime] = new TypeName(Namespace.javaTime, "LocalDateTime")

  implicit val localTime: TypeName[java.time.LocalTime] = new TypeName(Namespace.javaTime, "LocalTime")

  implicit val month: TypeName[java.time.Month] = new TypeName(Namespace.javaTime, "Month")

  implicit val monthDay: TypeName[java.time.MonthDay] = new TypeName(Namespace.javaTime, "MonthDay")

  implicit val offsetDateTime: TypeName[java.time.OffsetDateTime] = new TypeName(Namespace.javaTime, "OffsetDateTime")

  implicit val offsetTime: TypeName[java.time.OffsetTime] = new TypeName(Namespace.javaTime, "OffsetTime")

  implicit val period: TypeName[java.time.Period] = new TypeName(Namespace.javaTime, "Period")

  implicit val year: TypeName[java.time.Year] = new TypeName(Namespace.javaTime, "Year")

  implicit val yearMonth: TypeName[java.time.YearMonth] = new TypeName(Namespace.javaTime, "YearMonth")

  implicit val zoneId: TypeName[java.time.ZoneId] = new TypeName(Namespace.javaTime, "ZoneId")

  implicit val zoneOffset: TypeName[java.time.ZoneOffset] = new TypeName(Namespace.javaTime, "ZoneOffset")

  implicit val zonedDateTime: TypeName[java.time.ZonedDateTime] = new TypeName(Namespace.javaTime, "ZonedDateTime")

  implicit val currency: TypeName[java.util.Currency] = new TypeName(Namespace.javaUtil, "Currency")

  implicit val uuid: TypeName[java.util.UUID] = new TypeName(Namespace.javaUtil, "UUID")

  implicit val none: TypeName[None.type] = new TypeName(Namespace.scala, "None")

  implicit val dynamicValue: TypeName[DynamicValue] = new TypeName(Namespace.zioBlocksSchema, "DynamicValue")

  def some[A](element: TypeName[A]): TypeName[Some[A]] =
    _some.copy(params = Seq(element)).asInstanceOf[TypeName[Some[A]]]

  def option[A](element: TypeName[A]): TypeName[Option[A]] =
    _option.copy(params = Seq(element)).asInstanceOf[TypeName[Option[A]]]

  def list[A](element: TypeName[A]): TypeName[List[A]] =
    _list.copy(params = Seq(element)).asInstanceOf[TypeName[List[A]]]

  def map[K, V](key: TypeName[K], value: TypeName[V]): TypeName[Map[K, V]] =
    _map.copy(params = Seq(key, value)).asInstanceOf[TypeName[Map[K, V]]]

  def set[A](element: TypeName[A]): TypeName[Set[A]] = _set.copy(params = Seq(element)).asInstanceOf[TypeName[Set[A]]]

  def vector[A](element: TypeName[A]): TypeName[Vector[A]] =
    _vector.copy(params = Seq(element)).asInstanceOf[TypeName[Vector[A]]]

  def arraySeq[A](element: TypeName[A]): TypeName[ArraySeq[A]] =
    _arraySeq.copy(params = Seq(element)).asInstanceOf[TypeName[ArraySeq[A]]]

  def indexedSeq[A](element: TypeName[A]): TypeName[IndexedSeq[A]] =
    _indexedSeq.copy(params = Seq(element)).asInstanceOf[TypeName[IndexedSeq[A]]]

  def seq[A](element: TypeName[A]): TypeName[Seq[A]] =
    _seq.copy(params = Seq(element)).asInstanceOf[TypeName[Seq[A]]]

  private[this] val _some       = new TypeName(Namespace.scala, "Some")
  private[this] val _option     = new TypeName(Namespace.scala, "Option")
  private[this] val _list       = new TypeName(Namespace.scalaCollectionImmutable, "List")
  private[this] val _map        = new TypeName(Namespace.scalaCollectionImmutable, "Map")
  private[this] val _set        = new TypeName(Namespace.scalaCollectionImmutable, "Set")
  private[this] val _vector     = new TypeName(Namespace.scalaCollectionImmutable, "Vector")
  private[this] val _arraySeq   = new TypeName(Namespace.scalaCollectionImmutable, "ArraySeq")
  private[this] val _indexedSeq = new TypeName(Namespace.scalaCollectionImmutable, "IndexedSeq")
  private[this] val _seq        = new TypeName(Namespace.scalaCollectionImmutable, "Seq")
}
