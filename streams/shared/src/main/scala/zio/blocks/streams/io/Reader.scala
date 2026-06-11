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
import zio.blocks.combinators.Concat
import zio.blocks.streams.JvmType
import zio.blocks.streams.internal.{doubleEOF, longEOF, runBoth, EndOfStream, Interpreter, StreamError, unsafeEvidence}

import scala.annotation.unchecked.uncheckedVariance

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

  // ---- End-of-stream disambiguation (see class scaladoc, "Sentinel choice") --
  // The `Long`/`Double` specialized lanes have no spare bit pattern to reserve
  // as an end-of-stream sentinel, so a returned value that equals the sentinel
  // is ambiguous: it may be real data or EOF. This out-of-band flag records
  // which it was for the *immediately preceding* specialized read, letting
  // consumers disambiguate losslessly (via `internal.longEOF` / `internal.
  // doubleEOF`) without boxing. The reader contract is single-threaded, so a
  // plain field is sufficient. It is NOT monotonic: a subsequent data read
  // clears it, and `reset()` must clear it too (the base read methods and
  // overrides below maintain it).
  private[this] var _lastReadWasEOF: Boolean = false

  /**
   * Whether the immediately preceding specialized read
   * (`readLong`/`readDouble`, and by extension `readInt`/`readFloat`) returned
   * its end-of-stream sentinel because the stream was exhausted — as opposed to
   * returning a real element that merely equals the sentinel value.
   * Pass-through/transforming wrappers override this to delegate to the reader
   * they last pulled from.
   */
  def lastReadWasEOF: Boolean = _lastReadWasEOF

  /**
   * Records that the immediately preceding specialized read hit genuine EOF.
   */
  protected final def markReadEOF(): Unit = _lastReadWasEOF = true

  /**
   * Records that the immediately preceding specialized read returned a value.
   */
  protected final def markReadValue(): Unit = _lastReadWasEOF = false

  /** Alias for [[concat]]. */
  def ++[Elem2, Elem3](next: => Reader[Elem2])(implicit
    valueConcat: Concat.WithOut[Elem @uncheckedVariance, Elem2, Elem3],
    jtElem3: JvmType.Infer[Elem3]
  ): Reader[Elem3] = concat(() => next)

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
   *
   * The output element type `Elem3` follows the [[Concat]] derivation rules: it
   * is `Elem | Elem2` on Scala 3, the least upper bound on Scala 2 when the
   * types are meaningfully related, or `Either[Elem, Elem2]` on Scala 2 when
   * they are disjoint. In the disjoint (non-identity) case each side's elements
   * are projected through [[Concat.left]] / [[Concat.right]] before concat.
   */
  def concat[Elem2, Elem3](next: () => Reader[Elem2])(implicit
    valueConcat: Concat.WithOut[Elem @uncheckedVariance, Elem2, Elem3],
    jtElem3: JvmType.Infer[Elem3]
  ): Reader[Elem3] = {
    val outJvmType = jtElem3.jvmType
    if (valueConcat.isIdentityLike)
      this
        .asInstanceOf[Reader[Elem3]]
        .concatRaw(() => next().asInstanceOf[Reader[Elem3]], outJvmType)
    else
      new Reader.ProjectedReader(this, (valueConcat.left(_)).asInstanceOf[Any => Any], outJvmType)
        .asInstanceOf[Reader[Elem3]]
        .concatRaw(
          () =>
            new Reader.ProjectedReader(next(), (valueConcat.right(_)).asInstanceOf[Any => Any], outJvmType)
              .asInstanceOf[Reader[Elem3]],
          outJvmType
        )
  }

  /**
   * Internal concatenation that bypasses [[Concat]] derivation, used by already
   * type-aligned callers (e.g. `Stream.concat`, which projects element and
   * error channels before reaching the reader level). `outJvmType` is the JVM
   * lane of the output element type and is propagated to the resulting
   * [[Reader.ConcatReader]] so downstream specialization stays correct even
   * when the static element type widens (e.g. `Int | String` is `AnyRef`).
   *
   * The mutable-append fast path is only taken when the receiver is already a
   * `ConcatReader` with a matching output lane; otherwise a fresh
   * `ConcatReader` is created so a changed lane is never silently inherited.
   */
  private[streams] def concatRaw(next: () => Reader[Elem @uncheckedVariance], outJvmType: JvmType): Reader[Elem] =
    this match {
      case cr: Reader.ConcatReader[Elem @unchecked] if cr.jvmType eq outJvmType => cr.append(next); cr
      case _                                                                    => new Reader.ConcatReader[Elem](this, next, outJvmType)
    }

  /**
   * Like [[concatRaw]], but takes the tail as a raw [[ConcatReader]] entry — a
   * not-yet-compiled `Stream[_, Elem]` — instead of a thunk. The stream is
   * compiled lazily at segment-advance time, so laziness is identical to the
   * thunk form while skipping one closure allocation per segment (deep concat
   * spines — nested_concat).
   */
  private[streams] def concatRawEntry(next: AnyRef, outJvmType: JvmType): Reader[Elem] =
    this match {
      case cr: Reader.ConcatReader[Elem @unchecked] if cr.jvmType eq outJvmType => cr.append(next); cr
      case _                                                                    => new Reader.ConcatReader[Elem](this, next, outJvmType)
    }

  /**
   * `true` once the reader is closed. Reflects the state machine; does not
   * imply the buffer is empty.
   */
  def isClosed: Boolean

  /**
   * The primitive type of elements in this reader, or `AnyRef` for reference
   * types. Specialized subclasses override this to enable zero-boxing pull
   * paths via `readInt()(unsafeEvidence)`, `readLong()(unsafeEvidence)`, etc.
   */
  def jvmType: JvmType = JvmType.AnyRef

  /**
   * Reads the next element, or returns `sentinel` if the stream is closed. The
   * caller passes a sentinel value that can never appear as a real element;
   * getting it back means the stream is exhausted.
   */
  def read[A >: Elem](sentinel: A): A

  /**
   * Returns `true` if the next `read()` / `readInt()(unsafeEvidence)` etc.
   * would return a value (not closed/sentinel). Default implementation returns
   * `!isClosed`. Subclasses with buffered state should override for accuracy.
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
      var v = readInt(s)(unsafeEvidence);
      while (v != s) { b.addOne(v.toInt); v = readInt(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Long) {
      val b = new ChunkBuilder.Long(); val s = Long.MaxValue
      var v = readLong(s)(unsafeEvidence); while (!longEOF(this, v, s)) { b.addOne(v); v = readLong(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Float) {
      val b = new ChunkBuilder.Float(); val s = Double.MaxValue
      var v = readFloat(s)(unsafeEvidence);
      while (v != s) { b.addOne(v.toFloat); v = readFloat(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Double) {
      val b = new ChunkBuilder.Double(); val s = Double.MaxValue
      var v = readDouble(s)(unsafeEvidence);
      while (!doubleEOF(this, v, s)) { b.addOne(v); v = readDouble(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Byte) {
      val b = new ChunkBuilder.Byte(); val s = Long.MinValue
      var v = readInt(s)(unsafeEvidence);
      while (v != s) { b.addOne(v.toByte); v = readInt(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Byte) {
      val b = new ChunkBuilder.Byte()
      var v = readByte()
      while (v >= 0) { b.addOne(v.toByte); v = readByte() }
      b.result().asInstanceOf[Chunk[A]]
    } else {
      val b = ChunkBuilder.make[A](16)
      var v = read[Any](EndOfStream);
      while (v.asInstanceOf[AnyRef] ne EndOfStream) { b += v.asInstanceOf[A]; v = read[Any](EndOfStream) }
      b.result()
    }
  }

  /**
   * Reads at most `n` elements into a [[Chunk]], returning early if the reader
   * is exhausted. Returns [[Chunk.empty]] when `n <= 0` or the reader is
   * already closed. Dispatches on [[jvmType]] for zero-boxing on primitive
   * streams.
   */
  def readN[A >: Elem](n: Int): Chunk[A] = {
    if (n <= 0) return Chunk.empty
    val et = jvmType
    if (et eq JvmType.Int) {
      val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 4096))
      val s = Long.MinValue; var i = 0
      var v = readInt(s)(unsafeEvidence)
      while (v != s && i < n) { b.addOne(v.toInt); i += 1; if (i < n) v = readInt(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Long) {
      val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 4096))
      val s = Long.MaxValue; var i = 0
      var v = readLong(s)(unsafeEvidence)
      while (!longEOF(this, v, s) && i < n) { b.addOne(v); i += 1; if (i < n) v = readLong(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Float) {
      val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 4096))
      val s = Double.MaxValue; var i = 0
      var v = readFloat(s)(unsafeEvidence)
      while (v != s && i < n) { b.addOne(v.toFloat); i += 1; if (i < n) v = readFloat(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Double) {
      val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 4096))
      val s = Double.MaxValue; var i = 0
      var v = readDouble(s)(unsafeEvidence)
      while (!doubleEOF(this, v, s) && i < n) { b.addOne(v); i += 1; if (i < n) v = readDouble(s)(unsafeEvidence) }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Byte) {
      val b = new ChunkBuilder.Byte(); b.sizeHint(math.min(n, 4096)); var i = 0
      var v = readByte()
      while (v >= 0 && i < n) { b.addOne(v.toByte); i += 1; if (i < n) v = readByte() }
      b.result().asInstanceOf[Chunk[A]]
    } else {
      val b = ChunkBuilder.make[A](math.min(n, 16)); var i = 0
      var v = read[Any](EndOfStream)
      while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
        b += v.asInstanceOf[A]; i += 1; if (i < n) v = read[Any](EndOfStream)
      }
      b.result()
    }
  }

  /**
   * Reads up to `n` elements that are currently available. Blocks for at least
   * 1 element if none are ready yet. Returns fewer than `n` if fewer are
   * available without additional blocking. Returns [[Chunk.empty]] if `n <= 0`
   * or if the reader is closed and empty.
   *
   * Differs from [[readN]] in that [[readN]] blocks until exactly `n` elements
   * have been read. [[readUpToN]] returns as soon as at least one element is
   * read, without waiting for further elements.
   *
   * @param n
   *   maximum number of elements to read
   * @return
   *   a [[Chunk]] with between 1 and `n` elements, or empty on EOS
   */
  def readUpToN[A >: Elem](n: Int): Chunk[A] = {
    if (n <= 0) return Chunk.empty
    val first = read[Any](EndOfStream)
    if (first.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
    if (n == 1) return Chunk.single(first.asInstanceOf[A])
    val b = ChunkBuilder.make[A](math.min(n, 64))
    b += first.asInstanceOf[A]
    var i = 1
    while (i < n && readable()) {
      val v = read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) {
        return b.result()
      }
      b += v.asInstanceOf[A]
      i += 1
    }
    b.result()
  }

  /**
   * Sentinel-return Boolean pull. Returns `1` for `true`, `0` for `false`, or
   * `sentinel` when closed and empty. Sentinel must be outside `[0, 1]`. A
   * typical sentinel is `-1`. Requires implicit evidence that `Elem` is a
   * subtype of `Boolean`.
   */
  def readBoolean(sentinel: Int)(implicit ev: Elem <:< Boolean): Int = {
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
      val v = readInt(Long.MinValue)(unsafeEvidence)
      if (v == Long.MinValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Int) {
      val v = readInt(Long.MinValue)(unsafeEvidence)
      if (v == Long.MinValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Long) {
      val v = readLong(Long.MaxValue)(unsafeEvidence)
      if (longEOF(this, v, Long.MaxValue)) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Float) {
      val v = readFloat(Double.MaxValue)(unsafeEvidence)
      if (v == Double.MaxValue) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Double) {
      val v = readDouble(Double.MaxValue)(unsafeEvidence)
      if (doubleEOF(this, v, Double.MaxValue)) -1 else v.toInt & 0xff
    } else if (et eq JvmType.Short) {
      val v = readShort(Int.MinValue)(unsafeEvidence)
      if (v == Int.MinValue) -1 else v & 0xff
    } else if (et eq JvmType.Char) {
      val v = readChar(Int.MinValue)(unsafeEvidence)
      if (v == Int.MinValue) -1 else v & 0xff
    } else if (et eq JvmType.Boolean) {
      val v = readBoolean(-1)(unsafeEvidence)
      if (v == -1) -1 else v & 0xff
    } else {
      val v = read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else Reader.anyToLowByte(v)
    }
  }

  /**
   * Box-free bulk byte pull into a caller-supplied buffer. Requires
   * compile-time evidence that this reader's element type is `Byte`.
   *
   * Contract mirrors `java.io.InputStream.read(byte[], int, int)`:
   *   - Blocks until at least 1 byte is available.
   *   - Returns the number of bytes read (`1 <= r <= len`).
   *   - Returns `-1` when closed and empty.
   *   - Returns `0` immediately when `len == 0`.
   *
   * @param buf
   *   the destination byte array
   * @param offset
   *   the start position in `buf` to write into
   * @param len
   *   the maximum number of bytes to read
   * @param ev
   *   compile-time evidence that `Elem` is a subtype of `Byte`; this method is
   *   only available on `Reader[Byte]` instances
   * @return
   *   the number of bytes read, or `-1` on end-of-stream
   *
   * @example
   *   {{{
   * val reader: Reader[Byte] = Reader.fromInputStream(inputStream)
   * val buf = new Array[Byte](1024)
   * val n = reader.readBytes(buf, 0, buf.length)
   * if (n > 0) process(buf, 0, n)
   *   }}}
   */
  def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Elem <:< Byte): Int = {
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
   * Box-free bulk Int pull into a caller-supplied buffer.
   *
   * Contract mirrors [[readBytes]]:
   *   - Blocks until at least 1 element is available.
   *   - Returns the number of elements read (`1 <= r <= maxLen`).
   *   - Returns `-1` when closed and empty.
   *   - Returns `0` immediately when `maxLen == 0`.
   *
   * @param buf
   *   destination array
   * @param offset
   *   starting position in `buf`
   * @param maxLen
   *   maximum number of elements to read
   * @return
   *   number of elements read, `-1` on end-of-stream, or `0` when `maxLen == 0`
   */
  def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: Elem <:< Int): Int = {
    if (maxLen == 0) return 0
    val sentinel = Long.MinValue
    var i        = 0
    while (i < maxLen) {
      val v = readInt(sentinel)(unsafeEvidence)
      if (v == sentinel) return if (i > 0) i else -1
      buf(offset + i) = v.toInt
      i += 1
      if (i < maxLen && !readable()) return i
    }
    i
  }

  /**
   * Box-free bulk Long pull into a caller-supplied buffer.
   *
   * Contract mirrors [[readBytes]]:
   *   - Blocks until at least 1 element is available.
   *   - Returns the number of elements read (`1 <= r <= maxLen`).
   *   - Returns `-1` when closed and empty.
   *   - Returns `0` immediately when `maxLen == 0`.
   *
   * @param buf
   *   destination array
   * @param offset
   *   starting position in `buf`
   * @param maxLen
   *   maximum number of elements to read
   * @return
   *   number of elements read, `-1` on end-of-stream, or `0` when `maxLen == 0`
   */
  def readLongs(buf: Array[Long], offset: Int, maxLen: Int)(implicit ev: Elem <:< Long): Int = {
    if (maxLen == 0) return 0
    val sentinel = Long.MaxValue
    var i        = 0
    while (i < maxLen) {
      val v = readLong(sentinel)(unsafeEvidence)
      if (longEOF(this, v, sentinel)) return if (i > 0) i else -1
      buf(offset + i) = v
      i += 1
      if (i < maxLen && !readable()) return i
    }
    i
  }

  /**
   * Box-free bulk Double pull into a caller-supplied buffer.
   *
   * Contract mirrors [[readBytes]]:
   *   - Blocks until at least 1 element is available.
   *   - Returns the number of elements read (`1 <= r <= maxLen`).
   *   - Returns `-1` when closed and empty.
   *   - Returns `0` immediately when `maxLen == 0`.
   *
   * @param buf
   *   destination array
   * @param offset
   *   starting position in `buf`
   * @param maxLen
   *   maximum number of elements to read
   * @return
   *   number of elements read, `-1` on end-of-stream, or `0` when `maxLen == 0`
   */
  def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: Elem <:< Double): Int = {
    if (maxLen == 0) return 0
    val sentinel = Double.MaxValue
    var i        = 0
    while (i < maxLen) {
      val v = readDouble(sentinel)(unsafeEvidence)
      if (doubleEOF(this, v, sentinel)) return if (i > 0) i else -1
      buf(offset + i) = v
      i += 1
      if (i < maxLen && !readable()) return i
    }
    i
  }

  /**
   * Box-free bulk Float pull into a caller-supplied buffer.
   *
   * Contract mirrors [[readBytes]]:
   *   - Blocks until at least 1 element is available.
   *   - Returns the number of elements read (`1 <= r <= maxLen`).
   *   - Returns `-1` when closed and empty.
   *   - Returns `0` immediately when `maxLen == 0`.
   *
   * @param buf
   *   destination array
   * @param offset
   *   starting position in `buf`
   * @param maxLen
   *   maximum number of elements to read
   * @return
   *   number of elements read, `-1` on end-of-stream, or `0` when `maxLen == 0`
   */
  def readFloats(buf: Array[Float], offset: Int, maxLen: Int)(implicit ev: Elem <:< Float): Int = {
    if (maxLen == 0) return 0
    val sentinel = Double.MaxValue
    var i        = 0
    while (i < maxLen) {
      val v = readFloat(sentinel)(unsafeEvidence)
      if (v == sentinel) return if (i > 0) i else -1
      buf(offset + i) = v.toFloat
      i += 1
      if (i < maxLen && !readable()) return i
    }
    i
  }

  /**
   * Sentinel-return Char pull. Returns element widened to Int, or `sentinel`
   * when closed. Requires implicit evidence that `Elem` is a subtype of `Char`.
   */
  def readChar(sentinel: Int)(implicit ev: Elem <:< Char): Int = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel else v.asInstanceOf[java.lang.Character].charValue().toInt
  }

  /**
   * Sentinel-return Double pull. Returns element widened/exact, or `sentinel`
   * when closed. Requires implicit evidence that `Elem` is a subtype of
   * `Double`.
   */
  def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
    else { markReadValue(); v.asInstanceOf[java.lang.Number].doubleValue() }
  }

  /**
   * Sentinel-return Float pull. Returns element widened to Double, or
   * `sentinel` when closed. Requires implicit evidence that `Elem` is a subtype
   * of `Float`.
   */
  def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
    else { markReadValue(); v.asInstanceOf[java.lang.Number].floatValue().toDouble }
  }

  /**
   * Sentinel-return Int pull. Returns the element widened to `Long`, or
   * `sentinel` when closed and empty. The sentinel must lie outside
   * `[Int.MinValue, Int.MaxValue]` (e.g. `Long.MinValue`). Requires implicit
   * evidence that `Elem` is a subtype of `Int`.
   */
  def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
    else { markReadValue(); v.asInstanceOf[java.lang.Number].intValue().toLong }
  }

  /**
   * Terminal bulk fold over the `Int` lane: applies `f` to every remaining
   * element in order, threading the accumulator, and returns the final
   * accumulator at end-of-stream. Observationally identical to the sentinel
   * pull loop
   *
   * {{{
   * var acc = z; var v = readInt(Long.MinValue)
   * while (v != Long.MinValue) { acc = f(acc, v.toInt); v = readInt(Long.MinValue) }
   * acc
   * }}}
   *
   * which is the default implementation (`Int` widened to `Long` can never
   * equal the `Long.MinValue` sentinel, so the loop is collision-free). Hot
   * leaf readers override this with a tight local loop, and composite readers
   * (flatMap, concat) delegate segment-by-segment, so a terminal fold pays no
   * per-element virtual hop through the reader chain. Overrides MUST consume
   * elements in exactly the same order, leave the reader in the same state on
   * completion and on a thrown exception (from `f` or upstream), and respect
   * window (`setSkip`/`setLimit`) and repeat state.
   */
  def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: Elem <:< Int): Long = {
    val s   = Long.MinValue
    var acc = z
    var v   = readInt(s)
    while (v != s) { acc = f(acc, v.toInt); v = readInt(s) }
    acc
  }

  /**
   * Sentinel-return Long pull. Returns the element, or `sentinel` when closed
   * and empty. The sentinel must be a value that never appears in the stream
   * (e.g. `Long.MaxValue`). Requires implicit evidence that `Elem` is a subtype
   * of `Long`.
   */
  def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long = {
    val v = read[Any](EndOfStream);
    if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
    else { markReadValue(); v.asInstanceOf[java.lang.Number].longValue() }
  }

  /**
   * Sentinel-return Short pull. Returns element widened to Int, or `sentinel`
   * when closed. Requires implicit evidence that `Elem` is a subtype of
   * `Short`.
   */
  def readShort(sentinel: Int)(implicit ev: Elem <:< Short): Int = {
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
   * '''Contract:''' [[setLimit]] is the native pushdown of `take(n)` and
   * [[setSkip]] the native pushdown of `drop(n)`. They are NOT independent
   * absolute window parameters: successive calls compose '''in invocation
   * order''' over the current live window and must be observationally
   * equivalent to wrapping the reader in the corresponding `take`/`drop`
   * combinators. For example, on `0 until 10`, `setLimit(5)` then `setSkip(3)`
   * yields positions `[3,5)` (i.e. `take(5).drop(3)`), whereas `setSkip(3)`
   * then `setLimit(5)` yields `[3,8)` (i.e. `drop(3).take(5)`). Implementations
   * must store the derived window bounds (not raw skip/limit values that
   * recompute as `[skip, skip+limit)`), so `reset()` restores the same composed
   * window.
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
      val s = Long.MinValue; var r = n; while (r > 0) { if (readInt(s)(unsafeEvidence) == s) return; r -= 1 }
    } else if (et eq JvmType.Byte) {
      val s = Long.MinValue; var r = n; while (r > 0) { if (readInt(s)(unsafeEvidence) == s) return; r -= 1 }
    } else if (et eq JvmType.Long) {
      val s = Long.MaxValue; var r = n;
      while (r > 0) { if (longEOF(this, readLong(s)(unsafeEvidence), s)) return; r -= 1 }
    } else if (et eq JvmType.Float) {
      val s = Double.MaxValue; var r = n; while (r > 0) { if (readFloat(s)(unsafeEvidence) == s) return; r -= 1 }
    } else if (et eq JvmType.Double) {
      val s = Double.MaxValue; var r = n;
      while (r > 0) { if (doubleEOF(this, readDouble(s)(unsafeEvidence), s)) return; r -= 1 }
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
      // If both `self.close()` and `release()` fail, the release failure is
      // suppressed onto the close failure rather than discarding it
      // (Principle 4). `release` is a per-close hook (like `ensuring`): under
      // `repeated`, each cycle's close re-fires it after reset() re-arms the
      // wrapped reader.
      override def close(): Unit = runBoth(self.close())(release())
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
   * Extracts the low byte from a boxed element on the generic (ref-lane)
   * `readByte` path, mirroring the base `readByte` lane dispatch: numbers use
   * their integral value, `Char` its code point, `Boolean` 1/0. Anything else
   * fails with the same ClassCastException as before (a genuine misuse signal).
   * Cold path: only reached when a non-specialized reader is drained through
   * the byte view (BUG-R5-03).
   */
  private[streams] def anyToLowByte(v: Any): Int = v match {
    case n: java.lang.Number    => n.intValue() & 0xff
    case c: java.lang.Character => c.charValue().toInt & 0xff
    case b: java.lang.Boolean   => if (b.booleanValue()) 1 else 0
    case other                  => other.asInstanceOf[java.lang.Number].intValue() & 0xff
  }

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

  /** Wraps a [[java.io.InputStream]] as a `Reader[Byte]`. */
  def fromInputStream(is: InputStream): Reader[Byte] =
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
   * A lazy reader over the half-open integer interval `[from, until)` with step
   *   1. Unlike [[fromRange]] it never constructs a `scala.Range`, so it can
   *      back an interval spanning more than `Int.MaxValue` integers (e.g.
   *      `range(Int.MinValue, 0)`); the element count is tracked as a `Long`. A
   *      lazy consumer (`take`/`drop`) pulls only what it needs and never
   *      materializes the full length (BUG-N3).
   */
  def range(from: Int, until: Int): Reader[Int] =
    new FromIntRange(from, until)

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
   * Saturating window arithmetic shared by the chunk/range readers: advances
   * `from` forward by `by` elements, clamped to `[from, cap]`. A negative `by`
   * is treated as `0`. `by` is a `Long` (take/drop accept `Long` counts) but
   * the result is an `Int` index; capping at `cap` first guarantees `from + by`
   * never overflows `Int`, even for `by == Long.MaxValue`. Used to compose
   * `setSkip`/`setLimit` as incremental window operations so `take`/`drop`
   * order is preserved (BUG-B).
   */
  private[streams] def advanceWithin(from: Int, by: Long, cap: Int): Int =
    if (by <= 0L) from
    else {
      val room = (cap - from).toLong
      if (by >= room) cap else from + by.toInt
    }

  /**
   * `Long` analogue of [[advanceWithin]] for windows whose element count can
   * exceed `Int.MaxValue` (e.g. `Stream.range(Int.MinValue, 0)`).
   */
  private[streams] def advanceWithinL(from: Long, by: Long, cap: Long): Long =
    if (by <= 0L) from
    else {
      val room = cap - from
      if (by >= room) cap else from + by
    }

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
      while (r > 0 && reader.readInt(Long.MinValue)(unsafeEvidence) != Long.MinValue) r -= 1
    } else if (et eq JvmType.Byte) {
      while (r > 0 && reader.readInt(Long.MinValue)(unsafeEvidence) != Long.MinValue) r -= 1
    } else if (et eq JvmType.Long) {
      while (r > 0 && !longEOF(reader, reader.readLong(Long.MaxValue)(unsafeEvidence), Long.MaxValue)) r -= 1
    } else if (et eq JvmType.Float) {
      while (r > 0 && reader.readFloat(Double.MaxValue)(unsafeEvidence) != Double.MaxValue) r -= 1
    } else if (et eq JvmType.Double) {
      while (r > 0 && !doubleEOF(reader, reader.readDouble(Double.MaxValue)(unsafeEvidence), Double.MaxValue)) r -= 1
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

    override def readChar(sentinel: Int)(implicit ev: Char <:< Char): Int =
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
      val c = readChar(-1)(unsafeEvidence)
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
    firstTail: AnyRef, // a `() => Reader[Elem]` thunk OR a lazily-compiled `Stream[_, Elem]`
    outJvmType: JvmType
  ) extends Reader[Elem] {
    override def jvmType: JvmType = outJvmType

    private var current: Reader[Elem] = head
    private var tail: Array[AnyRef]   = { val a = new Array[AnyRef](4); a(0) = firstTail; a }
    private var tailLen: Int          = 1
    private var tailIdx: Int          = 0
    private var done: Boolean         = false

    // Lazily-created reusable single-element reader, re-armed for each
    // `Stream.succeed(prim)` tail segment instead of allocating a fresh
    // `SingletonPrim` per segment (long `s ++ succeed(..) ++ ...` spines —
    // nested_concat). Never user-visible; a segment is only re-armed after the
    // previous segment was closed by `advance()`, mirroring the approved
    // `FlatMapped.compileInner` reuse pattern.
    private var cachedSingleton: ReusableSingletonPrim = null

    /**
     * Append a lazy tail entry: either a `() => Reader[Elem]` thunk or a
     * `Stream[_, Elem]` compiled at `advance()` time (same laziness, no thunk
     * allocation — used by the deep concat-spine compiler). O(1) amortized.
     */
    private[streams] def append(entry: AnyRef): Unit = {
      if (tailLen == tail.length) {
        val next = new Array[AnyRef](tail.length * 2)
        System.arraycopy(tail, 0, next, 0, tailLen)
        tail = next
      }
      tail(tailLen) = entry
      tailLen += 1
    }

    /**
     * Presizes the tail array for `additional` more entries (one allocation
     * instead of O(log n) doubling copies). Used by the deep concat-spine
     * compiler, which knows the spine length up front.
     */
    private[streams] def ensureTailCapacity(additional: Int): Unit = {
      val need = tailLen + additional
      if (need > tail.length) {
        val next = new Array[AnyRef](need)
        System.arraycopy(tail, 0, next, 0, tailLen)
        tail = next
      }
    }

    /**
     * Constructs the next segment's reader from a tail entry (segment-boundary
     * cost only).
     */
    private def readerOf(entry: AnyRef): Reader[Elem] = entry match {
      case t: Function0[_]                              => t().asInstanceOf[Reader[Elem]]
      case sp: zio.blocks.streams.Stream.SucceedPrim[_] =>
        var c = cachedSingleton
        if (c eq null) { c = new ReusableSingletonPrim; cachedSingleton = c }
        c.arm(sp.prim, sp.ord)
        c.asInstanceOf[Reader[Elem]]
      case s =>
        zio.blocks.streams.Stream
          .compileToReader(s.asInstanceOf[zio.blocks.streams.Stream[Any, Elem]])
    }

    // True once `current` has been closed by `advance` but not yet replaced by a
    // successfully-constructed next segment. This makes `close()`/`reset()`
    // idempotent for `current`: if the next-segment thunk throws, `current`
    // still references the already-closed segment, and re-closing it would run
    // its finalizer twice (double finalization).
    private var currentClosed = false

    /** Advance to the next segment. Returns true if a next segment exists. */
    private def advance(): Boolean = {
      // Once terminal (EOF reached or close() called), never advance again: a
      // read after close()/EOF must return the sentinel, not materialize the
      // next tail segment, and must not re-run the already-finalized segment's
      // close hook a second time (BUG-R5-02).
      if (done) return false
      // Mark `current` finalized BEFORE closing it. A close failure here aborts
      // the read immediately; nothing else is in flight, so it is the primary
      // and must propagate (Principle 4). But even when `close()` throws, the
      // already-closed segment must not be finalized a second time by the outer
      // `close()` during terminal cleanup (double finalization), so the guard
      // must be set first.
      currentClosed = true
      current.close()
      if (tailIdx < tailLen) {
        // The entry's construction may throw (deferred construction / failing
        // acquire). If it does, `currentClosed` stays true so the
        // already-closed segment is not finalized again by the outer `close()`.
        current = readerOf(tail(tailIdx))
        currentClosed = false
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

    override def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long = {
      while (true) {
        // See `FlatMappedBase.readInt`: a ref-lane segment (a boxed Byte map —
        // BUG-N1 — or a deep interpreter with a ref-register element) must be
        // pulled via the boxed `read` and unboxed, never the specialized
        // `readInt` (CCE — BUG-N2 / silent corruption — BUG-N4). Native int-lane
        // segments stay on the zero-boxing fast path.
        val v =
          if (current.jvmType eq JvmType.AnyRef) {
            val r = current.read[Any](EndOfStream)
            if (r.asInstanceOf[AnyRef] eq EndOfStream) sentinel
            else r.asInstanceOf[java.lang.Number].longValue()
          } else current.readInt(sentinel)(unsafeEvidence)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    // Bulk terminal fold: drain each segment with ITS `foldInt` (tight local
    // loop for leaf segments), advancing exactly where the `readInt` loop
    // above would. The lane routing is the same, applied once per SEGMENT
    // instead of per element: ref-lane segments are pulled boxed and unboxed
    // (BUG-N1/N2/N4); native int-lane segments stay on the zero-boxing path.
    override def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: Elem <:< Int): Long = {
      var acc = z
      while (true) {
        if (current.jvmType eq JvmType.AnyRef) {
          var r = current.read[Any](EndOfStream)
          while (r.asInstanceOf[AnyRef] ne EndOfStream) {
            acc = f(acc, r.asInstanceOf[java.lang.Number].intValue())
            r = current.read[Any](EndOfStream)
          }
        } else acc = current.foldInt(acc, f)(unsafeEvidence)
        if (!advance()) return acc
      }
      acc // unreachable
    }

    // `lastReadWasEOF` reflects the last segment we pulled from: when a value is
    // returned, `current`'s last read was that value (flag false); when the
    // overall stream is exhausted, `current` is the final segment whose last
    // read hit EOF (flag true).
    override def lastReadWasEOF: Boolean = current.lastReadWasEOF

    override def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long = {
      while (true) {
        val v = current.readLong(sentinel)(unsafeEvidence)
        if (!longEOF(current, v, sentinel)) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double = {
      while (true) {
        val v = current.readFloat(sentinel)(unsafeEvidence)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double = {
      while (true) {
        val v = current.readDouble(sentinel)(unsafeEvidence)
        if (!doubleEOF(current, v, sentinel)) return v
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

    // The four bulk-array reads loop the advance exactly like `readUpToN`
    // below: a single `advance()` cannot skip an empty/exhausted INTERMEDIATE
    // segment, which would report premature EOF and silently drop every later
    // segment. The loop only runs at segment boundaries; the per-element hot
    // path is the delegated bulk read itself.
    override def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: Elem <:< Int): Int = {
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readInts(buf, offset, maxLen)(unsafeEvidence)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1 // unreachable
    }

    override def readLongs(buf: Array[Long], offset: Int, maxLen: Int)(implicit ev: Elem <:< Long): Int = {
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readLongs(buf, offset, maxLen)(unsafeEvidence)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1 // unreachable
    }

    override def readFloats(buf: Array[Float], offset: Int, maxLen: Int)(implicit ev: Elem <:< Float): Int = {
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readFloats(buf, offset, maxLen)(unsafeEvidence)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1 // unreachable
    }

    override def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: Elem <:< Double): Int = {
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readDoubles(buf, offset, maxLen)(unsafeEvidence)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1 // unreachable
    }

    override def readUpToN[A1 >: Elem](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      // Loop the advance: a single `advance()` cannot skip an empty/exhausted
      // INTERMEDIATE segment (which would silently drop every later segment).
      // Keep advancing until a segment yields elements or the spine is done.
      while (true) {
        val chunk = current.readUpToN[A1](n)
        if (chunk.nonEmpty) return chunk
        if (!advance()) return Chunk.empty
      }
      Chunk.empty // unreachable
    }

    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)

    def close(): Unit =
      if (!done) {
        done = true
        // No throwable in flight; a close failure is the primary (Principle 4).
        // Skip if `advance` already closed `current` (a failed next-segment
        // construction) to avoid finalizing it twice — and SET the flag after
        // closing, so a subsequent reset() cannot finalize the same segment a
        // second time (BUG-R9-03, the ITER-5a invariant: the idempotence flag
        // is both read AND set on every close path).
        if (!currentClosed) { currentClosed = true; current.close() }
      }

    override def reset(): Unit = {
      // Close current tail reader if it wasn't already closed by advance().
      // No throwable in flight; a close failure is the primary (Principle 4).
      if ((current ne head) && !currentClosed && !current.isClosed) current.close()
      current = head
      currentClosed = false
      head.reset()
      tailIdx = 0
      done = false
    }
  }

  /**
   * Projects every element of an inner reader through `f`, reporting
   * `outJvmType` as its JVM lane. Used by [[Reader.concat]] in the disjoint
   * (non-identity) `Concat` case to wrap each side's elements as `Left` /
   * `Right` (output is a boxed `Either`, hence reads go through the generic
   * boxed path).
   */
  private[streams] final class ProjectedReader(
    inner: Reader[_],
    f: Any => Any,
    outJvmType: JvmType
  ) extends Reader[Any] {
    override def jvmType: JvmType         = outJvmType
    def isClosed: Boolean                 = inner.isClosed
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = inner.read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else f(v).asInstanceOf[A1]
    }
    def close(): Unit                = inner.close()
    override def reset(): Unit       = inner.reset()
    override def skip(n: Long): Unit = {
      var r = n
      while (r > 0) {
        val v = inner.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return
        r -= 1
      }
    }
  }

  /**
   * A reader that delegates all methods to an inner reader. Subclass and
   * override only the methods you need to change (typically `close()`).
   */
  private[streams] abstract class DelegatingReader[+Elem](inner: Reader[Elem]) extends Reader[Elem] {
    override def jvmType: JvmType                                                 = inner.jvmType
    def isClosed: Boolean                                                         = inner.isClosed
    def read[A1 >: Elem](sentinel: A1): A1                                        = inner.read(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long         = inner.readInt(sentinel)(unsafeEvidence)
    override def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long       = inner.readLong(sentinel)(unsafeEvidence)
    override def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double =
      inner.readFloat(sentinel)(unsafeEvidence)
    override def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double =
      inner.readDouble(sentinel)(unsafeEvidence)
    override def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: Elem <:< Int): Int =
      inner.readInts(buf, offset, maxLen)(unsafeEvidence)
    override def readLongs(buf: Array[Long], offset: Int, maxLen: Int)(implicit ev: Elem <:< Long): Int =
      inner.readLongs(buf, offset, maxLen)(unsafeEvidence)
    override def readFloats(buf: Array[Float], offset: Int, maxLen: Int)(implicit ev: Elem <:< Float): Int =
      inner.readFloats(buf, offset, maxLen)(unsafeEvidence)
    override def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: Elem <:< Double): Int =
      inner.readDoubles(buf, offset, maxLen)(unsafeEvidence)
    override def readByte(): Int                          = inner.readByte()
    override def readUpToN[A1 >: Elem](n: Int): Chunk[A1] = inner.readUpToN(n)
    override def skip(n: Long): Unit                      = inner.skip(n)
    def close(): Unit                                     = inner.close()
    override def reset(): Unit                            = inner.reset()
    override def readable(): Boolean                      = inner.readable()
    override def lastReadWasEOF: Boolean                  = inner.lastReadWasEOF
  }

  /**
   * Adapts a ref(AnyRef)-lane inner reader so it can be pulled through the
   * zero-boxing `readInt` fast path without mis-casting its boxed element. The
   * underlying reader is read via the boxed `read` and unboxed to an `Int`
   * value; every other lane delegates unchanged. Used by
   * [[FlatMappedBase.advance]] to wrap a ref-lane inner ONCE (cold path) so
   * `FlatMappedBase.readInt` stays the tight, inlinable `inner.readInt` loop.
   * See the `advance` comment for the BUG-N1/N2/N4 rationale.
   */
  private[streams] final class RefLaneIntReader(u: Reader[Any]) extends DelegatingReader[Any](u) {
    override def jvmType: JvmType                                        = JvmType.Int
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val r = u.read[Any](EndOfStream)
      if (r.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else r.asInstanceOf[java.lang.Number].longValue()
    }
  }

  /**
   * Wraps a Double-specialized source reader and applies a single predicate.
   */
  private[streams] final class FilteredDouble(
    val source: Reader[_],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                                  = source.jvmType
    def isClosed: Boolean                                                          = source.isClosed
    override def lastReadWasEOF: Boolean                                           = source.lastReadWasEOF
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      var v = source.readDouble(sentinel)(unsafeEvidence)
      while (!doubleEOF(source, v, sentinel) && !pred.asInstanceOf[Double => Boolean](v))
        v = source.readDouble(sentinel)(unsafeEvidence)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readDouble(Double.MaxValue)(unsafeEvidence);
      if (doubleEOF(this, v, Double.MaxValue)) sentinel else Double.box(v).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
      val s = Double.MaxValue
      var v = readDouble(s)(unsafeEvidence)
      if (doubleEOF(this, v, s)) return Chunk.empty
      var i = 0
      while (!doubleEOF(this, v, s) && i < n) {
        b.addOne(v); i += 1
        if (i < n) v = readDouble(s)(unsafeEvidence)
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && !doubleEOF(this, readDouble(s)(unsafeEvidence), s)) r -= 1
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
    val source: Reader[_],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                                = source.jvmType
    def isClosed: Boolean                                                        = source.isClosed
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      var v = source.readFloat(sentinel)(unsafeEvidence)
      while (v != sentinel && !pred.asInstanceOf[Float => Boolean](v.toFloat))
        v = source.readFloat(sentinel)(unsafeEvidence)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readFloat(Double.MaxValue)(unsafeEvidence);
      if (v == Double.MaxValue) sentinel else Float.box(v.toFloat).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
      val s = Double.MaxValue
      var v = readFloat(s)(unsafeEvidence)
      if (v == s) return Chunk.empty
      var i = 0
      while (v != s && i < n) {
        b.addOne(v.toFloat); i += 1
        if (i < n) v = readFloat(s)(unsafeEvidence)
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && readFloat(s)(unsafeEvidence) != s) r -= 1
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
    val source: Reader[_],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                        = source.jvmType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      var v = source.readInt(sentinel)(unsafeEvidence)
      while (v != sentinel && !pred.asInstanceOf[Int => Boolean](v.toInt))
        v = source.readInt(sentinel)(unsafeEvidence)
      v
    }
    // Bulk terminal fold: compose the predicate into the fold step and
    // delegate to the source, collapsing this stage onto the leaf loop. Only
    // reached when `jvmType eq Int` (so the source is int-lane readable, the
    // same precondition `readInt` relies on).
    override def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: Any <:< Int): Long =
      source.foldInt(z, new FoldThroughIntFilter(pred, f))(unsafeEvidence)
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readInt(Long.MinValue)(unsafeEvidence);
      if (v == Long.MinValue) sentinel else Int.box(v.toInt).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
      val s = Long.MinValue
      var v = readInt(s)(unsafeEvidence)
      if (v == s) return Chunk.empty
      var i = 0
      while (v != s && i < n) {
        b.addOne(v.toInt); i += 1
        if (i < n) v = readInt(s)(unsafeEvidence)
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MinValue; var r = n; while (r > 0 && readInt(s)(unsafeEvidence) != s) r -= 1
    }
    override def setRepeat(): Boolean = source.setRepeat()
    def toInterpreter: Interpreter    = {
      val p = Interpreter(source)
      p.addFilter[Any](Interpreter.laneOf(JvmType.Int))(pred.asInstanceOf[Any => Boolean])
      p
    }
  }

  /**
   * Fused `filter(Int).map(Int => Int)` reader. The naive compilation nests
   * `MappedInt(FilteredInt(source))`, a 3-deep monomorphic `readInt` chain
   * whose full inlining HotSpot performs only intermittently — the `filterMap`
   * benchmark would land in the un-inlined plan and lose ~25% throughput.
   * Fusing the filter skip-loop and the map application into ONE method gives
   * the JIT a single self-contained hot loop, restoring (and exceeding)
   * baseline. Only created for `Int -> Int` maps over an `Int`-lane filter (the
   * common numeric case); other lanes/element types keep the generic wrappers.
   * The `Int` lane is sentinel-safe (`f`'s `Int` output widened to `Long` can
   * never equal the `Long.MinValue` sentinel), so no `lastReadWasEOF` tracking
   * is needed.
   */
  private[streams] final class FilteredMappedInt(
    val source: Reader[_],
    val pred: AnyRef,
    val f: AnyRef
  ) extends Reader[Any] {
    override def jvmType: JvmType                                        = JvmType.Int
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      var v = source.readInt(sentinel)(unsafeEvidence)
      while (v != sentinel && !pred.asInstanceOf[Int => Boolean](v.toInt))
        v = source.readInt(sentinel)(unsafeEvidence)
      if (v == sentinel) sentinel else f.asInstanceOf[Int => Int](v.toInt).toLong
    }
    // Bulk terminal fold: compose predicate + map into the fold step and
    // delegate to the source, collapsing this stage onto the leaf loop.
    override def foldInt(z: Long, fold: (Long, Int) => Long)(implicit ev: Any <:< Int): Long =
      source.foldInt(z, new FoldThroughIntFilterMap(pred, f, fold))(unsafeEvidence)
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readInt(Long.MinValue)(unsafeEvidence)
      if (v == Long.MinValue) sentinel else Int.box(v.toInt).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
      val s = Long.MinValue
      var v = readInt(s)(unsafeEvidence)
      if (v == s) return Chunk.empty
      var i = 0
      while (v != s && i < n) {
        b.addOne(v.toInt); i += 1
        if (i < n) v = readInt(s)(unsafeEvidence)
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    override def skip(n: Long): Unit = {
      val s = Long.MinValue; var r = n; while (r > 0 && readInt(s)(unsafeEvidence) != s) r -= 1
    }
    def close(): Unit                 = source.close()
    override def reset(): Unit        = source.reset()
    override def setRepeat(): Boolean = source.setRepeat()
  }

  /**
   * Specialized composition of two `Int => Int` functions, used to fuse
   * consecutive `map(Int => Int)` stages into a single [[MappedInt]] /
   * [[FilteredMappedInt]] reader at compile time. A chain of N maps otherwise
   * compiles to N nested readers whose per-element `readInt` virtual chain
   * HotSpot stops inlining after a few levels (~3 ns per level); composing the
   * functions instead keeps ONE reader on the hot path. Extends `(Int => Int)`
   * so the specialized `apply$mcII$sp` is generated and the composed
   * application never boxes.
   */
  private[streams] final class ComposedIntInt(val f: AnyRef, val g: AnyRef) extends (Int => Int) {
    def apply(i: Int): Int = g.asInstanceOf[Int => Int](f.asInstanceOf[Int => Int](i))
  }

  /**
   * Flat array-of-stages composition of three or more `Int => Int` functions,
   * applied left-to-right (application order). Deep map chains (e.g. 100
   * consecutive `.map`s) otherwise compose into a left-nested
   * [[ComposedIntInt]] tree whose `apply` recursion exceeds HotSpot's
   * recursive-inline depth (`MaxRecursiveInlineLevel` = 1), paying ~2
   * un-inlined calls plus a real stack frame per stage per element. A flat
   * array loop has ONE call site that stays monomorphic when the stages share a
   * lambda class, and no deep stack. Exception semantics and application order
   * match the nested form exactly.
   */
  private[streams] final class ComposedIntArray(val fns: Array[AnyRef]) extends (Int => Int) {
    def apply(i: Int): Int = {
      val a = fns
      // Hand-unrolled small arities: each array position gets its OWN bytecode
      // call site, so a chain of 3-5 DISTINCT lambda classes stays monomorphic
      // per site (the shared loop site below would go megamorphic, ~2x slower
      // on a 5-distinct-map chain). 6+ stages take the loop; deep chains are
      // typically built from one lambda class (monomorphic site) and the loop
      // avoids the nested tree's un-inlinable recursion either way.
      (a.length: @scala.annotation.switch) match {
        case 3 =>
          a(2).asInstanceOf[Int => Int](a(1).asInstanceOf[Int => Int](a(0).asInstanceOf[Int => Int](i)))
        case 4 =>
          a(3).asInstanceOf[Int => Int](
            a(2).asInstanceOf[Int => Int](a(1).asInstanceOf[Int => Int](a(0).asInstanceOf[Int => Int](i)))
          )
        case 5 =>
          a(4).asInstanceOf[Int => Int](
            a(3).asInstanceOf[Int => Int](
              a(2).asInstanceOf[Int => Int](a(1).asInstanceOf[Int => Int](a(0).asInstanceOf[Int => Int](i)))
            )
          )
        case _ =>
          var v = i
          var j = 0
          while (j < a.length) { v = a(j).asInstanceOf[Int => Int](v); j += 1 }
          v
      }
    }
  }

  /**
   * Composes an already-fused `Int => Int` stage function `f` with one more
   * stage `g` (applied after `f`). Two stages keep the direct
   * [[ComposedIntInt]] shape (fully inlinable); three or more switch to the
   * flat [[ComposedIntArray]] loop.
   */
  private[streams] def composeIntInt(f: AnyRef, g: AnyRef): AnyRef = f match {
    case c: ComposedIntArray =>
      val old = c.fns
      val n   = old.length
      val arr = new Array[AnyRef](n + 1)
      System.arraycopy(old, 0, arr, 0, n)
      arr(n) = g
      new ComposedIntArray(arr)
    case c: ComposedIntInt =>
      val arr = new Array[AnyRef](3)
      arr(0) = c.f; arr(1) = c.g; arr(2) = g
      new ComposedIntArray(arr)
    case _ => new ComposedIntInt(f, g)
  }

  // ---------------------------------------------------------------------------
  // Bulk-fold composition wrappers (see `Reader.foldInt`). Each stage reader
  // delegates its terminal fold to `source.foldInt` with the stage operation
  // composed INTO the fold function, so the whole stage chain collapses onto
  // the leaf reader's tight local loop — zero per-element virtual hops through
  // the reader chain. All three extend `(Long, Int) => Long`, which is inside
  // `Function2`'s specialization set, so `apply$mcJJI$sp` is generated and the
  // composed application never boxes. Application ORDER matches the pull path
  // exactly: source element -> stage op(s) -> fold step.
  // ---------------------------------------------------------------------------

  /** Fold step composed through a `map(Int => Int)` stage. */
  private[streams] final class FoldThroughIntMap(val g: AnyRef, val outer: (Long, Int) => Long)
      extends ((Long, Int) => Long) {
    def apply(acc: Long, v: Int): Long = outer(acc, g.asInstanceOf[Int => Int](v))
  }

  /** Fold step composed through a `filter(Int => Boolean)` stage. */
  private[streams] final class FoldThroughIntFilter(val p: AnyRef, val outer: (Long, Int) => Long)
      extends ((Long, Int) => Long) {
    def apply(acc: Long, v: Int): Long =
      if (p.asInstanceOf[Int => Boolean](v)) outer(acc, v) else acc
  }

  /** Fold step composed through a fused `filter.map` stage. */
  private[streams] final class FoldThroughIntFilterMap(val p: AnyRef, val g: AnyRef, val outer: (Long, Int) => Long)
      extends ((Long, Int) => Long) {
    def apply(acc: Long, v: Int): Long =
      if (p.asInstanceOf[Int => Boolean](v)) outer(acc, g.asInstanceOf[Int => Int](v)) else acc
  }

  /** Wraps a Long-specialized source reader and applies a single predicate. */
  private[streams] final class FilteredLong(
    val source: Reader[_],
    val pred: AnyRef
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                          = source.jvmType
    def isClosed: Boolean                                                  = source.isClosed
    override def lastReadWasEOF: Boolean                                   = source.lastReadWasEOF
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      var v = source.readLong(sentinel)(unsafeEvidence)
      while (!longEOF(source, v, sentinel) && !pred.asInstanceOf[Long => Boolean](v))
        v = source.readLong(sentinel)(unsafeEvidence)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readLong(Long.MaxValue)(unsafeEvidence);
      if (longEOF(this, v, Long.MaxValue)) sentinel else Long.box(v).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
      val s = Long.MaxValue
      var v = readLong(s)(unsafeEvidence)
      if (longEOF(this, v, s)) return Chunk.empty
      var i = 0
      while (!longEOF(this, v, s) && i < n) {
        b.addOne(v); i += 1
        if (i < n) v = readLong(s)(unsafeEvidence)
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MaxValue; var r = n; while (r > 0 && !longEOF(this, readLong(s)(unsafeEvidence), s)) r -= 1
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
    val source: Reader[_],
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
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = ChunkBuilder.make[A1](math.min(n, 64))
      var v = read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
      var i = 0
      while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
        b += v.asInstanceOf[A1]; i += 1
        if (i < n) v = read[Any](EndOfStream)
      }
      b.result()
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
    val source: Reader[_],
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
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = ChunkBuilder.make[A1](math.min(n, 64))
      var v = read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
      var i = 0
      while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
        b += v.asInstanceOf[A1]; i += 1
        if (i < n) v = read[Any](EndOfStream)
      }
      b.result()
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
        val toClose = inner
        inner = null
        // A close failure here aborts the read immediately; nothing else is in
        // flight, so it is the primary and must propagate (Principle 4).
        toClose.close()
      }
      if (!pullOuter()) { _closed = true; return false }
      // BUG-N1/N2/N4: a ref(AnyRef)-lane inner must NOT be pulled via the
      // zero-boxing `readInt` fast path — a boxed Byte map (BUG-N1) would mis-cast
      // a `java.lang.Byte` (CCE — BUG-N2), and a deep interpreter whose element
      // sits in the ref register would return the wrong lane (silent corruption —
      // BUG-N4). Wrap such an inner ONCE (cold path) in `RefLaneIntReader`, which
      // unboxes on `readInt`, so the per-element `readInt` hot loop stays the
      // tight, inlinable `inner.readInt` call that the common int-lane inner
      // takes. The allocation lives in the cold `wrapInnerRefLane`.
      if (inner.jvmType eq JvmType.AnyRef) wrapInnerRefLane()
      true
    }

    private def wrapInnerRefLane(): Unit = inner = new RefLaneIntReader(inner)

    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      while (true) {
        if (inner != null) { val v = inner.readInt(sentinel)(unsafeEvidence); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }

    // Bulk terminal fold: drain each inner reader with ITS `foldInt` (a tight
    // local loop for the common leaf inners) instead of bouncing every element
    // through this reader's two-level virtual `readInt` hop. Element order,
    // inner close/advance points, and exception propagation are identical to
    // the `readInt` loop above. Ref-lane inners were already wrapped in
    // `RefLaneIntReader` by `advance()` (BUG-N1/N2/N4), whose inherited
    // default `foldInt` unboxes via its own `readInt`.
    override def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: Any <:< Int): Long = {
      var acc = z
      while (true) {
        if (inner != null) acc = inner.foldInt(acc, f)(unsafeEvidence)
        if (!advance()) return acc
      }
      acc
    }

    // `inner` is nulled on `advance()`, so this reader cannot delegate
    // `lastReadWasEOF` to it; instead it sets its own flag on each return path.
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      while (true) {
        if (inner != null) {
          val v = inner.readLong(sentinel)(unsafeEvidence);
          if (!longEOF(inner, v, sentinel)) { markReadValue(); return v }
        }
        if (!advance()) { markReadEOF(); return sentinel }
      }
      sentinel
    }
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      while (true) {
        if (inner != null) { val v = inner.readFloat(sentinel)(unsafeEvidence); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      while (true) {
        if (inner != null) {
          val v = inner.readDouble(sentinel)(unsafeEvidence)
          if (!doubleEOF(inner, v, sentinel)) { markReadValue(); return v }
        }
        if (!advance()) { markReadEOF(); return sentinel }
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
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readInt(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readInt(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val s = Long.MaxValue
        var v = readLong(s)(unsafeEvidence)
        if (longEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!longEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readLong(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloat(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloat(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readDouble(s)(unsafeEvidence)
        if (doubleEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!doubleEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readDouble(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else {
        val b = ChunkBuilder.make[A1](math.min(n, 64))
        var v = read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
        var i = 0
        while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
          b += v.asInstanceOf[A1]; i += 1
          if (i < n) v = read[Any](EndOfStream)
        }
        b.result()
      }
    }
    def close(): Unit = {
      _closed = true
      val toClose = inner
      inner = null
      // Close the inner reader and the source; if both fail, the source failure
      // is suppressed onto the inner failure and the inner failure propagates
      // (Principle 4).
      if (toClose != null) runBoth(toClose.close())(closeSource())
      else closeSource()
    }
    protected def closeSource(): Unit

    // flatMap is a structural combinator: it must replay under `repeated`
    // whenever its outer source is resettable. Close any active inner reader,
    // reset the outer source (propagating UnsupportedOperationException for a
    // genuinely one-shot source), and return to the pre-read state so the next
    // read pulls a fresh outer element and compiles a fresh inner. Inner streams
    // remain replayable/one-shot per their own construction.
    override def reset(): Unit = {
      val toClose = inner
      inner = null
      // No throwable in flight; if both inner-close and source-reset fail, the
      // inner-close failure is primary and the source-reset failure is
      // suppressed (Principle 4).
      if (toClose != null) runBoth(toClose.close())(resetSource())
      else resetSource()
      _closed = false
    }
    protected def resetSource(): Unit
  }

  /** FlatMap reader for Double-specialized sources. */
  private[streams] final class FlatMappedDouble(
    val source: Reader[_],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readDouble(Double.MaxValue)(unsafeEvidence)
      // Use `doubleEOF` so a real `Double.MaxValue` outer element is flat-mapped
      // rather than mistaken for end-of-stream (BUG-004).
      if (doubleEOF(source, v, Double.MaxValue)) false
      else { inner = compileInner(f.asInstanceOf[Double => AnyRef](v)); true }
    }
    protected def closeSource(): Unit = source.close()
    protected def resetSource(): Unit = source.reset()
  }

  /** FlatMap reader for Float-specialized sources. */
  private[streams] final class FlatMappedFloat(
    val source: Reader[_],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readFloat(Double.MaxValue)(unsafeEvidence)
      if (v == Double.MaxValue) false
      else { inner = compileInner(f.asInstanceOf[Float => AnyRef](v.toFloat)); true }
    }
    protected def closeSource(): Unit = source.close()
    protected def resetSource(): Unit = source.reset()
  }

  /** FlatMap reader for Int-specialized sources. */
  private[streams] final class FlatMappedInt(
    val source: Reader[_],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readInt(Long.MinValue)(unsafeEvidence)
      if (v == Long.MinValue) false
      else { inner = compileInner(f.asInstanceOf[Int => AnyRef](v.toInt)); true }
    }
    protected def closeSource(): Unit = source.close()
    protected def resetSource(): Unit = source.reset()
  }

  /** FlatMap reader for Long-specialized sources. */
  private[streams] final class FlatMappedLong(
    val source: Reader[_],
    f: AnyRef,
    compileInner: AnyRef => Reader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readLong(Long.MaxValue)(unsafeEvidence)
      // Use `longEOF` so a real `Long.MaxValue` outer element is flat-mapped
      // rather than mistaken for end-of-stream (BUG-004).
      if (longEOF(source, v, Long.MaxValue)) false
      else { inner = compileInner(f.asInstanceOf[Long => AnyRef](v)); true }
    }
    protected def closeSource(): Unit = source.close()
    protected def resetSource(): Unit = source.reset()
  }

  /** FlatMap reader for reference-type (AnyRef) sources. */
  private[streams] final class FlatMappedRef(
    val source: Reader[_],
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
    protected def resetSource(): Unit = source.reset()
  }

  /** Generic (boxed) chunk-backed reader for reference-type elements. */
  private[streams] final class FromChunk[A](chunk: Chunk[A]) extends Reader[A] {
    private val originalLen: Int     = chunk.length
    private var startIdx: Int        = 0
    private var effectiveLen: Int    = originalLen
    private var idx: Int             = 0
    def isClosed: Boolean            = idx >= effectiveLen
    override def readable(): Boolean = idx < effectiveLen
    // setSkip/setLimit compose as INCREMENTAL window operations over the live
    // window [startIdx, effectiveLen): `drop` advances the left edge, `take`
    // narrows the right edge, applied in call order. This makes take(n).drop(m)
    // (positions [m, n)) differ correctly from drop(m).take(n) (positions
    // [m, m+n)) rather than collapsing them (BUG-B). `startIdx` is the post-drop
    // baseline that `reset()` restores to.
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithin(startIdx, n, effectiveLen)
      idx = startIdx
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithin(startIdx, n, effectiveLen)
      if (idx > effectiveLen) idx = effectiveLen
      true
    }
    override def skip(n: Long): Unit    = idx = Reader.advanceWithin(idx, n, effectiveLen)
    def read[A1 >: A](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk(i).asInstanceOf[A1] }
      else sentinel
    override def readN[A1 >: A](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readByte(): Int =
      if (idx < effectiveLen) { val i = idx; idx += 1; Reader.anyToLowByte(chunk(i)) }
      else -1
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = idx = startIdx
  }

  /** Specialized FromChunk for Double elements — zero-boxing via readDouble. */
  private[streams] final class FromChunkDouble(chunk: Chunk[Double]) extends Reader[Double] {
    override def jvmType: JvmType    = JvmType.Double
    private val originalLen: Int     = chunk.length
    private var startIdx: Int        = 0
    private var effectiveLen: Int    = originalLen
    private var idx: Int             = 0
    def isClosed: Boolean            = idx >= effectiveLen
    override def readable(): Boolean = idx < effectiveLen
    // setSkip/setLimit compose as INCREMENTAL window operations over the live
    // window [startIdx, effectiveLen): `drop` advances the left edge, `take`
    // narrows the right edge, applied in call order. This makes take(n).drop(m)
    // (positions [m, n)) differ correctly from drop(m).take(n) (positions
    // [m, m+n)) rather than collapsing them (BUG-B). `startIdx` is the post-drop
    // baseline that `reset()` restores to.
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithin(startIdx, n, effectiveLen)
      idx = startIdx
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithin(startIdx, n, effectiveLen)
      if (idx > effectiveLen) idx = effectiveLen
      true
    }
    override def skip(n: Long): Unit         = idx = Reader.advanceWithin(idx, n, effectiveLen)
    def read[A1 >: Double](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Double.box(chunk.double(i)).asInstanceOf[A1] }
      else sentinel
    override def readN[A1 >: Double](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readUpToN[A1 >: Double](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    // EOF is decided structurally by `idx`, so a real `Double.MaxValue` element
    // is never confused with the sentinel; the flag merely records which path
    // was taken so consumers using `doubleEOF` can disambiguate (BUG-004).
    override def readDouble(sentinel: Double)(implicit ev: Double <:< Double): Double =
      if (idx < effectiveLen) { val v = chunk.double(idx); idx += 1; markReadValue(); v }
      else { markReadEOF(); sentinel }
    override def readByte(): Int = {
      val v = readDouble(Double.MaxValue)(unsafeEvidence)
      if (doubleEOF(this, v, Double.MaxValue)) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = idx = startIdx
  }

  /** Specialized FromChunk for Float elements — zero-boxing via readFloat. */
  private[streams] final class FromChunkFloat(chunk: Chunk[Float]) extends Reader[Float] {
    override def jvmType: JvmType    = JvmType.Float
    private val originalLen: Int     = chunk.length
    private var startIdx: Int        = 0
    private var effectiveLen: Int    = originalLen
    private var idx: Int             = 0
    def isClosed: Boolean            = idx >= effectiveLen
    override def readable(): Boolean = idx < effectiveLen
    // setSkip/setLimit compose as INCREMENTAL window operations over the live
    // window [startIdx, effectiveLen): `drop` advances the left edge, `take`
    // narrows the right edge, applied in call order. This makes take(n).drop(m)
    // (positions [m, n)) differ correctly from drop(m).take(n) (positions
    // [m, m+n)) rather than collapsing them (BUG-B). `startIdx` is the post-drop
    // baseline that `reset()` restores to.
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithin(startIdx, n, effectiveLen)
      idx = startIdx
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithin(startIdx, n, effectiveLen)
      if (idx > effectiveLen) idx = effectiveLen
      true
    }
    override def skip(n: Long): Unit        = idx = Reader.advanceWithin(idx, n, effectiveLen)
    def read[A1 >: Float](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Float.box(chunk.float(i)).asInstanceOf[A1] }
      else sentinel
    override def readN[A1 >: Float](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readUpToN[A1 >: Float](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readFloat(sentinel: Double)(implicit ev: Float <:< Float): Double =
      if (idx < effectiveLen) { val v = chunk.float(idx); idx += 1; v.toDouble }
      else sentinel
    override def readByte(): Int = {
      val v = readFloat(Double.MaxValue)(unsafeEvidence)
      if (v == Double.MaxValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = idx = startIdx
  }

  /** Specialized FromChunk for Byte elements — zero-boxing via readInt. */
  private[streams] final class FromChunkByte(chunk: Chunk[Byte]) extends Reader[Byte] {
    override def jvmType: JvmType    = JvmType.Byte
    private val originalLen: Int     = chunk.length
    private var startIdx: Int        = 0
    private var effectiveLen: Int    = originalLen
    private var idx: Int             = 0
    def isClosed: Boolean            = idx >= effectiveLen
    override def readable(): Boolean = idx < effectiveLen
    // setSkip/setLimit compose as INCREMENTAL window operations over the live
    // window [startIdx, effectiveLen): `drop` advances the left edge, `take`
    // narrows the right edge, applied in call order. This makes take(n).drop(m)
    // (positions [m, n)) differ correctly from drop(m).take(n) (positions
    // [m, m+n)) rather than collapsing them (BUG-B). `startIdx` is the post-drop
    // baseline that `reset()` restores to.
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithin(startIdx, n, effectiveLen)
      idx = startIdx
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithin(startIdx, n, effectiveLen)
      if (idx > effectiveLen) idx = effectiveLen
      true
    }
    override def skip(n: Long): Unit       = idx = Reader.advanceWithin(idx, n, effectiveLen)
    def read[A1 >: Byte](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Byte.box(chunk.byte(i)).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(implicit ev: Byte <:< Int): Long =
      if (idx < effectiveLen) { val v = chunk.byte(idx); idx += 1; v.toLong }
      else sentinel
    override def readByte(): Int =
      if (idx < effectiveLen) { val v = chunk.byte(idx); idx += 1; v.toInt & 0xff }
      else -1
    override def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Byte): Int = {
      if (len == 0) return 0
      val avail = effectiveLen - idx
      if (avail <= 0) return -1
      val n = math.min(len, avail)
      var i = 0
      while (i < n) { buf(offset + i) = chunk.byte(idx); idx += 1; i += 1 }
      n
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = idx = startIdx
  }

  /** Specialized FromChunk for Int elements — zero-boxing via readInt. */
  private[streams] final class FromChunkInt(chunk: Chunk[Int]) extends Reader[Int] {
    override def jvmType: JvmType    = JvmType.Int
    private val originalLen: Int     = chunk.length
    private var startIdx: Int        = 0
    private var effectiveLen: Int    = originalLen
    private var idx: Int             = 0
    def isClosed: Boolean            = idx >= effectiveLen
    override def readable(): Boolean = idx < effectiveLen
    // setSkip/setLimit compose as INCREMENTAL window operations over the live
    // window [startIdx, effectiveLen): `drop` advances the left edge, `take`
    // narrows the right edge, applied in call order. This makes take(n).drop(m)
    // (positions [m, n)) differ correctly from drop(m).take(n) (positions
    // [m, m+n)) rather than collapsing them (BUG-B). `startIdx` is the post-drop
    // baseline that `reset()` restores to.
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithin(startIdx, n, effectiveLen)
      idx = startIdx
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithin(startIdx, n, effectiveLen)
      if (idx > effectiveLen) idx = effectiveLen
      true
    }
    override def skip(n: Long): Unit      = idx = Reader.advanceWithin(idx, n, effectiveLen)
    def read[A1 >: Int](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Int.box(chunk.int(i)).asInstanceOf[A1] }
      else sentinel
    override def readN[A1 >: Int](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readUpToN[A1 >: Int](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      if (idx < effectiveLen) { val v = chunk.int(idx); idx += 1; v.toLong }
      else sentinel
    override def readByte(): Int = {
      val v = readInt(Long.MinValue)(unsafeEvidence)
      if (v == Long.MinValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = idx = startIdx
  }

  /** Specialized FromChunk for Long elements — zero-boxing via readLong. */
  private[streams] final class FromChunkLong(chunk: Chunk[Long]) extends Reader[Long] {
    override def jvmType: JvmType    = JvmType.Long
    private val originalLen: Int     = chunk.length
    private var startIdx: Int        = 0
    private var effectiveLen: Int    = originalLen
    private var idx: Int             = 0
    def isClosed: Boolean            = idx >= effectiveLen
    override def readable(): Boolean = idx < effectiveLen
    // setSkip/setLimit compose as INCREMENTAL window operations over the live
    // window [startIdx, effectiveLen): `drop` advances the left edge, `take`
    // narrows the right edge, applied in call order. This makes take(n).drop(m)
    // (positions [m, n)) differ correctly from drop(m).take(n) (positions
    // [m, m+n)) rather than collapsing them (BUG-B). `startIdx` is the post-drop
    // baseline that `reset()` restores to.
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithin(startIdx, n, effectiveLen)
      idx = startIdx
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithin(startIdx, n, effectiveLen)
      if (idx > effectiveLen) idx = effectiveLen
      true
    }
    override def skip(n: Long): Unit       = idx = Reader.advanceWithin(idx, n, effectiveLen)
    def read[A1 >: Long](sentinel: A1): A1 =
      if (idx < effectiveLen) { val i = idx; idx += 1; Long.box(chunk.long(i)).asInstanceOf[A1] }
      else sentinel
    override def readN[A1 >: Long](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    override def readUpToN[A1 >: Long](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val from  = idx
      val until = math.min(idx + n, effectiveLen)
      idx = until
      chunk.slice(from, until).asInstanceOf[Chunk[A1]]
    }
    // EOF is decided structurally by `idx`, so a real `Long.MaxValue` element is
    // never confused with the sentinel; the flag merely records which path was
    // taken so consumers using `longEOF` can disambiguate (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Long <:< Long): Long =
      if (idx < effectiveLen) { val v = chunk.long(idx); idx += 1; markReadValue(); v }
      else { markReadEOF(); sentinel }
    override def readByte(): Int = {
      val v = readLong(Long.MaxValue)(unsafeEvidence)
      if (longEOF(this, v, Long.MaxValue)) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = idx = startIdx
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
      if (!exhausted && iter.hasNext) Reader.anyToLowByte(iter.next()) else -1
    def close(): Unit          = exhausted = true
    override def reset(): Unit = { iter = iterable.iterator; exhausted = false }
  }

  /** Int-specialized reader backed by a Scala `Range`. */
  private[streams] final class FromRange(range: Range) extends Reader[Int] {
    override def jvmType: JvmType    = JvmType.Int
    private val rangeStep: Int       = range.step
    private val originalLen: Int     = range.length
    private var startIdx: Int        = 0
    private var effectiveLen: Int    = originalLen
    private var idx: Int             = 0
    private var current: Int         = range.start
    def isClosed: Boolean            = idx >= effectiveLen
    override def readable(): Boolean = idx < effectiveLen
    // Like the chunk readers, `setSkip`/`setLimit` compose as incremental window
    // operations over [startIdx, effectiveLen) so take/drop order is preserved
    // (BUG-B). `current` is kept consistent with `idx` on every structural move.
    private def setIdx(i: Int): Unit       = { idx = i; current = range.start + i * rangeStep }
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithin(startIdx, n, effectiveLen)
      setIdx(startIdx)
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithin(startIdx, n, effectiveLen)
      if (idx > effectiveLen) setIdx(effectiveLen)
      true
    }
    override def skip(n: Long): Unit      = setIdx(Reader.advanceWithin(idx, n, effectiveLen))
    def read[A1 >: Int](sentinel: A1): A1 =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; Int.box(v).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; v.toLong }
      else sentinel
    override def readByte(): Int =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; (v & 0xff) }
      else -1
    override def readN[A1 >: Int](n: Int): Chunk[A1] = {
      if (n <= 0 || idx >= effectiveLen) return Chunk.empty
      val count = math.min(n, effectiveLen - idx)
      val arr   = new Array[Int](count)
      var i     = 0
      while (i < count) { arr(i) = current; current += rangeStep; i += 1 }
      idx += count
      Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = setIdx(startIdx)
  }

  /**
   * Lazy reader over `[from, until)` (step 1) backing [[Reader.range]]. Mirrors
   * [[FromRange]] but tracks the window in `Long` element units so an interval
   * larger than `Int.MaxValue` elements is representable and consumed lazily
   * (BUG-N3). Element values stay in `Int` (every member of `[from, until)` is
   * a valid `Int`).
   */
  private[streams] final class FromIntRange(from: Int, until: Int) extends Reader[Int] {
    override def jvmType: JvmType = JvmType.Int
    private val originalLen: Long = if (until > from) until.toLong - from.toLong else 0L
    // `setSkip`/`setLimit` compose as incremental window ops over the count range
    // [startIdx, effectiveLen) so take/drop order is preserved (BUG-B), using
    // `Long` arithmetic for >Int.MaxValue spans (BUG-N3). These are only touched
    // by the (cold) window setters.
    private var startIdx: Long     = 0L
    private var effectiveLen: Long = originalLen
    // Hot-path cursors: emit `current` while `current < limitVal`, using the
    // element VALUE itself as the loop counter. This keeps the per-element bounds
    // check in `Int`, which the JIT can fuse with a downstream value predicate to
    // eliminate bounds checks / unroll the loop — a large win for `takeWhile`
    // (a `Long` counter here blocked that fusion, halving throughput). It is also
    // overflow-safe for the full-width range: `current` and `limitVal` never
    // leave `[from, until]`, both `Int`, even when the element COUNT exceeds
    // `Int.MaxValue` (since `from + effectiveLen <= until`).
    private var current: Int               = from
    private var limitVal: Int              = (from.toLong + effectiveLen).toInt
    def isClosed: Boolean                  = current >= limitVal
    override def readable(): Boolean       = current < limitVal
    private def setIdx(i: Long): Unit      = current = (from.toLong + i).toInt
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithinL(startIdx, n, effectiveLen)
      setIdx(startIdx)
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithinL(startIdx, n, effectiveLen)
      limitVal = (from.toLong + effectiveLen).toInt
      if (current > limitVal) current = limitVal
      true
    }
    override def skip(n: Long): Unit =
      setIdx(Reader.advanceWithinL(current.toLong - from.toLong, n, effectiveLen))
    def read[A1 >: Int](sentinel: A1): A1 =
      if (current < limitVal) { val v = current; current += 1; Int.box(v).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      if (current < limitVal) { val v = current; current += 1; v.toLong }
      else sentinel
    // Bulk terminal fold: the whole remaining window in one local counted
    // loop — no per-element virtual call or field round-trip. The cursor is
    // committed in `finally` so an exception from `f` leaves `current` exactly
    // where the per-element pull loop would have (one past the consumed
    // element).
    override def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: Int <:< Int): Long = {
      var acc = z
      var c   = current
      val lim = limitVal
      try while (c < lim) { val v = c; c += 1; acc = f(acc, v) }
      finally current = c
      acc
    }
    override def readByte(): Int =
      if (current < limitVal) { val v = current; current += 1; (v & 0xff) }
      else -1
    def close(): Unit          = current = limitVal
    override def reset(): Unit = setIdx(startIdx)
  }

  /** Reader adapter for `java.io.InputStream`. Emits elements as `Byte`. */
  private[streams] final class InputStreamReader(is: InputStream) extends Reader[Byte] {

    private var closed               = false
    private var errored: IOException = null

    def isClosed: Boolean = closed

    override def readable(): Boolean =
      !closed && (try { is.available() > 0 }
      catch { case _: IOException => false })

    override def skip(n: Long): Unit = {
      var r = n; while (r > 0) { val b = readByte(); if (b < 0) r = 0 else r -= 1 }
    }

    override def jvmType: JvmType = JvmType.Byte

    override def readByte(): Int = {
      if (closed) return -1
      if (errored ne null) throw new StreamError(errored)
      try {
        val b = is.read()
        if (b < 0) { closed = true; -1 }
        else b
      } catch {
        case e: IOException => closed = true; errored = e; throw new StreamError(e)
      }
    }

    def read[A1 >: Byte](sentinel: A1): A1 = {
      val b = readByte()
      if (b >= 0) Byte.box(b.toByte).asInstanceOf[A1] else sentinel
    }

    override def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Byte): Int =
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

    override def readN[A1 >: Byte](n: Int): Chunk[A1] = {
      if (n <= 0 || closed) return Chunk.empty
      if (errored ne null) throw new StreamError(errored)
      if (n <= 8192) {
        val arr   = new Array[Byte](n)
        var total = 0
        while (total < n && !closed) {
          val read = readBytes(arr, total, n - total)(unsafeEvidence)
          if (read < 0) ()
          else total += read
        }
        if (total == 0) Chunk.empty
        else if (total == n) Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
        else Chunk.fromArray(java.util.Arrays.copyOf(arr, total)).asInstanceOf[Chunk[A1]]
      } else {
        val b   = new ChunkBuilder.Byte()
        val buf = new Array[Byte](8192)
        var rem = n
        while (rem > 0 && !closed) {
          val toRead = math.min(rem, 8192)
          val read   = readBytes(buf, 0, toRead)
          if (read > 0) {
            var k = 0
            while (k < read) { b.addOne(buf(k)); k += 1 }
            rem -= read
          }
        }
        b.result().asInstanceOf[Chunk[A1]]
      }
    }

    def close(): Unit = closed = true
  }

  /**
   * Wraps a Double-specialized source reader and applies a single erased map
   * function.
   */
  private[streams] final class MappedDouble(
    val source: Reader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      // Box Boolean AND Byte through the ref lane. `Function1` is not specialized
      // on a `Byte` return, so the `f.asInstanceOf[X => Int]` fast path in
      // `readInt` (reached for a `Byte` reader via base `readByte`) would invoke
      // `apply$mc?I$sp` -> `unboxToInt(java.lang.Byte)` -> ClassCastException.
      // Reporting `AnyRef` routes `Byte` output through the boxed `read` path,
      // which is correct (BUG-N1). Short/Char are safe (base readShort/readChar
      // already drain via boxed `read`).
      if ((outType eq JvmType.Boolean) || (outType eq JvmType.Byte)) JvmType.AnyRef else outType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = source.readDouble(Double.MaxValue)(unsafeEvidence);
      if (doubleEOF(source, v, Double.MaxValue)) sentinel
      else f.asInstanceOf[Double => Int](v).toLong
    }
    // A mapped output can equal the sentinel independently of the source, so
    // this reader sets its OWN flag from the source's EOF (it cannot delegate to
    // `source`, whose lane may differ from this reader's output lane). BUG-004.
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      val v = source.readDouble(Double.MaxValue)(unsafeEvidence);
      if (doubleEOF(source, v, Double.MaxValue)) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Double => Long](v) }
    }
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = source.readDouble(Double.MaxValue)(unsafeEvidence);
      if (doubleEOF(source, v, Double.MaxValue)) sentinel
      else f.asInstanceOf[Double => Float](v).toDouble
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      val v = source.readDouble(sentinel)(unsafeEvidence);
      if (doubleEOF(source, v, sentinel)) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Double => Double](v) }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readDouble(Double.MaxValue)(unsafeEvidence);
      if (doubleEOF(source, v, Double.MaxValue)) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Double => Boolean](v)).asInstanceOf[A1]
      } else f.asInstanceOf[Double => AnyRef](v).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readInt(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readInt(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val s = Long.MaxValue
        var v = readLong(s)(unsafeEvidence)
        if (longEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!longEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readLong(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloat(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloat(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readDouble(s)(unsafeEvidence)
        if (doubleEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!doubleEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readDouble(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else {
        val b = ChunkBuilder.make[A1](math.min(n, 64))
        var v = read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
        var i = 0
        while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
          b += v.asInstanceOf[A1]; i += 1
          if (i < n) v = read[Any](EndOfStream)
        }
        b.result()
      }
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n;
      while (r > 0 && !doubleEOF(source, source.readDouble(s)(unsafeEvidence), s)) r -= 1
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
    val source: Reader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      // Box Boolean AND Byte through the ref lane. `Function1` is not specialized
      // on a `Byte` return, so the `f.asInstanceOf[X => Int]` fast path in
      // `readInt` (reached for a `Byte` reader via base `readByte`) would invoke
      // `apply$mc?I$sp` -> `unboxToInt(java.lang.Byte)` -> ClassCastException.
      // Reporting `AnyRef` routes `Byte` output through the boxed `read` path,
      // which is correct (BUG-N1). Short/Char are safe (base readShort/readChar
      // already drain via boxed `read`).
      if ((outType eq JvmType.Boolean) || (outType eq JvmType.Byte)) JvmType.AnyRef else outType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = source.readFloat(Double.MaxValue)(unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Float => Int](v.toFloat).toLong
    }
    // Float source EOF is sentinel-safe, but a mapped Long/Double output can
    // equal the consumer's sentinel, so set this reader's own flag (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      val v = source.readFloat(Double.MaxValue)(unsafeEvidence);
      if (v == Double.MaxValue) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Float => Long](v.toFloat) }
    }
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = source.readFloat(sentinel)(unsafeEvidence);
      if (v == sentinel) sentinel
      else f.asInstanceOf[Float => Float](v.toFloat).toDouble
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      val v = source.readFloat(Double.MaxValue)(unsafeEvidence);
      if (v == Double.MaxValue) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Float => Double](v.toFloat) }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readFloat(Double.MaxValue)(unsafeEvidence);
      if (v == Double.MaxValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Float => Boolean](v.toFloat)).asInstanceOf[A1]
      } else f.asInstanceOf[Float => AnyRef](v.toFloat).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readInt(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readInt(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val s = Long.MaxValue
        var v = readLong(s)(unsafeEvidence)
        if (longEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!longEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readLong(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloat(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloat(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readDouble(s)(unsafeEvidence)
        if (doubleEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!doubleEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readDouble(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else {
        val b = ChunkBuilder.make[A1](math.min(n, 64))
        var v = read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
        var i = 0
        while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
          b += v.asInstanceOf[A1]; i += 1
          if (i < n) v = read[Any](EndOfStream)
        }
        b.result()
      }
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && source.readFloat(s)(unsafeEvidence) != s) r -= 1
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
    val source: Reader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      // Box Boolean AND Byte through the ref lane. `Function1` is not specialized
      // on a `Byte` return, so the `f.asInstanceOf[X => Int]` fast path in
      // `readInt` (reached for a `Byte` reader via base `readByte`) would invoke
      // `apply$mc?I$sp` -> `unboxToInt(java.lang.Byte)` -> ClassCastException.
      // Reporting `AnyRef` routes `Byte` output through the boxed `read` path,
      // which is correct (BUG-N1). Short/Char are safe (base readShort/readChar
      // already drain via boxed `read`).
      if ((outType eq JvmType.Boolean) || (outType eq JvmType.Byte)) JvmType.AnyRef else outType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = source.readInt(sentinel)(unsafeEvidence);
      if (v == sentinel) sentinel
      else f.asInstanceOf[Int => Int](v.toInt).toLong
    }
    // Bulk terminal fold: compose the map into the fold step and delegate to
    // the source, collapsing this stage onto the leaf loop. Only reached when
    // `jvmType eq Int` (i.e. `outType eq Int`, so `f` is `Int => Int` — the
    // same precondition `readInt` relies on; Boolean/Byte outputs report
    // `AnyRef` and never take this path).
    override def foldInt(z: Long, fold: (Long, Int) => Long)(implicit ev: Any <:< Int): Long =
      source.foldInt(z, new FoldThroughIntMap(f, fold))(unsafeEvidence)
    // Int source EOF is sentinel-safe, but a mapped Long/Double output can equal
    // the consumer's sentinel, so set this reader's own flag (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      val v = source.readInt(Long.MinValue)(unsafeEvidence);
      if (v == Long.MinValue) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Int => Long](v.toInt) }
    }
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = source.readInt(Long.MinValue)(unsafeEvidence);
      if (v == Long.MinValue) sentinel
      else f.asInstanceOf[Int => Float](v.toInt).toDouble
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      val v = source.readInt(Long.MinValue)(unsafeEvidence);
      if (v == Long.MinValue) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Int => Double](v.toInt) }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readInt(Long.MinValue)(unsafeEvidence);
      if (v == Long.MinValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Int => Boolean](v.toInt)).asInstanceOf[A1]
      } else f.asInstanceOf[Int => AnyRef](v.toInt).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readInt(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readInt(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val s = Long.MaxValue
        var v = readLong(s)(unsafeEvidence)
        if (longEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!longEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readLong(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloat(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloat(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readDouble(s)(unsafeEvidence)
        if (doubleEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!doubleEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readDouble(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else {
        val b = ChunkBuilder.make[A1](math.min(n, 64))
        var v = read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
        var i = 0
        while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
          b += v.asInstanceOf[A1]; i += 1
          if (i < n) v = read[Any](EndOfStream)
        }
        b.result()
      }
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MinValue; var r = n; while (r > 0 && source.readInt(s)(unsafeEvidence) != s) r -= 1
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
    val source: Reader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      // Box Boolean AND Byte through the ref lane. `Function1` is not specialized
      // on a `Byte` return, so the `f.asInstanceOf[X => Int]` fast path in
      // `readInt` (reached for a `Byte` reader via base `readByte`) would invoke
      // `apply$mc?I$sp` -> `unboxToInt(java.lang.Byte)` -> ClassCastException.
      // Reporting `AnyRef` routes `Byte` output through the boxed `read` path,
      // which is correct (BUG-N1). Short/Char are safe (base readShort/readChar
      // already drain via boxed `read`).
      if ((outType eq JvmType.Boolean) || (outType eq JvmType.Byte)) JvmType.AnyRef else outType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = source.readLong(Long.MaxValue)(unsafeEvidence);
      if (longEOF(source, v, Long.MaxValue)) sentinel
      else f.asInstanceOf[Long => Int](v).toLong
    }
    // A mapped output can equal the sentinel independently of the source, so
    // this reader sets its OWN flag from the Long source's EOF (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      val v = source.readLong(sentinel)(unsafeEvidence);
      if (longEOF(source, v, sentinel)) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Long => Long](v) }
    }
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = source.readLong(Long.MaxValue)(unsafeEvidence);
      if (longEOF(source, v, Long.MaxValue)) sentinel
      else f.asInstanceOf[Long => Float](v).toDouble
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      val v = source.readLong(Long.MaxValue)(unsafeEvidence);
      if (longEOF(source, v, Long.MaxValue)) { markReadEOF(); sentinel }
      else { markReadValue(); f.asInstanceOf[Long => Double](v) }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readLong(Long.MaxValue)(unsafeEvidence);
      if (longEOF(source, v, Long.MaxValue)) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Long => Boolean](v)).asInstanceOf[A1]
      } else f.asInstanceOf[Long => AnyRef](v).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readInt(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readInt(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val s = Long.MaxValue
        var v = readLong(s)(unsafeEvidence)
        if (longEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!longEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readLong(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloat(s)(unsafeEvidence)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloat(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readDouble(s)(unsafeEvidence)
        if (doubleEOF(this, v, s)) return Chunk.empty
        var i = 0
        while (!doubleEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readDouble(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else {
        val b = ChunkBuilder.make[A1](math.min(n, 64))
        var v = read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
        var i = 0
        while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
          b += v.asInstanceOf[A1]; i += 1
          if (i < n) v = read[Any](EndOfStream)
        }
        b.result()
      }
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Long.MaxValue; var r = n; while (r > 0 && !longEOF(source, source.readLong(s)(unsafeEvidence), s)) r -= 1
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
    val source: Reader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends Reader[Any]
      with WrappedReader {
    override def jvmType: JvmType =
      // Box Boolean AND Byte through the ref lane. `Function1` is not specialized
      // on a `Byte` return, so the `f.asInstanceOf[X => Int]` fast path in
      // `readInt` (reached for a `Byte` reader via base `readByte`) would invoke
      // `apply$mc?I$sp` -> `unboxToInt(java.lang.Byte)` -> ClassCastException.
      // Reporting `AnyRef` routes `Byte` output through the boxed `read` path,
      // which is correct (BUG-N1). Short/Char are safe (base readShort/readChar
      // already drain via boxed `read`).
      if ((outType eq JvmType.Boolean) || (outType eq JvmType.Byte)) JvmType.AnyRef else outType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else if (outType eq JvmType.Int)
        f.asInstanceOf[AnyRef => Int](v.asInstanceOf[AnyRef]).toLong
      else
        // Byte / Short / Char / other numeric outputs: extract via Number.
        f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef]).asInstanceOf[java.lang.Number].longValue()
    }
    // Source EOF is the unambiguous `EndOfStream` object, but a mapped
    // Long/Double output can equal the consumer's sentinel, so set this reader's
    // own flag (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
      else {
        markReadValue()
        if (outType eq JvmType.Long)
          f.asInstanceOf[AnyRef => Long](v.asInstanceOf[AnyRef])
        else
          f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef]).asInstanceOf[java.lang.Number].longValue()
      }
    }
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else if (outType eq JvmType.Float)
        f.asInstanceOf[AnyRef => Float](v.asInstanceOf[AnyRef]).toDouble
      else
        f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef]).asInstanceOf[java.lang.Number].doubleValue()
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
      else {
        markReadValue()
        if (outType eq JvmType.Double)
          f.asInstanceOf[AnyRef => Double](v.asInstanceOf[AnyRef])
        else
          f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef]).asInstanceOf[java.lang.Number].doubleValue()
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[AnyRef => Boolean](v.asInstanceOf[AnyRef])).asInstanceOf[A1]
      } else f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef]).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = ChunkBuilder.make[A1](math.min(n, 64))
      var v = read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
      var i = 0
      while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
        b += v.asInstanceOf[A1]; i += 1
        if (i < n) v = read[Any](EndOfStream)
      }
      b.result()
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
    override def jvmType: JvmType = inner.jvmType
    private var done: Boolean     = false
    // Whether the CURRENT cycle has emitted at least one element. Replay is
    // gated on this, NOT on `inner.isClosed`:
    //   - `inner.isClosed` is wrong as an exhaustion signal: an `Interpreter`
    //     reports `isClosed == false` at natural EOF (only an explicit `close()`
    //     sets it), so a repeated interpreter-compiled stream used to stop after
    //     the first cycle in the primitive lanes (ITER-3a).
    //   - gating on `emittedInCycle` also fixes the `empty.repeated` livelock:
    //     a cycle that emits nothing must NOT replay (it would spin forever:
    //     EOF -> reset -> EOF -> ...), so a clean zero-element cycle completes
    //     and `empty.repeated == empty`.
    private var emittedInCycle: Boolean = false
    def isClosed: Boolean               = done
    def read[A1 >: A](sentinel: A1): A1 = {
      if (done) return sentinel
      try {
        while (true) {
          val v = inner.read[Any](EndOfStream)
          if (v.asInstanceOf[AnyRef] ne EndOfStream) { emittedInCycle = true; return v.asInstanceOf[A1] }
          if (!emittedInCycle) { done = true; return sentinel }
          inner.reset(); emittedInCycle = false
        }
        sentinel // unreachable
      } catch {
        case e: Throwable => done = true; throw e
      }
    }
    // Like `read()`, every specialized lane latches `done` on an inner throw
    // so the cycle machinery does not resume the failed inner reader on a
    // subsequent pull (BUG-R8-05). Exception-table only: zero cost on the
    // non-throwing path.
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
      if (done) return sentinel
      try {
        while (true) {
          val v = inner.readInt(sentinel)(unsafeEvidence)
          if (v != sentinel) { emittedInCycle = true; return v }
          if (!emittedInCycle) { done = true; return sentinel }
          inner.reset(); emittedInCycle = false
        }
        sentinel // unreachable
      } catch {
        case e: Throwable => done = true; throw e
      }
    }
    // Use `longEOF`/`doubleEOF` (not raw `!= sentinel`) so a real element equal
    // to the sentinel is not mistaken for inner exhaustion (which would trigger
    // a spurious repeat). This reader also emits `inner`'s value unchanged, so
    // it maintains its own EOF flag for downstream `longEOF`/`doubleEOF`
    // consumers (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long = {
      if (done) { markReadEOF(); return sentinel }
      try {
        while (true) {
          val v = inner.readLong(sentinel)(unsafeEvidence)
          if (!longEOF(inner, v, sentinel)) { emittedInCycle = true; markReadValue(); return v }
          if (!emittedInCycle) { done = true; markReadEOF(); return sentinel }
          inner.reset(); emittedInCycle = false
        }
        markReadEOF(); sentinel // unreachable
      } catch {
        case e: Throwable => done = true; throw e
      }
    }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
      if (done) return sentinel
      try {
        while (true) {
          val v = inner.readFloat(sentinel)(unsafeEvidence)
          if (v != sentinel) { emittedInCycle = true; return v }
          if (!emittedInCycle) { done = true; return sentinel }
          inner.reset(); emittedInCycle = false
        }
        sentinel // unreachable
      } catch {
        case e: Throwable => done = true; throw e
      }
    }
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
      if (done) { markReadEOF(); return sentinel }
      try {
        while (true) {
          val v = inner.readDouble(sentinel)(unsafeEvidence)
          if (!doubleEOF(inner, v, sentinel)) { emittedInCycle = true; markReadValue(); return v }
          if (!emittedInCycle) { done = true; markReadEOF(); return sentinel }
          inner.reset(); emittedInCycle = false
        }
        markReadEOF(); sentinel // unreachable
      } catch {
        case e: Throwable => done = true; throw e
      }
    }
    // Mirror the scalar paths: gate replay on `emittedInCycle`, NOT
    // `inner.isClosed` (an `Interpreter` reports `isClosed == false` at natural
    // EOF, so a repeated interpreter-compiled stream consumed via `readUpToN`
    // used to stop after the first cycle — ITER-3a). A zero-element cycle
    // completes (no `empty.repeated` livelock); a non-empty cycle replays.
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
      if (done || n <= 0) return Chunk.empty
      try {
        while (true) {
          val chunk = inner.readUpToN[A1](n)
          if (chunk.nonEmpty) { emittedInCycle = true; return chunk }
          if (!emittedInCycle) { done = true; return Chunk.empty }
          inner.reset(); emittedInCycle = false
        }
        Chunk.empty // unreachable
      } catch {
        case e: Throwable => done = true; throw e
      }
    }
    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)
    override def readByte(): Int     = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else Reader.anyToLowByte(v)
    }
    def close(): Unit          = { done = true; inner.close() }
    override def reset(): Unit = { inner.reset(); done = false; emittedInCycle = false }
  }

  /**
   * Generic singleton — for reference types. Mode flag: 0 = fresh, 1 = taken, 2 =
   * repeat forever.
   */
  private[streams] final class SingletonGeneric[A](value: A) extends Reader[A] {
    // Mode encoding: 0 = fresh, 1 = taken/closed, 2 = repeat, 3 = repeat-
    // closed. Bit 0 is the CLOSED bit, so close() works in repeat mode too
    // (read-after-close returns the sentinel — BUG-R9-02) while reset()
    // (clear bit 0) preserves repeat mode for `repeated` restarts. The
    // closed test stays a single comparison (mask instead of equality).
    private var mode: Int            = 0
    def isClosed: Boolean            = (mode & 1) == 1
    override def readable(): Boolean = (mode & 1) == 0
    override def skip(n: Long): Unit =
      if (mode == 2) () // infinite — skip is a no-op
      else if (n > 0 && mode == 0) mode = 1
    def read[A1 >: A](sentinel: A1): A1 = {
      val m = mode
      if ((m & 1) == 1) sentinel
      else { if (m == 0) mode = 1; value.asInstanceOf[A1] }
    }
    override def readByte(): Int = {
      val m = mode
      if ((m & 1) == 1) -1
      else { if (m == 0) mode = 1; Reader.anyToLowByte(value) }
    }
    def close(): Unit          = mode |= 1
    override def reset(): Unit = mode &= ~1

    override def setRepeat(): Boolean = { mode = 2; true }
  }

  /**
   * Specialized singleton for all primitives. Stores value as Long bits. `A` is
   * phantom — never stored, never accessed.
   *
   * `modeOrd` packs mode in bits [7:0] and JvmType ordinal in bits [15:8].
   * Mode: 0 = fresh, 1 = taken/closed, 2 = repeat forever, 3 = repeat-closed.
   * Bit 0 is the CLOSED bit, so close() works in repeat mode too
   * (read-after-close returns the sentinel — BUG-R9-02) while reset() (clear
   * bit 0) preserves repeat mode for `repeated` restarts.
   */
  private[streams] final class SingletonPrim[A] private[streams] (
    private val storedLong: Long,
    private var modeOrd: Int // bits [7:0] = mode (0=fresh, 1=taken, 2=repeat), bits [15:8] = ordinal
  ) extends Reader[A] {
    private def mode: Int             = modeOrd & 0xff
    private def ordinal: Int          = (modeOrd >>> 8) & 0xff
    private def setMode(m: Int): Unit = modeOrd = m | (modeOrd & ~0xff)

    override def jvmType: JvmType    = JvmType.fromOrdinal(ordinal)
    def isClosed: Boolean            = (mode & 1) == 1
    override def readable(): Boolean = (mode & 1) == 0

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
      if ((m & 1) == 1) sentinel
      else {
        if (m == 0) setMode(1)
        boxed().asInstanceOf[A1]
      }
    }

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
      val m = mode;
      if ((m & 1) == 1) sentinel
      else { if (m == 0) setMode(1); storedLong }
    }

    // Bulk terminal fold: one application for the one element. Repeat mode
    // (m == 2, infinite re-emission) defers to the generic pull loop.
    override def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: A <:< Int): Long = {
      val m = mode
      if ((m & 1) == 1) z
      else if (m == 0) { setMode(1); f(z, storedLong.toInt) }
      else super.foldInt(z, f)
    }

    // EOF is structural (mode == 1), but the singleton value itself may equal
    // the sentinel, so maintain the out-of-band flag for `longEOF`/`doubleEOF`
    // consumers (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long = {
      val m = mode;
      if ((m & 1) == 1) { markReadEOF(); sentinel }
      else { if (m == 0) setMode(1); markReadValue(); storedLong }
    }

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
      val m = mode;
      if ((m & 1) == 1) sentinel
      else { if (m == 0) setMode(1); java.lang.Float.intBitsToFloat(storedLong.toInt).toDouble }
    }

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
      val m = mode;
      if ((m & 1) == 1) { markReadEOF(); sentinel }
      else { if (m == 0) setMode(1); markReadValue(); java.lang.Double.longBitsToDouble(storedLong) }
    }

    override def readByte(): Int = {
      val m = mode;
      if ((m & 1) == 1) -1
      else { if (m == 0) setMode(1); storedLong.toInt & 0xff }
    }

    def close(): Unit          = setMode(mode | 1)
    override def reset(): Unit = setMode(mode & ~1)

    override def setRepeat(): Boolean = { setMode(2); true }
  }

  /**
   * Mutable, re-armable single-element primitive reader. Used exclusively by
   * `Stream.FlatMapped`'s per-element inner compilation: a
   * `flatMap(_ => Stream.succeed(prim))` otherwise allocates a fresh
   * [[SingletonPrim]] (plus stream-node scaffolding) per OUTER element; the
   * flatMap reader instead keeps one private instance and re-arms it after the
   * previous inner has been closed. Never user-visible (only constructed inside
   * `FlatMapped.compile`'s `compileInner`), so re-arming can never resurrect an
   * aliased external reader. Same value encoding as [[SingletonPrim]]: raw bits
   * in `storedLong`, JvmType ordinal in `modeOrd` bits [15:8], mode (0=fresh,
   * 1=taken) in bits [7:0]. No repeat mode: `setRepeat` keeps the default
   * `false` (the flatMap machinery never sets repeat on an inner).
   */
  private[streams] final class ReusableSingletonPrim extends Reader[Any] {
    private var storedLong: Long = 0L
    private var modeOrd: Int     = 1 // taken/closed until first armed

    /** Re-arms with a new value; only called after the previous use closed. */
    private[streams] def arm(prim: Long, ord: Int): Unit = {
      storedLong = prim
      modeOrd = ord << 8 // mode 0 (fresh)
    }

    private def mode: Int             = modeOrd & 0xff
    private def ordinal: Int          = (modeOrd >>> 8) & 0xff
    private def setMode(m: Int): Unit = modeOrd = m | (modeOrd & ~0xff)

    override def jvmType: JvmType    = JvmType.fromOrdinal(ordinal)
    def isClosed: Boolean            = (mode & 1) == 1
    override def readable(): Boolean = (mode & 1) == 0

    override def skip(n: Long): Unit = if (n > 0 && mode == 0) setMode(1)

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

    def read[A1 >: Any](sentinel: A1): A1 = {
      val m = mode
      if ((m & 1) == 1) sentinel
      else {
        setMode(1)
        boxed().asInstanceOf[A1]
      }
    }

    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val m = mode
      if ((m & 1) == 1) sentinel
      else { setMode(1); storedLong }
    }

    // Bulk terminal fold: one application for the one element (no repeat mode
    // on the reusable variant).
    override def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: Any <:< Int): Long = {
      val m = mode
      if ((m & 1) == 1) z
      else { setMode(1); f(z, storedLong.toInt) }
    }

    // EOF is structural (mode == 1), but the singleton value itself may equal
    // the sentinel, so maintain the out-of-band flag for `longEOF`/`doubleEOF`
    // consumers (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
      val m = mode
      if ((m & 1) == 1) { markReadEOF(); sentinel }
      else { setMode(1); markReadValue(); storedLong }
    }

    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val m = mode
      if ((m & 1) == 1) sentinel
      else { setMode(1); java.lang.Float.intBitsToFloat(storedLong.toInt).toDouble }
    }

    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
      val m = mode
      if ((m & 1) == 1) { markReadEOF(); sentinel }
      else { setMode(1); markReadValue(); java.lang.Double.longBitsToDouble(storedLong) }
    }

    override def readByte(): Int = {
      val m = mode
      if ((m & 1) == 1) -1
      else { setMode(1); storedLong.toInt & 0xff }
    }

    def close(): Unit          = setMode(mode | 1)
    override def reset(): Unit = setMode(mode & ~1)
  }

  /**
   * Mutable, re-armable integer-interval reader (the reusable counterpart of
   * [[FromIntRange]]). Used exclusively by `Stream.FlatMapped`'s per-element
   * inner compilation: a `flatMap(_ => Stream.range(a, b))` otherwise allocates
   * a fresh [[FromIntRange]] (plus stream-node scaffolding) per OUTER element.
   * Never user-visible (only constructed inside `FlatMapped.compile`'s
   * `compileInner`). Window/cursor semantics are identical to [[FromIntRange]]
   * — see the comments there.
   */
  private[streams] final class ReusableIntRange extends Reader[Int] {
    private var from: Int          = 0
    private var startIdx: Long     = 0L
    private var effectiveLen: Long = 0L
    private var current: Int       = 0
    private var limitVal: Int      = 0

    /** Re-arms over `[f, u)`; only called after the previous use closed. */
    private[streams] def arm(f: Int, u: Int): Unit = {
      from = f
      val len = if (u > f) u.toLong - f.toLong else 0L
      startIdx = 0L
      effectiveLen = len
      current = f
      limitVal = (f.toLong + len).toInt
    }

    override def jvmType: JvmType          = JvmType.Int
    def isClosed: Boolean                  = current >= limitVal
    override def readable(): Boolean       = current < limitVal
    private def setIdx(i: Long): Unit      = current = (from.toLong + i).toInt
    override def setSkip(n: Long): Boolean = {
      startIdx = Reader.advanceWithinL(startIdx, n, effectiveLen)
      setIdx(startIdx)
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = Reader.advanceWithinL(startIdx, n, effectiveLen)
      limitVal = (from.toLong + effectiveLen).toInt
      if (current > limitVal) current = limitVal
      true
    }
    override def skip(n: Long): Unit =
      setIdx(Reader.advanceWithinL(current.toLong - from.toLong, n, effectiveLen))
    def read[A1 >: Int](sentinel: A1): A1 =
      if (current < limitVal) { val v = current; current += 1; Int.box(v).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      if (current < limitVal) { val v = current; current += 1; v.toLong }
      else sentinel
    // Same bulk fold as `FromIntRange.foldInt` — see the comment there.
    override def foldInt(z: Long, f: (Long, Int) => Long)(implicit ev: Int <:< Int): Long = {
      var acc = z
      var c   = current
      val lim = limitVal
      try while (c < lim) { val v = c; c += 1; acc = f(acc, v) }
      finally current = c
      acc
    }
    override def readByte(): Int =
      if (current < limitVal) { val v = current; current += 1; (v & 0xff) }
      else -1
    def close(): Unit          = current = limitVal
    override def reset(): Unit = setIdx(startIdx)
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

    // The skip is deferred to the first pull: performing it eagerly in the
    // constructor means a skip failure (e.g. an I/O error while draining)
    // escapes compile() before run() has any reader to close — leaking the
    // source and bypassing `ensuring` finalizers (BUG-R5-01). This is the
    // non-native fallback wrapper, so the one-shot flag costs one predictable
    // branch per pull, never inside a primitive sentinel drain loop.
    private var pendingSkip: Boolean = skipN > 0

    private def applyPendingSkip(): Unit = {
      pendingSkip = false
      if (!inner.setSkip(skipN)) inner.skip(skipN)
    }

    override def jvmType: JvmType    = inner.jvmType
    def isClosed: Boolean            = remaining <= 0 || inner.isClosed
    override def readable(): Boolean = remaining > 0 && (pendingSkip || inner.readable())

    def read[A1 >: A](sentinel: A1): A1 = {
      if (remaining <= 0) return sentinel
      if (pendingSkip) applyPendingSkip()
      val v = inner.read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] ne EndOfStream) { remaining -= 1; v.asInstanceOf[A1] }
      else sentinel
    }

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
      if (remaining <= 0) return sentinel
      if (pendingSkip) applyPendingSkip()
      val v = inner.readInt(sentinel)(unsafeEvidence)
      if (v != sentinel) remaining -= 1
      v
    }

    // Detect inner EOF via `longEOF`/`doubleEOF` (not raw `!= sentinel`) so a
    // real sentinel-valued element still decrements `remaining`; set this
    // reader's own flag because it can also return the sentinel for its own
    // reason (`remaining <= 0`) when inner is not at EOF (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long = {
      if (remaining <= 0) { markReadEOF(); return sentinel }
      if (pendingSkip) applyPendingSkip()
      val v = inner.readLong(sentinel)(unsafeEvidence)
      if (!longEOF(inner, v, sentinel)) { remaining -= 1; markReadValue() }
      else markReadEOF()
      v
    }

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
      if (remaining <= 0) return sentinel
      if (pendingSkip) applyPendingSkip()
      val v = inner.readFloat(sentinel)(unsafeEvidence)
      if (v != sentinel) remaining -= 1
      v
    }

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
      if (remaining <= 0) { markReadEOF(); return sentinel }
      if (pendingSkip) applyPendingSkip()
      val v = inner.readDouble(sentinel)(unsafeEvidence)
      if (!doubleEOF(inner, v, sentinel)) { remaining -= 1; markReadValue() }
      else markReadEOF()
      v
    }

    override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
      if (remaining <= 0 || n <= 0) return Chunk.empty
      if (pendingSkip) applyPendingSkip()
      val toRead = math.min(n.toLong, remaining).toInt
      val chunk  = inner.readUpToN[A1](toRead)
      remaining -= chunk.length
      chunk
    }

    // Skip must route through THIS reader (not straight to `inner`) so the
    // skipped elements are charged against `remaining` — a `drop` fused after a
    // `take` consumes the take budget, i.e. take(n).drop(m) keeps [m, n), not
    // [m, m+n) (BUG-B). Capping at `remaining` avoids over-draining `inner`.
    override def skip(n: Long): Unit = {
      val toSkip = if (n <= 0L) 0L else math.min(n, remaining)
      if (toSkip > 0L) Reader.skipViaSentinel(this, toSkip)
    }
    override def readByte(): Int = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else Reader.anyToLowByte(v)
    }
    def close(): Unit          = inner.close()
    override def reset(): Unit = {
      inner.reset()
      remaining = limitN
      pendingSkip = skipN > 0
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
    override def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long =
      if (doneSent) sentinel
      else if (remaining <= 0) { doneSent = true; sentinel }
      else {
        val v = self.readInt(sentinel)(unsafeEvidence);
        if (v != sentinel) { remaining -= 1; v }
        else { doneSent = true; sentinel }
      }
    // Detect `self` EOF via `longEOF`/`doubleEOF` (not raw `!= sentinel`) and
    // maintain this reader's own flag, since it returns the sentinel for its own
    // reasons (`doneSent`, `remaining <= 0`) and passes through values that may
    // equal the sentinel (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long =
      if (doneSent) { markReadEOF(); sentinel }
      else if (remaining <= 0) { doneSent = true; markReadEOF(); sentinel }
      else {
        val v = self.readLong(sentinel)(unsafeEvidence);
        if (!longEOF(self, v, sentinel)) { remaining -= 1; markReadValue(); v }
        else { doneSent = true; markReadEOF(); sentinel }
      }
    override def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double =
      if (doneSent) sentinel
      else if (remaining <= 0) { doneSent = true; sentinel }
      else {
        val v = self.readFloat(sentinel)(unsafeEvidence);
        if (v != sentinel) { remaining -= 1; v }
        else { doneSent = true; sentinel }
      }
    override def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double =
      if (doneSent) { markReadEOF(); sentinel }
      else if (remaining <= 0) { doneSent = true; markReadEOF(); sentinel }
      else {
        val v = self.readDouble(sentinel)(unsafeEvidence);
        if (!doubleEOF(self, v, sentinel)) { remaining -= 1; markReadValue(); v }
        else { doneSent = true; markReadEOF(); sentinel }
      }
    override def readUpToN[A1 >: Elem](n: Int): Chunk[A1] = {
      if (doneSent || n <= 0 || remaining <= 0) return Chunk.empty
      val toRead = math.min(n.toLong, remaining).toInt
      val chunk  = self.readUpToN[A1](toRead)
      if (chunk.isEmpty) doneSent = true
      remaining -= chunk.length
      if (remaining <= 0) doneSent = true
      chunk
    }
    override def skip(n: Long): Unit = {
      val toSkip = math.min(n, remaining); self.skip(toSkip); remaining -= toSkip
    }
    override def readByte(): Int = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else Reader.anyToLowByte(v)
    }
    def close(): Unit          = doneSent = true
    override def reset(): Unit = { doneSent = false; remaining = initialRemaining; self.reset() }
  }

  /** Produced by Stream.TakenWhile — wraps a reader with a predicate limit. */
  private[streams] final class TakenWhile[Elem](
    self: Reader[Elem],
    pred: Elem => Boolean
  ) extends Reader[Elem] {
    override def jvmType: JvmType = self.jvmType
    private var doneSent: Boolean = false
    def isClosed: Boolean         = doneSent || self.isClosed
    // A `Byte` source routes its values through `readInt` (base `readByte` and
    // `Sink.collectAllByte` both pull via `readInt`), but its `pred` is a
    // `Byte => Boolean`. `Function1` is NOT specialized on a `Byte` argument, so
    // casting to `Int => Boolean` and invoking `apply$mcZI$sp` boxes the arg as
    // `java.lang.Integer`, which the predicate body unboxes as `Byte` ->
    // ClassCastException (BUG-T1, the missed sibling of the `Mapped*` BUG-N1).
    // To keep the common `Int`-source `readInt` hot path free of any per-element
    // branch or megamorphic `self.jvmType` call, we resolve the source lane ONCE
    // at construction and bind `predInt: Int => Boolean` to a predicate that
    // already applies on the correct lane: for a `Byte` source it narrows the
    // value to `Byte` before delegating (matching BUG-T1), otherwise it is the
    // raw `Int` predicate. The Int loop then just calls `predInt(v.toInt)`,
    // exactly like the lean baseline.
    private[this] val predInt: Int => Boolean =
      if (self.jvmType eq JvmType.Byte) {
        val pb = pred.asInstanceOf[Byte => Boolean]
        (i: Int) => pb(i.toByte)
      } else pred.asInstanceOf[Int => Boolean]
    def read[A1 >: Elem](sentinel: A1): A1 =
      if (doneSent) sentinel
      else {
        val v = self.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) { doneSent = true; sentinel }
        else if (pred(v.asInstanceOf[Elem])) v.asInstanceOf[A1]
        else { doneSent = true; sentinel }
      }
    override def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long =
      if (doneSent) sentinel
      else {
        val v = self.readInt(sentinel)(unsafeEvidence);
        if (v != sentinel) { if (predInt(v.toInt)) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    // Detect `self` EOF via `longEOF` (not raw `!= sentinel`) and maintain this
    // reader's own flag, since it returns the sentinel for its own reasons
    // (`doneSent`, predicate failure) and passes through values that may equal
    // the sentinel (BUG-004).
    override def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long =
      if (doneSent) { markReadEOF(); sentinel }
      else {
        val v = self.readLong(sentinel)(unsafeEvidence);
        if (!longEOF(self, v, sentinel)) {
          if (pred.asInstanceOf[Long => Boolean](v)) { markReadValue(); v }
          else { doneSent = true; markReadEOF(); sentinel }
        } else { doneSent = true; markReadEOF(); sentinel }
      }
    override def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double =
      if (doneSent) sentinel
      else {
        val v = self.readFloat(sentinel)(unsafeEvidence);
        if (v != sentinel) { if (pred.asInstanceOf[Float => Boolean](v.toFloat)) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    override def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double =
      if (doneSent) { markReadEOF(); sentinel }
      else {
        val v = self.readDouble(sentinel)(unsafeEvidence);
        if (!doubleEOF(self, v, sentinel)) {
          if (pred.asInstanceOf[Double => Boolean](v)) { markReadValue(); v }
          else { doneSent = true; markReadEOF(); sentinel }
        } else { doneSent = true; markReadEOF(); sentinel }
      }
    override def readUpToN[A1 >: Elem](n: Int): Chunk[A1] = {
      if (n <= 0 || doneSent) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var i = 0
        var v = readInt(s)(unsafeEvidence)
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readInt(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val s = Long.MaxValue
        var i = 0
        var v = readLong(s)(unsafeEvidence)
        while (!longEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readLong(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var i = 0
        var v = readFloat(s)(unsafeEvidence)
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloat(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var i = 0
        var v = readDouble(s)(unsafeEvidence)
        while (!doubleEOF(this, v, s) && i < n) {
          b.addOne(v); i += 1
          if (i < n) v = readDouble(s)(unsafeEvidence)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else {
        val b = ChunkBuilder.make[A1](math.min(n, 16))
        var i = 0
        while (i < n && !doneSent) {
          val v = read[Any](EndOfStream)
          if (v.asInstanceOf[AnyRef] eq EndOfStream) return b.result()
          b += v.asInstanceOf[A1]
          i += 1
        }
        b.result()
      }
    }
    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)
    override def readByte(): Int     = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else Reader.anyToLowByte(v)
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
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = ChunkBuilder.make[A1](math.min(n, 16))
      var i = 0
      while (i < n && !isClosed) {
        val v = read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return b.result()
        b += v.asInstanceOf[A1]
        i += 1
      }
      b.result()
    }
    override def readByte(): Int = {
      val v = read[Any](EndOfStream);
      if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else Reader.anyToLowByte(v)
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
    def source: Reader[_]
    def toInterpreter: Interpreter
  }
}
