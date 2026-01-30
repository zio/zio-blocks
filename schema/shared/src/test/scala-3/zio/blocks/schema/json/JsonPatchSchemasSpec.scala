package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.test._

/**
 * Tests for [[JsonPatchSchemas]] inner type Schema instances.
 *
 * These tests verify roundtrip serialization through DynamicValue for each of
 * the JsonPatch inner types: Op, PrimitiveOp, StringOp, ArrayOp, ObjectOp.
 *
 * This spec only runs on Scala 3 since Schema.derived is not available on Scala 2.
 */
object JsonPatchSchemasSpec extends ZIOSpecDefault {

  import JsonPatchSchemas._

  override def spec: Spec[Any, Any] = suite("JsonPatchSchemas")(
    opSuite,
    primitiveOpSuite,
    stringOpSuite,
    arrayOpSuite,
    objectOpSuite
  )

  val opSuite: Spec[Any, Any] = suite("Schema[Op]")(
    test("roundtrip Set") {
      val op: JsonPatch.Op = JsonPatch.Op.Set(new Json.Number("42"))
      val dv               = opSchema.toDynamicValue(op)
      val roundtrip        = opSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip PrimitiveDelta") {
      val op: JsonPatch.Op = JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
      val dv               = opSchema.toDynamicValue(op)
      val roundtrip        = opSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip ArrayEdit") {
      val op: JsonPatch.Op = JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("1")))))
      val dv               = opSchema.toDynamicValue(op)
      val roundtrip        = opSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip ObjectEdit") {
      val op: JsonPatch.Op = JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("key", new Json.String("val"))))
      val dv               = opSchema.toDynamicValue(op)
      val roundtrip        = opSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Nested") {
      val op: JsonPatch.Op = JsonPatch.Op.Nested(JsonPatch.empty)
      val dv               = opSchema.toDynamicValue(op)
      val roundtrip        = opSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    }
  )

  val primitiveOpSuite: Spec[Any, Any] = suite("Schema[PrimitiveOp]")(
    test("roundtrip NumberDelta") {
      val op: JsonPatch.PrimitiveOp = JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-10))
      val dv                        = primitiveOpSchema.toDynamicValue(op)
      val roundtrip                 = primitiveOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip StringEdit") {
      val op: JsonPatch.PrimitiveOp = JsonPatch.PrimitiveOp.StringEdit(
        Vector(JsonPatch.StringOp.Insert(0, "hello"))
      )
      val dv        = primitiveOpSchema.toDynamicValue(op)
      val roundtrip = primitiveOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    }
  )

  val stringOpSuite: Spec[Any, Any] = suite("Schema[StringOp]")(
    test("roundtrip Insert") {
      val op: JsonPatch.StringOp = JsonPatch.StringOp.Insert(0, "prefix")
      val dv                     = stringOpSchema.toDynamicValue(op)
      val roundtrip              = stringOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Delete") {
      val op: JsonPatch.StringOp = JsonPatch.StringOp.Delete(5, 3)
      val dv                     = stringOpSchema.toDynamicValue(op)
      val roundtrip              = stringOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Append") {
      val op: JsonPatch.StringOp = JsonPatch.StringOp.Append("suffix")
      val dv                     = stringOpSchema.toDynamicValue(op)
      val roundtrip              = stringOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Modify") {
      val op: JsonPatch.StringOp = JsonPatch.StringOp.Modify(2, 5, "replacement")
      val dv                     = stringOpSchema.toDynamicValue(op)
      val roundtrip              = stringOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    }
  )

  val arrayOpSuite: Spec[Any, Any] = suite("Schema[ArrayOp]")(
    test("roundtrip Insert") {
      val op: JsonPatch.ArrayOp = JsonPatch.ArrayOp.Insert(0, Chunk(new Json.Number("1")))
      val dv                    = arrayOpSchema.toDynamicValue(op)
      val roundtrip             = arrayOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Delete") {
      val op: JsonPatch.ArrayOp = JsonPatch.ArrayOp.Delete(2, 1)
      val dv                    = arrayOpSchema.toDynamicValue(op)
      val roundtrip             = arrayOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Append") {
      val op: JsonPatch.ArrayOp = JsonPatch.ArrayOp.Append(Chunk(new Json.String("new")))
      val dv                    = arrayOpSchema.toDynamicValue(op)
      val roundtrip             = arrayOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Modify") {
      val op: JsonPatch.ArrayOp = JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.Set(new Json.Number("99")))
      val dv                    = arrayOpSchema.toDynamicValue(op)
      val roundtrip             = arrayOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    }
  )

  val objectOpSuite: Spec[Any, Any] = suite("Schema[ObjectOp]")(
    test("roundtrip Add") {
      val op: JsonPatch.ObjectOp = JsonPatch.ObjectOp.Add("key", new Json.String("value"))
      val dv                     = objectOpSchema.toDynamicValue(op)
      val roundtrip              = objectOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Remove") {
      val op: JsonPatch.ObjectOp = JsonPatch.ObjectOp.Remove("obsolete")
      val dv                     = objectOpSchema.toDynamicValue(op)
      val roundtrip              = objectOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    },
    test("roundtrip Modify") {
      val op: JsonPatch.ObjectOp = JsonPatch.ObjectOp.Modify("nested", JsonPatch.empty)
      val dv                     = objectOpSchema.toDynamicValue(op)
      val roundtrip              = objectOpSchema.fromDynamicValue(dv)
      assertTrue(roundtrip == Right(op))
    }
  )
}
