package zio.blocks.scope

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.scope.internal.{Finalizers, ScopeImplScala3}
import scala.collection.mutable.ArrayBuffer

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
        closeable.run(throw new RuntimeException("boom"))
      } catch {
        case _: RuntimeException => ()
      }
      assertTrue(cleaned)
    },
    test("run called twice throws IllegalStateException") {
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      val closeable         = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)

      closeable.run(())

      val threw = try {
        closeable.run(())
        false
      } catch {
        case e: IllegalStateException =>
          e.getMessage.contains("can only be called once")
      }
      assertTrue(threw)
    },
    test("run reports finalizer errors via errorReporter") {
      val parent: Scope.Any                      = Scope.global
      val config                                 = Config(true)
      val finalizers                             = new Finalizers
      val reportedErrors                         = ArrayBuffer.empty[Chunk[Throwable]]
      val testReporter: Chunk[Throwable] => Unit = (errors: Chunk[Throwable]) => { reportedErrors += errors; () }
      finalizers.add(throw new RuntimeException("finalizer error"))
      val closeable = new ScopeImplScala3[Config, TNil](parent, Context(config), finalizers, testReporter)

      val result = closeable.run(42)

      assertTrue(
        result == 42,
        reportedErrors.size == 1,
        reportedErrors.head.size == 1,
        reportedErrors.head.head.getMessage == "finalizer error"
      )
    },
    test("runWithErrors returns result and errors") {
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add(throw new RuntimeException("error1"))
      finalizers.add(throw new RuntimeException("error2"))
      val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)

      val (result, errors) = closeable.runWithErrors(42)

      assertTrue(
        result == 42,
        errors.size == 2,
        errors.exists(_.getMessage == "error1"),
        errors.exists(_.getMessage == "error2")
      )
    },
    test("runWithErrors called twice throws IllegalStateException") {
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      val closeable         = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)

      closeable.runWithErrors(())

      val threw = try {
        closeable.runWithErrors(())
        false
      } catch {
        case e: IllegalStateException =>
          e.getMessage.contains("can only be called once")
      }
      assertTrue(threw)
    },
    test("runWithErrors closes scope on exception") {
      var cleaned           = false
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add { cleaned = true }
      val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)

      try {
        closeable.runWithErrors[Unit](throw new RuntimeException("boom"))
      } catch {
        case _: RuntimeException => ()
      }
      assertTrue(cleaned)
    }
  )
}
