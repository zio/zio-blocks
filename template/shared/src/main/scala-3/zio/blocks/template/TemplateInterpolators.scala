package zio.blocks.template

import scala.quoted._

trait TemplateInterpolators {

  extension (inline sc: StringContext) {
    inline def css(inline args: Any*): Css  = ${ TemplateMacros.cssImpl('sc, 'args) }
    inline def js(inline args: Any*): Js    = ${ TemplateMacros.jsImpl('sc, 'args) }
    inline def html(inline args: Any*): Dom = ${ TemplateMacros.htmlImpl('sc, 'args) }
  }
}

private[template] object TemplateMacros {

  def cssImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Css] = {
    import quotes.reflect._

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    val processedArgs: Seq[Expr[String]] = argExprs.map { argExpr =>
      val argType   = argExpr.asTerm.tpe.widen
      val toCssTc   = TypeRepr.of[ToCss]
      val toCssType = toCssTc.appliedTo(argType)
      Implicits.search(toCssType) match {
        case success: ImplicitSearchSuccess =>
          argType.asType match {
            case '[t] =>
              val instanceExpr = success.tree.asExprOf[ToCss[t]]
              val typedArgExpr = argExpr.asExprOf[t]
              '{ $instanceExpr.toCss($typedArgExpr) }
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(s"No ToCss instance found for type ${argType.show}")
      }
    }

    val processedArgsExpr: Expr[Seq[String]] = Expr.ofSeq(processedArgs)
    '{ InterpolatorRuntime.buildCss($sc, $processedArgsExpr) }
  }

  def jsImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Js] = {
    import quotes.reflect._

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    val processedArgs: Seq[Expr[String]] = argExprs.map { argExpr =>
      val argType  = argExpr.asTerm.tpe.widen
      val toJsTc   = TypeRepr.of[ToJs]
      val toJsType = toJsTc.appliedTo(argType)
      Implicits.search(toJsType) match {
        case success: ImplicitSearchSuccess =>
          argType.asType match {
            case '[t] =>
              val instanceExpr = success.tree.asExprOf[ToJs[t]]
              val typedArgExpr = argExpr.asExprOf[t]
              '{ $instanceExpr.toJs($typedArgExpr) }
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(s"No ToJs instance found for type ${argType.show}")
      }
    }

    val processedArgsExpr: Expr[Seq[String]] = Expr.ofSeq(processedArgs)
    '{ InterpolatorRuntime.buildJs($sc, $processedArgsExpr) }
  }

  def htmlImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Dom] = {
    import quotes.reflect._

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    val processedArgs: Seq[Expr[Vector[Dom]]] = argExprs.map { argExpr =>
      val argType        = argExpr.asTerm.tpe.widen
      val toElementsTc   = TypeRepr.of[ToElements]
      val toElementsType = toElementsTc.appliedTo(argType)
      Implicits.search(toElementsType) match {
        case success: ImplicitSearchSuccess =>
          argType.asType match {
            case '[t] =>
              val instanceExpr = success.tree.asExprOf[ToElements[t]]
              val typedArgExpr = argExpr.asExprOf[t]
              '{ $instanceExpr.toElements($typedArgExpr) }
          }
        case _: ImplicitSearchFailure =>
          report.errorAndAbort(s"No ToElements instance found for type ${argType.show}")
      }
    }

    val processedArgsExpr: Expr[Seq[Vector[Dom]]] = Expr.ofSeq(processedArgs)
    '{ InterpolatorRuntime.buildHtml($sc, $processedArgsExpr) }
  }
}
