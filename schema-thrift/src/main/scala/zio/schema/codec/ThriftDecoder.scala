package zio.schema.codec

import java.time._
import java.util.UUID
import scala.util.Try
import org.apache.thrift.protocol._
import zio.Chunk
import zio.schema._
import zio.schema.MutableSchemaBasedValueBuilder.ReadingFieldResult
import zio.schema.codec.DecodeError.MalformedFieldWithPath

final class ThriftDecoder(chunk: Chunk[Byte])
    extends MutableSchemaBasedValueBuilder[Any, ThriftDecoder.DecoderContext] {
  private val read = new ChunkTransport.Read(chunk)
  private val p    = new TBinaryProtocol(read)

  private def decodePrimitive[A](f: TProtocol => A, name: String): ThriftDecoder.Path => A = path =>
    Try(f(p)).fold(_ => throw MalformedFieldWithPath(path, s"Unable to decode $name"), identity)

  override protected def createPrimitive(context: ThriftDecoder.DecoderContext, typ: StandardType[_]): Any = typ match {
    case StandardType.UnitType       => ()
    case StandardType.StringType     => decodePrimitive(_.readString(), "String")(context.path)
    case StandardType.BoolType       => decodePrimitive(_.readBool(), "Boolean")(context.path)
    case StandardType.ByteType       => decodePrimitive(_.readByte(), "Byte")(context.path)
    case StandardType.ShortType      => decodePrimitive(_.readI16(), "Short")(context.path)
    case StandardType.IntType        => decodePrimitive(_.readI32(), "Int")(context.path)
    case StandardType.LongType       => decodePrimitive(_.readI64(), "Long")(context.path)
    case StandardType.FloatType      => decodePrimitive(_.readDouble().toFloat, "Float")(context.path)
    case StandardType.DoubleType     => decodePrimitive(_.readDouble(), "Double")(context.path)
    case StandardType.BinaryType     => decodePrimitive(p => Chunk.fromByteBuffer(p.readBinary()), "Binary")(context.path)
    case StandardType.UUIDType       => UUID.fromString(decodePrimitive(_.readString(), "UUID")(context.path))
    case StandardType.DayOfWeekType  => DayOfWeek.of(decodePrimitive(_.readByte(), "Byte")(context.path).toInt)
    case StandardType.MonthType      => Month.of(decodePrimitive(_.readByte(), "Byte")(context.path).toInt)
    case StandardType.YearType       => Year.of(decodePrimitive(_.readI32(), "Int")(context.path))
    case StandardType.ZoneIdType     => ZoneId.of(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.ZoneOffsetType => ZoneOffset.ofTotalSeconds(decodePrimitive(_.readI32(), "Int")(context.path))
    case StandardType.BigDecimalType =>
      p.readFieldBegin(); val unscaled  = new java.math.BigInteger(p.readBinary().array())
      p.readFieldBegin(); val precision = p.readI32()
      p.readFieldBegin(); val scale     = p.readI32(); p.readFieldBegin()
      new java.math.BigDecimal(unscaled, scale, new java.math.MathContext(precision))
    case StandardType.DurationType =>
      p.readFieldBegin(); val s = decodePrimitive(_.readI64(), "Long")(context.path)
      p.readFieldBegin(); val n = decodePrimitive(_.readI32(), "Int")(context.path); p.readFieldBegin()
      java.time.Duration.ofSeconds(s, n.toLong)
    case StandardType.PeriodType =>
      p.readFieldBegin(); val y = p.readI32(); p.readFieldBegin(); val m = p.readI32(); p.readFieldBegin();
      val d                     = p.readI32(); p.readFieldBegin()
      Period.of(y, m, d)
    case StandardType.MonthDayType =>
      p.readFieldBegin(); val mon = p.readI32(); p.readFieldBegin(); val day = p.readI32(); p.readFieldBegin()
      MonthDay.of(mon, day)
    case StandardType.YearMonthType =>
      p.readFieldBegin(); val y = p.readI32(); p.readFieldBegin(); val mon = p.readI32(); p.readFieldBegin()
      YearMonth.of(y, mon)
    case StandardType.InstantType        => Instant.parse(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.LocalDateType      => LocalDate.parse(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.LocalTimeType      => LocalTime.parse(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.LocalDateTimeType  => LocalDateTime.parse(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.OffsetTimeType     => OffsetTime.parse(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.OffsetDateTimeType =>
      OffsetDateTime.parse(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.ZonedDateTimeType => ZonedDateTime.parse(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.CurrencyType      =>
      java.util.Currency.getInstance(decodePrimitive(_.readString(), "String")(context.path))
    case StandardType.CharType =>
      val s = decodePrimitive(_.readString(), "String")(context.path)
      if (s.length == 1) s.charAt(0) else throw MalformedFieldWithPath(context.path, s"Expected char, found string: $s")
    case _ => throw MalformedFieldWithPath(context.path, s"Unsupported primitive $typ")
  }

  override protected def startCreatingRecord(
    context: ThriftDecoder.DecoderContext,
    record: Schema.Record[_]
  ): ThriftDecoder.DecoderContext = context

  override protected def startReadingField(
    context: ThriftDecoder.DecoderContext,
    record: Schema.Record[_],
    index: Int
  ): ReadingFieldResult[ThriftDecoder.DecoderContext] = {
    val tfield = p.readFieldBegin()
    if (tfield.`type` == TType.STOP) ReadingFieldResult.Finished()
    else ReadingFieldResult.ReadField(context.copy(path = context.path :+ s"fieldId:${tfield.id}"), tfield.id - 1)
  }

  override protected def createRecord(
    context: ThriftDecoder.DecoderContext,
    record: Schema.Record[_],
    values: Chunk[(Int, Any)]
  ): Any = {
    val map  = values.toMap
    val args = record.fields.zipWithIndex.map { case (f, i) =>
      map.get(i) match {
        case Some(v) => v
        case None    =>
          val maybeEmpty = emptyValue(f.schema)
          if (f.optional || f.transient || maybeEmpty.isDefined) {
            maybeEmpty.getOrElse(f.defaultValue.getOrElse(null))
          } else {
            throw MalformedFieldWithPath(context.path :+ f.name, s"Missing mandatory field: ${f.name}")
          }
      }
    }
    zio.Unsafe.unsafe(implicit u =>
      record.construct(args).fold(m => throw MalformedFieldWithPath(context.path, m), identity)
    )
  }

  override protected def startCreatingEnum(
    context: ThriftDecoder.DecoderContext,
    cases: Chunk[Schema.Case[_, _]]
  ): (ThriftDecoder.DecoderContext, Int) = {
    val f = p.readFieldBegin(); val idx = f.id - 1
    (context.copy(path = context.path :+ s"case:${cases(idx).id}"), idx)
  }
  override protected def createEnum(
    context: ThriftDecoder.DecoderContext,
    cases: Chunk[Schema.Case[_, _]],
    index: Int,
    value: Any
  ): Any = { p.readFieldBegin(); value }
  override protected def startCreatingSequence(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Sequence[_, _, _]
  ): Option[ThriftDecoder.DecoderContext] = {
    val b = p.readListBegin(); if (b.size == 0) None else Some(context.copy(expectedCount = Some(b.size)))
  }
  override protected def finishedCreatingOneSequenceElement(
    context: ThriftDecoder.DecoderContext,
    index: Int
  ): Boolean = context.expectedCount.exists(_ > index + 1)
  override protected def createSequence(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Sequence[_, _, _],
    v: Chunk[Any]
  ): Any = s.fromChunk.asInstanceOf[Chunk[Any] => Any](v)
  override protected def startCreatingDictionary(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Map[_, _]
  ): Option[ThriftDecoder.DecoderContext] = {
    val b = p.readMapBegin(); if (b.size == 0) None else Some(context.copy(expectedCount = Some(b.size)))
  }
  override protected def finishedCreatingOneDictionaryElement(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Map[_, _],
    index: Int
  ): Boolean = context.expectedCount.exists(_ > index + 1)
  override protected def createDictionary(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Map[_, _],
    v: Chunk[(Any, Any)]
  ): Any = v.toMap
  override protected def startCreatingSet(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Set[_]
  ): Option[ThriftDecoder.DecoderContext] = {
    val b = p.readSetBegin(); if (b.size == 0) None else Some(context.copy(expectedCount = Some(b.size)))
  }
  override protected def finishedCreatingOneSetElement(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Set[_],
    index: Int
  ): Boolean                                                                                                    = context.expectedCount.exists(_ > index + 1)
  override protected def createSet(context: ThriftDecoder.DecoderContext, s: Schema.Set[_], v: Chunk[Any]): Any =
    v.toSet
  override protected def startCreatingOptional(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Optional[_]
  ): Option[ThriftDecoder.DecoderContext] =
    p.readFieldBegin().id match {
      case 1  => None; case 2 => Some(context);
      case id => throw MalformedFieldWithPath(context.path, s"Wrong Option ID $id")
    }
  override protected def createOptional(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Optional[_],
    v: Option[Any]
  ): Any = { p.readFieldBegin(); v }
  override protected def startCreatingEither(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Either[_, _]
  ): Either[ThriftDecoder.DecoderContext, ThriftDecoder.DecoderContext] =
    p.readFieldBegin().id match {
      case 1 => Left(context); case 2 => Right(context);
      case _ => throw MalformedFieldWithPath(context.path, "Either error")
    }
  override protected def createEither(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Either[_, _],
    v: Either[Any, Any]
  ): Any = v
  override protected def startCreatingFallback(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Fallback[_, _]
  ): Fallback[ThriftDecoder.DecoderContext, ThriftDecoder.DecoderContext] =
    p.readFieldBegin().id match {
      case 1 => Fallback.Left(context); case 2 => Fallback.Right(context); case 3 => Fallback.Both(context, context);
      case _ => throw MalformedFieldWithPath(context.path, "Fallback error")
    }
  override protected def createFallback(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Fallback[_, _],
    v: Fallback[Any, Any]
  ): Any = v
  override protected def startCreatingTuple(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Tuple2[_, _]
  ): ThriftDecoder.DecoderContext = { p.readFieldBegin(); context }
  override protected def startReadingSecondTupleElement(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Tuple2[_, _]
  ): ThriftDecoder.DecoderContext = { p.readFieldBegin(); context }
  override protected def createTuple(
    context: ThriftDecoder.DecoderContext,
    s: Schema.Tuple2[_, _],
    l: Any,
    r: Any
  ): Any = { p.readFieldBegin(); (l, r) }
  override protected def transform(
    context: ThriftDecoder.DecoderContext,
    v: Any,
    f: Any => Either[String, Any],
    s: Schema[_]
  ): Any =
    f(v).fold(m => throw MalformedFieldWithPath(context.path, m), identity)
  override protected def createDynamic(context: ThriftDecoder.DecoderContext): Option[Any] = None
  override def startCreatingOneSequenceElement(
    context: ThriftDecoder.DecoderContext,
    schema: Schema.Sequence[_, _, _]
  ): ThriftDecoder.DecoderContext = context
  override def startCreatingOneDictionaryElement(
    context: ThriftDecoder.DecoderContext,
    schema: Schema.Map[_, _]
  ): ThriftDecoder.DecoderContext = context
  override def startCreatingOneDictionaryValue(
    context: ThriftDecoder.DecoderContext,
    schema: Schema.Map[_, _]
  ): ThriftDecoder.DecoderContext = context
  override def startCreatingOneSetElement(
    context: ThriftDecoder.DecoderContext,
    schema: Schema.Set[_]
  ): ThriftDecoder.DecoderContext = context
  override def startReadingRightFallback(
    context: ThriftDecoder.DecoderContext,
    schema: Schema.Fallback[_, _]
  ): ThriftDecoder.DecoderContext = { p.readFieldBegin(); context }

  override protected def fail(context: ThriftDecoder.DecoderContext, message: String): Any =
    throw MalformedFieldWithPath(context.path, message)
  override protected val initialContext: ThriftDecoder.DecoderContext = ThriftDecoder.DecoderContext(Chunk.empty, None)

  private def emptyValue[A](schema: Schema[A]): Option[A] = schema match {
    case Schema.Lazy(s)                             => emptyValue(s())
    case Schema.Optional(_, _)                      => Some(None)
    case Schema.Sequence(_, fromChunk, _, _, _)     => Some(fromChunk(Chunk.empty))
    case Schema.Primitive(StandardType.UnitType, _) => Some(())
    case _                                          => None
  }
}

object ThriftDecoder {
  type Path = Chunk[String]
  final case class DecoderContext(path: Path, expectedCount: Option[Int])
}
