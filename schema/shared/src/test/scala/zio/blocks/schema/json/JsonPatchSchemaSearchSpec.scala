package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.patch.PatchMode
import zio.test._

object JsonPatchSchemaSearchSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatch SchemaSearch")(
    test("applies Set to all matching number values in an object") {
      val original = new Json.Object(
        Chunk(
          "a" -> new Json.Number(BigDecimal(1)),
          "b" -> new Json.Number(BigDecimal(2)),
          "c" -> new Json.String("hello")
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(99)))
      )
      val result = patch(original)

      val expected = new Json.Object(
        Chunk(
          "a" -> new Json.Number(BigDecimal(99)),
          "b" -> new Json.Number(BigDecimal(99)),
          "c" -> new Json.String("hello")
        )
      )
      assertTrue(result == Right(expected))
    },
    test("finds and patches nested matches") {
      val original = new Json.Object(
        Chunk(
          "outer" -> new Json.Object(
            Chunk(
              "inner" -> new Json.Object(
                Chunk(
                  "value" -> new Json.Number(BigDecimal(42))
                )
              )
            )
          ),
          "top" -> new Json.Number(BigDecimal(1))
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(0)))
      )
      val result = patch(original)

      val expected = new Json.Object(
        Chunk(
          "outer" -> new Json.Object(
            Chunk(
              "inner" -> new Json.Object(
                Chunk(
                  "value" -> new Json.Number(BigDecimal(0))
                )
              )
            )
          ),
          "top" -> new Json.Number(BigDecimal(0))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("finds matches in array elements") {
      val original = new Json.Array(
        Chunk(
          new Json.Number(BigDecimal(1)),
          new Json.Number(BigDecimal(2)),
          new Json.String("skip"),
          new Json.Number(BigDecimal(3))
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(100)))
      )
      val result = patch(original)

      val expected = new Json.Array(
        Chunk(
          new Json.Number(BigDecimal(101)),
          new Json.Number(BigDecimal(102)),
          new Json.String("skip"),
          new Json.Number(BigDecimal(103))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("finds matches in string values") {
      val original = new Json.Object(
        Chunk(
          "name"  -> new Json.String("Alice"),
          "count" -> new Json.Number(BigDecimal(5)),
          "label" -> new Json.String("test")
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("string")),
        JsonPatch.Op.Set(new Json.String("REDACTED"))
      )
      val result = patch(original)

      val expected = new Json.Object(
        Chunk(
          "name"  -> new Json.String("REDACTED"),
          "count" -> new Json.Number(BigDecimal(5)),
          "label" -> new Json.String("REDACTED")
        )
      )
      assertTrue(result == Right(expected))
    },
    test("matches record pattern (structural) on JSON objects") {
      val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))

      val original = new Json.Array(
        Chunk(
          new Json.Object(Chunk("name" -> new Json.String("Alice"), "age" -> new Json.Number(BigDecimal(30)))),
          new Json.Object(Chunk("name" -> new Json.String("Bob"), "age" -> new Json.Number(BigDecimal(25)))),
          new Json.Object(Chunk("title" -> new Json.String("Book")))
        )
      )
      val nestedPatch = JsonPatch(
        Chunk.single(
          JsonPatch.JsonPatchOp(
            DynamicOptic.root.field("name"),
            JsonPatch.Op.Set(new Json.String("REDACTED"))
          )
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(pattern),
        JsonPatch.Op.Nested(nestedPatch)
      )
      val result = patch(original)

      val expected = new Json.Array(
        Chunk(
          new Json.Object(Chunk("name" -> new Json.String("REDACTED"), "age" -> new Json.Number(BigDecimal(30)))),
          new Json.Object(Chunk("name" -> new Json.String("REDACTED"), "age" -> new Json.Number(BigDecimal(25)))),
          new Json.Object(Chunk("title" -> new Json.String("Book")))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("matches root if root matches pattern") {
      val original = new Json.Number(BigDecimal(42))
      val patch    = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(100)))
      )
      val result = patch(original)

      assertTrue(result == Right(new Json.Number(BigDecimal(100))))
    },
    test("returns unchanged value when zero matches (Lenient mode)") {
      val original = new Json.Object(
        Chunk(
          "a" -> new Json.String("hello"),
          "b" -> new Json.String("world")
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(0)))
      )
      val result = patch(original, PatchMode.Lenient)

      assertTrue(result == Right(original))
    },
    test("fails on zero matches in Strict mode") {
      val original = new Json.Object(
        Chunk(
          "a" -> new Json.String("hello")
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(0)))
      )
      val result = patch(original, PatchMode.Strict)

      assertTrue(result.isLeft)
    },
    test("SchemaSearch followed by field navigation") {
      val pattern = SchemaRepr.Record(Vector("value" -> SchemaRepr.Primitive("number")))

      val original = new Json.Array(
        Chunk(
          new Json.Object(Chunk("value" -> new Json.Number(BigDecimal(10)))),
          new Json.Object(Chunk("value" -> new Json.Number(BigDecimal(20)))),
          new Json.Object(Chunk("other" -> new Json.String("skip")))
        )
      )

      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(pattern).field("value"),
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
      )
      val result = patch(original, PatchMode.Lenient)

      val expected = new Json.Array(
        Chunk(
          new Json.Object(Chunk("value" -> new Json.Number(BigDecimal(15)))),
          new Json.Object(Chunk("value" -> new Json.Number(BigDecimal(25)))),
          new Json.Object(Chunk("other" -> new Json.String("skip")))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("field navigation followed by SchemaSearch") {
      val original = new Json.Object(
        Chunk(
          "data" -> new Json.Object(
            Chunk(
              "x" -> new Json.Number(BigDecimal(1)),
              "y" -> new Json.Number(BigDecimal(2)),
              "z" -> new Json.String("hello")
            )
          )
        )
      )

      val patch = JsonPatch(
        DynamicOptic.root.field("data").searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(0)))
      )
      val result = patch(original)

      val expected = new Json.Object(
        Chunk(
          "data" -> new Json.Object(
            Chunk(
              "x" -> new Json.Number(BigDecimal(0)),
              "y" -> new Json.Number(BigDecimal(0)),
              "z" -> new Json.String("hello")
            )
          )
        )
      )
      assertTrue(result == Right(expected))
    },
    test("Wildcard pattern matches everything") {
      val original = new Json.Object(
        Chunk(
          "a" -> new Json.Number(BigDecimal(1)),
          "b" -> new Json.String("hello"),
          "c" -> Json.Boolean(true)
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(0)))
      )
      val result = patch(original)

      // Wildcard matches everything including root - so root gets replaced
      assertTrue(result == Right(new Json.Number(BigDecimal(0))))
    },
    test("PatchMode.Clobber continues on match errors") {
      val original = new Json.Array(
        Chunk(
          new Json.Number(BigDecimal(10)),
          new Json.String("not a number"),
          new Json.Number(BigDecimal(20))
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
      )
      val result = patch(original, PatchMode.Clobber)

      val expected = new Json.Array(
        Chunk(
          new Json.Number(BigDecimal(15)),
          new Json.String("not a number"),
          new Json.Number(BigDecimal(25))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("multiple SchemaSearch patches compose correctly") {
      val original = new Json.Object(
        Chunk(
          "nums"    -> new Json.Array(Chunk(new Json.Number(BigDecimal(1)), new Json.Number(BigDecimal(2)))),
          "strings" -> new Json.Array(Chunk(new Json.String("a"), new Json.String("b")))
        )
      )

      val patch1 = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("number")),
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(10)))
      )
      val patch2 = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Primitive("string")),
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Append("!")))
        )
      )

      val combined = patch1 ++ patch2
      val result   = combined(original)

      val expected = new Json.Object(
        Chunk(
          "nums"    -> new Json.Array(Chunk(new Json.Number(BigDecimal(11)), new Json.Number(BigDecimal(12)))),
          "strings" -> new Json.Array(Chunk(new Json.String("a!"), new Json.String("b!")))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("TypeSearch returns error (requires Schema context)") {
      import zio.blocks.typeid.TypeId
      val original = new Json.Number(BigDecimal(42))
      val path     = DynamicOptic(Vector(DynamicOptic.Node.TypeSearch(TypeId.of[Int])))
      val patch    = JsonPatch(Chunk.single(JsonPatch.JsonPatchOp(path, JsonPatch.Op.Set(new Json.Number(BigDecimal(0))))))
      val result   = patch(original)

      assertTrue(result.isLeft)
    },
    test("match found but op fails in Strict mode propagates error") {
      val original = new Json.Object(
        Chunk(
          "a" -> new Json.Number(BigDecimal(10)),
          "b" -> new Json.String("hello")
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
      )
      val result = patch(original, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("match found but op fails in Lenient mode skips error") {
      val original = new Json.Object(
        Chunk(
          "a" -> new Json.Number(BigDecimal(10)),
          "b" -> new Json.String("hello")
        )
      )
      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
      )
      val result = patch(original, PatchMode.Lenient)
      assertTrue(result.isRight)
    },
    test("navigate: match + navigate fails in Strict mode") {
      val original = new Json.Array(
        Chunk(
          new Json.Object(Chunk("name" -> new Json.String("Alice"))),
          new Json.Object(Chunk("name" -> new Json.String("Bob")))
        )
      )
      val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
      val patch   = JsonPatch(
        DynamicOptic.root.searchSchema(pattern).field("nonexistent"),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(0)))
      )
      val result = patch(original, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("navigate: match + navigate fails in Lenient mode skips") {
      val original = new Json.Array(
        Chunk(
          new Json.Object(Chunk("name" -> new Json.String("Alice"))),
          new Json.Object(Chunk("name" -> new Json.String("Bob")))
        )
      )
      val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
      val patch   = JsonPatch(
        DynamicOptic.root.searchSchema(pattern).field("nonexistent"),
        JsonPatch.Op.Set(new Json.Number(BigDecimal(0)))
      )
      val result = patch(original, PatchMode.Lenient)
      assertTrue(result == Right(original))
    },
    test("SchemaSearch with nested patch operation") {
      val pattern = SchemaRepr.Record(Vector("count" -> SchemaRepr.Primitive("number")))

      val original = new Json.Array(
        Chunk(
          new Json.Object(Chunk("name" -> new Json.String("A"), "count" -> new Json.Number(BigDecimal(5)))),
          new Json.Object(Chunk("name" -> new Json.String("B"), "count" -> new Json.Number(BigDecimal(10))))
        )
      )

      val nestedPatch = JsonPatch(
        Chunk.single(
          JsonPatch.JsonPatchOp(
            DynamicOptic.root.field("count"),
            JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1)))
          )
        )
      )

      val patch = JsonPatch(
        DynamicOptic.root.searchSchema(pattern),
        JsonPatch.Op.Nested(nestedPatch)
      )
      val result = patch(original)

      val expected = new Json.Array(
        Chunk(
          new Json.Object(Chunk("name" -> new Json.String("A"), "count" -> new Json.Number(BigDecimal(6)))),
          new Json.Object(Chunk("name" -> new Json.String("B"), "count" -> new Json.Number(BigDecimal(11))))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("path interpolator with SchemaSearch") {
      val original = new Json.Object(
        Chunk(
          "a" -> new Json.String("hello"),
          "b" -> new Json.String("world")
        )
      )
      val patch = JsonPatch(
        p"#string",
        JsonPatch.Op.Set(new Json.String("replaced"))
      )
      val result = patch(original)

      val expected = new Json.Object(
        Chunk(
          "a" -> new Json.String("replaced"),
          "b" -> new Json.String("replaced")
        )
      )
      assertTrue(result == Right(expected))
    }
  )
}
