package zio.blocks.chunk

import zio.test._
import zio.test.Assertion._

object ChunkSpec extends ZIOSpecDefault {
  def spec = suite("ChunkSpec")(
    suite("Constructors")(
      test("apply") {
        val chunk = Chunk(1, 2, 3)
        assert(chunk.length)(equalTo(3)) &&
        assert(chunk.toList)(equalTo(List(1, 2, 3)))
      },
      test("empty") {
        val chunk = Chunk.empty
        assert(chunk.length)(equalTo(0)) &&
        assert(chunk.isEmpty)(isTrue)
      },
      test("fromArray") {
        val arr = Array(1, 2, 3)
        val chunk = Chunk.fromArray(arr)
        assert(chunk.toArray.toSeq)(equalTo(arr.toSeq))
      }
    ),
    suite("Operations")(
      test("map") {
        val chunk = Chunk(1, 2, 3)
        val mapped = chunk.map(_ * 2)
        assert(mapped.toList)(equalTo(List(2, 4, 6)))
      },
      test("flatMap") {
        val chunk = Chunk(1, 2)
        val flatMapped = chunk.flatMap(x => Chunk(x, x))
        assert(flatMapped.toList)(equalTo(List(1, 1, 2, 2)))
      },
      test("filter") {
        val chunk = Chunk(1, 2, 3, 4)
        val filtered = chunk.filter(_ % 2 == 0)
        assert(filtered.toList)(equalTo(List(2, 4)))
      },
      test("drop") {
        val chunk = Chunk(1, 2, 3, 4)
        assert(chunk.drop(2).toList)(equalTo(List(3, 4)))
      },
      test("take") {
        val chunk = Chunk(1, 2, 3, 4)
        assert(chunk.take(2).toList)(equalTo(List(1, 2)))
      },
      test("append (:+)") {
        val chunk = Chunk(1, 2)
        val appended = chunk :+ 3
        assert(appended.toList)(equalTo(List(1, 2, 3)))
      }
    ),
    suite("Concatenation")(
      test("++") {
        val c1 = Chunk(1, 2)
        val c2 = Chunk(3, 4)
        val c3 = c1 ++ c2
        assert(c3.toList)(equalTo(List(1, 2, 3, 4)))
      }
    )
  )
}