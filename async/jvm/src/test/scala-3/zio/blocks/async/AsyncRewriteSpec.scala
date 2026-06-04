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

import zio.test._

import java.util.concurrent.atomic.AtomicReference

/**
 * Proves the Scala 3 `Async.async { ... .await ... }` rewrite is real — i.e.
 * dotty-cps-async turns `.await` into a non-blocking `flatMap`/`map` chain
 * rather than the blocking [[Async.block]] escape hatch.
 *
 * The lexical restriction (`.await` outside `Async.async` is a compile error)
 * is checked separately in `AsyncAwaitCompileErrorSpec` (under `scala-3.4+`,
 * because `scala.compiletime.testing.typeChecks` is unavailable on 3.3.x).
 *
 * JVM-only because the "pending await did not block construction" assertion
 * needs threads. (On JS the rewrite is identical, but it cannot be probed this
 * way.)
 */
object AsyncRewriteSpec extends ZIOSpecDefault {

  def spec = suite("AsyncRewriteSpec")(
    test("a pending `.await` is rewritten to a non-blocking chain (construction does not block)") {
      val cRef        = new AtomicReference[Completer[Int]]()
      val pending     = Async.promiseInternal[Int](c => cRef.set(c))
      val constructed = new AtomicReference[Async[Int]]()

      val builder = new Thread(() => constructed.set(Async.async(pending.await + 1)))
      builder.setDaemon(true)
      builder.start()
      builder.join(2000)

      val finishedWithoutBlocking = !builder.isAlive
      // If the rewrite had instead blocked, the builder thread would still be
      // parked here; complete the completer so the test can never hang.
      if (builder.isAlive) cRef.get().succeed(-1)

      val fa = constructed.get()
      assertTrue(
        finishedWithoutBlocking,
        fa != null,
        fa.asInstanceOf[Any].isInstanceOf[Pollable[?]]
      )
    },
    test("the rewritten pending await yields the correct value when driven") {
      val cRef    = new AtomicReference[Completer[Int]]()
      val pending = Async.promiseInternal[Int](c => cRef.set(c))
      val fa      = Async.async(pending.await + 1)

      val worker = new Thread(() => {
        Thread.sleep(25)
        cRef.get().succeed(41)
      })
      worker.setDaemon(true)
      worker.start()

      assertTrue(fa.block == 42)
    },
    test("try/catch over a failed await is routed through CpsTryMonad") {
      val boom = new RuntimeException("boom")
      val fa   = Async.async {
        try Async.fail(boom).await
        catch { case _: RuntimeException => 42 }
      }
      assertTrue(fa.block == 42)
    }
  )
}
