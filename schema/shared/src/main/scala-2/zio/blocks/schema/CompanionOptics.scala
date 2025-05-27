package zio.blocks.schema

trait CompanionOptics[S] {
  import scala.language.experimental.macros

  def field[A](path: S => A)(implicit schema: Schema[S]): Lens[S, A] = macro CompanionOptics.field[S, A]

  def caseOf[A <: S](implicit schema: Schema[S]): Prism[S, A] = macro CompanionOptics.caseOf[S, A]
}

private object CompanionOptics {
  import scala.reflect.macros.blackbox
  import scala.reflect.NameTransformer

  def field[S: c.WeakTypeTag, A: c.WeakTypeTag](
    c: blackbox.Context
  )(path: c.Expr[S => A])(schema: c.Expr[Schema[S]]): c.Expr[Lens[S, A]] = {
    import c.universe._

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got: ${showRaw(tree)}")
    }

    toPathBody(path.tree) match {
      case q"$_.$child" =>
        val aTpe      = weakTypeOf[A].dealias
        val fieldName = NameTransformer.decode(child.toString)
        c.Expr[Lens[S, A]](q"$schema.reflect.asRecord.flatMap(_.lensByName[$aTpe]($fieldName)).get")
      case tree =>
        fail(s"Expected a path element, got: ${showRaw(tree)}")
    }
  }

  def caseOf[S: c.WeakTypeTag, A <: S: c.WeakTypeTag](
    c: blackbox.Context
  )(schema: c.Expr[Schema[S]]): c.Expr[Prism[S, A]] = {
    import c.universe._

    val aTpe     = weakTypeOf[A].dealias
    val caseName = NameTransformer.decode(aTpe.typeSymbol.name.toString)
    c.Expr[Prism[S, A]](q"$schema.reflect.asVariant.flatMap(_.prismByName[$aTpe]($caseName)).get")
  }
}
