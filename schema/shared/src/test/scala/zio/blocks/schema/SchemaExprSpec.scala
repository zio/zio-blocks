package zio.blocks.schema

import zio.blocks.schema.Validation.None
import zio.test._

object SchemaExprSpec extends ZIOSpecDefault {

  private def intLit(n: Int): SchemaExpr.Literal[Any, Int] =
    SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(n)))

  private def stringLit(s: String): SchemaExpr.Literal[Any, String] =
    SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(s)))

  private def boolLit(b: Boolean): SchemaExpr.Literal[Any, Boolean] =
    SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(b)))

  private def byteLit(b: Byte): SchemaExpr.Literal[Any, Byte] =
    SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(b)))

  private def shortLit(s: Short): SchemaExpr.Literal[Any, Short] =
    SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(s)))

  private def longLit(l: Long): SchemaExpr.Literal[Any, Long] =
    SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(l)))

  def spec = suite("SchemaExprSpec")(
    suite("Literal")(
      test("creates int literal") {
        val lit = intLit(42)
        assertTrue(lit.dynamicValue == DynamicValue.Primitive(PrimitiveValue.Int(42)))
      },
      test("creates string literal") {
        val lit = stringLit("hello")
        assertTrue(lit.dynamicValue == DynamicValue.Primitive(PrimitiveValue.String("hello")))
      },
      test("creates boolean literal") {
        val lit = boolLit(true)
        assertTrue(lit.dynamicValue == DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      },
      test("evalDynamic returns literal value") {
        val lit = intLit(99)
        assertTrue(lit.evalDynamic(()) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(99)))))
      }
    ),
    suite("PrimitiveConversion")(
      test("ByteToInt conversion") {
        val conv  = SchemaExpr.PrimitiveConversion[Any](SchemaExpr.ConversionType.ByteToInt)
        val input = DynamicValue.Primitive(PrimitiveValue.Byte(42.toByte))
        assertTrue(conv.convert(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("IntToLong conversion") {
        val conv  = SchemaExpr.PrimitiveConversion[Any](SchemaExpr.ConversionType.IntToLong)
        val input = DynamicValue.Primitive(PrimitiveValue.Int(100))
        assertTrue(conv.convert(input) == Right(DynamicValue.Primitive(PrimitiveValue.Long(100L))))
      },
      test("StringToInt conversion succeeds") {
        val conv  = SchemaExpr.PrimitiveConversion[Any](SchemaExpr.ConversionType.StringToInt)
        val input = DynamicValue.Primitive(PrimitiveValue.String("42"))
        assertTrue(conv.convert(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("StringToInt conversion fails on invalid input") {
        val conv  = SchemaExpr.PrimitiveConversion[Any](SchemaExpr.ConversionType.StringToInt)
        val input = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        assertTrue(conv.convert(input).isLeft)
      },
      test("FloatToDouble conversion") {
        val conv  = SchemaExpr.PrimitiveConversion[Any](SchemaExpr.ConversionType.FloatToDouble)
        val input = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
        assertTrue(conv.convert(input).isRight)
      }
    ),
    suite("Relational")(
      test("LessThan operator") {
        val expr = SchemaExpr.Relational(intLit(5), intLit(10), SchemaExpr.RelationalOperator.LessThan)
        assertTrue(expr.operator == SchemaExpr.RelationalOperator.LessThan)
      },
      test("GreaterThan operator") {
        val expr = SchemaExpr.Relational(intLit(10), intLit(5), SchemaExpr.RelationalOperator.GreaterThan)
        assertTrue(expr.operator == SchemaExpr.RelationalOperator.GreaterThan)
      },
      test("LessThanOrEqual operator") {
        val expr = SchemaExpr.Relational(intLit(5), intLit(5), SchemaExpr.RelationalOperator.LessThanOrEqual)
        assertTrue(expr.operator == SchemaExpr.RelationalOperator.LessThanOrEqual)
      },
      test("GreaterThanOrEqual operator") {
        val expr = SchemaExpr.Relational(intLit(10), intLit(10), SchemaExpr.RelationalOperator.GreaterThanOrEqual)
        assertTrue(expr.operator == SchemaExpr.RelationalOperator.GreaterThanOrEqual)
      },
      test("Equal operator") {
        val expr = SchemaExpr.Relational(intLit(42), intLit(42), SchemaExpr.RelationalOperator.Equal)
        assertTrue(expr.operator == SchemaExpr.RelationalOperator.Equal)
      },
      test("NotEqual operator") {
        val expr = SchemaExpr.Relational(intLit(42), intLit(99), SchemaExpr.RelationalOperator.NotEqual)
        assertTrue(expr.operator == SchemaExpr.RelationalOperator.NotEqual)
      }
    ),
    suite("Logical")(
      test("And operator") {
        val expr = SchemaExpr.Logical(boolLit(true), boolLit(false), SchemaExpr.LogicalOperator.And)
        assertTrue(expr.operator == SchemaExpr.LogicalOperator.And)
      },
      test("Or operator") {
        val expr = SchemaExpr.Logical(boolLit(true), boolLit(false), SchemaExpr.LogicalOperator.Or)
        assertTrue(expr.operator == SchemaExpr.LogicalOperator.Or)
      }
    ),
    suite("Not")(
      test("negates boolean expression") {
        val expr = SchemaExpr.Not(boolLit(true))
        assertTrue(expr.expr == boolLit(true))
      }
    ),
    suite("Arithmetic")(
      test("Add operator") {
        val expr =
          SchemaExpr.Arithmetic(intLit(10), intLit(5), SchemaExpr.ArithmeticOperator.Add, PrimitiveType.Int(None))
        assertTrue(expr.operator == SchemaExpr.ArithmeticOperator.Add)
      },
      test("Subtract operator") {
        val expr =
          SchemaExpr.Arithmetic(intLit(10), intLit(5), SchemaExpr.ArithmeticOperator.Subtract, PrimitiveType.Int(None))
        assertTrue(expr.operator == SchemaExpr.ArithmeticOperator.Subtract)
      },
      test("Multiply operator") {
        val expr =
          SchemaExpr.Arithmetic(intLit(10), intLit(5), SchemaExpr.ArithmeticOperator.Multiply, PrimitiveType.Int(None))
        assertTrue(expr.operator == SchemaExpr.ArithmeticOperator.Multiply)
      },
      test("Divide operator") {
        val expr =
          SchemaExpr.Arithmetic(intLit(10), intLit(2), SchemaExpr.ArithmeticOperator.Divide, PrimitiveType.Int(None))
        assertTrue(expr.operator == SchemaExpr.ArithmeticOperator.Divide)
      },
      test("Pow operator") {
        val expr =
          SchemaExpr.Arithmetic(intLit(2), intLit(3), SchemaExpr.ArithmeticOperator.Pow, PrimitiveType.Int(None))
        assertTrue(expr.operator == SchemaExpr.ArithmeticOperator.Pow)
      },
      test("Modulo operator") {
        val expr =
          SchemaExpr.Arithmetic(intLit(10), intLit(3), SchemaExpr.ArithmeticOperator.Modulo, PrimitiveType.Int(None))
        assertTrue(expr.operator == SchemaExpr.ArithmeticOperator.Modulo)
      }
    ),
    suite("Bitwise")(
      test("And operator") {
        val expr = SchemaExpr.Bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.And)
        assertTrue(expr.operator == SchemaExpr.BitwiseOperator.And)
      },
      test("Or operator") {
        val expr = SchemaExpr.Bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.Or)
        assertTrue(expr.operator == SchemaExpr.BitwiseOperator.Or)
      },
      test("Xor operator") {
        val expr = SchemaExpr.Bitwise(intLit(12), intLit(10), SchemaExpr.BitwiseOperator.Xor)
        assertTrue(expr.operator == SchemaExpr.BitwiseOperator.Xor)
      },
      test("LeftShift operator") {
        val expr = SchemaExpr.Bitwise(intLit(5), intLit(2), SchemaExpr.BitwiseOperator.LeftShift)
        assertTrue(expr.operator == SchemaExpr.BitwiseOperator.LeftShift)
      },
      test("RightShift operator") {
        val expr = SchemaExpr.Bitwise(intLit(20), intLit(2), SchemaExpr.BitwiseOperator.RightShift)
        assertTrue(expr.operator == SchemaExpr.BitwiseOperator.RightShift)
      },
      test("UnsignedRightShift operator") {
        val expr = SchemaExpr.Bitwise(intLit(20), intLit(2), SchemaExpr.BitwiseOperator.UnsignedRightShift)
        assertTrue(expr.operator == SchemaExpr.BitwiseOperator.UnsignedRightShift)
      },
      test("evalDynamic with byte values") {
        val expr   = SchemaExpr.Bitwise(byteLit(12), byteLit(10), SchemaExpr.BitwiseOperator.And)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with byte and short (And)") {
        val expr   = SchemaExpr.Bitwise(byteLit(12), shortLit(10), SchemaExpr.BitwiseOperator.And)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with byte and int (Or)") {
        val expr   = SchemaExpr.Bitwise(byteLit(12), intLit(10), SchemaExpr.BitwiseOperator.Or)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with byte and long (Xor)") {
        val expr   = SchemaExpr.Bitwise(byteLit(12), longLit(10), SchemaExpr.BitwiseOperator.Xor)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with short and int (And)") {
        val expr   = SchemaExpr.Bitwise(shortLit(12), intLit(10), SchemaExpr.BitwiseOperator.And)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with short and long (Or)") {
        val expr   = SchemaExpr.Bitwise(shortLit(12), longLit(10), SchemaExpr.BitwiseOperator.Or)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with int and long (Xor)") {
        val expr   = SchemaExpr.Bitwise(intLit(12), longLit(10), SchemaExpr.BitwiseOperator.Xor)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with long and byte (LeftShift)") {
        val expr   = SchemaExpr.Bitwise(longLit(12), byteLit(2), SchemaExpr.BitwiseOperator.LeftShift)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with long and short (RightShift)") {
        val expr   = SchemaExpr.Bitwise(longLit(12), shortLit(2), SchemaExpr.BitwiseOperator.RightShift)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      },
      test("evalDynamic with long and int (UnsignedRightShift)") {
        val expr   = SchemaExpr.Bitwise(longLit(12), intLit(2), SchemaExpr.BitwiseOperator.UnsignedRightShift)
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      }
    ),
    suite("BitwiseNot")(
      test("negates int value") {
        val expr = SchemaExpr.BitwiseNot(intLit(42))
        assertTrue(expr.expr == intLit(42))
      },
      test("evalDynamic returns negated value") {
        val expr   = SchemaExpr.BitwiseNot(byteLit(5))
        val result = expr.evalDynamic(())
        assertTrue(result.isRight && result.exists(seq => seq.nonEmpty))
      }
    ),
    suite("StringConcat")(
      test("concatenates two strings") {
        val expr = SchemaExpr.StringConcat(stringLit("hello"), stringLit("world"))
        assertTrue(expr.left == stringLit("hello") && expr.right == stringLit("world"))
      }
    ),
    suite("StringRegexMatch")(
      test("matches regex pattern") {
        val expr = SchemaExpr.StringRegexMatch(stringLit("[a-z]+"), stringLit("hello"))
        assertTrue(expr.regex == stringLit("[a-z]+") && expr.string == stringLit("hello"))
      }
    ),
    suite("StringLength")(
      test("gets string length") {
        val expr = SchemaExpr.StringLength(stringLit("hello"))
        assertTrue(expr.string == stringLit("hello"))
      }
    ),
    suite("StringSubstring")(
      test("extracts substring") {
        val expr = SchemaExpr.StringSubstring(stringLit("hello"), intLit(0), intLit(3))
        assertTrue(expr.string == stringLit("hello") && expr.start == intLit(0) && expr.end == intLit(3))
      }
    ),
    suite("StringTrim")(
      test("trims whitespace") {
        val expr = SchemaExpr.StringTrim(stringLit("  hello  "))
        assertTrue(expr.string == stringLit("  hello  "))
      }
    ),
    suite("StringToUpperCase")(
      test("converts to uppercase") {
        val expr = SchemaExpr.StringToUpperCase(stringLit("hello"))
        assertTrue(expr.string == stringLit("hello"))
      }
    ),
    suite("StringToLowerCase")(
      test("converts to lowercase") {
        val expr = SchemaExpr.StringToLowerCase(stringLit("HELLO"))
        assertTrue(expr.string == stringLit("HELLO"))
      }
    ),
    suite("StringReplace")(
      test("replaces target with replacement") {
        val expr = SchemaExpr.StringReplace(stringLit("hello world"), stringLit("world"), stringLit("scala"))
        assertTrue(
          expr.string == stringLit("hello world") &&
            expr.target == stringLit("world") &&
            expr.replacement == stringLit("scala")
        )
      }
    ),
    suite("StringStartsWith")(
      test("checks if string starts with prefix") {
        val expr = SchemaExpr.StringStartsWith(stringLit("hello"), stringLit("he"))
        assertTrue(expr.string == stringLit("hello") && expr.prefix == stringLit("he"))
      }
    ),
    suite("StringEndsWith")(
      test("checks if string ends with suffix") {
        val expr = SchemaExpr.StringEndsWith(stringLit("hello"), stringLit("lo"))
        assertTrue(expr.string == stringLit("hello") && expr.suffix == stringLit("lo"))
      }
    ),
    suite("StringContains")(
      test("checks if string contains substring") {
        val expr = SchemaExpr.StringContains(stringLit("hello world"), stringLit("world"))
        assertTrue(expr.string == stringLit("hello world") && expr.substring == stringLit("world"))
      }
    ),
    suite("StringIndexOf")(
      test("finds index of substring") {
        val expr = SchemaExpr.StringIndexOf(stringLit("hello world"), stringLit("world"))
        assertTrue(expr.string == stringLit("hello world") && expr.substring == stringLit("world"))
      }
    )
  )
}
