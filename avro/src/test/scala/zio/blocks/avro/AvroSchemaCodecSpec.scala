package zio.blocks.avro

import zio.blocks.schema.Schema
import zio.test.Assertion._
import zio.test._
import java.time._
import java.util.{Currency, UUID}

object AvroSchemaCodecSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AvroSchemaCodecSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip[Unit]("\"null\"")
      },
      test("Boolean") {
        roundTrip[Boolean]("\"boolean\"")
      },
      test("Byte") {
        roundTrip[Byte]("{\"type\":\"int\",\"zio.blocks.avro.primitiveType\":\"Byte\"}")
      },
      test("Short") {
        roundTrip[Short]("{\"type\":\"int\",\"zio.blocks.avro.primitiveType\":\"Short\"}")
      },
      test("Int") {
        roundTrip[Int]("\"int\"")
      },
      test("Long") {
        roundTrip[Long]("\"long\"")
      },
      test("Float") {
        roundTrip[Float]("\"float\"")
      },
      test("Double") {
        roundTrip[Double]("\"double\"")
      },
      test("Char") {
        roundTrip[Char]("{\"type\":\"int\",\"zio.blocks.avro.primitiveType\":\"Char\"}")
      },
      test("String") {
        roundTrip[String]("\"string\"")
      },
      test("BigInt") {
        roundTrip[BigInt]("{\"type\":\"bytes\",\"zio.blocks.avro.primitiveType\":\"BigInt\"}")
      },
      test("BigDecimal") {
        roundTrip[BigDecimal](
          "{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"mantissa\",\"type\":\"bytes\"},{\"name\":\"scale\",\"type\":\"int\"},{\"name\":\"precision\",\"type\":\"int\"},{\"name\":\"roundingMode\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"BigDecimal\"}"
        )
      },
      test("DayOfWeek") {
        roundTrip[DayOfWeek]("{\"type\":\"int\",\"zio.blocks.avro.primitiveType\":\"DayOfWeek\"}")
      },
      test("Duration") {
        roundTrip[Duration](
          "{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"seconds\",\"type\":\"long\"},{\"name\":\"nanos\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"Duration\"}"
        )
      },
      test("Instant") {
        roundTrip[Instant](
          "{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"epochSecond\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"Instant\"}"
        )
      },
      test("LocalDate") {
        roundTrip[LocalDate](
          "{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"LocalDate\"}"
        )
      },
      test("LocalDateTime") {
        roundTrip[LocalDateTime](
          "{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"LocalDateTime\"}"
        )
      },
      test("LocalTime") {
        roundTrip[LocalTime](
          "{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"LocalTime\"}"
        )
      },
      test("Month") {
        roundTrip[Month]("{\"type\":\"int\",\"zio.blocks.avro.primitiveType\":\"Month\"}")
      },
      test("MonthDay") {
        roundTrip[MonthDay](
          "{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"MonthDay\"}"
        )
      },
      test("OffsetDateTime") {
        roundTrip[OffsetDateTime](
          "{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offsetSecond\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"OffsetDateTime\"}"
        )
      },
      test("OffsetTime") {
        roundTrip[OffsetTime](
          "{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offsetSecond\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"OffsetTime\"}"
        )
      },
      test("Period") {
        roundTrip[Period](
          "{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"years\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"days\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"Period\"}"
        )
      },
      test("Year") {
        roundTrip[Year]("{\"type\":\"int\",\"zio.blocks.avro.primitiveType\":\"Year\"}")
      },
      test("YearMonth") {
        roundTrip[YearMonth](
          "{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"}],\"zio.blocks.avro.primitiveType\":\"YearMonth\"}"
        )
      },
      test("ZoneId") {
        roundTrip[ZoneId]("{\"type\":\"string\",\"zio.blocks.avro.primitiveType\":\"ZoneId\"}")
      },
      test("ZoneOffset") {
        roundTrip[ZoneOffset]("{\"type\":\"int\",\"zio.blocks.avro.primitiveType\":\"ZoneOffset\"}")
      },
      test("ZonedDateTime") {
        roundTrip[ZonedDateTime](
          "{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offsetSecond\",\"type\":\"int\"},{\"name\":\"zoneId\",\"type\":\"string\"}],\"zio.blocks.avro.primitiveType\":\"ZonedDateTime\"}"
        )
      },
      test("Currency") {
        roundTrip[Currency](
          "{\"type\":\"fixed\",\"name\":\"Currency\",\"size\":3,\"zio.blocks.avro.primitiveType\":\"Currency\"}"
        )
      },
      test("UUID") {
        roundTrip[UUID]("{\"type\":\"fixed\",\"name\":\"UUID\",\"size\":16,\"zio.blocks.avro.primitiveType\":\"UUID\"}")
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip[Record](
          "{\"type\":\"record\",\"name\":\"Record\",\"namespace\":\"zio.blocks.avro.AvroSchemaCodecSpec\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"int\"}],\"zio.blocks.avro.typeName\":{\"namespace\":{\"values\":[\"AvroSchemaCodecSpec\"],\"packages\":[\"zio\",\"blocks\",\"avro\"]},\"name\":\"Record\"}}"
        )
      }
    ),
    suite("sequences")(
      test("list") {
        roundTrip[List[Int]](
          "{\"type\":\"array\",\"items\":\"int\",\"zio.blocks.avro.typeName\":{\"namespace\":{\"packages\":[\"scala\",\"collection\",\"immutable\"]},\"name\":\"List\",\"params\":[{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"Int\"}]}}"
        )
      }
    ),
    suite("maps")(
      test("string key map") {
        roundTrip[Map[String, Int]](
          "{\"type\":\"map\",\"values\":\"int\",\"zio.blocks.avro.typeName\":{\"namespace\":{\"packages\":[\"scala\",\"collection\",\"immutable\"]},\"name\":\"Map\",\"params\":[{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"String\"},{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"Int\"}]}}"
        )
      },
      test("non string key map") {
        encode[Map[Int, Int]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"int\"}]},\"zio.blocks.avro.typeName\":{\"namespace\":{\"packages\":[\"scala\",\"collection\",\"immutable\"]},\"name\":\"Map\",\"params\":[{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"Int\"},{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"Int\"}]}}"
        )
      }
    ),
    suite("variants")(
      test("option") {
        implicit val schema: Schema[Option[Int]] = Schema.derived
        encode[Option[Int]](
          "[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[],\"zio.blocks.avro.typeName\":{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"None\"}},{\"type\":\"record\",\"name\":\"Some\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}],\"zio.blocks.avro.typeName\":{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"Some\",\"params\":[{\"namespace\":{\"packages\":[\"scala\"]},\"name\":\"Int\"}]}}]"
        )
      }
    )
  )

  def roundTrip[A](expectedAvroSchemaJson: String)(implicit schema: Schema[A]): TestResult = {
    val avroSchemaJson = AvroSchemaCodec.encode(schema)
    assert(avroSchemaJson)(equalTo(expectedAvroSchemaJson)) &&
    assert(AvroSchemaCodec.decode(avroSchemaJson))(isRight(equalTo(schema)))
  }

  def encode[A](expectedAvroSchemaJson: String)(implicit schema: Schema[A]): TestResult =
    assert(AvroSchemaCodec.encode(schema))(equalTo(expectedAvroSchemaJson))

  case class Record(name: String, value: Int)

  object Record {
    implicit val schemaRecord: Schema[Record] = Schema.derived
  }
}
