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

package zio.blocks.async

import scala.collection.mutable.ArrayBuffer
import zio.blocks.streams.{StreamsBaseSpec, _}
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Bounded, replayable JVM lifecycle schedules. This deliberately drives
 * Pollable values directly: a scheduler delay must never be the oracle for
 * these races.
 */
object AsyncReaderLinearizabilitySpec extends StreamsBaseSpec {
  private final case class Event(name: String, phase: String)
  private final class History {
    private val events                           = ArrayBuffer.empty[Event]
    def apply(name: String)(body: => Unit): Unit = {
      events += Event(name, "invoke")
      body
      events += Event(name, "return")
    }
    def snapshot: Vector[Event] = events.toVector
  }

  private def schedules[A](events: Vector[A]): Vector[Vector[A]] = events.permutations.toVector

  /**
   * A completed sequential history is linearizable iff its operation order is
   * accepted by the tiny sequential specification supplied by the test.
   */
  private def linearizable(history: Vector[Event])(specification: Vector[String] => Boolean): Boolean = {
    val invocations = history.collect { case Event(name, "invoke") => name }
    history.count(_.phase == "return") == invocations.length && specification(invocations)
  }

  private final class PendingInt extends Reader.AsyncReader[Int] {
    val result                                                             = new Completer[Int]
    var closes                                                             = 0
    override def jvmType                                                   = JvmType.Int
    def close(): Async[Unit]                                               = { closes += 1; Async.succeed(()) }
    def isClosed: Async[Boolean]                                           = Async.succeed(closes != 0)
    def readable(): Async[Boolean]                                         = Async.succeed(false)
    def read[A >: Int](eof: A): Async[A]                                   = result.map(_.asInstanceOf[A])
    override def readInt(eof: Long)(implicit ev: Int <:< Int): Async[Long] = result.map(_.toLong)
  }

  private def poll[A](effect: Async[A]): Async[A] = effect match {
    case pending: Pollable[?] => pending.asInstanceOf[Pollable[A]].poll(new Runnable { def run(): Unit = () })
    case complete             => complete
  }

  private def interspersed(source: PendingInt): Reader.AsyncReader[Int] =
    Stream.fromReader[Any, Int](source).intersperse(0).startAsync.block

  /**
   * Hostile physical source: primitive lanes are available only through their
   * bulk method. This catches an accidental fallback to generic or scalar
   * reads.
   */
  private final class PhysicalReader[A](
    values: Vector[A],
    override val jvmType: JvmType,
    closeFailure: Throwable = null
  ) extends Reader.AsyncReader[A] {
    private var index          = 0
    private var closed         = false
    var genericReads           = 0
    var specializedScalarReads = 0
    var specializedBulkReads   = 0
    var closes                 = 0
    def close(): Async[Unit]   = {
      closes += 1; closed = true; if (closeFailure eq null) Async.succeed(()) else Async.fail(closeFailure)
    }
    def isClosed: Async[Boolean]            = Async.succeed(closed)
    def readable(): Async[Boolean]          = Async.succeed(!closed && index < values.length)
    def read[B >: A](sentinel: B): Async[B] = {
      genericReads += 1
      if (jvmType != JvmType.AnyRef) Async.fail(new AssertionError(s"generic read used for $jvmType lane"))
      else if (closed || index == values.length) Async.succeed(sentinel)
      else { val value = values(index); index += 1; Async.succeed(value) }
    }
    private def transfer(length: Int)(put: (Int, A) => Unit): Async[Int] = {
      specializedBulkReads += 1
      if (closed || index == values.length) Async.succeed(-1)
      else {
        val n = math.min(length, values.length - index)
        var i = 0
        while (i < n) { put(i, values(index + i)); i += 1 }
        index += n
        Async.succeed(n)
      }
    }
    private def transferOne(sentinel: Int)(encode: A => Int): Async[Int] = {
      specializedScalarReads += 1
      if (closed || index == values.length) Async.succeed(sentinel)
      else { val value = encode(values(index)); index += 1; Async.succeed(value) }
    }
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      transferOne(sentinel)(value => if (value.asInstanceOf[Boolean]) 1 else 0)
    override def readByte(): Async[Int]                                       = transferOne(-1)(_.asInstanceOf[Byte].toInt & 0xff)
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      transferOne(sentinel)(_.asInstanceOf[Char].toInt)
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      transferOne(sentinel)(_.asInstanceOf[Short].toInt)
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] = {
      specializedScalarReads += 1
      if (closed || index == values.length) Async.succeed(sentinel)
      else { val value = values(index).asInstanceOf[Int]; index += 1; Async.succeed(value.toLong) }
    }
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] = {
      specializedScalarReads += 1
      if (closed || index == values.length) Async.succeed(sentinel)
      else { val value = values(index).asInstanceOf[Long]; index += 1; Async.succeed(value) }
    }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] = {
      specializedScalarReads += 1
      if (closed || index == values.length) Async.succeed(sentinel)
      else { val value = values(index).asInstanceOf[Float]; index += 1; Async.succeed(value.toDouble) }
    }
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] = {
      specializedScalarReads += 1
      if (closed || index == values.length) Async.succeed(sentinel)
      else { val value = values(index).asInstanceOf[Double]; index += 1; Async.succeed(value) }
    }
    override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Async[Int] =
      transfer(length)((i, value) => dest(offset + i) = value.asInstanceOf[Byte])
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Async[Int] =
      transfer(length)((i, value) => dest(offset + i) = value.asInstanceOf[Int])
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      transfer(length)((i, value) => dest(offset + i) = value.asInstanceOf[Long])
    override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Async[Int] =
      transfer(length)((i, value) => dest(offset + i) = value.asInstanceOf[Float])
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      transfer(length)((i, value) => dest(offset + i) = value.asInstanceOf[Double])
  }

  private def boundary[A](source: Reader.AsyncReader[A], buffered: Boolean, separator: A): Reader.AsyncReader[A] = {
    val stream = Stream.fromReader[Nothing, A](source)
    (if (buffered) stream.buffer(3) else stream.intersperse(separator)).startAsync.block
  }

  private def readBulk(reader: Reader.AsyncReader[?], length: Int): Vector[Any] = reader.jvmType match {
    case JvmType.Byte =>
      val a = new Array[Byte](length);
      val n = reader.asInstanceOf[Reader.AsyncReader[Byte]].readBytes(a, 0, length).block;
      a.take(math.max(n, 0)).toVector
    case JvmType.Int =>
      val a = new Array[Int](length); val n = reader.asInstanceOf[Reader.AsyncReader[Int]].readInts(a, 0, length).block;
      a.take(math.max(n, 0)).toVector
    case JvmType.Long =>
      val a = new Array[Long](length);
      val n = reader.asInstanceOf[Reader.AsyncReader[Long]].readLongs(a, 0, length).block;
      a.take(math.max(n, 0)).toVector
    case JvmType.Float =>
      val a = new Array[Float](length);
      val n = reader.asInstanceOf[Reader.AsyncReader[Float]].readFloats(a, 0, length).block;
      a.take(math.max(n, 0)).toVector
    case JvmType.Double =>
      val a = new Array[Double](length);
      val n = reader.asInstanceOf[Reader.AsyncReader[Double]].readDoubles(a, 0, length).block;
      a.take(math.max(n, 0)).toVector
    case _ => Vector.empty
  }

  private def drainBulk(reader: Reader.AsyncReader[?], request: Int): Vector[Any] = {
    val result = Vector.newBuilder[Any]
    var chunk  = readBulk(reader, request)
    var done   = false
    while (!done) {
      if (chunk.nonEmpty) result ++= chunk
      else if (!reader.readable().block) done = true
      if (!done) chunk = readBulk(reader, request)
    }
    result.result()
  }

  private def readScalar(reader: Reader.AsyncReader[?]): Any = reader.jvmType match {
    case JvmType.Boolean => reader.asInstanceOf[Reader.AsyncReader[Boolean]].readBoolean(-1).block != 0
    case JvmType.Byte    => reader.asInstanceOf[Reader.AsyncReader[Byte]].readByte().block.toByte
    case JvmType.Char    => reader.asInstanceOf[Reader.AsyncReader[Char]].readChar(-1).block.toChar
    case JvmType.Short   => reader.asInstanceOf[Reader.AsyncReader[Short]].readShort(Int.MinValue).block.toShort
    case JvmType.Int     => reader.asInstanceOf[Reader.AsyncReader[Int]].readInt(Long.MinValue).block.toInt
    case JvmType.Long    => reader.asInstanceOf[Reader.AsyncReader[Long]].readLong(Long.MinValue).block
    case JvmType.Float   => reader.asInstanceOf[Reader.AsyncReader[Float]].readFloat(Double.NaN).block.toFloat
    case JvmType.Double  => reader.asInstanceOf[Reader.AsyncReader[Double]].readDouble(Double.NaN).block
    case _               => reader.asInstanceOf[Reader.AsyncReader[Any]].read(new Object).block
  }

  private def bits(value: Any): Any = value match {
    case f: Float  => java.lang.Float.floatToRawIntBits(f)
    case d: Double => java.lang.Double.doubleToRawLongBits(d)
    case other     => other
  }

  def spec = suite("AsyncReader deterministic linearizability")(
    test("enumerates every close/completion/callback schedule and rejects post-close publication") {
      val checked = schedules(Vector("complete", "close", "callback")).map { schedule =>
        val source                = new PendingInt
        val reader                = interspersed(source)
        val read                  = reader.readInt(-1L)
        val history               = new History
        var observed: Async[Long] = read
        schedule.foreach {
          case "complete" => history("complete")(source.result.succeed(7))
          case "close"    => history("close")(reader.close().block)
          case "callback" => history("callback") { observed = poll(observed) }
        }
        val value = observed.block
        linearizable(history.snapshot)(_ == schedule) &&
        (value == -1L || value == 7L) &&
        reader.readInt(-1L).block == -1L && source.closes == 1
      }
      assertTrue(checked.length == 6, checked.forall(identity))
    },
    test("close before callback registration linearizes first") {
      val source = new PendingInt
      val reader = interspersed(source)
      val read   = reader.readInt(-1L)
      reader.close().block
      source.result.succeed(1)
      assertTrue(poll(read).block == -1L, source.closes == 1)
    },
    test("close during callback execution has one close winner") {
      val source       = new PendingInt
      val reader       = interspersed(source)
      val read         = reader.readInt(-1L).asInstanceOf[Pollable[Long]]
      val callbackDone = new Completer[Unit]
      var callbacks    = 0
      read.poll(new Runnable {
        def run(): Unit = {
          callbacks += 1
          reader.close().block
          callbackDone.succeed(())
        }
      })
      source.result.succeed(1)
      callbackDone.block
      assertTrue(
        read.poll(new Runnable { def run(): Unit = () }).block == 1L,
        reader.readInt(-1L).block == -1L,
        callbacks == 1,
        source.closes == 1
      )
    },
    test("bounded reset/read schedules admit the sequential reset specification") {
      val results = schedules(Vector("read", "reset")).map { schedule =>
        val reader  = Reader.singleInt(3).toAsync
        val history = new History
        var value   = Long.MinValue
        schedule.foreach {
          case "read"  => history("read") { value = reader.readInt(-1L).block }
          case "reset" => history("reset")(reader.reset().block)
        }
        linearizable(history.snapshot)(_ == schedule) && value == 3L
      }
      assertTrue(results.length == 2, results.forall(identity))
    },
    test("selector winner versus close has exactly one legal winner") {
      val outcomes = schedules(Vector("winner", "close")).map { schedule =>
        val value                                   = new Completer[Int]
        val selector                                = Async.selector(Vector[Async[Int]](value))
        val selected                                = selector.select
        var observed: Either[Throwable, (Int, Int)] = null
        schedule.foreach {
          case "winner" => { value.succeed(9); if (observed == null) observed = selected.either.block }
          case "close"  => selector.shutdown.block
        }
        if (observed == null) observed = selected.either.block
        observed match {
          case Right((0, 9)) => schedule.head == "winner"
          case Left(_)       => schedule.head == "close"
          case _             => false
        }
      }
      assertTrue(outcomes.length == 2, outcomes.forall(identity))
    },
    test("cleanup failure provenance retains null and repeated close identity") {
      def result(cause: Throwable) = {
        val child = new Pollable[Int] {
          def poll(wake: Runnable): Async[Int]                         = this
          override private[async] def cancelWithCleanup(): Async[Unit] = Async.fail(cause)
        }
        val selector = Async.selector(Vector[Async[Int]](child))
        (selector.shutdown.either.block, selector.shutdown.either.block)
      }
      val failure  = new RuntimeException("cleanup")
      val ordinary = result(failure)
      val nulled   = result(null)
      assertTrue(ordinary._1 == Left(failure), ordinary._2 == Left(failure), nulled == ((Left(null), Left(null))))
    },
    test("cancellation joins its owned operation before completing") {
      val cleanup = new Completer[Unit]
      val child   = new Pollable[Int] {
        def poll(wake: Runnable): Async[Int]                         = this
        override private[async] def cancelWithCleanup(): Async[Unit] = cleanup
      }
      val running = child.map(_ + 1).start
      val joined  = Async.cancelWithCleanup(running).start
      val before  = poll(joined).isInstanceOf[Pollable[?]]
      cleanup.succeed(())
      joined.block
      assertTrue(before)
    },
    test("buffered and intersperse preserve every physical domain and scalar/bulk cursor") {
      val lanes: Vector[(JvmType, Vector[Any], Any)] = Vector(
        (JvmType.Byte, Vector(Byte.MinValue, 0.toByte, Byte.MaxValue), Byte.MaxValue),
        (JvmType.Int, Vector(Int.MinValue, 0, Int.MaxValue), Int.MinValue),
        (JvmType.Long, Vector(Long.MinValue, 0L, Long.MaxValue), Long.MaxValue),
        (JvmType.Float, Vector(Float.NaN, Float.NegativeInfinity, -0.0f, 0.0f, Float.PositiveInfinity), Float.NaN),
        (JvmType.Double, Vector(Double.NaN, Double.NegativeInfinity, -0.0d, 0.0d, Double.PositiveInfinity), Double.NaN)
      )
      val checked = for {
        (kind, input, separator) <- lanes
        buffered                 <- Vector(true, false)
      } yield {
        def fresh() = {
          val source = new PhysicalReader[Any](input, kind)
          (source, boundary[Any](source, buffered, separator))
        }
        val expected =
          if (buffered) input
          else input.zipWithIndex.flatMap { case (v, i) => if (i == 0) Vector(v) else Vector(separator, v) }
        val (mixedSource, mixed) = fresh()
        val mixedValues          =
          if (buffered) Vector(readScalar(mixed)) ++ drainBulk(mixed, expected.length + 2)
          else Vector.fill(expected.length)(()).map(_ => readScalar(mixed))
        val mixedEnd           = if (buffered) readBulk(mixed, 1).isEmpty else !mixed.readable().block
        val (bulkSource, bulk) = fresh()
        val bulkValues         =
          if (buffered) drainBulk(bulk, expected.length + 2)
          else Vector.fill(expected.length)(()).map(_ => readScalar(bulk))
        mixedValues.map(bits) == expected.map(bits) && bulkValues.map(bits) == expected.map(bits) && mixedEnd &&
        mixedSource.genericReads == 0 && bulkSource.genericReads == 0
      }
      assertTrue(checked == Vector.fill(10)(true))
    },
    test("buffered and intersperse preserve Boolean Char and Short physical scalar lanes") {
      val lanes: Vector[(JvmType, Vector[Any], Any)] = Vector(
        (JvmType.Boolean, Vector(false, true, false), true),
        (JvmType.Char, Vector(Char.MinValue, 'x', Char.MaxValue), '|'),
        (JvmType.Short, Vector(Short.MinValue, 0.toShort, Short.MaxValue), 1.toShort)
      )
      val checked = for {
        (kind, input, separator) <- lanes
        buffered                 <- Vector(true, false)
      } yield {
        val source   = new PhysicalReader[Any](input, kind)
        val reader   = boundary[Any](source, buffered, separator)
        val expected =
          if (buffered) input
          else
            input.zipWithIndex.flatMap { case (value, index) =>
              if (index == 0) Vector(value) else Vector(separator, value)
            }
        val actual = Vector.fill(expected.length)(readScalar(reader))
        val end    = kind match {
          case JvmType.Boolean => reader.asInstanceOf[Reader.AsyncReader[Boolean]].readBoolean(-1).block == -1
          case JvmType.Char    => reader.asInstanceOf[Reader.AsyncReader[Char]].readChar(-1).block == -1
          case JvmType.Short   =>
            reader.asInstanceOf[Reader.AsyncReader[Short]].readShort(Int.MinValue).block == Int.MinValue
          case _ => false
        }
        actual == expected && end && source.genericReads == 0 && source.specializedScalarReads > 0
      }
      assertTrue(checked.length == 6, checked.forall(identity))
    },
    test("pending readable completion cannot publish true after close") {
      val ready  = new Completer[Boolean]
      var closes = 0
      val source = new Reader.AsyncReader[Int] {
        def close(): Async[Unit]                  = { closes += 1; Async.succeed(()) }
        def isClosed: Async[Boolean]              = Async.succeed(closes != 0)
        def readable(): Async[Boolean]            = ready
        def read[A >: Int](sentinel: A): Async[A] = Async.succeed(sentinel)
      }
      val reader  = Stream.fromReader[Nothing, Int](source).buffer(2).startAsync.block
      val query   = reader.readable().start
      val pending = poll(query).isInstanceOf[Pollable[?]]
      val closing = reader.close()
      val before  = poll(closing).isInstanceOf[Pollable[?]]
      ready.succeed(true)
      assertTrue(pending, before, query.block == false, closing.block == (), closes == 1)
    },
    test("generic buffered and intersperse boundaries preserve cursor and separator collisions") {
      val token   = new Object
      val input   = Vector[Any](token, "x", token)
      val checked = Vector(true, false).map { buffered =>
        val source   = new PhysicalReader[Any](input, JvmType.AnyRef)
        val reader   = boundary[Any](source, buffered, token)
        val expected = if (buffered) input else Vector(token, token, "x", token, token)
        val actual   = Vector.fill(expected.length)(readScalar(reader))
        actual == expected && (reader
          .read(token)
          .block
          .asInstanceOf[AnyRef] eq token) && source.genericReads > 0 && source.specializedBulkReads == 0
      }
      assertTrue(checked.forall(identity))
    },
    test("stateful boundaries memoize source close failure across repeated close") {
      val checked = Vector(true, false).map { buffered =>
        val failure = new RuntimeException(if (buffered) "buffered-close" else "intersperse-close")
        val source  = new PhysicalReader[Int](Vector(1), JvmType.Int, failure)
        val reader  = boundary[Int](source, buffered, 0)
        val first   = reader.close().either.block
        val second  = reader.close().either.block
        first == Left(failure) && second == Left(failure) && source.closes == 1 && reader.readInt(-1L).block == -1L
      }
      assertTrue(checked.forall(identity))
    }
  )
}
