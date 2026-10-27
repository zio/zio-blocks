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

package zio.blocks.streams.io

import zio.blocks.async.Async
import zio.blocks.chunk.Chunk
import zio.blocks.streams.JvmType

import java.io.{IOException, OutputStream, Writer => JWriter}

/**
 * A push-based sink for elements, the counterpart of [[Reader]]: the producer
 * calls [[write]] for each element, and `false` tells it that the writer is
 * closed and takes no more. Use it to adapt a destination such as a
 * `java.io.OutputStream` or `java.io.Writer`, or write your own by implementing
 * [[write]], [[isClosed]] and [[close]].
 *
 * ==Closing==
 * [[close]] ends the writer cleanly, and [[fail]] ends it with an error. The
 * default [[fail]] only calls [[close]]; a subclass can override it to keep the
 * error and throw it from later writes, but none of the library's writers does.
 * Implementations should make both idempotent and keep [[isClosed]] `true` once
 * it is.
 *
 * ==Deferred writes==
 * Each operation has a mirror ending in `Async` ([[writeAsync]],
 * [[closeAsync]], ...) that returns an `Async`: building it does nothing, and
 * running it calls the operation once, on the running thread. If the `Async` is
 * cancelled first, the writer is closed. The mirrors do not move a blocking
 * write to another thread or make it non-blocking.
 *
 * ==Thread safety==
 * Writers are not thread-safe: write from one thread at a time.
 *
 * {{{
 * import zio.blocks.chunk.Chunk
 * import zio.blocks.streams.io.Writer
 *
 * val out    = new java.io.StringWriter
 * val writer = Writer.fromWriter(out)
 * writer.write('Z')                // true
 * writer.writeAll(Chunk('I', 'O')) // Chunk()
 * writer.close()
 * writer.write('!')                // false
 * out.toString                     // ZIO
 * }}}
 *
 * @tparam Elem
 *   the type of the elements this writer accepts
 * @see
 *   [[https://zioblocks.com/reference/streams/primitives/writer/ Writer]]
 */
abstract class Writer[-Elem] {

  /**
   * Defers one synchronous writer operation until the returned effect is
   * driven. Cancellation closes this writer and suppresses a stale result. This
   * adaptation does not move blocking writes to another thread or make them
   * nonblocking.
   */
  private def deferred[A](operation: => A): Async[A] =
    Async.deferCancelable(() => operation, () => close())

  /**
   * Returns a writer that writes to this writer until it rejects an element,
   * then to `next`. Alias for `concat`.
   */
  def ++[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1] = concat(next)

  /**
   * Closes this writer cleanly: later writes return `false`, and [[isClosed]]
   * returns `true`. Closing twice has no further effect. The writers from
   * `Writer.fromOutputStream` and `Writer.fromWriter` flush and close the
   * stream they wrap, except after a failed write (a known bug: they then leave
   * it open).
   */
  def close(): Unit

  /** Returns an `Async` that calls [[close]] when it runs. */
  final def closeAsync(): Async[Unit] = deferred(close())

  /**
   * Returns a writer that writes to this writer until a write returns `false`,
   * then switches to `next` and writes the rejected element there. `next` is
   * evaluated once, at the switch, and never if this writer accepts every
   * element.
   *
   * A write that throws propagates the exception, and from then on the writer
   * never switches: later writes go to this writer only, a rejection returns
   * `false`, and `next` is never used. [[close]] closes this writer and, after
   * a switch, `next`, ignoring exceptions from both. [[isClosed]] is `true`
   * after [[close]], once the writer has switched and `next` is closed, or,
   * after a throwing write, once this writer is closed.
   *
   * {{{
   * import zio.blocks.streams.io.Writer
   *
   * val first  = new java.io.StringWriter
   * val second = new java.io.StringWriter
   * val writer = Writer.limited(Writer.fromWriter(first), 3) ++ Writer.fromWriter(second)
   * "Zionomicon".foreach(c => writer.write(c))
   * first.toString  // Zio
   * second.toString // nomicon
   * }}}
   */
  def concat[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1] =
    new Writer.ConcatWith(this, next)

  /**
   * Returns a writer that applies `g` to each element and writes the result to
   * this writer. [[isClosed]], [[close]] and [[fail]] go to this writer
   * unchanged.
   *
   * {{{
   * import zio.blocks.streams.io.Writer
   *
   * val out   = new java.io.StringWriter
   * val codes = Writer.fromWriter(out).contramap[Int](_.toChar)
   * codes.write(90) // true
   * out.toString    // Z
   * }}}
   */
  def contramap[Elem2](g: Elem2 => Elem): Writer[Elem2] =
    new Writer.Contramapped(this, g)

  /**
   * Closes this writer with `error`. The default discards `error` and calls
   * [[close]]; a subclass can override it to keep `error` and throw it from
   * later writes, but none of the library's writers does. `Writer.closed`
   * ignores the call, and `contramap` and `Writer.limited` pass it to the
   * writer they wrap.
   */
  def fail(error: Throwable): Unit = { val _ = error; close() }

  /** Returns an `Async` that calls [[fail]] with `error` when it runs. */
  final def failAsync(error: Throwable): Async[Unit] = deferred(fail(error))

  /** Returns `true` once this writer is closed and accepts no more elements. */
  def isClosed: Boolean

  /** Returns an `Async` that reads [[isClosed]] when it runs. */
  final def isClosedAsync: Async[Boolean] = deferred(isClosed)

  /**
   * The lane this writer takes its elements in: a primitive type, or
   * `JvmType.AnyRef`. The writers from the companion object report
   * `JvmType.AnyRef`; the `NioWriters` writers report their primitive lane.
   */
  def jvmType: JvmType = JvmType.AnyRef

  /** Returns an `Async` that reads [[jvmType]] when it runs. */
  final def jvmTypeAsync: Async[JvmType] = deferred(jvmType)

  /**
   * Writes `a` and returns `true`, or returns `false` when this writer is
   * closed and did not take it. A writer over a bounded destination may block
   * until there is room.
   *
   * The writers from `Writer.fromOutputStream` and `Writer.fromWriter` catch an
   * `IOException` from the stream they wrap, close themselves and return
   * `false`, so the exception is lost. Other exceptions propagate.
   */
  def write(a: Elem): Boolean

  /**
   * Returns an `Async` that calls [[write]] with `a` when it runs and produces
   * its result. Running the same `Async` again does not write again.
   *
   * {{{
   * import zio.blocks.async._
   * import zio.blocks.streams.io.Writer
   *
   * val out   = new java.io.StringWriter
   * val write = Writer.fromWriter(out).writeAsync('Z')
   * out.toString // (empty: nothing is written yet)
   * write.block  // true
   * out.toString // Z
   * }}}
   */
  final def writeAsync(a: Elem): Async[Boolean] = deferred(write(a))

  /**
   * Writes the elements of `chunk` in order, stopping at the first one this
   * writer rejects, and returns the elements it did not write: empty when all
   * were written, the whole chunk when the writer is already closed. An
   * exception from a write propagates. The writer from `Writer.fromWriter`
   * writes the chunk in one call and returns the whole chunk if that call
   * fails.
   */
  def writeAll[Elem1 <: Elem](chunk: Chunk[Elem1]): Chunk[Elem1] = {
    var i = 0
    val n = chunk.length
    while (i < n) {
      if (!write(chunk(i))) return chunk.drop(i)
      i += 1
    }
    Chunk.empty
  }

  /** Returns an `Async` that calls [[writeAll]] with `chunk` when it runs. */
  final def writeAllAsync[Elem1 <: Elem](chunk: Chunk[Elem1]): Async[Chunk[Elem1]] = deferred(writeAll(chunk))

  /**
   * Writes a `Boolean` and returns whether this writer took it. The default
   * calls [[write]]; a writer on the `Boolean` lane can override it to avoid
   * boxing.
   */
  def writeBoolean(value: Boolean)(implicit ev: Boolean <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeBoolean]] when it runs. */
  final def writeBooleanAsync(value: Boolean)(implicit ev: Boolean <:< Elem): Async[Boolean] =
    deferred(writeBoolean(value))

  /**
   * Writes a `Byte` and returns whether this writer took it. The default calls
   * [[write]]; the writer from `Writer.fromOutputStream` overrides it to write
   * without boxing.
   */
  def writeByte(b: Byte)(implicit ev: Byte <:< Elem): Boolean = write(b.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeByte]] when it runs. */
  final def writeByteAsync(b: Byte)(implicit ev: Byte <:< Elem): Async[Boolean] = deferred(writeByte(b))

  /**
   * Writes `len` bytes of `buf`, starting at `offset`, and returns how many
   * this writer took, fewer than `len` when it closes part-way. The default
   * calls [[writeByte]] for each byte and throws `NullPointerException` or
   * `IndexOutOfBoundsException` for an invalid range. The writer from
   * `Writer.fromOutputStream` passes the whole range to one
   * `OutputStream#write` call and returns `len`, or `0` when that call fails
   * with an `IOException`.
   */
  def writeBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Elem): Int = {
    if (buf eq null) throw new NullPointerException("buf")
    if (offset < 0 || len < 0 || offset > buf.length - len)
      throw new IndexOutOfBoundsException(s"offset=$offset length=$len size=${buf.length}")
    var written = 0
    while (written < len) {
      if (!writeByte(buf(offset + written))) return written
      written += 1
    }
    written
  }

  /** Returns an `Async` that calls [[writeBytes]] when it runs. */
  final def writeBytesAsync(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Elem): Async[Int] =
    deferred(writeBytes(buf, offset, len))

  /**
   * Writes a `Char` and returns whether this writer took it. The default calls
   * [[write]]; a writer on the `Char` lane can override it to avoid boxing.
   */
  def writeChar(value: Char)(implicit ev: Char <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeChar]] when it runs. */
  final def writeCharAsync(value: Char)(implicit ev: Char <:< Elem): Async[Boolean] = deferred(writeChar(value))

  /**
   * Writes a `Double` and returns whether this writer took it. The default
   * calls [[write]]; a writer on the `Double` lane can override it to avoid
   * boxing.
   */
  def writeDouble(value: Double)(implicit ev: Double <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeDouble]] when it runs. */
  final def writeDoubleAsync(value: Double)(implicit ev: Double <:< Elem): Async[Boolean] = deferred(writeDouble(value))

  /**
   * Writes a `Float` and returns whether this writer took it. The default calls
   * [[write]]; a writer on the `Float` lane can override it to avoid boxing.
   */
  def writeFloat(value: Float)(implicit ev: Float <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeFloat]] when it runs. */
  final def writeFloatAsync(value: Float)(implicit ev: Float <:< Elem): Async[Boolean] = deferred(writeFloat(value))

  /**
   * Writes an `Int` and returns whether this writer took it. The default calls
   * [[write]]; a writer on the `Int` lane can override it to avoid boxing.
   */
  def writeInt(value: Int)(implicit ev: Int <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeInt]] when it runs. */
  final def writeIntAsync(value: Int)(implicit ev: Int <:< Elem): Async[Boolean] = deferred(writeInt(value))

  /**
   * Writes a `Long` and returns whether this writer took it. The default calls
   * [[write]]; a writer on the `Long` lane can override it to avoid boxing.
   */
  def writeLong(value: Long)(implicit ev: Long <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeLong]] when it runs. */
  final def writeLongAsync(value: Long)(implicit ev: Long <:< Elem): Async[Boolean] = deferred(writeLong(value))

  /**
   * Writes a `Short` and returns whether this writer took it. The default calls
   * [[write]]; a writer on the `Short` lane can override it to avoid boxing.
   */
  def writeShort(value: Short)(implicit ev: Short <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /** Returns an `Async` that calls [[writeShort]] when it runs. */
  final def writeShortAsync(value: Short)(implicit ev: Short <:< Elem): Async[Boolean] = deferred(writeShort(value))

  /**
   * Returns `true` when the next [[write]] is expected to take an element
   * without blocking. The default is `!isClosed`, and none of the library's
   * writers overrides it, so `true` does not promise that a write will not
   * block.
   */
  def writeable(): Boolean = !isClosed

  /** Returns an `Async` that calls [[writeable]] when it runs. */
  final def writeableAsync(): Async[Boolean] = deferred(writeable())
}

/**
 * Constructors for [[Writer]]: [[closed]], [[single]], [[limited]], and the
 * adapters [[fromOutputStream]] and [[fromWriter]].
 */
object Writer {

  /**
   * Returns a writer that is already closed: every write returns `false`, and
   * `close()` and `fail` do nothing.
   */
  def closed: Writer[Any] = new Writer[Any] {
    def isClosed: Boolean                 = true
    def write(a: Any): Boolean            = false
    def close(): Unit                     = ()
    override def fail(t: Throwable): Unit = ()
  }

  /**
   * Returns a writer that writes bytes to `os`. [[Writer#close]] flushes and
   * closes `os`.
   *
   * An `IOException` from `os` is not thrown: the write returns `false` (or
   * `writeBytes` returns `0`) and the writer closes itself. A known bug: after
   * that, `close()` neither flushes nor closes `os`. An `IOException` from
   * `close()` is ignored.
   *
   * {{{
   * import zio.blocks.streams.io.Writer
   *
   * val out    = new java.io.ByteArrayOutputStream()
   * val writer = Writer.fromOutputStream(out)
   * writer.writeBytes("Zionomicon".getBytes("UTF-8"), 0, 3) // 3
   * writer.close()
   * out.toString("UTF-8")                                  // Zio
   * }}}
   */
  def fromOutputStream(os: OutputStream): Writer[Byte] =
    new OutputStreamWriter(os)

  /**
   * Returns a writer that writes characters to `w`. [[Writer#close]] flushes
   * and closes `w`.
   *
   * An `IOException` from `w` is not thrown: the write returns `false` (or
   * `writeAll` returns the whole chunk) and the writer closes itself. A known
   * bug: after that, `close()` neither flushes nor closes `w`. An `IOException`
   * from `close()` is ignored.
   */
  def fromWriter(w: JWriter): Writer[Char] =
    new CharWriter(w)

  /**
   * Returns a writer that passes at most `n` elements to `inner`. After `n`
   * accepted writes it reports closed and rejects every write, but it does not
   * close `inner`; its own [[Writer#close]] does. It also reports closed when
   * `inner` is closed, and when `n <= 0`.
   */
  def limited[Elem](inner: Writer[Elem], n: Long): Writer[Elem] =
    new LimitedWriter[Elem](inner, n)

  /**
   * Returns a writer that takes one element, then reports closed and rejects
   * every later write. It does not store or forward the element: the first
   * write only returns `true`.
   */
  def single[Elem]: Writer[Elem] =
    new SingleWriter[Elem]

  /** Writer adapter that writes chars to a `java.io.Writer`. */
  private[streams] final class CharWriter(w: JWriter) extends Writer[Char] {

    private var closed = false

    def isClosed: Boolean = closed

    def write(a: Char): Boolean = writeChar(a)

    override def writeChar(value: Char)(implicit ev: Char <:< Char): Boolean = {
      if (closed) return false
      try { w.write(value.toInt); true }
      catch { case _: IOException => closed = true; false }
    }

    override def writeAll[Elem1 <: Char](chunk: Chunk[Elem1]): Chunk[Elem1] = {
      if (closed) return chunk
      val arr = new Array[Char](chunk.length)
      var i   = 0
      while (i < arr.length) { arr(i) = chunk(i); i += 1 }
      try { w.write(arr, 0, arr.length); Chunk.empty }
      catch { case _: IOException => closed = true; chunk }
    }

    def close(): Unit =
      if (!closed) {
        closed = true
        try { w.flush(); w.close() }
        catch { case _: IOException => () }
      }
  }

  /**
   * Produced by [[Writer.concat]]. Writes to `self` until it closes, then
   * switches to `next`. Errors on `self` are propagated immediately.
   */
  private[streams] final class ConcatWith[Elem](
    self: Writer[Elem],
    next: => Writer[Elem]
  ) extends Writer[Elem] {
    private var current: Writer[Elem] = self
    private var switched: Boolean     = false
    private var closed: Boolean       = false
    def isClosed: Boolean             = closed || (current.isClosed && switched)

    def write(a: Elem): Boolean = {
      if (closed) return false
      if (switched) return current.write(a)
      val ok =
        try self.write(a)
        catch { case t: Throwable => switched = true; throw t }
      if (!ok && !switched) {
        switched = true
        current = next
        current.write(a)
      } else ok
    }

    def close(): Unit = {
      closed = true
      try self.close()
      catch { case _: Throwable => () }
      if (switched) {
        try current.close()
        catch { case _: Throwable => () }
      }
    }
  }

  /** Produced by [[Writer.contramap]]. */
  private[streams] final class Contramapped[Elem, Elem2](
    self: Writer[Elem],
    g: Elem2 => Elem
  ) extends Writer[Elem2] {
    def isClosed: Boolean                     = self.isClosed
    def write(a: Elem2): Boolean              = self.write(g(a))
    def close(): Unit                         = self.close()
    override def fail(error: Throwable): Unit = self.fail(error)
  }

  /** A writer that accepts at most `n` elements, then auto-closes. */
  private[streams] final class LimitedWriter[Elem](
    inner: Writer[Elem],
    n: Long
  ) extends Writer[Elem] {
    private var remaining: Long = n
    def isClosed: Boolean       = remaining <= 0 || inner.isClosed
    def write(a: Elem): Boolean = {
      if (remaining <= 0) return false
      val ok = inner.write(a)
      if (ok) remaining -= 1
      ok
    }
    def close(): Unit                         = inner.close()
    override def fail(error: Throwable): Unit = inner.fail(error)
  }

  /** Writer adapter that writes bytes to a `java.io.OutputStream`. */
  private[streams] final class OutputStreamWriter(os: OutputStream) extends Writer[Byte] {

    private var closed = false

    def isClosed: Boolean = closed

    def write(a: Byte): Boolean = writeByte(a)

    override def writeByte(b: Byte)(implicit ev: Byte <:< Byte): Boolean = {
      if (closed) return false
      try { os.write(b & 0xff); true }
      catch { case _: IOException => closed = true; false }
    }

    override def writeBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Byte): Int = {
      if (closed) return 0
      if (len == 0) return 0
      try { os.write(buf, offset, len); len }
      catch { case _: IOException => closed = true; 0 }
    }

    def close(): Unit =
      if (!closed) {
        closed = true
        try { os.flush(); os.close() }
        catch { case _: IOException => () }
      }
  }

  /**
   * A writer that accepts exactly one element, then auto-closes. The dual of
   * [[Reader.single]].
   */
  private[streams] final class SingleWriter[Elem] extends Writer[Elem] {
    @volatile private var taken: Boolean = false
    def isClosed: Boolean                = taken
    def write(a: Elem): Boolean          = {
      if (taken) return false
      taken = true
      true
    }
    def close(): Unit = taken = true
  }
}
