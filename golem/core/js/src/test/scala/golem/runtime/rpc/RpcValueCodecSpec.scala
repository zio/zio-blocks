package golem.runtime.rpc

import golem.host.js._
import golem.runtime.rpc.{Color, Labels, Point}
import zio.test._

object RpcValueCodecSpec extends ZIOSpecDefault {
  def spec = suite("RpcValueCodecSpec")(
    test("encodeArgs/decodeValue roundtrip for product") {
      val in        = Point(1, 2)
      val dataValue = RpcValueCodec.encodeArgs(in).fold(err => throw new RuntimeException(err), identity)
      val witValue =
        dataValue.asInstanceOf[JsDataValueTuple].value(0).asInstanceOf[JsElementValueComponentModel].value
      val decoded = RpcValueCodec.decodeValue[Point](witValue).fold(err => throw new RuntimeException(err), identity)
      assertTrue(decoded == in)
    },
    test("encodeArgs/decodeValue roundtrip for map") {
      val in        = Labels(Map("a" -> 1, "b" -> 2))
      val dataValue = RpcValueCodec.encodeArgs(in).fold(err => throw new RuntimeException(err), identity)
      val witValue =
        dataValue.asInstanceOf[JsDataValueTuple].value(0).asInstanceOf[JsElementValueComponentModel].value
      val decoded = RpcValueCodec.decodeValue[Labels](witValue).fold(err => throw new RuntimeException(err), identity)
      assertTrue(decoded == in)
    },
    test("encodeArgs/decodeValue roundtrip for enum") {
      val in: Color = Color.Blue
      val dataValue = RpcValueCodec.encodeArgs(in).fold(err => throw new RuntimeException(err), identity)
      val witValue =
        dataValue.asInstanceOf[JsDataValueTuple].value(0).asInstanceOf[JsElementValueComponentModel].value
      val decoded = RpcValueCodec.decodeValue[Color](witValue).fold(err => throw new RuntimeException(err), identity)
      assertTrue(decoded == in)
    }
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(30))
}
