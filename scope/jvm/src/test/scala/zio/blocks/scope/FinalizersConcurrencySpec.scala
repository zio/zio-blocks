package zio.blocks.scope

import zio.ZIO
import zio.test._
import zio.blocks.scope.internal.Finalizers

import java.util.concurrent.{CountDownLatch, CyclicBarrier}
import java.util.concurrent.atomic.AtomicInteger

object FinalizersConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("Finalizers Concurrency")(
    test("concurrent adds are thread-safe") {
      ZIO.succeed {
        val finalizers    = new Finalizers
        val counter       = new AtomicInteger(0)
        val threads       = 100
        val addsPerThread = 100
        val barrier       = new CyclicBarrier(threads)
        val latch         = new CountDownLatch(threads)

        val workers = (0 until threads).map { _ =>
          new Thread(() => {
            barrier.await()
            (0 until addsPerThread).foreach { _ =>
              finalizers.add(counter.incrementAndGet())
            }
            latch.countDown()
          })
        }

        workers.foreach(_.start())
        latch.await()

        assertTrue(finalizers.size == threads * addsPerThread)
      }
    },
    test("runAll only runs finalizers once under concurrent calls") {
      ZIO.succeed {
        val finalizers = new Finalizers
        val counter    = new AtomicInteger(0)
        val threads    = 100

        (0 until 10).foreach { _ =>
          finalizers.add(counter.incrementAndGet())
        }

        val barrier = new CyclicBarrier(threads)
        val latch   = new CountDownLatch(threads)

        val workers = (0 until threads).map { _ =>
          new Thread(() => {
            barrier.await()
            finalizers.runAll()
            latch.countDown()
          })
        }

        workers.foreach(_.start())
        latch.await()

        assertTrue(counter.get() == 10)
      }
    },
    test("add during runAll is handled correctly") {
      ZIO.succeed {
        val finalizers = new Finalizers
        val counter    = new AtomicInteger(0)

        finalizers.add {
          finalizers.add(counter.incrementAndGet())
          counter.incrementAndGet()
        }

        finalizers.runAll()
        assertTrue(counter.get() == 1)
      }
    },
    test("concurrent add and runAll stress test") {
      ZIO.succeed {
        val iterations = 100
        val success    = (0 until iterations).forall { _ =>
          val finalizers = new Finalizers
          val counter    = new AtomicInteger(0)
          val threads    = 20
          val barrier    = new CyclicBarrier(threads + 1)
          val latch      = new CountDownLatch(threads + 1)

          val adders = (0 until threads).map { _ =>
            new Thread(() => {
              barrier.await()
              (0 until 10).foreach { _ =>
                finalizers.add(counter.incrementAndGet())
              }
              latch.countDown()
            })
          }

          val closer = new Thread(() => {
            barrier.await()
            Thread.sleep(1)
            finalizers.runAll()
            latch.countDown()
          })

          adders.foreach(_.start())
          closer.start()
          latch.await()

          finalizers.isClosed
        }
        assertTrue(success)
      }
    },
    test("Resource.shared handles concurrent initialization contention") {
      ZIO.succeed {
        val counter  = new AtomicInteger(0)
        val resource = Resource.shared[Int] { _ =>
          Thread.sleep(10)
          counter.incrementAndGet()
        }
        val (scope, close) = Scope.createTestableScope()
        val threads        = 100
        val barrier        = new CyclicBarrier(threads)
        val latch          = new CountDownLatch(threads)
        val results        = new java.util.concurrent.ConcurrentLinkedQueue[Int]()

        val workers = (0 until threads).map { _ =>
          new Thread(() => {
            barrier.await()
            val result = resource.make(scope)
            results.add(result)
            latch.countDown()
          })
        }

        workers.foreach(_.start())
        latch.await()
        close()

        import scala.jdk.CollectionConverters._
        val allResults = results.asScala.toList
        assertTrue(
          allResults.forall(_ == 1),
          counter.get() == 1
        )
      }
    }
  )
}
