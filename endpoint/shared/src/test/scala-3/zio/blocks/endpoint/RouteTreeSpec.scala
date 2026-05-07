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

import zio.blocks.endpoint.RoutePattern.*
import zio.http.{Method, Path}
import zio.test._

object RouteTreeSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("RouteTreeSpec")(
    test("empty tree") {
      val tree = RouteTree.empty[String]
      assertTrue(tree.get(Method.GET, Path("/")).isEmpty)
    },
    test("single literal route") {
      val tree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.literal("users")), "handler")

      assertTrue(tree.get(Method.GET, Path("/users")) == Some("handler"))
    },
    test("single typed route") {
      val usersWithId = PathCodec.literal("users") / PathCodec.int("id")
      val tree        = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, usersWithId), "handler")

      assertTrue(tree.get(Method.GET, Path("/users/42")) == Some("handler"))
    },
    test("multiple methods") {
      val tree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.literal("users")), "get-users")
        .add(RoutePattern(Method.POST, PathCodec.literal("users")), "post-users")

      assertTrue(
        tree.get(Method.GET, Path("/users")) == Some("get-users"),
        tree.get(Method.POST, Path("/users")) == Some("post-users")
      )
    },
    test("multiple paths") {
      val tree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.literal("users")), "users")
        .add(RoutePattern(Method.GET, PathCodec.literal("posts")), "posts")

      assertTrue(
        tree.get(Method.GET, Path("/users")) == Some("users"),
        tree.get(Method.GET, Path("/posts")) == Some("posts")
      )
    },
    test("path with multiple segments") {
      val usersWithPosts =
        PathCodec.literal("users") /
          PathCodec.int("user-id") /
          PathCodec.literal("posts") /
          PathCodec.int("post-id")
      val tree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, usersWithPosts), "handler")

      assertTrue(tree.get(Method.GET, Path("/users/42/posts/7")) == Some("handler"))
    },
    test("no match") {
      val tree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.literal("users")), "handler")

      assertTrue(tree.get(Method.GET, Path("/posts")).isEmpty)
    },
    test("trailing matches suffix") {
      val assets = PathCodec.literal("assets") / PathCodec.trailing
      val tree   = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, assets), "handler")

      assertTrue(
        tree.get(Method.GET, Path("/assets")) == Some("handler"),
        tree.get(Method.GET, Path("/assets/js/app.js")) == Some("handler")
      )
    },
    test("trailing does not shadow more specific dynamic routes after merge") {
      val trailingTree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.trailing), "trailing")

      val intTree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.int("id")), "int")

      val merged = trailingTree.merge(intTree)

      assertTrue(
        merged.get(Method.GET, Path("/42")) == Some("int"),
        merged.get(Method.GET, Path("/foo/bar")) == Some("trailing")
      )
    },
    test("overlapping dynamic routes prefer more specific kinds before string") {
      val stringFirst = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.string("value")), "string")
        .add(RoutePattern(Method.GET, PathCodec.int("id")), "int")

      val intFirst = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.int("id")), "int")
        .add(RoutePattern(Method.GET, PathCodec.string("value")), "string")

      assertTrue(
        stringFirst.get(Method.GET, Path("/42")) == Some("int"),
        intFirst.get(Method.GET, Path("/42")) == Some("int")
      )
    },
    test("dynamic routes with different metadata share the same branch kind") {
      val first = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.int("id")), "first")
        .add(RoutePattern(Method.GET, PathCodec.int("userId")), "second")

      val second = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.int("userId")), "second")
        .add(RoutePattern(Method.GET, PathCodec.int("id")), "first")

      assertTrue(
        first.get(Method.GET, Path("/42")) == Some("second"),
        second.get(Method.GET, Path("/42")) == Some("first")
      )
    },
    test("merge prefers right-hand side") {
      val tree1 = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.literal("users")), "left")

      val tree2 = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.literal("users")), "right")
        .add(RoutePattern(Method.GET, PathCodec.literal("posts")), "posts")

      val merged = tree1.merge(tree2)

      assertTrue(
        merged.get(Method.GET, Path("/users")) == Some("right"),
        merged.get(Method.GET, Path("/posts")) == Some("posts")
      )
    },
    test("map transforms values") {
      val tree = RouteTree
        .empty[Int]
        .add(RoutePattern(Method.GET, PathCodec.literal("users")), 1)
      val mapped = tree.map(_ + 1)

      assertTrue(mapped.get(Method.GET, Path("/users")) == Some(2))
    },
    test("collision between literal and int segment") {
      val literalRoute = PathCodec("users/42")
      val intRoute     = PathCodec.literal("users") / PathCodec.int("id")
      val tree         = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, intRoute), "int")
        .add(RoutePattern(Method.GET, literalRoute), "literal")

      assertTrue(
        tree.get(Method.GET, Path("/users/42")) == Some("literal"),
        tree.get(Method.GET, Path("/users/99")) == Some("int")
      )
    },
    test("head falls back to get routes") {
      val tree = RouteTree.empty[String].add(Method.GET / "users", "handler")
      assertTrue(tree.get(Method.HEAD, Path("/users")) == Some("handler"))
    },
    test("path alternatives add both routes") {
      val tree = RouteTree
        .empty[String]
        .add(RoutePattern(Method.GET, PathCodec.literal("users").orElse(PathCodec.literal("posts"))), "handler")

      assertTrue(
        tree.get(Method.GET, Path("/users")) == Some("handler"),
        tree.get(Method.GET, Path("/posts")) == Some("handler")
      )
    },
    test("any route expands to all methods when inserted into route tree") {
      val tree = RouteTree.empty[String].add(RoutePattern.any, "handler")

      assertTrue(
        tree.get(Method.GET, Path("/users")) == Some("handler"),
        tree.get(Method.POST, Path("/users")) == Some("handler"),
        tree.get(Method.DELETE, Path("/users")) == Some("handler")
      )
    },
    test("multi-method route expands each method when inserted into route tree") {
      val tree =
        RouteTree.empty[String].add(RoutePattern(Method.GET #| Method.POST, PathCodec.literal("users")), "handler")

      assertTrue(
        tree.get(Method.GET, Path("/users")) == Some("handler"),
        tree.get(Method.POST, Path("/users")) == Some("handler"),
        tree.get(Method.DELETE, Path("/users")).isEmpty
      )
    },
    test("combined segments match within one path segment") {
      val route = Method.GET / PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("version"))
      val tree  = RouteTree.empty[String].add(route, "handler")

      assertTrue(
        tree.get(Method.GET, Path("/v42")) == Some("handler"),
        tree.get(Method.GET, Path("/vfoo")).isEmpty
      )
    }
  )
}
