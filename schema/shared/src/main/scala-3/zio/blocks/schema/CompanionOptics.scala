package zio.blocks.schema

import zio.blocks.schema.binding._

trait CompanionOptics[S] {
  inline def field[A](inline path: S => A)(using schema: Schema[S]): Lens[S, A] =
    ${ CompanionOptics.field('path, 'schema) }

  inline def caseOf[A <: S](using schema: Schema[S]): Prism[S, A] =
    ${ CompanionOptics.caseOf('schema) }
}

private object CompanionOptics {
  import scala.quoted._

  def field[S: Type, A: Type](path: Expr[S => A], schema: Expr[Schema[S]])(using q: Quotes): Expr[Lens[S, A]] = {
    import q.reflect._

    def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

    path.asTerm match {
      case Inlined(
            _,
            _,
            Block(
              List(
                DefDef(
                  _,
                  List(TermParamClause(List(valDef @ ValDef(_, _, _)))),
                  _,
                  Some(body @ Select(id @ Ident(_), fieldName))
                )
              ),
              _
            )
          ) if id.symbol == valDef.symbol =>
        val fieldTpe = body.tpe.widen
        fieldTpe.asType match {
          case '[ft] =>
            '{
              import zio.blocks.schema._
              import zio.blocks.schema.binding._

              val reflect = $schema.reflect.asInstanceOf[Reflect.Record[Binding, S]]
              val fields  = reflect.fields
              Lens(
                reflect,
                fields(fields.indexWhere(_.name == ${ Expr(fieldName) }))
                  .asInstanceOf[zio.blocks.schema.Term[Binding, S, ft]]
              )
            }.asExprOf[Lens[S, A]]
        }
      case pt =>
        fail(s"Expected a lambda expression that returns a field value, got: ${pt.show(using Printer.TreeStructure)}")
    }
  }

  def caseOf[S: Type, A <: S: Type](schema: Expr[Schema[S]])(using q: Quotes): Expr[Prism[S, A]] = {
    import q.reflect._

    val sTpe     = TypeRepr.of[A]
    var caseName = sTpe.typeSymbol.name.toString
    if (sTpe.typeSymbol.flags.is(Flags.Module)) caseName = caseName.substring(0, caseName.length - 1)
    '{
      import zio.blocks.schema._
      import zio.blocks.schema.binding._

      val reflect = $schema.reflect.asInstanceOf[Reflect.Variant[Binding, S]]
      val cases   = reflect.cases
      Prism(
        reflect,
        cases(cases.indexWhere(_.name == ${ Expr(caseName) })).asInstanceOf[zio.blocks.schema.Term[Binding, S, A]]
      )
    }.asExprOf[Prism[S, A]]
  }
}
