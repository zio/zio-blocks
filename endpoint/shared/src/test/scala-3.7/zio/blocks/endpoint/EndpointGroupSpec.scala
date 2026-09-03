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
import zio.http.Path
import zio.test.*
import scala.annotation.nowarn
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
        assertTrue(group == NamedTuple(EmptyTuple))
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
      test("trailing segment Endpoint(Method.GET / PathCodec.trailing) names `GET /...`") {
        val group = endpoints {
          Endpoint(Method.GET / PathCodec.trailing)
        }
        assertTrue(group.`GET /...`.route.render == "GET /...")
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
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
val _ = endpoints { Endpoint(Method.GET / "dup"); Endpoint(Method.GET / "dup") }
""")
        assertTrue(errors.exists(_.message.contains("duplicate")) && errors.exists(_.message.contains(":")))
      },
      test("builder chains still auto-name through in/out") {
        import zio.blocks.schema.Schema
        val group = endpoints(Endpoint(Method.GET / "x").in(Schema.int).out(Schema.string))
        assertTrue(group.`GET /x`.route.render == "GET /x")
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
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
val _ = endpoints { Endpoint(Method.GET / "dup"); Endpoint(Method.GET / "dup") }
""")
        assertTrue(
          errors.nonEmpty &&
            errors.exists(_.message.contains("duplicate")) &&
            errors.exists(_.message.contains(":")) &&
            errors.exists(e => e.message.contains("rename") || e.message.contains("val"))
        )
      },
      test("non-Endpoint val error message contains the val name and its actual type") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
val _ = endpoints { val x = 42 }
""")
        assertTrue(
          errors.nonEmpty &&
            errors.exists(_.message.contains("only accepts")) &&
            errors.exists(_.message.contains("val x")) &&
            errors.exists(_.message.contains("of type"))
        )
      },
      test("bare non-Endpoint expression reports error") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
val _ = endpoints { "not an endpoint" }
""")
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("only accepts")))
      },
      test("23-member block compiles and .e22 access works (proves >22 arity via ofTupleFromSeq)") {
        val group = endpoints {
          val e0  = Endpoint(Method.GET / "e0"); val e1   = Endpoint(Method.GET / "e1");
          val e2  = Endpoint(Method.GET / "e2"); val e3   = Endpoint(Method.GET / "e3");
          val e4  = Endpoint(Method.GET / "e4")
          val e5  = Endpoint(Method.GET / "e5"); val e6   = Endpoint(Method.GET / "e6");
          val e7  = Endpoint(Method.GET / "e7"); val e8   = Endpoint(Method.GET / "e8");
          val e9  = Endpoint(Method.GET / "e9")
          val e10 = Endpoint(Method.GET / "e10"); val e11 = Endpoint(Method.GET / "e11");
          val e12 = Endpoint(Method.GET / "e12"); val e13 = Endpoint(Method.GET / "e13");
          val e14 = Endpoint(Method.GET / "e14")
          val e15 = Endpoint(Method.GET / "e15"); val e16 = Endpoint(Method.GET / "e16");
          val e17 = Endpoint(Method.GET / "e17"); val e18 = Endpoint(Method.GET / "e18");
          val e19 = Endpoint(Method.GET / "e19")
          val e20 = Endpoint(Method.GET / "e20"); val e21 = Endpoint(Method.GET / "e21");
          val e22 = Endpoint(Method.GET / "e22")
        }
        assertTrue(group.e22.route.render == "GET /e22")
      },
      test("backticked reserved-word member name works (val `type`)") {
        val group = endpoints {
          val `type` = Endpoint(Method.GET / "t")
        }
        assertTrue(group.`type`.route.render == "GET /t")
      }
    ),
    suite("endpoints macro M4 path-variable prefixes")(
      test("int(\"id\") / endpoints { val get = Endpoint(Method.GET / \"orders\") }") {
        val byId = PathCodec.int("id") / endpoints { val get = Endpoint(Method.GET / "orders") }
        assertTrue(byId.get.route.render == "GET /{id}/orders")
      },
      test("bare Endpoint under path-var prefix") {
        val byId2 = PathCodec.int("id") / endpoints(Endpoint(Method.GET / "orders"))
        assertTrue(byId2.`GET /orders`.route.render == "GET /{id}/orders")
      },
      test("child endpoint with own var composes PathVars and decode order") {
        val group = PathCodec.int("id") / endpoints {
          val o = Endpoint(Method.GET / "orders" / PathCodec.int("orderId"))
        }
        assertTrue(group.o.route.render == "GET /{id}/orders/{orderId}")
        val rp: RoutePattern[(Int, Int)] = group.o.route
        val ok1                          = rp.decode(Method.GET, Path("/1/orders/2"))
        val ok2                          = rp.decode(Method.GET, Path("/2/orders/1"))
        assertTrue(ok1 == Right((1, 2)) && ok2 == Right((2, 1)))
      },
      test("path-var prefix preserves static PathInput type") {
        val byId                 = PathCodec.int("id") / endpoints { val get = Endpoint(Method.GET / "orders") }
        val get                  = byId.get
        val _: RoutePattern[Int] = get.route // compile-time: PathInput must be Int
        assertTrue(byId.get.route.render == "GET /{id}/orders")
      }
    ),
    suite("endpoints macro mixed nesting")(
      test("constant prefix containing capturing prefix") {
        val group = "api" / endpoints {
          PathCodec.int("id") / endpoints {
            val users = Endpoint(Method.GET / "users")
          }
        }
        assertTrue(group.id.users.route.render == "GET /api/{id}/users")
      },
      test("capturing prefix containing constant prefix") {
        val group = PathCodec.int("id") / endpoints {
          "v1" / endpoints {
            val items = Endpoint(Method.GET / "items")
          }
        }
        assertTrue(group.v1.items.route.render == "GET /{id}/v1/items")
      },
      test("mixed nesting with multiple leaves") {
        val group = "api" / endpoints {
          PathCodec.int("id") / endpoints {
            val users  = Endpoint(Method.GET / "users")
            val orders = Endpoint(Method.POST / "orders")
          }
        }
        assertTrue(
          group.id.users.route.render == "GET /api/{id}/users",
          group.id.orders.route.render == "POST /api/{id}/orders"
        )
      },
      test("3-level mixed nesting") {
        val group = "api" / endpoints {
          PathCodec.int("id") / endpoints {
            "v1" / endpoints {
              val items = Endpoint(Method.GET / "items")
            }
          }
        }
        assertTrue(group.id.v1.items.route.render == "GET /api/{id}/v1/items")
      }
    ),
    suite("endpoints macro focused auto-naming")(
      test("~ composition SegmentCodec.literal(\"v\") ~ SegmentCodec.int(\"major\") via PathCodec") {
        val group = endpoints {
          val v = Endpoint(Method.GET / PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("major")))
        }
        assertTrue(group.v.route.render == "GET /v{major}")
      },
      test(".unused renders as {name}") {
        val group = endpoints {
          val a = Endpoint(Method.GET / PathCodec.int("id").unused)
        }
        assertTrue(group.a.route.render == "GET /{id}")
      },
      test("ANY method renders *") {
        val group = endpoints {
          Endpoint(Method.ANY / "any")
        }
        assertTrue(group.`* /any`.route.render == "* /any")
      },
      test("non-Int codecs: string, bool, long, uuid auto-name correctly") {
        val group = endpoints {
          Endpoint(Method.GET / PathCodec.string("s"))
          Endpoint(Method.GET / PathCodec.bool("b"))
          Endpoint(Method.GET / PathCodec.long("l"))
          Endpoint(Method.GET / PathCodec.uuid("u"))
        }
        assertTrue(
          group.`GET /{s}`.route.render == "GET /{s}",
          group.`GET /{b}`.route.render == "GET /{b}",
          group.`GET /{l}`.route.render == "GET /{l}",
          group.`GET /{u}`.route.render == "GET /{u}"
        )
      },
      test("non-Int codecs preserve types and decode") {
        val gStr  = endpoints { val a = Endpoint(Method.GET / PathCodec.string("s")) }
        val gBool = endpoints { val a = Endpoint(Method.GET / PathCodec.bool("b")) }
        val gLong = endpoints { val a = Endpoint(Method.GET / PathCodec.long("l")) }
        val gUuid = endpoints { val a = Endpoint(Method.GET / PathCodec.uuid("u")) }
        val uuid  = java.util.UUID.randomUUID()
        assertTrue(
          gStr.a.route.decode(Method.GET, Path("/hello")) == Right("hello"),
          gBool.a.route.decode(Method.GET, Path("/true")) == Right(true),
          gLong.a.route.decode(Method.GET, Path("/123")) == Right(123L),
          gUuid.a.route.decode(Method.GET, Path(s"/$uuid")) == Right(uuid)
        )
      }
    ),
    suite("endpoints macro single-eval")(
      test("endpoint expression evaluated exactly once - direct") {
        var c                                                          = 0
        def mk(): Endpoint[Unit, Unit, Unit, Unit, AuthType.None.type] = { c += 1; Endpoint(Method.GET / "once") }
        val group                                                      = endpoints { val a = mk() }
        assertTrue(c == 1) && assertTrue(group.a.route.render == "GET /once")
      },
      test("endpoint expression evaluated exactly once - with prefix") {
        var c                                                          = 0
        def mk(): Endpoint[Unit, Unit, Unit, Unit, AuthType.None.type] = { c += 1; Endpoint(Method.GET / "once") }
        val group                                                      = PathCodec.literal("api") / endpoints { val a = mk() }
        assertTrue(c == 1) && assertTrue(group.a.route.render == "GET /api/once")
      },
      test("string prefix endpoint evaluated once") {
        var c                                                          = 0
        def mk(): Endpoint[Unit, Unit, Unit, Unit, AuthType.None.type] = { c += 1; Endpoint(Method.GET / "s") }
        val group                                                      = "api" / endpoints { val a = mk() }
        assertTrue(c == 1) && assertTrue(group.a.route.render == "GET /api/s")
      }
    ),
    suite("endpoints macro prefix hoist - sibling subgroups")(
      test("effectful prefix evaluated once across sibling subgroups (global hoist)") {
        var outerCount = 0
        var c1         = 0
        var c2         = 0
        val group      = ({ outerCount += 1; PathCodec.literal("api") }) / endpoints {
          ({ c1 += 1; PathCodec.literal("v1") }) / endpoints { val a = Endpoint(Method.GET / "a") }
          ({ c2 += 1; PathCodec.literal("v2") }) / endpoints { val b = Endpoint(Method.GET / "b") }
        }
        assertTrue(outerCount == 1, c1 == 1, c2 == 1) &&
        assertTrue(group.v1.a.route.render == "GET /api/v1/a") &&
        assertTrue(group.v2.b.route.render == "GET /api/v2/b")
      }
    ),
    suite("endpoints macro external refs")(
      test("bare external ref val x = Endpoint(GET / outside); endpoints { x } -> member .x") {
        val x = Endpoint(Method.GET / "outside")
        val g = endpoints { x }
        assertTrue(g.x.route.render == "GET /outside")
      },
      test("alias val y = x inside block") {
        val x = Endpoint(Method.GET / "outside")
        val g = endpoints { val y = x }
        assertTrue(g.y.route.render == "GET /outside")
      },
      test("multiple external refs endpoints { x; y } with different routes") {
        val x = Endpoint(Method.GET / "outside")
        val y = Endpoint(Method.POST / "other")
        @nowarn("msg=pure expression")
        val g = endpoints { x; y }
        assertTrue(
          g.x.route.render == "GET /outside",
          g.y.route.render == "POST /other"
        )
      },
      test("prefix composition \"api\" / endpoints { x } -> GET /api/outside") {
        val x = Endpoint(Method.GET / "outside")
        val g = "api" / endpoints { x }
        assertTrue(g.x.route.render == "GET /api/outside")
      },
      test("capturing prefix PathCodec.int(\"id\") / endpoints { x } -> GET /{id}/orders + static PathInput widening") {
        val x   = Endpoint(Method.GET / "orders")
        val g   = PathCodec.int("id") / endpoints { x }
        val _: RoutePattern[Int] = g.x.route
        assertTrue(g.x.route.render == "GET /{id}/orders")
      },
      test("nested \"api\" / endpoints { PathCodec.int(\"id\") / endpoints { x } }") {
        val x = Endpoint(Method.GET / "orders")
        val g = "api" / endpoints {
          PathCodec.int("id") / endpoints { x }
        }
        assertTrue(g.id.x.route.render == "GET /api/{id}/orders")
      },
      test("duplicate detection endpoints { x; x } should fail with typeCheckErrors") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
val x = Endpoint(Method.GET / "outside")
val _ = endpoints { x; x }
""")
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("duplicate")))
      },
      test("qualified Select Outer.x -> .x") {
        object Outer { val x = Endpoint(Method.GET / "outside") }
        val g = endpoints { Outer.x }
        assertTrue(g.x.route.render == "GET /outside")
      }
    ),
    suite("endpoints macro intra-group rejection")(
      test("earlier endpoints in same group are rejected - val refs val") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
val _ = endpoints {
  val a = Endpoint(Method.GET / "a")
  val b = a
}
""")
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("intra-group dependency")))
      },
      test("earlier endpoints in same group are rejected - second val refs earlier val via builder") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
val _ = endpoints {
  val a = Endpoint(Method.GET / "a")
  val b = Endpoint(Method.GET / "b").in(a.input)
}
""")
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("intra-group")))
      }
    ),
    suite("endpoints macro unsupported input")(
      test("endpoints(42) rejected with unsupported expression") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.http.Method
val _ = endpoints(42)
""")
        assertTrue(
          errors.nonEmpty &&
            errors.exists(_.message.contains("unsupported expression")) &&
            errors.exists(_.message.contains("only accepts"))
        )
      },
      test("endpoints { 42 } rejected") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.http.Method
val _ = endpoints { 42 }
""")
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("only accepts")))
      },
      test("endpoints with non-endpoint string rejected") {
        val errors = typeCheckErrors("""
import zio.blocks.endpoint.*
import zio.http.Method
val _ = endpoints { "oops" }
""")
        assertTrue(errors.nonEmpty && errors.exists(_.message.contains("only accepts")))
      }
    ),
    suite("endpoints macro default import")(
      test("default zio.blocks.endpoint.* import supports bulk creation and existing / composition") {
        // bulk creation via default import
        val group = endpoints {
          val a = Endpoint(Method.GET / "a")
        }
        // existing PathCodec / composition still works under same import
        val pc = PathCodec.int("x") / PathCodec.string("y")
        val rp = Method.GET / pc
        assertTrue(group.a.route.render == "GET /a" && rp.render == "GET /{x}/{y}")
      }
    )
  )
}
