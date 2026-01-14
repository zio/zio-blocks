package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Represents the result of navigating to one or more JSON values.
 *
 * JsonSelection provides a fluent API for chaining navigation operations and
 * handling both successful selections and errors.
 *
 * @param values
 *   The selected JSON values
 * @param errors
 *   Any errors encountered during selection
 */
final case class JsonSelection(
  values: Vector[Json],
  errors: Vector[JsonError]
) {

  /** Returns true if this selection contains any values. */
  def nonEmpty: Boolean = values.nonEmpty

  /** Returns true if this selection is empty (no values). */
  def isEmpty: Boolean = values.isEmpty

  /** Returns true if this selection has errors. */
  def hasErrors: Boolean = errors.nonEmpty

  /** Returns true if this selection succeeded (has values and no errors). */
  def isSuccess: Boolean = values.nonEmpty && errors.isEmpty

  /** Returns the first value, or None if empty. */
  def headOption: Option[Json] = values.headOption

  /** Returns the first value, or throws if empty. */
  def head: Json = values.head

  /** Returns all values. */
  def toVector: Vector[Json] = values

  /** Returns the single value, or an error if not exactly one value. */
  def single: Either[JsonError, Json] =
    if (errors.nonEmpty) Left(errors.head)
    else if (values.isEmpty) Left(JsonError("No values selected"))
    else if (values.length > 1) Left(JsonError("Multiple values selected"))
    else Right(values.head)

  // ============ Chaining Navigation ============

  /** Navigates to a field in each selected JSON object. */
  def apply(key: String): JsonSelection = flatMap(_.apply(key))

  /** Navigates to an element in each selected JSON array. */
  def apply(index: Int): JsonSelection = flatMap(_.apply(index))

  /** Navigates to values at the given path. */
  def get(path: DynamicOptic): JsonSelection =
    if (path.nodes.isEmpty) this
    else {
      var current = this
      var idx     = 0
      while (idx < path.nodes.length && current.nonEmpty) {
        val node = path.nodes(idx)
        current = node match {
          case DynamicOptic.Node.Field(name) => current.apply(name)
          case DynamicOptic.Node.AtIndex(i)  => current.apply(i)
          case DynamicOptic.Node.Elements    =>
            current.flatMap(_.asArray).flatMap(j => JsonSelection(j.elements.getOrElse(Vector.empty), Vector.empty))
          case _ => JsonSelection.error(JsonError(s"Unsupported path node: $node"))
        }
        idx += 1
      }
      current
    }

  // ============ Mapping ============

  /** Maps a function over all selected values. */
  def map(f: Json => Json): JsonSelection =
    JsonSelection(values.map(f), errors)

  /** FlatMaps a function over all selected values. */
  def flatMap(f: Json => JsonSelection): JsonSelection = {
    var newValues = Vector.empty[Json]
    var newErrors = errors
    values.foreach { v =>
      val result = f(v)
      newValues = newValues ++ result.values
      newErrors = newErrors ++ result.errors
    }
    JsonSelection(newValues, newErrors)
  }

  /** Filters selected values. */
  def filter(p: Json => Boolean): JsonSelection =
    JsonSelection(values.filter(p), errors)

  /** Filters out selected values. */
  def filterNot(p: Json => Boolean): JsonSelection =
    JsonSelection(values.filterNot(p), errors)

  /** Collects values using a partial function. */
  def collect[A](pf: PartialFunction[Json, A]): Vector[A] =
    values.collect(pf)

  // ============ Type Filters ============

  /** Filters to only object values. */
  def objects: JsonSelection = filter(_.isObject)

  /** Filters to only array values. */
  def arrays: JsonSelection = filter(_.isArray)

  /** Filters to only string values. */
  def strings: JsonSelection = filter(_.isString)

  /** Filters to only number values. */
  def numbers: JsonSelection = filter(_.isNumber)

  /** Filters to only boolean values. */
  def booleans: JsonSelection = filter(_.isBoolean)

  /** Filters to only null values. */
  def nulls: JsonSelection = filter(_.isNull)

  // ============ Value Extraction ============

  /** Extracts string values. */
  def stringValues: Vector[String] = values.flatMap(_.stringValue)

  /** Extracts number values (as strings). */
  def numberValues: Vector[String] = values.flatMap(_.numberValue)

  /** Extracts boolean values. */
  def booleanValues: Vector[Boolean] = values.flatMap(_.booleanValue)

  // ============ Folding ============

  /** Folds over all selected values. */
  def foldLeft[A](z: A)(f: (A, Json) => A): A =
    values.foldLeft(z)(f)

  /** Reduces selected values. */
  def reduce(f: (Json, Json) => Json): Option[Json] =
    if (values.isEmpty) None else Some(values.reduce(f))

  // ============ Error Handling ============

  /** Returns the first error, or None if no errors. */
  def firstError: Option[JsonError] = errors.headOption

  /** Combines all errors into one. */
  def combinedError: Option[JsonError] =
    if (errors.isEmpty) None
    else Some(errors.reduce(_ ++ _))

  /**
   * Converts to Either, returning Right with values if no errors, Left
   * otherwise.
   */
  def toEither: Either[JsonError, Vector[Json]] =
    if (errors.nonEmpty) Left(errors.head)
    else Right(values)

  /** Converts to Either, returning the single value or an error. */
  def toEitherSingle: Either[JsonError, Json] = single
}

object JsonSelection {

  /** Creates a selection with a single value. */
  def single(value: Json): JsonSelection =
    JsonSelection(Vector(value), Vector.empty)

  /** Creates a selection from multiple values. */
  def apply(values: Json*): JsonSelection =
    JsonSelection(values.toVector, Vector.empty)

  /** Creates an empty selection. */
  val empty: JsonSelection =
    JsonSelection(Vector.empty, Vector.empty)

  /** Creates a selection with a single error. */
  def error(err: JsonError): JsonSelection =
    JsonSelection(Vector.empty, Vector(err))

  /** Creates a selection with multiple errors. */
  def errors(errs: JsonError*): JsonSelection =
    JsonSelection(Vector.empty, errs.toVector)
}
