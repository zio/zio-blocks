/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
 */

package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.json._
import zio.blocks.schema.patch.DynamicPatch
import zio.test.Assertion._
import zio.test._
import zio.blocks.chunk.Chunk

object JsonPatchSpec extends ZIOSpecDefault {

  val genNull: Gen[Any, Json] = Gen.const(Json.Null)
  val genBool: Gen[Any, Json] = Gen.boolean.map(Json.Boolean(_))
  val genNum: Gen[Any, Json]  = Gen.double.map(d => Json.Number(BigDecimal(d)))
  val genStr: Gen[Any, Json]  = Gen.string.map(Json.String(_))

  lazy val genJson: Gen[Any, Json] = Gen.suspend {
    Gen.oneOf(
      genNull,
      genBool,
      genNum,
      genStr,
      Gen.listOfBounded(0, 3)(genJson).map(l => Json.Array(Chunk.from(l))),
      Gen.listOfBounded(0, 3)(Gen.stringBounded(1, 5)(Gen.alphaChar).zip(genJson)).map { fields =>
        Json.Object(Chunk.from(fields))
      }
    )
  }

  def spec = suite("JsonPatchSpec")(
    suite("Unit Tests: Operations")(
      test("T2: Set operation replaces value") {
        val json  = Json.Object(Chunk("a" -> Json.Number(1)))
        val patch = JsonPatch.root(JsonPatch.Op.Set(Json.Number(2)))
        assert(patch(json))(isRight(equalTo(Json.Number(2))))
      },
      test("T3: Array Operations (Append, Delete, Insert)") {
        val json = Json.Array(Json.Number(1), Json.Number(2))
        val p1   = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Vector(Json.Number(3))))))
        val p2   = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 1))))

        assert(p1(json))(isRight(equalTo(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))))) &&
        assert(p2(json))(isRight(equalTo(Json.Array(Json.Number(2)))))
      },
      test("T4: Object Operations (Add, Remove)") {
        val json        = Json.Object("a" -> Json.Number(1))
        val pAdd        = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", Json.Number(2)))))
        val pRem        = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("a"))))
        val expectedAdd = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))

        assert(pAdd(json))(isRight(equalTo(expectedAdd))) &&
        assert(pRem(json))(isRight(equalTo(Json.Object.empty)))
      }
    ),

    suite("Manual Operation Coverage")(
      test("String Operations (Insert, Delete, Append, Modify)") {
        val json = Json.String("hello")
        val ops  = Vector(
          JsonPatch.StringOp.Append(" world"),
          JsonPatch.StringOp.Insert(5, ","),
          JsonPatch.StringOp.Delete(0, 7),     // FIXED: Removes "hello, " (7 chars) leaving "world"
          JsonPatch.StringOp.Modify(0, 1, "W") // Replaces 'w' with 'W' -> "World"
        )
        val patch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(ops)))
        assert(patch(json))(isRight(equalTo(Json.String("World"))))
      },
      test("Array Modify") {
        val json  = Json.Array(Json.Number(1), Json.Number(2))
        val patch =
          JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(1, JsonPatch.Op.Set(Json.Number(3))))))
        assert(patch(json))(isRight(equalTo(Json.Array(Json.Number(1), Json.Number(3)))))
      }
    ),

    suite("Patch Modes")(
      test("Strict fails on invalid path") {
        val json  = Json.Object("a" -> Json.Number(1))
        val patch = JsonPatch(DynamicOptic.root.field("missing"), JsonPatch.Op.Set(Json.Number(2)))
        assert(patch(json, JsonPatchMode.Strict))(isLeft)
      },
      test("Lenient ignores invalid path") {
        val json  = Json.Object("a" -> Json.Number(1))
        val patch = JsonPatch(DynamicOptic.root.field("missing"), JsonPatch.Op.Set(Json.Number(2)))
        assert(patch(json, JsonPatchMode.Lenient))(isRight(equalTo(json)))
      },
      test("Clobber attempts to set on invalid path") {
        val json  = Json.Object("a" -> Json.Number(1))
        val patch = JsonPatch(DynamicOptic.root.field("missing"), JsonPatch.Op.Set(Json.Number(2)))
        assert(patch(json, JsonPatchMode.Clobber))(isRight)
      }
    ),

    suite("Interop Extended")(
      test("fromDynamicPatch coverage for primitive deltas") {
        val primOps = List(
          DynamicPatch.PrimitiveOp.IntDelta(1),
          DynamicPatch.PrimitiveOp.LongDelta(1L),
          DynamicPatch.PrimitiveOp.DoubleDelta(1.0),
          DynamicPatch.PrimitiveOp.FloatDelta(1.0f),
          DynamicPatch.PrimitiveOp.ShortDelta(1.toShort),
          DynamicPatch.PrimitiveOp.ByteDelta(1.toByte),
          DynamicPatch.PrimitiveOp.BigIntDelta(BigInt(1)),
          DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal(1))
        )

        val results = primOps.map { pop =>
          val dp = DynamicPatch(
            Vector(DynamicPatch.DynamicPatchOp(DynamicOptic.root, DynamicPatch.Operation.PrimitiveDelta(pop)))
          )
          JsonPatch.fromDynamicPatch(dp)
        }

        assert(results)(forall(isRight))
      },
      test("MapEdit conversion coverage") {
        val mapOp = DynamicPatch.Operation.MapEdit(
          Vector(
            DynamicPatch.MapOp
              .Add(DynamicValue.Primitive(PrimitiveValue.String("k")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val dp = DynamicPatch(Vector(DynamicPatch.DynamicPatchOp(DynamicOptic.root, mapOp)))
        assert(JsonPatch.fromDynamicPatch(dp))(isRight)
      },
      test("Roundtrip conversion") {
        check(genJson, genJson) { (a, b) =>
          val original = JsonPatch.diff(a, b)
          val dynamic  = original.toDynamicPatch
          val restored = JsonPatch.fromDynamicPatch(dynamic)
          assert(restored)(isRight) && assertTrue(restored.toOption.get.apply(a) == Right(b))
        }
      }
    )
  )
}
