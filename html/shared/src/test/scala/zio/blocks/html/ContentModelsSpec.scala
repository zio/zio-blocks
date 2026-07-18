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
      }
    ),

    // --- Select content model ---
    suite("select elements")(
      test("select with options") {
        val result = select(opt("1"), opt("2")).render
        assertTrue(result == "<select><option>1</option><option>2</option></select>")
      },
      test("select with optgroup") {
        val result = select(optgroup(opt("a"), opt("b"))).render
        assertTrue(result == "<select><optgroup><option>a</option><option>b</option></optgroup></select>")
      },
      test("opt with attributes") {
        val result = opt(value := "1", "One").render
        assertTrue(result == """<option value="1">One</option>""")
      },
      test("select with mixed opt and optgroup") {
        val result = select(opt("default"), optgroup(opt("a"), opt("b"))).render
        assertTrue(
          result == "<select><option>default</option><optgroup><option>a</option><option>b</option></optgroup></select>"
        )
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
      }
    )
  )
}
