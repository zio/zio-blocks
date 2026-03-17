package zio.blocks.template

/**
 * A sealed ADT representing CSS selectors with type-safe composition.
 *
 * Selectors can be combined using operators:
 *   - `>` — child combinator
 *   - `>>` — descendant combinator
 *   - `+` — adjacent sibling
 *   - `~` — general sibling
 *   - `&` — concatenation (and)
 *   - `|` — grouping (or)
 *
 * Values in attribute selectors are escaped to prevent CSS injection. Use
 * `CssSelector.Raw(string)` for selectors not expressible via the ADT.
 */

sealed trait CssSelector extends CssSelectable with Product with Serializable {

  val selector: CssSelector = this

  def render: String

  override def toString: String = render

}

object CssSelector {
  def raw(value: String): Raw = Raw(value)

  def `class`(name: String): Class = Class(name)

  def element(tag: String): Element = Element(tag)

  def id(name: String): Id = Id(name)

  def universal: Universal.type = Universal

  sealed trait AttributeMatch extends Product with Serializable

  object AttributeMatch {
    final case class Contains(value: String)           extends AttributeMatch
    final case class EndsWith(value: String)           extends AttributeMatch
    final case class Exact(value: String)              extends AttributeMatch
    final case class HyphenPrefix(value: String)       extends AttributeMatch
    final case class StartsWith(value: String)         extends AttributeMatch
    final case class WhitespaceContains(value: String) extends AttributeMatch
  }

  final case class AdjacentSibling(previous: CssSelector, next: CssSelector) extends CssSelector {
    def render: String = s"$previous + $next"
  }

  final case class And(left: CssSelector, right: CssSelector) extends CssSelector {
    def render: String = s"$left$right"
  }

  final case class Attribute(inner: CssSelector, attribute: String, matcher: Option[AttributeMatch])
      extends CssSelector {
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

  final case class Child(parent: CssSelector, child: CssSelector) extends CssSelector {
    def render: String = s"$parent > $child"
  }

  final case class Class(name: String) extends CssSelector {
    def render: String = s".$name"
  }

  final case class Descendant(parent: CssSelector, descendant: CssSelector) extends CssSelector {
    def render: String = s"$parent $descendant"
  }

  final case class Element(tag: String) extends CssSelector {
    def render: String = tag
  }

  final case class GeneralSibling(previous: CssSelector, sibling: CssSelector) extends CssSelector {
    def render: String = s"$previous ~ $sibling"
  }

  final case class Id(name: String) extends CssSelector {
    def render: String = s"#$name"
  }

  final case class Not(inner: CssSelector, negated: CssSelector) extends CssSelector {
    def render: String = s"$inner:not($negated)"
  }

  final case class Or(left: CssSelector, right: CssSelector) extends CssSelector {
    def render: String = s"$left, $right"
  }

  final case class PseudoClass(inner: CssSelector, pseudoClass: String) extends CssSelector {
    def render: String = s"$inner:$pseudoClass"
  }

  final case class PseudoElement(inner: CssSelector, pseudoElement: String) extends CssSelector {
    def render: String = s"$inner::$pseudoElement"
  }

  final case class Raw(value: String) extends CssSelector {
    def render: String = value
  }

  case object Universal extends CssSelector {
    def render: String = "*"
  }

  private def escapeCssSelectorValue(v: String): String =
    v.replace("\\", "\\\\").replace("\"", "\\\"")

}
