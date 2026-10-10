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

import scala.quoted._
import zio.blocks.chunk.Chunk

/**
 * Provides string interpolators for HTML, CSS, JavaScript, and CSS selectors.
 *
 * Interpolators:
 *   - `html"..."` — produces [[Dom]], position-aware: uses [[ToAttrValue]] for
 *     `=$$value`, [[ToElements]] for content
 *   - `css"..."` — produces [[Css]], uses [[ToCss]] for interpolated values
 *   - `js"..."` — produces [[Js]], uses [[ToJs]] for interpolated values
 *     (strings are quoted and escaped)
 *   - `selector"..."` — produces [[CssSelector]], uses [[ToCss]] for
 *     interpolated values
 *
 * On Scala 3, zero-argument interpolations are constant-folded at compile time.
 */

trait TemplateInterpolators {

  extension (inline sc: StringContext) {
    inline def css(inline args: Any*): Css              = ${ TemplateMacros.cssImpl('sc, 'args) }
    inline def js(inline args: Any*): Js                = ${ TemplateMacros.jsImpl('sc, 'args) }
    inline def html(inline args: Any*): Dom             = ${ TemplateMacros.htmlImpl('sc, 'args) }
    inline def selector(inline args: Any*): CssSelector = ${ TemplateMacros.selectorImpl('sc, 'args) }
  }
}

private[html] object TemplateMacros {

  private def templateArgs(args: Expr[Seq[Any]])(using Quotes): Seq[Expr[Any]] =
    args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

  private def templateParts(sc: Expr[StringContext])(using Quotes): Option[Seq[String]] =
    sc match {
      case '{ StringContext(${ Varargs(Exprs(partLiterals)) }*) } => Some(partLiterals.toSeq)
      case _                                                      => None
    }

  /**
   * Shared implicit search for the string-producing template typeclasses. The
   * css/js/selector interpolators differ only in the typeclass searched and the
   * conversion method applied, so all three route through here.
   */
  private def summonStringArg(using
    Quotes
  )(
    argExpr: Expr[Any],
    tc: quotes.reflect.TypeRepr,
    method: String,
    tcName: String
  ): Expr[String] = {
    import quotes.reflect._
    val argType = argExpr.asTerm.tpe.widen
    Implicits.search(tc.appliedTo(argType)) match {
      case success: ImplicitSearchSuccess =>
        Apply(Select.unique(success.tree, method), List(argExpr.asTerm)).asExprOf[String]
      case _: ImplicitSearchFailure =>
        report.errorAndAbort(s"No $tcName instance found for type ${argType.show}")
    }
  }

  /**
   * Shared implicit search returning the raw instance tree, for call sites that
   * wrap the conversion themselves (the html attr/content branches).
   */
  private def summonInstance(using
    Quotes
  )(
    argType: quotes.reflect.TypeRepr,
    tc: quotes.reflect.TypeRepr,
    tcName: String
  ): quotes.reflect.Term = {
    import quotes.reflect._
    Implicits.search(tc.appliedTo(argType)) match {
      case success: ImplicitSearchSuccess => success.tree
      case _: ImplicitSearchFailure       =>
        report.errorAndAbort(s"No $tcName instance found for type ${argType.show}")
    }
  }

  /**
   * One generic implementation for the css/js/selector interpolators:
   * constant-folds empty-arg templates, otherwise converts each argument
   * through the given typeclass and delegates to the given runtime builder.
   */
  private def stringTemplate[R: Type](using
    Quotes
  )(
    sc: Expr[StringContext],
    args: Expr[Seq[Any]],
    tc: quotes.reflect.TypeRepr,
    method: String,
    tcName: String,
    const: String => Expr[R],
    build: (Expr[StringContext], Expr[Seq[String]]) => Expr[R]
  ): Expr[R] = {
    val argExprs = templateArgs(args)
    if (argExprs.isEmpty)
      templateParts(sc) match {
        case Some(parts) => return const(parts.mkString)
        case None        => // fall through to runtime
      }
    build(sc, Expr.ofSeq(argExprs.map(a => summonStringArg(a, tc, method, tcName))))
  }

  def cssImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Css] = {
    import quotes.reflect._
    stringTemplate[Css](
      sc,
      args,
      TypeRepr.of[ToCss],
      "toCss",
      "ToCss",
      constant => '{ Css.Raw(${ Expr(constant) }) },
      (scExpr, argExprs) => '{ InterpolatorRuntime.buildCss($scExpr, $argExprs) }
    )
  }

  def jsImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Js] = {
    import quotes.reflect._
    stringTemplate[Js](
      sc,
      args,
      TypeRepr.of[ToJs],
      "toJs",
      "ToJs",
      constant => '{ Js(${ Expr(constant) }) },
      (scExpr, argExprs) => '{ InterpolatorRuntime.buildJs($scExpr, $argExprs) }
    )
  }

  def htmlImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Dom] = {
    import quotes.reflect._

    val argExprs: Seq[Expr[Any]] = templateArgs(args)

    if (argExprs.isEmpty)
      sc match {
        case '{ StringContext(${ Varargs(Exprs(partLiterals)) }*) } =>
          validateStaticHtml(partLiterals.mkString)
        case _ => ()
      }

    val parts: Seq[String] = templateParts(sc).getOrElse(Seq.empty)

    val contexts = determineContexts(parts)

    val processedArgs: Seq[Expr[Either[String, Chunk[Dom]]]] = argExprs.zipWithIndex.map { case (argExpr, idx) =>
      val context = if (idx < contexts.length) contexts(idx) else HtmlContext.Content
      val argType = argExpr.asTerm.tpe.widen

      context match {
        case HtmlContext.AttrValue =>
          val inst = summonInstance(argType, TypeRepr.of[ToAttrValue], "ToAttrValue")
          argType.asType match {
            case '[t] =>
              val instExpr = inst.asExprOf[ToAttrValue[t]]
              val arg      = argExpr.asExprOf[t]
              '{ Left($instExpr.toAttrValue($arg)) }
          }

        case HtmlContext.Content =>
          val inst = summonInstance(argType, TypeRepr.of[ToElements], "ToElements")
          argType.asType match {
            case '[t] =>
              val instExpr = inst.asExprOf[ToElements[t]]
              val arg      = argExpr.asExprOf[t]
              '{ Right($instExpr.toElements($arg)) }
          }
      }
    }

    val processedArgsExpr: Expr[Seq[Either[String, Chunk[Dom]]]] = Expr.ofSeq(processedArgs)
    '{ InterpolatorRuntime.buildHtml($sc, $processedArgsExpr) }
  }

  private def validateStaticHtml(input: String)(using Quotes): Unit = {
    import quotes.reflect.report
    val parsed = InterpolatorRuntime.parseHtml(input)
    if (parsed.length != 1) {
      report.errorAndAbort(
        "html interpolator requires exactly one root node for static templates. " +
          s"Found ${parsed.length} top-level nodes. Wrap them in a parent element."
      )
    }
  }

  def selectorImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[CssSelector] = {
    import quotes.reflect._
    stringTemplate[CssSelector](
      sc,
      args,
      TypeRepr.of[ToCss],
      "toCss",
      "ToCss",
      constant => '{ CssSelector.Raw(${ Expr(constant) }) },
      (scExpr, argExprs) => '{ InterpolatorRuntime.buildSelector($scExpr, $argExprs) }
    )
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
