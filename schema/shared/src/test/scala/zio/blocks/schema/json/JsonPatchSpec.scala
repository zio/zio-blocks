package zio.blocks.schema.json

import zio._
import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json._

object JsonPatchSpec extends ZIOSpecDefault {

  // ===========================================================================
  // Generators (Property-Based Testing)
  // ===========================================================================
  
  // Generator for primitive JSON values
  val genNull: Gen[Any, Json] = Gen.const(Json.Null)
  val genBool: Gen[Any, Json] = Gen.boolean.map(Json.Boolean(_))
  val genNum: Gen[Any, Json]  = Gen.double.map(d => Json.Number(BigDecimal(d)))
  val genStr: Gen[Any, Json]  = Gen.string.map(Json.String(_))

  // Recursive generator for complex JSON values (Arrays & Objects)
  lazy val genJson: Gen[Any, Json] = Gen.suspend {
    Gen.oneOf(
      genNull,
      genBool,
      genNum,
      genStr,
      Gen.listOfBounded(0, 3)(genJson).map(l => Json.Array(zio.blocks.chunk.Chunk.from(l))),
      // FIX: Used instance method .zip instead of static Gen.zip for ZIO 2.x
      Gen.listOfBounded(0, 3)(Gen.stringBounded(1, 5)(Gen.alphaChar).zip(genJson)).map { fields =>
        Json.Object(zio.blocks.chunk.Chunk.from(fields))
      }
    )
  }

  // ===========================================================================
  // Test Suite
  // ===========================================================================

  def spec = suite("JsonPatchSpec")(
    
    // -------------------------------------------------------------------------
    // Unit Tests for Operations
    // -------------------------------------------------------------------------
    suite("Unit Tests: Operations")(
      test("T2: Set operation replaces value") {
        val json = Json.Object(zio.blocks.chunk.Chunk("a" -> Json.Number(1)))
        val patch = JsonPatch.root(JsonPatch.Op.Set(Json.Number(2)))
        assert(patch(json))(isRight(equalTo(Json.Number(2))))
      },
      
      test("T3: Array Operations (Append, Delete, Insert)") {
        val json = Json.Array(Json.Number(1), Json.Number(2))
        
        // Append
        val p1 = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Vector(Json.Number(3))))))
        
        // Delete index 0
        val p2 = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 1))))
        
        assert(p1(json))(isRight(equalTo(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))))) &&
        assert(p2(json))(isRight(equalTo(Json.Array(Json.Number(2)))))
      },
      
      test("T4: Object Operations (Add, Remove)") {
        val json = Json.Object("a" -> Json.Number(1))
        
        val pAdd = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", Json.Number(2)))))
        val pRem = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("a"))))
        
        // Note: Map equality is order-independent in Json.Object implementation
        val expectedAdd = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
        
        assert(pAdd(json))(isRight(equalTo(expectedAdd))) &&
        assert(pRem(json))(isRight(equalTo(Json.Object.empty)))
      },

      test("T6: Numeric Deltas") {
        val json = Json.Number(10)
        val patch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
        assert(patch(json))(isRight(equalTo(Json.Number(15))))
      },
      
      test("T9: Edge Cases - Empty Structures") {
        val emptyArr = Json.Array.empty
        val emptyObj = Json.Object.empty
        
        // Diffing empty against empty should be empty
        assert(JsonPatch.diff(emptyArr, emptyArr).isEmpty)(isTrue) &&
        assert(JsonPatch.diff(emptyObj, emptyObj).isEmpty)(isTrue)
      }
    ),

    // -------------------------------------------------------------------------
    // Algebraic Laws (Property-Based Tests)
    // -------------------------------------------------------------------------
    suite("Algebraic Laws")(
      test("L4: Roundtrip (diff(a,b)(a) == b)") {
        check(genJson, genJson) { (a, b) =>
          val patch = JsonPatch.diff(a, b)
          val result = patch(a)
          assertTrue(result == Right(b))
        }
      },
      
      test("L5: Identity Diff (diff(a, a) is empty)") {
        check(genJson) { a =>
          val patch = JsonPatch.diff(a, a)
          assertTrue(patch.isEmpty)
        }
      },
      
      test("L1/L2: Identity Composition (empty ++ p == p)") {
        check(genJson, genJson) { (a, b) =>
          val p = JsonPatch.diff(a, b)
          val leftId = JsonPatch.empty ++ p
          val rightId = p ++ JsonPatch.empty
          
          assertTrue(leftId == p) && assertTrue(rightId == p)
        }
      }
    ),

    // -------------------------------------------------------------------------
    // Patch Modes
    // -------------------------------------------------------------------------
    suite("Patch Modes")(
      test("Strict fails on invalid path") {
        val json = Json.Object("a" -> Json.Number(1))
        val patch = JsonPatch(DynamicOptic.root.field("missing"), JsonPatch.Op.Set(Json.Number(2)))
        
        assert(patch(json, JsonPatchMode.Strict))(isLeft)
      },
      
      test("Lenient ignores invalid path") {
        val json = Json.Object("a" -> Json.Number(1))
        val patch = JsonPatch(DynamicOptic.root.field("missing"), JsonPatch.Op.Set(Json.Number(2)))
        
        assert(patch(json, JsonPatchMode.Lenient))(isRight(equalTo(json)))
      },
      
      test("Clobber/Strict Mixed Behavior") {
         // Create a scenario where one op fails and another succeeds
         val json = Json.Object("a" -> Json.Number(1))
         val patch = JsonPatch(Vector(
           JsonPatch.JsonPatchOp(DynamicOptic.root.field("missing"), JsonPatch.Op.Set(Json.Number(999))), // Fails strict
           JsonPatch.JsonPatchOp(DynamicOptic.root.field("a"), JsonPatch.Op.Set(Json.Number(2)))       // Succeeds
         ))
         
         // Strict should fail entirely
         val strictResult = patch(json, JsonPatchMode.Strict)
         // Lenient should apply valid ops and ignore invalid
         val lenientResult = patch(json, JsonPatchMode.Lenient)
         
         assert(strictResult)(isLeft) &&
         assert(lenientResult)(isRight(equalTo(Json.Object("a" -> Json.Number(2)))))
      }
    ),

    // -------------------------------------------------------------------------
    // Interop (DynamicPatch)
    // -------------------------------------------------------------------------
    suite("Interop")(
      test("Roundtrip conversion (toDynamicPatch / fromDynamicPatch)") {
        check(genJson, genJson) { (a, b) =>
          val originalPatch = JsonPatch.diff(a, b)
          
          // Convert to DynamicPatch
          val dynamicPatch = originalPatch.toDynamicPatch
          
          // Convert back to JsonPatch
          val restoredPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
          
          // Verify functionality is preserved
          assert(restoredPatch)(isRight) &&
          assertTrue(restoredPatch.toOption.get.apply(a) == Right(b))
        }
      }
    )
  )
}
