package zio.blocks.schema.json.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.patch.PatchMode
import zio.test._

object JsonPatchOperationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Operations")(
    setOperationSuite,
    primitiveDeltaSuite,
    arrayEditSuite,
    objectEditSuite,
    nestedOperationSuite,
    edgeCasesSuite,
    elementsNavigationSuite,
    wrappedNavigationSuite,
    jsonPatchEmptySuite
  )

  // Op.Set Suite

  private lazy val setOperationSuite = suite("Op.Set")(
    test("Set replaces value entirely") {
      val original = Json.Number(42)
      val patch    = JsonPatch.root(Op.Set(Json.String("replaced")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("replaced")))
    },
    test("Set works on Json.Object") {
      val original = Json.Object("a" -> Json.Number(1))
      val newValue = Json.Object("b" -> Json.Number(2), "c" -> Json.Number(3))
      val patch    = JsonPatch.root(Op.Set(newValue))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(newValue))
    },
    test("Set works on Json.Array") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val newValue = Json.Array(Json.String("a"), Json.String("b"), Json.String("c"))
      val patch    = JsonPatch.root(Op.Set(newValue))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(newValue))
    },
    test("Set works on Json.String") {
      val original = Json.String("hello")
      val newValue = Json.String("world")
      val patch    = JsonPatch.root(Op.Set(newValue))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(newValue))
    },
    test("Set works on Json.Number") {
      val original = Json.Number(123)
      val newValue = Json.Number("456.789")
      val patch    = JsonPatch.root(Op.Set(newValue))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(newValue))
    },
    test("Set works on Json.Boolean") {
      val original = Json.Boolean(true)
      val newValue = Json.Boolean(false)
      val patch    = JsonPatch.root(Op.Set(newValue))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(newValue))
    },
    test("Set works on Json.Null") {
      val original = Json.Null
      val newValue = Json.String("not null anymore")
      val patch    = JsonPatch.root(Op.Set(newValue))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(newValue))
    },
    test("Set can replace any type with any other type") {
      val original = Json.Object("x" -> Json.Number(1))
      val newValue = Json.Array(Json.Boolean(true))
      val patch    = JsonPatch.root(Op.Set(newValue))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(newValue))
    }
  )

  // Op.PrimitiveDelta Suite

  private lazy val primitiveDeltaSuite = suite("Op.PrimitiveDelta")(
    numberDeltaSuite,
    stringEditSuite
  )

  private lazy val numberDeltaSuite = suite("NumberDelta")(
    test("NumberDelta with positive delta (5 + 3 = 8)") {
      val original = Json.Number(5)
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(3))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number("8")))
    },
    test("NumberDelta with negative delta (10 - 3 = 7)") {
      val original = Json.Number(10)
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-3))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number("7")))
    },
    test("NumberDelta with zero delta (value unchanged)") {
      val original = Json.Number(42)
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(0))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number("42")))
    },
    test("NumberDelta with decimal delta (1.5 + 0.5 = 2.0)") {
      val original = Json.Number("1.5")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal("0.5"))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number("2.0")))
    },
    test("NumberDelta with large values") {
      val original = Json.Number("999999999999999999")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number("1000000000000000000")))
    },
    test("NumberDelta with high precision decimals") {
      val original = Json.Number("0.123456789")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal("0.000000001"))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number("0.123456790")))
    }
  )

  private lazy val stringEditSuite = suite("StringEdit")(
    test("StringEdit with Insert at beginning") {
      val original = Json.String("world")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(0, "hello ")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("hello world")))
    },
    test("StringEdit with Insert at middle") {
      val original = Json.String("helloworld")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, " ")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("hello world")))
    },
    test("StringEdit with Insert at end") {
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, " world")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("hello world")))
    },
    test("StringEdit with Delete at beginning") {
      val original = Json.String("hello world")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(0, 6)))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("world")))
    },
    test("StringEdit with Delete at middle") {
      val original = Json.String("hello world")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(5, 1)))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("helloworld")))
    },
    test("StringEdit with Delete at end") {
      val original = Json.String("hello world")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(5, 6)))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("hello")))
    },
    test("StringEdit with Append") {
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Append(" world")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("hello world")))
    },
    test("StringEdit with Modify (replace substring)") {
      val original = Json.String("hello world")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Modify(6, 5, "universe")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("hello universe")))
    },
    test("StringEdit with multiple operations in sequence") {
      val original = Json.String("abc")
      val patch    = JsonPatch.root(
        Op.PrimitiveDelta(
          PrimitiveOp.StringEdit(
            Vector(
              StringOp.Delete(1, 1),    // "abc" -> "ac"
              StringOp.Insert(1, "xyz") // "ac" -> "axyzc"
            )
          )
        )
      )
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("axyzc")))
    },
    test("StringEdit combining Insert and Append") {
      val original = Json.String("middle")
      val patch    = JsonPatch.root(
        Op.PrimitiveDelta(
          PrimitiveOp.StringEdit(
            Vector(
              StringOp.Insert(0, "start "),
              StringOp.Append(" end")
            )
          )
        )
      )
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("start middle end")))
    }
  )

  // Op.ArrayEdit Suite

  private lazy val arrayEditSuite = suite("Op.ArrayEdit")(
    arrayInsertSuite,
    arrayAppendSuite,
    arrayDeleteSuite,
    arrayModifySuite
  )

  private lazy val arrayInsertSuite = suite("ArrayOp.Insert")(
    test("Insert at beginning") {
      val original = Json.Array(Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(0, Chunk(Json.Number(1))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))))
    },
    test("Insert at middle") {
      val original = Json.Array(Json.Number(1), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(1, Chunk(Json.Number(2))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))))
    },
    test("Insert at end") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(2, Chunk(Json.Number(3))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))))
    },
    test("Insert multiple values") {
      val original = Json.Array(Json.Number(1), Json.Number(4))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(1, Chunk(Json.Number(2), Json.Number(3))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4))))
    }
  )

  private lazy val arrayAppendSuite = suite("ArrayOp.Append")(
    test("Append single value") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(3))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))))
    },
    test("Append multiple values") {
      val original = Json.Array(Json.Number(1))
      val patch    =
        JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(2), Json.Number(3), Json.Number(4))))))
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4))))
    },
    test("Append to empty array") {
      val original = Json.Array.empty
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.String("first"))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.String("first"))))
    }
  )

  private lazy val arrayDeleteSuite = suite("ArrayOp.Delete")(
    test("Delete single element at beginning") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(0, 1))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(2), Json.Number(3))))
    },
    test("Delete single element at middle") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(1, 1))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(3))))
    },
    test("Delete single element at end") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(2, 1))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2))))
    },
    test("Delete range of elements") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4), Json.Number(5))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(1, 3))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(5))))
    },
    test("Delete all elements") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(0, 3))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array.empty))
    }
  )

  private lazy val arrayModifySuite = suite("ArrayOp.Modify")(
    test("Modify element at index 0") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Modify(0, Op.Set(Json.Number(10))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(10), Json.Number(2), Json.Number(3))))
    },
    test("Modify element in middle") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Modify(1, Op.Set(Json.Number(20))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(20), Json.Number(3))))
    },
    test("Modify element at last index") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Modify(2, Op.Set(Json.Number(30))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1), Json.Number(2), Json.Number(30))))
    },
    test("Modify with nested number delta") {
      val original = Json.Array(Json.Number(10), Json.Number(20), Json.Number(30))
      val patch    = JsonPatch.root(
        Op.ArrayEdit(Vector(ArrayOp.Modify(1, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))))
      )
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(10), Json.Number("25"), Json.Number(30))))
    }
  )

  // Op.ObjectEdit Suite

  private lazy val objectEditSuite = suite("Op.ObjectEdit")(
    objectAddSuite,
    objectRemoveSuite,
    objectModifySuite
  )

  private lazy val objectAddSuite = suite("ObjectOp.Add")(
    test("Add new field to empty object") {
      val original = Json.Object.empty
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("name", Json.String("Alice")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("name" -> Json.String("Alice"))))
    },
    test("Add new field to existing object") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("age", Json.Number(30)))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))))
    },
    test("Add multiple fields") {
      val original = Json.Object.empty
      val patch    = JsonPatch.root(
        Op.ObjectEdit(
          Vector(
            ObjectOp.Add("a", Json.Number(1)),
            ObjectOp.Add("b", Json.Number(2)),
            ObjectOp.Add("c", Json.Number(3))
          )
        )
      )
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2), "c" -> Json.Number(3))))
    }
  )

  private lazy val objectRemoveSuite = suite("ObjectOp.Remove")(
    test("Remove existing field") {
      val original = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Remove("age"))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("name" -> Json.String("Alice"))))
    },
    test("Remove all fields") {
      val original = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Remove("a"), ObjectOp.Remove("b"))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object.empty))
    },
    test("Remove first field") {
      val original = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2), "c" -> Json.Number(3))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Remove("a"))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("b" -> Json.Number(2), "c" -> Json.Number(3))))
    }
  )

  private lazy val objectModifySuite = suite("ObjectOp.Modify")(
    test("Modify existing field value") {
      val original = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
      val patch    =
        JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("name", JsonPatch.root(Op.Set(Json.String("Bob")))))))
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("name" -> Json.String("Bob"), "age" -> Json.Number(30))))
    },
    test("Modify with nested number delta") {
      val original = Json.Object("count" -> Json.Number(10))
      val patch    = JsonPatch.root(
        Op.ObjectEdit(
          Vector(ObjectOp.Modify("count", JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))))
        )
      )
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("count" -> Json.Number("15"))))
    },
    test("Modify nested object") {
      val original = Json.Object(
        "user" -> Json.Object(
          "name" -> Json.String("Alice"),
          "age"  -> Json.Number(30)
        )
      )
      val nestedPatch =
        JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("age", JsonPatch.root(Op.Set(Json.Number(31)))))))
      val patch  = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("user", nestedPatch))))
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(
        result == Right(
          Json.Object(
            "user" -> Json.Object(
              "name" -> Json.String("Alice"),
              "age"  -> Json.Number(31)
            )
          )
        )
      )
    }
  )

  // Op.Nested Suite

  private lazy val nestedOperationSuite = suite("Op.Nested")(
    test("Nested patch with multiple operations") {
      val original    = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
      val nestedPatch = JsonPatch(
        Vector(
          JsonPatchOp(DynamicOptic.root, Op.ObjectEdit(Vector(ObjectOp.Add("c", Json.Number(3))))),
          JsonPatchOp(DynamicOptic.root, Op.ObjectEdit(Vector(ObjectOp.Remove("a"))))
        )
      )
      val patch  = JsonPatch.root(Op.Nested(nestedPatch))
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("b" -> Json.Number(2), "c" -> Json.Number(3))))
    },
    test("Nested patch applied to substructure") {
      val original = Json.Object(
        "data" -> Json.Object(
          "values" -> Json.Array(Json.Number(1), Json.Number(2))
        )
      )
      val innerPatch = JsonPatch(
        Vector(
          JsonPatchOp(DynamicOptic.root, Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(3))))))
        )
      )
      val nestedModify = Op.ObjectEdit(Vector(ObjectOp.Modify("values", innerPatch)))
      val outerModify  = Op.ObjectEdit(Vector(ObjectOp.Modify("data", JsonPatch.root(nestedModify))))
      val patch        = JsonPatch.root(outerModify)
      val result       = patch.apply(original, PatchMode.Strict)
      assertTrue(
        result == Right(
          Json.Object(
            "data" -> Json.Object(
              "values" -> Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
            )
          )
        )
      )
    },
    test("Deeply nested patch operations") {
      val original = Json.Object(
        "level1" -> Json.Object(
          "level2" -> Json.Object(
            "level3" -> Json.Number(100)
          )
        )
      )
      val level3Patch = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(50))))
      val level2Patch = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("level3", level3Patch))))
      val level1Patch = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("level2", level2Patch))))
      val rootPatch   = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("level1", level1Patch))))

      val result = rootPatch.apply(original, PatchMode.Strict)
      assertTrue(
        result == Right(
          Json.Object(
            "level1" -> Json.Object(
              "level2" -> Json.Object(
                "level3" -> Json.Number("150")
              )
            )
          )
        )
      )
    },
    test("Full path and nested path produce identical results") {
      val original = Json.Object(
        "a" -> Json.Object(
          "b" -> Json.Object(
            "c" -> Json.Number(1)
          )
        )
      )

      // Approach 1: Full path - navigate directly to target
      val fullPathPatch = JsonPatch(
        Vector(
          JsonPatchOp(
            path = DynamicOptic.root.field("a").field("b").field("c"),
            operation = Op.Set(Json.Number(42))
          )
        )
      )

      // Approach 2: Nested path - use Op.Nested to compose patches
      val nestedPathPatch = JsonPatch(
        Vector(
          JsonPatchOp(
            path = DynamicOptic.root.field("a"),
            operation = Op.Nested(
              JsonPatch(
                Vector(
                  JsonPatchOp(
                    path = DynamicOptic.root.field("b"),
                    operation = Op.Nested(
                      JsonPatch(
                        Vector(
                          JsonPatchOp(
                            path = DynamicOptic.root.field("c"),
                            operation = Op.Set(Json.Number(42))
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )

      val resultFullPath   = fullPathPatch.apply(original, PatchMode.Strict)
      val resultNestedPath = nestedPathPatch.apply(original, PatchMode.Strict)

      assertTrue(
        resultFullPath == resultNestedPath &&
          resultFullPath == Right(
            Json.Object(
              "a" -> Json.Object(
                "b" -> Json.Object(
                  "c" -> Json.Number(42)
                )
              )
            )
          )
      )
    },
    test("Full path and nested path equivalence with array index navigation") {
      val original = Json.Array(
        Json.Object("name" -> Json.String("Alice")),
        Json.Object("name" -> Json.String("Bob"))
      )

      // Approach 1: Full path to array[1].name
      val fullPathPatch = JsonPatch(
        Vector(
          JsonPatchOp(
            path = DynamicOptic.root.at(1).field("name"),
            operation = Op.Set(Json.String("Charlie"))
          )
        )
      )

      // Approach 2: Nested - first navigate to array[1], then apply nested patch for name
      val nestedPathPatch = JsonPatch(
        Vector(
          JsonPatchOp(
            path = DynamicOptic.root.at(1),
            operation = Op.Nested(
              JsonPatch(
                Vector(
                  JsonPatchOp(
                    path = DynamicOptic.root.field("name"),
                    operation = Op.Set(Json.String("Charlie"))
                  )
                )
              )
            )
          )
        )
      )

      val resultFullPath   = fullPathPatch.apply(original, PatchMode.Strict)
      val resultNestedPath = nestedPathPatch.apply(original, PatchMode.Strict)

      assertTrue(
        resultFullPath == resultNestedPath &&
          resultFullPath == Right(
            Json.Array(
              Json.Object("name" -> Json.String("Alice")),
              Json.Object("name" -> Json.String("Charlie"))
            )
          )
      )
    }
  )

  // Edge Cases Suite

  private lazy val edgeCasesSuite = suite("Edge Cases")(
    emptyValuesSuite,
    deeplyNestedSuite,
    largeCollectionsSuite
  )

  private lazy val emptyValuesSuite = suite("Empty values")(
    test("Apply patch to empty object") {
      val original = Json.Object.empty
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("key", Json.String("value")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("key" -> Json.String("value"))))
    },
    test("Apply patch to empty array") {
      val original = Json.Array.empty
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(1))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(1))))
    },
    test("Apply patch to empty string") {
      val original = Json.String("")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(0, "hello")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("hello")))
    },
    test("Append to empty string") {
      val original = Json.String("")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Append("world")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.String("world")))
    },
    test("Empty patch does nothing") {
      val original = Json.Object("a" -> Json.Number(1))
      val patch    = JsonPatch.empty
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(original))
    }
  )

  private lazy val deeplyNestedSuite = suite("Deeply nested structures (3+ levels)")(
    test("Modify value at depth 4") {
      val original = Json.Object(
        "a" -> Json.Object(
          "b" -> Json.Object(
            "c" -> Json.Object(
              "d" -> Json.Number(1)
            )
          )
        )
      )
      val dPatch = JsonPatch.root(Op.Set(Json.Number(2)))
      val cPatch = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("d", dPatch))))
      val bPatch = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("c", cPatch))))
      val aPatch = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("b", bPatch))))
      val patch  = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("a", aPatch))))

      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(
        result == Right(
          Json.Object(
            "a" -> Json.Object(
              "b" -> Json.Object(
                "c" -> Json.Object(
                  "d" -> Json.Number(2)
                )
              )
            )
          )
        )
      )
    },
    test("Array inside object inside array") {
      val original = Json.Array(
        Json.Object(
          "items" -> Json.Array(Json.Number(1), Json.Number(2))
        )
      )
      val innerAppend = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(3))))))
      val modifyItems = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Modify("items", innerAppend))))
      val patch       = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Modify(0, Op.Nested(modifyItems)))))

      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(
        result == Right(
          Json.Array(
            Json.Object(
              "items" -> Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
            )
          )
        )
      )
    }
  )

  private lazy val largeCollectionsSuite = suite("Large collections (10+ elements)")(
    test("Modify element in large array") {
      val elements = (0 until 15).map(i => Json.Number(i))
      val original = Json.Array(Chunk.from(elements))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Modify(10, Op.Set(Json.Number(100))))))
      val result   = patch.apply(original, PatchMode.Strict)

      val expected = elements.updated(10, Json.Number(100))
      assertTrue(result == Right(Json.Array(Chunk.from(expected))))
    },
    test("Delete multiple elements from large array") {
      val elements = (0 until 15).map(i => Json.Number(i))
      val original = Json.Array(Chunk.from(elements))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(5, 5))))
      val result   = patch.apply(original, PatchMode.Strict)

      val expected = elements.take(5) ++ elements.drop(10)
      assertTrue(result == Right(Json.Array(Chunk.from(expected))))
    },
    test("Object with 15 fields") {
      val fields   = (0 until 15).map(i => s"field$i" -> Json.Number(i))
      val original = Json.Object(Chunk.from(fields))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("field15", Json.Number(15)))))
      val result   = patch.apply(original, PatchMode.Strict)

      val expected = fields :+ ("field15" -> Json.Number(15))
      assertTrue(result == Right(Json.Object(Chunk.from(expected))))
    },
    test("Insert multiple elements in middle of large array") {
      val elements = (0 until 12).map(i => Json.Number(i))
      val original = Json.Array(Chunk.from(elements))
      val toInsert = Chunk(Json.Number(100), Json.Number(101), Json.Number(102))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(6, toInsert))))
      val result   = patch.apply(original, PatchMode.Strict)

      val expected = elements.take(6) ++ Seq(Json.Number(100), Json.Number(101), Json.Number(102)) ++ elements.drop(6)
      assertTrue(result == Right(Json.Array(Chunk.from(expected))))
    }
  )

  // Elements Navigation Suite

  private lazy val elementsNavigationSuite = suite("Elements Navigation")(
    test("Elements navigation applies operation to all array elements") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(10))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number("11"), Json.Number("12"), Json.Number("13"))))
    },
    test("Elements navigation on empty array succeeds in Lenient mode") {
      val original = Json.Array.empty
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Lenient)
      assertTrue(result == Right(Json.Array.empty))
    },
    test("Elements navigation on empty array succeeds in Clobber mode") {
      val original = Json.Array.empty
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Clobber)
      assertTrue(result == Right(Json.Array.empty))
    },
    test("Elements navigation with nested path navigates into all elements") {
      val original = Json.Array(
        Json.Object("value" -> Json.Number(1)),
        Json.Object("value" -> Json.Number(2)),
        Json.Object("value" -> Json.Number(3))
      )
      val path   = DynamicOptic.root.elements.field("value")
      val patch  = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(10))))
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(
        result == Right(
          Json.Array(
            Json.Object("value" -> Json.Number("11")),
            Json.Object("value" -> Json.Number("12")),
            Json.Object("value" -> Json.Number("13"))
          )
        )
      )
    },
    test("Elements navigation with Set replaces all elements") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.Set(Json.Number(0)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Array(Json.Number(0), Json.Number(0), Json.Number(0))))
    },
    test("applyToAllElements keeps original on error in Lenient mode") {
      val original = Json.Array(Json.Number(1), Json.String("not a number"), Json.Number(3))
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
      val result   = patch.apply(original, PatchMode.Lenient)
      assertTrue(result == Right(Json.Array(Json.Number("2"), Json.String("not a number"), Json.Number("4"))))
    },
    test("applyToAllElements keeps original on error in Clobber mode") {
      val original = Json.Array(Json.Number(1), Json.String("not a number"), Json.Number(3))
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
      val result   = patch.apply(original, PatchMode.Clobber)
      assertTrue(result == Right(Json.Array(Json.Number("2"), Json.String("not a number"), Json.Number("4"))))
    },
    test("navigateAllElements keeps original on error in Lenient mode") {
      val original = Json.Array(
        Json.Object("x" -> Json.Number(1)),
        Json.Object("y" -> Json.Number(2)) // missing field "x"
      )
      val path   = DynamicOptic.root.elements.field("x")
      val patch  = JsonPatch(path, Op.Set(Json.Number(100)))
      val result = patch.apply(original, PatchMode.Lenient)
      assertTrue(
        result == Right(
          Json.Array(
            Json.Object("x" -> Json.Number(100)),
            Json.Object("y" -> Json.Number(2)) // unchanged
          )
        )
      )
    },
    test("navigateAllElements keeps original on error in Clobber mode") {
      val original = Json.Array(
        Json.Object("x" -> Json.Number(1)),
        Json.Object("y" -> Json.Number(2)) // missing field "x"
      )
      val path   = DynamicOptic.root.elements.field("x")
      val patch  = JsonPatch(path, Op.Set(Json.Number(100)))
      val result = patch.apply(original, PatchMode.Clobber)
      assertTrue(
        result == Right(
          Json.Array(
            Json.Object("x" -> Json.Number(100)),
            Json.Object("y" -> Json.Number(2)) // unchanged
          )
        )
      )
    },
    test("navigateAllElements with deeply nested path") {
      val original = Json.Array(
        Json.Object("outer" -> Json.Object("inner" -> Json.Number(1))),
        Json.Object("outer" -> Json.Object("inner" -> Json.Number(2)))
      )
      val path   = DynamicOptic.root.elements.field("outer").field("inner")
      val patch  = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(100))))
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(
        result == Right(
          Json.Array(
            Json.Object("outer" -> Json.Object("inner" -> Json.Number("101"))),
            Json.Object("outer" -> Json.Object("inner" -> Json.Number("102")))
          )
        )
      )
    }
  )

  // Wrapped Navigation Suite

  private lazy val wrappedNavigationSuite = suite("Wrapped Navigation")(
    test("Wrapped navigation passes through and applies operation") {
      val original = Json.Number(42)
      val path     = DynamicOptic.root.wrapped
      val patch    = JsonPatch(path, Op.Set(Json.Number(100)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number(100)))
    },
    test("Wrapped navigation with nested path continues navigation") {
      val original = Json.Object("value" -> Json.Number(42))
      val path     = DynamicOptic.root.wrapped.field("value")
      val patch    = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(8))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Object("value" -> Json.Number("50"))))
    },
    test("Wrapped navigation with NumberDelta") {
      val original = Json.Number(10)
      val path     = DynamicOptic.root.wrapped
      val patch    = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertTrue(result == Right(Json.Number("15")))
    }
  )

  // JsonPatch.empty Suite

  private lazy val jsonPatchEmptySuite = suite("JsonPatch.empty")(
    test("JsonPatch.empty has no operations") {
      assertTrue(JsonPatch.empty.ops.isEmpty)
    },
    test("JsonPatch.empty.isEmpty returns true") {
      assertTrue(JsonPatch.empty.isEmpty)
    },
    test("JsonPatch.empty applied to any value returns that value unchanged") {
      val jsonValues = List(
        Json.Null,
        Json.Boolean(true),
        Json.Number(42),
        Json.String("hello"),
        Json.Array(Json.Number(1), Json.Number(2)),
        Json.Object("a" -> Json.Number(1))
      )
      assertTrue(
        jsonValues.forall(v => JsonPatch.empty.apply(v, PatchMode.Strict) == Right(v))
      )
    },
    test("JsonPatch.empty ++ patch equals patch") {
      val patch = JsonPatch.root(Op.Set(Json.Number(42)))
      assertTrue((JsonPatch.empty ++ patch) == patch)
    },
    test("patch ++ JsonPatch.empty equals patch") {
      val patch = JsonPatch.root(Op.Set(Json.Number(42)))
      assertTrue((patch ++ JsonPatch.empty) == patch)
    },
    test("JsonPatch.empty.toDynamicPatch produces empty DynamicPatch") {
      val dynamicPatch = JsonPatch.empty.toDynamicPatch
      assertTrue(dynamicPatch.ops.isEmpty)
    }
  )
}
