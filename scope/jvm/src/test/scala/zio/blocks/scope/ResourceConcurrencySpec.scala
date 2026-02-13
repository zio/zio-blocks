package zio.blocks.scope

import java.util.concurrent.atomic.AtomicInteger
import zio.test._

/**
 * JVM-only concurrency tests for Resource.shared.
 *
 * These tests use java.util.concurrent classes (CyclicBarrier, CountDownLatch,
 * ConcurrentLinkedQueue) which are not available on Scala.js.
 */
object ResourceConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("Resource concurrency (JVM)")(
    test("Resource.shared is thread-safe under concurrent makes") {
      val counter      = new AtomicInteger(0)
      val closeCounter = new AtomicInteger(0)
      val resource     = Resource.shared[Int] { finalizer =>
        finalizer.defer { closeCounter.incrementAndGet(); () }
        counter.incrementAndGet()
      }
      val results = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
      Scope.global.scoped { scope =>
        val barrier = new java.util.concurrent.CyclicBarrier(20)
        val latch   = new java.util.concurrent.CountDownLatch(20)
        (0 until 20).foreach { _ =>
          new Thread(() => {
            barrier.await()
            results.add(resource.make(scope))
            latch.countDown()
          }).start()
        }
        latch.await()
      }
      import scala.jdk.CollectionConverters._
      val allResults = results.asScala.toList
      assertTrue(allResults.forall(_ == 1), counter.get() == 1, closeCounter.get() == 1)
    }
  )
}
