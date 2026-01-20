package zio.blocks.schema.json

/**
 * Represents a selection of JSON values, supporting fluent navigation and
 * transformation.
 *
 * A [[JsonSelection]] can contain zero, one, or multiple JSON values. It
 * provides methods for navigating, filtering, and transforming JSON structures
 * in a type-safe way.
 *
 * ==Examples==
 * {{{
 * // Navigation
 * json("users")(0)("name")  // JsonSelection
 *
 * // Filtering
 * json("users").filter(_.isObject)
 *
 * // Transformation
 * json("users").map(user => user("name"))
 *
 * // Terminal operations
 * json("users")(0)("name").one  // Either[JsonError, Json]
 * json("users").toArray         // Array[Json]
 * }}}
 *
 * @param values
 *   The selected JSON values
 */
final case class JsonSelection(values: Vector[Json]) {

  // ===========================================================================
  // Type Filtering
  // ===========================================================================

  /**
   * Filters this selection to only include objects.
   */
  def objects: JsonSelection =
    JsonSelection(values.filter(_.isObject))

  /**
   * Filters this selection to only include arrays.
   */
  def arrays: JsonSelection =
    JsonSelection(values.filter(_.isArray))

  /**
   * Filters this selection to only include strings.
   */
  def strings: JsonSelection =
    JsonSelection(values.filter(_.isString))

  /**
   * Filters this selection to only include numbers.
   */
  def numbers: JsonSelection =
    JsonSelection(values.filter(_.isNumber))

  /**
   * Filters this selection to only include booleans.
   */
  def booleans: JsonSelection =
    JsonSelection(values.filter(_.isBoolean))

  /**
   * Filters this selection to only include null values.
   */
  def nulls: JsonSelection =
    JsonSelection(values.filter(_.isNull))

  // ===========================================================================
  // Navigation
  // ===========================================================================

  /**
   * For each object in this selection, selects the value at the given key.
   */
  def apply(key: java.lang.String): JsonSelection =
    JsonSelection(values.flatMap(_.apply(key).values))

  /**
   * For each array in this selection, selects the element at the given index.
   */
  def apply(index: Int): JsonSelection =
    JsonSelection(values.flatMap(_.apply(index).values))

  // ===========================================================================
  // Transformation
  // ===========================================================================

  /**
   * Maps each value in this selection using the given function.
   */
  def map(f: Json => Json): JsonSelection =
    JsonSelection(values.map(f))

  /**
   * FlatMaps each value in this selection using the given function.
   */
  def flatMap(f: Json => JsonSelection): JsonSelection =
    JsonSelection(values.flatMap(v => f(v).values))

  /**
   * Filters this selection using the given predicate.
   */
  def filter(p: Json => Boolean): JsonSelection =
    JsonSelection(values.filter(p))

  /**
   * Filters and maps this selection using the given partial function.
   */
  def collect(pf: PartialFunction[Json, Json]): JsonSelection =
    JsonSelection(values.collect(pf))

  // ===========================================================================
  // Terminal Operations
  // ===========================================================================

  /**
   * Returns the single value in this selection, or an error if the selection is
   * empty or contains multiple values.
   */
  def one: Either[JsonError, Json] = values match {
    case Vector(single) => Right(single)
    case Vector()       => Left(JsonError("Expected exactly one value, but selection is empty"))
    case _              => Left(JsonError(s"Expected exactly one value, but selection contains ${values.size} values"))
  }

  /**
   * Returns the first value in this selection, or an error if the selection is
   * empty.
   */
  def first: Either[JsonError, Json] = values.headOption match {
    case Some(v) => Right(v)
    case None    => Left(JsonError("Selection is empty"))
  }

  /**
   * Returns all values in this selection as an array.
   */
  def toArray: Array[Json] = values.toArray

  /**
   * Returns all values in this selection as a vector.
   */
  def toVector: Vector[Json] = values

  /**
   * Returns `true` if this selection is empty.
   */
  def isEmpty: Boolean = values.isEmpty

  /**
   * Returns `true` if this selection is non-empty.
   */
  def nonEmpty: Boolean = values.nonEmpty

  /**
   * Returns the number of values in this selection.
   */
  def size: Int = values.size
}

object JsonSelection {

  /**
   * Creates a selection containing a single value.
   */
  def apply(value: Json): JsonSelection =
    JsonSelection(Vector(value))

  /**
   * Creates an empty selection.
   */
  val empty: JsonSelection = JsonSelection(Vector.empty)
}
