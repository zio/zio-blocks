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
import scala.compiletime.testing.typeCheckErrors

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
    ),
    suite("endpoints macro M2 auto-naming")(
      test("bare Endpoint(Method.GET / \"health\") auto-names to `GET /health`") {
        val group = endpoints {
          Endpoint(Method.GET / "health")
        }
        assertTrue(group.`GET /health`.route.render == "GET /health")
      },
      test("path var Endpoint(Method.GET / \"user\" / PathCodec.int(\"userId\")) names `GET /user/{userId}`") {
        val group = endpoints {
          Endpoint(Method.GET / "user" / PathCodec.int("userId"))
        }
        assertTrue(group.`GET /user/{userId}`.route.render == "GET /user/{userId}")
      },
      test("multi-method Endpoint(Method.GET #| Method.POST / \"orders\") names `GET#|POST /orders`") {
        val group = endpoints {
          Endpoint(Method.GET #| Method.POST / "orders")
        }
        assertTrue(group.`GET#|POST /orders`.route.render == "GET#|POST /orders")
      },
      test("same path different method coexist: GET /users and POST /users") {
        val group = endpoints {
          Endpoint(Method.GET / "users")
          Endpoint(Method.POST / "users")
        }
        assertTrue(
          group.`GET /users`.route.render == "GET /users",
          group.`POST /users`.route.render == "POST /users"
        )
      },
      test("mixed val-named + bare auto-named in one block") {
        val group = endpoints {
          val mine = Endpoint(Method.GET / "mine")
          Endpoint(Method.GET / "auto")
        }
        assertTrue(
          group.mine.route.render == "GET /mine",
          group.`GET /auto`.route.render == "GET /auto"
        )
      },
      test("collision on identical bare endpoints reports error") {
        val errors = typeCheckErrors(
          "endpoints { Endpoint(Method.GET / \"dup\"); Endpoint(Method.GET / \"dup\") }"
        )
        assertTrue(errors.nonEmpty)
      }
    )
  )
}
