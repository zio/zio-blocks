package zio.blocks.schema

import zio.test._

object AsPrimitiveSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("AsPrimitiveSpec")(
    suite("AsLowPriorityImplicits")(
      test("reverseInto creates Into[B, A] from As[A, B]") {
        val asIntString: As[Int, String] = new As[Int, String] {
          def into(a: Int): Either[SchemaError, String] = Right(a.toString)
          def from(b: String): Either[SchemaError, Int] =
            scala.util.Try(b.toInt).toEither.left.map(e => SchemaError.conversionFailed(Nil, e.getMessage))
        }

        val reverseInto: Into[String, Int] = As.reverseInto(asIntString)
        assertTrue(
          reverseInto.into("42") == Right(42),
          reverseInto.into("0") == Right(0),
          reverseInto.into("-100") == Right(-100),
          reverseInto.into("not-a-number").isLeft
        )
      },
      test("As.reverse creates reversed As instance") {
        val asIntString: As[Int, String] = new As[Int, String] {
          def into(a: Int): Either[SchemaError, String] = Right(a.toString)
          def from(b: String): Either[SchemaError, Int] =
            scala.util.Try(b.toInt).toEither.left.map(e => SchemaError.conversionFailed(Nil, e.getMessage))
        }

        val reversed: As[String, Int] = asIntString.reverse
        assertTrue(
          reversed.into("42") == Right(42),
          reversed.from(42) == Right("42"),
          reversed.into("abc").isLeft
        )
      },
      test("As.apply creates from two Into instances") {
        val intoAB: Into[Int, String] = (a: Int) => Right(a.toString)
        val intoBA: Into[String, Int] =
          (b: String) => scala.util.Try(b.toInt).toEither.left.map(e => SchemaError.conversionFailed(Nil, e.getMessage))
        val asIntString: As[Int, String] = As(intoAB, intoBA)

        assertTrue(
          asIntString.into(42) == Right("42"),
          asIntString.from("123") == Right(123),
          asIntString.from("abc").isLeft
        )
      }
    ),
    suite("IntoPrimitiveInstances - Numeric Widening")(
      test("byte widens to all larger types") {
        val b: Byte = 42.toByte
        assertTrue(
          Into[Byte, Short].into(b) == Right(42.toShort),
          Into[Byte, Int].into(b) == Right(42),
          Into[Byte, Long].into(b) == Right(42L),
          Into[Byte, Float].into(b) == Right(42.0f),
          Into[Byte, Double].into(b) == Right(42.0)
        )
      },
      test("short widens to all larger types") {
        val s: Short = 1000.toShort
        assertTrue(
          Into[Short, Int].into(s) == Right(1000),
          Into[Short, Long].into(s) == Right(1000L),
          Into[Short, Float].into(s) == Right(1000.0f),
          Into[Short, Double].into(s) == Right(1000.0)
        )
      },
      test("int widens to all larger types") {
        val i: Int = 100000
        assertTrue(
          Into[Int, Long].into(i) == Right(100000L),
          Into[Int, Float].into(i) == Right(100000.0f),
          Into[Int, Double].into(i) == Right(100000.0)
        )
      },
      test("long widens to float and double") {
        val l: Long = 1000000L
        assertTrue(
          Into[Long, Float].into(l) == Right(1000000.0f),
          Into[Long, Double].into(l) == Right(1000000.0)
        )
      },
      test("float widens to double") {
        val f: Float = 3.14f
        assertTrue(Into[Float, Double].into(f) == Right(3.14f.toDouble))
      }
    ),
    suite("IntoPrimitiveInstances - Numeric Narrowing Success")(
      test("short to byte succeeds within range") {
        assertTrue(
          Into[Short, Byte].into(0.toShort) == Right(0.toByte),
          Into[Short, Byte].into(127.toShort) == Right(127.toByte),
          Into[Short, Byte].into((-128).toShort) == Right((-128).toByte)
        )
      },
      test("int to byte succeeds within range") {
        assertTrue(
          Into[Int, Byte].into(0) == Right(0.toByte),
          Into[Int, Byte].into(127) == Right(127.toByte),
          Into[Int, Byte].into(-128) == Right((-128).toByte)
        )
      },
      test("int to short succeeds within range") {
        assertTrue(
          Into[Int, Short].into(0) == Right(0.toShort),
          Into[Int, Short].into(32767) == Right(32767.toShort),
          Into[Int, Short].into(-32768) == Right((-32768).toShort)
        )
      },
      test("long to byte succeeds within range") {
        assertTrue(
          Into[Long, Byte].into(0L) == Right(0.toByte),
          Into[Long, Byte].into(127L) == Right(127.toByte),
          Into[Long, Byte].into(-128L) == Right((-128).toByte)
        )
      },
      test("long to short succeeds within range") {
        assertTrue(
          Into[Long, Short].into(0L) == Right(0.toShort),
          Into[Long, Short].into(32767L) == Right(32767.toShort),
          Into[Long, Short].into(-32768L) == Right((-32768).toShort)
        )
      },
      test("long to int succeeds within range") {
        assertTrue(
          Into[Long, Int].into(0L) == Right(0),
          Into[Long, Int].into(Int.MaxValue.toLong) == Right(Int.MaxValue),
          Into[Long, Int].into(Int.MinValue.toLong) == Right(Int.MinValue)
        )
      },
      test("double to float succeeds within range") {
        assertTrue(
          Into[Double, Float].into(0.0) == Right(0.0f),
          Into[Double, Float].into(3.14) == Right(3.14.toFloat),
          Into[Double, Float].into(-1000.5) == Right(-1000.5f)
        )
      },
      test("float to int succeeds for whole numbers in range") {
        assertTrue(
          Into[Float, Int].into(0.0f) == Right(0),
          Into[Float, Int].into(100.0f) == Right(100),
          Into[Float, Int].into(-500.0f) == Right(-500)
        )
      },
      test("float to long succeeds for whole numbers in range") {
        assertTrue(
          Into[Float, Long].into(0.0f) == Right(0L),
          Into[Float, Long].into(1000.0f) == Right(1000L),
          Into[Float, Long].into(-5000.0f) == Right(-5000L)
        )
      },
      test("double to int succeeds for whole numbers in range") {
        assertTrue(
          Into[Double, Int].into(0.0) == Right(0),
          Into[Double, Int].into(12345.0) == Right(12345),
          Into[Double, Int].into(-999.0) == Right(-999)
        )
      },
      test("double to long succeeds for whole numbers in range") {
        assertTrue(
          Into[Double, Long].into(0.0) == Right(0L),
          Into[Double, Long].into(123456789.0) == Right(123456789L),
          Into[Double, Long].into(-987654321.0) == Right(-987654321L)
        )
      }
    ),
    suite("IntoPrimitiveInstances - Numeric Narrowing Failure")(
      test("short to byte fails outside range") {
        assertTrue(
          Into[Short, Byte].into(128.toShort).isLeft,
          Into[Short, Byte].into((-129).toShort).isLeft,
          Into[Short, Byte].into(1000.toShort).isLeft
        )
      },
      test("int to byte fails outside range") {
        assertTrue(
          Into[Int, Byte].into(128).isLeft,
          Into[Int, Byte].into(-129).isLeft,
          Into[Int, Byte].into(1000).isLeft
        )
      },
      test("int to short fails outside range") {
        assertTrue(
          Into[Int, Short].into(32768).isLeft,
          Into[Int, Short].into(-32769).isLeft,
          Into[Int, Short].into(100000).isLeft
        )
      },
      test("long to byte fails outside range") {
        assertTrue(
          Into[Long, Byte].into(128L).isLeft,
          Into[Long, Byte].into(-129L).isLeft,
          Into[Long, Byte].into(10000L).isLeft
        )
      },
      test("long to short fails outside range") {
        assertTrue(
          Into[Long, Short].into(32768L).isLeft,
          Into[Long, Short].into(-32769L).isLeft,
          Into[Long, Short].into(100000L).isLeft
        )
      },
      test("long to int fails outside range") {
        assertTrue(
          Into[Long, Int].into(Int.MaxValue.toLong + 1).isLeft,
          Into[Long, Int].into(Int.MinValue.toLong - 1).isLeft,
          Into[Long, Int].into(Long.MaxValue).isLeft
        )
      },
      test("double to float fails outside range") {
        assertTrue(
          Into[Double, Float].into(Double.MaxValue).isLeft,
          Into[Double, Float].into(-Double.MaxValue).isLeft
        )
      },
      test("float to int fails for non-whole numbers") {
        assertTrue(
          Into[Float, Int].into(3.5f).isLeft,
          Into[Float, Int].into(0.1f).isLeft,
          Into[Float, Int].into(-10.999f).isLeft
        )
      },
      test("float to long fails for non-whole numbers") {
        assertTrue(
          Into[Float, Long].into(1.5f).isLeft,
          Into[Float, Long].into(0.001f).isLeft
        )
      },
      test("double to int fails for non-whole numbers") {
        assertTrue(
          Into[Double, Int].into(3.14159).isLeft,
          Into[Double, Int].into(0.5).isLeft
        )
      },
      test("double to long fails for non-whole numbers") {
        assertTrue(
          Into[Double, Long].into(2.71828).isLeft,
          Into[Double, Long].into(0.999).isLeft
        )
      }
    ),
    suite("Into.identity")(
      test("identity Into returns input unchanged") {
        assertTrue(
          Into[Int, Int].into(42) == Right(42),
          Into[String, String].into("hello") == Right("hello"),
          Into[Double, Double].into(3.14) == Right(3.14)
        )
      }
    )
  )
}
