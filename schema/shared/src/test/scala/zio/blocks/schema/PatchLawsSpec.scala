package zio.blocks.schema

import zio.test._

/**
 * Property-based tests for Patch & Diffing laws as specified in Issue #516.
 * Tests the roundtrip law and monoid laws.
 */
object PatchLawsSpec extends ZIOSpecDefault {

  def spec = suite("PatchLawsSpec")(
    suite("Roundtrip Law")(
      test("diff then apply returns new value for primitives") {
        // Roundtrip Law: schema.diff(old, new).apply(old) == Right(new)
        val oldValue = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val newValue = DynamicValue.Primitive(PrimitiveValue.Int(20))

        val patch  = Differ.diff(oldValue, newValue)
        val result = patch.apply(oldValue, PatchMode.Strict)

        assertTrue(result == Right(newValue))
      },
      test("diff then apply returns new value for strings") {
        val oldValue = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val newValue = DynamicValue.Primitive(PrimitiveValue.String("hello world"))

        val patch  = Differ.diff(oldValue, newValue)
        val result = patch.apply(oldValue, PatchMode.Strict)

        assertTrue(result == Right(newValue))
      },
      test("diff then apply returns new value for records") {
        val oldRecord = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val newRecord = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(31))
          )
        )

        val patch  = Differ.diff(oldRecord, newRecord)
        val result = patch.apply(oldRecord, PatchMode.Strict)

        assertTrue(result == Right(newRecord))
      },
      test("diff of identical values produces empty patch") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = Differ.diff(value, value)

        assertTrue(patch.ops.isEmpty)
      }
    ),
    suite("Monoid Laws")(
      test("identity law - empty ++ p == p") {
        val p = DynamicPatch.set(DynamicValue.Primitive(PrimitiveValue.Int(10)))

        assertTrue((DynamicPatch.empty ++ p) == p)
      },
      test("identity law - p ++ empty == p") {
        val p = DynamicPatch.set(DynamicValue.Primitive(PrimitiveValue.Int(10)))

        assertTrue((p ++ DynamicPatch.empty) == p)
      },
      test("associativity law - (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        val p1 = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1)))
        val p2 = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(2)))
        val p3 = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(3)))

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        assertTrue(left == right)
      }
    ),
    suite("Smart Diffing Heuristics")(
      test("uses IntDelta for integer changes") {
        val oldValue = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val newValue = DynamicValue.Primitive(PrimitiveValue.Int(15))

        val patch = Differ.diff(oldValue, newValue)

        // Should use delta, not set
        assertTrue(patch.ops.exists {
          case DynamicPatch.DynamicPatchOp(_, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5))) => true
          case _                                                                                 => false
        })
      },
      test("uses LongDelta for long changes") {
        val oldValue = DynamicValue.Primitive(PrimitiveValue.Long(100L))
        val newValue = DynamicValue.Primitive(PrimitiveValue.Long(150L))

        val patch = Differ.diff(oldValue, newValue)

        assertTrue(patch.ops.exists {
          case DynamicPatch.DynamicPatchOp(_, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(50L))) => true
          case _                                                                                    => false
        })
      },
      test("uses DoubleDelta for double changes") {
        val oldValue = DynamicValue.Primitive(PrimitiveValue.Double(1.0))
        val newValue = DynamicValue.Primitive(PrimitiveValue.Double(2.5))

        val patch = Differ.diff(oldValue, newValue)

        assertTrue(patch.ops.exists {
          case DynamicPatch.DynamicPatchOp(_, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(delta))) =>
            Math.abs(delta - 1.5) < 0.001
          case _ => false
        })
      }
    ),
    suite("Sequence Diffing")(
      test("uses LCS for sequence changes") {
        val oldSeq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val newSeq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(4)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )

        val patch  = Differ.diff(oldSeq, newSeq)
        val result = patch.apply(oldSeq, PatchMode.Strict)

        assertTrue(result == Right(newSeq))
      }
    ),
    suite("Map Diffing")(
      test("detects added keys") {
        val oldMap = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")) ->
              DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val newMap = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")) ->
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) ->
              DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )

        val patch  = Differ.diff(oldMap, newMap)
        val result = patch.apply(oldMap, PatchMode.Strict)

        assertTrue(result == Right(newMap))
      },
      test("detects removed keys") {
        val oldMap = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")) ->
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) ->
              DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val newMap = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")) ->
              DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )

        val patch  = Differ.diff(oldMap, newMap)
        val result = patch.apply(oldMap, PatchMode.Strict)

        assertTrue(result == Right(newMap))
      }
    )
  )
}
