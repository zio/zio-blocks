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
  def objects: JsonSelection = filter(JsonType.Object)

  /** Keeps only array values. */
  def arrays: JsonSelection = filter(JsonType.Array)

  /** Keeps only string values. */
  def strings: JsonSelection = filter(JsonType.String)

  /** Keeps only number values. */
  def numbers: JsonSelection = filter(JsonType.Number)

  /** Keeps only boolean values. */
  def booleans: JsonSelection = filter(JsonType.Boolean)

  /** Keeps only null values. */
  def nulls: JsonSelection = filter(JsonType.Null)

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
  // Query Methods (recursive search within selection)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively searches each JSON in the selection, collecting all values for
   * which the predicate returns true.
   *
   * @example
   *   {{{selection.query(JsonType.String) // all string values in selection}}}
   */
  def query(p: Json => Boolean): JsonSelection =
    flatMap(json => Json.queryImpl(json, DynamicOptic.root, (_, j) => p(j)))

  /**
   * Recursively searches each JSON in the selection, collecting all values at
   * paths for which the predicate returns true.
   */
  def queryPath(p: DynamicOptic => Boolean): JsonSelection =
    flatMap(json => Json.queryImpl(json, DynamicOptic.root, (path, _) => p(path)))

  /**
   * Recursively searches each JSON in the selection, collecting all values for
   * which the predicate on both path and value returns true.
   */
  def queryBoth(p: (DynamicOptic, Json) => Boolean): JsonSelection =
    flatMap(json => Json.queryImpl(json, DynamicOptic.root, p))

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

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively sorts all object keys alphabetically in each selected value.
   */
  def sortKeys: JsonSelection = map(_.sortKeys)

  /**
   * Recursively removes all null values from objects in each selected value.
   */
  def dropNulls: JsonSelection = map(_.dropNulls)

  /** Recursively removes empty objects and arrays in each selected value. */
  def dropEmpty: JsonSelection = map(_.dropEmpty)

  /** Applies sortKeys, dropNulls, and dropEmpty to each selected value. */
  def normalize: JsonSelection = map(_.normalize)

  // ─────────────────────────────────────────────────────────────────────────
  // Path Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Modifies the value at the given path in each selected value. */
  def modify(path: DynamicOptic)(f: Json => Json): JsonSelection = map(_.modify(path)(f))

  /** Sets a value at the given path in each selected value. */
  def set(path: DynamicOptic, value: Json): JsonSelection = map(_.set(path, value))

  /** Deletes the value at the given path in each selected value. */
  def delete(path: DynamicOptic): JsonSelection = map(_.delete(path))

  /** Inserts a value at the given path in each selected value. */
  def insert(path: DynamicOptic, value: Json): JsonSelection = map(_.insert(path, value))

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation
  // ─────────────────────────────────────────────────────────────────────────

  /** Transforms each selected value bottom-up using the given function. */
  def transformUp(f: (DynamicOptic, Json) => Json): JsonSelection = map(_.transformUp(f))

  /** Transforms each selected value top-down using the given function. */
  def transformDown(f: (DynamicOptic, Json) => Json): JsonSelection = map(_.transformDown(f))

  /**
   * Transforms all object keys in each selected value using the given function.
   */
  def transformKeys(f: (DynamicOptic, String) => String): JsonSelection = map(_.transformKeys(f))

  // ─────────────────────────────────────────────────────────────────────────
  // Pruning
  // ─────────────────────────────────────────────────────────────────────────

  /** Removes values matching the predicate from each selected value. */
  def prune(p: Json => Boolean): JsonSelection = map(_.prune(p))

  /**
   * Removes values at paths matching the predicate from each selected value.
   */
  def prunePath(p: DynamicOptic => Boolean): JsonSelection = map(_.prunePath(p))

  /**
   * Removes values matching both path and value predicates from each selected
   * value.
   */
  def pruneBoth(p: (DynamicOptic, Json) => Boolean): JsonSelection = map(_.pruneBoth(p))

  // ─────────────────────────────────────────────────────────────────────────
  // Retention
  // ─────────────────────────────────────────────────────────────────────────

  /** Retains only values matching the predicate in each selected value. */
  def retain(p: Json => Boolean): JsonSelection = map(_.retain(p))

  /**
   * Retains only values at paths matching the predicate in each selected value.
   */
  def retainPath(p: DynamicOptic => Boolean): JsonSelection = map(_.retainPath(p))

  /**
   * Retains only values matching both path and value predicates in each
   * selected value.
   */
  def retainBoth(p: (DynamicOptic, Json) => Boolean): JsonSelection = map(_.retainBoth(p))

  // ─────────────────────────────────────────────────────────────────────────
  // Projection
  // ─────────────────────────────────────────────────────────────────────────

  /** Projects each selected value to only include the specified paths. */
  def project(paths: DynamicOptic*): JsonSelection = map(_.project(paths: _*))

  // ─────────────────────────────────────────────────────────────────────────
  // Binary Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Merges each value in this selection with each value in `that` selection.
   * Uses cartesian product semantics: if this has N values and that has M
   * values, the result has N × M values.
   */
  def merge(that: JsonSelection, strategy: MergeStrategy = MergeStrategy.Auto): JsonSelection =
    JsonSelection(
      for {
        lefts  <- this.either
        rights <- that.either
      } yield
        for {
          left  <- lefts
          right <- rights
        } yield left.merge(right, strategy)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Fallible Mutation Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Modifies values at the given path using a partial function. Fails if the
   * path doesn't exist or the partial function is not defined.
   */
  def modifyOrFail(path: DynamicOptic)(pf: PartialFunction[Json, Json]): JsonSelection =
    flatMap { json =>
      json.modifyOrFail(path)(pf) match {
        case Right(j) => JsonSelection.succeed(j)
        case Left(e)  => JsonSelection.fail(e)
      }
    }

  /**
   * Sets a value at the given path. Fails if the path doesn't exist.
   */
  def setOrFail(path: DynamicOptic, value: Json): JsonSelection =
    flatMap { json =>
      json.setOrFail(path, value) match {
        case Right(j) => JsonSelection.succeed(j)
        case Left(e)  => JsonSelection.fail(e)
      }
    }

  /**
   * Deletes the value at the given path. Fails if the path doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): JsonSelection =
    flatMap { json =>
      json.deleteOrFail(path) match {
        case Right(j) => JsonSelection.succeed(j)
        case Left(e)  => JsonSelection.fail(e)
      }
    }

  /**
   * Inserts a value at the given path. Fails if the path already exists or the
   * parent doesn't exist.
   */
  def insertOrFail(path: DynamicOptic, value: Json): JsonSelection =
    flatMap { json =>
      json.insertOrFail(path, value) match {
        case Right(j) => JsonSelection.succeed(j)
        case Left(e)  => JsonSelection.fail(e)
      }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Type-Directed Extraction
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Narrows the single selected value to the specified JSON type. Fails if
   * there are 0 or more than 1 values, or if the value doesn't match.
   */
  def as(jsonType: JsonType): Either[JsonError, jsonType.Type] =
    one.flatMap { j =>
      j.as(jsonType).toRight(JsonError(s"Expected ${jsonType} but got ${j.jsonType}"))
    }

  /**
   * Narrows all selected values to the specified JSON type. Values not matching
   * the type are silently dropped.
   */
  def asAll(jsonType: JsonType): Either[JsonError, Vector[jsonType.Type]] =
    either.map(_.flatMap(_.as(jsonType).toVector))

  /**
   * Extracts the underlying Scala value from the single selected JSON value.
   * Fails if there are 0 or more than 1 values, or if the value doesn't match.
   *
   * Note: For JsonType.Number, this parses to BigDecimal and may fail if not
   * parseable.
   */
  def unwrap(jsonType: JsonType): Either[JsonError, jsonType.Unwrap] =
    one.flatMap { j =>
      j.unwrap(jsonType).toRight(JsonError(s"Cannot unwrap ${j.jsonType} as ${jsonType}"))
    }

  /**
   * Extracts the underlying Scala values from all selected JSON values. Values
   * not matching the type (or unparseable for Number) are silently dropped.
   */
  def unwrapAll(jsonType: JsonType): Either[JsonError, Vector[jsonType.Unwrap]] =
    either.map(_.flatMap(_.unwrap(jsonType).toVector))
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
