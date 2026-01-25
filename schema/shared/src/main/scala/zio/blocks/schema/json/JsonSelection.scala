package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * A wrapper around `Either[JsonError, Vector[Json]]` that provides fluent
 * chaining for JSON navigation and querying operations.
 *
 * JsonSelection enables a fluent API style for navigating through JSON
 * structures:
 * {{{
 *   json.get("users").asArrays.apply(0).get("name").asStrings
 * }}}
 *
 * The selection can contain zero, one, or multiple JSON values, supporting both
 * single-value navigation and multi-value queries.
 */
final case class JsonSelection(either: Either[JsonError, Vector[Json]]) extends AnyVal {

  // ─────────────────────────────────────────────────────────────────────────
  // Basic operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if the selection is successful (contains values). */
  def isSuccess: Boolean = either.isRight

  /** Returns true if the selection is a failure. */
  def isFailure: Boolean = either.isLeft

  /** Returns the error if this is a failure, otherwise None. */
  def error: Option[JsonError] = either.left.toOption

  /** Returns the selected values if successful, otherwise None. */
  def values: Option[Vector[Json]] = either.toOption

  /** Returns the selected values as a Vector, or an empty Vector on failure. */
  def toVector: Vector[Json] = either.getOrElse(Vector.empty)

  /**
   * Returns the single selected value, or fails if there are 0 or more than 1
   * values.
   */
  def one: Either[JsonError, Json] = either.flatMap { v =>
    if (v.length == 1) Right(v.head)
    else if (v.isEmpty) Left(JsonError("Expected single value but got none"))
    else Left(JsonError(s"Expected single value but got ${v.length}"))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Size Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if this selection is empty (no values or error). */
  def isEmpty: Boolean = either.fold(_ => true, _.isEmpty)

  /** Returns true if this selection contains at least one value. */
  def nonEmpty: Boolean = either.fold(_ => false, _.nonEmpty)

  /** Returns the number of selected values (0 on error). */
  def size: Int = either.fold(_ => 0, _.size)

  // ─────────────────────────────────────────────────────────────────────────
  // Terminal Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns any single value from the selection. Fails if the selection is
   * empty or an error.
   */
  def any: Either[JsonError, Json] = either.flatMap { v =>
    v.headOption match {
      case Some(j) => Right(j)
      case None    => Left(JsonError("Expected at least one value but got none"))
    }
  }

  /**
   * Returns all selected values condensed into a single Json. If there are
   * multiple values, wraps them in an array. Fails if the selection is empty or
   * an error.
   */
  def all: Either[JsonError, Json] = either.flatMap { v =>
    if (v.isEmpty) Left(JsonError("Expected at least one value but got none"))
    else if (v.length == 1) Right(v.head)
    else Right(Json.Array(zio.blocks.chunk.Chunk.from(v)))
  }

  /**
   * Returns all selected values as a JSON array.
   */
  def toArray: Either[JsonError, Json] = either.map(v => Json.Array(zio.blocks.chunk.Chunk.from(v)))

  /**
   * Unsafe version of `one` - throws JsonError on error.
   */
  def oneUnsafe: Json = one match {
    case Right(j) => j
    case Left(e)  => throw e
  }

  /**
   * Unsafe version of `any` - throws JsonError on error.
   */
  def anyUnsafe: Json = any match {
    case Right(j) => j
    case Left(e)  => throw e
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type Filtering (keeps only matching types)
  // ─────────────────────────────────────────────────────────────────────────

  /** Keeps only object values. */
  def objects: JsonSelection = filter(_.isObject)

  /** Keeps only array values. */
  def arrays: JsonSelection = filter(_.isArray)

  /** Keeps only string values. */
  def strings: JsonSelection = filter(_.isString)

  /** Keeps only number values. */
  def numbers: JsonSelection = filter(_.isNumber)

  /** Keeps only boolean values. */
  def booleans: JsonSelection = filter(_.isBoolean)

  /** Keeps only null values. */
  def nulls: JsonSelection = filter(_.isNull)

  // ─────────────────────────────────────────────────────────────────────────
  // Type Filtering (for chaining - fails on mismatch)
  // ─────────────────────────────────────────────────────────────────────────

  /** Filters to only object values, failing if any value is not an object. */
  def asObjects: JsonSelection = flatMap(_.asObject)

  /** Filters to only array values, failing if any value is not an array. */
  def asArrays: JsonSelection = flatMap(_.asArray)

  /** Filters to only string values, failing if any value is not a string. */
  def asStrings: JsonSelection = flatMap(_.asString)

  /** Filters to only number values, failing if any value is not a number. */
  def asNumbers: JsonSelection = flatMap(_.asNumber)

  /** Filters to only boolean values, failing if any value is not a boolean. */
  def asBooleans: JsonSelection = flatMap(_.asBoolean)

  /** Filters to only null values, failing if any value is not null. */
  def asNulls: JsonSelection = flatMap(_.asNull)

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /** Navigates to a field in each object. */
  def get(key: String): JsonSelection = flatMap(_.get(key))

  /** Navigates to an index in each array. */
  def apply(index: Int): JsonSelection = flatMap(_.get(index))

  /** Navigates to a field in each object (alias for get). */
  def apply(key: String): JsonSelection = get(key)

  /** Navigates using a DynamicOptic path. */
  def get(path: DynamicOptic): JsonSelection = flatMap(_.get(path))

  /** Navigates using a DynamicOptic path (alias for get). */
  def apply(path: DynamicOptic): JsonSelection = get(path)

  // ─────────────────────────────────────────────────────────────────────────
  // Combinators
  // ─────────────────────────────────────────────────────────────────────────

  /** Maps a function over all selected values. */
  def map(f: Json => Json): JsonSelection =
    JsonSelection(either.map(_.map(f)))

  /** FlatMaps a function over all selected values, combining results. */
  def flatMap(f: Json => JsonSelection): JsonSelection =
    JsonSelection(either.flatMap { jsons =>
      val results    = jsons.map(j => f(j).either)
      val firstError = results.collectFirst { case Left(e) => e }
      firstError match {
        case Some(error) => Left(error)
        case None        => Right(results.collect { case Right(v) => v }.flatten)
      }
    })

  /** Filters selected values based on a predicate. */
  def filter(p: Json => Boolean): JsonSelection =
    JsonSelection(either.map(_.filter(p)))

  /** Collects values for which the partial function is defined. */
  def collect[A](pf: PartialFunction[Json, A]): Either[JsonError, Vector[A]] =
    either.map(_.collect(pf))

  /** Returns this selection if successful, otherwise the alternative. */
  def orElse(alternative: => JsonSelection): JsonSelection =
    if (isSuccess) this else alternative

  /** Returns this selection's values, or the default on failure. */
  def getOrElse(default: => Vector[Json]): Vector[Json] =
    either.getOrElse(default)

  /** Combines two selections, concatenating their values. */
  def ++(other: JsonSelection): JsonSelection =
    (either, other.either) match {
      case (Right(v1), Right(v2)) => JsonSelection(Right(v1 ++ v2))
      case (Left(e), _)           => JsonSelection(Left(e))
      case (_, Left(e))           => JsonSelection(Left(e))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Decoding
  // ─────────────────────────────────────────────────────────────────────────

  /** Decodes the single selected value to type A. */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] =
    one.flatMap(decoder.decode)

  /** Decodes all selected values to type A. */
  def asAll[A](implicit decoder: JsonDecoder[A]): Either[JsonError, Vector[A]] =
    either.flatMap { jsons =>
      jsons.foldLeft[Either[JsonError, Vector[A]]](Right(Vector.empty)) {
        case (Right(acc), json) =>
          decoder.decode(json) match {
            case Right(a)    => Right(acc :+ a)
            case Left(error) => Left(error)
          }
        case (left, _) => left
      }
    }
}

object JsonSelection {

  /** Creates a successful selection with a single value. */
  def succeed(json: Json): JsonSelection = JsonSelection(Right(Vector(json)))

  /** Creates a successful selection with multiple values. */
  def succeedMany(jsons: Vector[Json]): JsonSelection = JsonSelection(Right(jsons))

  /** Creates a failed selection with an error. */
  def fail(error: JsonError): JsonSelection = JsonSelection(Left(error))

  /** An empty successful selection. */
  val empty: JsonSelection = JsonSelection(Right(Vector.empty))
}
