package zio.blocks.schema.binding

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import zio.blocks.chunk.{Chunk, ChunkBuilder}

trait SeqConstructor[C[_]] {
  type Builder[_]

  def newBuilder[A](sizeHint: Int = 8)(implicit ct: ClassTag[A]): Builder[A]

  def add[A](builder: Builder[A], a: A): Unit

  def result[A](builder: Builder[A]): C[A]

  def empty[A](implicit ct: ClassTag[A]): C[A]
}

object SeqConstructor {
  given setConstructor: SeqConstructor[Set] with {
    type Builder[A] = scala.collection.mutable.Builder[A, Set[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = Set.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): Set[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Set[A] = Set.empty
  }

  given listConstructor: SeqConstructor[List] with {
    type Builder[A] = scala.collection.mutable.ListBuffer[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = new ListBuffer[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): List[A] = builder.toList

    def empty[A](implicit ct: ClassTag[A]): List[A] = Nil
  }

  given vectorConstructor: SeqConstructor[Vector] with {
    type Builder[A] = scala.collection.mutable.Builder[A, Vector[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = Vector.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): Vector[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Vector[A] = Vector.empty
  }

  given arraySeqConstructor: SeqConstructor[ArraySeq] with {
    case class ArrayBuilder[A](var buffer: Array[A], var size: Int)

    type Builder[A] = ArrayBuilder[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] =
      new ArrayBuilder(new Array[A](Math.max(sizeHint, 1)), 0)

    def add[A](builder: Builder[A], a: A): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], idx << 1).asInstanceOf[Array[A]]
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def result[A](builder: Builder[A]): ArraySeq[A] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], size).asInstanceOf[Array[A]]
    }

    def empty[A](implicit ct: ClassTag[A]): ArraySeq[A] = ArraySeq.empty[A]
  }

  given indexedSeqConstructor: SeqConstructor[IndexedSeq] with {
    type Builder[A] = scala.collection.mutable.Builder[A, IndexedSeq[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = IndexedSeq.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): IndexedSeq[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): IndexedSeq[A] = Vector.empty
  }

  given seqConstructor: SeqConstructor[collection.immutable.Seq] with {
    type Builder[A] = scala.collection.mutable.Builder[A, collection.immutable.Seq[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] =
      collection.immutable.Seq.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): collection.immutable.Seq[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Seq[A] = Nil
  }

  given chunkConstructor: SeqConstructor[Chunk] with {
    type Builder[A] = ChunkBuilder[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = ChunkBuilder.make[A]()

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): Chunk[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Chunk[A] = Chunk.empty
  }

  implicit val arrayConstructor: SeqConstructor[Array] = new SeqConstructor[Array] {
    case class ArrayBuilder[A](var buffer: Array[A], var size: Int)

    type Builder[A] = ArrayBuilder[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] =
      new ArrayBuilder(new Array[A](Math.max(sizeHint, 1)), 0)

    def add[A](builder: Builder[A], a: A): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], idx << 1).asInstanceOf[Array[A]]
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def result[A](builder: Builder[A]): Array[A] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], size).asInstanceOf[Array[A]]
    }

    def empty[A](implicit ct: ClassTag[A]): Array[A] = Array.empty[A]
  }

  implicit val iArrayConstructor: SeqConstructor[IArray] = new SeqConstructor[IArray] {
    case class ArrayBuilder[A](var buffer: Array[A], var size: Int)

    type Builder[A] = ArrayBuilder[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] =
      new ArrayBuilder(new Array[A](Math.max(sizeHint, 1)), 0)

    def add[A](builder: Builder[A], a: A): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], idx << 1).asInstanceOf[Array[A]]
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def result[A](builder: Builder[A]): IArray[A] = IArray.unsafeFromArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], size).asInstanceOf[Array[A]]
    }

    def empty[A](implicit ct: ClassTag[A]): IArray[A] = IArray.empty[A]
  }
}
