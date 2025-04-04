package zio.blocks.schema

final case class TypeName[A](namespace: Namespace, name: String)
object TypeName {
  val unit: TypeName[Unit] = TypeName(Namespace("scala" :: Nil, Nil), "Unit")

  val boolean: TypeName[Boolean] = TypeName(Namespace("scala" :: Nil, Nil), "Boolean")

  val byte: TypeName[Byte] = TypeName(Namespace("scala" :: Nil, Nil), "Byte")

  val short: TypeName[Short] = TypeName(Namespace("scala" :: Nil, Nil), "Short")

  val int: TypeName[Int] = TypeName(Namespace("scala" :: Nil, Nil), "Int")

  val long: TypeName[Long] = TypeName(Namespace("scala" :: Nil, Nil), "Long")

  val float: TypeName[Float] = TypeName(Namespace("scala" :: Nil, Nil), "Float")

  val double: TypeName[Double] = TypeName(Namespace("scala" :: Nil, Nil), "Double")

  val char: TypeName[Char] = TypeName(Namespace("scala" :: Nil, Nil), "Char")

  val string: TypeName[String] = TypeName(Namespace("scala" :: Nil, Nil), "String")

  val bigInt: TypeName[BigInt] = TypeName(Namespace("scala" :: Nil, Nil), "BigInt")

  val bigDecimal: TypeName[BigDecimal] = TypeName(Namespace("scala" :: Nil, Nil), "BigDecimal")

  val dayOfWeek: TypeName[java.time.DayOfWeek] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "DayOfWeek")

  val duration: TypeName[java.time.Duration] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "Duration")

  val instant: TypeName[java.time.Instant] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "Instant")

  val localDate: TypeName[java.time.LocalDate] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "LocalDate")

  val localDateTime: TypeName[java.time.LocalDateTime] =
    TypeName(Namespace("java" :: "time" :: Nil, Nil), "LocalDateTime")

  val localTime: TypeName[java.time.LocalTime] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "LocalTime")

  val month: TypeName[java.time.Month] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "Month")

  val monthDay: TypeName[java.time.MonthDay] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "MonthDay")

  val offsetDateTime: TypeName[java.time.OffsetDateTime] =
    TypeName(Namespace("java" :: "time" :: Nil, Nil), "OffsetDateTime")

  val offsetTime: TypeName[java.time.OffsetTime] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "OffsetTime")

  val period: TypeName[java.time.Period] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "Period")

  val year: TypeName[java.time.Year] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "Year")

  val yearMonth: TypeName[java.time.YearMonth] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "YearMonth")

  val zoneId: TypeName[java.time.ZoneId] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "ZoneId")

  val zoneOffset: TypeName[java.time.ZoneOffset] = TypeName(Namespace("java" :: "time" :: Nil, Nil), "ZoneOffset")

  val zonedDateTime: TypeName[java.time.ZonedDateTime] =
    TypeName(Namespace("java" :: "time" :: Nil, Nil), "ZonedDateTime")

  val currency: TypeName[java.util.Currency] = TypeName(Namespace("java" :: "util" :: Nil, Nil), "Currency")

  val uuid: TypeName[java.util.UUID] = TypeName(Namespace("java" :: "util" :: Nil, Nil), "UUID")

  def some[A]: TypeName[Some[A]] = _some.asInstanceOf[TypeName[Some[A]]]

  val none: TypeName[None.type] = TypeName(Namespace("scala" :: Nil, Nil), "None")

  def option[A]: TypeName[Option[A]] = _option.asInstanceOf[TypeName[Option[A]]]

  def list[A]: TypeName[List[A]] = _list.asInstanceOf[TypeName[List[A]]]

  def map[K, V]: TypeName[Map[K, V]] = _map.asInstanceOf[TypeName[Map[K, V]]]

  def set[A]: TypeName[Set[A]] = _set.asInstanceOf[TypeName[Set[A]]]

  def vector[A]: TypeName[Vector[A]] = _vector.asInstanceOf[TypeName[Vector[A]]]

  def array[A]: TypeName[Array[A]] = _array.asInstanceOf[TypeName[Array[A]]]

  def left[A, B]: TypeName[Left[A, B]] = _left.asInstanceOf[TypeName[Left[A, B]]]

  def right[A, B]: TypeName[Right[A, B]] = _right.asInstanceOf[TypeName[Right[A, B]]]

  def either[A, B]: TypeName[Either[A, B]] = _either.asInstanceOf[TypeName[Either[A, B]]]

  def tuple2[A, B]: TypeName[(A, B)] = _tuple2.asInstanceOf[TypeName[(A, B)]]

  def tuple3[A, B, C]: TypeName[(A, B, C)] = _tuple3.asInstanceOf[TypeName[(A, B, C)]]

  def tuple4[A, B, C, D]: TypeName[(A, B, C, D)] = _tuple4.asInstanceOf[TypeName[(A, B, C, D)]]

  def tuple5[A, B, C, D, E]: TypeName[(A, B, C, D, E)] = _tuple5.asInstanceOf[TypeName[(A, B, C, D, E)]]

  private val _some   = TypeName(Namespace("scala" :: Nil, Nil), "Some")
  private val _option = TypeName(Namespace("scala" :: Nil, Nil), "Option")
  private val _list   = TypeName(Namespace("scala" :: Nil, Nil), "List")
  private val _map    = TypeName(Namespace("scala" :: "collection" :: "immutable" :: Nil, Nil), "Map")
  private val _set    = TypeName(Namespace("scala" :: "collection" :: "immutable" :: Nil, Nil), "Set")
  private val _vector = TypeName(Namespace("scala" :: Nil, Nil), "Vector")
  private val _array  = TypeName(Namespace("scala" :: Nil, Nil), "Array")
  private val _either = TypeName(Namespace("scala" :: Nil, Nil), "Either")
  private val _left   = TypeName(Namespace("scala" :: Nil, Nil), "Left")
  private val _right  = TypeName(Namespace("scala" :: Nil, Nil), "Right")
  private val _tuple2 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple2")
  private val _tuple3 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple3")
  private val _tuple4 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple4")
  private val _tuple5 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple5")
}
