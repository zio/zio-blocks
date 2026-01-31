/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.schema.migration

/**
 * A pure, serializable migration action.
 *
 * MigrationAction forms a hierarchical ADT where:
 *   - The top level encodes structural depth (via AtField, AtCase, etc.)
 *   - The leaf level encodes actual transformations (AddField, DropField, etc.)
 *
 * This "lists of lists" structure enables nested migrations like:
 * {{{
 *   AtField("address", Vector(
 *     RenameField("street", "streetName"),
 *     AddField("zipCode", Literal("00000"))
 *   ))
 * }}}
 *
 * All actions are:
 *   - Pure data (no functions, closures, or reflection)
 *   - Fully serializable
 *   - Structurally reversible
 */
sealed trait MigrationAction {

  /**
   * Returns the structural reverse of this action. Every migration action has a
   * reverse.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ===========================================================================
  // Record Field Operations (leaf-level transformations)
  // ===========================================================================

  /**
   * Add a new field with a default value.
   *
   * Forward: Adds field with default Reverse: Drops the field
   */
  final case class AddField(
    fieldName: String,
    default: ResolvedExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(fieldName, Some(default))
  }

  /**
   * Drop a field from the record.
   *
   * Forward: Removes the field Reverse: Adds the field back (requires
   * defaultForReverse)
   */
  final case class DropField(
    fieldName: String,
    defaultForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = defaultForReverse match {
      case Some(default) => AddField(fieldName, default)
      case None          => AddField(fieldName, ResolvedExpr.DefaultValue)
    }
  }

  /**
   * Rename a field.
   *
   * Forward: from -> to Reverse: to -> from
   */
  final case class RenameField(
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameField(to, from)
  }

  /**
   * Transform a field's value using an expression.
   *
   * Forward: Apply transform to field value Reverse: Apply reverse transform
   * (if available)
   */
  final case class TransformField(
    fieldName: String,
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformField(
      fieldName,
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  /**
   * Make an optional field mandatory.
   *
   * Forward: Option[A] -> A (with default for None) Reverse: A -> Option[A]
   * (wraps in Some)
   */
  final case class MandateField(
    fieldName: String,
    default: ResolvedExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = OptionalizeField(fieldName)
  }

  /**
   * Make a mandatory field optional.
   *
   * Forward: A -> Option[A] (wraps in Some) Reverse: Option[A] -> A (requires
   * default)
   */
  final case class OptionalizeField(
    fieldName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = MandateField(fieldName, ResolvedExpr.DefaultValue)
  }

  /**
   * Change a field's primitive type.
   *
   * Forward: Apply converter Reverse: Apply reverse converter
   */
  final case class ChangeFieldType(
    fieldName: String,
    converter: ResolvedExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeFieldType(
      fieldName,
      converter.reverse.getOrElse(ResolvedExpr.Identity)
    )
  }

  /**
   * Keep a field unchanged (explicit no-op for field tracking).
   */
  final case class KeepField(fieldName: String) extends MigrationAction {
    def reverse: MigrationAction = KeepField(fieldName)
  }

  // ===========================================================================
  // Hierarchical Nesting Operations (for nested structures)
  // ===========================================================================

  /**
   * Apply nested actions to a specific field.
   *
   * This is the KEY innovation for nested migrations. Enables migrations like:
   * _.address.street -> _.address.streetName
   *
   * {{{
   *   AtField("address", Vector(
   *     RenameField("street", "streetName")
   *   ))
   * }}}
   */
  final case class AtField(
    fieldName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = AtField(fieldName, actions.reverse.map(_.reverse))
  }

  /**
   * Apply nested actions to a specific variant case.
   */
  final case class AtCase(
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = AtCase(caseName, actions.reverse.map(_.reverse))
  }

  /**
   * Apply nested actions to all elements of a sequence.
   */
  final case class AtElements(
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = AtElements(actions.reverse.map(_.reverse))
  }

  /**
   * Apply nested actions to all keys of a map.
   */
  final case class AtMapKeys(
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = AtMapKeys(actions.reverse.map(_.reverse))
  }

  /**
   * Apply nested actions to all values of a map.
   */
  final case class AtMapValues(
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = AtMapValues(actions.reverse.map(_.reverse))
  }

  // ===========================================================================
  // Enum/Variant Operations
  // ===========================================================================

  /**
   * Rename a variant case.
   */
  final case class RenameCase(
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(to, from)
  }

  /**
   * Transform a specific variant case's content.
   */
  final case class TransformCase(
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(caseName, actions.reverse.map(_.reverse))
  }

  // ===========================================================================
  // Collection Operations
  // ===========================================================================

  /**
   * Transform all elements of a sequence using an expression.
   */
  final case class TransformElements(
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  /**
   * Transform all keys of a map.
   */
  final case class TransformKeys(
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  /**
   * Transform all values of a map.
   */
  final case class TransformValues(
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  // ===========================================================================
  // Join/Split Operations
  // ===========================================================================

  /**
   * Join multiple fields into one.
   *
   * Example: firstName + lastName -> fullName
   */
  final case class JoinFields(
    sourceFields: Vector[String],
    targetField: String,
    combiner: ResolvedExpr,
    splitterForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = splitterForReverse match {
      case Some(splitter) => SplitField(targetField, sourceFields, splitter, Some(combiner))
      case None           => SplitField(targetField, sourceFields, ResolvedExpr.Identity, Some(combiner))
    }
  }

  /**
   * Split one field into multiple.
   *
   * Example: fullName -> firstName + lastName
   */
  final case class SplitField(
    sourceField: String,
    targetFields: Vector[String],
    splitter: ResolvedExpr,
    combinerForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = combinerForReverse match {
      case Some(combiner) => JoinFields(targetFields, sourceField, combiner, Some(splitter))
      case None           => JoinFields(targetFields, sourceField, ResolvedExpr.Identity, Some(splitter))
    }
  }

  // ===========================================================================
  // Composite Operations
  // ===========================================================================

  /**
   * A sequence of actions to be applied in order. This is the root container
   * for migrations.
   */
  final case class Sequence(actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = Sequence(actions.reverse.map(_.reverse))
  }

  /**
   * Identity migration - no changes.
   */
  case object Identity extends MigrationAction {
    def reverse: MigrationAction = Identity
  }

  // ===========================================================================
  // Smart Constructors
  // ===========================================================================

  def addField(fieldName: String, default: ResolvedExpr): MigrationAction =
    AddField(fieldName, default)

  def dropField(fieldName: String): MigrationAction =
    DropField(fieldName, None)

  def dropField(fieldName: String, defaultForReverse: ResolvedExpr): MigrationAction =
    DropField(fieldName, Some(defaultForReverse))

  def renameField(from: String, to: String): MigrationAction =
    RenameField(from, to)

  def transformField(fieldName: String, transform: ResolvedExpr): MigrationAction =
    TransformField(fieldName, transform, None)

  def transformField(fieldName: String, transform: ResolvedExpr, reverse: ResolvedExpr): MigrationAction =
    TransformField(fieldName, transform, Some(reverse))

  def mandateField(fieldName: String, default: ResolvedExpr): MigrationAction =
    MandateField(fieldName, default)

  def optionalizeField(fieldName: String): MigrationAction =
    OptionalizeField(fieldName)

  def changeFieldType(fieldName: String, converter: ResolvedExpr): MigrationAction =
    ChangeFieldType(fieldName, converter)

  def keepField(fieldName: String): MigrationAction =
    KeepField(fieldName)

  def atField(fieldName: String)(actions: MigrationAction*): MigrationAction =
    AtField(fieldName, actions.toVector)

  def atCase(caseName: String)(actions: MigrationAction*): MigrationAction =
    AtCase(caseName, actions.toVector)

  def atElements(actions: MigrationAction*): MigrationAction =
    AtElements(actions.toVector)

  def atMapKeys(actions: MigrationAction*): MigrationAction =
    AtMapKeys(actions.toVector)

  def atMapValues(actions: MigrationAction*): MigrationAction =
    AtMapValues(actions.toVector)

  def renameCase(from: String, to: String): MigrationAction =
    RenameCase(from, to)

  def transformCase(caseName: String)(actions: MigrationAction*): MigrationAction =
    TransformCase(caseName, actions.toVector)

  def transformElements(transform: ResolvedExpr): MigrationAction =
    TransformElements(transform, None)

  def transformKeys(transform: ResolvedExpr): MigrationAction =
    TransformKeys(transform, None)

  def transformValues(transform: ResolvedExpr): MigrationAction =
    TransformValues(transform, None)

  def joinFields(sourceFields: Vector[String], targetField: String, combiner: ResolvedExpr): MigrationAction =
    JoinFields(sourceFields, targetField, combiner, None)

  def splitField(sourceField: String, targetFields: Vector[String], splitter: ResolvedExpr): MigrationAction =
    SplitField(sourceField, targetFields, splitter, None)

  def sequence(actions: MigrationAction*): MigrationAction =
    Sequence(actions.toVector)

  def identity: MigrationAction = Identity
}
