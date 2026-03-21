package golem.runtime.rpc

import golem.host.js._
import org.scalatest.concurrent.TimeLimits
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.SpanSugar._
import golem.runtime.rpc.{Color, Labels, Point}

final class RpcValueCodecSpec extends AnyFunSuite with TimeLimits {
  test("encodeArgs/decodeValue roundtrip for product") {
    failAfter(30.seconds) {
      val in = Point(1, 2)
      info("rpc encodeArgs product")
      val dataValue = RpcValueCodec.encodeArgs(in).fold(err => fail(err), identity)
      info(s"rpc params product: $dataValue")
      val witValue = dataValue.asInstanceOf[JsDataValueTuple].value(0).asInstanceOf[JsElementValueComponentModel].value
      val decoded = RpcValueCodec.decodeValue[Point](witValue).fold(err => fail(err), identity)
      assert(decoded == in)
    }
  }

  test("encodeArgs/decodeValue roundtrip for map") {
    failAfter(30.seconds) {
      val in = Labels(Map("a" -> 1, "b" -> 2))
      info("rpc encodeArgs map")
      val dataValue = RpcValueCodec.encodeArgs(in).fold(err => fail(err), identity)
      info(s"rpc params map: $dataValue")
      val witValue = dataValue.asInstanceOf[JsDataValueTuple].value(0).asInstanceOf[JsElementValueComponentModel].value
      val decoded = RpcValueCodec.decodeValue[Labels](witValue).fold(err => fail(err), identity)
      assert(decoded == in)
    }
  }

  test("encodeArgs/decodeValue roundtrip for enum") {
    failAfter(30.seconds) {
      val in: Color = Color.Blue
      info("rpc encodeArgs enum")
      val dataValue = RpcValueCodec.encodeArgs(in).fold(err => fail(err), identity)
      info(s"rpc params enum: $dataValue")
      val witValue = dataValue.asInstanceOf[JsDataValueTuple].value(0).asInstanceOf[JsElementValueComponentModel].value
      val decoded = RpcValueCodec.decodeValue[Color](witValue).fold(err => fail(err), identity)
      assert(decoded == in)
    }
  }
}
