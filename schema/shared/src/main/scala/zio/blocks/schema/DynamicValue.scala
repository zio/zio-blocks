package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.Json
import zio.blocks.schema.patch.{DynamicPatch, Differ}

/**
 * A schema-less, dynamically-typed representation of any value.
 *
 * `DynamicValue` provides a universal data model that can represent any
 * structured value without requiring compile-time type information. It serves
 * as an intermediate representation for serialization, schema evolution, data
 * transformation, and cross-format conversion.
 *
 * The ADT consists of six cases:
 *   - [[DynamicValue.Primitive]] - Scalar values (strings, numbers, booleans,
 *     temporal types, etc.)
 *   - [[DynamicValue.Record]] - Named fields, analogous to case classes or JSON
 *     objects
 *   - [[DynamicValue.Variant]] - Tagged unions, analogous to sealed traits
 *   - [[DynamicValue.Sequence]] - Ordered collections (lists, arrays, vectors)
 *   - [[DynamicValue.Map]] - Key-value pairs where keys are also DynamicValues
 *   - [[DynamicValue.Null]] - Represents absence of a value
 *
 * Navigation and modification use [[DynamicOptic]], a path-based lens system
 * for traversing and updating nested structures.
 *
 * @see
 *   [[Schema.toDynamicValue]] for converting typed values to DynamicValue
 * @see
 *   [[Schema.fromDynamicValue]] for converting DynamicValue back to typed
 *   values
 * @see
 *   [[DynamicOptic]] for path-based navigation
 */
sealed trait DynamicValue {

  /**
   * Returns a numeric index representing this value's type, used for ordering
   * values of different types. The ordering is: Primitive(0) < Record(1) <
   * Variant(2) < Sequence(3) < Map(4) < Null(5).
   */
  def typeIndex: Int

  def compare(that: DynamicValue): Int

  final def >(that: DynamicValue): Boolean = compare(that) > 0

  final def >=(that: DynamicValue): Boolean = compare(that) >= 0

  final def <(that: DynamicValue): Boolean = compare(that) < 0

  final def <=(that: DynamicValue): Boolean = compare(that) <= 0

  /**
   * Computes the difference between this DynamicValue and another, producing a
   * [[zio.blocks.schema.patch.DynamicPatch]] that can transform this value into
   * `that`.
   *
   * The diff algorithm produces minimal patches using type-appropriate
   * operations: numeric deltas for numbers, string edit operations for text,
   * and structural diffs for records, sequences, and maps.
   */
  def diff(that: DynamicValue): DynamicPatch = DynamicValue.diff(this, that)

  // ─────────────────────────────────────────────────────────────────────────
  // Type Information
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the [[DynamicValueType]] of this DynamicValue. */
  def valueType: DynamicValueType

  /**
   * Returns true if this DynamicValue is of the specified type.
   */
  def is(t: DynamicValueType): Boolean = this.valueType == t

  /**
   * Narrows this DynamicValue to the specified type, returning `Some` if the
   * types match or `None` otherwise.
   */
  def as(t: DynamicValueType): Option[t.Type] = None

  /**
   * Extracts the underlying value from this DynamicValue if it matches the
   * specified type.
   */
  def unwrap(t: DynamicValueType): Option[t.Unwrap] = None

  /**
   * Extracts a primitive value of type A if this is a Primitive with matching
   * type.
   */
  def asPrimitive[A](pt: PrimitiveType[A]): Option[A] = pt.fromDynamicValue(this).toOption

  // ─────────────────────────────────────────────────────────────────────────
  // Direct Accessors
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the fields if this is a Record, otherwise an empty Chunk. */
  def fields: Chunk[(String, DynamicValue)] = Chunk.empty

  /** Returns the elements if this is a Sequence, otherwise an empty Chunk. */
  def elements: Chunk[DynamicValue] = Chunk.empty

  /** Returns the entries if this is a Map, otherwise an empty Chunk. */
  def entries: Chunk[(DynamicValue, DynamicValue)] = Chunk.empty

  /** Returns the case name if this is a Variant, otherwise None. */
  def caseName: Option[String] = None

  /** Returns the case value if this is a Variant, otherwise None. */
  def caseValue: Option[DynamicValue] = None

  /** Returns the primitive value if this is a Primitive, otherwise None. */
  def primitiveValue: Option[PrimitiveValue] = None

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigates to a field in a Record by name. Returns a DynamicValueSelection
   * containing all matching values.
   */
  def get(fieldName: String): DynamicValueSelection =
    DynamicValueSelection.fail(SchemaError(s"Cannot get field '$fieldName' from non-Record value"))

  /**
   * Navigates to an element in a Sequence by index. Returns a
   * DynamicValueSelection containing the value, or an error if index is out of
   * bounds.
   */
  def get(index: Int): DynamicValueSelection =
    DynamicValueSelection.fail(SchemaError(s"Cannot get index $index from non-Sequence value"))

  /**
   * Navigates to an entry in a Map by key. Returns a DynamicValueSelection
   * containing all matching values.
   */
  def get(key: DynamicValue): DynamicValueSelection =
    DynamicValueSelection.fail(SchemaError(s"Cannot get key $key from non-Map value"))

  /**
   * Navigates to the value(s) at the given path. Returns a
   * DynamicValueSelection that may contain zero or more values.
   */
  def get(path: DynamicOptic): DynamicValueSelection = DynamicValue.getAtPath(this, path)

  /**
   * Navigates into a Variant case by name. Returns a DynamicValueSelection
   * containing the case value if the case matches.
   */
  def getCase(name: String): DynamicValueSelection =
    DynamicValueSelection.fail(SchemaError(s"Cannot get case '$name' from non-Variant value"))

  // ─────────────────────────────────────────────────────────────────────────
  // Path-based Modification
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Modifies the value at the given path using a function. If the path doesn't
   * exist, returns the original DynamicValue unchanged.
   */
  def modify(path: DynamicOptic)(f: DynamicValue => DynamicValue): DynamicValue =
    DynamicValue.modifyAtPath(this, path, f).getOrElse(this)

  /**
   * Modifies the value at the given path using a partial function. Returns Left
   * with an error if the path doesn't exist or the partial function is not
   * defined.
   */
  def modifyOrFail(path: DynamicOptic)(
    pf: PartialFunction[DynamicValue, DynamicValue]
  ): Either[SchemaError, DynamicValue] =
    DynamicValue.modifyAtPathOrFail(this, path, pf)

  /**
   * Sets a value at the given path. If the path doesn't exist, returns the
   * original DynamicValue unchanged.
   */
  def set(path: DynamicOptic, value: DynamicValue): DynamicValue = modify(path)(_ => value)

  /**
   * Sets a value at the given path. Returns Left with an error if the path
   * doesn't exist.
   */
  def setOrFail(path: DynamicOptic, value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicValue.modifyAtPathOrFail(this, path, { case _ => value })

  /**
   * Deletes the value at the given path. If the path doesn't exist, returns the
   * original DynamicValue unchanged.
   */
  def delete(path: DynamicOptic): DynamicValue = DynamicValue.deleteAtPath(this, path).getOrElse(this)

  /**
   * Deletes the value at the given path. Returns Left with an error if the path
   * doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): Either[SchemaError, DynamicValue] =
    DynamicValue.deleteAtPathOrFail(this, path)

  /**
   * Inserts a value at the given path. If the path already exists, returns the
   * original DynamicValue unchanged.
   */
  def insert(path: DynamicOptic, value: DynamicValue): DynamicValue =
    DynamicValue.insertAtPath(this, path, value).getOrElse(this)

  /**
   * Inserts a value at the given path. Returns Left with an error if the path
   * already exists or the parent doesn't exist.
   */
  def insertOrFail(path: DynamicOptic, value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicValue.insertAtPathOrFail(this, path, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Merging
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Merges this DynamicValue with another using the specified strategy.
   */
  def merge(that: DynamicValue, strategy: DynamicValueMergeStrategy = DynamicValueMergeStrategy.Auto): DynamicValue =
    DynamicValue.merge(this, that, strategy)

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization
  // ─────────────────────────────────────────────────────────────────────────

  /** Recursively sorts all Record fields alphabetically. */
  def sortFields: DynamicValue = DynamicValue.sortFieldsImpl(this)

  /** Recursively sorts all Map entries by key. */
  def sortMapKeys: DynamicValue = DynamicValue.sortMapKeysImpl(this)

  /** Recursively removes all Null values from containers. */
  def dropNulls: DynamicValue = DynamicValue.dropNullsImpl(this)

  /** Recursively removes all Primitive(Unit) values from containers. */
  def dropUnits: DynamicValue = DynamicValue.dropUnitsImpl(this)

  /** Recursively removes empty Records, Sequences, and Maps. */
  def dropEmpty: DynamicValue = DynamicValue.dropEmptyImpl(this)

  /** Applies sortFields, sortMapKeys, dropNulls, dropUnits, and dropEmpty. */
  def normalize: DynamicValue = sortFields.sortMapKeys.dropNulls.dropUnits.dropEmpty

  // ─────────────────────────────────────────────────────────────────────────
  // Schema Conformance
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Validates this value against a [[DynamicSchema]].
   *
   * @param schema
   *   The schema to validate against
   * @return
   *   `None` if this value conforms to the schema, or `Some(SchemaError)`
   *   describing the validation failure
   * @see
   *   [[DynamicSchema.check]] for the underlying validation logic
   */
  def check(schema: DynamicSchema): Option[SchemaError] = schema.check(this)

  /**
   * Tests whether this value conforms to a [[DynamicSchema]].
   *
   * @param schema
   *   The schema to validate against
   * @return
   *   `true` if this value passes all validation checks, `false` otherwise
   */
  def conforms(schema: DynamicSchema): Boolean = check(schema).isEmpty

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transforms this DynamicValue bottom-up using the given function. The
   * function receives the current path and the DynamicValue at that path. Child
   * values are transformed before their parents.
   */
  def transformUp(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValue =
    DynamicValue.transformUpImpl(this, DynamicOptic.root, f)

  /**
   * Transforms this DynamicValue top-down using the given function. The
   * function receives the current path and the DynamicValue at that path.
   * Parent values are transformed before their children.
   */
  def transformDown(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValue =
    DynamicValue.transformDownImpl(this, DynamicOptic.root, f)

  /**
   * Transforms all Record field names using the given function. The function
   * receives the current path and the field name at that path.
   */
  def transformFields(f: (DynamicOptic, String) => String): DynamicValue =
    DynamicValue.transformFieldsImpl(this, DynamicOptic.root, f)

  /**
   * Transforms all Map keys using the given function. The function receives the
   * current path and the key at that path.
   */
  def transformMapKeys(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValue =
    DynamicValue.transformMapKeysImpl(this, DynamicOptic.root, f)

  // ─────────────────────────────────────────────────────────────────────────
  // Selection
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Wraps this DynamicValue in a [[DynamicValueSelection]].
   */
  def select: DynamicValueSelection = DynamicValueSelection.succeed(this)

  /**
   * Wraps this DynamicValue in a [[DynamicValueSelection]] if its type matches
   * the specified [[DynamicValueType]], otherwise returns an empty selection.
   */
  def select(t: DynamicValueType): DynamicValueSelection =
    if (this.valueType == t) DynamicValueSelection.succeed(this)
    else DynamicValueSelection.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Pruning/Retention/Projection/Partition
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively removes elements/fields for which the predicate returns true.
   */
  def prune(p: DynamicValue => Boolean): DynamicValue =
    DynamicValue.pruneImpl(this, DynamicOptic.root, (_, dv) => p(dv))

  /**
   * Recursively removes elements/fields at paths for which the predicate
   * returns true.
   */
  def prunePath(p: DynamicOptic => Boolean): DynamicValue =
    DynamicValue.pruneImpl(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Recursively removes elements/fields for which the predicate on both path
   * and value returns true.
   */
  def pruneBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValue =
    DynamicValue.pruneImpl(this, DynamicOptic.root, p)

  /**
   * Recursively keeps only elements/fields for which the predicate returns
   * true.
   */
  def retain(p: DynamicValue => Boolean): DynamicValue =
    DynamicValue.retainImpl(this, DynamicOptic.root, (_, dv) => p(dv))

  /**
   * Recursively keeps only elements/fields at paths for which the predicate
   * returns true.
   */
  def retainPath(p: DynamicOptic => Boolean): DynamicValue =
    DynamicValue.retainImpl(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Recursively keeps only elements/fields for which the predicate on both path
   * and value returns true.
   */
  def retainBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValue =
    DynamicValue.retainImpl(this, DynamicOptic.root, p)

  /**
   * Projects only the specified paths from this DynamicValue. Creates a new
   * DynamicValue containing only values at the given paths.
   */
  def project(paths: DynamicOptic*): DynamicValue = DynamicValue.projectImpl(this, paths)

  /**
   * Partitions elements/fields based on a predicate on the value. Returns a
   * tuple of (matching, non-matching) DynamicValues.
   */
  def partition(p: DynamicValue => Boolean): (DynamicValue, DynamicValue) =
    DynamicValue.partitionImpl(this, DynamicOptic.root, (_, dv) => p(dv))

  /**
   * Partitions elements/fields based on a predicate on the path. Returns a
   * tuple of (matching, non-matching) DynamicValues.
   */
  def partitionPath(p: DynamicOptic => Boolean): (DynamicValue, DynamicValue) =
    DynamicValue.partitionImpl(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Partitions elements/fields based on a predicate on both path and value.
   * Returns a tuple of (matching, non-matching) DynamicValues.
   */
  def partitionBoth(p: (DynamicOptic, DynamicValue) => Boolean): (DynamicValue, DynamicValue) =
    DynamicValue.partitionImpl(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Folding
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Folds over the DynamicValue structure bottom-up. The function receives the
   * current path, the DynamicValue, and the accumulator. Child values are
   * folded before their parents.
   */
  def foldUp[B](z: B)(f: (DynamicOptic, DynamicValue, B) => B): B =
    DynamicValue.foldUpImpl(this, DynamicOptic.root, z, f)

  /**
   * Folds over the DynamicValue structure top-down. The function receives the
   * current path, the DynamicValue, and the accumulator. Parent values are
   * folded before their children.
   */
  def foldDown[B](z: B)(f: (DynamicOptic, DynamicValue, B) => B): B =
    DynamicValue.foldDownImpl(this, DynamicOptic.root, z, f)

  /**
   * Folds over the DynamicValue structure bottom-up, allowing failure.
   */
  def foldUpOrFail[B](z: B)(
    f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] =
    DynamicValue.foldUpOrFailImpl(this, DynamicOptic.root, z, f)

  /**
   * Folds over the DynamicValue structure top-down, allowing failure.
   */
  def foldDownOrFail[B](z: B)(
    f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] =
    DynamicValue.foldDownOrFailImpl(this, DynamicOptic.root, z, f)

  /**
   * Converts this DynamicValue to a Chunk of path-value pairs. Each pair
   * contains the path to a leaf value and the value itself.
   */
  def toKV: Chunk[(DynamicOptic, DynamicValue)] = DynamicValue.toKVImpl(this, DynamicOptic.root)

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion
  // ─────────────────────────────────────────────────────────────────────────

  /** Converts this DynamicValue to a Json value. */
  def toJson: Json = Json.fromDynamicValue(this)

  def toEjson(indent: Int = 0): String = DynamicValue.toEjson(this, indent)

  override def toString: String = toEjson()
}

object DynamicValue {

  // ─────────────────────────────────────────────────────────────────────────
  // ADT Cases
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A scalar value wrapped in a [[PrimitiveValue]].
   *
   * Primitives include strings, booleans, all numeric types (Int, Long, Double,
   * Float, Short, Byte, BigInt, BigDecimal), temporal types (Instant, Duration,
   * LocalDate, etc.), UUID, Char, and Unit.
   */
  case class Primitive(value: PrimitiveValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Primitive(thatValue) => value == thatValue
      case _                    => false
    }

    override def hashCode: Int = value.hashCode

    def typeIndex: Int = 0

    def valueType: DynamicValueType = DynamicValueType.Primitive

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t == DynamicValueType.Primitive) Some(this.asInstanceOf[t.Type]) else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t == DynamicValueType.Primitive) Some(value.asInstanceOf[t.Unwrap]) else None

    override def primitiveValue: Option[PrimitiveValue] = Some(value)

    def compare(that: DynamicValue): Int = that match {
      case thatPrimitive: Primitive => value.compare(thatPrimitive.value)
      case _                        => -that.typeIndex
    }
  }

  /**
   * A collection of named fields, analogous to a case class or JSON object.
   *
   * Field order is preserved and significant for equality comparison. Use
   * [[DynamicValue.sortFields]] to normalize field ordering for
   * order-independent comparison.
   */
  final case class Record(override val fields: Chunk[(String, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Record(thatFields) =>
        val len = fields.length
        if (len != thatFields.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = fields(idx)
          val kv2 = thatFields(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = fields.hashCode

    def typeIndex: Int = 1

    def valueType: DynamicValueType = DynamicValueType.Record

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t == DynamicValueType.Record) Some(this.asInstanceOf[t.Type]) else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t == DynamicValueType.Record) Some(fields.asInstanceOf[t.Unwrap]) else None

    override def get(fieldName: String): DynamicValueSelection = {
      val matches = fields.collect { case (name, value) if name == fieldName => value }
      if (matches.isEmpty) DynamicValueSelection.fail(SchemaError(s"Field '$fieldName' not found"))
      else DynamicValueSelection.succeedMany(matches)
    }

    def compare(that: DynamicValue): Int = that match {
      case thatRecord: Record =>
        val xs     = fields
        val ys     = thatRecord.fields
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 1 - that.typeIndex
    }
  }

  object Record {
    val empty: Record = Record(Chunk.empty)

    def apply(fields: (String, DynamicValue)*): Record = new Record(Chunk.from(fields))
  }

  /**
   * A tagged union value, analogous to a sealed trait with case classes.
   *
   * Contains a case name identifying which variant is active and the associated
   * value for that case.
   */
  final case class Variant(caseNameValue: String, value: DynamicValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Variant(thatCaseName, thatValue) => caseNameValue == thatCaseName && value == thatValue
      case _                                => false
    }

    override def hashCode: Int = 31 * caseNameValue.hashCode + value.hashCode

    def typeIndex: Int = 2

    def valueType: DynamicValueType = DynamicValueType.Variant

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t == DynamicValueType.Variant) Some(this.asInstanceOf[t.Type]) else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t == DynamicValueType.Variant) Some((caseNameValue, value).asInstanceOf[t.Unwrap]) else None

    override def caseName: Option[String] = Some(caseNameValue)

    override def caseValue: Option[DynamicValue] = Some(value)

    override def getCase(name: String): DynamicValueSelection =
      if (caseNameValue == name) DynamicValueSelection.succeed(value)
      else DynamicValueSelection.fail(SchemaError(s"Variant case '$name' does not match '$caseNameValue'"))

    def compare(that: DynamicValue): Int = that match {
      case thatVariant: Variant =>
        val cmp = caseNameValue.compare(thatVariant.caseNameValue)
        if (cmp != 0) return cmp
        value.compare(thatVariant.value)
      case _ => 2 - that.typeIndex
    }
  }

  /**
   * An ordered collection of values, analogous to a List, Array, or Chunk.
   *
   * Element order is preserved and significant for equality comparison.
   */
  final case class Sequence(override val elements: Chunk[DynamicValue]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Sequence(thatElements) =>
        val len = elements.length
        if (len != thatElements.length) return false
        var idx = 0
        while (idx < len) {
          if (elements(idx) != thatElements(idx)) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = elements.hashCode

    def typeIndex: Int = 3

    def valueType: DynamicValueType = DynamicValueType.Sequence

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t == DynamicValueType.Sequence) Some(this.asInstanceOf[t.Type]) else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t == DynamicValueType.Sequence) Some(elements.asInstanceOf[t.Unwrap]) else None

    override def get(index: Int): DynamicValueSelection =
      if (index >= 0 && index < elements.length) DynamicValueSelection.succeed(elements(index))
      else DynamicValueSelection.fail(SchemaError(s"Index $index out of bounds (size: ${elements.length})"))

    def compare(that: DynamicValue): Int = that match {
      case thatSequence: Sequence =>
        val xs     = elements
        val ys     = thatSequence.elements
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val cmp = xs(idx).compare(ys(idx))
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 3 - that.typeIndex
    }
  }

  object Sequence {
    val empty: Sequence = Sequence(Chunk.empty)

    def apply(elements: DynamicValue*): Sequence = new Sequence(Chunk.from(elements))
  }

  /**
   * A collection of key-value pairs where both keys and values are
   * DynamicValues.
   *
   * Unlike [[Record]] which uses String keys, Map supports arbitrary
   * DynamicValue keys. Entry order is preserved and significant for equality
   * comparison. Use [[DynamicValue.sortMapKeys]] to normalize key ordering.
   */
  final case class Map(override val entries: Chunk[(DynamicValue, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Map(thatEntries) =>
        val len = entries.length
        if (len != thatEntries.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = entries(idx)
          val kv2 = thatEntries(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = entries.hashCode

    def typeIndex: Int = 4

    def valueType: DynamicValueType = DynamicValueType.Map

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t == DynamicValueType.Map) Some(this.asInstanceOf[t.Type]) else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t == DynamicValueType.Map) Some(entries.asInstanceOf[t.Unwrap]) else None

    override def get(key: DynamicValue): DynamicValueSelection = {
      val matches = entries.collect { case (k, v) if k == key => v }
      if (matches.isEmpty) DynamicValueSelection.fail(SchemaError(s"Key not found in Map"))
      else DynamicValueSelection.succeedMany(matches)
    }

    def compare(that: DynamicValue): Int = that match {
      case thatMap: Map =>
        val xs     = entries
        val ys     = thatMap.entries
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 4 - that.typeIndex
    }
  }

  object Map {
    val empty: Map = Map(Chunk.empty)

    def apply(entries: (DynamicValue, DynamicValue)*): Map = new Map(Chunk.from(entries))
  }

  /**
   * Represents the absence of a value.
   *
   * Analogous to JSON null or Scala's None. Use [[DynamicValue.dropNulls]] to
   * recursively remove Null values from containers.
   */
  case object Null extends DynamicValue {
    def typeIndex: Int = 5

    def valueType: DynamicValueType = DynamicValueType.Null

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t == DynamicValueType.Null) Some(this.asInstanceOf[t.Type]) else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t == DynamicValueType.Null) Some(().asInstanceOf[t.Unwrap]) else None

    def compare(that: DynamicValue): Int = that match {
      case Null => 0
      case _    => 5 - that.typeIndex
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Convenience Constructors
  // ─────────────────────────────────────────────────────────────────────────

  val unit: DynamicValue = Primitive(PrimitiveValue.Unit)

  def string(s: String): DynamicValue = Primitive(PrimitiveValue.String(s))

  def int(i: Int): DynamicValue = Primitive(PrimitiveValue.Int(i))

  def long(l: Long): DynamicValue = Primitive(PrimitiveValue.Long(l))

  def boolean(b: Boolean): DynamicValue = Primitive(PrimitiveValue.Boolean(b))

  def double(d: Double): DynamicValue = Primitive(PrimitiveValue.Double(d))

  def float(f: Float): DynamicValue = Primitive(PrimitiveValue.Float(f))

  def short(s: Short): DynamicValue = Primitive(PrimitiveValue.Short(s))

  def byte(b: Byte): DynamicValue = Primitive(PrimitiveValue.Byte(b))

  def char(c: Char): DynamicValue = Primitive(PrimitiveValue.Char(c))

  def bigInt(b: BigInt): DynamicValue = Primitive(PrimitiveValue.BigInt(b))

  def bigDecimal(b: BigDecimal): DynamicValue = Primitive(PrimitiveValue.BigDecimal(b))

  // ─────────────────────────────────────────────────────────────────────────
  // Ordering
  // ─────────────────────────────────────────────────────────────────────────

  implicit val ordering: Ordering[DynamicValue] = new Ordering[DynamicValue] {
    def compare(x: DynamicValue, y: DynamicValue): Int = x.compare(y)
  }

  /**
   * Compute the difference between two DynamicValues. Returns a DynamicPatch
   * that transforms oldValue into newValue.
   */
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch =
    Differ.diff(oldValue, newValue)

  // ─────────────────────────────────────────────────────────────────────────
  // Path Navigation Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def getAtPath(dv: DynamicValue, path: DynamicOptic): DynamicValueSelection = {
    val nodes = path.nodes
    if (nodes.isEmpty) return DynamicValueSelection.succeed(dv)

    var current: Chunk[DynamicValue] = Chunk(dv)
    var idx                          = 0
    val len                          = nodes.length

    while (idx < len && current.nonEmpty) {
      val node = nodes(idx)
      current = node match {
        case DynamicOptic.Node.Field(name) =>
          current.flatMap {
            case r: Record => r.fields.collect { case (n, v) if n == name => v }
            case _         => Chunk.empty
          }

        case DynamicOptic.Node.Case(name) =>
          current.flatMap {
            case v: Variant if v.caseNameValue == name => Chunk(v.value)
            case _                                     => Chunk.empty
          }

        case DynamicOptic.Node.AtIndex(i) =>
          current.flatMap {
            case s: Sequence if i >= 0 && i < s.elements.length => Chunk(s.elements(i))
            case _                                              => Chunk.empty
          }

        case DynamicOptic.Node.AtMapKey(key) =>
          current.flatMap {
            case m: Map => m.entries.collect { case (k, v) if k == key => v }
            case _      => Chunk.empty
          }

        case DynamicOptic.Node.AtIndices(indices) =>
          current.flatMap {
            case s: Sequence =>
              Chunk.from(indices.flatMap(i => if (i >= 0 && i < s.elements.length) Some(s.elements(i)) else None))
            case _ => Chunk.empty
          }

        case DynamicOptic.Node.AtMapKeys(keys) =>
          current.flatMap {
            case m: Map => Chunk.from(keys.flatMap(key => m.entries.collect { case (k, v) if k == key => v }))
            case _      => Chunk.empty
          }

        case DynamicOptic.Node.Elements =>
          current.flatMap {
            case s: Sequence => s.elements
            case _           => Chunk.empty
          }

        case DynamicOptic.Node.MapKeys =>
          current.flatMap {
            case m: Map => m.entries.map(_._1)
            case _      => Chunk.empty
          }

        case DynamicOptic.Node.MapValues =>
          current.flatMap {
            case m: Map => m.entries.map(_._2)
            case _      => Chunk.empty
          }

        case DynamicOptic.Node.Wrapped =>
          current
      }
      idx += 1
    }

    if (current.isEmpty) DynamicValueSelection.fail(SchemaError.message(s"Path not found", path))
    else DynamicValueSelection.succeedMany(current)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Path Modification Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def modifyAtPath(
    dv: DynamicValue,
    path: DynamicOptic,
    f: DynamicValue => DynamicValue
  ): Option[DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return Some(f(dv))

    modifyAtPathImpl(dv, nodes, 0, f)
  }

  private def modifyAtPathImpl(
    dv: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    f: DynamicValue => DynamicValue
  ): Option[DynamicValue] = {
    if (idx >= nodes.length) return Some(f(dv))

    nodes(idx) match {
      case DynamicOptic.Node.Field(name) =>
        dv match {
          case r: Record =>
            var found     = false
            val newFields = r.fields.map { case (n, v) =>
              if (n == name) {
                found = true
                modifyAtPathImpl(v, nodes, idx + 1, f) match {
                  case Some(nv) => (n, nv)
                  case None     => (n, v)
                }
              } else (n, v)
            }
            if (found) Some(Record(newFields)) else None
          case _ => None
        }

      case DynamicOptic.Node.Case(name) =>
        dv match {
          case v: Variant if v.caseNameValue == name =>
            modifyAtPathImpl(v.value, nodes, idx + 1, f).map(nv => Variant(v.caseNameValue, nv))
          case _ => None
        }

      case DynamicOptic.Node.AtIndex(i) =>
        dv match {
          case s: Sequence if i >= 0 && i < s.elements.length =>
            modifyAtPathImpl(s.elements(i), nodes, idx + 1, f).map { nv =>
              Sequence(s.elements.updated(i, nv))
            }
          case _ => None
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        dv match {
          case m: Map =>
            var found      = false
            val newEntries = m.entries.map { case (k, v) =>
              if (k == key) {
                found = true
                modifyAtPathImpl(v, nodes, idx + 1, f) match {
                  case Some(nv) => (k, nv)
                  case None     => (k, v)
                }
              } else (k, v)
            }
            if (found) Some(Map(newEntries)) else None
          case _ => None
        }

      case DynamicOptic.Node.AtIndices(indices) =>
        dv match {
          case s: Sequence =>
            val indicesSet = indices.toSet
            var found      = false
            val newElems   = s.elements.zipWithIndex.map { case (e, i) =>
              if (indicesSet.contains(i)) {
                modifyAtPathImpl(e, nodes, idx + 1, f) match {
                  case Some(nv) =>
                    found = true
                    nv
                  case None => e
                }
              } else e
            }
            if (found) Some(Sequence(newElems)) else None
          case _ => None
        }

      case DynamicOptic.Node.AtMapKeys(keys) =>
        dv match {
          case m: Map =>
            val keysSet    = keys.toSet
            var found      = false
            val newEntries = m.entries.map { case (k, v) =>
              if (keysSet.contains(k)) {
                modifyAtPathImpl(v, nodes, idx + 1, f) match {
                  case Some(nv) =>
                    found = true
                    (k, nv)
                  case None => (k, v)
                }
              } else (k, v)
            }
            if (found) Some(Map(newEntries)) else None
          case _ => None
        }

      case DynamicOptic.Node.Elements =>
        dv match {
          case s: Sequence =>
            var found    = false
            val newElems = s.elements.map { e =>
              modifyAtPathImpl(e, nodes, idx + 1, f) match {
                case Some(nv) =>
                  found = true
                  nv
                case None => e
              }
            }
            if (found) Some(Sequence(newElems)) else None
          case _ => None
        }

      case DynamicOptic.Node.MapKeys =>
        dv match {
          case m: Map =>
            var found      = false
            val newEntries = m.entries.map { case (k, v) =>
              modifyAtPathImpl(k, nodes, idx + 1, f) match {
                case Some(nk) =>
                  found = true
                  (nk, v)
                case None => (k, v)
              }
            }
            if (found) Some(Map(newEntries)) else None
          case _ => None
        }

      case DynamicOptic.Node.MapValues =>
        dv match {
          case m: Map =>
            var found      = false
            val newEntries = m.entries.map { case (k, v) =>
              modifyAtPathImpl(v, nodes, idx + 1, f) match {
                case Some(nv) =>
                  found = true
                  (k, nv)
                case None => (k, v)
              }
            }
            if (found) Some(Map(newEntries)) else None
          case _ => None
        }

      case DynamicOptic.Node.Wrapped =>
        modifyAtPathImpl(dv, nodes, idx + 1, f)
    }
  }

  private[schema] def modifyAtPathOrFail(
    dv: DynamicValue,
    path: DynamicOptic,
    pf: PartialFunction[DynamicValue, DynamicValue]
  ): Either[SchemaError, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) {
      if (pf.isDefinedAt(dv)) Right(pf(dv))
      else Left(SchemaError.message("Partial function not defined at root", path))
    } else {
      modifyAtPath(dv, path, v => if (pf.isDefinedAt(v)) pf(v) else v) match {
        case Some(result) => Right(result)
        case None         => Left(SchemaError.message("Path not found", path))
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Delete Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def deleteAtPath(dv: DynamicValue, path: DynamicOptic): Option[DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return None

    deleteAtPathImpl(dv, nodes, 0)
  }

  private def deleteAtPathImpl(
    dv: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int
  ): Option[DynamicValue] = {
    if (idx >= nodes.length) return None

    val isLast = idx == nodes.length - 1

    nodes(idx) match {
      case DynamicOptic.Node.Field(name) =>
        dv match {
          case r: Record =>
            if (isLast) {
              val newFields = r.fields.filterNot(_._1 == name)
              if (newFields.length < r.fields.length) Some(Record(newFields)) else None
            } else {
              var found     = false
              val newFields = r.fields.map { case (n, v) =>
                if (n == name) {
                  deleteAtPathImpl(v, nodes, idx + 1) match {
                    case Some(nv) =>
                      found = true
                      (n, nv)
                    case None => (n, v)
                  }
                } else (n, v)
              }
              if (found) Some(Record(newFields)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.Case(name) =>
        dv match {
          case v: Variant if v.caseNameValue == name =>
            if (isLast) Some(Record.empty)
            else deleteAtPathImpl(v.value, nodes, idx + 1).map(nv => Variant(v.caseNameValue, nv))
          case _ => None
        }

      case DynamicOptic.Node.AtIndex(i) =>
        dv match {
          case s: Sequence if i >= 0 && i < s.elements.length =>
            if (isLast) {
              val newElems = s.elements.patch(i, Chunk.empty, 1)
              Some(Sequence(newElems))
            } else {
              deleteAtPathImpl(s.elements(i), nodes, idx + 1).map { nv =>
                Sequence(s.elements.updated(i, nv))
              }
            }
          case _ => None
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        dv match {
          case m: Map =>
            if (isLast) {
              val newEntries = m.entries.filterNot(_._1 == key)
              if (newEntries.length < m.entries.length) Some(Map(newEntries)) else None
            } else {
              var found      = false
              val newEntries = m.entries.map { case (k, v) =>
                if (k == key) {
                  deleteAtPathImpl(v, nodes, idx + 1) match {
                    case Some(nv) =>
                      found = true
                      (k, nv)
                    case None => (k, v)
                  }
                } else (k, v)
              }
              if (found) Some(Map(newEntries)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.AtIndices(indices) =>
        dv match {
          case s: Sequence =>
            if (isLast) {
              val indicesSet = indices.toSet
              val newElems   = s.elements.zipWithIndex.filterNot { case (_, i) => indicesSet.contains(i) }.map(_._1)
              if (newElems.length < s.elements.length) Some(Sequence(newElems)) else None
            } else {
              val indicesSet = indices.toSet
              var found      = false
              val newElems   = s.elements.zipWithIndex.map { case (e, i) =>
                if (indicesSet.contains(i)) {
                  deleteAtPathImpl(e, nodes, idx + 1) match {
                    case Some(nv) =>
                      found = true
                      nv
                    case None => e
                  }
                } else e
              }
              if (found) Some(Sequence(newElems)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.AtMapKeys(keys) =>
        dv match {
          case m: Map =>
            if (isLast) {
              val keysSet    = keys.toSet
              val newEntries = m.entries.filterNot { case (k, _) => keysSet.contains(k) }
              if (newEntries.length < m.entries.length) Some(Map(newEntries)) else None
            } else {
              val keysSet    = keys.toSet
              var found      = false
              val newEntries = m.entries.map { case (k, v) =>
                if (keysSet.contains(k)) {
                  deleteAtPathImpl(v, nodes, idx + 1) match {
                    case Some(nv) =>
                      found = true
                      (k, nv)
                    case None => (k, v)
                  }
                } else (k, v)
              }
              if (found) Some(Map(newEntries)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.Elements =>
        dv match {
          case s: Sequence =>
            if (isLast) Some(Sequence.empty)
            else {
              var found    = false
              val newElems = s.elements.flatMap { e =>
                deleteAtPathImpl(e, nodes, idx + 1) match {
                  case Some(nv) =>
                    found = true
                    Chunk(nv)
                  case None => Chunk(e)
                }
              }
              if (found) Some(Sequence(newElems)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.MapKeys =>
        dv match {
          case m: Map =>
            if (isLast) Some(Map.empty)
            else {
              var found      = false
              val newEntries = m.entries.flatMap { case (k, v) =>
                deleteAtPathImpl(k, nodes, idx + 1) match {
                  case Some(nk) =>
                    found = true
                    Chunk((nk, v))
                  case None => Chunk((k, v))
                }
              }
              if (found) Some(Map(newEntries)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.MapValues =>
        dv match {
          case m: Map =>
            if (isLast) Some(Map.empty)
            else {
              var found      = false
              val newEntries = m.entries.flatMap { case (k, v) =>
                deleteAtPathImpl(v, nodes, idx + 1) match {
                  case Some(nv) =>
                    found = true
                    Chunk((k, nv))
                  case None => Chunk((k, v))
                }
              }
              if (found) Some(Map(newEntries)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.Wrapped =>
        deleteAtPathImpl(dv, nodes, idx + 1)
    }
  }

  private[schema] def deleteAtPathOrFail(
    dv: DynamicValue,
    path: DynamicOptic
  ): Either[SchemaError, DynamicValue] =
    deleteAtPath(dv, path).toRight(SchemaError.message("Path not found", path))

  // ─────────────────────────────────────────────────────────────────────────
  // Insert Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def insertAtPath(dv: DynamicValue, path: DynamicOptic, value: DynamicValue): Option[DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return None

    insertAtPathImpl(dv, nodes, 0, value)
  }

  private def insertAtPathImpl(
    dv: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: DynamicValue
  ): Option[DynamicValue] = {
    if (idx >= nodes.length) return None

    val isLast = idx == nodes.length - 1

    nodes(idx) match {
      case DynamicOptic.Node.Field(name) =>
        dv match {
          case r: Record =>
            if (isLast) {
              if (r.fields.exists(_._1 == name)) None
              else Some(Record(r.fields :+ (name, value)))
            } else {
              var found     = false
              val newFields = r.fields.map { case (n, v) =>
                if (n == name) {
                  insertAtPathImpl(v, nodes, idx + 1, value) match {
                    case Some(nv) =>
                      found = true
                      (n, nv)
                    case None => (n, v)
                  }
                } else (n, v)
              }
              if (found) Some(Record(newFields)) else None
            }
          case _ => None
        }

      case DynamicOptic.Node.Case(name) =>
        dv match {
          case v: Variant if v.caseNameValue == name =>
            if (isLast) None
            else insertAtPathImpl(v.value, nodes, idx + 1, value).map(nv => Variant(v.caseNameValue, nv))
          case _ => None
        }

      case DynamicOptic.Node.AtIndex(i) =>
        dv match {
          case s: Sequence =>
            if (isLast) {
              if (i >= 0 && i <= s.elements.length) {
                val (before, after) = s.elements.splitAt(i)
                Some(Sequence(before ++ Chunk(value) ++ after))
              } else None
            } else if (i >= 0 && i < s.elements.length) {
              insertAtPathImpl(s.elements(i), nodes, idx + 1, value).map { nv =>
                Sequence(s.elements.updated(i, nv))
              }
            } else None
          case _ => None
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        dv match {
          case m: Map =>
            if (isLast) {
              if (m.entries.exists(_._1 == key)) None
              else Some(Map(m.entries :+ (key, value)))
            } else {
              var found      = false
              val newEntries = m.entries.map { case (k, v) =>
                if (k == key) {
                  insertAtPathImpl(v, nodes, idx + 1, value) match {
                    case Some(nv) =>
                      found = true
                      (k, nv)
                    case None => (k, v)
                  }
                } else (k, v)
              }
              if (found) Some(Map(newEntries)) else None
            }
          case _ => None
        }

      case _ => None
    }
  }

  private[schema] def insertAtPathOrFail(
    dv: DynamicValue,
    path: DynamicOptic,
    value: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    insertAtPath(dv, path, value).toRight(
      SchemaError.message("Cannot insert at path (already exists or parent not found)", path)
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Merge Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def merge(
    left: DynamicValue,
    right: DynamicValue,
    strategy: DynamicValueMergeStrategy
  ): DynamicValue =
    mergeImpl(DynamicOptic.root, left, right, strategy)

  private def mergeImpl(
    path: DynamicOptic,
    left: DynamicValue,
    right: DynamicValue,
    s: DynamicValueMergeStrategy
  ): DynamicValue =
    (left, right) match {
      case (lr: Record, rr: Record) if s.recurse(path, DynamicValueType.Record) =>
        mergeRecords(path, lr, rr, s)
      case (ls: Sequence, rs: Sequence) if s.recurse(path, DynamicValueType.Sequence) =>
        mergeSequences(path, ls, rs, s)
      case (lm: Map, rm: Map) if s.recurse(path, DynamicValueType.Map) =>
        mergeMaps(path, lm, rm, s)
      case (lv: Variant, rv: Variant) if s.recurse(path, DynamicValueType.Variant) =>
        if (lv.caseNameValue == rv.caseNameValue)
          Variant(lv.caseNameValue, mergeImpl(path.caseOf(lv.caseNameValue), lv.value, rv.value, s))
        else s(path, left, right)
      case _ =>
        s(path, left, right)
    }

  private def mergeRecords(
    path: DynamicOptic,
    left: Record,
    right: Record,
    s: DynamicValueMergeStrategy
  ): Record = {
    val leftMap  = left.fields.toMap
    val rightMap = right.fields.toMap
    val allKeys  = (left.fields.map(_._1) ++ right.fields.map(_._1)).distinct
    Record(Chunk.from(allKeys.map { key =>
      val childPath = path.field(key)
      (leftMap.get(key), rightMap.get(key)) match {
        case (Some(lv), Some(rv)) => (key, mergeImpl(childPath, lv, rv, s))
        case (Some(lv), None)     => (key, lv)
        case (None, Some(rv))     => (key, rv)
        case (None, None)         => throw new IllegalStateException("Key should exist in at least one map")
      }
    }))
  }

  private def mergeSequences(
    path: DynamicOptic,
    left: Sequence,
    right: Sequence,
    s: DynamicValueMergeStrategy
  ): Sequence = {
    val maxLen = Math.max(left.elements.length, right.elements.length)
    Sequence(Chunk.from((0 until maxLen).map { i =>
      val childPath = path.at(i)
      (left.elements.lift(i), right.elements.lift(i)) match {
        case (Some(lv), Some(rv)) => mergeImpl(childPath, lv, rv, s)
        case (Some(lv), None)     => lv
        case (None, Some(rv))     => rv
        case (None, None)         => throw new IllegalStateException("Index should exist in at least one sequence")
      }
    }))
  }

  private def mergeMaps(
    path: DynamicOptic,
    left: Map,
    right: Map,
    s: DynamicValueMergeStrategy
  ): Map = {
    val leftMap  = left.entries.toMap
    val rightMap = right.entries.toMap
    val allKeys  = (left.entries.map(_._1) ++ right.entries.map(_._1)).distinct
    Map(Chunk.from(allKeys.map { key =>
      val childPath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(key))
      (leftMap.get(key), rightMap.get(key)) match {
        case (Some(lv), Some(rv)) => (key, mergeImpl(childPath, lv, rv, s))
        case (Some(lv), None)     => (key, lv)
        case (None, Some(rv))     => (key, rv)
        case (None, None)         => throw new IllegalStateException("Key should exist in at least one map")
      }
    }))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def sortFieldsImpl(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => Record(r.fields.sortBy(_._1).map { case (k, v) => (k, sortFieldsImpl(v)) })
    case s: Sequence => Sequence(s.elements.map(sortFieldsImpl))
    case m: Map      => Map(m.entries.map { case (k, v) => (sortFieldsImpl(k), sortFieldsImpl(v)) })
    case v: Variant  => Variant(v.caseNameValue, sortFieldsImpl(v.value))
    case other       => other
  }

  private[schema] def sortMapKeysImpl(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => Record(r.fields.map { case (k, v) => (k, sortMapKeysImpl(v)) })
    case s: Sequence => Sequence(s.elements.map(sortMapKeysImpl))
    case m: Map      => Map(m.entries.sortBy(_._1).map { case (k, v) => (sortMapKeysImpl(k), sortMapKeysImpl(v)) })
    case v: Variant  => Variant(v.caseNameValue, sortMapKeysImpl(v.value))
    case other       => other
  }

  private[schema] def dropNullsImpl(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => Record(r.fields.filterNot(_._2 == Null).map { case (k, v) => (k, dropNullsImpl(v)) })
    case s: Sequence => Sequence(s.elements.filterNot(_ == Null).map(dropNullsImpl))
    case m: Map      =>
      Map(m.entries.filterNot { case (k, v) => k == Null || v == Null }.map { case (k, v) =>
        (dropNullsImpl(k), dropNullsImpl(v))
      })
    case v: Variant if v.value != Null => Variant(v.caseNameValue, dropNullsImpl(v.value))
    case other                         => other
  }

  private[schema] def dropUnitsImpl(dv: DynamicValue): DynamicValue = {
    val isUnit: DynamicValue => Boolean = {
      case Primitive(PrimitiveValue.Unit) => true
      case _                              => false
    }
    dv match {
      case r: Record   => Record(r.fields.filterNot(kv => isUnit(kv._2)).map { case (k, v) => (k, dropUnitsImpl(v)) })
      case s: Sequence => Sequence(s.elements.filterNot(isUnit).map(dropUnitsImpl))
      case m: Map      =>
        Map(m.entries.filterNot { case (k, v) => isUnit(k) || isUnit(v) }.map { case (k, v) =>
          (dropUnitsImpl(k), dropUnitsImpl(v))
        })
      case v: Variant if !isUnit(v.value) => Variant(v.caseNameValue, dropUnitsImpl(v.value))
      case other                          => other
    }
  }

  private[schema] def dropEmptyImpl(dv: DynamicValue): DynamicValue = {
    val isEmpty: DynamicValue => Boolean = {
      case r: Record   => r.fields.isEmpty
      case s: Sequence => s.elements.isEmpty
      case m: Map      => m.entries.isEmpty
      case _           => false
    }
    dv match {
      case r: Record   => Record(r.fields.filterNot(kv => isEmpty(kv._2)).map { case (k, v) => (k, dropEmptyImpl(v)) })
      case s: Sequence => Sequence(s.elements.filterNot(isEmpty).map(dropEmptyImpl))
      case m: Map      =>
        Map(m.entries.filterNot { case (k, v) => isEmpty(k) || isEmpty(v) }.map { case (k, v) =>
          (dropEmptyImpl(k), dropEmptyImpl(v))
        })
      case v: Variant if !isEmpty(v.value) => Variant(v.caseNameValue, dropEmptyImpl(v.value))
      case other                           => other
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Transformation Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def transformUpImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, DynamicValue) => DynamicValue
  ): DynamicValue = {
    val transformed = dv match {
      case r: Record =>
        Record(r.fields.map { case (k, v) =>
          val childPath = path.field(k)
          (k, transformUpImpl(v, childPath, f))
        })
      case s: Sequence =>
        Sequence(s.elements.zipWithIndex.map { case (e, i) =>
          val childPath = path.at(i)
          transformUpImpl(e, childPath, f)
        })
      case m: Map =>
        Map(m.entries.map { case (k, v) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
          (transformUpImpl(k, keyPath, f), transformUpImpl(v, valuePath, f))
        })
      case v: Variant =>
        val childPath = path.caseOf(v.caseNameValue)
        Variant(v.caseNameValue, transformUpImpl(v.value, childPath, f))
      case other => other
    }
    f(path, transformed)
  }

  private[schema] def transformDownImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, DynamicValue) => DynamicValue
  ): DynamicValue = {
    val transformed = f(path, dv)
    transformed match {
      case r: Record =>
        Record(r.fields.map { case (k, v) =>
          val childPath = path.field(k)
          (k, transformDownImpl(v, childPath, f))
        })
      case s: Sequence =>
        Sequence(s.elements.zipWithIndex.map { case (e, i) =>
          val childPath = path.at(i)
          transformDownImpl(e, childPath, f)
        })
      case m: Map =>
        Map(m.entries.map { case (k, v) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
          (transformDownImpl(k, keyPath, f), transformDownImpl(v, valuePath, f))
        })
      case v: Variant =>
        val childPath = path.caseOf(v.caseNameValue)
        Variant(v.caseNameValue, transformDownImpl(v.value, childPath, f))
      case other => other
    }
  }

  private[schema] def transformFieldsImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, String) => String
  ): DynamicValue = dv match {
    case r: Record =>
      Record(r.fields.map { case (k, v) =>
        val newKey    = f(path, k)
        val childPath = path.field(newKey)
        (newKey, transformFieldsImpl(v, childPath, f))
      })
    case s: Sequence =>
      Sequence(s.elements.zipWithIndex.map { case (e, i) =>
        transformFieldsImpl(e, path.at(i), f)
      })
    case m: Map =>
      Map(m.entries.map { case (k, v) =>
        (transformFieldsImpl(k, path, f), transformFieldsImpl(v, path, f))
      })
    case v: Variant =>
      Variant(v.caseNameValue, transformFieldsImpl(v.value, path.caseOf(v.caseNameValue), f))
    case other => other
  }

  private[schema] def transformMapKeysImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, DynamicValue) => DynamicValue
  ): DynamicValue = dv match {
    case r: Record =>
      Record(r.fields.map { case (k, v) =>
        (k, transformMapKeysImpl(v, path.field(k), f))
      })
    case s: Sequence =>
      Sequence(s.elements.zipWithIndex.map { case (e, i) =>
        transformMapKeysImpl(e, path.at(i), f)
      })
    case m: Map =>
      Map(m.entries.map { case (k, v) =>
        val keyPath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
        (f(keyPath, k), transformMapKeysImpl(v, path, f))
      })
    case v: Variant =>
      Variant(v.caseNameValue, transformMapKeysImpl(v.value, path.caseOf(v.caseNameValue), f))
    case other => other
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Prune/Retain/Partition Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def pruneImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): DynamicValue = dv match {
    case r: Record =>
      Record(r.fields.flatMap { case (k, v) =>
        val childPath = path.field(k)
        if (p(childPath, v)) None
        else Some((k, pruneImpl(v, childPath, p)))
      })
    case s: Sequence =>
      Sequence(s.elements.zipWithIndex.flatMap { case (e, i) =>
        val childPath = path.at(i)
        if (p(childPath, e)) None
        else Some(pruneImpl(e, childPath, p))
      })
    case m: Map =>
      Map(m.entries.flatMap { case (k, v) =>
        val childPath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
        if (p(childPath, v)) None
        else Some((pruneImpl(k, path, p), pruneImpl(v, childPath, p)))
      })
    case v: Variant =>
      val childPath = path.caseOf(v.caseNameValue)
      Variant(v.caseNameValue, pruneImpl(v.value, childPath, p))
    case other => other
  }

  private[schema] def retainImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): DynamicValue = {
    def retainRec(current: DynamicValue, currentPath: DynamicOptic): DynamicValue = current match {
      case r: Record =>
        Record(r.fields.flatMap { case (k, v) =>
          val childPath = currentPath.field(k)
          val retained  = retainRec(v, childPath)
          if (p(childPath, v) || hasContent(retained)) Some((k, retained))
          else None
        })
      case s: Sequence =>
        Sequence(s.elements.zipWithIndex.flatMap { case (e, i) =>
          val childPath = currentPath.at(i)
          val retained  = retainRec(e, childPath)
          if (p(childPath, e) || hasContent(retained)) Some(retained)
          else None
        })
      case m: Map =>
        Map(m.entries.flatMap { case (k, v) =>
          val childPath = new DynamicOptic(currentPath.nodes :+ DynamicOptic.Node.AtMapKey(k))
          val retained  = retainRec(v, childPath)
          if (p(childPath, v) || hasContent(retained)) Some((k, retained))
          else None
        })
      case v: Variant =>
        val childPath = currentPath.caseOf(v.caseNameValue)
        val retained  = retainRec(v.value, childPath)
        if (p(childPath, v.value) || hasContent(retained)) Variant(v.caseNameValue, retained)
        else current
      case other =>
        if (p(currentPath, other)) other
        else Null
    }

    retainRec(dv, path)
  }

  private def hasContent(dv: DynamicValue): Boolean = dv match {
    case r: Record   => r.fields.nonEmpty
    case s: Sequence => s.elements.nonEmpty
    case m: Map      => m.entries.nonEmpty
    case Null        => false
    case _           => true
  }

  private[schema] def projectImpl(dv: DynamicValue, paths: Seq[DynamicOptic]): DynamicValue = {
    val pathSet = paths.toSet
    retainImpl(
      dv,
      DynamicOptic.root,
      (path, _) => pathSet.exists(p => path.nodes.startsWith(p.nodes) || p.nodes.startsWith(path.nodes))
    )
  }

  private[schema] def partitionImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): (DynamicValue, DynamicValue) =
    (retainImpl(dv, path, p), pruneImpl(dv, path, p))

  // ─────────────────────────────────────────────────────────────────────────
  // Fold Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def foldUpImpl[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => B
  ): B = {
    val childAcc = dv match {
      case r: Record =>
        r.fields.foldLeft(z) { case (acc, (k, v)) =>
          foldUpImpl(v, path.field(k), acc, f)
        }
      case s: Sequence =>
        s.elements.zipWithIndex.foldLeft(z) { case (acc, (e, i)) =>
          foldUpImpl(e, path.at(i), acc, f)
        }
      case m: Map =>
        m.entries.foldLeft(z) { case (acc, (k, v)) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
          val acc1      = foldUpImpl(k, keyPath, acc, f)
          foldUpImpl(v, valuePath, acc1, f)
        }
      case v: Variant =>
        foldUpImpl(v.value, path.caseOf(v.caseNameValue), z, f)
      case _ => z
    }
    f(path, dv, childAcc)
  }

  private[schema] def foldDownImpl[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => B
  ): B = {
    val acc = f(path, dv, z)
    dv match {
      case r: Record =>
        r.fields.foldLeft(acc) { case (a, (k, v)) =>
          foldDownImpl(v, path.field(k), a, f)
        }
      case s: Sequence =>
        s.elements.zipWithIndex.foldLeft(acc) { case (a, (e, i)) =>
          foldDownImpl(e, path.at(i), a, f)
        }
      case m: Map =>
        m.entries.foldLeft(acc) { case (a, (k, v)) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
          val a1        = foldDownImpl(k, keyPath, a, f)
          foldDownImpl(v, valuePath, a1, f)
        }
      case v: Variant =>
        foldDownImpl(v.value, path.caseOf(v.caseNameValue), acc, f)
      case _ => acc
    }
  }

  private[schema] def foldUpOrFailImpl[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] = {
    val childResult = dv match {
      case r: Record =>
        r.fields.foldLeft[Either[SchemaError, B]](Right(z)) { case (accE, (k, v)) =>
          accE.flatMap(acc => foldUpOrFailImpl(v, path.field(k), acc, f))
        }
      case s: Sequence =>
        s.elements.zipWithIndex.foldLeft[Either[SchemaError, B]](Right(z)) { case (accE, (e, i)) =>
          accE.flatMap(acc => foldUpOrFailImpl(e, path.at(i), acc, f))
        }
      case m: Map =>
        m.entries.foldLeft[Either[SchemaError, B]](Right(z)) { case (accE, (k, v)) =>
          accE.flatMap { acc =>
            val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
            val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
            foldUpOrFailImpl(k, keyPath, acc, f).flatMap(a1 => foldUpOrFailImpl(v, valuePath, a1, f))
          }
        }
      case v: Variant =>
        foldUpOrFailImpl(v.value, path.caseOf(v.caseNameValue), z, f)
      case _ => Right(z)
    }
    childResult.flatMap(childAcc => f(path, dv, childAcc))
  }

  private[schema] def foldDownOrFailImpl[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] =
    f(path, dv, z).flatMap { acc =>
      dv match {
        case r: Record =>
          r.fields.foldLeft[Either[SchemaError, B]](Right(acc)) { case (aE, (k, v)) =>
            aE.flatMap(a => foldDownOrFailImpl(v, path.field(k), a, f))
          }
        case s: Sequence =>
          s.elements.zipWithIndex.foldLeft[Either[SchemaError, B]](Right(acc)) { case (aE, (e, i)) =>
            aE.flatMap(a => foldDownOrFailImpl(e, path.at(i), a, f))
          }
        case m: Map =>
          m.entries.foldLeft[Either[SchemaError, B]](Right(acc)) { case (aE, (k, v)) =>
            aE.flatMap { a =>
              val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
              val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
              foldDownOrFailImpl(k, keyPath, a, f).flatMap(a1 => foldDownOrFailImpl(v, valuePath, a1, f))
            }
          }
        case v: Variant =>
          foldDownOrFailImpl(v.value, path.caseOf(v.caseNameValue), acc, f)
        case _ => Right(acc)
      }
    }

  private[schema] def toKVImpl(dv: DynamicValue, path: DynamicOptic): Chunk[(DynamicOptic, DynamicValue)] = dv match {
    case r: Record =>
      r.fields.flatMap { case (k, v) =>
        toKVImpl(v, path.field(k))
      }
    case s: Sequence =>
      s.elements.zipWithIndex.flatMap { case (e, i) =>
        toKVImpl(e, path.at(i))
      }
    case m: Map =>
      m.entries.flatMap { case (k, v) =>
        val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
        toKVImpl(v, valuePath)
      }
    case v: Variant =>
      toKVImpl(v.value, path.caseOf(v.caseNameValue))
    case other =>
      Chunk((path, other))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // KV Reconstruction
  // ─────────────────────────────────────────────────────────────────────────

  def fromKV(kvs: Seq[(DynamicOptic, DynamicValue)]): Either[SchemaError, DynamicValue] =
    if (kvs.isEmpty) Right(Record.empty)
    else {
      try {
        Right(fromKVUnsafe(kvs))
      } catch {
        case e: SchemaError => Left(e)
      }
    }

  def fromKVUnsafe(kvs: Seq[(DynamicOptic, DynamicValue)]): DynamicValue = {
    if (kvs.isEmpty) return Record.empty

    var result: DynamicValue = Record.empty
    kvs.foreach { case (path, value) =>
      result = upsertAtPathCreatingParents(result, path, value)
    }
    result
  }

  private def upsertAtPathCreatingParents(
    dv: DynamicValue,
    path: DynamicOptic,
    value: DynamicValue
  ): DynamicValue = {
    val nodes = path.nodes
    if (nodes.isEmpty) return value

    def createContainer(nextNode: DynamicOptic.Node): DynamicValue = nextNode match {
      case _: DynamicOptic.Node.Field    => Record.empty
      case _: DynamicOptic.Node.AtIndex  => Sequence.empty
      case _: DynamicOptic.Node.AtMapKey => Map.empty
      case _: DynamicOptic.Node.Case     => Record.empty
      case _                             => Record.empty
    }

    def go(current: DynamicValue, idx: Int): DynamicValue = {
      if (idx >= nodes.length) return value

      val node   = nodes(idx)
      val isLast = idx == nodes.length - 1

      node match {
        case DynamicOptic.Node.Field(name) =>
          current match {
            case r: Record =>
              val fieldIdx = r.fields.indexWhere(_._1 == name)
              if (fieldIdx >= 0) {
                val (_, v) = r.fields(fieldIdx)
                val newV   = if (isLast) value else go(v, idx + 1)
                Record(r.fields.updated(fieldIdx, (name, newV)))
              } else {
                val newV = if (isLast) value else go(createContainer(nodes(idx + 1)), idx + 1)
                Record(r.fields :+ (name, newV))
              }
            case _ =>
              val newV = if (isLast) value else go(createContainer(nodes(idx + 1)), idx + 1)
              Record(Chunk((name, newV)))
          }

        case DynamicOptic.Node.AtIndex(index) =>
          current match {
            case s: Sequence =>
              if (index >= 0 && index < s.elements.length) {
                val newV = if (isLast) value else go(s.elements(index), idx + 1)
                Sequence(s.elements.updated(index, newV))
              } else if (index == s.elements.length) {
                val newV = if (isLast) value else go(createContainer(nodes(idx + 1)), idx + 1)
                Sequence(s.elements :+ newV)
              } else {
                val padding = Chunk.fill(index - s.elements.length)(Null: DynamicValue)
                val newV    = if (isLast) value else go(createContainer(nodes(idx + 1)), idx + 1)
                Sequence(s.elements ++ padding :+ newV)
              }
            case _ =>
              val padding = Chunk.fill(index)(Null: DynamicValue)
              val newV    = if (isLast) value else go(createContainer(nodes(idx + 1)), idx + 1)
              Sequence(padding :+ newV)
          }

        case DynamicOptic.Node.AtMapKey(key) =>
          current match {
            case m: Map =>
              val keyIdx = m.entries.indexWhere(_._1 == key)
              if (keyIdx >= 0) {
                val (k, v) = m.entries(keyIdx)
                val newV   = if (isLast) value else go(v, idx + 1)
                Map(m.entries.updated(keyIdx, (k, newV)))
              } else {
                val newV = if (isLast) value else go(createContainer(nodes(idx + 1)), idx + 1)
                Map(m.entries :+ (key, newV))
              }
            case _ =>
              val newV = if (isLast) value else go(createContainer(nodes(idx + 1)), idx + 1)
              Map(Chunk((key, newV)))
          }

        case DynamicOptic.Node.Case(caseName) =>
          current match {
            case v: Variant if v.caseNameValue == caseName =>
              val newInner = if (isLast) value else go(v.value, idx + 1)
              Variant(caseName, newInner)
            case _ =>
              val newInner = if (isLast) value else go(Record.empty, idx + 1)
              Variant(caseName, newInner)
          }

        case _ =>
          go(current, idx + 1)
      }
    }

    go(dv, 0)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Query Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def queryImpl(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): DynamicValueSelection = {
    val results = queryCollect(dv, path, p)
    DynamicValueSelection.succeedMany(results)
  }

  private def queryCollect(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): Chunk[DynamicValue] = {
    val current = if (p(path, dv)) Chunk(dv) else Chunk.empty

    val children = dv match {
      case r: Record =>
        r.fields.flatMap { case (k, v) =>
          queryCollect(v, path.field(k), p)
        }
      case s: Sequence =>
        s.elements.zipWithIndex.flatMap { case (e, i) =>
          queryCollect(e, path.at(i), p)
        }
      case m: Map =>
        m.entries.flatMap { case (k, v) =>
          val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
          queryCollect(v, valuePath, p)
        }
      case v: Variant =>
        queryCollect(v.value, path.caseOf(v.caseNameValue), p)
      case _ => Chunk.empty
    }

    current ++ children
  }

  /**
   * Convert a DynamicValue to EJSON (Extended JSON) format.
   *
   * EJSON is a superset of JSON that handles:
   *   - Non-string map keys
   *   - Tagged variants (using @ metadata)
   *   - Typed primitives (using @ metadata)
   *   - Records (unquoted keys) vs Maps (quoted string keys or unquoted
   *     non-string keys)
   *
   * @param value
   *   The DynamicValue to convert
   * @param indent
   *   Current indentation level
   * @return
   *   EJSON string representation
   */
  private def toEjson(value: DynamicValue, indent: Int): String = {
    val indentStr = "  " * indent

    value match {
      case Primitive(pv) =>
        primitiveToEjson(pv)

      case Record(fields) =>
        if (fields.isEmpty) {
          "{}"
        } else {
          val sb = new StringBuilder
          sb.append("{\n")
          fields.zipWithIndex.foreach { case ((name, value), idx) =>
            sb.append(indentStr).append("  ").append(escapeFieldName(name)).append(": ")
            sb.append(toEjson(value, indent + 1))
            if (idx < fields.length - 1) sb.append(",")
            sb.append("\n")
          }
          sb.append(indentStr).append("}")
          sb.toString
        }

      case Variant(caseName, value) =>
        // Variants use postfix @ metadata: { ... } @ {tag: "CaseName"}
        val valueEjson = toEjson(value, indent)
        s"$valueEjson @ {tag: ${quote(caseName)}}"

      case Sequence(elements) =>
        if (elements.isEmpty) {
          "[]"
        } else if (elements.length == 1 && elements(0).isInstanceOf[Primitive]) {
          // Only inline single-element sequences if the element is a primitive
          "[" + toEjson(elements(0), indent) + "]"
        } else {
          val sb = new StringBuilder
          sb.append("[\n")
          elements.zipWithIndex.foreach { case (elem, idx) =>
            sb.append(indentStr).append("  ")
            sb.append(toEjson(elem, indent + 1))
            if (idx < elements.length - 1) sb.append(",")
            sb.append("\n")
          }
          sb.append(indentStr).append("]")
          sb.toString
        }

      case Map(entries) =>
        if (entries.isEmpty) {
          "{}"
        } else {
          val sb = new StringBuilder
          sb.append("{\n")
          entries.zipWithIndex.foreach { case ((key, value), idx) =>
            sb.append(indentStr).append("  ")
            // For string keys in maps, we quote them. For non-string keys, we don't quote them.
            key match {
              case Primitive(PrimitiveValue.String(str)) =>
                sb.append(quote(str))
              case _ =>
                sb.append(toEjson(key, indent + 1))
            }
            sb.append(": ")
            sb.append(toEjson(value, indent + 1))
            if (idx < entries.length - 1) sb.append(",")
            sb.append("\n")
          }
          sb.append(indentStr).append("}")
          sb.toString
        }

      case Null => "null"
    }
  }

  /**
   * Convert a PrimitiveValue to EJSON format. Most primitives render as their
   * JSON equivalent. Some primitives need @ metadata for type information.
   */
  private def primitiveToEjson(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.Unit                  => "null"
    case PrimitiveValue.Boolean(value)        => value.toString
    case PrimitiveValue.Byte(value)           => value.toString
    case PrimitiveValue.Short(value)          => value.toString
    case PrimitiveValue.Int(value)            => value.toString
    case PrimitiveValue.Long(value)           => value.toString
    case PrimitiveValue.Float(value)          => value.toString
    case PrimitiveValue.Double(value)         => value.toString
    case PrimitiveValue.Char(value)           => quote(value.toString)
    case PrimitiveValue.String(value)         => quote(value)
    case PrimitiveValue.BigInt(value)         => value.toString
    case PrimitiveValue.BigDecimal(value)     => value.toString
    case PrimitiveValue.DayOfWeek(value)      => quote(value.toString)
    case PrimitiveValue.Month(value)          => quote(value.toString)
    case PrimitiveValue.Instant(value)        => s"${value.toEpochMilli} @ {type: ${quote("instant")}}"
    case PrimitiveValue.LocalDate(value)      => s"${quote(value.toString)} @ {type: ${quote("localDate")}}"
    case PrimitiveValue.LocalDateTime(value)  => quote(value.toString)
    case PrimitiveValue.LocalTime(value)      => quote(value.toString)
    case PrimitiveValue.OffsetDateTime(value) => quote(value.toString)
    case PrimitiveValue.OffsetTime(value)     => quote(value.toString)
    case PrimitiveValue.Year(value)           => value.getValue.toString
    case PrimitiveValue.YearMonth(value)      => quote(value.toString)
    case PrimitiveValue.ZoneOffset(value)     => quote(value.toString)
    case PrimitiveValue.ZonedDateTime(value)  => quote(value.toString)
    case PrimitiveValue.MonthDay(value)       => quote(value.toString)
    case PrimitiveValue.Period(value)         => s"${quote(value.toString)} @ {type: ${quote("period")}}"
    case PrimitiveValue.Duration(value)       => s"${quote(value.toString)} @ {type: ${quote("duration")}}"
    case PrimitiveValue.ZoneId(value)         => quote(value.toString)
    case PrimitiveValue.Currency(value)       => quote(value.getCurrencyCode)
    case PrimitiveValue.UUID(value)           => quote(value.toString)
  }

  /**
   * Escape a field name for EJSON output. Valid Scala identifiers are left
   * as-is; invalid identifiers are wrapped in backticks, with backticks doubled
   * for escaping.
   */
  private def escapeFieldName(name: String): String =
    if (isValidIdentifier(name)) name
    else {
      val escaped = name.replace("`", "``")
      s"`$escaped`"
    }

  /**
   * Check if a string is a valid Scala identifier. A valid identifier:
   *   - Must start with a letter or underscore
   *   - Can contain letters, digits, or underscores
   *   - Cannot be a Scala keyword
   *   - Cannot contain $ (discouraged in user-written code)
   */
  private def isValidIdentifier(s: String): Boolean = {
    if (s.isEmpty) return false
    if (scalaKeywords.contains(s)) return false

    val first = s.charAt(0)
    if (!Character.isLetter(first) && first != '_') return false

    var i = 1
    while (i < s.length) {
      val c = s.charAt(i)
      if (!Character.isLetterOrDigit(c) && c != '_') return false
      i += 1
    }
    true
  }

  /**
   * Scala keywords that cannot be used as identifiers without backticks.
   */
  private val scalaKeywords: Set[String] = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "if",
    "implicit",
    "import",
    "lazy",
    "macro",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "this",
    "throw",
    "trait",
    "true",
    "try",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
    "_",
    ":",
    "=",
    "=>",
    "<-",
    "<:",
    "<%",
    ">:",
    "#",
    "@"
  )

  /**
   * Quote a string for JSON/EJSON output, escaping special characters.
   */
  private def quote(s: String): String = {
    val sb = new StringBuilder
    sb.append('"')
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c    =>
          if (c < ' ') {
            sb.append("\\u")
            sb.append(String.format("%04x", Integer.valueOf(c.toInt)))
          } else {
            sb.append(c)
          }
      }
      i += 1
    }
    sb.append('"')
    sb.toString
  }
}
