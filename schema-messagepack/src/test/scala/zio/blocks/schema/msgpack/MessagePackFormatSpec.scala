package zio.blocks.schema.msgpack

import zio.blocks.schema._
import zio.blocks.schema.JavaTimeGen._
import zio.blocks.schema.msgpack.MessagePackTestUtils._
import zio.blocks.typeid.TypeId
import zio.test._
import zio.test.Assertion._

object MessagePackFormatSpec extends SchemaBaseSpec {

  case class Record(name: String, value: Int)

  object Record {
    implicit val schema: Schema[Record] = Schema.derived
  }

  case class BasicInt(value: Int)

  object BasicInt {
    implicit val schema: Schema[BasicInt] = Schema.derived
  }

  case class BasicString(value: String)

  object BasicString {
    implicit val schema: Schema[BasicString] = Schema.derived
  }

  case class BasicFloat(value: Float)

  object BasicFloat {
    implicit val schema: Schema[BasicFloat] = Schema.derived
  }

  case class BasicDouble(value: Double)

  object BasicDouble {
    implicit val schema: Schema[BasicDouble] = Schema.derived
  }

  case class BasicLong(value: Long)

  object BasicLong {
    implicit val schema: Schema[BasicLong] = Schema.derived
  }

  case class BasicBoolean(value: Boolean)

  object BasicBoolean {
    implicit val schema: Schema[BasicBoolean] = Schema.derived
  }

  case class Embedded(inner: BasicInt)

  object Embedded {
    implicit val schema: Schema[Embedded] = Schema.derived
  }

  case class IntList(items: List[Int])

  object IntList {
    implicit val schema: Schema[IntList] = Schema.derived
  }

  case class StringList(items: List[String])

  object StringList {
    implicit val schema: Schema[StringList] = Schema.derived
  }

  case class MapValue(value: Map[String, Record])

  object MapValue {
    implicit val schema: Schema[MapValue] = Schema.derived
  }

  case class SetValue(value: Set[Record])

  object SetValue {
    implicit val schema: Schema[SetValue] = Schema.derived
  }

  sealed trait OneOf

  case class StringValue(value: String) extends OneOf

  case class IntValue(value: Int) extends OneOf

  case class BooleanValue(value: Boolean) extends OneOf

  object OneOf {
    implicit val schema: Schema[OneOf] = Schema.derived
  }

  sealed trait RichSum

  object RichSum {
    case class Person(name: String, age: Int) extends RichSum

    case class AnotherSum(oneOf: OneOf) extends RichSum

    case class LongWrapper(long: Long) extends RichSum

    implicit val schema: Schema[RichSum] = Schema.derived
  }

  case class HighArity(
    f1: Int = 1,
    f2: Int = 2,
    f3: Int = 3,
    f4: Int = 4,
    f5: Int = 5,
    f6: Int = 6,
    f7: Int = 7,
    f8: Int = 8,
    f9: Int = 9,
    f10: Int = 10,
    f11: Int = 11,
    f12: Int = 12,
    f13: Int = 13,
    f14: Int = 14,
    f15: Int = 15,
    f16: Int = 16,
    f17: Int = 17,
    f18: Int = 18,
    f19: Int = 19,
    f20: Int = 20,
    f21: Int = 21,
    f22: Int = 22,
    f23: Int = 23,
    f24: Int = 24
  )

  object HighArity {
    implicit val schema: Schema[HighArity] = Schema.derived
  }

  case class ClassWithOption(number: Int, name: Option[String])

  object ClassWithOption {
    implicit val schema: Schema[ClassWithOption] = Schema.derived
  }

  case class NestedOption(value: Option[Option[Int]])

  object NestedOption {
    implicit val schema: Schema[NestedOption] = Schema.derived
  }

  case class RequestVars(someString: String, second: Int)

  object RequestVars {
    implicit val schema: Schema[RequestVars] = Schema.derived
  }

  case class SearchRequest(query: String, pageNumber: RequestVars, resultPerPage: Int)

  object SearchRequest {
    implicit val schema: Schema[SearchRequest] = Schema.derived
  }

  case class SequenceOfProduct(name: String, records: List[Record], richSum: RichSum)

  object SequenceOfProduct {
    implicit val schema: Schema[SequenceOfProduct] = Schema.derived
  }

  case class SequenceOfSum(value: String, enums: List[RichSum])

  object SequenceOfSum {
    implicit val schema: Schema[SequenceOfSum] = Schema.derived
  }

  case class MapRecord(age: Int, map: Map[Int, String])

  object MapRecord {
    implicit val schema: Schema[MapRecord] = Schema.derived
  }

  case class SetRecord(age: Int, set: Set[String])

  object SetRecord {
    implicit val schema: Schema[SetRecord] = Schema.derived
  }

  case class Recursive(value: Int, next: Option[Recursive])

  object Recursive {
    implicit val schema: Schema[Recursive] = Schema.derived
  }

  case class TreeNode(value: String, children: List[TreeNode])

  object TreeNode {
    implicit val schema: Schema[TreeNode] = Schema.derived
  }

  case class WithTuple2(value: (Int, String))

  object WithTuple2 {
    implicit val schema: Schema[WithTuple2] = Schema.derived
  }

  case class WithTuple3(value: (Int, String, Boolean))

  object WithTuple3 {
    implicit val schema: Schema[WithTuple3] = Schema.derived
  }

  case class ComplexTuple(value: (Record, OneOf))

  object ComplexTuple {
    implicit val schema: Schema[ComplexTuple] = Schema.derived
  }

  case class BasicIntWrapper(basic: BasicInt)

  object BasicIntWrapper {
    implicit val schema: Schema[BasicIntWrapper] = Schema.derived
  }

  case class BasicTwoInts(value1: Int, value2: Int)

  object BasicTwoInts {
    implicit val schema: Schema[BasicTwoInts] = Schema.derived
  }

  case class BasicTwoIntWrapper(basic: BasicTwoInts)

  object BasicTwoIntWrapper {
    implicit val schema: Schema[BasicTwoIntWrapper] = Schema.derived
  }

  case class SeparateWrapper(basic1: BasicInt, basic2: BasicInt)

  object SeparateWrapper {
    implicit val schema: Schema[SeparateWrapper] = Schema.derived
  }

  case class MaxArityCaseClass(
    f1: Int = 1,
    f2: Int = 2,
    f3: Int = 3,
    f4: Int = 4,
    f5: Int = 5,
    f6: Int = 6,
    f7: Int = 7,
    f8: Int = 8,
    f9: Int = 9,
    f10: Int = 10,
    f11: Int = 11,
    f12: Int = 12,
    f13: Int = 13,
    f14: Int = 14,
    f15: Int = 15,
    f16: Int = 16,
    f17: Int = 17,
    f18: Int = 18,
    f19: Int = 19,
    f20: Int = 20,
    f21: Int = 21,
    f22: Int = 22
  )
  object MaxArityCaseClass {
    implicit val schema: Schema[MaxArityCaseClass] = Schema.derived
  }

  case class Enumeration(oneOf: OneOf)

  object Enumeration {
    implicit val schema: Schema[Enumeration] = Schema.derived
  }

  case class RichProduct(stringOneOf: OneOf, basicString: BasicString, record: Record)

  object RichProduct {
    implicit val schema: Schema[RichProduct] = Schema.derived
  }

  case class MyRecord(age: Int)

  object MyRecord {
    implicit val schema: Schema[MyRecord] = Schema.derived
  }

  case class TupleWithEmptyList(value: (String, List[Int], String))

  object TupleWithEmptyList {
    implicit val schema: Schema[TupleWithEmptyList] = Schema.derived
  }

  case class TupleWithNestedEmptyList(value: (String, List[List[Int]], String))

  object TupleWithNestedEmptyList {
    implicit val schema: Schema[TupleWithNestedEmptyList] = Schema.derived
  }

  case class UserId(value: Long)

  object UserId {
    implicit val typeId: TypeId[UserId] = TypeId.of[UserId]
    implicit val schema: Schema[UserId] = Schema[Long].transform[UserId](x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex       = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r
    implicit val typeId: TypeId[Email] = TypeId.of[Email]
    implicit val schema: Schema[Email] =
      Schema[String].transform[Email](
        {
          case x @ EmailRegex(_*) => new Email(x)
          case _                  => throw SchemaError.validationFailed("expected e-mail")
        },
        _.value
      )
  }

  def spec: Spec[TestEnvironment, Any] = suite("MessagePackFormat Spec")(
    suite("primitives")(
      test("Unit") {
        roundTrip(())
      },
      test("Boolean - direct codec") {
        val codec        = MessagePackBinaryCodec.booleanCodec
        val encodedTrue  = codec.encode(true)
        val encodedFalse = codec.encode(false)
        assert(encodedTrue.toSeq)(equalTo(Seq(0xc3.toByte))) &&
        assert(encodedFalse.toSeq)(equalTo(Seq(0xc2.toByte))) &&
        assert(codec.decode(encodedTrue))(isRight(equalTo(true))) &&
        assert(codec.decode(encodedFalse))(isRight(equalTo(false)))
      },
      test("Boolean - via schema") {
        roundTrip(true) && roundTrip(false)
      },
      test("Byte") {
        check(Gen.byte)(x => roundTrip(x))
      },
      test("Short") {
        check(Gen.short)(x => roundTrip(x))
      },
      test("Int") {
        check(Gen.int)(x => roundTrip(x))
      },
      test("Long") {
        check(Gen.long)(x => roundTrip(x))
      },
      test("Float") {
        check(Gen.float.filter(f => !f.isNaN && !f.isInfinite))(x => roundTrip(x))
      },
      test("Double") {
        check(Gen.double.filter(d => !d.isNaN && !d.isInfinite))(x => roundTrip(x))
      },
      test("Char") {
        check(Gen.char.filter(c => !Character.isSurrogate(c)))(x => roundTrip(x))
      },
      test("String") {
        check(Gen.string)(x => roundTrip(x))
      },
      test("BigInt") {
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20)))(x => roundTrip(x))
      },
      test("BigDecimal") {
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20)))(x => roundTrip(x))
      },
      test("UUID") {
        check(Gen.uuid)(x => roundTrip(x))
      },
      test("Currency") {
        check(Gen.currency)(x => roundTrip(x))
      },
      test("binary (Array[Byte])") {
        check(Gen.listOf(Gen.byte).map(_.toArray)) { bytes =>
          val codec   = MessagePackBinaryCodec.binaryCodec
          val encoded = codec.encode(bytes)
          val decoded = codec.decode(encoded)
          assert(decoded.map(_.toSeq))(isRight(equalTo(bytes.toSeq)))
        }
      }
    ),
    suite("java.time types")(
      test("DayOfWeek") {
        check(genDayOfWeek)(x => roundTrip(x))
      },
      test("Duration") {
        check(genDuration)(x => roundTrip(x))
      },
      test("Instant") {
        check(genInstant)(x => roundTrip(x))
      },
      test("LocalDate") {
        check(genLocalDate)(x => roundTrip(x))
      },
      test("LocalDateTime") {
        check(genLocalDateTime)(x => roundTrip(x))
      },
      test("LocalTime") {
        check(genLocalTime)(x => roundTrip(x))
      },
      test("Month") {
        check(genMonth)(x => roundTrip(x))
      },
      test("MonthDay") {
        check(genMonthDay)(x => roundTrip(x))
      },
      test("OffsetDateTime") {
        check(genOffsetDateTime)(x => roundTrip(x))
      },
      test("OffsetTime") {
        check(genOffsetTime)(x => roundTrip(x))
      },
      test("Period") {
        check(genPeriod)(x => roundTrip(x))
      },
      test("Year") {
        check(genYear)(x => roundTrip(x))
      },
      test("YearMonth") {
        check(genYearMonth)(x => roundTrip(x))
      },
      test("ZoneId") {
        check(genZoneId)(x => roundTrip(x))
      },
      test("ZoneOffset") {
        check(genZoneOffset)(x => roundTrip(x))
      },
      test("ZonedDateTime") {
        check(genZonedDateTime)(x => roundTrip(x))
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record("hello", 150))
      },
      test("record with integer") {
        roundTrip(BasicInt(150))
      },
      test("record with string") {
        roundTrip(BasicString("testing"))
      },
      test("record with float") {
        roundTrip(BasicFloat(0.001f))
      },
      test("record with double") {
        roundTrip(BasicDouble(0.001))
      },
      test("record with long") {
        roundTrip(BasicLong(1000L))
      },
      test("record with boolean") {
        roundTrip(BasicBoolean(true)) &&
        roundTrip(BasicBoolean(false))
      },
      test("embedded messages") {
        roundTrip(Embedded(BasicInt(150)))
      },
      test("nested record") {
        val message = SearchRequest("query", RequestVars("var", 1), 100)
        roundTrip(message)
      },
      test("high arity record (>22 fields)") {
        roundTrip(HighArity())
      },
      test("case class with arity 22") {
        roundTrip(MaxArityCaseClass())
      },
      test("integer inside wrapper class") {
        roundTrip(BasicIntWrapper(BasicInt(150)))
      },
      test("two integers") {
        roundTrip(BasicTwoInts(150, 151))
      },
      test("two integers inside wrapper class") {
        roundTrip(BasicTwoIntWrapper(BasicTwoInts(150, 151)))
      },
      test("two wrapped integers inside wrapper class") {
        roundTrip(SeparateWrapper(BasicInt(150), BasicInt(151)))
      },
      test("enumeration wrapper") {
        roundTrip(Enumeration(BooleanValue(true))) &&
        roundTrip(Enumeration(IntValue(482)))
      },
      test("product type with inner product") {
        roundTrip(RichProduct(StringValue("sum_type"), BasicString("string"), Record("value", 47)))
      }
    ),
    suite("collections")(
      test("empty list") {
        roundTrip(List.empty[Int])
      },
      test("list of an empty list") {
        roundTrip(List(List.empty[Int]))
      },
      test("int list") {
        roundTrip(IntList(List(3, 270, 86942)))
      },
      test("empty int list") {
        roundTrip(IntList(List.empty))
      },
      test("string list") {
        roundTrip(StringList(List("foo", "bar", "baz")))
      },
      test("empty string list") {
        roundTrip(StringList(List.empty))
      },
      test("list of records") {
        roundTrip(List(Record("a", 1), Record("b", 2)))
      },
      test("nested list") {
        roundTrip(List(List(1, 2), List(3, 4)))
      },
      test("vector") {
        roundTrip(Vector(1, 2, 3))
      },
      test("set") {
        roundTrip(Set(1, 2, 3))
      },
      test("map") {
        roundTrip(MapValue(Map("a" -> Record("Foo", 123), "b" -> Record("Bar", 456))))
      },
      test("map with int keys") {
        roundTrip(MapRecord(1, Map(1 -> "aaa", 3 -> "ccc")))
      },
      test("set in record") {
        roundTrip(SetRecord(1, Set("aaa", "ccc")))
      },
      test("map of products") {
        roundTrip(Map(Record("AAA", 1) -> MyRecord(1), Record("BBB", 2) -> MyRecord(2)))
      },
      test("set of products") {
        roundTrip(Set(Record("AAA", 1), Record("BBB", 2)))
      },
      test("sequence of products") {
        roundTrip(
          SequenceOfProduct(
            "hello",
            List(Record("Jan", 30), Record("xxx", 40), Record("Peter", 22)),
            RichSum.LongWrapper(150L)
          )
        )
      },
      test("sequence of sums") {
        roundTrip(SequenceOfSum("hello", List(RichSum.LongWrapper(150L), RichSum.LongWrapper(200L))))
      }
    ),
    suite("optionals")(
      test("standalone Some for primitives") {
        check(Gen.boolean)(x => roundTrip(Some(x): Option[Boolean])) &&
        check(Gen.byte)(x => roundTrip(Some(x): Option[Byte])) &&
        check(Gen.char)(x => roundTrip(Some(x): Option[Char])) &&
        check(Gen.short)(x => roundTrip(Some(x): Option[Short])) &&
        check(Gen.float)(x => roundTrip(Some(x): Option[Float])) &&
        check(Gen.int)(x => roundTrip(Some(x): Option[Int])) &&
        check(Gen.double)(x => roundTrip(Some(x): Option[Double])) &&
        check(Gen.long)(x => roundTrip(Some(x): Option[Long])) &&
        check(Gen.string)(x => roundTrip(Some(x): Option[String])) &&
        check(Gen.unit)(x => roundTrip(Some(x): Option[Unit]))
      },
      test("option with value") {
        roundTrip(ClassWithOption(42, Some("hello")))
      },
      test("option without value") {
        roundTrip(ClassWithOption(42, None))
      },
      test("standalone Some") {
        roundTrip(Some(42): Option[Int])
      },
      test("standalone None") {
        roundTrip(None: Option[Int])
      },
      test("nested option Some(Some)") {
        roundTrip(NestedOption(Some(Some(42))))
      },
      test("nested option Some(None)") {
        roundTrip(NestedOption(Some(None)))
      },
      test("nested option None") {
        roundTrip(NestedOption(None))
      },
      test("complex optional with sum type") {
        roundTrip(Some(BooleanValue(true)): Option[OneOf])
      },
      test("complex optional with product type") {
        roundTrip(Some(Record("hello earth", 21)): Option[Record])
      },
      test("optional of product type within optional") {
        roundTrip(Some(Some(Record("hello", 10))): Option[Option[Record]])
      },
      test("optional of sum type within optional") {
        roundTrip(Some(Some(BooleanValue(true))): Option[Option[OneOf]])
      }
    ),
    suite("variants (sealed traits)")(
      test("variant - StringValue") {
        roundTrip[OneOf](StringValue("hello"))
      },
      test("variant - IntValue") {
        roundTrip[OneOf](IntValue(42))
      },
      test("variant - BooleanValue") {
        roundTrip[OneOf](BooleanValue(true))
      },
      test("rich sum - Person") {
        roundTrip[RichSum](RichSum.Person("John", 30))
      },
      test("rich sum - AnotherSum") {
        roundTrip[RichSum](RichSum.AnotherSum(IntValue(42)))
      },
      test("rich sum - LongWrapper") {
        roundTrip[RichSum](RichSum.LongWrapper(150L))
      }
    ),
    suite("Either")(
      test("either left") {
        roundTrip[Either[String, Int]](Left("error"))
      },
      test("either right") {
        roundTrip[Either[String, Int]](Right(42))
      },
      test("either with product type") {
        roundTrip[Either[String, Record]](Right(Record("test", 123)))
      },
      test("either with sum type") {
        roundTrip[Either[String, OneOf]](Right(IntValue(42)))
      },
      test("either within either") {
        roundTrip[Either[String, Either[Int, Boolean]]](Right(Right(true))) &&
        roundTrip[Either[String, Either[Int, Boolean]]](Right(Left(42))) &&
        roundTrip[Either[String, Either[Int, Boolean]]](Left("error"))
      },
      test("complex either with product type") {
        roundTrip[Either[Record, RichSum]](Left(Record("left", 1))) &&
        roundTrip[Either[Record, RichSum]](Right(RichSum.Person("right", 2)))
      }
    ),
    suite("Tuple types")(
      test("Tuple2 - simple") {
        roundTrip(WithTuple2((123, "hello")))
      },
      test("Tuple3 - simple") {
        roundTrip(WithTuple3((1, "test", true)))
      },
      test("Tuple with complex types") {
        roundTrip(ComplexTuple((Record("foo", 42), IntValue(100))))
      },
      test("tuple containing empty list") {
        roundTrip(TupleWithEmptyList(("first string", List.empty, "second string")))
      },
      test("tuple containing list of empty list") {
        roundTrip(TupleWithNestedEmptyList(("first string", List(List.empty), "second string")))
      }
    ),
    suite("recursive data structures")(
      test("simple recursive - single level") {
        roundTrip(Recursive(1, None))
      },
      test("simple recursive - two levels") {
        roundTrip(Recursive(1, Some(Recursive(2, None))))
      },
      test("simple recursive - deep nesting") {
        roundTrip(Recursive(1, Some(Recursive(2, Some(Recursive(3, Some(Recursive(4, None))))))))
      },
      test("tree structure - leaf") {
        roundTrip(TreeNode("root", Nil))
      },
      test("tree structure - with children") {
        roundTrip(
          TreeNode(
            "root",
            List(
              TreeNode("child1", Nil),
              TreeNode("child2", List(TreeNode("grandchild", Nil)))
            )
          )
        )
      },
      test("list of recursive") {
        roundTrip(
          List(
            Recursive(1, None),
            Recursive(2, Some(Recursive(3, None)))
          )
        )
      }
    ),
    suite("wrapper")(
      test("simple wrappers") {
        roundTrip(UserId(1L)) &&
        roundTrip(Email("xxx@test.com"))
      }
    ),
    suite("error handling")(
      test("empty input - Boolean") {
        decodeError[Boolean]("", "Unexpected end of input")
      },
      test("empty input - Int") {
        decodeError[Int]("", "Unexpected end of input")
      },
      test("empty input - String") {
        decodeError[String]("", "Unexpected end of input")
      },
      test("empty input - Record") {
        decodeError[Record]("", "Unexpected end of input")
      },
      test("wrong type - expected boolean got int") {
        decodeError[Boolean]("01", "Expected boolean")
      },
      test("wrong type - expected int got string") {
        decodeError[Int]("a568656c6c6f", "Expected int value")
      },
      test("wrong type - expected string got int") {
        decodeError[String]("01", "Expected string header")
      },
      test("truncated string - header says 5 bytes but only 2 present") {
        decodeError[String]("a56162", "exceeds remaining")
      },
      test("truncated array - header says 3 elements but only 1 present") {
        decodeError[List[Int]]("9301", "Unexpected end of input")
      },
      test("truncated map - header says 2 entries but only 1 present") {
        decodeError[Map[String, Int]]("82a16101", "Unexpected end of input")
      },
      test("truncated float - only 2 bytes after marker") {
        decodeError[Float]("ca0000", "Unexpected end of input")
      },
      test("truncated double - only 4 bytes after marker") {
        decodeError[Double]("cb00000000", "Unexpected end of input")
      },
      test("variant with invalid format - array instead of int") {
        // 0x91 is fixarray with 1 element, but variant expects int index first
        decodeError[OneOf]("9100", "Expected int value, got: 145")
      },
      test("variant with out of range index") {
        // 0x0a = index 10 (out of range for OneOf which has 3 cases), followed by fixstr "hello"
        decodeError[OneOf]("0aa568656c6c6f", "Expected variant index from 0 to")
      },
      test("record with wrong field count") {
        decodeError[BasicInt]("80", "Expected 1 fields, got: 0")
      },
      test("record with wrong field name") {
        // Map with "wrong" instead of expected "value": {wrong: 1}
        decodeError[BasicInt]("81a577726f6e6701", "Unknown field: wrong")
      }
    )
  )
}
