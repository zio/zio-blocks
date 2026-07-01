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

import zio.blocks.chunk.Chunk
import zio.blocks.scope.Resource
import zio.blocks.streams.internal.{Interpreter, SinkError, StreamError}
import zio.blocks.streams.io.Reader
import zio.test._

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * Stream resource lifecycle tests.
 *
 * Verifies that resources acquired during stream execution are always released
 * exactly once, regardless of whether the stream completes normally, fails with
 * a typed error ([[StreamError]]), or fails with a defect, including a suite of
 * adversarial regressions covering double-close, error-cleanup integrity,
 * concat/flatMap resource safety, and error-recovery combinators.
 */
object StreamResourceSpec extends StreamsBaseSpec {

  // ---- Helpers from AdversarialCleanupErrorIntegritySpec --------------------

  /** A uniquely-identifiable cleanup defect (tagged sentinel). */
  private final class CleanupSentinel(msg: String) extends RuntimeException(msg)

  /**
   * True iff `sentinel` is observable in `r` (thrown, cause, or suppressed).
   */
  private def surfaced(r: Try[Any], sentinel: Throwable): Boolean = r match {
    case scala.util.Failure(t) =>
      (t eq sentinel) || (t.getCause eq sentinel) || t.getSuppressed.contains(sentinel)
    case scala.util.Success(_) => false
  }

  /**
   * True iff `r` failed by THROWING exactly `defect` (the untyped cleanup
   * defect wins and propagates as a thrown exception, per the contract that
   * untyped defects propagate as exceptions) with a typed-error control carrier
   * of class `carrier` preserved as suppressed context (lossless errors).
   */
  private def threwDefectWithSuppressedCarrier(
    r: Try[Any],
    defect: Throwable,
    carrier: Class[_]
  ): Boolean = r match {
    case scala.util.Failure(t) => (t eq defect) && t.getSuppressed.exists(carrier.isInstance)
    case scala.util.Success(_) => false
  }

  // ---- Helpers from AdversarialFlatMapResourceSpec --------------------------

  // Wrap with enough identity maps to exceed Stream.DepthCutoff (100), forcing
  // the whole pipeline to compile into the fused Interpreter.
  private def forceInterpreter(s: Stream[Nothing, Int]): Stream[Nothing, Int] = {
    var t = s
    var i = 0
    while (i < 130) { t = t.map(x => x); i += 1 }
    t
  }

  // ---- Helpers from AdversarialRecoveryDoubleCloseSpec ----------------------

  private val EOI = new AnyRef

  private def drainAll(r: io.Reader[_]): List[Any] = {
    val b = List.newBuilder[Any]
    var v = r.read[Any](EOI)
    while (v.asInstanceOf[AnyRef] ne EOI) { b += v; v = r.read[Any](EOI) }
    b.result()
  }

  // ---- Helpers from AdversarialCatchAllDoubleCloseSpec ----------------------

  private final class CloseSentinel extends RuntimeException("close-defect")

  /**
   * A reader that fails its first read with a typed StreamError and whose
   * `close()` counts invocations and always throws a tagged defect. AnyRef lane
   * forces the boxed `read` path that `catchAll` (always AnyRef) uses.
   */
  private final class FailingThenThrowingClose(closeCount: AtomicInteger) extends Reader[Int] {
    override def jvmType: JvmType         = JvmType.AnyRef
    def isClosed: Boolean                 = false
    def read[A1 >: Int](sentinel: A1): A1 = throw new StreamError("boom")
    def close(): Unit                     = { closeCount.incrementAndGet(); throw new CloseSentinel }
  }

  /**
   * Emits `values` then EOF; close() counts invocations and always throws. Used
   * to exercise ConcatReader.advance, which closes the exhausted FIRST segment
   * before setting its `currentClosed` guard.
   */
  private final class EmitThenThrowingClose(values: List[Int], closeCount: AtomicInteger) extends Reader[Int] {
    private var rest                      = values
    override def jvmType: JvmType         = JvmType.AnyRef
    def isClosed: Boolean                 = rest.isEmpty
    def read[A1 >: Int](sentinel: A1): A1 = rest match {
      case h :: t => rest = t; Int.box(h).asInstanceOf[A1]
      case Nil    => sentinel
    }
    def close(): Unit = { closeCount.incrementAndGet(); throw new CloseSentinel }
  }

  private final class DefectSentinel extends RuntimeException("defect-boom")

  /**
   * Like above, but fails its first read with a non-fatal DEFECT (for the
   * `catchDefect` sibling path) instead of a typed StreamError.
   */
  private final class DefectThenThrowingClose(closeCount: AtomicInteger) extends Reader[Int] {
    override def jvmType: JvmType         = JvmType.AnyRef
    def isClosed: Boolean                 = false
    def read[A1 >: Int](sentinel: A1): A1 = throw new DefectSentinel
    def close(): Unit                     = { closeCount.incrementAndGet(); throw new CloseSentinel }
  }

  // ---- Helpers from Group B adversarial specs -------------------------------

  // From AdversarialResourceOnErrorConvergenceSpec.
  private def managed(opens: AtomicInteger, closes: AtomicInteger)(body: => Stream[Any, Int]): Stream[Any, Int] =
    Stream.fromAcquireRelease({ opens.incrementAndGet(); () }, (_: Unit) => { closes.incrementAndGet(); () })(_ => body)

  // From AdversarialResourceOrderingSpec.
  private def logged[A](
    log: ArrayBuffer[String]
  )(name: String)(inner: => Stream[Nothing, A]): Stream[Nothing, A] =
    Stream.fromAcquireRelease(
      { log += s"acq:$name"; () },
      (_: Unit) => { log += s"rel:$name"; () }
    )(_ => inner)

  // From AdversarialUserFnResourceSpec (renamed from `managed` to avoid collision).
  private def managedUserFn(opens: AtomicInteger, closes: AtomicInteger): Stream[Nothing, Int] =
    Stream.fromAcquireRelease({ opens.incrementAndGet(); () }, (_: Unit) => { closes.incrementAndGet(); () })(_ =>
      Stream(1, 2, 3)
    )

  // From AdversarialZipResourceSpec.
  private def resourceStream(count: AtomicInteger, elems: Chunk[Int]): Stream[Nothing, Int] =
    Stream.fromAcquireRelease("res", (_: String) => { count.incrementAndGet(); () }) { _ =>
      Stream.fromChunk(elems)
    }

  def spec: Spec[TestEnvironment, Any] = suite("Stream resource lifecycle")(
    suite("ensuring")(
      test("finalizer runs on normal completion") {
        val released = new AtomicBoolean(false)
        val result   = Stream.range(0, 5).ensuring(released.set(true)).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 5))) &&
        assertTrue(released.get)
      },
      test("finalizer runs on typed error (StreamError)") {
        val released = new AtomicBoolean(false)
        val result   = Stream.fail("boom").ensuring(released.set(true)).runCollect
        assertTrue(result == Left("boom")) &&
        assertTrue(released.get)
      },
      test("finalizer runs on defect (RuntimeException)") {
        val released = new AtomicBoolean(false)
        val caught   = try {
          Stream.die(new RuntimeException("defect")).ensuring(released.set(true)).runCollect
          false
        } catch {
          case _: RuntimeException => true
        }
        assertTrue(caught) &&
        assertTrue(released.get)
      },
      test("error in middle of stream: ensuring finalizer still runs") {
        val released = new AtomicBoolean(false)
        val stream   = (Stream.range(0, 3) ++ Stream.fail("mid-error")).ensuring(released.set(true))
        val result   = stream.runCollect
        assertTrue(result == Left("mid-error")) &&
        assertTrue(released.get)
      },
      test("nested ensuring: both finalizers run (inner-first due to wrapping)") {
        // Each ensuring wraps with `try src.close() finally finalizer`, so the
        // inner (first-registered) finalizer runs before the outer one.
        val order  = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
        val stream = Stream
          .range(0, 3)
          .ensuring { order.add(1); () }
          .ensuring { order.add(2); () }
        val result = stream.runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 3))) &&
        assertTrue(order.toArray.toList == List(1, 2))
      },
      test("ensuring + map: finalizer runs") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .range(0, 3)
          .ensuring(released.set(true))
          .map(_ * 2)
          .runCollect
        assertTrue(result == Right(Chunk(0, 2, 4))) &&
        assertTrue(released.get)
      },
      test("ensuring + filter: finalizer runs") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .range(0, 6)
          .ensuring(released.set(true))
          .filter(_ % 2 == 0)
          .runCollect
        assertTrue(result == Right(Chunk(0, 2, 4))) &&
        assertTrue(released.get)
      },
      test("ensuring + take: finalizer runs even with early termination") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .range(0, 100)
          .ensuring(released.set(true))
          .take(3)
          .runCollect
        assertTrue(result == Right(Chunk(0, 1, 2))) &&
        assertTrue(released.get)
      },
      test("ensuring + drop: finalizer runs") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .range(0, 5)
          .ensuring(released.set(true))
          .drop(2)
          .runCollect
        assertTrue(result == Right(Chunk(2, 3, 4))) &&
        assertTrue(released.get)
      },
      test("ensuring + flatMap: both outer and inner finalizers run") {
        val outerReleased = new AtomicBoolean(false)
        val innerReleased = new AtomicBoolean(false)
        val result        = Stream(1, 2)
          .ensuring(outerReleased.set(true))
          .flatMap { i =>
            Stream.succeed(i * 10).ensuring(innerReleased.set(true))
          }
          .runCollect
        assertTrue(result == Right(Chunk(10, 20))) &&
        assertTrue(outerReleased.get) &&
        assertTrue(innerReleased.get)
      }
    ),
    suite("fromAcquireRelease")(
      test("release runs on normal completion") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .fromAcquireRelease("resource", (_: String) => released.set(true)) { r =>
            Stream.succeed(r)
          }
          .runCollect
        assertTrue(result == Right(Chunk("resource"))) &&
        assertTrue(released.get)
      },
      test("release runs on typed error in use") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .fromAcquireRelease("resource", (_: String) => released.set(true)) { _ =>
            Stream.fail("error"): Stream[String, Nothing]
          }
          .runCollect
        assertTrue(result == Left("error")) &&
        assertTrue(released.get)
      },
      test("release runs on defect in use") {
        val released = new AtomicBoolean(false)
        val caught   = try {
          Stream
            .fromAcquireRelease("resource", (_: String) => released.set(true)) { _ =>
              Stream.die(new RuntimeException("defect")): Stream[Nothing, Nothing]
            }
            .runCollect
          false
        } catch {
          case _: RuntimeException => true
        }
        assertTrue(caught) &&
        assertTrue(released.get)
      },
      test("release runs on defect during compile (inside use function)") {
        val released = new AtomicBoolean(false)
        val caught   = try {
          Stream
            .fromAcquireRelease("resource", (_: String) => released.set(true)) { _ =>
              throw new RuntimeException("compile defect")
              Stream.empty
            }
            .runCollect
          false
        } catch {
          case _: RuntimeException => true
        }
        assertTrue(caught) &&
        assertTrue(released.get)
      }
    ),
    suite("fromResource")(
      test("resource is closed when stream completes normally") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        val result   = Stream
          .fromResource(resource)(r => Stream.succeed(r))
          .runCollect
        assertTrue(result == Right(Chunk("res"))) &&
        assertTrue(closed.get)
      },
      test("resource is closed when stream fails with typed error") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        val result   = Stream
          .fromResource(resource)(_ => Stream.fail("err"): Stream[String, Nothing])
          .runCollect
        assertTrue(result == Left("err")) &&
        assertTrue(closed.get)
      },
      test("resource is closed when use function throws during compilation") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        val caught   = try {
          Stream
            .fromResource(resource) { _ =>
              throw new RuntimeException("compile-time boom")
            }
            .runCollect
          false
        } catch {
          case _: RuntimeException => true
        }
        assertTrue(caught) &&
        assertTrue(closed.get)
      },
      test("resource is closed when stream fails with defect") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        val caught   = try {
          Stream
            .fromResource(resource) { _ =>
              Stream.die(new RuntimeException("defect")): Stream[Nothing, Nothing]
            }
            .runCollect
          false
        } catch {
          case _: RuntimeException => true
        }
        assertTrue(caught) &&
        assertTrue(closed.get)
      }
    ),
    suite("take with ensuring")(
      test("take(n) with ensuring: finalizer runs even though stream ends early") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .repeat(1)
          .ensuring(released.set(true))
          .take(5)
          .runCollect
        assertTrue(result == Right(Chunk(1, 1, 1, 1, 1))) &&
        assertTrue(released.get)
      },
      test("take(0) with ensuring: finalizer runs immediately") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .range(0, 100)
          .ensuring(released.set(true))
          .take(0)
          .runCollect
        assertTrue(result == Right(Chunk.empty[Int])) &&
        assertTrue(released.get)
      }
    ),
    suite("multiple resources in sequence")(
      test("all released in correct order") {
        val releaseOrder = new java.util.concurrent.ConcurrentLinkedQueue[String]()
        val result       = Stream
          .fromAcquireRelease("A", (r: String) => { releaseOrder.add(r); () }) { r =>
            Stream.succeed(r)
          }
          .concat(
            Stream.fromAcquireRelease("B", (r: String) => { releaseOrder.add(r); () }) { r =>
              Stream.succeed(r)
            }
          )
          .concat(
            Stream.fromAcquireRelease("C", (r: String) => { releaseOrder.add(r); () }) { r =>
              Stream.succeed(r)
            }
          )
          .runCollect
        assertTrue(result == Right(Chunk("A", "B", "C"))) &&
        assertTrue(releaseOrder.toArray.toList == List("A", "B", "C"))
      },
      test("multiple ensuring finalizers all run") {
        val count  = new AtomicInteger(0)
        val result = Stream
          .range(0, 2)
          .ensuring { count.incrementAndGet(); () }
          .map(_ + 10)
          .ensuring { count.incrementAndGet(); () }
          .filter(_ => true)
          .ensuring { count.incrementAndGet(); () }
          .runCollect
        assertTrue(result == Right(Chunk(10, 11))) &&
        assertTrue(count.get == 3)
      }
    ),
    suite("edge cases")(
      test("ensuring on empty stream: finalizer still runs") {
        val released = new AtomicBoolean(false)
        val result   = Stream.empty.ensuring(released.set(true)).runCollect
        assertTrue(result == Right(Chunk.empty)) &&
        assertTrue(released.get)
      },
      test("fromAcquireRelease with empty stream: release still runs") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .fromAcquireRelease("res", (_: String) => released.set(true)) { _ =>
            Stream.empty: Stream[Nothing, Nothing]
          }
          .runCollect
        assertTrue(result == Right(Chunk.empty)) &&
        assertTrue(released.get)
      },
      test("ensuring + takeWhile: finalizer runs on early termination") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .range(0, 100)
          .ensuring(released.set(true))
          .takeWhile(_ < 3)
          .runCollect
        assertTrue(result == Right(Chunk(0, 1, 2))) &&
        assertTrue(released.get)
      },
      test("fromAcquireRelease + takeWhile: release runs on early termination") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .fromAcquireRelease("res", (_: String) => released.set(true)) { _ =>
            Stream.range(0, 100)
          }
          .takeWhile(_ < 3)
          .runCollect
        assertTrue(result == Right(Chunk(0, 1, 2))) &&
        assertTrue(released.get)
      },
      test("ensuring + repeated + take: finalizer runs when repeated stream is closed") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .range(0, 3)
          .ensuring(released.set(true))
          .repeated
          .take(5)
          .runCollect
        assertTrue(result == Right(Chunk(0, 1, 2, 0, 1))) &&
        assertTrue(released.get)
      },
      test("fromAcquireRelease + repeated + take: release runs") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .fromAcquireRelease("res", (_: String) => released.set(true)) { _ =>
            Stream.range(0, 3)
          }
          .repeated
          .take(5)
          .runCollect
        assertTrue(result == Right(Chunk(0, 1, 2, 0, 1))) &&
        assertTrue(released.get)
      },
      test("ensuring + mapError: finalizer still runs") {
        val released = new AtomicBoolean(false)
        val result   = Stream
          .fail(42)
          .ensuring(released.set(true))
          .mapError(_.toString)
          .runCollect
        assertTrue(result == Left("42")) &&
        assertTrue(released.get)
      }
    ),
    suite("concat resource safety")(
      test("first stream errors: second stream never starts, first resources released") {
        val firstReleased  = new AtomicBoolean(false)
        val secondStarted  = new AtomicBoolean(false)
        val secondReleased = new AtomicBoolean(false)
        val stream         = Stream
          .fail("err")
          .ensuring(firstReleased.set(true))
          .concat(
            Stream.suspend {
              secondStarted.set(true)
              Stream.succeed(42).ensuring(secondReleased.set(true))
            }
          )
        val result = stream.runCollect
        assertTrue(result == Left("err")) &&
        assertTrue(firstReleased.get) &&
        assertTrue(!secondStarted.get) &&
        assertTrue(!secondReleased.get)
      },
      test("concat: both streams' resources released on normal completion") {
        val firstReleased  = new AtomicBoolean(false)
        val secondReleased = new AtomicBoolean(false)
        val result         = (
          Stream.range(0, 3).ensuring(firstReleased.set(true)) ++
            Stream.range(3, 6).ensuring(secondReleased.set(true))
        ).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 6))) &&
        assertTrue(firstReleased.get) &&
        assertTrue(secondReleased.get)
      },
      suite("regressions")(
        test(
          "first stream finalizer runs exactly once when second segment construction throws (suspend) [AdversarialConcatResourceSpec]"
        ) {
          val count  = new AtomicInteger(0)
          val boom   = new RuntimeException("second-segment-boom")
          val thrown =
            try {
              (Stream.range(0, 2).ensuring { count.incrementAndGet(); () } ++
                Stream.suspend[Nothing, Int](throw boom)).runCollect
              None
            } catch { case t: Throwable => Some(t) }
          assertTrue(thrown == Some(boom)) &&
          assertTrue(count.get == 1)
        },
        test(
          "first stream finalizer runs exactly once when second stream acquire throws [AdversarialConcatResourceSpec]"
        ) {
          val count  = new AtomicInteger(0)
          val boom   = new RuntimeException("acquire-boom")
          val thrown =
            try {
              (Stream.range(0, 2).ensuring { count.incrementAndGet(); () } ++
                Stream.fromAcquireRelease((throw boom): String, (_: String) => ()) { r =>
                  Stream.succeed(r.length)
                }).runCollect
              None
            } catch { case t: Throwable => Some(t) }
          assertTrue(thrown == Some(boom)) &&
          assertTrue(count.get == 1)
        }
      )
    ),
    suite("flatMap resource safety")(
      suite("regressions")(
        test(
          "fused flatMap: every inner resource released exactly once (multiple elements) [AdversarialFlatMapResourceSpec]"
        ) {
          val opened = new AtomicInteger(0)
          val closed = new AtomicInteger(0)
          val base   = Stream(1, 2, 3).flatMap { i =>
            Stream.fromAcquireRelease({ opened.incrementAndGet(); i }, (_: Int) => { closed.incrementAndGet(); () }) {
              r =>
                Stream.succeed(r * 10)
            }
          }
          val result = forceInterpreter(base).runCollect
          assertTrue(result == Right(Chunk(10, 20, 30))) &&
          assertTrue(opened.get == 3) &&
          assertTrue(closed.get == 3)
        },
        test(
          "fused flatMap: prior inner finalizer runs exactly once when a later inner construction throws [AdversarialFlatMapResourceSpec]"
        ) {
          val closedInner = new AtomicInteger(0)
          val boom        = new RuntimeException("inner-construction-boom")
          val base        = Stream(1, 2).flatMap { i =>
            if (i == 1)
              Stream.fromAcquireRelease("res", (_: String) => { closedInner.incrementAndGet(); () }) { r =>
                Stream.succeed(r.length)
              }
            else
              Stream.suspend[Nothing, Int](throw boom)
          }
          val thrown =
            try { forceInterpreter(base).runCollect; None }
            catch { case t: Throwable => Some(t) }
          assertTrue(thrown == Some(boom)) &&
          assertTrue(closedInner.get == 1)
        },
        // ---- shallow-path contrast (sanity): the reader path releases correctly ---
        test("shallow flatMap: every inner resource released exactly once (sanity) [AdversarialFlatMapResourceSpec]") {
          val opened = new AtomicInteger(0)
          val closed = new AtomicInteger(0)
          val result = Stream(1, 2, 3).flatMap { i =>
            Stream.fromAcquireRelease({ opened.incrementAndGet(); i }, (_: Int) => { closed.incrementAndGet(); () }) {
              r =>
                Stream.succeed(r * 10)
            }
          }.runCollect
          assertTrue(result == Right(Chunk(10, 20, 30))) &&
          assertTrue(opened.get == 3) &&
          assertTrue(closed.get == 3)
        }
      )
    ),
    suite("recovery")(
      suite("catchAll resource safety")(
        test("finalizer of failed stream runs, then recovery stream's finalizer runs") {
          val failedReleased   = new AtomicBoolean(false)
          val recoveryReleased = new AtomicBoolean(false)
          val result           = Stream
            .fail("err")
            .ensuring(failedReleased.set(true))
            .catchAll((_: String) => Stream.succeed(42).ensuring(recoveryReleased.set(true)))
            .runCollect
          assertTrue(result == Right(Chunk(42))) &&
          assertTrue(failedReleased.get) &&
          assertTrue(recoveryReleased.get)
        },
        test("catchAll: ensuring on partial stream before error") {
          val released = new AtomicBoolean(false)
          val result   = (Stream.range(0, 3) ++ Stream.fail("err"))
            .ensuring(released.set(true))
            .catchAll((_: String) => Stream.succeed(99))
            .runCollect
          assertTrue(result == Right(Chunk(0, 1, 2, 99))) &&
          assertTrue(released.get)
        }
      ),
      suite("catchDefect resource safety")(
        test("finalizer of defective stream runs, recovery stream's finalizer runs") {
          val defectReleased   = new AtomicBoolean(false)
          val recoveryReleased = new AtomicBoolean(false)
          val result           = Stream
            .die(new RuntimeException("boom"))
            .ensuring(defectReleased.set(true))
            .catchDefect { case _: RuntimeException =>
              Stream.succeed(42).ensuring(recoveryReleased.set(true))
            }
            .runCollect
          assertTrue(result == Right(Chunk(42))) &&
          assertTrue(defectReleased.get) &&
          assertTrue(recoveryReleased.get)
        },
        test("catchDefect: ensuring on partial stream before defect") {
          val released = new AtomicBoolean(false)
          val stream   = Stream
            .fromReader[Nothing, Int](new Reader[Int] {
              private var count                     = 0
              def isClosed: Boolean                 = count > 3
              def read[A1 >: Int](sentinel: A1): A1 = {
                count += 1
                if (count <= 3) Int.box(count).asInstanceOf[A1]
                else throw new RuntimeException("defect mid-stream")
              }
              def close(): Unit = ()
            })
            .ensuring(released.set(true))
            .catchDefect { case _: RuntimeException => Stream.empty }
          val result = stream.runCollect
          assertTrue(result == Right(Chunk(1, 2, 3))) &&
          assertTrue(released.get)
        }
      ),
      suite("regressions")(
        test(
          "catchAll: failed stream finalizer runs exactly once when recovery builder throws [AdversarialRecoveryResourceSpec]"
        ) {
          val count  = new AtomicInteger(0)
          val boom   = new RuntimeException("recovery-builder-boom")
          val thrown =
            try {
              Stream
                .fromAcquireRelease("res", (_: String) => { count.incrementAndGet(); () }) { _ =>
                  (Stream.fail("e"): Stream[String, Int])
                }
                .catchAll((_: String) => (throw boom): Stream[Nothing, Int])
                .runCollect
              None
            } catch { case t: Throwable => Some(t) }
          assertTrue(thrown == Some(boom)) &&
          assertTrue(count.get == 1)
        },
        test(
          "catchDefect: defective stream finalizer runs exactly once when recovery builder throws [AdversarialRecoveryResourceSpec]"
        ) {
          val count  = new AtomicInteger(0)
          val boom   = new RuntimeException("recovery-builder-boom")
          val thrown =
            try {
              Stream
                .fromAcquireRelease("res", (_: String) => { count.incrementAndGet(); () }) { _ =>
                  (Stream.die(new RuntimeException("orig")): Stream[Nothing, Int])
                }
                .catchDefect { case _: RuntimeException => (throw boom): Stream[Nothing, Int] }
                .runCollect
              None
            } catch { case t: Throwable => Some(t) }
          assertTrue(thrown == Some(boom)) &&
          assertTrue(count.get == 1)
        },
        test(
          "orElse: failed stream finalizer runs exactly once when fallback evaluation throws [AdversarialRecoveryResourceSpec]"
        ) {
          val count  = new AtomicInteger(0)
          val boom   = new RuntimeException("fallback-eval-boom")
          val thrown =
            try {
              Stream
                .fromAcquireRelease("res", (_: String) => { count.incrementAndGet(); () }) { _ =>
                  (Stream.fail("e"): Stream[String, Int])
                }
                .orElse((throw boom): Stream[Nothing, Int])
                .runCollect
              None
            } catch { case t: Throwable => Some(t) }
          assertTrue(thrown == Some(boom)) &&
          assertTrue(count.get == 1)
        }
      )
    ),
    suite("double-close")(
      suite("regressions")(
        test(
          "catchAll_whenFailingUpstreamCloseThrowsDuringSwitch_closesUpstreamOnce [AdversarialCatchAllDoubleCloseSpec]"
        ) {
          val closeCount = new AtomicInteger(0)
          val source     = new FailingThenThrowingClose(closeCount)
          val stream     = Stream.fromReader[String, Int](source).catchAll(_ => Stream(1, 2, 3))
          // Untyped close defect propagates; we only care about close-count integrity.
          val _ = Try(stream.runCollect)
          assertTrue(closeCount.get() == 1)
        },
        // Same root cause (blast radius), reached through catchDefect's trySwitch.
        test(
          "catchDefect_whenFailingUpstreamCloseThrowsDuringSwitch_closesUpstreamOnce [AdversarialCatchAllDoubleCloseSpec]"
        ) {
          val closeCount = new AtomicInteger(0)
          val source     = new DefectThenThrowingClose(closeCount)
          val stream     = Stream
            .fromReader[Nothing, Int](source)
            .catchDefect { case _: DefectSentinel => Stream(1, 2, 3) }
          val _ = Try(stream.runCollect)
          assertTrue(closeCount.get() == 1)
        },
        // ConcatReader.close() READS `currentClosed` but never SETS it (missed
        // sibling of ITER-5a, which made CatchAll/CatchDefect close set the
        // flag). On the pinned zip-eager-close + `repeated` restart sequence,
        // reset() re-closes the already-finalized tail segment whenever the
        // tail's isClosed stays false after close (Reader.repeat — BUG-R9-02),
        // so the cycle-1 `ensuring` hook fires TWICE for one close cycle.
        // Intended per-close-cycle count here is 2 (cycle-1 eager close +
        // cycle-2 terminal close), not 3.
        test(
          "zipEagerClose_thenRepeatedReset_firesEnsuringOncePerCloseCycle [AdversarialConcatResetDoubleCloseSpec]"
        ) {
          val fires = new AtomicInteger(0)
          val left  = Stream(1) ++ Stream.repeat(7).ensuring { fires.incrementAndGet(); () }
          val right = Stream(10, 20)
          val r     = (left && right).repeated.take(4).runCollect
          assertTrue(
            r == Right(Chunk((1, 10), (7, 20), (1, 10), (7, 20))),
            fires.get == 2
          )
        },
        // Same root cause (blast radius), reached through ConcatReader.advance: the
        // exhausted FIRST segment is closed before `currentClosed` is set, so a
        // throwing close leaves the guard open and `run`'s final close re-closes it.
        test(
          "concat_whenFirstSegmentCloseThrowsOnAdvance_closesFirstSegmentOnce [AdversarialCatchAllDoubleCloseSpec]"
        ) {
          val closeCount = new AtomicInteger(0)
          val first      = new EmitThenThrowingClose(List(1, 2), closeCount)
          val stream     = Stream.fromReader[Nothing, Int](first) ++ Stream(3, 4)
          val _          = Try(stream.runCollect)
          assertTrue(closeCount.get() == 1)
        },
        // ---- catchAll recovery finalized exactly once across close-then-reset (concat).
        test("catchAll_recoveryFinalizer_runsOnceAcrossResetAfterClose_concat [AdversarialRecoveryDoubleCloseSpec]") {
          var closes     = 0
          val recovery   = Stream(10, 20).ensuring(closes += 1)
          val s          = Stream.fail[String]("boom").catchAll(_ => recovery) ++ Stream(99)
          val r          = Stream.compileToReader(s)
          val cycle1     = drainAll(r)
          val afterDrain = closes
          r.reset()
          val afterReset = closes
          r.close()
          assertTrue(cycle1 == List(10, 20, 99), afterDrain == 1, afterReset == 1)
        },
        // ---- HIGHEST SEVERITY: acquireRelease inside catchAll recovery released twice.
        test("catchAll_acquireReleaseRecovery_releasedOnce_concat [AdversarialRecoveryDoubleCloseSpec]") {
          var releases = 0
          val recovery = Stream.fromAcquireRelease("res", (_: String) => { releases += 1 })(_ => Stream(10, 20))
          val s        = Stream.fail[String]("boom").catchAll(_ => recovery) ++ Stream(99)
          val r        = Stream.compileToReader(s)
          drainAll(r)
          val afterDrain = releases
          r.reset()
          val afterReset = releases
          r.close()
          assertTrue(afterDrain == 1, afterReset == 1)
        },
        // ---- catchDefect recovery finalized exactly once across close-then-reset (concat).
        test(
          "catchDefect_recoveryFinalizer_runsOnceAcrossResetAfterClose_concat [AdversarialRecoveryDoubleCloseSpec]"
        ) {
          var closes   = 0
          val recovery = Stream(10, 20).ensuring(closes += 1)
          val s        = Stream(1)
            .map[Int](_ => throw new RuntimeException("d"))
            .catchDefect { case _: RuntimeException => recovery } ++ Stream(99)
          val r          = Stream.compileToReader(s)
          val cycle1     = drainAll(r)
          val afterDrain = closes
          r.reset()
          val afterReset = closes
          r.close()
          assertTrue(cycle1 == List(10, 20, 99), afterDrain == 1, afterReset == 1)
        },
        // ---- Zip manifestation: `&&` eagerly closes the recovered (longer) side when
        // the other ends; the subsequent reset re-closes that recovery reader.
        test("catchAll_recoveryFinalizer_runsOnceAcrossResetAfterClose_zip [AdversarialRecoveryDoubleCloseSpec]") {
          var closes   = 0
          val recovery = Stream(10, 20, 30).ensuring(closes += 1)
          val left     = Stream.fail[String]("boom").catchAll(_ => recovery)
          val s        = left && Stream(1, 2)
          val r        = Stream.compileToReader(s)
          drainAll(r)
          val afterDrain = closes
          r.reset()
          val afterReset = closes
          r.close()
          assertTrue(afterDrain == 1, afterReset == 1)
        }
      )
    ),
    suite("error-cleanup integrity")(
      suite("regressions")(
        // ---- Controls: prove the intended behavior in the analogous non-typed cases.
        test("control_cleanSuccessPlusCleanupDefect_isSurfaced [AdversarialCleanupErrorIntegritySpec]") {
          val sentinel = new CleanupSentinel("cleanup")
          val r        = Try(Stream(1, 2, 3).ensuring(throw sentinel).runDrain)
          assertTrue(surfaced(r, sentinel))
        },
        test("control_rawDefectPrimaryPlusCleanupDefect_bothSurfaced [AdversarialCleanupErrorIntegritySpec]") {
          val sentinel = new CleanupSentinel("cleanup")
          val primary  = new CleanupSentinel("primary")
          val r        = Try(Stream(1, 2, 3).map[Int](_ => throw primary).ensuring(throw sentinel).runDrain)
          val ok       = r match {
            case scala.util.Failure(t) => (t eq primary) && t.getSuppressed.contains(sentinel)
            case _                     => false
          }
          assertTrue(ok)
        },
        // ---- BUG-001: typed STREAM error in flight + cleanup defect. The defect
        // must propagate as a thrown exception (not be swallowed by `Left`), with the
        // typed `StreamError` carrier preserved as suppressed context.
        test(
          "streamTypedError_plusEnsuringDefect_throwsDefectWithCarrierSuppressed [AdversarialCleanupErrorIntegritySpec]"
        ) {
          val sentinel = new CleanupSentinel("cleanup")
          val r        = Try(Stream.fail("boom").ensuring(throw sentinel).runDrain)
          assertTrue(threwDefectWithSuppressedCarrier(r, sentinel, classOf[StreamError]))
        },
        // ---- BUG-001 (SinkError arm): typed SINK error in flight + cleanup defect.
        test(
          "sinkTypedError_plusEnsuringDefect_throwsDefectWithCarrierSuppressed [AdversarialCleanupErrorIntegritySpec]"
        ) {
          val sentinel = new CleanupSentinel("cleanup")
          val r        = Try(Stream(1, 2, 3).ensuring(throw sentinel).run(Sink.fail("sinkboom")))
          assertTrue(threwDefectWithSuppressedCarrier(r, sentinel, classOf[SinkError]))
        },
        // ---- BUG-001 (resource-release variant): typed error + release defect.
        test(
          "streamTypedError_plusAcquireReleaseDefect_throwsDefectWithCarrierSuppressed [AdversarialCleanupErrorIntegritySpec]"
        ) {
          val sentinel = new CleanupSentinel("release")
          val s        = Stream.fromAcquireRelease((), (_: Unit) => throw sentinel)(_ => Stream.fail("boom"))
          val r        = Try(s.runDrain)
          assertTrue(threwDefectWithSuppressedCarrier(r, sentinel, classOf[StreamError]))
        }
      )
    ),
    suite("regressions")(
      suite("reset")(
        // BUG: fromIterator(<fresh-iterator expr>).repeated throws
        // UnsupportedOperationException instead of restarting from a freshly
        // re-evaluated iterator (sibling fromIterable restarts fine).
        test("repeated_afterFromIteratorInMemory_restartsCleanly [AdversarialRepeatedSourceResetSpec]") {
          val s      = Stream.fromIterator(List(1, 2, 3).iterator)
          val result = Try(s.repeated.take(7).runCollect)
          assertTrue(result == scala.util.Success(Right(Chunk(1, 2, 3, 1, 2, 3, 1))))
        },
        // BUG: buffer(n).repeated over a resettable in-memory source throws
        // UnsupportedOperationException instead of restarting.
        test("repeated_afterBufferInMemory_restartsCleanly [AdversarialRepeatedSourceResetSpec]") {
          val s      = Stream.range(0, 3).buffer(2)
          val result = Try(s.repeated.take(7).runCollect)
          assertTrue(result == scala.util.Success(Right(Chunk(0, 1, 2, 0, 1, 2, 0))))
        },
        // BUG: flatMap(...).repeated over a fully in-memory outer+inner throws
        // UnsupportedOperationException instead of restarting. flatMap is a core
        // combinator; its sibling stateless transforms (map/filter/scan/concat/zip)
        // all restart correctly under `repeated`. The current throwing behavior is
        // enshrined by StreamRepeatSpec's "flatMap then take works for finite case"
        // test (which asserts UnsupportedOperationException) — that test encodes the
        // bug and must be updated when FlatMappedBase gains a reset() override.
        test("repeated_afterFlatMapInMemory_restartsCleanly [AdversarialRepeatedSourceResetSpec]") {
          val s      = Stream.fromIterable(List(1, 2)).flatMap(i => Stream.succeed(i * 10))
          val result = Try(s.repeated.take(6).runCollect)
          assertTrue(result == scala.util.Success(Right(Chunk(10, 20, 10, 20, 10, 20))))
        },
        // BUG: mapPar/mergeAll are the remaining combinators whose readers do
        // not implement reset(): `xs.mapPar(n)(f).repeated` and
        // `Stream.mergeAll(n)(...).repeated` throw UnsupportedOperationException
        // over a resettable in-memory source, while every sibling transform
        // (map/filter/scan/concat/zip/flatMap, and crucially buffer — whose
        // ConcurrentBufferedReader re-spawns its producer on reset()) restarts
        // cleanly under `repeated`. On JS the mapPar/merge readers degrade to
        // plain sequential map/flatMap and still refuse to restart.
        test("repeated_afterMapParInMemory_restartsCleanly [AdversarialRepeatedSourceResetSpec]") {
          val s      = Stream.range(0, 3).mapPar(1)(_ * 10)
          val result = Try(s.repeated.take(6).runCollect)
          assertTrue(result.map(_.map(_.toList.sorted)) == scala.util.Success(Right(List(0, 0, 10, 10, 20, 20))))
        },
        test("repeated_afterMergeAllInMemory_restartsCleanly [AdversarialRepeatedSourceResetSpec]") {
          val s      = Stream.mergeAll(1)(Stream.range(0, 3).map(i => Stream(i * 10)))
          val result = Try(s.repeated.take(6).runCollect)
          assertTrue(result.map(_.map(_.toList.sorted)) == scala.util.Success(Right(List(0, 0, 10, 10, 20, 20))))
        },
        test("interpreter reset() works after close() and re-reads from the start [AdversarialResetAfterCloseSpec]") {
          val r = Interpreter.fromStream(Stream.succeed(1).map((_: Int) + 1))
          r.read[Any](EOI)
          r.close()
          r.reset()
          val first = r.read[Any](EOI)
          assertTrue(first == 2)
        },
        test(
          "interpreter reset() after a PARTIAL flatMap drain cleans up and replays [AdversarialResetAfterCloseSpec]"
        ) {
          val r = Interpreter.fromStream(
            Stream.fromChunk(Chunk(1, 2, 3)).flatMap((i: Int) => Stream.fromChunk(Chunk(i, i)))
          )
          // Pull a few elements (leaves an inner flatMap stage active), then reset.
          r.read[Any](EOI)
          r.read[Any](EOI)
          r.reset()
          val out = List.fill(6)(r.read[Any](EOI))
          assertTrue(out == List(1, 1, 2, 2, 3, 3))
        },
        test("double close() is idempotent (no exception, no double finalization) [AdversarialResetAfterCloseSpec]") {
          val finalizations = new java.util.concurrent.atomic.AtomicInteger(0)
          val r             = Interpreter.fromStream(
            Stream.succeed(1).ensuring(finalizations.incrementAndGet())
          )
          r.read[Any](EOI)
          r.close()
          r.close()
          assertTrue(finalizations.get() == 1)
        },
        test("concat of interpreter-backed segments survives repeated replays [AdversarialResetAfterCloseSpec]") {
          var stream: Stream[Nothing, Int] = Stream.succeed(1)
          var i                            = 0
          while (i < 99) { stream = stream ++ Stream.succeed(1); i += 1 }
          val result = stream.repeated.take(300).runFold(0L)(_ + _)
          assertTrue(result == Right(300L))
        },
        test("ConcatReader reset() replays a head that advance() already closed [AdversarialResetAfterCloseSpec]") {
          val r = Reader.fromChunk(Chunk(1, 2)).concatRaw(() => Reader.fromChunk(Chunk(3, 4)), JvmType.Int)
          // Drain fully so advance() closes the head segment.
          val firstPass = List.fill(4)(r.read[Any](EOI))
          r.reset()
          val secondPass = List.fill(4)(r.read[Any](EOI))
          assertTrue(firstPass == List(1, 2, 3, 4) && secondPass == List(1, 2, 3, 4))
        }
      ),
      suite("drop eager skip")(
        // BUG-R5-01: `drop(n)`'s fallback wrapper performs its skip EAGERLY in
        // the SkipLimitReader constructor — i.e. during `compile`, BEFORE
        // `Stream.run` holds a reader it can close. If a skipped read fails
        // with a typed I/O error, the already-built upstream — including
        // `ensuring` finalizers and the `fromInputStream` release — is never
        // closed: the typed error is returned as `Left` but the resource leaks.
        // Contract: `ensuring` runs "whether it completes cleanly or with an
        // error"; `fromInputStream` "closes the stream when done".
        test("drop_eagerSkipFails_runsEnsuringFinalizerAndClosesSource [AdversarialDropEagerSkipLeakSpec]") {
          val closed                  = new AtomicBoolean(false)
          val finalized               = new AtomicBoolean(false)
          val is: java.io.InputStream = new java.io.InputStream {
            def read(): Int            = throw new java.io.IOException("skip-boom")
            override def close(): Unit = closed.set(true)
          }
          val result = Stream.fromInputStream(is).ensuring(finalized.set(true)).drop(1).runDrain
          assertTrue(result.isLeft, finalized.get, closed.get)
        }
      ),
      suite("resource ordering")(
        test("nested_acquireRelease_LIFO_full_drain [AdversarialResourceOrderingSpec]") {
          val log = ArrayBuffer.empty[String]
          val s   = logged(log)("A")(logged(log)("B")(Stream.range(0, 3)))
          val r   = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(0, 1, 2))) &&
          assertTrue(log.toList == List("acq:A", "acq:B", "rel:B", "rel:A"))
        },
        test("nested_acquireRelease_LIFO_early_take [AdversarialResourceOrderingSpec]") {
          val log = ArrayBuffer.empty[String]
          val s   = logged(log)("A")(logged(log)("B")(Stream.range(0, 100))).take(2)
          val r   = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(0, 1))) &&
          assertTrue(log.toList == List("acq:A", "acq:B", "rel:B", "rel:A"))
        },
        test("nested_acquireRelease_head_releases_all_once [AdversarialResourceOrderingSpec]") {
          val log = ArrayBuffer.empty[String]
          val s   = logged(log)("A")(logged(log)("B")(Stream.range(0, 100)))
          val r   = s.run(Sink.head[Int])
          assertTrue(r == Right(Some(0))) &&
          assertTrue(log.count(_ == "rel:A") == 1) &&
          assertTrue(log.count(_ == "rel:B") == 1) &&
          assertTrue(log.toList == List("acq:A", "acq:B", "rel:B", "rel:A"))
        },
        test("acquireRelease_find_shortCircuit_releases_once [AdversarialResourceOrderingSpec]") {
          val log = ArrayBuffer.empty[String]
          val s   = logged(log)("A")(Stream.range(0, 100))
          val r   = s.run(Sink.find[Int](_ == 5))
          assertTrue(r == Right(Some(5))) && assertTrue(log.count(_ == "rel:A") == 1)
        },
        test("ensuring_runs_once_on_full_drain [AdversarialResourceOrderingSpec]") {
          val log = ArrayBuffer.empty[String]
          val s   = Stream.range(0, 3).ensuring { log += "fin"; () }
          val r   = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(0, 1, 2))) && assertTrue(log.toList == List("fin"))
        },
        test("ensuring_runs_once_on_early_take [AdversarialResourceOrderingSpec]") {
          val log = ArrayBuffer.empty[String]
          val s   = Stream.range(0, 100).ensuring { log += "fin"; () }.take(2)
          val r   = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(0, 1))) && assertTrue(log.count(_ == "fin") == 1)
        },
        test("ensuring_inside_acquireRelease_ordering [AdversarialResourceOrderingSpec]") {
          // acquire A, then ensuring fin wraps the inner stream.
          // close order: fin then rel:A (ensuring is closer to source than acquireRelease's release).
          val log = ArrayBuffer.empty[String]
          val s   = logged(log)("A")(Stream.range(0, 3).ensuring { log += "fin"; () })
          val r   = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(0, 1, 2))) &&
          assertTrue(log.toList == List("acq:A", "fin", "rel:A"))
        },
        test("midstream_map_error_releases_all_resources_once [AdversarialResourceOrderingSpec]") {
          val log  = ArrayBuffer.empty[String]
          val boom = new RuntimeException("map-boom")
          // map throws on element 2 (a defect). Resources must still release once.
          val s = logged(log)("A")(logged(log)("B")(Stream.range(0, 10)))
            .map(x => if (x == 2) throw boom else x)
          val thrown = scala.util.Try(s.runCollect)
          assertTrue(thrown.isFailure) &&
          assertTrue(log.count(_ == "rel:A") == 1) &&
          assertTrue(log.count(_ == "rel:B") == 1)
        },
        test("midstream_stream_fail_releases_resources_once [AdversarialResourceOrderingSpec]") {
          val log = ArrayBuffer.empty[String]
          // concat a failing tail after a managed prefix; the error surfaces in Left
          // and the prefix resource releases exactly once.
          val s: Stream[String, Int] =
            logged(log)("A")(Stream.range(0, 2)).concat(Stream.fail("tail-boom"))
          val r = s.runCollect
          assertTrue(r == Left("tail-boom")) && assertTrue(log.count(_ == "rel:A") == 1)
        },
        test("acquireRelease_repeated_take_acquires_and_releases_per_cycle [AdversarialResourceOrderingSpec]") {
          // repeated re-runs the managed stream; each cycle must acquire then release.
          val log = ArrayBuffer.empty[String]
          val s   = logged(log)("A")(Stream.range(0, 2)).repeated.take(5)
          val r   = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(0, 1, 0, 1, 0))) &&
          // acquire and release counts must be balanced (no leak, no double-release)
          assertTrue(log.count(_ == "acq:A") == log.count(_ == "rel:A")) &&
          assertTrue(log.count(_ == "rel:A") >= 1)
        }
      ),
      suite("user-fn resource safety")(
        test("map_fnThrowsMidStream_releasesOnce_preservesSentinel [AdversarialUserFnResourceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val boom   = new RuntimeException("map-boom")
          val s      = managedUserFn(opens, closes).map(x => if (x == 2) throw boom else x)
          val t      = Try(s.runCollect)
          assertTrue(t.failed.toOption.contains(boom)) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        },
        test("filter_predicateThrowsMidStream_releasesOnce_preservesSentinel [AdversarialUserFnResourceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val boom   = new RuntimeException("filter-boom")
          val s      = managedUserFn(opens, closes).filter(x => if (x == 2) throw boom else true)
          val t      = Try(s.runCollect)
          assertTrue(t.failed.toOption.contains(boom)) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        },
        test("scan_fnThrowsMidStream_releasesOnce_preservesSentinel [AdversarialUserFnResourceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val boom   = new RuntimeException("scan-boom")
          val s      = managedUserFn(opens, closes).scan(0)((acc, x) => if (x == 2) throw boom else acc + x)
          val t      = Try(s.runCollect)
          assertTrue(t.failed.toOption.contains(boom)) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        },
        test("mapAccum_fnThrowsMidStream_releasesOnce_preservesSentinel [AdversarialUserFnResourceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val boom   = new RuntimeException("mapAccum-boom")
          val s      = managedUserFn(opens, closes).mapAccum(0)((acc, x) => if (x == 2) throw boom else (acc + x, x))
          val t      = Try(s.runCollect)
          assertTrue(t.failed.toOption.contains(boom)) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        },
        test("takeWhile_predicateThrowsMidStream_releasesOnce_preservesSentinel [AdversarialUserFnResourceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val boom   = new RuntimeException("takeWhile-boom")
          val s      = managedUserFn(opens, closes).takeWhile(x => if (x == 2) throw boom else true)
          val t      = Try(s.runCollect)
          assertTrue(t.failed.toOption.contains(boom)) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        },
        test("grouped_userMapperThrowsDownstream_releasesOnce [AdversarialUserFnResourceSpec]") {
          // grouped builds chunks; a downstream map over the chunk throws.
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val boom   = new RuntimeException("grouped-boom")
          val s      = managedUserFn(opens, closes).grouped(2).map[Chunk[Int]](c => if (c.length == 1) throw boom else c)
          val t      = Try(s.runCollect)
          assertTrue(t.failed.toOption.contains(boom)) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        },
        test("flatMap_fnThrowsBuildingInner_releasesOnce_preservesSentinel [AdversarialUserFnResourceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val boom   = new RuntimeException("flatMap-boom")
          val s      = managedUserFn(opens, closes).flatMap(x => if (x == 2) throw boom else Stream(x))
          val t      = Try(s.runCollect)
          assertTrue(t.failed.toOption.contains(boom)) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        }
      ),
      suite("zip resource")(
        test("left exhausts first: right (longer) finalizer runs exactly once [AdversarialZipResourceSpec]") {
          val rightCount = new AtomicInteger(0)
          // left has 2 elements, right (resource) has 3; left ends first.
          val zipped = Stream.fromChunk(Chunk(1, 2)) && resourceStream(rightCount, Chunk(10, 20, 30))
          val result = zipped.runCollect
          assertTrue(result == Right(Chunk((1, 10), (2, 20)))) &&
          assertTrue(rightCount.get == 1)
        },
        test("right exhausts first: left (longer) finalizer runs exactly once [AdversarialZipResourceSpec]") {
          val leftCount = new AtomicInteger(0)
          // left (resource) has 3 elements, right has 2; right ends first.
          val zipped = resourceStream(leftCount, Chunk(1, 2, 3)) && Stream.fromChunk(Chunk(10, 20))
          val result = zipped.runCollect
          assertTrue(result == Right(Chunk((1, 10), (2, 20)))) &&
          assertTrue(leftCount.get == 1)
        }
      ),
      suite("error-on-resource")(
        test("zip_leftFails_closesManagedRightOnce [AdversarialResourceOnErrorConvergenceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val left   = Stream(1, 2).flatMap(_ => (Stream.fail("boom"): Stream[String, Int]))
          val right  = managed(opens, closes)(Stream(10, 20, 30))
          val z      = left && right
          val r      = z.runCollect
          assertTrue(r == Left("boom")) && assertTrue(opens.get() == 1) && assertTrue(closes.get() == 1)
        },
        test("zip_rightFails_closesManagedLeftOnce [AdversarialResourceOnErrorConvergenceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val left   = managed(opens, closes)(Stream(10, 20, 30))
          val right  = Stream(1, 2).flatMap(_ => (Stream.fail("boom"): Stream[String, Int]))
          val z      = left && right
          val r      = z.runCollect
          assertTrue(r == Left("boom")) && assertTrue(opens.get() == 1) && assertTrue(closes.get() == 1)
        },
        test("catchAll_errorIdentityPreserved [AdversarialResourceOnErrorConvergenceSpec]") {
          val seen = new java.util.concurrent.atomic.AtomicReference[String]("")
          val s    = (Stream(1, 2) ++ Stream.fail("the-error")).catchAll { e => seen.set(e); Stream(99) }
          val r    = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(1, 2, 99))) && assertTrue(seen.get() == "the-error")
        },
        test("flatMap_innerManaged_closedPerInner [AdversarialResourceOnErrorConvergenceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val s      = Stream(1, 2, 3).flatMap(x => managed(opens, closes)(Stream(x * 10)))
          val r      = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(10, 20, 30))) &&
          assertTrue(opens.get() == 3) && assertTrue(closes.get() == 3)
        },
        test("flatMapPar_oneInnerFails_closesAllManagedInners [AdversarialResourceOnErrorConvergenceSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val s      = Stream
            .range(0, 6)
            .flatMapPar(2)(x => if (x == 3) Stream.fail("boom") else managed(opens, closes)(Stream(x)))
          val r = s.runCollect
          // Every managed inner that was opened must be closed (no leak).
          assertTrue(r == Left("boom")) && assertTrue(opens.get() == closes.get())
        },
        test("ensuring_runsOnce_onError [AdversarialResourceOnErrorConvergenceSpec]") {
          val count = new AtomicInteger(0)
          val s     = (Stream(1, 2) ++ Stream.fail("boom")).ensuring(count.incrementAndGet())
          val r     = s.runCollect
          assertTrue(r == Left("boom")) && assertTrue(count.get() == 1)
        },
        test("ensuring_runsOnce_onTakeEarlyClose [AdversarialResourceOnErrorConvergenceSpec]") {
          val count = new AtomicInteger(0)
          val s     = Stream.range(0, 100).ensuring(count.incrementAndGet()).take(3)
          val r     = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(0, 1, 2))) && assertTrue(count.get() == 1)
        }
      ),
      suite("repeated + eager-close finalizer balance")(
        // BUG-R7-01: a combinator that eagerly closes a constituent mid-stream
        // (zip's closeLeft after the shorter side ends; ConcatReader.advance
        // closing an exhausted segment) guards that close with idempotence
        // flags — but `reset()` (driven by `repeated` at every cycle boundary)
        // clears those flags, and the finalizer-bearing wrapper built by
        // fromAcquireRelease has a non-idempotent close(). The once-acquired
        // resource is therefore released at every cycle boundary AND at the
        // final close: releases > acquires (double finalization), violating the
        // balanced acquire/release invariant pinned by
        // acquireRelease_repeated_take_acquires_and_releases_per_cycle (above)
        // and managed_repeated_take_acquiresAndReleasesOnce (StreamLawsSpec) —
        // and cycles after the first read a resource whose release already ran.
        test("concat_managedHead_underRepeated_releaseBalancedWithAcquire [AdversarialRepeatedEagerCloseSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val s      = (managed(opens, closes)(Stream(1, 2)) ++ Stream(9)).repeated.take(5)
          val r      = s.runCollect
          assertTrue(r.map(_.toList) == Right(List(1, 2, 9, 1, 2))) &&
          assertTrue(closes.get() == opens.get())
        },
        test("zip_managedLongerLeft_underRepeated_releaseBalancedWithAcquire [AdversarialRepeatedEagerCloseSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val z      = managed(opens, closes)(Stream(1, 2, 3)) && Stream(10)
          val r      = z.repeated.take(2).runCollect
          assertTrue(r.map(_.toList) == Right(List((1, 10), (1, 10)))) &&
          assertTrue(closes.get() == opens.get())
        }
      )
    ),
    run11DeepResourceTreeSuite
  )

  // ---- Run #11 convergence probes ------------------------------------------
  // Eleventh (convergence-verification) round: resource TREES three levels deep
  // (fromAcquireRelease nested through two flatMap layers), with the failure
  // injected at every level — leaf read (typed), middle `use` function
  // (defect), leaf release (cleanup defect), typed leaf failure combined with a
  // middle release defect (defect-wins with the typed carrier preserved as
  // suppressed context), early termination via take, and catchAll recovery with
  // its own managed resource. Every acquired level must be released exactly
  // once. Committed as convergence evidence.

  /** One level of a managed-resource tree with open/close accounting. */
  private final class Run11Level(name: String, failRelease: Boolean = false) {
    val opens                                              = new AtomicInteger(0)
    val closes                                             = new AtomicInteger(0)
    val releaseBoom: CleanupSentinel                       = new CleanupSentinel(s"run11-release-$name")
    def around[E, A](inner: => Stream[E, A]): Stream[E, A] =
      Stream.fromAcquireRelease(
        { opens.incrementAndGet(); () },
        (_: Unit) => {
          closes.incrementAndGet()
          if (failRelease) throw releaseBoom
          ()
        }
      )(_ => inner)
  }

  /**
   * True iff `target` is reachable from `t` via cause/suppressed at any depth.
   */
  private def run11Reaches(t: Throwable, target: Throwable): Boolean =
    (t ne null) && ((t eq target) || run11Reaches(t.getCause, target) || t.getSuppressed.exists(
      run11Reaches(_, target)
    ))

  private def run11ReachesClass(t: Throwable, c: Class[_]): Boolean =
    (t ne null) && (c.isInstance(t) || run11ReachesClass(t.getCause, c) || t.getSuppressed.exists(
      run11ReachesClass(_, c)
    ))

  private def run11DeepResourceTreeSuite = suite("run11 deep resource trees")(
    test("threeLevels_success_eachLevelBalanced") {
      val l1 = new Run11Level("l1"); val l2 = new Run11Level("l2"); val l3 = new Run11Level("l3")
      val s  = l1.around {
        Stream(1, 2).flatMap { _ =>
          l2.around {
            Stream(0).flatMap(_ => l3.around(Stream(10, 20)))
          }
        }
      }
      val r = s.runCollect
      assertTrue(
        r.map(_.toList) == Right(List(10, 20, 10, 20)),
        l1.opens.get() == 1,
        l1.closes.get() == 1,
        l2.opens.get() == 2,
        l2.closes.get() == 2,
        l3.opens.get() == 2,
        l3.closes.get() == 2
      )
    },
    test("threeLevels_leafTypedFailure_allLevelsReleaseOnce_errorPreserved") {
      val l1                     = new Run11Level("l1"); val l2 = new Run11Level("l2"); val l3 = new Run11Level("l3")
      val s: Stream[String, Int] = l1.around {
        Stream(1, 2).flatMap { _ =>
          l2.around {
            Stream(0).flatMap(_ =>
              l3.around((Stream(10): Stream[Nothing, Int]) ++ (Stream.fail("leaf-boom"): Stream[String, Nothing]))
            )
          }
        }
      }
      val r = s.runCollect
      assertTrue(
        r == Left("leaf-boom"),
        l1.opens.get() == 1,
        l1.closes.get() == 1,
        l2.opens.get() == 1,
        l2.closes.get() == 1,
        l3.opens.get() == 1,
        l3.closes.get() == 1
      )
    },
    test("threeLevels_midUseFnThrows_defectSurfaced_allOpenedReleased") {
      val l1   = new Run11Level("l1"); val l2 = new Run11Level("l2")
      val boom = new RuntimeException("run11-mid-boom")
      val s    = l1.around {
        Stream(1, 2).flatMap { _ =>
          l2.around[Nothing, Int]((throw boom): Stream[Nothing, Int])
        }
      }
      val r = Try(s.runCollect)
      assertTrue(
        r.isFailure,
        r.fold(run11Reaches(_, boom), _ => false),
        l1.opens.get() == 1,
        l1.closes.get() == 1,
        l2.opens.get() == 1,
        l2.closes.get() == 1
      )
    },
    test("threeLevels_leafReleaseThrows_cleanupDefectSurfaced_outerLevelsStillReleased") {
      val l1 = new Run11Level("l1"); val l2 = new Run11Level("l2")
      val l3 = new Run11Level("l3", failRelease = true)
      val s  = l1.around(Stream(0).flatMap(_ => l2.around(Stream(0).flatMap(_ => l3.around(Stream(1, 2))))))
      val r  = Try(s.runCollect)
      assertTrue(
        r.isFailure,
        r.fold(run11Reaches(_, l3.releaseBoom), _ => false),
        l1.closes.get() == 1,
        l2.closes.get() == 1,
        l3.closes.get() == 1
      )
    },
    test("threeLevels_typedLeafFailure_plusMidReleaseDefect_defectWinsCarrierSuppressed") {
      val l1                     = new Run11Level("l1")
      val l2                     = new Run11Level("l2", failRelease = true)
      val l3                     = new Run11Level("l3")
      val s: Stream[String, Int] = l1.around {
        Stream(1).flatMap { _ =>
          l2.around {
            Stream(0).flatMap(_ =>
              l3.around((Stream(10): Stream[Nothing, Int]) ++ (Stream.fail("leaf-boom"): Stream[String, Nothing]))
            )
          }
        }
      }
      val r = Try(s.runCollect)
      assertTrue(
        r.isFailure,
        r.fold(run11Reaches(_, l2.releaseBoom), _ => false),
        r.fold(run11ReachesClass(_, classOf[StreamError]), _ => false),
        l1.closes.get() == 1,
        l2.closes.get() == 1,
        l3.closes.get() == 1
      )
    },
    test("threeLevels_takeOne_earlyClose_releasesAllOnce") {
      val l1 = new Run11Level("l1"); val l2 = new Run11Level("l2"); val l3 = new Run11Level("l3")
      val s  = l1.around {
        Stream(1, 2).flatMap { _ =>
          l2.around(Stream(0).flatMap(_ => l3.around(Stream(10, 20))))
        }
      }
      val r = s.take(1L).runCollect
      assertTrue(
        r.map(_.toList) == Right(List(10)),
        l1.opens.get() == 1,
        l1.closes.get() == 1,
        l2.opens.get() == 1,
        l2.closes.get() == 1,
        l3.opens.get() == 1,
        l3.closes.get() == 1
      )
    },
    test("threeLevels_catchAllAtMidLevel_recoveryResourceBalanced") {
      val l1 = new Run11Level("l1"); val l2 = new Run11Level("l2"); val l3 = new Run11Level("l3")
      val lr = new Run11Level("recovery")
      val s  = l1.around {
        Stream(1).flatMap { _ =>
          l2.around {
            (Stream(0): Stream[Nothing, Int])
              .flatMap(_ =>
                l3.around((Stream(10): Stream[Nothing, Int]) ++ (Stream.fail("mid-boom"): Stream[String, Nothing]))
              )
              .catchAll(_ => lr.around(Stream(77)))
          }
        }
      }
      val r = s.runCollect
      assertTrue(
        r.map(_.toList) == Right(List(10, 77)),
        l1.opens.get() == 1,
        l1.closes.get() == 1,
        l2.opens.get() == 1,
        l2.closes.get() == 1,
        l3.opens.get() == 1,
        l3.closes.get() == 1,
        lr.opens.get() == 1,
        lr.closes.get() == 1
      )
    }
  )
}
