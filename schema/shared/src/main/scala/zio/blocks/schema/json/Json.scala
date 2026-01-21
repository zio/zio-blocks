package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.DynamicOptic.Node
import scala.math.Ordering
import zio.blocks.schema.json.{JsonBinaryCodec, JsonReader, JsonWriter}

/**
 * Represents a JSON value.
 *
 * The JSON data model consists of:
 *   - '''Objects''': Unordered collections of key-value pairs
 *   - '''Arrays''': Ordered sequences of values
 *   - '''Strings''': Unicode text
 *   - '''Numbers''': Numeric values (stored as strings for precision)
 *   - '''Booleans''': `true` or `false`
 *   - '''Null''': The null value
 */
sealed trait Json { self =>

  /**
   * Type index for ordering: Null=0, Boolean=1, Number=2, String=3, Array=4,
   * Object=5
   */
  def typeIndex: Int

  /**
   * Compares this JSON to another for ordering.
   */
  def compare(that: Json): Int

  // ===========================================================================
  // Type Testing
  // ===========================================================================

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

  // ===========================================================================
  // Direct Accessors
  // ===========================================================================

  /**
   * If this is an object, returns its fields as key-value pairs. Otherwise
   * returns an empty sequence.
   */
  def fields: Seq[(String, Json)] = Seq.empty

  /**
   * If this is an array, returns its elements. Otherwise returns an empty
   * sequence.
   */
  def elements: Seq[Json] = Seq.empty

  /**
   * If this is a string, returns its value. Otherwise returns `None`.
   */
  def stringValue: Option[String] = None

  /**
   * If this is a number, returns its string representation. Otherwise returns
   * `None`.
   */
  def numberValue: Option[String] = None

  /**
   * If this is a boolean, returns its value. Otherwise returns `None`.
   */
  def booleanValue: Option[scala.Boolean] = None

  // ===========================================================================
  // Navigation
  // ===========================================================================

  /**
   * Selects values at the given index if this is an array.
   */
  def apply(index: Int): JsonSelection = self match {
    case Json.Array(elems) if index >= 0 && index < elems.length =>
      JsonSelection(Right(Vector(elems(index))))
    case _ =>
      JsonSelection.empty
  }

  /**
   * Selects values with the given key if this is an object.
   */
  def apply(key: String): JsonSelection = self match {
    case Json.Object(fields) =>
      val matches = fields.collect { case (k, v) if k == key => v }
      JsonSelection(Right(matches))
    case _ =>
      JsonSelection.empty
  }

  /**
   * Navigates using a DynamicOptic path.
   */
  def get(path: DynamicOptic): JsonSelection =
    path.nodes.foldLeft(JsonSelection(self)) { (acc, node) =>
      acc.flatMap { json =>
        (json, node) match {
          case (Json.Object(fields), Node.Field(name)) =>
            val matches = fields.collect { case (k, v) if k == name => v }
            JsonSelection.fromVector(matches)
          case (Json.Array(elems), Node.AtIndex(index)) =>
            if (index >= 0 && index < elems.length) JsonSelection(elems(index))
            else JsonSelection.empty
          case _ => JsonSelection.empty
        }
      }
    }

  /**
   * Modifies the value at the given path.
   */
  def modify(path: DynamicOptic, f: Json => Json): Json =
    if (path.nodes.isEmpty) f(self)
    else {
      val head = path.nodes.head
      val tail = new DynamicOptic(path.nodes.tail)
      (self, head) match {
        case (Json.Object(fields), Node.Field(name)) =>
          val newFields = fields.map {
            case (k, v) if k == name => (k, v.modify(tail, f))
            case other               => other
          }
          Json.Object(newFields)
        case (Json.Array(elems), Node.AtIndex(index)) =>
          if (index >= 0 && index < elems.length) {
            val newElems = elems.updated(index, elems(index).modify(tail, f))
            Json.Array(newElems)
          } else self
        case _ => self
      }
    }

  /**
   * Sets the value at the given path.
   */
  def set(path: DynamicOptic, value: Json): Json = modify(path, _ => value)

  /**
   * Deletes the value at the given path.
   */
  def delete(path: DynamicOptic): Json =
    if (path.nodes.isEmpty) self
    else {
      val parentPath = new DynamicOptic(path.nodes.init)
      val targetNode = path.nodes.last
      modify(parentPath, _.deleteNode(targetNode))
    }

  private def deleteNode(node: DynamicOptic.Node): Json = (self, node) match {
    case (Json.Object(fields), Node.Field(name)) =>
      Json.Object(fields.filterNot(_._1 == name))
    case (Json.Array(elems), Node.AtIndex(index)) =>
      if (index >= 0 && index < elems.length) Json.Array(elems.patch(index, Vector.empty, 1))
      else self
    case _ => self
  }

  /**
   * Decodes this JSON value to a value of type `A`.
   */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] = decoder.decode(self)

  // ===========================================================================
  // DynamicValue Conversion
  // ===========================================================================

  /**
   * Converts this JSON value to a [[DynamicValue]].
   */
  def toDynamicValue: DynamicValue = self match {
    case Json.Null =>
      DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Boolean(b) =>
      DynamicValue.Primitive(PrimitiveValue.Boolean(b))
    case Json.Number(s) =>
      // Use BigDecimal to preserve precision
      try {
        DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(s)))
      } catch {
        case _: NumberFormatException =>
          DynamicValue.Primitive(PrimitiveValue.String(s))
      }
    case Json.String(s) =>
      DynamicValue.Primitive(PrimitiveValue.String(s))
    case Json.Array(elems) =>
      DynamicValue.Sequence(elems.map(_.toDynamicValue))
    case Json.Object(fields) =>
      DynamicValue.Record(fields.map { case (k, v) => (k, v.toDynamicValue) })
  }

  // ===========================================================================
  // Standard Methods
  // ===========================================================================

  override def toString: String = self match {
    case Json.Null         => "null"
    case Json.Boolean(v)   => v.toString
    case Json.Number(v)    => v
    case Json.String(v)    => "\"" + v + "\""
    case Json.Array(elems) => elems.mkString("[", ",", "]")
    case Json.Object(flds) => flds.map { case (k, v) => "\"" + k + "\":" + v }.mkString("{", ",", "}")
  }
}

object Json {

  /**
   * Encodes a value of type `A` to JSON.
   */
  def from[A](value: A)(implicit encoder: JsonEncoder[A]): Json = encoder.encode(value)

  // ===========================================================================
  // ADT Cases
  // ===========================================================================

  /**
   * The JSON null value.
   */
  case object Null extends Json {
    override def isNull: scala.Boolean = true
    override def typeIndex: Int        = 0

    override def compare(that: Json): Int = that match {
      case Null => 0
      case _    => -that.typeIndex
    }
  }

  /**
   * A JSON boolean.
   *
   * @param value
   *   The boolean value
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean            = true
    override def booleanValue: Option[scala.Boolean] = Some(value)
    override def typeIndex: Int                      = 1

    override def compare(that: Json): Int = that match {
      case Boolean(thatValue) => value.compare(thatValue)
      case _                  => 1 - that.typeIndex
    }
  }

  object Boolean {
    val True: Boolean  = Boolean(true)
    val False: Boolean = Boolean(false)
  }

  /**
   * A JSON number.
   *
   * Stored as a string to preserve exact representation (precision, trailing
   * zeros, etc.).
   *
   * @param value
   *   The number as a string (should be valid JSON number syntax)
   */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean               = true
    override def numberValue: Option[java.lang.String] = Some(value)
    override def typeIndex: Int                        = 2

    override def compare(that: Json): Int = that match {
      case Number(thatValue) => BigDecimal(value).compare(BigDecimal(thatValue))
      case _                 => 2 - that.typeIndex
    }

    /**
     * Converts to `Int`, truncating if necessary.
     */
    lazy val toInt: Int = toBigDecimal.toInt

    /**
     * Converts to `Long`, truncating if necessary.
     */
    lazy val toLong: Long = toBigDecimal.toLong

    /**
     * Converts to `Float`.
     */
    lazy val toFloat: Float = value.toFloat

    /**
     * Converts to `Double`.
     */
    lazy val toDouble: Double = value.toDouble

    /**
     * Converts to `BigInt`, truncating fractional part.
     */
    lazy val toBigInt: BigInt = toBigDecimal.toBigInt

    /**
     * Converts to `BigDecimal` (lossless).
     */
    lazy val toBigDecimal: BigDecimal = BigDecimal(value)
  }

  /**
   * A JSON string.
   *
   * @param value
   *   The string value (unescaped)
   */
  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean               = true
    override def stringValue: Option[java.lang.String] = Some(value)
    override def typeIndex: Int                        = 3

    override def compare(that: Json): Int = that match {
      case String(thatValue) => value.compareTo(thatValue)
      case _                 => 3 - that.typeIndex
    }
  }

  /**
   * A JSON array: an ordered sequence of values.
   *
   * @param elements
   *   The array elements
   */
  final case class Array(elems: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true
    override def elements: Seq[Json]    = elems
    override def typeIndex: Int         = 4

    override def equals(that: Any): scala.Boolean = that match {
      case Array(thatElems) =>
        val len = elems.length
        if (len != thatElems.length) return false
        var idx = 0
        while (idx < len) {
          if (elems(idx) != thatElems(idx)) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = elems.hashCode

    override def compare(that: Json): Int = that match {
      case Array(thatElems) =>
        val xs     = elems
        val ys     = thatElems
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
      case _ => 4 - that.typeIndex
    }
  }

  object Array {

    /**
     * Creates an empty JSON array.
     */
    val empty: Array = Array(Vector.empty)

    /**
     * Creates a JSON array from elements.
     */
    def apply(elements: Json*): Array = Array(elements.toVector)
  }

  /**
   * A JSON object: an unordered collection of key-value pairs.
   *
   * Equality and comparison are order-independent: `{"a":1, "b":2}` equals
   * `{"b":2, "a":1}`.
   *
   * @param flds
   *   The key-value pairs. Keys should be unique; if duplicates are present,
   *   behavior of accessors is undefined.
   */
  final case class Object(flds: Vector[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean               = true
    override def fields: Seq[(java.lang.String, Json)] = flds
    override def typeIndex: Int                        = 5

    /**
     * Cached sorted fields for order-independent comparison.
     */
    private lazy val sortedFields: Vector[(java.lang.String, Json)] = flds.sortBy(_._1)

    override def equals(that: Any): scala.Boolean = that match {
      case thatObj: Object =>
        if (flds.length != thatObj.flds.length) return false
        val xs  = sortedFields
        val ys  = thatObj.sortedFields
        val len = xs.length
        var idx = 0
        while (idx < len) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = sortedFields.hashCode

    override def compare(that: Json): Int = that match {
      case thatObj: Object =>
        val xs     = sortedFields
        val ys     = thatObj.sortedFields
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compareTo(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 5 - that.typeIndex
    }
  }

  object Object {

    /**
     * Creates an empty JSON object.
     */
    val empty: Object = Object(Vector.empty)

    /**
     * Creates a JSON object from key-value pairs.
     */
    def apply(fields: (java.lang.String, Json)*): Object = Object(fields.toVector)
  }

  // ===========================================================================
  // Convenience Constructors
  // ===========================================================================

  /**
   * Creates a JSON number from an `Int`.
   */
  def number(n: Int): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Long`.
   */
  def number(n: Long): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Float`.
   */
  def number(n: Float): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Double`.
   */
  def number(n: Double): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `BigInt`.
   */
  def number(n: BigInt): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `BigDecimal`.
   */
  def number(n: BigDecimal): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Short`.
   */
  def number(n: Short): Number = Number(n.toString)

  /**
   * Creates a JSON number from a `Byte`.
   */
  def number(n: Byte): Number = Number(n.toString)

  // ===========================================================================
  // DynamicValue Conversion
  // ===========================================================================

  /**
   * Converts a [[DynamicValue]] to a JSON value.
   */
  def fromDynamicValue(dv: DynamicValue): Json = dv match {
    case DynamicValue.Primitive(pv) =>
      pv match {
        case PrimitiveValue.Unit           => Json.Null
        case PrimitiveValue.Boolean(b)     => Json.Boolean(b)
        case PrimitiveValue.Byte(b)        => Json.Number(b.toString)
        case PrimitiveValue.Short(s)       => Json.Number(s.toString)
        case PrimitiveValue.Int(i)         => Json.Number(i.toString)
        case PrimitiveValue.Long(l)        => Json.Number(l.toString)
        case PrimitiveValue.Float(f)       => Json.Number(f.toString)
        case PrimitiveValue.Double(d)      => Json.Number(d.toString)
        case PrimitiveValue.BigInt(bi)     => Json.Number(bi.toString)
        case PrimitiveValue.BigDecimal(bd) => Json.Number(bd.toString)
        case PrimitiveValue.String(s)      => Json.String(s)
        case PrimitiveValue.Char(c)        => Json.String(c.toString)
        case other                         => Json.String(other.toString)

      }
    case DynamicValue.Record(fields) =>
      Json.Object(fields.map { case (k, v) => (k, fromDynamicValue(v)) })
    case DynamicValue.Sequence(elements) =>
      Json.Array(elements.map(fromDynamicValue))
    case DynamicValue.Map(entries) =>
      // Convert map entries to JSON object if keys are strings, otherwise to array of pairs
      val allKeysAreStrings = entries.forall {
        case (DynamicValue.Primitive(PrimitiveValue.String(_)), _) => true
        case _                                                     => false
      }
      if (allKeysAreStrings) {
        val fields = entries.map {
          case (DynamicValue.Primitive(PrimitiveValue.String(k)), v) => (k, fromDynamicValue(v))
          case _                                                     => throw new IllegalStateException("Unexpected non-string key")
        }
        Json.Object(fields)
      } else {
        // Represent as array of [key, value] pairs
        Json.Array(entries.map { case (k, v) =>
          Json.Array(Vector(fromDynamicValue(k), fromDynamicValue(v)))
        })
      }
    case DynamicValue.Variant(caseName, value) =>
      // Represent variant as object with single field
      Json.Object(Vector((caseName, fromDynamicValue(value))))
  }

  // ===========================================================================
  // Codec & Parsing
  // ===========================================================================

  implicit val codec: JsonBinaryCodec[Json] = new JsonBinaryCodec[Json] {
    def decodeValue(in: JsonReader, default: Json): Json = {
      val b = in.nextToken()
      if (b == '"') {
        in.rollbackToken()
        Json.String(in.readString(null))
      } else if (b == 'f' || b == 't') {
        in.rollbackToken()
        Json.Boolean(in.readBoolean())
      } else if (b >= '0' && b <= '9' || b == '-') {
        in.rollbackToken()
        val bd = in.readBigDecimal(null)
        Json.Number(bd.toString)
      } else if (b == '[') {
        if (in.isNextToken(']')) Json.Array(Vector.empty)
        else {
          in.rollbackToken()
          val builder = new scala.collection.immutable.VectorBuilder[Json]
          while ({
            builder.addOne(decodeValue(in, default))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken(']')) Json.Array(builder.result())
          else in.arrayEndOrCommaError()
        }
      } else if (b == '{') {
        if (in.isNextToken('}')) Json.Object(Vector.empty)
        else {
          in.rollbackToken()
          val builder = new scala.collection.immutable.VectorBuilder[(java.lang.String, Json)]
          while ({
            builder.addOne((in.readKeyAsString(), decodeValue(in, default)))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken('}')) Json.Object(builder.result())
          else in.objectEndOrCommaError()
        }
      } else {
        in.rollbackToken()
        in.readNullOrError(Json.Null, "expected JSON value")
      }
    }

    def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case Json.Null       => out.writeNull()
      case Json.Boolean(b) => out.writeVal(b)
      case Json.Number(s)  => out.writeVal(BigDecimal(s))
      case Json.String(s)  => out.writeVal(s)
      case Json.Array(xs)  =>
        out.writeArrayStart()
        xs.foreach(encodeValue(_, out))
        out.writeArrayEnd()
      case Json.Object(xs) =>
        out.writeObjectStart()
        xs.foreach { case (k, v) =>
          out.writeKey(k)
          encodeValue(v, out)
        }
        out.writeObjectEnd()
    }
  }

  /**
   * Parses a string into a JSON value.
   */
  def parse(s: java.lang.String): Either[JsonError, Json] =
    codec.decode(s).left.map(e => JsonError(e.getMessage))

  /**
   * Encodes a JSON value into a string.
   */
  def encode(json: Json): java.lang.String = codec.encodeToString(json)

  // ===========================================================================
  // Ordering
  // ===========================================================================

  /**
   * Ordering for JSON values.
   *
   * Order: Null < Boolean < Number < String < Array < Object
   */
  implicit val ordering: Ordering[Json] = (x: Json, y: Json) => x.compare(y)
}
