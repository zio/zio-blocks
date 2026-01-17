package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * Represents a JSON value.
 *
 * The JSON data model consists of:
 *  - '''Objects''': Unordered collections of key-value pairs with string keys
 *  - '''Arrays''': Ordered sequences of values
 *  - '''Strings''': Unicode text
 *  - '''Numbers''': Numeric values (stored as BigDecimal for precision)
 *  - '''Booleans''': `true` or `false`
 *  - '''Null''': The null value
 *
 * ==Construction==
 * {{{
 * val obj = Json.Object(Vector("name" -> Json.String("Alice"), "age" -> Json.Number(BigDecimal(30))))
 * val arr = Json.Array(Vector(Json.String("a"), Json.String("b")))
 * val str = Json.String("hello")
 * val num = Json.Number(BigDecimal(42))
 * val bool = Json.Boolean(true)
 * val nul = Json.Null
 * }}}
 *
 * ==Pattern Matching==
 * {{{
 * json match {
 *   case Json.Object(fields) => ...
 *   case Json.Array(elements) => ...
 *   case Json.String(value) => ...
 *   case Json.Number(value) => ...
 *   case Json.Boolean(value) => ...
 *   case Json.Null => ...
 * }
 * }}}
 */
sealed trait Json { self =>

  /**
   * Returns the type index for ordering comparisons.
   * Order: Null(0) < Boolean(1) < Number(2) < String(3) < Array(4) < Object(5)
   */
  def typeIndex: Int

  /**
   * Returns `true` if this is a JSON object.
   */
  def isObject: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON array.
   */
  def isArray: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON string.
   */
  def isString: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON number.
   */
  def isNumber: scala.Boolean = false

  /**
   * Returns `true` if this is a JSON boolean.
   */
  def isBoolean: scala.Boolean = false

  /**
   * Returns `true` if this is JSON null.
   */
  def isNull: scala.Boolean = false

  /**
   * Compares this JSON value to another for ordering.
   */
  def compare(that: Json): Int

  final def >(that: Json): Boolean  = compare(that) > 0
  final def >=(that: Json): Boolean = compare(that) >= 0
  final def <(that: Json): Boolean  = compare(that) < 0
  final def <=(that: Json): Boolean = compare(that) <= 0

  /**
   * Converts this JSON to a [[DynamicValue]].
   *
   * This conversion is lossless; all JSON values can be represented as DynamicValue.
   * - JSON objects become DynamicValue.Record (string keys only)
   * - JSON arrays become DynamicValue.Sequence
   * - JSON strings become DynamicValue.Primitive(PrimitiveValue.String)
   * - JSON numbers become DynamicValue.Primitive(PrimitiveValue.BigDecimal)
   * - JSON booleans become DynamicValue.Primitive(PrimitiveValue.Boolean)
   * - JSON null becomes DynamicValue.Primitive(PrimitiveValue.Unit)
   */
  def toDynamicValue: DynamicValue = self match {
    case Json.Null =>
      DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(v) =>
      DynamicValue.Primitive(PrimitiveValue.Boolean(v))
    case Json.Number(v) =>
      DynamicValue.Primitive(PrimitiveValue.BigDecimal(v))
    case Json.String(v) =>
      DynamicValue.Primitive(PrimitiveValue.String(v))
    case Json.Array(elems) =>
      DynamicValue.Sequence(elems.map(_.toDynamicValue))
    case Json.Object(flds) =>
      DynamicValue.Record(flds.map { case (k, v) => (k: java.lang.String, v.toDynamicValue) })
  }
}

object Json {

  /**
   * A JSON object: an unordered collection of key-value pairs.
   *
   * @param fields The key-value pairs. Keys should be unique.
   */
  final case class Object(fields: Vector[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean = true
    def typeIndex: Int             = 5

    def compare(that: Json): Int = that match {
      case thatObj: Object =>
        val xs     = Object.normalized(fields)
        val ys     = Object.normalized(thatObj.fields)
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val (k1, v1) = xs(idx)
          val (k2, v2) = ys(idx)
          var cmp      = k1.compare(k2)
          if (cmp != 0) return cmp
          cmp = v1.compare(v2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => typeIndex - that.typeIndex
    }

    override def equals(obj: Any): scala.Boolean = obj match {
      case Object(otherFields) =>
        // JSON object semantics treat key order as irrelevant.
        // We assume keys are unique (as documented).
        fields.toMap == otherFields.toMap
      case _                   => false
    }

    override def hashCode(): Int = fields.toMap.hashCode()
  }

  object Object {
    val empty: Object = Object(Vector.empty)

    def apply(fields: (java.lang.String, Json)*): Object = Object(fields.toVector)

    private[json] def normalized(fields: Vector[(java.lang.String, Json)]): Vector[(java.lang.String, Json)] =
      fields.sortBy(_._1)
  }

  /**
   * A JSON array: an ordered sequence of values.
   *
   * @param elements The array elements
   */
  final case class Array(elements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true
    def typeIndex: Int            = 4

    def compare(that: Json): Int = that match {
      case thatArr: Array =>
        val xs     = elements
        val ys     = thatArr.elements
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val cmp = xs(idx).compare(ys(idx))
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => typeIndex - that.typeIndex
    }

    override def equals(obj: Any): scala.Boolean = obj match {
      case Array(otherElements) => elements == otherElements
      case _                    => false
    }

    override def hashCode(): Int = elements.hashCode()
  }

  object Array {
    val empty: Array = Array(Vector.empty)

    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  /**
   * A JSON string.
   *
   * @param value The string value (unescaped)
   */
  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean = true
    def typeIndex: Int             = 3

    def compare(that: Json): Int = that match {
      case thatStr: String => value.compare(thatStr.value)
      case _               => typeIndex - that.typeIndex
    }
  }

  /**
   * A JSON number.
   *
   * Stored as BigDecimal to preserve precision.
   *
   * @param value The number value
   */
  final case class Number(value: BigDecimal) extends Json {
    override def isNumber: scala.Boolean = true
    def typeIndex: Int             = 2

    def compare(that: Json): Int = that match {
      case thatNum: Number => value.compare(thatNum.value)
      case _               => typeIndex - that.typeIndex
    }

    def toInt: Int           = value.toInt
    def toLong: Long         = value.toLong
    def toFloat: Float       = value.toFloat
    def toDouble: Double     = value.toDouble
    def toBigInt: BigInt     = value.toBigInt
    def toBigDecimal: BigDecimal = value
  }

  object Number {
    def apply(n: Int): Number         = Number(BigDecimal(n))
    def apply(n: Long): Number        = Number(BigDecimal(n))
    def apply(n: Double): Number      = Number(BigDecimal(n))
    def apply(n: Float): Number       = Number(BigDecimal(n.toDouble))
    def apply(n: BigInt): Number      = Number(BigDecimal(n))
    def apply(s: java.lang.String): Number = Number(BigDecimal(s))
  }

  /**
   * A JSON boolean.
   *
   * @param value The boolean value
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean = true
    def typeIndex: Int              = 1

    def compare(that: Json): Int = that match {
      case thatBool: Boolean => value.compare(thatBool.value)
      case _                 => typeIndex - that.typeIndex
    }
  }

  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  /**
   * The JSON null value.
   */
  case object Null extends Json {
    override def isNull: scala.Boolean = true
    def typeIndex: Int           = 0

    def compare(that: Json): Int = that match {
      case Null => 0
      case _    => typeIndex - that.typeIndex
    }
  }

  /**
   * Ordering for JSON values.
   */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)

  /**
   * Converts a [[DynamicValue]] to JSON.
   *
   * This conversion handles JSON-compatible DynamicValue types:
   * - DynamicValue.Record -> Json.Object (string keys only)
   * - DynamicValue.Sequence -> Json.Array
   * - DynamicValue.Primitive -> appropriate Json leaf type
   *
   * For non-JSON-compatible types (Variant, Map with non-string keys),
   * reasonable conversions are made.
   */
  def fromDynamicValue(value: DynamicValue): Json = value match {
    case DynamicValue.Primitive(pv) => fromPrimitiveValue(pv)
    case DynamicValue.Record(flds) =>
      Object(flds.map { case (k, v) => (k, fromDynamicValue(v)) })
    case DynamicValue.Variant(caseName, v) =>
      // Represent variant as object with _type and _value fields
      Object(Vector("_type" -> String(caseName), "_value" -> fromDynamicValue(v)))
    case DynamicValue.Sequence(elems) =>
      Array(elems.map(fromDynamicValue))
    case DynamicValue.Map(entries) =>
      // Represent map as array of key-value objects
      Array(entries.map { case (k, v) =>
        Object(Vector("key" -> fromDynamicValue(k), "value" -> fromDynamicValue(v)))
      })
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit              => Null
    case PrimitiveValue.Boolean(v)        => Boolean(v)
    case PrimitiveValue.Byte(v)           => Number(BigDecimal(v.toInt))
    case PrimitiveValue.Short(v)          => Number(BigDecimal(v.toInt))
    case PrimitiveValue.Int(v)            => Number(BigDecimal(v))
    case PrimitiveValue.Long(v)           => Number(BigDecimal(v))
    case PrimitiveValue.Float(v)          => Number(BigDecimal(v.toDouble))
    case PrimitiveValue.Double(v)         => Number(BigDecimal(v))
    case PrimitiveValue.Char(v)           => String(v.toString)
    case PrimitiveValue.String(v)         => String(v)
    case PrimitiveValue.BigInt(v)         => Number(BigDecimal(v))
    case PrimitiveValue.BigDecimal(v)     => Number(v)
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
}
