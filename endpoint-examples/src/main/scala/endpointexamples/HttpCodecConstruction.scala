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

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.endpoint._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.schema.Schema

/**
 * HttpCodec — Smart Constructors and Composition
 *
 * Demonstrates building `HttpCodec` atoms (query parameters, headers, bodies,
 * status codes) and composing them with `++` (sequential) and `|`
 * (alternative).
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.HttpCodecConstruction"
 */
@main def HttpCodecConstruction(): Unit = {

  // --- Smart constructors ---

  // Query parameter with an optional default value
  val pageCodec  = HttpCodec.query("page", Schema.int, default = Some(1))
  val limitCodec = HttpCodec.query("limit", Schema.int)

  println(s"Query 'page'  default: ${pageCodec.default}")
  println(s"Query 'limit' default: ${limitCodec.default}")

  // Request header by name and schema
  val traceHeader = HttpCodec.requestHeader("X-Trace-Id", Schema.string)
  println(s"Request header name: ${traceHeader.name}")

  // Response header
  val totalCount = HttpCodec.responseHeader("X-Total-Count", Schema.int)
  println(s"Response header name: ${totalCount.name}")

  // Request body restricted to JSON
  val jsonBody =
    HttpCodec.requestBody(Schema.string, mediaTypes = Chunk.single(MediaTypes.application.`json`))
  println(s"Request body codec node: ${jsonBody.getClass.getSimpleName}")

  // Response body
  val respBody = HttpCodec.responseBody(Schema.string)
  println(s"Response body codec node: ${respBody.getClass.getSimpleName}")

  // Status code atoms — predefined constants
  println(s"Ok status:       ${HttpCodec.Ok}")
  println(s"Created status:  ${HttpCodec.Created}")
  println(s"NotFound status: ${HttpCodec.NotFound}")

  // --- Sequential composition with ++ ---
  // Combines two request-side codecs into a single codec whose value is a tuple
  val nameAndAgeQuery: HttpCodec[CodecKind.Request, (String, Int)] =
    HttpCodec.query("name", Schema.string) ++ HttpCodec.query("age", Schema.int)

  println(s"Sequential codec: ${nameAndAgeQuery.getClass.getSimpleName}")

  // --- Alternative composition with | ---
  // Builds a fallback: try the left codec, then the right
  val okOrCreated =
    (HttpCodec.responseBody(Schema.string) ++ HttpCodec.Ok) |
      (HttpCodec.responseBody(Schema.int) ++ HttpCodec.Created)

  println(s"Alternative codec: ${okOrCreated.getClass.getSimpleName}")

  // --- Metadata: doc, examples, default ---
  val richQuery = HttpCodec.query(
    name = "limit",
    schema = Schema.int,
    default = Some(20),
    doc = Doc.empty,
    examples = Chunk("default" -> 20, "max" -> 100)
  )
  println(s"Rich query default: ${richQuery.default}")
  println(s"Rich query examples count: ${richQuery.examples.length}")

  // --- Auth codecs ---
  val bearerCodec = HttpCodec.bearerAuth
  val basicCodec  = HttpCodec.basicAuth
  val digestCodec = HttpCodec.digestAuth
  println(s"Bearer codec: ${bearerCodec.getClass.getSimpleName}")
  println(s"Basic codec:  ${basicCodec.getClass.getSimpleName}")
  println(s"Digest codec: ${digestCodec.getClass.getSimpleName}")

  println("HttpCodecConstruction complete")
}
