package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}

import java.io.{Reader, Writer}
import java.nio.ByteBuffer

/**
 * Represents a selection of zero or more JSON values, with accumulated errors.
 *
 * `JsonSelection` enables fluent chaining of operations that may fail without
 * requiring immediate error handling. Operations are applied to all values in
 * the selection, and errors are accumulated.
 *
 * {{{
 * val selection: JsonSelection = json.get(p"users[*].name")
 * val result: Either[SchemaError, Vector[Json]] = selection.toEither
 * }}}
 */
final case class JsonSelection(toEither: Either[SchemaError, Vector[Json]]) { self =>

  /**
   * Returns true if this selection contains no values (either empty or errored).
   */
  def isEmpty: Boolean = toEither.fold(_ => true, _.isEmpty)

  /**
   * Returns true if this selection contains at least one value.
   */
  def nonEmpty: Boolean = toEither.fold(_ => false, _.nonEmpty)

  /**
   * Returns the number of values in this selection, or 0 if errored.
   */
  def size: Int = toEither.fold(_ => 0, _.size)

  /**
   * Applies a function to each JSON value in this selection.
   *
   * @param f The transformation function
   * @return A new selection with transformed values
   */
  def map(f: Json => Json): JsonSelection =
    JsonSelection(toEither.map(_.map(f)))

  /**
   * Applies a function returning a selection to each value, flattening results.
   *
   * @param f The function producing selections
   * @return A new selection with all results combined
   */
  def flatMap(f: Json => JsonSelection): JsonSelection =
    JsonSelection(toEither.flatMap { jsons =>
      jsons.foldLeft[Either[SchemaError, Vector[Json]]](Right(Vector.empty)) { (acc, json) =>
        for {
          existing <- acc
          next     <- f(json).toEither
        } yield existing ++ next
      }
    })

  /**
   * Filters values in this selection by a predicate.
   *
   * @param p The predicate to test values
   * @return A new selection containing only values satisfying the predicate
   */
  def filter(p: Json => Boolean): JsonSelection =
    JsonSelection(toEither.map(_.filter(p)))

  /**
   * Collects values for which the partial function is defined.
   *
   * @param pf A partial function to apply
   * @return A new selection with collected results
   */
  def collect(pf: PartialFunction[Json, Json]): JsonSelection =
    JsonSelection(toEither.map(_.collect(pf)))

  /**
   * Navigates to values at the given path within each selected value.
   *
   * @param path The path to navigate
   * @return A new selection with values at the path
   */
  def get(path: DynamicOptic): JsonSelection =
    flatMap(json => json.get(path))

  /**
   * Alias for [[get]].
   */
  def apply(path: DynamicOptic): JsonSelection = get(path)

  /**
   * Navigates to array element at given index within each selected value.
   *
   * @param index The array index
   * @return A new selection with elements at the index
   */
  def apply(index: Int): JsonSelection =
    flatMap(json => json.apply(index))

  /**
   * Navigates to object field with given key within each selected value.
   *
   * @param key The object key
   * @return A new selection with values at the key
   */
  def apply(key: java.lang.String): JsonSelection =
    flatMap(json => json.apply(key))

  /**
   * Filters to only JSON objects.
   */
  def objects: JsonSelection = filter(_.isObject)

  /**
   * Filters to only JSON arrays.
   */
  def arrays: JsonSelection = filter(_.isArray)

  /**
   * Filters to only JSON strings.
   */
  def strings: JsonSelection = filter(_.isString)

  /**
   * Filters to only JSON numbers.
   */
  def numbers: JsonSelection = filter(_.isNumber)

  /**
   * Filters to only JSON booleans.
   */
  def booleans: JsonSelection = filter(_.isBoolean)

  /**
   * Filters to only JSON nulls.
   */
  def nulls: JsonSelection = filter(_.isNull)

  /**
   * Combines this selection with another, concatenating values or errors.
   *
   * @param other The other selection
   * @return A combined selection
   */
  def ++(other: JsonSelection): JsonSelection =
    (toEither, other.toEither) match {
      case (Right(a), Right(b)) => JsonSelection(Right(a ++ b))
      case (Left(a), Left(b))   => JsonSelection(Left(a ++ b))
      case (Left(a), _)         => JsonSelection(Left(a))
      case (_, Left(b))         => JsonSelection(Left(b))
    }

  /**
   * Returns the single value if exactly one, an array of values if there are many, or 
   * otherwise an error.
   */
  def one: Either[SchemaError, Json] =
    toEither.flatMap { jsons =>
      if (jsons.size == 1) Right(jsons.head)
      else if (jsons.size > 1) toArray
      else Left(SchemaError.expectationMismatch(Nil, s"expected exactly one value, got ${jsons.size}"))
    }

  /**
   * Returns the first value if any, otherwise an error.
   */
  def first: Either[SchemaError, Json] =
    toEither.flatMap { jsons =>
      jsons.headOption.toRight(SchemaError.expectationMismatch(Nil, "expected at least one value, got none"))
    }

  /**
   * Returns all values as a [[Json.Array]], or an error.
   */
  def toArray: Either[SchemaError, Json] =
    toEither.map(jsons => Json.Array(jsons))

  /**
   * Unsafe version of [[one]], throws on error or wrong count.
   */
  def oneUnsafe: Json = one.fold(e => throw JsonError.fromSchemaError(e), identity)

  /**
   * Unsafe version of [[first]], throws on error or empty.
   */
  def firstUnsafe: Json = first.fold(e => throw JsonError.fromSchemaError(e), identity)
}

object JsonSelection {

  /**
   * Creates a selection containing a single value.
   */
  def apply(json: Json): JsonSelection = JsonSelection(Right(Vector(json)))

  /**
   * Creates a selection containing multiple values.
   */
  def fromVector(jsons: Vector[Json]): JsonSelection = JsonSelection(Right(jsons))

  /**
   * Creates an empty selection (no values, no error).
   */
  val empty: JsonSelection = JsonSelection(Right(Vector.empty))

  /**
   * Creates a failed selection with the given error.
   */
  def fail(error: SchemaError): JsonSelection = JsonSelection(Left(error))

  /**
   * Creates a failed selection with the given message.
   */
  def fail(message: java.lang.String): JsonSelection =
    JsonSelection(Left(SchemaError.expectationMismatch(Nil, message)))
}

/**
 * Represents a JSON value.
 */
sealed trait Json { self =>

  /**
   * Returns `true` if this is a JSON object.
   */
  def isObject: Boolean = false

  /**
   * Returns `true` if this is a JSON array.
   */
  def isArray: Boolean = false

  /**
   * Returns `true` if this is a JSON string.
   */
  def isString: Boolean = false

  /**
   * Returns `true` if this is a JSON number.
   */
  def isNumber: Boolean = false

  /**
   * Returns `true` if this is a JSON boolean.
   */
  def isBoolean: Boolean = false

  /**
   * Returns `true` if this is JSON null.
   */
  def isNull: Boolean = false

  def asObject: JsonSelection = if (isObject) JsonSelection(self) else JsonSelection.empty
  def asArray: JsonSelection = if (isArray) JsonSelection(self) else JsonSelection.empty
  def asString: JsonSelection = if (isString) JsonSelection(self) else JsonSelection.empty
  def asNumber: JsonSelection = if (isNumber) JsonSelection(self) else JsonSelection.empty
  def asBoolean: JsonSelection = if (isBoolean) JsonSelection(self) else JsonSelection.empty
  def asNull: JsonSelection = if (isNull) JsonSelection(self) else JsonSelection.empty

  def fields: Seq[(java.lang.String, Json)] = Seq.empty
  def elements: Seq[Json] = Seq.empty
  def stringValue: Option[java.lang.String] = None
  def numberValue: Option[java.lang.String] = None
  def booleanValue: Option[scala.Boolean] = None

  def get(path: DynamicOptic): JsonSelection =
    path.nodes.foldLeft(JsonSelection(self)) { (sel, node) =>
      sel.flatMap { json =>
        node match {
          case DynamicOptic.Node.Field(name) =>
            json match {
              case Json.Object(fields) =>
                fields.collectFirst { case (k, v) if k == name => v } match {
                  case Some(v) => JsonSelection(v)
                  case None    => JsonSelection.empty
                }
              case _ => JsonSelection.empty
            }
          case DynamicOptic.Node.AtIndex(index) =>
            json match {
              case Json.Array(elements) if index >= 0 && index < elements.size =>
                JsonSelection(elements(index))
              case _ => JsonSelection.empty
            }
          case DynamicOptic.Node.Elements =>
            json match {
              case Json.Array(elements) => JsonSelection.fromVector(elements)
              case _                    => JsonSelection.empty
            }
          case _ => JsonSelection.empty
        }
      }
    }

  def apply(path: DynamicOptic): JsonSelection = get(path)

  def apply(index: Int): JsonSelection = self match {
    case Json.Array(elems) if index >= 0 && index < elems.size =>
      JsonSelection(elems(index))
    case _ =>
      JsonSelection.empty
  }

  def apply(key: java.lang.String): JsonSelection = self match {
    case Json.Object(flds) =>
      flds.collectFirst { case (k, v) if k == key => v } match {
        case Some(v) => JsonSelection(v)
        case None    => JsonSelection.empty
      }
    case _ =>
      JsonSelection.empty
  }

  def compare(that: Json): Int = (self, that) match {
    case (Json.Null, Json.Null)               => 0
    case (Json.Null, _)                       => -1
    case (_, Json.Null)                       => 1
    case (Json.Boolean(a), Json.Boolean(b))   => a.compare(b)
    case (Json.Boolean(_), _)                 => -1
    case (_, Json.Boolean(_))                 => 1
    case (Json.Number(a), Json.Number(b))     => BigDecimal(a).compare(BigDecimal(b))
    case (Json.Number(_), _)                  => -1
    case (_, Json.Number(_))                  => 1
    case (Json.String(a), Json.String(b))     => a.compare(b)
    case (Json.String(_), _)                  => -1
    case (_, Json.String(_))                  => 1
    case (Json.Array(a), Json.Array(b))       => compareArrays(a, b)
    case (Json.Array(_), _)                   => -1
    case (_, Json.Array(_))                   => 1
    case (Json.Object(a), Json.Object(b))     => compareObjects(a, b)
  }

  private def compareArrays(a: Vector[Json], b: Vector[Json]): Int = {
    val len = math.min(a.size, b.size)
    var i   = 0
    while (i < len) {
      val cmp = a(i).compare(b(i))
      if (cmp != 0) return cmp
      i += 1
    }
    a.size.compare(b.size)
  }

  private def compareObjects(a: Vector[(java.lang.String, Json)], b: Vector[(java.lang.String, Json)]): Int = {
    val aSorted = a.sortBy(_._1)
    val bSorted = b.sortBy(_._1)
    val len     = math.min(aSorted.size, bSorted.size)
    var i       = 0
    while (i < len) {
      val (ak, av) = aSorted(i)
      val (bk, bv) = bSorted(i)
      val keyCmp   = ak.compare(bk)
      if (keyCmp != 0) return keyCmp
      val valCmp = av.compare(bv)
      if (valCmp != 0) return valCmp
      i += 1
    }
    aSorted.size.compare(bSorted.size)
  }

  def toDynamicValue: DynamicValue = self match {
    case Json.Null =>
      DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(v) =>
      DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Number(v) =>
      DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(v)))
    case Json.String(v) =>
      DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Array(elems) =>
      DynamicValue.Sequence(elems.map(_.toDynamicValue))
    case Json.Object(flds) =>
      DynamicValue.Record(flds.map { case (k, v) => (k, v.toDynamicValue) })
  }
  
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(self)

  def asUnsafe[A](implicit decoder: JsonDecoder[A]): A = as[A].fold(throw _, identity)

  private[json] def decodeWith[A](codec: JsonBinaryCodec[A]): Either[JsonError, A] =
    codec.decode(this.encodeToBytes).left.map(JsonError.fromSchemaError)

  def print: java.lang.String = encode(WriterConfig)
  def print(config: WriterConfig): java.lang.String = encode(config)
  def encode: java.lang.String = encode(WriterConfig)
  def encode(config: WriterConfig): java.lang.String =
    JsonBinaryCodec.dynamicValueCodec.encodeToString(toDynamicValue, config)
  
  def printTo(writer: Writer): Unit = printTo(writer, WriterConfig)
  def printTo(writer: Writer, config: WriterConfig): Unit =
    writer.write(encode(config))

  def encodeToBytes: scala.Array[Byte] = encodeToBytes(WriterConfig)
  def encodeToBytes(config: WriterConfig): scala.Array[Byte] =
    JsonBinaryCodec.dynamicValueCodec.encode(toDynamicValue, config)

  def encodeTo(buffer: ByteBuffer): Unit = encodeTo(buffer, WriterConfig)
  def encodeTo(buffer: ByteBuffer, config: WriterConfig): Unit =
    JsonBinaryCodec.dynamicValueCodec.encode(toDynamicValue, buffer, config)

  override def hashCode(): Int = self match {
    case Json.Null           => 0
    case Json.Boolean(v)     => v.hashCode()
    case Json.Number(v)      => BigDecimal(v).hashCode()
    case Json.String(v)      => v.hashCode()
    case Json.Array(elems)   => elems.hashCode()
    case Json.Object(flds)   => flds.sortBy(_._1).hashCode()
  }

  override def equals(that: Any): Boolean = that match {
    case other: Json => compare(other) == 0
    case _           => false
  }

  override def toString: java.lang.String = print
  
  def normalize: Json = sortKeys
  
  def sortKeys: Json = self match {
    case Json.Object(flds) =>
      Json.Object(flds.map { case (k, v) => (k, v.sortKeys) }.sortBy(_._1))
    case Json.Array(elems) =>
      Json.Array(elems.map(_.sortKeys))
    case other =>
      other
  }

  def dropNulls: Json = self match {
    case Json.Object(flds) =>
      Json.Object(flds.collect { case (k, v) if !v.isNull => (k, v.dropNulls) })
    case Json.Array(elems) =>
      Json.Array(elems.map(_.dropNulls))
    case other =>
      other
  }

  def dropEmpty: Json = self match {
    case Json.Object(flds) =>
      val filtered = flds.collect {
        case (k, v) =>
          val dropped = v.dropEmpty
          dropped match {
            case Json.Object(f) if f.isEmpty => None
            case Json.Array(e) if e.isEmpty  => None
            case other                       => Some((k, other))
          }
      }.flatten
      Json.Object(filtered)
    case Json.Array(elems) =>
      val filtered = elems.map(_.dropEmpty).filter {
        case Json.Object(f) if f.isEmpty => false
        case Json.Array(e) if e.isEmpty  => false
        case _                           => true
      }
      Json.Array(filtered)
    case other =>
      other
  }
}

object Json {

  final case class Object(override val fields: Vector[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean = true
  }

  object Object {
    val empty: Object = Object(Vector.empty)
    def apply(fields: (java.lang.String, Json)*): Object = Object(fields.toVector)
  }

  final case class Array(override val elements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true
  }

  object Array {
    val empty: Array = Array(Vector.empty)
    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean              = true
    override def stringValue: Option[java.lang.String] = Some(value)
  }

  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean                = true
    override def numberValue: Option[java.lang.String]  = Some(value)

    lazy val toInt: Int = toBigDecimal.toInt
    lazy val toLong: Long = toBigDecimal.toLong
    lazy val toFloat: Float = value.toFloat
    lazy val toDouble: Double = value.toDouble
    lazy val toBigInt: BigInt = toBigDecimal.toBigInt
    lazy val toBigDecimal: BigDecimal = BigDecimal(value)
  }

  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean              = true
    override def booleanValue: Option[scala.Boolean]   = Some(value)
  }

  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  case object Null extends Json {
    override def isNull: scala.Boolean = true
  }

  def number(n: Int): Number = Number(n.toString)
  def number(n: Long): Number = Number(n.toString)
  def number(n: Float): Number = Number(n.toString)
  def number(n: Double): Number = Number(n.toString)
  def number(n: BigInt): Number = Number(n.toString)
  def number(n: BigDecimal): Number = Number(n.toString)
  def number(n: Short): Number = Number(n.toString)
  def number(n: Byte): Number = Number(n.toString)

  def parse(s: java.lang.String): Either[JsonError, Json] = decode(s)
  def parse(s: CharSequence): Either[JsonError, Json] = decode(s)
  def parse(bytes: scala.Array[Byte]): Either[JsonError, Json] = decode(bytes)
  def parse(buffer: ByteBuffer): Either[JsonError, Json] = decode(buffer)
  def parse(reader: Reader): Either[JsonError, Json] = decode(reader)

  def decode(s: java.lang.String): Either[JsonError, Json] =
    JsonBinaryCodec.dynamicValueCodec.decode(s).map(fromDynamicValue).left.map(JsonError.fromSchemaError)

  def decode(s: CharSequence): Either[JsonError, Json] =
    decode(s.toString)

  def decode(bytes: scala.Array[Byte]): Either[JsonError, Json] =
    JsonBinaryCodec.dynamicValueCodec.decode(bytes).map(fromDynamicValue).left.map(JsonError.fromSchemaError)

  def decode(buffer: ByteBuffer): Either[JsonError, Json] =
    JsonBinaryCodec.dynamicValueCodec.decode(buffer).map(fromDynamicValue).left.map(JsonError.fromSchemaError)

  def decode(reader: Reader): Either[JsonError, Json] =
    try {
      val charBuffer = new scala.Array[Char](8 * 1024)
      val builder    = new StringBuilder
      var numRead    = 0
      while ({
        numRead = reader.read(charBuffer)
        numRead >= 0
      }) builder.appendAll(charBuffer, 0, numRead)
      decode(builder.toString)
    } catch {
      case e: Exception => Left(JsonError(e.getMessage))
    }

  def parseUnsafe(s: java.lang.String): Json = decode(s).fold(throw _, identity)
  def decodeUnsafe(s: java.lang.String): Json = parseUnsafe(s)

  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  private[json] def encodeWith[A](value: A, codec: JsonBinaryCodec[A]): Json =
    Json.parseUnsafe(codec.encodeToString(value))

  def fromDynamicValue(value: DynamicValue): Json = value match {
    case DynamicValue.Primitive(pv) => fromPrimitiveValue(pv)
    case DynamicValue.Record(flds) =>
      Object(flds.map { case (k, v) => (k, fromDynamicValue(v)) })
    case DynamicValue.Variant(caseName, v) =>
      Object(Vector("_type" -> String(caseName), "_value" -> fromDynamicValue(v)))
    case DynamicValue.Sequence(elems) =>
      Array(elems.map(fromDynamicValue))
    case DynamicValue.Map(entries) =>
      Array(entries.map { case (k, v) =>
        Object(Vector("key" -> fromDynamicValue(k), "value" -> fromDynamicValue(v)))
      })
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case PrimitiveValue.Boolean(v)        => Boolean(v)
    case PrimitiveValue.Byte(v)           => number(v)
    case PrimitiveValue.Short(v)          => number(v)
    case PrimitiveValue.Int(v)            => number(v)
    case PrimitiveValue.Long(v)           => number(v)
    case PrimitiveValue.Float(v)          => number(v)
    case PrimitiveValue.Double(v)         => number(v)
    case PrimitiveValue.Char(v)           => String(v.toString)
    case PrimitiveValue.String(v)         => String(v)
    case PrimitiveValue.BigInt(v)         => number(v)
    case PrimitiveValue.BigDecimal(v)     => number(v)
    case PrimitiveValue.DayOfWeek(v)      => String(v.toString)
    case PrimitiveValue.Duration(v)       => String(v.toString)
    case PrimitiveValue.Instant(v)        => String(v.toString)
    case PrimitiveValue.LocalDate(v)      => String(v.toString)
    case PrimitiveValue.LocalDateTime(v)  => String(v.toString)
    case PrimitiveValue.LocalTime(v)      => String(v.toString)
    case PrimitiveValue.Month(v)          => String(v.toString)
    case PrimitiveValue.MonthDay(v)       => String(v.toString)
    case PrimitiveValue.OffsetDateTime(v) => String(v.toString)
    case PrimitiveValue.OffsetTime(v)     => String(v.toString)
    case PrimitiveValue.Period(v)         => String(v.toString)
    case PrimitiveValue.Year(v)           => String(v.toString)
    case PrimitiveValue.YearMonth(v)      => String(v.toString)
    case PrimitiveValue.ZoneId(v)         => String(v.getId)
    case PrimitiveValue.ZoneOffset(v)     => String(v.toString)
    case PrimitiveValue.ZonedDateTime(v)  => String(v.toString)
    case PrimitiveValue.Currency(v)       => String(v.getCurrencyCode)
    case PrimitiveValue.UUID(v)           => String(v.toString)
  }

  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)
}

sealed trait MergeStrategy

object MergeStrategy {
  case object Auto extends MergeStrategy
  case object Deep extends MergeStrategy
  case object Shallow extends MergeStrategy
  case object Concat extends MergeStrategy
  case object Replace extends MergeStrategy
  final case class Custom(f: (DynamicOptic, Json, Json) => Json) extends MergeStrategy
}
