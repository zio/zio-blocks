package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A builder for constructing [[Migration]] instances.
 *
 * The builder provides a fluent API for defining migration actions using
 * selector expressions. In the full implementation, selector-accepting methods
 * would be implemented via macros that:
 *   1. Inspect the selector expression
 *   2. Validate it is a supported projection
 *   3. Convert it to a [[DynamicOptic]]
 *   4. Store the optic in the migration action
 *
 * This implementation provides runtime versions of these methods that accept
 * [[DynamicOptic]] paths directly. The macro layer can be added for
 * compile-time selector validation.
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the target type
 */
class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  // ==================== Record Operations ====================

  /**
   * Add a field to the target record.
   *
   * @param path
   *   path to the new field (typically _.fieldName)
   * @param default
   *   expression producing the default value
   */
  def addField(path: DynamicOptic, default: DynamicSchemaExpr): MigrationBuilder[A, B] = {
    val (parentPath, fieldName) = splitPath(path)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AddField(parentPath, fieldName, default)
    )
  }

  /**
   * Add a field with a literal default value.
   */
  def addField[T](path: DynamicOptic, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = {
    val defaultValue = schema.toDynamicValue(default)
    addField(path, DynamicSchemaExpr.Literal(defaultValue))
  }

  /**
   * Drop a field from the source record.
   *
   * @param path
   *   path to the field to drop
   * @param defaultForReverse
   *   expression producing value when reversing (optional)
   */
  def dropField(
    path: DynamicOptic,
    defaultForReverse: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] = {
    val (parentPath, fieldName) = splitPath(path)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(parentPath, fieldName, defaultForReverse)
    )
  }

  /**
   * Rename a field.
   *
   * @param from
   *   path to the source field
   * @param to
   *   path to the target field (must be at same level as source)
   */
  def renameField(from: DynamicOptic, to: DynamicOptic): MigrationBuilder[A, B] = {
    val (parentPath, fromName) = splitPath(from)
    val (_, toName)            = splitPath(to)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(parentPath, fromName, toName)
    )
  }

  /**
   * Rename a field using string names.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    renameField(DynamicOptic.root.field(from), DynamicOptic.root.field(to))

  /**
   * Transform a field value.
   *
   * @param path
   *   path to the field
   * @param transform
   *   expression to transform the value
   * @param reverseTransform
   *   expression to reverse the transform (for reverse migration)
   */
  def transformField(
    path: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(path, transform, reverseTransform)
    )

  /**
   * Make an optional field mandatory.
   *
   * @param path
   *   path to the optional field
   * @param default
   *   expression producing value when None
   */
  def mandateField(
    path: DynamicOptic,
    default: DynamicSchemaExpr
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Mandate(path, default)
    )

  /**
   * Make an optional field mandatory with a literal default.
   */
  def mandateField[T](path: DynamicOptic, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    mandateField(path, DynamicSchemaExpr.Literal(schema.toDynamicValue(default)))

  /**
   * Make a mandatory field optional.
   *
   * @param path
   *   path to the field
   */
  def optionalizeField(
    path: DynamicOptic,
    defaultForReverse: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Optionalize(path, defaultForReverse)
    )

  /**
   * Change the type of a field (primitive-to-primitive only).
   *
   * @param path
   *   path to the field
   * @param converter
   *   expression to convert to the new type
   * @param reverseConverter
   *   expression to convert back
   */
  def changeFieldType(
    path: DynamicOptic,
    converter: DynamicSchemaExpr,
    reverseConverter: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.ChangeType(path, converter, reverseConverter)
    )

  /**
   * Change a field from one primitive type to another using type names.
   */
  def changeFieldType(path: DynamicOptic, toType: String, fromType: String): MigrationBuilder[A, B] =
    changeFieldType(
      path,
      DynamicSchemaExpr.CoercePrimitive(
        DynamicSchemaExpr.Path(
          if (path.nodes.isEmpty) DynamicOptic.root
          else DynamicOptic(Vector(path.nodes.last))
        ),
        toType
      ),
      DynamicSchemaExpr.CoercePrimitive(
        DynamicSchemaExpr.Path(
          if (path.nodes.isEmpty) DynamicOptic.root
          else DynamicOptic(Vector(path.nodes.last))
        ),
        fromType
      )
    )

  /**
   * Join multiple fields into one.
   *
   * @param target
   *   path to the target combined field
   * @param sourcePaths
   *   paths to source fields
   * @param combiner
   *   expression to combine source values
   * @param splitter
   *   expression to split back (for reverse)
   */
  def joinFields(
    target: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicSchemaExpr,
    splitter: DynamicSchemaExpr
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Join(target, sourcePaths, combiner, splitter)
    )

  /**
   * Split a field into multiple fields.
   *
   * @param source
   *   path to the source field
   * @param targetPaths
   *   paths to target fields
   * @param splitter
   *   expression to split the source value
   * @param combiner
   *   expression to combine back (for reverse)
   */
  def splitField(
    source: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicSchemaExpr,
    combiner: DynamicSchemaExpr
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Split(source, targetPaths, splitter, combiner)
    )

  // ==================== Enum Operations ====================

  /**
   * Rename a case in a variant/enum.
   *
   * @param from
   *   original case name
   * @param to
   *   new case name
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    renameCaseAt(DynamicOptic.root, from, to)

  /**
   * Rename a case in a variant at a specific path.
   */
  def renameCaseAt(path: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(path, from, to)
    )

  /**
   * Transform the structure within a specific case.
   *
   * @param caseName
   *   name of the case to transform
   * @param caseMigration
   *   function that configures the nested migration
   */
  def transformCase(
    caseName: String,
    caseMigration: MigrationBuilder[Any, Any] => MigrationBuilder[Any, Any]
  ): MigrationBuilder[A, B] =
    transformCaseAt(DynamicOptic.root, caseName, caseMigration)

  /**
   * Transform a case at a specific path.
   */
  def transformCaseAt(
    path: DynamicOptic,
    caseName: String,
    caseMigration: MigrationBuilder[Any, Any] => MigrationBuilder[Any, Any]
  ): MigrationBuilder[A, B] = {
    // Create an empty builder and let the user configure it
    val emptyBuilder = new MigrationBuilder[Any, Any](
      Schema.dynamic.asInstanceOf[Schema[Any]],
      Schema.dynamic.asInstanceOf[Schema[Any]],
      Vector.empty
    )
    val configuredBuilder = caseMigration(emptyBuilder)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(path, caseName, configuredBuilder.actions)
    )
  }

  // ==================== Collection Operations ====================

  /**
   * Transform all elements in a sequence.
   *
   * @param path
   *   path to the sequence
   * @param transform
   *   expression to apply to each element
   * @param reverseTransform
   *   expression to reverse the transform
   */
  def transformElements(
    path: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(path, transform, reverseTransform)
    )

  // ==================== Map Operations ====================

  /**
   * Transform all keys in a map.
   *
   * @param path
   *   path to the map
   * @param transform
   *   expression to apply to each key
   * @param reverseTransform
   *   expression to reverse the transform
   */
  def transformKeys(
    path: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformKeys(path, transform, reverseTransform)
    )

  /**
   * Transform all values in a map.
   *
   * @param path
   *   path to the map
   * @param transform
   *   expression to apply to each value
   * @param reverseTransform
   *   expression to reverse the transform
   */
  def transformValues(
    path: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverseTransform: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValues(path, transform, reverseTransform)
    )

  // ==================== Build Methods ====================

  /**
   * Build the migration with full validation.
   *
   * This validates that applying the migration actions to the source schema
   * structure would produce a structure compatible with the target schema.
   *
   * @throws java.lang.IllegalArgumentException
   *   if validation fails
   */
  def build: Migration[A, B] = {
    val validation = MigrationValidator.validate(sourceSchema, targetSchema, actions)
    if (!validation.isValid) {
      throw new IllegalArgumentException(
        s"Migration validation failed:\n${validation.errors.mkString("  - ", "\n  - ", "")}"
      )
    }
    buildPartial
  }

  /**
   * Build the migration and return validation result.
   *
   * Unlike `build`, this does not throw on validation failure but returns the
   * validation result along with the migration.
   */
  def buildValidated: Either[List[String], Migration[A, B]] = {
    val validation = MigrationValidator.validate(sourceSchema, targetSchema, actions)
    if (validation.isValid) {
      Right(buildPartial)
    } else {
      Left(validation.errors)
    }
  }

  /**
   * Build the migration without full structural validation.
   *
   * Use this when you want to skip validation, such as for partial migrations
   * or when validation is too restrictive.
   */
  def buildPartial: Migration[A, B] =
    new Migration(new DynamicMigration(resolveDefaults(actions)), sourceSchema, targetSchema)

  // ==================== Helper Methods ====================

  private def resolveDefaults(actions: Vector[MigrationAction]): Vector[MigrationAction] = {
    def resolveDefaultValue(schema: Schema[_], optic: DynamicOptic): Option[DynamicValue] =
      schema.reflect.get(optic).flatMap { reflect =>
        val anyReflect = reflect.asInstanceOf[Reflect[zio.blocks.schema.binding.Binding, Any]]
        anyReflect.getDefaultValue.map(anyReflect.toDynamicValue)
      }

    def containsDefaultValue(expr: DynamicSchemaExpr): Boolean = expr match {
      case DynamicSchemaExpr.DefaultValue          => true
      case DynamicSchemaExpr.ResolvedDefault(_)    => false
      case DynamicSchemaExpr.Literal(_)            => false
      case DynamicSchemaExpr.Path(_)               => false
      case DynamicSchemaExpr.Not(e)                => containsDefaultValue(e)
      case DynamicSchemaExpr.Logical(l, r, _)      => containsDefaultValue(l) || containsDefaultValue(r)
      case DynamicSchemaExpr.Relational(l, r, _)   => containsDefaultValue(l) || containsDefaultValue(r)
      case DynamicSchemaExpr.Arithmetic(l, r, _)   => containsDefaultValue(l) || containsDefaultValue(r)
      case DynamicSchemaExpr.StringConcat(l, r)    => containsDefaultValue(l) || containsDefaultValue(r)
      case DynamicSchemaExpr.StringLength(e)       => containsDefaultValue(e)
      case DynamicSchemaExpr.CoercePrimitive(e, _) => containsDefaultValue(e)
    }

    def rewriteDefault(expr: DynamicSchemaExpr, replacement: DynamicSchemaExpr): DynamicSchemaExpr = expr match {
      case DynamicSchemaExpr.DefaultValue =>
        replacement
      case DynamicSchemaExpr.ResolvedDefault(_) =>
        expr
      case DynamicSchemaExpr.Literal(_) =>
        expr
      case DynamicSchemaExpr.Path(_) =>
        expr
      case DynamicSchemaExpr.Not(e) =>
        DynamicSchemaExpr.Not(rewriteDefault(e, replacement))
      case DynamicSchemaExpr.Logical(l, r, op) =>
        DynamicSchemaExpr.Logical(rewriteDefault(l, replacement), rewriteDefault(r, replacement), op)
      case DynamicSchemaExpr.Relational(l, r, op) =>
        DynamicSchemaExpr.Relational(rewriteDefault(l, replacement), rewriteDefault(r, replacement), op)
      case DynamicSchemaExpr.Arithmetic(l, r, op) =>
        DynamicSchemaExpr.Arithmetic(rewriteDefault(l, replacement), rewriteDefault(r, replacement), op)
      case DynamicSchemaExpr.StringConcat(l, r) =>
        DynamicSchemaExpr.StringConcat(rewriteDefault(l, replacement), rewriteDefault(r, replacement))
      case DynamicSchemaExpr.StringLength(e) =>
        DynamicSchemaExpr.StringLength(rewriteDefault(e, replacement))
      case DynamicSchemaExpr.CoercePrimitive(e, tpe) =>
        DynamicSchemaExpr.CoercePrimitive(rewriteDefault(e, replacement), tpe)
    }

    def resolveExprStrict(schema: Schema[_], defaultAt: DynamicOptic, expr: DynamicSchemaExpr): DynamicSchemaExpr =
      if (!containsDefaultValue(expr)) expr
      else {
        val defaultValue = resolveDefaultValue(schema, defaultAt).getOrElse {
          throw new IllegalArgumentException(s"DefaultValue used but no default is available at path $defaultAt.")
        }
        rewriteDefault(expr, DynamicSchemaExpr.ResolvedDefault(defaultValue))
      }

    def resolveExprBestEffort(schema: Schema[_], defaultAt: DynamicOptic, expr: DynamicSchemaExpr): DynamicSchemaExpr =
      if (!containsDefaultValue(expr)) expr
      else
        resolveDefaultValue(schema, defaultAt).fold(expr) { dv =>
          rewriteDefault(expr, DynamicSchemaExpr.ResolvedDefault(dv))
        }

    actions.map {
      case MigrationAction.AddField(at, name, default) =>
        val fieldPath = at.field(name)
        MigrationAction.AddField(at, name, resolveExprStrict(targetSchema, fieldPath, default))

      case MigrationAction.DropField(at, name, defaultForReverse) =>
        val fieldPath = at.field(name)
        MigrationAction.DropField(at, name, resolveExprBestEffort(sourceSchema, fieldPath, defaultForReverse))

      case a: MigrationAction.RenameField =>
        a

      case MigrationAction.TransformValue(at, transform, reverseTransform) =>
        MigrationAction.TransformValue(
          at,
          resolveExprStrict(targetSchema, at, transform),
          resolveExprBestEffort(sourceSchema, at, reverseTransform)
        )

      case MigrationAction.Mandate(at, default) =>
        MigrationAction.Mandate(at, resolveExprStrict(targetSchema, at, default))

      case a: MigrationAction.Optionalize =>
        a

      case MigrationAction.ChangeType(at, converter, reverseConverter) =>
        MigrationAction.ChangeType(
          at,
          resolveExprStrict(targetSchema, at, converter),
          resolveExprBestEffort(sourceSchema, at, reverseConverter)
        )

      case MigrationAction.Join(at, sourcePaths, combiner, splitter) =>
        MigrationAction.Join(
          at,
          sourcePaths,
          resolveExprStrict(targetSchema, at, combiner),
          resolveExprBestEffort(sourceSchema, at, splitter)
        )

      case MigrationAction.Split(at, targetPaths, splitter, combiner) =>
        MigrationAction.Split(
          at,
          targetPaths,
          resolveExprStrict(targetSchema, at, splitter),
          resolveExprBestEffort(sourceSchema, at, combiner)
        )

      case a: MigrationAction.RenameCase =>
        a

      case a: MigrationAction.TransformCase =>
        // TransformCase nests actions under dynamic schemas; default resolution is best-effort at runtime.
        a

      case MigrationAction.TransformElements(at, transform, reverseTransform) =>
        val elementPath = new DynamicOptic(at.nodes :+ DynamicOptic.Node.Elements)
        MigrationAction.TransformElements(
          at,
          resolveExprStrict(targetSchema, elementPath, transform),
          resolveExprBestEffort(sourceSchema, elementPath, reverseTransform)
        )

      case MigrationAction.TransformKeys(at, transform, reverseTransform) =>
        val keyPath = new DynamicOptic(at.nodes :+ DynamicOptic.Node.MapKeys)
        MigrationAction.TransformKeys(
          at,
          resolveExprStrict(targetSchema, keyPath, transform),
          resolveExprBestEffort(sourceSchema, keyPath, reverseTransform)
        )

      case MigrationAction.TransformValues(at, transform, reverseTransform) =>
        val valuePath = new DynamicOptic(at.nodes :+ DynamicOptic.Node.MapValues)
        MigrationAction.TransformValues(
          at,
          resolveExprStrict(targetSchema, valuePath, transform),
          resolveExprBestEffort(sourceSchema, valuePath, reverseTransform)
        )

      case MigrationAction.Identity =>
        MigrationAction.Identity
    }
  }

  /**
   * Split a path into parent path and field name.
   */
  private def splitPath(path: DynamicOptic): (DynamicOptic, String) = {
    val nodes = path.nodes
    nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) =>
        (DynamicOptic(nodes.dropRight(1)), name)
      case _ =>
        throw new IllegalArgumentException(s"Path must end in a field node, but was: $path")
    }
  }
}

object MigrationBuilder {

  /**
   * Create a new builder for migrating from A to B.
   */
  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Convenient syntax for creating paths.
   */
  object paths {
    def field(name: String): DynamicOptic   = DynamicOptic.root.field(name)
    def field(names: String*): DynamicOptic = names.foldLeft(DynamicOptic.root)(_.field(_))
    def elements: DynamicOptic              = DynamicOptic.elements
    def mapKeys: DynamicOptic               = DynamicOptic.mapKeys
    def mapValues: DynamicOptic             = DynamicOptic.mapValues
  }

  /**
   * Convenient syntax for creating expressions.
   */
  object exprs {
    def literal[T](value: T)(implicit schema: Schema[T]): DynamicSchemaExpr =
      DynamicSchemaExpr.Literal(schema.toDynamicValue(value))

    def path(optic: DynamicOptic): DynamicSchemaExpr =
      DynamicSchemaExpr.Path(optic)

    def path(fieldName: String): DynamicSchemaExpr =
      DynamicSchemaExpr.Path(DynamicOptic.root.field(fieldName))

    def concat(left: DynamicSchemaExpr, right: DynamicSchemaExpr): DynamicSchemaExpr =
      DynamicSchemaExpr.StringConcat(left, right)

    def defaultValue: DynamicSchemaExpr =
      DynamicSchemaExpr.DefaultValue

    def coerce(expr: DynamicSchemaExpr, targetType: String): DynamicSchemaExpr =
      DynamicSchemaExpr.CoercePrimitive(expr, targetType)
  }
}
