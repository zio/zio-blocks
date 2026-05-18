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
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

/**
 * AuthType — Authentication Scheme Variants and Composition
 *
 * Demonstrates all built-in `AuthType` variants (None, Basic, Bearer, Digest),
 * the Custom variant for API keys, OR composition with `|`, scoped bearer
 * tokens, and overriding the default unauthorized status.
 *
 * Run with: sbt "endpoint-examples/runMain endpointexamples.AuthTypePatterns"
 */
@main def AuthTypePatterns(): Unit = {

  // --- AuthType.None (default) ---
  // No authentication required; every new Endpoint starts with None
  val publicEndpoint = Endpoint(Method.GET / "health")
    .out(Schema.string)

  println(s"Public endpoint auth: ${publicEndpoint.auth}")

  // --- AuthType.Basic ---
  val basicEndpoint = Endpoint(Method.GET / "admin")
    .auth(AuthType.Basic)

  println(s"Basic auth unauth status: ${basicEndpoint.auth.unauthorizedStatus}")

  // --- AuthType.Bearer ---
  val bearerEndpoint = Endpoint(Method.GET / "me")
    .auth(AuthType.Bearer)

  println(s"Bearer auth unauth status: ${bearerEndpoint.auth.unauthorizedStatus}")

  // --- AuthType.Digest ---
  val digestEndpoint = Endpoint(Method.GET / "secure")
    .auth(AuthType.Digest)

  println(s"Digest auth unauth status: ${digestEndpoint.auth.unauthorizedStatus}")

  // --- AuthType.Custom ---
  // Wrap any HttpCodec for schemes not covered by the built-in variants
  val apiKeyCodec = HttpCodec.requestHeader("X-Api-Key", Schema.string)
  val apiKeyAuth  = AuthType.Custom(apiKeyCodec)
  val keyEndpoint = Endpoint(Method.GET / "data").auth(apiKeyAuth)

  println(s"Custom auth unauth status: ${keyEndpoint.auth.unauthorizedStatus}")

  // --- OR composition: accept either scheme ---
  // The codec tries the left scheme first and falls back to the right
  val flexEndpoint = Endpoint(Method.GET / "resource")
    .auth(AuthType.Basic | AuthType.Bearer)

  println(s"Flex auth unauth status: ${flexEndpoint.auth.unauthorizedStatus}")

  // --- Scoped bearer: attach OAuth scope metadata ---
  val scopedEndpoint = Endpoint(Method.GET / "admin")
    .auth(AuthType.Scoped(AuthType.Bearer, List("admin:read", "admin:write")))

  println(s"Scoped auth unauth status: ${scopedEndpoint.auth.unauthorizedStatus}")

  // --- Override the default unauthorized status ---
  // Default is Status.NotFound (to avoid leaking endpoint existence);
  // override to Status.Unauthorized when endpoint existence is public knowledge
  val strictEndpoint = Endpoint(Method.GET / "me")
    .auth(AuthType.Bearer)
    .unauthorizedStatus(Status.Unauthorized)

  println(s"Strict unauth status: ${strictEndpoint.auth.unauthorizedStatus}")

  println("AuthTypePatterns complete")
}
