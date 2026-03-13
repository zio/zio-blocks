package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

import java.nio.CharBuffer

object CsvIntegrationSpec extends SchemaBaseSpec {

  case class Employee(id: Int, name: String, salary: BigDecimal, startDate: java.time.LocalDate, active: Boolean)

  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  case class Address(street: String, city: String, zip: String, country: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Sensor(id: Long, timestamp: java.time.Instant, temperature: Double, humidity: Float, label: String)

  object Sensor {
    implicit val schema: Schema[Sensor] = Schema.derived
  }

  case class SimpleRecord(value: String)

  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class AllPrimitives(
    b: Byte,
    s: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    bool: Boolean,
    c: Char,
    str: String
  )

  object AllPrimitives {
    implicit val schema: Schema[AllPrimitives] = Schema.derived
  }

  case class WrappedId(value: Int) extends AnyVal

  object WrappedId {
    implicit val schema: Schema[WrappedId] = Schema.derived
  }

  case class WithWrappedField(id: WrappedId, name: String)

  // Note: Records with wrapper-typed fields (e.g., case class Person(id: WrappedId, name: String))
  // work correctly since record derivation uses field names directly for CSV headers,
  // not the wrapped codec's headerNames. We test wrappers standalone for isolation.

  private def deriveCodec[A](implicit s: Schema[A]): CsvCodec[A] =
    s.derive(CsvFormat)

  private def roundTrip[A](codec: CsvCodec[A], value: A): Either[SchemaError, A] = {
    val buf = CharBuffer.allocate(8192)
    codec.encode(value, buf)
    buf.flip()
    val encoded = buf.toString
    codec.decode(CharBuffer.wrap(encoded))
  }

  private def encodeToString[A](codec: CsvCodec[A], value: A): String = {
    val buf = CharBuffer.allocate(8192)
    codec.encode(value, buf)
    buf.flip()
    buf.toString
  }

  def spec = suite("CsvIntegrationSpec")(
    suite("schema.derive(CsvFormat) API")(
      test("derives codec for Employee") {
        val codec = deriveCodec[Employee]
        assertTrue(
          codec.headerNames == IndexedSeq("id", "name", "salary", "startDate", "active")
        )
      },
      test("derives codec for Address") {
        val codec = deriveCodec[Address]
        assertTrue(
          codec.headerNames == IndexedSeq("street", "city", "zip", "country")
        )
      },
      test("derives codec for Sensor") {
        val codec = deriveCodec[Sensor]
        assertTrue(
          codec.headerNames == IndexedSeq("id", "timestamp", "temperature", "humidity", "label")
        )
      },
      test("derives codec via Schema[T].derive(CsvFormat) syntax") {
        val codec = Schema[Employee].derive(CsvFormat)
        assertTrue(codec.headerNames.nonEmpty)
      }
    ),
    suite("full encode/decode cycle")(
      test("Employee round-trips through CharBuffer") {
        val codec    = deriveCodec[Employee]
        val employee = Employee(1, "Alice", BigDecimal("75000.50"), java.time.LocalDate.of(2023, 1, 15), true)
        assertTrue(roundTrip(codec, employee) == Right(employee))
      },
      test("Address round-trips") {
        val codec   = deriveCodec[Address]
        val address = Address("123 Main St", "Springfield", "62701", "US")
        assertTrue(roundTrip(codec, address) == Right(address))
      },
      test("Sensor round-trips") {
        val codec  = deriveCodec[Sensor]
        val sensor = Sensor(42L, java.time.Instant.parse("2024-06-15T10:30:00Z"), 22.5, 65.3f, "lab-1")
        assertTrue(roundTrip(codec, sensor) == Right(sensor))
      },
      test("AllPrimitives round-trips") {
        val codec = deriveCodec[AllPrimitives]
        val value = AllPrimitives(1.toByte, 2.toShort, 3, 4L, 5.5f, 6.6, true, 'Z', "hello")
        assertTrue(roundTrip(codec, value) == Right(value))
      }
    ),
    suite("encoding format")(
      test("Employee encodes to CSV row with correct field order") {
        val codec    = deriveCodec[Employee]
        val employee = Employee(1, "Alice", BigDecimal("75000.50"), java.time.LocalDate.of(2023, 1, 15), true)
        val encoded  = encodeToString(codec, employee)
        assertTrue(encoded == "1,Alice,75000.50,2023-01-15,true\r\n")
      },
      test("fields with commas are quoted") {
        val codec   = deriveCodec[Address]
        val address = Address("123 Main St, Apt 4", "Springfield", "62701", "US")
        val encoded = encodeToString(codec, address)
        assertTrue(encoded.contains("\"123 Main St, Apt 4\""))
      },
      test("fields with quotes are escaped") {
        val codec   = deriveCodec[Address]
        val address = Address("O\"Brien Rd", "Dublin", "D02", "IE")
        val encoded = encodeToString(codec, address)
        assertTrue(encoded.contains("\"O\"\"Brien Rd\""))
      },
      test("fields with newlines are quoted") {
        val codec   = deriveCodec[Address]
        val address = Address("Line1\nLine2", "City", "12345", "US")
        val encoded = encodeToString(codec, address)
        assertTrue(encoded.contains("\"Line1\nLine2\""))
      }
    ),
    suite("edge cases")(
      test("empty string field round-trips") {
        val codec = deriveCodec[SimpleRecord]
        val value = SimpleRecord("")
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("unicode characters round-trip") {
        val codec = deriveCodec[SimpleRecord]
        val value = SimpleRecord("日本語テスト 🎉")
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("very long field round-trips") {
        val codec     = deriveCodec[SimpleRecord]
        val longValue = "x" * 500
        val value     = SimpleRecord(longValue)
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("field with only special characters") {
        val codec = deriveCodec[SimpleRecord]
        val value = SimpleRecord(",,,\"\"\"")
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("numeric boundary values round-trip") {
        val codec = deriveCodec[AllPrimitives]
        val value = AllPrimitives(
          Byte.MaxValue,
          Short.MaxValue,
          Int.MaxValue,
          Long.MaxValue,
          Float.MaxValue,
          Double.MaxValue,
          false,
          '\u0000',
          ""
        )
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("negative numeric values round-trip") {
        val codec = deriveCodec[AllPrimitives]
        val value = AllPrimitives(
          Byte.MinValue,
          Short.MinValue,
          Int.MinValue,
          Long.MinValue,
          Float.MinValue,
          Double.MinValue,
          true,
          'A',
          "neg"
        )
        assertTrue(roundTrip(codec, value) == Right(value))
      }
    ),
    suite("error handling")(
      test("wrong field count returns error") {
        val codec  = deriveCodec[Employee]
        val result = codec.decode(CharBuffer.wrap("1,Alice,75000"))
        assertTrue(result.isLeft)
      },
      test("invalid type in field returns error") {
        val codec  = deriveCodec[Employee]
        val result = codec.decode(CharBuffer.wrap("not_int,Alice,75000.50,2023-01-15,true"))
        assertTrue(result.isLeft)
      },
      test("invalid boolean returns error") {
        val codec  = deriveCodec[Employee]
        val result = codec.decode(CharBuffer.wrap("1,Alice,75000.50,2023-01-15,maybe"))
        assertTrue(result.isLeft)
      },
      test("invalid date returns error") {
        val codec  = deriveCodec[Employee]
        val result = codec.decode(CharBuffer.wrap("1,Alice,75000.50,not-a-date,true"))
        assertTrue(result.isLeft)
      }
    ),
    suite("wrapper type integration")(
      test("WrappedId derives and round-trips via CsvFormat") {
        val codec = Schema[WrappedId].derive(CsvFormat)
        val value = WrappedId(42)
        assertTrue(roundTrip(codec, value) == Right(value))
      }
    )
  )
}
