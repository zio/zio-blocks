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
 * A value that has a [[CssSelector]] and can be combined with other selectables
 * into larger selectors through combinators, pseudo-classes and attribute
 * matchers.
 */
trait CssSelectable {

  /** The selector this value stands for. */
  val selector: CssSelector

  /** Combines the two with the child combinator, rendered `a > b`. */
  def >(child: CssSelectable): CssSelector = CssSelector.Child(selector, child.selector)

  /**
   * Combines the two with the descendant combinator, rendered with a space,
   * `a b`.
   */
  def >>(descendant: CssSelectable): CssSelector = CssSelector.Descendant(selector, descendant.selector)

  /**
   * Combines the two with the adjacent sibling combinator, rendered `a + b`.
   */
  def +(adjacent: CssSelectable): CssSelector = CssSelector.AdjacentSibling(selector, adjacent.selector)

  /**
   * Joins the two without a separator, so both must match the same element, as
   * in `.card.wide`.
   */
  def &(other: CssSelectable): CssSelector = CssSelector.And(selector, other.selector)

  /** Combines the two with the general sibling combinator, rendered `a ~ b`. */
  def ~(sibling: CssSelectable): CssSelector = CssSelector.GeneralSibling(selector, sibling.selector)

  /** Groups the two as a selector list, rendered `a, b`. */
  def |(other: CssSelectable): CssSelector = CssSelector.Or(selector, other.selector)

  /** Adds the `:active` pseudo-class. */
  def active: CssSelector = CssSelector.PseudoClass(selector, "active")

  /** Named form of `+`: the adjacent sibling combinator. */
  def adjacentSibling(sel: CssSelectable): CssSelector = selector + sel

  /** Adds the `::after` pseudo-element. */
  def after: CssSelector = CssSelector.PseudoElement(selector, "after")

  /** Named form of `&`: joins the two selectors without a separator. */
  def and(sel: CssSelectable): CssSelector = selector & sel

  /** Adds the `::before` pseudo-element. */
  def before: CssSelector = CssSelector.PseudoElement(selector, "before")

  /** Named form of `>`: the child combinator. */
  def child(sel: CssSelectable): CssSelector = selector > sel

  /** Named form of `>>`: the descendant combinator. */
  def descendant(sel: CssSelectable): CssSelector = selector >> sel

  /** Adds the `:first-child` pseudo-class. */
  def firstChild: CssSelector = CssSelector.PseudoClass(selector, "first-child")

  /** Adds the `::first-letter` pseudo-element. */
  def firstLetter: CssSelector = CssSelector.PseudoElement(selector, "first-letter")

  /** Adds the `::first-line` pseudo-element. */
  def firstLine: CssSelector = CssSelector.PseudoElement(selector, "first-line")

  /** Adds the `:focus` pseudo-class. */
  def focus: CssSelector = CssSelector.PseudoClass(selector, "focus")

  /** Named form of `~`: the general sibling combinator. */
  def generalSibling(sel: CssSelectable): CssSelector = selector ~ sel

  /** Adds the `:host` pseudo-class. */
  def host: CssSelector = CssSelector.PseudoClass(selector, "host")

  /**
   * Adds the `:host(...)` pseudo-class with the given selector inside the
   * parentheses.
   */
  def host(sel: CssSelectable): CssSelector = CssSelector.PseudoClass(selector, s"host(${sel.selector})")

  /**
   * Adds the `:host-context(...)` pseudo-class with the given selector inside
   * the parentheses.
   */
  def hostContext(sel: CssSelectable): CssSelector = CssSelector.PseudoClass(selector, s"host-context(${sel.selector})")

  /** Adds the `:hover` pseudo-class. */
  def hover: CssSelector = CssSelector.PseudoClass(selector, "hover")

  /** Adds the `:last-child` pseudo-class. */
  def lastChild: CssSelector = CssSelector.PseudoClass(selector, "last-child")

  /**
   * Adds the `:not(...)` pseudo-class with the given selector inside the
   * parentheses.
   */
  def not(sel: CssSelectable): CssSelector = CssSelector.Not(selector, sel.selector)

  /** Adds the `:nth-child(n)` pseudo-class for the given position. */
  def nthChild(n: Int): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($n)")

  /**
   * Adds the `:nth-child(...)` pseudo-class with the formula as written, such
   * as `2n+1`.
   */
  def nthChild(formula: String): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($formula)")

  /** Named form of `|`: groups the two as a selector list. */
  def or(sel: CssSelectable): CssSelector = selector | sel

  /** Adds the `::part(name)` pseudo-element. */
  def part(name: String): CssSelector = CssSelector.PseudoElement(selector, s"part($name)")

  /** Adds the `::slotted(*)` pseudo-element. */
  def slotted: CssSelector = CssSelector.PseudoElement(selector, "slotted(*)")

  /**
   * Adds the `::slotted(...)` pseudo-element with the given selector inside the
   * parentheses.
   */
  def slotted(sel: CssSelectable): CssSelector = CssSelector.PseudoElement(selector, s"slotted(${sel.selector})")

  /** Adds the `:visited` pseudo-class. */
  def visited: CssSelector = CssSelector.PseudoClass(selector, "visited")

  /** Requires the attribute to be present, rendered `[attr]`. */
  def withAttribute(attr: String): CssSelector = CssSelector.Attribute(selector, attr, None)

  /**
   * Requires the attribute to equal the value, rendered `[attr="value"]`, with
   * the value escaped.
   */
  def withAttribute(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Exact(value)))

  /**
   * Requires the attribute to contain the value as a substring, rendered
   * `[attr*="value"]`.
   */
  def withAttributeContaining(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Contains(value)))

  /**
   * Requires the attribute to end with the value, rendered `[attr\$="value"]`.
   */
  def withAttributeEnding(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.EndsWith(value)))

  /**
   * Requires the attribute to equal the value or start with the value followed
   * by a hyphen, rendered `[attr|="value"]`.
   */
  def withAttributePrefix(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.HyphenPrefix(value)))

  /**
   * Requires the attribute to start with the value, rendered `[attr^="value"]`.
   */
  def withAttributeStarting(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.StartsWith(value)))

  /**
   * Requires the attribute to hold the value as one of its whitespace-separated
   * words, rendered `[attr~="value"]`.
   */
  def withAttributeWord(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.WhitespaceContains(value)))
}
