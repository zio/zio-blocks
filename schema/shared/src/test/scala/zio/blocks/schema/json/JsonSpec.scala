package zio.blocks.schema.json

import zio.Chunk
import zio.test._
import zio.test.Assertion._

object JsonSpec extends ZIOSpecDefault {
  def spec = suite("JsonSpec")(
    suite("Json construction")(
      test("obj creates object") {
        val json = Json.obj("name" -> Json.str("test"), "value" -> Json.num(42))
        assertTrue(json.isObject)
      },
      test("arr creates array") {
        val json = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        assertTrue(json.isArray)
      },
      test("null is null") {
        assertTrue(Json.`null`.isNull)
      },
      test("true is boolean") {
        assertTrue(Json.`true`.isBoolean && Json.`true`.asBoolean.contains(true))
      },
      test("false is boolean") {
        assertTrue(Json.`false`.isBoolean && Json.`false`.asBoolean.contains(false))
      }
    ),
    suite("Json access")(
      test("apply with key returns field value") {
        val json = Json.obj("name" -> Json.str("test"))
        assertTrue(json("name").contains(Json.str("test")))
      },
      test("apply with missing key returns None") {
        val json = Json.obj("name" -> Json.str("test"))
        assertTrue(json("missing").isEmpty)
      },
      test("apply with index returns element") {
        val json = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        assertTrue(json(1).contains(Json.num(2)))
      },
      test("apply with out of bounds index returns None") {
        val json = Json.arr(Json.num(1))
        assertTrue(json(5).isEmpty)
      }
    ),
    suite("Json transformations")(
      test("dropNulls removes null values") {
        val json = Json.obj("a" -> Json.str("value"), "b" -> Json.Null)
        val result = json.dropNulls
        assertTrue(result.asObject.exists(_.length == 1))
      },
      test("sortKeys sorts object keys") {
        val json = Json.obj("z" -> Json.num(1), "a" -> Json.num(2))
        val result = json.sortKeys
        val keys = result.asObject.map(_.map(_._1).toList)
        assertTrue(keys.contains(List("a", "z")))
      },
      test("merge combines objects") {
        val json1 = Json.obj("a" -> Json.num(1))
        val json2 = Json.obj("b" -> Json.num(2))
        val merged = json1.merge(json2)
        assertTrue(merged.asObject.exists(_.length == 2))
      }
    ),
    suite("JsonDecoder")(
      test("decodes string") {
        val result = JsonDecoder[String].decode(Json.str("hello"))
        assertTrue(result == Right("hello"))
      },
      test("decodes int") {
        val result = JsonDecoder[Int].decode(Json.num(42))
        assertTrue(result == Right(42))
      },
      test("decodes list") {
        val json = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        val result = JsonDecoder[List[Int]].decode(json)
        assertTrue(result == Right(List(1, 2, 3)))
      },
      test("returns error for type mismatch") {
        val result = JsonDecoder[String].decode(Json.num(42))
        assertTrue(result.isLeft)
      }
    ),
    suite("JsonEncoder")(
      test("encodes string") {
        val result = JsonEncoder[String].encode("hello")
        assertTrue(result == Json.str("hello"))
      },
      test("encodes int") {
        val result = JsonEncoder[Int].encode(42)
        assertTrue(result.asNumber.exists(_.intValue == 42))
      },
      test("encodes list") {
        val result = JsonEncoder[List[Int]].encode(List(1, 2, 3))
        assertTrue(result.isArray && result.asArray.exists(_.length == 3))
      },
      test("encodes map") {
        val result = JsonEncoder[Map[String, Int]].encode(Map("a" -> 1))
        assertTrue(result.isObject && result("a").exists(_.asNumber.exists(_.intValue == 1)))
      }
    )
  )
}
