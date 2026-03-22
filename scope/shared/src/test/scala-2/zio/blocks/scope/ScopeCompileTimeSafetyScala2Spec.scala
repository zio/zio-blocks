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
import zio.test.Assertion.{containsString, isLeft}

/**
 * Scala 2-only compile-time safety tests.
 *
 * Tests that verify the Scala 2 specific compile error behavior. Cross-platform
 * tests live in ScopeSpec and NArySpec.
 */
object ScopeCompileTimeSafetyScala2Spec extends ZIOSpecDefault {

  def spec = suite("Scope compile-time safety (Scala 2)")(
    test("scoped rejects non-Unscoped return type") {
      assertZIO(typeCheck("""
        import zio.blocks.scope._

        class Database extends AutoCloseable {
          var closed = false
          def query(sql: String): String = s"res: $sql"
          def close(): Unit = closed = true
        }

        // The scoped block rejects () => String since there's no Unscoped instance
        Scope.global.scoped { child =>
          import child._
          () => "leaked"
        }
      """))(isLeft(containsString("Unscoped")))
    },
    // ── N-ary Scala 2-specific negative tests ──────────────────────────────
    test("N=2: eta-expanded method reference rejected") {
      // Passing an eta-expanded method reference must fail — either the macro
      // rejects it as "not a lambda literal" or catches that the params are
      // used unsafely (eta-expansion passes them as arguments).
      assertZIO(typeCheck("""
        import zio.blocks.scope._
        class MyDB2 extends AutoCloseable { def query(s: String) = s; def close() = () }
        class MyCache2 extends AutoCloseable { def key() = "k"; def close() = () }

        def myFn(d: MyDB2, c: MyCache2): String = d.query(c.key())

        Scope.global.scoped { scope =>
          import scope._
          val db: $[MyDB2]       = allocate(Resource(new MyDB2))
          val cache: $[MyCache2] = allocate(Resource(new MyCache2))
          $(db, cache)(myFn _)
          ()
        }
      """))(isLeft)
    }
  )
}
