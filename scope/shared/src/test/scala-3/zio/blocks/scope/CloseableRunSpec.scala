package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

object CloseableRunSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  def spec = suite("Closeable.run (Scala 3)")(
    test("run executes block and closes scope") {
      var blockRan          = false
      var cleaned           = false
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add { cleaned = true }
      val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
      closeable.run {
        blockRan = true
        val c = $[Config]
        assertTrue(c == config)
      }
      assertTrue(blockRan, cleaned)
    },
    test("run even on exception") {
      var cleaned           = false
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add { cleaned = true }
      val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
      try {
        closeable.run { throw new RuntimeException("boom") }
      } catch {
        case _: RuntimeException => ()
      }
      assertTrue(cleaned)
    }
  )
}
