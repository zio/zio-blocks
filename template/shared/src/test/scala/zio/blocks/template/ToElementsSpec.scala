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

object ToElementsSpec extends ZIOSpecDefault {
  def spec = suite("ToElements")(
    test("String to text element") {
      assertTrue(ToElements[String].toElements("hello") == Chunk(Dom.Text("hello")))
    },
    test("Int to text element") {
      assertTrue(ToElements[Int].toElements(42) == Chunk(Dom.Text("42")))
    },
    test("Dom passthrough") {
      assertTrue(ToElements[Dom].toElements(Dom.Empty) == Chunk(Dom.Empty))
    },
    test("Option[String] Some") {
      assertTrue(ToElements[Option[String]].toElements(Some("x")) == Chunk(Dom.Text("x")))
    },
    test("Option[String] None") {
      assertTrue(ToElements[Option[String]].toElements(None) == Chunk.empty)
    },
    test("List[String]") {
      assertTrue(
        ToElements[List[String]].toElements(List("a", "b")) == Chunk(Dom.Text("a"), Dom.Text("b"))
      )
    },
    test("Chunk[Dom]") {
      val elems = Chunk(Dom.Text("x"), Dom.Empty)
      assertTrue(ToElements[Chunk[Dom]].toElements(elems) == Chunk(Dom.Text("x"), Dom.Empty))
    },
    test("Long to text element") {
      assertTrue(ToElements[Long].toElements(100L) == Chunk(Dom.Text("100")))
    },
    test("Double to text element") {
      assertTrue(ToElements[Double].toElements(3.14) == Chunk(Dom.Text("3.14")))
    },
    test("Boolean to text element") {
      assertTrue(ToElements[Boolean].toElements(true) == Chunk(Dom.Text("true")))
    },
    test("Char to text element") {
      assertTrue(ToElements[Char].toElements('x') == Chunk(Dom.Text("x")))
    },
    test("Iterable[Dom] to elements") {
      val elems: Iterable[Dom] = List(Dom.Text("a"), Dom.Text("b"))
      assertTrue(ToElements[Iterable[Dom]].toElements(elems) == Chunk(Dom.Text("a"), Dom.Text("b")))
    }
  )
}
