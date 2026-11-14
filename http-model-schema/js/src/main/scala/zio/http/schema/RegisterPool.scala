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

package zio.http.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.Registers

/**
 * Per-codec pool of `Registers` for record encode/decode: one pooled instance,
 * borrowed for the whole operation and released in a `finally`, so the hot path
 * never allocates registers. JavaScript is single-threaded, so a plain field
 * suffices. A nested borrow of the same pool (only possible through
 * user-installed custom codecs that call back into this codec) gets a fresh,
 * unpooled instance instead of corrupting the in-flight one. Released instances
 * have their object slots cleared so a pooled instance never retains the
 * previous operation's objects.
 */
private[schema] final class RegisterPool(usedRegisters: RegisterOffset) {
  private[this] var cached: Registers = null
  private[this] var depth: Int        = 0

  def borrow(): Registers =
    if (depth != 0) Registers(usedRegisters)
    else {
      var registers = cached
      if (registers eq null) {
        registers = Registers(usedRegisters)
        cached = registers
      }
      depth = 1
      registers
    }

  def release(registers: Registers): Unit =
    if (registers eq cached) {
      registers.clearObjects(usedRegisters)
      depth = 0
    }
}
