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

package endpointexamples

import scala.language.implicitConversions

import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

/**
 * PathCodec and SegmentCodec — Typed Path Construction and Decoding
 *
 * Demonstrates `SegmentCodec` kinds, intra-segment composition with `~`,
 * `PathCodec` construction, bidirectional decode/format, `RoutePattern`
 * matching, nesting, and type transformations.
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.PathAndSegmentCodecs"
 */
@main def PathAndSegmentCodecs(): Unit = {

  // --- SegmentCodec kinds ---
  val intSeg   = SegmentCodec.int("id")
  val strSeg   = SegmentCodec.string("slug")
  val uuidSeg  = SegmentCodec.uuid("id")
  val boolSeg  = SegmentCodec.bool("flag")
  val longSeg  = SegmentCodec.long("id")
  val trailing = SegmentCodec.Trailing

  println(s"Segment kinds: int=${intSeg.render()}, string=${strSeg.render()}, uuid=${uuidSeg.render()}")
  println(s"  bool=${boolSeg.render()}, long=${longSeg.render()}, trailing=${trailing.render()}")

  // Intra-segment composition: single path segment containing a literal prefix and an integer
  // "v42" decodes to 42 and formats 42 back to "v42"
  val versionSeg: SegmentCodec[Int] =
    SegmentCodec.literal("v") ~ SegmentCodec.int("major")

  println(s"Version segment renders as: ${versionSeg.render()}")
  val formattedVersion: Path = versionSeg.format(3)
  println(s"Version 3 formats to: $formattedVersion")

  // --- PathCodec construction ---
  val usersPath   = PathCodec.literal("users") / PathCodec.int("id")
  val apiPath     = PathCodec("/api/v1/users")
  val versionPath = PathCodec(versionSeg)
  println(s"versionPath renders as: ${versionPath.render}")

  println(s"usersPath renders as: ${usersPath.render}")
  println(s"apiPath renders as:   ${apiPath.render}")

  // Bidirectional: decode a Path to a typed value
  val decoded: Either[String, Int] = PathCodec.int("id").decode(Path("/42"))
  println(s"Decoded /42: $decoded")

  // Bidirectional: format a typed value back to a Path
  val uuidValue = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
  val formatted = PathCodec.uuid("id").format(uuidValue)
  println(s"Formatted UUID: $formatted")

  // Literal alternatives: match either /users or /members
  val eitherPath: PathCodec[Unit] =
    PathCodec.literal("users").orElse(PathCodec.literal("members"))

  println(s"orElse matches /users: ${eitherPath.matches(Path("/users"))}")
  println(s"orElse matches /members: ${eitherPath.matches(Path("/members"))}")

  // --- RoutePattern construction and operations ---
  val route = Method.GET / "users" / PathCodec.int("id")

  // Decode: extract a typed value from a method + path
  val routeDecoded: Either[String, Int] = route.decode(Method.GET, Path("/users/42"))
  println(s"Route decoded: $routeDecoded")

  // Encode: rebuild (Method, Path) from a typed value
  val routeEncoded: Either[String, (Method, Path)] = route.encode(42)
  println(s"Route encoded: $routeEncoded")

  // Render: human-readable string matching OpenAPI path parameter convention
  println(s"Route rendered: ${route.render}")

  // Nest: prepend a version prefix to an existing pattern
  val versioned = route.nest(PathCodec("/api/v1"))
  println(s"Versioned route: ${versioned.render}")

  // --- Type transformations ---
  final case class UserId(value: Int)

  val userIdCodec: PathCodec[UserId] =
    PathCodec.int("id").transform[UserId](UserId(_), _.value)

  val decodedUserId = userIdCodec.decode(Path("/99"))
  println(s"UserId decoded: $decodedUserId")

  // transformOrFail: reject non-positive segment values at parse time
  val positiveInt: PathCodec[Int] =
    PathCodec
      .int("count")
      .transformOrFail[Int](
        n => if (n > 0) Right(n) else Left(s"Expected positive, got $n"),
        n => Right(n)
      )

  println(s"Positive decode 5:  ${positiveInt.decode(Path("/5"))}")
  println(s"Positive decode -1: ${positiveInt.decode(Path("/-1"))}")

  println("PathAndSegmentCodecs complete")
}
