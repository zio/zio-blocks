package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Resolved expression evaluation.
 *
 * Covers:
 *   - Literal expressions
 *   - Identity expression
 *   - Field access
 *   - Composition
 *   - Conditionals
 *   - Collection operations
 *   - Type conversions
 *   - Error handling
 */
object TransformExpressionSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

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
    DynamicValue.Sequence(elements: _*)

  def dynamicSome(value: DynamicValue): DynamicValue =
    DynamicValue.Variant("Some", DynamicValue.Record(("value", value)))

  def dynamicNone: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Record())

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
        val expr  = Resolved.Literal(value)
        assertTrue(expr.evalDynamic == Right(value))
      }
    ),
    suite("Identity expression")(
      test("Identity returns input unchanged") {
        val expr  = Resolved.Identity
        val input = dynamicInt(42)
        assertTrue(expr.evalDynamic(input) == Right(input))
      },
      test("Identity with complex value") {
        val expr  = Resolved.Identity
        val input = dynamicRecord(
          "name"  -> dynamicString("Alice"),
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
        val expr  = Resolved.FieldAccess("name", Resolved.Identity)
        val input = dynamicRecord("name" -> dynamicString("Alice"), "age" -> dynamicInt(30))
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("Alice")))
      },
      test("accesses nested field with composition") {
        val expr  = Resolved.FieldAccess("city", Resolved.FieldAccess("address", Resolved.Identity))
        val input = dynamicRecord(
          "address" -> dynamicRecord("city" -> dynamicString("Boston"))
        )
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("Boston")))
      },
      test("returns error for missing field") {
        val expr  = Resolved.FieldAccess("missing", Resolved.Identity)
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(expr.evalDynamic(input).isLeft)
      },
      test("returns error for non-record input") {
        val expr  = Resolved.FieldAccess("field", Resolved.Identity)
        val input = dynamicInt(42)
        assertTrue(expr.evalDynamic(input).isLeft)
      }
    ),
    suite("Compose expression")(
      test("composes two expressions") {
        // outer(inner(x)) - first inner runs, then outer
        val expr = Resolved.Compose(
          Resolved.FieldAccess("value", Resolved.Identity), // outer
          Resolved.FieldAccess("data", Resolved.Identity)   // inner
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
          case Right(_)  => assertTrue(false)
        }
      }
    ),
    suite("Concat expression")(
      test("concatenates string values") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.Literal.string("Hello"),
            Resolved.Literal.string(" "),
            Resolved.Literal.string("World")
          ),
          ""
        )
        assertTrue(expr.evalDynamic == Right(dynamicString("Hello World")))
      },
      test("concatenates with separator") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.Literal.string("a"),
            Resolved.Literal.string("b"),
            Resolved.Literal.string("c")
          ),
          ","
        )
        assertTrue(expr.evalDynamic == Right(dynamicString("a,b,c")))
      },
      test("concatenates field values") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.FieldAccess("first", Resolved.Identity),
            Resolved.Literal.string(" "),
            Resolved.FieldAccess("last", Resolved.Identity)
          ),
          ""
        )
        val input = dynamicRecord(
          "first" -> dynamicString("John"),
          "last"  -> dynamicString("Doe")
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
          Vector(
            Resolved.Literal(dynamicNone),
            Resolved.Literal(dynamicSome(dynamicInt(42)))
          )
        )
        assertTrue(expr.evalDynamic == Right(dynamicSome(dynamicInt(42))))
      },
      test("returns primary if not None") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Literal(dynamicSome(dynamicInt(1))),
            Resolved.Literal(dynamicSome(dynamicInt(2)))
          )
        )
        assertTrue(expr.evalDynamic == Right(dynamicSome(dynamicInt(1))))
      },
      test("returns fallback if primary is None") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Literal(dynamicNone),
            Resolved.Literal.int(99)
          )
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
        val expr   = Resolved.WrapSome(Resolved.Literal(record))
        assertTrue(expr.evalDynamic == Right(dynamicSome(record)))
      },
      test("wraps input value in Some") {
        val expr  = Resolved.WrapSome(Resolved.Identity)
        val input = dynamicString("hello")
        assertTrue(expr.evalDynamic(input) == Right(dynamicSome(dynamicString("hello"))))
      }
    ),
    suite("Construct expression")(
      test("constructs record from field expressions") {
        val expr = Resolved.Construct(
          Vector(
            "name" -> Resolved.Literal.string("Alice"),
            "age"  -> Resolved.Literal.int(30)
          )
        )
        assertTrue(
          expr.evalDynamic == Right(
            dynamicRecord(
              "name" -> dynamicString("Alice"),
              "age"  -> dynamicInt(30)
            )
          )
        )
      },
      test("constructs record using input values") {
        val expr = Resolved.Construct(
          Vector(
            "doubled"  -> Resolved.Identity,
            "constant" -> Resolved.Literal.int(100)
          )
        )
        // Note: This test shows the construct uses the input for Identity expressions
        val input  = dynamicInt(42)
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
        val expr = Resolved.ConstructSeq(
          Vector(
            Resolved.Literal.int(1),
            Resolved.Literal.int(2),
            Resolved.Literal.int(3)
          )
        )
        assertTrue(
          expr.evalDynamic == Right(
            dynamicSequence(
              dynamicInt(1),
              dynamicInt(2),
              dynamicInt(3)
            )
          )
        )
      },
      test("constructs empty sequence") {
        val expr = Resolved.ConstructSeq(Vector.empty)
        assertTrue(expr.evalDynamic == Right(dynamicSequence()))
      },
      test("constructs heterogeneous sequence") {
        val expr = Resolved.ConstructSeq(
          Vector(
            Resolved.Literal.int(1),
            Resolved.Literal.string("two"),
            Resolved.Literal.boolean(true)
          )
        )
        assertTrue(
          expr.evalDynamic == Right(
            dynamicSequence(
              dynamicInt(1),
              dynamicString("two"),
              dynamicBool(true)
            )
          )
        )
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
        val expr = Resolved.Construct(
          Vector(
            "extracted" -> Resolved.FieldAccess("inner", Resolved.FieldAccess("outer", Resolved.Identity))
          )
        )
        val input = dynamicRecord(
          "outer" -> dynamicRecord("inner" -> dynamicInt(99))
        )
        assertTrue(
          expr.evalDynamic(input) == Right(
            dynamicRecord(
              "extracted" -> dynamicInt(99)
            )
          )
        )
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
          case Right(_)  => assertTrue(false)
        }
      },
      test("construct stops on first error") {
        val expr = Resolved.Construct(
          Vector(
            "good"      -> Resolved.Literal.int(1),
            "bad"       -> Resolved.Fail("field error"),
            "unreached" -> Resolved.Literal.int(3)
          )
        )
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("concat propagates error") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.Literal.string("start"),
            Resolved.Fail("concat error")
          ),
          " "
        )
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("Head expression")(
      test("head returns first element of non-empty sequence") {
        val expr = Resolved.Head(Resolved.Literal(dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))))
        assertTrue(expr.evalDynamic(dynamicInt(0)) == Right(dynamicInt(1)))
      },
      test("head fails on empty sequence") {
        val expr = Resolved.Head(Resolved.Literal(dynamicSequence()))
        assertTrue(expr.evalDynamic(dynamicInt(0)).isLeft)
      },
      test("head fails on non-sequence") {
        val expr = Resolved.Head(Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicInt(42)).isLeft)
      },
      test("head without input fails") {
        val expr = Resolved.Head(Resolved.Identity)
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("JoinStrings expression")(
      test("joins sequence elements with separator") {
        val expr = Resolved.JoinStrings(
          "-",
          Resolved.Literal(dynamicSequence(dynamicString("a"), dynamicString("b"), dynamicString("c")))
        )
        assertTrue(expr.evalDynamic(dynamicInt(0)) == Right(dynamicString("a-b-c")))
      },
      test("joins empty sequence to empty string") {
        val expr = Resolved.JoinStrings(",", Resolved.Literal(dynamicSequence()))
        assertTrue(expr.evalDynamic(dynamicInt(0)) == Right(dynamicString("")))
      },
      test("joins non-string elements using toString") {
        val expr   = Resolved.JoinStrings(",", Resolved.Literal(dynamicSequence(dynamicInt(1), dynamicInt(2))))
        val result = expr.evalDynamic(dynamicInt(0))
        assertTrue(result.isRight)
      },
      test("join fails on non-sequence input") {
        val expr = Resolved.JoinStrings(",", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicInt(42)).isLeft)
      },
      test("join without input fails") {
        val expr = Resolved.JoinStrings(",", Resolved.Identity)
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("Coalesce expression")(
      test("returns first non-None value") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Literal(dynamicNone),
            Resolved.Literal(dynamicSome(dynamicInt(42)))
          )
        )
        assertTrue(expr.evalDynamic == Right(dynamicSome(dynamicInt(42))))
      },
      test("fails when all alternatives are None") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Literal(dynamicNone),
            Resolved.Literal(dynamicNone)
          )
        )
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("fails when empty alternatives") {
        val expr = Resolved.Coalesce(Vector.empty)
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("coalesce with input") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.FieldAccess("missing", Resolved.Identity),
            Resolved.Literal.int(99)
          )
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(99)))
      },
      test("returns first successful non-failing alternative") {
        val expr = Resolved.Coalesce(
          Vector(
            Resolved.Fail("first fails"),
            Resolved.Literal.int(42)
          )
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      }
    ),
    suite("GetOrElse expression")(
      test("extracts value from Some variant") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(dynamicSome(dynamicInt(42))),
          Resolved.Literal.int(0)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      },
      test("returns fallback for None variant") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(dynamicNone),
          Resolved.Literal.int(99)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(99)))
      },
      test("returns fallback for Null") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(DynamicValue.Null),
          Resolved.Literal.int(99)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(99)))
      },
      test("returns non-option value as-is") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal.int(42),
          Resolved.Literal.int(0)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      },
      test("returns fallback when primary fails") {
        val expr = Resolved.GetOrElse(
          Resolved.Fail("primary failed"),
          Resolved.Literal.int(99)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(99)))
      },
      test("works with input for field access") {
        val expr = Resolved.GetOrElse(
          Resolved.FieldAccess("value", Resolved.Identity),
          Resolved.Literal.int(0)
        )
        // GetOrElse extracts the value from Some, so we expect the inner int
        val input = dynamicRecord("value" -> dynamicSome(dynamicInt(42)))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(42)))
      }
    ),
    suite("DefaultValue expression")(
      test("returns value when available") {
        val expr = Resolved.DefaultValue(Right(dynamicInt(42)))
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      },
      test("fails when no default") {
        val expr = Resolved.DefaultValue.noDefault
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("fails with custom message") {
        val expr   = Resolved.DefaultValue.fail("custom error")
        val result = expr.evalDynamic
        assertTrue(result.isLeft && result.swap.getOrElse("").contains("custom error"))
      },
      test("ignores input and returns default") {
        val expr = Resolved.DefaultValue(Right(dynamicInt(42)))
        assertTrue(expr.evalDynamic(dynamicString("ignored")) == Right(dynamicInt(42)))
      }
    ),
    suite("SplitString expression")(
      test("splits string by separator") {
        val expr   = Resolved.SplitString("-", Resolved.Literal.string("a-b-c"))
        val result = expr.evalDynamic(dynamicInt(0))
        assertTrue(result == Right(dynamicSequence(dynamicString("a"), dynamicString("b"), dynamicString("c"))))
      },
      test("handles empty parts from consecutive separators") {
        val expr   = Resolved.SplitString(",", Resolved.Literal.string("a,,b"))
        val result = expr.evalDynamic(dynamicInt(0))
        assertTrue(result == Right(dynamicSequence(dynamicString("a"), dynamicString(""), dynamicString("b"))))
      },
      test("fails on non-string input") {
        val expr = Resolved.SplitString(",", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicInt(42)).isLeft)
      },
      test("without input fails") {
        val expr = Resolved.SplitString(",", Resolved.Identity)
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("OpticAccess expression")(
      test("accesses value at path") {
        val expr  = Resolved.OpticAccess(DynamicOptic.root.field("name"), Resolved.Identity)
        val input = dynamicRecord("name" -> dynamicString("Alice"))
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("Alice")))
      },
      test("without input fails") {
        val expr = Resolved.OpticAccess(DynamicOptic.root.field("name"), Resolved.Identity)
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("UnwrapOption expression")(
      test("extracts Some value") {
        val expr  = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(0))
        val input = DynamicValue.Variant("Some", dynamicInt(42))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(42)))
      },
      test("uses fallback for None") {
        val expr  = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(99))
        val input = DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(99)))
      },
      test("uses fallback for Null") {
        val expr = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(99))
        assertTrue(expr.evalDynamic(DynamicValue.Null) == Right(dynamicInt(99)))
      },
      test("returns non-optional value unchanged") {
        val expr = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(0))
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicInt(42)))
      },
      test("without input fails") {
        val expr = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(0))
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("WrapSome expression")(
      test("wraps value in Some variant") {
        val expr = Resolved.WrapSome(Resolved.Literal.int(42))
        assertTrue(expr.evalDynamic == Right(dynamicSome(dynamicInt(42))))
      },
      test("wraps input using Identity") {
        val expr = Resolved.WrapSome(Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicString("test")) == Right(dynamicSome(dynamicString("test"))))
      }
    ),
    suite("Convert edge cases")(
      test("convert without input fails") {
        val expr = Resolved.Convert("Int", "String", Resolved.Identity)
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("convert Long to String") {
        val expr = Resolved.Convert("Long", "String", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicLong(123L)) == Right(dynamicString("123")))
      },
      test("convert Boolean to String") {
        val expr = Resolved.Convert("Boolean", "String", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicBool(true)) == Right(dynamicString("true")))
      },
      test("convert with inner expression error propagates") {
        val expr = Resolved.Convert("Int", "String", Resolved.Fail("inner error"))
        assertTrue(expr.evalDynamic(dynamicInt(42)).isLeft)
      },
      test("unsupported conversion returns error") {
        val expr = Resolved.Convert("Boolean", "Long", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicBool(true)).isLeft)
      }
    ),
    suite("Compose expression extended")(
      test("compose without input when inner requires input") {
        val expr = Resolved.Compose(Resolved.Literal.int(1), Resolved.Identity)
        assertTrue(expr.evalDynamic.isLeft)
      },
      test("compose two literals") {
        val expr = Resolved.Compose(Resolved.Literal.int(1), Resolved.Literal.int(2))
        assertTrue(expr.evalDynamic == Right(dynamicInt(1)))
      }
    ),
    suite("FieldAccess extended")(
      test("nested field access") {
        val expr  = Resolved.FieldAccess("inner", Resolved.FieldAccess("outer", Resolved.Identity))
        val input = dynamicRecord("outer" -> dynamicRecord("inner" -> dynamicInt(42)))
        assertTrue(expr.evalDynamic(input) == Right(dynamicInt(42)))
      }
    ),
    suite("Concat extended")(
      test("concat with mixed types") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.Literal.string("value: "),
            Resolved.Literal.int(42)
          ),
          ""
        )
        val result = expr.evalDynamic
        assertTrue(result.isRight)
      },
      test("concat with input using FieldAccess") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.FieldAccess("first", Resolved.Identity),
            Resolved.FieldAccess("second", Resolved.Identity)
          ),
          "-"
        )
        val input = dynamicRecord("first" -> dynamicString("a"), "second" -> dynamicString("b"))
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("a-b")))
      }
    ),
    suite("Construct extended")(
      test("construct with input-dependent fields") {
        val expr = Resolved.Construct(
          Vector(
            "copied" -> Resolved.FieldAccess("source", Resolved.Identity)
          )
        )
        val input = dynamicRecord("source" -> dynamicInt(42))
        assertTrue(expr.evalDynamic(input) == Right(dynamicRecord("copied" -> dynamicInt(42))))
      }
    ),
    suite("ConstructSeq extended")(
      test("construct seq with input-dependent elements") {
        val expr = Resolved.ConstructSeq(
          Vector(
            Resolved.FieldAccess("x", Resolved.Identity),
            Resolved.FieldAccess("y", Resolved.Identity)
          )
        )
        val input = dynamicRecord("x" -> dynamicInt(1), "y" -> dynamicInt(2))
        assertTrue(expr.evalDynamic(input) == Right(dynamicSequence(dynamicInt(1), dynamicInt(2))))
      }
    ),
    suite("OpticAccess extended")(
      test("optic access with inner transformation") {
        val expr =
          Resolved.OpticAccess(DynamicOptic.root.field("value"), Resolved.Convert("Int", "String", Resolved.Identity))
        val input = dynamicRecord("value" -> dynamicInt(42))
        assertTrue(expr.evalDynamic(input) == Right(dynamicString("42")))
      }
    ),
    suite("DefaultValue extended")(
      test("fromValue creates successful default") {
        val expr = Resolved.DefaultValue(Right(dynamicInt(100)))
        assertTrue(expr.evalDynamic == Right(dynamicInt(100)))
        assertTrue(expr.evalDynamic(dynamicString("ignored")) == Right(dynamicInt(100)))
      }
    ),
    suite("GetOrElse extended")(
      test("getOrElse with Some missing value field") {
        val expr = Resolved.GetOrElse(
          Resolved.Literal(DynamicValue.Variant("Some", dynamicRecord("other" -> dynamicInt(1)))),
          Resolved.Literal.int(99)
        )
        assertTrue(expr.evalDynamic == Right(dynamicInt(99)))
      }
    )
  )
}
