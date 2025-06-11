package zio.blocks.schema

import zio.blocks.schema.binding.Binding
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

/**
 * A `Schema` is a data type that contains reified information on the structure
 * of a Scala data type, together with the ability to tear down and build up
 * values of that type.
 */
final case class Schema[A](reflect: Reflect.Bound[A]) {
  private[this] val cache: ConcurrentHashMap[codec.Format, _] = new ConcurrentHashMap

  private[this] def getInstance[F <: codec.Format](format: F): format.TypeClass[A] =
    cache
      .asInstanceOf[ConcurrentHashMap[codec.Format, format.TypeClass[A]]]
      .computeIfAbsent(format, _ => derive(format))

  def getDefaultValue: Option[A] = reflect.getDefaultValue

  def getDefaultValue[B](optic: Optic[A, B]): Option[B] = get(optic).flatMap(_.getDefaultValue)

  def defaultValue[B](optic: Optic[A, B], value: => B): Schema[A] =
    updated(optic)(_.defaultValue(value)).getOrElse(this)

  def defaultValue(value: => A): Schema[A] = new Schema(reflect.defaultValue(value))

  def derive[F <: codec.Format](format: F): format.TypeClass[A] = deriving(format).derive

  def deriving[F <: codec.Format](format: F): zio.blocks.schema.derive.DerivationBuilder[format.TypeClass, A] =
    zio.blocks.schema.derive
      .DerivationBuilder[format.TypeClass, A](this, format.deriver, IndexedSeq.empty, IndexedSeq.empty)

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

  def get(dynamic: DynamicOptic): Option[Reflect.Bound[_]] = reflect.get(dynamic)

  def toDynamicValue(value: A): DynamicValue = reflect.toDynamicValue(value)

  def updated(dynamic: DynamicOptic)(f: Reflect.Updater[Binding]): Option[Schema[A]] =
    reflect.updated(dynamic)(f).map(Schema(_))

  def updated[B](optic: Optic[A, B])(f: Reflect.Bound[B] => Reflect.Bound[B]): Option[Schema[A]] =
    reflect.updated(optic)(f).map(Schema(_))

  def @@[Min >: A, Max <: A](aspect: SchemaAspect[Min, Max, Binding]): Schema[A] =
    new Schema(reflect.aspect(aspect))

  def @@[B](part: Optic[A, B], aspect: SchemaAspect[B, B, Binding]) = new Schema(reflect.aspect(part, aspect))
}

object Schema extends SchemaVersionSpecific {
  def apply[A](implicit schema: Schema[A]): Schema[A] = schema

  implicit val dynamic: Schema[DynamicValue] = new Schema(Reflect.dynamic[Binding])

  implicit val unit: Schema[Unit] = fromPrimitiveType(PrimitiveType.Unit)

  implicit val boolean: Schema[Boolean] = fromPrimitiveType(PrimitiveType.Boolean(Validation.None))

  implicit val byte: Schema[Byte] = fromPrimitiveType(PrimitiveType.Byte(Validation.None))

  implicit val short: Schema[Short] = fromPrimitiveType(PrimitiveType.Short(Validation.None))

  implicit val int: Schema[Int] = fromPrimitiveType(PrimitiveType.Int(Validation.None))

  implicit val long: Schema[Long] = fromPrimitiveType(PrimitiveType.Long(Validation.None))

  implicit val float: Schema[Float] = fromPrimitiveType(PrimitiveType.Float(Validation.None))

  implicit val double: Schema[Double] = fromPrimitiveType(PrimitiveType.Double(Validation.None))

  implicit val char: Schema[Char] = fromPrimitiveType(PrimitiveType.Char(Validation.None))

  implicit val string: Schema[String] = fromPrimitiveType(PrimitiveType.String(Validation.None))

  implicit val bigInteger: Schema[BigInt] = fromPrimitiveType(PrimitiveType.BigInt(Validation.None))

  implicit val bigDecimal: Schema[BigDecimal] = fromPrimitiveType(PrimitiveType.BigDecimal(Validation.None))

  implicit val dayOfWeek: Schema[java.time.DayOfWeek] = fromPrimitiveType(PrimitiveType.DayOfWeek(Validation.None))

  implicit val duration: Schema[java.time.Duration] = fromPrimitiveType(PrimitiveType.Duration(Validation.None))

  implicit val instant: Schema[java.time.Instant] = fromPrimitiveType(PrimitiveType.Instant(Validation.None))

  implicit val localDate: Schema[java.time.LocalDate] = fromPrimitiveType(PrimitiveType.LocalDate(Validation.None))

  implicit val localDateTime: Schema[java.time.LocalDateTime] =
    fromPrimitiveType(PrimitiveType.LocalDateTime(Validation.None))

  implicit val localTime: Schema[java.time.LocalTime] = fromPrimitiveType(PrimitiveType.LocalTime(Validation.None))

  implicit val month: Schema[java.time.Month] = fromPrimitiveType(PrimitiveType.Month(Validation.None))

  implicit val monthDay: Schema[java.time.MonthDay] = fromPrimitiveType(PrimitiveType.MonthDay(Validation.None))

  implicit val offsetDateTime: Schema[java.time.OffsetDateTime] =
    fromPrimitiveType(PrimitiveType.OffsetDateTime(Validation.None))

  implicit val offsetTime: Schema[java.time.OffsetTime] = fromPrimitiveType(PrimitiveType.OffsetTime(Validation.None))

  implicit val period: Schema[java.time.Period] = fromPrimitiveType(PrimitiveType.Period(Validation.None))

  implicit val year: Schema[java.time.Year] = fromPrimitiveType(PrimitiveType.Year(Validation.None))

  implicit val yearMonth: Schema[java.time.YearMonth] = fromPrimitiveType(PrimitiveType.YearMonth(Validation.None))

  implicit val zoneId: Schema[java.time.ZoneId] = fromPrimitiveType(PrimitiveType.ZoneId(Validation.None))

  implicit val zoneOffset: Schema[java.time.ZoneOffset] = fromPrimitiveType(PrimitiveType.ZoneOffset(Validation.None))

  implicit val zonedDateTime: Schema[java.time.ZonedDateTime] =
    fromPrimitiveType(PrimitiveType.ZonedDateTime(Validation.None))

  implicit val currency: Schema[java.util.Currency] = fromPrimitiveType(PrimitiveType.Currency(Validation.None))

  implicit val uuid: Schema[java.util.UUID] = fromPrimitiveType(PrimitiveType.UUID(Validation.None))

  def fromPrimitiveType[A](primitiveType: PrimitiveType[A]): Schema[A] = new Schema(Reflect.primitive(primitiveType))

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

  implicit def array[A](implicit element: Schema[A]): Schema[Array[A]] = new Schema(Reflect.array(element.reflect))

  implicit def map[A, B](implicit key: Schema[A], value: Schema[B]): Schema[collection.immutable.Map[A, B]] =
    new Schema(Reflect.map(key.reflect, value.reflect))
}
