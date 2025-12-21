package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object DynamicPatchSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicPatchSpec")(
    suite("PatchMode")(
      test("Strict mode fails on precondition violations") {
        val patch = DynamicPatch.at(
          DynamicOptic.root.field("nonexistent"),
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val record = DynamicValue.Record(Vector(("name", DynamicValue.Primitive(PrimitiveValue.String("test")))))
        
        assert(patch(record, PatchMode.Strict))(isLeft)
      },
      test("Lenient mode skips failed operations") {
        val patch = DynamicPatch.at(
          DynamicOptic.root.field("nonexistent"),
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val record = DynamicValue.Record(Vector(("name", DynamicValue.Primitive(PrimitiveValue.String("test")))))
        
        val result = patch(record, PatchMode.Lenient)
        assert(result)(isRight(equalTo(record)))
      },
      test("Clobber mode overwrites on conflicts") {
        val patch = DynamicPatch.single(Operation.MapEdit(Vector(
          MapOp.Add(
            DynamicValue.Primitive(PrimitiveValue.String("key")),
            DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )))
        val existing = DynamicValue.Map(Vector(
          (DynamicValue.Primitive(PrimitiveValue.String("key")), 
           DynamicValue.Primitive(PrimitiveValue.Int(1)))
        ))
        
        val result = patch(existing, PatchMode.Clobber)
        assert(result)(isRight)
      }
    ),
    suite("PrimitiveOp")(
      test("IntDelta adds to integer value") {
        val patch = DynamicPatch.single(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5)))
        val value = DynamicValue.Primitive(PrimitiveValue.Int(10))
        
        assert(patch(value, PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(15))))
        )
      },
      test("LongDelta adds to long value") {
        val patch = DynamicPatch.single(Operation.PrimitiveDelta(PrimitiveOp.LongDelta(100L)))
        val value = DynamicValue.Primitive(PrimitiveValue.Long(50L))
        
        assert(patch(value, PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Long(150L))))
        )
      },
      test("DoubleDelta adds to double value") {
        val patch = DynamicPatch.single(Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(0.5)))
        val value = DynamicValue.Primitive(PrimitiveValue.Double(1.5))
        
        assert(patch(value, PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Double(2.0))))
        )
      },
      test("StringEdit modifies string value") {
        val ops = Vector(StringOp.Insert(5, " World"))
        val patch = DynamicPatch.single(Operation.PrimitiveDelta(PrimitiveOp.StringEdit(ops)))
        val value = DynamicValue.Primitive(PrimitiveValue.String("Hello"))
        
        assert(patch(value, PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("Hello World"))))
        )
      }
    ),
    suite("SeqOp")(
      test("Append adds elements to end") {
        val patch = DynamicPatch.single(Operation.SequenceEdit(Vector(
          SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(3))))
        )))
        val seq = DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        
        val result = patch(seq, PatchMode.Strict)
        assert(result)(isRight(equalTo(DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )))))
      },
      test("Insert adds elements at index") {
        val patch = DynamicPatch.single(Operation.SequenceEdit(Vector(
          SeqOp.Insert(1, Vector(DynamicValue.Primitive(PrimitiveValue.Int(99))))
        )))
        val seq = DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        
        val result = patch(seq, PatchMode.Strict)
        assert(result)(isRight(equalTo(DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(99)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )))))
      },
      test("Delete removes elements") {
        val patch = DynamicPatch.single(Operation.SequenceEdit(Vector(
          SeqOp.Delete(1, 1)
        )))
        val seq = DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        
        val result = patch(seq, PatchMode.Strict)
        assert(result)(isRight(equalTo(DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )))))
      }
    ),
    suite("MapOp")(
      test("Add adds new key-value pair") {
        val key = DynamicValue.Primitive(PrimitiveValue.String("newKey"))
        val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = DynamicPatch.single(Operation.MapEdit(Vector(MapOp.Add(key, value))))
        
        val map = DynamicValue.Map(Vector.empty)
        val result = patch(map, PatchMode.Strict)
        
        assert(result)(isRight(equalTo(DynamicValue.Map(Vector((key, value))))))
      },
      test("Remove removes key") {
        val key = DynamicValue.Primitive(PrimitiveValue.String("toRemove"))
        val patch = DynamicPatch.single(Operation.MapEdit(Vector(MapOp.Remove(key))))
        
        val map = DynamicValue.Map(Vector(
          (key, DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("keep")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        ))
        
        val result = patch(map, PatchMode.Strict)
        assert(result)(isRight(equalTo(DynamicValue.Map(Vector(
          (DynamicValue.Primitive(PrimitiveValue.String("keep")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )))))
      }
    ),
    suite("Monoid laws")(
      test("Identity left: empty ++ p == p") {
        val p = DynamicPatch.single(Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))))
        assert(DynamicPatch.empty ++ p)(equalTo(p))
      },
      test("Identity right: p ++ empty == p") {
        val p = DynamicPatch.single(Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))))
        assert(p ++ DynamicPatch.empty)(equalTo(p))
      },
      test("Associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        val p1 = DynamicPatch.single(Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val p2 = DynamicPatch.single(Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(2))))
        val p3 = DynamicPatch.single(Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(3))))
        
        assert((p1 ++ p2) ++ p3)(equalTo(p1 ++ (p2 ++ p3)))
      }
    )
  )
}
