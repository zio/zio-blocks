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

object ContentModelsSpec extends ZIOSpecDefault {
  def spec = suite("ContentModels")(
    // --- List content model ---
    suite("list elements")(
      test("li returns Li accepted by ul") {
        val result = ul(li("a"), li("b")).render
        assertTrue(result == "<ul><li>a</li><li>b</li></ul>")
      },
      test("li returns Li accepted by ol") {
        val result = ol(li("1"), li("2")).render
        assertTrue(result == "<ol><li>1</li><li>2</li></ol>")
      },
      test("li with attributes") {
        val result = li(id := "item1", "text").render
        assertTrue(result == """<li id="item1">text</li>""")
      },
      test("ul mixes attributes and li children") {
        val result = ul(id := "list", className := "items", li("a")).render
        assertTrue(result == """<ul id="list" class="items"><li>a</li></ul>""")
      },
      test("ul from Iterable of li") {
        val items  = List(li("a"), li("b"))
        val result = ul(items).render
        assertTrue(result == "<ul><li>a</li><li>b</li></ul>")
      },
      test("ol from Iterable of li") {
        val items  = Vector(li("1"), li("2"))
        val result = ol(items).render
        assertTrue(result == "<ol><li>1</li><li>2</li></ol>")
      },
      test("empty ul and ol") {
        assertTrue(ul().render == "<ul></ul>") &&
        assertTrue(ol().render == "<ol></ol>")
      }
    ),

    // --- Table content model ---
    suite("table elements")(
      test("tr with th and td") {
        val result = tr(th("Header"), td("Value")).render
        assertTrue(result == "<tr><th>Header</th><td>Value</td></tr>")
      },
      test("table with tr") {
        val result = table(tr(td("cell"))).render
        assertTrue(result == "<table><tr><td>cell</td></tr></table>")
      },
      test("th with attributes") {
        val result = th(scopeAttr := "col", "H").render
        assertTrue(result == """<th scope="col">H</th>""")
      },
      test("td with attributes") {
        val result = td(colspan := "2", "V").render
        assertTrue(result == """<td colspan="2">V</td>""")
      },
      test("tr mixes attributes and cells") {
        val result = tr(className := "row", th("H"), td("D")).render
        assertTrue(result == """<tr class="row"><th>H</th><td>D</td></tr>""")
      },
      test("table mixes attributes and rows") {
        val result = table(summaryAttr := "desc", tr(td("cell"))).render
        assertTrue(result == """<table summary="desc"><tr><td>cell</td></tr></table>""")
      },
      test("tr from Iterable of cells") {
        val cells  = List(th("H"), td("D"))
        val result = tr(cells).render
        assertTrue(result == "<tr><th>H</th><td>D</td></tr>")
      },
      test("table from Iterable of rows") {
        val rows   = List(tr(td("a")), tr(td("b")))
        val result = table(rows).render
        assertTrue(result == "<table><tr><td>a</td></tr><tr><td>b</td></tr></table>")
      },
      test("empty table") {
        assertTrue(table().render == "<table></table>") &&
        assertTrue(tr().render == "<tr></tr>")
      }
    ),

    // --- Select content model ---
    suite("select elements")(
      test("select with options") {
        val result = select(option("1"), option("2")).render
        assertTrue(result == "<select><option>1</option><option>2</option></select>")
      },
      test("select with optgroup") {
        val result = select(optgroup(option("a"), option("b"))).render
        assertTrue(result == "<select><optgroup><option>a</option><option>b</option></optgroup></select>")
      },
      test("opt with attributes") {
        val result = option(value := "1", "One").render
        assertTrue(result == """<option value="1">One</option>""")
      },
      test("select with mixed opt and optgroup") {
        val result = select(option("default"), optgroup(option("a"), option("b"))).render
        assertTrue(
          result == "<select><option>default</option><optgroup><option>a</option><option>b</option></optgroup></select>"
        )
      },
      test("optgroup factory returns Optgroup accepted by select") {
        val group: Dom.Element.Optgroup = optgroup(option("a"), option("b"))
        val result                      = select(group).render
        assertTrue(result == "<select><optgroup><option>a</option><option>b</option></optgroup></select>")
      },
      test("optgroup mixes attributes and options") {
        val result = optgroup(labelAttr := "Group", option("a")).render
        assertTrue(result == """<optgroup label="Group"><option>a</option></optgroup>""")
      },
      test("select mixes attributes and children") {
        val result = select(id := "s", name := "choice", option("x")).render
        assertTrue(result == """<select id="s" name="choice"><option>x</option></select>""")
      },
      test("select from Iterable of children") {
        val kids   = List(option("x"), optgroup(option("y")))
        val result = select(kids).render
        assertTrue(result == "<select><option>x</option><optgroup><option>y</option></optgroup></select>")
      },
      test("optgroup from Iterable of options") {
        val opts   = List(option("a"), option("b"))
        val result = optgroup(opts).render
        assertTrue(result == "<optgroup><option>a</option><option>b</option></optgroup>")
      },
      test("empty select and optgroup") {
        assertTrue(select().render == "<select></select>") &&
        assertTrue(optgroup().render == "<optgroup></optgroup>")
      }
    ),

    // --- Element apply/when still works (result is Dom.Element) ---
    suite("Dom.Element methods still work")(
      test("when(true) on ul result") {
        val result = ul(li("a")).when(true)(id := "list")
        assertTrue(result.render == """<ul id="list"><li>a</li></ul>""")
      },
      test("when(false) on ul result") {
        val result = ul(li("a")).when(false)(id := "list")
        assertTrue(result.render == "<ul><li>a</li></ul>")
      },
      test("when(true) on tr result") {
        val row = tr(td("cell")).when(true)(className := "r")
        assertTrue(row.render == """<tr class="r"><td>cell</td></tr>""")
      }
    ),

    // --- Structural equality across Element implementations ---
    suite("structural equality")(
      test("factory li equals structurally identical Generic") {
        val typed: Dom.Element   = li("a")
        val generic: Dom.Element = Dom.Element.Generic("li", Chunk.empty, Chunk(Dom.Text("a")))
        assertTrue(typed == generic)
      },
      test("Generic equals factory li (symmetry)") {
        val typed: Dom.Element   = li(id := "i1", "text")
        val generic: Dom.Element = Dom.Element.Generic(
          "li",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("i1"))),
          Chunk(Dom.Text("text"))
        )
        assertTrue(generic == typed)
      },
      test("equal elements have equal hashCodes") {
        val typed: Dom.Element   = th("H")
        val generic: Dom.Element = Dom.Element.Generic("th", Chunk.empty, Chunk(Dom.Text("H")))
        assertTrue(typed.hashCode == generic.hashCode && typed == generic)
      },
      test("different tags are not equal despite same children shape") {
        val headerCell: Dom.Element = th("X")
        val dataCell: Dom.Element   = td("X")
        assertTrue(headerCell != dataCell)
      },
      test("different attributes are not equal") {
        val a: Dom.Element = li(id := "one", "x")
        val b: Dom.Element = li(id := "two", "x")
        assertTrue(a != b)
      },
      test("script never equals structurally identical Generic (escaping differs)") {
        val scriptEl: Dom.Element = script().inlineJs(js"console.log(1);")
        val generic: Dom.Element  =
          Dom.Element.Generic("script", Chunk.empty, Chunk(Dom.Text(js"console.log(1);".value)))
        assertTrue(scriptEl != generic && scriptEl.hashCode != generic.hashCode)
      },
      test("elements with different escaping semantics diverge on raw content") {
        val raw                   = Chunk(Dom.Text("<b>&"))
        val scriptEl: Dom.Element = Dom.Element.Script(Chunk.empty, raw)
        val generic: Dom.Element  = Dom.Element.Generic("script", Chunk.empty, raw)
        assertTrue(scriptEl != generic && scriptEl.render != generic.render)
      }
    )
  )
}
