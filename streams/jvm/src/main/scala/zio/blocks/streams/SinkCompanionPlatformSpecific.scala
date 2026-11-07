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

import zio.blocks.async._
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader

/**
 * The platform-specific members of the [[Sink]] companion. On the JVM it adds
 * `Sink.create`, whose callback reads a blocking reader. The Scala.js copy adds
 * no public member; cross-platform code uses `Sink.createAsync` or
 * `Sink.createBoth`.
 */
trait SinkCompanionPlatformSpecific {

  /**
   * Creates a sink from a callback that reads the run's blocking reader and
   * returns the result; JVM only. The callback decides how much input to read.
   * Input it leaves unread is closed with the run, not kept for later. The
   * reader is valid only during the callback and must not escape it.
   *
   * A typed stream error raised while reading stays the run's typed error; an
   * exception thrown by the callback is a defect. At an `*Async` terminal the
   * callback runs inline on the thread that drives the run, blocking it until
   * the callback returns, and cancellation closes the reader. To read a `Long`
   * or `Double` input without a sentinel that could equal real data, use the
   * bulk reads `readLongs` and `readDoubles`, whose returned count marks the
   * end.
   */
  def create[E, A, Z](f: Reader.SyncReader[A] => Z): Sink[E, A, Z] =
    new Sink[E, A, Z] {
      private[streams] def drain(reader: Reader.SyncReader[_]): Z =
        Sink.readerCallbackSync(reader.asInstanceOf[Reader.SyncReader[A]])(f)

      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Z] =
        Async
          .deferCancelableWithCleanup(
            () => Sink.readerCallbackSync(reader.toSync.asInstanceOf[Reader.SyncReader[A]])(f),
            () => reader.close()
          )
          .catchAll {
            case error: StreamError if error.isTrusted => Async.failTrusted(error)
            case cause                                 => Async.fail(cause)
          }
    }

  /**
   * Drives an async-only sink at an explicit JVM blocking terminal boundary.
   */
  private[streams] final def blockOnJvm[A](async: Async[A]): A = async.block

  /**
   * Normalizes a sealed reader at an explicit JVM blocking terminal boundary.
   */
  private[streams] final def toSyncReader[A](reader: Reader[A]): Reader.SyncReader[A] = reader match {
    case sync: Reader.SyncReader[A @unchecked]   => sync
    case async: Reader.AsyncReader[A @unchecked] => async.toSync
  }
}
