package zio.blocks.schema.json

import zio.test._
import zio.test.Assertion._

object JsonToStringSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonToStringSpec")(
    test("Json.toString") {
      val json = Json.obj("key" -> Json.str("value"), "arr" -> Json.arr(Json.number(1), Json.bool(true)))
      // Exact output depends on JsonCodec implementation (spacing, etc).
      // Assuming it produces standard compact JSON.
      assert(json.toString)(equalTo("""{"key":"value","arr":[1,true]}"""))
    }
  )
}
