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

trait SinkCompanionPlatformSpecific {

  /**
   * Creates a JVM sink whose blocking callback receives the run's synchronous
   * reader and decides how much input to consume and what result to produce.
   * Returning early leaves the remaining input unread; the enclosing stream run
   * closes that input rather than exposing it as reusable leftovers.
   *
   * The callback and reader are valid only for the run and must not be
   * retained. Reader failures preserve the stream's typed error; callback
   * exceptions are defects. At an asynchronous terminal, the callback runs on
   * the blocking executor and cancellation closes the reader.
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
