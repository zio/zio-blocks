package zio.blocks.schema.json

import zio.test._
import zio.test.Assertion._

object JsonPatchSpec extends ZIOSpecDefault {

  // Generators for test data
  val genJsonNull: Gen[Any, Json] = Gen.const(Json.Null)

  val genJsonBoolean: Gen[Any, Json] = Gen.boolean.map(Json.Boolean(_))

  val genJsonNumber: Gen[Any, Json] = Gen.oneOf(
    Gen.int(-1000, 1000).map(n => Json.number(n)),
    Gen.double(-1000.0, 1000.0).map(n => Json.number(n)),
    Gen.bigDecimal(BigDecimal(-1000), BigDecimal(1000)).map(n => Json.number(n))
  )

  val genJsonString: Gen[Any, Json] = Gen.alphaNumericStringBounded(0, 50).map(Json.String(_))

  def genJsonArray(depth: Int): Gen[Any, Json] =
    if (depth <= 0) Gen.const(Json.Array.empty)
    else Gen.listOfBounded(0, 5)(genJson(depth - 1)).map(elems => Json.Array(elems.toVector))

  def genJsonObject(depth: Int): Gen[Any, Json] =
    if (depth <= 0) Gen.const(Json.Object.empty)
    else
      Gen.listOfBounded(0, 5)(
        for {
          key   <- Gen.alphaNumericStringBounded(1, 10)
          value <- genJson(depth - 1)
        } yield (key, value)
      ).map(fields => Json.Object(fields.toVector))

  def genJson(depth: Int): Gen[Any, Json] =
    if (depth <= 0) Gen.oneOf(genJsonNull, genJsonBoolean, genJsonNumber, genJsonString)
    else Gen.oneOf(genJsonNull, genJsonBoolean, genJsonNumber, genJsonString, genJsonArray(depth - 1), genJsonObject(depth - 1))

  val genSimpleJson: Gen[Any, Json] = genJson(2)

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatchSpec")(
    suite("Monoid Laws")(
      test("L1: Left identity — (empty ++ p)(j) == p(j)") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch    = JsonPatch.diff(source, target)
          val composed = JsonPatch.empty ++ patch

          val result1 = composed(source, JsonPatchMode.Strict)
          val result2 = patch(source, JsonPatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("L2: Right identity — (p ++ empty)(j) == p(j)") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch    = JsonPatch.diff(source, target)
          val composed = patch ++ JsonPatch.empty

          val result1 = composed(source, JsonPatchMode.Strict)
          val result2 = patch(source, JsonPatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("L3: Associativity — ((p1 ++ p2) ++ p3)(j) == (p1 ++ (p2 ++ p3))(j)") {
        check(genSimpleJson, genSimpleJson, genSimpleJson, genSimpleJson) { (v0, v1, v2, v3) =>
          val p1 = JsonPatch.diff(v0, v1)
          val p2 = JsonPatch.diff(v1, v2)
          val p3 = JsonPatch.diff(v2, v3)

          val left  = (p1 ++ p2) ++ p3
          val right = p1 ++ (p2 ++ p3)

          val result1 = left(v0, JsonPatchMode.Strict)
          val result2 = right(v0, JsonPatchMode.Strict)

          assertTrue(result1 == result2)
        }
      }
    ),
    suite("Diff/Apply Laws")(
      test("L4: Roundtrip — diff(a, b)(a) == Right(b)") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch  = JsonPatch.diff(source, target)
          val result = patch(source, JsonPatchMode.Strict)

          assertTrue(result == Right(target))
        }
      },
      test("L5: Identity diff — diff(j, j).isEmpty") {
        check(genSimpleJson) { json =>
          val patch = JsonPatch.diff(json, json)
          assertTrue(patch.isEmpty)
        }
      },
      test("L6: Diff composition — (diff(a, b) ++ diff(b, c))(a) == Right(c)") {
        check(genSimpleJson, genSimpleJson, genSimpleJson) { (a, b, c) =>
          val p1     = JsonPatch.diff(a, b)
          val p2     = JsonPatch.diff(b, c)
          val result = (p1 ++ p2)(a, JsonPatchMode.Strict)

          assertTrue(result == Right(c))
        }
      }
    ),
    suite("PatchMode Laws")(
      test("L7: Lenient subsumes Strict — if p(j, Strict) == Right(r) then p(j, Lenient) == Right(r)") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch        = JsonPatch.diff(source, target)
          val strictResult = patch(source, JsonPatchMode.Strict)

          strictResult match {
            case Right(r) =>
              val lenientResult = patch(source, JsonPatchMode.Lenient)
              assertTrue(lenientResult == Right(r))
            case Left(_) =>
              assertTrue(true) // Strict failed, so no constraint on Lenient
          }
        }
      }
    ),
    suite("Op.Set Tests (T2)")(
      test("Set replaces a value") {
        val json   = Json.number(42)
        val patch  = JsonPatch.root(JsonPatch.Op.Set(Json.String("hello")))
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.String("hello")))
      },
      test("Set replaces nested value") {
        val json = Json.Object(Vector(
          "name" -> Json.String("Alice"),
          "age"  -> Json.number(30)
        ))
        val patch = JsonPatch(
          DynamicOptic.root.field("name"),
          JsonPatch.Op.Set(Json.String("Bob"))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Object(Vector(
          "name" -> Json.String("Bob"),
          "age"  -> Json.number(30)
        ))))
      }
    ),
    suite("PrimitiveDelta Tests (T2)")(
      test("NumberDelta adds to a number") {
        val json   = Json.number(10)
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Number("15")))
      },
      test("T6: NumberDelta with positive delta") {
        val json   = Json.number(100)
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(50))))
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Number("150")))
      },
      test("T6: NumberDelta with negative delta") {
        val json   = Json.number(100)
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-30))))
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Number("70")))
      },
      test("T6: NumberDelta with zero delta") {
        val json   = Json.number(42)
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(0))))
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Number("42")))
      },
      test("T6: NumberDelta with decimal delta") {
        val json   = Json.number(10.5)
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal("2.75"))))
        val result = patch(json, JsonPatchMode.Strict)

        result match {
          case Right(Json.Number(v)) => assertTrue(BigDecimal(v) == BigDecimal("13.25"))
          case _                     => assertTrue(false)
        }
      }
    ),
    suite("StringOp Tests (T5)")(
      test("StringOp.Insert inserts text at index") {
        val json  = Json.String("hello world")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(
            JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(5, " beautiful")))
          )
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.String("hello beautiful world")))
      },
      test("StringOp.Delete removes characters") {
        val json  = Json.String("hello beautiful world")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(
            JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(5, 10)))
          )
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.String("hello world")))
      },
      test("StringOp.Append appends text") {
        val json  = Json.String("hello")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(
            JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append(" world")))
          )
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.String("hello world")))
      },
      test("StringOp.Modify replaces characters") {
        val json  = Json.String("hello world")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(
            JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(0, 5, "hi")))
          )
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.String("hi world")))
      }
    ),
    suite("ArrayOp Tests (T3)")(
      test("ArrayOp.Insert inserts values at index") {
        val json = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ArrayEdit(Vector(
            JsonPatch.ArrayOp.Insert(1, Vector(Json.number(10), Json.number(20)))
          ))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Array(Vector(
          Json.number(1), Json.number(10), Json.number(20), Json.number(2), Json.number(3)
        ))))
      },
      test("ArrayOp.Append appends values") {
        val json = Json.Array(Vector(Json.number(1), Json.number(2)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ArrayEdit(Vector(
            JsonPatch.ArrayOp.Append(Vector(Json.number(3), Json.number(4)))
          ))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Array(Vector(
          Json.number(1), Json.number(2), Json.number(3), Json.number(4)
        ))))
      },
      test("ArrayOp.Delete removes elements") {
        val json = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3), Json.number(4)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ArrayEdit(Vector(
            JsonPatch.ArrayOp.Delete(1, 2)
          ))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Array(Vector(Json.number(1), Json.number(4)))))
      },
      test("ArrayOp.Modify modifies an element") {
        val json = Json.Array(Vector(Json.number(1), Json.number(2), Json.number(3)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ArrayEdit(Vector(
            JsonPatch.ArrayOp.Modify(1, JsonPatch.Op.Set(Json.number(20)))
          ))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Array(Vector(Json.number(1), Json.number(20), Json.number(3)))))
      }
    ),
    suite("ObjectOp Tests (T4)")(
      test("ObjectOp.Add adds a field") {
        val json = Json.Object(Vector("a" -> Json.number(1)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ObjectEdit(Vector(
            JsonPatch.ObjectOp.Add("b", Json.number(2))
          ))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Object(Vector("a" -> Json.number(1), "b" -> Json.number(2)))))
      },
      test("ObjectOp.Remove removes a field") {
        val json = Json.Object(Vector("a" -> Json.number(1), "b" -> Json.number(2)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ObjectEdit(Vector(
            JsonPatch.ObjectOp.Remove("a")
          ))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Object(Vector("b" -> Json.number(2)))))
      },
      test("ObjectOp.Modify modifies a field") {
        val json = Json.Object(Vector("a" -> Json.number(1), "b" -> Json.number(2)))
        val innerPatch = JsonPatch.root(JsonPatch.Op.Set(Json.number(100)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ObjectEdit(Vector(
            JsonPatch.ObjectOp.Modify("a", innerPatch)
          ))
        )
        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Object(Vector("a" -> Json.number(100), "b" -> Json.number(2)))))
      }
    ),
    suite("Op.Nested Tests (T2)")(
      test("Nested patch applies inner operations") {
        val json = Json.Object(Vector(
          "user" -> Json.Object(Vector(
            "name" -> Json.String("Alice"),
            "age"  -> Json.number(30)
          ))
        ))

        val innerPatch = JsonPatch(Vector(
          JsonPatch.JsonPatchOp(
            DynamicOptic.root.field("age"),
            JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1)))
          )
        ))

        val patch = JsonPatch(
          DynamicOptic.root.field("user"),
          JsonPatch.Op.Nested(innerPatch)
        )

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Object(Vector(
          "user" -> Json.Object(Vector(
            "name" -> Json.String("Alice"),
            "age"  -> Json.number(31)
          ))
        ))))
      }
    ),
    suite("JsonPatchMode Tests (T7)")(
      test("Strict mode fails on missing field") {
        val json  = Json.Object(Vector("a" -> Json.number(1)))
        val patch = JsonPatch(DynamicOptic.root.field("nonexistent"), JsonPatch.Op.Set(Json.number(2)))

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result.isLeft)
      },
      test("Lenient mode skips on missing field") {
        val json  = Json.Object(Vector("a" -> Json.number(1)))
        val patch = JsonPatch(DynamicOptic.root.field("nonexistent"), JsonPatch.Op.Set(Json.number(2)))

        val result = patch(json, JsonPatchMode.Lenient)

        assertTrue(result == Right(json))
      },
      test("Clobber mode creates missing field when at leaf") {
        val json  = Json.Object(Vector("a" -> Json.number(1)))
        val patch = JsonPatch(DynamicOptic.root.field("b"), JsonPatch.Op.Set(Json.number(2)))

        val result = patch(json, JsonPatchMode.Clobber)

        assertTrue(result == Right(Json.Object(Vector("a" -> Json.number(1), "b" -> Json.number(2)))))
      },
      test("Strict mode fails on Remove non-existent field") {
        val json = Json.Object(Vector("a" -> Json.number(1)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("nonexistent")))
        )

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result.isLeft)
      },
      test("Lenient mode skips Remove non-existent field") {
        val json = Json.Object(Vector("a" -> Json.number(1)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("nonexistent")))
        )

        val result = patch(json, JsonPatchMode.Lenient)

        assertTrue(result == Right(json))
      },
      test("Clobber mode skips Remove non-existent field") {
        val json = Json.Object(Vector("a" -> Json.number(1)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("nonexistent")))
        )

        val result = patch(json, JsonPatchMode.Clobber)

        assertTrue(result == Right(json))
      }
    ),
    suite("DynamicPatch Conversion Tests (T8)")(
      test("toDynamicPatch roundtrip") {
        val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("test")))
        val dynPatch = patch.toDynamicPatch
        val converted = JsonPatch.fromDynamicPatch(dynPatch)

        converted match {
          case Right(p) => assertTrue(p.ops.length == 1)
          case Left(_)  => assertTrue(false)
        }
      },
      test("fromDynamicPatch converts numeric deltas") {
        import zio.blocks.schema.patch.{DynamicPatch => DP}
        import zio.blocks.schema.DynamicValue
        import zio.blocks.schema.PrimitiveValue

        val dynPatch = DP.root(DP.Operation.PrimitiveDelta(DP.PrimitiveOp.IntDelta(5)))
        val converted = JsonPatch.fromDynamicPatch(dynPatch)

        converted match {
          case Right(p) =>
            p.ops.headOption match {
              case Some(JsonPatch.JsonPatchOp(_, JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(delta)))) =>
                assertTrue(delta == BigDecimal(5))
              case _ =>
                assertTrue(false)
            }
          case Left(_) =>
            assertTrue(false)
        }
      }
    ),
    suite("Edge Cases (T9)")(
      test("Empty array operations") {
        val json  = Json.Array.empty
        val patch = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Vector(Json.number(1))))))

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Array(Vector(Json.number(1)))))
      },
      test("Empty object operations") {
        val json  = Json.Object.empty
        val patch = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", Json.number(1)))))

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Object(Vector("a" -> Json.number(1)))))
      },
      test("Empty string operations") {
        val json = Json.String("")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "hello"))))
        )

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.String("hello")))
      },
      test("Nested structures") {
        val json = Json.Object(Vector(
          "level1" -> Json.Object(Vector(
            "level2" -> Json.Object(Vector(
              "value" -> Json.number(1)
            ))
          ))
        ))

        val patch = JsonPatch(
          DynamicOptic.root.field("level1").field("level2").field("value"),
          JsonPatch.Op.Set(Json.number(999))
        )

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result == Right(Json.Object(Vector(
          "level1" -> Json.Object(Vector(
            "level2" -> Json.Object(Vector(
              "value" -> Json.number(999)
            ))
          ))
        ))))
      }
    ),
    suite("Error Cases (T10)")(
      test("Invalid path - type mismatch") {
        val json  = Json.String("hello")
        val patch = JsonPatch(DynamicOptic.root.field("name"), JsonPatch.Op.Set(Json.number(1)))

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result.isLeft)
      },
      test("Out of bounds array index") {
        val json  = Json.Array(Vector(Json.number(1), Json.number(2)))
        val patch = JsonPatch(DynamicOptic.root.at(10), JsonPatch.Op.Set(Json.number(99)))

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result.isLeft)
      },
      test("Type mismatch - number delta on string") {
        val json  = Json.String("hello")
        val patch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result.isLeft)
      },
      test("Type mismatch - string edit on number") {
        val json = Json.number(42)
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append("x"))))
        )

        val result = patch(json, JsonPatchMode.Strict)

        assertTrue(result.isLeft)
      }
    )
  )

  // Helper to import DynamicOptic
  import zio.blocks.schema.DynamicOptic
}
