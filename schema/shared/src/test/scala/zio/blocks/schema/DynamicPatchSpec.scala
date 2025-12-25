package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import DynamicPatch._

object DynamicPatchSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicPatchSpec")(
    suite("DynamicPatch basics")(
      test("empty patch is identity") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicPatch.empty.apply(value, PatchMode.Strict)
        assert(result)(isRight(equalTo(value)))
      },
      test("set operation replaces value at root") {
        val oldValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val newValue = DynamicValue.Primitive(PrimitiveValue.Int(100))
        val patch = DynamicPatch.set(newValue)
        assert(patch.apply(oldValue, DynamicPatch.PatchMode.Strict))(isRight(equalTo(newValue)))
      },
      test("patches compose via ++") {
        val patch1 = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root.field("a"), DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val patch2 = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root.field("b"), DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(2))))
        )
        val combined = patch1 ++ patch2
        assertTrue(combined.ops.length == 2)
      }
    ),
    suite("Primitive operations")(
      test("IntDelta adds to int") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(15))))
        )
      },
      test("LongDelta adds to long") {
        val value = DynamicValue.Primitive(PrimitiveValue.Long(100L))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(-50L)))
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Long(50L))))
        )
      },
      test("DoubleDelta adds to double") {
        val value = DynamicValue.Primitive(PrimitiveValue.Double(3.14))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(0.86)))
        )
        val result = patch.apply(value, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isRight && {
          val DynamicValue.Primitive(PrimitiveValue.Double(d)) = result.toOption.get: @unchecked
          math.abs(d - 4.0) < 0.001
        })
      },
      test("StringEdit applies insert/delete operations") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val ops = Vector(
          DynamicPatch.StringOp.Delete(0, 5),      // Delete "hello"
          DynamicPatch.StringOp.Insert(0, "world") // Insert "world"
        )
        val patch = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.StringEdit(ops)))
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("world"))))
        )
      }
    ),
    suite("Record navigation")(
      test("set field in record") {
        val value = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.field("name"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Bob")))
          )
        )
        val result = patch.apply(value, DynamicPatch.PatchMode.Strict)
        assert(result)(isRight(equalTo(
          DynamicValue.Record(Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          ))
        )))
      },
      test("strict mode fails on missing field") {
        val value = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.field("missing"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val result = patch.apply(value, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("lenient mode ignores missing field") {
        val value = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.field("missing"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val result = patch.apply(value, DynamicPatch.PatchMode.Lenient)
        assert(result)(isRight(equalTo(value)))
      },
      test("clobber mode adds missing field") {
        val value = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.field("age"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(25)))
          )
        )
        val result = patch.apply(value, DynamicPatch.PatchMode.Clobber)
        assertTrue(result.isRight && {
          val DynamicValue.Record(fields) = result.toOption.get: @unchecked
          fields.length == 2 && fields.exists(_._1 == "age")
        })
      }
    ),
    suite("Variant navigation")(
      test("navigate into matching case") {
        val value = DynamicValue.Variant("Some",
          DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.caseOf("Some"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(100)))
          )
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(100)))))
        )
      },
      test("strict mode fails on wrong case") {
        val value = DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.caseOf("Some"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(100)))
          )
        )
        val result = patch.apply(value, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("Sequence operations")(
      test("insert element at index") {
        val value = DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root,
            DynamicPatch.Operation.SequenceEdit(Vector(DynamicPatch.SeqOp.Insert(1, Vector(DynamicValue.Primitive(PrimitiveValue.Int(2))))))
          )
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Sequence(Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          ))))
        )
      },
      test("delete element at index") {
        val value = DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root,
            DynamicPatch.Operation.SequenceEdit(Vector(DynamicPatch.SeqOp.Delete(1, 1)))
          )
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Sequence(Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          ))))
        )
      },
      test("append elements") {
        val value = DynamicValue.Sequence(Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root,
            DynamicPatch.Operation.SequenceEdit(Vector(
              DynamicPatch.SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(2)))),
              DynamicPatch.SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(3))))
            ))
          )
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Sequence(Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          ))))
        )
      }
    ),
    suite("Map operations")(
      test("add entry to map") {
        val value = DynamicValue.Map(Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root,
            DynamicPatch.Operation.MapEdit(Vector(
              DynamicPatch.MapOp.Add(
                DynamicValue.Primitive(PrimitiveValue.String("b")),
                DynamicValue.Primitive(PrimitiveValue.Int(2))
              )
            ))
          )
        )
        val result = patch.apply(value, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isRight && {
          val DynamicValue.Map(entries) = result.toOption.get: @unchecked
          entries.length == 2
        })
      },
      test("remove entry from map") {
        val value = DynamicValue.Map(Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root,
            DynamicPatch.Operation.MapEdit(Vector(
              DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("a")))
            ))
          )
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Strict))(
          isRight(equalTo(DynamicValue.Map(Vector(
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          ))))
        )
      }
    ),
    suite("PatchMode semantics")(
      test("strict mode fails on type mismatch") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
        )
        assertTrue(patch.apply(value, DynamicPatch.PatchMode.Strict).isLeft)
      },
      test("lenient mode ignores type mismatch") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
        )
        assert(patch.apply(value, DynamicPatch.PatchMode.Lenient))(isRight(equalTo(value)))
      },
      test("clobber mode replaces on type mismatch") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val patch = DynamicPatch.single(
          DynamicPatch.Op(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5)))
        )
        // Clobber treats delta as set to target value - this should replace
        val result = patch.apply(value, DynamicPatch.PatchMode.Clobber)
        assertTrue(result.isRight)
      }
    ),
    suite("DynamicPatch.StringOp.diff")(
      test("diff identical strings produces empty ops") {
        val ops = DynamicPatch.StringOp.diff("hello", "hello")
        assertTrue(ops.isEmpty)
      },
      test("diff computes insert operations") {
        val ops = DynamicPatch.StringOp.diff("abc", "aXbc")
        assertTrue(ops.nonEmpty)
      },
      test("diff computes delete operations") {
        val ops = DynamicPatch.StringOp.diff("hello", "hlo")
        assertTrue(ops.nonEmpty)
      }
    ),
    suite("DynamicPatch.SeqOp.diff")(
      test("diff identical sequences produces empty ops") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val ops = DynamicPatch.SeqOp.diff(a, a)
        assertTrue(ops.isEmpty || ops.forall {
          case DynamicPatch.SeqOp.Modify(_, _) => true
          case _ => false
        })
      },
      test("diff computes insert for added elements") {
        val a = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val ops = DynamicPatch.SeqOp.diff(a, b)
        assertTrue(ops.exists {
          case DynamicPatch.SeqOp.Insert(_, _) | DynamicPatch.SeqOp.Append(_) => true
          case _ => false
        })
      },
      test("diff computes delete for removed elements") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val ops = DynamicPatch.SeqOp.diff(a, b)
        assertTrue(ops.exists {
          case DynamicPatch.SeqOp.Delete(_, _) => true
          case _ => false
        })
      }
    ),
    suite("DynamicPatch.MapOp.diff")(
      test("diff identical maps produces empty ops") {
        val entries = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val ops = DynamicPatch.MapOp.diff(entries, entries)
        assertTrue(ops.isEmpty)
      },
      test("diff computes add for new keys") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        assertTrue(ops.exists {
          case DynamicPatch.MapOp.Add(_, _) => true
          case _ => false
        })
      },
      test("diff computes remove for deleted keys") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        assertTrue(ops.exists {
          case DynamicPatch.MapOp.Remove(_) => true
          case _ => false
        })
      }
    ),
    suite("Monoid laws")(
      test("empty ++ patch == patch") {
        val patch = DynamicPatch.set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue((DynamicPatch.empty ++ patch) == patch)
      },
      test("patch ++ empty == patch") {
        val patch = DynamicPatch.set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue((patch ++ DynamicPatch.empty) == patch)
      },
      test("(p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        val p1 = DynamicPatch.single(DynamicPatch.Op(DynamicOptic.root.field("a"), DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        val p2 = DynamicPatch.single(DynamicPatch.Op(DynamicOptic.root.field("b"), DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(2)))))
        val p3 = DynamicPatch.single(DynamicPatch.Op(DynamicOptic.root.field("c"), DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(3)))))
        assertTrue(((p1 ++ p2) ++ p3) == (p1 ++ (p2 ++ p3)))
      }
    )
  )
}
