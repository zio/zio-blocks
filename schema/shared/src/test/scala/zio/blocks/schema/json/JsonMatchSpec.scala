package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

object JsonMatchSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("JsonMatchSpec")(
    suite("Wildcard matching")(
      test("Wildcard matches any Json value") {
        assertTrue(
          JsonMatch.matches(SchemaRepr.Wildcard, Json.String("test")),
          JsonMatch.matches(SchemaRepr.Wildcard, Json.Number(42)),
          JsonMatch.matches(SchemaRepr.Wildcard, Json.Boolean(true)),
          JsonMatch.matches(SchemaRepr.Wildcard, Json.Null),
          JsonMatch.matches(SchemaRepr.Wildcard, Json.Object.empty),
          JsonMatch.matches(SchemaRepr.Wildcard, Json.Array.empty)
        )
      }
    ),
    suite("Primitive matching")(
      test("Primitive(string) matches Json.String") {
        assertTrue(JsonMatch.matches(SchemaRepr.Primitive("string"), Json.String("hello")))
      },
      test("Primitive(string) rejects non-string values") {
        assertTrue(
          !JsonMatch.matches(SchemaRepr.Primitive("string"), Json.Number(42)),
          !JsonMatch.matches(SchemaRepr.Primitive("string"), Json.Boolean(true)),
          !JsonMatch.matches(SchemaRepr.Primitive("string"), Json.Null)
        )
      },
      test("Primitive(int) matches Json.Number") {
        assertTrue(JsonMatch.matches(SchemaRepr.Primitive("int"), Json.Number(42)))
      },
      test("Primitive(long) matches Json.Number") {
        assertTrue(JsonMatch.matches(SchemaRepr.Primitive("long"), Json.Number(1000000L)))
      },
      test("Primitive(double) matches Json.Number") {
        assertTrue(JsonMatch.matches(SchemaRepr.Primitive("double"), Json.Number(3.14)))
      },
      test("Primitive(number) matches Json.Number") {
        assertTrue(JsonMatch.matches(SchemaRepr.Primitive("number"), Json.Number(42)))
      },
      test("Primitive(boolean) matches Json.Boolean") {
        assertTrue(
          JsonMatch.matches(SchemaRepr.Primitive("boolean"), Json.Boolean(true)),
          JsonMatch.matches(SchemaRepr.Primitive("boolean"), Json.Boolean(false))
        )
      },
      test("Primitive(boolean) rejects non-boolean values") {
        assertTrue(
          !JsonMatch.matches(SchemaRepr.Primitive("boolean"), Json.String("true")),
          !JsonMatch.matches(SchemaRepr.Primitive("boolean"), Json.Number(1))
        )
      },
      test("Primitive(null) matches Json.Null") {
        assertTrue(JsonMatch.matches(SchemaRepr.Primitive("null"), Json.Null))
      },
      test("Primitive matching is case-insensitive") {
        assertTrue(
          JsonMatch.matches(SchemaRepr.Primitive("STRING"), Json.String("hello")),
          JsonMatch.matches(SchemaRepr.Primitive("String"), Json.String("hello")),
          JsonMatch.matches(SchemaRepr.Primitive("INT"), Json.Number(42)),
          JsonMatch.matches(SchemaRepr.Primitive("Boolean"), Json.Boolean(true))
        )
      }
    ),
    suite("Record matching")(
      test("Record with single field matches object with that field") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val json    = Json.Object("name" -> Json.String("Alice"))
        assertTrue(JsonMatch.matches(pattern, json))
      },
      test("Record with multiple fields matches object with those fields") {
        val pattern = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
        assertTrue(JsonMatch.matches(pattern, json))
      },
      test("Record pattern allows extra fields in object") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val json    = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30), "active" -> Json.Boolean(true))
        assertTrue(JsonMatch.matches(pattern, json))
      },
      test("Record rejects object missing required field") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val json    = Json.Object("age" -> Json.Number(30))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Record rejects object with field of wrong type") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val json    = Json.Object("name" -> Json.Number(42))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Record rejects non-object values") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        assertTrue(
          !JsonMatch.matches(pattern, Json.String("test")),
          !JsonMatch.matches(pattern, Json.Array.empty)
        )
      },
      test("Empty Record matches any object") {
        val pattern = SchemaRepr.Record(Vector.empty)
        assertTrue(
          JsonMatch.matches(pattern, Json.Object.empty),
          JsonMatch.matches(pattern, Json.Object("x" -> Json.Number(1)))
        )
      }
    ),
    suite("Sequence matching")(
      test("Sequence matches array with matching elements") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val json    = Json.Array(Json.String("a"), Json.String("b"))
        assertTrue(JsonMatch.matches(pattern, json))
      },
      test("Sequence matches empty array") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        assertTrue(JsonMatch.matches(pattern, Json.Array.empty))
      },
      test("Sequence rejects array with non-matching elements") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val json    = Json.Array(Json.String("a"), Json.Number(42))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Sequence rejects non-array values") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        assertTrue(
          !JsonMatch.matches(pattern, Json.String("test")),
          !JsonMatch.matches(pattern, Json.Object.empty)
        )
      }
    ),
    suite("Map matching")(
      test("Map with string keys matches object with matching values") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val json    = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
        assertTrue(JsonMatch.matches(pattern, json))
      },
      test("Map with string keys matches empty object") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        assertTrue(JsonMatch.matches(pattern, Json.Object.empty))
      },
      test("Map with non-string key pattern rejects objects") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("int"), SchemaRepr.Primitive("string"))
        val json    = Json.Object("a" -> Json.String("test"))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Map rejects object with non-matching values") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val json    = Json.Object("a" -> Json.String("not a number"))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Map with Wildcard key pattern matches object") {
        val pattern = SchemaRepr.Map(SchemaRepr.Wildcard, SchemaRepr.Primitive("int"))
        val json    = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
        assertTrue(JsonMatch.matches(pattern, json))
      },
      test("Map with Wildcard key pattern matches empty object") {
        val pattern = SchemaRepr.Map(SchemaRepr.Wildcard, SchemaRepr.Primitive("int"))
        assertTrue(JsonMatch.matches(pattern, Json.Object.empty))
      },
      test("Map with Wildcard key but mismatched values rejects object") {
        val pattern = SchemaRepr.Map(SchemaRepr.Wildcard, SchemaRepr.Primitive("int"))
        val json    = Json.Object("a" -> Json.String("not a number"))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Map with Record key pattern rejects objects") {
        val pattern = SchemaRepr.Map(SchemaRepr.Record(Vector.empty), SchemaRepr.Primitive("int"))
        val json    = Json.Object("a" -> Json.Number(1))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Map with Optional key pattern rejects objects") {
        val pattern = SchemaRepr.Map(SchemaRepr.Optional(SchemaRepr.Primitive("string")), SchemaRepr.Primitive("int"))
        val json    = Json.Object("a" -> Json.Number(1))
        assertTrue(!JsonMatch.matches(pattern, json))
      },
      test("Map with Sequence key pattern rejects objects") {
        val pattern = SchemaRepr.Map(SchemaRepr.Sequence(SchemaRepr.Primitive("string")), SchemaRepr.Primitive("int"))
        val json    = Json.Object("a" -> Json.Number(1))
        assertTrue(!JsonMatch.matches(pattern, json))
      }
    ),
    suite("Optional matching")(
      test("Optional matches Null") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Primitive("string"))
        assertTrue(JsonMatch.matches(pattern, Json.Null))
      },
      test("Optional matches inner type") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Primitive("string"))
        assertTrue(JsonMatch.matches(pattern, Json.String("hello")))
      },
      test("Optional rejects non-matching non-null values") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Primitive("string"))
        assertTrue(!JsonMatch.matches(pattern, Json.Number(42)))
      }
    ),
    suite("Nominal matching")(
      test("Nominal always returns false for JSON") {
        val pattern = SchemaRepr.Nominal("Person")
        assertTrue(
          !JsonMatch.matches(pattern, Json.Object("name" -> Json.String("Alice"))),
          !JsonMatch.matches(pattern, Json.String("test")),
          !JsonMatch.matches(pattern, Json.Null)
        )
      }
    ),
    suite("Variant matching")(
      test("Variant matches if value matches any case pattern") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        // JSON doesn't have tagged variants, but we try to match against any case pattern
        assertTrue(
          JsonMatch.matches(pattern, Json.Number(42)),
          JsonMatch.matches(pattern, Json.String("hello"))
        )
      },
      test("Variant rejects if value matches no case pattern") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        assertTrue(!JsonMatch.matches(pattern, Json.Boolean(true)))
      }
    ),
    suite("Nested patterns")(
      test("Nested Record in Record") {
        val pattern = SchemaRepr.Record(
          Vector(
            "person" -> SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
          )
        )
        val json = Json.Object("person" -> Json.Object("name" -> Json.String("Alice")))
        assertTrue(JsonMatch.matches(pattern, json))
      },
      test("Sequence of Records") {
        val pattern = SchemaRepr.Sequence(
          SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        )
        val json = Json.Array(
          Json.Object("name" -> Json.String("Alice")),
          Json.Object("name" -> Json.String("Bob"))
        )
        assertTrue(JsonMatch.matches(pattern, json))
      }
    )
  )
}
