package zio.blocks.schema.toon

import zio.test._

object StringCodecSpec extends ZIOSpecDefault {
  def spec = suite("StringCodec")(
    test("simple unquoted string") {
      val codec  = ToonBinaryCodec.stringCodec
      val result = codec.encodeToString("hello")
      assertTrue(result == "hello")
    },
    test("quoted string with comma") {
      val codec  = ToonBinaryCodec.stringCodec
      val result = codec.encodeToString("hello, world")
      assertTrue(result == "\"hello, world\"")
    },
    test("quoted string with space") {
      val codec  = ToonBinaryCodec.stringCodec
      val result = codec.encodeToString("hello world")
      assertTrue(result == "\"hello world\"")
    },
    test("quoted string with colon") {
      val codec  = ToonBinaryCodec.stringCodec
      val result = codec.encodeToString("key:value")
      assertTrue(result == "\"key:value\"")
    },
    test("empty string is quoted") {
      val codec  = ToonBinaryCodec.stringCodec
      val result = codec.encodeToString("")
      assertTrue(result == "\"\"")
    }
  )
}
