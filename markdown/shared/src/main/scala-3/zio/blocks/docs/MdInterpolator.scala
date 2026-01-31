package zio.blocks.docs

import scala.quoted._

trait MdInterpolator {

  extension (inline sc: StringContext) {
    inline def md(inline args: Any*): Doc = ${ MdMacros.mdImpl('sc, 'args) }
  }
}

private[docs] object MdMacros {

  def mdImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Doc] = {
    import quotes.reflect._

    val parts = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort }
      case _ => report.errorAndAbort("Expected a StringContext with string literal parts")
    }

    val testInput = parts.mkString("X")
    Parser.parse(testInput) match {
      case Left(err) => report.errorAndAbort(s"Invalid markdown: ${err.message}")
      case Right(_)  =>
    }

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    val processedArgs: Seq[Expr[Inline]] = argExprs.map { argExpr =>
      val argType        = argExpr.asTerm.tpe.widen
      val toMarkdownTc   = TypeRepr.of[ToMarkdown]
      val toMarkdownType = toMarkdownTc.appliedTo(argType)
      Implicits.search(toMarkdownType) match {
        case success: ImplicitSearchSuccess =>
          argType.asType match {
            case '[t] =>
              val instanceExpr = success.tree.asExprOf[ToMarkdown[t]]
              val typedArgExpr = argExpr.asExprOf[t]
              '{ $instanceExpr.toMarkdown($typedArgExpr) }
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(s"No ToMarkdown instance found for type ${argType.show}")
      }
    }

    val processedArgsExpr: Expr[Seq[Inline]] = Expr.ofSeq(processedArgs)
    '{ MdInterpolatorRuntime.parseAndBuild($sc, $processedArgsExpr) }
  }
}
