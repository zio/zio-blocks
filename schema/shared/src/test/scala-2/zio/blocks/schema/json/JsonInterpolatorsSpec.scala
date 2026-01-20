package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec}
import zio.test._

object JsonInterpolatorsSpec extends SchemaBaseSpec {
  import JsonInterpolators._

  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorsSpec")(
    suite("json interpolator")(
      test("parses simple object") {
        val j = json"""{"name": "Alice", "age": 30}"""
        assertTrue(
          j.get("name").string == Right("Alice"),
          j.get("age").int == Right(30)
        )
      },
      test("parses nested object") {
        val j = json"""{"user": {"name": "Bob", "active": true}}"""
        assertTrue(
          j.get("user").get("name").string == Right("Bob"),
          j.get("user").get("active").boolean == Right(true)
        )
      },
      test("parses array") {
        val j = json"""[1, 2, 3]"""
        assertTrue(
          j.isArray,
          j.elements.length == 3
        )
      },
      test("parses primitives") {
        val jsonStr = json""""hello""""
        val jsonNum = json"""42"""
        val jsonBool = json"""true"""
        val jsonNull = json"""null"""
        assertTrue(
          jsonStr == Json.str("hello"),
          jsonNum == Json.number(42),
          jsonBool == Json.bool(true),
          jsonNull == Json.Null
        )
      },
      test("supports interpolated string values") {
        val name = "Charlie"
        val j = json"""{"name": $name}"""
        assertTrue(j.get("name").string == Right("Charlie"))
      },
      test("supports interpolated numeric values") {
        val age = 25
        val j = json"""{"age": $age}"""
        assertTrue(j.get("age").int == Right(25))
      },
      test("supports interpolated boolean values") {
        val active = true
        val j = json"""{"active": $active}"""
        assertTrue(j.get("active").boolean == Right(true))
      },
      test("supports interpolated Json values") {
        val inner = Json.obj("x" -> Json.number(1))
        val j = json"""{"inner": $inner}"""
        assertTrue(j.get("inner").get("x").int == Right(1))
      }
    ),
    suite("p interpolator")(
      test("parses simple field access") {
        val path = p".name"
        assertTrue(path == DynamicOptic.root.field("name"))
      },
      test("parses nested field access") {
        val path = p".user.name"
        assertTrue(path == DynamicOptic.root.field("user").field("name"))
      },
      test("parses array index access") {
        val path = p"[0]"
        assertTrue(path == DynamicOptic.root.at(0))
      },
      test("parses mixed field and index access") {
        val path = p".users[0].name"
        assertTrue(path == DynamicOptic.root.field("users").at(0).field("name"))
      },
      test("parses bracket notation for fields") {
        val path = p"""["user-name"]"""
        assertTrue(path == DynamicOptic.root.field("user-name"))
      },
      test("parses complex path") {
        val path = p""".data["items"][2].value"""
        assertTrue(path == DynamicOptic.root.field("data").field("items").at(2).field("value"))
      },
      test("works with Json navigation") {
        val j = Json.obj(
          "users" -> Json.arr(
            Json.obj("name" -> Json.str("Alice")),
            Json.obj("name" -> Json.str("Bob"))
          )
        )
        val path = p".users[1].name"
        assertTrue(j.get(path).string == Right("Bob"))
      }
    )
  )
}
