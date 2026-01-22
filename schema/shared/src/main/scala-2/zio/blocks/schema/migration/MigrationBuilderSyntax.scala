package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.DynamicOptic

/**
 * Scala 2 implicit class for MigrationBuilder that uses macros to extract field
 * names and paths from lambda expressions.
 *
 * This provides a type-safe, IDE-friendly API: builder.addField(_.country,
 * "USA") builder.renameField(_.name, _.fullName) builder.dropField(_.oldField)
 * builder.atPath(_.address).addField(_.zip, "00000")
 */
object MigrationBuilderSyntax {

  implicit class MigrationBuilderOps[A, B](val builder: MigrationBuilder[A, B]) extends AnyVal {

    /**
     * Add a field using a selector and string default value.
     *
     * Example: builder.addField(_.country, "USA")
     */
    def addField[F](selector: B => F, defaultValue: String): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.addFieldStringImpl[A, B, F]

    /**
     * Add a field using a selector and int default value.
     *
     * Example: builder.addField(_.age, 0)
     */
    def addFieldInt[F](selector: B => F, defaultValue: Int): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.addFieldIntImpl[A, B, F]

    /**
     * Add a field using a selector and boolean default value.
     *
     * Example: builder.addField(_.active, true)
     */
    def addFieldBool[F](selector: B => F, defaultValue: Boolean): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.addFieldBoolImpl[A, B, F]

    /**
     * Drop a field using a selector.
     *
     * Example: builder.dropField(_.oldField)
     */
    def dropField[F](selector: A => F): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.dropFieldImpl[A, B, F]

    /**
     * Rename a field using two selectors.
     *
     * Example: builder.renameField(_.name, _.fullName)
     */
    def renameField[F1, F2](from: A => F1, to: B => F2): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.renameFieldImpl[A, B, F1, F2]

    /**
     * Make an optional field mandatory with a default value.
     *
     * Example: builder.mandateField(_.email, "default@example.com")
     */
    def mandateField[F](selector: A => F, defaultValue: String): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.mandateFieldImpl[A, B, F]

    /**
     * Make a mandatory field optional.
     *
     * Example: builder.optionalizeField(_.middleName)
     */
    def optionalizeField[F](selector: A => F): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.optionalizeFieldImpl[A, B, F]

    /**
     * Navigate to a nested path and apply operations there.
     *
     * Example: builder.atPath(_.address).addField("zip", "00000")
     */
    def atPath[F](selector: A => F): MigrationBuilderAtPath[A, B] = macro MigrationBuilderSyntaxMacros.atPathImpl[A, B, F]
  }
}

/**
 * A builder scoped to a specific path for nested operations.
 */
final class MigrationBuilderAtPath[A, B](
  val builder: MigrationBuilder[A, B],
  val path: DynamicOptic
) {

  /**
   * Add a field at the current path.
   */
  def addField(fieldName: String, defaultValue: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.AddField(
      path,
      fieldName,
      SchemaExpr.literalString(defaultValue)
    )
    new MigrationBuilder(builder.sourceSchema, builder.targetSchema, builder.actions :+ action)
  }

  /**
   * Drop a field at the current path.
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.DropField(
      path,
      fieldName,
      None
    )
    new MigrationBuilder(builder.sourceSchema, builder.targetSchema, builder.actions :+ action)
  }

  /**
   * Rename a field at the current path.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.Rename(
      path,
      from,
      to
    )
    new MigrationBuilder(builder.sourceSchema, builder.targetSchema, builder.actions :+ action)
  }
}

/**
 * Macro implementations for Scala 2.
 */
object MigrationBuilderSyntaxMacros {
  import scala.reflect.macros.whitebox

  def addFieldStringImpl[A, B, F](c: whitebox.Context)(
    selector: c.Expr[B => F],
    defaultValue: c.Expr[String]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName   = MigrationBuilderMacros.extractFieldName(c)(selector)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilder[A, B]](q"""
      val action = _root_.zio.blocks.schema.migration.MigrationAction.AddField(
        _root_.zio.blocks.schema.DynamicOptic.root,
        $fieldName,
        _root_.zio.blocks.schema.migration.SchemaExpr.literalString($defaultValue)
      )
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        $builderTree.sourceSchema,
        $builderTree.targetSchema,
        $builderTree.actions :+ action
      )
    """)
  }

  def addFieldIntImpl[A, B, F](c: whitebox.Context)(
    selector: c.Expr[B => F],
    defaultValue: c.Expr[Int]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName   = MigrationBuilderMacros.extractFieldName(c)(selector)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilder[A, B]](q"""
      val action = _root_.zio.blocks.schema.migration.MigrationAction.AddField(
        _root_.zio.blocks.schema.DynamicOptic.root,
        $fieldName,
        _root_.zio.blocks.schema.migration.SchemaExpr.literalInt($defaultValue)
      )
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        $builderTree.sourceSchema,
        $builderTree.targetSchema,
        $builderTree.actions :+ action
      )
    """)
  }

  def addFieldBoolImpl[A, B, F](c: whitebox.Context)(
    selector: c.Expr[B => F],
    defaultValue: c.Expr[Boolean]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName   = MigrationBuilderMacros.extractFieldName(c)(selector)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilder[A, B]](q"""
      val action = _root_.zio.blocks.schema.migration.MigrationAction.AddField(
        _root_.zio.blocks.schema.DynamicOptic.root,
        $fieldName,
        _root_.zio.blocks.schema.migration.SchemaExpr.literalBool($defaultValue)
      )
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        $builderTree.sourceSchema,
        $builderTree.targetSchema,
        $builderTree.actions :+ action
      )
    """)
  }

  def dropFieldImpl[A, B, F](c: whitebox.Context)(
    selector: c.Expr[A => F]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName   = MigrationBuilderMacros.extractFieldName(c)(selector)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilder[A, B]](q"""
      val action = _root_.zio.blocks.schema.migration.MigrationAction.DropField(
        _root_.zio.blocks.schema.DynamicOptic.root,
        $fieldName,
        _root_.scala.None
      )
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        $builderTree.sourceSchema,
        $builderTree.targetSchema,
        $builderTree.actions :+ action
      )
    """)
  }

  def renameFieldImpl[A, B, F1, F2](c: whitebox.Context)(
    from: c.Expr[A => F1],
    to: c.Expr[B => F2]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fromName    = MigrationBuilderMacros.extractFieldName(c)(from)
    val toName      = MigrationBuilderMacros.extractFieldName(c)(to)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilder[A, B]](q"""
      val action = _root_.zio.blocks.schema.migration.MigrationAction.Rename(
        _root_.zio.blocks.schema.DynamicOptic.root,
        $fromName,
        $toName
      )
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        $builderTree.sourceSchema,
        $builderTree.targetSchema,
        $builderTree.actions :+ action
      )
    """)
  }

  def mandateFieldImpl[A, B, F](c: whitebox.Context)(
    selector: c.Expr[A => F],
    defaultValue: c.Expr[String]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName   = MigrationBuilderMacros.extractFieldName(c)(selector)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilder[A, B]](q"""
      val action = _root_.zio.blocks.schema.migration.MigrationAction.Mandate(
        _root_.zio.blocks.schema.DynamicOptic.root,
        $fieldName,
        _root_.zio.blocks.schema.migration.SchemaExpr.literalString($defaultValue)
      )
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        $builderTree.sourceSchema,
        $builderTree.targetSchema,
        $builderTree.actions :+ action
      )
    """)
  }

  def optionalizeFieldImpl[A, B, F](c: whitebox.Context)(
    selector: c.Expr[A => F]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName   = MigrationBuilderMacros.extractFieldName(c)(selector)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilder[A, B]](q"""
      val action = _root_.zio.blocks.schema.migration.MigrationAction.Optionalize(
        _root_.zio.blocks.schema.DynamicOptic.root,
        $fieldName
      )
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        $builderTree.sourceSchema,
        $builderTree.targetSchema,
        $builderTree.actions :+ action
      )
    """)
  }

  def atPathImpl[A, B, F](c: whitebox.Context)(
    selector: c.Expr[A => F]
  ): c.Expr[MigrationBuilderAtPath[A, B]] = {
    import c.universe._

    val path        = MigrationBuilderMacros.extractPath(c)(selector)
    val builderTree = q"${c.prefix.tree}.builder"

    c.Expr[MigrationBuilderAtPath[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilderAtPath($builderTree, $path)
    """)
  }
}
