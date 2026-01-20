package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

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
 *
 * ==Navigation==
 * {{{
 * json.get(p"users[0].name")   // JsonSelection
 * json("users")(0)("name")     // JsonSelection
 * json.fields                  // for objects
 * json.elements                // for arrays
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

  // ===========================================================================
  // Type Testing
  // ===========================================================================

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

  // ===========================================================================
  // Type Filtering (returns JsonSelection)
  // ===========================================================================

  /**
   * Returns a [[JsonSelection]] containing this value if it is an object,
   * otherwise an empty selection.
   */
  def asObject: JsonSelection = if (isObject) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is an array,
   * otherwise an empty selection.
   */
  def asArray: JsonSelection = if (isArray) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a string,
   * otherwise an empty selection.
   */
  def asString: JsonSelection = if (isString) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a number,
   * otherwise an empty selection.
   */
  def asNumber: JsonSelection = if (isNumber) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is a boolean,
   * otherwise an empty selection.
   */
  def asBoolean: JsonSelection = if (isBoolean) JsonSelection(self) else JsonSelection.empty

  /**
   * Returns a [[JsonSelection]] containing this value if it is null,
   * otherwise an empty selection.
   */
  def asNull: JsonSelection = if (isNull) JsonSelection(self) else JsonSelection.empty

  // ===========================================================================
  // Direct Accessors
  // ===========================================================================

  /**
   * If this is an object, returns its fields as key-value pairs.
   * Otherwise returns an empty sequence.
   */
  def fields: Seq[(java.lang.String, Json)] = Seq.empty

  /**
   * If this is an array, returns its elements.
   * Otherwise returns an empty sequence.
   */
  def elements: Seq[Json] = Seq.empty

  /**
   * If this is a string, returns its value.
   * Otherwise returns `None`.
   */
  def stringValue: Option[java.lang.String] = None

  /**
   * If this is a number, returns its string representation.
   * Otherwise returns `None`.
   */
  def numberValue: Option[java.lang.String] = None

  /**
   * If this is a boolean, returns its value.
   * Otherwise returns `None`.
   */
  def booleanValue: Option[scala.Boolean] = None

  // ===========================================================================
  // Navigation
  // ===========================================================================

  /**
   * Navigates to values at the given path.
   *
   * {{{
   * json.get(p"users[0].name")
   * json.get(DynamicOptic.root.field("users").at(0).field("name"))
   * }}}
   *
   * @param path The path to navigate
   * @return A [[JsonSelection]] containing values at the path
   */
  def get(path: DynamicOptic): JsonSelection = {
    // TODO: Implement path navigation using DynamicOptic.nodes
    val _ = path // suppress unused warning
    JsonSelection.empty
  }

  /**
   * Alias for [[get]].
   */
  def apply(path: DynamicOptic): JsonSelection = get(path)

  /**
   * If this is an array, returns a selection containing the element at the given index.
   * Returns an empty selection if not an array or index is out of bounds.
   *
   * @param index The array index (0-based)
   */
  def apply(index: Int): JsonSelection = self match {
    case Json.Array(elems) if index >= 0 && index < elems.size =>
      JsonSelection(elems(index))
    case _ =>
      JsonSelection.empty
  }

  /**
   * If this is an object, returns a selection containing the value at the given key.
   * Returns an empty selection if not an object or key is not present.
   *
   * @param key The object key
   */
  def apply(key: java.lang.String): JsonSelection = self match {
    case Json.Object(flds) =>
      flds.collectFirst { case (k, v) if k == key => v } match {
        case Some(v) => JsonSelection(v)
        case None    => JsonSelection.empty
      }
    case _ =>
      JsonSelection.empty
  }

  // ===========================================================================
  // Comparison
  // ===========================================================================

  /**
   * Compares this JSON to another for ordering.
   *
   * Ordering is defined as:
   *  1. Null < Boolean < Number < String < Array < Object
   *  2. Within types, natural ordering applies
   */
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

  // ===========================================================================
  // Standard Methods
  // ===========================================================================

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

  override def toString: String = {
    // TODO: Implement proper JSON encoding
    this match {
      case Json.Null => "null"
      case Json.Boolean(v) => v.toString
      case Json.Number(v) => v
      case Json.String(v) => s""""$v""""
      case Json.Array(elems) => elems.mkString("[", ",", "]")
      case Json.Object(flds) => flds.map { case (k, v) => s""""$k":$v""" }.mkString("{", ",", "}")
    }
  }
}

object Json {

  // ===========================================================================
  // ADT Cases
  // ===========================================================================

  /**
   * Represents a JSON object (unordered collection of key-value pairs).
   *
   * @param fields The object's fields as key-value pairs
   */
  final case class Object(override val fields: Vector[(java.lang.String, Json)]) extends Json {
    override def isObject: scala.Boolean = true

    /**
     * Returns the value associated with the given key, if present.
     */
    def get(key: java.lang.String): Option[Json] =
      fields.collectFirst { case (k, v) if k == key => v }

    /**
     * Returns a new object with the given key-value pair added or updated.
     */
    def +(kv: (java.lang.String, Json)): Object = {
      val (key, value) = kv
      val filtered     = fields.filterNot(_._1 == key)
      Object(filtered :+ (key -> value))
    }

    /**
     * Returns a new object with the given key removed.
     */
    def -(key: java.lang.String): Object =
      Object(fields.filterNot(_._1 == key))
  }

  object Object {
    /**
     * Creates an empty JSON object.
     */
    val empty: Object = Object(Vector.empty)

    /**
     * Creates a JSON object from key-value pairs.
     */
    def apply(fields: (java.lang.String, Json)*): Object =
      Object(fields.toVector)
  }

  /**
   * Represents a JSON array (ordered sequence of values).
   *
   * @param elements The array's elements
   */
  final case class Array(override val elements: Vector[Json]) extends Json {
    override def isArray: scala.Boolean = true

    /**
     * Returns the element at the given index, if present.
     */
    def get(index: Int): Option[Json] =
      if (index >= 0 && index < elements.size) Some(elements(index))
      else None

    /**
     * Returns a new array with the given element appended.
     */
    def :+(elem: Json): Array =
      Array(elements :+ elem)

    /**
     * Returns a new array with the given element prepended.
     */
    def +:(elem: Json): Array =
      Array(elem +: elements)

    /**
     * Returns the size of the array.
     */
    def size: Int = elements.size
  }

  object Array {
    /**
     * Creates an empty JSON array.
     */
    val empty: Array = Array(Vector.empty)

    /**
     * Creates a JSON array from elements.
     */
    def apply(elements: Json*): Array =
      Array(elements.toVector)
  }

  /**
   * Represents a JSON string.
   *
   * @param value The string value
   */
  final case class String(value: java.lang.String) extends Json {
    override def isString: scala.Boolean = true

    override def stringValue: Option[java.lang.String] = Some(value)
  }

  /**
   * Represents a JSON number.
   *
   * Numbers are stored as strings to preserve precision and avoid
   * floating-point rounding errors.
   *
   * @param value The string representation of the number
   */
  final case class Number(value: java.lang.String) extends Json {
    override def isNumber: scala.Boolean = true

    override def numberValue: Option[java.lang.String] = Some(value)

    /**
     * Converts this number to a [[BigDecimal]].
     */
    def toBigDecimal: BigDecimal = BigDecimal(value)

    /**
     * Converts this number to an [[Int]], if possible.
     */
    def toInt: Option[Int] =
      try Some(BigDecimal(value).toIntExact)
      catch { case _: ArithmeticException => None }

    /**
     * Converts this number to a [[Long]], if possible.
     */
    def toLong: Option[Long] =
      try Some(BigDecimal(value).toLongExact)
      catch { case _: ArithmeticException => None }

    /**
     * Converts this number to a [[Double]].
     */
    def toDouble: Double = value.toDouble
  }

  /**
   * Represents a JSON boolean.
   *
   * @param value The boolean value
   */
  final case class Boolean(value: scala.Boolean) extends Json {
    override def isBoolean: scala.Boolean = true

    override def booleanValue: Option[scala.Boolean] = Some(value)
  }

  /**
   * Represents JSON null.
   */
  case object Null extends Json {
    override def isNull: scala.Boolean = true
  }

  // ===========================================================================
  // Smart Constructors
  // ===========================================================================

  /**
   * Creates a JSON number from an [[Int]].
   */
  def number(value: Int): Number = Number(value.toString)

  /**
   * Creates a JSON number from a [[Long]].
   */
  def number(value: Long): Number = Number(value.toString)

  /**
   * Creates a JSON number from a [[Double]].
   */
  def number(value: Double): Number = Number(value.toString)

  /**
   * Creates a JSON number from a [[BigDecimal]].
   */
  def number(value: BigDecimal): Number = Number(value.toString)

  /**
   * Creates a JSON number from a string representation.
   */
  def number(value: java.lang.String): Number = Number(value)

  // More methods will be added in subsequent edits...
}

