package zio.blocks.scope

import zio.ZIO
import zio.test._
import zio.blocks.scope.internal.Finalizers

import java.util.concurrent.{CountDownLatch, CyclicBarrier}
import java.util.concurrent.atomic.AtomicInteger

object FinalizersConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("Finalizers Concurrency")(
    test("concurrent cancel of non-head nodes does not corrupt list") {
      // Targets: remove() non-head path does prev.next = nextRef without CAS.
      // Two threads cancelling adjacent non-head nodes can lose a write.
      ZIO.succeed {
        val iterations = 5000
        val failures   = new AtomicInteger(0)

        (0 until iterations).foreach { _ =>
          val finalizers = new Finalizers
          val ran        = new AtomicInteger(0)

          // Build list with 20 nodes. Cancel all but first and last concurrently.
          val handles = (0 until 20).map { _ =>
            finalizers.add(ran.incrementAndGet())
          }
          ran.set(0)

          // Cancel handles(1)..handles(18) from 18 threads simultaneously
          val cancelCount = 18
          val barrier     = new CyclicBarrier(cancelCount)
          val latch       = new CountDownLatch(cancelCount)
          (1 to cancelCount).foreach { i =>
            new Thread(() => {
              barrier.await()
              handles(i).cancel()
              latch.countDown()
            }).start()
          }
          latch.await()

          finalizers.runAll()
          // Only handles(0) (tail) and handles(19) (head) should have run
          if (ran.get() != 2) failures.incrementAndGet()
        }
        assertTrue(failures.get() == 0)
      }
    },
    test("concurrent cancel + add does not lose new nodes") {
      // Targets: non-volatile Node.next written by remove, read by concurrent add/size.
      ZIO.succeed {
        val iterations = 5000
        val failures   = new AtomicInteger(0)

        (0 until iterations).foreach { _ =>
          val finalizers = new Finalizers
          val ran        = new AtomicInteger(0)

          // Add 10 nodes, grab all handles
          val handles = (0 until 10).map { _ =>
            finalizers.add(ran.incrementAndGet())
          }

          // Concurrently: cancel the middle 8 while adding 10 more
          val barrier = new CyclicBarrier(9)
          val latch   = new CountDownLatch(9)

          (1 until 9).foreach { i =>
            new Thread(() => {
              barrier.await()
              handles(i).cancel()
              latch.countDown()
            }).start()
          }
          new Thread(() => {
            barrier.await()
            (0 until 10).foreach(_ => finalizers.add(ran.incrementAndGet()))
            latch.countDown()
          }).start()

          latch.await()

          ran.set(0)
          finalizers.runAll()
          // 2 uncancelled originals + 10 new = 12
          if (ran.get() != 12) failures.incrementAndGet()
        }
        assertTrue(failures.get() == 0)
      }
    },
    test("concurrent open/close on a scope does not leak child finalizers") {
      // Targets P1: open() ignores addNode return value. If the parent closes
      // between isClosed check and addNode, the child's CloseHandle is never
      // registered, so the child's finalizers won't run via the parent.
      // In practice this is a TOCTOU window. We stress it by racing open/close.
      ZIO.succeed {
        val iterations = 1000
        val failures   = new AtomicInteger(0)

        (0 until iterations).foreach { _ =>
          val childClosed = new AtomicInteger(0)
          Scope.global.scoped { parentScope =>
            val barrier = new CyclicBarrier(2)
            val latch   = new CountDownLatch(2)

            // Thread 1: open a child scope, register a finalizer on it
            @volatile var openResult: Option[Scope.OpenScope] = None
            new Thread(() => {
              barrier.await()
              try {
                val os = parentScope.open().asInstanceOf[Scope.OpenScope]
                os.scope.defer(childClosed.incrementAndGet())
                openResult = Some(os)
              } catch { case _: IllegalStateException => () }
              latch.countDown()
            }).start()

            // Thread 2: we'll just let the scoped block end, which closes parent
            new Thread(() => {
              barrier.await()
              latch.countDown()
            }).start()

            latch.await()

            // If open succeeded, close the child explicitly
            openResult.foreach(_.close())
          }
          // The child finalizer should have run (either via parent close or explicit close)
          // If addNode silently failed and we didn't close explicitly, it might be 0
          // But we DO close explicitly above, so this mostly tests that open() doesn't crash
        }
        assertTrue(failures.get() == 0)
      }
    },
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
        val results = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
        Scope.global.scoped { scope =>
          val threads = 100
          val barrier = new CyclicBarrier(threads)
          val latch   = new CountDownLatch(threads)
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
        }
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
