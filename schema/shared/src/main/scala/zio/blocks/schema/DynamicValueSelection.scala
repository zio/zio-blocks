/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * The result of selecting parts of a [[DynamicValue]]: zero or more values, or
 * the [[SchemaError]] that explains why the selection failed.
 * `DynamicValue#get` and `DynamicValue#select` return one, and its methods
 * chain further steps. Navigation steps apply to every selected value; when a
 * step fails for any of them, the whole selection fails with the first error.
 * An empty selection is a success, not a failure.
 *
 * {{{
 * val dv = DynamicValue.Record(
 *   "users" -> DynamicValue.Sequence(
 *     DynamicValue.Record("name" -> DynamicValue.string("Alice"))
 *   )
 * )
 *
 * val names: DynamicValueSelection =
 *   dv.get("users").sequences.apply(0).get("name").primitives
 * }}}
 */
final case class DynamicValueSelection(either: Either[SchemaError, Chunk[DynamicValue]]) extends AnyVal {

  // ─────────────────────────────────────────────────────────────────────────
  // Basic operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns `true` unless the selection failed. An empty selection is a
   * success.
   */
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
    else new Left(SchemaError(s"Expected single value but got ${v.length}"))
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
   * Returns the first selected value. Fails when the selection is empty or
   * failed.
   */
  def any: Either[SchemaError, DynamicValue] = either.flatMap { v =>
    v.headOption match {
      case Some(dv) => new Right(dv)
      case _        => new Left(SchemaError("Expected at least one value but got none"))
    }
  }

  /**
   * Returns all selected values condensed into a single DynamicValue. If there
   * are multiple values, wraps them in a Sequence. Fails if the selection is
   * empty or an error.
   */
  def all: Either[SchemaError, DynamicValue] = either.flatMap { v =>
    if (v.isEmpty) new Left(SchemaError("Expected at least one value but got none"))
    else
      new Right({
        if (v.length == 1) v.head
        else new DynamicValue.Sequence(v)
      })
  }

  /**
   * Returns all selected values as a [[DynamicValue.Sequence]], also when there
   * is one value or none. A failed selection gives its error.
   */
  def toSequence: Either[SchemaError, DynamicValue] = either.map(v => new DynamicValue.Sequence(v))

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
    new DynamicValueSelection(either.map(_.filter(dv => dv.valueType == t)))

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Selects the field `fieldName` of every selected value. Fails when any of
   * them is not a record or lacks the field.
   */
  def get(fieldName: String): DynamicValueSelection = flatMap(_.get(fieldName))

  /**
   * Selects the element at `index` of every selected value. Fails when any of
   * them is not a sequence or is too short.
   */
  def apply(index: Int): DynamicValueSelection = flatMap(_.get(index))

  /**
   * Selects the field `fieldName` of every selected value, the same as
   * `get(fieldName)`.
   */
  def apply(fieldName: String): DynamicValueSelection = get(fieldName)

  /**
   * Selects the value at `key` of every selected value. Fails when any of them
   * is not a map or lacks the key.
   */
  def get(key: DynamicValue): DynamicValueSelection = flatMap(_.get(key))

  /**
   * Selects what `path` reaches in every selected value, as `DynamicValue#get`
   * does. Fails when the path reaches nothing in any of them.
   */
  def get(path: DynamicOptic): DynamicValueSelection = flatMap(_.get(path))

  /**
   * Selects what `path` reaches in every selected value, the same as
   * `get(path)`.
   */
  def apply(path: DynamicOptic): DynamicValueSelection = get(path)

  /**
   * Selects the payload of every selected value. Fails when any of them is not
   * a variant of the case `caseName`.
   */
  def getCase(caseName: String): DynamicValueSelection = flatMap(_.getCase(caseName))

  // ─────────────────────────────────────────────────────────────────────────
  // Combinators
  // ─────────────────────────────────────────────────────────────────────────

  /** Maps a function over all selected values. */
  def map(f: DynamicValue => DynamicValue): DynamicValueSelection = new DynamicValueSelection(either.map(_.map(f)))

  /**
   * Applies `f` to every selected value and concatenates the values of the
   * selections it returns, in order. Fails with the first failure among them; a
   * failed selection stays failed.
   */
  def flatMap(f: DynamicValue => DynamicValueSelection): DynamicValueSelection =
    new DynamicValueSelection(either.flatMap { dvs =>
      val results    = dvs.map(dv => f(dv).either)
      val firstError = results.collectFirst { case Left(e) => e }
      firstError match {
        case Some(error) => Left(error)
        case _           => Right(results.collect { case Right(v) => v }.flatten)
      }
    })

  /** Filters selected values based on a predicate. */
  def filter(p: DynamicValue => Boolean): DynamicValueSelection = new DynamicValueSelection(either.map(_.filter(p)))

  /** Collects values for which the partial function is defined. */
  def collect[A](pf: PartialFunction[DynamicValue, A]): Either[SchemaError, Chunk[A]] = either.map(_.collect(pf))

  /** Returns this selection if successful, otherwise the alternative. */
  def orElse(alternative: => DynamicValueSelection): DynamicValueSelection =
    if (isSuccess) this
    else alternative

  /** Returns this selection's values, or the default on failure. */
  def getOrElse(default: => Chunk[DynamicValue]): Chunk[DynamicValue] = either.getOrElse(default)

  /**
   * Concatenates the values of the two selections. When both failed, the result
   * fails with both errors; when one failed, with its error.
   */
  def ++(other: DynamicValueSelection): DynamicValueSelection = new DynamicValueSelection((either, other.either) match {
    case (Right(v1), Right(v2)) => new Right(v1 ++ v2)
    case (Left(e1), Left(e2))   => new Left(e1 ++ e2)
    case (Left(e), _)           => new Left(e)
    case (_, Left(e))           => new Left(e)
  })

  // ─────────────────────────────────────────────────────────────────────────
  // Query Methods (recursive search within selection)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Searches every selected value and everything inside it, and selects the
   * values that satisfy `p`. The search visits a value before its children
   * (record fields, variant payloads, sequence elements and map values; map
   * keys are not visited), so the selected values themselves are tested first.
   */
  def query(p: DynamicValue => Boolean): DynamicValueSelection =
    flatMap(dv => DynamicValueSelection.succeedMany(DynamicValue.query(dv, DynamicOptic.root, (_, v) => p(v))))

  /**
   * Like `query`, testing the path of each value instead. Paths start at the
   * root for every selected value.
   */
  def queryPath(p: DynamicOptic => Boolean): DynamicValueSelection =
    flatMap(dv => DynamicValueSelection.succeedMany(DynamicValue.query(dv, DynamicOptic.root, (path, _) => p(path))))

  /**
   * Like `query`, testing both the path and the value. Paths start at the root
   * for every selected value.
   */
  def queryBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValueSelection =
    flatMap(dv => DynamicValueSelection.succeedMany(DynamicValue.query(dv, DynamicOptic.root, p)))

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
  def modify(path: DynamicOptic)(f: DynamicValue => DynamicValue): DynamicValueSelection = map(_.modify(path)(f))

  /** Sets a value at the given path in each selected value. */
  def set(path: DynamicOptic, value: DynamicValue): DynamicValueSelection = map(_.set(path, value))

  /** Deletes the value at the given path in each selected value. */
  def delete(path: DynamicOptic): DynamicValueSelection = map(_.delete(path))

  /** Inserts a value at the given path in each selected value. */
  def insert(path: DynamicOptic, value: DynamicValue): DynamicValueSelection = map(_.insert(path, value))

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation
  // ─────────────────────────────────────────────────────────────────────────

  /** Transforms each selected value bottom-up using the given function. */
  def transformUp(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValueSelection = map(_.transformUp(f))

  /** Transforms each selected value top-down using the given function. */
  def transformDown(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValueSelection = map(_.transformDown(f))

  /**
   * Transforms all record field names in each selected value using the given
   * function.
   */
  def transformFields(f: (DynamicOptic, String) => String): DynamicValueSelection = map(_.transformFields(f))

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
  def pruneBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValueSelection = map(_.pruneBoth(p))

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
  def retainBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValueSelection = map(_.retainBoth(p))

  /** Projects each selected value to only include the specified paths. */
  def project(paths: DynamicOptic*): DynamicValueSelection = map(_.project(paths: _*))

  // ─────────────────────────────────────────────────────────────────────────
  // Binary Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Merges each value of this selection with each value of `that`, with
   * `DynamicValue#merge`. The result has N × M values for N and M values: the
   * first value of this selection merged with every value of `that`, in order,
   * then the second, and so on. Fails when either selection failed.
   */
  def merge(
    that: DynamicValueSelection,
    strategy: DynamicValueMergeStrategy = DynamicValueMergeStrategy.Auto
  ): DynamicValueSelection =
    new DynamicValueSelection(
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
   * Applies `DynamicValue#modifyOrFail` to every selected value. Fails when the
   * path reaches nothing in any of them; a path that stops after a matching
   * field or map-key step does not fail. Values at which `pf` is not defined
   * are left unchanged, except at the root path (see
   * `DynamicValue#modifyOrFail`).
   */
  def modifyOrFail(path: DynamicOptic)(pf: PartialFunction[DynamicValue, DynamicValue]): DynamicValueSelection =
    flatMap { dv =>
      dv.modifyOrFail(path)(pf) match {
        case Right(v) => DynamicValueSelection.succeed(v)
        case Left(e)  => DynamicValueSelection.fail(e)
      }
    }

  /**
   * Applies `DynamicValue#setOrFail` to every selected value. Fails when the
   * path reaches nothing in any of them; a path that stops after a matching
   * field or map-key step does not fail (see `DynamicValue#setOrFail`).
   */
  def setOrFail(path: DynamicOptic, value: DynamicValue): DynamicValueSelection = flatMap { dv =>
    dv.setOrFail(path, value) match {
      case Right(v) => DynamicValueSelection.succeed(v)
      case Left(e)  => DynamicValueSelection.fail(e)
    }
  }

  /**
   * Deletes the value at the given path. Fails if the path doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): DynamicValueSelection = flatMap { dv =>
    dv.deleteOrFail(path) match {
      case Right(v) => DynamicValueSelection.succeed(v)
      case Left(e)  => DynamicValueSelection.fail(e)
    }
  }

  /**
   * Inserts a value at the given path. Fails if the path already exists or the
   * parent doesn't exist.
   */
  def insertOrFail(path: DynamicOptic, value: DynamicValue): DynamicValueSelection = flatMap { dv =>
    dv.insertOrFail(path, value) match {
      case Right(v) => DynamicValueSelection.succeed(v)
      case Left(e)  => DynamicValueSelection.fail(e)
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type-Directed Extraction
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Narrows the single selected value to the type `t`. Fails if there are 0 or
   * more than 1 values, or if the value is of another type. The error message
   * names both types as `<function1>` (see `docs/dev/issues.md`).
   */
  def as(t: DynamicValueType): Either[SchemaError, t.Type] = one.flatMap { dv =>
    dv.as(t).toRight(SchemaError(s"Expected $t but got ${dv.valueType}"))
  }

  /**
   * Narrows all selected values to the specified DynamicValue type. Values not
   * matching the type are silently dropped.
   */
  def asAll(t: DynamicValueType): Either[SchemaError, Chunk[t.Type]] = either.map(_.flatMap(_.as(t)))

  /**
   * Returns the contents of the single selected value (see
   * `DynamicValue#unwrap`). Fails if there are 0 or more than 1 values, or if
   * the value is not of type `t`; the error message names the types as
   * `<function1>` (see `docs/dev/issues.md`).
   */
  def unwrap(t: DynamicValueType): Either[SchemaError, t.Unwrap] = one.flatMap { dv =>
    dv.unwrap(t).toRight(SchemaError(s"Cannot unwrap ${dv.valueType} as $t"))
  }

  /**
   * Extracts the underlying Scala values from all selected DynamicValues.
   * Values not matching the type are silently dropped.
   */
  def unwrapAll(t: DynamicValueType): Either[SchemaError, Chunk[t.Unwrap]] = either.map(_.flatMap(_.unwrap(t)))

  /**
   * Converts the single selected value to an `A` with `pt`, as
   * `DynamicValue#asPrimitive` does. Fails if there are 0 or more than 1
   * values, or if `pt` does not accept the value.
   */
  def asPrimitive[A](pt: PrimitiveType[A]): Either[SchemaError, A] = one.flatMap { dv =>
    dv.asPrimitive(pt).toRight(SchemaError(s"Expected primitive $pt but got ${dv.valueType}"))
  }

  /**
   * Converts every selected value to an `A` with `pt`, as
   * `DynamicValue#asPrimitive` does, and drops the values it does not accept.
   */
  def asPrimitiveAll[A](pt: PrimitiveType[A]): Either[SchemaError, Chunk[A]] = either.map(_.flatMap(_.asPrimitive(pt)))
}

/**
 * Constructors for [[DynamicValueSelection]].
 */
object DynamicValueSelection {

  /** Creates a successful selection with a single value. */
  def succeed(dv: DynamicValue): DynamicValueSelection = new DynamicValueSelection(new Right(Chunk.single(dv)))

  /** Creates a successful selection with multiple values. */
  def succeedMany(dvs: Chunk[DynamicValue]): DynamicValueSelection = new DynamicValueSelection(new Right(dvs))

  /** Creates a failed selection with an error. */
  def fail(error: SchemaError): DynamicValueSelection = new DynamicValueSelection(new Left(error))

  /** An empty successful selection. */
  val empty: DynamicValueSelection = new DynamicValueSelection(new Right(Chunk.empty))
}
