package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer

/**
 * Tests for wrapper/newtype encoding and decoding with ThriftFormat.
 */
object ThriftWrapperSpec extends SchemaBaseSpec {

  case class UserId(value: Long)

  object UserId {
    implicit val schema: Schema[UserId] = Schema.derived
  }

  case class Email(value: String)

  object Email {
    implicit val schema: Schema[Email] = Schema.derived
  }

  case class Counter(value: Int)

  object Counter {
    implicit val schema: Schema[Counter] = Schema.derived
  }

  case class Percentage(value: Double)

  object Percentage {
    implicit val schema: Schema[Percentage] = Schema.derived
  }

  case class Flag(value: Boolean)

  object Flag {
    implicit val schema: Schema[Flag] = Schema.derived
  }

  case class Initial(value: Char)

  object Initial {
    implicit val schema: Schema[Initial] = Schema.derived
  }

  case class SmallNum(value: Byte)

  object SmallNum {
    implicit val schema: Schema[SmallNum] = Schema.derived
  }

  case class ShortId(value: Short)

  object ShortId {
    implicit val schema: Schema[ShortId] = Schema.derived
  }

  case class Score(value: Float)

  object Score {
    implicit val schema: Schema[Score] = Schema.derived
  }

  case class UserWithWrapper(id: UserId, name: String)

  object UserWithWrapper {
    implicit val schema: Schema[UserWithWrapper] = Schema.derived
  }

  case class NestedWrapper(outer: UserId, inner: Email)

  object NestedWrapper {
    implicit val schema: Schema[NestedWrapper] = Schema.derived
  }

  case class WrapperInList(items: List[UserId])

  object WrapperInList {
    implicit val schema: Schema[WrapperInList] = Schema.derived
  }

  case class WrapperInOption(maybeId: Option[UserId])

  object WrapperInOption {
    implicit val schema: Schema[WrapperInOption] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftWrapperSpec")(
    suite("primitive wrappers")(
      test("encode/decode Long wrapper") {
        check(Gen.long)(x => roundTrip(UserId(x)))
      },
      test("encode/decode String wrapper") {
        check(Gen.string)(x => roundTrip(Email(x)))
      },
      test("encode/decode Int wrapper") {
        check(Gen.int)(x => roundTrip(Counter(x)))
      },
      test("encode/decode Double wrapper") {
        check(Gen.double)(x => roundTrip(Percentage(x)))
      },
      test("encode/decode Boolean wrapper") {
        check(Gen.boolean)(x => roundTrip(Flag(x)))
      },
      test("encode/decode Char wrapper") {
        check(Gen.char)(x => roundTrip(Initial(x)))
      },
      test("encode/decode Byte wrapper") {
        check(Gen.byte)(x => roundTrip(SmallNum(x)))
      },
      test("encode/decode Short wrapper") {
        check(Gen.short)(x => roundTrip(ShortId(x)))
      },
      test("encode/decode Float wrapper") {
        check(Gen.float)(x => roundTrip(Score(x)))
      }
    ),
    suite("wrapper in record")(
      test("encode/decode record with wrapper field") {
        roundTrip(UserWithWrapper(UserId(12345L), "Alice"))
      },
      test("encode/decode record with multiple wrapper fields") {
        roundTrip(NestedWrapper(UserId(999L), Email("test@example.com")))
      }
    ),
    suite("wrapper in collections")(
      test("encode/decode list of wrappers") {
        roundTrip(WrapperInList(List(UserId(1L), UserId(2L), UserId(3L))))
      },
      test("encode/decode empty list of wrappers") {
        roundTrip(WrapperInList(List.empty))
      },
      test("encode/decode option of wrapper - Some") {
        roundTrip(WrapperInOption(Some(UserId(42L))))
      },
      test("encode/decode option of wrapper - None") {
        roundTrip(WrapperInOption(None))
      }
    ),
    suite("wrapper edge cases")(
      test("encode/decode wrapper with max Long value") {
        roundTrip(UserId(Long.MaxValue))
      },
      test("encode/decode wrapper with min Long value") {
        roundTrip(UserId(Long.MinValue))
      },
      test("encode/decode wrapper with empty string") {
        roundTrip(Email(""))
      },
      test("encode/decode wrapper with unicode string") {
        roundTrip(Email("hello@世界.com"))
      }
    )
  )
}
