package zio.blocks.template

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

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
}

private[template] object TemplateMacros {

  def cssImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Css] = {
    import c.universe._

    val processedArgs = args.map { argExpr =>
      val argType    = argExpr.actualType.widen
      val toCssTc    = typeOf[ToCss[_]].typeConstructor
      val toCssType  = appliedType(toCssTc, argType)
      val instance   = c.inferImplicitValue(toCssType, silent = true)
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
      val argType   = argExpr.actualType.widen
      val toJsTc    = typeOf[ToJs[_]].typeConstructor
      val toJsType  = appliedType(toJsTc, argType)
      val instance  = c.inferImplicitValue(toJsType, silent = true)
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

    val processedArgs = args.map { argExpr =>
      val argType         = argExpr.actualType.widen
      val toElementsTc    = typeOf[ToElements[_]].typeConstructor
      val toElementsType  = appliedType(toElementsTc, argType)
      val instance        = c.inferImplicitValue(toElementsType, silent = true)
      if (instance == EmptyTree) {
        c.abort(argExpr.tree.pos, s"No ToElements instance found for type $argType")
      }
      q"$instance.toElements(${argExpr.tree})"
    }

    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[Vector[Dom]]](q"_root_.scala.Seq(..$processedArgs)")
    reify(InterpolatorRuntime.buildHtml(scExpr.splice, argsExpr.splice))
  }
}
