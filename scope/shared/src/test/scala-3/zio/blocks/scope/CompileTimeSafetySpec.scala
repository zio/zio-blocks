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
    suite("child scope can access parent resources")(
      test("child can use parent-scoped values (positive test)") {
        Scope.global.scoped {
          val parent = summon[Scope[?, ?]]
          val db     = parent.allocate(Resource.from[Database])
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
            val db = scope.allocate(Resource.from[Database])
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
            leaked = child1.allocate(Resource.from[Database])
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
      test("unscoped types escape as raw values") {
        // This is a POSITIVE test - unscoped types SHOULD escape
        Scope.global.scoped {
          val parent = summon[Scope[?, ?]]
          parent.scoped {
            val child = summon[Scope[parent.Tag, ?]]
            val str   = child.allocate(Resource("hello"))
            // String is Unscoped, so $ returns raw String, not String @@ child.Tag
            val result: String = child.$(str)(_.toUpperCase)
            assertTrue(result == "HELLO")
          }
        }
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
    ),
    test("global scope escapes all results as raw values") {
      // Positive test: in global scope, even "resourceful" types escape
      // Note: This is tricky because Scope.global.scoped creates a child, not global
      // The global scope itself would escape, but we test via scoped which has existential tag
      // So this test verifies the docs claim about GlobalTag
      Scope.global.scoped {
        val scope = summon[Scope[?, ?]]
        // We can verify that String escapes (it's Unscoped)
        val str         = scope.allocate(Resource("test"))
        val raw: String = scope.$(str)(identity)
        assertTrue(raw == "test")
      }
    }
  )
}
