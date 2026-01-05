package zio.blocks.chunk

import zio.test._

object ChunkSpec extends ZIOSpecDefault {

  def spec = suite("ChunkSpec")(
    test("size equals length") {
      val empty   = Chunk.empty[Int]
      val single  = Chunk.single(1)
      val array   = Chunk.fromArray(Array(1, 2, 3))
      val concat  = Chunk(1, 2) ++ Chunk(3, 4)
      assertTrue(
        empty.size == empty.length,
        single.size == single.length,
        array.size == array.length,
        concat.size == concat.length
      )
    },
    test("append and prepend") {
      val a = Chunk(1, 2, 3)
      val b = Chunk(4, 5)
      assertTrue(
        a.foldRight(b)(_ +: _) == a ++ b,
        b.foldLeft(a)(_ :+ _) == a ++ b
      )
    },
    test("apply") {
      val chunk = Chunk(1, 2, 3, 4, 5)
      assertTrue(chunk(0) == 1, chunk(2) == 3, chunk(4) == 5)
    },
    test("collect and collectWhile") {
      val chunk = Chunk(1, 2, 3, 4, 5)
      assertTrue(
        chunk.collect { case x if x % 2 == 0 => x * 2 } == Chunk(4, 8),
        chunk.collectWhile { case x if x < 4 => x * 2 } == Chunk(2, 4, 6)
      )
    },
    test("concat") {
      val a = Chunk(1, 2)
      assertTrue(
        (a ++ Chunk(3, 4)) == Chunk(1, 2, 3, 4),
        (a ++ Chunk.empty) == a,
        (Chunk.empty ++ a) == a
      )
    },
    test("drop and dropWhile") {
      val chunk = Chunk(1, 2, 3, 4, 5)
      assertTrue(
        chunk.drop(2) == Chunk(3, 4, 5),
        chunk.drop(10) == Chunk.empty,
        chunk.dropWhile(_ < 3) == Chunk(3, 4, 5)
      )
    },
    test("filter and map") {
      val chunk = Chunk(1, 2, 3, 4, 5)
      assertTrue(
        chunk.filter(_ % 2 == 0) == Chunk(2, 4),
        chunk.map(_ * 2) == Chunk(2, 4, 6, 8, 10)
      )
    },
    test("flatMap") {
      val chunk = Chunk(1, 2, 3)
      assertTrue(chunk.flatMap(x => Chunk(x, x * 2)) == Chunk(1, 2, 2, 4, 3, 6))
    },
    test("fold") {
      assertTrue(
        Chunk(1, 2, 3).foldLeft(0)(_ + _) == 6,
        Chunk("a", "b", "c").foldRight("")(_ + _) == "abc"
      )
    },
    test("head and last") {
      val chunk = Chunk(1, 2, 3)
      assertTrue(
        chunk.head == 1,
        chunk.last == 3,
        chunk.headOption == Some(1),
        Chunk.empty[Int].headOption == None
      )
    },
    test("isEmpty") {
      assertTrue(Chunk.empty.isEmpty, !Chunk(1).isEmpty)
    },
    test("reverse") {
      assertTrue(Chunk(1, 2, 3).reverse == Chunk(3, 2, 1))
    },
    test("take and takeWhile") {
      val chunk = Chunk(1, 2, 3, 4, 5)
      assertTrue(
        chunk.take(3) == Chunk(1, 2, 3),
        chunk.takeWhile(_ < 4) == Chunk(1, 2, 3)
      )
    },
    test("conversions") {
      val chunk = Chunk(1, 2, 3)
      assertTrue(
        chunk.toList == List(1, 2, 3),
        chunk.toArray.toList == List(1, 2, 3)
      )
    },
    test("zip") {
      assertTrue(
        Chunk(1, 2, 3).zip(Chunk("a", "b", "c")) == Chunk((1, "a"), (2, "b"), (3, "c")),
        Chunk("a", "b", "c").zipWithIndex == Chunk(("a", 0), ("b", 1), ("c", 2))
      )
    },
    test("NonEmptyChunk") {
      assertTrue(
        NonEmptyChunk(1, 2, 3).toChunk == Chunk(1, 2, 3),
        NonEmptyChunk.fromChunk(Chunk(1, 2, 3)).isDefined,
        NonEmptyChunk.fromChunk(Chunk.empty[Int]).isEmpty
      )
    }
  )
}
