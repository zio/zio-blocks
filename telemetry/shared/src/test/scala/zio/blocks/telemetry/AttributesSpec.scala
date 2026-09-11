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

package zio.blocks.telemetry

import zio.test._

object AttributesSpec extends ZIOSpecDefault {

  private def fullBuilder(): Attributes.AttributesBuilder =
    Attributes.builder
      .put("s", "value")
      .put("l", 42L)
      .put("d", 2.5)
      .put("b", true)
      .put(AttributeKey.stringSeq("ss"), Seq("a", "b"))
      .put(AttributeKey.longSeq("ls"), Seq(1L, 2L))
      .put(AttributeKey.doubleSeq("ds"), Seq(1.5))
      .put(AttributeKey.booleanSeq("bs"), Seq(true, false))

  def spec = suite("Attributes")(
    suite("build")(
      test("build pins every stored type and value") {
        val attrs = fullBuilder().build
        assertTrue(
          attrs.size == 8,
          attrs.get(AttributeKey.string("s")).contains("value"),
          attrs.get(AttributeKey.long("l")).contains(42L),
          attrs.get(AttributeKey.double("d")).contains(2.5),
          attrs.get(AttributeKey.boolean("b")).contains(true),
          attrs.get(AttributeKey.stringSeq("ss")).contains(Seq("a", "b")),
          attrs.get(AttributeKey.longSeq("ls")).contains(Seq(1L, 2L)),
          attrs.get(AttributeKey.doubleSeq("ds")).contains(Seq(1.5)),
          attrs.get(AttributeKey.booleanSeq("bs")).contains(Seq(true, false))
        )
      },
      test("buildAndReset pins the same content with zero copies") {
        val builder = fullBuilder()
        val attrs   = builder.buildAndReset()
        assertTrue(
          attrs.size == 8,
          attrs.get(AttributeKey.string("s")).contains("value"),
          attrs.get(AttributeKey.long("l")).contains(42L),
          attrs.get(AttributeKey.double("d")).contains(2.5),
          attrs.get(AttributeKey.boolean("b")).contains(true),
          attrs.get(AttributeKey.stringSeq("ss")).contains(Seq("a", "b")),
          attrs.get(AttributeKey.longSeq("ls")).contains(Seq(1L, 2L)),
          attrs.get(AttributeKey.doubleSeq("ds")).contains(Seq(1.5)),
          attrs.get(AttributeKey.booleanSeq("bs")).contains(Seq(true, false)),
          builder.build.isEmpty
        )
      }
    ),
    suite("hashCode")(
      test("equal instances share a hash") {
        val a = fullBuilder().build
        val b = fullBuilder().build
        assertTrue(a == b, a.hashCode == b.hashCode)
      },
      test("hash is stable across reads") {
        val a = fullBuilder().build
        assertTrue(a.hashCode == a.hashCode)
      },
      test("empty attributes hash to zero") {
        assertTrue(Attributes.empty.hashCode == 0)
      },
      test("build and buildAndReset agree on hash") {
        val a = fullBuilder().build
        val b = fullBuilder().buildAndReset()
        assertTrue(a == b, a.hashCode == b.hashCode)
      }
    )
  )
}
