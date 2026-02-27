package zio.blocks.schema.migration

import scala.reflect.macros.whitebox

/**
 * Scala 2 macros for the migration builder. Converts selector lambda
 * expressions (e.g., `_.name`, `_.address.street`) into [[DynamicOptic]] paths
 * at compile time.
 */
object MigrationBuilderMacros {

  def selectorToOptic(c: whitebox.Context)(selector: c.Tree): c.Tree = {
    import c.universe._

    def extractFields(tree: c.Tree): List[String] = tree match {
      case q"($param) => $body"            => extractFieldsFromBody(body)
      case Function(_, body)               => extractFieldsFromBody(body)
      case Block(_, expr)                  => extractFields(expr)
      case _                               => extractFieldsFromBody(tree)
    }

    def extractFieldsFromBody(body: c.Tree): List[String] = body match {
      case Select(inner, name) if name.toString != "<init>" =>
        extractFieldsFromBody(inner) :+ name.toString
      case Ident(_) => Nil
      case _        =>
        c.abort(
          body.pos,
          s"Unsupported selector expression. Expected field access like _.field or _.nested.field."
        )
    }

    val fields = extractFields(selector)

    if (fields.isEmpty) {
      q"_root_.zio.blocks.schema.DynamicOptic.root"
    } else {
      fields.foldLeft[c.Tree](q"_root_.zio.blocks.schema.DynamicOptic.root") { (optic, name) =>
        q"$optic.field($name)"
      }
    }
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Tree,
    defaultValue: c.Tree
  ): c.Tree = {
    import c.universe._
    val optic = selectorToOptic(c)(target)
    q"${c.prefix.tree}.addFieldAt($optic, $defaultValue)"
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Tree,
    defaultForReverse: c.Tree
  ): c.Tree = {
    import c.universe._
    val optic = selectorToOptic(c)(source)
    q"${c.prefix.tree}.dropFieldAt($optic, $defaultForReverse)"
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Tree,
    to: c.Tree
  ): c.Tree = {
    import c.universe._
    val fromOptic = selectorToOptic(c)(from)
    val toOptic   = selectorToOptic(c)(to)
    q"""
    {
      val fromO = $fromOptic
      val toO   = $toOptic
      val fromNodes = fromO.nodes
      val toNodes   = toO.nodes
      if (fromNodes.isEmpty || toNodes.isEmpty)
        ${c.prefix.tree}
      else {
        (fromNodes.last, toNodes.last) match {
          case (fromField: _root_.zio.blocks.schema.DynamicOptic.Node.Field, toField: _root_.zio.blocks.schema.DynamicOptic.Node.Field) =>
            val parentPath = new _root_.zio.blocks.schema.DynamicOptic(fromNodes.init)
            ${c.prefix.tree}.renameFieldAt(parentPath, fromField.name, toField.name)
          case _ => ${c.prefix.tree}
        }
      }
    }
    """
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Tree,
    to: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val fromOptic = selectorToOptic(c)(from)
    val toOptic   = selectorToOptic(c)(to)
    q"${c.prefix.tree}.transformFieldAt($fromOptic, $toOptic, $transform)"
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Tree,
    target: c.Tree
  ): c.Tree = {
    import c.universe._
    val sourceOptic = selectorToOptic(c)(source)
    val targetOptic = selectorToOptic(c)(target)
    q"${c.prefix.tree}.optionalizeFieldAt($sourceOptic, $targetOptic)"
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Tree,
    target: c.Tree,
    converter: c.Tree
  ): c.Tree = {
    import c.universe._
    val sourceOptic = selectorToOptic(c)(source)
    val targetOptic = selectorToOptic(c)(target)
    q"${c.prefix.tree}.changeFieldTypeAt($sourceOptic, $targetOptic, $converter)"
  }

  def renameCaseImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Tree,
    fromName: c.Tree,
    toName: c.Tree
  ): c.Tree = {
    import c.universe._
    val optic = selectorToOptic(c)(at)
    q"${c.prefix.tree}.renameCaseAt($optic, $fromName, $toName)"
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val optic = selectorToOptic(c)(at)
    q"${c.prefix.tree}.transformElementsAt($optic, $transform)"
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val optic = selectorToOptic(c)(at)
    q"${c.prefix.tree}.transformKeysAt($optic, $transform)"
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val optic = selectorToOptic(c)(at)
    q"${c.prefix.tree}.transformValuesAt($optic, $transform)"
  }

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe._
    q"""
    {
      val b = ${c.prefix.tree}
      new _root_.zio.blocks.schema.migration.Migration[${weakTypeOf[A]}, ${weakTypeOf[B]}](
        new _root_.zio.blocks.schema.migration.DynamicMigration(b.actions),
        b.sourceSchema,
        b.targetSchema
      )
    }
    """
  }
}
