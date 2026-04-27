package zio.blocks.sql

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive._
import zio.blocks.docs.Doc
import zio.blocks.typeid.TypeId

class DbCodecDeriver(columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase) extends Deriver[DbCodec] {

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[DbCodec[A]] = Lazy {
    primitiveType match {
      case PrimitiveType.Unit             => unitCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Boolean       => booleanCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Byte          => byteCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Short         => shortCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Int           => intCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Long          => longCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Float         => floatCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Double        => doubleCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Char          => charCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.String        => stringCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.BigDecimal    => bigDecimalCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Duration      => durationCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.Instant       => instantCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.LocalDate     => localDateCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.LocalDateTime =>
        localDateTimeCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.LocalTime => localTimeCodec.asInstanceOf[DbCodec[A]]
      case _: PrimitiveType.UUID      => uuidCodec.asInstanceOf[DbCodec[A]]
      case other                      =>
        throw new UnsupportedOperationException(
          s"DbCodec does not support primitive type: ${other.getClass.getSimpleName}"
        )
    }
  }

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[DbCodec[A]] = Lazy {
    val recordBinding = binding
    val constructor   = recordBinding.constructor
    val deconstructor = recordBinding.deconstructor
    val len           = fields.length

    val fieldNames     = new Array[String](len)
    val fieldCodecs    = new Array[DbCodec[Any]](len)
    val fieldTransient = new Array[Boolean](len)

    val reflects = new Array[Reflect[F, ?]](len)
    var idx      = 0
    while (idx < len) {
      reflects(idx) = fields(idx).value
      idx += 1
    }
    val registers: IndexedSeq[Register[Any]] =
      scala.collection.immutable.ArraySeq.unsafeWrapArray(
        Reflect.Record.registers(reflects.asInstanceOf[Array[Reflect[F, ?]]])
      )

    idx = 0
    while (idx < len) {
      val field       = fields(idx)
      val isTransient = field.modifiers.exists(_.isInstanceOf[Modifier.transient])
      fieldTransient(idx) = isTransient

      if (!isTransient) {
        val renamed = field.modifiers.collectFirst { case m: Modifier.rename => m.name }
        fieldNames(idx) = renamed.getOrElse(columnNameMapper(field.name))

        fieldCodecs(idx) = D.instance(field.value.metadata).force.asInstanceOf[DbCodec[Any]]
      }
      idx += 1
    }

    val activeFieldIndices: Array[Int] = (0 until len).filter(i => !fieldTransient(i)).toArray
    val allColumns: IndexedSeq[String] = {
      val builder = IndexedSeq.newBuilder[String]
      var fi      = 0
      while (fi < activeFieldIndices.length) {
        val i         = activeFieldIndices(fi)
        val codec     = fieldCodecs(i)
        val fieldName = fieldNames(i)
        if (codec.columnCount == 1) {
          builder += fieldName
        } else {
          val cols = codec.columns
          var ci   = 0
          while (ci < cols.length) {
            builder += fieldName + "_" + cols(ci)
            ci += 1
          }
        }
        fi += 1
      }
      builder.result()
    }

    new DbCodec[A] {
      val columns: IndexedSeq[String] = allColumns

      def readValue(reader: DbResultReader, startIndex: Int): A = {
        val regs   = Registers(constructor.usedRegisters)
        var colIdx = startIndex
        var fi     = 0
        while (fi < activeFieldIndices.length) {
          val i          = activeFieldIndices(fi)
          val codec      = fieldCodecs(i)
          val fieldValue = codec.readValue(reader, colIdx)
          registers(i).set(regs, 0, fieldValue)
          colIdx += codec.columnCount
          fi += 1
        }
        var ti = 0
        while (ti < len) {
          if (fieldTransient(ti)) {
            fields(ti).value.getDefaultValue(F) match {
              case Some(dv) => registers(ti).set(regs, 0, dv)
              case None     =>
            }
          }
          ti += 1
        }
        constructor.construct(regs, 0)
      }

      def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit = {
        val regs = Registers(deconstructor.usedRegisters)
        deconstructor.deconstruct(regs, 0, value)
        var colIdx = startIndex
        var fi     = 0
        while (fi < activeFieldIndices.length) {
          val i     = activeFieldIndices(fi)
          val codec = fieldCodecs(i)
          codec.writeValue(writer, colIdx, registers(i).get(regs, 0))
          colIdx += codec.columnCount
          fi += 1
        }
      }

      def toDbValues(value: A): IndexedSeq[DbValue] = {
        val regs = Registers(deconstructor.usedRegisters)
        deconstructor.deconstruct(regs, 0, value)
        val builder = IndexedSeq.newBuilder[DbValue]
        var fi      = 0
        while (fi < activeFieldIndices.length) {
          val i     = activeFieldIndices(fi)
          val codec = fieldCodecs(i)
          builder ++= codec.toDbValues(registers(i).get(regs, 0))
          fi += 1
        }
        builder.result()
      }
    }
  }

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[DbCodec[A]] =
    D.instance(wrapped.metadata).map { wrappedCodec =>
      val wc = wrappedCodec.asInstanceOf[DbCodec[B]]
      new DbCodec[A] {
        val columns: IndexedSeq[String] = wc.columns

        def readValue(reader: DbResultReader, startIndex: Int): A =
          binding.wrap(wc.readValue(reader, startIndex))

        def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit =
          wc.writeValue(writer, startIndex, binding.unwrap(value))

        def toDbValues(value: A): IndexedSeq[DbValue] =
          wc.toDbValues(binding.unwrap(value))
      }
    }

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[DbCodec[A]] = Lazy {
    if (isOptionType(typeId, cases)) {
      val someCase   = cases(1)
      val someRecord = someCase.value.asRecord.get
      val innerField = someRecord.fields(0)
      val innerCodec = D.instance(innerField.value.metadata).force.asInstanceOf[DbCodec[Any]]

      if (innerCodec.columnCount > 1) {
        throw new UnsupportedOperationException(
          s"Option[A] where A has ${innerCodec.columnCount} columns is not supported. " +
            "Option is only supported for single-column types."
        )
      }

      new DbCodec[A] {
        val columns: IndexedSeq[String] = innerCodec.columns

        // Note: wasNull reflects the last column read by the inner codec.
        // For single-column types (the common case), this is correct.
        // For multi-column inner types, wasNull only reflects the last column,
        // so a NULL in an earlier column may not be detected.
        def readValue(reader: DbResultReader, startIndex: Int): A = {
          val innerValue  = innerCodec.readValue(reader, startIndex)
          val result: Any = if (reader.wasNull) None else Some(innerValue)
          result.asInstanceOf[A]
        }

        def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit = {
          val opt = value.asInstanceOf[Option[Any]]
          opt match {
            case Some(v) => innerCodec.writeValue(writer, startIndex, v)
            case None    =>
              var i = 0
              while (i < innerCodec.columnCount) {
                writer.setNull(startIndex + i, 0)
                i += 1
              }
          }
        }

        def toDbValues(value: A): IndexedSeq[DbValue] = {
          val opt = value.asInstanceOf[Option[Any]]
          opt match {
            case Some(v) => innerCodec.toDbValues(v)
            case None    =>
              val builder = IndexedSeq.newBuilder[DbValue]
              var i       = 0
              while (i < innerCodec.columnCount) {
                builder += DbValue.DbNull
                i += 1
              }
              builder.result()
          }
        }
      }
    } else if (isSimpleEnum(cases)) {
      val discr                    = binding.discriminator
      val constructorByName        = buildConstructorMap(cases)
      val caseNames: Array[String] = collectCaseNames(cases)
      val caseNamesJoined          = caseNames.mkString(", ")

      new DbCodec[A] {
        val columns: IndexedSeq[String] = IndexedSeq("value")

        def readValue(reader: DbResultReader, startIndex: Int): A = {
          val name = reader.getString(startIndex)
          constructorByName.get(name) match {
            case Some(ctor) => ctor.construct(null, 0).asInstanceOf[A]
            case None       =>
              throw new IllegalArgumentException(
                s"Unknown enum variant '${name}'. Expected one of: ${caseNamesJoined}"
              )
          }
        }

        def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit =
          writer.setString(startIndex, enumName(discr, cases, value))

        def toDbValues(value: A): IndexedSeq[DbValue] =
          IndexedSeq(DbValue.DbString(enumName(discr, cases, value)))
      }
    } else {
      throw new UnsupportedOperationException(
        "DbCodec does not support sum types (sealed trait/enum) with data fields as SQL columns"
      )
    }
  }

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[DbCodec[C[A]]] =
    Lazy {
      throw new UnsupportedOperationException(
        "DbCodec does not support collection types (Seq, List, etc.) as SQL columns"
      )
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[DbCodec[M[K, V]]] =
    Lazy {
      throw new UnsupportedOperationException(
        "DbCodec does not support Map types as SQL columns"
      )
    }

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[DbCodec[DynamicValue]] =
    Lazy {
      throw new UnsupportedOperationException(
        "DbCodec does not support DynamicValue as SQL columns"
      )
    }

  private def isOptionType[F[_, _], A](
    typeId: TypeId[A],
    cases: IndexedSeq[Term[F, A, ?]]
  ): Boolean =
    typeId.owner == zio.blocks.typeid.Owner.fromPackagePath("scala") &&
      typeId.name == "Option" &&
      cases.length == 2 &&
      cases(1).name == "Some"

  private def isSimpleEnum[F[_, _], A](cases: IndexedSeq[Term[F, A, ?]]): Boolean =
    cases.forall { case_ =>
      val caseReflect = case_.value
      caseReflect.asRecord.exists(_.fields.isEmpty) ||
      (caseReflect.isVariant && caseReflect.asVariant.exists(v => isSimpleEnum(v.cases)))
    }

  private def buildConstructorMap[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]]
  )(implicit F: HasBinding[F]): Map[String, Constructor[?]] = {
    val builder = Map.newBuilder[String, Constructor[?]]
    collectConstructors(cases, builder)
    builder.result()
  }

  private def collectConstructors[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    builder: scala.collection.mutable.Builder[(String, Constructor[?]), Map[String, Constructor[?]]]
  )(implicit F: HasBinding[F]): Unit = {
    var idx = 0
    while (idx < cases.length) {
      val case_       = cases(idx)
      val caseReflect = case_.value
      if (caseReflect.isVariant) {
        val nestedVariant = caseReflect.asVariant.get
        collectConstructors(
          nestedVariant.cases.asInstanceOf[IndexedSeq[Term[F, A, ?]]],
          builder
        )
      } else {
        val recordBinding = F.binding(caseReflect.asRecord.get.recordBinding)
        val constructor   = recordBinding.asInstanceOf[Binding.Record[Any]].constructor
        builder += (case_.name -> constructor)
      }
      idx += 1
    }
  }

  private def collectCaseNames[F[_, _], A](cases: IndexedSeq[Term[F, A, ?]]): Array[String] = {
    val builder                                 = Array.newBuilder[String]
    def go(cs: IndexedSeq[Term[F, A, ?]]): Unit = {
      var idx = 0
      while (idx < cs.length) {
        val case_       = cs(idx)
        val caseReflect = case_.value
        if (caseReflect.isVariant) {
          go(caseReflect.asVariant.get.cases.asInstanceOf[IndexedSeq[Term[F, A, ?]]])
        } else {
          builder += case_.name
        }
        idx += 1
      }
    }
    go(cases)
    builder.result()
  }

  private def enumName[F[_, _], A](
    discr: Discriminator[A],
    cases: IndexedSeq[Term[F, A, ?]],
    value: A
  )(implicit F: HasBinding[F]): String = {
    val idx   = discr.discriminate(value)
    val case_ = cases(idx)
    if (case_.value.isVariant) {
      val nestedVariant = case_.value.asVariant.get.asInstanceOf[Reflect.Variant[F, A]]
      val nestedDiscr   = F.binding(nestedVariant.variantBinding).asInstanceOf[Binding.Variant[A]].discriminator
      enumName(nestedDiscr, nestedVariant.cases.asInstanceOf[IndexedSeq[Term[F, A, ?]]], value)
    } else {
      case_.name
    }
  }

  private val unitCodec: DbCodec[Unit] = new DbCodec[Unit] {
    val columns: IndexedSeq[String]                                           = IndexedSeq.empty
    def readValue(reader: DbResultReader, startIndex: Int): Unit              = ()
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Unit): Unit = ()
    def toDbValues(value: Unit): IndexedSeq[DbValue]                          = IndexedSeq.empty
    override def columnCount: Int                                             = 0
  }

  private val booleanCodec: DbCodec[Boolean] = new DbCodec[Boolean] {
    val columns: IndexedSeq[String]                                 = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Boolean =
      reader.getBoolean(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Boolean): Unit =
      writer.setBoolean(startIndex, value)
    def toDbValues(value: Boolean): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbBoolean(value))
  }

  private val byteCodec: DbCodec[Byte] = new DbCodec[Byte] {
    val columns: IndexedSeq[String]                              = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Byte =
      reader.getByte(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Byte): Unit =
      writer.setByte(startIndex, value)
    def toDbValues(value: Byte): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbByte(value))
  }

  private val shortCodec: DbCodec[Short] = new DbCodec[Short] {
    val columns: IndexedSeq[String]                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Short =
      reader.getShort(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Short): Unit =
      writer.setShort(startIndex, value)
    def toDbValues(value: Short): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbShort(value))
  }

  private val intCodec: DbCodec[Int] = new DbCodec[Int] {
    val columns: IndexedSeq[String]                             = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Int =
      reader.getInt(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Int): Unit =
      writer.setInt(startIndex, value)
    def toDbValues(value: Int): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbInt(value))
  }

  private val longCodec: DbCodec[Long] = new DbCodec[Long] {
    val columns: IndexedSeq[String]                              = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Long =
      reader.getLong(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Long): Unit =
      writer.setLong(startIndex, value)
    def toDbValues(value: Long): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbLong(value))
  }

  private val floatCodec: DbCodec[Float] = new DbCodec[Float] {
    val columns: IndexedSeq[String]                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Float =
      reader.getFloat(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Float): Unit =
      writer.setFloat(startIndex, value)
    def toDbValues(value: Float): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbFloat(value))
  }

  private val doubleCodec: DbCodec[Double] = new DbCodec[Double] {
    val columns: IndexedSeq[String]                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Double =
      reader.getDouble(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Double): Unit =
      writer.setDouble(startIndex, value)
    def toDbValues(value: Double): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbDouble(value))
  }

  private val charCodec: DbCodec[Char] = new DbCodec[Char] {
    val columns: IndexedSeq[String]                              = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): Char = {
      val s = reader.getString(startIndex)
      if (s != null && s.length > 0) s.charAt(0) else '\u0000'
    }
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Char): Unit =
      writer.setString(startIndex, value.toString)
    def toDbValues(value: Char): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbChar(value))
  }

  private val stringCodec: DbCodec[String] = new DbCodec[String] {
    val columns: IndexedSeq[String]                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): String =
      reader.getString(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: String): Unit =
      writer.setString(startIndex, value)
    def toDbValues(value: String): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbString(value))
  }

  private val bigDecimalCodec: DbCodec[BigDecimal] = new DbCodec[BigDecimal] {
    val columns: IndexedSeq[String]                                    = IndexedSeq("value")
    def readValue(reader: DbResultReader, startIndex: Int): BigDecimal = {
      val jbd = reader.getBigDecimal(startIndex)
      if (jbd != null) scala.BigDecimal(jbd) else null.asInstanceOf[BigDecimal]
    }
    def writeValue(writer: DbParamWriter, startIndex: Int, value: BigDecimal): Unit =
      writer.setBigDecimal(startIndex, value.bigDecimal)
    def toDbValues(value: BigDecimal): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbBigDecimal(value))
  }

  private val durationCodec: DbCodec[java.time.Duration] =
    new DbCodec[java.time.Duration] {
      val columns: IndexedSeq[String]                                            = IndexedSeq("value")
      def readValue(reader: DbResultReader, startIndex: Int): java.time.Duration =
        reader.getDuration(startIndex)
      def writeValue(writer: DbParamWriter, startIndex: Int, value: java.time.Duration): Unit =
        writer.setDuration(startIndex, value)
      def toDbValues(value: java.time.Duration): IndexedSeq[DbValue] =
        IndexedSeq(DbValue.DbDuration(value))
    }

  private val instantCodec: DbCodec[java.time.Instant] =
    new DbCodec[java.time.Instant] {
      val columns: IndexedSeq[String]                                           = IndexedSeq("value")
      def readValue(reader: DbResultReader, startIndex: Int): java.time.Instant =
        reader.getInstant(startIndex)
      def writeValue(writer: DbParamWriter, startIndex: Int, value: java.time.Instant): Unit =
        writer.setInstant(startIndex, value)
      def toDbValues(value: java.time.Instant): IndexedSeq[DbValue] =
        IndexedSeq(DbValue.DbInstant(value))
    }

  private val localDateCodec: DbCodec[java.time.LocalDate] =
    new DbCodec[java.time.LocalDate] {
      val columns: IndexedSeq[String]                                             = IndexedSeq("value")
      def readValue(reader: DbResultReader, startIndex: Int): java.time.LocalDate =
        reader.getLocalDate(startIndex)
      def writeValue(writer: DbParamWriter, startIndex: Int, value: java.time.LocalDate): Unit =
        writer.setLocalDate(startIndex, value)
      def toDbValues(value: java.time.LocalDate): IndexedSeq[DbValue] =
        IndexedSeq(DbValue.DbLocalDate(value))
    }

  private val localDateTimeCodec: DbCodec[java.time.LocalDateTime] =
    new DbCodec[java.time.LocalDateTime] {
      val columns: IndexedSeq[String]                                                 = IndexedSeq("value")
      def readValue(reader: DbResultReader, startIndex: Int): java.time.LocalDateTime =
        reader.getLocalDateTime(startIndex)
      def writeValue(writer: DbParamWriter, startIndex: Int, value: java.time.LocalDateTime): Unit =
        writer.setLocalDateTime(startIndex, value)
      def toDbValues(value: java.time.LocalDateTime): IndexedSeq[DbValue] =
        IndexedSeq(DbValue.DbLocalDateTime(value))
    }

  private val localTimeCodec: DbCodec[java.time.LocalTime] =
    new DbCodec[java.time.LocalTime] {
      val columns: IndexedSeq[String]                                             = IndexedSeq("value")
      def readValue(reader: DbResultReader, startIndex: Int): java.time.LocalTime =
        reader.getLocalTime(startIndex)
      def writeValue(writer: DbParamWriter, startIndex: Int, value: java.time.LocalTime): Unit =
        writer.setLocalTime(startIndex, value)
      def toDbValues(value: java.time.LocalTime): IndexedSeq[DbValue] =
        IndexedSeq(DbValue.DbLocalTime(value))
    }

  private val uuidCodec: DbCodec[java.util.UUID] =
    new DbCodec[java.util.UUID] {
      val columns: IndexedSeq[String]                                        = IndexedSeq("value")
      def readValue(reader: DbResultReader, startIndex: Int): java.util.UUID =
        reader.getUUID(startIndex)
      def writeValue(writer: DbParamWriter, startIndex: Int, value: java.util.UUID): Unit =
        writer.setUUID(startIndex, value)
      def toDbValues(value: java.util.UUID): IndexedSeq[DbValue] =
        IndexedSeq(DbValue.DbUUID(value))
    }
}

object DbCodecDeriver extends DbCodecDeriver(SqlNameMapper.SnakeCase) {
  def withColumnNameMapper(mapper: SqlNameMapper): DbCodecDeriver =
    new DbCodecDeriver(mapper)
}
