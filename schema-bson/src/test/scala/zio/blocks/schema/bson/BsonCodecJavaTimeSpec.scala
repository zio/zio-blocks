package zio.blocks.schema.bson

import zio.blocks.schema.Schema
import zio.bson.BsonDecoderOps
import zio.test._
import java.time._
import java.util.{Currency, UUID}

/**
 * Comprehensive tests for BSON codec support of java.time types and other
 * standard types.
 */
object BsonCodecJavaTimeSpec extends ZIOSpecDefault {

  def spec = suite("BsonCodecJavaTimeSpec")(
    suite("UUID type")(
      test("encodes random UUID") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[UUID])
        val value   = UUID.randomUUID()
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[UUID](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encodes nil UUID") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[UUID])
        val value   = new UUID(0L, 0L)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[UUID](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various UUIDs") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[UUID])
        val values = List(
          UUID.randomUUID(),
          UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
          new UUID(0L, 0L),
          new UUID(-1L, -1L)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[UUID](codec.decoder) == Right(value)
        })
      }
    ),
    suite("Currency type")(
      test("encodes USD") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Currency])
        val value   = Currency.getInstance("USD")
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Currency](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various currencies") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Currency])
        val values = List("USD", "EUR", "GBP", "JPY", "CNY").map(Currency.getInstance)
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[Currency](codec.decoder) == Right(value)
        })
      }
    ),
    suite("DayOfWeek type")(
      test("encodes MONDAY") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[DayOfWeek])
        val value   = DayOfWeek.MONDAY
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[DayOfWeek](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip all days") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[DayOfWeek])
        val values = DayOfWeek.values().toList
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[DayOfWeek](codec.decoder) == Right(value)
        })
      }
    ),
    suite("Month type")(
      test("encodes JANUARY") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Month])
        val value   = Month.JANUARY
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Month](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip all months") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Month])
        val values = Month.values().toList
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[Month](codec.decoder) == Right(value)
        })
      }
    ),
    suite("Duration type")(
      test("encodes zero duration") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Duration])
        val value   = Duration.ZERO
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Duration](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encodes positive duration") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Duration])
        val value   = Duration.ofHours(24)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Duration](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encodes negative duration") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Duration])
        val value   = Duration.ofMinutes(-30)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Duration](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various durations") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Duration])
        val values = List(
          Duration.ZERO,
          Duration.ofNanos(123456789),
          Duration.ofSeconds(3600),
          Duration.ofMinutes(90),
          Duration.ofHours(48),
          Duration.ofDays(7)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[Duration](codec.decoder) == Right(value)
        })
      }
    ),
    suite("Period type")(
      test("encodes zero period") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Period])
        val value   = Period.ZERO
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Period](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encodes period of days") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Period])
        val value   = Period.ofDays(30)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Period](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various periods") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Period])
        val values = List(
          Period.ZERO,
          Period.ofDays(1),
          Period.ofMonths(3),
          Period.ofYears(2),
          Period.of(1, 2, 3)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[Period](codec.decoder) == Right(value)
        })
      }
    ),
    suite("Instant type")(
      test("encodes epoch") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Instant])
        val value   = Instant.EPOCH
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Instant](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encodes current time") {
        val codec = BsonSchemaCodec.bsonCodec(Schema[Instant])
        // BSON stores millisecond precision, so truncate to millis for comparison
        val value   = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Instant](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various instants") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Instant])
        val values = List(
          Instant.EPOCH,
          Instant.parse("2020-01-01T00:00:00Z"),
          Instant.parse("2025-12-31T23:59:59Z"),
          Instant.ofEpochMilli(1234567890123L)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[Instant](codec.decoder) == Right(value)
        })
      }
    ),
    suite("LocalDate type")(
      test("encodes epoch date") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[LocalDate])
        val value   = LocalDate.ofEpochDay(0)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[LocalDate](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encodes specific date") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[LocalDate])
        val value   = LocalDate.of(2025, 1, 16)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[LocalDate](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various dates") {
        val codec = BsonSchemaCodec.bsonCodec(Schema[LocalDate])
        // Note: LocalDate.MIN/MAX cause overflow in BSON's millisecond epoch representation
        // so we test with reasonable historical and future dates instead
        val values = List(
          LocalDate.of(2000, 1, 1),
          LocalDate.of(2025, 12, 31),
          LocalDate.of(1990, 6, 15),
          LocalDate.of(1900, 1, 1),
          LocalDate.of(2100, 12, 31)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[LocalDate](codec.decoder) == Right(value)
        })
      }
    ),
    suite("LocalTime type")(
      test("encodes midnight") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[LocalTime])
        val value   = LocalTime.MIDNIGHT
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[LocalTime](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("encodes noon") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[LocalTime])
        val value   = LocalTime.NOON
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[LocalTime](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various times") {
        val codec = BsonSchemaCodec.bsonCodec(Schema[LocalTime])
        // Note: BSON has millisecond precision, so avoid nanosecond values
        val values = List(
          LocalTime.MIDNIGHT,
          LocalTime.NOON,
          LocalTime.of(14, 30, 45),
          LocalTime.of(23, 59, 59, 999000000) // 999 millis, not nanos
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[LocalTime](codec.decoder) == Right(value)
        })
      }
    ),
    suite("LocalDateTime type")(
      test("encodes specific datetime") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[LocalDateTime])
        val value   = LocalDateTime.of(2000, 1, 1, 0, 0, 0)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[LocalDateTime](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various datetimes") {
        val codec = BsonSchemaCodec.bsonCodec(Schema[LocalDateTime])
        // Note: LocalDateTime.MIN/MAX cause overflow
        // in BSON's epoch millisecond conversion
        // Test with reasonable date ranges instead
        val values = List(
          LocalDateTime.of(2025, 1, 16, 14, 30, 0),
          LocalDateTime.of(2020, 12, 31, 23, 59, 59),
          LocalDateTime.of(1900, 1, 1, 0, 0, 0),
          LocalDateTime.of(2100, 12, 31, 23, 59, 59)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[LocalDateTime](codec.decoder) == Right(value)
        })
      }
    ),
    suite("MonthDay type")(
      test("encodes specific month-day") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[MonthDay])
        val value   = MonthDay.of(12, 25)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[MonthDay](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various month-days") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[MonthDay])
        val values = List(
          MonthDay.of(1, 1),
          MonthDay.of(12, 31),
          MonthDay.of(2, 29),
          MonthDay.of(7, 4)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[MonthDay](codec.decoder) == Right(value)
        })
      }
    ),
    suite("Year type")(
      test("encodes specific year") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[Year])
        val value   = Year.of(2025)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[Year](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various years") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Year])
        val values = List(
          Year.of(2000),
          Year.of(2025),
          Year.of(1900),
          Year.of(2100)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[Year](codec.decoder) == Right(value)
        })
      }
    ),
    suite("YearMonth type")(
      test("encodes specific year-month") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[YearMonth])
        val value   = YearMonth.of(2025, 1)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[YearMonth](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various year-months") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[YearMonth])
        val values = List(
          YearMonth.of(2025, 1),
          YearMonth.of(2020, 12),
          YearMonth.of(2000, 6)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[YearMonth](codec.decoder) == Right(value)
        })
      }
    ),
    suite("OffsetTime type")(
      test("encodes time with offset") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[OffsetTime])
        val value   = OffsetTime.of(14, 30, 0, 0, ZoneOffset.ofHours(2))
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[OffsetTime](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various offset times") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[OffsetTime])
        val values = List(
          OffsetTime.of(12, 0, 0, 0, ZoneOffset.UTC),
          OffsetTime.of(14, 30, 0, 0, ZoneOffset.ofHours(-5)),
          OffsetTime.of(23, 59, 59, 0, ZoneOffset.ofHours(9))
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[OffsetTime](codec.decoder) == Right(value)
        })
      }
    ),
    suite("OffsetDateTime type")(
      test("encodes datetime with offset") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[OffsetDateTime])
        val value   = OffsetDateTime.of(2025, 1, 16, 14, 30, 0, 0, ZoneOffset.UTC)
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[OffsetDateTime](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various offset datetimes") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[OffsetDateTime])
        val values = List(
          OffsetDateTime.of(2025, 1, 16, 12, 0, 0, 0, ZoneOffset.UTC),
          OffsetDateTime.of(2020, 12, 31, 23, 59, 59, 0, ZoneOffset.ofHours(-8))
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[OffsetDateTime](codec.decoder) == Right(value)
        })
      }
    ),
    suite("ZonedDateTime type")(
      test("encodes datetime with zone") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[ZonedDateTime])
        val value   = ZonedDateTime.of(2025, 1, 16, 14, 30, 0, 0, ZoneId.of("UTC"))
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[ZonedDateTime](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various zoned datetimes") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[ZonedDateTime])
        val values = List(
          ZonedDateTime.of(2025, 1, 16, 12, 0, 0, 0, ZoneId.of("UTC")),
          ZonedDateTime.of(2020, 12, 31, 23, 59, 59, 0, ZoneId.of("America/New_York")),
          ZonedDateTime.of(2025, 6, 1, 0, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[ZonedDateTime](codec.decoder) == Right(value)
        })
      }
    ),
    suite("ZoneId type")(
      test("encodes UTC") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[ZoneId])
        val value   = ZoneId.of("UTC")
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[ZoneId](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various zone ids") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[ZoneId])
        val values = List(
          ZoneId.of("UTC"),
          ZoneId.of("America/New_York"),
          ZoneId.of("Europe/London"),
          ZoneId.of("Asia/Tokyo")
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[ZoneId](codec.decoder) == Right(value)
        })
      }
    ),
    suite("ZoneOffset type")(
      test("encodes UTC offset") {
        val codec   = BsonSchemaCodec.bsonCodec(Schema[ZoneOffset])
        val value   = ZoneOffset.UTC
        val bson    = codec.encoder.toBsonValue(value)
        val decoded = bson.as[ZoneOffset](codec.decoder)
        assertTrue(decoded == Right(value))
      },
      test("round-trip various offsets") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[ZoneOffset])
        val values = List(
          ZoneOffset.UTC,
          ZoneOffset.ofHours(2),
          ZoneOffset.ofHours(-5),
          ZoneOffset.ofHoursMinutes(5, 30)
        )
        assertTrue(values.forall { value =>
          val bson = codec.encoder.toBsonValue(value)
          bson.as[ZoneOffset](codec.decoder) == Right(value)
        })
      }
    )
  )
}
