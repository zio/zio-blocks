package zio.blocks.schema.toon

import zio.test._

object PrimitiveCodecSpec extends ZIOSpecDefault {
  def spec = suite("PrimitiveCodec")(
    test("boolean codec - true") {
      val codec = ToonBinaryCodec.booleanCodec
      assertTrue(codec.encodeToString(true) == "true")
    },
    test("boolean codec - false") {
      val codec = ToonBinaryCodec.booleanCodec
      assertTrue(codec.encodeToString(false) == "false")
    },
    test("unit codec - writes nothing") {
      val codec = ToonBinaryCodec.unitCodec
      assertTrue(codec.encodeToString(()) == "")
    }
  )
}
