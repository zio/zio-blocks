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

object SchemaExprSpec extends ZIOSpecDefault {

  private def intLit(n: Int): SchemaExpr[Any, Int] =
    SchemaExpr.literal[Any, Int](n)

  private def stringLit(s: String): SchemaExpr[Any, String] =
    SchemaExpr.literal[Any, String](s)

  private def boolLit(b: Boolean): SchemaExpr[Any, Boolean] =
    SchemaExpr.literal[Any, Boolean](b)

  private def byteLit(b: Byte): SchemaExpr[Any, Byte] =
    SchemaExpr.literal[Any, Byte](b)

  private def shortLit(s: Short): SchemaExpr[Any, Short] =
    SchemaExpr.literal[Any, Short](s)

  private def longLit(l: Long): SchemaExpr[Any, Long] =
    SchemaExpr.literal[Any, Long](l)

  def spec = suite("SchemaExprSpec")(
    suite("Literal")(
      test("creates int literal") {
        val lit      = intLit(42)
        val expected = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(lit.dynamic == expected)
      },
      test("creates string literal") {
        val lit      = stringLit("hello")
        val expected = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        assertTrue(lit.dynamic == expected)
      },
      test("creates boolean literal") {
        val lit      = boolLit(true)
        val expected = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        assertTrue(lit.dynamic == expected)
      },
      test("evalDynamic returns literal value") {
        val lit = intLit(99)
        assertTrue(lit.evalDynamic(()) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(99)))))
      }
    ),
    suite("PrimitiveConversion")(
      test("ByteToInt conversion") {
        val conv  = SchemaExpr.ConversionType.ByteToInt
        val input = DynamicValue.Primitive(PrimitiveValue.Byte(42.toByte))
        assertTrue(conv.convert(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("IntToLong conversion") {
        val conv  = SchemaExpr.ConversionType.IntToLong
        val input = DynamicValue.Primitive(PrimitiveValue.Int(100))
        assertTrue(conv.convert(input) == Right(DynamicValue.Primitive(PrimitiveValue.Long(100L))))
      },
      test("StringToInt conversion succeeds") {
        val conv  = SchemaExpr.ConversionType.StringToInt
        val input = DynamicValue.Primitive(PrimitiveValue.String("42"))
        assertTrue(conv.convert(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("StringToInt conversion fails on invalid input") {
        val conv  = SchemaExpr.ConversionType.StringToInt
        val input = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        assertTrue(conv.convert(input).isLeft)
      },
      test("FloatToDouble conversion") {
        val conv  = SchemaExpr.ConversionType.FloatToDouble
        val input = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
        assertTrue(conv.convert(input).isRight)
      }
    ),
    suite("Bitwise")(
      test("And operator (Byte)") {
        val expr   = SchemaExpr.bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.And)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with byte and byte (Or)") {
        val expr   = SchemaExpr.bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.Or)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with byte and byte (Xor)") {
        val expr   = SchemaExpr.bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.Xor)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with short and short (And)") {
        val expr   = SchemaExpr.bitwise(shortLit(12), shortLit(10), SchemaExpr.BitwiseOperator.And)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with short and short (Or)") {
        val expr   = SchemaExpr.bitwise(shortLit(12), shortLit(10), SchemaExpr.BitwiseOperator.Or)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with int and int (Xor)") {
        val expr   = SchemaExpr.bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.Xor)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      }
    )
  )
}
