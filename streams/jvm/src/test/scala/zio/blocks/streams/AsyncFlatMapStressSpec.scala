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

import zio._
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncFlatMapStressSpec extends StreamsBaseSpec {
  private final class ReadyRangeReader(from: Int, until: Int) extends Reader.AsyncReader[Int] {
    private var closed  = false
    private var current = from

    override def jvmType: JvmType = JvmType.Int

    def close(): Async[Unit] = {
      closed = true
      Async.succeed(())
    }

    def isClosed: Async[Boolean] = Async.succeed(closed)

    def readable(): Async[Boolean] = Async.succeed(!closed && current < until)

    def read[A >: Int](sentinel: A): Async[A] =
      if (closed || current >= until) Async.succeed(sentinel)
      else {
        val value = current
        current += 1
        Async.succeed(value)
      }

    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      if (closed || current >= until) Async.succeed(sentinel)
      else {
        val value = current.toLong
        current += 1
        Async.succeed(value)
      }

    override def readUpToN[A >: Int](n: Int): Async[Chunk[A]] = Async.succeed {
      val length = math.min(math.max(n, 0), math.max(until - current, 0))
      if (closed || length == 0) Chunk.empty
      else {
        val values = Array.tabulate(length)(current + _)
        current += length
        Chunk.fromArray(values).asInstanceOf[Chunk[A]]
      }
    }
  }

  private def source(from: Int, until: Int): Stream[Nothing, Int] =
    Stream.fromReaderAsync(Async.succeed(new ReadyRangeReader(from, until)))

  def spec: Spec[TestEnvironment, Any] = suite("AsyncFlatMapStressSpec")(
    test("async flatMap cannot lose its continuation after repeated child handoffs") {
      ZIO.attemptBlockingInterrupt {
        val n      = 100_000
        val result = source(0, n)
          .flatMap(value => source(value * 2, value * 2 + 1))
          .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value))
          .block
        assertTrue(result == Right(n.toLong * (n - 1L)))
      }
    }
  )
}
