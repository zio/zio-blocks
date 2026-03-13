package golem.runtime.rpc

import org.scalatest.concurrent.TimeLimits
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.SpanSugar._
import golem.runtime.rpc.{Color, Labels, Point}

final class RpcValueCodecSpec extends AnyFunSuite with TimeLimits {
  test("encodeArgs/decodeValue roundtrip for product") {
    failAfter(30.seconds) {
      val in = Point(1, 2)
      info("rpc encodeArgs product")
      val params = RpcValueCodec.encodeArgs(in).fold(err => fail(err), identity)
      info(s"rpc params product: $params")
      val decoded = RpcValueCodec.decodeValue[Point](params(0)).fold(err => fail(err), identity)
      assert(decoded == in)
    }
  }

  test("encodeArgs/decodeValue roundtrip for map") {
    failAfter(30.seconds) {
      val in = Labels(Map("a" -> 1, "b" -> 2))
      info("rpc encodeArgs map")
      val params = RpcValueCodec.encodeArgs(in).fold(err => fail(err), identity)
      info(s"rpc params map: $params")
      val decoded = RpcValueCodec.decodeValue[Labels](params(0)).fold(err => fail(err), identity)
      assert(decoded == in)
    }
  }

  test("encodeArgs/decodeValue roundtrip for enum") {
    failAfter(30.seconds) {
      val in: Color = Color.Blue
      info("rpc encodeArgs enum")
      val params = RpcValueCodec.encodeArgs(in).fold(err => fail(err), identity)
      info(s"rpc params enum: $params")
      val decoded = RpcValueCodec.decodeValue[Color](params(0)).fold(err => fail(err), identity)
      assert(decoded == in)
    }
  }
}
