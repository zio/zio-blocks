package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Schema, SchemaError}
import zio.test._

/**
 * Tests that validate Schema instances for JsonPatch inner types. These tests
 * ensure that Op, PrimitiveOp, StringOp, ArrayOp, and ObjectOp can roundtrip
 * through DynamicValue.
 *
 * Note: These tests are Scala 3 only because Schema.derived requires Scala 3
 * macros from the companion VersionSpecific traits.
 */
object JsonPatchInnerSchemasSpec extends ZIOSpecDefault {

  // Helper to verify schema is available implicitly
  private def roundtripViaImplicit[A](value: A)(implicit schema: Schema[A]): Either[SchemaError, A] = {
    val dynamic = schema.toDynamicValue(value)
    schema.fromDynamicValue(dynamic)
  }

  def spec: Spec[Any, Any] = suite("JsonPatch Inner Schemas (Scala 3)")(
    suite("Implicit schema resolution")(
      test("Op.schema is implicitly available") {
        val op: JsonPatch.Op = JsonPatch.Op.Set(Json.Null)
        assertTrue(roundtripViaImplicit(op) == Right(op))
      },
      test("PrimitiveOp.schema is implicitly available") {
        val op: JsonPatch.PrimitiveOp = JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(42))
        assertTrue(roundtripViaImplicit(op) == Right(op))
      },
      test("StringOp.schema is implicitly available") {
        val op: JsonPatch.StringOp = JsonPatch.StringOp.Insert(0, "hello")
        assertTrue(roundtripViaImplicit(op) == Right(op))
      },
      test("ArrayOp.schema is implicitly available") {
        val op: JsonPatch.ArrayOp = JsonPatch.ArrayOp.Delete(0, 1)
        assertTrue(roundtripViaImplicit(op) == Right(op))
      },
      test("ObjectOp.schema is implicitly available") {
        val op: JsonPatch.ObjectOp = JsonPatch.ObjectOp.Remove("key")
        assertTrue(roundtripViaImplicit(op) == Right(op))
      }
    ),
    suite("Op roundtrip coverage")(
      test("Op.Set roundtrips") {
        val op     = JsonPatch.Op.Set(Json.Null)
        val schema = JsonPatch.Op.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      }
    ),
    suite("PrimitiveOp roundtrip coverage")(
      test("NumberDelta roundtrips") {
        val op     = JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(42))
        val schema = JsonPatch.PrimitiveOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("StringEdit roundtrips") {
        val op     = JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "hello")))
        val schema = JsonPatch.PrimitiveOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      }
    ),
    suite("StringOp roundtrip coverage")(
      test("Insert roundtrips") {
        val op     = JsonPatch.StringOp.Insert(5, "world")
        val schema = JsonPatch.StringOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Delete roundtrips") {
        val op     = JsonPatch.StringOp.Delete(0, 10)
        val schema = JsonPatch.StringOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Append roundtrips") {
        val op     = JsonPatch.StringOp.Append("suffix")
        val schema = JsonPatch.StringOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Modify roundtrips") {
        val op     = JsonPatch.StringOp.Modify(2, 3, "replacement")
        val schema = JsonPatch.StringOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      }
    ),
    suite("ArrayOp roundtrip coverage")(
      test("Insert roundtrips") {
        val op     = JsonPatch.ArrayOp.Insert(0, Chunk(new Json.String("a"), new Json.String("b")))
        val schema = JsonPatch.ArrayOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Delete roundtrips") {
        val op     = JsonPatch.ArrayOp.Delete(5, 3)
        val schema = JsonPatch.ArrayOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Append roundtrips") {
        val op     = JsonPatch.ArrayOp.Append(Chunk(new Json.Number("42")))
        val schema = JsonPatch.ArrayOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Modify roundtrips") {
        val nestedOp = JsonPatch.Op.Set(new Json.String("modified"))
        val op       = JsonPatch.ArrayOp.Modify(2, nestedOp)
        val schema   = JsonPatch.ArrayOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      }
    ),
    suite("ObjectOp roundtrip coverage")(
      test("Add roundtrips") {
        val op     = JsonPatch.ObjectOp.Add("key", new Json.String("value"))
        val schema = JsonPatch.ObjectOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Remove roundtrips") {
        val op     = JsonPatch.ObjectOp.Remove("obsolete_key")
        val schema = JsonPatch.ObjectOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      },
      test("Modify roundtrips") {
        val nestedPatch = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("99")))
        val op          = JsonPatch.ObjectOp.Modify("field", nestedPatch)
        val schema      = JsonPatch.ObjectOp.schema
        assertTrue(schema.fromDynamicValue(schema.toDynamicValue(op)) == Right(op))
      }
    )
  )
}
