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
    suite("Unscoped constraint behavior")(
      test("Unscoped types can be returned from single scoped block") {
        val result: String = Scope.global.scoped { scope =>
          import scope._
          "hello"
        }
        assertTrue(result == "hello")
      },
      test("Int can be returned from scoped block") {
        val result: Int = Scope.global.scoped { scope =>
          import scope._
          42
        }
        assertTrue(result == 42)
      },
      test("Unit can be returned from scoped block") {
        var sideEffect = false
        Scope.global.scoped { scope =>
          import scope._
          sideEffect = true
        }
        assertTrue(sideEffect)
      },
      test("custom Unscoped type can be returned") {
        case class TxResult(value: Int) derives Unscoped

        val result: TxResult = Scope.global.scoped { scope =>
          import scope._
          val data: $[Int] = allocate(Resource(42))
          data.map(n => TxResult(n))
        }
        assertTrue(result.value == 42)
      }
    ),
    suite("closure cannot escape scoped blocks")(
      test("closure cannot be returned from scoped block") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            () => "captured"
          }
        """))(isLeft)
      },
      test("returning scope itself is rejected") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            scope
          }
        """))(isLeft)
      }
    ),
    suite("resourceful values cannot escape scoped blocks")(
      test("resourceful value cannot escape scoped block (no Unscoped instance)") {
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
    suite("lower() enables access to parent scope values")(
      test("lower() works") {
        val captured: String = Scope.global.scoped { outer =>
          import outer._
          val parentStr: $[String] = allocate(Resource("hello"))
          val result: String       = outer.scoped { child =>
            import child._
            val childStr = lower(parentStr)
            child.$(childStr)(identity)
          }
          result
        }
        assertTrue(captured == "hello")
      }
    ),
    suite("$ returns scoped values")(
      test("$ returns $[B], not raw B") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource("test"))
            val result: String = $(db)(identity)
            result
          }
        """))(isLeft(containsString("Found:")))
      },
      test("map returns $[B], not raw B") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          Scope.global.scoped { scope =>
            import scope._
            val db = allocate(Resource("test"))
            val result: String = db.map(_.toUpperCase)
            result
          }
        """))(isLeft(containsString("Found:")))
      }
    ),
    suite("returning values from scoped blocks")(
      test("returning raw Unscoped values works") {
        var captured: String | Null = null
        Scope.global.scoped { scope =>
          import scope._
          val db: $[Database] = allocate(Resource(new Database))
          val _: $[String]    = $(db) { d =>
            val r = d.query("SELECT 1")
            captured = r
            r
          }
        }
        assertTrue(captured == "result: SELECT 1")
      },
      test("scoped block returns Unscoped Int") {
        val result: Int = Scope.global.scoped { scope =>
          import scope._
          defer(())
          100
        }
        assertTrue(result == 100)
      }
    )
  )
}
