package zio.blocks.scope

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.scope.internal.{Finalizers, ScopeImplScala2}
import scala.collection.mutable.ArrayBuffer

object CloseableRunSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  def spec = suite("Closeable.run (Scala 2)")(
    test("run executes block and closes scope") {
      var blockRan          = false
      var cleaned           = false
      val parent: Scope.Any = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add { cleaned = true }
      val closeable = Scope.makeCloseable[Config, TNil](parent, Context(config), finalizers)
      closeable.run { scope =>
        blockRan = true
        val c = scope.get[Config]
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
        closeable.run(_ => throw new RuntimeException("boom"))
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

      closeable.run(_ => ())

      val threw = try {
        closeable.run(_ => ())
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
      val closeable = new ScopeImplScala2[Config, TNil, parent.Tag](parent, Context(config), finalizers, testReporter)

      val result = closeable.run(_ => 42)

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

      val (result, errors) = closeable.runWithErrors(_ => 42)

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

      closeable.runWithErrors(_ => ())

      val threw = try {
        closeable.runWithErrors(_ => ())
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
        closeable.runWithErrors[Unit](_ => throw new RuntimeException("boom"))
      } catch {
        case _: RuntimeException => ()
      }
      assertTrue(cleaned)
    },
    test("user-registered finalizers run before AutoCloseable.close()") {
      // When injecting an AutoCloseable, its close() is registered first.
      // User-registered finalizers (via defer) are added later.
      // Since finalizers run in LIFO order, user finalizers should run
      // BEFORE close(), allowing them to use the resource safely.
      val order = ArrayBuffer.empty[String]

      class Resource extends AutoCloseable {
        var closed = false
        def use(): Unit = {
          if (closed) throw new IllegalStateException("Resource already closed!")
          order += "use"
        }
        def close(): Unit = {
          closed = true
          order += "close"
        }
      }

      implicit val globalScope: Scope.Any = Scope.global

      val resource  = new Resource
      val closeable = injected(resource)

      closeable.run { implicit scope =>
        // Register a finalizer that uses the resource
        defer { resource.use() }
        order += "body"
      }

      // Expected order: body executed, then user finalizer (use), then close
      assertTrue(
        order.toList == List("body", "use", "close"),
        !resource.closed || order.indexOf("use") < order.indexOf("close")
      )
    }
  )
}
