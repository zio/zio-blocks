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

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.scope.Resource
import zio.blocks.streams.io.Reader
import zio.test._

object StreamSinkCriticalResidualAug21Spec extends StreamsBaseSpec {
  private def async[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class HookReader(onReset: () => Unit, onClose: () => Unit) extends Reader.SyncReader[Int] {
    private var done                   = false
    override def jvmType: JvmType      = JvmType.Int
    def isClosed: Boolean              = done
    def close(): Unit                  = { done = true; onClose() }
    override def reset(): Unit         = { done = false; onReset() }
    def read[A >: Int](sentinel: A): A = if (done) sentinel else { done = true; 1 }
  }

  def spec = suite("Stream and Sink critical residuals")(
    test("resource acquisition failure closes the partial scope and suppresses all cleanup failures") {
      val acquisition = new RuntimeException("acquisition")
      val cleanup     = new RuntimeException("cleanup")
      val released    = new AtomicInteger
      val resource    = Resource
        .acquireRelease("opened") { _ =>
          released.incrementAndGet()
          throw cleanup
        }
        .flatMap(_ => Resource[Int](throw acquisition))
      val result = Stream.fromResource(resource)(_ => Stream.unwrap(Async.succeed(Stream.empty))).runCollectAsync.either
      runAsync(result).map(exit =>
        assertTrue(exit == Left(acquisition), released.get() == 1, acquisition.getSuppressed.toList == List(cleanup))
      )
    },
    test("zip discards a failing pull made stale by concurrent close") {
      val gate = new Completer[Any]
      val left = new Reader.AsyncReader[Any] {
        override def jvmType                      = JvmType.AnyRef
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(true)
        def close()                               = Async.succeed(())
        def read[A >: Any](sentinel: A): Async[A] = gate
      }
      val reader = Stream
        .compileToReader(Stream.fromReader[Nothing, Any](left) && Stream.fromReader[Nothing, Int](async(1)))
        .expectedAsync
      val pull = reader.read[Any]("pull-eof")
      for {
        _      <- runAsync(reader.close())
        _       = gate.fail(new RuntimeException("late"))
        value  <- runAsync(pull)
        closed <- runAsync(reader.isClosed)
      } yield assertTrue(value == null, closed)
    },
    test("close reentered during reset finalizes once and rejects the reset") {
      val finalized                       = new AtomicInteger
      var wrapped: Reader.SyncReader[Int] = null
      val source                          = new HookReader(() => wrapped.close(), () => ())
      wrapped = Stream
        .compileToReader(Stream.fromReader[Nothing, Int](source).ensuring(finalized.incrementAndGet()))
        .expectedSync
      val result = scala.util.Try(wrapped.reset()).failed.toOption
      assertTrue(
        result.exists(_.isInstanceOf[UnsupportedOperationException]),
        finalized.get() == 1,
        wrapped.isClosed
      )
    },
    test("close preserves source failure and attaches finalizer failure") {
      val sourceFailure = new RuntimeException("source-close")
      val finalFailure  = new RuntimeException("finalizer")
      val source        = new HookReader(() => (), () => throw sourceFailure)
      val reader        =
        Stream.compileToReader(Stream.fromReader[Nothing, Int](source).ensuring(throw finalFailure)).expectedSync
      val result = scala.util.Try(reader.close()).failed.toOption
      assertTrue(result.contains(sourceFailure), sourceFailure.getSuppressed.toList == List(finalFailure))
    },
    test("stack-safe asynchronous unary and primitive collect branches preserve values") {
      val deep = (0 until 140).foldLeft[Stream[Nothing, Int]](Stream.fromReader[Nothing, Int](async(1, 2, 3))) {
        case (stream, _) => stream.map(identity)
      }
      val stream = deep.mapAsync(n => Async.succeed(n + 1)).collect { case n if n != 3 => n.toDouble + 0.5 }
      runAsync(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(2.5, 4.5))))
    },
    test("drain consumes the specialized double lane") {
      val result = Stream(1.0, 2.0, 3.0).runBlocking(Sink.drain)
      assertTrue(result == Right(()))
    }
  )
}
