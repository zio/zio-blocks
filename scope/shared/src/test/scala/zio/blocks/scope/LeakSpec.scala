package zio.blocks.scope

import scala.annotation.nowarn
import zio.test._

/**
 * Tests for Scope.leak — the escape hatch that unwraps $[A] to A
 * with a compiler warning.
 */
object LeakSpec extends ZIOSpecDefault {

  class Database extends AutoCloseable {
    var closed                     = false
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = closed = true
  }

  @nowarn("msg=.*leaked.*|.*leak.*")
  def spec = suite("leak")(
    test("leak unwraps $[A] to A for String") {
      val result: String = Scope.global.scoped { scope =>
        import scope._
        val s: $[String] = allocate(Resource("hello"))
        val leaked: String = scope.leak(s)
        leaked
      }
      assertTrue(result == "hello")
    },
    test("leak unwraps $[A] to A for Int") {
      val result: Int = Scope.global.scoped { scope =>
        import scope._
        val n: $[Int] = $(42)
        val leaked: Int = scope.leak(n)
        leaked
      }
      assertTrue(result == 42)
    },
    test("leak unwraps $[A] to A for custom class") {
      val result: String = Scope.global.scoped { scope =>
        import scope._
        val db: $[Database] = allocate(Resource.from[Database])
        val leaked: Database = scope.leak(db)
        leaked.query("SELECT 1")
      }
      assertTrue(result == "result: SELECT 1")
    },
    test("leaked value is usable after scope closes") {
      var leaked: Database = null
      Scope.global.scoped { scope =>
        import scope._
        val db: $[Database] = allocate(Resource(new Database))
        leaked = scope.leak(db)
      }
      // The value is usable (though the resource is closed — that's the user's problem)
      assertTrue(leaked != null, leaked.closed)
    },
    test("leak works with AutoCloseable allocated via allocate overload") {
      val result: String = Scope.global.scoped { scope =>
        import scope._
        val db: $[Database] = allocate(new Database)
        val leaked: Database = scope.leak(db)
        leaked.query("test")
      }
      assertTrue(result == "result: test")
    },
    test("leak works with Boolean") {
      val result: Boolean = Scope.global.scoped { scope =>
        import scope._
        val b: $[Boolean] = $(true)
        val leaked: Boolean = scope.leak(b)
        leaked
      }
      assertTrue(result)
    },
    test("leak works with Option") {
      val result: Option[Int] = Scope.global.scoped { scope =>
        import scope._
        val opt: $[Option[Int]] = $(Some(99))
        val leaked: Option[Int] = scope.leak(opt)
        leaked
      }
      assertTrue(result.contains(99))
    }
  )
}
