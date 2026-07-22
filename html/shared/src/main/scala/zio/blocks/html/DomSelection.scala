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

import zio.blocks.chunk.{Chunk, ChunkBuilder}

/**
 * A wrapper around `Chunk[Dom]` that provides fluent chaining for CSS
 * selector-based DOM navigation and querying operations.
 *
 * DomSelection enables a fluent API style for navigating through DOM
 * structures:
 * {{{
 *   dom.select(CssSelector.Element("div")).children.select(CssSelector.Element("p")).texts
 * }}}
 *
 * The selection can contain zero, one, or multiple DOM nodes, supporting both
 * single-value navigation and multi-value queries.
 */
final case class DomSelection(nodes: Chunk[Dom]) extends AnyVal {

  // ─────────────────────────────────────────────────────────────────────────
  // Size Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if this selection contains no nodes. */
  def isEmpty: Boolean = nodes.isEmpty

  /** Returns true if this selection contains at least one node. */
  def nonEmpty: Boolean = nodes.nonEmpty

  /** Returns the number of selected nodes. */
  def length: Int = nodes.length

  // ─────────────────────────────────────────────────────────────────────────
  // Extraction
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the selected nodes as a Chunk. */
  def toChunk: Chunk[Dom] = nodes

  /** Returns the first node if present. */
  def headOption: Option[Dom] =
    if (nodes.isEmpty) None
    else new Some(nodes(0))

  // ─────────────────────────────────────────────────────────────────────────
  // Selection
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Selects all descendant nodes matching the given CSS selector.
   *
   * For simple selectors (Element, Class, Id, Universal, And, Or, Not,
   * Attribute), searches all descendants of each node in this selection. For
   * structural selectors (Child, Descendant), walks the tree accordingly.
   *
   * @param selector
   *   the CSS selector to match against
   * @return
   *   a new selection containing all matching nodes
   */
  def select(selector: CssSelector): DomSelection = selector match {
    case CssSelector.Child(parent, child) =>
      DomSelection.selectChild(nodes, parent, child)
    case CssSelector.Descendant(ancestor, desc) =>
      DomSelection.selectDescendant(nodes, ancestor, desc)
    case _ =>
      val builder = ChunkBuilder.make[Dom]()
      var i       = 0
      while (i < nodes.length) {
        DomSelection.collectDescendants(nodes(i), selector, builder)
        i += 1
      }
      new DomSelection(builder.result())
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns the direct children of all element nodes in this selection. */
  def children: DomSelection = {
    val builder = ChunkBuilder.make[Dom]()
    var i       = 0
    while (i < nodes.length) {
      nodes(i) match {
        case el: Dom.Element =>
          var j = 0
          while (j < el.children.length) {
            builder += el.children(j)
            j += 1
          }
        case _ => ()
      }
      i += 1
    }
    new DomSelection(builder.result())
  }

  /**
   * Returns all descendant nodes of all element nodes in this selection
   * (excluding the nodes themselves).
   */
  def descendants: DomSelection = {
    val builder = ChunkBuilder.make[Dom]()
    var i       = 0
    while (i < nodes.length) {
      nodes(i) match {
        case el: Dom.Element =>
          DomSelection.collectAllDescendants(el, builder)
        case _ => ()
      }
      i += 1
    }
    new DomSelection(builder.result())
  }

  /** Returns a selection containing only the first node. */
  def first: DomSelection =
    if (nodes.isEmpty) this
    else new DomSelection(Chunk.single(nodes(0)))

  /** Returns a selection containing only the last node. */
  def last: DomSelection =
    if (nodes.isEmpty) this
    else new DomSelection(Chunk.single(nodes(nodes.length - 1)))

  /** Returns a selection containing the node at the given index. */
  def apply(index: Int): DomSelection =
    if (index < 0 || index >= nodes.length) DomSelection.empty
    else new DomSelection(Chunk.single(nodes(index)))

  // ─────────────────────────────────────────────────────────────────────────
  // Element-specific
  // ─────────────────────────────────────────────────────────────────────────

  /** Filters this selection to keep only Element nodes. */
  def elements: DomSelection = new DomSelection(nodes.filter(_.isInstanceOf[Dom.Element]))

  /**
   * Extracts text content from all nodes in this selection.
   *
   * For Text nodes, returns the content directly. For Element nodes, returns
   * the concatenated text of all child Text nodes.
   */
  def texts: Chunk[String] = {
    val builder = ChunkBuilder.make[String]()
    var i       = 0
    while (i < nodes.length) {
      nodes(i) match {
        case Dom.Text(content) => builder += content
        case el: Dom.Element   => builder += DomSelection.extractText(el)
        case _                 => ()
      }
      i += 1
    }
    builder.result()
  }

  /**
   * Extracts attribute values with the given name from all element nodes in
   * this selection.
   */
  def attrs(name: String): Chunk[String] = {
    val builder = ChunkBuilder.make[String]()
    var i       = 0
    while (i < nodes.length) {
      nodes(i) match {
        case el: Dom.Element =>
          DomSelection.getAttributeValue(el, name) match {
            case Some(v) => builder += v
            case None    => ()
          }
        case _ => ()
      }
      i += 1
    }
    builder.result()
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Filtering
  // ─────────────────────────────────────────────────────────────────────────

  /** Filters the selection by a predicate. */
  def filter(predicate: Dom => Boolean): DomSelection =
    new DomSelection(nodes.filter(predicate))

  /** Keeps only elements with the given tag name. */
  def withTag(tag: String): DomSelection = new DomSelection(nodes.filter {
    case el: Dom.Element => el.tag == tag
    case _               => false
  })

  /** Keeps only elements that have the given CSS class. */
  def withClass(cls: String): DomSelection = new DomSelection(nodes.filter {
    case el: Dom.Element => DomSelection.hasClass(el, cls)
    case _               => false
  })

  /** Keeps only elements that have the given id. */
  def withId(idValue: String): DomSelection = new DomSelection(nodes.filter {
    case el: Dom.Element => DomSelection.hasAttributeValue(el, "id", idValue)
    case _               => false
  })

  /** Keeps only elements that have the given attribute (any value). */
  def withAttribute(name: String): DomSelection = new DomSelection(nodes.filter {
    case el: Dom.Element => DomSelection.hasAttribute(el, name)
    case _               => false
  })

  /** Keeps only elements that have the given attribute with the given value. */
  def withAttribute(name: String, value: String): DomSelection = new DomSelection(nodes.filter {
    case el: Dom.Element => DomSelection.hasAttributeValue(el, name, value)
    case _               => false
  })

  // ─────────────────────────────────────────────────────────────────────────
  // Modification
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Applies a transformation function to all Element nodes in this selection,
   * returning the modified nodes.
   */
  def modifyAll(f: Dom.Element => Dom.Element): DomSelection =
    new DomSelection(nodes.map {
      case el: Dom.Element => f(el)
      case other           => other
    })

  /** Replaces all nodes in this selection with the given replacement. */
  def replaceAll(replacement: Dom): DomSelection =
    new DomSelection(nodes.map(_ => replacement))

  /** Returns an empty selection (all nodes removed). */
  def removeAll: DomSelection = DomSelection.empty

  // ─────────────────────────────────────────────────────────────────────────
  // Combinators
  // ─────────────────────────────────────────────────────────────────────────

  /** Combines two selections. */
  def ++(other: DomSelection): DomSelection =
    new DomSelection(nodes ++ other.nodes)
}

object DomSelection {

  /** An empty selection. */
  val empty: DomSelection = new DomSelection(Chunk.empty)

  /** Creates a selection from a single DOM node. */
  def single(dom: Dom): DomSelection = new DomSelection(Chunk.single(dom))

  /** Creates a selection from multiple DOM nodes. */
  def fromChunk(nodes: Chunk[Dom]): DomSelection = new DomSelection(nodes)

  /**
   * Selects all descendant nodes of the given root that match the CSS selector.
   */
  def select(root: Dom, selector: CssSelector): DomSelection =
    new DomSelection(Chunk.single(root)).select(selector)

  // ─── Internal helpers ────────────────────────────────────────────────

  private[html] def selectorMatches(el: Dom.Element, selector: CssSelector): Boolean =
    selector match {
      case CssSelector.Element(tag)                    => el.tag == tag
      case CssSelector.Class(cls)                      => hasClass(el, cls)
      case CssSelector.Id(idVal)                       => hasAttributeValue(el, "id", idVal)
      case CssSelector.Universal                       => true
      case CssSelector.And(a, b)                       => selectorMatches(el, a) && selectorMatches(el, b)
      case CssSelector.Or(a, b)                        => selectorMatches(el, a) || selectorMatches(el, b)
      case CssSelector.Not(inner, neg)                 => selectorMatches(el, inner) && !selectorMatches(el, neg)
      case CssSelector.Attribute(inner, attr, matcher) =>
        selectorMatches(el, inner) && matchesAttribute(el, attr, matcher)
      case CssSelector.PseudoClass(inner, _)   => selectorMatches(el, inner)
      case CssSelector.PseudoElement(inner, _) => selectorMatches(el, inner)
      case CssSelector.Raw(_)                  => false
      case CssSelector.Child(_, _)             => false
      case CssSelector.Descendant(_, _)        => false
      case CssSelector.AdjacentSibling(_, _)   => false
      case CssSelector.GeneralSibling(_, _)    => false
    }

  private def matchesAttribute(el: Dom.Element, attr: String, matcher: Option[CssSelector.AttributeMatch]): Boolean =
    matcher match {
      case None    => hasAttribute(el, attr)
      case Some(m) =>
        getAttributeValue(el, attr) match {
          case None    => false
          case Some(v) =>
            m match {
              case CssSelector.AttributeMatch.Exact(expected)            => v == expected
              case CssSelector.AttributeMatch.Contains(sub)              => v.indexOf(sub) >= 0
              case CssSelector.AttributeMatch.StartsWith(prefix)         => v.startsWith(prefix)
              case CssSelector.AttributeMatch.EndsWith(suffix)           => v.endsWith(suffix)
              case CssSelector.AttributeMatch.WhitespaceContains(word)   => v.split("\\s+").exists(_ == word)
              case CssSelector.AttributeMatch.HyphenPrefix(hyphenPrefix) =>
                v == hyphenPrefix || v.startsWith(hyphenPrefix + "-")
            }
        }
    }

  /**
   * Collects descendants of a node matching the selector (excludes the node
   * itself).
   */
  private[html] def collectDescendants(
    node: Dom,
    selector: CssSelector,
    builder: ChunkBuilder[Dom]
  ): Unit = node match {
    case el: Dom.Element =>
      var i = 0
      while (i < el.children.length) {
        el.children(i) match {
          case childEl: Dom.Element =>
            if (selectorMatches(childEl, selector)) builder += childEl
            collectDescendants(childEl, selector, builder)
          case _ => ()
        }
        i += 1
      }
    case _ => ()
  }

  /** Collects all descendant nodes (all types). */
  private def collectAllDescendants(el: Dom.Element, builder: ChunkBuilder[Dom]): Unit = {
    var i = 0
    while (i < el.children.length) {
      val child = el.children(i)
      builder += child
      child match {
        case childEl: Dom.Element => collectAllDescendants(childEl, builder)
        case _                    => ()
      }
      i += 1
    }
  }

  /**
   * Handles `Child(parent, child)` selector: find children of parent-matching
   * elements.
   */
  private def selectChild(
    roots: Chunk[Dom],
    parentSel: CssSelector,
    childSel: CssSelector
  ): DomSelection = {
    val parentBuilder = ChunkBuilder.make[Dom]()
    var i             = 0
    while (i < roots.length) {
      collectSelfAndDescendants(roots(i), parentSel, parentBuilder)
      i += 1
    }
    val parents = parentBuilder.result()

    val resultBuilder = ChunkBuilder.make[Dom]()
    i = 0
    while (i < parents.length) {
      parents(i) match {
        case el: Dom.Element =>
          var j = 0
          while (j < el.children.length) {
            el.children(j) match {
              case childEl: Dom.Element if selectorMatches(childEl, childSel) =>
                resultBuilder += childEl
              case _ => ()
            }
            j += 1
          }
        case _ => ()
      }
      i += 1
    }
    new DomSelection(resultBuilder.result())
  }

  /** Handles `Descendant(ancestor, desc)` selector. */
  private def selectDescendant(
    roots: Chunk[Dom],
    ancestorSel: CssSelector,
    descSel: CssSelector
  ): DomSelection = {
    val ancestorBuilder = ChunkBuilder.make[Dom]()
    var i               = 0
    while (i < roots.length) {
      collectSelfAndDescendants(roots(i), ancestorSel, ancestorBuilder)
      i += 1
    }
    val ancestors = ancestorBuilder.result()

    val resultBuilder = ChunkBuilder.make[Dom]()
    i = 0
    while (i < ancestors.length) {
      collectDescendants(ancestors(i), descSel, resultBuilder)
      i += 1
    }
    new DomSelection(resultBuilder.result())
  }

  /** Collects self (if matching) and all descendants matching a selector. */
  private def collectSelfAndDescendants(
    node: Dom,
    selector: CssSelector,
    builder: ChunkBuilder[Dom]
  ): Unit = node match {
    case el: Dom.Element =>
      if (selectorMatches(el, selector)) builder += el
      var i = 0
      while (i < el.children.length) {
        collectSelfAndDescendants(el.children(i), selector, builder)
        i += 1
      }
    case _ => ()
  }

  /** Extracts concatenated text content from an element. */
  private[html] def extractText(el: Dom.Element): String = {
    val sb = new java.lang.StringBuilder
    extractTextImpl(el, sb)
    sb.toString
  }

  private def extractTextImpl(node: Dom, sb: java.lang.StringBuilder): Unit = node match {
    case Dom.Text(content) => sb.append(content)
    case el: Dom.Element   =>
      var i = 0
      while (i < el.children.length) {
        extractTextImpl(el.children(i), sb)
        i += 1
      }
    case _ => ()
  }

  /**
   * Checks if an element has the given CSS class (space-separated "class"
   * attribute).
   */
  private[html] def hasClass(el: Dom.Element, cls: String): Boolean = {
    var i = 0
    while (i < el.attributes.length) {
      el.attributes(i) match {
        case Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue(v)) =>
          return splitContains(v, cls)
        case Dom.Attribute.KeyValue("class", Dom.AttributeValue.MultiValue(values, _)) =>
          var j = 0
          while (j < values.length) {
            if (values(j) == cls) return true
            j += 1
          }
          return false
        case Dom.Attribute.AppendValue("class", Dom.AttributeValue.StringValue(v), _) =>
          if (splitContains(v, cls)) return true
        case Dom.Attribute.AppendValue("class", Dom.AttributeValue.MultiValue(values, _), _) =>
          var j = 0
          while (j < values.length) {
            if (values(j) == cls) return true
            j += 1
          }
        case _ => ()
      }
      i += 1
    }
    false
  }

  private def splitContains(spaceDelimited: String, target: String): Boolean = {
    val len = spaceDelimited.length
    val tl  = target.length
    if (tl == 0 || len == 0) return false
    var start = 0
    while (start < len) {
      while (start < len && spaceDelimited.charAt(start) == ' ') start += 1
      var end = start
      while (end < len && spaceDelimited.charAt(end) != ' ') end += 1
      if (end - start == tl && spaceDelimited.regionMatches(start, target, 0, tl)) return true
      start = end
    }
    false
  }

  /** Checks if an element has an attribute with the given name. */
  private[html] def hasAttribute(el: Dom.Element, name: String): Boolean = {
    var i = 0
    while (i < el.attributes.length) {
      el.attributes(i) match {
        case Dom.Attribute.KeyValue(n, _)               => if (n == name) return true
        case Dom.Attribute.AppendValue(n, _, _)         => if (n == name) return true
        case Dom.Attribute.BooleanAttribute(n, enabled) =>
          if (n == name && enabled) return true
      }
      i += 1
    }
    false
  }

  /** Checks if an element has an attribute with the given name and value. */
  private[html] def hasAttributeValue(el: Dom.Element, name: String, value: String): Boolean =
    getAttributeValue(el, name) match {
      case Some(v) => v == value
      case None    => false
    }

  /** Gets the string value of an attribute by name. */
  private[html] def getAttributeValue(el: Dom.Element, name: String): Option[String] = {
    var i = 0
    while (i < el.attributes.length) {
      el.attributes(i) match {
        case Dom.Attribute.KeyValue(n, Dom.AttributeValue.StringValue(v)) =>
          if (n == name) return new Some(v)
        case Dom.Attribute.KeyValue(n, Dom.AttributeValue.MultiValue(values, sep)) =>
          if (n == name) {
            val sb = new java.lang.StringBuilder
            var j  = 0
            while (j < values.length) {
              if (j > 0) sb.append(sep.render)
              sb.append(values(j))
              j += 1
            }
            return new Some(sb.toString)
          }
        case Dom.Attribute.KeyValue(n, Dom.AttributeValue.BooleanValue(v)) =>
          if (n == name) return new Some(v.toString)
        case Dom.Attribute.KeyValue(n, Dom.AttributeValue.JsValue(js)) =>
          if (n == name) return new Some(js.value)
        case _ => ()
      }
      i += 1
    }
    None
  }
}
