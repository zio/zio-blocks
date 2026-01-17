package zio.blocks.schema.json

import zio.test._
import zio.test.Assertion._

object JsonPatchSpec extends ZIOSpecDefault {
  
  def spec = suite("JsonPatchSpec")(
    suite("Add operation")(
      test("add field to object") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root / "age",
          Json.Num(30)
        ))
        val result = patch.apply(json)
        val expected = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        assertTrue(result == Right(expected))
      },
      test("add element to array") {
        val json = Json.arr(Json.Num(1), Json.Num(2))
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root(1),
          Json.Num(1.5)
        ))
        val result = patch.apply(json)
        val expected = Json.arr(Json.Num(1), Json.Num(1.5), Json.Num(2))
        assertTrue(result == Right(expected))
      },
      test("add element at end of array") {
        val json = Json.arr(Json.Num(1), Json.Num(2))
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root(2),
          Json.Num(3)
        ))
        val result = patch.apply(json)
        val expected = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        assertTrue(result == Right(expected))
      },
      test("add to nested path") {
        val json = Json.obj("user" -> Json.obj("name" -> Json.Str("Alice")))
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root / "user" / "age",
          Json.Num(30)
        ))
        val result = patch.apply(json)
        val expected = Json.obj("user" -> Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30)))
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("Remove operation")(
      test("remove field from object") {
        val json = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        val patch = JsonPatch.single(JsonPatch.Operation.Remove(JsonPath.root / "age"))
        val result = patch.apply(json)
        val expected = Json.obj("name" -> Json.Str("Alice"))
        assertTrue(result == Right(expected))
      },
      test("remove element from array") {
        val json = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        val patch = JsonPatch.single(JsonPatch.Operation.Remove(JsonPath.root(1)))
        val result = patch.apply(json)
        val expected = Json.arr(Json.Num(1), Json.Num(3))
        assertTrue(result == Right(expected))
      },
      test("remove from nested path") {
        val json = Json.obj("user" -> Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30)))
        val patch = JsonPatch.single(JsonPatch.Operation.Remove(JsonPath.root / "user" / "age"))
        val result = patch.apply(json)
        val expected = Json.obj("user" -> Json.obj("name" -> Json.Str("Alice")))
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("Replace operation")(
      test("replace field value") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val patch = JsonPatch.single(JsonPatch.Operation.Replace(
          JsonPath.root / "name",
          Json.Str("Bob")
        ))
        val result = patch.apply(json)
        val expected = Json.obj("name" -> Json.Str("Bob"))
        assertTrue(result == Right(expected))
      },
      test("replace array element") {
        val json = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        val patch = JsonPatch.single(JsonPatch.Operation.Replace(
          JsonPath.root(1),
          Json.Num(20)
        ))
        val result = patch.apply(json)
        val expected = Json.arr(Json.Num(1), Json.Num(20), Json.Num(3))
        assertTrue(result == Right(expected))
      },
      test("replace entire document") {
        val json = Json.obj("old" -> Json.Str("value"))
        val patch = JsonPatch.single(JsonPatch.Operation.Replace(
          JsonPath.root,
          Json.obj("new" -> Json.Str("value"))
        ))
        val result = patch.apply(json)
        val expected = Json.obj("new" -> Json.Str("value"))
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("Move operation")(
      test("move field within object") {
        val json = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2))
        val patch = JsonPatch.single(JsonPatch.Operation.Move(
          JsonPath.root / "a",
          JsonPath.root / "c"
        ))
        val result = patch.apply(json)
        val expected = Json.obj("b" -> Json.Num(2), "c" -> Json.Num(1))
        assertTrue(result == Right(expected))
      },
      test("move array element") {
        val json = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        val patch = JsonPatch.single(JsonPatch.Operation.Move(
          JsonPath.root(0),
          JsonPath.root(2)
        ))
        val result = patch.apply(json)
        // After removing index 0, we have [2, 3], then insert 1 at index 2 (end)
        val expected = Json.arr(Json.Num(2), Json.Num(3), Json.Num(1))
        assertTrue(result == Right(expected))
      },
      test("move between different paths") {
        val json = Json.obj(
          "user" -> Json.obj("name" -> Json.Str("Alice")),
          "metadata" -> Json.obj()
        )
        val patch = JsonPatch.single(JsonPatch.Operation.Move(
          JsonPath.root / "user" / "name",
          JsonPath.root / "metadata" / "userName"
        ))
        val result = patch.apply(json)
        val expected = Json.obj(
          "user" -> Json.obj(),
          "metadata" -> Json.obj("userName" -> Json.Str("Alice"))
        )
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("Copy operation")(
      test("copy field within object") {
        val json = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2))
        val patch = JsonPatch.single(JsonPatch.Operation.Copy(
          JsonPath.root / "a",
          JsonPath.root / "c"
        ))
        val result = patch.apply(json)
        val expected = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2), "c" -> Json.Num(1))
        assertTrue(result == Right(expected))
      },
      test("copy array element") {
        val json = Json.arr(Json.Num(1), Json.Num(2))
        val patch = JsonPatch.single(JsonPatch.Operation.Copy(
          JsonPath.root(0),
          JsonPath.root(2)
        ))
        val result = patch.apply(json)
        val expected = Json.arr(Json.Num(1), Json.Num(2), Json.Num(1))
        assertTrue(result == Right(expected))
      },
      test("copy nested value") {
        val json = Json.obj(
          "user" -> Json.obj("name" -> Json.Str("Alice")),
          "backup" -> Json.obj()
        )
        val patch = JsonPatch.single(JsonPatch.Operation.Copy(
          JsonPath.root / "user" / "name",
          JsonPath.root / "backup" / "name"
        ))
        val result = patch.apply(json)
        val expected = Json.obj(
          "user" -> Json.obj("name" -> Json.Str("Alice")),
          "backup" -> Json.obj("name" -> Json.Str("Alice"))
        )
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("Test operation")(
      test("test succeeds when values match") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val patch = JsonPatch.single(JsonPatch.Operation.Test(
          JsonPath.root / "name",
          Json.Str("Alice")
        ))
        val result = patch.apply(json)
        assertTrue(result == Right(json))
      },
      test("test fails when values don't match") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val patch = JsonPatch.single(JsonPatch.Operation.Test(
          JsonPath.root / "name",
          Json.Str("Bob")
        ))
        val result = patch.apply(json)
        assertTrue(result.isLeft)
      },
      test("test fails when path not found") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val patch = JsonPatch.single(JsonPatch.Operation.Test(
          JsonPath.root / "age",
          Json.Num(30)
        ))
        val result = patch.apply(json)
        assertTrue(result.isLeft)
      }
    ),
    
    suite("Diff computation")(
      test("diff identical values") {
        val json = Json.obj("name" -> Json.Str("Alice"))
        val patch = JsonPatch.diff(json, json)
        assertTrue(patch.isEmpty)
      },
      test("diff simple field change") {
        val old = Json.obj("name" -> Json.Str("Alice"))
        val updated = Json.obj("name" -> Json.Str("Bob"))
        val patch = JsonPatch.diff(old, updated)
        val result = patch.apply(old)
        assertTrue(result == Right(updated))
      },
      test("diff field addition") {
        val old = Json.obj("name" -> Json.Str("Alice"))
        val updated = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        val patch = JsonPatch.diff(old, updated)
        val result = patch.apply(old)
        assertTrue(result == Right(updated))
      },
      test("diff field removal") {
        val old = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        val updated = Json.obj("name" -> Json.Str("Alice"))
        val patch = JsonPatch.diff(old, updated)
        val result = patch.apply(old)
        assertTrue(result == Right(updated))
      },
      test("diff nested objects") {
        val old = Json.obj("user" -> Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30)))
        val updated = Json.obj("user" -> Json.obj("name" -> Json.Str("Bob"), "age" -> Json.Num(30)))
        val patch = JsonPatch.diff(old, updated)
        val result = patch.apply(old)
        assertTrue(result == Right(updated))
      },
      test("diff arrays") {
        val old = Json.arr(Json.Num(1), Json.Num(2), Json.Num(3))
        val updated = Json.arr(Json.Num(1), Json.Num(20), Json.Num(3), Json.Num(4))
        val patch = JsonPatch.diff(old, updated)
        val result = patch.apply(old)
        assertTrue(result == Right(updated))
      },
      test("diff complex nested structure") {
        val old = Json.obj(
          "users" -> Json.arr(
            Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30)),
            Json.obj("name" -> Json.Str("Bob"), "age" -> Json.Num(25))
          ),
          "count" -> Json.Num(2)
        )
        val updated = Json.obj(
          "users" -> Json.arr(
            Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(31)),
            Json.obj("name" -> Json.Str("Bob"), "age" -> Json.Num(25)),
            Json.obj("name" -> Json.Str("Charlie"), "age" -> Json.Num(35))
          ),
          "count" -> Json.Num(3)
        )
        val patch = JsonPatch.diff(old, updated)
        val result = patch.apply(old)
        assertTrue(result == Right(updated))
      }
    ),
    
    suite("Patch composition")(
      test("compose two patches") {
        val json = Json.obj("a" -> Json.Num(1))
        val patch1 = JsonPatch.single(JsonPatch.Operation.Add(JsonPath.root / "b", Json.Num(2)))
        val patch2 = JsonPatch.single(JsonPatch.Operation.Add(JsonPath.root / "c", Json.Num(3)))
        val composed = patch1 ++ patch2
        val result = composed.apply(json)
        val expected = Json.obj("a" -> Json.Num(1), "b" -> Json.Num(2), "c" -> Json.Num(3))
        assertTrue(result == Right(expected))
      },
      test("compose multiple operations") {
        val json = Json.obj("name" -> Json.Str("Alice"), "age" -> Json.Num(30))
        val patch = JsonPatch(Vector(
          JsonPatch.Operation.Replace(JsonPath.root / "name", Json.Str("Bob")),
          JsonPatch.Operation.Remove(JsonPath.root / "age"),
          JsonPatch.Operation.Add(JsonPath.root / "active", Json.Bool(true))
        ))
        val result = patch.apply(json)
        val expected = Json.obj("name" -> Json.Str("Bob"), "active" -> Json.Bool(true))
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("RFC 6902 format")(
      test("serialize add operation") {
        val op = JsonPatch.Operation.Add(JsonPath.root / "name", Json.Str("Alice"))
        val json = op.toJson
        val expected = Json.obj(
          "op" -> Json.Str("add"),
          "path" -> Json.Str("/name"),
          "value" -> Json.Str("Alice")
        )
        assertTrue(json == expected)
      },
      test("serialize remove operation") {
        val op = JsonPatch.Operation.Remove(JsonPath.root / "name")
        val json = op.toJson
        val expected = Json.obj(
          "op" -> Json.Str("remove"),
          "path" -> Json.Str("/name")
        )
        assertTrue(json == expected)
      },
      test("serialize move operation") {
        val op = JsonPatch.Operation.Move(JsonPath.root / "a", JsonPath.root / "b")
        val json = op.toJson
        val expected = Json.obj(
          "op" -> Json.Str("move"),
          "from" -> Json.Str("/a"),
          "path" -> Json.Str("/b")
        )
        assertTrue(json == expected)
      },
      test("parse add operation") {
        val json = Json.obj(
          "op" -> Json.Str("add"),
          "path" -> Json.Str("/name"),
          "value" -> Json.Str("Alice")
        )
        val result = JsonPatch.Operation.fromJson(json)
        assertTrue(result.isRight && result.toOption.get.isInstanceOf[JsonPatch.Operation.Add])
      },
      test("parse remove operation") {
        val json = Json.obj(
          "op" -> Json.Str("remove"),
          "path" -> Json.Str("/name")
        )
        val result = JsonPatch.Operation.fromJson(json)
        assertTrue(result.isRight && result.toOption.get.isInstanceOf[JsonPatch.Operation.Remove])
      },
      test("parse patch array") {
        val json = Json.arr(
          Json.obj("op" -> Json.Str("add"), "path" -> Json.Str("/a"), "value" -> Json.Num(1)),
          Json.obj("op" -> Json.Str("remove"), "path" -> Json.Str("/b"))
        )
        val result = JsonPatch.fromJson(json)
        assertTrue(result.isRight && result.toOption.get.operations.length == 2)
      },
      test("roundtrip patch through JSON") {
        val original = JsonPatch(Vector(
          JsonPatch.Operation.Add(JsonPath.root / "name", Json.Str("Alice")),
          JsonPatch.Operation.Remove(JsonPath.root / "age"),
          JsonPatch.Operation.Replace(JsonPath.root / "active", Json.Bool(true))
        ))
        val json = original.toJson
        val parsed = JsonPatch.fromJson(json)
        assertTrue(parsed.isRight && parsed.toOption.get.operations.length == 3)
      }
    ),
    
    suite("Error handling")(
      test("add to non-existent parent path") {
        val json = Json.obj("a" -> Json.Num(1))
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root / "b" / "c",
          Json.Num(2)
        ))
        val result = patch.apply(json)
        assertTrue(result.isLeft)
      },
      test("remove non-existent path") {
        val json = Json.obj("a" -> Json.Num(1))
        val patch = JsonPatch.single(JsonPatch.Operation.Remove(JsonPath.root / "b"))
        val result = patch.apply(json)
        assertTrue(result.isLeft)
      },
      test("array index out of bounds") {
        val json = Json.arr(Json.Num(1), Json.Num(2))
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root(10),
          Json.Num(3)
        ))
        val result = patch.apply(json)
        assertTrue(result.isLeft)
      },
      test("type mismatch - add to array as object") {
        val json = Json.arr(Json.Num(1), Json.Num(2))
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root / "field",
          Json.Num(3)
        ))
        val result = patch.apply(json)
        assertTrue(result.isLeft)
      }
    ),
    
    suite("Edge cases")(
      test("empty patch") {
        val json = Json.obj("a" -> Json.Num(1))
        val patch = JsonPatch.empty
        val result = patch.apply(json)
        assertTrue(result == Right(json))
      },
      test("replace root") {
        val json = Json.obj("old" -> Json.Str("value"))
        val patch = JsonPatch.single(JsonPatch.Operation.Replace(
          JsonPath.root,
          Json.Num(42)
        ))
        val result = patch.apply(json)
        assertTrue(result == Right(Json.Num(42)))
      },
      test("add to empty object") {
        val json = Json.obj()
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root / "first",
          Json.Str("value")
        ))
        val result = patch.apply(json)
        val expected = Json.obj("first" -> Json.Str("value"))
        assertTrue(result == Right(expected))
      },
      test("add to empty array") {
        val json = Json.arr()
        val patch = JsonPatch.single(JsonPatch.Operation.Add(
          JsonPath.root(0),
          Json.Str("first")
        ))
        val result = patch.apply(json)
        val expected = Json.arr(Json.Str("first"))
        assertTrue(result == Right(expected))
      }
    )
  )
}
