package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic

/**
 * A wrapper around `Either[JsonError, Vector[Json]]` that provides fluent
 * chaining for JSON navigation and querying operations.
 *
 * JsonSelection enables a fluent API style for navigating through JSON
 * structures:
 * {{{
 *   json.get("users").asArray.apply(0).get("name").asString
 * }}}
 *
 * The selection can contain zero, one, or multiple JSON values, supporting both
 * single-value navigation and multi-value queries.
 */
final case class JsonSelection(value: Either[JsonError, Vector[Json]]) extends AnyVal {

  // ─────────────────────────────────────────────────────────────────────────
  // Basic operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the underlying Either. */
  def toEither: Either[JsonError, Vector[Json]] = value

  /** Returns all selected values (alias for toEither). */
  def all: Either[JsonError, Vector[Json]] = value

  /** Returns true if the selection is successful (contains values). */
  def isSuccess: Boolean = value.isRight

  /** Returns true if the selection is a failure. */
  def isFailure: Boolean = value.isLeft

  /** Returns the error if this is a failure, otherwise None. */
  def error: Option[JsonError] = value.left.toOption

  /** Returns the selected values if successful, otherwise None. */
  def values: Option[Vector[Json]] = value.toOption

  /**
   * Returns the first selected value if successful and non-empty, otherwise
   * None.
   */
  def headOption: Option[Json] = value.toOption.flatMap(_.headOption)

  /** Returns the selected values as a Vector, or an empty Vector on failure. */
  def toVector: Vector[Json] = value.getOrElse(Vector.empty)

  /**
   * Returns the single selected value, or fails if there are 0 or more than 1
   * values.
   */
  def single: Either[JsonError, Json] = value.flatMap { v =>
    if (v.length == 1) Right(v.head)
    else if (v.isEmpty) Left(JsonError("Expected single value but got none"))
    else Left(JsonError(s"Expected single value but got ${v.length}"))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Size Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if this selection is empty (no values or error). */
  def isEmpty: Boolean = value.fold(_ => true, _.isEmpty)

  /** Returns true if this selection contains at least one value. */
  def nonEmpty: Boolean = value.fold(_ => false, _.nonEmpty)

  /** Returns the number of selected values (0 on error). */
  def size: Int = value.fold(_ => 0, _.size)

  // ─────────────────────────────────────────────────────────────────────────
  // Terminal Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns the single value, or wraps multiple values in an array. Fails if
   * the selection is empty or an error.
   */
  def one: Either[JsonError, Json] = value.flatMap { v =>
    if (v.isEmpty) Left(JsonError("Expected at least one value but got none"))
    else if (v.length == 1) Right(v.head)
    else Right(new Json.Array(Chunk.from(v)))
  }

  /**
   * Returns the first value in the selection. Fails if the selection is empty
   * or an error.
   */
  def first: Either[JsonError, Json] = value.flatMap { v =>
    v.headOption match {
      case Some(j) => Right(j)
      case None    => Left(JsonError("Expected at least one value but got none"))
    }
  }

  /**
   * Returns all selected values as a JSON array.
   */
  def toArray: Either[JsonError, Json] = value.map(v => new Json.Array(Chunk.from(v)))

  /**
   * Unsafe version of `one` - throws JsonError on error.
   */
  def oneUnsafe: Json = one match {
    case Right(j) => j
    case Left(e)  => throw e
  }

  /**
   * Unsafe version of `first` - throws JsonError on error.
   */
  def firstUnsafe: Json = first match {
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
  def stringValues: JsonSelection = filter(_.isString)

  /** Keeps only number values. */
  def numberValues: JsonSelection = filter(_.isNumber)

  /** Keeps only boolean values. */
  def booleanValues: JsonSelection = filter(_.isBoolean)

  /** Keeps only null values. */
  def nullValues: JsonSelection = filter(_.isNull)

  // ─────────────────────────────────────────────────────────────────────────
  // Type Filtering (for chaining - fails on mismatch)
  // ─────────────────────────────────────────────────────────────────────────

  /** Filters to only object values, failing if any value is not an object. */
  def asObject: JsonSelection = flatMap(_.asObject)

  /** Filters to only array values, failing if any value is not an array. */
  def asArray: JsonSelection = flatMap(_.asArray)

  /** Filters to only string values, failing if any value is not a string. */
  def asString: JsonSelection = flatMap(_.asString)

  /** Filters to only number values, failing if any value is not a number. */
  def asNumber: JsonSelection = flatMap(_.asNumber)

  /** Filters to only boolean values, failing if any value is not a boolean. */
  def asBoolean: JsonSelection = flatMap(_.asBoolean)

  /** Filters to only null values, failing if any value is not null. */
  def asNull: JsonSelection = flatMap(_.asNull)

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /** Navigates to a field in each object. */
  def get(key: String): JsonSelection = flatMap(_.get(key))

  /** Navigates to an index in each array. */
  def get(index: Int): JsonSelection = flatMap(_.get(index))

  /** Navigates using a DynamicOptic path. */
  def get(path: DynamicOptic): JsonSelection = flatMap(_.get(path))

  // ─────────────────────────────────────────────────────────────────────────
  // Extraction
  // ─────────────────────────────────────────────────────────────────────────

  /** Extracts string values from all selections. */
  def strings: Either[JsonError, Vector[String]] =
    value.flatMap { jsons =>
      jsons.zipWithIndex.foldLeft[Either[JsonError, Vector[String]]](Right(Vector.empty)) {
        case (Right(acc), (json, idx)) =>
          json.stringValue match {
            case Some(s) => Right(acc :+ s)
            case None    => Left(JsonError(s"Expected string at index $idx"))
          }
        case (left, _) => left
      }
    }

  /** Extracts a single string value. */
  def string: Either[JsonError, String] = single.flatMap { json =>
    json.stringValue match {
      case Some(s) => Right(s)
      case None    => Left(JsonError("Expected string value"))
    }
  }

  /** Extracts number values from all selections. */
  def numbers: Either[JsonError, Vector[BigDecimal]] =
    value.flatMap { jsons =>
      jsons.zipWithIndex.foldLeft[Either[JsonError, Vector[BigDecimal]]](Right(Vector.empty)) {
        case (Right(acc), (json, idx)) =>
          json.numberValue match {
            case Some(n) => Right(acc :+ n)
            case None    => Left(JsonError(s"Expected number at index $idx"))
          }
        case (left, _) => left
      }
    }

  /** Extracts a single number value. */
  def number: Either[JsonError, BigDecimal] = single.flatMap { json =>
    json.numberValue match {
      case Some(n) => Right(n)
      case None    => Left(JsonError("Expected number value"))
    }
  }

  /** Extracts boolean values from all selections. */
  def booleans: Either[JsonError, Vector[Boolean]] =
    value.flatMap { jsons =>
      jsons.zipWithIndex.foldLeft[Either[JsonError, Vector[Boolean]]](Right(Vector.empty)) {
        case (Right(acc), (json, idx)) =>
          json.booleanValue match {
            case Some(b) => Right(acc :+ b)
            case None    => Left(JsonError(s"Expected boolean at index $idx"))
          }
        case (left, _) => left
      }
    }

  /** Extracts a single boolean value. */
  def boolean: Either[JsonError, Boolean] = single.flatMap { json =>
    json.booleanValue match {
      case Some(b) => Right(b)
      case None    => Left(JsonError("Expected boolean value"))
    }
  }

  /** Extracts a single int value (fails if not representable as Int). */
  def int: Either[JsonError, Int] = number.flatMap { n =>
    if (n.isValidInt) Right(n.toInt)
    else Left(JsonError(s"Number $n is not a valid Int"))
  }

  /** Extracts a single long value (fails if not representable as Long). */
  def long: Either[JsonError, Long] = number.flatMap { n =>
    if (n.isValidLong) Right(n.toLong)
    else Left(JsonError(s"Number $n is not a valid Long"))
  }

  /** Extracts a single float value. */
  def float: Either[JsonError, Float] = number.map(_.toFloat)

  /** Extracts a single double value. */
  def double: Either[JsonError, Double] = number.map(_.toDouble)

  // ─────────────────────────────────────────────────────────────────────────
  // Combinators
  // ─────────────────────────────────────────────────────────────────────────

  /** Maps a function over all selected values. */
  def map(f: Json => Json): JsonSelection =
    JsonSelection(value.map(_.map(f)))

  /** FlatMaps a function over all selected values, combining results. */
  def flatMap(f: Json => JsonSelection): JsonSelection =
    JsonSelection(value.flatMap { jsons =>
      val results    = jsons.map(j => f(j).value)
      val firstError = results.collectFirst { case Left(e) => e }
      firstError match {
        case Some(error) => Left(error)
        case None        => Right(results.collect { case Right(v) => v }.flatten)
      }
    })

  /** Filters selected values based on a predicate. */
  def filter(p: Json => Boolean): JsonSelection =
    JsonSelection(value.map(_.filter(p)))

  /** Collects values for which the partial function is defined. */
  def collect[A](pf: PartialFunction[Json, A]): Either[JsonError, Vector[A]] =
    value.map(_.collect(pf))

  /** Returns this selection if successful, otherwise the alternative. */
  def orElse(alternative: => JsonSelection): JsonSelection =
    if (isSuccess) this else alternative

  /** Returns this selection's values, or the default on failure. */
  def getOrElse(default: => Vector[Json]): Vector[Json] =
    value.getOrElse(default)

  /** Combines two selections, concatenating their values. */
  def ++(other: JsonSelection): JsonSelection =
    (value, other.value) match {
      case (Right(v1), Right(v2)) => JsonSelection(Right(v1 ++ v2))
      case (Left(e), _)           => JsonSelection(Left(e))
      case (_, Left(e))           => JsonSelection(Left(e))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Decoding
  // ─────────────────────────────────────────────────────────────────────────

  /** Decodes the single selected value to type A. */
  def as[A](implicit decoder: JsonDecoder[A]): Either[JsonError, A] =
    single.flatMap(decoder.decode)

  /** Decodes all selected values to type A. */
  def asAll[A](implicit decoder: JsonDecoder[A]): Either[JsonError, Vector[A]] =
    value.flatMap { jsons =>
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
