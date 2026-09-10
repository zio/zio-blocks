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

private[streams] abstract class SyncMappedIntLongUse[E](
  acquire0: () => Async[Reader[Int]],
  map0: AnyRef,
  zero: Long,
  fold: (Long, Int) => Async[Long]
) extends Async.BracketAsyncPollable[Reader[Int], Either[E, Long]](
      releaseAfterUse = true,
      acquisitionContext = acquire0,
      useContext = map0
    ) {
  protected def acquireResource(): Async[Reader[Int]] = {
    val acquire = initialAcquisitionContext.asInstanceOf[() => Async[Reader[Int]]]
    evaluateAcquisition(acquire)
  }

  override protected def completeResult(result: Async[Either[E, Long]]): Async[Either[E, Long]] =
    if (Async.stepKind(result) == 1 && Async.stepTrusted(result))
      Async.stepCause(result) match {
        case error: StreamError if !error.cleanupFailed => Async.succeed(Left(error.value.asInstanceOf[E]))
        case _                                          => result
      }
    else result

  protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]]

  protected def evaluateMap(map: AnyRef, value: Int): Int

  protected def releaseResource(reader: Reader[Int]): Async[Unit] =
    releaseResource(reader, Async.succeed(Stream.rightLong[E](0L)))

  override protected def releaseResource(
    reader: Reader[Int],
    useResult: Async[Either[E, Long]]
  ): Async[Unit] = {
    val close =
      try
        reader match {
          case sync: Reader.SyncReader[Int @unchecked]   => Async.succeed(sync.close())
          case async: Reader.AsyncReader[Int @unchecked] => async.close()
        }
      catch { case cause: Throwable => Async.fail(cause) }
    close.catchAll { closeFailure =>
      val useFailure =
        if (Async.stepKind(useResult) == 1) Async.stepCause(useResult)
        else null
      if (useFailure eq null) Async.fail(StreamError.attachCleanup(null, closeFailure))
      else {
        StreamError.attachCleanupReplay(useFailure, closeFailure)
        Async.succeed(())
      }
    }
  }

  protected def useResource(reader: Reader[Int]): Async[Either[E, Long]] = reader match {
    case sync: Reader.SyncReader[Int @unchecked]   => useSyncResource(sync)
    case async: Reader.AsyncReader[Int @unchecked] => useAsyncResource(async)
  }

  private def callbackFailedStep[A](effect: Async[A]): Async[Either[E, Long]] =
    Async.fail(StreamError.callbackFailure(Async.stepCause(effect)))

  private def failedStep[A](effect: Async[A]): Async[Either[E, Long]] = {
    val cause = Async.stepCause(effect)
    if (Async.stepTrusted(effect)) Async.failTrusted(cause)
    else Async.fail(cause)
  }

  private def finish(effect: Async[Long]): Async[Either[E, Long]] =
    effect.map(value => Stream.rightLong[E](value))

  private def useAsyncResource(reader: Reader.AsyncReader[Int]): Async[Either[E, Long]] = {
    val map    = initialUseContext.asInstanceOf[AnyRef]
    var acc    = zero
    var budget = 1024
    while (budget > 0) {
      val read =
        try reader.readIntPhysical(Long.MinValue)
        catch {
          case error: StreamError if error.isTrusted => return Async.failTrusted(error)
          case failure: Throwable                    => return Async.fail(failure)
        }
      Async.stepKind(read) match {
        case 1 => return failedStep(read)
        case 2 =>
          return finish(
            read.flatMap { value =>
              if (value == Long.MinValue) Async.succeed(acc)
              else
                StreamError
                  .callbackAsync(fold(acc, evaluateMap(map, value.toInt)))
                  .flatMap(next =>
                    Sink.foldMappedNativeAsyncIntLong(
                      reader,
                      value => StreamError.callbackAsync(Async.succeed(evaluateMap(map, value))),
                      next,
                      fold
                    )
                  )
            }
          )
        case _ =>
      }
      val value = Async.stepLong(read)
      if (value == Long.MinValue) return Async.succeed(Stream.rightLong[E](acc))

      val mapped =
        try evaluateMap(map, value.toInt)
        catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
      val reduced =
        try fold(acc, mapped)
        catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
      Async.stepKind(reduced) match {
        case 1 => return callbackFailedStep(reduced)
        case 2 =>
          return finish(
            reduced.flatMap(next =>
              Sink.foldMappedNativeAsyncIntLong(
                reader,
                value => StreamError.callbackAsync(Async.succeed(evaluateMap(map, value))),
                next,
                fold
              )
            )
          )
        case _ => acc = Async.stepLong(reduced)
      }
      budget -= 1
    }
    finish(
      Async.rescheduleKnown(
        Sink.foldMappedNativeAsyncIntLong(
          reader,
          value => StreamError.callbackAsync(Async.succeed(evaluateMap(map, value))),
          acc,
          fold
        ),
        () => ()
      )
    )
  }

  private def useSyncResource(reader: Reader.SyncReader[Int]): Async[Either[E, Long]] = {
    val map    = initialUseContext.asInstanceOf[AnyRef]
    var acc    = zero
    var budget = 1024
    while (budget > 0) {
      val value =
        try reader.readIntPhysical(Long.MinValue)
        catch {
          case error: StreamError if error.isTrusted => return Async.failTrusted(error)
          case failure: Throwable                    => return Async.fail(failure)
        }
      if (value == Long.MinValue) return Async.succeed(Stream.rightLong[E](acc))

      val mapped =
        try evaluateMap(map, value.toInt)
        catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
      val reduced =
        try fold(acc, mapped)
        catch { case failure: Throwable => return Async.fail(StreamError.callbackFailure(failure)) }
      Async.stepKind(reduced) match {
        case 1 => return callbackFailedStep(reduced)
        case 2 =>
          return finish(
            reduced.flatMap(next =>
              Sink.foldMappedSyncAsyncIntLong(
                reader,
                value => StreamError.callbackAsync(Async.succeed(evaluateMap(map, value))),
                next,
                fold
              )
            )
          )
        case _ => acc = Async.stepLong(reduced)
      }
      budget -= 1
    }
    finish(
      Async.rescheduleKnown(
        Sink.foldMappedSyncAsyncIntLong(
          reader,
          value => StreamError.callbackAsync(Async.succeed(evaluateMap(map, value))),
          acc,
          fold
        ),
        () => ()
      )
    )
  }
}

private[streams] final class SyncMappedIntLongUseProtected[E](
  acquire: () => Async[Reader[Int]],
  map: Int => Int,
  zero: Long,
  fold: (Long, Int) => Async[Long]
) extends SyncMappedIntLongUse[E](acquire, map, zero, fold) {
  override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
  protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  protected def evaluateMap(map: AnyRef, value: Int): Int                                  = map.asInstanceOf[Int => Int](value)
}

private[streams] final class SyncMappedIntLongUseUnprotected[E](
  acquire: () => Async[Reader[Int]],
  map: Int => Int,
  zero: Long,
  fold: (Long, Int) => Async[Long]
) extends SyncMappedIntLongUse[E](acquire, map, zero, fold) {
  protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  protected def evaluateMap(map: AnyRef, value: Int): Int                                  = map.asInstanceOf[Int => Int](value)
}

private[streams] final class SyncMappedIntChainLongUseProtected[E](
  acquire: () => Async[Reader[Int]],
  maps: Array[Int => Int],
  zero: Long,
  fold: (Long, Int) => Async[Long]
) extends SyncMappedIntLongUse[E](acquire, maps, zero, fold) {
  override protected def acquisitionFailure(cause: Throwable): Throwable                   = StreamError.callbackFailure(cause)
  protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  protected def evaluateMap(map: AnyRef, value: Int): Int                                  = evaluateMaps(map, value)

  private def evaluateMaps(map: AnyRef, value: Int): Int = {
    val maps    = map.asInstanceOf[Array[Int => Int]]
    var current = value
    var index   = 0
    while (index < maps.length) {
      current = maps(index)(current)
      index += 1
    }
    current
  }
}

private[streams] final class SyncMappedIntChainLongUseUnprotected[E](
  acquire: () => Async[Reader[Int]],
  maps: Array[Int => Int],
  zero: Long,
  fold: (Long, Int) => Async[Long]
) extends SyncMappedIntLongUse[E](acquire, maps, zero, fold) {
  protected def evaluateAcquisition(acquire: () => Async[Reader[Int]]): Async[Reader[Int]] = acquire()
  protected def evaluateMap(map: AnyRef, value: Int): Int                                  = evaluateMaps(map, value)

  private def evaluateMaps(map: AnyRef, value: Int): Int = {
    val maps    = map.asInstanceOf[Array[Int => Int]]
    var current = value
    var index   = 0
    while (index < maps.length) {
      current = maps(index)(current)
      index += 1
    }
    current
  }
}
