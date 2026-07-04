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

// Regression test for a cross-package macro-splice visibility bug: `PathVarTuples.Combine.concat`
// is a Scala 2.13 blackbox macro whose generated tree embeds a literal reference to
// `_root_.zio.blocks.endpoint.PathVarTuples.Combine` INTO THE CALLER's compilation unit at the
// macro's expansion site (the call site of `++`/`/`/`~`, not PathVarTuples' own definition site).
// This file deliberately lives in package `zio.http.crosscheck` - NOT nested under
// `zio.blocks.endpoint` or even `zio.blocks` at all - to faithfully reproduce the real-world
// zio-http caller scenario (zio-http's routing DSL lives in package `zio.http`, a sibling
// top-level package to `zio.blocks`, not a sub-package of it). A sub-package of
// `zio.blocks.endpoint` (e.g. `zio.blocks.endpoint.external`) does NOT reproduce this bug -
// `private[endpoint]` grants access to nested sub-packages - and a sibling package still nested
// under `zio.blocks` (e.g. `zio.blocks.somethingelse`) is fixed by `private[blocks]` alone; only
// a genuinely disjoint top-level package like `zio.http` requires `PathVarTuples` to be fully
// public. Both narrower scopes were verified empirically (see notepad) before landing on this one.
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
