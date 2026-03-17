package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.test._

object ZipSpec extends StreamsBaseSpec {
  def spec = suite("Stream.&&")(
    test("zips two streams of equal length") {
      val result = (Stream(1, 2, 3) && Stream("a", "b", "c")).runCollect
      assertTrue(result == Right(Chunk((1, "a"), (2, "b"), (3, "c"))))
    },
    test("shorter stream determines length (left shorter)") {
      val result = (Stream(1, 2) && Stream("a", "b", "c")).runCollect
      assertTrue(result == Right(Chunk((1, "a"), (2, "b"))))
    },
    test("shorter stream determines length (right shorter)") {
      val result = (Stream(1, 2, 3) && Stream("a")).runCollect
      assertTrue(result == Right(Chunk((1, "a"))))
    },
    test("empty stream yields empty") {
      val result = (Stream[Int]() && Stream(1, 2, 3)).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("triple zip flattens: a && b && c gives (A, B, C)") {
      val result = (Stream(1, 2) && Stream("a", "b") && Stream(true, false)).runCollect
      assertTrue(result == Right(Chunk((1, "a", true), (2, "b", false))))
    },
    test("error in left stream propagates") {
      val result = ((Stream.fail("boom"): Stream[String, Int]) && Stream(1, 2)).runCollect
      assertTrue(result == Left("boom"))
    },
    test("error in right stream propagates") {
      val result = (Stream(1, 2) && (Stream.fail("boom"): Stream[String, Int])).runCollect
      assertTrue(result == Left("boom"))
    },
    test("resources are cleaned up on both sides") {
      val leftClosed  = new java.util.concurrent.atomic.AtomicBoolean(false)
      val rightClosed = new java.util.concurrent.atomic.AtomicBoolean(false)
      val left        = Stream.range(0, 100).ensuring(leftClosed.set(true))
      val right       = Stream.range(0, 3).ensuring(rightClosed.set(true))
      val result      = (left && right).runCollect
      assertTrue(result == Right(Chunk((0, 0), (1, 1), (2, 2)))) &&
      assertTrue(leftClosed.get()) &&
      assertTrue(rightClosed.get())
    },
    test("&& closes left if right compilation fails") {
      var leftClosed = false
      val left       = Stream.fromAcquireRelease("left", (_: String) => leftClosed = true)(_ => Stream(1, 2, 3))
      val right      = Stream.fromReader[Nothing, Int](throw new RuntimeException("compile fail"))
      val result     = try {
        (left && right).runCollect
        "no exception"
      } catch {
        case _: RuntimeException => "threw"
      }
      assertTrue(result == "threw") && assertTrue(leftClosed)
    },
    test("&& close accumulates exceptions from both sides") {
      var leftClosed        = false
      var rightClosed       = false
      val left              = Stream(1, 2, 3).ensuring { leftClosed = true; throw new RuntimeException("left close") }
      val right             = Stream(4, 5, 6).ensuring { rightClosed = true; throw new RuntimeException("right close") }
      val (msg, suppressed) = try {
        (left && right).runCollect
        ("no exception", -1)
      } catch {
        case e: RuntimeException =>
          (e.getMessage, e.getSuppressed.length)
      }
      assertTrue(leftClosed) && assertTrue(rightClosed) &&
      assertTrue(msg == "left close") && assertTrue(suppressed == 1)
    }
  )
}
