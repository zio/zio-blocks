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
      val (scope, close) = Scope.createTestableScope()
      import scope._
      var captured: Int = 0

      val resource: $[StringHolder] = allocate(Resource(new StringHolder("hello")))
      val len: $[Int]               = resource.map(_.length)

      $(len) { l =>
        captured = l
        l
      }
      close()
      assertTrue(captured == 5)
    },
    test("$ operator works") {
      val (scope, close) = Scope.createTestableScope()
      import scope._
      var captured: String = null

      val resource: $[StringHolder] = allocate(Resource(new StringHolder("hello")))
      $(resource) { holder =>
        captured = holder.value
        holder.value
      }
      close()
      assertTrue(captured == "hello")
    },
    test("for-comprehension works") {
      val (scope, close) = Scope.createTestableScope()
      import scope._
      var captured: Boolean = false

      val a: $[StringHolder] = allocate(Resource(new StringHolder("hello")))
      val b: $[StringHolder] = allocate(Resource(new StringHolder("world")))

      val result: $[Boolean] = for {
        x <- a
        y <- b
      } yield x.length == y.length

      $(result) { r =>
        captured = r
        r
      }
      close()
      assertTrue(captured == true)
    }
  )
}
