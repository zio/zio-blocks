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
 * An immutable HTML node. A `Dom` value is a [[Dom.Element]] (a tag with
 * attributes and children), a [[Dom.Element.Void]] (a tag such as `br` or `img`
 * that takes attributes only), a [[Dom.Text]], a [[Dom.Doctype]] or
 * [[Dom.Empty]]. Build trees with the DSL from `import zio.blocks.html._` or
 * with the `html"..."` interpolator, query them with `select`, and turn them
 * into a string with `render`.
 *
 * Rendering escapes text and attribute values. Text inside `script` and `style`
 * elements is the exception: it renders without escaping (only `</` becomes
 * `<\/`), so never put untrusted values there.
 *
 * {{{
 * val card = div(className := "card", h2("Tea & cakes"), p("From ", strong("4.50")))
 * card.render
 * // <div class="card"><h2>Tea &amp; cakes</h2><p>From <strong>4.50</strong></p></div>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#the-dom-adt The Dom ADT]]
 */
sealed trait Dom extends Product with Serializable {

  /**
   * Renders this node and its children as compact HTML, with no whitespace
   * added between tags.
   *
   * Text and attribute values are HTML-escaped (`&`, `<`, `>`, `"` and `'`),
   * void elements close as `<br/>`, and enabled boolean attributes render as
   * the bare name. Text children of `script` and `style` elements are not
   * escaped; only `</` becomes `<\/`, so never put untrusted values there.
   * Repeated attribute names are merged as [[Dom.Attribute]] describes. Some
   * URL attributes, such as `href` and `src`, get an `unsafe:` prefix when the
   * value starts with a `javascript:`, `vbscript:` or `data:text/html` scheme.
   * This is a best-effort check, not a sanitizer, so validate untrusted URLs
   * yourself.
   *
   * {{{
   * div(id := "cart", className := "box", p("2 items & 1 offer")).render
   * // <div id="cart" class="box"><p>2 items &amp; 1 offer</p></div>
   * }}}
   */
  def render: String = {
    val sb = new java.lang.StringBuilder(256)
    renderTo(sb)
    sb.toString
  }

  /**
   * Renders this node as indented HTML, with `indent` spaces per level. Each
   * child of an element goes on its own line, except that an element whose only
   * child is a text node stays on one line. Escaping is the same as for
   * `render`, and an `indent` of zero or less gives the output of `render`. The
   * added whitespace becomes part of the text, `pre` and `textarea` included.
   *
   * {{{
   * div(h1("Menu"), ul(li("Tea"), li("Cake"))).render(2)
   * // <div>
   * //   <h1>Menu</h1>
   * //   <ul>
   * //     <li>Tea</li>
   * //     <li>Cake</li>
   * //   </ul>
   * // </div>
   * }}}
   */
  def render(indent: Int): String =
    if (indent <= 0) render
    else {
      val sb = new java.lang.StringBuilder(256)
      renderIndented(sb, level = 0, indent = indent)
      sb.toString
    }

  /**
   * Renders this node as compact HTML. Returns the same string as `render`.
   *
   * {{{
   * p("Fresh ", em("today")).renderMinified
   * // <p>Fresh <em>today</em></p>
   * }}}
   */
  def renderMinified: String = {
    val sb = new java.lang.StringBuilder(256)
    renderMinifiedTo(sb)
    sb.toString
  }

  /** Appends the compact HTML of this node to `sb`. */
  private[html] def renderTo(sb: java.lang.StringBuilder): Unit
  private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit
  private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit

  /**
   * Applies `pf` to this node and to every node below it, a node before its
   * children (depth-first pre-order), and returns the results in that order.
   *
   * {{{
   * val menu = div(h2("Drinks"), ul(li("Tea"), li(strong("Coffee"))))
   * menu.collect { case Dom.Text(s) => Dom.Text(s.toUpperCase) }
   * // List(Text(DRINKS), Text(TEA), Text(COFFEE))
   * }}}
   */
  def collect(pf: PartialFunction[Dom, Dom]): List[Dom] = {
    val buf = List.newBuilder[Dom]
    Dom.collectImpl(this, pf, buf)
    buf.result()
  }

  /**
   * Returns a copy of the tree without the nodes that fail `predicate`. A
   * failing child is removed together with everything below it, and an element
   * whose children all fail stays, with no children. When this node itself
   * fails, the result is [[Dom.Empty]].
   *
   * {{{
   * val menu = div(h2("Drinks"), ul(li("Tea"), li(strong("Coffee"))))
   * menu.filter { case el: Dom.Element => el.tag != "strong"; case _ => true }.render
   * // <div><h2>Drinks</h2><ul><li>Tea</li><li></li></ul></div>
   * }}}
   */
  def filter(predicate: Dom => Boolean): Dom =
    Dom.filterImpl(this, predicate)

  /**
   * Returns the first node that satisfies `predicate`, checking this node first
   * and then the nodes below it in depth-first pre-order.
   *
   * {{{
   * val menu = div(h2("Drinks"), ul(li("Tea"), li(strong("Coffee"))))
   * menu.find { case Dom.Text(s) => s.startsWith("C"); case _ => false }
   * // Some(Text(Coffee))
   * }}}
   */
  def find(predicate: Dom => Boolean): Option[Dom] =
    Dom.findImpl(this, predicate)

  /**
   * Rebuilds the tree by applying `f` to this node and then, when the result is
   * an element, to each of its children, recursively. `f` sees a node before
   * its children, and the children it sees are those of the node `f` returned.
   * Returning [[Dom.Empty]] hides a node, which stays in its parent's children.
   *
   * {{{
   * val menu = div(h2("Drinks"), ul(li("Tea"), li(strong("Coffee"))))
   * menu.transform {
   *   case el: Dom.Element.Generic if el.tag == "h2" => el.copy(tag = "h3")
   *   case other                                     => other
   * }.render
   * // <div><h3>Drinks</h3><ul><li>Tea</li><li><strong>Coffee</strong></li></ul></div>
   * }}}
   */
  def transform(f: Dom => Dom): Dom =
    Dom.transformImpl(this, f)

  /**
   * Returns the elements below this node that match `selector`, in document
   * order. This node is not a candidate itself, except as the left side of `>`
   * or `>>`. [[DomSelection#select]] lists what matches and the known bugs.
   *
   * {{{
   * val menu = div(h2("Drinks"), ul(li("Tea"), li(strong("Coffee"))))
   * menu.select(CssSelector.Element("li")).texts
   * // Chunk(Tea,Coffee)
   * }}}
   */
  def select(selector: CssSelector): DomSelection = DomSelection.select(this, selector)

  /**
   * Returns `true` for [[Dom.Empty]] and for a [[Dom.Text]] with empty content,
   * `false` otherwise. An element with no children is not empty, because it
   * still renders its tags.
   */
  def isEmpty: Boolean = this match {
    case Dom.Empty           => true
    case Dom.Text(c)         => c.isEmpty
    case _: Dom.Element      => false
    case _: Dom.Element.Void => false
    case _: Dom.Doctype      => false
  }
}

/**
 * Constructors for nodes and attributes, and the node types. Most code uses the
 * DSL from `import zio.blocks.html._` instead of calling these directly.
 */
object Dom {

  /**
   * An HTML element that can have children: a tag name, attributes and child
   * nodes. The DSL values (`div`, `p`, `a`, ...) are elements with no
   * attributes or children; apply one to modifiers to get a copy with more of
   * them.
   *
   * Most tags are [[Dom.Element.Generic]]. [[Dom.Element.Script]] and
   * [[Dom.Element.Style]] render their text children without escaping, and the
   * typed classes behind the content-model markers ([[Dom.Element.Li]],
   * [[Dom.Element.Tr]], ...) are what `li(...)`, `tr(...)` and the other typed
   * factories return. Two elements are equal when their tag, attributes,
   * children and escaping match, whatever their classes. An element also stands
   * for a CSS selector of its tag, as in `div > p`.
   *
   * {{{
   * div(className := "badge").when(true)(className += "sale", "-20%").render
   * // <div class="badge sale">-20%</div>
   * }}}
   */
  sealed trait Element extends Dom with CssSelectable {

    /** The tag name, such as `div`. */
    def tag: String

    /**
     * The attributes in the order they were added. Rendering merges repeated
     * names.
     */
    def attributes: Chunk[Attribute]

    /** The child nodes, in order. */
    def children: Chunk[Dom]

    /**
     * A selector that matches this element's tag, so `div > p` reads like CSS.
     */
    val selector: CssSelector         = CssSelector.Element(tag)
    private[html] def isVoid: Boolean = Dom.voidElements.contains(tag)

    /** Returns a copy with `attrs` in place of the current attributes. */
    def withAttributes(attrs: Chunk[Attribute]): Element

    /** Returns a copy with `kids` in place of the current children. */
    def withChildren(kids: Chunk[Dom]): Element

    /**
     * Returns a copy with the modifiers applied in order: attributes are
     * appended to the attributes and nodes to the children. A `String` becomes
     * a text child, a disabled boolean attribute or `None` adds nothing, and a
     * collection adds each item. The typed factories' content-model checks do
     * not apply here.
     */
    def apply(effect: DomModifier, effects: DomModifier*): Element =
      ToModifier.buildFromEffects(this, effect, effects)

    /**
     * Applies the modifiers as `apply` does when `condition` is `true`, and
     * returns this element unchanged otherwise.
     */
    def when(condition: Boolean)(effect: DomModifier, effects: DomModifier*): Element =
      if (condition) ToModifier.buildFromEffects(this, effect, effects)
      else this

    /**
     * Applies the modifiers that `f` returns for the value in `option`, as
     * `apply` does, and returns this element unchanged for `None`.
     *
     * {{{
     * div(className := "badge").whenSome(Option("Sold out"))(msg => Seq(titleAttr := msg, msg)).render
     * // <div class="badge" title="Sold out">Sold out</div>
     * }}}
     */
    def whenSome[T](option: Option[T])(f: T => Seq[DomModifier]): Element =
      option match {
        case Some(value) => ToModifier.buildFromEffects(this, f(value))
        case None        => this
      }

    /** Whether rendering HTML-escapes this element's text children. */
    private[html] def escapeText: Boolean

    /**
     * Equality shared by the `Element` implementations: equal tag, attributes,
     * children and [[escapeText]], whatever the concrete classes, so equal
     * elements render identically (a `Script` never equals an equally shaped
     * `Generic`).
     */
    private[html] def structuralEquals(other: Any): Boolean = other match {
      case e: Element =>
        tag == e.tag && attributes == e.attributes && children == e.children && escapeText == e.escapeText
      case _ => false
    }

    /** Hash code consistent with [[structuralEquals]]. */
    private[html] def structuralHashCode: Int = (tag, attributes, children, escapeText).hashCode

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

  /** The element classes and the typed content-model markers. */
  object Element {

    // --- Sealed markers for typed content models ---
    //
    // These markers classify elements by the HTML content-model role they play
    // as children of other elements (e.g., only `Li` elements may appear
    // directly inside `ul`/`ol`). They are sealed: the only implementations are
    // the dedicated element classes in this file, so a marker type guarantees
    // both the tag and the content-model role at compile time.

    /**
     * An `<li>` list item, accepted as an element child of `ul(...)` and
     * `ol(...)`. Produced by the `li(...)` factory.
     */
    sealed trait Li extends Element

    /**
     * A table cell (`<th>` or `<td>`), accepted as an element child of
     * `tr(...)`.
     */
    sealed trait Cell extends Element

    /**
     * A `<th>` header cell, accepted as an element child of `tr(...)`. Produced
     * by the `th(...)` factory.
     */
    sealed trait Th extends Cell

    /**
     * A `<td>` data cell, accepted as an element child of `tr(...)`. Produced
     * by the `td(...)` factory.
     */
    sealed trait Td extends Cell

    /**
     * A `<tr>` table row, accepted as an element child of `table(...)`.
     * Produced by the `tr(...)` factory.
     */
    sealed trait Tr extends Element

    /**
     * An `<option>` or `<optgroup>`, accepted as an element child of
     * `select(...)`.
     */
    sealed trait SelectChild extends Element

    /**
     * An `<option>` element, accepted as an element child of `select(...)` and
     * `optgroup(...)`. Produced by `option(...)`, and on Scala 3 also by
     * `opt(...)`.
     */
    sealed trait Opt extends SelectChild

    /**
     * An `<optgroup>` element, accepted as an element child of `select(...)`.
     * Produced by the `optgroup(...)` factory.
     */
    sealed trait Optgroup extends SelectChild

    /**
     * An element for any tag without a dedicated class (`div`, `p`, `span`,
     * ...). Text children are HTML-escaped when rendered. A `Generic` with a
     * void tag such as `br` or `img`, which the `html"..."` interpolator
     * creates, renders as `<br/>` and drops its children.
     *
     * Equality is structural across all `Element` implementations: a `Generic`
     * equals any other element with the same tag, attributes, children, and
     * text-escaping semantics, regardless of the concrete class (e.g.,
     * `Generic("li", ...)` equals the equivalent `LiElement`).
     *
     * @param tag
     *   the element tag name (e.g., "div", "h1")
     * @param attributes
     *   the attributes, in the order they were added
     * @param children
     *   child DOM nodes
     */
    final case class Generic(
      tag: String,
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element {
      private[html] def escapeText: Boolean = true

      /** Returns a copy with `attrs` in place of the current attributes. */
      def withAttributes(attrs: Chunk[Attribute]): Generic = copy(attributes = attrs)

      /** Returns a copy with `kids` in place of the current children. */
      def withChildren(kids: Chunk[Dom]): Generic = copy(children = kids)

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    // --- Typed content model elements (proper OO hierarchy for Scala 2/3 parity) ---

    /**
     * An `<li>` list item element.
     *
     * Equality is structural across all `Element` implementations, so a
     * `LiElement` equals a `Generic("li", ...)` with the same attributes and
     * children.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes
     */
    final case class LiElement(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element
        with Li {

      /** Always `li`. */
      def tag: String                       = "li"
      private[html] def escapeText: Boolean = true

      /**
       * Returns a copy with `attrs` in place of the current attributes, still
       * typed [[Dom.Element.Li]].
       */
      def withAttributes(attrs: Chunk[Attribute]): Li = copy(attributes = attrs)

      /**
       * Returns a copy with `kids` in place of the current children, still
       * typed [[Dom.Element.Li]].
       */
      def withChildren(kids: Chunk[Dom]): Li                             = copy(children = kids)
      override def apply(effect: DomModifier, effects: DomModifier*): Li =
        ToModifier.buildFromEffects(this, effect, effects).asInstanceOf[Li]
      override def when(condition: Boolean)(effect: DomModifier, effects: DomModifier*): Li =
        if (condition) apply(effect, effects: _*) else this

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * A `<th>` table header cell element.
     *
     * Equality is structural across all `Element` implementations, so a
     * `ThElement` equals a `Generic("th", ...)` with the same attributes and
     * children.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes
     */
    final case class ThElement(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element
        with Th {

      /** Always `th`. */
      def tag: String                       = "th"
      private[html] def escapeText: Boolean = true

      /**
       * Returns a copy with `attrs` in place of the current attributes, still
       * typed [[Dom.Element.Th]].
       */
      def withAttributes(attrs: Chunk[Attribute]): Th = copy(attributes = attrs)

      /**
       * Returns a copy with `kids` in place of the current children, still
       * typed [[Dom.Element.Th]].
       */
      def withChildren(kids: Chunk[Dom]): Th                             = copy(children = kids)
      override def apply(effect: DomModifier, effects: DomModifier*): Th =
        ToModifier.buildFromEffects(this, effect, effects).asInstanceOf[Th]
      override def when(condition: Boolean)(effect: DomModifier, effects: DomModifier*): Th =
        if (condition) apply(effect, effects: _*) else this

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * A `<td>` table data cell element.
     *
     * Equality is structural across all `Element` implementations, so a
     * `TdElement` equals a `Generic("td", ...)` with the same attributes and
     * children.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes
     */
    final case class TdElement(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element
        with Td {

      /** Always `td`. */
      def tag: String                       = "td"
      private[html] def escapeText: Boolean = true

      /**
       * Returns a copy with `attrs` in place of the current attributes, still
       * typed [[Dom.Element.Td]].
       */
      def withAttributes(attrs: Chunk[Attribute]): Td = copy(attributes = attrs)

      /**
       * Returns a copy with `kids` in place of the current children, still
       * typed [[Dom.Element.Td]].
       */
      def withChildren(kids: Chunk[Dom]): Td                             = copy(children = kids)
      override def apply(effect: DomModifier, effects: DomModifier*): Td =
        ToModifier.buildFromEffects(this, effect, effects).asInstanceOf[Td]
      override def when(condition: Boolean)(effect: DomModifier, effects: DomModifier*): Td =
        if (condition) apply(effect, effects: _*) else this

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * A `<tr>` table row element, returned by `tr(...)`.
     *
     * It does not override `apply` or `withChildren`: both come back typed
     * [[Dom.Element]], so a row changed through them no longer fits
     * `table(...)`. The check of the cells happens in the `tr(...)` factory
     * only; `apply` accepts any modifier, so `tr(td("Tea"))(p("note"))`
     * compiles and renders a `<p>` inside the row. [[withAttributes]] keeps the
     * [[Dom.Element.Tr]] type.
     *
     * Equality is structural across all `Element` implementations, so a
     * `TrElement` equals a `Generic("tr", ...)` with the same attributes and
     * children.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes (`<th>`/`<td>` cells when built by `tr(...)`)
     */
    final case class TrElement(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element
        with Tr {

      /** Always `tr`. */
      def tag: String                       = "tr"
      private[html] def escapeText: Boolean = true

      /**
       * Returns a copy with `attrs` in place of the current attributes, still
       * typed [[Dom.Element.Tr]].
       */
      def withAttributes(attrs: Chunk[Attribute]): Tr = copy(attributes = attrs)

      /**
       * Returns a copy with `kids` as children, typed [[Dom.Element]] rather
       * than [[Dom.Element.Tr]], so it no longer fits `table(...)`. At run time
       * it is still a `TrElement`.
       */
      def withChildren(kids: Chunk[Dom]): Element = copy(children = kids)

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * An `<option>` element.
     *
     * Equality is structural across all `Element` implementations, so an
     * `OptElement` equals a `Generic("option", ...)` with the same attributes
     * and children.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes
     */
    final case class OptElement(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element
        with Opt {

      /** Always `option`. */
      def tag: String                       = "option"
      private[html] def escapeText: Boolean = true

      /**
       * Returns a copy with `attrs` in place of the current attributes, still
       * typed [[Dom.Element.Opt]].
       */
      def withAttributes(attrs: Chunk[Attribute]): Opt = copy(attributes = attrs)

      /**
       * Returns a copy with `kids` in place of the current children, still
       * typed [[Dom.Element.Opt]].
       */
      def withChildren(kids: Chunk[Dom]): Opt                             = copy(children = kids)
      override def apply(effect: DomModifier, effects: DomModifier*): Opt =
        ToModifier.buildFromEffects(this, effect, effects).asInstanceOf[Opt]
      override def when(condition: Boolean)(effect: DomModifier, effects: DomModifier*): Opt =
        if (condition) apply(effect, effects: _*) else this

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * An `<optgroup>` element, returned by `optgroup(...)`.
     *
     * It does not override `apply` or `withChildren`: both come back typed
     * [[Dom.Element]], so a group changed through them no longer fits
     * `select(...)`. The check of the options happens in the `optgroup(...)`
     * factory only; `apply` accepts any modifier. [[withAttributes]] keeps the
     * [[Dom.Element.Optgroup]] type.
     *
     * Equality is structural across all `Element` implementations, so an
     * `OptgroupElement` equals a `Generic("optgroup", ...)` with the same
     * attributes and children.
     *
     * @param attributes
     *   attribute key-value pairs
     * @param children
     *   child DOM nodes (`<option>` elements when built by `optgroup(...)`)
     */
    final case class OptgroupElement(
      attributes: Chunk[Attribute],
      children: Chunk[Dom]
    ) extends Element
        with Optgroup {

      /** Always `optgroup`. */
      def tag: String                       = "optgroup"
      private[html] def escapeText: Boolean = true

      /**
       * Returns a copy with `attrs` in place of the current attributes, still
       * typed [[Dom.Element.Optgroup]].
       */
      def withAttributes(attrs: Chunk[Attribute]): Optgroup = copy(attributes = attrs)

      /**
       * Returns a copy with `kids` as children, typed [[Dom.Element]] rather
       * than [[Dom.Element.Optgroup]], so it no longer fits `select(...)`. At
       * run time it is still an `OptgroupElement`.
       */
      def withChildren(kids: Chunk[Dom]): Element = copy(children = kids)

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * A `<script>` element. Its text children render without HTML escaping, so
     * JavaScript keeps its `<`, `&` and quotes; rendering only rewrites `</` as
     * `<\/`, so the code cannot close the element early. Element children are
     * rendered as usual. Add code with `inlineJs` or a source URL with
     * `externalJs`.
     *
     * Equality is structural across all `Element` implementations, including
     * the text-escaping semantics: because `Script` renders its children
     * unescaped, it never equals an equally-shaped `Generic("script", ...)`,
     * whose children would be escaped on render.
     *
     * {{{
     * script().inlineJs(js"cart.total = \${42}").externalJs("/cart.js").render
     * // <script src="/cart.js">cart.total = 42</script>
     * }}}
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

      /** Always `script`. */
      def tag: String                       = "script"
      private[html] def escapeText: Boolean = false

      /** Returns a copy with `attrs` in place of the current attributes. */
      def withAttributes(attrs: Chunk[Attribute]): Script = copy(attributes = attrs)

      /** Returns a copy with `kids` in place of the current children. */
      def withChildren(kids: Chunk[Dom]): Script = copy(children = kids)

      /**
       * Returns a copy with `code` appended as a text child, with each `</` in
       * it replaced by `<\/`. The code is not otherwise escaped, so build it
       * with `js"..."` rather than from user text.
       */
      def inlineJs(code: Js): Script = {
        val escaped = code.value.replace("</", "<\\/")
        copy(children = children :+ Dom.Text(escaped))
      }

      /**
       * Returns a copy with a `src` attribute set to `url`, appended after the
       * current attributes. When `src` is already set, the last value wins when
       * rendered.
       */
      def externalJs(url: String): Script =
        copy(attributes = attributes :+ Attribute.KeyValue("src", AttributeValue.StringValue(url)))

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * A `<style>` element. Its text children render without HTML escaping, so
     * CSS keeps its `>` and quotes; rendering only rewrites `</` as `<\/`. Add
     * a stylesheet with `inlineCss`.
     *
     * Equality is structural across all `Element` implementations, including
     * the text-escaping semantics: because `Style` renders its children
     * unescaped, it never equals an equally-shaped `Generic("style", ...)`,
     * whose children would be escaped on render.
     *
     * {{{
     * import zio.blocks.chunk.Chunk
     *
     * style().inlineCss(Css.Rule(CssSelector.Class("price"), Chunk(Css.Declaration("color", CssColor.Rgb(200, 0, 0))))).render
     * // <style>.price{color:rgb(200,0,0);}</style>
     * }}}
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

      /** Always `style`. */
      def tag: String                       = "style"
      private[html] def escapeText: Boolean = false

      /** Returns a copy with `attrs` in place of the current attributes. */
      def withAttributes(attrs: Chunk[Attribute]): Style = copy(attributes = attrs)

      /** Returns a copy with `kids` in place of the current children. */
      def withChildren(kids: Chunk[Dom]): Style = copy(children = kids)

      /** Returns a copy with `code.render` appended as a text child. */
      def inlineCss(code: Css): Style =
        copy(children = children :+ Dom.Text(code.render))

      override def equals(other: Any): Boolean = structuralEquals(other)
      override def hashCode: Int               = structuralHashCode
    }

    /**
     * A void element: a tag such as `br`, `img` or `input` that takes
     * attributes but no children. Passing a child does not compile, and the
     * element renders self-closed, as in `<img src="/tea.png"/>`. It extends
     * [[Dom]] directly, not [[Dom.Element]], so it has no `children` and no
     * `apply(DomModifier*)`.
     *
     * `Dom#select` never returns a void element, because matching only looks at
     * [[Dom.Element]] nodes, which is a known bug. `DomSelection#children` and
     * `DomSelection#descendants` do include void elements.
     *
     * {{{
     * img(src := "/tea.png")(alt := "Tea").render
     * // <img src="/tea.png" alt="Tea"/>
     * }}}
     */
    sealed trait Void extends Dom with CssSelectable {

      /** The tag name, such as `img`. */
      def tag: String

      /** The attributes in the order they were added. */
      def attributes: Chunk[Attribute]

      /** A selector that matches this element's tag. */
      val selector: CssSelector = CssSelector.Element(tag)

      /** Returns a copy with `attrs` in place of the current attributes. */
      def withAttributes(attrs: Chunk[Attribute]): Void

      /** Returns a copy with the attributes appended, in order. */
      def apply(attr: Attribute, attrs: Attribute*): Void

      private[html] def renderTo(sb: java.lang.StringBuilder): Unit = {
        sb.append('<')
        sb.append(tag)
        renderAttributes(resolveOrPassthrough(attributes), sb)
        sb.append("/>")
      }

      private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit = renderTo(sb)

      private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit = renderTo(sb)
    }

    /**
     * A void element for any tag, such as `br`, `img` or `input`. Takes
     * attributes only; passing a child does not compile.
     *
     * @param tag
     *   the element tag name (e.g., "br", "img")
     * @param attributes
     *   attribute key-value pairs
     */
    final case class VoidGeneric(
      tag: String,
      attributes: Chunk[Attribute]
    ) extends Void {

      /** Returns a copy with `attrs` in place of the current attributes. */
      def withAttributes(attrs: Chunk[Attribute]): VoidGeneric = copy(attributes = attrs)

      /** Returns a copy with the attributes appended, in order. */
      def apply(attr: Attribute, attrs: Attribute*): VoidGeneric = {
        val b = Chunk.newBuilder[Attribute]
        b ++= attributes
        b += attr
        var i = 0
        while (i < attrs.length) {
          b += attrs(i)
          i += 1
        }
        copy(attributes = b.result())
      }
    }
  }

  /**
   * A document type declaration, rendered as `<!DOCTYPE value>`; the DSL value
   * `doctype` is `Doctype("html")`. It adds no indentation of its own, but as
   * the child of an element `render(indent)` indents it like any other child,
   * so render it before the `html` element rather than inside it. Tree
   * operations treat it as a leaf.
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
   * A text node. It stores the raw string and escapes `&`, `<`, `>`, `"` and
   * `'` when rendered. As a direct child of a `script` or `style` element it is
   * written as is instead, apart from `</` becoming `<\/`, so never put
   * untrusted text there.
   *
   * {{{
   * Dom.Text("Fish & chips").render
   * // Fish &amp; chips
   * }}}
   *
   * @param content
   *   the raw text
   */
  final case class Text(content: String) extends Dom {
    private[html] def renderTo(sb: java.lang.StringBuilder): Unit                                = Escape.htmlTo(content, sb)
    private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit                        = Escape.htmlTo(content, sb)
    private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit =
      Escape.htmlTo(content, sb)
  }

  /**
   * A node that renders nothing. Use it for a branch with no output, as in
   * `if (onSale) badge else Dom.Empty`. As a child it stays in the children
   * list. `filter` returns it when the root fails the predicate.
   */
  case object Empty extends Dom {
    private[html] def renderTo(sb: java.lang.StringBuilder): Unit                                = ()
    private[html] def renderMinifiedTo(sb: java.lang.StringBuilder): Unit                        = ()
    private[html] def renderIndented(sb: java.lang.StringBuilder, level: Int, indent: Int): Unit = ()
  }

  /**
   * An attribute of an element, usually built with the DSL keys (`id :=
   * "cart"`, `className += "sale"`, `disabled`) rather than directly.
   *
   * Rendering merges attributes that share a name. The last
   * [[Dom.Attribute.KeyValue]] wins and stays at its own position; earlier ones
   * are dropped. [[Dom.Attribute.AppendValue]] values are joined after that
   * value with their separators, at the position of the last `KeyValue`, or of
   * the first `AppendValue` when the name has no `KeyValue`.
   * [[Dom.Attribute.BooleanAttribute]] values are not merged, so a repeated one
   * renders twice.
   *
   * {{{
   * div(className := "box", className += "wide", id := "a", className += "dark", id := "b").render
   * // <div class="box wide dark" id="b"></div>
   * }}}
   */
  sealed trait Attribute extends Product with Serializable

  /** The attribute cases. */
  object Attribute {

    /**
     * Sets attribute `name` to `value`. A later `KeyValue` with the same name
     * replaces it when rendered.
     */
    final case class KeyValue(name: String, value: AttributeValue) extends Attribute

    /**
     * Adds `value` to attribute `name`, joined to the other values with
     * `separator` when rendered. `+=` on a [[MultiAttributeKey]] builds it.
     */
    final case class AppendValue(name: String, value: AttributeValue, separator: AttributeSeparator) extends Attribute

    /**
     * An attribute without a value, such as `disabled`: renders as the bare
     * name when `enabled` and not at all otherwise.
     *
     * {{{
     * input(`type` := "checkbox", checked := false, disabled := true).render
     * // <input type="checkbox" disabled/>
     * }}}
     */
    final case class BooleanAttribute(name: String, enabled: Boolean = true) extends Attribute {

      /**
       * Returns a copy with `enabled` set to `value`, as in
       * `disabled := isLocked`.
       */
      def :=(value: Boolean): BooleanAttribute = copy(enabled = value)
    }
  }

  /**
   * The value of a [[Dom.Attribute.KeyValue]] or [[Dom.Attribute.AppendValue]].
   */
  sealed trait AttributeValue extends Product with Serializable

  /** The attribute value cases. */
  object AttributeValue {

    /** A text value, HTML-escaped when rendered. */
    final case class StringValue(value: String) extends AttributeValue

    /**
     * Several values, each HTML-escaped and joined with `separator` when
     * rendered. With no values the attribute is left out.
     */
    final case class MultiValue(values: Chunk[String], separator: AttributeSeparator) extends AttributeValue

    /**
     * JavaScript code, for event handlers such as `onclick`. It is HTML-escaped
     * when rendered.
     */
    final case class JsValue(js: Js) extends AttributeValue

    /**
     * Renders the bare attribute name when `true` and leaves the attribute out
     * when `false`. `:=` with a `Boolean` on an [[AttributeKey]] builds it.
     */
    final case class BooleanValue(value: Boolean) extends AttributeValue
  }

  /** The text placed between the values of a multi-valued attribute. */
  sealed trait AttributeSeparator extends Product with Serializable {

    /** Returns the separator text. */
    def render: String
  }

  /** The separators. */
  object AttributeSeparator {

    /** A single space, used by `class` and `rel`. */
    case object Space extends AttributeSeparator { def render: String = " " }

    /** A comma, with no space. */
    case object Comma extends AttributeSeparator { def render: String = "," }

    /** A semicolon, with no space. */
    case object Semicolon extends AttributeSeparator { def render: String = ";" }

    /** Any separator text. */
    final case class Custom(separator: String) extends AttributeSeparator {

      /** Returns `separator`. */
      def render: String = separator
    }
  }

  /** Creates a text node; the same as `Dom.Text(content)`. */
  def text(content: String): Text = Text(content)

  /** [[Dom.Empty]], typed as `Dom`. */
  val empty: Dom = Empty

  /**
   * Creates a boolean attribute such as `hidden`, enabled unless `enabled` is
   * `false`.
   */
  def boolAttr(name: String, enabled: Boolean = true): Attribute.BooleanAttribute =
    Attribute.BooleanAttribute(name, enabled)

  /**
   * Returns a key for the multi-valued attribute `name`, whose values are
   * joined with spaces.
   */
  def multiAttr(name: String): MultiAttributeKey =
    new MultiAttributeKey(name, AttributeSeparator.Space)

  /**
   * Returns a key for the multi-valued attribute `name`, whose values are
   * joined with `separator`.
   */
  def multiAttr(name: String, separator: AttributeSeparator): MultiAttributeKey =
    new MultiAttributeKey(name, separator)

  /** Creates attribute `name` holding `values` joined with spaces. */
  def multiAttr(name: String, values: Iterable[String]): Attribute =
    Attribute.KeyValue(name, AttributeValue.MultiValue(Chunk.from(values), AttributeSeparator.Space))

  /**
   * Creates attribute `name` holding the values joined with `separator`.
   *
   * {{{
   * div(Dom.multiAttr("data-tags", Dom.AttributeSeparator.Comma, "tea", "green")).render
   * // <div data-tags="tea,green"></div>
   * }}}
   */
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

  private val urlAttributes: Set[String] = Set(
    "href",
    "src",
    "action",
    "formaction",
    "hx-get",
    "hx-post",
    "hx-put",
    "hx-patch",
    "hx-delete",
    "hx-push-url",
    "hx-replace-url"
  )

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
