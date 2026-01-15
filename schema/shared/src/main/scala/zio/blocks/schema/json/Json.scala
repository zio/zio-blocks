package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}

import scala.util.control.NoStackTrace

// =============================================================================
// JSON ERROR
// =============================================================================

/**
 * Represents an error that occurred during JSON parsing, encoding, or processing.
 *
 * @param message A human-readable description of the error
 * @param path The location in the JSON structure where the error occurred,
 *             represented as a [[DynamicOptic]]
 * @param offset Optional byte offset in the input where the error occurred
 * @param line Optional 1-indexed line number where the error occurred
 * @param column Optional 1-indexed column number where the error occurred
 */
final case class JsonError(
  message: String,
  path: DynamicOptic,
  offset: Option[Long],
  line: Option[Int],
  column: Option[Int]
) extends Exception
    with NoStackTrace {

  override def getMessage: String = {
    val posInfo = (line, column) match {
      case (Some(l), Some(c)) => s" at line $l, column $c"
      case _                  => offset.map(o => s" at offset $o").getOrElse("")
    }
    val pathInfo = if (path.nodes.isEmpty) "" else s" at path $path"
    s"$message$pathInfo$posInfo"
  }

  /**
   * Combines this error with another, preserving both error messages.
   */
  def ++(other: JsonError): JsonError =
    JsonError(s"${this.message}; ${other.message}", this.path, this.offset, this.line, this.column)
}

object JsonError {

  /**
   * Creates a JsonError with only a message, using root path and no position info.
   */
  def apply(message: String): JsonError =
    JsonError(message, DynamicOptic.root, None, None, None)

  /**
   * Creates a JsonError with a message and path, no position info.
   */
  def apply(message: String, path: DynamicOptic): JsonError =
    JsonError(message, path, None, None, None)

  /**
   * Converts a [[SchemaError]] to a [[JsonError]].
   */
  def fromSchemaError(error: SchemaError): JsonError =
    JsonError(error.message, DynamicOptic.root, None, None, None)
}

// =============================================================================
// JSON ADT
// =============================================================================

/**
 * Represents a JSON value.
 *
 * The JSON data model consists of:
 *  - '''Objects''': Unordered collections of key-value pairs
 *  - '''Arrays''': Ordered sequences of values
 *  - '''Strings''': Unicode text
 *  - '''Numbers''': Numeric values (stored as strings for precision)
 *  - '''Booleans''': `true` or `false`
 *  - '''Null''': The null value
 *
 * ==Construction==
 * {{{
 * val obj = Json.Object("name" -> Json.String("Alice"), "age" -> Json.number(30))
 * val arr = Json.Array(Json.String("a"), Json.String("b"))
 * val str = Json.String("hello")
 * val num = Json.number(42)
 * val bool = Json.Boolean(true)
 * val nul = Json.Null
 * }}}
 */
sealed trait Json { self =>

  // ===========================================================================
  // Type Testing
  // ===========================================================================

  /** Returns `true` if this is a JSON object. */
  def isObject: Boolean = false

  /** Returns `true` if this is a JSON array. */
  def isArray: Boolean = false

  /** Returns `true` if this is a JSON string. */
  def isString: Boolean = false

  /** Returns `true` if this is a JSON number. */
  def isNumber: Boolean = false

  /** Returns `true` if this is a JSON boolean. */
  def isBoolean: Boolean = false

  /** Returns `true` if this is JSON null. */
  def isNull: Boolean = false

  // ===========================================================================
  // Direct Accessors
  // ===========================================================================

  /** If this is an object, returns its fields as key-value pairs. Otherwise returns an empty sequence. */
  def fields: Seq[(String, Json)] = Seq.empty

  /** If this is an array, returns its elements. Otherwise returns an empty sequence. */
  def elements: Seq[Json] = Seq.empty

  /** If this is a string, returns its value. Otherwise returns `None`. */
  def stringValue: Option[String] = None

  /** If this is a number, returns its string representation. Otherwise returns `None`. */
  def numberValue: Option[String] = None

  /** If this is a boolean, returns its value. Otherwise returns `None`. */
  def booleanValue: Option[scala.Boolean] = None

  // ===========================================================================
  // DynamicValue Interop
  // ===========================================================================

  /**
   * Converts this JSON to a [[DynamicValue]].
   *
   * This conversion is lossless; all JSON values can be represented as DynamicValue.
   */
  def toDynamicValue: DynamicValue = self match {
    case Json.Null =>
      DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(v) =>
      DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Number(v) =>
      // Preserve as BigDecimal for maximum precision
      DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(v)))
    case Json.String(v) =>
      DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Array(elems) =>
      DynamicValue.Sequence(elems.map(_.toDynamicValue))
    case Json.Object(flds) =>
      DynamicValue.Record(flds.map { case (k, v) => (k, v.toDynamicValue) })
  }

  // ===========================================================================
  // Comparison & Ordering
  // ===========================================================================

  /**
   * Compares this JSON to another for ordering.
   *
   * Order: Null < Boolean < Number < String < Array < Object
   */
  def compare(that: Json): Int = (self, that) match {
    case (Json.Null, Json.Null)           => 0
    case (Json.Null, _)                   => -1
    case (_, Json.Null)                   => 1
    case (Json.Boolean(a), Json.Boolean(b)) => a.compare(b)
    case (Json.Boolean(_), _)             => -1
    case (_, Json.Boolean(_))             => 1
    case (Json.Number(a), Json.Number(b)) => BigDecimal(a).compare(BigDecimal(b))
    case (Json.Number(_), _)              => -1
    case (_, Json.Number(_))              => 1
    case (Json.String(a), Json.String(b)) => a.compare(b)
    case (Json.String(_), _)              => -1
    case (_, Json.String(_))              => 1
    case (Json.Array(a), Json.Array(b))   => compareArrays(a, b)
    case (Json.Array(_), _)               => -1
    case (_, Json.Array(_))               => 1
    case (Json.Object(a), Json.Object(b)) => compareObjects(a, b)
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

  private def compareObjects(a: Vector[(String, Json)], b: Vector[(String, Json)]): Int = {
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

  // ===========================================================================
  // Standard Methods
  // ===========================================================================

  override def hashCode(): Int = self match {
    case Json.Null         => 0
    case Json.Boolean(v)   => v.hashCode()
    case Json.Number(v)    => BigDecimal(v).hashCode()
    case Json.String(v)    => v.hashCode()
    case Json.Array(elems) => elems.hashCode()
    case Json.Object(flds) => flds.sortBy(_._1).hashCode()
  }

  override def equals(that: Any): Boolean = that match {
    case other: Json => compare(other) == 0
    case _           => false
  }
}

object Json {

  // ===========================================================================
  // ADT Cases
  // ===========================================================================

  /**
   * A JSON object: an unordered collection of key-value pairs.
   *
   * @param fields The key-value pairs. Keys should be unique; if duplicates
   *               are present, behavior of accessors is undefined.
   */
  final case class Object(fields: Vector[(String, Json)]) extends Json {
    override def isObject: scala.Boolean     = true
    override def fields: Seq[(String, Json)] = this.fields
  }

  object Object {
    /** Creates an empty JSON object. */
    val empty: Object = Object(Vector.empty)

    /** Creates a JSON object from key-value pairs. */
    def apply(fields: (String, Json)*): Object = Object(fields.toVector)
  }

  /**
   * A JSON array: an ordered sequence of values.
   *
   * @param elements The array elements
   */
  final case class Array(elements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true
    override def elements: Seq[Json]    = this.elements
  }

  object Array {
    /** Creates an empty JSON array. */
    val empty: Array = Array(Vector.empty)

    /** Creates a JSON array from elements. */
    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  /**
   * A JSON string.
   *
   * @param value The string value (unescaped)
   */
  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean               = true
    override def stringValue: Option[java.lang.String] = Some(value)
  }

  /**
   * A JSON number.
   *
   * Stored as a string to preserve exact representation (precision, trailing zeros, etc.).
   * Provides lazy conversion to numeric types.
   *
   * @param value The number as a string (should be valid JSON number syntax)
   */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean               = true
    override def numberValue: Option[java.lang.String] = Some(value)

    /** Converts to `Int`, truncating if necessary. */
    lazy val toInt: Int = toBigDecimal.toInt

    /** Converts to `Long`, truncating if necessary. */
    lazy val toLong: Long = toBigDecimal.toLong

    /** Converts to `Float`. */
    lazy val toFloat: Float = value.toFloat

    /** Converts to `Double`. */
    lazy val toDouble: Double = value.toDouble

    /** Converts to `BigInt`, truncating fractional part. */
    lazy val toBigInt: BigInt = toBigDecimal.toBigInt

    /** Converts to `BigDecimal` (lossless). */
    lazy val toBigDecimal: BigDecimal = BigDecimal(value)
  }

  /**
   * A JSON boolean.
   *
   * @param value The boolean value
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean            = true
    override def booleanValue: Option[scala.Boolean] = Some(value)
  }

  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  /** The JSON null value. */
  case object Null extends Json {
    override def isNull: scala.Boolean = true
  }

  // ===========================================================================
  // Convenience Constructors
  // ===========================================================================

  /** Creates a JSON number from an `Int`. */
  def number(n: Int): Number = Number(n.toString)

  /** Creates a JSON number from a `Long`. */
  def number(n: Long): Number = Number(n.toString)

  /** Creates a JSON number from a `Float`. */
  def number(n: Float): Number = Number(n.toString)

  /** Creates a JSON number from a `Double`. */
  def number(n: Double): Number = Number(n.toString)

  /** Creates a JSON number from a `BigInt`. */
  def number(n: BigInt): Number = Number(n.toString)

  /** Creates a JSON number from a `BigDecimal`. */
  def number(n: BigDecimal): Number = Number(n.toString)

  /** Creates a JSON number from a `Short`. */
  def number(n: Short): Number = Number(n.toString)

  /** Creates a JSON number from a `Byte`. */
  def number(n: Byte): Number = Number(n.toString)

  // ===========================================================================
  // DynamicValue Interop
  // ===========================================================================

  /**
   * Converts a [[DynamicValue]] to JSON.
   *
   * This conversion is lossy for `DynamicValue` types that have no JSON equivalent:
   *  - `PrimitiveValue` types like `java.time.*` are converted to strings
   *  - `DynamicValue.Variant` uses a discriminator field
   *
   * @param value The dynamic value to convert
   * @return The JSON representation
   */
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

  // ===========================================================================
  // Ordering
  // ===========================================================================

  /** Ordering for JSON values. Order: Null < Boolean < Number < String < Array < Object */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)
}
