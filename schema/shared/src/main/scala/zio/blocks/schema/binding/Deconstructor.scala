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

package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A `Deconstructor` writes the fields of a value of type `A` into
 * [[Registers]]: it is the deconstruction half of a [[Binding.Record]]. To
 * encode a record, a codec calls `deconstruct` and then reads each field from
 * the registers, at the positions that `Reflect.Record#registers` gives.
 * Writing the fields into registers, instead of into a tuple or an array of
 * boxed values, keeps primitive fields unboxed.
 *
 * `deconstruct` writes at positions counted from its `offset` argument, with
 * the same layout that the record's [[Constructor]] reads:
 *
 * {{{
 * import zio.blocks.schema.binding._
 * import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
 *
 * case class Book(title: String, year: Int)
 *
 * val bookDeconstructor: Deconstructor[Book] = new Deconstructor[Book] {
 *   def usedRegisters: RegisterOffset = RegisterOffset(objects = 1, ints = 1)
 *
 *   def deconstruct(out: Registers, offset: RegisterOffset, in: Book): Unit = {
 *     out.setObject(offset, in.title)
 *     out.setInt(offset + RegisterOffset(objects = 1), in.year)
 *   }
 * }
 *
 * val registers = Registers(bookDeconstructor.usedRegisters)
 * bookDeconstructor.deconstruct(registers, RegisterOffset.Zero, Book("Zionomicon", 2021))
 * val title = registers.getObject(RegisterOffset.Zero)     // "Zionomicon"
 * val year  = registers.getInt(RegisterOffset(objects = 1)) // 2021
 * }}}
 *
 * @tparam A
 *   the type of the values it takes apart
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/registers/ Register System]]
 */
abstract class Deconstructor[-A] { self =>

  /**
   * The registers that `deconstruct` writes, counted from its `offset`: the
   * number of object slots and the number of bytes. Callers size their
   * [[Registers]] with it.
   */
  def usedRegisters: RegisterOffset

  /**
   * Writes the fields of `in` into `out`, at positions counted from `offset`.
   */
  def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit
}

/**
 * A deconstructor that uses no registers and writes nothing, for types without
 * fields such as case objects.
 */
class ConstantDeconstructor[A] extends Deconstructor[A] {
  override def usedRegisters: RegisterOffset = 0L

  override def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {}
}
