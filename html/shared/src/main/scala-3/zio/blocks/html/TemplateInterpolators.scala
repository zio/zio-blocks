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
