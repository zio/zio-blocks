package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._

/**
 * Tests for error handling, resource management, new constructors, and
 * combinators.
 */
object ErrorHandlingSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ErrorHandling")(
    // ---- Suite 1: Error construction ----

    suite("Error construction")(
      test("Stream.fail(e).runCollect returns Left(e)") {
        assert(Stream.fail("boom").runCollect)(equalTo(Left("boom")))
      },
      test("Stream.fail(e).runDrain returns Left(e)") {
        assert(Stream.fail(42).runDrain)(equalTo(Left(42)))
      },
      test("Stream.die(t).runCollect throws t") {
        val t      = new RuntimeException("die")
        val result = try {
          Stream.die(t).runCollect
          "no exception"
        } catch {
          case e: RuntimeException => e.getMessage
        }
        assert(result)(equalTo("die"))
      },
      test("Stream.attempt { 42 }.runCollect returns Right(Chunk(42))") {
        assert(Stream.attempt(42).runCollect)(equalTo(Right(Chunk(42))))
      },
      test("Stream.attempt { throw RuntimeException }.runCollect returns Left") {
        val result = Stream.attempt(throw new RuntimeException("boom")).runCollect
        assert(result.isLeft)(isTrue) &&
        assert(result.left.exists(_.isInstanceOf[RuntimeException]))(isTrue)
      },
      test("Stream.suspend produces correct elements") {
        val result = Stream.suspend(Stream.range(0, 3)).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2))))
      },
      test("Stream.eval executes side-effect and emits nothing") {
        var executed = false
        val result   = Stream.eval { executed = true }.runCollect
        assertTrue(executed) && assert(result)(equalTo(Right(Chunk.empty)))
      },
      test("Stream.eval defect propagates as defect") {
        val result = scala.util.Try(Stream.eval(throw new RuntimeException("boom")).runCollect)
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[RuntimeException])
      },
      test("Stream.attemptEval captures throwable as typed error") {
        val result = Stream.attemptEval(throw new RuntimeException("oops")).runCollect
        assertTrue(result.isLeft) &&
        assertTrue(result.left.exists(_.getMessage == "oops"))
      },
      test("Stream.attemptEval succeeds with no elements on success") {
        var executed = false
        val result   = Stream.attemptEval { executed = true }.runCollect
        assertTrue(executed) && assert(result)(equalTo(Right(Chunk.empty)))
      },
      test("Stream.defer registers release action on close") {
        var released = false
        val result   = Stream.defer { released = true }.runCollect
        assert(result)(equalTo(Right(Chunk.empty))) && assertTrue(released)
      },
      test("Stream.defer release runs after preceding elements") {
        var released = false
        val result   = (Stream.range(0, 3) ++ Stream.defer { released = true }).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2)))) && assertTrue(released)
      },
      test("nested defers run in LIFO order") {
        val order  = scala.collection.mutable.ListBuffer.empty[Int]
        val result = (
          Stream.defer(order += 1) ++
            Stream.range(0, 2) ++
            Stream.defer(order += 2)
        ).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1)))) &&
        assert(order.toList)(equalTo(List(1, 2)))
      },
      test("concat of defers have distinct lifetimes") {
        val order  = scala.collection.mutable.ListBuffer.empty[String]
        val stream =
          Stream.defer(order += "a-release") ++
            Stream.succeed(1) ++
            Stream.defer(order += "b-release") ++
            Stream.succeed(2)
        val result = stream.runCollect
        assert(result)(equalTo(Right(Chunk(1, 2)))) &&
        assert(order.toList)(equalTo(List("a-release", "b-release")))
      }
    ),

    // ---- Suite 2: catchAll ----

    suite("catchAll")(
      test("catches error and switches to fallback stream") {
        val result = Stream.fail("e").catchAll(_ => Stream.succeed(1)).runCollect
        assert(result)(equalTo(Right(Chunk(1))))
      },
      test("catches error after some elements") {
        val result = (Stream.range(0, 5) ++ Stream.fail("e")).catchAll(_ => Stream.succeed(99)).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4, 99))))
      },
      test("fallback can also fail") {
        val result = Stream.fail("e").catchAll((_: String) => Stream.fail("e2")).runCollect
        assert(result)(equalTo(Left("e2")))
      },
      test("does not catch when no error") {
        val result = Stream.succeed(1).catchAll((_: Nothing) => Stream.succeed(2)).runCollect
        assert(result)(equalTo(Right(Chunk(1))))
      },
      test("passes the error value to the handler") {
        val result = Stream.fail("hello").catchAll((e: String) => Stream.succeed(e.length)).runCollect
        assert(result)(equalTo(Right(Chunk(5))))
      }
    ),

    // ---- Suite 3: catchDefect ----

    suite("catchDefect")(
      test("catches RuntimeException from die") {
        val result = Stream
          .die(new RuntimeException("boom"))
          .catchDefect { case _: RuntimeException => Stream.succeed(42) }
          .runCollect
        assert(result)(equalTo(Right(Chunk(42))))
      },
      test("does NOT catch typed StreamError") {
        val result = Stream.fail("typed").catchDefect { case _ => Stream.empty }.runCollect
        assert(result)(equalTo(Left("typed")))
      },
      test("non-matching defect is rethrown") {
        // Use an Exception subclass that doesn't match the PartialFunction
        val t      = new java.io.IOException("io error")
        val result = try {
          Stream.die(t).catchDefect { case _: RuntimeException => Stream.succeed(42) }.runCollect
          "no exception"
        } catch {
          case e: java.io.IOException => e.getMessage
        }
        assert(result)(equalTo("io error"))
      }
    ),

    // ---- Suite 4: orElse / || ----

    suite("orElse / ||")(
      test("fail || succeed produces the succeed") {
        val result = (Stream.fail("e") || Stream.succeed(1)).runCollect
        assert(result)(equalTo(Right(Chunk(1))))
      },
      test("succeed || succeed produces the first") {
        val result = (Stream.succeed(1) || Stream.succeed(2)).runCollect
        assert(result)(equalTo(Right(Chunk(1))))
      },
      test("orElse is the same as ||") {
        val result = Stream.fail("e").orElse(Stream.succeed(99)).runCollect
        assert(result)(equalTo(Right(Chunk(99))))
      }
    ),

    // ---- Suite 5: Error + combinators ----

    suite("Error + combinators")(
      test("error in map, caught by catchAll") {
        val result = Stream
          .range(0, 3)
          .map { (_: Int) => throw new StreamError("e"); 0 }
          .catchAll((_: String) => Stream.succeed(99))
          .runCollect
        assert(result)(equalTo(Right(Chunk(99))))
      },
      test("error in flatMap inner stream, caught by catchAll") {
        val result = Stream
          .range(0, 10)
          .flatMap((i: Int) => if (i == 5) Stream.fail("e") else Stream.succeed(i))
          .catchAll((_: String) => Stream.empty)
          .runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4))))
      },
      test("error in concat, caught by catchAll") {
        val result = (Stream.range(0, 3) ++ Stream.fail("e"))
          .catchAll((_: String) => Stream.range(10, 13))
          .runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2, 10, 11, 12))))
      }
    ),

    // ---- Suite 6: Resource cleanup ----

    suite("Resource cleanup")(
      test("catchAll closes upstream reader on switch") {
        var closed   = false
        val upstream = Stream.fromReader[String, Int](new Reader[Int] {
          private var done                      = false
          def isClosed: Boolean                 = done
          def read[A1 >: Int](sentinel: A1): A1 = { done = true; throw new StreamError("err") }
          def close(): Unit                     = closed = true
        })
        val result = upstream.catchAll((_: String) => Stream.succeed(42)).runCollect
        assert(result)(equalTo(Right(Chunk(42)))) &&
        assert(closed)(isTrue)
      },
      test("ConcatWith closes first reader on switch") {
        var closed = false
        val first  = Stream.fromReader[Nothing, Int](new Reader[Int] {
          private var emitted                   = false
          def isClosed: Boolean                 = emitted
          def read[A1 >: Int](sentinel: A1): A1 = if (!emitted) { emitted = true; Int.box(1).asInstanceOf[A1] }
          else sentinel
          def close(): Unit = closed = true
        })
        val result = (first ++ Stream.succeed(2)).runCollect
        assert(result)(equalTo(Right(Chunk(1, 2)))) &&
        assert(closed)(isTrue)
      },
      test("ensuring calls finalizer on normal completion") {
        var finalized = false
        val result    = Stream.range(0, 3).ensuring { finalized = true }.runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2)))) &&
        assert(finalized)(isTrue)
      },
      test("ensuring calls finalizer on error") {
        var finalized = false
        val result    = Stream.fail("err").ensuring { finalized = true }.runCollect
        assert(result)(equalTo(Left("err"))) &&
        assert(finalized)(isTrue)
      },
      test("fromAcquireRelease calls release on error during use compile") {
        var released = false
        val result   = try {
          Stream
            .fromAcquireRelease("resource", (_: String) => { released = true }) { _ =>
              throw new RuntimeException("compile failed")
              Stream.empty
            }
            .runCollect
          "no exception"
        } catch {
          case _: RuntimeException => "caught"
        }
        assert(result)(equalTo("caught")) &&
        assert(released)(isTrue)
      },
      test("fromAcquireRelease calls release on normal close") {
        var released = false
        val result   = Stream
          .fromAcquireRelease("res", (_: String) => { released = true }) { r =>
            Stream.succeed(r)
          }
          .runCollect
        assert(result)(equalTo(Right(Chunk("res")))) &&
        assert(released)(isTrue)
      }
    ),

    // ---- Suite 7: mapAccum ----

    suite("mapAccum")(
      test("cumulative sum") {
        val result = Stream
          .range(0, 5)
          .mapAccum(0) { (sum, a) =>
            val s = sum + a; (s, s)
          }
          .runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 3, 6, 10))))
      },
      test("running index") {
        val result = Stream
          .fromIterable(List("a", "b", "c"))
          .mapAccum(0)((idx, s) => (idx + 1, s"$idx:$s"))
          .runCollect
        assert(result)(equalTo(Right(Chunk("0:a", "1:b", "2:c"))))
      }
    ),

    // ---- Suite 8: Sink errors ----

    suite("Sink errors")(
      test("Sink.fail(e) produces Left(e)") {
        val result = Stream.succeed(1).run(Sink.fail("e"))
        assert(result)(equalTo(Left("e")))
      },
      test("Sink.mapError transforms the error value") {
        val result = Stream.fail(42).mapError(_.toString).run(Sink.drain)
        assert(result)(equalTo(Left("42")))
      }
    ),

    // ---- Suite 9: fromReader ----

    suite("fromReader")(
      test("fromReader wraps a reader as a stream") {
        val result = Stream.fromReader[Nothing, Int](Reader.fromRange(0 until 3)).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2))))
      }
    )
  )
}
