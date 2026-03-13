package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

import java.nio.CharBuffer

object CsvRecordCodecSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)

  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Point(x: Double, y: Double, z: Double)

  object Point {
    implicit val schema: Schema[Point] = Schema.derived
  }

  case class SingleField(value: String)

  object SingleField {
    implicit val schema: Schema[SingleField] = Schema.derived
  }

  case class MixedTypes(name: String, age: Int, score: Double, active: Boolean, initial: Char)

  object MixedTypes {
    implicit val schema: Schema[MixedTypes] = Schema.derived
  }

  private def deriveCodec[A](implicit s: Schema[A]): CsvCodec[A] =
    s.derive(CsvFormat)

  private def roundTrip[A](codec: CsvCodec[A], value: A): Either[SchemaError, A] = {
    val buf = CharBuffer.allocate(4096)
    codec.encode(value, buf)
    buf.flip()
    val encoded = buf.toString
    codec.decode(CharBuffer.wrap(encoded))
  }

  def spec = suite("CsvRecordCodecSpec")(
    suite("headerNames")(
      test("Person has correct headers") {
        val codec = deriveCodec[Person]
        assertTrue(codec.headerNames == IndexedSeq("name", "age"))
      },
      test("Point has correct headers") {
        val codec = deriveCodec[Point]
        assertTrue(codec.headerNames == IndexedSeq("x", "y", "z"))
      },
      test("SingleField has correct headers") {
        val codec = deriveCodec[SingleField]
        assertTrue(codec.headerNames == IndexedSeq("value"))
      },
      test("MixedTypes has correct headers") {
        val codec = deriveCodec[MixedTypes]
        assertTrue(codec.headerNames == IndexedSeq("name", "age", "score", "active", "initial"))
      }
    ),
    suite("encode")(
      test("Person encodes to CSV row") {
        val codec = deriveCodec[Person]
        val buf   = CharBuffer.allocate(1024)
        codec.encode(Person("Alice", 30), buf)
        buf.flip()
        val encoded = buf.toString
        assertTrue(encoded == "Alice,30\r\n")
      },
      test("Person with special characters encodes with quoting") {
        val codec = deriveCodec[Person]
        val buf   = CharBuffer.allocate(1024)
        codec.encode(Person("O'Brien, Jr.", 45), buf)
        buf.flip()
        val encoded = buf.toString
        assertTrue(encoded == "\"O'Brien, Jr.\",45\r\n")
      }
    ),
    suite("decode")(
      test("Person decodes from CSV row") {
        val codec  = deriveCodec[Person]
        val result = codec.decode(CharBuffer.wrap("Alice,30\r\n"))
        assertTrue(result == Right(Person("Alice", 30)))
      },
      test("Person decodes without trailing newline") {
        val codec  = deriveCodec[Person]
        val result = codec.decode(CharBuffer.wrap("Bob,25"))
        assertTrue(result == Right(Person("Bob", 25)))
      },
      test("wrong field count returns error") {
        val codec  = deriveCodec[Person]
        val result = codec.decode(CharBuffer.wrap("Alice,30,extra"))
        assertTrue(result.isLeft)
      }
    ),
    suite("round-trip")(
      test("Person round-trips") {
        val codec = deriveCodec[Person]
        val value = Person("Alice", 30)
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("Point round-trips") {
        val codec = deriveCodec[Point]
        val value = Point(1.5, 2.7, -3.14)
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("SingleField round-trips") {
        val codec = deriveCodec[SingleField]
        val value = SingleField("hello")
        assertTrue(roundTrip(codec, value) == Right(value))
      },
      test("MixedTypes round-trips") {
        val codec = deriveCodec[MixedTypes]
        val value = MixedTypes("Test", 42, 3.14, true, 'X')
        assertTrue(roundTrip(codec, value) == Right(value))
      }
    ),
    suite("nullValue")(
      test("Person nullValue has default field values") {
        val codec = deriveCodec[Person]
        val nv    = codec.nullValue
        assertTrue(nv.name == null && nv.age == 0)
      }
    )
  )
}
