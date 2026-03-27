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

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationExpr.PrimitiveConversion
import zio.test._

object MigrationExprSpec extends SchemaBaseSpec {
  private def str(v: String): DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String(v))
  private def int(v: Int): DynamicValue      = DynamicValue.Primitive(PrimitiveValue.Int(v))
  private def long(v: Long): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Long(v))
  private def dbl(v: Double): DynamicValue   = DynamicValue.Primitive(PrimitiveValue.Double(v))
  private def flt(v: Float): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Float(v))
  private def bool(v: Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(v))

  def spec: Spec[Any, Any] = suite("MigrationExprSpec")(
    test("FieldAccess reads an existing path") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val expr   = MigrationExpr.FieldAccess(DynamicOptic.root.field("name"))
      assertTrue(expr(source) == Right(str("Ann")))
    },
    test("FieldAccess reports missing paths") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val expr   = MigrationExpr.FieldAccess(DynamicOptic.root.field("age"))
      assertTrue(expr(source).isLeft)
    },
    test("Convert supports all primitive success branches") {
      val cases = Vector(
        (int(1), PrimitiveConversion.IntToLong, long(1L)),
        (long(2L), PrimitiveConversion.LongToInt, int(2)),
        (int(3), PrimitiveConversion.IntToString, str("3")),
        (str("4"), PrimitiveConversion.StringToInt, int(4)),
        (long(5L), PrimitiveConversion.LongToString, str("5")),
        (str("6"), PrimitiveConversion.StringToLong, long(6L)),
        (dbl(1.5), PrimitiveConversion.DoubleToString, str("1.5")),
        (str("2.5"), PrimitiveConversion.StringToDouble, dbl(2.5)),
        (flt(3.5f), PrimitiveConversion.FloatToDouble, dbl(3.5)),
        (dbl(4.5), PrimitiveConversion.DoubleToFloat, flt(4.5f)),
        (bool(true), PrimitiveConversion.BooleanToString, str("true")),
        (str("false"), PrimitiveConversion.StringToBoolean, bool(false))
      )
      assertTrue(
        cases.forall { case (input, conversion, expected) =>
          MigrationExpr.Convert(MigrationExpr.Identity, conversion)(input) == Right(expected)
        }
      )
    },
    test("Convert reports parse and type mismatch failures") {
      val parseFailure = MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.StringToInt)(str("oops"))
      val typeFailure  = MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToLong)(str("oops"))
      assertTrue(parseFailure.isLeft && typeFailure.isLeft)
    },
    test("Concat joins string like values and fails for unsupported types") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann"), "age" -> int(10)))
      val okExpr = MigrationExpr.Concat(
        Vector(
          MigrationExpr.FieldAccess(DynamicOptic.root.field("name")),
          MigrationExpr.FieldAccess(DynamicOptic.root.field("age"))
        ),
        "-"
      )
      val badExpr = MigrationExpr.Concat(Vector(MigrationExpr.Literal(DynamicValue.Record(Chunk.empty))), "-")
      assertTrue(
        okExpr(source) == Right(str("Ann-10")) &&
          badExpr(source).isLeft
      )
    },
    test("Compose and DefaultValue cover remaining expression branches") {
      val composed = MigrationExpr.Compose(
        MigrationExpr.FieldAccess(DynamicOptic.root.field("age")),
        MigrationExpr.Convert(MigrationExpr.Identity, PrimitiveConversion.IntToString)
      )
      val source = DynamicValue.Record(Chunk("age" -> int(42)))
      assertTrue(
        composed(source) == Right(str("42")) &&
          MigrationExpr.DefaultValue(source) == Right(DynamicValue.Null)
      )
    }
  )
}
