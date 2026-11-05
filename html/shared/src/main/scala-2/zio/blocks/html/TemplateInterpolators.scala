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

  /** Adds the `css` interpolator to string literals. */
  implicit class CssStringContext(val sc: StringContext) {

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
    def css(args: Any*): Css = macro TemplateMacros.cssImpl
  }

  /** Adds the `js` interpolator to string literals. */
  implicit class JsStringContext(val sc: StringContext) {

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
    def js(args: Any*): Js = macro TemplateMacros.jsImpl
  }

  /** Adds the `html` interpolator to string literals. */
  implicit class HtmlStringContext(val sc: StringContext) {

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
    def html(args: Any*): Dom = macro TemplateMacros.htmlImpl
  }

  /** Adds the `selector` interpolator to string literals. */
  implicit class SelectorStringContext(val sc: StringContext) {

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
    def selector(args: Any*): CssSelector = macro TemplateMacros.selectorImpl
  }
}

/** Macro implementations of the interpolators. */
private[html] object TemplateMacros {

  /** Expands `css"..."`. */
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

  /** Expands `js"..."`. */
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

  /**
   * Expands `html"..."`, picking [[ToAttrValue]] or [[ToElements]] per value.
   */
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

  /** Expands `selector"..."`. */
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
