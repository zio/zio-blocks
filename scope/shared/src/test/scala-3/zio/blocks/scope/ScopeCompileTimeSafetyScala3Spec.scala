package zio.blocks.scope

import zio.test._
import zio.test.Assertion.{containsString, isLeft}

object ScopeCompileTimeSafetyScala3Spec extends ZIOSpecDefault {

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = closed = true
  }

  def spec = suite("Scope compile-time safety (Scala 3)")(
    suite("scopedUnscoped UNSOUNDNESS (to be fixed)")(
      test("UNSOUND: scopedUnscoped outputs raw A instead of A @@ S") {
        // This test demonstrates the unsoundness of the current scopedUnscoped.
        //
        // Current behavior: scopedUnscoped outputs raw A
        // Sound behavior: should output A @@ S (parent-scoped)
        //
        // Why this matters:
        // - Raw A loses all scope tracking
        // - If parent scope was leaked from a sibling and is closed,
        //   the raw A is still accessible (bad!)
        // - With A @@ S, the value stays lazy and scope-protected
        //
        // This test PASSES with the current (unsound) implementation.
        // After fix, the result type should be TxResult @@ connScope.ScopeTag,
        // not raw TxResult.

        case class TxResult(value: Int) derives Unscoped

        val rawResult: TxResult = Scope.global.scoped { connScope =>
          import connScope._

          // Child scope produces TxResult @@ txScope.ScopeTag
          // scopedUnscoped unwraps to raw TxResult (UNSOUND)
          // Should be TxResult @@ connScope.ScopeTag (SOUND)
          scoped { txScope =>
            import txScope._
            val data = allocate(Resource(42))
            // This for-comprehension produces TxResult @@ txScope.ScopeTag
            data.map(n => TxResult(n))
          }
        }
        // We got raw TxResult - scope tracking is lost!
        // If connScope had been leaked/closed, this would still work (bad!)
        assertTrue(rawResult.value == 42)
      },
      test("UNSOUND: child-scoped Unscoped value accessible even if parent scope closed") {
        // More concrete demonstration of the unsoundness.
        // We can extract a raw value that was computed in a child scope,
        // completely bypassing scope lifecycle.

        var parentScopeClosed = false

        case class ComputedValue(data: String) derives Unscoped

        val escaped: ComputedValue = Scope.global.scoped { parent =>
          import parent._
          defer { parentScopeClosed = true }

          // This returns raw ComputedValue, not ComputedValue @@ parent.ScopeTag
          scoped { child =>
            import child._
            val resource = allocate(Resource("secret"))
            resource.map(s => ComputedValue(s.toUpperCase))
          }
        }

        // Parent scope is now closed
        assertTrue(parentScopeClosed)

        // But we have the raw value! No scope protection.
        // With sound scopedUnscoped, this would be ComputedValue @@ parent.ScopeTag,
        // and we couldn't access .data directly without going through scope operations.
        assertTrue(escaped.data == "SECRET")
      }
    ),
    suite("scopedUnscoped behavior")(
      test("parent-scoped Unscoped value correctly keeps tag when returned from child") {
        // Verifies that ScopeLift.scoped (higher priority) wins over scopedUnscoped.
        // When returning String @@ parent.ScopeTag from child scope:
        // - scoped applies because parent.ScopeTag <:< parent.ScopeTag
        // - The value stays as String @@ parent.ScopeTag, NOT unwrapped to raw String
        var captured = ""
        Scope.global.scoped { parent =>
          import parent._
          val parentStr: String @@ parent.ScopeTag = allocate(Resource("hello"))

          // Return parentStr from child scope - it stays tagged
          val result: String @@ parent.ScopeTag = parent.scoped { _ =>
            parentStr // String @@ parent.ScopeTag - scoped instance keeps the tag
          }

          // result is still String @@ parent.ScopeTag, we can use it with parent's $
          $(result)(s => captured = s)
        }
        assertTrue(captured == "hello")
      },
      test("raw String assigned to leaked should fail to compile") {
        // This proves that parent-scoped Unscoped values are NOT unwrapped
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            import parent._
            val parentStr: String @@ parent.ScopeTag = allocate(Resource("hello"))

            // This should FAIL: scoped keeps the tag, so we get String @@ parent.ScopeTag, not String
            val leaked: String = parent.scoped { _ =>
              parentStr
            }
            leaked
          }
        """))(isLeft(containsString("Required: String") || containsString("Found:")))
      },
      test("grandparent-scoped value correctly keeps tag through nested scopes") {
        // Verifies that scoped instance is chosen for grandparent-scoped values,
        // NOT scopedUnscoped.
        //
        // Scenario: grandparent -> parent -> child
        // We have String @@ grandparent.ScopeTag and return it from child scope.
        //
        // At child->parent boundary: ScopeLift[String @@ grandparent.ScopeTag, parent.ScopeTag]
        // Since parent.ScopeTag <: grandparent.ScopeTag (by construction), the compiler
        // CAN prove parent.ScopeTag <:< grandparent.ScopeTag, so `scoped` applies.
        //
        // This test verifies assigning to raw String fails (the tag is preserved).
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          val leaked: String = Scope.global.scoped { grandparent =>
            import grandparent._
            val gpStr: String @@ grandparent.ScopeTag = allocate(Resource("hello"))

            grandparent.scoped { parent =>
              parent.scoped { _ =>
                gpStr // String @@ grandparent.ScopeTag
              }
            }
          }
          leaked
        """))(isLeft) // Fails because result is String @@ grandparent.ScopeTag, not String
      },
      test("grandparent-scoped value can be used in grandparent scope after nested returns") {
        // The grandparent-scoped value stays tagged through all scope boundaries
        var captured = ""
        Scope.global.scoped { grandparent =>
          import grandparent._
          val gpStr: String @@ grandparent.ScopeTag = allocate(Resource("hello"))

          // Return gpStr through nested scopes - it stays tagged
          val result: String @@ grandparent.ScopeTag = grandparent.scoped { parent =>
            parent.scoped { _ =>
              gpStr // String @@ grandparent.ScopeTag stays tagged
            }
          }

          // result is still String @@ grandparent.ScopeTag, can use with grandparent's $
          $(result)(s => captured = s)
        }
        assertTrue(captured == "hello")
      },
      test("PROBE: what instance is chosen for grandparent-scoped value at child boundary?") {
        // This test probes which ScopeLift instance is selected.
        // If scopedUnscoped is chosen, the result would be raw String.
        // If scoped is chosen, the result would be String @@ grandparent.ScopeTag.
        //
        // We test by checking if we can assign the result to String @@ grandparent.ScopeTag
        Scope.global.scoped { grandparent =>
          import grandparent._
          val gpStr: String @@ grandparent.ScopeTag = allocate(Resource("hello"))

          grandparent.scoped { parent =>
            // At this boundary, what is lift.Out for String @@ grandparent.ScopeTag?
            val fromChild = parent.scoped { _ =>
              gpStr
            }

            // If fromChild is raw String (scopedUnscoped was chosen), this line won't compile
            // If fromChild is String @@ grandparent.ScopeTag (scoped was chosen), this will work
            val _: String @@ grandparent.ScopeTag = fromChild

            // Can we use it with grandparent's $? (only works if it kept the tag)
            $(fromChild)(identity)
          }
          assertTrue(true)
        }
      }
    ),
    suite("parent cannot use child-created resources after child closes")(
      test("child-scoped value cannot be used by parent via $") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { parent =>
            import parent._
            val leakedFromChild = scoped { child =>
              import child._
              allocate(Resource.from[Database])
            }
            $(leakedFromChild)(_.query("test"))
          }
        """))(isLeft(containsString("Required:") || containsString("is not a member")))
      },
      test("child-scoped resourceful value cannot escape (no ScopeLift instance)") {
        // Database @@ child.ScopeTag has no ScopeLift instance:
        // - scoped doesn't apply (child tag is not <:< parent tag)
        // - scopedUnscoped doesn't apply (Database is not Unscoped)
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { parent =>
            import parent._
            scoped { child =>
              import child._
              allocate(Resource.from[Database]) // Database @@ child.ScopeTag - no ScopeLift
            }
          }
        """))(isLeft)
      }
    ),
    suite("scoped values hide methods")(
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
          }
        """))(isLeft(containsString("Recursive value") || containsString("is not a member")))
      }
    ),
    suite("sibling scopes cannot share resources")(
      test("child-scoped resourceful value fails to escape first scope") {
        // Database @@ child.ScopeTag has no ScopeLift instance,
        // so it fails at the first scoped boundary
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          def unsafeAttempt(): Unit = {
            Scope.global.scoped { parent =>
              val fromChild1 = parent.scoped { child1 =>
                child1.allocate(Resource.from[Database]) // No ScopeLift for Database @@ child1.ScopeTag
              }
              fromChild1 // Would never reach here anyway
            }
          }
        """))(isLeft)
      }
    ),
    // NOTE: @@.unscoped IS package-private (private[scope]) but ZIO Test's typeCheck
    // macro does not correctly evaluate package visibility, so this test passes when
    // it should fail. The visibility is verified by inspecting Scoped.scala directly.
    // Uncomment if typeCheck gains package-visibility support in the future.
    //
    // suite("@@.unscoped is package-private")(
    //   test("unscoped method requires scope package access") {
    //     assertZIO(typeCheck("""
    //       object External {
    //         import zio.blocks.scope._
    //         def leak[A, S](scoped: A @@ S): A = @@.unscoped(scoped)
    //       }
    //     """))(isLeft)
    //   }
    // ),
    suite("tag invariance prevents widening")(
      test("child-scoped resourceful value has no ScopeLift instance") {
        // Db @@ child.ScopeTag has no ScopeLift instance, so the scoped call fails
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Db extends AutoCloseable { def close() = () }

          Scope.global.scoped { parent =>
            import parent._
            scoped { child =>
              import child._
              allocate(Resource(new Db)) // Db @@ child.ScopeTag - no ScopeLift
            }
          }
        """))(isLeft)
      }
    ),
    suite("ScopeLift prevents scope leaks")(
      test("closure capturing child scope is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              import child._
              val db: Database @@ child.ScopeTag = allocate(Resource(new Database))

              // Attempt to leak via closure - should fail to compile
              // Now $(db)(_.query(...)) returns String @@ child.ScopeTag, not String
              // But () => ... is not liftable anyway
              val leakedAction: () => String = () => {
                val scoped = $(db)(_.query("SELECT 1"))
                execute(scoped).run() // trying to extract the string
              }
              leakedAction
            }
          }
        """))(isLeft)
      },
      test("returning child scope itself is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              child // attempt to return the scope itself
            }
          }
        """))(isLeft)
      },
      test("returning unscoped values is allowed") {
        // Return raw Unscoped value from scoped block - ScopeLift extracts it
        val result: String = Scope.global.scoped { parent =>
          parent.scoped { child =>
            import child._
            val db: Database @@ child.ScopeTag = allocate(Resource(new Database))
            // Capture result via side effect
            var captured: String | Null = null
            $(db) { d =>
              val r = d.query("SELECT 1")
              captured = r
              r
            }
            captured.nn // Return raw String - Unscoped, lifts automatically
          }
        }
        assertTrue(result == "result: SELECT 1")
      },
      test("returning parent-scoped values is allowed") {
        var captured: Boolean = false
        Scope.global.scoped { parent =>
          import parent._
          val parentDb: Database @@ parent.ScopeTag = allocate(Resource(new Database))
          val result: Database @@ parent.ScopeTag   = parent.scoped { _ =>
            parentDb // parent-tagged value can be returned from child
          }
          $(result) { db =>
            captured = !db.closed
            db.closed
          }
        }
        assertTrue(captured) // Was not closed while in scope
      }
    ),
    suite("$ and execute always return scoped values")(
      test("$ results are always scoped in non-global scope") {
        // $(db)(identity) returns Db @@ child.ScopeTag
        // Returning Db @@ child.ScopeTag from child scope fails (no ScopeLift for resourceful types)
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Db extends AutoCloseable { def close() = () }

          Scope.global.scoped { parent =>
            import parent._
            scoped { child =>
              import child._
              val db = allocate(Resource(new Db))
              $(db)(identity) // Db @@ child.ScopeTag - no ScopeLift instance
            }
          }
        """))(isLeft)
      },
      test("$ returns tagged value even for unscoped types") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            import parent._
            scoped { child =>
              import child._
              val str = allocate(Resource("hello"))
              // $(str)(_.toUpperCase) now returns String @@ child.ScopeTag
              // So assigning to raw String should fail
              val raw: String = $(str)(_.toUpperCase)
              raw
            }
          }
        """))(isLeft(containsString("Found:")))
      },
      test("child-scoped Unscoped result CAN escape via scopedUnscoped") {
        // String @@ child.ScopeTag CAN escape because String is Unscoped
        // The scopedUnscoped instance unwraps it to raw String
        val result: String = Scope.global.scoped { parent =>
          import parent._
          scoped { child =>
            import child._
            val str = allocate(Resource("hello"))
            // Returns String @@ child.ScopeTag, which is unwrapped to String via scopedUnscoped
            $(str)(_.toUpperCase)
          }
        }
        assertTrue(result == "HELLO")
      },
      test("child-scoped NON-Unscoped result cannot escape (no ScopeLift instance)") {
        // Database @@ child.ScopeTag has no ScopeLift instance
        // $(db)(identity) returns Database @@ child.ScopeTag, which cannot be lifted
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { parent =>
            import parent._
            scoped { child =>
              import child._
              val db = allocate(Resource(new Database))
              $(db)(identity) // Database @@ child.ScopeTag - no ScopeLift
            }
          }
        """))(isLeft)
      }
    )
  )
}
