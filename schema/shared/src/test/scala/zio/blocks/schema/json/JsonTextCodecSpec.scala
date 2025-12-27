package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import java.nio.CharBuffer

object JsonTextCodecSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("JsonTextCodecSpec")(
    test("reject trailing characters") {
      val codec = JsonTextCodec(Schema[String])
      val input = CharBuffer.wrap("\"hello\"world")
      assert(codec.decode(input))(isLeft)
    },
    test("valid string") {
      val codec = JsonTextCodec(Schema[String])
      val input = CharBuffer.wrap("\"hello\"")
      assert(codec.decode(input))(isRight(equalTo("hello")))
    },
    test("case object") {
      case object Foo derives Schema
      val codec = JsonTextCodec(Schema[Foo.type])
      val input = CharBuffer.wrap("null")
      assert(codec.decode(input))(isRight(equalTo(Foo)))
    },
    test("case object with trailing") {
      case object Foo derives Schema
      val codec = JsonTextCodec(Schema[Foo.type])
      val input = CharBuffer.wrap("null}")
      assert(codec.decode(input))(isLeft)
    },
    test("case class") {
      final case class Bar(a: Int) derives Schema
      val codec = JsonTextCodec(Schema[Bar])
      val input = CharBuffer.wrap("{\"a\":1}")
      assert(codec.decode(input))(isRight(equalTo(Bar(1))))
    },
    test("case class with trailing") {
      final case class Bar(a: Int) derives Schema
      val codec = JsonTextCodec(Schema[Bar])
      val input = CharBuffer.wrap("{\"a\":1}}")
      assert(codec.decode(input))(isLeft)
    }
  )
}