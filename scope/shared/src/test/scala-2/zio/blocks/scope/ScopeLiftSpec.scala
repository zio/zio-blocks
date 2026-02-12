package zio.blocks.scope

import zio.test._
import zio.test.Assertion._

/**
 * Compile-time verification of ScopeLift typeclass behavior (Scala 2 version).
 *
 * ScopeLift controls what types can exit a child scope and how they transform.
 * These tests verify the instances work correctly.
 */
object ScopeLiftSpec extends ZIOSpecDefault {

  class Database extends AutoCloseable {
    var closed        = false
    def close(): Unit = closed = true
  }

  case class Config(value: String)

  def spec = suite("ScopeLift (Scala 2)")(
    suite("Unscoped types escape as raw values")(
      test("String escapes from child scope as String") {
        val result: String = Scope.global.scoped { parent =>
          parent.scoped { _ =>
            "hello"
          }
        }
        assertTrue(result == "hello")
      },
      test("Int escapes from child scope as Int") {
        val result: Int = Scope.global.scoped { parent =>
          parent.scoped { _ =>
            42
          }
        }
        assertTrue(result == 42)
      },
      test("Boolean escapes from child scope as Boolean") {
        val result: Boolean = Scope.global.scoped { parent =>
          parent.scoped { _ =>
            true
          }
        }
        assertTrue(result)
      },
      test("Unit escapes from child scope as Unit") {
        var sideEffect = false
        Scope.global.scoped { parent =>
          parent.scoped { _ =>
            sideEffect = true
            ()
          }
        }
        assertTrue(sideEffect)
      }
    ),
    suite("Global scope lifts everything as-is")(
      test("TestResult works in Scope.global.scoped") {
        Scope.global.scoped { _ =>
          assertTrue(1 + 1 == 2)
        }
      },
      test("Any type works in global scope") {
        val db: Database = Scope.global.scoped { _ =>
          new Database()
        }
        assertTrue(!db.closed)
      }
    ),
    suite("Parent-scoped values can be returned from child")(
      test("parent-tagged value returns from child as-is") {
        Scope.global.scoped { parent =>
          val parentDb: Database @@ parent.Tag  = parent.allocate(Resource(new Database))
          val escapedDb: Database @@ parent.Tag = parent.scoped { _ =>
            parentDb
          }
          escapedDb.map(!_.closed) // verify we can use it
          assertTrue(true)
        }
      }
    ),
    suite("Nothing lifts for throwing blocks")(
      test("throwing block compiles") {
        var finalizerRan = false
        try {
          Scope.global.scoped { parent =>
            parent.scoped { child =>
              child.defer { finalizerRan = true }
              throw new RuntimeException("expected")
            }
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(finalizerRan)
      }
    ),
    suite("Compile-time rejections")(
      test("closure cannot escape child scope") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              () => "captured"
            }
          }
        """))(isLeft(containsString("ScopeLift")))
      },
      test("child scope itself cannot escape") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              child
            }
          }
        """))(isLeft(containsString("ScopeLift")))
      }
    ),
    suite("ScopeLift.Out type precision")(
      test("unscoped String returns exactly String from nested scopes") {
        val result = Scope.global.scoped { parent =>
          parent.scoped { _ =>
            "hello"
          }
        }
        val _: String = result
        assertTrue(result.length == 5)
      },
      test("unscoped Int returns exactly Int from nested scopes") {
        val result = Scope.global.scoped { parent =>
          parent.scoped { _ =>
            42
          }
        }
        val _: Int = result
        assertTrue(result + 1 == 43)
      },
      test("child-scoped values cannot escape to parent (compile-time rejection)") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              val s: String @@ child.Tag = child.allocate(Resource("hello"))
              s
            }
          }
        """))(isLeft(containsString("ScopeLift")))
      }
    ),
    suite("$ and execute return scoped values")(
      test("$ returns B @@ scope.Tag, not raw B") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            val db: String @@ scope.Tag = scope.allocate(Resource("test"))
            val result: String = scope.$(db)(identity)
            result
          }
        """))(isLeft(containsString("type mismatch")))
      },
      test("execute returns A @@ scope.Tag, not raw A") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            val db: String @@ scope.Tag = scope.allocate(Resource("test"))
            val computation = db.map(_.toUpperCase)
            val result: String = scope.execute(computation)
            result
          }
        """))(isLeft(containsString("type mismatch")))
      }
    ),
    suite("Working patterns for tests")(
      test("use for-comprehension and return Unscoped at boundary") {
        val result: String = Scope.global.scoped { parent =>
          parent.scoped { child =>
            val db: String @@ child.Tag = child.allocate(Resource("data"))
            db.map(_.toUpperCase) // use it to avoid unused warning
            "COMPUTED RESULT"
          }
        }
        assertTrue(result == "COMPUTED RESULT")
      },
      test("nested scopes with unscoped return values") {
        var innerRan    = false
        var outerRan    = false
        val result: Int = Scope.global.scoped { outer =>
          outer.defer { outerRan = true }
          outer.scoped { inner =>
            inner.defer { innerRan = true }
            100
          }
        }
        assertTrue(result == 100, innerRan, outerRan)
      }
    )
  )
}
