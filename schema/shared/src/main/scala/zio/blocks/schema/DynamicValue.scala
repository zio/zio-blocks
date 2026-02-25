package zio.blocks.schema

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.json.{Json, JsonBinaryCodec}
import zio.blocks.schema.patch.{Differ, DynamicPatch}

import java.util

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
  def is(t: DynamicValueType): Boolean = this.valueType eq t

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
    DynamicValue.modifyAtPath(this, path, f) match {
      case Some(dv) => dv
      case _        => this
    }

  /**
   * Modifies the value at the given path using a partial function. Returns Left
   * with an error if the path doesn't exist or the partial function is not
   * defined.
   */
  def modifyOrFail(path: DynamicOptic)(
    pf: PartialFunction[DynamicValue, DynamicValue]
  ): Either[SchemaError, DynamicValue] = DynamicValue.modifyAtPathOrFail(this, path, pf)

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
  def delete(path: DynamicOptic): DynamicValue =
    DynamicValue.deleteAtPath(this, path) match {
      case Some(dv) => dv
      case _        => this
    }

  /**
   * Deletes the value at the given path. Returns Left with an error if the path
   * doesn't exist.
   */
  def deleteOrFail(path: DynamicOptic): Either[SchemaError, DynamicValue] =
    DynamicValue.deleteAtPath(this, path) match {
      case Some(dv) => new Right(dv)
      case _        => new Left(SchemaError.message("Path not found", path))
    }

  /**
   * Inserts a value at the given path. If the path already exists, returns the
   * original DynamicValue unchanged.
   */
  def insert(path: DynamicOptic, value: DynamicValue): DynamicValue =
    DynamicValue.insertAtPath(this, path, value) match {
      case Some(dv) => dv
      case _        => this
    }

  /**
   * Inserts a value at the given path. Returns Left with an error if the path
   * already exists or the parent doesn't exist.
   */
  def insertOrFail(path: DynamicOptic, value: DynamicValue): Either[SchemaError, DynamicValue] =
    DynamicValue.insertAtPath(this, path, value) match {
      case Some(dv) => new Right(dv)
      case _        => new Left(SchemaError.message("Cannot insert at path (already exists or parent not found)", path))
    }

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
  def sortFields: DynamicValue = DynamicValue.sortFields(this)

  /** Recursively sorts all Map entries by key. */
  def sortMapKeys: DynamicValue = DynamicValue.sortMapKeys(this)

  /** Recursively removes all Null values from containers. */
  def dropNulls: DynamicValue = DynamicValue.dropNulls(this)

  /** Recursively removes all Primitive(Unit) values from containers. */
  def dropUnits: DynamicValue = DynamicValue.dropUnits(this)

  /** Recursively removes empty Records, Sequences, and Maps. */
  def dropEmpty: DynamicValue = DynamicValue.dropEmpty(this)

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
    DynamicValue.transformUp(this, DynamicOptic.root, f)

  /**
   * Transforms this DynamicValue top-down using the given function. The
   * function receives the current path and the DynamicValue at that path.
   * Parent values are transformed before their children.
   */
  def transformDown(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValue =
    DynamicValue.transformDown(this, DynamicOptic.root, f)

  /**
   * Transforms all Record field names using the given function. The function
   * receives the current path and the field name at that path.
   */
  def transformFields(f: (DynamicOptic, String) => String): DynamicValue =
    DynamicValue.transformFields(this, DynamicOptic.root, f)

  /**
   * Transforms all Map keys using the given function. The function receives the
   * current path and the key at that path.
   */
  def transformMapKeys(f: (DynamicOptic, DynamicValue) => DynamicValue): DynamicValue =
    DynamicValue.transformMapKeys(this, DynamicOptic.root, f)

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
    if (this.valueType eq t) DynamicValueSelection.succeed(this)
    else DynamicValueSelection.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Pruning/Retention/Projection/Partition
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Recursively removes elements/fields for which the predicate returns true.
   */
  def prune(p: DynamicValue => Boolean): DynamicValue =
    DynamicValue.prune(this, DynamicOptic.root, (_, dv) => p(dv))

  /**
   * Recursively removes elements/fields at paths for which the predicate
   * returns true.
   */
  def prunePath(p: DynamicOptic => Boolean): DynamicValue =
    DynamicValue.prune(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Recursively removes elements/fields for which the predicate on both path
   * and value returns true.
   */
  def pruneBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValue =
    DynamicValue.prune(this, DynamicOptic.root, p)

  /**
   * Recursively keeps only elements/fields for which the predicate returns
   * true.
   */
  def retain(p: DynamicValue => Boolean): DynamicValue =
    DynamicValue.retain(this, DynamicOptic.root, (_, dv) => p(dv))

  /**
   * Recursively keeps only elements/fields at paths for which the predicate
   * returns true.
   */
  def retainPath(p: DynamicOptic => Boolean): DynamicValue =
    DynamicValue.retain(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Recursively keeps only elements/fields for which the predicate on both path
   * and value returns true.
   */
  def retainBoth(p: (DynamicOptic, DynamicValue) => Boolean): DynamicValue =
    DynamicValue.retain(this, DynamicOptic.root, p)

  /**
   * Projects only the specified paths from this DynamicValue. Creates a new
   * DynamicValue containing only values at the given paths.
   */
  def project(paths: DynamicOptic*): DynamicValue = DynamicValue.project(this, paths)

  /**
   * Partitions elements/fields based on a predicate on the value. Returns a
   * tuple of (matching, non-matching) DynamicValues.
   */
  def partition(p: DynamicValue => Boolean): (DynamicValue, DynamicValue) =
    DynamicValue.partition(this, DynamicOptic.root, (_, dv) => p(dv))

  /**
   * Partitions elements/fields based on a predicate on the path. Returns a
   * tuple of (matching, non-matching) DynamicValues.
   */
  def partitionPath(p: DynamicOptic => Boolean): (DynamicValue, DynamicValue) =
    DynamicValue.partition(this, DynamicOptic.root, (path, _) => p(path))

  /**
   * Partitions elements/fields based on a predicate on both path and value.
   * Returns a tuple of (matching, non-matching) DynamicValues.
   */
  def partitionBoth(p: (DynamicOptic, DynamicValue) => Boolean): (DynamicValue, DynamicValue) =
    DynamicValue.partition(this, DynamicOptic.root, p)

  // ─────────────────────────────────────────────────────────────────────────
  // Folding
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Folds over the DynamicValue structure bottom-up. The function receives the
   * current path, the DynamicValue, and the accumulator. Child values are
   * folded before their parents.
   */
  def foldUp[B](z: B)(f: (DynamicOptic, DynamicValue, B) => B): B =
    DynamicValue.foldUp(this, DynamicOptic.root, z, f)

  /**
   * Folds over the DynamicValue structure top-down. The function receives the
   * current path, the DynamicValue, and the accumulator. Parent values are
   * folded before their children.
   */
  def foldDown[B](z: B)(f: (DynamicOptic, DynamicValue, B) => B): B =
    DynamicValue.foldDown(this, DynamicOptic.root, z, f)

  /**
   * Folds over the DynamicValue structure bottom-up, allowing failure.
   */
  def foldUpOrFail[B](z: B)(f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]): Either[SchemaError, B] =
    DynamicValue.foldUpOrFail(this, DynamicOptic.root, z, f)

  /**
   * Folds over the DynamicValue structure top-down, allowing failure.
   */
  def foldDownOrFail[B](z: B)(f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]): Either[SchemaError, B] =
    DynamicValue.foldDownOrFail(this, DynamicOptic.root, z, f)

  /**
   * Converts this DynamicValue to a Chunk of path-value pairs. Each pair
   * contains the path to a leaf value and the value itself.
   */
  def toKV: Chunk[(DynamicOptic, DynamicValue)] = DynamicValue.toKV(this, DynamicOptic.root)

  // ─────────────────────────────────────────────────────────────────────────
  // Conversion
  // ─────────────────────────────────────────────────────────────────────────

  /** Converts this DynamicValue to a Json value. */
  def toJson: Json = Json.fromDynamicValue(this)

  def toEjson(indent: Int = 0): String = {
    val sb = new java.lang.StringBuilder
    DynamicValue.toEjson(this, indent, sb)
    sb.toString
  }

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
      case p: Primitive => value == p.value
      case _            => false
    }

    override def hashCode: Int = value.hashCode

    def typeIndex: Int = 0

    def valueType: DynamicValueType = DynamicValueType.Primitive

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t eq DynamicValueType.Primitive) new Some(this.asInstanceOf[t.Type])
      else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t eq DynamicValueType.Primitive) new Some(value.asInstanceOf[t.Unwrap])
      else None

    override def primitiveValue: Option[PrimitiveValue] = new Some(value)

    def compare(that: DynamicValue): Int = that match {
      case thatPrimitive: Primitive => value.compare(thatPrimitive.value)
      case _                        => -that.typeIndex
    }
  }

  /**
   * A collection of named fields, analogous to a case class or JSON object.
   *
   * Field order is preserved and significant for equality comparison. Use
   * sortFields to normalize field ordering for order-independent comparison.
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
      if (t eq DynamicValueType.Record) new Some(this.asInstanceOf[t.Type])
      else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t eq DynamicValueType.Record) new Some(fields.asInstanceOf[t.Unwrap])
      else None

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
      case v: Variant => caseNameValue == v.caseNameValue && value == v.value
      case _          => false
    }

    override def hashCode: Int = 31 * caseNameValue.hashCode + value.hashCode

    def typeIndex: Int = 2

    def valueType: DynamicValueType = DynamicValueType.Variant

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t eq DynamicValueType.Variant) new Some(this.asInstanceOf[t.Type])
      else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t eq DynamicValueType.Variant) new Some((caseNameValue, value).asInstanceOf[t.Unwrap])
      else None

    override def caseName: Option[String] = new Some(caseNameValue)

    override def caseValue: Option[DynamicValue] = new Some(value)

    override def getCase(name: String): DynamicValueSelection =
      if (caseNameValue == name) DynamicValueSelection.succeed(value)
      else DynamicValueSelection.fail(SchemaError(s"Variant case '$name' does not match '$caseNameValue'"))

    def compare(that: DynamicValue): Int = that match {
      case v: Variant =>
        val cmp = caseNameValue.compare(v.caseNameValue)
        if (cmp != 0) return cmp
        value.compare(v.value)
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
      case s: Sequence =>
        val thatElements = s.elements
        val len          = elements.length
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
      if (t eq DynamicValueType.Sequence) new Some(this.asInstanceOf[t.Type])
      else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t eq DynamicValueType.Sequence) new Some(elements.asInstanceOf[t.Unwrap])
      else None

    override def get(index: Int): DynamicValueSelection =
      if (index >= 0 && index < elements.length) DynamicValueSelection.succeed(elements(index))
      else DynamicValueSelection.fail(SchemaError(s"Index $index out of bounds (size: ${elements.length})"))

    def compare(that: DynamicValue): Int = that match {
      case s: Sequence =>
        val xs     = elements
        val ys     = s.elements
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
   * comparison. Use sortMapKeys to normalize key ordering.
   */
  final case class Map(override val entries: Chunk[(DynamicValue, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case m: Map =>
        val thatEntries = m.entries
        val len         = entries.length
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
      if (t eq DynamicValueType.Map) new Some(this.asInstanceOf[t.Type])
      else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t eq DynamicValueType.Map) new Some(entries.asInstanceOf[t.Unwrap])
      else None

    override def get(key: DynamicValue): DynamicValueSelection = {
      val matches = entries.collect { case kv if kv._1 == key => kv._2 }
      if (matches.isEmpty) DynamicValueSelection.fail(SchemaError(s"Key not found in Map"))
      else DynamicValueSelection.succeedMany(matches)
    }

    def compare(that: DynamicValue): Int = that match {
      case m: Map =>
        val xs     = entries
        val ys     = m.entries
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
   * Analogous to JSON null or Scala's None. Use dropNulls to recursively remove
   * Null values from containers.
   */
  case object Null extends DynamicValue {
    def typeIndex: Int = 5

    def valueType: DynamicValueType = DynamicValueType.Null

    override def as(t: DynamicValueType): Option[t.Type] =
      if (t eq DynamicValueType.Null) new Some(this.asInstanceOf[t.Type])
      else None

    override def unwrap(t: DynamicValueType): Option[t.Unwrap] =
      if (t eq DynamicValueType.Null) new Some(().asInstanceOf[t.Unwrap])
      else None

    def compare(that: DynamicValue): Int =
      if (that eq Null) 0
      else 5 - that.typeIndex
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Convenience Constructors
  // ─────────────────────────────────────────────────────────────────────────

  val unit: DynamicValue = new Primitive(PrimitiveValue.Unit)

  def string(s: String): DynamicValue = new Primitive(new PrimitiveValue.String(s))

  def int(i: Int): DynamicValue = new Primitive(new PrimitiveValue.Int(i))

  def long(l: Long): DynamicValue = new Primitive(new PrimitiveValue.Long(l))

  def boolean(b: Boolean): DynamicValue = new Primitive(new PrimitiveValue.Boolean(b))

  def double(d: Double): DynamicValue = new Primitive(new PrimitiveValue.Double(d))

  def float(f: Float): DynamicValue = new Primitive(new PrimitiveValue.Float(f))

  def short(s: Short): DynamicValue = new Primitive(new PrimitiveValue.Short(s))

  def byte(b: Byte): DynamicValue = new Primitive(new PrimitiveValue.Byte(b))

  def char(c: Char): DynamicValue = new Primitive(new PrimitiveValue.Char(c))

  def bigInt(b: BigInt): DynamicValue = new Primitive(new PrimitiveValue.BigInt(b))

  def bigDecimal(b: BigDecimal): DynamicValue = new Primitive(new PrimitiveValue.BigDecimal(b))

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
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch = Differ.diff(oldValue, newValue)

  // ─────────────────────────────────────────────────────────────────────────
  // Path Navigation Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def getAtPath(dv: DynamicValue, path: DynamicOptic): DynamicValueSelection = {
    val nodes = path.nodes
    if (nodes.isEmpty) return DynamicValueSelection.succeed(dv)
    var current: Chunk[DynamicValue] = Chunk.single(dv)
    var idx                          = 0
    val len                          = nodes.length
    while (idx < len && current.nonEmpty) {
      val node = nodes(idx)
      current = node match {
        case DynamicOptic.Node.Field(name) =>
          current.flatMap {
            case r: Record => r.fields.collect { case kv if kv._1 == name => kv._2 }
            case _         => Chunk.empty
          }
        case DynamicOptic.Node.Case(name) =>
          current.collect { case v: Variant if v.caseNameValue == name => v.value }
        case DynamicOptic.Node.AtIndex(i) =>
          current.collect { case s: Sequence if i >= 0 && i < s.elements.length => s.elements(i) }
        case DynamicOptic.Node.AtMapKey(key) =>
          current.flatMap {
            case m: Map => m.entries.collect { case kv if kv._1 == key => kv._2 }
            case _      => Chunk.empty
          }
        case DynamicOptic.Node.AtIndices(indices) =>
          current.flatMap {
            case s: Sequence =>
              Chunk.from(indices).collect { case i if i >= 0 && i < s.elements.length => s.elements(i) }
            case _ => Chunk.empty
          }
        case DynamicOptic.Node.AtMapKeys(keys) =>
          current.flatMap {
            case m: Map => Chunk.from(keys).flatMap(key => m.entries.collect { case kv if kv._1 == key => kv._2 })
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
        case DynamicOptic.Node.Wrapped => current
      }
      idx += 1
    }
    if (current.isEmpty) DynamicValueSelection.fail(SchemaError.message(s"Path not found", path))
    else DynamicValueSelection.succeedMany(current)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Path Modification Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def modifyAtPath(
    dv: DynamicValue,
    path: DynamicOptic,
    f: DynamicValue => DynamicValue
  ): Option[DynamicValue] = modifyAtPath(dv, path.nodes, 0, f)

  private[this] def modifyAtPath(
    dv: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    index: Int,
    f: DynamicValue => DynamicValue
  ): Option[DynamicValue] = {
    if (index >= nodes.length) return new Some(f(dv))
    nodes(index) match {
      case DynamicOptic.Node.Field(name) =>
        dv match {
          case r: Record =>
            var found     = false
            val newFields = r.fields.map { kv =>
              if (kv._1 == name) {
                found = true
                modifyAtPath(kv._2, nodes, index + 1, f) match {
                  case Some(nv) => (kv._1, nv)
                  case _        => kv
                }
              } else kv
            }
            if (found) new Some(new Record(newFields))
            else None
          case _ => None
        }
      case DynamicOptic.Node.Case(name) =>
        dv match {
          case v: Variant if v.caseNameValue == name =>
            modifyAtPath(v.value, nodes, index + 1, f) match {
              case Some(nv) => new Some(Variant(v.caseNameValue, nv))
              case _        => None
            }
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(i) =>
        dv match {
          case s: Sequence if i >= 0 && i < s.elements.length =>
            modifyAtPath(s.elements(i), nodes, index + 1, f) match {
              case Some(nv) => new Some(new Sequence(s.elements.updated(i, nv)))
              case _        => None
            }
          case _ => None
        }
      case DynamicOptic.Node.AtMapKey(key) =>
        dv match {
          case m: Map =>
            var found      = false
            val newEntries = m.entries.map { kv =>
              if (kv._1 == key) {
                found = true
                modifyAtPath(kv._2, nodes, index + 1, f) match {
                  case Some(nv) => (kv._1, nv)
                  case _        => kv
                }
              } else kv
            }
            if (found) new Some(new Map(newEntries)) else None
          case _ => None
        }
      case DynamicOptic.Node.AtIndices(indices) =>
        dv match {
          case s: Sequence =>
            val indicesSet = indices.toSet
            var found      = false
            val newElems   = s.elements.map {
              var idx = -1
              e =>
                idx += 1
                if (indicesSet.contains(idx)) {
                  modifyAtPath(e, nodes, index + 1, f) match {
                    case Some(nv) =>
                      found = true
                      nv
                    case _ => e
                  }
                } else e
            }
            if (found) new Some(new Sequence(newElems))
            else None
          case _ => None
        }
      case DynamicOptic.Node.AtMapKeys(keys) =>
        dv match {
          case m: Map =>
            val keysSet = new util.HashSet[DynamicValue](keys.length)
            keys.foreach(keysSet.add)
            var found      = false
            val newEntries = m.entries.map { kv =>
              if (keysSet.contains(kv._1)) {
                modifyAtPath(kv._2, nodes, index + 1, f) match {
                  case Some(nv) =>
                    found = true
                    (kv._1, nv)
                  case _ => kv
                }
              } else kv
            }
            if (found) new Some(new Map(newEntries))
            else None
          case _ => None
        }
      case DynamicOptic.Node.Elements =>
        dv match {
          case s: Sequence =>
            var found    = false
            val newElems = s.elements.map { e =>
              modifyAtPath(e, nodes, index + 1, f) match {
                case Some(nv) =>
                  found = true
                  nv
                case _ => e
              }
            }
            if (found) new Some(new Sequence(newElems))
            else None
          case _ => None
        }
      case DynamicOptic.Node.MapKeys =>
        dv match {
          case m: Map =>
            var found      = false
            val newEntries = m.entries.map { kv =>
              modifyAtPath(kv._1, nodes, index + 1, f) match {
                case Some(nk) =>
                  found = true
                  (nk, kv._2)
                case _ => kv
              }
            }
            if (found) new Some(new Map(newEntries))
            else None
          case _ => None
        }
      case DynamicOptic.Node.MapValues =>
        dv match {
          case m: Map =>
            var found      = false
            val newEntries = m.entries.map { kv =>
              modifyAtPath(kv._2, nodes, index + 1, f) match {
                case Some(nv) =>
                  found = true
                  (kv._1, nv)
                case _ => kv
              }
            }
            if (found) new Some(new Map(newEntries))
            else None
          case _ => None
        }
      case DynamicOptic.Node.Wrapped => modifyAtPath(dv, nodes, index + 1, f)
    }
  }

  private def modifyAtPathOrFail(
    dv: DynamicValue,
    path: DynamicOptic,
    pf: PartialFunction[DynamicValue, DynamicValue]
  ): Either[SchemaError, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) {
      if (pf.isDefinedAt(dv)) new Right(pf(dv))
      else new Left(SchemaError.message("Partial function not defined at root", path))
    } else {
      modifyAtPath(dv, path, v => if (pf.isDefinedAt(v)) pf(v) else v) match {
        case Some(result) => new Right(result)
        case _            => new Left(SchemaError.message("Path not found", path))
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Delete Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def deleteAtPath(dv: DynamicValue, path: DynamicOptic): Option[DynamicValue] = deleteAtPath(dv, path.nodes, 0)

  private[this] def deleteAtPath(
    dv: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    index: Int
  ): Option[DynamicValue] = {
    if (index >= nodes.length) return None
    val isLast = index == nodes.length - 1
    nodes(index) match {
      case DynamicOptic.Node.Field(name) =>
        dv match {
          case r: Record =>
            if (isLast) {
              val newFields = r.fields.filterNot(_._1 == name)
              if (newFields.length < r.fields.length) new Some(new Record(newFields))
              else None
            } else {
              var found     = false
              val newFields = r.fields.map { kv =>
                if (kv._1 == name) {
                  deleteAtPath(kv._2, nodes, index + 1) match {
                    case Some(nv) =>
                      found = true
                      (kv._1, nv)
                    case _ => kv
                  }
                } else kv
              }
              if (found) new Some(new Record(newFields))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.Case(name) =>
        dv match {
          case v: Variant if v.caseNameValue == name =>
            if (isLast) new Some(Record.empty)
            else {
              deleteAtPath(v.value, nodes, index + 1) match {
                case Some(nv) => new Some(new Variant(v.caseNameValue, nv))
                case _        => None
              }
            }
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(i) =>
        dv match {
          case s: Sequence if i >= 0 && i < s.elements.length =>
            if (isLast) new Some(new Sequence(s.elements.patch(i, Chunk.empty, 1)))
            else {
              deleteAtPath(s.elements(i), nodes, index + 1) match {
                case Some(nv) => new Some(new Sequence(s.elements.updated(i, nv)))
                case _        => None
              }
            }
          case _ => None
        }
      case DynamicOptic.Node.AtMapKey(key) =>
        dv match {
          case m: Map =>
            if (isLast) {
              val newEntries = m.entries.filterNot(_._1 == key)
              if (newEntries.length < m.entries.length) new Some(new Map(newEntries))
              else None
            } else {
              var found      = false
              val newEntries = m.entries.map { kv =>
                if (kv._1 == key) {
                  deleteAtPath(kv._2, nodes, index + 1) match {
                    case Some(nv) =>
                      found = true
                      (kv._1, nv)
                    case _ => kv
                  }
                } else kv
              }
              if (found) new Some(new Map(newEntries))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.AtIndices(indices) =>
        dv match {
          case s: Sequence =>
            val indicesSet = indices.toSet
            if (isLast) {
              val newElems = s.elements
                .foldLeft(ChunkBuilder.make[DynamicValue](indicesSet.size)) {
                  var idx = -1
                  (acc, dv) =>
                    idx += 1
                    if (!indicesSet.contains(idx)) acc.addOne(dv)
                    else acc
                }
                .result()
              if (newElems.length < s.elements.length) new Some(new Sequence(newElems))
              else None
            } else {
              var found    = false
              val newElems = s.elements.map {
                var idx = -1
                e =>
                  idx += 1
                  if (indicesSet.contains(idx)) {
                    deleteAtPath(e, nodes, index + 1) match {
                      case Some(nv) =>
                        found = true
                        nv
                      case _ => e
                    }
                  } else e
              }
              if (found) new Some(new Sequence(newElems))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.AtMapKeys(keys) =>
        dv match {
          case m: Map =>
            val keysSet = new util.HashSet[DynamicValue](keys.length)
            keys.foreach(keysSet.add)
            if (isLast) {
              val newEntries = m.entries.filterNot(kv => keysSet.contains(kv._1))
              if (newEntries.length < m.entries.length) new Some(new Map(newEntries))
              else None
            } else {
              var found      = false
              val newEntries = m.entries.map { kv =>
                if (keysSet.contains(kv._1)) {
                  deleteAtPath(kv._2, nodes, index + 1) match {
                    case Some(nv) =>
                      found = true
                      (kv._1, nv)
                    case _ => kv
                  }
                } else kv
              }
              if (found) new Some(new Map(newEntries))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.Elements =>
        dv match {
          case s: Sequence =>
            if (isLast) new Some(Sequence.empty)
            else {
              var found    = false
              val newElems = s.elements.map { e =>
                deleteAtPath(e, nodes, index + 1) match {
                  case Some(nv) =>
                    found = true
                    nv
                  case _ => e
                }
              }
              if (found) new Some(new Sequence(newElems))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.MapKeys =>
        dv match {
          case m: Map =>
            if (isLast) new Some(Map.empty)
            else {
              var found      = false
              val newEntries = m.entries.map { kv =>
                deleteAtPath(kv._1, nodes, index + 1) match {
                  case Some(nk) =>
                    found = true
                    (nk, kv._2)
                  case _ => kv
                }
              }
              if (found) new Some(new Map(newEntries))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.MapValues =>
        dv match {
          case m: Map =>
            if (isLast) new Some(Map.empty)
            else {
              var found      = false
              val newEntries = m.entries.map { kv =>
                deleteAtPath(kv._2, nodes, index + 1) match {
                  case Some(nv) =>
                    found = true
                    (kv._1, nv)
                  case _ => kv
                }
              }
              if (found) new Some(new Map(newEntries))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.Wrapped => deleteAtPath(dv, nodes, index + 1)
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Insert Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def insertAtPath(dv: DynamicValue, path: DynamicOptic, value: DynamicValue): Option[DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return None
    insertAtPath(dv, nodes, 0, value)
  }

  private[this] def insertAtPath(
    dv: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    index: Int,
    value: DynamicValue
  ): Option[DynamicValue] = {
    if (index >= nodes.length) return None
    val isLast = index == nodes.length - 1
    nodes(index) match {
      case DynamicOptic.Node.Field(name) =>
        dv match {
          case r: Record =>
            if (isLast) {
              if (r.fields.exists(_._1 == name)) None
              else new Some(new Record(r.fields :+ (name, value)))
            } else {
              var found     = false
              val newFields = r.fields.map { kv =>
                if (kv._1 == name) {
                  insertAtPath(kv._2, nodes, index + 1, value) match {
                    case Some(nv) =>
                      found = true
                      (kv._1, nv)
                    case _ => kv
                  }
                } else kv
              }
              if (found) new Some(new Record(newFields))
              else None
            }
          case _ => None
        }
      case DynamicOptic.Node.Case(name) =>
        dv match {
          case v: Variant if v.caseNameValue == name =>
            if (isLast) None
            else {
              insertAtPath(v.value, nodes, index + 1, value) match {
                case Some(nv) => new Some(new Variant(v.caseNameValue, nv))
                case _        => None
              }
            }
          case _ => None
        }
      case DynamicOptic.Node.AtIndex(i) =>
        dv match {
          case s: Sequence =>
            val elements = s.elements
            if (isLast) {
              if (i >= 0 && i <= elements.length) {
                new Some(new Sequence(elements.take(i) ++ (value +: elements.drop(i))))
              } else None
            } else if (i >= 0 && i < elements.length) {
              insertAtPath(elements(i), nodes, index + 1, value) match {
                case Some(nv) => new Some(new Sequence(elements.updated(i, nv)))
                case _        => None
              }
            } else None
          case _ => None
        }
      case DynamicOptic.Node.AtMapKey(key) =>
        dv match {
          case m: Map =>
            if (isLast) {
              if (m.entries.exists(_._1 == key)) None
              else new Some(new Map(m.entries :+ (key, value)))
            } else {
              var found      = false
              val newEntries = m.entries.map { kv =>
                if (kv._1 == key) {
                  insertAtPath(kv._2, nodes, index + 1, value) match {
                    case Some(nv) =>
                      found = true
                      (kv._1, nv)
                    case _ => kv
                  }
                } else kv
              }
              if (found) new Some(new Map(newEntries))
              else None
            }
          case _ => None
        }
      case _ => None
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Merge Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def merge(
    left: DynamicValue,
    right: DynamicValue,
    strategy: DynamicValueMergeStrategy
  ): DynamicValue = merge(DynamicOptic.root, left, right, strategy)

  private[this] def merge(
    path: DynamicOptic,
    left: DynamicValue,
    right: DynamicValue,
    s: DynamicValueMergeStrategy
  ): DynamicValue = {
    left match {
      case lr: Record =>
        right match {
          case rr: Record if s.recurse(path, DynamicValueType.Record) => return mergeRecords(path, lr, rr, s)
          case _                                                      =>
        }
      case ls: Sequence =>
        right match {
          case rs: Sequence if s.recurse(path, DynamicValueType.Sequence) => return mergeSequences(path, ls, rs, s)
          case _                                                          =>
        }
      case lm: Map =>
        right match {
          case rm: Map if s.recurse(path, DynamicValueType.Map) => return mergeMaps(path, lm, rm, s)
          case _                                                =>
        }
      case lv: Variant =>
        right match {
          case rv: Variant if lv.caseNameValue == rv.caseNameValue && s.recurse(path, DynamicValueType.Variant) =>
            return new Variant(lv.caseNameValue, merge(path.caseOf(lv.caseNameValue), lv.value, rv.value, s))
          case _ =>
        }
      case _ =>
    }
    s(path, left, right)
  }

  private[this] def mergeRecords(
    path: DynamicOptic,
    left: Record,
    right: Record,
    s: DynamicValueMergeStrategy
  ): Record = {
    val leftFields  = left.fields
    val rightFields = right.fields
    val leftLen     = leftFields.length
    val rightLen    = rightFields.length
    val leftSeenAt  = new java.util.HashMap[java.lang.String, Int](leftLen)
    val rightSeenAt = new java.util.HashMap[java.lang.String, Int](rightLen)
    val rightDedup  = new scala.Array[(java.lang.String, DynamicValue)](rightLen)
    var merged      = new scala.Array[(java.lang.String, DynamicValue)](leftLen + rightLen)
    var idx         = 0
    leftFields.foreach { kv =>
      val key = kv._1
      val pos = leftSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        leftSeenAt.put(key, idx)
        merged(idx) = kv
        idx += 1
      } else merged(pos) = kv
    }
    var rightIdx = 0
    rightFields.foreach { kv =>
      val key = kv._1
      val pos = rightSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        rightSeenAt.put(key, rightIdx)
        rightDedup(rightIdx) = kv
        rightIdx += 1
      } else rightDedup(pos) = kv
    }
    val rightDedupLen = rightIdx
    rightIdx = 0
    while (rightIdx < rightDedupLen) {
      val kv = rightDedup(rightIdx)
      rightIdx += 1
      val key = kv._1
      val pos = leftSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        merged(idx) = kv
        idx += 1
      } else merged(pos) = (key, merge(path.field(key), merged(pos)._2, kv._2, s))
    }
    if (merged.length != idx) merged = java.util.Arrays.copyOf(merged, idx)
    new Record(Chunk.from(merged))
  }

  private[this] def mergeSequences(
    path: DynamicOptic,
    left: Sequence,
    right: Sequence,
    s: DynamicValueMergeStrategy
  ): Sequence = {
    val leftElements  = left.elements
    val rightElements = right.elements
    val leftLen       = leftElements.length
    val rightLen      = rightElements.length
    val merged        = new Array[DynamicValue](Math.max(leftLen, rightLen))
    val minLen        = Math.min(leftLen, rightLen)
    var idx           = 0
    while (idx < minLen) {
      merged(idx) = merge(path.at(idx), leftElements(idx), rightElements(idx), s)
      idx += 1
    }
    while (idx < leftLen) {
      merged(idx) = leftElements(idx)
      idx += 1
    }
    while (idx < rightLen) {
      merged(idx) = rightElements(idx)
      idx += 1
    }
    new Sequence(Chunk.fromArray(merged))
  }

  private[this] def mergeMaps(
    path: DynamicOptic,
    left: Map,
    right: Map,
    s: DynamicValueMergeStrategy
  ): Map = {
    val leftEntries  = left.entries
    val rightEntries = right.entries
    val leftLen      = leftEntries.length
    val rightLen     = rightEntries.length
    val leftSeenAt   = new java.util.HashMap[DynamicValue, Int](leftLen)
    val rightSeenAt  = new java.util.HashMap[DynamicValue, Int](rightLen)
    val rightDedup   = new scala.Array[(DynamicValue, DynamicValue)](rightLen)
    var merged       = new scala.Array[(DynamicValue, DynamicValue)](leftLen + rightLen)
    var idx          = 0
    leftEntries.foreach { kv =>
      val key = kv._1
      val pos = leftSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        leftSeenAt.put(key, idx)
        merged(idx) = kv
        idx += 1
      } else merged(pos) = kv
    }
    var rightIdx = 0
    rightEntries.foreach { kv =>
      val key = kv._1
      val pos = rightSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        rightSeenAt.put(key, rightIdx)
        rightDedup(rightIdx) = kv
        rightIdx += 1
      } else rightDedup(pos) = kv
    }
    val rightDedupLen = rightIdx
    rightIdx = 0
    while (rightIdx < rightDedupLen) {
      val kv = rightDedup(rightIdx)
      rightIdx += 1
      val key = kv._1
      val pos = leftSeenAt.getOrDefault(key, -1)
      if (pos < 0) {
        merged(idx) = kv
        idx += 1
      } else {
        val path_ = new DynamicOptic(path.nodes :+ new DynamicOptic.Node.AtMapKey(key))
        merged(pos) = (key, merge(path_, merged(pos)._2, kv._2, s))
      }
    }
    if (merged.length != idx) merged = java.util.Arrays.copyOf(merged, idx)
    new Map(Chunk.from(merged))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Normalization Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def sortFields(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => new Record(r.fields.sortBy(_._1).map(kv => (kv._1, sortFields(kv._2))))
    case s: Sequence => new Sequence(s.elements.map(sortFields))
    case m: Map      => new Map(m.entries.map(kv => (sortFields(kv._1), sortFields(kv._2))))
    case v: Variant  => new Variant(v.caseNameValue, sortFields(v.value))
    case other       => other
  }

  private def sortMapKeys(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => new Record(r.fields.map(kv => (kv._1, sortMapKeys(kv._2))))
    case s: Sequence => new Sequence(s.elements.map(sortMapKeys))
    case m: Map      => new Map(m.entries.sortBy(_._1).map(kv => (sortMapKeys(kv._1), sortMapKeys(kv._2))))
    case v: Variant  => new Variant(v.caseNameValue, sortMapKeys(v.value))
    case other       => other
  }

  private def dropNulls(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => new Record(r.fields.collect { case kv if kv._2 ne Null => (kv._1, dropNulls(kv._2)) })
    case s: Sequence => new Sequence(s.elements.collect { case e if e ne Null => dropNulls(e) })
    case m: Map      =>
      new Map(m.entries.collect { case (k, v) if (k ne Null) && (v ne Null) => (dropNulls(k), dropNulls(v)) })
    case v: Variant if v.value ne Null => new Variant(v.caseNameValue, dropNulls(v.value))
    case other                         => other
  }

  private def dropUnits(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => new Record(r.fields.collect { case (k, v) if isNotUnit(v) => (k, dropUnits(v)) })
    case s: Sequence => new Sequence(s.elements.collect { case e if isNotUnit(e) => dropUnits(e) })
    case m: Map      =>
      new Map(m.entries.collect { case (k, v) if isNotUnit(k) && isNotUnit(v) => (dropUnits(k), dropUnits(v)) })
    case v: Variant if isNotUnit(v.value) => new Variant(v.caseNameValue, dropUnits(v.value))
    case other                            => other
  }

  private[this] def isNotUnit(dv: DynamicValue): Boolean = dv match {
    case Primitive(PrimitiveValue.Unit) => false
    case _                              => true
  }

  private def dropEmpty(dv: DynamicValue): DynamicValue = dv match {
    case r: Record   => new Record(r.fields.collect { case (k, v) if isNotEmpty(v) => (k, dropEmpty(v)) })
    case s: Sequence => new Sequence(s.elements.collect { case e if isNotEmpty(e) => dropEmpty(e) })
    case m: Map      =>
      new Map(m.entries.collect { case (k, v) if isNotEmpty(k) && isNotEmpty(v) => (dropEmpty(k), dropEmpty(v)) })
    case v: Variant if isNotEmpty(v.value) => new Variant(v.caseNameValue, dropEmpty(v.value))
    case other                             => other
  }

  private[this] def isNotEmpty(dv: DynamicValue): Boolean = dv match {
    case r: Record   => r.fields.nonEmpty
    case s: Sequence => s.elements.nonEmpty
    case m: Map      => m.entries.nonEmpty
    case _           => true
  }
  // ─────────────────────────────────────────────────────────────────────────
  // Transformation Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def transformUp(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, DynamicValue) => DynamicValue
  ): DynamicValue = {
    val transformed = dv match {
      case r: Record   => new Record(r.fields.map { case (k, v) => (k, transformUp(v, path.field(k), f)) })
      case s: Sequence =>
        new Sequence(s.elements.map {
          var idx = -1
          e =>
            idx += 1
            transformUp(e, path.at(idx), f)
        })
      case m: Map =>
        new Map(m.entries.map { case (k, v) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ new DynamicOptic.Node.AtMapKey(k))
          (transformUp(k, keyPath, f), transformUp(v, valuePath, f))
        })
      case v: Variant => new Variant(v.caseNameValue, transformUp(v.value, path.caseOf(v.caseNameValue), f))
      case other      => other
    }
    f(path, transformed)
  }

  private def transformDown(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, DynamicValue) => DynamicValue
  ): DynamicValue = {
    val transformed = f(path, dv)
    transformed match {
      case r: Record   => new Record(r.fields.map { case (k, v) => (k, transformDown(v, path.field(k), f)) })
      case s: Sequence =>
        new Sequence(s.elements.map {
          var idx = -1
          e =>
            idx += 1
            transformDown(e, path.at(idx), f)
        })
      case m: Map =>
        new Map(m.entries.map { case (k, v) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ new DynamicOptic.Node.AtMapKey(k))
          (transformDown(k, keyPath, f), transformDown(v, valuePath, f))
        })
      case v: Variant => new Variant(v.caseNameValue, transformDown(v.value, path.caseOf(v.caseNameValue), f))
      case other      => other
    }
  }

  private def transformFields(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, String) => String
  ): DynamicValue = dv match {
    case r: Record =>
      new Record(r.fields.map { kv =>
        val newKey = f(path, kv._1)
        (newKey, transformFields(kv._2, path.field(newKey), f))
      })
    case s: Sequence =>
      new Sequence(s.elements.map {
        var idx = -1
        e =>
          idx += 1
          transformFields(e, path.at(idx), f)
      })
    case m: Map =>
      new Map(m.entries.map { kv =>
        (transformFields(kv._1, path, f), transformFields(kv._2, path, f))
      })
    case v: Variant => Variant(v.caseNameValue, transformFields(v.value, path.caseOf(v.caseNameValue), f))
    case other      => other
  }

  private def transformMapKeys(
    dv: DynamicValue,
    path: DynamicOptic,
    f: (DynamicOptic, DynamicValue) => DynamicValue
  ): DynamicValue = dv match {
    case r: Record   => new Record(r.fields.map { case (k, v) => (k, transformMapKeys(v, path.field(k), f)) })
    case s: Sequence =>
      new Sequence(s.elements.map {
        var idx = -1
        e =>
          idx += 1
          transformMapKeys(e, path.at(idx), f)
      })
    case m: Map =>
      new Map(m.entries.map { kv =>
        (f(new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys), kv._1), transformMapKeys(kv._2, path, f))
      })
    case v: Variant => new Variant(v.caseNameValue, transformMapKeys(v.value, path.caseOf(v.caseNameValue), f))
    case other      => other
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Prune/Retain/Partition Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def prune(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): DynamicValue = dv match {
    case r: Record =>
      new Record(r.fields.flatMap { case (k, v) =>
        val childPath = path.field(k)
        if (p(childPath, v)) None
        else new Some((k, prune(v, childPath, p)))
      })
    case s: Sequence =>
      new Sequence(s.elements.flatMap {
        var idx = -1
        e =>
          idx += 1
          val childPath = path.at(idx)
          if (p(childPath, e)) None
          else new Some(prune(e, childPath, p))
      })
    case m: Map =>
      new Map(m.entries.flatMap { case (k, v) =>
        val childPath = new DynamicOptic(path.nodes :+ new DynamicOptic.Node.AtMapKey(k))
        if (p(childPath, v)) None
        else new Some((prune(k, path, p), prune(v, childPath, p)))
      })
    case v: Variant => new Variant(v.caseNameValue, prune(v.value, path.caseOf(v.caseNameValue), p))
    case other      => other
  }

  private def retain(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): DynamicValue = {
    def retainRec(current: DynamicValue, currentPath: DynamicOptic): DynamicValue = current match {
      case r: Record =>
        new Record(r.fields.flatMap { case (k, v) =>
          val childPath = currentPath.field(k)
          val retained  = retainRec(v, childPath)
          if (p(childPath, v) || hasContent(retained)) new Some((k, retained))
          else None
        })
      case s: Sequence =>
        new Sequence(s.elements.flatMap {
          var idx = -1
          e =>
            idx += 1
            val childPath = currentPath.at(idx)
            val retained  = retainRec(e, childPath)
            if (p(childPath, e) || hasContent(retained)) new Some(retained)
            else None
        })
      case m: Map =>
        new Map(m.entries.flatMap { case (k, v) =>
          val childPath = new DynamicOptic(currentPath.nodes :+ DynamicOptic.Node.AtMapKey(k))
          val retained  = retainRec(v, childPath)
          if (p(childPath, v) || hasContent(retained)) new Some((k, retained))
          else None
        })
      case v: Variant =>
        val childPath = currentPath.caseOf(v.caseNameValue)
        val retained  = retainRec(v.value, childPath)
        if (p(childPath, v.value) || hasContent(retained)) new Variant(v.caseNameValue, retained)
        else current
      case other =>
        if (p(currentPath, other)) other
        else Null
    }

    retainRec(dv, path)
  }

  private[this] def hasContent(dv: DynamicValue): Boolean = dv match {
    case r: Record   => r.fields.nonEmpty
    case s: Sequence => s.elements.nonEmpty
    case m: Map      => m.entries.nonEmpty
    case Null        => false
    case _           => true
  }

  private def project(dv: DynamicValue, paths: Seq[DynamicOptic]): DynamicValue =
    retain(
      dv,
      DynamicOptic.root,
      (path, _) => paths.exists(p => path.nodes.startsWith(p.nodes) || p.nodes.startsWith(path.nodes))
    )

  private def partition(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): (DynamicValue, DynamicValue) = (retain(dv, path, p), prune(dv, path, p))

  // ─────────────────────────────────────────────────────────────────────────
  // Fold Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private def foldUp[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => B
  ): B = {
    val childAcc = dv match {
      case r: Record   => r.fields.foldLeft(z)((acc, kv) => foldUp(kv._2, path.field(kv._1), acc, f))
      case s: Sequence =>
        s.elements.foldLeft(z) {
          var idx = -1
          (acc, e) =>
            idx += 1
            foldUp(e, path.at(idx), acc, f)
        }
      case m: Map =>
        m.entries.foldLeft(z) { case (acc, (k, v)) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(k))
          foldUp(v, valuePath, foldUp(k, keyPath, acc, f), f)
        }
      case v: Variant => foldUp(v.value, path.caseOf(v.caseNameValue), z, f)
      case _          => z
    }
    f(path, dv, childAcc)
  }

  private def foldDown[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => B
  ): B = {
    val acc = f(path, dv, z)
    dv match {
      case r: Record   => r.fields.foldLeft(acc)((a, kv) => foldDown(kv._2, path.field(kv._1), a, f))
      case s: Sequence =>
        s.elements.foldLeft(acc) {
          var idx = -1
          (a, e) =>
            idx += 1
            foldDown(e, path.at(idx), a, f)
        }
      case m: Map =>
        m.entries.foldLeft(acc) { (a, kv) =>
          val keyPath   = new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)
          val valuePath = new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(kv._1))
          foldDown(kv._2, valuePath, foldDown(kv._1, keyPath, a, f), f)
        }
      case v: Variant => foldDown(v.value, path.caseOf(v.caseNameValue), acc, f)
      case _          => acc
    }
  }

  private def foldUpOrFail[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] =
    f(
      path,
      dv,
      dv match {
        case r: Record =>
          var b      = z
          val fields = r.fields
          val len    = fields.length
          var idx    = 0
          while (idx < len) {
            val kv = fields(idx)
            foldUpOrFail(kv._2, path.field(kv._1), b, f) match {
              case Right(b1) => b = b1
              case l         => return l
            }
            idx += 1
          }
          b
        case s: Sequence =>
          var b        = z
          val elements = s.elements
          val len      = elements.length
          var idx      = 0
          while (idx < len) {
            val e = elements(idx)
            foldUpOrFail(e, path.at(idx), b, f) match {
              case Right(b1) => b = b1
              case l         => return l
            }
            idx += 1
          }
          b
        case m: Map =>
          var b       = z
          val entries = m.entries
          val len     = entries.length
          var idx     = 0
          while (idx < len) {
            val kv  = entries(idx)
            val key = kv._1
            foldUpOrFail(key, new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys), b, f) match {
              case Right(a1) =>
                foldUpOrFail(kv._2, new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(key)), a1, f) match {
                  case Right(b1) => b = b1
                  case l         => return l
                }
              case l => return l
            }
            idx += 1
          }
          b
        case v: Variant =>
          foldUpOrFail(v.value, path.caseOf(v.caseNameValue), z, f) match {
            case Right(b) => b
            case l        => return l
          }
        case _ => z
      }
    )

  private def foldDownOrFail[B](
    dv: DynamicValue,
    path: DynamicOptic,
    z: B,
    f: (DynamicOptic, DynamicValue, B) => Either[SchemaError, B]
  ): Either[SchemaError, B] =
    f(path, dv, z) match {
      case Right(z1) =>
        dv match {
          case r: Record =>
            var b      = z1
            val fields = r.fields
            val len    = fields.length
            var idx    = 0
            while (idx < len) {
              val kv = fields(idx)
              foldDownOrFail(kv._2, path.field(kv._1), b, f) match {
                case Right(b1) => b = b1
                case l         => return l
              }
              idx += 1
            }
            new Right(b)
          case s: Sequence =>
            var b        = z1
            val elements = s.elements
            val len      = elements.length
            var idx      = 0
            while (idx < len) {
              val e = elements(idx)
              foldDownOrFail(e, path.at(idx), b, f) match {
                case Right(b1) => b = b1
                case l         => return l
              }
              idx += 1
            }
            new Right(b)
          case m: Map =>
            var b       = z1
            val entries = m.entries
            val len     = entries.length
            var idx     = 0
            while (idx < len) {
              val kv  = entries(idx)
              val key = kv._1
              foldDownOrFail(key, new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys), b, f) match {
                case Right(a1) =>
                  foldDownOrFail(kv._2, new DynamicOptic(path.nodes :+ DynamicOptic.Node.AtMapKey(key)), a1, f) match {
                    case Right(b1) => b = b1
                    case l         => return l
                  }
                case l => return l
              }
              idx += 1
            }
            new Right(b)
          case v: Variant => foldDownOrFail(v.value, path.caseOf(v.caseNameValue), z1, f)
          case _          => new Right(z1)
        }
      case l => l
    }

  private def toKV(dv: DynamicValue, path: DynamicOptic): Chunk[(DynamicOptic, DynamicValue)] = dv match {
    case r: Record   => r.fields.flatMap(kv => toKV(kv._2, path.field(kv._1)))
    case s: Sequence =>
      s.elements.flatMap {
        var idx = -1
        e =>
          idx += 1
          toKV(e, path.at(idx))
      }
    case m: Map =>
      m.entries.flatMap(kv => toKV(kv._2, new DynamicOptic(path.nodes :+ new DynamicOptic.Node.AtMapKey(kv._1))))
    case v: Variant => toKV(v.value, path.caseOf(v.caseNameValue))
    case other      => Chunk.single((path, other))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // KV Reconstruction
  // ─────────────────────────────────────────────────────────────────────────

  def fromKV(kvs: Seq[(DynamicOptic, DynamicValue)]): Either[SchemaError, DynamicValue] =
    if (kvs.isEmpty) new Right(Record.empty)
    else {
      try new Right(fromKVUnsafe(kvs))
      catch {
        case e: SchemaError => new Left(e)
      }
    }

  def fromKVUnsafe(kvs: Seq[(DynamicOptic, DynamicValue)]): DynamicValue = {
    var result: DynamicValue = Record.empty
    kvs.foreach(kv => result = upsertAtPathCreatingParents(result, kv._1, kv._2))
    result
  }

  private[this] def upsertAtPathCreatingParents(
    dv: DynamicValue,
    path: DynamicOptic,
    value: DynamicValue
  ): DynamicValue = {
    val nodes = path.nodes
    if (nodes.isEmpty) return value

    def go(current: DynamicValue, idx: Int): DynamicValue = {
      if (idx >= nodes.length) return value
      val node   = nodes(idx)
      val isLast = idx == nodes.length - 1
      node match {
        case f: DynamicOptic.Node.Field =>
          val name = f.name
          new Record(current match {
            case r: Record =>
              val fieldIdx = r.fields.indexWhere(_._1 == name)
              if (fieldIdx >= 0) {
                val v = r.fields(fieldIdx)._2
                r.fields.updated(
                  fieldIdx,
                  (
                    name, {
                      if (isLast) value
                      else go(v, idx + 1)
                    }
                  )
                )
              } else {
                r.fields :+ (
                  name, {
                    if (isLast) value
                    else go(createContainer(nodes(idx + 1)), idx + 1)
                  }
                )
              }
            case _ =>
              Chunk.single(
                (
                  name, {
                    if (isLast) value
                    else go(createContainer(nodes(idx + 1)), idx + 1)
                  }
                )
              )
          })
        case ai: DynamicOptic.Node.AtIndex =>
          val index = ai.index
          new Sequence(current match {
            case s: Sequence =>
              if (index >= 0 && index < s.elements.length) {
                s.elements.updated(
                  index,
                  if (isLast) value
                  else go(s.elements(index), idx + 1)
                )
              } else if (index == s.elements.length) {
                s.elements :+ {
                  if (isLast) value
                  else go(createContainer(nodes(idx + 1)), idx + 1)
                }
              } else {
                s.elements ++ Chunk.fill(index - s.elements.length)(Null: DynamicValue) :+ {
                  if (isLast) value
                  else go(createContainer(nodes(idx + 1)), idx + 1)
                }
              }
            case _ =>
              val padding = Chunk.fill(index)(Null: DynamicValue)
              padding :+ {
                if (isLast) value
                else go(createContainer(nodes(idx + 1)), idx + 1)
              }
          })
        case amt: DynamicOptic.Node.AtMapKey =>
          val key = amt.key
          new Map(current match {
            case m: Map =>
              val keyIdx = m.entries.indexWhere(_._1 == key)
              if (keyIdx >= 0) {
                val (k, v) = m.entries(keyIdx)
                m.entries.updated(
                  keyIdx,
                  (
                    k, {
                      if (isLast) value
                      else go(v, idx + 1)
                    }
                  )
                )
              } else {
                m.entries :+ (
                  key, {
                    if (isLast) value
                    else go(createContainer(nodes(idx + 1)), idx + 1)
                  }
                )
              }
            case _ =>
              Chunk.single(
                (
                  key, {
                    if (isLast) value
                    else go(createContainer(nodes(idx + 1)), idx + 1)
                  }
                )
              )
          })
        case c: DynamicOptic.Node.Case =>
          val caseName = c.name
          new Variant(
            caseName, {
              if (isLast) value
              else
                go(
                  current match {
                    case v: Variant if v.caseNameValue == caseName => v.value
                    case _                                         => Record.empty
                  },
                  idx + 1
                )
            }
          )
        case _ => go(current, idx + 1)
      }
    }

    go(dv, 0)
  }

  private[this] def createContainer(nextNode: DynamicOptic.Node): DynamicValue = nextNode match {
    case _: DynamicOptic.Node.AtIndex  => Sequence.empty
    case _: DynamicOptic.Node.AtMapKey => Map.empty
    case _                             => Record.empty
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Query Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[schema] def query(
    dv: DynamicValue,
    path: DynamicOptic,
    p: (DynamicOptic, DynamicValue) => Boolean
  ): Chunk[DynamicValue] = {
    val doPrepend = p(path, dv)
    val children  = dv match {
      case r: Record   => r.fields.flatMap(kv => query(kv._2, path.field(kv._1), p))
      case s: Sequence =>
        s.elements.flatMap {
          var idx = -1
          e =>
            idx += 1
            query(e, path.at(idx), p)
        }
      case m: Map =>
        m.entries.flatMap(kv => query(kv._2, new DynamicOptic(path.nodes :+ new DynamicOptic.Node.AtMapKey(kv._1)), p))
      case v: Variant => query(v.value, path.caseOf(v.caseNameValue), p)
      case _          => Chunk.empty
    }
    if (doPrepend) dv +: children
    else children
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
  private def toEjson(value: DynamicValue, indent: Int, sb: java.lang.StringBuilder): Unit =
    value match {
      case p: Primitive => primitiveToEjson(p.value, sb)
      case r: Record    =>
        val fields = r.fields
        if (fields.isEmpty) sb.append("{}")
        else {
          sb.append("{\n")
          fields.foreach {
            val len = fields.length
            var idx = 0
            nv =>
              indentStr(sb, indent + 1)
              escapeFieldName(nv._1, sb)
              sb.append(": ")
              toEjson(nv._2, indent + 1, sb)
              idx += 1
              if (idx < len) sb.append(',')
              sb.append('\n')
          }
          indentStr(sb, indent).append('}')
        }
      case v: Variant =>
        // Variants use postfix @ metadata: { ... } @ {tag: "CaseName"}
        toEjson(v.value, indent, sb)
        sb.append(" @ {tag: ")
        escapeString(sb, v.caseNameValue)
        sb.append('}')
      case s: Sequence =>
        val elements = s.elements
        if (elements.isEmpty) sb.append("[]")
        else if (elements.length == 1 && elements(0).isInstanceOf[Primitive]) {
          // Only inline single-element sequences if the element is a primitive
          sb.append('[')
          toEjson(elements(0), indent, sb)
          sb.append(']')
        } else {
          sb.append("[\n")
          elements.foreach {
            val len = elements.length
            var idx = 0
            elem =>
              indentStr(sb, indent + 1)
              toEjson(elem, indent + 1, sb)
              idx += 1
              if (idx < len) sb.append(',')
              sb.append('\n')
          }
          indentStr(sb, indent).append(']')
        }
      case m: Map =>
        val entries = m.entries
        if (entries.isEmpty) sb.append("{}")
        else {
          sb.append("{\n")
          entries.foreach {
            val len = entries.length
            var idx = 0
            kv =>
              indentStr(sb, indent + 1)
              val key = kv._1
              key match { // For string keys in maps, we quote them. For non-string keys, we don't quote them.
                case Primitive(PrimitiveValue.String(str)) => escapeString(sb, str)
                case _                                     => toEjson(key, indent + 1, sb)
              }
              sb.append(": ")
              toEjson(kv._2, indent + 1, sb)
              idx += 1
              if (idx < len) sb.append(',')
              sb.append('\n')
          }
          indentStr(sb, indent).append('}')
        }
      case _ => sb.append("null")
    }

  private[this] def indentStr(sb: java.lang.StringBuilder, indent: Int): java.lang.StringBuilder = {
    var idx = 0
    while (idx < indent) {
      sb.append(' ').append(' ')
      idx += 1
    }
    sb
  }

  /**
   * Convert a PrimitiveValue to EJSON format. Most primitives render as their
   * JSON equivalent. Some primitives need @ metadata for type information.
   */
  private[this] def primitiveToEjson(pv: PrimitiveValue, sb: java.lang.StringBuilder): Unit = pv match {
    case v: PrimitiveValue.Boolean    => sb.append(v.value)
    case v: PrimitiveValue.Byte       => sb.append(v.value)
    case v: PrimitiveValue.Short      => sb.append(v.value)
    case v: PrimitiveValue.Int        => sb.append(v.value)
    case v: PrimitiveValue.Long       => sb.append(v.value)
    case v: PrimitiveValue.Float      => sb.append(JsonBinaryCodec.floatCodec.encodeToString(v.value))
    case v: PrimitiveValue.Double     => sb.append(JsonBinaryCodec.doubleCodec.encodeToString(v.value))
    case v: PrimitiveValue.Char       => escapeString(sb, v.value.toString)
    case v: PrimitiveValue.String     => escapeString(sb, v.value)
    case v: PrimitiveValue.BigInt     => sb.append(JsonBinaryCodec.bigIntCodec.encodeToString(v.value))
    case v: PrimitiveValue.BigDecimal => sb.append(JsonBinaryCodec.bigDecimalCodec.encodeToString(v.value))
    case v: PrimitiveValue.DayOfWeek  => sb.append(JsonBinaryCodec.dayOfWeekCodec.encodeToString(v.value))
    case v: PrimitiveValue.Month      => sb.append(JsonBinaryCodec.monthCodec.encodeToString(v.value))
    case v: PrimitiveValue.Instant    => sb.append(v.value.toEpochMilli).append(" @ {type: \"instant\"}")
    case v: PrimitiveValue.LocalDate  =>
      sb.append(JsonBinaryCodec.localDateCodec.encodeToString(v.value)).append(" @ {type: \"localDate\"}")
    case v: PrimitiveValue.LocalDateTime  => sb.append(JsonBinaryCodec.localDateTimeCodec.encodeToString(v.value))
    case v: PrimitiveValue.LocalTime      => sb.append(JsonBinaryCodec.localTimeCodec.encodeToString(v.value))
    case v: PrimitiveValue.OffsetDateTime => sb.append(JsonBinaryCodec.offsetDateTimeCodec.encodeToString(v.value))
    case v: PrimitiveValue.OffsetTime     => sb.append(JsonBinaryCodec.offsetTimeCodec.encodeToString(v.value))
    case v: PrimitiveValue.Year           => sb.append(v.value.getValue)
    case v: PrimitiveValue.YearMonth      => sb.append(JsonBinaryCodec.yearMonthCodec.encodeToString(v.value))
    case v: PrimitiveValue.ZoneOffset     => sb.append(JsonBinaryCodec.zoneOffsetCodec.encodeToString(v.value))
    case v: PrimitiveValue.ZonedDateTime  => sb.append(JsonBinaryCodec.zonedDateTimeCodec.encodeToString(v.value))
    case v: PrimitiveValue.MonthDay       => sb.append(JsonBinaryCodec.monthDayCodec.encodeToString(v.value))
    case v: PrimitiveValue.Period         =>
      sb.append(JsonBinaryCodec.periodCodec.encodeToString(v.value)).append(" @ {type: \"period\"}")
    case v: PrimitiveValue.Duration =>
      sb.append(JsonBinaryCodec.durationCodec.encodeToString(v.value)).append(" @ {type: \"duration\"}")
    case v: PrimitiveValue.ZoneId    => sb.append(JsonBinaryCodec.zoneIdCodec.encodeToString(v.value))
    case v: PrimitiveValue.Currency  => sb.append(JsonBinaryCodec.currencyCodec.encodeToString(v.value))
    case v: PrimitiveValue.UUID      => sb.append(JsonBinaryCodec.uuidCodec.encodeToString(v.value))
    case _: PrimitiveValue.Unit.type => sb.append("null")
  }

  /**
   * Escape a field name for EJSON output. Valid Scala identifiers are left
   * as-is; invalid identifiers are wrapped in backticks, with backticks doubled
   * for escaping.
   */
  private[this] def escapeFieldName(name: String, sb: java.lang.StringBuilder): Unit =
    if (isValidIdentifier(name)) sb.append(name)
    else sb.append('`').append(name.replace("`", "``")).append('`')

  /**
   * Check if a string is a valid Scala identifier. A valid identifier:
   *   - Must start with a letter or underscore
   *   - Can contain letters, digits, or underscores
   *   - Cannot be a Scala keyword
   *   - Cannot contain $ (discouraged in user-written code)
   */
  private[this] def isValidIdentifier(s: String): Boolean = {
    if (s.isEmpty) return false
    if (scalaKeywords.contains(s)) return false
    val first = s.charAt(0)
    if (!Character.isLetter(first) && first != '_') return false
    var idx = 1
    while (idx < s.length) {
      val c = s.charAt(idx)
      if (!Character.isLetterOrDigit(c) && c != '_') return false
      idx += 1
    }
    true
  }

  /**
   * Scala keywords that cannot be used as identifiers without backticks.
   */
  private[this] val scalaKeywords: Set[String] = Set(
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
  private[this] def escapeString(sb: java.lang.StringBuilder, s: String): Unit = {
    sb.append('"')
    val len = s.length
    var idx = 0
    while (idx < len) {
      val ch = s.charAt(idx)
      idx += 1
      if (ch >= ' ' && ch != '"' && ch != '\\') sb.append(ch)
      else {
        sb.append('\\')
        ch match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case '\b' => sb.append('b')
          case '\f' => sb.append('f')
          case '\n' => sb.append('n')
          case '\r' => sb.append('r')
          case '\t' => sb.append('t')
          case _    =>
            sb.append('u')
              .append(hexDigit((ch >> 12) & 0xf))
              .append(hexDigit((ch >> 8) & 0xf))
              .append(hexDigit((ch >> 4) & 0xf))
              .append(hexDigit(ch & 0xf))
        }
      }
    }
    sb.append('"')
  }

  private[this] def hexDigit(n: Int): Char = (n + (if (n < 10) 48 else 87)).toChar
}
