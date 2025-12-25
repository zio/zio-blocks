package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object DynamicPatchSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicPatchSpec")(
    suite("Operation.Set")(
      test("sets a primitive value") {
        val old    = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Int(20))
        val patch  = DynamicPatch(Operation.Set(newVal))
        assert(patch(old))(isRight(equalTo(newVal)))
      }
    ),
    suite("PrimitiveOp")(
      test("increments an int") {
        val old      = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val expected = DynamicValue.Primitive(PrimitiveValue.Int(15))
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5)))
        assert(patch(old))(isRight(equalTo(expected)))
      },
      test("increments a long") {
        val old      = DynamicValue.Primitive(PrimitiveValue.Long(100L))
        val expected = DynamicValue.Primitive(PrimitiveValue.Long(150L))
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.LongDelta(50L)))
        assert(patch(old))(isRight(equalTo(expected)))
      },
      test("increments a double") {
        val old      = DynamicValue.Primitive(PrimitiveValue.Double(1.5))
        val expected = DynamicValue.Primitive(PrimitiveValue.Double(2.0))
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(0.5)))
        assert(patch(old))(isRight(equalTo(expected)))
      }
    ),
    suite("StringOp")(
      test("inserts text") {
        val old      = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val expected = DynamicValue.Primitive(PrimitiveValue.String("hello world"))
        val patch    = DynamicPatch(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, " world")))
          )
        )
        assert(patch(old))(isRight(equalTo(expected)))
      },
      test("deletes text") {
        val old      = DynamicValue.Primitive(PrimitiveValue.String("hello world"))
        val expected = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val patch    = DynamicPatch(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(Vector(StringOp.Delete(5, 6)))
          )
        )
        assert(patch(old))(isRight(equalTo(expected)))
      }
    ),
    suite("SeqOp")(
      test("appends elements") {
        val old = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val patch = DynamicPatch(
          Operation.SequenceEdit(
            Vector(
              SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(3))))
            )
          )
        )
        assert(patch(old))(isRight(equalTo(expected)))
      },
      test("deletes elements") {
        val old = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val patch = DynamicPatch(
          Operation.SequenceEdit(
            Vector(
              SeqOp.Delete(1, 1)
            )
          )
        )
        assert(patch(old))(isRight(equalTo(expected)))
      }
    ),
    suite("MapOp")(
      test("adds a key") {
        val old = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val expected = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val patch = DynamicPatch(
          Operation.MapEdit(
            Vector(
              MapOp.Add(
                DynamicValue.Primitive(PrimitiveValue.String("b")),
                DynamicValue.Primitive(PrimitiveValue.Int(2))
              )
            )
          )
        )
        assert(patch(old))(isRight(equalTo(expected)))
      },
      test("removes a key") {
        val old = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val expected = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val patch = DynamicPatch(
          Operation.MapEdit(
            Vector(
              MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("b")))
            )
          )
        )
        assert(patch(old))(isRight(equalTo(expected)))
      }
    ),
    suite("RecordPatch")(
      test("modifies a record field") {
        val old = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(31)))
          )
        )
        val patch = DynamicPatch(
          Operation.RecordPatch(
            Vector(
              ("age", Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1)))
            )
          )
        )
        assert(patch(old))(isRight(equalTo(expected)))
      }
    ),
    suite("Patch composition")(
      test("composes two patches") {
        val old      = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val expected = DynamicValue.Primitive(PrimitiveValue.Int(25))
        val patch1   = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5)))
        val patch2   = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)))
        val combined = patch1 ++ patch2
        assert(combined(old))(isRight(equalTo(expected)))
      }
    ),
    suite("LCS")(
      test("computes string diff") {
        val old  = "hello"
        val new1 = "hello world"
        val ops  = LCS.stringDiff(old, new1)
        assert(StringOp.applyAll(old, ops))(isRight(equalTo(new1)))
      },
      test("computes sequence diff") {
        val old = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val new1 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val ops = LCS.sequenceDiff(old, new1)
        assert(SeqOp.applyAll(old, ops, PatchMode.Strict))(isRight(equalTo(new1)))
      }
    ),
    suite("PatchMode")(
      test("Strict mode fails on missing field") {
        val old = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
          )
        )
        val patch = DynamicPatch(
          Operation.RecordPatch(
            Vector(
              ("missing", Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
          )
        )
        assert(patch(old, PatchMode.Strict))(isLeft)
      },
      test("Lenient mode skips missing field") {
        val old = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
          )
        )
        val patch = DynamicPatch(
          Operation.RecordPatch(
            Vector(
              ("missing", Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
          )
        )
        assert(patch(old, PatchMode.Lenient))(isRight(equalTo(old)))
      }
    )
  )
}
