package zio.blocks.schema.binding

import scala.collection.immutable.ArraySeq
import zio.blocks.chunk.Chunk

trait SeqDeconstructor[C[_]] {
  def deconstruct[A](c: C[A]): Iterator[A]

  def size[A](c: C[A]): Int
}

object SeqDeconstructor {
  sealed trait SpecializedIndexed[C[_]] extends SeqDeconstructor[C] {
    def elementType[A](c: C[A]): RegisterType[A]

    def objectAt[A](c: C[A], index: Int): A

    def booleanAt(c: C[Boolean], index: Int): Boolean

    def byteAt(c: C[Byte], index: Int): Byte

    def shortAt(c: C[Short], index: Int): Short

    def intAt(c: C[Int], index: Int): Int

    def longAt(c: C[Long], index: Int): Long

    def floatAt(c: C[Float], index: Int): Float

    def doubleAt(c: C[Double], index: Int): Double

    def charAt(c: C[Char], index: Int): Char
  }

  given setDeconstructor: SeqDeconstructor[Set] = new SeqDeconstructor[Set] {
    def deconstruct[A](c: Set[A]): Iterator[A] = c.iterator

    def size[A](c: Set[A]): Int = c.size
  }

  given listDeconstructor: SeqDeconstructor[List] = new SeqDeconstructor[List] {
    def deconstruct[A](c: List[A]): Iterator[A] = c.iterator

    def size[A](c: List[A]): Int = c.size
  }

  given vectorDeconstructor: SeqDeconstructor[Vector] = new SeqDeconstructor[Vector] {
    def deconstruct[A](c: Vector[A]): Iterator[A] = c.iterator

    def size[A](c: Vector[A]): Int = c.length
  }

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

  given indexedSeqDeconstructor: SeqDeconstructor[IndexedSeq] = new SeqDeconstructor[IndexedSeq] {
    def deconstruct[A](c: IndexedSeq[A]): Iterator[A] = c.iterator

    def size[A](c: IndexedSeq[A]): Int = c.length
  }

  given seqDeconstructor: SeqDeconstructor[Seq] = new SeqDeconstructor[Seq] {
    def deconstruct[A](c: Seq[A]): Iterator[A] = c.iterator

    def size[A](c: Seq[A]): Int = c.length
  }

  given chunkDeconstructor: SeqDeconstructor[Chunk] = new SeqDeconstructor[Chunk] {
    def deconstruct[A](c: Chunk[A]): Iterator[A] = c.iterator

    def size[A](c: Chunk[A]): Int = c.length
  }

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
