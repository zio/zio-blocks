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
import zio.test._

object ToDomSpec extends ZIOSpecDefault {
  def spec = suite("ToDom")(
    suite("primitive instances")(
      test("String produces Text") {
        assertTrue(ToDom[String].toDom("hello") == Dom.Text("hello"))
      },
      test("Int produces Text") {
        assertTrue(ToDom[Int].toDom(42) == Dom.Text("42"))
      },
      test("Long produces Text") {
        assertTrue(ToDom[Long].toDom(100L) == Dom.Text("100"))
      },
      test("Double produces Text") {
        assertTrue(ToDom[Double].toDom(3.14) == Dom.Text("3.14"))
      },
      test("Boolean produces Text") {
        assertTrue(ToDom[Boolean].toDom(true) == Dom.Text("true"))
      }
    ),
    suite("Dom identity instances")(
      test("Dom passes through unchanged") {
        val d: Dom = Dom.Element.Generic("div", Chunk.empty, Chunk(Dom.Text("hi")))
        assertTrue(ToDom[Dom].toDom(d) == d)
      },
      test("Dom.Element passes through unchanged") {
        val el = Dom.Element.Generic("span", Chunk.empty, Chunk.empty)
        assertTrue(ToDom[Dom.Element].toDom(el) == el)
      },
      test("Dom.Text passes through unchanged") {
        val t = Dom.Text("world")
        assertTrue(ToDom[Dom.Text].toDom(t) == t)
      }
    ),
    suite("Option instance")(
      test("Some converts inner value") {
        assertTrue(ToDom[Option[String]].toDom(Some("hello")) == Dom.Text("hello"))
      },
      test("None produces Empty") {
        assertTrue(ToDom[Option[String]].toDom(None) == Dom.Empty)
      }
    ),
    suite("Iterable instance")(
      test("empty Iterable produces Empty") {
        assertTrue(ToDom[Iterable[String]].toDom(Nil) == Dom.Empty)
      },
      test("single-element Iterable produces the element directly") {
        assertTrue(ToDom[Iterable[String]].toDom(List("only")) == Dom.Text("only"))
      },
      test("multi-element Iterable wraps in span") {
        val result = ToDom[Iterable[String]].toDom(List("a", "b"))
        assertTrue(result.isInstanceOf[Dom.Element])
        val el = result.asInstanceOf[Dom.Element]
        assertTrue(
          el.tag == "span",
          el.children.length == 2,
          el.children(0) == Dom.Text("a"),
          el.children(1) == Dom.Text("b")
        )
      }
    ),
    suite("summoner")(
      test("ToDom.apply summons instance") {
        val ev = ToDom[Int]
        assertTrue(ev.toDom(7) == Dom.Text("7"))
      }
    )
  )
}
