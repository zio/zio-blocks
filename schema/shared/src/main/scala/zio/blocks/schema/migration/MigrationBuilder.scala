package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A type-safe builder for creating migrations between schema versions.
 *
 * The builder provides a fluent API with compile-time validation in Scala 3 and
 * string-based API in Scala 2.
 *
 * Scala 3 Example:
 * {{{
 *   val migration = Migration.builder[PersonV0, PersonV1]
 *     .renameField(_.firstName, _.fullName)  // Type-safe!
 *     .addField("country", "USA")
 *     .build
 * }}}
 *
 * Scala 2 Example:
 * {{{
 *   val migration = Migration.builder[PersonV0, PersonV1]
 *     .renameField("firstName", "fullName")  // String-based
 *     .addField("country", "USA")
 *     .build
 * }}}
 *
 * @tparam A
 *   The source schema type
 * @tparam B
 *   The target schema type
 */
final class MigrationBuilder[A, B](
  val actions: Vector[MigrationAction]
)(implicit val fromSchema: Schema[A], val toSchema: Schema[B])
    extends MigrationBuilderPlatform[A, B] { self =>

  private def copy(actions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](actions)(fromSchema, toSchema)

  /**
   * Drop a field.
   */
  def dropField(name: String): MigrationBuilder[A, B] =
    dropField(DynamicOptic.root.field(name))

  /**
   * Drop a field at the given optic path.
   */
  def dropField(at: DynamicOptic): MigrationBuilder[A, B] = {
    val default = fromSchema.get(at).flatMap(r => r.getDefaultValue.map(v => r.asInstanceOf[Reflect.Bound[Any]].toDynamicValue(v)))
    copy(actions = actions :+ MigrationAction.DropField(at, default))
  }

  /**
   * Rename a field.
   */
  def renameField(oldName: String, newName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(DynamicOptic.root.field(oldName), newName))

  /**
   * Rename a field at the given optic path.
   */
  def renameField(at: DynamicOptic, newName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(at, newName))

  /**
   * Add a new field with a constant default value.
   */
  def addField[T](name: String, defaultValue: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    addField(name, schema.toDynamicValue(defaultValue))

  /**
   * Add a new field with a constant default value.
   */
  def addField(name: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(DynamicOptic.root.field(name), defaultValue))

  /**
   * Add a new field at the given optic path with a constant default value.
   */
  def addField(at: DynamicOptic, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(at, defaultValue))

  /**
   * Make a field optional.
   */
  def optionalize(name: String): MigrationBuilder[A, B] =
    optionalize(DynamicOptic.root.field(name))

  /**
   * Make a field at the given optic path optional.
   */
  def optionalize(at: DynamicOptic): MigrationBuilder[A, B] = {
    val default = fromSchema.get(at).flatMap(r => r.getDefaultValue.map(v => r.asInstanceOf[Reflect.Bound[Any]].toDynamicValue(v)))
    copy(actions = actions :+ MigrationAction.Optionalize(at, default))
  }

  /**
   * Make an optional field required by extracting from Some or using a default.
   */
  def mandate[T](name: String, defaultValue: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    mandate(name, schema.toDynamicValue(defaultValue))

  /**
   * Make an optional field required by extracting from Some or using a default.
   */
  def mandate(name: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(DynamicOptic.root.field(name), defaultValue))

  /**
   * Make an optional field at the given optic path required by extracting from
   * Some or using a default.
   */
  def mandate(at: DynamicOptic, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(at, defaultValue))

  /**
   * Rename a case in an enum/variant.
   */
  def renameCase(oldName: String, newName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(DynamicOptic.root, oldName, newName))

  /**
   * Rename a case in an enum/variant at the given optic path.
   */
  def renameCase(at: DynamicOptic, oldName: String, newName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(at, oldName, newName))

  /**
   * Remove a case from an enum/variant.
   */
  def removeCase(name: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RemoveCase(DynamicOptic.root, name))

  /**
   * Remove a case from an enum/variant at the given optic path.
   */
  def removeCase(at: DynamicOptic, name: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RemoveCase(at, name))

  /**
   * Transform a case in an enum/variant.
   */
  def transformCase(
    caseName: String,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformCase(DynamicOptic.root, caseName, transform))

  /**
   * Transform a case in an enum/variant at the given optic path.
   */
  def transformCase(
    at: DynamicOptic,
    caseName: String,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformCase(at, caseName, transform))

  /**
   * Transform elements in a collection.
   */
  def transformElements(
    name: String,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(DynamicOptic.root.field(name), transform))

  /**
   * Transform elements in a collection at the given optic path.
   */
  def transformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformElements(at, transform))

  /**
   * Transform keys in a map.
   */
  def transformKeys(
    name: String,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(DynamicOptic.root.field(name), transform))

  /**
   * Transform keys in a map at the given optic path.
   */
  def transformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformKeys(at, transform))

  /**
   * Transform values in a map.
   */
  def transformValues(
    name: String,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(DynamicOptic.root.field(name), transform))

  /**
   * Transform values in a map at the given optic path.
   */
  def transformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValues(at, transform))

  /**
   * Transform a field's value using an expression.
   */
  def transformField(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformValue(at, transform))

  /**
   * Change a field's type using a coercion expression.
   */
  def changeFieldType(
    at: DynamicOptic,
    converter: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.ChangeType(at, converter))

  /**
   * Join multiple fields into one.
   */
  def join(
    at: DynamicOptic,
    sources: Vector[DynamicOptic],
    combiner: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Join(at, sources, combiner))

  /**
   * Split one field into multiple.
   */
  def split(
    at: DynamicOptic,
    targets: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, DynamicValue]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Split(at, targets, splitter))

  /**
   * Internal validation for use by build methods.
   */
  def validate: Either[String, Unit] = {
    val start  = MigrationValidator.fromSchema(fromSchema)
    val target = MigrationValidator.fromSchema(toSchema)
    MigrationValidator.validate(start, target, actions)
  }

  /**
   * Build migration without validation (for incremental development).
   *
   * Unlike `build`, this does not verify schema compatibility.
   */
  def buildPartial: Migration[A, B] =
    Migration[A, B](DynamicMigration(actions))(fromSchema, toSchema)
}

object MigrationBuilder {

  /**
   * Create a new migration builder.
   */
  def apply[A, B](implicit fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](Vector.empty)(fromSchema, toSchema)

  /**
   * Internal constructor for creating builders with actions (used by macros).
   */
  def apply[A, B](
    actions: Vector[MigrationAction]
  )(implicit fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](actions)(fromSchema, toSchema)
}
