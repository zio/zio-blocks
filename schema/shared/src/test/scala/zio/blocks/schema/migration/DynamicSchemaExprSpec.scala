package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaBaseSpec}
import zio.test.Assertion._
import zio.test._

object DynamicSchemaExprSpec extends SchemaBaseSpec {

  private val intValue    = DynamicValue.Primitive(PrimitiveValue.Int(42))
  private val strValue    = DynamicValue.Primitive(PrimitiveValue.String("hello"))
  private val boolValue   = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
  private val longValue   = DynamicValue.Primitive(PrimitiveValue.Long(100L))
  private val floatValue  = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
  private val doubleValue = DynamicValue.Primitive(PrimitiveValue.Double(2.718))

  private val record = DynamicValue.Record(
    Chunk(
      "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
      "age"   -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
      "score" -> DynamicValue.Primitive(PrimitiveValue.Double(95.5))
    )
  )

  private val nestedRecord = DynamicValue.Record(
    Chunk(
      "user"   -> record,
      "active" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true))
    )
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicSchemaExprSpec")(
    literalSuite,
    selectSuite,
    primitiveConversionSuite,
    stringConcatSuite,
    stringLengthSuite,
    arithmeticSuite,
    relationalSuite,
    logicalSuite,
    notSuite
  )

  private val literalSuite = suite("Literal")(
    test("always returns its value regardless of input") {
      val expr = DynamicSchemaExpr.Literal(intValue)
      assert(expr.eval(strValue))(isRight(equalTo(Chunk.single(intValue): Seq[DynamicValue])))
    },
    test("returns the same value for DynamicValue.Null input") {
      val expr = DynamicSchemaExpr.Literal(strValue)
      assert(expr.eval(DynamicValue.Null))(isRight(equalTo(Chunk.single(strValue): Seq[DynamicValue])))
    },
    test("returns record literal") {
      val expr = DynamicSchemaExpr.Literal(record)
      assert(expr.eval(DynamicValue.Null))(isRight(equalTo(Chunk.single(record): Seq[DynamicValue])))
    }
  )

  private val selectSuite = suite("Select")(
    test("extracts a top-level field from a record") {
      val expr = DynamicSchemaExpr.Select(DynamicOptic.root.field("name"))
      assert(expr.eval(record))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.String("Alice"))): Seq[DynamicValue]))
      )
    },
    test("extracts a nested field") {
      val expr = DynamicSchemaExpr.Select(DynamicOptic.root.field("user").field("age"))
      assert(expr.eval(nestedRecord))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(30))): Seq[DynamicValue]))
      )
    },
    test("fails on missing field") {
      val expr = DynamicSchemaExpr.Select(DynamicOptic.root.field("missing"))
      assert(expr.eval(record))(isLeft)
    },
    test("fails on non-record for field path") {
      val expr = DynamicSchemaExpr.Select(DynamicOptic.root.field("x"))
      assert(expr.eval(intValue))(isLeft)
    },
    test("extracts from variant case") {
      val variant = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(99)))
      val expr    = DynamicSchemaExpr.Select(DynamicOptic.root.caseOf("Some"))
      assert(expr.eval(variant))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(99))): Seq[DynamicValue]))
      )
    },
    test("extracts element by index from sequence") {
      val seq  = DynamicValue.Sequence(Chunk(intValue, strValue, boolValue))
      val expr = DynamicSchemaExpr.Select(DynamicOptic.root.at(1))
      assert(expr.eval(seq))(
        isRight(equalTo(Chunk.single(strValue): Seq[DynamicValue]))
      )
    }
  )

  private val primitiveConversionSuite = suite("PrimitiveConversion")(
    test("IntToLong converts Int to Long") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToLong)
      assert(expr.eval(intValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Long(42L))): Seq[DynamicValue]))
      )
    },
    test("IntToString converts Int to String") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToString)
      assert(expr.eval(intValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.String("42"))): Seq[DynamicValue]))
      )
    },
    test("IntToFloat converts Int to Float") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToFloat)
      assert(expr.eval(intValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Float(42.0f))): Seq[DynamicValue]))
      )
    },
    test("IntToDouble converts Int to Double") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToDouble)
      assert(expr.eval(intValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Double(42.0))): Seq[DynamicValue]))
      )
    },
    test("LongToDouble converts Long to Double") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.LongToDouble)
      assert(expr.eval(longValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Double(100.0))): Seq[DynamicValue]))
      )
    },
    test("LongToFloat converts Long to Float") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.LongToFloat)
      assert(expr.eval(longValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Float(100.0f))): Seq[DynamicValue]))
      )
    },
    test("FloatToDouble converts Float to Double") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.FloatToDouble)
      assert(expr.eval(floatValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Double(3.14f.toDouble))): Seq[DynamicValue]))
      )
    },
    test("StringToInt converts valid string") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.StringToInt)
      assert(expr.eval(DynamicValue.Primitive(PrimitiveValue.String("123"))))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(123))): Seq[DynamicValue]))
      )
    },
    test("StringToInt fails on non-numeric string") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.StringToInt)
      assert(expr.eval(DynamicValue.Primitive(PrimitiveValue.String("abc"))))(isLeft)
    },
    test("StringToLong converts valid string") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.StringToLong)
      assert(expr.eval(DynamicValue.Primitive(PrimitiveValue.String("9876543210"))))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Long(9876543210L))): Seq[DynamicValue]))
      )
    },
    test("StringToDouble converts valid string") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.StringToDouble)
      assert(expr.eval(DynamicValue.Primitive(PrimitiveValue.String("3.14"))))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Double(3.14))): Seq[DynamicValue]))
      )
    },
    test("BooleanToString converts boolean") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.BooleanToString)
      assert(expr.eval(boolValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.String("true"))): Seq[DynamicValue]))
      )
    },
    test("StringToBoolean converts 'true'") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.StringToBoolean)
      assert(expr.eval(DynamicValue.Primitive(PrimitiveValue.String("true"))))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("StringToBoolean converts 'false'") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.StringToBoolean)
      assert(expr.eval(DynamicValue.Primitive(PrimitiveValue.String("false"))))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(false))): Seq[DynamicValue]))
      )
    },
    test("StringToBoolean fails on invalid string") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.StringToBoolean)
      assert(expr.eval(DynamicValue.Primitive(PrimitiveValue.String("yes"))))(isLeft)
    },
    test("LongToString converts Long to String") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.LongToString)
      assert(expr.eval(longValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.String("100"))): Seq[DynamicValue]))
      )
    },
    test("DoubleToString converts Double to String") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.DoubleToString)
      assert(expr.eval(doubleValue))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.String("2.718"))): Seq[DynamicValue]))
      )
    },
    test("ByteToShort converts Byte to Short") {
      val expr    = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.ByteToShort)
      val byteVal = DynamicValue.Primitive(PrimitiveValue.Byte(10.toByte))
      assert(expr.eval(byteVal))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Short(10.toShort))): Seq[DynamicValue]))
      )
    },
    test("ByteToInt converts Byte to Int") {
      val expr    = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.ByteToInt)
      val byteVal = DynamicValue.Primitive(PrimitiveValue.Byte(10.toByte))
      assert(expr.eval(byteVal))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(10))): Seq[DynamicValue]))
      )
    },
    test("ByteToLong converts Byte to Long") {
      val expr    = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.ByteToLong)
      val byteVal = DynamicValue.Primitive(PrimitiveValue.Byte(10.toByte))
      assert(expr.eval(byteVal))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Long(10L))): Seq[DynamicValue]))
      )
    },
    test("ShortToInt converts Short to Int") {
      val expr     = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.ShortToInt)
      val shortVal = DynamicValue.Primitive(PrimitiveValue.Short(200.toShort))
      assert(expr.eval(shortVal))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(200))): Seq[DynamicValue]))
      )
    },
    test("ShortToLong converts Short to Long") {
      val expr     = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.ShortToLong)
      val shortVal = DynamicValue.Primitive(PrimitiveValue.Short(200.toShort))
      assert(expr.eval(shortVal))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Long(200L))): Seq[DynamicValue]))
      )
    },
    test("fails when applying IntToLong to a String") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToLong)
      assert(expr.eval(strValue))(isLeft)
    },
    test("fails on non-Primitive input") {
      val expr = DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToLong)
      assert(expr.eval(record))(isLeft)
    }
  )

  private val stringConcatSuite = suite("StringConcat")(
    test("concatenates two literal strings") {
      val expr = DynamicSchemaExpr.StringConcat(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello "))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("world")))
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.String("hello world"))): Seq[DynamicValue]))
      )
    },
    test("concatenates selected field with literal") {
      val expr = DynamicSchemaExpr.StringConcat(
        DynamicSchemaExpr.Select(DynamicOptic.root.field("name")),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("!")))
      )
      assert(expr.eval(record))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.String("Alice!"))): Seq[DynamicValue]))
      )
    },
    test("fails when left is not a string") {
      val expr = DynamicSchemaExpr.StringConcat(
        DynamicSchemaExpr.Literal(intValue),
        DynamicSchemaExpr.Literal(strValue)
      )
      assert(expr.eval(DynamicValue.Null))(isLeft)
    }
  )

  private val stringLengthSuite = suite("StringLength")(
    test("returns length of a string") {
      val expr = DynamicSchemaExpr.StringLength(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(5))): Seq[DynamicValue]))
      )
    },
    test("returns 0 for empty string") {
      val expr = DynamicSchemaExpr.StringLength(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(0))): Seq[DynamicValue]))
      )
    },
    test("fails on non-string") {
      val expr = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(intValue))
      assert(expr.eval(DynamicValue.Null))(isLeft)
    }
  )

  private val arithmeticSuite = suite("Arithmetic")(
    test("adds two ints") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(20))),
        DynamicSchemaExpr.ArithmeticOperator.Add
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(30))): Seq[DynamicValue]))
      )
    },
    test("subtracts two ints") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(50))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(20))),
        DynamicSchemaExpr.ArithmeticOperator.Subtract
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(30))): Seq[DynamicValue]))
      )
    },
    test("multiplies two ints") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(6))),
        DynamicSchemaExpr.ArithmeticOperator.Multiply
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(30))): Seq[DynamicValue]))
      )
    },
    test("adds two longs") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(1000L))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(2000L))),
        DynamicSchemaExpr.ArithmeticOperator.Add
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Long(3000L))): Seq[DynamicValue]))
      )
    },
    test("adds two doubles") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(1.5))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(2.5))),
        DynamicSchemaExpr.ArithmeticOperator.Add
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Double(4.0))): Seq[DynamicValue]))
      )
    },
    test("adds two floats") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(1.5f))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
        DynamicSchemaExpr.ArithmeticOperator.Add
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))): Seq[DynamicValue]))
      )
    },
    test("fails on mismatched types (int + string)") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(intValue),
        DynamicSchemaExpr.Literal(strValue),
        DynamicSchemaExpr.ArithmeticOperator.Add
      )
      assert(expr.eval(DynamicValue.Null))(isLeft)
    },
    test("fails on non-primitive input") {
      val expr = DynamicSchemaExpr.Arithmetic(
        DynamicSchemaExpr.Literal(record),
        DynamicSchemaExpr.Literal(intValue),
        DynamicSchemaExpr.ArithmeticOperator.Add
      )
      assert(expr.eval(DynamicValue.Null))(isLeft)
    }
  )

  private val relationalSuite = suite("Relational")(
    test("LessThan returns true when left < right") {
      val expr = DynamicSchemaExpr.Relational(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.RelationalOperator.LessThan
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("LessThan returns false when left >= right") {
      val expr = DynamicSchemaExpr.Relational(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
        DynamicSchemaExpr.RelationalOperator.LessThan
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(false))): Seq[DynamicValue]))
      )
    },
    test("Equal returns true for equal values") {
      val expr = DynamicSchemaExpr.Relational(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
        DynamicSchemaExpr.RelationalOperator.Equal
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("NotEqual returns true for different values") {
      val expr = DynamicSchemaExpr.Relational(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
        DynamicSchemaExpr.RelationalOperator.NotEqual
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("GreaterThan works correctly") {
      val expr = DynamicSchemaExpr.Relational(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
        DynamicSchemaExpr.RelationalOperator.GreaterThan
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("GreaterThanOrEqual works on equal values") {
      val expr = DynamicSchemaExpr.Relational(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("LessThanOrEqual works on equal values") {
      val expr = DynamicSchemaExpr.Relational(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
        DynamicSchemaExpr.RelationalOperator.LessThanOrEqual
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    }
  )

  private val logicalSuite = suite("Logical")(
    test("And returns true when both true") {
      val expr = DynamicSchemaExpr.Logical(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
        DynamicSchemaExpr.LogicalOperator.And
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("And returns false when one is false") {
      val expr = DynamicSchemaExpr.Logical(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
        DynamicSchemaExpr.LogicalOperator.And
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(false))): Seq[DynamicValue]))
      )
    },
    test("Or returns true when one is true") {
      val expr = DynamicSchemaExpr.Logical(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
        DynamicSchemaExpr.LogicalOperator.Or
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("Or returns false when both false") {
      val expr = DynamicSchemaExpr.Logical(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
        DynamicSchemaExpr.LogicalOperator.Or
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(false))): Seq[DynamicValue]))
      )
    },
    test("fails when input is not boolean") {
      val expr = DynamicSchemaExpr.Logical(
        DynamicSchemaExpr.Literal(intValue),
        DynamicSchemaExpr.Literal(boolValue),
        DynamicSchemaExpr.LogicalOperator.And
      )
      assert(expr.eval(DynamicValue.Null))(isLeft)
    }
  )

  private val notSuite = suite("Not")(
    test("negates true to false") {
      val expr = DynamicSchemaExpr.Not(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(false))): Seq[DynamicValue]))
      )
    },
    test("negates false to true") {
      val expr = DynamicSchemaExpr.Not(
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
      )
      assert(expr.eval(DynamicValue.Null))(
        isRight(equalTo(Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Seq[DynamicValue]))
      )
    },
    test("fails on non-boolean") {
      val expr = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(intValue))
      assert(expr.eval(DynamicValue.Null))(isLeft)
    }
  )
}
