package zio.blocks.template

sealed trait Dom extends Product with Serializable {
  def render: String = {
    val sb = new java.lang.StringBuilder(256)
    Dom.renderTo(this, sb, minified = false)
    sb.toString
  }

  def renderMinified: String = {
    val sb = new java.lang.StringBuilder(256)
    Dom.renderTo(this, sb, minified = true)
    sb.toString
  }
}

object Dom {

  final case class Element(
    tag: String,
    attributes: Vector[Attribute],
    children: Vector[Dom]
  ) extends Dom

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
    final case class StringValue(value: String)                          extends AttributeValue
    final case class MultiValue(values: Vector[String], separator: Char) extends AttributeValue
    final case class JsValue(js: Js)                                     extends AttributeValue
    final case class BooleanValue(value: Boolean)                        extends AttributeValue
  }

  def element(tag: String, attributes: Vector[Attribute], children: Vector[Dom]): Element =
    Element(tag, attributes, children)

  def text(content: String): Text = Text(content)

  def raw(html: String): RawHtml = RawHtml(html)

  def fragment(children: Vector[Dom]): Dom =
    if (children.isEmpty) Empty
    else if (children.length == 1) children(0)
    else Fragment(children)

  val empty: Dom = Empty

  private def renderTo(dom: Dom, sb: java.lang.StringBuilder, minified: Boolean): Unit =
    dom match {
      case Element(tag, attributes, children) =>
        sb.append('<')
        sb.append(tag)
        renderAttributes(attributes, sb)
        sb.append('>')
        if (!isVoidElement(tag)) {
          var i = 0
          while (i < children.length) {
            renderTo(children(i), sb, minified)
            i += 1
          }
          sb.append("</")
          sb.append(tag)
          sb.append('>')
        }

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
            if (j > 0) sb.append(separator)
            sb.append(Escape.html(values(j)))
            j += 1
          }
          sb.append('"')
        }

      case AttributeValue.JsValue(js) =>
        sb.append(' ')
        sb.append(name)
        sb.append("=\"")
        sb.append(js.value)
        sb.append('"')
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
