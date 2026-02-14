package zio.blocks.scope

import zio.test._

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object PoolConcurrencySpec extends ZIOSpecDefault {

  final class Connection(val id: Int)

  def spec = suite("Pool concurrency (JVM)")(
    test("Pool.shared handles concurrent lease/release without leaking") {
      val created  = new AtomicInteger(0)
      val recycled = new AtomicInteger(0)
      val closed   = new AtomicInteger(0)

      val connectionResource = Resource.acquireRelease {
        new Connection(created.incrementAndGet())
      } { _ =>
        closed.incrementAndGet()
      }

      val poolResource = Pool.shared(SharedResource(connectionResource, (_: Connection) => recycled.incrementAndGet()))
      val leaseIds     = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
      val threads      = 40
      val workerError  = new AtomicReference[Throwable](null)

      Scope.global.scoped { scope =>
        import scope._
        val pool    = allocate(poolResource)
        val barrier = new java.util.concurrent.CyclicBarrier(threads)
        val latch   = new java.util.concurrent.CountDownLatch(threads)

        use(pool) { livePool =>
          (0 until threads).foreach { _ =>
            new Thread(() => {
              try {
                barrier.await()
                Scope.global.scoped { leaseScope =>
                  import leaseScope._
                  val lease = allocate(livePool.lease)
                  use(lease) { c =>
                    leaseIds.add(c.id)
                    ()
                  }
                }
              } catch {
                case t: Throwable =>
                  workerError.compareAndSet(null, t)
                  ()
              } finally latch.countDown()
            }).start()
          }
          latch.await()
        }
      }

      val error = workerError.get()
      if (error ne null) throw error

      import scala.jdk.CollectionConverters._
      val ids = leaseIds.asScala.toList

      assertTrue(
        ids.size == 40,
        recycled.get() == 40,
        closed.get() == created.get(),
        created.get() <= threads
      )
    },
    test("Pool.shared close and lease race is safe and leak-free") {
      val iterations    = 100
      val leasedOutcome = new AtomicInteger(0)
      val closedOutcome = new AtomicInteger(0)

      (0 until iterations).foreach { _ =>
        val created  = new AtomicInteger(0)
        val recycled = new AtomicInteger(0)
        val closed   = new AtomicInteger(0)

        val connectionResource = Resource.acquireRelease {
          new Connection(created.incrementAndGet())
        } { _ =>
          closed.incrementAndGet()
        }

        val poolResource =
          Pool.shared(SharedResource(connectionResource, (_: Connection) => recycled.incrementAndGet()))
        val raceDone    = new java.util.concurrent.CountDownLatch(1)
        val raceBarrier = new java.util.concurrent.CyclicBarrier(2)
        val closeSignal = new java.util.concurrent.CountDownLatch(1)
        val raceError   = new AtomicReference[Throwable](null)
        val raceOutcome = new AtomicReference[String]("")

        Scope.global.scoped { scope =>
          import scope._
          val pool = allocate(poolResource)

          use(pool) { livePool =>
            Scope.global.scoped { leaseScope =>
              import leaseScope._
              val lease = allocate(livePool.lease)
              use(lease)(_ => ())
            }

            new Thread(() => {
              try {
                raceBarrier.await()
                closeSignal.await()
                Scope.global.scoped { leaseScope =>
                  import leaseScope._
                  val lease = allocate(livePool.lease)
                  use(lease)(_ => ())
                }
                raceOutcome.set("leased")
              } catch {
                case _: IllegalStateException => raceOutcome.set("closed")
                case t: Throwable             =>
                  raceError.compareAndSet(null, t)
                  ()
              } finally raceDone.countDown()
            }).start()

            raceBarrier.await()
            closeSignal.countDown()
          }
        }

        raceDone.await()
        val error = raceError.get()
        if (error ne null) throw error

        raceOutcome.get() match {
          case "leased" => leasedOutcome.incrementAndGet()
          case "closed" => closedOutcome.incrementAndGet()
          case other    => throw new IllegalStateException("unexpected race outcome: " + other)
        }

        if (closed.get() != created.get())
          throw new AssertionError(s"leak detected: created=${created.get()} closed=${closed.get()}")
      }

      assertTrue(leasedOutcome.get() + closedOutcome.get() == iterations)
    }
  )
}
