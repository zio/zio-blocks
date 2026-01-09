package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.blocks.schema.PatchSchemas._
import zio.test._

object DynamicPatchSpec extends ZIOSpecDefault {

  // Helper to create primitive DynamicValues
  def intVal(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  def longVal(n: Long): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.Long(n))
  def doubleVal(n: Double): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Double(n))
  def stringVal(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  def boolVal(b: Boolean): DynamicValue  = DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  // Helper to create a simple record
  def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Vector(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  // Helper to create a nested record
  def personWithAddressRecord(name: String, age: Int, city: String): DynamicValue =
    DynamicValue.Record(
      Vector(
        "name"    -> stringVal(name),
        "age"     -> intVal(age),
        "address" -> DynamicValue.Record(
          Vector(
            "city" -> stringVal(city)
          )
        )
      )
    )

  def spec: Spec[Any, Any] = suite("DynamicPatchSpec")(
    suite("Operation.Set")(
      test("applies Set to Primitive at root") {
        val original = intVal(42)
        val patch    = DynamicPatch(Operation.Set(intVal(100)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(100)))
      },
      test("applies Set to Record field") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          Vector(PatchPath.Field("age")),
          Operation.Set(intVal(31))
        )
        val result = patch(original)
        assertTrue(result == Right(personRecord("Alice", 31)))
      },
      test("applies Set to nested Record field") {
        val original = personWithAddressRecord("Alice", 30, "NYC")
        val patch    = DynamicPatch(
          Vector(PatchPath.Field("address"), PatchPath.Field("city")),
          Operation.Set(stringVal("LA"))
        )
        val result = patch(original)
        assertTrue(result == Right(personWithAddressRecord("Alice", 30, "LA")))
      },
      test("applies Set to sequence element") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          Vector(PatchPath.AtIndex(1)),
          Operation.Set(intVal(99))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(99), intVal(3)))))
      },
      test("applies Set to map value") {
        val original = DynamicValue.Map(
          Vector(
            stringVal("a") -> intVal(1),
            stringVal("b") -> intVal(2)
          )
        )
        val patch = DynamicPatch(
          Vector(PatchPath.AtMapKey(stringVal("b"))),
          Operation.Set(intVal(99))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Vector(
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
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(52)))
      },
      test("applies negative IntDelta (decrement)") {
        val original = intVal(42)
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(-10)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(32)))
      },
      test("applies LongDelta to Long") {
        val original = longVal(1000L)
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.LongDelta(500L)))
        val result   = patch(original)
        assertTrue(result == Right(longVal(1500L)))
      },
      test("applies DoubleDelta to Double") {
        val original = doubleVal(3.0)
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(1.5)))
        val result   = patch(original)
        assertTrue(result == Right(doubleVal(4.5)))
      },
      test("applies StringEdit Insert") {
        val original = stringVal("Hello World")
        val patch    = DynamicPatch(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, " Beautiful")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello Beautiful World")))
      },
      test("applies StringEdit Delete") {
        val original = stringVal("Hello Beautiful World")
        val patch    = DynamicPatch(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(Vector(StringOp.Delete(5, 10)))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello World")))
      },
      test("applies multiple StringEdits in sequence") {
        val original = stringVal("abc")
        val patch    = DynamicPatch(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(
              Vector(
                StringOp.Insert(1, "X"), // "aXbc"
                StringOp.Delete(3, 1)    // "aXb"
              )
            )
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("aXb")))
      },
      test("applies IntDelta to Record field") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          Vector(PatchPath.Field("age")),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))
        )
        val result = patch(original)
        assertTrue(result == Right(personRecord("Alice", 31)))
      },
      test("fails on type mismatch") {
        val original = intVal(42)
        val patch    = DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.LongDelta(10L)))
        val result   = patch(original)
        assertTrue(result.isLeft)
      }
    ),
    suite("SequenceEdit")(
      test("applies Append") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Append(Vector(intVal(3), intVal(4)))))
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3), intVal(4))))
        )
      },
      test("applies Append to empty sequence") {
        val original = DynamicValue.Sequence(Vector.empty)
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Append(Vector(intVal(1)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1)))))
      },
      test("applies Insert at beginning") {
        val original = DynamicValue.Sequence(Vector(intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Insert(0, Vector(intVal(1)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3)))))
      },
      test("applies Insert in middle") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(3)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Insert(1, Vector(intVal(2)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3)))))
      },
      test("applies Insert at end") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Insert(2, Vector(intVal(3)))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3)))))
      },
      test("applies Delete") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3), intVal(4)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Delete(1, 2)))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(4)))))
      },
      test("applies Delete at beginning") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Delete(0, 1)))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(2), intVal(3)))))
      },
      test("applies Modify to sequence element") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(
            Vector(SeqOp.Modify(1, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10))))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(12), intVal(3)))))
      },
      test("applies multiple sequence operations") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(
            Vector(
              SeqOp.Append(Vector(intVal(3))),
              SeqOp.Insert(0, Vector(intVal(0)))
            )
          )
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Sequence(Vector(intVal(0), intVal(1), intVal(2), intVal(3))))
        )
      }
    ),
    suite("MapEdit")(
      test("applies Add to empty map") {
        val original = DynamicValue.Map(Vector.empty)
        val patch    = DynamicPatch(
          Operation.MapEdit(Vector(MapOp.Add(stringVal("key"), intVal(42))))
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Map(Vector(stringVal("key") -> intVal(42))))
        )
      },
      test("applies Add to existing map") {
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(Vector(MapOp.Add(stringVal("b"), intVal(2))))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Vector(
                stringVal("a") -> intVal(1),
                stringVal("b") -> intVal(2)
              )
            )
          )
        )
      },
      test("applies Remove") {
        val original = DynamicValue.Map(
          Vector(
            stringVal("a") -> intVal(1),
            stringVal("b") -> intVal(2)
          )
        )
        val patch = DynamicPatch(
          Operation.MapEdit(Vector(MapOp.Remove(stringVal("a"))))
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Map(Vector(stringVal("b") -> intVal(2)))))
      },
      test("applies Modify to map value") {
        val original = DynamicValue.Map(Vector(stringVal("count") -> intVal(10)))
        val patch    = DynamicPatch(
          Operation.MapEdit(
            Vector(MapOp.Modify(stringVal("count"), Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5))))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Map(Vector(stringVal("count") -> intVal(15)))))
      },
      test("applies multiple map operations") {
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(
            Vector(
              MapOp.Add(stringVal("b"), intVal(2)),
              MapOp.Remove(stringVal("a"))
            )
          )
        )
        val result = patch(original)
        assertTrue(result == Right(DynamicValue.Map(Vector(stringVal("b") -> intVal(2)))))
      }
    ),
    suite("Patch Composition")(
      test("combines operations with ++") {
        val patch1 = DynamicPatch(
          Vector(PatchPath.Field("age")),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))
        )
        val patch2 = DynamicPatch(
          Vector(PatchPath.Field("name")),
          Operation.Set(stringVal("Bob"))
        )
        val combined = patch1 ++ patch2

        val original = personRecord("Alice", 30)
        val result   = combined(original)
        assertTrue(result == Right(personRecord("Bob", 31)))
      },
      test("empty is identity (left)") {
        val patch    = DynamicPatch(Operation.Set(intVal(42)))
        val combined = DynamicPatch.empty ++ patch
        assertTrue(combined.ops == patch.ops)
      },
      test("empty is identity (right)") {
        val patch    = DynamicPatch(Operation.Set(intVal(42)))
        val combined = patch ++ DynamicPatch.empty
        assertTrue(combined.ops == patch.ops)
      },
      test("associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        val p1 = DynamicPatch(Vector(PatchPath.Field("a")), Operation.Set(intVal(1)))
        val p2 = DynamicPatch(Vector(PatchPath.Field("b")), Operation.Set(intVal(2)))
        val p3 = DynamicPatch(Vector(PatchPath.Field("c")), Operation.Set(intVal(3)))

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        assertTrue(left.ops == right.ops)
      }
    ),
    suite("PatchMode.Strict")(
      test("fails on missing field") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          Vector(PatchPath.Field("nonexistent")),
          Operation.Set(intVal(99))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on non-existent map key for Remove") {
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(Vector(MapOp.Remove(stringVal("nonexistent"))))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on duplicate key for Add") {
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(Vector(MapOp.Add(stringVal("a"), intVal(2))))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on out of bounds sequence index") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2)))
        val patch    = DynamicPatch(
          Vector(PatchPath.AtIndex(10)),
          Operation.Set(intVal(99))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on invalid Insert index") {
        val original = DynamicValue.Sequence(Vector(intVal(1)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Insert(10, Vector(intVal(2)))))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on invalid Delete range") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Delete(0, 10)))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("PatchMode.Lenient")(
      test("skips operation on missing field") {
        val original = personRecord("Alice", 30)
        val patch    = DynamicPatch(
          Vector(
            DynamicPatchOp(Vector(PatchPath.Field("nonexistent")), Operation.Set(intVal(99))),
            DynamicPatchOp(Vector(PatchPath.Field("age")), Operation.Set(intVal(31)))
          )
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(result == Right(personRecord("Alice", 31)))
      },
      test("skips Remove on non-existent key") {
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(
            Vector(
              MapOp.Remove(stringVal("nonexistent")),
              MapOp.Add(stringVal("b"), intVal(2))
            )
          )
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Vector(
                stringVal("a") -> intVal(1),
                stringVal("b") -> intVal(2)
              )
            )
          )
        )
      },
      test("skips Add on duplicate key") {
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(
            Vector(
              MapOp.Add(stringVal("a"), intVal(99)),
              MapOp.Add(stringVal("b"), intVal(2))
            )
          )
        )
        val result = patch(original, PatchMode.Lenient)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Vector(
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
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(Vector(MapOp.Add(stringVal("a"), intVal(99))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(DynamicValue.Map(Vector(stringVal("a") -> intVal(99)))))
      },
      test("succeeds on Remove non-existent key (no-op)") {
        val original = DynamicValue.Map(Vector(stringVal("a") -> intVal(1)))
        val patch    = DynamicPatch(
          Operation.MapEdit(Vector(MapOp.Remove(stringVal("nonexistent"))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(original))
      },
      test("clamps Insert index to valid range") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Insert(100, Vector(intVal(3)))))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(
          result == Right(DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3))))
        )
      },
      test("clamps Delete range to valid range") {
        val original = DynamicValue.Sequence(Vector(intVal(1), intVal(2), intVal(3)))
        val patch    = DynamicPatch(
          Operation.SequenceEdit(Vector(SeqOp.Delete(1, 100)))
        )
        val result = patch(original, PatchMode.Clobber)
        assertTrue(result == Right(DynamicValue.Sequence(Vector(intVal(1)))))
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
        val patch = DynamicPatch(Operation.Set(intVal(42)))
        assertTrue(!patch.isEmpty)
      }
    ),
    suite("Nested operations")(
      test("modifies field in sequence element") {
        val original = DynamicValue.Sequence(
          Vector(
            personRecord("Alice", 30),
            personRecord("Bob", 25)
          )
        )
        val patch = DynamicPatch(
          Vector(PatchPath.AtIndex(0), PatchPath.Field("age")),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              Vector(
                personRecord("Alice", 31),
                personRecord("Bob", 25)
              )
            )
          )
        )
      },
      test("modifies field in map value") {
        val original = DynamicValue.Map(
          Vector(
            stringVal("person1") -> personRecord("Alice", 30)
          )
        )
        val patch = DynamicPatch(
          Vector(PatchPath.AtMapKey(stringVal("person1")), PatchPath.Field("name")),
          Operation.Set(stringVal("Alicia"))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Map(
              Vector(
                stringVal("person1") -> personRecord("Alicia", 30)
              )
            )
          )
        )
      },
      test("deeply nested modification") {
        val original = DynamicValue.Record(
          Vector(
            "data" -> DynamicValue.Sequence(
              Vector(
                DynamicValue.Map(
                  Vector(
                    stringVal("value") -> intVal(100)
                  )
                )
              )
            )
          )
        )
        val patch = DynamicPatch(
          Vector(
            PatchPath.Field("data"),
            PatchPath.AtIndex(0),
            PatchPath.AtMapKey(stringVal("value"))
          ),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(50))
        )
        val result = patch(original)
        assertTrue(
          result == Right(
            DynamicValue.Record(
              Vector(
                "data" -> DynamicValue.Sequence(
                  Vector(
                    DynamicValue.Map(
                      Vector(
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
      test("PatchPath.Field roundtrips through JSON") {
        roundTrip(
          PatchPath.Field("name"): PatchPath,
          """{"Field":{"name":"name"}}"""
        )
      },
      test("PatchPath.AtIndex roundtrips through JSON") {
        roundTrip(
          PatchPath.AtIndex(42): PatchPath,
          """{"AtIndex":{"index":42}}"""
        )
      },
      test("PatchPath.AtMapKey roundtrips through JSON") {
        roundTrip(
          PatchPath.AtMapKey(stringVal("myKey")): PatchPath,
          """{"AtMapKey":{"key":"myKey"}}"""
        )
      },
      test("DynamicPatchOp roundtrips through JSON") {
        roundTrip(
          DynamicPatchOp(
            Vector(PatchPath.Field("age")),
            Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5))
          ),
          """{"path":[{"Field":{"name":"age"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}"""
        )
      },
      test("DynamicPatchOp with empty path roundtrips") {
        roundTrip(
          DynamicPatchOp(
            Vector.empty,
            Operation.Set(intVal(42))
          ),
          """{"operation":{"Set":{"value":42}}}"""
        )
      },
      test("DynamicPatch roundtrips through JSON") {
        roundTrip(
          DynamicPatch(
            Vector(
              DynamicPatchOp(Vector(PatchPath.Field("name")), Operation.Set(stringVal("Bob")))
            )
          ),
          """{"ops":[{"path":[{"Field":{"name":"name"}}],"operation":{"Set":{"value":"Bob"}}}]}"""
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
        val patch    = DynamicPatch(Vector.empty, Operation.Set(intVal(100)))
        val result   = patch(original)
        assertTrue(result == Right(intVal(100)))
      },
      test("unicode strings in field names") {
        val original = DynamicValue.Record(Vector("名前" -> stringVal("Alice")))
        val patch    = DynamicPatch(
          Vector(PatchPath.Field("名前")),
          Operation.Set(stringVal("アリス"))
        )
        val result = patch(original)
        assertTrue(
          result == Right(DynamicValue.Record(Vector("名前" -> stringVal("アリス"))))
        )
      },
      test("unicode strings in values") {
        val original = stringVal("Hello")
        val patch    = DynamicPatch(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, " 世界")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello 世界")))
      },
      test("empty string operations") {
        val original = stringVal("")
        val patch    = DynamicPatch(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(Vector(StringOp.Insert(0, "Hello")))
          )
        )
        val result = patch(original)
        assertTrue(result == Right(stringVal("Hello")))
      },
      test("type mismatch: apply to wrong DynamicValue type") {
        val original = intVal(42)
        val patch    = DynamicPatch(
          Vector(PatchPath.Field("name")),
          Operation.Set(stringVal("Alice"))
        )
        val result = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Sequence operation on non-sequence fails") {
        val original = intVal(42)
        val patch    = DynamicPatch(Operation.SequenceEdit(Vector(SeqOp.Append(Vector(intVal(1))))))
        val result   = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Map operation on non-map fails") {
        val original = intVal(42)
        val patch    = DynamicPatch(Operation.MapEdit(Vector(MapOp.Add(stringVal("a"), intVal(1)))))
        val result   = patch(original, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    )
  )
}
