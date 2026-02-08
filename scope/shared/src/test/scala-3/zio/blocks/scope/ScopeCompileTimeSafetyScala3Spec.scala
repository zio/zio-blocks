package zio.blocks.scope

import scala.compiletime.testing.typeCheckErrors
import zio.test._

object ScopeCompileTimeSafetyScala3Spec extends ZIOSpecDefault {

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = closed = true
  }

  def spec = suite("Scope compile-time safety (Scala 3)")(
    suite("parent cannot use child-created resources after child closes")(
      test("child-scoped value cannot be used by parent via $") {
        val errs = typeCheckErrors("""
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
        """)
        assertTrue(errs.nonEmpty)
      },
      test("child-scoped value cannot be used by parent via Scoped.map") {
        val errs = typeCheckErrors("""
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
        """)
        assertTrue(errs.nonEmpty)
      }
    ),

    suite("scoped values hide methods")(
      test("cannot directly call methods on scoped value") {
        val errs = typeCheckErrors("""
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
        """)
        assertTrue(errs.nonEmpty)
      }
    ),
    suite("sibling scopes cannot share resources")(
      test("correctly typed sibling leak attempt fails at compile time") {
        val errs = typeCheckErrors("""
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
        """)
        assertTrue(errs.nonEmpty)
      }
    ),
    suite("@@.unscoped is package-private")(
      test("unscoped method requires scope package access") {
        val errs = typeCheckErrors("""
          object External {
            import zio.blocks.scope._
            def leak[A, S](scoped: A @@ S): A = @@.unscoped(scoped)
          }
        """)
        assertTrue(errs.nonEmpty)
      }
    ),
    suite("tag invariance prevents widening")(
      test("cannot assign child-tagged value to parent-tagged variable") {
        val errs = typeCheckErrors("""
          import zio.blocks.scope._

          class Db extends AutoCloseable { def close() = () }

          Scope.global.scoped { parent =>
            val x: Db @@ parent.Tag = parent.scoped { child =>
              child.allocate(Resource(new Db)) // Db @@ child.Tag (existential)
            }
            x
          }
        """)
        assertTrue(errs.nonEmpty)
      }
    ),
    suite("ScopeEscape behavior")(
      test("resourceful results stay scoped in non-global scope") {
        val errs = typeCheckErrors("""
          import zio.blocks.scope._

          class Db extends AutoCloseable { def close() = () }

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              val db = child.allocate(Resource(new Db))
              val raw: Db = child.$(db)(identity) // should be Db @@ child.Tag, not Db
              raw
            }
          }
        """)
        assertTrue(errs.nonEmpty)
      },
      test("cannot treat escaped unscoped result as scoped") {
        val errs = typeCheckErrors("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              val str = child.allocate(Resource("hello"))
              val escaped: String @@ child.Tag = child.$(str)(_.toUpperCase)
              escaped
            }
          }
        """)
        assertTrue(errs.nonEmpty)
      }
    )
  )
}
