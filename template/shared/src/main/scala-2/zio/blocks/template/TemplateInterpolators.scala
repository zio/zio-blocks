package zio.blocks.template

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import zio.blocks.chunk.Chunk

trait TemplateInterpolators {

  implicit class CssStringContext(val sc: StringContext) {
    def css(args: Any*): Css = macro TemplateMacros.cssImpl
  }

  implicit class JsStringContext(val sc: StringContext) {
    def js(args: Any*): Js = macro TemplateMacros.jsImpl
  }

  implicit class HtmlStringContext(val sc: StringContext) {
    def html(args: Any*): Dom = macro TemplateMacros.htmlImpl
  }

  implicit class SelectorStringContext(val sc: StringContext) {
    def selector(args: Any*): CssSelector = macro TemplateMacros.selectorImpl
  }
}

private[template] object TemplateMacros {

  def cssImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Css] = {
    import c.universe._

    val processedArgs = args.map { argExpr =>
      val argType   = argExpr.actualType.widen
      val toCssTc   = typeOf[ToCss[_]].typeConstructor
      val toCssType = appliedType(toCssTc, argType)
      val instance  = c.inferImplicitValue(toCssType, silent = true)
      if (instance == EmptyTree) {
        c.abort(argExpr.tree.pos, s"No ToCss instance found for type $argType")
      }
      q"$instance.toCss(${argExpr.tree})"
    }

    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[String]](q"_root_.scala.Seq(..$processedArgs)")
    reify(InterpolatorRuntime.buildCss(scExpr.splice, argsExpr.splice))
  }

  def jsImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Js] = {
    import c.universe._

    val processedArgs = args.map { argExpr =>
      val argType  = argExpr.actualType.widen
      val toJsTc   = typeOf[ToJs[_]].typeConstructor
      val toJsType = appliedType(toJsTc, argType)
      val instance = c.inferImplicitValue(toJsType, silent = true)
      if (instance == EmptyTree) {
        c.abort(argExpr.tree.pos, s"No ToJs instance found for type $argType")
      }
      q"$instance.toJs(${argExpr.tree})"
    }

    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[String]](q"_root_.scala.Seq(..$processedArgs)")
    reify(InterpolatorRuntime.buildJs(scExpr.splice, argsExpr.splice))
  }

  def htmlImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Dom] = {
    import c.universe._

    val scTree             = c.prefix.tree.asInstanceOf[Apply].args.head
    val parts: Seq[String] = scTree match {
      case Apply(_, partLiterals) =>
        partLiterals.collect { case Literal(Constant(s: String)) => s }
      case _ => Seq.empty
    }

    val contexts = determineContexts(parts)

    val processedArgs = args.zipWithIndex.map { case (argExpr, idx) =>
      val context = if (idx < contexts.length) contexts(idx) else HtmlContext.Content
      val argType = argExpr.actualType.widen

      context match {
        case HtmlContext.TagName =>
          val toTagNameTc   = typeOf[ToTagName[_]].typeConstructor
          val toTagNameType = appliedType(toTagNameTc, argType)
          val instance      = c.inferImplicitValue(toTagNameType, silent = true)
          if (instance == EmptyTree) {
            c.abort(
              argExpr.tree.pos,
              s"No ToTagName instance found for type $argType. Tag name interpolation requires SafeTagName."
            )
          }
          q"_root_.scala.Left($instance.toTagName(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.template.Dom]]"

        case HtmlContext.AttrName =>
          val toAttrNameTc   = typeOf[ToAttrName[_]].typeConstructor
          val toAttrNameType = appliedType(toAttrNameTc, argType)
          val instance       = c.inferImplicitValue(toAttrNameType, silent = true)
          if (instance == EmptyTree) {
            c.abort(
              argExpr.tree.pos,
              s"No ToAttrName instance found for type $argType. Attribute name interpolation requires SafeAttrName or EventAttrName."
            )
          }
          q"_root_.scala.Left($instance.toAttrName(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.template.Dom]]"

        case HtmlContext.AttrValue =>
          val toAttrValueTc   = typeOf[ToAttrValue[_]].typeConstructor
          val toAttrValueType = appliedType(toAttrValueTc, argType)
          val instance        = c.inferImplicitValue(toAttrValueType, silent = true)
          if (instance == EmptyTree) {
            c.abort(argExpr.tree.pos, s"No ToAttrValue instance found for type $argType")
          }
          q"_root_.scala.Left($instance.toAttrValue(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.template.Dom]]"

        case HtmlContext.Content =>
          val toElementsTc   = typeOf[ToElements[_]].typeConstructor
          val toElementsType = appliedType(toElementsTc, argType)
          val instance       = c.inferImplicitValue(toElementsType, silent = true)
          if (instance == EmptyTree) {
            c.abort(argExpr.tree.pos, s"No ToElements instance found for type $argType")
          }
          q"_root_.scala.Right($instance.toElements(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.template.Dom]]"
      }
    }

    val scExpr   = c.Expr[StringContext](scTree)
    val argsExpr = c.Expr[Seq[Either[String, Chunk[Dom]]]](q"_root_.scala.Seq(..$processedArgs)")
    reify(InterpolatorRuntime.buildHtml(scExpr.splice, argsExpr.splice))
  }

  def selectorImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[CssSelector] = {
    import c.universe._

    val processedArgs = args.map { argExpr =>
      val argType   = argExpr.actualType.widen
      val toCssTc   = typeOf[ToCss[_]].typeConstructor
      val toCssType = appliedType(toCssTc, argType)
      val instance  = c.inferImplicitValue(toCssType, silent = true)
      if (instance == EmptyTree) {
        c.abort(argExpr.tree.pos, s"No ToCss instance found for type $argType")
      }
      q"$instance.toCss(${argExpr.tree})"
    }

    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[String]](q"_root_.scala.Seq(..$processedArgs)")
    reify(InterpolatorRuntime.buildSelector(scExpr.splice, argsExpr.splice))
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
