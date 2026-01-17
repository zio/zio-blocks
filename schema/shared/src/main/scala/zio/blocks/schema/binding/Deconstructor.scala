/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A `Deconstructor` is a typeclass that can deconstruct a value of type `A`
 * into a set of registers.
 */
abstract class Deconstructor[-A] { self =>

  /**
   * The size of the registers required to deconstruct a value of type `A`.
   */
  def usedRegisters: RegisterOffset

  /**
   * Deconstructs a value of type `A` into the registers.
   */
  def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit
}

class ConstantDeconstructor[A] extends Deconstructor[A] {
  override def usedRegisters: RegisterOffset = 0L

  override def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {}
}
