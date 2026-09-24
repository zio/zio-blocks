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

import zio.blocks.async.Async
import zio.blocks.streams.io.Reader

trait SinkCompanionPlatformSpecific {

  /**
   * This dispatch is unreachable because Scala.js exposes no blocking stream
   * terminal.
   */
  private[streams] final def blockOnJvm[A](async: Async[A]): A =
    throw new IllegalStateException("an async-only sink cannot be synchronously drained on Scala.js")

  /** Unreachable because Scala.js exposes no blocking stream terminal. */
  private[streams] final def toSyncReader[A](reader: Reader[A]): Reader.SyncReader[A] =
    throw new IllegalStateException("an asynchronous reader cannot be synchronously drained on Scala.js")
}
