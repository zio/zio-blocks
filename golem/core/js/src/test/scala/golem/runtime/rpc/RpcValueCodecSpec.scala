/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
