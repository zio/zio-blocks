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

package zio.blocks.schema.csv

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive._
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc

import java.nio.CharBuffer
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Deriver for CSV codecs that converts between Scala types and CSV row
 * representations.
 *
 * Supports all 27 primitive types, flat record types (case classes), and
 * wrapper/newtype types. Variant (sum types), sequence, map, and dynamic types
 * are rejected with clear error messages.
 *
 * Uses ThreadLocal caches for performance: a reusable `CharBuffer` for field
 * encoding and a recursive record cache for types with self-referential fields.
 */
object CsvCodecDeriver extends Deriver[CsvCodec] {

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[CsvCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      Lazy(primitiveType match {
        case _: PrimitiveType.Unit.type      => unitCodec
        case _: PrimitiveType.Boolean        => booleanCodec
        case _: PrimitiveType.Byte           => byteCodec
        case _: PrimitiveType.Short          => shortCodec
        case _: PrimitiveType.Int            => intCodec
        case _: PrimitiveType.Long           => longCodec
        case _: PrimitiveType.Float          => floatCodec
        case _: PrimitiveType.Double         => doubleCodec
        case _: PrimitiveType.Char           => charCodec
        case _: PrimitiveType.String         => stringCodec
        case _: PrimitiveType.BigInt         => bigIntCodec
        case _: PrimitiveType.BigDecimal     => bigDecimalCodec
        case _: PrimitiveType.DayOfWeek      => dayOfWeekCodec
        case _: PrimitiveType.Duration       => durationCodec
        case _: PrimitiveType.Instant        => instantCodec
        case _: PrimitiveType.LocalDate      => localDateCodec
        case _: PrimitiveType.LocalDateTime  => localDateTimeCodec
        case _: PrimitiveType.LocalTime      => localTimeCodec
        case _: PrimitiveType.Month          => monthCodec
        case _: PrimitiveType.MonthDay       => monthDayCodec
        case _: PrimitiveType.OffsetDateTime => offsetDateTimeCodec
        case _: PrimitiveType.OffsetTime     => offsetTimeCodec
        case _: PrimitiveType.Period         => periodCodec
        case _: PrimitiveType.Year           => yearCodec
        case _: PrimitiveType.YearMonth      => yearMonthCodec
        case _: PrimitiveType.ZoneId         => zoneIdCodec
        case _: PrimitiveType.ZoneOffset     => zoneOffsetCodec
        case _: PrimitiveType.ZonedDateTime  => zonedDateTimeCodec
        case _: PrimitiveType.Currency       => currencyCodec
        case _: PrimitiveType.UUID           => uuidCodec
      })
    } else binding.asInstanceOf[BindingInstance[CsvCodec, ?, A]].instance
  }.asInstanceOf[Lazy[CsvCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val len           = fields.length
      val isRecursive   = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
      var cachedInfo    =
        if (isRecursive) recursiveRecordCache.get.get(typeId)
        else null
      val deriveCodecs = cachedInfo eq null
      if (deriveCodecs) {
        val names    = new Array[String](len)
        val codecs   = new Array[CsvCodec[Any]](len)
        val typeTags = new Array[Int](len)
        val offsets  = new Array[Long](len)
        cachedInfo = new CsvRecordInfo(names, codecs, typeTags, offsets)
        var idx = 0
        while (idx < len) {
          names(idx) = fields(idx).name
          idx += 1
        }
        if (isRecursive) recursiveRecordCache.get.put(typeId, cachedInfo)
      }
      val fieldNames    = cachedInfo.fieldNames
      val fieldCodecs   = cachedInfo.fieldCodecs
      val fieldTypeTags = cachedInfo.fieldTypeTags
      val fieldOffsets  = cachedInfo.fieldOffsets
      var offset        = 0L
      var idx           = 0
      while (idx < len) {
        val field = fields(idx)
        if (deriveCodecs) {
          val fieldReflect = field.value
          val codec        = D.instance(fieldReflect.metadata).force.asInstanceOf[CsvCodec[Any]]
          fieldCodecs(idx) = codec
          fieldTypeTags(idx) = Reflect.typeTag(fieldReflect)
          fieldOffsets(idx) = offset
          offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
        }
        idx += 1
      }
      val headers = IndexedSeq.newBuilder[String]
      idx = 0
      while (idx < len) {
        headers += fieldNames(idx)
        idx += 1
      }
      val headerResult = headers.result()
      new CsvCodec[A] {
        private[csv] val deconstructor      = recordBinding.deconstructor
        private[csv] val constructor        = recordBinding.constructor
        private[csv] val usedRegisters      = constructor.usedRegisters
        val headerNames: IndexedSeq[String] = headerResult

        def encode(value: A, output: CharBuffer): Unit = {
          val regs = Registers(usedRegisters)
          deconstructor.deconstruct(regs, 0, value)
          val fieldStrings = new Array[String](len)
          val buf          = encodeBuffer.get
          var idx          = 0
          while (idx < len) {
            val fieldCodec  = fieldCodecs(idx)
            val fieldOffset = fieldOffsets(idx)
            val fieldValue  = readFieldFromRegisters(fieldTypeTags(idx), regs, fieldOffset)
            buf.clear()
            val usedBuf = try {
              fieldCodec.encode(fieldValue, buf)
              buf
            } catch {
              case _: java.nio.BufferOverflowException =>
                val largeBuf = CharBuffer.allocate(buf.capacity() * 8)
                fieldCodec.encode(fieldValue, largeBuf)
                largeBuf
            }
            usedBuf.flip()
            fieldStrings(idx) = usedBuf.toString
            idx += 1
          }
          val row =
            CsvWriter.writeRow(scala.collection.immutable.ArraySeq.unsafeWrapArray(fieldStrings), CsvConfig.default)
          output.put(row)
        }

        def decode(input: CharBuffer): Either[SchemaError, A] = {
          val str = input.toString
          input.position(input.limit())
          CsvReader.readRow(str, 0, CsvConfig.default) match {
            case Left(err)                => new Left(SchemaError.expectationMismatch(Nil, err.getMessage))
            case Right((parsedFields, _)) =>
              if (parsedFields.length != len)
                new Left(SchemaError.expectationMismatch(Nil, s"Expected $len fields, got ${parsedFields.length}"))
              else {
                val regs = Registers(usedRegisters)
                var idx  = 0
                while (idx < len) {
                  val fieldCodec  = fieldCodecs(idx)
                  val fieldOffset = fieldOffsets(idx)
                  val fieldStr    = parsedFields(idx)
                  fieldCodec.decode(CharBuffer.wrap(fieldStr)) match {
                    case Left(err)         => return new Left(err)
                    case Right(fieldValue) =>
                      writeFieldToRegisters(fieldTypeTags(idx), regs, fieldOffset, fieldValue)
                  }
                  idx += 1
                }
                new Right(constructor.construct(regs, 0))
              }
          }
        }
      }
    }
    else binding.asInstanceOf[BindingInstance[CsvCodec, ?, A]].instance

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[A]] =
    throw new UnsupportedOperationException("CSV does not support variant/sum types. Use a flat case class instead.")

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[C[A]]] =
    throw new UnsupportedOperationException("CSV does not support sequence fields.")

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[M[K, V]]] =
    throw new UnsupportedOperationException("CSV does not support map fields.")

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[DynamicValue]] =
    throw new UnsupportedOperationException("CSV does not support dynamic values.")

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      D.instance(wrapped.metadata).map { codec =>
        new CsvCodec[A] {
          private[this] val wrap              = wrapperBinding.wrap
          private[this] val unwrap            = wrapperBinding.unwrap
          private[this] val wrappedCodec      = codec
          val headerNames: IndexedSeq[String] = wrappedCodec.headerNames

          def encode(value: A, output: CharBuffer): Unit = wrappedCodec.encode(unwrap(value), output)

          def decode(input: CharBuffer): Either[SchemaError, A] =
            wrappedCodec.decode(input) match {
              case Right(b) => new Right(wrap(b))
              case left     => left.asInstanceOf[Either[SchemaError, A]]
            }
        }
      }
    } else binding.asInstanceOf[BindingInstance[CsvCodec, ?, A]].instance

  override def instanceOverrides: IndexedSeq[InstanceOverride] = {
    recursiveRecordCache.remove()
    super.instanceOverrides
  }

  override def modifierOverrides: IndexedSeq[ModifierOverride] = Chunk.empty

  private[csv] final class CsvRecordInfo(
    val fieldNames: Array[String],
    val fieldCodecs: Array[CsvCodec[Any]],
    val fieldTypeTags: Array[Int],
    val fieldOffsets: Array[Long]
  )

  private[csv] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeId[?], CsvRecordInfo]] {
      override def initialValue: java.util.HashMap[TypeId[?], CsvRecordInfo] =
        new java.util.HashMap
    }

  private[csv] val encodeBuffer =
    new ThreadLocal[CharBuffer] {
      override def initialValue: CharBuffer = CharBuffer.allocate(1024)
    }

  // ---------------------------------------------------------------------------
  // Primitive codecs
  // ---------------------------------------------------------------------------

  private def typeError(str: String, typeName: String, cause: Throwable): SchemaError =
    SchemaError.expectationMismatch(Nil, s"Cannot parse '$str' as $typeName: ${cause.getMessage}")

  private val singleHeader: IndexedSeq[String] = IndexedSeq("value")

  private val unitCodec: CsvCodec[Unit] = new CsvCodec[Unit] {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def encode(value: Unit, output: CharBuffer): Unit        = ()
    def decode(input: CharBuffer): Either[SchemaError, Unit] = new Right(())
  }

  private val booleanCodec: CsvCodec[Boolean] = new CsvCodec[Boolean] {
    val headerNames: IndexedSeq[String]                         = singleHeader
    def encode(value: Boolean, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Boolean] = {
      val str = readString(input)
      str.toLowerCase match {
        case "true"  => new Right(true)
        case "false" => new Right(false)
        case _       => new Left(SchemaError.expectationMismatch(Nil, s"Cannot parse '$str' as Boolean"))
      }
    }
  }

  private val byteCodec: CsvCodec[Byte] = new CsvCodec[Byte] {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def encode(value: Byte, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Byte] = {
      val str = readString(input)
      try new Right(java.lang.Byte.parseByte(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Byte", e)) }
    }
  }

  private val shortCodec: CsvCodec[Short] = new CsvCodec[Short] {
    val headerNames: IndexedSeq[String]                       = singleHeader
    def encode(value: Short, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Short] = {
      val str = readString(input)
      try new Right(java.lang.Short.parseShort(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Short", e)) }
    }
  }

  private val intCodec: CsvCodec[scala.Int] = new CsvCodec[scala.Int] {
    val headerNames: IndexedSeq[String]                           = singleHeader
    def encode(value: scala.Int, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, scala.Int] = {
      val str = readString(input)
      try new Right(java.lang.Integer.parseInt(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Int", e)) }
    }
  }

  private val longCodec: CsvCodec[Long] = new CsvCodec[Long] {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def encode(value: Long, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Long] = {
      val str = readString(input)
      try new Right(java.lang.Long.parseLong(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Long", e)) }
    }
  }

  private val floatCodec: CsvCodec[Float] = new CsvCodec[Float] {
    val headerNames: IndexedSeq[String]                       = singleHeader
    def encode(value: Float, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Float] = {
      val str = readString(input)
      try new Right(java.lang.Float.parseFloat(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Float", e)) }
    }
  }

  private val doubleCodec: CsvCodec[Double] = new CsvCodec[Double] {
    val headerNames: IndexedSeq[String]                        = singleHeader
    def encode(value: Double, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Double] = {
      val str = readString(input)
      try new Right(java.lang.Double.parseDouble(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Double", e)) }
    }
  }

  private val charCodec: CsvCodec[Char] = new CsvCodec[Char] {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def encode(value: Char, output: CharBuffer): Unit        = output.put(value)
    def decode(input: CharBuffer): Either[SchemaError, Char] = {
      val str = readString(input)
      if (str.length == 1) new Right(str.charAt(0))
      else new Left(SchemaError.expectationMismatch(Nil, s"Cannot parse '$str' as Char: expected single character"))
    }
  }

  private val stringCodec: CsvCodec[String] = new CsvCodec[String] {
    val headerNames: IndexedSeq[String]                        = singleHeader
    def encode(value: String, output: CharBuffer): Unit        = output.put(value)
    def decode(input: CharBuffer): Either[SchemaError, String] = new Right(readString(input))
  }

  private val bigIntCodec: CsvCodec[BigInt] = new CsvCodec[BigInt] {
    val headerNames: IndexedSeq[String]                        = singleHeader
    def encode(value: BigInt, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, BigInt] = {
      val str = readString(input)
      try new Right(BigInt(str))
      catch { case NonFatal(e) => new Left(typeError(str, "BigInt", e)) }
    }
  }

  private val bigDecimalCodec: CsvCodec[BigDecimal] = new CsvCodec[BigDecimal] {
    val headerNames: IndexedSeq[String]                            = singleHeader
    def encode(value: BigDecimal, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, BigDecimal] = {
      val str = readString(input)
      try new Right(BigDecimal(str))
      catch { case NonFatal(e) => new Left(typeError(str, "BigDecimal", e)) }
    }
  }

  private val dayOfWeekCodec: CsvCodec[java.time.DayOfWeek] = new CsvCodec[java.time.DayOfWeek] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def encode(value: java.time.DayOfWeek, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.DayOfWeek] = {
      val str = readString(input)
      try new Right(java.time.DayOfWeek.valueOf(str.toUpperCase))
      catch { case NonFatal(e) => new Left(typeError(str, "DayOfWeek", e)) }
    }
  }

  private val durationCodec: CsvCodec[java.time.Duration] = new CsvCodec[java.time.Duration] {
    val headerNames: IndexedSeq[String]                                    = singleHeader
    def encode(value: java.time.Duration, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Duration] = {
      val str = readString(input)
      try new Right(java.time.Duration.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Duration", e)) }
    }
  }

  private val instantCodec: CsvCodec[java.time.Instant] = new CsvCodec[java.time.Instant] {
    val headerNames: IndexedSeq[String]                                   = singleHeader
    def encode(value: java.time.Instant, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Instant] = {
      val str = readString(input)
      try new Right(java.time.Instant.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Instant", e)) }
    }
  }

  private val localDateCodec: CsvCodec[java.time.LocalDate] = new CsvCodec[java.time.LocalDate] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def encode(value: java.time.LocalDate, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.LocalDate] = {
      val str = readString(input)
      try new Right(java.time.LocalDate.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "LocalDate", e)) }
    }
  }

  private val localDateTimeCodec: CsvCodec[java.time.LocalDateTime] = new CsvCodec[java.time.LocalDateTime] {
    val headerNames: IndexedSeq[String]                                         = singleHeader
    def encode(value: java.time.LocalDateTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.LocalDateTime] = {
      val str = readString(input)
      try new Right(java.time.LocalDateTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "LocalDateTime", e)) }
    }
  }

  private val localTimeCodec: CsvCodec[java.time.LocalTime] = new CsvCodec[java.time.LocalTime] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def encode(value: java.time.LocalTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.LocalTime] = {
      val str = readString(input)
      try new Right(java.time.LocalTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "LocalTime", e)) }
    }
  }

  private val monthCodec: CsvCodec[java.time.Month] = new CsvCodec[java.time.Month] {
    val headerNames: IndexedSeq[String]                                 = singleHeader
    def encode(value: java.time.Month, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Month] = {
      val str = readString(input)
      try new Right(java.time.Month.valueOf(str.toUpperCase))
      catch { case NonFatal(e) => new Left(typeError(str, "Month", e)) }
    }
  }

  private val monthDayCodec: CsvCodec[java.time.MonthDay] = new CsvCodec[java.time.MonthDay] {
    val headerNames: IndexedSeq[String]                                    = singleHeader
    def encode(value: java.time.MonthDay, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.MonthDay] = {
      val str = readString(input)
      try new Right(java.time.MonthDay.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "MonthDay", e)) }
    }
  }

  private val offsetDateTimeCodec: CsvCodec[java.time.OffsetDateTime] = new CsvCodec[java.time.OffsetDateTime] {
    val headerNames: IndexedSeq[String]                                          = singleHeader
    def encode(value: java.time.OffsetDateTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.OffsetDateTime] = {
      val str = readString(input)
      try new Right(java.time.OffsetDateTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "OffsetDateTime", e)) }
    }
  }

  private val offsetTimeCodec: CsvCodec[java.time.OffsetTime] = new CsvCodec[java.time.OffsetTime] {
    val headerNames: IndexedSeq[String]                                      = singleHeader
    def encode(value: java.time.OffsetTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.OffsetTime] = {
      val str = readString(input)
      try new Right(java.time.OffsetTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "OffsetTime", e)) }
    }
  }

  private val periodCodec: CsvCodec[java.time.Period] = new CsvCodec[java.time.Period] {
    val headerNames: IndexedSeq[String]                                  = singleHeader
    def encode(value: java.time.Period, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Period] = {
      val str = readString(input)
      try new Right(java.time.Period.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Period", e)) }
    }
  }

  private val yearCodec: CsvCodec[java.time.Year] = new CsvCodec[java.time.Year] {
    val headerNames: IndexedSeq[String]                                = singleHeader
    def encode(value: java.time.Year, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Year] = {
      val str = readString(input)
      try new Right(java.time.Year.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Year", e)) }
    }
  }

  private val yearMonthCodec: CsvCodec[java.time.YearMonth] = new CsvCodec[java.time.YearMonth] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def encode(value: java.time.YearMonth, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.YearMonth] = {
      val str = readString(input)
      try new Right(java.time.YearMonth.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "YearMonth", e)) }
    }
  }

  private val zoneIdCodec: CsvCodec[java.time.ZoneId] = new CsvCodec[java.time.ZoneId] {
    val headerNames: IndexedSeq[String]                                  = singleHeader
    def encode(value: java.time.ZoneId, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.ZoneId] = {
      val str = readString(input)
      try new Right(java.time.ZoneId.of(str))
      catch { case NonFatal(e) => new Left(typeError(str, "ZoneId", e)) }
    }
  }

  private val zoneOffsetCodec: CsvCodec[java.time.ZoneOffset] = new CsvCodec[java.time.ZoneOffset] {
    val headerNames: IndexedSeq[String]                                      = singleHeader
    def encode(value: java.time.ZoneOffset, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.ZoneOffset] = {
      val str = readString(input)
      try new Right(java.time.ZoneOffset.of(str))
      catch { case NonFatal(e) => new Left(typeError(str, "ZoneOffset", e)) }
    }
  }

  private val zonedDateTimeCodec: CsvCodec[java.time.ZonedDateTime] = new CsvCodec[java.time.ZonedDateTime] {
    val headerNames: IndexedSeq[String]                                         = singleHeader
    def encode(value: java.time.ZonedDateTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.ZonedDateTime] = {
      val str = readString(input)
      try new Right(java.time.ZonedDateTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "ZonedDateTime", e)) }
    }
  }

  private val currencyCodec: CsvCodec[Currency] = new CsvCodec[Currency] {
    val headerNames: IndexedSeq[String]                          = singleHeader
    def encode(value: Currency, output: CharBuffer): Unit        = output.put(value.getCurrencyCode)
    def decode(input: CharBuffer): Either[SchemaError, Currency] = {
      val str = readString(input)
      try new Right(Currency.getInstance(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Currency", e)) }
    }
  }

  private val uuidCodec: CsvCodec[UUID] = new CsvCodec[UUID] {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def encode(value: UUID, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, UUID] = {
      val str = readString(input)
      try new Right(UUID.fromString(str))
      catch { case NonFatal(e) => new Left(typeError(str, "UUID", e)) }
    }
  }

  private def readString(input: CharBuffer): String = {
    val str = input.toString
    input.position(input.limit())
    str
  }

  private def readFieldFromRegisters(
    typeTag: Int,
    regs: Registers,
    offset: RegisterOffset.RegisterOffset
  ): Any =
    (typeTag: @scala.annotation.switch) match {
      case 0 => regs.getObject(offset)
      case 1 => regs.getInt(offset)
      case 2 => regs.getLong(offset)
      case 3 => regs.getFloat(offset)
      case 4 => regs.getDouble(offset)
      case 5 => regs.getBoolean(offset)
      case 6 => regs.getByte(offset)
      case 7 => regs.getChar(offset)
      case 8 => regs.getShort(offset)
      case _ => ()
    }

  private def writeFieldToRegisters(
    typeTag: Int,
    regs: Registers,
    offset: RegisterOffset.RegisterOffset,
    value: Any
  ): Unit =
    (typeTag: @scala.annotation.switch) match {
      case 0 => regs.setObject(offset, value.asInstanceOf[AnyRef])
      case 1 => regs.setInt(offset, value.asInstanceOf[Int])
      case 2 => regs.setLong(offset, value.asInstanceOf[Long])
      case 3 => regs.setFloat(offset, value.asInstanceOf[Float])
      case 4 => regs.setDouble(offset, value.asInstanceOf[Double])
      case 5 => regs.setBoolean(offset, value.asInstanceOf[Boolean])
      case 6 => regs.setByte(offset, value.asInstanceOf[Byte])
      case 7 => regs.setChar(offset, value.asInstanceOf[Char])
      case 8 => regs.setShort(offset, value.asInstanceOf[Short])
      case _ => ()
    }
}
