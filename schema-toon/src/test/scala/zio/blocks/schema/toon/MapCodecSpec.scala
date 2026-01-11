package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

object MapCodecSpec extends ZIOSpecDefault {
  def spec = suite("MapCodec")(
    test("string-keyed map") {
      val schema                                   = Schema[Map[String, Int]]
      val codec: ToonBinaryCodec[Map[String, Int]] = schema.derive(ToonFormat.deriver)
      val map                                      = Map("a" -> 1, "b" -> 2)
      val encoded                                  = codec.encodeToString(map)
      // Map ordering may vary, so check contains
      assertTrue(encoded.contains("a: 1") && encoded.contains("b: 2"))
    },
    test("empty map") {
      val schema                                   = Schema[Map[String, Int]]
      val codec: ToonBinaryCodec[Map[String, Int]] = schema.derive(ToonFormat.deriver)
      val encoded                                  = codec.encodeToString(Map.empty)
      assertTrue(encoded == "")
    }
  )
}
