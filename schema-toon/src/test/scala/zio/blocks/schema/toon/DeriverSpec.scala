package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

object DeriverSpec extends ZIOSpecDefault {
  def spec = suite("Deriver")(
    test("derive primitive string codec") {
      val schema                         = Schema[String]
      val codec: ToonBinaryCodec[String] = schema.derive(ToonFormat.deriver)
      assertTrue(codec.encodeToString("hello") == "hello")
    },
    test("derive primitive int codec") {
      val schema                      = Schema[Int]
      val codec: ToonBinaryCodec[Int] = schema.derive(ToonFormat.deriver)
      assertTrue(codec.encodeToString(42) == "42")
    }
  )
}
