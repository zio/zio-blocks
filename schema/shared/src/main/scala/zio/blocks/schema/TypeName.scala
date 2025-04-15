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

  def tuple6[A, B, C, D, E, F]: TypeName[(A, B, C, D, E, F)] = _tuple6.asInstanceOf[TypeName[(A, B, C, D, E, F)]]

  def tuple7[A, B, C, D, E, F, G]: TypeName[(A, B, C, D, E, F, G)] =
    _tuple7.asInstanceOf[TypeName[(A, B, C, D, E, F, G)]]

  def tuple8[A, B, C, D, E, F, G, H]: TypeName[(A, B, C, D, E, F, G, H)] =
    _tuple8.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H)]]

  def tuple9[A, B, C, D, E, F, G, H, I]: TypeName[(A, B, C, D, E, F, G, H, I)] =
    _tuple9.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I)]]

  def tuple10[A, B, C, D, E, F, G, H, I, J]: TypeName[(A, B, C, D, E, F, G, H, I, J)] =
    _tuple10.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J)]]

  def tuple11[A, B, C, D, E, F, G, H, I, J, K]: TypeName[(A, B, C, D, E, F, G, H, I, J, K)] =
    _tuple11.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K)]]

  def tuple12[A, B, C, D, E, F, G, H, I, J, K, L]: TypeName[(A, B, C, D, E, F, G, H, I, J, K, L)] =
    _tuple12.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L)]]

  def tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M]: TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    _tuple13.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M)]]

  def tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N]: TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    _tuple14.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)]]

  def tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]: TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    _tuple15.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)]]

  def tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]
    : TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] =
    _tuple16.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)]]

  def tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]
    : TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] =
    _tuple17.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)]]

  def tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]
    : TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    _tuple18.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)]]

  def tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]
    : TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    _tuple19.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)]]

  def tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]
    : TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    _tuple20.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)]]

  def tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]
    : TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    _tuple21.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)]]

  def tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V]
    : TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    _tuple22.asInstanceOf[TypeName[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)]]

  private[this] val _some    = TypeName(Namespace("scala" :: Nil, Nil), "Some")
  private[this] val _option  = TypeName(Namespace("scala" :: Nil, Nil), "Option")
  private[this] val _list    = TypeName(Namespace("scala" :: Nil, Nil), "List")
  private[this] val _map     = TypeName(Namespace("scala" :: "collection" :: "immutable" :: Nil, Nil), "Map")
  private[this] val _set     = TypeName(Namespace("scala" :: "collection" :: "immutable" :: Nil, Nil), "Set")
  private[this] val _vector  = TypeName(Namespace("scala" :: Nil, Nil), "Vector")
  private[this] val _array   = TypeName(Namespace("scala" :: Nil, Nil), "Array")
  private[this] val _either  = TypeName(Namespace("scala" :: Nil, Nil), "Either")
  private[this] val _left    = TypeName(Namespace("scala" :: Nil, Nil), "Left")
  private[this] val _right   = TypeName(Namespace("scala" :: Nil, Nil), "Right")
  private[this] val _tuple2  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple2")
  private[this] val _tuple3  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple3")
  private[this] val _tuple4  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple4")
  private[this] val _tuple5  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple5")
  private[this] val _tuple6  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple6")
  private[this] val _tuple7  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple7")
  private[this] val _tuple8  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple8")
  private[this] val _tuple9  = TypeName(Namespace("scala" :: Nil, Nil), "Tuple9")
  private[this] val _tuple10 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple10")
  private[this] val _tuple11 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple11")
  private[this] val _tuple12 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple12")
  private[this] val _tuple13 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple13")
  private[this] val _tuple14 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple14")
  private[this] val _tuple15 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple15")
  private[this] val _tuple16 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple16")
  private[this] val _tuple17 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple17")
  private[this] val _tuple18 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple18")
  private[this] val _tuple19 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple19")
  private[this] val _tuple20 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple20")
  private[this] val _tuple21 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple21")
  private[this] val _tuple22 = TypeName(Namespace("scala" :: Nil, Nil), "Tuple22")
}
