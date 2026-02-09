package zio.blocks.scope

import zio.test._
import zio.test.Assertion.isLeft

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
        """))(isLeft)
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
            val db = scope.allocate(Resource.from[Database])
            db.query("test")
          }
        """))(isLeft)
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
        """))(isLeft)
      }
    ),
    suite("ScopeEscape compile-time safety")(
      test("resourceful results stay scoped in non-global scope") {
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
        """))(isLeft)
      },
      test("cannot treat escaped unscoped result as scoped") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { parent =>
            parent.scoped { child =>
              val str = child.allocate(Resource("hello"))
              val escaped: String @@ child.Tag = child.$(str)(_.toUpperCase)
              escaped
            }
          }
        """))(isLeft)
      }
    )
  )
}
