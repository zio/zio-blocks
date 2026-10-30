---
id: write-a-codec-for-a-new-wire-format
title: "How to Write a Codec for a New Wire Format"
description: "Write a BinaryCodec and its Deriver for a format ZIO Blocks doesn't ship, report decode errors with paths, and package it as a BinaryFormat."
keywords:
  - "Custom Codec"
  - "Wire Format"
  - "BinaryCodec"
  - "BinaryFormat"
  - "Deriver"
  - "SchemaError"
---

In this guide, we write a codec for a small binary format that ZIO Blocks does not ship, and we package it as a [`BinaryFormat`](../reference/schema/format.md) so that any type with a `Schema` gets it through `Schema#derive`, exactly like `derive(JsonFormat)`. By the end, an IoT gateway's sensor readings encode to a compact, length-prefixed binary message and decode back, a broken message fails with an error that names the field that broke (such as `.readings.at(1).takenAt`), and per-type overrides with `Deriver#withInstance` work as they do for the built-in formats.

The same steps apply to any format you need to integrate: Protobuf, DynamoDB's `AttributeValue`, a message broker's payload, CBOR or an in-house protocol. We build the format one schema node at a time: primitives, records, variants, collections, wrappers, then the format singleton, error paths, recursion and overrides. The guide uses only public API, so the code works from your own module on every Scala version ZIO Blocks supports.

We cover:

- Defining the codec base class
- Encoding primitives, records, variants, sequences and maps
- Handling wrappers, validation and dynamic values
- Packaging the format and checking the round trip
- Reporting errors with paths
- Encoding recursive types
- Overriding one type with `Deriver#withInstance`

## The Problem

A service that speaks a binary protocol needs an encoder and a decoder for every message type, and without a derivation mechanism each pair is written by hand. Here is the hand-written version for two message types of a sensor gateway:

```scala mdoc:compile-only
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

final case class SensorReading(device: String, takenAt: Instant, celsius: Double)
final case class GatewayBatch(gateway: String, readings: List[SensorReading])

def writeString(s: String, out: ByteBuffer): Unit = {
  val bytes = s.getBytes(UTF_8)
  out.putInt(bytes.length)
  out.put(bytes)
}

def readString(in: ByteBuffer): String = {
  val bytes = new Array[Byte](in.getInt())
  in.get(bytes)
  new String(bytes, UTF_8)
}

def writeReading(r: SensorReading, out: ByteBuffer): Unit = {
  writeString(r.device, out)
  out.putLong(r.takenAt.getEpochSecond)
  out.putInt(r.takenAt.getNano)
  out.putDouble(r.celsius)
}

def readReading(in: ByteBuffer): SensorReading =
  SensorReading(readString(in), Instant.ofEpochSecond(in.getLong(), in.getInt().toLong), in.getDouble())

def writeBatch(b: GatewayBatch, out: ByteBuffer): Unit = {
  writeString(b.gateway, out)
  out.putInt(b.readings.length)
  b.readings.foreach(writeReading(_, out))
}

def readBatch(in: ByteBuffer): GatewayBatch = {
  val gateway = readString(in)
  val count   = in.getInt()
  GatewayBatch(gateway, List.fill(count)(readReading(in)))
}
```

Every new message type needs two more functions, and every new field means editing both of them in the same order; nothing checks that they agree. When a message arrives cut off, `readBatch` throws a `BufferUnderflowException` that says nothing about which reading or which field was missing. A library author is worse off still: a Protobuf or DynamoDB integration has to handle types its users define, which no hand-written function can know about. This guide writes the answer down.

ZIO Blocks already knows the shape of every type that has a `Schema`. A [`Deriver`](../reference/schema/type-class-derivation.md) turns that shape into codecs: we write one function per kind of schema node (primitive, record, variant, sequence, map, wrapper, dynamic value), and derivation combines them for any type.

## Prerequisites

The guide needs only the schema module:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

These imports are used throughout the guide:

```scala mdoc:silent
import java.nio.{BufferUnderflowException, ByteBuffer}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.UUID
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.control.NonFatal
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.codec.{BinaryCodec, BinaryFormat}
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId
```

You should know how to define a schema with `Schema.derived` ([Your First Schema](./your-first-schema.md)) and how a `Deriver` builds a type class from one ([How to Derive Your Own Type Class from a Schema](./derive-your-own-type-class.md)). That guide derives a type class that only renders values; this one adds what a codec needs on top: building values back from bytes and reporting where decoding failed.

## The Wire Format and the Sensor Model

Our format, which we call the packed format, is tag-free: it writes no field names and no type information, only the values in a fixed order, so both sides must use the same schema. Each kind of schema node is laid out as follows:

| Schema node                          | Bytes on the wire                                     |
| ------------------------------------ | ----------------------------------------------------- |
| `Boolean`                            | One byte, `0` or `1`                                  |
| `Int`, `Long`, `Float`, `Double`     | 4, 8, 4 and 8 bytes, big-endian                       |
| `String`                             | An `Int` byte length, then the UTF-8 bytes            |
| `Instant`                            | A `Long` of epoch seconds, then an `Int` of nanos     |
| Record (case class)                  | Each field in declaration order                       |
| Variant (sealed trait, `Option`)     | An `Int` case index, then the case                    |
| Sequence (`List`, `Vector`, ...)     | An `Int` element count, then each element             |
| Map                                  | An `Int` entry count, then each key and its value     |
| Wrapper (`Schema#transform`)         | The wrapped value                                     |

The messages come from a gateway that collects sensor readings. A `DeviceId` is a validated newtype, a `Measurement` is a sealed trait with a case object, a `Batch` holds a list and a map, and a `Zone` is a recursive tree that describes the site:

```scala mdoc:silent
final case class DeviceId(value: String)

object DeviceId {
  implicit val schema: Schema[DeviceId] = Schema[String].transform(
    id =>
      if (id.nonEmpty && id.length <= 16) DeviceId(id)
      else throw SchemaError.validationFailed(s"device id must be 1 to 16 characters, got ${id.length}"),
    _.value
  )
}

sealed trait Measurement
final case class Temperature(celsius: Double) extends Measurement
final case class Humidity(percent: Float)     extends Measurement
case object DoorOpened                        extends Measurement

object Measurement {
  implicit val schema: Schema[Measurement] = Schema.derived
}

final case class Reading(device: DeviceId, takenAt: Instant, measurement: Measurement, battery: Option[Int])

object Reading {
  implicit val schema: Schema[Reading] = Schema.derived
}

final case class Batch(gateway: String, readings: List[Reading], calibration: Map[String, Double])

object Batch {
  implicit val schema: Schema[Batch] = Schema.derived

  val sample: Batch = Batch(
    gateway = "gw-north",
    readings = List(
      Reading(DeviceId("t-17"), Instant.parse("2026-10-29T08:00:00Z"), Temperature(21.5), Some(87)),
      Reading(DeviceId("h-02"), Instant.parse("2026-10-29T08:00:05Z"), Humidity(41.0f), None),
      Reading(DeviceId("d-01"), Instant.parse("2026-10-29T08:00:09Z"), DoorOpened, Some(12))
    ),
    calibration = Map("t-17" -> -0.4, "h-02" -> 1.5)
  )
}

final case class Zone(name: String, devices: Vector[DeviceId], subzones: List[Zone])

object Zone {
  implicit val schema: Schema[Zone] = Schema.derived
}
```

None of these types mentions the packed format. The schemas are all the codec needs.

## Defining the Codec Base Class

Every format's codec extends [`BinaryCodec[A]`](../reference/schema/codec.md), which fixes the contract that `Schema#derive`, `Schema#encode` and `Schema#decode` rely on: `BinaryCodec#encode` writes a value into a `ByteBuffer` the caller provides, and `BinaryCodec#decode` returns `Either[SchemaError, A]`. Our base class adds `PackedCodec#write` and `PackedCodec#read`, which every node's codec implements. `PackedCodec#read` throws on bad input instead of returning `Either`, so a record does not allocate an `Either` per field, and an error travels up through the nested codecs, where each one adds its part of the path. `PackedCodec#decode` catches it once, at the top:

```scala mdoc:silent
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
```

[`SchemaError`](../reference/schema/schema-error.md) is itself an exception, so `PackedCodec.fail` throws one directly. `SchemaError.expectationMismatch(Nil, ...)` creates an error at the root path; the codecs of records, variants and collections add the path segments as the error passes through them. `PackedCodec.toSchemaError` turns the exceptions a `ByteBuffer` throws, such as `BufferUnderflowException` on cut-off input, into a `SchemaError`. `PackedCodec#encodeToBuffer` is a convenience for the examples: it allocates a fixed-size buffer, so a value larger than `capacity` throws `BufferOverflowException`.

## Encoding Primitives

A `Deriver[PackedCodec]` has one method for each kind of schema node. To keep each section to one kind of node, we write the deriver as a chain of traits, one per section, and finish it with an `object` that extends the last trait. In your own module, you can put all seven methods in one object instead.

The primitive codecs read and write fixed-width values and length-prefixed strings:

```scala mdoc:silent
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
```

`Deriver#derivePrimitive` receives a `PrimitiveType` that says which primitive the node is, and returns its codec wrapped in `Lazy`:

```scala mdoc:silent
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
```

ZIO Blocks has 30 primitive types; the packed format supports the seven our messages use and rejects the rest. Derivation calls `Deriver#derivePrimitive` while `Schema#derive` walks the schema, and we choose the codec before wrapping it in `Lazy`, so a type with an unsupported primitive fails when its codec is derived, not when the first message arrives. This holds for a primitive behind a `Reflect.Deferred` node too, such as a recursive field or a field with a default value: the packed format's `Schema#derive` fails for it as well.

## Encoding Records Field by Field

A record's `Binding.Record` holds a `Constructor`, which builds a value from a `Registers` buffer, and a `Deconstructor`, which writes a value's fields into one. `Registers` stores primitive fields without boxing them, so each field has a fixed slot whose position depends on the types of the fields before it. `Reflect.Record.registers` computes those slots from the field nodes, one `Register` per field, in the layout the binding uses, and `Reflect.Record.usedRegisters` returns the size of the buffer they need.

`Deriver#deriveRecord` receives the fields as `Term`s, each with its name and its node. `Deriver#instance` reads a field's codec from the node's metadata:

```scala mdoc:silent
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
```

Encoding deconstructs the value into a fresh `Registers` buffer and writes each field with its codec. Decoding reads each field into its register, then calls the constructor once. Two details make the codec correct for every record. The field codecs live in a `lazy val`, so they are looked up on the first `PackedCodec#write` or `PackedCodec#read` and never during derivation; that is what lets a recursive type such as `Zone` derive without looping. And each field read sits in a `try` that rethrows the error with `SchemaError#atField`, which prepends the field's name to the error's path.

:::note
The Avro module's deriver computes register offsets with `Reflect.registerOffset` and `Reflect.typeTag`. Both are `private[blocks]`, so code outside ZIO Blocks cannot call them. If you use the Avro source as a model, use the public `Reflect.Record.registers` and `Reflect.Record.usedRegisters` instead, as above.
:::

## Encoding Variants and Options

A variant's `Binding.Variant` has a `Discriminator` that returns the index of the case a value belongs to. Each case is a `Term` whose node is the case's own record (or wrapper), so its codec already exists. The variant writes the index, then lets the case's codec write the value:

```scala mdoc:silent
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
```

A case object such as `DoorOpened` is a record with no fields, so it encodes as its index alone. `Option` needs no code of its own: its schema is a variant with the cases `None` (index 0) and `Some` (index 1), so `battery: Option[Int]` encodes as `0`, or as `1` followed by the `Int`. A decode error inside a case gets the case's name added with `SchemaError#atCase`, and an index outside the cases fails before any case is read.

## Encoding Sequences and Maps

A sequence's `Binding.Seq` has a `SeqConstructor`, which builds the collection through a mutable builder, and a `SeqDeconstructor`, which gives its size and an iterator over its elements. One binding serves every collection type, from `List` to `Array`. The array-backed ones need a `ClassTag` of the element type to allocate their array, which `TypeId#classTag` provides:

```scala mdoc:silent
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
```

Here the child codecs come from `Lazy#map` and `Lazy#zip` instead of a `lazy val`: a `Lazy` is evaluated only when forced and remembers its result, so the element's codec is still looked up on first use. The size hint passed to the builder is capped by the bytes left in the buffer, so a corrupt count cannot make the decoder allocate a huge collection. An element error gets its index with `SchemaError#atIndex`; a map value error gets its key with `SchemaError#atKey`, which takes the key as a `DynamicValue` that `Reflect#toDynamicValue` produces.

## Handling Wrappers and Dynamic Values

A wrapper node comes from `Schema#transform` or a newtype: its `Binding.Wrapper` converts between the wrapper type and the wrapped one. The packed format writes the wrapped value as it is. The last trait in the chain becomes the deriver itself:

```scala mdoc:silent
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
```

The wrapper's codec needs no error handling of its own. `DeviceId`'s schema throws a `SchemaError` from the `Binding.Wrapper#wrap` function when an id is invalid; the exception passes through the wrapper's `PackedCodec#read` unchanged, and the record that holds the `DeviceId` adds the field name to it. A wrap function that throws another exception, such as an `IllegalArgumentException` from `require`, becomes a `SchemaError` with the exception's message in `PackedCodec.toSchemaError`.

`Deriver#deriveDynamic` handles `DynamicValue`, the node for a part of a schema that accepts any value. A tag-free format cannot encode a value whose shape the schema does not fix, so the packed format rejects it; like `Deriver#derivePrimitive`, it runs while `Schema#derive` walks the schema, so the failure comes from `Schema#derive`. A self-describing format would encode the `DynamicValue` instead, as the Avro module does.

## Deriving Codecs and Checking the Round Trip

A format singleton ties the codec type, a MIME type and the deriver together. Extending `BinaryFormat` also brings the implicit that lets `Schema#derive` accept the format:

```scala mdoc:silent
object PackedFormat extends BinaryFormat[PackedCodec]("application/x-packed", PackedCodecDeriver)
```

Now every type with a schema gets a packed codec through `Schema#derive`, as with `JsonFormat`. `Schema#getInstance`, `Schema#encode` and `Schema#decode` accept `PackedFormat` too:

```scala mdoc:silent
val batchCodec: PackedCodec[Batch] = Batch.schema.derive(PackedFormat)
val batchBytes: ByteBuffer         = batchCodec.encodeToBuffer(Batch.sample)
```

The whole batch, with three readings and two calibration entries, takes 156 bytes, and it decodes back to the value we started with:

```scala mdoc
println(s"${PackedFormat.mimeType}: ${batchBytes.remaining} bytes")
println(batchCodec.decode(batchBytes.duplicate()))
```

A round-trip check belongs in the test suite of every format: encode a value, decode it, and compare. It also checks that decoding consumed every byte, which catches an encoder that writes something the decoder never reads:

```scala mdoc:silent
def roundTrips[A](codec: PackedCodec[A], value: A): Boolean = {
  val bytes = codec.encodeToBuffer(value)
  codec.decode(bytes) == Right(value) && !bytes.hasRemaining
}

val readingCodec: PackedCodec[Reading] = Reading.schema.derive(PackedFormat)
```

The sample readings cover every case of `Measurement` and both cases of `Option`:

```scala mdoc
Batch.sample.readings.foreach { reading =>
  println(s"${reading.measurement} round-trips: ${roundTrips(readingCodec, reading)}")
}
```

<details>
  <summary>The whole format in one file, as the example files use it: <code>PackedFormat.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/customwireformat/PackedFormat.scala:show-line-numbers
```

</details>

<details>
  <summary>Runnable version, with the model and the format from the shared files: <code>Step10RoundTripExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/customwireformat/Step10RoundTripExample.scala:show-line-numbers
```

</details>

## Reporting Errors with Paths

Every `try` around a child's `PackedCodec#read` adds one segment to the error's path, so a decode error names the exact place where it happens. A message cut off after 70 bytes fails inside the second reading:

```scala mdoc:silent
val truncated: ByteBuffer = batchCodec.encodeToBuffer(Batch.sample)
truncated.limit(70)
```

The error says where the bytes ran out:

```scala mdoc
println(batchCodec.decode(truncated))
```

The encoder does not validate, so a sender built without the `DeviceId` check can send an id that is too long:

```scala mdoc:silent
val longIdBatch = Batch.sample.copy(readings = List(Batch.sample.readings.head.copy(device = DeviceId("north-wing-probe-07"))))
```

The receiver's decoder runs the check through `Binding.Wrapper#wrap`, and the record adds the path:

```scala mdoc
println(batchCodec.decode(batchCodec.encodeToBuffer(longIdBatch)))
```

In the first reading, the case index of `measurement` starts at byte 20, after the device id (8 bytes) and the timestamp (12 bytes). Overwriting it with an index the variant does not have gives an error at that field:

```scala mdoc:silent
val corrupted: ByteBuffer = readingCodec.encodeToBuffer(Batch.sample.readings.head)
corrupted.putInt(20, 7)
```

The variant rejects the index before reading any case:

```scala mdoc
println(readingCodec.decode(corrupted))
```

An unsupported type fails earlier, when its codec is derived. The packed format has no codec for `UUID`, so a firmware update message with a `UUID` field cannot get one:

```scala mdoc
final case class FirmwareUpdate(device: DeviceId, release: UUID)

object FirmwareUpdate {
  implicit val schema: Schema[FirmwareUpdate] = Schema.derived
}

println(Try(FirmwareUpdate.schema.derive(PackedFormat)))
```

<details>
  <summary>Runnable version, with the codec of <code>Batch</code> from the round-trip section: <code>Step11ErrorsExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/customwireformat/Step11ErrorsExample.scala:show-line-numbers
```

</details>

## Encoding Recursive Types

A `Zone` holds a list of `Zone`s, so its schema refers to itself. The deriver needs no extra code: every codec looks up its children on first use, through a `lazy val` or a `Lazy`, so by the time the `subzones` codec asks for the `Zone` codec, that codec already exists. A site with two floors and a cold room on the first floor round-trips:

```scala mdoc:silent
val site = Zone(
  "north-site",
  Vector(DeviceId("gw-north")),
  List(
    Zone("floor-1", Vector(DeviceId("t-17"), DeviceId("h-02")), List(Zone("cold-room", Vector(DeviceId("t-18")), Nil))),
    Zone("floor-2", Vector(DeviceId("d-01")), Nil)
  )
)
val zoneCodec: PackedCodec[Zone] = Zone.schema.derive(PackedFormat)
```

Decoding the encoded site gives back the whole tree:

```scala mdoc
println(zoneCodec.decode(zoneCodec.encodeToBuffer(site)))
```

:::warning
Never force a child's instance inside a `derive*` method itself, for example with `D.instance(field.value.metadata).force` outside the codec. For a recursive type, that derives the same type again, without end, until the stack overflows.
:::

<details>
  <summary>Runnable version, with the model and the format from the shared files: <code>Step12RecursiveTypesExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/customwireformat/Step12RecursiveTypesExample.scala:show-line-numbers
```

</details>

## Overriding One Type with `Deriver#withInstance`

Because the format's deriver is an ordinary `Deriver`, users of the format get the same override mechanism as with the built-in formats, without any work from the format's author. Suppose a gateway's firmware sends timestamps as epoch milliseconds. A hand-written `PackedCodec[Instant]` registered with `Deriver#withInstance` replaces the derived one for every `Instant`, at any depth:

```scala mdoc:silent
val epochMillis: PackedCodec[Instant] = new PackedCodec[Instant] {
  def write(value: Instant, out: ByteBuffer): Unit = out.putLong(value.toEpochMilli)
  def read(in: ByteBuffer): Instant                = Instant.ofEpochMilli(in.getLong())
}
val millisDeriver: Deriver[PackedCodec]       = PackedFormat.deriver.withInstance[Instant](epochMillis)
val compactReadingCodec: PackedCodec[Reading] = Reading.schema.derive(millisDeriver)

val firstReading = Batch.sample.readings.head
```

Each timestamp now takes 8 bytes instead of 12, and the reading still decodes:

```scala mdoc
println(s"default: ${Reading.schema.derive(PackedFormat).encodeToBuffer(firstReading).remaining} bytes")
println(s"epoch millis: ${compactReadingCodec.encodeToBuffer(firstReading).remaining} bytes")
println(compactReadingCodec.decode(compactReadingCodec.encodeToBuffer(firstReading)))
```

The override drops sub-millisecond precision, which these timestamps do not have. `Schema#derive(PackedFormat)`, `Schema#getInstance`, `Schema#encode` and `Schema#decode` keep using the format's default deriver, so pass the configured deriver to `Schema#derive` wherever the override should apply.

<details>
  <summary>Runnable version, with the model and the format from the shared files: <code>Step13OverrideExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/customwireformat/Step13OverrideExample.scala:show-line-numbers
```

</details>

## Putting It Together

The complete example runs a gateway's batch through the packed format: it derives the codecs, encodes and decodes the batch, rejects a cut-off message with a path, round-trips the zone tree and sends timestamps as epoch milliseconds. It differs from the sections in three ways: it cuts the already encoded batch to 70 bytes with `ByteBuffer#duplicate` instead of encoding it again, it prints only whether the zone tree round-trips instead of the whole tree, and it applies the epoch-milliseconds override to the whole `Batch` instead of one `Reading`. The format and the model come from `PackedFormat.scala` and `SensorModel.scala`:

```scala mdoc:embed:schema-examples/src/main/scala/customwireformat/CompleteExample.scala
```

Running it prints:

```text
application/x-packed: 156 bytes
Right(Batch(gw-north,List(Reading(DeviceId(t-17),2026-10-29T08:00:00Z,Temperature(21.5),Some(87)), Reading(DeviceId(h-02),2026-10-29T08:00:05Z,Humidity(41.0),None), Reading(DeviceId(d-01),2026-10-29T08:00:09Z,DoorOpened,Some(12))),Map(t-17 -> -0.4, h-02 -> 1.5)))
Left(zio.blocks.schema.SchemaError: Unexpected end of input at: .readings.at(1).takenAt)
zone tree round-trips: true
epoch millis: 144 bytes
```

## Running the Examples

All runnable code from this guide is in [`schema-examples/src/main/scala/customwireformat/`](https://github.com/zio/zio-blocks/tree/main/schema-examples/src/main/scala/customwireformat). Run any file from that folder with `scala-cli run <File>.scala`, or from `schema-examples` with `sbt "runMain customwireformat.<ObjectName>"`.

## Going Further

The packed format is deliberately small. A production format usually adds the following, and the built-in formats show how:

- **Schema evolution.** The format writes no field names or tags, so adding, removing or reordering a field breaks every reader that uses the old schema. Avro writes a schema next to the data, and Protobuf numbers its fields; see [Schema Evolution](../reference/schema/schema-evolution/index.md) for what ZIO Blocks offers on the schema side.
- **Modifiers and defaults.** The deriver ignores the `modifiers` and `defaultValue` it receives, so `Modifier.rename` and default values change nothing. A format with field names reads them here, as `JsonCodecDeriver` does.
- **Performance.** Every field passes through `Register[Any]`, which boxes primitive values, and every element of a primitive array is boxed too. The built-in codecs write specialized code for each primitive type and for primitive arrays.
- **Buffer sizing.** `PackedCodec#encodeToBuffer` allocates a fixed capacity. A real format computes the encoded size first or writes to a growing buffer.
- **The remaining primitives and `DynamicValue`.** The format supports seven primitive types and no `DynamicValue`.

The pages below cover the pieces this guide builds on:

- The Avro module is the full-scale version of this guide: [`AvroCodecDeriver.scala`](https://github.com/zio/zio-blocks/blob/main/schema-avro/src/main/scala/zio/blocks/schema/avro/AvroCodecDeriver.scala) handles every primitive type, specializes primitive collections and encodes `DynamicValue`.
- [Type-Class Derivation](../reference/schema/type-class-derivation.md) explains every `Deriver` method and its parameters, and how instance and modifier overrides combine.
- [Register System](../reference/schema/registers.md) and [Binding](../reference/schema/binding.md) document the storage and the constructors that record codecs use.
- [SchemaError](../reference/schema/schema-error.md) lists the error kinds and the `at*` methods that build paths.
- [Codec](../reference/schema/codec.md) and [Format](../reference/schema/format.md) cover using codecs once you have them.
- [How to Override the Codec for One Type Everywhere](./override-codec-for-one-type.md) replaces one type's codec inside an existing format, without writing a new format.
