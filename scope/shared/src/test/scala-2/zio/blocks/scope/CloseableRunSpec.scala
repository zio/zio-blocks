package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers
import scala.collection.mutable.ArrayBuffer

object CloseableRunSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  def spec = suite("Closeable.use (Scala 2)")(
    test("run executes block and closes scope") {
      var blockRan          = false
      var cleaned           = false
      val parent = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add { cleaned = true }
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
      closeable.use { scope =>
        blockRan = true
        val c = scope.get[Config]
        assertTrue(c == config)
      }
      assertTrue(blockRan, cleaned)
    },
    test("run even on exception") {
      var cleaned           = false
      val parent = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add { cleaned = true }
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
      try {
        closeable.use(_ => throw new RuntimeException("boom"))
      } catch {
        case _: RuntimeException => ()
      }
      assertTrue(cleaned)
    },
    test("run called twice throws IllegalStateException") {
      val parent = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      val closeable         = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

      closeable.use(_ => ())

      val threw = try {
        closeable.use(_ => ())
        false
      } catch {
        case e: IllegalStateException =>
          e.getMessage.contains("can only be called once")
      }
      assertTrue(threw)
    },
    test("useWithErrors captures finalizer errors") {
      val parent = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add(throw new RuntimeException("finalizer error"))
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

      val (result, errors) = closeable.useWithErrors(_ => 42)

      assertTrue(
        result == 42,
        errors.size == 1,
        errors.head.getMessage == "finalizer error"
      )
    },
    test("useWithErrors returns result and errors") {
      val parent = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add(throw new RuntimeException("error1"))
      finalizers.add(throw new RuntimeException("error2"))
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

      val (result, errors) = closeable.useWithErrors(_ => 42)

      assertTrue(
        result == 42,
        errors.size == 2,
        errors.exists(_.getMessage == "error1"),
        errors.exists(_.getMessage == "error2")
      )
    },
    test("useWithErrors called twice throws IllegalStateException") {
      val parent = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      val closeable         = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

      closeable.useWithErrors(_ => ())

      val threw = try {
        closeable.useWithErrors(_ => ())
        false
      } catch {
        case e: IllegalStateException =>
          e.getMessage.contains("can only be called once")
      }
      assertTrue(threw)
    },
    test("useWithErrors closes scope on exception") {
      var cleaned           = false
      val parent = Scope.global
      val config            = Config(true)
      val finalizers        = new Finalizers
      finalizers.add { cleaned = true }
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

      try {
        closeable.useWithErrors[Unit](_ => throw new RuntimeException("boom"))
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
        var closed      = false
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

      closeable.use { implicit scope =>
        // Register a finalizer that uses the resource
        defer(resource.use())
        order += "body"
      }

      // Expected order: body executed, then user finalizer (use), then close
      assertTrue(
        order.toList == List("body", "use", "close"),
        !resource.closed || order.indexOf("use") < order.indexOf("close")
      )
    },
    test("injected[T] wireable: user finalizers run before AutoCloseable.close()") {
      // Same test but using injected[T] with wireable construction
      // instead of injected(value)
      val order = ArrayBuffer.empty[String]

      implicit val globalScope: Scope.Any = Scope.global

      // Use a static holder to track order across the test
      OrderTracker.order = order

      val closeable = injected[OrderTrackingResource]

      closeable.use { implicit scope =>
        val resource = $[OrderTrackingResource]
        // Register a finalizer that uses the resource
        defer(resource.$(_.use()))
        order += "body"
      }

      // Expected order: body executed, then user finalizer (use), then close
      assertTrue(
        order.toList == List("body", "use", "close"),
        order.indexOf("use") < order.indexOf("close")
      )
    }
  )
}

// Helper object for test communication
object OrderTracker {
  var order: ArrayBuffer[String] = ArrayBuffer.empty
}

// Helper class for wireable construction test - must be top-level for macro
class OrderTrackingResource extends AutoCloseable {
  private val order   = OrderTracker.order
  var closed: Boolean = false
  def use(): Unit     = {
    if (closed) throw new IllegalStateException("Resource already closed!")
    order += "use"
  }
  def close(): Unit = {
    closed = true
    order += "close"
  }
}
