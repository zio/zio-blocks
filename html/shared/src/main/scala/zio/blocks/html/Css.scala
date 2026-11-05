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

import zio.blocks.chunk.Chunk

/**
 * CSS content: a [[Css.Rule]] (a selector with declarations), a [[Css.Sheet]]
 * of several items, [[Css.Raw]] text for anything the types do not cover (media
 * queries, keyframes) or a [[Css.Comment]]. `render` gives compact CSS and
 * `render(indent)` an indented form. Embed it with `style(...)` or
 * `style().inlineCss(...)`, or build it with the `css"..."` interpolator, which
 * gives a [[Css.Raw]].
 *
 * {{{
 * import zio.blocks.chunk.Chunk
 *
 * val price = Css.Rule(CssSelector.Class("price"), Chunk(Css.Declaration("color", CssColor.Rgb(200, 0, 0)), Css.Declaration("font-weight", 700)))
 * price.render
 * // .price{color:rgb(200,0,0);font-weight:700;}
 * price.render(2)
 * // .price {
 * //   color: rgb(200,0,0);
 * //   font-weight: 700;
 * // }
 * }}}
 *
 * Declaration values and [[Css.Raw]] text are not checked, so pass only trusted
 * text to them.
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#the-css-adt The CSS ADT]]
 */
sealed trait Css extends Product with Serializable {

  /** Renders compact CSS, with no whitespace added. */
  def render: String

  /**
   * Renders indented CSS with `indent` spaces before each declaration. An
   * `indent` of zero or less gives the output of `render`, except for
   * [[Css.Comment]].
   */
  def render(indent: Int): String
}

/** The CSS cases and their constructors. */
object Css {

  /**
   * A rule: a selector and its declarations, rendered as `selector{a:b;c:d;}`
   * or, indented, with one declaration per line.
   */
  final case class Rule(selector: CssSelector, declarations: Chunk[Declaration]) extends Css {

    /** Renders `selector{property:value;...}`. */
    def render: String = {
      val sb = new java.lang.StringBuilder
      sb.append(selector.render)
      sb.append('{')
      var i = 0
      while (i < declarations.length) {
        val d = declarations(i)
        sb.append(d.property)
        sb.append(':')
        sb.append(d.value)
        sb.append(';')
        i += 1
      }
      sb.append('}')
      sb.toString
    }

    /**
     * Renders the selector, then each declaration on its own line indented by
     * `indent` spaces, then `}`.
     */
    def render(indent: Int): String = {
      if (indent <= 0) return render
      val sb        = new java.lang.StringBuilder
      val indentStr = " " * indent
      sb.append(selector.render)
      sb.append(" {\n")
      var i = 0
      while (i < declarations.length) {
        val d = declarations(i)
        sb.append(indentStr)
        sb.append(d.property)
        sb.append(": ")
        sb.append(d.value)
        sb.append(";\n")
        i += 1
      }
      sb.append('}')
      sb.toString
    }
  }

  /**
   * A property and its value, as in `color: red`. Build one with
   * `Css.Declaration(property, value)`, which converts the value with its
   * [[ToCss]] instance, so a number, a [[CssLength]] or a [[CssColor]] can be
   * passed directly.
   *
   * For a `String` value the result depends on the Scala version, which is a
   * known bug. On Scala 3 the string goes through `ToCss[String]`, which
   * escapes quotes, `<`, `>` and `&`, so `"\"Inter\", sans-serif"` renders as
   * `\"Inter\", sans-serif`, which the browser reads as a font named `"Inter"`
   * with the quotes, so the Inter font is not used. On Scala 2 the case class's
   * own `apply` is chosen and the string is used as written.
   */
  final case class Declaration private (property: String, value: String)

  /** Constructors for [[Css.Declaration]]. */
  object Declaration {

    /**
     * Creates a declaration, converting `value` with its [[ToCss]] instance.
     */
    def apply[A](property: String, value: A)(implicit ev: ToCss[A]): Declaration =
      new Declaration(property, ev.toCss(value))
  }

  /** Several CSS items rendered one after another. */
  final case class Sheet(rules: Chunk[Css]) extends Css {

    /** Renders each item compactly, with nothing between them. */
    def render: String = {
      val sb = new java.lang.StringBuilder
      var i  = 0
      while (i < rules.length) {
        sb.append(rules(i).render)
        i += 1
      }
      sb.toString
    }

    /** Renders each item indented, separated by newlines. */
    def render(indent: Int): String = {
      if (indent <= 0) return render
      val sb = new java.lang.StringBuilder
      var i  = 0
      while (i < rules.length) {
        if (i > 0) sb.append('\n')
        sb.append(rules(i).render(indent))
        i += 1
      }
      sb.toString
    }
  }

  /**
   * CSS text used as written, for media queries, keyframes and other at-rules.
   * It is not checked or escaped.
   */
  final case class Raw(value: String) extends Css {

    /** Returns `value`. */
    def render: String = value

    /** Returns `value`; the text is not re-indented. */
    def render(indent: Int): String = value

    /** Returns a copy with `stripMargin` applied to the text. */
    def stripMargin: Raw = Raw(value.stripMargin)
  }

  /**
   * A CSS comment. Each `*` followed by `/` in `text` gets a space between
   * them, so the text cannot end the comment early.
   */
  final case class Comment(text: String) extends Css {

    /** Renders the comment with no spaces inside the delimiters. */
    def render: String = "/*" + text.replace("*/", "* /") + "*/"

    /**
     * Renders the comment with one space inside each delimiter, whatever
     * `indent` is.
     */
    def render(indent: Int): String = "/* " + text.replace("*/", "* /") + " */"
  }

  /** Creates [[Css.Raw]] text; the same as `Css.Raw(value)`. */
  def apply(value: String): Css = Raw(value)
}
