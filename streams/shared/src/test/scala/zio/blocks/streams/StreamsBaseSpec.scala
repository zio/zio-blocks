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

import zio.{durationInt, ZIO}
import zio.blocks.async._
import zio.blocks.streams.io.Reader
import zio.test._

/** Base trait for all streams test specs. */
trait StreamsBaseSpec extends ZIOSpecDefault {
  protected implicit final class ReaderTestOps[A](private val reader: Reader[A]) {
    def expectedSync: Reader.SyncReader[A]   = requireSyncReader(reader)
    def expectedAsync: Reader.AsyncReader[A] = requireAsyncReader(reader)
  }

  protected final def requireSyncReader[A](reader: Reader[A]): Reader.SyncReader[A] = reader match {
    case sync: Reader.SyncReader[A @unchecked] => sync
    case _: Reader.AsyncReader[_]              => throw new AssertionError("Expected a SyncReader, but got an AsyncReader")
  }

  protected final def requireAsyncReader[A](reader: Reader[A]): Reader.AsyncReader[A] = reader match {
    case async: Reader.AsyncReader[A @unchecked] => async
    case _: Reader.SyncReader[_]                 => throw new AssertionError("Expected an AsyncReader, but got a SyncReader")
  }

  protected final def compileToSyncReader[E, A](stream: Stream[E, A]): Reader.SyncReader[A] =
    requireSyncReader(Stream.compileToReader(stream))

  protected final def runAsync[A](effect: Async[A]): ZIO[Any, Throwable, A] = {
    implicit val executionContext: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.parasitic
    ZIO.fromFuture(_ => effect.toFuture)
  }

  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM)
      zio.Chunk(TestAspect.timeout(30.seconds), TestAspect.timed)
    else
      zio.Chunk(TestAspect.timeout(30.seconds), TestAspect.timed, TestAspect.sequential, TestAspect.size(10))
}
