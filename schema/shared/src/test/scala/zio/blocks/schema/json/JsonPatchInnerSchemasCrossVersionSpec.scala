package zio.blocks.schema.json

import zio.Scope
import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.test._

/**
 * Cross-version tests for JsonPatch inner type schemas.
 *
 * These tests verify that the manually-defined Scala 2 schemas and the
 * Schema.derived Scala 3 schemas produce correct results for serialization
 * round-trips via DynamicValue.
 */
object JsonPatchInnerSchemasCrossVersionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("JsonPatch Inner Schemas Cross-Version")(
    suite("StringOp schema")(
      test("Insert roundtrips through DynamicValue") {
        val op       = JsonPatch.StringOp.Insert(5, "hello")
        val schema   = implicitly[Schema[JsonPatch.StringOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Delete roundtrips through DynamicValue") {
        val op       = JsonPatch.StringOp.Delete(10, 5)
        val schema   = implicitly[Schema[JsonPatch.StringOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Append roundtrips through DynamicValue") {
        val op       = JsonPatch.StringOp.Append("world")
        val schema   = implicitly[Schema[JsonPatch.StringOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Modify roundtrips through DynamicValue") {
        val op       = JsonPatch.StringOp.Modify(3, 2, "test")
        val schema   = implicitly[Schema[JsonPatch.StringOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      }
    ),
    suite("PrimitiveOp schema")(
      test("NumberDelta roundtrips through DynamicValue") {
        val op       = JsonPatch.PrimitiveOp.NumberDelta(BigDecimal("3.14159"))
        val schema   = implicitly[Schema[JsonPatch.PrimitiveOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("StringEdit roundtrips through DynamicValue") {
        val op = JsonPatch.PrimitiveOp.StringEdit(
          Vector(JsonPatch.StringOp.Insert(0, "x"), JsonPatch.StringOp.Delete(5, 2))
        )
        val schema   = implicitly[Schema[JsonPatch.PrimitiveOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      }
    ),
    suite("ArrayOp schema")(
      test("Insert roundtrips through DynamicValue") {
        val op       = JsonPatch.ArrayOp.Insert(0, Chunk(new Json.Number("1"), new Json.Number("2")))
        val schema   = implicitly[Schema[JsonPatch.ArrayOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Append roundtrips through DynamicValue") {
        val op       = JsonPatch.ArrayOp.Append(Chunk(Json.True, Json.False))
        val schema   = implicitly[Schema[JsonPatch.ArrayOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Delete roundtrips through DynamicValue") {
        val op       = JsonPatch.ArrayOp.Delete(2, 3)
        val schema   = implicitly[Schema[JsonPatch.ArrayOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Modify roundtrips through DynamicValue") {
        val op       = JsonPatch.ArrayOp.Modify(1, JsonPatch.Op.Set(new Json.String("updated")))
        val schema   = implicitly[Schema[JsonPatch.ArrayOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      }
    ),
    suite("ObjectOp schema")(
      test("Add roundtrips through DynamicValue") {
        val op       = JsonPatch.ObjectOp.Add("newKey", new Json.String("value"))
        val schema   = implicitly[Schema[JsonPatch.ObjectOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Remove roundtrips through DynamicValue") {
        val op       = JsonPatch.ObjectOp.Remove("oldKey")
        val schema   = implicitly[Schema[JsonPatch.ObjectOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Modify roundtrips through DynamicValue") {
        val op       = JsonPatch.ObjectOp.Modify("field", JsonPatch.root(JsonPatch.Op.Set(Json.Null)))
        val schema   = implicitly[Schema[JsonPatch.ObjectOp]]
        val dynamic  = schema.toDynamicValue(op)
        val restored = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      }
    ),
    suite("Op schema")(
      test("Set roundtrips through DynamicValue") {
        val op: JsonPatch.Op = JsonPatch.Op.Set(new Json.Number("42"))
        val schema           = implicitly[Schema[JsonPatch.Op]]
        val dynamic          = schema.toDynamicValue(op)
        val restored         = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("PrimitiveDelta roundtrips through DynamicValue") {
        val op: JsonPatch.Op = JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(100)))
        val schema           = implicitly[Schema[JsonPatch.Op]]
        val dynamic          = schema.toDynamicValue(op)
        val restored         = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("ArrayEdit roundtrips through DynamicValue") {
        val op: JsonPatch.Op = JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 1)))
        val schema           = implicitly[Schema[JsonPatch.Op]]
        val dynamic          = schema.toDynamicValue(op)
        val restored         = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("ObjectEdit roundtrips through DynamicValue") {
        val op: JsonPatch.Op = JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("x")))
        val schema           = implicitly[Schema[JsonPatch.Op]]
        val dynamic          = schema.toDynamicValue(op)
        val restored         = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      },
      test("Nested roundtrips through DynamicValue") {
        val op: JsonPatch.Op = JsonPatch.Op.Nested(JsonPatch.root(JsonPatch.Op.Set(Json.True)))
        val schema           = implicitly[Schema[JsonPatch.Op]]
        val dynamic          = schema.toDynamicValue(op)
        val restored         = schema.fromDynamicValue(dynamic)
        assertTrue(restored == Right(op))
      }
    )
  )
}
