package zio.blocks.schema.binding

import zio.blocks.schema.DynamicValue

/**
 * A binding is used to attach non-serializable Scala functions, such as
 * constructors, deconstructors, and matchers, to a reflection type.
 *
 * The {{Binding}} type is indexed by `T`, which is a phantom type that
 * represents the type of binding. The type `A` represents the type of the
 * reflection type.
 *
 * So, for example, `Binding[BindingType.Record, Int]` represents a binding for
 * the reflection type `Int` that has a record binding (and therefore, both a
 * constructor and a deconstructor).
 */
sealed trait Binding[T, A] { self =>

  /**
   * An optional generator for a default value for the type `A`.
   */
  def defaultValue: Option[() => A]

  def defaultValue(value: => A): Binding[T, A]

  /**
   * A user-defined list of example values for the type `A`, to be used for
   * testing and documentation.
   */
  def examples: Seq[A]

  def examples(value: A, values: A*): Binding[T, A]
}

object Binding {
  final case class Primitive[A](
    defaultValue: Option[() => A] = None,
    examples: collection.immutable.Seq[A] = Nil
  ) extends Binding[BindingType.Primitive, A] {
    def defaultValue(value: => A): Primitive[A] = copy(defaultValue = Some(() => value))

    def examples(value: A, values: A*): Primitive[A] = copy(examples = value :: values.toList)
  }

  object Primitive {
    val unit: Primitive[Unit] = new Primitive[Unit]()

    val boolean: Primitive[Boolean] = new Primitive[Boolean]()

    val byte: Primitive[Byte] = new Primitive[Byte]()

    val short: Primitive[Short] = new Primitive[Short]()

    val int: Primitive[Int] = new Primitive[Int]()

    val long: Primitive[Long] = new Primitive[Long]()

    val float: Primitive[Float] = new Primitive[Float]()

    val double: Primitive[Double] = new Primitive[Double]()

    val char: Primitive[Char] = new Primitive[Char]()

    val string: Primitive[String] = new Primitive[String]()

    val bigInt: Primitive[BigInt] = new Primitive[BigInt]()

    val bigDecimal: Primitive[BigDecimal] = new Primitive[BigDecimal]()

    val dayOfWeek: Primitive[java.time.DayOfWeek] = new Primitive[java.time.DayOfWeek]()

    val duration: Primitive[java.time.Duration] = new Primitive[java.time.Duration]()

    val instant: Primitive[java.time.Instant] = new Primitive[java.time.Instant]()

    val localDate: Primitive[java.time.LocalDate] = new Primitive[java.time.LocalDate]()

    val localDateTime: Primitive[java.time.LocalDateTime] = new Primitive[java.time.LocalDateTime]()

    val localTime: Primitive[java.time.LocalTime] = new Primitive[java.time.LocalTime]()

    val month: Primitive[java.time.Month] = new Primitive[java.time.Month]()

    val monthDay: Primitive[java.time.MonthDay] = new Primitive[java.time.MonthDay]()

    val offsetDateTime: Primitive[java.time.OffsetDateTime] = new Primitive[java.time.OffsetDateTime]()

    val offsetTime: Primitive[java.time.OffsetTime] = new Primitive[java.time.OffsetTime]()

    val period: Primitive[java.time.Period] = new Primitive[java.time.Period]()

    val year: Primitive[java.time.Year] = new Primitive[java.time.Year]()

    val yearMonth: Primitive[java.time.YearMonth] = new Primitive[java.time.YearMonth]()

    val zoneId: Primitive[java.time.ZoneId] = new Primitive[java.time.ZoneId]()

    val zoneOffset: Primitive[java.time.ZoneOffset] = new Primitive[java.time.ZoneOffset]()

    val zonedDateTime: Primitive[java.time.ZonedDateTime] = new Primitive[java.time.ZonedDateTime]()

    val currency: Primitive[java.util.Currency] = new Primitive[java.util.Currency]()

    val uuid: Primitive[java.util.UUID] = new Primitive[java.util.UUID]()
  }

  final case class Record[A](
    constructor: Constructor[A],
    deconstructor: Deconstructor[A],
    defaultValue: Option[() => A] = None,
    examples: collection.immutable.Seq[A] = Nil
  ) extends Binding[BindingType.Record, A] {
    def transform[B](f: A => B)(g: B => A): Record[B] = Record(
      constructor.map(f),
      deconstructor.contramap(g),
      defaultValue.map(thunk => () => f(thunk())),
      examples.map(f)
    )

    def defaultValue(value: => A): Record[A] = copy(defaultValue = Some(() => value))

    def examples(value: A, values: A*): Record[A] = copy(examples = value :: values.toList)
  }

  final case class Variant[A](
    discriminator: Discriminator[A],
    matchers: Matchers[A],
    defaultValue: Option[() => A] = None,
    examples: collection.immutable.Seq[A] = Nil
  ) extends Binding[BindingType.Variant, A] {
    def defaultValue(value: => A): Variant[A] = copy(defaultValue = Some(() => value))

    def examples(value: A, values: A*): Variant[A] = copy(examples = value :: values.toList)
  }

  final case class Seq[C[_], A](
    constructor: SeqConstructor[C],
    deconstructor: SeqDeconstructor[C],
    defaultValue: Option[() => C[A]] = None,
    examples: collection.immutable.Seq[C[A]] = Nil
  ) extends Binding[BindingType.Seq[C], C[A]] {
    def defaultValue(value: => C[A]): Seq[C, A] = copy(defaultValue = Some(() => value))

    def examples(value: C[A], values: C[A]*): Seq[C, A] = copy(examples = value :: values.toList)
  }

  object Seq {
    def apply[C[_], A](implicit s: Seq[C, A]): Seq[C, A] = s

    def set[A]: Seq[Set, A] = Seq(SeqConstructor.setConstructor, SeqDeconstructor.setDeconstructor)

    def list[A]: Seq[List, A] = Seq(SeqConstructor.listConstructor, SeqDeconstructor.listDeconstructor)

    def vector[A]: Seq[Vector, A] = Seq(SeqConstructor.vectorConstructor, SeqDeconstructor.vectorDeconstructor)

    def array[A]: Seq[Array, A] = Seq(SeqConstructor.arrayConstructor, SeqDeconstructor.arrayDeconstructor)
  }

  final case class Map[M[_, _], K, V](
    constructor: MapConstructor[M],
    deconstructor: MapDeconstructor[M],
    defaultValue: Option[() => M[K, V]] = None,
    examples: collection.immutable.Seq[M[K, V]] = Nil
  ) extends Binding[BindingType.Map[M], M[K, V]] {
    def defaultValue(value: => M[K, V]): Map[M, K, V] = copy(defaultValue = Some(() => value))

    def examples(value: M[K, V], values: M[K, V]*): Map[M, K, V] = copy(examples = value :: values.toList)
  }

  object Map {
    def map[K, V]: Map[Predef.Map, K, V] = Map(MapConstructor.map, MapDeconstructor.map)
  }

  final case class Dynamic(
    defaultValue: Option[() => DynamicValue] = None,
    examples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Binding[BindingType.Dynamic, DynamicValue] {
    def defaultValue(value: => DynamicValue): Dynamic = copy(defaultValue = Some(() => value))

    def examples(value: DynamicValue, values: DynamicValue*): Dynamic = copy(examples = value :: values.toList)
  }

  implicit val bindingHasBinding: HasBinding[Binding] = new HasBinding[Binding] {
    def binding[T, A](fa: Binding[T, A]): Binding[T, A] = fa

    def updateBinding[T, A](fa: Binding[T, A], f: Binding[T, A] => Binding[T, A]): Binding[T, A] = f(fa)
  }

  implicit val bindingFromBinding: FromBinding[Binding] = new FromBinding[Binding] {
    def fromBinding[T, A](binding: Binding[T, A]): Binding[T, A] = binding
  }
}
