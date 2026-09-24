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

import zio.blocks.async.{Async, _}
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader

private[streams] final class MappedNativeAsyncIntEitherLongFold[E](
  reader: Reader.AsyncReader[Int],
  map: Int => Async[Int],
  private var acc: Long,
  fold: (Long, Int) => Async[Long]
) extends Pollable[Either[E, Long]]
    with Runnable
    with Async.IntStepFold[Unit]
    with Async.LongStepFold[Unit] {
  private var cause: Throwable = null
  private var intValue: Int    = 0
  // 0 = failure, 1 = pending, 2 = success
  private var kind: Int        = 1
  private var longValue: Long  = 0L
  private var trusted: Boolean = false
  private var wake: Runnable   = null

  def failureInt(failure: Throwable): Unit        = failed(failure, isTrusted = false)
  def failureLong(failure: Throwable): Unit       = failed(failure, isTrusted = false)
  def pendingInt(pollable: Pollable[Int]): Unit   = { val _ = pollable; kind = 1 }
  def pendingLong(pollable: Pollable[Long]): Unit = { val _ = pollable; kind = 1 }

  def poll(onComplete: Runnable): Async[Either[E, Long]] = {
    var budget = 1024
    while (budget > 0) {
      val read =
        try reader.readIntPhysical(Long.MinValue)
        catch {
          case error: StreamError if error.isTrusted => return Async.failTrusted(error)
          case failure: Throwable                    => return Async.fail(failure)
        }
      resetStep()
      Async.foldLongStep(read)(this)
      if (kind == 1)
        return read.flatMap { value =>
          if (value == Long.MinValue) Async.succeed(Right(acc))
          else
            StreamError
              .callbackAsync(map(value.toInt))
              .flatMap(mapped => StreamError.callbackAsync(fold(acc, mapped)).flatMap { next => acc = next; this })
        }
      else if (kind == 0)
        return if (trusted) Async.failTrusted(cause) else Async.fail(cause)
      else if (longValue == Long.MinValue) return Async.succeed(Right(acc))

      val mapped = StreamError.callbackAsync(map(longValue.toInt))
      resetStep()
      Async.foldIntStep(mapped)(this)
      if (kind == 1)
        return mapped
          .flatMap(value => StreamError.callbackAsync(fold(acc, value)).flatMap { next => acc = next; this })
      else if (kind == 0) return Async.fail(cause)

      val reduced = StreamError.callbackAsync(fold(acc, intValue))
      resetStep()
      Async.foldLongStep(reduced)(this)
      if (kind == 2) acc = longValue
      else if (kind == 1)
        return reduced.flatMap { next => acc = next; this }
      else return Async.fail(cause)
      budget -= 1
    }
    wake = onComplete
    Async.schedule(this, forceMacrotask = true)
    this
  }

  def run(): Unit = {
    val callback = wake
    wake = null
    callback.run()
  }

  def successInt(result: Int): Unit                         = { intValue = result; kind = 2 }
  def successLong(result: Long): Unit                       = { longValue = result; kind = 2 }
  override def trustedFailureLong(failure: Throwable): Unit = failed(failure, isTrusted = true)

  private def failed(failure: Throwable, isTrusted: Boolean): Unit = {
    cause = failure
    kind = 0
    trusted = isTrusted
  }

  private def resetStep(): Unit = {
    cause = null
    kind = 1
    trusted = false
  }
}
