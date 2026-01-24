package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

object JsonSchemaCombinatorSpec extends SchemaBaseSpec {

  private val stringSchema  = JsonSchema.string()
  private val integerSchema = JsonSchema.integer()
  private val booleanSchema = JsonSchema.boolean
  private val nullSchema    = JsonSchema.`null`

  private val minLength3 = JsonSchema.string(minLength = NonNegativeInt(3))
  private val maxLength5 = JsonSchema.string(maxLength = NonNegativeInt(5))

  private val positive    = JsonSchema.number(exclusiveMinimum = Some(BigDecimal(0)))
  private val lessThan100 = JsonSchema.number(exclusiveMaximum = Some(BigDecimal(100)))

  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaCombinatorSpec")(
    suite("&& (allOf) combinator")(
      test("validates if both schemas pass") {
        val combined = minLength3 && maxLength5
        assertTrue(
          combined.conforms(Json.str("abc")),
          combined.conforms(Json.str("abcd")),
          combined.conforms(Json.str("abcde"))
        )
      },
      test("fails if first schema fails") {
        val combined = minLength3 && maxLength5
        assertTrue(!combined.conforms(Json.str("ab")))
      },
      test("fails if second schema fails") {
        val combined = minLength3 && maxLength5
        assertTrue(!combined.conforms(Json.str("abcdef")))
      },
      test("fails if both schemas fail") {
        val combined = minLength3 && maxLength5
        assertTrue(!combined.conforms(Json.number(42)))
      },
      test("&& with True is identity") {
        val schema = stringSchema && JsonSchema.True
        assertTrue(
          schema.conforms(Json.str("hello")),
          !schema.conforms(Json.number(42))
        )
      },
      test("&& with False always fails") {
        val schema = stringSchema && JsonSchema.False
        assertTrue(
          !schema.conforms(Json.str("hello")),
          !schema.conforms(Json.number(42))
        )
      },
      test("multiple schemas via &&") {
        val schema = positive && lessThan100
        assertTrue(
          schema.conforms(Json.number(50)),
          !schema.conforms(Json.number(0)),
          !schema.conforms(Json.number(-5)),
          !schema.conforms(Json.number(100)),
          !schema.conforms(Json.number(150))
        )
      }
    ),
    suite("|| (anyOf) combinator")(
      test("validates if either schema passes") {
        val combined = stringSchema || integerSchema
        assertTrue(
          combined.conforms(Json.str("hello")),
          combined.conforms(Json.number(42))
        )
      },
      test("validates if first schema passes") {
        val combined = stringSchema || integerSchema
        assertTrue(combined.conforms(Json.str("hello")))
      },
      test("validates if second schema passes") {
        val combined = stringSchema || integerSchema
        assertTrue(combined.conforms(Json.number(42)))
      },
      test("fails if neither schema passes") {
        val combined = stringSchema || integerSchema
        assertTrue(!combined.conforms(Json.True))
      },
      test("|| with True always passes") {
        val schema = stringSchema || JsonSchema.True
        assertTrue(
          schema.conforms(Json.str("hello")),
          schema.conforms(Json.number(42)),
          schema.conforms(Json.True)
        )
      },
      test("|| with False is identity") {
        val schema = stringSchema || JsonSchema.False
        assertTrue(
          schema.conforms(Json.str("hello")),
          !schema.conforms(Json.number(42))
        )
      },
      test("multiple schemas via ||") {
        val schema = stringSchema || integerSchema || booleanSchema
        assertTrue(
          schema.conforms(Json.str("hello")),
          schema.conforms(Json.number(42)),
          schema.conforms(Json.True),
          !schema.conforms(Json.Null)
        )
      }
    ),
    suite("! (not) combinator")(
      test("validates if schema fails") {
        val notString = !stringSchema
        assertTrue(
          notString.conforms(Json.number(42)),
          notString.conforms(Json.True),
          notString.conforms(Json.Null)
        )
      },
      test("fails if schema passes") {
        val notString = !stringSchema
        assertTrue(!notString.conforms(Json.str("hello")))
      },
      test("!True is False") {
        val notTrue = !JsonSchema.True
        assertTrue(
          !notTrue.conforms(Json.str("hello")),
          !notTrue.conforms(Json.number(42))
        )
      },
      test("!False is True") {
        val notFalse = !JsonSchema.False
        assertTrue(
          notFalse.conforms(Json.str("hello")),
          notFalse.conforms(Json.number(42))
        )
      },
      test("double negation is identity") {
        val doubleNot = !(!stringSchema)
        assertTrue(
          doubleNot.conforms(Json.str("hello")),
          !doubleNot.conforms(Json.number(42))
        )
      },
      test("not with constraint schema") {
        val notPositive = !positive
        assertTrue(
          notPositive.conforms(Json.number(0)),
          notPositive.conforms(Json.number(-5)),
          !notPositive.conforms(Json.number(5))
        )
      }
    ),
    suite("Associativity of &&")(
      test("(a && b) && c is equivalent to a && (b && c) for validation") {
        val a = JsonSchema.string(minLength = NonNegativeInt(2))
        val b = JsonSchema.string(maxLength = NonNegativeInt(10))
        val c = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))

        val leftAssoc  = (a && b) && c
        val rightAssoc = a && (b && c)

        val testCases = List(
          Json.str("abc"),
          Json.str("a"),
          Json.str("abcdefghijk"),
          Json.str("ABC"),
          Json.number(42)
        )

        assertTrue(
          testCases.forall(j => leftAssoc.conforms(j) == rightAssoc.conforms(j))
        )
      }
    ),
    suite("Associativity of ||")(
      test("(a || b) || c is equivalent to a || (b || c) for validation") {
        val a = stringSchema
        val b = integerSchema
        val c = booleanSchema

        val leftAssoc  = (a || b) || c
        val rightAssoc = a || (b || c)

        val testCases = List(
          Json.str("hello"),
          Json.number(42),
          Json.True,
          Json.Null,
          Json.arr()
        )

        assertTrue(
          testCases.forall(j => leftAssoc.conforms(j) == rightAssoc.conforms(j))
        )
      }
    ),
    suite("Commutativity for validation purposes")(
      test("a && b is equivalent to b && a for validation") {
        val a = minLength3
        val b = maxLength5

        val ab = a && b
        val ba = b && a

        val testCases = List(
          Json.str("ab"),
          Json.str("abc"),
          Json.str("abcde"),
          Json.str("abcdef"),
          Json.number(42)
        )

        assertTrue(
          testCases.forall(j => ab.conforms(j) == ba.conforms(j))
        )
      },
      test("a || b is equivalent to b || a for validation") {
        val a = stringSchema
        val b = integerSchema

        val ab = a || b
        val ba = b || a

        val testCases = List(
          Json.str("hello"),
          Json.number(42),
          Json.True,
          Json.Null
        )

        assertTrue(
          testCases.forall(j => ab.conforms(j) == ba.conforms(j))
        )
      }
    ),
    suite("De Morgan's laws")(
      test("!(a && b) is equivalent to !a || !b for validation") {
        val a = stringSchema
        val b = minLength3

        val lhs = !(a && b)
        val rhs = (!a) || (!b)

        val testCases = List(
          Json.str("hello"),
          Json.str("ab"),
          Json.number(42),
          Json.True,
          Json.Null
        )

        assertTrue(
          testCases.forall(j => lhs.conforms(j) == rhs.conforms(j))
        )
      },
      test("!(a || b) is equivalent to !a && !b for validation") {
        val a = stringSchema
        val b = integerSchema

        val lhs = !(a || b)
        val rhs = (!a) && (!b)

        val testCases = List(
          Json.str("hello"),
          Json.number(42),
          Json.True,
          Json.Null,
          Json.arr()
        )

        assertTrue(
          testCases.forall(j => lhs.conforms(j) == rhs.conforms(j))
        )
      }
    ),
    suite("Complex combinator expressions")(
      test("string or (integer and positive)") {
        val schema = stringSchema || (integerSchema && positive)
        assertTrue(
          schema.conforms(Json.str("hello")),
          schema.conforms(Json.number(5)),
          !schema.conforms(Json.number(-5)),
          !schema.conforms(Json.number(3.14)),
          !schema.conforms(Json.True)
        )
      },
      test("not string and not integer") {
        val schema = (!stringSchema) && (!integerSchema)
        assertTrue(
          schema.conforms(Json.True),
          schema.conforms(Json.Null),
          schema.conforms(Json.arr()),
          !schema.conforms(Json.str("hello")),
          !schema.conforms(Json.number(42))
        )
      },
      test("(positive or null) and (lessThan100 or null)") {
        val positiveOrNull    = positive || nullSchema
        val lessThan100OrNull = lessThan100 || nullSchema
        val schema            = positiveOrNull && lessThan100OrNull
        assertTrue(
          schema.conforms(Json.number(50)),
          schema.conforms(Json.Null),
          !schema.conforms(Json.number(0)),
          !schema.conforms(Json.number(150)),
          !schema.conforms(Json.str("hello"))
        )
      },
      test("triple negation") {
        val tripleNot = !(!(!stringSchema))
        assertTrue(
          !tripleNot.conforms(Json.str("hello")),
          tripleNot.conforms(Json.number(42))
        )
      }
    ),
    suite("Combinator identity laws")(
      test("schema && True == schema (for validation)") {
        val schema    = minLength3
        val combined  = schema && JsonSchema.True
        val testCases = List(Json.str("ab"), Json.str("abc"), Json.number(42))
        assertTrue(
          testCases.forall(j => combined.conforms(j) == schema.conforms(j))
        )
      },
      test("schema || False == schema (for validation)") {
        val schema    = minLength3
        val combined  = schema || JsonSchema.False
        val testCases = List(Json.str("ab"), Json.str("abc"), Json.number(42))
        assertTrue(
          testCases.forall(j => combined.conforms(j) == schema.conforms(j))
        )
      },
      test("schema && False == False") {
        val combined = stringSchema && JsonSchema.False
        assertTrue(
          !combined.conforms(Json.str("hello")),
          !combined.conforms(Json.number(42))
        )
      },
      test("schema || True == True") {
        val combined = stringSchema || JsonSchema.True
        assertTrue(
          combined.conforms(Json.str("hello")),
          combined.conforms(Json.number(42)),
          combined.conforms(Json.Null)
        )
      }
    ),
    suite("Combinator absorption laws")(
      test("a && (a || b) == a (for validation)") {
        val a         = stringSchema
        val b         = integerSchema
        val lhs       = a && (a || b)
        val testCases = List(Json.str("hello"), Json.number(42), Json.True)
        assertTrue(
          testCases.forall(j => lhs.conforms(j) == a.conforms(j))
        )
      },
      test("a || (a && b) == a (for validation)") {
        val a         = stringSchema
        val b         = minLength3
        val lhs       = a || (a && b)
        val testCases = List(Json.str("hello"), Json.str("ab"), Json.number(42))
        assertTrue(
          testCases.forall(j => lhs.conforms(j) == a.conforms(j))
        )
      }
    )
  )
}
