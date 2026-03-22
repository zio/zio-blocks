package zio.blocks.template

import zio.blocks.chunk.Chunk

/**
 * A sealed algebraic data type (ADT) representing an HTML document node.
 *
 * The Dom trait models HTML as an abstract syntax tree where each node is
 * either:
 *   - Text content (automatically HTML-escaped during rendering)
 *   - An empty node (renders to nothing)
 *   - An Element with a tag, attributes, and child nodes
 *
 * ===Rendering===
 * Text content is HTML-escaped by default to prevent injection attacks.
 * However, Script and Style elements intentionally render their children
 * WITHOUT escaping, allowing inline JavaScript and CSS to be rendered as-is.
 *
 * Use `render` for minified HTML or `render(indent: Int)` for pretty-printed
 * output with N spaces per indentation level.
 *
 * ===Tree Operations===
 * The ADT supports traversal via `collect`, `filter`, `find`, and `transform`
 * methods for navigating and transforming the tree structure.
 *
 * @see
 *   [[Dom.Element]] for element construction
 * @see
 *   [[Dom.Text]] for text content
 * @see
 *   [[Dom.Empty]] for empty nodes
 */
sealed trait Dom extends Product with Serializable {

  /**
   * Renders the DOM tree to a minified HTML string.
   *
   * Text content is HTML-escaped. Script and Style elements render their
   * children without escaping to allow inline code.
   *
   * @return
   *   minified HTML output with no extra whitespace
   */
  def render: String = {
    val sb = new java.lang.StringBuilder(256)
    Dom.renderTo(this, sb, minified = false)
    sb.toString
  }

  /**
   * Renders the DOM tree to a pretty-printed HTML string with indentation.
   *
   * Text content is HTML-escaped. Script and Style elements render their
   * children without escaping to allow inline code.
   *
   * @param indent
   *   the number of spaces per indentation level; if <= 0, behaves like
   *   `render`
   * @return
   *   formatted HTML with newlines and indentation
   */
  def render(indent: Int): String =
    if (indent <= 0) render
    else {
      val sb = new java.lang.StringBuilder(256)
      Dom.renderIndented(this, sb, level = 0, indent = indent)
      sb.toString
    }

  /**
   * Renders the DOM tree to a minified HTML string (alias for `render`).
   *
   * @return
   *   minified HTML output
   */
  def renderMinified: String = {
    val sb = new java.lang.StringBuilder(256)
    Dom.renderTo(this, sb, minified = true)
    sb.toString
  }

  /**
   * Collects all nodes in the tree matching the given partial function.
   *
   * Traverses the entire tree in depth-first order, applying the partial
   * function to each node and collecting matches.
   *
   * @param pf
   *   the partial function to apply; nodes that match contribute their
   *   transformed values
   * @return
   *   a list of matching nodes (or their transformed versions)
   */
  def collect(pf: PartialFunction[Dom, Dom]): List[Dom] = {
    val buf = List.newBuilder[Dom]
    Dom.collectImpl(this, pf, buf)
    buf.result()
  }

  /**
   * Filters the tree to keep only nodes satisfying the predicate.
   *
   * Removes any node for which the predicate returns false, and recursively
   * filters children. Empty branches collapse to `Empty`.
   *
   * @param predicate
   *   the condition each node must satisfy
   * @return
   *   a new tree with non-matching nodes removed
   */
  def filter(predicate: Dom => Boolean): Dom =
    Dom.filterImpl(this, predicate)

  /**
   * Finds the first node in the tree satisfying the predicate.
   *
   * Traverses the tree in depth-first order and returns the first matching
   * node.
   *
   * @param predicate
   *   the condition to search for
   * @return
   *   `Some(node)` if a match is found, `None` otherwise
   */
  def find(predicate: Dom => Boolean): Option[Dom] =
    Dom.findImpl(this, predicate)

  /**
   * Transforms every node in the tree using the given function.
   *
   * Applies the transformation function to each node in depth-first order, then
   * recursively transforms children of Elements. Suitable for rewriting the
   * tree, e.g., removing nodes, modifying attributes, or simplifying structure.
   *
   * @param f
   *   the transformation function to apply to each node
   * @return
   *   a new tree with all nodes transformed
   */
  def transform(f: Dom => Dom): Dom =
    Dom.transformImpl(this, f)

  /**
   * Checks whether this node renders to empty HTML.
   *
   * Returns true if the node is `Empty` or a `Text` with empty content.
   *
   * @return
   *   true if this node produces no output when rendered
   */
  def isEmpty: Boolean = this match {
    case Dom.Empty      => true
    case Dom.Text(c)    => c.isEmpty
    case _: Dom.Element => false
  }
}

object Dom {

  /**
   * A sealed trait for HTML elements with a tag, attributes, and children.
   *
   * Subtypes include:
   *   - [[Dom.Element.Generic]] — standard HTML elements
   *   - [[Dom.Element.Script]] — script tags (children rendered without
   *     escaping)
   *   - [[Dom.Element.Style]] — style tags (children rendered without escaping)
   *
   * Elements support modifier chaining via `apply(modifier, ...)` and
   * `when(condition)(...)` for fluent construction.
   */
  sealed trait Element extends Dom with CssSelectable {
    def tag: String
    def attributes: Chunk[Attribute]
    def children: Chunk[Dom]
    val selector: CssSelector = CssSelector.Element(tag)
    def withAttributes(attrs: Chunk[Attribute]): Element
    def withChildren(kids: Chunk[Dom]): Element

    def apply(modifier: Modifier, modifiers: Modifier*): Element = {
      var elem: Element = modifier.applyTo(this)
      var i             = 0
      while (i < modifiers.length) {
        elem = modifiers(i).applyTo(elem)
        i += 1
      }
      elem
    }

    def when(condition: Boolean)(modifiers: Modifier*): Element =
      if (condition) {
        var elem: Element = this
        var i             = 0
        while (i < modifiers.length) {
          elem = modifiers(i).applyTo(elem)
          i += 1
        }
        elem
      } else this

    def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Element =
      option match {
        case Some(value) =>
          val mods          = f(value)
          var elem: Element = this
          var i             = 0
          while (i < mods.length) {
            elem = mods(i).applyTo(elem)
            i += 1
          }
          elem
        case None => this
      }
  }

  object Element {

    /**
     * A generic HTML element.
     *
     * Represents any standard HTML tag (e.g., "div", "p", "span"). Text content
     * in children is HTML-escaped during rendering.
     *
     * @param tag
     *   the element tag name (e.g., "div", "h1")
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes
     */
    final case class Generic(
      tag: String,
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element {
      def withAttributes(attrs: Chunk[Attribute]): Generic = copy(attributes = attrs)
      def withChildren(kids: Chunk[Dom]): Generic          = copy(children = kids)
    }

    /**
     * An HTML script element.
     *
     * The Script element renders its children WITHOUT HTML-escaping, allowing
     * inline JavaScript to be rendered as-is. Provides convenience methods
     * `inlineJs(code)` to inject escaped JavaScript or `externalJs(url)` to
     * link external scripts.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes (typically Text with JavaScript code)
     */
    final case class Script(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element {
      def tag: String                                     = "script"
      def withAttributes(attrs: Chunk[Attribute]): Script = copy(attributes = attrs)
      def withChildren(kids: Chunk[Dom]): Script          = copy(children = kids)

      def inlineJs(code: String): Script = {
        val escaped = code.replace("</", "<\\/")
        copy(children = children :+ Dom.Text(escaped))
      }

      def inlineJs(code: Js): Script = {
        val escaped = code.value.replace("</", "<\\/")
        copy(children = children :+ Dom.Text(escaped))
      }

      def externalJs(url: String): Script =
        copy(attributes = attributes :+ Attribute.KeyValue("src", AttributeValue.StringValue(url)))
    }

    /**
     * An HTML style element.
     *
     * The Style element renders its children WITHOUT HTML-escaping, allowing
     * inline CSS to be rendered as-is. Provides convenience method
     * `inlineCss(code)` to inject CSS code directly.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes (typically Text with CSS code)
     */
    final case class Style(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element {
      def tag: String                                    = "style"
      def withAttributes(attrs: Chunk[Attribute]): Style = copy(attributes = attrs)
      def withChildren(kids: Chunk[Dom]): Style          = copy(children = kids)

      def inlineCss(code: String): Style =
        copy(children = children :+ Dom.Text(code))

      def inlineCss(code: Css): Style =
        copy(children = children :+ Dom.Text(code.render))
    }
  }

  /**
   * A text node containing HTML content to be escaped during rendering.
   *
   * Text nodes are automatically HTML-escaped to prevent XSS attacks when
   * rendered (except within Script and Style elements, which are unescaped).
   *
   * @param content
   *   the text content (will be HTML-escaped in output)
   */
  final case class Text(content: String) extends Dom

  /**
   * An empty DOM node that renders to nothing.
   *
   * Used as a neutral element for filtering and tree operations.
   */
  case object Empty extends Dom

  sealed trait Attribute extends Product with Serializable

  object Attribute {
    final case class KeyValue(name: String, value: AttributeValue) extends Attribute
    final case class AppendValue(name: String, value: AttributeValue, separator: AttributeSeparator)
        extends Attribute
        with Modifier {
      def applyTo(element: Element): Element =
        element.withAttributes(element.attributes :+ this)
    }
    final case class BooleanAttribute(name: String, enabled: Boolean = true) extends Attribute with Modifier {
      def applyTo(element: Element): Element =
        if (enabled) element.withAttributes(element.attributes :+ this)
        else element
    }
  }

  sealed trait AttributeValue extends Product with Serializable

  object AttributeValue {
    final case class StringValue(value: String)                                       extends AttributeValue
    final case class MultiValue(values: Chunk[String], separator: AttributeSeparator) extends AttributeValue
    final case class JsValue(js: Js)                                                  extends AttributeValue
    final case class BooleanValue(value: Boolean)                                     extends AttributeValue
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

  def text(content: String): Text = Text(content)

  val empty: Dom = Empty

  def boolAttr(name: String, enabled: Boolean = true): Attribute.BooleanAttribute =
    Attribute.BooleanAttribute(name, enabled)

  def multiAttr(name: String): PartialMultiAttribute =
    new PartialMultiAttribute(name, AttributeSeparator.Space)

  def multiAttr(name: String, separator: AttributeSeparator): PartialMultiAttribute =
    new PartialMultiAttribute(name, separator)

  def multiAttr(name: String, values: Iterable[String]): Attribute =
    Attribute.KeyValue(name, AttributeValue.MultiValue(Chunk.from(values), AttributeSeparator.Space))

  def multiAttr(name: String, separator: AttributeSeparator, values: String*): Attribute =
    Attribute.KeyValue(name, AttributeValue.MultiValue(Chunk.from(values), separator))

  // --- Rendering ---

  private def isValidAttrName(name: String): Boolean =
    name.nonEmpty && name.forall(c => c != '"' && c != '\'' && c != '=' && c != '>' && c != ' ' && c != '/' && c != '<')

  private def isValidTagName(tag: String): Boolean =
    tag.nonEmpty && {
      val first = tag.charAt(0)
      (first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z')
    } && tag.forall(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-')

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

      case Empty =>
        ()
    }

  private def renderElement(
    tag: String,
    attributes: Chunk[Attribute],
    children: Chunk[Dom],
    sb: java.lang.StringBuilder,
    minified: Boolean,
    escapeText: Boolean
  ): Unit = {
    if (!isValidTagName(tag)) return
    sb.append('<')
    sb.append(tag)
    renderAttributes(resolveAttributes(attributes), sb)
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

  private def renderAttributes(attrs: Chunk[Attribute], sb: java.lang.StringBuilder): Unit = {
    var i = 0
    while (i < attrs.length) {
      attrs(i) match {
        case Attribute.KeyValue(name, value) if isValidAttrName(name) =>
          renderAttributeValue(name, value, sb)

        case Attribute.KeyValue(_, _) => ()

        case Attribute.AppendValue(_, _, _) =>
          throw new IllegalStateException("AppendValue should be resolved before rendering")

        case Attribute.BooleanAttribute(name, enabled) if enabled && isValidAttrName(name) =>
          sb.append(' ')
          sb.append(name)

        case Attribute.BooleanAttribute(_, _) => ()
      }
      i += 1
    }
  }

  private def resolveAttributes(attrs: Chunk[Attribute]): Chunk[Attribute] = {
    val needsResolve = {
      var hasAppend   = false
      var hasDupNames = false
      val seen        = new java.util.HashSet[String]()
      var c           = 0
      while (c < attrs.length && !hasAppend) {
        attrs(c) match {
          case _: Attribute.AppendValue    => hasAppend = true
          case Attribute.KeyValue(name, _) => if (!seen.add(name)) hasDupNames = true
          case _                           => ()
        }
        c += 1
      }
      hasAppend || hasDupNames
    }
    if (!needsResolve) return attrs

    val keyValues = new java.util.LinkedHashMap[String, AttributeValue]()
    val appends   = new java.util.LinkedHashMap[String, java.util.ArrayList[(AttributeValue, AttributeSeparator)]]()

    var i = 0
    while (i < attrs.length) {
      attrs(i) match {
        case Attribute.KeyValue(name, value) =>
          keyValues.put(name, value)
        case Attribute.AppendValue(name, value, sep) =>
          var list = appends.get(name)
          if (list == null) { list = new java.util.ArrayList(); appends.put(name, list) }
          list.add((value, sep))
        case _: Attribute.BooleanAttribute => ()
      }
      i += 1
    }

    // Pre-compute resolved values for names that have appends
    val resolved = new java.util.HashMap[String, AttributeValue]()
    val nameIter = appends.entrySet().iterator()
    while (nameIter.hasNext) {
      val entry   = nameIter.next()
      val name    = entry.getKey
      val appList = entry.getValue
      val base    = Option(keyValues.get(name))
      val sb      = new java.lang.StringBuilder
      base.foreach(bv => appendBaseValue(bv, sb))
      var k = 0
      while (k < appList.size()) {
        val (av, sep) = appList.get(k)
        if (sb.length() > 0) sb.append(sep.render)
        appendBaseValue(av, sb)
        k += 1
      }
      resolved.put(name, AttributeValue.StringValue(sb.toString))
    }

    // Second pass: emit in original order, dedup same-name KeyValue (last wins),
    // and replace AppendValue names with their resolved value at first occurrence
    val result  = Chunk.newBuilder[Attribute]
    val emitted = new java.util.HashSet[String]()
    // Walk backwards to find which KeyValue is the last for each name
    val lastKV = new java.util.HashMap[String, Int]()
    var m      = 0
    while (m < attrs.length) {
      attrs(m) match {
        case Attribute.KeyValue(name, _)       => lastKV.put(name, m)
        case Attribute.AppendValue(name, _, _) =>
          if (!lastKV.containsKey(name) && !appends.containsKey(name))
            lastKV.put(name, m)
        case _ => ()
      }
      m += 1
    }

    // Forward pass: emit each attr in order
    var n = 0
    while (n < attrs.length) {
      attrs(n) match {
        case Attribute.KeyValue(name, _) if resolved.containsKey(name) =>
          // This name has appends; emit resolved value at last KeyValue position
          if (!emitted.contains(name) && lastKV.get(name) == n) {
            result += Attribute.KeyValue(name, resolved.get(name))
            emitted.add(name)
          }
        case Attribute.KeyValue(name, value) =>
          // No appends for this name; last KeyValue wins
          if (lastKV.get(name) == n) {
            result += Attribute.KeyValue(name, value)
          }
        case Attribute.AppendValue(name, _, _) =>
          // Emit resolved value at first AppendValue if no KeyValue exists for this name
          if (!emitted.contains(name) && !keyValues.containsKey(name)) {
            result += Attribute.KeyValue(name, resolved.get(name))
            emitted.add(name)
          }
        case ba: Attribute.BooleanAttribute =>
          result += ba
      }
      n += 1
    }

    result.result()
  }

  private def appendBaseValue(value: AttributeValue, sb: java.lang.StringBuilder): Unit =
    value match {
      case AttributeValue.StringValue(v)          => sb.append(v)
      case AttributeValue.MultiValue(values, sep) =>
        var i = 0
        while (i < values.length) {
          if (i > 0) sb.append(sep.render)
          sb.append(values(i))
          i += 1
        }
      case other => sb.append(other.toString)
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

  private def renderIndented(dom: Dom, sb: java.lang.StringBuilder, level: Int, indent: Int): Unit =
    dom match {
      case el: Element.Generic =>
        renderElementIndented(el.tag, el.attributes, el.children, sb, level, escapeText = true, indent = indent)

      case el: Element.Script =>
        renderElementIndented(el.tag, el.attributes, el.children, sb, level, escapeText = false, indent = indent)

      case el: Element.Style =>
        renderElementIndented(el.tag, el.attributes, el.children, sb, level, escapeText = false, indent = indent)

      case Text(content) =>
        sb.append(Escape.html(content))

      case Empty =>
        ()
    }

  private def renderElementIndented(
    tag: String,
    attributes: Chunk[Attribute],
    children: Chunk[Dom],
    sb: java.lang.StringBuilder,
    level: Int,
    escapeText: Boolean,
    indent: Int
  ): Unit = {
    if (!isValidTagName(tag)) return
    sb.append('<')
    sb.append(tag)
    renderAttributes(resolveAttributes(attributes), sb)
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
        case _                            => renderIndented(children(0), sb, level, indent)
      }
      sb.append("</")
      sb.append(tag)
      sb.append('>')
    } else {
      sb.append('>')
      var i = 0
      while (i < children.length) {
        sb.append('\n')
        appendIndent(sb, level + 1, indent)
        children(i) match {
          case Text(content) if !escapeText => sb.append(content)
          case child                        => renderIndented(child, sb, level + 1, indent)
        }
        i += 1
      }
      sb.append('\n')
      appendIndent(sb, level, indent)
      sb.append("</")
      sb.append(tag)
      sb.append('>')
    }
  }

  private def isSingleLineContent(dom: Dom): Boolean = dom match {
    case _: Text => true
    case _       => false
  }

  private def appendIndent(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit = {
    var i = 0
    val n = level * indent
    while (i < n) {
      sb.append(' ')
      i += 1
    }
  }
  // --- Traversal ---

  private def collectImpl(
    dom: Dom,
    pf: PartialFunction[Dom, Dom],
    buf: scala.collection.mutable.Builder[Dom, List[Dom]]
  ): Unit = {
    pf.lift(dom).foreach(buf += _)
    dom match {
      case el: Element =>
        var i = 0
        while (i < el.children.length) {
          collectImpl(el.children(i), pf, buf)
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
          val builder = Chunk.newBuilder[Dom]
          var i       = 0
          while (i < el.children.length) {
            val c = el.children(i)
            if (predicate(c)) builder += filterImpl(c, predicate)
            i += 1
          }
          el.withChildren(builder.result())
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
        case _ => None
      }

  private def transformImpl(dom: Dom, f: Dom => Dom): Dom = {
    val transformed = f(dom)
    transformed match {
      case el: Element =>
        val newChildren = el.children.map(c => transformImpl(c, f))
        el.withChildren(newChildren)
      case other => other
    }
  }

  private def isVoidElement(tag: String): Boolean = Dom.voidElements.contains(tag)

  private[template] val voidElements: Set[String] = Set(
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
}
