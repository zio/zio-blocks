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

package zio.blocks.async

import zio.test._

/**
 * Cross-platform, cross-version semantics for `.zip` + the
 * [[zio.blocks.combinators.Tuples.Tuples]] combiner. Confirms that:
 *
 *   - `a zip b` yields a `Tuple2`;
 *   - `a zip b zip c` flattens to a `Tuple3` rather than nesting;
 *   - `Unit` on either side is erased.
 *
 * Both Scala 2 (whitebox-macro flattening) and Scala 3 (`Tuple.Concat`
 * match-type flattening) take the same path via the shared `combinators`
 * module; this spec is the cross-version contract.
 */
object AsyncZipTuplesSpec extends ZIOSpecDefault {

  def spec = suite("AsyncZipTuplesSpec")(
    test("a zip b yields a Tuple2") {
      val r: (Int, String) = Async.succeed(1).zip(Async.succeed("a")).block
      assertTrue(r == ((1, "a")))
    },
    test("a zip b zip c flattens to a Tuple3") {
      val r: (Int, String, Boolean) =
        Async.succeed(1).zip(Async.succeed("a")).zip(Async.succeed(true)).block
      assertTrue(r == ((1, "a", true)))
    },
    test("a zip b zip c zip d flattens to a Tuple4") {
      val r: (Int, String, Boolean, Double) =
        Async
          .succeed(1)
          .zip(Async.succeed("a"))
          .zip(Async.succeed(true))
          .zip(Async.succeed(1.5))
          .block
      assertTrue(r == ((1, "a", true, 1.5)))
    },
    test("Unit on the right is erased") {
      val r: Int = Async.succeed(1).zip(Async.succeed(())).block
      assertTrue(r == 1)
    },
    test("Unit on the left is erased") {
      val r: Int = Async.succeed(()).zip(Async.succeed(1)).block
      assertTrue(r == 1)
    }
  )
}
