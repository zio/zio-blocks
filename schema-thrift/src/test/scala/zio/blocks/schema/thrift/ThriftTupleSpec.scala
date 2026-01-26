package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer

/**
 * Tests for tuple encoding and decoding with ThriftFormat.
 */
object ThriftTupleSpec extends SchemaBaseSpec {

  case class RecordWithTuple2(pair: (String, Int))

  object RecordWithTuple2 {
    implicit val schema: Schema[RecordWithTuple2] = Schema.derived
  }

  case class RecordWithTuple3(triple: (String, Int, Boolean))

  object RecordWithTuple3 {
    implicit val schema: Schema[RecordWithTuple3] = Schema.derived
  }

  case class RecordWithTuple4(quad: (String, Int, Boolean, Double))

  object RecordWithTuple4 {
    implicit val schema: Schema[RecordWithTuple4] = Schema.derived
  }

  case class NestedTuple(nested: ((String, Int), (Boolean, Double)))

  object NestedTuple {
    implicit val schema: Schema[NestedTuple] = Schema.derived
  }

  case class TupleOfOptions(data: (Option[String], Option[Int]))

  object TupleOfOptions {
    implicit val schema: Schema[TupleOfOptions] = Schema.derived
  }

  case class TupleInList(items: List[(String, Int)])

  object TupleInList {
    implicit val schema: Schema[TupleInList] = Schema.derived
  }

  case class Inner(value: Int)

  object Inner {
    implicit val schema: Schema[Inner] = Schema.derived
  }

  case class TupleWithRecords(pair: (Inner, Inner))

  object TupleWithRecords {
    implicit val schema: Schema[TupleWithRecords] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftTupleSpec")(
    suite("Tuple2")(
      test("encode/decode record with Tuple2") {
        roundTrip(RecordWithTuple2(("name", 100)))
      }
    ),
    suite("Tuple3")(
      test("encode/decode record with Tuple3") {
        roundTrip(RecordWithTuple3(("example", 456, false)))
      }
    ),
    suite("Tuple4")(
      test("encode/decode record with Tuple4") {
        roundTrip(RecordWithTuple4(("quad", 999, false, 1.618)))
      }
    ),
    suite("nested tuples")(
      test("encode/decode nested tuple pairs") {
        roundTrip(NestedTuple((("inner", 10), (true, 5.5))))
      }
    ),
    suite("tuples with Option")(
      test("encode/decode tuple of Options - both Some") {
        roundTrip(TupleOfOptions((Some("value"), Some(42))))
      },
      test("encode/decode tuple of Options - first None") {
        roundTrip(TupleOfOptions((None, Some(42))))
      },
      test("encode/decode tuple of Options - second None") {
        roundTrip(TupleOfOptions((Some("value"), None)))
      },
      test("encode/decode tuple of Options - both None") {
        roundTrip(TupleOfOptions((None, None)))
      }
    ),
    suite("tuples in collections")(
      test("encode/decode List of Tuple2") {
        roundTrip(TupleInList(List(("a", 1), ("b", 2), ("c", 3))))
      },
      test("encode/decode empty List of Tuple2") {
        roundTrip(TupleInList(List.empty))
      }
    ),
    suite("tuples with records")(
      test("encode/decode tuple of records") {
        roundTrip(TupleWithRecords((Inner(10), Inner(20))))
      }
    ),
    suite("tuple edge cases")(
      test("encode/decode record with tuple with empty string") {
        roundTrip(RecordWithTuple2(("", 0)))
      },
      test("encode/decode record with tuple with unicode") {
        roundTrip(RecordWithTuple2(("hello 世界", 42)))
      }
    )
  )
}
