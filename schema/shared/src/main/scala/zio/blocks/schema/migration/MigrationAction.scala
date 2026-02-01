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

import zio.blocks.schema.DynamicOptic

/**
 * A pure, serializable migration action.
 *
 * All actions operate at a path represented by `DynamicOptic`. This enables:
 *   - Nested migrations like `_.address.street` -> `_.address.streetName`
 *   - Full serialization of migration paths
 *   - Introspection of migration structure
 *
 * The path-based design means migrations are specified as:
 * {{{
 *   Rename(DynamicOptic.root.field("address").field("street"), "streetName")
 * }}}
 *
 * All actions are:
 *   - Pure data (no functions, closures, or reflection)
 *   - Fully serializable
 *   - Structurally reversible
 */
sealed trait MigrationAction {

  /**
   * The path where this action is applied.
   */
  def at: DynamicOptic

  /**
   * Returns the structural reverse of this action.
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ===========================================================================
  // Record Field Operations
  // ===========================================================================

  /**
   * Add a new field with a default value at the specified path.
   *
   * The `at` path should point to the parent record, and the field will be
   * added to that record.
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: ResolvedExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, Some(default))
  }

  object AddField {
    /**
     * Legacy 2-argument constructor for backward compatibility.
     */
    def apply(fieldName: String, default: ResolvedExpr): AddField =
      AddField(DynamicOptic.root, fieldName, default)
  }

  /**
   * Drop a field from the record at the specified path.
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = defaultForReverse match {
      case Some(default) => AddField(at, fieldName, default)
      case None          => AddField(at, fieldName, ResolvedExpr.DefaultValue)
    }
  }

  object DropField {
    /**
     * Legacy 2-argument constructor for backward compatibility.
     */
    def apply(fieldName: String, defaultForReverse: Option[ResolvedExpr]): DropField =
      DropField(DynamicOptic.root, fieldName, defaultForReverse)
  }

  /**
   * Rename a field at the specified path.
   *
   * The `at` path points to the field being renamed (the full path including
   * the field name).
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      // Extract the old field name from the path
      val oldName = at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case _                                   => "unknown"
      }
      // Build path with new field name for reverse
      val reversePath = DynamicOptic(at.nodes.dropRight(1) :+ DynamicOptic.Node.Field(to))
      Rename(reversePath, oldName)
    }
  }

  /**
   * Transform a value at the specified path.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(
      at,
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  /**
   * Make an optional field mandatory at the specified path.
   */
  final case class Mandate(
    at: DynamicOptic,
    default: ResolvedExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Make a mandatory field optional at the specified path.
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, ResolvedExpr.DefaultValue)
  }

  /**
   * Change a field's type at the specified path.
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: ResolvedExpr
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(
      at,
      converter.reverse.getOrElse(ResolvedExpr.Identity)
    )
  }

  /**
   * Keep a field unchanged (explicit no-op for field tracking).
   */
  final case class Keep(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Keep(at)
  }

  // ===========================================================================
  // Enum/Variant Operations
  // ===========================================================================

  /**
   * Rename a variant case at the specified path.
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  object RenameCase {
    /**
     * Legacy 2-argument constructor for backward compatibility.
     */
    def apply(from: String, to: String): RenameCase =
      RenameCase(DynamicOptic.root, from, to)
  }

  /**
   * Transform a specific variant case's content.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  object TransformCase {
    /**
     * Legacy 2-argument constructor for backward compatibility.
     */
    def apply(caseName: String, actions: Vector[MigrationAction]): TransformCase =
      TransformCase(DynamicOptic.root, caseName, actions)
  }

  // ===========================================================================
  // Collection Operations
  // ===========================================================================

  /**
   * Transform all elements of a sequence at the specified path.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(
      at,
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  object TransformElements {
    /**
     * Legacy 2-argument constructor for backward compatibility.
     */
    def apply(transform: ResolvedExpr, reverseTransform: Option[ResolvedExpr]): TransformElements =
      TransformElements(DynamicOptic.root, transform, reverseTransform)
  }

  /**
   * Transform all keys of a map at the specified path.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(
      at,
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  object TransformKeys {
    /**
     * Legacy 2-argument constructor for backward compatibility.
     */
    def apply(transform: ResolvedExpr, reverseTransform: Option[ResolvedExpr]): TransformKeys =
      TransformKeys(DynamicOptic.root, transform, reverseTransform)
  }

  /**
   * Transform all values of a map at the specified path.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(
      at,
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  object TransformValues {
    /**
     * Legacy 2-argument constructor for backward compatibility.
     */
    def apply(transform: ResolvedExpr, reverseTransform: Option[ResolvedExpr]): TransformValues =
      TransformValues(DynamicOptic.root, transform, reverseTransform)
  }

  // ===========================================================================
  // Join/Split Operations
  // ===========================================================================

  /**
   * Join multiple fields into one at the specified path.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    targetFieldName: String,
    combiner: ResolvedExpr,
    splitterForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = splitterForReverse match {
      case Some(splitter) =>
        Split(at, DynamicOptic(at.nodes :+ DynamicOptic.Node.Field(targetFieldName)), sourcePaths, splitter, Some(combiner))
      case None =>
        Split(at, DynamicOptic(at.nodes :+ DynamicOptic.Node.Field(targetFieldName)), sourcePaths, ResolvedExpr.Identity, Some(combiner))
    }
  }

  /**
   * Split one field into multiple at the specified path.
   */
  final case class Split(
    at: DynamicOptic,
    sourcePath: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: ResolvedExpr,
    combinerForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      val targetFieldName = sourcePath.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case _                                   => "combined"
      }
      combinerForReverse match {
        case Some(combiner) => Join(at, targetPaths, targetFieldName, combiner, Some(splitter))
        case None           => Join(at, targetPaths, targetFieldName, ResolvedExpr.Identity, Some(splitter))
      }
    }
  }

  // ===========================================================================
  // Composite Operations
  // ===========================================================================

  /**
   * A sequence of actions to be applied in order.
   */
  final case class Sequence(
    at: DynamicOptic,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = Sequence(at, actions.reverse.map(_.reverse))
  }

  object Sequence {
    /**
     * Legacy 1-argument constructor for backward compatibility.
     */
    def apply(actions: Vector[MigrationAction]): Sequence =
      Sequence(DynamicOptic.root, actions)
  }

  /**
   * Identity migration - no changes.
   */
  final case class Identity(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = Identity(at)
  }

  /**
   * Companion providing legacy singleton access.
   */
  object Identity extends MigrationAction {
    val at: DynamicOptic           = DynamicOptic.root
    def reverse: MigrationAction   = this
    def apply(): Identity          = new Identity(DynamicOptic.root)
  }

  // ===========================================================================
  // Legacy Support - Backward compatible case classes for existing tests
  // These have the old string-based signatures but convert to path-based internally
  // ===========================================================================

  /**
   * Backward compatible RenameField (old API).
   * @deprecated Use Rename with DynamicOptic instead
   */
  final case class RenameField(
    from: String,
    to: String
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = RenameField(to, from)
  }

  /**
   * Backward compatible TransformField (old API).
   * @deprecated Use TransformValue with DynamicOptic instead
   */
  final case class TransformField(
    fieldName: String,
    transform: ResolvedExpr,
    reverseTransform: Option[ResolvedExpr]
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = TransformField(
      fieldName,
      reverseTransform.getOrElse(ResolvedExpr.Identity),
      transform.reverse
    )
  }

  /**
   * Backward compatible MandateField (old API).
   * @deprecated Use Mandate with DynamicOptic instead
   */
  final case class MandateField(
    fieldName: String,
    default: ResolvedExpr
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = OptionalizeField(fieldName)
  }

  /**
   * Backward compatible OptionalizeField (old API).
   * @deprecated Use Optionalize with DynamicOptic instead
   */
  final case class OptionalizeField(
    fieldName: String
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = MandateField(fieldName, ResolvedExpr.DefaultValue)
  }

  /**
   * Backward compatible ChangeFieldType (old API).
   * @deprecated Use ChangeType with DynamicOptic instead
   */
  final case class ChangeFieldType(
    fieldName: String,
    converter: ResolvedExpr
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = ChangeFieldType(
      fieldName,
      converter.reverse.getOrElse(ResolvedExpr.Identity)
    )
  }

  /**
   * Backward compatible KeepField (old API).
   * @deprecated Use Keep with DynamicOptic instead
   */
  final case class KeepField(
    fieldName: String
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = KeepField(fieldName)
  }

  /**
   * Backward compatible AtField (old API).
   * @deprecated Use path-based actions instead
   */
  final case class AtField(
    fieldName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = AtField(fieldName, actions.reverse.map(_.reverse))
  }

  /**
   * Backward compatible AtCase (old API).
   * @deprecated Use path-based actions instead
   */
  final case class AtCase(
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = AtCase(caseName, actions.reverse.map(_.reverse))
  }

  /**
   * Backward compatible AtElements (old API).
   * @deprecated Use path-based actions instead
   */
  final case class AtElements(
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = AtElements(actions.reverse.map(_.reverse))
  }

  /**
   * Backward compatible AtMapKeys (old API).
   * @deprecated Use path-based actions instead
   */
  final case class AtMapKeys(
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = AtMapKeys(actions.reverse.map(_.reverse))
  }

  /**
   * Backward compatible AtMapValues (old API).
   * @deprecated Use path-based actions instead
   */
  final case class AtMapValues(
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = AtMapValues(actions.reverse.map(_.reverse))
  }

  /**
   * Backward compatible JoinFields (old API).
   * @deprecated Use Join with DynamicOptic instead
   */
  final case class JoinFields(
    sourceFields: Vector[String],
    targetField: String,
    combiner: ResolvedExpr,
    splitterForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = splitterForReverse match {
      case Some(splitter) => SplitField(targetField, sourceFields, splitter, Some(combiner))
      case None           => SplitField(targetField, sourceFields, ResolvedExpr.Identity, Some(combiner))
    }
  }

  /**
   * Backward compatible SplitField (old API).
   * @deprecated Use Split with DynamicOptic instead
   */
  final case class SplitField(
    sourceField: String,
    targetFields: Vector[String],
    splitter: ResolvedExpr,
    combinerForReverse: Option[ResolvedExpr]
  ) extends MigrationAction {
    // Use root path so this is handled in applyActionAtRoot
    def at: DynamicOptic = DynamicOptic.root
    def reverse: MigrationAction = combinerForReverse match {
      case Some(combiner) => JoinFields(targetFields, sourceField, combiner, Some(splitter))
      case None           => JoinFields(targetFields, sourceField, ResolvedExpr.Identity, Some(splitter))
    }
  }

  // ===========================================================================
  // Smart Constructors (String-based API for backward compatibility)
  // ===========================================================================

  def addField(fieldName: String, default: ResolvedExpr): MigrationAction =
    AddField(DynamicOptic.root, fieldName, default)

  def dropField(fieldName: String): MigrationAction =
    DropField(DynamicOptic.root, fieldName, None)

  def dropField(fieldName: String, defaultForReverse: ResolvedExpr): MigrationAction =
    DropField(DynamicOptic.root, fieldName, Some(defaultForReverse))

  def renameField(from: String, to: String): MigrationAction =
    RenameField(from, to)

  def transformField(fieldName: String, transform: ResolvedExpr): MigrationAction =
    TransformField(fieldName, transform, None)

  def transformField(fieldName: String, transform: ResolvedExpr, reverseTransform: ResolvedExpr): MigrationAction =
    TransformField(fieldName, transform, Some(reverseTransform))

  def mandateField(fieldName: String, default: ResolvedExpr): MigrationAction =
    MandateField(fieldName, default)

  def optionalizeField(fieldName: String): MigrationAction =
    OptionalizeField(fieldName)

  def changeFieldType(fieldName: String, converter: ResolvedExpr): MigrationAction =
    ChangeFieldType(fieldName, converter)

  def keepField(fieldName: String): MigrationAction =
    KeepField(fieldName)

  def renameCase(from: String, to: String): MigrationAction =
    RenameCase(DynamicOptic.root, from, to)

  def transformCase(caseName: String)(actions: MigrationAction*): MigrationAction =
    TransformCase(DynamicOptic.root, caseName, actions.toVector)

  def transformElements(transform: ResolvedExpr): MigrationAction =
    TransformElements(DynamicOptic.root, transform, None)

  def transformKeys(transform: ResolvedExpr): MigrationAction =
    TransformKeys(DynamicOptic.root, transform, None)

  def transformValues(transform: ResolvedExpr): MigrationAction =
    TransformValues(DynamicOptic.root, transform, None)

  def identity: MigrationAction = Identity

  def sequence(actions: MigrationAction*): MigrationAction =
    Sequence(DynamicOptic.root, actions.toVector)

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

  def joinFields(sourceFields: Vector[String], targetField: String, combiner: ResolvedExpr): MigrationAction =
    JoinFields(sourceFields, targetField, combiner, None)

  def splitField(sourceField: String, targetFields: Vector[String], splitter: ResolvedExpr): MigrationAction =
    SplitField(sourceField, targetFields, splitter, None)
}
