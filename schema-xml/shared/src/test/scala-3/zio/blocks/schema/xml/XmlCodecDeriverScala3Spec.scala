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

package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._

object XmlCodecDeriverScala3Spec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlCodecDeriverScala3Spec")(
    suite("Scala 3 enums")(
      test("simple enum round-trip - Red") {
        val codec   = Schema[TrafficLight].derive(XmlCodecDeriver)
        val traffic = TrafficLight.Red
        val result  = codec.decode(codec.encode(traffic))
        assertTrue(result == Right(TrafficLight.Red))
      },
      test("simple enum round-trip - Yellow") {
        val codec   = Schema[TrafficLight].derive(XmlCodecDeriver)
        val traffic = TrafficLight.Yellow
        val result  = codec.decode(codec.encode(traffic))
        assertTrue(result == Right(TrafficLight.Yellow))
      },
      test("simple enum round-trip - Green") {
        val codec   = Schema[TrafficLight].derive(XmlCodecDeriver)
        val traffic = TrafficLight.Green
        val result  = codec.decode(codec.encode(traffic))
        assertTrue(result == Right(TrafficLight.Green))
      },
      test("simple enum encode produces valid XML") {
        val codec = Schema[TrafficLight].derive(XmlCodecDeriver)
        val xml   = codec.encodeToString(TrafficLight.Red)
        assertTrue(xml == "<Red/>")
      }
    ),
    suite("generic tuples")(
      test("generic tuple 8 round-trip") {
        type GenericTuple8 = Boolean *: Byte *: Char *: Short *: Float *: Int *: Double *: Long *: EmptyTuple

        implicit val schema: Schema[GenericTuple8] = Schema.derived

        val codec  = Schema[GenericTuple8].derive(XmlCodecDeriver)
        val tuple  = true *: (2: Byte) *: '3' *: (4: Short) *: 5.0f *: 6 *: 7.0 *: 8L *: EmptyTuple
        val result = codec.decode(codec.encode(tuple))
        assertTrue(result == Right(tuple))
      },
      test("generic tuple 2 round-trip") {
        type GenericTuple2 = Int *: String *: EmptyTuple

        implicit val schema: Schema[GenericTuple2] = Schema.derived

        val codec  = Schema[GenericTuple2].derive(XmlCodecDeriver)
        val tuple  = 42 *: "hello" *: EmptyTuple
        val result = codec.decode(codec.encode(tuple))
        assertTrue(result == Right(tuple))
      }
    ),
    suite("enum with parameterized cases")(
      test("parameterized enum - RGB case") {
        val codec  = Schema[Color].derive(XmlCodecDeriver)
        val color  = Color.RGB(255, 128, 64)
        val result = codec.decode(codec.encode(color))
        assertTrue(result == Right(color))
      },
      test("parameterized enum - constant Black") {
        val codec  = Schema[Color].derive(XmlCodecDeriver)
        val color  = Color.Black
        val result = codec.decode(codec.encode(color))
        assertTrue(result == Right(color))
      },
      test("parameterized enum - Hex case") {
        val codec  = Schema[Color].derive(XmlCodecDeriver)
        val color  = Color.Hex("FFFFFF")
        val result = codec.decode(codec.encode(color))
        assertTrue(result == Right(color))
      }
    ),
    suite("sealed trait variants")(
      test("sealed trait Foo variant") {
        val codec   = Schema[MySealedTrait].derive(XmlCodecDeriver)
        val variant = MySealedTrait.Foo(42)
        val result  = codec.decode(codec.encode(variant))
        assertTrue(result == Right(variant))
      },
      test("sealed trait Bar variant") {
        val codec   = Schema[MySealedTrait].derive(XmlCodecDeriver)
        val variant = MySealedTrait.Bar("test")
        val result  = codec.decode(codec.encode(variant))
        assertTrue(result == Right(variant))
      },
      test("sealed trait Baz variant") {
        val codec   = Schema[MySealedTrait].derive(XmlCodecDeriver)
        val variant = MySealedTrait.Baz(3.14)
        val result  = codec.decode(codec.encode(variant))
        assertTrue(result == Right(variant))
      },
      test("sealed trait encode produces XML with variant info") {
        val codec   = Schema[MySealedTrait].derive(XmlCodecDeriver)
        val variant = MySealedTrait.Foo(1)
        val xml     = codec.encodeToString(variant)
        assertTrue(xml == "<Foo><value>1</value></Foo>")
      }
    )
  )

  enum TrafficLight derives Schema {
    case Red, Yellow, Green
  }

  enum Color derives Schema {
    case RGB(r: Int, g: Int, b: Int)
    case Hex(code: String)
    case Black
  }

  sealed trait MySealedTrait derives Schema

  object MySealedTrait {
    case class Foo(value: Int) extends MySealedTrait derives Schema

    case class Bar(value: String) extends MySealedTrait derives Schema

    case class Baz(value: Double) extends MySealedTrait derives Schema
  }
}
