package zio.blocks.schema.json

import java.time._
import java.util.{Currency, UUID}

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object JsonInterpolatorRuntimeCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorRuntimeCoverageSpec")(
    test("jsonWithInterpolation handles many value types") {
      val instant        = Instant.ofEpochMilli(0L)
      val localDate      = LocalDate.of(2020, 1, 2)
      val localTime      = LocalTime.of(3, 4, 5)
      val localDateTime  = LocalDateTime.of(localDate, localTime)
      val offsetTime     = OffsetTime.of(localTime, ZoneOffset.UTC)
      val offsetDateTime = OffsetDateTime.of(localDateTime, ZoneOffset.UTC)
      val zonedDateTime  = ZonedDateTime.of(localDateTime, ZoneId.of("UTC"))

      val sampleMap: Map[Any, Any] = Map(
        "s"                                                      -> "v",
        true                                                     -> 1,
        2.toByte                                                 -> 3,
        4.toShort                                                -> 5,
        6                                                        -> 7,
        8L                                                       -> 9,
        1.25f                                                    -> false,
        2.5d                                                     -> true,
        BigDecimal("1.2")                                        -> BigInt(3),
        new AnyRef { override def toString: String = "unknown" } -> 0
      )

      val sampleSeq: Iterable[Any] = List(1, "a", false)
      val sampleArr: Array[Any]    = Array[Any](1, 2, 3)

      val embeddedJson: Json = Json.obj(
        "x" -> Json.number(1),
        "y" -> Json.str("z")
      )

      val fallbackNumber = new AnyRef {
        override def toString: String = "123"
      }

      val parts = Seq(
        "{\"str\":",
        ",\"bool\":",
        ",\"byte\":",
        ",\"short\":",
        ",\"int\":",
        ",\"long\":",
        ",\"float\":",
        ",\"double\":",
        ",\"char\":",
        ",\"bigDecimal\":",
        ",\"bigInt\":",
        ",\"dayOfWeek\":",
        ",\"duration\":",
        ",\"instant\":",
        ",\"localDate\":",
        ",\"localDateTime\":",
        ",\"localTime\":",
        ",\"month\":",
        ",\"monthDay\":",
        ",\"offsetDateTime\":",
        ",\"offsetTime\":",
        ",\"period\":",
        ",\"year\":",
        ",\"yearMonth\":",
        ",\"zoneOffset\":",
        ",\"zoneId\":",
        ",\"zonedDateTime\":",
        ",\"currency\":",
        ",\"uuid\":",
        ",\"optSome\":",
        ",\"optNone\":",
        ",\"nullValue\":",
        ",\"unit\":",
        ",\"json\":",
        ",\"map\":",
        ",\"seq\":",
        ",\"arr\":",
        ",\"fallback\":",
        "}"
      )

      val args: Seq[Any] = Seq(
        "aé€😀",
        true,
        1.toByte,
        2.toShort,
        3,
        4L,
        1.5f,
        2.5d,
        'x',
        BigDecimal("1.23"),
        BigInt(99),
        DayOfWeek.MONDAY,
        Duration.ofSeconds(5),
        instant,
        localDate,
        localDateTime,
        localTime,
        Month.JANUARY,
        MonthDay.of(1, 2),
        offsetDateTime,
        offsetTime,
        Period.ofDays(1),
        Year.of(2020),
        YearMonth.of(2020, 1),
        ZoneOffset.UTC,
        ZoneId.of("UTC"),
        zonedDateTime,
        Currency.getInstance("USD"),
        UUID.fromString("00000000-0000-0000-0000-000000000000"),
        Option(1),
        Option.empty[Int],
        null,
        (),
        embeddedJson,
        sampleMap,
        sampleSeq,
        sampleArr,
        fallbackNumber
      )

      val json = JsonInterpolatorRuntime.jsonWithInterpolation(new StringContext(parts: _*), args)

      assertTrue(
        json.get("str").headOption.isDefined &&
          json.get("map").headOption.isDefined &&
          json.get("seq").headOption.isDefined &&
          json.get("arr").headOption.isDefined &&
          json.get("fallback").headOption.isDefined
      )
    },
    test("jsonWithInterpolation fails on illegal surrogate pair") {
      val bad = "\uD800"
      val sc  = new StringContext("{\"s\":", "}")

      assertTrue(
        scala.util
          .Try(JsonInterpolatorRuntime.jsonWithInterpolation(sc, Seq(bad)))
          .failed
          .toOption
          .exists(_.isInstanceOf[JsonBinaryCodecError])
      )
    }
  )
}
