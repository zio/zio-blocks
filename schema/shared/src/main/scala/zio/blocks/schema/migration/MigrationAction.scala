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
 *   - `applyWithRoot`: The transformation with access to the root document
 *
 * Actions can be composed into a [[DynamicMigration]] and applied to transform
 * data between schema versions.
 */
sealed trait MigrationAction {

  /** The path where this action operates */
  def at: DynamicOptic

  /** Structural reverse of this action (for bidirectional migrations) */
  def reverse: MigrationAction

  /**
   * Apply this action to a DynamicValue.
   *
   * Delegates to [[applyWithRoot]] using the value as its own root, maintaining
   * backward compatibility.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    applyWithRoot(value, value)

  /**
   * Apply this action with access to the root document.
   *
   * This method enables expressions like `RootAccess` to access values from
   * anywhere in the document, regardless of the current action's path. This is
   * essential for cross-branch operations that need to combine fields from
   * different branches of the document tree.
   *
   * @param value
   *   The value at the action's path (local context)
   * @param root
   *   The root document (for RootAccess expressions)
   * @return
   *   Right with the transformed value, or Left with an error
   */
  def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue]

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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyRecord(value, at) { fields =>
        // Pass the current record to evalDynamicWithRoot so expressions like FieldAccess work
        val recordValue = DynamicValue.Record(fields: _*)
        default.evalDynamicWithRoot(recordValue, root) match {
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] = {
      val fieldPath = at.field(fieldName)
      modifyAtPath(value, fieldPath) { fieldValue =>
        transform.evalDynamicWithRoot(fieldValue, root) match {
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
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
                case scala.None        =>
                  Left(
                    MigrationError.General(at.field(fieldName), "Malformed Option: Some variant missing 'value' field")
                  )
              }
            case DynamicValue.Variant("Some", inner) =>
              // Fallback for simple representation
              Right(inner)
            case DynamicValue.Variant("None", _) =>
              default.evalDynamicWithRoot(recordValue, root) match {
                case Right(d)  => Right(d)
                case Left(err) => Left(MigrationError.ExpressionFailed(at.field(fieldName), err))
              }
            case DynamicValue.Null =>
              default.evalDynamicWithRoot(recordValue, root) match {
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] = {
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] = {
      val fieldPath = at.field(fieldName)
      modifyAtPath(value, fieldPath) { fieldValue =>
        converter.evalDynamicWithRoot(fieldValue, root) match {
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Variant(name, caseValue) if name == caseName =>
          // Apply nested migration to the case value, passing root for cross-branch access
          caseActions
            .foldLeft[Either[MigrationError, DynamicValue]](Right(caseValue)) {
              case (Right(v), action) => action.applyWithRoot(v, root)
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Sequence(elements) =>
          transformAllWithRoot(elements.toVector, elementTransform, at, root).map(v => DynamicValue.Sequence(v: _*))
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Record(entries) =>
          transformMapKeysWithRoot(entries.toVector, keyTransform, at, root).map(v => DynamicValue.Record(v: _*))
        case DynamicValue.Map(entries) =>
          transformMapKeysFullMapWithRoot(entries.toVector, keyTransform, at, root).map(v => DynamicValue.Map(v: _*))
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

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyAtPath(value, at) {
        case DynamicValue.Record(entries) =>
          transformMapValuesWithRoot(entries.toVector, valueTransform, at, root).map(v => DynamicValue.Record(v: _*))
        case DynamicValue.Map(entries) =>
          transformMapValuesFullMapWithRoot(entries.toVector, valueTransform, at, root).map(v =>
            DynamicValue.Map(v: _*)
          )
        case other =>
          Left(MigrationError.ExpectedMap(at, other))
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Nested Migration Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Apply a nested migration to a record field.
   *
   * The nested actions are applied sequentially to the field's value within the
   * record at `at`. This provides structurally nested migrations where the
   * nesting is visible in the serialized representation as a tree rather than a
   * flat list of path-prefixed actions.
   *
   * The root document is threaded through to all nested actions, enabling
   * cross-branch expressions (e.g., `RootAccess`) within nested contexts.
   *
   * Reverse: [[TransformField]] with reversed nested actions
   */
  final case class TransformField(
    at: DynamicOptic,
    fieldName: String,
    fieldActions: Vector[MigrationAction]
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformField(
      at,
      fieldName,
      fieldActions.reverseIterator.map(_.reverse).toVector
    )

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyRecord(value, at) { fields =>
        val fieldIdx = fields.indexWhere(_._1 == fieldName)
        if (fieldIdx < 0) {
          Left(MigrationError.FieldNotFound(at, fieldName))
        } else {
          val (name, fieldValue) = fields(fieldIdx)
          fieldActions
            .foldLeft[Either[MigrationError, DynamicValue]](Right(fieldValue)) {
              case (Right(v), action) => action.applyWithRoot(v, root)
              case (left, _)          => left
            }
            .map(newValue => fields.updated(fieldIdx, (name, newValue)))
        }
      }
  }

  /**
   * Apply a nested migration to each element in a sequence field.
   *
   * The nested actions are applied sequentially to each element of the sequence
   * stored at `fieldName` within the record at `at`. This enables structural
   * migrations on collection elements where each element undergoes a full
   * sub-migration.
   *
   * Reverse: [[TransformEachElement]] with reversed nested actions
   */
  final case class TransformEachElement(
    at: DynamicOptic,
    fieldName: String,
    elementActions: Vector[MigrationAction]
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformEachElement(
      at,
      fieldName,
      elementActions.reverseIterator.map(_.reverse).toVector
    )

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyRecord(value, at) { fields =>
        val fieldIdx = fields.indexWhere(_._1 == fieldName)
        if (fieldIdx < 0) {
          Left(MigrationError.FieldNotFound(at, fieldName))
        } else {
          val (name, fieldValue) = fields(fieldIdx)
          fieldValue match {
            case DynamicValue.Sequence(elements) =>
              applyActionsToAll(elements.toVector, elementActions, at.field(fieldName), root)
                .map(newElems => fields.updated(fieldIdx, (name, DynamicValue.Sequence(newElems: _*))))
            case other =>
              Left(MigrationError.ExpectedSequence(at.field(fieldName), other))
          }
        }
      }
  }

  /**
   * Apply a nested migration to each value in a map field.
   *
   * The nested actions are applied sequentially to each value of the map stored
   * at `fieldName` within the record at `at`. Maps are represented as
   * [[DynamicValue.Record]] or [[DynamicValue.Map]], and both representations
   * are handled.
   *
   * Reverse: [[TransformEachMapValue]] with reversed nested actions
   */
  final case class TransformEachMapValue(
    at: DynamicOptic,
    fieldName: String,
    valueActions: Vector[MigrationAction]
  ) extends MigrationAction {

    def reverse: MigrationAction = TransformEachMapValue(
      at,
      fieldName,
      valueActions.reverseIterator.map(_.reverse).toVector
    )

    def prefixPath(prefix: DynamicOptic): MigrationAction = copy(at = prefix(at))

    def applyWithRoot(value: DynamicValue, root: DynamicValue): Either[MigrationError, DynamicValue] =
      modifyRecord(value, at) { fields =>
        val fieldIdx = fields.indexWhere(_._1 == fieldName)
        if (fieldIdx < 0) {
          Left(MigrationError.FieldNotFound(at, fieldName))
        } else {
          val (name, fieldValue) = fields(fieldIdx)
          fieldValue match {
            case DynamicValue.Record(entries) =>
              applyActionsToMapValues(entries.toVector, valueActions, at.field(fieldName), root)
                .map(newEntries => fields.updated(fieldIdx, (name, DynamicValue.Record(newEntries: _*))))
            case DynamicValue.Map(entries) =>
              applyActionsToFullMapValues(entries.toVector, valueActions, at.field(fieldName), root)
                .map(newEntries => fields.updated(fieldIdx, (name, DynamicValue.Map(newEntries: _*))))
            case other =>
              Left(MigrationError.ExpectedMap(at.field(fieldName), other))
          }
        }
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
   *
   * Note: Uses a mutable variable to capture errors because
   * DynamicValue.modifyOrFail expects a PartialFunction that returns the
   * modified value directly, not an Either. We work around this by returning
   * the original value on error while capturing the error in a mutable
   * variable, then checking it after modification completes.
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
   * Transform all elements in a vector using the given transform with root
   * context.
   */
  private def transformAllWithRoot(
    elements: Vector[DynamicValue],
    transform: Resolved,
    path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[DynamicValue]] = {
    var idx     = 0
    val len     = elements.length
    val results = Vector.newBuilder[DynamicValue]
    results.sizeHint(len)

    while (idx < len) {
      transform.evalDynamicWithRoot(elements(idx), root) match {
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
   * Transform map keys (for Record representation of maps) with root context.
   */
  private def transformMapKeysWithRoot(
    entries: Vector[(String, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(String, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      val keyDV  = DynamicValue.Primitive(PrimitiveValue.String(k))
      transform.evalDynamicWithRoot(keyDV, root) match {
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
   * Transform map keys (for Map representation) with root context.
   */
  private def transformMapKeysFullMapWithRoot(
    entries: Vector[(DynamicValue, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(DynamicValue, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      transform.evalDynamicWithRoot(k, root) match {
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
   * Transform map values (for Record representation) with root context.
   */
  private def transformMapValuesWithRoot(
    entries: Vector[(String, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(String, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      transform.evalDynamicWithRoot(v, root) match {
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
   * Transform map values (for Map representation) with root context.
   */
  private def transformMapValuesFullMapWithRoot(
    entries: Vector[(DynamicValue, DynamicValue)],
    transform: Resolved,
    path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(DynamicValue, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v) = entries(idx)
      transform.evalDynamicWithRoot(v, root) match {
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
   * Apply a sequence of migration actions to each element of a vector.
   */
  private def applyActionsToAll(
    elements: Vector[DynamicValue],
    actions: Vector[MigrationAction],
    @annotation.unused path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[DynamicValue]] = {
    var idx     = 0
    val len     = elements.length
    val results = Vector.newBuilder[DynamicValue]
    results.sizeHint(len)

    while (idx < len) {
      val elem                                          = elements(idx)
      var current: Either[MigrationError, DynamicValue] = Right(elem)
      var actIdx                                        = 0
      val actLen                                        = actions.length

      while (actIdx < actLen && current.isRight) {
        current = current.flatMap(v => actions(actIdx).applyWithRoot(v, root))
        actIdx += 1
      }

      current match {
        case Right(v) =>
          results += v
          idx += 1
        case Left(err) =>
          return Left(err)
      }
    }
    Right(results.result())
  }

  /**
   * Apply a sequence of migration actions to each value in a Record-encoded
   * map.
   */
  private def applyActionsToMapValues(
    entries: Vector[(String, DynamicValue)],
    actions: Vector[MigrationAction],
    @annotation.unused path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[(String, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(String, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v)                                        = entries(idx)
      var current: Either[MigrationError, DynamicValue] = Right(v)
      var actIdx                                        = 0
      val actLen                                        = actions.length

      while (actIdx < actLen && current.isRight) {
        current = current.flatMap(cv => actions(actIdx).applyWithRoot(cv, root))
        actIdx += 1
      }

      current match {
        case Right(newV) =>
          results += ((k, newV))
          idx += 1
        case Left(err) =>
          return Left(err)
      }
    }
    Right(results.result())
  }

  /**
   * Apply a sequence of migration actions to each value in a Map-encoded map.
   */
  private def applyActionsToFullMapValues(
    entries: Vector[(DynamicValue, DynamicValue)],
    actions: Vector[MigrationAction],
    @annotation.unused path: DynamicOptic,
    root: DynamicValue
  ): Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = {
    var idx     = 0
    val len     = entries.length
    val results = Vector.newBuilder[(DynamicValue, DynamicValue)]
    results.sizeHint(len)

    while (idx < len) {
      val (k, v)                                        = entries(idx)
      var current: Either[MigrationError, DynamicValue] = Right(v)
      var actIdx                                        = 0
      val actLen                                        = actions.length

      while (actIdx < actLen && current.isRight) {
        current = current.flatMap(cv => actions(actIdx).applyWithRoot(cv, root))
        actIdx += 1
      }

      current match {
        case Right(newV) =>
          results += ((k, newV))
          idx += 1
        case Left(err) =>
          return Left(err)
      }
    }
    Right(results.result())
  }

  /**
   * Schema for MigrationAction enabling serialization to JSON, Protobuf, etc.
   */
  implicit def schema: zio.blocks.schema.Schema[MigrationAction] =
    MigrationSchemas.migrationActionSchema
}
