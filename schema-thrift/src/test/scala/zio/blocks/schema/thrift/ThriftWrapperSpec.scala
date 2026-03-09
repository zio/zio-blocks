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
    implicit val schema: Schema[UserId] =
      Schema[Long].transform[UserId](x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

    implicit val schema: Schema[Email] =
      Schema[String].transform[Email](
        {
          case x @ EmailRegex(_*) => new Email(x)
          case _                  => throw SchemaError.validationFailed("expected e-mail")
        },
        _.value
      )
  }

  case class Counter(value: Int)

  object Counter {
    implicit val schema: Schema[Counter] = Schema[Int].transform[Counter](x => new Counter(x), _.value)
  }

  case class Percentage(value: Double)

  object Percentage {
    implicit val schema: Schema[Percentage] = Schema[Double].transform[Percentage](x => new Percentage(x), _.value)
  }

  case class Flag(value: Boolean)

  object Flag {
    implicit val schema: Schema[Flag] = Schema[Boolean].transform[Flag](x => new Flag(x), _.value)
  }

  case class Initial(value: Char)

  object Initial {
    implicit val schema: Schema[Initial] = Schema[Char].transform[Initial](x => new Initial(x), _.value)
  }

  case class Flags(value: Byte)

  object Flags {
    implicit val schema: Schema[Flags] = Schema[Byte].transform[Flags](x => new Flags(x), _.value)
  }

  case class ShortId(value: Short)

  object ShortId {
    implicit val schema: Schema[ShortId] = Schema[Short].transform[ShortId](x => new ShortId(x), _.value)
  }

  case class Score(value: Float)

  object Score {
    implicit val schema: Schema[Score] = Schema[Float].transform[Score](x => new Score(x), _.value)
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
        check(Gen.byte)(x => roundTrip(Flags(x)))
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
      }
    )
  )
}
