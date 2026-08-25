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

/** Argument accepted by the Scala 2 `script(...)` element constructor. */
sealed trait ScriptArg
object ScriptArg {

  /** Adds an HTML attribute to the `script` element. */
  final case class Attribute(value: Dom.Attribute) extends ScriptArg

  /** Adds typed inline JavaScript to the `script` element body. */
  final case class Source(value: Js) extends ScriptArg

  implicit def fromAttribute(attribute: Dom.Attribute): ScriptArg = Attribute(attribute)
  implicit def fromJs(js: Js): ScriptArg                          = Source(js)
}

/** Argument accepted by the Scala 2 `style(...)` element constructor. */
sealed trait StyleArg
object StyleArg {

  /** Adds an HTML attribute to the `style` element. */
  final case class Attribute(value: Dom.Attribute) extends StyleArg

  /** Adds typed inline CSS to the `style` element body. */
  final case class Source(value: Css) extends StyleArg

  implicit def fromAttribute(attribute: Dom.Attribute): StyleArg = Attribute(attribute)
  implicit def fromCss(css: Css): StyleArg                       = Source(css)
}

/**
 * Argument accepted by the Scala 2 `ul(...)` and `ol(...)` factories: either an
 * HTML attribute or a `<li>` list item (the only permitted element child).
 */
sealed trait ListArg
object ListArg {

  /** Adds an HTML attribute to the `ul`/`ol` element. */
  final case class Attribute(value: Dom.Attribute) extends ListArg

  /** Adds a `<li>` item to the `ul`/`ol` element body. */
  final case class Item(value: Dom.Element.Li) extends ListArg

  implicit def fromAttribute(attribute: Dom.Attribute): ListArg = Attribute(attribute)
  implicit def fromLi(item: Dom.Element.Li): ListArg            = Item(item)
}

/**
 * Argument accepted by the Scala 2 `tr(...)` factory: either an HTML attribute
 * or a table cell (`<th>`/`<td>`, the only permitted children).
 */
sealed trait CellArg
object CellArg {

  /** Adds an HTML attribute to the `tr` element. */
  final case class Attribute(value: Dom.Attribute) extends CellArg

  /** Adds a `<th>`/`<td>` cell to the `tr` element body. */
  final case class Cell(value: Dom.Element.Cell) extends CellArg

  implicit def fromAttribute(attribute: Dom.Attribute): CellArg = Attribute(attribute)
  implicit def fromCell(cell: Dom.Element.Cell): CellArg        = Cell(cell)
}

/**
 * Argument accepted by the Scala 2 `table(...)` factory: either an HTML
 * attribute or a `<tr>` row (the typed marker for direct table children).
 */
sealed trait RowArg
object RowArg {

  /** Adds an HTML attribute to the `table` element. */
  final case class Attribute(value: Dom.Attribute) extends RowArg

  /** Adds a `<tr>` row to the `table` element body. */
  final case class Row(value: Dom.Element.Tr) extends RowArg

  implicit def fromAttribute(attribute: Dom.Attribute): RowArg = Attribute(attribute)
  implicit def fromRow(row: Dom.Element.Tr): RowArg            = Row(row)
}

/**
 * Argument accepted by the Scala 2 `select(...)` factory: either an HTML
 * attribute or a select child (`<option>`/`<optgroup>`).
 */
sealed trait SelectArg
object SelectArg {

  /** Adds an HTML attribute to the `select` element. */
  final case class Attribute(value: Dom.Attribute) extends SelectArg

  /** Adds an `<option>`/`<optgroup>` child to the `select` element body. */
  final case class Child(value: Dom.Element.SelectChild) extends SelectArg

  implicit def fromAttribute(attribute: Dom.Attribute): SelectArg         = Attribute(attribute)
  implicit def fromSelectChild(child: Dom.Element.SelectChild): SelectArg = Child(child)
}

/**
 * Argument accepted by the Scala 2 `optgroup(...)` factory: either an HTML
 * attribute or an `<option>` (the only permitted element child).
 */
sealed trait OptgroupArg
object OptgroupArg {

  /** Adds an HTML attribute to the `optgroup` element. */
  final case class Attribute(value: Dom.Attribute) extends OptgroupArg

  /** Adds an `<option>` to the `optgroup` element body. */
  final case class Option(value: Dom.Element.Opt) extends OptgroupArg

  implicit def fromAttribute(attribute: Dom.Attribute): OptgroupArg = Attribute(attribute)
  implicit def fromOpt(option: Dom.Element.Opt): OptgroupArg        = Option(option)
}

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
    var i            = 0
    while (i < args.length) {
      args(i) match {
        case ListArg.Attribute(attr) => attrBuilder += attr
        case ListArg.Item(item)      => childBuilder += item
      }
      i += 1
    }
    Dom.Element.Generic(tag, attrBuilder.result(), childBuilder.result())
  }

  /** Builds a `TrElement` row from `tr` arguments (attributes and cells). */
  private def trFromCellArgs(args: Seq[CellArg]): Dom.Element.Tr = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    var i            = 0
    while (i < args.length) {
      args(i) match {
        case CellArg.Attribute(attr) => attrBuilder += attr
        case CellArg.Cell(cell)      => childBuilder += cell
      }
      i += 1
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
    var i            = 0
    while (i < args.length) {
      args(i) match {
        case RowArg.Attribute(attr) => attrBuilder += attr
        case RowArg.Row(row)        => childBuilder += row
      }
      i += 1
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
    var i            = 0
    while (i < args.length) {
      args(i) match {
        case SelectArg.Attribute(attr) => attrBuilder += attr
        case SelectArg.Child(child)    => childBuilder += child
      }
      i += 1
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
    var i            = 0
    while (i < args.length) {
      args(i) match {
        case OptgroupArg.Attribute(attr) => attrBuilder += attr
        case OptgroupArg.Option(opt)     => childBuilder += opt
      }
      i += 1
    }
    Dom.Element.OptgroupElement(attrBuilder.result(), childBuilder.result())
  }

  val doctype: Dom.Doctype    = Dom.Doctype("html")
  val html: Dom.Element       = Dom.Element.Generic("html", Chunk.empty, Chunk.empty)
  val head: Dom.Element       = Dom.Element.Generic("head", Chunk.empty, Chunk.empty)
  val body: Dom.Element       = Dom.Element.Generic("body", Chunk.empty, Chunk.empty)
  val title: Dom.Element      = Dom.Element.Generic("title", Chunk.empty, Chunk.empty)
  val div: Dom.Element        = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
  val span: Dom.Element       = Dom.Element.Generic("span", Chunk.empty, Chunk.empty)
  val p: Dom.Element          = Dom.Element.Generic("p", Chunk.empty, Chunk.empty)
  val h1: Dom.Element         = Dom.Element.Generic("h1", Chunk.empty, Chunk.empty)
  val h2: Dom.Element         = Dom.Element.Generic("h2", Chunk.empty, Chunk.empty)
  val h3: Dom.Element         = Dom.Element.Generic("h3", Chunk.empty, Chunk.empty)
  val h4: Dom.Element         = Dom.Element.Generic("h4", Chunk.empty, Chunk.empty)
  val h5: Dom.Element         = Dom.Element.Generic("h5", Chunk.empty, Chunk.empty)
  val h6: Dom.Element         = Dom.Element.Generic("h6", Chunk.empty, Chunk.empty)
  val a: Dom.Element          = Dom.Element.Generic("a", Chunk.empty, Chunk.empty)
  val abbr: Dom.Element       = Dom.Element.Generic("abbr", Chunk.empty, Chunk.empty)
  val address: Dom.Element    = Dom.Element.Generic("address", Chunk.empty, Chunk.empty)
  val area: Dom.Element.Void  = Dom.Element.VoidGeneric("area", Chunk.empty)
  val article: Dom.Element    = Dom.Element.Generic("article", Chunk.empty, Chunk.empty)
  val aside: Dom.Element      = Dom.Element.Generic("aside", Chunk.empty, Chunk.empty)
  val audio: Dom.Element      = Dom.Element.Generic("audio", Chunk.empty, Chunk.empty)
  val b: Dom.Element          = Dom.Element.Generic("b", Chunk.empty, Chunk.empty)
  val base: Dom.Element.Void  = Dom.Element.VoidGeneric("base", Chunk.empty)
  val bdi: Dom.Element        = Dom.Element.Generic("bdi", Chunk.empty, Chunk.empty)
  val bdo: Dom.Element        = Dom.Element.Generic("bdo", Chunk.empty, Chunk.empty)
  val blockquote: Dom.Element = Dom.Element.Generic("blockquote", Chunk.empty, Chunk.empty)
  val br: Dom.Element.Void    = Dom.Element.VoidGeneric("br", Chunk.empty)
  val button: Dom.Element     = Dom.Element.Generic("button", Chunk.empty, Chunk.empty)
  val canvas: Dom.Element     = Dom.Element.Generic("canvas", Chunk.empty, Chunk.empty)
  val caption: Dom.Element    = Dom.Element.Generic("caption", Chunk.empty, Chunk.empty)
  val cite: Dom.Element       = Dom.Element.Generic("cite", Chunk.empty, Chunk.empty)
  val code: Dom.Element       = Dom.Element.Generic("code", Chunk.empty, Chunk.empty)
  val col: Dom.Element.Void   = Dom.Element.VoidGeneric("col", Chunk.empty)
  val colgroup: Dom.Element   = Dom.Element.Generic("colgroup", Chunk.empty, Chunk.empty)
  val data: Dom.Element       = Dom.Element.Generic("data", Chunk.empty, Chunk.empty)
  val datalist: Dom.Element   = Dom.Element.Generic("datalist", Chunk.empty, Chunk.empty)
  val dd: Dom.Element         = Dom.Element.Generic("dd", Chunk.empty, Chunk.empty)
  val del: Dom.Element        = Dom.Element.Generic("del", Chunk.empty, Chunk.empty)
  val details: Dom.Element    = Dom.Element.Generic("details", Chunk.empty, Chunk.empty)
  val dfn: Dom.Element        = Dom.Element.Generic("dfn", Chunk.empty, Chunk.empty)
  val dialog: Dom.Element     = Dom.Element.Generic("dialog", Chunk.empty, Chunk.empty)
  val dl: Dom.Element         = Dom.Element.Generic("dl", Chunk.empty, Chunk.empty)
  val dt: Dom.Element         = Dom.Element.Generic("dt", Chunk.empty, Chunk.empty)
  val em: Dom.Element         = Dom.Element.Generic("em", Chunk.empty, Chunk.empty)
  val embed: Dom.Element.Void = Dom.Element.VoidGeneric("embed", Chunk.empty)
  val fieldset: Dom.Element   = Dom.Element.Generic("fieldset", Chunk.empty, Chunk.empty)
  val figure: Dom.Element     = Dom.Element.Generic("figure", Chunk.empty, Chunk.empty)
  val figcaption: Dom.Element = Dom.Element.Generic("figcaption", Chunk.empty, Chunk.empty)
  val footer: Dom.Element     = Dom.Element.Generic("footer", Chunk.empty, Chunk.empty)
  val form: Dom.Element       = Dom.Element.Generic("form", Chunk.empty, Chunk.empty)
  val header: Dom.Element     = Dom.Element.Generic("header", Chunk.empty, Chunk.empty)
  val hgroup: Dom.Element     = Dom.Element.Generic("hgroup", Chunk.empty, Chunk.empty)
  val hr: Dom.Element.Void    = Dom.Element.VoidGeneric("hr", Chunk.empty)
  val i: Dom.Element          = Dom.Element.Generic("i", Chunk.empty, Chunk.empty)
  val iframe: Dom.Element     = Dom.Element.Generic("iframe", Chunk.empty, Chunk.empty)
  val img: Dom.Element.Void   = Dom.Element.VoidGeneric("img", Chunk.empty)
  val input: Dom.Element.Void = Dom.Element.VoidGeneric("input", Chunk.empty)
  val ins: Dom.Element        = Dom.Element.Generic("ins", Chunk.empty, Chunk.empty)
  val kbd: Dom.Element        = Dom.Element.Generic("kbd", Chunk.empty, Chunk.empty)
  val label: Dom.Element      = Dom.Element.Generic("label", Chunk.empty, Chunk.empty)
  val legend: Dom.Element     = Dom.Element.Generic("legend", Chunk.empty, Chunk.empty)
  val link: Dom.Element.Void  = Dom.Element.VoidGeneric("link", Chunk.empty)
  val main: Dom.Element       = Dom.Element.Generic("main", Chunk.empty, Chunk.empty)
  val menu: Dom.Element       = Dom.Element.Generic("menu", Chunk.empty, Chunk.empty)
  val map: Dom.Element        = Dom.Element.Generic("map", Chunk.empty, Chunk.empty)
  val mark: Dom.Element       = Dom.Element.Generic("mark", Chunk.empty, Chunk.empty)
  val math: Dom.Element       = Dom.Element.Generic("math", Chunk.empty, Chunk.empty)
  val meta: Dom.Element.Void  = Dom.Element.VoidGeneric("meta", Chunk.empty)
  val meter: Dom.Element      = Dom.Element.Generic("meter", Chunk.empty, Chunk.empty)
  val nav: Dom.Element        = Dom.Element.Generic("nav", Chunk.empty, Chunk.empty)
  val noscript: Dom.Element   = Dom.Element.Generic("noscript", Chunk.empty, Chunk.empty)
  val `object`: Dom.Element   = Dom.Element.Generic("object", Chunk.empty, Chunk.empty)
  val objectTag: Dom.Element  = Dom.Element.Generic("object", Chunk.empty, Chunk.empty)
  val output: Dom.Element     = Dom.Element.Generic("output", Chunk.empty, Chunk.empty)
  val param: Dom.Element.Void = Dom.Element.VoidGeneric("param", Chunk.empty)
  val picture: Dom.Element    = Dom.Element.Generic("picture", Chunk.empty, Chunk.empty)
  val pre: Dom.Element        = Dom.Element.Generic("pre", Chunk.empty, Chunk.empty)
  val progress: Dom.Element   = Dom.Element.Generic("progress", Chunk.empty, Chunk.empty)
  val q: Dom.Element          = Dom.Element.Generic("q", Chunk.empty, Chunk.empty)
  val rp: Dom.Element         = Dom.Element.Generic("rp", Chunk.empty, Chunk.empty)
  val rt: Dom.Element         = Dom.Element.Generic("rt", Chunk.empty, Chunk.empty)
  val ruby: Dom.Element       = Dom.Element.Generic("ruby", Chunk.empty, Chunk.empty)
  val s: Dom.Element          = Dom.Element.Generic("s", Chunk.empty, Chunk.empty)
  val samp: Dom.Element       = Dom.Element.Generic("samp", Chunk.empty, Chunk.empty)

  /** Creates an empty `script` element. */
  def script(): Dom.Element.Script = elScript(Seq.empty)

  /**
   * Creates a `script` element from attributes and typed JavaScript fragments.
   *
   * Plain strings are intentionally not accepted. Use [[Js]] values for inline
   * JavaScript so script content is explicit and cannot be confused with normal
   * child text or other DOM modifiers. `Js.Raw` content is emitted verbatim, so
   * only trusted JavaScript should be passed to it.
   */
  def script(effect: ScriptArg, effects: ScriptArg*): Dom.Element.Script = elScript(effect +: effects)
  val search: Dom.Element                                                = Dom.Element.Generic("search", Chunk.empty, Chunk.empty)
  val section: Dom.Element                                               = Dom.Element.Generic("section", Chunk.empty, Chunk.empty)
  val slot: Dom.Element                                                  = Dom.Element.Generic("slot", Chunk.empty, Chunk.empty)
  val small: Dom.Element                                                 = Dom.Element.Generic("small", Chunk.empty, Chunk.empty)
  val source: Dom.Element.Void                                           = Dom.Element.VoidGeneric("source", Chunk.empty)
  val strong: Dom.Element                                                = Dom.Element.Generic("strong", Chunk.empty, Chunk.empty)

  /** Creates an empty `style` element. */
  def style(): Dom.Element.Style = elStyle(Seq.empty)

  /**
   * Creates a `style` element from attributes and typed CSS fragments.
   *
   * Plain strings are intentionally not accepted. Use [[Css]] values for inline
   * stylesheets so CSS content is explicit and cannot be confused with normal
   * child text or other DOM modifiers. Raw CSS is emitted according to the
   * chosen [[Css]] constructor, so untrusted input must not be passed to raw
   * CSS.
   */
  def style(effect: StyleArg, effects: StyleArg*): Dom.Element.Style = elStyle(effect +: effects)
  val sub: Dom.Element                                               = Dom.Element.Generic("sub", Chunk.empty, Chunk.empty)
  val summary: Dom.Element                                           = Dom.Element.Generic("summary", Chunk.empty, Chunk.empty)
  val sup: Dom.Element                                               = Dom.Element.Generic("sup", Chunk.empty, Chunk.empty)
  val svg: Dom.Element                                               = Dom.Element.Generic("svg", Chunk.empty, Chunk.empty)
  val tbody: Dom.Element                                             = Dom.Element.Generic("tbody", Chunk.empty, Chunk.empty)
  val `template`: Dom.Element                                        = Dom.Element.Generic("template", Chunk.empty, Chunk.empty)
  val templateTag: Dom.Element                                       = Dom.Element.Generic("template", Chunk.empty, Chunk.empty)
  val textarea: Dom.Element                                          = Dom.Element.Generic("textarea", Chunk.empty, Chunk.empty)
  val tfoot: Dom.Element                                             = Dom.Element.Generic("tfoot", Chunk.empty, Chunk.empty)
  val thead: Dom.Element                                             = Dom.Element.Generic("thead", Chunk.empty, Chunk.empty)
  val time: Dom.Element                                              = Dom.Element.Generic("time", Chunk.empty, Chunk.empty)
  val track: Dom.Element.Void                                        = Dom.Element.VoidGeneric("track", Chunk.empty)
  val u: Dom.Element                                                 = Dom.Element.Generic("u", Chunk.empty, Chunk.empty)
  val `var`: Dom.Element                                             = Dom.Element.Generic("var", Chunk.empty, Chunk.empty)
  val varTag: Dom.Element                                            = Dom.Element.Generic("var", Chunk.empty, Chunk.empty)
  val video: Dom.Element                                             = Dom.Element.Generic("video", Chunk.empty, Chunk.empty)
  val wbr: Dom.Element.Void                                          = Dom.Element.VoidGeneric("wbr", Chunk.empty)
  def element(tag: String): Dom.Element                              = Dom.Element.Generic(tag, Chunk.empty, Chunk.empty)

  /**
   * Empty `<li>` element; apply attributes/children via `li(...)`, returning
   * `Li`.
   */
  val li: Dom.Element.LiElement = Dom.Element.LiElement(Chunk.empty, Chunk.empty)

  /** Creates an empty `<ul>` element. */
  def ul(): Dom.Element = Dom.Element.Generic("ul", Chunk.empty, Chunk.empty)

  /**
   * Creates a `<ul>` element from attributes and `<li>` children.
   *
   * The HTML content model of `<ul>` permits only `<li>` element children, so
   * the child arguments are restricted to [[Dom.Element.Li]] at compile time;
   * [[Dom.Attribute]] values are accepted alongside them.
   */
  def ul(effect: ListArg, effects: ListArg*): Dom.Element =
    elFromListArgs(effect +: effects, "ul")

  /** Creates a `<ul>` element from an iterable of `<li>` children. */
  def ul(children: Iterable[Dom.Element.Li]): Dom.Element =
    Dom.Element.Generic("ul", Chunk.empty, Chunk.from(children))

  /** Creates an empty `<ol>` element. */
  def ol(): Dom.Element = Dom.Element.Generic("ol", Chunk.empty, Chunk.empty)

  /**
   * Creates an `<ol>` element from attributes and `<li>` children.
   *
   * The HTML content model of `<ol>` permits only `<li>` element children, so
   * the child arguments are restricted to [[Dom.Element.Li]] at compile time;
   * [[Dom.Attribute]] values are accepted alongside them.
   */
  def ol(effect: ListArg, effects: ListArg*): Dom.Element =
    elFromListArgs(effect +: effects, "ol")

  /** Creates an `<ol>` element from an iterable of `<li>` children. */
  def ol(children: Iterable[Dom.Element.Li]): Dom.Element =
    Dom.Element.Generic("ol", Chunk.empty, Chunk.from(children))

  /**
   * Empty `<th>` element; apply attributes/children via `th(...)`, returning
   * `Th`.
   */
  val th: Dom.Element.ThElement = Dom.Element.ThElement(Chunk.empty, Chunk.empty)

  /**
   * Empty `<td>` element; apply attributes/children via `td(...)`, returning
   * `Td`.
   */
  val td: Dom.Element.TdElement = Dom.Element.TdElement(Chunk.empty, Chunk.empty)

  /** Creates an empty `<tr>` element, returning `Tr`. */
  def tr(): Dom.Element.TrElement = Dom.Element.TrElement(Chunk.empty, Chunk.empty)

  /**
   * Creates a `<tr>` element from attributes and table cells (`<th>`/`<td>`)
   *
   * The HTML content model of `<tr>` permits only `<th>` and `<td>` element
   * children, so the child arguments are restricted to [[Dom.Element.Cell]] at
   * compile time; [[Dom.Attribute]] values are accepted alongside them.
   */
  def tr(effect: CellArg, effects: CellArg*): Dom.Element.Tr =
    trFromCellArgs(effect +: effects)

  /** Creates a `<tr>` element from an iterable of `<th>`/`<td>` cells. */
  def tr(children: Iterable[Dom.Element.Cell]): Dom.Element.Tr =
    Dom.Element.TrElement(Chunk.empty, Chunk.from(children))

  /** Creates an empty `<table>` element. */
  def table(): Dom.Element = Dom.Element.Generic("table", Chunk.empty, Chunk.empty)

  /**
   * Creates a `<table>` element from attributes and `<tr>` rows.
   *
   * The row arguments are restricted to [[Dom.Element.Tr]] at compile time;
   * [[Dom.Attribute]] values are accepted alongside them. Other table sections
   * (`caption`, `colgroup`, `thead`, `tbody`, `tfoot`) do not have typed
   * markers yet — compose tables containing them with the `html"` interpolator
   * or generic elements instead.
   */
  def table(effect: RowArg, effects: RowArg*): Dom.Element =
    tableFromRowArgs(effect +: effects)

  /** Creates a `<table>` element from an iterable of `<tr>` rows. */
  def table(children: Iterable[Dom.Element.Tr]): Dom.Element =
    Dom.Element.Generic("table", Chunk.empty, Chunk.from(children))

  /**
   * Empty `<option>` element; apply attributes/children via `option(...)`,
   * returning `Opt`.
   */
  val option: Dom.Element.OptElement = Dom.Element.OptElement(Chunk.empty, Chunk.empty)

  /** Creates an empty `<optgroup>` element, returning `Optgroup`. */
  def optgroup(): Dom.Element.OptgroupElement = Dom.Element.OptgroupElement(Chunk.empty, Chunk.empty)

  /**
   * Creates an `<optgroup>` element from attributes and `<option>` children.
   *
   * The HTML content model of `<optgroup>` permits only `<option>` element
   * children, so the child arguments are restricted to [[Dom.Element.Opt]] at
   * compile time; [[Dom.Attribute]] values are accepted alongside them.
   */
  def optgroup(effect: OptgroupArg, effects: OptgroupArg*): Dom.Element.OptgroupElement =
    groupFromOptgroupArgs(effect +: effects)

  /**
   * Creates an `<optgroup>` element from an iterable of `<option>` children.
   */
  def optgroup(children: Iterable[Dom.Element.Opt]): Dom.Element.OptgroupElement =
    Dom.Element.OptgroupElement(Chunk.empty, Chunk.from(children))

  /** Creates an empty `<select>` element. */
  def select(): Dom.Element = Dom.Element.Generic("select", Chunk.empty, Chunk.empty)

  /**
   * Creates a `<select>` element from attributes and `<option>`/`<optgroup>`
   * children.
   *
   * The HTML content model of `<select>` permits only `<option>` and
   * `<optgroup>` element children, so the child arguments are restricted to
   * [[Dom.Element.SelectChild]] at compile time; [[Dom.Attribute]] values are
   * accepted alongside them.
   */
  def select(effect: SelectArg, effects: SelectArg*): Dom.Element =
    selectFromSelectArgs(effect +: effects)

  /**
   * Creates a `<select>` element from an iterable of `<option>`/`<optgroup>`
   * children.
   */
  def select(children: Iterable[Dom.Element.SelectChild]): Dom.Element =
    Dom.Element.Generic("select", Chunk.empty, Chunk.from(children))

  // --- Attribute helpers ---

  val id: AttributeKey                               = new AttributeKey("id")
  val `class`: MultiAttributeKey                     = new MultiAttributeKey("class", Dom.AttributeSeparator.Space)
  val className: MultiAttributeKey                   = new MultiAttributeKey("class", Dom.AttributeSeparator.Space)
  val styleAttr: AttributeKey                        = new AttributeKey("style")
  val titleAttr: AttributeKey                        = new AttributeKey("title")
  val href: AttributeKey                             = new AttributeKey("href")
  val src: AttributeKey                              = new AttributeKey("src")
  val alt: AttributeKey                              = new AttributeKey("alt")
  val width: AttributeKey                            = new AttributeKey("width")
  val height: AttributeKey                           = new AttributeKey("height")
  val action: AttributeKey                           = new AttributeKey("action")
  val method: AttributeKey                           = new AttributeKey("method")
  val name: AttributeKey                             = new AttributeKey("name")
  val value: AttributeKey                            = new AttributeKey("value")
  val `type`: AttributeKey                           = new AttributeKey("type")
  val typeAttr: AttributeKey                         = new AttributeKey("type")
  val placeholder: AttributeKey                      = new AttributeKey("placeholder")
  val required: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("required")
  val disabled: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("disabled")
  val readonly: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("readonly")
  val checked: Dom.Attribute.BooleanAttribute        = Dom.boolAttr("checked")
  val selected: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("selected")
  val multiple: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("multiple")
  val min: AttributeKey                              = new AttributeKey("min")
  val max: AttributeKey                              = new AttributeKey("max")
  val step: AttributeKey                             = new AttributeKey("step")
  val pattern: AttributeKey                          = new AttributeKey("pattern")
  val autofocus: Dom.Attribute.BooleanAttribute      = Dom.boolAttr("autofocus")
  val autoComplete: AttributeKey                     = new AttributeKey("autocomplete")
  val target: AttributeKey                           = new AttributeKey("target")
  val rel: MultiAttributeKey                         = new MultiAttributeKey("rel", Dom.AttributeSeparator.Space)
  val download: AttributeKey                         = new AttributeKey("download")
  val role: AttributeKey                             = new AttributeKey("role")
  val tabIndex: AttributeKey                         = new AttributeKey("tabindex")
  val hidden: Dom.Attribute.BooleanAttribute         = Dom.boolAttr("hidden")
  val draggable: AttributeKey                        = new AttributeKey("draggable")
  val contentEditable: AttributeKey                  = new AttributeKey("contenteditable")
  val lang: AttributeKey                             = new AttributeKey("lang")
  val dir: AttributeKey                              = new AttributeKey("dir")
  val colspan: AttributeKey                          = new AttributeKey("colspan")
  val rowspan: AttributeKey                          = new AttributeKey("rowspan")
  val `scope`: AttributeKey                          = new AttributeKey("scope")
  val scopeAttr: AttributeKey                        = new AttributeKey("scope")
  val headers: AttributeKey                          = new AttributeKey("headers")
  val `for`: AttributeKey                            = new AttributeKey("for")
  val forAttr: AttributeKey                          = new AttributeKey("for")
  val encType: AttributeKey                          = new AttributeKey("enctype")
  val formAction: AttributeKey                       = new AttributeKey("formaction")
  val formMethod: AttributeKey                       = new AttributeKey("formmethod")
  val loading: AttributeKey                          = new AttributeKey("loading")
  val srcSet: AttributeKey                           = new AttributeKey("srcset")
  val sizes: AttributeKey                            = new AttributeKey("sizes")
  val minLength: AttributeKey                        = new AttributeKey("minlength")
  val maxLength: AttributeKey                        = new AttributeKey("maxlength")
  val size: AttributeKey                             = new AttributeKey("size")
  val cols: AttributeKey                             = new AttributeKey("cols")
  val rows: AttributeKey                             = new AttributeKey("rows")
  val wrap: AttributeKey                             = new AttributeKey("wrap")
  val accept: AttributeKey                           = new AttributeKey("accept")
  val blocking: AttributeKey                         = new AttributeKey("blocking")
  val enterKeyHint: AttributeKey                     = new AttributeKey("enterkeyhint")
  val exportParts: AttributeKey                      = new AttributeKey("exportparts")
  val fetchPriority: AttributeKey                    = new AttributeKey("fetchpriority")
  val inputMode: AttributeKey                        = new AttributeKey("inputmode")
  val inert: Dom.Attribute.BooleanAttribute          = Dom.boolAttr("inert")
  val itemId: AttributeKey                           = new AttributeKey("itemid")
  val itemProp: AttributeKey                         = new AttributeKey("itemprop")
  val itemRef: AttributeKey                          = new AttributeKey("itemref")
  val itemScope: Dom.Attribute.BooleanAttribute      = Dom.boolAttr("itemscope")
  val itemType: AttributeKey                         = new AttributeKey("itemtype")
  val nonce: AttributeKey                            = new AttributeKey("nonce")
  val part: AttributeKey                             = new AttributeKey("part")
  val popover: AttributeKey                          = new AttributeKey("popover")
  val popoverTarget: AttributeKey                    = new AttributeKey("popovertarget")
  val popoverTargetAction: AttributeKey              = new AttributeKey("popovertargetaction")
  val writingSuggestions: AttributeKey               = new AttributeKey("writingsuggestions")
  val accesskey: AttributeKey                        = new AttributeKey("accesskey")
  val async: Dom.Attribute.BooleanAttribute          = Dom.boolAttr("async")
  val autoplay: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("autoplay")
  val charset: AttributeKey                          = new AttributeKey("charset")
  val content: AttributeKey                          = new AttributeKey("content")
  val controls: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("controls")
  val crossorigin: AttributeKey                      = new AttributeKey("crossorigin")
  val datetime: AttributeKey                         = new AttributeKey("datetime")
  val defer: Dom.Attribute.BooleanAttribute          = Dom.boolAttr("defer")
  val formAttr: AttributeKey                         = new AttributeKey("form")
  val formNoValidate: Dom.Attribute.BooleanAttribute = Dom.boolAttr("formnovalidate")
  val high: AttributeKey                             = new AttributeKey("high")
  val httpEquiv: AttributeKey                        = new AttributeKey("http-equiv")
  val integrity: AttributeKey                        = new AttributeKey("integrity")
  val labelAttr: AttributeKey                        = new AttributeKey("label")
  val list: AttributeKey                             = new AttributeKey("list")
  val loop: Dom.Attribute.BooleanAttribute           = Dom.boolAttr("loop")
  val low: AttributeKey                              = new AttributeKey("low")
  val media: AttributeKey                            = new AttributeKey("media")
  val muted: Dom.Attribute.BooleanAttribute          = Dom.boolAttr("muted")
  val noValidate: Dom.Attribute.BooleanAttribute     = Dom.boolAttr("novalidate")
  val open: Dom.Attribute.BooleanAttribute           = Dom.boolAttr("open")
  val optimum: AttributeKey                          = new AttributeKey("optimum")
  val poster: AttributeKey                           = new AttributeKey("poster")
  val preload: AttributeKey                          = new AttributeKey("preload")
  val referrerpolicy: AttributeKey                   = new AttributeKey("referrerpolicy")
  val reversed: Dom.Attribute.BooleanAttribute       = Dom.boolAttr("reversed")
  val sandbox: AttributeKey                          = new AttributeKey("sandbox")
  val spanAttr: AttributeKey                         = new AttributeKey("span")
  val spellcheck: AttributeKey                       = new AttributeKey("spellcheck")
  val summaryAttr: AttributeKey                      = new AttributeKey("summary")
  val translate: AttributeKey                        = new AttributeKey("translate")
  val citeAttr: AttributeKey                         = new AttributeKey("cite")
  val slotAttr: AttributeKey                         = new AttributeKey("slot")
  val xmlns: AttributeKey                            = new AttributeKey("xmlns")

  // --- Multi-value attribute helpers ---

  val ariaDescribedby: MultiAttributeKey =
    new MultiAttributeKey("aria-describedby", Dom.AttributeSeparator.Space)
  val ariaLabelledby: MultiAttributeKey = new MultiAttributeKey("aria-labelledby", Dom.AttributeSeparator.Space)

  def multiAttr(name: String): MultiAttributeKey                                                              = Dom.multiAttr(name)
  def multiAttr(name: String, separator: Dom.AttributeSeparator): MultiAttributeKey                           = Dom.multiAttr(name, separator)
  def multiAttr(name: String, values: Iterable[String]): Dom.Attribute                                        = Dom.multiAttr(name, values)
  def multiAttr(name: String, separator: Dom.AttributeSeparator, value: String, rest: String*): Dom.Attribute =
    Dom.multiAttr(name, separator, value, rest: _*)

  // --- DOM helper functions ---

  val empty: Dom = Dom.Empty

  def aria(name: String): AttributeKey     = new AttributeKey("aria-" + name)
  def dataAttr(name: String): AttributeKey = new AttributeKey("data-" + name)
  def attr(name: String): AttributeKey     = new AttributeKey(name)
}
