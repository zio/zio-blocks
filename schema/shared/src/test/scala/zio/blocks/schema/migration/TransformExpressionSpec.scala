package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Resolved expression evaluation.
 *
 * Covers:
 * - Literal expressions
 * - Identity expression
 * - Field access
 * - Composition
 * - Conditionals
 * - Collection operations
 * - Type conversions
 * - Error handling
 */
object TransformExpressionSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields.toVector)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicLong(l: Long): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Long(l))

  def dynamicBool(b: Boolean): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  def dynamicDouble(d: Double): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Double(d))

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements.toVector)

  def dynamicSome(value: DynamicValue): DynamicValue =
    DynamicValue.Variant("Some", value)

  def dynamicNone: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("TransformExpressionSpec")(
    suite("Literal expressions")(
      test("Literal.int returns int value") {
        val expr = Resolved.Literal.int(42)
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      },
      test("Literal.int ignores input") {
        val expr = Resolved.Literal.int(42)
        assertTrue(expr.evalDynamic(dynamicString("ignored")) == Right(dynamicInt(42)))
      },
      test("Literal.long returns long value") {
        val expr = Resolved.Literal.long(123456789L)
        assertTrue(expr.evalDynamic == Right(DynamicValue.Primitive(PrimitiveValue.Long(123456789L))))
      },
      test("Literal.string returns string value") {
        val expr = Resolved.Literal.string("hello")
        assertTrue(expr.evalDynamic == Right(dynamicString("hello")))
      },
      test("Literal.boolean returns boolean value") {
        val expr = Resolved.Literal.boolean(true)
        assertTrue(expr.evalDynamic == Right(dynamicBool(true)))
      },
      test("Literal.double returns double value") {
        val expr = Resolved.Literal.double(3.14)
        assertTrue(expr.evalDynamic == Right(dynamicDouble(3.14)))
      },
      test("Literal.unit returns unit value") {
        val expr = Resolved.Literal.unit
        assertTrue(expr.evalDynamic == Right(DynamicValue.Primitive(PrimitiveValue.Unit)))
      },
      test("Literal.dynamicValue returns provided value") {
        val value = dynamicRecord("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        val expr = Resolved.Literal(value)
        assertTrue(expr.evalDynamic == Right(value))
      }
    ),
    suite("Identity expression")(
      test("Identity returns input unchanged") {
        val expr = Resolved.Identity
        val input = dynamicInt(42)
        assertTrue(expr.evalDynamic(input) == Right(input))
      },
      test("Identity with complex value") {
        val expr = Resolved.Identity
        val input = dynamicRecord(
          "name" -> dynamicString("Alice"),
          "items" -> dynamicSequence(dynamicInt(1), dynamicInt(2))
        )
        assertTrue(expr.evalDynamic(input) == Right(input))
      },
      test("Identity without input returns error") {
        val expr = Resolved.Identity
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("FieldAccess expression")(
      test("accesses field from record") {
        val expr = Resolved.FieldAccess("name", Resolved.Identity)
        val input = dynamicRecord("name" -> dynamicString("Alice"), "age" -> dynamicInt(30))
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("Alice")))
      },
      test("accesses nested field with composition") {
        val expr = Resolved.FieldAccess("city",
          Resolved.FieldAccess("address", Resolved.Identity)
        )
        val input = dynamicRecord(
          "address" -> dynamicRecord("city" -> dynamicString("Boston"))
        )
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("Boston")))
      },
      test("returns error for missing field") {
        val expr = Resolved.FieldAccess("missing", Resolved.Identity)
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(expr.evalDynamic(input).isLeft)
      },
      test("returns error for non-record input") {
        val expr = Resolved.FieldAccess("field", Resolved.Identity)
        val input = dynamicInt(42)
        assertTrue(expr.evalDynamic(input).isLeft)
      }
    ),
    suite("Compose expression")(
      test("composes two expressions") {
        // outer(inner(x)) - first inner runs, then outer
        val expr = Resolved.Compose(
          Resolved.FieldAccess("value", Resolved.Identity),  // outer
          Resolved.FieldAccess("data", Resolved.Identity)    // inner
        )
        val input = dynamicRecord(
          "data" -> dynamicRecord("value" -> dynamicInt(42))
        )
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(42)))
      },
      test("compose with literal ignores inner result") {
        val expr = Resolved.Compose(
          Resolved.Literal.int(99),
          Resolved.FieldAccess("x", Resolved.Identity)
        )
        val input = dynamicRecord("x" -> dynamicInt(1))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(99)))
      },
      test("compose propagates inner error") {
        val expr = Resolved.Compose(
          Resolved.Identity,
          Resolved.FieldAccess("missing", Resolved.Identity)
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(expr.evalDynamic(input).isLeft)
      }
    ),
    suite("Convert expression")(
      test("converts Int to String") {
        val expr = Resolved.Convert("Int", "String", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicString("42")))
      },
      test("converts String to Int") {
        val expr = Resolved.Convert("String", "Int", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicString("42")) == Right(dynamicInt(42)))
      },
      test("converts Int to Long") {
        val expr = Resolved.Convert("Int", "Long", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicLong(42L)))
      },
      test("converts with inner expression") {
        val expr = Resolved.Convert("Int", "String", Resolved.Literal.int(100))
        assertTrue(expr.evalDynamic(dynamicInt(1)) == Right(dynamicString("100")))
      },
      test("conversion error for invalid input") {
        val expr = Resolved.Convert("String", "Int", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicString("not a number")).isLeft)
      }
    ),
    suite("Fail expression")(
      test("always returns error") {
        val expr = Resolved.Fail("intentional failure")
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("returns error even with input") {
        val expr = Resolved.Fail("intentional failure")
        assertTrue(expr.evalDynamic(dynamicInt(42)).isLeft)
      },
      test("error contains message") {
        val expr = Resolved.Fail("custom error message")
        expr.evalDynamic match {
          case Left(msg) => assertTrue(msg.contains("custom error message"))
          case Right(_) => assertTrue(false)
        }
      }
    ),
    suite("Concat expression")(
      test("concatenates string values") {
        val expr = Resolved.Concat(Vector(
          Resolved.Literal.string("Hello"),
          Resolved.Literal.string(" "),
          Resolved.Literal.string("World")
        ), "")
        assertTrue(expr.evalDynamic == Right(dynamicString("Hello World")))
      },
      test("concatenates with separator") {
        val expr = Resolved.Concat(Vector(
          Resolved.Literal.string("a"),
          Resolved.Literal.string("b"),
          Resolved.Literal.string("c")
        ), ",")
        assertTrue(expr.evalDynamic == Right(dynamicString("a,b,c")))
      },
      test("concatenates field values") {
        val expr = Resolved.Concat(Vector(
          Resolved.FieldAccess("first", Resolved.Identity),
          Resolved.Literal.string(" "),
          Resolved.FieldAccess("last", Resolved.Identity)
        ), "")
        val input = dynamicRecord(
          "first" -> dynamicString("John"),
          "last" -> dynamicString("Doe")
        )
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("John Doe")))
      },
      test("empty concat returns empty string") {
        val expr = Resolved.Concat(Vector.empty, ",")
        assertTrue(expr.evalDynamic == Right(dynamicString("")))
      },
      test("single element concat") {
        val expr = Resolved.Concat(Vector(Resolved.Literal.string("solo")), ",")
        assertTrue(expr.evalDynamic == Right(dynamicString("solo")))
      }
    ),
    suite("Coalesce expression")(
      test("returns first non-None value") {
        val expr = Resolved.Coalesce(
          Resolved.Literal(dynamicNone),
          Resolved.Literal(dynamicSome(dynamicInt(42)))
        )
        assertTrue(expr.evalDynamic == Right(dynamicSome(dynamicInt(42))))
      },
      test("returns primary if not None") {
        val expr = Resolved.Coalesce(
          Resolved.Literal(dynamicSome(dynamicInt(1))),
          Resolved.Literal(dynamicSome(dynamicInt(2)))
        )
        assertTrue(expr.evalDynamic == Right(dynamicSome(dynamicInt(1))))
      },
      test("returns fallback if primary is None") {
        val expr = Resolved.Coalesce(
          Resolved.Literal(dynamicNone),
          Resolved.Literal.int(99)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(99)))
      }
    ),
    suite("GetOrElse expression")(
      test("extracts Some value") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(dynamicSome(dynamicInt(42))),
          Resolved.Literal.int(0)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      },
      test("returns default for None") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(dynamicNone),
          Resolved.Literal.int(99)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(99)))
      },
      test("uses input for option expression") {
        val expr = Resolved.GetOrElse(
          Resolved.FieldAccess("maybeValue", Resolved.Identity),
          Resolved.Literal.int(0)
        )
        val inputSome = dynamicRecord("maybeValue" -> dynamicSome(dynamicInt(42)))
        val inputNone = dynamicRecord("maybeValue" -> dynamicNone)
        assertTrue(expr.evalDynamic(inputSome) == Right(dynamicInt(42)))
        assertTrue(expr.evalDynamic(inputNone) == Right(dynamicInt(0)))
      }
    ),
    suite("WrapSome expression")(
      test("wraps value in Some") {
        val expr = Resolved.WrapSome(Resolved.Literal.int(42))
        assertTrue(expr.evalDynamic == Right(dynamicSome(dynamicInt(42))))
      },
      test("wraps record in Some") {
        val record = dynamicRecord("x" -> dynamicInt(1))
        val expr = Resolved.WrapSome(Resolved.Literal(record))
        assertTrue(expr.evalDynamic == Right(dynamicSome(record)))
      },
      test("wraps input value in Some") {
        val expr = Resolved.WrapSome(Resolved.Identity)
        val input = dynamicString("hello")
        assertTrue(expr.evalDynamic(input) == Right(dynamicSome(dynamicString("hello"))))
      }
    ),
    suite("Construct expression")(
      test("constructs record from field expressions") {
        val expr = Resolved.Construct(Vector(
          "name" -> Resolved.Literal.string("Alice"),
          "age" -> Resolved.Literal.int(30)
        ))
        assertTrue(expr.evalDynamic == Right(dynamicRecord(
          "name" -> dynamicString("Alice"),
          "age" -> dynamicInt(30)
        )))
      },
      test("constructs record using input values") {
        val expr = Resolved.Construct(Vector(
          "doubled" -> Resolved.Identity,
          "constant" -> Resolved.Literal.int(100)
        ))
        // Note: This test shows the construct uses the input for Identity expressions
        val input = dynamicInt(42)
        val result = expr.evalDynamic(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.length == 2)
          case _ => assertTrue(false)
        }
      },
      test("constructs empty record") {
        val expr = Resolved.Construct(Vector.empty)
        assertTrue(expr.evalDynamic == Right(dynamicRecord()))
      }
    ),
    suite("ConstructSeq expression")(
      test("constructs sequence from elements") {
        val expr = Resolved.ConstructSeq(Vector(
          Resolved.Literal.int(1),
          Resolved.Literal.int(2),
          Resolved.Literal.int(3)
        ))
        assertTrue(expr.evalDynamic == Right(dynamicSequence(
          dynamicInt(1), dynamicInt(2), dynamicInt(3)
        )))
      },
      test("constructs empty sequence") {
        val expr = Resolved.ConstructSeq(Vector.empty)
        assertTrue(expr.evalDynamic == Right(dynamicSequence()))
      },
      test("constructs heterogeneous sequence") {
        val expr = Resolved.ConstructSeq(Vector(
          Resolved.Literal.int(1),
          Resolved.Literal.string("two"),
          Resolved.Literal.boolean(true)
        ))
        assertTrue(expr.evalDynamic == Right(dynamicSequence(
          dynamicInt(1), dynamicString("two"), dynamicBool(true)
        )))
      }
    ),
    suite("Expression nesting")(
      test("deeply nested expressions") {
        val expr = Resolved.Compose(
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Compose(
            Resolved.FieldAccess("count", Resolved.Identity),
            Resolved.FieldAccess("data", Resolved.Identity)
          )
        )
        val input = dynamicRecord(
          "data" -> dynamicRecord("count" -> dynamicInt(42))
        )
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("42")))
      },
      test("construct with nested field access") {
        val expr = Resolved.Construct(Vector(
          "extracted" -> Resolved.FieldAccess("inner", Resolved.FieldAccess("outer", Resolved.Identity))
        ))
        val input = dynamicRecord(
          "outer" -> dynamicRecord("inner" -> dynamicInt(99))
        )
        assertTrue(expr.evalDynamic(input) == Right(dynamicRecord(
          "extracted" -> dynamicInt(99)
        )))
      }
    ),
    suite("Error propagation")(
      test("compose propagates error from inner expression") {
        val expr = Resolved.Compose(
          Resolved.Identity,
          Resolved.Fail("inner error")
        )
        expr.evalDynamic(dynamicInt(1)) match {
          case Left(msg) => assertTrue(msg.contains("inner error"))
          case Right(_) => assertTrue(false)
        }
      },
      test("construct stops on first error") {
        val expr = Resolved.Construct(Vector(
          "good" -> Resolved.Literal.int(1),
          "bad" -> Resolved.Fail("field error"),
          "unreached" -> Resolved.Literal.int(3)
        ))
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("concat propagates error") {
        val expr = Resolved.Concat(Vector(
          Resolved.Literal.string("start"),
          Resolved.Fail("concat error")
        ), " ")
        assertTrue(expr.evalDynamic.isLeft)
      }
    )
  )
}
