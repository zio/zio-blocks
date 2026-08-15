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

package zio.blocks.endpoint

import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
import zio.test.*
import scala.language.implicitConversions

object EndpointGroupSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("EndpointGroupSpec")(
    suite("endpoints macro M1")(
      test("two-val block returns NamedTuple with static .a .b access") {
        val group = endpoints {
          val a = Endpoint(Method.GET / "a")
          val b = Endpoint(Method.GET / "b")
        }
        assertTrue(
          group.a.route.render == "GET /a",
          group.b.route.render == "GET /b"
        )
      },
      test("empty block returns NamedTuple[EmptyTuple, EmptyTuple]") {
        val group = endpoints {}
        assertTrue(group.isInstanceOf[scala.NamedTuple.NamedTuple[EmptyTuple, EmptyTuple]])
      },
      test("single val block returns 1-member NamedTuple") {
        val group = endpoints {
          val only = Endpoint(Method.GET / "only")
        }
        assertTrue(group.only.route.render == "GET /only")
      }
    )
  )
}
