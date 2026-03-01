package zio.blocks.template

import zio.test._

object HtmlElementsSpec extends ZIOSpecDefault {
  def spec = suite("HtmlElements")(
    suite("element constructors")(
      test("div renders empty div") {
        assertTrue(div().render == "<div></div>")
      },
      test("div with string modifier") {
        assertTrue(div("hello").render == "<div>hello</div>")
      },
      test("div with attribute") {
        assertTrue(div(id := "main").render == "<div id=\"main\"></div>")
      },
      test("div with multiple attributes and content") {
        val result = div(id := "main", className := "box", "content").render
        assertTrue(
          result.contains("id=\"main\""),
          result.contains("class=\"box\""),
          result.contains("content"),
          result.startsWith("<div"),
          result.endsWith("</div>")
        )
      },
      test("nested elements") {
        assertTrue(ul(li("one"), li("two")).render == "<ul><li>one</li><li>two</li></ul>")
      },
      test("void elements self-close") {
        assertTrue(
          br().render == "<br/>",
          hr().render == "<hr/>"
        )
      },
      test("input as void element") {
        assertTrue(input().render == "<input/>")
      },
      test("img as void element with attributes") {
        val result = img(src := "pic.png", alt := "photo").render
        assertTrue(
          result.startsWith("<img"),
          result.contains("src=\"pic.png\""),
          result.contains("alt=\"photo\""),
          result.endsWith("/>")
        )
      }
    ),
    suite("boolean attributes")(
      test("required as boolean attribute via PartialAttribute") {
        assertTrue(input(required).render == "<input required/>")
      },
      test("disabled as boolean attribute") {
        assertTrue(input(disabled).render == "<input disabled/>")
      },
      test("required with := true") {
        assertTrue(input(required := true).render == "<input required/>")
      },
      test("required with := false omits attribute") {
        assertTrue(input(required := false).render == "<input/>")
      }
    ),
    suite("attribute helpers")(
      test("class attribute via className") {
        assertTrue(div(className := "container").render == "<div class=\"container\"></div>")
      },
      test("href attribute") {
        assertTrue(
          a(href := "https://example.com", "link").render == "<a href=\"https://example.com\">link</a>"
        )
      },
      test("multi-value attribute via Vector") {
        val result = div(className := Vector("a", "b", "c")).render
        assertTrue(result == "<div class=\"a b c\"></div>")
      },
      test("multi-value attribute via varargs tuple") {
        val result = div(className.:=("a", "b", "c")).render
        assertTrue(result == "<div class=\"a b c\"></div>")
      }
    ),
    suite("modifiers")(
      test("Dom as modifier adds child") {
        val child  = Dom.Text("child")
        val result = div(Modifier.domToModifier(child)).render
        assertTrue(result == "<div>child</div>")
      },
      test("Option[Modifier] None is no-op") {
        val none: Option[Modifier] = None
        assertTrue(div(none).render == "<div></div>")
      },
      test("Option[Modifier] Some applies modifier") {
        val some: Option[Modifier] = Some(Modifier.stringToModifier("text"))
        assertTrue(div(some).render == "<div>text</div>")
      },
      test("Iterable[Modifier] applies all") {
        val mods: Iterable[Modifier] = List(
          Modifier.stringToModifier("a"),
          Modifier.stringToModifier("b")
        )
        assertTrue(div(mods).render == "<div>ab</div>")
      }
    ),
    suite("various elements")(
      test("h1 through h6") {
        assertTrue(
          h1("Title").render == "<h1>Title</h1>",
          h2("Sub").render == "<h2>Sub</h2>",
          h3("Sub2").render == "<h3>Sub2</h3>"
        )
      },
      test("p element") {
        assertTrue(p("paragraph").render == "<p>paragraph</p>")
      },
      test("span element") {
        assertTrue(span("inline").render == "<span>inline</span>")
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
    suite("raw, fragment, empty helpers")(
      test("raw renders unescaped HTML") {
        assertTrue(raw("<b>bold</b>").render == "<b>bold</b>")
      },
      test("fragment combines children") {
        assertTrue(fragment(Dom.Text("a"), Dom.Text("b")).render == "ab")
      },
      test("empty renders nothing") {
        assertTrue(empty.render == "")
      }
    ),
    suite("ARIA multi-value attributes")(
      test("ariaDescribedby with multiple values") {
        val result = div(ariaDescribedby.:=("desc1", "desc2")).render
        assertTrue(result == "<div aria-describedby=\"desc1 desc2\"></div>")
      },
      test("ariaLabelledby with multiple values") {
        val result = div(ariaLabelledby.:=("label1", "label2")).render
        assertTrue(result == "<div aria-labelledby=\"label1 label2\"></div>")
      },
      test("ariaDescribedby with single value") {
        val result = div(ariaDescribedby := "desc1").render
        assertTrue(result == "<div aria-describedby=\"desc1\"></div>")
      }
    ),
    suite("multiAttr helpers")(
      test("multiAttr creates multi-value attribute") {
        val cls    = multiAttr("class")
        val result = div(cls("container", "fluid")).render
        assertTrue(result == "<div class=\"container fluid\"></div>")
      },
      test("multiAttr with custom separator") {
        val styles = multiAttr("style", Dom.AttributeSeparator.Semicolon)
        val result = div(styles("color: red", "font-size: 14px")).render
        assertTrue(result == "<div style=\"color: red;font-size: 14px\"></div>")
      }
    ),
    suite("Element apply for curried modifiers")(
      test("form with apply for additional modifiers") {
        val f      = form(action := "/submit")
        val result = f(Modifier.domToModifier(div("content")), Modifier.domToModifier(button("Submit")))
        assertTrue(
          result.render.contains("action=\"/submit\""),
          result.render.contains("<div>content</div>"),
          result.render.contains("<button>Submit</button>")
        )
      }
    ),
    suite("uncommon HTML5 tags")(
      test("renders a representative sample of less-common tags") {
        assertTrue(
          abbr("abbr").render == "<abbr>abbr</abbr>",
          address("addr").render == "<address>addr</address>",
          bdi("x").render == "<bdi>x</bdi>",
          bdo("x").render == "<bdo>x</bdo>",
          datalist().render == "<datalist></datalist>",
          dfn("x").render == "<dfn>x</dfn>",
          dialog("x").render == "<dialog>x</dialog>",
          figcaption("x").render == "<figcaption>x</figcaption>",
          kbd("x").render == "<kbd>x</kbd>",
          meter("x").render == "<meter>x</meter>",
          optgroup().render == "<optgroup></optgroup>",
          output("x").render == "<output>x</output>",
          progress("x").render == "<progress>x</progress>",
          ruby("x").render == "<ruby>x</ruby>",
          samp("x").render == "<samp>x</samp>",
          search("x").render == "<search>x</search>",
          slot("x").render == "<slot>x</slot>",
          svg().render == "<svg></svg>",
          time("x").render == "<time>x</time>",
          noscript("x").render == "<noscript>x</noscript>",
          map("x").render == "<map>x</map>",
          math("x").render == "<math>x</math>",
          picture().render == "<picture></picture>",
          data("x").render == "<data>x</data>",
          details("x").render == "<details>x</details>",
          dl(dt("term"), dd("def")).render == "<dl><dt>term</dt><dd>def</dd></dl>",
          figure("x").render == "<figure>x</figure>",
          mark("x").render == "<mark>x</mark>",
          ins("x").render == "<ins>x</ins>",
          del("x").render == "<del>x</del>",
          rp("x").render == "<rp>x</rp>",
          rt("x").render == "<rt>x</rt>"
        )
      },
      test("renders void element tags") {
        assertTrue(
          base().render == "<base/>",
          embed().render == "<embed/>",
          track().render == "<track/>",
          area().render == "<area/>",
          col().render == "<col/>",
          wbr().render == "<wbr/>",
          param().render == "<param/>",
          source().render == "<source/>"
        )
      },
      test("renders remaining common tags") {
        assertTrue(
          article("x").render == "<article>x</article>",
          aside("x").render == "<aside>x</aside>",
          audio().render == "<audio></audio>",
          b("x").render == "<b>x</b>",
          blockquote("x").render == "<blockquote>x</blockquote>",
          canvas().render == "<canvas></canvas>",
          caption("x").render == "<caption>x</caption>",
          cite("x").render == "<cite>x</cite>",
          code("x").render == "<code>x</code>",
          colgroup().render == "<colgroup></colgroup>",
          em("x").render == "<em>x</em>",
          fieldset("x").render == "<fieldset>x</fieldset>",
          footer("x").render == "<footer>x</footer>",
          header("x").render == "<header>x</header>",
          i("x").render == "<i>x</i>",
          iframe().render == "<iframe></iframe>",
          label("x").render == "<label>x</label>",
          legend("x").render == "<legend>x</legend>",
          link().render == "<link/>",
          element("main", "x").render == "<main>x</main>",
          meta().render == "<meta/>",
          nav("x").render == "<nav>x</nav>",
          ol(li("x")).render == "<ol><li>x</li></ol>",
          option("x").render == "<option>x</option>",
          pre("x").render == "<pre>x</pre>",
          q("x").render == "<q>x</q>",
          s("x").render == "<s>x</s>",
          section("x").render == "<section>x</section>",
          select(option("x")).render == "<select><option>x</option></select>",
          small("x").render == "<small>x</small>",
          strong("x").render == "<strong>x</strong>",
          sub("x").render == "<sub>x</sub>",
          summary("x").render == "<summary>x</summary>",
          sup("x").render == "<sup>x</sup>",
          tbody().render == "<tbody></tbody>",
          tfoot().render == "<tfoot></tfoot>",
          thead().render == "<thead></thead>",
          textarea("x").render == "<textarea>x</textarea>",
          u("x").render == "<u>x</u>",
          video().render == "<video></video>"
        )
      }
    ),
    suite("backtick/alternative tag variants")(
      test("object and objectTag both render object element") {
        assertTrue(
          `object`("x").render == "<object>x</object>",
          objectTag("x").render == "<object>x</object>"
        )
      },
      test("template and templateTag both render template element") {
        assertTrue(
          `template`("x").render == "<template>x</template>",
          templateTag("x").render == "<template>x</template>"
        )
      },
      test("var and varTag both render var element") {
        assertTrue(
          `var`("x").render == "<var>x</var>",
          varTag("x").render == "<var>x</var>"
        )
      }
    ),
    suite("custom element helper")(
      test("element creates custom-named elements") {
        assertTrue(element("custom-tag", "content").render == "<custom-tag>content</custom-tag>")
      },
      test("element with attributes") {
        assertTrue(element("x-app", id := "root").render == "<x-app id=\"root\"></x-app>")
      }
    ),
    suite("additional attribute vals")(
      test("scope-related attributes") {
        assertTrue(
          th(scopeAttr := "col").render == "<th scope=\"col\"></th>",
          th(`scope` := "row").render == "<th scope=\"row\"></th>"
        )
      },
      test("for-related attributes") {
        assertTrue(
          label(forAttr := "input1").render == "<label for=\"input1\"></label>",
          label(`for` := "input1").render == "<label for=\"input1\"></label>"
        )
      },
      test("cite and span attributes") {
        assertTrue(
          blockquote(citeAttr := "url").render == "<blockquote cite=\"url\"></blockquote>",
          col(spanAttr := "2").render == "<col span=\"2\"/>"
        )
      },
      test("slot and summary attributes") {
        assertTrue(
          div(slotAttr := "main").render == "<div slot=\"main\"></div>",
          table(summaryAttr := "desc").render == "<table summary=\"desc\"></table>"
        )
      },
      test("form and label attribute aliases") {
        assertTrue(
          input(formAttr := "myform").render == "<input form=\"myform\"/>",
          option(labelAttr := "opt").render == "<option label=\"opt\"></option>"
        )
      },
      test("httpEquiv attribute") {
        assertTrue(meta(httpEquiv := "refresh").render == "<meta http-equiv=\"refresh\"/>")
      },
      test("xmlns attribute") {
        assertTrue(html(xmlns := "http://www.w3.org/1999/xhtml").render.contains("xmlns=\""))
      },
      test("miscellaneous attributes") {
        assertTrue(
          input(accept := "image/*").render.contains("accept=\"image/*\""),
          div(accesskey := "h").render.contains("accesskey=\"h\""),
          script(async).render.contains("async"),
          script(defer).render.contains("defer"),
          video(autoplay).render.contains("autoplay"),
          video(controls).render.contains("controls"),
          video(loop).render.contains("loop"),
          video(muted).render.contains("muted"),
          img(loading := "lazy").render.contains("loading=\"lazy\""),
          meta(charset := "utf-8").render.contains("charset=\"utf-8\""),
          meta(content := "text").render.contains("content=\"text\""),
          img(crossorigin := "anonymous").render.contains("crossorigin="),
          time(datetime := "2024-01-01").render.contains("datetime="),
          details(open).render.contains("open"),
          input(list := "opts").render.contains("list=\"opts\""),
          form(noValidate).render.contains("novalidate"),
          form(encType := "multipart").render.contains("enctype="),
          button(formAction := "/go").render.contains("formaction="),
          button(formMethod := "post").render.contains("formmethod="),
          button(formNoValidate).render.contains("formnovalidate"),
          img(srcSet := "a.png 1x").render.contains("srcset="),
          img(sizes := "100vw").render.contains("sizes="),
          input(minLength := "3").render.contains("minlength="),
          input(maxLength := "10").render.contains("maxlength="),
          input(size := "20").render.contains("size="),
          textarea(cols := "40").render.contains("cols="),
          textarea(rows := "5").render.contains("rows="),
          textarea(wrap := "hard").render.contains("wrap="),
          script(integrity := "sha384-xxx").render.contains("integrity="),
          img(referrerpolicy := "no-referrer").render.contains("referrerpolicy="),
          ol(reversed).render.contains("reversed"),
          iframe(sandbox := "allow-scripts").render.contains("sandbox="),
          div(spellcheck := "true").render.contains("spellcheck="),
          div(translate := "yes").render.contains("translate="),
          video(poster := "img.png").render.contains("poster="),
          video(preload := "auto").render.contains("preload="),
          meter(high := "90").render.contains("high="),
          meter(low := "10").render.contains("low="),
          meter(optimum := "50").render.contains("optimum="),
          audio(media := "all").render.contains("media="),
          div(`class` := "x").render.contains("class=")
        )
      }
    ),
    suite("aria, dataAttr, attr helpers")(
      test("aria helper") {
        assertTrue(div(aria("label") := "hi").render == "<div aria-label=\"hi\"></div>")
      },
      test("dataAttr helper") {
        assertTrue(div(dataAttr("id") := "42").render == "<div data-id=\"42\"></div>")
      },
      test("attr helper") {
        assertTrue(div(attr("custom") := "val").render == "<div custom=\"val\"></div>")
      }
    ),
    suite("multiAttr with Iterable overload")(
      test("multiAttr with Iterable[String] creates attribute") {
        val a  = multiAttr("class", List("a", "b"))
        val el = Dom.Element.Generic("div", Vector(a), Vector.empty)
        assertTrue(el.render == "<div class=\"a b\"></div>")
      },
      test("multiAttr with separator and varargs") {
        val a  = multiAttr("style", Dom.AttributeSeparator.Semicolon, "color: red", "font-size: 14px")
        val el = Dom.Element.Generic("div", Vector(a), Vector.empty)
        assertTrue(el.render == "<div style=\"color: red;font-size: 14px\"></div>")
      }
    )
  )
}
