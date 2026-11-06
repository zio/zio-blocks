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

/**
 * A CSS selector, for [[Css.Rule]] and for querying a tree with `Dom#select`.
 * Build one from the cases ([[CssSelector.Element]], [[CssSelector.Class]],
 * [[CssSelector.Id]], ...) or from a DSL element, which stands for its tag, and
 * combine selectors with the [[CssSelectable]] operators: `>` (child), `>>`
 * (descendant), `+` (adjacent sibling), `~` (general sibling), `&` (both) and
 * `|` (either), plus pseudo-classes such as `hover`. `render` gives the CSS
 * text.
 *
 * Scala's operator precedence applies: `|` binds loosest, then `&`, then `>`
 * and `>>`, then `+`, and `~` binds tightest, so add parentheses when mixing
 * operators.
 *
 * {{{
 * val sel = (div & CssSelector.Class("card")) > p.hover
 * sel.render
 * // div.card > p:hover
 * }}}
 *
 * Attribute selector values are escaped (`\` and `"`), but tag, class, id and
 * attribute names are used as written. [[DomSelection#select]] describes which
 * selectors match a tree.
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#css-selectors-and-the-dsl CSS Selectors and the DSL]]
 */
sealed trait CssSelector extends CssSelectable with Product with Serializable {

  /**
   * This selector, so a selector works wherever a [[CssSelectable]] is
   * expected.
   */
  val selector: CssSelector = this

  /** Returns the selector as CSS text. */
  def render: String

  /** Returns `render`. */
  override def toString: String = render

}

/** The selector cases and short constructors for them. */
object CssSelector {

  /** Creates a [[CssSelector.Raw]] selector. */
  def raw(value: String): Raw = Raw(value)

  /** Creates a class selector, rendered as `.name`. */
  def `class`(name: String): Class = Class(name)

  /** Creates a tag selector, rendered as `tag`. */
  def element(tag: String): Element = Element(tag)

  /** Creates an id selector, rendered as `#name`. */
  def id(name: String): Id = Id(name)

  /** The universal selector `*`. */
  def universal: Universal.type = Universal

  /** How an attribute selector compares the attribute value. */
  sealed trait AttributeMatch extends Product with Serializable

  /** The attribute comparisons. */
  object AttributeMatch {

    /** Matches a value containing `value`; rendered with `*=`. */
    final case class Contains(value: String) extends AttributeMatch

    /** Matches a value ending with `value`; rendered with `\$=`. */
    final case class EndsWith(value: String) extends AttributeMatch

    /** Matches a value equal to `value`; rendered with `=`. */
    final case class Exact(value: String) extends AttributeMatch

    /**
     * Matches `value` or a value starting with `value` and a hyphen, as for
     * `lang`; rendered with `|=`.
     */
    final case class HyphenPrefix(value: String) extends AttributeMatch

    /** Matches a value starting with `value`; rendered with `^=`. */
    final case class StartsWith(value: String) extends AttributeMatch

    /**
     * Matches a value with `value` as one of its space-separated words;
     * rendered with `~=`.
     */
    final case class WhitespaceContains(value: String) extends AttributeMatch
  }

  /**
   * The element matching `next` right after one matching `previous`, rendered
   * as `previous + next`. `Dom#select` never matches it.
   */
  final case class AdjacentSibling(previous: CssSelector, next: CssSelector) extends CssSelector {

    /** Renders `previous + next`. */
    def render: String = s"$previous + $next"
  }

  /**
   * An element matching both selectors, rendered by joining them, as in
   * `div.card`.
   */
  final case class And(left: CssSelector, right: CssSelector) extends CssSelector {

    /** Renders the two selectors joined without a space. */
    def render: String = s"$left$right"
  }

  /**
   * An element matching `inner` that has `attribute`, compared with `matcher`
   * when one is given, rendered as `inner[attribute="value"]`.
   */
  final case class Attribute(inner: CssSelector, attribute: String, matcher: Option[AttributeMatch])
      extends CssSelector {

    /**
     * Renders `inner` followed by the bracketed attribute test, with the value
     * escaped.
     */
    def render: String = {
      val base     = if (inner.toString != "") inner.toString else ""
      val attrPart = matcher match {
        case None                                           => s"[$attribute]"
        case Some(AttributeMatch.Contains(value))           => s"""[$attribute*="${escapeCssSelectorValue(value)}"]"""
        case Some(AttributeMatch.EndsWith(value))           => s"""[$attribute$$="${escapeCssSelectorValue(value)}"]"""
        case Some(AttributeMatch.Exact(value))              => s"""[$attribute="${escapeCssSelectorValue(value)}"]"""
        case Some(AttributeMatch.HyphenPrefix(value))       => s"""[$attribute|="${escapeCssSelectorValue(value)}"]"""
        case Some(AttributeMatch.StartsWith(value))         => s"""[$attribute^="${escapeCssSelectorValue(value)}"]"""
        case Some(AttributeMatch.WhitespaceContains(value)) => s"""[$attribute~="${escapeCssSelectorValue(value)}"]"""
      }
      s"$base$attrPart"
    }
  }

  /**
   * An element matching `child` whose parent matches `parent`, rendered as
   * `parent > child`.
   */
  final case class Child(parent: CssSelector, child: CssSelector) extends CssSelector {

    /** Renders `parent > child`. */
    def render: String = s"$parent > $child"
  }

  /** An element with class `name`, rendered as `.name`. */
  final case class Class(name: String) extends CssSelector {

    /** Renders `.name`. */
    def render: String = s".$name"
  }

  /**
   * An element matching `descendant` below one matching `parent`, rendered as
   * `parent descendant`.
   */
  final case class Descendant(parent: CssSelector, descendant: CssSelector) extends CssSelector {

    /** Renders the two selectors separated by a space. */
    def render: String = s"$parent $descendant"
  }

  /** An element with tag `tag`. */
  final case class Element(tag: String) extends CssSelector {

    /** Renders `tag`. */
    def render: String = tag
  }

  /**
   * An element matching `sibling` after one matching `previous` with the same
   * parent, rendered as `previous ~ sibling`. `Dom#select` never matches it.
   */
  final case class GeneralSibling(previous: CssSelector, sibling: CssSelector) extends CssSelector {

    /** Renders `previous ~ sibling`. */
    def render: String = s"$previous ~ $sibling"
  }

  /** An element whose `id` is `name`, rendered as `#name`. */
  final case class Id(name: String) extends CssSelector {

    /** Renders `#name`. */
    def render: String = s"#$name"
  }

  /**
   * An element matching `inner` but not `negated`, rendered as
   * `inner:not(negated)`.
   */
  final case class Not(inner: CssSelector, negated: CssSelector) extends CssSelector {

    /** Renders `inner:not(negated)`. */
    def render: String = s"$inner:not($negated)"
  }

  /** An element matching either selector, rendered as `left, right`. */
  final case class Or(left: CssSelector, right: CssSelector) extends CssSelector {

    /** Renders `left, right`. */
    def render: String = s"$left, $right"
  }

  /**
   * `inner` with a pseudo-class such as `hover`, rendered as `inner:hover`.
   * `Dom#select` ignores the pseudo-class and matches `inner`.
   */
  final case class PseudoClass(inner: CssSelector, pseudoClass: String) extends CssSelector {

    /** Renders `inner:pseudoClass`. */
    def render: String = s"$inner:$pseudoClass"
  }

  /**
   * `inner` with a pseudo-element such as `before`, rendered as
   * `inner::before`. `Dom#select` ignores the pseudo-element and matches
   * `inner`.
   */
  final case class PseudoElement(inner: CssSelector, pseudoElement: String) extends CssSelector {

    /** Renders `inner::pseudoElement`. */
    def render: String = s"$inner::$pseudoElement"
  }

  /**
   * Selector text used as written. `selector"..."` builds one. `Dom#select`
   * never matches it, which is a known bug.
   */
  final case class Raw(value: String) extends CssSelector {

    /** Returns `value`. */
    def render: String = value
  }

  /** The universal selector, matching any element. */
  case object Universal extends CssSelector {

    /** Renders `*`. */
    def render: String = "*"
  }

  private def escapeCssSelectorValue(v: String): String =
    v.replace("\\", "\\\\").replace("\"", "\\\"")

}
