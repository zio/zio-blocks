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

package zio.blocks.scope

import zio.test._
import zio.test.Assertion.isLeft

/**
 * Scala 3-only compile-time safety tests.
 *
 * Tests that require Scala 3 syntax (e.g. `derives`, union types).
 * Cross-platform tests live in ScopeSpec and NArySpec.
 */
object ScopeCompileTimeSafetyScala3Spec extends ZIOSpecDefault {

  def spec = suite("Scope compile-time safety (Scala 3)")(
    test("returning scope itself is rejected") {
      assertZIO(typeCheck("""
        import zio.blocks.scope._

        Scope.global.scoped { scope =>
          import scope._
          scope
        }
      """))(isLeft)
    },
    test("custom Unscoped type via derives") {
      case class TxResult(value: Int) derives Unscoped

      val result: TxResult = Scope.global.scoped { _ =>
        TxResult(42)
      }
      assertTrue(result.value == 42)
    },
    // ── N-ary Scala 3-specific negative tests ──────────────────────────────
    test("N=2: method reference (not lambda literal) rejected") {
      // Passing a method reference (not a lambda literal) must fail.
      // In Scala 3 this may produce "requires a lambda" or a "Cyclic reference"
      // error from the inline macro expansion — both indicate rejection.
      assertZIO(typeCheck("""
        import zio.blocks.scope._
        class NaryDB extends AutoCloseable { def query(s: String) = s; def close() = () }
        class NaryCache extends AutoCloseable { def key() = "k"; def close() = () }

        def myFn(d: NaryDB, c: NaryCache): String = d.query(c.key())

        Scope.global.scoped { scope =>
          import scope._
          val db: $[NaryDB]     = allocate(Resource(new NaryDB))
          val cache: $[NaryCache] = allocate(Resource(new NaryCache))
          $(db, cache)(myFn)
          ()
        }
      """))(isLeft)
    },
    test("N=2: named argument passing param — rejected") {
      assertZIO(typeCheck("""
        import zio.blocks.scope._
        class DB extends AutoCloseable { def close() = () }
        class Cache extends AutoCloseable { def close() = () }
        def sink(x: Any): String = x.toString

        Scope.global.scoped { scope =>
          import scope._
          val db: $[DB]       = allocate(Resource(new DB))
          val cache: $[Cache] = allocate(Resource(new Cache))
          $(db, cache)((d1, d2) => sink(x = d1))
          ()
        }
      """))(isLeft)
    }
  )
}
