package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object DynamicSchemaExprSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicSchemaExprSpec")(
    suite("Literal")(
      test("evaluates to the literal value") {
        val expr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.String("ignored")))
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      }
    ),
    suite("Path navigation")(
      test("navigates to a simple field") {
        val record = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))
        val expr = DynamicSchemaExpr.Path(DynamicOptic.root.field("name"))
        val result = expr.eval(record)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      },
      test("navigates to a nested field") {
        val record = DynamicValue.Record(Vector(
          "person" -> DynamicValue.Record(Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob"))
          ))
        ))
        val expr = DynamicSchemaExpr.Path(DynamicOptic.root.field("person").field("name"))
        val result = expr.eval(record)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("Bob"))))
      },
      test("returns error for non-existent path") {
        val record = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        ))
        val expr = DynamicSchemaExpr.Path(DynamicOptic.root.field("missing"))
        val result = expr.eval(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Logical operations")(
      test("NOT negates boolean value") {
        val expr = DynamicSchemaExpr.Not(DynamicSchemaExpr.Literal(
          DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        ))
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("AND combines two booleans") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.LogicalOperator.And
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      },
      test("OR combines two booleans") {
        val expr = DynamicSchemaExpr.Logical(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          DynamicSchemaExpr.LogicalOperator.Or
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      }
    ),
    suite("Relational operations")(
      test("LessThan compares values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(10))),
          DynamicSchemaExpr.RelationalOperator.LessThan
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("Equal compares values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
          DynamicSchemaExpr.RelationalOperator.Equal
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("NotEqual compares values") {
        val expr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.RelationalOperator.NotEqual
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      }
    ),
    suite("Arithmetic operations")(
      test("Add integers") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Int(8))))
      },
      test("Subtract longs") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(10L))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(4L))),
          DynamicSchemaExpr.ArithmeticOperator.Subtract
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Long(6L))))
      },
      test("Multiply doubles") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(2.5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(4.0))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Double(10.0))))
      },
      test("fails for incompatible numeric types") {
        val expr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(3L))),
          DynamicSchemaExpr.ArithmeticOperator.Add
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))).isLeft)
      }
    ),
    suite("String operations")(
      test("StringConcat concatenates strings") {
        val expr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello, "))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("World!")))
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.String("Hello, World!"))))
      },
      test("StringLength returns string length") {
        val expr = DynamicSchemaExpr.StringLength(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello")))
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Int(5))))
      }
    ),
    suite("Type coercion")(
      test("CoercePrimitive converts Int to String") {
        val expr = DynamicSchemaExpr.CoercePrimitive(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          "String"
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("CoercePrimitive converts String to Int") {
        val expr = DynamicSchemaExpr.CoercePrimitive(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("123"))),
          "Int"
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Int(123))))
      },
      test("CoercePrimitive fails for invalid conversion") {
        val expr = DynamicSchemaExpr.CoercePrimitive(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("not a number"))),
          "Int"
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))).isLeft)
      },
      test("CoercePrimitive converts Int to Long") {
        val expr = DynamicSchemaExpr.CoercePrimitive(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(100))),
          "Long"
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Long(100L))))
      },
      test("CoercePrimitive converts Boolean to String") {
        val expr = DynamicSchemaExpr.CoercePrimitive(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          "String"
        )
        assertTrue(expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.String("true"))))
      }
    ),
    suite("Composition")(
      test("&& composes expressions with AND") {
        val left = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        val right = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        val composed = left && right
        assertTrue(composed.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      },
      test("|| composes expressions with OR") {
        val left = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
        val right = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        val composed = left || right
        assertTrue(composed.eval(DynamicValue.Primitive(PrimitiveValue.Int(0))) ==
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      }
    )
  )
}
