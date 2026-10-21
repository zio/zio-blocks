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

import scala.collection.immutable.ArraySeq
import zio.blocks.chunk.Chunk

/**
 * Reads a collection of type `C[A]` back as its elements and size: the
 * deconstruction half of a [[Binding.Seq]]. The instances for array-backed
 * collections are [[SeqDeconstructor.SpecializedIndexed]], which also read an
 * element by index; the JSON codec and the optics use that when they can.
 *
 * @tparam C
 *   the collection type constructor
 */
trait SeqDeconstructor[C[_]] {

  /** Returns an iterator over the elements of `c`, in its iteration order. */
  def deconstruct[A](c: C[A]): Iterator[A]

  /** Returns the number of elements of `c`. */
  def size[A](c: C[A]): Int
}

/** The sequence deconstructors for the standard collections. */
object SeqDeconstructor {

  /**
   * A deconstructor for an array-backed collection (`Array`, `ArraySeq` and, on
   * Scala 3 only, `IArray`) that also reads an element by index. `elementType`
   * tells which of the typed readers fits, and `index` must be in
   * `0 until size(c)`.
   */
  sealed trait SpecializedIndexed[C[_]] extends SeqDeconstructor[C] {

    /**
     * Returns the register type of the elements of `c`, from the runtime class
     * of its array: a primitive [[RegisterType]] for an array of `Boolean`,
     * `Byte`, `Short`, `Int`, `Long`, `Float`, `Double` or `Char`, and
     * `RegisterType.Object()` for any other array.
     */
    def elementType[A](c: C[A]): RegisterType[A]

    /** Returns the element of `c` at `index`. */
    def objectAt[A](c: C[A], index: Int): A

    /** Returns the `Boolean` element of `c` at `index`. */
    def booleanAt(c: C[Boolean], index: Int): Boolean

    /** Returns the `Byte` element of `c` at `index`. */
    def byteAt(c: C[Byte], index: Int): Byte

    /** Returns the `Short` element of `c` at `index`. */
    def shortAt(c: C[Short], index: Int): Short

    /** Returns the `Int` element of `c` at `index`. */
    def intAt(c: C[Int], index: Int): Int

    /** Returns the `Long` element of `c` at `index`. */
    def longAt(c: C[Long], index: Int): Long

    /** Returns the `Float` element of `c` at `index`. */
    def floatAt(c: C[Float], index: Int): Float

    /** Returns the `Double` element of `c` at `index`. */
    def doubleAt(c: C[Double], index: Int): Double

    /** Returns the `Char` element of `c` at `index`. */
    def charAt(c: C[Char], index: Int): Char
  }

  /** Reads an immutable `Set`. */
  given setDeconstructor: SeqDeconstructor[Set] = new SeqDeconstructor[Set] {
    def deconstruct[A](c: Set[A]): Iterator[A] = c.iterator

    def size[A](c: Set[A]): Int = c.size
  }

  /** Reads a `List`. */
  given listDeconstructor: SeqDeconstructor[List] = new SeqDeconstructor[List] {
    def deconstruct[A](c: List[A]): Iterator[A] = c.iterator

    def size[A](c: List[A]): Int = c.size
  }

  /** Reads a `Vector`. */
  given vectorDeconstructor: SeqDeconstructor[Vector] = new SeqDeconstructor[Vector] {
    def deconstruct[A](c: Vector[A]): Iterator[A] = c.iterator

    def size[A](c: Vector[A]): Int = c.length
  }

  /** Reads an immutable `ArraySeq`, including by index. */
  given arraySeqDeconstructor: SpecializedIndexed[ArraySeq] = new SpecializedIndexed[ArraySeq] {
    def deconstruct[A](c: ArraySeq[A]): Iterator[A] = c.iterator

    def elementType[A](c: ArraySeq[A]): RegisterType[A] = (c.unsafeArray match {
      case _: Array[Boolean] => RegisterType.Boolean
      case _: Array[Byte]    => RegisterType.Byte
      case _: Array[Short]   => RegisterType.Short
      case _: Array[Int]     => RegisterType.Int
      case _: Array[Long]    => RegisterType.Long
      case _: Array[Float]   => RegisterType.Float
      case _: Array[Double]  => RegisterType.Double
      case _: Array[Char]    => RegisterType.Char
      case _                 => RegisterType.Object()
    }).asInstanceOf[RegisterType[A]]

    def size[A](c: ArraySeq[A]): Int = c.unsafeArray.length

    def objectAt[A](c: ArraySeq[A], index: Int): A = c(index)

    def booleanAt(c: ArraySeq[Boolean], index: Int): Boolean = c(index)

    def byteAt(c: ArraySeq[Byte], index: Int): Byte = c(index)

    def shortAt(c: ArraySeq[Short], index: Int): Short = c(index)

    def intAt(c: ArraySeq[Int], index: Int): Int = c(index)

    def longAt(c: ArraySeq[Long], index: Int): Long = c(index)

    def floatAt(c: ArraySeq[Float], index: Int): Float = c(index)

    def doubleAt(c: ArraySeq[Double], index: Int): Double = c(index)

    def charAt(c: ArraySeq[Char], index: Int): Char = c(index)
  }

  /** Reads an immutable `IndexedSeq`. */
  given indexedSeqDeconstructor: SeqDeconstructor[IndexedSeq] = new SeqDeconstructor[IndexedSeq] {
    def deconstruct[A](c: IndexedSeq[A]): Iterator[A] = c.iterator

    def size[A](c: IndexedSeq[A]): Int = c.length
  }

  /** Reads an immutable `Seq`. */
  given seqDeconstructor: SeqDeconstructor[Seq] = new SeqDeconstructor[Seq] {
    def deconstruct[A](c: Seq[A]): Iterator[A] = c.iterator

    def size[A](c: Seq[A]): Int = c.length
  }

  /** Reads a `Chunk`. */
  given chunkDeconstructor: SeqDeconstructor[Chunk] = new SeqDeconstructor[Chunk] {
    def deconstruct[A](c: Chunk[A]): Iterator[A] = c.iterator

    def size[A](c: Chunk[A]): Int = c.length
  }

  /** Reads an `Array`, including by index. */
  implicit val arrayDeconstructor: SpecializedIndexed[Array] = new SpecializedIndexed[Array] {
    def deconstruct[A](c: Array[A]): Iterator[A] = c.iterator

    def elementType[A](c: Array[A]): RegisterType[A] = c match {
      case _: Array[Boolean] => RegisterType.Boolean
      case _: Array[Byte]    => RegisterType.Byte
      case _: Array[Short]   => RegisterType.Short
      case _: Array[Int]     => RegisterType.Int
      case _: Array[Long]    => RegisterType.Long
      case _: Array[Float]   => RegisterType.Float
      case _: Array[Double]  => RegisterType.Double
      case _: Array[Char]    => RegisterType.Char
      case _                 => RegisterType.Object().asInstanceOf[RegisterType[A]]
    }

    def size[A](c: Array[A]): Int = c.length

    def objectAt[A](c: Array[A], index: Int): A = c(index)

    def booleanAt(c: Array[Boolean], index: Int): Boolean = c(index)

    def byteAt(c: Array[Byte], index: Int): Byte = c(index)

    def shortAt(c: Array[Short], index: Int): Short = c(index)

    def intAt(c: Array[Int], index: Int): Int = c(index)

    def longAt(c: Array[Long], index: Int): Long = c(index)

    def floatAt(c: Array[Float], index: Int): Float = c(index)

    def doubleAt(c: Array[Double], index: Int): Double = c(index)

    def charAt(c: Array[Char], index: Int): Char = c(index)
  }

  /** Reads an `IArray`, including by index. Scala 3 only. */
  implicit val iArrayDeconstructor: SpecializedIndexed[IArray] = new SpecializedIndexed[IArray] {
    def deconstruct[A](c: IArray[A]): Iterator[A] = c.iterator

    def elementType[A](c: IArray[A]): RegisterType[A] = (c match {
      case _: Array[Boolean] => RegisterType.Boolean
      case _: Array[Byte]    => RegisterType.Byte
      case _: Array[Short]   => RegisterType.Short
      case _: Array[Int]     => RegisterType.Int
      case _: Array[Long]    => RegisterType.Long
      case _: Array[Float]   => RegisterType.Float
      case _: Array[Double]  => RegisterType.Double
      case _: Array[Char]    => RegisterType.Char
      case _                 => RegisterType.Object()
    }).asInstanceOf[RegisterType[A]]

    def size[A](c: IArray[A]): Int = c.length

    def objectAt[A](c: IArray[A], index: Int): A = c(index)

    def booleanAt(c: IArray[Boolean], index: Int): Boolean = c(index)

    def byteAt(c: IArray[Byte], index: Int): Byte = c(index)

    def shortAt(c: IArray[Short], index: Int): Short = c(index)

    def intAt(c: IArray[Int], index: Int): Int = c(index)

    def longAt(c: IArray[Long], index: Int): Long = c(index)

    def floatAt(c: IArray[Float], index: Int): Float = c(index)

    def doubleAt(c: IArray[Double], index: Int): Double = c(index)

    def charAt(c: IArray[Char], index: Int): Char = c(index)
  }
}
