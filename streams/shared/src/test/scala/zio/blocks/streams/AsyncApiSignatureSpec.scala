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

import asyncapisignatureexternal.AsyncApiExternalCallSite
import zio.blocks.async.Async
import zio.test._

object AsyncApiSignatureSpec extends StreamsBaseSpec {
  def spec = suite("Async API signatures")(test("production APIs compile at an external call site") {
    val stream                             = Stream(1, 2, 3)
    val mapped: Stream[Nothing, Long]      = stream.mapAsync(i => Async.succeed(i.toLong))
    val filtered: Stream[Nothing, Int]     = stream.filterAsync(i => Async.succeed(i > 1))
    val collected: Stream[Nothing, String] = stream.collectAsync(i => Async.succeed(Option.when(i > 1)(i.toString)))
    val tapped: Stream[Nothing, Int]       = stream.tapEachAsync(_ => Async.succeed(()))
    val distinct: Stream[Nothing, Int]     = stream.distinctByAsync(i => Async.succeed(i % 2))
    val taken: Stream[Nothing, Int]        = stream.takeWhileAsync(i => Async.succeed(i < 3))
    val accumulated: Stream[Nothing, Long] =
      stream.mapAccumAsync(0L)((state, i) => Async.succeed((state + i, state + i)))
    val scanned: Stream[Nothing, Long]            = stream.scanAsync(0L)((state, i) => Async.succeed(state + i))
    val attempted: Stream[Throwable, Int]         = Stream.attemptAsync(Async.succeed(1))
    val attemptedEval: Stream[Throwable, Nothing] = Stream.attemptEvalAsync(Async.succeed(()))
    val evaluated: Stream[Nothing, Nothing]       = Stream.evalAsync(Async.succeed(()))
    val deferred: Stream[Nothing, Nothing]        = Stream.deferAsync(Async.succeed(()))
    val unwrapped: Stream[Nothing, Int]           = Stream.unwrap(Async.succeed(stream))
    val buffered: Stream[Nothing, Int]            = Stream.bufferSize(16)(Stream.unwrap(Async.succeed(stream)))
    val iterated: Stream[Nothing, Int]            = Stream.fromIteratorAsync(Async.succeed(Iterator(1)))
    val read: Stream[Nothing, Int]                = Stream.fromReaderAsync(Async.succeed(zio.blocks.streams.io.Reader.singleInt(1)))
    val managed: Stream[Nothing, Int]             =
      Stream.fromAcquireReleaseAsync(Async.succeed(1), (_: Int) => Async.succeed(()))(i => Stream(i))
    val flatMapped: Stream[Nothing, Long] = stream.flatMap(i => Stream.unwrap(Async.succeed(Stream(i.toLong))))
    val recovered: Stream[String, Int]    =
      Stream.fail[String]("failure").catchAll(_ => Stream.unwrap(Async.succeed(Stream(1))))
    val defectRecovered: Stream[Nothing, Int] =
      stream.catchDefect { case _ => Stream.unwrap(Async.succeed(Stream(1))) }
    val alternative: Stream[String, Int] =
      Stream.fail[String]("failure").orElse(Stream.unwrap(Async.succeed(Stream(1))))
    val errorMapped: Stream[Long, Nothing] =
      Stream.fail[String]("failure").mapErrorAsync(e => Async.succeed(e.length.toLong))
    val finalized: Stream[Nothing, Int] = stream.ensuringAsync(Async.succeed(()))
    val pipelines                       = (
      Pipeline.mapAsync[Int, Long](i => Async.succeed(i.toLong)),
      Pipeline.filterAsync[Int](i => Async.succeed(i > 0)),
      Pipeline.collectAsync[Int, Long](i => Async.succeed(Some(i.toLong)))
    )
    AsyncApiExternalCallSite.check(
      mapped,
      filtered,
      collected,
      tapped,
      distinct,
      taken,
      accumulated,
      scanned,
      AsyncApiExternalCallSite.reader,
      AsyncApiExternalCallSite.stream,
      pipelines
    )
    val _ = (
      attempted,
      attemptedEval,
      evaluated,
      deferred,
      unwrapped,
      buffered,
      iterated,
      read,
      managed,
      flatMapped,
      recovered,
      defectRecovered,
      alternative,
      errorMapped,
      finalized
    )
    val genericLongReader = Stream.compileToReader(AsyncApiExternalCallSite.genericLong)
    val genericLongType   = genericLongReader.jvmType
    genericLongReader.asInstanceOf[zio.blocks.streams.io.Reader.SyncReader[Long]].close()
    assertTrue(AsyncApiExternalCallSite.genericLongTag == JvmType.Long, genericLongType == JvmType.Long)
  })
}
