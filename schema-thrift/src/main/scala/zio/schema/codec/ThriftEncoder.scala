package zio.schema.codec

import java.nio.ByteBuffer
import java.time._
import java.util.UUID
import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import org.apache.thrift.protocol._
import zio.Chunk
import zio.schema._

final class ThriftEncoder extends MutableSchemaBasedValueProcessor[Unit, ThriftEncoder.Context] {
  import ThriftEncoder._

  private val write = new ChunkTransport.Write()
  private val p     = new TBinaryProtocol(write)

  private[codec] def encode[A](schema: Schema[A], value: A): Chunk[Byte] = {
    process(schema, value)
    write.chunk
  }

  override protected def processPrimitive(context: Context, value: Any, typ: StandardType[Any]): Unit = {
    writeFieldBegin(context.fieldNumber, getPrimitiveType(typ))
    writePrimitiveType(typ, value)
  }

  override protected def startProcessingRecord(context: Context, schema: Schema.Record[_]): Unit =
    if (schema.fields.nonEmpty) writeFieldBegin(context.fieldNumber, TType.STRUCT)
    else { writeFieldBegin(context.fieldNumber, TType.BYTE); writeByte(0) }

  override protected def processRecord(context: Context, schema: Schema.Record[_], value: ListMap[String, Unit]): Unit =
    if (schema.fields.nonEmpty) writeFieldEnd()

  override protected def startProcessingEnum(context: Context, schema: Schema.Enum[_]): Unit =
    writeFieldBegin(context.fieldNumber, TType.STRUCT)

  override protected def processEnum(context: Context, schema: Schema.Enum[_], tuple: (String, Unit)): Unit =
    writeFieldEnd()

  override protected def startProcessingSequence(
    context: Context,
    schema: Schema.Sequence[_, _, _],
    size: Int
  ): Unit = {
    writeFieldBegin(context.fieldNumber, TType.LIST)
    writeListBegin(getType(schema.elementSchema), size)
  }

  override protected def processSequence(
    context: Context,
    schema: Schema.Sequence[_, _, _],
    value: Chunk[Unit]
  ): Unit = {}

  override protected def startProcessingDictionary(context: Context, schema: Schema.Map[_, _], size: Int): Unit = {
    writeFieldBegin(context.fieldNumber, TType.MAP)
    writeMapBegin(getType(schema.keySchema), getType(schema.valueSchema), size)
  }

  override protected def processDictionary(
    context: Context,
    schema: Schema.Map[_, _],
    value: Chunk[(Unit, Unit)]
  ): Unit = {}

  override protected def startProcessingSet(context: Context, schema: Schema.Set[_], size: Int): Unit = {
    writeFieldBegin(context.fieldNumber, TType.SET)
    writeSetBegin(getType(schema.elementSchema), size)
  }

  override protected def processSet(context: Context, schema: Schema.Set[_], value: Set[Unit]): Unit = {}

  override protected def startProcessingEither(context: Context, schema: Schema.Either[_, _]): Unit =
    writeFieldBegin(context.fieldNumber, TType.STRUCT)

  override protected def processEither(context: Context, schema: Schema.Either[_, _], value: Either[Unit, Unit]): Unit =
    writeFieldEnd()

  override protected def startProcessingFallback(context: Context, schema: Schema.Fallback[_, _]): Unit =
    writeFieldBegin(context.fieldNumber, TType.STRUCT)

  override protected def processFallback(
    context: Context,
    schema: Schema.Fallback[_, _],
    value: Fallback[Unit, Unit]
  ): Unit =
    writeFieldEnd()

  override def startProcessingOption(context: Context, schema: Schema.Optional[_]): Unit =
    writeFieldBegin(context.fieldNumber, TType.STRUCT)

  override protected def processOption(context: Context, schema: Schema.Optional[_], value: Option[Unit]): Unit = {
    if (value.isEmpty)
      processPrimitive(context.copy(fieldNumber = Some(1)), (), StandardType.UnitType.asInstanceOf[StandardType[Any]])
    writeFieldEnd()
  }

  override protected def startProcessingTuple(context: Context, schema: Schema.Tuple2[_, _]): Unit =
    writeFieldBegin(context.fieldNumber, TType.STRUCT)

  override protected def processTuple(context: Context, schema: Schema.Tuple2[_, _], left: Unit, right: Unit): Unit =
    writeFieldEnd()

  override protected def fail(context: Context, message: String): Unit                       = sys.error(message)
  override protected def processDynamic(context: Context, value: DynamicValue): Option[Unit] = None
  override protected val initialContext: Context                                             = Context(None)

  override protected def contextForRecordField(context: Context, index: Int, field: Schema.Field[_, _]): Context =
    context.copy(fieldNumber = Some((index + 1).toShort))

  override protected def contextForEnumConstructor(context: Context, index: Int, c: Schema.Case[_, _]): Context =
    context.copy(fieldNumber = Some((index + 1).toShort))

  override protected def contextForEither(context: Context, e: Either[Unit, Unit]): Context =
    e match {
      case Left(_) => context.copy(fieldNumber = Some(1)); case Right(_) => context.copy(fieldNumber = Some(2))
    }

  override protected def contextForFallback(context: Context, f: Fallback[Unit, Unit]): Context =
    f match {
      case Fallback.Left(_)    => context.copy(fieldNumber = Some(1));
      case Fallback.Right(_)   => context.copy(fieldNumber = Some(2));
      case Fallback.Both(_, _) => context.copy(fieldNumber = Some(3))
    }

  override protected def contextForOption(context: Context, o: Option[Unit]): Context =
    o match { case None => context.copy(fieldNumber = Some(1)); case Some(_) => context.copy(fieldNumber = Some(2)) }

  override protected def contextForTuple(context: Context, index: Int): Context =
    context.copy(fieldNumber = Some(index.toShort))

  override protected def contextForSequence(context: Context, s: Schema.Sequence[_, _, _], i: Int): Context =
    context.copy(fieldNumber = None)
  override protected def contextForMap(context: Context, s: Schema.Map[_, _], i: Int): Context =
    context.copy(fieldNumber = None)
  override protected def contextForSet(context: Context, s: Schema.Set[_], i: Int): Context =
    context.copy(fieldNumber = None)

  private def writeFieldBegin(fieldNumber: Option[Short], ttype: Byte): Unit =
    fieldNumber.foreach(num => p.writeFieldBegin(new TField("", ttype, num)))

  private def writeFieldEnd(): Unit                           = p.writeFieldStop()
  private def writeByte(value: Byte): Unit                    = p.writeByte(value)
  private def writeListBegin(ttype: Byte, count: Int): Unit   = p.writeListBegin(new TList(ttype, count))
  private def writeSetBegin(ttype: Byte, count: Int): Unit    = p.writeSetBegin(new TSet(ttype, count))
  private def writeMapBegin(kT: Byte, vT: Byte, c: Int): Unit = p.writeMapBegin(new TMap(kT, vT, c))

  private def writePrimitiveType[A](standardType: StandardType[A], value: A): Unit =
    (standardType, value) match {
      case (StandardType.UnitType, _)                             =>
      case (StandardType.StringType, str: String)                 => p.writeString(str)
      case (StandardType.BoolType, b: Boolean)                    => p.writeBool(b)
      case (StandardType.ByteType, v: Byte)                       => p.writeByte(v)
      case (StandardType.ShortType, v: Short)                     => p.writeI16(v)
      case (StandardType.IntType, v: Int)                         => p.writeI32(v)
      case (StandardType.LongType, v: Long)                       => p.writeI64(v)
      case (StandardType.FloatType, v: Float)                     => p.writeDouble(v.toDouble)
      case (StandardType.DoubleType, v: Double)                   => p.writeDouble(v)
      case (StandardType.BinaryType, bytes: Chunk[Byte])          => p.writeBinary(ByteBuffer.wrap(bytes.toArray))
      case (StandardType.CharType, c: Char)                       => p.writeString(c.toString)
      case (StandardType.UUIDType, u: UUID)                       => p.writeString(u.toString)
      case (StandardType.DayOfWeekType, v: DayOfWeek)             => p.writeByte(v.getValue.toByte)
      case (StandardType.MonthType, v: Month)                     => p.writeByte(v.getValue.toByte)
      case (StandardType.YearType, v: Year)                       => p.writeI32(v.getValue)
      case (StandardType.ZoneIdType, v: ZoneId)                   => p.writeString(v.getId)
      case (StandardType.ZoneOffsetType, v: ZoneOffset)           => p.writeI32(v.getTotalSeconds)
      case (StandardType.InstantType, v: Instant)                 => p.writeString(v.toString)
      case (StandardType.LocalDateType, v: LocalDate)             => p.writeString(v.toString)
      case (StandardType.LocalTimeType, v: LocalTime)             => p.writeString(v.toString)
      case (StandardType.LocalDateTimeType, v: LocalDateTime)     => p.writeString(v.toString)
      case (StandardType.OffsetTimeType, v: OffsetTime)           => p.writeString(v.toString)
      case (StandardType.OffsetDateTimeType, v: OffsetDateTime)   => p.writeString(v.toString)
      case (StandardType.ZonedDateTimeType, v: ZonedDateTime)     => p.writeString(v.toString)
      case (StandardType.CurrencyType, v: java.util.Currency)     => p.writeString(v.getCurrencyCode)
      case (StandardType.BigDecimalType, v: java.math.BigDecimal) =>
        writeFieldBegin(Some(1), TType.STRING); p.writeBinary(ByteBuffer.wrap(v.unscaledValue().toByteArray))
        writeFieldBegin(Some(2), TType.I32); p.writeI32(v.precision())
        writeFieldBegin(Some(3), TType.I32); p.writeI32(v.scale()); writeFieldEnd()
      case (StandardType.DurationType, v: java.time.Duration) =>
        writeFieldBegin(Some(1), TType.I64); p.writeI64(v.getSeconds)
        writeFieldBegin(Some(2), TType.I32); p.writeI32(v.getNano); writeFieldEnd()
      case (StandardType.PeriodType, v: Period) =>
        writeFieldBegin(Some(1), TType.I32); p.writeI32(v.getYears)
        writeFieldBegin(Some(2), TType.I32); p.writeI32(v.getMonths)
        writeFieldBegin(Some(3), TType.I32); p.writeI32(v.getDays); writeFieldEnd()
      case (StandardType.MonthDayType, v: MonthDay) =>
        writeFieldBegin(Some(1), TType.I32); p.writeI32(v.getMonthValue)
        writeFieldBegin(Some(2), TType.I32); p.writeI32(v.getDayOfMonth); writeFieldEnd()
      case (StandardType.YearMonthType, v: YearMonth) =>
        writeFieldBegin(Some(1), TType.I32); p.writeI32(v.getYear)
        writeFieldBegin(Some(2), TType.I32); p.writeI32(v.getMonthValue); writeFieldEnd()
      case _ => sys.error(s"No encoder for $standardType")
    }
}

object ThriftEncoder {
  final case class Context(fieldNumber: Option[Short])
  private def getPrimitiveType[A](standardType: StandardType[A]): Byte = standardType match {
    case StandardType.UnitType => TType.VOID
    case StandardType.StringType | StandardType.UUIDType | StandardType.CharType | StandardType.BinaryType |
        StandardType.BigIntegerType =>
      TType.STRING
    case StandardType.BoolType                                                       => TType.BOOL
    case StandardType.ByteType | StandardType.DayOfWeekType | StandardType.MonthType => TType.BYTE
    case StandardType.ShortType                                                      => TType.I16
    case StandardType.IntType | StandardType.YearType | StandardType.ZoneOffsetType  => TType.I32
    case StandardType.LongType                                                       => TType.I64
    case StandardType.FloatType | StandardType.DoubleType                            => TType.DOUBLE
    case _                                                                           => TType.STRUCT
  }
  @tailrec
  private def getType[A](schema: Schema[A]): Byte = schema match {
    case _: Schema.Record[_] | _: Schema.Enum[_] | _: Schema.Tuple2[_, _] | _: Schema.Either[_, _] => TType.STRUCT
    case _: Schema.Sequence[_, _, _]                                                               => TType.LIST
    case _: Schema.Map[_, _]                                                                       => TType.MAP
    case _: Schema.Set[_]                                                                          => TType.SET
    case Schema.Transform(s, _, _, _, _)                                                           => getType(s)
    case Schema.Primitive(p, _)                                                                    => getPrimitiveType(p)
    case Schema.Lazy(l)                                                                            => getType(l())
    case Schema.Optional(s, _)                                                                     => getType(s)
    case _                                                                                         => TType.VOID
  }
}
