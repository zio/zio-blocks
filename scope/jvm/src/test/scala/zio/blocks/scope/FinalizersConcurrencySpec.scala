/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.scope

import zio.ZIO
import zio.test._
import zio.blocks.scope.internal.Finalizers

import java.util.concurrent.{CountDownLatch, CyclicBarrier, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

object FinalizersConcurrencySpec extends ZIOSpecDefault {

  def spec = suite("Finalizers Concurrency")(
    test("concurrent cancel of non-head nodes does not corrupt list") {
      // Validates that the Treiber stack with volatile cancelled flags handles
      // concurrent cancellation correctly: cancelled nodes are skipped by runAll.
      ZIO.succeed {
        val iterations = 5000
        val failures   = new AtomicInteger(0)

        (0 until iterations).foreach { _ =>
          val finalizers = new Finalizers
          val ran        = new AtomicInteger(0)

          val handles = (0 until 20).map { _ =>
            finalizers.add(ran.incrementAndGet())
          }
          ran.set(0)

          val cancelCount = 18
          val barrier     = new CyclicBarrier(cancelCount)
          val latch       = new CountDownLatch(cancelCount)
          (1 to cancelCount).foreach { i =>
            new Thread(new Runnable {
              def run(): Unit =
                try {
                  barrier.await()
                  handles(i).cancel()
                } finally latch.countDown()
            }).start()
          }
          latch.await(30, TimeUnit.SECONDS)

          finalizers.runAll()
          if (ran.get() != 2) failures.incrementAndGet()
        }
        assertTrue(failures.get() == 0)
      }
    },
    test("concurrent cancel + add does not lose new nodes") {
      // Validates that concurrent cancellation (setting volatile flag + head CAS)
      // does not interfere with concurrent Treiber stack pushes.
      ZIO.succeed {
        val iterations = 5000
        val failures   = new AtomicInteger(0)

        (0 until iterations).foreach { _ =>
          val finalizers = new Finalizers
          val ran        = new AtomicInteger(0)

          val handles = (0 until 10).map { _ =>
            finalizers.add(ran.incrementAndGet())
          }

          val barrier = new CyclicBarrier(9)
          val latch   = new CountDownLatch(9)

          (1 until 9).foreach { i =>
            new Thread(new Runnable {
              def run(): Unit =
                try {
                  barrier.await()
                  handles(i).cancel()
                } finally latch.countDown()
            }).start()
          }
          new Thread(new Runnable {
            def run(): Unit =
              try {
                barrier.await()
                (0 until 10).foreach(_ => finalizers.add(ran.incrementAndGet()))
              } finally latch.countDown()
          }).start()

          latch.await(30, TimeUnit.SECONDS)

          ran.set(0)
          finalizers.runAll()
          // 2 uncancelled originals + 10 new = 12
          if (ran.get() != 12) failures.incrementAndGet()
        }
        assertTrue(failures.get() == 0)
      }
    },
    test("concurrent open/close on a scope races parent closure with child open") {
      // Validates that open() either succeeds or throws IllegalStateException
      // if the parent closed first (TOCTOU check). When open() succeeds, the
      // child's finalizer should run at most once (via parent close or explicit close).
      ZIO.succeed {
        val iterations = 5000
        val failures   = new AtomicInteger(0)

        (0 until iterations).foreach { _ =>
          val childRan    = new AtomicInteger(0)
          val parentFins  = new Finalizers
          val parentScope =
            new Scope.Child[Scope](Scope.global, parentFins, owner = null, unowned = true)

          val barrier = new CyclicBarrier(2)
          val latch   = new CountDownLatch(2)

          @volatile var opened = false
          @volatile var threw  = false

          new Thread(new Runnable {
            def run(): Unit =
              try {
                barrier.await()
                try {
                  val os = parentScope.open().asInstanceOf[Scope.OpenScope]
                  os.scope.defer(childRan.incrementAndGet())
                  opened = true
                  os.close()
                  ()
                } catch { case _: IllegalStateException => threw = true }
              } finally latch.countDown()
          }).start()

          new Thread(new Runnable {
            def run(): Unit =
              try {
                barrier.await()
                parentFins.runAll()
                ()
              } finally latch.countDown()
          }).start()

          latch.await(30, TimeUnit.SECONDS)

          // Either open() threw (parent closed first) or it succeeded.
          // If opened, childRan is 0 (parent closed child before defer) or
          // 1 (defer registered before close ran it). Never > 1 (double-run).
          if (opened && childRan.get() > 1) failures.incrementAndGet()
          if (!opened && !threw) failures.incrementAndGet()
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
          new Thread(new Runnable {
            def run(): Unit =
              try {
                barrier.await()
                (0 until addsPerThread).foreach { _ =>
                  finalizers.add(counter.incrementAndGet())
                }
              } finally latch.countDown()
          })
        }

        workers.foreach(_.start())
        latch.await(30, TimeUnit.SECONDS)

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
          new Thread(new Runnable {
            def run(): Unit =
              try {
                barrier.await()
                finalizers.runAll()
                ()
              } finally latch.countDown()
          })
        }

        workers.foreach(_.start())
        latch.await(30, TimeUnit.SECONDS)

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
            new Thread(new Runnable {
              def run(): Unit =
                try {
                  barrier.await()
                  (0 until 10).foreach { _ =>
                    finalizers.add(counter.incrementAndGet())
                  }
                } finally latch.countDown()
            })
          }

          val closer = new Thread(new Runnable {
            def run(): Unit =
              try {
                barrier.await()
                Thread.sleep(1)
                finalizers.runAll()
                ()
              } finally latch.countDown()
          })

          adders.foreach(_.start())
          closer.start()
          latch.await(30, TimeUnit.SECONDS)

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
            new Thread(new Runnable {
              def run(): Unit =
                try {
                  barrier.await()
                  val result = resource.make(scope)
                  results.add(result)
                  ()
                } finally latch.countDown()
            })
          }
          workers.foreach(_.start())
          latch.await(30, TimeUnit.SECONDS)
        }
        import scala.jdk.CollectionConverters._
        val allResults = results.asScala.toList
        assertTrue(
          allResults.forall(_ == 1),
          counter.get() == 1
        )
      }
    },
    test("defer on closed scope returns Noop without running finalizer") {
      ZIO.succeed {
        val finalizers = Finalizers.closed
        val ran        = new AtomicInteger(0)
        val handle     = finalizers.addFn(() => ran.incrementAndGet())
        assertTrue(handle eq DeferHandle.Noop, ran.get() == 0)
      }
    },
    test("add on closed scope returns Noop without running finalizer") {
      ZIO.succeed {
        val finalizers = Finalizers.closed
        val ran        = new AtomicInteger(0)
        val handle     = finalizers.add(ran.incrementAndGet())
        assertTrue(handle eq DeferHandle.Noop, ran.get() == 0)
      }
    }
  )
}
