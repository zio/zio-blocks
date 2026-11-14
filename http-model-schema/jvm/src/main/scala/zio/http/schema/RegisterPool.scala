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
 * Per-codec pool of `Registers` for record encode/decode: one pooled instance
 * per thread, borrowed for the whole operation and released in a `finally`, so
 * the hot path never allocates registers. A nested borrow of the same pool on
 * the same thread (only possible through user-installed custom codecs that call
 * back into this codec) gets a fresh, unpooled instance instead of corrupting
 * the in-flight one. Released instances have their object slots cleared so a
 * pooled instance never retains the previous operation's objects.
 */
private[schema] final class RegisterPool(usedRegisters: RegisterOffset) {
  private[this] val local: ThreadLocal[Slot] = new ThreadLocal[Slot]()

  def borrow(): Registers = {
    var slot = local.get()
    if (slot eq null) {
      slot = new Slot(Registers(usedRegisters))
      local.set(slot)
    }
    if (slot.depth != 0) Registers(usedRegisters)
    else {
      slot.depth = 1
      slot.registers
    }
  }

  def release(registers: Registers): Unit = {
    val slot = local.get()
    if ((slot ne null) && (registers eq slot.registers)) {
      registers.clearObjects(usedRegisters)
      slot.depth = 0
    }
  }

  private final class Slot(val registers: Registers, var depth: Int) {
    def this(registers: Registers) = this(registers, 0)
  }
}
