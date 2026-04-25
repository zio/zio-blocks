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
    renderTo(sb)
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
      renderIndented(sb, level = 0, indent = indent)
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
    renderMinifiedTo(sb)
    sb.toString
  }

  private[html] def renderTo(sb: java.lang.StringBuilder): Unit
  private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit
  private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit

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
   * Selects all descendant nodes matching the given CSS selector.
   *
   * @param selector
   *   the CSS selector to match against
   * @return
   *   a [[DomSelection]] containing all matching descendant nodes
   */
  def select(selector: CssSelector): DomSelection = DomSelection.select(this, selector)

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
    case _: Dom.Doctype => false
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
   * Elements support modifier chaining via `apply(effect, ...)` and
   * `when(condition)(...)` for fluent construction.
   *
   * Import `DomModifierConversions._` (or use the `zio.blocks.html` package
   * object) to enable implicit conversions from `String`, `Dom`,
   * `Dom.Attribute`, etc. to [[DomModifier]].
   */
  sealed trait Element extends Dom with CssSelectable {
    def tag: String
    def attributes: Chunk[Attribute]
    def children: Chunk[Dom]
    val selector: CssSelector         = CssSelector.Element(tag)
    private[html] def isVoid: Boolean = Dom.voidElements.contains(tag)
    def withAttributes(attrs: Chunk[Attribute]): Element
    def withChildren(kids: Chunk[Dom]): Element

    def apply(effect: DomModifier, effects: DomModifier*): Element =
      ToModifier.buildFromEffects(this, effect, effects)

    def when(condition: Boolean)(effect: DomModifier, effects: DomModifier*): Element =
      if (condition) ToModifier.buildFromEffects(this, effect, effects)
      else this

    def whenSome[T](option: Option[T])(f: T => Seq[DomModifier]): Element =
      option match {
        case Some(value) => ToModifier.buildFromEffects(this, f(value))
        case None        => this
      }

    private[html] def escapeText: Boolean

    private[html] def renderTo(sb: java.lang.StringBuilder): Unit = {
      sb.append('<')
      sb.append(tag)
      renderAttributes(resolveOrPassthrough(attributes), sb)
      if (isVoid) {
        sb.append("/>")
      } else {
        sb.append('>')
        val escape = escapeText
        var i      = 0
        while (i < children.length) {
          children(i) match {
            case Text(content) if !escape => sb.append(content.replace("</", "<\\/"))
            case child                    => child.renderTo(sb)
          }
          i += 1
        }
        sb.append("</")
        sb.append(tag)
        sb.append('>')
      }
    }

    private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit =
      renderTo(sb)

    private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit = {
      sb.append('<')
      sb.append(tag)
      renderAttributes(resolveOrPassthrough(attributes), sb)
      if (isVoid) {
        sb.append("/>")
      } else if (children.isEmpty) {
        sb.append("></")
        sb.append(tag)
        sb.append('>')
      } else if (children.length == 1 && children(0).isInstanceOf[Text]) {
        sb.append('>')
        val escape = escapeText
        children(0) match {
          case Text(content) if !escape => sb.append(content.replace("</", "<\\/"))
          case Text(content)            => Escape.htmlTo(content, sb)
          case _                        => children(0).renderIndented(sb, level, indent)
        }
        sb.append("</")
        sb.append(tag)
        sb.append('>')
      } else {
        val escape = escapeText
        sb.append('>')
        var i = 0
        while (i < children.length) {
          sb.append('\n')
          appendIndent(sb, level + 1, indent)
          children(i) match {
            case Text(content) if !escape => sb.append(content.replace("</", "<\\/"))
            case child                    => child.renderIndented(sb, level + 1, indent)
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
      private[html] def escapeText: Boolean                = true
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
      private[html] def escapeText: Boolean               = false
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
      private[html] def escapeText: Boolean              = false
      def withAttributes(attrs: Chunk[Attribute]): Style = copy(attributes = attrs)
      def withChildren(kids: Chunk[Dom]): Style          = copy(children = kids)

      def inlineCss(code: String): Style =
        copy(children = children :+ Dom.Text(code))

      def inlineCss(code: Css): Style =
        copy(children = children :+ Dom.Text(code.render))
    }
  }

  /**
   * A document type declaration node.
   *
   * Renders as `<!DOCTYPE value>`, typically `<!DOCTYPE html>` for HTML5
   * documents. Doctype nodes are always rendered without indentation and are
   * treated as leaf nodes by tree operations.
   *
   * @param value
   *   the doctype value (e.g., "html")
   */
  final case class Doctype(value: String) extends Dom {
    private[html] def renderTo(sb: java.lang.StringBuilder): Unit = {
      sb.append("<!DOCTYPE ")
      sb.append(value)
      sb.append('>')
    }
    private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit =
      renderTo(sb)
    private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit =
      renderTo(sb)
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
  final case class Text(content: String) extends Dom {
    private[html] def renderTo(sb: java.lang.StringBuilder): Unit                                = Escape.htmlTo(content, sb)
    private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit                        = Escape.htmlTo(content, sb)
    private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit =
      Escape.htmlTo(content, sb)
  }

  /**
   * An empty DOM node that renders to nothing.
   *
   * Used as a neutral element for filtering and tree operations.
   */
  case object Empty extends Dom {
    private[html] def renderTo(sb: java.lang.StringBuilder): Unit                                = ()
    private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit                        = ()
    private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit = ()
  }

  sealed trait Attribute extends Product with Serializable

  object Attribute {
    final case class KeyValue(name: String, value: AttributeValue)                                   extends Attribute
    final case class AppendValue(name: String, value: AttributeValue, separator: AttributeSeparator) extends Attribute
    final case class BooleanAttribute(name: String, enabled: Boolean = true)                         extends Attribute {
      def :=(value: Boolean): BooleanAttribute = copy(enabled = value)
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

  def multiAttr(name: String): MultiAttributeKey =
    new MultiAttributeKey(name, AttributeSeparator.Space)

  def multiAttr(name: String, separator: AttributeSeparator): MultiAttributeKey =
    new MultiAttributeKey(name, separator)

  def multiAttr(name: String, values: Iterable[String]): Attribute =
    Attribute.KeyValue(name, AttributeValue.MultiValue(Chunk.from(values), AttributeSeparator.Space))

  def multiAttr(name: String, separator: AttributeSeparator, value: String, rest: String*): Attribute =
    Attribute.KeyValue(name, AttributeValue.MultiValue(Chunk.from(value +: rest), separator))

  // --- Rendering ---

  private[html] def isValidAttrName(name: String): Boolean =
    name.nonEmpty && name.forall(c => c != '"' && c != '\'' && c != '=' && c != '>' && c != ' ' && c != '/' && c != '<')

  private[html] def isValidTagName(tag: String): Boolean =
    tag.nonEmpty && {
      val first = tag.charAt(0)
      (first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z')
    } && tag.forall(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-')

  private def resolveOrPassthrough(attrs: Chunk[Attribute]): Chunk[Attribute] =
    if (attrs.length <= 1 && (attrs.isEmpty || !attrs(0).isInstanceOf[Attribute.AppendValue])) attrs
    else resolveAttributes(attrs)

  private def renderAttributes(attrs: Chunk[Attribute], sb: java.lang.StringBuilder): Unit = {
    var i = 0
    while (i < attrs.length) {
      attrs(i) match {
        case Attribute.KeyValue(name, value) =>
          renderAttributeValue(name, value, sb)

        case Attribute.AppendValue(_, _, _) =>
          throw new IllegalStateException("AppendValue should be resolved before rendering")

        case Attribute.BooleanAttribute(name, enabled) if enabled =>
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
        case Attribute.KeyValue(name, _) => lastKV.put(name, m)
        case _                           => ()
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
        val sanitized = if (isUrlAttribute(name)) Escape.sanitizeUrl(v) else v
        Escape.htmlTo(sanitized, sb)
        sb.append('"')

      case AttributeValue.MultiValue(values, separator) =>
        if (values.nonEmpty) {
          sb.append(' ')
          sb.append(name)
          sb.append("=\"")
          var j = 0
          while (j < values.length) {
            if (j > 0) sb.append(separator.render)
            Escape.htmlTo(values(j), sb)
            j += 1
          }
          sb.append('"')
        }

      case AttributeValue.JsValue(js) =>
        sb.append(' ')
        sb.append(name)
        sb.append("=\"")
        Escape.htmlTo(js.value, sb)
        sb.append('"')
    }

  // --- Indented rendering: cached indent strings ---

  private val indentStrings: Array[String] = {
    val arr = new Array[String](128)
    var i   = 0
    while (i < 128) {
      arr(i) = " " * i
      i += 1
    }
    arr
  }

  private def appendIndent(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit = {
    val n = level * indent
    if (n > 0) {
      if (n < indentStrings.length) sb.append(indentStrings(n))
      else {
        var i = 0
        while (i < n) {
          sb.append(' ')
          i += 1
        }
      }
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

  private val urlAttributes: Set[String] = Set("href", "src", "action", "formaction")

  private def isUrlAttribute(name: String): Boolean = urlAttributes.contains(name)

  private[html] val voidElements: Set[String] = Set(
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
