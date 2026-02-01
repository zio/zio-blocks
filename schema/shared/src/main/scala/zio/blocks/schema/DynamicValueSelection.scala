package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * A wrapper around `Either[SchemaError, Chunk[DynamicValue]]` that provides
 * fluent chaining for DynamicValue navigation and querying operations.
 *
 * DynamicValueSelection enables a fluent API style for navigating through
 * DynamicValue structures:
 * {{{
 *   dv.get("users").sequences.apply(0).get("name").primitives
 * }}}
 *
 * The selection can contain zero, one, or multiple DynamicValue values,
 * supporting both single-value navigation and multi-value queries.
 */
final case class DynamicValueSelection(either: Either[SchemaError, Chunk[DynamicValue]]) extends AnyVal {

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
  def values: Option[Chunk[DynamicValue]] = either.toOption

  /** Returns the selected values as a Chunk, or an empty Chunk on failure. */
  def toChunk: Chunk[DynamicValue] = either.getOrElse(Chunk.empty)

  /**
   * Returns the single selected value, or fails if there are 0 or more than 1
   * values.
   */
  def one: Either[SchemaError, DynamicValue] = either.flatMap { v =>
    if (v.length == 1) Right(v.head)
    else if (v.isEmpty) Left(SchemaError("Expected single value but got none"))
    else Left(SchemaError(s"Expected single value but got ${v.length}"))
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
  def any: Either[SchemaError, DynamicValue] = either.flatMap { v =>
    v.headOption match {
      case Some(dv) => Right(dv)
      case None     => Left(SchemaError("Expected at least one value but got none"))
    }
  }

  /**
   * Returns all selected values condensed into a single DynamicValue. If there
   * are multiple values, wraps them in a Sequence. Fails if the selection is
   * empty or an error.
   */
  def all: Either[SchemaError, DynamicValue] = either.flatMap { v =>
    if (v.isEmpty) Left(SchemaError("Expected at least one value but got none"))
    else if (v.length == 1) Right(v.head)
    else Right(DynamicValue.Sequence(v))
  }

  /**
   * Returns all selected values as a DynamicValue.Sequence.
   */
  def toSequence: Either[SchemaError, DynamicValue] = either.map(v => DynamicValue.Sequence(v))

  /**
   * Unsafe version of `one` - throws SchemaError on error.
   */
  def oneUnsafe: DynamicValue = one match {
    case Right(dv) => dv
    case Left(e)   => throw e
  }

  /**
   * Unsafe version of `any` - throws SchemaError on error.
   */
  def anyUnsafe: DynamicValue = any match {
    case Right(dv) => dv
    case Left(e)   => throw e
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type Filtering (keeps only matching types)
  // ─────────────────────────────────────────────────────────────────────────

  /** Keeps only primitive values. */
  def primitives: DynamicValueSelection = filter(DynamicValueType.Primitive)

  /** Keeps only record values. */
  def records: DynamicValueSelection = filter(DynamicValueType.Record)

  /** Keeps only variant values. */
  def variants: DynamicValueSelection = filter(DynamicValueType.Variant)

  /** Keeps only sequence values. */
  def sequences: DynamicValueSelection = filter(DynamicValueType.Sequence)

  /** Keeps only map values. */
  def maps: DynamicValueSelection = filter(DynamicValueType.Map)

  /** Keeps only null values. */
  def nulls: DynamicValueSelection = filter(DynamicValueType.Null)

  /** Filters by DynamicValueType. */
  def filter(t: DynamicValueType): DynamicValueSelection =
    DynamicValueSelection(either.map(_.filter(dv => dv.valueType == t)))

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /** Navigates to a field in each record. */
  def get(fieldName: String): DynamicValueSelection = flatMap(_.get(fieldName))

  /** Navigates to an index in each sequence. */
  def apply(index: Int): DynamicValueSelection = flatMap(_.get(index))

  /** Navigates to a field in each record (alias for get). */
  def apply(fieldName: String): DynamicValueSelection = get(fieldName)

  /** Navigates to a map entry by key. */
  def get(key: DynamicValue): DynamicValueSelection = flatMap(_.get(key))

  /** Navigates using a DynamicOptic path. */
  def get(path: DynamicOptic): DynamicValueSelection = flatMap(_.get(path))

  /** Navigates using a DynamicOptic path (alias for get). */
  def apply(path: DynamicOptic): DynamicValueSelection = get(path)

  /** Navigates into a variant case. */
  def getCase(caseName: String): DynamicValueSelection = flatMap(_.getCase(caseName))

  // ─────────────────────────────────────────────────────────────────────────
  // Combinators
  // ─────────────────────────────────────────────────────────────────────────

  /** Maps a function over all selected values. */
  def map(f: DynamicValue => DynamicValue): DynamicValueSelection =
    DynamicValueSelection(either.map(_.map(f)))

  /** FlatMaps a function over all selected values, combining results. */
  def flatMap(f: DynamicValue => DynamicValueSelection): DynamicValueSelection =
    DynamicValueSelection(either.flatMap { dvs =>
      val results    = dvs.map(dv => f(dv).either)
      val firstError = results.collectFirst { case Left(e) => e }
      firstError match {
        case Some(error) => Left(error)
        case None        => Right(results.collect { case Right(v) => v }.flatten)
      }
    })

  /** Filters selected values based on a predicate. */
  def filter(p: DynamicValue => Boolean): DynamicValueSelection =
    DynamicValueSelection(either.map(_.filter(p)))

  /** Collects values for which the partial function is defined. */
  def collect[A](pf: PartialFunction[DynamicValue, A]): Either[SchemaError, Chunk[A]] =
    either.map(_.collect(pf))

  /** Returns this selection if successful, otherwise the alternative. */
  def orElse(alternative: => DynamicValueSelection): DynamicValueSelection =
    if (isSuccess) this else alternative

  /** Returns this selection's values, or the default on failure. */
  def getOrElse(default: => Chunk[DynamicValue]): Chunk[DynamicValue] =
    either.getOrElse(default)

  /** Combines two selections, concatenating their values. */
  def ++(other: DynamicValueSelection): DynamicValueSelection =
    (either, other.either) match {
      case (Right(v1), Right(v2)) => DynamicValueSelection(Right(v1 ++ v2))
      case (Left(e1), Left(e2))   => DynamicValueSelection(Left(e1 ++ e2))
      case (Left(e), _)           => DynamicValueSelection(Left(e))
      case (_, Left(e))           => DynamicValueSelection(Left(e))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Query Methods (recursive search within selection)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively searches each DynamicValue in the selection, collecting all
   * values for which the predicate returns true.
   */
  def query(p: DynamicValue => Boolean): DynamicValueSelection =
    flatMap(dv => DynamicValue.queryImpl(dv, DynamicOptic.root, (_, v) => p(v)))

  /**
   * Recursively searches each DynamicValue in the selection, collecting all
   * values at paths for which the predicate returns true.
   */
  def queryPath(p: DynamicOptic => Boolean): DynamicValueSelection =
    flatMap(dv => DynamicValue.queryImpl(dv, DynamicOptic.root, (path, _) => p(path)))

  /**
   * Recursively searches each DynamicValue in the selection, collecting all
   * values for which the predicate on both path and value returns true.
   */
  def queryBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValueSelection =
    flatMap(dv => DynamicValue.queryImpl(dv, DynamicOptic.root, p))

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively sorts all record fields alphabetically in each selected value.
   */
  def sortFields: DynamicValueSelection = map(_.sortFields)

  /** Recursively sorts all map entries by key in each selected value. */
  def sortMapKeys: DynamicValueSelection = map(_.sortMapKeys)

  /**
   * Recursively removes all Null values from containers in each selected value.
   */
  def dropNulls: DynamicValueSelection = map(_.dropNulls)

  /**
   * Recursively removes all Primitive(Unit) values from containers in each
   * selected value.
   */
  def dropUnits: DynamicValueSelection = map(_.dropUnits)

  /**
   * Recursively removes empty Records, Sequences, and Maps in each selected
   * value.
   */
  def dropEmpty: DynamicValueSelection = map(_.dropEmpty)

  /**
   * Applies sortFields, sortMapKeys, dropNulls, dropUnits, and dropEmpty to
   * each selected value.
   */
  def normalize: DynamicValueSelection = map(_.normalize)

  // ─────────────────────────────────────────────────────────────────────────
  // Path Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Modifies the value at the given path in each selected value. */
  def modify(path: DynamicOptic)(f: DynamicValue => DynamicValue): DynamicValueSelection =
    map(_.modify(path)(f))

  /** Sets a value at the given path in each selected value. */
  def set(path: DynamicOptic, value: DynamicValue): DynamicValueSelection =
    map(_.set(path, value))

  /** Deletes the value at the given path in each selected value. */
  def delete(path: DynamicOptic): DynamicValueSelection = map(_.delete(path))

  /** Inserts a value at the given path in each selected value. */
  def insert(path: DynamicOptic, value: DynamicValue): DynamicValueSelection =
    map(_.insert(path, value))

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation
  // ─────────────────────────────────────────────────────────────────────────

  /** Transforms each selected value bottom-up using the given function. */
  def transformUp(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValueSelection =
    map(_.transformUp(f))

  /** Transforms each selected value top-down using the given function. */
  def transformDown(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValueSelection =
    map(_.transformDown(f))

  /**
   * Transforms all record field names in each selected value using the given
   * function.
   */
  def transformFields(f: (DynamicOptic, String) => String): DynamicValueSelection =
    map(_.transformFields(f))

  /**
   * Transforms all map keys in each selected value using the given function.
   */
  def transformMapKeys(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValueSelection =
    map(_.transformMapKeys(f))

  // ─────────────────────────────────────────────────────────────────────────
  // Pruning/Retention
  // ─────────────────────────────────────────────────────────────────────────

  /** Removes values matching the predicate from each selected value. */
  def prune(p: DynamicValue => Boolean): DynamicValueSelection = map(_.prune(p))

  /**
   * Removes values at paths matching the predicate from each selected value.
   */
  def prunePath(p: DynamicOptic => Boolean): DynamicValueSelection = map(_.prunePath(p))

  /**
   * Removes values matching both path and value predicates from each selected
   * value.
   */
  def pruneBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValueSelection =
    map(_.pruneBoth(p))

  /** Retains only values matching the predicate in each selected value. */
  def retain(p: DynamicValue => Boolean): DynamicValueSelection = map(_.retain(p))

  /**
   * Retains only values at paths matching the predicate in each selected value.
   */
  def retainPath(p: DynamicOptic => Boolean): DynamicValueSelection = map(_.retainPath(p))

  /**
   * Retains only values matching both path and value predicates in each
   * selected value.
   */
  def retainBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValueSelection =
    map(_.retainBoth(p))

  /** Projects each selected value to only include the specified paths. */
  def project(paths: DynamicOptic*): DynamicValueSelection = map(_.project(paths: _*))

  // ─────────────────────────────────────────────────────────────────────────
  // Binary Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Merges each value in this selection with each value in `that` selection.
   * Uses cartesian product semantics: if this has N values and that has M
   * values, the result has N × M values.
   */
  def merge(
    that: DynamicValueSelection,
    strategy: DynamicValueMergeStrategy = DynamicValueMergeStrategy.Auto
  ): DynamicValueSelection =
    DynamicValueSelection(
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
  def modifyOrFail(path: DynamicOptic)(pf: PartialFunction[DynamicValue, DynamicValue]): DynamicValueSelection =
    flatMap { dv =>
      dv.modifyOrFail(path)(pf) match {
        case Right(v) => DynamicValueSelection.succeed(v)
        case Left(e)  => DynamicValueSelection.fail(e)
      }
    }

  /**
   * Sets a value at the given path. Fails if the path doesn't exist.
   */
  def setOrFail(path: DynamicOptic, value: DynamicValue): DynamicValueSelection =
    flatMap { dv =>
      dv.setOrFail(path, value) match {
        case Right(v) => DynamicValueSelection.succeed(v)
        case Left(e)  => DynamicValueSelection.fail(e)
      }
    }

  /**
   * Deletes the value at the given path. Fails if the path doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): DynamicValueSelection =
    flatMap { dv =>
      dv.deleteOrFail(path) match {
        case Right(v) => DynamicValueSelection.succeed(v)
        case Left(e)  => DynamicValueSelection.fail(e)
      }
    }

  /**
   * Inserts a value at the given path. Fails if the path already exists or the
   * parent doesn't exist.
   */
  def insertOrFail(path: DynamicOptic, value: DynamicValue): DynamicValueSelection =
    flatMap { dv =>
      dv.insertOrFail(path, value) match {
        case Right(v) => DynamicValueSelection.succeed(v)
        case Left(e)  => DynamicValueSelection.fail(e)
      }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Type-Directed Extraction
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Narrows the single selected value to the specified DynamicValue type. Fails
   * if there are 0 or more than 1 values, or if the value doesn't match.
   */
  def as(t: DynamicValueType): Either[SchemaError, t.Type] =
    one.flatMap { dv =>
      dv.as(t).toRight(SchemaError(s"Expected ${t} but got ${dv.valueType}"))
    }

  /**
   * Narrows all selected values to the specified DynamicValue type. Values not
   * matching the type are silently dropped.
   */
  def asAll(t: DynamicValueType): Either[SchemaError, Chunk[t.Type]] =
    either.map(_.flatMap(_.as(t).toList))

  /**
   * Extracts the underlying Scala value from the single selected DynamicValue.
   * Fails if there are 0 or more than 1 values, or if the value doesn't match.
   */
  def unwrap(t: DynamicValueType): Either[SchemaError, t.Unwrap] =
    one.flatMap { dv =>
      dv.unwrap(t).toRight(SchemaError(s"Cannot unwrap ${dv.valueType} as ${t}"))
    }

  /**
   * Extracts the underlying Scala values from all selected DynamicValues.
   * Values not matching the type are silently dropped.
   */
  def unwrapAll(t: DynamicValueType): Either[SchemaError, Chunk[t.Unwrap]] =
    either.map(_.flatMap(_.unwrap(t).toList))

  /**
   * Extracts a primitive value of type A from the single selected value. Fails
   * if there are 0 or more than 1 values, or if the value isn't the expected
   * primitive.
   */
  def asPrimitive[A](pt: PrimitiveType[A]): Either[SchemaError, A] =
    one.flatMap { dv =>
      dv.asPrimitive(pt).toRight(SchemaError(s"Expected primitive ${pt} but got ${dv.valueType}"))
    }

  /**
   * Extracts primitive values of type A from all selected values. Values not
   * matching the type are silently dropped.
   */
  def asPrimitiveAll[A](pt: PrimitiveType[A]): Either[SchemaError, Chunk[A]] =
    either.map(_.flatMap(_.asPrimitive(pt).toList))
}

object DynamicValueSelection {

  /** Creates a successful selection with a single value. */
  def succeed(dv: DynamicValue): DynamicValueSelection = DynamicValueSelection(Right(Chunk(dv)))

  /** Creates a successful selection with multiple values. */
  def succeedMany(dvs: Chunk[DynamicValue]): DynamicValueSelection = DynamicValueSelection(Right(dvs))

  /** Creates a failed selection with an error. */
  def fail(error: SchemaError): DynamicValueSelection = DynamicValueSelection(Left(error))

  /** An empty successful selection. */
  val empty: DynamicValueSelection = DynamicValueSelection(Right(Chunk.empty))
}
