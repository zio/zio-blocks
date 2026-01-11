package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

object SequenceCodecSpec extends ZIOSpecDefault {
  def spec = suite("SequenceCodec")(
    test("primitive array inline format (Auto mode)") {
      val schema                            = Schema[List[Int]]
      val codec: ToonBinaryCodec[List[Int]] = schema.derive(ToonFormat.deriver)
      val encoded                           = codec.encodeToString(List(1, 2, 3))
      assertTrue(encoded == "[3]: 1,2,3")
    },
    test("string list inline format") {
      val schema                               = Schema[List[String]]
      val codec: ToonBinaryCodec[List[String]] = schema.derive(ToonFormat.deriver)
      val encoded                              = codec.encodeToString(List("a", "b"))
      assertTrue(encoded == "[2]: a,b")
    },
    test("empty list") {
      val schema                            = Schema[List[Int]]
      val codec: ToonBinaryCodec[List[Int]] = schema.derive(ToonFormat.deriver)
      val encoded                           = codec.encodeToString(List())
      assertTrue(encoded == "[0]: ")
    },
    test("explicit inline format") {
      val schema                            = Schema[List[Int]]
      val codec: ToonBinaryCodec[List[Int]] = schema.derive(
        ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Inline)
      )
      val encoded = codec.encodeToString(List(1, 2, 3))
      assertTrue(encoded == "[3]: 1,2,3")
    }
  )
}
