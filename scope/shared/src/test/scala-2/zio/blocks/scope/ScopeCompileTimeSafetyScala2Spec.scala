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
  // In Scala 3, child scopes have truly existential tags (using `opaque type $[+A] = A`)
  // that prevent resources from escaping at compile time.
  //
  // In Scala 2, due to language limitations around opaque types, child scope types are
  // equivalent at runtime. This means some type-level safety guarantees that work in
  // Scala 3 are not enforced at compile time in Scala 2.
  //
  // The following tests verify the compile-time safety that IS available in Scala 2:
  // - Scoped values hide their methods ($[A]-wrapped values don't expose A's methods)

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
            import scope._
            val db = allocate(Resource.from[Database])
            db.query("test")
          }
        """))(isLeft(containsString("is not a member")))
      }
    ),
    suite("scope leaks prevented")(
      test("closure returning non-Unscoped type is rejected from scoped block") {
        assertZIO(typeCheck("""
          import zio.blocks.scope._

          class Database extends AutoCloseable {
            var closed = false
            def query(sql: String): String = s"res: $sql"
            def close(): Unit = closed = true
          }

          // The macro rejects () => String since there's no Unscoped instance
          Scope.global.scoped { child =>
            import child._
            val db = allocate(Resource(new Database))
            val leakedAction: () => String = () => "leaked"
            $(leakedAction)
          }
        """))(isLeft(containsString("Unscoped")))
      },
      test("returning unscoped values works via scoped") {
        var captured: String = null
        Scope.global.scoped { scope =>
          import scope._
          val db = allocate(Resource(new Database))
          scope.use(db) { d =>
            val r = d.query("SELECT 1")
            captured = r
            r
          }
        }
        assertTrue(captured == "result: SELECT 1")
      }
    )
  )
}
