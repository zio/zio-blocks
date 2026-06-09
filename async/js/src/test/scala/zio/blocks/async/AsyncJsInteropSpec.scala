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

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext

import zio.ZIO
import zio.test._

/**
 * Scala.js-only conversions between [[Async]] and `scala.concurrent.Future` /
 * `js.Promise`. The JVM has a separate suite (`AsyncInteropSpec`) for the same
 * operations against `Future` / `CompletionStage`.
 *
 * No `.block` here — JavaScript can't block. All assertions go through a
 * `ZIO.fromFuture` that completes when the underlying microtask resolves.
 */
object AsyncJsInteropSpec extends ZIOSpecDefault {

  private implicit val ec: ExecutionContext = JSExecutionContext.queue

  /**
   * A chain of distinct pollables (see `AsyncRunSpec`): each `poll` arms the
   * waker and returns the NEXT, brand-new pollable, never `this`. `toFuture`'s
   * microtask driver only walks the chain to completion if it advances to the
   * pollable returned by `poll` rather than re-polling the original.
   */
  private final class StepChain(remaining: Int, taken: Int) extends Pollable[Int] {
    def poll(waker: Waker): Async[Int] =
      if (remaining <= 0) Async.succeed(taken)
      else {
        waker.wake()
        new StepChain(remaining - 1, taken + 1)
      }
  }

  def spec = suite("AsyncJsInteropSpec")(
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
      test("toFuture: ready value collapses to Future.successful") {
        ZIO.fromFuture(_ => AsyncInterop.toFuture(Async.succeed(3))).map(v => assertTrue(v == 3))
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
      test("toFuture: pending pollable resolves via microtask polling") {
        // Create a Completer that fires from a queued microtask so it does
        // NOT complete inside the promise body — the pollable path is taken.
        val a = Async.promiseInternal[Int] { c =>
          js.timers.setTimeout(0.0)(c.succeed(99))
          ()
        }
        ZIO.fromFuture(_ => AsyncInterop.toFuture(a)).map(v => assertTrue(v == 99))
      },
      test("toFuture: driver advances to the pollable returned by poll (not re-polling the original)") {
        ZIO.fromFuture(_ => AsyncInterop.toFuture(new StepChain(5, 0))).map(v => assertTrue(v == 5))
      }
    ),
    suite("js.Promise ↔ Async")(
      test("fromJsPromise: resolved promise yields the value") {
        val pr: js.Promise[Int] = js.Promise.resolve[Int](42)
        ZIO
          .fromFuture(_ => AsyncInterop.toFuture(AsyncInterop.fromJsPromise(pr)))
          .map(v => assertTrue(v == 42))
      },
      test("toJsPromise: ready value resolves the JS promise") {
        val jp = AsyncInterop.toJsPromise(Async.succeed("ok"))
        ZIO.fromFuture(_ => jp.toFuture).map(v => assertTrue(v == "ok"))
      },
      test("toJsPromise: failure rejects the JS promise") {
        val boom = new RuntimeException("rej")
        val jp   = AsyncInterop.toJsPromise(Async.fail(boom))
        ZIO
          .fromFuture(_ => jp.toFuture)
          .either
          .map {
            case Left(t)  => assertTrue(t eq boom)
            case Right(_) => assertTrue(false)
          }
      }
    )
  )
}
