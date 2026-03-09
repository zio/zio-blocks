package zio.blocks.schema.bson

import zio.Scope
import zio.blocks.schema._
import zio.blocks.schema.JavaTimeGen._
import zio.test._
import java.time._

/**
 * Comprehensive tests for BSON codec support of all primitive types. Tests
 * include:
 *   - Round-trip encoding/decoding
 *   - Edge cases (min/max values, special values)
 *   - Proper BSON type mapping
 */
object BsonCodecPrimitivesSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("BsonCodecPrimitivesSpec")(
    test("Unit round-trip") {
      val codec       = BsonSchemaCodec.bsonCodec(Schema.unit)
      val value: Unit = ()
      val bson        = codec.encoder.toBsonValue(value)
      assertTrue(
        bson.isDocument,
        bson.asDocument().isEmpty,
        bson.as[Unit](codec.decoder) == Right(value)
      )
    },
    test("Boolean round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
      check(Gen.boolean) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(
          bson.asBoolean().getValue == x,
          bson.as[Boolean](codec.decoder) == Right(x)
        )
      }
    },
    test("Byte round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.byte)
      check(Gen.byte) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(
          bson.asInt32().getValue == x,
          bson.as[Byte](codec.decoder) == Right(x)
        )
      }
    },
    test("Short round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.short)
      check(Gen.short) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(
          bson.asInt32().getValue == x,
          bson.as[Short](codec.decoder) == Right(x)
        )
      }
    },
    test("Int round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.int)
      check(Gen.int) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(
          bson.asInt32().getValue == x,
          bson.as[Int](codec.decoder) == Right(x)
        )
      }
    },
    test("Long round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.long)
      check(Gen.long) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(
          bson.asInt64().getValue == x,
          bson.as[Long](codec.decoder) == Right(x)
        )
      }
    },
    test("Float round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.float)
      check(Gen.float) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Float](codec.decoder) == Right(x))
      }
    },
    test("Double round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.double)
      check(Gen.double) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Double](codec.decoder) == Right(x))
      }
    },
    test("Char round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.char)
      check(Gen.char) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Char](codec.decoder) == Right(x))
      }
    },
    test("String round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.string)
      check(Gen.string) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[String](codec.decoder) == Right(x))
      }
    },
    test("BigInt round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.bigInt)
      check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20))) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[BigInt](codec.decoder) == Right(x))
      }
    },
    test("BigDecimal round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.bigDecimal)
      check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20))) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[BigDecimal](codec.decoder) == Right(x))
      }
    },
    test("DayOfWeek round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.dayOfWeek)
      check(genDayOfWeek) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[DayOfWeek](codec.decoder) == Right(x))
      }
    },
    test("Duration round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.duration)
      check(genDuration) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Duration](codec.decoder) == Right(x))
      }
    },
    test("Instant round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.instant)
      check(genInstant) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Instant](codec.decoder) == Right(x))
      }
    },
    test("LocalDate round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.localDate)
      check(genLocalDate) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[LocalDate](codec.decoder) == Right(x))
      }
    },
    test("LocalDateTime round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.localDateTime)
      check(genLocalDateTime) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[LocalDateTime](codec.decoder) == Right(x))
      }
    },
    test("LocalTime round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.localTime)
      check(genLocalTime) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[LocalTime](codec.decoder) == Right(x))
      }
    },
    test("Month round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.month)
      check(genMonth) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Month](codec.decoder) == Right(x))
      }
    },
    test("MonthDay round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.monthDay)
      check(genMonthDay) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[MonthDay](codec.decoder) == Right(x))
      }
    },
    test("OffsetDateTime round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.offsetDateTime)
      check(genOffsetDateTime) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[OffsetDateTime](codec.decoder) == Right(x))
      }
    },
    test("OffsetTime round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.offsetTime)
      check(genOffsetTime) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[OffsetTime](codec.decoder) == Right(x))
      }
    },
    test("Period round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.period)
      check(genPeriod) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Period](codec.decoder) == Right(x))
      }
    },
    test("Year round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.year)
      check(genYear) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[Year](codec.decoder) == Right(x))
      }
    },
    test("YearMonth round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.yearMonth)
      check(genYearMonth) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[YearMonth](codec.decoder) == Right(x))
      }
    },
    test("ZoneId round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.zoneId)
      check(genZoneId) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[ZoneId](codec.decoder) == Right(x))
      }
    },
    test("ZoneOffset round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.zoneOffset)
      check(genZoneOffset) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[ZoneOffset](codec.decoder) == Right(x))
      }
    },
    test("ZonedDateTime round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.zonedDateTime)
      check(genZonedDateTime) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[ZonedDateTime](codec.decoder) == Right(x))
      }
    },
    test("Currency round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.currency)
      check(Gen.currency) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[java.util.Currency](codec.decoder) == Right(x))
      }
    },
    test("UUID round-trip") {
      val codec = BsonSchemaCodec.bsonCodec(Schema.uuid)
      check(Gen.uuid) { x =>
        val bson = codec.encoder.toBsonValue(x)
        assertTrue(bson.as[java.util.UUID](codec.decoder) == Right(x))
      }
    }
  )
}
