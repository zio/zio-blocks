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

import scala.language.implicitConversions

import java.util.UUID

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.endpoint.RoutePattern.*
import zio.blocks.schema.Schema
import zio.http.Header
import zio.http.{Method, Status}
import zio.test._
import zio.test.Assertion.{isLeft, isRight}

object EndpointSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("EndpointSpec")(
    suite("SegmentCodec")(
      test("literal segment") {
        val seg = SegmentCodec.Literal("users")
        assertTrue(seg.value == "users")
      },
      test("int segment") {
        val seg = SegmentCodec.IntSeg("id")
        assertTrue(seg.name == "id")
      },
      test("rejects combining two string segments") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val invalid = SegmentCodec.string("a") ~ SegmentCodec.string("b")
          """)
        )(isLeft)
      },
      test("rejects combining two numeric segments (int ~ int)") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val invalid = SegmentCodec.int("a") ~ SegmentCodec.int("b")
          """)
        )(isLeft)
      },
      test("rejects combining int after long") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val invalid = SegmentCodec.long("a") ~ SegmentCodec.int("b")
          """)
        )(isLeft)
      },
      test("rejects combining long after int") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val invalid = SegmentCodec.int("a") ~ SegmentCodec.long("b")
          """)
        )(isLeft)
      },
      test("rejects invalid nested combinations at compile time") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val invalid = SegmentCodec.uuid("id") ~ SegmentCodec.int("a") ~ SegmentCodec.int("b")
          """)
        )(isLeft)
      },
      test("allows string followed by uuid followed by string at compile time") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val valid: SegmentCodec[?] =
              SegmentCodec.string("prefix") ~ SegmentCodec.uuid("id") ~ SegmentCodec.string("suffix")
          """)
        )(isRight)
      },
      test("allows string followed by int followed by string at compile time") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val valid: SegmentCodec[?] =
              SegmentCodec.string("prefix") ~ SegmentCodec.int("id") ~ SegmentCodec.string("suffix")
          """)
        )(isRight)
      },
      test("rejects grouped combinations with an ambiguous boundary at compile time") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val invalid = SegmentCodec.string("prefix") ~ (SegmentCodec.string("middle") ~ SegmentCodec.uuid("id"))
          """)
        )(isLeft)
      },
      test("allows valid mixed combinations") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val valid: SegmentCodec[(Int, String)] =
              SegmentCodec.literal("v") ~ SegmentCodec.int("major") ~ SegmentCodec.string("suffix")
          """)
        )(isRight)
      },
      test("runtime validation still rejects opaque invalid combinations") {
        val left: SegmentCodec[Any]  = SegmentCodec.string("a").asInstanceOf[SegmentCodec[Any]]
        val right: SegmentCodec[Any] = SegmentCodec.string("b").asInstanceOf[SegmentCodec[Any]]

        val result = scala.util.Try {
          SegmentCodec.combineValidated(
            left,
            right,
            summon[zio.blocks.combinators.Tuples.Tuples.WithOut[Any, Any, (Any, Any)]]
          )
        }

        assertTrue(result.failed.toOption.exists(_.getMessage.contains("Cannot combine two string segments")))
      },
      test("runtime validation allows non-adjacent string boundaries") {
        val left: SegmentCodec[Any] =
          (SegmentCodec.string("prefix") ~ SegmentCodec.uuid("id")).asInstanceOf[SegmentCodec[Any]]
        val right: SegmentCodec[Any] = SegmentCodec.string("suffix").asInstanceOf[SegmentCodec[Any]]

        val result = scala.util.Try {
          SegmentCodec.combineValidated(
            left,
            right,
            summon[zio.blocks.combinators.Tuples.Tuples.WithOut[Any, Any, (Any, Any)]]
          )
        }

        assertTrue(result.isSuccess)
      },
      test("string followed by uuid followed by string decodes") {
        val uuid  = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val codec = PathCodec(SegmentCodec.string("prefix") ~ SegmentCodec.uuid("id") ~ SegmentCodec.string("suffix"))

        assertTrue(codec.decode(zio.http.Path(s"/pre${uuid}post")).isRight)
      },
      test("string followed by int followed by string decodes") {
        val codec = PathCodec(SegmentCodec.string("prefix") ~ SegmentCodec.int("id") ~ SegmentCodec.string("suffix"))

        assertTrue(codec.decode(zio.http.Path("/pre123post")).isRight)
      },
      test("runtime validation rejects grouped ambiguous boundaries") {
        val left: SegmentCodec[Any]  = SegmentCodec.string("prefix").asInstanceOf[SegmentCodec[Any]]
        val right: SegmentCodec[Any] =
          (SegmentCodec.string("middle") ~ SegmentCodec.uuid("id")).asInstanceOf[SegmentCodec[Any]]

        val result = scala.util.Try {
          SegmentCodec.combineValidated(
            left,
            right,
            summon[zio.blocks.combinators.Tuples.Tuples.WithOut[Any, Any, (Any, Any)]]
          )
        }

        assertTrue(result.failed.toOption.exists(_.getMessage.contains("Cannot combine two string segments")))
      }
    ),
    suite("PathCodec")(
      test("path string parses slash-separated segments") {
        val path = PathCodec("/users/posts")
        val tree = RouteTree.empty[String].add(RoutePattern(Method.GET, path), "handler")
        assertTrue(tree.get(Method.GET, zio.http.Path("/users/posts")) == Some("handler"))
      },
      test("literal path") {
        val path = PathCodec.literal("users")
        assertTrue(path.isInstanceOf[PathCodec.Segment[?]])
      },
      test("int path") {
        val path = PathCodec.int("id")
        assertTrue(path.isInstanceOf[PathCodec.Segment[?]])
      },
      test("literal path alternatives decode both branches") {
        val path = PathCodec.literal("users").orElse(PathCodec.literal("posts"))
        assertTrue(
          path.decode(zio.http.Path("/users")) == Right(()),
          path.decode(zio.http.Path("/posts")) == Right(())
        )
      },
      test("combined segment path decodes and formats") {
        val path = PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("version"))
        assertTrue(
          path.decode(zio.http.Path("/v42")) == Right(42),
          path.format(42).map(_.render) == Right("/v42")
        )
      },
      test("bool path decodes and formats") {
        val path = PathCodec.bool("flag")
        assertTrue(
          path.decode(zio.http.Path("/true")) == Right(true),
          path.format(false).map(_.render) == Right("/false")
        )
      },
      test("long path decodes and formats") {
        val path = PathCodec.long("id")
        assertTrue(
          path.decode(zio.http.Path("/922337203685477580")) == Right(922337203685477580L),
          path.format(42L).map(_.render) == Right("/42")
        )
      },
      test("string path decodes and formats") {
        val path = PathCodec.string("slug")
        assertTrue(
          path.decode(zio.http.Path("/hello-world")) == Right("hello-world"),
          path.format("zio-http").map(_.render) == Right("/zio-http")
        )
      },
      test("uuid path decodes and formats") {
        val value = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val path  = PathCodec.uuid("id")
        assertTrue(
          path.decode(zio.http.Path(s"/$value")) == Right(value),
          path.format(value).map(_.render) == Right(s"/$value")
        )
      }
    ),
    suite("RoutePattern")(
      test("method syntax is the primary constructor") {
        val getRoute  = Method.GET / "users"
        val postRoute = Method.POST / "users"
        assertTrue(
          getRoute.method == Method.GET,
          RouteTree.empty[String].add(getRoute, "handler").get(Method.GET, zio.http.Path("/users")) == Some("handler"),
          postRoute.method == Method.POST
        )
      },
      test("method + path") {
        val rp = Method.GET / "users"
        assertTrue(rp.method == Method.GET)
      },
      test("route pattern supports chained path composition") {
        val route = Method.GET / "users" / PathCodec.int("id")
        val tree  = RouteTree.empty[String].add(route, "handler")

        assertTrue(tree.get(Method.GET, zio.http.Path("/users/42")) == Some("handler"))
      },
      test("method only") {
        val rp = RoutePattern(Method.POST)
        assertTrue(rp.method == Method.POST)
      },
      test("route pattern any captures trailing path") {
        val route = RoutePattern.any(Method.GET)
        assertTrue(route.decode(Method.GET, zio.http.Path("/users/42")).isRight)
      },
      test("route pattern any matches any method and any path") {
        val route = RoutePattern.any
        assertTrue(
          route.decode(Method.GET, zio.http.Path("/users/42")).isRight,
          route.decode(Method.POST, zio.http.Path("/users/42")).isRight
        )
      },
      test("route pattern alternatives expand ANY into standard methods") {
        val route = RoutePattern.any
        assertTrue(route.alternatives.map(_.method).toSet == Method.standardMethods)
      },
      test("route pattern alternatives expand combined methods") {
        val route = RoutePattern(Method.GET #| Method.POST, PathCodec.literal("users"))
        assertTrue(route.alternatives.map(_.method).toSet == Set(Method.GET, Method.POST))
      },
      test("nest prepends path prefix") {
        val route  = RoutePattern(Method.GET, PathCodec.literal("users") / PathCodec.int("id"))
        val nested = route.nest(PathCodec("/api/v1"))
        val tree   = RouteTree.empty[String].add(nested, "handler")
        assertTrue(
          tree.get(Method.GET, zio.http.Path("/api/v1/users/42")) == Some("handler"),
          tree.get(Method.GET, zio.http.Path("/users/42")).isEmpty
        )
      },
      test("combined method route pattern decodes matching methods") {
        val route = RoutePattern(Method.GET #| Method.POST, PathCodec.literal("users"))
        assertTrue(
          route.decode(Method.GET, zio.http.Path("/users")) == Right(()),
          route.decode(Method.POST, zio.http.Path("/users")) == Right(()),
          route.decode(Method.DELETE, zio.http.Path("/users")).isLeft
        )
      }
    ),
    suite("HttpCodec")(
      test("smart constructors") {
        val q = HttpCodec.query("limit", Schema.int)
        val h = HttpCodec.requestHeader("Authorization", Schema.string)
        val b = HttpCodec.responseBody(Schema.string)
        val s = HttpCodec.status(Status.Created)
        val a = HttpCodec.bearerAuth

        assertTrue(
          q == HttpCodec.Query("limit", Schema.int),
          h == HttpCodec.Header[CodecKind.Request, String]("Authorization", Schema.string),
          b == HttpCodec.Body[CodecKind.Response, String](Schema.string),
          s == HttpCodec.StatusCodec(Some(Status.Created)),
          a.isInstanceOf[HttpCodec[CodecKind.Request, zio.http.headers.Authorization.Bearer]]
        )
      },
      test("query codec") {
        val q = HttpCodec.Query("limit", Schema.int)
        assertTrue(q.name == "limit")
      },
      test("header codec") {
        val h = HttpCodec.Header[CodecKind.Request, String]("Authorization", Schema.string)
        assertTrue(h.name == "Authorization")
      },
      test("explicit header constructors preserve provided names") {
        val requestHeader  = HttpCodec.requestHeader("Authorization", Schema.string)
        val responseHeader = HttpCodec.responseHeader("X-Trace-Id", Schema.string)

        assertTrue(
          requestHeader.name == "Authorization",
          responseHeader.name == "X-Trace-Id"
        )
      },
      test("typed header constructors preserve typed names") {
        object MixedCaseHeaderType extends Header.Typed[Header.Custom] {
          def name: String                                        = "X-Trace-Id"
          def parse(value: String): Either[String, Header.Custom] = Right(Header.Custom(name, value))
          def render(h: Header.Custom): String                    = h.renderedValue
        }

        val requestHeader  = HttpCodec.requestHeader(MixedCaseHeaderType)
        val responseHeader = HttpCodec.responseHeader(MixedCaseHeaderType)

        assertTrue(
          requestHeader.name == "X-Trace-Id",
          responseHeader.name == "X-Trace-Id"
        )
      },
      test("body codec") {
        val b = HttpCodec.Body[CodecKind.Request, String](Schema.string)
        assertTrue(b.name.isEmpty)
      },
      test("status codec") {
        val s = HttpCodec.StatusCodec(Some(Status.Ok))
        assertTrue(s.status.isDefined)
      },
      test("status codec stores metadata") {
        val s = HttpCodec.status(
          Status.Created,
          Doc.empty,
          examples = Chunk(Status.Created, Status.Accepted),
          deprecated = Some(Doc.empty)
        )

        assertTrue(
          s == HttpCodec.StatusCodec(
            Some(Status.Created),
            Doc.empty,
            Chunk(Status.Created, Status.Accepted),
            Some(Doc.empty)
          )
        )
      },
      test("typed authorization schema failures surface SchemaError") {
        val codec = HttpCodec.bearerAuth.asInstanceOf[
          HttpCodec.Header[CodecKind.Request, zio.http.headers.Authorization.Bearer]
        ]
        val result = codec.schema.fromDynamicValue(Schema.string.toDynamicValue("Basic Zm9vOmJhcg=="))

        assertTrue(
          result.isLeft,
          result.swap.exists(_.message.contains("Expected Bearer authorization header"))
        )
      },
      test("empty codec") {
        assertTrue(HttpCodec.Empty.isInstanceOf[HttpCodec[?, ?]])
      }
    ),
    suite("AuthType")(
      test("none") {
        assertTrue(AuthType.None.isInstanceOf[AuthType])
      },
      test("bearer") {
        assertTrue(AuthType.Bearer.isInstanceOf[AuthType])
      },
      test("or composition") {
        val auth = AuthType.Basic | AuthType.Bearer
        assertTrue(auth.isInstanceOf[AuthType.Or[?, ?, ?]])
      },
      test("scoped") {
        val auth = AuthType.Scoped(AuthType.Bearer, List("read", "write"))
        assertTrue(auth.scopes == List("read", "write"))
      },
      test("typed auth is preserved on endpoint") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val endpoint = Endpoint(Method.GET / "users").auth(AuthType.Bearer)
            val codec = endpoint.auth.codec
          """)
        )(isRight)
      },
      test("unauthorizedStatus returns wrapped auth type") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.http.Status
            import zio.http.headers

            val endpoint = Endpoint(Method.GET / "users")
              .auth(AuthType.Bearer)
              .unauthorizedStatus(Status.Unauthorized)

            val auth = endpoint.auth
            val status = auth.unauthorizedStatus
            val codec: HttpCodec[CodecKind.Request, headers.Authorization.Bearer] = auth.codec
          """)
        )(isRight)
      }
    ),
    suite("Endpoint")(
      test("create from route pattern") {
        val ep = Endpoint(Method.GET / "users")
        assertTrue(ep.route.method == Method.GET, ep.auth == AuthType.None)
      }
    ),
    suite("HttpCodec phantom type")(
      test("rejects mixing request and response") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema
            import zio.http.Status

            val bad = HttpCodec.Query("name", Schema.string) ++ HttpCodec.StatusCodec(Some(Status.Ok))
          """)
        )(isLeft)
      },
      test("accepts valid request combination") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val good = HttpCodec.Query("name", Schema.string) ++ HttpCodec.Query("age", Schema.int)
          """)
        )(isRight)
      },
      test("combines request codecs") {
        val input = HttpCodec.Query("name", Schema.string) ++ HttpCodec.Query("age", Schema.int)
        assertTrue(input.isInstanceOf[HttpCodec[CodecKind.Request, ?]])
      },
      test("combines response codecs") {
        val output = HttpCodec.StatusCodec(Some(Status.Ok)) ++
          HttpCodec.Body[CodecKind.Response, String](Schema.string)
        assertTrue(output.isInstanceOf[HttpCodec[CodecKind.Response, ?]])
      }
    ),
    suite("Endpoint builders")(
      test("query and header builders compose request input") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.GET / "users")
              .query(HttpCodec.query("q", Schema.string))
              .header(HttpCodec.requestHeader("X-Trace", Schema.string))
          """)
        )(isRight)
      },
      test("header builder has schema overload") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.GET / "users")
              .header("X-Trace", Schema.string)
          """)
        )(isRight)
      },
      test("header builder has documented schema overload") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.GET / "users")
              .header("X-Trace", Schema.string, Doc.empty)
          """)
        )(isRight)
      },
      test("in builder is additive") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.GET / "users")
              .in(HttpCodec.query("q", Schema.string))
              .in(HttpCodec.requestHeader("X-Trace", Schema.string))
          """)
        )(isRight)
      },
      test("query builder rejects non-query codecs") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            Endpoint(Method.GET / "users").query(HttpCodec.requestBody(Schema.string))
          """)
        )(isLeft)
      },
      test("header builder rejects non-header codecs") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            Endpoint(Method.GET / "users").header(HttpCodec.query("q", Schema.string))
          """)
        )(isLeft)
      },
      test("out builder keeps previous alternatives") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema
            import zio.http.Status

            val endpoint = Endpoint(Method.GET / "users")
              .out(Schema.string)
              .out(Status.Created, Schema.int)
          """)
        )(isRight)
      },
      test("out supports documented schema overload") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.GET / "users")
              .out(Schema.string, Doc.empty)
          """)
        )(isRight)
      },
      test("out supports media-type overloads") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.mediatype.MediaTypes
            import zio.blocks.schema.Schema
            import zio.http.Status

            val endpoint = Endpoint(Method.GET / "users")
              .out(MediaTypes.application.`json`, Schema.string)
              .out(Status.Created, MediaTypes.text.`plain`, Schema.int, Doc.empty)
          """)
        )(isRight)
      },
      test("outHeader has schema overloads") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.GET / "users")
              .outHeader("X-Trace", Schema.string)
              .outHeader("X-Other", Schema.int, Doc.empty)
          """)
        )(isRight)
      },
      test("outError helper adds status-tagged error alternative") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema
            import zio.http.Status

            val endpoint = Endpoint(Method.GET / "users")
              .outError(Status.BadRequest, Schema.string)
          """)
        )(isRight)
      },
      test("outError supports documented schema overload") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema
            import zio.http.Status

            val endpoint = Endpoint(Method.GET / "users")
              .outError(Status.BadRequest, Schema.string, Doc.empty)
          """)
        )(isRight)
      },
      test("outError supports media-type overloads") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.mediatype.MediaTypes
            import zio.blocks.schema.Schema
            import zio.http.Status

            val endpoint = Endpoint(Method.GET / "users")
              .outError(Status.BadRequest, MediaTypes.application.`json`, Schema.string)
              .outError(Status.Conflict, MediaTypes.text.`plain`, Schema.int, Doc.empty)
          """)
        )(isRight)
      },
      test("orOutError introduces Scala 3 union error types") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema
            import zio.http.Status

            val endpoint = Endpoint(Method.GET / "users")
              .orOutError(Status.BadRequest, Schema.string)
              .orOutError(Status.Conflict, Schema.int)

            val _: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None] = endpoint
          """)
        )(isRight)
      },
      test("orOutError supports documented and media-type overloads") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.mediatype.MediaTypes
            import zio.blocks.schema.Schema
            import zio.http.Status

            val endpoint = Endpoint(Method.GET / "users")
              .orOutError(Status.BadRequest, Schema.string, Doc.empty)
              .orOutError(Status.Conflict, MediaTypes.text.`plain`, Schema.int, Doc.empty)

            val _: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None] = endpoint
          """)
        )(isRight)
      },
      test("orOutError builds union-aware fallback codec") {
        val first: HttpCodec[CodecKind.Response, String] =
          HttpCodec.responseBody(Schema.string, name = Some("error-response")) ++ HttpCodec.status(Status.BadRequest)
        val second: HttpCodec[CodecKind.Response, Int] =
          HttpCodec.responseBody(Schema.int, name = Some("error-response")) ++ HttpCodec.status(Status.Conflict)
        val builder = summon[EndpointUnionErrorBuilder.ErrorBuilder.WithOut[String, Int, String | Int]]
        val codec   = builder.add(first, second)

        codec match {
          case fallback: HttpCodec.Fallback[CodecKind.Response, String, Int, String | Int] @unchecked =>
            assertTrue(
              fallback.left == first,
              fallback.right == second,
              fallback.alternator.separate("bad request") == Left("bad request"),
              fallback.alternator.separate(409) == Right(409)
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("orOutError rejects overlapping union types") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema
            import zio.http.Status

            Endpoint(Method.GET / "users")
              .orOutError(Status.BadRequest, Schema.string)
              .orOutError(Status.Conflict, Schema.string)
          """)
        )(isLeft)
      },
      test("in schema overload adds request body") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.POST / "users")
              .in(Schema.string)
          """)
        )(isRight)
      },
      test("in supports documented schema overload") {
        assertZIO(
          typeCheck("""
            import zio.blocks.docs.Doc
            import zio.blocks.endpoint._
            import zio.blocks.schema.Schema

            val endpoint = Endpoint(Method.POST / "users")
              .in(Schema.string, Doc.empty)
          """)
        )(isRight)
      },
      test("in/out/error/auth/doc") {
        val endpoint = Endpoint(Method.GET / "users")
          .in(HttpCodec.Query("q", Schema.string))
          .out(Schema.string)
          .outError(HttpCodec.StatusCodec())
          .auth(AuthType.Bearer)
          .doc(zio.blocks.docs.Doc.empty)

        assertTrue(
          endpoint.auth == AuthType.Bearer,
          endpoint.route.method == Method.GET
        )
      }
    )
  )
}
