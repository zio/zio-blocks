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
 * The `html"..."`, `css"..."`, `js"..."` and `selector"..."` string
 * interpolators. The `zio.blocks.html` package object mixes this trait in, so
 * `import zio.blocks.html._` enables them. Each converts its interpolated
 * values with a type class chosen at compile time ([[ToAttrValue]] or
 * [[ToElements]] for `html`, [[ToCss]] for `css` and `selector`, [[ToJs]] for
 * `js`), and a value without an instance is a compile error that names the
 * missing instance, as in `No ToJs instance found for type ...`.
 *
 * {{{
 * val brand = CssColor.Rgb(200, 0, 0)
 * css"a { color: \$brand }".render
 * // a { color: rgb(200,0,0) }
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#string-interpolators String Interpolators]]
 */
trait TemplateInterpolators {

  extension (inline sc: StringContext) {

    /**
     * Builds [[Css.Raw]] from the template, converting each value with
     * [[ToCss]]. A `String` value has `\`, quotes, `<`, `>` and `&` escaped but
     * is not quoted, and `;`, `{` and `}` pass through, so do not interpolate
     * untrusted text.
     *
     * {{{
     * val gap = 16
     * css"main { gap: \${gap}px }".render
     * // main { gap: 16px }
     * }}}
     */
    inline def css(inline args: Any*): Css = ${ TemplateMacros.cssImpl('sc, 'args) }

    /**
     * Builds a [[Js]] value from the template, converting each value into a
     * JavaScript literal with [[ToJs]]: a string is quoted and escaped (`<`,
     * `>` and `&` become Unicode escapes, so it cannot close a script), numbers
     * and booleans are written as literals, `None` as `null`, and lists and
     * maps as JSON. The template text itself is not checked.
     *
     * {{{
     * val shopper = "Ann \"VIP\""
     * js"greet(\$shopper, \${3})".value
     * // greet("Ann \"VIP\"", 3)
     * }}}
     */
    inline def js(inline args: Any*): Js = ${ TemplateMacros.jsImpl('sc, 'args) }

    /**
     * Parses the template into a [[Dom]] tree with a single root node.
     *
     * A value whose preceding text ends with `=`, `='` or `="` (ignoring
     * spaces) is an attribute value: it is converted with [[ToAttrValue]] and
     * quoted when the template has no quotes. Any other value is content,
     * converted with [[ToElements]]: a string becomes a text node that is
     * escaped when rendered, and a [[Dom]] value is inserted as is. Tag and
     * attribute names are lowercased, and comments, `<!DOCTYPE ...>` and
     * processing instructions are dropped. The parser creates plain elements,
     * so `html"<br>"` renders like `br` but is not equal to it.
     *
     * {{{
     * val item = "Tea & cake"
     * val qty  = 2
     * html"""<li class="item" data-qty=\$qty>\$item</li>""".render
     * // <li class="item" data-qty="2">Tea &amp; cake</li>
     * }}}
     *
     * The template must hold exactly one root node, and text around the root, a
     * leading newline included, counts as a node. On Scala 3 a template without
     * values that breaks this rule does not compile; otherwise the call throws
     * `java.lang.IllegalArgumentException` at run time.
     *
     * Known bugs:
     *   - string attribute values are escaped twice, so `href=\$url` with `a&b`
     *     renders `a&amp;amp;b`; entities written in the template, such as
     *     `&amp;`, are escaped again too;
     *   - a value inside a quoted attribute value, as in `id="user-\$n"`, is
     *     treated as content and leaves control characters in the attribute;
     *   - any `=` before a value makes it an attribute value, so
     *     `<p>Total = \$n</p>` renders `Total = &quot;2&quot;`;
     *   - content values inside `<script>` and `<style>` are inserted without
     *     escaping, so a string there can add code; use `script(js"...")`
     *     instead.
     *
     * @throws java.lang.IllegalArgumentException
     *   if the template does not hold exactly one root node
     */
    inline def html(inline args: Any*): Dom = ${ TemplateMacros.htmlImpl('sc, 'args) }

    /**
     * Builds a [[CssSelector.Raw]] from the template, converting each value
     * with [[ToCss]]. The result renders as the text, but `Dom#select` never
     * matches a [[CssSelector.Raw]], which is a known bug, so build selectors
     * for querying from the [[CssSelector]] cases.
     *
     * {{{
     * val section = "sale"
     * selector".\$section > a".render
     * // .sale > a
     * }}}
     */
    inline def selector(inline args: Any*): CssSelector = ${ TemplateMacros.selectorImpl('sc, 'args) }
  }
}

/** Macro implementations of the interpolators. */
private[html] object TemplateMacros {

  /** Expands `css"..."`; a template without values becomes a constant. */
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

  /** Expands `js"..."`; a template without values becomes a constant. */
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

  /**
   * Expands `html"..."`, picking [[ToAttrValue]] or [[ToElements]] per value.
   */
  def htmlImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Dom] = {
    import quotes.reflect._

    val argExprs: Seq[Expr[Any]] = args match {
      case Varargs(exprs) => exprs.toSeq
      case _              => Seq.empty
    }

    if (argExprs.isEmpty)
      sc match {
        case '{ StringContext(${ Varargs(Exprs(partLiterals)) }*) } =>
          validateStaticHtml(partLiterals.mkString)
        case _ => ()
      }

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

  /**
   * Fails compilation when a template without values does not hold exactly one
   * root node.
   */
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

  /** Expands `selector"..."`; a template without values becomes a constant. */
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

  /**
   * Classifies each value as an attribute value or content from the text before
   * it.
   */
  private def determineContexts(parts: Seq[String]): Seq[HtmlContext] =
    parts.init.map { part =>
      val trimmed = part.trim
      if (trimmed.endsWith("=") || trimmed.endsWith("='") || trimmed.endsWith("=\"")) HtmlContext.AttrValue
      else HtmlContext.Content
    }
}
