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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import zio.blocks.async._
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError, SyncInterpreter}
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * JVM-only because these white-box tests intentionally exercise synchronous
 * blocking entry points.
 */
object AsyncInterpreterRecoveryResidualCoverageSpec extends StreamsBaseSpec {
  private val noop = new Runnable { def run(): Unit = () }

  private final class Gate[A] extends Async.Operation[A] {
    private val result                           = new Completer[A]
    val polled                                   = new CountDownLatch(1)
    val polls                                    = new AtomicInteger
    val cancels                                  = new AtomicInteger
    def poll(wake: Runnable): Async[A]           = { polls.incrementAndGet(); polled.countDown(); result.poll(wake) }
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def succeed(value: A): Unit                  = result.succeed(value)
  }

  private final class Replace[A](next: => Async[A], wakeTwice: Boolean = false) extends Pollable[A] {
    val polls                          = new AtomicInteger
    def poll(wake: Runnable): Async[A] = {
      polls.incrementAndGet()
      if (wakeTwice) { wake.run(); wake.run() }
      next
    }
  }

  private class Source(readEffect: () => Async[Any], closeEffect: () => Async[Unit] = () => Async.succeed(()))
      extends Reader.AsyncReader[Any] {
    val closes                                = new AtomicInteger
    def read[A >: Any](sentinel: A): Async[A] = readEffect().asInstanceOf[Async[A]]
    def readable(): Async[Boolean]            = Async.succeed(true)
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def close(): Async[Unit]                  = { closes.incrementAndGet(); closeEffect() }
  }

  /* Residual checklist (line numbers are /tmp/current2-AsyncInterpreter.scala.txt):
   * 2091,2095,2097-99,2101,2107: "close failure combinations..."
   * 2121-25: "incompatible replacement..." and "rejected replacement close failure..."
   * 2136,2142,2145: "defect classification..."
   * 2152,2155: "cancellation owns close commit..."
   * 2186,2188-90: "cancellation owns callback commit..." (the false claim arm)
   * 2198,2200,2203,2206-11: "None/null callback results..."
   * 2224-25: "cancellation during detached materialization..."
   * 2243,2245-59,2267-70: "incompatible replacement..." / cancellation cleanup
   * 2265: "replacement read defect..."
   * 3950-57: "nested callback cancellation repairs outer frame..."
   * The individual && short-circuit records for phase/state/epoch/active/claim
   * mismatches have no independent externally observable entrance; cancellation
   * deterministically supplies their reachable false-commit path in the named tests.
   */

  private def typed(source: Source)(f: String => Async[Stream[_, Any]]): AsyncInterpreter = {
    val interpreter = new AsyncInterpreter(source)
    interpreter.addAsyncCatchAll[String](f)
    interpreter
  }

  def spec = suite("AsyncInterpreter recovery residual coverage")(
    test("close failure combinations preserve primary, suppress cleanup, and retain null Throwable") {
      val trigger = StreamError.source("typed")
      val cleanup = new RuntimeException("cleanup")
      val a       =
        typed(new Source(() => Async.failTrusted(trigger), () => Async.fail(cleanup)))(_ => Async.succeed(Stream.empty))
      val b =
        typed(new Source(() => Async.failTrusted(trigger), () => Async.fail(null)))(_ => Async.succeed(Stream.empty))
      val c = new AsyncInterpreter(new Source(() => Async.fail(null), () => Async.fail(cleanup)))
      c.addAsyncCatchDefect { case null => Async.succeed(Some(Stream.empty)) }
      for {
        ar <- runAsync(a.read[Any]("eof").either)
        br <- runAsync(b.read[Any]("eof").either)
        cr <- runAsync(c.read[Any]("eof").either)
      } yield assertTrue(
        ar == Left(trigger),
        trigger.getSuppressed.toList == List(cleanup),
        br == Left(trigger),
        cr == Left(null)
      )
    },
    test("defect classification rejects, throws, and accepts only after close commit") {
      val trigger = new RuntimeException("trigger")
      val thrown  = new RuntimeException("classification")
      val closed  = new AtomicBoolean(false)
      val reject  = new AsyncInterpreter(new Source(() => Async.fail(trigger)))
      reject.addAsyncCatchDefect { case `thrown` => Async.succeed(Some(Stream.empty)) }
      val crash = new AsyncInterpreter(new Source(() => Async.fail(trigger)))
      crash.addAsyncCatchDefect(new PartialFunction[Throwable, Async[Option[Stream[_, Any]]]] {
        def isDefinedAt(t: Throwable): Boolean = throw thrown
        def apply(t: Throwable)                = Async.succeed(Some(Stream.empty))
      })
      val accept =
        new AsyncInterpreter(new Source(() => Async.fail(trigger), () => { closed.set(true); Async.succeed(()) }))
      accept.addAsyncCatchDefect { case `trigger` if closed.get() => Async.succeed(Some(Stream.succeed("ok": Any))) }
      for {
        rr <- runAsync(reject.read[Any]("eof").either)
        cr <- runAsync(crash.read[Any]("eof").either)
        ok <- runAsync(accept.read[Any]("eof"))
      } yield assertTrue(rr == Left(trigger), cr == Left(thrown), ok == "ok")
    },
    test("typed recovery rejects a trusted failure without typed stream provenance") {
      val trusted = new RuntimeException("trusted-but-untyped")
      val calls   = new AtomicInteger
      val i       = typed(new Source(() => Async.failTrusted(trusted))) { _ =>
        calls.incrementAndGet()
        Async.succeed(Stream.succeed("wrong": Any))
      }
      runAsync(i.read[Any]("eof").either).map(result => assertTrue(result == Left(trusted), calls.get() == 0))
    },
    test("None and null callback results cover trusted replay and callback null failures") {
      val trusted = StreamError.source("trusted")
      val none    = new AsyncInterpreter(new Source(() => Async.failTrusted(trusted)))
      none.addAsyncCatchDefect { case _ => Async.succeed(None) }
      val nullOption = new AsyncInterpreter(new Source(() => Async.fail(new RuntimeException("x"))))
      nullOption.addAsyncCatchDefect { case _ => Async.succeed(null) }
      val nullEffect = typed(new Source(() => Async.failTrusted(trusted)))(_ => null)
      for {
        a <- runAsync(none.read[Any]("eof").either)
        b <- runAsync(nullOption.read[Any]("eof").either)
        c <- runAsync(nullEffect.read[Any]("eof").either)
      } yield assertTrue(
        a == Left(trusted),
        b.left.exists(cause =>
          cause.isInstanceOf[NullPointerException] &&
            cause.getMessage == "async recovery callback returned null"
        ),
        c.left.exists(cause =>
          cause.isInstanceOf[NullPointerException] && cause.getMessage == "async recovery callback returned null"
        )
      )
    },
    test("pending distinct replacement and duplicate reentrant wake install exactly once") {
      val trigger = StreamError.source("typed")
      val gate    = new Gate[Stream[_, Any]]
      val second  = new Replace[Stream[_, Any]](gate, wakeTwice = true)
      val first   = new Replace[Stream[_, Any]](second)
      val calls   = new AtomicInteger
      val i       = typed(new Source(() => Async.failTrusted(trigger))) { _ => calls.incrementAndGet(); first }
      val pull    = i.read[Any]("eof")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      gate.succeed(Stream.succeed("ok": Any))
      runAsync(pull).map(v => assertTrue(v == "ok", calls.get() == 1, first.polls.get() == 1, second.polls.get() >= 1))
    },
    test("cancellation owns callback commit and stale completion cannot install replacement") {
      val trigger = StreamError.source("typed")
      val gate    = new Gate[Stream[_, Any]]
      val i       = typed(new Source(() => Async.failTrusted(trigger)))(_ => gate)
      val pull    = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val callbackPending = gate.polled.await(10, TimeUnit.SECONDS)
      val cancel          = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      gate.succeed(Stream.succeed("stale": Any))
      for {
        _    <- runAsync(cancel)
        next <- runAsync(i.read[Any]("eof"))
      } yield assertTrue(
        callbackPending,
        next == "eof",
        // Completion and cancellation may race to signal the callback, but
        // cancellation must own the interpreter commit and cleanup is idempotent.
        gate.cancels.get() <= 1
      )
    },
    test("cancellation started reentrantly during recovery materialization owns the detached replacement") {
      val trigger                   = StreamError.source("typed")
      val closeCalls                = new AtomicInteger
      var pull: Async[Any]          = null
      var cancellation: Async[Unit] = null
      val owner                     = new Source(() => Async.succeed("stale")) {
        override def close(): Async[Unit] = { closeCalls.incrementAndGet(); Async.succeed(()) }
      }
      val replacement = new Stream[Nothing, Any] {
        def render: String                                                       = "ReentrantRecoveryMaterialization"
        private[streams] def compile(depth: Int, bufferSize: Int): Reader[Any]   = owner
        private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
          throw new UnsupportedOperationException
        override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
          val requested = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
          Async.startRegistered(requested)(running => cancellation = running)
          pipeline.deferOwnedReaderRoot(owner)
          null
        }
      }
      val i = typed(new Source(() => Async.failTrusted(trigger)))(_ => Async.succeed(replacement))
      pull = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancelled = cancellation.either.block
      val original  = pull.either.block
      val next      = i.read[Any]("eof").either.block
      assertTrue(cancelled.isRight, original == Right("cancelled"), next == Right("eof"), closeCalls.get() == 1)
    },
    test("cancellation owns close commit before callback invocation") {
      val trigger = StreamError.source("typed")
      val close   = new Gate[Unit]
      val calls   = new AtomicInteger
      val i       = typed(new Source(() => Async.failTrusted(trigger), () => close)) { _ =>
        calls.incrementAndGet(); Async.succeed(Stream.succeed("wrong": Any))
      }
      val pull = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancel = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      close.succeed(())
      runAsync(cancel).map(_ => assertTrue(calls.get() == 0, close.cancels.get() == 0))
    },
    test("cancellation between defect classification and handler invocation wins") {
      val trigger                   = new RuntimeException("trigger")
      val handlerCalls              = new AtomicInteger
      var pull: Async[Any]          = null
      var cancellation: Async[Unit] = null
      val i                         = new AsyncInterpreter(new Source(() => Async.fail(trigger)))
      i.addAsyncCatchDefect(new PartialFunction[Throwable, Async[Option[Stream[_, Any]]]] {
        def isDefinedAt(cause: Throwable): Boolean = {
          val requested = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
          Async.startRegistered(requested)(running => cancellation = running)
          true
        }
        def apply(cause: Throwable): Async[Option[Stream[_, Any]]] = {
          handlerCalls.incrementAndGet()
          Async.succeed(Some(Stream.succeed("wrong": Any)))
        }
      })
      pull = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancelled = cancellation.either.block
      val result    = pull.either.block
      assertTrue(cancelled.isRight, result == Right("cancelled"), handlerCalls.get() == 0)
    },
    test("failed recovery materialization reentrantly cancelled hands claimed cleanup to cancellation") {
      val trigger                   = StreamError.source("typed")
      val closeCalls                = new AtomicInteger
      val failure                   = new RuntimeException("materialization")
      var pull: Async[Any]          = null
      var cancellation: Async[Unit] = null
      val owner                     = new Source(() => Async.succeed("stale")) {
        override def close(): Async[Unit] = { closeCalls.incrementAndGet(); Async.succeed(()) }
      }
      val replacement = new Stream[Nothing, Any] {
        def render: String                                                       = "FailingReentrantRecoveryMaterialization"
        private[streams] def compile(depth: Int, bufferSize: Int): Reader[Any]   = owner
        private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
          throw new UnsupportedOperationException
        override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
          pipeline.deferOwnedReaderRoot(owner)
          val requested = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
          Async.startRegistered(requested)(running => cancellation = running)
          throw failure
        }
      }
      val i = typed(new Source(() => Async.failTrusted(trigger)))(_ => Async.succeed(replacement))
      pull = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancelled = cancellation.either.block
      val result    = pull.either.block
      assertTrue(cancelled.isRight, result == Right("cancelled"), closeCalls.get() == 1)
    },
    test("failed push materialization reentrantly cancelled hands claimed cleanup to cancellation") {
      val failure                   = new RuntimeException("materialization")
      var pull: Async[Any]          = null
      var cancellation: Async[Unit] = null
      val cancelling                = new CountDownLatch(1)
      val closeCalls                = new AtomicInteger
      val owner                     = new Source(() => Async.succeed("unused")) {
        override def close(): Async[Unit] = { closeCalls.incrementAndGet(); Async.succeed(()) }
      }
      val nested = new Stream[Nothing, Any] {
        def render: String                                                       = "FailingReentrantPushMaterialization"
        private[streams] def compile(depth: Int, bufferSize: Int): Reader[Any]   = throw failure
        private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
          throw new UnsupportedOperationException
        override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
          pipeline.deferOwnedReaderRoot(owner)
          throw failure
        }
      }
      val interpreter = new AsyncInterpreter(new Source(() => Async.succeed(1)))
      interpreter.addPush[Any](JvmType.AnyRef, JvmType.AnyRef)(_ => nested)
      interpreter.beforePushMaterializationFailureForTest { () =>
        val thread = new Thread(() => {
          val requested = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
          Async.startRegistered(requested)(running => cancellation = running)
          while (!interpreter.activeReadCancellingForTest) Thread.onSpinWait()
          cancelling.countDown()
        })
        thread.start()
        cancelling.await()
      }
      pull = interpreter.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancelled = cancellation.either.block
      val result    = pull.either.block
      assertTrue(cancelled.isRight, result == Right("cancelled"), closeCalls.get() == 1)
    },
    test("failed push materialization joins detached owner cleanup to the primary failure") {
      val failure      = new RuntimeException("push-materialization")
      val closeFailure = new RuntimeException("push-materialization-close")
      val owner        = new Source(() => Async.succeed("unused"), () => Async.fail(closeFailure))
      val nested       = new Stream[Nothing, Any] {
        def render: String                                                       = "FailingPushMaterializationWithOwner"
        private[streams] def compile(depth: Int, bufferSize: Int): Reader[Any]   = throw failure
        private[streams] def compileInterpreter(pipeline: SyncInterpreter): Unit =
          throw new UnsupportedOperationException
        override private[streams] def materializeAsync(pipeline: AsyncInterpreter): Stream[_, _] = {
          pipeline.deferOwnedReaderRoot(owner)
          throw failure
        }
      }
      val interpreter = new AsyncInterpreter(new Source(() => Async.succeed(1)))
      interpreter.addPush[Any](JvmType.AnyRef, JvmType.AnyRef)(_ => nested)
      val result = interpreter.read[Any]("eof").either.block
      assertTrue(
        result.left.exists(_ eq failure),
        failure.getSuppressed.toList == List(closeFailure),
        owner.closes.get() == 1
      )
    },
    test("installed recovery reader synchronous read throw is captured") {
      val trigger = StreamError.source("typed")
      val failure = new RuntimeException("replacement read")
      val owner   = new Source(() => Async.succeed("unused")) {
        override def read[A >: Any](sentinel: A): Async[A] = throw failure
      }
      val replacement = Stream.fromReader[Nothing, Any](owner)
      val i           = typed(new Source(() => Async.failTrusted(trigger)))(_ => Async.succeed(replacement))
      for {
        result <- runAsync(i.read[Any]("eof").either)
      } yield assertTrue(result == Left(failure))
    },
    test("nested callback cancellation repairs outer frame state") {
      val trigger = StreamError.source("typed")
      val gate    = new Gate[Stream[_, Any]]
      val outer   = new Source(() => Async.succeed(42))
      val inner   = new Source(() => Async.failTrusted(trigger))
      val i       = typed(outer)(_ => gate)
      i.installNestedFrameForTest(inner, JvmType.AnyRef)
      val pull = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancel = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      for {
        _     <- runAsync(cancel)
        value <- runAsync(i.read[Any]("eof"))
      } yield assertTrue(value == 42, inner.closes.get() == 1)
    },
    test("incompatible replacement close success and failure retain mismatch as primary") {
      def attempt(closeFails: Boolean) = {
        val trigger = StreamError.source("typed")
        val owner   = new Reader.SyncReader[String] {
          def isClosed                       = false
          def read[A >: String](sentinel: A) = "bad"
          def close(): Unit                  = if (closeFails) throw new RuntimeException("owner-close")
        }
        val stream = Stream.fromReader[Nothing, String](owner).asInstanceOf[Stream[_, Any]]
        val source = new Source(() => Async.failTrusted(trigger)) {
          override def jvmType                                           = JvmType.Int
          override def readInt(sentinel: Long)(implicit ev: Any <:< Int) = Async.failTrusted(trigger)
        }
        val i = typed(source)(_ => Async.succeed(stream))
        runAsync(i.readInt(Long.MinValue).either)
      }
      for {
        success <- attempt(false)
        failure <- attempt(true)
      } yield assertTrue(
        success.left.exists(_.isInstanceOf[IllegalArgumentException]),
        failure.left.exists(t => t.isInstanceOf[IllegalArgumentException] && t.getSuppressed.nonEmpty)
      )
    }
  )
}
