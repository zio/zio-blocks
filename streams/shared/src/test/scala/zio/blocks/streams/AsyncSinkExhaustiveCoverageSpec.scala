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

package zio.blocks.streams

import java.io.{ByteArrayOutputStream, StringWriter}
import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncSinkExhaustiveCoverageSpec extends StreamsBaseSpec {
  // Exact residual checklist from /tmp/coverage-gaps-current3.txt:
  // 62 -> mapErrorAsync Nothing identity branch.
  // 175,179 -> readerCallbackAsync mark: thrown/null effects and trusted/untrusted failed effects.
  // 408-410 -> createAsync synchronous drain; 426-427 -> createBoth synchronous drain.
  // 443-444,450,452 -> drain's Long, Byte, and reference synchronous lanes.
  // 879-880,895-896 -> fromJavaWriter/fromOutputStream asynchronous drains and callbacks.
  // 1216 -> ContramappedAsync synchronous drain; 1229 -> ErrorMapped asynchronous drain.
  // 1239 -> ErrorMappedAsync synchronous drain; 1253-1256 -> FoldLeftDouble asynchronous success/error.
  // 1341-1344 -> FoldLeftInt asynchronous success/error; 1443-1446 -> FoldLeftLong asynchronous success/error.
  // 1567 -> MappedAsync synchronous drain.

  private def sync[A: JvmType.Infer](values: A*): Reader.SyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).expectedSync

  private def async[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] = sync(values: _*).toAsync

  private def thrown(effect: => Any): Throwable =
    try { effect; new AssertionError("expected failure") }
    catch { case cause: Throwable => cause }

  private class SignatureReader(fault: String = "", trusted: Boolean = false) extends Reader.AsyncReader[Any] {
    private def result[A](name: String, value: => A): Async[A] =
      if (fault != name && !(name == "read" && (fault == "throw" || fault == "null"))) Async.succeed(value)
      else if (trusted) Async.failTrusted(StreamError.source(name))
      else
        fault match {
          case "throw" => throw new IllegalStateException("throw")
          case "null"  => null
          case _       => Async.fail(new IllegalArgumentException(name))
        }
    override def jvmType: JvmType                                                                 = JvmType.AnyRef
    def close()                                                                                   = result("close", ())
    def isClosed                                                                                  = result("isClosed", false)
    def readable()                                                                                = result("readable", true)
    def read[A >: Any](sentinel: A)                                                               = result("read", sentinel)
    override def readAll[A >: Any]()                                                              = result("readAll", Chunk.empty[A])
    override def readN[A >: Any](n: Int)                                                          = result("readN", Chunk.empty[A])
    override def readUpToN[A >: Any](n: Int)                                                      = result("readUpToN", Chunk.empty[A])
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean)                         = result("readBoolean", sentinel)
    override def readByte()                                                                       = result("readByte", -1)
    override def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Any <:< Byte)    = result("readBytes", -1)
    override def readInts(buf: Array[Int], offset: Int, len: Int)(implicit ev: Any <:< Int)       = result("readInts", -1)
    override def readLongs(buf: Array[Long], offset: Int, len: Int)(implicit ev: Any <:< Long)    = result("readLongs", -1)
    override def readFloats(buf: Array[Float], offset: Int, len: Int)(implicit ev: Any <:< Float) =
      result("readFloats", -1)
    override def readDoubles(buf: Array[Double], offset: Int, len: Int)(implicit ev: Any <:< Double) =
      result("readDoubles", -1)
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char)        = result("readChar", sentinel)
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short)      = result("readShort", sentinel)
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int)         = result("readInt", sentinel)
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long)       = result("readLong", sentinel)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float)   = result("readFloat", sentinel)
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double) = result("readDouble", sentinel)
    override def reset()                                                   = result("reset", ())
    override def setLimit(n: Long)                                         = result("setLimit", true)
    override def setRepeat()                                               = result("setRepeat", true)
    override def setSkip(n: Long)                                          = result("setSkip", true)
    override def skip(n: Long)                                             = result("skip", ())
  }

  private val allSignatures = Sink.createAsync[Nothing, Any, Vector[Any]] { r =>
    val effects = List[Async[Any]](
      r.close(),
      r.isClosed,
      r.readable(),
      r.read("eof"),
      r.readAll(),
      r.readN(1),
      r.readUpToN(1),
      r.reset(),
      r.setLimit(1),
      r.setRepeat(),
      r.setSkip(1),
      r.skip(1)
    )
    Async.collectAll(effects).map(_.toVector)
  }

  def spec = suite("Async Sink exhaustive residual coverage")(
    test("every protected AsyncReader signature is marked and successful results are unchanged") {
      runAsync(allSignatures.drain(new SignatureReader)).map { actual =>
        assertTrue(actual.length == 12, actual(1) == false, actual(2) == true, actual(3) == "eof")
      }
    },
    test("mark normalizes throw, null, ordinary failure and trusted typed failure") {
      val one           = Sink.createAsync[Nothing, Any, Any](_.read("eof"))
      val thrownTrusted = StreamError.source("thrown-trusted")
      for {
        thrownResult        <- runAsync(one.drain(new SignatureReader("throw")).either)
        nullResult          <- runAsync(one.drain(new SignatureReader("null")).either)
        failedResult        <- runAsync(one.drain(new SignatureReader("read")).either)
        trustedResult       <- runAsync(one.drain(new SignatureReader("read", trusted = true)).either)
        thrownTrustedResult <- runAsync(
                                 one
                                   .drain(new SignatureReader {
                                     override def read[A >: Any](sentinel: A): Async[A] = throw thrownTrusted
                                   })
                                   .either
                               )
      } yield assertTrue(
        thrownResult.left.exists(_.getMessage == "throw"),
        nullResult.left.exists(_.isInstanceOf[NullPointerException]),
        failedResult.left.exists(_.getMessage == "read"),
        trustedResult.left.exists {
          case error: StreamError => error.isTrusted && error.value == "read"
          case _                  => false
        },
        thrownTrustedResult == Left(thrownTrusted)
      )
    },
    test("callback normalization rejects callback throw and null") {
      val boom     = new RuntimeException("callback")
      val throwing = Sink.createAsync[Nothing, Int, Unit](_ => throw boom)
      val nil      = Sink.createAsync[Nothing, Int, Unit](_ => null)
      for {
        a <- runAsync(throwing.drain(async(1)).either)
        b <- runAsync(nil.drain(async(1)).either)
      } yield assertTrue(a == Left(boom), b.left.exists(_.isInstanceOf[NullPointerException]))
    },
    test("protected close preserves ordinary and trusted close failures and rejects null") {
      val close = Sink.createAsync[Nothing, Any, Unit](_.close())
      for {
        ordinary <- runAsync(close.drain(new SignatureReader("close")).either)
        trusted  <- runAsync(close.drain(new SignatureReader("close", trusted = true)).either)
        nil      <- runAsync(
                 close
                   .drain(new SignatureReader("null") {
                     override def close(): Async[Unit] = null
                   })
                   .either
               )
      } yield assertTrue(
        ordinary.left.exists(_.getMessage == "close"),
        trusted.left.exists { case e: StreamError => e.isTrusted && e.value == "close"; case _ => false },
        nil.left.exists(_.isInstanceOf[NullPointerException])
      )
    },
    test("createAsync, createBoth and async wrappers execute their synchronous drains") {
      val create             = Sink.createAsync[Nothing, Int, Int](_.readInt(-1).map(_.toInt))
      val both               = Sink.createBoth[Nothing, Int, Int](_.readInt(-1).toInt, _ => Async.succeed(99))
      val contra             = Sink.foldLeft[Int, Int](0)(_ + _).contramapAsync[Int, String](s => Async.succeed(s.toInt))
      val mapped             = Sink.foldLeft[Int, Int](0)(_ + _).mapAsync(n => Async.succeed(n * 3))
      val error              = Sink.fail("bad").mapErrorAsync(s => Async.succeed(s.length))
      val identity           = Sink.drain.mapErrorAsync((n: Nothing) => Async.succeed(n))
      val platformAssertions =
        if (TestPlatform.isJVM) {
          val mappedFailure = thrown(error.drain(sync(1)))
          assertTrue(
            create.drain(sync(7)) == 7,
            both.drain(sync(8)) == 8,
            contra.drain(sync("2", "3")) == 5,
            mapped.drain(sync(2, 3)) == 15,
            mappedFailure match {
              case streamError: StreamError => streamError.value == 3
              case _                        => false
            }
          )
        } else {
          val failures = List(
            thrown(create.drain(sync(7))),
            thrown(contra.drain(sync("2", "3"))),
            thrown(mapped.drain(sync(2, 3))),
            thrown(error.drain(sync(1)))
          )
          assertTrue(
            failures.forall(cause =>
              cause.isInstanceOf[IllegalStateException] && cause.getMessage ==
                "an async-only sink cannot be synchronously drained on Scala.js"
            )
          )
        }
      platformAssertions && assertTrue(identity.asInstanceOf[AnyRef] eq Sink.drain.asInstanceOf[AnyRef])
    },
    test("drain consumes exact Long, Float, Byte and reference synchronous lanes") {
      val long = sync(1L, 2L); val float = sync(1.0f, 2.0f); val byte = sync[Byte](1, 2); val ref = sync("a", "b")
      Sink.drain.drain(long); Sink.drain.drain(float); Sink.drain.drain(byte); Sink.drain.drain(ref)
      assertTrue(
        long.readLong(-1) == -1,
        float.readFloat(-1) == -1,
        byte.readByte() == -1,
        ref.read("eof") == "eof"
      )
    },
    test("writer sinks consume asynchronous Char and Byte lanes exactly") {
      val writer = new StringWriter; val bytes = new ByteArrayOutputStream
      for {
        a <- runAsync(Sink.fromJavaWriter(writer).drain(async('A', '\u03bb')))
        b <- runAsync(Sink.fromOutputStream(bytes).drain(async[Byte](0, -1, 127)))
      } yield assertTrue(
        a == (),
        b == (),
        writer.toString == "A\u03bb",
        bytes.toByteArray.toVector == Vector[Byte](0, -1, 127)
      )
    },
    test("all primitive specialized asynchronous fold lanes succeed and untrust callback StreamError") {
      val forged    = StreamError.source("forged")
      val intOk     = new Sink.FoldLeftInt[Int](1, _ + _)
      val longOk    = new Sink.FoldLeftLong[Long](1L, _ + _)
      val doubleOk  = new Sink.FoldLeftDouble[Double](1d, _ + _)
      val intBad    = new Sink.FoldLeftInt[Int](0, (_, _) => throw forged)
      val longBad   = new Sink.FoldLeftLong[Long](0L, (_, _) => throw forged)
      val doubleBad = new Sink.FoldLeftDouble[Double](0d, (_, _) => throw forged)
      for {
        i  <- runAsync(intOk.drain(async(2, 3))); l       <- runAsync(longOk.drain(async(2L, 3L)))
        d  <- runAsync(doubleOk.drain(async(2d, 3d)))
        ib <- runAsync(intBad.drain(async(1)).either); lb <- runAsync(longBad.drain(async(1L)).either)
        db <- runAsync(doubleBad.drain(async(1d)).either)
      } yield assertTrue(
        i == 6,
        l == 6L,
        d == 6d,
        ib.left.exists { case e: StreamError => !e.isTrusted; case _ => false },
        lb.left.exists { case e: StreamError => !e.isTrusted; case _ => false },
        db.left.exists { case e: StreamError => !e.isTrusted; case _ => false }
      )
    },
    test("mapError and mapErrorAsync distinguish typed failure, defect and cleanup failure") {
      val defect  = new RuntimeException("defect")
      val cleanup = StreamError.source("cleanup")
      StreamError.attachCleanup(cleanup, defect)
      val typed       = Sink.fail("typed").mapError(_.length)
      val asyncTyped  = Sink.fail("typed").mapErrorAsync(s => Async.succeed(s.length))
      val defectSink  = Sink.createAsync[String, Int, Unit](_ => Async.fail(defect)).mapError(_.length)
      val cleanupSink =
        Sink.createAsync[String, Int, Unit](_ => Async.failTrusted(cleanup)).mapErrorAsync(s => Async.succeed(s.length))
      for {
        a <- runAsync(typed.drain(async(1)).either); b      <- runAsync(asyncTyped.drain(async(1)).either)
        c <- runAsync(defectSink.drain(async(1)).either); d <- runAsync(cleanupSink.drain(async(1)).either)
      } yield assertTrue(
        a.left.exists { case e: StreamError => e.value == 5; case _ => false },
        b.left.exists { case e: StreamError => e.value == 5; case _ => false },
        c == Left(defect),
        d.left.exists { case e: StreamError => e.cleanupFailed; case _ => false }
      )
    },
    test("pending reader and callback effects resume exactly once without polling delays") {
      val readGate     = new Completer[Any]
      val callbackGate = new Completer[Int]
      val reads        = new AtomicInteger
      val reader       = new SignatureReader {
        override def read[A >: Any](sentinel: A): Async[A] = {
          reads.incrementAndGet(); readGate.asInstanceOf[Async[A]]
        }
      }
      val sink    = Sink.createAsync[Nothing, Any, Int](_.read("eof").flatMap(_ => callbackGate))
      val running = runAsync(sink.drain(reader))
      for {
        fiber  <- running.fork
        _      <- zio.ZIO.succeed(readGate.succeed("ready"))
        _      <- zio.ZIO.succeed(callbackGate.succeed(42))
        result <- fiber.join
      } yield assertTrue(result == 42, reads.get() == 1)
    }
  )
}
