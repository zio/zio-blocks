package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._

object XmlBinaryCodecDeriverScala3Spec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlBinaryCodecDeriverScala3Spec")(
    suite("Scala 3 enums")(
      test("simple enum round-trip - Red") {
        val codec   = Schema[TrafficLight].derive(XmlBinaryCodecDeriver)
        val traffic = TrafficLight.Red
        val result  = codec.decode(codec.encode(traffic))
        assertTrue(result == Right(TrafficLight.Red))
      },
      test("simple enum round-trip - Yellow") {
        val codec   = Schema[TrafficLight].derive(XmlBinaryCodecDeriver)
        val traffic = TrafficLight.Yellow
        val result  = codec.decode(codec.encode(traffic))
        assertTrue(result == Right(TrafficLight.Yellow))
      },
      test("simple enum round-trip - Green") {
        val codec   = Schema[TrafficLight].derive(XmlBinaryCodecDeriver)
        val traffic = TrafficLight.Green
        val result  = codec.decode(codec.encode(traffic))
        assertTrue(result == Right(TrafficLight.Green))
      },
      test("simple enum encode produces valid XML") {
        val codec = Schema[TrafficLight].derive(XmlBinaryCodecDeriver)
        val xml   = codec.encodeToString(TrafficLight.Red)
        assertTrue(
          xml.contains("Red") ||
            xml.contains("TrafficLight")
        )
      }
    ),
    suite("generic tuples")(
      test("generic tuple 4 round-trip") {
        type GenericTuple4 = Byte *: Short *: Int *: Long *: EmptyTuple

        implicit val schema: Schema[GenericTuple4] = Schema.derived

        val codec  = Schema[GenericTuple4].derive(XmlBinaryCodecDeriver)
        val tuple  = (1: Byte) *: (2: Short) *: 3 *: 4L *: EmptyTuple
        val result = codec.decode(codec.encode(tuple))
        assertTrue(result == Right(tuple))
      },
      test("generic tuple 2 round-trip") {
        type GenericTuple2 = Int *: String *: EmptyTuple

        implicit val schema: Schema[GenericTuple2] = Schema.derived

        val codec  = Schema[GenericTuple2].derive(XmlBinaryCodecDeriver)
        val tuple  = 42 *: "hello" *: EmptyTuple
        val result = codec.decode(codec.encode(tuple))
        assertTrue(result == Right(tuple))
      }
    ),
    suite("enum with parameterized cases")(
      test("parameterized enum - RGB case") {
        val codec  = Schema[Color].derive(XmlBinaryCodecDeriver)
        val color  = Color.RGB(255, 128, 64)
        val result = codec.decode(codec.encode(color))
        assertTrue(result == Right(color))
      },
      test("parameterized enum - constant Black") {
        val codec  = Schema[Color].derive(XmlBinaryCodecDeriver)
        val color  = Color.Black
        val result = codec.decode(codec.encode(color))
        assertTrue(result == Right(color))
      },
      test("parameterized enum - Hex case") {
        val codec  = Schema[Color].derive(XmlBinaryCodecDeriver)
        val color  = Color.Hex("FFFFFF")
        val result = codec.decode(codec.encode(color))
        assertTrue(result == Right(color))
      }
    ),
    suite("sealed trait variants")(
      test("sealed trait Foo variant") {
        val codec   = Schema[MySealedTrait].derive(XmlBinaryCodecDeriver)
        val variant = MySealedTrait.Foo(42)
        val result  = codec.decode(codec.encode(variant))
        assertTrue(result == Right(variant))
      },
      test("sealed trait Bar variant") {
        val codec   = Schema[MySealedTrait].derive(XmlBinaryCodecDeriver)
        val variant = MySealedTrait.Bar("test")
        val result  = codec.decode(codec.encode(variant))
        assertTrue(result == Right(variant))
      },
      test("sealed trait Baz variant") {
        val codec   = Schema[MySealedTrait].derive(XmlBinaryCodecDeriver)
        val variant = MySealedTrait.Baz(3.14)
        val result  = codec.decode(codec.encode(variant))
        assertTrue(result == Right(variant))
      },
      test("sealed trait encode produces XML with variant info") {
        val codec   = Schema[MySealedTrait].derive(XmlBinaryCodecDeriver)
        val variant = MySealedTrait.Foo(1)
        val xml     = codec.encodeToString(variant)
        assertTrue(xml.contains("Foo") || xml.contains("value"))
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
