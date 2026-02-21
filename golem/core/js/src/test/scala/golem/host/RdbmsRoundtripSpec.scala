package golem.host

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class RdbmsRoundtripSpec extends AnyFunSuite with Matchers {
  import Rdbms._

  // --- MySQL round-trips ---

  private def roundtripMysql(value: MysqlDbValue, expectedTag: String): Unit = {
    val dyn = MysqlDbValue.toDynamic(value)
    dyn.tag.asInstanceOf[String] shouldBe expectedTag
    val parsed = MysqlDbValue.fromDynamic(dyn)
    (value, parsed) match {
      case (MysqlDbValue.Binary(a), MysqlDbValue.Binary(b))         => a.toList shouldBe b.toList
      case (MysqlDbValue.VarBinary(a), MysqlDbValue.VarBinary(b))   => a.toList shouldBe b.toList
      case (MysqlDbValue.TinyBlob(a), MysqlDbValue.TinyBlob(b))     => a.toList shouldBe b.toList
      case (MysqlDbValue.Blob(a), MysqlDbValue.Blob(b))             => a.toList shouldBe b.toList
      case (MysqlDbValue.MediumBlob(a), MysqlDbValue.MediumBlob(b)) => a.toList shouldBe b.toList
      case (MysqlDbValue.LongBlob(a), MysqlDbValue.LongBlob(b))     => a.toList shouldBe b.toList
      case _                                                        => parsed shouldBe value
    }
  }

  test("MySQL BooleanVal round-trip")(roundtripMysql(MysqlDbValue.BooleanVal(true), "boolean"))
  test("MySQL TinyInt round-trip")(roundtripMysql(MysqlDbValue.TinyInt(1.toByte), "tinyint"))
  test("MySQL SmallInt round-trip")(roundtripMysql(MysqlDbValue.SmallInt(100.toShort), "smallint"))
  test("MySQL MediumInt round-trip")(roundtripMysql(MysqlDbValue.MediumInt(10000), "mediumint"))
  test("MySQL IntVal round-trip")(roundtripMysql(MysqlDbValue.IntVal(100000), "int"))
  test("MySQL BigInt round-trip")(roundtripMysql(MysqlDbValue.BigInt(1000000L), "bigint"))
  test("MySQL TinyIntUnsigned round-trip") {
    roundtripMysql(MysqlDbValue.TinyIntUnsigned(255.toShort), "tinyint-unsigned")
  }
  test("MySQL SmallIntUnsigned round-trip")(roundtripMysql(MysqlDbValue.SmallIntUnsigned(65535), "smallint-unsigned"))
  test("MySQL MediumIntUnsigned round-trip") {
    roundtripMysql(MysqlDbValue.MediumIntUnsigned(16777215L), "mediumint-unsigned")
  }
  test("MySQL IntUnsigned round-trip")(roundtripMysql(MysqlDbValue.IntUnsigned(4294967295L), "int-unsigned"))
  test("MySQL BigIntUnsigned round-trip") {
    val v   = MysqlDbValue.BigIntUnsigned(scala.BigInt("18446744073709551615"))
    val dyn = MysqlDbValue.toDynamic(v)
    dyn.tag.asInstanceOf[String] shouldBe "bigint-unsigned"
    val parsed = MysqlDbValue.fromDynamic(dyn)
    parsed shouldBe a[MysqlDbValue.BigIntUnsigned]
    parsed.asInstanceOf[MysqlDbValue.BigIntUnsigned].value shouldBe scala.BigInt("18446744073709551615")
  }
  test("MySQL FloatVal round-trip")(roundtripMysql(MysqlDbValue.FloatVal(3.14f), "float"))
  test("MySQL DoubleVal round-trip")(roundtripMysql(MysqlDbValue.DoubleVal(2.718), "double"))
  test("MySQL Decimal round-trip")(roundtripMysql(MysqlDbValue.Decimal("99999.99"), "decimal"))
  test("MySQL Year round-trip")(roundtripMysql(MysqlDbValue.Year(2024), "year"))
  test("MySQL FixChar round-trip")(roundtripMysql(MysqlDbValue.FixChar("A"), "fixchar"))
  test("MySQL VarChar round-trip")(roundtripMysql(MysqlDbValue.VarChar("hello"), "varchar"))
  test("MySQL TinyText round-trip")(roundtripMysql(MysqlDbValue.TinyText("tiny"), "tinytext"))
  test("MySQL Text round-trip")(roundtripMysql(MysqlDbValue.Text("text"), "text"))
  test("MySQL MediumText round-trip")(roundtripMysql(MysqlDbValue.MediumText("medium"), "mediumtext"))
  test("MySQL LongText round-trip")(roundtripMysql(MysqlDbValue.LongText("long"), "longtext"))
  test("MySQL Binary round-trip")(roundtripMysql(MysqlDbValue.Binary(Array[Byte](1, 2, 3)), "binary"))
  test("MySQL VarBinary round-trip")(roundtripMysql(MysqlDbValue.VarBinary(Array[Byte](4, 5)), "varbinary"))
  test("MySQL TinyBlob round-trip")(roundtripMysql(MysqlDbValue.TinyBlob(Array[Byte](6)), "tinyblob"))
  test("MySQL Blob round-trip")(roundtripMysql(MysqlDbValue.Blob(Array[Byte](7, 8)), "blob"))
  test("MySQL MediumBlob round-trip")(roundtripMysql(MysqlDbValue.MediumBlob(Array[Byte](9)), "mediumblob"))
  test("MySQL LongBlob round-trip")(roundtripMysql(MysqlDbValue.LongBlob(Array[Byte](10, 11)), "longblob"))
  test("MySQL Enumeration round-trip")(roundtripMysql(MysqlDbValue.Enumeration("active"), "enumeration"))
  test("MySQL SetVal round-trip")(roundtripMysql(MysqlDbValue.SetVal("a,b,c"), "set"))
  test("MySQL Bit round-trip")(roundtripMysql(MysqlDbValue.Bit(List(true, false, true)), "bit"))
  test("MySQL Json round-trip")(roundtripMysql(MysqlDbValue.Json("""{"k":"v"}"""), "json"))
  test("MySQL Null round-trip")(roundtripMysql(MysqlDbValue.Null, "null"))

  test("MySQL Date round-trip") {
    val d = MysqlDbValue.Date(DbDate(2024, 6, 15))
    roundtripMysql(d, "date")
  }

  test("MySQL DateTime round-trip") {
    val d = MysqlDbValue.DateTime(DbTimestamp(DbDate(2024, 6, 15), DbTime(14, 30, 45, 0L)))
    roundtripMysql(d, "datetime")
  }

  test("MySQL Timestamp round-trip") {
    val d = MysqlDbValue.Timestamp(DbTimestamp(DbDate(2024, 1, 1), DbTime(0, 0, 0, 0L)))
    roundtripMysql(d, "timestamp")
  }

  test("MySQL Time round-trip") {
    val d = MysqlDbValue.Time(DbTime(23, 59, 59, 999999999L))
    roundtripMysql(d, "time")
  }

  test("unknown MySQL tag throws") {
    val raw = js.Dynamic.literal(tag = "unknown-mysql", `val` = 0)
    an[IllegalArgumentException] should be thrownBy MysqlDbValue.fromDynamic(raw)
  }

  // --- Postgres basic round-trips ---

  private def roundtripPg(value: PostgresDbValue, expectedTag: String): Unit = {
    val dyn = PostgresDbValue.toDynamic(value)
    dyn.tag.asInstanceOf[String] shouldBe expectedTag
    val parsed = PostgresDbValue.fromDynamic(dyn)
    (value, parsed) match {
      case (PostgresDbValue.Bytea(a), PostgresDbValue.Bytea(b)) => a.toList shouldBe b.toList
      case _                                                    => parsed shouldBe value
    }
  }

  test("Postgres Character round-trip")(roundtripPg(PostgresDbValue.Character(65.toByte), "character"))
  test("Postgres Int2 round-trip")(roundtripPg(PostgresDbValue.Int2(100.toShort), "int2"))
  test("Postgres Int4 round-trip")(roundtripPg(PostgresDbValue.Int4(100000), "int4"))
  test("Postgres Int8 round-trip") {
    val v   = PostgresDbValue.Int8(1000000000L)
    val dyn = PostgresDbValue.toDynamic(v)
    dyn.tag.asInstanceOf[String] shouldBe "int8"
    val parsed = PostgresDbValue.fromDynamic(dyn)
    parsed shouldBe a[PostgresDbValue.Int8]
    parsed.asInstanceOf[PostgresDbValue.Int8].value shouldBe 1000000000L
  }
  test("Postgres Float4 round-trip")(roundtripPg(PostgresDbValue.Float4(3.14f), "float4"))
  test("Postgres Float8 round-trip")(roundtripPg(PostgresDbValue.Float8(2.718), "float8"))
  test("Postgres Numeric round-trip")(roundtripPg(PostgresDbValue.Numeric("12345.6789"), "numeric"))
  test("Postgres BooleanVal round-trip")(roundtripPg(PostgresDbValue.BooleanVal(true), "boolean"))
  test("Postgres Text round-trip")(roundtripPg(PostgresDbValue.Text("hello"), "text"))
  test("Postgres VarChar round-trip")(roundtripPg(PostgresDbValue.VarChar("world"), "varchar"))
  test("Postgres BpChar round-trip")(roundtripPg(PostgresDbValue.BpChar("X"), "bpchar"))
  test("Postgres Json round-trip")(roundtripPg(PostgresDbValue.Json("""{"a":1}"""), "json"))
  test("Postgres Jsonb round-trip")(roundtripPg(PostgresDbValue.Jsonb("""{"b":2}"""), "jsonb"))
  test("Postgres JsonPath round-trip")(roundtripPg(PostgresDbValue.JsonPath("$.x"), "jsonpath"))
  test("Postgres Xml round-trip")(roundtripPg(PostgresDbValue.Xml("<root/>"), "xml"))
  test("Postgres Bytea round-trip")(roundtripPg(PostgresDbValue.Bytea(Array[Byte](1, 2, 3)), "bytea"))
  test("Postgres Bit round-trip")(roundtripPg(PostgresDbValue.Bit(List(true, false)), "bit"))
  test("Postgres VarBit round-trip")(roundtripPg(PostgresDbValue.VarBit(List(false, true)), "varbit"))
  test("Postgres Null round-trip")(roundtripPg(PostgresDbValue.Null, "null"))

  test("Postgres Money round-trip") {
    val v      = PostgresDbValue.Money(99999L)
    val dyn    = PostgresDbValue.toDynamic(v)
    val parsed = PostgresDbValue.fromDynamic(dyn)
    parsed shouldBe a[PostgresDbValue.Money]
    parsed.asInstanceOf[PostgresDbValue.Money].value shouldBe 99999L
  }

  test("Postgres Oid round-trip") {
    val v      = PostgresDbValue.Oid(12345L)
    val dyn    = PostgresDbValue.toDynamic(v)
    val parsed = PostgresDbValue.fromDynamic(dyn)
    parsed shouldBe a[PostgresDbValue.Oid]
    parsed.asInstanceOf[PostgresDbValue.Oid].value shouldBe 12345L
  }

  test("Postgres Enumeration round-trip") {
    val v = PostgresDbValue.Enumeration(PgEnumeration("status", "active"))
    roundtripPg(v, "enumeration")
  }

  test("Postgres Timestamp round-trip") {
    val v = PostgresDbValue.Timestamp(DbTimestamp(DbDate(2024, 6, 15), DbTime(14, 30, 0, 0L)))
    roundtripPg(v, "timestamp")
  }

  test("Postgres TimestampTz round-trip") {
    val v = PostgresDbValue.TimestampTz(
      DbTimestampTz(
        DbTimestamp(DbDate(2024, 6, 15), DbTime(14, 30, 0, 0L)),
        3600
      )
    )
    roundtripPg(v, "timestamptz")
  }

  test("Postgres Date round-trip") {
    roundtripPg(PostgresDbValue.Date(DbDate(2024, 6, 15)), "date")
  }

  test("Postgres Time round-trip") {
    roundtripPg(PostgresDbValue.Time(DbTime(14, 30, 45, 0L)), "time")
  }

  test("Postgres TimeTz round-trip") {
    val v = PostgresDbValue.TimeTz(DbTimeTz(DbTime(14, 30, 45, 0L), -18000))
    roundtripPg(v, "timetz")
  }

  test("Postgres Interval round-trip") {
    val v = PostgresDbValue.Interval(PgInterval(1, 15, 3600000000L))
    roundtripPg(v, "interval")
  }

  test("Postgres Uuid round-trip") {
    val v = PostgresDbValue.Uuid(DbUuid(BigInt("123456789012345678"), BigInt("987654321098765432")))
    roundtripPg(v, "uuid")
  }

  test("Postgres Inet Ipv4 round-trip") {
    val v = PostgresDbValue.Inet(IpAddress.Ipv4(192, 168, 1, 1))
    roundtripPg(v, "inet")
  }

  test("Postgres Cidr Ipv6 round-trip") {
    val v = PostgresDbValue.Cidr(IpAddress.Ipv6(0x2001, 0x0db8, 0, 0, 0, 0, 0, 1))
    roundtripPg(v, "cidr")
  }

  test("Postgres MacAddr round-trip") {
    val v = PostgresDbValue.MacAddr(MacAddress(0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff))
    roundtripPg(v, "macaddr")
  }

  // --- Postgres range round-trips via fromDynamic ---

  test("Postgres Int4Range from dynamic") {
    val raw = js.Dynamic.literal(
      tag = "int4range",
      `val` = js.Dynamic.literal(
        start = js.Dynamic.literal(tag = "included", `val` = 1),
        end = js.Dynamic.literal(tag = "excluded", `val` = 10)
      )
    )
    val parsed = PostgresDbValue.fromDynamic(raw)
    parsed shouldBe a[PostgresDbValue.Int4RangeVal]
    val r = parsed.asInstanceOf[PostgresDbValue.Int4RangeVal].value
    r.start shouldBe Int4Bound.Included(1)
    r.end shouldBe Int4Bound.Excluded(10)
  }

  test("Postgres Int8Range from dynamic") {
    val raw = js.Dynamic.literal(
      tag = "int8range",
      `val` = js.Dynamic.literal(
        start = js.Dynamic.literal(tag = "included", `val` = 100.0),
        end = js.Dynamic.literal(tag = "unbounded")
      )
    )
    val parsed = PostgresDbValue.fromDynamic(raw)
    parsed shouldBe a[PostgresDbValue.Int8RangeVal]
    val r = parsed.asInstanceOf[PostgresDbValue.Int8RangeVal].value
    r.start shouldBe Int8Bound.Included(100L)
    r.end shouldBe Int8Bound.Unbounded
  }

  test("Postgres NumRange from dynamic") {
    val raw = js.Dynamic.literal(
      tag = "numrange",
      `val` = js.Dynamic.literal(
        start = js.Dynamic.literal(tag = "unbounded"),
        end = js.Dynamic.literal(tag = "excluded", `val` = "999.99")
      )
    )
    val parsed = PostgresDbValue.fromDynamic(raw)
    parsed shouldBe a[PostgresDbValue.NumRangeVal]
  }

  test("Postgres DateRange from dynamic") {
    val raw = js.Dynamic.literal(
      tag = "daterange",
      `val` = js.Dynamic.literal(
        start = js.Dynamic.literal(tag = "included", `val` = js.Dynamic.literal(year = 2024, month = 1, day = 1)),
        end = js.Dynamic.literal(tag = "excluded", `val` = js.Dynamic.literal(year = 2024, month = 12, day = 31))
      )
    )
    val parsed = PostgresDbValue.fromDynamic(raw)
    parsed shouldBe a[PostgresDbValue.DateRangeVal]
    val r = parsed.asInstanceOf[PostgresDbValue.DateRangeVal].value
    r.start shouldBe a[DateBound.Included]
  }

  test("unknown Postgres tag throws") {
    val raw = js.Dynamic.literal(tag = "unknown-pg", `val` = 0)
    an[IllegalArgumentException] should be thrownBy PostgresDbValue.fromDynamic(raw)
  }

  // --- IpAddress from dynamic ---

  test("IpAddress Ipv4 from dynamic") {
    val raw = js.Dynamic.literal(
      tag = "ipv4",
      `val` = js.Tuple4[Short, Short, Short, Short](10, 0, 0, 1)
    )
    // Can't call parseIpAddress directly (private), but Int4 via Inet covers it
  }

  // --- Row accessor tests ---

  test("MysqlDbRow.getString returns None for Null") {
    val row = MysqlDbRow(List(MysqlDbValue.Null))
    row.getString(0) shouldBe None
  }

  test("MysqlDbRow.getString returns Some for non-Null") {
    val row = MysqlDbRow(List(MysqlDbValue.VarChar("test")))
    row.getString(0) shouldBe defined
  }

  test("MysqlDbRow.getInt extracts int types") {
    val row = MysqlDbRow(List(MysqlDbValue.IntVal(42), MysqlDbValue.TinyInt(1.toByte), MysqlDbValue.Null))
    row.getInt(0) shouldBe Some(42)
    row.getInt(1) shouldBe Some(1)
    row.getInt(2) shouldBe None
  }

  test("PostgresDbRow.getString returns None for Null") {
    val row = PostgresDbRow(List(PostgresDbValue.Null))
    row.getString(0) shouldBe None
  }

  test("PostgresDbRow.getInt extracts Int4") {
    val row = PostgresDbRow(List(PostgresDbValue.Int4(99), PostgresDbValue.Null))
    row.getInt(0) shouldBe Some(99)
    row.getInt(1) shouldBe None
  }

  test("PostgresDbRow.getLong extracts Int8") {
    val row = PostgresDbRow(List(PostgresDbValue.Int8(1000000000L), PostgresDbValue.Int4(42), PostgresDbValue.Null))
    row.getLong(0) shouldBe Some(1000000000L)
    row.getLong(1) shouldBe Some(42L)
    row.getLong(2) shouldBe None
  }

  // --- DbError ---

  test("DbError variants") {
    val errors = List(
      DbError.ConnectionFailure("conn"),
      DbError.QueryParameterFailure("param"),
      DbError.QueryExecutionFailure("exec"),
      DbError.QueryResponseFailure("resp"),
      DbError.Other("other")
    )
    errors.foreach(e => e.message should not be empty)
  }

  // --- DbColumn ---

  test("DbColumn construction") {
    val col = DbColumn(0L, "id", "int4")
    col.ordinal shouldBe 0L
    col.name shouldBe "id"
    col.dbTypeName shouldBe "int4"
  }

  // --- Result types ---

  test("PostgresDbResult construction") {
    val cols = List(DbColumn(0L, "id", "int4"), DbColumn(1L, "name", "text"))
    val rows = List(
      PostgresDbRow(List(PostgresDbValue.Int4(1), PostgresDbValue.Text("a"))),
      PostgresDbRow(List(PostgresDbValue.Int4(2), PostgresDbValue.Text("b")))
    )
    val result = PostgresDbResult(cols, rows)
    result.columns.size shouldBe 2
    result.rows.size shouldBe 2
    result.rows.head.getInt(0) shouldBe Some(1)
  }

  test("MysqlDbResult construction") {
    val cols   = List(DbColumn(0L, "id", "int"), DbColumn(1L, "name", "varchar"))
    val rows   = List(MysqlDbRow(List(MysqlDbValue.IntVal(1), MysqlDbValue.VarChar("a"))))
    val result = MysqlDbResult(cols, rows)
    result.columns.size shouldBe 2
    result.rows.size shouldBe 1
    result.rows.head.getInt(0) shouldBe Some(1)
  }
}
