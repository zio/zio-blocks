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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.JvmType

import java.io.{IOException, OutputStream, Writer => JWriter}

/**
 * The write-side dual of [[Reader]]: a producer pushes elements and observes
 * when the channel is done. Used internally by channel-based implementations
 * and available directly for push-based integration.
 *
 * ==Thread safety==
 * Writer instances are ''not'' thread-safe unless otherwise documented. They
 * are designed for single-threaded push-based production.
 *
 * ==Termination==
 * A writer is closed by calling [[close]] (clean) or [[fail]] (with error).
 * After [[close]], [[write]] returns `false`. After [[fail]], subclasses that
 * override [[fail]] may cause [[write]] to throw the stored error; the default
 * [[fail]] simply delegates to [[close]]. Both are idempotent; only the first
 * call wins.
 *
 * ==Laws==
 *   - `isClosed` is monotone: once `true` it never returns `false`.
 *   - After `isClosed`: `write` returns `false` (clean close) or throws (error
 *     close, if the subclass implements error storage via [[fail]]).
 *
 * @tparam Elem
 *   Element type (contravariant).
 */
abstract class Writer[-Elem] {

  /** Alias for [[concat]]. */
  def ++[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1] = concat(next)

  /**
   * Closes the writer. After this call, [[write]] returns `false` and
   * [[isClosed]] returns `true`. Idempotent.
   */
  def close(): Unit

  /**
   * Returns a `Writer` that writes to `this` until it closes, then
   * transparently switches to `next`. If `this` closes with an error, the error
   * is propagated immediately without consulting `next`.
   *
   * The dual of [[Reader.concat]].
   */
  def concat[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1] =
    new Writer.ConcatWith(this, next)

  /**
   * Returns a `Writer` that transforms incoming elements with `g` before
   * passing them to this writer. All other operations (`isClosed`, `close`,
   * `fail`) delegate unchanged.
   */
  def contramap[Elem2](g: Elem2 => Elem): Writer[Elem2] =
    new Writer.Contramapped(this, g)

  /**
   * Closes the writer with an error. After this call, [[isClosed]] returns
   * `true`. Subclasses that override this method may cause [[write]] to throw
   * `error` on subsequent calls; the default simply delegates to [[close]].
   * Close and fail are idempotent; only the first call wins.
   */
  def fail(error: Throwable): Unit = { val _ = error; close() }

  /** `true` once the writer is closed. */
  def isClosed: Boolean

  /**
   * The primitive type of elements in this writer, or `AnyRef` for reference
   * types.
   */
  def jvmType: JvmType = JvmType.AnyRef

  /**
   * Writes one element. Returns `true` on success, `false` if the writer is
   * closed. Implementations backed by bounded buffers may block until space is
   * available. Throws if the writer was closed with an error via [[fail]].
   */
  def write(a: Elem): Boolean

  /**
   * Writes every element in chunk. Returns the suffix not delivered. If the
   * writer is already closed, returns the entire chunk. Exceptions from
   * individual writes propagate to the caller.
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

  /**
   * Specialized Boolean write. Default delegates to generic `write`. Requires
   * implicit evidence that `Boolean` is a subtype of `Elem`.
   */
  def writeBoolean(value: Boolean)(using Boolean <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /**
   * Blocking byte write. Equivalent to `write(b.asInstanceOf[Elem])` but avoids
   * boxing when `Elem = Byte`. Returns `false` if the writer is closed.
   * Requires implicit evidence that `Byte` is a subtype of `Elem`.
   */
  def writeByte(b: Byte)(using Byte <:< Elem): Boolean = write(b.asInstanceOf[Elem])

  /**
   * Blocking bulk byte write. Calls [[writeByte]] for each byte in
   * `buf[offset, offset+len)`, stopping early if the channel closes. Returns
   * the number of bytes successfully written, which may be less than `len` if
   * the channel closes mid-way. Requires implicit evidence that `Byte` is a
   * subtype of `Elem`.
   */
  def writeBytes(buf: Array[Byte], offset: Int, len: Int)(using Byte <:< Elem): Int = {
    var written = 0
    while (written < len) {
      if (!writeByte(buf(offset + written))) return written
      written += 1
    }
    written
  }

  /**
   * Specialized Char write. Default delegates to generic `write`. Requires
   * implicit evidence that `Char` is a subtype of `Elem`.
   */
  def writeChar(value: Char)(using Char <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /**
   * Specialized Double write. Default delegates to generic `write`. Requires
   * implicit evidence that `Double` is a subtype of `Elem`.
   */
  def writeDouble(value: Double)(using Double <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /**
   * Specialized Float write. Default delegates to generic `write`. Requires
   * implicit evidence that `Float` is a subtype of `Elem`.
   */
  def writeFloat(value: Float)(using Float <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /**
   * Specialized Int write. Default delegates to generic `write`. Requires
   * implicit evidence that `Int` is a subtype of `Elem`.
   */
  def writeInt(value: Int)(using Int <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /**
   * Specialized Long write. Default delegates to generic `write`. Requires
   * implicit evidence that `Long` is a subtype of `Elem`.
   */
  def writeLong(value: Long)(using Long <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /**
   * Specialized Short write. Default delegates to generic `write`. Requires
   * implicit evidence that `Short` is a subtype of `Elem`.
   */
  def writeShort(value: Short)(using Short <:< Elem): Boolean = write(value.asInstanceOf[Elem])

  /**
   * Returns `true` if the next [[write]] would accept a value without blocking
   * (space is available and the writer is not closed). Default implementation
   * returns `!isClosed`. Subclasses backed by bounded buffers should override
   * for accuracy.
   */
  def writeable(): Boolean = !isClosed
}

/**
 * Companion object for [[Writer]]. Provides factory constructors for common
 * sinks: `closed`, `single`, `limited`, `fromOutputStream`, and `fromWriter`.
 */
object Writer {

  /** A pre-closed writer that rejects all writes. */
  def closed: Writer[Any] = new Writer[Any] {
    def isClosed: Boolean                 = true
    def write(a: Any): Boolean            = false
    def close(): Unit                     = ()
    override def fail(t: Throwable): Unit = ()
  }

  /**
   * Wraps a [[java.io.OutputStream]] as a `Writer[Byte]`. Calling `close()`
   * flushes and closes the underlying stream.
   */
  def fromOutputStream(os: OutputStream): Writer[Byte] =
    new OutputStreamWriter(os)

  /**
   * Wraps a [[java.io.Writer]] as a `Writer[Char]`. Calling `close()` flushes
   * and closes the underlying writer.
   */
  def fromWriter(w: JWriter): Writer[Char] =
    new CharWriter(w)

  /**
   * A writer that accepts at most `n` elements from `inner`, then auto-closes.
   * The dual of `Stream.take`.
   */
  def limited[Elem](inner: Writer[Elem], n: Long): Writer[Elem] =
    new LimitedWriter[Elem](inner, n)

  /**
   * A writer that accepts exactly one element, then auto-closes. The dual of
   * [[Reader.single]].
   */
  def single[Elem]: Writer[Elem] =
    new SingleWriter[Elem]

  /** Writer adapter that writes chars to a `java.io.Writer`. */
  private[streams] final class CharWriter(w: JWriter) extends Writer[Char] {

    private var closed = false

    def isClosed: Boolean = closed

    def write(a: Char): Boolean = writeChar(a)

    override def writeChar(value: Char)(using Char <:< Char): Boolean = {
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
    def isClosed: Boolean             = current.isClosed && switched

    def write(a: Elem): Boolean = {
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

    override def writeByte(b: Byte)(using Byte <:< Byte): Boolean = {
      if (closed) return false
      try { os.write(b & 0xff); true }
      catch { case _: IOException => closed = true; false }
    }

    override def writeBytes(buf: Array[Byte], offset: Int, len: Int)(using Byte <:< Byte): Int = {
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
