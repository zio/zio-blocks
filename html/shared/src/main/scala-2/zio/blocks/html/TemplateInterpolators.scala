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

  /**
   * Shared implicit search for the string-producing template typeclasses. The
   * css/js/selector interpolators differ only in the typeclass searched and the
   * conversion method applied, so all three route through here.
   */
  private def summonStringArg(
    c: blackbox.Context
  )(argExpr: c.Expr[Any], tc: c.Type, method: c.TermName, tcName: String): c.Tree = {
    import c.universe._
    val argType  = argExpr.actualType.widen
    val applied  = appliedType(tc.typeConstructor, argType)
    val instance = c.inferImplicitValue(applied, silent = true)
    if (instance == EmptyTree) {
      c.abort(argExpr.tree.pos, s"No $tcName instance found for type $argType")
    }
    q"$instance.$method(${argExpr.tree})"
  }

  /**
   * Shared implicit search returning the raw instance tree, for call sites that
   * wrap the conversion themselves (the html attr/content branches).
   */
  private def summonInstance(
    c: blackbox.Context
  )(argType: c.Type, tc: c.Type, tcName: String, pos: c.Position): c.Tree = {
    import c.universe._
    val applied  = appliedType(tc.typeConstructor, argType)
    val instance = c.inferImplicitValue(applied, silent = true)
    if (instance == EmptyTree) {
      c.abort(pos, s"No $tcName instance found for type $argType")
    }
    instance
  }

  /**
   * One generic implementation for the css/js/selector interpolators: converts
   * each argument through the given typeclass and delegates to the given
   * runtime builder.
   */
  private def stringTemplate[R](
    c: blackbox.Context
  )(args: Seq[c.Expr[Any]], tc: c.Type, method: c.TermName, tcName: String)(
    build: (c.Tree, c.Tree) => c.Tree
  ): c.Expr[R] = {
    import c.universe._
    val processedArgs = args.map(argExpr => summonStringArg(c)(argExpr, tc, method, tcName))
    val scTree        = c.prefix.tree.asInstanceOf[Apply].args.head
    c.Expr[R](build(scTree, q"_root_.scala.Seq(..$processedArgs)"))
  }

  def cssImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Css] = {
    import c.universe._
    stringTemplate[Css](c)(args, typeOf[ToCss[_]], TermName("toCss"), "ToCss") { (scTree, argsTree) =>
      q"_root_.zio.blocks.html.InterpolatorRuntime.buildCss($scTree, $argsTree)"
    }
  }

  def jsImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Js] = {
    import c.universe._
    stringTemplate[Js](c)(args, typeOf[ToJs[_]], TermName("toJs"), "ToJs") { (scTree, argsTree) =>
      q"_root_.zio.blocks.html.InterpolatorRuntime.buildJs($scTree, $argsTree)"
    }
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
          val instance = summonInstance(c)(argType, typeOf[ToAttrValue[_]], "ToAttrValue", argExpr.tree.pos)
          q"_root_.scala.Left($instance.toAttrValue(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.html.Dom]]"

        case HtmlContext.Content =>
          val instance = summonInstance(c)(argType, typeOf[ToElements[_]], "ToElements", argExpr.tree.pos)
          q"_root_.scala.Right($instance.toElements(${argExpr.tree})): _root_.scala.util.Either[_root_.java.lang.String, _root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.html.Dom]]"
      }
    }

    val scExpr   = c.Expr[StringContext](scTree)
    val argsExpr = c.Expr[Seq[Either[String, Chunk[Dom]]]](q"_root_.scala.Seq(..$processedArgs)")
    reify(InterpolatorRuntime.buildHtml(scExpr.splice, argsExpr.splice))
  }

  def selectorImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[CssSelector] = {
    import c.universe._
    stringTemplate[CssSelector](c)(args, typeOf[ToCss[_]], TermName("toCss"), "ToCss") { (scTree, argsTree) =>
      q"_root_.zio.blocks.html.InterpolatorRuntime.buildSelector($scTree, $argsTree)"
    }
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
