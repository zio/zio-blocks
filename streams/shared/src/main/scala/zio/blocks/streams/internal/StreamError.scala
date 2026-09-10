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

package zio.blocks.streams.internal

import zio.blocks.async._

/**
 * Wraps a non-Throwable error value so it can propagate through the
 * Reader/SyncInterpreter stack via exceptions. Used by
 * [[zio.blocks.streams.Stream.fail]] and caught by
 * [[zio.blocks.streams.Stream.run]]. The 4th constructor arg
 * (`writableStackTrace=false`) disables stack trace capture for performance.
 */
final class StreamError private (
  val value: Any,
  private val provenance: AnyRef,
  private val replay: AnyRef,
  private val origin: AnyRef
) extends Exception(null, null, true, false) {
  def this(value: Any) = this(value, StreamError.Untrusted, new AnyRef, StreamError.StreamOrigin)
  private[streams] var cleanupFailed: Boolean                     = false
  private[streams] def isTrusted: Boolean                         = provenance eq StreamError.Trusted
  private[streams] def isSinkOrigin: Boolean                      = origin eq StreamError.SinkOrigin
  private[streams] def replayEquivalent(that: Throwable): Boolean = that match {
    case error: StreamError => replay eq error.replay
    case _                  => false
  }
}

private[streams] object StreamError {
  private object Trusted
  private object Untrusted
  private object StreamOrigin
  private object SinkOrigin

  /** Creates a typed failure owned by the stream runtime. */
  def source(value: Any): StreamError = new StreamError(value, Trusted, new AnyRef, StreamOrigin)

  /** Creates a typed failure owned by a sink. */
  def sink(value: Any): StreamError = new StreamError(value, Trusted, new AnyRef, SinkOrigin)

  /**
   * Marks a StreamError escaping user code as a defect without exposing
   * forgeable provenance.
   */
  def untrusted(error: StreamError): StreamError = {
    val result = new StreamError(error.value, Untrusted, new AnyRef, error.origin)
    if (error.cleanupFailed) result.cleanupFailed = true
    val suppressed = error.getSuppressed
    var i          = 0
    while (i < suppressed.length) {
      result.addSuppressed(suppressed(i))
      i += 1
    }
    result
  }

  /** Maps an internally trusted typed error while retaining replay identity. */
  def mapped(error: StreamError, value: Any): StreamError =
    new StreamError(value, error.provenance, error.replay, error.origin)

  def callback[A](body: => A): A =
    try body
    catch { case error: StreamError => throw untrusted(error) }

  /** Classifies both ways an Async-returning user callback can fail. */
  def callbackAsync[A](body: => Async[A]): Async[A] = {
    val effect =
      try body
      catch { case error: StreamError => return Async.fail(untrusted(error)) }
    effect.mapError {
      case error: StreamError => untrusted(error)
      case failure            => failure
    }
  }

  def callbackFailure(failure: Throwable): Throwable = failure match {
    case error: StreamError => untrusted(error)
    case _                  => failure
  }

  /**
   * Sink.create callbacks may pull the supplied reader; preserve only errors
   * that the runtime already marked as trusted reader-origin failures.
   */
  def readerCallback[A](body: => A): A =
    try body
    catch {
      case error: StreamError if error.isTrusted => throw error
      case error: StreamError                    => throw untrusted(error)
    }

  def replayEquivalent(left: Throwable, right: Throwable): Boolean =
    (left eq right) || (left match {
      case error: StreamError => error.replayEquivalent(right)
      case _                  => false
    })

  /**
   * True when `replay` is only a plain replay of `primary` and carries no
   * cleanup failure that still has to cross the boundary.
   */
  def ignorableReplay(primary: Throwable, replay: Throwable): Boolean =
    replayEquivalent(primary, replay) && !cleanupBearing(replay)

  private def cleanupBearing(failure: Throwable): Boolean = failure match {
    case error: StreamError => error.cleanupFailed
    case _                  => failure.getSuppressed.nonEmpty
  }

  private def containsIdentity(failure: Throwable, target: Throwable): Boolean = {
    val visited                           = new java.util.IdentityHashMap[Throwable, java.lang.Boolean]()
    def loop(current: Throwable): Boolean =
      if (current eq target) true
      else if (visited.put(current, java.lang.Boolean.TRUE) ne null) false
      else {
        val suppressed = current.getSuppressed
        var i          = 0
        while (i < suppressed.length) {
          if (loop(suppressed(i))) return true
          i += 1
        }
        false
      }
    loop(failure)
  }

  /**
   * Copies cleanup state carried by a replay-equivalent StreamError onto the
   * selected primary. Mapped errors can share replay identity without sharing
   * mutable cleanup state.
   */
  private def propagateReplayCleanup(primary: Throwable, replay: Throwable): Unit =
    (primary, replay) match {
      case (target: StreamError, source: StreamError) if source.cleanupFailed =>
        target.cleanupFailed = true
        val suppressed = source.getSuppressed
        var i          = 0
        while (i < suppressed.length) {
          val cause = suppressed(i)
          if ((cause ne target) && !target.getSuppressed.exists(_ eq cause) && !containsIdentity(cause, target))
            target.addSuppressed(cause)
          i += 1
        }
      case _ => ()
    }

  /** Attaches a cleanup failure without changing the primary failure. */
  def attachCleanup(primary: Throwable, cleanup: Throwable): Throwable = {
    val result = if (primary eq null) cleanup else primary
    result match {
      case error: StreamError => error.cleanupFailed = true
      case _                  => ()
    }
    if (
      (primary ne null) && (cleanup ne null) && (primary ne cleanup) && !primary.getSuppressed.exists(_ eq cleanup) &&
      !containsIdentity(cleanup, primary)
    )
      primary.addSuppressed(cleanup)
    result
  }

  /** Aggregates cleanup at a boundary that may replay its primary failure. */
  def attachCleanupReplay(primary: Throwable, cleanup: Throwable): Throwable =
    if ((primary ne null) && replayEquivalent(primary, cleanup)) {
      propagateReplayCleanup(primary, cleanup)
      primary
    } else attachCleanup(primary, cleanup)

  /**
   * Suppresses a non-cleanup lifecycle failure, ignoring a replay of the
   * primary.
   */
  def attachSuppressedReplay(primary: Throwable, secondary: Throwable): Throwable =
    if (primary eq null) secondary
    else {
      if (replayEquivalent(primary, secondary)) propagateReplayCleanup(primary, secondary)
      else if (!primary.getSuppressed.exists(_ eq secondary) && !containsIdentity(secondary, primary))
        primary.addSuppressed(secondary)
      primary
    }
}
