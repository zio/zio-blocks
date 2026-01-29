/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk

import java.nio._
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq, IndexedSeqOps, StrictOptimizedSeqOps}
import scala.collection.mutable.{ArrayBuilder, Builder}
import scala.collection.{IterableFactoryDefaults, IterableOnce, SeqFactory, StrictOptimizedSeqFactory, mutable}
import scala.language.implicitConversions
import scala.reflect.{ClassTag, classTag}
import scala.util.hashing.MurmurHash3
import scala.{
  Boolean => SBoolean,
  Byte => SByte,
  Char => SChar,
  Double => SDouble,
  Float => SFloat,
  Int => SInt,
  Long => SLong,
  Short => SShort
}

/**
 * `ChunkLike` represents the capability for a `Chunk` to extend Scala's
 * collection library. Because of changes to Scala's collection library in 2.13,
 * separate versions of this trait are implemented for 2.12 and 2.13 / Dotty.
 * This allows code in `Chunk` to be written without concern for the
 * implementation details of Scala's collection library to the maximum extent
 * possible.
 *
 * Note that `IndexedSeq` is not a referentially transparent interface in that
 * it exposes methods that are partial (e.g. `apply`), allocate mutable state
 * (e.g. `iterator`), or are purely side-effecting (e.g. `foreach`). `ChunkLike`
 * extends `IndexedSeq` to provide interoperability with Scala's collection
 * library, but users should avoid these methods whenever possible.
 */
trait ChunkLike[+A]
    extends IndexedSeq[A]
    with IndexedSeqOps[A, Chunk, Chunk[A]]
    with StrictOptimizedSeqOps[A, Chunk, Chunk[A]]
    with IterableFactoryDefaults[A, Chunk] { self: Chunk[A] =>

  override final def appended[A1 >: A](a1: A1): Chunk[A1] = append(a1)

  override final def prepended[A1 >: A](a1: A1): Chunk[A1] = prepend(a1)

  /**
   * Returns a filtered, mapped subset of the elements of this `Chunk`.
   */
  override def collect[B](pf: PartialFunction[A, B]): Chunk[B] = collectChunk(pf)

  override def copyToArray[B >: A](dest: Array[B], destPos: Int, length: Int): Int = {
    val n = Math.max(Math.min(Math.min(length, self.length), dest.length - destPos), 0)
    if (n > 0) toArray(0, dest, destPos, n)
    n
  }

  /**
   * Returns the concatenation of mapping every element into a new chunk using
   * the specified function.
   */
  override final def flatMap[B](f: A => IterableOnce[B]): Chunk[B] = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    val builder  = ChunkBuilder.make[B]()
    while (idx < len) {
      builder.addAll(f(iterator.nextAt(idx)))
      idx += 1
    }
    builder.result()
  }

  /**
   * Flattens a chunk of chunks into a single chunk by concatenating all chunks.
   */
  override def flatten[B](implicit ev: A => IterableOnce[B]): Chunk[B] = flatMap(ev(_))

  /**
   * Returns a `SeqFactory` that can construct `Chunk` values. The `SeqFactory`
   * exposes a `newBuilder` method that is not referentially transparent because
   * it allocates mutable state.
   */
  override def iterableFactory: SeqFactory[Chunk] = Chunk

  /**
   * Returns a chunk with the elements mapped by the specified function.
   */
  override final def map[B](f: A => B): Chunk[B] = mapChunk(f)

  override def sorted[A1 >: A](implicit ord: Ordering[A1]): Chunk[A] = {
    implicit val classTag = Chunk.classTagOf(self)
    val array             = self.toArray
    (ord, classTag) match {
      case (Ordering.Byte, ClassTag.Byte) => java.util.Arrays.sort(array.asInstanceOf[Array[Byte]])
      case (Ordering.Char, ClassTag.Char) => java.util.Arrays.sort(array.asInstanceOf[Array[Char]])
      case (
            Ordering.Double.IeeeOrdering | Ordering.Double.TotalOrdering | Ordering.DeprecatedDoubleOrdering,
            ClassTag.Double
          ) =>
        java.util.Arrays.sort(array.asInstanceOf[Array[Double]])
      case (
            Ordering.Float.IeeeOrdering | Ordering.Float.TotalOrdering | Ordering.DeprecatedFloatOrdering,
            ClassTag.Float
          ) =>
        java.util.Arrays.sort(array.asInstanceOf[Array[Float]])
      case (Ordering.Int, ClassTag.Int)     => java.util.Arrays.sort(array.asInstanceOf[Array[Int]])
      case (Ordering.Long, ClassTag.Long)   => java.util.Arrays.sort(array.asInstanceOf[Array[Long]])
      case (Ordering.Short, ClassTag.Short) => java.util.Arrays.sort(array.asInstanceOf[Array[Short]])
      case _                                => java.util.Arrays.sort(array.asInstanceOf[Array[AnyRef]], ord.asInstanceOf[Ordering[AnyRef]])
    }
    Chunk.fromArray(array).asInstanceOf[Chunk[A]]
  }

  override final def updated[A1 >: A](index: Int, elem: A1): Chunk[A1] = update(index, elem)

  /**
   * Zips this chunk with the index of every element.
   */
  override final def zipWithIndex: Chunk[(A, Int)] = zipWithIndexFrom(0)
}

/**
 * A `ChunkBuilder[A]` can build a `Chunk[A]` given elements of type `A`.
 * `ChunkBuilder` is a mutable data structure that is implemented to efficiently
 * build chunks of unboxed primitives and for compatibility with the Scala
 * collection library.
 */
sealed abstract class ChunkBuilder[A] extends Builder[A, Chunk[A]]

object ChunkBuilder {

  /**
   * Constructs a generic `ChunkBuilder`.
   */
  def make[A](): ChunkBuilder[A] = make(4)

  /**
   * Constructs a generic `ChunkBuilder` with the size hint.
   */
  def make[A](capacityHint: SInt): ChunkBuilder[A] = new ChunkBuilder[A] {
    private[this] var array: Array[A] = null
    private[this] var count: SInt     = 0

    def addOne(a: A): this.type = {
      if (array eq null) {
        implicit val ct: ClassTag[A] = Chunk.Tags.fromValue(a)
        array = new Array[A](Math.max(capacityHint, 1))
      } else if (count == array.length) array = Array.copyOf(array, array.length << 1)
      try array(count) = a
      catch {
        case _: ClassCastException =>
          val newArray = new Array[AnyRef](array.length).asInstanceOf[Array[A]]
          array.copyToArray(newArray, 0, count)
          array = newArray
          array(count) = a
      }
      count += 1
      this
    }

    def clear(): Unit = count = 0

    def result(): Chunk[A] =
      if (array eq null) Chunk.empty
      else {
        Chunk.fromArray(
          if (count == array.length) array
          else Array.copyOf(array, count)
        )
      }

    override def sizeHint(n: SInt): Unit =
      if (array eq null) {
        implicit val ct: ClassTag[A] = ClassTag.AnyRef.asInstanceOf[ClassTag[A]]
        array = new Array[A](Math.max(n, 1))
      } else array = Array.copyOf(array, Math.max(n, count))

    override def knownSize: SInt = count

    override def toString: String = "ChunkBuilder"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Boolean`
   * values.
   */
  final class Boolean extends ChunkBuilder[SBoolean] { self =>
    private val arrayBuilder: ArrayBuilder.ofByte = new ArrayBuilder.ofByte
    private var lastByte: SByte                   = 0.toByte
    private var maxBitIndex: SInt                 = 0

    override def addAll(as: IterableOnce[SBoolean]): this.type = {
      as.iterator.foreach(addOne _)
      this
    }

    def addOne(b: SBoolean): this.type = {
      if (b) {
        if (maxBitIndex == 8) {
          arrayBuilder.addOne(lastByte)
          lastByte = (1 << 7).toByte
          maxBitIndex = 1
        } else {
          val bitIndex = 7 - maxBitIndex
          lastByte = (lastByte | (1 << bitIndex)).toByte
          maxBitIndex = maxBitIndex + 1
        }
      } else {
        if (maxBitIndex == 8) {
          arrayBuilder.addOne(lastByte)
          lastByte = 0.toByte
          maxBitIndex = 1
        } else maxBitIndex = maxBitIndex + 1
      }
      this
    }

    def clear(): Unit = {
      arrayBuilder.clear()
      maxBitIndex = 0
      lastByte = 0.toByte
    }

    override def equals(that: Any): SBoolean = that match {
      case that: Boolean =>
        self.arrayBuilder.equals(that.arrayBuilder) &&
        self.maxBitIndex == that.maxBitIndex &&
        self.lastByte == that.lastByte
      case _ => false
    }

    def result(): Chunk[SBoolean] = {
      arrayBuilder.addOne(lastByte)
      val bytes: Chunk[SByte] = Chunk.fromArray(arrayBuilder.result())
      Chunk.BitChunkByte(bytes, 0, 8 * (bytes.length - 1) + maxBitIndex)
    }

    override def sizeHint(n: SInt): Unit = {
      val hint =
        if (n == 0) 0
        else n / 8 + 1
      arrayBuilder.sizeHint(hint)
    }

    override def toString: String = "ChunkBuilder.Boolean"

    override def knownSize: SInt = arrayBuilder.knownSize * 8 + maxBitIndex
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Byte` values.
   */
  final class Byte extends ChunkBuilder[SByte] { self =>
    private val arrayBuilder: ArrayBuilder.ofByte = new ArrayBuilder.ofByte

    override def addAll(as: IterableOnce[SByte]): this.type = {
      arrayBuilder.addAll(as)
      this
    }

    def addOne(a: SByte): this.type = {
      arrayBuilder.addOne(a)
      this
    }

    def clear(): Unit = arrayBuilder.clear()

    override def equals(that: Any): SBoolean = that match {
      case that: Byte => self.arrayBuilder == that.arrayBuilder
      case _          => false
    }

    def result(): Chunk[SByte] = Chunk.fromArray(arrayBuilder.result())

    override def sizeHint(n: SInt): Unit = arrayBuilder.sizeHint(n)

    override def toString: String = "ChunkBuilder.Byte"

    override def knownSize: SInt = arrayBuilder.knownSize
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Char` values.
   */
  final class Char extends ChunkBuilder[SChar] { self =>
    private val arrayBuilder: ArrayBuilder.ofChar = new ArrayBuilder.ofChar

    override def addAll(as: IterableOnce[SChar]): this.type = {
      arrayBuilder.addAll(as)
      this
    }

    def addOne(a: SChar): this.type = {
      arrayBuilder.addOne(a)
      this
    }

    def clear(): Unit = arrayBuilder.clear()

    override def equals(that: Any): SBoolean = that match {
      case that: Char => self.arrayBuilder == that.arrayBuilder
      case _          => false
    }

    def result(): Chunk[SChar] = Chunk.fromArray(arrayBuilder.result())

    override def sizeHint(n: SInt): Unit = arrayBuilder.sizeHint(n)

    override def toString: String = "ChunkBuilder.Char"

    override def knownSize: SInt = arrayBuilder.knownSize
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Double`
   * values.
   */
  final class Double extends ChunkBuilder[SDouble] { self =>
    private val arrayBuilder: ArrayBuilder.ofDouble = new ArrayBuilder.ofDouble

    override def addAll(as: IterableOnce[SDouble]): this.type = {
      arrayBuilder.addAll(as)
      this
    }

    def addOne(a: SDouble): this.type = {
      arrayBuilder.addOne(a)
      this
    }

    def clear(): Unit = arrayBuilder.clear()

    override def equals(that: Any): SBoolean = that match {
      case that: Double => self.arrayBuilder == that.arrayBuilder
      case _            => false
    }

    def result(): Chunk[SDouble] = Chunk.fromArray(arrayBuilder.result())

    override def sizeHint(n: SInt): Unit = arrayBuilder.sizeHint(n)

    override def toString: String = "ChunkBuilder.Double"

    override def knownSize: SInt = arrayBuilder.knownSize
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Float` values.
   */
  final class Float extends ChunkBuilder[SFloat] { self =>
    private val arrayBuilder: ArrayBuilder.ofFloat = new ArrayBuilder.ofFloat

    override def addAll(as: IterableOnce[SFloat]): this.type = {
      arrayBuilder.addAll(as)
      this
    }

    def addOne(a: SFloat): this.type = {
      arrayBuilder.addOne(a)
      this
    }

    def clear(): Unit = arrayBuilder.clear()

    override def equals(that: Any): SBoolean = that match {
      case that: Float => self.arrayBuilder == that.arrayBuilder
      case _           => false
    }

    def result(): Chunk[SFloat] = Chunk.fromArray(arrayBuilder.result())

    override def sizeHint(n: SInt): Unit = arrayBuilder.sizeHint(n)

    override def toString: String = "ChunkBuilder.Float"

    override def knownSize: SInt = arrayBuilder.knownSize
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Int` values.
   */
  final class Int extends ChunkBuilder[SInt] { self =>
    private val arrayBuilder: ArrayBuilder.ofInt = new ArrayBuilder.ofInt

    override def addAll(as: IterableOnce[SInt]): this.type = {
      arrayBuilder.addAll(as)
      this
    }

    def addOne(a: SInt): this.type = {
      arrayBuilder.addOne(a)
      this
    }

    def clear(): Unit = arrayBuilder.clear()

    override def equals(that: Any): SBoolean = that match {
      case that: Int => self.arrayBuilder == that.arrayBuilder
      case _         => false
    }

    def result(): Chunk[SInt] = Chunk.fromArray(arrayBuilder.result())

    override def sizeHint(n: SInt): Unit = arrayBuilder.sizeHint(n)

    override def toString: String = "ChunkBuilder.Int"

    override def knownSize: SInt = arrayBuilder.knownSize
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Long` values.
   */
  final class Long extends ChunkBuilder[SLong] { self =>
    private val arrayBuilder: ArrayBuilder.ofLong = new ArrayBuilder.ofLong

    override def addAll(as: IterableOnce[SLong]): this.type = {
      arrayBuilder.addAll(as)
      this
    }

    def addOne(a: SLong): this.type = {
      arrayBuilder.addOne(a)
      this
    }

    def clear(): Unit = arrayBuilder.clear()

    override def equals(that: Any): SBoolean =
      that match {
        case that: Long => self.arrayBuilder == that.arrayBuilder
        case _          => false
      }

    def result(): Chunk[SLong] = Chunk.fromArray(arrayBuilder.result())

    override def sizeHint(n: SInt): Unit = arrayBuilder.sizeHint(n)

    override def toString: String = "ChunkBuilder.Long"

    override def knownSize: SInt = arrayBuilder.knownSize
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Short` values.
   */
  final class Short extends ChunkBuilder[SShort] { self =>
    private val arrayBuilder: ArrayBuilder.ofShort = new ArrayBuilder.ofShort

    override def addAll(as: IterableOnce[SShort]): this.type = {
      arrayBuilder.addAll(as)
      this
    }

    def addOne(a: SShort): this.type = {
      arrayBuilder.addOne(a)
      this
    }

    def clear(): Unit = arrayBuilder.clear()

    override def equals(that: Any): SBoolean = that match {
      case that: Short => self.arrayBuilder == that.arrayBuilder
      case _           => false
    }

    def result(): Chunk[SShort] = Chunk.fromArray(arrayBuilder.result())

    override def sizeHint(n: SInt): Unit = arrayBuilder.sizeHint(n)

    override def toString: String = "ChunkBuilder.Short"

    override def knownSize: SInt = arrayBuilder.knownSize
  }
}

private[chunk] trait ChunkFactory extends StrictOptimizedSeqFactory[Chunk] {
  final def from[A](source: IterableOnce[A]): Chunk[A] = source match {
    case iterable: Iterable[A] => Chunk.fromIterable(iterable)
    case iterableOnce          =>
      val chunkBuilder = ChunkBuilder.make[A]()
      iterableOnce.iterator.foreach(chunkBuilder.addOne)
      chunkBuilder.result()
  }

  final protected def fromArraySeq[A](seq: mutable.ArraySeq[A]): Chunk[A] = {
    val arr = seq.array
    Chunk.fromArray(Array.copyAs(arr, arr.length)(seq.elemTag)).asInstanceOf[Chunk[A]]
  }
}

/**
 * A `Chunk[A]` represents a chunk of values of type `A`. Chunks are usually
 * backed by arrays, but expose a purely functional, safe interface to the
 * underlying elements, and they become lazy on operations that would be costly
 * with arrays, such as repeated concatenation.
 *
 * The implementation of balanced concatenation is based on the one for
 * Conc-Trees in "Conc-Trees for Functional and Parallel Programming" by
 * Aleksandar Prokopec and Martin Odersky.
 * [[http://aleksandar-prokopec.com/resources/docs/lcpc-conc-trees.pdf]]
 *
 * NOTE: For performance reasons `Chunk` does not box primitive types. As a
 * result, it is not safe to construct chunks from heterogeneous primitive
 * types.
 */
sealed abstract class Chunk[+A] extends ChunkLike[A] with Serializable { self =>
  def chunkIterator: Chunk.ChunkIterator[A]

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  final def ++[A1 >: A](that: Chunk[A1]): Chunk[A1] =
    (self, that) match {
      case (Chunk.AppendN(start, buffer, bufferUsed, _), that) =>
        val chunk = Chunk.fromArray(buffer.asInstanceOf[Array[A1]]).take(bufferUsed)
        start ++ chunk ++ that
      case (self, Chunk.PrependN(end, buffer, bufferUsed, _)) =>
        val chunk = Chunk.fromArray(buffer.asInstanceOf[Array[A1]]).takeRight(bufferUsed)
        self ++ chunk ++ end
      case (self, Chunk.Empty) => self
      case (Chunk.Empty, that) => that
      case (self, that)        =>
        val diff = that.concatDepth - self.concatDepth
        if (Math.abs(diff) <= 1) new Chunk.Concat(self, that)
        else if (diff < -1) {
          if (self.left.concatDepth >= self.right.concatDepth) {
            val nr = self.right ++ that
            new Chunk.Concat(self.left, nr)
          } else {
            val nrr = self.right.right ++ that
            if (nrr.concatDepth == self.concatDepth - 3) {
              new Chunk.Concat(self.left, new Chunk.Concat(self.right.left, nrr))
            } else new Chunk.Concat(new Chunk.Concat(self.left, self.right.left), nrr)
          }
        } else {
          if (that.right.concatDepth >= that.left.concatDepth) {
            val nl = self ++ that.left
            new Chunk.Concat(nl, that.right)
          } else {
            val nll = self ++ that.left.left
            if (nll.concatDepth == that.concatDepth - 3) {
              new Chunk.Concat(new Chunk.Concat(nll, that.left.right), that.right)
            } else new Chunk.Concat(nll, new Chunk.Concat(that.left.right, that.right))
          }
        }
    }

  /**
   * Returns the concatenation of this chunk with the specified chunk.
   */
  final def ++[A1 >: A](that: NonEmptyChunk[A1]): NonEmptyChunk[A1] = that.prepend(self)

  /**
   * Returns the bitwise AND of this chunk and the specified chunk.
   */
  def &(that: Chunk[Boolean])(implicit ev: A <:< Boolean): Chunk.BitChunkByte =
    Chunk.bitwise(self.asInstanceOf[Chunk[Boolean]], that, _ & _)

  /**
   * Returns the bitwise OR of this chunk and the specified chunk.
   */
  def |(that: Chunk[Boolean])(implicit ev: A <:< Boolean): Chunk.BitChunkByte =
    Chunk.bitwise(self.asInstanceOf[Chunk[Boolean]], that, _ | _)

  /**
   * Returns the bitwise XOR of this chunk and the specified chunk.
   */
  def ^(that: Chunk[Boolean])(implicit ev: A <:< Boolean): Chunk.BitChunkByte =
    Chunk.bitwise(self.asInstanceOf[Chunk[Boolean]], that, _ ^ _)

  /**
   * Returns the bitwise NOT of this chunk.
   */
  def negate(implicit ev: A <:< Boolean): Chunk.BitChunkByte = {
    val bits      = self.length
    val fullBytes = bits >> 3
    val remBytes  = bits & 7
    val arr       = new Array[Byte](fullBytes + (if (remBytes == 0) 0 else 1))
    var idx       = 0
    var mask      = 128
    while (idx < fullBytes) {
      var byte = 0
      mask = 128
      var bitIdx = 0
      while (bitIdx < 8) {
        byte = byte | (if (!ev(self(idx * 8 + bitIdx))) mask else 0)
        mask >>= 1
        bitIdx += 1
      }
      arr(idx) = byte.asInstanceOf[Byte]
      idx += 1
    }
    if (remBytes != 0) {
      var byte = 0
      mask = 128
      var bitIdx = 0
      while (bitIdx < remBytes) {
        byte = byte | (if (!ev(self(fullBytes * 8 + bitIdx))) mask else 0)
        mask >>= 1
        bitIdx += 1
      }
      arr(fullBytes) = byte.asInstanceOf[Byte]
    }
    new Chunk.BitChunkByte(Chunk.fromArray(arr), 0, bits)
  }

  /**
   * Creates a base64 encoded string based on the chunk's data.
   */
  def asBase64String(implicit ev: Chunk.IsText[A]): String =
    java.util.Base64.getEncoder.encodeToString(
      if (ev eq Chunk.IsText.byteIsText) self.asInstanceOf[Chunk[Byte]].toArray
      else if (ev eq Chunk.IsText.charIsText) self.asInstanceOf[Chunk[Char]].toArray.map(_.toByte)
      else ev.convert(self).getBytes
    )

  /**
   * Converts a chunk of ints to a chunk of bits.
   */
  final def asBitsInt(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Int): Chunk[Boolean] =
    if (self.isEmpty) Chunk.empty
    else new Chunk.BitChunkInt(self.asInstanceOf[Chunk[Int]], endianness, 0, length << 5)

  /**
   * Converts a chunk of longs to a chunk of bits.
   */
  final def asBitsLong(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Long): Chunk[Boolean] =
    if (self.isEmpty) Chunk.empty
    else new Chunk.BitChunkLong(self.asInstanceOf[Chunk[Long]], endianness, 0, length << 6)

  /**
   * Converts a chunk of bytes to a chunk of bits.
   */
  final def asBitsByte(implicit ev: A <:< Byte): Chunk[Boolean] =
    if (self.isEmpty) Chunk.empty
    else new Chunk.BitChunkByte(self.map(ev), 0, length << 3)

  def toPackedByte(implicit ev: A <:< Boolean): Chunk[Byte] =
    if (self.isEmpty) Chunk.empty
    else new Chunk.ChunkPackedBoolean[Byte](self.asInstanceOf[Chunk[Boolean]], 8, Chunk.BitChunk.Endianness.BigEndian)

  def toPackedInt(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Boolean): Chunk[Int] =
    if (self.isEmpty) Chunk.empty
    else new Chunk.ChunkPackedBoolean[Int](self.asInstanceOf[Chunk[Boolean]], 32, endianness)

  def toPackedLong(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Boolean): Chunk[Long] =
    if (self.isEmpty) Chunk.empty
    else new Chunk.ChunkPackedBoolean[Long](self.asInstanceOf[Chunk[Boolean]], 64, endianness)

  /**
   * Creates a new String based on this chunk's data.
   */
  final def asString(implicit ev: Chunk.IsText[A]): String = ev.convert(self)

  /**
   * Creates a new String based on this chunk of bytes and using the given
   * charset.
   */
  final def asString(charset: Charset)(implicit ev: A <:< Byte): String = {
    implicit val ct: ClassTag[A] = classTag[Byte].asInstanceOf[ClassTag[A]]
    new String(self.toArray.asInstanceOf[Array[Byte]], charset)
  }

  /**
   * Get the element at the specified index.
   */
  def boolean(index: Int)(implicit ev: A <:< Boolean): Boolean = ev(apply(index))

  /**
   * Get the element at the specified index.
   */
  def byte(index: Int)(implicit ev: A <:< Byte): Byte = ev(apply(index))

  /**
   * Get the element at the specified index.
   */
  def char(index: Int)(implicit ev: A <:< Char): Char = ev(apply(index))

  /**
   * Transforms all elements of the chunk for as long as the specified partial
   * function is defined.
   */
  def collectWhile[B](pf: PartialFunction[A, B]): Chunk[B] =
    if (isEmpty) Chunk.empty
    else self.materialize.collectWhile(pf)

  /**
   * Determines whether this chunk and the specified chunk have the same length
   * and every pair of corresponding elements of this chunk and the specified
   * chunk satisfy the specified predicate.
   */
  final def corresponds[B](that: Chunk[B])(f: (A, B) => Boolean): Boolean = {
    val len = self.length
    if (len != that.length) false
    else {
      val leftIterator  = self.chunkIterator
      val rightIterator = that.chunkIterator
      var idx           = 0
      while (idx < len) {
        if (!f(leftIterator.nextAt(idx), rightIterator.nextAt(idx))) return false
        idx += 1
      }
      true
    }
  }

  /**
   * Deduplicates adjacent elements that are identical.
   */
  def dedupe: Chunk[A] = {
    var lastA    = null.asInstanceOf[A]
    val iterator = self.chunkIterator
    val len      = iterator.length
    val builder  = ChunkBuilder.make[A](len)
    var idx      = 0
    while (idx < len) {
      val a = iterator.nextAt(idx)
      if (a != lastA) builder.addOne(a)
      lastA = a
      idx += 1
    }
    builder.result()
  }

  /**
   * Get the element at the specified index.
   */
  def double(index: Int)(implicit ev: A <:< Double): Double = ev(apply(index))

  /**
   * Drops the first `n` elements of the chunk.
   */
  override def drop(n: Int): Chunk[A] = {
    val len = self.length
    if (n <= 0) self
    else if (n >= len) Chunk.empty
    else {
      self match {
        case Chunk.Slice(c, o, l) => new Chunk.Slice(c, o + n, l - n)
        case Chunk.Concat(l, r)   =>
          if (n > l.length) r.drop(n - l.length)
          else new Chunk.Concat(l.drop(n), r)
        case _ =>
          new Chunk.Slice(
            {
              if (depth >= Chunk.MaxDepthBeforeMaterialize) self.materialize
              else self
            },
            n,
            len - n
          )
      }
    }
  }

  /**
   * Drops the last `n` elements of the chunk.
   */
  override def dropRight(n: Int): Chunk[A] = {
    val len = self.length
    if (n <= 0) self
    else if (n >= len) Chunk.empty
    else {
      self match {
        case Chunk.Slice(c, o, l) => new Chunk.Slice(c, o, l - n)
        case Chunk.Concat(l, r)   =>
          if (n > r.length) l.dropRight(n - r.length)
          else new Chunk.Concat(l, r.dropRight(n))
        case _ =>
          new Chunk.Slice(
            {
              if (depth >= Chunk.MaxDepthBeforeMaterialize) self.materialize
              else self
            },
            0,
            len - n
          )
      }
    }
  }

  /**
   * Drops all elements until the predicate returns true.
   */
  def dropUntil(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var continue = true
    var idx      = 0
    while (continue && idx < len) {
      if (f(iterator.nextAt(idx))) continue = false
      idx += 1
    }
    drop(idx)
  }

  /**
   * Drops all elements so long as the predicate returns true.
   */
  override def dropWhile(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    var continue = true
    while (continue && idx < len) {
      if (f(iterator.nextAt(idx))) idx += 1
      else continue = false
    }
    drop(idx)
  }

  override def equals(that: Any): Boolean =
    (self eq that.asInstanceOf[AnyRef]) || (that match {
      case that: Seq[_] => self.corresponds(that)(_ == _)
      case _            => false
    })

  /**
   * Determines whether a predicate is satisfied for at least one element of
   * this chunk.
   */
  override final def exists(f: A => Boolean): Boolean = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    while (idx < len) {
      if (f(iterator.nextAt(idx))) return true
      idx += 1
    }
    false
  }

  /**
   * Returns a filtered subset of this chunk.
   */
  override def filter(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    val builder  = ChunkBuilder.make[A](len)
    var idx      = 0
    while (idx < len) {
      val a = iterator.nextAt(idx)
      if (f(a)) builder.addOne(a)
      idx += 1
    }
    builder.result()
  }

  /**
   * Returns the first element that satisfies the predicate.
   */
  override final def find(f: A => Boolean): Option[A] = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    while (idx < len) {
      val a = iterator.nextAt(idx)
      if (f(a)) return new Some(a)
      idx += 1
    }
    None
  }

  /**
   * Get the element at the specified index.
   */
  def float(index: Int)(implicit ev: A <:< Float): Float = ev(apply(index))

  /**
   * Folds over the elements in this chunk from the left.
   */
  override def foldLeft[S](s0: S)(f: (S, A) => S): S = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    var s        = s0
    while (idx < len) {
      s = f(s, iterator.nextAt(idx))
      idx += 1
    }
    s
  }

  /**
   * Folds over the elements in this chunk from the right.
   */
  override def foldRight[S](s0: S)(f: (A, S) => S): S = {
    val iterator = self.reverseIterator
    var s        = s0
    while (iterator.hasNext) {
      s = f(iterator.next(), s)
    }
    s
  }

  /**
   * Folds over the elements in this chunk from the left. Stops the fold early
   * when the condition is not fulfilled.
   */
  final def foldWhile[S](s0: S)(pred: S => Boolean)(f: (S, A) => S): S = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    var s        = s0
    while (pred(s) && idx < len) {
      s = f(s, iterator.nextAt(idx))
      idx += 1
    }
    s
  }

  /**
   * Determines whether a predicate is satisfied for all elements of this chunk.
   */
  override final def forall(f: A => Boolean): Boolean = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    while (idx < len) {
      if (!f(iterator.nextAt(idx))) return false
      idx += 1
    }
    true
  }

  override final def hashCode: Int = toArrayOrNull match {
    case null  => Vector.empty[AnyRef].hashCode()
    case array => MurmurHash3.arrayHash(array, MurmurHash3.seqSeed)
  }

  /**
   * Returns the first element of this chunk. Note that this method is partial
   * in that it will throw an exception if the chunk is empty. Consider using
   * `headOption` to explicitly handle the possibility that the chunk is empty
   * or iterating over the elements of the chunk in lower level, performance
   * sensitive code unless you really only need the first element of the chunk.
   */
  override def head: A = self(0)

  /**
   * Returns the first element of this chunk if it exists.
   */
  override final def headOption: Option[A] =
    if (length == 0) None
    else new Some(self(0))

  /**
   * Returns the first index for which the given predicate is satisfied after or
   * at some given index.
   */
  override final def indexWhere(f: A => Boolean, from: Int): Int = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = Math.max(from, 0)
    while (idx < len) {
      if (f(iterator.nextAt(idx))) return idx
      idx += 1
    }
    -1
  }

  /**
   * Get the element at the specified index.
   */
  def int(index: Int)(implicit ev: A <:< Int): Int = ev(apply(index))

  /**
   * Determines if the chunk is empty.
   */
  override final def isEmpty: Boolean = length == 0

  /**
   * Returns the last element of this chunk if it exists.
   */
  override final def lastOption: Option[A] = {
    val len = self.length
    if (len == 0) None
    else new Some(self(len - 1))
  }

  /**
   * Get the element at the specified index.
   */
  def long(index: Int)(implicit ev: A <:< Long): Long = ev(apply(index))

  /**
   * Statefully maps over the chunk, producing new elements of type `B`.
   */
  final def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): (S1, Chunk[B]) = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    val builder  = ChunkBuilder.make[B](len)
    var s        = s1
    var idx      = 0
    while (idx < len) {
      val tuple = f1(s, iterator.nextAt(idx))
      s = tuple._1
      builder.addOne(tuple._2)
      idx += 1
    }
    (s, builder.result())
  }

  /**
   * Materializes a chunk into a chunk backed by an array. This method can
   * improve the performance of bulk operations.
   */
  def materialize[A1 >: A]: Chunk[A1] = self.toArrayOrNull[A1] match {
    case null  => Chunk.Empty
    case array => Chunk.fromArray(array)
  }

  /**
   * Runs `fn` if a `chunk` is not empty or returns default value
   */
  def nonEmptyOrElse[B](ifEmpty: => B)(fn: NonEmptyChunk[A] => B): B =
    if (isEmpty) ifEmpty
    else fn(NonEmptyChunk.nonEmpty(self))

  /**
   * Partitions the elements of this chunk into two chunks using the specified
   * function.
   */
  override final def partitionMap[B, C](f: A => Either[B, C]): (Chunk[B], Chunk[C]) = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    val bs       = ChunkBuilder.make[B](len >> 1)
    val cs       = ChunkBuilder.make[C](len >> 1)
    var idx      = 0
    while (idx < len) {
      f(iterator.nextAt(idx)) match {
        case Left(b)  => bs.addOne(b)
        case Right(c) => cs.addOne(c)
      }
      idx += 1
    }
    (bs.result(), cs.result())
  }

  /**
   * Get the element at the specified index.
   */
  def short(index: Int)(implicit ev: A <:< Short): Short = ev(apply(index))

  override def slice(from: Int, until: Int): Chunk[A] = {
    val len   = self.length
    val start =
      if (from < 0) 0
      else if (from > len) len
      else from
    val end =
      if (until < start) start
      else if (until > len) len
      else until
    new Chunk.Slice(
      {
        if (depth > Chunk.MaxDepthBeforeMaterialize) self.materialize
        else self
      },
      start,
      end - start
    )
  }

  override def span(f: A => Boolean): (Chunk[A], Chunk[A]) = splitWhere(!f(_))

  /**
   * Splits this chunk into `n` equally sized chunks.
   */
  final def split(n: Int): Chunk[Chunk[A]] = {
    val len       = self.length
    val quotient  = len / n
    val remainder = len % n
    val iterator  = self.chunkIterator
    var idx       = 0
    val chunks    = ChunkBuilder.make[Chunk[A]](n)
    var remIdx    = 0
    while (remIdx < remainder) {
      val chunk   = ChunkBuilder.make[A](quotient)
      var quotIdx = 0
      while (quotIdx <= quotient) {
        chunk.addOne(iterator.nextAt(idx))
        idx += 1
        quotIdx += 1
      }
      chunks.addOne(chunk.result())
      remIdx += 1
    }
    if (quotient > 0) {
      while (remIdx < n) {
        val chunk   = ChunkBuilder.make[A](quotient)
        var quotIdx = 0
        while (quotIdx < quotient) {
          chunk.addOne(iterator.nextAt(idx))
          idx += 1
          quotIdx += 1
        }
        chunks.addOne(chunk.result())
        remIdx += 1
      }
    }
    chunks.result()
  }

  /**
   * Returns two splits of this chunk at the specified index.
   */
  override final def splitAt(n: Int): (Chunk[A], Chunk[A]) = (take(n), drop(n))

  /**
   * Splits this chunk on the first element that matches this predicate.
   */
  final def splitWhere(f: A => Boolean): (Chunk[A], Chunk[A]) = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    while (idx < len) {
      val a = iterator.nextAt(idx)
      if (f(a)) return (take(idx), drop(idx))
      else idx += 1
    }
    (self, Chunk.empty)
  }

  /**
   * Takes the first `n` elements of the chunk.
   */
  override def take(n: Int): Chunk[A] =
    if (n <= 0) Chunk.Empty
    else if (n >= length) this
    else
      self match {
        case Chunk.Slice(c, o, _) => new Chunk.Slice(c, o, n)
        case Chunk.Concat(l, r)   =>
          if (n > l.length) new Chunk.Concat(l, r.take(n - l.length))
          else l.take(n)
        case _ =>
          new Chunk.Slice(
            {
              if (depth >= Chunk.MaxDepthBeforeMaterialize) self.materialize
              else self
            },
            0,
            n
          )
      }

  /**
   * Takes the last `n` elements of the chunk.
   */
  override def takeRight(n: Int): Chunk[A] =
    if (n <= 0) Chunk.Empty
    else if (n >= length) this
    else
      self match {
        case Chunk.Slice(c, o, l) => new Chunk.Slice(c, o + l - n, n)
        case Chunk.Concat(l, r)   =>
          if (n > r.length) new Chunk.Concat(l.takeRight(n - r.length), r)
          else r.takeRight(n)
        case _ =>
          new Chunk.Slice(
            {
              if (depth >= Chunk.MaxDepthBeforeMaterialize) self.materialize
              else self
            },
            length - n,
            n
          )
      }

  /**
   * Takes all elements so long as the predicate returns true.
   */
  override def takeWhile(f: A => Boolean): Chunk[A] = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    while (idx < len) {
      if (!f(iterator.nextAt(idx))) return take(idx)
      idx += 1
    }
    Chunk.empty
  }

  /**
   * Takes all elements so long as the effectual predicate returns true.
   */

  /**
   * Converts the chunk into an array.
   */
  override def toArray[A1 >: A: ClassTag]: Array[A1] =
    try {
      val dest = new Array[A1](self.length)
      self.toArray(0, dest)
      dest
    } catch {
      case _: ClassCastException =>
        val dest = new Array[AnyRef](self.length).asInstanceOf[Array[A1]]
        self.toArray(0, dest)
        dest
    }

  /**
   * Renders this chunk of bits as a binary string.
   */
  final def toBinaryString(implicit ev: A <:< Boolean): String = {
    val builder  = new java.lang.StringBuilder(length)
    val iterator = self.chunkIterator
    val len      = iterator.length
    var idx      = 0
    while (idx < len) {
      builder.append(if (iterator.nextAt(idx)) '1' else '0')
      idx += 1
    }
    builder.toString
  }

  override final def toList: List[A] = fromBuilder(List.newBuilder[A])

  override final def toVector: Vector[A] = fromBuilder(Vector.newBuilder[A])

  override final def toString: String = toArrayOrNull match {
    case null  => "Chunk()"
    case array => array.mkString("Chunk(", ",", ")")
  }

  /**
   * Zips this chunk with the specified chunk to produce a new chunk with pairs
   * of elements from each chunk. The returned chunk will have the length of the
   * shorter chunk.
   */
  final def zip[B](that: Chunk[B]): Chunk[(A, B)] = zipWith(that)((_, _))

  /**
   * Zips this chunk with the specified chunk to produce a new chunk with pairs
   * of elements from each chunk, filling in missing values from the shorter
   * chunk with `None`. The returned chunk will have the length of the longer
   * chunk.
   */
  final def zipAll[B](that: Chunk[B]): Chunk[(Option[A], Option[B])] =
    zipAllWith(that)(a => (Some(a), None), b => (None, Some(b)))((a, b) => (Some(a), Some(b)))

  /**
   * Zips with chunk with the specified chunk to produce a new chunk with pairs
   * of elements from each chunk combined using the specified function `both`.
   * If one chunk is shorter than the other, uses the specified function `left`
   * or `right` to map the element that does exist to the result type.
   */
  final def zipAllWith[B, C](that: Chunk[B])(left: A => C, right: B => C)(both: (A, B) => C): Chunk[C] = {
    val len1   = self.length
    val len2   = that.length
    val maxLen = Math.max(len1, len2)
    if (maxLen == 0) Chunk.empty
    else {
      val builder       = ChunkBuilder.make[C](maxLen)
      val leftIterator  = self.chunkIterator
      val rightIterator = that.chunkIterator
      val minLen        = Math.min(len1, len2)
      var idx           = 0
      while (idx < minLen) {
        builder.addOne(both(leftIterator.nextAt(idx), rightIterator.nextAt(idx)))
        idx += 1
      }
      while (idx < len1) {
        builder.addOne(left(leftIterator.nextAt(idx)))
        idx += 1
      }
      while (idx < len2) {
        builder.addOne(right(rightIterator.nextAt(idx)))
        idx += 1
      }
      builder.result()
    }
  }

  /**
   * Zips this chunk with the specified chunk using the specified combiner.
   */
  final def zipWith[B, C](that: Chunk[B])(f: (A, B) => C): Chunk[C] = {
    val minLen = self.length.min(that.length)
    if (minLen == 0) Chunk.empty
    else {
      val leftIterator  = self.chunkIterator
      val rightIterator = that.chunkIterator
      var idx           = 0
      val builder       = ChunkBuilder.make[C](minLen)
      while (idx < minLen) {
        val a = leftIterator.nextAt(idx)
        val b = rightIterator.nextAt(idx)
        builder.addOne(f(a, b))
        idx += 1
      }
      builder.result()
    }
  }

  /**
   * Zips this chunk with the index of every element, starting from the initial
   * index value.
   */
  final def zipWithIndexFrom(indexOffset: Int): Chunk[(A, Int)] = {
    val iterator  = self.chunkIterator
    val len       = iterator.length
    val builder   = ChunkBuilder.make[(A, Int)](len)
    var offsetIdx = indexOffset
    var idx       = 0
    while (idx < len) {
      builder.addOne((iterator.nextAt(idx), offsetIdx))
      idx += 1
      offsetIdx += 1
    }
    builder.result()
  }

  // noinspection AccessorLikeMethodIsUnit
  protected[chunk] final def toArray[A1 >: A](n: Int, dest: Array[A1]): Unit = toArray(0, dest, n, length)

  protected[chunk] def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit =
    if (!isEmpty) materialize.toArray(srcPos, dest, destPos, length)

  /**
   * Appends an element to the chunk.
   */
  protected def append[A1 >: A](a1: A1): Chunk[A1] = {
    val buffer = new Array[AnyRef](Chunk.BufferSize)
    buffer(0) = a1.asInstanceOf[AnyRef]
    if (depth >= Chunk.MaxDepthBeforeMaterialize) new Chunk.AppendN(self.materialize, buffer, 1, new AtomicInteger(1))
    else new Chunk.AppendN(self, buffer, 1, new AtomicInteger(1))
  }

  /**
   * Returns a filtered, mapped subset of the elements of this chunk.
   */
  protected def collectChunk[B](pf: PartialFunction[A, B]): Chunk[B] =
    if (isEmpty) Chunk.empty
    else self.materialize.collectChunk(pf)

  protected def concatDepth: Int = 0

  protected def depth: Int = 0

  protected def left: Chunk[A] = Chunk.empty

  /**
   * Returns a chunk with the elements mapped by the specified function.
   */
  protected def mapChunk[B](f: A => B): Chunk[B] = {
    val iterator = self.chunkIterator
    val len      = iterator.length
    val builder  = ChunkBuilder.make[B](len)
    var idx      = 0
    while (idx < len) {
      builder.addOne(f(iterator.nextAt(idx)))
      idx += 1
    }
    builder.result()
  }

  /**
   * Prepends an element to the chunk.
   */
  protected def prepend[A1 >: A](a1: A1): Chunk[A1] = {
    val buffer = new Array[AnyRef](Chunk.BufferSize)
    buffer(Chunk.BufferSize - 1) = a1.asInstanceOf[AnyRef]
    if (depth >= Chunk.MaxDepthBeforeMaterialize) Chunk.PrependN(self.materialize, buffer, 1, new AtomicInteger(1))
    else Chunk.PrependN(self, buffer, 1, new AtomicInteger(1))
  }

  protected def right: Chunk[A] = Chunk.empty

  /**
   * Updates an element at the specified index of the chunk.
   */
  protected def update[A1 >: A](index: Int, a1: A1): Chunk[A1] =
    if (index < 0 || index >= length) throw new IndexOutOfBoundsException(s"Update chunk access to $index")
    else {
      val bufferIndices = new Array[Int](Chunk.UpdateBufferSize)
      val bufferValues  = new Array[AnyRef](Chunk.UpdateBufferSize)
      bufferIndices(0) = index
      bufferValues(0) = a1.asInstanceOf[AnyRef]
      new Chunk.Update(
        {
          if (depth >= Chunk.MaxDepthBeforeMaterialize) self.materialize
          else self
        },
        bufferIndices,
        bufferValues,
        1,
        new AtomicInteger(1)
      )
    }

  private def fromBuilder[A1 >: A, B[_]](builder: Builder[A1, B[A1]]): B[A1] = {
    val chunk = materialize
    val len   = chunk.length
    builder.sizeHint(len)
    var idx = 0
    while (idx < len) {
      builder.addOne(chunk(idx))
      idx += 1
    }
    builder.result()
  }

  /**
   * A helper function that converts the chunk into an array if it is not empty.
   */
  private def toArrayOrNull[A1 >: A]: Array[A1] =
    if (self eq Chunk.Empty) null
    else self.toArray(Chunk.classTagOf(self))
}

object Chunk extends ChunkFactory with ChunkPlatformSpecific {

  /**
   * Returns a chunk from a number of values.
   */
  override def apply[A](as: A*): Chunk[A] =
    if (as.size == 1) single(as.head)
    else fromIterable(as)

  /*
   * Performs bitwise operations on boolean chunks returning a Chunk.BitChunk
   */
  private def bitwise(
    left: Chunk[Boolean],
    right: Chunk[Boolean],
    op: (Boolean, Boolean) => Boolean
  ): Chunk.BitChunkByte = {
    val bits      = Math.min(left.length, right.length)
    val fullBytes = bits >> 3
    val remBits   = bits & 7
    val arr       = new Array[Byte](
      if (remBits == 0) fullBytes
      else fullBytes + 1
    )
    var idx  = 0
    var mask = 128
    while (idx < fullBytes) {
      var byte = 0
      mask = 128
      var bitIdx = 0
      while (bitIdx < 8) {
        byte = byte | (if (op(left(idx * 8 + bitIdx), right(idx * 8 + bitIdx))) mask else 0)
        mask >>= 1
        bitIdx += 1
      }
      arr(idx) = byte.toByte
      idx += 1
    }
    if (remBits != 0) {
      val offset = fullBytes * 8
      var byte   = 0
      mask = 128
      var bitIdx = 0
      while (bitIdx < remBits) {
        byte = byte | (if (op(left(offset + bitIdx), right(offset + bitIdx))) mask else 0)
        mask >>= 1
        bitIdx += 1
      }
      arr(fullBytes) = byte.toByte
    }
    Chunk.BitChunkByte(Chunk.fromArray(arr), 0, bits)
  }

  /**
   * Returns the empty chunk.
   */
  override def empty[A]: Chunk[A] = Empty

  /**
   * Returns a chunk backed by an array.
   *
   * '''WARNING''': The array must not be mutated after creating the chunk. If
   * you're unsure whether the array will be mutated, prefer
   * `Chunk.fromIterable` or `Chunk.from` which create a copy of the provided
   * array.
   */
  def fromArray[A](array: Array[A]): Chunk[A] =
    (if (array.isEmpty) Empty
     else {
       (array.asInstanceOf[AnyRef]: @unchecked) match {
         case x: Array[AnyRef]  => new AnyRefArray(x, 0, array.length)
         case x: Array[Int]     => new IntArray(x, 0, array.length)
         case x: Array[Double]  => new DoubleArray(x, 0, array.length)
         case x: Array[Long]    => new LongArray(x, 0, array.length)
         case x: Array[Float]   => new FloatArray(x, 0, array.length)
         case x: Array[Char]    => new CharArray(x, 0, array.length)
         case x: Array[Byte]    => new ByteArray(x, 0, array.length)
         case x: Array[Short]   => new ShortArray(x, 0, array.length)
         case x: Array[Boolean] => new BooleanArray(x, 0, array.length)
       }
     }).asInstanceOf[Chunk[A]]

  /**
   * Returns a chunk backed by a [[java.nio.ByteBuffer]].
   */
  def fromByteBuffer(buffer: ByteBuffer): Chunk[Byte] = {
    val dest = new Array[Byte](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.CharBuffer]].
   */
  def fromCharBuffer(buffer: CharBuffer): Chunk[Char] = {
    val dest = new Array[Char](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.DoubleBuffer]].
   */
  def fromDoubleBuffer(buffer: DoubleBuffer): Chunk[Double] = {
    val dest = new Array[Double](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.FloatBuffer]].
   */
  def fromFloatBuffer(buffer: FloatBuffer): Chunk[Float] = {
    val dest = new Array[Float](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.IntBuffer]].
   */
  def fromIntBuffer(buffer: IntBuffer): Chunk[Int] = {
    val dest = new Array[Int](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.LongBuffer]].
   */
  def fromLongBuffer(buffer: LongBuffer): Chunk[Long] = {
    val dest = new Array[Long](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by a [[java.nio.ShortBuffer]].
   */
  def fromShortBuffer(buffer: ShortBuffer): Chunk[Short] = {
    val dest = new Array[Short](buffer.remaining())
    val pos  = buffer.position()
    buffer.get(dest)
    buffer.position(pos)
    Chunk.fromArray(dest)
  }

  /**
   * Returns a chunk backed by an iterable.
   */
  def fromIterable[A](it: Iterable[A]): Chunk[A] = it match {
    case chunk: Chunk[A]              => chunk
    case iterable if iterable.isEmpty => Empty
    case vector: Vector[A]            => new VectorChunk(vector)
    case arrSeq: mutable.ArraySeq[_]  => fromArraySeq(arrSeq.asInstanceOf[mutable.ArraySeq[A]])
    case iterable                     =>
      val builder = ChunkBuilder.make[A]()
      builder.addAll(iterable)
      builder.result()
  }

  /**
   * Creates a chunk from an iterator.
   */
  def fromIterator[A](iterator: Iterator[A]): Chunk[A] =
    if (iterator.hasNext) {
      val builder = ChunkBuilder.make[A]()
      builder.addAll(iterator)
      builder.result()
    } else Empty

  /**
   * Returns a chunk backed by a Java iterable.
   */
  def fromJavaIterable[A](iterable: java.lang.Iterable[A]): Chunk[A] = {
    val builder =
      if (iterable.isInstanceOf[java.util.Collection[_]]) {
        ChunkBuilder.make[A](iterable.asInstanceOf[java.util.Collection[_]].size())
      } else ChunkBuilder.make[A]()
    val iterator = iterable.iterator()
    while (iterator.hasNext) builder.addOne(iterator.next())
    builder.result()
  }

  /**
   * Creates a chunk from a Java iterator.
   */
  def fromJavaIterator[A](iterator: java.util.Iterator[A]): Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    while (iterator.hasNext) builder.addOne(iterator.next())
    builder.result()
  }

  override def fill[A](n: Int)(elem: => A): Chunk[A] =
    if (n <= 0) Chunk.empty
    else {
      val builder = ChunkBuilder.make[A](n)
      var idx     = 0
      while (idx < n) {
        builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

  override def iterate[A](start: A, len: Int)(f: A => A): Chunk[A] =
    if (len <= 0) Chunk.empty
    else {
      val builder = ChunkBuilder.make[A](len)
      var idx     = 0
      var a       = start
      while (idx < len) {
        builder.addOne(a)
        a = f(a)
        idx += 1
      }
      builder.result()
    }

  def newBuilder[A]: ChunkBuilder[A] = ChunkBuilder.make()

  /**
   * Returns a singleton chunk, eagerly evaluated.
   */
  def single[A](a: A): Chunk[A] = new Singleton(a)

  /**
   * Alias for [[Chunk.single]].
   */
  def succeed[A](a: A): Chunk[A] = new Singleton(a)

  /**
   * Constructs a `Chunk` by repeatedly applying the function `f` as long as it
   * returns `Some`.
   */
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Chunk[A] = {
    @tailrec
    def go(s: S, builder: ChunkBuilder[A]): Chunk[A] = f(s) match {
      case Some((a, s)) => go(s, builder.addOne(a))
      case None         => builder.result()
    }

    go(s, ChunkBuilder.make[A]())
  }

  /**
   * Constructs a `Chunk` by repeatedly applying the effectual function `f` as
   * long as it returns `Some`.
   */

  /**
   * The unit chunk
   */
  val unit: Chunk[Unit] = new Singleton(())

  /**
   * Returns the `ClassTag` for the element type of the chunk.
   */
  private[chunk] def classTagOf[A](chunk: Chunk[A]): ClassTag[A] =
    chunk match {
      case x: AppendN[_]            => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Arr[_]                => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Concat[_]             => x.classTag.asInstanceOf[ClassTag[A]]
      case Empty                    => classTag[java.lang.Object].asInstanceOf[ClassTag[A]]
      case x: PrependN[_]           => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Singleton[_]          => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Slice[_]              => x.classTag.asInstanceOf[ClassTag[A]]
      case x: Update[_]             => x.classTag.asInstanceOf[ClassTag[A]]
      case x: VectorChunk[_]        => x.classTag.asInstanceOf[ClassTag[A]]
      case x: ChunkPackedBoolean[_] => x.classTag.asInstanceOf[ClassTag[A]]
      case _: BitChunk[_]           => ClassTag.Boolean.asInstanceOf[ClassTag[A]]
    }

  sealed trait IsText[-T] {
    def convert(chunk: Chunk[T]): String
  }

  object IsText {
    implicit val byteIsText: IsText[Byte] =
      new IsText[Byte] { def convert(chunk: Chunk[Byte]): String = new String(chunk.toArray) }
    implicit val charIsText: IsText[Char] =
      new IsText[Char] { def convert(chunk: Chunk[Char]): String = new String(chunk.toArray) }
    implicit val strIsText: IsText[String] =
      new IsText[String] { def convert(chunk: Chunk[String]): String = chunk.toArray.mkString }
  }

  /**
   * The maximum number of elements in the buffer for fast append.
   */
  private val BufferSize: Int = 64

  /**
   * The maximum depth of elements in the chunk before it is materialized.
   */
  private val MaxDepthBeforeMaterialize: Int = 128

  /**
   * The maximum number of elements in the buffer for fast update.
   */
  private val UpdateBufferSize: Int = 256

  private final case class AppendN[A](start: Chunk[A], buffer: Array[AnyRef], bufferUsed: Int, chain: AtomicInteger)
      extends Chunk[A] { self =>

    def chunkIterator: ChunkIterator[A] =
      start.chunkIterator ++ ChunkIterator.fromArray(buffer.asInstanceOf[Array[A]]).sliceIterator(0, bufferUsed)

    implicit val classTag: ClassTag[A] = classTagOf(start)

    override val depth: Int = start.depth + 1

    val length: Int = start.length + bufferUsed

    override def append[A1 >: A](a1: A1): Chunk[A1] =
      if (bufferUsed < buffer.length && chain.compareAndSet(bufferUsed, bufferUsed + 1)) {
        buffer(bufferUsed) = a1.asInstanceOf[AnyRef]
        new AppendN(start, buffer, bufferUsed + 1, chain)
      } else {
        val buffer = new Array[AnyRef](BufferSize)
        buffer(0) = a1.asInstanceOf[AnyRef]
        val chunk = Chunk.fromArray(self.buffer.asInstanceOf[Array[A1]]).take(bufferUsed)
        new AppendN(start ++ chunk, buffer, 1, new AtomicInteger(1))
      }

    def apply(n: Int): A =
      if (n < 0 || n >= length) throw new IndexOutOfBoundsException(s"Append chunk access to $n")
      else if (n < start.length) start(n)
      else buffer(n - start.length).asInstanceOf[A]

    override def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit = {
      val n = Math.max(Math.min(Math.min(length, start.length - srcPos), dest.length - destPos), 0)
      start.toArray(Math.min(start.length, srcPos), dest, destPos, n)
      Array.copy(buffer, Math.max(srcPos - start.length, 0), dest, destPos + n, length - n)
    }
  }

  private final case class PrependN[A](end: Chunk[A], buffer: Array[AnyRef], bufferUsed: Int, chain: AtomicInteger)
      extends Chunk[A] { self =>
    def chunkIterator: ChunkIterator[A] =
      ChunkIterator
        .fromArray(buffer.asInstanceOf[Array[A]])
        .sliceIterator(BufferSize - bufferUsed, bufferUsed) ++ end.chunkIterator

    implicit val classTag: ClassTag[A] = classTagOf(end)

    override val depth: Int = end.depth + 1

    val length: Int = end.length + bufferUsed

    override def prepend[A1 >: A](a1: A1): Chunk[A1] =
      if (bufferUsed < buffer.length && chain.compareAndSet(bufferUsed, bufferUsed + 1)) {
        buffer(BufferSize - bufferUsed - 1) = a1.asInstanceOf[AnyRef]
        new PrependN(end, buffer, bufferUsed + 1, chain)
      } else {
        val buffer = new Array[AnyRef](BufferSize)
        buffer(BufferSize - 1) = a1.asInstanceOf[AnyRef]
        val chunk = Chunk.fromArray(self.buffer.asInstanceOf[Array[A1]]).takeRight(bufferUsed)
        new PrependN(chunk ++ end, buffer, 1, new AtomicInteger(1))
      }

    def apply(n: Int): A =
      if (n < 0 || n >= length) throw new IndexOutOfBoundsException(s"Prepend chunk access to $n")
      else if (n < bufferUsed) buffer(BufferSize - bufferUsed + n).asInstanceOf[A]
      else end(n - bufferUsed)

    override def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit = {
      val n = Math.max(Math.min(Math.min(length, bufferUsed - srcPos), dest.length - destPos), 0)
      Array.copy(buffer, Math.min(BufferSize, BufferSize - bufferUsed + srcPos), dest, destPos, n)
      end.toArray(Math.max(srcPos - bufferUsed, 0), dest, destPos + n, length - n)
    }
  }

  private final case class Update[A](
    chunk: Chunk[A],
    bufferIndices: Array[Int],
    bufferValues: Array[AnyRef],
    used: Int,
    chain: AtomicInteger
  ) extends Chunk[A] { self =>
    def chunkIterator: ChunkIterator[A] = ChunkIterator.fromArray(self.toArray)

    implicit val classTag: ClassTag[A] = Chunk.classTagOf(chunk)

    override val depth: Int = chunk.depth + 1

    def length: Int = chunk.length

    def apply(i: Int): A = {
      var j = used - 1
      var a = null.asInstanceOf[A]
      while (j >= 0) {
        if (bufferIndices(j) == i) {
          a = bufferValues(j).asInstanceOf[A]
          j = -1
        } else {
          j -= 1
        }
      }
      if (a != null) a else chunk(i)
    }

    override def update[A1 >: A](i: Int, a: A1): Chunk[A1] =
      if (i < 0 || i >= length) throw new IndexOutOfBoundsException(s"Update chunk access to $i")
      else if (used < UpdateBufferSize && chain.compareAndSet(used, used + 1)) {
        bufferIndices(used) = i
        bufferValues(used) = a.asInstanceOf[AnyRef]
        new Update(chunk, bufferIndices, bufferValues, used + 1, chain)
      } else {
        val bufferIndices = new Array[Int](UpdateBufferSize)
        val bufferValues  = new Array[AnyRef](UpdateBufferSize)
        bufferIndices(0) = i
        bufferValues(0) = a.asInstanceOf[AnyRef]
        val array = self.asInstanceOf[Chunk[AnyRef]].toArray
        new Update(Chunk.fromArray(array.asInstanceOf[Array[A1]]), bufferIndices, bufferValues, 1, new AtomicInteger(1))
      }

    override def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit = {
      chunk.toArray(srcPos, dest, destPos, length)
      var i = 0
      while (i < used) {
        val index = bufferIndices(i)
        val value = self.bufferValues(i)
        if (index >= srcPos && index < srcPos + length) dest(index + destPos) = value.asInstanceOf[A1]
        i += 1
      }
    }
  }

  private final case class Concat[A](override protected val left: Chunk[A], override protected val right: Chunk[A])
      extends Chunk[A] { self =>
    def chunkIterator: ChunkIterator[A] = left.chunkIterator ++ right.chunkIterator

    implicit val classTag: ClassTag[A] = {
      val lct = classTagOf(left)
      val rct = classTagOf(right)
      if (left eq Empty) lct
      else if (right eq Empty) rct
      else if (lct == rct) lct
      else ClassTag.AnyRef.asInstanceOf[ClassTag[A]]
    }

    override val concatDepth: Int = Math.max(left.concatDepth, right.concatDepth) + 1

    override val depth: Int = Math.max(left.depth, right.depth) + 1

    override val length: Int = left.length + right.length

    override def apply(n: Int): A =
      if (n < left.length) left(n)
      else right(n - left.length)

    override def foreach[B](f: A => B): Unit = {
      left.foreach(f)
      right.foreach(f)
    }

    override def iterator: Iterator[A] = left.iterator ++ right.iterator

    override def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit = {
      val n = Math.max(Math.min(Math.min(length, left.length - srcPos), dest.length - destPos), 0)
      left.toArray(Math.min(left.length, srcPos), dest, destPos, n)
      right.toArray(Math.max(srcPos - left.length, 0), dest, destPos + n, length - n)
    }
  }

  private final case class Singleton[A](a: A) extends Chunk[A] with ChunkIterator[A] { self =>
    implicit val classTag: ClassTag[A] = Tags.fromValue(a)

    override def length = 1

    override def apply(n: Int): A =
      if (n == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $n")

    override def foreach[B](f: A => B): Unit = {
      val _ = f(a)
    }

    override def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit = dest(destPos) = a

    override def chunkIterator: ChunkIterator[A] = self

    override def hasNextAt(index: Int): Boolean = index == 0

    override def nextAt(index: Int): A =
      if (index == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $index")

    override def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= 1) self
      else ChunkIterator.empty
  }

  private final case class Slice[A](private val chunk: Chunk[A], offset: Int, l: Int) extends Chunk[A] {
    def chunkIterator: ChunkIterator[A] = chunk.chunkIterator.sliceIterator(offset, l)

    implicit val classTag: ClassTag[A] = classTagOf(chunk)

    override val depth: Int = chunk.depth + 1

    override def length: Int = l

    override def apply(n: Int): A = chunk.apply(offset + n)

    override def foreach[B](f: A => B): Unit = {
      val len = length
      var idx = 0
      while (idx < len) {
        f(apply(idx))
        idx += 1
      }
    }

    override def iterator: Iterator[A] = chunk.iterator.slice(offset, offset + l)

    override def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit =
      chunk.toArray(srcPos + offset, dest, destPos, Math.min(length, l - srcPos))
  }

  private final case class VectorChunk[A](private val vector: Vector[A]) extends Chunk[A] {
    def chunkIterator: ChunkIterator[A] = ChunkIterator.fromVector(vector)

    implicit val classTag: ClassTag[A] = Tags.fromValue(vector(0))

    override def length: Int = vector.length

    override def apply(n: Int): A = vector(n)

    override def foreach[B](f: A => B): Unit = vector.foreach(f)

    override def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit =
      vector.drop(srcPos).copyToArray(dest, destPos, length)
  }

  private[chunk] trait BitOps[T] {
    def zero: T
    def one: T
    def reverse(a: T): T
    def <<(x: T, n: Int): T
    def >>(x: T, n: Int): T
    def |(x: T, y: T): T
    def &(x: T, y: T): T
    def ^(x: T, y: T): T
    def invert(x: T): T
    def classTag: ClassTag[T]
  }

  private[chunk] object BitOps {
    def apply[T](implicit ops: BitOps[T]): BitOps[T] = ops

    implicit val ByteOps: BitOps[Byte] = new BitOps[Byte] {
      def zero: Byte                = 0
      def one: Byte                 = 1
      def reverse(a: Byte): Byte    = throw new UnsupportedOperationException
      def <<(x: Byte, n: Int): Byte = (x << n).toByte
      def >>(x: Byte, n: Int): Byte = (x >> n).toByte
      def |(x: Byte, y: Byte): Byte = (x | y).toByte
      def &(x: Byte, y: Byte): Byte = (x & y).toByte
      def ^(x: Byte, y: Byte): Byte = (x ^ y).toByte
      def invert(x: Byte): Byte     = (~x).toByte
      def classTag: ClassTag[Byte]  = ClassTag.Byte
    }
    implicit val IntOps: BitOps[Int] = new BitOps[Int] {
      def zero: Int               = 0
      def one: Int                = 1
      def reverse(a: Int): Int    = Integer.reverse(a)
      def <<(x: Int, n: Int): Int = x << n
      def >>(x: Int, n: Int): Int = x >> n
      def |(x: Int, y: Int): Int  = x | y
      def &(x: Int, y: Int): Int  = x & y
      def ^(x: Int, y: Int): Int  = x ^ y
      def invert(x: Int): Int     = ~x
      def classTag: ClassTag[Int] = ClassTag.Int
    }
    implicit val LongOps: BitOps[Long] = new BitOps[Long] {
      def zero: Long                = 0L
      def one: Long                 = 1L
      def reverse(a: Long): Long    = java.lang.Long.reverse(a)
      def <<(x: Long, n: Int): Long = x << n
      def >>(x: Long, n: Int): Long = x >> n
      def |(x: Long, y: Long): Long = x | y
      def &(x: Long, y: Long): Long = x & y
      def ^(x: Long, y: Long): Long = x ^ y
      def invert(x: Long): Long     = ~x
      def classTag: ClassTag[Long]  = ClassTag.Long
    }
  }

  private[chunk] sealed abstract class BitChunk[T](
    chunk: Chunk[T],
    val bits: Int
  ) extends Chunk[Boolean]
      with ChunkIterator[Boolean] { self =>
    protected val minBitIndex: Int
    protected val maxBitIndex: Int
    protected val bitsLog2: Int = (Math.log(bits.toDouble) / Math.log(2d)).toInt
    val length: Int             = maxBitIndex - minBitIndex

    protected def elementAt(n: Int): T

    protected def newBitChunk(chunk: Chunk[T], min: Int, max: Int): BitChunk[T]

    override def drop(n: Int): Chunk[Boolean] = {
      val index  = Math.min(minBitIndex + n, maxBitIndex)
      val toDrop = index >> bitsLog2
      val min    = index & bits - 1
      val max    = maxBitIndex - index + min
      newBitChunk(chunk.drop(toDrop), min, max)
    }

    override def take(n: Int): Chunk[Boolean] = {
      val index  = Math.min(minBitIndex + n, maxBitIndex)
      val toTake = (index + bits - 1) >> bitsLog2
      newBitChunk(chunk.take(toTake), minBitIndex, index)
    }

    override def foreach[A](f: Boolean => A): Unit = {
      val minElementIndex = (minBitIndex + bits - 1) >> bitsLog2
      val maxElementIndex = maxBitIndex >> bitsLog2
      val minFullBitIndex = Math.min(minElementIndex << bitsLog2, maxBitIndex)
      val maxFullBitIndex = Math.max(maxElementIndex << bitsLog2, minFullBitIndex)
      val prefixBits      = minFullBitIndex - minBitIndex
      val suffixBitsStart = maxFullBitIndex - minBitIndex
      var idx             = 0
      while (idx < prefixBits) {
        f(self.apply(idx))
        idx += 1
      }
      idx = minElementIndex
      while (idx < maxElementIndex) {
        foreachElement(f, elementAt(idx))
        idx += 1
      }
      idx = suffixBitsStart
      while (idx < length) {
        f(self.apply(idx))
        idx += 1
      }
    }

    protected def foreachElement[A](f: Boolean => A, elem: T): Unit

    override protected[chunk] def toArray[A1 >: Boolean](
      srcPos: Int,
      dest: Array[A1],
      destPos: Int,
      length: Int
    ): Unit = {
      var idx = 0
      while (idx < length) {
        dest(idx + destPos) = self.apply(idx + srcPos)
        idx += 1
      }
    }

    def chunkIterator: ChunkIterator[Boolean] = self

    def hasNextAt(index: Int): Boolean = index < length

    def nextAt(index: Int): Boolean = self(index)

    override def slice(from: Int, until: Int): BitChunk[T] = {
      val lo = Math.max(from, 0)
      if (lo <= 0 && until >= self.length) self
      else if (lo >= self.length || until <= lo) newBitChunk(Chunk.empty, 0, 0)
      else newBitChunk(chunk, self.minBitIndex + lo, Math.min(self.minBitIndex + until, self.maxBitIndex))
    }

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Boolean] = {
      val lo = Math.max(offset, 0)
      if (lo <= 0 && length >= self.length) self
      else if (lo >= self.length || length <= 0) ChunkIterator.empty
      else newBitChunk(chunk, self.minBitIndex + lo, Math.min(self.minBitIndex + lo + length, self.maxBitIndex))
    }
  }

  object BitChunk {
    sealed trait Endianness
    object Endianness {
      case object BigEndian    extends Endianness
      case object LittleEndian extends Endianness
    }
  }

  private[chunk] final case class BitChunkByte(bytes: Chunk[Byte], minBitIndex: Int, maxBitIndex: Int)
      extends BitChunk[Byte](bytes, 8) { self =>

    override val length: Int = maxBitIndex - minBitIndex

    override def apply(n: Int): Boolean = {
      val bitIndex = n + minBitIndex
      (bytes(bitIndex >> bitsLog2) & (1 << (bits - 1 - (bitIndex & bits - 1)))) != 0
    }

    override protected def elementAt(n: Int): Byte = bytes(n)

    override protected def newBitChunk(bytes: Chunk[Byte], min: Int, max: Int): BitChunk[Byte] =
      new BitChunkByte(bytes, min, max)

    override protected def foreachElement[A](f: Boolean => A, byte: Byte): Unit = {
      f((byte & 128) != 0)
      f((byte & 64) != 0)
      f((byte & 32) != 0)
      f((byte & 16) != 0)
      f((byte & 8) != 0)
      f((byte & 4) != 0)
      f((byte & 2) != 0)
      f((byte & 1) != 0)
      ()
    }

    override def toPackedByte(implicit ev: Boolean <:< Boolean): Chunk[Byte] =
      if (minBitIndex == maxBitIndex) Chunk.empty
      else if ((minBitIndex & bits - 1) != 0) new ChunkPackedBoolean[Byte](self, bits, BitChunk.Endianness.BigEndian)
      else if ((maxBitIndex & bits - 1) != 0) {
        val minByteIndex = minBitIndex >> bitsLog2
        val maxByteIndex = maxBitIndex >> bitsLog2
        val maxByte      = bytes(maxByteIndex)
        val maxByteValue = (maxByte & 0xff) >> bits - (maxBitIndex & bits - 1)
        bytes.slice(minByteIndex, maxByteIndex) :+ maxByteValue.toByte
      } else bytes

    private def nthByte(n: Int): Byte = {
      val offset    = minBitIndex & 7
      val startByte = minBitIndex >> 3
      if (offset == 0) bytes(n + startByte)
      else {
        val leftover = minBitIndex + (n + 1) * 8 - maxBitIndex
        if (leftover <= 0) {
          val index  = n + startByte
          val first  = ((255 >> offset) & bytes(index)) << offset
          val second = (255 << (8 - offset) & 255 & bytes(index + 1)) >> (8 - offset)
          (first | second).asInstanceOf[Byte]
        } else throw new ArrayIndexOutOfBoundsException(s"There are only $leftover bits left.")
      }
    }

    private def bitwise(that: BitChunkByte, f: (Byte, Byte) => Byte, g: (Boolean, Boolean) => Boolean): BitChunkByte = {
      val bits      = Math.min(self.length, that.length)
      val bytes     = bits >> 3
      val offset    = bytes << 3
      val leftovers = bits - offset
      val arr       = new Array[Byte](
        if (leftovers == 0) bytes
        else bytes + 1
      )
      var idx = 0
      while (idx < bytes) {
        arr(idx) = f(self.nthByte(idx), that.nthByte(idx))
        idx += 1
      }
      if (leftovers != 0) {
        var last: Byte = 0
        var idx        = 0
        while (idx < leftovers) {
          if (g(self.apply(offset + idx), that.apply(offset + idx))) last = (last | (128 >> idx)).toByte
          idx += 1
        }
        arr(bytes) = last
      }
      new BitChunkByte(Chunk.fromArray(arr), 0, bits)
    }

    def and(that: BitChunkByte): BitChunkByte = bitwise(that, (l, r) => (l & r).toByte, _ && _)

    def &(that: BitChunkByte): BitChunkByte = bitwise(that, (l, r) => (l & r).toByte, _ && _)

    def or(that: BitChunkByte): BitChunkByte = bitwise(that, (l, r) => (l | r).toByte, _ || _)

    def |(that: BitChunkByte): BitChunkByte = bitwise(that, (l, r) => (l | r).toByte, _ || _)

    def xor(that: BitChunkByte): BitChunkByte = bitwise(that, (l, r) => (l ^ r).toByte, _ ^ _)

    def ^(that: BitChunkByte): BitChunkByte = bitwise(that, (l, r) => (l ^ r).toByte, _ ^ _)

    def negate: BitChunkByte = {
      val bits      = self.length
      val bytes     = bits >> 3
      val leftovers = bits - bytes * 8
      val arr       = new Array[Byte](
        if (leftovers == 0) bytes
        else bytes + 1
      )
      var idx = 0
      while (idx < bytes) {
        arr(idx) = (~self.nthByte(idx)).toByte
        idx += 1
      }
      if (leftovers != 0) {
        val offset     = bytes * 8
        var last: Byte = null.asInstanceOf[Byte]
        var idx        = 0
        while (idx < leftovers) {
          if (!self.apply(offset + idx)) last = (last | (128 >> idx)).toByte
          idx += 1
        }
        arr(bytes) = last
      }
      new BitChunkByte(Chunk.fromArray(arr), 0, bits)
    }
  }

  private[chunk] final case class BitChunkInt(
    ints: Chunk[Int],
    endianness: BitChunk.Endianness,
    minBitIndex: Int,
    maxBitIndex: Int
  ) extends BitChunk[Int](ints, 32) { self =>
    override val length: Int = maxBitIndex - minBitIndex

    override protected def elementAt(n: Int): Int = respectEndian(endianness, ints(n))

    override def apply(n: Int): Boolean = {
      val bitIndex = n + minBitIndex
      (elementAt(bitIndex >> bitsLog2) & (1 << (bits - 1 - (bitIndex & bits - 1)))) != 0
    }

    override protected def newBitChunk(chunk: Chunk[Int], min: Int, max: Int): BitChunk[Int] =
      new BitChunkInt(chunk, endianness, min, max)

    override protected def foreachElement[A](f: Boolean => A, int: Int): Unit = {
      f((int & 0x80000000) != 0)
      f((int & 0x40000000) != 0)
      f((int & 0x20000000) != 0)
      f((int & 0x10000000) != 0)
      f((int & 0x8000000) != 0)
      f((int & 0x4000000) != 0)
      f((int & 0x2000000) != 0)
      f((int & 0x1000000) != 0)
      f((int & 0x800000) != 0)
      f((int & 0x400000) != 0)
      f((int & 0x200000) != 0)
      f((int & 0x100000) != 0)
      f((int & 0x80000) != 0)
      f((int & 0x40000) != 0)
      f((int & 0x20000) != 0)
      f((int & 0x10000) != 0)
      f((int & 0x8000) != 0)
      f((int & 0x4000) != 0)
      f((int & 0x2000) != 0)
      f((int & 0x1000) != 0)
      f((int & 0x800) != 0)
      f((int & 0x400) != 0)
      f((int & 0x200) != 0)
      f((int & 0x100) != 0)
      f((int & 0x80) != 0)
      f((int & 0x40) != 0)
      f((int & 0x20) != 0)
      f((int & 0x10) != 0)
      f((int & 0x8) != 0)
      f((int & 0x4) != 0)
      f((int & 0x2) != 0)
      f((int & 0x1) != 0)
      ()
    }

    override def toPackedInt(endianness: BitChunk.Endianness)(implicit ev: Boolean <:< Boolean): Chunk[Int] =
      if (minBitIndex == maxBitIndex) Chunk.empty
      else if ((minBitIndex & bits - 1) != 0) new ChunkPackedBoolean[Int](self, bits, endianness)
      else if ((maxBitIndex & bits - 1) != 0) {
        val minIntIndex = minBitIndex >> bitsLog2
        val maxIntIndex = maxBitIndex >> bitsLog2
        val maxInt      = elementAt(maxIntIndex)
        val maxIntValue = maxInt >>> bits - (maxBitIndex & bits - 1)
        val fullInts    = ints.slice(minIntIndex, maxIntIndex)
        if (self.endianness == endianness) fullInts :+ respectEndian(endianness, maxIntValue)
        else fullInts.map(Integer.reverse) :+ respectEndian(endianness, maxIntValue)
      } else if (self.endianness == endianness) ints
      else ints.map(Integer.reverse)

    private def respectEndian(endianness: BitChunk.Endianness, bigEndianValue: Int) =
      if (endianness == BitChunk.Endianness.BigEndian) bigEndianValue
      else Integer.reverse(bigEndianValue)
  }

  private[chunk] final case class BitChunkLong(
    longs: Chunk[Long],
    endianness: BitChunk.Endianness,
    minBitIndex: Int,
    maxBitIndex: Int
  ) extends BitChunk[Long](longs, 64) { self =>
    override protected def elementAt(n: Int): Long =
      if (endianness == BitChunk.Endianness.BigEndian) longs(n)
      else java.lang.Long.reverse(longs(n))

    def apply(n: Int): Boolean = {
      val bitIndex = n + minBitIndex
      (elementAt(bitIndex >> bitsLog2) & (1L << (bits - 1 - (bitIndex & bits - 1)))) != 0
    }

    override protected def newBitChunk(longs: Chunk[Long], min: Int, max: Int): BitChunk[Long] =
      new BitChunkLong(longs, endianness, min, max)

    override protected def foreachElement[A](f: Boolean => A, long: Long): Unit = {
      f((long & 0x8000000000000000L) != 0)
      f((long & 0x4000000000000000L) != 0)
      f((long & 0x2000000000000000L) != 0)
      f((long & 0x1000000000000000L) != 0)
      f((long & 0x800000000000000L) != 0)
      f((long & 0x400000000000000L) != 0)
      f((long & 0x200000000000000L) != 0)
      f((long & 0x100000000000000L) != 0)
      f((long & 0x80000000000000L) != 0)
      f((long & 0x40000000000000L) != 0)
      f((long & 0x20000000000000L) != 0)
      f((long & 0x10000000000000L) != 0)
      f((long & 0x8000000000000L) != 0)
      f((long & 0x4000000000000L) != 0)
      f((long & 0x2000000000000L) != 0)
      f((long & 0x1000000000000L) != 0)
      f((long & 0x800000000000L) != 0)
      f((long & 0x400000000000L) != 0)
      f((long & 0x200000000000L) != 0)
      f((long & 0x100000000000L) != 0)
      f((long & 0x80000000000L) != 0)
      f((long & 0x40000000000L) != 0)
      f((long & 0x20000000000L) != 0)
      f((long & 0x10000000000L) != 0)
      f((long & 0x8000000000L) != 0)
      f((long & 0x4000000000L) != 0)
      f((long & 0x2000000000L) != 0)
      f((long & 0x1000000000L) != 0)
      f((long & 0x800000000L) != 0)
      f((long & 0x400000000L) != 0)
      f((long & 0x200000000L) != 0)
      f((long & 0x100000000L) != 0)
      f((long & 0x80000000L) != 0)
      f((long & 0x40000000L) != 0)
      f((long & 0x20000000L) != 0)
      f((long & 0x10000000L) != 0)
      f((long & 0x8000000L) != 0)
      f((long & 0x4000000L) != 0)
      f((long & 0x2000000L) != 0)
      f((long & 0x1000000L) != 0)
      f((long & 0x800000L) != 0)
      f((long & 0x400000L) != 0)
      f((long & 0x200000L) != 0)
      f((long & 0x100000L) != 0)
      f((long & 0x80000L) != 0)
      f((long & 0x40000L) != 0)
      f((long & 0x20000L) != 0)
      f((long & 0x10000L) != 0)
      f((long & 0x8000L) != 0)
      f((long & 0x4000L) != 0)
      f((long & 0x2000L) != 0)
      f((long & 0x1000L) != 0)
      f((long & 0x800L) != 0)
      f((long & 0x400L) != 0)
      f((long & 0x200L) != 0)
      f((long & 0x100L) != 0)
      f((long & 0x80L) != 0)
      f((long & 0x40L) != 0)
      f((long & 0x20L) != 0)
      f((long & 0x10L) != 0)
      f((long & 0x8L) != 0)
      f((long & 0x4L) != 0)
      f((long & 0x2L) != 0)
      f((long & 0x1L) != 0)
      ()
    }

    override def toPackedLong(endianness: BitChunk.Endianness)(implicit ev: Boolean <:< Boolean): Chunk[Long] =
      if (minBitIndex == maxBitIndex) Chunk.empty
      else if ((minBitIndex & bits - 1) != 0) new ChunkPackedBoolean[Long](self, bits, endianness)
      else if ((maxBitIndex & bits - 1) != 0) {
        val minLongIndex = minBitIndex >> bitsLog2
        val maxLongIndex = maxBitIndex >> bitsLog2
        val maxLong      = elementAt(maxLongIndex)
        val maxLongValue = maxLong >>> bits - (maxBitIndex & bits - 1)
        val fullLongs    = longs.slice(minLongIndex, maxLongIndex)
        if (self.endianness == endianness) fullLongs :+ respectEndian(endianness, maxLongValue)
        else fullLongs.map(java.lang.Long.reverse) :+ respectEndian(endianness, maxLongValue)
      } else if (self.endianness == endianness) longs
      else longs.map(java.lang.Long.reverse)

    private def respectEndian(endianness: BitChunk.Endianness, bigEndianValue: Long) =
      if (endianness == BitChunk.Endianness.BigEndian) bigEndianValue
      else java.lang.Long.reverse(bigEndianValue)
  }

  private[chunk] final case class ChunkPackedBoolean[T](
    unpacked: Chunk[Boolean],
    bits: Int,
    endianness: Chunk.BitChunk.Endianness
  )(implicit val ops: BitOps[T])
      extends Chunk[T]
      with ChunkIterator[T] { self =>
    import ops._

    override val length: Int = unpacked.length / bits + {
      if (unpacked.length % bits == 0) 0
      else 1
    }

    private def bitOr0(index: Int) =
      if (index < unpacked.length && unpacked(index)) one
      else zero

    override def apply(n: Int): T =
      if (n < 0 || n >= length) {
        throw new IndexOutOfBoundsException(s"Packed boolean chunk index $n out of bounds [0, $length)")
      } else {
        val offset     = n * bits
        val bitsToRead = if ((n + 1) * bits > unpacked.length) unpacked.length % bits else bits
        var elem       = zero
        var idx        = bitsToRead - 1
        while (idx >= 0) {
          val shiftBy = bitsToRead - 1 - idx
          val shifted = <<(bitOr0(offset + idx), shiftBy)
          elem = ops.|(elem, shifted)
          idx -= 1
        }
        if (endianness == BitChunk.Endianness.BigEndian) elem
        else ops.reverse(elem)
      }

    override protected[chunk] def toArray[A1 >: T](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit = {
      var idx = 0
      while (idx < length) {
        dest(idx + destPos) = self.apply(idx + srcPos)
        idx += 1
      }
    }

    override def chunkIterator: ChunkIterator[T] = self

    implicit val classTag: ClassTag[T] = ops.classTag

    def hasNextAt(index: Int): Boolean = index < length

    def nextAt(index: Int): T = self(index)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[T] =
      if (offset < 0 && offset >= this.length) self
      else new ChunkPackedBoolean(unpacked.slice(offset * bits, length * bits), bits, endianness)
  }

  private case object Empty extends Chunk[Nothing] { self =>
    def chunkIterator: ChunkIterator[Nothing] = ChunkIterator.empty

    override def length: Int = 0

    override def apply(n: Int): Nothing = throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $n")

    override def equals(that: Any): Boolean = that match {
      case seq: Seq[_] => seq.isEmpty
      case _           => false
    }

    override def foreach[B](f: Nothing => B): Unit = {
      val _ = f
    }

    /**
     * Materializes a chunk into a chunk backed by an array. This method can
     * improve the performance of bulk operations.
     */
    override def materialize[A1]: Chunk[A1] = Empty

    override def toArray[A1: ClassTag]: Array[A1] = Array.empty
  }

  private[chunk] sealed abstract class Arr[A] extends Chunk[A] with Serializable { self =>
    val array: Array[A]

    implicit val classTag: ClassTag[A] = ClassTag(array.getClass.getComponentType)

    override def collectWhile[B](pf: PartialFunction[A, B]): Chunk[B] = {
      val arr     = array
      val len     = arr.length
      val builder = ChunkBuilder.make[B](len)
      var idx     = 0
      var done    = false
      while (!done && idx < len) {
        val a = arr(idx)
        if (pf.isDefinedAt(a)) builder.addOne(pf.apply(a))
        else done = true
        idx += 1
      }
      builder.result()
    }

    override def dropWhile(f: A => Boolean): Chunk[A] = {
      val arr = array
      val len = length
      var idx = 0
      while (idx < len && f(arr(idx))) idx += 1
      drop(idx)
    }

    override def filter(f: A => Boolean): Chunk[A] = {
      val len     = self.length
      val builder = ChunkBuilder.make[A](len)
      var idx     = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    override def foldLeft[S](s0: S)(f: (S, A) => S): S = {
      val len = self.length
      var s   = s0
      var idx = 0
      while (idx < len) {
        s = f(s, self(idx))
        idx += 1
      }
      s
    }

    override def foldRight[S](s0: S)(f: (A, S) => S): S = {
      val arr = array
      val len = arr.length
      var s   = s0
      var idx = len - 1
      while (idx >= 0) {
        s = f(arr(idx), s)
        idx -= 1
      }
      s
    }

    override def foreach[B](f: A => B): Unit = array.foreach(f)

    override def iterator: Iterator[A] = array.iterator

    override def materialize[A1 >: A]: Chunk[A1] = self

    /**
     * Takes all elements so long as the predicate returns true.
     */
    override def takeWhile(f: A => Boolean): Chunk[A] = {
      val arr = array
      val len = arr.length
      var idx = 0
      while (idx < len && f(arr(idx))) idx += 1
      take(idx)
    }

    override protected[chunk] def toArray[A1 >: A](srcPos: Int, dest: Array[A1], destPos: Int, length: Int): Unit =
      Array.copy(array, srcPos, dest, destPos, length)

    override protected def collectChunk[B](pf: PartialFunction[A, B]): Chunk[B] = {
      val arr     = array
      val len     = arr.length
      val builder = ChunkBuilder.make[B](len)
      var idx     = 0
      while (idx < len) {
        val a = arr(idx)
        if (pf.isDefinedAt(a)) builder.addOne(pf.apply(a))
        idx += 1
      }
      builder.result()
    }

    override protected def mapChunk[B](f: A => B): Chunk[B] = {
      implicit val ct: ClassTag[B] = ClassTag.AnyRef.asInstanceOf[ClassTag[B]]
      Chunk.fromArray(self.array.map(f))
    }
  }

  final case class AnyRefArray[A <: AnyRef](array: Array[A], offset: Int, override val length: Int)
      extends Arr[A]
      with ChunkIterator[A] { self =>
    def apply(index: Int): A = array(index + offset)

    def chunkIterator: ChunkIterator[A] = self

    def hasNextAt(index: Int): Boolean = index < length

    def nextAt(index: Int): A = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new AnyRefArray(array, self.offset + offset, Math.min(self.length - offset, length))
  }

  final case class ByteArray(array: Array[Byte], offset: Int, override val length: Int)
      extends Arr[Byte]
      with ChunkIterator[Byte] { self =>
    def apply(index: Int): Byte = array(index + offset)

    override def byte(index: Int)(implicit ev: Byte <:< Byte): Byte = array(index + offset)

    def chunkIterator: ChunkIterator[Byte] = self

    override def filter(f: Byte => Boolean): Chunk[Byte] = {
      val len     = self.length
      val builder = new ChunkBuilder.Byte
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    def hasNextAt(index: Int): Boolean = index < length

    override protected def mapChunk[B](f: Byte => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try {
            newArr(idx) = newVal
          } catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Byte = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Byte] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new ByteArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Byte => Boolean): Chunk[Byte] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  final case class CharArray(array: Array[Char], offset: Int, override val length: Int)
      extends Arr[Char]
      with ChunkIterator[Char] { self =>
    def apply(index: Int): Char = array(index + offset)

    override def char(index: Int)(implicit ev: Char <:< Char): Char = array(index + offset)

    def chunkIterator: ChunkIterator[Char] = self

    override def filter(f: Char => Boolean): Chunk[Char] = {
      val len     = self.length
      val builder = new ChunkBuilder.Char
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    def hasNextAt(index: Int): Boolean = index < length

    override protected def mapChunk[B](f: Char => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try {
            newArr(idx) = newVal
          } catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Char = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Char] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new CharArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Char => Boolean): Chunk[Char] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  final case class IntArray(array: Array[Int], offset: Int, override val length: Int)
      extends Arr[Int]
      with ChunkIterator[Int] { self =>
    def apply(index: Int): Int = array(index + offset)

    def chunkIterator: ChunkIterator[Int] = self

    override def filter(f: Int => Boolean): Chunk[Int] = {
      val len     = self.length
      val builder = new ChunkBuilder.Int
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    def hasNextAt(index: Int): Boolean = index < length

    override def int(index: Int)(implicit ev: Int <:< Int): Int = array(index + offset)

    override protected def mapChunk[B](f: Int => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try {
            newArr(idx) = newVal
          } catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Int = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Int] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new IntArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Int => Boolean): Chunk[Int] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  final case class LongArray(array: Array[Long], offset: Int, override val length: Int)
      extends Arr[Long]
      with ChunkIterator[Long] { self =>
    def apply(index: Int): Long = array(index + offset)

    def chunkIterator: ChunkIterator[Long] = self

    override def filter(f: Long => Boolean): Chunk[Long] = {
      val len     = self.length
      val builder = new ChunkBuilder.Long
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    def hasNextAt(index: Int): Boolean = index < length

    override def long(index: Int)(implicit ev: Long <:< Long): Long = array(index + offset)

    override protected def mapChunk[B](f: Long => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try newArr(idx) = newVal
          catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Long = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Long] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new LongArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Long => Boolean): Chunk[Long] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  final case class DoubleArray(array: Array[Double], offset: Int, override val length: Int)
      extends Arr[Double]
      with ChunkIterator[Double] { self =>
    def apply(index: Int): Double = array(index + offset)

    def chunkIterator: ChunkIterator[Double] = self

    override def double(index: Int)(implicit ev: Double <:< Double): Double = array(index + offset)

    override def filter(f: Double => Boolean): Chunk[Double] = {
      val len     = self.length
      val builder = new ChunkBuilder.Double
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    def hasNextAt(index: Int): Boolean = index < length

    override protected def mapChunk[B](f: Double => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try newArr(idx) = newVal
          catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Double = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Double] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new DoubleArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Double => Boolean): Chunk[Double] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  final case class FloatArray(array: Array[Float], offset: Int, override val length: Int)
      extends Arr[Float]
      with ChunkIterator[Float] { self =>
    def apply(index: Int): Float = array(index + offset)

    def chunkIterator: ChunkIterator[Float] = self

    override def filter(f: Float => Boolean): Chunk[Float] = {
      val len     = self.length
      val builder = new ChunkBuilder.Float
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    override def float(index: Int)(implicit ev: Float <:< Float): Float = array(index + offset)

    def hasNextAt(index: Int): Boolean = index < length

    override protected def mapChunk[B](f: Float => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try newArr(idx) = newVal
          catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Float = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Float] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new FloatArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Float => Boolean): Chunk[Float] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  final case class ShortArray(array: Array[Short], offset: Int, override val length: Int)
      extends Arr[Short]
      with ChunkIterator[Short] { self =>
    def apply(index: Int): Short = array(index + offset)

    def chunkIterator: ChunkIterator[Short] = self

    override def filter(f: Short => Boolean): Chunk[Short] = {
      val len     = self.length
      val builder = new ChunkBuilder.Short
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    def hasNextAt(index: Int): Boolean = index < length

    override protected def mapChunk[B](f: Short => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try newArr(idx) = newVal
          catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Short = array(index + offset)

    override def short(index: Int)(implicit ev: Short <:< Short): Short = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Short] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new ShortArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Short => Boolean): Chunk[Short] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  final case class BooleanArray(array: Array[Boolean], offset: Int, length: Int)
      extends Arr[Boolean]
      with ChunkIterator[Boolean] { self =>
    def apply(index: Int): Boolean = array(index + offset)

    override def boolean(index: Int)(implicit ev: Boolean <:< Boolean): Boolean = array(index + offset)

    def chunkIterator: ChunkIterator[Boolean] = self

    override def filter(f: Boolean => Boolean): Chunk[Boolean] = {
      val len     = self.length
      val builder = new ChunkBuilder.Boolean
      builder.sizeHint(len)
      var idx = 0
      while (idx < len) {
        val elem = self(idx)
        if (f(elem)) builder.addOne(elem)
        idx += 1
      }
      builder.result()
    }

    def hasNextAt(index: Int): Boolean = index < length

    override protected def mapChunk[B](f: Boolean => B): Chunk[B] = {
      val len = self.length
      if (len == 0) Empty
      else {
        val oldArr = array
        val head   = f(oldArr(0))
        var newArr = Array.ofDim[B](len)(Chunk.Tags.fromValue(head))
        var idx    = 1
        newArr(0) = head
        while (idx < len) {
          val newVal = f(oldArr(idx))
          try newArr(idx) = newVal
          catch {
            case _: ClassCastException =>
              val newArr2 = new Array[AnyRef](len)
              var newIdx  = 0
              while (newIdx < idx) {
                newArr2(newIdx) = newArr(newIdx).asInstanceOf[AnyRef]
                newIdx += 1
              }
              newArr = newArr2.asInstanceOf[Array[B]]
              newArr(idx) = newVal
          }
          idx += 1
        }
        Chunk.fromArray(newArr)
      }
    }

    def nextAt(index: Int): Boolean = array(index + offset)

    def sliceIterator(offset: Int, length: Int): ChunkIterator[Boolean] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) ChunkIterator.empty
      else new BooleanArray(array, self.offset + offset, Math.min(self.length - offset, length))

    override def takeWhile(f: Boolean => Boolean): Chunk[Boolean] = {
      val self = array
      val len  = length
      var idx  = 0
      while (idx < len && f(self(idx))) idx += 1
      take(idx)
    }
  }

  /**
   * A `ChunkIterator` is a specialized iterator that supports efficient
   * iteration over chunks. Unlike a normal iterator, the caller is responsible
   * for providing an `index` with each call to `hasNextAt` and `nextAt`. By
   * contract this should be `0` initially and incremented by `1` each time
   * `nextAt` is called. This allows the caller to maintain the current index in
   * local memory rather than the iterator having to do it on the heap for array
   * backed chunks.
   */
  sealed trait ChunkIterator[+A] { self =>

    /**
     * Checks if the chunk iterator has another element.
     */
    def hasNextAt(index: Int): Boolean

    /**
     * The length of the iterator.
     */
    def length: Int

    /**
     * Gets the next element from the chunk iterator.
     */
    def nextAt(index: Int): A

    /**
     * Returns a new iterator that is a slice of this iterator.
     */
    def sliceIterator(offset: Int, length: Int): ChunkIterator[A]

    /**
     * Concatenates this chunk iterator with the specified chunk iterator.
     */
    final def ++[A1 >: A](that: ChunkIterator[A1]): ChunkIterator[A1] = ChunkIterator.Concat(self, that)
  }

  object ChunkIterator {

    /**
     * The empty iterator.
     */
    val empty: ChunkIterator[Nothing] = Empty

    /**
     * Constructs an iterator from an array of arbitrary values.
     */
    def fromArray[A](array: Array[A]): ChunkIterator[A] = array match {
      case array: Array[AnyRef]  => Chunk.AnyRefArray(array, 0, array.length)
      case array: Array[Boolean] => Chunk.BooleanArray(array, 0, array.length)
      case array: Array[Byte]    => Chunk.ByteArray(array, 0, array.length)
      case array: Array[Char]    => Chunk.CharArray(array, 0, array.length)
      case array: Array[Double]  => Chunk.DoubleArray(array, 0, array.length)
      case array: Array[Float]   => Chunk.FloatArray(array, 0, array.length)
      case array: Array[Int]     => Chunk.IntArray(array, 0, array.length)
      case array: Array[Long]    => Chunk.LongArray(array, 0, array.length)
      case array: Array[Short]   => Chunk.ShortArray(array, 0, array.length)
    }

    /**
     * Constructs an iterator from a `Vector`.
     */
    def fromVector[A](vector: Vector[A]): ChunkIterator[A] =
      if (vector.length <= 0) Empty
      else Iterator(vector.iterator, vector.length)

    private case class Concat[A](left: ChunkIterator[A], right: ChunkIterator[A]) extends ChunkIterator[A] { self =>
      def hasNextAt(index: Int): Boolean = index < length

      val length: Int = left.length + right.length

      def nextAt(index: Int): A =
        if (left.hasNextAt(index)) left.nextAt(index)
        else right.nextAt(index - left.length)

      def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
        if (offset <= 0 && length >= self.length) self
        else if (offset >= self.length || length <= 0) Empty
        else if (offset >= left.length) right.sliceIterator(offset - left.length, length)
        else if (length <= left.length - offset) left.sliceIterator(offset, length)
        else {
          new Concat(
            left.sliceIterator(offset, left.length - offset),
            right.sliceIterator(0, offset + length - left.length)
          )
        }
    }

    private case object Empty extends ChunkIterator[Nothing] { self =>
      def hasNextAt(index: Int): Boolean = false

      def length: Int = 0

      def nextAt(index: Int): Nothing = throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $index")

      def sliceIterator(offset: Int, length: Int): ChunkIterator[Nothing] = self
    }

    private case class Iterator[A](iterator: scala.Iterator[A], length: Int) extends ChunkIterator[A] { self =>
      def hasNextAt(index: Int): Boolean = iterator.hasNext

      def nextAt(index: Int): A = iterator.next()

      def sliceIterator(offset: Int, length: Int): ChunkIterator[A] =
        if (offset <= 0 && length >= self.length) self
        else if (offset >= self.length || length <= 0) Empty
        else new Iterator(iterator.slice(offset, offset + length), Math.min(self.length - offset, length))
    }
  }
}

/**
 * A `NonEmptyChunk` is a `Chunk` that is guaranteed to contain at least one
 * element. As a result, operations which would not be safe when performed on
 * `Chunk`, such as `head` or `reduce`, are safe when performed on
 * `NonEmptyChunk`. Operations on `NonEmptyChunk` which could potentially return
 * an empty chunk will return a `Chunk` instead.
 */
final class NonEmptyChunk[+A] private (private val chunk: Chunk[A]) extends Serializable { self =>
  import NonEmptyChunk.nonEmpty

  /**
   * A symbolic alias for `prepended`.
   */
  def +:[A1 >: A](a: A1): NonEmptyChunk[A1] = prepended(a)

  /**
   * A symbolic alias for `appended`.
   */
  def :+[A1 >: A](a: A1): NonEmptyChunk[A1] = appended(a)

  /**
   * Appends the specified `Chunk` to the end of this `NonEmptyChunk`.
   */
  def ++[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1] = append(that)

  /**
   * A named alias for `++`.
   */
  def append[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1] = nonEmpty(chunk ++ that)

  /**
   * Appends a single element to the end of this `NonEmptyChunk`.
   */
  def appended[A1 >: A](a: A1): NonEmptyChunk[A1] = nonEmpty(chunk :+ a)

  /**
   * Converts this `NonEmptyChunk` of ints to a `NonEmptyChunk` of bits.
   */
  def asBitsInt(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Int): NonEmptyChunk[Boolean] =
    nonEmpty(chunk.asBitsInt(endianness))

  /**
   * Converts this `NonEmptyChunk` of longs to a `NonEmptyChunk` of bits.
   */
  def asBitsLong(endianness: Chunk.BitChunk.Endianness)(implicit ev: A <:< Long): NonEmptyChunk[Boolean] =
    nonEmpty(chunk.asBitsLong(endianness))

  /**
   * Converts this `NonEmptyChunk` of bytes to a `NonEmptyChunk` of bits.
   */
  def asBits(implicit ev: A <:< Byte): NonEmptyChunk[Boolean] = nonEmpty(chunk.asBitsByte)

  def collect[B](pf: PartialFunction[A, B]): Chunk[B] = chunk.collect(pf)

  def collectFirst[B](pf: PartialFunction[A, B]): Option[B] = chunk.collectFirst(pf)

  def distinct: NonEmptyChunk[A] = nonEmpty(chunk.distinct)

  /**
   * Returns whether this `NonEmptyChunk` and the specified `NonEmptyChunk` are
   * equal to each other.
   */
  override def equals(that: Any): Boolean = that match {
    case that: NonEmptyChunk[_] => self.chunk == that.chunk
    case _                      => false
  }

  /**
   * Determines whether a predicate is satisfied for at least one element of
   * this `NonEmptyChunk`.
   */
  def exists(p: A => Boolean): Boolean = chunk.exists(p)

  def filter(p: A => Boolean): Chunk[A] = chunk.filter(p)

  def filterNot(p: A => Boolean): Chunk[A] = chunk.filterNot(p)

  def find(p: A => Boolean): Option[A] = chunk.find(p)

  /**
   * Maps each element of this `NonEmptyChunk` to a new `NonEmptyChunk` and then
   * concatenates them together.
   */
  def flatMap[B](f: A => NonEmptyChunk[B]): NonEmptyChunk[B] = nonEmpty(chunk.flatMap(a => f(a).chunk))

  /**
   * Flattens a `NonEmptyChunk` of `NonEmptyChunk` values to a single
   * `NonEmptyChunk`.
   */
  def flatten[B](implicit ev: A <:< NonEmptyChunk[B]): NonEmptyChunk[B] = flatMap(ev)

  def foldLeft[B](z: B)(op: (B, A) => B): B = chunk.foldLeft(z)(op)

  def forall(p: A => Boolean): Boolean = chunk.forall(p)

  def grouped(size: Int): Iterator[NonEmptyChunk[A]] = chunk.grouped(size).map(nonEmpty)

  /**
   * Groups the values in this `NonEmptyChunk` using the specified function.
   */
  def groupBy[K](f: A => K): Map[K, NonEmptyChunk[A]] = groupMap(f)(identity)

  /**
   * Groups and transformers the values in this `NonEmptyChunk` using the
   * specified function.
   */
  def groupMap[K, V](key: A => K)(f: A => V): Map[K, NonEmptyChunk[V]] = {
    val m = collection.mutable.Map.empty[K, ChunkBuilder[V]]
    chunk.foreach { a =>
      m.getOrElseUpdate(key(a), ChunkBuilder.make[V](1)).addOne(f(a))
    }
    var result = collection.immutable.Map.empty[K, NonEmptyChunk[V]]
    m.foreach { kv =>
      result = result.updated(kv._1, nonEmpty(kv._2.result()))
    }
    result
  }

  /**
   * Returns the hashcode of this `NonEmptyChunk`.
   */
  override def hashCode: Int = chunk.hashCode

  def head: A = chunk.head

  def init: Chunk[A] = chunk.init

  def iterator: Iterator[A] = chunk.iterator

  def last: A = chunk.last

  /**
   * Transforms the elements of this `NonEmptyChunk` with the specified
   * function.
   */
  def map[B](f: A => B): NonEmptyChunk[B] = nonEmpty(chunk.map(f))

  /**
   * Maps over the elements of this `NonEmptyChunk`, maintaining some state
   * along the way.
   */
  def mapAccum[S, B](s: S)(f: (S, A) => (S, B)): (S, NonEmptyChunk[B]) =
    chunk.mapAccum(s)(f) match { case (s, chunk) => (s, nonEmpty(chunk)) }

  /**
   * Materialize the elements of this `NonEmptyChunk` into a `NonEmptyChunk`
   * backed by an array.
   */
  def materialize[A1 >: A]: NonEmptyChunk[A1] = nonEmpty(chunk.materialize)

  /**
   * Prepends the specified `Chunk` to the beginning of this `NonEmptyChunk`.
   */
  def prepend[A1 >: A](that: Chunk[A1]): NonEmptyChunk[A1] = nonEmpty(that ++ chunk)

  /**
   * Prepends a single element to the beginning of this `NonEmptyChunk`.
   */
  def prepended[A1 >: A](a: A1): NonEmptyChunk[A1] = nonEmpty(a +: chunk)

  def reduce[B >: A](op: (B, B) => B): B = chunk.reduce(op)

  /**
   * Reduces the elements of this `NonEmptyChunk` from left to right using the
   * function `map` to transform the first value to the type `B` and then the
   * function `reduce` to combine the `B` value with each other `A` value.
   */
  def reduceMapLeft[B](map: A => B)(reduce: (B, A) => B): B = {
    val iterator = chunk.iterator
    var b: B     = null.asInstanceOf[B]
    while (iterator.hasNext) {
      val a = iterator.next()
      if (b == null) b = map(a)
      else b = reduce(b, a)
    }
    b
  }

  /**
   * Reduces the elements of this `NonEmptyChunk` from right to left using the
   * function `map` to transform the first value to the type `B` and then the
   * function `reduce` to combine the `B` value with each other `A` value.
   */
  def reduceMapRight[B](map: A => B)(reduce: (A, B) => B): B = {
    val iterator = chunk.reverseIterator
    var b: B     = null.asInstanceOf[B]
    while (iterator.hasNext) {
      val a = iterator.next()
      if (b == null) b = map(a)
      else b = reduce(a, b)
    }
    b
  }

  def reverse: NonEmptyChunk[A] = nonEmpty(chunk.reverse)

  def size: Int = chunk.size

  def sortBy[B](f: A => B)(implicit ord: Ordering[B]): NonEmptyChunk[A] = nonEmpty(chunk.sortBy(f))

  def sorted[B >: A](implicit ord: Ordering[B]): NonEmptyChunk[B] = nonEmpty(chunk.sorted[B])

  def tail: Chunk[A] = chunk.tail

  /**
   * Converts this `NonEmptyChunk` to an array.
   */
  def toArray[B >: A: ClassTag]: Array[B] = chunk.toArray

  /**
   * Converts this `NonEmptyChunk` to a `Chunk`, discarding information about it
   * not being empty.
   */
  def toChunk: Chunk[A] = chunk

  /**
   * Converts this `NonEmptyChunk` to the `::` case of a `List`.
   */
  def toCons[A1 >: A]: ::[A1] = ::(chunk(0), chunk.drop(1).toList)

  /**
   * Converts this `NonEmptyChunk` to an `Iterable`.
   */
  def toIterable: Iterable[A] = chunk

  /**
   * Converts this `NonEmptyChunk` to a `List`.
   */
  def toList: List[A] = chunk.toList

  /**
   * Renders this `NonEmptyChunk` as a `String`.
   */
  override def toString: String = chunk.mkString("NonEmptyChunk(", ", ", ")")

  /**
   * Zips this `NonEmptyChunk` with the specified `NonEmptyChunk`, only keeping
   * as many elements as are in the smaller chunk.
   */
  def zip[B](that: NonEmptyChunk[B]): NonEmptyChunk[(A, B)] = zipWith(that)((_, _))

  /**
   * Zips this `NonEmptyChunk` with the specified `Chunk`, using `None` to "fill
   * in" missing values if one chunk has fewer elements than the other.
   */
  def zipAll[B](that: Chunk[B]): NonEmptyChunk[(Option[A], Option[B])] =
    zipAllWith(that)(a => (Some(a), None), b => (None, Some(b)))((a, b) => (Some(a), Some(b)))

  /**
   * Zips this `NonEmptyChunk` with the specified `Chunk`, using the specified
   * functions to "fill in" missing values if one chunk has fewer elements than
   * the other.
   */
  def zipAllWith[B, C](
    that: Chunk[B]
  )(left: A => C, right: B => C)(both: (A, B) => C): NonEmptyChunk[C] =
    nonEmpty(chunk.zipAllWith(that)(left, right)(both))

  /**
   * Zips this `NonEmptyChunk` with the specified `NonEmptyChunk`, only keeping
   * as many elements as are in the smaller chunk.
   */
  def zipWith[B, C](that: NonEmptyChunk[B])(f: (A, B) => C): NonEmptyChunk[C] = nonEmpty(chunk.zipWith(that.chunk)(f))

  /**
   * Annotates each element of this `NonEmptyChunk` with its index.
   */
  def zipWithIndex: NonEmptyChunk[(A, Int)] = nonEmpty(chunk.zipWithIndex)

  /**
   * Annotates each element of this `NonEmptyChunk` with its index, with the
   * specified offset.
   */
  def zipWithIndexFrom(indexOffset: Int): NonEmptyChunk[(A, Int)] = nonEmpty(chunk.zipWithIndexFrom(indexOffset))
}

object NonEmptyChunk {

  /**
   * Constructs a `NonEmptyChunk` from one or more values.
   */
  def apply[A](a: A, as: A*): NonEmptyChunk[A] = fromIterable(a, as)

  /**
   * Checks if a `chunk` is not empty and constructs a `NonEmptyChunk` from it.
   */
  def fromChunk[A](chunk: Chunk[A]): Option[NonEmptyChunk[A]] =
    chunk.nonEmptyOrElse[Option[NonEmptyChunk[A]]](None)(Some(_))

  /**
   * Constructs a `NonEmptyChunk` from the `::` case of a `List`.
   */
  def fromCons[A](as: ::[A]): NonEmptyChunk[A] = fromIterable(as.head, as.tail)

  /**
   * Constructs a `NonEmptyChunk` from an `Iterable`.
   */
  def fromIterable[A](a: A, as: Iterable[A]): NonEmptyChunk[A] =
    if (as.isEmpty) single(a)
    else {
      nonEmpty {
        val builder = ChunkBuilder.make[A](as.size + 1)
        builder.addOne(a)
        builder.addAll(as)
        builder.result()
      }
    }

  /**
   * Constructs a `NonEmptyChunk` from an `Iterable` or `None` otherwise.
   */
  def fromIterableOption[A](as: Iterable[A]): Option[NonEmptyChunk[A]] =
    if (as.isEmpty) None
    else new Some(nonEmpty(Chunk.fromIterable(as)))

  /**
   * Constructs a `NonEmptyChunk` from a single value.
   */
  def single[A](a: A): NonEmptyChunk[A] = nonEmpty(Chunk.single(a))

  /**
   * Extracts the elements from a `Chunk`.
   */
  def unapplySeq[A](seq: Seq[A]): Option[Seq[A]] = seq match {
    case chunk: Chunk[A] if chunk.nonEmpty => new Some(chunk)
    case _                                 => None
  }

  /**
   * Extracts the elements from a `NonEmptyChunk`.
   */
  def unapplySeq[A](nonEmptyChunk: NonEmptyChunk[A]): Some[Seq[A]] = new Some(nonEmptyChunk.chunk)

  /**
   * The unit non-empty chunk.
   */
  val unit: NonEmptyChunk[Unit] = single(())

  /**
   * Provides an implicit conversion from `NonEmptyChunk` to `Chunk` for methods
   * that may not return a `NonEmptyChunk`.
   */
  implicit def toChunk[A](nonEmptyChunk: NonEmptyChunk[A]): Chunk[A] = nonEmptyChunk.chunk

  /**
   * Constructs a `NonEmptyChunk` from a `Chunk`. This should only be used when
   * it is statically known that the `Chunk` must have at least one element.
   */
  private[chunk] def nonEmpty[A](chunk: Chunk[A]): NonEmptyChunk[A] = new NonEmptyChunk(chunk)
}
