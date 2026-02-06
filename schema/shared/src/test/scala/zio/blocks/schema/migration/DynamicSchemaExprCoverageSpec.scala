package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.chunk.Chunk
import zio.test._

object DynamicSchemaExprCoverageSpec extends SchemaBaseSpec {

  private val intVal  = DynamicValue.Primitive(PrimitiveValue.Int(10))
  private val strVal  = DynamicValue.Primitive(PrimitiveValue.String("hello"))
  private val boolT   = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
  private val boolF   = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
  private val longVal = DynamicValue.Primitive(PrimitiveValue.Long(100L))
  private val dblVal  = DynamicValue.Primitive(PrimitiveValue.Double(3.14))
  private val fltVal  = DynamicValue.Primitive(PrimitiveValue.Float(2.5f))
  private val record  = DynamicValue.Record(Chunk("name" -> strVal, "age" -> intVal))

  def spec: Spec[TestEnvironment, Any] = suite("DynamicSchemaExprCoverageSpec")(
    suite("Not error paths")(
      test("Not fails on record") {
        val expr = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(record))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("Not fails on Variant") {
        val variant = DynamicValue.Variant("A", intVal)
        val expr    = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(variant))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("Not fails on Sequence") {
        val seq  = DynamicValue.Sequence(Chunk(intVal))
        val expr = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(seq))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("Not fails on Map") {
        val map  = DynamicValue.Map(Chunk(strVal -> intVal))
        val expr = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(map))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("Not fails on Null") {
        val expr = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(DynamicValue.Null))
        assertTrue(expr.eval(intVal).isLeft)
      }
    ),
    suite("Logical error paths")(
      test("Logical fails when left is non-boolean") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(boolT),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("Logical fails when right is non-boolean") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(boolT),
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.LogicalOperator.Or
        )
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("Logical fails when both are non-boolean") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(strVal),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(intVal).isLeft)
      }
    ),
    suite("Relational additional operators")(
      test("LessThanOrEqual: 10 <= 10 = true") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.RelationalOperator.LessThanOrEqual
        )
        assertTrue(expr.eval(intVal) == Right(boolT))
      },
      test("LessThanOrEqual: 10 <= 3 = false") {
        val three = DynamicValue.Primitive(PrimitiveValue.Int(3))
        val expr  = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(three),
          DynamicSchemaExpr.RelationalOperator.LessThanOrEqual
        )
        assertTrue(expr.eval(intVal) == Right(boolF))
      },
      test("GreaterThan: 10 > 3 = true") {
        val three = DynamicValue.Primitive(PrimitiveValue.Int(3))
        val expr  = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(three),
          DynamicSchemaExpr.RelationalOperator.GreaterThan
        )
        assertTrue(expr.eval(intVal) == Right(boolT))
      },
      test("GreaterThanOrEqual: 10 >= 10 = true") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual
        )
        assertTrue(expr.eval(intVal) == Right(boolT))
      },
      test("GreaterThanOrEqual: 3 >= 10 = false") {
        val three = DynamicValue.Primitive(PrimitiveValue.Int(3))
        val expr  = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(three),
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual
        )
        assertTrue(expr.eval(intVal) == Right(boolF))
      },
      test("LessThan: 10 < 3 = false") {
        val three = DynamicValue.Primitive(PrimitiveValue.Int(3))
        val expr  = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(three),
          DynamicSchemaExpr.RelationalOperator.LessThan
        )
        assertTrue(expr.eval(intVal) == Right(boolF))
      },
      test("Equal: different values = false") {
        val three = DynamicValue.Primitive(PrimitiveValue.Int(3))
        val expr  = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(three),
          DynamicSchemaExpr.RelationalOperator.Equal
        )
        assertTrue(expr.eval(intVal) == Right(boolF))
      },
      test("NotEqual: same values = false") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.RelationalOperator.NotEqual
        )
        assertTrue(expr.eval(intVal) == Right(boolF))
      }
    ),
    suite("Arithmetic additional types")(
      test("Float Add") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(fltVal),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Float(1.5f))),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(4.0f))))
      },
      test("Short Subtract") {
        val s1   = DynamicValue.Primitive(PrimitiveValue.Short(30.toShort))
        val s2   = DynamicValue.Primitive(PrimitiveValue.Short(10.toShort))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(s1),
          DynamicSchemaExpr.Literal(s2),
          DynamicSchemaExpr.ArithmeticOperator.Subtract
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Short(20.toShort))))
      },
      test("Short Multiply") {
        val s1   = DynamicValue.Primitive(PrimitiveValue.Short(3.toShort))
        val s2   = DynamicValue.Primitive(PrimitiveValue.Short(4.toShort))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(s1),
          DynamicSchemaExpr.Literal(s2),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Short(12.toShort))))
      },
      test("Byte Add") {
        val b1   = DynamicValue.Primitive(PrimitiveValue.Byte(10.toByte))
        val b2   = DynamicValue.Primitive(PrimitiveValue.Byte(20.toByte))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(b1),
          DynamicSchemaExpr.Literal(b2),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Byte(30.toByte))))
      },
      test("Byte Subtract") {
        val b1   = DynamicValue.Primitive(PrimitiveValue.Byte(30.toByte))
        val b2   = DynamicValue.Primitive(PrimitiveValue.Byte(10.toByte))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(b1),
          DynamicSchemaExpr.Literal(b2),
          DynamicSchemaExpr.ArithmeticOperator.Subtract
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Byte(20.toByte))))
      },
      test("Byte Multiply") {
        val b1   = DynamicValue.Primitive(PrimitiveValue.Byte(3.toByte))
        val b2   = DynamicValue.Primitive(PrimitiveValue.Byte(4.toByte))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(b1),
          DynamicSchemaExpr.Literal(b2),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Byte(12.toByte))))
      },
      test("BigInt Add") {
        val bi1  = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(100)))
        val bi2  = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(200)))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(bi1),
          DynamicSchemaExpr.Literal(bi2),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(300)))))
      },
      test("BigInt Subtract") {
        val bi1  = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(300)))
        val bi2  = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(100)))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(bi1),
          DynamicSchemaExpr.Literal(bi2),
          DynamicSchemaExpr.ArithmeticOperator.Subtract
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(200)))))
      },
      test("BigInt Multiply") {
        val bi1  = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(10)))
        val bi2  = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(20)))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(bi1),
          DynamicSchemaExpr.Literal(bi2),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(200)))))
      },
      test("BigDecimal Add") {
        val bd1  = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.5)))
        val bd2  = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2.5)))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(bd1),
          DynamicSchemaExpr.Literal(bd2),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(4.0)))))
      },
      test("BigDecimal Subtract") {
        val bd1  = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(5.0)))
        val bd2  = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(2.0)))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(bd1),
          DynamicSchemaExpr.Literal(bd2),
          DynamicSchemaExpr.ArithmeticOperator.Subtract
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.0)))))
      },
      test("BigDecimal Multiply") {
        val bd1  = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.0)))
        val bd2  = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(4.0)))
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(bd1),
          DynamicSchemaExpr.Literal(bd2),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(12.0)))))
      },
      test("non-primitive arithmetic fails") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(record),
          DynamicSchemaExpr.Literal(record),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(intVal).isLeft)
      }
    ),
    suite("StringConcat error")(
      test("fails on non-string left") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(strVal)
        )
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("fails on non-string right") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(strVal),
          DynamicSchemaExpr.Literal(intVal)
        )
        assertTrue(expr.eval(intVal).isLeft)
      }
    ),
    suite("StringLength error")(
      test("fails on non-string") {
        val expr = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(intVal))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("fails on Variant") {
        val variant = DynamicValue.Variant("X", intVal)
        val expr    = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(variant))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("fails on Sequence") {
        val seq  = DynamicValue.Sequence(Chunk(intVal))
        val expr = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(seq))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("fails on Map") {
        val map  = DynamicValue.Map(Chunk(strVal -> intVal))
        val expr = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(map))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("fails on Null") {
        val expr = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(DynamicValue.Null))
        assertTrue(expr.eval(intVal).isLeft)
      }
    ),
    suite("CoercePrimitive additional coverage")(
      test("coerce Double to Double") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Double")
        assertTrue(expr.eval(intVal) == Right(dblVal))
      },
      test("coerce Float to Double") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Double")
        assertTrue(expr.eval(intVal).isRight)
      },
      test("coerce Int to Double") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "Double")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(10.0))))
      },
      test("coerce Long to Double") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(longVal), "Double")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(100.0))))
      },
      test("coerce Short to Double") {
        val sVal = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sVal), "Double")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(5.0))))
      },
      test("coerce Byte to Double") {
        val bVal = DynamicValue.Primitive(PrimitiveValue.Byte(3.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bVal), "Double")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(3.0))))
      },
      test("coerce String to Double valid") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("3.14"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Double")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(3.14))))
      },
      test("coerce String to Double invalid") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Double")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Boolean to Double fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Double")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Float to Float") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Float")
        assertTrue(expr.eval(intVal) == Right(fltVal))
      },
      test("coerce Double to Float") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Float")
        assertTrue(expr.eval(intVal).isRight)
      },
      test("coerce Int to Float") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "Float")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(10.0f))))
      },
      test("coerce Long to Float") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(longVal), "Float")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(100.0f))))
      },
      test("coerce String to Float valid") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("2.5"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Float")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(2.5f))))
      },
      test("coerce String to Float invalid") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("xyz"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Float")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Boolean to Float fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Float")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Boolean to Boolean") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Boolean")
        assertTrue(expr.eval(intVal) == Right(boolT))
      },
      test("coerce String true to Boolean") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("true"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Boolean")
        assertTrue(expr.eval(intVal) == Right(boolT))
      },
      test("coerce invalid String to Boolean fails") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("maybe"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Int 0 to Boolean false") {
        val zero = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(zero), "Boolean")
        assertTrue(expr.eval(intVal) == Right(boolF))
      },
      test("coerce Int 1 to Boolean true") {
        val one  = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(one), "Boolean")
        assertTrue(expr.eval(intVal) == Right(boolT))
      },
      test("coerce Long to Boolean fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(longVal), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce to unknown type fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "Complex")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce record fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(record), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce String to String is identity") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(strVal), "String")
        assertTrue(expr.eval(intVal) == Right(strVal))
      },
      test("coerce Char to String") {
        val ch   = DynamicValue.Primitive(PrimitiveValue.Char('Z'))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(ch), "String")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.String("Z"))))
      },
      test("coerce BigInt to String") {
        val bi   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(999)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bi), "String")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.String("999"))))
      },
      test("coerce BigDecimal to String") {
        val bd   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.5)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bd), "String")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.String("1.5"))))
      },
      test("coerce Boolean to Int fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Boolean to Long fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Long")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Char to Int fails") {
        val ch   = DynamicValue.Primitive(PrimitiveValue.Char('a'))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(ch), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Char to Long fails") {
        val ch   = DynamicValue.Primitive(PrimitiveValue.Char('x'))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(ch), "Long")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Char to Double fails") {
        val ch   = DynamicValue.Primitive(PrimitiveValue.Char('z'))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(ch), "Double")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Char to Float fails") {
        val ch   = DynamicValue.Primitive(PrimitiveValue.Char('q'))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(ch), "Float")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Char to Boolean fails") {
        val ch   = DynamicValue.Primitive(PrimitiveValue.Char('t'))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(ch), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigInt to Int fails") {
        val bi   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(42)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bi), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigInt to Long fails") {
        val bi   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(42)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bi), "Long")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigInt to Double fails") {
        val bi   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(42)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bi), "Double")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigInt to Float fails") {
        val bi   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(42)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bi), "Float")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigInt to Boolean fails") {
        val bi   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bi), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigDecimal to Int fails") {
        val bd   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bd), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigDecimal to Long fails") {
        val bd   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bd), "Long")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigDecimal to Double fails") {
        val bd   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bd), "Double")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigDecimal to Float fails") {
        val bd   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(3.14)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bd), "Float")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce BigDecimal to Boolean fails") {
        val bd   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.0)))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bd), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Boolean to Double fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Double")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Boolean to Float fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Float")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce to unsupported type Char fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce to unsupported type BigInt fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "BigInt")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce to unsupported type BigDecimal fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "BigDecimal")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce invalid String to Int fails") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce invalid String to Long fails") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Long")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce invalid String to Double fails") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Double")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce invalid String to Float fails") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Float")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce invalid String to Boolean fails") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce UUID to String via toString") {
        val uuid =
          DynamicValue.Primitive(PrimitiveValue.UUID(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(uuid), "String")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Short to Int") {
        val sVal = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sVal), "Int")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Int(5))))
      },
      test("coerce Byte to Int") {
        val bVal = DynamicValue.Primitive(PrimitiveValue.Byte(7.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bVal), "Int")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Int(7))))
      },
      test("coerce Double to Int") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Int")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Int(3))))
      },
      test("coerce Float to Int") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Int")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Int(2))))
      },
      test("coerce Short to Long") {
        val sVal = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sVal), "Long")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(5L))))
      },
      test("coerce Byte to Long") {
        val bVal = DynamicValue.Primitive(PrimitiveValue.Byte(3.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bVal), "Long")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(3L))))
      },
      test("coerce Double to Long") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Long")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(3L))))
      },
      test("coerce Float to Long") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Long")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(2L))))
      },
      test("coerce String to Long valid") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("999"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Long")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(999L))))
      },
      test("coerce String to Long invalid") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("bad"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Long")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Short to Float") {
        val sVal = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sVal), "Float")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(5.0f))))
      },
      test("coerce Byte to Float") {
        val bVal = DynamicValue.Primitive(PrimitiveValue.Byte(3.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bVal), "Float")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(3.0f))))
      },
      test("coerce null string to Int fails") {
        // Use a non-numeric string to ensure failure
        val sv   = DynamicValue.Primitive(PrimitiveValue.String(""))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      }
    ),
    suite("navigateDynamicValue coverage")(
      test("navigate through variant case") {
        val v      = DynamicValue.Variant("Active", intVal)
        val result = DynamicSchemaExpr.navigateDynamicValue(v, DynamicOptic.root.caseOf("Active"))
        assertTrue(result == Some(intVal))
      },
      test("navigate fails on wrong case") {
        val v      = DynamicValue.Variant("Active", intVal)
        val result = DynamicSchemaExpr.navigateDynamicValue(v, DynamicOptic.root.caseOf("Wrong"))
        assertTrue(result == None)
      },
      test("navigate through index") {
        val seq    = DynamicValue.Sequence(Chunk(intVal, strVal))
        val result = DynamicSchemaExpr.navigateDynamicValue(seq, DynamicOptic(Vector(DynamicOptic.Node.AtIndex(1))))
        assertTrue(result == Some(strVal))
      },
      test("navigate fails on negative index") {
        val seq    = DynamicValue.Sequence(Chunk(intVal))
        val result = DynamicSchemaExpr.navigateDynamicValue(seq, DynamicOptic(Vector(DynamicOptic.Node.AtIndex(-1))))
        assertTrue(result == None)
      },
      test("navigate fails AtIndex on non-sequence") {
        val result = DynamicSchemaExpr.navigateDynamicValue(intVal, DynamicOptic(Vector(DynamicOptic.Node.AtIndex(0))))
        assertTrue(result == None)
      },
      test("navigate through AtMapKey") {
        val m      = DynamicValue.Map(Chunk(strVal -> intVal))
        val result = DynamicSchemaExpr.navigateDynamicValue(m, DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(strVal))))
        assertTrue(result == Some(intVal))
      },
      test("navigate fails AtMapKey on missing key") {
        val m      = DynamicValue.Map(Chunk(strVal -> intVal))
        val result = DynamicSchemaExpr.navigateDynamicValue(m, DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(intVal))))
        assertTrue(result == None)
      },
      test("navigate fails AtMapKey on non-map") {
        val result =
          DynamicSchemaExpr.navigateDynamicValue(intVal, DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(strVal))))
        assertTrue(result == None)
      },
      test("navigate through Wrapped") {
        val w      = DynamicValue.Record(Chunk("value" -> intVal))
        val result = DynamicSchemaExpr.navigateDynamicValue(w, DynamicOptic(Vector(DynamicOptic.Node.Wrapped)))
        assertTrue(result == Some(intVal))
      },
      test("navigate fails Wrapped on multi-field record") {
        val result = DynamicSchemaExpr.navigateDynamicValue(record, DynamicOptic(Vector(DynamicOptic.Node.Wrapped)))
        assertTrue(result == None)
      },
      test("navigate fails Elements traversal") {
        val seq    = DynamicValue.Sequence(Chunk(intVal))
        val result = DynamicSchemaExpr.navigateDynamicValue(seq, DynamicOptic(Vector(DynamicOptic.Node.Elements)))
        assertTrue(result == None)
      },
      test("navigate fails case on non-variant") {
        val result = DynamicSchemaExpr.navigateDynamicValue(intVal, DynamicOptic.root.caseOf("X"))
        assertTrue(result == None)
      },
      test("navigate fails field on non-record") {
        val result = DynamicSchemaExpr.navigateDynamicValue(intVal, DynamicOptic.root.field("x"))
        assertTrue(result == None)
      }
    ),
    suite("fromSchemaExpr coverage")(
      test("converts Literal") {
        val expr   = SchemaExpr.Literal[Any, Int](42, Schema[Int])
        val result = DynamicSchemaExpr.fromSchemaExpr(expr)
        assertTrue(result.isRight)
      },
      test("converts Not") {
        val inner  = SchemaExpr.Literal[Any, Boolean](true, Schema[Boolean])
        val result = DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Not(inner))
        assertTrue(result.isRight)
      },
      test("converts StringLength") {
        val inner  = SchemaExpr.Literal[Any, String]("test", Schema[String])
        val result = DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.StringLength(inner))
        assertTrue(result.isRight)
      },
      test("rejects StringRegexMatch") {
        val inner  = SchemaExpr.Literal[Any, String]("test", Schema[String])
        val regex  = SchemaExpr.Literal[Any, String](".*", Schema[String])
        val result = DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.StringRegexMatch(inner, regex))
        assertTrue(result.isLeft)
      },
      test("converts StringConcat") {
        val l      = SchemaExpr.Literal[Any, String]("a", Schema[String])
        val r      = SchemaExpr.Literal[Any, String]("b", Schema[String])
        val result = DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.StringConcat(l, r))
        assertTrue(result.isRight)
      },
      test("converts Logical And") {
        val l      = SchemaExpr.Literal[Any, Boolean](true, Schema[Boolean])
        val r      = SchemaExpr.Literal[Any, Boolean](false, Schema[Boolean])
        val result = DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Logical(l, r, SchemaExpr.LogicalOperator.And))
        assertTrue(result.isRight)
      },
      test("converts Logical Or") {
        val l      = SchemaExpr.Literal[Any, Boolean](true, Schema[Boolean])
        val r      = SchemaExpr.Literal[Any, Boolean](false, Schema[Boolean])
        val result = DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Logical(l, r, SchemaExpr.LogicalOperator.Or))
        assertTrue(result.isRight)
      },
      test("converts Relational LessThan") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result =
          DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Relational(l, r, SchemaExpr.RelationalOperator.LessThan))
        assertTrue(result.isRight)
      },
      test("converts Relational LessThanOrEqual") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result =
          DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Relational(l, r, SchemaExpr.RelationalOperator.LessThanOrEqual))
        assertTrue(result.isRight)
      },
      test("converts Relational GreaterThan") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result =
          DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Relational(l, r, SchemaExpr.RelationalOperator.GreaterThan))
        assertTrue(result.isRight)
      },
      test("converts Relational GreaterThanOrEqual") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result = DynamicSchemaExpr.fromSchemaExpr(
          SchemaExpr.Relational(l, r, SchemaExpr.RelationalOperator.GreaterThanOrEqual)
        )
        assertTrue(result.isRight)
      },
      test("converts Relational Equal") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result = DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Relational(l, r, SchemaExpr.RelationalOperator.Equal))
        assertTrue(result.isRight)
      },
      test("converts Relational NotEqual") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result =
          DynamicSchemaExpr.fromSchemaExpr(SchemaExpr.Relational(l, r, SchemaExpr.RelationalOperator.NotEqual))
        assertTrue(result.isRight)
      },
      test("converts Arithmetic Add") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result =
          DynamicSchemaExpr.fromSchemaExpr(
            SchemaExpr.Arithmetic(l, r, SchemaExpr.ArithmeticOperator.Add, IsNumeric.IsInt)
          )
        assertTrue(result.isRight)
      },
      test("converts Arithmetic Subtract") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result = DynamicSchemaExpr.fromSchemaExpr(
          SchemaExpr.Arithmetic(l, r, SchemaExpr.ArithmeticOperator.Subtract, IsNumeric.IsInt)
        )
        assertTrue(result.isRight)
      },
      test("converts Arithmetic Multiply") {
        val l      = SchemaExpr.Literal[Any, Int](1, Schema[Int])
        val r      = SchemaExpr.Literal[Any, Int](2, Schema[Int])
        val result = DynamicSchemaExpr.fromSchemaExpr(
          SchemaExpr.Arithmetic(l, r, SchemaExpr.ArithmeticOperator.Multiply, IsNumeric.IsInt)
        )
        assertTrue(result.isRight)
      }
    ),
    suite("navigateDynamicValue coverage")(
      test("navigate Field into record") {
        val r     = DynamicValue.Record(Chunk("x" -> intVal))
        val optic = DynamicOptic.root.field("x")
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(r, optic) == Some(intVal))
      },
      test("navigate Field missing returns None") {
        val r     = DynamicValue.Record(Chunk("x" -> intVal))
        val optic = DynamicOptic.root.field("y")
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(r, optic).isEmpty)
      },
      test("navigate Field on non-record returns None") {
        val optic = DynamicOptic.root.field("x")
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(intVal, optic).isEmpty)
      },
      test("navigate Case into matching variant") {
        val v     = DynamicValue.Variant("A", intVal)
        val optic = DynamicOptic(Vector(DynamicOptic.Node.Case("A")))
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(v, optic) == Some(intVal))
      },
      test("navigate Case on non-matching variant returns None") {
        val v     = DynamicValue.Variant("B", intVal)
        val optic = DynamicOptic(Vector(DynamicOptic.Node.Case("A")))
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(v, optic).isEmpty)
      },
      test("navigate Case on non-variant returns None") {
        val optic = DynamicOptic(Vector(DynamicOptic.Node.Case("A")))
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(intVal, optic).isEmpty)
      },
      test("navigate AtIndex into sequence") {
        val s     = DynamicValue.Sequence(Chunk(intVal, strVal))
        val optic = DynamicOptic.root.at(1)
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(s, optic) == Some(strVal))
      },
      test("navigate AtIndex out of bounds returns None") {
        val s     = DynamicValue.Sequence(Chunk(intVal))
        val optic = DynamicOptic.root.at(5)
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(s, optic).isEmpty)
      },
      test("navigate AtIndex on non-sequence returns None") {
        val optic = DynamicOptic.root.at(0)
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(intVal, optic).isEmpty)
      },
      test("navigate AtMapKey into map") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("k"))
        val m     = DynamicValue.Map(Chunk(key -> intVal))
        val optic = DynamicOptic.root.atKey[String]("k")
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(m, optic) == Some(intVal))
      },
      test("navigate AtMapKey missing returns None") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("k"))
        val m     = DynamicValue.Map(Chunk(key -> intVal))
        val optic = DynamicOptic.root.atKey[String]("z")
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(m, optic).isEmpty)
      },
      test("navigate AtMapKey on non-map returns None") {
        val optic = DynamicOptic.root.atKey[String]("k")
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(intVal, optic).isEmpty)
      },
      test("navigate Wrapped into single-field record") {
        val r     = DynamicValue.Record(Chunk("value" -> intVal))
        val optic = DynamicOptic.root.wrapped
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(r, optic) == Some(intVal))
      },
      test("navigate Wrapped on multi-field record returns None") {
        val r     = DynamicValue.Record(Chunk("a" -> intVal, "b" -> strVal))
        val optic = DynamicOptic.root.wrapped
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(r, optic).isEmpty)
      },
      test("navigate Wrapped on non-record returns None") {
        val optic = DynamicOptic.root.wrapped
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(intVal, optic).isEmpty)
      },
      test("navigate Elements traversal returns None") {
        val s     = DynamicValue.Sequence(Chunk(intVal))
        val optic = DynamicOptic.root.elements
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(s, optic).isEmpty)
      },
      test("navigate MapKeys traversal returns None") {
        val k     = DynamicValue.Primitive(PrimitiveValue.String("k"))
        val m     = DynamicValue.Map(Chunk(k -> intVal))
        val optic = DynamicOptic.root.mapKeys
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(m, optic).isEmpty)
      },
      test("navigate MapValues traversal returns None") {
        val k     = DynamicValue.Primitive(PrimitiveValue.String("k"))
        val m     = DynamicValue.Map(Chunk(k -> intVal))
        val optic = DynamicOptic.root.mapValues
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(m, optic).isEmpty)
      },
      test("navigate root path returns value itself") {
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(intVal, DynamicOptic.root) == Some(intVal))
      },
      test("navigate chained Field.Field") {
        val inner = DynamicValue.Record(Chunk("y" -> intVal))
        val outer = DynamicValue.Record(Chunk("x" -> inner))
        val optic = DynamicOptic.root.field("x").field("y")
        assertTrue(DynamicSchemaExpr.navigateDynamicValue(outer, optic) == Some(intVal))
      }
    ),
    suite("getDynamicValueTypeName coverage")(
      test("Arithmetic non-primitive error uses type name") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(record),
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("StringConcat non-string error") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.Literal(strVal)
        )
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("StringLength non-string error") {
        val expr = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(intVal))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("StringLength on Null error") {
        val expr = DynamicSchemaExpr.StringLength(DynamicSchemaExpr.Literal(DynamicValue.Null))
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("CoercePrimitive on Variant error") {
        val variant = DynamicValue.Variant("A", intVal)
        val expr    = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(variant), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("CoercePrimitive on Sequence error") {
        val seq  = DynamicValue.Sequence(Chunk(intVal))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(seq), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("CoercePrimitive on Map error") {
        val k    = DynamicValue.Primitive(PrimitiveValue.String("k"))
        val m    = DynamicValue.Map(Chunk(k -> intVal))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(m), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("CoercePrimitive on Null error") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(DynamicValue.Null), "Int")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("StringConcat with Variant fails") {
        val variant = DynamicValue.Variant("A", intVal)
        val expr    = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(variant),
          DynamicSchemaExpr.Literal(strVal)
        )
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("StringConcat with Sequence fails") {
        val seq  = DynamicValue.Sequence(Chunk(intVal))
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(seq),
          DynamicSchemaExpr.Literal(strVal)
        )
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("Arithmetic with Null fails") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Null),
          DynamicSchemaExpr.Literal(intVal),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(intVal).isLeft)
      }
    ),
    suite("additional coercion branches")(
      test("coerce Long to Boolean fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(longVal), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Double to Boolean fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Float to Boolean fails") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Short to Boolean fails") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.Short(1.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Byte to Boolean fails") {
        val bv   = DynamicValue.Primitive(PrimitiveValue.Byte(1.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bv), "Boolean")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Long to Short unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(longVal), "Short")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Long to Byte unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(longVal), "Byte")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Double to Short unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Short")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Double to Byte unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Byte")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Float to Short unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Short")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Float to Byte unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Byte")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Int to Char unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Long to Char unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(longVal), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Double to Char unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(dblVal), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Float to Char unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(fltVal), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Boolean to Char unsupported target") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Byte to Short unsupported target") {
        val bv   = DynamicValue.Primitive(PrimitiveValue.Byte(7.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bv), "Short")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Short to Byte unsupported target") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Byte")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Short to Long") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Long")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(5L))))
      },
      test("coerce Byte to Long") {
        val bv   = DynamicValue.Primitive(PrimitiveValue.Byte(7.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bv), "Long")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(7L))))
      },
      test("coerce Short to Double") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Double")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(5.0))))
      },
      test("coerce Byte to Double") {
        val bv   = DynamicValue.Primitive(PrimitiveValue.Byte(7.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bv), "Double")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(7.0))))
      },
      test("coerce Short to Float") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Float")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(5.0f))))
      },
      test("coerce Byte to Float") {
        val bv   = DynamicValue.Primitive(PrimitiveValue.Byte(7.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bv), "Float")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Float(7.0f))))
      },
      test("coerce Short to String") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.Short(5.toShort))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "String")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.String("5"))))
      },
      test("coerce Byte to String") {
        val bv   = DynamicValue.Primitive(PrimitiveValue.Byte(7.toByte))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(bv), "String")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.String("7"))))
      },
      test("coerce Char to String") {
        val cv   = DynamicValue.Primitive(PrimitiveValue.Char('A'))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(cv), "String")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.String("A"))))
      },
      test("coerce Boolean to String") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(boolT), "String")
        assertTrue(expr.eval(intVal) == Right(DynamicValue.Primitive(PrimitiveValue.String("true"))))
      },
      test("coerce Int to BigDecimal") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "BigDecimal")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce Int to BigInt") {
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(intVal), "BigInt")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce String to Short unsupported target") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("not-a-number"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Short")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce String to Byte unsupported target") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("not-a-number"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Byte")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce String to Short numeric unsupported target") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("42"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Short")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce String to Byte numeric unsupported target") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("7"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Byte")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce String to Char single unsupported target") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("A"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      },
      test("coerce String to Char multi-char unsupported target") {
        val sv   = DynamicValue.Primitive(PrimitiveValue.String("AB"))
        val expr = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Literal(sv), "Char")
        assertTrue(expr.eval(intVal).isLeft)
      }
    )
  )
}
