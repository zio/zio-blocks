package golem.host

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facades for the Golem RDBMS host packages.
 *
 * Provides fully typed wrappers for `golem:rdbms/postgres@0.0.1`,
 * `golem:rdbms/mysql@0.0.1`, and shared types from `golem:rdbms/types@0.0.1`.
 */
object Rdbms {

  // ===========================================================================
  // Shared types (golem:rdbms/types@0.0.1)
  // ===========================================================================

  final case class DbDate(year: Int, month: Short, day: Short)

  final case class DbTime(hour: Short, minute: Short, second: Short, nanosecond: Long)

  final case class DbTimestamp(date: DbDate, time: DbTime)

  final case class DbTimestampTz(timestamp: DbTimestamp, offset: Int)

  final case class DbTimeTz(time: DbTime, offset: Int)

  final case class DbUuid(highBits: BigInt, lowBits: BigInt)

  sealed trait IpAddress extends Product with Serializable
  object IpAddress {
    final case class Ipv4(a: Short, b: Short, c: Short, d: Short)                         extends IpAddress
    final case class Ipv6(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int) extends IpAddress
  }

  final case class MacAddress(a: Short, b: Short, c: Short, d: Short, e: Short, f: Short)

  // Parsing helpers for shared types

  private def parseDbDate(raw: js.Dynamic): DbDate =
    DbDate(raw.year.asInstanceOf[Int], raw.month.asInstanceOf[Short], raw.day.asInstanceOf[Short])

  private def parseDbTime(raw: js.Dynamic): DbTime =
    DbTime(
      raw.hour.asInstanceOf[Short],
      raw.minute.asInstanceOf[Short],
      raw.second.asInstanceOf[Short],
      raw.nanosecond.asInstanceOf[Double].toLong
    )

  private def parseDbTimestamp(raw: js.Dynamic): DbTimestamp =
    DbTimestamp(parseDbDate(raw.date.asInstanceOf[js.Dynamic]), parseDbTime(raw.time.asInstanceOf[js.Dynamic]))

  private def parseDbTimestampTz(raw: js.Dynamic): DbTimestampTz =
    DbTimestampTz(parseDbTimestamp(raw.timestamp.asInstanceOf[js.Dynamic]), raw.offset.asInstanceOf[Int])

  private def parseDbTimeTz(raw: js.Dynamic): DbTimeTz =
    DbTimeTz(parseDbTime(raw.time.asInstanceOf[js.Dynamic]), raw.offset.asInstanceOf[Int])

  private def parseDbUuid(raw: js.Dynamic): DbUuid =
    DbUuid(BigInt(raw.highBits.toString), BigInt(raw.lowBits.toString))

  private def parseIpAddress(raw: js.Dynamic): IpAddress = {
    val tag = raw.tag.asInstanceOf[String]
    val v   = raw.selectDynamic("val").asInstanceOf[js.Dynamic]
    tag match {
      case "ipv4" =>
        val t = v.asInstanceOf[js.Tuple4[Short, Short, Short, Short]]
        IpAddress.Ipv4(t._1, t._2, t._3, t._4)
      case "ipv6" =>
        val arr = v.asInstanceOf[js.Array[Int]]
        IpAddress.Ipv6(arr(0), arr(1), arr(2), arr(3), arr(4), arr(5), arr(6), arr(7))
      case other =>
        throw new IllegalArgumentException(s"Unknown IpAddress tag: $other")
    }
  }

  private def parseMacAddress(raw: js.Dynamic): MacAddress = {
    val o = raw.octets.asInstanceOf[js.Array[Short]]
    MacAddress(o(0), o(1), o(2), o(3), o(4), o(5))
  }

  // toDynamic helpers for shared types

  private def dbDateToDynamic(d: DbDate): js.Dynamic =
    js.Dynamic.literal(year = d.year, month = d.month, day = d.day)

  private def dbTimeToDynamic(t: DbTime): js.Dynamic =
    js.Dynamic.literal(hour = t.hour, minute = t.minute, second = t.second, nanosecond = t.nanosecond.toDouble)

  private def dbTimestampToDynamic(ts: DbTimestamp): js.Dynamic =
    js.Dynamic.literal(date = dbDateToDynamic(ts.date), time = dbTimeToDynamic(ts.time))

  private def dbTimestampTzToDynamic(tstz: DbTimestampTz): js.Dynamic =
    js.Dynamic.literal(timestamp = dbTimestampToDynamic(tstz.timestamp), offset = tstz.offset)

  private def dbTimeTzToDynamic(ttz: DbTimeTz): js.Dynamic =
    js.Dynamic.literal(time = dbTimeToDynamic(ttz.time), offset = ttz.offset)

  private def dbUuidToDynamic(u: DbUuid): js.Dynamic =
    js.Dynamic.literal(highBits = js.BigInt(u.highBits.toString), lowBits = js.BigInt(u.lowBits.toString))

  private def ipAddressToDynamic(ip: IpAddress): js.Dynamic = ip match {
    case IpAddress.Ipv4(a, b, c, d)             => js.Dynamic.literal(tag = "ipv4", `val` = js.Tuple4(a, b, c, d))
    case IpAddress.Ipv6(a, b, c, d, e, f, g, h) =>
      js.Dynamic.literal(tag = "ipv6", `val` = js.Array(a, b, c, d, e, f, g, h))
  }

  private def macAddressToDynamic(m: MacAddress): js.Dynamic =
    js.Dynamic.literal(octets = js.Array(m.a, m.b, m.c, m.d, m.e, m.f))

  // ===========================================================================
  // MySQL db-value (36 variants)
  // ===========================================================================

  sealed trait MysqlDbValue extends Product with Serializable
  object MysqlDbValue {
    final case class BooleanVal(value: Boolean)          extends MysqlDbValue
    final case class TinyInt(value: Byte)                extends MysqlDbValue
    final case class SmallInt(value: Short)              extends MysqlDbValue
    final case class MediumInt(value: Int)               extends MysqlDbValue
    final case class IntVal(value: Int)                  extends MysqlDbValue
    final case class BigInt(value: Long)                 extends MysqlDbValue
    final case class TinyIntUnsigned(value: Short)       extends MysqlDbValue
    final case class SmallIntUnsigned(value: Int)        extends MysqlDbValue
    final case class MediumIntUnsigned(value: Long)      extends MysqlDbValue
    final case class IntUnsigned(value: Long)            extends MysqlDbValue
    final case class BigIntUnsigned(value: scala.BigInt) extends MysqlDbValue
    final case class FloatVal(value: Float)              extends MysqlDbValue
    final case class DoubleVal(value: Double)            extends MysqlDbValue
    final case class Decimal(value: String)              extends MysqlDbValue
    final case class Date(value: DbDate)                 extends MysqlDbValue
    final case class DateTime(value: DbTimestamp)        extends MysqlDbValue
    final case class Timestamp(value: DbTimestamp)       extends MysqlDbValue
    final case class Time(value: DbTime)                 extends MysqlDbValue
    final case class Year(value: Int)                    extends MysqlDbValue
    final case class FixChar(value: String)              extends MysqlDbValue
    final case class VarChar(value: String)              extends MysqlDbValue
    final case class TinyText(value: String)             extends MysqlDbValue
    final case class Text(value: String)                 extends MysqlDbValue
    final case class MediumText(value: String)           extends MysqlDbValue
    final case class LongText(value: String)             extends MysqlDbValue
    final case class Binary(value: Array[Byte])          extends MysqlDbValue
    final case class VarBinary(value: Array[Byte])       extends MysqlDbValue
    final case class TinyBlob(value: Array[Byte])        extends MysqlDbValue
    final case class Blob(value: Array[Byte])            extends MysqlDbValue
    final case class MediumBlob(value: Array[Byte])      extends MysqlDbValue
    final case class LongBlob(value: Array[Byte])        extends MysqlDbValue
    final case class Enumeration(value: String)          extends MysqlDbValue
    final case class SetVal(value: String)               extends MysqlDbValue
    final case class Bit(value: List[Boolean])           extends MysqlDbValue
    final case class Json(value: String)                 extends MysqlDbValue
    case object Null                                     extends MysqlDbValue

    def fromDynamic(raw: js.Dynamic): MysqlDbValue = {
      val tag = raw.tag.asInstanceOf[String]
      tag match {
        case "null" => Null
        case _      =>
          val v = raw.selectDynamic("val")
          tag match {
            case "boolean"            => BooleanVal(v.asInstanceOf[Boolean])
            case "tinyint"            => TinyInt(v.asInstanceOf[Byte])
            case "smallint"           => SmallInt(v.asInstanceOf[Short])
            case "mediumint"          => MediumInt(v.asInstanceOf[Int])
            case "int"                => IntVal(v.asInstanceOf[Int])
            case "bigint"             => BigInt(v.asInstanceOf[Double].toLong)
            case "tinyint-unsigned"   => TinyIntUnsigned(v.asInstanceOf[Short])
            case "smallint-unsigned"  => SmallIntUnsigned(v.asInstanceOf[Int])
            case "mediumint-unsigned" => MediumIntUnsigned(v.asInstanceOf[Double].toLong)
            case "int-unsigned"       => IntUnsigned(v.asInstanceOf[Double].toLong)
            case "bigint-unsigned"    => BigIntUnsigned(scala.BigInt(v.toString))
            case "float"              => FloatVal(v.asInstanceOf[Float])
            case "double"             => DoubleVal(v.asInstanceOf[Double])
            case "decimal"            => Decimal(v.asInstanceOf[String])
            case "date"               => Date(parseDbDate(v.asInstanceOf[js.Dynamic]))
            case "datetime"           => DateTime(parseDbTimestamp(v.asInstanceOf[js.Dynamic]))
            case "timestamp"          => Timestamp(parseDbTimestamp(v.asInstanceOf[js.Dynamic]))
            case "time"               => Time(parseDbTime(v.asInstanceOf[js.Dynamic]))
            case "year"               => Year(v.asInstanceOf[Int])
            case "fixchar"            => FixChar(v.asInstanceOf[String])
            case "varchar"            => VarChar(v.asInstanceOf[String])
            case "tinytext"           => TinyText(v.asInstanceOf[String])
            case "text"               => Text(v.asInstanceOf[String])
            case "mediumtext"         => MediumText(v.asInstanceOf[String])
            case "longtext"           => LongText(v.asInstanceOf[String])
            case "binary"             => Binary(parseByteList(v))
            case "varbinary"          => VarBinary(parseByteList(v))
            case "tinyblob"           => TinyBlob(parseByteList(v))
            case "blob"               => Blob(parseByteList(v))
            case "mediumblob"         => MediumBlob(parseByteList(v))
            case "longblob"           => LongBlob(parseByteList(v))
            case "enumeration"        => Enumeration(v.asInstanceOf[String])
            case "set"                => SetVal(v.asInstanceOf[String])
            case "bit"                => Bit(v.asInstanceOf[js.Array[Boolean]].toList)
            case "json"               => Json(v.asInstanceOf[String])
            case other                => throw new IllegalArgumentException(s"Unknown MySQL db-value tag: $other")
          }
      }
    }

    def toDynamic(v: MysqlDbValue): js.Dynamic = v match {
      case Null                 => js.Dynamic.literal(tag = "null")
      case BooleanVal(b)        => js.Dynamic.literal(tag = "boolean", `val` = b)
      case TinyInt(n)           => js.Dynamic.literal(tag = "tinyint", `val` = n)
      case SmallInt(n)          => js.Dynamic.literal(tag = "smallint", `val` = n)
      case MediumInt(n)         => js.Dynamic.literal(tag = "mediumint", `val` = n)
      case IntVal(n)            => js.Dynamic.literal(tag = "int", `val` = n)
      case BigInt(n)            => js.Dynamic.literal(tag = "bigint", `val` = n.toDouble)
      case TinyIntUnsigned(n)   => js.Dynamic.literal(tag = "tinyint-unsigned", `val` = n)
      case SmallIntUnsigned(n)  => js.Dynamic.literal(tag = "smallint-unsigned", `val` = n)
      case MediumIntUnsigned(n) => js.Dynamic.literal(tag = "mediumint-unsigned", `val` = n.toDouble)
      case IntUnsigned(n)       => js.Dynamic.literal(tag = "int-unsigned", `val` = n.toDouble)
      case BigIntUnsigned(n)    => js.Dynamic.literal(tag = "bigint-unsigned", `val` = js.BigInt(n.toString))
      case FloatVal(n)          => js.Dynamic.literal(tag = "float", `val` = n)
      case DoubleVal(n)         => js.Dynamic.literal(tag = "double", `val` = n)
      case Decimal(s)           => js.Dynamic.literal(tag = "decimal", `val` = s)
      case Date(d)              => js.Dynamic.literal(tag = "date", `val` = dbDateToDynamic(d))
      case DateTime(ts)         => js.Dynamic.literal(tag = "datetime", `val` = dbTimestampToDynamic(ts))
      case Timestamp(ts)        => js.Dynamic.literal(tag = "timestamp", `val` = dbTimestampToDynamic(ts))
      case Time(t)              => js.Dynamic.literal(tag = "time", `val` = dbTimeToDynamic(t))
      case Year(y)              => js.Dynamic.literal(tag = "year", `val` = y)
      case FixChar(s)           => js.Dynamic.literal(tag = "fixchar", `val` = s)
      case VarChar(s)           => js.Dynamic.literal(tag = "varchar", `val` = s)
      case TinyText(s)          => js.Dynamic.literal(tag = "tinytext", `val` = s)
      case Text(s)              => js.Dynamic.literal(tag = "text", `val` = s)
      case MediumText(s)        => js.Dynamic.literal(tag = "mediumtext", `val` = s)
      case LongText(s)          => js.Dynamic.literal(tag = "longtext", `val` = s)
      case Binary(b)            => js.Dynamic.literal(tag = "binary", `val` = byteListToDynamic(b))
      case VarBinary(b)         => js.Dynamic.literal(tag = "varbinary", `val` = byteListToDynamic(b))
      case TinyBlob(b)          => js.Dynamic.literal(tag = "tinyblob", `val` = byteListToDynamic(b))
      case Blob(b)              => js.Dynamic.literal(tag = "blob", `val` = byteListToDynamic(b))
      case MediumBlob(b)        => js.Dynamic.literal(tag = "mediumblob", `val` = byteListToDynamic(b))
      case LongBlob(b)          => js.Dynamic.literal(tag = "longblob", `val` = byteListToDynamic(b))
      case Enumeration(s)       => js.Dynamic.literal(tag = "enumeration", `val` = s)
      case SetVal(s)            => js.Dynamic.literal(tag = "set", `val` = s)
      case Bit(bs)              => js.Dynamic.literal(tag = "bit", `val` = js.Array(bs: _*))
      case Json(s)              => js.Dynamic.literal(tag = "json", `val` = s)
    }
  }

  // ===========================================================================
  // Postgres supporting types (ranges, composites, etc.)
  // ===========================================================================

  final case class PgInterval(months: Int, days: Int, microseconds: Long)

  sealed trait Int4Bound extends Product with Serializable
  object Int4Bound {
    final case class Included(value: Int) extends Int4Bound
    final case class Excluded(value: Int) extends Int4Bound
    case object Unbounded                 extends Int4Bound
  }

  final case class Int4Range(start: Int4Bound, end: Int4Bound)

  sealed trait Int8Bound extends Product with Serializable
  object Int8Bound {
    final case class Included(value: Long) extends Int8Bound
    final case class Excluded(value: Long) extends Int8Bound
    case object Unbounded                  extends Int8Bound
  }

  final case class Int8Range(start: Int8Bound, end: Int8Bound)

  sealed trait NumBound extends Product with Serializable
  object NumBound {
    final case class Included(value: String) extends NumBound
    final case class Excluded(value: String) extends NumBound
    case object Unbounded                    extends NumBound
  }

  final case class NumRange(start: NumBound, end: NumBound)

  sealed trait TsBound extends Product with Serializable
  object TsBound {
    final case class Included(value: DbTimestamp) extends TsBound
    final case class Excluded(value: DbTimestamp) extends TsBound
    case object Unbounded                         extends TsBound
  }

  final case class TsRange(start: TsBound, end: TsBound)

  sealed trait TsTzBound extends Product with Serializable
  object TsTzBound {
    final case class Included(value: DbTimestampTz) extends TsTzBound
    final case class Excluded(value: DbTimestampTz) extends TsTzBound
    case object Unbounded                           extends TsTzBound
  }

  final case class TsTzRange(start: TsTzBound, end: TsTzBound)

  sealed trait DateBound extends Product with Serializable
  object DateBound {
    final case class Included(value: DbDate) extends DateBound
    final case class Excluded(value: DbDate) extends DateBound
    case object Unbounded                    extends DateBound
  }

  final case class DateRange(start: DateBound, end: DateBound)

  final case class PgEnumeration(name: String, value: String)

  final case class PgComposite(name: String, values: List[PostgresDbValue])

  final case class PgDomain(name: String, value: PostgresDbValue)

  sealed trait PgValueBound extends Product with Serializable
  object PgValueBound {
    final case class Included(value: PostgresDbValue) extends PgValueBound
    final case class Excluded(value: PostgresDbValue) extends PgValueBound
    case object Unbounded                             extends PgValueBound
  }

  final case class PgValuesRange(start: PgValueBound, end: PgValueBound)

  final case class PgRange(name: String, value: PgValuesRange)

  // Parsing helpers for Postgres supporting types

  private def parseInt4Bound(raw: js.Dynamic): Int4Bound = {
    val tag = raw.tag.asInstanceOf[String]
    tag match {
      case "included"  => Int4Bound.Included(raw.selectDynamic("val").asInstanceOf[Int])
      case "excluded"  => Int4Bound.Excluded(raw.selectDynamic("val").asInstanceOf[Int])
      case "unbounded" => Int4Bound.Unbounded
      case other       => throw new IllegalArgumentException(s"Unknown Int4Bound tag: $other")
    }
  }

  private def parseInt8Bound(raw: js.Dynamic): Int8Bound = {
    val tag = raw.tag.asInstanceOf[String]
    tag match {
      case "included"  => Int8Bound.Included(raw.selectDynamic("val").asInstanceOf[Double].toLong)
      case "excluded"  => Int8Bound.Excluded(raw.selectDynamic("val").asInstanceOf[Double].toLong)
      case "unbounded" => Int8Bound.Unbounded
      case other       => throw new IllegalArgumentException(s"Unknown Int8Bound tag: $other")
    }
  }

  private def parseNumBound(raw: js.Dynamic): NumBound = {
    val tag = raw.tag.asInstanceOf[String]
    tag match {
      case "included"  => NumBound.Included(raw.selectDynamic("val").asInstanceOf[String])
      case "excluded"  => NumBound.Excluded(raw.selectDynamic("val").asInstanceOf[String])
      case "unbounded" => NumBound.Unbounded
      case other       => throw new IllegalArgumentException(s"Unknown NumBound tag: $other")
    }
  }

  private def parseTsBound(raw: js.Dynamic): TsBound = {
    val tag = raw.tag.asInstanceOf[String]
    tag match {
      case "included"  => TsBound.Included(parseDbTimestamp(raw.selectDynamic("val").asInstanceOf[js.Dynamic]))
      case "excluded"  => TsBound.Excluded(parseDbTimestamp(raw.selectDynamic("val").asInstanceOf[js.Dynamic]))
      case "unbounded" => TsBound.Unbounded
      case other       => throw new IllegalArgumentException(s"Unknown TsBound tag: $other")
    }
  }

  private def parseTsTzBound(raw: js.Dynamic): TsTzBound = {
    val tag = raw.tag.asInstanceOf[String]
    tag match {
      case "included"  => TsTzBound.Included(parseDbTimestampTz(raw.selectDynamic("val").asInstanceOf[js.Dynamic]))
      case "excluded"  => TsTzBound.Excluded(parseDbTimestampTz(raw.selectDynamic("val").asInstanceOf[js.Dynamic]))
      case "unbounded" => TsTzBound.Unbounded
      case other       => throw new IllegalArgumentException(s"Unknown TsTzBound tag: $other")
    }
  }

  private def parseDateBound(raw: js.Dynamic): DateBound = {
    val tag = raw.tag.asInstanceOf[String]
    tag match {
      case "included"  => DateBound.Included(parseDbDate(raw.selectDynamic("val").asInstanceOf[js.Dynamic]))
      case "excluded"  => DateBound.Excluded(parseDbDate(raw.selectDynamic("val").asInstanceOf[js.Dynamic]))
      case "unbounded" => DateBound.Unbounded
      case other       => throw new IllegalArgumentException(s"Unknown DateBound tag: $other")
    }
  }

  private def parsePgInterval(raw: js.Dynamic): PgInterval =
    PgInterval(raw.months.asInstanceOf[Int], raw.days.asInstanceOf[Int], raw.microseconds.asInstanceOf[Double].toLong)

  private def parsePgEnumeration(raw: js.Dynamic): PgEnumeration =
    PgEnumeration(raw.name.asInstanceOf[String], raw.value.asInstanceOf[String])

  private def parsePgComposite(raw: js.Dynamic): PgComposite = {
    val vals = raw.values.asInstanceOf[js.Array[js.Dynamic]].toList.map { lazy_ =>
      PostgresDbValue.fromDynamic(lazy_.get().asInstanceOf[js.Dynamic])
    }
    PgComposite(raw.name.asInstanceOf[String], vals)
  }

  private def parsePgDomain(raw: js.Dynamic): PgDomain =
    PgDomain(raw.name.asInstanceOf[String], PostgresDbValue.fromDynamic(raw.value.get().asInstanceOf[js.Dynamic]))

  private def parsePgValueBound(raw: js.Dynamic): PgValueBound = {
    val tag = raw.tag.asInstanceOf[String]
    tag match {
      case "included" =>
        PgValueBound.Included(PostgresDbValue.fromDynamic(raw.selectDynamic("val").get().asInstanceOf[js.Dynamic]))
      case "excluded" =>
        PgValueBound.Excluded(PostgresDbValue.fromDynamic(raw.selectDynamic("val").get().asInstanceOf[js.Dynamic]))
      case "unbounded" => PgValueBound.Unbounded
      case other       => throw new IllegalArgumentException(s"Unknown PgValueBound tag: $other")
    }
  }

  // ===========================================================================
  // Postgres db-value (43 variants)
  // ===========================================================================

  sealed trait PostgresDbValue extends Product with Serializable
  object PostgresDbValue {
    final case class Character(value: Byte)                extends PostgresDbValue
    final case class Int2(value: Short)                    extends PostgresDbValue
    final case class Int4(value: Int)                      extends PostgresDbValue
    final case class Int8(value: Long)                     extends PostgresDbValue
    final case class Float4(value: Float)                  extends PostgresDbValue
    final case class Float8(value: Double)                 extends PostgresDbValue
    final case class Numeric(value: String)                extends PostgresDbValue
    final case class BooleanVal(value: Boolean)            extends PostgresDbValue
    final case class Text(value: String)                   extends PostgresDbValue
    final case class VarChar(value: String)                extends PostgresDbValue
    final case class BpChar(value: String)                 extends PostgresDbValue
    final case class Timestamp(value: DbTimestamp)         extends PostgresDbValue
    final case class TimestampTz(value: DbTimestampTz)     extends PostgresDbValue
    final case class Date(value: DbDate)                   extends PostgresDbValue
    final case class Time(value: DbTime)                   extends PostgresDbValue
    final case class TimeTz(value: DbTimeTz)               extends PostgresDbValue
    final case class Interval(value: PgInterval)           extends PostgresDbValue
    final case class Bytea(value: Array[Byte])             extends PostgresDbValue
    final case class Json(value: String)                   extends PostgresDbValue
    final case class Jsonb(value: String)                  extends PostgresDbValue
    final case class JsonPath(value: String)               extends PostgresDbValue
    final case class Xml(value: String)                    extends PostgresDbValue
    final case class Uuid(value: DbUuid)                   extends PostgresDbValue
    final case class Inet(value: IpAddress)                extends PostgresDbValue
    final case class Cidr(value: IpAddress)                extends PostgresDbValue
    final case class MacAddr(value: MacAddress)            extends PostgresDbValue
    final case class Bit(value: List[Boolean])             extends PostgresDbValue
    final case class VarBit(value: List[Boolean])          extends PostgresDbValue
    final case class Int4RangeVal(value: Int4Range)        extends PostgresDbValue
    final case class Int8RangeVal(value: Int8Range)        extends PostgresDbValue
    final case class NumRangeVal(value: NumRange)          extends PostgresDbValue
    final case class TsRangeVal(value: TsRange)            extends PostgresDbValue
    final case class TsTzRangeVal(value: TsTzRange)        extends PostgresDbValue
    final case class DateRangeVal(value: DateRange)        extends PostgresDbValue
    final case class Money(value: Long)                    extends PostgresDbValue
    final case class Oid(value: Long)                      extends PostgresDbValue
    final case class Enumeration(value: PgEnumeration)     extends PostgresDbValue
    final case class Composite(value: PgComposite)         extends PostgresDbValue
    final case class Domain(value: PgDomain)               extends PostgresDbValue
    final case class PgArray(value: List[PostgresDbValue]) extends PostgresDbValue
    final case class Range(value: PgRange)                 extends PostgresDbValue
    case object Null                                       extends PostgresDbValue

    def fromDynamic(raw: js.Dynamic): PostgresDbValue = {
      val tag = raw.tag.asInstanceOf[String]
      tag match {
        case "null" => Null
        case _      =>
          val v = raw.selectDynamic("val")
          tag match {
            case "character"   => Character(v.asInstanceOf[Byte])
            case "int2"        => Int2(v.asInstanceOf[Short])
            case "int4"        => Int4(v.asInstanceOf[Int])
            case "int8"        => Int8(v.asInstanceOf[Double].toLong)
            case "float4"      => Float4(v.asInstanceOf[Float])
            case "float8"      => Float8(v.asInstanceOf[Double])
            case "numeric"     => Numeric(v.asInstanceOf[String])
            case "boolean"     => BooleanVal(v.asInstanceOf[Boolean])
            case "text"        => Text(v.asInstanceOf[String])
            case "varchar"     => VarChar(v.asInstanceOf[String])
            case "bpchar"      => BpChar(v.asInstanceOf[String])
            case "timestamp"   => Timestamp(parseDbTimestamp(v.asInstanceOf[js.Dynamic]))
            case "timestamptz" => TimestampTz(parseDbTimestampTz(v.asInstanceOf[js.Dynamic]))
            case "date"        => Date(parseDbDate(v.asInstanceOf[js.Dynamic]))
            case "time"        => Time(parseDbTime(v.asInstanceOf[js.Dynamic]))
            case "timetz"      => TimeTz(parseDbTimeTz(v.asInstanceOf[js.Dynamic]))
            case "interval"    => Interval(parsePgInterval(v.asInstanceOf[js.Dynamic]))
            case "bytea"       => Bytea(parseByteList(v))
            case "json"        => Json(v.asInstanceOf[String])
            case "jsonb"       => Jsonb(v.asInstanceOf[String])
            case "jsonpath"    => JsonPath(v.asInstanceOf[String])
            case "xml"         => Xml(v.asInstanceOf[String])
            case "uuid"        => Uuid(parseDbUuid(v.asInstanceOf[js.Dynamic]))
            case "inet"        => Inet(parseIpAddress(v.asInstanceOf[js.Dynamic]))
            case "cidr"        => Cidr(parseIpAddress(v.asInstanceOf[js.Dynamic]))
            case "macaddr"     => MacAddr(parseMacAddress(v.asInstanceOf[js.Dynamic]))
            case "bit"         => Bit(v.asInstanceOf[js.Array[Boolean]].toList)
            case "varbit"      => VarBit(v.asInstanceOf[js.Array[Boolean]].toList)
            case "int4range"   =>
              val d = v.asInstanceOf[js.Dynamic]
              Int4RangeVal(
                Int4Range(
                  parseInt4Bound(d.start.asInstanceOf[js.Dynamic]),
                  parseInt4Bound(d.end.asInstanceOf[js.Dynamic])
                )
              )
            case "int8range" =>
              val d = v.asInstanceOf[js.Dynamic]
              Int8RangeVal(
                Int8Range(
                  parseInt8Bound(d.start.asInstanceOf[js.Dynamic]),
                  parseInt8Bound(d.end.asInstanceOf[js.Dynamic])
                )
              )
            case "numrange" =>
              val d = v.asInstanceOf[js.Dynamic]
              NumRangeVal(
                NumRange(parseNumBound(d.start.asInstanceOf[js.Dynamic]), parseNumBound(d.end.asInstanceOf[js.Dynamic]))
              )
            case "tsrange" =>
              val d = v.asInstanceOf[js.Dynamic]
              TsRangeVal(
                TsRange(parseTsBound(d.start.asInstanceOf[js.Dynamic]), parseTsBound(d.end.asInstanceOf[js.Dynamic]))
              )
            case "tstzrange" =>
              val d = v.asInstanceOf[js.Dynamic]
              TsTzRangeVal(
                TsTzRange(
                  parseTsTzBound(d.start.asInstanceOf[js.Dynamic]),
                  parseTsTzBound(d.end.asInstanceOf[js.Dynamic])
                )
              )
            case "daterange" =>
              val d = v.asInstanceOf[js.Dynamic]
              DateRangeVal(
                DateRange(
                  parseDateBound(d.start.asInstanceOf[js.Dynamic]),
                  parseDateBound(d.end.asInstanceOf[js.Dynamic])
                )
              )
            case "money"       => Money(v.asInstanceOf[Double].toLong)
            case "oid"         => Oid(v.asInstanceOf[Double].toLong)
            case "enumeration" => Enumeration(parsePgEnumeration(v.asInstanceOf[js.Dynamic]))
            case "composite"   => Composite(parsePgComposite(v.asInstanceOf[js.Dynamic]))
            case "domain"      => Domain(parsePgDomain(v.asInstanceOf[js.Dynamic]))
            case "array"       =>
              val arr = v.asInstanceOf[js.Array[js.Dynamic]].toList.map { lazy_ =>
                fromDynamic(lazy_.get().asInstanceOf[js.Dynamic])
              }
              PgArray(arr)
            case "range" =>
              val d      = v.asInstanceOf[js.Dynamic]
              val bounds = d.value.asInstanceOf[js.Dynamic]
              Range(
                PgRange(
                  d.name.asInstanceOf[String],
                  PgValuesRange(
                    parsePgValueBound(bounds.start.asInstanceOf[js.Dynamic]),
                    parsePgValueBound(bounds.end.asInstanceOf[js.Dynamic])
                  )
                )
              )
            case other => throw new IllegalArgumentException(s"Unknown Postgres db-value tag: $other")
          }
      }
    }

    def toDynamic(v: PostgresDbValue): js.Dynamic = v match {
      case Null              => js.Dynamic.literal(tag = "null")
      case Character(n)      => js.Dynamic.literal(tag = "character", `val` = n)
      case Int2(n)           => js.Dynamic.literal(tag = "int2", `val` = n)
      case Int4(n)           => js.Dynamic.literal(tag = "int4", `val` = n)
      case Int8(n)           => js.Dynamic.literal(tag = "int8", `val` = n.toDouble)
      case Float4(n)         => js.Dynamic.literal(tag = "float4", `val` = n)
      case Float8(n)         => js.Dynamic.literal(tag = "float8", `val` = n)
      case Numeric(s)        => js.Dynamic.literal(tag = "numeric", `val` = s)
      case BooleanVal(b)     => js.Dynamic.literal(tag = "boolean", `val` = b)
      case Text(s)           => js.Dynamic.literal(tag = "text", `val` = s)
      case VarChar(s)        => js.Dynamic.literal(tag = "varchar", `val` = s)
      case BpChar(s)         => js.Dynamic.literal(tag = "bpchar", `val` = s)
      case Timestamp(ts)     => js.Dynamic.literal(tag = "timestamp", `val` = dbTimestampToDynamic(ts))
      case TimestampTz(tstz) => js.Dynamic.literal(tag = "timestamptz", `val` = dbTimestampTzToDynamic(tstz))
      case Date(d)           => js.Dynamic.literal(tag = "date", `val` = dbDateToDynamic(d))
      case Time(t)           => js.Dynamic.literal(tag = "time", `val` = dbTimeToDynamic(t))
      case TimeTz(ttz)       => js.Dynamic.literal(tag = "timetz", `val` = dbTimeTzToDynamic(ttz))
      case Interval(i)       =>
        js.Dynamic.literal(
          tag = "interval",
          `val` = js.Dynamic.literal(months = i.months, days = i.days, microseconds = i.microseconds.toDouble)
        )
      case Bytea(b)       => js.Dynamic.literal(tag = "bytea", `val` = byteListToDynamic(b))
      case Json(s)        => js.Dynamic.literal(tag = "json", `val` = s)
      case Jsonb(s)       => js.Dynamic.literal(tag = "jsonb", `val` = s)
      case JsonPath(s)    => js.Dynamic.literal(tag = "jsonpath", `val` = s)
      case Xml(s)         => js.Dynamic.literal(tag = "xml", `val` = s)
      case Uuid(u)        => js.Dynamic.literal(tag = "uuid", `val` = dbUuidToDynamic(u))
      case Inet(ip)       => js.Dynamic.literal(tag = "inet", `val` = ipAddressToDynamic(ip))
      case Cidr(ip)       => js.Dynamic.literal(tag = "cidr", `val` = ipAddressToDynamic(ip))
      case MacAddr(m)     => js.Dynamic.literal(tag = "macaddr", `val` = macAddressToDynamic(m))
      case Bit(bs)        => js.Dynamic.literal(tag = "bit", `val` = js.Array(bs: _*))
      case VarBit(bs)     => js.Dynamic.literal(tag = "varbit", `val` = js.Array(bs: _*))
      case Money(n)       => js.Dynamic.literal(tag = "money", `val` = n.toDouble)
      case Oid(n)         => js.Dynamic.literal(tag = "oid", `val` = n.toDouble)
      case Enumeration(e) =>
        js.Dynamic.literal(tag = "enumeration", `val` = js.Dynamic.literal(name = e.name, value = e.value))
      case _ => throw new UnsupportedOperationException(s"toDynamic not yet implemented for: $v")
    }
  }

  // ===========================================================================
  // Shared byte-list helpers
  // ===========================================================================

  private def parseByteList(raw: js.Any): Array[Byte] = {
    val arr = raw.asInstanceOf[js.Array[Int]]
    arr.toArray.map(_.toByte)
  }

  private def byteListToDynamic(bytes: Array[Byte]): js.Array[Int] = {
    val arr = js.Array[Int]()
    bytes.foreach(b => arr.push(b.toInt & 0xff))
    arr
  }

  // ===========================================================================
  // Typed row types
  // ===========================================================================

  final case class MysqlDbRow(values: List[MysqlDbValue]) {
    def getString(index: Int): Option[String] = values(index) match {
      case MysqlDbValue.Null => None
      case v                 => Some(v.toString)
    }

    def getInt(index: Int): Option[Int] = values(index) match {
      case MysqlDbValue.Null         => None
      case MysqlDbValue.IntVal(n)    => Some(n)
      case MysqlDbValue.TinyInt(n)   => Some(n.toInt)
      case MysqlDbValue.SmallInt(n)  => Some(n.toInt)
      case MysqlDbValue.MediumInt(n) => Some(n)
      case v                         => Some(v.toString.toInt)
    }
  }

  final case class PostgresDbRow(values: List[PostgresDbValue]) {
    def getString(index: Int): Option[String] = values(index) match {
      case PostgresDbValue.Null => None
      case v                    => Some(v.toString)
    }

    def getInt(index: Int): Option[Int] = values(index) match {
      case PostgresDbValue.Null    => None
      case PostgresDbValue.Int4(n) => Some(n)
      case PostgresDbValue.Int2(n) => Some(n.toInt)
      case v                       => Some(v.toString.toInt)
    }

    def getLong(index: Int): Option[Long] = values(index) match {
      case PostgresDbValue.Null    => None
      case PostgresDbValue.Int8(n) => Some(n)
      case PostgresDbValue.Int4(n) => Some(n.toLong)
      case v                       => Some(v.toString.toLong)
    }
  }

  final case class DbColumn(ordinal: Long, name: String, dbTypeName: String)

  final case class MysqlDbResult(columns: List[DbColumn], rows: List[MysqlDbRow])

  final case class PostgresDbResult(columns: List[DbColumn], rows: List[PostgresDbRow])

  // ===========================================================================
  // Error types
  // ===========================================================================

  sealed trait DbError extends Product with Serializable {
    def message: String
  }

  object DbError {
    final case class ConnectionFailure(message: String)     extends DbError
    final case class QueryParameterFailure(message: String) extends DbError
    final case class QueryExecutionFailure(message: String) extends DbError
    final case class QueryResponseFailure(message: String)  extends DbError
    final case class Other(message: String)                 extends DbError

    private[Rdbms] def fromThrowable(t: Throwable): DbError = {
      val msg = if (t.getMessage != null) t.getMessage else t.toString
      Other(msg)
    }
  }

  // ===========================================================================
  // Native imports
  // ===========================================================================

  @js.native
  @JSImport("golem:rdbms/postgres@0.0.1", JSImport.Namespace)
  private object PostgresModule extends js.Object {
    val DbConnection: js.Dynamic = js.native
  }

  @js.native
  @JSImport("golem:rdbms/mysql@0.0.1", JSImport.Namespace)
  private object MysqlModule extends js.Object {
    val DbConnection: js.Dynamic = js.native
  }

  @js.native
  @JSImport("golem:rdbms/types@0.0.1", JSImport.Namespace)
  private object TypesModule extends js.Object

  // ===========================================================================
  // PostgresConnection resource
  // ===========================================================================

  final class PostgresConnection private[Rdbms] (private val underlying: js.Dynamic) {

    def query(statement: String, params: List[PostgresDbValue] = Nil): Either[DbError, PostgresDbResult] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(PostgresDbValue.toDynamic(p)))
        val raw = underlying.query(statement, jsParams).asInstanceOf[js.Dynamic]
        Right(parsePostgresResult(raw))
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def execute(statement: String, params: List[PostgresDbValue] = Nil): Either[DbError, Long] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(PostgresDbValue.toDynamic(p)))
        Right(underlying.execute(statement, jsParams).asInstanceOf[Double].toLong)
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def beginTransaction(): Either[DbError, PostgresTransaction] =
      try Right(new PostgresTransaction(underlying.beginTransaction().asInstanceOf[js.Dynamic]))
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }
  }

  final class PostgresTransaction private[Rdbms] (private val underlying: js.Dynamic) {

    def query(statement: String, params: List[PostgresDbValue] = Nil): Either[DbError, PostgresDbResult] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(PostgresDbValue.toDynamic(p)))
        Right(parsePostgresResult(underlying.query(statement, jsParams).asInstanceOf[js.Dynamic]))
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def execute(statement: String, params: List[PostgresDbValue] = Nil): Either[DbError, Long] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(PostgresDbValue.toDynamic(p)))
        Right(underlying.execute(statement, jsParams).asInstanceOf[Double].toLong)
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def commit(): Either[DbError, Unit] =
      try { underlying.commit(); Right(()) }
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def rollback(): Either[DbError, Unit] =
      try { underlying.rollback(); Right(()) }
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }
  }

  // ===========================================================================
  // MysqlConnection resource
  // ===========================================================================

  final class MysqlConnection private[Rdbms] (private val underlying: js.Dynamic) {

    def query(statement: String, params: List[MysqlDbValue] = Nil): Either[DbError, MysqlDbResult] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(MysqlDbValue.toDynamic(p)))
        val raw = underlying.query(statement, jsParams).asInstanceOf[js.Dynamic]
        Right(parseMysqlResult(raw))
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def execute(statement: String, params: List[MysqlDbValue] = Nil): Either[DbError, Long] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(MysqlDbValue.toDynamic(p)))
        Right(underlying.execute(statement, jsParams).asInstanceOf[Double].toLong)
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def beginTransaction(): Either[DbError, MysqlTransaction] =
      try Right(new MysqlTransaction(underlying.beginTransaction().asInstanceOf[js.Dynamic]))
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }
  }

  final class MysqlTransaction private[Rdbms] (private val underlying: js.Dynamic) {

    def query(statement: String, params: List[MysqlDbValue] = Nil): Either[DbError, MysqlDbResult] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(MysqlDbValue.toDynamic(p)))
        Right(parseMysqlResult(underlying.query(statement, jsParams).asInstanceOf[js.Dynamic]))
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def execute(statement: String, params: List[MysqlDbValue] = Nil): Either[DbError, Long] =
      try {
        val jsParams = js.Array[js.Dynamic]()
        params.foreach(p => jsParams.push(MysqlDbValue.toDynamic(p)))
        Right(underlying.execute(statement, jsParams).asInstanceOf[Double].toLong)
      } catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def commit(): Either[DbError, Unit] =
      try { underlying.commit(); Right(()) }
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }

    def rollback(): Either[DbError, Unit] =
      try { underlying.rollback(); Right(()) }
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }
  }

  // ===========================================================================
  // Result parsing
  // ===========================================================================

  private def parseColumns(raw: js.Dynamic): List[DbColumn] =
    raw.columns.asInstanceOf[js.Array[js.Dynamic]].toList.map { c =>
      DbColumn(
        ordinal = c.ordinal.asInstanceOf[Double].toLong,
        name = c.name.asInstanceOf[String],
        dbTypeName = c.dbTypeName.asInstanceOf[String]
      )
    }

  private def parsePostgresResult(raw: js.Dynamic): PostgresDbResult = {
    val cols = parseColumns(raw)
    val rows = raw.rows.asInstanceOf[js.Array[js.Dynamic]].toList.map { r =>
      PostgresDbRow(r.values.asInstanceOf[js.Array[js.Dynamic]].toList.map(PostgresDbValue.fromDynamic))
    }
    PostgresDbResult(cols, rows)
  }

  private def parseMysqlResult(raw: js.Dynamic): MysqlDbResult = {
    val cols = parseColumns(raw)
    val rows = raw.rows.asInstanceOf[js.Array[js.Dynamic]].toList.map { r =>
      MysqlDbRow(r.values.asInstanceOf[js.Array[js.Dynamic]].toList.map(MysqlDbValue.fromDynamic))
    }
    MysqlDbResult(cols, rows)
  }

  // ===========================================================================
  // Top-level factory methods
  // ===========================================================================

  object Postgres {
    def open(address: String): Either[DbError, PostgresConnection] =
      try Right(new PostgresConnection(PostgresModule.DbConnection.open(address).asInstanceOf[js.Dynamic]))
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }
  }

  object Mysql {
    def open(address: String): Either[DbError, MysqlConnection] =
      try Right(new MysqlConnection(MysqlModule.DbConnection.open(address).asInstanceOf[js.Dynamic]))
      catch { case t: Throwable => Left(DbError.fromThrowable(t)) }
  }

  def postgresRaw: Any = PostgresModule
  def mysqlRaw: Any    = MysqlModule
  def typesRaw: Any    = TypesModule
}
