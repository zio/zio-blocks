package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer

/**
 * Tests for Vector collection encoding and decoding with ThriftFormat.
 */
object ThriftVectorSpec extends SchemaBaseSpec {

  case class Record(name: String, value: Int)

  object Record {
    implicit val schema: Schema[Record] = Schema.derived
  }

  case class VectorOfInts(items: Vector[Int])

  object VectorOfInts {
    implicit val schema: Schema[VectorOfInts] = Schema.derived
  }

  case class VectorOfStrings(items: Vector[String])

  object VectorOfStrings {
    implicit val schema: Schema[VectorOfStrings] = Schema.derived
  }

  case class VectorOfRecords(items: Vector[Record])

  object VectorOfRecords {
    implicit val schema: Schema[VectorOfRecords] = Schema.derived
  }

  case class VectorOfVectors(items: Vector[Vector[Int]])

  object VectorOfVectors {
    implicit val schema: Schema[VectorOfVectors] = Schema.derived
  }

  case class VectorOfOptions(items: Vector[Option[String]])

  object VectorOfOptions {
    implicit val schema: Schema[VectorOfOptions] = Schema.derived
  }

  case class MixedRecord(id: Int, tags: Vector[String], nested: Vector[Record])

  object MixedRecord {
    implicit val schema: Schema[MixedRecord] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftVectorSpec")(
    suite("standalone Vector")(
      test("encode/decode Vector[Int]") {
        roundTrip(Vector(1, 2, 3, 4, 5))
      },
      test("encode/decode empty Vector[Int]") {
        roundTrip(Vector.empty[Int])
      },
      test("encode/decode Vector[String]") {
        roundTrip(Vector("a", "b", "c"))
      },
      test("encode/decode Vector[Boolean]") {
        roundTrip(Vector(true, false, true))
      },
      test("encode/decode Vector[Long]") {
        roundTrip(Vector(1L, 2L, Long.MaxValue, Long.MinValue))
      },
      test("encode/decode Vector[Double]") {
        roundTrip(Vector(1.1, 2.2, 3.3))
      }
    ),
    suite("Vector in record")(
      test("encode/decode record with Vector[Int]") {
        roundTrip(VectorOfInts(Vector(10, 20, 30)))
      },
      test("encode/decode record with Vector[String]") {
        roundTrip(VectorOfStrings(Vector("hello", "world")))
      },
      test("encode/decode record with Vector[Record]") {
        roundTrip(VectorOfRecords(Vector(Record("a", 1), Record("b", 2))))
      },
      test("encode/decode record with empty Vector") {
        roundTrip(VectorOfInts(Vector.empty))
      }
    ),
    suite("nested Vector")(
      test("encode/decode Vector[Vector[Int]]") {
        roundTrip(VectorOfVectors(Vector(Vector(1, 2), Vector(3, 4, 5), Vector())))
      },
      test("encode/decode empty Vector[Vector[Int]]") {
        roundTrip(VectorOfVectors(Vector.empty))
      }
    ),
    suite("Vector with Option")(
      test("encode/decode Vector[Option[String]] - mixed") {
        roundTrip(VectorOfOptions(Vector(Some("a"), None, Some("b"), None)))
      },
      test("encode/decode Vector[Option[String]] - all Some") {
        roundTrip(VectorOfOptions(Vector(Some("x"), Some("y"), Some("z"))))
      },
      test("encode/decode Vector[Option[String]] - all None") {
        roundTrip(VectorOfOptions(Vector(None, None, None)))
      }
    ),
    suite("mixed record with Vector")(
      test("encode/decode record with multiple Vector fields") {
        roundTrip(
          MixedRecord(
            id = 42,
            tags = Vector("tag1", "tag2"),
            nested = Vector(Record("first", 1), Record("second", 2))
          )
        )
      }
    ),
    suite("Vector edge cases")(
      test("encode/decode large Vector (500 elements)") {
        val large = Vector.tabulate(500)(identity)
        roundTrip(VectorOfInts(large))
      },
      test("encode/decode Vector with unicode strings") {
        roundTrip(VectorOfStrings(Vector("hello", "世界", "🌍")))
      }
    )
  )
}
