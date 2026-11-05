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

import scala.language.implicitConversions

/**
 * Argument accepted by the Scala 2 `script(...)` factory: a [[Dom.Attribute]]
 * or [[Js]] code. Implicit conversions in the companion wrap either one, so you
 * pass the attribute or the code directly.
 */
sealed trait ScriptArg

/**
 * Companion of `ScriptArg`: its cases and the implicit conversions that build
 * them.
 */
object ScriptArg {

  /** Adds an HTML attribute to the `script` element. */
  final case class Attribute(value: Dom.Attribute) extends ScriptArg

  /** Adds [[Js]] code to the `script` element body. */
  final case class Source(value: Js) extends ScriptArg

  /**
   * Wraps an attribute as a `ScriptArg`, so `script(...)` accepts it directly.
   */
  implicit def fromAttribute(attribute: Dom.Attribute): ScriptArg = Attribute(attribute)

  /**
   * Wraps [[Js]] code as a `ScriptArg`, so `script(...)` accepts it directly.
   */
  implicit def fromJs(js: Js): ScriptArg = Source(js)
}

/**
 * Argument accepted by the Scala 2 `style(...)` factory: a [[Dom.Attribute]] or
 * a [[Css]] value. Implicit conversions in the companion wrap either one, so
 * you pass the attribute or the CSS directly.
 */
sealed trait StyleArg

/**
 * Companion of `StyleArg`: its cases and the implicit conversions that build
 * them.
 */
object StyleArg {

  /** Adds an HTML attribute to the `style` element. */
  final case class Attribute(value: Dom.Attribute) extends StyleArg

  /** Adds the rendered [[Css]] to the `style` element body. */
  final case class Source(value: Css) extends StyleArg

  /**
   * Wraps an attribute as a `StyleArg`, so `style(...)` accepts it directly.
   */
  implicit def fromAttribute(attribute: Dom.Attribute): StyleArg = Attribute(attribute)

  /**
   * Wraps a [[Css]] value as a `StyleArg`, so `style(...)` accepts it directly.
   */
  implicit def fromCss(css: Css): StyleArg = Source(css)
}

/**
 * Argument accepted by the Scala 2 `ul(...)` and `ol(...)` factories: either an
 * HTML attribute or a `<li>` item, the only element child these factories
 * accept.
 */
sealed trait ListArg

/**
 * Companion of `ListArg`: its cases and the implicit conversions that build
 * them.
 */
object ListArg {

  /** Adds an HTML attribute to the `ul`/`ol` element. */
  final case class Attribute(value: Dom.Attribute) extends ListArg

  /** Adds a `<li>` item to the `ul`/`ol` element body. */
  final case class Item(value: Dom.Element.Li) extends ListArg

  /**
   * Wraps an attribute as a `ListArg`, so `ul(...)` and `ol(...)` accept it
   * directly.
   */
  implicit def fromAttribute(attribute: Dom.Attribute): ListArg = Attribute(attribute)

  /**
   * Wraps a `<li>` item as a `ListArg`, so `ul(...)` and `ol(...)` accept it
   * directly.
   */
  implicit def fromLi(item: Dom.Element.Li): ListArg = Item(item)
}

/**
 * Argument accepted by the Scala 2 `tr(...)` factory: either an HTML attribute
 * or a `<th>` or `<td>` cell, the only element children this factory accepts.
 */
sealed trait CellArg

/**
 * Companion of `CellArg`: its cases and the implicit conversions that build
 * them.
 */
object CellArg {

  /** Adds an HTML attribute to the `tr` element. */
  final case class Attribute(value: Dom.Attribute) extends CellArg

  /** Adds a `<th>`/`<td>` cell to the `tr` element body. */
  final case class Cell(value: Dom.Element.Cell) extends CellArg

  /** Wraps an attribute as a `CellArg`, so `tr(...)` accepts it directly. */
  implicit def fromAttribute(attribute: Dom.Attribute): CellArg = Attribute(attribute)

  /**
   * Wraps a `<th>` or `<td>` cell as a `CellArg`, so `tr(...)` accepts it
   * directly.
   */
  implicit def fromCell(cell: Dom.Element.Cell): CellArg = Cell(cell)
}

/**
 * Argument accepted by the Scala 2 `table(...)` factory: either an HTML
 * attribute or a `<tr>` row (the typed marker for direct table children).
 */
sealed trait RowArg

/**
 * Companion of `RowArg`: its cases and the implicit conversions that build
 * them.
 */
object RowArg {

  /** Adds an HTML attribute to the `table` element. */
  final case class Attribute(value: Dom.Attribute) extends RowArg

  /** Adds a `<tr>` row to the `table` element body. */
  final case class Row(value: Dom.Element.Tr) extends RowArg

  /** Wraps an attribute as a `RowArg`, so `table(...)` accepts it directly. */
  implicit def fromAttribute(attribute: Dom.Attribute): RowArg = Attribute(attribute)

  /** Wraps a `<tr>` row as a `RowArg`, so `table(...)` accepts it directly. */
  implicit def fromRow(row: Dom.Element.Tr): RowArg = Row(row)
}

/**
 * Argument accepted by the Scala 2 `select(...)` factory: either an HTML
 * attribute or a select child (`<option>`/`<optgroup>`).
 */
sealed trait SelectArg

/**
 * Companion of `SelectArg`: its cases and the implicit conversions that build
 * them.
 */
object SelectArg {

  /** Adds an HTML attribute to the `select` element. */
  final case class Attribute(value: Dom.Attribute) extends SelectArg

  /** Adds an `<option>`/`<optgroup>` child to the `select` element body. */
  final case class Child(value: Dom.Element.SelectChild) extends SelectArg

  /**
   * Wraps an attribute as a `SelectArg`, so `select(...)` accepts it directly.
   */
  implicit def fromAttribute(attribute: Dom.Attribute): SelectArg = Attribute(attribute)

  /**
   * Wraps an `<option>` or `<optgroup>` element as a `SelectArg`, so
   * `select(...)` accepts it directly.
   */
  implicit def fromSelectChild(child: Dom.Element.SelectChild): SelectArg = Child(child)
}

/**
 * Argument accepted by the Scala 2 `optgroup(...)` factory: either an HTML
 * attribute or an `<option>`, the only element child this factory accepts.
 */
sealed trait OptgroupArg

/**
 * Companion of `OptgroupArg`: its cases and the implicit conversions that build
 * them.
 */
object OptgroupArg {

  /** Adds an HTML attribute to the `optgroup` element. */
  final case class Attribute(value: Dom.Attribute) extends OptgroupArg

  /** Adds an `<option>` to the `optgroup` element body. */
  final case class Option(value: Dom.Element.Opt) extends OptgroupArg

  /**
   * Wraps an attribute as an `OptgroupArg`, so `optgroup(...)` accepts it
   * directly.
   */
  implicit def fromAttribute(attribute: Dom.Attribute): OptgroupArg = Attribute(attribute)

  /**
   * Wraps an `<option>` element as an `OptgroupArg`, so `optgroup(...)` accepts
   * it directly.
   */
  implicit def fromOpt(option: Dom.Element.Opt): OptgroupArg = Option(option)
}

/**
 * The HTML DSL: a value or factory for each HTML tag (`div`, `p`, `a`, `img`,
 * ...), keys for common attributes (`id`, `className`, `href`, `disabled`, ...)
 * and the helpers `attr`, `dataAttr`, `aria` and `multiAttr`. The
 * `zio.blocks.html` package object mixes this trait in, so
 * `import zio.blocks.html._` brings every member into scope.
 *
 * Most tags are [[Dom.Element]] values. Apply one to modifiers to add
 * attributes and children: attributes, strings (escaped when rendered), other
 * nodes, numbers, and `Option` values or collections of them. Void tags (`br`,
 * `hr`, `img`, `input`, `link`, `meta`, ...) are [[Dom.Element.Void]] values
 * and accept attributes only. `script` takes attributes and [[Js]] code, and
 * `style` takes attributes and [[Css]].
 *
 * Some factories check their children against the HTML content model: `ul` and
 * `ol` take `li(...)` items, `tr` takes `th(...)` and `td(...)` cells, `table`
 * takes `tr(...)` rows, `select` takes `option(...)` and `optgroup(...)`, and
 * `optgroup` takes `option(...)`. The check covers the factory call only:
 * applying the result to more modifiers, as in `ul(li("Tea"))(div("Ad"))`,
 * compiles and adds any child.
 *
 * {{{
 * val nav = div(className := "nav", ul(li(a(href := "/", "Home")), li(a(href := "/cart", "Cart (2)"))))
 * nav.render
 * // <div class="nav"><ul><li><a href="/">Home</a></li><li><a href="/cart">Cart (2)</a></li></ul></div>
 * }}}
 *
 * The names are short, so some clash with other imports: `span` with
 * `scala.concurrent.duration._`, `main` with a `def main` method in the same
 * object, and tag values such as `search`, `title`, `select` or `label` with
 * your own helpers of the same name. Import such helpers by name, or qualify
 * the tag (`zio.blocks.html.main`). On Scala 3, `p(...)` is ambiguous when
 * `zio.blocks.schema._` is imported too, which is a known bug.
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#the-html-dsl HTML DSL]]
 */
trait HtmlElements {

  // --- Element constructors ---

  private def elScript(effects: Seq[ScriptArg]): Dom.Element.Script = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    var i            = 0
    while (i < effects.length) {
      effects(i) match {
        case ScriptArg.Attribute(attr) => attrBuilder += attr
        case ScriptArg.Source(js)      => childBuilder += Dom.Text(js.value)
      }
      i += 1
    }
    Dom.Element.Script(attrBuilder.result(), childBuilder.result())
  }

  private def elStyle(effects: Seq[StyleArg]): Dom.Element.Style = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    var i            = 0
    while (i < effects.length) {
      effects(i) match {
        case StyleArg.Attribute(attr) => attrBuilder += attr
        case StyleArg.Source(css)     => childBuilder += Dom.Text(css.render)
      }
      i += 1
    }
    Dom.Element.Style(attrBuilder.result(), childBuilder.result())
  }

  /**
   * Builds a `Generic` element from `ul`/`ol` arguments (attributes and `<li>`
   * items).
   */
  private def elFromListArgs(args: Seq[ListArg], tag: String): Dom.Element = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    val it           = args.iterator
    while (it.hasNext) {
      it.next() match {
        case ListArg.Attribute(attr) => attrBuilder += attr
        case ListArg.Item(item)      => childBuilder += item
      }
    }
    Dom.Element.Generic(tag, attrBuilder.result(), childBuilder.result())
  }

  /** Builds a `TrElement` row from `tr` arguments (attributes and cells). */
  private def trFromCellArgs(args: Seq[CellArg]): Dom.Element.Tr = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    val it           = args.iterator
    while (it.hasNext) {
      it.next() match {
        case CellArg.Attribute(attr) => attrBuilder += attr
        case CellArg.Cell(cell)      => childBuilder += cell
      }
    }
    Dom.Element.TrElement(attrBuilder.result(), childBuilder.result())
  }

  /**
   * Builds a `Generic` `table` element from `table` arguments (attributes and
   * rows).
   */
  private def tableFromRowArgs(args: Seq[RowArg]): Dom.Element = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    val it           = args.iterator
    while (it.hasNext) {
      it.next() match {
        case RowArg.Attribute(attr) => attrBuilder += attr
        case RowArg.Row(row)        => childBuilder += row
      }
    }
    Dom.Element.Generic("table", attrBuilder.result(), childBuilder.result())
  }

  /**
   * Builds a `Generic` `select` element from `select` arguments (attributes and
   * options/optgroups).
   */
  private def selectFromSelectArgs(args: Seq[SelectArg]): Dom.Element = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    val it           = args.iterator
    while (it.hasNext) {
      it.next() match {
        case SelectArg.Attribute(attr) => attrBuilder += attr
        case SelectArg.Child(child)    => childBuilder += child
      }
    }
    Dom.Element.Generic("select", attrBuilder.result(), childBuilder.result())
  }

  /**
   * Builds an `OptgroupElement` from `optgroup` arguments (attributes and
   * options).
   */
  private def groupFromOptgroupArgs(args: Seq[OptgroupArg]): Dom.Element.OptgroupElement = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    val it           = args.iterator
    while (it.hasNext) {
      it.next() match {
        case OptgroupArg.Attribute(attr) => attrBuilder += attr
        case OptgroupArg.Option(opt)     => childBuilder += opt
      }
    }
    Dom.Element.OptgroupElement(attrBuilder.result(), childBuilder.result())
  }

  /** The HTML5 document type declaration, rendered as `<!DOCTYPE html>`. */
  val doctype: Dom.Doctype = Dom.Doctype("html")

  /** The root `<html>` element of a document. */
  val html: Dom.Element = Dom.Element.Generic("html", Chunk.empty, Chunk.empty)

  /**
   * The `<head>` element: metadata about the document, such as its `title` and
   * `meta` tags.
   */
  val head: Dom.Element = Dom.Element.Generic("head", Chunk.empty, Chunk.empty)

  /** The `<body>` element: the visible content of the document. */
  val body: Dom.Element = Dom.Element.Generic("body", Chunk.empty, Chunk.empty)

  /**
   * The `<title>` element: the document title, shown in the browser tab. The
   * attribute `title` is `titleAttr`.
   */
  val title: Dom.Element = Dom.Element.Generic("title", Chunk.empty, Chunk.empty)

  /**
   * The `<div>` element: a generic block container with no meaning of its own.
   */
  val div: Dom.Element = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)

  /**
   * The `<span>` element: a generic inline container with no meaning of its
   * own.
   */
  val span: Dom.Element = Dom.Element.Generic("span", Chunk.empty, Chunk.empty)

  /** The `<p>` element: a paragraph. */
  val p: Dom.Element = Dom.Element.Generic("p", Chunk.empty, Chunk.empty)

  /** The `h1` element: a top-level heading. */
  val h1: Dom.Element = Dom.Element.Generic("h1", Chunk.empty, Chunk.empty)

  /** The `h2` element: a second-level heading. */
  val h2: Dom.Element = Dom.Element.Generic("h2", Chunk.empty, Chunk.empty)

  /** The `h3` element: a third-level heading. */
  val h3: Dom.Element = Dom.Element.Generic("h3", Chunk.empty, Chunk.empty)

  /** The `h4` element: a fourth-level heading. */
  val h4: Dom.Element = Dom.Element.Generic("h4", Chunk.empty, Chunk.empty)

  /** The `h5` element: a fifth-level heading. */
  val h5: Dom.Element = Dom.Element.Generic("h5", Chunk.empty, Chunk.empty)

  /** The `h6` element: a sixth-level heading. */
  val h6: Dom.Element = Dom.Element.Generic("h6", Chunk.empty, Chunk.empty)

  /** The `<a>` element: a hyperlink, usually with an `href`. */
  val a: Dom.Element = Dom.Element.Generic("a", Chunk.empty, Chunk.empty)

  /** The `<abbr>` element: an abbreviation or acronym. */
  val abbr: Dom.Element = Dom.Element.Generic("abbr", Chunk.empty, Chunk.empty)

  /**
   * The `<address>` element: contact information for its nearest article or the
   * page.
   */
  val address: Dom.Element = Dom.Element.Generic("address", Chunk.empty, Chunk.empty)

  /**
   * The `<area>` void element: a clickable region of an image map. It renders
   * as `<area/>`, with no closing tag.
   */
  val area: Dom.Element.Void = Dom.Element.VoidGeneric("area", Chunk.empty)

  /**
   * The `<article>` element: a self-contained piece of content, such as a blog
   * post.
   */
  val article: Dom.Element = Dom.Element.Generic("article", Chunk.empty, Chunk.empty)

  /**
   * The `<aside>` element: content related to the surrounding content but set
   * apart, such as a sidebar.
   */
  val aside: Dom.Element = Dom.Element.Generic("aside", Chunk.empty, Chunk.empty)

  /** The `<audio>` element: embedded sound content. */
  val audio: Dom.Element = Dom.Element.Generic("audio", Chunk.empty, Chunk.empty)

  /**
   * The `<b>` element: text drawn to the reader's attention, usually shown in
   * bold.
   */
  val b: Dom.Element = Dom.Element.Generic("b", Chunk.empty, Chunk.empty)

  /**
   * The `<base>` void element: the base URL that relative URLs in the document
   * resolve against. It renders as `<base/>`, with no closing tag.
   */
  val base: Dom.Element.Void = Dom.Element.VoidGeneric("base", Chunk.empty)

  /**
   * The `<bdi>` element: text isolated from the text direction of its
   * surroundings.
   */
  val bdi: Dom.Element = Dom.Element.Generic("bdi", Chunk.empty, Chunk.empty)

  /** The `<bdo>` element: text whose direction is overridden. */
  val bdo: Dom.Element = Dom.Element.Generic("bdo", Chunk.empty, Chunk.empty)

  /** The `<blockquote>` element: a block quotation from another source. */
  val blockquote: Dom.Element = Dom.Element.Generic("blockquote", Chunk.empty, Chunk.empty)

  /**
   * The `<br>` void element: a line break. It renders as `<br/>`, with no
   * closing tag.
   */
  val br: Dom.Element.Void = Dom.Element.VoidGeneric("br", Chunk.empty)

  /** The `<button>` element: a clickable button. */
  val button: Dom.Element = Dom.Element.Generic("button", Chunk.empty, Chunk.empty)

  /** The `<canvas>` element: a drawing surface for scripts. */
  val canvas: Dom.Element = Dom.Element.Generic("canvas", Chunk.empty, Chunk.empty)

  /** The `<caption>` element: the title of a table. */
  val caption: Dom.Element = Dom.Element.Generic("caption", Chunk.empty, Chunk.empty)

  /** The `<cite>` element: the title of a cited work. */
  val cite: Dom.Element = Dom.Element.Generic("cite", Chunk.empty, Chunk.empty)

  /** The `<code>` element: a fragment of computer code. */
  val code: Dom.Element = Dom.Element.Generic("code", Chunk.empty, Chunk.empty)

  /**
   * The `<col>` void element: a column, or a group of columns, inside a
   * `colgroup`. It renders as `<col/>`, with no closing tag.
   */
  val col: Dom.Element.Void = Dom.Element.VoidGeneric("col", Chunk.empty)

  /** The `<colgroup>` element: a group of table columns. */
  val colgroup: Dom.Element = Dom.Element.Generic("colgroup", Chunk.empty, Chunk.empty)

  /** The `<data>` element: content with a machine-readable `value`. */
  val data: Dom.Element = Dom.Element.Generic("data", Chunk.empty, Chunk.empty)

  /** The `<datalist>` element: a list of suggested values for an input. */
  val datalist: Dom.Element = Dom.Element.Generic("datalist", Chunk.empty, Chunk.empty)

  /**
   * The `<dd>` element: the description of a term in a description list (`dl`).
   */
  val dd: Dom.Element = Dom.Element.Generic("dd", Chunk.empty, Chunk.empty)

  /** The `<del>` element: text that was removed from the document. */
  val del: Dom.Element = Dom.Element.Generic("del", Chunk.empty, Chunk.empty)

  /**
   * The `<details>` element: a disclosure widget that shows or hides its
   * content.
   */
  val details: Dom.Element = Dom.Element.Generic("details", Chunk.empty, Chunk.empty)

  /** The `<dfn>` element: the term being defined. */
  val dfn: Dom.Element = Dom.Element.Generic("dfn", Chunk.empty, Chunk.empty)

  /** The `<dialog>` element: a dialog box or window. */
  val dialog: Dom.Element = Dom.Element.Generic("dialog", Chunk.empty, Chunk.empty)

  /**
   * The `<dl>` element: a description list of terms (`dt`) and descriptions
   * (`dd`).
   */
  val dl: Dom.Element = Dom.Element.Generic("dl", Chunk.empty, Chunk.empty)

  /** The `<dt>` element: a term in a description list (`dl`). */
  val dt: Dom.Element = Dom.Element.Generic("dt", Chunk.empty, Chunk.empty)

  /** The `<em>` element: emphasized text, usually shown in italics. */
  val em: Dom.Element = Dom.Element.Generic("em", Chunk.empty, Chunk.empty)

  /**
   * The `<embed>` void element: an external application or interactive content.
   * It renders as `<embed/>`, with no closing tag.
   */
  val embed: Dom.Element.Void = Dom.Element.VoidGeneric("embed", Chunk.empty)

  /** The `<fieldset>` element: a group of related form controls. */
  val fieldset: Dom.Element = Dom.Element.Generic("fieldset", Chunk.empty, Chunk.empty)

  /**
   * The `<figure>` element: self-contained content, such as an illustration,
   * with an optional `figcaption`.
   */
  val figure: Dom.Element = Dom.Element.Generic("figure", Chunk.empty, Chunk.empty)

  /** The `<figcaption>` element: the caption of a `figure`. */
  val figcaption: Dom.Element = Dom.Element.Generic("figcaption", Chunk.empty, Chunk.empty)

  /** The `<footer>` element: the footer of a page or section. */
  val footer: Dom.Element = Dom.Element.Generic("footer", Chunk.empty, Chunk.empty)

  /**
   * The `<form>` element: a form that collects and submits user input. The
   * attribute `form` is `formAttr`.
   */
  val form: Dom.Element = Dom.Element.Generic("form", Chunk.empty, Chunk.empty)

  /** The `<header>` element: introductory content of a page or section. */
  val header: Dom.Element = Dom.Element.Generic("header", Chunk.empty, Chunk.empty)

  /** The `<hgroup>` element: a heading with its subheading or tagline. */
  val hgroup: Dom.Element = Dom.Element.Generic("hgroup", Chunk.empty, Chunk.empty)

  /**
   * The `<hr>` void element: a thematic break between paragraphs. It renders as
   * `<hr/>`, with no closing tag.
   */
  val hr: Dom.Element.Void = Dom.Element.VoidGeneric("hr", Chunk.empty)

  /**
   * The `<i>` element: text in an alternate voice, usually shown in italics.
   */
  val i: Dom.Element = Dom.Element.Generic("i", Chunk.empty, Chunk.empty)

  /**
   * The `<iframe>` element: a nested browsing context that embeds another page.
   */
  val iframe: Dom.Element = Dom.Element.Generic("iframe", Chunk.empty, Chunk.empty)

  /**
   * The `<img>` void element: an image. It renders as `<img/>`, with no closing
   * tag.
   */
  val img: Dom.Element.Void = Dom.Element.VoidGeneric("img", Chunk.empty)

  /**
   * The `<input>` void element: a form control; its `type` attribute selects
   * the kind. It renders as `<input/>`, with no closing tag.
   */
  val input: Dom.Element.Void = Dom.Element.VoidGeneric("input", Chunk.empty)

  /** The `<ins>` element: text that was added to the document. */
  val ins: Dom.Element = Dom.Element.Generic("ins", Chunk.empty, Chunk.empty)

  /** The `<kbd>` element: keyboard input. */
  val kbd: Dom.Element = Dom.Element.Generic("kbd", Chunk.empty, Chunk.empty)

  /**
   * The `<label>` element: a caption for a form control. The attribute `label`
   * is `labelAttr`.
   */
  val label: Dom.Element = Dom.Element.Generic("label", Chunk.empty, Chunk.empty)

  /** The `<legend>` element: the caption of a `fieldset`. */
  val legend: Dom.Element = Dom.Element.Generic("legend", Chunk.empty, Chunk.empty)

  /**
   * The `<link>` void element: a relationship to an external resource, such as
   * a stylesheet. It renders as `<link/>`, with no closing tag.
   */
  val link: Dom.Element.Void = Dom.Element.VoidGeneric("link", Chunk.empty)

  /** The `<main>` element: the main content of the page. */
  val main: Dom.Element = Dom.Element.Generic("main", Chunk.empty, Chunk.empty)

  /** The `<menu>` element: a list of commands. */
  val menu: Dom.Element = Dom.Element.Generic("menu", Chunk.empty, Chunk.empty)

  /** The `<map>` element: an image map, which holds `area` elements. */
  val map: Dom.Element = Dom.Element.Generic("map", Chunk.empty, Chunk.empty)

  /** The `<mark>` element: highlighted text. */
  val mark: Dom.Element = Dom.Element.Generic("mark", Chunk.empty, Chunk.empty)

  /** The `<math>` element: the root of a MathML formula. */
  val math: Dom.Element = Dom.Element.Generic("math", Chunk.empty, Chunk.empty)

  /**
   * The `<meta>` void element: metadata that no other element covers, such as
   * the character set. It renders as `<meta/>`, with no closing tag.
   */
  val meta: Dom.Element.Void = Dom.Element.VoidGeneric("meta", Chunk.empty)

  /** The `<meter>` element: a scalar measurement within a known range. */
  val meter: Dom.Element = Dom.Element.Generic("meter", Chunk.empty, Chunk.empty)

  /** The `<nav>` element: a section of navigation links. */
  val nav: Dom.Element = Dom.Element.Generic("nav", Chunk.empty, Chunk.empty)

  /**
   * The `<noscript>` element: content shown when scripting is off or
   * unsupported.
   */
  val noscript: Dom.Element = Dom.Element.Generic("noscript", Chunk.empty, Chunk.empty)

  /**
   * The `<object>` element: an external resource such as an image or a nested
   * context. `objectTag` is the same element without backticks.
   */
  val `object`: Dom.Element = Dom.Element.Generic("object", Chunk.empty, Chunk.empty)

  /**
   * The `<object>` element, under a name that needs no backticks. `object` is
   * the same element.
   */
  val objectTag: Dom.Element = Dom.Element.Generic("object", Chunk.empty, Chunk.empty)

  /** The `<output>` element: the result of a calculation or user action. */
  val output: Dom.Element = Dom.Element.Generic("output", Chunk.empty, Chunk.empty)

  /**
   * The `<param>` void element: a parameter for an `object` element. It renders
   * as `<param/>`, with no closing tag.
   */
  val param: Dom.Element.Void = Dom.Element.VoidGeneric("param", Chunk.empty)

  /** The `<picture>` element: an image with alternative sources. */
  val picture: Dom.Element = Dom.Element.Generic("picture", Chunk.empty, Chunk.empty)

  /** The `<pre>` element: preformatted text. */
  val pre: Dom.Element = Dom.Element.Generic("pre", Chunk.empty, Chunk.empty)

  /** The `<progress>` element: the completion progress of a task. */
  val progress: Dom.Element = Dom.Element.Generic("progress", Chunk.empty, Chunk.empty)

  /** The `<q>` element: a short inline quotation. */
  val q: Dom.Element = Dom.Element.Generic("q", Chunk.empty, Chunk.empty)

  /**
   * The `<rp>` element: fallback parentheses for browsers that do not support
   * ruby text.
   */
  val rp: Dom.Element = Dom.Element.Generic("rp", Chunk.empty, Chunk.empty)

  /**
   * The `<rt>` element: the pronunciation or annotation text of a `ruby`
   * element.
   */
  val rt: Dom.Element = Dom.Element.Generic("rt", Chunk.empty, Chunk.empty)

  /**
   * The `<ruby>` element: a ruby annotation, used for East Asian pronunciation.
   */
  val ruby: Dom.Element = Dom.Element.Generic("ruby", Chunk.empty, Chunk.empty)

  /**
   * The `<s>` element: text that is no longer accurate, usually shown struck
   * through.
   */
  val s: Dom.Element = Dom.Element.Generic("s", Chunk.empty, Chunk.empty)

  /** The `<samp>` element: sample output of a program. */
  val samp: Dom.Element = Dom.Element.Generic("samp", Chunk.empty, Chunk.empty)

  /**
   * Creates an empty `script` element. Add code with
   * [[Dom.Element.Script#inlineJs]] or a source URL with
   * [[Dom.Element.Script#externalJs]].
   */
  def script(): Dom.Element.Script = elScript(Seq.empty)

  /**
   * Creates a `script` element from attributes and [[Js]] code. Each [[Js]]
   * value becomes a text child that is not HTML-escaped when rendered;
   * rendering only rewrites `</` as `<\/`, so the code cannot close the element
   * early.
   *
   * A plain `String` does not compile here. Build code with `js"..."`, which
   * quotes and escapes interpolated values, and pass only trusted text to
   * `Js(...)`.
   */
  def script(effect: ScriptArg, effects: ScriptArg*): Dom.Element.Script = elScript(effect +: effects)

  /** The `<search>` element: a section with search or filtering controls. */
  val search: Dom.Element = Dom.Element.Generic("search", Chunk.empty, Chunk.empty)

  /** The `<section>` element: a thematic section of a document. */
  val section: Dom.Element = Dom.Element.Generic("section", Chunk.empty, Chunk.empty)

  /**
   * The `<slot>` element: a placeholder inside a web component, filled by its
   * user.
   */
  val slot: Dom.Element = Dom.Element.Generic("slot", Chunk.empty, Chunk.empty)

  /** The `<small>` element: side comments such as fine print. */
  val small: Dom.Element = Dom.Element.Generic("small", Chunk.empty, Chunk.empty)

  /**
   * The `<source>` void element: an alternative media resource for `audio`,
   * `video` or `picture`. It renders as `<source/>`, with no closing tag.
   */
  val source: Dom.Element.Void = Dom.Element.VoidGeneric("source", Chunk.empty)

  /**
   * The `<strong>` element: text of strong importance, usually shown in bold.
   */
  val strong: Dom.Element = Dom.Element.Generic("strong", Chunk.empty, Chunk.empty)

  /**
   * Creates an empty `style` element. Add a stylesheet with
   * [[Dom.Element.Style#inlineCss]].
   */
  def style(): Dom.Element.Style = elStyle(Seq.empty)

  /**
   * Creates a `style` element from attributes and [[Css]] values. Each [[Css]]
   * value is rendered with `render` and added as a text child that is not
   * HTML-escaped when rendered; rendering only rewrites `</` as `<\/`.
   *
   * A plain `String` does not compile here. `Css.Raw` text is used as written,
   * so pass only trusted CSS to it.
   */
  def style(effect: StyleArg, effects: StyleArg*): Dom.Element.Style = elStyle(effect +: effects)

  /** The `<sub>` element: subscript text. */
  val sub: Dom.Element = Dom.Element.Generic("sub", Chunk.empty, Chunk.empty)

  /**
   * The `<summary>` element: the visible heading of a `details` element. The
   * attribute `summary` is `summaryAttr`.
   */
  val summary: Dom.Element = Dom.Element.Generic("summary", Chunk.empty, Chunk.empty)

  /** The `<sup>` element: superscript text. */
  val sup: Dom.Element = Dom.Element.Generic("sup", Chunk.empty, Chunk.empty)

  /** The `<svg>` element: the root of an inline SVG graphic. */
  val svg: Dom.Element = Dom.Element.Generic("svg", Chunk.empty, Chunk.empty)

  /** The `<tbody>` element: the body rows of a table. */
  val tbody: Dom.Element = Dom.Element.Generic("tbody", Chunk.empty, Chunk.empty)

  /**
   * The `<template>` element: markup that the page does not render until a
   * script uses it. `templateTag` is the same element without backticks.
   */
  val `template`: Dom.Element = Dom.Element.Generic("template", Chunk.empty, Chunk.empty)

  /**
   * The `<template>` element, under a name that needs no backticks. `template`
   * is the same element.
   */
  val templateTag: Dom.Element = Dom.Element.Generic("template", Chunk.empty, Chunk.empty)

  /** The `<textarea>` element: a multi-line text input. */
  val textarea: Dom.Element = Dom.Element.Generic("textarea", Chunk.empty, Chunk.empty)

  /** The `<tfoot>` element: the footer rows of a table. */
  val tfoot: Dom.Element = Dom.Element.Generic("tfoot", Chunk.empty, Chunk.empty)

  /** The `<thead>` element: the header rows of a table. */
  val thead: Dom.Element = Dom.Element.Generic("thead", Chunk.empty, Chunk.empty)

  /** The `<time>` element: a date, a time or a duration. */
  val time: Dom.Element = Dom.Element.Generic("time", Chunk.empty, Chunk.empty)

  /**
   * The `<track>` void element: a timed text track, such as subtitles, for
   * `audio` or `video`. It renders as `<track/>`, with no closing tag.
   */
  val track: Dom.Element.Void = Dom.Element.VoidGeneric("track", Chunk.empty)

  /**
   * The `<u>` element: text with an unarticulated annotation, usually shown
   * underlined.
   */
  val u: Dom.Element = Dom.Element.Generic("u", Chunk.empty, Chunk.empty)

  /**
   * The `<var>` element: a variable in a mathematical expression or program.
   * `varTag` is the same element without backticks.
   */
  val `var`: Dom.Element = Dom.Element.Generic("var", Chunk.empty, Chunk.empty)

  /**
   * The `<var>` element, under a name that needs no backticks. `var` is the
   * same element.
   */
  val varTag: Dom.Element = Dom.Element.Generic("var", Chunk.empty, Chunk.empty)

  /** The `<video>` element: embedded video content. */
  val video: Dom.Element = Dom.Element.Generic("video", Chunk.empty, Chunk.empty)

  /**
   * The `<wbr>` void element: a position where the browser may break a long
   * word across lines. It renders as `<wbr/>`, with no closing tag.
   */
  val wbr: Dom.Element.Void = Dom.Element.VoidGeneric("wbr", Chunk.empty)

  /**
   * Creates an empty element with the tag `tag`, for a tag that has no
   * predefined member, such as a custom element.
   */
  def element(tag: String): Dom.Element = Dom.Element.Generic(tag, Chunk.empty, Chunk.empty)

  /** Creates an empty `<li>` element, typed [[Dom.Element.Li]]. */
  def li(): Dom.Element.Li = Dom.Element.LiElement(Chunk.empty, Chunk.empty)

  /**
   * Creates a `<li>` element from attributes and children, typed
   * [[Dom.Element.Li]] so that `ul(...)` and `ol(...)` accept it. Any modifier
   * is accepted.
   */
  def li(effect: DomModifier, effects: DomModifier*): Dom.Element.Li =
    Dom.Element.LiElement(Chunk.empty, Chunk.empty)(effect, effects: _*)

  /** Creates an empty `<ul>` element. */
  def ul(): Dom.Element = Dom.Element.Generic("ul", Chunk.empty, Chunk.empty)

  /**
   * Creates a `<ul>` element from attributes and `<li>` items. Child arguments
   * must be `li(...)` values ([[Dom.Element.Li]]); any other element does not
   * compile.
   */
  def ul(effect: ListArg, effects: ListArg*): Dom.Element =
    elFromListArgs(effect +: effects, "ul")

  /** Creates a `<ul>` element from a collection of `<li>` items, in order. */
  def ul(children: Iterable[Dom.Element.Li]): Dom.Element =
    Dom.Element.Generic("ul", Chunk.empty, Chunk.from(children))

  /** Creates an empty `<ol>` element. */
  def ol(): Dom.Element = Dom.Element.Generic("ol", Chunk.empty, Chunk.empty)

  /**
   * Creates an `<ol>` element from attributes and `<li>` items. Child arguments
   * must be `li(...)` values ([[Dom.Element.Li]]); any other element does not
   * compile.
   */
  def ol(effect: ListArg, effects: ListArg*): Dom.Element =
    elFromListArgs(effect +: effects, "ol")

  /** Creates an `<ol>` element from a collection of `<li>` items, in order. */
  def ol(children: Iterable[Dom.Element.Li]): Dom.Element =
    Dom.Element.Generic("ol", Chunk.empty, Chunk.from(children))

  /** Creates an empty `<th>` header cell, typed [[Dom.Element.Th]]. */
  def th(): Dom.Element.Th = Dom.Element.ThElement(Chunk.empty, Chunk.empty)

  /**
   * Creates a `<th>` header cell from attributes and children, typed
   * [[Dom.Element.Th]] so that `tr(...)` accepts it. Any modifier is accepted.
   */
  def th(effect: DomModifier, effects: DomModifier*): Dom.Element.Th =
    Dom.Element.ThElement(Chunk.empty, Chunk.empty)(effect, effects: _*)

  /** Creates an empty `<td>` data cell, typed [[Dom.Element.Td]]. */
  def td(): Dom.Element.Td = Dom.Element.TdElement(Chunk.empty, Chunk.empty)

  /**
   * Creates a `<td>` data cell from attributes and children, typed
   * [[Dom.Element.Td]] so that `tr(...)` accepts it. Any modifier is accepted.
   */
  def td(effect: DomModifier, effects: DomModifier*): Dom.Element.Td =
    Dom.Element.TdElement(Chunk.empty, Chunk.empty)(effect, effects: _*)

  /** Creates an empty `<tr>` row, typed [[Dom.Element.Tr]]. */
  def tr(): Dom.Element.Tr = Dom.Element.TrElement(Chunk.empty, Chunk.empty)

  /**
   * Creates a `<tr>` row from attributes and table cells, typed
   * [[Dom.Element.Tr]] so that `table(...)` accepts it. Child arguments must be
   * `th(...)` or `td(...)` values ([[Dom.Element.Cell]]); any other element
   * does not compile.
   */
  def tr(effect: CellArg, effects: CellArg*): Dom.Element.Tr =
    trFromCellArgs(effect +: effects)

  /**
   * Creates a `<tr>` row from a collection of `<th>` and `<td>` cells, in
   * order.
   */
  def tr(children: Iterable[Dom.Element.Cell]): Dom.Element.Tr =
    Dom.Element.TrElement(Chunk.empty, Chunk.from(children))

  /** Creates an empty `<table>` element. */
  def table(): Dom.Element = Dom.Element.Generic("table", Chunk.empty, Chunk.empty)

  /**
   * Creates a `<table>` element from attributes and `<tr>` rows. Child
   * arguments must be `tr(...)` values ([[Dom.Element.Tr]]); any other element
   * does not compile. The other table parts (`caption`, `colgroup`, `thead`,
   * `tbody`, `tfoot`) have no typed marker, so build a table that needs them
   * with `element("table")(...)` or the `html"..."` interpolator.
   */
  def table(effect: RowArg, effects: RowArg*): Dom.Element =
    tableFromRowArgs(effect +: effects)

  /** Creates a `<table>` element from a collection of `<tr>` rows, in order. */
  def table(children: Iterable[Dom.Element.Tr]): Dom.Element =
    Dom.Element.Generic("table", Chunk.empty, Chunk.from(children))

  /**
   * Creates an empty `<option>` element, typed [[Dom.Element.Opt]]. On Scala 3,
   * `opt()` does the same.
   */
  def option(): Dom.Element.Opt = Dom.Element.OptElement(Chunk.empty, Chunk.empty)

  /**
   * Creates an `<option>` element from attributes and children, typed
   * [[Dom.Element.Opt]] so that `select(...)` and `optgroup(...)` accept it.
   * Any modifier is accepted. On Scala 3, `opt(...)` does the same.
   */
  def option(effect: DomModifier, effects: DomModifier*): Dom.Element.Opt =
    Dom.Element.OptElement(Chunk.empty, Chunk.empty)(effect, effects: _*)

  /** Creates an empty `<optgroup>` element, typed [[Dom.Element.Optgroup]]. */
  def optgroup(): Dom.Element.Optgroup = Dom.Element.OptgroupElement(Chunk.empty, Chunk.empty)

  /**
   * Creates an `<optgroup>` element from attributes and `<option>` children,
   * typed [[Dom.Element.Optgroup]] so that `select(...)` accepts it. Child
   * arguments must be `option(...)` values ([[Dom.Element.Opt]]); any other
   * element does not compile.
   */
  def optgroup(effect: OptgroupArg, effects: OptgroupArg*): Dom.Element.Optgroup =
    groupFromOptgroupArgs(effect +: effects)

  /**
   * Creates an `<optgroup>` element from a collection of `<option>` children,
   * in order.
   */
  def optgroup(children: Iterable[Dom.Element.Opt]): Dom.Element.Optgroup =
    Dom.Element.OptgroupElement(Chunk.empty, Chunk.from(children))

  /** Creates an empty `<select>` element. */
  def select(): Dom.Element = Dom.Element.Generic("select", Chunk.empty, Chunk.empty)

  /**
   * Creates a `<select>` element from attributes and `<option>` or `<optgroup>`
   * children. Child arguments must be `option(...)` or `optgroup(...)` values
   * ([[Dom.Element.SelectChild]]); any other element does not compile.
   */
  def select(effect: SelectArg, effects: SelectArg*): Dom.Element =
    selectFromSelectArgs(effect +: effects)

  /**
   * Creates a `<select>` element from a collection of `<option>` and
   * `<optgroup>` children, in order.
   */
  def select(children: Iterable[Dom.Element.SelectChild]): Dom.Element =
    Dom.Element.Generic("select", Chunk.empty, Chunk.from(children))

  // --- Attribute helpers ---

  /** The `id` attribute: the unique identifier of the element. */
  val id: AttributeKey = new AttributeKey("id")

  /**
   * The `class` attribute: the CSS classes of the element. Several values join
   * with spaces, as in `class := ("a", "b")`. `className` is the same key.
   */
  val `class`: MultiAttributeKey = new MultiAttributeKey("class", Dom.AttributeSeparator.Space)

  /**
   * The `class` attribute (`className` in Scala): the CSS classes of the
   * element. Several values join with spaces, as in `className := ("a", "b")`.
   */
  val className: MultiAttributeKey = new MultiAttributeKey("class", Dom.AttributeSeparator.Space)

  /**
   * The `style` attribute (`styleAttr` in Scala): inline CSS declarations for
   * the element.
   */
  val styleAttr: AttributeKey = new AttributeKey("style")

  /**
   * The `title` attribute (`titleAttr` in Scala): advisory text about the
   * element, often shown as a tooltip.
   */
  val titleAttr: AttributeKey = new AttributeKey("title")

  /** The `href` attribute: the URL a link or resource points to. */
  val href: AttributeKey = new AttributeKey("href")

  /**
   * The `src` attribute: the URL of an embedded resource such as an image,
   * script or frame.
   */
  val src: AttributeKey = new AttributeKey("src")

  /**
   * The `alt` attribute: the text that replaces an image when it cannot be
   * shown.
   */
  val alt: AttributeKey = new AttributeKey("alt")

  /**
   * The `width` attribute: the width of the element, for example of an image or
   * a canvas.
   */
  val width: AttributeKey = new AttributeKey("width")

  /**
   * The `height` attribute: the height of the element, for example of an image
   * or a canvas.
   */
  val height: AttributeKey = new AttributeKey("height")

  /** The `action` attribute: the URL a form submits to. */
  val action: AttributeKey = new AttributeKey("action")

  /**
   * The `method` attribute: the HTTP method a form submits with, such as `get`
   * or `post`.
   */
  val method: AttributeKey = new AttributeKey("method")

  /**
   * The `name` attribute: the name of the element, for example of a form
   * control or a `meta` entry.
   */
  val name: AttributeKey = new AttributeKey("name")

  /**
   * The `value` attribute: the value of the element, for example of an input or
   * an option.
   */
  val value: AttributeKey = new AttributeKey("value")

  /**
   * The `type` attribute: the type of the element, for example the kind of an
   * input or the media type of a script. `typeAttr` is the same key.
   */
  val `type`: AttributeKey = new AttributeKey("type")

  /**
   * The `type` attribute (`typeAttr` in Scala): the type of the element, for
   * example the kind of an input. It is the same key as `type`, under a name
   * that needs no backticks.
   */
  val typeAttr: AttributeKey = new AttributeKey("type")

  /** The `placeholder` attribute: a hint shown in an empty input. */
  val placeholder: AttributeKey = new AttributeKey("placeholder")

  /**
   * The `required` boolean attribute: marks an input as required before a form
   * submits. It renders as the bare name.
   */
  val required: Dom.Attribute.BooleanAttribute = Dom.boolAttr("required")

  /**
   * The `disabled` boolean attribute: disables a form control. It renders as
   * the bare name.
   */
  val disabled: Dom.Attribute.BooleanAttribute = Dom.boolAttr("disabled")

  /**
   * The `readonly` boolean attribute: makes an input read-only. It renders as
   * the bare name.
   */
  val readonly: Dom.Attribute.BooleanAttribute = Dom.boolAttr("readonly")

  /**
   * The `checked` boolean attribute: checks a checkbox or radio button. It
   * renders as the bare name.
   */
  val checked: Dom.Attribute.BooleanAttribute = Dom.boolAttr("checked")

  /**
   * The `selected` boolean attribute: preselects an `option`. It renders as the
   * bare name.
   */
  val selected: Dom.Attribute.BooleanAttribute = Dom.boolAttr("selected")

  /**
   * The `multiple` boolean attribute: lets an input or a `select` take more
   * than one value. It renders as the bare name.
   */
  val multiple: Dom.Attribute.BooleanAttribute = Dom.boolAttr("multiple")

  /** The `min` attribute: the smallest value an input accepts. */
  val min: AttributeKey = new AttributeKey("min")

  /** The `max` attribute: the largest value an input accepts. */
  val max: AttributeKey = new AttributeKey("max")

  /** The `step` attribute: the step between the values an input accepts. */
  val step: AttributeKey = new AttributeKey("step")

  /**
   * The `pattern` attribute: a regular expression the value of an input has to
   * match.
   */
  val pattern: AttributeKey = new AttributeKey("pattern")

  /**
   * The `autofocus` boolean attribute: focuses the element when the page loads.
   * It renders as the bare name.
   */
  val autofocus: Dom.Attribute.BooleanAttribute = Dom.boolAttr("autofocus")

  /**
   * The `autocomplete` attribute (`autoComplete` in Scala): whether the browser
   * may fill an input automatically.
   */
  val autoComplete: AttributeKey = new AttributeKey("autocomplete")

  /**
   * The `target` attribute: where a link or a form opens its result, such as
   * `_blank`.
   */
  val target: AttributeKey = new AttributeKey("target")

  /**
   * The `rel` attribute: the relationship between the document and the linked
   * resource. Several values join with spaces.
   */
  val rel: MultiAttributeKey = new MultiAttributeKey("rel", Dom.AttributeSeparator.Space)

  /**
   * The `download` attribute: marks a link as a download, optionally with the
   * file name.
   */
  val download: AttributeKey = new AttributeKey("download")

  /** The `role` attribute: the ARIA role of the element. */
  val role: AttributeKey = new AttributeKey("role")

  /**
   * The `tabindex` attribute (`tabIndex` in Scala): the position of the element
   * in the keyboard focus order.
   */
  val tabIndex: AttributeKey = new AttributeKey("tabindex")

  /**
   * The `hidden` boolean attribute: hides the element. It renders as the bare
   * name.
   */
  val hidden: Dom.Attribute.BooleanAttribute = Dom.boolAttr("hidden")

  /**
   * The `draggable` attribute: whether the element can be dragged, as `true` or
   * `false`.
   */
  val draggable: AttributeKey = new AttributeKey("draggable")

  /**
   * The `contenteditable` attribute (`contentEditable` in Scala): whether the
   * user can edit the content of the element.
   */
  val contentEditable: AttributeKey = new AttributeKey("contenteditable")

  /**
   * The `lang` attribute: the language of the element content, such as `en`.
   */
  val lang: AttributeKey = new AttributeKey("lang")

  /**
   * The `dir` attribute: the text direction of the element content, such as
   * `ltr` or `rtl`.
   */
  val dir: AttributeKey = new AttributeKey("dir")

  /** The `colspan` attribute: the number of columns a table cell spans. */
  val colspan: AttributeKey = new AttributeKey("colspan")

  /** The `rowspan` attribute: the number of rows a table cell spans. */
  val rowspan: AttributeKey = new AttributeKey("rowspan")

  /**
   * The `scope` attribute: which cells a table header cell applies to, such as
   * `col` or `row`. `scopeAttr` is the same key.
   */
  val `scope`: AttributeKey = new AttributeKey("scope")

  /**
   * The `scope` attribute (`scopeAttr` in Scala): which cells a table header
   * cell applies to, such as `col` or `row`. It is the same key as `scope`.
   */
  val scopeAttr: AttributeKey = new AttributeKey("scope")

  /**
   * The `headers` attribute: the ids of the header cells that apply to a table
   * cell.
   */
  val headers: AttributeKey = new AttributeKey("headers")

  /**
   * The `for` attribute: the id of the form control a `label` belongs to.
   * `forAttr` is the same key.
   */
  val `for`: AttributeKey = new AttributeKey("for")

  /**
   * The `for` attribute (`forAttr` in Scala): the id of the form control a
   * `label` belongs to. It is the same key as `for`, under a name that needs no
   * backticks.
   */
  val forAttr: AttributeKey = new AttributeKey("for")

  /**
   * The `enctype` attribute (`encType` in Scala): the encoding a form uses to
   * submit its data.
   */
  val encType: AttributeKey = new AttributeKey("enctype")

  /**
   * The `formaction` attribute (`formAction` in Scala): the URL a submit button
   * sends its form to, overriding the form `action`.
   */
  val formAction: AttributeKey = new AttributeKey("formaction")

  /**
   * The `formmethod` attribute (`formMethod` in Scala): the HTTP method a
   * submit button uses, overriding the form `method`.
   */
  val formMethod: AttributeKey = new AttributeKey("formmethod")

  /**
   * The `loading` attribute: when the browser loads an image or frame, as
   * `lazy` or `eager`.
   */
  val loading: AttributeKey = new AttributeKey("loading")

  /**
   * The `srcset` attribute (`srcSet` in Scala): a list of image sources for
   * different display conditions.
   */
  val srcSet: AttributeKey = new AttributeKey("srcset")

  /**
   * The `sizes` attribute: the display sizes that go with the sources in
   * `srcSet`.
   */
  val sizes: AttributeKey = new AttributeKey("sizes")

  /**
   * The `minlength` attribute (`minLength` in Scala): the minimum number of
   * characters an input accepts.
   */
  val minLength: AttributeKey = new AttributeKey("minlength")

  /**
   * The `maxlength` attribute (`maxLength` in Scala): the maximum number of
   * characters an input accepts.
   */
  val maxLength: AttributeKey = new AttributeKey("maxlength")

  /**
   * The `size` attribute: the visible width of an input in characters, or the
   * number of visible rows of a `select`.
   */
  val size: AttributeKey = new AttributeKey("size")

  /** The `cols` attribute: the visible width of a `textarea` in characters. */
  val cols: AttributeKey = new AttributeKey("cols")

  /** The `rows` attribute: the number of visible text lines of a `textarea`. */
  val rows: AttributeKey = new AttributeKey("rows")

  /** The `wrap` attribute: how a `textarea` wraps its text on submission. */
  val wrap: AttributeKey = new AttributeKey("wrap")

  /** The `accept` attribute: the file types an input accepts. */
  val accept: AttributeKey = new AttributeKey("accept")

  /**
   * The `blocking` attribute: the operations that the element blocks until it
   * is fetched.
   */
  val blocking: AttributeKey = new AttributeKey("blocking")

  /**
   * The `enterkeyhint` attribute (`enterKeyHint` in Scala): the label or icon
   * of the enter key on virtual keyboards.
   */
  val enterKeyHint: AttributeKey = new AttributeKey("enterkeyhint")

  /**
   * The `exportparts` attribute (`exportParts` in Scala): the shadow parts of a
   * nested element that a web component exposes.
   */
  val exportParts: AttributeKey = new AttributeKey("exportparts")

  /**
   * The `fetchpriority` attribute (`fetchPriority` in Scala): a hint for the
   * fetch priority of a resource, such as `high` or `low`.
   */
  val fetchPriority: AttributeKey = new AttributeKey("fetchpriority")

  /**
   * The `inputmode` attribute (`inputMode` in Scala): a hint for the kind of
   * virtual keyboard to show.
   */
  val inputMode: AttributeKey = new AttributeKey("inputmode")

  /**
   * The `inert` boolean attribute: makes the element and its content
   * unresponsive to user interaction. It renders as the bare name.
   */
  val inert: Dom.Attribute.BooleanAttribute = Dom.boolAttr("inert")

  /**
   * The `itemid` attribute (`itemId` in Scala): the global identifier of a
   * microdata item.
   */
  val itemId: AttributeKey = new AttributeKey("itemid")

  /**
   * The `itemprop` attribute (`itemProp` in Scala): the microdata property that
   * the element holds.
   */
  val itemProp: AttributeKey = new AttributeKey("itemprop")

  /**
   * The `itemref` attribute (`itemRef` in Scala): the ids of elements that add
   * properties to a microdata item.
   */
  val itemRef: AttributeKey = new AttributeKey("itemref")

  /**
   * The `itemscope` boolean attribute (`itemScope` in Scala): marks the element
   * as a microdata item. It renders as the bare name.
   */
  val itemScope: Dom.Attribute.BooleanAttribute = Dom.boolAttr("itemscope")

  /**
   * The `itemtype` attribute (`itemType` in Scala): the vocabulary URL that
   * describes a microdata item.
   */
  val itemType: AttributeKey = new AttributeKey("itemtype")

  /**
   * The `nonce` attribute: a one-time token that lets the element pass a
   * content security policy.
   */
  val nonce: AttributeKey = new AttributeKey("nonce")

  /**
   * The `part` attribute: the name of the element as a shadow part, for styling
   * from outside.
   */
  val part: AttributeKey = new AttributeKey("part")

  /** The `popover` attribute: turns the element into a popover. */
  val popover: AttributeKey = new AttributeKey("popover")

  /**
   * The `popovertarget` attribute (`popoverTarget` in Scala): the id of the
   * popover that a button controls.
   */
  val popoverTarget: AttributeKey = new AttributeKey("popovertarget")

  /**
   * The `popovertargetaction` attribute (`popoverTargetAction` in Scala): what
   * a button does to its popover: `show`, `hide` or `toggle`.
   */
  val popoverTargetAction: AttributeKey = new AttributeKey("popovertargetaction")

  /**
   * The `writingsuggestions` attribute (`writingSuggestions` in Scala): whether
   * the browser may offer writing suggestions in the element.
   */
  val writingSuggestions: AttributeKey = new AttributeKey("writingsuggestions")

  /**
   * The `accesskey` attribute: a keyboard shortcut that activates or focuses
   * the element.
   */
  val accesskey: AttributeKey = new AttributeKey("accesskey")

  /**
   * The `async` boolean attribute: runs a script as soon as it loads, without
   * blocking the page. It renders as the bare name.
   */
  val async: Dom.Attribute.BooleanAttribute = Dom.boolAttr("async")

  /**
   * The `autoplay` boolean attribute: starts media playback as soon as it can.
   * It renders as the bare name.
   */
  val autoplay: Dom.Attribute.BooleanAttribute = Dom.boolAttr("autoplay")

  /**
   * The `charset` attribute: the character encoding of the document or of a
   * script.
   */
  val charset: AttributeKey = new AttributeKey("charset")

  /** The `content` attribute: the value of a `meta` element. */
  val content: AttributeKey = new AttributeKey("content")

  /**
   * The `controls` boolean attribute: shows the playback controls of `audio` or
   * `video`. It renders as the bare name.
   */
  val controls: Dom.Attribute.BooleanAttribute = Dom.boolAttr("controls")

  /**
   * The `crossorigin` attribute: how the element makes cross-origin requests,
   * such as `anonymous`.
   */
  val crossorigin: AttributeKey = new AttributeKey("crossorigin")

  /**
   * The `datetime` attribute: the date, time or duration that a `time`, `ins`
   * or `del` element represents.
   */
  val datetime: AttributeKey = new AttributeKey("datetime")

  /**
   * The `defer` boolean attribute: runs a script after the page is parsed. It
   * renders as the bare name.
   */
  val defer: Dom.Attribute.BooleanAttribute = Dom.boolAttr("defer")

  /**
   * The `form` attribute (`formAttr` in Scala): the id of the form that a
   * control belongs to.
   */
  val formAttr: AttributeKey = new AttributeKey("form")

  /**
   * The `formnovalidate` boolean attribute (`formNoValidate` in Scala): submits
   * the form without validation when a button carries it. It renders as the
   * bare name.
   */
  val formNoValidate: Dom.Attribute.BooleanAttribute = Dom.boolAttr("formnovalidate")

  /** The `high` attribute: the lower bound of the high range of a `meter`. */
  val high: AttributeKey = new AttributeKey("high")

  /**
   * The `http-equiv` attribute (`httpEquiv` in Scala): the HTTP header that a
   * `meta` element stands in for.
   */
  val httpEquiv: AttributeKey = new AttributeKey("http-equiv")

  /**
   * The `integrity` attribute: the hash a fetched script or stylesheet has to
   * match.
   */
  val integrity: AttributeKey = new AttributeKey("integrity")

  /**
   * The `label` attribute (`labelAttr` in Scala): the visible label of an
   * `option`, `optgroup` or `track`.
   */
  val labelAttr: AttributeKey = new AttributeKey("label")

  /**
   * The `list` attribute: the id of the `datalist` that suggests values for an
   * input.
   */
  val list: AttributeKey = new AttributeKey("list")

  /**
   * The `loop` boolean attribute: restarts media playback at the end. It
   * renders as the bare name.
   */
  val loop: Dom.Attribute.BooleanAttribute = Dom.boolAttr("loop")

  /** The `low` attribute: the upper bound of the low range of a `meter`. */
  val low: AttributeKey = new AttributeKey("low")

  /** The `media` attribute: the media query a resource applies to. */
  val media: AttributeKey = new AttributeKey("media")

  /**
   * The `muted` boolean attribute: mutes the audio of a media element. It
   * renders as the bare name.
   */
  val muted: Dom.Attribute.BooleanAttribute = Dom.boolAttr("muted")

  /**
   * The `novalidate` boolean attribute (`noValidate` in Scala): submits the
   * form without validating it. It renders as the bare name.
   */
  val noValidate: Dom.Attribute.BooleanAttribute = Dom.boolAttr("novalidate")

  /**
   * The `open` boolean attribute: shows the content of a `details` or `dialog`
   * element. It renders as the bare name.
   */
  val open: Dom.Attribute.BooleanAttribute = Dom.boolAttr("open")

  /** The `optimum` attribute: the optimum value of a `meter`. */
  val optimum: AttributeKey = new AttributeKey("optimum")

  /** The `poster` attribute: the image shown for a `video` before it plays. */
  val poster: AttributeKey = new AttributeKey("poster")

  /**
   * The `preload` attribute: how much of a media resource the browser loads up
   * front.
   */
  val preload: AttributeKey = new AttributeKey("preload")

  /** The `referrerpolicy` attribute: which referrer a request sends. */
  val referrerpolicy: AttributeKey = new AttributeKey("referrerpolicy")

  /**
   * The `reversed` boolean attribute: numbers the items of an `ol` in
   * descending order. It renders as the bare name.
   */
  val reversed: Dom.Attribute.BooleanAttribute = Dom.boolAttr("reversed")

  /**
   * The `sandbox` attribute: restrictions that apply to the content of an
   * `iframe`.
   */
  val sandbox: AttributeKey = new AttributeKey("sandbox")

  /**
   * The `span` attribute (`spanAttr` in Scala): the number of columns that a
   * `col` or `colgroup` spans.
   */
  val spanAttr: AttributeKey = new AttributeKey("span")

  /**
   * The `spellcheck` attribute: whether the browser checks the spelling of the
   * element content.
   */
  val spellcheck: AttributeKey = new AttributeKey("spellcheck")

  /**
   * The `summary` attribute (`summaryAttr` in Scala): a summary of the content
   * of a table.
   */
  val summaryAttr: AttributeKey = new AttributeKey("summary")

  /**
   * The `translate` attribute: whether the content of the element is translated
   * with the page.
   */
  val translate: AttributeKey = new AttributeKey("translate")

  /**
   * The `cite` attribute (`citeAttr` in Scala): the URL of the source of a
   * quotation or change.
   */
  val citeAttr: AttributeKey = new AttributeKey("cite")

  /**
   * The `slot` attribute (`slotAttr` in Scala): the name of the web component
   * slot that the element fills.
   */
  val slotAttr: AttributeKey = new AttributeKey("slot")

  /** The `xmlns` attribute: the XML namespace of the element. */
  val xmlns: AttributeKey = new AttributeKey("xmlns")

  // --- Multi-value attribute helpers ---

  /**
   * The `aria-describedby` attribute (`ariaDescribedby` in Scala): the ids of
   * the elements that describe this one, for assistive technology. Several
   * values join with spaces.
   */
  val ariaDescribedby: MultiAttributeKey =
    new MultiAttributeKey("aria-describedby", Dom.AttributeSeparator.Space)

  /**
   * The `aria-labelledby` attribute (`ariaLabelledby` in Scala): the ids of the
   * elements that label this one, for assistive technology. Several values join
   * with spaces.
   */
  val ariaLabelledby: MultiAttributeKey = new MultiAttributeKey("aria-labelledby", Dom.AttributeSeparator.Space)

  /**
   * Returns a key for the multi-valued attribute `name`; its values join with
   * spaces.
   */
  def multiAttr(name: String): MultiAttributeKey = Dom.multiAttr(name)

  /**
   * Returns a key for the multi-valued attribute `name`; its values join with
   * `separator`.
   */
  def multiAttr(name: String, separator: Dom.AttributeSeparator): MultiAttributeKey = Dom.multiAttr(name, separator)

  /** Creates the attribute `name` with `values` joined by spaces. */
  def multiAttr(name: String, values: Iterable[String]): Dom.Attribute = Dom.multiAttr(name, values)

  /**
   * Creates the attribute `name` with the given values joined by `separator`.
   */
  def multiAttr(name: String, separator: Dom.AttributeSeparator, value: String, rest: String*): Dom.Attribute =
    Dom.multiAttr(name, separator, value, rest: _*)

  // --- DOM helper functions ---

  /** A [[Dom]] that renders nothing, for use as a placeholder child. */
  val empty: Dom = Dom.Empty

  /**
   * Returns the key for the `aria-name` attribute: `aria("label")` renders as
   * `aria-label`.
   */
  def aria(name: String): AttributeKey = new AttributeKey("aria-" + name)

  /**
   * Returns the key for the `data-name` attribute: `dataAttr("id")` renders as
   * `data-id`.
   */
  def dataAttr(name: String): AttributeKey = new AttributeKey("data-" + name)

  /**
   * Returns the key for the attribute `name`, for an attribute that has no
   * predefined member.
   */
  def attr(name: String): AttributeKey = new AttributeKey(name)
}
