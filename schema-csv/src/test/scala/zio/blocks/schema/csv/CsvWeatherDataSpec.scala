package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

import java.nio.CharBuffer

object CsvWeatherDataSpec extends SchemaBaseSpec {

  case class WeatherObservation(
    stationId: String,
    date: java.time.LocalDate,
    temperatureMax: Double,
    temperatureMin: Double,
    precipitation: Double,
    windSpeed: Double,
    humidity: Int
  )

  object WeatherObservation {
    implicit val schema: Schema[WeatherObservation] = Schema.derived
  }

  case class DailyWeatherSummary(
    station: String,
    date: java.time.LocalDate,
    avgTemp: Double,
    condition: String
  )

  object DailyWeatherSummary {
    implicit val schema: Schema[DailyWeatherSummary] = Schema.derived
  }

  private def roundTrip[A](value: A)(implicit s: Schema[A]): Either[SchemaError, A] = {
    val codec = s.derive(CsvFormat)
    val buf   = CharBuffer.allocate(8192)
    codec.encode(value, buf)
    buf.flip()
    val encoded = buf.toString
    codec.decode(CharBuffer.wrap(encoded))
  }

  private def encodeToString[A](value: A)(implicit s: Schema[A]): String = {
    val codec = s.derive(CsvFormat)
    val buf   = CharBuffer.allocate(8192)
    codec.encode(value, buf)
    buf.flip()
    buf.toString
  }

  private def decode[A](raw: String)(implicit s: Schema[A]): Either[SchemaError, A] = {
    val codec = s.derive(CsvFormat)
    codec.decode(CharBuffer.wrap(raw))
  }

  private val weatherCsv: String =
    "stationId,date,temperatureMax,temperatureMin,precipitation,windSpeed,humidity\r\n" +
      "USW00094728,2024-01-15,2.3,-4.1,12.7,15.2,78\r\n" +
      "USW00094728,2024-01-16,-1.8,-8.3,0.0,22.5,65\r\n" +
      "USW00094728,2024-01-17,5.1,-2.4,3.2,11.8,72\r\n" +
      "USW00012839,2024-01-15,18.9,8.7,0.0,6.3,45\r\n" +
      "USW00012839,2024-01-16,21.2,11.4,0.0,8.1,38\r\n" +
      "USW00012839,2024-01-17,19.5,9.8,2.1,12.6,52\r\n" +
      "GME00127786,2024-01-15,-3.2,-9.7,5.8,18.4,82\r\n" +
      "GME00127786,2024-01-16,-5.6,-12.1,8.3,25.7,88\r\n" +
      "GME00127786,2024-01-17,-1.4,-7.8,0.0,14.2,71\r\n" +
      "ASN00086071,2024-01-15,35.8,22.4,0.0,9.7,33\r\n" +
      "ASN00086071,2024-01-16,38.2,24.1,0.0,7.3,28\r\n" +
      "ASN00086071,2024-01-17,33.9,21.7,15.6,16.8,61\r\n"

  def spec = suite("CsvWeatherDataSpec")(
    suite("real-world weather data parsing")(
      test("decodes a single weather observation from raw CSV") {
        val raw    = "USW00094728,2024-01-15,2.3,-4.1,12.7,15.2,78\r\n"
        val result = decode[WeatherObservation](raw)
        assertTrue(
          result == Right(
            WeatherObservation("USW00094728", java.time.LocalDate.of(2024, 1, 15), 2.3, -4.1, 12.7, 15.2, 78)
          )
        )
      },
      test("decodes observation with zero precipitation") {
        val raw    = "USW00012839,2024-07-22,38.9,26.1,0.0,8.5,42\r\n"
        val result = decode[WeatherObservation](raw)
        assertTrue(
          result == Right(
            WeatherObservation("USW00012839", java.time.LocalDate.of(2024, 7, 22), 38.9, 26.1, 0.0, 8.5, 42)
          )
        )
      }
    ),
    suite("weather data encoding")(
      test("encodes weather observation to CSV row") {
        val obs =
          WeatherObservation("USW00094728", java.time.LocalDate.of(2024, 1, 15), 2.3, -4.1, 12.7, 15.2, 78)
        val encoded = encodeToString(obs)
        assertTrue(encoded == "USW00094728,2024-01-15,2.3,-4.1,12.7,15.2,78\r\n")
      },
      test("encodes negative temperatures correctly") {
        val obs =
          WeatherObservation("RSM00024266", java.time.LocalDate.of(2024, 12, 1), -18.5, -31.2, 3.1, 22.7, 91)
        val encoded = encodeToString(obs)
        assertTrue(encoded == "RSM00024266,2024-12-01,-18.5,-31.2,3.1,22.7,91\r\n")
      }
    ),
    suite("weather data round-trips")(
      test("typical mid-latitude observation round-trips") {
        val obs =
          WeatherObservation("USW00094728", java.time.LocalDate.of(2024, 3, 20), 12.8, 3.3, 5.6, 11.2, 65)
        assertTrue(roundTrip(obs) == Right(obs))
      },
      test("tropical station observation round-trips") {
        val obs =
          WeatherObservation("MYM00048647", java.time.LocalDate.of(2024, 8, 5), 33.2, 25.8, 45.3, 6.1, 88)
        assertTrue(roundTrip(obs) == Right(obs))
      },
      test("daily weather summary round-trips") {
        val summary = DailyWeatherSummary("Denver Intl", java.time.LocalDate.of(2024, 6, 15), 28.4, "Clear")
        assertTrue(roundTrip(summary) == Right(summary))
      }
    ),
    suite("multi-station weather report")(
      test("multiple daily summaries encode and decode independently") {
        val stations = List(
          DailyWeatherSummary("JFK Airport", java.time.LocalDate.of(2024, 9, 1), 24.5, "Partly Cloudy"),
          DailyWeatherSummary("LAX Airport", java.time.LocalDate.of(2024, 9, 1), 22.1, "Fog"),
          DailyWeatherSummary("ORD Airport", java.time.LocalDate.of(2024, 9, 1), 19.8, "Rain")
        )
        assertTrue(stations.forall(s => roundTrip(s) == Right(s)))
      },
      test("multiple observations encode and decode independently") {
        val observations = List(
          WeatherObservation("USW00094728", java.time.LocalDate.of(2024, 1, 1), -2.0, -10.5, 0.0, 20.1, 55),
          WeatherObservation("USW00094728", java.time.LocalDate.of(2024, 1, 2), 1.1, -5.3, 8.4, 14.7, 72),
          WeatherObservation("USW00094728", java.time.LocalDate.of(2024, 1, 3), 3.6, -1.2, 0.0, 9.3, 60)
        )
        assertTrue(observations.forall(o => roundTrip(o) == Right(o)))
      }
    ),
    suite("edge cases in weather data")(
      test("station name with comma is quoted in CSV") {
        val summary =
          DailyWeatherSummary("Nome, Alaska", java.time.LocalDate.of(2024, 2, 14), -15.3, "Blizzard")
        val encoded = encodeToString(summary)
        assertTrue(
          encoded.contains("\"Nome, Alaska\"") &&
            roundTrip(summary) == Right(summary)
        )
      },
      test("extreme cold temperature (Vostok Station record: -89.2C)") {
        val obs =
          WeatherObservation("AYM00089606", java.time.LocalDate.of(1983, 7, 21), -89.2, -89.2, 0.0, 5.0, 30)
        assertTrue(roundTrip(obs) == Right(obs))
      },
      test("extreme hot temperature (Death Valley record: 56.7C)") {
        val obs =
          WeatherObservation("USC00042319", java.time.LocalDate.of(1913, 7, 10), 56.7, 39.4, 0.0, 12.0, 8)
        assertTrue(roundTrip(obs) == Right(obs))
      },
      test("unicode station name round-trips") {
        val summary =
          DailyWeatherSummary("東京 (Tokyo)", java.time.LocalDate.of(2024, 4, 1), 18.5, "Sakura season")
        assertTrue(roundTrip(summary) == Right(summary))
      },
      test("condition with quotes round-trips") {
        val summary =
          DailyWeatherSummary("Test Station", java.time.LocalDate.of(2024, 1, 1), 0.0, "\"Feels like\" -10")
        assertTrue(roundTrip(summary) == Right(summary))
      }
    ),
    suite("parsing CSV file content")(
      test("parses header row from weather CSV") {
        val result = CsvReader.readAll(weatherCsv, CsvConfig())
        assertTrue(
          result.isRight &&
            result.toOption.get._1 == IndexedSeq(
              "stationId",
              "date",
              "temperatureMax",
              "temperatureMin",
              "precipitation",
              "windSpeed",
              "humidity"
            )
        )
      },
      test("parses all data rows from weather CSV") {
        val result = CsvReader.readAll(weatherCsv, CsvConfig())
        assertTrue(
          result.isRight &&
            result.toOption.get._2.size == 12
        )
      },
      test("decodes each row into WeatherObservation") {
        val Right((_, dataRows)) = CsvReader.readAll(weatherCsv, CsvConfig()): @unchecked
        val firstRow             = dataRows.head.mkString(",") + "\r\n"
        val lastRow              = dataRows.last.mkString(",") + "\r\n"
        val firstResult          = decode[WeatherObservation](firstRow)
        val lastResult           = decode[WeatherObservation](lastRow)
        assertTrue(
          firstResult == Right(
            WeatherObservation("USW00094728", java.time.LocalDate.of(2024, 1, 15), 2.3, -4.1, 12.7, 15.2, 78)
          ) &&
            lastResult == Right(
              WeatherObservation("ASN00086071", java.time.LocalDate.of(2024, 1, 17), 33.9, 21.7, 15.6, 16.8, 61)
            )
        )
      },
      test("round-trips all decoded observations") {
        val Right((_, dataRows)) = CsvReader.readAll(weatherCsv, CsvConfig()): @unchecked
        val allDecoded           = dataRows.map { row =>
          val raw = row.mkString(",") + "\r\n"
          decode[WeatherObservation](raw)
        }
        val allRoundTripped = allDecoded.collect { case Right(obs) => roundTrip(obs) == Right(obs) }
        assertTrue(
          allDecoded.forall(_.isRight) &&
            allRoundTripped.size == 12 &&
            allRoundTripped.forall(identity)
        )
      }
    )
  )
}
