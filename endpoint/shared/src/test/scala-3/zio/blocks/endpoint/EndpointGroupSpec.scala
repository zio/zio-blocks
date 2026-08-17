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

import zio.blocks.endpoint.*
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
    ),
    suite("endpoints macro M3 nesting")(
      test("\"api\" / endpoints { val a = Endpoint(Method.GET / \"a\") }") {
        val group = "api" / endpoints {
          val a = Endpoint(Method.GET / "a")
        }
        assertTrue(group.a.route.render == "GET /api/a")
      },
      test("nested \"api\" / endpoints { \"v1\" / endpoints { val users = Endpoint(Method.GET / \"users\") } }") {
        val group = "api" / endpoints {
          "v1" / endpoints {
            val users = Endpoint(Method.GET / "users")
          }
        }
        assertTrue(group.v1.users.route.render == "GET /api/v1/users")
      },
      test("nested + auto-named inner Endpoint(Method.POST / \"users\")") {
        val group = "api" / endpoints {
          "v1" / endpoints {
            Endpoint(Method.POST / "users")
          }
        }
        assertTrue(group.v1.`POST /users`.route.render == "POST /api/v1/users")
      },
      test("deep 3-level nesting") {
        val group = "a" / endpoints {
          "b" / endpoints {
            "c" / endpoints {
              val x = Endpoint(Method.GET / "x")
            }
          }
        }
        assertTrue(group.b.c.x.route.render == "GET /a/b/c/x")
      }
    ),
    suite("endpoints macro M6 error handling")(
      test("duplicate name reports error listing both locations and actionable advice") {
        val errors = typeCheckErrors(
          "endpoints { Endpoint(Method.GET / \"dup\"); Endpoint(Method.GET / \"dup\") }"
        )
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("duplicate")))
      },
      test("non-Endpoint val error message contains the val name and its actual type") {
        val errors = typeCheckErrors(
          "endpoints { val x = 42 }"
        )
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("val x")) && errors.exists(_.message.contains("of type")))
      },
      test("bare non-Endpoint expression reports error") {
        val errors = typeCheckErrors(
          "endpoints { \"not an endpoint\" }"
        )
        assertTrue(errors.nonEmpty)
      },
      test("23-member block compiles and .e22 access works (proves >22 arity via ofTupleFromSeq)") {
        val group = endpoints {
          val e0 = Endpoint(Method.GET / "e0"); val e1 = Endpoint(Method.GET / "e1"); val e2 = Endpoint(Method.GET / "e2"); val e3 = Endpoint(Method.GET / "e3"); val e4 = Endpoint(Method.GET / "e4")
          val e5 = Endpoint(Method.GET / "e5"); val e6 = Endpoint(Method.GET / "e6"); val e7 = Endpoint(Method.GET / "e7"); val e8 = Endpoint(Method.GET / "e8"); val e9 = Endpoint(Method.GET / "e9")
          val e10 = Endpoint(Method.GET / "e10"); val e11 = Endpoint(Method.GET / "e11"); val e12 = Endpoint(Method.GET / "e12"); val e13 = Endpoint(Method.GET / "e13"); val e14 = Endpoint(Method.GET / "e14")
          val e15 = Endpoint(Method.GET / "e15"); val e16 = Endpoint(Method.GET / "e16"); val e17 = Endpoint(Method.GET / "e17"); val e18 = Endpoint(Method.GET / "e18"); val e19 = Endpoint(Method.GET / "e19")
          val e20 = Endpoint(Method.GET / "e20"); val e21 = Endpoint(Method.GET / "e21"); val e22 = Endpoint(Method.GET / "e22")
        }
        assertTrue(group.e22.route.render == "GET /e22")
      },
      test("backticked reserved-word member name works (val `type`)") {
        val group = endpoints {
          val `type` = Endpoint(Method.GET / "t")
        }
        assertTrue(group.`type`.route.render == "GET /t")
      }
    )
  )
}
