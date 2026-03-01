package zio.blocks.schema.migration

import scala.reflect.macros.whitebox

private[migration] object MigrationBuilderMacros {

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = selectorToOptic(c)(target.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.AddField($path, $default),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  def dropFieldImplDefault[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = selectorToOptic(c)(source.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField($path, _root_.zio.blocks.schema.DynamicValue.Null),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = selectorToOptic(c)(source.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField($path, $defaultForReverse),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val fromPath = selectorToOptic(c)(from.tree)
    val toName   = lastFieldName(c)(to.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Rename($fromPath, $toName),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val fromPath = selectorToOptic(c)(from.tree)
    val toPath   = selectorToOptic(c)(to.tree)
    val toName   = lastFieldName(c)(to.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Rename($fromPath, $toName) :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValue($toPath, $transform),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    default: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = selectorToOptic(c)(source.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Mandate($path, $default),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = selectorToOptic(c)(source.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Optionalize($path),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    converter: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = selectorToOptic(c)(source.tree)
    c.Expr[MigrationBuilder[A, B]](q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ChangeType($path, $converter),
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema
      )
    """)
  }

  // ── Selector extraction ────────────────────────────────────────────

  private def selectorToOptic(c: whitebox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._
    val body       = extractLambdaBody(c)(tree)
    val fieldNames = collectFieldNames(c)(body)
    if (fieldNames.isEmpty)
      c.abort(c.enclosingPosition, "Migration selector must access at least one field (e.g. _.field or _.field.nested)")
    fieldNames.foldLeft(q"_root_.zio.blocks.schema.DynamicOptic.root": c.Tree) { (acc, name) =>
      q"$acc.field($name)"
    }
  }

  private def lastFieldName(c: whitebox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._
    val body       = extractLambdaBody(c)(tree)
    val fieldNames = collectFieldNames(c)(body)
    if (fieldNames.isEmpty)
      c.abort(c.enclosingPosition, "Migration selector must access at least one field")
    Literal(Constant(fieldNames.last))
  }

  private def extractLambdaBody(c: whitebox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._
    tree match {
      case q"($_) => $body" => body
      case _                =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }
  }

  private def collectFieldNames(c: whitebox.Context)(tree: c.Tree): List[String] = {
    import c.universe._
    def loop(t: c.Tree): List[String] = t match {
      case Select(parent, fieldName) => loop(parent) :+ fieldName.decodedName.toString
      case _: Ident                  => Nil
      case Typed(inner, _)           => loop(inner)
      case _                         =>
        c.abort(
          c.enclosingPosition,
          s"Migration selectors only support field access (e.g. _.field or _.a.b.c), got '$t'"
        )
    }
    loop(tree)
  }
}
