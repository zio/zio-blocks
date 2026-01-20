package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic}

/**
 * Represents a selection of zero or more JSON values, with accumulated errors.
 *
 * `JsonSelection` enables fluent chaining of operations that may fail without
 * requiring immediate error handling. Operations are applied to all values in
 * the selection, and errors are accumulated.
 *
 * {{{
 * val selection: JsonSelection = json.get(p"users[*].name")
 * val result: Either[JsonError, Vector[Json]] = selection.toEither
 * }}}
 */
final case class JsonSelection(toEither: Either[JsonError, Vector[Json]]) { self =>

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

  // ---------------------------------------------------------------------------
  // Transformations
  // ---------------------------------------------------------------------------

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
      jsons.foldLeft[Either[JsonError, Vector[Json]]](Right(Vector.empty)) { (acc, json) =>
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

  // ---------------------------------------------------------------------------
  // Navigation
  // ---------------------------------------------------------------------------

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
  def apply(key: String): JsonSelection =
    flatMap(json => json.apply(key))

  // ---------------------------------------------------------------------------
  // Type Filtering
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Combination
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Terminal Operations
  // ---------------------------------------------------------------------------

  /**
   * Returns the single value if exactly one, an array of values if there are many, or 
   * otherwise an error.
   */
  def one: Either[JsonError, Json] =
    toEither.flatMap { jsons =>
      if (jsons.size == 1) Right(jsons.head)
      else if (jsons.size > 1) toArray
      else Left(JsonError(s"expected exactly one value, got ${jsons.size}", DynamicOptic.root))
    }

  /**
   * Returns the first value if any, otherwise an error.
   */
  def first: Either[JsonError, Json] =
    toEither.flatMap { jsons =>
      jsons.headOption.toRight(JsonError("expected at least one value, got none", DynamicOptic.root))
    }

  /**
   * Returns all values as a [[Json.Array]], or an error.
   */
  def toArray: Either[JsonError, Json] =
    toEither.map(jsons => Json.Array(jsons))

  /**
   * Unsafe version of [[one]], throws on error or wrong count.
   */
  def oneUnsafe: Json = one.fold(throw _, identity)

  /**
   * Unsafe version of [[first]], throws on error or empty.
   */
  def firstUnsafe: Json = first.fold(throw _, identity)
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
  def fail(error: JsonError): JsonSelection = JsonSelection(Left(error))

  /**
   * Creates a failed selection with the given message.
   */
  def fail(message: String): JsonSelection =
    JsonSelection(Left(JsonError(message, DynamicOptic.root)))
}
