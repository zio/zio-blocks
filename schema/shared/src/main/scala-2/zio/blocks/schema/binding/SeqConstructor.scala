package zio.blocks.schema.binding

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import zio.blocks.chunk.{Chunk, ChunkBuilder}

trait SeqConstructor[C[_]] {
  type ObjectBuilder[_]
  type BooleanBuilder
  type ByteBuilder
  type ShortBuilder
  type IntBuilder
  type LongBuilder
  type FloatBuilder
  type DoubleBuilder
  type CharBuilder

  def newObjectBuilder[A](sizeHint: Int = 8): ObjectBuilder[A]

  def newBooleanBuilder(sizeHint: Int = 8): BooleanBuilder

  def newByteBuilder(sizeHint: Int = 8): ByteBuilder

  def newShortBuilder(sizeHint: Int = 8): ShortBuilder

  def newIntBuilder(sizeHint: Int = 8): IntBuilder

  def newLongBuilder(sizeHint: Int = 8): LongBuilder

  def newFloatBuilder(sizeHint: Int = 8): FloatBuilder

  def newDoubleBuilder(sizeHint: Int = 8): DoubleBuilder

  def newCharBuilder(sizeHint: Int = 8): CharBuilder

  def addObject[A](builder: ObjectBuilder[A], a: A): Unit

  def addBoolean(builder: BooleanBuilder, a: Boolean): Unit

  def addByte(builder: ByteBuilder, a: Byte): Unit

  def addShort(builder: ShortBuilder, a: Short): Unit

  def addInt(builder: IntBuilder, a: Int): Unit

  def addLong(builder: LongBuilder, a: Long): Unit

  def addFloat(builder: FloatBuilder, a: Float): Unit

  def addDouble(builder: DoubleBuilder, a: Double): Unit

  def addChar(builder: CharBuilder, a: Char): Unit

  def resultObject[A](builder: ObjectBuilder[A]): C[A]

  def resultBoolean(builder: BooleanBuilder): C[Boolean]

  def resultByte(builder: ByteBuilder): C[Byte]

  def resultShort(builder: ShortBuilder): C[Short]

  def resultInt(builder: IntBuilder): C[Int]

  def resultLong(builder: LongBuilder): C[Long]

  def resultFloat(builder: FloatBuilder): C[Float]

  def resultDouble(builder: DoubleBuilder): C[Double]

  def resultChar(builder: CharBuilder): C[Char]

  def emptyObject[A]: C[A]

  def emptyBoolean: C[Boolean]

  def emptyByte: C[Byte]

  def emptyShort: C[Short]

  def emptyInt: C[Int]

  def emptyLong: C[Long]

  def emptyFloat: C[Float]

  def emptyDouble: C[Double]

  def emptyChar: C[Char]
}

object SeqConstructor {
  abstract class Boxed[C[_]] extends SeqConstructor[C] {
    override type BooleanBuilder = ObjectBuilder[Boolean]
    override type ByteBuilder    = ObjectBuilder[Byte]
    override type ShortBuilder   = ObjectBuilder[Short]
    override type IntBuilder     = ObjectBuilder[Int]
    override type LongBuilder    = ObjectBuilder[Long]
    override type FloatBuilder   = ObjectBuilder[Float]
    override type DoubleBuilder  = ObjectBuilder[Double]
    override type CharBuilder    = ObjectBuilder[Char]

    def newBooleanBuilder(sizeHint: Int): BooleanBuilder = newObjectBuilder(sizeHint)

    def newByteBuilder(sizeHint: Int): ByteBuilder = newObjectBuilder(sizeHint)

    def newShortBuilder(sizeHint: Int): ShortBuilder = newObjectBuilder(sizeHint)

    def newIntBuilder(sizeHint: Int): IntBuilder = newObjectBuilder(sizeHint)

    def newLongBuilder(sizeHint: Int): LongBuilder = newObjectBuilder(sizeHint)

    def newFloatBuilder(sizeHint: Int): FloatBuilder = newObjectBuilder(sizeHint)

    def newDoubleBuilder(sizeHint: Int): DoubleBuilder = newObjectBuilder(sizeHint)

    def newCharBuilder(sizeHint: Int): CharBuilder = newObjectBuilder(sizeHint)

    def addBoolean(builder: BooleanBuilder, a: Boolean): Unit = addObject(builder, a)

    def addByte(builder: ByteBuilder, a: Byte): Unit = addObject(builder, a)

    def addShort(builder: ShortBuilder, a: Short): Unit = addObject(builder, a)

    def addInt(builder: IntBuilder, a: Int): Unit = addObject(builder, a)

    def addLong(builder: LongBuilder, a: Long): Unit = addObject(builder, a)

    def addFloat(builder: FloatBuilder, a: Float): Unit = addObject(builder, a)

    def addDouble(builder: DoubleBuilder, a: Double): Unit = addObject(builder, a)

    def addChar(builder: CharBuilder, a: Char): Unit = addObject(builder, a)

    def resultBoolean(builder: BooleanBuilder): C[Boolean] = resultObject(builder)

    def resultByte(builder: ByteBuilder): C[Byte] = resultObject(builder)

    def resultShort(builder: ShortBuilder): C[Short] = resultObject(builder)

    def resultInt(builder: IntBuilder): C[Int] = resultObject(builder)

    def resultLong(builder: LongBuilder): C[Long] = resultObject(builder)

    def resultFloat(builder: FloatBuilder): C[Float] = resultObject(builder)

    def resultDouble(builder: DoubleBuilder): C[Double] = resultObject(builder)

    def resultChar(builder: CharBuilder): C[Char] = resultObject(builder)

    def emptyBoolean: C[Boolean] = emptyObject

    def emptyByte: C[Byte] = emptyObject

    def emptyShort: C[Short] = emptyObject

    def emptyInt: C[Int] = emptyObject

    def emptyLong: C[Long] = emptyObject

    def emptyFloat: C[Float] = emptyObject

    def emptyDouble: C[Double] = emptyObject

    def emptyChar: C[Char] = emptyObject
  }

  implicit val setConstructor: SeqConstructor[Set] = new Boxed[Set] {
    type ObjectBuilder[A] = scala.collection.mutable.Builder[A, Set[A]]

    def newObjectBuilder[A](sizeHint: Int): ObjectBuilder[A] = Set.newBuilder[A]

    def addObject[A](builder: ObjectBuilder[A], a: A): Unit = builder.addOne(a)

    def resultObject[A](builder: ObjectBuilder[A]): Set[A] = builder.result()

    def emptyObject[A]: Set[A] = Set.empty
  }

  implicit val listConstructor: SeqConstructor[List] = new Boxed[List] {
    type ObjectBuilder[A] = scala.collection.mutable.ListBuffer[A]

    def newObjectBuilder[A](sizeHint: Int): ObjectBuilder[A] = new ListBuffer[A]

    def addObject[A](builder: ObjectBuilder[A], a: A): Unit = builder.addOne(a)

    def resultObject[A](builder: ObjectBuilder[A]): List[A] = builder.toList

    def emptyObject[A]: List[A] = Nil
  }

  implicit val vectorConstructor: SeqConstructor[Vector] = new Boxed[Vector] {
    type ObjectBuilder[A] = scala.collection.mutable.Builder[A, Vector[A]]

    def newObjectBuilder[A](sizeHint: Int): ObjectBuilder[A] = Vector.newBuilder[A]

    def addObject[A](builder: ObjectBuilder[A], a: A): Unit = builder.addOne(a)

    def resultObject[A](builder: ObjectBuilder[A]): Vector[A] = builder.result()

    def emptyObject[A]: Vector[A] = Vector.empty
  }

  abstract class ArraySeqConstructor extends SeqConstructor[ArraySeq] {
    case class Builder[A](var buffer: Array[A], var size: Int)

    type ObjectBuilder[A] = Builder[A]
    type BooleanBuilder   = Builder[Boolean]
    type ByteBuilder      = Builder[Byte]
    type ShortBuilder     = Builder[Short]
    type IntBuilder       = Builder[Int]
    type LongBuilder      = Builder[Long]
    type FloatBuilder     = Builder[Float]
    type DoubleBuilder    = Builder[Double]
    type CharBuilder      = Builder[Char]

    def newBooleanBuilder(sizeHint: Int): BooleanBuilder = new Builder(new Array[Boolean](Math.max(sizeHint, 1)), 0)

    def newByteBuilder(sizeHint: Int): ByteBuilder = new Builder(new Array[Byte](Math.max(sizeHint, 1)), 0)

    def newShortBuilder(sizeHint: Int): ShortBuilder = new Builder(new Array[Short](Math.max(sizeHint, 1)), 0)

    def newIntBuilder(sizeHint: Int): IntBuilder = new Builder(new Array[Int](Math.max(sizeHint, 1)), 0)

    def newLongBuilder(sizeHint: Int): LongBuilder = new Builder(new Array[Long](Math.max(sizeHint, 1)), 0)

    def newFloatBuilder(sizeHint: Int): FloatBuilder = new Builder(new Array[Float](Math.max(sizeHint, 1)), 0)

    def newDoubleBuilder(sizeHint: Int): DoubleBuilder = new Builder(new Array[Double](Math.max(sizeHint, 1)), 0)

    def newCharBuilder(sizeHint: Int): CharBuilder = new Builder(new Array[Char](Math.max(sizeHint, 1)), 0)

    def addBoolean(builder: BooleanBuilder, a: Boolean): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addByte(builder: ByteBuilder, a: Byte): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addShort(builder: ShortBuilder, a: Short): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addInt(builder: IntBuilder, a: Int): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addLong(builder: LongBuilder, a: Long): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addFloat(builder: FloatBuilder, a: Float): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addDouble(builder: DoubleBuilder, a: Double): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addChar(builder: CharBuilder, a: Char): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def resultBoolean(builder: BooleanBuilder): ArraySeq[Boolean] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultByte(builder: ByteBuilder): ArraySeq[Byte] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultShort(builder: ShortBuilder): ArraySeq[Short] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultInt(builder: IntBuilder): ArraySeq[Int] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultLong(builder: LongBuilder): ArraySeq[Long] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultFloat(builder: FloatBuilder): ArraySeq[Float] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultDouble(builder: DoubleBuilder): ArraySeq[Double] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultChar(builder: CharBuilder): ArraySeq[Char] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    val emptyBoolean: ArraySeq[Boolean] = ArraySeq.empty

    val emptyByte: ArraySeq[Byte] = ArraySeq.empty

    val emptyShort: ArraySeq[Short] = ArraySeq.empty

    val emptyInt: ArraySeq[Int] = ArraySeq.empty

    val emptyLong: ArraySeq[Long] = ArraySeq.empty

    val emptyFloat: ArraySeq[Float] = ArraySeq.empty

    val emptyDouble: ArraySeq[Double] = ArraySeq.empty

    val emptyChar: ArraySeq[Char] = ArraySeq.empty
  }

  implicit val arraySeqConstructor: SeqConstructor[ArraySeq] = new ArraySeqConstructor {
    def newObjectBuilder[A](sizeHint: Int): Builder[A] =
      new Builder(new Array[Any](Math.max(sizeHint, 1)).asInstanceOf[Array[A]], 0)

    def addObject[A](builder: Builder[A], a: A): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], idx << 1).asInstanceOf[Array[A]]
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def resultObject[A](builder: Builder[A]): ArraySeq[A] = ArraySeq.unsafeWrapArray {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], size).asInstanceOf[Array[A]]
    }

    def emptyObject[A]: ArraySeq[A] = ArraySeq.unsafeWrapArray(Array.empty[AnyRef]).asInstanceOf[ArraySeq[A]]
  }

  implicit val indexedSeqConstructor: SeqConstructor[IndexedSeq] = new Boxed[IndexedSeq] {
    type ObjectBuilder[A] = scala.collection.mutable.Builder[A, IndexedSeq[A]]

    def newObjectBuilder[A](sizeHint: Int): ObjectBuilder[A] = IndexedSeq.newBuilder[A]

    def addObject[A](builder: ObjectBuilder[A], a: A): Unit = builder.addOne(a)

    def resultObject[A](builder: ObjectBuilder[A]): IndexedSeq[A] = builder.result()

    def emptyObject[A]: IndexedSeq[A] = Vector.empty
  }

  implicit val seqConstructor: SeqConstructor[collection.immutable.Seq] = new Boxed[collection.immutable.Seq] {
    type ObjectBuilder[A] = scala.collection.mutable.Builder[A, collection.immutable.Seq[A]]

    def newObjectBuilder[A](sizeHint: Int): ObjectBuilder[A] = collection.immutable.Seq.newBuilder[A]

    def addObject[A](builder: ObjectBuilder[A], a: A): Unit = builder.addOne(a)

    def resultObject[A](builder: ObjectBuilder[A]): collection.immutable.Seq[A] = builder.result()

    def emptyObject[A]: Seq[A] = Nil
  }

  implicit val chunkConstructor: SeqConstructor[Chunk] = new Boxed[Chunk] {
    type ObjectBuilder[A] = ChunkBuilder[A]

    def newObjectBuilder[A](sizeHint: Int): ObjectBuilder[A] = ChunkBuilder.make[A]()

    def addObject[A](builder: ObjectBuilder[A], a: A): Unit = builder.addOne(a)

    def resultObject[A](builder: ObjectBuilder[A]): Chunk[A] = builder.result()

    def emptyObject[A]: Chunk[A] = Chunk.empty
  }

  abstract class ArrayConstructor extends SeqConstructor[Array] {
    case class Builder[A](var buffer: Array[A], var size: Int)

    type ObjectBuilder[A] = Builder[A]
    type BooleanBuilder   = Builder[Boolean]
    type ByteBuilder      = Builder[Byte]
    type ShortBuilder     = Builder[Short]
    type IntBuilder       = Builder[Int]
    type LongBuilder      = Builder[Long]
    type FloatBuilder     = Builder[Float]
    type DoubleBuilder    = Builder[Double]
    type CharBuilder      = Builder[Char]

    def newBooleanBuilder(sizeHint: Int): BooleanBuilder = new Builder(new Array[Boolean](Math.max(sizeHint, 1)), 0)

    def newByteBuilder(sizeHint: Int): ByteBuilder = new Builder(new Array[Byte](Math.max(sizeHint, 1)), 0)

    def newShortBuilder(sizeHint: Int): ShortBuilder = new Builder(new Array[Short](Math.max(sizeHint, 1)), 0)

    def newIntBuilder(sizeHint: Int): IntBuilder = new Builder(new Array[Int](Math.max(sizeHint, 1)), 0)

    def newLongBuilder(sizeHint: Int): LongBuilder = new Builder(new Array[Long](Math.max(sizeHint, 1)), 0)

    def newFloatBuilder(sizeHint: Int): FloatBuilder = new Builder(new Array[Float](Math.max(sizeHint, 1)), 0)

    def newDoubleBuilder(sizeHint: Int): DoubleBuilder = new Builder(new Array[Double](Math.max(sizeHint, 1)), 0)

    def newCharBuilder(sizeHint: Int): CharBuilder = new Builder(new Array[Char](Math.max(sizeHint, 1)), 0)

    def addBoolean(builder: BooleanBuilder, a: Boolean): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addByte(builder: ByteBuilder, a: Byte): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addShort(builder: ShortBuilder, a: Short): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addInt(builder: IntBuilder, a: Int): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addLong(builder: LongBuilder, a: Long): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addFloat(builder: FloatBuilder, a: Float): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addDouble(builder: DoubleBuilder, a: Double): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def addChar(builder: CharBuilder, a: Char): Unit = {
      var buf = builder.buffer
      val idx = builder.size
      if (buf.length == idx) {
        buf = java.util.Arrays.copyOf(buf, idx << 1)
        builder.buffer = buf
      }
      buf(idx) = a
      builder.size = idx + 1
    }

    def resultBoolean(builder: BooleanBuilder): Array[Boolean] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultByte(builder: ByteBuilder): Array[Byte] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultShort(builder: ShortBuilder): Array[Short] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultInt(builder: IntBuilder): Array[Int] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultLong(builder: LongBuilder): Array[Long] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultFloat(builder: FloatBuilder): Array[Float] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultDouble(builder: DoubleBuilder): Array[Double] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    def resultChar(builder: CharBuilder): Array[Char] = {
      val buf  = builder.buffer
      val size = builder.size
      if (buf.length == size) buf
      else java.util.Arrays.copyOf(buf, size)
    }

    val emptyBoolean: Array[Boolean] = Array.emptyBooleanArray

    val emptyByte: Array[Byte] = Array.emptyByteArray

    val emptyShort: Array[Short] = Array.emptyShortArray

    val emptyInt: Array[Int] = Array.emptyIntArray

    val emptyLong: Array[Long] = Array.emptyLongArray

    val emptyFloat: Array[Float] = Array.emptyFloatArray

    val emptyDouble: Array[Double] = Array.emptyDoubleArray

    val emptyChar: Array[Char] = Array.emptyCharArray
  }
}
