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
 * A `Constructor` builds a value of type `A` from [[Registers]]: it is the
 * construction half of a [[Binding.Record]]. To decode a record, a codec writes
 * each field into registers, at the positions that `Reflect.Record#registers`
 * gives, and then calls `construct`. Reading the fields from registers, instead
 * of from a tuple or an array of boxed values, keeps primitive fields unboxed.
 *
 * `construct` reads at positions counted from its `offset` argument. A record
 * decoded on its own starts at `RegisterOffset.Zero`; the JSON codec keeps one
 * `Registers` as a stack and passes the start of the record's part of it. A
 * [[RegisterOffset]] counts the registers before a field, so `year` below,
 * which comes after one object field, is at `RegisterOffset(objects = 1)`,
 * whose byte position is 0:
 *
 * {{{
 * import zio.blocks.schema.binding._
 * import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
 *
 * case class Book(title: String, year: Int)
 *
 * val bookConstructor: Constructor[Book] = new Constructor[Book] {
 *   def usedRegisters: RegisterOffset = RegisterOffset(objects = 1, ints = 1)
 *
 *   def construct(in: Registers, offset: RegisterOffset): Book =
 *     Book(in.getObject(offset).asInstanceOf[String], in.getInt(offset + RegisterOffset(objects = 1)))
 * }
 *
 * val registers = Registers(bookConstructor.usedRegisters)
 * registers.setObject(RegisterOffset.Zero, "Zionomicon")
 * registers.setInt(RegisterOffset(objects = 1), 2021)
 * val book = bookConstructor.construct(registers, RegisterOffset.Zero) // Book("Zionomicon", 2021)
 * }}}
 *
 * @tparam A
 *   the type of the values it builds
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/registers/ Register System]]
 */
abstract class Constructor[+A] { self =>

  /**
   * The registers that `construct` reads, counted from its `offset`: the number
   * of object slots and the number of bytes. Callers size their [[Registers]]
   * with it.
   */
  def usedRegisters: RegisterOffset

  /**
   * Builds an `A` from the values in `in`, at positions counted from `offset`.
   */
  def construct(in: Registers, offset: RegisterOffset): A
}

/**
 * A constructor that uses no registers and always returns `constant`.
 * `Binding.of` uses it for case objects and for Scala 3 enum cases without
 * parameters.
 */
class ConstantConstructor[A](constant: A) extends Constructor[A] {
  def usedRegisters: RegisterOffset = 0L

  def construct(in: Registers, offset: RegisterOffset): A = constant
}
