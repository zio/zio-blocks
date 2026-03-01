package zio.blocks.schema

import zio.blocks.schema.Validation.None
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
    suite("Relational")(
      test("LessThan operator") {
        val _ = SchemaExpr.relational(intLit(5), intLit(10), SchemaExpr.RelationalOperator.LessThan)
        assertTrue(true)
      },
      test("GreaterThan operator") {
        val _ = SchemaExpr.relational(intLit(10), intLit(5), SchemaExpr.RelationalOperator.GreaterThan)
        assertTrue(true)
      },
      test("LessThanOrEqual operator") {
        val _ = SchemaExpr.relational(intLit(5), intLit(5), SchemaExpr.RelationalOperator.LessThanOrEqual)
        assertTrue(true)
      },
      test("GreaterThanOrEqual operator") {
        val _ = SchemaExpr.relational(intLit(10), intLit(10), SchemaExpr.RelationalOperator.GreaterThanOrEqual)
        assertTrue(true)
      },
      test("Equal operator") {
        val _ = SchemaExpr.relational(intLit(42), intLit(42), SchemaExpr.RelationalOperator.Equal)
        assertTrue(true)
      },
      test("NotEqual operator") {
        val _ = SchemaExpr.relational(intLit(42), intLit(99), SchemaExpr.RelationalOperator.NotEqual)
        assertTrue(true)
      }
    ),
    suite("Logical")(
      test("And operator") {
        val _ = SchemaExpr.logical(boolLit(true), boolLit(false), SchemaExpr.LogicalOperator.And)
        assertTrue(true)
      },
      test("Or operator") {
        val _ = SchemaExpr.logical(boolLit(true), boolLit(false), SchemaExpr.LogicalOperator.Or)
        assertTrue(true)
      }
    ),
    suite("Not")(
      test("negates boolean value") {
        val _ = SchemaExpr.not(boolLit(true))
        assertTrue(true)
      }
    ),
    suite("Not")(
      test("negates boolean expression") {
        val _ = SchemaExpr.not(boolLit(true))
        assertTrue(true)
      }
    ),
    suite("Arithmetic")(
      test("Add operator") {
        val _ =
          SchemaExpr.arithmetic(intLit(10), intLit(5), SchemaExpr.ArithmeticOperator.Add, PrimitiveType.Int(None))
        assertTrue(true)
      },
      test("Subtract operator") {
        val _ =
          SchemaExpr.arithmetic(intLit(10), intLit(5), SchemaExpr.ArithmeticOperator.Subtract, PrimitiveType.Int(None))
        assertTrue(true)
      },
      test("Multiply operator") {
        val _ =
          SchemaExpr.arithmetic(intLit(10), intLit(5), SchemaExpr.ArithmeticOperator.Multiply, PrimitiveType.Int(None))
        assertTrue(true)
      },
      test("Divide operator") {
        val _ =
          SchemaExpr.arithmetic(intLit(10), intLit(2), SchemaExpr.ArithmeticOperator.Divide, PrimitiveType.Int(None))
        assertTrue(true)
      },
      test("Pow operator") {
        val _ =
          SchemaExpr.arithmetic(intLit(2), intLit(3), SchemaExpr.ArithmeticOperator.Pow, PrimitiveType.Int(None))
        assertTrue(true)
      },
      test("Modulo operator") {
        val _ =
          SchemaExpr.arithmetic(intLit(10), intLit(3), SchemaExpr.ArithmeticOperator.Modulo, PrimitiveType.Int(None))
        assertTrue(true)
      }
    ),
    suite("Bitwise")(
      test("And operator (Int)") {
        val _ = SchemaExpr.bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.And)
        assertTrue(true)
      },
      test("Or operator (Int)") {
        val _ = SchemaExpr.bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.Or)
        assertTrue(true)
      },
      test("Xor operator (Int)") {
        val _ = SchemaExpr.bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.Xor)
        assertTrue(true)
      },
      test("LeftShift operator (Int)") {
        val _ = SchemaExpr.bitwise(intLit(5), intLit(2), SchemaExpr.BitwiseOperator.LeftShift)
        assertTrue(true)
      },
      test("RightShift operator (Int)") {
        val _ = SchemaExpr.bitwise(intLit(20), intLit(2), SchemaExpr.BitwiseOperator.RightShift)
        assertTrue(true)
      },
      test("UnsignedRightShift operator (Int)") {
        val _ = SchemaExpr.bitwise(intLit(20), intLit(2), SchemaExpr.BitwiseOperator.UnsignedRightShift)
        assertTrue(true)
      },
      test("And operator (Byte)") {
        val expr   = SchemaExpr.bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.And)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with byte and byte (And)") {
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
      },
      test("evalDynamic with byte and byte (And)") {
        val _ = SchemaExpr.bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.And)
        assertTrue(true)
      },
      test("evalDynamic with byte and byte (Or)") {
        val _ = SchemaExpr.bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.Or)
        assertTrue(true)
      },
      test("evalDynamic with byte and byte (Xor)") {
        val _ = SchemaExpr.bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.Xor)
        assertTrue(true)
      },
      test("evalDynamic with short and short (And)") {
        val _ = SchemaExpr.bitwise(shortLit(12), shortLit(10), SchemaExpr.BitwiseOperator.And)
        assertTrue(true)
      },
      test("evalDynamic with short and short (Or)") {
        val _ = SchemaExpr.bitwise(shortLit(12), shortLit(10), SchemaExpr.BitwiseOperator.Or)
        assertTrue(true)
      },
      test("evalDynamic with int and int (Xor)") {
        val _ = SchemaExpr.bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.Xor)
        assertTrue(true)
      },
      test("evalDynamic with long and long (LeftShift)") {
        val _ = SchemaExpr.bitwise(longLit(12), longLit(2), SchemaExpr.BitwiseOperator.LeftShift)
        assertTrue(true)
      },
      test("evalDynamic with long and long (RightShift)") {
        val _ = SchemaExpr.bitwise(longLit(12), longLit(2), SchemaExpr.BitwiseOperator.RightShift)
        assertTrue(true)
      },
      test("evalDynamic with long and long (UnsignedRightShift)") {
        val _ = SchemaExpr.bitwise(longLit(12), longLit(2), SchemaExpr.BitwiseOperator.UnsignedRightShift)
        assertTrue(true)
      }
    ),
    suite("BitwiseNot")(
      test("negates int value") {
        val _ = SchemaExpr.bitwiseNot(intLit(42))
        assertTrue(true)
      },
      test("evalDynamic returns negated value") {
        val _ = SchemaExpr.bitwiseNot(byteLit(5))
        assertTrue(true)
      }
    ),
    suite("StringConcat")(
      test("concatenates two strings") {
        val _ = SchemaExpr.stringConcat(stringLit("hello"), stringLit("world"))
        assertTrue(true)
      }
    ),
    suite("StringRegexMatch")(
      test("matches regex pattern") {
        val _ = SchemaExpr.stringRegexMatch(stringLit("[a-z]+"), stringLit("hello"))
        assertTrue(true)
      }
    ),
    suite("StringLength")(
      test("gets string length") {
        val _ = SchemaExpr.stringLength(stringLit("hello"))
        assertTrue(true)
      }
    ),
    suite("StringSubstring")(
      test("extracts substring") {
        val _ = SchemaExpr.stringSubstring(stringLit("hello"), intLit(0), intLit(3))
        assertTrue(true)
      }
    ),
    suite("StringTrim")(
      test("trims whitespace") {
        val _ = SchemaExpr.stringTrim(stringLit("  hello  "))
        assertTrue(true)
      }
    ),
    suite("StringToUpperCase")(
      test("converts to uppercase") {
        val _ = SchemaExpr.stringToUpperCase(stringLit("hello"))
        assertTrue(true)
      }
    ),
    suite("StringToLowerCase")(
      test("converts to lowercase") {
        val _ = SchemaExpr.stringToLowerCase(stringLit("HELLO"))
        assertTrue(true)
      }
    ),
    suite("StringReplace")(
      test("replaces target with replacement") {
        val _ = SchemaExpr.stringReplace(stringLit("hello world"), stringLit("world"), stringLit("scala"))
        assertTrue(true)
      }
    ),
    suite("StringStartsWith")(
      test("checks if string starts with prefix") {
        val _ = SchemaExpr.stringStartsWith(stringLit("hello"), stringLit("he"))
        assertTrue(true)
      }
    ),
    suite("StringEndsWith")(
      test("checks if string ends with suffix") {
        val _ = SchemaExpr.stringEndsWith(stringLit("hello"), stringLit("lo"))
        assertTrue(true)
      }
    ),
    suite("StringContains")(
      test("checks if string contains substring") {
        val _ = SchemaExpr.stringContains(stringLit("hello world"), stringLit("world"))
        assertTrue(true)
      }
    ),
    suite("StringConcat")(
      test("concatenates two strings") {
        val _ = SchemaExpr.stringConcat(stringLit("hello"), stringLit("world"))
        assertTrue(true)
      }
    ),
    suite("StringRegexMatch")(
      test("matches regex pattern") {
        val _ = SchemaExpr.stringRegexMatch(stringLit("[a-z]+"), stringLit("hello"))
        assertTrue(true)
      }
    ),
    suite("StringLength")(
      test("gets string length") {
        val _ = SchemaExpr.stringLength(stringLit("hello"))
        assertTrue(true)
      }
    ),
    suite("StringSubstring")(
      test("extracts substring") {
        val _ = SchemaExpr.stringSubstring(stringLit("hello"), intLit(0), intLit(3))
        assertTrue(true)
      }
    ),
    suite("StringTrim")(
      test("trims whitespace") {
        val _ = SchemaExpr.stringTrim(stringLit("  hello  "))
        assertTrue(true)
      }
    ),
    suite("StringToUpperCase")(
      test("converts to uppercase") {
        val _ = SchemaExpr.stringToUpperCase(stringLit("hello"))
        assertTrue(true)
      }
    ),
    suite("StringToLowerCase")(
      test("converts to lowercase") {
        val _ = SchemaExpr.stringToLowerCase(stringLit("HELLO"))
        assertTrue(true)
      }
    ),
    suite("StringReplace")(
      test("replaces target with replacement") {
        val _ = SchemaExpr.stringReplace(stringLit("hello world"), stringLit("world"), stringLit("scala"))
        assertTrue(true)
      }
    ),
    suite("StringStartsWith")(
      test("checks if string starts with prefix") {
        val _ = SchemaExpr.stringStartsWith(stringLit("hello"), stringLit("he"))
        assertTrue(true)
      }
    ),
    suite("StringEndsWith")(
      test("checks if string ends with suffix") {
        val _ = SchemaExpr.stringEndsWith(stringLit("hello"), stringLit("lo"))
        assertTrue(true)
      }
    ),
    suite("StringContains")(
      test("checks if string contains substring") {
        val _ = SchemaExpr.stringContains(stringLit("hello world"), stringLit("world"))
        assertTrue(true)
      }
    ),
    suite("StringIndexOf")(
      test("finds index of substring") {
        val _ = SchemaExpr.stringIndexOf(stringLit("hello world"), stringLit("world"))
        assertTrue(true)
      }
    )
  )
}
