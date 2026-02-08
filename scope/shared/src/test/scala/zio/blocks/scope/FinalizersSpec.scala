package zio.blocks.scope

import zio.test._
import zio.blocks.scope.internal.Finalizers

import scala.collection.mutable

object FinalizersSpec extends ZIOSpecDefault {

  def spec = suite("Finalizers")(
    test("run in LIFO order") {
      val order      = mutable.Buffer[Int]()
      val finalizers = new Finalizers
      finalizers.add(order += 1)
      finalizers.add(order += 2)
      finalizers.add(order += 3)
      finalizers.runAll()
      assertTrue(order.toList == List(3, 2, 1))
    },
    test("exception in finalizer is returned in Chunk") {
      val finalizers = new Finalizers
      finalizers.add(throw new RuntimeException("finalizer boom"))
      val errors = finalizers.runAll()
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
    test("all finalizers run even if one throws") {
      val order      = mutable.Buffer[Int]()
      val finalizers = new Finalizers
      finalizers.add(order += 1)
      finalizers.add(throw new RuntimeException("boom"))
      finalizers.add(order += 3)
      finalizers.runAll()
      assertTrue(order.toList == List(3, 1))
    },
    test("if block throws and finalizers throw: primary thrown, finalizer errors suppressed") {
      val (scope, close) = Scope.createTestableScope()
      scope.defer(throw new RuntimeException("finalizer1"))
      scope.defer(throw new RuntimeException("finalizer2"))

      val primary = new RuntimeException("primary")
      val result  = try {
        try throw primary
        finally {
          val errors = scope.close()
          if (errors.nonEmpty) errors.foreach(primary.addSuppressed)
        }
        None
      } catch {
        case e: RuntimeException => Some(e)
      }

      assertTrue(
        result.exists(_.getMessage == "primary"),
        result.exists(_.getSuppressed.length == 2),
        result.exists(_.getSuppressed.map(_.getMessage).toSet == Set("finalizer1", "finalizer2"))
      )
    },
    test("if block succeeds and finalizers throw multiple: first thrown, rest suppressed") {
      val (scope, close) = Scope.createTestableScope()
      scope.defer(throw new RuntimeException("finalizer1"))
      scope.defer(throw new RuntimeException("finalizer2"))
      scope.defer(throw new RuntimeException("finalizer3"))

      val result = try {
        close()
        None
      } catch {
        case e: RuntimeException => Some(e)
      }

      assertTrue(
        result.exists(_.getMessage == "finalizer3"),
        result.exists(_.getSuppressed.length == 2),
        result.exists(_.getSuppressed.map(_.getMessage).toSet == Set("finalizer1", "finalizer2"))
      )
    }
  )
}
