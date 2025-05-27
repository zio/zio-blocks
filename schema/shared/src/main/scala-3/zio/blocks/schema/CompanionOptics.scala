package zio.blocks.schema

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

              $schema.reflect.asRecord.flatMap(_.lensByName[A](${ Expr(fieldName) })).get
            }.asExprOf[Lens[S, A]]
        }
      case pt =>
        fail(s"Expected a lambda expression that returns a field value, got: ${pt.show(using Printer.TreeStructure)}")
    }
  }

  def caseOf[S: Type, A <: S: Type](schema: Expr[Schema[S]])(using q: Quotes): Expr[Prism[S, A]] = {
    import q.reflect._

    def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

    val sTpe     = TypeRepr.of[A]
    var caseName = sTpe.typeSymbol.name.toString
    if (sTpe.termSymbol.flags.is(Flags.Enum)) {
      sTpe match {
        case TermRef(_, n) => caseName = n
        case _             => fail(s"Unsupported enum type: '${sTpe.show}', tree=$sTpe")
      }
    } else if (sTpe.typeSymbol.flags.is(Flags.Module)) caseName = caseName.substring(0, caseName.length - 1)
    '{
      import zio.blocks.schema._
      import zio.blocks.schema.binding._

      $schema.reflect.asVariant.flatMap(_.prismByName[A](${ Expr(caseName) })).get
    }.asExprOf[Prism[S, A]]
  }
}
