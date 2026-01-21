package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

import zio.blocks.schema.json.JsonInterpolators._

object JsonInterpolatorSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorSpec")(
    test("interpolate simple object") {
      val name = "Alice"
      val age  = 30
      val json = json"""{ "name": $name, "age": $age }"""

      assertTrue(
        json == Json.Object(
          Vector(
            "name" -> Json.String("Alice"),
            "age"  -> Json.Number("30")
          )
        )
      )
    },
    test("interpolate nested object") {
      val nested = json"""{ "foo": "bar" }"""
      val json   = json"""{ "nested": $nested }"""

      assertTrue(
        json == Json.Object(
          Vector(
            "nested" -> Json.Object(Vector("foo" -> Json.String("bar")))
          )
        )
      )
    },
    test("interpolate array") {
      val item1 = 1
      val item2 = 2
      val json  = json"""[ $item1, $item2 ]"""

      assertTrue(
        json == Json.Array(
          Vector(
            Json.Number("1"),
            Json.Number("2")
          )
        )
      )
    },
    test("handle escaping") {
      val text = "Hello \"World\""
      val json = json"""{ "text": $text }"""

      assertTrue(
        json == Json.Object(
          Vector(
            "text" -> Json.String("Hello \"World\"")
          )
        )
      )
    }
  )
}
