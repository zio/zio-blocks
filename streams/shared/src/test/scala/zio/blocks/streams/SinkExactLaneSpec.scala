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
import scala.collection.mutable.ListBuffer

import zio.ZIO
import zio.blocks.async.Async
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * The terminal half of the exact-lane proof. Each case gets a fresh hostile
 * reader: generic and seven wrong primitive entry points throw, while Long and
 * Double scalar entry points throw even on their matching lane.
 */
object SinkExactLaneSpec extends StreamsBaseSpec {
  private[streams] final class State(val lane: JvmType, initial: List[Any]) {
    private var values                                                  = initial
    val calls                                                           = ListBuffer.empty[String]
    def available                                                       = values.nonEmpty
    def fail[A](name: String): A                                        = throw new AssertionError(s"$lane probe received $name")
    def one[A](expected: JvmType, name: String, eof: A)(f: Any => A): A =
      if (lane != expected) fail(name)
      else if (values.isEmpty) eof
      else { val a = f(values.head); values = values.tail; calls += name; a }
    def bulk[A](expected: JvmType, name: String, dest: Array[A], offset: Int, length: Int)(f: Any => A): Int =
      if (lane != expected) fail(name)
      else if (length == 0) 0
      else one(expected, name, -1) { value => dest(offset) = f(value); 1 }
  }

  private[streams] trait ExactMethods {
    protected def state: State
    final protected def bool(eof: Int) =
      state.one(JvmType.Boolean, "readBoolean", eof)(x => if (x.asInstanceOf[Boolean]) 1 else 0)
    final protected def byte()             = state.one(JvmType.Byte, "readByte", -1)(_.asInstanceOf[Byte].toInt & 0xff)
    final protected def char(eof: Int)     = state.one(JvmType.Char, "readChar", eof)(_.asInstanceOf[Char].toInt)
    final protected def short(eof: Int)    = state.one(JvmType.Short, "readShort", eof)(_.asInstanceOf[Short].toInt)
    final protected def int(eof: Long)     = state.one(JvmType.Int, "readInt", eof)(_.asInstanceOf[Int].toLong)
    final protected def float(eof: Double) = state.one(JvmType.Float, "readFloat", eof)(_.asInstanceOf[Float].toDouble)
  }

  private[streams] final class SyncProbe(protected val state: State) extends Reader.SyncReader[Any] with ExactMethods {
    override def jvmType                                                              = state.lane
    def close()                                                                       = (); def isClosed = false; override def readable() = state.available
    def read[A >: Any](eof: A): A                                                     = state.fail("read")
    override def readBoolean(eof: Int)(implicit ev: Any <:< Boolean)                  = bool(eof)
    override def readByte()                                                           = byte()
    override def readChar(eof: Int)(implicit ev: Any <:< Char)                        = char(eof)
    override def readShort(eof: Int)(implicit ev: Any <:< Short)                      = short(eof)
    override def readInt(eof: Long)(implicit ev: Any <:< Int)                         = int(eof)
    override def readLong(eof: Long)(implicit ev: Any <:< Long)                       = state.fail("readLong(scalar)")
    override def readLongs(a: Array[Long], o: Int, n: Int)(implicit ev: Any <:< Long) =
      state.bulk(JvmType.Long, "readLongs", a, o, n)(_.asInstanceOf[Long])
    override def readFloat(eof: Double)(implicit ev: Any <:< Float)                         = float(eof)
    override def readDouble(eof: Double)(implicit ev: Any <:< Double)                       = state.fail("readDouble(scalar)")
    override def readDoubles(a: Array[Double], o: Int, n: Int)(implicit ev: Any <:< Double) =
      state.bulk(JvmType.Double, "readDoubles", a, o, n)(_.asInstanceOf[Double])
  }

  private[streams] final class AsyncProbe(protected val state: State)
      extends Reader.AsyncReader[Any]
      with ExactMethods {
    override def jvmType                                                              = state.lane
    def close()                                                                       = Async.succeed(()); def isClosed = Async.succeed(false);
    def readable()                                                                    = Async.succeed(state.available)
    def read[A >: Any](eof: A): Async[A]                                              = state.fail("read")
    override def readBoolean(eof: Int)(implicit ev: Any <:< Boolean)                  = Async.succeed(bool(eof))
    override def readByte()                                                           = Async.succeed(byte())
    override def readBytes(a: Array[Byte], o: Int, n: Int)(implicit ev: Any <:< Byte) =
      Async.succeed(state.bulk(JvmType.Byte, "readBytes", a, o, n)(_.asInstanceOf[Byte]))
    override def readChar(eof: Int)(implicit ev: Any <:< Char)                     = Async.succeed(char(eof))
    override def readShort(eof: Int)(implicit ev: Any <:< Short)                   = Async.succeed(short(eof))
    override def readInt(eof: Long)(implicit ev: Any <:< Int)                      = Async.succeed(int(eof))
    override def readInts(a: Array[Int], o: Int, n: Int)(implicit ev: Any <:< Int) =
      Async.succeed(state.bulk(JvmType.Int, "readInts", a, o, n)(_.asInstanceOf[Int]))
    override def readLong(eof: Long)(implicit ev: Any <:< Long): Async[Long]          = state.fail("readLong(scalar)")
    override def readLongs(a: Array[Long], o: Int, n: Int)(implicit ev: Any <:< Long) =
      Async.succeed(state.bulk(JvmType.Long, "readLongs", a, o, n)(_.asInstanceOf[Long]))
    override def readFloat(eof: Double)(implicit ev: Any <:< Float)                      = Async.succeed(float(eof))
    override def readFloats(a: Array[Float], o: Int, n: Int)(implicit ev: Any <:< Float) =
      Async.succeed(state.bulk(JvmType.Float, "readFloats", a, o, n)(_.asInstanceOf[Float]))
    override def readDouble(eof: Double)(implicit ev: Any <:< Double): Async[Double]        = state.fail("readDouble(scalar)")
    override def readDoubles(a: Array[Double], o: Int, n: Int)(implicit ev: Any <:< Double) =
      Async.succeed(state.bulk(JvmType.Double, "readDoubles", a, o, n)(_.asInstanceOf[Double]))
  }

  private[streams] val lanes: List[(JvmType, Any)] = List(
    JvmType.Boolean -> true,
    JvmType.Byte    -> Byte.MinValue,
    JvmType.Char    -> '\uffff',
    JvmType.Short   -> Short.MinValue,
    JvmType.Int     -> Int.MinValue,
    JvmType.Long    -> Long.MinValue,
    JvmType.Float   -> java.lang.Float.intBitsToFloat(0x7fc00001),
    JvmType.Double  -> java.lang.Double.longBitsToDouble(0x7ff8000000000001L)
  )

  private def asynchronousSinks[A]: List[(String, Sink[Nothing, A, _])] = List(
    "existsAsync"   -> Sink.existsAsync[A](_ => Async.succeed(true)),
    "findAsync"     -> Sink.findAsync[A](_ => Async.succeed(true)),
    "foldLeftAsync" -> Sink.foldLeftAsync[A, Int](0)((n, _) => Async.succeed(n + 1)),
    "forallAsync"   -> Sink.forallAsync[A](_ => Async.succeed(false)),
    "foreachAsync"  -> Sink.foreachAsync[A](_ => Async.succeed(()))
  )

  private def sinks[A]: List[(String, Sink[Nothing, A, _])] = synchronousSinks[A] ++ asynchronousSinks[A]

  private def synchronousSinks[A]: List[(String, Sink[Nothing, A, _])] = List(
    "collectAll" -> Sink.collectAll[A],
    "count"      -> Sink.count,
    "drain"      -> Sink.drain,
    "foldLeft"   -> Sink.foldLeft[A, Int](0)((n, _) => n + 1),
    "foreach"    -> Sink.foreach[A](_ => ()),
    "exists"     -> Sink.exists[A](_ => true),
    "forall"     -> Sink.forall[A](_ => false),
    "find"       -> Sink.find[A](_ => true),
    "head"       -> Sink.head[A],
    "last"       -> Sink.last[A],
    "take"       -> Sink.take[A](1)
  )

  private def syncMatrix: TestResult = {
    val checks = for { (lane, value) <- lanes; (name, sink) <- synchronousSinks[Any] } yield {
      val state = new State(lane, List(value)); sink.drain(new SyncProbe(state))
      assertTrue(state.calls.nonEmpty).label(s"sync/$lane/$name")
    }
    checks.reduce(_ && _)
  }

  private def asyncMatrix: ZIO[Any, Throwable, TestResult] =
    ZIO
      .foreach(lanes) { case (lane, value) =>
        ZIO
          .foreach(sinks[Any]) { case (name, sink) =>
            val state = new State(lane, List(value))
            runAsync(sink.drain(new AsyncProbe(state))).as(assertTrue(state.calls.nonEmpty).label(s"async/$lane/$name"))
          }
          .map(_.reduce(_ && _))
      }
      .map(_.reduce(_ && _))

  private def expectedChunk(lane: JvmType, value: Any): Chunk[Any] = (lane match {
    case JvmType.Boolean =>
      val b = new ChunkBuilder.Boolean(); b.addOne(value.asInstanceOf[Boolean]); b.addOne(value.asInstanceOf[Boolean]);
      b.result()
    case JvmType.Byte =>
      val b = new ChunkBuilder.Byte(); b.addOne(value.asInstanceOf[Byte]); b.addOne(value.asInstanceOf[Byte]);
      b.result()
    case JvmType.Char =>
      val b = new ChunkBuilder.Char(); b.addOne(value.asInstanceOf[Char]); b.addOne(value.asInstanceOf[Char]);
      b.result()
    case JvmType.Short =>
      val b = new ChunkBuilder.Short(); b.addOne(value.asInstanceOf[Short]); b.addOne(value.asInstanceOf[Short]);
      b.result()
    case JvmType.Int =>
      val b = new ChunkBuilder.Int(); b.addOne(value.asInstanceOf[Int]); b.addOne(value.asInstanceOf[Int]); b.result()
    case JvmType.Long =>
      val b = new ChunkBuilder.Long(); b.addOne(value.asInstanceOf[Long]); b.addOne(value.asInstanceOf[Long]);
      b.result()
    case JvmType.Float =>
      val b = new ChunkBuilder.Float(); b.addOne(value.asInstanceOf[Float]); b.addOne(value.asInstanceOf[Float]);
      b.result()
    case JvmType.Double =>
      val b = new ChunkBuilder.Double(); b.addOne(value.asInstanceOf[Double]); b.addOne(value.asInstanceOf[Double]);
      b.result()
    case other => throw new AssertionError(s"not a primitive lane: $other")
  }).asInstanceOf[Chunk[Any]]

  private def sameValue(lane: JvmType, actual: Any, expected: Any): Boolean = lane match {
    case JvmType.Float =>
      java.lang.Float.floatToRawIntBits(actual.asInstanceOf[Float]) ==
        java.lang.Float.floatToRawIntBits(expected.asInstanceOf[Float])
    case JvmType.Double =>
      java.lang.Double.doubleToRawLongBits(actual.asInstanceOf[Double]) ==
        java.lang.Double.doubleToRawLongBits(expected.asInstanceOf[Double])
    case _ => actual == expected
  }

  def spec = suite("Sink exact primitive lanes")(
    test("all direct-pull behavior families use every exact synchronous lane")(syncMatrix),
    test("all direct-pull behavior families use every exact asynchronous lane")(asyncMatrix),
    test("async take short-circuits through every exact lane and retains specialized chunks") {
      ZIO
        .foreach(lanes) { case (lane, value) =>
          val state    = new State(lane, List(value, value, value))
          val expected = expectedChunk(lane, value)
          runAsync(Sink.take[Any](2).drain(new AsyncProbe(state))).map { result =>
            val resultClass   = result.getClass.getName
            val expectedClass = expected.getClass.getName
            assertTrue(
              result.length == 2,
              sameValue(lane, result(0), value),
              sameValue(lane, result(1), value),
              resultClass == expectedClass,
              state.calls.length == 2
            ).label(s"async take/$lane")
          }
        }
        .map(_.reduce(_ && _))
    },
    test("lane-specific writers and numeric sums use authoritative physical pulls") {
      val out                                                = new ByteArrayOutputStream; val text = new StringWriter
      val cases: List[(JvmType, Any, Sink[Nothing, Any, _])] = List(
        (JvmType.Byte, 1.toByte, Sink.fromOutputStream(out)),
        (JvmType.Char, 'x', Sink.fromJavaWriter(text)),
        (JvmType.Int, 1, Sink.sumInt),
        (JvmType.Long, Long.MinValue, Sink.sumLong),
        (JvmType.Float, 1.0f, Sink.sumFloat),
        (JvmType.Double, Double.NaN, Sink.sumDouble)
      ).asInstanceOf[List[(JvmType, Any, Sink[Nothing, Any, _])]]
      val checks = cases.map { case (lane, value, sink) =>
        val state = new State(lane, List(value)); sink.drain(new SyncProbe(state)); assertTrue(state.calls.nonEmpty)
      }
      checks.reduce(_ && _) && assertTrue(out.toByteArray.toList == List[Byte](1), text.toString == "x")
    },
    test("lane-specific writers and numeric sums use authoritative asynchronous pulls") {
      val out   = new ByteArrayOutputStream; val text = new StringWriter
      val cases = List[(JvmType, Any, Sink[Nothing, Any, _])](
        (JvmType.Byte, 1.toByte, Sink.fromOutputStream(out).asInstanceOf[Sink[Nothing, Any, Unit]]),
        (JvmType.Char, 'x', Sink.fromJavaWriter(text).asInstanceOf[Sink[Nothing, Any, Unit]]),
        (JvmType.Int, 1, Sink.sumInt.asInstanceOf[Sink[Nothing, Any, Long]]),
        (JvmType.Long, Long.MinValue, Sink.sumLong.asInstanceOf[Sink[Nothing, Any, Long]]),
        (JvmType.Float, 1.0f, Sink.sumFloat.asInstanceOf[Sink[Nothing, Any, Double]]),
        (JvmType.Double, Double.NaN, Sink.sumDouble.asInstanceOf[Sink[Nothing, Any, Double]])
      )
      ZIO
        .foreach(cases) { case (lane, value, sink) =>
          val state = new State(lane, List(value));
          runAsync(sink.drain(new AsyncProbe(state))).as(assertTrue(state.calls.nonEmpty))
        }
        .map(_.reduce(_ && _) && assertTrue(out.toByteArray.toList == List[Byte](1), text.toString == "x"))
    }
  )
}
