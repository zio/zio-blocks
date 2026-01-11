package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.DynamicValueGen._

/**
 * Property-based tests for DynamicPatch Monoid laws and core operations.
 */
object DynamicPatchSpec extends ZIOSpecDefault {

  // Generator for DynamicOptic
  val genDynamicOptic: Gen[Any, DynamicOptic] = Gen.oneOf(
    Gen.const(DynamicOptic(Vector.empty)),
    Gen.alphaNumericStringBounded(1, 10).map(name => DynamicOptic(Vector(DynamicOptic.Node.Field(name)))),
    Gen.int(0, 10).map(idx => DynamicOptic(Vector(DynamicOptic.Node.AtIndex(idx))))
  )

  // Generator for Operation
  val genOperation: Gen[Any, Operation] = Gen.oneOf(
    genDynamicValue.map(Operation.Set(_)),
    Gen.int(-100, 100).map(d => Operation.PrimitiveDelta(PrimitiveOp.IntDelta(d))),
    Gen.long(-100L, 100L).map(d => Operation.PrimitiveDelta(PrimitiveOp.LongDelta(d)))
  )

  // Generator for DynamicPatchOp
  val genDynamicPatchOp: Gen[Any, DynamicPatchOp] = for {
    optic <- genDynamicOptic
    op    <- genOperation
  } yield DynamicPatchOp(optic, op)

  // Generator for DynamicPatch
  val genDynamicPatch: Gen[Any, DynamicPatch] =
    Gen.listOfBounded(0, 3)(genDynamicPatchOp).map(ops => DynamicPatch(ops.toVector))

  // Stable generators for JSON roundtrip (avoids numeric type normalization issues in DynamicValue codec)
  val genStableDynamicValue: Gen[Any, DynamicValue] = Gen.oneOf(
    Gen.alphaNumericString.map(s => DynamicValue.Primitive(PrimitiveValue.String(s))),
    Gen.boolean.map(b => DynamicValue.Primitive(PrimitiveValue.Boolean(b))),
    Gen.int.map(i => DynamicValue.Primitive(PrimitiveValue.Int(i)))
  )

  val genStableOperation: Gen[Any, Operation] = Gen.oneOf(
    genStableDynamicValue.map(Operation.Set(_)),
    Gen.int(-100, 100).map(d => Operation.PrimitiveDelta(PrimitiveOp.IntDelta(d))),
    Gen.long(-100L, 100L).map(d => Operation.PrimitiveDelta(PrimitiveOp.LongDelta(d))),
    Gen.double(-100.0, 100.0).map(d => Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(d)))
  )

  val genStableDynamicPatchOp: Gen[Any, DynamicPatchOp] = for {
    optic <- genDynamicOptic
    op    <- genStableOperation
  } yield DynamicPatchOp(optic, op)

  val genStableDynamicPatch: Gen[Any, DynamicPatch] =
    Gen.listOfBounded(0, 3)(genStableDynamicPatchOp).map(ops => DynamicPatch(ops.toVector))

  def spec: Spec[TestEnvironment, Any] = suite("DynamicPatchSpec")(
    suite("Monoid Laws")(
      test("Left Identity: empty ++ p == p") {
        check(genDynamicPatch) { patch =>
          val empty = DynamicPatch.empty
          assert(empty ++ patch)(equalTo(patch))
        }
      },
      test("Right Identity: p ++ empty == p") {
        check(genDynamicPatch) { patch =>
          val empty = DynamicPatch.empty
          assert(patch ++ empty)(equalTo(patch))
        }
      },
      test("Associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        check(genDynamicPatch, genDynamicPatch, genDynamicPatch) { (p1, p2, p3) =>
          assert((p1 ++ p2) ++ p3)(equalTo(p1 ++ (p2 ++ p3)))
        }
      },
      test("Semantic Composition: (p1 ++ p2).apply(v) == p1.apply(v).flatMap(p2.apply(_))") {
        check(genStableDynamicValue, genStableDynamicPatch, genStableDynamicPatch) { (v, p1, p2) =>
          val result1 = (p1 ++ p2).apply(v, PatchMode.Lenient) // Use Lenient to ensure it usually succeeds
          val result2 = p1.apply(v, PatchMode.Lenient).flatMap(p2.apply(_, PatchMode.Lenient))
          assert(result1)(equalTo(result2))
        }
      }
    ),
    suite("Roundtrip Law")(
      test("diff(a, b).apply(a) == b for same-type primitives") {
        // Test with Int primitives
        val oldDyn = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val newDyn = DynamicValue.Primitive(PrimitiveValue.Int(20))
        val patch  = DynamicPatch.diff(oldDyn, newDyn)
        val result = patch.apply(oldDyn, PatchMode.Strict)
        assert(result)(isRight(equalTo(newDyn)))
      },
      test("diff(a, b).apply(a) == b for strings") {
        val oldDyn = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val newDyn = DynamicValue.Primitive(PrimitiveValue.String("help"))
        val patch  = DynamicPatch.diff(oldDyn, newDyn)
        val result = patch.apply(oldDyn, PatchMode.Strict)
        assert(result)(isRight(equalTo(newDyn)))
      },
      test("diff(a, b).apply(a) == b for sequences") {
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
        val patch  = DynamicPatch.diff(oldSeq, newSeq)
        val result = patch.apply(oldSeq, PatchMode.Strict)
        assert(result)(isRight(equalTo(newSeq)))
      },
      test("diff(a, b).apply(a) == b for records with same fields") {
        val oldRec = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val newRec = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val patch  = DynamicPatch.diff(oldRec, newRec)
        val result = patch.apply(oldRec, PatchMode.Strict)
        assert(result)(isRight(equalTo(newRec)))
      },
      test("diff(a, b).apply(a) == b for variants with same case") {
        val oldDyn = DynamicValue.Variant("Foo", DynamicValue.Primitive(PrimitiveValue.Int(10)))
        val newDyn = DynamicValue.Variant("Foo", DynamicValue.Primitive(PrimitiveValue.Int(20)))
        val patch  = DynamicPatch.diff(oldDyn, newDyn)
        val result = patch.apply(oldDyn, PatchMode.Strict)
        assert(result)(isRight(equalTo(newDyn)))
      }
    ),
    suite("Operation Application")(
      test("Set operation replaces value") {
        check(genDynamicValue, genDynamicValue) { (original, replacement) =>
          val op     = Operation.Set(replacement)
          val result = op.apply(original, PatchMode.Strict)
          assert(result)(isRight(equalTo(replacement)))
        }
      },
      test("IntDelta adds to Int primitive") {
        check(Gen.int, Gen.int(-1000, 1000)) { (base, delta) =>
          val original = DynamicValue.Primitive(PrimitiveValue.Int(base))
          val op       = Operation.PrimitiveDelta(PrimitiveOp.IntDelta(delta))
          val expected = DynamicValue.Primitive(PrimitiveValue.Int(base + delta))
          val result   = op.apply(original, PatchMode.Strict)
          assert(result)(isRight(equalTo(expected)))
        }
      },
      test("LongDelta adds to Long primitive") {
        check(Gen.long, Gen.long(-1000L, 1000L)) { (base, delta) =>
          val original = DynamicValue.Primitive(PrimitiveValue.Long(base))
          val op       = Operation.PrimitiveDelta(PrimitiveOp.LongDelta(delta))
          val expected = DynamicValue.Primitive(PrimitiveValue.Long(base + delta))
          val result   = op.apply(original, PatchMode.Strict)
          assert(result)(isRight(equalTo(expected)))
        }
      },
      test("DoubleDelta adds to Double primitive") {
        check(Gen.double, Gen.double(-1000.0, 1000.0)) { (base, delta) =>
          val original = DynamicValue.Primitive(PrimitiveValue.Double(base))
          val op       = Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(delta))
          val expected = DynamicValue.Primitive(PrimitiveValue.Double(base + delta))
          val result   = op.apply(original, PatchMode.Strict)
          assert(result)(isRight(equalTo(expected)))
        }
      },
      test("BigIntDelta adds to BigInt primitive") {
        check(Gen.bigInt(BigInt(0), BigInt(1000000)), Gen.bigInt(BigInt(-1000), BigInt(1000))) { (base, delta) =>
          val original = DynamicValue.Primitive(PrimitiveValue.BigInt(base))
          val op       = Operation.PrimitiveDelta(PrimitiveOp.BigIntDelta(delta))
          val expected = DynamicValue.Primitive(PrimitiveValue.BigInt(base + delta))
          val result   = op.apply(original, PatchMode.Strict)
          assert(result)(isRight(equalTo(expected)))
        }
      },
      test("BigDecimalDelta adds to BigDecimal primitive") {
        check(Gen.bigDecimal(BigDecimal(0), BigDecimal(1000000)), Gen.bigDecimal(BigDecimal(-1000), BigDecimal(1000))) {
          (base, delta) =>
            val original = DynamicValue.Primitive(PrimitiveValue.BigDecimal(base))
            val op       = Operation.PrimitiveDelta(PrimitiveOp.BigDecimalDelta(delta))
            val expected = DynamicValue.Primitive(PrimitiveValue.BigDecimal(base + delta))
            val result   = op.apply(original, PatchMode.Strict)
            assert(result)(isRight(equalTo(expected)))
        }
      },
      test("DurationDelta adds to Duration primitive") {
        val base     = java.time.Duration.ofMinutes(30)
        val delta    = java.time.Duration.ofMinutes(60)
        val original = DynamicValue.Primitive(PrimitiveValue.Duration(base))
        val op       = Operation.PrimitiveDelta(PrimitiveOp.DurationDelta(delta))
        val expected = DynamicValue.Primitive(PrimitiveValue.Duration(base.plus(delta)))
        val result   = op.apply(original, PatchMode.Strict)
        assert(result)(isRight(equalTo(expected)))
      },
      test("InstantDelta adds to Instant primitive") {
        val base     = java.time.Instant.parse("2023-01-01T00:00:00Z")
        val delta    = java.time.Duration.ofDays(1)
        val original = DynamicValue.Primitive(PrimitiveValue.Instant(base))
        val op       = Operation.PrimitiveDelta(PrimitiveOp.InstantDelta(delta))
        val expected = DynamicValue.Primitive(PrimitiveValue.Instant(base.plus(delta)))
        val result   = op.apply(original, PatchMode.Strict)
        assert(result)(isRight(equalTo(expected)))
      }
    ),
    suite("PatchMode Behavior")(
      test("Strict mode fails on missing field") {
        val record = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val optic  = DynamicOptic(Vector(DynamicOptic.Node.Field("missing")))
        val patch  = DynamicPatch(
          Vector(DynamicPatchOp(optic, Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("new")))))
        )
        val result = patch.apply(record, PatchMode.Strict)
        assert(result)(isLeft)
      },
      test("Lenient mode skips missing field") {
        val record = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val optic  = DynamicOptic(Vector(DynamicOptic.Node.Field("missing")))
        val patch  = DynamicPatch(
          Vector(DynamicPatchOp(optic, Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("new")))))
        )
        val result = patch.apply(record, PatchMode.Lenient)
        assert(result)(isRight(equalTo(record)))
      }
    ),
    suite("Sequence Operations")(
      test("SeqOp.Insert adds elements at index") {
        val seq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val newElem  = DynamicValue.Primitive(PrimitiveValue.Int(2))
        val op       = Operation.SequenceEdit(Vector(SeqOp.Insert(1, Vector(newElem))))
        val result   = op.apply(seq, PatchMode.Strict)
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assert(result)(isRight(equalTo(expected)))
      },
      test("SeqOp.Delete removes elements at index") {
        val seq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val op       = Operation.SequenceEdit(Vector(SeqOp.Delete(1, 1)))
        val result   = op.apply(seq, PatchMode.Strict)
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assert(result)(isRight(equalTo(expected)))
      },
      test("SeqOp.Append adds elements at end") {
        val seq = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val newElems = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val op       = Operation.SequenceEdit(Vector(SeqOp.Append(newElems)))
        val result   = op.apply(seq, PatchMode.Strict)
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        assert(result)(isRight(equalTo(expected)))
      }
    ),
    suite("Map Operations")(
      test("MapOp.Add adds new key-value pair") {
        val map = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val key      = DynamicValue.Primitive(PrimitiveValue.String("b"))
        val value    = DynamicValue.Primitive(PrimitiveValue.Int(2))
        val op       = Operation.MapEdit(Vector(MapOp.Add(key, value)))
        val result   = op.apply(map, PatchMode.Strict)
        val expected = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        assert(result)(isRight(equalTo(expected)))
      },
      test("MapOp.Remove deletes existing key") {
        val map = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val key      = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val op       = Operation.MapEdit(Vector(MapOp.Remove(key)))
        val result   = op.apply(map, PatchMode.Strict)
        val expected = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        assert(result)(isRight(equalTo(expected)))
      }
    ),
    suite("String Edit Operations")(
      test("StringOp.Insert adds text at index") {
        val original = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val op       = Operation.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, " world"))))
        val result   = op.apply(original, PatchMode.Strict)
        assert(result)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("hello world")))))
      },
      test("StringOp.Delete removes text at index") {
        val original = DynamicValue.Primitive(PrimitiveValue.String("hello world"))
        val op       = Operation.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(5, 6))))
        val result   = op.apply(original, PatchMode.Strict)
        assert(result)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("hello")))))
      }
    ),
    suite("JSON Serialization")(
      test("DynamicPatch roundtrip") {
        import zio.blocks.schema.json.JsonBinaryCodecDeriver
        implicit val codec: zio.blocks.schema.json.JsonBinaryCodec[DynamicPatch] =
          DynamicPatch.schema.derive(JsonBinaryCodecDeriver)

        check(genStableDynamicPatch) { patch =>
          val json    = codec.encodeToString(patch)
          val decoded = codec.decode(json)
          assert(decoded)(isRight(equalTo(patch)))
        }
      }
    )
  )
}
