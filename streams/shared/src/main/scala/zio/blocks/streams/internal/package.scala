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

package object internal extends InternalVersionSpecific {

  /**
   * An interpreter storage lane: 0 = `Int` (which also carries `Boolean`,
   * `Byte`, `Char` and `Short`), 1 = `Long`, 2 = `Float`, 3 = `Double`, 4 =
   * references.
   */
  type Lane = Int

  /**
   * An interpreter stage position packed into one `Long`: four 15-bit indices
   * and a 4-bit output lane, all 64 bits (see the `StreamState` object).
   */
  type StreamState = Long

  /**
   * An operation tag of the interpreters, from 0 to 138, kept in the low 8 bits
   * of an operation word (see the `OpTag` object).
   */
  type OpTag = Int

  /**
   * Internal sentinel object used instead of `null` to detect end-of-stream in
   * the AnyRef lane. Using a dedicated object instead of `null` allows streams
   * to contain `null` elements without them being confused with end-of-stream.
   */
  private[streams] val EndOfStream: AnyRef = new AnyRef {
    override def toString: String = "EndOfStream"
  }
}
