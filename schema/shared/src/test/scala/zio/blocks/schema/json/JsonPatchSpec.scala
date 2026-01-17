package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec}
import zio.test._

object JsonPatchSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatchSpec")(
    suite("T2: Operation Type Tests")(
      suite("Op.Set")(
        test("replaces value at root") {
          val json   = Json.Number(BigDecimal(10))
          val patch  = JsonPatch.root(JsonPatch.Op.Set(Json.Number(BigDecimal(20))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Number(BigDecimal(20))))
        },
        test("replaces value at path in object") {
          val json  = Json.Object(Vector("name" -> Json.String("Alice"), "age" -> Json.Number(BigDecimal(30))))
          val patch = JsonPatch(DynamicOptic.root.field("name"), JsonPatch.Op.Set(Json.String("Bob")))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("name" -> Json.String("Bob"), "age" -> Json.Number(BigDecimal(30))))))
        },
        test("replaces entire object") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.Set(Json.Object(Vector("b" -> Json.Number(BigDecimal(2))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("b" -> Json.Number(BigDecimal(2))))))
        }
      ),
      suite("Op.PrimitiveDelta")(
        test("NumberDelta adds to number") {
          val json   = Json.Number(BigDecimal(10))
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Number(BigDecimal(15))))
        },
        test("NumberDelta subtracts from number") {
          val json   = Json.Number(BigDecimal(10))
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-3))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Number(BigDecimal(7))))
        },
        test("NumberDelta with zero delta") {
          val json   = Json.Number(BigDecimal(10))
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(0))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Number(BigDecimal(10))))
        },
        test("NumberDelta with decimal values") {
          val json   = Json.Number(BigDecimal(10.5))
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(0.25))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Number(BigDecimal(10.75))))
        }
      ),
      suite("Op.ArrayEdit")(
        test("applies array operations") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 1))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(3))))))
        }
      ),
      suite("Op.ObjectEdit")(
        test("applies object operations") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1)), "b" -> Json.Number(BigDecimal(2))))
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("a"))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("b" -> Json.Number(BigDecimal(2))))))
        }
      ),
      suite("Op.Nested")(
        test("applies nested patch") {
          val json = Json.Object(Vector("user" -> Json.Object(Vector("age" -> Json.Number(BigDecimal(30))))))
          val innerPatch = JsonPatch(
            DynamicOptic.root.field("age"),
            JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1)))
          )
          val patch  = JsonPatch(DynamicOptic.root.field("user"), JsonPatch.Op.Nested(innerPatch))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("user" -> Json.Object(Vector("age" -> Json.Number(BigDecimal(31))))))))
        }
      )
    ),
    suite("T3: ArrayOp Tests")(
      suite("ArrayOp.Insert")(
        test("inserts at beginning") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(0, Vector(Json.Number(BigDecimal(1)))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))))
        },
        test("inserts in middle") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(3))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(1, Vector(Json.Number(BigDecimal(2)))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))))
        },
        test("inserts at end") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(2, Vector(Json.Number(BigDecimal(3)))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))))
        },
        test("inserts multiple values") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(1, Vector(Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3)))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))))
        }
      ),
      suite("ArrayOp.Append")(
        test("appends to empty array") {
          val json   = Json.Array(Vector.empty)
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Vector(Json.Number(BigDecimal(1)))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1))))))
        },
        test("appends to non-empty array") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Vector(Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3)))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))))
        }
      ),
      suite("ArrayOp.Delete")(
        test("deletes single element") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 1))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(3))))))
        },
        test("deletes multiple elements") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2)), Json.Number(BigDecimal(3)), Json.Number(BigDecimal(4))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 2))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(4))))))
        },
        test("deletes first element") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 1))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(2))))))
        },
        test("deletes last element") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 1))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1))))))
        }
      ),
      suite("ArrayOp.Modify")(
        test("modifies element at index") {
          val json   = Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(10)), Json.Number(BigDecimal(3))))
          val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(
            JsonPatch.ArrayOp.Modify(1, JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
          )))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(15)), Json.Number(BigDecimal(3))))))
        }
      )
    ),
    suite("T4: ObjectOp Tests")(
      suite("ObjectOp.Add")(
        test("adds field to empty object") {
          val json   = Json.Object(Vector.empty)
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("name", Json.String("Alice")))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("name" -> Json.String("Alice")))))
        },
        test("adds field to non-empty object") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", Json.Number(BigDecimal(2))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("a" -> Json.Number(BigDecimal(1)), "b" -> Json.Number(BigDecimal(2))))))
        },
        test("fails on duplicate key in Strict mode") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", Json.Number(BigDecimal(2))))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result.isLeft)
        },
        test("overwrites duplicate key in Clobber mode") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", Json.Number(BigDecimal(2))))))
          val result = patch(json, JsonPatchMode.Clobber)
          assertTrue(result == Right(Json.Object(Vector("a" -> Json.Number(BigDecimal(2))))))
        }
      ),
      suite("ObjectOp.Remove")(
        test("removes existing field") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1)), "b" -> Json.Number(BigDecimal(2))))
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("a"))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("b" -> Json.Number(BigDecimal(2))))))
        },
        test("fails on missing field in Strict mode") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("b"))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result.isLeft)
        },
        test("skips missing field in Lenient mode") {
          val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("b"))))
          val result = patch(json, JsonPatchMode.Lenient)
          assertTrue(result == Right(Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))))
        }
      ),
      suite("ObjectOp.Modify")(
        test("modifies existing field") {
          val json = Json.Object(Vector("count" -> Json.Number(BigDecimal(10))))
          val innerPatch = JsonPatch.root(
            JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
          )
          val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("count", innerPatch))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.Object(Vector("count" -> Json.Number(BigDecimal(15))))))
        }
      )
    ),
    suite("T5: StringOp Tests")(
      suite("StringOp.Insert")(
        test("inserts at beginning") {
          val json   = Json.String("world")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "hello ")))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("hello world")))
        },
        test("inserts in middle") {
          val json   = Json.String("helloworld")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(5, " ")))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("hello world")))
        },
        test("inserts at end") {
          val json   = Json.String("hello")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(5, " world")))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("hello world")))
        }
      ),
      suite("StringOp.Delete")(
        test("deletes from beginning") {
          val json   = Json.String("hello world")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(0, 6)))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("world")))
        },
        test("deletes from middle") {
          val json   = Json.String("hello world")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(5, 1)))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("helloworld")))
        },
        test("deletes from end") {
          val json   = Json.String("hello world")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(5, 6)))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("hello")))
        }
      ),
      suite("StringOp.Append")(
        test("appends to string") {
          val json   = Json.String("hello")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append(" world")))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("hello world")))
        },
        test("appends to empty string") {
          val json   = Json.String("")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append("hello")))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("hello")))
        }
      ),
      suite("StringOp.Modify")(
        test("modifies characters") {
          val json   = Json.String("hello world")
          val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(0, 5, "hi")))))
          val result = patch(json, JsonPatchMode.Strict)
          assertTrue(result == Right(Json.String("hi world")))
        }
      )
    ),
    suite("T6: NumberDelta Extended Tests")(
      test("positive delta") {
        val json   = Json.Number(BigDecimal(0))
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(100))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Number(BigDecimal(100))))
      },
      test("negative delta") {
        val json   = Json.Number(BigDecimal(100))
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-100))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Number(BigDecimal(0))))
      },
      test("zero delta") {
        val json   = Json.Number(BigDecimal(42))
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(0))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Number(BigDecimal(42))))
      },
      test("decimal delta") {
        val json   = Json.Number(BigDecimal(1.5))
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(0.25))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Number(BigDecimal(1.75))))
      },
      test("large number delta") {
        val json   = Json.Number(BigDecimal("99999999999999999999"))
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Number(BigDecimal("100000000000000000000"))))
      }
    ),
    suite("T7: JsonPatchMode Tests")(
      test("Strict mode fails on missing field") {
        val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
        val patch  = JsonPatch(DynamicOptic.root.field("b"), JsonPatch.Op.Set(Json.Number(BigDecimal(2))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Lenient mode skips on missing field") {
        val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
        val patch  = JsonPatch(DynamicOptic.root.field("b"), JsonPatch.Op.Set(Json.Number(BigDecimal(2))))
        val result = patch(json, JsonPatchMode.Lenient)
        assertTrue(result == Right(json))
      },
      test("Clobber mode skips on missing field") {
        val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
        val patch  = JsonPatch(DynamicOptic.root.field("b"), JsonPatch.Op.Set(Json.Number(BigDecimal(2))))
        val result = patch(json, JsonPatchMode.Clobber)
        assertTrue(result == Right(json))
      },
      test("Clobber mode clamps out-of-bounds array insert") {
        val json  = Json.Array(Vector(Json.Number(BigDecimal(1))))
        val patch = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(10, Vector(Json.Number(BigDecimal(2)))))))

        val strictResult  = patch(json, JsonPatchMode.Strict)
        val clobberResult = patch(json, JsonPatchMode.Clobber)

        assertTrue(strictResult.isLeft) &&
        assertTrue(clobberResult == Right(Json.Array(Vector(Json.Number(BigDecimal(1)), Json.Number(BigDecimal(2))))))
      }
    ),
    suite("T8: DynamicPatch Roundtrip")(
      test("toDynamicPatch and fromDynamicPatch roundtrip") {
        val patch = JsonPatch.diff(
          Json.Object(Vector("x" -> Json.Number(BigDecimal(1)))),
          Json.Object(Vector("x" -> Json.Number(BigDecimal(5))))
        )
        val dynamicPatch = patch.toDynamicPatch
        val roundtrip    = JsonPatch.fromDynamicPatch(dynamicPatch)
        
        // Apply both patches to verify equivalence
        val source = Json.Object(Vector("x" -> Json.Number(BigDecimal(1))))
        val original = patch(source, JsonPatchMode.Strict)
        val converted = roundtrip.flatMap(_.apply(source, JsonPatchMode.Strict))
        
        assertTrue(converted == original)
      }
    ),
    suite("T9: Edge Cases")(
      test("empty array diff") {
        val old    = Json.Array(Vector.empty)
        val new_   = Json.Array(Vector(Json.Number(BigDecimal(1))))
        val patch  = JsonPatch.diff(old, new_)
        val result = patch(old, JsonPatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("diff to empty array") {
        val old    = Json.Array(Vector(Json.Number(BigDecimal(1))))
        val new_   = Json.Array(Vector.empty)
        val patch  = JsonPatch.diff(old, new_)
        val result = patch(old, JsonPatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("empty object diff") {
        val old    = Json.Object(Vector.empty)
        val new_   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
        val patch  = JsonPatch.diff(old, new_)
        val result = patch(old, JsonPatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("diff to empty object") {
        val old    = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
        val new_   = Json.Object(Vector.empty)
        val patch  = JsonPatch.diff(old, new_)
        val result = patch(old, JsonPatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("empty string diff") {
        val old    = Json.String("")
        val new_   = Json.String("hello")
        val patch  = JsonPatch.diff(old, new_)
        val result = patch(old, JsonPatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("diff to empty string") {
        val old    = Json.String("hello")
        val new_   = Json.String("")
        val patch  = JsonPatch.diff(old, new_)
        val result = patch(old, JsonPatchMode.Strict)
        assertTrue(result == Right(new_))
      },
      test("deeply nested structure") {
        val old = Json.Object(Vector(
          "a" -> Json.Object(Vector(
            "b" -> Json.Object(Vector(
              "c" -> Json.Number(BigDecimal(1))
            ))
          ))
        ))
        val new_ = Json.Object(Vector(
          "a" -> Json.Object(Vector(
            "b" -> Json.Object(Vector(
              "c" -> Json.Number(BigDecimal(2))
            ))
          ))
        ))
        val patch  = JsonPatch.diff(old, new_)
        val result = patch(old, JsonPatchMode.Strict)
        assertTrue(result == Right(new_))
      }
    ),
    suite("T10: Error Cases")(
      test("type mismatch - number delta on string") {
        val json   = Json.String("hello")
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("type mismatch - string edit on number") {
        val json   = Json.Number(BigDecimal(42))
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append("x")))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("array edit on non-array") {
        val json   = Json.Object(Vector.empty)
        val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Vector(Json.Null)))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("object edit on non-object") {
        val json   = Json.Array(Vector.empty)
        val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("x", Json.Null))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("index out of bounds") {
        val json   = Json.Array(Vector(Json.Number(BigDecimal(1))))
        val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(5, 1))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("invalid path") {
        val json   = Json.Object(Vector("a" -> Json.Number(BigDecimal(1))))
        val patch  = JsonPatch(DynamicOptic.root.field("a").field("b"), JsonPatch.Op.Set(Json.Null))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      }
    )
  )
}
