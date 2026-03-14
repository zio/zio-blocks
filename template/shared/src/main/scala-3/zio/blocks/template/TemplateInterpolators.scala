package zio.blocks.template

import scala.quoted._
import zio.blocks.chunk.Chunk

trait TemplateInterpolators {

  extension (inline sc: StringContext) {
    inline def css(inline args: Any*): Css              = ${ TemplateMacros.cssImpl('sc, 'args) }
    inline def js(inline args: Any*): Js                = ${ TemplateMacros.jsImpl('sc, 'args) }
    inline def html(inline args: Any*): Dom             = ${ TemplateMacros.htmlImpl('sc, 'args) }
    inline def selector(inline args: Any*): CssSelector = ${ TemplateMacros.selectorImpl('sc, 'args) }
  }
}

private[template] object TemplateMacros {

  def cssImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Css] = {
    import quotes.reflect._

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    if (argExprs.isEmpty) {
      sc match {
        case '{ StringContext(${ Varargs(Exprs(partLiterals)) }*) } =>
          val constant = partLiterals.mkString
          return '{ Css.Raw(${ Expr(constant) }) }
        case _ => // fall through to runtime
      }
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

    if (argExprs.isEmpty) {
      sc match {
        case '{ StringContext(${ Varargs(Exprs(partLiterals)) }*) } =>
          val constant = partLiterals.mkString
          return '{ Js(${ Expr(constant) }) }
        case _ => // fall through to runtime
      }
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

    if (argExprs.isEmpty)
      return '{ InterpolatorRuntime.buildHtml($sc, Seq.empty) }

    val parts: Seq[String] = sc match {
      case '{ StringContext(${ Varargs(Exprs(partLiterals)) }*) } => partLiterals.toSeq
      case _                                                      => Seq.empty
    }

    val processedArgs: Seq[Expr[Either[String, Chunk[Dom]]]] = argExprs.zipWithIndex.map { case (argExpr, idx) =>
      val precedingText = if (idx < parts.length) parts(idx) else ""
      val isAttrValue   = precedingText.endsWith("=") ||
        precedingText.endsWith("=\"") ||
        precedingText.endsWith("=\'")

      val argType = argExpr.asTerm.tpe.widen

      if (isAttrValue) {
        val toAttrValueTc   = TypeRepr.of[ToAttrValue]
        val toAttrValueType = toAttrValueTc.appliedTo(argType)
        Implicits.search(toAttrValueType) match {
          case success: ImplicitSearchSuccess =>
            argType.asType match {
              case '[t] =>
                val instanceExpr = success.tree.asExprOf[ToAttrValue[t]]
                val typedArgExpr = argExpr.asExprOf[t]
                '{ Left($instanceExpr.toAttrValue($typedArgExpr)) }
            }
          case _: ImplicitSearchFailure =>
            report.errorAndAbort(s"No ToAttrValue instance found for type ${argType.show}")
        }
      } else {
        val toElementsTc   = TypeRepr.of[ToElements]
        val toElementsType = toElementsTc.appliedTo(argType)
        Implicits.search(toElementsType) match {
          case success: ImplicitSearchSuccess =>
            argType.asType match {
              case '[t] =>
                val instanceExpr = success.tree.asExprOf[ToElements[t]]
                val typedArgExpr = argExpr.asExprOf[t]
                '{ Right($instanceExpr.toElements($typedArgExpr)) }
            }
          case _: ImplicitSearchFailure =>
            report.errorAndAbort(s"No ToElements instance found for type ${argType.show}")
        }
      }
    }

    val processedArgsExpr: Expr[Seq[Either[String, Chunk[Dom]]]] = Expr.ofSeq(processedArgs)
    '{ InterpolatorRuntime.buildHtml($sc, $processedArgsExpr) }
  }

  def selectorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[CssSelector] = {
    import quotes.reflect._

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    if (argExprs.isEmpty) {
      sc match {
        case '{ StringContext(${ Varargs(Exprs(partLiterals)) }*) } =>
          val constant = partLiterals.mkString
          return '{ CssSelector.Raw(${ Expr(constant) }) }
        case _ => // fall through to runtime
      }
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
    '{ InterpolatorRuntime.buildSelector($sc, $processedArgsExpr) }
  }
}
