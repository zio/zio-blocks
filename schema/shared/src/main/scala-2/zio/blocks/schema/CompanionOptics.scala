package zio.blocks.schema

import zio.blocks.schema.binding._

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

    val sTpe = weakTypeOf[S].dealias
    val aTpe = weakTypeOf[A].dealias
    path.tree match {
      case Function(List(valDef @ ValDef(_, _, _, _)), Select(id @ Ident(_), TermName(name)))
          if id.symbol == valDef.symbol =>
        val fieldName = NameTransformer.decode(name)
        c.Expr[Lens[S, A]] {
          q"""{
                import _root_.zio.blocks.schema._
                import _root_.zio.blocks.schema.binding._

                val reflect = $schema.reflect.asInstanceOf[Reflect.Record[Binding, $sTpe]]
                val fields = reflect.fields
                Lens(reflect, fields(fields.indexWhere(_.name == $fieldName)).asInstanceOf[Term[Binding, $sTpe, $aTpe]])
              }"""
        }
      case pt =>
        fail(s"Expected a lambda expression that returns a field value, got: ${showRaw(pt)}")
    }
  }

  def caseOf[S: c.WeakTypeTag, A <: S: c.WeakTypeTag](
    c: blackbox.Context
  )(schema: c.Expr[Schema[S]]): c.Expr[Prism[S, A]] = {
    import c.universe._

    val sTpe     = weakTypeOf[S].dealias
    val aTpe     = weakTypeOf[A].dealias
    val caseName = NameTransformer.decode(aTpe.typeSymbol.name.toString)
    c.Expr[Prism[S, A]] {
      q"""{
            import _root_.zio.blocks.schema._
            import _root_.zio.blocks.schema.binding._

            val reflect = $schema.reflect.asInstanceOf[Reflect.Variant[Binding, $sTpe]]
            val cases = reflect.cases
            Prism(reflect, cases(cases.indexWhere(_.name == $caseName)).asInstanceOf[Term[Binding, $sTpe, $aTpe]])
          }"""
    }
  }
}
