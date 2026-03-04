package zio.blocks.template

sealed trait Dom extends Product with Serializable {
  def render: String = {
    val sb = new java.lang.StringBuilder(256)
    Dom.renderTo(this, sb, minified = false)
    sb.toString
  }

  def render(indentation: Boolean): String =
    if (!indentation) render
    else {
      val sb = new java.lang.StringBuilder(256)
      Dom.renderIndented(this, sb, level = 0)
      sb.toString
    }

  def renderMinified: String = {
    val sb = new java.lang.StringBuilder(256)
    Dom.renderTo(this, sb, minified = true)
    sb.toString
  }

  def collect(pf: PartialFunction[Dom, Dom]): List[Dom] = {
    val buf = List.newBuilder[Dom]
    Dom.collectImpl(this, pf, buf)
    buf.result()
  }

  def filter(predicate: Dom => Boolean): Dom =
    Dom.filterImpl(this, predicate)

  def find(predicate: Dom => Boolean): Option[Dom] =
    Dom.findImpl(this, predicate)

  def transform(f: Dom => Dom): Dom =
    Dom.transformImpl(this, f)

  def isEmpty: Boolean = this match {
    case Dom.Empty              => true
    case Dom.Text(c)            => c.isEmpty
    case Dom.RawHtml(h)         => h.isEmpty
    case Dom.Fragment(children) => children.forall(_.isEmpty)
    case _: Dom.Element         => false
  }
}

object Dom {

  sealed trait Element extends Dom {
    def tag: String
    def attributes: Vector[Attribute]
    def children: Vector[Dom]
    def withAttributes(attrs: Vector[Attribute]): Element
    def withChildren(kids: Vector[Dom]): Element

    def apply(modifiers: Modifier*): Element = {
      var elem: Element = this
      var i             = 0
      while (i < modifiers.length) {
        elem = modifiers(i).applyTo(elem)
        i += 1
      }
      elem
    }

    def when(condition: Boolean)(modifiers: Modifier*): Element =
      if (condition) apply(modifiers: _*) else this

    def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Element =
      option match {
        case Some(value) => apply(f(value): _*)
        case None        => this
      }
  }

  object Element {

    final case class Generic(
      tag: String,
      attributes: Vector[Attribute],
      children: Vector[Dom]
    ) extends Element {
      def withAttributes(attrs: Vector[Attribute]): Generic = copy(attributes = attrs)
      def withChildren(kids: Vector[Dom]): Generic          = copy(children = kids)
    }

    final case class Script(
      attributes: Vector[Attribute],
      children: Vector[Dom]
    ) extends Element {
      def tag: String                                      = "script"
      def withAttributes(attrs: Vector[Attribute]): Script = copy(attributes = attrs)
      def withChildren(kids: Vector[Dom]): Script          = copy(children = kids)

      def inlineJs(code: String): Script =
        copy(children = children :+ Dom.Text(code))

      def inlineJs(code: Js): Script =
        copy(children = children :+ Dom.Text(code.value))

      def externalJs(url: String): Script =
        copy(attributes = attributes :+ Attribute.KeyValue("src", AttributeValue.StringValue(url)))
    }

    final case class Style(
      attributes: Vector[Attribute],
      children: Vector[Dom]
    ) extends Element {
      def tag: String                                     = "style"
      def withAttributes(attrs: Vector[Attribute]): Style = copy(attributes = attrs)
      def withChildren(kids: Vector[Dom]): Style          = copy(children = kids)

      def inlineCss(code: String): Style =
        copy(children = children :+ Dom.Text(code))

      def inlineCss(code: Css): Style =
        copy(children = children :+ Dom.Text(code.value))
    }
  }

  final case class Text(content: String) extends Dom

  final case class RawHtml(html: String) extends Dom

  final case class Fragment(children: Vector[Dom]) extends Dom

  case object Empty extends Dom

  sealed trait Attribute extends Product with Serializable

  object Attribute {
    final case class KeyValue(name: String, value: AttributeValue) extends Attribute
    final case class BooleanAttribute(name: String)                extends Attribute
  }

  sealed trait AttributeValue extends Product with Serializable

  object AttributeValue {
    final case class StringValue(value: String)                                        extends AttributeValue
    final case class MultiValue(values: Vector[String], separator: AttributeSeparator) extends AttributeValue
    final case class JsValue(js: Js)                                                   extends AttributeValue
    final case class BooleanValue(value: Boolean)                                      extends AttributeValue
  }

  sealed trait AttributeSeparator extends Product with Serializable {
    def render: String
  }

  object AttributeSeparator {
    case object Space                          extends AttributeSeparator { def render: String = " " }
    case object Comma                          extends AttributeSeparator { def render: String = "," }
    case object Semicolon                      extends AttributeSeparator { def render: String = ";" }
    final case class Custom(separator: String) extends AttributeSeparator {
      def render: String = separator
    }
  }

  def element(tag: String, attributes: Vector[Attribute], children: Vector[Dom]): Element =
    Element.Generic(tag, attributes, children)

  def text(content: String): Text = Text(content)

  def raw(html: String): RawHtml = RawHtml(html)

  def fragment(children: Vector[Dom]): Dom =
    if (children.isEmpty) Empty
    else if (children.length == 1) children(0)
    else Fragment(children)

  val empty: Dom = Empty

  def boolAttr(name: String, enabled: Boolean = true): Attribute =
    Attribute.KeyValue(name, AttributeValue.BooleanValue(enabled))

  def multiAttr(name: String): PartialMultiAttribute =
    new PartialMultiAttribute(name, AttributeSeparator.Space)

  def multiAttr(name: String, separator: AttributeSeparator): PartialMultiAttribute =
    new PartialMultiAttribute(name, separator)

  def multiAttr(name: String, values: Iterable[String]): Attribute =
    Attribute.KeyValue(name, AttributeValue.MultiValue(values.toVector, AttributeSeparator.Space))

  def multiAttr(name: String, separator: AttributeSeparator, values: String*): Attribute =
    Attribute.KeyValue(name, AttributeValue.MultiValue(values.toVector, separator))

  // --- Rendering ---

  private def renderTo(dom: Dom, sb: java.lang.StringBuilder, minified: Boolean): Unit =
    dom match {
      case el: Element.Generic =>
        renderElement(el.tag, el.attributes, el.children, sb, minified, escapeText = true)

      case el: Element.Script =>
        renderElement(el.tag, el.attributes, el.children, sb, minified, escapeText = false)

      case el: Element.Style =>
        renderElement(el.tag, el.attributes, el.children, sb, minified, escapeText = false)

      case Text(content) =>
        sb.append(Escape.html(content))

      case RawHtml(html) =>
        sb.append(html)

      case Fragment(children) =>
        var i = 0
        while (i < children.length) {
          renderTo(children(i), sb, minified)
          i += 1
        }

      case Empty =>
        ()
    }

  private def renderElement(
    tag: String,
    attributes: Vector[Attribute],
    children: Vector[Dom],
    sb: java.lang.StringBuilder,
    minified: Boolean,
    escapeText: Boolean
  ): Unit = {
    sb.append('<')
    sb.append(tag)
    renderAttributes(attributes, sb)
    if (isVoidElement(tag)) {
      sb.append("/>")
    } else {
      sb.append('>')
      var i = 0
      while (i < children.length) {
        children(i) match {
          case Text(content) if !escapeText => sb.append(content)
          case child                        => renderTo(child, sb, minified)
        }
        i += 1
      }
      sb.append("</")
      sb.append(tag)
      sb.append('>')
    }
  }

  private def renderAttributes(attrs: Vector[Attribute], sb: java.lang.StringBuilder): Unit = {
    var i = 0
    while (i < attrs.length) {
      attrs(i) match {
        case Attribute.KeyValue(name, value) =>
          renderAttributeValue(name, value, sb)

        case Attribute.BooleanAttribute(name) =>
          sb.append(' ')
          sb.append(name)
      }
      i += 1
    }
  }

  private def renderAttributeValue(
    name: String,
    value: AttributeValue,
    sb: java.lang.StringBuilder
  ): Unit =
    value match {
      case AttributeValue.BooleanValue(v) =>
        if (v) {
          sb.append(' ')
          sb.append(name)
        }

      case AttributeValue.StringValue(v) =>
        sb.append(' ')
        sb.append(name)
        sb.append("=\"")
        sb.append(Escape.html(v))
        sb.append('"')

      case AttributeValue.MultiValue(values, separator) =>
        if (values.nonEmpty) {
          sb.append(' ')
          sb.append(name)
          sb.append("=\"")
          var j = 0
          while (j < values.length) {
            if (j > 0) sb.append(separator.render)
            sb.append(Escape.html(values(j)))
            j += 1
          }
          sb.append('"')
        }

      case AttributeValue.JsValue(js) =>
        sb.append(' ')
        sb.append(name)
        sb.append("=\"")
        sb.append(Escape.html(js.value))
        sb.append('"')
    }
  // --- Indented rendering ---

  private val Indent = "  "

  private def renderIndented(dom: Dom, sb: java.lang.StringBuilder, level: Int): Unit =
    dom match {
      case el: Element.Generic =>
        renderElementIndented(el.tag, el.attributes, el.children, sb, level, escapeText = true)

      case el: Element.Script =>
        renderElementIndented(el.tag, el.attributes, el.children, sb, level, escapeText = false)

      case el: Element.Style =>
        renderElementIndented(el.tag, el.attributes, el.children, sb, level, escapeText = false)

      case Text(content) =>
        sb.append(Escape.html(content))

      case RawHtml(html) =>
        sb.append(html)

      case Fragment(children) =>
        var i = 0
        while (i < children.length) {
          if (i > 0) sb.append('\n')
          renderIndented(children(i), sb, level)
          i += 1
        }

      case Empty =>
        ()
    }

  private def renderElementIndented(
    tag: String,
    attributes: Vector[Attribute],
    children: Vector[Dom],
    sb: java.lang.StringBuilder,
    level: Int,
    escapeText: Boolean
  ): Unit = {
    sb.append('<')
    sb.append(tag)
    renderAttributes(attributes, sb)
    if (isVoidElement(tag)) {
      sb.append("/>")
    } else if (children.isEmpty) {
      sb.append("></")
      sb.append(tag)
      sb.append('>')
    } else if (children.length == 1 && isSingleLineContent(children(0))) {
      sb.append('>')
      children(0) match {
        case Text(content) if !escapeText => sb.append(content)
        case Text(content)                => sb.append(Escape.html(content))
        case RawHtml(html)                => sb.append(html)
        case _                            => renderIndented(children(0), sb, level)
      }
      sb.append("</")
      sb.append(tag)
      sb.append('>')
    } else {
      sb.append('>')
      var i = 0
      while (i < children.length) {
        sb.append('\n')
        appendIndent(sb, level + 1)
        children(i) match {
          case Text(content) if !escapeText => sb.append(content)
          case child                        => renderIndented(child, sb, level + 1)
        }
        i += 1
      }
      sb.append('\n')
      appendIndent(sb, level)
      sb.append("</")
      sb.append(tag)
      sb.append('>')
    }
  }

  private def isSingleLineContent(dom: Dom): Boolean = dom match {
    case _: Text    => true
    case _: RawHtml => true
    case _          => false
  }

  private def appendIndent(sb: java.lang.StringBuilder, level: Int): Unit = {
    var i = 0
    while (i < level) {
      sb.append(Indent)
      i += 1
    }
  }

  // --- Traversal ---

  private def collectImpl(
    dom: Dom,
    pf: PartialFunction[Dom, Dom],
    buf: scala.collection.mutable.Builder[Dom, List[Dom]]
  ): Unit = {
    if (pf.isDefinedAt(dom)) buf += pf(dom)
    dom match {
      case el: Element =>
        var i = 0
        while (i < el.children.length) {
          collectImpl(el.children(i), pf, buf)
          i += 1
        }
      case Fragment(children) =>
        var i = 0
        while (i < children.length) {
          collectImpl(children(i), pf, buf)
          i += 1
        }
      case _ => ()
    }
  }

  private def filterImpl(dom: Dom, predicate: Dom => Boolean): Dom =
    if (!predicate(dom)) Empty
    else
      dom match {
        case el: Element =>
          val filtered = el.children.collect {
            case c if predicate(c) => filterImpl(c, predicate)
          }
          el.withChildren(filtered)
        case Fragment(children) =>
          val filtered = children.collect {
            case c if predicate(c) => filterImpl(c, predicate)
          }
          fragment(filtered)
        case other => other
      }

  private def findImpl(dom: Dom, predicate: Dom => Boolean): Option[Dom] =
    if (predicate(dom)) Some(dom)
    else
      dom match {
        case el: Element =>
          var i                   = 0
          var result: Option[Dom] = None
          while (i < el.children.length && result.isEmpty) {
            result = findImpl(el.children(i), predicate)
            i += 1
          }
          result
        case Fragment(children) =>
          var i                   = 0
          var result: Option[Dom] = None
          while (i < children.length && result.isEmpty) {
            result = findImpl(children(i), predicate)
            i += 1
          }
          result
        case _ => None
      }

  private def transformImpl(dom: Dom, f: Dom => Dom): Dom = {
    val transformed = f(dom)
    transformed match {
      case el: Element =>
        val newChildren = el.children.map(c => transformImpl(c, f))
        el.withChildren(newChildren)
      case Fragment(children) =>
        val newChildren = children.map(c => transformImpl(c, f))
        fragment(newChildren)
      case other => other
    }
  }

  private val voidElements: Set[String] = Set(
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr"
  )

  private def isVoidElement(tag: String): Boolean = voidElements.contains(tag)
}
