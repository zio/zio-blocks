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

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => fail(s"Expected a lambda expression, got: ${term.show(using Printer.TreeStructure)}")
    }

    toPathBody(path.asTerm) match {
      case Select(Ident(_), fieldName) =>
        '{ $schema.reflect.asRecord.flatMap(_.lensByName[A](${ Expr(fieldName) })).get }.asExprOf[Lens[S, A]]
      case term =>
        fail(s"Expected a path element, got: ${term.show(using Printer.TreeStructure)}")
    }
  }

  def caseOf[S: Type, A <: S: Type](schema: Expr[Schema[S]])(using q: Quotes): Expr[Prism[S, A]] = {
    import q.reflect._

    val sTpe     = TypeRepr.of[A]
    var caseName = sTpe.typeSymbol.name
    if (sTpe.termSymbol.flags.is(Flags.Enum)) caseName = sTpe.termSymbol.name
    else if (sTpe.typeSymbol.flags.is(Flags.Module)) caseName = caseName.substring(0, caseName.length - 1)
    '{ $schema.reflect.asVariant.flatMap(_.prismByName[A](${ Expr(caseName) })).get }.asExprOf[Prism[S, A]]
  }
}
