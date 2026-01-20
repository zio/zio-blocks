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
 * A `Constructor` is a typeclass that can construct a value of type `A` from a
 * set of registers.
 */
abstract class Constructor[+A] { self =>

  /**
   * The size of the registers required to construct a value of type `A`.
   */
  def usedRegisters: RegisterOffset

  /**
   * Constructs a value of type `A` from the registers.
   */
  def construct(in: Registers, offset: RegisterOffset): A
}

class ConstantConstructor[A](constant: A) extends Constructor[A] {
  def usedRegisters: RegisterOffset = 0L

  def construct(in: Registers, offset: RegisterOffset): A = constant
}
