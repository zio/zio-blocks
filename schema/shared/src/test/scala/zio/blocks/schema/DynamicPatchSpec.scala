package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object DynamicPatchSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicPatchSpec")(
    suite("DynamicPatch operations")(
      test("empty patch returns original value") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = DynamicPatch.empty
        assert(patch(value, PatchMode.Strict))(isRight(equalTo(value)))
      },
      test("Set operation replaces value") {
        val oldValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val newValue = DynamicValue.Primitive(PrimitiveValue.Int(100))
        val patch    = DynamicPatch.single(DynamicOptic.root, Operation.Set(newValue))
        assert(patch(oldValue, PatchMode.Strict))(isRight(equalTo(newValue)))
      },
      test("IntDelta operation increments value") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)))
        assert(patch(value, PatchMode.Strict))(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(52)))))
      },
      test("LongDelta operation increments Long value") {
        val value = DynamicValue.Primitive(PrimitiveValue.Long(100L))
        val patch = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(50L)))
        assert(patch(value, PatchMode.Strict))(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Long(150L)))))
      },
      test("DoubleDelta operation increments Double value") {
        val value  = DynamicValue.Primitive(PrimitiveValue.Double(3.14))
        val patch  = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(1.0)))
        val result = patch(value, PatchMode.Strict)
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get match {
          case DynamicValue.Primitive(PrimitiveValue.Double(d)) => Math.abs(d - 4.14) < 0.001
          case _                                                => false
        })
      },
      test("StringEdit with Insert operation") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("Hello"))
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, " World"))))
        )
        assert(patch(value, PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("Hello World"))))
        )
      },
      test("StringEdit with Delete operation") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("Hello World"))
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(5, 6))))
        )
        assert(patch(value, PatchMode.Strict))(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("Hello")))))
      }
    ),
    suite("Record operations")(
      test("modify field in record") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root.field("age"),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))
        )
        val expected = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(31))
          )
        )
        assert(patch(record, PatchMode.Strict))(isRight(equalTo(expected)))
      },
      test("strict mode fails on missing field") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root.field("missing"),
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assert(patch(record, PatchMode.Strict))(isLeft)
      },
      test("lenient mode skips missing field") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root.field("missing"),
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        assert(patch(record, PatchMode.Lenient))(isRight(equalTo(record)))
      }
    ),
    suite("Sequence operations")(
      test("append to sequence") {
        val seq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.SequenceEdit(
            Vector(
              SeqOp.Append(
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.Int(3)),
                  DynamicValue.Primitive(PrimitiveValue.Int(4))
                )
              )
            )
          )
        )
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3)),
            DynamicValue.Primitive(PrimitiveValue.Int(4))
          )
        )
        assert(patch(seq, PatchMode.Strict))(isRight(equalTo(expected)))
      },
      test("insert at index in sequence") {
        val seq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.SequenceEdit(
            Vector(
              SeqOp.Insert(
                1,
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.Int(2))
                )
              )
            )
          )
        )
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assert(patch(seq, PatchMode.Strict))(isRight(equalTo(expected)))
      },
      test("delete from sequence") {
        val seq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.SequenceEdit(Vector(SeqOp.Delete(1, 1)))
        )
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assert(patch(seq, PatchMode.Strict))(isRight(equalTo(expected)))
      }
    ),
    suite("Map operations")(
      test("add key to map") {
        val map = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.MapEdit(
            Vector(
              MapOp.Add(
                DynamicValue.Primitive(PrimitiveValue.String("b")),
                DynamicValue.Primitive(PrimitiveValue.Int(2))
              )
            )
          )
        )
        val expected = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        assert(patch(map, PatchMode.Strict))(isRight(equalTo(expected)))
      },
      test("remove key from map") {
        val map = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.MapEdit(
            Vector(
              MapOp.Remove(
                DynamicValue.Primitive(PrimitiveValue.String("a"))
              )
            )
          )
        )
        val expected = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        assert(patch(map, PatchMode.Strict))(isRight(equalTo(expected)))
      },
      test("clobber mode overwrites existing key") {
        val map = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.MapEdit(
            Vector(
              MapOp.Add(
                DynamicValue.Primitive(PrimitiveValue.String("a")),
                DynamicValue.Primitive(PrimitiveValue.Int(100))
              )
            )
          )
        )
        val expected = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(100)))
          )
        )
        assert(patch(map, PatchMode.Clobber))(isRight(equalTo(expected)))
      },
      test("strict mode fails on duplicate key") {
        val map = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.MapEdit(
            Vector(
              MapOp.Add(
                DynamicValue.Primitive(PrimitiveValue.String("a")),
                DynamicValue.Primitive(PrimitiveValue.Int(100))
              )
            )
          )
        )
        assert(patch(map, PatchMode.Strict))(isLeft)
      }
    ),
    suite("Patch composition")(
      test("monoid identity - empty ++ patch == patch") {
        val value    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch    = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)))
        val composed = DynamicPatch.empty ++ patch
        assert(composed(value, PatchMode.Strict))(equalTo(patch(value, PatchMode.Strict)))
      },
      test("monoid identity - patch ++ empty == patch") {
        val value    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch    = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)))
        val composed = patch ++ DynamicPatch.empty
        assert(composed(value, PatchMode.Strict))(equalTo(patch(value, PatchMode.Strict)))
      },
      test("sequential composition applies patches in order") {
        val value    = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val patch1   = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)))
        val patch2   = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5)))
        val composed = patch1 ++ patch2
        assert(composed(value, PatchMode.Strict))(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(15)))))
      }
    ),
    suite("LCS string diffing")(
      test("diff identical strings returns empty ops") {
        val ops = LCS.diffStrings("hello", "hello")
        assertTrue(ops.isEmpty)
      },
      test("diff empty to non-empty is single insert") {
        val ops = LCS.diffStrings("", "hello")
        assertTrue(ops.length == 1) &&
        assertTrue(ops.head match {
          case StringOp.Insert(0, "hello") => true
          case _                           => false
        })
      },
      test("diff non-empty to empty is single delete") {
        val ops = LCS.diffStrings("hello", "")
        assertTrue(ops.length == 1) &&
        assertTrue(ops.head match {
          case StringOp.Delete(0, 5) => true
          case _                     => false
        })
      }
    ),
    suite("LCS sequence diffing")(
      test("diff identical sequences returns empty ops") {
        val old = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val ops = LCS.diffSequences(old, old)
        assertTrue(ops.isEmpty)
      },
      test("diff empty to non-empty is append") {
        val newSeq = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val ops    = LCS.diffSequences(Vector.empty, newSeq)
        assertTrue(ops.length == 1) &&
        assertTrue(ops.head match {
          case SeqOp.Append(elems) => elems == newSeq
          case _                   => false
        })
      }
    ),
    suite("Roundtrip law")(
      test("diff followed by apply equals target value (Int primitive)") {
        val oldDv  = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val newDv  = DynamicValue.Primitive(PrimitiveValue.Int(25))
        val patch  = Schema.computeDiff(oldDv, newDv, DynamicOptic.root)
        val result = patch(oldDv, PatchMode.Strict)
        assert(result)(isRight(equalTo(newDv)))
      },
      test("diff followed by apply equals target value (String primitive)") {
        val oldDv  = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val newDv  = DynamicValue.Primitive(PrimitiveValue.String("world"))
        val patch  = Schema.computeDiff(oldDv, newDv, DynamicOptic.root)
        val result = patch(oldDv, PatchMode.Strict)
        assert(result)(isRight(equalTo(newDv)))
      },
      test("diff followed by apply equals target value (Record)") {
        val oldDv = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val newDv = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(35))
          )
        )
        val patch  = Schema.computeDiff(oldDv, newDv, DynamicOptic.root)
        val result = patch(oldDv, PatchMode.Strict)
        assert(result)(isRight(equalTo(newDv)))
      },
      test("diff followed by apply equals target value (Sequence)") {
        val oldDv = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val newDv = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val patch  = Schema.computeDiff(oldDv, newDv, DynamicOptic.root)
        val result = patch(oldDv, PatchMode.Strict)
        assert(result)(isRight(equalTo(newDv)))
      },
      test("diff of identical values produces empty patch") {
        val dv    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = Schema.computeDiff(dv, dv, DynamicOptic.root)
        assertTrue(patch.ops.isEmpty)
      }
    ),
    suite("Schema#diff integration")(
      test("Schema.diffDynamic produces correct patch for primitives") {
        val oldValue = 10
        val newValue = 25
        val schema   = Schema.int
        val patch    = schema.diffDynamic(oldValue, newValue)
        val result   = schema.applyDynamicPatch(oldValue, patch, PatchMode.Strict)
        assert(result)(isRight(equalTo(newValue)))
      }
    ),
    suite("Monoid associativity")(
      test("(a ++ b) ++ c == a ++ (b ++ c)") {
        val value       = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val a           = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1)))
        val b           = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(2)))
        val c           = DynamicPatch.single(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(3)))
        val leftAssoc   = (a ++ b) ++ c
        val rightAssoc  = a ++ (b ++ c)
        val leftResult  = leftAssoc(value, PatchMode.Strict)
        val rightResult = rightAssoc(value, PatchMode.Strict)
        assert(leftResult)(equalTo(rightResult)) &&
        assert(leftResult)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(6)))))
      }
    ),
    suite("PatchSerialization")(
      test("roundtrip serialization of Set operation") {
        val patch = DynamicPatch.single(
          DynamicOptic.root.field("name"),
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("test")))
        )
        val serialized   = PatchSerialization.toValue(patch)
        val deserialized = PatchSerialization.fromValue(serialized)
        assertTrue(deserialized.isRight) &&
        assertTrue(deserialized.toOption.get.ops.length == 1)
      },
      test("roundtrip serialization of IntDelta operation") {
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(42))
        )
        val serialized   = PatchSerialization.toValue(patch)
        val deserialized = PatchSerialization.fromValue(serialized)
        val result       = deserialized.flatMap(p => p(DynamicValue.Primitive(PrimitiveValue.Int(0)), PatchMode.Strict))
        assert(result)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
      },
      test("roundtrip serialization of SequenceEdit operation") {
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.SequenceEdit(Vector(SeqOp.Delete(0, 1)))
        )
        val serialized   = PatchSerialization.toValue(patch)
        val deserialized = PatchSerialization.fromValue(serialized)
        assertTrue(deserialized.isRight)
      },
      test("roundtrip serialization of StringEdit operation") {
        val patch = DynamicPatch.single(
          DynamicOptic.root,
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(
              Vector(
                StringOp.Insert(0, "Hello "),
                StringOp.Delete(6, 5)
              )
            )
          )
        )
        val serialized   = PatchSerialization.toValue(patch)
        val deserialized = PatchSerialization.fromValue(serialized)
        assertTrue(deserialized.isRight) &&
        assertTrue(deserialized.toOption.get.ops.length == 1)
      },
      test("serialization preserves DynamicOptic path") {
        val optic        = DynamicOptic.root.field("person").field("name")
        val patch        = DynamicPatch.single(optic, Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("John"))))
        val serialized   = PatchSerialization.toValue(patch)
        val deserialized = PatchSerialization.fromValue(serialized)
        assertTrue(deserialized.isRight) && {
          val opticNodes = deserialized.toOption.get.ops.head.optic.nodes
          assertTrue(opticNodes.length == 2)
        }
      }
    )
  )
}
