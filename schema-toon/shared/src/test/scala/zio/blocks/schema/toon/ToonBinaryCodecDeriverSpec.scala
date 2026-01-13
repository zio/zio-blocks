package zio.blocks.schema.toon

import zio.blocks.schema.toon.ToonTestUtils._
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object ToonBinaryCodecDeriverSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ToonBinaryCodecDeriverSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), "null")
      },
      test("Boolean") {
        roundTrip(true, "true") &&
        roundTrip(false, "false")
      },
      test("Byte") {
        roundTrip(1: Byte, "1") &&
        roundTrip(10: Byte, "10") &&
        roundTrip(Byte.MinValue, "-128") &&
        roundTrip(Byte.MaxValue, "127")
      },
      test("Short") {
        roundTrip(1: Short, "1") &&
        roundTrip(10: Short, "10") &&
        roundTrip(100: Short, "100") &&
        roundTrip(Short.MinValue, "-32768") &&
        roundTrip(Short.MaxValue, "32767")
      },
      test("Int") {
        roundTrip(1, "1") &&
        roundTrip(Int.MinValue, "-2147483648") &&
        roundTrip(Int.MaxValue, "2147483647")
      },
      test("Long") {
        roundTrip(1L, "1") &&
        roundTrip(Long.MinValue, "-9223372036854775808") &&
        roundTrip(Long.MaxValue, "9223372036854775807")
      },
      test("Float") {
        // TOON uses canonical number format (no exponent)
        roundTrip(1.5f, "1.5") &&
        roundTrip(-3.14f, "-3.14")
      },
      test("Double") {
        roundTrip(1.5, "1.5") &&
        roundTrip(-3.14, "-3.14")
      },
      test("String") {
        roundTrip("hello", "hello") &&
        roundTrip("", "\"\"") && // Empty string needs quotes
        roundTrip("with space", "\"with space\"") && // Spaces in values need quotes
        roundTrip("true", "\"true\"") && // Reserved words need quotes
        roundTrip("false", "\"false\"") &&
        roundTrip("null", "\"null\"")
      },
      test("BigInt") {
        roundTrip(BigInt("12345678901234567890"), "12345678901234567890")
      },
      test("BigDecimal") {
        // TOON uses canonical decimal format (no trailing zeros)
        roundTrip(BigDecimal("123.45"), "123.45")
      }
    ),
    suite("records")(
      test("simple case class") {
        case class Person(name: String, age: Int)
        implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
        // TOON object format with newlines and indentation
        roundTrip(Person("Alice", 30), "name: Alice\nage: 30")
      }
    ),
    suite("sequences")(
      test("inline array of ints") {
        implicit val schema: Schema[List[Int]] = DeriveSchema.gen[List[Int]]
        // TOON inline array format: key[N]: v1,v2,v3
        roundTrip(List(1, 2, 3), "[3]: 1,2,3")
      },
      test("inline array of strings") {
        implicit val schema: Schema[List[String]] = DeriveSchema.gen[List[String]]
        roundTrip(List("a", "b", "c"), "[3]: a,b,c")
      }
    )
  )
}
