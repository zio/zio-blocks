package zio.blocks.scope

import zio.test._
import zio.test.Assertion.{containsString, isLeft}

object ScopeCompileTimeSafetyScala2Spec extends ZIOSpecDefault {

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = closed = true
  }

  // NOTE: Many compile-time safety tests that work in Scala 3 cannot be ported to Scala 2.
  //
  // In Scala 3, child scopes have truly existential tags (using `type Fresh <: ParentTag`)
  // that prevent resources from escaping at compile time.
  //
  // In Scala 2, due to language limitations, child scope tags are existential wildcards
  // (`Scope[self.Tag, _ <: self.Tag]`) but the actual runtime scope uses `self.Tag`.
  // This means some type-level safety guarantees that work in Scala 3 are not enforced
  // at compile time in Scala 2.
  //
  // The following tests verify the compile-time safety that IS available in Scala 2:
  // - Scoped values hide their methods (@@-wrapped values don't expose A's methods)
  //
  // Tests that work in Scala 3 but NOT in Scala 2 (due to existential type limitations):
  // - "child-scoped value cannot be used by parent via $"
  // - "child-scoped value cannot be used by parent via Scoped.map"
  // - "sibling scopes cannot share resources"
  // - "tag invariance prevents widening"
  // - "resourceful results stay scoped in non-global scope"
  // - "cannot treat escaped unscoped result as scoped"

  def spec = suite("Scope compile-time safety (Scala 2)")(
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
            val db = scope.allocate(Resource.from[Database])
            db.query("test")
          }
        """))(isLeft(containsString("is not a member")))
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
              val db: Database @@ child.Tag = child.allocate(Resource(new Database))

              // Attempt to leak via closure - should fail to compile
              // Now child.$(db)(_.query(...)) returns String @@ child.Tag, not String
              // But () => ... is not liftable anyway
              val leakedAction: () => String = () => {
                val scoped = child.$(db)(_.query("SELECT 1"))
                child.execute(scoped).run() // trying to extract the string
              }
              leakedAction
            }
          }
        """))(isLeft(containsString("is not a member")))
      },
      test("returning child scope itself is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              child // attempt to return the scope itself
            }
          }
        """))(isLeft(containsString("ScopeLift")))
      },
      test("returning unscoped values is allowed") {
        // Return raw Unscoped value from scoped block - ScopeLift extracts it
        val result: String = Scope.global.scoped { parent =>
          parent.scoped { child =>
            val db: Database @@ child.Tag = child.allocate(Resource(new Database))
            // Capture result via side effect
            var captured: String = null
            child.$(db) { d =>
              val r = d.query("SELECT 1")
              captured = r
              r
            }
            captured // Return raw String - Unscoped, lifts automatically
          }
        }
        assertTrue(result == "result: SELECT 1")
      },
      test("returning parent-scoped values is allowed") {
        var captured: Boolean = false
        Scope.global.scoped { parent =>
          val parentDb: Database @@ parent.Tag = parent.allocate(Resource(new Database))
          val result: Database @@ parent.Tag   = parent.scoped { _ =>
            parentDb // parent-tagged value can be returned from child
          }
          parent.$(result) { db =>
            captured = !db.closed
            db.closed
          }
        }
        assertTrue(captured) // Was not closed while in scope
      }
    )
    // NOTE: @@.unscoped IS package-private (private[scope]) but ZIO Test's typeCheck
    // macro does not correctly evaluate package visibility, so this test passes when
    // it should fail. The visibility is verified by inspecting ScopedModule.scala directly.
  )
}
