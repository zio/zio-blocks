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

import java.util.concurrent.{CompletableFuture, CompletionException, ExecutionException, Executors, TimeUnit}

import scala.concurrent.{ExecutionContext, Future, Promise}

import zio.ZIO
import zio.test._

/**
 * JVM-only conversions between [[Async]] and `scala.concurrent.Future` /
 * `java.util.concurrent.CompletionStage`. Covers:
 *
 *   - sync-completed and pending inputs (both directions)
 *   - success and failure propagation
 *   - `CompletionException` unwrapping on the `CompletionStage` side
 */
object AsyncInteropSpec extends ZIOSpecDefault {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  def spec = suite("AsyncInteropSpec")(
    suite("Future ↔ Async")(
      test("fromFuture: already-succeeded collapses to a value") {
        val r = AsyncInterop.fromFuture(Future.successful(7)).block
        assertTrue(r == 7)
      },
      test("fromFuture: already-failed collapses to a fail") {
        val boom   = new RuntimeException("boom")
        val r      = AsyncInterop.fromFuture(Future.failed(boom))
        val thrown = scala.util.Try(r.block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("fromFuture: pending future completes asynchronously") {
        ZIO.attemptBlocking {
          val p = Promise[Int]()
          val t = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); p.success(11); () }
          })
          t.setDaemon(true)
          t.start()
          val r = AsyncInterop.fromFuture(p.future).block
          assertTrue(r == 11)
        }
      },
      test("fromFuture: pending future that fails surfaces the cause") {
        ZIO.attemptBlocking {
          val boom = new RuntimeException("late")
          val p    = Promise[Int]()
          val t    = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); p.failure(boom); () }
          })
          t.setDaemon(true)
          t.start()
          val thrown = scala.util.Try(AsyncInterop.fromFuture(p.future).block).failed.toOption
          assertTrue(thrown.contains(boom))
        }
      },
      test("toFuture: ready value collapses to Future.successful") {
        ZIO
          .fromFuture(_ => AsyncInterop.toFuture(Async.succeed(3)))
          .map(v => assertTrue(v == 3))
      },
      test("toFuture: failure collapses to Future.failed") {
        val boom = new RuntimeException("nope")
        ZIO
          .fromFuture(_ => AsyncInterop.toFuture(Async.fail(boom)))
          .either
          .map {
            case Left(t)  => assertTrue(t eq boom)
            case Right(_) => assertTrue(false)
          }
      },
      test("toFuture: suspended pollable resolves via the EC") {
        val a = Async.promiseInternal[Int] { c =>
          val t = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); c.succeed(99) }
          })
          t.setDaemon(true)
          t.start()
        }
        ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).map(v => assertTrue(v == 99))
      },
      test("toFuture: suspended pollable that fails surfaces the cause via the EC") {
        val boom = new RuntimeException("late-future-fail")
        val a    = Async.promiseInternal[Int] { c =>
          val t = new Thread(new Runnable {
            def run(): Unit = { Thread.sleep(30); c.fail(boom) }
          })
          t.setDaemon(true)
          t.start()
        }
        ZIO
          .fromFuture(_ => AsyncInterop.toFuture(a))
          .either
          .map {
            case Left(t)  => assertTrue(t eq boom)
            case Right(_) => assertTrue(false)
          }
      }
    ),
    suite("CompletionStage ↔ Async")(
      test("fromCompletionStage: already-completed collapses to a value") {
        val cf = CompletableFuture.completedFuture(5)
        val r  = AsyncInterop.fromCompletionStage(cf).block
        assertTrue(r == 5)
      },
      test("fromCompletionStage: already-failed unwraps the cause") {
        val boom = new RuntimeException("ce")
        val cf   = new CompletableFuture[Int]()
        cf.completeExceptionally(boom)
        val thrown = scala.util.Try(AsyncInterop.fromCompletionStage(cf).block).failed.toOption
        assertTrue(thrown.contains(boom))
      },
      test("fromCompletionStage: pending stage completes asynchronously") {
        ZIO.attemptBlocking {
          val ex = Executors.newSingleThreadExecutor()
          try {
            val cf = CompletableFuture.supplyAsync[Int](
              () => { Thread.sleep(30); 21 },
              ex
            )
            val r = AsyncInterop.fromCompletionStage(cf).block
            assertTrue(r == 21)
          } finally {
            ex.shutdown(); ex.awaitTermination(2, TimeUnit.SECONDS); ()
          }
        }
      },
      test("fromCompletionStage: pending failure is unwrapped from CompletionException") {
        ZIO.attemptBlocking {
          val boom = new RuntimeException("late-ce")
          val ex   = Executors.newSingleThreadExecutor()
          try {
            val cf = CompletableFuture.supplyAsync[Int](
              () => { Thread.sleep(30); throw boom },
              ex
            )
            val thrown = scala.util.Try(AsyncInterop.fromCompletionStage(cf).block).failed.toOption
            // CompletableFuture wraps in CompletionException; interop unwraps it.
            assertTrue(thrown.contains(boom))
          } finally {
            ex.shutdown(); ex.awaitTermination(2, TimeUnit.SECONDS); ()
          }
        }
      },
      test("toCompletableFuture: ready value completes immediately") {
        ZIO.attemptBlocking {
          val cf = AsyncInterop.toCompletableFuture(Async.succeed(8))
          assertTrue(cf.isDone, cf.get() == 8)
        }
      },
      test("toCompletableFuture: failure completes exceptionally") {
        ZIO.attemptBlocking {
          val boom   = new RuntimeException("nope")
          val cf     = AsyncInterop.toCompletableFuture(Async.fail(boom))
          val thrown = scala.util.Try(cf.get()).failed.toOption
          assertTrue(thrown.exists {
            case ce: CompletionException => ce.getCause eq boom
            case ee: ExecutionException  => ee.getCause eq boom
            case other                   => other eq boom
          })
        }
      },
      test("toCompletableFuture: suspended pollable completes via the EC") {
        ZIO.attemptBlocking {
          val a = Async.promiseInternal[Int] { c =>
            val t = new Thread(new Runnable {
              def run(): Unit = { Thread.sleep(30); c.succeed(77) }
            })
            t.setDaemon(true)
            t.start()
          }
          val cf = AsyncInterop.toCompletableFuture(a)
          assertTrue(cf.get(2, TimeUnit.SECONDS) == 77)
        }
      },
      test("toCompletableFuture: suspended pollable that fails completes exceptionally via the EC") {
        ZIO.attemptBlocking {
          val boom = new RuntimeException("late-cf-fail")
          val a    = Async.promiseInternal[Int] { c =>
            val t = new Thread(new Runnable {
              def run(): Unit = { Thread.sleep(30); c.fail(boom) }
            })
            t.setDaemon(true)
            t.start()
          }
          val cf     = AsyncInterop.toCompletableFuture(a)
          val thrown = scala.util.Try(cf.get(2, TimeUnit.SECONDS)).failed.toOption
          assertTrue(thrown.exists {
            case ce: CompletionException => ce.getCause eq boom
            case ee: ExecutionException  => ee.getCause eq boom
            case other                   => other eq boom
          })
        }
      }
    )
  )
}
