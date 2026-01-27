package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Tests for Map with various key types in ThriftFormat.
 */
object ThriftMapKeyTypesSpec extends SchemaBaseSpec {

  case class IntKeyMap(data: Map[Int, String])

  object IntKeyMap {
    implicit val schema: Schema[IntKeyMap] = Schema.derived
  }

  case class LongKeyMap(data: Map[Long, String])

  object LongKeyMap {
    implicit val schema: Schema[LongKeyMap] = Schema.derived
  }

  case class UUIDKeyMap(data: Map[UUID, String])

  object UUIDKeyMap {
    implicit val schema: Schema[UUIDKeyMap] = Schema.derived
  }

  case class BoolKeyMap(data: Map[Boolean, String])

  object BoolKeyMap {
    implicit val schema: Schema[BoolKeyMap] = Schema.derived
  }

  case class Record(name: String, value: Int)

  object Record {
    implicit val schema: Schema[Record] = Schema.derived
  }

  case class MapWithRecordValue(data: Map[Int, Record])

  object MapWithRecordValue {
    implicit val schema: Schema[MapWithRecordValue] = Schema.derived
  }

  case class MapWithListValue(data: Map[String, List[Int]])

  object MapWithListValue {
    implicit val schema: Schema[MapWithListValue] = Schema.derived
  }

  case class NestedMap(data: Map[String, Map[String, Int]])

  object NestedMap {
    implicit val schema: Schema[NestedMap] = Schema.derived
  }

  case class MapOfMaps(outer: Map[Int, Map[String, Double]])

  object MapOfMaps {
    implicit val schema: Schema[MapOfMaps] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftMapKeyTypesSpec")(
    suite("integer key types")(
      test("encode/decode Map[Int, String]") {
        roundTrip(IntKeyMap(Map(1 -> "one", 2 -> "two", 3 -> "three")))
      },
      test("encode/decode Map[Int, String] - empty") {
        roundTrip(IntKeyMap(Map.empty))
      },
      test("encode/decode Map[Int, String] - extreme keys") {
        roundTrip(IntKeyMap(Map(Int.MinValue -> "min", 0 -> "zero", Int.MaxValue -> "max")))
      },
      test("encode/decode Map[Long, String]") {
        roundTrip(LongKeyMap(Map(1L -> "one", Long.MaxValue -> "max", Long.MinValue -> "min")))
      },
      test("encode/decode Map[Long, String] - empty") {
        roundTrip(LongKeyMap(Map.empty))
      }
    ),
    suite("UUID key type")(
      test("encode/decode Map[UUID, String]") {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        roundTrip(UUIDKeyMap(Map(uuid1 -> "first", uuid2 -> "second")))
      },
      test("encode/decode Map[UUID, String] - empty") {
        roundTrip(UUIDKeyMap(Map.empty))
      }
    ),
    suite("boolean key type")(
      test("encode/decode Map[Boolean, String]") {
        roundTrip(BoolKeyMap(Map(true -> "yes", false -> "no")))
      },
      test("encode/decode Map[Boolean, String] - single entry") {
        roundTrip(BoolKeyMap(Map(true -> "only true")))
      }
    ),
    suite("complex value types")(
      test("encode/decode Map[Int, Record]") {
        roundTrip(MapWithRecordValue(Map(1 -> Record("a", 10), 2 -> Record("b", 20))))
      },
      test("encode/decode Map[String, List[Int]]") {
        roundTrip(MapWithListValue(Map("evens" -> List(2, 4, 6), "odds" -> List(1, 3, 5))))
      },
      test("encode/decode Map[String, List[Int]] - empty list value") {
        roundTrip(MapWithListValue(Map("empty" -> List.empty, "full" -> List(1, 2, 3))))
      }
    ),
    suite("nested maps")(
      test("encode/decode Map[String, Map[String, Int]]") {
        roundTrip(
          NestedMap(
            Map(
              "group1" -> Map("a" -> 1, "b" -> 2),
              "group2" -> Map("x" -> 10, "y" -> 20)
            )
          )
        )
      },
      test("encode/decode Map[String, Map[String, Int]] - empty inner") {
        roundTrip(NestedMap(Map("empty" -> Map.empty, "full" -> Map("key" -> 42))))
      },
      test("encode/decode Map[Int, Map[String, Double]]") {
        roundTrip(
          MapOfMaps(
            Map(
              1 -> Map("pi" -> 3.14, "e" -> 2.718),
              2 -> Map("phi" -> 1.618)
            )
          )
        )
      }
    ),
    suite("standalone Map types")(
      test("encode/decode standalone Map[String, Int]") {
        roundTrip(Map("a" -> 1, "b" -> 2, "c" -> 3))
      },
      test("encode/decode standalone Map[Int, Int]") {
        roundTrip(Map(1 -> 10, 2 -> 20, 3 -> 30))
      },
      test("encode/decode standalone Map[Long, Long]") {
        roundTrip(Map(100L -> 1000L, 200L -> 2000L))
      }
    )
  )
}
