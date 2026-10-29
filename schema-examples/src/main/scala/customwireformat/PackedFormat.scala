package customwireformat

import java.nio.{BufferUnderflowException, ByteBuffer}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.codec.{BinaryCodec, BinaryFormat}
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

/**
 * How to Write a Codec for a New Wire Format — The Packed Format
 *
 * The codec base class, the primitive codecs, the deriver (one trait per
 * section, from "Defining the Codec Base Class" to "Handling Wrappers and
 * Dynamic Values") and the format singleton. It has no main method.
 */
abstract class PackedCodec[A] extends BinaryCodec[A] {
  def write(value: A, out: ByteBuffer): Unit
  def read(in: ByteBuffer): A

  final def encode(value: A, output: ByteBuffer): Unit = write(value, output)

  final def decode(input: ByteBuffer): Either[SchemaError, A] =
    try Right(read(input))
    catch { case e if NonFatal(e) => Left(PackedCodec.toSchemaError(e)) }

  final def encodeToBuffer(value: A, capacity: Int = 1024): ByteBuffer = {
    val buffer = ByteBuffer.allocate(capacity)
    write(value, buffer)
    buffer.flip()
    buffer
  }
}

object PackedCodec {
  def fail(expectation: String): Nothing = throw SchemaError.expectationMismatch(Nil, expectation)

  def toSchemaError(e: Throwable): SchemaError = e match {
    case error: SchemaError          => error
    case _: BufferUnderflowException => SchemaError.expectationMismatch(Nil, "Unexpected end of input")
    case other                       => SchemaError.expectationMismatch(Nil, String.valueOf(other.getMessage))
  }

  def readCount(in: ByteBuffer): Int = {
    val count = in.getInt()
    if (count < 0) fail(s"Expected a non-negative count, got $count")
    count
  }
}

object PackedPrimitives {
  val boolean: PackedCodec[Boolean] = new PackedCodec[Boolean] {
    def write(value: Boolean, out: ByteBuffer): Unit = out.put(if (value) 1.toByte else 0.toByte)
    def read(in: ByteBuffer): Boolean                = in.get() match {
      case 0 => false
      case 1 => true
      case b => PackedCodec.fail(s"Expected a boolean byte 0 or 1, got $b")
    }
  }

  val int: PackedCodec[Int] = new PackedCodec[Int] {
    def write(value: Int, out: ByteBuffer): Unit = out.putInt(value)
    def read(in: ByteBuffer): Int                = in.getInt()
  }

  val long: PackedCodec[Long] = new PackedCodec[Long] {
    def write(value: Long, out: ByteBuffer): Unit = out.putLong(value)
    def read(in: ByteBuffer): Long                = in.getLong()
  }

  val float: PackedCodec[Float] = new PackedCodec[Float] {
    def write(value: Float, out: ByteBuffer): Unit = out.putFloat(value)
    def read(in: ByteBuffer): Float                = in.getFloat()
  }

  val double: PackedCodec[Double] = new PackedCodec[Double] {
    def write(value: Double, out: ByteBuffer): Unit = out.putDouble(value)
    def read(in: ByteBuffer): Double                = in.getDouble()
  }

  val string: PackedCodec[String] = new PackedCodec[String] {
    def write(value: String, out: ByteBuffer): Unit = {
      val bytes = value.getBytes(UTF_8)
      out.putInt(bytes.length)
      out.put(bytes)
    }
    def read(in: ByteBuffer): String = {
      val length = PackedCodec.readCount(in)
      if (length > in.remaining) PackedCodec.fail(s"Expected $length bytes of text, got ${in.remaining}")
      val bytes = new Array[Byte](length)
      in.get(bytes)
      new String(bytes, UTF_8)
    }
  }

  val instant: PackedCodec[Instant] = new PackedCodec[Instant] {
    def write(value: Instant, out: ByteBuffer): Unit = {
      out.putLong(value.getEpochSecond)
      out.putInt(value.getNano)
    }
    def read(in: ByteBuffer): Instant = Instant.ofEpochSecond(in.getLong(), in.getInt().toLong)
  }
}

trait PrimitiveDerivation extends Deriver[PackedCodec] {
  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[PackedCodec[A]] = {
    val codec = primitiveType match {
      case _: PrimitiveType.Boolean => PackedPrimitives.boolean
      case _: PrimitiveType.Int     => PackedPrimitives.int
      case _: PrimitiveType.Long    => PackedPrimitives.long
      case _: PrimitiveType.Float   => PackedPrimitives.float
      case _: PrimitiveType.Double  => PackedPrimitives.double
      case _: PrimitiveType.String  => PackedPrimitives.string
      case _: PrimitiveType.Instant => PackedPrimitives.instant
      case _                        =>
        throw new UnsupportedOperationException(s"The packed format has no codec for ${typeId.name}")
    }
    Lazy(codec.asInstanceOf[PackedCodec[A]])
  }
}

trait RecordDerivation extends PrimitiveDerivation {
  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PackedCodec[A]] = {
    val names         = fields.map(_.name).toArray
    val registers     = Reflect.Record.registers(fields.map(_.value).toArray)
    val usedRegisters = Reflect.Record.usedRegisters(registers)
    Lazy {
      new PackedCodec[A] {
        private lazy val fieldCodecs: Array[PackedCodec[Any]] =
          fields.map(field => D.instance(field.value.metadata).force.asInstanceOf[PackedCodec[Any]]).toArray

        def write(value: A, out: ByteBuffer): Unit = {
          val regs = Registers(usedRegisters)
          binding.deconstructor.deconstruct(regs, RegisterOffset.Zero, value)
          fieldCodecs.indices.foreach { i =>
            fieldCodecs(i).write(registers(i).get(regs, RegisterOffset.Zero), out)
          }
        }

        def read(in: ByteBuffer): A = {
          val regs = Registers(usedRegisters)
          fieldCodecs.indices.foreach { i =>
            val fieldValue =
              try fieldCodecs(i).read(in)
              catch { case e if NonFatal(e) => throw PackedCodec.toSchemaError(e).atField(names(i)) }
            registers(i).set(regs, RegisterOffset.Zero, fieldValue)
          }
          binding.constructor.construct(regs, RegisterOffset.Zero)
        }
      }
    }
  }
}

trait VariantDerivation extends RecordDerivation {
  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PackedCodec[A]] = {
    val names = cases.map(_.name).toArray
    Lazy {
      new PackedCodec[A] {
        private lazy val caseCodecs: Array[PackedCodec[A]] =
          cases.map(c => D.instance(c.value.metadata).force.asInstanceOf[PackedCodec[A]]).toArray

        def write(value: A, out: ByteBuffer): Unit = {
          val index = binding.discriminator.discriminate(value)
          out.putInt(index)
          caseCodecs(index).write(value, out)
        }

        def read(in: ByteBuffer): A = {
          val index = in.getInt()
          if (index < 0 || index >= caseCodecs.length)
            PackedCodec.fail(s"Expected a case index from 0 to ${caseCodecs.length - 1}, got $index")
          try caseCodecs(index).read(in)
          catch { case e if NonFatal(e) => throw PackedCodec.toSchemaError(e).atCase(names(index)) }
        }
      }
    }
  }
}

trait CollectionDerivation extends VariantDerivation {
  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PackedCodec[C[A]]] = {
    implicit val elementClassTag: ClassTag[A] = element.typeId.classTag.asInstanceOf[ClassTag[A]]
    D.instance(element.metadata).map { elementCodec =>
      new PackedCodec[C[A]] {
        def write(value: C[A], out: ByteBuffer): Unit = {
          out.putInt(binding.deconstructor.size(value))
          binding.deconstructor.deconstruct(value).foreach(elementCodec.write(_, out))
        }

        def read(in: ByteBuffer): C[A] = {
          val count   = PackedCodec.readCount(in)
          val builder = binding.constructor.newBuilder[A](math.min(count, in.remaining))
          (0 until count).foreach { i =>
            val elem =
              try elementCodec.read(in)
              catch { case e if NonFatal(e) => throw PackedCodec.toSchemaError(e).atIndex(i) }
            binding.constructor.add(builder, elem)
          }
          binding.constructor.result(builder)
        }
      }
    }
  }

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PackedCodec[M[K, V]]] =
    D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (keyCodec, valueCodec) =>
      new PackedCodec[M[K, V]] {
        def write(map: M[K, V], out: ByteBuffer): Unit = {
          val entries = binding.deconstructor
          out.putInt(entries.size(map))
          entries.deconstruct(map).foreach { entry =>
            keyCodec.write(entries.getKey(entry), out)
            valueCodec.write(entries.getValue(entry), out)
          }
        }

        def read(in: ByteBuffer): M[K, V] = {
          val count   = PackedCodec.readCount(in)
          val builder = binding.constructor.newObjectBuilder[K, V](math.min(count, in.remaining))
          (0 until count).foreach { i =>
            val k =
              try keyCodec.read(in)
              catch { case e if NonFatal(e) => throw PackedCodec.toSchemaError(e).atIndex(i) }
            val v =
              try valueCodec.read(in)
              catch { case e if NonFatal(e) => throw PackedCodec.toSchemaError(e).atKey(key.toDynamicValue(k)) }
            binding.constructor.addObject(builder, k, v)
          }
          binding.constructor.resultObject(builder)
        }
      }
    }
}

object PackedCodecDeriver extends CollectionDerivation {
  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PackedCodec[A]] =
    D.instance(wrapped.metadata).map { inner =>
      new PackedCodec[A] {
        def write(value: A, out: ByteBuffer): Unit = inner.write(binding.unwrap(value), out)
        def read(in: ByteBuffer): A                = binding.wrap(inner.read(in))
      }
    }

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PackedCodec[DynamicValue]] =
    throw new UnsupportedOperationException("The packed format does not encode DynamicValue")
}

object PackedFormat extends BinaryFormat[PackedCodec]("application/x-packed", PackedCodecDeriver)
