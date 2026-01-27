package zio.blocks.schema

import zio.test._

/**
 * Comprehensive tests for IntoPrimitiveInstances and IntoContainerInstances to
 * increase coverage of Into.scala (currently at 37.97% for
 * IntoPrimitiveInstances).
 */
object IntoPrimitiveSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("IntoPrimitiveSpec")(
    suite("Numeric Widening (Lossless)")(
      suite("Byte widening")(
        test("byteToShort with boundary values") {
          assertTrue(
            Into[Byte, Short].into(Byte.MinValue) == Right(Byte.MinValue.toShort),
            Into[Byte, Short].into(Byte.MaxValue) == Right(Byte.MaxValue.toShort),
            Into[Byte, Short].into(0.toByte) == Right(0.toShort),
            Into[Byte, Short].into((-1).toByte) == Right((-1).toShort)
          )
        },
        test("byteToInt with boundary values") {
          assertTrue(
            Into[Byte, Int].into(Byte.MinValue) == Right(Byte.MinValue.toInt),
            Into[Byte, Int].into(Byte.MaxValue) == Right(Byte.MaxValue.toInt),
            Into[Byte, Int].into(42.toByte) == Right(42)
          )
        },
        test("byteToLong with boundary values") {
          assertTrue(
            Into[Byte, Long].into(Byte.MinValue) == Right(Byte.MinValue.toLong),
            Into[Byte, Long].into(Byte.MaxValue) == Right(Byte.MaxValue.toLong),
            Into[Byte, Long].into(99.toByte) == Right(99L)
          )
        },
        test("byteToFloat with boundary values") {
          assertTrue(
            Into[Byte, Float].into(Byte.MinValue) == Right(Byte.MinValue.toFloat),
            Into[Byte, Float].into(Byte.MaxValue) == Right(Byte.MaxValue.toFloat),
            Into[Byte, Float].into(0.toByte) == Right(0.0f)
          )
        },
        test("byteToDouble with boundary values") {
          assertTrue(
            Into[Byte, Double].into(Byte.MinValue) == Right(Byte.MinValue.toDouble),
            Into[Byte, Double].into(Byte.MaxValue) == Right(Byte.MaxValue.toDouble),
            Into[Byte, Double].into(50.toByte) == Right(50.0)
          )
        }
      ),
      suite("Short widening")(
        test("shortToInt with boundary values") {
          assertTrue(
            Into[Short, Int].into(Short.MinValue) == Right(Short.MinValue.toInt),
            Into[Short, Int].into(Short.MaxValue) == Right(Short.MaxValue.toInt),
            Into[Short, Int].into(0.toShort) == Right(0)
          )
        },
        test("shortToLong with boundary values") {
          assertTrue(
            Into[Short, Long].into(Short.MinValue) == Right(Short.MinValue.toLong),
            Into[Short, Long].into(Short.MaxValue) == Right(Short.MaxValue.toLong),
            Into[Short, Long].into(1000.toShort) == Right(1000L)
          )
        },
        test("shortToFloat with boundary values") {
          assertTrue(
            Into[Short, Float].into(Short.MinValue) == Right(Short.MinValue.toFloat),
            Into[Short, Float].into(Short.MaxValue) == Right(Short.MaxValue.toFloat)
          )
        },
        test("shortToDouble with boundary values") {
          assertTrue(
            Into[Short, Double].into(Short.MinValue) == Right(Short.MinValue.toDouble),
            Into[Short, Double].into(Short.MaxValue) == Right(Short.MaxValue.toDouble)
          )
        }
      ),
      suite("Int widening")(
        test("intToLong with boundary values") {
          assertTrue(
            Into[Int, Long].into(Int.MinValue) == Right(Int.MinValue.toLong),
            Into[Int, Long].into(Int.MaxValue) == Right(Int.MaxValue.toLong),
            Into[Int, Long].into(0) == Right(0L)
          )
        },
        test("intToFloat with boundary values") {
          assertTrue(
            Into[Int, Float].into(Int.MinValue) == Right(Int.MinValue.toFloat),
            Into[Int, Float].into(Int.MaxValue) == Right(Int.MaxValue.toFloat)
          )
        },
        test("intToDouble with boundary values") {
          assertTrue(
            Into[Int, Double].into(Int.MinValue) == Right(Int.MinValue.toDouble),
            Into[Int, Double].into(Int.MaxValue) == Right(Int.MaxValue.toDouble)
          )
        }
      ),
      suite("Long widening")(
        test("longToFloat with boundary values") {
          assertTrue(
            Into[Long, Float].into(Long.MinValue) == Right(Long.MinValue.toFloat),
            Into[Long, Float].into(Long.MaxValue) == Right(Long.MaxValue.toFloat),
            Into[Long, Float].into(0L) == Right(0.0f)
          )
        },
        test("longToDouble with boundary values") {
          assertTrue(
            Into[Long, Double].into(Long.MinValue) == Right(Long.MinValue.toDouble),
            Into[Long, Double].into(Long.MaxValue) == Right(Long.MaxValue.toDouble),
            Into[Long, Double].into(123456789L) == Right(123456789.0)
          )
        }
      ),
      suite("Float widening")(
        test("floatToDouble with boundary values") {
          assertTrue(
            Into[Float, Double].into(Float.MinValue) == Right(Float.MinValue.toDouble),
            Into[Float, Double].into(Float.MaxValue) == Right(Float.MaxValue.toDouble),
            Into[Float, Double].into(0.0f) == Right(0.0),
            Into[Float, Double].into(-1.5f) == Right(-1.5)
          )
        }
      )
    ),
    suite("Numeric Narrowing (with Runtime Validation)")(
      suite("Short to Byte")(
        test("succeeds for in-range values") {
          assertTrue(
            Into[Short, Byte].into(0.toShort) == Right(0.toByte),
            Into[Short, Byte].into(Byte.MaxValue.toShort) == Right(Byte.MaxValue),
            Into[Short, Byte].into(Byte.MinValue.toShort) == Right(Byte.MinValue),
            Into[Short, Byte].into(100.toShort) == Right(100.toByte),
            Into[Short, Byte].into((-100).toShort) == Right((-100).toByte)
          )
        },
        test("fails for out-of-range values") {
          assertTrue(
            Into[Short, Byte].into((Byte.MaxValue + 1).toShort).isLeft,
            Into[Short, Byte].into((Byte.MinValue - 1).toShort).isLeft,
            Into[Short, Byte].into(Short.MaxValue).isLeft,
            Into[Short, Byte].into(Short.MinValue).isLeft,
            Into[Short, Byte].into(200.toShort).isLeft
          )
        }
      ),
      suite("Int to Byte")(
        test("succeeds for in-range values") {
          assertTrue(
            Into[Int, Byte].into(0) == Right(0.toByte),
            Into[Int, Byte].into(Byte.MaxValue.toInt) == Right(Byte.MaxValue),
            Into[Int, Byte].into(Byte.MinValue.toInt) == Right(Byte.MinValue),
            Into[Int, Byte].into(50) == Right(50.toByte)
          )
        },
        test("fails for out-of-range values") {
          assertTrue(
            Into[Int, Byte].into(Byte.MaxValue + 1).isLeft,
            Into[Int, Byte].into(Byte.MinValue - 1).isLeft,
            Into[Int, Byte].into(Int.MaxValue).isLeft,
            Into[Int, Byte].into(Int.MinValue).isLeft,
            Into[Int, Byte].into(1000).isLeft
          )
        }
      ),
      suite("Int to Short")(
        test("succeeds for in-range values") {
          assertTrue(
            Into[Int, Short].into(0) == Right(0.toShort),
            Into[Int, Short].into(Short.MaxValue.toInt) == Right(Short.MaxValue),
            Into[Int, Short].into(Short.MinValue.toInt) == Right(Short.MinValue),
            Into[Int, Short].into(10000) == Right(10000.toShort)
          )
        },
        test("fails for out-of-range values") {
          assertTrue(
            Into[Int, Short].into(Short.MaxValue + 1).isLeft,
            Into[Int, Short].into(Short.MinValue - 1).isLeft,
            Into[Int, Short].into(Int.MaxValue).isLeft,
            Into[Int, Short].into(Int.MinValue).isLeft,
            Into[Int, Short].into(100000).isLeft
          )
        }
      ),
      suite("Long to Byte")(
        test("succeeds for in-range values") {
          assertTrue(
            Into[Long, Byte].into(0L) == Right(0.toByte),
            Into[Long, Byte].into(Byte.MaxValue.toLong) == Right(Byte.MaxValue),
            Into[Long, Byte].into(Byte.MinValue.toLong) == Right(Byte.MinValue),
            Into[Long, Byte].into(-50L) == Right((-50).toByte)
          )
        },
        test("fails for out-of-range values") {
          assertTrue(
            Into[Long, Byte].into(Byte.MaxValue + 1L).isLeft,
            Into[Long, Byte].into(Byte.MinValue - 1L).isLeft,
            Into[Long, Byte].into(Long.MaxValue).isLeft,
            Into[Long, Byte].into(Long.MinValue).isLeft
          )
        }
      ),
      suite("Long to Short")(
        test("succeeds for in-range values") {
          assertTrue(
            Into[Long, Short].into(0L) == Right(0.toShort),
            Into[Long, Short].into(Short.MaxValue.toLong) == Right(Short.MaxValue),
            Into[Long, Short].into(Short.MinValue.toLong) == Right(Short.MinValue)
          )
        },
        test("fails for out-of-range values") {
          assertTrue(
            Into[Long, Short].into(Short.MaxValue + 1L).isLeft,
            Into[Long, Short].into(Short.MinValue - 1L).isLeft,
            Into[Long, Short].into(Long.MaxValue).isLeft,
            Into[Long, Short].into(Long.MinValue).isLeft
          )
        }
      ),
      suite("Long to Int")(
        test("succeeds for in-range values") {
          assertTrue(
            Into[Long, Int].into(0L) == Right(0),
            Into[Long, Int].into(Int.MaxValue.toLong) == Right(Int.MaxValue),
            Into[Long, Int].into(Int.MinValue.toLong) == Right(Int.MinValue),
            Into[Long, Int].into(1000000L) == Right(1000000)
          )
        },
        test("fails for out-of-range values") {
          assertTrue(
            Into[Long, Int].into(Int.MaxValue.toLong + 1L).isLeft,
            Into[Long, Int].into(Int.MinValue.toLong - 1L).isLeft,
            Into[Long, Int].into(Long.MaxValue).isLeft,
            Into[Long, Int].into(Long.MinValue).isLeft
          )
        }
      ),
      suite("Double to Float")(
        test("succeeds for in-range values") {
          assertTrue(
            Into[Double, Float].into(0.0) == Right(0.0f),
            Into[Double, Float].into(1.5) == Right(1.5f),
            Into[Double, Float].into(-Float.MaxValue.toDouble) == Right(-Float.MaxValue),
            Into[Double, Float].into(Float.MaxValue.toDouble) == Right(Float.MaxValue)
          )
        },
        test("fails for out-of-range values") {
          assertTrue(
            Into[Double, Float].into(Double.MaxValue).isLeft,
            Into[Double, Float].into(Double.MinValue).isLeft,
            Into[Double, Float].into(Float.MaxValue.toDouble * 2).isLeft,
            Into[Double, Float].into(-Float.MaxValue.toDouble * 2).isLeft
          )
        }
      ),
      suite("Float to Int")(
        test("succeeds for precisely convertible values") {
          assertTrue(
            Into[Float, Int].into(0.0f) == Right(0),
            Into[Float, Int].into(100.0f) == Right(100),
            Into[Float, Int].into(-100.0f) == Right(-100)
          )
        },
        test("fails for non-integer float values") {
          assertTrue(
            Into[Float, Int].into(1.5f).isLeft,
            Into[Float, Int].into(-2.7f).isLeft,
            Into[Float, Int].into(Float.MaxValue).isLeft
          )
        }
      ),
      suite("Float to Long")(
        test("succeeds for precisely convertible values") {
          assertTrue(
            Into[Float, Long].into(0.0f) == Right(0L),
            Into[Float, Long].into(100.0f) == Right(100L),
            Into[Float, Long].into(-100.0f) == Right(-100L)
          )
        },
        test("fails for non-integer float values") {
          assertTrue(
            Into[Float, Long].into(1.5f).isLeft,
            Into[Float, Long].into(-2.7f).isLeft
          )
        }
      ),
      suite("Double to Int")(
        test("succeeds for precisely convertible values") {
          assertTrue(
            Into[Double, Int].into(0.0) == Right(0),
            Into[Double, Int].into(100.0) == Right(100),
            Into[Double, Int].into(-100.0) == Right(-100),
            Into[Double, Int].into(Int.MaxValue.toDouble) == Right(Int.MaxValue),
            Into[Double, Int].into(Int.MinValue.toDouble) == Right(Int.MinValue)
          )
        },
        test("fails for non-integer double values") {
          assertTrue(
            Into[Double, Int].into(1.5).isLeft,
            Into[Double, Int].into(-2.7).isLeft,
            Into[Double, Int].into(Double.MaxValue).isLeft
          )
        }
      ),
      suite("Double to Long")(
        test("succeeds for precisely convertible values") {
          assertTrue(
            Into[Double, Long].into(0.0) == Right(0L),
            Into[Double, Long].into(100.0) == Right(100L),
            Into[Double, Long].into(-100.0) == Right(-100L)
          )
        },
        test("fails for non-integer double values") {
          assertTrue(
            Into[Double, Long].into(1.5).isLeft,
            Into[Double, Long].into(-2.7).isLeft
          )
        }
      )
    ),
    suite("Container Instances")(
      suite("Option")(
        test("Some with successful conversion") {
          assertTrue(
            Into[Option[Byte], Option[Int]].into(Some(42.toByte)) == Right(Some(42)),
            Into[Option[Int], Option[Long]].into(Some(100)) == Right(Some(100L))
          )
        },
        test("None conversion") {
          assertTrue(
            Into[Option[Byte], Option[Int]].into(None) == Right(None),
            Into[Option[Long], Option[Int]].into(None) == Right(None)
          )
        },
        test("Some with failing narrowing conversion") {
          assertTrue(
            Into[Option[Long], Option[Int]].into(Some(Long.MaxValue)).isLeft,
            Into[Option[Int], Option[Byte]].into(Some(1000)).isLeft
          )
        }
      ),
      suite("Either")(
        test("Right with successful conversion") {
          val result: Either[SchemaError, Either[String, Int]] =
            Into[Either[String, Byte], Either[String, Int]].into(Right(42.toByte))
          assertTrue(result == Right(Right(42)))
        },
        test("Left with successful conversion") {
          val result: Either[SchemaError, Either[Long, String]] =
            Into[Either[Int, String], Either[Long, String]].into(Left(100))
          assertTrue(result == Right(Left(100L)))
        },
        test("Right with failing conversion") {
          assertTrue(
            Into[Either[String, Long], Either[String, Int]].into(Right(Long.MaxValue)).isLeft
          )
        },
        test("Left with failing conversion") {
          assertTrue(
            Into[Either[Long, String], Either[Int, String]].into(Left(Long.MaxValue)).isLeft
          )
        }
      ),
      suite("Map")(
        test("empty map conversion") {
          assertTrue(
            Into[Map[Int, Long], Map[Long, Int]].into(Map.empty) == Right(Map.empty)
          )
        },
        test("map with successful key and value conversion") {
          assertTrue(
            Into[Map[Byte, Byte], Map[Int, Int]].into(Map(1.toByte -> 2.toByte)) == Right(Map(1 -> 2))
          )
        },
        test("map with failing key conversion") {
          assertTrue(
            Into[Map[Long, Int], Map[Int, Int]].into(Map(Long.MaxValue -> 1)).isLeft
          )
        },
        test("map with failing value conversion") {
          assertTrue(
            Into[Map[Int, Long], Map[Int, Int]].into(Map(1 -> Long.MaxValue)).isLeft
          )
        },
        test("nested map conversion") {
          case class Inner(value: Int)
          case class Outer(value: Long)
          implicit val innerToOuter: Into[Inner, Outer] = Into.derived[Inner, Outer]
          val result                                    = Into[Map[String, Inner], Map[String, Outer]].into(Map("key" -> Inner(42)))
          assertTrue(result == Right(Map("key" -> Outer(42L))))
        }
      ),
      suite("Iterable")(
        test("List to Vector conversion") {
          assertTrue(
            Into[List[Byte], Vector[Int]].into(List(1.toByte, 2.toByte, 3.toByte)) == Right(Vector(1, 2, 3))
          )
        },
        test("List to Set conversion with element coercion") {
          assertTrue(
            Into[List[Byte], Set[Long]].into(List(1.toByte, 2.toByte, 3.toByte)) == Right(Set(1L, 2L, 3L))
          )
        },
        test("Vector to List conversion") {
          assertTrue(
            Into[Vector[Int], List[Long]].into(Vector(1, 2, 3)) == Right(List(1L, 2L, 3L))
          )
        },
        test("Set to List conversion") {
          val result = Into[Set[Int], List[Long]].into(Set(1, 2, 3))
          assertTrue(result.isRight && result.map(_.toSet) == Right(Set(1L, 2L, 3L)))
        },
        test("empty iterable conversion") {
          assertTrue(
            Into[List[Long], Vector[Int]].into(List.empty) == Right(Vector.empty)
          )
        },
        test("iterable with failing element conversion") {
          assertTrue(
            Into[List[Long], List[Int]].into(List(1L, Long.MaxValue)).isLeft
          )
        },
        test("nested iterable conversion") {
          assertTrue(
            Into[List[List[Byte]], Vector[Vector[Int]]].into(List(List(1.toByte, 2.toByte))) ==
              Right(Vector(Vector(1, 2)))
          )
        }
      ),
      suite("Array")(
        test("Array to List conversion") {
          assertTrue(
            Into[Array[Byte], List[Int]].into(Array(1.toByte, 2.toByte, 3.toByte)) == Right(List(1, 2, 3))
          )
        },
        test("Array to Vector conversion") {
          assertTrue(
            Into[Array[Int], Vector[Long]].into(Array(10, 20, 30)) == Right(Vector(10L, 20L, 30L))
          )
        },
        test("Array to Set conversion") {
          assertTrue(
            Into[Array[Byte], Set[Int]].into(Array(1.toByte, 2.toByte, 2.toByte)) == Right(Set(1, 2))
          )
        },
        test("List to Array conversion") {
          val result = Into[List[Byte], Array[Int]].into(List(1.toByte, 2.toByte))
          assertTrue(result.isRight && result.map(_.toList) == Right(List(1, 2)))
        },
        test("Vector to Array conversion") {
          val result = Into[Vector[Int], Array[Long]].into(Vector(100, 200))
          assertTrue(result.isRight && result.map(_.toList) == Right(List(100L, 200L)))
        },
        test("Array to Array conversion") {
          val result = Into[Array[Byte], Array[Int]].into(Array(5.toByte, 10.toByte))
          assertTrue(result.isRight && result.map(_.toList) == Right(List(5, 10)))
        },
        test("empty Array conversions") {
          assertTrue(
            Into[Array[Long], List[Int]].into(Array.empty[Long]) == Right(List.empty),
            Into[List[Long], Array[Int]].into(List.empty).isRight
          )
        },
        test("Array with failing element conversion") {
          assertTrue(
            Into[Array[Long], Array[Int]].into(Array(Long.MaxValue)).isLeft,
            Into[Array[Long], List[Int]].into(Array(1L, Long.MaxValue)).isLeft,
            Into[List[Long], Array[Int]].into(List(Long.MaxValue)).isLeft
          )
        }
      )
    ),
    suite("Identity")(
      test("identity conversion returns same value") {
        assertTrue(
          Into[Int, Int].into(42) == Right(42),
          Into[String, String].into("hello") == Right("hello"),
          Into[List[Int], List[Int]].into(List(1, 2, 3)) == Right(List(1, 2, 3))
        )
      }
    ),
    suite("Error Accumulation")(
      test("collects multiple errors in collection") {
        val result = Into[List[Long], List[Int]].into(List(Long.MaxValue, Long.MinValue))
        assertTrue(result.isLeft)
      },
      test("collects errors from map keys and values") {
        val result = Into[Map[Long, Long], Map[Int, Int]].into(Map(Long.MaxValue -> Long.MaxValue))
        assertTrue(result.isLeft)
      }
    )
  )
}
