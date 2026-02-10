package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.test._

object DynamicSchemaExprSpec extends ZIOSpecDefault {

  private val dummyInput = DynamicValue.Primitive(PrimitiveValue.Unit)

  def spec = suite("DynamicSchemaExprSpec")(
    suite("Literal")(
      test("should return the stored value") {
        val dv   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expr = DynamicSchemaExpr.Literal(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("DefaultValue")(
      test("should return the stored value regardless of input") {
        val dv   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expr = DynamicSchemaExpr.DefaultValue(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("should return string DynamicValue") {
        val dv   = DynamicValue.Primitive(PrimitiveValue.String("default"))
        val expr = DynamicSchemaExpr.DefaultValue(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("should return record DynamicValue") {
        val dv   = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val expr = DynamicSchemaExpr.DefaultValue(dv)
        assertTrue(expr.eval(dummyInput) == Right(Seq(dv)))
      },
      test("inverse should be Some(this)") {
        val expr = DynamicSchemaExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        assertTrue(expr.inverse == Some(expr))
      }
    ),
    suite("Dynamic")(
      test("should read a field from a record") {
        val record = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("name"))
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
      },
      test("should read nested field") {
        val inner  = DynamicValue.Record(Chunk("city" -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))))
        val record = DynamicValue.Record(Chunk("address" -> inner))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("address").field("city"))
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("NYC")))))
      },
      test("should fail when field not found") {
        val record = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        val expr   = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("age"))
        assertTrue(expr.eval(record).isLeft)
      },
      test("inverse should be Some(this)") {
        val expr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))
        assertTrue(expr.inverse == Some(expr))
      }
    ),
    suite("Arithmetic")(
      test("should add two ints") {
        val record = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(10)),
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(5))
          )
        )
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("a")),
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("b")),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(15)))))
      },
      test("should subtract two ints") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          DynamicSchemaExpr.ArithmeticOperator.Subtract,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(7)))))
      },
      test("should multiply two doubles") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(2.5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(4.0))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply,
          DynamicSchemaExpr.NumericType.DoubleType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Double(10.0)))))
      },
      test("should divide two ints (integer division)") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(7))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Divide,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(3)))))
      },
      test("should fail on type mismatch") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not a number"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("inverse of Add should be Subtract") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        val inv = expr.inverse.get.asInstanceOf[DynamicSchemaExpr.Arithmetic]
        assertTrue(inv.operator == DynamicSchemaExpr.ArithmeticOperator.Subtract)
      }
    ),
    suite("StringConcat")(
      test("should concatenate two strings") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" World")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("Hello World")))))
      },
      test("should concatenate from record fields") {
        val record = DynamicValue.Record(
          Chunk(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("first")),
          DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" "))),
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("last"))
          )
        )
        assertTrue(expr.eval(record) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("John Doe")))))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("b")))
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("StringSplit")(
      test("should split string by single space delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("John Doe"))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("John")),
              DynamicValue.Primitive(PrimitiveValue.String("Doe"))
            )
          )
        )
      },
      test("should split by comma delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("apple,orange,banana"))),
          ","
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("apple")),
              DynamicValue.Primitive(PrimitiveValue.String("orange")),
              DynamicValue.Primitive(PrimitiveValue.String("banana"))
            )
          )
        )
      },
      test("should handle string with no delimiter present") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("NoDelimiterHere"))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("NoDelimiterHere")))))
      },
      test("should handle empty string") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(result == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("")))))
      },
      test("should handle multiple consecutive delimiters") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a  b"))),
          " "
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.String("")),
              DynamicValue.Primitive(PrimitiveValue.String("b"))
            )
          )
        )
      },
      test("should handle trailing delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a,b,"))),
          ","
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("a")),
              DynamicValue.Primitive(PrimitiveValue.String("b")),
              DynamicValue.Primitive(PrimitiveValue.String(""))
            )
          )
        )
      },
      test("should split by multi-character delimiter") {
        val expr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("one::two::three"))),
          "::"
        )
        val result = expr.eval(dummyInput)
        assertTrue(
          result == Right(
            Seq(
              DynamicValue.Primitive(PrimitiveValue.String("one")),
              DynamicValue.Primitive(PrimitiveValue.String("two")),
              DynamicValue.Primitive(PrimitiveValue.String("three"))
            )
          )
        )
      }
    ),
    suite("StringUppercase")(
      test("should convert string to uppercase") {
        val expr = DynamicSchemaExpr.StringUppercase(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("HELLO")))))
      },
      test("inverse should be StringLowercase") {
        val inner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        val expr  = DynamicSchemaExpr.StringUppercase(inner)
        assertTrue(expr.inverse == Some(DynamicSchemaExpr.StringLowercase(inner)))
      }
    ),
    suite("StringLowercase")(
      test("should convert string to lowercase") {
        val expr = DynamicSchemaExpr.StringLowercase(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("HELLO")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("hello")))))
      },
      test("inverse should be StringUppercase") {
        val inner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        val expr  = DynamicSchemaExpr.StringLowercase(inner)
        assertTrue(expr.inverse == Some(DynamicSchemaExpr.StringUppercase(inner)))
      }
    ),
    suite("StringLength")(
      test("should return length of string") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(5)))))
      },
      test("should return 0 for empty string") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(0)))))
      },
      test("inverse should be None") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        )
        assertTrue(expr.inverse.isEmpty)
      }
    ),
    suite("StringRegexMatch")(
      test("should return true for matching regex") {
        val expr = DynamicSchemaExpr.StringRegexMatch(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello123"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(".*\\d+")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("should return false for non-matching regex") {
        val expr = DynamicSchemaExpr.StringRegexMatch(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("^\\d+$")))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      }
    ),
    suite("Not")(
      test("should negate true to false") {
        val expr = DynamicSchemaExpr.Not(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      },
      test("should negate false to true") {
        val expr = DynamicSchemaExpr.Not(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("inverse of Not should be Not") {
        val inner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        val expr  = DynamicSchemaExpr.Not(inner)
        assertTrue(expr.inverse == Some(DynamicSchemaExpr.Not(inner)))
      }
    ),
    suite("Relational")(
      test("Equal should compare DynamicValues") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          DynamicSchemaExpr.RelationalOperator.Equal
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("NotEqual should detect different values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.NotEqual
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("LessThan should compare ordered values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.LessThan
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("GreaterThanOrEqual should compare ordered values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      }
    ),
    suite("Logical")(
      test("And should return true when both are true") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      },
      test("And should return false when one is false") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))
      },
      test("Or should return true when one is true") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.LogicalOperator.Or
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))))
      }
    ),
    suite("Convert")(
      test("should convert string to int") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("42"))),
          PrimitiveConverter.StringToInt
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("should fail to convert invalid string to int") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("abc"))),
          PrimitiveConverter.StringToInt
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert int to string") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          PrimitiveConverter.IntToString
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("42")))))
      },
      test("should convert string to long") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("9223372036854775807"))),
          PrimitiveConverter.StringToLong
        )
        assertTrue(
          expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))))
        )
      },
      test("should fail to convert invalid string to long") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not-a-number"))),
          PrimitiveConverter.StringToLong
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert long to string") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))),
          PrimitiveConverter.LongToString
        )
        assertTrue(
          expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("9223372036854775807"))))
        )
      },
      test("should convert string to double") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("3.14159"))),
          PrimitiveConverter.StringToDouble
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Double(3.14159)))))
      },
      test("should fail to convert invalid string to double") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not-a-double"))),
          PrimitiveConverter.StringToDouble
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert double to string") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14159))),
          PrimitiveConverter.DoubleToString
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.String("3.14159")))))
      },
      test("should convert int to long") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          PrimitiveConverter.IntToLong
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Long(42L)))))
      },
      test("should convert long to int when in range") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(42L))),
          PrimitiveConverter.LongToInt
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("should fail to convert long to int when out of range") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))),
          PrimitiveConverter.LongToInt
        )
        assertTrue(expr.eval(dummyInput).isLeft)
      },
      test("should convert double to int (truncating)") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(42.7))),
          PrimitiveConverter.DoubleToInt
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("should convert int to double") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          PrimitiveConverter.IntToDouble
        )
        assertTrue(expr.eval(dummyInput) == Right(Seq(DynamicValue.Primitive(PrimitiveValue.Double(42.0)))))
      },
      test("inverse should use reverse converter") {
        val expr = DynamicSchemaExpr.Convert(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("42"))),
          PrimitiveConverter.StringToInt
        )
        val inv = expr.inverse.get.asInstanceOf[DynamicSchemaExpr.Convert]
        assertTrue(inv.converter == PrimitiveConverter.IntToString)
      }
    ),
    suite("PrimitiveConverter reverse")(
      test("StringToInt reverse is IntToString") {
        assertTrue(PrimitiveConverter.StringToInt.reverse == PrimitiveConverter.IntToString)
      },
      test("IntToLong reverse is LongToInt") {
        assertTrue(PrimitiveConverter.IntToLong.reverse == PrimitiveConverter.LongToInt)
      }
    )
  )
}
