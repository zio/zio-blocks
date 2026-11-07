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

package zio.blocks.async

import zio.ZIO
import zio.blocks.async.internal.PlatformAsync
import zio.test._

import java.util.concurrent.{CountDownLatch, CyclicBarrier, ForkJoinPool, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

/**
 * JVM concurrency probes.
 */
object AsyncConcurrencySpec extends ZIOSpecDefault {

  private val boom = AsyncTestSupport.boom
  private val Boom = AsyncTestSupport.boom

  // A genuinely-asynchronous leaf: a daemon thread sleeps `ms` then completes a
  // Completer off the driving thread. Unlike the synchronous `succeedAfter`
  // helpers (which complete inside `poll`), this exercises the waker/park path
  // with a real off-thread wakeup landing between polls.
  private def realAsync[A](value: A, ms: Long): Async[A] = {
    val c = new Completer[A]
    val t = new Thread(new Runnable { def run(): Unit = { Thread.sleep(ms); c.succeed(value) } })
    t.setDaemon(true); t.start()
    c.peek
  }

  private def realAsyncFail(cause: Throwable, ms: Long): Async[Nothing] = {
    val c = new Completer[Nothing]
    val t = new Thread(new Runnable { def run(): Unit = { Thread.sleep(ms); c.fail(cause) } })
    t.setDaemon(true); t.start()
    c.peek
  }

  def spec = suite("AsyncConcurrencySpec")(
    test("interpreter scheduler enqueues instead of invoking recursively") {
      ZIO.attemptBlocking {
        val caller    = Thread.currentThread()
        val completed = new CountDownLatch(1)
        val driver    = new AtomicReference[Thread]()
        val owner     = new Object
        val owned     = new CountDownLatch(1)
        val seenOwner = new AtomicReference[AnyRef]()
        Async.schedule(
          new Runnable {
            def run(): Unit = {
              driver.set(Thread.currentThread())
              completed.countDown()
            }
          },
          forceMacrotask = false
        )
        PlatformAsync.withExecutionOwner(owner) {
          Async.schedule(
            new Runnable {
              def run(): Unit = {
                seenOwner.set(PlatformAsync.currentExecutionOwner)
                owned.countDown()
              }
            },
            forceMacrotask = false
          )
        }
        assertTrue(
          completed.await(5, TimeUnit.SECONDS),
          driver.get() ne caller,
          owned.await(5, TimeUnit.SECONDS),
          seenOwner.get() eq owner
        )
      }
    },
    // Round-32 convergence: combinators driven over GENUINELY off-thread leaves
    // (a worker that sleeps then completes a Completer), where the wakeup lands
    // between polls via the park/onComplete path — not synchronously inside
    // `poll` as the `succeedAfter` helpers do. Covers the timing/scheduling
    // surface (real async wakeups through map/flatMap/zip/collectAll/ensuring).
    suite("real off-thread leaves through combinators")(
      test("zipWith of two off-thread leaves lands both wakeups and combines") {
        ZIO.attemptBlocking {
          val r = (realAsync(3, 30): Async[Int]).zipWith(realAsync(4, 5): Async[Int])(_ + _)
          assertTrue(r.block == 7)
        }
      },
      test("collectAll over off-thread leaves completing out of order preserves input order") {
        ZIO.attemptBlocking {
          val r = Async.collectAll(List[Async[Int]](realAsync(1, 40), realAsync(2, 5), realAsync(3, 20)))
          assertTrue(r.block == List(1, 2, 3))
        }
      },
      test("zipWith slow-success left and fast-fail right waits for left then surfaces right's failure") {
        ZIO.attemptBlocking {
          val rs  = new RuntimeException("zip-right")
          val r   = (realAsync(1, 40): Async[Int]).zipWith(realAsyncFail(rs, 5): Async[Int])(_ + _)
          val got =
            try { r.block; None }
            catch { case t: Throwable => Some(t) }
          assertTrue(got.contains(rs))
        }
      },
      test("collectAll with an off-thread middle failure does not drive a still-pending later element") {
        ZIO.attemptBlocking {
          val mid               = new RuntimeException("collect-mid")
          val driven            = new AtomicInteger(0)
          val later: Async[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = { driven.incrementAndGet(); onComplete.run(); this }
          }
          val r   = Async.collectAll(List[Async[Int]](realAsync(1, 10), realAsyncFail(mid, 20), later))
          val got =
            try { r.block; None }
            catch { case t: Throwable => Some(t) }
          assertTrue(got.contains(mid), driven.get() == 0)
        }
      },
      test("ensuring runs an off-thread finalizer after an off-thread primary succeeds") {
        ZIO.attemptBlocking {
          val fin = new AtomicInteger(0)
          val r   =
            (realAsync(5, 20): Async[Int]).ensuring((realAsync((), 20): Async[Unit]).map(_ => fin.incrementAndGet()))
          assertTrue(r.block == 5, fin.get() == 1)
        }
      },
      test(
        "ensuring over off-thread primary-fail and off-thread finalizer-fail keeps primary with finalizer suppressed"
      ) {
        ZIO.attemptBlocking {
          val prim = new RuntimeException("ens-primary")
          val fin  = new RuntimeException("ens-finalizer")
          val r    = (realAsyncFail(prim, 20): Async[Int]).ensuring(realAsyncFail(fin, 20))
          val got  =
            try { r.block; None }
            catch { case t: Throwable => Some(t) }
          val suppressed = got.toList.flatMap(_.getSuppressed.toList)
          assertTrue(got.contains(prim), suppressed.contains(fin))
        }
      },
      test("deep flatMap chain over off-thread leaves resumes across every wakeup") {
        ZIO.attemptBlocking {
          val r = (realAsync(1, 10): Async[Int])
            .flatMap(a => (realAsync(a + 1, 10): Async[Int]))
            .flatMap(b => (realAsync(b + 1, 10): Async[Int]))
            .flatMap(c => (realAsync(c + 1, 10): Async[Int]))
          assertTrue(r.block == 4)
        }
      },
      test("zipWith of off-thread leaves driven through start observes the combined value") {
        ZIO.attemptBlocking {
          val r = (realAsync(3, 20): Async[Int]).zipWith(realAsync(4, 5): Async[Int])(_ + _).start
          assertTrue(r.block == 7)
        }
      }
    ),
    suite("concurrency")(
      test("a nested blocking await does not reuse its outer busy parker") {
        ZIO.attemptBlocking {
          val inner = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(42)
          }
          val outer = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(inner.block)
          }
          assertTrue(outer.block == 42)
        }
      },
      test("Running cancellation replays cleanup and captures a throwing cancellation hook") {
        ZIO.attemptBlocking {
          def suspended(onCancel: => Async[Unit]): (Async.Running[Int], CountDownLatch) = {
            val entered = new CountDownLatch(1)
            val running = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int]                   = { entered.countDown(); this }
              override private[async] def cancelWithCleanup(): Async[Unit] = onCancel
            }.start
            (running, entered)
          }

          val (replayed, replayedEntered) = suspended(Async.succeed(()))
          val replayArmed                 = replayedEntered.await(5, TimeUnit.SECONDS)
          val first                       = replayed.cancelWithCleanup()
          val second                      = replayed.cancelWithCleanup()
          val cancellationFailure         = new RuntimeException("cancellation-hook")
          val (throwing, throwingEntered) = suspended(throw cancellationFailure)
          val throwingArmed               = throwingEntered.await(5, TimeUnit.SECONDS)
          val throwingResult              = throwing.cancelWithCleanup().either.block

          assertTrue(
            replayArmed,
            first.block == (),
            second.block == (),
            throwingArmed,
            throwingResult == Left(cancellationFailure)
          )
        }
      },
      test("Running cancel reports cleanup failures and normalizes a null cause") {
        ZIO.attemptBlocking {
          def reported(cause: Throwable): Throwable = {
            val entered  = new CountDownLatch(1)
            val reported = new CountDownLatch(1)
            val failure  = new AtomicReference[Throwable]()
            val group    = new ThreadGroup("async-cleanup-report") {
              override def uncaughtException(thread: Thread, cause: Throwable): Unit = {
                failure.set(cause)
                reported.countDown()
              }
            }
            val caller = new Thread(
              group,
              new Runnable {
                def run(): Unit = {
                  val running = new Pollable[Int] {
                    def poll(onComplete: Runnable): Async[Int]                   = { entered.countDown(); this }
                    override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(cause)
                  }.start
                  if (entered.await(5, TimeUnit.SECONDS)) running.cancel()
                }
              }
            )
            caller.start()
            caller.join(5000)
            if (!reported.await(5, TimeUnit.SECONDS)) null else failure.get()
          }

          val boom = new RuntimeException("cleanup")
          assertTrue(reported(boom) eq boom, reported(null) eq Failure.NullCauseMarker)
        }
      },
      test("Running invokes completion callbacks outside its waiter monitor") {
        ZIO.attemptBlocking {
          val source              = new Completer[Int]
          val running             = source.peek.start
          val callbackEntered     = new CountDownLatch(1)
          val releaseCallback     = new CountDownLatch(1)
          val secondPollDone      = new CountDownLatch(1)
          val callbackHeldMonitor = new AtomicBoolean(true)
          running.poll(new Runnable {
            def run(): Unit = {
              callbackHeldMonitor.set(Thread.holdsLock(running))
              callbackEntered.countDown()
              releaseCallback.await()
            }
          })
          source.succeed(1)
          val entered = callbackEntered.await(5, TimeUnit.SECONDS)
          val second  = new Thread(() => {
            running.poll(AsyncTestSupport.noopRunnable)
            secondPollDone.countDown()
          })
          second.start()
          val pollCompletedWhileCallbackBlocked = secondPollDone.await(5, TimeUnit.SECONDS)
          releaseCallback.countDown()
          second.join(5000)
          assertTrue(
            entered,
            !callbackHeldMonitor.get(),
            pollCompletedWhileCallbackBlocked,
            !second.isAlive,
            running.block == 1
          )
        }
      },
      test("bracket acquisition cancellation atomically hands off an in-flight replacement") {
        ZIO.attemptBlocking {
          val predecessorFailure = new RuntimeException("predecessor cleanup")
          val replacementFailure = new RuntimeException("replacement cleanup")
          val releaseFailure     = new RuntimeException("release")
          val pollEntered        = new CountDownLatch(1)
          val cancelEntered      = new CountDownLatch(1)
          val allowPollReturn    = new CountDownLatch(1)
          val allowCancelReturn  = new CountDownLatch(1)
          val predecessorCancels = new AtomicInteger(0)
          val replacementCancels = new AtomicInteger(0)
          val predecessorCleans  = new AtomicInteger(0)
          val replacementCleans  = new AtomicInteger(0)
          val releases           = new AtomicInteger(0)
          val cleanupRef         = new AtomicReference[Async[Unit]]()

          def failingCleanup(count: AtomicInteger, cause: Throwable): Async[Unit] = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              count.incrementAndGet()
              Async.fail(cause)
            }
          }

          val replacement = new Pollable[String] {
            @volatile private var cancelled               = false
            def poll(onComplete: Runnable): Async[String] =
              if (cancelled) Async.succeed("resource") else this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              replacementCancels.incrementAndGet()
              cancelled = true
              failingCleanup(replacementCleans, replacementFailure)
            }
          }
          val predecessor = new Pollable[String] {
            private val polls                             = new AtomicInteger(0)
            def poll(onComplete: Runnable): Async[String] =
              if (polls.incrementAndGet() == 1) this
              else {
                pollEntered.countDown()
                cancelEntered.await()
                allowPollReturn.await()
                replacement
              }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              predecessorCancels.incrementAndGet()
              cancelEntered.countDown()
              allowCancelReturn.await()
              failingCleanup(predecessorCleans, predecessorFailure)
            }
          }
          val bracket = Async.bracketAsync[String, Int](
            () => predecessor,
            _ => Async.succeed(1),
            _ => { releases.incrementAndGet(); Async.fail(releaseFailure) }
          )
          val pollable = bracket.asInstanceOf[Pollable[Int]]
          pollable.poll(AsyncTestSupport.noopRunnable)

          val driver    = new Thread(() => { pollable.poll(AsyncTestSupport.noopRunnable); () })
          val canceller = new Thread(() => {
            pollEntered.await()
            cleanupRef.set(pollable.cancelWithCleanup())
          })
          driver.start()
          canceller.start()
          cancelEntered.await()
          allowPollReturn.countDown()
          driver.join()
          allowCancelReturn.countDown()
          canceller.join()

          val got =
            try { cleanupRef.get().block; None }
            catch { case t: Throwable => Some(t) }
          assertTrue(
            got.contains(predecessorFailure),
            predecessorFailure.getSuppressed.toList == List(replacementFailure, releaseFailure),
            predecessorCancels.get() == 1,
            replacementCancels.get() == 1,
            predecessorCleans.get() == 1,
            replacementCleans.get() == 1,
            releases.get() == 1
          )
        }
      },
      test("bracket cleanup waits for an in-flight use cancellation and ignores throwing waiters") {
        ZIO.attemptBlocking {
          val cancelEntered          = new CountDownLatch(1)
          val allowCancelReturn      = new CountDownLatch(1)
          val cleanupAwaiting        = new CountDownLatch(1)
          val useCancels             = new AtomicInteger(0)
          val releases               = new AtomicInteger(0)
          val cleanupResult          = new AtomicReference[Throwable]()
          val cleanupRef             = new AtomicReference[Async[Unit]]()
          var bracket: Pollable[Int] = null
          val use                    = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              useCancels.incrementAndGet()
              cleanupRef.set(bracket.cancelWithCleanup())
              cancelEntered.countDown()
              allowCancelReturn.await()
              Async.succeed(())
            }
          }
          bracket = Async
            .bracketAsync[String, Int](
              () => Async.succeed("resource"),
              _ => use,
              _ => { releases.incrementAndGet(); Async.succeed(()) }
            )
            .asInstanceOf[Pollable[Int]]
          bracket.poll(AsyncTestSupport.noopRunnable)

          val canceller = new Thread(() => { bracket.cancelWithCleanup(); () })
          canceller.start()
          val entered = cancelEntered.await(5, TimeUnit.SECONDS)
          val cleaner = new Thread(() => {
            cleanupAwaiting.countDown()
            try
              cleanupRef
                .get()
                .asInstanceOf[Pollable[Unit]]
                .poll(new Runnable { def run(): Unit = throw new RuntimeException("cleanup waiter") })
            catch { case cause: Throwable => cleanupResult.set(cause) }
            ()
          })
          cleaner.start()
          cleanupAwaiting.await(5, TimeUnit.SECONDS)
          val waited = cleaner.isAlive || cleanupRef
            .get()
            .asInstanceOf[Pollable[Unit]]
            .poll(new Runnable { def run(): Unit = throw new RuntimeException("replacement waiter") })
            .isInstanceOf[Pollable[?]]
          allowCancelReturn.countDown()
          canceller.join(5000)
          cleaner.join(5000)
          cleanupRef.get().block

          assertTrue(
            entered,
            waited,
            !canceller.isAlive,
            !cleaner.isAlive,
            cleanupResult.get() == null,
            useCancels.get() == 1,
            releases.get() == 1
          )
        }
      },
      test("bracket permit handoff isolates throwing poll waiters during acquisition and release") {
        ZIO.attemptBlocking {
          val acquisitionEntered = new CountDownLatch(1)
          val allowAcquisition   = new CountDownLatch(1)
          val releaseEntered     = new CountDownLatch(1)
          val allowRelease       = new CountDownLatch(1)
          val acquisition        = new Pollable[String] {
            def poll(onComplete: Runnable): Async[String] = {
              acquisitionEntered.countDown()
              allowAcquisition.await()
              Async.succeed("resource")
            }
          }
          val release = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              releaseEntered.countDown()
              allowRelease.await()
              Async.succeed(())
            }
          }
          val bracket = Async
            .bracketAsync[String, Int](() => acquisition, _ => Async.succeed(1), _ => release)
            .asInstanceOf[Pollable[Int]]
          val firstResult = new AtomicReference[Async[Int]]()
          val first       = new Thread(() => firstResult.set(bracket.poll(AsyncTestSupport.noopRunnable)))
          first.start()
          val acquired            = acquisitionEntered.await(5, TimeUnit.SECONDS)
          val queuedDuringAcquire = bracket.poll(new Runnable {
            def run(): Unit = throw new RuntimeException("acquire waiter")
          })
          allowAcquisition.countDown()
          val releasing           = releaseEntered.await(5, TimeUnit.SECONDS)
          val queuedDuringRelease = bracket.poll(new Runnable {
            def run(): Unit = throw new RuntimeException("release waiter")
          })
          allowRelease.countDown()
          first.join(5000)

          assertTrue(
            acquired,
            releasing,
            queuedDuringAcquire.asInstanceOf[AnyRef] eq bracket.asInstanceOf[AnyRef],
            queuedDuringRelease.asInstanceOf[AnyRef] eq bracket.asInstanceOf[AnyRef],
            !first.isAlive,
            firstResult.get() == Async.succeed(1)
          )
        }
      },
      test("flatMap cancellation joins a pending child returned by an in-flight continuation") {
        ZIO.attemptBlocking {
          val continuationEntered = new CountDownLatch(1)
          val allowContinuation   = new CountDownLatch(1)
          val childCancels        = new AtomicInteger(0)
          val polls               = new AtomicInteger(0)
          val cleanupRef          = new AtomicReference[Async[Unit]]()
          val resultRef           = new AtomicReference[Async[Int]]()
          val child               = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              childCancels.incrementAndGet()
              Async.succeed(())
            }
          }
          val source = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] =
              if (polls.incrementAndGet() == 1) this else Async.succeed(())
          }
          val effect = source.flatMap { _ =>
            continuationEntered.countDown()
            allowContinuation.await()
            child
          }
          val pending = effect.asInstanceOf[Pollable[Int]]
          pending.poll(AsyncTestSupport.noopRunnable)

          val driver = new Thread(() => resultRef.set(pending.poll(AsyncTestSupport.noopRunnable)))
          driver.start()
          continuationEntered.await()
          cleanupRef.set(pending.cancelWithCleanup())
          allowContinuation.countDown()
          driver.join(5000)
          cleanupRef.get().block

          assertTrue(
            !driver.isAlive,
            resultRef.get().asInstanceOf[AnyRef] eq effect.asInstanceOf[AnyRef],
            childCancels.get() == 1,
            polls.get() == 2
          )
        }
      },
      test("flatMap cancellation does not join its own reentrant cleanup completion") {
        ZIO.attemptBlocking {
          val pollEntered   = new CountDownLatch(1)
          val allowPoll     = new CountDownLatch(1)
          val cancellations = new AtomicInteger(0)
          val rootRef       = new AtomicReference[Pollable[Int]]()
          val leaf          = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = {
              pollEntered.countDown()
              allowPoll.await()
              this
            }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancellations.incrementAndGet()
              rootRef.get().cancelWithCleanup()
            }
          }
          val root = leaf.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          rootRef.set(root)
          val poller = new Thread(() => { root.poll(AsyncTestSupport.noopRunnable); () })
          poller.start()
          val entered = pollEntered.await(5, TimeUnit.SECONDS)
          val cleanup = root.cancelWithCleanup().start
          allowPoll.countDown()
          val done      = new CountDownLatch(1)
          val completed = cleanup.poll(new Runnable { def run(): Unit = done.countDown() }) match {
            case _: Pollable[?] => done.await(5, TimeUnit.SECONDS)
            case _              => true
          }
          poller.join(5000)
          assertTrue(entered, completed, !poller.isAlive, cancellations.get() == 1)
        }
      },
      test("shared cancellation does not join its own reentrant cleanup completion") {
        ZIO.attemptBlocking {
          val pollEntered   = new CountDownLatch(1)
          val allowPoll     = new CountDownLatch(1)
          val cancellations = new AtomicInteger(0)
          val rootRef       = new AtomicReference[Pollable[Int]]()
          val leaf          = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = {
              pollEntered.countDown()
              allowPoll.await()
              this
            }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancellations.incrementAndGet()
              rootRef.get().cancelWithCleanup()
            }
          }
          val root = Async.cancelTo(leaf, () => Async.succeed(-1))
          rootRef.set(root)
          val poller = new Thread(() => { root.poll(AsyncTestSupport.noopRunnable); () })
          poller.start()
          val entered = pollEntered.await(5, TimeUnit.SECONDS)
          val cleanup = root.cancelWithCleanup().start
          allowPoll.countDown()
          val done      = new CountDownLatch(1)
          val completed = cleanup.poll(new Runnable { def run(): Unit = done.countDown() }) match {
            case _: Pollable[?] => done.await(5, TimeUnit.SECONDS)
            case _              => true
          }
          poller.join(5000)
          assertTrue(entered, completed, !poller.isAlive, cancellations.get() == 1)
        }
      },
      test("overlapping continuation cancellation batches do not form an indirect completion cycle") {
        ZIO.attemptBlocking {
          val outerRef      = new AtomicReference[Pollable[Int]]()
          val cancellations = new AtomicInteger(0)
          val leaf          = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancellations.incrementAndGet()
              outerRef.get().cancelWithCleanup()
            }
          }
          val inner = leaf.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          val outer = inner.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          outerRef.set(outer)
          val cleanup   = inner.cancelWithCleanup().start
          val done      = new CountDownLatch(1)
          val completed = cleanup.poll(new Runnable { def run(): Unit = done.countDown() }) match {
            case _: Pollable[?] => done.await(5, TimeUnit.SECONDS)
            case _              => true
          }
          assertTrue(completed, cancellations.get() == 1)
        }
      },
      test("different-owner cancellation batches union through a shared continuation") {
        ZIO.attemptBlocking {
          val gate          = new Completer[Unit]
          val cancellations = new AtomicInteger(0)
          val leaf          = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancellations.incrementAndGet()
              gate.peek
            }
          }
          val shared = leaf.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          val roots  = Array(
            shared.flatMap(Async.succeed).asInstanceOf[Pollable[Int]],
            shared.map(identity).asInstanceOf[Pollable[Int]]
          )
          val owners  = Array[AnyRef](new Object, new Object)
          val barrier = new CyclicBarrier(2)
          val running = Array.fill[Async.Running[Unit]](2)(null)
          val threads = Array.tabulate(2) { index =>
            new Thread(() => {
              barrier.await()
              running(index) = PlatformAsync.withExecutionOwner(owners(index)) {
                roots(index).cancelWithCleanup().start
              }
            })
          }
          threads.foreach(_.start())
          threads.foreach(_.join(5000))
          val started = threads.forall(!_.isAlive) && running.forall(_ ne null)
          gate.succeed(())
          running.foreach(_.block)

          assertTrue(started, cancellations.get() == 1)
        }
      },
      test("same-owner cancellation batch preserves task failure order and replay") {
        ZIO.attemptBlocking {
          val owner    = new Object
          val gates    = Array(new Completer[Unit], new Completer[Unit])
          val failures = Array(new RuntimeException("first"), new RuntimeException("second"))
          val roots    = List.tabulate(2) { index =>
            val leaf = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int]                   = this
              override private[async] def cancelWithCleanup(): Async[Unit] =
                gates(index).peek.flatMap(_ => Async.fail(failures(index)))
            }
            leaf.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          }
          val cleanups = PlatformAsync.withExecutionOwner(owner) {
            roots.map(_.cancelWithCleanup())
          }
          val running = cleanups.map(_.start)
          gates(1).succeed(())
          gates(0).succeed(())

          def failureOf(effect: Async[Unit]): Throwable =
            try { effect.block; null }
            catch { case cause: Throwable => cause }

          val first  = failureOf(running(0))
          val second = failureOf(running(1))
          val replay = failureOf(roots(0).cancelWithCleanup())
          assertTrue(
            first eq failures(0),
            second eq failures(0),
            replay eq failures(0),
            failures(0).getSuppressed.toList == List(failures(1))
          )
        }
      },
      test("completed failing BatchJoin is replayed through a wrapped cleanup leaf") {
        ZIO.attemptBlocking {
          val failure = new RuntimeException("cleanup")
          val source  = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(failure)
          }
          val firstRoot = source.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          val completed = firstRoot.cancelWithCleanup()
          try completed.block
          catch { case _: Throwable => () }
          val dependent = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] =
              completed.flatMap(_ => Async.succeed(()))
          }
          val secondRoot = dependent.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          val replayed   =
            try { secondRoot.cancelWithCleanup().block; null }
            catch { case cause: Throwable => cause }

          assertTrue(replayed eq failure)
        }
      },
      test("suspended cleanup restores its cancellation batch on an off-thread wakeup") {
        ZIO.attemptBlocking {
          val gate          = new Completer[Unit]
          val rootRef       = new AtomicReference[Pollable[Int]]()
          val cancellations = new AtomicInteger(0)
          val leaf          = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancellations.incrementAndGet()
              val joined = Async.cancellation()
              joined.primary(gate.peek.flatMap(_ => rootRef.get().cancelWithCleanup()))
              joined.noReplacement()
              joined.effect
            }
          }
          val root = leaf.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          rootRef.set(root)
          val cleanup = root.cancelWithCleanup().start
          val release = new Thread(() => gate.succeed(()))
          release.start()
          release.join(5000)
          val done      = new CountDownLatch(1)
          val completed = cleanup.poll(new Runnable { def run(): Unit = done.countDown() }) match {
            case _: Pollable[?] => done.await(5, TimeUnit.SECONDS)
            case _              => true
          }

          assertTrue(completed, !release.isAlive, cancellations.get() == 1)
        }
      },
      test("start body replays a completed same-batch cancellation failure") {
        ZIO.attemptBlocking {
          val failure    = new RuntimeException("cleanup")
          val release    = new CountDownLatch(1)
          val cleanupRef = new AtomicReference[Async[Unit]]()
          val delayedRef = new AtomicReference[Async.Running[Unit]]()
          val leaf       = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              delayedRef.set(Async.start {
                release.await()
                cleanupRef.get().block
              })
              Async.fail(failure)
            }
          }
          val root    = leaf.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          val cleanup = root.cancelWithCleanup()
          cleanupRef.set(cleanup)
          val first =
            try { cleanup.block; null }
            catch { case cause: Throwable => cause }
          release.countDown()
          val replay =
            try { delayedRef.get().block; null }
            catch { case cause: Throwable => cause }

          assertTrue(first eq failure, replay eq failure)
        }
      },
      test("runner terminal handoff restores its cancellation batch") {
        ZIO.attemptBlocking {
          val pollEntered     = new CountDownLatch(1)
          val allowPoll       = new CountDownLatch(1)
          val cleanupObserved = new CountDownLatch(1)
          val rootRef         = new AtomicReference[Pollable[Int]]()
          val runnerLeaf      = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              pollEntered.countDown()
              allowPoll.await()
              Async.succeed(())
            }
            override private[async] def cancelWithCleanup(): Async[Unit] =
              rootRef.get().cancelWithCleanup()
          }
          val outerLeaf = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              val running = runnerLeaf.start
              if (!pollEntered.await(5, TimeUnit.SECONDS)) Async.fail(new RuntimeException("runner did not poll"))
              else {
                val cleanup = running.cancelWithCleanup().asInstanceOf[Pollable[Unit]]
                new Pollable[Unit] {
                  def poll(onComplete: Runnable): Async[Unit] = {
                    cleanupObserved.countDown()
                    cleanup.poll(onComplete)
                  }
                  override private[async] def cancelWithCleanup(): Async[Unit] = cleanup.cancelWithCleanup()
                }
              }
            }
          }
          val root = outerLeaf.flatMap(Async.succeed).asInstanceOf[Pollable[Int]]
          rootRef.set(root)
          val cleanup  = root.cancelWithCleanup().start
          val observed = cleanupObserved.await(5, TimeUnit.SECONDS)
          allowPoll.countDown()
          val done      = new CountDownLatch(1)
          val completed = cleanup.poll(new Runnable { def run(): Unit = done.countDown() }) match {
            case _: Pollable[?] => done.await(5, TimeUnit.SECONDS)
            case _              => true
          }

          assertTrue(observed, completed)
        }
      },
      test("cancellation completes after a continuation publishes an unpolled child") {
        ZIO.attemptBlocking {
          val failures = List("flatMap", "catchAll").flatMap { kind =>
            val childCancels = new AtomicInteger(0)
            val child        = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int]                   = this
              override private[async] def cancelWithCleanup(): Async[Unit] = {
                childCancels.incrementAndGet()
                Async.succeed(())
              }
            }
            val sourcePolls = new AtomicInteger(0)
            val source      = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int] =
                if (sourcePolls.incrementAndGet() == 1) this
                else if (kind == "flatMap") Async.succeed(1)
                else Async.fail(new RuntimeException("source"))
            }
            val effect =
              if (kind == "flatMap") source.flatMap(_ => child)
              else source.catchAll(_ => child)
            val pending = effect.asInstanceOf[Pollable[Int]]
            pending.poll(AsyncTestSupport.noopRunnable)
            val handed    = pending.poll(AsyncTestSupport.noopRunnable)
            val cleanup   = pending.cancelWithCleanup().start
            val done      = new CountDownLatch(1)
            val completed = cleanup.poll(new Runnable { def run(): Unit = done.countDown() }) match {
              case _: Pollable[?] => done.await(5, TimeUnit.SECONDS)
              case _              => true
            }
            val failure =
              if (
                !(handed.asInstanceOf[AnyRef] eq child.asInstanceOf[AnyRef]) || !completed ||
                childCancels.get() != 1
              ) Some(s"$kind: handed=$handed, completed=$completed, childCancels=${childCancels.get()}")
              else None
            failure
          }

          assertTrue(failures.isEmpty)
        }
      },
      test("acquireCancelable cancellation waits for an in-flight acquire and releases its late value") {
        ZIO.attemptBlocking {
          val acquireEntered = new CountDownLatch(1)
          val allowAcquire   = new CountDownLatch(1)
          val releases       = new AtomicInteger(0)
          val effect         = Async.acquireCancelable[Int](
            () => { acquireEntered.countDown(); allowAcquire.await(); 42 },
            value => { if (value == 42) releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending    = effect.asInstanceOf[Pollable[Int]]
          val pollResult = new AtomicReference[Async[Int]]()
          val driver     = new Thread(() => pollResult.set(pending.poll(AsyncTestSupport.noopRunnable)))
          driver.start()
          val entered = acquireEntered.await(5, TimeUnit.SECONDS)
          val cleanup = pending.cancelWithCleanup()
          val joined  = new CountDownLatch(1)
          val cleaner = new Thread(() => { cleanup.block; joined.countDown() })
          cleaner.start()
          val waited = joined.getCount == 1L
          allowAcquire.countDown()
          driver.join(5000)
          val completed = joined.await(5, TimeUnit.SECONDS)
          cleaner.join(5000)

          assertTrue(
            entered,
            waited,
            completed,
            !driver.isAlive,
            !cleaner.isAlive,
            pollResult.get().asInstanceOf[AnyRef] eq pending.asInstanceOf[AnyRef],
            releases.get() == 1
          )
        }
      },
      test("map and flatMap cancellation own pending in-flight source results without running continuations") {
        ZIO.attemptBlocking {
          val cases    = List(("map", false), ("map", true), ("flatMap", false), ("flatMap", true))
          val failures = cases.flatMap { case (kind, distinct) =>
            val pollEntered        = new CountDownLatch(1)
            val allowPollReturn    = new CountDownLatch(1)
            val polls              = new AtomicInteger(0)
            val sourceCancels      = new AtomicInteger(0)
            val replacementCancels = new AtomicInteger(0)
            val continuations      = new AtomicInteger(0)
            val cleanupRef         = new AtomicReference[Async[Unit]]()
            val cleanupFailure     = new AtomicReference[Throwable]()

            val replacement = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int]                   = this
              override private[async] def cancelWithCleanup(): Async[Unit] = {
                replacementCancels.incrementAndGet()
                Async.succeed(())
              }
            }
            val source = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int] =
                if (polls.incrementAndGet() == 1) this
                else {
                  pollEntered.countDown()
                  allowPollReturn.await()
                  if (distinct) replacement else this
                }
              override private[async] def cancelWithCleanup(): Async[Unit] = {
                sourceCancels.incrementAndGet()
                Async.succeed(())
              }
            }
            val acquisition =
              if (kind == "map") source.map { value => continuations.incrementAndGet(); value }
              else source.flatMap { value => continuations.incrementAndGet(); Async.succeed(value) }
            val bracket =
              Async.bracketAsync[Int, Unit](() => acquisition, _ => Async.succeed(()), _ => Async.succeed(()))
            val pending = bracket.asInstanceOf[Pollable[Unit]]
            pending.poll(AsyncTestSupport.noopRunnable)

            val driver = new Thread(() => { pending.poll(AsyncTestSupport.noopRunnable); () })
            driver.start()
            pollEntered.await()
            cleanupRef.set(pending.cancelWithCleanup())
            allowPollReturn.countDown()
            driver.join(5000)
            val cleanupDriver = new Thread(() =>
              try cleanupRef.get().block
              catch { case t: Throwable => cleanupFailure.set(t) }
            )
            cleanupDriver.setDaemon(true)
            cleanupDriver.start()
            cleanupDriver.join(5000)

            val expectedReplacementCancels = if (distinct) 1 else 0
            if (
              driver.isAlive || cleanupDriver.isAlive || (cleanupFailure.get() ne null) || continuations.get() != 0 ||
              sourceCancels.get() != 1 || replacementCancels.get() != expectedReplacementCancels
            )
              Some(
                s"$kind distinct=$distinct: driverAlive=${driver.isAlive}, cleanupAlive=${cleanupDriver.isAlive}, " +
                  s"cleanupFailure=${cleanupFailure.get()}, continuations=${continuations.get()}, " +
                  s"sourceCancels=${sourceCancels.get()}, replacementCancels=${replacementCancels.get()}"
              )
            else None
          }
          assertTrue(failures.isEmpty)
        }
      },
      test("collectAll and zipWith cancellation own pending in-flight aggregate results") {
        ZIO.attemptBlocking {
          val failures = List("collectAll", "zipWith").flatMap { kind =>
            val pollEntered        = new CountDownLatch(1)
            val allowPollReturn    = new CountDownLatch(1)
            val polls              = new AtomicInteger(0)
            val sourceCancels      = new AtomicInteger(0)
            val replacementCancels = new AtomicInteger(0)
            val combines           = new AtomicInteger(0)
            val releases           = new AtomicInteger(0)
            val cleanupRef         = new AtomicReference[Async[Unit]]()
            val cleanupFailure     = new AtomicReference[Throwable]()

            val replacement = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int]                   = this
              override private[async] def cancelWithCleanup(): Async[Unit] = {
                replacementCancels.incrementAndGet()
                Async.succeed(())
              }
            }
            val source = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int] =
                if (polls.incrementAndGet() == 1) this
                else {
                  pollEntered.countDown()
                  allowPollReturn.await()
                  replacement
                }
              override private[async] def cancelWithCleanup(): Async[Unit] = {
                sourceCancels.incrementAndGet()
                Async.succeed(())
              }
            }
            val acquisition: Async[Any] =
              if (kind == "collectAll") Async.collectAll(List(source, Async.succeed(2)))
              else source.zipWith(Async.succeed(2)) { (left, right) => combines.incrementAndGet(); left + right }
            val bracket = Async.bracketAsync[Any, Unit](
              () => acquisition,
              _ => Async.succeed(()),
              _ => { releases.incrementAndGet(); Async.succeed(()) }
            )
            val pending = bracket.asInstanceOf[Pollable[Unit]]
            pending.poll(AsyncTestSupport.noopRunnable)

            val driver = new Thread(() => { pending.poll(AsyncTestSupport.noopRunnable); () })
            driver.start()
            pollEntered.await()
            cleanupRef.set(pending.cancelWithCleanup())
            allowPollReturn.countDown()
            driver.join(5000)
            val cleanupDriver = new Thread(() =>
              try cleanupRef.get().block
              catch { case t: Throwable => cleanupFailure.set(t) }
            )
            cleanupDriver.setDaemon(true)
            cleanupDriver.start()
            cleanupDriver.join(5000)

            if (
              driver.isAlive || cleanupDriver.isAlive || (cleanupFailure.get() ne null) || combines.get() != 0 ||
              releases.get() != 0 || sourceCancels.get() != 1 || replacementCancels.get() != 1
            )
              Some(
                s"$kind: driverAlive=${driver.isAlive}, cleanupAlive=${cleanupDriver.isAlive}, " +
                  s"cleanupFailure=${cleanupFailure.get()}, combines=${combines.get()}, releases=${releases.get()}, " +
                  s"sourceCancels=${sourceCancels.get()}, replacementCancels=${replacementCancels.get()}"
              )
            else None
          }
          assertTrue(failures.isEmpty)
        }
      },
      test("collectAll cancellation owns a next child discovered by an in-flight continuation") {
        ZIO.attemptBlocking {
          val nextEntered     = new CountDownLatch(1)
          val allowNextReturn = new CountDownLatch(1)
          val nextCancels     = new AtomicInteger(0)
          val releases        = new AtomicInteger(0)
          val cleanupRef      = new AtomicReference[Async[Unit]]()
          val cleanupFailure  = new AtomicReference[Throwable]()

          val source = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(1)
          }
          val nextChild = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              nextCancels.incrementAndGet()
              Async.succeed(())
            }
          }
          val inputs = new Iterable[Async[Int]] {
            def iterator: Iterator[Async[Int]] = new Iterator[Async[Int]] {
              private var index      = 0
              def hasNext: Boolean   = index < 2
              def next(): Async[Int] = {
                index += 1
                if (index == 1) source
                else {
                  nextEntered.countDown()
                  allowNextReturn.await()
                  nextChild
                }
              }
            }
          }
          val acquisition = Async.collectAll(inputs)
          val bracket     = Async.bracketAsync[List[Int], Unit](
            () => acquisition,
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          val driver  = new Thread(() => { pending.poll(AsyncTestSupport.noopRunnable); () })
          driver.start()
          nextEntered.await()
          cleanupRef.set(pending.cancelWithCleanup())
          allowNextReturn.countDown()
          driver.join(5000)
          val cleanupDriver = new Thread(() =>
            try cleanupRef.get().block
            catch { case t: Throwable => cleanupFailure.set(t) }
          )
          cleanupDriver.setDaemon(true)
          cleanupDriver.start()
          cleanupDriver.join(5000)

          assertTrue(
            !driver.isAlive,
            !cleanupDriver.isAlive,
            cleanupFailure.get() == null,
            nextCancels.get() == 1,
            releases.get() == 0
          )
        }
      },
      test("zipWith cancellation racing a null combine releases the null resource") {
        ZIO.attemptBlocking {
          val bracketRef = new AtomicReference[Pollable[Unit]]()
          val cleanupRef = new AtomicReference[Async[Unit]]()
          val releases   = new AtomicInteger(0)
          val source     = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(1)
          }
          val acquisition = source.zipWith(Async.succeed(2)) { (_, _) =>
            cleanupRef.set(bracketRef.get().cancelWithCleanup())
            null.asInstanceOf[String]
          }
          val bracket = Async.bracketAsync[String, Unit](
            () => acquisition,
            _ => Async.succeed(()),
            resource => {
              if (resource == null) releases.incrementAndGet()
              Async.succeed(())
            }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          bracketRef.set(pending)
          val result = pending.poll(AsyncTestSupport.noopRunnable)
          cleanupRef.get().block
          assertTrue(result.asInstanceOf[AnyRef] eq pending.asInstanceOf[AnyRef], releases.get() == 1)
        }
      },
      test("catchAll cancellation follows an in-flight replacement to release a late successful acquisition") {
        ZIO.attemptBlocking {
          val pollEntered        = new CountDownLatch(1)
          val allowPollReturn    = new CountDownLatch(1)
          val polls              = new AtomicInteger(0)
          val sourceCancels      = new AtomicInteger(0)
          val replacementCancels = new AtomicInteger(0)
          val recoveries         = new AtomicInteger(0)
          val releases           = new AtomicInteger(0)
          val cleanupRef         = new AtomicReference[Async[Unit]]()
          val cleanupFailure     = new AtomicReference[Throwable]()

          val replacement = new Pollable[String] {
            @volatile private var cancelled                              = false
            def poll(onComplete: Runnable): Async[String]                = if (cancelled) Async.succeed("resource") else this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              replacementCancels.incrementAndGet()
              cancelled = true
              Async.succeed(())
            }
          }
          val source = new Pollable[String] {
            def poll(onComplete: Runnable): Async[String] =
              if (polls.incrementAndGet() == 1) this
              else {
                pollEntered.countDown()
                allowPollReturn.await()
                replacement
              }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              sourceCancels.incrementAndGet()
              Async.succeed(())
            }
          }
          val acquisition = source.catchAll { _ => recoveries.incrementAndGet(); Async.succeed("recovered") }
          val bracket     = Async.bracketAsync[String, Unit](
            () => acquisition,
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          pending.poll(AsyncTestSupport.noopRunnable)

          val driver = new Thread(() => { pending.poll(AsyncTestSupport.noopRunnable); () })
          driver.start()
          pollEntered.await()
          cleanupRef.set(pending.cancelWithCleanup())
          allowPollReturn.countDown()
          driver.join(5000)
          val cleanupDriver = new Thread(() =>
            try cleanupRef.get().block
            catch { case t: Throwable => cleanupFailure.set(t) }
          )
          cleanupDriver.setDaemon(true)
          cleanupDriver.start()
          cleanupDriver.join(5000)

          assertTrue(
            !driver.isAlive,
            !cleanupDriver.isAlive,
            cleanupFailure.get() == null,
            sourceCancels.get() == 1,
            replacementCancels.get() == 1,
            recoveries.get() == 0,
            releases.get() == 1
          )
        }
      },
      test("flatMap cancellation observes and releases a late resource from an in-flight continuation child") {
        ZIO.attemptBlocking {
          val childPollEntered = new CountDownLatch(1)
          val allowPollReturn  = new CountDownLatch(1)
          val sourcePolls      = new AtomicInteger(0)
          val childPolls       = new AtomicInteger(0)
          val childCancels     = new AtomicInteger(0)
          val continuations    = new AtomicInteger(0)
          val releases         = new AtomicInteger(0)
          val cleanupRef       = new AtomicReference[Async[Unit]]()
          val cleanupFailure   = new AtomicReference[Throwable]()

          val child = new Pollable[String] {
            @volatile private var cancelled               = false
            def poll(onComplete: Runnable): Async[String] =
              if (childPolls.incrementAndGet() == 1) {
                childPollEntered.countDown()
                allowPollReturn.await()
                this
              } else if (cancelled) Async.succeed("resource")
              else this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              childCancels.incrementAndGet()
              cancelled = true
              Async.succeed(())
            }
          }
          val source = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] =
              if (sourcePolls.incrementAndGet() == 1) this else Async.succeed(())
          }
          val acquisition = source.flatMap { _ => continuations.incrementAndGet(); child }
          val bracket     = Async.bracketAsync[String, Unit](
            () => acquisition,
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          pending.poll(AsyncTestSupport.noopRunnable)

          val driver = new Thread(() => { pending.poll(AsyncTestSupport.noopRunnable); () })
          driver.start()
          childPollEntered.await()
          cleanupRef.set(pending.cancelWithCleanup())
          allowPollReturn.countDown()
          driver.join(5000)
          val cleanupDriver = new Thread(() =>
            try cleanupRef.get().block
            catch { case t: Throwable => cleanupFailure.set(t) }
          )
          cleanupDriver.setDaemon(true)
          cleanupDriver.start()
          cleanupDriver.join(5000)

          assertTrue(
            !driver.isAlive,
            !cleanupDriver.isAlive,
            cleanupFailure.get() == null,
            sourcePolls.get() == 2,
            childPolls.get() == 2,
            childCancels.get() == 1,
            continuations.get() == 1,
            releases.get() == 1
          )
        }
      },
      test("tap cancellation follows an in-flight replacement before releasing its retained resource") {
        ZIO.attemptBlocking {
          val childPollEntered   = new CountDownLatch(1)
          val allowPollReturn    = new CountDownLatch(1)
          val childPolls         = new AtomicInteger(0)
          val childCancels       = new AtomicInteger(0)
          val replacementCancels = new AtomicInteger(0)
          val releases           = new AtomicInteger(0)
          val cleanupRef         = new AtomicReference[Async[Unit]]()
          val cleanupFailure     = new AtomicReference[Throwable]()

          val replacement = new Pollable[Unit] {
            @volatile private var cancelled                              = false
            def poll(onComplete: Runnable): Async[Unit]                  = if (cancelled) Async.succeed(()) else this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              replacementCancels.incrementAndGet()
              cancelled = true
              Async.succeed(())
            }
          }
          val child = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              childPolls.incrementAndGet()
              childPollEntered.countDown()
              allowPollReturn.await()
              replacement
            }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              childCancels.incrementAndGet()
              Async.succeed(())
            }
          }
          val acquisition = Async.succeed("resource").tap(_ => child)
          val bracket     = Async.bracketAsync[String, Unit](
            () => acquisition,
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]

          val driver = new Thread(() => { pending.poll(AsyncTestSupport.noopRunnable); () })
          driver.start()
          childPollEntered.await()
          cleanupRef.set(pending.cancelWithCleanup())
          allowPollReturn.countDown()
          driver.join(5000)
          val cleanupDriver = new Thread(() =>
            try cleanupRef.get().block
            catch { case t: Throwable => cleanupFailure.set(t) }
          )
          cleanupDriver.setDaemon(true)
          cleanupDriver.start()
          cleanupDriver.join(5000)

          assertTrue(
            !driver.isAlive,
            !cleanupDriver.isAlive,
            cleanupFailure.get() == null,
            childPolls.get() == 1,
            childCancels.get() == 1,
            replacementCancels.get() == 1,
            releases.get() == 1
          )
        }
      },
      test("tap cancellation releases its retained resource when the canceled child owns its output") {
        ZIO.attemptBlocking {
          val childCancels   = new AtomicInteger(0)
          val maps           = new AtomicInteger(0)
          val releases       = new AtomicInteger(0)
          val cleanupFailure = new AtomicReference[Throwable]()
          val child          = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit]                  = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              childCancels.incrementAndGet()
              Async.succeed(())
            }
          }
          val acquisition = Async.succeed("resource").tap(_ => child.map { value => maps.incrementAndGet(); value })
          val bracket     = Async.bracketAsync[String, Unit](
            () => acquisition,
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          pending.poll(AsyncTestSupport.noopRunnable)
          val cleanup       = pending.cancelWithCleanup()
          val cleanupDriver = new Thread(() =>
            try cleanup.block
            catch { case t: Throwable => cleanupFailure.set(t) }
          )
          cleanupDriver.setDaemon(true)
          cleanupDriver.start()
          cleanupDriver.join(5000)

          assertTrue(
            !cleanupDriver.isAlive,
            cleanupFailure.get() == null,
            childCancels.get() == 1,
            maps.get() == 0,
            releases.get() == 1
          )
        }
      },
      test("tap cancellation releases its retained resource when child cleanup leaves the child pending") {
        ZIO.attemptBlocking {
          val childCancels = new AtomicInteger(0)
          val releases     = new AtomicInteger(0)
          val child        = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit]                  = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              childCancels.incrementAndGet()
              Async.succeed(())
            }
          }
          val bracket = Async.bracketAsync[String, Unit](
            () => Async.succeed("resource").tap(_ => child),
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          pending.poll(AsyncTestSupport.noopRunnable)
          pending.cancelWithCleanup().block
          assertTrue(childCancels.get() == 1, releases.get() == 1)
        }
      },
      test("tap cancellation releases its retained resource when the canceled child later fails") {
        ZIO.attemptBlocking {
          val childFailure = new RuntimeException("canceled child")
          val childCancels = new AtomicInteger(0)
          val releases     = new AtomicInteger(0)
          val child        = new Pollable[Unit] {
            @volatile private var cancelled                              = false
            def poll(onComplete: Runnable): Async[Unit]                  = if (cancelled) Async.fail(childFailure) else this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              childCancels.incrementAndGet()
              cancelled = true
              Async.succeed(())
            }
          }
          val acquisition = Async.succeed("resource").tap(_ => child)
          val bracket     = Async.bracketAsync[String, Unit](
            () => acquisition,
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          pending.poll(AsyncTestSupport.noopRunnable)
          pending.cancelWithCleanup().block
          assertTrue(childCancels.get() == 1, releases.get() == 1)
        }
      },
      test("tap cancellation racing child failure publication releases its retained resource") {
        ZIO.attemptBlocking {
          val childFailure = new RuntimeException("child")
          val releases     = new AtomicInteger(0)
          val bracketRef   = new AtomicReference[Pollable[Unit]]()
          val cleanupRef   = new AtomicReference[Async[Unit]]()
          val child        = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              cleanupRef.set(bracketRef.get().cancelWithCleanup())
              Async.fail(childFailure)
            }
          }
          val bracket = Async.bracketAsync[String, Unit](
            () => Async.succeed("resource").tap(_ => child),
            _ => Async.succeed(()),
            _ => { releases.incrementAndGet(); Async.succeed(()) }
          )
          val pending = bracket.asInstanceOf[Pollable[Unit]]
          bracketRef.set(pending)
          val result = pending.poll(AsyncTestSupport.noopRunnable)
          cleanupRef.get().block
          assertTrue(result.asInstanceOf[AnyRef] eq pending.asInstanceOf[AnyRef], releases.get() == 1)
        }
      },
      test("reset supervision reports rejection close failure and preserves reset failure as primary") {
        ZIO.attemptBlocking {
          val resetFailure = new RuntimeException("reset")
          val closeFailure = new RuntimeException("close")

          val closeOnly = Async
            .superviseResetUnit(() => Async.succeed(()), () => Async.fail(closeFailure))
            .asInstanceOf[Pollable[Unit]]
          val closeResult =
            try { closeOnly.cancelWithCleanup().block; None }
            catch { case t: Throwable => Some(t) }

          val both = Async
            .superviseResetUnit(() => Async.fail(resetFailure), () => Async.fail(closeFailure))
            .asInstanceOf[Pollable[Unit]]
          val bothResult =
            try { both.cancelWithCleanup().block; None }
            catch { case t: Throwable => Some(t) }

          val nullReset = Async
            .superviseResetUnit(() => Async.fail(null), () => Async.fail(closeFailure))
            .asInstanceOf[Pollable[Unit]]
          val nullResetResult = nullReset.cancelWithCleanup().either.block

          val nullClose = Async
            .superviseResetUnit(() => Async.fail(resetFailure), () => Async.fail(null))
            .asInstanceOf[Pollable[Unit]]
          val nullCloseResult = nullClose.cancelWithCleanup().either.block

          assertTrue(
            closeResult.contains(closeFailure),
            bothResult.contains(resetFailure),
            resetFailure.getSuppressed.toList == List(closeFailure),
            nullResetResult == Left(null),
            nullCloseResult == Left(resetFailure)
          )
        }
      },
      test("reset supervision joins reset before one shared rejection close") {
        ZIO.attemptBlocking {
          val closeConstructionEntered = new CountDownLatch(1)
          val allowCloseConstruction   = new CountDownLatch(1)
          val reset                    = new Completer[Unit]
          val closes                   = new AtomicInteger(0)
          val supervisor               = Async
            .superviseResetUnit(
              () => reset,
              () => {
                closeConstructionEntered.countDown()
                allowCloseConstruction.await()
                closes.incrementAndGet()
                Async.succeed(())
              }
            )
            .asInstanceOf[Pollable[Unit]]

          val firstCleanup  = supervisor.cancelWithCleanup()
          val secondCleanup = supervisor.cancelWithCleanup()
          val secondEntered = new CountDownLatch(1)
          val second        = new Thread(() => { secondEntered.countDown(); secondCleanup.block })
          second.start()
          secondEntered.await(5, TimeUnit.SECONDS)
          val closeDeferred = closeConstructionEntered.getCount == 1L
          reset.succeed(())
          val entered     = closeConstructionEntered.await(5, TimeUnit.SECONDS)
          val closeWaited = second.isAlive
          allowCloseConstruction.countDown()
          second.join(5000)
          firstCleanup.block

          assertTrue(
            closeDeferred,
            entered,
            closeWaited,
            !second.isAlive,
            closes.get() == 1
          )
        }
      },
      test("nested reset supervision leaves final close to its enclosing owner") {
        ZIO.attemptBlocking {
          val reset      = new Completer[Unit]
          val closes     = new AtomicInteger(0)
          val supervisor = Async
            .superviseResetWithinClose(() => reset, () => { closes.incrementAndGet(); Async.succeed(()) })
            .asInstanceOf[Pollable[Unit]]

          val pending = supervisor.poll(AsyncTestSupport.noopRunnable)
          val cleanup = supervisor.cancelWithCleanup()
          reset.succeed(())
          cleanup.block

          assertTrue(
            pending.asInstanceOf[AnyRef] eq supervisor.asInstanceOf[AnyRef],
            closes.get() == 0
          )
        }
      },
      test("nested reset supervision uses only an early close for blocked construction") {
        ZIO.attemptBlocking {
          val entered    = new CountDownLatch(1)
          val release    = new CountDownLatch(1)
          val closes     = new AtomicInteger(0)
          val supervisor = Async
            .superviseResetWithinClose(
              () => { entered.countDown(); release.await(); Async.succeed(()) },
              () => { closes.incrementAndGet(); Async.succeed(()) }
            )
            .asInstanceOf[Pollable[Unit]]
          val driver = new Thread(() => { supervisor.poll(AsyncTestSupport.noopRunnable); () })
          driver.start()
          val resetEntered = entered.await(5, TimeUnit.SECONDS)
          val cleanup      = supervisor.cancelWithCleanup()
          release.countDown()
          driver.join(5000)
          cleanup.block

          assertTrue(resetEntered, !driver.isAlive, closes.get() == 1)
        }
      },
      test("reset cancellation starts an early close while construction is blocked and joins reset") {
        ZIO.attemptBlocking {
          val resetConstructionEntered = new CountDownLatch(1)
          val allowResetConstruction   = new CountDownLatch(1)
          val reset                    = new Completer[Unit]
          val closes                   = new AtomicInteger(0)
          val closeFailure             = new RuntimeException("early close")
          val supervisor               = Async
            .superviseResetUnit(
              () => { resetConstructionEntered.countDown(); allowResetConstruction.await(); reset },
              () => {
                if (closes.incrementAndGet() == 1) Async.fail(closeFailure)
                else Async.succeed(())
              }
            )
            .asInstanceOf[Pollable[Unit]]
          val driver = new Thread(() => { supervisor.poll(AsyncTestSupport.noopRunnable); () })
          driver.start()
          val entered             = resetConstructionEntered.await(5, TimeUnit.SECONDS)
          val cleanup             = supervisor.cancelWithCleanup()
          val earlyCloseCompleted = closes.get() == 1
          allowResetConstruction.countDown()
          driver.join(5000)
          reset.succeed(())
          val observed =
            try { cleanup.block; None }
            catch { case cause: Throwable => Some(cause) }

          assertTrue(
            entered,
            earlyCloseCompleted,
            !driver.isAlive,
            closes.get() == 2,
            observed.contains(closeFailure)
          )
        }
      },
      test("reset supervisor direct poll covers success, failure, replacement, and post-cleanup cancellation") {
        ZIO.attemptBlocking {
          val failure = new RuntimeException("reset poll")
          val success = Async
            .superviseResetUnit(() => Async.succeed(()), () => Async.succeed(()))
            .asInstanceOf[Pollable[Unit]]
          val failed = Async
            .superviseResetUnit(() => Async.fail(failure), () => Async.succeed(()))
            .asInstanceOf[Pollable[Unit]]
          val replacement = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = Async.succeed(())
          }
          val replaced = Async
            .superviseResetUnit(() => replacement, () => Async.succeed(()))
            .asInstanceOf[Pollable[Unit]]

          val successResult     = success.poll(AsyncTestSupport.noopRunnable).either.block
          val failureResult     = failed.poll(AsyncTestSupport.noopRunnable).either.block
          val replacementResult = replaced.poll(AsyncTestSupport.noopRunnable).either.block
          val cleanup           = success.cancelWithCleanup()
          cleanup.block
          val cancelledResult = success.poll(AsyncTestSupport.noopRunnable)

          assertTrue(
            successResult == Right(()),
            failureResult == Left(failure),
            replacementResult == Right(()),
            cancelledResult.either.block == Right(())
          )
        }
      },
      test("reset supervisor serializes a reentrant poll and cancellation poll waits for cleanup") {
        ZIO.attemptBlocking {
          val resetGate                  = new Completer[Unit]
          val closeGate                  = new Completer[Unit]
          val permitWake                 = new AtomicInteger(0)
          val cancelledWake              = new AtomicInteger(0)
          var supervisor: Pollable[Unit] = null
          val reset                      = new Pollable[Unit] {
            private val reentered                       = new AtomicBoolean(false)
            def poll(onComplete: Runnable): Async[Unit] = {
              if (reentered.compareAndSet(false, true))
                supervisor.poll(new Runnable { def run(): Unit = permitWake.incrementAndGet() })
              resetGate.poll(onComplete)
            }
          }
          supervisor = Async.superviseResetUnit(() => reset, () => closeGate).asInstanceOf[Pollable[Unit]]
          val first         = supervisor.poll(AsyncTestSupport.noopRunnable)
          val cleanup       = supervisor.cancelWithCleanup().asInstanceOf[Pollable[Unit]]
          val cancelledPoll = supervisor.poll(new Runnable { def run(): Unit = cancelledWake.incrementAndGet() })
          val cleanupPoll   = cleanup.poll(AsyncTestSupport.noopRunnable)
          resetGate.succeed(())
          val closePending = cleanup.poll(AsyncTestSupport.noopRunnable)
          closeGate.succeed(())
          cleanup.block

          assertTrue(
            first.asInstanceOf[AnyRef] eq supervisor.asInstanceOf[AnyRef],
            cancelledPoll.asInstanceOf[AnyRef] eq supervisor.asInstanceOf[AnyRef],
            cleanupPoll.asInstanceOf[AnyRef] eq cleanup.asInstanceOf[AnyRef],
            closePending.asInstanceOf[AnyRef] eq cleanup.asInstanceOf[AnyRef],
            permitWake.get() > 0,
            cancelledWake.get() > 0
          )
        }
      },
      test("shared cancellation wakes every observer and publishes one fallback after cleanup") {
        ZIO.attemptBlocking {
          val childCleanup = new Completer[Unit]
          val cancels      = new AtomicInteger(0)
          val fallbacks    = new AtomicInteger(0)
          val child        = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = this
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancels.incrementAndGet()
              childCleanup
            }
          }
          val shared = Async.cancelTo(child, () => { fallbacks.incrementAndGet(); Async.succeed(42) })
          val wakes  = new CountDownLatch(2)
          shared.poll(new Runnable { def run(): Unit = wakes.countDown() })
          shared.poll(new Runnable { def run(): Unit = wakes.countDown() })
          val cleanup        = shared.cancelWithCleanup()
          val cleanerEntered = new CountDownLatch(1)
          val cleaner        = new Thread(() => { cleanerEntered.countDown(); cleanup.block })
          cleaner.start()
          cleanerEntered.await()
          val cleanupWaited = cleaner.isAlive
          childCleanup.succeed(())
          val observersWoke = wakes.await(5, TimeUnit.SECONDS)
          cleaner.join(5000)

          assertTrue(
            cleanupWaited,
            observersWoke,
            !cleaner.isAlive,
            shared.poll(AsyncTestSupport.noopRunnable) == Async.succeed(42),
            cancels.get() == 1,
            fallbacks.get() == 1
          )
        }
      },
      test("shared cancellation hands off same, distinct, and terminal in-flight poll results") {
        ZIO.attemptBlocking {
          final case class Outcome(kind: String, childCancels: Int, replacementCancels: Int)
          val outcomes = List("same", "distinct", "terminal").map { kind =>
            val childCancels          = new AtomicInteger(0)
            val replacementCancels    = new AtomicInteger(0)
            var shared: Pollable[Int] = null
            var cleanup: Async[Unit]  = null
            val replacement           = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int]                   = this
              override private[async] def cancelWithCleanup(): Async[Unit] = {
                replacementCancels.incrementAndGet()
                Async.succeed(())
              }
            }
            val child = new Pollable[Int] {
              def poll(onComplete: Runnable): Async[Int] = {
                cleanup = shared.cancelWithCleanup()
                kind match {
                  case "same"     => this
                  case "distinct" => replacement
                  case _          => Async.succeed(1)
                }
              }
              override private[async] def cancelWithCleanup(): Async[Unit] = {
                childCancels.incrementAndGet()
                Async.succeed(())
              }
            }
            shared = Async.shareCancellation(child)
            val result = shared.poll(AsyncTestSupport.noopRunnable)
            cleanup.block
            assertTrue(result.asInstanceOf[AnyRef] eq shared.asInstanceOf[AnyRef])
            Outcome(kind, childCancels.get(), replacementCancels.get())
          }

          assertTrue(
            outcomes == List(
              Outcome("same", 1, 0),
              Outcome("distinct", 1, 1),
              Outcome("terminal", 1, 0)
            )
          )
        }
      },
      test("Completer settles exactly once under many racing succeed/fail callers") {
        ZIO.attemptBlocking {
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[Int]
            val racers  = 8
            val start   = new CountDownLatch(1)
            val done    = new CountDownLatch(racers)
            val threads = (0 until racers).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  start.await()
                  if (id % 2 == 0) c.succeed(id) else c.fail(new RuntimeException(s"boom-$id"))
                  done.countDown()
                }
              })
              t.setDaemon(true)
              t.start()
              t
            }
            start.countDown()
            done.await()
            // The settled AsyncTestSupport.outcome must be stable across independent polls.
            val o1      = c.poll(AsyncTestSupport.noopRunnable)
            val o2      = c.poll(AsyncTestSupport.noopRunnable)
            val v1: Any = o1
            val v2: Any = o2
            val settled =
              !v1.isInstanceOf[Completer[_]] // a still-pending completer poll returns `this`
            val stable = (v1, v2) match {
              case (a: Failure, b: Failure) => a.cause eq b.cause
              case (a, b)                   => a == b
            }
            if (!settled) anomaly = Some(s"trial $i: completer never settled despite ${racers} settlers")
            else if (!stable) anomaly = Some(s"trial $i: completer outcome not stable across polls: $v1 vs $v2")
            threads.foreach(_.join())
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("Completer poll racing settle eventually observes the value (no lost wakeup / no stuck pending)") {
        ZIO.attemptBlocking {
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c      = new Completer[Int]
            val start  = new CountDownLatch(1)
            val woke   = new AtomicInteger(0)
            val waker  = new Runnable { def run(): Unit = { woke.incrementAndGet(); () } }
            val poller = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.poll(waker); () }
            })
            val settler = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(42); () }
            })
            poller.setDaemon(true); settler.setDaemon(true)
            poller.start(); settler.start()
            start.countDown()
            poller.join(); settler.join()
            // After both have run, a fresh poll must observe the settled value:
            // either the racing poll registered a waker that was woken, or it lost
            // the CAS and observed the value directly; either way the completer is
            // settled now.
            val v: Any = c.poll(AsyncTestSupport.noopRunnable)
            val ok     = v match {
              case _: Completer[_] => false
              case other           => other == (42: Any)
            }
            if (!ok) anomaly = Some(s"trial $i: completer not settled to 42 after race: $v")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("every distinct waker registered while pending is resumed under settle contention (waiter chain)") {
        ZIO.attemptBlocking {
          // Round-2 surface: Completer keeps a CAS-linked chain of distinct
          // waiters. Under contention each poller must either be woken by the
          // settle chain-walk or lose the CAS and observe the settled value
          // directly — never silently dropped from the chain.
          val trials  = 500
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[Int]
            val pollers = 8
            val start   = new CountDownLatch(1)
            val done    = new CountDownLatch(pollers)
            val resumed = new AtomicInteger(0)
            (0 until pollers).foreach { _ =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  start.await()
                  val waker  = new Runnable { def run(): Unit = { resumed.incrementAndGet(); () } }
                  val r: Any = c.poll(waker)
                  if (!r.isInstanceOf[Completer[_]]) resumed.incrementAndGet() // observed the value directly
                  done.countDown()
                }
              })
              t.setDaemon(true)
              t.start()
            }
            val settler = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(7) }
            })
            settler.setDaemon(true)
            settler.start()
            start.countDown()
            done.await()
            settler.join()
            val n = resumed.get()
            if (n != pollers) anomaly = Some(s"trial $i: $n of $pollers pollers resumed (lost wakeup or double count)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("Completer peek racing settle observes only pending-or-the-value (null settle included)") {
        ZIO.attemptBlocking {
          // `peek` is the registration-free snapshot: while a settle is in
          // flight on another thread it must observe either the still-pending
          // completer itself or the exact settled value — never a foreign
          // state (in particular the internal null-value sentinel must not
          // escape, and a registered waiter chain must read as pending).
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val useNull       = i % 2 == 1
            val expected: Any = if (useNull) null else "v"
            val c             = new Completer[String]
            if (i % 4 < 2) { c.poll(AsyncTestSupport.noopRunnable); () } // chain a waiter first on half the trials
            val start   = new CountDownLatch(1)
            val settler = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(if (useNull) null else "v") }
            })
            settler.setDaemon(true)
            settler.start()
            start.countDown()
            var settledSeen = false
            var spins       = 0
            while (!settledSeen && anomaly.isEmpty && spins < 10000000) {
              val p: Any = c.peek
              if (p.asInstanceOf[AnyRef] eq c) ()        // still pending: peek returns the completer
              else if (p == expected) settledSeen = true // settled: the exact value (or raw null)
              else anomaly = Some(s"trial $i: peek observed $p (expected pending or $expected)")
              spins += 1
            }
            settler.join()
            val fin: Any = c.peek
            if (anomaly.isEmpty && fin != expected)
              anomaly = Some(s"trial $i: post-settle peek observed $fin instead of $expected")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("Running pollers racing a null-terminal publish are all resumed and observe null") {
        ZIO.attemptBlocking {
          // Round-3 surface: the JVM runner publishes a raw-null success through
          // the `NullTerminal` sentinel. Pollers racing that publish must either
          // be woken by `wakeAll` or observe the terminal directly on `poll` —
          // never stay pending, and never read the sentinel as a value.
          val trials  = 500
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[String]
            val running = c.peek.start
            val pollers = 4
            val start   = new CountDownLatch(1)
            val done    = new CountDownLatch(pollers)
            val resumed = new AtomicInteger(0)
            (0 until pollers).foreach { _ =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  start.await()
                  val waker  = new Runnable { def run(): Unit = { resumed.incrementAndGet(); () } }
                  val r: Any = running.poll(waker)
                  if (!r.isInstanceOf[Async.Running[_]]) resumed.incrementAndGet() // observed terminal directly
                  done.countDown()
                }
              })
              t.setDaemon(true)
              t.start()
            }
            val settler = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(null) }
            })
            settler.setDaemon(true)
            settler.start()
            start.countDown()
            done.await()
            settler.join()
            // Wait for the worker to publish, then check every poller resumed and
            // a fresh poll exposes the raw null value (not the sentinel, not `this`).
            var spins = 0
            while (AsyncTestSupport.isPending(running.poll(AsyncTestSupport.noopRunnable)) && spins < 5000) {
              Thread.sleep(1); spins += 1
            }
            val terminal: Any = running.poll(AsyncTestSupport.noopRunnable)
            var waitWake      = 0
            while (resumed.get() < pollers && waitWake < 5000) { Thread.sleep(1); waitWake += 1 }
            if (terminal != null) anomaly = Some(s"trial $i: terminal was $terminal, expected raw null")
            else if (resumed.get() < pollers)
              anomaly = Some(s"trial $i: only ${resumed.get()} of $pollers pollers resumed (lost wakeup)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("concurrent fan-out via the shared Running handle runs the user function exactly once") {
        // Concurrent fan-out by re-driving the RAW `Async` from two threads is
        // UNDEFINED (the `done` memo is a plain `var`, not a synchronizer — see
        // the slowPath memo note; it cannot happen on single-threaded JS). The
        // SUPPORTED way to fan one `Async` out to concurrent consumers is to
        // share the `Running` handle from `fa.start`: it drives the underlying
        // `Async` exactly once on its worker and publishes the result through an
        // atomic, so any number of threads blocking the SAME handle observe that
        // one result. This pins that supported guarantee.
        ZIO.attemptBlocking {
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val calls   = new AtomicInteger(0)
            val c       = new Completer[Int]
            val running = c.peek.map { x => calls.incrementAndGet(); x + 1 }.start
            c.succeed(1)
            val barrier = new CyclicBarrier(2)
            val results = new Array[Any](2)
            val threads = (0 until 2).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  barrier.await()
                  results(id) =
                    try running.block
                    catch { case t: Throwable => t }
                }
              })
              t.setDaemon(true)
              t.start()
              t
            }
            threads.foreach(_.join())
            val n = calls.get()
            if (n != 1)
              anomaly = Some(s"trial $i: user function ran $n times via shared Running handle (must be 1)")
            else if (!results.forall(_ == 2))
              anomaly = Some(s"trial $i: shared handle delivered ${results.toList} (must be [2, 2])")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("concurrent fan-out of collectAll via the shared Running handle yields one correct list") {
        // As above, for `collectAll`: concurrent raw re-drive is undefined (it
        // may corrupt the shared drain buffer), so fan out by sharing one
        // `Running` handle, driven once on its worker. Concurrent consumers all
        // observe the same published list.
        ZIO.attemptBlocking {
          val trials  = 2000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[Int]
            val running =
              Async.collectAll(List[Async[Int]](Async.succeed(0), c.peek, Async.succeed(2))).start
            c.succeed(1)
            val barrier = new CyclicBarrier(2)
            val results = new Array[Any](2)
            val threads = (0 until 2).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  barrier.await()
                  results(id) =
                    try running.block
                    catch { case t: Throwable => t }
                }
              })
              t.setDaemon(true)
              t.start()
              t
            }
            threads.foreach(_.join())
            if (!results.forall(_ == List(0, 1, 2)))
              anomaly =
                Some(s"trial $i: shared collectAll handle delivered ${results.toList} (must be two List(0, 1, 2))")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("unsafeRunAsync delivers the callback at most once when completion races cancel") {
        ZIO.attemptBlocking {
          val trials  = 1000
          var anomaly = Option.empty[String]
          var i       = 0
          while (i < trials && anomaly.isEmpty) {
            val c         = new Completer[Int]
            val calls     = new AtomicInteger(0)
            val running   = AsyncTestSupport.startTap(c.peek)(_ => calls.incrementAndGet())
            val start     = new CountDownLatch(1)
            val completer = new Thread(new Runnable {
              def run(): Unit = { start.await(); c.succeed(1) }
            })
            val canceller = new Thread(new Runnable {
              def run(): Unit = { start.await(); running.cancel() }
            })
            completer.setDaemon(true); canceller.setDaemon(true)
            completer.start(); canceller.start()
            start.countDown()
            completer.join(); canceller.join()
            // Give the worker a moment to deliver if completion won the race.
            Thread.sleep(1)
            val n = calls.get()
            if (n > 1) anomaly = Some(s"trial $i: callback delivered $n times (must be at most once)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("runner polls an already-completed replacement before parking") {
        ZIO.attemptBlocking {
          val replacement = new Completer[Int]
          replacement.succeed(42)
          val predecessor = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = replacement
          }
          val running = AsyncTestSupport.fromPollable(predecessor).start
          val result  = new AtomicReference[Any](null)
          val blocker = new Thread(() => result.set(running.block))
          blocker.setDaemon(true)
          blocker.start()
          blocker.join(1000)

          assertTrue(!blocker.isAlive, result.get() == 42)
        }
      },
      test("runner re-polls continuations that mask a distinct ready replacement") {
        ZIO.attemptBlocking {
          def readyReplacement(): Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = Async.succeed(41)
          }
          def predecessor(): Pollable[Int] = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = readyReplacement()
          }
          def await(effect: Async[Int]): (Boolean, Any) = {
            val result  = new AtomicReference[Any](null)
            val running = effect.start
            val blocker = new Thread(() => result.set(running.block))
            blocker.setDaemon(true)
            blocker.start()
            blocker.join(1000)
            if (blocker.isAlive) running.cancel()
            (!blocker.isAlive, result.get())
          }

          val mapped     = await(AsyncTestSupport.fromPollable(predecessor()).map(_ + 1))
          val flatMapped =
            await(AsyncTestSupport.fromPollable(predecessor()).flatMap(value => Async.succeed(value + 1)))
          val recovered = await(AsyncTestSupport.fromPollable(predecessor()).catchAll(_ => Async.succeed(0)))

          assertTrue(mapped == ((true, 42)), flatMapped == ((true, 42)), recovered == ((true, 41)))
        }
      },
      test("runner converts a poll throw into the exact terminal failure") {
        ZIO.attemptBlocking {
          val failure = new RuntimeException("poll")
          val leaf    = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = throw failure
          }
          val observed =
            try { AsyncTestSupport.fromPollable(leaf).start.block; None }
            catch { case cause: Throwable => Some(cause) }

          assertTrue(observed.contains(failure))
        }
      },
      test("runner isolates a throwing completion observer and wakes the remaining observer") {
        ZIO.attemptBlocking {
          val source  = new Completer[Int]
          val running = source.peek.start
          val woke    = new CountDownLatch(1)
          running.poll(new Runnable { def run(): Unit = throw new RuntimeException("waker") })
          running.poll(new Runnable { def run(): Unit = woke.countDown() })
          source.succeed(42)

          assertTrue(woke.await(5, TimeUnit.SECONDS), running.block == 42)
        }
      },
      test("cancellation cleanup wakes after shared child publishes a completed replacement") {
        ZIO.attemptBlocking {
          val operationPolled      = new CountDownLatch(1)
          val cancellations        = new AtomicInteger(0)
          val completedReplacement = new Completer[Unit]
          completedReplacement.succeed(())
          val cleanupPredecessor = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = completedReplacement
          }
          val operation = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int]                   = { operationPolled.countDown(); this }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancellations.incrementAndGet()
              Async.shareCancellation(cleanupPredecessor)
            }
          }
          val running = AsyncTestSupport.fromPollable(operation).start
          val entered = operationPolled.await(5, TimeUnit.SECONDS)
          val cleanup = Async.cancelWithCleanup(running).start
          val result  = new AtomicReference[Either[Throwable, Unit]]()
          val blocker = new Thread(() =>
            result.set(
              try Right(cleanup.block)
              catch { case cause: Throwable => Left(cause) }
            )
          )
          blocker.setDaemon(true)
          blocker.start()
          blocker.join(1000)

          assertTrue(entered, !blocker.isAlive, result.get() == Right(()), cancellations.get() == 1)
        }
      },
      test("nested continuation cancellation uses acyclic per-node cleanup suffixes") {
        ZIO.attemptBlocking {
          val entered       = new CountDownLatch(1)
          val release       = new CountDownLatch(1)
          val cancellations = new AtomicInteger
          val leaf          = new Pollable[Int] {
            def poll(onComplete: Runnable): Async[Int] = {
              entered.countDown()
              release.await()
              this
            }
            override private[async] def cancelWithCleanup(): Async[Unit] = {
              cancellations.incrementAndGet()
              Async.succeed(())
            }
          }
          val root = AsyncTestSupport
            .fromPollable(leaf)
            .map(_ + 1)
            .flatMap(value => Async.succeed(value))
            .catchAll(Async.fail)
            .asInstanceOf[Pollable[Int]]
          val poller = new Thread(new Runnable {
            def run(): Unit = { root.poll(AsyncTestSupport.noopRunnable); () }
          })
          poller.setDaemon(true)
          poller.start()
          val observed = entered.await(5, TimeUnit.SECONDS)
          val cleanup  = root.cancelWithCleanup()
          release.countDown()
          poller.join(5000)
          val result = cleanup.either.block

          assertTrue(observed, !poller.isAlive, result == Right(()), cancellations.get() == 1)
        }
      },
      test("many threads blocking ONE shared Running handle all wake and agree when completion lands after they park") {
        // Directly stresses the SuspendedRunning lost-wakeup window: the
        // off-thread leaf completes AFTER every blocker has already entered
        // `.block` (and is parking inside `registerOnComplete`/park), so the
        // terminal publish + lock-guarded `wakeAll` must drain EVERY waiter
        // added before it ran — no blocker may stay parked, and all must agree
        // on the single published value exactly once. (Existing fan-out probes
        // complete the leaf BEFORE blocking; this one inverts the order so the
        // waiter chain is non-empty at publish time.)
        ZIO.attemptBlocking {
          val trials   = 400
          val blockers = 8
          var anomaly  = Option.empty[String]
          var i        = 0
          while (i < trials && anomaly.isEmpty) {
            val c       = new Completer[Int]
            val running = c.peek.map(_ + 1).start
            val parked  = new CountDownLatch(blockers)
            val release = new CountDownLatch(1)
            val results = new Array[Any](blockers)
            val threads = (0 until blockers).map { id =>
              val t = new Thread(new Runnable {
                def run(): Unit = {
                  parked.countDown() // announce intent to block
                  release.await()    // all blockers released together
                  results(id) =
                    try running.block
                    catch { case t: Throwable => t }
                }
              })
              t.setDaemon(true); t.start(); t
            }
            parked.await()
            release.countDown()
            // Complete off-thread only AFTER releasing the blockers, so the
            // publish races them parking — the waiter chain is populated when
            // wakeAll runs (or a late blocker observes the terminal directly).
            val settler = new Thread(new Runnable {
              def run(): Unit = { Thread.sleep(2); c.succeed(41) }
            })
            settler.setDaemon(true); settler.start()
            threads.foreach(_.join(5000))
            settler.join(5000)
            val stuck = threads.zipWithIndex.collect { case (t, idx) if t.isAlive => idx }
            if (stuck.nonEmpty) anomaly = Some(s"trial $i: blockers $stuck never woke (lost wakeup)")
            else if (!results.forall(_ == 42))
              anomaly = Some(s"trial $i: blockers disagreed/wrong: ${results.toList} (all must be 42)")
            i += 1
          }
          assertTrue(anomaly.isEmpty)
        }
      },
      test("selector replays a slot wake that runs before selection waiter registration") {
        ZIO.attemptBlocking {
          val outerWake = new CountDownLatch(1)
          val input     = new Pollable[Int] {
            private var first                          = true
            def poll(onComplete: Runnable): Async[Int] = synchronized {
              if (first) {
                first = false
                onComplete.run()
                ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS)
                this
              } else Async.succeed(42)
            }
          }
          val selector  = Async.selector(Vector[Async[Int]](input))
          val selection = selector.select.asInstanceOf[Pollable[(Int, Int)]]
          val pending   = selection.poll(new Runnable { def run(): Unit = outerWake.countDown() })
          val woke      = outerWake.await(5, TimeUnit.SECONDS)
          val result    = selection.block

          assertTrue(
            pending.asInstanceOf[AnyRef] eq selection.asInstanceOf[AnyRef],
            woke,
            result == ((0, 42))
          )
        }
      }
    )
  )
}
