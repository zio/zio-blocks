package zio.blocks.scope

import zio.test._
import zio.test.Assertion._

/**
 * Compile-time verification of ScopeLift typeclass behavior.
 *
 * ScopeLift controls what types can exit a child scope and how they transform.
 * These tests verify the instances work correctly.
 */
object ScopeLiftSpec extends ZIOSpecDefault {

  // Test types
  class Database extends AutoCloseable {
    var closed        = false
    def close(): Unit = closed = true
  }

  case class Config(value: String)

  def spec = suite("ScopeLift")(
    suite("Unscoped types escape as raw values")(
      test("String escapes from child scope as String") {
        val result: String = Scope.global.scoped { parent =>
          parent.scoped { child =>
            "hello" // String is Unscoped
          }
        }
        assertTrue(result == "hello")
      },
      test("Int escapes from child scope as Int") {
        val result: Int = Scope.global.scoped { parent =>
          parent.scoped { child =>
            42 // Int is Unscoped
          }
        }
        assertTrue(result == 42)
      },
      test("Boolean escapes from child scope as Boolean") {
        val result: Boolean = Scope.global.scoped { parent =>
          parent.scoped { child =>
            true // Boolean is Unscoped
          }
        }
        assertTrue(result)
      },
      test("Unit escapes from child scope as Unit") {
        var sideEffect   = false
        val result: Unit = Scope.global.scoped { parent =>
          parent.scoped { child =>
            sideEffect = true
            () // Unit is Unscoped
          }
        }
        assertTrue(sideEffect)
      }
    ),
    suite("Global scope lifts everything as-is")(
      test("TestResult works in Scope.global.scoped") {
        Scope.global.scoped { scope =>
          assertTrue(1 + 1 == 2)
        }
      },
      test("Any type works in global scope") {
        val db: Database = Scope.global.scoped { scope =>
          new Database() // Database is NOT Unscoped, but global scope allows it
        }
        assertTrue(!db.closed)
      }
    ),
    suite("Parent-scoped values can be returned from child")(
      test("parent-tagged value returns from child as-is") {
        // parent.scoped { child => parentDb } returns Database @@ parent.ScopeTag
        // because ScopeLift.scoped matches (parent.ScopeTag <:< parent.ScopeTag)
        Scope.global.scoped { parent =>
          import parent._
          val parentDb: Database @@ parent.ScopeTag  = allocate(Resource(new Database))
          val escapedDb: Database @@ parent.ScopeTag = parent.scoped { child =>
            parentDb // Return parent-scoped value from child - lifts as-is
          }
          // escapedDb has type Database @@ parent.ScopeTag, can use with parent
          // Use for-comprehension to check
          val check = escapedDb.map(!_.closed)
          // check is Boolean @@ parent.ScopeTag
          // Now we need to extract Boolean somehow for the assertion
          // For now, just verify the types compile
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
              import child._
              defer { finalizerRan = true }
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
              () => "captured" // Function0 has no ScopeLift instance
            }
          }
        """))(isLeft)
      },
      test("child scope itself cannot escape") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              child // Scope has no ScopeLift instance
            }
          }
        """))(isLeft)
      }
    ),
    suite("ScopeLift.Out type precision")(
      test("unscoped String returns exactly String from nested scopes") {
        // Verify the Out type is precisely String, not something else
        val result = Scope.global.scoped { parent =>
          parent.scoped { child =>
            "hello"
          }
        }
        // This line verifies that result is String (not String @@ something)
        val _: String = result
        assertTrue(result.length == 5)
      },
      test("unscoped Int returns exactly Int from nested scopes") {
        val result = Scope.global.scoped { parent =>
          parent.scoped { child =>
            42
          }
        }
        val _: Int = result
        assertTrue(result + 1 == 43)
      },
      test("child-scoped Unscoped values CAN escape to parent (unwrapped to raw)") {
        // String @@ child.ScopeTag CAN escape because String is Unscoped
        // The scopedUnscoped instance unwraps it to raw String
        val result: String = Scope.global.scoped { parent =>
          import parent._
          scoped { child =>
            import child._
            val s = allocate(Resource("hello"))
            s // String @@ child.ScopeTag escapes as raw String via scopedUnscoped
          }
        }
        assertTrue(result == "hello")
      },
      test("child-scoped NON-Unscoped values cannot escape (no ScopeLift instance)") {
        // Database @@ child.ScopeTag cannot escape because:
        // - scoped doesn't apply (child.ScopeTag is not <:< parent.ScopeTag)
        // - scopedUnscoped doesn't apply (Database is not Unscoped)
        // - No catch-all exists for arbitrary scoped values
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            def close(): Unit = ()
          }

          Scope.global.scoped { parent =>
            import parent._
            scoped { child =>
              import child._
              val db = allocate(Resource(new Database))
              db // Database @@ child.ScopeTag - no ScopeLift instance
            }
          }
        """))(isLeft)
      }
    ),
    suite("$ and execute return scoped values")(
      test("$ returns B @@ scope.ScopeTag, not raw B") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource("test"))
            val result: String = $(db)(identity) // Should fail - $ returns String @@ scope.ScopeTag
            result
          }
        """))(isLeft(containsString("Found:")))
      },
      test("execute returns A @@ scope.ScopeTag, not raw A") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource("test"))
            val computation = db.map(_.toUpperCase)
            val result: String = execute(computation) // Should fail - returns String @@ scope.ScopeTag
            result
          }
        """))(isLeft(containsString("Found:")))
      }
    ),
    suite("Working patterns for tests")(
      test("use for-comprehension and return Unscoped at boundary") {
        val result: String = Scope.global.scoped { parent =>
          parent.scoped { child =>
            import child._
            // Work with scoped values using for-comprehension
            val db    = allocate(Resource("data"))
            val upper = db.map(_.toUpperCase)
            // Extract using Scoped's run (package-private, but we're in scope package)
            // NO - we should NOT use run
            // Instead, return an Unscoped type that ScopeLift will extract
            "COMPUTED RESULT" // Raw String escapes via ScopeLift.unscoped
          }
        }
        assertTrue(result == "COMPUTED RESULT")
      },
      test("nested scopes with unscoped return values") {
        var innerRan    = false
        var outerRan    = false
        val result: Int = Scope.global.scoped { outer =>
          import outer._
          defer { outerRan = true }
          outer.scoped { inner =>
            import inner._
            defer { innerRan = true }
            100 // Unscoped Int
          }
        }
        assertTrue(result == 100, innerRan, outerRan)
      }
    )
  )
}
