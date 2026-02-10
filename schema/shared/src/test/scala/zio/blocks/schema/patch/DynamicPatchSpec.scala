package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

object DynamicPatchSpec extends SchemaBaseSpec {

  // Helper to create primitive DynamicValues
  def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  def longVal(n: Long): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.Long(n))
  def doubleVal(n: Double): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Double(n))
  def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  def boolVal(b: Boolean): DynamicValue  = DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  // Helper to create a simple record
  def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  // Helper to create a nested record
  def personWithAddressRecord(name: String, age: Int, city: String): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name"    -> stringVal(name),
        "age"     -> intVal(age),
        "address" -> DynamicValue.Record(
          Chunk(
            "city" -> stringVal(city)
          )
        )
      )
    )

  def spec: Spec[Any, Any] = suite("DynamicPatchSpec")(
    suite("Operation.Set")(
      test("applies Set to Primitive at root") {
        val original = intVal(42)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.Set(intVal(100)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(100)))
      },
      test("applies Set to Record field") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          DynamicOptic.root.field("age"),
          DynamicPatch.Operation.Set(intVal(31))
        )
        val result = patch(original)
        assertTrue(result == Right(personRecord("Alice", 31)))
      },
      test("applies Set to Record field using path interpolator") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          p".age",
          DynamicPatch.Operation.Set(intVal(31))
        )
        val result = patch(original)
        assertTrue(result == Right(personRecord("Alice", 31)))
      },
      test("applies Set to nested Record field") {
        val original = personWithAddressRecord("Alice", 30, "NYC")
        val patch    = DynamicPatch(
          DynamicOptic.root.field("address").field("city"),
          DynamicPatch.Operation.Set(stringVal("LA"))
        )
        val result = patch(original)
        assertTrue(result == Right(personWithAddressRecord("Alice", 30, "LA")))
      },
      test("applies Set to nested Record field using path interpolator") {
        val original = personWithAddressRecord("Alice", 30, "NYC")
        val patch    = DynamicPatch(
          p".address.city",
          DynamicPatch.Operation.Set(stringVal("LA"))
        )
        val result = patch(original)
        assertTrue(result == Right(personWithAddressRecord("Alice", 30, "LA")))
      },
      test("applies Set to sequence element") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          DynamicOptic.root.at(1),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(99), intVal(3)))))
      },
      test("applies Set to sequence element using path interpolator") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          p"[1]",
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(99), intVal(3)))))
      },
      test("applies Set to map value") {
        val original = DynamicValue.Map(
          Chunk(
            stringVal("a") -> intVal(1),
            stringVal("b") -> intVal(2)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(stringVal("b")))),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Chunk(
                stringVal("a") -> intVal(1),
                stringVal("b") -> intVal(99)
              )
            )
          )
        )
      }
    ),
    suite("PrimitiveDelta")(
      test("applies IntDelta to Int") {
        val original = intVal(42)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(52)))
      },
      test("applies negative IntDelta (decrement)") {
        val original = intVal(42)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(-10)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(32)))
      },
      test("applies LongDelta to Long") {
        val original = longVal(1000L)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(500L)))
        val result   = patch(original)
        assertTrue(result == Right(longVal(1500L)))
      },
      test("applies negative LongDelta (decrement)") {
        val original = longVal(1000L)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(-500L)))
        val result   = patch(original)
        assertTrue(result == Right(longVal(500L)))
      },
      test("applies DoubleDelta to Double") {
        val original = doubleVal(3.0)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(1.5)))
        val result   = patch(original)
        assertTrue(result == Right(doubleVal(4.5)))
      },
      test("applies negative DoubleDelta (decrement)") {
        val original = doubleVal(5.0)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(-2.5)))
        val result   = patch(original)
        assertTrue(result == Right(doubleVal(2.5)))
      },
      test("applies StringEdit Insert") {
        val original = stringVal("Hello World")
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Insert(5, " Beautiful")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello Beautiful World")))
      },
      test("applies StringEdit Delete") {
        val original = stringVal("Hello Beautiful World")
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Delete(5, 10)))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello World")))
      },
      test("applies multiple StringEdits in sequence") {
        val original = stringVal("abc")
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(
              Chunk(
                DynamicPatch.StringOp.Insert(1, "X"), // "aXbc"
                DynamicPatch.StringOp.Delete(3, 1)    // "aXb"
              )
            )
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("aXb")))
      },
      test("applies StringEdit Append") {
        val original = stringVal("Hello")
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Append(" Golem")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello Golem")))
      },
      test("applies StringEdit Modify") {
        val original = stringVal("Hello World")
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Modify(6, 5, "everyone")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello everyone")))
      },
      test("fails on type mismatch") {
        val original = intVal(42)
        val patch    = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(10L)))
        val result   = patch(original)
        assertTrue(result.isLeft)
      }
    ),
    suite("SequenceEdit")(
      test("applies Append") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Append(Chunk(intVal(3), intVal(4)))))
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3), intVal(4))))
        )
      },
      test("applies Append to empty sequence") {
        val original = DynamicValue.Sequence(Chunk.empty)
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Append(Chunk(intVal(1)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1)))))
      },
      test("applies Insert at beginning") {
        val original = DynamicValue.Sequence(Chunk(intVal(2), intVal(3)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Insert(0, Chunk(intVal(1)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))))
      },
      test("applies Insert in middle") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(3)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Insert(1, Chunk(intVal(2)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))))
      },
      test("applies Insert at end") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Insert(2, Chunk(intVal(3)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))))
      },
      test("applies Delete") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3), intVal(4)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Delete(1, 2)))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(4)))))
      },
      test("applies Delete at beginning") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Delete(0, 1)))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(2), intVal(3)))))
      },
      test("applies Modify to sequence element") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(
            Chunk(
              DynamicPatch.SeqOp.Modify(1, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10)))
            )
          )
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(12), intVal(3)))))
      },
      test("applies multiple sequence operations") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(
            Chunk(
              DynamicPatch.SeqOp.Append(Chunk(intVal(3))),
              DynamicPatch.SeqOp.Insert(0, Chunk(intVal(0)))
            )
          )
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Sequence(Chunk(intVal(0), intVal(1), intVal(2), intVal(3))))
        )
      }
    ),
    suite("MapEdit")(
      test("applies Add to empty map") {
        val original = DynamicValue.Map(Chunk.empty)
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Add(stringVal("key"), intVal(42))))
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Map(Chunk(stringVal("key") -> intVal(42))))
        )
      },
      test("applies Add to existing map") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Add(stringVal("b"), intVal(2))))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Chunk(
                stringVal("a") -> intVal(1),
                stringVal("b") -> intVal(2)
              )
            )
          )
        )
      },
      test("applies Remove") {
        val original = DynamicValue.Map(
          Chunk(
            stringVal("a") -> intVal(1),
            stringVal("b") -> intVal(2)
          )
        )
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Remove(stringVal("a"))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Map(Chunk(stringVal("b") -> intVal(2)))))
      },
      test("applies Modify to map value") {
        val original = DynamicValue.Map(Chunk(stringVal("count") -> intVal(10)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(
            Chunk(
              DynamicPatch.MapOp
                .Modify(
                  stringVal("count"),
                  DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
                )
            )
          )
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Map(Chunk(stringVal("count") -> intVal(15)))))
      },
      test("applies multiple map operations") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(
            Chunk(
              DynamicPatch.MapOp.Add(stringVal("b"), intVal(2)),
              DynamicPatch.MapOp.Remove(stringVal("a"))
            )
          )
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Map(Chunk(stringVal("b") -> intVal(2)))))
      }
    ),
    suite("Patch Composition")(
      test("combines operations with ++") {
        val patch1 = DynamicPatch(
          DynamicOptic.root.field("age"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val patch2 = DynamicPatch(
          DynamicOptic.root.field("name"),
          DynamicPatch.Operation.Set(stringVal("Bob"))
        )
        val combined = patch1 ++ patch2

        val original = personRecord("Alice", 30)
        val result   = combined(original)
        assertTrue(result == Right(personRecord("Bob", 31)))
      },
      test("empty is identity (left)") {
        val patch    = DynamicPatch.root(DynamicPatch.Operation.Set(intVal(42)))
        val combined = DynamicPatch.empty ++ patch
        assertTrue(combined.ops == patch.ops)
      },
      test("empty is identity (right)") {
        val patch    = DynamicPatch.root(DynamicPatch.Operation.Set(intVal(42)))
        val combined = patch ++ DynamicPatch.empty
        assertTrue(combined.ops == patch.ops)
      },
      test("associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        val p1 = DynamicPatch(DynamicOptic.root.field("a"), DynamicPatch.Operation.Set(intVal(1)))
        val p2 = DynamicPatch(DynamicOptic.root.field("b"), DynamicPatch.Operation.Set(intVal(2)))
        val p3 = DynamicPatch(DynamicOptic.root.field("c"), DynamicPatch.Operation.Set(intVal(3)))

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        assertTrue(left.ops == right.ops)
      }
    ),
    suite("PatchMode.Strict")(
      test("fails on missing field") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          DynamicOptic.root.field("nonexistent"),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on non-existent map key for Remove") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Remove(stringVal("nonexistent"))))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on duplicate key for Add") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Add(stringVal("a"), intVal(2))))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on out of bounds sequence index") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch(
          DynamicOptic.root.at(10),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on invalid Insert index") {
        val original = DynamicValue.Sequence(Chunk(intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Insert(10, Chunk(intVal(2)))))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on invalid Delete range") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Delete(0, 10)))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on SeqOp.Modify with out-of-bounds index") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(
            Chunk(
              DynamicPatch.SeqOp.Modify(10, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
            )
          )
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on SeqOp.Modify with negative index") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(
            Chunk(
              DynamicPatch.SeqOp.Modify(-1, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
            )
          )
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on MapOp.Modify when key not found") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(
            Chunk(
              DynamicPatch.MapOp.Modify(
                stringVal("nonexistent"),
                DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
              )
            )
          )
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails when applying Elements node to non-sequence (Record)") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          DynamicOptic(Vector(DynamicOptic.Node.Elements)),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails when applying Elements node to non-sequence (Primitive)") {
        val original = intVal(42)
        val patch    = DynamicPatch(
          DynamicOptic(Vector(DynamicOptic.Node.Elements)),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("PatchMode.Lenient")(
      test("skips operation on missing field") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("nonexistent"), DynamicPatch.Operation.Set(intVal(99))),
            DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("age"), DynamicPatch.Operation.Set(intVal(31)))
          )
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(result == Right(personRecord("Alice", 31)))
      },
      test("skips Remove on non-existent key") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(
            Chunk(
              DynamicPatch.MapOp.Remove(stringVal("nonexistent")),
              DynamicPatch.MapOp.Add(stringVal("b"), intVal(2))
            )
          )
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Chunk(
                stringVal("a") -> intVal(1),
                stringVal("b") -> intVal(2)
              )
            )
          )
        )
      },
      test("skips Add on duplicate key") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(
            Chunk(
              DynamicPatch.MapOp.Add(stringVal("a"), intVal(99)),
              DynamicPatch.MapOp.Add(stringVal("b"), intVal(2))
            )
          )
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Chunk(
                stringVal("a") -> intVal(1),
                stringVal("b") -> intVal(2)
              )
            )
          )
        )
      }
    ),
    suite("PatchMode.Clobber")(
      test("overwrites on duplicate key for Add") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Add(stringVal("a"), intVal(99))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(DynamicValue.Map(Chunk(stringVal("a") -> intVal(99)))))
      },
      test("succeeds on Remove non-existent key (no-op)") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Remove(stringVal("nonexistent"))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(original))
      },
      test("clamps Insert index to valid range") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Insert(100, Chunk(intVal(3)))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(
          result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3))))
        )
      },
      test("clamps Delete range to valid range") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Delete(1, 100)))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1)))))
      }
    ),
    suite("DynamicPatch.empty")(
      test("is identity for apply") {
        val original = personRecord("Alice", 30)
        val result   = DynamicPatch.empty(original)
        assertTrue(result == Right(original))
      },
      test("isEmpty returns true") {
        assertTrue(DynamicPatch.empty.isEmpty)
      },
      test("non-empty patch isEmpty returns false") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.Set(intVal(42)))
        assertTrue(!patch.isEmpty)
      }
    ),
    suite("Nested operations")(
      test("modifies field in sequence element") {
        val original = DynamicValue.Sequence(
          Chunk(
            personRecord("Alice", 30),
            personRecord("Bob", 25)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.at(0).field("age"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              Chunk(
                personRecord("Alice", 31),
                personRecord("Bob", 25)
              )
            )
          )
        )
      },
      test("modifies field in map value") {
        val original = DynamicValue.Map(
          Chunk(
            stringVal("person1") -> personRecord("Alice", 30)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic(Chunk(DynamicOptic.Node.AtMapKey(stringVal("person1")), DynamicOptic.Node.Field("name"))),
          DynamicPatch.Operation.Set(stringVal("Alicia"))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Chunk(
                stringVal("person1") -> personRecord("Alicia", 30)
              )
            )
          )
        )
      },
      test("deeply nested modification") {
        val original = DynamicValue.Record(
          Chunk(
            "data" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Map(
                  Chunk(
                    stringVal("value") -> intVal(100)
                  )
                )
              )
            )
          )
        )
        val patch = DynamicPatch(
          DynamicOptic(
            Chunk(
              DynamicOptic.Node.Field("data"),
              DynamicOptic.Node.AtIndex(0),
              DynamicOptic.Node.AtMapKey(stringVal("value"))
            )
          ),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(50))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "data" -> DynamicValue.Sequence(
                  Chunk(
                    DynamicValue.Map(
                      Chunk(
                        stringVal("value") -> intVal(150)
                      )
                    )
                  )
                )
              )
            )
          )
        )
      }
    ),
    suite("Serialization")(
      test("DynamicOptic.Node.Field roundtrips through JSON") {
        roundTrip(
          DynamicOptic.Node.Field("name"): DynamicOptic.Node,
          """{"Field":{"name":"name"}}"""
        )
      },
      test("DynamicOptic.Node.AtIndex roundtrips through JSON") {
        roundTrip(
          DynamicOptic.Node.AtIndex(42): DynamicOptic.Node,
          """{"AtIndex":{"index":42}}"""
        )
      },
      test("DynamicOptic.Node.AtMapKey roundtrips through JSON") {
        roundTrip(
          DynamicOptic.Node.AtMapKey(stringVal("myKey")): DynamicOptic.Node,
          """{"AtMapKey":{"key":"myKey"}}"""
        )
      },
      test("DynamicOptic roundtrips through JSON") {
        roundTrip(
          DynamicOptic.root.field("name").at(0),
          """{"nodes":[{"Field":{"name":"name"}},{"AtIndex":{"index":0}}]}"""
        )
      },
      test("DynamicOptic.root roundtrips through JSON") {
        roundTrip(
          DynamicOptic.root,
          """{}"""
        )
      },
      test("DynamicPatchOp roundtrips through JSON") {
        roundTrip(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("age"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
          ),
          """{"path":{"nodes":[{"Field":{"name":"age"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}"""
        )
      },
      test("DynamicPatchOp with empty path roundtrips") {
        roundTrip(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.Set(intVal(42))
          ),
          """{"path":{},"operation":{"Set":{"value":42}}}"""
        )
      },
      test("DynamicPatch roundtrips through JSON") {
        roundTrip(
          DynamicPatch(
            Chunk(
              DynamicPatch.DynamicPatchOp(DynamicOptic.root.field("name"), DynamicPatch.Operation.Set(stringVal("Bob")))
            )
          ),
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"name"}}]},"operation":{"Set":{"value":"Bob"}}}]}"""
        )
      },
      test("DynamicPatch.empty roundtrips through JSON") {
        roundTrip(
          DynamicPatch.empty,
          """{}"""
        )
      }
    ),
    suite("Edge cases")(
      test("empty path applies to root") {
        val original = intVal(42)
        val patch    = DynamicPatch(DynamicOptic.root, DynamicPatch.Operation.Set(intVal(100)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(100)))
      },
      test("unicode strings in field names") {
        val original = DynamicValue.Record(Chunk("名前" -> stringVal("Alice")))
        val patch    = DynamicPatch(
          DynamicOptic.root.field("名前"),
          DynamicPatch.Operation.Set(stringVal("アリス"))
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Record(Chunk("名前" -> stringVal("アリス"))))
        )
      },
      test("unicode strings in values") {
        val original = stringVal("Hello")
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Insert(5, " 世界")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello 世界")))
      },
      test("empty string operations") {
        val original = stringVal("")
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Insert(0, "Hello")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello")))
      },
      test("type mismatch: apply to wrong DynamicValue type") {
        val original = intVal(42)
        val patch    = DynamicPatch(
          DynamicOptic.root.field("name"),
          DynamicPatch.Operation.Set(stringVal("Alice"))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Sequence operation on non-sequence fails") {
        val original = intVal(42)
        val patch    =
          DynamicPatch.root(DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Append(Chunk(intVal(1))))))
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Map operation on non-map fails") {
        val original = intVal(42)
        val patch    =
          DynamicPatch.root(DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Add(stringVal("a"), intVal(1)))))
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("Operation.Patch (nested patches)")(
      test("applies nested patch to update multiple fields in a nested record") {
        // Original: Person with Address(street="123 Main", city="NYC", zip="10001")
        val original = DynamicValue.Record(
          Chunk(
            "name"    -> stringVal("Alice"),
            "address" -> DynamicValue.Record(
              Chunk(
                "street" -> stringVal("123 Main"),
                "city"   -> stringVal("NYC"),
                "zip"    -> stringVal("10001")
              )
            )
          )
        )

        // Nested patch: Update all address fields using Operation.Patch
        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("street"),
              DynamicPatch.Operation.Set(stringVal("456 Elm"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("city"),
              DynamicPatch.Operation.Set(stringVal("LA"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("zip"),
              DynamicPatch.Operation.Set(stringVal("90002"))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address"),
              DynamicPatch.Operation.Patch(nestedPatch)
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "name"    -> stringVal("Alice"),
            "address" -> DynamicValue.Record(
              Chunk(
                "street" -> stringVal("456 Elm"),
                "city"   -> stringVal("LA"),
                "zip"    -> stringVal("90002")
              )
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(expected))
      },
      test("flat vs nested approach produces same result") {
        // Test that both approaches produce identical results
        val original = DynamicValue.Record(
          Chunk(
            "name"    -> stringVal("Alice"),
            "address" -> DynamicValue.Record(
              Chunk(
                "street" -> stringVal("123 Main"),
                "city"   -> stringVal("NYC"),
                "zip"    -> stringVal("10001")
              )
            )
          )
        )

        // Flat approach: Each operation has full path
        val flatPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address").field("street"),
              DynamicPatch.Operation.Set(stringVal("456 Elm"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address").field("city"),
              DynamicPatch.Operation.Set(stringVal("LA"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address").field("zip"),
              DynamicPatch.Operation.Set(stringVal("90002"))
            )
          )
        )

        // Nested approach: Path to parent + nested patch for children
        val nestedPatchOps = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("street"),
              DynamicPatch.Operation.Set(stringVal("456 Elm"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("city"),
              DynamicPatch.Operation.Set(stringVal("LA"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("zip"),
              DynamicPatch.Operation.Set(stringVal("90002"))
            )
          )
        )

        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address"),
              DynamicPatch.Operation.Patch(nestedPatchOps)
            )
          )
        )

        val flatResult   = flatPatch(original)
        val nestedResult = nestedPatch(original)

        assertTrue(
          flatResult.isRight,
          nestedResult.isRight,
          flatResult == nestedResult
        )
      },
      test("nested patch with empty operations acts as no-op") {
        val original = DynamicValue.Record(
          Chunk(
            "name"    -> stringVal("Alice"),
            "address" -> DynamicValue.Record(
              Chunk(
                "city" -> stringVal("NYC")
              )
            )
          )
        )

        val emptyNestedPatch = DynamicPatch(Chunk.empty)
        val patch            = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address"),
              DynamicPatch.Operation.Patch(emptyNestedPatch)
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(original))
      },
      test("nested patch with single operation") {
        val original = DynamicValue.Record(
          Chunk(
            "name"    -> stringVal("Alice"),
            "address" -> DynamicValue.Record(
              Chunk(
                "city" -> stringVal("NYC")
              )
            )
          )
        )

        val singleOpPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("city"),
              DynamicPatch.Operation.Set(stringVal("LA"))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address"),
              DynamicPatch.Operation.Patch(singleOpPatch)
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "name"    -> stringVal("Alice"),
            "address" -> DynamicValue.Record(
              Chunk(
                "city" -> stringVal("LA")
              )
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(expected))
      },
      test("nested patch with increment operations") {
        val original = DynamicValue.Record(
          Chunk(
            "stats" -> DynamicValue.Record(
              Chunk(
                "count"  -> intVal(10),
                "errors" -> intVal(2)
              )
            )
          )
        )

        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("count"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("errors"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("stats"),
              DynamicPatch.Operation.Patch(nestedPatch)
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "stats" -> DynamicValue.Record(
              Chunk(
                "count"  -> intVal(15),
                "errors" -> intVal(3)
              )
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(expected))
      },
      test("nested patch respects PatchMode.Strict on failures") {
        val original = DynamicValue.Record(
          Chunk(
            "address" -> DynamicValue.Record(
              Chunk(
                "city" -> stringVal("NYC")
              )
            )
          )
        )

        // Nested patch tries to set a non-existent field
        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              DynamicPatch.Operation.Set(stringVal("value"))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address"),
              DynamicPatch.Operation.Patch(nestedPatch)
            )
          )
        )

        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("nested patch respects PatchMode.Lenient on failures") {
        val original = DynamicValue.Record(
          Chunk(
            "address" -> DynamicValue.Record(
              Chunk(
                "city" -> stringVal("NYC")
              )
            )
          )
        )

        // Nested patch tries to set a non-existent field
        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              DynamicPatch.Operation.Set(stringVal("value"))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("address"),
              DynamicPatch.Operation.Patch(nestedPatch)
            )
          )
        )

        val result = patch(original, PatchMode.Lenient)
        // Should succeed but skip the invalid operation
        assertTrue(result == Right(original))
      },
      test("recursive patch: patch within patch within patch (3 levels deep)") {
        // Structure: Company -> Department -> Team -> Members
        val original = DynamicValue.Record(
          Chunk(
            "company" -> DynamicValue.Record(
              Chunk(
                "name"       -> stringVal("Acme Corp"),
                "department" -> DynamicValue.Record(
                  Chunk(
                    "name" -> stringVal("Engineering"),
                    "team" -> DynamicValue.Record(
                      Chunk(
                        "name"    -> stringVal("Backend"),
                        "members" -> intVal(5),
                        "lead"    -> stringVal("Alice")
                      )
                    )
                  )
                )
              )
            )
          )
        )

        // Level 3: Patch for team (innermost)
        val teamPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("members"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(2))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("lead"),
              DynamicPatch.Operation.Set(stringVal("Bob"))
            )
          )
        )

        // Level 2: Patch for department (wraps team patch)
        val departmentPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("team"),
              DynamicPatch.Operation.Patch(teamPatch)
            )
          )
        )

        // Level 1: Patch for company (wraps department patch)
        val companyPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("company"),
              DynamicPatch.Operation.Patch(
                DynamicPatch(
                  Chunk(
                    DynamicPatch.DynamicPatchOp(
                      DynamicOptic.root.field("department"),
                      DynamicPatch.Operation.Patch(departmentPatch)
                    )
                  )
                )
              )
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "company" -> DynamicValue.Record(
              Chunk(
                "name"       -> stringVal("Acme Corp"),
                "department" -> DynamicValue.Record(
                  Chunk(
                    "name" -> stringVal("Engineering"),
                    "team" -> DynamicValue.Record(
                      Chunk(
                        "name"    -> stringVal("Backend"),
                        "members" -> intVal(7), // 5 + 2
                        "lead"    -> stringVal("Bob")
                      )
                    )
                  )
                )
              )
            )
          )
        )

        val result = companyPatch(original)
        assertTrue(result == Right(expected))
      },
      test("nested patch with mixed operation types") {
        // Test combining Set, PrimitiveDelta, SequenceEdit in nested patch
        val original = DynamicValue.Record(
          Chunk(
            "user" -> DynamicValue.Record(
              Chunk(
                "name"   -> stringVal("Alice"),
                "score"  -> intVal(100),
                "tags"   -> DynamicValue.Sequence(Chunk(stringVal("admin"))),
                "active" -> boolVal(true)
              )
            )
          )
        )

        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("name"),
              DynamicPatch.Operation.Set(stringVal("Bob"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("score"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(50))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("tags"),
              DynamicPatch.Operation.SequenceEdit(
                Chunk(
                  DynamicPatch.SeqOp.Append(Chunk(stringVal("premium"), stringVal("verified")))
                )
              )
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("active"),
              DynamicPatch.Operation.Set(boolVal(false))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("user"),
              DynamicPatch.Operation.Patch(nestedPatch)
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "user" -> DynamicValue.Record(
              Chunk(
                "name"  -> stringVal("Bob"),
                "score" -> intVal(150),
                "tags"  -> DynamicValue.Sequence(
                  Chunk(stringVal("admin"), stringVal("premium"), stringVal("verified"))
                ),
                "active" -> boolVal(false)
              )
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(expected))
      },
      test("nested patch on sequence element") {
        // Apply nested patch to an element within a sequence
        val original = DynamicValue.Record(
          Chunk(
            "users" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Record(
                  Chunk(
                    "name"  -> stringVal("Alice"),
                    "score" -> intVal(100)
                  )
                ),
                DynamicValue.Record(
                  Chunk(
                    "name"  -> stringVal("Bob"),
                    "score" -> intVal(150)
                  )
                )
              )
            )
          )
        )

        // Update Bob's record (index 1) using nested patch
        val userPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("name"),
              DynamicPatch.Operation.Set(stringVal("Robert"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("score"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(50))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("users").at(1),
              DynamicPatch.Operation.Patch(userPatch)
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "users" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Record(
                  Chunk(
                    "name"  -> stringVal("Alice"),
                    "score" -> intVal(100)
                  )
                ),
                DynamicValue.Record(
                  Chunk(
                    "name"  -> stringVal("Robert"),
                    "score" -> intVal(200)
                  )
                )
              )
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(expected))
      },
      test("nested patch on map value") {
        // Apply nested patch to a value within a map
        val original = DynamicValue.Record(
          Chunk(
            "settings" -> DynamicValue.Map(
              Chunk(
                stringVal("theme") -> DynamicValue.Record(
                  Chunk(
                    "color"      -> stringVal("blue"),
                    "brightness" -> intVal(80)
                  )
                ),
                stringVal("privacy") -> DynamicValue.Record(
                  Chunk(
                    "public" -> boolVal(true)
                  )
                )
              )
            )
          )
        )

        // Update theme settings using nested patch
        val themePatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("color"),
              DynamicPatch.Operation.Set(stringVal("red"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("brightness"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(20))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("settings"), DynamicOptic.Node.AtMapKey(stringVal("theme")))),
              DynamicPatch.Operation.Patch(themePatch)
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "settings" -> DynamicValue.Map(
              Chunk(
                stringVal("theme") -> DynamicValue.Record(
                  Chunk(
                    "color"      -> stringVal("red"),
                    "brightness" -> intVal(100)
                  )
                ),
                stringVal("privacy") -> DynamicValue.Record(
                  Chunk(
                    "public" -> boolVal(true)
                  )
                )
              )
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(expected))
      },
      test("complex whole picture: multiple nested patches at different levels") {
        // Complex scenario: Update multiple nested structures in one patch
        val original = DynamicValue.Record(
          Chunk(
            "metadata" -> DynamicValue.Record(
              Chunk(
                "version" -> stringVal("1.0.0"),
                "author"  -> DynamicValue.Record(
                  Chunk(
                    "name"  -> stringVal("Alice"),
                    "email" -> stringVal("alice@example.com")
                  )
                )
              )
            ),
            "stats" -> DynamicValue.Record(
              Chunk(
                "views"     -> intVal(1000),
                "downloads" -> intVal(50)
              )
            )
          )
        )

        // Patch for metadata.author
        val authorPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("name"),
              DynamicPatch.Operation.Set(stringVal("Alice Smith"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("email"),
              DynamicPatch.Operation.Set(stringVal("alice.smith@example.com"))
            )
          )
        )

        // Patch for stats
        val statsPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("views"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(500))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("downloads"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(25))
            )
          )
        )

        // Combine everything
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("metadata").field("version"),
              DynamicPatch.Operation.Set(stringVal("2.0.0"))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("metadata").field("author"),
              DynamicPatch.Operation.Patch(authorPatch)
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("stats"),
              DynamicPatch.Operation.Patch(statsPatch)
            )
          )
        )

        val expected = DynamicValue.Record(
          Chunk(
            "metadata" -> DynamicValue.Record(
              Chunk(
                "version" -> stringVal("2.0.0"),
                "author"  -> DynamicValue.Record(
                  Chunk(
                    "name"  -> stringVal("Alice Smith"),
                    "email" -> stringVal("alice.smith@example.com")
                  )
                )
              )
            ),
            "stats" -> DynamicValue.Record(
              Chunk(
                "views"     -> intVal(1500),
                "downloads" -> intVal(75)
              )
            )
          )
        )

        val result = patch(original)
        assertTrue(result == Right(expected))
      }
    ),
    suite("Elements traversal")(
      test("applies operation to all elements via Elements node") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          DynamicOptic.root.elements,
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(11), intVal(12), intVal(13)))))
      },
      test("fails on empty sequence in Strict mode") {
        val original = DynamicValue.Sequence(Chunk.empty)
        val patch    = DynamicPatch(
          DynamicOptic.root.elements,
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("returns unchanged on empty sequence in Lenient mode") {
        val original = DynamicValue.Sequence(Chunk.empty)
        val patch    = DynamicPatch(
          DynamicOptic.root.elements,
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(result == Right(original))
      },
      test("applies nested path through elements") {
        val original = DynamicValue.Sequence(
          Chunk(
            personRecord("Alice", 30),
            personRecord("Bob", 25)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.elements.field("age"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              Chunk(
                personRecord("Alice", 31),
                personRecord("Bob", 26)
              )
            )
          )
        )
      }
    ),
    suite("DynamicPatch.toString")(
      test("renders Set operation") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.Set(intVal(42)))
        assertTrue(patch.toString.contains("=") && patch.toString.contains("42"))
      },
      test("renders IntDelta positive") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10)))
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("10"))
      },
      test("renders IntDelta negative") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(-10)))
        assertTrue(patch.toString.contains("-=") && patch.toString.contains("10"))
      },
      test("renders LongDelta") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(100L)))
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("100"))
      },
      test("renders LongDelta negative") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(-100L)))
        assertTrue(patch.toString.contains("-=") && patch.toString.contains("100"))
      },
      test("renders DoubleDelta") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(3.14)))
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("3.14"))
      },
      test("renders DoubleDelta negative") {
        val patch =
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(-3.14)))
        assertTrue(patch.toString.contains("-=") && patch.toString.contains("3.14"))
      },
      test("renders FloatDelta") {
        val patch = DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.FloatDelta(1.5f)))
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("1.5"))
      },
      test("renders ShortDelta") {
        val patch =
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ShortDelta(5.toShort)))
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("5"))
      },
      test("renders ByteDelta") {
        val patch =
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ByteDelta(3.toByte)))
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("3"))
      },
      test("renders BigIntDelta") {
        val patch =
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigIntDelta(BigInt(1000))))
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("1000"))
      },
      test("renders BigDecimalDelta") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal("99.99")))
        )
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("99.99"))
      },
      test("renders InstantDelta") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.InstantDelta(java.time.Duration.ofHours(2)))
        )
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("PT2H"))
      },
      test("renders DurationDelta") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.DurationDelta(java.time.Duration.ofMinutes(30))
          )
        )
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("PT30M"))
      },
      test("renders LocalDateDelta") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LocalDateDelta(java.time.Period.ofDays(7)))
        )
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("P7D"))
      },
      test("renders LocalDateTimeDelta") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.LocalDateTimeDelta(java.time.Period.ofDays(1), java.time.Duration.ofHours(2))
          )
        )
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("P1D") && patch.toString.contains("PT2H"))
      },
      test("renders PeriodDelta") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.PeriodDelta(java.time.Period.ofMonths(3)))
        )
        assertTrue(patch.toString.contains("+=") && patch.toString.contains("P3M"))
      },
      test("renders StringEdit with Insert") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Insert(5, "hello")))
          )
        )
        assertTrue(patch.toString.contains("[5") && patch.toString.contains("hello"))
      },
      test("renders StringEdit with Delete") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Delete(0, 5)))
          )
        )
        assertTrue(patch.toString.contains("-") && patch.toString.contains("[0"))
      },
      test("renders StringEdit with Append") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Append(" suffix")))
          )
        )
        assertTrue(patch.toString.contains("+") && patch.toString.contains("suffix"))
      },
      test("renders StringEdit with Modify") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Modify(2, 3, "new")))
          )
        )
        assertTrue(patch.toString.contains("~") && patch.toString.contains("new"))
      },
      test("renders StringEdit with escaped quotes") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Insert(0, "say \"hello\"")))
          )
        )
        val str = patch.toString
        assertTrue(str.nonEmpty && str.contains("\\\""))
      },
      test("renders StringEdit with escaped backslash") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Insert(0, "path\\to\\file")))
          )
        )
        val str = patch.toString
        assertTrue(str.nonEmpty && str.contains("\\\\"))
      },
      test("renders StringEdit with escaped newline") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Append("line1\nline2")))
          )
        )
        val str = patch.toString
        assertTrue(str.nonEmpty && str.contains("\\n"))
      },
      test("renders StringEdit with escaped tab") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Append("col1\tcol2")))
          )
        )
        val str = patch.toString
        assertTrue(str.nonEmpty && str.contains("\\t"))
      },
      test("renders empty patch") {
        val patch = DynamicPatch.empty
        assertTrue(patch.toString == "DynamicPatch {}")
      },
      test("renders SequenceEdit with Modify") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(
            Chunk(DynamicPatch.SeqOp.Modify(0, DynamicPatch.Operation.Set(intVal(99))))
          )
        )
        assertTrue(patch.toString.contains("~") && patch.toString.contains("[0"))
      },
      test("renders SequenceEdit with nested operation") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(
            Chunk(
              DynamicPatch.SeqOp.Modify(
                0,
                DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
              )
            )
          )
        )
        assertTrue(patch.toString.contains("~") && patch.toString.contains("[0"))
      },
      test("renders MapEdit with Modify") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(
            Chunk(
              DynamicPatch.MapOp.Modify(
                stringVal("key"),
                DynamicPatch(
                  Chunk(DynamicPatch.DynamicPatchOp(DynamicOptic.root, DynamicPatch.Operation.Set(intVal(1))))
                )
              )
            )
          )
        )
        assertTrue(patch.toString.contains("~") && patch.toString.contains("key"))
      }
    ),
    suite("PatchMode.Clobber edge cases")(
      test("clobber mode clamps insert index") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Insert(100, Chunk(intVal(99)))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(99)))))
      },
      test("clobber mode handles delete out of bounds") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))
        val patch    = DynamicPatch.root(
          DynamicPatch.Operation.SequenceEdit(Chunk(DynamicPatch.SeqOp.Delete(100, 5)))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(DynamicValue.Sequence(Chunk(intVal(1), intVal(2)))))
      },
      test("clobber mode overwrites existing map key on Add") {
        val original = DynamicValue.Map(
          Chunk(
            stringVal("a") -> intVal(1)
          )
        )
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Add(stringVal("a"), intVal(99))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(DynamicValue.Map(Chunk(stringVal("a") -> intVal(99)))))
      },
      test("clobber mode handles remove for non-existent key") {
        val original = DynamicValue.Map(
          Chunk(
            stringVal("a") -> intVal(1)
          )
        )
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Remove(stringVal("nonexistent"))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(original))
      }
    ),
    suite("Wrapped node")(
      test("Wrapped node applies operation to value") {
        val original = intVal(42)
        val patch    = DynamicPatch(
          DynamicOptic(Chunk(DynamicOptic.Node.Wrapped)),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(8))
        )
        val result = patch(original)
        assertTrue(result == Right(intVal(50)))
      },
      test("Wrapped node navigates deeper") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          DynamicOptic(Chunk(DynamicOptic.Node.Wrapped, DynamicOptic.Node.Field("age"))),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result = patch(original)
        assertTrue(result == Right(personRecord("Alice", 35)))
      }
    ),
    suite("Unsupported nodes")(
      test("AtIndices is not supported") {
        val original = DynamicValue.Sequence(Chunk(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          DynamicOptic(Chunk(DynamicOptic.Node.AtIndices(Chunk(0, 2)))),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original)
        assertTrue(result.isLeft)
      },
      test("MapKeys is not supported") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          DynamicOptic(Chunk(DynamicOptic.Node.MapKeys)),
          DynamicPatch.Operation.Set(stringVal("b"))
        )
        val result = patch(original)
        assertTrue(result.isLeft)
      },
      test("MapValues is not supported") {
        val original = DynamicValue.Map(Chunk(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          DynamicOptic(Chunk(DynamicOptic.Node.MapValues)),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original)
        assertTrue(result.isLeft)
      }
    ),
    suite("DynamicPatch additional coverage")(
      test("toString renders positive numeric deltas with +=") {
        val patches = List(
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ByteDelta(1.toByte))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ShortDelta(1.toShort))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(100))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(1000L))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.FloatDelta(1.5f))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(2.5)))
        )

        patches.foldLeft(assertTrue(true)) { case (acc, patch) =>
          acc && assertTrue(patch.toString.contains("+="))
        }
      },
      test("toString renders negative numeric deltas with -=") {
        val patches = List(
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ByteDelta(-1.toByte))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ShortDelta(-1.toShort))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(-100))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(-1000L))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.FloatDelta(-1.5f))),
          DynamicPatch.root(DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(-2.5)))
        )

        patches.foldLeft(assertTrue(true)) { case (acc, patch) =>
          acc && assertTrue(patch.toString.contains("-="))
        }
      },
      test("applies patch to deeply nested record field") {
        val original = DynamicValue.Record(
          Chunk(
            "level1" -> DynamicValue.Record(
              Chunk(
                "level2" -> DynamicValue.Record(
                  Chunk(
                    "value" -> intVal(1)
                  )
                )
              )
            )
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.field("level1").field("level2").field("value"),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original)

        val expected = DynamicValue.Record(
          Chunk(
            "level1" -> DynamicValue.Record(
              Chunk(
                "level2" -> DynamicValue.Record(
                  Chunk(
                    "value" -> intVal(99)
                  )
                )
              )
            )
          )
        )
        assertTrue(result == Right(expected))
      },
      test("applies patch through variant structure") {
        val original = DynamicValue.Variant("Some", intVal(42))
        val patch    = DynamicPatch(
          DynamicOptic.root,
          DynamicPatch.Operation.Set(DynamicValue.Variant("Some", intVal(99)))
        )
        val result = patch(original)

        assertTrue(result == Right(DynamicValue.Variant("Some", intVal(99))))
      },
      test("navigateAndApply handles map values") {
        val original = DynamicValue.Map(
          Chunk(
            stringVal("key1") -> intVal(1),
            stringVal("key2") -> intVal(2)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.atKey("key1"),
          DynamicPatch.Operation.Set(intVal(100))
        )
        val result = patch(original)

        assertTrue(result.isRight)
      }
    ),
    suite("SchemaSearch")(
      test("applies Set to all matching primitive values in a record") {
        // Record with multiple int fields
        val original = DynamicValue.Record(
          Chunk(
            "a" -> intVal(1),
            "b" -> intVal(2),
            "c" -> stringVal("hello")
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.Set(intVal(99))
        )
        val result = patch(original)

        val expected = DynamicValue.Record(
          Chunk(
            "a" -> intVal(99),
            "b" -> intVal(99),
            "c" -> stringVal("hello")
          )
        )
        assertTrue(result == Right(expected))
      },
      test("finds and patches nested matches") {
        // Deeply nested structure
        val original = DynamicValue.Record(
          Chunk(
            "outer" -> DynamicValue.Record(
              Chunk(
                "inner" -> DynamicValue.Record(
                  Chunk(
                    "value" -> intVal(42)
                  )
                )
              )
            ),
            "top" -> intVal(1)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original)

        val expected = DynamicValue.Record(
          Chunk(
            "outer" -> DynamicValue.Record(
              Chunk(
                "inner" -> DynamicValue.Record(
                  Chunk(
                    "value" -> intVal(0)
                  )
                )
              )
            ),
            "top" -> intVal(0)
          )
        )
        assertTrue(result == Right(expected))
      },
      test("finds matches in sequence elements") {
        val original = DynamicValue.Sequence(
          Chunk(
            intVal(1),
            intVal(2),
            stringVal("skip"),
            intVal(3)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(100))
        )
        val result = patch(original)

        val expected = DynamicValue.Sequence(
          Chunk(
            intVal(101),
            intVal(102),
            stringVal("skip"),
            intVal(103)
          )
        )
        assertTrue(result == Right(expected))
      },
      test("finds matches in map values") {
        val original = DynamicValue.Map(
          Chunk(
            stringVal("a") -> intVal(10),
            stringVal("b") -> stringVal("ignore"),
            stringVal("c") -> intVal(20)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original)

        val expected = DynamicValue.Map(
          Chunk(
            stringVal("a") -> intVal(0),
            stringVal("b") -> stringVal("ignore"),
            stringVal("c") -> intVal(0)
          )
        )
        assertTrue(result == Right(expected))
      },
      test("finds matches in variant payload") {
        val original = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(
            Chunk(
              "x" -> intVal(5),
              "y" -> intVal(10)
            )
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val result = patch(original)

        val expected = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(
            Chunk(
              "x" -> intVal(6),
              "y" -> intVal(11)
            )
          )
        )
        assertTrue(result == Right(expected))
      },
      test("matches record pattern (structural)") {
        // Pattern: record with "name" field of type string
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))

        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringVal("Alice"), "age" -> intVal(30))),
            DynamicValue.Record(Chunk("name" -> stringVal("Bob"), "age" -> intVal(25))),
            DynamicValue.Record(Chunk("title" -> stringVal("Book"))) // doesn't match
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern),
          DynamicPatch.Operation.Patch(
            DynamicPatch(
              DynamicOptic.root.field("name"),
              DynamicPatch.Operation.Set(stringVal("REDACTED"))
            )
          )
        )
        val result = patch(original)

        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringVal("REDACTED"), "age" -> intVal(30))),
            DynamicValue.Record(Chunk("name" -> stringVal("REDACTED"), "age" -> intVal(25))),
            DynamicValue.Record(Chunk("title" -> stringVal("Book")))
          )
        )
        assertTrue(result == Right(expected))
      },
      test("matches root if root matches pattern") {
        val original = intVal(42)
        val patch    = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.Set(intVal(100))
        )
        val result = patch(original)

        assertTrue(result == Right(intVal(100)))
      },
      test("returns unchanged value when zero matches (Lenient mode)") {
        val original = DynamicValue.Record(
          Chunk(
            "a" -> stringVal("hello"),
            "b" -> stringVal("world")
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Lenient)

        assertTrue(result == Right(original))
      },
      test("fails on zero matches in Strict mode") {
        val original = DynamicValue.Record(
          Chunk(
            "a" -> stringVal("hello")
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Strict)

        assertTrue(result.isLeft)
      },
      test("SchemaSearch followed by field navigation") {
        // Search for records with "value" field, then navigate to that field
        val pattern = SchemaRepr.Record(Vector("value" -> SchemaRepr.Primitive("int")))

        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("value" -> intVal(10))),
            DynamicValue.Record(Chunk("value" -> intVal(20))),
            DynamicValue.Record(Chunk("other" -> stringVal("skip")))
          )
        )

        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("value"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result = patch(original, PatchMode.Lenient)

        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("value" -> intVal(15))),
            DynamicValue.Record(Chunk("value" -> intVal(25))),
            DynamicValue.Record(Chunk("other" -> stringVal("skip")))
          )
        )
        assertTrue(result == Right(expected))
      },
      test("field navigation followed by SchemaSearch") {
        val original = DynamicValue.Record(
          Chunk(
            "data" -> DynamicValue.Record(
              Chunk(
                "x" -> intVal(1),
                "y" -> intVal(2),
                "z" -> stringVal("hello")
              )
            )
          )
        )

        val patch = DynamicPatch(
          DynamicOptic.root.field("data").searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original)

        val expected = DynamicValue.Record(
          Chunk(
            "data" -> DynamicValue.Record(
              Chunk(
                "x" -> intVal(0),
                "y" -> intVal(0),
                "z" -> stringVal("hello")
              )
            )
          )
        )
        assertTrue(result == Right(expected))
      },
      test("Wildcard pattern matches everything") {
        val original = DynamicValue.Record(
          Chunk(
            "a" -> intVal(1),
            "b" -> stringVal("hello"),
            "c" -> boolVal(true)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original)

        // Wildcard matches everything including root - so root gets replaced
        assertTrue(result == Right(intVal(0)))
      },
      test("PatchMode.Clobber continues on match errors") {
        // Create scenario where some matches will fail to apply the operation
        val original = DynamicValue.Sequence(
          Chunk(
            intVal(10),
            stringVal("not an int"), // Will match Wildcard but IntDelta will fail
            intVal(20)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result = patch(original, PatchMode.Clobber)

        val expected = DynamicValue.Sequence(
          Chunk(
            intVal(15),
            stringVal("not an int"),
            intVal(25)
          )
        )
        assertTrue(result == Right(expected))
      },
      test("multiple SchemaSearch patches compose correctly") {
        val original = DynamicValue.Record(
          Chunk(
            "ints"    -> DynamicValue.Sequence(Chunk(intVal(1), intVal(2))),
            "strings" -> DynamicValue.Sequence(Chunk(stringVal("a"), stringVal("b")))
          )
        )

        val patch1 = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("int")),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
        )
        val patch2 = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Primitive("string")),
          DynamicPatch.Operation.PrimitiveDelta(
            DynamicPatch.PrimitiveOp.StringEdit(Chunk(DynamicPatch.StringOp.Append("!")))
          )
        )

        val combined = patch1 ++ patch2
        val result   = combined(original)

        val expected = DynamicValue.Record(
          Chunk(
            "ints"    -> DynamicValue.Sequence(Chunk(intVal(11), intVal(12))),
            "strings" -> DynamicValue.Sequence(Chunk(stringVal("a!"), stringVal("b!")))
          )
        )
        assertTrue(result == Right(expected))
      },
      test("TypeSearch returns error (requires Schema context)") {
        import zio.blocks.typeid.TypeId
        val original = intVal(42)
        val patch    = DynamicPatch(
          DynamicOptic(Vector(DynamicOptic.Node.TypeSearch(TypeId.of[Int]))),
          DynamicPatch.Operation.Set(intVal(100))
        )
        val result = patch(original)

        assertTrue(result.isLeft)
      },
      // ── 11a: schemaSearchApplyOperation error handling ──
      test("match found but op fails in Strict mode propagates error") {
        // Use Wildcard to match everything, then IntDelta will fail on strings
        val original = DynamicValue.Record(
          Chunk(
            "a" -> intVal(10),
            "b" -> stringVal("hello") // Wildcard matches this, but IntDelta fails on it
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result = patch(original, PatchMode.Strict)
        // Should propagate the error from the failed op on a matched value
        assertTrue(result.isLeft)
      },
      test("match found but op fails in Lenient mode skips error") {
        // Use Wildcard to match everything, IntDelta fails on strings but continues
        val original = DynamicValue.Record(
          Chunk(
            "a" -> intVal(10),
            "b" -> stringVal("hello")
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result = patch(original, PatchMode.Lenient)
        // Lenient mode: skip errors and continue. Root record matches Wildcard so IntDelta fails
        // on it, but continues. Then int fields get modified.
        assertTrue(result.isRight)
      },
      test("match found but op fails in Clobber mode skips error") {
        // Use Wildcard to match everything, IntDelta fails on strings but continues
        val original = DynamicValue.Record(
          Chunk(
            "a" -> intVal(10),
            "b" -> stringVal("hello")
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result.isRight)
      },
      test("early termination on globalError in Strict mode for applyOperation") {
        // Multiple matches where first match fails the operation in Strict mode
        // After the first error, remaining matches should not be processed
        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("x" -> intVal(1))), // matches Wildcard, IntDelta fails
            DynamicValue.Record(Chunk("y" -> intVal(2))) // also matches but shouldn't be processed after error
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Wildcard),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      // ── 11b: schemaSearchNavigate gaps ──
      test("navigate: isCaseMismatch skip in search results") {
        // Record contains variant fields. Search matches all variants,
        // then Case("Left") filters — "Right" variant produces case mismatch, skipped silently
        val original = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Variant("Left", intVal(10)),
            "b" -> DynamicValue.Variant("Right", stringVal("hello")),
            "c" -> DynamicValue.Variant("Left", intVal(20))
          )
        )
        // Variant pattern matches both Left and Right cases
        val variantPattern = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        // Path: SchemaSearch → Case("Left") — nextIsCase is true
        // "Right" variant matches search but Case("Left") fails with case mismatch → silently skipped
        val patch = DynamicPatch(
          DynamicOptic.root
            .searchSchema(variantPattern)
            .caseOf("Left"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(100))
        )
        val result   = patch(original, PatchMode.Strict)
        val expected = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Variant("Left", intVal(110)),
            "b" -> DynamicValue.Variant("Right", stringVal("hello")),
            "c" -> DynamicValue.Variant("Left", intVal(120))
          )
        )
        assertTrue(result == Right(expected))
      },
      test("navigate: match + navigate fails in Strict mode") {
        // Search finds matching records, then try to navigate to a non-existent field
        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringVal("Alice"))),
            DynamicValue.Record(Chunk("name" -> stringVal("Bob")))
          )
        )
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("nonexistent"),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("navigate: match + navigate fails in Lenient mode skips") {
        // Search finds matching records, then try to navigate to a non-existent field
        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringVal("Alice"))),
            DynamicValue.Record(Chunk("name" -> stringVal("Bob")))
          )
        )
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("nonexistent"),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Lenient)
        // Lenient mode: navigate failures are skipped, original returned
        assertTrue(result == Right(original))
      },
      test("navigate: match + navigate fails in Clobber mode skips") {
        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringVal("Alice")))
          )
        )
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("nonexistent"),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(original))
      },
      test("navigate: early termination on globalError in Strict mode") {
        // Multiple matches where navigate fails on first match in Strict mode
        val original = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Record(Chunk("x" -> intVal(1))),
            "b" -> DynamicValue.Record(Chunk("x" -> intVal(2)))
          )
        )
        val pattern = SchemaRepr.Record(Vector("x" -> SchemaRepr.Primitive("int")))
        // Navigate to "missing" field after search — first match will fail
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("missing"),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("navigate: recurses through Variant payload to find matches") {
        // Matches are nested inside a variant's payload
        val original = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(
            Chunk(
              "data" -> DynamicValue.Record(Chunk("value" -> intVal(42)))
            )
          )
        )
        val pattern = SchemaRepr.Record(Vector("value" -> SchemaRepr.Primitive("int")))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("value"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(8))
        )
        val result   = patch(original)
        val expected = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(
            Chunk(
              "data" -> DynamicValue.Record(Chunk("value" -> intVal(50)))
            )
          )
        )
        assertTrue(result == Right(expected))
      },
      test("navigate: recurses through Map values to find matches") {
        // Matches are nested inside map values
        val original = DynamicValue.Map(
          Chunk(
            stringVal("key1") -> DynamicValue.Record(Chunk("value" -> intVal(10))),
            stringVal("key2") -> DynamicValue.Record(Chunk("value" -> intVal(20)))
          )
        )
        val pattern = SchemaRepr.Record(Vector("value" -> SchemaRepr.Primitive("int")))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("value"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        val result   = patch(original)
        val expected = DynamicValue.Map(
          Chunk(
            stringVal("key1") -> DynamicValue.Record(Chunk("value" -> intVal(15))),
            stringVal("key2") -> DynamicValue.Record(Chunk("value" -> intVal(25)))
          )
        )
        assertTrue(result == Right(expected))
      },
      test("navigate: no matches in Strict mode returns error") {
        val original = DynamicValue.Record(
          Chunk("a" -> stringVal("hello"))
        )
        val pattern = SchemaRepr.Primitive("int")
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("x"),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("navigate: no matches in Lenient mode returns unchanged") {
        val original = DynamicValue.Record(
          Chunk("a" -> stringVal("hello"))
        )
        val pattern = SchemaRepr.Primitive("int")
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern).field("x"),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(result == Right(original))
      },
      // ── 11c: Untested SchemaRepr pattern types in patches ──
      test("SchemaRepr.Optional pattern matches Null and inner values") {
        val original = DynamicValue.Sequence(
          Chunk(
            intVal(10),
            DynamicValue.Null,
            intVal(20)
          )
        )
        // Optional(Primitive("int")) should match int values and Null
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Optional(SchemaRepr.Primitive("int"))),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result   = patch(original)
        val expected = DynamicValue.Sequence(
          Chunk(
            intVal(0),
            intVal(0), // Null matched Optional and was replaced
            intVal(0)
          )
        )
        assertTrue(result == Right(expected))
      },
      test("SchemaRepr.Nominal pattern never matches (requires schema context)") {
        val original = DynamicValue.Record(
          Chunk(
            "name" -> stringVal("Alice"),
            "age"  -> intVal(30)
          )
        )
        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Nominal("Person")),
          DynamicPatch.Operation.Set(intVal(0))
        )
        // Nominal never matches in DynamicValue context — returns error in Strict
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("SchemaRepr.Nominal in Lenient mode returns unchanged") {
        val original = intVal(42)
        val patch    = DynamicPatch(
          DynamicOptic.root.searchSchema(SchemaRepr.Nominal("Int")),
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(result == Right(original))
      },
      test("SchemaRepr.Variant pattern matches variant values") {
        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Variant("Left", intVal(10)),
            DynamicValue.Variant("Right", stringVal("hello")),
            DynamicValue.Variant("Left", intVal(20))
          )
        )
        // Match only Left variants with int payload
        val pattern = SchemaRepr.Variant(Vector("Left" -> SchemaRepr.Primitive("int")))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern),
          DynamicPatch.Operation.Set(DynamicValue.Variant("Left", intVal(0)))
        )
        val result   = patch(original)
        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Variant("Left", intVal(0)),
            DynamicValue.Variant("Right", stringVal("hello")),
            DynamicValue.Variant("Left", intVal(0))
          )
        )
        assertTrue(result == Right(expected))
      },
      test("SchemaRepr.Sequence pattern matches sequence values") {
        val original = DynamicValue.Record(
          Chunk(
            "nums" -> DynamicValue.Sequence(Chunk(intVal(1), intVal(2))),
            "name" -> stringVal("test"),
            "tags" -> DynamicValue.Sequence(Chunk(stringVal("a"), stringVal("b")))
          )
        )
        // Match sequences of ints
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("int"))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern),
          DynamicPatch.Operation.Set(DynamicValue.Sequence(Chunk(intVal(99))))
        )
        val result   = patch(original)
        val expected = DynamicValue.Record(
          Chunk(
            "nums" -> DynamicValue.Sequence(Chunk(intVal(99))),
            "name" -> stringVal("test"),
            "tags" -> DynamicValue.Sequence(Chunk(stringVal("a"), stringVal("b")))
          )
        )
        assertTrue(result == Right(expected))
      },
      test("SchemaRepr.Map pattern matches map values") {
        val original = DynamicValue.Record(
          Chunk(
            "lookup" -> DynamicValue.Map(
              Chunk(stringVal("a") -> intVal(1), stringVal("b") -> intVal(2))
            ),
            "name" -> stringVal("test")
          )
        )
        // Match maps with string keys and int values
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val patch   = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern),
          DynamicPatch.Operation.Set(DynamicValue.Map(Chunk.empty))
        )
        val result   = patch(original)
        val expected = DynamicValue.Record(
          Chunk(
            "lookup" -> DynamicValue.Map(Chunk.empty),
            "name"   -> stringVal("test")
          )
        )
        assertTrue(result == Right(expected))
      },
      test("path interpolator with SchemaSearch") {
        val original = DynamicValue.Record(
          Chunk(
            "a" -> intVal(1),
            "b" -> intVal(2)
          )
        )
        val patch = DynamicPatch(
          p"#int",
          DynamicPatch.Operation.Set(intVal(0))
        )
        val result = patch(original)

        val expected = DynamicValue.Record(
          Chunk(
            "a" -> intVal(0),
            "b" -> intVal(0)
          )
        )
        assertTrue(result == Right(expected))
      },
      test("SchemaSearch with nested patch operation") {
        val pattern = SchemaRepr.Record(Vector("count" -> SchemaRepr.Primitive("int")))

        val original = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringVal("A"), "count" -> intVal(5))),
            DynamicValue.Record(Chunk("name" -> stringVal("B"), "count" -> intVal(10)))
          )
        )

        val nestedPatch = DynamicPatch(
          DynamicOptic.root.field("count"),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )

        val patch = DynamicPatch(
          DynamicOptic.root.searchSchema(pattern),
          DynamicPatch.Operation.Patch(nestedPatch)
        )
        val result = patch(original)

        val expected = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringVal("A"), "count" -> intVal(6))),
            DynamicValue.Record(Chunk("name" -> stringVal("B"), "count" -> intVal(11)))
          )
        )
        assertTrue(result == Right(expected))
      }
    )
  )
}
