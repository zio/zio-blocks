package zio.blocks.scope

import scala.compiletime.testing.typeCheckErrors
import zio.test._

object CompileTimeSafetySpec extends ZIOSpecDefault {

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = closed = true
  }

  def spec = suite("Compile-time safety")(
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
              child.allocate(Resource[Database])
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
              child.allocate(Resource[Database])
            }
            val computation = leakedFromChild.map(_.query("test"))
            parent(computation)
          }
        """)
        assertTrue(errs.nonEmpty)
      }
    ),
    suite("child scope can access parent resources")(
      test("child can use parent-scoped values (positive test)") {
        Scope.global.scoped {
          val parent = summon[Scope[?, ?]]
          val db     = parent.allocate(Resource[Database])
          parent.scoped {
            val child = summon[Scope[parent.Tag, ?]]
            val r     = child.$(db)(_.query("works"))
            assertTrue(r == "result: works")
          }
        }
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
            val db = scope.allocate(Resource[Database])
            db.query("test")
          }
        """)
        assertTrue(errs.nonEmpty)
      }
    ),
    suite("sibling scopes cannot share resources")(
      test("runtime cast works but is unsafe (demonstration)") {
        Scope.global.scoped { (parent: Scope[?, ?]) ?=>
          var leaked: Any = null
          parent.scoped { (child1: Scope[?, ?]) ?=>
            leaked = child1.allocate(Resource[Database])
          }
          parent.scoped { (child2: Scope[?, ?]) ?=>
            val db = leaked.asInstanceOf[Database @@ child2.Tag]
            val r  = child2.$(db)(_.query("test"))
            assertTrue(r == "result: test")
          }
        }
      },
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
                child1.allocate(Resource[Database])
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
    )
  )
}
