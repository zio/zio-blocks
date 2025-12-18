package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import zio.blocks.schema.derive.{Deriver, DerivationBuilder}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

/**
 * A `Schema` is a data type that contains reified information on the structure
 * of a Scala data type, together with the ability to tear down and build up
 * values of that type.
 */
final case class Schema[A](reflect: Reflect.Bound[A]) {
  private[this] val cache: ConcurrentHashMap[codec.Format, ?] = new ConcurrentHashMap

  private[this] def getInstance[F <: codec.Format](format: F): format.TypeClass[A] =
    cache
      .asInstanceOf[ConcurrentHashMap[codec.Format, format.TypeClass[A]]]
      .computeIfAbsent(format, _ => derive(format.deriver))

  def getDefaultValue: Option[A] = reflect.getDefaultValue

  def getDefaultValue[B](optic: Optic[A, B]): Option[B] = get(optic).flatMap(_.getDefaultValue)

  def defaultValue[B](optic: Optic[A, B], value: => B): Schema[A] =
    updated(optic)(_.defaultValue(value)).getOrElse(this)

  def defaultValue(value: => A): Schema[A] = new Schema(reflect.defaultValue(value))

  def structural(implicit ts: ToStructural[A]): Schema[ts.StructuralType] = ts.structuralSchema(this)

  def derive[TC[_]](deriver: Deriver[TC]): TC[A] = deriving(deriver).derive

  def deriving[TC[_]](deriver: Deriver[TC]): DerivationBuilder[TC, A] =
    new DerivationBuilder[TC, A](this, deriver, IndexedSeq.empty, IndexedSeq.empty)

  def decode[F <: codec.Format](format: F)(decodeInput: format.DecodeInput): Either[SchemaError, A] =
    getInstance(format).decode(decodeInput)

  def doc: Doc = reflect.doc

  def doc(value: String): Schema[A] = new Schema(reflect.doc(value))

  def doc[B](optic: Optic[A, B]): Doc = get(optic).fold[Doc](Doc.Empty)(_.doc)

  def doc[B](optic: Optic[A, B], value: String): Schema[A] = updated(optic)(_.doc(value)).getOrElse(this)

  def encode[F <: codec.Format](format: F)(output: format.EncodeOutput)(value: A): Unit =
    getInstance(format).encode(value, output)

  def examples: Seq[A] = reflect.examples

  def examples(value: A, values: A*): Schema[A] = new Schema(reflect.examples(value, values: _*))

  def examples[B](optic: Optic[A, B]): Seq[B] = get(optic).fold[Seq[B]](Nil)(_.examples)

  def examples[B](optic: Optic[A, B], value: B, values: B*): Schema[A] =
    updated(optic)(_.examples(value, values: _*)).getOrElse(this)

  def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = reflect.fromDynamicValue(value, Nil)

  def get[B](optic: Optic[A, B]): Option[Reflect.Bound[B]] = reflect.get(optic)

  def get(dynamic: DynamicOptic): Option[Reflect.Bound[?]] = reflect.get(dynamic)

  def toDynamicValue(value: A): DynamicValue = reflect.toDynamicValue(value)

  def updated(dynamic: DynamicOptic)(f: Reflect.Updater[Binding]): Option[Schema[A]] =
    reflect.updated(dynamic)(f).map(x => new Schema(x))

  def updated[B](optic: Optic[A, B])(f: Reflect.Bound[B] => Reflect.Bound[B]): Option[Schema[A]] =
    reflect.updated(optic)(f).map(x => new Schema(x))

  def @@[Min >: A, Max <: A](aspect: SchemaAspect[Min, Max, Binding]): Schema[A] = new Schema(reflect.aspect(aspect))

  def @@[B](part: Optic[A, B], aspect: SchemaAspect[B, B, Binding]) = new Schema(reflect.aspect(part, aspect))

  def modifier(modifier: Modifier.Reflect): Schema[A] = new Schema(reflect.modifier(modifier))

  def modifiers(modifiers: Iterable[Modifier.Reflect]): Schema[A] = new Schema(reflect.modifiers(modifiers))

  def wrap[B: Schema](wrap: B => Either[String, A], unwrap: A => B): Schema[A] = new Schema(
    new Reflect.Wrapper[Binding, A, B](
      Schema[B].reflect,
      reflect.typeName,
      Reflect.unwrapToPrimitiveTypeOption(reflect),
      new Binding.Wrapper(wrap, unwrap)
    )
  )

  def wrapTotal[B: Schema](wrap: B => A, unwrap: A => B): Schema[A] = new Schema(
    new Reflect.Wrapper[Binding, A, B](
      Schema[B].reflect,
      reflect.typeName,
      Reflect.unwrapToPrimitiveTypeOption(reflect),
      new Binding.Wrapper(x => new Right(wrap(x)), unwrap)
    )
  )
}

object Schema extends SchemaVersionSpecific {
  def apply[A](implicit schema: Schema[A]): Schema[A] = schema

  implicit val dynamic: Schema[DynamicValue] = new Schema(Reflect.dynamic[Binding])

  implicit val unit: Schema[Unit] = new Schema(Reflect.unit[Binding])

  implicit val boolean: Schema[Boolean] = new Schema(Reflect.boolean[Binding])

  implicit val byte: Schema[Byte] = new Schema(Reflect.byte[Binding])

  implicit val short: Schema[Short] = new Schema(Reflect.short[Binding])

  implicit val int: Schema[Int] = new Schema(Reflect.int[Binding])

  implicit val long: Schema[Long] = new Schema(Reflect.long[Binding])

  implicit val float: Schema[Float] = new Schema(Reflect.float[Binding])

  implicit val double: Schema[Double] = new Schema(Reflect.double[Binding])

  implicit val char: Schema[Char] = new Schema(Reflect.char[Binding])

  implicit val string: Schema[String] = new Schema(Reflect.string[Binding])

  implicit val bigInt: Schema[BigInt] = new Schema(Reflect.bigInt[Binding])

  implicit val bigDecimal: Schema[BigDecimal] = new Schema(Reflect.bigDecimal[Binding])

  implicit val dayOfWeek: Schema[java.time.DayOfWeek] = new Schema(Reflect.dayOfWeek[Binding])

  implicit val duration: Schema[java.time.Duration] = new Schema(Reflect.duration[Binding])

  implicit val instant: Schema[java.time.Instant] = new Schema(Reflect.instant[Binding])

  implicit val localDate: Schema[java.time.LocalDate] = new Schema(Reflect.localDate[Binding])

  implicit val localDateTime: Schema[java.time.LocalDateTime] = new Schema(Reflect.localDateTime[Binding])

  implicit val localTime: Schema[java.time.LocalTime] = new Schema(Reflect.localTime[Binding])

  implicit val month: Schema[java.time.Month] = new Schema(Reflect.month[Binding])

  implicit val monthDay: Schema[java.time.MonthDay] = new Schema(Reflect.monthDay[Binding])

  implicit val offsetDateTime: Schema[java.time.OffsetDateTime] = new Schema(Reflect.offsetDateTime[Binding])

  implicit val offsetTime: Schema[java.time.OffsetTime] = new Schema(Reflect.offsetTime[Binding])

  implicit val period: Schema[java.time.Period] = new Schema(Reflect.period[Binding])

  implicit val year: Schema[java.time.Year] = new Schema(Reflect.year[Binding])

  implicit val yearMonth: Schema[java.time.YearMonth] = new Schema(Reflect.yearMonth[Binding])

  implicit val zoneId: Schema[java.time.ZoneId] = new Schema(Reflect.zoneId[Binding])

  implicit val zoneOffset: Schema[java.time.ZoneOffset] = new Schema(Reflect.zoneOffset[Binding])

  implicit val zonedDateTime: Schema[java.time.ZonedDateTime] = new Schema(Reflect.zonedDateTime[Binding])

  implicit val currency: Schema[java.util.Currency] = new Schema(Reflect.currency[Binding])

  implicit val uuid: Schema[java.util.UUID] = new Schema(Reflect.uuid[Binding])

  implicit def option[A <: AnyRef](implicit element: Schema[A]): Schema[Option[A]] =
    new Schema(Reflect.option(element.reflect))

  implicit val optionDouble: Schema[Option[Double]] = new Schema(Reflect.optionDouble(Schema[Double].reflect))

  implicit val optionLong: Schema[Option[Long]] = new Schema(Reflect.optionLong(Schema[Long].reflect))

  implicit val optionFloat: Schema[Option[Float]] = new Schema(Reflect.optionFloat(Schema[Float].reflect))

  implicit val optionInt: Schema[Option[Int]] = new Schema(Reflect.optionInt(Schema[Int].reflect))

  implicit val optionChar: Schema[Option[Char]] = new Schema(Reflect.optionChar(Schema[Char].reflect))

  implicit val optionShort: Schema[Option[Short]] = new Schema(Reflect.optionShort(Schema[Short].reflect))

  implicit val optionBoolean: Schema[Option[Boolean]] = new Schema(Reflect.optionBoolean(Schema[Boolean].reflect))

  implicit val optionByte: Schema[Option[Byte]] = new Schema(Reflect.optionByte(Schema[Byte].reflect))

  implicit val optionUnit: Schema[Option[Unit]] = new Schema(Reflect.optionUnit(Schema[Unit].reflect))

  implicit def set[A](implicit element: Schema[A]): Schema[Set[A]] = new Schema(Reflect.set(element.reflect))

  implicit def list[A](implicit element: Schema[A]): Schema[List[A]] = new Schema(Reflect.list(element.reflect))

  implicit def vector[A](implicit element: Schema[A]): Schema[Vector[A]] = new Schema(Reflect.vector(element.reflect))

  implicit def arraySeq[A](implicit element: Schema[A]): Schema[ArraySeq[A]] =
    new Schema(Reflect.arraySeq(element.reflect))

  implicit def indexedSeq[A](implicit element: Schema[A]): Schema[IndexedSeq[A]] =
    new Schema(Reflect.indexedSeq(element.reflect))

  implicit def seq[A](implicit element: Schema[A]): Schema[Seq[A]] = new Schema(Reflect.seq(element.reflect))

  implicit def map[A, B](implicit key: Schema[A], value: Schema[B]): Schema[collection.immutable.Map[A, B]] =
    new Schema(Reflect.map(key.reflect, value.reflect))
}
