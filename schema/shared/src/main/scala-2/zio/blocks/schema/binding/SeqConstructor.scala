package zio.blocks.schema.binding

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import zio.blocks.chunk.{Chunk, ChunkBuilder}

trait SeqConstructor[C[_]] {
  type Builder[_]

  def newBuilder[A](sizeHint: Int = 8)(implicit ct: ClassTag[A]): Builder[A]

  def add[A](builder: Builder[A], a: A): Unit

  def addBoolean(builder: Builder[Boolean], a: Boolean): Unit = add(builder, a)
  def addByte(builder: Builder[Byte], a: Byte): Unit          = add(builder, a)
  def addShort(builder: Builder[Short], a: Short): Unit       = add(builder, a)
  def addInt(builder: Builder[Int], a: Int): Unit             = add(builder, a)
  def addLong(builder: Builder[Long], a: Long): Unit          = add(builder, a)
  def addFloat(builder: Builder[Float], a: Float): Unit       = add(builder, a)
  def addDouble(builder: Builder[Double], a: Double): Unit    = add(builder, a)
  def addChar(builder: Builder[Char], a: Char): Unit          = add(builder, a)

  def result[A](builder: Builder[A]): C[A]

  def empty[A](implicit ct: ClassTag[A]): C[A]
}

object SeqConstructor {
  implicit val setConstructor: SeqConstructor[Set] = new SeqConstructor[Set] {
    type Builder[A] = scala.collection.mutable.Builder[A, Set[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = Set.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): Set[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Set[A] = Set.empty
  }

  implicit val listConstructor: SeqConstructor[List] = new SeqConstructor[List] {
    type Builder[A] = scala.collection.mutable.ListBuffer[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = new ListBuffer[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): List[A] = builder.toList

    def empty[A](implicit ct: ClassTag[A]): List[A] = Nil
  }

  implicit val vectorConstructor: SeqConstructor[Vector] = new SeqConstructor[Vector] {
    type Builder[A] = scala.collection.mutable.Builder[A, Vector[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = Vector.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): Vector[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Vector[A] = Vector.empty
  }

  private[binding] abstract class PrimitiveArraySeqConstructor[C[_]] extends SeqConstructor[C] {
    case class ArrayBuilder[A](var buffer: Array[A], var size: Int, ct: ClassTag[A])

    type Builder[A] = ArrayBuilder[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] =
      new ArrayBuilder(ct.newArray(Math.max(sizeHint, 1)), 0, ct)

    def add[A](builder: Builder[A], a: A): Unit = {
      val buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        val newBuf = builder.ct.newArray(idx << 1)
        System.arraycopy(buf, 0, newBuf, 0, idx)
        builder.buffer = newBuf
        newBuf(idx) = a
      } else {
        buf(idx) = a
      }
      builder.size = idx + 1
    }

    override def addBoolean(builder: Builder[Boolean], a: Boolean): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    override def addByte(builder: Builder[Byte], a: Byte): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    override def addShort(builder: Builder[Short], a: Short): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    override def addInt(builder: Builder[Int], a: Int): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    override def addLong(builder: Builder[Long], a: Long): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    override def addFloat(builder: Builder[Float], a: Float): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    override def addDouble(builder: Builder[Double], a: Double): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    override def addChar(builder: Builder[Char], a: Char): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    protected def trimArray[A](builder: Builder[A]): Array[A] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else {
        val newBuf = builder.ct.newArray(size)
        System.arraycopy(buf, 0, newBuf, 0, size)
        newBuf
      }
    }
  }

  implicit val arraySeqConstructor: SeqConstructor[ArraySeq] = new PrimitiveArraySeqConstructor[ArraySeq] {
    def result[A](builder: Builder[A]): ArraySeq[A] = ArraySeq.unsafeWrapArray(trimArray(builder))

    def empty[A](implicit ct: ClassTag[A]): ArraySeq[A] = ArraySeq.empty[A]
  }

  implicit val indexedSeqConstructor: SeqConstructor[IndexedSeq] = new SeqConstructor[IndexedSeq] {
    type Builder[A] = scala.collection.mutable.Builder[A, IndexedSeq[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = IndexedSeq.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): IndexedSeq[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): IndexedSeq[A] = Vector.empty
  }

  implicit val seqConstructor: SeqConstructor[collection.immutable.Seq] = new SeqConstructor[collection.immutable.Seq] {
    type Builder[A] = scala.collection.mutable.Builder[A, collection.immutable.Seq[A]]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = collection.immutable.Seq.newBuilder[A]

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    def result[A](builder: Builder[A]): collection.immutable.Seq[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Seq[A] = Nil
  }

  implicit val chunkConstructor: SeqConstructor[Chunk] = new SeqConstructor[Chunk] {
    type Builder[A] = ChunkBuilder[A]

    def newBuilder[A](sizeHint: Int)(implicit ct: ClassTag[A]): Builder[A] = ChunkBuilder.make[A]()

    def add[A](builder: Builder[A], a: A): Unit = builder.addOne(a)

    override def addBoolean(builder: Builder[Boolean], a: Boolean): Unit = builder.addOne(a)
    override def addByte(builder: Builder[Byte], a: Byte): Unit          = builder.addOne(a)
    override def addShort(builder: Builder[Short], a: Short): Unit       = builder.addOne(a)
    override def addInt(builder: Builder[Int], a: Int): Unit             = builder.addOne(a)
    override def addLong(builder: Builder[Long], a: Long): Unit          = builder.addOne(a)
    override def addFloat(builder: Builder[Float], a: Float): Unit       = builder.addOne(a)
    override def addDouble(builder: Builder[Double], a: Double): Unit    = builder.addOne(a)
    override def addChar(builder: Builder[Char], a: Char): Unit          = builder.addOne(a)

    def result[A](builder: Builder[A]): Chunk[A] = builder.result()

    def empty[A](implicit ct: ClassTag[A]): Chunk[A] = Chunk.empty
  }

  implicit val arrayConstructor: SeqConstructor[Array] = new PrimitiveArraySeqConstructor[Array] {
    def result[A](builder: Builder[A]): Array[A] = trimArray(builder)

    def empty[A](implicit ct: ClassTag[A]): Array[A] = Array.empty[A]
  }
}
