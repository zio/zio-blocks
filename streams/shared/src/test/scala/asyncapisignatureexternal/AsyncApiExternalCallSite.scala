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

package asyncapisignatureexternal

import zio.blocks.async.Async
import zio.blocks.streams.{JvmType, Pipeline, Stream}
import zio.blocks.streams.io.Reader

final case class Meter(value: Long) extends AnyVal

/**
 * Proves that implementation-shaped Sink evidence does not leak at an external
 * call site.
 */
object AsyncApiExternalCallSite {
  def check(
    mapped: Stream[Nothing, Long],
    filtered: Stream[Nothing, Int],
    collected: Stream[Nothing, String],
    tapped: Stream[Nothing, Int],
    distinct: Stream[Nothing, Int],
    taken: Stream[Nothing, Int],
    accumulated: Stream[Nothing, Long],
    scanned: Stream[Nothing, Long],
    unfoldedReader: Reader.AsyncReader[Int],
    unfoldedStream: Stream[Nothing, Int],
    pipelines: (Pipeline[Int, Long], Pipeline[Int, Int], Pipeline[Int, Long])
  ): Unit = {
    val _ = (
      mapped,
      filtered,
      collected,
      tapped,
      distinct,
      taken,
      accumulated,
      scanned,
      unfoldedReader,
      unfoldedStream,
      pipelines
    )
  }

  val reader: Reader.AsyncReader[Int] =
    Reader.unfoldAsync(0)(n => Async.succeed(if (n < 2) Some((n, n + 1)) else None))
  val stream: Stream[Nothing, Int] =
    Stream.unfoldAsync(0)(n => Async.succeed(if (n < 2) Some((n, n + 1)) else None))

  val inferredLong: Stream[Nothing, Long]    = Stream(1).map(_.toLong)
  val inferredUnit: Stream[Nothing, Unit]    = Stream(1).map(_ => ())
  val widenedAny: Stream[Nothing, Any]       = Stream(1).map[Any](_.toLong)
  val widenedAnyVal: Stream[Nothing, AnyVal] = Stream(1).map[AnyVal](_.toLong)
  val valueClass: Stream[Nothing, Meter]     = Stream(1).map(i => Meter(i.toLong))
  val bottom: Stream[Nothing, Nothing]       = Stream(1).map(_ => throw new IllegalStateException("not evaluated"))

  val legacyInferInt: JvmType.Infer[Int]       = JvmType.Infer.int
  val legacyInferLong: JvmType.Infer[Long]     = JvmType.Infer.long
  val explicitLegacyMap: Stream[Nothing, Long] =
    Stream(1).map(_.toLong)(legacyInferLong)

  val failedCharSource: Stream[String, Char] = Stream.fail("failed")
  val primitiveRecovery                      = failedCharSource.catchAll(_ => Stream.succeed(1L))

  def mapGeneric[A, B](source: Stream[Nothing, A])(f: A => B)(implicit
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ): Stream[Nothing, B] = source.map(f)

  val genericLong: Stream[Nothing, Long] = mapGeneric(Stream(1))(_.toLong)
  val genericLongTag: JvmType            = implicitly[JvmType.Infer[Long]].jvmType

  def explicitLegacyGeneric[A, B](source: Stream[Nothing, A])(f: A => B)(implicit
    jtA: JvmType.Infer[A],
    jtB: JvmType.Infer[B]
  ): Stream[Nothing, B] = {
    val _ = jtA
    source.map(f)(jtB)
  }
}
