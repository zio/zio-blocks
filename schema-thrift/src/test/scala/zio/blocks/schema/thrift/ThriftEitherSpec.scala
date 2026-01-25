package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer

/**
 * Tests for Either type encoding and decoding with ThriftFormat.
 */
object ThriftEitherSpec extends SchemaBaseSpec {

  case class Error(code: Int, message: String)

  object Error {
    implicit val schema: Schema[Error] = Schema.derived
  }

  case class Success(value: String)

  object Success {
    implicit val schema: Schema[Success] = Schema.derived
  }

  case class RecordWithEither(id: Int, result: Either[String, Int])

  object RecordWithEither {
    implicit val schema: Schema[RecordWithEither] = Schema.derived
  }

  case class NestedEither(data: Either[Either[String, Int], Either[Boolean, Double]])

  object NestedEither {
    implicit val schema: Schema[NestedEither] = Schema.derived
  }

  case class EitherInList(items: List[Either[String, Int]])

  object EitherInList {
    implicit val schema: Schema[EitherInList] = Schema.derived
  }

  case class EitherWithRecords(result: Either[Error, Success])

  object EitherWithRecords {
    implicit val schema: Schema[EitherWithRecords] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftEitherSpec")(
    suite("Either in record")(
      test("encode/decode record with Either field - Left") {
        roundTrip(RecordWithEither(1, Left("failed")))
      },
      test("encode/decode record with Either field - Right") {
        roundTrip(RecordWithEither(2, Right(100)))
      }
    ),
    suite("Either with records")(
      test("encode/decode Either[Error, Success] - Left") {
        roundTrip(EitherWithRecords(Left(Error(404, "Not Found"))))
      },
      test("encode/decode Either[Error, Success] - Right") {
        roundTrip(EitherWithRecords(Right(Success("All good"))))
      }
    ),
    suite("nested Either")(
      test("encode/decode nested Either - Left Left") {
        roundTrip(NestedEither(Left(Left("inner left"))))
      },
      test("encode/decode nested Either - Left Right") {
        roundTrip(NestedEither(Left(Right(42))))
      },
      test("encode/decode nested Either - Right Left") {
        roundTrip(NestedEither(Right(Left(true))))
      },
      test("encode/decode nested Either - Right Right") {
        roundTrip(NestedEither(Right(Right(2.718))))
      }
    ),
    suite("Either in collections")(
      test("encode/decode list of Either - mixed") {
        roundTrip(EitherInList(List(Left("a"), Right(1), Left("b"), Right(2))))
      },
      test("encode/decode list of Either - all Left") {
        roundTrip(EitherInList(List(Left("a"), Left("b"), Left("c"))))
      },
      test("encode/decode list of Either - all Right") {
        roundTrip(EitherInList(List(Right(1), Right(2), Right(3))))
      },
      test("encode/decode empty list of Either") {
        roundTrip(EitherInList(List.empty))
      }
    ),
    suite("Either edge cases")(
      test("encode/decode record with Either with empty string Left") {
        roundTrip(RecordWithEither(1, Left("")))
      },
      test("encode/decode record with Either with extreme int Right") {
        roundTrip(RecordWithEither(2, Right(Int.MaxValue))) &&
        roundTrip(RecordWithEither(3, Right(Int.MinValue)))
      }
    )
  )
}
