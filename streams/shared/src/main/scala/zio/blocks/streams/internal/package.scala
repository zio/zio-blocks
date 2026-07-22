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

package object internal extends InternalVersionSpecific {

  /**
   * Interpreter storage lane index (0=Int, 1=Long, 2=Float, 3=Double, 4=Ref).
   */
  type Lane = Int

  /** Packed interpreter state in a single Long (56 bits used). */
  type StreamState = Long

  /** Operation tag for the Interpreter (8-bit value). */
  type OpTag = Int

  /**
   * Internal sentinel object used instead of `null` to detect end-of-stream in
   * the AnyRef lane. Using a dedicated object instead of `null` allows streams
   * to contain `null` elements without them being confused with end-of-stream.
   */
  private[streams] val EndOfStream: AnyRef = new AnyRef {
    override def toString: String = "EndOfStream"
  }

  /**
   * Returns `true` if `t` is a throwable that ordinary recovery/conversion
   * logic may catch — i.e. a genuine defect — and `false` for throwables that
   * must be left to propagate.
   *
   * Two families must never be caught for recovery:
   *
   *   - Fatal JVM errors (`VirtualMachineError` such as `OutOfMemoryError` /
   *     `StackOverflowError`, `ThreadDeath`, `InterruptedException`,
   *     `LinkageError`). Recovering from these is unsound; the process/thread
   *     is no longer in a trustworthy state.
   *   - Control throwables ([[scala.util.control.ControlThrowable]], which
   *     includes [[StreamError]] and [[SinkError]]). These encode non-local
   *     control flow / the typed error channel and must only be observed by the
   *     layer that uses them as control flow (e.g. `Stream.run`); absorbing
   *     them elsewhere corrupts the error channel.
   *
   * We delegate to `scala.util.control.NonFatal.apply`, whose `match` performs
   * exactly these `isInstanceOf`-style checks and returns a `Boolean` directly.
   * It is the zero-allocation form: only the `case NonFatal(e)` *extractor*
   * (`unapply`) allocates a `Some`; calling `NonFatal(t)` in boolean position
   * does not. Delegating also avoids referencing the (now deprecated for
   * throwing) `java.lang.ThreadDeath` symbol directly, which keeps the source
   * warning-clean under `-Werror` across every Scala version / platform while
   * preserving the exact fatal-throwable taxonomy.
   */
  private[streams] def isCatchable(t: Throwable): Boolean = scala.util.control.NonFatal(t)

  /**
   * Lossless end-of-stream test for the `Long` specialized lane.
   *
   * `Long` has no spare bit pattern to reserve as an end-of-stream sentinel, so
   * a returned value that equals `sentinel` is ambiguous — it may be a real
   * element (e.g. a stream legitimately containing `Long.MaxValue`) or genuine
   * EOF. The value comparison is necessary but not sufficient; we additionally
   * consult the reader's out-of-band [[io.Reader.lastReadWasEOF]] flag, which
   * the read that produced `v` set. Using this everywhere a `Long` lane is
   * drained prevents silent truncation of streams that contain the sentinel
   * value (BUG-004 / AdversarialSentinelSpec). The fast path is unchanged for
   * ordinary data: the flag is only consulted on the rare value/sentinel
   * collision.
   */
  @inline private[streams] def longEOF(reader: io.Reader[_], v: Long, sentinel: Long): Boolean =
    v == sentinel && reader.lastReadWasEOF

  /**
   * Lossless end-of-stream test for the `Double`/`Float` specialized lanes.
   * Like [[longEOF]], but uses raw bit comparison in addition to `==` so that a
   * NaN sentinel (whose `==` is always `false`) is still detectable and so that
   * a real `NaN`/`-0.0` element is never confused with the sentinel. EOF still
   * additionally requires the reader's [[io.Reader.lastReadWasEOF]] flag.
   */
  @inline private[streams] def doubleEOF(reader: io.Reader[_], v: Double, sentinel: Double): Boolean =
    (v == sentinel ||
      java.lang.Double.doubleToRawLongBits(v) == java.lang.Double.doubleToRawLongBits(sentinel)) &&
      reader.lastReadWasEOF

  /**
   * Adds `secondary` to `primary` as a suppressed exception, guarding against
   * `null`s and self-suppression (which `Throwable.addSuppressed` rejects with
   * an `IllegalArgumentException`).
   */
  private[streams] def addSuppressedSafe(primary: Throwable, secondary: Throwable): Unit =
    if ((primary ne null) && (secondary ne null) && (primary ne secondary)) primary.addSuppressed(secondary)

  /**
   * A typed-error carrier is the internal control throwable that transports the
   * typed error channel ([[StreamError]] for the stream side, [[SinkError]] for
   * the sink side). `Stream.run` catches these and projects them to `Left(e)`;
   * any other throwable is an untyped defect that propagates as a thrown
   * exception.
   */
  private[streams] def isTypedErrorCarrier(t: Throwable): Boolean =
    t.isInstanceOf[StreamError] || t.isInstanceOf[SinkError]

  /**
   * Combines two in-flight failures losslessly into the single throwable that
   * should propagate, never swallowing either (Principle 4).
   *
   *   - If either is `null`, the other wins.
   *   - If the existing `primary` is a typed-error carrier (destined for
   *     `Left(e)`) but `secondary` is an untyped defect, the DEFECT wins and
   *     the carrier is attached to it as suppressed. Otherwise the carrier
   *     would be projected to `Left` by `run` and the untyped defect — together
   *     with its stack trace — would vanish (ITER-3b /
   *     AdversarialCleanupErrorIntegritySpec).
   *   - Otherwise `primary` wins and `secondary` is suppressed onto it.
   */
  private[streams] def combineFailures(primary: Throwable, secondary: Throwable): Throwable =
    if (primary eq null) secondary
    else if (secondary eq null) primary
    else if (isTypedErrorCarrier(primary) && !isTypedErrorCarrier(secondary)) {
      addSuppressedSafe(secondary, primary)
      secondary
    } else {
      addSuppressedSafe(primary, secondary)
      primary
    }

  /**
   * Runs `cleanup` while a `primary` throwable is in flight, applying
   * try-with-resources suppression semantics, and returns the throwable that
   * should ultimately propagate (or `null` if none).
   *
   *   - `primary` succeeds, `cleanup` succeeds → returns `null`-or-`primary`
   *     unchanged.
   *   - `cleanup` throws and `primary` is `null` → the cleanup failure becomes
   *     the primary.
   *   - `cleanup` throws and `primary` is non-`null` → the cleanup failure is
   *     suppressed onto `primary`, which is returned.
   *
   * Callers throw the result if it is non-`null`. This never swallows a
   * throwable (Principle 4) and, via [[combineFailures]], lets an untyped
   * cleanup defect win over an in-flight typed-error carrier so the defect is
   * not silently projected to `Left` by `run`.
   */
  private[streams] def cleanupWithPrimary(primary: Throwable)(cleanup: => Unit): Throwable =
    try { cleanup; primary }
    catch { case secondary: Throwable => combineFailures(primary, secondary) }

  /**
   * Runs `first` then `second`, guaranteeing both run even if `first` throws,
   * and applying try-with-resources suppression: if both throw, `second`'s
   * failure is suppressed onto `first`'s and `first`'s propagates; if only one
   * throws, that one propagates. Unlike `try first finally second`, this never
   * lets `second`'s failure silently discard `first`'s.
   */
  private[streams] def runBoth(first: => Unit)(second: => Unit): Unit = {
    var primary: Throwable = null
    try first
    catch { case t: Throwable => primary = t }
    val toThrow = cleanupWithPrimary(primary)(second)
    if (toThrow ne null) throw toThrow
  }

  /**
   * Element-wise `readUpToN` for pass-through wrapper readers whose per-element
   * pulls carry semantics a bulk delegation would bypass — e.g. the recovery
   * wrappers (`CatchAllReader` / `CatchDefectReader`), where delegating the
   * whole bulk read to the upstream silently loses the prefix the upstream
   * buffered before a mid-chunk failure (the partial chunk inside the
   * upstream's `readUpToN` is unreachable from outside; BUG-A02).
   *
   * Pulls each element through `reader`'s own (virtual) `read*` methods so the
   * wrapper's semantics apply at element granularity, dispatching on
   * [[io.Reader.jvmType]] to stay unboxed on the primitive lanes, and checking
   * `readable()` between elements to preserve `readUpToN`'s
   * no-additional-blocking contract.
   */
  private[streams] def elementWiseReadUpToN[A](reader: io.Reader[A], n: Int): zio.blocks.chunk.Chunk[A] = {
    import zio.blocks.chunk.{Chunk, ChunkBuilder}
    import zio.blocks.streams.JvmType
    if (n <= 0) return Chunk.empty
    val et = reader.jvmType
    if (et eq JvmType.Int) {
      val b = new ChunkBuilder.Int()
      val s = Long.MinValue
      var v = pullInt(reader, s)
      if (v == s) return Chunk.empty
      b.sizeHint(math.min(n, 64))
      var i = 0
      while (v != s && i < n) {
        b.addOne(v.toInt); i += 1
        if (i < n) v = if (reader.readable()) pullInt(reader, s) else s
      }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Long) {
      val b = new ChunkBuilder.Long()
      val s = Long.MaxValue
      var v = pullLong(reader, s)
      if (longEOF(reader, v, s)) return Chunk.empty
      b.sizeHint(math.min(n, 64))
      var i    = 0
      var live = true
      while (live && !longEOF(reader, v, s) && i < n) {
        b.addOne(v); i += 1
        if (i < n) { if (reader.readable()) v = pullLong(reader, s) else live = false }
      }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Float) {
      val b = new ChunkBuilder.Float()
      val s = Double.MaxValue
      var v = pullFloat(reader, s)
      if (v == s) return Chunk.empty
      b.sizeHint(math.min(n, 64))
      var i = 0
      while (v != s && i < n) {
        b.addOne(v.toFloat); i += 1
        if (i < n) v = if (reader.readable()) pullFloat(reader, s) else s
      }
      b.result().asInstanceOf[Chunk[A]]
    } else if (et eq JvmType.Double) {
      val b = new ChunkBuilder.Double()
      val s = Double.MaxValue
      var v = pullDouble(reader, s)
      if (doubleEOF(reader, v, s)) return Chunk.empty
      b.sizeHint(math.min(n, 64))
      var i    = 0
      var live = true
      while (live && !doubleEOF(reader, v, s) && i < n) {
        b.addOne(v); i += 1
        if (i < n) { if (reader.readable()) v = pullDouble(reader, s) else live = false }
      }
      b.result().asInstanceOf[Chunk[A]]
    } else {
      val first = reader.read[Any](EndOfStream)
      if (first.asInstanceOf[AnyRef] eq EndOfStream) return Chunk.empty
      if (n == 1) return Chunk.single(first.asInstanceOf[A])
      val b = ChunkBuilder.make[A](math.min(n, 64))
      b += first.asInstanceOf[A]
      var i = 1
      while (i < n && reader.readable()) {
        val v = reader.read[Any](EndOfStream)
        if (v.asInstanceOf[AnyRef] eq EndOfStream) return b.result()
        b += v.asInstanceOf[A]
        i += 1
      }
      b.result()
    }
  }
}
