package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import java.time.{Duration => JDuration, Period => JPeriod}

/**
 * Coverage tests for DynamicPatch schema serialization paths. Exercises
 * toDynamicValue/fromDynamicValue for all variant cases to cover the
 * downcastOrNull matcher methods.
 */
object DynamicPatchSchemaCoverageSpec extends SchemaBaseSpec {

  // Helper to create primitive DynamicValues
  def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  def longVal(n: Long): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.Long(n))
  def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  def doubleVal(d: Double): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Double(d))
  def floatVal(f: Float): DynamicValue   = DynamicValue.Primitive(PrimitiveValue.Float(f))
  def shortVal(s: Short): DynamicValue   = DynamicValue.Primitive(PrimitiveValue.Short(s))
  def byteVal(b: Byte): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.Byte(b))

  def spec: Spec[Any, Any] = suite("DynamicPatchSchemaCoverageSpec")(
    operationSchemaTests,
    primitiveOpSchemaTests,
    seqOpSchemaTests,
    mapOpSchemaTests,
    stringOpSchemaTests,
    patchModeSchemaTests,
    dynamicPatchOpSchemaTests,
    dynamicPatchSchemaTests,
    operationPrismTests
  )

  // Tests for Operation schema - exercises all Operation variant cases
  val operationSchemaTests = suite("Operation schema coverage")(
    test("Operation.Set round-trip") {
      val op     = DynamicPatch.Operation.Set(intVal(42))
      val schema = Schema[DynamicPatch.Operation]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("Operation.PrimitiveDelta round-trip with IntDelta") {
      val op     = DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
      val schema = Schema[DynamicPatch.Operation]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("Operation.SequenceEdit round-trip with Append") {
      val op = DynamicPatch.Operation.SequenceEdit(
        Vector(
          DynamicPatch.SeqOp.Append(Chunk(intVal(1), intVal(2)))
        )
      )
      val schema = Schema[DynamicPatch.Operation]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("Operation.MapEdit round-trip with Add") {
      val op = DynamicPatch.Operation.MapEdit(
        Vector(
          DynamicPatch.MapOp.Add(stringVal("key"), intVal(100))
        )
      )
      val schema = Schema[DynamicPatch.Operation]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("Operation.Patch round-trip with nested patch") {
      val nested = DynamicPatch.root(DynamicPatch.Operation.Set(intVal(99)))
      val op     = DynamicPatch.Operation.Patch(nested)
      val schema = Schema[DynamicPatch.Operation]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    }
  )

  // Tests for PrimitiveOp schema - exercises all PrimitiveOp variant cases
  val primitiveOpSchemaTests = suite("PrimitiveOp schema coverage")(
    test("PrimitiveOp.IntDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.IntDelta(42)
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.LongDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.LongDelta(123456789L)
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.DoubleDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.DoubleDelta(3.14)
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.FloatDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.FloatDelta(2.5f)
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.ShortDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.ShortDelta(100.toShort)
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.ByteDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.ByteDelta(10.toByte)
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.BigIntDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.BigIntDelta(BigInt("123456789012345"))
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.BigDecimalDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal("123.456"))
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.DurationDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.DurationDelta(JDuration.ofSeconds(100))
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.InstantDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.InstantDelta(JDuration.ofMillis(5000))
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.LocalDateDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.LocalDateDelta(JPeriod.of(1, 2, 3))
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.LocalDateTimeDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.LocalDateTimeDelta(JPeriod.of(0, 1, 0), JDuration.ofHours(2))
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.PeriodDelta round-trip") {
      val op     = DynamicPatch.PrimitiveOp.PeriodDelta(JPeriod.of(1, 2, 3))
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("PrimitiveOp.StringEdit round-trip") {
      val op = DynamicPatch.PrimitiveOp.StringEdit(
        Vector(
          DynamicPatch.StringOp.Insert(0, "Hello")
        )
      )
      val schema = Schema[DynamicPatch.PrimitiveOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    }
  )

  // Tests for SeqOp schema - exercises all SeqOp variant cases
  val seqOpSchemaTests = suite("SeqOp schema coverage")(
    test("SeqOp.Append round-trip") {
      val op     = DynamicPatch.SeqOp.Append(Chunk(intVal(1), intVal(2), intVal(3)))
      val schema = Schema[DynamicPatch.SeqOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("SeqOp.Insert round-trip") {
      val op     = DynamicPatch.SeqOp.Insert(2, Chunk(intVal(100), intVal(200)))
      val schema = Schema[DynamicPatch.SeqOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("SeqOp.Delete round-trip") {
      val op     = DynamicPatch.SeqOp.Delete(1, 3)
      val schema = Schema[DynamicPatch.SeqOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("SeqOp.Modify round-trip") {
      val op     = DynamicPatch.SeqOp.Modify(5, DynamicPatch.Operation.Set(intVal(999)))
      val schema = Schema[DynamicPatch.SeqOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    }
  )

  // Tests for MapOp schema - exercises all MapOp variant cases
  val mapOpSchemaTests = suite("MapOp schema coverage")(
    test("MapOp.Add round-trip") {
      val op     = DynamicPatch.MapOp.Add(stringVal("newKey"), intVal(123))
      val schema = Schema[DynamicPatch.MapOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("MapOp.Remove round-trip") {
      val op     = DynamicPatch.MapOp.Remove(stringVal("oldKey"))
      val schema = Schema[DynamicPatch.MapOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("MapOp.Modify round-trip") {
      val op     = DynamicPatch.MapOp.Modify(stringVal("myKey"), DynamicPatch.root(DynamicPatch.Operation.Set(intVal(456))))
      val schema = Schema[DynamicPatch.MapOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    }
  )

  // Tests for StringOp schema - exercises all StringOp variant cases
  val stringOpSchemaTests = suite("StringOp schema coverage")(
    test("StringOp.Insert round-trip") {
      val op     = DynamicPatch.StringOp.Insert(5, "inserted")
      val schema = Schema[DynamicPatch.StringOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("StringOp.Delete round-trip") {
      val op     = DynamicPatch.StringOp.Delete(0, 10)
      val schema = Schema[DynamicPatch.StringOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("StringOp.Append round-trip") {
      val op     = DynamicPatch.StringOp.Append("appended text")
      val schema = Schema[DynamicPatch.StringOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("StringOp.Modify round-trip") {
      val op     = DynamicPatch.StringOp.Modify(2, 5, "replacement")
      val schema = Schema[DynamicPatch.StringOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    }
  )

  // Tests for PatchMode schema
  val patchModeSchemaTests = suite("PatchMode schema coverage")(
    test("PatchMode.Strict round-trip") {
      val pm     = PatchMode.Strict
      val schema = Schema[PatchMode]
      val dv     = schema.reflect.toDynamicValue(pm)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(pm))
    },
    test("PatchMode.Lenient round-trip") {
      val pm     = PatchMode.Lenient
      val schema = Schema[PatchMode]
      val dv     = schema.reflect.toDynamicValue(pm)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(pm))
    },
    test("PatchMode.Clobber round-trip") {
      val pm     = PatchMode.Clobber
      val schema = Schema[PatchMode]
      val dv     = schema.reflect.toDynamicValue(pm)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(pm))
    }
  )

  // Tests for DynamicPatchOp schema
  val dynamicPatchOpSchemaTests = suite("DynamicPatchOp schema coverage")(
    test("DynamicPatchOp with field path round-trip") {
      val op = DynamicPatch.DynamicPatchOp(
        DynamicOptic.root.field("name"),
        DynamicPatch.Operation.Set(stringVal("Alice"))
      )
      val schema = Schema[DynamicPatch.DynamicPatchOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    },
    test("DynamicPatchOp with nested path round-trip") {
      val op = DynamicPatch.DynamicPatchOp(
        DynamicOptic.root.field("outer").field("inner"),
        DynamicPatch.Operation.Set(intVal(99))
      )
      val schema = Schema[DynamicPatch.DynamicPatchOp]
      val dv     = schema.reflect.toDynamicValue(op)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(op))
    }
  )

  // Tests for DynamicPatch schema
  val dynamicPatchSchemaTests = suite("DynamicPatch schema coverage")(
    test("Empty DynamicPatch round-trip") {
      val patch  = DynamicPatch.empty
      val schema = Schema[DynamicPatch]
      val dv     = schema.reflect.toDynamicValue(patch)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(patch))
    },
    test("DynamicPatch with single Set operation round-trip") {
      val patch  = DynamicPatch.root(DynamicPatch.Operation.Set(intVal(42)))
      val schema = Schema[DynamicPatch]
      val dv     = schema.reflect.toDynamicValue(patch)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(patch))
    },
    test("DynamicPatch with multiple operations round-trip") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("a"), DynamicPatch.Operation.Set(intVal(1))),
          DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("b"), DynamicPatch.Operation.Set(intVal(2))),
          DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("c"), DynamicPatch.Operation.Set(intVal(3)))
        )
      )
      val schema = Schema[DynamicPatch]
      val dv     = schema.reflect.toDynamicValue(patch)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(patch))
    },
    test("DynamicPatch with complex nested operations round-trip") {
      val nested = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("inner"), DynamicPatch.Operation.Set(intVal(999)))
        )
      )
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("outer"), DynamicPatch.Operation.Patch(nested))
        )
      )
      val schema = Schema[DynamicPatch]
      val dv     = schema.reflect.toDynamicValue(patch)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(patch))
    }
  )

  // Tests that exercise prism operations on Operation variant to cover downcastOrNull
  val operationPrismTests = suite("Operation prism coverage")(
    test("Operation variant has correct number of cases") {
      val schema  = Schema[DynamicPatch.Operation]
      val variant = schema.reflect.asVariant.get
      assertTrue(variant.cases.size == 5)
    },
    test("Set prism matches Set and rejects others") {
      val schema                         = Schema[DynamicPatch.Operation]
      val variant                        = schema.reflect.asVariant.get
      val setPrism                       = variant.prismByIndex[DynamicPatch.Operation.Set](0).get
      val setOp: DynamicPatch.Operation  = DynamicPatch.Operation.Set(intVal(1))
      val primOp: DynamicPatch.Operation = DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
      assertTrue(
        setPrism.getOption(setOp).isDefined,
        setPrism.getOption(primOp).isEmpty
      )
    },
    test("PrimitiveDelta prism matches PrimitiveDelta and rejects others") {
      val schema                         = Schema[DynamicPatch.Operation]
      val variant                        = schema.reflect.asVariant.get
      val primPrism                      = variant.prismByIndex[DynamicPatch.Operation.PrimitiveDelta](1).get
      val primOp: DynamicPatch.Operation = DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
      val seqOp: DynamicPatch.Operation  = DynamicPatch.Operation.SequenceEdit(Vector.empty)
      assertTrue(
        primPrism.getOption(primOp).isDefined,
        primPrism.getOption(seqOp).isEmpty
      )
    },
    test("SequenceEdit prism matches SequenceEdit and rejects others") {
      val schema                        = Schema[DynamicPatch.Operation]
      val variant                       = schema.reflect.asVariant.get
      val seqPrism                      = variant.prismByIndex[DynamicPatch.Operation.SequenceEdit](2).get
      val seqOp: DynamicPatch.Operation = DynamicPatch.Operation.SequenceEdit(Vector.empty)
      val mapOp: DynamicPatch.Operation = DynamicPatch.Operation.MapEdit(Vector.empty)
      assertTrue(
        seqPrism.getOption(seqOp).isDefined,
        seqPrism.getOption(mapOp).isEmpty
      )
    },
    test("MapEdit prism matches MapEdit and rejects others") {
      val schema                          = Schema[DynamicPatch.Operation]
      val variant                         = schema.reflect.asVariant.get
      val mapPrism                        = variant.prismByIndex[DynamicPatch.Operation.MapEdit](3).get
      val mapOp: DynamicPatch.Operation   = DynamicPatch.Operation.MapEdit(Vector.empty)
      val patchOp: DynamicPatch.Operation = DynamicPatch.Operation.Patch(DynamicPatch.empty)
      assertTrue(
        mapPrism.getOption(mapOp).isDefined,
        mapPrism.getOption(patchOp).isEmpty
      )
    },
    test("Patch prism matches Patch and rejects others") {
      val schema                          = Schema[DynamicPatch.Operation]
      val variant                         = schema.reflect.asVariant.get
      val patchPrism                      = variant.prismByIndex[DynamicPatch.Operation.Patch](4).get
      val patchOp: DynamicPatch.Operation = DynamicPatch.Operation.Patch(DynamicPatch.empty)
      val setOp: DynamicPatch.Operation   = DynamicPatch.Operation.Set(intVal(1))
      assertTrue(
        patchPrism.getOption(patchOp).isDefined,
        patchPrism.getOption(setOp).isEmpty
      )
    },
    test("PrimitiveOp variant has correct number of cases") {
      val schema  = Schema[DynamicPatch.PrimitiveOp]
      val variant = schema.reflect.asVariant.get
      assertTrue(variant.cases.size >= 10)
    },
    test("SeqOp variant has correct number of cases") {
      val schema  = Schema[DynamicPatch.SeqOp]
      val variant = schema.reflect.asVariant.get
      assertTrue(variant.cases.size == 4)
    },
    test("MapOp variant has correct number of cases") {
      val schema  = Schema[DynamicPatch.MapOp]
      val variant = schema.reflect.asVariant.get
      assertTrue(variant.cases.size == 3)
    },
    test("StringOp variant has correct number of cases") {
      val schema  = Schema[DynamicPatch.StringOp]
      val variant = schema.reflect.asVariant.get
      assertTrue(variant.cases.size == 4)
    },
    test("PatchMode variant has correct number of cases") {
      val schema  = Schema[PatchMode]
      val variant = schema.reflect.asVariant.get
      assertTrue(variant.cases.size == 3)
    }
  )
}
