package zio.blocks.schema

import zio.test._
import zio.blocks.schema.patch._
import zio.blocks.chunk.Chunk
import java.time._

// Import nested types from DynamicPatch
import DynamicPatch.{StringOp, PrimitiveOp, SeqOp, MapOp, Operation}

/**
 * Coverage tests targeting DynamicPatch manual schemas:
 *   - StringOp (Insert, Delete, Append, Modify) - 4 variants
 *   - PrimitiveOp (IntDelta, LongDelta, etc.) - 14 variants
 *   - SeqOp (Insert, Append, Delete, Modify) - 4 variants
 *   - MapOp (Add, Remove, Modify) - 3 variants
 *   - Operation (Set, PrimitiveDelta, SequenceEdit, MapEdit, Patch) - 5
 *     variants
 */
object PatchCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("PatchCoverageSpec")(
    stringOpSchemaTests,
    primitiveOpSchemaTests,
    seqOpSchemaTests,
    mapOpSchemaTests,
    operationSchemaTests,
    dynamicPatchOpSchemaTests,
    dynamicPatchSchemaTests,
    primitiveOpDifferTests,
    renderOpTests
  )

  // ===========================================================================
  // StringOp Schema Tests - 4 variants
  // ===========================================================================
  val stringOpSchemaTests = suite("StringOp schema coverage")(
    suite("StringOp.Insert")(
      (1 to 10).map { i =>
        test(s"Insert roundtrip $i") {
          val op: StringOp = StringOp.Insert(i, s"text$i")
          val dv           = Schema[StringOp].toDynamicValue(op)
          val restored     = Schema[StringOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("StringOp.Delete")(
      (1 to 10).map { i =>
        test(s"Delete roundtrip $i") {
          val op: StringOp = StringOp.Delete(i, i * 2)
          val dv           = Schema[StringOp].toDynamicValue(op)
          val restored     = Schema[StringOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("StringOp.Append")(
      (1 to 10).map { i =>
        test(s"Append roundtrip $i") {
          val op: StringOp = StringOp.Append("appended" * i)
          val dv           = Schema[StringOp].toDynamicValue(op)
          val restored     = Schema[StringOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("StringOp.Modify")(
      (1 to 10).map { i =>
        test(s"Modify roundtrip $i") {
          val op: StringOp = StringOp.Modify(i, i + 5, s"replacement$i")
          val dv           = Schema[StringOp].toDynamicValue(op)
          val restored     = Schema[StringOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    )
  )

  // ===========================================================================
  // PrimitiveOp Schema Tests - 14 variants (KEY FOR COVERAGE)
  // ===========================================================================
  val primitiveOpSchemaTests = suite("PrimitiveOp schema coverage")(
    suite("PrimitiveOp.IntDelta")(
      (1 to 10).map { i =>
        test(s"IntDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.IntDelta(i * 100)
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.LongDelta")(
      (1 to 10).map { i =>
        test(s"LongDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.LongDelta(i.toLong * 1000000000L)
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.DoubleDelta")(
      (1 to 10).map { i =>
        test(s"DoubleDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.DoubleDelta(i.toDouble / 7.0)
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.FloatDelta")(
      (1 to 10).map { i =>
        test(s"FloatDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.FloatDelta(i.toFloat / 3.0f)
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.ShortDelta")(
      (1 to 10).map { i =>
        test(s"ShortDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.ShortDelta((i * 100).toShort)
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.ByteDelta")(
      (1 to 10).map { i =>
        test(s"ByteDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.ByteDelta((i * 10).toByte)
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.BigIntDelta")(
      (1 to 10).map { i =>
        test(s"BigIntDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.BigIntDelta(BigInt(i) * BigInt("123456789012345678901234567890"))
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.BigDecimalDelta")(
      (1 to 10).map { i =>
        test(s"BigDecimalDelta roundtrip $i") {
          val op: PrimitiveOp =
            PrimitiveOp.BigDecimalDelta(BigDecimal(i) * BigDecimal("123.456789012345678901234567890"))
          val dv       = Schema[PrimitiveOp].toDynamicValue(op)
          val restored = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.StringEdit")(
      (1 to 10).map { i =>
        test(s"StringEdit roundtrip $i") {
          val ops             = Vector(StringOp.Insert(0, "hello"), StringOp.Append("world"))
          val op: PrimitiveOp = PrimitiveOp.StringEdit(ops)
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.InstantDelta")(
      (1 to 10).map { i =>
        test(s"InstantDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.InstantDelta(Duration.ofSeconds(i * 1000))
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.DurationDelta")(
      (1 to 10).map { i =>
        test(s"DurationDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.DurationDelta(Duration.ofHours(i))
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.LocalDateDelta")(
      (1 to 10).map { i =>
        test(s"LocalDateDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.LocalDateDelta(Period.ofDays(i * 10))
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.LocalDateTimeDelta")(
      (1 to 10).map { i =>
        test(s"LocalDateTimeDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.LocalDateTimeDelta(Period.ofMonths(i), Duration.ofHours(i))
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("PrimitiveOp.PeriodDelta")(
      (1 to 10).map { i =>
        test(s"PeriodDelta roundtrip $i") {
          val op: PrimitiveOp = PrimitiveOp.PeriodDelta(Period.of(i, i * 2, i * 3))
          val dv              = Schema[PrimitiveOp].toDynamicValue(op)
          val restored        = Schema[PrimitiveOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    )
  )

  // ===========================================================================
  // SeqOp Schema Tests - 4 variants
  // ===========================================================================
  val seqOpSchemaTests = suite("SeqOp schema coverage")(
    suite("SeqOp.Insert")(
      (1 to 10).map { i =>
        test(s"Insert roundtrip $i") {
          val values =
            Chunk.fromArray((1 to i).map(j => DynamicValue.Primitive(PrimitiveValue.Int(j)): DynamicValue).toArray)
          val op: SeqOp = SeqOp.Insert(i, values)
          val dv        = Schema[SeqOp].toDynamicValue(op)
          val restored  = Schema[SeqOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("SeqOp.Append")(
      (1 to 10).map { i =>
        test(s"Append roundtrip $i") {
          val values = Chunk.fromArray(
            (1 to i).map(j => DynamicValue.Primitive(PrimitiveValue.String(s"item$j")): DynamicValue).toArray
          )
          val op: SeqOp = SeqOp.Append(values)
          val dv        = Schema[SeqOp].toDynamicValue(op)
          val restored  = Schema[SeqOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("SeqOp.Delete")(
      (1 to 10).map { i =>
        test(s"Delete roundtrip $i") {
          val op: SeqOp = SeqOp.Delete(i, i + 5)
          val dv        = Schema[SeqOp].toDynamicValue(op)
          val restored  = Schema[SeqOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("SeqOp.Modify")(
      (1 to 10).map { i =>
        test(s"Modify roundtrip $i") {
          val innerOp   = Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(i * 100)))
          val op: SeqOp = SeqOp.Modify(i, innerOp)
          val dv        = Schema[SeqOp].toDynamicValue(op)
          val restored  = Schema[SeqOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    )
  )

  // ===========================================================================
  // MapOp Schema Tests - 3 variants
  // ===========================================================================
  val mapOpSchemaTests = suite("MapOp schema coverage")(
    suite("MapOp.Add")(
      (1 to 10).map { i =>
        test(s"Add roundtrip $i") {
          val key       = DynamicValue.Primitive(PrimitiveValue.String(s"key$i"))
          val value     = DynamicValue.Primitive(PrimitiveValue.Int(i * 10))
          val op: MapOp = MapOp.Add(key, value)
          val dv        = Schema[MapOp].toDynamicValue(op)
          val restored  = Schema[MapOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("MapOp.Remove")(
      (1 to 10).map { i =>
        test(s"Remove roundtrip $i") {
          val key       = DynamicValue.Primitive(PrimitiveValue.String(s"key$i"))
          val op: MapOp = MapOp.Remove(key)
          val dv        = Schema[MapOp].toDynamicValue(op)
          val restored  = Schema[MapOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("MapOp.Modify")(
      (1 to 10).map { i =>
        test(s"Modify roundtrip $i") {
          val key        = DynamicValue.Primitive(PrimitiveValue.String(s"key$i"))
          val innerPatch = DynamicPatch(
            Vector(
              DynamicPatch.DynamicPatchOp(
                DynamicOptic.root,
                Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(i)))
              )
            )
          )
          val op: MapOp = MapOp.Modify(key, innerPatch)
          val dv        = Schema[MapOp].toDynamicValue(op)
          val restored  = Schema[MapOp].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    )
  )

  // ===========================================================================
  // Operation Schema Tests - 5 variants
  // ===========================================================================
  val operationSchemaTests = suite("Operation schema coverage")(
    suite("Operation.Set")(
      (1 to 10).map { i =>
        test(s"Set roundtrip $i") {
          val op: Operation = Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(i)))
          val dv            = Schema[Operation].toDynamicValue(op)
          val restored      = Schema[Operation].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("Operation.PrimitiveDelta")(
      (1 to 10).map { i =>
        test(s"PrimitiveDelta roundtrip $i") {
          val primitiveOp   = PrimitiveOp.IntDelta(i * 100)
          val op: Operation = Operation.PrimitiveDelta(primitiveOp)
          val dv            = Schema[Operation].toDynamicValue(op)
          val restored      = Schema[Operation].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("Operation.SequenceEdit")(
      (1 to 10).map { i =>
        test(s"SequenceEdit roundtrip $i") {
          val seqOps = Vector(
            SeqOp.Insert(0, Chunk.fromArray(Array(DynamicValue.Primitive(PrimitiveValue.Int(i)): DynamicValue))),
            SeqOp.Delete(1, 2)
          )
          val op: Operation = Operation.SequenceEdit(seqOps)
          val dv            = Schema[Operation].toDynamicValue(op)
          val restored      = Schema[Operation].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("Operation.MapEdit")(
      (1 to 10).map { i =>
        test(s"MapEdit roundtrip $i") {
          val mapOps = Vector(
            MapOp.Add(
              DynamicValue.Primitive(PrimitiveValue.String(s"key$i")),
              DynamicValue.Primitive(PrimitiveValue.Int(i))
            ),
            MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("old")))
          )
          val op: Operation = Operation.MapEdit(mapOps)
          val dv            = Schema[Operation].toDynamicValue(op)
          val restored      = Schema[Operation].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    ),
    suite("Operation.Patch")(
      (1 to 10).map { i =>
        test(s"Patch roundtrip $i") {
          val innerPatch = DynamicPatch(
            Vector(
              DynamicPatch.DynamicPatchOp(
                DynamicOptic.root.field("field"),
                Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(i)))
              )
            )
          )
          val op: Operation = Operation.Patch(innerPatch)
          val dv            = Schema[Operation].toDynamicValue(op)
          val restored      = Schema[Operation].fromDynamicValue(dv)
          assertTrue(restored == Right(op))
        }
      }: _*
    )
  )

  // ===========================================================================
  // DynamicPatchOp Schema Tests
  // ===========================================================================
  val dynamicPatchOpSchemaTests = suite("DynamicPatchOp schema coverage")(
    (1 to 20).map { i =>
      test(s"DynamicPatchOp roundtrip $i") {
        val path      = DynamicOptic.root.field(s"field$i").at(i)
        val operation = Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(i * 100)))
        val op        = DynamicPatch.DynamicPatchOp(path, operation)
        val dv        = Schema[DynamicPatch.DynamicPatchOp].toDynamicValue(op)
        val restored  = Schema[DynamicPatch.DynamicPatchOp].fromDynamicValue(dv)
        assertTrue(restored == Right(op))
      }
    }: _*
  )

  // ===========================================================================
  // DynamicPatch Schema Tests
  // ===========================================================================
  val dynamicPatchSchemaTests = suite("DynamicPatch schema coverage")(
    Seq(
      test("Empty patch roundtrip") {
        val patch    = DynamicPatch.empty
        val dv       = Schema[DynamicPatch].toDynamicValue(patch)
        val restored = Schema[DynamicPatch].fromDynamicValue(dv)
        assertTrue(restored == Right(patch))
      }
    ) ++ (1 to 20).map { i =>
      test(s"DynamicPatch with $i ops roundtrip") {
        val ops = (1 to i).map { j =>
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field(s"field$j"),
            Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(j)))
          )
        }.toVector
        val patch    = DynamicPatch(ops)
        val dv       = Schema[DynamicPatch].toDynamicValue(patch)
        val restored = Schema[DynamicPatch].fromDynamicValue(dv)
        assertTrue(restored == Right(patch))
      }
    }: _*
  )

  // ===========================================================================
  // PrimitiveOp Differ Tests
  // ===========================================================================
  val primitiveOpDifferTests = suite("PrimitiveOp via Differ")(
    test("BigInt diff") {
      val old    = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("100")))
      val newVal = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("250")))
      val patch  = Differ.diff(old, newVal)
      assertTrue(!patch.isEmpty)
    },
    test("BigDecimal diff") {
      val old    = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("100.5")))
      val newVal = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("250.75")))
      val patch  = Differ.diff(old, newVal)
      assertTrue(!patch.isEmpty)
    },
    test("Instant diff") {
      val now    = java.time.Instant.now()
      val later  = now.plusSeconds(3600)
      val old    = DynamicValue.Primitive(PrimitiveValue.Instant(now))
      val newVal = DynamicValue.Primitive(PrimitiveValue.Instant(later))
      val patch  = Differ.diff(old, newVal)
      assertTrue(!patch.isEmpty)
    },
    test("Duration diff") {
      val old    = DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofHours(1)))
      val newVal = DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofHours(5)))
      val patch  = Differ.diff(old, newVal)
      assertTrue(!patch.isEmpty)
    },
    test("LocalDate diff") {
      val today    = java.time.LocalDate.now()
      val tomorrow = today.plusDays(10)
      val old      = DynamicValue.Primitive(PrimitiveValue.LocalDate(today))
      val newVal   = DynamicValue.Primitive(PrimitiveValue.LocalDate(tomorrow))
      val patch    = Differ.diff(old, newVal)
      assertTrue(!patch.isEmpty)
    },
    test("LocalDateTime diff") {
      val now    = java.time.LocalDateTime.now()
      val later  = now.plusDays(5).plusHours(3)
      val old    = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(now))
      val newVal = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(later))
      val patch  = Differ.diff(old, newVal)
      assertTrue(!patch.isEmpty)
    },
    test("Period diff") {
      val old    = DynamicValue.Primitive(PrimitiveValue.Period(Period.ofMonths(1)))
      val newVal = DynamicValue.Primitive(PrimitiveValue.Period(Period.ofMonths(6)))
      val patch  = Differ.diff(old, newVal)
      assertTrue(!patch.isEmpty)
    }
  )

  // ===========================================================================
  // Render Op Tests - toString coverage
  // ===========================================================================
  val renderOpTests = suite("Render operation strings")(
    test("Render Set operation") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch
            .DynamicPatchOp(DynamicOptic.root.field("x"), Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
      )
      assertTrue(patch.toString.contains(".x"))
    },
    test("Render IntDelta positive") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(100)))
        )
      )
      assertTrue(patch.toString.contains("+="))
    },
    test("Render IntDelta negative") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(-100)))
        )
      )
      assertTrue(patch.toString.contains("-="))
    },
    test("Render LongDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(100L)))
        )
      )
      assertTrue(patch.toString.contains("+="))
    },
    test("Render DoubleDelta negative") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(-3.14)))
        )
      )
      assertTrue(patch.toString.contains("-="))
    },
    test("Render FloatDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.FloatDelta(2.5f)))
        )
      )
      assertTrue(patch.toString.contains("2.5"))
    },
    test("Render ShortDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.ShortDelta(100.toShort)))
        )
      )
      assertTrue(patch.toString.contains("100"))
    },
    test("Render ByteDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.ByteDelta(10.toByte)))
        )
      )
      assertTrue(patch.toString.contains("10"))
    },
    test("Render BigIntDelta positive") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.BigIntDelta(BigInt(123))))
        )
      )
      assertTrue(patch.toString.contains("+="))
    },
    test("Render BigIntDelta negative") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch
            .DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.BigIntDelta(BigInt(-123))))
        )
      )
      assertTrue(patch.toString.contains("-="))
    },
    test("Render BigDecimalDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.PrimitiveDelta(PrimitiveOp.BigDecimalDelta(BigDecimal("123.45")))
          )
        )
      )
      assertTrue(patch.toString.contains("123.45"))
    },
    test("Render InstantDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.PrimitiveDelta(PrimitiveOp.InstantDelta(Duration.ofSeconds(3600)))
          )
        )
      )
      assertTrue(patch.toString.contains("PT"))
    },
    test("Render DurationDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch
            .DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.DurationDelta(Duration.ofHours(2))))
        )
      )
      assertTrue(patch.toString.contains("PT2H"))
    },
    test("Render LocalDateDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch
            .DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.LocalDateDelta(Period.ofDays(10))))
        )
      )
      assertTrue(patch.toString.contains("P10D"))
    },
    test("Render LocalDateTimeDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.PrimitiveDelta(PrimitiveOp.LocalDateTimeDelta(Period.ofMonths(1), Duration.ofHours(2)))
          )
        )
      )
      assertTrue(patch.toString.contains("P1M"))
    },
    test("Render PeriodDelta") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch
            .DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.PeriodDelta(Period.of(1, 2, 3))))
        )
      )
      assertTrue(patch.toString.contains("P1Y2M3D"))
    },
    test("Render StringEdit") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.PrimitiveDelta(
              PrimitiveOp.StringEdit(
                Vector(
                  StringOp.Insert(0, "hello"),
                  StringOp.Delete(5, 3),
                  StringOp.Append("world"),
                  StringOp.Modify(0, 2, "HI")
                )
              )
            )
          )
        )
      )
      val str = patch.toString
      assertTrue(str.contains("+") && str.contains("-") && str.contains("~"))
    },
    test("Render SequenceEdit insert") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.SequenceEdit(
              Vector(
                SeqOp.Insert(0, Chunk.fromArray(Array(DynamicValue.Primitive(PrimitiveValue.Int(1)): DynamicValue)))
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("+"))
    },
    test("Render SequenceEdit append") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.SequenceEdit(
              Vector(
                SeqOp.Append(Chunk.fromArray(Array(DynamicValue.Primitive(PrimitiveValue.Int(1)): DynamicValue)))
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("+"))
    },
    test("Render SequenceEdit delete single") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.SequenceEdit(
              Vector(
                SeqOp.Delete(5, 1)
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("-"))
    },
    test("Render SequenceEdit delete multiple") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.SequenceEdit(
              Vector(
                SeqOp.Delete(5, 3)
              )
            )
          )
        )
      )
      // Note: checking for both "-" and "," depends on render logic
      assertTrue(patch.toString.contains("-"))
    },
    test("Render SequenceEdit modify with Set") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.SequenceEdit(
              Vector(
                SeqOp.Modify(0, Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))))
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("~"))
    },
    test("Render SequenceEdit modify with nested") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.SequenceEdit(
              Vector(
                SeqOp.Modify(0, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)))
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("~"))
    },
    test("Render MapEdit add") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.MapEdit(
              Vector(
                MapOp.Add(
                  DynamicValue.Primitive(PrimitiveValue.String("key")),
                  DynamicValue.Primitive(PrimitiveValue.Int(1))
                )
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("+"))
    },
    test("Render MapEdit remove") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.MapEdit(
              Vector(
                MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("key")))
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("-"))
    },
    test("Render MapEdit modify") {
      val innerPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root, Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
      )
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.MapEdit(
              Vector(
                MapOp.Modify(DynamicValue.Primitive(PrimitiveValue.String("key")), innerPatch)
              )
            )
          )
        )
      )
      assertTrue(patch.toString.contains("~"))
    },
    test("Render nested Patch operation") {
      val innerPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("inner"),
            Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
      )
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("outer"), Operation.Patch(innerPatch))
        )
      )
      assertTrue(patch.toString.contains("inner") && patch.toString.contains("outer"))
    },
    test("Render escapeString special chars") {
      val patch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            Operation.PrimitiveDelta(
              PrimitiveOp.StringEdit(
                Vector(
                  StringOp.Append("hello\n\t\r\"\\world"),
                  StringOp.Append("\b\f" + 3.toChar)
                )
              )
            )
          )
        )
      )
      val str = patch.toString
      assertTrue(str.contains("\\n") || str.contains("\\t"))
    }
  )
}
