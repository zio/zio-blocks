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

package zio.blocks.streams.io

/**
 * The platform-specific members of [[Reader.AsyncReader]]: `toSync` on the JVM,
 * none on Scala.js.
 */
private[io] trait AsyncReaderPlatform[+A] { self: Reader.AsyncReader[A] =>

  /**
   * Returns a blocking [[Reader.SyncReader]] view of this reader, for a JVM API
   * that expects one. Each pull runs the asynchronous operation and blocks the
   * calling thread until it completes; pulls from several threads run one at a
   * time. `close()` from another thread interrupts a pull in progress, which
   * then returns the end value.
   *
   * The view shares this reader's position and lifecycle: closing the view
   * closes this reader, and after that the view's control operations
   * (`reset()`, `skip`, `setLimit`, ...) throw `IOException`. On a reader made
   * by `SyncReader#toAsync`, it returns the original synchronous reader instead
   * of wrapping again.
   *
   * A known bug: when this reader is on the `AnyRef` lane, the view's primitive
   * pulls (`readInt`, `readLongs`, ...) throw `IllegalStateException`; `read`,
   * `readAll`, `readN` and `readUpToN` work.
   */
  final def toSync: Reader.SyncReader[A] = self match {
    case inverse: Reader.SyncToAsyncView[A @unchecked] => inverse.source
    case _                                             => new AsyncToSyncReader[A](self)
  }
}
