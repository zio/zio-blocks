package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationExprSpec extends SchemaBaseSpec {

  def spec = suite("MigrationExprSpec")(
    suite("MigrationExpr")(
      test("Literal evaluates to its value") {
        val expr   = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val input  = DynamicValue.Record(Chunk.empty)
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("FieldRef extracts field from record") {
        val expr   = MigrationExpr.FieldRef(DynamicOptic.root.field("name"))
        val input  = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("test"))))
      },
      test("FieldRef fails on missing field") {
        val expr   = MigrationExpr.FieldRef(DynamicOptic.root.field("missing"))
        val input  = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("StringConcat concatenates strings") {
        val expr = MigrationExpr.StringConcat(
          MigrationExpr.FieldRef(DynamicOptic.root.field("first")),
          MigrationExpr.FieldRef(DynamicOptic.root.field("last"))
        )
        val input = DynamicValue.Record(
          Chunk(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("Hello")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("World"))
          )
        )
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("HelloWorld"))))
      },
      test("Arithmetic Add works with integers") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(15))))
      },
      test("Arithmetic Subtract works with integers") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(7))))
      },
      test("Arithmetic Multiply works with doubles") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(2.5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(4.0))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(10.0))))
      },
      test("Arithmetic Divide works with longs") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(20L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(4L))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(5L))))
      },
      test("Arithmetic Divide by zero fails for integers") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Arithmetic works with floats") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.0f))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(6.0f))))
      },
      test("Convert ToString converts int to string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("Convert ToInt converts string to int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("123"))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(123))))
      },
      test("Convert ToInt fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("abc"))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToLong converts various types") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("Convert ToDouble converts various types") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(42.0))))
      },
      test("Convert ToFloat converts from double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(
          result
            .map(_.asInstanceOf[DynamicValue.Primitive].value.asInstanceOf[PrimitiveValue.Float].value)
            .map(f => Math.abs(f - 3.14f) < 0.01f) == Right(true)
        )
      },
      test("Convert ToBoolean converts string true") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("true"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Convert ToBoolean converts int to boolean") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToBigInt converts from long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(12345678901L))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345678901L)))))
      },
      test("Convert ToBigDecimal converts from float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.14f))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isRight)
      },
      test("Conditional returns ifTrue when condition is true") {
        val expr = MigrationExpr.Conditional(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("yes"))))
      },
      test("Conditional returns ifFalse when condition is false") {
        val expr = MigrationExpr.Conditional(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("no"))))
      },
      test("Compare Eq returns true for equal values") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Eq
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Ne returns true for unequal values") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          MigrationExpr.CompareOp.Ne
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Lt returns true when left < right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Lt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Le returns true when left <= right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Le
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Gt returns true when left > right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(7))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Gt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Compare Ge returns true when left >= right") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          MigrationExpr.CompareOp.Ge
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DefaultValue returns fallback") {
        val expr   = MigrationExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(0))))
      },
      test("DSL + operator creates Add arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(4)))
        val expr   = a + b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(7))))
      },
      test("DSL ++ operator creates StringConcat") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello")))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.String("World")))
        val expr   = a ++ b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("HelloWorld"))))
      },
      test("FieldRef fails when path expects Record but gets Primitive") {
        val expr   = MigrationExpr.FieldRef(DynamicOptic.root.field("name"))
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = expr.eval(input)
        assertTrue(result.isLeft)
      },
      test("Arithmetic fails with non-primitive values") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert fails with non-primitive input") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("DSL * operator creates Multiply arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(4)))
        val expr   = a * b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(12))))
      },
      test("DSL / operator creates Divide arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(12)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(4)))
        val expr   = a / b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(3))))
      },
      test("DSL < operator creates Lt comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val expr   = a < b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL <= operator creates Le comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a <= b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL > operator creates Gt comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a > b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL >= operator creates Ge comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val expr   = a >= b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL toLong creates ToLong conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).toLong
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("DSL toFloat creates ToFloat conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).toFloat
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(42.0f))))
      },
      test("DSL toDouble creates ToDouble conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).toDouble
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(42.0))))
      },
      test("DSL toBigInt creates ToBigInt conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Long(1234567890123L))).toBigInt
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1234567890123L)))))
      },
      test("DSL toBigDecimal creates ToBigDecimal conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))).toBigDecimal
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))))
      },
      test("DSL toBoolean creates ToBoolean conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))).toBoolean
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL asString creates ToString conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))).asString
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("DSL - operator creates Subtract arithmetic") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(10)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a - b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(7))))
      },
      test("DSL === operator creates Eq comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val expr   = a === b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL =!= operator creates Ne comparison") {
        import MigrationExpr._
        val a      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val b      = Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val expr   = a =!= b
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("DSL toInt creates ToInt conversion") {
        import MigrationExpr._
        val expr   = Literal(DynamicValue.Primitive(PrimitiveValue.String("42"))).toInt
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("MigrationExpr.field creates FieldRef") {
        val expr   = MigrationExpr.field(DynamicOptic.root.field("name"))
        val input  = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result = expr.eval(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("test"))))
      },
      test("Convert ToString works with Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(9876543210L))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("9876543210"))))
      },
      test("Convert ToString works with Double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("3.14"))))
      },
      test("Convert ToString works with Float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("2.5"))))
      },
      test("Convert ToString works with Boolean") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("true"))))
      },
      test("Convert ToString works with BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("123456789012345")))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("123456789012345"))))
      },
      test("Convert ToString works with BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("999.99")))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("999.99"))))
      },
      test("Convert ToString works with Char") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Char('X'))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("X"))))
      }
    ),
    suite("Expression-based MigrationActions")(
      test("TransformValueExpr evaluates expression") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.FieldRef(DynamicOptic.root),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          MigrationExpr.ArithmeticOp.Add
        )
        val action    = MigrationAction.TransformValueExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "value").map(_._2) == Some(
            DynamicValue.Primitive(PrimitiveValue.Int(15))
          )
        )
      },
      test("TransformValueExpr reverse swaps expressions") {
        val expr        = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val reverseExpr = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val action      = MigrationAction.TransformValueExpr(DynamicOptic.root.field("x"), expr, Some(reverseExpr))
        val reversed    = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.TransformValueExpr])
      },
      test("ChangeTypeExpr converts field type") {
        val expr =
          MigrationExpr.Convert(MigrationExpr.FieldRef(DynamicOptic.root), MigrationExpr.PrimitiveTargetType.ToString)
        val action    = MigrationAction.ChangeTypeExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        val result    = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "value").map(_._2) == Some(
            DynamicValue.Primitive(PrimitiveValue.String("42"))
          )
        )
      },
      test("JoinExpr combines fields using expression") {
        val combineExpr = MigrationExpr.StringConcat(
          MigrationExpr.FieldRef(DynamicOptic.root.field("first")),
          MigrationExpr.StringConcat(
            MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" "))),
            MigrationExpr.FieldRef(DynamicOptic.root.field("last"))
          )
        )
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          combineExpr
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists {
            case ("fullName", DynamicValue.Primitive(PrimitiveValue.String("John Doe"))) => true
            case _                                                                       => false
          }
        )
      },
      test("JoinExpr fails when path doesn't end with Field") {
        val action    = MigrationAction.JoinExpr(DynamicOptic.root, Vector.empty, MigrationExpr.Literal(DynamicValue.Null))
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk.empty)
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("SplitExpr splits field using expressions") {
        val firstExpr = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("John")))
        val lastExpr  = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Doe")))
        val action    = MigrationAction.SplitExpr(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Vector(firstExpr, lastExpr)
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          Chunk("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
        )
        val result = migration(input)
        assertTrue(
          result.isRight,
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "first"),
          result.toOption.get.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "last")
        )
      },
      test("SplitExpr fails when targetPaths and splitExprs have different lengths") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Vector(MigrationExpr.Literal(DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("SplitExpr fails when target path doesn't end with Field") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root),
          Vector(MigrationExpr.Literal(DynamicValue.Null))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        val result    = migration(input)
        assertTrue(result.isLeft)
      },
      test("JoinExpr reverse is SplitExpr") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          MigrationExpr.Literal(DynamicValue.Null)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.SplitExpr])
      },
      test("SplitExpr reverse is JoinExpr") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.Literal(DynamicValue.Null))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.JoinExpr])
      },
      test("SplitExpr reverse with combineExpr uses provided expression") {
        val combineExpr = MigrationExpr.StringConcat(
          MigrationExpr.FieldRef(DynamicOptic.root.field("a")),
          MigrationExpr.FieldRef(DynamicOptic.root.field("b"))
        )
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          Vector(
            MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("first"))),
            MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("second")))
          ),
          Some(combineExpr)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.JoinExpr])
      }
    ),
    suite("Additional arithmetic and conversion coverage")(
      test("Arithmetic Multiply with Long") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(5L))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(500L))))
      },
      test("Arithmetic Subtract with Double") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(10.5))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.5))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(7.0))))
      },
      test("Arithmetic Add with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(1.5f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))))
      },
      test("Arithmetic Subtract with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(5.5f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.0f))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.5f))))
      },
      test("Arithmetic Multiply with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))),
          MigrationExpr.ArithmeticOp.Multiply
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(10.0f))))
      },
      test("Arithmetic Divide with Float") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(9.0f))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))))
      },
      test("Arithmetic Add with Long") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(50L))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(150L))))
      },
      test("Arithmetic Subtract with Long") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(30L))),
          MigrationExpr.ArithmeticOp.Subtract
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(70L))))
      },
      test("Arithmetic Divide by zero with Long fails") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(0L))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Arithmetic with BigDecimal fails gracefully") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(100)))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(4)))),
          MigrationExpr.ArithmeticOp.Divide
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Arithmetic with BigInt fails gracefully") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1000)))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(234)))),
          MigrationExpr.ArithmeticOp.Add
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToInt from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(42L))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("Convert ToDouble from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(100.0))))
      },
      test("Convert ToBigDecimal from Int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(42)))))
      },
      test("Convert ToLong from Double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(99.9))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(99L))))
      },
      test("Convert ToBigInt from String") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("12345"))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345)))))
      },
      test("Convert ToFloat from String") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("3.14"))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.14f))))
      },
      test("Convert ToBoolean from 0") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToBoolean from false string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("false"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToString works with Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(123))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("123"))))
      },
      test("Convert ToString works with Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(42))),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("Convert ToInt from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(100))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("Convert ToInt from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(50))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(50))))
      },
      test("Convert ToInt from Float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(3.14f))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(3))))
      },
      test("Convert ToInt from Double") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(9.99))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(9))))
      },
      test("Convert ToInt from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(999)))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(999))))
      },
      test("Convert ToInt from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(777)))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(777))))
      },
      test("Convert ToLong from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(200))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(200L))))
      },
      test("Convert ToLong from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(10))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(10L))))
      },
      test("Convert ToLong from Float") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(5.5f))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(5L))))
      },
      test("Convert ToLong from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345L)))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(12345L))))
      },
      test("Convert ToLong from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(9999L)))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(9999L))))
      },
      test("Convert ToDouble from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(30))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(30.0))))
      },
      test("Convert ToDouble from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(5))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(5.0))))
      },
      test("Convert ToDouble from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1000)))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(1000.0))))
      },
      test("Convert ToDouble from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Double(3.14))))
      },
      test("Convert ToFloat from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(15))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(15.0f))))
      },
      test("Convert ToFloat from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(3))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))))
      },
      test("Convert ToFloat from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(100L))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(100.0f))))
      },
      test("Convert ToFloat from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(500)))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(500.0f))))
      },
      test("Convert ToFloat from BigDecimal") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2.5)))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))))
      },
      test("Convert ToBigInt from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(42))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(42)))))
      },
      test("Convert ToBigInt from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(7))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(7)))))
      },
      test("Convert ToBigInt from Int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(123))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(123)))))
      },
      test("Convert ToBigDecimal from Short") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Short(50))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(50)))))
      },
      test("Convert ToBigDecimal from Byte") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Byte(8))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(8)))))
      },
      test("Convert ToBigDecimal from Long") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(999L))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(999)))))
      },
      test("Convert ToBigDecimal from BigInt") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(12345)))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(12345)))))
      },
      test("Convert ToInt fails for invalid Boolean") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToDouble fails for invalid Char") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Char('x'))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToBoolean from Long fails") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(1L))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("Convert ToLong from Int") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("Convert ToBigDecimal from String") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("123.456"))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        val result = expr.eval(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456")))))
      }
    ),
    suite("MigrationExpr error path coverage")(
      test("Conditional fails when condition is not boolean") {
        val expr = MigrationExpr.Conditional(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("StringConcat fails when operand is not primitive") {
        val expr = MigrationExpr.StringConcat(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("world")))
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Compare fails with non-primitive values") {
        val expr = MigrationExpr.Compare(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.CompareOp.Eq
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("FieldRef fails with unsupported path node") {
        val expr  = MigrationExpr.FieldRef(DynamicOptic.root.elements)
        val input = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(expr.eval(input).isLeft)
      },
      test("Arithmetic fails with mismatched numeric types") {
        val expr = MigrationExpr.Arithmetic(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(2L))),
          MigrationExpr.ArithmeticOp.Add
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBoolean from string 'no' returns false") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        assertTrue(expr.eval(DynamicValue.Null) == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("Convert ToBoolean from string 'yes' returns true") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        assertTrue(expr.eval(DynamicValue.Null) == Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Convert ToBoolean fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("maybe"))),
          MigrationExpr.PrimitiveTargetType.ToBoolean
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigInt fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigInt fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_a_number"))),
          MigrationExpr.PrimitiveTargetType.ToBigInt
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigDecimal fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("xyz"))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToLong fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_long"))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToDouble fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_double"))),
          MigrationExpr.PrimitiveTargetType.ToDouble
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToFloat fails for invalid string") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not_float"))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToFloat fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToFloat
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToLong fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          MigrationExpr.PrimitiveTargetType.ToLong
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Convert ToBigDecimal fails for unsupported type") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Char('x'))),
          MigrationExpr.PrimitiveTargetType.ToBigDecimal
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      },
      test("Type conversion fails with non-primitive value") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.Literal(DynamicValue.Record(Chunk.empty)),
          MigrationExpr.PrimitiveTargetType.ToInt
        )
        assertTrue(expr.eval(DynamicValue.Null).isLeft)
      }
    ),
    suite("Executor error paths and reverse coverage")(
      test("JoinExpr fails when combine expression fails") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a")),
          MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent"))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(migration(input).isLeft)
      },
      test("JoinExpr fails on non-Record parent") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector.empty,
          MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("SplitExpr fails when split expression fails") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent")))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("source" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
        assertTrue(migration(input).isLeft)
      },
      test("SplitExpr fails on non-Record parent") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("TransformValueExpr fails when expression fails") {
        val expr      = MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent"))
        val action    = MigrationAction.TransformValueExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        assertTrue(migration(input).isLeft)
      },
      test("ChangeTypeExpr fails when expression fails") {
        val expr      = MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent"))
        val action    = MigrationAction.ChangeTypeExpr(DynamicOptic.root.field("value"), expr)
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        assertTrue(migration(input).isLeft)
      },
      test("Join fails on non-Record parent") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("combined"),
          Vector.empty,
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("Split fails on non-Record parent") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration(input).isLeft)
      },
      test("Rename fails when path doesn't end with Field node") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "newName")
        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(migration(input).isLeft)
      },
      test("JoinExpr reverse without splitExprs uses FieldRef fallback") {
        val action = MigrationAction.JoinExpr(
          DynamicOptic.root.field("combined"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          MigrationExpr.Literal(DynamicValue.Null),
          None
        )
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.SplitExpr(_, paths, exprs, Some(_)) =>
            paths.length == 2 && exprs.length == 2 && exprs.forall(_.isInstanceOf[MigrationExpr.FieldRef])
          case _ => false
        })
      },
      test("SplitExpr reverse without combineExpr uses FieldRef fallback") {
        val action = MigrationAction.SplitExpr(
          DynamicOptic.root.field("source"),
          Vector(DynamicOptic.root.field("a")),
          Vector(MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))),
          None
        )
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.JoinExpr(_, _, MigrationExpr.FieldRef(_), Some(_)) => true
          case _                                                                  => false
        })
      },
      test("ChangeTypeExpr reverse without reverseExpr uses original expr") {
        val expr = MigrationExpr.Convert(
          MigrationExpr.FieldRef(DynamicOptic.root),
          MigrationExpr.PrimitiveTargetType.ToString
        )
        val action   = MigrationAction.ChangeTypeExpr(DynamicOptic.root.field("v"), expr, None)
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.ChangeTypeExpr(_, e, Some(re)) => e == expr && re == expr
          case _                                              => false
        })
      },
      test("TransformValueExpr reverse without reverseExpr uses original expr") {
        val expr     = MigrationExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val action   = MigrationAction.TransformValueExpr(DynamicOptic.root.field("x"), expr, None)
        val reversed = action.reverse
        assertTrue(reversed match {
          case MigrationAction.TransformValueExpr(_, e, Some(re)) => e == expr && re == expr
          case _                                                  => false
        })
      }
    )
  )
}
