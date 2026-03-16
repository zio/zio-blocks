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

    val contexts = determineContexts(parts)

    val processedArgs: Seq[Expr[Either[String, Chunk[Dom]]]] = argExprs.zipWithIndex.map { case (argExpr, idx) =>
      val context = if (idx < contexts.length) contexts(idx) else HtmlContext.Content
      val argType = argExpr.asTerm.tpe.widen

      context match {
        case HtmlContext.TagName =>
          val tc = TypeRepr.of[ToTagName].appliedTo(argType)
          Implicits.search(tc) match {
            case success: ImplicitSearchSuccess =>
              argType.asType match {
                case '[t] =>
                  val inst = success.tree.asExprOf[ToTagName[t]]
                  val arg  = argExpr.asExprOf[t]
                  '{ Left($inst.toTagName($arg)) }
              }
            case _: ImplicitSearchFailure =>
              report.errorAndAbort(
                s"No ToTagName instance found for type ${argType.show}. Tag name interpolation requires SafeTagName."
              )
          }

        case HtmlContext.AttrName =>
          val tc = TypeRepr.of[ToAttrName].appliedTo(argType)
          Implicits.search(tc) match {
            case success: ImplicitSearchSuccess =>
              argType.asType match {
                case '[t] =>
                  val inst = success.tree.asExprOf[ToAttrName[t]]
                  val arg  = argExpr.asExprOf[t]
                  '{ Left($inst.toAttrName($arg)) }
              }
            case _: ImplicitSearchFailure =>
              report.errorAndAbort(
                s"No ToAttrName instance found for type ${argType.show}. Attribute name interpolation requires SafeAttrName or EventAttrName."
              )
          }

        case HtmlContext.AttrValue =>
          val tc = TypeRepr.of[ToAttrValue].appliedTo(argType)
          Implicits.search(tc) match {
            case success: ImplicitSearchSuccess =>
              argType.asType match {
                case '[t] =>
                  val inst = success.tree.asExprOf[ToAttrValue[t]]
                  val arg  = argExpr.asExprOf[t]
                  '{ Left($inst.toAttrValue($arg)) }
              }
            case _: ImplicitSearchFailure =>
              report.errorAndAbort(s"No ToAttrValue instance found for type ${argType.show}")
          }

        case HtmlContext.Content =>
          val tc = TypeRepr.of[ToElements].appliedTo(argType)
          Implicits.search(tc) match {
            case success: ImplicitSearchSuccess =>
              argType.asType match {
                case '[t] =>
                  val inst = success.tree.asExprOf[ToElements[t]]
                  val arg  = argExpr.asExprOf[t]
                  '{ Right($inst.toElements($arg)) }
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

  private sealed trait HtmlContext
  private object HtmlContext {
    case object TagName   extends HtmlContext
    case object AttrName  extends HtmlContext
    case object AttrValue extends HtmlContext
    case object Content   extends HtmlContext
  }

  private def determineContexts(parts: Seq[String]): Seq[HtmlContext] = {
    sealed trait State
    case object InText                    extends State
    case object InTagName                 extends State
    case object InClosingTagName          extends State
    case class InTag(afterSpace: Boolean) extends State
    case object InAttrEq                  extends State
    case class InAttrValueQ(quote: Char)  extends State
    case object InAttrValue               extends State

    var state: State = InText

    parts.init.map { part =>
      var i = 0
      while (i < part.length) {
        val c = part.charAt(i)
        state match {
          case InText =>
            if (c == '<') {
              if (i + 1 < part.length && part.charAt(i + 1) == '/') {
                state = InClosingTagName
                i += 1
              } else {
                state = InTagName
              }
            }
          case InTagName =>
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') state = InTag(afterSpace = true)
            else if (c == '>') state = InText
            else if (c == '/') {
              if (i + 1 < part.length && part.charAt(i + 1) == '>') { state = InText; i += 1 }
            }
          case InClosingTagName =>
            if (c == '>') state = InText
          case InTag(_) =>
            if (c == '>') state = InText
            else if (c == '/') {
              if (i + 1 < part.length && part.charAt(i + 1) == '>') { state = InText; i += 1 }
            } else if (c == '=') state = InAttrEq
            else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') state = InTag(afterSpace = true)
            else state = InTag(afterSpace = false)
          case InAttrEq =>
            if (c == '"') state = InAttrValueQ('"')
            else if (c == '\'') state = InAttrValueQ('\'')
            else if (c == ' ' || c == '\t') ()
            else state = InAttrValue
          case InAttrValueQ(q) =>
            if (c == q) state = InTag(afterSpace = false)
          case InAttrValue =>
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') state = InTag(afterSpace = true)
            else if (c == '>') state = InText
        }
        i += 1
      }

      state match {
        case InTagName | InClosingTagName             => HtmlContext.TagName
        case InTag(true)                              => HtmlContext.AttrName
        case InTag(false)                             => HtmlContext.AttrName
        case InAttrEq | InAttrValue | _: InAttrValueQ => HtmlContext.AttrValue
        case InText                                   => HtmlContext.Content
      }
    }
  }
}
