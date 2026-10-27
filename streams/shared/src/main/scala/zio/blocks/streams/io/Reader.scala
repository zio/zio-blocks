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

import zio.blocks.async._
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.{JvmType, LifecycleWaitPlatform}
import zio.blocks.streams.internal.{AsyncInterpreter, EndOfStream, SyncInterpreter, StreamError}

import java.io.{IOException, InputStream, Reader => JReader}
import java.lang.{Double => JDouble, Float => JFloat}

/**
 * A pull-based source of elements: the consumer asks for the next element, and
 * the reader returns it or reports that it has no more. Every
 * [[zio.blocks.streams.Stream]] runs by compiling to a `Reader` that a
 * [[zio.blocks.streams.Sink]] drains, and `Stream.fromReader` turns a reader
 * into a stream. Build a reader by hand, with the constructors in the companion
 * object or by extending one of the two kinds, to plug in a source the library
 * does not cover or to pull elements yourself.
 *
 * ==Two kinds==
 * `Reader` itself only concatenates readers (`++`, `concat`, [[concatAsync]]),
 * attaches asynchronous cleanup ([[withReleaseAsync]]) and reports its lane
 * ([[jvmType]]). Pulling, querying and closing belong to its two kinds:
 *   - [[Reader.SyncReader]] returns every result directly, and a pull may block
 *     the calling thread.
 *   - [[Reader.AsyncReader]] returns every result in an `Async`, and allows one
 *     operation in flight at a time.
 *
 * `SyncReader#toAsync` and, on the JVM, `AsyncReader#toSync` convert one kind
 * into the other without copying.
 *
 * ==End of stream==
 * `read(sentinel)` returns the next element, or `sentinel` once the reader is
 * exhausted or closed. The caller chooses the sentinel, and it must be a value
 * that never occurs as an element: the reader returns an element equal to the
 * sentinel as is, and the caller cannot tell it from the end. When elements may
 * be `null`, pass a fresh object such as `new AnyRef` and compare with `eq`.
 * Failures are thrown by a `SyncReader` and fail the `Async` of an
 * `AsyncReader`; they are never reported through the sentinel.
 *
 * ==Primitive lanes==
 * [[jvmType]] names how a reader stores its elements. A reader on a primitive
 * lane supports the unboxed pull of that lane, which returns a wider type so
 * that a sentinel outside the element's range is free:
 *   - `readBoolean`, `readChar` and `readShort` return an `Int` (use `-1`, or
 *     `Int.MinValue` for `readShort`), and `readByte()` returns `0` to `255`,
 *     or `-1` at the end;
 *   - `readInt` returns a `Long` (use `Long.MinValue`), and `readFloat` returns
 *     a `Double` (use `Double.MaxValue`);
 *   - `readLong` and `readDouble` return the element type itself, so no
 *     sentinel is safe: an element equal to the sentinel looks like the end.
 *     For a loop that keeps every value, call the bulk reads `readLongs` or
 *     `readDoubles` with a one-element array, whose returned count is `-1` only
 *     at the end.
 *
 * The bulk reads (`readAll`, `readN`, `readUpToN`, `skip`) handle the end of
 * the stream themselves and never mistake an element for it. On a reader on the
 * `AnyRef` lane, the default primitive pulls unbox each element. Known bugs:
 * `readBytes`, `readInts` and `readFloats` throw `IllegalStateException` there,
 * and so does every primitive pull of a reader that wraps an `AnyRef`-lane
 * reader (`toAsync`, `toSync`, `withRelease`, `withReleaseAsync`,
 * `Reader.repeated`, and the result of `++`, `concat` or `concatAsync`).
 *
 * ==Thread safety==
 * A reader is a single-consumer cursor and is not thread-safe. Pull from one
 * thread at a time, and on an `AsyncReader` wait for each operation to finish
 * before starting the next.
 *
 * {{{
 * import zio.blocks.chunk.Chunk
 * import zio.blocks.streams.io.Reader
 *
 * val reader = Reader.fromChunk(Chunk("Zionomicon", "Functional Programming in Scala"))
 * val eof    = new AnyRef
 *
 * reader.read[AnyRef](eof)        // Zionomicon
 * reader.readAll[String]()        // Chunk(Functional Programming in Scala)
 * reader.read[AnyRef](eof) eq eof // true
 * reader.isClosed                 // true
 *
 * // A stream built from a reader closes it when the stream ends.
 * Stream.fromReader[Nothing, Int](Reader.fromRange(1 to 3)).runCollect // Right(Chunk(1,2,3))
 * }}}
 *
 * @tparam Elem
 *   the type of the elements this reader produces
 * @see
 *   [[https://zioblocks.com/reference/streams/primitives/reader/ Reader]]
 */
abstract class Reader[+Elem] {

  /**
   * Returns a reader that produces this reader's elements, then the elements of
   * `next`. Alias for `concat`, with `next` passed by name.
   */
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]

  /**
   * Returns a reader that produces this reader's elements, then the elements of
   * the reader that `next` returns. `next` runs when this reader is exhausted,
   * and this reader is closed before the switch. The result is an
   * [[Reader.AsyncReader]] unless both sides are statically known to be
   * [[Reader.SyncReader]]s (see `SyncReader#concat`).
   *
   * The result is on the `AnyRef` lane. A known bug: its primitive pulls
   * (`readInt`, `readBytes`, ...) throw, or fail with, `IllegalStateException`;
   * read it with `read`, `readAll`, `readN` or `readUpToN`, or through a
   * stream.
   *
   * When the result is an `AsyncReader`, an element that is the same object as
   * the `read` sentinel ends the current part early: that element and the rest
   * of its part are skipped (see `AsyncReader#read`).
   */
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]

  /**
   * Returns an [[Reader.AsyncReader]] that produces this reader's elements,
   * then the elements of the reader that the `Async` from `next` produces.
   * `next` runs when this reader is exhausted. The result is on the `AnyRef`
   * lane, with the known bug described on `Reader#concat`.
   */
  def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): Reader.AsyncReader[Elem2]

  /**
   * Returns an [[Reader.AsyncReader]] over this reader that runs `release` when
   * it is closed and waits for it to finish. `release` runs once, however many
   * times the reader is closed; reading to the end does not run it. A failure
   * of `release` fails that `close()` and every later one.
   *
   * A known bug: when this reader is on the `AnyRef` lane, the result's
   * primitive pulls (`readInt`, `readLongs`, ...) throw, or fail with,
   * `IllegalStateException`; `read`, `readAll`, `readN` and `readUpToN` work.
   */
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]

  /**
   * The lane this reader stores its elements in: a primitive type, whose
   * unboxed pull this reader supports, or `JvmType.AnyRef` for boxed elements.
   */
  def jvmType: JvmType = JvmType.AnyRef

  /**
   * Internal, non-destructive availability probe used by bounded bulk reads.
   * Unlike `readable`, this operation never suspends or initiates I/O.
   */
  private[streams] def tryReadable: Reader.Availability
}

/**
 * Constructors for [[Reader]] and its two kinds, [[Reader.SyncReader]] and
 * [[Reader.AsyncReader]].
 *
 * Every constructor states its kind in its return type. All of them return a
 * `SyncReader` except [[unfoldAsync]], and `repeated` keeps the kind of the
 * reader it wraps:
 *   - [[closed]]: no elements.
 *   - [[single]] and `singleInt`, `singleLong`, ...: exactly one element.
 *   - [[fromChunk]], [[fromIterable]] and [[fromRange]]: the elements of a
 *     collection, on the primitive lane of the element type.
 *   - [[fromInputStream]] and [[fromReader]]: the bytes of a
 *     `java.io.InputStream` or the characters of a `java.io.Reader`.
 *   - [[repeat]]: one value forever. `repeated`: a reader restarted each time
 *     it ends.
 *   - [[unfold]] and [[unfoldAsync]]: elements computed from a state.
 *
 * {{{
 * import zio.blocks.streams.io.Reader
 *
 * val numbers = Reader.fromRange(1 to 5)
 * numbers.readN[Int](2) // Chunk(1,2)
 * numbers.readAll[Int]() // Chunk(3,4,5)
 * }}}
 */
object Reader {

  /**
   * Normalizes an owned synchronous child to the stable lane advertised by its
   * parent.
   */
  private[streams] def normalizeSyncChild[A](child: SyncReader[A], outType: JvmType): SyncReader[A] =
    SyncInterpreter.normalizeOutput(child, outType)

  /** Normalizes an owned asynchronous child without forcing its acquisition. */
  private[streams] def normalizeAsyncChild[A](child: AsyncReader[A], outType: JvmType): AsyncReader[A] =
    if (child.jvmType eq outType) child
    else
      AsyncInterpreter
        .transform(child)(_.normalizeRecoveryOutput(outType))
        .toReader[A]

  private def replayFailure[A](cause: Throwable): Async[A] = cause match {
    case error: StreamError if error.isTrusted && !error.cleanupFailed => Async.failTrusted(error)
    case other                                                         => Async.fail(other)
  }

  private def lowByte(value: Any): Int = value match {
    case char: java.lang.Character => char.charValue().toInt & 0xff
    case bool: java.lang.Boolean   => if (bool.booleanValue()) 1 else 0
    case number: java.lang.Number  => number.intValue() & 0xff
  }

  private[streams] sealed trait Availability
  private[streams] case object Available   extends Availability
  private[streams] case object Unavailable extends Availability
  private[streams] case object Unknown     extends Availability

  /**
   * A non-owning view used when a nested consumer may close its input but the
   * enclosing interpreter retains ownership of the original reader.
   */
  private[streams] def borrowed[A](reader: SyncReader[A]): SyncReader[A] =
    // specialization-id: reader-borrowed-sync
    new DelegatingReader[A](reader) {
      override def close(): Unit = ()
    }

  /** Asynchronous counterpart of [[borrowed]]. */
  private[streams] def borrowed[A](reader: AsyncReader[A]): AsyncReader[A] =
    // specialization-id: reader-borrowed-async
    new AsyncReader[A] {
      override def jvmType: JvmType                                                   = reader.jvmType
      override private[streams] def tryReadable: Availability                         = reader.tryReadable
      def read[B >: A](sentinel: B): Async[B]                                         = reader.read(sentinel)
      override def readAll[B >: A](): Async[Chunk[B]]                                 = reader.readAll[B]()
      override def readN[B >: A](n: Int): Async[Chunk[B]]                             = reader.readN[B](n)
      override def readUpToN[B >: A](n: Int): Async[Chunk[B]]                         = reader.readUpToN[B](n)
      override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
        reader.readBooleanPhysical(sentinel)
      override def readByte(): Async[Int]                                               = reader.readBytePhysical()
      override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int]         = reader.readCharPhysical(sentinel)
      override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int]       = reader.readShortPhysical(sentinel)
      override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long]         = reader.readIntPhysical(sentinel)
      override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long]       = reader.readLongPhysical(sentinel)
      override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
        reader.readFloatPhysical(sentinel)
      override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
        reader.readDoublePhysical(sentinel)
      override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Async[Int] =
        reader.readBytesPhysical(dest, offset, length)
      override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Async[Int] =
        reader.readIntsPhysical(dest, offset, length)
      override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
        reader.readLongsPhysical(dest, offset, length)
      override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Async[Int] =
        reader.readFloatsPhysical(dest, offset, length)
      override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
        reader.readDoublesPhysical(dest, offset, length)
      def readable(): Async[Boolean]                 = reader.readable()
      def isClosed: Async[Boolean]                   = reader.isClosed
      def close(): Async[Unit]                       = Async.succeed(())
      override def reset(): Async[Unit]              = reader.reset()
      override def setLimit(n: Long): Async[Boolean] = reader.setLimit(n)
      override def setRepeat(): Async[Boolean]       = reader.setRepeat()
      override def setSkip(n: Long): Async[Boolean]  = reader.setSkip(n)
      override def skip(n: Long): Async[Unit]        = reader.skip(n)
    }

  private[streams] def borrowedSync[A](reader: AsyncReader[A]): SyncReader[A] = reader match {
    case view: SyncToAsyncView[_] => borrowed(view.source.asInstanceOf[SyncReader[A]])
    case _                        => null
  }

  /**
   * Internal lifecycle hook used only by readers installed in a
   * SyncInterpreter. Standalone readers retain their original behavior.
   */
  private[streams] trait InterpreterGuard {

    /** Throws if the interpreter operation that started this read is stale. */
    def check(): Unit

    /**
     * Closes `owner` after it failed with `primary`, recording any cleanup
     * failure.
     */
    def reject(owner: SyncReader[_], primary: Throwable): Unit

    /** Records that cleanup failed with `cleanup` after `primary`. */
    def cleanupFailed(primary: Throwable, cleanup: Throwable): Unit
  }

  private[streams] def validateArrayRange(array: Array[_], offset: Int, length: Int): Unit = {
    if (array eq null) throw new NullPointerException("array")
    if (offset < 0 || length < 0 || offset > array.length - length)
      throw new IndexOutOfBoundsException(s"offset=$offset, length=$length, array.length=${array.length}")
  }

  private def validateArrayRangeAsync(array: Array[_], offset: Int, length: Int): Async[Unit] =
    try { validateArrayRange(array, offset, length); Async.succeed(()) }
    catch { case cause: Throwable => Async.fail(cause) }

  /**
   * A [[Reader]] whose pulls, queries and `close()` return their results
   * directly. A pull may block the calling thread, for example on a reader over
   * a `java.io.InputStream`.
   *
   * The companion constructors return this kind, and so does `Stream#start` on
   * the JVM, for any stream. To write your own, extend it and implement
   * [[read]], [[isClosed]] and [[close]]: every other method has a default
   * built on [[read]]. A reader that does not override [[jvmType]] is on the
   * `AnyRef` lane, so its elements are boxed.
   *
   * {{{
   * import zio.blocks.streams.io.Reader
   *
   * final class Countdown(from: Int) extends Reader.SyncReader[Int] {
   *   private var next   = from
   *   private var closed = false
   *
   *   def read[A >: Int](sentinel: A): A =
   *     if (closed || next == 0) { closed = true; sentinel }
   *     else { next -= 1; next + 1 }
   *
   *   def isClosed: Boolean = closed
   *   def close(): Unit     = closed = true
   * }
   *
   * new Countdown(3).readAll[Int]() // Chunk(3,2,1)
   * Stream.fromReader[Nothing, Int](new Countdown(3)).map(_ * 10).runCollect // Right(Chunk(30,20,10))
   * }}}
   */
  abstract class SyncReader[+Elem] extends Reader[Elem] { self =>

    private lazy val doubleScratch = new Array[Double](1)
    private lazy val longScratch   = new Array[Long](1)

    private[io] final def collisionFreeDoubleScratch: Array[Double] = doubleScratch
    private[io] final def collisionFreeLongScratch: Array[Long]     = longScratch

    /**
     * Constructs a trusted typed source failure for custom Reader
     * implementations.
     */
    protected final def sourceError(value: Any): StreamError = StreamError.source(value)

    /** Fails immediately with a trusted typed source error. */
    protected final def failSource(value: Any): Nothing = throw sourceError(value)

    /**
     * Returns a synchronous reader that produces this reader's elements, then
     * those of `next`, which is evaluated when this reader is exhausted. Alias
     * for the synchronous `concat`, chosen when `next` is statically a
     * `SyncReader`.
     *
     * {{{
     * import zio.blocks.chunk.Chunk
     * import zio.blocks.streams.io.Reader
     *
     * val both = Reader.fromChunk(Chunk(1, 2)) ++ Reader.fromChunk(Chunk(3))
     * both.readAll[Int]() // Chunk(1,2,3)
     * }}}
     */
    final def ++[Elem2 >: Elem](next: => SyncReader[Elem2])(implicit dummy: DummyImplicit): SyncReader[Elem2] =
      concat(() => next)

    /**
     * Returns a synchronous reader that produces this reader's elements, then
     * those of the reader that `next` returns. Chosen over the asynchronous
     * overload when `next` statically returns a `SyncReader`.
     *
     * `next` runs when this reader is exhausted, and this reader is closed
     * first; an exception from `next` is thrown by the pull that reached the
     * end. `reset()` rewinds this reader and runs `next` again on the next
     * pass. Chains such as `a ++ b ++ c` share one flat reader.
     *
     * The result is on the `AnyRef` lane, whatever the lanes of the two
     * readers. A known bug: its primitive pulls (`readInt`, `readLong`,
     * `readByte`, `readBytes`, ...) throw `IllegalStateException`; read it with
     * `read`, `readAll`, `readN` or `readUpToN`, or through a stream.
     */
    final def concat[Elem2 >: Elem](next: () => SyncReader[Elem2])(implicit dummy: DummyImplicit): SyncReader[Elem2] =
      concatWithJvmType(() => StreamError.callback(next()), JvmType.AnyRef)

    private[streams] final def concatWithJvmType[Elem2 >: Elem](
      next: () => SyncReader[Elem2],
      outType: JvmType
    ): SyncReader[Elem2] =
      this match {
        case cr: ConcatReader[Elem2 @unchecked] if cr.jvmType eq outType => cr.append(next); cr
        case _                                                           => new ConcatReader[Elem2](this, next, outType)
      }

    /**
     * Returns an `AsyncReader` that produces this reader's elements, then those
     * of `next`. Chosen when `next` is statically a `Reader` of unknown kind;
     * this reader is adapted with [[toAsync]]. The result is on the `AnyRef`
     * lane, with the known bug and the sentinel limitation described on
     * `Reader#concat`.
     */
    final override def ++[Elem2 >: Elem](next: => Reader[Elem2]): AsyncReader[Elem2] =
      new AsyncConcatReader[Elem2](toAsync, () => StreamError.callbackAsync(Async.succeed(next)), JvmType.AnyRef)

    /**
     * Returns an `AsyncReader` that produces this reader's elements, then those
     * of the reader that `next` returns. Chosen when `next` statically returns
     * a `Reader` of unknown kind; this reader is adapted with [[toAsync]]. The
     * result is on the `AnyRef` lane, with the known bug and the sentinel
     * limitation described on `Reader#concat`.
     */
    final override def concat[Elem2 >: Elem](next: () => Reader[Elem2]): AsyncReader[Elem2] =
      new AsyncConcatReader[Elem2](toAsync, () => StreamError.callbackAsync(Async.succeed(next())), JvmType.AnyRef)

    private[streams] final def concatReaderWithJvmType[Elem2 >: Elem](
      next: () => Reader[Elem2],
      outType: JvmType
    ): AsyncReader[Elem2] =
      new AsyncConcatReader[Elem2](toAsync, () => StreamError.callbackAsync(Async.succeed(next())), outType)

    /**
     * Returns an `AsyncReader` that produces this reader's elements, then those
     * of the reader that the `Async` from `next` produces. `next` runs when
     * this reader is exhausted. The result is on the `AnyRef` lane, with the
     * known bug and the sentinel limitation described on `Reader#concat`.
     */
    final def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): AsyncReader[Elem2] =
      new AsyncConcatReader[Elem2](toAsync, () => StreamError.callbackAsync(next()), JvmType.AnyRef)

    /**
     * Returns an `AsyncReader` over this reader that runs `release` when it is
     * closed and waits for it to finish. `release` runs once, however many
     * times the reader is closed; reading to the end does not run it. A failure
     * of `release` fails that `close()` and every later one. Use
     * [[withRelease]] to stay synchronous.
     *
     * A known bug: when this reader is on the `AnyRef` lane, the result's
     * primitive pulls (`readInt`, `readLongs`, ...) throw, or fail with,
     * `IllegalStateException`; `read`, `readAll`, `readN` and `readUpToN` work.
     */
    final def withReleaseAsync(release: () => Async[Unit]): AsyncReader[Elem] =
      new AsyncReleaseReader[Elem](toAsync, release)

    /**
     * Returns an `AsyncReader` view of this reader, for code written against
     * the asynchronous kind. Each pull of the view calls this reader on the
     * thread that runs the `Async`, so a pull that blocks still blocks that
     * thread.
     *
     * The view shares this reader's position and lifecycle: closing the view
     * closes this reader, and pulling from both interleaves on one cursor. On a
     * reader made by `AsyncReader#toSync`, it returns the original asynchronous
     * reader instead of wrapping again.
     *
     * Once one of the view's pulls returns its end value, the view treats the
     * reader as exhausted, and every later pull returns the end value without
     * reading this reader again (until `reset()`). For `read`, that is an
     * element that is the same object as the sentinel: on the JVM a boxed
     * `Boolean`, a boxed number from -128 to 127 or an interned string can be
     * (`read(-1)` matches an element `-1`), and on Scala.js any equal `String`,
     * `Int`, `Float` or `Double` is (an equal `Long` or `Char` is not); for
     * `readBoolean`, `readChar`, `readShort`, `readInt` and `readFloat`, an
     * element equal to the sentinel. So an element that collides with the
     * sentinel ends the view early. The view's `readLong` and `readDouble`
     * detect the end by count and never stop early.
     *
     * A known bug: when this reader is on the `AnyRef` lane, the result's
     * primitive pulls (`readInt`, `readLongs`, ...) throw, or fail with,
     * `IllegalStateException`; `read`, `readAll`, `readN` and `readUpToN` work.
     *
     * {{{
     * import zio.blocks.async._
     * import zio.blocks.chunk.Chunk
     * import zio.blocks.streams.io.Reader
     *
     * val view = Reader.fromChunk(Chunk(1, 2, 3)).toAsync
     * view.readAll[Int]().block // Chunk(1,2,3)
     * }}}
     */
    final def toAsync: AsyncReader[Elem] = this match {
      case inverse: AsyncToSyncView[Elem @unchecked] => inverse.source
      case _                                         => new SyncToAsyncReader[Elem](this)
    }

    /**
     * Closes this reader: later pulls return the end value (the sentinel, `-1`
     * or an empty chunk). Closing again has no further effect, except that a
     * reader whose close failed may throw that failure again (see
     * [[withRelease]]). What happens to an underlying resource depends on the
     * reader: the readers from `Reader.fromInputStream` and `Reader.fromReader`
     * leave the stream they wrap open.
     *
     * A known bug: the readers from `Reader.repeat` and the synchronous
     * `Reader.repeated` keep producing elements after `close()`.
     */
    def close(): Unit

    /**
     * Returns `true` once this reader is exhausted or closed. Readers over a
     * collection report `true` as soon as the last element is read; a reader
     * that learns the end only by trying, such as `Reader.unfold`, reports it
     * after a pull has returned the end value. `reset()` can make it `false`
     * again.
     */
    def isClosed: Boolean

    /**
     * The lane this reader stores its elements in. A subclass that stores
     * primitives overrides it, and the matching unboxed pull (`readInt`,
     * `readLong`, ...) must then work.
     */
    override def jvmType: JvmType = JvmType.AnyRef

    /**
     * Returns the next element, or `sentinel` once this reader is exhausted or
     * closed, blocking until one of the two is known.
     *
     * `sentinel` must not occur as an element: an element equal to it is
     * returned as is, and the caller cannot tell it from the end. For elements
     * that may be `null`, pass a fresh object and compare with `eq`. A failure
     * of the source is thrown.
     */
    def read[A >: Elem](sentinel: A): A

    /**
     * Returns `true` when this reader expects to produce another element. The
     * default is `!isClosed`, so `true` does not promise that the next pull
     * returns an element. Readers over a collection answer exactly, and the
     * reader from `Reader.fromInputStream` answers `true` only when
     * `InputStream#available` reports bytes that can be read without blocking.
     */
    def readable(): Boolean = !isClosed

    private[streams] def tryReadable: Availability = if (readable()) Available else Unavailable

    /**
     * Reads every remaining element into a `Chunk`, blocking until this reader
     * is exhausted, and returns `Chunk.empty` when it already is. On an
     * infinite reader it never returns. Primitive lanes are read unboxed, and
     * no element is mistaken for the end of the stream.
     */
    def readAll[A >: Elem](): Chunk[A] = {
      val et = jvmType
      if (et eq JvmType.Boolean) {
        val b = new ChunkBuilder.Boolean(); var v = readBooleanPhysical(-1)
        while (v >= 0) { b.addOne(v != 0); v = readBooleanPhysical(-1) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Byte) {
        val b = new ChunkBuilder.Byte(); var v = readBytePhysical()
        while (v >= 0) { b.addOne(v.toByte); v = readBytePhysical() }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Char) {
        val b = new ChunkBuilder.Char(); var v = readCharPhysical(-1)
        while (v >= 0) { b.addOne(v.toChar); v = readCharPhysical(-1) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Short) {
        val b = new ChunkBuilder.Short(); val s = Int.MinValue
        var v = readShortPhysical(s)
        while (v != s) { b.addOne(v.toShort); v = readShortPhysical(s) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); val s = Long.MinValue
        var v = readIntPhysical(s);
        while (v != s) { b.addOne(v.toInt); v = readIntPhysical(s) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); val one = collisionFreeLongScratch
        var n = readLongsPhysical(one, 0, 1)
        while (n >= 0) { b.addOne(one(0)); n = readLongsPhysical(one, 0, 1) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); val s = Double.MaxValue
        var v = readFloatPhysical(s);
        while (v != s) { b.addOne(v.toFloat); v = readFloatPhysical(s) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Double) {
        val b = new ChunkBuilder.Double(); val one = collisionFreeDoubleScratch
        var n = readDoublesPhysical(one, 0, 1)
        while (n >= 0) { b.addOne(one(0)); n = readDoublesPhysical(one, 0, 1) }
        b.result().asInstanceOf[Chunk[A]]
      } else {
        val b = ChunkBuilder.make[A](16)
        var v = read[Any](EndOfStream);
        while (v.asInstanceOf[AnyRef] ne EndOfStream) { b += v.asInstanceOf[A]; v = read[Any](EndOfStream) }
        b.result()
      }
    }

    /**
     * Reads up to `n` elements into a `Chunk`, blocking until it has `n` or
     * this reader is exhausted. Returns `Chunk.empty` when `n <= 0` or the
     * reader is already exhausted. Primitive lanes are read unboxed, and no
     * element is mistaken for the end of the stream.
     *
     * {{{
     * import zio.blocks.streams.io.Reader
     *
     * val lines = Reader.fromIterable(List("first line", "second line", "third line"))
     * lines.readN[String](2) // Chunk(first line,second line)
     * lines.readN[String](2) // Chunk(third line)
     * lines.readN[String](2) // Chunk()
     * }}}
     */
    def readN[A >: Elem](n: Int): Chunk[A] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Boolean) {
        val b = new ChunkBuilder.Boolean(); b.sizeHint(math.min(n, 4096)); var i = 0
        var v = readBooleanPhysical(-1)
        while (v >= 0 && i < n) { b.addOne(v != 0); i += 1; if (i < n) v = readBooleanPhysical(-1) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Byte) {
        val b = new ChunkBuilder.Byte(); b.sizeHint(math.min(n, 4096)); var i = 0
        var v = readBytePhysical()
        while (v >= 0 && i < n) { b.addOne(v.toByte); i += 1; if (i < n) v = readBytePhysical() }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Char) {
        val b = new ChunkBuilder.Char(); b.sizeHint(math.min(n, 4096)); var i = 0
        var v = readCharPhysical(-1)
        while (v >= 0 && i < n) { b.addOne(v.toChar); i += 1; if (i < n) v = readCharPhysical(-1) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Short) {
        val b = new ChunkBuilder.Short(); b.sizeHint(math.min(n, 4096)); val s = Int.MinValue; var i = 0
        var v = readShortPhysical(s)
        while (v != s && i < n) { b.addOne(v.toShort); i += 1; if (i < n) v = readShortPhysical(s) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 4096))
        val s = Long.MinValue; var i = 0
        var v = readIntPhysical(s)
        while (v != s && i < n) { b.addOne(v.toInt); i += 1; if (i < n) v = readIntPhysical(s) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Long) {
        val b   = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 4096))
        val one = collisionFreeLongScratch; var i = 0; var count = readLongsPhysical(one, 0, 1)
        while (count >= 0 && i < n) {
          b.addOne(one(0)); i += 1; if (i < n) count = readLongsPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 4096))
        val s = Double.MaxValue; var i = 0
        var v = readFloatPhysical(s)
        while (v != s && i < n) { b.addOne(v.toFloat); i += 1; if (i < n) v = readFloatPhysical(s) }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Double) {
        val b   = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 4096))
        val one = collisionFreeDoubleScratch; var i = 0; var count = readDoublesPhysical(one, 0, 1)
        while (count >= 0 && i < n) {
          b.addOne(one(0)); i += 1; if (i < n) count = readDoublesPhysical(one, 0, 1)
        }
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
     * Reads up to `n` elements without waiting for more than the first: blocks
     * until one element or the end is known, then keeps reading only while
     * [[readable]] reports another element. Returns between 1 and `n` elements,
     * or `Chunk.empty` when `n <= 0` or this reader is exhausted.
     *
     * Unlike [[readN]], it does not wait for a slow source to fill the request.
     * On a reader whose `readable()` is the default `!isClosed`, it reads like
     * [[readN]].
     */
    def readUpToN[A >: Elem](n: Int): Chunk[A] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Boolean) {
        val b = new ChunkBuilder.Boolean(); var v = readBooleanPhysical(-1); var i = 0
        while (v >= 0 && i < n) {
          b.addOne(v != 0); i += 1; if (i < n && (tryReadable eq Available)) v = readBooleanPhysical(-1) else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Byte) {
        val b = new ChunkBuilder.Byte(); var v = readBytePhysical(); var i = 0
        while (v >= 0 && i < n) {
          b.addOne(v.toByte); i += 1; if (i < n && (tryReadable eq Available)) v = readBytePhysical() else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Char) {
        val b = new ChunkBuilder.Char(); var v = readCharPhysical(-1); var i = 0
        while (v >= 0 && i < n) {
          b.addOne(v.toChar); i += 1; if (i < n && (tryReadable eq Available)) v = readCharPhysical(-1) else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Short) {
        val b = new ChunkBuilder.Short(); val s = Int.MinValue; var v = readShortPhysical(s); var i = 0
        while (v != s && i < n) {
          b.addOne(v.toShort); i += 1; if (i < n && (tryReadable eq Available)) v = readShortPhysical(s) else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); val s = Long.MinValue; var v = readIntPhysical(s); var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1; if (i < n && (tryReadable eq Available)) v = readIntPhysical(s) else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Long) {
        val b = new ChunkBuilder.Long(); val one = collisionFreeLongScratch; var count = readLongsPhysical(one, 0, 1);
        var i = 0
        while (count >= 0 && i < n) {
          b.addOne(one(0)); i += 1;
          if (i < n && (tryReadable eq Available)) count = readLongsPhysical(one, 0, 1) else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); val s = Double.MaxValue; var v = readFloatPhysical(s); var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1; if (i < n && (tryReadable eq Available)) v = readFloatPhysical(s) else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else if (et eq JvmType.Double) {
        val b     = new ChunkBuilder.Double(); val one    = collisionFreeDoubleScratch;
        var count = readDoublesPhysical(one, 0, 1); var i = 0
        while (count >= 0 && i < n) {
          b.addOne(one(0)); i += 1;
          if (i < n && (tryReadable eq Available)) count = readDoublesPhysical(one, 0, 1) else i = n
        }
        b.result().asInstanceOf[Chunk[A]]
      } else {
        val b = ChunkBuilder.make[A](math.min(n, 64)); var v = read[Any](EndOfStream); var i = 0
        while ((v.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
          b += v.asInstanceOf[A]; i += 1; if (i < n && (tryReadable eq Available)) v = read[Any](EndOfStream) else i = n
        }
        b.result()
      }
    }

    /**
     * Returns the next `Boolean` as `1` (`true`) or `0` (`false`), or
     * `_sentinel` at the end, without boxing on the `Boolean` lane. Choose a
     * sentinel outside `0` and `1`, such as `-1`, and no element can be
     * mistaken for the end. Throws `UnsupportedOperationException` on a reader
     * of another primitive lane that does not implement it.
     */
    def readBoolean(_sentinel: Int)(implicit _ev: Elem <:< Boolean): Int =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel
        else if (value.asInstanceOf[Boolean]) 1
        else 0
      } else throw unsupportedExactRead(JvmType.Boolean, _sentinel, _ev)

    /**
     * Returns the next element as an unsigned byte, `0` to `255`, or `-1` at
     * the end, so no element can be mistaken for the end. It needs no evidence
     * and compiles on any reader: the `Byte` lane reads unboxed, the default on
     * the `AnyRef` lane expects `Byte` elements, and on another primitive lane
     * the collection readers return the low 8 bits of each element while the
     * default throws `UnsupportedOperationException`.
     */
    def readByte(): Int =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) -1 else value.asInstanceOf[Byte].toInt & 0xff
      } else throw unsupportedExactRead(JvmType.Byte)

    /**
     * Reads up to `len` bytes into `buf`, starting at `offset`, without boxing,
     * like `java.io.InputStream#read(byte[], int, int)`. Blocks until one byte
     * or the end is known, then keeps reading only while [[readable]] reports
     * another byte. Returns the number of bytes read (`1` to `len`), `-1` at
     * the end, or `0` when `len` is `0`.
     *
     * Throws `NullPointerException` when `buf` is `null` and
     * `IndexOutOfBoundsException` when the range does not fit in `buf`, even
     * when `len` is `0`. The reader from `Reader.fromInputStream` passes the
     * whole request to `InputStream#read`. A known bug: on a reader on the
     * `AnyRef` lane, such as a custom reader that does not override
     * [[jvmType]], it throws `IllegalStateException` instead of unboxing.
     *
     * {{{
     * import zio.blocks.streams.io.Reader
     *
     * val reader = Reader.fromInputStream(new java.io.ByteArrayInputStream("Zionomicon".getBytes("UTF-8")))
     * val buf    = new Array[Byte](4)
     * val text   = new StringBuilder
     * var n      = reader.readBytes(buf, 0, buf.length)
     * while (n > 0) {
     *   text ++= new String(buf, 0, n, "UTF-8")
     *   n = reader.readBytes(buf, 0, buf.length)
     * }
     * text.toString // Zionomicon
     * n             // -1
     * }}}
     */
    def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Elem <:< Byte): Int = {
      validateArrayRange(buf, offset, len)
      if (len == 0) return 0
      var i = 0
      while (i < len) {
        val b = readBytePhysical()
        if (b < 0) return if (i > 0) i else -1
        buf(offset + i) = b.toByte
        i += 1
        if (i < len && (tryReadable ne Available)) return i
      }
      i
    }

    /**
     * Reads up to `maxLen` `Int` elements into `buf`, starting at `offset`,
     * without boxing. Blocks until one element or the end is known, then keeps
     * reading only while [[readable]] reports another element. Returns the
     * number read (`1` to `maxLen`), `-1` at the end, or `0` when `maxLen` is
     * `0`; no element is mistaken for the end.
     *
     * Throws `NullPointerException` or `IndexOutOfBoundsException` for an
     * invalid range, as [[readBytes]] does. A known bug: on a reader on the
     * `AnyRef` lane it throws `IllegalStateException` instead of unboxing.
     */
    def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: Elem <:< Int): Int = {
      validateArrayRange(buf, offset, maxLen)
      if (maxLen == 0) return 0
      val sentinel = Long.MinValue
      var i        = 0
      while (i < maxLen) {
        val v = readIntPhysical(sentinel)
        if (v == sentinel) return if (i > 0) i else -1
        buf(offset + i) = v.toInt
        i += 1
        if (i < maxLen && (tryReadable ne Available)) return i
      }
      i
    }

    /**
     * Reads up to `maxLen` `Long` elements into `buf`, starting at `offset`,
     * without boxing. Blocks until one element or the end is known, then keeps
     * reading only while [[readable]] reports another element. Returns the
     * number read (`1` to `maxLen`), `-1` at the end, or `0` when `maxLen` is
     * `0`. Throws `NullPointerException` or `IndexOutOfBoundsException` for an
     * invalid range, as [[readBytes]] does.
     *
     * The count reports the end separately from the data, so every `Long`
     * value, `Long.MaxValue` included, stays readable. Called with a
     * one-element array, it is the element-by-element pull to use when no
     * sentinel is safe for [[readLong]]:
     *
     * {{{
     * import zio.blocks.chunk.Chunk
     * import zio.blocks.streams.io.Reader
     *
     * val ids = Reader.fromChunk(Chunk(7L, Long.MaxValue, 9L))
     * val one = new Array[Long](1)
     * val all = Chunk.newBuilder[Long]
     * while (ids.readLongs(one, 0, 1) > 0) all += one(0)
     * all.result() // Chunk(7,9223372036854775807,9)
     * }}}
     */
    def readLongs(buf: Array[Long], offset: Int, maxLen: Int)(implicit ev: Elem <:< Long): Int =
      if (jvmType eq JvmType.AnyRef) {
        validateArrayRange(buf, offset, maxLen)
        var count = 0
        while (count < maxLen) {
          val value = read[Any](EndOfStream)
          if (value.asInstanceOf[AnyRef] eq EndOfStream) return if (count == 0 && maxLen > 0) -1 else count
          buf(offset + count) = value.asInstanceOf[Long]
          count += 1
          if (count < maxLen && (tryReadable ne Available)) return count
        }
        count
      } else throw unsupportedExactRead(JvmType.Long, buf, offset, maxLen, ev)

    /**
     * Reads up to `maxLen` `Double` elements into `buf`, starting at `offset`,
     * without boxing. Blocks until one element or the end is known, then keeps
     * reading only while [[readable]] reports another element. Returns the
     * number read (`1` to `maxLen`), `-1` at the end, or `0` when `maxLen` is
     * `0`. Throws `NullPointerException` or `IndexOutOfBoundsException` for an
     * invalid range, as [[readBytes]] does.
     *
     * The count reports the end separately from the data, so every `Double`,
     * including `Double.MaxValue`, the infinities and `NaN`, stays readable.
     * Called with a one-element array, it is the element-by-element pull to use
     * when no sentinel is safe for [[readDouble]].
     */
    def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: Elem <:< Double): Int =
      if (jvmType eq JvmType.AnyRef) {
        validateArrayRange(buf, offset, maxLen)
        var count = 0
        while (count < maxLen) {
          val value = read[Any](EndOfStream)
          if (value.asInstanceOf[AnyRef] eq EndOfStream) return if (count == 0 && maxLen > 0) -1 else count
          buf(offset + count) = value.asInstanceOf[Double]
          count += 1
          if (count < maxLen && (tryReadable ne Available)) return count
        }
        count
      } else throw unsupportedExactRead(JvmType.Double, buf, offset, maxLen, ev)

    /**
     * Reads up to `maxLen` `Float` elements into `buf`, starting at `offset`,
     * without boxing. Blocks until one element or the end is known, then keeps
     * reading only while [[readable]] reports another element. Returns the
     * number read (`1` to `maxLen`), `-1` at the end, or `0` when `maxLen` is
     * `0`; no element is mistaken for the end.
     *
     * Throws `NullPointerException` or `IndexOutOfBoundsException` for an
     * invalid range, as [[readBytes]] does. A known bug: on a reader on the
     * `AnyRef` lane it throws `IllegalStateException` instead of unboxing.
     */
    def readFloats(buf: Array[Float], offset: Int, maxLen: Int)(implicit ev: Elem <:< Float): Int = {
      validateArrayRange(buf, offset, maxLen)
      if (maxLen == 0) return 0
      val sentinel = Double.MaxValue
      var i        = 0
      while (i < maxLen) {
        val v = readFloatPhysical(sentinel)
        if (v == sentinel) return if (i > 0) i else -1
        buf(offset + i) = v.toFloat
        i += 1
        if (i < maxLen && (tryReadable ne Available)) return i
      }
      i
    }

    /**
     * Returns the next `Char` widened to an `Int` (`0` to `65535`), or
     * `_sentinel` at the end, without boxing on the `Char` lane. Choose a
     * sentinel outside that range, such as `-1`, and no element can be mistaken
     * for the end. Throws `UnsupportedOperationException` on a reader of
     * another primitive lane that does not implement it.
     */
    def readChar(_sentinel: Int)(implicit _ev: Elem <:< Char): Int =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Char].toInt
      } else throw unsupportedExactRead(JvmType.Char, _sentinel, _ev)

    /**
     * Returns the next `Double`, or `_sentinel` at the end, without boxing on
     * the `Double` lane. Throws `UnsupportedOperationException` on a reader of
     * another primitive lane that does not implement it.
     *
     * Every `Double` can be an element, so no sentinel is safe: an element
     * equal to `_sentinel` is returned as is, and the caller cannot tell it
     * from the end. A `NaN` sentinel never compares equal to anything, so the
     * end is not detectable with `==` either. To keep every value, read with
     * [[readDoubles]] and a one-element array instead.
     */
    def readDouble(_sentinel: Double)(implicit _ev: Elem <:< Double): Double =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Double]
      } else throw unsupportedExactRead(JvmType.Double, _sentinel, _ev)

    /**
     * Returns the next `Float` widened to a `Double`, or `_sentinel` at the
     * end, without boxing on the `Float` lane. Choose a sentinel that no
     * `Float` widens to, such as `Double.MaxValue`, and no element can be
     * mistaken for the end. Throws `UnsupportedOperationException` on a reader
     * of another primitive lane that does not implement it.
     */
    def readFloat(_sentinel: Double)(implicit _ev: Elem <:< Float): Double =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Float].toDouble
      } else throw unsupportedExactRead(JvmType.Float, _sentinel, _ev)

    /**
     * Returns the next `Int` widened to a `Long`, or `_sentinel` at the end,
     * without boxing on the `Int` lane. Choose a sentinel outside the `Int`
     * range, such as `Long.MinValue`, and no element can be mistaken for the
     * end. Throws `UnsupportedOperationException` on a reader of another
     * primitive lane that does not implement it.
     */
    def readInt(_sentinel: Long)(implicit _ev: Elem <:< Int): Long =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Int].toLong
      } else throw unsupportedExactRead(JvmType.Int, _sentinel, _ev)

    /**
     * Returns the next `Long`, or `_sentinel` at the end, without boxing on the
     * `Long` lane. Throws `UnsupportedOperationException` on a reader of
     * another primitive lane that does not implement it.
     *
     * Every `Long` can be an element, so no sentinel is safe: an element equal
     * to `_sentinel` is returned as is, and the caller cannot tell it from the
     * end. Use it only when some value, such as `Long.MaxValue`, cannot occur
     * in your data; otherwise read with [[readLongs]] and a one-element array.
     */
    def readLong(_sentinel: Long)(implicit _ev: Elem <:< Long): Long =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Long]
      } else throw unsupportedExactRead(JvmType.Long, _sentinel, _ev)

    /**
     * Returns the next `Short` widened to an `Int`, or `_sentinel` at the end,
     * without boxing on the `Short` lane. Choose a sentinel outside the `Short`
     * range, such as `Int.MinValue`, and no element can be mistaken for the
     * end. Throws `UnsupportedOperationException` on a reader of another
     * primitive lane that does not implement it.
     */
    def readShort(_sentinel: Int)(implicit _ev: Elem <:< Short): Int =
      if (jvmType eq JvmType.AnyRef) {
        val value = read[Any](EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Short].toInt
      } else throw unsupportedExactRead(JvmType.Short, _sentinel, _ev)

    private[streams] final def readBooleanPhysical(sentinel: Int): Int = {
      requirePhysicalLane(JvmType.Boolean)
      asInstanceOf[SyncReader[Boolean]].readBoolean(sentinel)
    }

    private[streams] final def readBytePhysical(): Int = {
      requirePhysicalLane(JvmType.Byte)
      readByte()
    }

    private[streams] final def readBytesPhysical(dest: Array[Byte], offset: Int, length: Int): Int = {
      requirePhysicalLane(JvmType.Byte)
      asInstanceOf[SyncReader[Byte]].readBytes(dest, offset, length)
    }

    private[streams] final def readCharPhysical(sentinel: Int): Int = {
      requirePhysicalLane(JvmType.Char)
      asInstanceOf[SyncReader[Char]].readChar(sentinel)
    }

    private[streams] final def readDoublePhysical(sentinel: Double): Double = {
      requirePhysicalLane(JvmType.Double)
      asInstanceOf[SyncReader[Double]].readDouble(sentinel)
    }

    private[streams] final def readDoublesPhysical(dest: Array[Double], offset: Int, length: Int): Int = {
      requirePhysicalLane(JvmType.Double)
      asInstanceOf[SyncReader[Double]].readDoubles(dest, offset, length)
    }

    private[streams] final def readFloatPhysical(sentinel: Double): Double = {
      requirePhysicalLane(JvmType.Float)
      asInstanceOf[SyncReader[Float]].readFloat(sentinel)
    }

    private[streams] final def readFloatsPhysical(dest: Array[Float], offset: Int, length: Int): Int = {
      requirePhysicalLane(JvmType.Float)
      asInstanceOf[SyncReader[Float]].readFloats(dest, offset, length)
    }

    private[streams] final def readIntPhysical(sentinel: Long): Long = {
      requirePhysicalLane(JvmType.Int)
      asInstanceOf[SyncReader[Int]].readInt(sentinel)
    }

    private[streams] final def readIntsPhysical(dest: Array[Int], offset: Int, length: Int): Int = {
      requirePhysicalLane(JvmType.Int)
      asInstanceOf[SyncReader[Int]].readInts(dest, offset, length)
    }

    private[streams] final def readLongPhysical(sentinel: Long): Long = {
      requirePhysicalLane(JvmType.Long)
      asInstanceOf[SyncReader[Long]].readLong(sentinel)
    }

    private[streams] final def readLongsPhysical(dest: Array[Long], offset: Int, length: Int): Int = {
      requirePhysicalLane(JvmType.Long)
      asInstanceOf[SyncReader[Long]].readLongs(dest, offset, length)
    }

    private[streams] final def readShortPhysical(sentinel: Int): Int = {
      requirePhysicalLane(JvmType.Short)
      asInstanceOf[SyncReader[Short]].readShort(sentinel)
    }

    private def requirePhysicalLane(expected: JvmType): Unit =
      if (jvmType ne expected)
        throw new IllegalStateException(s"Reader physical lane $jvmType cannot be pulled as $expected")

    private def unsupportedExactRead(expected: JvmType, ignored: Any*): UnsupportedOperationException = {
      val _ = ignored
      new UnsupportedOperationException(
        s"Reader with physical lane $jvmType does not implement exact $expected pulling"
      )
    }

    /**
     * Rewinds this reader so that its elements can be read again from the
     * start; the collection readers also rewind after [[close]]. The default
     * throws `UnsupportedOperationException`, and so do the readers over a
     * one-shot source (`Reader.fromInputStream`, `Reader.fromReader`). The
     * collection readers rewind to the position set by [[setSkip]] and keep the
     * limit set by [[setLimit]], and `Reader.unfold` restarts from its initial
     * state.
     *
     * {{{
     * import zio.blocks.chunk.Chunk
     * import zio.blocks.streams.io.Reader
     *
     * val reader = Reader.fromChunk(Chunk(1, 2, 3))
     * reader.readAll[Int]() // Chunk(1,2,3)
     * reader.reset()
     * reader.readAll[Int]() // Chunk(1,2,3)
     * }}}
     */
    def reset(): Unit = throw new UnsupportedOperationException("This reader does not support reset")

    /**
     * Asks this reader to produce at most `n` more elements and returns whether
     * it can do so itself, without a per-element counter. `false`, the default,
     * leaves the reader unchanged, and the caller has to count. The readers
     * from `fromChunk`, `fromRange` and `fromIterable` over a `Vector` return
     * `true`, and keep the limit across [[reset]].
     */
    def setLimit(n: Long): Boolean = false

    /**
     * Asks this reader to start again from the beginning whenever it would end,
     * so that it never ends, and returns whether it can do so itself. `false`,
     * the default, leaves the reader unchanged. Only the readers from
     * `Reader.single` and its primitive variants return `true`; the collection
     * readers return `false`.
     */
    def setRepeat(): Boolean = false

    /**
     * Asks this reader to drop its next `n` elements and returns whether it can
     * do so itself, without reading them one by one. `false`, the default,
     * leaves the reader unchanged. The readers from `fromChunk`, `fromRange`
     * and `fromIterable` over a `Vector` return `true`, and [[reset]] rewinds
     * them to the position after the drop.
     */
    def setSkip(n: Long): Boolean = false

    /**
     * Reads and discards up to `n` elements, stopping early at the end. Does
     * nothing when `n <= 0`. The chunk, range and `Vector` readers move their
     * position instead of reading.
     */
    def skip(n: Long): Unit =
      Reader.skipViaSentinel(this, n)

    /**
     * Returns a reader with this reader's elements that runs `release` once,
     * after closing this reader, the first time it is closed. Reading to the
     * end does not run `release`; only [[close]] does.
     *
     * A failure of this reader's `close()` or of `release` is thrown by
     * `close()`, and every later `close()` throws it again. A `close()` from
     * another thread waits until the first one has finished. The result can be
     * [[reset]] while it is open, if this reader supports it.
     *
     * A known bug: when this reader is on the `AnyRef` lane, the result's
     * primitive pulls (`readInt`, `readLongs`, ...) throw
     * `IllegalStateException`; `read`, `readAll`, `readN` and `readUpToN` work.
     *
     * {{{
     * import zio.blocks.streams.io.Reader
     *
     * val log    = new StringBuilder
     * val reader = Reader.fromRange(1 to 3).withRelease(() => log ++= "released")
     * reader.readAll[Int]() // Chunk(1,2,3)
     * log.toString          // ""
     * reader.close()
     * log.toString          // released
     * }}}
     */
    def withRelease(release: () => Unit): SyncReader[Elem] = {
      val self = this
      // specialization-id: reader-sync-with-release
      new Reader.DelegatingReader[Elem](self) with LifecycleWaitPlatform {
        private var closeResult: Completer[Unit] = null
        private var closingThread: Thread        = null
        private var resettingThread: Thread      = null
        private var closeRequestedDuringReset    = false
        private var lifecycle                    = 0 // open, resetting, closing, finalized

        override protected def delegationOpen: Boolean = synchronized(lifecycle == 0)

        override def reset(): Unit = {
          synchronized {
            if (lifecycle == 1 || lifecycle == 2)
              throw new IllegalStateException("Cannot reset reader from its active operation")
            if (lifecycle == 3) throw new UnsupportedOperationException("Cannot reset a finalized release reader")
            lifecycle = 1
            resettingThread = Thread.currentThread()
            closeRequestedDuringReset = false
          }
          var failure: Throwable = null
          try self.reset()
          catch { case cause: Throwable => failure = cause }
          val deferredClose = synchronized {
            resettingThread = null
            if (!closeRequestedDuringReset) {
              lifecycle = 0
              closeResult = null
              false
            } else {
              lifecycle = 2
              closingThread = Thread.currentThread()
              true
            }
          }
          if (deferredClose) {
            var closeFailure: Throwable = null
            try self.close()
            catch { case cause: Throwable => closeFailure = cause }
            try StreamError.callback(release())
            catch { case cause: Throwable => closeFailure = StreamError.attachCleanup(closeFailure, cause) }
            if (closeFailure eq null) closeResult.succeed(()) else closeResult.fail(closeFailure)
            synchronized { closingThread = null; lifecycle = 3 }
            val rejection =
              if (failure ne null) failure
              else new UnsupportedOperationException("Cannot reset a finalized release reader")
            if (closeFailure ne null) StreamError.attachCleanup(rejection, closeFailure)
            throw rejection
          }
          if (failure ne null) throw failure
        }

        override def close(): Unit = {
          val (completion, leader, reentrant) = synchronized {
            if (lifecycle == 0) {
              closeResult = new Completer[Unit]
              lifecycle = 2
              closingThread = Thread.currentThread()
              (closeResult, true, false)
            } else if (lifecycle == 1) {
              if (closeResult eq null) closeResult = new Completer[Unit]
              closeRequestedDuringReset = true
              (closeResult, false, resettingThread eq Thread.currentThread())
            } else (closeResult, false, closingThread eq Thread.currentThread())
          }
          if (leader) {
            var failure: Throwable = null
            try self.close()
            catch { case cause: Throwable => failure = cause }
            try StreamError.callback(release())
            catch {
              case cause: Throwable => failure = StreamError.attachCleanup(failure, cause)
            }
            if (failure eq null) completion.succeed(()) else completion.fail(failure)
            synchronized { closingThread = null; lifecycle = 3 }
          }
          if (!reentrant) awaitClose(completion)
        }
      }
    }

  }

  /**
   * A [[Reader]] whose pulls, queries and `close()` return an `Async`, so a
   * pull can wait for its source without blocking a thread. A stream with an
   * asynchronous stage materializes as this kind, `Stream#startAsync` and
   * `Stream#useReaderAsync` hand one out, and [[Reader.unfoldAsync]] builds
   * one. It is the kind to use for I/O on Scala.js, where a pull must not block
   * a thread.
   *
   * Allow at most one operation in flight: wait for the `Async` of a pull, a
   * control operation or `close()` before starting the next one. The owner of
   * the reader (for example the caller of `Stream#startAsync`) runs `close()`
   * and waits for it.
   *
   * To write your own, extend it and implement [[read]], [[readable]],
   * [[isClosed]] and [[close]]; every other method has a default built on
   * [[read]].
   *
   * {{{
   * import zio.blocks.async._
   * import zio.blocks.streams.io.Reader
   *
   * val ticks = Reader.unfoldAsync(1)(n => Async.succeed(if (n > 3) None else Some((n, n + 1))))
   * val all   = ticks.readAll[Int]().flatMap(chunk => ticks.close().map(_ => chunk))
   * all.block // Chunk(1,2,3)
   * }}}
   */
  abstract class AsyncReader[+Elem] extends Reader[Elem] with AsyncReaderPlatform[Elem] { self =>

    private lazy val doubleScratch = new Array[Double](1)
    private lazy val longScratch   = new Array[Long](1)

    /**
     * Constructs a trusted typed source failure for custom Reader
     * implementations.
     */
    protected final def sourceError(value: Any): StreamError = StreamError.source(value)

    /** Returns a failed Async with a trusted typed source error. */
    protected final def failSource[A](value: Any): Async[A] = Async.failTrusted(sourceError(value))

    final override def ++[Elem2 >: Elem](next: => Reader[Elem2]): AsyncReader[Elem2] =
      concat(() => next)
    final override def concat[Elem2 >: Elem](next: () => Reader[Elem2]): AsyncReader[Elem2] =
      concatAsyncWithJvmType(() => Async.succeed(next()), JvmType.AnyRef)
    private[streams] final def concatReaderWithJvmType[Elem2 >: Elem](
      next: () => Reader[Elem2],
      outType: JvmType
    ): AsyncReader[Elem2] =
      concatAsyncWithJvmType(() => Async.succeed(next()), outType)
    final override def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): AsyncReader[Elem2] =
      concatAsyncWithJvmType(next, JvmType.AnyRef)
    private[streams] final def concatAsyncWithJvmType[Elem2 >: Elem](
      next: () => Async[Reader[Elem2]],
      outType: JvmType
    ): AsyncReader[Elem2] =
      this match {
        case concat: AsyncConcatReader[Elem2 @unchecked] if concat.jvmType eq outType =>
          concat
            .appendIfPristine(() => StreamError.callbackAsync(next()))
            .getOrElse(new AsyncConcatReader[Elem2](this, () => StreamError.callbackAsync(next()), outType))
        case _ => new AsyncConcatReader[Elem2](this, () => StreamError.callbackAsync(next()), outType)
      }
    final override def withReleaseAsync(release: () => Async[Unit]): AsyncReader[Elem] =
      new AsyncReleaseReader[Elem](this, release)

    /**
     * Returns an `Async` that closes this reader: it cancels or waits for work
     * in flight and releases what the reader holds, and later pulls produce the
     * end value. A failure while closing fails the `Async`. The library's
     * readers accept a repeated close, but the owner should close once.
     */
    def close(): Async[Unit]

    /**
     * Returns an `Async` that produces `true` once this reader is exhausted or
     * closed. As with `SyncReader#isClosed`, a reader that learns the end only
     * by trying, such as [[Reader.unfoldAsync]], reports it after a pull has
     * returned the end value.
     */
    def isClosed: Async[Boolean]

    /**
     * Returns an `Async` that produces `true` when this reader expects to
     * produce another element. As with `SyncReader#readable`, `true` does not
     * promise that the next pull returns an element.
     */
    def readable(): Async[Boolean]

    /**
     * Returns an `Async` that produces the next element, or `sentinel` once
     * this reader is exhausted or closed. A failure of the source fails the
     * `Async`.
     *
     * `sentinel` must not occur as an element: on most readers an element equal
     * to it is returned as is, and the caller cannot tell it from the end. Two
     * readers lose more: the `SyncReader#toAsync` view stops until `reset()`
     * (see there), and an asynchronous concatenation built by `++`, `concat` or
     * `concatAsync` treats an element that is the same object as the sentinel
     * as the end of its current part, skipping that element and the rest of the
     * part.
     */
    def read[A >: Elem](sentinel: A): Async[A]

    private[streams] def tryReadable: Availability = Unknown

    /**
     * Returns an `Async` that reads every remaining element into a `Chunk`,
     * like [[readN]] with `Int.MaxValue`. On an infinite reader it never
     * completes.
     */
    def readAll[A >: Elem](): Async[Chunk[A]] =
      readN(Int.MaxValue)

    /**
     * Returns an `Async` that reads up to `n` elements into a `Chunk`, stopping
     * early only at the end, and produces `Chunk.empty` when `n <= 0` or the
     * reader is exhausted. The default pulls one element at a time and
     * regularly yields to the scheduler, so a fast reader does not hold the
     * thread; no element is mistaken for the end.
     */
    def readN[A >: Elem](n: Int): Async[Chunk[A]] =
      if (n <= 0) Async.succeed(Chunk.empty)
      else readExactN[A](n, stopWhenUnavailable = false)

    /**
     * Returns an `Async` that reads up to `n` elements without waiting for more
     * than the first: after the first element it stops as soon as the reader
     * cannot say that another one is ready. Produces `Chunk.empty` when
     * `n <= 0` or the reader is exhausted. A reader that never knows whether an
     * element is ready, such as [[Reader.unfoldAsync]], produces one element
     * per call.
     */
    def readUpToN[A >: Elem](n: Int): Async[Chunk[A]] =
      if (n <= 0) Async.succeed(Chunk.empty)
      else readExactN[A](n, stopWhenUnavailable = true)

    private def readExactN[A >: Elem](n: Int, stopWhenUnavailable: Boolean): Async[Chunk[A]] = jvmType match {
      case JvmType.Boolean =>
        val b                                          = new ChunkBuilder.Boolean()
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readBooleanPhysical(-1).flatMap { v =>
          if (v < 0) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(v != 0); continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case JvmType.Byte =>
        val b                                          = new ChunkBuilder.Byte()
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readBytePhysical().flatMap { v =>
          if (v < 0) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(v.toByte);
            continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case JvmType.Char =>
        val b                                          = new ChunkBuilder.Char()
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readCharPhysical(-1).flatMap { v =>
          if (v < 0) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(v.toChar);
            continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case JvmType.Short =>
        val b                                          = new ChunkBuilder.Short(); val sentinel = Int.MinValue
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readShortPhysical(sentinel).flatMap { v =>
          if (v == sentinel) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(v.toShort);
            continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case JvmType.Int =>
        val b                                          = new ChunkBuilder.Int(); val sentinel = Long.MinValue
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readIntPhysical(sentinel).flatMap { v =>
          if (v == sentinel) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(v.toInt);
            continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case JvmType.Long =>
        val b                                          = new ChunkBuilder.Long()
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readLongsPhysical(longScratch, 0, 1).flatMap { count =>
          if (count < 0) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(longScratch(0));
            continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case JvmType.Float =>
        val b                                          = new ChunkBuilder.Float(); val sentinel = Double.MaxValue
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readFloatPhysical(sentinel).flatMap { v =>
          if (v == sentinel) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(v.toFloat);
            continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case JvmType.Double =>
        val b                                          = new ChunkBuilder.Double()
        def loop(i: Int, budget: Int): Async[Chunk[A]] = readDoublesPhysical(doubleScratch, 0, 1).flatMap { count =>
          if (count < 0) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else {
            b.addOne(doubleScratch(0));
            continueExactN(i, budget, n, stopWhenUnavailable, b.result().asInstanceOf[Chunk[A]])(loop)
          }
        }
        loop(0, 255)
      case _ =>
        val b                                          = ChunkBuilder.make[A](math.min(n, 16))
        def loop(i: Int, budget: Int): Async[Chunk[A]] = read[Any](EndOfStream).flatMap { value =>
          if (value.asInstanceOf[AnyRef] eq EndOfStream) Async.succeed(b.result())
          else { b += value.asInstanceOf[A]; continueExactN(i, budget, n, stopWhenUnavailable, b.result())(loop) }
        }
        loop(0, 255)
    }

    private def continueExactN[A](i: Int, budget: Int, n: Int, stop: Boolean, result: => Chunk[A])(
      loop: (Int, Int) => Async[Chunk[A]]
    ): Async[Chunk[A]] =
      if (i + 1 >= n || (stop && (tryReadable ne Available))) Async.succeed(result)
      else if (budget > 0) loop(i + 1, budget - 1)
      else Async.reschedule(() => loop(i + 1, 255))

    /**
     * Returns an `Async` that produces `1`, `0` or `_sentinel`, like
     * [[Reader.SyncReader#readBoolean]]. Choose a sentinel outside `0` and `1`,
     * such as `-1`, and no element can be mistaken for the end. On a reader of
     * another primitive lane that does not implement it, the `Async` fails with
     * `UnsupportedOperationException`.
     */
    def readBoolean(_sentinel: Int)(implicit _ev: Elem <:< Boolean): Async[Int] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream).map(value =>
          if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else if (value.asInstanceOf[Boolean]) 1 else 0
        )
      else Async.fail(unsupportedExactRead(JvmType.Boolean, _sentinel, _ev))

    /**
     * Returns an `Async` that produces the next element as `0` to `255`, or
     * `-1` at the end, so no element can be mistaken for the end, like
     * [[Reader.SyncReader#readByte]]. On a reader of another primitive lane
     * that does not implement it, the `Async` fails with
     * `UnsupportedOperationException`.
     */
    def readByte(): Async[Int] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream)
          .map(value => if (value.asInstanceOf[AnyRef] eq EndOfStream) -1 else value.asInstanceOf[Byte].toInt & 0xff)
      else Async.fail(unsupportedExactRead(JvmType.Byte))

    /**
     * Returns an `Async` that produces the next `Char` as an `Int`, or
     * `_sentinel`, like [[Reader.SyncReader#readChar]]. Choose a sentinel
     * outside `0` to `65535`, such as `-1`, and no element can be mistaken for
     * the end. On a reader of another primitive lane that does not implement
     * it, the `Async` fails with `UnsupportedOperationException`.
     */
    def readChar(_sentinel: Int)(implicit _ev: Elem <:< Char): Async[Int] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream)
          .map(value => if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Char].toInt)
      else Async.fail(unsupportedExactRead(JvmType.Char, _sentinel, _ev))

    /**
     * Returns an `Async` that produces the next `Short` as an `Int`, or
     * `_sentinel`, like [[Reader.SyncReader#readShort]]. Choose a sentinel
     * outside the `Short` range, such as `Int.MinValue`, and no element can be
     * mistaken for the end. On a reader of another primitive lane that does not
     * implement it, the `Async` fails with `UnsupportedOperationException`.
     */
    def readShort(_sentinel: Int)(implicit _ev: Elem <:< Short): Async[Int] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream)
          .map(value => if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Short].toInt)
      else Async.fail(unsupportedExactRead(JvmType.Short, _sentinel, _ev))

    /**
     * Returns an `Async` that produces the next `Int` as a `Long`, or
     * `_sentinel`, like [[Reader.SyncReader#readInt]]. Choose a sentinel
     * outside the `Int` range, such as `Long.MinValue`, and no element can be
     * mistaken for the end. On a reader of another primitive lane that does not
     * implement it, the `Async` fails with `UnsupportedOperationException`.
     */
    def readInt(_sentinel: Long)(implicit _ev: Elem <:< Int): Async[Long] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream)
          .map(value => if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Int].toLong)
      else Async.fail(unsupportedExactRead(JvmType.Int, _sentinel, _ev))

    /**
     * Returns an `Async` that produces the next `Long`, or `_sentinel`, like
     * [[Reader.SyncReader#readLong]]. No sentinel is safe, since an element
     * equal to it is produced as is; use [[readLongs]] with a one-element array
     * to keep every value. On a reader of another primitive lane that does not
     * implement it, the `Async` fails with `UnsupportedOperationException`.
     */
    def readLong(_sentinel: Long)(implicit _ev: Elem <:< Long): Async[Long] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream)
          .map(value => if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Long])
      else Async.fail(unsupportedExactRead(JvmType.Long, _sentinel, _ev))

    /**
     * Returns an `Async` that produces the next `Float` as a `Double`, or
     * `_sentinel`, like [[Reader.SyncReader#readFloat]]. Choose a sentinel that
     * no `Float` widens to, such as `Double.MaxValue`, and no element can be
     * mistaken for the end. On a reader of another primitive lane that does not
     * implement it, the `Async` fails with `UnsupportedOperationException`.
     */
    def readFloat(_sentinel: Double)(implicit _ev: Elem <:< Float): Async[Double] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream).map(value =>
          if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Float].toDouble
        )
      else Async.fail(unsupportedExactRead(JvmType.Float, _sentinel, _ev))

    /**
     * Returns an `Async` that produces the next `Double`, or `_sentinel`, like
     * [[Reader.SyncReader#readDouble]]. No sentinel is safe, since an element
     * equal to it is produced as is; use [[readDoubles]] with a one-element
     * array to keep every value. On a reader of another primitive lane that
     * does not implement it, the `Async` fails with
     * `UnsupportedOperationException`.
     */
    def readDouble(_sentinel: Double)(implicit _ev: Elem <:< Double): Async[Double] =
      if (jvmType eq JvmType.AnyRef)
        read[Any](EndOfStream)
          .map(value => if (value.asInstanceOf[AnyRef] eq EndOfStream) _sentinel else value.asInstanceOf[Double])
      else Async.fail(unsupportedExactRead(JvmType.Double, _sentinel, _ev))

    private def bulk[A](length: Int)(pull: Int => Async[Boolean]): Async[Int] =
      if (length == 0) Async.succeed(0)
      else {
        def loop(i: Int, budget: Int): Async[Int] =
          pull(i).flatMap(ok =>
            if (!ok) Async.succeed(if (i == 0) -1 else i)
            else if (i + 1 == length) Async.succeed(i + 1)
            else if (tryReadable ne Available) Async.succeed(i + 1)
            else if (budget > 0) loop(i + 1, budget - 1)
            else Async.reschedule(() => loop(i + 1, 255))
          )
        loop(0, 255)
      }

    /**
     * Returns an `Async` that reads up to `length` `Byte` elements into `dest`,
     * starting at `offset`, and produces the number read, `-1` at the end, or
     * `0` when `length` is `0`. After the first element it stops as soon as the
     * reader cannot say that another one is ready, so the default reads one
     * element per call on a reader such as [[Reader.unfoldAsync]]. An invalid
     * range fails the `Async` with `IndexOutOfBoundsException` (or
     * `NullPointerException`).
     *
     * A known bug: on a reader on the `AnyRef` lane, such as a custom reader
     * that does not override `jvmType`, it throws `IllegalStateException` at
     * the call site instead of unboxing.
     */
    def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Elem <:< Byte): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        bulk(length)(i =>
          readBytePhysical().map(v =>
            if (v < 0) false
            else { dest(offset + i) = v.toByte; true }
          )
        )
      )

    /**
     * Returns an `Async` that reads up to `length` `Int` elements into `dest`,
     * starting at `offset`, and produces the number read, `-1` at the end, or
     * `0` when `length` is `0`. After the first element it stops as soon as the
     * reader cannot say that another one is ready, so the default reads one
     * element per call on a reader such as [[Reader.unfoldAsync]]. An invalid
     * range fails the `Async` with `IndexOutOfBoundsException` (or
     * `NullPointerException`).
     *
     * A known bug: on a reader on the `AnyRef` lane, such as a custom reader
     * that does not override `jvmType`, it throws `IllegalStateException` at
     * the call site instead of unboxing.
     */
    def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Elem <:< Int): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        bulk(length)(i =>
          readIntPhysical(Long.MinValue).map(v =>
            if (v == Long.MinValue) false
            else { dest(offset + i) = v.toInt; true }
          )
        )
      )

    /**
     * Returns an `Async` that reads up to `length` `Long` elements into `dest`,
     * starting at `offset`, and produces the number read, `-1` at the end, or
     * `0` when `length` is `0`. After the first element it stops as soon as the
     * reader cannot say that another one is ready, so the default reads one
     * element per call on a reader such as [[Reader.unfoldAsync]]. An invalid
     * range fails the `Async` with `IndexOutOfBoundsException` (or
     * `NullPointerException`).
     *
     * The count reports the end separately from the data, so every `Long` stays
     * readable: with a one-element array, it is the pull to use when no
     * sentinel is safe for [[readLong]].
     */
    def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Elem <:< Long): Async[Int] =
      if (jvmType eq JvmType.AnyRef)
        validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
          bulk(length)(i =>
            read[Any](EndOfStream).map(value =>
              if (value.asInstanceOf[AnyRef] eq EndOfStream) false
              else { dest(offset + i) = value.asInstanceOf[Long]; true }
            )
          )
        )
      else Async.fail(unsupportedExactRead(JvmType.Long, dest, offset, length, ev))

    /**
     * Returns an `Async` that reads up to `length` `Float` elements into
     * `dest`, starting at `offset`, and produces the number read, `-1` at the
     * end, or `0` when `length` is `0`. After the first element it stops as
     * soon as the reader cannot say that another one is ready, so the default
     * reads one element per call on a reader such as [[Reader.unfoldAsync]]. An
     * invalid range fails the `Async` with `IndexOutOfBoundsException` (or
     * `NullPointerException`).
     *
     * A known bug: on a reader on the `AnyRef` lane, such as a custom reader
     * that does not override `jvmType`, it throws `IllegalStateException` at
     * the call site instead of unboxing.
     */
    def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: Elem <:< Float): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        bulk(length)(i =>
          readFloatPhysical(Double.MaxValue).map(v =>
            if (v == Double.MaxValue) false
            else { dest(offset + i) = v.toFloat; true }
          )
        )
      )

    /**
     * Returns an `Async` that reads up to `length` `Double` elements into
     * `dest`, starting at `offset`, and produces the number read, `-1` at the
     * end, or `0` when `length` is `0`. After the first element it stops as
     * soon as the reader cannot say that another one is ready, so the default
     * reads one element per call on a reader such as [[Reader.unfoldAsync]]. An
     * invalid range fails the `Async` with `IndexOutOfBoundsException` (or
     * `NullPointerException`).
     *
     * The count reports the end separately from the data, so every `Double`
     * stays readable: with a one-element array, it is the pull to use when no
     * sentinel is safe for [[readDouble]].
     */
    def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Elem <:< Double): Async[Int] =
      if (jvmType eq JvmType.AnyRef)
        validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
          bulk(length)(i =>
            read[Any](EndOfStream).map(value =>
              if (value.asInstanceOf[AnyRef] eq EndOfStream) false
              else { dest(offset + i) = value.asInstanceOf[Double]; true }
            )
          )
        )
      else Async.fail(unsupportedExactRead(JvmType.Double, dest, offset, length, ev))

    private[streams] def pollInt(
      sentinel: Long,
      onComplete: Runnable,
      observer: Async.LongStepFold[Unit]
    ): Unit = {
      val read     = readIntPhysical(sentinel)
      val observed = read match {
        case pending: Pollable[?] =>
          try pending.asInstanceOf[Pollable[Long]].poll(onComplete)
          catch { case failure: Throwable => Async.fail(failure) }
        case _ => read
      }
      Async.foldLongStep(observed)(observer)
    }

    private[streams] final def readBooleanPhysical(sentinel: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Boolean)
      asInstanceOf[AsyncReader[Boolean]].readBoolean(sentinel)
    }

    private[streams] final def readBytePhysical(): Async[Int] = {
      requirePhysicalLane(JvmType.Byte)
      readByte()
    }

    private[streams] final def readBytesPhysical(dest: Array[Byte], offset: Int, length: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Byte)
      asInstanceOf[AsyncReader[Byte]].readBytes(dest, offset, length)
    }

    private[streams] final def readCharPhysical(sentinel: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Char)
      asInstanceOf[AsyncReader[Char]].readChar(sentinel)
    }

    private[streams] final def readDoublePhysical(sentinel: Double): Async[Double] = {
      requirePhysicalLane(JvmType.Double)
      asInstanceOf[AsyncReader[Double]].readDouble(sentinel)
    }

    private[streams] final def readDoublesPhysical(dest: Array[Double], offset: Int, length: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Double)
      asInstanceOf[AsyncReader[Double]].readDoubles(dest, offset, length)
    }

    private[streams] final def readFloatPhysical(sentinel: Double): Async[Double] = {
      requirePhysicalLane(JvmType.Float)
      asInstanceOf[AsyncReader[Float]].readFloat(sentinel)
    }

    private[streams] final def readFloatsPhysical(dest: Array[Float], offset: Int, length: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Float)
      asInstanceOf[AsyncReader[Float]].readFloats(dest, offset, length)
    }

    private[streams] final def readIntPhysical(sentinel: Long): Async[Long] = {
      requirePhysicalLane(JvmType.Int)
      asInstanceOf[AsyncReader[Int]].readInt(sentinel)
    }

    private[streams] final def pollIntPhysical(
      sentinel: Long,
      onComplete: Runnable,
      observer: Async.LongStepFold[Unit]
    ): Unit = {
      requirePhysicalLane(JvmType.Int)
      asInstanceOf[AsyncReader[Int]].pollInt(sentinel, onComplete, observer)
    }

    private[streams] final def readIntsPhysical(dest: Array[Int], offset: Int, length: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Int)
      asInstanceOf[AsyncReader[Int]].readInts(dest, offset, length)
    }

    private[streams] final def readLongPhysical(sentinel: Long): Async[Long] = {
      requirePhysicalLane(JvmType.Long)
      asInstanceOf[AsyncReader[Long]].readLong(sentinel)
    }

    private[streams] final def readLongsPhysical(dest: Array[Long], offset: Int, length: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Long)
      asInstanceOf[AsyncReader[Long]].readLongs(dest, offset, length)
    }

    private[streams] final def readShortPhysical(sentinel: Int): Async[Int] = {
      requirePhysicalLane(JvmType.Short)
      asInstanceOf[AsyncReader[Short]].readShort(sentinel)
    }

    /**
     * Returns an `Async` that rewinds this reader to its start, like
     * [[Reader.SyncReader#reset]]. The default fails with
     * `UnsupportedOperationException`; [[Reader.unfoldAsync]] restarts from its
     * initial state.
     */
    def reset(): Async[Unit] = Async.fail(new UnsupportedOperationException("This reader does not support reset"))

    /**
     * Returns an `Async` that asks this reader to produce at most `n` more
     * elements itself, like [[Reader.SyncReader#setLimit]]. The default
     * produces `false` and changes nothing.
     */
    def setLimit(n: Long): Async[Boolean] = { val _ = n; Async.succeed(false) }

    /**
     * Returns an `Async` that asks this reader to restart whenever it would
     * end, like [[Reader.SyncReader#setRepeat]]. The default produces `false`
     * and changes nothing.
     */
    def setRepeat(): Async[Boolean] = Async.succeed(false)

    /**
     * Returns an `Async` that asks this reader to drop its next `n` elements
     * itself, like [[Reader.SyncReader#setSkip]]. The default produces `false`
     * and changes nothing.
     */
    def setSkip(n: Long): Async[Boolean] = { val _ = n; Async.succeed(false) }

    /**
     * Returns an `Async` that reads and discards up to `n` elements, stopping
     * early at the end, and does nothing when `n <= 0`.
     */
    def skip(n: Long): Async[Unit] =
      if (n <= 0) Async.succeed(())
      else
        jvmType match {
          case JvmType.Boolean =>
            def loop(left: Long, budget: Int): Async[Unit] =
              readBooleanPhysical(-1).flatMap(v => if (v < 0) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case JvmType.Byte =>
            def loop(left: Long, budget: Int): Async[Unit] =
              readBytePhysical().flatMap(v => if (v < 0) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case JvmType.Char =>
            def loop(left: Long, budget: Int): Async[Unit] =
              readCharPhysical(-1).flatMap(v => if (v < 0) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case JvmType.Short =>
            def loop(left: Long, budget: Int): Async[Unit] = readShortPhysical(Int.MinValue)
              .flatMap(v => if (v == Int.MinValue) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case JvmType.Int =>
            def loop(left: Long, budget: Int): Async[Unit] = readIntPhysical(Long.MinValue)
              .flatMap(v => if (v == Long.MinValue) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case JvmType.Long =>
            def loop(left: Long, budget: Int): Async[Unit] = readLongsPhysical(longScratch, 0, 1)
              .flatMap(v => if (v < 0) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case JvmType.Float =>
            def loop(left: Long, budget: Int): Async[Unit] = readFloatPhysical(Double.MaxValue)
              .flatMap(v => if (v == Double.MaxValue) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case JvmType.Double =>
            def loop(left: Long, budget: Int): Async[Unit] = readDoublesPhysical(doubleScratch, 0, 1)
              .flatMap(v => if (v < 0) Async.succeed(()) else continueSkip(left, budget)(loop))
            loop(n, 255)
          case _ =>
            def loop(left: Long, budget: Int): Async[Unit] = read[Any](EndOfStream).flatMap(v =>
              if (v.asInstanceOf[AnyRef] eq EndOfStream) Async.succeed(()) else continueSkip(left, budget)(loop)
            )
            loop(n, 255)
        }

    private def continueSkip(left: Long, budget: Int)(loop: (Long, Int) => Async[Unit]): Async[Unit] =
      if (left <= 1) Async.succeed(())
      else if (budget > 0) loop(left - 1, budget - 1)
      else Async.reschedule(() => loop(left - 1, 255))

    private def requirePhysicalLane(expected: JvmType): Unit =
      if (jvmType ne expected)
        throw new IllegalStateException(s"Reader physical lane $jvmType cannot be pulled as $expected")

    private def unsupportedExactRead(expected: JvmType, ignored: Any*): UnsupportedOperationException = {
      val _ = ignored
      new UnsupportedOperationException(
        s"Reader with physical lane $jvmType does not implement exact $expected pulling"
      )
    }
  }

  private[io] trait AsyncToSyncView[+A] { self: SyncReader[A] =>

    /** The asynchronous reader this synchronous view reads from. */
    def source: AsyncReader[A]
  }

  private[streams] trait AsyncToSyncTestView { self: SyncReader[_] =>
    private[streams] def afterCloseCaptureForTest(hook: () => Unit): Unit
    private[streams] def beforeAwaitCloseInterruptForTest(hook: () => Unit): Unit
  }

  private[io] trait SyncToAsyncView[+A] { self: AsyncReader[A] =>

    /** The synchronous reader this asynchronous view reads from. */
    def source: SyncReader[A]
  }

  private object SynchronousCloseFold extends Async.StepFold[Unit, Unit] {
    def success(value: Unit): Unit                      = value
    def failure(cause: Throwable): Unit                 = throw cause
    override def trustedFailure(cause: Throwable): Unit = throw cause
    def pending(pollable: Pollable[Unit]): Unit         =
      throw new IllegalStateException("synchronous reader close unexpectedly suspended")
  }

  private[streams] final class MemoizedClose(
    onClaim: () => Unit,
    close0: () => Async[Unit],
    synchronous: Boolean = false,
    isReentrant: () => Boolean = () => false
  ) {
    private var result: Completer[Unit]        = null
    private var constructingThread: Thread     = null
    private var closeRunning: Async.Running[_] = null

    private def claim(): Async[Unit] = {
      val (completion, leader, reentrant) = synchronized {
        if (result eq null) {
          result = new Completer[Unit]
          constructingThread = Thread.currentThread()
          onClaim()
          (result, true, isReentrant())
        } else {
          val nested = isReentrant() || (constructingThread eq Thread.currentThread()) ||
            ((closeRunning ne null) && closeRunning.isDriverThread)
          (result, false, nested)
        }
      }
      if (leader) {
        if (synchronous) {
          try {
            Async.foldStep(close0())(SynchronousCloseFold)
            completion.succeed(())
          } catch { case cause: Throwable => completion.fail(cause) }
          finally synchronized { constructingThread = null }
        } else {
          val closeEffect =
            try close0()
            catch { case cause: Throwable => Async.fail(cause) }
            finally synchronized { constructingThread = null }
          val publish = closeEffect.foldCause { cause =>
            completion.fail(cause); ()
          } { _ => completion.succeed(()); () }
          Async.startRegistered(publish)(started => synchronized { closeRunning = started })
        }
      }
      if (reentrant) Async.succeed(()) else completion
    }

    def close(): Async[Unit] =
      Async.deferCancelable(() => claim(), () => ()).flatten

    /**
     * Claims close ownership and performs the synchronous state transition
     * before returning.
     */
    private[streams] def closeClaimed(): Async[Unit] = claim()
  }

  private final class SyncToAsyncReader[A](val source: SyncReader[A]) extends AsyncReader[A] with SyncToAsyncView[A] {
    private val sourceLock                               = new AnyRef
    private val longScratch                              = new Array[Long](1)
    private val doubleScratch                            = new Array[Double](1)
    @volatile private var closed                         = false
    @volatile private var eof                            = false
    private var epoch                                    = 0L
    private var resetting                                = false
    private var compensatingReset                        = false
    private var compensationPreviousOwner: MemoizedClose = null
    private var compensationEof                          = false
    private var closing                                  = false
    override def jvmType: JvmType                        = source.jvmType
    private def makeCloseOwner(): MemoizedClose          =
      new MemoizedClose(
        () => synchronized { closed = true; closing = true; epoch += 1 },
        () => {
          val closingReset       = synchronized(resetting && !compensatingReset)
          var failure: Throwable =
            try { source.close(); null }
            catch { case cause: Throwable => cause }
          sourceLock.synchronized(())
          if (closingReset)
            try source.close()
            catch {
              case cause: Throwable =>
                if (failure eq null) failure = cause
                else if (failure ne cause) failure.addSuppressed(cause)
            }
          synchronized {
            closing = false
            if (compensatingReset) {
              resetting = false
              compensatingReset = false
              closed = true
              eof = compensationEof
              closeOwner = compensationPreviousOwner
              compensationPreviousOwner = null
            } else if (closingReset) resetting = false
          }
          if (failure eq null) Async.succeed(()) else Async.fail(failure)
        },
        synchronous = true
      )
    private var closeOwner = makeCloseOwner()
    private def deferred[B](closedValue: => B, allowEof: Boolean = false)(body: => B)(
      onCommit: B => Unit = (_: B) => ()
    ): Async[B] = {
      var operationOwner: MemoizedClose = null
      Async
        .deferCancelableWithCleanup(
          () => {
            val token = synchronized {
              if (closed || (!allowEof && eof)) -1L
              else {
                operationOwner = closeOwner
                epoch
              }
            }
            if (token < 0L) closedValue
            else {
              val result: Either[Throwable, Option[B]] =
                try
                  sourceLock.synchronized {
                    if (synchronized(closed || epoch != token)) Right(None)
                    else Right(Some(body))
                  }
                catch { case cause: Throwable => Left(cause) }
              val outcome = synchronized {
                if (closed || epoch != token) Right(None)
                else
                  result match {
                    case Left(cause)        => Left(cause)
                    case Right(None)        => Right(None)
                    case Right(Some(value)) =>
                      onCommit(value)
                      Right(Some(value))
                  }
              }
              outcome match {
                case Left(cause)        => throw cause
                case Right(None)        => closedValue
                case Right(Some(value)) => value
              }
            }
          },
          () => synchronized(if (operationOwner eq null) closeOwner else operationOwner).close()
        )
        .catchAll {
          case error: StreamError if error.isTrusted => Async.failTrusted(error)
          case cause                                 => Async.fail(cause)
        }
    }
    def close(): Async[Unit]                                = synchronized(closeOwner).close()
    def isClosed: Async[Boolean]                            = deferred(true)(source.isClosed)()
    def readable(): Async[Boolean]                          = deferred(false)(source.readable())()
    override private[streams] def tryReadable: Availability = synchronized {
      if (closed || eof || resetting || closing) Unavailable else source.tryReadable
    }
    def read[B >: A](sentinel: B): Async[B] = deferred(sentinel) {
      source.read(sentinel)
    }(value => if (value.asInstanceOf[AnyRef] eq sentinel.asInstanceOf[AnyRef]) eof = true)
    override def readAll[B >: A](): Async[Chunk[B]] = deferred[Chunk[B]](Chunk.empty) {
      source.readAll[B]()
    }(chunk => if (chunk.isEmpty) eof = true)
    override def readN[B >: A](n: Int): Async[Chunk[B]] = deferred[Chunk[B]](Chunk.empty) {
      source.readN[B](n)
    }(chunk => if (n > 0 && chunk.isEmpty) eof = true)
    override def readUpToN[B >: A](n: Int): Async[Chunk[B]] = deferred[Chunk[B]](Chunk.empty) {
      source.readUpToN[B](n)
    }(chunk => if (n > 0 && chunk.isEmpty) eof = true)
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      deferred(sentinel)(source.readBooleanPhysical(sentinel))(value => if (value == sentinel) eof = true)
    override def readByte(): Async[Int]                                       = deferred(-1)(source.readBytePhysical())(value => if (value < 0) eof = true)
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      deferred(sentinel)(source.readCharPhysical(sentinel))(value => if (value == sentinel) eof = true)
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      deferred(sentinel)(source.readShortPhysical(sentinel))(value => if (value == sentinel) eof = true)
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      deferred(sentinel)(source.readIntPhysical(sentinel))(value => if (value == sentinel) eof = true)
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      readLongsPhysical(longScratch, 0, 1).map(count => if (count < 0) sentinel else longScratch(0))
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      deferred(sentinel)(source.readFloatPhysical(sentinel))(value => if (value == sentinel) eof = true)
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      readDoublesPhysical(doubleScratch, 0, 1).map(count => if (count < 0) sentinel else doubleScratch(0))
    override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        deferred(if (length == 0) 0 else -1)(source.readBytesPhysical(dest, offset, length))(value =>
          if (length > 0 && value < 0) eof = true
        )
      )
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        deferred(if (length == 0) 0 else -1)(source.readIntsPhysical(dest, offset, length))(value =>
          if (length > 0 && value < 0) eof = true
        )
      )
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        deferred(if (length == 0) 0 else -1)(source.readLongsPhysical(dest, offset, length))(value =>
          if (length > 0 && value < 0) eof = true
        )
      )
    override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        deferred(if (length == 0) 0 else -1)(source.readFloatsPhysical(dest, offset, length))(value =>
          if (length > 0 && value < 0) eof = true
        )
      )
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        deferred(if (length == 0) 0 else -1)(source.readDoublesPhysical(dest, offset, length))(value =>
          if (length > 0 && value < 0) eof = true
        )
      )
    override def reset(): Async[Unit] = {
      var operationOwner: MemoizedClose = null
      var cancelRequested               = false
      Async
        .deferCancelableWithCleanup(
          () => {
            val state: Either[Throwable, Option[(Long, Boolean, Boolean, MemoizedClose)]] = synchronized {
              if (cancelRequested) Right(None)
              else if (resetting) Left(new IOException("Reader reset is already in progress"))
              else if (closing) Left(new IOException("Reader close is in progress"))
              else {
                resetting = true
                val previousClosed = closed
                val previousEof    = eof
                val previousOwner  = closeOwner
                if (previousClosed) closeOwner = makeCloseOwner()
                operationOwner = closeOwner
                epoch += 1
                closed = true
                Right(Some((epoch, previousClosed, previousEof, previousOwner)))
              }
            }
            state match {
              case Left(cause)                                            => Async.fail(cause)
              case Right(None)                                            => Async.succeed(())
              case Right(Some((token, wasClosed, wasEof, previousOwner))) =>
                val result: Either[Throwable, Unit] =
                  try
                    sourceLock.synchronized {
                      if (synchronized(epoch == token)) source.reset()
                      Right(())
                    }
                  catch { case cause: Throwable => Left(cause) }
                result match {
                  case Right(_) =>
                    val outcome = synchronized {
                      if (epoch != token) Left(new IOException("Reader was closed during reset"))
                      else {
                        resetting = false
                        closed = false
                        eof = false
                        Right(())
                      }
                    }
                    outcome match {
                      case Left(cause) => Async.fail(cause)
                      case Right(_)    => Async.succeed(())
                    }
                  case Left(resetFailure) =>
                    val failureAction = synchronized {
                      if (epoch != token) 0
                      else if (wasClosed) {
                        compensatingReset = true
                        compensationPreviousOwner = previousOwner
                        compensationEof = wasEof
                        1
                      } else {
                        resetting = false
                        closed = wasClosed
                        eof = wasEof
                        2
                      }
                    }
                    failureAction match {
                      case 0 => Async.fail(new IOException("Reader was closed during reset"))
                      case 2 => Async.fail(resetFailure)
                      case _ =>
                        operationOwner
                          .close()
                          .foldCause[Either[Throwable, Unit]](cause => Left(cause))(_ => Right(()))
                          .flatMap { closeResult =>
                            closeResult match {
                              case Left(closeFailure) if closeFailure ne resetFailure =>
                                resetFailure.addSuppressed(closeFailure)
                              case _ => ()
                            }
                            Async.fail(resetFailure)
                          }
                    }
                }
            }
          },
          () =>
            synchronized {
              cancelRequested = true
              operationOwner
            } match {
              case null  => Async.succeed(())
              case owner => owner.close()
            }
        )
        .flatten
    }
    override def setLimit(n: Long): Async[Boolean] =
      deferred(throw new IOException("Reader is closed"), allowEof = true)(source.setLimit(n))()
    override def setRepeat(): Async[Boolean] = deferred(throw new IOException("Reader is closed"), allowEof = true) {
      source.setRepeat()
    }(result => if (result) eof = false)
    override def setSkip(n: Long): Async[Boolean] =
      deferred(throw new IOException("Reader is closed"), allowEof = true)(source.setSkip(n))()
    override def skip(n: Long): Async[Unit] =
      deferred(throw new IOException("Reader is closed"), allowEof = true)(source.skip(n))()
  }

  private final class ClosedControlException extends IOException("Reader is closed")

  private final class AsyncConcatReader[A] private (
    rawHead: AsyncReader[A],
    tails: Vector[() => Async[Reader[A]]],
    outType: JvmType
  ) extends AsyncReader[A] {
    def this(head: AsyncReader[A], makeTail: () => Async[Reader[A]], outType: JvmType) =
      this(head, Vector(makeTail), outType)

    private val head: AsyncReader[A]                 = normalizeAsyncChild(rawHead, outType)
    private var current: AsyncReader[A]              = head
    private var currentClose                         = segmentClose(head)
    private var tailIndex                            = 0
    private var started                              = false
    private var closed                               = false
    private var explicitlyClosed                     = false
    private var epoch                                = 0L
    private var failure: Throwable                   = null
    private var failureSet                           = false
    private var activeTransition: Pollable[Boolean]  = null
    private var closingTransition: Pollable[Boolean] = null
    private var activeReset: Pollable[Unit]          = null
    private var closingReset: Pollable[Unit]         = null
    private var resetting                            = false
    private var beforeNextHook: () => Unit           = null
    private val longScratch                          = new Array[Long](1)
    private val doubleScratch                        = new Array[Double](1)
    override def jvmType: JvmType                    = outType

    private[streams] def beforeNextForTest(hook: () => Unit): Unit = synchronized {
      beforeNextHook = hook
    }

    private def runBeforeNextHook(): Unit = {
      val hook = synchronized {
        val current = beforeNextHook
        beforeNextHook = null
        current
      }
      if (hook ne null) hook()
    }

    def appendIfPristine(next: () => Async[Reader[A]]): Option[AsyncConcatReader[A]] = synchronized {
      if (started || closed || failureSet || resetting || (activeTransition ne null) || (activeReset ne null)) None
      else Some(new AsyncConcatReader[A](head, tails :+ next, outType))
    }

    private def segmentClose(reader: AsyncReader[A]): MemoizedClose =
      new MemoizedClose(() => (), () => reader.close())

    private def recordFailure(cause: Throwable): Unit = synchronized {
      if (!failureSet) {
        failure = cause
        failureSet = true
      }
    }

    private val ownerClose = new MemoizedClose(
      () =>
        synchronized {
          closed = true
          explicitlyClosed = true
          epoch += 1
          closingTransition = activeTransition
          closingReset = activeReset
        },
      () => {
        Async
          .bracketSync[Unit, Unit](
            () => (),
            _ => {
              val transitions = synchronized(List(closingTransition, closingReset).filter(_ ne null))
              val joined      = transitions.map { transition =>
                val cleanup = Async.cancelWithCleanup(transition)
                cleanup.either
              }
              Async.collectAll(joined).flatMap { results =>
                val failures =
                  results.collect { case Left(cause) if !cause.isInstanceOf[ClosedControlException] => cause }
                failures match {
                  case Nil                   => Async.succeed(())
                  case primary :: suppressed =>
                    if (primary ne null)
                      suppressed.foreach { cause =>
                        if ((cause ne null) && (cause ne primary)) primary.addSuppressed(cause)
                      }
                    Async.fail(primary)
                }
              }
            },
            _ => synchronized(currentClose).close()
          )
          .mapError { cause => recordFailure(cause); cause }
      }
    )

    private def promote(reader: Reader[A]): AsyncReader[A] = reader match {
      case sync: SyncReader[A @unchecked]   => normalizeSyncChild(sync, outType).toAsync
      case async: AsyncReader[A @unchecked] => normalizeAsyncChild(async, outType)
    }

    private def rememberFailure[B](effect: Async[B]): Async[B] =
      effect.mapError { cause =>
        if (!cause.isInstanceOf[ClosedControlException]) recordFailure(cause)
        cause
      }

    private def closeForReset(): Async[Unit] =
      Async
        .deferCancelable(
          () => {
            val (transitions, segment) = synchronized {
              closed = true
              epoch += 1
              val pending = List(activeTransition, activeReset).filter(_ ne null)
              activeTransition = null
              activeReset = null
              (pending, currentClose)
            }
            val joined = transitions.map(transition => Async.cancelWithCleanup(transition).either)
            Async.collectAll(joined).flatMap { results =>
              val failures = results.collect { case Left(cause) => cause }
              val cleanup  = failures match {
                case Nil                   => Async.succeed(())
                case primary :: suppressed =>
                  if (primary ne null)
                    suppressed.foreach { cause =>
                      if ((cause ne null) && (cause ne primary)) primary.addSuppressed(cause)
                    }
                  Async.fail(primary)
              }
              Async.bracketSync[Unit, Unit](() => (), _ => cleanup, _ => segment.close())
            }
          },
          () => ()
        )
        .flatten
        .mapError { cause => recordFailure(cause); cause }

    private def resetClose(reader: AsyncReader[A]): Async[Unit] = reader match {
      case concat: AsyncConcatReader[A @unchecked] => concat.closeForReset()
      case _                                       => reader.close()
    }

    private def finishFinalSegment(): Async[Boolean] = {
      val segment = synchronized {
        closed = true
        epoch += 1
        currentClose
      }
      rememberFailure(segment.close()).map(_ => false)
    }

    private def next(expectedReader: AsyncReader[A], expectedEpoch: Long): Async[Boolean] = {
      type NextState = Either[Async[Boolean], (Long, MemoizedClose, () => Async[Reader[A]])]
      val state: NextState = synchronized {
        if (failureSet) Left(Async.fail(failure))
        else if (closed || epoch != expectedEpoch || (current ne expectedReader)) Left(Async.succeed(false))
        else if (tailIndex >= tails.length) Left(finishFinalSegment())
        else {
          val next = tails(tailIndex)
          tailIndex += 1
          Right((epoch, currentClose, next))
        }
      }
      state match {
        case Left(done)                                 => done
        case Right((expected, previousClose, nextTail)) =>
          rememberFailure(previousClose.close().flatMap { _ =>
            val mayStart = synchronized(!closed && epoch == expected)
            if (!mayStart) Async.succeed(false)
            else {
              @volatile var installed: Reader[A] = null
              val rawTransition                  = Async
                .deferCancelable(
                  () =>
                    Async.bracketAsync[Reader[A], Boolean](
                      () => {
                        try nextTail()
                        catch { case t: Throwable => Async.fail(t) }
                      },
                      reader => {
                        val promoted = promote(reader)
                        val accepted = synchronized {
                          if (!closed && epoch == expected) {
                            current = promoted
                            currentClose = segmentClose(promoted)
                            installed = reader
                            true
                          } else false
                        }
                        Async.succeed(accepted)
                      },
                      reader =>
                        if (installed.asInstanceOf[AnyRef] eq reader.asInstanceOf[AnyRef]) Async.succeed(())
                        else promote(reader).close()
                    ),
                  () => ()
                )
                .flatten
                .asInstanceOf[Pollable[Boolean]]
              val transition = Async.cancelTo(rawTransition, () => Async.succeed(false))
              val accepted   = synchronized {
                if (!closed && epoch == expected) { activeTransition = transition; true }
                else false
              }
              if (!accepted) Async.cancelWithCleanup(transition).map(_ => false)
              else
                transition.mapError { cause => recordFailure(cause); cause }
                  .tap(_ =>
                    Async.succeed { synchronized { if (activeTransition eq transition) activeTransition = null }; () }
                  )
            }
          })
      }
    }

    private def pull[B](eof: B)(read0: AsyncReader[A] => Async[B])(isEof: B => Boolean): Async[B] =
      lazyEffect(Async.succeed(eof)) { (reader, token) =>
        val delegated =
          try read0(reader)
          catch { case cause: Throwable => Async.fail(cause) }
        delegated.map[Either[Throwable, B]](Right(_)).catchAll(cause => Async.succeed(Left(cause))).flatMap { result =>
          val accepted = synchronized(!closed && epoch == token && (current eq reader))
          if (!accepted) Async.succeed(eof)
          else
            result match {
              case Left(error: StreamError) if error.isTrusted => Async.failTrusted(error)
              case Left(cause)                                 => Async.fail(cause)
              case Right(value)                                =>
                if (!isEof(value)) Async.succeed(value)
                else {
                  runBeforeNextHook()
                  next(reader, token).flatMap(if (_) pull(eof)(read0)(isEof) else Async.succeed(eof))
                }
            }
        }
      }

    def read[B >: A](sentinel: B): Async[B] =
      pull(sentinel)(_.read(sentinel))((value: B) => value.asInstanceOf[AnyRef] eq sentinel.asInstanceOf[AnyRef])
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      pull(sentinel)(_.readBooleanPhysical(sentinel))(_ == sentinel)
    override def readByte(): Async[Int] =
      pull(-1)(_.readBytePhysical())(_ < 0)
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      pull(sentinel)(_.readCharPhysical(sentinel))(_ == sentinel)
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      pull(sentinel)(_.readShortPhysical(sentinel))(_ == sentinel)
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      intPull.prepare(sentinel)
    override private[streams] def pollInt(
      sentinel: Long,
      onComplete: Runnable,
      observer: Async.LongStepFold[Unit]
    ): Unit = {
      var reader: AsyncReader[A] = null
      var token                  = 0L
      val state                  = synchronized {
        if (failureSet) 0
        else if (closed) 1
        else {
          started = true
          reader = current
          token = epoch
          2
        }
      }
      if (state == 0) observer.failureLong(failure)
      else if (state == 1) observer.successLong(sentinel)
      else {
        intPoll.prepare(reader, token, sentinel, onComplete, observer)
        try reader.pollIntPhysical(sentinel, onComplete, intPoll)
        catch { case failure: Throwable => intPoll.failureLong(failure) }
      }
    }
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      readLongsPhysical(longScratch, 0, 1).map(count => if (count < 0) sentinel else longScratch(0))
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      pull(sentinel)(_.readFloatPhysical(sentinel))(_ == sentinel)
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      readDoublesPhysical(doubleScratch, 0, 1).map(count => if (count < 0) sentinel else doubleScratch(0))
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      pull(if (length == 0) 0 else -1)(_.readLongsPhysical(dest, offset, length))(_ < 0)
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      pull(if (length == 0) 0 else -1)(_.readDoublesPhysical(dest, offset, length))(_ < 0)
    override def readUpToN[B >: A](n: Int): Async[Chunk[B]] =
      if (n <= 0) Async.succeed(Chunk.empty)
      else pull[Chunk[B]](Chunk.empty[B])(_.readUpToN[B](n))(_.isEmpty)

    private val intPull = new IntPull
    private val intPoll = new IntPoll

    private final class IntPoll extends Async.LongStepFold[Unit] {
      private var downstream: Async.LongStepFold[Unit] = null
      private var onComplete: Runnable                 = null
      private var reader: AsyncReader[A]               = null
      private var sentinel                             = 0L
      private var token                                = 0L

      def failureLong(failure: Throwable): Unit =
        if (accepted) downstream.failureLong(failure) else downstream.successLong(sentinel)

      def pendingLong(pollable: Pollable[Long]): Unit =
        downstream.pendingLong(
          pollable
            .flatMap(value => continueInt(reader, token, value, sentinel))
            .asInstanceOf[Pollable[Long]]
        )

      def successLong(value: Long): Unit =
        if (!accepted) downstream.successLong(sentinel)
        else if (value != sentinel) downstream.successLong(value)
        else {
          val continued = continueInt(reader, token, value, sentinel)
          val observed  = continued match {
            case pending: Pollable[?] =>
              try pending.asInstanceOf[Pollable[Long]].poll(onComplete)
              catch { case failure: Throwable => Async.fail(failure) }
            case _ => continued
          }
          Async.foldLongStep(observed)(downstream)
        }

      override def trustedFailureLong(failure: Throwable): Unit =
        if (accepted) downstream.trustedFailureLong(failure) else downstream.successLong(sentinel)

      def prepare(
        current: AsyncReader[A],
        expected: Long,
        end: Long,
        wake: Runnable,
        observer: Async.LongStepFold[Unit]
      ): Unit = {
        reader = current
        token = expected
        sentinel = end
        onComplete = wake
        downstream = observer
      }

      private def accepted: Boolean =
        AsyncConcatReader.this.synchronized(!closed && epoch == token && (current eq reader))
    }

    private final class IntPull extends Pollable[Long] with Async.LongStepFold[Unit] {
      private var cause: Throwable = null
      private var kind             = 2
      private var sentinel         = 0L
      private var trusted          = false
      private var value            = 0L

      def failureLong(failure: Throwable): Unit                 = { cause = failure; kind = 1; trusted = false }
      def pendingLong(pollable: Pollable[Long]): Unit           = { val _ = pollable; kind = 2 }
      def successLong(result: Long): Unit                       = { value = result; kind = 0 }
      override def trustedFailureLong(failure: Throwable): Unit = { cause = failure; kind = 1; trusted = true }

      def prepare(end: Long): Async[Long] = {
        sentinel = end
        this
      }

      def poll(onComplete: Runnable): Async[Long] = {
        val state = synchronized {
          if (failureSet) Left(failure)
          else if (closed) Right(None)
          else {
            started = true
            Right(Some((current, epoch)))
          }
        }
        state match {
          case Left(failure)                => Async.fail(failure)
          case Right(None)                  => Async.succeed(sentinel)
          case Right(Some((reader, token))) =>
            val delegated =
              try reader.readIntPhysical(sentinel)
              catch { case failure: Throwable => Async.fail(failure) }
            resetProbe()
            Async.foldLongStep(delegated)(this)
            if (kind == 0) continue(reader, token, value)
            else if (kind == 1) failed(reader, token)
            else delegated.flatMap(result => continue(reader, token, result))
        }
      }

      private def continue(reader: AsyncReader[A], token: Long, result: Long): Async[Long] =
        continueInt(reader, token, result, sentinel)

      private def failed(reader: AsyncReader[A], token: Long): Async[Long] = {
        val accepted = synchronized(!closed && epoch == token && (current eq reader))
        if (!accepted) Async.succeed(sentinel)
        else if (trusted) Async.failTrusted(cause)
        else Async.fail(cause)
      }

      private def resetProbe(): Unit = {
        cause = null
        kind = 2
        trusted = false
      }
    }

    private def continueInt(reader: AsyncReader[A], token: Long, result: Long, sentinel: Long): Async[Long] = {
      val accepted = synchronized(!closed && epoch == token && (current eq reader))
      if (!accepted) Async.succeed(sentinel)
      else if (result != sentinel) Async.succeed(result)
      else next(reader, token).flatMap(if (_) intPull.prepare(sentinel) else Async.succeed(sentinel))
    }

    private def lazyEffect[B](closedEffect: => Async[B])(f: (AsyncReader[A], Long) => Async[B]): Async[B] =
      Async
        .deferCancelable(
          () => {
            val state = synchronized {
              if (failureSet) Left(failure)
              else if (closed) Right(None)
              else {
                started = true
                Right(Some((current, epoch)))
              }
            }
            state match {
              case Left(cause)                  => Async.fail(cause)
              case Right(None)                  => closedEffect
              case Right(Some((reader, token))) =>
                f(reader, token)
            }
          },
          () => ()
        )
        .flatten
    override private[streams] def tryReadable: Availability = synchronized {
      if (closed || failureSet) Unavailable else current.tryReadable
    }
    def readable(): Async[Boolean] = lazyEffect(Async.succeed(false)) { (reader, token) =>
      val delegated =
        try reader.readable()
        catch { case cause: Throwable => Async.fail(cause) }
      delegated.either.flatMap { result =>
        val accepted = synchronized(!closed && epoch == token && (current eq reader))
        if (!accepted) Async.succeed(false) else result.fold(Async.fail, Async.succeed)
      }
    }
    def isClosed: Async[Boolean] =
      Async
        .deferCancelable(() => synchronized(if (failureSet) Async.fail(failure) else Async.succeed(closed)), () => ())
        .flatten
    def close(): Async[Unit]               = ownerClose.close()
    private def closedControl[B]: Async[B] = Async.fail(new ClosedControlException)
    override def reset(): Async[Unit]      = {
      type ResetState = Either[Throwable, Option[(Long, AsyncReader[A], MemoizedClose, Pollable[Boolean])]]
      var managed: Pollable[Unit] = null
      val managedRaw              = Async
        .bracketSync[ResetState, Unit](
          () =>
            synchronized {
              if (failureSet) Left(failure)
              else if (explicitlyClosed) Right(None)
              else if (resetting) Left(new IOException("Reader reset is already in progress"))
              else {
                started = true
                resetting = true
                epoch += 1
                closed = true
                val transition = activeTransition
                activeTransition = null
                activeReset = managed
                Right(Some((epoch, current, currentClose, transition)))
              }
            },
          {
            case Left(cause)                                                         => Async.fail(cause)
            case Right(None)                                                         => closedControl
            case Right(Some((expected, segmentReader, segment, previousTransition))) =>
              @volatile var installed   = false
              val cancellationHeadClose = new MemoizedClose(() => (), () => resetClose(head))
              val rejectedHeadClose     = new MemoizedClose(() => (), () => resetClose(head))
              val supervisedHeadReset   = Async.superviseResetUnit(
                () => head.reset(),
                () => cancellationHeadClose.close()
              )
              val rawReset = Async
                .bracketAsync[Unit, Unit](
                  () => {
                    val transitionClose =
                      if (previousTransition eq null) Async.succeed(())
                      else Async.cancelWithCleanup(previousTransition)
                    transitionClose.flatMap(_ =>
                      segmentReader match {
                        case concat: AsyncConcatReader[A @unchecked]
                            if concat.asInstanceOf[AnyRef] eq head.asInstanceOf[AnyRef] =>
                          concat.closeForReset()
                        case _ => segment.close()
                      }
                    )
                  },
                  _ =>
                    supervisedHeadReset.flatMap(_ =>
                      if (
                        synchronized {
                          if (!explicitlyClosed && epoch == expected) {
                            current = head
                            currentClose = segmentClose(head)
                            tailIndex = 0
                            closed = false
                            installed = true
                            true
                          } else false
                        }
                      ) Async.succeed(())
                      else closedControl
                    ),
                  _ => if (installed) Async.succeed(()) else rejectedHeadClose.close()
                )
                .asInstanceOf[Pollable[Unit]]
              rememberFailure(Async.shareCancellation(rawReset))
          },
          {
            case Right(Some(_)) =>
              Async.succeed {
                synchronized {
                  if (activeReset eq managed) activeReset = null
                  resetting = false
                }
              }
            case _ => Async.succeed(())
          }
        )
        .asInstanceOf[Pollable[Unit]]
      managed = Async.cancelTo(managedRaw, () => closedControl)
      managed
    }
    private[streams] def clearActiveResetForTest(): Unit = synchronized { activeReset = null }
    override def setLimit(n: Long): Async[Boolean]       = { val _ = n; Async.succeed(false) }
    override def setRepeat(): Async[Boolean]             = Async.succeed(false)
    override def setSkip(n: Long): Async[Boolean]        = { val _ = n; Async.succeed(false) }
    override def skip(n: Long): Async[Unit]              = super.skip(n)
  }

  private[streams] def clearConcatActiveResetForTest(reader: AsyncReader[_]): Unit = reader match {
    case concat: AsyncConcatReader[_] => concat.clearActiveResetForTest()
    case _                            => ()
  }

  private[streams] def beforeConcatNextForTest(reader: AsyncReader[_], hook: () => Unit): Unit = reader match {
    case concat: AsyncConcatReader[_] => concat.beforeNextForTest(hook)
    case _                            => ()
  }

  private final class AsyncReleaseReader[A](inner: AsyncReader[A], release: () => Async[Unit]) extends AsyncReader[A] {
    @volatile private var closed = false
    private val ownerClose       = new MemoizedClose(
      () => closed = true,
      () => Async.bracketSync[Unit, Unit](() => (), _ => inner.close(), _ => StreamError.callbackAsync(release()))
    )
    private def delegated[B](closedEffect: => Async[B])(effect: => Async[B]): Async[B] =
      Async
        .deferCancelable(
          () =>
            if (closed) closedEffect
            else {
              val delegated =
                try effect
                catch { case cause: Throwable => Async.fail(cause) }
              delegated.either.flatMap { result =>
                if (closed) closedEffect
                else result.fold(replayFailure, Async.succeed)
              }
            },
          () => ()
        )
        .flatten
    private def eof[B](value: B): Async[B]                                          = Async.succeed(value)
    private def control[B]: Async[B]                                                = Async.fail(new IOException("Reader is closed"))
    override def jvmType: JvmType                                                   = inner.jvmType
    def read[B >: A](sentinel: B): Async[B]                                         = delegated(eof(sentinel))(inner.read(sentinel))
    override def readAll[B >: A](): Async[Chunk[B]]                                 = delegated(eof(Chunk.empty[B]))(inner.readAll[B]())
    override def readN[B >: A](n: Int): Async[Chunk[B]]                             = delegated(eof(Chunk.empty[B]))(inner.readN[B](n))
    override def readUpToN[B >: A](n: Int): Async[Chunk[B]]                         = delegated(eof(Chunk.empty[B]))(inner.readUpToN[B](n))
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      delegated(eof(sentinel))(inner.readBooleanPhysical(sentinel))
    override def readByte(): Async[Int]                                       = delegated(eof(-1))(inner.readBytePhysical())
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      delegated(eof(sentinel))(inner.readCharPhysical(sentinel))
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      delegated(eof(sentinel))(inner.readShortPhysical(sentinel))
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      delegated(eof(sentinel))(inner.readIntPhysical(sentinel))
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      delegated(eof(sentinel))(inner.readLongPhysical(sentinel))
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      delegated(eof(sentinel))(inner.readFloatPhysical(sentinel))
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      delegated(eof(sentinel))(inner.readDoublePhysical(sentinel))
    override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        delegated(eof(if (length == 0) 0 else -1))(inner.readBytesPhysical(dest, offset, length))
      )
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        delegated(eof(if (length == 0) 0 else -1))(inner.readIntsPhysical(dest, offset, length))
      )
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        delegated(eof(if (length == 0) 0 else -1))(inner.readLongsPhysical(dest, offset, length))
      )
    override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        delegated(eof(if (length == 0) 0 else -1))(inner.readFloatsPhysical(dest, offset, length))
      )
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        delegated(eof(if (length == 0) 0 else -1))(inner.readDoublesPhysical(dest, offset, length))
      )
    def readable(): Async[Boolean]                 = delegated(eof(false))(inner.readable())
    def isClosed: Async[Boolean]                   = delegated(eof(true))(inner.isClosed)
    def close(): Async[Unit]                       = ownerClose.close()
    override def reset(): Async[Unit]              = delegated(control)(inner.reset())
    override def setLimit(n: Long): Async[Boolean] = delegated(control)(inner.setLimit(n))
    override def setRepeat(): Async[Boolean]       = delegated(control)(inner.setRepeat())
    override def setSkip(n: Long): Async[Boolean]  = delegated(control)(inner.setSkip(n))
    override def skip(n: Long): Async[Unit]        = delegated(control)(inner.skip(n))
  }

  private[streams] def beforeRepeatedAdmissionForTest(reader: AsyncReader[_], hook: () => Unit): Unit = reader match {
    case repeated: AsyncRepeated[_] => repeated.beforeAdmissionForTest(hook)
    case _                          => ()
  }

  private[streams] def beforeRepeatedLoopForTest(reader: AsyncReader[_], hook: () => Unit): Unit = reader match {
    case repeated: AsyncRepeated[_] => repeated.beforeLoopForTest(hook)
    case _                          => ()
  }

  private[streams] def beforeUnfoldRunRegistrationForTest(reader: AsyncReader[_], hook: () => Unit): Unit =
    reader match {
      case unfold: UnfoldAsync[_, _] => unfold.beforeRunRegistrationForTest(hook)
      case _                         => ()
    }

  private[streams] def beforeUnfoldPullAcquiredForTest(reader: AsyncReader[_], hook: () => Unit): Unit = reader match {
    case unfold: UnfoldAsync[_, _] => unfold.beforePullAcquiredForTest(hook)
    case _                         => ()
  }

  private[streams] def beforeUnfoldSettleForTest(reader: AsyncReader[_], hook: () => Unit): Unit = reader match {
    case unfold: UnfoldAsync[_, _] => unfold.beforeSettleForTest(hook)
    case _                         => ()
  }

  /**
   * Returns the reader that has no elements and is already closed: its pulls
   * return the end value (the sentinel, `-1` or an empty chunk), and `close()`
   * and `reset()` do nothing. A known bug: `readBytes`, `readInts` and
   * `readFloats` throw `IllegalStateException` instead of returning `-1`. Its
   * element type is `Nothing`, so it fits wherever any reader is expected.
   */
  def closed: SyncReader[Nothing] = ClosedReader

  /**
   * Returns a reader over the elements of `chunk`, in order. It is on the
   * primitive lane of `A` (for example `JvmType.Int` for a `Chunk[Int]`), so
   * its typed pulls read without boxing. It supports `reset()`, `setSkip` and
   * `setLimit`, but not `setRepeat`, and it reports `isClosed` as soon as the
   * last element is read.
   *
   * {{{
   * import zio.blocks.chunk.Chunk
   * import zio.blocks.streams.io.Reader
   *
   * val temperatures = Reader.fromChunk(Chunk(21.5, 22.0, 19.5))
   * temperatures.jvmType     // Double
   * temperatures.readAll[Double]() // Chunk(21.5,22.0,19.5)
   * }}}
   */
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): SyncReader[A] =
    jt.jvmType match {
      case JvmType.Int    => new FromChunkInt(chunk.asInstanceOf[Chunk[Int]]).asInstanceOf[SyncReader[A]]
      case JvmType.Long   => new FromChunkLong(chunk.asInstanceOf[Chunk[Long]]).asInstanceOf[SyncReader[A]]
      case JvmType.Float  => new FromChunkFloat(chunk.asInstanceOf[Chunk[Float]]).asInstanceOf[SyncReader[A]]
      case JvmType.Double => new FromChunkDouble(chunk.asInstanceOf[Chunk[Double]]).asInstanceOf[SyncReader[A]]
      case JvmType.Byte   => new FromChunkByte(chunk.asInstanceOf[Chunk[Byte]]).asInstanceOf[SyncReader[A]]
      case _              => new FromChunk(chunk, jt.jvmType)
    }

  /**
   * Returns a reader over the bytes of `is`, on the `Byte` lane. Each pull
   * calls `is.read()`, and `readBytes` passes the whole request to
   * `is.read(buf, offset, len)`, so pulls block when `is` blocks.
   *
   * An `IOException` from `is` is thrown wrapped in the library's
   * `zio.blocks.streams.internal.StreamError`, whose `value` is the
   * `IOException`, and every later pull throws it again. Closing the reader
   * does not close `is`. It does not support `reset()`, and `readable()`
   * reports `is.available() > 0`.
   *
   * {{{
   * import zio.blocks.streams.io.Reader
   *
   * val bytes = Reader.fromInputStream(new java.io.ByteArrayInputStream(Array[Byte](90, 73, 79)))
   * bytes.readAll[Byte]() // Chunk(90,73,79)
   * }}}
   */
  def fromInputStream(is: InputStream): SyncReader[Byte] =
    new InputStreamReader(is)

  /**
   * Returns a reader over the elements of `it`, in iteration order, on the
   * primitive lane of `A`. `reset()` asks `it` for a new iterator. Over a
   * `Vector` it also supports `setSkip` and `setLimit`. An exception thrown by
   * the iterator is thrown by the pull.
   */
  def fromIterable[A](it: Iterable[A])(implicit jt: JvmType.Infer[A]): SyncReader[A] =
    it match {
      case values: Vector[_] => new FromVector(values.asInstanceOf[Vector[A]], jt.jvmType)
      case _                 => new FromIterable(it, jt.jvmType)
    }

  /**
   * Returns a reader over the numbers of `range`, in order, on the `Int` lane.
   * It computes each number instead of storing them, and supports `reset()`,
   * `setSkip` and `setLimit`.
   */
  def fromRange(range: Range): SyncReader[Int] =
    new FromRange(range.start, range.step, range.length)

  private[streams] def fromRange(start: Int, step: Int, length: Int): SyncReader[Int] =
    new FromRange(start, step, length)

  /**
   * Returns a reader over the characters of `r`, on the `Char` lane. Each pull
   * calls `r.read()` and blocks when `r` blocks.
   *
   * An `IOException` from `r` is thrown wrapped in the library's
   * `zio.blocks.streams.internal.StreamError`, whose `value` is the
   * `IOException`, and every later pull throws it again. Closing the reader
   * does not close `r`. It does not support `reset()`, and `readable()` reports
   * `r.ready()`.
   */
  def fromReader(r: JReader): SyncReader[Char] =
    new CharReader(r)

  /**
   * Returns a reader that produces `a` forever: a [[single]] reader in repeat
   * mode. It never reports the end, so read it with a bound, such as `readN`.
   *
   * A known bug: `close()` does not stop it. After `close()`, `isClosed` stays
   * `false` and every pull still returns `a`.
   */
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): SyncReader[A] = {
    val r = single[A](a)
    r.setRepeat()
    r
  }

  /**
   * Returns a reader that produces the elements of `inner` again and again:
   * each time `inner` ends, it calls `inner.reset()` and keeps reading. An
   * exception from `inner`, including the `UnsupportedOperationException` of an
   * `inner` that cannot reset, is thrown by the pull; `isClosed` turns `true`
   * only when the failing pull went through `read`, which is what the bulk
   * reads of an `AnyRef`-lane `inner` use; the primitive pulls leave it
   * `false`. If `inner` produces no element between two resets, a pull loops
   * forever.
   *
   * A known bug: after `close()`, the next pull resets `inner` and keeps
   * producing elements (the asynchronous overload stops as expected).
   *
   * A known bug: when `inner` is on the `AnyRef` lane, the result's primitive
   * pulls (`readInt`, `readLongs`, ...) throw `IllegalStateException`; `read`,
   * `readAll`, `readN` and `readUpToN` work.
   *
   * {{{
   * import zio.blocks.chunk.Chunk
   * import zio.blocks.streams.io.Reader
   *
   * val ticks = Reader.repeated(Reader.fromChunk(Chunk("tick", "tock")))
   * ticks.readN[String](5) // Chunk(tick,tock,tick,tock,tick)
   * }}}
   */
  def repeated[A](inner: SyncReader[A]): SyncReader[A] =
    new Repeated(inner)

  /**
   * Returns an asynchronous reader that produces the elements of `inner` again
   * and again, resetting it each time it ends. A failure of `inner`, including
   * a reset it does not support, fails the pull.
   *
   * A known bug: when `inner` is on the `AnyRef` lane, the result's primitive
   * pulls (`readInt`, `readLongs`, ...) fail with `IllegalStateException`;
   * `read`, `readAll`, `readN` and `readUpToN` work.
   */
  def repeated[A](inner: AsyncReader[A]): AsyncReader[A] =
    new AsyncRepeated(inner)

  /**
   * Returns a repeating reader of the same kind as `inner`, for a reader whose
   * kind is not known statically: it calls the synchronous or the asynchronous
   * overload at run time.
   */
  def repeated[A](inner: Reader[A]): Reader[A] = inner match {
    case sync: SyncReader[A @unchecked]   => repeated(sync)
    case async: AsyncReader[A @unchecked] => repeated(async)
  }

  private[streams] def repeated[A](inner: SyncReader[A], guard: InterpreterGuard): SyncReader[A] =
    new Repeated(inner, guard)

  /**
   * Returns a reader that produces `value` once, then ends. For a primitive `A`
   * it is on that lane and stores `value` unboxed. It supports `reset()` and
   * `setRepeat`.
   *
   * {{{
   * import zio.blocks.streams.io.Reader
   *
   * val title = Reader.single("Zionomicon")
   * title.read[AnyRef](null) // Zionomicon
   * title.read[AnyRef](null) // null
   * }}}
   */
  def single[A](value: A)(implicit jt: JvmType.Infer[A]): SyncReader[A] = {
    val pt = jt.jvmType
    if (pt ne JvmType.AnyRef)
      new SingletonPrim[A](primToLong(value, pt), pt.ordinal << 8)
    else
      new SingletonGeneric(value)
  }

  /** Returns a reader on the `Boolean` lane that produces `value` once. */
  def singleBoolean(value: Boolean): SyncReader[Boolean] =
    new SingletonPrim[Boolean](if (value) 1L else 0L, JvmType.Boolean.ordinal << 8)

  /** Returns a reader on the `Byte` lane that produces `value` once. */
  def singleByte(value: Byte): SyncReader[Byte] =
    new SingletonPrim[Byte](value.toLong, JvmType.Byte.ordinal << 8)

  /** Returns a reader on the `Char` lane that produces `value` once. */
  def singleChar(value: Char): SyncReader[Char] =
    new SingletonPrim[Char](value.toLong, JvmType.Char.ordinal << 8)

  /** Returns a reader on the `Double` lane that produces `value` once. */
  def singleDouble(value: Double): SyncReader[Double] =
    new SingletonPrim[Double](JDouble.doubleToRawLongBits(value), JvmType.Double.ordinal << 8)

  /** Returns a reader on the `Float` lane that produces `value` once. */
  def singleFloat(value: Float): SyncReader[Float] =
    new SingletonPrim[Float](JFloat.floatToRawIntBits(value).toLong, JvmType.Float.ordinal << 8)

  /** Returns a reader on the `Int` lane that produces `value` once. */
  def singleInt(value: Int): SyncReader[Int] =
    new SingletonPrim[Int](value.toLong, JvmType.Int.ordinal << 8)

  /** Returns a reader on the `Long` lane that produces `value` once. */
  def singleLong(value: Long): SyncReader[Long] =
    new SingletonPrim[Long](value, JvmType.Long.ordinal << 8)

  /** Returns a reader on the `Short` lane that produces `value` once. */
  def singleShort(value: Short): SyncReader[Short] =
    new SingletonPrim[Short](value.toLong, JvmType.Short.ordinal << 8)

  /**
   * Returns a reader that computes its elements from a state: each pull calls
   * `f` with the current state, and `Some((element, next))` produces `element`
   * and moves to `next`, while `None` ends the reader. `f` runs on the pulling
   * thread, once per pull, and not again after `None`.
   *
   * An exception from `f` is thrown by the pull and leaves the state unchanged,
   * so the next pull calls `f` with the same state. `isClosed` becomes `true`
   * only after `f` has returned `None`. `reset()` restarts from `s`.
   *
   * {{{
   * import zio.blocks.streams.io.Reader
   *
   * val powers = Reader.unfold(1)(n => if (n > 100) None else Some((n, n * 2)))
   * powers.readAll[Int]() // Chunk(1,2,4,8,16,32,64)
   * }}}
   */
  def unfold[S, A](s: S)(f: S => Option[(A, S)])(implicit jt: JvmType.Infer[A]): SyncReader[A] =
    new Unfold(s, (state: S) => StreamError.callback(f(state)), jt.jvmType)

  /**
   * Returns an asynchronous reader that computes its elements from a state:
   * each pull runs the `Async` from `f` with the current state, and
   * `Some((element, next))` produces `element` and moves to `next`, while
   * `None` ends the reader. Nothing runs until the pull's `Async` runs.
   *
   * A failure of `f`'s `Async` fails the pull and ends the reader: `isClosed`
   * becomes `true`, and every later pull fails with the same error without
   * calling `f` again, until `reset()`. The next state is kept only when `f`
   * succeeds before a `reset()` or `close()`, which discard a result still in
   * flight. `reset()` restarts from `s`.
   *
   * {{{
   * import zio.blocks.async._
   * import zio.blocks.streams.io.Reader
   *
   * val pages = Reader.unfoldAsync(1)(page => Async.succeed(if (page > 3) None else Some(("page " + page, page + 1))))
   * pages.readAll[String]().block // Chunk(page 1,page 2,page 3)
   * }}}
   */
  def unfoldAsync[S, A](s: S)(f: S => Async[Option[(A, S)]])(implicit jt: JvmType.Infer[A]): AsyncReader[A] =
    new UnfoldAsync(s, f, jt.jvmType)

  /**
   * Wraps a reader with skip and limit tracking. Used as a fallback when the
   * reader cannot handle setSkip/setLimit natively (i.e. they return `false`).
   */
  private[streams] def withSkipLimit[A](inner: SyncReader[A], skip: Long, limit: Long): SyncReader[A] =
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

  /** Skips `n` elements using the non-colliding identity sentinel path. */
  private[streams] def skipViaSentinel(reader: SyncReader[_], n: Long): Unit = {
    val et = reader.jvmType
    var r  = n
    while (r > 0) {
      val available =
        if (et eq JvmType.Boolean) reader.readBooleanPhysical(-1) >= 0
        else if (et eq JvmType.Byte) reader.readBytePhysical() >= 0
        else if (et eq JvmType.Char) reader.readCharPhysical(-1) >= 0
        else if (et eq JvmType.Short) reader.readShortPhysical(Int.MinValue) != Int.MinValue
        else if (et eq JvmType.Int) reader.readIntPhysical(Long.MinValue) != Long.MinValue
        else if (et eq JvmType.Long)
          reader.readLongsPhysical(reader.collisionFreeLongScratch, 0, 1) > 0
        else if (et eq JvmType.Float) reader.readFloatPhysical(Double.MaxValue) != Double.MaxValue
        else if (et eq JvmType.Double)
          reader.readDoublesPhysical(reader.collisionFreeDoubleScratch, 0, 1) > 0
        else {
          val value = reader.read[Any](EndOfStream)
          value.asInstanceOf[AnyRef] ne EndOfStream
        }
      if (available) r -= 1 else r = 0
    }
  }

  /** Reader adapter that reads chars from a `java.io.Reader`. */
  private[streams] final class CharReader(r: JReader) extends SyncReader[Char] {

    private sealed trait St
    private case object Open                        extends St
    private case object Finished                    extends St
    private final class Errored(val e: StreamError) extends St

    private var st: St = Open

    def isClosed: Boolean = st ne Open

    override def jvmType: JvmType = JvmType.Char

    override def readable(): Boolean =
      (st eq Open) && (try { r.ready() }
      catch { case _: IOException => false })

    override def skip(n: Long): Unit = {
      var rem = n;
      while (rem > 0) {
        if (readCharPhysical(-1) < 0) rem = 0 else rem -= 1
      }
    }

    override def readChar(sentinel: Int)(implicit ev: Char <:< Char): Int =
      st match {
        case Finished   => sentinel
        case e: Errored => throw e.e
        case Open       =>
          try {
            val c = r.read()
            if (c < 0) { st = Finished; sentinel }
            else c
          } catch {
            case e: IOException =>
              val failure = StreamError.source(e)
              st = new Errored(failure)
              throw failure
          }
      }

    override def readByte(): Int = {
      val c = readCharPhysical(-1)
      if (c < 0) -1 else (c & 0xff)
    }

    def read[A1 >: Char](sentinel: A1): A1 =
      st match {
        case Finished   => sentinel
        case e: Errored => throw e.e
        case Open       =>
          try {
            val c = r.read()
            if (c < 0) { st = Finished; sentinel }
            else Char.box(c.toChar).asInstanceOf[A1]
          } catch {
            case e: IOException =>
              val failure = StreamError.source(e)
              st = new Errored(failure)
              throw failure
          }
      }

    def close(): Unit = if (st eq Open) st = Finished
  }

  /**
   * Pre-closed reader singleton. All reads immediately return sentinel/null.
   */
  private object ClosedReader extends SyncReader[Nothing] {
    def isClosed: Boolean                                                                                         = true
    override def readable(): Boolean                                                                              = false
    def read[A1 >: Nothing](sentinel: A1): A1                                                                     = sentinel
    override def readBoolean(sentinel: Int)(implicit ev: Nothing <:< Boolean): Int                                = sentinel
    override def readByte(): Int                                                                                  = -1
    override def readChar(sentinel: Int)(implicit ev: Nothing <:< Char): Int                                      = sentinel
    override def readDouble(sentinel: Double)(implicit ev: Nothing <:< Double): Double                            = sentinel
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Nothing <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0 else -1
    }
    override def readFloat(sentinel: Double)(implicit ev: Nothing <:< Float): Double                        = sentinel
    override def readInt(sentinel: Long)(implicit ev: Nothing <:< Int): Long                                = sentinel
    override def readLong(sentinel: Long)(implicit ev: Nothing <:< Long): Long                              = sentinel
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Nothing <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0 else -1
    }
    override def readShort(sentinel: Int)(implicit ev: Nothing <:< Short): Int = sentinel
    def close(): Unit                                                          = ()
    override def skip(n: Long): Unit                                           = ()
    override def reset(): Unit                                                 = ()
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
    rawHead: SyncReader[Elem],
    firstTail: () => SyncReader[Elem],
    outType: JvmType,
    guard: InterpreterGuard = null
  ) extends SyncReader[Elem] {
    override def jvmType: JvmType = outType

    private val head: SyncReader[Elem]    = normalizeSyncChild(rawHead, outType)
    private var current: SyncReader[Elem] = head
    private var tail: Array[AnyRef]       = { val a = new Array[AnyRef](4); a(0) = firstTail; a }
    private var tailLen: Int              = 1
    private var tailIdx: Int              = 0
    private var done: Boolean             = false
    private var closeFailure: Throwable   = null

    private def checkFailure(): Unit = if (closeFailure ne null) throw closeFailure

    /** Append a lazy tail thunk. O(1) amortized. */
    private[streams] def append(thunk: () => SyncReader[Elem @unchecked]): Unit = {
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
      checkFailure()
      if (guard ne null) guard.check()
      val exhausted = current
      current = null
      try exhausted.close()
      catch {
        case cause: Throwable =>
          if (guard ne null)
            try guard.check()
            catch {
              case stale: Throwable =>
                guard.cleanupFailed(stale, cause)
                throw stale
            }
          closeFailure = StreamError.attachCleanup(null, cause)
          done = true
          throw cause
      }
      if (guard ne null) guard.check()
      if (tailIdx < tailLen) {
        if (guard ne null) guard.check()
        var next: SyncReader[Elem] = null
        try {
          val rawNext = tail(tailIdx).asInstanceOf[() => SyncReader[Elem]].apply()
          if (rawNext eq null) throw new NullPointerException("Concatenated reader tail returned null")
          next = normalizeSyncChild(rawNext, outType)
          if (guard ne null) guard.check()
          current = next
          if (guard ne null) guard.check()
          tailIdx += 1
          true
        } catch {
          case cause: Throwable =>
            current = null
            if (next ne null) {
              if (guard ne null) guard.reject(next, cause)
              else
                try next.close()
                catch { case cleanup: Throwable => StreamError.attachCleanupReplay(cause, cleanup) }
            }
            if (guard ne null) guard.check()
            done = true
            closeFailure = cause
            throw closeFailure
        }
      } else {
        done = true
        false
      }
    }

    def isClosed: Boolean = done || (closeFailure ne null)

    def read[A1 >: Elem](sentinel: A1): A1 = {
      checkFailure()
      while (true) {
        val v = current.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] ne EndOfStream) return v.asInstanceOf[A1]
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long = {
      checkFailure()
      while (true) {
        val v = current.readIntPhysical(sentinel)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long = {
      val one = collisionFreeLongScratch
      if (readLongsPhysical(one, 0, 1) < 0) sentinel else one(0)
    }

    override def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double = {
      checkFailure()
      while (true) {
        val v = current.readFloatPhysical(sentinel)
        if (v != sentinel) return v
        if (!advance()) return sentinel
      }
      sentinel // unreachable
    }

    override def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double = {
      val one = collisionFreeDoubleScratch
      if (readDoublesPhysical(one, 0, 1) < 0) sentinel else one(0)
    }

    override def readBoolean(sentinel: Int)(implicit ev: Elem <:< Boolean): Int = {
      checkFailure()
      while (true) {
        val value = current.readBooleanPhysical(sentinel)
        if (value != sentinel) return value
        if (!advance()) return sentinel
      }
      sentinel
    }

    override def readByte(): Int = {
      checkFailure()
      while (true) {
        val b = current.readBytePhysical()
        if (b >= 0) return b
        if (!advance()) return -1
      }
      -1 // unreachable
    }

    override def readChar(sentinel: Int)(implicit ev: Elem <:< Char): Int = {
      checkFailure()
      while (true) {
        val value = current.readCharPhysical(sentinel)
        if (value != sentinel) return value
        if (!advance()) return sentinel
      }
      sentinel
    }

    override def readShort(sentinel: Int)(implicit ev: Elem <:< Short): Int = {
      checkFailure()
      while (true) {
        val value = current.readShortPhysical(sentinel)
        if (value != sentinel) return value
        if (!advance()) return sentinel
      }
      sentinel
    }

    override def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: Elem <:< Int): Int = {
      validateArrayRange(buf, offset, maxLen)
      checkFailure()
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readIntsPhysical(buf, offset, maxLen)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1
    }

    override def readLongs(buf: Array[Long], offset: Int, maxLen: Int)(implicit ev: Elem <:< Long): Int = {
      validateArrayRange(buf, offset, maxLen)
      checkFailure()
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readLongsPhysical(buf, offset, maxLen)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1
    }

    override def readFloats(buf: Array[Float], offset: Int, maxLen: Int)(implicit ev: Elem <:< Float): Int = {
      validateArrayRange(buf, offset, maxLen)
      checkFailure()
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readFloatsPhysical(buf, offset, maxLen)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1
    }

    override def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: Elem <:< Double): Int = {
      validateArrayRange(buf, offset, maxLen)
      checkFailure()
      if (maxLen == 0) return 0
      while (true) {
        val n = current.readDoublesPhysical(buf, offset, maxLen)
        if (n > 0) return n
        if (!advance()) return -1
      }
      -1
    }

    override def readUpToN[A1 >: Elem](n: Int): Chunk[A1] = {
      checkFailure()
      if (n <= 0) return Chunk.empty
      while (true) {
        val chunk = current.readUpToN[A1](n)
        if (chunk.nonEmpty) return chunk
        if (!advance()) return Chunk.empty
      }
      Chunk.empty
    }

    override def readN[A1 >: Elem](n: Int): Chunk[A1] = {
      checkFailure()
      super.readN(n)
    }

    override def skip(n: Long): Unit = {
      checkFailure()
      Reader.skipViaSentinel(this, n)
    }

    def close(): Unit = {
      if (!done) {
        done = true
        if (current ne null) {
          val active = current
          current = null
          try active.close()
          catch {
            case cause: Throwable =>
              closeFailure =
                if (closeFailure eq null) cause
                else StreamError.attachCleanupReplay(closeFailure, cause)
          }
        }
      }
      if (closeFailure ne null) throw closeFailure
    }

    override def reset(): Unit = {
      var failure: Throwable = null
      if ((current ne null) && (current ne head)) {
        try current.close()
        catch { case cause: Throwable => failure = StreamError.attachCleanup(null, cause) }
      }
      current = head
      try head.reset()
      catch {
        case cause: Throwable =>
          failure = StreamError.attachCleanup(failure, cause)
      }
      tailIdx = 0
      done = false
      closeFailure = failure
      if (failure ne null) throw failure
    }
  }

  /**
   * A reader that delegates all methods to an inner reader. Subclass and
   * override only the methods you need to change (typically `close()`).
   */
  private[streams] abstract class DelegatingReader[+Elem](inner: SyncReader[Elem]) extends SyncReader[Elem] {
    protected def delegationOpen: Boolean                                       = true
    override def jvmType: JvmType                                               = inner.jvmType
    def isClosed: Boolean                                                       = !delegationOpen || inner.isClosed
    def read[A1 >: Elem](sentinel: A1): A1                                      = if (delegationOpen) inner.read(sentinel) else sentinel
    override def readBoolean(sentinel: Int)(implicit ev: Elem <:< Boolean): Int =
      if (delegationOpen) inner.readBooleanPhysical(sentinel) else sentinel
    override def readByte(): Int                                                                        = if (delegationOpen) inner.readBytePhysical() else -1
    override def readBytes(buf: Array[Byte], offset: Int, maxLen: Int)(implicit ev: Elem <:< Byte): Int = {
      validateArrayRange(buf, offset, maxLen)
      if (delegationOpen) inner.readBytesPhysical(buf, offset, maxLen) else if (maxLen == 0) 0 else -1
    }
    override def readChar(sentinel: Int)(implicit ev: Elem <:< Char): Int =
      if (delegationOpen) inner.readCharPhysical(sentinel) else sentinel
    override def readShort(sentinel: Int)(implicit ev: Elem <:< Short): Int =
      if (delegationOpen) inner.readShortPhysical(sentinel) else sentinel
    override def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long =
      if (delegationOpen) inner.readIntPhysical(sentinel) else sentinel
    override def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long =
      if (delegationOpen) inner.readLongPhysical(sentinel) else sentinel
    override def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double =
      if (delegationOpen) inner.readFloatPhysical(sentinel) else sentinel
    override def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double =
      if (delegationOpen) inner.readDoublePhysical(sentinel) else sentinel
    override def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: Elem <:< Int): Int = {
      validateArrayRange(buf, offset, maxLen)
      if (delegationOpen) inner.readIntsPhysical(buf, offset, maxLen) else if (maxLen == 0) 0 else -1
    }
    override def readLongs(buf: Array[Long], offset: Int, maxLen: Int)(implicit ev: Elem <:< Long): Int = {
      validateArrayRange(buf, offset, maxLen)
      if (delegationOpen) inner.readLongsPhysical(buf, offset, maxLen) else if (maxLen == 0) 0 else -1
    }
    override def readFloats(buf: Array[Float], offset: Int, maxLen: Int)(implicit ev: Elem <:< Float): Int = {
      validateArrayRange(buf, offset, maxLen)
      if (delegationOpen) inner.readFloatsPhysical(buf, offset, maxLen) else if (maxLen == 0) 0 else -1
    }
    override def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: Elem <:< Double): Int = {
      validateArrayRange(buf, offset, maxLen)
      if (delegationOpen) inner.readDoublesPhysical(buf, offset, maxLen) else if (maxLen == 0) 0 else -1
    }
    override def readUpToN[A1 >: Elem](n: Int): Chunk[A1] = if (delegationOpen) inner.readUpToN(n) else Chunk.empty
    override def skip(n: Long): Unit                      = if (delegationOpen) inner.skip(n)
    def close(): Unit                                     = inner.close()
    override def reset(): Unit                            = inner.reset()
    override def readable(): Boolean                      = delegationOpen && inner.readable()
  }

  /**
   * Wraps a Double-specialized source reader and applies a single predicate.
   */
  private[streams] final class FilteredDouble(
    val source: SyncReader[_],
    val pred: AnyRef
  ) extends SyncReader[Any]
      with WrappedReader {
    private val scratch                                                            = new Array[Double](1)
    override def jvmType: JvmType                                                  = source.jvmType
    def isClosed: Boolean                                                          = source.isClosed
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double =
      if (nextAccepted()) scratch(0) else sentinel
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        while (count < length && nextAccepted()) { dest(offset + count) = scratch(0); count += 1 }
        if (count == 0) -1 else count
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 =
      if (nextAccepted()) Double.box(scratch(0)).asInstanceOf[A1] else sentinel
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b         = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
      var available = nextAccepted()
      var i         = 0
      while (available && i < n) {
        b.addOne(scratch(0)); i += 1
        if (i < n) available = nextAccepted()
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    def close(): Unit                   = source.close()
    override def reset(): Unit          = source.reset()
    override def skip(n: Long): Unit    = Reader.skipViaSentinel(this, n)
    override def setRepeat(): Boolean   = source.setRepeat()
    private def nextAccepted(): Boolean = {
      var count = source.readDoublesPhysical(scratch, 0, 1)
      while (count > 0 && !pred.asInstanceOf[Double => Boolean](scratch(0)))
        count = source.readDoublesPhysical(scratch, 0, 1)
      count > 0
    }
    def toInterpreter: SyncInterpreter = {
      val p = SyncInterpreter(source)
      p.addAdaptedFilter(JvmType.Double, pred)
      p
    }
  }

  /** Wraps a Float-specialized source reader and applies a single predicate. */
  private[streams] final class FilteredFloat(
    val source: SyncReader[_],
    val pred: AnyRef
  ) extends SyncReader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                                = source.jvmType
    def isClosed: Boolean                                                        = source.isClosed
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      var v = source.readFloatPhysical(sentinel)
      while (v != sentinel && !pred.asInstanceOf[Float => Boolean](v.toFloat))
        v = source.readFloatPhysical(sentinel)
      v
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = readFloatPhysical(Double.MaxValue);
      if (v == Double.MaxValue) sentinel else Float.box(v.toFloat).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
      val s = Double.MaxValue
      var v = readFloatPhysical(s)
      if (v == s) return Chunk.empty
      var i = 0
      while (v != s && i < n) {
        b.addOne(v.toFloat); i += 1
        if (i < n) v = readFloatPhysical(s)
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    def close(): Unit                = source.close()
    override def reset(): Unit       = source.reset()
    override def skip(n: Long): Unit = {
      val s = Double.MaxValue; var r = n; while (r > 0 && readFloatPhysical(s) != s) r -= 1
    }
    override def setRepeat(): Boolean  = source.setRepeat()
    def toInterpreter: SyncInterpreter = {
      val p = SyncInterpreter(source)
      p.addAdaptedFilter(JvmType.Float, pred)
      p
    }
  }

  /**
   * Wraps an Int-specialized source reader and applies a single predicate.
   * Loops on rejection. The filter doesn't change the element type so `jvmType`
   * delegates to the source.
   */
  private[streams] final class FilteredIntInt(val source: SyncReader[_], val pred: AnyRef)
      extends SyncReader[Any]
      with WrappedReader {
    override def jvmType: JvmType         = JvmType.Int
    def isClosed: Boolean                 = source.isClosed
    private def test(value: Int): Boolean =
      try pred.asInstanceOf[Int => Boolean](value)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    private[streams] def foldLong(z: Long, f: AnyRef): Long = source match {
      case range: FromRange => range.foldFilteredLong(z, pred, f)
      case _                =>
        var acc   = z
        val end   = Long.MinValue
        var value = source.readIntPhysical(end)
        while (value != end) {
          if (test(value.toInt))
            try acc = f.asInstanceOf[(Long, Int) => Long](acc, value.toInt)
            catch { case error: StreamError => throw StreamError.untrusted(error) }
          value = source.readIntPhysical(end)
        }
        acc
    }
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      var value = source.readIntPhysical(sentinel)
      while (value != sentinel && !test(value.toInt))
        value = source.readIntPhysical(sentinel)
      value
    }
    def read[A >: Any](sentinel: A): A = {
      val value = readIntPhysical(Long.MinValue)
      if (value == Long.MinValue) sentinel else Int.box(value.toInt).asInstanceOf[A]
    }
    override def readUpToN[A >: Any](n: Int): Chunk[A] = {
      if (n <= 0) return Chunk.empty
      val builder = new ChunkBuilder.Int(); builder.sizeHint(math.min(n, 64))
      var value   = readIntPhysical(Long.MinValue)
      var count   = 0
      while (value != Long.MinValue && count < n) {
        builder.addOne(value.toInt); count += 1
        if (count < n) value = readIntPhysical(Long.MinValue)
      }
      builder.result().asInstanceOf[Chunk[A]]
    }
    def close(): Unit                  = source.close()
    override def reset(): Unit         = source.reset()
    override def skip(n: Long): Unit   = Reader.skipViaSentinel(this, n)
    override def setRepeat(): Boolean  = source.setRepeat()
    def toInterpreter: SyncInterpreter = {
      val interpreter = SyncInterpreter(source)
      interpreter.addAdaptedFilter(JvmType.Int, pred)
      interpreter
    }
  }

  private[streams] final class FilteredInt(
    val source: SyncReader[_],
    val pred: AnyRef,
    val inType: JvmType = JvmType.Int
  ) extends SyncReader[Any]
      with WrappedReader {
    override def jvmType: JvmType                                        = source.jvmType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      var v = pullInput(sentinel)
      while (v != sentinel && !pred.asInstanceOf[Int => Boolean](v.toInt))
        v = pullInput(sentinel)
      v
    }
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int = {
      var value = source.readBooleanPhysical(sentinel)
      while (value != sentinel && !pred.asInstanceOf[Int => Boolean](value))
        value = source.readBooleanPhysical(sentinel)
      value
    }
    override def readByte(): Int = {
      var value = source.readBytePhysical()
      while (value >= 0 && !pred.asInstanceOf[Int => Boolean](value)) value = source.readBytePhysical()
      value
    }
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int = {
      var value = source.readCharPhysical(sentinel)
      while (value != sentinel && !pred.asInstanceOf[Int => Boolean](value))
        value = source.readCharPhysical(sentinel)
      value
    }
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int = {
      var value = source.readShortPhysical(sentinel)
      while (value != sentinel && !pred.asInstanceOf[Int => Boolean](value))
        value = source.readShortPhysical(sentinel)
      value
    }
    private def pullInput(sentinel: Long): Long =
      if (inType eq JvmType.Boolean) {
        val value = source.readBooleanPhysical(-1)
        if (value < 0) sentinel else value.toLong
      } else if (inType eq JvmType.Byte) {
        val value = source.readBytePhysical()
        if (value < 0) sentinel else value.toLong
      } else if (inType eq JvmType.Char) {
        val value = source.readCharPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel else value.toLong
      } else if (inType eq JvmType.Short) {
        val value = source.readShortPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel else value.toLong
      } else source.readIntPhysical(sentinel)
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v =
        if (inType eq JvmType.Boolean) readBooleanPhysical(-1).toLong
        else if (inType eq JvmType.Byte) readBytePhysical().toLong
        else if (inType eq JvmType.Char) readCharPhysical(Int.MinValue).toLong
        else if (inType eq JvmType.Short) readShortPhysical(Int.MinValue).toLong
        else readIntPhysical(Long.MinValue)
      if (v == Long.MinValue) sentinel
      else if (inType eq JvmType.Boolean) {
        if (v < 0L) sentinel else Boolean.box(v != 0L).asInstanceOf[A1]
      } else if (inType eq JvmType.Byte) {
        if (v < 0L) sentinel else Byte.box(v.toByte).asInstanceOf[A1]
      } else if (inType eq JvmType.Char) {
        if (v == Int.MinValue.toLong) sentinel else Char.box(v.toChar).asInstanceOf[A1]
      } else if (inType eq JvmType.Short) {
        if (v == Int.MinValue.toLong) sentinel else Short.box(v.toShort).asInstanceOf[A1]
      } else Int.box(v.toInt).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val builder = ChunkBuilder.make[A1](math.min(n, 64))
      var value   = read[A1](EndOfStream.asInstanceOf[A1])
      var i       = 0
      while ((value.asInstanceOf[AnyRef] ne EndOfStream) && i < n) {
        builder.addOne(value); i += 1
        if (i < n) value = read[A1](EndOfStream.asInstanceOf[A1])
      }
      builder.result()
    }
    def close(): Unit                  = source.close()
    override def reset(): Unit         = source.reset()
    override def skip(n: Long): Unit   = Reader.skipViaSentinel(this, n)
    override def setRepeat(): Boolean  = source.setRepeat()
    def toInterpreter: SyncInterpreter = {
      val p = SyncInterpreter(source)
      p.addAdaptedFilter(source.jvmType, pred)
      p
    }
  }

  /** Wraps a Long-specialized source reader and applies a single predicate. */
  private[streams] final class FilteredLong(
    val source: SyncReader[_],
    val pred: AnyRef
  ) extends SyncReader[Any]
      with WrappedReader {
    private val scratch                                                    = new Array[Long](1)
    override def jvmType: JvmType                                          = source.jvmType
    def isClosed: Boolean                                                  = source.isClosed
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long =
      if (nextAccepted()) scratch(0) else sentinel
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        while (count < length && nextAccepted()) { dest(offset + count) = scratch(0); count += 1 }
        if (count == 0) -1 else count
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 =
      if (nextAccepted()) Long.box(scratch(0)).asInstanceOf[A1] else sentinel
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val b         = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
      var available = nextAccepted()
      var i         = 0
      while (available && i < n) {
        b.addOne(scratch(0)); i += 1
        if (i < n) available = nextAccepted()
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
    def close(): Unit                   = source.close()
    override def reset(): Unit          = source.reset()
    override def skip(n: Long): Unit    = Reader.skipViaSentinel(this, n)
    override def setRepeat(): Boolean   = source.setRepeat()
    private def nextAccepted(): Boolean = {
      var count = source.readLongsPhysical(scratch, 0, 1)
      while (count > 0 && !pred.asInstanceOf[Long => Boolean](scratch(0)))
        count = source.readLongsPhysical(scratch, 0, 1)
      count > 0
    }
    def toInterpreter: SyncInterpreter = {
      val p = SyncInterpreter(source)
      p.addAdaptedFilter(JvmType.Long, pred)
      p
    }
  }

  /**
   * Wraps a reference-type (AnyRef) source reader and applies a single
   * predicate.
   */
  private[streams] final class FilteredRef(
    val source: SyncReader[_],
    val pred: AnyRef
  ) extends SyncReader[Any]
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
    override def setRepeat(): Boolean  = source.setRepeat()
    def toInterpreter: SyncInterpreter = {
      val p = SyncInterpreter(source)
      p.addAdaptedFilter(JvmType.AnyRef, pred)
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
  private[streams] abstract class FlatMappedBase(outType: JvmType) extends SyncReader[Any] {
    protected var inner: SyncReader[Any]    = null
    private var _closed                     = false
    private var closeDone                   = false
    private var closeFailure: Throwable     = null
    private var operationFailed             = false
    private var operationFailure: Throwable = null

    override def jvmType: JvmType = outType
    def isClosed: Boolean         = _closed || operationFailed

    protected def pullOuter(): Boolean

    protected final def installInner(reader: SyncReader[Any]): Unit =
      inner = normalizeSyncChild(reader, outType)

    private def protect[A](body: => A): A = {
      if (operationFailed) throw operationFailure
      try body
      catch {
        case cause: Throwable =>
          operationFailed = true
          operationFailure = cause
          throw cause
      }
    }

    private def recordCloseFailure(cause: Throwable): Unit =
      if (operationFailed)
        operationFailure = StreamError.attachCleanupReplay(operationFailure, cause)
      else
        closeFailure =
          if (closeFailure eq null) cause
          else StreamError.attachCleanupReplay(closeFailure, cause)

    private def advance(): Boolean = {
      if (closeFailure ne null) throw closeFailure
      if (inner != null) {
        val exhausted = inner
        inner = null
        try exhausted.close()
        catch {
          case cause: Throwable =>
            closeFailure = StreamError.attachCleanup(null, cause)
            throw cause
        }
      }
      if (!pullOuter()) { _closed = true; return false }
      true
    }

    private def readBooleanRaw(sentinel: Int): Int = {
      while (true) {
        if (inner != null) { val v = inner.readBooleanPhysical(sentinel); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int = protect(readBooleanRaw(sentinel))
    private def readByteRaw(): Int                                             = {
      while (true) {
        if (inner != null) { val v = inner.readBytePhysical(); if (v >= 0) return v }
        if (!advance()) return -1
      }
      -1
    }
    override def readByte(): Int                = protect(readByteRaw())
    private def readCharRaw(sentinel: Int): Int = {
      while (true) {
        if (inner != null) { val v = inner.readCharPhysical(sentinel); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int = protect(readCharRaw(sentinel))
    private def readShortRaw(sentinel: Int): Int                         = {
      while (true) {
        if (inner != null) { val v = inner.readShortPhysical(sentinel); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int = protect(readShortRaw(sentinel))

    private def readIntRaw(sentinel: Long): Long = {
      while (true) {
        if (inner != null) {
          val v = inner.asInstanceOf[SyncReader[Int]].readInt(sentinel)
          if (v != sentinel) return v
        }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long   = protect(readIntRaw(sentinel))
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = protect {
      if (readLongsPhysical(collisionFreeLongScratch, 0, 1) < 0) sentinel else collisionFreeLongScratch(0)
    }
    private def readFloatRaw(sentinel: Double): Double = {
      while (true) {
        if (inner != null) { val v = inner.readFloatPhysical(sentinel); if (v != sentinel) return v }
        if (!advance()) return sentinel
      }
      sentinel
    }
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double   = protect(readFloatRaw(sentinel))
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = protect {
      if (readDoublesPhysical(collisionFreeDoubleScratch, 0, 1) < 0) sentinel else collisionFreeDoubleScratch(0)
    }
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = protect {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        var done  = false
        while (count < length && !done) {
          if (inner != null) {
            val read = inner.readLongsPhysical(dest, offset + count, length - count)
            if (read > 0) count += read
            else if (!advance()) done = true
          } else if (!advance()) done = true
        }
        if (count == 0 && done) -1 else count
      }
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int =
      protect {
        validateArrayRange(dest, offset, length)
        if (length == 0) 0
        else {
          var count = 0
          var done  = false
          while (count < length && !done) {
            if (inner != null) {
              val read = inner.readDoublesPhysical(dest, offset + count, length - count)
              if (read > 0) count += read
              else if (!advance()) done = true
            } else if (!advance()) done = true
          }
          if (count == 0 && done) -1 else count
        }
      }
    private def readRaw[A1](sentinel: A1): A1 = {
      while (true) {
        if (inner != null) {
          val v = inner.read[Any](EndOfStream); if (v.asInstanceOf[AnyRef] ne EndOfStream) return v.asInstanceOf[A1]
        }
        if (!advance()) return sentinel
      }
      sentinel
    }
    def read[A1 >: Any](sentinel: A1): A1                = protect(readRaw(sentinel))
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readIntPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readIntPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b      = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val values = new Array[Long](math.min(n, 64))
        var total  = 0
        var done   = false
        while (total < n && !done) {
          val count = readLongsPhysical(values, 0, math.min(values.length, n - total))
          if (count < 0) done = true
          else {
            var i = 0
            while (i < count) { b.addOne(values(i)); i += 1 }
            total += count
          }
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloatPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloatPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b      = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val values = new Array[Double](math.min(n, 64))
        var total  = 0
        var done   = false
        while (total < n && !done) {
          val count = readDoublesPhysical(values, 0, math.min(values.length, n - total))
          if (count < 0) done = true
          else {
            var i = 0
            while (i < count) { b.addOne(values(i)); i += 1 }
            total += count
          }
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
      if (!closeDone) {
        closeDone = true
        _closed = true
        if (inner != null) {
          try inner.close()
          catch { case cause: Throwable => recordCloseFailure(cause) }
          inner = null
        }
        try closeSource()
        catch { case cause: Throwable => recordCloseFailure(cause) }
      }
      if (operationFailed) throw operationFailure
      if (closeFailure ne null) throw closeFailure
    }
    protected def closeSource(): Unit
  }

  /** FlatMap reader for Double-specialized sources. */
  private[streams] final class FlatMappedDouble(
    val source: SyncReader[_],
    f: AnyRef,
    compileInner: AnyRef => SyncReader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    private val input = new Array[Double](1)

    protected def pullOuter(): Boolean =
      if (source.readDoublesPhysical(input, 0, 1) < 0) false
      else { installInner(compileInner(f.asInstanceOf[Double => AnyRef](input(0)))); true }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for Float-specialized sources. */
  private[streams] final class FlatMappedFloat(
    val source: SyncReader[_],
    f: AnyRef,
    compileInner: AnyRef => SyncReader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.readFloatPhysical(Double.MaxValue)
      if (v == Double.MaxValue) false
      else { installInner(compileInner(f.asInstanceOf[Float => AnyRef](v.toFloat))); true }
    }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for Int-specialized sources. */
  private[streams] final class FlatMappedInt(
    val source: SyncReader[_],
    f: AnyRef,
    compileInner: AnyRef => SyncReader[Any],
    outType: JvmType,
    inType: JvmType = JvmType.Int
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val stream =
        if (inType eq JvmType.Boolean) {
          val value = source.readBooleanPhysical(-1)
          if (value < 0) return false
          f.asInstanceOf[Boolean => AnyRef](value != 0)
        } else if (inType eq JvmType.Byte) {
          val value = source.readBytePhysical()
          if (value < 0) return false
          f.asInstanceOf[Byte => AnyRef](value.toByte)
        } else if (inType eq JvmType.Char) {
          val value = source.readCharPhysical(-1)
          if (value < 0) return false
          f.asInstanceOf[Char => AnyRef](value.toChar)
        } else if (inType eq JvmType.Short) {
          val value = source.readShortPhysical(Int.MinValue)
          if (value == Int.MinValue) return false
          f.asInstanceOf[Short => AnyRef](value.toShort)
        } else {
          val value = source.readIntPhysical(Long.MinValue)
          if (value == Long.MinValue) return false
          f.asInstanceOf[Int => AnyRef](value.toInt)
        }
      installInner(compileInner(stream))
      true
    }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for Long-specialized sources. */
  private[streams] final class FlatMappedLong(
    val source: SyncReader[_],
    f: AnyRef,
    compileInner: AnyRef => SyncReader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    private val input = new Array[Long](1)

    protected def pullOuter(): Boolean =
      if (source.readLongsPhysical(input, 0, 1) < 0) false
      else { installInner(compileInner(f.asInstanceOf[Long => AnyRef](input(0)))); true }
    protected def closeSource(): Unit = source.close()
  }

  /** FlatMap reader for reference-type (AnyRef) sources. */
  private[streams] final class FlatMappedRef(
    val source: SyncReader[_],
    f: AnyRef,
    compileInner: AnyRef => SyncReader[Any],
    outType: JvmType
  ) extends FlatMappedBase(outType) {
    protected def pullOuter(): Boolean = {
      val v = source.read[Any](EndOfStream)
      if (v.asInstanceOf[AnyRef] eq EndOfStream) false
      else { installInner(compileInner(f.asInstanceOf[AnyRef => AnyRef](v.asInstanceOf[AnyRef]))); true }
    }
    protected def closeSource(): Unit = source.close()
  }

  /** Generic (boxed) chunk-backed reader for reference-type elements. */
  private[streams] final class FromChunk[A](chunk: Chunk[A], elemType: JvmType = JvmType.AnyRef) extends SyncReader[A] {
    override def jvmType: JvmType                           = elemType
    private val originalLen: Int                            = chunk.length
    private var effectiveLen: Int                           = originalLen
    private var limitN: Long                                = Long.MaxValue
    private var skipN: Long                                 = 0
    private var idx: Int                                    = 0
    def isClosed: Boolean                                   = idx >= effectiveLen
    override def readable(): Boolean                        = idx < effectiveLen
    override private[streams] def tryReadable: Availability = if (idx < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      idx = math.min(idx.toLong + math.max(0L, n), originalLen.toLong).toInt
      skipN = idx.toLong
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = math.max(0L, n)
      effectiveLen = math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
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
      if (elemType eq JvmType.Boolean) readBooleanPhysical(-1)
      else if (idx < effectiveLen) {
        val i = idx; idx += 1; lowByte(chunk(i))
      } else -1
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int =
      if (idx < effectiveLen) { val i = idx; idx += 1; if (chunk.boolean(i)(ev)) 1 else 0 }
      else sentinel
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk.char(i)(ev).toInt }
      else sentinel
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk(i).asInstanceOf[Double] }
      else sentinel
    override def readDoubles(buf: Array[Double], offset: Int, len: Int)(implicit ev: A <:< Double): Int = {
      validateArrayRange(buf, offset, len)
      if (len == 0) return 0
      val count = math.min(len, effectiveLen - idx)
      if (count <= 0) return -1
      var i = 0
      while (i < count) { buf(offset + i) = chunk(idx).asInstanceOf[Double]; idx += 1; i += 1 }
      count
    }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk(i).asInstanceOf[Float].toDouble }
      else sentinel
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk(i).asInstanceOf[Int].toLong }
      else sentinel
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk(i).asInstanceOf[Long] }
      else sentinel
    override def readLongs(buf: Array[Long], offset: Int, len: Int)(implicit ev: A <:< Long): Int = {
      validateArrayRange(buf, offset, len)
      if (len == 0) return 0
      val count = math.min(len, effectiveLen - idx)
      if (count <= 0) return -1
      var i = 0
      while (i < count) { buf(offset + i) = chunk(idx).asInstanceOf[Long]; idx += 1; i += 1 }
      count
    }
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int =
      if (idx < effectiveLen) { val i = idx; idx += 1; chunk.short(i)(ev).toInt }
      else sentinel
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
    }
  }

  /** Specialized FromChunk for Double elements — zero-boxing via readDouble. */
  private[streams] final class FromChunkDouble(chunk: Chunk[Double]) extends SyncReader[Double] {
    override def jvmType: JvmType                           = JvmType.Double
    private val originalLen: Int                            = chunk.length
    private var effectiveLen: Int                           = originalLen
    private var limitN: Long                                = Long.MaxValue
    private var skipN: Long                                 = 0
    private var idx: Int                                    = 0
    def isClosed: Boolean                                   = idx >= effectiveLen
    override def readable(): Boolean                        = idx < effectiveLen
    override private[streams] def tryReadable: Availability = if (idx < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      idx = math.min(idx.toLong + math.max(0L, n), originalLen.toLong).toInt
      skipN = idx.toLong
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = math.max(0L, n)
      effectiveLen = math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
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
    override def readDouble(sentinel: Double)(implicit ev: Double <:< Double): Double =
      if (idx < effectiveLen) { val v = chunk.double(idx); idx += 1; v }
      else sentinel
    override def readDoubles(buf: Array[Double], offset: Int, len: Int)(implicit ev: Double <:< Double): Int = {
      validateArrayRange(buf, offset, len)
      if (len == 0) return 0
      val available = effectiveLen - idx
      if (available <= 0) return -1
      val count = math.min(len, available)
      var i     = 0
      while (i < count) { buf(offset + i) = chunk.double(idx); idx += 1; i += 1 }
      count
    }
    override def readByte(): Int =
      if (idx < effectiveLen) { val v = chunk.double(idx); idx += 1; v.toInt & 0xff }
      else -1
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
    }
  }

  /** Specialized FromChunk for Float elements — zero-boxing via readFloat. */
  private[streams] final class FromChunkFloat(chunk: Chunk[Float]) extends SyncReader[Float] {
    override def jvmType: JvmType                           = JvmType.Float
    private val originalLen: Int                            = chunk.length
    private var effectiveLen: Int                           = originalLen
    private var limitN: Long                                = Long.MaxValue
    private var skipN: Long                                 = 0
    private var idx: Int                                    = 0
    def isClosed: Boolean                                   = idx >= effectiveLen
    override def readable(): Boolean                        = idx < effectiveLen
    override private[streams] def tryReadable: Availability = if (idx < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      idx = math.min(idx.toLong + math.max(0L, n), originalLen.toLong).toInt
      skipN = idx.toLong
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = math.max(0L, n)
      effectiveLen = math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
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
      val v = readFloatPhysical(Double.MaxValue)
      if (v == Double.MaxValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
    }
  }

  /** Specialized FromChunk for Byte elements — zero-boxing via readInt. */
  private[streams] final class FromChunkByte(chunk: Chunk[Byte]) extends SyncReader[Byte] {
    override def jvmType: JvmType                           = JvmType.Byte
    private val originalLen: Int                            = chunk.length
    private var effectiveLen: Int                           = originalLen
    private var limitN: Long                                = Long.MaxValue
    private var skipN: Long                                 = 0
    private var idx: Int                                    = 0
    def isClosed: Boolean                                   = idx >= effectiveLen
    override def readable(): Boolean                        = idx < effectiveLen
    override private[streams] def tryReadable: Availability = if (idx < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      idx = math.min(idx.toLong + math.max(0L, n), originalLen.toLong).toInt
      skipN = idx.toLong
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = math.max(0L, n)
      effectiveLen = math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
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
      validateArrayRange(buf, offset, len)
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
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
    }
  }

  /** Specialized FromChunk for Int elements — zero-boxing via readInt. */
  private[streams] final class FromChunkInt(chunk: Chunk[Int]) extends SyncReader[Int] {
    override def jvmType: JvmType                           = JvmType.Int
    private val originalLen: Int                            = chunk.length
    private var effectiveLen: Int                           = originalLen
    private var limitN: Long                                = Long.MaxValue
    private var skipN: Long                                 = 0
    private var idx: Int                                    = 0
    def isClosed: Boolean                                   = idx >= effectiveLen
    override def readable(): Boolean                        = idx < effectiveLen
    override private[streams] def tryReadable: Availability = if (idx < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      idx = math.min(idx.toLong + math.max(0L, n), originalLen.toLong).toInt
      skipN = idx.toLong
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = math.max(0L, n)
      effectiveLen = math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
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
      val v = readIntPhysical(Long.MinValue)
      if (v == Long.MinValue) -1 else v.toInt & 0xff
    }
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
    }
  }

  /** Specialized FromChunk for Long elements — zero-boxing via readLong. */
  private[streams] final class FromChunkLong(chunk: Chunk[Long]) extends SyncReader[Long] {
    override def jvmType: JvmType                           = JvmType.Long
    private val originalLen: Int                            = chunk.length
    private var effectiveLen: Int                           = originalLen
    private var limitN: Long                                = Long.MaxValue
    private var skipN: Long                                 = 0
    private var idx: Int                                    = 0
    def isClosed: Boolean                                   = idx >= effectiveLen
    override def readable(): Boolean                        = idx < effectiveLen
    override private[streams] def tryReadable: Availability = if (idx < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      idx = math.min(idx.toLong + math.max(0L, n), originalLen.toLong).toInt
      skipN = idx.toLong
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = math.max(0L, n)
      effectiveLen = math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def skip(n: Long): Unit = idx =
      math.max(0, math.min(idx.toLong + math.max(0L, n), effectiveLen.toLong).toInt)
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
    override def readLong(sentinel: Long)(implicit ev: Long <:< Long): Long =
      if (idx < effectiveLen) { val v = chunk.long(idx); idx += 1; v }
      else sentinel
    override def readLongs(buf: Array[Long], offset: Int, len: Int)(implicit ev: Long <:< Long): Int = {
      validateArrayRange(buf, offset, len)
      if (len == 0) return 0
      val available = effectiveLen - idx
      if (available <= 0) return -1
      val count = math.min(len, available)
      var i     = 0
      while (i < count) { buf(offset + i) = chunk.long(idx); idx += 1; i += 1 }
      count
    }
    override def readByte(): Int =
      if (idx < effectiveLen) { val v = chunk.long(idx); idx += 1; v.toInt & 0xff }
      else -1
    def close(): Unit          = idx = effectiveLen
    override def reset(): Unit = {
      idx = math.max(0, math.min(skipN, chunk.length.toLong).toInt)
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
    }
  }

  /** Reader backed by an `Iterable`; emits elements in iteration order. */
  private[streams] final class FromIterable[A](iterable: Iterable[A], elemType: JvmType) extends SyncReader[A] {
    override def jvmType: JvmType                           = elemType
    private var iter                                        = StreamError.callback(iterable.iterator)
    private var exhausted                                   = false
    def isClosed: Boolean                                   = exhausted || !StreamError.callback(iter.hasNext)
    override def readable(): Boolean                        = !exhausted && StreamError.callback(iter.hasNext)
    override private[streams] def tryReadable: Availability = if (readable()) Available else Unavailable
    override def skip(n: Long): Unit                        = {
      var r = n; while (r > 0 && StreamError.callback(iter.hasNext)) { StreamError.callback(iter.next()); r -= 1 }
    }
    def read[A1 >: A](sentinel: A1): A1 =
      if (!exhausted && StreamError.callback(iter.hasNext)) StreamError.callback(iter.next()).asInstanceOf[A1]
      else { exhausted = true; sentinel }
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int =
      if (!exhausted && StreamError.callback(iter.hasNext)) {
        if (StreamError.callback(iter.next()).asInstanceOf[Boolean]) 1 else 0
      } else { exhausted = true; sentinel }
    override def readByte(): Int =
      if (!exhausted && StreamError.callback(iter.hasNext))
        lowByte(StreamError.callback(iter.next()))
      else -1
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int =
      if (!exhausted && StreamError.callback(iter.hasNext)) StreamError.callback(iter.next()).asInstanceOf[Char].toInt
      else { exhausted = true; sentinel }
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      if (!exhausted && StreamError.callback(iter.hasNext)) StreamError.callback(iter.next()).asInstanceOf[Double]
      else { exhausted = true; sentinel }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      if (!exhausted && StreamError.callback(iter.hasNext))
        StreamError.callback(iter.next()).asInstanceOf[Float].toDouble
      else { exhausted = true; sentinel }
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      if (!exhausted && StreamError.callback(iter.hasNext)) StreamError.callback(iter.next()).asInstanceOf[Int].toLong
      else { exhausted = true; sentinel }
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      if (!exhausted && StreamError.callback(iter.hasNext)) StreamError.callback(iter.next()).asInstanceOf[Long]
      else { exhausted = true; sentinel }
    override def readLongs(buf: Array[Long], offset: Int, len: Int)(implicit ev: A <:< Long): Int = {
      validateArrayRange(buf, offset, len)
      if (len == 0) return 0
      var i = 0
      while (i < len && !exhausted && StreamError.callback(iter.hasNext)) {
        buf(offset + i) = StreamError.callback(iter.next()).asInstanceOf[Long]; i += 1
      }
      if (i == 0) { exhausted = true; -1 }
      else i
    }
    override def readDoubles(buf: Array[Double], offset: Int, len: Int)(implicit ev: A <:< Double): Int = {
      validateArrayRange(buf, offset, len)
      if (len == 0) return 0
      var i = 0
      while (i < len && !exhausted && StreamError.callback(iter.hasNext)) {
        buf(offset + i) = StreamError.callback(iter.next()).asInstanceOf[Double]; i += 1
      }
      if (i == 0) { exhausted = true; -1 }
      else i
    }
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int =
      if (!exhausted && StreamError.callback(iter.hasNext)) StreamError.callback(iter.next()).asInstanceOf[Short].toInt
      else { exhausted = true; sentinel }
    private[streams] def discardRemaining(): Unit = {
      while (!exhausted && StreamError.callback(iter.hasNext)) StreamError.callback(iter.next())
      exhausted = true
    }
    def close(): Unit          = exhausted = true
    override def reset(): Unit = { iter = StreamError.callback(iterable.iterator); exhausted = false }
  }

  /** Lane-aware reader backed by an immutable strict `Vector`. */
  private[streams] final class FromVector[A](values: Vector[A], elemType: JvmType) extends SyncReader[A] {
    override def jvmType: JvmType                           = elemType
    private var effectiveLen: Int                           = values.length
    private var resetEnd: Int                               = values.length
    private var resetIndex: Int                             = 0
    private var index: Int                                  = 0
    def isClosed: Boolean                                   = index >= effectiveLen
    override def readable(): Boolean                        = index < effectiveLen
    override private[streams] def tryReadable: Availability = if (index < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      val skipped = math.min(math.max(0L, n), (effectiveLen - index).toLong).toInt
      index += skipped
      resetIndex = index
      true
    }
    override def setLimit(n: Long): Boolean = {
      effectiveLen = math.min(effectiveLen.toLong, index.toLong + math.max(0L, n)).toInt
      resetEnd = effectiveLen
      true
    }
    override def skip(n: Long): Unit =
      index += math.min(math.max(0L, n), (effectiveLen - index).toLong).toInt
    def read[A1 >: A](sentinel: A1): A1 =
      if (index < effectiveLen) { val value = values(index); index += 1; value.asInstanceOf[A1] }
      else sentinel
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int =
      if (index < effectiveLen) { val value = values(index).asInstanceOf[Boolean]; index += 1; if (value) 1 else 0 }
      else sentinel
    override def readByte(): Int =
      if (index < effectiveLen) { val value = lowByte(values(index)); index += 1; value }
      else -1
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int =
      if (index < effectiveLen) { val value = values(index).asInstanceOf[Char].toInt; index += 1; value }
      else sentinel
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
      if (index < effectiveLen) { val value = values(index).asInstanceOf[Double]; index += 1; value }
      else sentinel
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
      if (index < effectiveLen) { val value = values(index).asInstanceOf[Float].toDouble; index += 1; value }
      else sentinel
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
      if (index < effectiveLen) { val value = values(index).asInstanceOf[Int].toLong; index += 1; value }
      else sentinel
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
      if (index < effectiveLen) { val value = values(index).asInstanceOf[Long]; index += 1; value }
      else sentinel
    override def readLongs(buf: Array[Long], offset: Int, len: Int)(implicit ev: A <:< Long): Int = {
      validateArrayRange(buf, offset, len)
      val count = math.min(len, effectiveLen - index)
      var i     = 0
      while (i < count) { buf(offset + i) = values(index).asInstanceOf[Long]; index += 1; i += 1 }
      if (count == 0 && len != 0) -1 else count
    }
    override def readDoubles(buf: Array[Double], offset: Int, len: Int)(implicit ev: A <:< Double): Int = {
      validateArrayRange(buf, offset, len)
      val count = math.min(len, effectiveLen - index)
      var i     = 0
      while (i < count) { buf(offset + i) = values(index).asInstanceOf[Double]; index += 1; i += 1 }
      if (count == 0 && len != 0) -1 else count
    }
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int =
      if (index < effectiveLen) { val value = values(index).asInstanceOf[Short].toInt; index += 1; value }
      else sentinel
    private[streams] def discardRemaining(): Unit = index = effectiveLen
    def close(): Unit                             = index = effectiveLen
    override def reset(): Unit                    = {
      index = resetIndex
      effectiveLen = resetEnd
    }
  }

  /** Int-specialized reader backed by a Scala `Range`. */
  private[streams] final class FromRange(rangeStart: Int, rangeStep: Int, originalLen: Int) extends SyncReader[Int] {
    override def jvmType: JvmType                           = JvmType.Int
    private var effectiveLen: Int                           = originalLen
    private var limitN: Long                                = Long.MaxValue
    private var skipN: Long                                 = 0
    private var idx: Int                                    = 0
    private var current: Int                                = rangeStart
    def isClosed: Boolean                                   = idx >= effectiveLen
    override def readable(): Boolean                        = idx < effectiveLen
    override private[streams] def tryReadable: Availability = if (idx < effectiveLen) Available else Unavailable
    override def setSkip(n: Long): Boolean                  = {
      val s = math.min(math.max(0L, n), originalLen.toLong - idx.toLong).toInt
      idx += s; current += s * rangeStep
      skipN = idx.toLong
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def setLimit(n: Long): Boolean = {
      limitN = math.max(0L, n)
      effectiveLen = math.min(originalLen.toLong, idx.toLong + limitN).toInt
      true
    }
    override def skip(n: Long): Unit = {
      val s = math.min(math.max(0L, n), (effectiveLen - idx).toLong).toInt; idx += s; current += s * rangeStep
    }
    def read[A1 >: Int](sentinel: A1): A1 =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; Int.box(v).asInstanceOf[A1] }
      else sentinel
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
      if (idx < effectiveLen) { val v = current; idx += 1; current += rangeStep; v.toLong }
      else sentinel
    private[streams] def discardRemaining(): Unit = {
      idx = effectiveLen
      current = rangeStart + effectiveLen * rangeStep
    }
    private[streams] def foldFilteredLong(z: Long, pred: AnyRef, f: AnyRef): Long = {
      val predicate = pred.asInstanceOf[Int => Boolean]
      val fold      = f.asInstanceOf[(Long, Int) => Long]
      var acc       = z
      while (idx < effectiveLen) {
        val value = current
        idx += 1
        current += rangeStep
        if (predicate(value)) acc = fold(acc, value)
      }
      acc
    }
    private[streams] def foldMappedLong(z: Long, map0: AnyRef, fold0: AnyRef): Long = {
      val map  = if (map0 eq null) null else map0.asInstanceOf[Int => Int]
      val fold = fold0.asInstanceOf[(Long, Int) => Long]
      var acc  = z
      while (idx < effectiveLen) {
        val value = current
        idx += 1
        current += rangeStep
        val mapped = if (map eq null) value else map(value)
        acc = fold(acc, mapped)
      }
      acc
    }
    private[streams] def foldMappedChainLong(z: Long, maps0: AnyRef, fold0: AnyRef): Long = {
      val maps = maps0.asInstanceOf[Array[Int => Int]]
      val fold = fold0.asInstanceOf[(Long, Int) => Long]
      var acc  = z
      while (idx < effectiveLen) {
        var value = current
        idx += 1
        current += rangeStep
        var i = 0
        while (i < maps.length) {
          try value = maps(i)(value)
          catch { case error: StreamError => throw StreamError.untrusted(error) }
          i += 1
        }
        acc = fold(acc, value)
      }
      acc
    }
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
    override def reset(): Unit = {
      val s = math.max(0L, math.min(skipN, originalLen.toLong)).toInt
      idx = s; current = rangeStart + s * rangeStep
      effectiveLen =
        if (limitN == Long.MaxValue) originalLen
        else math.min(originalLen.toLong, idx.toLong + limitN).toInt
    }
  }

  /** Reader adapter for `java.io.InputStream`. Emits elements as `Byte`. */
  private[streams] final class InputStreamReader(is: InputStream) extends SyncReader[Byte] {

    private var closed               = false
    private var errored: StreamError = null

    def isClosed: Boolean = closed

    override def readable(): Boolean =
      !closed && (try { is.available() > 0 }
      catch { case _: IOException => false })

    override def skip(n: Long): Unit = {
      var r = n; while (r > 0) { val b = readBytePhysical(); if (b < 0) r = 0 else r -= 1 }
    }

    override def jvmType: JvmType = JvmType.Byte

    override def readByte(): Int = {
      if (errored ne null) throw errored
      if (closed) return -1
      try {
        val b = is.read()
        if (b < 0) { closed = true; -1 }
        else b
      } catch {
        case e: IOException =>
          val failure = StreamError.source(e)
          closed = true; errored = failure; throw failure
      }
    }

    def read[A1 >: Byte](sentinel: A1): A1 = {
      val b = readBytePhysical()
      if (b >= 0) Byte.box(b.toByte).asInstanceOf[A1] else sentinel
    }

    override def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Byte): Int = {
      validateArrayRange(buf, offset, len)
      if (errored ne null) throw errored
      else if (len == 0) 0
      else if (closed) -1
      else
        try {
          val n = is.read(buf, offset, len)
          if (n < 0) { closed = true; -1 }
          else n
        } catch {
          case e: IOException =>
            val failure = StreamError.source(e)
            closed = true; errored = failure; throw failure
        }
    }

    override def readN[A1 >: Byte](n: Int): Chunk[A1] = {
      if (errored ne null) throw errored
      if (n <= 0 || closed) return Chunk.empty
      if (n <= 8192) {
        val arr   = new Array[Byte](n)
        var total = 0
        while (total < n && !closed) {
          val read = readBytesPhysical(arr, total, n - total)
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
          val read   = readBytesPhysical(buf, 0, toRead)
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
    val source: SyncReader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends SyncReader[Any]
      with WrappedReader {
    private val input                                                          = new Array[Double](1)
    private val outputDoubleScratch                                            = new Array[Double](1)
    private val outputLongScratch                                              = new Array[Long](1)
    override def jvmType: JvmType                                              = outType
    def isClosed: Boolean                                                      = source.isClosed
    private def pullInput(): Boolean                                           = source.readDoublesPhysical(input, 0, 1) >= 0
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int =
      if (!pullInput()) sentinel else f.asInstanceOf[Double => Int](input(0))
    override def readByte(): Int =
      if (!pullInput()) -1 else f.asInstanceOf[Double => Byte](input(0)).toInt & 0xff
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int =
      if (!pullInput()) sentinel else f.asInstanceOf[Double => Char](input(0)).toInt
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int =
      if (!pullInput()) sentinel else f.asInstanceOf[Double => Short](input(0)).toInt
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long =
      if (!pullInput()) sentinel else f.asInstanceOf[Double => Int](input(0)).toLong
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long =
      if (readLongsPhysical(outputLongScratch, 0, 1) < 0) sentinel else outputLongScratch(0)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double =
      if (!pullInput()) sentinel else f.asInstanceOf[Double => Float](input(0)).toDouble
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double =
      if (readDoublesPhysical(outputDoubleScratch, 0, 1) < 0) sentinel else outputDoubleScratch(0)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        while (count < length && pullInput()) {
          dest(offset + count) = f.asInstanceOf[Double => Long](input(0))
          count += 1
        }
        if (count == 0) -1 else count
      }
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        while (count < length && pullInput()) {
          dest(offset + count) = f.asInstanceOf[Double => Double](input(0))
          count += 1
        }
        if (count == 0) -1 else count
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 =
      if (!pullInput()) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Double => Int](input(0)) != 0).asInstanceOf[A1]
      } else f.asInstanceOf[Double => AnyRef](input(0)).asInstanceOf[A1]
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readIntPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readIntPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b    = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Long](1)
        var read = readLongsPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readLongsPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloatPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloatPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b    = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Double](1)
        var read = readDoublesPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readDoublesPhysical(one, 0, 1)
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
    def close(): Unit                       = source.close()
    override def reset(): Unit              = source.reset()
    override def skip(n: Long): Unit        = Reader.skipViaSentinel(this, n)
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: SyncInterpreter      = {
      val p = SyncInterpreter(source)
      p.addAdaptedMap(JvmType.Double, outType, f)
      p
    }
  }

  /**
   * Wraps a Float-specialized source reader and applies a single erased map
   * function.
   */
  private[streams] final class MappedFloat(
    val source: SyncReader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends SyncReader[Any]
      with WrappedReader {
    private val outputDoubleScratch                                            = new Array[Double](1)
    private val outputLongScratch                                              = new Array[Long](1)
    override def jvmType: JvmType                                              = outType
    def isClosed: Boolean                                                      = source.isClosed
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int = {
      val v = source.readFloatPhysical(Double.MaxValue)
      if (v == Double.MaxValue) sentinel else f.asInstanceOf[Float => Int](v.toFloat)
    }
    override def readByte(): Int = {
      val v = source.readFloatPhysical(Double.MaxValue)
      if (v == Double.MaxValue) -1 else f.asInstanceOf[Float => Byte](v.toFloat).toInt & 0xff
    }
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int = {
      val v = source.readFloatPhysical(Double.MaxValue)
      if (v == Double.MaxValue) sentinel else f.asInstanceOf[Float => Char](v.toFloat).toInt
    }
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int = {
      val v = source.readFloatPhysical(Double.MaxValue)
      if (v == Double.MaxValue) sentinel else f.asInstanceOf[Float => Short](v.toFloat).toInt
    }
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = source.readFloatPhysical(Double.MaxValue);
      if (v == Double.MaxValue) sentinel
      else f.asInstanceOf[Float => Int](v.toFloat).toLong
    }
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long =
      if (readLongsPhysical(outputLongScratch, 0, 1) < 0) sentinel else outputLongScratch(0)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = source.readFloatPhysical(sentinel);
      if (v == sentinel) sentinel
      else f.asInstanceOf[Float => Float](v.toFloat).toDouble
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double =
      if (readDoublesPhysical(outputDoubleScratch, 0, 1) < 0) sentinel else outputDoubleScratch(0)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        val eof   = Double.MaxValue
        var count = 0
        var value = source.readFloatPhysical(eof)
        while (count < length && value != eof) {
          dest(offset + count) = f.asInstanceOf[Float => Long](value.toFloat)
          count += 1
          if (count < length) value = source.readFloatPhysical(eof)
        }
        if (count == 0) -1 else count
      }
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        val eof   = Double.MaxValue
        var count = 0
        var value = source.readFloatPhysical(eof)
        while (count < length && value != eof) {
          dest(offset + count) = f.asInstanceOf[Float => Double](value.toFloat)
          count += 1
          if (count < length) value = source.readFloatPhysical(eof)
        }
        if (count == 0) -1 else count
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = source.readFloatPhysical(Double.MaxValue);
      if (v == Double.MaxValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Float => Int](v.toFloat) != 0).asInstanceOf[A1]
      } else f.asInstanceOf[Float => AnyRef](v.toFloat).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readIntPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readIntPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b    = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Long](1)
        var read = readLongsPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readLongsPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloatPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloatPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b    = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Double](1)
        var read = readDoublesPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readDoublesPhysical(one, 0, 1)
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
      val s = Double.MaxValue; var r = n; while (r > 0 && source.readFloatPhysical(s) != s) r -= 1
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: SyncInterpreter      = {
      val p = SyncInterpreter(source)
      p.addAdaptedMap(JvmType.Float, outType, f)
      p
    }
  }

  /**
   * Wraps an Int-specialized source reader and applies a single erased map
   * function. The sink dispatches on `jvmType` (= `outType`) and calls the
   * matching readXxx. Each readXxx pulls from `source.readInt` and applies `f`.
   */
  private[streams] final class MappedIntInt(val source: SyncReader[Int], val f: AnyRef)
      extends SyncReader[Int]
      with WrappedReader {
    override def jvmType: JvmType                                        = JvmType.Int
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = {
      val value = source.readIntPhysical(sentinel)
      if (value == sentinel) sentinel else f.asInstanceOf[Int => Int](value.toInt).toLong
    }
    private[streams] def foldLong(z: Long, fold: AnyRef): Long = source match {
      case range: FromRange => range.foldMappedLong(z, f, fold)
      case _                =>
        val map   = f.asInstanceOf[Int => Int]
        val op    = fold.asInstanceOf[(Long, Int) => Long]
        var acc   = z
        val end   = Long.MinValue
        var value = source.readIntPhysical(end)
        while (value != end) {
          val mapped =
            try map(value.toInt)
            catch { case error: StreamError => throw StreamError.untrusted(error) }
          try acc = op(acc, mapped)
          catch { case error: StreamError => throw StreamError.untrusted(error) }
          value = source.readIntPhysical(end)
        }
        acc
    }
    def read[A >: Int](sentinel: A): A = {
      val value = readIntPhysical(Long.MinValue)
      if (value == Long.MinValue) sentinel else Int.box(value.toInt).asInstanceOf[A]
    }
    override def readUpToN[A >: Int](n: Int): Chunk[A] = {
      if (n <= 0) return Chunk.empty
      val builder = new ChunkBuilder.Int(); builder.sizeHint(math.min(n, 64))
      var value   = readIntPhysical(Long.MinValue)
      var count   = 0
      while (value != Long.MinValue && count < n) {
        builder.addOne(value.toInt); count += 1
        if (count < n) value = readIntPhysical(Long.MinValue)
      }
      builder.result().asInstanceOf[Chunk[A]]
    }
    def close(): Unit                       = source.close()
    override def reset(): Unit              = source.reset()
    override def skip(n: Long): Unit        = source.skip(n)
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: SyncInterpreter      = {
      val interpreter = SyncInterpreter(source)
      interpreter.addAdaptedMap(JvmType.Int, JvmType.Int, f)
      interpreter
    }
  }

  private[streams] final class MappedAsyncEffectIntInt(
    val source: AsyncReader[Int],
    val f: Int => Async[Int]
  ) extends AsyncReader[Int] {
    def close(): Async[Unit]                  = source.close()
    def isClosed: Async[Boolean]              = source.isClosed
    override def jvmType: JvmType             = JvmType.Int
    def read[A >: Int](sentinel: A): Async[A] =
      readIntPhysical(Long.MinValue).map(value =>
        if (value == Long.MinValue) sentinel else Int.box(value.toInt).asInstanceOf[A]
      )
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      source
        .readIntPhysical(sentinel)
        .flatMap(value =>
          if (value == sentinel) Async.succeed(sentinel)
          else StreamError.callbackAsync(f(value.toInt)).map(_.toLong)
        )
    def readable(): Async[Boolean]                 = source.readable()
    override def reset(): Async[Unit]              = source.reset()
    override def setLimit(n: Long): Async[Boolean] = source.setLimit(n)
    override def setRepeat(): Async[Boolean]       = source.setRepeat()
    override def setSkip(n: Long): Async[Boolean]  = source.setSkip(n)
    override def skip(n: Long): Async[Unit]        = source.skip(n)
  }

  private[streams] final class MappedAsyncIntInt(val source: AsyncReader[Int], val f: Int => Int)
      extends AsyncReader[Int] {
    def close(): Async[Unit]                  = source.close()
    def isClosed: Async[Boolean]              = source.isClosed
    override def jvmType: JvmType             = JvmType.Int
    def read[A >: Int](sentinel: A): Async[A] =
      readIntPhysical(Long.MinValue).map(value =>
        if (value == Long.MinValue) sentinel else Int.box(value.toInt).asInstanceOf[A]
      )
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
      intMapPull.prepare(sentinel)
    override private[streams] def pollInt(
      sentinel: Long,
      onComplete: Runnable,
      observer: Async.LongStepFold[Unit]
    ): Unit = {
      intPoll.prepare(sentinel, observer)
      source.pollIntPhysical(sentinel, onComplete, intPoll)
    }
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Int <:< Int): Async[Int] =
      source.readIntsPhysical(dest, offset, length).map { count =>
        var i = 0
        while (i < count) { dest(offset + i) = mapInt(dest(offset + i)); i += 1 }
        count
      }
    def readable(): Async[Boolean]                 = source.readable()
    override def reset(): Async[Unit]              = source.reset()
    override def setLimit(n: Long): Async[Boolean] = source.setLimit(n)
    override def setRepeat(): Async[Boolean]       = source.setRepeat()
    override def setSkip(n: Long): Async[Boolean]  = source.setSkip(n)
    override def skip(n: Long): Async[Unit]        = source.skip(n)

    private val intMapPull = new IntMapPull
    private val intPoll    = new IntPoll

    private final class IntPoll extends Async.LongStepFold[Unit] {
      private var downstream: Async.LongStepFold[Unit] = null
      private var sentinel                             = 0L

      def failureLong(failure: Throwable): Unit       = downstream.failureLong(failure)
      def pendingLong(pollable: Pollable[Long]): Unit =
        downstream.pendingLong(
          pollable
            .map(value => if (value == sentinel) sentinel else mapInt(value.toInt).toLong)
            .asInstanceOf[Pollable[Long]]
        )
      def successLong(value: Long): Unit =
        if (value == sentinel) downstream.successLong(sentinel)
        else
          try downstream.successLong(mapInt(value.toInt).toLong)
          catch { case failure: Throwable => downstream.failureLong(failure) }
      override def trustedFailureLong(failure: Throwable): Unit = downstream.trustedFailureLong(failure)

      def prepare(end: Long, observer: Async.LongStepFold[Unit]): Unit = {
        downstream = observer
        sentinel = end
      }
    }

    private final class IntMapPull extends Pollable[Long] with Async.LongStepFold[Unit] {
      private var cause: Throwable = null
      private var kind             = 2
      private var sentinel         = 0L
      private var trusted          = false
      private var value            = 0L

      def failureLong(failure: Throwable): Unit                 = { cause = failure; kind = 1; trusted = false }
      def pendingLong(pollable: Pollable[Long]): Unit           = { val _ = pollable; kind = 2 }
      def successLong(result: Long): Unit                       = { value = result; kind = 0 }
      override def trustedFailureLong(failure: Throwable): Unit = { cause = failure; kind = 1; trusted = true }

      def prepare(end: Long): Async[Long] = {
        sentinel = end
        this
      }

      def poll(onComplete: Runnable): Async[Long] = {
        val read     = source.readIntPhysical(sentinel)
        val observed = read match {
          case pending: Pollable[?] =>
            try pending.asInstanceOf[Pollable[Long]].poll(onComplete)
            catch { case failure: Throwable => Async.fail(failure) }
          case _ => read
        }
        resetProbe()
        Async.foldLongStep(observed)(this)
        if (kind == 0) mapValue(value)
        else if (kind == 1) {
          if (trusted) Async.failTrusted(cause) else Async.fail(cause)
        } else observed.map(result => if (result == sentinel) sentinel else mapInt(result.toInt).toLong)
      }

      private def mapValue(result: Long): Async[Long] =
        if (result == sentinel) Async.succeed(sentinel)
        else
          try Async.succeed(mapInt(result.toInt).toLong)
          catch { case failure: Throwable => Async.fail(failure) }

      private def resetProbe(): Unit = {
        cause = null
        kind = 2
        trusted = false
      }
    }

    private def mapInt(value: Int): Int =
      try f(value)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
  }

  private[streams] final class MappedAsyncIntRef[B](val source: AsyncReader[Int], val f: Int => B)
      extends AsyncReader[B] {
    def close(): Async[Unit]                = source.close()
    def isClosed: Async[Boolean]            = source.isClosed
    def read[A >: B](sentinel: A): Async[A] = {
      val read =
        try source.readIntPhysical(Long.MinValue)
        catch {
          case error: StreamError if error.isTrusted => Async.failTrusted(error)
          case failure: Throwable                    => Async.fail(failure)
        }
      read.flatMap { value =>
        if (value == Long.MinValue) Async.succeed(sentinel)
        else mapRef(value.toInt)
      }
    }
    def readable(): Async[Boolean]                 = source.readable()
    override def reset(): Async[Unit]              = source.reset()
    override def setLimit(n: Long): Async[Boolean] = source.setLimit(n)
    override def setRepeat(): Async[Boolean]       = source.setRepeat()
    override def setSkip(n: Long): Async[Boolean]  = source.setSkip(n)
    override def skip(n: Long): Async[Unit]        = source.skip(n)

    private def mapRef(value: Int): Async[B] =
      try Async.succeed(f(value))
      catch {
        case error: StreamError => Async.fail(StreamError.untrusted(error))
        case failure: Throwable => Async.fail(failure)
      }
  }

  private[streams] final class MappedInt(
    val source: SyncReader[_],
    val f: AnyRef,
    val outType: JvmType,
    val inType: JvmType = JvmType.Int
  ) extends SyncReader[Any]
      with WrappedReader {
    private val outputDoubleScratch                                      = new Array[Double](1)
    private val outputLongScratch                                        = new Array[Long](1)
    override def jvmType: JvmType                                        = outType
    def isClosed: Boolean                                                = source.isClosed
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = pullInput(sentinel)
      if (v == sentinel) sentinel
      else f.asInstanceOf[Int => Int](v.toInt).toLong
    }
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int = {
      val v = pullInput(Long.MinValue)
      if (v == Long.MinValue) sentinel
      else f.asInstanceOf[Int => Int](v.toInt)
    }
    override def readByte(): Int = {
      val v = pullInput(Long.MinValue)
      if (v == Long.MinValue) -1 else f.asInstanceOf[Int => Int](v.toInt) & 0xff
    }
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int = {
      val v = pullInput(Long.MinValue)
      if (v == Long.MinValue) sentinel else f.asInstanceOf[Int => Int](v.toInt)
    }
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int = {
      val v = pullInput(Long.MinValue)
      if (v == Long.MinValue) sentinel else f.asInstanceOf[Int => Int](v.toInt)
    }
    private def pullInput(sentinel: Long): Long =
      if (inType eq JvmType.Boolean) {
        val value = source.readBooleanPhysical(-1)
        if (value < 0) sentinel else value.toLong
      } else if (inType eq JvmType.Byte) {
        val value = source.readBytePhysical()
        if (value < 0) sentinel else value.toLong
      } else if (inType eq JvmType.Char) {
        val value = source.readCharPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel else value.toLong
      } else if (inType eq JvmType.Short) {
        val value = source.readShortPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel else value.toLong
      } else source.readIntPhysical(sentinel)
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long =
      if (readLongsPhysical(outputLongScratch, 0, 1) < 0) sentinel else outputLongScratch(0)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = pullInput(Long.MinValue)
      if (v == Long.MinValue) sentinel
      else f.asInstanceOf[Int => Float](v.toInt).toDouble
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double =
      if (readDoublesPhysical(outputDoubleScratch, 0, 1) < 0) sentinel else outputDoubleScratch(0)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        val eof   = Long.MinValue
        var count = 0
        var value = pullInput(eof)
        while (count < length && value != eof) {
          dest(offset + count) = f.asInstanceOf[Int => Long](value.toInt)
          count += 1
          if (count < length) value = pullInput(eof)
        }
        if (count == 0) -1 else count
      }
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        val eof   = Long.MinValue
        var count = 0
        var value = pullInput(eof)
        while (count < length && value != eof) {
          dest(offset + count) = f.asInstanceOf[Int => Double](value.toInt)
          count += 1
          if (count < length) value = pullInput(eof)
        }
        if (count == 0) -1 else count
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = pullInput(Long.MinValue)
      if (v == Long.MinValue) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Int => Int](v.toInt) != 0).asInstanceOf[A1]
      } else if (outType eq JvmType.Byte) Byte.box(f.asInstanceOf[Int => Int](v.toInt).toByte).asInstanceOf[A1]
      else if (outType eq JvmType.Char) Char.box(f.asInstanceOf[Int => Int](v.toInt).toChar).asInstanceOf[A1]
      else if (outType eq JvmType.Short) Short.box(f.asInstanceOf[Int => Int](v.toInt).toShort).asInstanceOf[A1]
      else if (outType eq JvmType.Int) Int.box(f.asInstanceOf[Int => Int](v.toInt)).asInstanceOf[A1]
      else f.asInstanceOf[Int => AnyRef](v.toInt).asInstanceOf[A1]
    }
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readIntPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readIntPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b    = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Long](1)
        var read = readLongsPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readLongsPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloatPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloatPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b    = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Double](1)
        var read = readDoublesPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readDoublesPhysical(one, 0, 1)
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
      val s = Long.MinValue; var r = n; while (r > 0 && pullInput(s) != s) r -= 1
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: SyncInterpreter      = {
      val p = SyncInterpreter(source)
      p.addAdaptedMap(source.jvmType, outType, f)
      p
    }
  }

  /**
   * Wraps a Long-specialized source reader and applies a single erased map
   * function.
   */
  private[streams] final class MappedLong(
    val source: SyncReader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends SyncReader[Any]
      with WrappedReader {
    private val input                                                          = new Array[Long](1)
    private val outputDoubleScratch                                            = new Array[Double](1)
    private val outputLongScratch                                              = new Array[Long](1)
    override def jvmType: JvmType                                              = outType
    def isClosed: Boolean                                                      = source.isClosed
    private def pullInput(): Boolean                                           = source.readLongsPhysical(input, 0, 1) >= 0
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int =
      if (!pullInput()) sentinel else f.asInstanceOf[Long => Int](input(0))
    override def readByte(): Int =
      if (!pullInput()) -1 else f.asInstanceOf[Long => Byte](input(0)).toInt & 0xff
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int =
      if (!pullInput()) sentinel else f.asInstanceOf[Long => Char](input(0)).toInt
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int =
      if (!pullInput()) sentinel else f.asInstanceOf[Long => Short](input(0)).toInt
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long =
      if (!pullInput()) sentinel else f.asInstanceOf[Long => Int](input(0)).toLong
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long =
      if (readLongsPhysical(outputLongScratch, 0, 1) < 0) sentinel else outputLongScratch(0)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double =
      if (!pullInput()) sentinel else f.asInstanceOf[Long => Float](input(0)).toDouble
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double =
      if (readDoublesPhysical(outputDoubleScratch, 0, 1) < 0) sentinel else outputDoubleScratch(0)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        while (count < length && pullInput()) {
          dest(offset + count) = f.asInstanceOf[Long => Long](input(0))
          count += 1
        }
        if (count == 0) -1 else count
      }
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        while (count < length && pullInput()) {
          dest(offset + count) = f.asInstanceOf[Long => Double](input(0))
          count += 1
        }
        if (count == 0) -1 else count
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 =
      if (!pullInput()) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[Long => Int](input(0)) != 0).asInstanceOf[A1]
      } else f.asInstanceOf[Long => AnyRef](input(0)).asInstanceOf[A1]
    override def readUpToN[A1 >: Any](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var v = readIntPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readIntPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b    = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Long](1)
        var read = readLongsPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readLongsPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var v = readFloatPhysical(s)
        if (v == s) return Chunk.empty
        var i = 0
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloatPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b    = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val one  = new Array[Double](1)
        var read = readDoublesPhysical(one, 0, 1)
        var i    = 0
        while (read >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) read = readDoublesPhysical(one, 0, 1)
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
    def close(): Unit                       = source.close()
    override def reset(): Unit              = source.reset()
    override def skip(n: Long): Unit        = Reader.skipViaSentinel(this, n)
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: SyncInterpreter      = {
      val p = SyncInterpreter(source)
      p.addAdaptedMap(JvmType.Long, outType, f)
      p
    }
  }

  /**
   * Wraps a reference-type (AnyRef) source reader and applies a single erased
   * map function.
   */
  private[streams] final class MappedRef(
    val source: SyncReader[_],
    val f: AnyRef,
    val outType: JvmType
  ) extends SyncReader[Any]
      with WrappedReader {
    private val outputDoubleScratch                                            = new Array[Double](1)
    private val outputLongScratch                                              = new Array[Long](1)
    override def jvmType: JvmType                                              = outType
    def isClosed: Boolean                                                      = source.isClosed
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int = {
      val v = nextRef()
      if (v eq EndOfStream) sentinel
      else f.asInstanceOf[AnyRef => Int](v)
    }
    override def readByte(): Int = {
      val v = nextRef()
      if (v eq EndOfStream) -1
      else f.asInstanceOf[AnyRef => Byte](v).toInt & 0xff
    }
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int = {
      val v = nextRef()
      if (v eq EndOfStream) sentinel
      else f.asInstanceOf[AnyRef => Char](v).toInt
    }
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int = {
      val v = nextRef()
      if (v eq EndOfStream) sentinel
      else f.asInstanceOf[AnyRef => Short](v).toInt
    }
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
      val v = nextRef()
      if (v eq EndOfStream) sentinel
      else if (outType eq JvmType.Int)
        f.asInstanceOf[AnyRef => Int](v).toLong
      else if (outType eq JvmType.Char)
        f.asInstanceOf[AnyRef => Char](v).toLong
      else
        f.asInstanceOf[AnyRef => AnyRef](v).asInstanceOf[java.lang.Number].longValue()
    }
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long =
      if (readLongsPhysical(outputLongScratch, 0, 1) < 0) sentinel else outputLongScratch(0)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
      val v = nextRef()
      if (v eq EndOfStream) sentinel
      else if (outType eq JvmType.Float)
        f.asInstanceOf[AnyRef => Float](v).toDouble
      else if (outType eq JvmType.Char)
        f.asInstanceOf[AnyRef => Char](v).toDouble
      else
        f.asInstanceOf[AnyRef => AnyRef](v).asInstanceOf[java.lang.Number].doubleValue()
    }
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double =
      if (readDoublesPhysical(outputDoubleScratch, 0, 1) < 0) sentinel else outputDoubleScratch(0)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        var value = nextRef()
        while (count < length && (value ne EndOfStream)) {
          dest(offset + count) = f.asInstanceOf[AnyRef => Long](value)
          count += 1
          if (count < length) value = nextRef()
        }
        if (count == 0) -1 else count
      }
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        var count = 0
        var value = nextRef()
        while (count < length && (value ne EndOfStream)) {
          dest(offset + count) = f.asInstanceOf[AnyRef => Double](value)
          count += 1
          if (count < length) value = nextRef()
        }
        if (count == 0) -1 else count
      }
    }
    def read[A1 >: Any](sentinel: A1): A1 = {
      val v = nextRef()
      if (v eq EndOfStream) sentinel
      else if (outType eq JvmType.Boolean) {
        Boolean.box(f.asInstanceOf[AnyRef => Int](v) != 0).asInstanceOf[A1]
      } else f.asInstanceOf[AnyRef => AnyRef](v).asInstanceOf[A1]
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
      while (r > 0) { if (nextRef() eq EndOfStream) return; r -= 1 }
    }
    override def setSkip(n: Long): Boolean  = source.setSkip(n)
    override def setLimit(n: Long): Boolean = source.setLimit(n)
    override def setRepeat(): Boolean       = source.setRepeat()
    def toInterpreter: SyncInterpreter      = {
      val p = SyncInterpreter(source)
      p.addAdaptedMap(JvmType.AnyRef, outType, f)
      p
    }

    private def nextRef(): AnyRef = source.asInstanceOf[SyncReader[AnyRef]].read[AnyRef](EndOfStream)
  }

  /**
   * Repeats an inner reader forever. When the inner closes cleanly (no
   * exception), calls `reset()` and pulls again. On error, re-throws.
   */
  private[streams] final class Repeated[A](inner: SyncReader[A], guard: InterpreterGuard = null) extends SyncReader[A] {
    override def jvmType: JvmType  = inner.jvmType
    private var done: Boolean      = false
    private def resetInner(): Unit =
      try inner.reset()
      catch { case cause: Throwable => throw StreamError.attachCleanup(null, cause) }
    def isClosed: Boolean               = done
    def read[A1 >: A](sentinel: A1): A1 = {
      while (true) {
        try {
          val v = inner.read[Any](EndOfStream)
          if (guard ne null) guard.check()
          if (v.asInstanceOf[AnyRef] ne EndOfStream) return v.asInstanceOf[A1]
          // Clean close — reset and try again
          resetInner()
          if (guard ne null) guard.check()
        } catch {
          case e: Throwable => done = true; throw e
        }
      }
      sentinel // unreachable
    }
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
      while (true) {
        val v = inner.readIntPhysical(sentinel)
        if (guard ne null) guard.check()
        if (v != sentinel) return v
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return sentinel }
      }
      sentinel // unreachable
    }
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long = {
      val one = collisionFreeLongScratch
      if (readLongsPhysical(one, 0, 1) < 0) sentinel else one(0)
    }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
      while (true) {
        val v = inner.readFloatPhysical(sentinel)
        if (guard ne null) guard.check()
        if (v != sentinel) return v
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return sentinel }
      }
      sentinel // unreachable
    }
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
      val one = collisionFreeDoubleScratch
      if (readDoublesPhysical(one, 0, 1) < 0) sentinel else one(0)
    }
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = {
      while (true) {
        val value = inner.readBooleanPhysical(sentinel)
        if (guard ne null) guard.check()
        if (value != sentinel) return value
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return sentinel }
      }
      sentinel
    }
    override def readByte(): Int = {
      while (true) {
        val value = inner.readBytePhysical()
        if (guard ne null) guard.check()
        if (value >= 0) return value
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return -1 }
      }
      -1
    }
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int = {
      while (true) {
        val value = inner.readCharPhysical(sentinel)
        if (guard ne null) guard.check()
        if (value != sentinel) return value
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return sentinel }
      }
      sentinel
    }
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int = {
      while (true) {
        val value = inner.readShortPhysical(sentinel)
        if (guard ne null) guard.check()
        if (value != sentinel) return value
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return sentinel }
      }
      sentinel
    }
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) return 0
      while (true) {
        val count = inner.readLongsPhysical(dest, offset, length)
        if (guard ne null) guard.check()
        if (count > 0) return count
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return -1 }
      }
      -1
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) return 0
      while (true) {
        val count = inner.readDoublesPhysical(dest, offset, length)
        if (guard ne null) guard.check()
        if (count > 0) return count
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (closed) { resetInner(); if (guard ne null) guard.check() }
        else { done = true; return -1 }
      }
      -1
    }
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
      if (n <= 0) return Chunk.empty
      val chunk = inner.readUpToN[A1](n)
      if (guard ne null) guard.check()
      if (chunk.nonEmpty) chunk
      else {
        val closed = inner.isClosed
        if (guard ne null) guard.check()
        if (!closed) Chunk.empty
        else {
          resetInner()
          if (guard ne null) guard.check()
          val result = inner.readUpToN[A1](n)
          if (guard ne null) guard.check()
          result
        }
      }
    }
    override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)
    def close(): Unit                = { done = true; inner.close() }
    override def reset(): Unit       = { done = false; inner.reset() }
  }

  private final class AsyncRepeated[A](inner: AsyncReader[A]) extends AsyncReader[A] {
    override def jvmType: JvmType               = inner.jvmType
    private var closed                          = false
    private var resetting                       = false
    private var generation                      = 0L
    private var active: Pollable[_]             = null
    private var terminalFailure: Throwable      = null
    private var terminalFailed                  = false
    private var beforeAdmissionHook: () => Unit = null
    private var beforeLoopHook: () => Unit      = null
    private val longScratch                     = new Array[Long](1)
    private val doubleScratch                   = new Array[Double](1)

    private[streams] def beforeAdmissionForTest(hook: () => Unit): Unit = synchronized {
      beforeAdmissionHook = hook
    }

    private[streams] def beforeLoopForTest(hook: () => Unit): Unit = synchronized {
      beforeLoopHook = hook
    }

    private def runBeforeAdmissionHook(): Unit = {
      val hook = synchronized { val value = beforeAdmissionHook; beforeAdmissionHook = null; value }
      if (hook ne null) hook()
    }

    private def runBeforeLoopHook(): Unit = {
      val hook = synchronized { val value = beforeLoopHook; beforeLoopHook = null; value }
      if (hook ne null) hook()
    }

    private def cancelAndJoin(operation: Pollable[_]): Async[Unit] =
      if (operation eq null) Async.succeed(())
      else Async.cancelWithCleanup(operation)

    private def joinPrimary(primary: Throwable, cleanup: => Async[Unit]): Async[Unit] =
      cleanup.either.flatMap {
        case Right(_)        => replayFailure(primary)
        case Left(secondary) =>
          replayFailure(if (primary eq null) null else StreamError.attachCleanupReplay(primary, secondary))
      }

    private def makeCloseOwner(): MemoizedClose = {
      var operation: Pollable[_] = null
      new MemoizedClose(
        () =>
          synchronized {
            closed = true
            generation += 1
            operation = active
          },
        () => {
          val joined =
            cancelAndJoin(operation)
          joined.either.flatMap {
            case Right(_)      => inner.close()
            case Left(primary) =>
              joinPrimary(primary, inner.close())
          }
        }
      )
    }

    private var closeOwner = makeCloseOwner()

    def close(): Async[Unit] = {
      val owner = synchronized {
        if (resetting) {
          resetting = false
          generation += 1
        }
        closed = true
        closeOwner
      }
      owner.close()
    }
    def isClosed: Async[Boolean]   = Async.succeed(synchronized(closed || terminalFailed))
    def readable(): Async[Boolean] = {
      val token = synchronized {
        if (closed || terminalFailed || resetting || (active ne null)) None
        else Some(generation)
      }
      if (token.isEmpty) Async.succeed(false)
      else {
        val expected = token.get
        val reserved = Async
          .reschedule(() => inner.readable().flatMap(ready => if (ready) Async.succeed(true) else inner.isClosed))
          .asInstanceOf[Pollable[Boolean]]
        val operation = Async.cancelTo(reserved, () => Async.succeed(false))
        runBeforeAdmissionHook()
        val admitted = synchronized {
          if (!closed && !terminalFailed && !resetting && generation == expected && (active eq null)) {
            active = operation
            true
          } else false
        }
        val result =
          if (admitted) operation
          else Async.cancelWithCleanup(operation).map(_ => false)
        result.map { ready =>
          synchronized {
            if (active eq operation) active = null
            ready && !closed && !terminalFailed && !resetting && generation == expected
          }
        }.catchAll { cause =>
          val current = synchronized {
            if (active eq operation) active = null
            !closed && !terminalFailed && !resetting && generation == expected
          }
          if (current) replayFailure(cause) else Async.succeed(false)
        }
      }
    }

    private def readWith[B](sentinel: B)(pull: => Async[B])(isEof: B => Boolean): Async[B] = {
      val initial = synchronized {
        if (terminalFailed) Left(terminalFailure)
        else if (closed || resetting) Right(None)
        else if (active ne null) Left(new IllegalStateException("Only one Reader pull may be in flight"))
        else Right(Some(generation))
      }
      initial match {
        case Left(cause) => return replayFailure(cause)
        case Right(None) => return Async.succeed(sentinel)
        case _           => ()
      }
      val token                       = initial.toOption.flatten.get
      def current: Boolean            = synchronized(!closed && generation == token)
      def loop(budget: Int): Async[B] = {
        runBeforeLoopHook()
        if (!current) Async.succeed(sentinel)
        else
          pull.flatMap { value =>
            if (!current) Async.succeed(sentinel)
            else if (!isEof(value)) Async.succeed(value)
            else
              Async
                .superviseResetWithinClose(
                  () => inner.reset(),
                  () => inner.close()
                )
                .flatMap { _ =>
                  if (!current) Async.succeed(sentinel)
                  else if (budget > 0) loop(budget - 1)
                  else Async.reschedule(() => loop(255))
                }
          }
      }
      // Publish ownership before the first scheduler turn can construct or
      // start the underlying read. Close can therefore always observe every
      // admitted operation; a losing admission only has an unstarted
      // reservation to cancel.
      val reserved  = Async.reschedule(() => loop(255)).asInstanceOf[Pollable[B]]
      val operation = Async.cancelTo(reserved, () => Async.succeed(sentinel))
      runBeforeAdmissionHook()
      val admitted = synchronized {
        if (!closed && !resetting && generation == token && (active eq null)) { active = operation; true }
        else false
      }
      val result =
        if (admitted) operation
        else Async.cancelWithCleanup(operation).map(_ => sentinel)
      result.map { value => synchronized { if (active eq operation) active = null }; value }.catchAll { cause =>
        val accepted = synchronized {
          if (active eq operation) active = null
          if (!closed && generation == token) {
            terminalFailure = cause
            terminalFailed = true
            closed = true
            generation += 1
            true
          } else false
        }
        if (accepted) joinPrimary(cause, synchronized(closeOwner).closeClaimed()).flatMap(_ => Async.fail(cause))
        else Async.succeed(sentinel)
      }
    }

    def read[A1 >: A](sentinel: A1): Async[A1] =
      readWith[A1](sentinel)(inner.read[Any](EndOfStream).map(_.asInstanceOf[A1]))(value =>
        value.asInstanceOf[AnyRef] eq EndOfStream
      )

    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      readWith(sentinel)(inner.readBooleanPhysical(sentinel))(_ == sentinel)

    override def readByte(): Async[Int] = readWith(-1)(inner.readBytePhysical())(_ < 0)

    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      readWith(sentinel)(inner.readCharPhysical(sentinel))(_ == sentinel)

    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      readWith(sentinel)(inner.readShortPhysical(sentinel))(_ == sentinel)

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      readWith(sentinel)(inner.readIntPhysical(sentinel))(_ == sentinel)

    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      readLongsPhysical(longScratch, 0, 1).map(count => if (count < 0) sentinel else longScratch(0))

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      readWith(Double.MaxValue)(inner.readFloatPhysical(Double.MaxValue))(_ == Double.MaxValue)
        .map(value => if (value == Double.MaxValue) sentinel else value)

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      readDoublesPhysical(doubleScratch, 0, 1).map(count => if (count < 0) sentinel else doubleScratch(0))

    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        if (length == 0) Async.succeed(0)
        else readWith(-1)(inner.readLongsPhysical(dest, offset, length))(_ < 0)
      )

    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        if (length == 0) Async.succeed(0)
        else readWith(-1)(inner.readDoublesPhysical(dest, offset, length))(_ < 0)
      )

    override def reset(): Async[Unit] = {
      var token                        = Long.MinValue
      var previousOwner: MemoizedClose = null
      Async
        .deferCancelableWithCleanup(
          () => {
            val reserved = synchronized {
              if (resetting) Left(new IOException("Reader reset is already in progress"))
              else {
                resetting = true
                closed = true
                generation += 1
                token = generation
                previousOwner = closeOwner
                Right(())
              }
            }
            reserved match {
              case Left(cause) => Async.fail(cause)
              case Right(_)    =>
                val closing = previousOwner.closeClaimed()
                synchronized { if (resetting) token = generation }
                closing.either.flatMap {
                  case Left(cause) =>
                    synchronized { if (generation == token) resetting = false }
                    Async.fail(cause)
                  case Right(_) =>
                    val stillCurrent = synchronized(resetting && generation == token)
                    if (!stillCurrent) Async.fail(new IOException("Reader was closed during reset"))
                    else {
                      val supervised = Async.superviseResetUnit(
                        () => inner.reset(),
                        () => inner.close()
                      )
                      val nextOwner = new MemoizedClose(
                        () =>
                          synchronized {
                            closed = true
                            resetting = false
                            generation += 1L
                          },
                        () => Async.cancelWithCleanup(supervised)
                      )
                      val published = synchronized {
                        if (!resetting || generation != token) false
                        else { closeOwner = nextOwner; true }
                      }
                      if (!published) Async.fail(new IOException("Reader was closed during reset"))
                      else
                        supervised.either.flatMap {
                          case Left(cause) =>
                            synchronized { if (generation == token) resetting = false }
                            joinPrimary(cause, nextOwner.closeClaimed())
                          case Right(_) =>
                            val committed = synchronized {
                              if (generation != token || !resetting) false
                              else {
                                closed = false
                                resetting = false
                                active = null
                                terminalFailure = null
                                terminalFailed = false
                                true
                              }
                            }
                            if (committed) Async.succeed(())
                            else Async.fail(new IOException("Reader was closed during reset"))
                        }
                    }
                }
            }
          },
          () => synchronized(closeOwner).closeClaimed()
        )
        .flatten
    }
  }

  /**
   * Generic singleton — for reference types. Mode flag: 0 = fresh, 1 = taken, 2 =
   * repeat forever.
   */
  private[streams] final class SingletonGeneric[A](value: A) extends SyncReader[A] {
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
      else { if (m == 0) mode = 1; lowByte(value) }
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
  ) extends SyncReader[A] {
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

    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = {
      val m = mode
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); if (storedLong == 0L) 0 else 1 }
    }

    override def readByte(): Int = {
      val m = mode
      if (m == 1) -1
      else { if (m == 0) setMode(1); storedLong.toInt & 0xff }
    }

    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int = {
      val m = mode
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); storedLong.toInt }
    }

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); java.lang.Double.longBitsToDouble(storedLong) }
    }

    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else if (mode == 1) -1
      else {
        dest(offset) = java.lang.Double.longBitsToDouble(storedLong)
        if (mode == 0) setMode(1)
        1
      }
    }

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); storedLong }
    }

    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); storedLong }
    }

    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else if (mode == 1) -1
      else {
        dest(offset) = storedLong
        if (mode == 0) setMode(1)
        1
      }
    }

    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int = {
      val m = mode
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); storedLong.toInt }
    }

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
      val m = mode;
      if (m == 1) sentinel
      else { if (m == 0) setMode(1); java.lang.Float.intBitsToFloat(storedLong.toInt).toDouble }
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
    inner: SyncReader[A],
    skipN: Long,
    limitN: Long
  ) extends SyncReader[A] {
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

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
      if (remaining <= 0) return sentinel
      val v = inner.readIntPhysical(sentinel)
      if (v != sentinel) remaining -= 1
      v
    }

    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long = {
      val one = collisionFreeLongScratch
      if (readLongsPhysical(one, 0, 1) < 0) sentinel else one(0)
    }

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
      if (remaining <= 0) return sentinel
      val v = inner.readFloatPhysical(sentinel)
      if (v != sentinel) remaining -= 1
      v
    }

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
      val one = collisionFreeDoubleScratch
      if (readDoublesPhysical(one, 0, 1) < 0) sentinel else one(0)
    }

    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = {
      if (remaining <= 0) return sentinel
      val value = inner.readBooleanPhysical(sentinel)
      if (value != sentinel) remaining -= 1
      value
    }

    override def readByte(): Int = {
      if (remaining <= 0) return -1
      val value = inner.readBytePhysical()
      if (value >= 0) remaining -= 1
      value
    }

    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int = {
      if (remaining <= 0) return sentinel
      val value = inner.readCharPhysical(sentinel)
      if (value != sentinel) remaining -= 1
      value
    }

    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int = {
      if (remaining <= 0) return sentinel
      val value = inner.readShortPhysical(sentinel)
      if (value != sentinel) remaining -= 1
      value
    }

    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else if (remaining <= 0) -1
      else {
        val count = inner.readLongsPhysical(dest, offset, math.min(length.toLong, remaining).toInt)
        if (count > 0) remaining -= count
        count
      }
    }

    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else if (remaining <= 0) -1
      else {
        val count = inner.readDoublesPhysical(dest, offset, math.min(length.toLong, remaining).toInt)
        if (count > 0) remaining -= count
        count
      }
    }

    override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
      if (remaining <= 0 || n <= 0) return Chunk.empty
      val toRead = math.min(n.toLong, remaining).toInt
      val chunk  = inner.readUpToN[A1](toRead)
      remaining -= chunk.length
      chunk
    }

    override def skip(n: Long): Unit = {
      val count = math.min(math.max(0L, n), remaining)
      if (count > 0L) {
        inner.skip(count)
        remaining -= count
      }
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

  /** Produced by Stream.TakenWhile — wraps a reader with a predicate limit. */
  private[streams] final class TakenWhile[Elem](
    self: SyncReader[Elem],
    pred: Elem => Boolean,
    guard: InterpreterGuard = null
  ) extends SyncReader[Elem] {
    override def jvmType: JvmType          = self.jvmType
    private var doneSent: Boolean          = false
    private def keep(value: Elem): Boolean = {
      if (guard ne null) guard.check()
      val result = pred(value)
      if (guard ne null) guard.check()
      result
    }
    def isClosed: Boolean                  = doneSent || self.isClosed
    def read[A1 >: Elem](sentinel: A1): A1 =
      if (doneSent) sentinel
      else {
        val v = self.read[Any](EndOfStream)
        if (guard ne null) guard.check()
        if (v.asInstanceOf[AnyRef] eq EndOfStream) { doneSent = true; sentinel }
        else {
          val keep = pred(v.asInstanceOf[Elem])
          if (guard ne null) guard.check()
          if (keep) v.asInstanceOf[A1] else { doneSent = true; sentinel }
        }
      }
    override def readInt(sentinel: Long)(implicit ev: Elem <:< Int): Long =
      if (doneSent) sentinel
      else {
        val v = self.readIntPhysical(sentinel);
        if (guard ne null) guard.check()
        if (v != sentinel) { if (keep(v.toInt.asInstanceOf[Elem])) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    override def readLong(sentinel: Long)(implicit ev: Elem <:< Long): Long =
      if (readLongsPhysical(collisionFreeLongScratch, 0, 1) < 0) sentinel else collisionFreeLongScratch(0)
    override def readFloat(sentinel: Double)(implicit ev: Elem <:< Float): Double =
      if (doneSent) sentinel
      else {
        val v = self.readFloatPhysical(sentinel);
        if (guard ne null) guard.check()
        if (v != sentinel) { if (keep(v.toFloat.asInstanceOf[Elem])) v else { doneSent = true; sentinel } }
        else { doneSent = true; sentinel }
      }
    override def readDouble(sentinel: Double)(implicit ev: Elem <:< Double): Double =
      if (readDoublesPhysical(collisionFreeDoubleScratch, 0, 1) < 0) sentinel
      else collisionFreeDoubleScratch(0)
    override def readBoolean(sentinel: Int)(implicit ev: Elem <:< Boolean): Int =
      if (doneSent) sentinel
      else {
        val value = self.readBooleanPhysical(sentinel)
        if (guard ne null) guard.check()
        if (value == sentinel) { doneSent = true; sentinel }
        else if (keep((value != 0).asInstanceOf[Elem])) value
        else { doneSent = true; sentinel }
      }
    override def readByte(): Int =
      if (doneSent) -1
      else {
        val value = self.readBytePhysical()
        if (guard ne null) guard.check()
        if (value < 0) { doneSent = true; -1 }
        else if (keep(value.toByte.asInstanceOf[Elem])) value
        else { doneSent = true; -1 }
      }
    override def readChar(sentinel: Int)(implicit ev: Elem <:< Char): Int =
      if (doneSent) sentinel
      else {
        val value = self.readCharPhysical(sentinel)
        if (guard ne null) guard.check()
        if (value == sentinel) { doneSent = true; sentinel }
        else if (keep(value.toChar.asInstanceOf[Elem])) value
        else { doneSent = true; sentinel }
      }
    override def readShort(sentinel: Int)(implicit ev: Elem <:< Short): Int =
      if (doneSent) sentinel
      else {
        val value = self.readShortPhysical(sentinel)
        if (guard ne null) guard.check()
        if (value == sentinel) { doneSent = true; sentinel }
        else if (keep(value.toShort.asInstanceOf[Elem])) value
        else { doneSent = true; sentinel }
      }
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Elem <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) return 0
      if (doneSent) return -1
      var count = 0
      var stop  = false
      val input = collisionFreeLongScratch
      while (count < length && !stop) {
        if (self.readLongsPhysical(input, 0, 1) < 0) { doneSent = true; stop = true }
        else {
          if (guard ne null) guard.check()
          val value = input(0)
          if (keep(value.asInstanceOf[Elem])) { dest(offset + count) = value; count += 1 }
          else { doneSent = true; stop = true }
        }
      }
      if (count == 0 && doneSent) -1 else count
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Elem <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) return 0
      if (doneSent) return -1
      var count = 0
      var stop  = false
      val input = collisionFreeDoubleScratch
      while (count < length && !stop) {
        if (self.readDoublesPhysical(input, 0, 1) < 0) { doneSent = true; stop = true }
        else {
          if (guard ne null) guard.check()
          val value = input(0)
          if (keep(value.asInstanceOf[Elem])) { dest(offset + count) = value; count += 1 }
          else { doneSent = true; stop = true }
        }
      }
      if (count == 0 && doneSent) -1 else count
    }
    override def readUpToN[A1 >: Elem](n: Int): Chunk[A1] = {
      if (n <= 0 || doneSent) return Chunk.empty
      val et = jvmType
      if (et eq JvmType.Boolean) {
        val b = new ChunkBuilder.Boolean(); b.sizeHint(math.min(n, 64)); var i = 0;
        var v = readBooleanPhysical(-1)
        while (v >= 0 && i < n) { b.addOne(v != 0); i += 1; if (i < n) v = readBooleanPhysical(-1) }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Byte) {
        val b = new ChunkBuilder.Byte(); b.sizeHint(math.min(n, 64)); var i = 0; var v = readBytePhysical()
        while (v >= 0 && i < n) { b.addOne(v.toByte); i += 1; if (i < n) v = readBytePhysical() }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Char) {
        val b = new ChunkBuilder.Char(); b.sizeHint(math.min(n, 64)); var i = 0; var v = readCharPhysical(-1)
        while (v >= 0 && i < n) { b.addOne(v.toChar); i += 1; if (i < n) v = readCharPhysical(-1) }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Short) {
        val b = new ChunkBuilder.Short(); b.sizeHint(math.min(n, 64)); var i = 0
        val s = Int.MinValue; var v                                          = readShortPhysical(s)
        while (v != s && i < n) { b.addOne(v.toShort); i += 1; if (i < n) v = readShortPhysical(s) }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Int) {
        val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
        val s = Long.MinValue
        var i = 0
        var v = readIntPhysical(s)
        while (v != s && i < n) {
          b.addOne(v.toInt); i += 1
          if (i < n) v = readIntPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Long) {
        val b   = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
        val one = collisionFreeLongScratch; var i = 0; var count = readLongsPhysical(one, 0, 1)
        while (count >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) count = readLongsPhysical(one, 0, 1)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Float) {
        val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
        val s = Double.MaxValue
        var i = 0
        var v = readFloatPhysical(s)
        while (v != s && i < n) {
          b.addOne(v.toFloat); i += 1
          if (i < n) v = readFloatPhysical(s)
        }
        b.result().asInstanceOf[Chunk[A1]]
      } else if (et eq JvmType.Double) {
        val b   = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
        val one = collisionFreeDoubleScratch; var i = 0; var count = readDoublesPhysical(one, 0, 1)
        while (count >= 0 && i < n) {
          b.addOne(one(0)); i += 1
          if (i < n) count = readDoublesPhysical(one, 0, 1)
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
    def close(): Unit                = { doneSent = true; self.close() }
    override def reset(): Unit       = { doneSent = false; self.reset() }
  }

  /** Reader produced by unfolding a state function until `None`. */
  private[streams] final class Unfold[S, A](s: S, f: S => Option[(A, S)], elemType: JvmType) extends SyncReader[A] {
    override def jvmType: JvmType = elemType
    private var state: S          = s
    private var finished          = false
    def isClosed: Boolean         = finished
    private def nextValue(): Any  =
      if (finished) EndOfStream
      else
        f(state) match {
          case Some((value, next)) => state = next; value
          case None                => finished = true; EndOfStream
        }
    def read[A1 >: A](sentinel: A1): A1 = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else value.asInstanceOf[A1]
    }
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else if (value.asInstanceOf[Boolean]) 1 else 0
    }
    override def readByte(): Int = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) -1 else value.asInstanceOf[Byte].toInt & 0xff
    }
    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else value.asInstanceOf[Char].toInt
    }
    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else value.asInstanceOf[Short].toInt
    }
    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else value.asInstanceOf[Int].toLong
    }
    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else value.asInstanceOf[Long]
    }
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else value.asInstanceOf[Float].toDouble
    }
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
      val value = nextValue()
      if (value.asInstanceOf[AnyRef] eq EndOfStream) sentinel else value.asInstanceOf[Double]
    }
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        val value = nextValue()
        if (value.asInstanceOf[AnyRef] eq EndOfStream) -1 else { dest(offset) = value.asInstanceOf[Long]; 1 }
      }
    }
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int = {
      validateArrayRange(dest, offset, length)
      if (length == 0) 0
      else {
        val value = nextValue()
        if (value.asInstanceOf[AnyRef] eq EndOfStream) -1 else { dest(offset) = value.asInstanceOf[Double]; 1 }
      }
    }
    override def readUpToN[A1 >: A](n: Int): Chunk[A1] = super.readUpToN(n)
    override def skip(n: Long): Unit                   = Reader.skipViaSentinel(this, n)
    def close(): Unit                                  = finished = true
    override def reset(): Unit                         = { state = s; finished = false }
  }

  /** Native asynchronous Reader produced by unfolding a state callback. */
  private[streams] final class UnfoldAsync[S, A](initial: S, f: S => Async[Option[(A, S)]], elemType: JvmType)
      extends AsyncReader[A] {
    override def jvmType: JvmType                     = elemType
    private var state: S                              = initial
    private var epoch                                 = 0L
    private var closed                                = false
    private var eof                                   = false
    private var resetting                             = false
    private var resetReservation                      = 0L
    private var callbackInFlight                      = false
    private var activeRead: Completer[Unit]           = null
    private var activeRun: Async.Running[_]           = null
    private var activeOperation: Pollable[_]          = null
    private var activeOwner: AnyRef                   = null
    private var closingRead: Async[Unit]              = Async.succeed(())
    private var terminalFailure: Throwable            = null
    private var terminalFailed                        = false
    private var beforeRunRegistrationHook: () => Unit = null
    private var beforePullAcquiredHook: () => Unit    = null
    private var beforeSettleHook: () => Unit          = null

    private[streams] def beforeRunRegistrationForTest(hook: () => Unit): Unit = synchronized {
      beforeRunRegistrationHook = hook
    }

    private[streams] def beforePullAcquiredForTest(hook: () => Unit): Unit = synchronized {
      beforePullAcquiredHook = hook
    }

    private[streams] def beforeSettleForTest(hook: () => Unit): Unit = synchronized {
      beforeSettleHook = hook
    }

    private def runBeforeRunRegistrationHook(): Unit = {
      val hook = synchronized {
        val value = beforeRunRegistrationHook
        beforeRunRegistrationHook = null
        value
      }
      if (hook ne null) hook()
    }

    private def runBeforePullAcquiredHook(): Unit = {
      val hook = synchronized { val value = beforePullAcquiredHook; beforePullAcquiredHook = null; value }
      if (hook ne null) hook()
    }

    private def runBeforeSettleHook(): Unit = {
      val hook = synchronized { val value = beforeSettleHook; beforeSettleHook = null; value }
      if (hook ne null) hook()
    }

    private final class PullResource[B](
      val operation: Pollable[B],
      val owner: AnyRef,
      val completion: Completer[Unit]
    ) {
      var used = false
    }

    private def cancelAndJoin(operation: Pollable[_], owner: AnyRef): Async[Unit] =
      if (operation eq null) Async.succeed(())
      else {
        val cleanup = Async.withExecutionOwner(owner) {
          Async.cancelWithCleanup(operation)
        }
        Async.startRegisteredOwned(cleanup, owner)(_ => ())
      }

    private def releasePull(resource: PullResource[_]): Async[Unit] =
      if (resource.completion eq null) Async.succeed(())
      else {
        def settle(): Async[Unit] = {
          runBeforeSettleHook()
          synchronized {
            if (activeOperation eq resource.operation) {
              activeOperation = null
              activeRead = null
              activeOwner = null
            }
          }
          resource.completion.succeed(())
          Async.succeed(())
        }
        val cancel = synchronized(!resource.used)
        if (cancel)
          cancelAndJoin(resource.operation, resource.owner).either.flatMap {
            case Right(_)    => settle()
            case Left(cause) => settle().flatMap(_ => Async.fail(cause))
          }
        else settle()
      }

    private def usePull[B](resource: PullResource[B]): Async[B] = {
      synchronized {
        resource.used = true
      }
      resource.operation
    }

    private def join(left: Async[Unit], right: => Async[Unit]): Async[Unit] =
      left.either.flatMap {
        case Right(_)      => right
        case Left(primary) =>
          right.either.flatMap {
            case Right(_)        => Async.fail(primary)
            case Left(secondary) =>
              Async.fail(
                if ((primary eq null) || (secondary eq null)) primary
                else StreamError.attachCleanupReplay(primary, secondary)
              )
          }
      }

    private def makeCloseOwner(): MemoizedClose = {
      var operation: Pollable[_] = null
      var run: Pollable[_]       = null
      var operationOwner: AnyRef = null
      new MemoizedClose(
        () =>
          synchronized {
            closed = true
            epoch += 1
            operation = activeOperation
            run = activeRun
            operationOwner = activeOwner
            closingRead = if (activeRead eq null) Async.succeed(()) else activeRead
          },
        () => join(cancelAndJoin(operation, operationOwner), join(cancelAndJoin(run, operationOwner), closingRead)),
        isReentrant = () => Async.isExecutionOwner(synchronized(operationOwner))
      )
    }

    private var closeOwner = makeCloseOwner()

    private def claimClose(): Async[Unit] = {
      val owner = synchronized {
        if (resetting) {
          resetting = false
          resetReservation += 1
          epoch += 1
        }
        closed = true
        closeOwner
      }
      owner.close()
    }

    def close(): Async[Unit] = Async.deferCancelable(() => claimClose(), () => ()).flatten

    def isClosed: Async[Boolean] = Async.deferCancelable(() => synchronized(closed || eof), () => ())

    def readable(): Async[Boolean] = Async.deferCancelable(
      () => synchronized(!closed && !eof && !terminalFailed && !resetting && !callbackInFlight),
      () => ()
    )

    private def pullValue[B](sentinel: B)(convert: A => B): Async[B] =
      Async
        .bracketSync[PullResource[B], B](
          () => {
            var token: Long                 = Long.MinValue
            var snapshot: S                 = initial
            var owner: MemoizedClose        = null
            val pullOwner                   = new AnyRef
            var completion: Completer[Unit] = null
            val installed                   = new Completer[Async[B]]
            var reservation: Pollable[B]    = null
            val admission                   = synchronized {
              if (terminalFailed) Left(terminalFailure)
              else if (closed || eof || resetting) Right(false)
              else if (callbackInFlight) Left(new IllegalStateException("Only one Reader pull may be in flight"))
              else {
                callbackInFlight = true
                token = epoch
                snapshot = state
                owner = closeOwner
                completion = new Completer[Unit]
                reservation = Async
                  .withExecutionOwner(pullOwner)(
                    Async.cancelTo(
                      installed.peek
                        .flatMap((effect: Async[B]) => effect)
                        .asInstanceOf[Pollable[B]],
                      () => owner.closeClaimed().map(_ => sentinel)
                    )
                  )
                  .asInstanceOf[Pollable[B]]
                activeRead = completion
                activeOwner = pullOwner
                activeOperation = reservation
                Right(true)
              }
            }
            admission match {
              case Left(cause) =>
                new PullResource(
                  Async.reschedule(() => Async.fail(cause).asInstanceOf[Async[B]]).asInstanceOf[Pollable[B]],
                  null,
                  null
                )
              case Right(false) =>
                new PullResource(
                  Async.reschedule(() => Async.succeed(sentinel)).asInstanceOf[Pollable[B]],
                  null,
                  null
                )
              case Right(true) =>
                var started: Async.Running[B] = null
                val body                      = Async.bracketSync[Unit, B](
                  () => (),
                  (_: Unit) =>
                    Async
                      .reschedule(() => StreamError.callbackAsync(f(snapshot)))
                      .flatMap { result =>
                        synchronized {
                          if (!closed && epoch == token && callbackInFlight) {
                            result match {
                              case Some((value, next)) => state = next; Async.succeed(convert(value))
                              case None                => eof = true; Async.succeed(sentinel)
                            }
                          } else Async.succeed(sentinel)
                        }
                      }
                      .catchAll { cause =>
                        val accepted = synchronized {
                          if (!closed && epoch == token && callbackInFlight) {
                            closed = true
                            terminalFailure = cause
                            terminalFailed = true
                            epoch += 1
                            true
                          } else false
                        }
                        if (accepted) Async.fail(cause) else Async.succeed(sentinel)
                      },
                  (_: Unit) => {
                    synchronized {
                      callbackInFlight = false
                      if (activeRun eq started) activeRun = null
                    }
                    Async.succeed(())
                  }
                )
                started = Async.startRegisteredOwned(body, pullOwner) { running =>
                  started = running
                  runBeforeRunRegistrationHook()
                  synchronized {
                    activeRun = running
                  }
                  installed.succeed(running)
                }
                runBeforePullAcquiredHook()
                new PullResource(reservation, pullOwner, completion)
            }
          },
          usePull,
          releasePull
        )

    def read[B >: A](sentinel: B): Async[B] = pullValue(sentinel)(_.asInstanceOf[B])

    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Async[Int] =
      pullValue(sentinel)(value => if (value.asInstanceOf[Boolean]) 1 else 0)

    override def readByte(): Async[Int] = pullValue(-1)(value => value.asInstanceOf[Byte].toInt & 0xff)

    override def readChar(sentinel: Int)(implicit ev: A <:< Char): Async[Int] =
      pullValue(sentinel)(_.asInstanceOf[Char].toInt)

    override def readShort(sentinel: Int)(implicit ev: A <:< Short): Async[Int] =
      pullValue(sentinel)(_.asInstanceOf[Short].toInt)

    override def readInt(sentinel: Long)(implicit ev: A <:< Int): Async[Long] =
      pullValue(sentinel)(_.asInstanceOf[Int].toLong)

    override def readLong(sentinel: Long)(implicit ev: A <:< Long): Async[Long] =
      pullValue(sentinel)(_.asInstanceOf[Long])

    override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Async[Double] =
      pullValue(sentinel)(_.asInstanceOf[Float].toDouble)

    override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Async[Double] =
      pullValue(sentinel)(_.asInstanceOf[Double])

    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        if (length == 0) Async.succeed(0)
        else pullValue(-1) { value => dest(offset) = value.asInstanceOf[Long]; 1 }
      )

    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Async[Int] =
      validateArrayRangeAsync(dest, offset, length).flatMap(_ =>
        if (length == 0) Async.succeed(0)
        else pullValue(-1) { value => dest(offset) = value.asInstanceOf[Double]; 1 }
      )

    override def reset(): Async[Unit] = {
      var token                   = Long.MinValue
      var reservation             = Long.MinValue
      var previous: MemoizedClose = null
      Async
        .deferCancelableWithCleanup(
          () => {
            val reserved = synchronized {
              if (resetting) Left(new IOException("Reader reset is already in progress"))
              else if (Async.isExecutionOwner(activeOwner))
                Left(new IOException("Reader reset cannot run reentrantly from an active pull"))
              else {
                resetting = true
                resetReservation += 1
                reservation = resetReservation
                closed = true
                epoch += 1
                token = epoch
                previous = closeOwner
                Right(())
              }
            }
            reserved match {
              case Left(cause) => Async.fail(cause)
              case Right(_)    =>
                val closing = previous.closeClaimed()
                synchronized {
                  if (resetting && resetReservation == reservation) token = epoch
                }
                closing.either.flatMap {
                  case Left(cause) =>
                    synchronized {
                      if (resetting && resetReservation == reservation) resetting = false
                    }
                    Async.fail(cause)
                  case Right(_) =>
                    val nextOwner = makeCloseOwner()
                    val committed = synchronized {
                      if (epoch != token || !resetting || resetReservation != reservation) false
                      else {
                        state = initial
                        closed = false
                        eof = false
                        resetting = false
                        callbackInFlight = false
                        activeRead = null
                        activeRun = null
                        activeOperation = null
                        activeOwner = null
                        closingRead = Async.succeed(())
                        terminalFailure = null
                        terminalFailed = false
                        closeOwner = nextOwner
                        true
                      }
                    }
                    if (committed) Async.succeed(())
                    else Async.fail(new IOException("Reader was closed during reset"))
                }
            }
          },
          () => {
            val owner = synchronized(previous)
            if (owner eq null) Async.succeed(()) else owner.closeClaimed()
          }
        )
        .flatten
    }

    private[streams] def invalidateRunRegistrationForTest(): Unit = synchronized {
      closed = true
      epoch += 1
    }
  }

  private[streams] def invalidateUnfoldRunRegistrationForTest(reader: AsyncReader[_]): Unit = reader match {
    case unfold: UnfoldAsync[_, _] => unfold.invalidateRunRegistrationForTest()
    case _                         => ()
  }

  /**
   * Marker trait for thin reader wrappers (MappedXxx, FilteredXxx) that apply a
   * single map function or filter predicate directly. `toInterpreter` upgrades
   * a wrapper to a full SyncInterpreter when another operation cannot remain a
   * zero-allocation direct wrapper.
   */
  private[streams] sealed trait WrappedReader {

    /** The reader this wrapper maps or filters. */
    def source: SyncReader[_]

    /**
     * Rebuilds this wrapper as a `SyncInterpreter` that can take more
     * operations.
     */
    def toInterpreter: SyncInterpreter
  }
}
