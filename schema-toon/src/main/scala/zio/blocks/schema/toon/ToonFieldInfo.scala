package zio.blocks.schema.toon

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.binding.{Registers, RegisterOffset}

import scala.annotation.switch

private[toon] final class ToonFieldInfo(
  val span: DynamicOptic.Node.Field,
  defaultValueConstructor: () => ?,
  val idx: Int,
  val isOptional: Boolean,
  val isCollection: Boolean
) {
  private[this] var codec: ToonBinaryCodec[?]             = null
  private[this] var _name: String                         = null
  private[this] var offset: RegisterOffset.RegisterOffset = 0
  var nonTransient: Boolean                               = true

  def name: String = _name

  def setName(name: String): Unit = this._name = name

  def setCodec(codec: ToonBinaryCodec[?]): Unit = this.codec = codec

  def setOffset(offset: RegisterOffset.RegisterOffset): Unit = this.offset = offset

  @inline
  def hasDefault: Boolean = defaultValueConstructor ne null

  /**
   * Returns true if the codec for this field represents a primitive type.
   */
  def isPrimitiveCodec: Boolean = codec.isPrimitive

  def usedRegisters: RegisterOffset.RegisterOffset = codec.valueOffset + offset

  def readValue(in: ToonReader, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    (codec.valueType: @switch) match {
      case 0 =>
        regs.setObject(
          off,
          codec
            .asInstanceOf[ToonBinaryCodec[AnyRef]]
            .decodeValue(in, codec.asInstanceOf[ToonBinaryCodec[AnyRef]].nullValue)
        )
      case 1 =>
        regs.setInt(off, codec.asInstanceOf[ToonBinaryCodec[Int]].decodeValue(in, 0))
      case 2 =>
        regs.setLong(off, codec.asInstanceOf[ToonBinaryCodec[Long]].decodeValue(in, 0L))
      case 3 =>
        regs.setFloat(off, codec.asInstanceOf[ToonBinaryCodec[Float]].decodeValue(in, 0f))
      case 4 =>
        regs.setDouble(off, codec.asInstanceOf[ToonBinaryCodec[Double]].decodeValue(in, 0.0))
      case 5 =>
        regs.setBoolean(off, codec.asInstanceOf[ToonBinaryCodec[Boolean]].decodeValue(in, false))
      case 6 =>
        regs.setByte(off, codec.asInstanceOf[ToonBinaryCodec[Byte]].decodeValue(in, 0.toByte))
      case 7 =>
        regs.setChar(off, codec.asInstanceOf[ToonBinaryCodec[Char]].decodeValue(in, 0.toChar))
      case 8 =>
        regs.setShort(off, codec.asInstanceOf[ToonBinaryCodec[Short]].decodeValue(in, 0.toShort))
      case _ =>
        codec.asInstanceOf[ToonBinaryCodec[Unit]].decodeValue(in, ())
    }
  }

  def readArrayFieldValue(in: ToonReader, regs: Registers, top: RegisterOffset.RegisterOffset, rawKey: String): Unit = {
    val off          = this.offset + top
    val bracketStart = if (rawKey.startsWith("\"")) {
      val closeQuoteIdx = rawKey.indexOf('"', 1)
      if (closeQuoteIdx > 0) rawKey.indexOf('[', closeQuoteIdx + 1)
      else rawKey.indexOf('[')
    } else {
      rawKey.indexOf('[')
    }
    val bracketEnd = rawKey.indexOf(']', bracketStart)
    val lengthStr  = if (bracketStart >= 0 && bracketEnd > bracketStart) {
      rawKey.substring(bracketStart + 1, bracketEnd)
    } else "0"
    val delimChar = if (lengthStr.nonEmpty) {
      val lastChar = lengthStr.charAt(lengthStr.length - 1)
      if (lastChar == '\t') Delimiter.Tab
      else if (lastChar == '|') Delimiter.Pipe
      else Delimiter.Comma
    } else Delimiter.Comma
    val length =
      try {
        val numPart = if (delimChar != Delimiter.Comma) lengthStr.dropRight(1).trim else lengthStr
        numPart.toInt
      } catch {
        case _: NumberFormatException => 0
      }
    val braceStart = rawKey.indexOf('{', bracketEnd)
    val braceEnd   = rawKey.indexOf('}', braceStart)
    if (braceStart > 0 && braceEnd > braceStart) {
      val fieldNamesStr = rawKey.substring(braceStart + 1, braceEnd)
      val fieldNames    = splitFieldNames(fieldNamesStr, delimChar).map { f =>
        val trimmed = f.trim
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2)
          trimmed.substring(1, trimmed.length - 1)
        else trimmed
      }
      in.advanceLine()
      in.skipBlankLines()
      regs.setObject(
        off,
        codec.asInstanceOf[ToonBinaryCodec[AnyRef]].decodeTabularArray(in, fieldNames, length, delimChar)
      )
    } else {
      val remaining = in.peekTrimmedContent
      if (remaining.isEmpty) {
        in.advanceLine()
        in.skipBlankLines()
        regs.setObject(
          off,
          codec.asInstanceOf[ToonBinaryCodec[AnyRef]].decodeListArray(in, length)
        )
      } else {
        in.setActiveDelimiter(delimChar)
        val values = in.readInlineArray()
        regs.setObject(
          off,
          codec.asInstanceOf[ToonBinaryCodec[AnyRef]].decodeInlineArray(in, values, length)
        )
      }
    }
  }

  def setMissingValueOrError(in: ToonReader, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    if (defaultValueConstructor ne null) {
      val defaultValue = defaultValueConstructor.apply()
      (codec.valueType: @switch) match {
        case 0 => regs.setObject(off, defaultValue.asInstanceOf[AnyRef])
        case 1 => regs.setInt(off, defaultValue.asInstanceOf[Int])
        case 2 => regs.setLong(off, defaultValue.asInstanceOf[Long])
        case 3 => regs.setFloat(off, defaultValue.asInstanceOf[Float])
        case 4 => regs.setDouble(off, defaultValue.asInstanceOf[Double])
        case 5 => regs.setBoolean(off, defaultValue.asInstanceOf[Boolean])
        case 6 => regs.setByte(off, defaultValue.asInstanceOf[Byte])
        case 7 => regs.setChar(off, defaultValue.asInstanceOf[Char])
        case 8 => regs.setShort(off, defaultValue.asInstanceOf[Short])
        case _ =>
      }
    } else if (isOptional) {
      regs.setObject(off, None)
    } else if (isCollection) {
      regs.setObject(off, codec.nullValue.asInstanceOf[AnyRef])
    } else {
      in.decodeError(s"Missing required field: $name")
    }
  }

  def writeRequired(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    (codec.valueType: @switch) match {
      case 0 =>
        val value = regs.getObject(off)
        codec.asInstanceOf[ToonBinaryCodec[AnyRef]].encodeAsField(name, value, out)
      case 1 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Int]].encodeValue(regs.getInt(off), out)
        out.newLine()
      case 2 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Long]].encodeValue(regs.getLong(off), out)
        out.newLine()
      case 3 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Float]].encodeValue(regs.getFloat(off), out)
        out.newLine()
      case 4 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Double]].encodeValue(regs.getDouble(off), out)
        out.newLine()
      case 5 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Boolean]].encodeValue(regs.getBoolean(off), out)
        out.newLine()
      case 6 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Byte]].encodeValue(regs.getByte(off), out)
        out.newLine()
      case 7 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Char]].encodeValue(regs.getChar(off), out)
        out.newLine()
      case 8 =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Short]].encodeValue(regs.getShort(off), out)
        out.newLine()
      case _ =>
        out.writeKey(name)
        codec.asInstanceOf[ToonBinaryCodec[Unit]].encodeValue((), out)
        out.newLine()
    }
  }

  def writeOptional(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off   = this.offset + top
    val value = regs.getObject(off).asInstanceOf[Option[?]]
    if (value.isDefined) {
      codec.asInstanceOf[ToonBinaryCodec[Option[Any]]].encodeAsField(name, value, out)
    }
  }

  def writeCollection(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off     = this.offset + top
    val value   = regs.getObject(off)
    val isEmpty = value match {
      case s: Iterable[?] => s.isEmpty
      case _              => false
    }
    if (!isEmpty) {
      codec.asInstanceOf[ToonBinaryCodec[AnyRef]].encodeAsField(name, value, out)
    }
  }

  def writeDefaultValue(out: ToonWriter, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off          = this.offset + top
    val currentValue = (codec.valueType: @switch) match {
      case 0 => regs.getObject(off)
      case 1 => regs.getInt(off)
      case 2 => regs.getLong(off)
      case 3 => regs.getFloat(off)
      case 4 => regs.getDouble(off)
      case 5 => regs.getBoolean(off)
      case 6 => regs.getByte(off)
      case 7 => regs.getChar(off)
      case 8 => regs.getShort(off)
      case _ => ()
    }
    val defaultValue = if (defaultValueConstructor ne null) defaultValueConstructor.apply() else null
    if (currentValue != defaultValue) {
      writeRequired(out, regs, top)
    }
  }

  def writeTabularValue(
    out: ToonWriter,
    regs: Registers,
    top: RegisterOffset.RegisterOffset,
    delimiter: Delimiter
  ): Unit = {
    val off = this.offset + top
    (codec.valueType: @switch) match {
      case 0 =>
        val value = regs.getObject(off)
        if (value == null || value == None) out.writeNull()
        else
          value match {
            case s: String => out.writeString(s, delimiter)
            case _         => codec.asInstanceOf[ToonBinaryCodec[AnyRef]].encodeValue(value, out)
          }
      case 1 => codec.asInstanceOf[ToonBinaryCodec[Int]].encodeValue(regs.getInt(off), out)
      case 2 => codec.asInstanceOf[ToonBinaryCodec[Long]].encodeValue(regs.getLong(off), out)
      case 3 => codec.asInstanceOf[ToonBinaryCodec[Float]].encodeValue(regs.getFloat(off), out)
      case 4 => codec.asInstanceOf[ToonBinaryCodec[Double]].encodeValue(regs.getDouble(off), out)
      case 5 => codec.asInstanceOf[ToonBinaryCodec[Boolean]].encodeValue(regs.getBoolean(off), out)
      case 6 => codec.asInstanceOf[ToonBinaryCodec[Byte]].encodeValue(regs.getByte(off), out)
      case 7 => codec.asInstanceOf[ToonBinaryCodec[Char]].encodeValue(regs.getChar(off), out)
      case 8 => codec.asInstanceOf[ToonBinaryCodec[Short]].encodeValue(regs.getShort(off), out)
      case _ => codec.asInstanceOf[ToonBinaryCodec[Unit]].encodeValue((), out)
    }
  }

  def readTabularValue(value: String, regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top

    val reader = ToonReader(ReaderConfig.withDelimiter(Delimiter.None))
    reader.reset(value)
    (codec.valueType: @switch) match {
      case 0 =>
        regs.setObject(
          off,
          codec
            .asInstanceOf[ToonBinaryCodec[AnyRef]]
            .decodeValue(reader, codec.asInstanceOf[ToonBinaryCodec[AnyRef]].nullValue)
        )
      case 1 =>
        regs.setInt(off, codec.asInstanceOf[ToonBinaryCodec[Int]].decodeValue(reader, 0))
      case 2 =>
        regs.setLong(off, codec.asInstanceOf[ToonBinaryCodec[Long]].decodeValue(reader, 0L))
      case 3 =>
        regs.setFloat(off, codec.asInstanceOf[ToonBinaryCodec[Float]].decodeValue(reader, 0f))
      case 4 =>
        regs.setDouble(off, codec.asInstanceOf[ToonBinaryCodec[Double]].decodeValue(reader, 0.0))
      case 5 =>
        regs.setBoolean(off, codec.asInstanceOf[ToonBinaryCodec[Boolean]].decodeValue(reader, false))
      case 6 =>
        regs.setByte(off, codec.asInstanceOf[ToonBinaryCodec[Byte]].decodeValue(reader, 0.toByte))
      case 7 =>
        regs.setChar(off, codec.asInstanceOf[ToonBinaryCodec[Char]].decodeValue(reader, 0.toChar))
      case 8 =>
        regs.setShort(off, codec.asInstanceOf[ToonBinaryCodec[Short]].decodeValue(reader, 0.toShort))
      case _ =>
        codec.asInstanceOf[ToonBinaryCodec[Unit]].decodeValue(reader, ())
    }
  }

  def setDefaultValue(regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    if (defaultValueConstructor ne null) {
      val defaultValue = defaultValueConstructor.apply()
      (codec.valueType: @switch) match {
        case 0 => regs.setObject(off, defaultValue.asInstanceOf[AnyRef])
        case 1 => regs.setInt(off, defaultValue.asInstanceOf[Int])
        case 2 => regs.setLong(off, defaultValue.asInstanceOf[Long])
        case 3 => regs.setFloat(off, defaultValue.asInstanceOf[Float])
        case 4 => regs.setDouble(off, defaultValue.asInstanceOf[Double])
        case 5 => regs.setBoolean(off, defaultValue.asInstanceOf[Boolean])
        case 6 => regs.setByte(off, defaultValue.asInstanceOf[Byte])
        case 7 => regs.setChar(off, defaultValue.asInstanceOf[Char])
        case 8 => regs.setShort(off, defaultValue.asInstanceOf[Short])
        case _ =>
      }
    }
  }

  def setOptionalNone(regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    regs.setObject(off, None)
  }

  def setEmptyCollection(regs: Registers, top: RegisterOffset.RegisterOffset): Unit = {
    val off = this.offset + top
    regs.setObject(off, codec.nullValue.asInstanceOf[AnyRef])
  }

  private def splitFieldNames(s: String, delim: Delimiter): Array[String] = {
    val result  = new scala.collection.mutable.ArrayBuffer[String]()
    var start   = 0
    var inQuote = false
    var i       = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"') inQuote = !inQuote
      else if (!inQuote && c == delim.char) {
        result.addOne(s.substring(start, i).trim)
        start = i + 1
      }
      i += 1
    }
    if (start <= s.length) result.addOne(s.substring(start).trim)
    result.toArray
  }
}
