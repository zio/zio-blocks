package zio.blocks.schema.tostring

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.{Json, JsonPatch}

object JsonPatchToStringSpec extends ZIOSpecDefault {

  def spec = suite("JsonPatch toString")(
    test("renders empty patch") {
      val patch = JsonPatch.empty
      assertTrue(patch.toString == "JsonPatch {}")
    },
    test("renders simple set operation at root") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("hello")))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root = "hello"
            |}""".stripMargin
      )
    },
    test("renders number delta (positive)") {
      val patch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root += 5
            |}""".stripMargin
      )
    },
    test("renders number delta (negative)") {
      val patch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-5))))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root -= 5
            |}""".stripMargin
      )
    },
    test("renders string edit insert") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "Hello")))
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    + [0: "Hello"]
            |}""".stripMargin
      )
    },
    test("renders string edit delete") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(0, 5)))
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    - [0, 5]
            |}""".stripMargin
      )
    },
    test("renders string edit append") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append(" world")))
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    + " world"
            |}""".stripMargin
      )
    },
    test("renders string edit modify") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(0, 5, "Hi")))
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    ~ [0, 5: "Hi"]
            |}""".stripMargin
      )
    },
    test("renders array append") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(Json.Number("1"), Json.Number("2")))))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    + 1
            |    + 2
            |}""".stripMargin
      )
    },
    test("renders array insert") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(1, Chunk(Json.Number("42"), Json.Number("43")))))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    + [1: 42]
            |    + [2: 43]
            |}""".stripMargin
      )
    },
    test("renders array delete single element") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 1)))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    - [0]
            |}""".stripMargin
      )
    },
    test("renders array delete multiple elements") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 3)))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    - [0, 1, 2]
            |}""".stripMargin
      )
    },
    test("renders array modify with set") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.Set(Json.Number("99")))))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    ~ [0: 99]
            |}""".stripMargin
      )
    },
    test("renders object add") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("name", Json.String("Alice"))))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    + {"name": "Alice"}
            |}""".stripMargin
      )
    },
    test("renders object remove") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("name")))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    - {"name"}
            |}""".stripMargin
      )
    },
    test("renders object modify") {
      val nestedPatch = JsonPatch.root(JsonPatch.Op.Set(Json.String("New York")))
      val patch       = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("city", nestedPatch)))
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    ~ {"city"}:
            |      root = "New York"
            |}""".stripMargin
      )
    },
    test("renders composite patch") {
      val patch = JsonPatch(
        Vector(
          JsonPatch.JsonPatchOp(
            DynamicOptic.root,
            JsonPatch.Op.ObjectEdit(
              Vector(
                JsonPatch.ObjectOp.Add("name", Json.String("John")),
                JsonPatch.ObjectOp.Remove("temp")
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    + {"name": "John"}
            |    - {"temp"}
            |}""".stripMargin
      )
    },
    test("renders diff-generated patch for number change") {
      val source = Json.Number("10")
      val target = Json.Number("15")
      val patch  = JsonPatch.diff(source, target)
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root += 5
            |}""".stripMargin
      )
    },
    test("renders diff-generated patch for object field changes") {
      val source = Json.Object(Chunk("name" -> Json.String("Alice"), "age" -> Json.Number("30")))
      val target = Json.Object(Chunk("name" -> Json.String("Bob"), "age" -> Json.Number("30")))
      val patch  = JsonPatch.diff(source, target)
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    ~ {"name"}:
            |      root = "Bob"
            |}""".stripMargin
      )
    },
    test("renders diff-generated patch for array changes") {
      val source = Json.Array(Chunk(Json.Number("1"), Json.Number("2"), Json.Number("3")))
      val target = Json.Array(Chunk(Json.Number("1"), Json.Number("2"), Json.Number("3"), Json.Number("4")))
      val patch  = JsonPatch.diff(source, target)
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    + 4
            |}""".stripMargin
      )
    },
    test("renders special characters in strings") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("hello\nworld\t\"quoted\"")))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root = "hello\nworld\t\"quoted\""
            |}""".stripMargin
      )
    },
    test("renders nested operation") {
      val innerPatch = JsonPatch.root(JsonPatch.Op.Set(Json.Number("42")))
      val patch      = JsonPatch.root(JsonPatch.Op.Nested(innerPatch))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root:
            |    root = 42
            |}""".stripMargin
      )
    },
    test("renders JsonPatch.empty correctly") {
      assertTrue(JsonPatch.empty.toString == "JsonPatch {}")
    },
    test("renders path with field and index") {
      val patch = JsonPatch(
        Vector(
          JsonPatch.JsonPatchOp(
            DynamicOptic.root.field("items").at(0),
            JsonPatch.Op.Set(Json.Number("1"))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  .items[0] = 1
            |}""".stripMargin
      )
    },
    test("renders path with multiple fields") {
      val patch = JsonPatch(
        Vector(
          JsonPatch.JsonPatchOp(
            DynamicOptic.root.field("user").field("address").field("city"),
            JsonPatch.Op.Set(Json.String("NYC"))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  .user.address.city = "NYC"
            |}""".stripMargin
      )
    },
    test("renders path with nested indices") {
      val patch = JsonPatch(
        Vector(
          JsonPatch.JsonPatchOp(
            DynamicOptic.root.at(0).at(1),
            JsonPatch.Op.Set(Json.Number("42"))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  [0][1] = 42
            |}""".stripMargin
      )
    },
    test("escapes backslash in strings") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("path\\to\\file")))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root = "path\\to\\file"
            |}""".stripMargin
      )
    },
    test("escapes backspace character") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("hello\bworld")))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root = "hello\bworld"
            |}""".stripMargin
      )
    },
    test("escapes form feed character") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("hello\fworld")))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root = "hello\fworld"
            |}""".stripMargin
      )
    },
    test("escapes carriage return character") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("hello\rworld")))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root = "hello\rworld"
            |}""".stripMargin
      )
    },
    test("escapes control characters") {
      // ASCII control character (e.g., SOH = 0x01)
      val patch          = JsonPatch.root(JsonPatch.Op.Set(Json.String("hello\u0001world")))
      val escapedControl = "\\u0001"
      assertTrue(
        patch.toString ==
          s"""JsonPatch {
             |  root = "hello${escapedControl}world"
             |}""".stripMargin
      )
    },
    test("escapes multiple special characters in one string") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("a\tb\nc\rd\be\ff")))
      assertTrue(
        patch.toString ==
          """JsonPatch {
            |  root = "a\tb\nc\rd\be\ff"
            |}""".stripMargin
      )
    },
    test("renders array modify with non-Set nested operation") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Vector(
            JsonPatch.ArrayOp.Modify(
              0,
              JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
            )
          )
        )
      )
      assertTrue(
        patch.toString.contains("~ [0]:")
      )
    }
  )
}
