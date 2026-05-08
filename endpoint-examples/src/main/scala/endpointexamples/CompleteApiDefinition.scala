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
import zio.http.{Method, Path, Status}

/**
 * Complete REST API — All Endpoint Types Working Together
 *
 * Shows a full users CRUD API built from `Endpoint`, `RoutePattern`,
 * `PathCodec`, `SegmentCodec`, `HttpCodec`, `AuthType`, and `RouteTree`.
 * Demonstrates versioned routes via `nest`, Scala 3 union error types via
 * `orOutError`, and `RouteTree` lookup priority.
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.CompleteApiDefinition"
 */
@main def CompleteApiDefinition(): Unit = {

  // --- Domain type with a typed path codec ---
  final case class UserId(value: Int)

  val userIdPath: PathCodec[UserId] =
    PathCodec.int("id").transform[UserId](UserId(_), _.value)

  // --- Endpoint definitions ---

  // GET /users?page=&limit=  — paginated list, bearer-secured
  val listUsers = Endpoint(Method.GET / "users")
    .query("page", Schema.int)
    .query("limit", Schema.int)
    .out(Schema.string)
    .outError(Status.BadRequest, Schema.string)
    .auth(AuthType.Bearer)

  // GET /users/{id}  — fetch a single user, bearer-secured
  val getUser = Endpoint(Method.GET / "users" / userIdPath)
    .out(Schema.string)
    .outError(Status.NotFound, Schema.string)
    .auth(AuthType.Bearer)

  // POST /users  — create a user, 201 on success, bearer-secured
  val createUser = Endpoint(Method.POST / "users")
    .in(Schema.string)
    .out(Status.Created, Schema.int)
    .outError(Status.BadRequest, Schema.string)
    .outError(Status.Conflict, Schema.string)
    .auth(AuthType.Bearer)

  // DELETE /users/{id}  — remove a user, bearer-secured
  val deleteUser = Endpoint(Method.DELETE / "users" / PathCodec.int("id"))
    .out(Status.NoContent, Schema.unit)
    .outError(Status.NotFound, Schema.string)
    .auth(AuthType.Bearer)

  // GET /health  — public health check, no auth required
  val health = Endpoint(Method.GET / "health")
    .out(Schema.string)

  // --- Union error types (Scala 3 only) ---
  // orOutError accumulates error types as a native union instead of nested Eithers;
  // the first call sets Err directly, subsequent calls widen to a union
  val withUnionErrors = Endpoint(Method.GET / "items" / PathCodec.int("id"))
    .orOutError(Status.NotFound, Schema.string)
    .orOutError(Status.Conflict, Schema.int)

  val _: Endpoint[Int, Unit, String | Int, Unit, AuthType.None] = withUnionErrors

  // --- Versioned routes via nest ---
  val v1ListUsers = listUsers.route.nest(PathCodec("/api/v1"))
  val v2ListUsers = listUsers.route.nest(PathCodec("/api/v2"))

  println("Endpoint routes:")
  println(s"  ${listUsers.route.render}")
  println(s"  ${getUser.route.render}")
  println(s"  ${createUser.route.render}")
  println(s"  ${deleteUser.route.render}")
  println(s"  ${health.route.render}")
  println(s"  v1: ${v1ListUsers.render}")
  println(s"  v2: ${v2ListUsers.render}")

  // --- RouteTree: O(depth) routing trie ---
  // Literals are matched first; dynamic segments follow priority ordering
  // (int > long > uuid > bool > string > combined > trailing)
  val tree = RouteTree
    .empty[String]
    .add(Method.GET / "users", "list-users")
    .add(Method.GET / "users" / PathCodec.int("id"), "get-user")
    .add(Method.POST / "users", "create-user")
    .add(Method.DELETE / "users" / PathCodec.int("id"), "delete-user")
    .add(Method.GET / "health", "health")

  println("\nRouteTree lookups:")
  println(s"  GET    /users       → ${tree.get(Method.GET, Path("/users"))}")
  println(s"  GET    /users/42    → ${tree.get(Method.GET, Path("/users/42"))}")
  println(s"  POST   /users       → ${tree.get(Method.POST, Path("/users"))}")
  println(s"  DELETE /users/7     → ${tree.get(Method.DELETE, Path("/users/7"))}")
  println(s"  GET    /health      → ${tree.get(Method.GET, Path("/health"))}")
  // HEAD falls back to GET per HTTP spec
  println(s"  HEAD   /users       → ${tree.get(Method.HEAD, Path("/users"))}")
  // Unregistered path returns None
  println(s"  GET    /notfound    → ${tree.get(Method.GET, Path("/notfound"))}")

  // --- RouteTree merge: right-hand side wins on conflict ---
  val treeA  = RouteTree.empty[String].add(Method.GET / "users", "users-v1")
  val treeB  = RouteTree.empty[String].add(Method.GET / "users", "users-v2")
  val merged = treeA.merge(treeB)
  println(s"\nMerged GET /users → ${merged.get(Method.GET, Path("/users"))}")

  // --- RoutePattern decode and encode ---
  val route   = Method.GET / "users" / PathCodec.int("id")
  val decoded = route.decode(Method.GET, Path("/users/99"))
  val encoded = route.encode(99)
  println(s"\nRoute decode /users/99 → $decoded")
  println(s"Route encode 99        → $encoded")

  println("\nCompleteApiDefinition complete")
}
