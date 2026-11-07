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

package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * Metadata attached to parts of a type `S`: a map from optics into `S` to
 * values of type `G[A]`, where `A` is the focus type of each optic, so the type
 * of a value follows the part it describes. An optic can have several values,
 * kept in the order they were added. Optics are compared with `==`, so two
 * optics built for the same path share their values.
 *
 * @tparam S
 *   the type whose parts the metadata describes
 * @tparam G
 *   the type of a value for a part of type `A`; [[SchemaMetadata.Id]] makes it
 *   `A` itself
 */
final case class SchemaMetadata[S, G[_]](private val map: Map[Optic[S, ?], IndexedSeq[?]]) {

  /**
   * Returns a copy with `value` added after the values `optic` already has.
   */
  def add[A](optic: Optic[S, A], value: G[A]): SchemaMetadata[S, G] =
    new SchemaMetadata[S, G](map.updated(optic, map.getOrElse(optic, Chunk.empty).appended(value)))

  /**
   * Folds over every value, starting from `z` and calling `fold.fold` with the
   * accumulator, the optic and the value. `Folder#initial` is not used. Optics
   * come in no particular order, and the values of one optic in the order they
   * were added.
   */
  def fold[Z](z: Z)(fold: SchemaMetadata.Folder[S, G, Z]): Z =
    map.foldLeft(z) { case (z, (optic, values)) =>
      values.foldLeft(z) { case (z, value) =>
        fold.fold(z, optic.asInstanceOf[Optic[S, Any]], value.asInstanceOf[G[Any]])
      }
    }

  /**
   * Returns the first value added for `optic`, if any.
   */
  def get[A](optic: Optic[S, A]): Option[G[A]] = getAll(optic).headOption

  /**
   * Returns the values of `optic`, in the order they were added.
   */
  def getAll[A](optic: Optic[S, A]): IndexedSeq[G[A]] = map.getOrElse(optic, Chunk.empty).asInstanceOf[IndexedSeq[G[A]]]

  /**
   * Returns a copy without the values of `optic`.
   */
  def removeAll[A](optic: Optic[S, A]): SchemaMetadata[S, G] = SchemaMetadata[S, G](map - optic)

  /**
   * Returns the number of optics that have values, not the number of values.
   */
  def size: Int = map.size
}

/**
 * Constructors and type aliases for [[SchemaMetadata]].
 */
object SchemaMetadata {

  /**
   * The function that `SchemaMetadata#fold` applies to each value. It is a
   * trait because it must work for every focus type `A`, which a function value
   * cannot.
   */
  trait Folder[S, G[_], Z] {

    /**
     * A starting accumulator for callers; `SchemaMetadata#fold` takes its own
     * and does not read this one.
     */
    def initial: Z

    /**
     * Combines the accumulator `z` with `value`, a value of `optic`.
     */
    def fold[A](z: Z, optic: Optic[S, A], value: G[A]): Z
  }

  /**
   * Type aliases and a constructor for [[Folder]].
   */
  object Folder {

    /**
     * Another name for [[Folder]].
     */
    type Bound[S, G[_], Z] = Folder[S, G, Z]

    /**
     * A folder over a [[SchemaMetadata.Simple]].
     */
    type Simple[S, Z] = Bound[S, Id, Z]

    /**
     * Returns a folder whose `initial` is `initial0` and that calls `f` with
     * the accumulator and the optic of each value, ignoring the value.
     */
    def simple[S, Z](initial0: Z)(f: (Z, Optic[S, ?]) => Z): Simple[S, Z] = new Simple[S, Z] {
      val initial: Z = initial0

      def fold[A](z: Z, optic: Optic[S, A], value: Id[A]): Z = f(z, optic)
    }
  }

  /**
   * The identity type: in a `SchemaMetadata[S, Id]`, each optic's values are of
   * its focus type.
   */
  type Id[A] = A

  /**
   * Another name for [[SchemaMetadata]].
   */
  type Bound[S, G[_]] = SchemaMetadata[S, G]

  /**
   * Metadata whose values have the focus type of their optic.
   */
  type Simple[S] = Bound[S, Id]

  /**
   * Returns empty metadata, the same as `empty`.
   */
  def bound[S, G[_]]: SchemaMetadata[S, G] = empty[S, G]

  /**
   * Returns metadata with no values.
   */
  def empty[S, G[_]]: SchemaMetadata[S, G] = new SchemaMetadata[S, G](Map.empty)

  /**
   * Returns empty [[SchemaMetadata.Simple]] metadata.
   */
  def simple[S]: SchemaMetadata.Simple[S] = empty[S, Id]
}
