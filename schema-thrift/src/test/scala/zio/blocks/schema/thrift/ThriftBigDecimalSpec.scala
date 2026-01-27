package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer
import java.math.{MathContext, RoundingMode}

/**
 * Tests for BigDecimal encoding and decoding with ThriftFormat.
 */
object ThriftBigDecimalSpec extends SchemaBaseSpec {

  case class BigDecimalRecord(value: BigDecimal)

  object BigDecimalRecord {
    implicit val schema: Schema[BigDecimalRecord] = Schema.derived
  }

  case class MultipleBigDecimals(a: BigDecimal, b: BigDecimal, c: BigDecimal)

  object MultipleBigDecimals {
    implicit val schema: Schema[MultipleBigDecimals] = Schema.derived
  }

  case class BigDecimalList(values: List[BigDecimal])

  object BigDecimalList {
    implicit val schema: Schema[BigDecimalList] = Schema.derived
  }

  case class BigDecimalOption(maybe: Option[BigDecimal])

  object BigDecimalOption {
    implicit val schema: Schema[BigDecimalOption] = Schema.derived
  }

  case class BigIntRecord(value: BigInt)

  object BigIntRecord {
    implicit val schema: Schema[BigIntRecord] = Schema.derived
  }

  case class BigIntList(values: List[BigInt])

  object BigIntList {
    implicit val schema: Schema[BigIntList] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftBigDecimalSpec")(
    suite("BigDecimal basic")(
      test("encode/decode zero") {
        roundTrip(BigDecimalRecord(BigDecimal(0)))
      },
      test("encode/decode positive integer") {
        roundTrip(BigDecimalRecord(BigDecimal(12345)))
      },
      test("encode/decode negative integer") {
        roundTrip(BigDecimalRecord(BigDecimal(-12345)))
      },
      test("encode/decode positive decimal") {
        roundTrip(BigDecimalRecord(BigDecimal("123.456")))
      },
      test("encode/decode negative decimal") {
        roundTrip(BigDecimalRecord(BigDecimal("-123.456")))
      },
      test("encode/decode very small decimal") {
        roundTrip(BigDecimalRecord(BigDecimal("0.000000001")))
      }
    ),
    suite("BigDecimal precision")(
      test("encode/decode high precision") {
        roundTrip(BigDecimalRecord(BigDecimal("123456789.123456789123456789")))
      },
      test("encode/decode with specific MathContext - DECIMAL32") {
        val value = BigDecimal("1234567", MathContext.DECIMAL32)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with specific MathContext - DECIMAL64") {
        val value = BigDecimal("1234567890123456", MathContext.DECIMAL64)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with specific MathContext - DECIMAL128") {
        val value = BigDecimal("12345678901234567890123456789", MathContext.DECIMAL128)
        roundTrip(BigDecimalRecord(value))
      }
    ),
    suite("BigDecimal rounding modes")(
      test("encode/decode with HALF_UP rounding") {
        val mc    = new MathContext(10, RoundingMode.HALF_UP)
        val value = BigDecimal("123.456789", mc)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with HALF_DOWN rounding") {
        val mc    = new MathContext(10, RoundingMode.HALF_DOWN)
        val value = BigDecimal("123.456789", mc)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with CEILING rounding") {
        val mc    = new MathContext(10, RoundingMode.CEILING)
        val value = BigDecimal("123.456789", mc)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with FLOOR rounding") {
        val mc    = new MathContext(10, RoundingMode.FLOOR)
        val value = BigDecimal("123.456789", mc)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with DOWN rounding") {
        val mc    = new MathContext(10, RoundingMode.DOWN)
        val value = BigDecimal("123.456789", mc)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with UP rounding") {
        val mc    = new MathContext(10, RoundingMode.UP)
        val value = BigDecimal("123.456789", mc)
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with HALF_EVEN rounding") {
        val mc    = new MathContext(10, RoundingMode.HALF_EVEN)
        val value = BigDecimal("123.456789", mc)
        roundTrip(BigDecimalRecord(value))
      }
    ),
    suite("BigDecimal scale")(
      test("encode/decode with large positive scale") {
        val value = BigDecimal("0." + "0" * 20 + "1")
        roundTrip(BigDecimalRecord(value))
      },
      test("encode/decode with negative scale (large number)") {
        val value = BigDecimal("1" + "0" * 30)
        roundTrip(BigDecimalRecord(value))
      }
    ),
    suite("BigDecimal collections")(
      test("encode/decode multiple BigDecimals in record") {
        roundTrip(
          MultipleBigDecimals(
            BigDecimal("1.1"),
            BigDecimal("2.2"),
            BigDecimal("3.3")
          )
        )
      },
      test("encode/decode list of BigDecimals") {
        roundTrip(BigDecimalList(List(BigDecimal("1.0"), BigDecimal("2.0"), BigDecimal("3.0"))))
      },
      test("encode/decode empty list of BigDecimals") {
        roundTrip(BigDecimalList(List.empty))
      },
      test("encode/decode Option[BigDecimal] - Some") {
        roundTrip(BigDecimalOption(Some(BigDecimal("42.0"))))
      },
      test("encode/decode Option[BigDecimal] - None") {
        roundTrip(BigDecimalOption(None))
      }
    ),
    suite("BigInt")(
      test("encode/decode zero") {
        roundTrip(BigIntRecord(BigInt(0)))
      },
      test("encode/decode positive") {
        roundTrip(BigIntRecord(BigInt("12345678901234567890")))
      },
      test("encode/decode negative") {
        roundTrip(BigIntRecord(BigInt("-12345678901234567890")))
      },
      test("encode/decode very large positive") {
        roundTrip(BigIntRecord(BigInt("9" * 100)))
      },
      test("encode/decode very large negative") {
        roundTrip(BigIntRecord(BigInt("-" + "9" * 100)))
      },
      test("encode/decode list of BigInts") {
        roundTrip(BigIntList(List(BigInt(1), BigInt(2), BigInt("999999999999999999999"))))
      }
    ),
    suite("standalone BigDecimal/BigInt")(
      test("encode/decode standalone BigDecimal") {
        roundTrip(BigDecimal("12345.67890"))
      },
      test("encode/decode standalone BigInt") {
        roundTrip(BigInt("9876543210123456789"))
      }
    )
  )
}
