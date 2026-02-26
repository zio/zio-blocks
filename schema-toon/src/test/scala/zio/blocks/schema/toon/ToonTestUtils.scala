package zio.blocks.schema.toon

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq
import scala.util.Try

object ToonTestUtils {
  def roundTrip[A](value: A, expectedToon: String)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedToon, getOrDeriveCodec(schema))

  def roundTrip[A](value: A, expectedToon: String, readerConfig: ReaderConfig, writerConfig: WriterConfig)(implicit
    schema: Schema[A]
  ): TestResult = roundTrip(value, expectedToon, getOrDeriveCodec(schema), readerConfig, writerConfig)

  def roundTrip[A](
    value: A,
    expectedToon: String,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig = readerConfig,
    writerConfig: WriterConfig = writerConfig
  ): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer, writerConfig)
    val encodedBySchema1 = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    val directByteBuffer = ByteBuffer.allocateDirect(maxBufSize)
    codec.encode(value, directByteBuffer, writerConfig)
    val encodedBySchema2 = util.Arrays.copyOf(
      {
        val dup = directByteBuffer.duplicate()
        val out = new Array[Byte](dup.position)
        dup.position(0)
        dup.get(out)
        out
      },
      directByteBuffer.position
    )
    val output = new java.io.ByteArrayOutputStream(maxBufSize)
    codec.encode(value, output, writerConfig)
    output.close()
    val encodedBySchema3 = output.toByteArray
    val encodedBySchema4 = codec.encode(value, writerConfig)
    val encodedBySchema5 = codec.encodeToString(value, writerConfig).getBytes(UTF_8)
    assert(new String(encodedBySchema1, UTF_8))(equalTo(expectedToon)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema5))) &&
    assert(codec.decode(encodedBySchema1, readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(new String(encodedBySchema1, UTF_8), readerConfig))(isRight(equalTo(value)))
  }

  def decode[A](toon: String, expectedValue: A)(implicit schema: Schema[A]): TestResult =
    decode(toon, expectedValue, getOrDeriveCodec(schema))

  def decode[A](toon: String, expectedValue: A, readerConfig: ReaderConfig)(implicit schema: Schema[A]): TestResult =
    decode(toon, expectedValue, getOrDeriveCodec(schema), readerConfig)

  def decode[A](
    toon: String,
    expectedValue: A,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig = readerConfig
  ): TestResult = {
    val toonBytes = toon.getBytes(UTF_8)
    assert(codec.decode(toonBytes, readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toInputStream(toonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toHeapByteBuffer(toonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toDirectByteBuffer(toonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toon, readerConfig))(isRight(equalTo(expectedValue)))
  }

  def record(fields: (String, DynamicValue)*): DynamicValue.Record = DynamicValue.Record(Chunk.from(fields))

  def dynamicUnit: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Unit)

  def dynamicBoolean(v: Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(v))

  def dynamicInt(v: Int): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(v))

  def dynamicStr(v: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(v))

  def decodeDynamic(
    toon: String,
    expected: DynamicValue,
    readerConfig: ReaderConfig = ReaderConfig
  ): TestResult = {
    val codec = ToonBinaryCodec.dynamicValueCodec
    assert(codec.decode(toon, readerConfig))(isRight(equalTo(expected)))
  }

  def decodeDynamicError(toon: String, error: String, readerConfig: ReaderConfig = ReaderConfig): TestResult = {
    val codec = ToonBinaryCodec.dynamicValueCodec
    assert(codec.decode(toon, readerConfig))(isLeft(hasError(error)))
  }

  def encodeDynamic(
    value: DynamicValue,
    expectedToon: String,
    writerConfig: WriterConfig = WriterConfig
  ): TestResult = {
    val codec  = ToonBinaryCodec.dynamicValueCodec
    val result = codec.encodeToString(value, writerConfig)
    assert(result)(equalTo(expectedToon))
  }

  def decodeError[A](invalidToon: String, error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(invalidToon.getBytes(UTF_8), error)

  def decodeError[A](invalidToon: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(invalidToon, error, getOrDeriveCodec(schema))

  def decodeError[A](invalidToon: String, error: String, codec: ToonBinaryCodec[A]): TestResult =
    decodeError(invalidToon.getBytes(UTF_8), error, codec)

  def decodeError[A](
    invalidToon: String,
    error: String,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig
  ): TestResult =
    decodeError(invalidToon.getBytes(UTF_8), error, codec, readerConfig)

  def decodeError[A](invalidToon: Array[Byte], error: String, codec: ToonBinaryCodec[A]): TestResult =
    decodeError(invalidToon, error, codec, ReaderConfig)

  def decodeError[A](
    invalidToon: Array[Byte],
    error: String,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig
  ): TestResult =
    assert(codec.decode(invalidToon, readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toInputStream(invalidToon), readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toHeapByteBuffer(invalidToon), readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toDirectByteBuffer(invalidToon), readerConfig))(isLeft(hasError(error))) &&
      {
        if (error.startsWith("malformed byte(s)") || error.startsWith("illegal surrogate")) assertTrue(true)
        else assert(codec.decode(new String(invalidToon, UTF_8), readerConfig))(isLeft(hasError(error)))
      }

  def encode[A](value: A, expectedToon: String)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedToon, getOrDeriveCodec(schema))

  def encode[A](value: A, expectedToon: String, writerConfig: WriterConfig)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedToon, getOrDeriveCodec(schema), writerConfig)

  def encode[A](
    value: A,
    expectedToon: String,
    codec: ToonBinaryCodec[A],
    writerConfig: WriterConfig = writerConfig
  ): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer, writerConfig)
    val encodedBySchema1 = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    val directByteBuffer = ByteBuffer.allocateDirect(maxBufSize)
    codec.encode(value, directByteBuffer, writerConfig)
    val encodedBySchema2 = util.Arrays.copyOf(
      {
        val dup = directByteBuffer.duplicate()
        val out = new Array[Byte](dup.position)
        dup.position(0)
        dup.get(out)
        out
      },
      directByteBuffer.position
    )
    val output = new java.io.ByteArrayOutputStream(maxBufSize)
    codec.encode(value, output, writerConfig)
    output.close()
    val encodedBySchema3 = output.toByteArray
    val encodedBySchema4 = codec.encode(value, writerConfig)
    val encodedBySchema5 = codec.encodeToString(value, writerConfig).getBytes(UTF_8)
    assert(new String(encodedBySchema1, UTF_8))(equalTo(expectedToon)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema5)))
  }

  def encodeError[A](value: A, error: String)(implicit schema: Schema[A]): TestResult = {
    val codec = getOrDeriveCodec(schema)
    assert(Try(codec.encode(value)).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocate(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocateDirect(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, new java.io.ByteArrayOutputStream(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encodeToString(value)).toEither)(isLeft(hasError(error)))
  }

  def encodeError[A](value: A, error: String, writerConfig: WriterConfig)(implicit schema: Schema[A]): TestResult = {
    val codec = getOrDeriveCodec(schema)
    assert(Try(codec.encode(value, writerConfig)).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocate(maxBufSize), writerConfig)).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocateDirect(maxBufSize), writerConfig)).toEither)(
      isLeft(hasError(error))
    ) &&
    assert(Try(codec.encode(value, new java.io.ByteArrayOutputStream(maxBufSize), writerConfig)).toEither)(
      isLeft(hasError(error))
    ) &&
    assert(Try(codec.encodeToString(value, writerConfig)).toEither)(isLeft(hasError(error)))
  }

  def hasError(message: String): Assertion[Throwable] =
    hasField[Throwable, String]("getMessage", _.getMessage, equalTo(message))

  private[this] def readerConfig = ReaderConfig

  private[this] def writerConfig = WriterConfig

  private[this] def getOrDeriveCodec[A](schema: Schema[A]): ToonBinaryCodec[A] =
    codecs.computeIfAbsent(schema, _.deriving(ToonBinaryCodecDeriver).derive).asInstanceOf[ToonBinaryCodec[A]]

  def deriveCodec[A: Schema](deriverModifier: ToonBinaryCodecDeriver => ToonBinaryCodecDeriver): ToonBinaryCodec[A] =
    Schema[A].derive(deriverModifier(ToonBinaryCodecDeriver))

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private[this] val codecs     = new ConcurrentHashMap[Schema[?], ToonBinaryCodec[?]]()
  private[this] val maxBufSize = 4096

  object ToonDynamicValueGen {
    import DynamicValue._

    private val genSafeKey: Gen[Any, String] =
      Gen.alphaNumericStringBounded(4, 8).map(s => "k_" + s)

    val genPrimitiveValue: Gen[Any, PrimitiveValue] =
      Gen.oneOf(
        Gen.unit.map(_ => PrimitiveValue.Unit),
        Gen.alphaNumericStringBounded(1, 10).map(PrimitiveValue.String.apply),
        Gen.int.map(PrimitiveValue.Int.apply),
        Gen.boolean.map(PrimitiveValue.Boolean.apply),
        Gen.long.filter(l => l < Int.MinValue || l > Int.MaxValue).map(PrimitiveValue.Long.apply),
        Gen
          .bigInt(BigInt(Long.MaxValue) + 1, BigInt(Long.MaxValue) * 1000)
          .flatMap(bi => Gen.boolean.map(neg => if (neg) -bi else bi))
          .map(PrimitiveValue.BigInt.apply),
        Gen.bigDecimal(BigDecimal("-1e20"), BigDecimal("1e20")).filter(!_.isWhole).map(PrimitiveValue.BigDecimal.apply)
      )

    val genDynamicValue: Gen[Any, DynamicValue] = genDynamicValueWithDepth(2)

    private def genDynamicValueWithDepth(maxDepth: Int): Gen[Any, DynamicValue] =
      if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_))
      else
        Gen.oneOf(
          genPrimitiveValue.map(Primitive(_)),
          genRecordWithDepth(maxDepth - 1),
          genSequenceWithDepth(maxDepth - 1),
          genVariantWithDepth(maxDepth - 1),
          genMapWithDepth(maxDepth - 1)
        )

    private def genRecordWithDepth(maxDepth: Int): Gen[Any, Record] =
      Gen
        .listOfBounded(1, 4) {
          for {
            key   <- genSafeKey
            value <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
          } yield key -> value
        }
        .map(_.distinctBy(_._1))
        .map(f => Record(Chunk.from(f)))

    private def genSequenceWithDepth(maxDepth: Int): Gen[Any, Sequence] =
      Gen
        .listOfBounded(0, 4)(
          if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
        )
        .map(f => Sequence(Chunk.from(f)))

    private def genVariantWithDepth(maxDepth: Int): Gen[Any, Variant] =
      for {
        caseName <- genSafeKey
        value    <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
      } yield Variant(caseName, value)

    private def genMapWithDepth(maxDepth: Int): Gen[Any, DynamicValue.Map] =
      Gen
        .listOfBounded(1, 3) {
          for {
            key   <- genSafeKey.map(s => Primitive(PrimitiveValue.String(s)))
            value <- if (maxDepth <= 0) genPrimitiveValue.map(Primitive(_)) else genDynamicValueWithDepth(maxDepth)
          } yield (key: DynamicValue, value)
        }
        .map(_.distinctBy { case (k, _) => k })
        .map(f => DynamicValue.Map(Chunk.from(f)))

    def normalize(value: DynamicValue, discriminatorField: Option[String] = None): DynamicValue =
      value match {
        case Primitive(v)   => Primitive(v)
        case Record(fields) =>
          Record(fields.map { case (k, v) => (k, normalize(v, discriminatorField)) })
        case Sequence(elems)      => Sequence(elems.map(normalize(_, discriminatorField)))
        case Variant(caseName, v) =>
          discriminatorField match {
            case Some(_) => Variant(caseName, normalize(v, discriminatorField))
            case None    => Record(Chunk((caseName, normalize(v, discriminatorField))))
          }
        case DynamicValue.Map(entries) =>
          val fields = entries.map { case (k, v) =>
            val keyStr = k match {
              case Primitive(PrimitiveValue.String(s)) => s
              case other                               => encodeKeyToString(other)
            }
            (keyStr, normalize(v, discriminatorField))
          }
          Record(fields)
        case DynamicValue.Null => DynamicValue.Null
      }

    private def encodeKeyToString(value: DynamicValue): String = value match {
      case Primitive(PrimitiveValue.String(s))     => s
      case Primitive(PrimitiveValue.Int(i))        => i.toString
      case Primitive(PrimitiveValue.Long(l))       => l.toString
      case Primitive(PrimitiveValue.Boolean(b))    => b.toString
      case Primitive(PrimitiveValue.Float(f))      => f.toString
      case Primitive(PrimitiveValue.Double(d))     => d.toString
      case Primitive(PrimitiveValue.BigInt(bi))    => bi.toString
      case Primitive(PrimitiveValue.BigDecimal(d)) => d.toString
      case _                                       => value.toString
    }
  }
}
