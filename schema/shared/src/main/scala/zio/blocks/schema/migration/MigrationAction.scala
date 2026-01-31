package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A single migration action operating at a specific path.
 *
 * All actions are pure data - no functions, closures, or reflection. This makes
 * migrations fully serializable and introspectable.
 *
 * Each action defines:
 *   - `at`: The path where this action operates (using DynamicOptic)
 *   - `reverse`: The structural inverse of this action
 *   - `apply`: The runtime transformation on DynamicValue
 *
 * Actions can be composed into a [[DynamicMigration]] and applied to transform
 * data between schema versions.
 */
sealed trait MigrationAction {

  /** The path where this action operates */
  def at: DynamicOptic

  /** Structural reverse of this action (for bidirectional migrations) */
  def reverse: MigrationAction

  /** Apply this action to a DynamicValue */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  /**
   * Create a copy of this action with the path prefixed by the given prefix.
   * Used for nested migrations where actions need to be scoped to a sub-path.
   */
  def prefixPath(prefix: DynamicOptic): MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Field Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to a record with a default value.
   *
   * The default value is stored as a [[Resolved]] expression that is evaluated
   * at migration time. This allows defaults to be computed from other fields or
   * to use schema-defined default values.
   *
   * Reverse: [[DropField]] with the same default (to preserve round-trip
   * capability)
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = DropField(at, fieldName, default)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyRecord(value, at) { fields =>
        // Pass the current record to evalDynamic so expressions like FieldAccess work
        val recordValue = DynamicValue.Record(fields: _*)
        default.evalDynamic(recordValue) match {
          case Right(defaultValue) =>
            Right(fields :+ (fieldName -> defaultValue))
          case Left(err) =>
            Left(MigrationError.ExpressionFailed(at, err))
        }
      }
  }

  /**
   * Remove a field from a record.
   *
   * Stores a default value for use in reverse migration. If no meaningful
   * default exists, use [[Resolved.Fail]] to indicate that reverse migration
   * will fail.
   *
   * Reverse: [[AddField]] with the stored default
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyRecord(value, at) { fields =>
        Right(fields.filterNot(_._1 == fieldName))
      }
  }

  /**
   * Rename a field in a record.
   *
   * The field value is preserved; only the name changes.
   *
   * Reverse: [[Rename]] with from/to swapped
   */
  final case class Rename(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {

    def reverse: MigrationAction = Rename(at, to, from)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyRecord(value, at) { fields =>
        Right(fields.map {
          case (name, v) if name == from => (to, v)
          case other                     => other
        })
      }
  }

  /**
   * Transform a field's value using a pure expression.
   *
   * Both forward and reverse transforms must be provided to enable
   * bidirectional migration. Use [[Resolved.Fail]] for the reverse if the
   * transformation is not reversible.
   *
   * Reverse: [[TransformValue]] with transforms swapped
   */
  final case class TransformValue(
    at: DynamicOptic,
    fieldName: String,
    transform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformValue(at, fieldName, reverseTransform, transform)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val fieldPath = at.field(fieldName)
      modifyAtPath(value, fieldPath) { fieldValue =>
        transform.evalDynamic(fieldValue) match {
          case Right(result) => Right(result)
          case Left(err)     => Left(MigrationError.ExpressionFailed(fieldPath, err))
        }
      }
    }
  }

  /**
   * Make an optional field mandatory by unwrapping Some or using a default for
   * None.
   *
   * Handles Option represented as Variant("Some", value) or Variant("None", _).
   *
   * Reverse: [[Optionalize]]
   */
  final case class Mandate(
    at: DynamicOptic,
    fieldName: String,
    default: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = Optionalize(at, fieldName)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      // Work at record level so we have context for FieldAccess in defaults
      modifyRecord(value, at) { fields =>
        val recordValue = DynamicValue.Record(fields: _*)
        val fieldIdx    = fields.indexWhere(_._1 == fieldName)
        if (fieldIdx < 0) {
          // Field doesn't exist, nothing to do
          Right(fields)
        } else {
          val (name, fieldValue)                             = fields(fieldIdx)
          val newValue: Either[MigrationError, DynamicValue] = fieldValue match {
            // Some is represented as Variant("Some", Record(Vector(("value", inner))))
            case DynamicValue.Variant("Some", DynamicValue.Record(innerFields)) =>
              innerFields.find(_._1 == "value").map(_._2) match {
                case scala.Some(inner) => Right(inner)
                case scala.None        => Right(fieldValue) // Malformed, pass through
              }
            case DynamicValue.Variant("Some", inner) =>
              // Fallback for simple representation
              Right(inner)
            case DynamicValue.Variant("None", _) =>
              default.evalDynamic(recordValue) match {
                case Right(d)  => Right(d)
                case Left(err) => Left(MigrationError.ExpressionFailed(at.field(fieldName), err))
              }
            case DynamicValue.Null =>
              default.evalDynamic(recordValue) match {
                case Right(d)  => Right(d)
                case Left(err) => Left(MigrationError.ExpressionFailed(at.field(fieldName), err))
              }
            case other => Right(other) // Already non-optional, pass through
          }
          newValue.map(nv => fields.updated(fieldIdx, (name, nv)))
        }
      }
  }

  /**
   * Make a mandatory field optional by wrapping in Some.
   *
   * The value is wrapped as Variant("Some", value).
   *
   * Reverse: [[Mandate]] with a Fail expression (since we don't have a default)
   */
  final case class Optionalize(
    at: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {

    def reverse: MigrationAction =
      Mandate(at, fieldName, Resolved.Fail("Cannot reverse optionalize without default"))

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val fieldPath = at.field(fieldName)
      modifyAtPath(value, fieldPath) { fieldValue =>
        // Some is represented as Variant("Some", Record(Chunk(("value", inner))))
        val someRecord = DynamicValue.Record(("value", fieldValue))
        Right(DynamicValue.Variant("Some", someRecord))
      }
    }
  }

  /**
   * Change a field's primitive type using a converter.
   *
   * Both forward and reverse converters must be provided. The converters should
   * be [[Resolved.Convert]] expressions that specify the type conversion.
   *
   * Reverse: [[ChangeType]] with converters swapped
   */
  final case class ChangeType(
    at: DynamicOptic,
    fieldName: String,
    converter: Resolved,
    reverseConverter: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = ChangeType(at, fieldName, reverseConverter, converter)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val fieldPath = at.field(fieldName)
      modifyAtPath(value, fieldPath) { fieldValue =>
        converter.evalDynamic(fieldValue) match {
          case Right(result) => Right(result)
          case Left(err)     => Left(MigrationError.ExpressionFailed(fieldPath, err))
        }
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum/Variant Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename a case in an enum/variant type.
   *
   * Only affects values that match the `from` case name.
   *
   * Reverse: [[RenameCase]] with from/to swapped
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {

    def reverse: MigrationAction = RenameCase(at, to, from)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Variant(caseName, caseValue) if caseName == from =>
          Right(DynamicValue.Variant(to, caseValue))
        case other => Right(other) // Different case, no change
      }
  }

  /**
   * Transform the contents of a specific enum case.
   *
   * The nested actions are applied to the case value when the case matches.
   *
   * Reverse: [[TransformCase]] with reversed nested actions
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    caseActions: Vector[MigrationAction]
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformCase(
      at,
      caseName,
      caseActions.reverseIterator.map(_.reverse).toVector
    )

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Variant(name, caseValue) if name == caseName =>
          // Apply nested migration to the case value
          caseActions
            .foldLeft[Either[MigrationError, DynamicValue]](Right(caseValue)) {
              case (Right(v), action) => action.apply(v)
              case (left, _)          => left
            }
            .map(DynamicValue.Variant(name, _))
        case other => Right(other) // Different case, no change
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transform each element in a sequence/collection.
   *
   * Both forward and reverse transforms must be provided.
   *
   * Reverse: [[TransformElements]] with transforms swapped
   */
  final case class TransformElements(
    at: DynamicOptic,
    elementTransform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformElements(at, reverseTransform, elementTransform)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Sequence(elements) =>
          transformAll(elements.toVector, elementTransform, at).map(v => DynamicValue.Sequence(v: _*))
        case other =>
          Left(MigrationError.ExpectedSequence(at, other))
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Map Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transform each key in a map.
   *
   * Maps are represented as Record with string keys, so this transforms the
   * field names.
   *
   * Reverse: [[TransformKeys]] with transforms swapped
   */
  final case class TransformKeys(
    at: DynamicOptic,
    keyTransform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformKeys(at, reverseTransform, keyTransform)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Record(entries) =>
          transformMapKeys(entries.toVector, keyTransform, at).map(v => DynamicValue.Record(v: _*))
        case DynamicValue.Map(entries) =>
          transformMapKeysFullMap(entries.toVector, keyTransform, at).map(v => DynamicValue.Map(v: _*))
        case other =>
          Left(MigrationError.ExpectedMap(at, other))
      }
  }

  /**
   * Transform each value in a map.
   *
   * Reverse: [[TransformValues]] with transforms swapped
   */
  final case class TransformValues(
    at: DynamicOptic,
    valueTransform: Resolved,
    reverseTransform: Resolved
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformValues(at, reverseTransform, valueTransform)

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Record(entries) =>
          transformMapValues(entries.toVector, valueTransform, at).map(v => DynamicValue.Record(v: _*))
        case DynamicValue.Map(entries) =>
          transformMapValuesFullMap(entries.toVector, valueTransform, at).map(v => DynamicValue.Map(v: _*))
        case other =>
          Left(MigrationError.ExpectedMap(at, other))
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Helper Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Modify a record at the given path.
   */
  private def modifyRecord(
    value: DynamicValue,
    path: DynamicOptic
  )(
    f: Vector[(String, DynamicValue)] => Either[MigrationError, Vector[(String, DynamicValue)]]
  ): Either[MigrationError, DynamicValue] =
    modifyAtPath(value, path) {
      case DynamicValue.Record(fields) => f(fields.toVector).map(v => DynamicValue.Record(v: _*))
      case other                       => Left(MigrationError.ExpectedRecord(path, other))
    }

  /**
   * Modify a value at the given path, returning an error if modification fails.
   */
  private def modifyAtPath(
    value: DynamicValue,
    path: DynamicOptic
  )(
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (path.nodes.isEmpty) {
      // At root - apply directly
      f(value)
    } else {
      // Navigate to path and apply, tracking any error from f
      var capturedError: Option[MigrationError] = None
      val result                                = value.modifyOrFail(path) { case v =>
        f(v) match {
          case Right(result) => result
          case Left(err)     =>
            capturedError = Some(err)
            v // Return original but capture the error
        }
      }

      // Check if f failed
      capturedError match {
        case Some(err) => Left(err)
        case None      =>
          result match {
            case Right(result) =>
              val selection = value.get(path)
              if (selection.isEmpty) Left(MigrationError.PathNotFound(path))
              else Right(result)
            case Left(err) =>
              Left(MigrationError.General(path, err.message))
          }
      }
    }

  /**
   * Transform all elements in a vector using the given transform.
   */
  private def transformAll(
    elements: Vector[DynamicValue],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[DynamicValue]] = {
    var idx     = 0
    val len     = elements.length
    val results = Vector.newBuilder[DynamicValue]
    results.sizeHint(len)

    while (idx < len) {
      transform.evalDynamic(elements(idx)) match {
        case Right(v) =>
          results += v
          idx += 1
        case Left(err) =>
          return Left(MigrationError.ExpressionFailed(path.at(idx), err))
      }
    }
    Right(results.result())
  }

  /**
   * Transform map keys (for Record representation of maps).
   */
  private def transformMapKeys(
    entries: Vector[(String, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(String, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      val keyDV  = DynamicValue.Primitive(PrimitiveValue.String(k))
      transform.evalDynamic(keyDV) match {
        case Right(DynamicValue.Primitive(PrimitiveValue.String(newK))) =>
          results += ((newK, v))
          idx += 1
        case Right(other) =>
          results += ((other.toString, v))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.ExpressionFailed(path, err))
      }
    }
    Right(results.result())
  }

  /**
   * Transform map keys (for Map representation).
   */
  private def transformMapKeysFullMap(
    entries: Vector[(DynamicValue, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(DynamicValue, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      transform.evalDynamic(k) match {
        case Right(newK) =>
          results += ((newK, v))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.ExpressionFailed(path, err))
      }
    }
    Right(results.result())
  }

  /**
   * Transform map values (for Record representation).
   */
  private def transformMapValues(
    entries: Vector[(String, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(String, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      transform.evalDynamic(v) match {
        case Right(newV) =>
          results += ((k, newV))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.ExpressionFailed(path, err))
      }
    }
    Right(results.result())
  }

  /**
   * Transform map values (for Map representation).
   */
  private def transformMapValuesFullMap(
    entries: Vector[(DynamicValue, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic
  ): Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(DynamicValue, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      transform.evalDynamic(v) match {
        case Right(newV) =>
          results += ((k, newV))
          idx += 1
        case Left(err) =>
          return Left(MigrationError.ExpressionFailed(path, err))
      }
    }
    Right(results.result())
  }
}
