package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.SchemaError

/**
 * A wrapper around `Either[SchemaError, Vector[Json]]` that provides fluent
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
final case class JsonSelection(either: Either[SchemaError, Vector[Json]]) extends AnyVal {

  // ─────────────────────────────────────────────────────────────────────────
  // Basic operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if the selection is successful (contains values). */
  def isSuccess: Boolean = either.isRight

  /** Returns true if the selection is a failure. */
  def isFailure: Boolean = either.isLeft

  /** Returns the error if this is a failure, otherwise None. */
  def error: Option[SchemaError] = either.left.toOption

  /** Returns the selected values if successful, otherwise None. */
  def values: Option[Vector[Json]] = either.toOption

  /** Returns the selected values as a Vector, or an empty Vector on failure. */
  def toVector: Vector[Json] = either.getOrElse(Vector.empty)

  /**
   * Returns the single selected value, or fails if there are 0 or more than 1
   * values.
   */
  def one: Either[SchemaError, Json] = either match {
    case Right(v) =>
      val len = v.length
      if (len == 1) new Right(v.head)
      else
        new Left(
          SchemaError(
            if (len == 0) "Expected single value but got none"
            else s"Expected single value but got $len"
          )
        )
    case l => l.asInstanceOf[Either[SchemaError, Json]]
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Size Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if this selection is empty (no values or error). */
  def isEmpty: Boolean = either match {
    case Right(v) => v.isEmpty
    case _        => true
  }

  /** Returns true if this selection contains at least one value. */
  def nonEmpty: Boolean = !isEmpty

  /** Returns the number of selected values (0 on error). */
  def size: Int = either match {
    case Right(v) => v.length
    case _        => 0
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Terminal Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns any single value from the selection. Fails if the selection is
   * empty or an error.
   */
  def any: Either[SchemaError, Json] = either match {
    case Right(j) =>
      if (j.isEmpty) new Left(SchemaError("Expected at least one value but got none"))
      else new Right(j.head)
    case l => l.asInstanceOf[Either[SchemaError, Json]]
  }

  /**
   * Returns all selected values condensed into a single Json. If there are
   * multiple values, wraps them in an array. Fails if the selection is empty or
   * an error.
   */
  def all: Either[SchemaError, Json] = either match {
    case Right(v) =>
      if (v.isEmpty) new Left(SchemaError("Expected at least one value but got none"))
      else
        new Right({
          if (v.length == 1) v.head
          else new Json.Array(zio.blocks.chunk.Chunk.from(v))
        })
    case l => l.asInstanceOf[Either[SchemaError, Json]]
  }

  /**
   * Returns all selected values as a JSON array.
   */
  def toArray: Either[SchemaError, Json] = either match {
    case Right(v) => new Right(new Json.Array(zio.blocks.chunk.Chunk.from(v)))
    case l        => l.asInstanceOf[Either[SchemaError, Json]]
  }

  /**
   * Unsafe version of `one` - throws SchemaError on error.
   */
  def oneUnsafe: Json = one match {
    case Right(j) => j
    case Left(e)  => throw e
  }

  /**
   * Unsafe version of `any` - throws SchemaError on error.
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
  def map(f: Json => Json): JsonSelection = new JsonSelection({
    either match {
      case Right(v) => new Right(v.map(f))
      case l        => l
    }
  })

  /** FlatMaps a function over all selected values, combining results. */
  def flatMap(f: Json => JsonSelection): JsonSelection = new JsonSelection({
    either match {
      case Right(v) =>
        new Right({
          val len = v.length
          if (len == 0) Vector.empty
          else {
            val builder = Vector.newBuilder[Json]
            var idx     = 0
            while (idx < len) {
              f(v(idx)).either match {
                case Right(v1) => builder.addAll(v1)
                case l         => return new JsonSelection(l)
              }
              idx += 1
            }
            builder.result()
          }
        })
      case l => l
    }
  })

  /** Filters selected values based on a predicate. */
  def filter(p: Json => Boolean): JsonSelection = new JsonSelection({
    either match {
      case Right(v) => new Right(v.filter(p))
      case l        => l
    }
  })

  /** Collects values for which the partial function is defined. */
  def collect[A](pf: PartialFunction[Json, A]): Either[SchemaError, Vector[A]] = either match {
    case Right(v) => new Right(v.collect(pf))
    case l        => l.asInstanceOf[Either[SchemaError, Vector[A]]]
  }

  /** Returns this selection if successful, otherwise the alternative. */
  def orElse(alternative: => JsonSelection): JsonSelection =
    if (either.isRight) this
    else alternative

  /** Returns this selection's values, or the default on failure. */
  def getOrElse(default: => Vector[Json]): Vector[Json] = either match {
    case Right(v) => v
    case _        => default
  }

  /** Combines two selections, concatenating their values. */
  def ++(other: JsonSelection): JsonSelection = new JsonSelection({
    (either, other.either) match {
      case (Right(v1), Right(v2)) => new Right(v1 ++ v2)
      case (Left(e1), Left(e2))   => new Left(e1 ++ e2)
      case (l @ Left(_), _)       => l
      case (_, l @ Left(_))       => l
    }
  })

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
  def as[A](implicit decoder: JsonDecoder[A]): Either[SchemaError, A] = either match {
    case Right(v) =>
      val len = v.length
      if (len == 1) decoder.decode(v.head)
      else
        new Left(
          SchemaError(
            if (len == 0) "Expected single value but got none"
            else s"Expected single value but got $len"
          )
        )
    case l => l.asInstanceOf[Either[SchemaError, A]]
  }

  /** Decodes all selected values to type A. */
  def asAll[A](implicit decoder: JsonDecoder[A]): Either[SchemaError, Vector[A]] = either match {
    case Right(v) =>
      new Right({
        val len = v.length
        if (len == 0) Vector.empty
        else {
          val builder = Vector.newBuilder[A]
          var idx     = 0
          while (idx < len) {
            decoder.decode(v(idx)) match {
              case Right(va) => builder.addOne(va)
              case l         => return l.asInstanceOf[Either[SchemaError, Vector[A]]]
            }
            idx += 1
          }
          builder.result()
        }
      })
    case l => l.asInstanceOf[Either[SchemaError, Vector[A]]]
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
  def merge(that: JsonSelection, strategy: MergeStrategy = MergeStrategy.Auto): JsonSelection = new JsonSelection({
    either match {
      case Right(v1) =>
        that.either match {
          case Right(v2) =>
            new Right({
              val builder = Vector.newBuilder[Json]
              v1.foreach(j1 => v2.foreach(j2 => builder.addOne(j1.merge(j2, strategy))))
              builder.result()
            })
          case l => l
        }
      case l => l
    }
  })

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
  def as(jsonType: JsonType): Either[SchemaError, jsonType.Type] = either match {
    case Right(v) =>
      new Left(SchemaError {
        val len = v.length
        if (len == 1) {
          val j = v.head
          if (j.jsonType eq jsonType) return new Right(j.asInstanceOf[jsonType.Type])
          else s"Expected $jsonType but got ${j.jsonType}"
        } else {
          if (len == 0) "Expected single value but got none"
          else s"Expected single value but got $len"
        }
      })
    case l => l.asInstanceOf[Either[SchemaError, jsonType.Type]]
  }

  /**
   * Narrows all selected values to the specified JSON type. Values not matching
   * the type are silently dropped.
   */
  def asAll(jsonType: JsonType): Either[SchemaError, Vector[jsonType.Type]] = either match {
    case Right(v) => new Right(v.collect { case j if j.jsonType eq jsonType => j.asInstanceOf[jsonType.Type] })
    case l        => l.asInstanceOf[Either[SchemaError, Vector[jsonType.Type]]]
  }

  /**
   * Extracts the underlying Scala value from the single selected JSON value.
   * Fails if there are 0 or more than 1 values, or if the value doesn't match.
   *
   * Note: For JsonType.Number, this parses to BigDecimal and may fail if not
   * parseable.
   */
  def unwrap(jsonType: JsonType): Either[SchemaError, jsonType.Unwrap] = either match {
    case Right(v) =>
      new Left(SchemaError {
        val len = v.length
        if (len == 1) {
          val j = v.head
          j.unwrap(jsonType) match {
            case Some(x) => return new Right(x)
            case _       => s"Cannot unwrap ${j.jsonType} as $jsonType"
          }
        } else {
          if (len == 0) "Expected single value but got none"
          else s"Expected single value but got $len"
        }
      })
    case l => l.asInstanceOf[Either[SchemaError, jsonType.Unwrap]]
  }

  /**
   * Extracts the underlying Scala values from all selected JSON values. Values
   * not matching the type (or unparseable for Number) are silently dropped.
   */
  def unwrapAll(jsonType: JsonType): Either[SchemaError, Vector[jsonType.Unwrap]] = either match {
    case Right(v) =>
      new Right({
        val len = v.length
        if (len == 0) Vector.empty
        else {
          val builder = Vector.newBuilder[jsonType.Unwrap]
          var idx     = 0
          while (idx < len) {
            v(idx).unwrap(jsonType) match {
              case Some(x) => builder.addOne(x)
              case _       =>
            }
            idx += 1
          }
          builder.result()
        }
      })
    case l => l.asInstanceOf[Either[SchemaError, Vector[jsonType.Unwrap]]]
  }
}

object JsonSelection {

  /** Creates a successful selection with a single value. */
  def succeed(json: Json): JsonSelection = new JsonSelection(new Right(Vector(json)))

  /** Creates a successful selection with multiple values. */
  def succeedMany(jsons: Vector[Json]): JsonSelection = new JsonSelection(new Right(jsons))

  /** Creates a failed selection with an error. */
  def fail(error: SchemaError): JsonSelection = new JsonSelection(new Left(error))

  /** An empty successful selection. */
  val empty: JsonSelection = new JsonSelection(new Right(Vector.empty))
}
