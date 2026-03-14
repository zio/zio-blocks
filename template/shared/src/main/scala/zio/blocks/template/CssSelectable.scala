package zio.blocks.template

trait CssSelectable {
  val selector: CssSelector

  def >(child: CssSelectable): CssSelector       = CssSelector.Child(selector, child.selector)
  def >>(descendant: CssSelectable): CssSelector = CssSelector.Descendant(selector, descendant.selector)
  def +(adjacent: CssSelectable): CssSelector    = CssSelector.AdjacentSibling(selector, adjacent.selector)
  def &(other: CssSelectable): CssSelector       = CssSelector.And(selector, other.selector)
  def ~(sibling: CssSelectable): CssSelector     = CssSelector.GeneralSibling(selector, sibling.selector)
  def |(other: CssSelectable): CssSelector       = CssSelector.Or(selector, other.selector)

  def active: CssSelector = CssSelector.PseudoClass(selector, "active")

  def adjacentSibling(sel: CssSelectable): CssSelector = selector + sel

  def after: CssSelector = CssSelector.PseudoElement(selector, "after")

  def and(sel: CssSelectable): CssSelector = selector & sel

  def before: CssSelector = CssSelector.PseudoElement(selector, "before")

  def child(sel: CssSelectable): CssSelector = selector > sel

  def descendant(sel: CssSelectable): CssSelector = selector >> sel

  def firstChild: CssSelector = CssSelector.PseudoClass(selector, "first-child")

  def firstLetter: CssSelector = CssSelector.PseudoElement(selector, "first-letter")

  def firstLine: CssSelector = CssSelector.PseudoElement(selector, "first-line")

  def focus: CssSelector = CssSelector.PseudoClass(selector, "focus")

  def generalSibling(sel: CssSelectable): CssSelector = selector ~ sel

  def host: CssSelector = CssSelector.PseudoClass(selector, "host")

  def host(sel: CssSelectable): CssSelector = CssSelector.PseudoClass(selector, s"host(${sel.selector})")

  def hostContext(sel: CssSelectable): CssSelector = CssSelector.PseudoClass(selector, s"host-context(${sel.selector})")

  def hover: CssSelector = CssSelector.PseudoClass(selector, "hover")

  def lastChild: CssSelector = CssSelector.PseudoClass(selector, "last-child")

  def not(sel: CssSelectable): CssSelector = CssSelector.Not(selector, sel.selector)

  def nthChild(n: Int): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($n)")

  def nthChild(formula: String): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($formula)")

  def or(sel: CssSelectable): CssSelector = selector | sel

  def part(name: String): CssSelector = CssSelector.PseudoElement(selector, s"part($name)")

  def slotted: CssSelector = CssSelector.PseudoElement(selector, "slotted(*)")

  def slotted(sel: CssSelectable): CssSelector = CssSelector.PseudoElement(selector, s"slotted(${sel.selector})")

  def visited: CssSelector = CssSelector.PseudoClass(selector, "visited")

  def withAttribute(attr: String): CssSelector = CssSelector.Attribute(selector, attr, None)

  def withAttribute(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Exact(value)))

  def withAttributeContaining(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Contains(value)))

  def withAttributeEnding(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.EndsWith(value)))

  def withAttributePrefix(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.HyphenPrefix(value)))

  def withAttributeStarting(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.StartsWith(value)))

  def withAttributeWord(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.WhitespaceContains(value)))
}
