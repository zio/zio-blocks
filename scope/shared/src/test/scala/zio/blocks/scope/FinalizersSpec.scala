package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

import scala.collection.mutable

object FinalizersSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  def spec = suite("Finalizers")(
    test("run in LIFO order") {
      val order      = mutable.Buffer[Int]()
      val parent     = Scope.global
      val config     = Config(true)
      val finalizers = new Finalizers
      finalizers.add(order += 1)
      finalizers.add(order += 2)
      finalizers.add(order += 3)
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
      closeable.close()
      assertTrue(order.toList == List(3, 2, 1))
    },
    test("exception in finalizer is returned in Chunk") {
      val parent     = Scope.global
      val config     = Config(true)
      val finalizers = new Finalizers
      finalizers.add(throw new RuntimeException("finalizer boom"))
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

      val errors = closeable.close()
      assertTrue(
        errors.size == 1,
        errors.headOption.exists(_.getMessage == "finalizer boom")
      )
    },
    test("isClosed returns correct state") {
      val finalizers = new Finalizers
      assertTrue(!finalizers.isClosed)
      finalizers.runAll()
      assertTrue(finalizers.isClosed)
    },
    test("size returns correct count") {
      val finalizers = new Finalizers
      assertTrue(finalizers.size == 0)
      finalizers.add(())
      assertTrue(finalizers.size == 1)
      finalizers.add(())
      assertTrue(finalizers.size == 2)
    },
    test("add after close is ignored") {
      val finalizers = new Finalizers
      finalizers.runAll()
      finalizers.add(())
      assertTrue(finalizers.size == 0)
    },
    test("runAll is idempotent") {
      val finalizers = new Finalizers
      var count      = 0
      finalizers.add(count += 1)
      finalizers.runAll()
      finalizers.runAll()
      assertTrue(count == 1)
    },
    test("closeOrThrow throws first exception") {
      val parent     = Scope.global
      val config     = Config(true)
      val finalizers = new Finalizers
      finalizers.add(throw new RuntimeException("boom"))
      val closeable = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)

      val threw = try {
        closeable.closeOrThrow()
        false
      } catch {
        case e: RuntimeException => e.getMessage == "boom"
      }
      assertTrue(threw)
    },
    test("closeOrThrow does not throw on success") {
      val parent     = Scope.global
      val config     = Config(true)
      val finalizers = new Finalizers
      val closeable  = Scope.makeCloseable[Config, Scope.Global](parent, Context(config), finalizers)
      closeable.closeOrThrow()
      assertTrue(true)
    }
  )
}
