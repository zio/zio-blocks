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
import zio.test.Assertion.{containsString, isLeft, isRight}

/**
 * Cross-platform tests for Scope.
 */
object ScopeSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)
  object Config {
    implicit val unscoped: Unscoped[Config] = Unscoped.derived[Config]
  }

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def status(): String           = if (closed) "closed" else "open"
    def close(): Unit              = closed = true
  }

  class CloseableResource(val name: String) extends AutoCloseable {
    var closed: Boolean = false
    def close(): Unit   = closed = true
  }

  /**
   * Whether the running compiler reports a spurious `$anonfun` cyclic error for
   * every N=1 `$` call inside a `typeCheck` string — including legal ones.
   *
   * Verified boundary: Scala 2.13 and Scala 3.3 (LTS) typecheck `$` string
   * snippets faithfully (legal code passes, illegal code fails with the exact
   * macro domain sentence). On newer Scala 3 (verified 3.8.3 and 3.9.0) the
   * `transparent inline` N=1 `$` trips signature computation inside the
   * `typeCheck` string harness, so legal and illegal snippets fail identically
   * with "Cyclic reference involving method $anonfun". Direct compilation of
   * the same code is unaffected on every version (see the "use macro allows
   * safe patterns" runtime suite, which compiles the legal shapes directly).
   */
  private val newInlineHarness: Boolean = {
    // BuildInfo.scalaVersion is the compiling compiler's version (also valid
    // on Scala.js, unlike scala.util.Properties).
    val v = BuildInfo.scalaVersion
    v.startsWith("3.") && !v.startsWith("3.3.")
  }

  /**
   * Runs the test only where `typeCheck` strings exercise the real `$` macro.
   */
  private val onlyFaithfulInlineHarness =
    if (newInlineHarness) TestAspect.ignore else TestAspect.identity

  /** Runs the test only where `typeCheck` strings hit the cyclic artifact. */
  private val onlyNewInlineHarness =
    if (newInlineHarness) TestAspect.identity else TestAspect.ignore

  def spec = suite("Scope")(
    suite("global")(
      test("global scope exists") {
        assertTrue(Scope.global != null)
      },
      test("global scope defer works") {
        var ran = false
        Scope.global.defer { ran = true }
        assertTrue(!ran) // deferred, not run yet
      }
    ),
    suite("scope.scoped")(
      test("scoped executes block and closes scope") {
        var cleaned           = false
        val blockRan: Boolean = Scope.global.scoped { scope =>
          import scope._
          defer { cleaned = true }
          true
        }
        assertTrue(blockRan, cleaned)
      },
      test("scoped returns plain Unscoped type") {
        val result: String = Scope.global.scoped { _ =>
          "hello"
        }
        assertTrue(result == "hello")
      },
      test("scoped returns value directly") {
        val result: Int = Scope.global.scoped { _ =>
          100
        }
        assertTrue(result == 100)
      },
      test("scoped closes scope even on exception") {
        var cleaned = false
        try {
          Scope.global.scoped { scope =>
            import scope._
            defer { cleaned = true }
            if (true) throw new RuntimeException("boom")
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(cleaned)
      },
      test("nested scoped blocks work") {
        val result: Int = Scope.global.scoped { _ =>
          val x: Int = Scope.global.scoped { _ =>
            10
          }
          x + 5
        }
        assertTrue(result == 15)
      },
      test("scoped close propagates error from finalizer") {
        val result = try {
          Scope.global.scoped { scope =>
            import scope._
            defer(throw new RuntimeException("test error")): Unit
          }
          false
        } catch {
          case e: RuntimeException => e.getMessage == "test error"
        }
        assertTrue(result)
      },
      test("scoped runs multiple finalizers") {
        var counter = 0
        Scope.global.scoped { scope =>
          import scope._
          defer(counter += 1)
          defer(counter += 10): Unit
        }
        assertTrue(counter == 11)
      }
    ),
    suite("Resource.from macro")(
      test("Resource.from[T] derives from no-arg constructor") {
        val isDb: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.isInstanceOf[Database])
        }
        assertTrue(isDb)
      },
      test("Resource.from[T] handles AutoCloseable") {
        var closed               = false
        val beforeClose: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          defer { closed = true }
          $(db)(d => !d.closed)
        }
        assertTrue(beforeClose, closed)
      }
    ),
    suite("allocate")(
      test("allocate returns scoped value and $ works") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.query("SELECT 1"))
        }
        assertTrue(captured == "result: SELECT 1")
      },
      test("allocate AutoCloseable registers close() as finalizer") {
        var closed = false

        class TestCloseable extends AutoCloseable {
          def value: String          = "test"
          override def close(): Unit = closed = true
        }

        val beforeClose: Boolean = Scope.global.scoped { scope =>
          import scope._
          val resource: $[TestCloseable] = allocate(new TestCloseable)
          val captured: String           = $(resource)(_.value)
          captured == "test" && !closed
        }
        assertTrue(beforeClose, closed)
      },
      test("ScopedResourceOps.allocate works for $[Resource[A]]") {
        class Outer extends AutoCloseable {
          def makeInner: Resource[Inner] = Resource.fromAutoCloseable(new Inner)
          def close(): Unit              = ()
        }

        class Inner extends AutoCloseable {
          def value: String = "inner"
          def close(): Unit = ()
        }

        val result: String = Scope.global.scoped { scope =>
          import scope._
          val outer: $[Outer] = allocate(Resource.fromAutoCloseable(new Outer))
          val inner: $[Inner] = $(outer)(_.makeInner).allocate
          $(inner)(_.value)
        }
        assertTrue(result == "inner")
      }
    ),
    suite("$ operator")(
      test("$ extracts value and applies function") {
        val captured: Boolean = Scope.global.scoped { scope =>
          import scope._
          val config: $[Config] = allocate(Resource(Config(true)))
          $(config)(_.debug)
        }
        assertTrue(captured == true)
      },
      test("$ auto-unwraps Unscoped results") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.query("test"))
        }
        assertTrue(captured == "result: test")
      },
      test("$ with chained method calls") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.query("test").toUpperCase)
        }
        assertTrue(captured == "RESULT: TEST")
      },
      test("$ with multiple references to param") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(d => d.query("a") + " " + d.query("b"))
        }
        assertTrue(captured == "result: a result: b")
      }
    ),
    suite("eager operations")(
      test("$ executes eagerly when scope is open") {
        class TrackedResource extends AutoCloseable {
          var done              = false
          def doWork(): Boolean = { done = true; done }
          def close(): Unit     = ()
        }

        val executed: Boolean = Scope.global.scoped { scope =>
          import scope._
          val resource: $[TrackedResource] = allocate(Resource(new TrackedResource))
          $(resource)(_.doWork())
        }
        assertTrue(executed)
      }
    ),
    suite("nested scopes")(
      test("child scope can access parent resources via lower") {
        val captured: String = Scope.global.scoped { outer =>
          val db: outer.$[Database] = outer.allocate(Resource.from[Database])

          val result: String = outer.scoped { inner =>
            val innerDb: inner.$[Database] = inner.lower(db)
            (inner $ innerDb)(_.query("child"))
          }
          result
        }
        assertTrue(captured == "result: child")
      },
      test("child scope closes before parent") {
        val order = scala.collection.mutable.ArrayBuffer.empty[String]
        Scope.global.scoped { outer =>
          outer.defer(order += "parent")

          outer.scoped { inner =>
            inner.defer(order += "child"): Unit
          }
        }
        assertTrue(order.toList == List("child", "parent"))
      }
    ),
    suite("defer")(
      test("finalizers run in LIFO order") {
        val order = scala.collection.mutable.ArrayBuffer.empty[Int]
        Scope.global.scoped { scope =>
          import scope._
          defer(order += 1)
          defer(order += 2)
          defer(order += 3): Unit
        }
        assertTrue(order.toList == List(3, 2, 1))
      },
      test("package-level defer works") {
        var cleaned = false
        Scope.global.scoped { scope =>
          import scope._
          defer { cleaned = true }: Unit
        }
        assertTrue(cleaned)
      },
      test("defer works with Finalizer capability") {
        var finalized = false
        Scope.global.scoped { scope =>
          import scope._
          defer { finalized = true }: Unit
        }
        assertTrue(finalized)
      },
      test("all finalizers run even if one throws") {
        val order = scala.collection.mutable.ArrayBuffer.empty[Int]
        try {
          Scope.global.scoped { scope =>
            import scope._
            defer(order += 1)
            defer(throw new RuntimeException("finalizer boom"))
            defer(order += 3): Unit
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(order.toList == List(3, 1))
      },
      test("block throws and finalizers throw: primary thrown, finalizer errors suppressed") {
        var caught: Throwable = null
        try {
          Scope.global.scoped { scope =>
            import scope._
            defer(throw new RuntimeException("finalizer 1"))
            defer(throw new RuntimeException("finalizer 2")): Unit
            if (true) throw new RuntimeException("block boom")
          }
        } catch {
          case t: RuntimeException => caught = t
        }
        val suppressed = caught.getSuppressed
        assertTrue(
          caught != null,
          caught.getMessage == "block boom",
          suppressed.length == 2,
          suppressed(0).getMessage == "finalizer 2",
          suppressed(1).getMessage == "finalizer 1"
        )
      },
      test("block succeeds and finalizers throw multiple: first thrown, rest suppressed") {
        var caught: Throwable = null
        try {
          Scope.global.scoped { scope =>
            import scope._
            defer(throw new RuntimeException("finalizer 1"))
            defer(throw new RuntimeException("finalizer 2"))
            defer(throw new RuntimeException("finalizer 3")): Unit
          }
        } catch {
          case t: RuntimeException => caught = t
        }
        val suppressed = caught.getSuppressed
        assertTrue(
          caught != null,
          caught.getMessage == "finalizer 3",
          suppressed.length == 2,
          suppressed(0).getMessage == "finalizer 2",
          suppressed(1).getMessage == "finalizer 1"
        )
      }
    ),
    // The N=1 `$` domain guards below are asserted exactly, but only where the
    // `typeCheck` string harness exercises the real macro: Scala 2.13 and Scala
    // 3.3. On newer Scala 3 the harness reports a spurious `$anonfun` cyclic
    // error for legal and illegal `$` calls alike (see `newInlineHarness`),
    // so a cyclic failure there proves nothing about the guard and is
    // characterized separately instead of being counted as guard proof.
    suite("N=1 $ domain guards")(
      test("passing param as argument is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => println(a))
            ()
          }
        """))(
          isLeft(
            containsString(
              "Parameter 1 ('a') cannot be passed as an argument to a function or method. " +
                "Scoped values may only be used as a method receiver (e.g., a.method())."
            )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("capturing in nested lambda is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => () => a.query("test"))
            ()
          }
        """))(
          // The capture diagnostic names its context precisely and differs by
          // compiler: Scala 2.13 ends at "closure.", Scala 3.3 enumerates
          // "nested lambda, def, or anonymous class". Both arms are complete
          // exact diagnostics, not lax alternatives.
          isLeft(
            containsString("Parameter 1 ('a') cannot be captured in a nested lambda or closure.") ||
              containsString(
                "Parameter 1 ('a') cannot be captured in a nested lambda, def, or anonymous class. " +
                  "Scoped values may only be used as a method receiver (e.g., a.method())."
              )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("storing param in var is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            var stash: Any = null
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            var someVar: Any = null
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => { someVar = a; 42 })
            ()
          }
        """))(
          isLeft(
            containsString(
              "Parameter 1 ('a') cannot be assigned to a variable. " +
                "Scoped values may only be used as a method receiver (e.g., a.method())."
            )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("returning param directly (identity) is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => a)
            ()
          }
        """))(
          isLeft(
            containsString(
              "Parameter 1 ('a') must only be used as a method receiver. " +
                "It cannot be returned, stored, passed as an argument, or captured. " +
                "Scoped values may only be used as a method receiver (e.g., a.method())."
            )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("tuple construction with param is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => (a, 1))
            ()
          }
        """))(
          isLeft(
            containsString(
              "Parameter 1 ('a') cannot be passed as an argument to a function or method. " +
                "Scoped values may only be used as a method receiver (e.g., a.method())."
            )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("constructor argument with param is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          case class Wrapper(value: Any)

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => new Wrapper(a))
            ()
          }
        """))(
          isLeft(
            containsString(
              "Parameter 1 ('a') cannot be passed as an argument to a function or method. " +
                "Scoped values may only be used as a method receiver (e.g., a.method())."
            )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("match expression where param could escape via pattern binding is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(d => d match { case x => x })
            ()
          }
        """))(
          isLeft(
            containsString(
              "Parameter 1 ('d') must only be used as a method receiver. " +
                "It cannot be returned, stored, passed as an argument, or captured. " +
                "Scoped values may only be used as a method receiver (e.g., d.method())."
            )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("if-expression where param is branch result is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(d => if (true) d else null)
            ()
          }
        """))(
          isLeft(
            containsString(
              "Parameter 1 ('d') must only be used as a method receiver. " +
                "It cannot be returned, stored, passed as an argument, or captured. " +
                "Scoped values may only be used as a method receiver (e.g., d.method())."
            )
          )
        )
      } @@ onlyFaithfulInlineHarness,
      test("legal receiver-only use typechecks (positive control)") {
        // Near-identical twin of the rejected shapes above (same Database,
        // same allocate, same scope.$(db) call): only the lambda body is the
        // legal receiver-only use. Guards against a vacuously always-Left
        // harness, which would otherwise let every rejection test above pass.
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => a.query("test"))
            ()
          }
        """))(isRight)
      } @@ onlyFaithfulInlineHarness
    ),
    // Characterization of the newer-Scala-3 `typeCheck` string-harness
    // limitation (see `newInlineHarness`). On those compilers EVERY N=1 `$`
    // call inside a `typeCheck` string — legal or not — fails with the same
    // `$anonfun` cyclic error before the macro domain check runs. These tests
    // therefore document the observed rejection WITHOUT claiming the macro
    // guard fired; the legal-shape test below is the proof: it must not
    // compile in-string here, yet the identical code compiles and runs
    // directly (see "use macro allows safe patterns"). Domain-guard proof
    // lives in the "N=1 $ domain guards" suite on the Scala 2.13 / 3.3 legs.
    // If a future compiler fixes the regression, these tests will fail: that
    // failure is the signal to retire this suite and un-gate the guards.
    suite("N=1 $ typeCheck-harness limitation (newer Scala 3)")(
      test("legal receiver-only use is also rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => a.query("test"))
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("passing param as argument is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => println(a))
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("capturing in nested lambda is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => () => a.query("test"))
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("storing param in var is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            var stash: Any = null
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            var someVar: Any = null
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => { someVar = a; 42 })
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("returning param directly (identity) is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => a)
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("tuple construction with param is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => (a, 1))
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("constructor argument with param is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          case class Wrapper(value: Any)

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(a => new Wrapper(a))
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("match expression where param could escape is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(d => d match { case x => x })
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness,
      test("if-expression where param is branch result is rejected in-string (cyclic artifact)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db: $[Database] = allocate(Resource.from[Database])
            scope.$(db)(d => if (true) d else null)
            ()
          }
        """))(
          isLeft(
            containsString("Cyclic reference involving method $anonfun") &&
              containsString("compute the signature of method $anonfun")
          )
        )
      } @@ onlyNewInlineHarness
    ),
    suite("use macro allows safe patterns")(
      test("simple method call is allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.query("SELECT 1"))
        }
        assertTrue(captured == "result: SELECT 1")
      },
      test("chained method calls are allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.query("test").toUpperCase)
        }
        assertTrue(captured == "RESULT: TEST")
      },
      test("multiple receiver uses are allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(a => a.query("a") + " " + a.query("b"))
        }
        assertTrue(captured == "result: a result: b")
      },
      test("method with non-param args is allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.query("raw arg"))
        }
        assertTrue(captured == "result: raw arg")
      },
      test("field access is allowed") {
        val captured: Boolean = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.closed)
        }
        assertTrue(captured == false)
      },
      test("arity-0 method call is allowed") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource.from[Database])
          $(db)(_.status())
        }
        assertTrue(captured == "open")
      }
    ),
    suite("compile-time safety")(
      test("cannot directly call methods on scoped value") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource.from[Database])
            db.query("test")
            ()
          }
        """))(
          isLeft(
            // Each arm is the genuine diagnostic on its compiler, verified by
            // probe: Scala 2.13 / 3.3 reject the member access on the scoped
            // type; newer Scala 3 stops earlier at the untyped `val db`
            // (recursive value). Both name the offending member/value exactly.
            containsString("value query is not a member of scope.$[Database]") ||
              containsString("Recursive value db needs type")
          )
        )
      },
      test("closure cannot be returned from scoped block") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            () => "captured"
          }
        """))(isLeft(containsString("Unscoped[() => String]")))
      },
      test("resourceful value cannot escape scoped block") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          val db: Database = Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource.from[Database])
            db
          }
        """))(
          isLeft(
            // Each arm is the genuine diagnostic on its compiler, verified by
            // probe: Scala 3 reports the missing Unscoped instance naming the
            // escaping type; Scala 2.13 reports the exact type mismatch
            // instead. Both arms pin the escaping type precisely.
            containsString("No given instance of type zio.blocks.scope.Unscoped[Database]") ||
              (containsString("type mismatch") &&
                containsString("scope.$[Database]") &&
                containsString("required: Database"))
          )
        )
      }
    ),
    suite("Unscoped constraint")(
      test("String can be returned from scoped block") {
        val result: String = Scope.global.scoped { _ =>
          "hello"
        }
        assertTrue(result == "hello")
      },
      test("Int can be returned from scoped block") {
        val result: Int = Scope.global.scoped { _ =>
          42
        }
        assertTrue(result == 42)
      },
      test("Unit can be returned from scoped block") {
        var sideEffect = false
        Scope.global.scoped { _ =>
          sideEffect = true
        }
        assertTrue(sideEffect)
      },
      test("custom Unscoped type can be returned") {
        val result: Config = Scope.global.scoped { scope =>
          import scope._
          val data: $[Boolean] = allocate(Resource(true))
          Config($(data)(_.booleanValue()))
        }
        assertTrue(result.debug)
      }
    ),
    suite("type inference")(
      test("use operator works") {
        val captured: String = Scope.global.scoped { scope =>
          import scope._
          val resource: $[CloseableResource] = allocate(Resource(new CloseableResource("hello")))
          $(resource)(_.name)
        }
        assertTrue(captured == "hello")
      },
      test("returning raw Unscoped values works") {
        val captured: Option[String] = Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource(new Database))
          $(db)(d => Option(d.query("SELECT 1")))
        }
        assertTrue(captured.contains("result: SELECT 1"))
      },
      test("scoped block returns Unscoped Int") {
        val result: Int = Scope.global.scoped { scope =>
          import scope._
          defer(())
          100
        }
        assertTrue(result == 100)
      }
    ),
    suite("closed scope defense")(
      test("isClosed is false while scope is open") {
        val result: Boolean = Scope.global.scoped { scope =>
          !scope.isClosed
        }
        assertTrue(result)
      },
      test("isClosed is true after scope closes") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        assertTrue(child.isClosed)
      },
      test("$ on closed scope throws IllegalStateException with full message") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        val wrapped = child.allocate(Resource(new CloseableResource("test")))
        child.close()
        val caught = try {
          (child $ wrapped)(_.name)
          None
        } catch {
          case e: IllegalStateException => Some(e)
        }
        val expected = zio.blocks.scope.internal.ErrorMessages
          .renderUseOnClosedScope("Scope.Child", color = false)
        assertTrue(
          caught.isDefined,
          caught.exists(_.getMessage == expected)
        )
      },
      test("$ on closed scope with pre-allocated value throws IllegalStateException with full message") {
        // Allocate while open, close, then verify $ throws the same message.
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        val wrapped = child.allocate(Resource(new CloseableResource("pre")))
        child.close()
        val caught = try {
          (child $ wrapped)(_.name)
          None
        } catch {
          case e: IllegalStateException => Some(e)
        }
        val expected = zio.blocks.scope.internal.ErrorMessages
          .renderUseOnClosedScope("Scope.Child", color = false)
        assertTrue(
          caught.isDefined,
          caught.exists(_.getMessage == expected)
        )
      },
      test("allocate on closed scope throws IllegalStateException with full message") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        val caught = try {
          child.allocate(Resource(new Database))
          None
        } catch {
          case e: IllegalStateException => Some(e)
        }
        val expected = zio.blocks.scope.internal.ErrorMessages
          .renderAllocateOnClosedScope("Scope.Child", color = false)
        assertTrue(
          caught.isDefined,
          caught.exists(_.getMessage == expected)
        )
      },
      test("allocate AutoCloseable overload on closed scope throws IllegalStateException with full message") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        val caught = try {
          child.allocate(new Database)
          None
        } catch {
          case e: IllegalStateException => Some(e)
        }
        val expected = zio.blocks.scope.internal.ErrorMessages
          .renderAllocateOnClosedScope("Scope.Child", color = false)
        assertTrue(
          caught.isDefined,
          caught.exists(_.getMessage == expected)
        )
      },
      test("open() on closed scope throws IllegalStateException with full message") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        val caught = try {
          child.open()
          None
        } catch {
          case e: IllegalStateException => Some(e)
        }
        val expected = zio.blocks.scope.internal.ErrorMessages
          .renderOpenOnClosedScope("Scope.Child", color = false)
        assertTrue(
          caught.isDefined,
          caught.exists(_.getMessage == expected)
        )
      },
      test("open() on closed scope does not register spurious finalizers on parent") {
        // Before the fix, open() registered a defer on the parent *before* checking
        // isClosed, leaving a phantom entry in the parent's finalizer registry.
        var parentFinalizerCount = 0
        val parentFins           = new zio.blocks.scope.internal.Finalizers
        val parent               = new Scope.Child[Scope.global.type](
          Scope.global,
          parentFins,
          PlatformScope.captureOwner()
        )
        parent.defer(parentFinalizerCount += 1)

        // Create the closed child *with parent as its parent*, so open() would
        // register the spurious defer on parentFins (not on Scope.global).
        val childFins = new zio.blocks.scope.internal.Finalizers
        val child     = new Scope.Child[parent.type](
          parent,
          childFins,
          PlatformScope.captureOwner()
        )
        child.close()

        try child.open()
        catch { case _: IllegalStateException => () }

        // Only the one explicitly registered finalizer should be in the parent.
        // If open() leaked a defer, runAll would increment the counter twice.
        parent.close()
        assertTrue(parentFinalizerCount == 1)
      },
      test("scoped on closed scope creates born-closed child") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        var innerClosed = false
        child.scoped { inner =>
          innerClosed = inner.isClosed
        }
        assertTrue(child.isClosed, innerClosed)
      },
      test("defer on closed scope is silently ignored") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        var finalizerRan = false
        child.defer { finalizerRan = true }
        assertTrue(child.isClosed, !finalizerRan)
      },
      test("closed-scope trio behaves as documented in one scope") {
        val child = new Scope.Child[Scope.global.type](
          Scope.global,
          new zio.blocks.scope.internal.Finalizers,
          PlatformScope.captureOwner()
        )
        child.close()
        var finalizerRan   = false
        val handle         = child.defer { finalizerRan = true }
        val allocateThrows = try {
          child.allocate(Resource(new Database))
          false
        } catch {
          case _: IllegalStateException => true
        }
        val openThrows = try {
          child.open()
          false
        } catch {
          case _: IllegalStateException => true
        }
        assertTrue(
          child.isClosed,
          handle == DeferHandle.Noop,
          !finalizerRan,
          allocateThrows,
          openThrows
        )
      },
      test("global scope isClosed is always false") {
        assertTrue(!Scope.global.isClosed)
      }
    )
  )
}
