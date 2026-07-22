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

object DomModifierSpec extends ZIOSpecDefault {
  private val emptyDiv: Dom.Element = Dom.Element.Generic("div", Chunk.empty, Chunk.empty)

  def spec = suite("DomModifier")(
    suite("applyTo")(
      test("AddAttr adds attribute") {
        val effect = DomModifier.AddAttr(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("x")))
        val result = effect.applyTo(emptyDiv)
        assertTrue(result.render == """<div id="x"></div>""")
      },
      test("AddChild adds child") {
        val effect = DomModifier.AddChild(Dom.Text("hello"))
        val result = effect.applyTo(emptyDiv)
        assertTrue(result.render == "<div>hello</div>")
      },
      test("AddChildren adds multiple children") {
        val effect = DomModifier.AddChildren(Chunk(Dom.Text("a"), Dom.Text("b")))
        val result = effect.applyTo(emptyDiv)
        assertTrue(result.render == "<div>ab</div>")
      },
      test("AddEffects applies nested effects sequentially") {
        val effects = DomModifier.AddEffects(
          Chunk(
            DomModifier.AddAttr(Dom.Attribute.KeyValue("class", Dom.AttributeValue.StringValue("box"))),
            DomModifier.AddChild(Dom.Text("content"))
          )
        )
        val result = effects.applyTo(emptyDiv)
        assertTrue(result.render == """<div class="box">content</div>""")
      },
      test("applyTo preserves existing attributes and children") {
        val existing = Dom.Element.Generic(
          "div",
          Chunk(Dom.Attribute.KeyValue("id", Dom.AttributeValue.StringValue("a"))),
          Chunk(Dom.Text("old"))
        )
        val effect = DomModifier.AddChild(Dom.Text("new"))
        val result = effect.applyTo(existing)
        assertTrue(result.render == """<div id="a">oldnew</div>""")
      },
      test("AddChildren with empty chunk is a no-op") {
        val effect = DomModifier.AddChildren(Chunk.empty)
        val result = effect.applyTo(emptyDiv)
        assertTrue(result.render == "<div></div>")
      }
    )
  )
}
