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
    binding: Binding[BindingType.Primitive, A],
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
    binding: Binding[BindingType.Record, A],
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
        val names   = new Array[String](len)
        val codecs  = new Array[CsvCodec[Any]](len)
        val offsets = new Array[Long](len)
        cachedInfo = new CsvRecordInfo(names, codecs, offsets)
        var idx = 0
        while (idx < len) {
          names(idx) = fields(idx).name
          idx += 1
        }
        if (isRecursive) recursiveRecordCache.get.put(typeId, cachedInfo)
      }
      val fieldNames                            = cachedInfo.fieldNames
      val fieldCodecs                           = cachedInfo.fieldCodecs
      val fieldOffsets                          = cachedInfo.fieldOffsets
      var offset: RegisterOffset.RegisterOffset = 0L
      var idx                                   = 0
      while (idx < len) {
        val field = fields(idx)
        if (deriveCodecs) {
          val codec = D.instance(field.value.metadata).force.asInstanceOf[CsvCodec[Any]]
          fieldCodecs(idx) = codec
          fieldOffsets(idx) = offset
          offset = RegisterOffset.add(codec.valueOffset, offset)
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
        private[this] val deconstructor     = recordBinding.deconstructor
        private[this] val constructor       = recordBinding.constructor
        private[this] var usedRegisters     = offset
        val headerNames: IndexedSeq[String] = headerResult
        def nullValue: A                    = {
          if (len > 0 && usedRegisters == 0L)
            usedRegisters = RegisterOffset.add(fieldCodecs(len - 1).valueOffset, fieldOffsets(len - 1))
          val regs = Registers(usedRegisters)
          constructor.construct(regs, 0)
        }
        def encode(value: A, output: CharBuffer): Unit = {
          if (len > 0 && usedRegisters == 0L)
            usedRegisters = RegisterOffset.add(fieldCodecs(len - 1).valueOffset, fieldOffsets(len - 1))
          val regs = Registers(usedRegisters)
          deconstructor.deconstruct(regs, 0, value)
          val fieldStrings = new Array[String](len)
          val buf          = encodeBuffer.get
          var i            = 0
          while (i < len) {
            val fieldCodec  = fieldCodecs(i)
            val fieldOffset = fieldOffsets(i)
            val fieldValue  = readFieldFromRegisters(fieldCodec, regs, fieldOffset)
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
            fieldStrings(i) = usedBuf.toString
            i += 1
          }
          val row =
            CsvWriter.writeRow(scala.collection.immutable.ArraySeq.unsafeWrapArray(fieldStrings), CsvConfig.default)
          output.put(row)
        }
        def decode(input: CharBuffer): Either[SchemaError, A] = {
          if (len > 0 && usedRegisters == 0L)
            usedRegisters = RegisterOffset.add(fieldCodecs(len - 1).valueOffset, fieldOffsets(len - 1))
          val str = input.toString
          input.position(input.limit())
          CsvReader.readRow(str, 0, CsvConfig.default) match {
            case Left(err)                => new Left(SchemaError.expectationMismatch(Nil, err.formatMessage))
            case Right((parsedFields, _)) =>
              if (parsedFields.length != len)
                new Left(SchemaError.expectationMismatch(Nil, s"Expected $len fields, got ${parsedFields.length}"))
              else {
                val regs = Registers(usedRegisters)
                var i    = 0
                while (i < len) {
                  val fieldCodec  = fieldCodecs(i)
                  val fieldOffset = fieldOffsets(i)
                  val fieldStr    = parsedFields(i)
                  fieldCodec.decode(CharBuffer.wrap(fieldStr)) match {
                    case Left(err)         => return new Left(err)
                    case Right(fieldValue) =>
                      writeFieldToRegisters(fieldCodec, regs, fieldOffset, fieldValue)
                  }
                  i += 1
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
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[A]] = Lazy(
    throw new UnsupportedOperationException("CSV does not support variant/sum types. Use a flat case class instead.")
  )

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[C[A]]] = Lazy(
    throw new UnsupportedOperationException("CSV does not support sequence fields.")
  )

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[M[K, V]]] = Lazy(
    throw new UnsupportedOperationException("CSV does not support map fields.")
  )

  override def deriveDynamic[F[_, _]](
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[DynamicValue]] = Lazy(
    throw new UnsupportedOperationException("CSV does not support dynamic values.")
  )

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CsvCodec[A]] =
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      D.instance(wrapped.metadata).map { codec =>
        new CsvCodec[A](codec.valueType) {
          private[this] val wrap                                = wrapperBinding.wrap
          private[this] val unwrap                              = wrapperBinding.unwrap
          private[this] val wrappedCodec                        = codec
          val headerNames: IndexedSeq[String]                   = wrappedCodec.headerNames
          def nullValue: A                                      = wrap(wrappedCodec.nullValue)
          def encode(value: A, output: CharBuffer): Unit        = wrappedCodec.encode(unwrap(value), output)
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

  private final class CsvRecordInfo(
    val fieldNames: Array[String],
    val fieldCodecs: Array[CsvCodec[Any]],
    val fieldOffsets: Array[Long]
  )

  private[this] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeId[?], CsvRecordInfo]] {
      override def initialValue: java.util.HashMap[TypeId[?], CsvRecordInfo] =
        new java.util.HashMap
    }

  private[this] val encodeBuffer =
    new ThreadLocal[CharBuffer] {
      override def initialValue: CharBuffer = CharBuffer.allocate(1024)
    }

  // ---------------------------------------------------------------------------
  // Primitive codecs
  // ---------------------------------------------------------------------------

  private def typeError(str: String, typeName: String, cause: Throwable): SchemaError =
    SchemaError.expectationMismatch(Nil, s"Cannot parse '$str' as $typeName: ${cause.getMessage}")

  private val singleHeader: IndexedSeq[String] = IndexedSeq("value")

  private val unitCodec: CsvCodec[Unit] = new CsvCodec[Unit](CsvCodec.unitType) {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def nullValue: Unit                                      = ()
    def encode(value: Unit, output: CharBuffer): Unit        = ()
    def decode(input: CharBuffer): Either[SchemaError, Unit] = new Right(())
  }

  private val booleanCodec: CsvCodec[Boolean] = new CsvCodec[Boolean](CsvCodec.booleanType) {
    val headerNames: IndexedSeq[String]                         = singleHeader
    def nullValue: Boolean                                      = false
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

  private val byteCodec: CsvCodec[Byte] = new CsvCodec[Byte](CsvCodec.byteType) {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def nullValue: Byte                                      = 0
    def encode(value: Byte, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Byte] = {
      val str = readString(input)
      try new Right(java.lang.Byte.parseByte(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Byte", e)) }
    }
  }

  private val shortCodec: CsvCodec[Short] = new CsvCodec[Short](CsvCodec.shortType) {
    val headerNames: IndexedSeq[String]                       = singleHeader
    def nullValue: Short                                      = 0
    def encode(value: Short, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Short] = {
      val str = readString(input)
      try new Right(java.lang.Short.parseShort(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Short", e)) }
    }
  }

  private val intCodec: CsvCodec[scala.Int] = new CsvCodec[scala.Int](CsvCodec.intType) {
    val headerNames: IndexedSeq[String]                           = singleHeader
    def nullValue: scala.Int                                      = 0
    def encode(value: scala.Int, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, scala.Int] = {
      val str = readString(input)
      try new Right(java.lang.Integer.parseInt(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Int", e)) }
    }
  }

  private val longCodec: CsvCodec[Long] = new CsvCodec[Long](CsvCodec.longType) {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def nullValue: Long                                      = 0L
    def encode(value: Long, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Long] = {
      val str = readString(input)
      try new Right(java.lang.Long.parseLong(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Long", e)) }
    }
  }

  private val floatCodec: CsvCodec[Float] = new CsvCodec[Float](CsvCodec.floatType) {
    val headerNames: IndexedSeq[String]                       = singleHeader
    def nullValue: Float                                      = 0.0f
    def encode(value: Float, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Float] = {
      val str = readString(input)
      try new Right(java.lang.Float.parseFloat(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Float", e)) }
    }
  }

  private val doubleCodec: CsvCodec[Double] = new CsvCodec[Double](CsvCodec.doubleType) {
    val headerNames: IndexedSeq[String]                        = singleHeader
    def nullValue: Double                                      = 0.0d
    def encode(value: Double, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, Double] = {
      val str = readString(input)
      try new Right(java.lang.Double.parseDouble(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Double", e)) }
    }
  }

  private val charCodec: CsvCodec[Char] = new CsvCodec[Char](CsvCodec.charType) {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def nullValue: Char                                      = '\u0000'
    def encode(value: Char, output: CharBuffer): Unit        = output.put(value)
    def decode(input: CharBuffer): Either[SchemaError, Char] = {
      val str = readString(input)
      if (str.length == 1) new Right(str.charAt(0))
      else new Left(SchemaError.expectationMismatch(Nil, s"Cannot parse '$str' as Char: expected single character"))
    }
  }

  private val stringCodec: CsvCodec[String] = new CsvCodec[String] {
    val headerNames: IndexedSeq[String]                        = singleHeader
    def nullValue: String                                      = ""
    def encode(value: String, output: CharBuffer): Unit        = output.put(value)
    def decode(input: CharBuffer): Either[SchemaError, String] = new Right(readString(input))
  }

  private val bigIntCodec: CsvCodec[BigInt] = new CsvCodec[BigInt] {
    val headerNames: IndexedSeq[String]                        = singleHeader
    def nullValue: BigInt                                      = BigInt(0)
    def encode(value: BigInt, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, BigInt] = {
      val str = readString(input)
      try new Right(BigInt(str))
      catch { case NonFatal(e) => new Left(typeError(str, "BigInt", e)) }
    }
  }

  private val bigDecimalCodec: CsvCodec[BigDecimal] = new CsvCodec[BigDecimal] {
    val headerNames: IndexedSeq[String]                            = singleHeader
    def nullValue: BigDecimal                                      = BigDecimal(0)
    def encode(value: BigDecimal, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, BigDecimal] = {
      val str = readString(input)
      try new Right(BigDecimal(str))
      catch { case NonFatal(e) => new Left(typeError(str, "BigDecimal", e)) }
    }
  }

  private val dayOfWeekCodec: CsvCodec[java.time.DayOfWeek] = new CsvCodec[java.time.DayOfWeek] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def nullValue: java.time.DayOfWeek                                      = java.time.DayOfWeek.MONDAY
    def encode(value: java.time.DayOfWeek, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.DayOfWeek] = {
      val str = readString(input)
      try new Right(java.time.DayOfWeek.valueOf(str.toUpperCase))
      catch { case NonFatal(e) => new Left(typeError(str, "DayOfWeek", e)) }
    }
  }

  private val durationCodec: CsvCodec[java.time.Duration] = new CsvCodec[java.time.Duration] {
    val headerNames: IndexedSeq[String]                                    = singleHeader
    def nullValue: java.time.Duration                                      = java.time.Duration.ZERO
    def encode(value: java.time.Duration, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Duration] = {
      val str = readString(input)
      try new Right(java.time.Duration.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Duration", e)) }
    }
  }

  private val instantCodec: CsvCodec[java.time.Instant] = new CsvCodec[java.time.Instant] {
    val headerNames: IndexedSeq[String]                                   = singleHeader
    def nullValue: java.time.Instant                                      = java.time.Instant.EPOCH
    def encode(value: java.time.Instant, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Instant] = {
      val str = readString(input)
      try new Right(java.time.Instant.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Instant", e)) }
    }
  }

  private val localDateCodec: CsvCodec[java.time.LocalDate] = new CsvCodec[java.time.LocalDate] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def nullValue: java.time.LocalDate                                      = java.time.LocalDate.MIN
    def encode(value: java.time.LocalDate, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.LocalDate] = {
      val str = readString(input)
      try new Right(java.time.LocalDate.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "LocalDate", e)) }
    }
  }

  private val localDateTimeCodec: CsvCodec[java.time.LocalDateTime] = new CsvCodec[java.time.LocalDateTime] {
    val headerNames: IndexedSeq[String]                                         = singleHeader
    def nullValue: java.time.LocalDateTime                                      = java.time.LocalDateTime.MIN
    def encode(value: java.time.LocalDateTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.LocalDateTime] = {
      val str = readString(input)
      try new Right(java.time.LocalDateTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "LocalDateTime", e)) }
    }
  }

  private val localTimeCodec: CsvCodec[java.time.LocalTime] = new CsvCodec[java.time.LocalTime] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def nullValue: java.time.LocalTime                                      = java.time.LocalTime.MIDNIGHT
    def encode(value: java.time.LocalTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.LocalTime] = {
      val str = readString(input)
      try new Right(java.time.LocalTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "LocalTime", e)) }
    }
  }

  private val monthCodec: CsvCodec[java.time.Month] = new CsvCodec[java.time.Month] {
    val headerNames: IndexedSeq[String]                                 = singleHeader
    def nullValue: java.time.Month                                      = java.time.Month.JANUARY
    def encode(value: java.time.Month, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Month] = {
      val str = readString(input)
      try new Right(java.time.Month.valueOf(str.toUpperCase))
      catch { case NonFatal(e) => new Left(typeError(str, "Month", e)) }
    }
  }

  private val monthDayCodec: CsvCodec[java.time.MonthDay] = new CsvCodec[java.time.MonthDay] {
    val headerNames: IndexedSeq[String]                                    = singleHeader
    def nullValue: java.time.MonthDay                                      = java.time.MonthDay.of(1, 1)
    def encode(value: java.time.MonthDay, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.MonthDay] = {
      val str = readString(input)
      try new Right(java.time.MonthDay.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "MonthDay", e)) }
    }
  }

  private val offsetDateTimeCodec: CsvCodec[java.time.OffsetDateTime] = new CsvCodec[java.time.OffsetDateTime] {
    val headerNames: IndexedSeq[String]                                          = singleHeader
    def nullValue: java.time.OffsetDateTime                                      = java.time.OffsetDateTime.MIN
    def encode(value: java.time.OffsetDateTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.OffsetDateTime] = {
      val str = readString(input)
      try new Right(java.time.OffsetDateTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "OffsetDateTime", e)) }
    }
  }

  private val offsetTimeCodec: CsvCodec[java.time.OffsetTime] = new CsvCodec[java.time.OffsetTime] {
    val headerNames: IndexedSeq[String]                                      = singleHeader
    def nullValue: java.time.OffsetTime                                      = java.time.OffsetTime.MIN
    def encode(value: java.time.OffsetTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.OffsetTime] = {
      val str = readString(input)
      try new Right(java.time.OffsetTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "OffsetTime", e)) }
    }
  }

  private val periodCodec: CsvCodec[java.time.Period] = new CsvCodec[java.time.Period] {
    val headerNames: IndexedSeq[String]                                  = singleHeader
    def nullValue: java.time.Period                                      = java.time.Period.ZERO
    def encode(value: java.time.Period, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Period] = {
      val str = readString(input)
      try new Right(java.time.Period.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Period", e)) }
    }
  }

  private val yearCodec: CsvCodec[java.time.Year] = new CsvCodec[java.time.Year] {
    val headerNames: IndexedSeq[String]                                = singleHeader
    def nullValue: java.time.Year                                      = java.time.Year.of(1970)
    def encode(value: java.time.Year, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.Year] = {
      val str = readString(input)
      try new Right(java.time.Year.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Year", e)) }
    }
  }

  private val yearMonthCodec: CsvCodec[java.time.YearMonth] = new CsvCodec[java.time.YearMonth] {
    val headerNames: IndexedSeq[String]                                     = singleHeader
    def nullValue: java.time.YearMonth                                      = java.time.YearMonth.of(1970, 1)
    def encode(value: java.time.YearMonth, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.YearMonth] = {
      val str = readString(input)
      try new Right(java.time.YearMonth.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "YearMonth", e)) }
    }
  }

  private val zoneIdCodec: CsvCodec[java.time.ZoneId] = new CsvCodec[java.time.ZoneId] {
    val headerNames: IndexedSeq[String]                                  = singleHeader
    def nullValue: java.time.ZoneId                                      = java.time.ZoneId.of("UTC")
    def encode(value: java.time.ZoneId, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.ZoneId] = {
      val str = readString(input)
      try new Right(java.time.ZoneId.of(str))
      catch { case NonFatal(e) => new Left(typeError(str, "ZoneId", e)) }
    }
  }

  private val zoneOffsetCodec: CsvCodec[java.time.ZoneOffset] = new CsvCodec[java.time.ZoneOffset] {
    val headerNames: IndexedSeq[String]                                      = singleHeader
    def nullValue: java.time.ZoneOffset                                      = java.time.ZoneOffset.UTC
    def encode(value: java.time.ZoneOffset, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.ZoneOffset] = {
      val str = readString(input)
      try new Right(java.time.ZoneOffset.of(str))
      catch { case NonFatal(e) => new Left(typeError(str, "ZoneOffset", e)) }
    }
  }

  private val zonedDateTimeCodec: CsvCodec[java.time.ZonedDateTime] = new CsvCodec[java.time.ZonedDateTime] {
    val headerNames: IndexedSeq[String]    = singleHeader
    def nullValue: java.time.ZonedDateTime =
      java.time.ZonedDateTime.of(java.time.LocalDateTime.MIN, java.time.ZoneOffset.UTC)
    def encode(value: java.time.ZonedDateTime, output: CharBuffer): Unit        = output.put(value.toString)
    def decode(input: CharBuffer): Either[SchemaError, java.time.ZonedDateTime] = {
      val str = readString(input)
      try new Right(java.time.ZonedDateTime.parse(str))
      catch { case NonFatal(e) => new Left(typeError(str, "ZonedDateTime", e)) }
    }
  }

  private val currencyCodec: CsvCodec[Currency] = new CsvCodec[Currency] {
    val headerNames: IndexedSeq[String]                          = singleHeader
    def nullValue: Currency                                      = Currency.getInstance("USD")
    def encode(value: Currency, output: CharBuffer): Unit        = output.put(value.getCurrencyCode)
    def decode(input: CharBuffer): Either[SchemaError, Currency] = {
      val str = readString(input)
      try new Right(Currency.getInstance(str))
      catch { case NonFatal(e) => new Left(typeError(str, "Currency", e)) }
    }
  }

  private val uuidCodec: CsvCodec[UUID] = new CsvCodec[UUID] {
    val headerNames: IndexedSeq[String]                      = singleHeader
    def nullValue: UUID                                      = new UUID(0L, 0L)
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
    codec: CsvCodec[Any],
    regs: Registers,
    offset: RegisterOffset.RegisterOffset
  ): Any =
    (codec.valueType: @scala.annotation.switch) match {
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
    codec: CsvCodec[Any],
    regs: Registers,
    offset: RegisterOffset.RegisterOffset,
    value: Any
  ): Unit =
    (codec.valueType: @scala.annotation.switch) match {
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
