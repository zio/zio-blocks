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
import zio.blocks.streams.io.Reader
import zio.test._

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

/**
 * Comprehensive resource leak tests for [[Stream]].
 *
 * Verifies that resources acquired during stream execution are always released,
 * regardless of whether the stream completes normally, fails with a typed error
 * ([[StreamError]]), or fails with a defect (e.g. [[RuntimeException]]).
 *
 * Covers: ensuring, fromAcquireRelease, fromResource, catchAll, catchDefect,
 * and interactions with combinators (map, filter, take, drop, flatMap, concat).
 */
object ResourceLeakSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ResourceLeakSpec")(
    // ---- ensuring -----------------------------------------------------------

    suite("ensuring")(
      test("finalizer runs on normal completion") {
        val released = new AtomicBoolean(false)
        runAsync(Stream.range(0, 5).ensuring(released.set(true)).runCollectAsync).map { result =>
          assertTrue(result == Right(Chunk.fromIterable(0 until 5))) &&
          assertTrue(released.get)
        }
      },
      test("finalizer runs on typed error (StreamError)") {
        val released = new AtomicBoolean(false)
        runAsync(Stream.fail("boom").ensuring(released.set(true)).runCollectAsync).map { result =>
          assertTrue(result == Left("boom")) &&
          assertTrue(released.get)
        }
      },
      test("finalizer runs on defect (RuntimeException)") {
        val released = new AtomicBoolean(false)
        zio.ZIO
          .attempt(Stream.die(new RuntimeException("defect")).ensuring(released.set(true)).runCollectAsync)
          .flatMap(effect => runAsync(effect))
          .either
          .map { result =>
            val caught = result match {
              case Left(_: RuntimeException) => true
              case _                         => false
            }
            assertTrue(caught) &&
            assertTrue(released.get)
          }
      },
      test("error in middle of stream: ensuring finalizer still runs") {
        val released = new AtomicBoolean(false)
        val stream   = (Stream.range(0, 3) ++ Stream.fail("mid-error")).ensuring(released.set(true))
        runAsync(stream.runCollectAsync).map { result =>
          assertTrue(result == Left("mid-error")) &&
          assertTrue(released.get)
        }
      },
      test("nested ensuring: both finalizers run (inner-first due to wrapping)") {
        // Each ensuring wraps with `try src.close() finally finalizer`, so the
        // inner (first-registered) finalizer runs before the outer one.
        val order  = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
        val stream = Stream
          .range(0, 3)
          .ensuring { order.add(1); () }
          .ensuring { order.add(2); () }
        runAsync(stream.runCollectAsync).map { result =>
          assertTrue(result == Right(Chunk.fromIterable(0 until 3))) &&
          assertTrue(order.toArray.toList == List(1, 2))
        }
      },
      test("ensuring + map: finalizer runs") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .range(0, 3)
            .ensuring(released.set(true))
            .map(_ * 2)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 2, 4))) &&
          assertTrue(released.get)
        }
      },
      test("ensuring + filter: finalizer runs") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .range(0, 6)
            .ensuring(released.set(true))
            .filter(_ % 2 == 0)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 2, 4))) &&
          assertTrue(released.get)
        }
      },
      test("ensuring + take: finalizer runs even with early termination") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .range(0, 100)
            .ensuring(released.set(true))
            .take(3)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 1, 2))) &&
          assertTrue(released.get)
        }
      },
      test("ensuring + drop: finalizer runs") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .range(0, 5)
            .ensuring(released.set(true))
            .drop(2)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(2, 3, 4))) &&
          assertTrue(released.get)
        }
      },
      test("ensuring + flatMap: both outer and inner finalizers run") {
        val outerReleased = new AtomicBoolean(false)
        val innerReleased = new AtomicBoolean(false)
        runAsync(
          Stream(1, 2)
            .ensuring(outerReleased.set(true))
            .flatMap { i =>
              Stream.succeed(i * 10).ensuring(innerReleased.set(true))
            }
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(10, 20))) &&
          assertTrue(outerReleased.get) &&
          assertTrue(innerReleased.get)
        }
      },
      test("upstream close failure remains primary when the finalizer also fails") {
        val closeFailure      = new RuntimeException("close")
        val finalizerFailure  = new RuntimeException("finalizer")
        var closeAttempts     = 0
        var finalizerAttempts = 0
        val reader            = Stream
          .fromReader[Nothing, Int](new Reader.SyncReader[Int] {
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = sentinel
            def close(): Unit                     = {
              closeAttempts += 1
              throw closeFailure
            }
          })
          .ensuring {
            finalizerAttempts += 1
            throw finalizerFailure
          }
          .compile(0)
          .expectedSync

        val first = try { reader.close(); null }
        catch { case t: Throwable => t }
        val second = try { reader.close(); null }
        catch { case t: Throwable => t }

        assertTrue(
          first eq closeFailure,
          second eq closeFailure,
          first.getSuppressed.toList == List(finalizerFailure),
          closeAttempts == 1,
          finalizerAttempts == 1
        )
      }
    ),

    // ---- catchAll -----------------------------------------------------------

    suite("catchAll resource safety")(
      test("finalizer of failed stream runs, then recovery stream's finalizer runs") {
        val failedReleased   = new AtomicBoolean(false)
        val recoveryReleased = new AtomicBoolean(false)
        runAsync(
          Stream
            .fail("err")
            .ensuring(failedReleased.set(true))
            .catchAll((_: String) => Stream.succeed(42).ensuring(recoveryReleased.set(true)))
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(42))) &&
          assertTrue(failedReleased.get) &&
          assertTrue(recoveryReleased.get)
        }
      },
      test("catchAll: ensuring on partial stream before error") {
        val released = new AtomicBoolean(false)
        runAsync(
          (Stream.range(0, 3) ++ Stream.fail("err"))
            .ensuring(released.set(true))
            .catchAll((_: String) => Stream.succeed(99))
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 1, 2, 99))) &&
          assertTrue(released.get)
        }
      },
      test("close preserves the active stream close failure") {
        val closeFailure  = new RuntimeException("close")
        var closeAttempts = 0
        val reader        = Stream
          .fromReader[String, Int](new Reader.SyncReader[Int] {
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = sentinel
            def close(): Unit                     = {
              closeAttempts += 1
              throw closeFailure
            }
          })
          .catchAll(_ => Stream.empty)
          .compile(0)
          .expectedAsync

        for {
          first  <- runAsync(reader.close()).either
          second <- runAsync(reader.close()).either
        } yield assertTrue(
          first == Left(closeFailure),
          second == Left(closeFailure),
          closeAttempts == 1
        )
      }
    ),

    // ---- catchDefect --------------------------------------------------------

    suite("catchDefect resource safety")(
      test("finalizer of defective stream runs, recovery stream's finalizer runs") {
        val defectReleased   = new AtomicBoolean(false)
        val recoveryReleased = new AtomicBoolean(false)
        runAsync(
          Stream
            .die(new RuntimeException("boom"))
            .ensuring(defectReleased.set(true))
            .catchDefect { case _: RuntimeException =>
              Stream.succeed(42).ensuring(recoveryReleased.set(true))
            }
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(42))) &&
          assertTrue(defectReleased.get) &&
          assertTrue(recoveryReleased.get)
        }
      },
      test("catchDefect: ensuring on partial stream before defect") {
        val released = new AtomicBoolean(false)
        val stream   = Stream
          .fromReader[Nothing, Int](new Reader.SyncReader[Int] {
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
        runAsync(stream.runCollectAsync).map { result =>
          assertTrue(result == Right(Chunk(1, 2, 3))) &&
          assertTrue(released.get)
        }
      },
      test("close preserves the active stream close failure") {
        val closeFailure  = new RuntimeException("close")
        var closeAttempts = 0
        val reader        = Stream
          .fromReader[Nothing, Int](new Reader.SyncReader[Int] {
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = sentinel
            def close(): Unit                     = {
              closeAttempts += 1
              throw closeFailure
            }
          })
          .catchDefect { case _ => Stream.empty }
          .compile(0)
          .expectedAsync

        for {
          first  <- runAsync(reader.close()).either
          second <- runAsync(reader.close()).either
        } yield assertTrue(
          first == Left(closeFailure),
          second == Left(closeFailure),
          closeAttempts == 1
        )
      }
    ),

    // ---- fromAcquireRelease -------------------------------------------------

    suite("fromAcquireRelease")(
      test("release runs on normal completion") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .fromAcquireRelease("resource", (_: String) => released.set(true)) { r =>
              Stream.succeed(r)
            }
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk("resource"))) &&
          assertTrue(released.get)
        }
      },
      test("release runs on typed error in use") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .fromAcquireRelease("resource", (_: String) => released.set(true)) { _ =>
              Stream.fail("error"): Stream[String, Nothing]
            }
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Left("error")) &&
          assertTrue(released.get)
        }
      },
      test("release runs on defect in use") {
        val released = new AtomicBoolean(false)
        zio.ZIO
          .attempt(
            Stream
              .fromAcquireRelease("resource", (_: String) => released.set(true)) { _ =>
                Stream.die(new RuntimeException("defect")): Stream[Nothing, Nothing]
              }
              .runCollectAsync
          )
          .flatMap(effect => runAsync(effect))
          .either
          .map { result =>
            val caught = result match {
              case Left(_: RuntimeException) => true
              case _                         => false
            }
            assertTrue(caught) &&
            assertTrue(released.get)
          }
      },
      test("release runs on defect during compile (inside use function)") {
        val released = new AtomicBoolean(false)
        zio.ZIO
          .attempt(
            Stream
              .fromAcquireRelease("resource", (_: String) => released.set(true)) { _ =>
                throw new RuntimeException("compile defect")
                Stream.empty
              }
              .runCollectAsync
          )
          .flatMap(effect => runAsync(effect))
          .either
          .map { result =>
            val caught = result match {
              case Left(_: RuntimeException) => true
              case _                         => false
            }
            assertTrue(caught) &&
            assertTrue(released.get)
          }
      },
      test("compile defect remains primary and suppresses release failure") {
        val compileFailure = new RuntimeException("compile")
        val releaseFailure = new RuntimeException("release")

        val caught = try {
          Stream
            .fromAcquireRelease("resource", (_: String) => throw releaseFailure) { _ =>
              throw compileFailure
            }
            .compile(0)
            .expectedSync
          null
        } catch { case t: Throwable => t }

        assertTrue(caught eq compileFailure) &&
        assertTrue(caught.getSuppressed.toList == List(releaseFailure))
      },
      test("upstream close failure remains primary and release is exact-once") {
        val closeFailure    = new RuntimeException("close")
        val releaseFailure  = new RuntimeException("release")
        var closeAttempts   = 0
        var releaseAttempts = 0
        val reader          = Stream
          .fromAcquireRelease(
            "resource",
            (_: String) => {
              releaseAttempts += 1
              throw releaseFailure
            }
          ) { _ =>
            Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
              def isClosed: Boolean                 = false
              def read[A1 >: Int](sentinel: A1): A1 = sentinel
              def close(): Unit                     = {
                closeAttempts += 1
                throw closeFailure
              }
            })
          }
          .compile(0)
          .expectedSync

        val first = try { reader.close(); null }
        catch { case t: Throwable => t }
        val second = try { reader.close(); null }
        catch { case t: Throwable => t }

        assertTrue(
          first eq closeFailure,
          second eq closeFailure,
          first.getSuppressed.toList == List(releaseFailure),
          closeAttempts == 1,
          releaseAttempts == 1
        )
      }
    ),

    // ---- fromResource (Scope-based) -----------------------------------------

    suite("fromResource")(
      test("resource is closed when stream completes normally") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        runAsync(
          Stream
            .fromResource(resource)(r => Stream.succeed(r))
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk("res"))) &&
          assertTrue(closed.get)
        }
      },
      test("resource is closed when stream fails with typed error") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        runAsync(
          Stream
            .fromResource(resource)(_ => Stream.fail("err"): Stream[String, Nothing])
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Left("err")) &&
          assertTrue(closed.get)
        }
      },
      test("resource is closed when use function throws during compilation") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        zio.ZIO
          .attempt(
            Stream
              .fromResource(resource) { _ =>
                throw new RuntimeException("compile-time boom")
              }
              .runCollectAsync
          )
          .flatMap(effect => runAsync(effect))
          .either
          .map { result =>
            val caught = result match {
              case Left(_: RuntimeException) => true
              case _                         => false
            }
            assertTrue(caught) &&
            assertTrue(closed.get)
          }
      },
      test("resource is closed when stream fails with defect") {
        val closed   = new AtomicBoolean(false)
        val resource = Resource.acquireRelease("res")(_ => closed.set(true))
        zio.ZIO
          .attempt(
            Stream
              .fromResource(resource) { _ =>
                Stream.die(new RuntimeException("defect")): Stream[Nothing, Nothing]
              }
              .runCollectAsync
          )
          .flatMap(effect => runAsync(effect))
          .either
          .map { result =>
            val caught = result match {
              case Left(_: RuntimeException) => true
              case _                         => false
            }
            assertTrue(caught) &&
            assertTrue(closed.get)
          }
      },
      test("upstream close failure remains primary and scope release is exact-once") {
        val closeFailure    = new RuntimeException("close")
        val releaseFailure  = new RuntimeException("release")
        var closeAttempts   = 0
        var releaseAttempts = 0
        val resource        = Resource.acquireRelease("resource") { _ =>
          releaseAttempts += 1
          throw releaseFailure
        }
        val reader = Stream
          .fromResource(resource) { _ =>
            Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
              def isClosed: Boolean                 = false
              def read[A1 >: Int](sentinel: A1): A1 = sentinel
              def close(): Unit                     = {
                closeAttempts += 1
                throw closeFailure
              }
            })
          }
          .compile(0)
          .expectedSync

        val first = try { reader.close(); null }
        catch { case t: Throwable => t }
        val second = try { reader.close(); null }
        catch { case t: Throwable => t }

        assertTrue(
          first eq closeFailure,
          second eq closeFailure,
          first.getSuppressed.toList == List(releaseFailure),
          closeAttempts == 1,
          releaseAttempts == 1
        )
      },
      test("compile defect remains primary and suppresses scope release failure") {
        val compileFailure = new RuntimeException("compile")
        val releaseFailure = new RuntimeException("release")
        val resource       = Resource.acquireRelease("resource")(_ => throw releaseFailure)

        val caught = try {
          Stream
            .fromResource(resource)(_ => throw compileFailure)
            .compile(0)
            .expectedSync
          null
        } catch { case t: Throwable => t }

        assertTrue(caught eq compileFailure, caught.getSuppressed.toList == List(releaseFailure))
      }
    ),

    // ---- take + ensuring ----------------------------------------------------

    suite("take with ensuring")(
      test("take(n) with ensuring: finalizer runs even though stream ends early") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .repeat(1)
            .ensuring(released.set(true))
            .take(5)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(1, 1, 1, 1, 1))) &&
          assertTrue(released.get)
        }
      },
      test("take(0) with ensuring: finalizer runs immediately") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .range(0, 100)
            .ensuring(released.set(true))
            .take(0)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk.empty[Int])) &&
          assertTrue(released.get)
        }
      }
    ),

    // ---- concat resource safety ---------------------------------------------

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
        runAsync(stream.runCollectAsync).map { result =>
          assertTrue(result == Left("err")) &&
          assertTrue(firstReleased.get) &&
          assertTrue(!secondStarted.get) &&
          assertTrue(!secondReleased.get)
        }
      },
      test("concat: both streams' resources released on normal completion") {
        val firstReleased  = new AtomicBoolean(false)
        val secondReleased = new AtomicBoolean(false)
        runAsync(
          (
            Stream.range(0, 3).ensuring(firstReleased.set(true)) ++
              Stream.range(3, 6).ensuring(secondReleased.set(true))
          ).runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk.fromIterable(0 until 6))) &&
          assertTrue(firstReleased.get) &&
          assertTrue(secondReleased.get)
        }
      }
    ),

    // ---- multiple resources in sequence -------------------------------------

    suite("multiple resources in sequence")(
      test("all released in correct order") {
        val releaseOrder = new java.util.concurrent.ConcurrentLinkedQueue[String]()
        runAsync(
          Stream
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
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk("A", "B", "C"))) &&
          assertTrue(releaseOrder.toArray.toList == List("A", "B", "C"))
        }
      },
      test("multiple ensuring finalizers all run") {
        val count = new AtomicInteger(0)
        runAsync(
          Stream
            .range(0, 2)
            .ensuring { count.incrementAndGet(); () }
            .map(_ + 10)
            .ensuring { count.incrementAndGet(); () }
            .filter(_ => true)
            .ensuring { count.incrementAndGet(); () }
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(10, 11))) &&
          assertTrue(count.get == 3)
        }
      }
    ),

    // ---- edge cases ---------------------------------------------------------

    suite("edge cases")(
      test("ensuring on empty stream: finalizer still runs") {
        val released = new AtomicBoolean(false)
        runAsync(Stream.empty.ensuring(released.set(true)).runCollectAsync).map { result =>
          assertTrue(result == Right(Chunk.empty)) &&
          assertTrue(released.get)
        }
      },
      test("fromAcquireRelease with empty stream: release still runs") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .fromAcquireRelease("res", (_: String) => released.set(true)) { _ =>
              Stream.empty: Stream[Nothing, Nothing]
            }
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk.empty)) &&
          assertTrue(released.get)
        }
      },
      test("ensuring + takeWhile: finalizer runs on early termination") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .range(0, 100)
            .ensuring(released.set(true))
            .takeWhile(_ < 3)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 1, 2))) &&
          assertTrue(released.get)
        }
      },
      test("fromAcquireRelease + takeWhile: release runs on early termination") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .fromAcquireRelease("res", (_: String) => released.set(true)) { _ =>
              Stream.range(0, 100)
            }
            .takeWhile(_ < 3)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 1, 2))) &&
          assertTrue(released.get)
        }
      },
      test("ensuring + repeated + take: finalizer runs when repeated stream is closed") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .range(0, 3)
            .ensuring(released.set(true))
            .repeated
            .take(5)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 1, 2, 0, 1))) &&
          assertTrue(released.get)
        }
      },
      test("fromAcquireRelease + repeated + take: release runs") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .fromAcquireRelease("res", (_: String) => released.set(true)) { _ =>
              Stream.range(0, 3)
            }
            .repeated
            .take(5)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Right(Chunk(0, 1, 2, 0, 1))) &&
          assertTrue(released.get)
        }
      },
      test("ensuring + mapError: finalizer still runs") {
        val released = new AtomicBoolean(false)
        runAsync(
          Stream
            .fail(42)
            .ensuring(released.set(true))
            .mapError(_.toString)
            .runCollectAsync
        ).map { result =>
          assertTrue(result == Left("42")) &&
          assertTrue(released.get)
        }
      }
    )
  )
}
