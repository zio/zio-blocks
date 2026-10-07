/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.test._

/**
 * Boundary and allocation-behavior tests: exact float/double boundary guards,
 * fused collection converters, closure-free option conversion, `SchemaMatch`
 * indeterminacy for nominal patterns, and `RebindException` stack-trace
 * suppression.
 *
 * Allocation notes (not benchmarks — behavioral tests only): the collection
 * converters fuse the former 2-pass `toList.map` + `sequence` traversal into a
 * single `while` loop over the iterator writing straight into the result
 * builder, so the happy path allocates no intermediate `List[Either]` and no
 * per-call closures; `optionInto` avoids the `Function1` closure; migration
 * avoids per-element `DynamicOptic` copies on the happy path.
 */
object SchemaConversionBoundarySpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaConversionBoundarySpec")(
    suite("float/double boundary guards")(
      test("floatToInt rejects 2^31f") {
        assertTrue(Into.floatToInt.into(java.lang.Float.intBitsToFloat(0x4f000000)).isLeft)
      },
      test("floatToInt accepts in-range and boundary values") {
        assertTrue(
          Into.floatToInt.into(1.0f) == Right(1) &&
            Into.floatToInt.into(2147483520.0f) == Right(2147483520) &&
            Into.floatToInt.into(-2147483648.0f) == Right(Int.MinValue) &&
            Into.floatToInt.into(0.5f).isLeft
        )
      },
      test("floatToLong rejects 2^63f") {
        assertTrue(Into.floatToLong.into(9223372036854775808.0f).isLeft)
      },
      test("floatToLong accepts Long.MinValue float") {
        assertTrue(Into.floatToLong.into(-9223372036854775808.0f) == Right(Long.MinValue))
      },
      test("doubleToInt rejects 2^63-adjacent Double Int boundary") {
        assertTrue(Into.doubleToInt.into(2147483648.0).isLeft)
      },
      test("doubleToInt accepts Double Int boundaries") {
        assertTrue(
          Into.doubleToInt.into(2147483647.0) == Right(Int.MaxValue) &&
            Into.doubleToInt.into(-2147483648.0) == Right(Int.MinValue)
        )
      },
      test("doubleToLong rejects 2^63") {
        assertTrue(Into.doubleToLong.into(9223372036854775808.0).isLeft)
      },
      test("doubleToLong accepts Long.MinValue double") {
        assertTrue(Into.doubleToLong.into(-9223372036854775808.0) == Right(Long.MinValue))
      },
      test("SchemaExpr conversions reject boundary values identically") {
        import SchemaExpr.ConversionType._
        assertTrue(
          FloatToInt
            .convert(DynamicValue.Primitive(PrimitiveValue.Float(java.lang.Float.intBitsToFloat(0x4f000000))))
            .isLeft &&
            FloatToLong.convert(DynamicValue.Primitive(PrimitiveValue.Float(9223372036854775808.0f))).isLeft &&
            DoubleToInt.convert(DynamicValue.Primitive(PrimitiveValue.Double(2147483648.0))).isLeft &&
            DoubleToLong.convert(DynamicValue.Primitive(PrimitiveValue.Double(9223372036854775808.0))).isLeft &&
            FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))).isRight &&
            DoubleToLong.convert(DynamicValue.Primitive(PrimitiveValue.Double(1.0))).isRight
        )
      }
    ),
    suite("non-finite float/double rejection")(
      test("floatToInt rejects NaN and both infinities") {
        assertTrue(
          Into.floatToInt.into(Float.NaN).isLeft &&
            Into.floatToInt.into(Float.PositiveInfinity).isLeft &&
            Into.floatToInt.into(Float.NegativeInfinity).isLeft
        )
      },
      test("floatToLong rejects NaN and both infinities") {
        assertTrue(
          Into.floatToLong.into(Float.NaN).isLeft &&
            Into.floatToLong.into(Float.PositiveInfinity).isLeft &&
            Into.floatToLong.into(Float.NegativeInfinity).isLeft
        )
      },
      test("doubleToInt rejects NaN and both infinities") {
        assertTrue(
          Into.doubleToInt.into(Double.NaN).isLeft &&
            Into.doubleToInt.into(Double.PositiveInfinity).isLeft &&
            Into.doubleToInt.into(Double.NegativeInfinity).isLeft
        )
      },
      test("doubleToLong rejects NaN and both infinities") {
        assertTrue(
          Into.doubleToLong.into(Double.NaN).isLeft &&
            Into.doubleToLong.into(Double.PositiveInfinity).isLeft &&
            Into.doubleToLong.into(Double.NegativeInfinity).isLeft
        )
      },
      test("SchemaExpr conversions reject non-finite values identically") {
        import SchemaExpr.ConversionType._
        assertTrue(
          FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(Float.NaN))).isLeft &&
            FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(Float.PositiveInfinity))).isLeft &&
            FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(Float.NegativeInfinity))).isLeft &&
            FloatToLong.convert(DynamicValue.Primitive(PrimitiveValue.Float(Float.NaN))).isLeft &&
            DoubleToInt.convert(DynamicValue.Primitive(PrimitiveValue.Double(Double.NaN))).isLeft &&
            DoubleToInt.convert(DynamicValue.Primitive(PrimitiveValue.Double(Double.PositiveInfinity))).isLeft &&
            DoubleToInt.convert(DynamicValue.Primitive(PrimitiveValue.Double(Double.NegativeInfinity))).isLeft &&
            DoubleToLong.convert(DynamicValue.Primitive(PrimitiveValue.Double(Double.NaN))).isLeft
        )
      }
    ),
    suite("decimal-literal rounding at integer boundaries")(
      test("floatToInt rejects 2147483647.0f because the literal denotes 2^31f") {
        // 2147483647 is not representable in Float32: the literal rounds to 2^31.
        assertTrue(
          2147483647.0f == java.lang.Float.intBitsToFloat(0x4f000000),
          Into.floatToInt.into(2147483647.0f).isLeft
        )
      },
      test("floatToInt accepts 2147483520.0f, the largest exactly-convertible Float below 2^31") {
        assertTrue(Into.floatToInt.into(2147483520.0f) == Right(2147483520))
      },
      test("doubleToLong rejects 9223372036854775807.0 because the literal denotes 2^63") {
        // 9223372036854775807 (Long.MaxValue) is not representable in Float64:
        // the literal rounds up to 2^63.
        assertTrue(
          9223372036854775807.0 == 9223372036854775808.0,
          Into.doubleToLong.into(9223372036854775807.0).isLeft
        )
      },
      test("doubleToLong accepts 9223372036854774784.0, the largest exactly-convertible Double below 2^63") {
        // 2^63 - 2^10: the Double spacing at this magnitude is 1024.
        assertTrue(Into.doubleToLong.into(9223372036854774784.0) == Right(9223372036854774784L))
      },
      test("floatToLong rejects 9223372036854775807.0f because the literal denotes 2^63f") {
        assertTrue(
          9223372036854775807.0f == 9223372036854775808.0f,
          Into.floatToLong.into(9223372036854775807.0f).isLeft
        )
      },
      test("floatToLong accepts 9223371487098961920.0f, the largest exactly-convertible Float below 2^63") {
        // 2^63 - 2^39: the Float spacing at this magnitude is 549755813888.
        assertTrue(Into.floatToLong.into(9223371487098961920.0f) == Right(9223371487098961920L))
      },
      test("SchemaExpr conversions reject rounded decimal literals identically") {
        import SchemaExpr.ConversionType._
        assertTrue(
          FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(2147483647.0f))).isLeft &&
            DoubleToLong.convert(DynamicValue.Primitive(PrimitiveValue.Double(9223372036854775807.0))).isLeft &&
            FloatToLong.convert(DynamicValue.Primitive(PrimitiveValue.Float(9223372036854775807.0f))).isLeft &&
            FloatToInt.convert(DynamicValue.Primitive(PrimitiveValue.Float(2147483520.0f))).isRight &&
            DoubleToLong.convert(DynamicValue.Primitive(PrimitiveValue.Double(9223372036854774784.0))).isRight
        )
      }
    ),
    suite("fused collection converters")(
      test("iterableInto converts and accumulates errors") {
        val ok     = implicitly[Into[List[Int], List[Long]]].into(List(1, 2, 3))
        val failed = implicitly[Into[List[Int], List[Byte]]].into(List(1, 128))
        assertTrue(ok == Right(List(1L, 2L, 3L)) && failed.isLeft)
      },
      test("iterableInto accumulates one error per failing element in element order") {
        val result = implicitly[Into[List[Float], List[Int]]].into(List(1.5f, 2.5f, 1.0f))
        val errors = result.swap.getOrElse(SchemaError("unreachable")).errors.toList
        assertTrue(
          result.isLeft,
          errors.length == 2,
          errors.map(_.message) == List(
            "Value 1.5 cannot be precisely converted to Int",
            "Value 2.5 cannot be precisely converted to Int"
          ),
          errors.forall(_.source == DynamicOptic.root)
        )
      },
      test("mapInto converts entries") {
        val ok = implicitly[Into[Map[String, Int], Map[String, Long]]].into(Map("a" -> 1))
        assertTrue(ok == Right(Map("a" -> 1L)))
      },
      test("mapInto accumulates one error per failing entry in iteration order") {
        val result =
          implicitly[Into[Map[String, Float], Map[String, Int]]].into(Map("a" -> 1.5f, "b" -> 2.5f, "c" -> 1.0f))
        val errors = result.swap.getOrElse(SchemaError("unreachable")).errors.toList
        assertTrue(
          result.isLeft,
          errors.length == 2,
          errors.map(_.message) == List(
            "Value 1.5 cannot be precisely converted to Int",
            "Value 2.5 cannot be precisely converted to Int"
          ),
          errors.forall(_.source == DynamicOptic.root)
        )
      },
      test("arrayToArray converts elements") {
        val ok = implicitly[Into[Array[Int], Array[Long]]].into(Array(1, 2))
        assertTrue(ok.toOption.exists(_.toList == List(1L, 2L)))
      },
      test("arrayToIterable converts elements") {
        val ok = implicitly[Into[Array[Int], List[Long]]].into(Array(1, 2))
        assertTrue(ok == Right(List(1L, 2L)))
      }
    ),
    suite("option conversion")(
      test("optionInto maps Some without behavior change") {
        val some = implicitly[Into[Option[Int], Option[Long]]].into(Some(1))
        val none = implicitly[Into[Option[Int], Option[Long]]].into(None)
        val bad  = implicitly[Into[Option[Float], Option[Int]]].into(Some(0.5f))
        assertTrue(some == Right(Some(1L)) && none == Right(None) && bad.isLeft)
      }
    ),
    suite("SchemaMatch indeterminacy")(
      test("Nominal never matches") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(!SchemaMatch.matches(SchemaRepr.Nominal("User"), value))
      },
      test("nested Nominal never matches") {
        val pattern = SchemaRepr.Record(IndexedSeq("name" -> SchemaRepr.Nominal("Name")))
        val value   = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("a")))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("decidable patterns match") {
        assertTrue(
          SchemaMatch.matches(SchemaRepr.Wildcard, DynamicValue.Null) &&
            !SchemaMatch.matches(
              SchemaRepr.Primitive("int"),
              DynamicValue.Primitive(PrimitiveValue.String("x"))
            )
        )
      }
    ),
    suite("RebindException stack-trace suppression")(
      test("RebindException suppresses stack traces") {
        val ex = new RebindException(DynamicOptic.root, Schema[Int].reflect.typeId, "Record")
        assertTrue(ex.isInstanceOf[scala.util.control.NoStackTrace])
      }
    )
  )
}
