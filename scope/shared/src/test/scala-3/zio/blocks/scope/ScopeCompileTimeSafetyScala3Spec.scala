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
            val leakedFromChild = parent.scoped { child =>
              child.allocate(Resource.from[Database])
            }
            parent.$(leakedFromChild)(_.query("test"))
          }
        """))(isLeft(containsString("is not a member")))
      },
      test("child-scoped value cannot be used by parent via Scoped.map") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          Scope.global.scoped { parent =>
            val leakedFromChild = parent.scoped { child =>
              child.allocate(Resource.from[Database])
            }
            val computation = leakedFromChild.map(_.query("test"))
            parent(computation)
          }
        """))(isLeft(containsString("does not take parameters")))
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
            val db = scope.allocate(Resource.from[Database])
            db.query("test")
          }
        """))(isLeft(containsString("Recursive value") || containsString("is not a member")))
      }
    ),
    suite("sibling scopes cannot share resources")(
      test("correctly typed sibling leak attempt fails at compile time") {
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
                child1.allocate(Resource.from[Database])
              }
              parent.scoped { child2 =>
                child2.$(fromChild1)(_.query("test"))
              }
            }
          }
        """))(isLeft(containsString("ScopeLift")))
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
      test("cannot assign child-tagged value to parent-tagged variable") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Db extends AutoCloseable { def close() = () }

          Scope.global.scoped { parent =>
            val x: Db @@ parent.Tag = parent.scoped { child =>
              child.allocate(Resource(new Db)) // Db @@ child.Tag (existential)
            }
            x
          }
        """))(isLeft(containsString("ScopeLift")))
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
        """))(isLeft(containsString("ScopeLift")))
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
            var captured: String | Null = null
            child.$(db) { d =>
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
    ),
    suite("$ and execute always return scoped values")(
      test("$ results are always scoped in non-global scope") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Db extends AutoCloseable { def close() = () }

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              val db = child.allocate(Resource(new Db))
              val raw: Db = child.$(db)(identity) // should be Db @@ child.Tag, not Db
              raw
            }
          }
        """))(isLeft(containsString("ScopeLift")))
      },
      test("$ returns tagged value even for unscoped types") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              val str = child.allocate(Resource("hello"))
              // child.$(str)(_.toUpperCase) now returns String @@ child.Tag
              // So assigning to raw String should fail
              val raw: String = child.$(str)(_.toUpperCase)
              raw
            }
          }
        """))(isLeft(containsString("Found:")))
      },
      test("child-scoped result cannot escape via ScopeLift") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              val str = child.allocate(Resource("hello"))
              // Returns String @@ child.Tag, which has no ScopeLift instance for parent.Tag
              child.$(str)(_.toUpperCase)
            }
          }
        """))(isLeft(containsString("ScopeLift")))
      }
    )
  )
}
