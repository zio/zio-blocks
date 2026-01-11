package zio.blocks.schema.toon

import zio.test._
import java.time._

object JavaTimeCodecSpec extends ZIOSpecDefault {
  def spec = suite("JavaTimeCodec")(
    test("LocalDate codec") {
      val date    = LocalDate.of(2025, 1, 11)
      val codec   = ToonBinaryCodec.localDateCodec
      val encoded = codec.encodeToString(date)
      assertTrue(encoded == "\"2025-01-11\"")
    },
    test("LocalTime codec") {
      val time    = LocalTime.of(14, 30, 15)
      val codec   = ToonBinaryCodec.localTimeCodec
      val encoded = codec.encodeToString(time)
      assertTrue(encoded == "\"14:30:15\"")
    },
    test("LocalDateTime codec") {
      val dateTime = LocalDateTime.of(2025, 1, 11, 14, 30, 15)
      val codec    = ToonBinaryCodec.localDateTimeCodec
      val encoded  = codec.encodeToString(dateTime)
      assertTrue(encoded == "\"2025-01-11T14:30:15\"")
    },
    test("Instant codec") {
      val instant = Instant.parse("2025-01-11T09:00:00Z")
      val codec   = ToonBinaryCodec.instantCodec
      val encoded = codec.encodeToString(instant)
      assertTrue(encoded == "\"2025-01-11T09:00:00Z\"")
    },
    test("Duration codec") {
      val duration = Duration.ofHours(2).plusMinutes(30)
      val codec    = ToonBinaryCodec.durationCodec
      val encoded  = codec.encodeToString(duration)
      assertTrue(encoded == "\"PT2H30M\"")
    },
    test("Period codec") {
      val period  = Period.of(1, 2, 3) // 1 year, 2 months, 3 days
      val codec   = ToonBinaryCodec.periodCodec
      val encoded = codec.encodeToString(period)
      assertTrue(encoded == "\"P1Y2M3D\"")
    },
    test("OffsetDateTime codec") {
      val offsetDateTime = OffsetDateTime.parse("2025-01-11T14:30:15+05:30")
      val codec          = ToonBinaryCodec.offsetDateTimeCodec
      val encoded        = codec.encodeToString(offsetDateTime)
      assertTrue(encoded == "\"2025-01-11T14:30:15+05:30\"")
    },
    test("ZonedDateTime codec") {
      val zonedDateTime = ZonedDateTime.parse("2025-01-11T14:30:15+05:30[Asia/Kolkata]")
      val codec         = ToonBinaryCodec.zonedDateTimeCodec
      val encoded       = codec.encodeToString(zonedDateTime)
      // ZonedDateTime format includes the zone ID
      assertTrue(encoded.startsWith("\"2025-01-11T14:30:15+05:30"))
    },
    test("Duration with seconds") {
      val duration = Duration.ofSeconds(125)
      val codec    = ToonBinaryCodec.durationCodec
      val encoded  = codec.encodeToString(duration)
      assertTrue(encoded == "\"PT2M5S\"")
    },
    test("Period with days only") {
      val period  = Period.ofDays(10)
      val codec   = ToonBinaryCodec.periodCodec
      val encoded = codec.encodeToString(period)
      assertTrue(encoded == "\"P10D\"")
    }
  )
}
