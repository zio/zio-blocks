package zio.blocks.schema.json

import zio.test._

/**
 * Tests for RFC 6902 JSON Patch operations (diff and patch).
 */
object JsonPatchSpec extends ZIOSpecDefault {
  def spec = suite("JsonPatchSpec")(
    suite("diff")(
      test("diff returns empty array for identical values") {
        val json   = Json.obj("a" -> Json.number(1), "b" -> Json.str("hello"))
        val result = json.diff(json)
        assertTrue(result == Json.arr())
      },
      test("diff detects added property") {
        val source = Json.obj("a" -> Json.number(1))
        val target = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
        val result = source.diff(target)
        val ops    = result.elements
        assertTrue(ops.exists { op =>
          op.get("op").headOption.flatMap(_.stringValue).contains("add") &&
          op.get("path").headOption.flatMap(_.stringValue).contains("/b")
        })
      },
      test("diff detects removed property") {
        val source = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
        val target = Json.obj("a" -> Json.number(1))
        val result = source.diff(target)
        val ops    = result.elements
        assertTrue(ops.exists { op =>
          op.get("op").headOption.flatMap(_.stringValue).contains("remove") &&
          op.get("path").headOption.flatMap(_.stringValue).contains("/b")
        })
      },
      test("diff detects replaced value") {
        val source = Json.obj("a" -> Json.number(1))
        val target = Json.obj("a" -> Json.number(2))
        val result = source.diff(target)
        val ops    = result.elements
        assertTrue(ops.exists { op =>
          op.get("op").headOption.flatMap(_.stringValue).contains("replace") &&
          op.get("path").headOption.flatMap(_.stringValue).contains("/a")
        })
      },
      test("diff handles nested objects") {
        val source = Json.obj("nested" -> Json.obj("x" -> Json.number(1)))
        val target = Json.obj("nested" -> Json.obj("x" -> Json.number(2)))
        val result = source.diff(target)
        val ops    = result.elements
        assertTrue(ops.exists { op =>
          op.get("path").headOption.flatMap(_.stringValue).contains("/nested/x")
        })
      },
      test("diff handles primitive replacement") {
        val source = Json.str("hello")
        val target = Json.number(42)
        val result = source.diff(target)
        val ops    = result.elements
        assertTrue(ops.exists { op =>
          op.get("op").headOption.flatMap(_.stringValue).contains("replace") &&
          op.get("path").headOption.flatMap(_.stringValue).contains("")
        })
      }
    ),
    suite("patch")(
      test("patch add operation") {
        val json  = Json.obj("a" -> Json.number(1))
        val patch = Json.arr(
          Json.obj(
            "op"    -> Json.str("add"),
            "path"  -> Json.str("/b"),
            "value" -> Json.number(2)
          )
        )
        val result = json.patch(patch)
        assertTrue(result.isRight) &&
        assertTrue(result.exists { j =>
          j.get("b").headOption.flatMap(_.numberValue).contains(BigDecimal(2))
        })
      },
      test("patch remove operation") {
        val json  = Json.obj("a" -> Json.number(1), "b" -> Json.number(2))
        val patch = Json.arr(
          Json.obj(
            "op"   -> Json.str("remove"),
            "path" -> Json.str("/b")
          )
        )
        val result = json.patch(patch)
        assertTrue(result.isRight) &&
        assertTrue(result.exists { j =>
          j.get("b").isEmpty && j.get("a").headOption.flatMap(_.numberValue).contains(BigDecimal(1))
        })
      },
      test("patch replace operation") {
        val json  = Json.obj("a" -> Json.number(1))
        val patch = Json.arr(
          Json.obj(
            "op"    -> Json.str("replace"),
            "path"  -> Json.str("/a"),
            "value" -> Json.number(99)
          )
        )
        val result = json.patch(patch)
        assertTrue(result.isRight) &&
        assertTrue(result.exists { j =>
          j.get("a").headOption.flatMap(_.numberValue).contains(BigDecimal(99))
        })
      },
      test("patch test operation succeeds") {
        val json  = Json.obj("a" -> Json.number(1))
        val patch = Json.arr(
          Json.obj(
            "op"    -> Json.str("test"),
            "path"  -> Json.str("/a"),
            "value" -> Json.number(1)
          )
        )
        val result = json.patch(patch)
        assertTrue(result.isRight)
      },
      test("patch test operation fails on mismatch") {
        val json  = Json.obj("a" -> Json.number(1))
        val patch = Json.arr(
          Json.obj(
            "op"    -> Json.str("test"),
            "path"  -> Json.str("/a"),
            "value" -> Json.number(2)
          )
        )
        val result = json.patch(patch)
        assertTrue(result.isLeft)
      },
      test("patch multiple operations") {
        val json  = Json.obj("a" -> Json.number(1))
        val patch = Json.arr(
          Json.obj("op" -> Json.str("add"), "path"     -> Json.str("/b"), "value" -> Json.number(2)),
          Json.obj("op" -> Json.str("replace"), "path" -> Json.str("/a"), "value" -> Json.number(10))
        )
        val result = json.patch(patch)
        assertTrue(result.isRight) &&
        assertTrue(result.exists { j =>
          j.get("a").headOption.flatMap(_.numberValue).contains(BigDecimal(10)) &&
          j.get("b").headOption.flatMap(_.numberValue).contains(BigDecimal(2))
        })
      },
      test("diff and patch roundtrip") {
        val source = Json.obj("a" -> Json.number(1), "b" -> Json.str("hello"))
        val target = Json.obj("a" -> Json.number(2), "c" -> Json.str("world"))
        val patch  = source.diff(target)
        val result = source.patch(patch)
        assertTrue(result.isRight) &&
        assertTrue(result.exists(_ == target))
      }
    )
  )
}
