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

  /**
   * A user API that happens to have a method named `await` — the rewrite must
   * only touch the `zio.blocks.async` `.await` extension, never a same-named
   * user method (the Scala 2 macro rejects the latter with a diagnostic).
   */
  private object legacyClient {
    var calls: Int                = 0
    def await[T](fa: Async[T]): T = { calls += 1; fa.block }
  }

  def spec = suite("AsyncRewriteSpec")(
    test("a user method named `await` is not hijacked by the rewrite") {
      legacyClient.calls = 0
      val r = Async.async {
        legacyClient.await(Async.succeed(21)) * 2
      }.block
      assertTrue(r == 42, legacyClient.calls == 1)
    },
    test("a nested Async.async block awaited by the outer one composes") {
      val r = Async.async {
        val inner = Async.async(Async.succeed(20).await + 1)
        inner.await * 2
      }.block
      assertTrue(r == 42)
    },
    test("`.await` imported under a rename is still rewritten") {
      import zio.blocks.async.{await => waitFor}
      val r = Async.async(Async.succeed(21).waitFor * 2).block
      assertTrue(r == 42)
    },
    test("a local (non-awaiting) lazy val alongside an await compiles and runs") {
      // Only a lazy val whose INITIALIZER awaits is rejected; an ordinary lazy
      // val sharing the block with awaits must pass through (parity with the
      // Scala 2 macro probe of the same shape).
      val r = Async.async {
        lazy val k = 21
        Async.succeed(k).await * 2
      }.block
      assertTrue(r == 42)
    },
    test("a lazy val initializer containing await is rejected with a named diagnostic") {
      // Suspending lazy initialization is unsupported; silently forcing the
      // initializer eagerly would be a miscompile, so the macro rejects the
      // construct (parity with the Scala 2 macro's diagnostic).
      typeCheck {
        """
        import zio.blocks.async._
        val a = Async.async {
          lazy val x = Async.succeed(5).await
          Async.succeed(1).await
        }
        a
        """
      }.map(result => assert(result)(Assertion.isLeft))
    },
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
    },
    test("try/catch over a ready pollable-as-value await preserves pollable identity") {
      val inner: Pollable[Int] = new Pollable[Int] {
        def poll(onComplete: Runnable): Async[Int] = Async.succeed(99)
      }
      val fa = Async.async {
        try Async.succeed(inner).await
        catch { case _: Throwable => null }
      }
      assertTrue(fa.block.asInstanceOf[AnyRef] eq inner.asInstanceOf[AnyRef])
    },
    test("try/catch over a pending await drives CpsTryMonad's suspended success path") {
      // A genuinely-pending await inside try/catch routes through
      // AsyncCpsMonad.flatMapTry's Pollable branch (map/catchAll reification),
      // not the ready-Failure fast path the previous test exercises.
      val cRef    = new AtomicReference[Completer[Int]]()
      val pending = Async.promiseInternal[Int](c => cRef.set(c))
      val fa      = Async.async {
        try pending.await + 1
        catch { case _: RuntimeException => -1 }
      }
      val worker = new Thread(() => { Thread.sleep(25); cRef.get().succeed(41) })
      worker.setDaemon(true)
      worker.start()
      assertTrue(fa.block == 42)
    },
    test("try/catch over a pending await that fails recovers via the handler") {
      val boom    = new RuntimeException("late")
      val cRef    = new AtomicReference[Completer[Int]]()
      val pending = Async.promiseInternal[Int](c => cRef.set(c))
      val fa      = Async.async {
        try pending.await
        catch { case _: RuntimeException => 7 }
      }
      val worker = new Thread(() => { Thread.sleep(25); cRef.get().fail(boom) })
      worker.setDaemon(true)
      worker.start()
      assertTrue(fa.block == 7)
    },
    test("a throwing catch handler over a failed await surfaces as a monadic failure") {
      // The continuation `f` applied by AsyncCpsMonad.flatMapTry throws (the
      // handler re-raises), so flatMapTry's `try f(ta) catch { case t =>
      // Async.fail(t) }` converts it into an Async failure rather than letting
      // it escape — proving the DCA try/catch rewrite treats it monadically.
      val boom1 = new RuntimeException("boom1")
      val boom2 = new RuntimeException("boom2")
      val fa    = Async.async {
        try Async.fail(boom1).await
        catch { case _: RuntimeException => throw boom2 }
      }
      assertTrue(scala.util.Try(fa.block).failed.toOption.contains(boom2))
    },
    test("a raw exception thrown by the post-await continuation inside try is recovered by the surrounding catch") {
      // After the await, `require` throws a raw IllegalArgumentException in the
      // post-await continuation. DCA's try-block machinery captures it and
      // routes it to the surrounding `catch`, which recovers with 99.
      val fa = Async.async {
        try {
          val a = Async.succeed(1).await
          require(a == 2, "nope")
          a
        } catch { case _: IllegalArgumentException => 99 }
      }
      assertTrue(fa.block == 99)
    }
  )
}
