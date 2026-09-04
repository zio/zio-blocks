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
 * PR1 regression tests: exact float/double boundary guards (B1), fused
 * collection converters (C1), closure-free option conversion (C2),
 * `SchemaMatch.matchesOption` (P3), and `RebindException` stack-trace
 * suppression (P3).
 *
 * Allocation notes (not benchmarks — behavioral tests only): C1 fuses the
 * former 2-pass `toList.map` + `sequence` traversal into a single `while` loop
 * over the iterator writing straight into the result builder, so the happy path
 * allocates no intermediate `List[Either]` and no per-call closures; C2 avoids
 * the `Function1` closure in `optionInto`; C4 (migration) avoids per-element
 * `DynamicOptic` copies on the happy path.
 */
object Pr1RegressionSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Pr1RegressionSpec")(
    suite("B1 float/double boundary guards")(
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
    suite("C1 fused collection converters")(
      test("iterableInto converts and accumulates errors") {
        val ok     = implicitly[Into[List[Int], List[Long]]].into(List(1, 2, 3))
        val failed = implicitly[Into[List[Int], List[Byte]]].into(List(1, 128))
        assertTrue(ok == Right(List(1L, 2L, 3L)) && failed.isLeft)
      },
      test("mapInto converts entries") {
        val ok = implicitly[Into[Map[String, Int], Map[String, Long]]].into(Map("a" -> 1))
        assertTrue(ok == Right(Map("a" -> 1L)))
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
    suite("C2 option conversion")(
      test("optionInto maps Some without behavior change") {
        val some = implicitly[Into[Option[Int], Option[Long]]].into(Some(1))
        val none = implicitly[Into[Option[Int], Option[Long]]].into(None)
        val bad  = implicitly[Into[Option[Float], Option[Int]]].into(Some(0.5f))
        assertTrue(some == Right(Some(1L)) && none == Right(None) && bad.isLeft)
      }
    ),
    suite("P3 SchemaMatch.matchesOption")(
      test("Nominal is indeterminate but matches stays false") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(
          SchemaMatch.matchesOption(SchemaRepr.Nominal("User"), value) == None &&
            !SchemaMatch.matches(SchemaRepr.Nominal("User"), value)
        )
      },
      test("nested Nominal propagates indeterminacy") {
        val pattern = SchemaRepr.Record(IndexedSeq("name" -> SchemaRepr.Nominal("Name")))
        val value   = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("a")))
        assertTrue(SchemaMatch.matchesOption(pattern, value) == None && !SchemaMatch.matches(pattern, value))
      },
      test("decidable patterns return Some") {
        assertTrue(
          SchemaMatch.matchesOption(SchemaRepr.Wildcard, DynamicValue.Null) == Some(true) &&
            SchemaMatch.matchesOption(
              SchemaRepr.Primitive("int"),
              DynamicValue.Primitive(PrimitiveValue.String("x"))
            ) == Some(false)
        )
      }
    ),
    suite("P3 RebindException")(
      test("RebindException suppresses stack traces") {
        val ex = new RebindException(DynamicOptic.root, Schema[Int].reflect.typeId, "Record")
        assertTrue(ex.isInstanceOf[scala.util.control.NoStackTrace])
      }
    )
  )
}
