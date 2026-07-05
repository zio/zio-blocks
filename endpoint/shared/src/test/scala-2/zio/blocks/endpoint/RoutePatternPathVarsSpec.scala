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

import zio.blocks.endpoint.RoutePattern._
import zio.http.Method
import zio.test._

object RoutePatternPathVarsSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("RoutePattern/PathCodec ordered PathVars propagation (Scala 2.13)")(
    test("Method.GET / int / literal / string produces an ordered, concatenated PathVars tuple") {
      val pattern = Method.GET / SegmentCodec.int("userId") / "posts" / SegmentCodec.string("postId")
      implicitly[pattern.PathVars =:= Tuple2[PathVar["userId", Int], PathVar["postId", String]]]
      assertCompletes
    },
    test("PathCodec ++ propagates the same ordered PathVars as the RoutePattern chain it backs") {
      val codec =
        PathCodec.apply(SegmentCodec.int("userId")) ++ PathCodec.literal("posts") ++ PathCodec.string("postId")
      implicitly[codec.PathVars =:= Tuple2[PathVar["userId", Int], PathVar["postId", String]]]
      assertCompletes
    },
    test("a single captured segment produces a one-element PathVars tuple end-to-end") {
      val pattern = Method.GET / SegmentCodec.int("id")
      implicitly[pattern.PathVars =:= PathVar["id", Int]]
      assertCompletes
    },
    test("literal-only routes produce a Unit (NoPathVars) end-to-end") {
      val pattern = Method.GET / "health"
      implicitly[pattern.PathVars =:= Unit]
      assertCompletes
    },
    test("nest prepends a literal prefix without disturbing pre-existing decode behavior") {
      val pattern = Method.GET / SegmentCodec.int("id")
      val nested  = pattern.nest(PathCodec.literal("api"))
      assertTrue(nested.decode(Method.GET, zio.http.Path("/api/42")) == Right(42))
    },
    test("Method.GET / int / literal / string.unused produces an ordered tuple with a PathVar.Ignored entry") {
      val pattern = Method.GET / SegmentCodec.int("userId") / "posts" / SegmentCodec.string("postId").unused
      implicitly[pattern.PathVars =:= Tuple2[PathVar["userId", Int], PathVar.Ignored["postId", String]]]
      assertCompletes
    }
  )
}
