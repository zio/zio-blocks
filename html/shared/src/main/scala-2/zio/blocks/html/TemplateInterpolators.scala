/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.html

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

private[html] object TemplateMacros {

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
        case HtmlContext.AttrValue =>
          val toAttrValueTc   = typeOf[ToAttrValue[_]].typeConstructor
          val toAttrValueType = appliedType(toAttrValueTc, argType)
          val instance        = c.inferImplicitValue(toAttrValueType, silent = true)
          if (instance == EmptyTree) {
            c.abort(argExpr.tree.pos, s"No ToAttrValue instance found for type $argType")
          }
          q"_root_.scala.Left($instance.toAttrValue(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.html.Dom]]"

        case HtmlContext.Content =>
          val toElementsTc   = typeOf[ToElements[_]].typeConstructor
          val toElementsType = appliedType(toElementsTc, argType)
          val instance       = c.inferImplicitValue(toElementsType, silent = true)
          if (instance == EmptyTree) {
            c.abort(argExpr.tree.pos, s"No ToElements instance found for type $argType")
          }
          q"_root_.scala.Right($instance.toElements(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.html.Dom]]"
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
    case object AttrValue extends HtmlContext
    case object Content   extends HtmlContext
  }

  private def determineContexts(parts: Seq[String]): Seq[HtmlContext] =
    parts.init.map { part =>
      val trimmed = part.trim
      if (trimmed.endsWith("=") || trimmed.endsWith("='") || trimmed.endsWith("=\"")) HtmlContext.AttrValue
      else HtmlContext.Content
    }
}
