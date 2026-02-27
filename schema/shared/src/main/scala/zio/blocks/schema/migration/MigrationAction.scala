package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A pure, serializable migration action that operates on [[DynamicValue]] via
 * path-based [[DynamicOptic]] targeting. These actions contain no user
 * functions, closures, reflection, or code generation, enabling serialization,
 * registry storage, and dynamic application.
 *
 * Actions form the untyped core of the migration system. The typed
 * [[Migration]] wrapper provides compile-time safety on top of these.
 */
sealed trait MigrationAction {

  /**
   * Returns the structural reverse of this action. For example, the reverse of
   * `AddField` is `DropField` and vice versa.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ─── Record Operations ──────────────────────────────────────────────

  /**
   * Adds a field at the specified path with a default value.
   *
   * @param target
   *   path to the new field location
   * @param defaultValue
   *   the default value for the new field
   */
  final case class AddField(
    target: DynamicOptic,
    defaultValue: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(target, defaultValue)
  }

  /**
   * Drops a field at the specified path. The default value is used when
   * reversing the migration.
   *
   * @param source
   *   path to the field to remove
   * @param defaultForReverse
   *   the default value used if this action is reversed
   */
  final case class DropField(
    source: DynamicOptic,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(source, defaultForReverse)
  }

  /**
   * Renames a field from one name to another.
   *
   * @param sourcePath
   *   path to the parent record
   * @param fromName
   *   the old field name
   * @param toName
   *   the new field name
   */
  final case class Rename(
    sourcePath: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Rename(sourcePath, toName, fromName)
  }

  /**
   * Transforms a value at a path using a pure, serializable expression
   * represented as a sequence of [[DynamicValue]] operations.
   *
   * @param source
   *   path to the source value
   * @param target
   *   path to the target value
   * @param transform
   *   the transformation as a DynamicValue expression
   * @param reverseTransform
   *   the reverse transformation, if available
   */
  final case class TransformValue(
    source: DynamicOptic,
    target: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform]
  ) extends MigrationAction {
    def reverse: MigrationAction = reverseTransform match {
      case Some(rev) => TransformValue(target, source, rev, Some(transform))
      case None      => TransformValue(target, source, transform, None)
    }
  }

  /**
   * Makes an optional field mandatory by providing a default value for None
   * cases.
   *
   * @param source
   *   path to the optional field
   * @param target
   *   path to the mandatory field
   * @param defaultValue
   *   default value when source is None/Null
   */
  final case class Mandate(
    source: DynamicOptic,
    target: DynamicOptic,
    defaultValue: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(target, source)
  }

  /**
   * Makes a mandatory field optional (wraps it in Option/nullable).
   *
   * @param source
   *   path to the mandatory field
   * @param target
   *   path to the optional field
   */
  final case class Optionalize(
    source: DynamicOptic,
    target: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(target, source, DynamicValue.Null)
  }

  /**
   * Changes the type of a field using a serializable conversion specification.
   *
   * @param source
   *   path to the source field
   * @param target
   *   path to the target field
   * @param converter
   *   the type conversion specification
   * @param reverseConverter
   *   the reverse conversion specification, if available
   */
  final case class ChangeType(
    source: DynamicOptic,
    target: DynamicOptic,
    converter: DynamicValueTransform,
    reverseConverter: Option[DynamicValueTransform]
  ) extends MigrationAction {
    def reverse: MigrationAction = reverseConverter match {
      case Some(rev) => ChangeType(target, source, rev, Some(converter))
      case None      => ChangeType(target, source, converter, None)
    }
  }

  /**
   * Joins multiple source fields into a single target field using a combiner.
   * The combiner receives the parent record and produces the combined value.
   * Source fields are removed and the target field is added.
   *
   * @param at
   *   path to the parent record
   * @param sourceFields
   *   names of the fields to combine (relative to the record at `at`)
   * @param targetField
   *   name of the new field for the combined value
   * @param combiner
   *   transformation that receives the parent record and produces the combined
   *   value (e.g. [[DynamicValueTransform.ConcatFields]])
   * @param reverseTransform
   *   optional reverse transformation that receives the combined value and
   *   produces a Record with the source fields
   */
  final case class Join(
    at: DynamicOptic,
    sourceFields: Vector[String],
    targetField: String,
    combiner: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform]
  ) extends MigrationAction {
    def reverse: MigrationAction = reverseTransform match {
      case Some(rev) => Split(at, targetField, sourceFields, rev, Some(combiner))
      case None      => Split(at, targetField, sourceFields, combiner, None)
    }
  }

  /**
   * Splits a single source field into multiple target fields using a splitter.
   * The splitter receives the source field value and produces a Record
   * containing the target fields. The source field is removed and the target
   * fields are added.
   *
   * @param at
   *   path to the parent record
   * @param sourceField
   *   name of the field to split (relative to the record at `at`)
   * @param targetFields
   *   names of the new fields for the split values
   * @param splitter
   *   transformation that receives the source field value and produces a
   *   Record with the target fields (e.g.
   *   [[DynamicValueTransform.SplitString]])
   * @param reverseTransform
   *   optional reverse transformation that receives the parent record and
   *   produces the combined value
   */
  final case class Split(
    at: DynamicOptic,
    sourceField: String,
    targetFields: Vector[String],
    splitter: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform]
  ) extends MigrationAction {
    def reverse: MigrationAction = reverseTransform match {
      case Some(rev) => Join(at, targetFields, sourceField, rev, Some(splitter))
      case None      => Join(at, targetFields, sourceField, splitter, None)
    }
  }

  // ─── Enum Operations ────────────────────────────────────────────────

  /**
   * Renames a case in a variant/enum.
   *
   * @param path
   *   path to the variant
   * @param fromName
   *   the old case name
   * @param toName
   *   the new case name
   */
  final case class RenameCase(
    path: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(path, toName, fromName)
  }

  /**
   * Transforms a specific case of a variant by applying sub-actions to its
   * value.
   *
   * @param path
   *   path to the variant
   * @param caseName
   *   the case to transform
   * @param targetCaseName
   *   the new case name after transformation
   * @param actions
   *   the migration actions to apply to the case value
   */
  final case class TransformCase(
    path: DynamicOptic,
    caseName: String,
    targetCaseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(
      path,
      targetCaseName,
      caseName,
      actions.reverse.map(_.reverse)
    )
  }

  // ─── Collection/Map Operations ──────────────────────────────────────

  /**
   * Transforms elements of a sequence at the given path.
   *
   * @param path
   *   path to the sequence
   * @param transform
   *   the transformation to apply to each element
   * @param reverseTransform
   *   the reverse transformation, if available
   */
  final case class TransformElements(
    path: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform]
  ) extends MigrationAction {
    def reverse: MigrationAction = reverseTransform match {
      case Some(rev) => TransformElements(path, rev, Some(transform))
      case None      => TransformElements(path, transform, None)
    }
  }

  /**
   * Transforms keys of a map at the given path.
   *
   * @param path
   *   path to the map
   * @param transform
   *   the transformation to apply to each key
   * @param reverseTransform
   *   the reverse transformation, if available
   */
  final case class TransformKeys(
    path: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform]
  ) extends MigrationAction {
    def reverse: MigrationAction = reverseTransform match {
      case Some(rev) => TransformKeys(path, rev, Some(transform))
      case None      => TransformKeys(path, transform, None)
    }
  }

  /**
   * Transforms values of a map at the given path.
   *
   * @param path
   *   path to the map
   * @param transform
   *   the transformation to apply to each value
   * @param reverseTransform
   *   the reverse transformation, if available
   */
  final case class TransformValues(
    path: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform]
  ) extends MigrationAction {
    def reverse: MigrationAction = reverseTransform match {
      case Some(rev) => TransformValues(path, rev, Some(transform))
      case None      => TransformValues(path, transform, None)
    }
  }
}
