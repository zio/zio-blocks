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

package zio.blocks.streams

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncReaderJvmSpec extends StreamsBaseSpec {
  private abstract class IntSyncReader extends Reader.SyncReader[Int] {
    final override def jvmType: JvmType                                  = JvmType.Int
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      read[AnyVal](sentinel).asInstanceOf[Number].longValue()
  }

  private abstract class IntAsyncReader extends Reader.AsyncReader[Int] {
    final override def jvmType: JvmType                                         = JvmType.Int
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      read[AnyVal](sentinel).map(_.asInstanceOf[Number].longValue())
  }

  private final class PendingIntReader(started: CountDownLatch) extends IntAsyncReader {
    val value                                 = new Completer[Int]
    val closes                                = new AtomicInteger(0)
    def close(): Async[Unit]                  = Async.succeed { closes.incrementAndGet(); () }
    def isClosed: Async[Boolean]              = Async.succeed(closes.get() != 0)
    def readable(): Async[Boolean]            = Async.succeed(false)
    def read[A >: Int](sentinel: A): Async[A] = {
      started.countDown()
      value.map(_.asInstanceOf[A])
    }
  }

  def spec = suite("AsyncReader JVM")(
    test("toSync close wakes a blocked pull with its EOF value") {
      val started = new CountDownLatch(1)
      val async   = new PendingIntReader(started)
      val sync    = async.toSync
      val result  = new AtomicReference[Either[Throwable, Int]]()
      val thread  = new Thread(() =>
        try result.set(Right(sync.readInt(-1L).toInt))
        catch { case t: Throwable => result.set(Left(t)) }
      )
      thread.start()
      val entered = started.await(5, TimeUnit.SECONDS)
      sync.close()
      thread.join(5000)
      assertTrue(entered, !thread.isAlive, result.get() == Right(-1), async.closes.get() == 1)
    },
    test("toSync close cannot deliver its interrupt after the consumer returns") {
      val started          = new CountDownLatch(1)
      val closeCaptured    = new CountDownLatch(1)
      val allowClose       = new CountDownLatch(1)
      val awaitingDelivery = new CountDownLatch(1)
      val interruptCalls   = new AtomicInteger(0)
      val value            = new Completer[Int]
      val result           = new AtomicReference[(Int, Boolean)]()
      val async            = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = { started.countDown(); value.map(_.asInstanceOf[A]) }
      }
      val sync = async.toSync
      val view = sync.asInstanceOf[Reader.AsyncToSyncTestView]
      view.afterCloseCaptureForTest { () => closeCaptured.countDown(); allowClose.await() }
      view.beforeAwaitCloseInterruptForTest(() => awaitingDelivery.countDown())
      val consumer = new Thread(() => result.set((sync.readInt(-1L).toInt, Thread.currentThread().isInterrupted))) {
        override def interrupt(): Unit = {
          interruptCalls.incrementAndGet()
          super.interrupt()
        }
      }
      consumer.start()
      val entered = started.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() => sync.close())
      closer.start()
      val captured = closeCaptured.await(5, TimeUnit.SECONDS)
      value.succeed(1)
      val consumerWaitedForDelivery = awaitingDelivery.await(5, TimeUnit.SECONDS)
      allowClose.countDown()
      consumer.join(5000); closer.join(5000)
      assertTrue(
        entered,
        captured,
        consumerWaitedForDelivery,
        interruptCalls.get() == 1,
        !consumer.isAlive,
        !closer.isAlive,
        result.get() == ((-1, false))
      )
    },
    test("toSync permits an eager source method to close its own adapter") {
      val adapter = new AtomicReference[Reader.SyncReader[Int]]()
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = {
          adapter.get().close()
          Async.succeed(1.asInstanceOf[A])
        }
      }
      val sync = async.toSync
      adapter.set(sync)
      val result = new AtomicReference[Long]()
      val thread = new Thread(() => result.set(sync.readInt(-1L)))
      thread.start()
      thread.join(5000)
      assertTrue(!thread.isAlive, result.get() == -1L, sync.isClosed)
    },
    test("toSync suppresses an eager source failure after a reentrant close") {
      val adapter = new AtomicReference[Reader.SyncReader[Int]]()
      val failure = new RuntimeException("stale")
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = {
          adapter.get().close()
          throw failure
        }
      }
      val sync = async.toSync
      adapter.set(sync)
      assertTrue(sync.readInt(-1L) == -1L, sync.isClosed)
    },
    test("toSync permits a source poll to close its own adapter") {
      val adapter = new AtomicReference[Reader.SyncReader[Int]]()
      val polled  = new CountDownLatch(1)
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = new Pollable[A] {
          def poll(onComplete: Runnable): Async[A] = {
            polled.countDown()
            adapter.get().close()
            Async.succeed(1.asInstanceOf[A])
          }
        }
      }
      val sync = async.toSync
      adapter.set(sync)
      val result = new AtomicReference[Long]()
      val thread = new Thread(() => result.set(sync.readInt(-1L)))
      thread.start()
      val entered = polled.await(5, TimeUnit.SECONDS)
      thread.join(5000)
      assertTrue(entered, !thread.isAlive, result.get() == -1L, sync.isClosed)
    },
    test("toSync permits a source poll to join an externally claimed close") {
      val adapter        = new AtomicReference[Reader.SyncReader[Int]]()
      val pollStarted    = new CountDownLatch(1)
      val allowSelfClose = new CountDownLatch(1)
      val async          = new IntAsyncReader {
        def close(): Async[Unit]                  = { allowSelfClose.countDown(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = new Pollable[A] {
          def poll(onComplete: Runnable): Async[A] = {
            pollStarted.countDown()
            allowSelfClose.await()
            adapter.get().close()
            Async.succeed(1.asInstanceOf[A])
          }
        }
      }
      val sync = async.toSync
      adapter.set(sync)
      val result = new AtomicReference[Long]()
      val reader = new Thread(() => result.set(sync.readInt(-1L)))
      reader.start()
      val entered = pollStarted.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() => sync.close())
      closer.start()
      reader.join(5000); closer.join(5000)
      assertTrue(entered, !reader.isAlive, !closer.isAlive, result.get() == -1L, sync.isClosed)
    },
    test("toSync reentrant close replays source-poll cancellation cleanup failure") {
      val adapter = new AtomicReference[Reader.SyncReader[Int]]()
      val failure = new RuntimeException("cleanup")
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          Async.bracketAsync(
            () => Async.succeed(()),
            (_: Unit) =>
              new Pollable[A] {
                def poll(onComplete: Runnable): Async[A] = { adapter.get().close(); this }
              },
            (_: Unit) => Async.fail(failure)
          )
      }
      val sync = async.toSync
      adapter.set(sync)
      val result = new AtomicReference[Long]()
      val reader = new Thread(() => result.set(sync.readInt(-1L)))
      reader.start()
      reader.join(5000)
      val closeFailure = try { sync.close(); null }
      catch { case cause: Throwable => cause }
      assertTrue(!reader.isAlive, result.get() == -1L, closeFailure eq failure)
    },
    test("toSync external interruption cancels and restores interrupt status") {
      val started = new CountDownLatch(1)
      val async   = new PendingIntReader(started)
      val sync    = async.toSync
      val result  = new AtomicReference[(Boolean, Boolean)]()
      val thread  = new Thread(() =>
        try { sync.readInt(-1L); result.set((false, Thread.currentThread().isInterrupted)) }
        catch {
          case _: InterruptedException => result.set((true, Thread.currentThread().isInterrupted))
        }
      )
      thread.start()
      val entered = started.await(5, TimeUnit.SECONDS)
      thread.interrupt()
      thread.join(5000)
      assertTrue(entered, !thread.isAlive, result.get() == ((true, true)))
    },
    test("toSync interruption retains serialization until cancellation cleanup completes") {
      val useStarted     = new CountDownLatch(1)
      val cleanupStarted = new CountDownLatch(1)
      val cleanupDone    = new Completer[Unit]
      val calls          = new AtomicInteger(0)
      val async          = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] =
          if (calls.incrementAndGet() == 1)
            Async.bracketAsync(
              () => Async.succeed(()),
              (_: Unit) =>
                new Pollable[A] {
                  def poll(onComplete: Runnable): Async[A] = { useStarted.countDown(); this }
                },
              (_: Unit) => { cleanupStarted.countDown(); cleanupDone }
            )
          else Async.succeed(2.asInstanceOf[A])
      }
      val sync        = async.toSync
      val interrupted = new AtomicReference[Boolean](false)
      val first       = new Thread(() =>
        try { sync.readInt(-1L); () }
        catch { case _: InterruptedException => interrupted.set(true) }
      )
      val secondResult = new AtomicReference[Long]()
      val second       = new Thread(() => secondResult.set(sync.readInt(-1L)))
      first.start()
      val entered = useStarted.await(5, TimeUnit.SECONDS)
      first.interrupt()
      val cleaning = cleanupStarted.await(5, TimeUnit.SECONDS)
      second.start()
      Thread.sleep(20)
      val serializedBeforeSecondInterrupt = calls.get() == 1
      first.interrupt()
      Thread.sleep(20)
      val serializedAfterSecondInterrupt = calls.get() == 1
      cleanupDone.succeed(())
      first.join(5000); second.join(5000)
      assertTrue(
        entered,
        cleaning,
        serializedBeforeSecondInterrupt,
        serializedAfterSecondInterrupt,
        !first.isAlive,
        !second.isAlive,
        interrupted.get(),
        secondResult.get() == 2L
      )
    },
    test("toSync serializes concurrent consumers") {
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val calls   = new AtomicInteger(0)
      val active  = new AtomicInteger(0)
      val maximum = new AtomicInteger(0)
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.deferCancelable(
          () => {
            calls.incrementAndGet()
            val now = active.incrementAndGet()
            maximum.updateAndGet(previous => math.max(previous, now))
            entered.countDown()
            release.await()
            active.decrementAndGet()
            1.asInstanceOf[A]
          },
          () => release.countDown()
        )
      }
      val sync = async.toSync
      val t1   = new Thread(() => { sync.readInt(-1L); () })
      val t2   = new Thread(() => { sync.readInt(-1L); () })
      t1.start()
      val firstEntered = entered.await(5, TimeUnit.SECONDS)
      t2.start()
      val onlyOneBeforeRelease = calls.get() == 1
      release.countDown()
      t1.join(5000); t2.join(5000)
      assertTrue(firstEntered, onlyOneBeforeRelease, !t1.isAlive, !t2.isAlive, calls.get() == 2, maximum.get() == 1)
    },
    test("toSync close can wake an eager source method before it returns Async") {
      val entered = new CountDownLatch(1)
      val wake    = new CountDownLatch(1)
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = { wake.countDown(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = {
          entered.countDown()
          wake.await()
          Async.succeed(1.asInstanceOf[A])
        }
      }
      val sync   = async.toSync
      val result = new AtomicReference[Either[Throwable, Long]]()
      val reader = new Thread(() =>
        try result.set(Right(sync.readInt(-1L)))
        catch { case t: Throwable => result.set(Left(t)) }
      )
      reader.start()
      val blocked = entered.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() => sync.close())
      closer.start()
      reader.join(5000); closer.join(5000)
      assertTrue(blocked, !reader.isAlive, !closer.isAlive, result.get() == Right(-1L))
    },
    test("toSync close suppresses an eager stale source failure") {
      val entered = new CountDownLatch(1)
      val wake    = new CountDownLatch(1)
      val failure = new RuntimeException("stale")
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = { wake.countDown(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = {
          entered.countDown()
          wake.await()
          Async.fail(failure)
        }
      }
      val sync   = async.toSync
      val result = new AtomicReference[Either[Throwable, Long]]()
      val reader = new Thread(() =>
        try result.set(Right(sync.readInt(-1L)))
        catch { case t: Throwable => result.set(Left(t)) }
      )
      reader.start()
      val blocked = entered.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() => sync.close())
      closer.start()
      reader.join(5000); closer.join(5000)
      assertTrue(blocked, !reader.isAlive, !closer.isAlive, result.get() == Right(-1L))
    },
    test("toSync close awaits active operation cancellation cleanup") {
      val useStarted     = new CountDownLatch(1)
      val cleanupStarted = new CountDownLatch(1)
      val cleanupDone    = new Completer[Unit]
      val async          = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) =>
            new Pollable[A] {
              def poll(onComplete: Runnable): Async[A] = { useStarted.countDown(); this }
            },
          (_: Unit) => { cleanupStarted.countDown(); cleanupDone }
        )
      }
      val sync   = async.toSync
      val reader = new Thread(() => { sync.readInt(-1L); () })
      reader.start()
      val entered = useStarted.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() => sync.close())
      closer.start()
      val cleaning = cleanupStarted.await(5, TimeUnit.SECONDS)
      Thread.sleep(20)
      val closeWaited = closer.isAlive
      cleanupDone.succeed(())
      reader.join(5000); closer.join(5000)
      assertTrue(entered, cleaning, closeWaited, !reader.isAlive, !closer.isAlive)
    },
    test("toSync starts active cancellation before interrupting its consumer") {
      val useStarted                  = new CountDownLatch(1)
      val cleanupStarted              = new CountDownLatch(1)
      val cleanupStarts               = new AtomicInteger(0)
      val cancellationBeforeInterrupt = new AtomicBoolean(false)
      val async                       = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) =>
            new Pollable[A] {
              def poll(onComplete: Runnable): Async[A] = { useStarted.countDown(); this }
            },
          (_: Unit) =>
            Async.succeed {
              cleanupStarts.incrementAndGet()
              cleanupStarted.countDown()
              ()
            }
        )
      }
      val sync   = async.toSync
      val reader = new Thread(() => { sync.readInt(-1L); () }) {
        override def interrupt(): Unit = {
          cancellationBeforeInterrupt.set(cleanupStarted.await(5, TimeUnit.SECONDS))
          super.interrupt()
        }
      }
      reader.start()
      val entered = useStarted.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() => sync.close())
      closer.start()
      reader.join(5000); closer.join(5000)
      assertTrue(
        entered,
        cancellationBeforeInterrupt.get(),
        cleanupStarts.get() == 1,
        !reader.isAlive,
        !closer.isAlive
      )
    },
    test("toSync drives source close when read cancellation cleanup depends on it") {
      val useStarted     = new CountDownLatch(1)
      val closeDriven    = new Completer[Unit]
      val closes         = new AtomicInteger(0)
      val cleanupStarted = new AtomicInteger(0)
      val async          = new IntAsyncReader {
        def close(): Async[Unit] = Async.succeed {
          closes.incrementAndGet()
          closeDriven.succeed(())
          ()
        }
        def isClosed: Async[Boolean]              = Async.succeed(closes.get() != 0)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) =>
            new Pollable[A] {
              def poll(onComplete: Runnable): Async[A] = { useStarted.countDown(); this }
            },
          (_: Unit) => {
            cleanupStarted.incrementAndGet()
            closeDriven
          }
        )
      }
      val sync   = async.toSync
      val reader = new Thread(() => { sync.readInt(-1L); () })
      reader.start()
      val entered = useStarted.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() => sync.close())
      closer.start()
      reader.join(5000); closer.join(5000)
      assertTrue(
        entered,
        !reader.isAlive,
        !closer.isAlive,
        closes.get() == 1,
        cleanupStarted.get() == 1,
        sync.isClosed
      )
    },
    test("toSync close propagates immediate cancellation cleanup failure without hanging") {
      val useStarted = new CountDownLatch(1)
      val failure    = new RuntimeException("cleanup")
      val async      = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) =>
            new Pollable[A] {
              def poll(onComplete: Runnable): Async[A] = { useStarted.countDown(); this }
            },
          (_: Unit) => Async.fail(failure)
        )
      }
      val sync        = async.toSync
      val reader      = new Thread(() => { sync.readInt(-1L); () })
      val closeResult = new AtomicReference[Throwable]()
      reader.start()
      val entered = useStarted.await(5, TimeUnit.SECONDS)
      val closer  = new Thread(() =>
        try sync.close()
        catch { case cause: Throwable => closeResult.set(cause) }
      )
      closer.start()
      reader.join(5000); closer.join(5000)
      assertTrue(entered, !reader.isAlive, !closer.isAlive, closeResult.get() eq failure)
    },
    test("toSync repeated close replays an immediate close failure") {
      val failure = new RuntimeException("close")
      val async   = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.fail(failure)
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val sync  = async.toSync
      val first = try { sync.close(); null }
      catch { case cause: Throwable => cause }
      val second = try { sync.close(); null }
      catch { case cause: Throwable => cause }
      assertTrue(first eq failure, second eq failure)
    },
    test("toSync permits source close to reenter its own adapter") {
      val adapter = new AtomicReference[Reader.SyncReader[Int]]()
      val closes  = new AtomicInteger(0)
      val async   = new IntAsyncReader {
        def close(): Async[Unit] = {
          closes.incrementAndGet()
          adapter.get().close()
          Async.succeed(())
        }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val sync = async.toSync
      adapter.set(sync)
      val closer = new Thread(() => sync.close())
      closer.start()
      closer.join(5000)
      sync.close()
      assertTrue(!closer.isAlive, closes.get() == 1, sync.isClosed)
    },
    test("toSync close remains joined when its caller is interrupted") {
      val closeStarted = new CountDownLatch(1)
      val closeDone    = new Completer[Unit]
      val async        = new IntAsyncReader {
        def close(): Async[Unit]                  = { closeStarted.countDown(); closeDone }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val sync   = async.toSync
      val result = new AtomicReference[(Boolean, List[Throwable])]()
      val closer = new Thread(() =>
        try { sync.close(); result.set((false, Nil)) }
        catch {
          case cause: InterruptedException =>
            result.set((Thread.currentThread().isInterrupted, cause.getSuppressed.toList))
        }
      )
      closer.start()
      val entered = closeStarted.await(5, TimeUnit.SECONDS)
      closer.interrupt()
      Thread.sleep(20)
      val waited = closer.isAlive
      closeDone.succeed(())
      closer.join(5000)
      assertTrue(entered, waited, !closer.isAlive, result.get() == ((true, Nil)))
    },
    test("toSync interrupted close keeps interruption primary over close failure") {
      val closeStarted = new CountDownLatch(1)
      val closeDone    = new Completer[Unit]
      val failure      = new RuntimeException("close")
      val async        = new IntAsyncReader {
        def close(): Async[Unit]                  = { closeStarted.countDown(); closeDone }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val sync   = async.toSync
      val result = new AtomicReference[(Throwable, Boolean)]()
      val closer = new Thread(() =>
        try sync.close()
        catch { case cause: Throwable => result.set((cause, Thread.currentThread().isInterrupted)) }
      )
      closer.start()
      val entered = closeStarted.await(5, TimeUnit.SECONDS)
      closer.interrupt()
      closeDone.fail(failure)
      closer.join(5000)
      val thrown = result.get()
      assertTrue(
        entered,
        !closer.isAlive,
        thrown._1.isInstanceOf[InterruptedException],
        thrown._2,
        thrown._1.getSuppressed.toList == List(failure)
      )
    },
    test("toSync external interruption suppresses cancellation cleanup failure") {
      val useStarted = new CountDownLatch(1)
      val failure    = new RuntimeException("cleanup")
      val async      = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.bracketAsync(
          () => Async.succeed(()),
          (_: Unit) =>
            new Pollable[A] {
              def poll(onComplete: Runnable): Async[A] = { useStarted.countDown(); this }
            },
          (_: Unit) => Async.fail(failure)
        )
      }
      val sync   = async.toSync
      val result = new AtomicReference[(Throwable, Boolean)]()
      val reader = new Thread(() =>
        try { sync.readInt(-1L); () }
        catch { case cause: Throwable => result.set((cause, Thread.currentThread().isInterrupted)) }
      )
      reader.start()
      val entered = useStarted.await(5, TimeUnit.SECONDS)
      reader.interrupt()
      reader.join(5000)
      val thrown = result.get()
      assertTrue(
        entered,
        !reader.isAlive,
        thrown._1.isInstanceOf[InterruptedException],
        thrown._2,
        thrown._1.getSuppressed.toList == List(failure)
      )
    },
    test("toSync interruption of a waiting consumer does not start another read") {
      val firstStarted = new CountDownLatch(1)
      val firstValue   = new Completer[Int]
      val calls        = new AtomicInteger(0)
      val async        = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = {
          calls.incrementAndGet()
          firstStarted.countDown()
          firstValue.map(_.asInstanceOf[A])
        }
      }
      val sync         = async.toSync
      val firstResult  = new AtomicReference[Long]()
      val secondResult = new AtomicReference[(Throwable, Boolean)]()
      val first        = new Thread(() => firstResult.set(sync.readInt(-1L)))
      val second       = new Thread(() =>
        try { sync.readInt(-1L); () }
        catch { case cause: Throwable => secondResult.set((cause, Thread.currentThread().isInterrupted)) }
      )
      first.start()
      val entered = firstStarted.await(5, TimeUnit.SECONDS)
      second.start()
      var attempts = 0
      while (second.getState != Thread.State.WAITING && attempts < 1000000) {
        Thread.`yield`()
        attempts += 1
      }
      val waiting = second.getState == Thread.State.WAITING
      second.interrupt()
      second.join(5000)
      firstValue.succeed(1)
      first.join(5000)
      val thrown = secondResult.get()
      assertTrue(
        entered,
        waiting,
        !first.isAlive,
        !second.isAlive,
        calls.get() == 1,
        firstResult.get() == 1L,
        thrown._1.isInstanceOf[InterruptedException],
        thrown._2
      )
    },
    test("toSync propagates open read failure and synchronous close failure") {
      val readFailure  = new RuntimeException("read")
      val closeFailure = new RuntimeException("close")
      val async        = new IntAsyncReader {
        def close(): Async[Unit]                  = throw closeFailure
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.fail(readFailure)
      }
      val sync = async.toSync
      val read = try { sync.readInt(-1L); null }
      catch { case cause: Throwable => cause }
      val firstClose = try { sync.close(); null }
      catch { case cause: Throwable => cause }
      val secondClose = try { sync.close(); null }
      catch { case cause: Throwable => cause }
      assertTrue(read eq readFailure, firstClose eq closeFailure, secondClose eq closeFailure)
    },
    test("concat cancellation closes a tail produced after cancellation") {
      val polled     = new CountDownLatch(1)
      val tailClosed = new CountDownLatch(1)
      final class LateAcquire(reader: Reader[Int]) extends Pollable[Reader[Int]] {
        private var value: Async[Reader[Int]]              = this
        private var observer: Runnable                     = null
        def poll(onComplete: Runnable): Async[Reader[Int]] = synchronized {
          polled.countDown()
          if (value.asInstanceOf[AnyRef] eq this) observer = onComplete
          value
        }
        def succeed(reader: Reader[Int]): Unit = {
          val notify = synchronized {
            value = Async.succeed(reader)
            observer
          }
          if (notify ne null) notify.run()
        }
        override def cancel(): Unit = succeed(reader)
      }
      val tail = new IntSyncReader {
        def close(): Unit                  = tailClosed.countDown()
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = 1.asInstanceOf[A]
      }
      val acquire            = new LateAcquire(tail)
      val reader             = Reader.closed.toAsync.concatAsyncWithJvmType(() => acquire, JvmType.Int)
      val running            = reader.readInt(-1L).start
      val acquisitionStarted = polled.await(5, TimeUnit.SECONDS)
      val cleanup            = Async.cancelWithCleanup(running)
      cleanup.block
      val released = tailClosed.await(5, TimeUnit.SECONDS)
      assertTrue(acquisitionStarted, released)
    },
    test("concat cancellation follows a replacement acquisition pollable") {
      val replacementPolled      = new CountDownLatch(1)
      val cancellations          = new AtomicInteger(0)
      val cancelled              = new CountDownLatch(1)
      val cancellableReplacement = new Pollable[Reader[Int]] {
        def poll(onComplete: Runnable): Async[Reader[Int]] = {
          replacementPolled.countDown()
          this
        }
        override def cancel(): Unit = { cancellations.incrementAndGet(); cancelled.countDown() }
      }
      val initial = new Pollable[Reader[Int]] {
        private var first                                  = true
        def poll(onComplete: Runnable): Async[Reader[Int]] =
          if (first) { first = false; onComplete.run(); cancellableReplacement }
          else throw new IllegalStateException("initial pollable was driven twice")
      }
      val reader  = Reader.closed.toAsync.concatAsyncWithJvmType(() => initial, JvmType.Int)
      val running = reader.readInt(-1L).start
      val entered = replacementPolled.await(5, TimeUnit.SECONDS)
      running.cancel()
      val signalled = cancelled.await(5, TimeUnit.SECONDS)
      assertTrue(entered, signalled, cancellations.get() == 1)
    },
    test("concat close wakes the original read without a child callback") {
      val started    = new CountDownLatch(1)
      val cancelled  = new AtomicBoolean(false)
      val tailCloses = new AtomicInteger(0)
      val tail       = new IntAsyncReader {
        def close(): Async[Unit]                  = { tailCloses.incrementAndGet(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(1.asInstanceOf[A])
      }
      val acquisition = new Pollable[Reader[Int]] {
        def poll(onComplete: Runnable): Async[Reader[Int]] =
          if (cancelled.get()) Async.succeed(tail)
          else { started.countDown(); this }
        override def cancel(): Unit = cancelled.set(true)
      }
      val reader  = Reader.closed.toAsync.concatAsyncWithJvmType(() => acquisition, JvmType.Int)
      val running = reader.readInt(-1L).start
      val entered = started.await(5, TimeUnit.SECONDS)
      reader.close().block
      assertTrue(
        entered,
        running.block == -1L,
        // Cancellation may win before the acquisition exposes `tail`; when
        // it loses that race, the rejected tail is still closed exactly once.
        tailCloses.get() <= 1
      )
    },
    test("concat pollInt close rejects late child success and failures without forcing the tail") {
      final class Observer extends Async.LongStepFold[Unit] {
        var cause: Throwable                                    = null
        var kind                                                = -1
        var trusted                                             = false
        var value                                               = 0L
        def failureLong(error: Throwable): Unit                 = { cause = error; kind = 1 }
        def pendingLong(pollable: Pollable[Long]): Unit         = { val _ = pollable; kind = 2 }
        def successLong(result: Long): Unit                     = { kind = 0; value = result }
        override def trustedFailureLong(error: Throwable): Unit = { cause = error; kind = 1; trusted = true }
      }
      def race(complete: Async.LongStepFold[Unit] => Unit): (Observer, Int, Int) = {
        val closes                          = new AtomicInteger
        var child: Async.LongStepFold[Unit] = null
        var tails                           = 0
        val head                            = new IntAsyncReader {
          def close(): Async[Unit]                  = Async.succeed { closes.incrementAndGet(); () }
          def isClosed: Async[Boolean]              = Async.succeed(false)
          def readable(): Async[Boolean]            = Async.succeed(true)
          def read[A >: Int](sentinel: A): Async[A] = Async.never
          override private[streams] def pollInt(
            sentinel: Long,
            onComplete: Runnable,
            observer: Async.LongStepFold[Unit]
          ): Unit = { val _ = sentinel; val _ = onComplete; child = observer }
        }
        val reader = head.concatReaderWithJvmType(
          () => { tails += 1; Reader.fromChunk(Chunk(1)).toAsync },
          JvmType.Int
        )
        val observer = new Observer
        reader.pollIntPhysical(-7L, () => (), observer)
        reader.close().block
        complete(child)
        (observer, closes.get(), tails)
      }
      val failure  = new RuntimeException("late")
      val success  = race(_.successLong(1L))
      val ordinary = race(_.failureLong(failure))
      val trusted  = race(_.trustedFailureLong(failure))
      assertTrue(
        success._1.kind == 0,
        success._1.value == -7L,
        success._2 == 1,
        success._3 == 0,
        ordinary._1.kind == 0,
        ordinary._1.value == -7L,
        ordinary._2 == 1,
        ordinary._3 == 0,
        trusted._1.kind == 0,
        trusted._1.value == -7L,
        trusted._2 == 1,
        trusted._3 == 0
      )
    },
    test("toAsync close callers share a source close failure") {
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val closes  = new AtomicInteger(0)
      val failure = new RuntimeException("close")
      val source  = new IntSyncReader {
        def close(): Unit = {
          closes.incrementAndGet()
          entered.countDown()
          release.await()
          throw failure
        }
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
      }
      val reader       = source.toAsync
      val first        = reader.close().start
      val closeStarted = entered.await(5, TimeUnit.SECONDS)
      val second       = reader.close().start
      release.countDown()
      val firstFailure = try { first.block; null }
      catch { case cause: Throwable => cause }
      val secondFailure = try { second.block; null }
      catch { case cause: Throwable => cause }
      assertTrue(closeStarted, closes.get() == 1, firstFailure eq failure, secondFailure eq failure)
    },
    test("toAsync close failure still joins an active synchronous pull") {
      val readStarted = new CountDownLatch(1)
      val closeCalled = new CountDownLatch(1)
      val allowReturn = new CountDownLatch(1)
      val failure     = new RuntimeException("close")
      val source      = new IntSyncReader {
        def close(): Unit                  = { closeCalled.countDown(); throw failure }
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = {
          readStarted.countDown()
          allowReturn.await()
          1.asInstanceOf[A]
        }
      }
      val reader       = source.toAsync
      val running      = reader.readInt(-1L).start
      val entered      = readStarted.await(5, TimeUnit.SECONDS)
      val closing      = reader.close().start
      val closeEntered = closeCalled.await(5, TimeUnit.SECONDS)
      Thread.sleep(20)
      val closeWaited = closing.poll(new Runnable { def run(): Unit = () }).isInstanceOf[Pollable[_]]
      allowReturn.countDown()
      val closeFailure = try { closing.block; null }
      catch { case cause: Throwable => cause }
      assertTrue(entered, closeEntered, closeWaited, running.block == -1L, closeFailure eq failure)
    },
    test("toAsync close rejects a stale synchronous pull result") {
      val entered = new CountDownLatch(1)
      val wake    = new CountDownLatch(1)
      val source  = new IntSyncReader {
        def close(): Unit                  = wake.countDown()
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = {
          entered.countDown()
          wake.await()
          1.asInstanceOf[A]
        }
      }
      val reader  = source.toAsync
      val running = reader.readInt(-1L).start
      val blocked = entered.await(5, TimeUnit.SECONDS)
      reader.close().block
      assertTrue(blocked, running.block == -1L)
    },
    test("toAsync close rejects a stale synchronous pull failure") {
      val entered = new CountDownLatch(1)
      val wake    = new CountDownLatch(1)
      val failure = new RuntimeException("stale")
      val source  = new IntSyncReader {
        def close(): Unit                  = wake.countDown()
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = {
          entered.countDown()
          wake.await()
          throw failure
        }
      }
      val reader  = source.toAsync
      val running = reader.readInt(-1L).start
      val blocked = entered.await(5, TimeUnit.SECONDS)
      reader.close().block
      assertTrue(blocked, running.block == -1L)
    },
    test("toAsync late EOF from a closed generation does not poison a reset generation") {
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val calls   = new AtomicInteger(0)
      val closes  = new AtomicInteger(0)
      val source  = new IntSyncReader {
        def close(): Unit                  = { closes.incrementAndGet(); release.countDown() }
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A =
          if (calls.incrementAndGet() == 1) {
            entered.countDown()
            release.await()
            sentinel
          } else 42.asInstanceOf[A]
        override def reset(): Unit = ()
      }
      val reader  = source.toAsync
      val stale   = reader.readInt(-1L).start
      val blocked = entered.await(5, TimeUnit.SECONDS)
      reader.close().block
      reader.reset().block
      release.countDown()
      val staleValue = stale.block
      val freshValue = reader.readInt(-1L).block
      assertTrue(blocked, staleValue == -1L, freshValue == 42L, closes.get() == 1)
    },
    test("toAsync reset joins an old pull before resetting the source cursor") {
      val readStarted  = new CountDownLatch(1)
      val allowRead    = new CountDownLatch(1)
      val resetStarted = new CountDownLatch(1)
      val index        = new AtomicInteger(0)
      val source       = new IntSyncReader {
        def close(): Unit                  = ()
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = {
          readStarted.countDown()
          allowRead.await()
          (10 + index.getAndIncrement()).asInstanceOf[A]
        }
        override def reset(): Unit = { resetStarted.countDown(); index.set(0) }
      }
      val reader    = source.toAsync
      val stale     = reader.readInt(-1L).start
      val entered   = readStarted.await(5, TimeUnit.SECONDS)
      val resetting = reader.reset().start
      val deadline  = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (reader.tryReadable != Reader.Unavailable && System.nanoTime() < deadline) Thread.`yield`()
      val resetWaited = resetStarted.getCount == 1L
      allowRead.countDown()
      resetting.block
      val staleValue = stale.block
      val freshValue = reader.readInt(-1L).block
      assertTrue(entered, resetWaited, staleValue == -1L, freshValue == 10L)
    },
    test("toAsync close wakes an active reset, leaves the source closed, and rejects the reset") {
      val resetStarted     = new CountDownLatch(1)
      val closeCalled      = new CountDownLatch(1)
      val resetWake        = new CountDownLatch(1)
      val physicallyClosed = new AtomicBoolean(false)
      val source           = new IntSyncReader {
        def close(): Unit = {
          physicallyClosed.set(true)
          closeCalled.countDown()
          resetWake.countDown()
        }
        def isClosed: Boolean              = physicallyClosed.get()
        def read[A >: Int](sentinel: A): A = sentinel
        override def reset(): Unit         = {
          resetStarted.countDown()
          resetWake.await()
          physicallyClosed.set(false)
        }
      }
      val reader       = source.toAsync
      val resetting    = reader.reset().start
      val resetEntered = resetStarted.await(5, TimeUnit.SECONDS)
      val closing      = reader.close().start
      val closeEntered = closeCalled.await(5, TimeUnit.SECONDS)
      val resetFailure =
        try { resetting.block; None }
        catch { case cause: Throwable => Some(cause) }
      closing.block
      assertTrue(
        resetEntered,
        closeEntered,
        resetFailure.exists(_.isInstanceOf[java.io.IOException]),
        physicallyClosed.get(),
        reader.isClosed.block
      )
    },
    test("toAsync rejects reset while physical close is in progress") {
      val closeStarted = new CountDownLatch(1)
      val allowClose   = new CountDownLatch(1)
      val source       = new IntSyncReader {
        def close(): Unit                  = { closeStarted.countDown(); allowClose.await() }
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
        override def reset(): Unit         = ()
      }
      val reader       = source.toAsync
      val closing      = reader.close().start
      val entered      = closeStarted.await(5, TimeUnit.SECONDS)
      val resetFailure =
        try { reader.reset().block; None }
        catch { case cause: Throwable => Some(cause) }
      allowClose.countDown()
      closing.block
      assertTrue(entered, resetFailure.exists(_.isInstanceOf[java.io.IOException]), reader.isClosed.block)
    },
    test("toAsync failed-reset compensation and concurrent close share one physical close") {
      val compensateStarted = new CountDownLatch(1)
      val allowCompensate   = new CountDownLatch(1)
      val closes            = new AtomicInteger(0)
      val failure           = new RuntimeException("reset")
      val source            = new IntSyncReader {
        def close(): Unit =
          if (closes.incrementAndGet() == 2) { compensateStarted.countDown(); allowCompensate.await() }
        def isClosed: Boolean              = closes.get() != 0
        def read[A >: Int](sentinel: A): A = sentinel
        override def reset(): Unit         = throw failure
      }
      val reader = source.toAsync
      reader.close().block
      val resetting = reader.reset().start
      val entered   = compensateStarted.await(5, TimeUnit.SECONDS)
      val closing   = reader.close().start
      Thread.sleep(20)
      val joined = closes.get() == 2
      allowCompensate.countDown()
      val resetFailure =
        try { resetting.block; None }
        catch { case cause: Throwable => Some(cause) }
      closing.block
      assertTrue(entered, joined, resetFailure.contains(failure), closes.get() == 2, reader.isClosed.block)
    },
    test("toAsync cancelled failed-reset compensation restores lifecycle state after cleanup") {
      var trial = 0
      while (trial < 1) {
        val compensateStarted = new CountDownLatch(1)
        val allowCompensate   = new CountDownLatch(1)
        val cleanupBlocking   = new CountDownLatch(1)
        val resets            = new AtomicInteger(0)
        val physicallyClosed  = new AtomicBoolean(false)
        val failure           = new RuntimeException("reset")
        val source            = new IntSyncReader {
          private val closes = new AtomicInteger(0)
          def close(): Unit  = {
            physicallyClosed.set(true)
            if (closes.incrementAndGet() == 2) {
              compensateStarted.countDown()
              var waiting = true
              while (waiting)
                try { allowCompensate.await(); waiting = false }
                catch { case _: InterruptedException => () }
            }
          }
          def isClosed: Boolean              = physicallyClosed.get()
          def read[A >: Int](sentinel: A): A = sentinel
          override def reset(): Unit         = {
            physicallyClosed.set(false)
            if (resets.incrementAndGet() == 1) throw failure
          }
        }
        val reader = source.toAsync
        reader.close().block
        val resetting = reader.reset().start
        if (!compensateStarted.await(5, TimeUnit.SECONDS))
          throw new AssertionError(s"trial $trial: compensating close did not start")
        val cleanup       = Async.cancelWithCleanup(resetting).start
        val cleanupWaiter = new Thread(() => { cleanupBlocking.countDown(); cleanup.block; () })
        cleanupWaiter.setName(s"failed-reset-cleanup-$trial")
        cleanupWaiter.setDaemon(true)
        cleanupWaiter.start()
        cleanupBlocking.await()
        val waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (
          cleanupWaiter.isAlive && cleanupWaiter.getState != Thread.State.WAITING &&
          System.nanoTime() < waitDeadline
        ) Thread.`yield`()
        val blockedState = cleanupWaiter.getState
        val blockedStack = cleanupWaiter.getStackTrace.mkString("\n  at ", "\n  at ", "")
        allowCompensate.countDown()
        cleanupWaiter.join(1000)
        if (cleanupWaiter.isAlive) {
          val stuckStack  = cleanupWaiter.getStackTrace.mkString("\n  at ", "\n  at ", "")
          val asyncStacks = Thread.getAllStackTraces
            .entrySet()
            .toArray
            .toList
            .map(_.asInstanceOf[java.util.Map.Entry[Thread, Array[StackTraceElement]]])
            .filter(entry => entry.getKey.getName.contains("async") || entry.getKey.getName == cleanupWaiter.getName)
            .map(entry =>
              s"${entry.getKey.getName} ${entry.getKey.getState}" +
                entry.getValue.mkString("\n    at ", "\n    at ", "")
            )
            .mkString("\n")
          throw new AssertionError(
            s"trial $trial: cancellation cleanup remained ${cleanupWaiter.getState} after compensating close " +
              s"was released; before release it was $blockedState:$blockedStack; after release:$stuckStack; " +
              s"async threads:\n$asyncStacks"
          )
        }
        reader.reset().block
        if (resets.get() != 2 || reader.isClosed.block)
          throw new AssertionError(s"trial $trial: lifecycle was not restored (resets=${resets.get()})")
        trial += 1
      }
      assertTrue(trial == 1)
    },
    test("toAsync cancelled reset from closed state cannot reopen the source") {
      val resetStarted     = new CountDownLatch(1)
      val allowReset       = new CountDownLatch(1)
      val physicallyClosed = new AtomicBoolean(false)
      val source           = new IntSyncReader {
        def close(): Unit                  = physicallyClosed.set(true)
        def isClosed: Boolean              = physicallyClosed.get()
        def read[A >: Int](sentinel: A): A = sentinel
        override def reset(): Unit         = {
          resetStarted.countDown()
          allowReset.await()
          physicallyClosed.set(false)
        }
      }
      val reader = source.toAsync
      reader.close().block
      val resetting = reader.reset().start
      val entered   = resetStarted.await(5, TimeUnit.SECONDS)
      val cleanup   = Async.cancelWithCleanup(resetting).start
      allowReset.countDown()
      cleanup.block
      assertTrue(entered, physicallyClosed.get(), reader.isClosed.block)
    },
    test("toAsync rejects an overlapping reset without stranding the reader closed") {
      val resetStarted = new CountDownLatch(1)
      val allowReset   = new CountDownLatch(1)
      val source       = new IntSyncReader {
        def close(): Unit                  = ()
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = 1.asInstanceOf[A]
        override def reset(): Unit         = { resetStarted.countDown(); allowReset.await() }
      }
      val reader  = source.toAsync
      val first   = reader.reset().start
      val entered = resetStarted.await(5, TimeUnit.SECONDS)
      val second  = reader.reset().either.block
      allowReset.countDown()
      first.block
      assertTrue(
        entered,
        second.left.exists(_.isInstanceOf[java.io.IOException]),
        !reader.isClosed.block,
        reader.readInt(-1L).block == 1L
      )
    },
    test("toAsync stale cancellation closes only its captured generation") {
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val closes  = new AtomicInteger(0)
      val source  = new IntSyncReader {
        def close(): Unit                  = { closes.incrementAndGet(); release.countDown() }
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = {
          entered.countDown()
          release.await()
          sentinel
        }
        override def reset(): Unit = ()
      }
      val reader  = source.toAsync
      val stale   = reader.readInt(-1L).start
      val blocked = entered.await(5, TimeUnit.SECONDS)
      reader.close().block
      reader.reset().block
      Async.cancelWithCleanup(stale).block
      val oldGenerationStayedMemoized = closes.get() == 1
      reader.close().block
      assertTrue(blocked, oldGenerationStayedMemoized, closes.get() == 2)
    },
    test("sync release joins concurrent close callers") {
      val entered  = new CountDownLatch(1)
      val release  = new CountDownLatch(1)
      val closes   = new AtomicInteger(0)
      val releases = new AtomicInteger(0)
      val source   = new IntSyncReader {
        def close(): Unit                  = { closes.incrementAndGet(); entered.countDown(); release.await() }
        def isClosed: Boolean              = false
        def read[A >: Int](sentinel: A): A = sentinel
      }
      val reader = source.withRelease { () => releases.incrementAndGet(); () }
      val first  = new Thread(() => reader.close())
      val second = new Thread(() => reader.close())
      first.start()
      val closeStarted = entered.await(5, TimeUnit.SECONDS)
      second.start()
      Thread.sleep(20)
      val followerWaited = second.isAlive
      release.countDown()
      first.join(5000); second.join(5000)
      assertTrue(closeStarted, followerWaited, !first.isAlive, !second.isAlive, closes.get() == 1, releases.get() == 1)
    },
    test("concat close awaits rejected late-tail cleanup") {
      val acquisitionPolled = new CountDownLatch(1)
      val tailCloseStarted  = new CountDownLatch(1)
      val tailClosed        = new Completer[Unit]
      final class LateAcquire(reader: Reader[Int]) extends Pollable[Reader[Int]] {
        private var value: Async[Reader[Int]]              = this
        private var observer: Runnable                     = null
        def poll(onComplete: Runnable): Async[Reader[Int]] = synchronized {
          acquisitionPolled.countDown()
          if (value.asInstanceOf[AnyRef] eq this) observer = onComplete
          value
        }
        def succeed(reader: Reader[Int]): Unit = {
          val notify = synchronized {
            value = Async.succeed(reader)
            observer
          }
          if (notify ne null) notify.run()
        }
        override def cancel(): Unit = succeed(reader)
      }
      val tail = new IntAsyncReader {
        def close(): Async[Unit]                  = { tailCloseStarted.countDown(); tailClosed }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(1.asInstanceOf[A])
      }
      val acquire     = new LateAcquire(tail)
      val reader      = Reader.closed.toAsync.concatAsyncWithJvmType(() => acquire, JvmType.Int)
      val readRunning = reader.readInt(-1L).start
      val entered     = acquisitionPolled.await(5, TimeUnit.SECONDS)
      val closeResult = new AtomicReference[Either[Throwable, Unit]]()
      val closeThread = new Thread(() =>
        try closeResult.set(Right(reader.close().block))
        catch { case t: Throwable => closeResult.set(Left(t)) }
      )
      closeThread.start()
      val deadline     = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      var closeClaimed = reader.isClosed.block
      while (!closeClaimed && System.nanoTime() < deadline) closeClaimed = reader.isClosed.block
      val cleanupStarted   = tailCloseStarted.await(5, TimeUnit.SECONDS)
      val waitedForCleanup = closeThread.isAlive
      tailClosed.succeed(())
      closeThread.join(5000)
      val readValue = readRunning.block
      assertTrue(
        entered,
        closeClaimed,
        cleanupStarted,
        waitedForCleanup,
        !closeThread.isAlive,
        closeResult.get() == Right(()),
        readValue == -1L
      )
    },
    test("concat close rejects an eager stale segment failure") {
      val entered = new CountDownLatch(1)
      val wake    = new CountDownLatch(1)
      val failure = new RuntimeException("stale")
      val head    = new IntAsyncReader {
        def close(): Async[Unit]                  = { wake.countDown(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = {
          entered.countDown()
          wake.await()
          throw failure
        }
      }
      val reader  = head.concatReaderWithJvmType(() => Reader.singleInt(2), JvmType.Int)
      val running = reader.readInt(-1L).start
      val started = entered.await(5, TimeUnit.SECONDS)
      reader.close().block
      assertTrue(started, running.block == -1L)
    },
    test("concat late EOF from before reset does not advance the reset generation") {
      val entered    = new CountDownLatch(1)
      val first      = new Completer[Long]
      val reads      = new AtomicInteger(0)
      val forcedTail = new AtomicInteger(0)
      val head       = new IntAsyncReader {
        def close(): Async[Unit]                                                    = Async.succeed(())
        def isClosed: Async[Boolean]                                                = Async.succeed(false)
        def readable(): Async[Boolean]                                              = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]                                   = readInt(Long.MinValue).map(_.asInstanceOf[A])
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
          val call = reads.incrementAndGet()
          if (call == 1) { entered.countDown(); first }
          else Async.succeed(1L)
        }
        override def reset(): Async[Unit] = Async.succeed(())
      }
      val reader = head.concatReaderWithJvmType(
        () => { forcedTail.incrementAndGet(); Reader.singleInt(2) },
        JvmType.Int
      )
      val staleValue = new AtomicReference[Long]()
      val stale      = new Thread(() => staleValue.set(reader.readInt(9999L).block))
      stale.start()
      val started            = entered.await(5, TimeUnit.SECONDS)
      val oneReadBeforeReset = reads.get() == 1
      reader.reset().block
      val oneReadAfterReset = reads.get() == 1
      first.succeed(9999L)
      stale.join(5000)
      val oneStaleRead = reads.get() == 1
      val freshValue   = reader.readInt(9999L).block
      assertTrue(
        started,
        oneReadBeforeReset,
        oneReadAfterReset,
        !stale.isAlive,
        staleValue.get() == 9999L,
        oneStaleRead,
        freshValue == 1L,
        reads.get() == 2,
        forcedTail.get() == 0
      )
    },
    test("nested concat reset retires its active head without explicitly closing the nested reader") {
      val inner = Reader.singleInt(1).toAsync ++ Reader.singleInt(2)
      val outer = inner ++ Reader.singleInt(3)
      val first = outer.read[Int](-1).block
      outer.reset().block
      val afterReset = List(
        outer.read[Int](-1).block,
        outer.read[Int](-1).block,
        outer.read[Int](-1).block,
        outer.read[Int](-1).block
      )
      assertTrue(first == 1, afterReset == List(1, 2, 3, -1))
    },
    test("concat close joins a pending reset and re-closes the rejected head") {
      val resetStarted = new CountDownLatch(1)
      val resetDone    = new Completer[Unit]
      val closes       = new AtomicInteger(0)
      val head         = new IntAsyncReader {
        def close(): Async[Unit]                  = { closes.incrementAndGet(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         = { resetStarted.countDown(); resetDone }
      }
      val reader       = head.concatReaderWithJvmType(() => Reader.singleInt(1), JvmType.Int)
      val resetRunning = reader.reset().start
      val entered      = resetStarted.await(5, TimeUnit.SECONDS)
      val closeRunning = reader.close().start
      Thread.sleep(20)
      val closeWaited = closeRunning.poll(new Runnable { def run(): Unit = () }).isInstanceOf[Pollable[_]]
      resetDone.succeed(())
      closeRunning.block
      val resetResult = resetRunning.either.block
      assertTrue(
        entered,
        closeWaited,
        reader.isClosed.block,
        closes.get() == 3,
        resetResult.left.exists(_.isInstanceOf[java.io.IOException])
      )
    },
    test("concat close can reject an eager reset blocked until its second close") {
      val resetStarted  = new CountDownLatch(1)
      val resetReleased = new CountDownLatch(1)
      val closes        = new AtomicInteger(0)
      val head          = new IntAsyncReader {
        def close(): Async[Unit] = Async.succeed {
          if (closes.incrementAndGet() >= 2) resetReleased.countDown()
          ()
        }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         = {
          resetStarted.countDown()
          resetReleased.await()
          Async.succeed(())
        }
      }
      val reader      = head.concatReaderWithJvmType(() => Reader.singleInt(1), JvmType.Int)
      val resetResult = new AtomicReference[Either[Throwable, Unit]]()
      val resetThread = new Thread(() => resetResult.set(reader.reset().either.block))
      resetThread.start()
      val entered     = resetStarted.await(5, TimeUnit.SECONDS)
      val closeThread = new Thread(() => reader.close().block)
      closeThread.start()
      resetThread.join(5000)
      closeThread.join(5000)
      assertTrue(
        entered,
        !resetThread.isAlive,
        !closeThread.isAlive,
        closes.get() == 3,
        resetResult.get().left.exists(_.isInstanceOf[java.io.IOException])
      )
    },
    test("concat rejects a second reset while the first is pending") {
      val resetStarted = new CountDownLatch(1)
      val resetDone    = new Completer[Unit]
      val head         = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         = { resetStarted.countDown(); resetDone }
      }
      val reader       = head.concatReaderWithJvmType(() => Reader.singleInt(1), JvmType.Int)
      val first        = reader.reset().start
      val entered      = resetStarted.await(5, TimeUnit.SECONDS)
      val secondEffect = reader.reset()
      val second       = secondEffect.either.block
      resetDone.succeed(())
      first.block
      assertTrue(entered, second.left.exists(_.isInstanceOf[java.io.IOException]), !reader.isClosed.block)
    },
    test("concat permits reset again after pending reset cancellation cleanup") {
      var trial = 0
      while (trial < 1) {
        val resetStarted = new CountDownLatch(1)
        val pollEntered  = new CountDownLatch(1)
        val allowPoll    = new CountDownLatch(1)
        val resetDone    = new Completer[Unit]
        val resets       = new AtomicInteger(0)
        val pendingReset = new Pollable[Unit] {
          def poll(onComplete: Runnable): Async[Unit] = {
            pollEntered.countDown()
            var waiting = true
            while (waiting)
              try { allowPoll.await(); waiting = false }
              catch { case _: InterruptedException => () }
            resetDone.poll(onComplete)
          }
        }
        val head = new IntAsyncReader {
          def close(): Async[Unit]                  = Async.succeed(())
          def isClosed: Async[Boolean]              = Async.succeed(false)
          def readable(): Async[Boolean]            = Async.succeed(false)
          def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
          override def reset(): Async[Unit]         =
            if (resets.incrementAndGet() == 1) { resetStarted.countDown(); pendingReset }
            else Async.succeed(())
        }
        val reader = head.concatReaderWithJvmType(() => Reader.singleInt(1), JvmType.Int)
        val first  = reader.reset().start
        if (!resetStarted.await(5, TimeUnit.SECONDS))
          throw new AssertionError(s"trial $trial: reset did not start")
        if (!pollEntered.await(5, TimeUnit.SECONDS))
          throw new AssertionError(s"trial $trial: reset poll did not start")
        val cleanup = Async.cancelWithCleanup(first).start
        resetDone.succeed(())
        allowPoll.countDown()
        val result = new AtomicReference[Any](null)
        val phase  = new AtomicInteger(0)
        val waiter = new Thread(() =>
          result.set(
            try {
              phase.set(1)
              cleanup.block
              phase.set(2)
              reader.reset().block
              phase.set(3)
              ()
            } catch { case cause: Throwable => cause }
          )
        )
        waiter.setName(s"concat-reset-cleanup-$trial")
        waiter.setDaemon(true)
        waiter.start()
        waiter.join(1000)
        if (waiter.isAlive) {
          val stacks = Thread.getAllStackTraces
            .entrySet()
            .toArray
            .toList
            .map(_.asInstanceOf[java.util.Map.Entry[Thread, Array[StackTraceElement]]])
            .filter(entry => entry.getKey.getName.contains("async") || entry.getKey.getName == waiter.getName)
            .map(entry =>
              s"${entry.getKey.getName} ${entry.getKey.getState}" +
                entry.getValue.mkString("\n    at ", "\n    at ", "")
            )
            .mkString("\n")
          throw new AssertionError(
            s"trial $trial: cancellation cleanup did not hand off completed reset; " +
              s"phase=${phase.get()}, waiter=${waiter.getState}, resets=${resets.get()}, threads:\n$stacks"
          )
        }
        result.get() match {
          case cause: Throwable => throw new AssertionError(s"trial $trial failed", cause)
          case _                =>
        }
        if (resets.get() != 2 || reader.isClosed.block)
          throw new AssertionError(s"trial $trial: reader was not restored (resets=${resets.get()})")
        trial += 1
      }
      assertTrue(trial == 1)
    },
    test("concat reset joins the previous tail transition before reopening") {
      val firstAcquireStarted                                    = new CountDownLatch(1)
      val secondAcquireStarted                                   = new CountDownLatch(1)
      val firstTailCloseStarted                                  = new CountDownLatch(1)
      val secondTailCloseStarted                                 = new CountDownLatch(1)
      val firstAcquire                                           = new Completer[Reader[Int]]
      val secondAcquire                                          = new Completer[Reader[Int]]
      val firstTailClose                                         = new Completer[Unit]
      val secondTailClose                                        = new Completer[Unit]
      val acquisitions                                           = new AtomicInteger(0)
      def tail(started: CountDownLatch, closed: Completer[Unit]) = new IntAsyncReader {
        def close(): Async[Unit]                  = { started.countDown(); closed }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val head = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
        override def reset(): Async[Unit]         = Async.succeed(())
      }
      val reader = head.concatAsyncWithJvmType(
        () =>
          if (acquisitions.incrementAndGet() == 1) { firstAcquireStarted.countDown(); firstAcquire }
          else { secondAcquireStarted.countDown(); secondAcquire },
        JvmType.Int
      )
      val firstRead    = reader.readInt(-1L).start
      val firstStarted = firstAcquireStarted.await(5, TimeUnit.SECONDS)
      val resetting    = reader.reset().start
      firstAcquire.succeed(tail(firstTailCloseStarted, firstTailClose))
      val firstCleaning = firstTailCloseStarted.await(5, TimeUnit.SECONDS)
      val resetWaited   = resetting.poll(new Runnable { def run(): Unit = () }).isInstanceOf[Pollable[_]]
      firstTailClose.succeed(())
      resetting.block
      val secondRead    = reader.readInt(-1L).start
      val secondStarted = secondAcquireStarted.await(5, TimeUnit.SECONDS)
      val closer        = new Thread(() => reader.close().block)
      closer.start()
      secondAcquire.succeed(tail(secondTailCloseStarted, secondTailClose))
      val secondCleaning = secondTailCloseStarted.await(5, TimeUnit.SECONDS)
      val closeWaited    = closer.isAlive
      secondTailClose.succeed(())
      closer.join(5000)
      firstRead.cancel(); secondRead.cancel()
      assertTrue(firstStarted, firstCleaning, resetWaited, secondStarted, secondCleaning, closeWaited, !closer.isAlive)
    },
    test("release wrapper close makes an in-flight isClosed result monotonic") {
      val entered = new CountDownLatch(1)
      val status  = new Completer[Boolean]
      val inner   = new IntAsyncReader {
        def close(): Async[Unit]                  = Async.succeed(())
        def isClosed: Async[Boolean]              = { entered.countDown(); status }
        def readable(): Async[Boolean]            = Async.succeed(false)
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val reader  = inner.withReleaseAsync(() => Async.succeed(()))
      val running = reader.isClosed.start
      val started = entered.await(5, TimeUnit.SECONDS)
      reader.close().block
      status.succeed(false)
      assertTrue(started, running.block)
    },
    test("release wrapper close rejects an eager stale inner failure") {
      val entered = new CountDownLatch(1)
      val wake    = new CountDownLatch(1)
      val failure = new RuntimeException("stale")
      val inner   = new IntAsyncReader {
        def close(): Async[Unit]                  = { wake.countDown(); Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def readable(): Async[Boolean]            = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A] = {
          entered.countDown()
          wake.await()
          throw failure
        }
      }
      val reader  = inner.withReleaseAsync(() => Async.succeed(()))
      val running = reader.readInt(-1L).start
      val started = entered.await(5, TimeUnit.SECONDS)
      reader.close().block
      assertTrue(started, running.block == -1L)
    },
    test("toSync delegates collection, scalar, bulk, and control operations") {
      def adapted[A](reader: Reader.SyncReader[A]): Reader.SyncReader[A] =
        reader.toAsync.withReleaseAsync(() => Async.succeed(())).toSync
      val ints    = adapted(Reader.fromChunk(Chunk(1, 2, 3, 4)))
      val intBulk = new Array[Int](3)
      val intRead = ints.readInts(intBulk, 1, 2)
      ints.reset()
      val limited   = ints.setLimit(2)
      val prefix    = ints.readUpToN[Int](1)
      val remainder = ints.readAll[Int]()
      val bytes     = adapted(Reader.fromChunk(Chunk[Byte](1, 2, 3)))
      val byteBulk  = new Array[Byte](4)
      val byteRead  = bytes.readBytes(byteBulk, 1, 2)
      val longs     = adapted(Reader.fromChunk(Chunk(1L, 2L)))
      val longBulk  = new Array[Long](2)
      val longRead  = longs.readLongs(longBulk, 0, 2)
      longs.reset()
      val longScalar = longs.readLong(Long.MinValue)
      val floats     = adapted(Reader.fromChunk(Chunk(1.5f, 2.5f)))
      val floatBulk  = new Array[Float](2)
      val floatRead  = floats.readFloats(floatBulk, 0, 2)
      val doubles    = adapted(Reader.fromChunk(Chunk(1.5d, 2.5d)))
      val doubleBulk = new Array[Double](2)
      val doubleRead = doubles.readDoubles(doubleBulk, 0, 2)
      doubles.reset()
      val doubleScalar = doubles.readDouble(Double.NaN)
      val booleans     = adapted(Reader.fromChunk(Chunk(true)))
      val chars        = adapted(Reader.fromChunk(Chunk('x')))
      val shorts       = adapted(Reader.fromChunk(Chunk(7.toShort)))
      assertTrue(
        ints.jvmType == JvmType.Int,
        intRead == 2,
        intBulk.toList == List(0, 1, 2),
        limited,
        prefix == Chunk(1),
        remainder == Chunk(2),
        ints.readInt(-1L) == -1L,
        byteRead == 2,
        byteBulk.toList == List[Byte](0, 1, 2, 0),
        bytes.readByte() == 3,
        longRead == 2,
        longBulk.toList == List(1L, 2L),
        longScalar == 1L,
        floatRead == 2,
        floatBulk.toList == List(1.5f, 2.5f),
        doubleRead == 2,
        doubleBulk.toList == List(1.5d, 2.5d),
        doubleScalar == 1.5d,
        booleans.readBoolean(-1) == 1,
        chars.readChar(-1) == 'x'.toInt,
        shorts.readShort(-1) == 7
      )
    },
    test("open toSync adapters delegate status, collection, and controls") {
      val controls = new AtomicInteger(0)
      val async    = new IntAsyncReader {
        def close(): Async[Unit]                              = Async.succeed(())
        def isClosed: Async[Boolean]                          = Async.succeed(false)
        def readable(): Async[Boolean]                        = Async.succeed(true)
        def read[A >: Int](sentinel: A): Async[A]             = Async.succeed(1.asInstanceOf[A])
        override def readN[A >: Int](n: Int): Async[Chunk[A]] = Async.succeed(Chunk(1.asInstanceOf[A]))
        override def setRepeat(): Async[Boolean]              = Async.succeed { controls.incrementAndGet(); true }
        override def setSkip(n: Long): Async[Boolean]         = Async.succeed { controls.incrementAndGet(); n == 2L }
        override def skip(n: Long): Async[Unit]               = Async.succeed { controls.addAndGet(n.toInt); () }
      }
      val sync = async.toSync
      assertTrue(
        !sync.isClosed,
        sync.readable(),
        sync.readN[Int](1) == Chunk(1),
        sync.setRepeat(),
        sync.setSkip(2L),
        scala.util.Try(sync.skip(3L)).isSuccess,
        controls.get() == 5
      )
    },
    test("closed toSync adapters return data sentinels and reject control operations") {
      val reader = Reader.fromChunk(Chunk(1)).toAsync.withReleaseAsync(() => Async.succeed(())).toSync
      reader.close()
      val resetFailure   = scala.util.Try(reader.reset()).failed.toOption.orNull
      val limitFailure   = scala.util.Try(reader.setLimit(1)).failed.toOption.orNull
      val repeatFailure  = scala.util.Try(reader.setRepeat()).failed.toOption.orNull
      val skipFailure    = scala.util.Try(reader.setSkip(1)).failed.toOption.orNull
      val skipNowFailure = scala.util.Try(reader.skip(1)).failed.toOption.orNull
      val byteDest       = new Array[Byte](1)
      val intDest        = new Array[Int](1)
      val longDest       = new Array[Long](1)
      val floatDest      = new Array[Float](1)
      val doubleDest     = new Array[Double](1)
      assertTrue(
        reader.isClosed,
        !reader.readable(),
        reader.readInt(-7L) == -7L,
        reader.readAll[Int]() == Chunk.empty,
        reader.readN[Int](1) == Chunk.empty,
        reader.readUpToN[Int](1) == Chunk.empty,
        reader.asInstanceOf[Reader.SyncReader[Byte]].readBytes(byteDest, 0, 1) == -1,
        reader.asInstanceOf[Reader.SyncReader[Byte]].readBytes(byteDest, 0, 0) == 0,
        reader.readInts(intDest, 0, 1) == -1,
        reader.readInts(intDest, 0, 0) == 0,
        reader.asInstanceOf[Reader.SyncReader[Long]].readLongs(longDest, 0, 1) == -1,
        reader.asInstanceOf[Reader.SyncReader[Long]].readLongs(longDest, 0, 0) == 0,
        reader.asInstanceOf[Reader.SyncReader[Float]].readFloats(floatDest, 0, 1) == -1,
        reader.asInstanceOf[Reader.SyncReader[Float]].readFloats(floatDest, 0, 0) == 0,
        reader.asInstanceOf[Reader.SyncReader[Double]].readDoubles(doubleDest, 0, 1) == -1,
        reader.asInstanceOf[Reader.SyncReader[Double]].readDoubles(doubleDest, 0, 0) == 0,
        resetFailure.isInstanceOf[java.io.IOException],
        limitFailure.isInstanceOf[java.io.IOException],
        repeatFailure.isInstanceOf[java.io.IOException],
        skipFailure.isInstanceOf[java.io.IOException],
        skipNowFailure.isInstanceOf[java.io.IOException]
      )
    }
  )
}
