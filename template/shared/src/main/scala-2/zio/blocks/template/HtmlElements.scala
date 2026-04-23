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

package zio.blocks.template

import zio.blocks.chunk.Chunk
trait HtmlElements {

  // --- Element constructors ---

  private def elScript(effects: Seq[DomModifier]): Dom.Element.Script = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    var i            = 0
    while (i < effects.length) {
      effects(i) match {
        case DomModifier.AddAttr(a)       => attrBuilder += a
        case DomModifier.AddChild(c)      => childBuilder += c
        case DomModifier.AddChildren(cs)  => childBuilder ++= cs
        case DomModifier.AddEffects(effs) =>
          var j = 0
          while (j < effs.length) {
            effs(j) match {
              case DomModifier.AddAttr(a)      => attrBuilder += a
              case DomModifier.AddChild(c)     => childBuilder += c
              case DomModifier.AddChildren(cs) => childBuilder ++= cs
              case _                           => ()
            }
            j += 1
          }
      }
      i += 1
    }
    Dom.Element.Script(attrBuilder.result(), childBuilder.result())
  }

  private def elStyle(effects: Seq[DomModifier]): Dom.Element.Style = {
    val attrBuilder  = Chunk.newBuilder[Dom.Attribute]
    val childBuilder = Chunk.newBuilder[Dom]
    var i            = 0
    while (i < effects.length) {
      effects(i) match {
        case DomModifier.AddAttr(a)       => attrBuilder += a
        case DomModifier.AddChild(c)      => childBuilder += c
        case DomModifier.AddChildren(cs)  => childBuilder ++= cs
        case DomModifier.AddEffects(effs) =>
          var j = 0
          while (j < effs.length) {
            effs(j) match {
              case DomModifier.AddAttr(a)      => attrBuilder += a
              case DomModifier.AddChild(c)     => childBuilder += c
              case DomModifier.AddChildren(cs) => childBuilder ++= cs
              case _                           => ()
            }
            j += 1
          }
      }
      i += 1
    }
    Dom.Element.Style(attrBuilder.result(), childBuilder.result())
  }

  val doctype: Dom.Doctype                                                   = Dom.Doctype("html")
  val html: Dom.Element                                                      = Dom.Element.Generic("html", Chunk.empty, Chunk.empty)
  val head: Dom.Element                                                      = Dom.Element.Generic("head", Chunk.empty, Chunk.empty)
  val body: Dom.Element                                                      = Dom.Element.Generic("body", Chunk.empty, Chunk.empty)
  val title: Dom.Element                                                     = Dom.Element.Generic("title", Chunk.empty, Chunk.empty)
  val div: Dom.Element                                                       = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)
  val span: Dom.Element                                                      = Dom.Element.Generic("span", Chunk.empty, Chunk.empty)
  val p: Dom.Element                                                         = Dom.Element.Generic("p", Chunk.empty, Chunk.empty)
  val h1: Dom.Element                                                        = Dom.Element.Generic("h1", Chunk.empty, Chunk.empty)
  val h2: Dom.Element                                                        = Dom.Element.Generic("h2", Chunk.empty, Chunk.empty)
  val h3: Dom.Element                                                        = Dom.Element.Generic("h3", Chunk.empty, Chunk.empty)
  val h4: Dom.Element                                                        = Dom.Element.Generic("h4", Chunk.empty, Chunk.empty)
  val h5: Dom.Element                                                        = Dom.Element.Generic("h5", Chunk.empty, Chunk.empty)
  val h6: Dom.Element                                                        = Dom.Element.Generic("h6", Chunk.empty, Chunk.empty)
  val a: Dom.Element                                                         = Dom.Element.Generic("a", Chunk.empty, Chunk.empty)
  val abbr: Dom.Element                                                      = Dom.Element.Generic("abbr", Chunk.empty, Chunk.empty)
  val address: Dom.Element                                                   = Dom.Element.Generic("address", Chunk.empty, Chunk.empty)
  val area: Dom.Element                                                      = Dom.Element.Generic("area", Chunk.empty, Chunk.empty)
  val article: Dom.Element                                                   = Dom.Element.Generic("article", Chunk.empty, Chunk.empty)
  val aside: Dom.Element                                                     = Dom.Element.Generic("aside", Chunk.empty, Chunk.empty)
  val audio: Dom.Element                                                     = Dom.Element.Generic("audio", Chunk.empty, Chunk.empty)
  val b: Dom.Element                                                         = Dom.Element.Generic("b", Chunk.empty, Chunk.empty)
  val base: Dom.Element                                                      = Dom.Element.Generic("base", Chunk.empty, Chunk.empty)
  val bdi: Dom.Element                                                       = Dom.Element.Generic("bdi", Chunk.empty, Chunk.empty)
  val bdo: Dom.Element                                                       = Dom.Element.Generic("bdo", Chunk.empty, Chunk.empty)
  val blockquote: Dom.Element                                                = Dom.Element.Generic("blockquote", Chunk.empty, Chunk.empty)
  val br: Dom.Element                                                        = Dom.Element.Generic("br", Chunk.empty, Chunk.empty)
  val button: Dom.Element                                                    = Dom.Element.Generic("button", Chunk.empty, Chunk.empty)
  val canvas: Dom.Element                                                    = Dom.Element.Generic("canvas", Chunk.empty, Chunk.empty)
  val caption: Dom.Element                                                   = Dom.Element.Generic("caption", Chunk.empty, Chunk.empty)
  val cite: Dom.Element                                                      = Dom.Element.Generic("cite", Chunk.empty, Chunk.empty)
  val code: Dom.Element                                                      = Dom.Element.Generic("code", Chunk.empty, Chunk.empty)
  val col: Dom.Element                                                       = Dom.Element.Generic("col", Chunk.empty, Chunk.empty)
  val colgroup: Dom.Element                                                  = Dom.Element.Generic("colgroup", Chunk.empty, Chunk.empty)
  val data: Dom.Element                                                      = Dom.Element.Generic("data", Chunk.empty, Chunk.empty)
  val datalist: Dom.Element                                                  = Dom.Element.Generic("datalist", Chunk.empty, Chunk.empty)
  val dd: Dom.Element                                                        = Dom.Element.Generic("dd", Chunk.empty, Chunk.empty)
  val del: Dom.Element                                                       = Dom.Element.Generic("del", Chunk.empty, Chunk.empty)
  val details: Dom.Element                                                   = Dom.Element.Generic("details", Chunk.empty, Chunk.empty)
  val dfn: Dom.Element                                                       = Dom.Element.Generic("dfn", Chunk.empty, Chunk.empty)
  val dialog: Dom.Element                                                    = Dom.Element.Generic("dialog", Chunk.empty, Chunk.empty)
  val dl: Dom.Element                                                        = Dom.Element.Generic("dl", Chunk.empty, Chunk.empty)
  val dt: Dom.Element                                                        = Dom.Element.Generic("dt", Chunk.empty, Chunk.empty)
  val em: Dom.Element                                                        = Dom.Element.Generic("em", Chunk.empty, Chunk.empty)
  val embed: Dom.Element                                                     = Dom.Element.Generic("embed", Chunk.empty, Chunk.empty)
  val fieldset: Dom.Element                                                  = Dom.Element.Generic("fieldset", Chunk.empty, Chunk.empty)
  val figure: Dom.Element                                                    = Dom.Element.Generic("figure", Chunk.empty, Chunk.empty)
  val figcaption: Dom.Element                                                = Dom.Element.Generic("figcaption", Chunk.empty, Chunk.empty)
  val footer: Dom.Element                                                    = Dom.Element.Generic("footer", Chunk.empty, Chunk.empty)
  val form: Dom.Element                                                      = Dom.Element.Generic("form", Chunk.empty, Chunk.empty)
  val header: Dom.Element                                                    = Dom.Element.Generic("header", Chunk.empty, Chunk.empty)
  val hgroup: Dom.Element                                                    = Dom.Element.Generic("hgroup", Chunk.empty, Chunk.empty)
  val hr: Dom.Element                                                        = Dom.Element.Generic("hr", Chunk.empty, Chunk.empty)
  val i: Dom.Element                                                         = Dom.Element.Generic("i", Chunk.empty, Chunk.empty)
  val iframe: Dom.Element                                                    = Dom.Element.Generic("iframe", Chunk.empty, Chunk.empty)
  val img: Dom.Element                                                       = Dom.Element.Generic("img", Chunk.empty, Chunk.empty)
  val input: Dom.Element                                                     = Dom.Element.Generic("input", Chunk.empty, Chunk.empty)
  val ins: Dom.Element                                                       = Dom.Element.Generic("ins", Chunk.empty, Chunk.empty)
  val kbd: Dom.Element                                                       = Dom.Element.Generic("kbd", Chunk.empty, Chunk.empty)
  val label: Dom.Element                                                     = Dom.Element.Generic("label", Chunk.empty, Chunk.empty)
  val legend: Dom.Element                                                    = Dom.Element.Generic("legend", Chunk.empty, Chunk.empty)
  val li: Dom.Element                                                        = Dom.Element.Generic("li", Chunk.empty, Chunk.empty)
  val link: Dom.Element                                                      = Dom.Element.Generic("link", Chunk.empty, Chunk.empty)
  val main: Dom.Element                                                      = Dom.Element.Generic("main", Chunk.empty, Chunk.empty)
  val menu: Dom.Element                                                      = Dom.Element.Generic("menu", Chunk.empty, Chunk.empty)
  val map: Dom.Element                                                       = Dom.Element.Generic("map", Chunk.empty, Chunk.empty)
  val mark: Dom.Element                                                      = Dom.Element.Generic("mark", Chunk.empty, Chunk.empty)
  val math: Dom.Element                                                      = Dom.Element.Generic("math", Chunk.empty, Chunk.empty)
  val meta: Dom.Element                                                      = Dom.Element.Generic("meta", Chunk.empty, Chunk.empty)
  val meter: Dom.Element                                                     = Dom.Element.Generic("meter", Chunk.empty, Chunk.empty)
  val nav: Dom.Element                                                       = Dom.Element.Generic("nav", Chunk.empty, Chunk.empty)
  val noscript: Dom.Element                                                  = Dom.Element.Generic("noscript", Chunk.empty, Chunk.empty)
  val `object`: Dom.Element                                                  = Dom.Element.Generic("object", Chunk.empty, Chunk.empty)
  val objectTag: Dom.Element                                                 = Dom.Element.Generic("object", Chunk.empty, Chunk.empty)
  val ol: Dom.Element                                                        = Dom.Element.Generic("ol", Chunk.empty, Chunk.empty)
  val optgroup: Dom.Element                                                  = Dom.Element.Generic("optgroup", Chunk.empty, Chunk.empty)
  val option: Dom.Element                                                    = Dom.Element.Generic("option", Chunk.empty, Chunk.empty)
  val output: Dom.Element                                                    = Dom.Element.Generic("output", Chunk.empty, Chunk.empty)
  val param: Dom.Element                                                     = Dom.Element.Generic("param", Chunk.empty, Chunk.empty)
  val picture: Dom.Element                                                   = Dom.Element.Generic("picture", Chunk.empty, Chunk.empty)
  val pre: Dom.Element                                                       = Dom.Element.Generic("pre", Chunk.empty, Chunk.empty)
  val progress: Dom.Element                                                  = Dom.Element.Generic("progress", Chunk.empty, Chunk.empty)
  val q: Dom.Element                                                         = Dom.Element.Generic("q", Chunk.empty, Chunk.empty)
  val rp: Dom.Element                                                        = Dom.Element.Generic("rp", Chunk.empty, Chunk.empty)
  val rt: Dom.Element                                                        = Dom.Element.Generic("rt", Chunk.empty, Chunk.empty)
  val ruby: Dom.Element                                                      = Dom.Element.Generic("ruby", Chunk.empty, Chunk.empty)
  val s: Dom.Element                                                         = Dom.Element.Generic("s", Chunk.empty, Chunk.empty)
  val samp: Dom.Element                                                      = Dom.Element.Generic("samp", Chunk.empty, Chunk.empty)
  def script(): Dom.Element.Script                                           = elScript(Seq.empty)
  def script(effect: DomModifier, effects: DomModifier*): Dom.Element.Script = elScript(effect +: effects)
  val search: Dom.Element                                                    = Dom.Element.Generic("search", Chunk.empty, Chunk.empty)
  val section: Dom.Element                                                   = Dom.Element.Generic("section", Chunk.empty, Chunk.empty)
  val select: Dom.Element                                                    = Dom.Element.Generic("select", Chunk.empty, Chunk.empty)
  val slot: Dom.Element                                                      = Dom.Element.Generic("slot", Chunk.empty, Chunk.empty)
  val small: Dom.Element                                                     = Dom.Element.Generic("small", Chunk.empty, Chunk.empty)
  val source: Dom.Element                                                    = Dom.Element.Generic("source", Chunk.empty, Chunk.empty)
  val strong: Dom.Element                                                    = Dom.Element.Generic("strong", Chunk.empty, Chunk.empty)
  def style(): Dom.Element.Style                                             = elStyle(Seq.empty)
  def style(effect: DomModifier, effects: DomModifier*): Dom.Element.Style   = elStyle(effect +: effects)
  val sub: Dom.Element                                                       = Dom.Element.Generic("sub", Chunk.empty, Chunk.empty)
  val summary: Dom.Element                                                   = Dom.Element.Generic("summary", Chunk.empty, Chunk.empty)
  val sup: Dom.Element                                                       = Dom.Element.Generic("sup", Chunk.empty, Chunk.empty)
  val svg: Dom.Element                                                       = Dom.Element.Generic("svg", Chunk.empty, Chunk.empty)
  val table: Dom.Element                                                     = Dom.Element.Generic("table", Chunk.empty, Chunk.empty)
  val tbody: Dom.Element                                                     = Dom.Element.Generic("tbody", Chunk.empty, Chunk.empty)
  val td: Dom.Element                                                        = Dom.Element.Generic("td", Chunk.empty, Chunk.empty)
  val `template`: Dom.Element                                                = Dom.Element.Generic("template", Chunk.empty, Chunk.empty)
  val templateTag: Dom.Element                                               = Dom.Element.Generic("template", Chunk.empty, Chunk.empty)
  val textarea: Dom.Element                                                  = Dom.Element.Generic("textarea", Chunk.empty, Chunk.empty)
  val tfoot: Dom.Element                                                     = Dom.Element.Generic("tfoot", Chunk.empty, Chunk.empty)
  val th: Dom.Element                                                        = Dom.Element.Generic("th", Chunk.empty, Chunk.empty)
  val thead: Dom.Element                                                     = Dom.Element.Generic("thead", Chunk.empty, Chunk.empty)
  val time: Dom.Element                                                      = Dom.Element.Generic("time", Chunk.empty, Chunk.empty)
  val tr: Dom.Element                                                        = Dom.Element.Generic("tr", Chunk.empty, Chunk.empty)
  val track: Dom.Element                                                     = Dom.Element.Generic("track", Chunk.empty, Chunk.empty)
  val u: Dom.Element                                                         = Dom.Element.Generic("u", Chunk.empty, Chunk.empty)
  val ul: Dom.Element                                                        = Dom.Element.Generic("ul", Chunk.empty, Chunk.empty)
  val `var`: Dom.Element                                                     = Dom.Element.Generic("var", Chunk.empty, Chunk.empty)
  val varTag: Dom.Element                                                    = Dom.Element.Generic("var", Chunk.empty, Chunk.empty)
  val video: Dom.Element                                                     = Dom.Element.Generic("video", Chunk.empty, Chunk.empty)
  val wbr: Dom.Element                                                       = Dom.Element.Generic("wbr", Chunk.empty, Chunk.empty)
  def element(tag: String): Dom.Element                                      = Dom.Element.Generic(tag, Chunk.empty, Chunk.empty)

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

  def multiAttr(name: String): MultiAttributeKey                                                 = Dom.multiAttr(name)
  def multiAttr(name: String, separator: Dom.AttributeSeparator): MultiAttributeKey              = Dom.multiAttr(name, separator)
  def multiAttr(name: String, values: Iterable[String]): Dom.Attribute                           = Dom.multiAttr(name, values)
  def multiAttr(name: String, separator: Dom.AttributeSeparator, values: String*): Dom.Attribute =
    Dom.multiAttr(name, separator, values: _*)

  // --- DOM helper functions ---

  val empty: Dom = Dom.Empty

  def aria(name: String): AttributeKey     = new AttributeKey("aria-" + name)
  def dataAttr(name: String): AttributeKey = new AttributeKey("data-" + name)
  def attr(name: String): AttributeKey     = new AttributeKey(name)
}
