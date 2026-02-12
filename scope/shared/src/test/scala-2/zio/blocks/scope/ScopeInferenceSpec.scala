package zio.blocks.scope

import zio.test._

/**
 * Scala 2 version of scope type inference tests. Uses allocate() which ensures
 * proper type inference.
 */
object ScopeInferenceSpec extends ZIOSpecDefault {

  class StringHolder(val value: String) extends AutoCloseable {
    def length: Int   = value.length
    def close(): Unit = ()
  }

  def spec = suite("Scope inference (Scala 2)")(
    test("map works with allocate") {
      val captured: Int = Scope.global.scoped { scope =>
        import scope._
        val resource: $[StringHolder] = allocate(Resource(new StringHolder("hello")))
        resource.map(_.length)
      }
      assertTrue(captured == 5)
    },
    test("use operator works") {
      val captured: String = Scope.global.scoped { scope =>
        import scope._
        val resource: $[StringHolder] = allocate(Resource(new StringHolder("hello")))
        scope.use(resource)(_.value)
      }
      assertTrue(captured == "hello")
    },
    test("for-comprehension works") {
      val captured: Boolean = Scope.global.scoped { scope =>
        import scope._
        val a: $[StringHolder] = allocate(Resource(new StringHolder("hello")))
        val b: $[StringHolder] = allocate(Resource(new StringHolder("world")))
        val result: $[Boolean] = for {
          x <- a
          y <- b
        } yield x.length == y.length
        result
      }
      assertTrue(captured == true)
    }
  )
}
