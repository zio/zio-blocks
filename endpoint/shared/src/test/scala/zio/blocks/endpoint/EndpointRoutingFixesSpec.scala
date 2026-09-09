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
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.mediatype.{MediaType, MediaTypes}
import zio.blocks.schema.Schema
import zio.http.{Method, Path, Status}
import zio.test._
import zio.test.Assertion.isLeft

object EndpointRoutingFixesSpec extends ZIOSpecDefault {

  private def statusOf(codec: HttpCodec[_, _]): Option[Status] =
    codec match {
      case HttpCodec.StatusCodec(status, _, _, _) => status
      case HttpCodec.Combine(left, right, _)      => statusOf(left).orElse(statusOf(right))
      case HttpCodec.Fallback(left, right, _)     => statusOf(left).orElse(statusOf(right))
      case _                                      => None
    }

  private def bodyMediaTypes(codec: HttpCodec[_, _]): Chunk[MediaType] =
    codec match {
      case HttpCodec.Body(_, mediaTypes, _, _, _, _) => mediaTypes
      case HttpCodec.Combine(left, right, _)         => bodyMediaTypes(left) ++ bodyMediaTypes(right)
      case HttpCodec.Fallback(left, right, _)        => bodyMediaTypes(left) ++ bodyMediaTypes(right)
      case _                                         => Chunk.empty
    }

  def spec: Spec[Any, Nothing] = suite("EndpointRoutingFixesSpec")(
    suite("out overload defaults")(
      test("out(schema) defaults the status to 200 Ok") {
        val endpoint = Endpoint(Method.GET / "users").out(Schema.string)
        assertTrue(statusOf(endpoint.output) == Some(Status.Ok))
      },
      test("out(schema, doc) defaults the status to 200 Ok") {
        val endpoint = Endpoint(Method.GET / "users").out(Schema.string, Doc.empty)
        assertTrue(statusOf(endpoint.output) == Some(Status.Ok))
      },
      test("out(mediaType, schema) defaults the status to 200 Ok and keeps the media type") {
        val json     = MediaTypes.application.json
        val endpoint = Endpoint(Method.GET / "users").out(json, Schema.string)
        assertTrue(statusOf(endpoint.output) == Some(Status.Ok), bodyMediaTypes(endpoint.output) == Chunk.single(json))
      },
      test("out(status, schema) uses exactly the given status") {
        val endpoint = Endpoint(Method.GET / "users").out(Status.Created, Schema.int)
        assertTrue(statusOf(endpoint.output) == Some(Status.Created))
      },
      test("out(status, mediaType, schema, doc) uses exactly the given status and media type") {
        val plain    = MediaTypes.text.plain
        val endpoint = Endpoint(Method.GET / "users").out(Status.Created, plain, Schema.int, Doc.empty)
        assertTrue(
          statusOf(endpoint.output) == Some(Status.Created),
          bodyMediaTypes(endpoint.output) == Chunk.single(plain)
        )
      },
      test("outError(status, schema) never defaults to Ok") {
        val endpoint = Endpoint(Method.GET / "users").outError(Status.BadRequest, Schema.string)
        assertTrue(statusOf(endpoint.error) == Some(Status.BadRequest))
      },
      test("outError(status, mediaType, schema, doc) keeps the given status and media type") {
        val plain    = MediaTypes.text.plain
        val endpoint = Endpoint(Method.GET / "users").outError(Status.Conflict, plain, Schema.int, Doc.empty)
        assertTrue(
          statusOf(endpoint.error) == Some(Status.Conflict),
          bodyMediaTypes(endpoint.error) == Chunk.single(plain)
        )
      }
    ),
    suite("decode error position")(
      test("path decode names the failing segment and value") {
        val codec = PathCodec.literal("users") / PathCodec.int("id")
        assertTrue(
          codec.decode(Path("/users/abc")) == Left(
            "Path /users/abc did not match /users/{id}: failed at segment 1 ('abc')"
          )
        )
      },
      test("path decode reports a missing trailing segment without a complete match") {
        val codec = PathCodec.literal("users") / PathCodec.int("id")
        assertTrue(
          codec.decode(Path("/users")) == Left(
            "Path /users did not match /users/{id}: matched 1 of 1 segments without a complete match"
          )
        )
      },
      test("route decode prefixes the failure with the route") {
        val route = RoutePattern(Method.GET, PathCodec.literal("users") / PathCodec.int("id"))
        assertTrue(
          route.decode(Method.GET, Path("/users/abc")) == Left(
            "Route GET /users/{id}: Path /users/abc did not match /users/{id}: failed at segment 1 ('abc')"
          )
        )
      },
      test("method mismatch message is unchanged") {
        val route = RoutePattern(Method.GET, PathCodec.literal("users"))
        assertTrue(
          route.decode(Method.POST, Path("/users")) == Left("Expected HTTP method GET but found POST")
        )
      }
    ),
    suite("matches parity")(
      test("matches agrees with decode for literal and typed segments") {
        val literal  = PathCodec.literal("users")
        val withInt  = PathCodec.literal("users") / PathCodec.int("id")
        val withLong = PathCodec.literal("orders") / PathCodec.long("orderId")
        val withBool = PathCodec.literal("flags") / PathCodec.bool("active")
        assertTrue(
          literal.matches(Path("/users")) == literal.decode(Path("/users")).isRight,
          literal.matches(Path("/posts")) == literal.decode(Path("/posts")).isRight,
          withInt.matches(Path("/users/42")) == withInt.decode(Path("/users/42")).isRight,
          withInt.matches(Path("/users/abc")) == withInt.decode(Path("/users/abc")).isRight,
          withInt.matches(Path("/users")) == withInt.decode(Path("/users")).isRight,
          withLong.matches(Path("/orders/42")) == withLong.decode(Path("/orders/42")).isRight,
          withLong.matches(Path("/orders/abc")) == withLong.decode(Path("/orders/abc")).isRight,
          withBool.matches(Path("/flags/true")) == withBool.decode(Path("/flags/true")).isRight,
          withBool.matches(Path("/flags/yes")) == withBool.decode(Path("/flags/yes")).isRight
        )
      },
      test("matches agrees with decode for strings, UUIDs, and sign-prefixed numerics") {
        val withString = PathCodec.literal("files") / PathCodec.string("name")
        val withUuid   = PathCodec.literal("items") / PathCodec.uuid("itemId")
        val withInt    = PathCodec.literal("users") / PathCodec.int("id")
        assertTrue(
          withString.matches(Path("/files/a/b")) == withString.decode(Path("/files/a/b")).isRight,
          withUuid.matches(Path("/items/123e4567-e89b-12d3-a456-426614174000")) ==
            withUuid.decode(Path("/items/123e4567-e89b-12d3-a456-426614174000")).isRight,
          withUuid.matches(Path("/items/123E4567-E89B-12D3-A456-426614174000")) ==
            withUuid.decode(Path("/items/123E4567-E89B-12D3-A456-426614174000")).isRight,
          withUuid.matches(Path("/items/not-a-uuid")) == withUuid.decode(Path("/items/not-a-uuid")).isRight,
          withUuid.matches(Path("/items/123e4567-e89b-12d3-a456-42661417400g")) ==
            withUuid.decode(Path("/items/123e4567-e89b-12d3-a456-42661417400g")).isRight,
          withInt.matches(Path("/users/+42")) == withInt.decode(Path("/users/+42")).isRight,
          withInt.matches(Path("/users/-42")) == withInt.decode(Path("/users/-42")).isRight,
          withInt.matches(Path("/users/9999999999")) == withInt.decode(Path("/users/9999999999")).isRight
        )
      },
      test("matches agrees with decode for combined, transformed, fallback, and trailing paths") {
        val versioned = PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("n"))
        val bang      = PathCodec(SegmentCodec.string("word") ~ SegmentCodec.literal("!"))
        val positive  = PathCodec
          .int("id")
          .transformOrFail[Int](
            value => if (value > 0) Right(value) else Left("must be positive"),
            value => Right(value)
          )
        val fallback = PathCodec.literal("users").orElse(PathCodec.literal("posts"))
        val trailing = PathCodec.literal("assets") / PathCodec.trailing
        assertTrue(
          versioned.matches(Path("/v42")) == versioned.decode(Path("/v42")).isRight,
          versioned.matches(Path("/vabc")) == versioned.decode(Path("/vabc")).isRight,
          bang.matches(Path("/hi!")) == bang.decode(Path("/hi!")).isRight,
          bang.matches(Path("/hi")) == bang.decode(Path("/hi")).isRight,
          bang.matches(Path("/hi!x")) == bang.decode(Path("/hi!x")).isRight,
          positive.matches(Path("/5")) == positive.decode(Path("/5")).isRight,
          positive.matches(Path("/-3")) == positive.decode(Path("/-3")).isRight,
          fallback.matches(Path("/posts")) == fallback.decode(Path("/posts")).isRight,
          fallback.matches(Path("/other")) == fallback.decode(Path("/other")).isRight,
          trailing.matches(Path("/assets")) == trailing.decode(Path("/assets")).isRight,
          trailing.matches(Path("/assets/a/b")) == trailing.decode(Path("/assets/a/b")).isRight
        )
      },
      test("decoded values are unchanged by the deterministic single-split decode") {
        val versioned = PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("n"))
        val bang      = PathCodec(SegmentCodec.string("word") ~ SegmentCodec.literal("!"))
        val uuid      = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val withUuid  = PathCodec.literal("items") / PathCodec.uuid("itemId")
        assertTrue(
          versioned.decode(Path("/v42")) == Right(42),
          bang.decode(Path("/hi!")) == Right("hi"),
          withUuid.decode(Path("/items/123e4567-e89b-12d3-a456-426614174000")) == Right(uuid),
          (PathCodec.literal("users") / PathCodec.int("id")).decode(Path("/users/42")) == Right(42),
          (PathCodec.literal("users") / PathCodec.int("id")).decode(Path("/users/abc")).isLeft
        )
      },
      test("route matches agrees with route decode, including the HEAD-to-GET fallback") {
        val route = RoutePattern(Method.GET, PathCodec.literal("users") / PathCodec.int("id"))
        assertTrue(
          route.matches(Method.GET, Path("/users/42")) == route.decode(Method.GET, Path("/users/42")).isRight,
          route.matches(Method.GET, Path("/users/abc")) == route.decode(Method.GET, Path("/users/abc")).isRight,
          route.matches(Method.POST, Path("/users/42")) == route.decode(Method.POST, Path("/users/42")).isRight,
          route.matches(Method.HEAD, Path("/users/42")) == route.decode(Method.HEAD, Path("/users/42")).isRight,
          route.matches(Method.HEAD, Path("/users/abc")) == route.decode(Method.HEAD, Path("/users/abc")).isRight
        )
      }
    ),
    suite("allocation-free numeric parsing")(
      test("plus sign is rejected for int and long, whole and intra segment") {
        val withInt  = PathCodec.literal("users") / PathCodec.int("id")
        val withLong = PathCodec.literal("orders") / PathCodec.long("orderId")
        val vInt     = PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("n"))
        assertTrue(
          withInt.decode(Path("/users/+42")).isLeft,
          withInt.matches(Path("/users/+42")) == false,
          withLong.decode(Path("/orders/+42")).isLeft,
          withLong.matches(Path("/orders/+42")) == false,
          vInt.decode(Path("/v+42")).isLeft,
          vInt.matches(Path("/v+42")) == false
        )
      },
      test("minus zero and leading zeroes decode, overflow and blanks do not") {
        val withInt  = PathCodec.literal("users") / PathCodec.int("id")
        val withLong = PathCodec.literal("orders") / PathCodec.long("orderId")
        assertTrue(
          withInt.decode(Path("/users/-0")) == Right(0),
          withInt.decode(Path("/users/007")) == Right(7),
          withLong.decode(Path("/orders/-0")) == Right(0L),
          withLong.decode(Path("/orders/007")) == Right(7L),
          withInt.decode(Path("/users/2147483647")) == Right(Int.MaxValue),
          withInt.decode(Path("/users/2147483648")).isLeft,
          withInt.decode(Path("/users/-2147483648")) == Right(Int.MinValue),
          withInt.decode(Path("/users/-2147483649")).isLeft,
          withLong.decode(Path("/orders/9223372036854775807")) == Right(Long.MaxValue),
          withLong.decode(Path("/orders/9223372036854775808")).isLeft,
          withLong.decode(Path("/orders/-9223372036854775808")) == Right(Long.MinValue),
          withLong.decode(Path("/orders/-9223372036854775809")).isLeft,
          SegmentCodec.matchesComplete(SegmentCodec.int("n"), "") == false,
          SegmentCodec.decodeComplete(SegmentCodec.int("n"), "") == Nil,
          SegmentCodec.matchesComplete(SegmentCodec.int("n"), " 42") == false,
          SegmentCodec.matchesComplete(SegmentCodec.int("n"), "42 ") == false,
          SegmentCodec.matchesComplete(SegmentCodec.long("n"), "  ") == false
        )
      },
      test("intra-segment number-then-string is greedy and overflow-checked") {
        val vIntRest = PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("n") ~ SegmentCodec.string("rest"))
        val numStr   = PathCodec(SegmentCodec.int("n") ~ SegmentCodec.string("s"))
        assertTrue(
          vIntRest.decode(Path("/v-42rest")) == Right((-42, "rest")),
          vIntRest.matches(Path("/v-42rest")) == true,
          numStr.decode(Path("/42rest")) == Right((42, "rest")),
          numStr.decode(Path("/9999999999rest")).isLeft,
          numStr.matches(Path("/9999999999rest")) == false
        )
      },
      test("string-before-literal uses first-lookahead with no backtracking") {
        val bang = PathCodec(SegmentCodec.string("word") ~ SegmentCodec.literal("!"))
        assertTrue(
          bang.decode(Path("/hi!")) == Right("hi"),
          bang.decode(Path("/a!b!")).isLeft,
          bang.matches(Path("/a!b!")) == false
        )
      }
    ),
    suite("uuid boundaries and deterministic splits")(
      test("v1, v4, v7, nil and uppercase decode; short and malformed do not") {
        val v1    = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        val v4    = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val v7    = UUID.fromString("0190e8d5-5c9d-7b4a-9c9d-1e2f3a4b5c6d")
        val nil   = UUID.fromString("00000000-0000-0000-0000-000000000000")
        val items = PathCodec.literal("items") / PathCodec.uuid("itemId")
        assertTrue(
          items.decode(Path(s"/items/$v1")) == Right(v1),
          items.decode(Path(s"/items/$v4")) == Right(v4),
          items.decode(Path(s"/items/$v7")) == Right(v7),
          items.decode(Path(s"/items/$nil")) == Right(nil),
          items.decode(Path("/items/123E4567-E89B-12D3-A456-426614174000")) == Right(v4),
          items.decode(Path("/items/123e4567-e89b-12d3-a456-42661417400")).isLeft,
          items.matches(Path("/items/123e4567-e89b-12d3-a456-42661417400")) == false,
          items.decode(Path("/items/123e4567_e89b-12d3-a456-426614174000")).isLeft,
          items.matches(Path("/items/123e4567_e89b-12d3-a456-426614174000")) == false
        )
      },
      test("uuid directly followed by a literal matches without backtracking") {
        val uuid     = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val suffixed = PathCodec(SegmentCodec.uuid("id") ~ SegmentCodec.literal("-v1"))
        assertTrue(
          suffixed.decode(Path(s"/$uuid-v1")) == Right(uuid),
          suffixed.matches(Path(s"/$uuid-v1")) == true,
          suffixed.decode(Path("/not-a-uuid-v1")).isLeft,
          suffixed.matches(Path("/not-a-uuid-v1")) == false,
          suffixed.decode(Path("/123e4567-e89b-12d3-a456-426614174000-v1x")).isLeft
        )
      }
    ),
    suite("alternatives")(
      test("duplicated orElse branches collapse to a single alternative") {
        val codec = PathCodec.literal("users").orElse(PathCodec.literal("users"))
        assertTrue(codec.alternatives.length == 1)
      },
      test("multi-method patterns fan out to one pattern per method") {
        val route = RoutePattern(Method.GET #| Method.POST, PathCodec.literal("users"))
        assertTrue(route.alternatives.map(_.method).map(Method.render).toSet == Set("GET", "POST"))
      }
    ),
    suite("route tree behavior")(
      test("exact routes win, trailing routes catch the rest and their own prefix") {
        val tree = RouteTree
          .empty[String]
          .add(RoutePattern(Method.GET, PathCodec.literal("users")), "exact")
          .add(RoutePattern(Method.GET, PathCodec.trailing), "catch-all")
        assertTrue(
          tree.get(Method.GET, Path("/users")) == Some("exact"),
          tree.get(Method.GET, Path("/users/extra")) == Some("catch-all"),
          tree.get(Method.GET, Path("/other")) == Some("catch-all"),
          tree.get(Method.GET, Path("/")) == Some("catch-all")
        )
      }
    ),
    suite("segment transform lifts to PathCodec")(
      test("SegmentCodec.string(...).transform(...) returns a PathCodec that composes with /") {
        val id: PathCodec[String] = SegmentCodec.string("id").transform(_.toUpperCase, _.toLowerCase)
        val route                 = RoutePattern(Method.GET, PathCodec.literal("users") / id)
        assertTrue(
          id.decode(Path("/abc")) == Right("ABC"),
          id.format("ABC").map(_.render) == Right("/abc"),
          route.decode(Method.GET, Path("/users/abc")) == Right("ABC")
        )
      },
      test("~ composes raw segments before transform; the composed codec then transforms correctly") {
        final case class Versioned(major: Int, suffix: String)
        val combined: SegmentCodec[(Int, String)] =
          SegmentCodec.literal("v") ~ SegmentCodec.int("major") ~ SegmentCodec.string("suffix")
        val codec: PathCodec[Versioned] = combined.transform(
          { case (major, suffix) => Versioned(major, suffix) },
          versioned => (versioned.major, versioned.suffix)
        )
        assertTrue(
          codec.decode(Path("/v42stable")) == Right(Versioned(42, "stable")),
          codec.format(Versioned(42, "stable")).map(_.render) == Right("/v42stable")
        )
      },
      test("transformed ~ rawSegment does not compile") {
        assertZIO(
          typeCheck("""
            import zio.blocks.endpoint._

            val left = SegmentCodec.string("left").transform(identity, identity)
            val invalid = left ~ SegmentCodec.string("right")
          """)
        )(isLeft)
      }
    )
  )
}
