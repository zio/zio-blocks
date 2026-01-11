package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

case class Item(id: Int, name: String)
object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

/**
 * Comprehensive tests for array/sequence codecs with different formats.
 */
object ComprehensiveArraySpec extends ZIOSpecDefault {
  def spec = suite("ComprehensiveArray")(
    suite("Primitive Arrays with Auto Format")(
      test("List[Int] inline format") {
        val codec = Schema[List[Int]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(List(1, 2, 3)) == "[3]: 1,2,3")
      },
      test("List[String] inline format") {
        val codec = Schema[List[String]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(List("a", "b", "c")) == "[3]: a,b,c")
      },
      test("List[Boolean] inline format") {
        val codec = Schema[List[Boolean]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(List(true, false, true)) == "[3]: true,false,true")
      },
      test("List[Double] inline format") {
        val codec = Schema[List[Double]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(List(1.1, 2.2, 3.3)) == "[3]: 1.1,2.2,3.3")
      },
      test("empty list") {
        val codec = Schema[List[Int]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(List()) == "[0]: ")
      },
      test("single element list") {
        val codec = Schema[List[Int]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(List(42)) == "[1]: 42")
      }
    ),
    suite("Explicit Inline Format")(
      test("forced inline for primitives") {
        val codec = Schema[List[Int]].derive(
          ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Inline)
        )
        assertTrue(codec.encodeToString(List(1, 2, 3)) == "[3]: 1,2,3")
      }
    ),
    suite("List Format")(
      test("list format for records") {
        val codec = Schema[List[Item]].derive(
          ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        )
        val items   = List(Item(1, "first"), Item(2, "second"))
        val encoded = codec.encodeToString(items)
        assertTrue(
          encoded.contains("[2]:") &&
            encoded.contains("- ") &&
            encoded.contains("id:")
        )
      }
    ),
    suite("Vector and Set")(
      test("Vector[Int]") {
        val codec = Schema[Vector[Int]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Vector(1, 2, 3)) == "[3]: 1,2,3")
      },
      test("Set[Int]") {
        val codec   = Schema[Set[Int]].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString(Set(1, 2, 3))
        // Set order may vary
        assertTrue(
          encoded.startsWith("[3]: ") && encoded.contains("1") && encoded.contains("2") && encoded.contains("3")
        )
      }
    ),
    suite("Nested Arrays")(
      test("List[List[Int]]") {
        val codec   = Schema[List[List[Int]]].derive(ToonFormat.deriver)
        val nested  = List(List(1, 2), List(3, 4))
        val encoded = codec.encodeToString(nested)
        assertTrue(encoded.contains("[2]:"))
      }
    )
  )
}
