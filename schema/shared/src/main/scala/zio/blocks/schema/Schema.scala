package zio.blocks.schema

import zio.blocks.schema.binding.Binding

import java.util.concurrent.ConcurrentHashMap

/**
 * A {{Schema}} is a data type that contains reified information on the
 * structure of a Scala data type, together with the ability to tear down and
 * build up values of that type.
 */
final case class Schema[A](reflect: Reflect.Bound[A]) {
  private[this] val cache: ConcurrentHashMap[codec.Format, _] = new ConcurrentHashMap

  private[this] def getInstance[F <: codec.Format](format: F): format.TypeClass[A] =
    cache
      .asInstanceOf[ConcurrentHashMap[codec.Format, format.TypeClass[A]]]
      .computeIfAbsent(format, _ => derive(format))

  def defaultValue[B](optic: Optic.Bound[A, B], value: => B): Schema[A] = ??? // TODO

  def defaultValue(value: => A): Schema[A] = Schema(reflect.defaultValue(value))

  def derive[F <: codec.Format](format: F): format.TypeClass[A] = ??? // TODO

  def deriving[F <: codec.Format](format: F): DerivationBuilder[format.TypeClass, A] = ??? // TODO

  def decode[F <: codec.Format](format: F)(decodeInput: format.DecodeInput): Either[codec.CodecError, A] =
    getInstance(format).decode(decodeInput)

  def doc: Doc = reflect.doc

  def doc(value: String): Schema[A] = Schema(reflect.doc(value))

  def doc[B](optic: Optic.Bound[A, B]): Doc = optic.focus.doc

  def doc[B](optic: Optic.Bound[A, B])(value: String): Schema[A] = ??? // TODO

  def encode[F <: codec.Format](format: F)(output: format.EncodeOutput)(value: A): Unit =
    getInstance(format).encode(value, output)

  def examples: List[A] = reflect.binding.examples

  def examples(value: A, values: A*): Schema[A] = Schema(reflect.examples(value, values: _*))

  def examples[B](optic: Optic.Bound[A, B]): List[B] = optic.focus.binding.examples

  def examples[B](optic: Optic.Bound[A, B])(value: B, values: B*): Schema[A] = ??? // TODO

  def fromDynamicValue(value: DynamicValue): Either[codec.CodecError, A] = ??? // TODO

  def toDynamicValue(value: A): DynamicValue = ??? // TODO
}

object Schema {
  def apply[A](implicit schema: Schema[A]): Schema[A] = schema

  implicit val unit: Schema[Unit] = Schema(Reflect.unit[Binding])

  implicit val boolean: Schema[Boolean] = Schema(Reflect.boolean[Binding])

  implicit val byte: Schema[Byte] = Schema(Reflect.byte[Binding])

  implicit val short: Schema[Short] = Schema(Reflect.short[Binding])

  implicit val int: Schema[Int] = Schema(Reflect.int[Binding])

  implicit val long: Schema[Long] = Schema(Reflect.long[Binding])

  implicit val float: Schema[Float] = Schema(Reflect.float[Binding])

  implicit val double: Schema[Double] = Schema(Reflect.double[Binding])

  implicit val char: Schema[Char] = Schema(Reflect.char[Binding])

  implicit val string: Schema[String] = Schema(Reflect.string[Binding])

  implicit val bigInteger: Schema[BigInt] = Schema(Reflect.bigInt[Binding])

  implicit val bigDecimal: Schema[BigDecimal] = Schema(Reflect.bigDecimal[Binding])

  implicit val dayOfWeek: Schema[java.time.DayOfWeek] = Schema(Reflect.dayOfWeek[Binding])

  implicit val duration: Schema[java.time.Duration] = Schema(Reflect.duration[Binding])

  implicit val instant: Schema[java.time.Instant] = Schema(Reflect.instant[Binding])

  implicit val localDate: Schema[java.time.LocalDate] = Schema(Reflect.localDate[Binding])

  implicit val localDateTime: Schema[java.time.LocalDateTime] = Schema(Reflect.localDateTime[Binding])

  implicit val localTime: Schema[java.time.LocalTime] = Schema(Reflect.localTime[Binding])

  implicit val month: Schema[java.time.Month] = Schema(Reflect.month[Binding])

  implicit val monthDay: Schema[java.time.MonthDay] = Schema(Reflect.monthDay[Binding])

  implicit val offsetDateTime: Schema[java.time.OffsetDateTime] = Schema(Reflect.offsetDateTime[Binding])

  implicit val offsetTime: Schema[java.time.OffsetTime] = Schema(Reflect.offsetTime[Binding])

  implicit val period: Schema[java.time.Period] = Schema(Reflect.period[Binding])

  implicit val year: Schema[java.time.Year] = Schema(Reflect.year[Binding])

  implicit val yearMonth: Schema[java.time.YearMonth] = Schema(Reflect.yearMonth[Binding])

  implicit val zoneId: Schema[java.time.ZoneId] = Schema(Reflect.zoneId[Binding])

  implicit val zoneOffset: Schema[java.time.ZoneOffset] = Schema(Reflect.zoneOffset[Binding])

  implicit val zonedDateTime: Schema[java.time.ZonedDateTime] = Schema(Reflect.zonedDateTime[Binding])

  implicit val currency: Schema[java.util.Currency] = Schema(Reflect.currency[Binding])

  implicit val uuid: Schema[java.util.UUID] = Schema(Reflect.uuid[Binding])

  implicit def set[A](implicit element: Schema[A]): Schema[Set[A]] = Schema(Reflect.set(element.reflect))

  implicit def list[A](implicit element: Schema[A]): Schema[List[A]] = Schema(Reflect.list(element.reflect))

  implicit def vector[A](implicit element: Schema[A]): Schema[Vector[A]] = Schema(Reflect.vector(element.reflect))

  implicit def array[A](implicit element: Schema[A]): Schema[Array[A]] = Schema(Reflect.array(element.reflect))

  implicit def map[A, B](implicit key: Schema[A], value: Schema[B]): Schema[collection.immutable.Map[A, B]] =
    Schema(Reflect.map(key.reflect, value.reflect))

  implicit def some[A](implicit element: Schema[A]): Schema[Some[A]] = Schema(Reflect.some(element.reflect))

  implicit val none: Schema[None.type] = Schema(Reflect.none[Binding])

  implicit def option[A](implicit element: Schema[A]): Schema[Option[A]] = Schema(Reflect.option(element.reflect))

  implicit def left[A, B](implicit element: Schema[A]): Schema[Left[A, B]] =
    Schema(Reflect.left[Binding, A, B](element.reflect))

  implicit def right[A, B](implicit element: Schema[B]): Schema[Right[A, B]] =
    Schema(Reflect.right[Binding, A, B](element.reflect))

  implicit def either[L, R](implicit l: Schema[L], r: Schema[R]): Reflect.Bound[Either[L, R]] =
    Reflect.either(l.reflect, r.reflect)

  implicit def tuple2[A, B](implicit a: Schema[A], b: Schema[B]): Reflect.Bound[(A, B)] =
    Reflect.tuple2(a.reflect, b.reflect)

  implicit def tuple3[A, B, C](implicit a: Schema[A], b: Schema[B], c: Schema[C]): Reflect.Bound[(A, B, C)] =
    Reflect.tuple3(a.reflect, b.reflect, c.reflect)

  implicit def tuple4[A, B, C, D](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D]
  ): Reflect.Bound[(A, B, C, D)] = Reflect.tuple4(a.reflect, b.reflect, c.reflect, d.reflect)

  implicit def tuple5[A, B, C, D, E](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E]
  ): Reflect.Bound[(A, B, C, D, E)] = Reflect.tuple5(a.reflect, b.reflect, c.reflect, d.reflect, e.reflect)

  implicit def tuple6[A, B, C, D, E, F](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F]
  ): Reflect.Bound[(A, B, C, D, E, F)] =
    Reflect.tuple6(a.reflect, b.reflect, c.reflect, d.reflect, e.reflect, f.reflect)

  implicit def tuple7[A, B, C, D, E, F, G](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G]
  ): Reflect.Bound[(A, B, C, D, E, F, G)] =
    Reflect.tuple7(a.reflect, b.reflect, c.reflect, d.reflect, e.reflect, f.reflect, g.reflect)

  implicit def tuple8[A, B, C, D, E, F, G, H](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H)] =
    Reflect.tuple8(a.reflect, b.reflect, c.reflect, d.reflect, e.reflect, f.reflect, g.reflect, h.reflect)

  implicit def tuple9[A, B, C, D, E, F, G, H, I](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I)] =
    Reflect.tuple9(a.reflect, b.reflect, c.reflect, d.reflect, e.reflect, f.reflect, g.reflect, h.reflect, i.reflect)

  implicit def tuple10[A, B, C, D, E, F, G, H, I, J](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J)] =
    Reflect.tuple10(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect
    )

  implicit def tuple11[A, B, C, D, E, F, G, H, I, J, K](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K)] =
    Reflect.tuple11(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect
    )

  implicit def tuple12[A, B, C, D, E, F, G, H, I, J, K, L](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L)] =
    Reflect.tuple12(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect
    )

  implicit def tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    Reflect.tuple13(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect
    )

  implicit def tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    Reflect.tuple14(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect
    )

  implicit def tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    Reflect.tuple15(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect
    )

  implicit def tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O],
    p: Schema[P]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] =
    Reflect.tuple16(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect,
      p.reflect
    )

  implicit def tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O],
    p: Schema[P],
    q: Schema[Q]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] =
    Reflect.tuple17(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect,
      p.reflect,
      q.reflect
    )

  implicit def tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O],
    p: Schema[P],
    q: Schema[Q],
    r: Schema[R]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    Reflect.tuple18(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect,
      p.reflect,
      q.reflect,
      r.reflect
    )

  implicit def tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O],
    p: Schema[P],
    q: Schema[Q],
    r: Schema[R],
    s: Schema[S]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    Reflect.tuple19(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect,
      p.reflect,
      q.reflect,
      r.reflect,
      s.reflect
    )

  implicit def tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O],
    p: Schema[P],
    q: Schema[Q],
    r: Schema[R],
    s: Schema[S],
    t: Schema[T]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    Reflect.tuple20(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect,
      p.reflect,
      q.reflect,
      r.reflect,
      s.reflect,
      t.reflect
    )

  implicit def tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O],
    p: Schema[P],
    q: Schema[Q],
    r: Schema[R],
    s: Schema[S],
    t: Schema[T],
    u: Schema[U]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    Reflect.tuple21(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect,
      p.reflect,
      q.reflect,
      r.reflect,
      s.reflect,
      t.reflect,
      u.reflect
    )

  implicit def tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](implicit
    a: Schema[A],
    b: Schema[B],
    c: Schema[C],
    d: Schema[D],
    e: Schema[E],
    f: Schema[F],
    g: Schema[G],
    h: Schema[H],
    i: Schema[I],
    j: Schema[J],
    k: Schema[K],
    l: Schema[L],
    m: Schema[M],
    n: Schema[N],
    o: Schema[O],
    p: Schema[P],
    q: Schema[Q],
    r: Schema[R],
    s: Schema[S],
    t: Schema[T],
    u: Schema[U],
    v: Schema[V]
  ): Reflect.Bound[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    Reflect.tuple22(
      a.reflect,
      b.reflect,
      c.reflect,
      d.reflect,
      e.reflect,
      f.reflect,
      g.reflect,
      h.reflect,
      i.reflect,
      j.reflect,
      k.reflect,
      l.reflect,
      m.reflect,
      n.reflect,
      o.reflect,
      p.reflect,
      q.reflect,
      r.reflect,
      s.reflect,
      t.reflect,
      u.reflect,
      v.reflect
    )
}
