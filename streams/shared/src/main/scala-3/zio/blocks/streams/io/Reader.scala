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

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.JvmType
import zio.blocks.streams.internal.{EndOfStream, Interpreter, StreamError, unsafeEvidence}

import java.io.{IOException, InputStream, Reader => JReader}
import java.lang.{Double => JDouble, Float => JFloat}

/**
 * A pull-based source of elements. Returns elements until closed.
 *
 * ==Thread safety==
 * Reader instances are ''not'' thread-safe. They are designed for
 * single-threaded, pull-based consumption. Do not share a Reader across threads
 * without external synchronization.
 *
 * ==Termination==
 * The `read` method returns elements while available, or a caller-chosen
 * sentinel when closed. For errors, `read` throws — either the raw exception,
 * or a [[StreamError]] wrapping non-Throwable error values. See
 * [[zio.blocks.streams.internal.StreamError]].
 *
 * ==Sentinel-return protocol==
 * Specialized methods (`readInt`, `readLong`, etc.) widen the return type and
 * use a caller-chosen sentinel for end-of-stream. These are the hot-path
 * zero-boxing pull methods.
 *
 * '''Sentinel choice:''' The `Int` lane uses `Long.MinValue` (safe because no
 * `Int` value widens to `Long.MinValue`). The `Long` lane uses `Long.MaxValue`
 * and the `Double` lane uses `Double.MaxValue`; these sentinels coincide with
 * valid data values, so streams containing exactly `Long.MaxValue` or
 * `Double.MaxValue` may be misinterpreted as end-of-stream in specialized
 * paths. The `Float` lane uses `Double.MaxValue` (safe because `Float.MaxValue`
 * widened to `Double` is distinct from `Double.MaxValue`).
 *
 * ==Laws==
 *   - `isClosed` is monotone: once `true` it never returns `false`.
 *   - When `isClosed` and buffer is empty: `read(sentinel)` returns the
 *     sentinel.
 *
 * @tparam Elem
 *   Element type produced by this reader.
 */
abstract class Reader[+Elem] {

  /** Alias for [[concat]]. */
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2] = concat(() => next)

  /**
   * Signals close from the consumer side. Implementations should set internal
   * closed state and wake any blocked readers.
   */
  def close(): Unit

  /**
   * Concatenates this reader with `next`. When this reader is exhausted, it is
   * closed, and elements are pulled from `next` (evaluated lazily).
   *
   * If `this` is already a [[Reader.ConcatReader]], the thunk is appended to
   * its internal array and `this` is returned (mutable append, O(1) amortized).
   * Otherwise a new [[Reader.ConcatReader]] is created with `this` as head and
   * `next` as the first tail entry.
   *
   * This design ensures that left-associative chains like `a ++ b ++ c ++ d`
   * compile into a single flat `ConcatReader` with O(1) per-element read,
   * rather than O(n) nested wrappers.
   */
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2] =
    this match {
      case cr: Reader.ConcatReader[Elem2 @unchecked] => cr.append(next); cr
      case _                                         => new Reader.ConcatReader[Elem2](this.asInstanceOf[Reader[Elem2]], next)
    }

  /**
   * `true` once the reader is closed. Reflects the state machine; does not
   * imply the buffer is empty.
   */
  def isClosed: Boolean

  /**
   * The primitive type of elements in this reader, or `AnyRef` for reference
   * types. Specialized subclasses override this to enable zero-boxing pull
   * paths via `readInt()(using unsafeEvidence)`,
   * `readLong()(using unsafeEvidence)`, etc.
   */
  def jvmType: JvmType = JvmType.AnyRef

  /**
   * Reads the next element, or returns `sentinel` if the stream is closed. The
   * caller passes a sentinel value that can never appear as a real element;
   * getting it back means the stream is exhausted.
   */
  def read[A >: Elem](sentinel: A): A

  /**
   * Returns `true` if the next `read()` / `readInt()(using unsafeEvidence)`
   * etc. would return a value (not closed/sentinel). Default implementation
   * returns `!isClosed`. Subclasses with buffered state should override for
   * accuracy.
   */
  def readable(): Boolean = !isClosed

  /**
   * Drains this reader into a [[Chunk]], consuming all remaining elements.
   * Returns [[Chunk.empty]] when already at EOF. Dispatches on [[jvmType]] for
   * zero-boxing on primitive streams.
   */
  def readAll[A >: Elem](): Chunk[A] = {
    val et = jvmType
    if (et eq JvmType.Int) {
      val b = new ChunkBuilder.Int(); val s = Long.MinValue
      var v = readInt(s)(using unsafeEvidence);
      while (v != s) { b.addOne(v.toInt); v = readInt(s)(using unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Long) {
      val b = new ChunkBuilder.Long(); val s = Long.MaxValue
      var v = readLong(s)(using unsafeEvidence); while (v != s) { b.addOne(v); v = readLong(s)(using unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Float) {
      val b = new ChunkBuilder.Float(); val s = Double.MaxValue
      var v = readFloat(s)(using unsafeEvidence);
      while (v != s) { b.addOne(v.toFloat); v = readFloat(s)(using unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Double) {
      val b = new ChunkBuilder.Double(); val s = Double.MaxValue
      var v = readDouble(s)(using unsafeEvidence);
      while (v != s) { b.addOne(v); v = readDouble(s)(using unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Byte) {
      val b = new ChunkBuilder.Byte(); val s = Long.MinValue
      var v = readInt(s)(using unsafeEvidence);
      while (v != s) { b.addOne(v.toByte); v = readInt(s)(using unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else {
      val b = ChunkBuilder.make[A](16)
      var v = read[Any](EndOfStream);
      while (v.asInstanceOf[AnyRef] ne EndOfStream) { b += v.asInstanceOf[A]; v = read[Any](EndOfStream) }
      b.result()
    }
  }

  /**
   * Sentinel-return Boolean pull. Returns `1` for `true`, `0` for `false`, or
   * `sentinel` when closed and empty. Sentinel must be outside `[0, 1]`. A
   * typical sentinel is `-1`. Requires implicit evidence that `Elem` is a
   * subtype of `Boolean`.
   */
  def readBoolean(sentinel: Int)(using Elem <:< Boolean): Int = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
    else if (v.asInstanceOf[java.lang.Boolean].booleanValue()) 1
    else 0
  }

  /**
   * Reads a single byte, returning -1 when closed. Default delegates to `read`
   * and extracts the low byte. Byte-specialized readers override for
   * efficiency.
   */
  def readByte(): Int = {
    val et = jvmType
    if (et eq JvmType.Byte) {
      val v = readInt(Long.MinValue)(using unsafeEvidence)
      if (v == Long.MinValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Int) {
      val v = readInt(Long.MinValue)(using unsafeEvidence)
      if (v == Long.MinValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Long) {
      val v = readLong(Long.MaxValue)(using unsafeEvidence)
      if (v == Long.MaxValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Float) {
      val v = readFloat(Double.MaxValue)(using unsafeEvidence)
      if (v == Double.MaxValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Double) {
      val v = readDouble(Double.MaxValue)(using unsafeEvidence)
      if (v == Double.MaxValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Short) {
      val v = readShort(Int.MinValue)(using unsafeEvidence)
      if (v == Int.MinValue) -1 else v & 0xff
    } else if (et eq JvmType.Char) {
      val v = readChar(Int.MinValue)(using unsafeEvidence)
      if (v == Int.MinValue) -1 else v & 0xff
    } else if (et eq JvmType.Boolean) {
      val v = readBoolean(-1)(using unsafeEvidence)
      if (v == -1) -1 else v & 0xff
    } else {
      val v = read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else v.asInstanceOf[java.lang.Number].intValue() & 0xff
    }
  }

  /**
   * Box-free bulk byte pull into a caller-supplied buffer.
   *
   * Contract mirrors [[java.io.InputStream#read(byte[],int,int)]]:
   *   - Blocks until at least 1 byte is available.
   *   - Returns the number of bytes read (`1 <= r <= len`).
   *   - Returns `-1` when closed and empty.
   *   - Returns `0` immediately when `len == 0`.
   */
  def readBytes(buf: Array[Byte], offset: Int, len: Int): Int = {
    if (len == 0) return 0
    var i = 0
    while (i < len) {
      val b = readByte()
      if (b < 0) return if (i > 0) i else -1
      buf(offset + i) = b.toByte
      i += 1
    }
    i
  }

  /**
   * Sentinel-return Char pull. Returns element widened to Int, or `sentinel`
   * when closed. Requires implicit evidence that `Elem` is a subtype of `Char`.
   */
  def readChar(sentinel: Int)(using Elem <:< Char): Int = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[java.lang.Character].charValue().toInt
  }

  /**
   * Sentinel-return Double pull. Returns element widened/exact, or `sentinel`
   * when closed. Requires implicit evidence that `Elem` is a subtype of
   * `Double`.
   */
  def readDouble(sentinel: Double)(using Elem <:< Double): Double = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[java.lang.Number].doubleValue()
  }

  /**
   * Sentinel-return Float pull. Returns element widened to Double, or
   * `sentinel` when closed. Requires implicit evidence that `Elem` is a subtype
   * of `Float`.
   */
  def readFloat(sentinel: Double)(using Elem <:< Float): Double = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[java.lang.Number].floatValue().toDouble
  }

  /**
   * Sentinel-return Int pull. Returns the element widened to `Long`, or
   * `sentinel` when closed and empty. The sentinel must lie outside
   * `[Int.MinValue, Int.MaxValue]` (e.g. `Long.MinValue`). Requires implicit
   * evidence that `Elem` is a subtype of `Int`.
   */
  def readInt(sentinel: Long)(using Elem <:< Int): Long = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[java.lang.Number].intValue().toLong
  }

  /**
   * Sentinel-return Long pull. Returns the element, or `sentinel` when closed
   * and empty. The sentinel must be a value that never appears in the stream
   * (e.g. `Long.MaxValue`). Requires implicit evidence that `Elem` is a subtype
   * of `Long`.
   */
  def readLong(sentinel: Long)(using Elem <:< Long): Long = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[java.lang.Number].longValue()
  }

  /**
   * Sentinel-return Short pull. Returns element widened to Int, or `sentinel`
   * when closed. Requires implicit evidence that `Elem` is a subtype of
   * `Short`.
   */
  def readShort(sentinel: Int)(using Elem <:< Short): Int = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[java.lang.Number].shortValue().toInt
  }

  /**
   * Rewinds this reader to its initial state, as if freshly constructed. After
   * `reset()`, all elements are available again from the beginning.
   *
   * Not all readers support this. Readers backed by one-shot resources
   * (InputStreams, java.io.Readers) throw `UnsupportedOperationException`.
   */
  def reset(): Unit = throw new UnsupportedOperationException("This reader does not support reset")

  /**
   * Attempts to set a limit on this reader so it produces at most `n` elements.
   * Returns `true` if the reader handled it natively (O(1), zero per-element
   * cost), `false` if it cannot. When `true`, the reader will produce at most
   * `limit` elements before reporting closed. After `reset()`, the limit is
   * re-applied from the new start position.
   *
   * Default returns `false` — most readers don't support native pushdown.
   */
  def setLimit(n: Long): Boolean = false

  /**
   * Attempts to set this reader into repeat-forever mode, so it restarts from
   * the beginning whenever it would otherwise close. Returns `true` if the
   * reader handled it natively, `false` if the caller must wrap in a repeating
   * wrapper.
   *
   * Default returns `false`.
   */
  def setRepeat(): Boolean = false

  /**
   * Attempts to set a skip (drop) on this reader. Returns `true` if the reader
   * handled it natively (O(1), zero per-element cost), `false` if it cannot.
   * When `true`, the next `skip` elements will be skipped before producing.
   * After `reset()`, the skip is re-applied.
   *
   * Default returns `false` — most readers don't support native pushdown.
   */
  def setSkip(n: Long): Boolean = false

  /**
   * Eagerly discards the first `n` elements. Returns `Unit`. If the stream
   * closes before `n` elements, returns early. Subclasses should override for
   * efficient skipping (e.g. index advancement).
   */
  def skip(n: Long): Unit = {
    val et = jvmType
    if (et eq JvmType.Int) {
      val s = Long.MinValue; var r = n; while (r > 0) { if (readInt(s)(using unsafeEvidence) == s) return; r -= 1 }
    } else if (et eq JvmType.Byte) {
      val s = Long.MinValue; var r = n; while (r > 0) { if (readInt(s)(using unsafeEvidence) == s) return; r -= 1 }
    } else if (et eq JvmType.Long) {
      val s = Long.MaxValue; var r = n; while (r > 0) { if (readLong(s)(using unsafeEvidence) == s) return; r -= 1 }
    } else if (et eq JvmType.Float) {
      val s = Double.MaxValue; var r = n; while (r > 0) { if (readFloat(s)(using unsafeEvidence) == s) return; r -= 1 }
    } else if (et eq JvmType.Double) {
      val s = Double.MaxValue; var r = n; while (r > 0) { if (readDouble(s)(using unsafeEvidence) == s) return; r -= 1 }
    } else {
      var r = n;
      while (r > 0) { val v = read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] eq EndOfStream) return; r -= 1 }
    }
  }

  /**
   * Wraps this reader so that `release` runs after `close()`.
   *
   * @param release
   *   Action to execute when this reader is closed.
   */
  def withRelease(release: () => Unit): Reader[Elem] = {
    val self = this
    new Reader.DelegatingReader[Elem](self) {
      override def close(): Unit = try self.close()
      finally release()
    }
  }
}

/**
 * Companion object for [[Reader]]. Provides factory constructors for common
 * sources (chunks, iterables, ranges, unfold, repeat) and named combinator
 * classes.
 *
 * ==Factory summary==
 *   - [[closed]] — already-closed, no elements.
 *   - [[single]] — exactly one element.
 *   - [[fromChunk]] — backed by a `Chunk`; primitive-specialized variants.
 *   - [[fromIterable]] — backed by any `Iterable`.
 *   - [[fromRange]] — backed by a `Range`.
 *   - [[repeat]] — infinite repetition of a single value.
 *   - [[unfold]] — state-machine generator.
 *   - [[repeated]] — restarts an inner reader on clean close.
 *   - [[fromInputStream]] / [[fromReader]] — I/O adapters.
 */
object Reader {

  /**
   * A reader that is already closed. [[read]] returns `null`; [[readByte]]
   * returns `-1`. The element type is `Nothing` — a closed reader emits no
   * elements and widens by covariance to any `Reader[Elem]`.
   */
  def closed: Reader[Nothing] = ClosedReader

  /**
   * A reader backed by a [[zio.blocks.chunk.Chunk]]. Emits all elements in
   * index order, then closes. No virtual thread or queue needed.
   */
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Reader[A] =
    jt.jvmType match {
      case JvmType.Int    => new FromChunkInt(chunk.asInstanceOf[Chunk[Int]]).asInstanceOf[Reader[A]]
      case JvmType.Long   => new FromChunkLong(chunk.asInstanceOf[Chunk[Long]]).asInstanceOf[Reader[A]]
      case JvmType.Float  => new FromChunkFloat(chunk.asInstanceOf[Chunk[Float]]).asInstanceOf[Reader[A]]
      case JvmType.Double => new FromChunkDouble(chunk.asInstanceOf[Chunk[Double]]).asInstanceOf[Reader[A]]
      case JvmType.Byte   => new FromChunkByte(chunk.asInstanceOf[Chunk[Byte]]).asInstanceOf[Reader[A]]
      case _              => new FromChunk(chunk)
    }

  /**
   * Wraps a [[java.io.InputStream]] as a `Reader[Int]` where each element is a
   * byte widened to Int (0–255). This avoids boxing on `.map`/`.filter` since
   * `Function1` is specialized for `Int` but not `Byte`.
   */
  def fromInputStream(is: InputStream): Reader[Int] =
    new InputStreamReader(is)

  /**
   * A reader backed by an [[Iterable]]. Emits all elements in iteration order,
   * then closes. No virtual thread or queue needed.
   */
  def fromIterable[A](it: Iterable[A]): Reader[A] =
    new FromIterable(it)

  /**
   * A reader backed by a Scala [[Range]]. Emits integers in range order, then
   * closes. No virtual thread or queue needed.
   */
  def fromRange(range: Range): Reader[Int] =
    new FromRange(range)

  /**
   * Wraps a [[java.io.Reader]] as a `Reader[Char]`.
   */
  def fromReader(r: JReader): Reader[Char] =
    new CharReader(r)

  /**
   * Creates an infinite reader that always emits `a`. The reader sets repeat
   * mode on a [[single]] reader so it never closes.
   */
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Reader[A] = {
    val r = single[A](a)
    r.setRepeat()
    r
  }

  /**
   * A reader that restarts `inner` each time it closes cleanly. If it closes
   * with an error (exception), the error is propagated. Used by
   * [[Stream.Repeated]].
   */
  def repeated[A](inner: Reader[A]): Reader[A] =
    new Repeated(inner)

  /**
   * A reader that emits exactly one element then closes. Primitive types
   * (`Int`, `Long`, `Float`, `Double`, etc.) use [[SingletonPrim]] for
   * zero-boxing; reference types use [[SingletonGeneric]].
   */
  def single[A](value: A)(implicit jt: JvmType.Infer[A]): Reader[A] = {
    val pt = jt.jvmType
    if (pt ne JvmType.AnyRef)
      new SingletonPrim[A](primToLong(value, pt), pt.ordinal << 8)
    else
      new SingletonGeneric(value)
  }

  /**
   * Creates a Reader that emits exactly one Boolean, zero-boxing on the read
   * path.
   */
  def singleBoolean(value: Boolean): Reader[Boolean] =
    new SingletonPrim[Boolean](if (value) 1L else 0L, JvmType.Boolean.ordinal << 8)

  /**
   * Creates a Reader that emits exactly one Byte widened to Int, avoiding
   * boxing since `Function1` is specialized for `Int` but not `Byte`.
   */
  def singleByte(value: Byte): Reader[Int] =
    new SingletonPrim[Int]((value & 0xff).toLong, JvmType.Int.ordinal << 8)

  /**
   * Creates a Reader that emits exactly one Char, zero-boxing on the read path.
   */
  def singleChar(value: Char): Reader[Char] =
    new SingletonPrim[Char](value.toLong, JvmType.Char.ordinal << 8)

  /**
   * Creates a Reader that emits exactly one Double, zero-boxing on the read
   * path.
   */
  def singleDouble(value: Double): Reader[Double] =
    new SingletonPrim[Double](JDouble.doubleToRawLongBits(value), JvmType.Double.ordinal << 8)

  /**
   * Creates a Reader that emits exactly one Float, zero-boxing on the read
   * path.
   */
  def singleFloat(value: Float): Reader[Float] =
    new SingletonPrim[Float](JFloat.floatToRawIntBits(value).toLong, JvmType.Float.ordinal << 8)

  /**
   * Creates a Reader that emits exactly one Int, zero-boxing on the read path.
   */
  def singleInt(value: Int): Reader[Int] =
    new SingletonPrim[Int](value.toLong, JvmType.Int.ordinal << 8)

  /**
   * Creates a Reader that emits exactly one Long, zero-boxing on the read path.
   */
  def singleLong(value: Long): Reader[Long] =
    new SingletonPrim[Long](value, JvmType.Long.ordinal << 8)

  /**
   * Creates a Reader that emits exactly one Short, zero-boxing on the read
   * path.
   */
  def singleShort(value: Short): Reader[Short] =
    new SingletonPrim[Short](value.toLong, JvmType.Short.ordinal << 8)

  /**
   * A reader produced by unfolding state `s` with `f`. `f` returns `None` to
   * signal completion, or `Some((elem, nextState))` to emit an element and
   * advance state. Closes when `f` returns `None`. No virtual thread or queue
   * needed — `read()` never blocks.
   */
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Reader[A] =
    new Unfold(s, f)

  /**
   * Wraps a reader with skip and limit tracking. Used as a fallback when the
   * reader cannot handle setSkip/setLimit natively (i.e. they return `false`).
   */
  private[streams] def withSkipLimit[A](inner: Reader[A], skip: Long, limit: Long): Reader[A] =
    new SkipLimitReader[A](inner, skip, limit)

  /**
   * Converts a boxed primitive value to its `Long` bit representation for
   * storage in [[SingletonPrim]]. Int/Long/Byte/Short/Char/Boolean store
   * directly; Float uses `floatToRawIntBits`; Double uses
   * `doubleToRawLongBits`.
   */
  private[streams] def primToLong(value: Any, pt: JvmType): Long = pt match {
    case JvmType.Int     => value.asInstanceOf[Int].toLong
    case JvmType.Long    => value.asInstanceOf[Long]
    case JvmType.Double  => JDouble.doubleToRawLongBits(value.asInstanceOf[Double])
    case JvmType.Float   => JFloat.floatToRawIntBits(value.asInstanceOf[Float]).toLong
    case JvmType.Byte    => value.asInstanceOf[Byte].toLong
    case JvmType.Short   => value.asInstanceOf[Short].toLong
    case JvmType.Char    => value.asInstanceOf[Char].toLong
    case JvmType.Boolean => if (value.asInstanceOf[Boolean]) 1L else 0L
    case _               => 0L
  }

  /**
   * Skips `n` elements by pulling through the given reader's sentinel-return
   * path. Dispatches on `jvmType` for zero-boxing: uses `readInt`, `readLong`,
   * `readFloat`, or `readDouble` when the reader advertises a primitive type;
   * falls back to boxed `read()` otherwise.
   */
  private[streams] def skipViaSentinel(reader: Reader[_], n: Long): Unit = {
    val et = reader.jvmType; var r = n
    if (et eq JvmType.Int) {
      while (r > 0 && reader.readInt(Long.MinValue)(using unsafeEvidence) != Long.MinValue) r -= 1
    } else if (et eq JvmType.Byte) {
      while (r > 0 && reader.readInt(Long.MinValue)(using unsafeEvidence) != Long.MinValue) r -= 1
    } else if (et eq JvmType.Long) {
      while (r > 0 && reader.readLong(Long.MaxValue)(using unsafeEvidence) != Long.MaxValue) r -= 1
    } else if (et eq JvmType.Float) {
      while (r > 0 && reader.readFloat(Double.MaxValue)(using unsafeEvidence) != Double.MaxValue) r -= 1
    } else if (et eq JvmType.Double) {
      while (r > 0 && reader.readDouble(Double.MaxValue)(using unsafeEvidence) != Double.MaxValue) r -= 1
    } else {
      while (r > 0) {
        val v = reader.read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] eq EndOfStream) r = 0 else r -= 1
      }
    }
  }

  /** Reader adapter that reads chars from a `java.io.Reader`. */
  private[streams] final class CharReader(r: JReader) extends Reader[Char] {

    private sealed trait St
    private case object Open                        extends St
    private case object Finished                    extends St
    private final class Errored(val e: IOException) extends St

    private var st: St = Open

    def isClosed: Boolean = st ne Open

    override def readable(): Boolean =
      (st eq Open) && (try { r.ready() }
      catch { case _: IOException => false })

    override def skip(n: Long): Unit = {
      var rem = n;
      while (rem > 0) {
        val v = read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] eq EndOfStream) rem = 0 else rem -= 1
      }
    }

    override def readChar(sentinel: Int)(using Char <:< Char): Int =
      st match {
        case Finished   => sentinel
        case e: Errored => throw new StreamError(e.e)
        case Open       =>
          try {
            val c = r.read()
            if (c < 0) { st = Finished; sentinel }
            else c
          } catch {
            case e: IOException =>
              st = new Errored(e)
              throw new StreamError(e)
          }
      }

    override def readByte(): Int = {
      val c = readChar(-1)(using unsafeEvidence)
      if (c < 0) -1 else (c & 0xff)
    }

    def read[A1 >: Char](sentinel: A1): A1 =
      st match {
        case Finished   => sentinel
        case e: Errored => throw new StreamError(e.e)
        case Open       =>
          try {
            val c = r.read()
            if (c < 0) { st = Finished; sentinel }
            else Char.box(c.toChar).asInstanceOf[A1]
          } catch {
            case e: IOException =>
              st = new Errored(e)
              throw new StreamError(e)
          }
      }

    def close(): Unit = st = Finished
  }

  /**
   * Pre-closed reader singleton. All reads immediately return sentinel/null.
   */
  private object ClosedReader extends Reader[Nothing] {
    def isClosed: Boolean                     = true
    override def readable(): Boolean          = false
    def read[A1 >: Nothing](sentinel: A1): A1 = sentinel
    override def readByte(): Int              = -1
    def close(): Unit                         = ()
    override def skip(n: Long): Unit          = ()
    override def reset(): Unit                = ()
  }

  /**
   * Produced by [[Reader.concat]]. Switches to `next` when `self` closes
   * cleanly (no exception).
   */
  /**
   * A flat, mutable reader that concatenates a head reader with a growable
   * array of lazily-evaluated tail readers. Replaces the old nested
   * `ConcatWith` wrapper to achieve O(1) per-element reads regardless of concat
   * chain depth.
   *
   * ==Lifecycle==
   * The head reader is consumed first. When exhausted, it is closed and the
   * next tail thunk is evaluated to obtain the next reader. This continues
   * until all tail thunks are consumed.
   *
   * ==Mutation==
   * [[append]] adds a new tail thunk in O(1) amortized time (array doubles on
   * overflow). This is called by `Reader.concat` when the receiver is already a
   * `ConcatReader`, enabling `a ++ b ++ c` to build a single flat structure.
   *
   * ==Reset==
   * `reset()` closes any materialized tail readers, resets the head reader, and
   * rewinds `tailIdx` to 0. Tail thunks are re-evaluated on the next cycle
   * (required because consumed readers are closed/exhausted).
   */
  private[streams] final class ConcatReader[Elem](
    head: Reader[Elem],
    firstTail: () => Reader[Elem]
  ) extends Reader[Elem] {
    override def jvmType: JvmType = head.jvmType

    private var current: Reader[Elem] = head
    private var tail: Array[AnyRef]   = { val a = new Array[AnyRef](4); a(0) = firstTail; a }
    private var tailLen: Int          = 1
    private var tailIdx: Int          = 0
    private var done: Boolean         = false

    /** Append a lazy tail thunk. O(1) amortized. */
    private[streams] def append(thunk: () => Reader[Elem @unchecked]): Unit = {
      if (tailLen == tail.length) {
        val next = new Array[AnyRef](tail.length * 2)
        System.arraycopy(tail, 0, next, 0, tailLen)
        tail = next
      }
      tail(tailLen) = thunk
      tailLen += 1
    }

    /** Advance to the next segment. Returns true if a next segment exists. */
    private def advance(): Boolean = {
      try current.close()
      catch { case _: Throwable => () }
      if (tailIdx < tailLen) {
        current = tail(tailIdx).asInstanceOf[() => Reader[Elem]].apply()
        tailIdx += 1
        true
      } else {
        done = true
        false
      }
    }

    def isClosed: Boolean = done

    def read[A1 >: Elem](sentinel: A1): A1 = {
      while (true) {
        val v = current.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] ne EndOfStream) return v.asInstanceOf[A1]
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readInt(sentinel: Long)(using Elem <:< Int): Long = {
      while (true) {
        val v = current.readInt(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readLong(sentinel: Long)(using Elem <:< Long): Long = {
      while (true) {
        val v = current.readLong(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readFloat(sentinel: Double)(using Elem <:< Float): Double = {
      while (true) {
        val v = current.readFloat(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readDouble(sentinel: Double)(using Elem <:< Double): Double = {
      while (true) {
        val v = current.readDouble(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readByte(): Int = {
      while (true) {
        val b = current.readByte()
        if (b >= 0) return b
        if (!advance()) return -1
      }
      -1 // unreachable
    }

    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)

    def close(): Unit =
      if (!done) {
        done = true
        try current.close()
        catch { case _: Throwable => () }
      }

    override def reset(): Unit = {
      // Close current tail reader if it wasn't already closed by advance()
      if ((current ne head) && !current.isClosed) {
        try current.close()
        catch { case _: Throwable => () }
      }
      current = head
      head.reset()
      tailIdx = 0
      done = false
    }
  }

  /**
   * A reader that delegates all methods to an inner reader. Subclass and
   * override only the methods you need to change (typically `close()`).
   */
  private[streams] abstract class DelegatingReader[+Elem](inner: Reader[Elem]) extends Reader[Elem] {
    override def jvmType: JvmType                                          = inner.jvmType
    def isClosed: Boolean                                                  = inner.isClosed
    def read[A1 >: Elem](sentinel: A1): A1                                 = inner.read(sentinel)
    override def readInt(sentinel: Long)(using Elem <:< Int): Long         = inner.readInt(sentinel)(using unsafeEvidence)
    override def readLong(sentinel: Long)(using Elem <:< Long): Long       = inner.readLong(sentinel)(using unsafeEvidence)
    override def readFloat(sentinel: Double)(using Elem <:< Float): Double =
      inner.readFloat(sentinel)(using unsafeEvidence)
    override def readDouble(sentinel: Double)(using Elem <:< Double): Double =
      inner.readDouble(sentinel)(using unsafeEvidence)
    override def readByte(): Int     = inner.readByte()
    override def skip(n: Long): Unit = inner.skip(n)
    def close(): Unit                = inner.close()
    override def reset(): Unit       = inner.reset()
    override def readable(): Boolean = inner.readable()
  }

  /**
   * Wraps a Double-specialized source reader and applies a single predicate.
   */
  private[streams] final class FilteredDouble(
    val source: Reader[?],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                           = source.jvmType
    def isClosed: Boolean                                                   = source.isClosed
    override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
      var v = source.readDouble(sentinel)(using unsafeEvidence)
      while (v != sentinel && !pred.asInstanceOf[Double => Boolean](v))
        v = source.readDouble(sentinel)(using unsafeEvidence)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readDouble(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel else Double.box(v).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && readDouble(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setRepeat(): Boolean = source.setRepeat()
    def toInterpreter: Interpreter    = {
      val p = Interpreter(source)
      p.addFilter[Any](Interpreter.laneOf(JvmType.Double))(pred.asInstanceOf[Any => Boolean])
      p
    }
  }

  /** Wraps a Float-specialized source reader and applies a single predicate. */
  private[streams] final class FilteredFloat(
    val source: Reader[?],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                         = source.jvmType
    def isClosed: Boolean                                                 = source.isClosed
    override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
      var v = source.readFloat(sentinel)(using unsafeEvidence)
      while (v != sentinel && !pred.asInstanceOf[Float => Boolean](v.toFloat))
        v = source.readFloat(sentinel)(using unsafeEvidence)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readFloat(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel else Float.box(v.toFloat).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && readFloat(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setRepeat(): Boolean = source.setRepeat()
    def toInterpreter: Interpreter    = {
      val p = Interpreter(source)
      p.addFilter[Any](Interpreter.laneOf(JvmType.Float))(pred.asInstanceOf[Any => Boolean])
      p
    }
  }

  /**
   * Wraps an Int-specialized source reader and applies a single predicate.
   * Loops on rejection. The filter doesn't change the element type so `jvmType`
   * delegates to the source.
   */
  private[streams] final class FilteredInt(
    val source: Reader[?],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                 = source.jvmType
    def isClosed: Boolean                                         = source.isClosed
    override def readInt(sentinel: Long)(using Any <:< Int): Long = {
      var v = source.readInt(sentinel)(using unsafeEvidence)
      while (v != sentinel && !pred.asInstanceOf[Int => Boolean](v.toInt))
        v = source.readInt(sentinel)(using unsafeEvidence)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readInt(Long.MinValue)(using unsafeEvidence);
      if (v == Long.MinValue) sentinel else Int.box(v.toInt).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MinValue; var r = n; while (r > 0 && readInt(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setRepeat(): Boolean = source.setRepeat()
    def toInterpreter: Interpreter    = {
      val p = Interpreter(source)
      p.addFilter[Any](Interpreter.laneOf(JvmType.Int))(pred.asInstanceOf[Any => Boolean])
      p
    }
  }

  /** Wraps a Long-specialized source reader and applies a single predicate. */
  private[streams] final class FilteredLong(
    val source: Reader[?],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                   = source.jvmType
    def isClosed: Boolean                                           = source.isClosed
    override def readLong(sentinel: Long)(using Any <:< Long): Long = {
      var v = source.readLong(sentinel)(using unsafeEvidence)
      while (v != sentinel && !pred.asInstanceOf[Long => Boolean](v))
        v = source.readLong(sentinel)(using unsafeEvidence)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readLong(Long.MaxValue)(using unsafeEvidence);
      if (v == Long.MaxValue) sentinel else Long.box(v).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MaxValue; var r = n; while (r > 0 && readLong(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setRepeat(): Boolean = source.setRepeat()
    def toInterpreter: Interpreter    = {
      val p = Interpreter(source)
      p.addFilter[Any](Interpreter.laneOf(JvmType.Long))(pred.asInstanceOf[Any => Boolean])
      p
    }
  }

  /**
   * Wraps a reference-type (AnyRef) source reader and applies a single
   * predicate.
   */
  private[streams] final class FilteredRef(
    val source: Reader[?],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType         = source.jvmType
    def isClosed: Boolean                 = source.isClosed
    def read[A1 >: Any](sentinel: A1): A1 = {
      var v = source.read[Any](EndOfStream)
      while ((v.asInstanceOf[AnyRef] ne EndOfStream) && !pred.asInstanceOf[AnyRef => Boolean](v.asInstanceOf[AnyRef]))
        v = source.read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      var r = n;
      while (r > 0) { val v = read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] eq EndOfStream) return; r -= 1 }
    }
    override def setRepeat(): Boolean = source.setRepeat()
    def toInterpreter: Interpreter    = {
      val p = Interpreter(source)
      p.addFilter[Any](Interpreter.laneOf(JvmType.AnyRef))(pred.asInstanceOf[Any => Boolean])
      p
    }
  }

  /**
   * Applies a partial function via `applyOrElse`, skipping non-matching
   * elements without double evaluation.
   */
  private[streams] final class CollectedRef(
    val source: Reader[?],
    val pf: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      if (outType eq JvmType.Boolean) JvmType.AnyRef else outType
    def isClosed: Boolean                 = source.isClosed
    def read[A1 >: Any](sentinel: A1): A1 = {
      val pfTyped = pf.asInstanceOf[PartialFunction[AnyRef, AnyRef]]
      while (true) {
        val v = source.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return sentinel
        val result = pfTyped.applyOrElse(v.asInstanceOf[AnyRef], Reader.CollectedRef.fallback)
        if (result.asInstanceOf[AnyRef] ne Reader.CollectedRef.sentinel) {
          return (if (outType eq JvmType.Boolean) Boolean.box(result.asInstanceOf[Boolean]) else result)
            .asInstanceOf[A1]
        }
      }
      sentinel // unreachable, but needed for the compiler
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      var r = n;
      while (r > 0) { val v = read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] eq EndOfStream) return; r -= 1 }
    }
    override def setRepeat(): Boolean = source.setRepeat()
    def toInterpreter: Interpreter    = {
      val p       = Interpreter(source)
      val pfTyped = pf.asInstanceOf[PartialFunction[Any, Any]]
      p.addFilter[Any](Interpreter.laneOf(JvmType.AnyRef))(pfTyped.isDefinedAt)
      p.addMap[Any, Any](Interpreter.laneOf(JvmType.AnyRef), Interpreter.outLaneOf(outType))(pfTyped)
      p
    }
  }

  private[streams] object CollectedRef {
    val sentinel: AnyRef           = new AnyRef
    val fallback: AnyRef => AnyRef = (_: AnyRef) => sentinel
  }

  /**
   * Abstract base for reader-level flatMap. Manages inner reader lifecycle and
   * delegates reads to the current inner reader.
   */
  private[streams] abstract class FlatMappedBase(outType: JvmType) extends Reader[Any] {
    protected var inner: Reader[Any] = null
    private var _closed              = false

    override def jvmType: JvmType = outType
    def isClosed: Boolean         = _closed

    protected def pullOuter(): Boolean

    private def advance(): Boolean = {
      if (inner != null) {
        try inner.close()
        catch { case _: Throwable => () };
        inner = null
      }
      if (!pullOuter()) { _closed = true; return false }
      true
    }

    override def readInt(sentinel: Long)(using Any <:< Int): Long = {
      while (true) {
        if (inner != null) { val v = inner.readInt(sentinel)(using unsafeEvidence); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readLong(sentinel: Long)(using Any <:< Long): Long = {
      while (true) {
        if (inner != null) { val v = inner.readLong(sentinel)(using unsafeEvidence); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
      while (true) {
        if (inner != null) { val v = inner.readFloat(sentinel)(using unsafeEvidence); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
      while (true) {
        if (inner != null) { val v = inner.readDouble(sentinel)(using unsafeEvidence); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      while (true) {
        if (inner != null) {
          val v = inner.read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] ne EndOfStream) return v.asInstanceOf[A1]
        }
        if (!advance()) return sentinel
      }
      sentinel
    }
    def close(): Unit = {
      _closed = true
      if (inner != null) {
        try inner.close()
        catch { case _: Throwable => () };
        inner = null
      }
      closeSource()
    }
    protected def closeSource(): Unit
  }

  /** FlatMap reader for Double-specialized sources. */
  private[streams] final class FlatMappedDouble(
    val source: Reader[?],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readDouble(Double.MaxValue)(using unsafeEvidence)
      if (v == Double.MaxValue) false
      else { inner = compileInner(f.asInstanceOf[Double => AnyRef](v)); true }
    }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for Float-specialized sources. */
  private[streams] final class FlatMappedFloat(
    val source: Reader[?],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readFloat(Double.MaxValue)(using unsafeEvidence)
      if (v == Double.MaxValue) false
      else { inner = compileInner(f.asInstanceOf[Float => AnyRef](v.toFloat)); true }
    }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for Int-specialized sources. */
  private[streams] final class FlatMappedInt(
    val source: Reader[?],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readInt(Long.MinValue)(using unsafeEvidence)
      if (v == Long.MinValue) false
      else { inner = compileInner(f.asInstanceOf[Int => AnyRef](v.toInt)); true }
    }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for Long-specialized sources. */
  private[streams] final class FlatMappedLong(
    val source: Reader[?],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readLong(Long.MaxValue)(using unsafeEvidence)
      if (v == Long.MaxValue) false
      else { inner = compileInner(f.asInstanceOf[Long => AnyRef](v)); true }
    }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for reference-type (AnyRef) sources. */
  private[streams] final class FlatMappedRef(
    val source: Reader[?],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) false
      else { inner = compileInner(f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef])); true }
    }
    protected def closeSource(): Unit = source.close()
  }

  /** Generic (boxed) chunk-backed reader for reference-type elements. */
  private[streams] final class FromChunk[A](chunk: Chunk[A]) extends Reader[A] {
    private val originalLen: Int           = chunk.length
    private var effectiveLen: Int          = originalLen
    private var limitN: Long               = Long.MaxValue
    private var skipN: Long                = 0
    private var idx: Int                   = 0
    def isClosed: Boolean                  = idx >= effectiveLen
    override def readable(): Boolean       = idx < effectiveLen
    override def setSkip(n: Long): Boolean = {
      skipN = n
      idx = math.max(0, math.min(n, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = n
      effectiveLen = math.min(originalLen, idx + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
    def read[A1 >: A](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk(i).asInstanceOf[A1] }
      else sentinel
    override def readByte(): Int =
      if (idx < effectiveLen) { val i = idx; idx += 1; (chunk(i).asInstanceOf[java.lang.Number].intValue() & 0xff) }
      else -1
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
    }
  }

  /** Specialized FromChunk for Double elements — zero-boxing via readDouble. */
  private[streams] final class FromChunkDouble(chunk: Chunk[Double]) extends Reader[Double] {
    override def jvmType: JvmType          = JvmType.Double
    private val originalLen: Int           = chunk.length
    private var effectiveLen: Int          = originalLen
    private var limitN: Long               = Long.MaxValue
    private var skipN: Long                = 0
    private var idx: Int                   = 0
    def isClosed: Boolean                  = idx >= effectiveLen
    override def readable(): Boolean       = idx < effectiveLen
    override def setSkip(n: Long): Boolean = {
      skipN = n
      idx = math.max(0, math.min(n, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = n
      effectiveLen = math.min(originalLen, idx + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
    def read[A1 >: Double](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Double.box(chunk.double(i)).asInstanceOf[A1] }
      else sentinel
    override def readDouble(sentinel: Double)(using Double <:< Double): Double =
      if (idx < effectiveLen) { val v = chunk.double(idx); idx += 1; v }
      else sentinel
    override def readByte(): Int = {
      val v = readDouble(Double.MaxValue)(using unsafeEvidence)
      if (v == Double.MaxValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
    }
  }

  /** Specialized FromChunk for Float elements — zero-boxing via readFloat. */
  private[streams] final class FromChunkFloat(chunk: Chunk[Float]) extends Reader[Float] {
    override def jvmType: JvmType          = JvmType.Float
    private val originalLen: Int           = chunk.length
    private var effectiveLen: Int          = originalLen
    private var limitN: Long               = Long.MaxValue
    private var skipN: Long                = 0
    private var idx: Int                   = 0
    def isClosed: Boolean                  = idx >= effectiveLen
    override def readable(): Boolean       = idx < effectiveLen
    override def setSkip(n: Long): Boolean = {
      skipN = n
      idx = math.max(0, math.min(n, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = n
      effectiveLen = math.min(originalLen, idx + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
    def read[A1 >: Float](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Float.box(chunk.float(i)).asInstanceOf[A1] }
      else sentinel
    override def readFloat(sentinel: Double)(using Float <:< Float): Double =
      if (idx < effectiveLen) { val v = chunk.float(idx); idx += 1; v.toDouble }
      else sentinel
    override def readByte(): Int = {
      val v = readFloat(Double.MaxValue)(using unsafeEvidence)
      if (v == Double.MaxValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
    }
  }

  /** Specialized FromChunk for Byte elements — zero-boxing via readInt. */
  private[streams] final class FromChunkByte(chunk: Chunk[Byte]) extends Reader[Byte] {
    override def jvmType: JvmType          = JvmType.Byte
    private val originalLen: Int           = chunk.length
    private var effectiveLen: Int          = originalLen
    private var limitN: Long               = Long.MaxValue
    private var skipN: Long                = 0
    private var idx: Int                   = 0
    def isClosed: Boolean                  = idx >= effectiveLen
    override def readable(): Boolean       = idx < effectiveLen
    override def setSkip(n: Long): Boolean = {
      skipN = n
      idx = math.max(0, math.min(n, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = n
      effectiveLen = math.min(originalLen, idx + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
    def read[A1 >: Byte](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Byte.box(chunk.byte(i)).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(using Byte <:< Int): Long =
      if (idx < effectiveLen) { val v = chunk.byte(idx); idx += 1; v.toLong }
      else sentinel
    override def readByte(): Int =
      if (idx < effectiveLen) { val v = chunk.byte(idx); idx += 1; v.toInt & 0xff }
      else -1
    override def readBytes(buf: Array[Byte], offset: Int, len: Int): Int = {
      if (len == 0) return 0
      val avail = effectiveLen - idx
      if (avail <= 0) return -1
      val n = math.min(len, avail)
      var i = 0
      while (i < n) { buf(offset + i) = chunk.byte(idx); idx += 1; i += 1 }
      n
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN.toInt, chunk.length))
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
    }
  }

  /** Specialized FromChunk for Int elements — zero-boxing via readInt. */
  private[streams] final class FromChunkInt(chunk: Chunk[Int]) extends Reader[Int] {
    override def jvmType: JvmType          = JvmType.Int
    private val originalLen: Int           = chunk.length
    private var effectiveLen: Int          = originalLen
    private var limitN: Long               = Long.MaxValue
    private var skipN: Long                = 0
    private var idx: Int                   = 0
    def isClosed: Boolean                  = idx >= effectiveLen
    override def readable(): Boolean       = idx < effectiveLen
    override def setSkip(n: Long): Boolean = {
      skipN = n
      idx = math.max(0, math.min(n, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = n
      effectiveLen = math.min(originalLen, idx + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
    def read[A1 >: Int](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Int.box(chunk.int(i)).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(using Int <:< Int): Long =
      if (idx < effectiveLen) { val v = chunk.int(idx); idx += 1; v.toLong }
      else sentinel
    override def readByte(): Int = {
      val v = readInt(Long.MinValue)(using unsafeEvidence)
      if (v == Long.MinValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
    }
  }

  /** Specialized FromChunk for Long elements — zero-boxing via readLong. */
  private[streams] final class FromChunkLong(chunk: Chunk[Long]) extends Reader[Long] {
    override def jvmType: JvmType          = JvmType.Long
    private val originalLen: Int           = chunk.length
    private var effectiveLen: Int          = originalLen
    private var limitN: Long               = Long.MaxValue
    private var skipN: Long                = 0
    private var idx: Int                   = 0
    def isClosed: Boolean                  = idx >= effectiveLen
    override def readable(): Boolean       = idx < effectiveLen
    override def setSkip(n: Long): Boolean = {
      skipN = n
      idx = math.max(0, math.min(n, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = n
      effectiveLen = math.min(originalLen, idx + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
    def read[A1 >: Long](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Long.box(chunk.long(i)).asInstanceOf[A1] }
      else sentinel
    override def readLong(sentinel: Long)(using Long <:< Long): Long =
      if (idx < effectiveLen) { val v = chunk.long(idx); idx += 1; v }
      else sentinel
    override def readByte(): Int = {
      val v = readLong(Long.MaxValue)(using unsafeEvidence)
      if (v == Long.MaxValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
    }
  }

  /** Reader backed by an `Iterable`; emits elements in iteration order. */
  private[streams] final class FromIterable[A](iterable: Iterable[A]) extends Reader[A] {
    private var iter                    = iterable.iterator
    private var exhausted               = false
    def isClosed: Boolean               = exhausted || !iter.hasNext
    override def readable(): Boolean    = !exhausted && iter.hasNext
    override def skip(n: Long): Unit    = { var r = n; while (r > 0 && iter.hasNext) { iter.next(); r -= 1 } }
    def read[A1 >: A](sentinel: A1): A1 =
      if (!exhausted && iter.hasNext) iter.next().asInstanceOf[A1]
      else { exhausted = true; sentinel }
    override def readByte(): Int =
      if (!exhausted && iter.hasNext) (iter.next().asInstanceOf[java.lang.Number].intValue() & 0xff) else -1
    def close(): Unit          = exhausted = true
    override def reset(): Unit = { iter = iterable.iterator; exhausted = false }
  }

  /** Int-specialized reader backed by a Scala `Range`. */
  private[streams] final class FromRange(range: Range) extends Reader[Int] {
    override def jvmType: JvmType          = JvmType.Int
    private val rangeStep: Int             = range.step
    private val originalLen: Int           = range.length
    private var effectiveLen: Int          = originalLen
    private var limitN: Long               = Long.MaxValue
    private var skipN: Long                = 0
    private var idx: Int                   = 0
    private var current: Int               = range.start
    def isClosed: Boolean                  = idx >= effectiveLen
    override def readable(): Boolean       = idx < effectiveLen
    override def setSkip(n: Long): Boolean = {
      skipN = n
      val s = math.max(0L, math.min(n, originalLen.toLong)).toInt
      idx = s; current = range.start + s * rangeStep
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = n
      effectiveLen = math.min(originalLen, idx + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
      true
    }
    override def skip(n: Long): Unit = {
      val s = math.min(n, (effectiveLen - idx).toLong).toInt; idx += s; current += s * rangeStep
    }
    def read[A1 >: Int](sentinel: A1): A1 =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; Int.box(v).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(using Int <:< Int): Long =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; v.toLong }
      else sentinel
    override def readByte(): Int =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; (v & 0xff) }
      else -1
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      val s = math.max(0L, math.min(skipN, originalLen.toLong)).toInt
      idx = s; current = range.start + s * rangeStep
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen, idx + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt))
    }
  }

  /**
   * Reader adapter that reads bytes from a `java.io.InputStream`, widened to
   * `Int` (0–255) so that downstream `.map`/`.filter` use Int-specialized
   * `Function1` and avoid boxing.
   */
  private[streams] final class InputStreamReader(is: InputStream) extends Reader[Int] {

    private var closed               = false
    private var errored: IOException = scala.compiletime.uninitialized

    def isClosed: Boolean = closed

    override def readable(): Boolean =
      !closed && (try { is.available() > 0 }
      catch { case _: IOException => false })

    override def skip(n: Long): Unit = {
      var r = n; while (r > 0) { val b = readByte(); if (b < 0) r = 0 else r -= 1 }
    }

    override def jvmType: JvmType = JvmType.Int

    override def readInt(sentinel: Long)(using Int <:< Int): Long = {
      if (closed) return sentinel
      if (errored ne null) throw new StreamError(errored)
      try {
        val b = is.read()
        if (b < 0) { closed = true; sentinel }
        else b.toLong
      } catch {
        case e: IOException => closed = true; errored = e; throw new StreamError(e)
      }
    }

    override def readByte(): Int = {
      val v = readInt(-1L)(using unsafeEvidence)
      if (v >= 0) v.toInt else -1
    }

    def read[A1 >: Int](sentinel: A1): A1 = {
      val b = readInt(-1L)(using unsafeEvidence)
      if (b >= 0) Int.box(b.toInt).asInstanceOf[A1] else sentinel
    }

    override def readBytes(buf: Array[Byte], offset: Int, len: Int): Int =
      if (len == 0) 0
      else if (closed) -1
      else if (errored ne null) throw new StreamError(errored)
      else
        try {
          val n = is.read(buf, offset, len)
          if (n < 0) { closed = true; -1 }
          else n
        } catch {
          case e: IOException => closed = true; errored = e; throw new StreamError(e)
        }

    def close(): Unit = closed = true
  }

  /**
   * Wraps a Double-specialized source reader and applies a single erased map
   * function.
   */
  private[streams] final class MappedDouble(
    val source: Reader[?],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      if (outType eq JvmType.Boolean) JvmType.AnyRef else outType
    def isClosed: Boolean                                         = source.isClosed
    override def readInt(sentinel: Long)(using Any <:< Int): Long = {
      val v = source.readDouble(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Double => Int](v).toLong
    }
    override def readLong(sentinel: Long)(using Any <:< Long): Long = {
      val v = source.readDouble(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Double => Long](v)
    }
    override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
      val v = source.readDouble(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Double => Float](v).toDouble
    }
    override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
      val v = source.readDouble(sentinel)(using unsafeEvidence);
      if (v == sentinel) sentinel
      else f.asInstanceOf[Double => Double](v)
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readDouble(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Double => Boolean](v)).asInstanceOf[A1]
      } else f.asInstanceOf[Double => AnyRef](v).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && source.readDouble(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: Interpreter          = {
      val p = Interpreter(source)
      p.addMap[Any, Any](Interpreter.laneOf(JvmType.Double), Interpreter.outLaneOf(outType))(
        f.asInstanceOf[Any => Any]
      )
      p
    }
  }

  /**
   * Wraps a Float-specialized source reader and applies a single erased map
   * function.
   */
  private[streams] final class MappedFloat(
    val source: Reader[?],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      if (outType eq JvmType.Boolean) JvmType.AnyRef else outType
    def isClosed: Boolean                                         = source.isClosed
    override def readInt(sentinel: Long)(using Any <:< Int): Long = {
      val v = source.readFloat(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Float => Int](v.toFloat).toLong
    }
    override def readLong(sentinel: Long)(using Any <:< Long): Long = {
      val v = source.readFloat(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Float => Long](v.toFloat)
    }
    override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
      val v = source.readFloat(sentinel)(using unsafeEvidence);
      if (v == sentinel) sentinel
      else f.asInstanceOf[Float => Float](v.toFloat).toDouble
    }
    override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
      val v = source.readFloat(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Float => Double](v.toFloat)
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readFloat(Double.MaxValue)(using unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Float => Boolean](v.toFloat)).asInstanceOf[A1]
      } else f.asInstanceOf[Float => AnyRef](v.toFloat).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && source.readFloat(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: Interpreter          = {
      val p = Interpreter(source)
      p.addMap[Any, Any](Interpreter.laneOf(JvmType.Float), Interpreter.outLaneOf(outType))(
        f.asInstanceOf[Any => Any]
      )
      p
    }
  }

  /**
   * Wraps an Int-specialized source reader and applies a single erased map
   * function. The sink dispatches on `jvmType` (= `outType`) and calls the
   * matching readXxx. Each readXxx pulls from `source.readInt` and applies `f`.
   */
  private[streams] final class MappedInt(
    val source: Reader[?],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      if (outType eq JvmType.Boolean) JvmType.AnyRef else outType
    def isClosed: Boolean                                         = source.isClosed
    override def readInt(sentinel: Long)(using Any <:< Int): Long = {
      val v = source.readInt(sentinel)(using unsafeEvidence);
      if (v == sentinel) sentinel
      else f.asInstanceOf[Int => Int](v.toInt).toLong
    }
    override def readLong(sentinel: Long)(using Any <:< Long): Long = {
      val v = source.readInt(Long.MinValue)(using unsafeEvidence);
      if (v == Long.MinValue) sentinel
      else f.asInstanceOf[Int => Long](v.toInt)
    }
    override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
      val v = source.readInt(Long.MinValue)(using unsafeEvidence);
      if (v == Long.MinValue) sentinel
      else f.asInstanceOf[Int => Float](v.toInt).toDouble
    }
    override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
      val v = source.readInt(Long.MinValue)(using unsafeEvidence);
      if (v == Long.MinValue) sentinel
      else f.asInstanceOf[Int => Double](v.toInt)
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readInt(Long.MinValue)(using unsafeEvidence);
      if (v == Long.MinValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Int => Boolean](v.toInt)).asInstanceOf[A1]
      } else f.asInstanceOf[Int => AnyRef](v.toInt).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MinValue; var r = n; while (r > 0 && source.readInt(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: Interpreter          = {
      val p = Interpreter(source)
      p.addMap[Any, Any](Interpreter.laneOf(JvmType.Int), Interpreter.outLaneOf(outType))(
        f.asInstanceOf[Any => Any]
      )
      p
    }
  }

  /**
   * Wraps a Long-specialized source reader and applies a single erased map
   * function.
   */
  private[streams] final class MappedLong(
    val source: Reader[?],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      if (outType eq JvmType.Boolean) JvmType.AnyRef else outType
    def isClosed: Boolean                                         = source.isClosed
    override def readInt(sentinel: Long)(using Any <:< Int): Long = {
      val v = source.readLong(Long.MaxValue)(using unsafeEvidence);
      if (v == Long.MaxValue) sentinel
      else f.asInstanceOf[Long => Int](v).toLong
    }
    override def readLong(sentinel: Long)(using Any <:< Long): Long = {
      val v = source.readLong(sentinel)(using unsafeEvidence);
      if (v == sentinel) sentinel
      else f.asInstanceOf[Long => Long](v)
    }
    override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
      val v = source.readLong(Long.MaxValue)(using unsafeEvidence);
      if (v == Long.MaxValue) sentinel
      else f.asInstanceOf[Long => Float](v).toDouble
    }
    override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
      val v = source.readLong(Long.MaxValue)(using unsafeEvidence);
      if (v == Long.MaxValue) sentinel
      else f.asInstanceOf[Long => Double](v)
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readLong(Long.MaxValue)(using unsafeEvidence);
      if (v == Long.MaxValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Long => Boolean](v)).asInstanceOf[A1]
      } else f.asInstanceOf[Long => AnyRef](v).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MaxValue; var r = n; while (r > 0 && source.readLong(s)(using unsafeEvidence) != s) r -= 1
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: Interpreter          = {
      val p = Interpreter(source)
      p.addMap[Any, Any](Interpreter.laneOf(JvmType.Long), Interpreter.outLaneOf(outType))(
        f.asInstanceOf[Any => Any]
      )
      p
    }
  }

  /**
   * Wraps a reference-type (AnyRef) source reader and applies a single erased
   * map function.
   */
  private[streams] final class MappedRef(
    val source: Reader[?],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      if (outType eq JvmType.Boolean) JvmType.AnyRef else outType
    def isClosed: Boolean                                         = source.isClosed
    override def readInt(sentinel: Long)(using Any <:< Int): Long = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else f.asInstanceOf[AnyRef => Int](v.asInstanceOf[AnyRef]).toLong
    }
    override def readLong(sentinel: Long)(using Any <:< Long): Long = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else f.asInstanceOf[AnyRef => Long](v.asInstanceOf[AnyRef])
    }
    override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else f.asInstanceOf[AnyRef => Float](v.asInstanceOf[AnyRef]).toDouble
    }
    override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else f.asInstanceOf[AnyRef => Double](v.asInstanceOf[AnyRef])
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[AnyRef => Boolean](v.asInstanceOf[AnyRef])).asInstanceOf[A1]
      } else f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef]).asInstanceOf[A1]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      var r = n;
      while (r > 0) { val v = source.read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] eq EndOfStream) return; r -= 1 }
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: Interpreter          = {
      val p = Interpreter(source)
      p.addMap[Any, Any](Interpreter.laneOf(JvmType.AnyRef), Interpreter.outLaneOf(outType))(
        f.asInstanceOf[Any => Any]
      )
      p
    }
  }

  /**
   * Repeats an inner reader forever. When the inner closes cleanly (no
   * exception), calls `reset()` and pulls again. On error, re-throws.
   */
  private[streams] final class Repeated[A](inner: Reader[A]) extends Reader[A] {
    override def jvmType: JvmType       = inner.jvmType
    private var done: Boolean           = false
    def isClosed: Boolean               = done
    def read[A1 >: A](sentinel: A1): A1 = {
      while (true) {
        try {
          val v = inner.read[Any](EndOfStream)
          if (v.asInstanceOf[AnyRef] ne EndOfStream) return v.asInstanceOf[A1]
          // Clean close — reset and try again
          inner.reset()
        } catch {
          case e: Throwable => done = true; throw e
        }
      }
      sentinel // unreachable
    }
    override def readInt(sentinel: Long)(using A <:< Int): Long = {
      while (true) {
        val v = inner.readInt(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (inner.isClosed) inner.reset()
        else { done = true; return sentinel }
      }
      sentinel // unreachable
    }
    override def readLong(sentinel: Long)(using A <:< Long): Long = {
      while (true) {
        val v = inner.readLong(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (inner.isClosed) inner.reset()
        else { done = true; return sentinel }
      }
      sentinel // unreachable
    }
    override def readFloat(sentinel: Double)(using A <:< Float): Double = {
      while (true) {
        val v = inner.readFloat(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (inner.isClosed) inner.reset()
        else { done = true; return sentinel }
      }
      sentinel // unreachable
    }
    override def readDouble(sentinel: Double)(using A <:< Double): Double = {
      while (true) {
        val v = inner.readDouble(sentinel)(using unsafeEvidence)
        if (v != sentinel) return v
        if (inner.isClosed) inner.reset()
        else { done = true; return sentinel }
      }
      sentinel // unreachable
    }
    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)
    override def readByte(): Int     = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else (v.asInstanceOf[java.lang.Number].intValue() & 0xff)
    }
    def close(): Unit          = { done = true; inner.close() }
    override def reset(): Unit = { done = false; inner.reset() }
  }

  /**
   * Generic singleton — for reference types. Mode flag: 0 = fresh, 1 = taken, 2 =
   * repeat forever.
   */
  private[streams] final class SingletonGeneric[A](value: A) extends Reader[A] {
    private var mode: Int            = 0
    def isClosed: Boolean            = mode == 1
    override def readable(): Boolean = mode != 1
    override def skip(n: Long): Unit =
      if (mode == 2) () // infinite — skip is a no-op
      else if (n > 0 && mode == 0) mode = 1
    def read[A1 >: A](sentinel: A1): A1 = {
      val m = mode
      if (m == 1) sentinel
      else { if (m == 0) mode = 1; value.asInstanceOf[A1] }
    }
    override def readByte(): Int = {
      val m = mode
      if (m == 1) -1
      else { if (m == 0) mode = 1; (value.asInstanceOf[java.lang.Number].intValue() & 0xff) }
    }
    def close(): Unit          = if (mode != 2) mode = 1
    override def reset(): Unit = if (mode != 2) mode = 0

    override def setRepeat(): Boolean = { mode = 2; true }
  }

  /**
   * Specialized singleton for all primitives. Stores value as Long bits. `A` is
   * phantom — never stored, never accessed.
   *
   * `modeOrd` packs mode in bits [7:0] and JvmType ordinal in bits [15:8].
   * Mode: 0 = fresh, 1 = taken, 2 = repeat forever.
   */
  private[streams] final class SingletonPrim[A] private[streams] (
    private val storedLong: Long,
    private var modeOrd: Int // bits [7:0] = mode (0=fresh, 1=taken, 2=repeat), bits [15:8] = ordinal
  ) extends Reader[A] {
    private def mode: Int             = modeOrd & 0xff
    private def ordinal: Int          = (modeOrd >>> 8) & 0xff
    private def setMode(m: Int): Unit = modeOrd = m | (modeOrd & ~0xff)

    override def jvmType: JvmType    = JvmType.fromOrdinal(ordinal)
    def isClosed: Boolean            = mode == 1
    override def readable(): Boolean = mode != 1

    override def skip(n: Long): Unit =
      if (mode == 2) ()
      else if (n > 0 && mode == 0) setMode(1)

    // Reconstruct boxed value from storedLong + ordinal (cold path)
    private def boxed(): AnyRef = (ordinal: @scala.annotation.switch) match {
      case 0 => Int.box(storedLong.toInt)
      case 1 => Long.box(storedLong)
      case 2 => Double.box(java.lang.Double.longBitsToDouble(storedLong))
      case 3 => Float.box(java.lang.Float.intBitsToFloat(storedLong.toInt))
      case 4 => Byte.box(storedLong.toByte)
      case 5 => Short.box(storedLong.toShort)
      case 6 => Char.box(storedLong.toChar)
      case 7 => Boolean.box(storedLong != 0)
      case _ => null
    }

    def read[A1 >: A](sentinel: A1): A1 = {
      val m = mode
      if (m == 1) sentinel
      else {
        if (m == 0) setMode(1)
        boxed().asInstanceOf[A1]
      }
    }

    override def readInt(sentinel: Long)(using A <:< Int): Long = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); storedLong }
    }

    override def readLong(sentinel: Long)(using A <:< Long): Long = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); storedLong }
    }

    override def readFloat(sentinel: Double)(using A <:< Float): Double = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); java.lang.Float.intBitsToFloat(storedLong.toInt).toDouble }
    }

    override def readDouble(sentinel: Double)(using A <:< Double): Double = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); java.lang.Double.longBitsToDouble(storedLong) }
    }

    override def readByte(): Int = {
      val m = mode;
      if (m == 1) -1
      else { if (m == 0) setMode(1); storedLong.toInt & 0xff }
    }

    def close(): Unit          = if (mode != 2) setMode(1)
    override def reset(): Unit = if (mode != 2) setMode(0)

    override def setRepeat(): Boolean = { setMode(2); true }
  }

  /**
   * Fallback wrapper that tracks skip and limit with per-element counters. Only
   * created when the underlying reader cannot handle pushdown natively.
   */
  private[streams] final class SkipLimitReader[A](
    inner: Reader[A],
    skipN: Long,
    limitN: Long
  ) extends Reader[A] {
    private var remaining: Long = limitN

    // On construction, perform the skip eagerly
    if (skipN > 0) {
      if (!inner.setSkip(skipN)) inner.skip(skipN)
    }

    override def jvmType: JvmType    = inner.jvmType
    def isClosed: Boolean            = remaining <= 0 || inner.isClosed
    override def readable(): Boolean = remaining > 0 && inner.readable()

    def read[A1 >: A](sentinel: A1): A1 = {
      if (remaining <= 0) return sentinel
      val v = inner.read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] ne EndOfStream) { remaining -= 1; v.asInstanceOf[A1] }
      else sentinel
    }

    override def readInt(sentinel: Long)(using A <:< Int): Long = {
      if (remaining <= 0) return sentinel
      val v = inner.readInt(sentinel)(using unsafeEvidence)
      if (v != sentinel) remaining -= 1
      v
    }

    override def readLong(sentinel: Long)(using A <:< Long): Long = {
      if (remaining <= 0) return sentinel
      val v = inner.readLong(sentinel)(using unsafeEvidence)
      if (v != sentinel) remaining -= 1
      v
    }

    override def readFloat(sentinel: Double)(using A <:< Float): Double = {
      if (remaining <= 0) return sentinel
      val v = inner.readFloat(sentinel)(using unsafeEvidence)
      if (v != sentinel) remaining -= 1
      v
    }

    override def readDouble(sentinel: Double)(using A <:< Double): Double = {
      if (remaining <= 0) return sentinel
      val v = inner.readDouble(sentinel)(using unsafeEvidence)
      if (v != sentinel) remaining -= 1
      v
    }

    override def skip(n: Long): Unit = inner.skip(n)
    override def readByte(): Int     = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else (v.asInstanceOf[java.lang.Number].intValue() & 0xff)
    }
    def close(): Unit          = inner.close()
    override def reset(): Unit = {
      inner.reset()
      remaining = limitN
      if (skipN > 0) {
        if (!inner.setSkip(skipN)) inner.skip(skipN)
      }
    }
  }

  /** Produced by Stream.Taken — wraps a reader with a take-N limit. */
  private[streams] final class Taken[Elem](
    self: Reader[Elem],
    n: Long
  ) extends Reader[Elem] {
    override def jvmType: JvmType          = self.jvmType
    private var remaining: Long            = n
    private val initialRemaining: Long     = n
    private var doneSent: Boolean          = false
    def isClosed: Boolean                  = doneSent || self.isClosed
    def read[A1 >: Elem](sentinel: A1): A1 =
      if (doneSent) sentinel
      else if (remaining <= 0) { doneSent = true; sentinel }
      else {
        val v = self.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] ne EndOfStream) { remaining -= 1; v.asInstanceOf[A1] }
        else { doneSent = true; sentinel }
      }
    override def readInt(sentinel: Long)(using Elem <:< Int): Long =
      if (doneSent) sentinel
      else if (remaining <= 0) { doneSent = true; sentinel }
      else {
        val v = self.readInt(sentinel)(using unsafeEvidence);
        if (v != sentinel) { remaining -= 1; v }
        else { doneSent = true; sentinel }
      }
    override def readLong(sentinel: Long)(using Elem <:< Long): Long =
      if (doneSent) sentinel
      else if (remaining <= 0) { doneSent = true; sentinel }
      else {
        val v = self.readLong(sentinel)(using unsafeEvidence);
        if (v != sentinel) { remaining -= 1; v }
        else { doneSent = true; sentinel }
      }
    override def readFloat(sentinel: Double)(using Elem <:< Float): Double =
      if (doneSent) sentinel
      else if (remaining <= 0) { doneSent = true; sentinel }
      else {
        val v = self.readFloat(sentinel)(using unsafeEvidence);
        if (v != sentinel) { remaining -= 1; v }
        else { doneSent = true; sentinel }
      }
    override def readDouble(sentinel: Double)(using Elem <:< Double): Double =
      if (doneSent) sentinel
      else if (remaining <= 0) { doneSent = true; sentinel }
      else {
        val v = self.readDouble(sentinel)(using unsafeEvidence);
        if (v != sentinel) { remaining -= 1; v }
        else { doneSent = true; sentinel }
      }
    override def skip(n: Long): Unit = {
      val toSkip = math.min(n, remaining); self.skip(toSkip); remaining -= toSkip
    }
    override def readByte(): Int = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else (v.asInstanceOf[java.lang.Number].intValue() & 0xff)
    }
    def close(): Unit          = doneSent = true
    override def reset(): Unit = { doneSent = false; remaining = initialRemaining; self.reset() }
  }

  /** Produced by Stream.TakenWhile — wraps a reader with a predicate limit. */
  private[streams] final class TakenWhile[Elem](
    self: Reader[Elem],
    pred: Elem => Boolean
  ) extends Reader[Elem] {
    override def jvmType: JvmType          = self.jvmType
    private var doneSent: Boolean          = false
    def isClosed: Boolean                  = doneSent || self.isClosed
    def read[A1 >: Elem](sentinel: A1): A1 =
      if (doneSent) sentinel
      else {
        val v = self.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) { doneSent = true; sentinel }
        else if (pred(v.asInstanceOf[Elem])) v.asInstanceOf[A1]
        else { doneSent = true; sentinel }
      }
    override def readInt(sentinel: Long)(using Elem <:< Int): Long =
      if (doneSent) sentinel
      else {
        val v = self.readInt(sentinel)(using unsafeEvidence);
        if (v != sentinel) { if (pred.asInstanceOf[Int => Boolean](v.toInt)) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    override def readLong(sentinel: Long)(using Elem <:< Long): Long =
      if (doneSent) sentinel
      else {
        val v = self.readLong(sentinel)(using unsafeEvidence);
        if (v != sentinel) { if (pred.asInstanceOf[Long => Boolean](v)) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    override def readFloat(sentinel: Double)(using Elem <:< Float): Double =
      if (doneSent) sentinel
      else {
        val v = self.readFloat(sentinel)(using unsafeEvidence);
        if (v != sentinel) { if (pred.asInstanceOf[Float => Boolean](v.toFloat)) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    override def readDouble(sentinel: Double)(using Elem <:< Double): Double =
      if (doneSent) sentinel
      else {
        val v = self.readDouble(sentinel)(using unsafeEvidence);
        if (v != sentinel) { if (pred.asInstanceOf[Double => Boolean](v)) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)
    override def readByte(): Int     = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else (v.asInstanceOf[java.lang.Number].intValue() & 0xff)
    }
    def close(): Unit          = { doneSent = true; self.close() }
    override def reset(): Unit = { doneSent = false; self.reset() }
  }

  /** Reader produced by unfolding a state function until `None`. */
  private[streams] final class Unfold[S, A](s: S, f: S => Option[(A, S)]) extends Reader[A] {
    private var state: S             = s
    private var finished             = false
    def isClosed: Boolean            = finished
    override def skip(n: Long): Unit = {
      var r = n;
      while (r > 0) { val v = read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] eq EndOfStream) r = 0 else r -= 1 }
    }
    def read[A1 >: A](sentinel: A1): A1 =
      if (finished) sentinel
      else
        f(state) match {
          case Some((a, next)) => state = next; a.asInstanceOf[A1]
          case None            => finished = true; sentinel
        }
    override def readByte(): Int = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else (v.asInstanceOf[java.lang.Number].intValue() & 0xff)
    }
    def close(): Unit          = finished = true
    override def reset(): Unit = { state = s; finished = false }
  }

  /**
   * Marker trait for thin reader wrappers (MappedXxx, FilteredXxx) that apply a
   * single map function or filter predicate directly. When a second op is
   * added, `toInterpreter` upgrades to a full Interpreter.
   */
  private[streams] sealed trait WrappedReader {
    def source: Reader[?]
    def toInterpreter: Interpreter
  }
}
