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

// Verifies that `PathCodec`/`RoutePattern` composition - including its `combinators.Tuples`-based
// ordered `PathVars` combination - works correctly when called from a genuinely external top-level
// package. This file deliberately lives in package `zio.http.crosscheck` - NOT nested under
// `zio.blocks.endpoint` or even `zio.blocks` at all - to faithfully reproduce the real-world
// zio-http caller scenario (zio-http's routing DSL lives in package `zio.http`, a sibling
// top-level package to `zio.blocks`, not a sub-package of it), catching any cross-package
// resolution regression in the `++`/`/`/`~` combinators' Scala 2.13 whitebox-macro expansion.
package zio.http.crosscheck

import zio.blocks.endpoint._
import zio.test._

object CrossPackagePathVarsSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("Cross-package PathVars composition (Scala 2.13, package zio.http)")(
    test("PathCodec ++ composes a multi-segment PathVars tuple from a package outside zio.blocks") {
      val codec = PathCodec.int("a") ++ PathCodec.literal("b") ++ PathCodec.string("c")
      implicitly[codec.PathVars =:= Tuple2[PathVar["a", Int], PathVar["c", String]]]
      assertCompletes
    }
  )
}
