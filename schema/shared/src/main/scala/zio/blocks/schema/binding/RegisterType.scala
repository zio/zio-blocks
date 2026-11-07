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

/**
 * The kind of register that holds a value of type `A`: one case object for each
 * of `Unit`, `Char`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double` and
 * `Boolean`, and `Object` for reference types.
 * `SeqDeconstructor.SpecializedIndexed#elementType` returns one to say how the
 * elements of an array-backed collection are stored.
 *
 * @tparam A
 *   the type of the values
 */
sealed trait RegisterType[A]

/** The register kinds. */
object RegisterType {

  /** The register kind of `Unit` values. */
  case object Unit extends RegisterType[Unit]

  /** The register kind of `Char` values. */
  case object Char extends RegisterType[Char]

  /** The register kind of `Byte` values. */
  case object Byte extends RegisterType[Byte]

  /** The register kind of `Short` values. */
  case object Short extends RegisterType[Short]

  /** The register kind of `Int` values. */
  case object Int extends RegisterType[Int]

  /** The register kind of `Long` values. */
  case object Long extends RegisterType[Long]

  /** The register kind of `Float` values. */
  case object Float extends RegisterType[Float]

  /** The register kind of `Double` values. */
  case object Double extends RegisterType[Double]

  /** The register kind of `Boolean` values. */
  case object Boolean extends RegisterType[Boolean]

  /**
   * The register kind of reference values. `RegisterType.Object()` returns its
   * one instance.
   */
  sealed trait Object[A <: AnyRef] extends RegisterType[A]

  /**
   * Returns the `Object` register kind for the reference type `A`. Every call
   * returns the same instance, whose `toString` is `Object`.
   */
  def Object[A <: AnyRef](): Object[A] = _object.asInstanceOf[Object[A]]

  private[this] val _object = new Object[AnyRef] {
    override def toString: String = "Object"
  }
}
