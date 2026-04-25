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
import zio.test._

object HtmlElementsSpec extends ZIOSpecDefault {
  def spec = suite("HtmlElements")(
    suite("element constructors")(
      test("div renders empty div") {
        assertTrue(div.render == "<div></div>")
      },
      test("div with string modifier") {
        val result = div("hello").render
        assertTrue(result == "<div>hello</div>")
      },
      test("div with attribute") {
        val result = div(id := "main").render
        assertTrue(result == """<div id="main"></div>""")
      },
      test("div with multiple attributes and content") {
        val result = div(id := "main", className := "box", "content").render
        assertTrue(
          result == """<div id="main" class="box">content</div>"""
        )
      },
      test("nested elements") {
        val result = ul(li("one"), li("two")).render
        assertTrue(result == "<ul><li>one</li><li>two</li></ul>")
      },
      test("void elements self-close") {
        assertTrue(
          br.render == "<br/>",
          hr.render == "<hr/>"
        )
      },
      test("input as void element") {
        assertTrue(input.render == "<input/>")
      },
      test("img as void element with attributes") {
        val result = img(src := "pic.png", alt := "photo").render
        assertTrue(
          result == """<img src="pic.png" alt="photo"/>"""
        )
      }
    ),
    suite("boolean attributes")(
      test("required as boolean attribute via BooleanAttribute") {
        val result = input(required).render
        assertTrue(result == "<input required/>")
      },
      test("disabled as boolean attribute") {
        val result = input(disabled).render
        assertTrue(result == "<input disabled/>")
      },
      test("BooleanAttribute enabled=true renders") {
        val result = input(Dom.boolAttr("required", enabled = true)).render
        assertTrue(result == "<input required/>")
      },
      test("BooleanAttribute enabled=false omits attribute") {
        val result = input(Dom.boolAttr("required", enabled = false)).render
        assertTrue(result == "<input/>")
      }
    ),
    suite("attribute helpers")(
      test("class attribute via className") {
        val result = div(className := "container").render
        assertTrue(result == """<div class="container"></div>""")
      },
      test("href attribute") {
        val result = a(href := "https://example.com", "link").render
        assertTrue(result == """<a href="https://example.com">link</a>""")
      },
      test("multi-value attribute via Chunk") {
        val result = div(className := Chunk("a", "b", "c")).render
        assertTrue(result == """<div class="a b c"></div>""")
      },
      test("multi-value attribute via varargs tuple") {
        val result = div(className.:=("a", "b", "c")).render
        assertTrue(result == """<div class="a b c"></div>""")
      }
    ),
    suite("modifiers")(
      test("Dom as modifier adds child") {
        val child: Dom = Dom.Text("child")
        val result     = div(child).render
        assertTrue(result == "<div>child</div>")
      },
      test("Option[Dom] None is no-op") {
        val none: Option[Dom] = None
        val result            = div(none).render
        assertTrue(result == "<div></div>")
      },
      test("Option[String] Some applies modifier") {
        val some: Option[String] = Some("text")
        val result               = div(some).render
        assertTrue(result == "<div>text</div>")
      },
      test("Iterable[String] applies all") {
        val mods: Iterable[String] = List("a", "b")
        val result                 = div(mods).render
        assertTrue(result == "<div>ab</div>")
      },
      test("Iterable[Dom] applies all children") {
        val mods: Iterable[Dom] = List(Dom.Text("x"), Dom.Text("y"))
        val result              = div(mods).render
        assertTrue(result == "<div>xy</div>")
      },
      test("Int as modifier renders as text") {
        assertTrue(div(42).render == "<div>42</div>")
      },
      test("Long as modifier renders as text") {
        assertTrue(div(123L).render == "<div>123</div>")
      },
      test("Double as modifier renders as text") {
        assertTrue(div(3.14).render == "<div>3.14</div>")
      },
      test("Float as modifier renders as text") {
        assertTrue(div(2.5f).render == "<div>2.5</div>")
      },
      test("Boolean as modifier renders as text") {
        assertTrue(div(true).render == "<div>true</div>")
      },
      test("Char as modifier renders as text") {
        assertTrue(div('x').render == "<div>x</div>")
      },
      test("Array as modifier renders children") {
        val items = Array("a", "b", "c")
        assertTrue(ul(items.map(i => li(i))).render == "<ul><li>a</li><li>b</li><li>c</li></ul>")
      },
      test("empty iterable renders nothing") {
        assertTrue(div(List.empty[String]).render == "<div></div>")
      },
      test("single-element list delegates to element") {
        assertTrue(div(List("hello")).render == "<div>hello</div>")
      }
    ),

    suite("various elements")(
      test("h1 through h6") {
        val r1 = h1("Title").render
        val r2 = h2("Sub").render
        val r3 = h3("Sub2").render
        assertTrue(
          r1 == "<h1>Title</h1>",
          r2 == "<h2>Sub</h2>",
          r3 == "<h3>Sub2</h3>"
        )
      },
      test("p element") {
        val result = p("paragraph").render
        assertTrue(result == "<p>paragraph</p>")
      },
      test("span element") {
        val result = span("inline").render
        assertTrue(result == "<span>inline</span>")
      },
      test("table structure") {
        val result = table(tr(td("cell"))).render
        assertTrue(result == "<table><tr><td>cell</td></tr></table>")
      }
    ),
    suite("script and style elements")(
      test("script() returns Script type") {
        val s: Dom.Element.Script = script()
        assertTrue(s.tag == "script")
      },
      test("style() returns Style type") {
        val s: Dom.Element.Style = style()
        assertTrue(s.tag == "style")
      },
      test("script renders JS without escaping") {
        assertTrue(script("var x = 1 < 2;").render == "<script>var x = 1 < 2;</script>")
      },
      test("style renders CSS without escaping") {
        assertTrue(style("div > p { color: red; }").render == "<style>div > p { color: red; }</style>")
      },
      test("script with attributes") {
        assertTrue(script(src := "app.js").render == """<script src="app.js"></script>""")
      },
      test("script inlineJs convenience") {
        val s = script().inlineJs("alert('hello')")
        assertTrue(s.render == "<script>alert('hello')</script>")
      },
      test("script externalJs convenience") {
        val s = script().externalJs("app.js")
        assertTrue(s.render == """<script src="app.js"></script>""")
      },
      test("style inlineCss convenience") {
        val s = style().inlineCss("body { margin: 0; }")
        assertTrue(s.render == "<style>body { margin: 0; }</style>")
      },
      test("script inlineJs with Js type") {
        val s = script().inlineJs(Js("console.log(1)"))
        assertTrue(s.render == "<script>console.log(1)</script>")
      },
      test("style inlineCss with Css type") {
        val s = style().inlineCss(Css("a > b { color: blue; }"))
        assertTrue(s.render == "<style>a > b { color: blue; }</style>")
      }
    ),
    suite("script/style modifier type coercion")(
      test("script with Dom.Text child preserves as Script") {
        val genericChild: Dom = Dom.Text("code")
        val s                 = script(genericChild)
        assertTrue(s.tag == "script", s.render == "<script>code</script>")
      },
      test("style with Dom.Text child preserves as Style") {
        val genericChild: Dom = Dom.Text("css")
        val s                 = style(genericChild)
        assertTrue(s.tag == "style", s.render == "<style>css</style>")
      },
      test("element with no modifiers creates empty element") {
        val el = element("custom")
        assertTrue(el.render == "<custom></custom>")
      }
    ),
    suite("empty helper")(
      test("empty renders nothing") {
        assertTrue(empty.render == "")
      }
    ),
    suite("ARIA multi-value attributes")(
      test("ariaDescribedby with multiple values") {
        val result = div(ariaDescribedby.:=("desc1", "desc2")).render
        assertTrue(result == """<div aria-describedby="desc1 desc2"></div>""")
      },
      test("ariaLabelledby with multiple values") {
        val result = div(ariaLabelledby.:=("label1", "label2")).render
        assertTrue(result == """<div aria-labelledby="label1 label2"></div>""")
      },
      test("ariaDescribedby with single value") {
        val result = div(ariaDescribedby := "desc1").render
        assertTrue(result == """<div aria-describedby="desc1"></div>""")
      }
    ),
    suite("multiAttr helpers")(
      test("multiAttr creates multi-value attribute") {
        val cls    = multiAttr("class")
        val result = div(cls("container", "fluid")).render
        assertTrue(result == """<div class="container fluid"></div>""")
      },
      test("multiAttr with custom separator") {
        val styles = multiAttr("style", Dom.AttributeSeparator.Semicolon)
        val result = div(styles("color: red", "font-size: 14px")).render
        assertTrue(result == """<div style="color: red;font-size: 14px"></div>""")
      }
    ),
    suite("Element apply for curried modifiers")(
      test("form with apply for additional modifiers") {
        val f        = form(action := "/submit")
        val result   = f(div("content"), button("Submit"))
        val rendered = result.render
        assertTrue(
          rendered == """<form action="/submit"><div>content</div><button>Submit</button></form>"""
        )
      }
    ),
    suite("uncommon HTML5 tags")(
      test("renders a representative sample of less-common tags") {
        val rAbbr       = abbr("abbr").render
        val rAddress    = address("addr").render
        val rBdi        = bdi("x").render
        val rBdo        = bdo("x").render
        val rDfn        = dfn("x").render
        val rDialog     = dialog("x").render
        val rFigcaption = figcaption("x").render
        val rKbd        = kbd("x").render
        val rMeter      = meter("x").render
        val rOutput     = output("x").render
        val rProgress   = progress("x").render
        val rRuby       = ruby("x").render
        val rSamp       = samp("x").render
        val rSearch     = search("x").render
        val rSlot       = slot("x").render
        val rTime       = time("x").render
        val rNoscript   = noscript("x").render
        val rMap        = map("x").render
        val rMath       = math("x").render
        val rData       = data("x").render
        val rDetails    = details("x").render
        val rDl         = dl(dt("term"), dd("def")).render
        val rFigure     = figure("x").render
        val rMark       = mark("x").render
        val rIns        = ins("x").render
        val rDel        = del("x").render
        val rRp         = rp("x").render
        val rRt         = rt("x").render
        assertTrue(
          rAbbr == "<abbr>abbr</abbr>",
          rAddress == "<address>addr</address>",
          rBdi == "<bdi>x</bdi>",
          rBdo == "<bdo>x</bdo>",
          datalist.render == "<datalist></datalist>",
          rDfn == "<dfn>x</dfn>",
          rDialog == "<dialog>x</dialog>",
          rFigcaption == "<figcaption>x</figcaption>",
          rKbd == "<kbd>x</kbd>",
          rMeter == "<meter>x</meter>",
          optgroup.render == "<optgroup></optgroup>",
          rOutput == "<output>x</output>",
          rProgress == "<progress>x</progress>",
          rRuby == "<ruby>x</ruby>",
          rSamp == "<samp>x</samp>",
          rSearch == "<search>x</search>",
          rSlot == "<slot>x</slot>",
          svg.render == "<svg></svg>",
          rTime == "<time>x</time>",
          rNoscript == "<noscript>x</noscript>",
          rMap == "<map>x</map>",
          rMath == "<math>x</math>",
          picture.render == "<picture></picture>",
          rData == "<data>x</data>",
          rDetails == "<details>x</details>",
          rDl == "<dl><dt>term</dt><dd>def</dd></dl>",
          rFigure == "<figure>x</figure>",
          rMark == "<mark>x</mark>",
          rIns == "<ins>x</ins>",
          rDel == "<del>x</del>",
          rRp == "<rp>x</rp>",
          rRt == "<rt>x</rt>"
        )
      },
      test("renders void element tags") {
        assertTrue(
          base.render == "<base/>",
          embed.render == "<embed/>",
          track.render == "<track/>",
          area.render == "<area/>",
          col.render == "<col/>",
          wbr.render == "<wbr/>",
          param.render == "<param/>",
          source.render == "<source/>"
        )
      },
      test("renders remaining common tags") {
        val rArticle    = article("x").render
        val rAside      = aside("x").render
        val rB          = b("x").render
        val rBlockquote = blockquote("x").render
        val rCaption    = caption("x").render
        val rCite       = cite("x").render
        val rCode       = code("x").render
        val rEm         = em("x").render
        val rFieldset   = fieldset("x").render
        val rFooter     = footer("x").render
        val rHeader     = header("x").render
        val rI          = i("x").render
        val rLabel      = label("x").render
        val rLegend     = legend("x").render
        val rNav        = nav("x").render
        val rOlLi       = ol(li("x")).render
        val rOption     = option("x").render
        val rPre        = pre("x").render
        val rQ          = q("x").render
        val rS          = s("x").render
        val rSection    = section("x").render
        val rSelectOpt  = select(option("x")).render
        val rSmall      = small("x").render
        val rStrong     = strong("x").render
        val rSub        = sub("x").render
        val rSummary    = summary("x").render
        val rSup        = sup("x").render
        val rTextarea   = textarea("x").render
        val rU          = u("x").render
        val rMainEl     = element("main")("x").render
        assertTrue(
          rArticle == "<article>x</article>",
          rAside == "<aside>x</aside>",
          audio.render == "<audio></audio>",
          rB == "<b>x</b>",
          rBlockquote == "<blockquote>x</blockquote>",
          canvas.render == "<canvas></canvas>",
          rCaption == "<caption>x</caption>",
          rCite == "<cite>x</cite>",
          rCode == "<code>x</code>",
          colgroup.render == "<colgroup></colgroup>",
          rEm == "<em>x</em>",
          rFieldset == "<fieldset>x</fieldset>",
          rFooter == "<footer>x</footer>",
          rHeader == "<header>x</header>",
          rI == "<i>x</i>",
          iframe.render == "<iframe></iframe>",
          rLabel == "<label>x</label>",
          rLegend == "<legend>x</legend>",
          link.render == "<link/>",
          rMainEl == "<main>x</main>",
          meta.render == "<meta/>",
          rNav == "<nav>x</nav>",
          rOlLi == "<ol><li>x</li></ol>",
          rOption == "<option>x</option>",
          rPre == "<pre>x</pre>",
          rQ == "<q>x</q>",
          rS == "<s>x</s>",
          rSection == "<section>x</section>",
          rSelectOpt == "<select><option>x</option></select>",
          rSmall == "<small>x</small>",
          rStrong == "<strong>x</strong>",
          rSub == "<sub>x</sub>",
          rSummary == "<summary>x</summary>",
          rSup == "<sup>x</sup>",
          tbody.render == "<tbody></tbody>",
          tfoot.render == "<tfoot></tfoot>",
          thead.render == "<thead></thead>",
          rTextarea == "<textarea>x</textarea>",
          rU == "<u>x</u>",
          video.render == "<video></video>"
        )
      }
    ),
    suite("backtick/alternative tag variants")(
      test("object and objectTag both render object element") {
        val rObj    = `object`("x").render
        val rObjTag = objectTag("x").render
        assertTrue(
          rObj == "<object>x</object>",
          rObjTag == "<object>x</object>"
        )
      },
      test("template and templateTag both render template element") {
        val rTpl    = `template`("x").render
        val rTplTag = templateTag("x").render
        assertTrue(
          rTpl == "<template>x</template>",
          rTplTag == "<template>x</template>"
        )
      },
      test("var and varTag both render var element") {
        val rVar    = `var`("x").render
        val rVarTag = varTag("x").render
        assertTrue(
          rVar == "<var>x</var>",
          rVarTag == "<var>x</var>"
        )
      }
    ),
    suite("custom element helper")(
      test("element creates custom-named elements") {
        val result = element("custom-tag")("content").render
        assertTrue(result == "<custom-tag>content</custom-tag>")
      },
      test("element with attributes") {
        val result = element("x-app")(id := "root").render
        assertTrue(result == """<x-app id="root"></x-app>""")
      }
    ),
    suite("additional attribute vals")(
      test("scope-related attributes") {
        val r1 = th(scopeAttr := "col").render
        val r2 = th(`scope` := "row").render
        assertTrue(
          r1 == """<th scope="col"></th>""",
          r2 == """<th scope="row"></th>"""
        )
      },
      test("for-related attributes") {
        val r1 = label(forAttr := "input1").render
        val r2 = label(`for` := "input1").render
        assertTrue(
          r1 == """<label for="input1"></label>""",
          r2 == """<label for="input1"></label>"""
        )
      },
      test("cite and span attributes") {
        val r1 = blockquote(citeAttr := "url").render
        val r2 = col(spanAttr := "2").render
        assertTrue(
          r1 == """<blockquote cite="url"></blockquote>""",
          r2 == """<col span="2"/>"""
        )
      },
      test("slot and summary attributes") {
        val r1 = div(slotAttr := "main").render
        val r2 = table(summaryAttr := "desc").render
        assertTrue(
          r1 == """<div slot="main"></div>""",
          r2 == """<table summary="desc"></table>"""
        )
      },
      test("form and label attribute aliases") {
        val r1 = input(formAttr := "myform").render
        val r2 = option(labelAttr := "opt").render
        assertTrue(
          r1 == """<input form="myform"/>""",
          r2 == """<option label="opt"></option>"""
        )
      },
      test("httpEquiv attribute") {
        val result = meta(httpEquiv := "refresh").render
        assertTrue(result == """<meta http-equiv="refresh"/>""")
      },
      test("xmlns attribute") {
        val result = html(xmlns := "http://www.w3.org/1999/xhtml").render
        assertTrue(result == """<html xmlns="http://www.w3.org/1999/xhtml"></html>""")
      },
      test("miscellaneous attributes") {
        val rInputAccept     = input(accept := "image/*").render
        val rDivAccesskey    = div(accesskey := "h").render
        val rScriptAsync     = script(async).render
        val rScriptDefer     = script(defer).render
        val rVideoAutoplay   = video(autoplay).render
        val rVideoControls   = video(controls).render
        val rVideoLoop       = video(loop).render
        val rVideoMuted      = video(muted).render
        val rImgLoading      = img(loading := "lazy").render
        val rMetaCharset     = meta(charset := "utf-8").render
        val rMetaContent     = meta(content := "text").render
        val rImgCrossorigin  = img(crossorigin := "anonymous").render
        val rTimeDatetime    = time(datetime := "2024-01-01").render
        val rDetailsOpen     = details(open).render
        val rInputList       = input(list := "opts").render
        val rFormNoValidate  = form(noValidate).render
        val rFormEncType     = form(encType := "multipart").render
        val rBtnFormAction   = button(formAction := "/go").render
        val rBtnFormMethod   = button(formMethod := "post").render
        val rBtnFormNoVal    = button(formNoValidate).render
        val rImgSrcSet       = img(srcSet := "a.png 1x").render
        val rImgSizes        = img(sizes := "100vw").render
        val rInputMinLen     = input(minLength := "3").render
        val rInputMaxLen     = input(maxLength := "10").render
        val rInputSize       = input(size := "20").render
        val rTextareaCols    = textarea(cols := "40").render
        val rTextareaRows    = textarea(rows := "5").render
        val rTextareaWrap    = textarea(wrap := "hard").render
        val rScriptIntegrity = script(integrity := "sha384-xxx").render
        val rImgReferrer     = img(referrerpolicy := "no-referrer").render
        val rOlReversed      = ol(reversed).render
        val rIframeSandbox   = iframe(sandbox := "allow-scripts").render
        val rDivSpellcheck   = div(spellcheck := "true").render
        val rDivTranslate    = div(translate := "yes").render
        val rVideoPoster     = video(poster := "img.png").render
        val rVideoPreload    = video(preload := "auto").render
        val rMeterHigh       = meter(high := "90").render
        val rMeterLow        = meter(low := "10").render
        val rMeterOptimum    = meter(optimum := "50").render
        val rAudioMedia      = audio(media := "all").render
        val rDivClass        = div(`class` := "x").render
        assertTrue(
          rInputAccept == """<input accept="image/*"/>""",
          rDivAccesskey == """<div accesskey="h"></div>""",
          rScriptAsync == "<script async></script>",
          rScriptDefer == "<script defer></script>",
          rVideoAutoplay == "<video autoplay></video>",
          rVideoControls == "<video controls></video>",
          rVideoLoop == "<video loop></video>",
          rVideoMuted == "<video muted></video>",
          rImgLoading == """<img loading="lazy"/>""",
          rMetaCharset == """<meta charset="utf-8"/>""",
          rMetaContent == """<meta content="text"/>""",
          rImgCrossorigin == """<img crossorigin="anonymous"/>""",
          rTimeDatetime == """<time datetime="2024-01-01"></time>""",
          rDetailsOpen == "<details open></details>",
          rInputList == """<input list="opts"/>""",
          rFormNoValidate == "<form novalidate></form>",
          rFormEncType == """<form enctype="multipart"></form>""",
          rBtnFormAction == """<button formaction="/go"></button>""",
          rBtnFormMethod == """<button formmethod="post"></button>""",
          rBtnFormNoVal == "<button formnovalidate></button>",
          rImgSrcSet == """<img srcset="a.png 1x"/>""",
          rImgSizes == """<img sizes="100vw"/>""",
          rInputMinLen == """<input minlength="3"/>""",
          rInputMaxLen == """<input maxlength="10"/>""",
          rInputSize == """<input size="20"/>""",
          rTextareaCols == """<textarea cols="40"></textarea>""",
          rTextareaRows == """<textarea rows="5"></textarea>""",
          rTextareaWrap == """<textarea wrap="hard"></textarea>""",
          rScriptIntegrity == """<script integrity="sha384-xxx"></script>""",
          rImgReferrer == """<img referrerpolicy="no-referrer"/>""",
          rOlReversed == "<ol reversed></ol>",
          rIframeSandbox == """<iframe sandbox="allow-scripts"></iframe>""",
          rDivSpellcheck == """<div spellcheck="true"></div>""",
          rDivTranslate == """<div translate="yes"></div>""",
          rVideoPoster == """<video poster="img.png"></video>""",
          rVideoPreload == """<video preload="auto"></video>""",
          rMeterHigh == """<meter high="90"></meter>""",
          rMeterLow == """<meter low="10"></meter>""",
          rMeterOptimum == """<meter optimum="50"></meter>""",
          rAudioMedia == """<audio media="all"></audio>""",
          rDivClass == """<div class="x"></div>"""
        )
      }
    ),
    suite("aria, dataAttr, attr helpers")(
      test("aria helper") {
        val result = div(aria("label") := "hi").render
        assertTrue(result == """<div aria-label="hi"></div>""")
      },
      test("dataAttr helper") {
        val result = div(dataAttr("id") := "42").render
        assertTrue(result == """<div data-id="42"></div>""")
      },
      test("attr helper") {
        val result = div(attr("custom") := "val").render
        assertTrue(result == """<div custom="val"></div>""")
      }
    ),
    suite("multiAttr with Iterable overload")(
      test("multiAttr with Iterable[String] creates attribute") {
        val a  = multiAttr("class", List("a", "b"))
        val el = Dom.Element.Generic("div", Chunk(a), Chunk.empty)
        assertTrue(el.render == """<div class="a b"></div>""")
      },
      test("multiAttr with separator and varargs") {
        val a  = multiAttr("style", Dom.AttributeSeparator.Semicolon, "color: red", "font-size: 14px")
        val el = Dom.Element.Generic("div", Chunk(a), Chunk.empty)
        assertTrue(el.render == """<div style="color: red;font-size: 14px"></div>""")
      }
    ),
    suite("when and whenSome on elements")(
      test("when true with multiple modifiers applies all") {
        val el = div.when(true)(
          id := "x",
          "text"
        )
        assertTrue(el.render == """<div id="x">text</div>""")
      },
      test("when false with multiple modifiers returns unchanged") {
        val el = div.when(false)(
          id := "x",
          "text"
        )
        assertTrue(el.render == "<div></div>")
      },
      test("whenSome with Some applies all modifiers from function") {
        val el = div.whenSome(Some(42)) { n =>
          Seq(ToModifier.mod(n.toString))
        }
        assertTrue(el.render == "<div>42</div>")
      },
      test("whenSome with None returns unchanged element") {
        val el = div.whenSome(Option.empty[Int]) { n =>
          Seq(ToModifier.mod(n.toString))
        }
        assertTrue(el.render == "<div></div>")
      },
      test("when true with one modifier applies it") {
        val el = div("content")
        val r  = el.when(true)(id := "test")
        assertTrue(r.render == """<div id="test">content</div>""")
      },
      test("whenSome with Some and multiple modifiers") {
        val el = span.whenSome(Some("cls")) { cls =>
          Seq(
            ToModifier.mod(className := cls),
            ToModifier.mod("inner")
          )
        }
        assertTrue(el.render == """<span class="cls">inner</span>""")
      }
    ),
    suite("class attribute accumulation via DSL")(
      test("className := overrides (last wins)") {
        val result = div(className := "a", className := "b").render
        assertTrue(result == """<div class="b"></div>""")
      },
      test("className += accumulates") {
        val result = div(className += "a", className += "b").render
        assertTrue(result == """<div class="a b"></div>""")
      },
      test("className += with when(true) accumulates") {
        val result = div(className += "a").when(true)(className += "b").render
        assertTrue(result == """<div class="a b"></div>""")
      },
      test("div with className and when(false) keeps single class") {
        val result = div(className += "a").when(false)(className += "b").render
        assertTrue(result == """<div class="a"></div>""")
      },
      test("className += triple accumulate") {
        val result = div(className += "a", className += "b", className += "c").render
        assertTrue(result == """<div class="a b c"></div>""")
      },
      test("className := then += appends to override") {
        val result = div(className := "base", className += "extra").render
        assertTrue(result == """<div class="base extra"></div>""")
      },
      test("+= without prior := works") {
        val result = div(className += "only").render
        assertTrue(result == """<div class="only"></div>""")
      }
    ),
    suite("element constructor edge cases")(
      test("script with Js inlineJs") {
        val s = script().inlineJs(Js("var x = 1 < 2;"))
        assertTrue(s.render == "<script>var x = 1 < 2;</script>")
      },
      test("script externalJs with path") {
        val s = script().externalJs("/assets/main.js")
        assertTrue(s.render == """<script src="/assets/main.js"></script>""")
      },
      test("style inlineCss with Css ADT") {
        val c = Css("body { margin: 0 }")
        val s = style().inlineCss(c)
        assertTrue(s.render == "<style>body { margin: 0 }</style>")
      },
      test("meta charset renders correctly") {
        val m = meta(charset := "utf-8")
        assertTrue(m.render == """<meta charset="utf-8"/>""")
      },
      test("link with rel and href") {
        val l = link(rel := "stylesheet", href := "/style.css")
        assertTrue(
          l.render == """<link rel="stylesheet" href="/style.css"/>"""
        )
      },
      test("element helper with no modifiers creates empty element") {
        val el = element("x-widget")
        assertTrue(el.render == "<x-widget></x-widget>")
      },
      test("element helper is a generic element") {
        val el = element("section")("content")
        assertTrue(el.render == "<section>content</section>")
      }
    ),
    suite("HtmlElements constructor with script/style type conversions")(
      test("script with string modifier creates Script element") {
        val s = script("console.log(1)")
        assertTrue(s.tag == "script", s.render == "<script>console.log(1)</script>")
      },
      test("script with attribute modifier creates Script element") {
        val s = script(src := "app.js")
        assertTrue(s.tag == "script", s.render == """<script src="app.js"></script>""")
      },
      test("style with string modifier creates Style element") {
        val s = style(".cls { color: red }")
        assertTrue(s.tag == "style", s.render == "<style>.cls { color: red }</style>")
      },
      test("script with multiple modifiers") {
        val s = script(`type` := "module", "import x from 'y';")
        assertTrue(
          s.render == """<script type="module">import x from 'y';</script>"""
        )
      }
    ),
    suite("inline JS")(
      test("script().inlineJs with simple alert") {
        val result = script().inlineJs(Js("alert('hello')")).render
        assertTrue(result == "<script>alert('hello')</script>")
      },
      test("script().inlineJs with datastar expression") {
        val result = script().inlineJs(Js("$count++")).render
        assertTrue(result == "<script>$count++</script>")
      },
      test("script().inlineJs does not escape HTML special chars") {
        val result = script().inlineJs(Js("if (x < 10 && y > 5) {}")).render
        assertTrue(
          result == "<script>if (x < 10 && y > 5) {}</script>"
        )
      },
      test("script().externalJs with path") {
        val result = script().externalJs("/app.js").render
        assertTrue(result == """<script src="/app.js"></script>""")
      },
      test("script with type attribute and inlineJs") {
        val result = script(`type` := "module").inlineJs(Js("import { x } from './mod.js'")).render
        assertTrue(
          result == """<script type="module">import { x } from './mod.js'</script>"""
        )
      },
      test("script().inlineJs with multiline JS") {
        val code   = """function add(a, b) {
  return a + b;
"}"""
        val result = script().inlineJs(Js(code)).render
        assertTrue(result == "<script>" + code + "</script>")
      },
      test("Js constructor preserves content") {
        val js = Js("var x = 1; console.log(x);").toString()
        assertTrue(js == "var x = 1; console.log(x);")
      }
    ),
    suite("inline CSS")(
      test("style().inlineCss with Css.Raw") {
        val result = style().inlineCss(Css.Raw("body { margin: 0; }")).render
        assertTrue(result == "<style>body { margin: 0; }</style>")
      },
      test("style().inlineCss with Css.Rule containing declarations") {
        val rule = Css.Rule(
          CssSelector.Element("div"),
          Chunk(
            Css.Declaration("color", "red"),
            Css.Declaration("margin", "10px")
          )
        )
        val result = style().inlineCss(rule).render
        assertTrue(
          result == "<style>div{color:red;margin:10px;}</style>"
        )
      },
      test("style().inlineCss with Css.Sheet containing multiple rules") {
        val rule1  = Css.Rule(CssSelector.Element("body"), Chunk(Css.Declaration("margin", "0")))
        val rule2  = Css.Rule(CssSelector.Element("p"), Chunk(Css.Declaration("color", "blue")))
        val sheet  = Css.Sheet(Chunk(rule1, rule2))
        val result = style().inlineCss(sheet).render
        assertTrue(
          result == "<style>body{margin:0;}p{color:blue;}</style>"
        )
      },
      test("style().inlineCss does not escape HTML special chars") {
        val result = style().inlineCss(Css.Raw("div > p { color: red; }")).render
        assertTrue(
          result == "<style>div > p { color: red; }</style>"
        )
      },
      test("style with media attribute and inlineCss") {
        val result = style(media := "screen").inlineCss(Css.Raw("body { margin: 0; }")).render
        assertTrue(
          result == """<style media="screen">body { margin: 0; }</style>"""
        )
      }
    ),
    suite("interpolator + inline integration")(
      test("js interpolator with script().inlineJs") {
        val name   = "World"
        val result = script().inlineJs(js"console.log(\"$name\")").render
        assertTrue(result == """<script>console.log(\""World"\")</script>""")
      },
      test("css interpolator with style().inlineCss") {
        val color  = "red"
        val result = style().inlineCss(css"body { color: $color; }").render
        assertTrue(result == "<style>body { color: red; }</style>")
      },
      test("css interpolator zero-arg with inlineCss") {
        val result = style().inlineCss(css"body { margin: 0; }").render
        assertTrue(result == "<style>body { margin: 0; }</style>")
      }
    ),
    suite("script and style with AddChildren")(
      test("script with Seq[Dom] children modifier") {
        val children: Seq[Dom] = Seq(Dom.Text("var a = 1;"), Dom.Text("var b = 2;"))
        val result             = script(children).render
        assertTrue(result == "<script>var a = 1;var b = 2;</script>")
      },
      test("style with Seq[Dom] children modifier") {
        val children: Seq[Dom] = Seq(Dom.Text("body {}"), Dom.Text("p {}"))
        val result             = style(children).render
        assertTrue(result == "<style>body {}p {}</style>")
      }
    )
  )
}
