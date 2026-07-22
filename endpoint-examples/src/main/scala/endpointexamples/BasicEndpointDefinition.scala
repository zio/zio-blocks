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
 * Endpoint — Basic Endpoint Definition
 *
 * Demonstrates constructing an `Endpoint` from a `RoutePattern` and attaching
 * request body, query parameters, headers, success outputs, and error outputs
 * using the builder DSL.
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.BasicEndpointDefinition"
 */
@main def BasicEndpointDefinition(): Unit = {

  // Simplest endpoint: GET /health with a string response
  val health = Endpoint(Method.GET / "health")
    .out(Schema.string)

  println(s"Health route: ${health.route.render}")

  // POST with a typed request body and a 201 Created response
  val createUser = Endpoint(Method.POST / "users")
    .in(Schema.string)
    .out(Status.Created, Schema.int)

  println(s"Create user route: ${createUser.route.render}")

  // GET with query parameters and a request header
  val listUsers = Endpoint(Method.GET / "users")
    .query("page", Schema.int)
    .query("limit", Schema.int)
    .header("X-Trace-Id", Schema.string)
    .out(Schema.string)

  println(s"List users route: ${listUsers.route.render}")

  // GET with a dynamic path segment and typed error variants
  val getUser = Endpoint(Method.GET / "users" / PathCodec.int("id"))
    .out(Schema.string)
    .outError(Status.NotFound, Schema.string)
    .outError(Status.BadRequest, Schema.string)

  println(s"Get user route: ${getUser.route.render}")

  // Response header on the success channel
  val withRespHeader = Endpoint(Method.GET / "users")
    .out(Schema.string)
    .outHeader("X-Total-Count", Schema.int)

  println(s"With response header route: ${withRespHeader.route.render}")

  println("BasicEndpointDefinition complete")
}
