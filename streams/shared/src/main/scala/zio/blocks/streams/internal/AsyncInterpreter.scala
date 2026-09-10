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

import java.io.IOException

import zio.blocks.async._
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.{JvmType, Sink, Stream}
import zio.blocks.streams.io.Reader

/**
 * Private scalar coroutine used to prove the async interpreter's operation,
 * wake, replacement, and cancellation protocol before the op-array execution
 * loop is installed. It is intentionally not reachable from `Stream.compile`.
 */
private[streams] final class AsyncInterpreter(source: Reader.AsyncReader[Any]) {
  import AsyncInterpreter._

  private var incomingPrim: Array[Long]        = new Array[Long](8)
  private var incomingRef: Array[AnyRef]       = new Array[AnyRef](8)
  private var incomingType: Array[JvmType]     = new Array[JvmType](8)
  private var savedState: Array[StreamState]   = new Array[StreamState](8)
  private var savedOutputType: Array[JvmType]  = new Array[JvmType](8)
  private var outgoingPrim: Array[Long]        = new Array[Long](4)
  private var outgoingRef: Array[AnyRef]       = new Array[AnyRef](4)
  private var outgoingType: Array[JvmType]     = new Array[JvmType](4)
  private var state: StreamState               = StreamState.empty
  private var logicalOutputType: JvmType       = JvmType.AnyRef
  private var outputLane: Lane                 = SyncInterpreter.LANE_R
  private var afterPush                        = false
  private var sealedState: StreamState         = StreamState.empty
  private var sealedLogicalOutputType: JvmType = JvmType.AnyRef
  private var outputRemaining: Long            = Long.MaxValue
  private var sealedOutputRemaining: Long      = Long.MaxValue

  private val deferred        = new scala.collection.mutable.ArrayBuffer[() => Unit]
  private val acquired        = new scala.collection.mutable.ArrayBuffer[Reader[_]]
  private val asyncErrorMaps  = new scala.collection.mutable.ArrayBuffer[Any => Async[Any]]
  private val asyncRecoveries = new scala.collection.mutable.ArrayBuffer[AsyncRecovery]
  private val asyncFinalizers = new scala.collection.mutable.ArrayBuffer[() => Async[Unit]]

  private var active: ReadOperation[_]                      = null
  private var epoch                                         = 0L
  private var nextGeneration                                = 0L
  private var transitionFailure: Throwable                  = null
  private var transitionFailed                              = false
  private var transitionTrusted                             = false
  private var exhausted                                     = false
  private var closed                                        = false
  private var closing                                       = false
  private var activeControl: ControlOperation[_]            = null
  private var ownedClose: Async[Unit]                       = null
  private var rootCloseOwner: RootClose                     = null
  private var beforeAsyncErrorMapCommitHook: () => Unit     = null
  private var beforeAsyncReadyCommitHook: () => Unit        = null
  private var beforeInnerCloseHook: () => Unit              = null
  private var beforePushMaterializationHook: () => Unit     = null
  private var beforeRecoveryCloseCommitHook: () => Unit     = null
  private var beforeRecoveryMaterializationHook: () => Unit = null
  private var pushMaterializationFailureHook: () => Unit    = null

  if (source ne null) { appendRead(source); seal() }

  def jvmType: JvmType = logicalOutputType

  private[streams] def activeReadCancellingForTest: Boolean = synchronized {
    (active ne null) && active.state == Cancelling
  }
  private[streams] def toReader[A]: Reader.AsyncReader[A] =
    new InterpreterReader(this).asInstanceOf[Reader.AsyncReader[A]]

  private[streams] def incomingCount: Int                                                            = StreamState.incomingLen(sealedState)
  private[streams] def outgoingCount: Int                                                            = StreamState.outgoingLen(sealedState)
  private[streams] def incomingTag(index: Int): Int                                                  = (incomingPrim(index) & 0xffL).toInt
  private[streams] def outgoingTag(index: Int): Int                                                  = (outgoingPrim(index) & 0xffL).toInt
  private[streams] def incomingOp(index: Int): AnyRef                                                = incomingRef(index)
  private[streams] def closeForTest(): Async[Unit]                                                   = closeOwned()
  private[streams] def rootCloseForTest(): Async[Unit]                                               = closeRoot()
  private[streams] def finalizerReaderForTest(finalizer: () => Async[Unit]): Reader.AsyncReader[Any] =
    new FinalizerReader(finalizer)
  private[streams] def closeReadersForTest(readers: Reader.AsyncReader[Any]*): Async[Unit] =
    closeReaders(scala.collection.mutable.ArrayBuffer(readers: _*))
  private[streams] def combineCleanupForTest(first: Async[Unit], second: Async[Unit]): Async[Unit] =
    combineCleanup(first, second)
  private[streams] def beforePushMaterializationFailureForTest(hook: () => Unit): Unit = synchronized {
    pushMaterializationFailureHook = hook
  }
  private[streams] def beforeRecoveryMaterializationForTest(hook: () => Unit): Unit = synchronized {
    beforeRecoveryMaterializationHook = hook
  }
  private[streams] def beforeAsyncReadyCommitForTest(hook: () => Unit): Unit = synchronized {
    beforeAsyncReadyCommitHook = hook
  }
  private[streams] def beforeAsyncErrorMapCommitForTest(hook: () => Unit): Unit = synchronized {
    beforeAsyncErrorMapCommitHook = hook
  }
  private[streams] def beforeInnerCloseForTest(hook: () => Unit): Unit = synchronized {
    beforeInnerCloseHook = hook
  }
  private[streams] def beforePushMaterializationForTest(hook: () => Unit): Unit = synchronized {
    beforePushMaterializationHook = hook
  }
  private[streams] def beforeRecoveryCloseCommitForTest(hook: () => Unit): Unit = synchronized {
    beforeRecoveryCloseCommitHook = hook
  }
  private[streams] def markActiveReadScheduledForTest(): Unit = synchronized {
    active.scheduled = true
  }

  private[streams] def installNestedFrameForTest(reader: Reader.AsyncReader[Any], innerType: JvmType): Unit =
    synchronized {
      if ((reader eq null) || (innerType eq null)) throw new NullPointerException
      if ((active ne null) || transitionFailed || exhausted || StreamState.incomingLen(state) == 0)
        throw new IllegalStateException("Cannot install a nested reader in the current state")
      if (logicalOutputType != innerType)
        throw new IllegalArgumentException(s"Nested reader type $innerType does not match $logicalOutputType")
      val idx        = ensureIncoming()
      val outerState = StreamState.withOutputLane(state, outputLane)
      incomingPrim(idx) = OpTag.readTag(innerType).toLong
      incomingRef(idx) = reader.asInstanceOf[AnyRef]
      incomingType(idx) = innerType
      savedState(idx) = outerState
      savedOutputType(idx) = logicalOutputType
      state = StreamState.withStageStart(StreamState.withIncomingLen(state, idx + 1), idx)
      outputLane = SyncInterpreter.laneOf(innerType)
      logicalOutputType = innerType
    }

  private[streams] def deferReader(
    make: () => Reader.SyncReader[_],
    knownType: JvmType
  ): Unit = deferred += (() => {
    val owner = make()
    acquired += owner
    appendRead(new InterpreterSyncReader(owner.asInstanceOf[Reader.SyncReader[Any]]), knownType)
  })

  private[streams] def deferReaderRoot(make: () => Reader[_]): Unit = deferred += (() =>
    make() match {
      case owner: Reader.SyncReader[_] =>
        acquired += owner
        appendRead(new InterpreterSyncReader(owner.asInstanceOf[Reader.SyncReader[Any]]), owner.jvmType)
      case owner: Reader.AsyncReader[_] =>
        acquired += owner
        appendRead(owner.asInstanceOf[Reader.AsyncReader[Any]], owner.jvmType)
    }
  )

  private[streams] def deferOwnedReaderRoot(owner: Reader[_]): Unit = {
    acquired += owner
    deferred += (() =>
      owner match {
        case sync: Reader.SyncReader[_] =>
          appendRead(new InterpreterSyncReader(sync.asInstanceOf[Reader.SyncReader[Any]]), sync.jvmType)
        case async: Reader.AsyncReader[_] => appendRead(async.asInstanceOf[Reader.AsyncReader[Any]], async.jvmType)
      }
    )
  }

  /**
   * Enqueues construction of an already-stable async boundary without driving
   * it.
   */
  private[streams] def deferAsyncReader(make: () => Reader.AsyncReader[_]): Unit = deferred += (() => {
    val owner = make()
    acquired += owner
    appendRead(owner, owner.jvmType)
  })

  private[streams] def deferMap[A, B](inType: JvmType, outType: JvmType)(f: A => B): Unit =
    deferred += (() => addMap(inType, outType)(f))

  private[streams] def deferMap[A, B](outType: JvmType)(f: A => B): Unit =
    deferred += (() => addMap(logicalOutputType, outType)(f))

  private[streams] def deferAsyncMap[A, B](outType: JvmType)(f: A => Async[B]): Unit =
    deferred += (() => addAsyncMap(logicalOutputType, outType)(f))

  private[streams] def deferAsyncFilter[A](f: A => Async[Boolean]): Unit =
    deferred += (() => addAsyncFilter(logicalOutputType)(f))

  private[streams] def deferAsyncCollect[A, B](outType: JvmType)(f: A => Async[Option[B]]): Unit =
    deferred += (() => addAsyncCollect(logicalOutputType, outType)(f))

  private[streams] def deferAsyncTap[A](f: A => Async[Unit]): Unit =
    deferred += (() => addAsyncTap(logicalOutputType)(f))

  private[streams] def deferAsyncDistinctKey[A, K](f: A => Async[K]): Unit =
    deferred += (() => addAsyncDistinctKey(logicalOutputType)(f))

  private[streams] def deferAsyncMapAccum[S, A, B](init: S, outType: JvmType)(f: (S, A) => Async[(S, B)]): Unit =
    deferred += (() => addAsyncMapAccum(init, logicalOutputType, outType)(f))

  private[streams] def deferAsyncScan[S, A](init: S, inType: JvmType, stateType: JvmType)(
    f: (S, A) => Async[S]
  ): Unit = deferred += (() => addAsyncScan(init, inType, stateType)(f))

  private[streams] def deferAsyncScan[S, A](init: S, stateType: JvmType)(f: (S, A) => Async[S]): Unit =
    deferred += (() => addAsyncScan(init, logicalOutputType, stateType)(f))

  private[streams] def deferAsyncTakeWhile[A](f: A => Async[Boolean]): Unit =
    deferred += (() => addAsyncTakeWhile(logicalOutputType)(f))

  private[streams] def addAsyncErrorMap[E, E2](f: E => Async[E2]): Unit = synchronized {
    asyncErrorMaps += ((value: Any) => f(value.asInstanceOf[E]).asInstanceOf[Async[Any]])
  }

  private[streams] def normalizeRecoveryOutput(
    expectedType: JvmType,
    allowReferenceUnboxing: Boolean = true
  ): Unit = synchronized {
    val actualType = logicalOutputType
    if (actualType != expectedType) {
      if (
        (actualType != JvmType.AnyRef && expectedType != JvmType.AnyRef &&
          !isNumericWidening(actualType, expectedType)) ||
        (actualType == JvmType.AnyRef && expectedType != JvmType.AnyRef && !allowReferenceUnboxing)
      )
        throw new IllegalArgumentException(
          s"Async recovery stream has incompatible element type $actualType; expected $expectedType"
        )
      addMap[Any, Any](actualType, expectedType)(value => convertRecoveryValue(value, actualType, expectedType))
    }
  }

  private def isNumericWidening(source: JvmType, target: JvmType): Boolean = source match {
    case JvmType.Byte =>
      target == JvmType.Short || target == JvmType.Int || target == JvmType.Long ||
      target == JvmType.Float || target == JvmType.Double
    case JvmType.Short =>
      target == JvmType.Int || target == JvmType.Long || target == JvmType.Float || target == JvmType.Double
    case JvmType.Char =>
      target == JvmType.Int || target == JvmType.Long || target == JvmType.Float || target == JvmType.Double
    case JvmType.Int =>
      target == JvmType.Long || target == JvmType.Float || target == JvmType.Double
    case JvmType.Long  => target == JvmType.Float || target == JvmType.Double
    case JvmType.Float => target == JvmType.Double
    case _             => false
  }

  private def convertRecoveryValue(value: Any, source: JvmType, target: JvmType): Any = {
    def intValue: Int = source match {
      case JvmType.Char   => value.asInstanceOf[Char].toInt
      case JvmType.AnyRef =>
        value match {
          case char: java.lang.Character  => char.charValue().toInt
          case number: Number             => number.intValue()
          case boolean: java.lang.Boolean => if (boolean.booleanValue()) 1 else 0
        }
      case _ => value.asInstanceOf[Number].intValue()
    }
    def longValue: Long = source match {
      case JvmType.Char                                              => intValue.toLong
      case JvmType.AnyRef if value.isInstanceOf[java.lang.Character] => intValue.toLong
      case _                                                         => value.asInstanceOf[Number].longValue()
    }
    def floatValue: Float = source match {
      case JvmType.Char                                              => intValue.toFloat
      case JvmType.AnyRef if value.isInstanceOf[java.lang.Character] => intValue.toFloat
      case _                                                         => value.asInstanceOf[Number].floatValue()
    }
    def doubleValue: Double = source match {
      case JvmType.Char                                              => intValue.toDouble
      case JvmType.AnyRef if value.isInstanceOf[java.lang.Character] => intValue.toDouble
      case _                                                         => value.asInstanceOf[Number].doubleValue()
    }
    target match {
      case JvmType.AnyRef =>
        source match {
          case JvmType.Byte    => Byte.box(intValue.toByte)
          case JvmType.Short   => Short.box(intValue.toShort)
          case JvmType.Char    => Char.box(intValue.toChar)
          case JvmType.Boolean => Boolean.box(value.asInstanceOf[Boolean])
          case _               => value.asInstanceOf[AnyRef]
        }
      case JvmType.Byte    => intValue.toByte.toInt
      case JvmType.Short   => intValue.toShort.toInt
      case JvmType.Char    => intValue.toChar.toInt
      case JvmType.Boolean => value.asInstanceOf[Boolean]
      case JvmType.Int     => intValue
      case JvmType.Long    => longValue
      case JvmType.Float   => floatValue
      case JvmType.Double  => doubleValue
    }
  }

  /**
   * White-box recovery boundary. Kept internal until the public async Stream
   * API is exposed.
   */
  private[streams] def addAsyncCatchAll[E](
    f: E => Async[Stream[_, Any]],
    allowReferenceRecovery: Boolean = false
  ): Unit = synchronized {
    if (f eq null) throw new NullPointerException("async recovery callback is null")
    asyncRecoveries += new AsyncRecovery(
      asyncRecoveries.length,
      logicalOutputType,
      allowReferenceRecovery,
      typed = true,
      null,
      value => f(value.asInstanceOf[E])
    )
  }

  /** White-box defect-recovery boundary. */
  private[streams] def addAsyncCatchDefect(
    pf: PartialFunction[Throwable, Async[Option[Stream[_, Any]]]],
    allowReferenceRecovery: Boolean = false
  ): Unit = synchronized {
    if (pf eq null) throw new NullPointerException("async recovery callback is null")
    asyncRecoveries +=
      new AsyncRecovery(asyncRecoveries.length, logicalOutputType, allowReferenceRecovery, typed = false, pf, null)
  }

  /**
   * Registers an exactly-once terminal finalizer without adding a public Stream
   * node.
   */
  private[streams] def addAsyncFinalizer(finalizer: () => Async[Unit]): Unit = synchronized {
    if (finalizer eq null) throw new NullPointerException("async finalizer is null")
    if (closed || closing || (ownedClose.asInstanceOf[AnyRef] ne null) || (active ne null) || (activeControl ne null))
      throw new IllegalStateException("Cannot register an async finalizer after close has begun")
    asyncFinalizers += finalizer
  }

  private[streams] def deferFilter[A](inType: JvmType)(f: A => Boolean): Unit =
    deferred += (() => addFilter(inType)(f))

  private[streams] def deferFilter[A](f: A => Boolean): Unit =
    deferred += (() => addFilter(logicalOutputType)(f))

  private[streams] def deferDrop(n: Long): Unit =
    deferred += (() => {
      var remaining = math.max(0L, n)
      addFilter[Any](logicalOutputType) { _ =>
        if (remaining <= 0L) true
        else { remaining -= 1L; false }
      }
    })

  private[streams] def deferTake(n: Long): Unit =
    deferred += (() => {
      outputRemaining = math.min(outputRemaining, math.max(0L, n))
    })

  private[streams] def deferTakeWhile[A](f: A => Boolean): Unit =
    deferred += (() => addAsyncTakeWhile[A](logicalOutputType)(value => Async.succeed(f(value))))

  private[streams] def deferPush[A](outType: JvmType)(f: A => Any): Unit =
    deferred += (() => addPush(logicalOutputType, outType)(f))

  /** Construct a lazy, generation-bound generic scalar pull. */
  def read[A](sentinel: A): Async[A]              = operation(ReadRef, sentinel)
  def readBoolean(sentinel: Int): Async[Int]      = operation(ReadBoolean, sentinel)
  def readByte(): Async[Int]                      = operation(ReadByte, -1)
  def readChar(sentinel: Int): Async[Int]         = operation(ReadChar, sentinel)
  def readShort(sentinel: Int): Async[Int]        = operation(ReadShort, sentinel)
  def readInt(sentinel: Long): Async[Long]        = operation(ReadInt, sentinel)
  def readLong(sentinel: Long): Async[Long]       = operation(ReadLong, sentinel)
  def readFloat(sentinel: Double): Async[Double]  = operation(ReadFloat, sentinel)
  def readDouble(sentinel: Double): Async[Double] = operation(ReadDouble, sentinel)

  def readN[A](n: Int): Async[Chunk[A]]                                  = readChunk[A](n, upTo = false)
  def readUpToN[A](n: Int): Async[Chunk[A]]                              = readChunk[A](n, upTo = true)
  def readBytes(dest: Array[Byte], offset: Int, length: Int): Async[Int] = {
    try Reader.validateArrayRange(dest, offset, length)
    catch { case cause: Throwable => return Async.fail(cause) }
    bulkArray(length)((owner, i, commit) =>
      bulkPull(ReadByte, -1, owner).map {
        case (_, true)      => false
        case (value, false) => dest(offset + i) = value.toByte; commit(); true
      }
    )
  }
  def readInts(dest: Array[Int], offset: Int, length: Int): Async[Int] = {
    try Reader.validateArrayRange(dest, offset, length)
    catch { case cause: Throwable => return Async.fail(cause) }
    bulkArray(length)((owner, i, commit) =>
      bulkPull(ReadInt, Long.MinValue, owner).map {
        case (_, true)      => false
        case (value, false) => dest(offset + i) = value.toInt; commit(); true
      }
    )
  }
  def readLongs(dest: Array[Long], offset: Int, length: Int): Async[Int] = {
    try Reader.validateArrayRange(dest, offset, length)
    catch { case cause: Throwable => return Async.fail(cause) }
    bulkArray(length)((owner, i, commit) =>
      bulkPull(ReadLong, Long.MaxValue, owner).map {
        case (_, true)      => false
        case (value, false) => dest(offset + i) = value; commit(); true
      }
    )
  }
  def readFloats(dest: Array[Float], offset: Int, length: Int): Async[Int] = {
    try Reader.validateArrayRange(dest, offset, length)
    catch { case cause: Throwable => return Async.fail(cause) }
    bulkArray(length)((owner, i, commit) =>
      bulkPull(ReadFloat, Double.MaxValue, owner).map {
        case (_, true)      => false
        case (value, false) => dest(offset + i) = value.toFloat; commit(); true
      }
    )
  }
  def readDoubles(dest: Array[Double], offset: Int, length: Int): Async[Int] = {
    try Reader.validateArrayRange(dest, offset, length)
    catch { case cause: Throwable => return Async.fail(cause) }
    bulkArray(length)((owner, i, commit) =>
      bulkPull(ReadDouble, Double.MaxValue, owner).map {
        case (_, true)      => false
        case (value, false) => dest(offset + i) = value; commit(); true
      }
    )
  }

  private def bulkControl[A](snapshot: () => A)(effect: ControlOperation[A] => Async[A]): Async[A] = synchronized {
    nextGeneration += 1L
    var owner: ControlOperation[A] = null
    owner = new ControlOperation[A](
      nextGeneration,
      () => effect(owner),
      (_: A) => (),
      BulkControl,
      () => Async.succeed(()),
      snapshot
    )
    owner
  }

  private def bulkArray(length: Int)(
    pull: (ControlOperation[_], Int, () => Unit) => Async[Boolean]
  ): Async[Int] = {
    var committed = 0
    bulkControl[Int](() => if (committed == 0) -1 else committed) { owner =>
      def loop(i: Int): Async[Int] =
        pull(owner, i, () => committed = i + 1).flatMap(ok =>
          if (!ok) Async.succeed(if (i == 0) -1 else i)
          else if (i + 1 >= length) Async.succeed(i + 1)
          else continueBulk(i + 1)(if (tryReadableOwned()) loop(i + 1) else Async.succeed(i + 1))
        )
      if (length == 0) Async.succeed(0) else loop(0)
    }
  }

  private def tryReadableOwned(): Boolean = synchronized {
    !closed && !transitionFailed && !exhausted &&
    (incomingRef(StreamState.stageStart(state)).asInstanceOf[Reader.AsyncReader[Any]].tryReadable eq Reader.Available)
  }

  private def readChunk[A](n: Int, upTo: Boolean): Async[Chunk[A]] = {
    var snapshot: () => Chunk[A] = () => Chunk.empty
    bulkControl[Chunk[A]](() => snapshot()) { owner =>
      val limit = math.max(0, n)
      val hint  = math.min(limit, if (upTo) 64 else 4096)
      logicalOutputType match {
        case JvmType.Boolean =>
          val b = new ChunkBuilder.Boolean; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadBoolean, -1, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v != 0; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case JvmType.Byte =>
          val b = new ChunkBuilder.Byte; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadByte, -1, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v.toByte; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case JvmType.Char =>
          val b = new ChunkBuilder.Char; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadChar, Int.MinValue, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v.toChar; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case JvmType.Short =>
          val b = new ChunkBuilder.Short; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadShort, Int.MinValue, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v.toShort; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case JvmType.Int =>
          val b = new ChunkBuilder.Int; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadInt, Long.MinValue, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v.toInt; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case JvmType.Long =>
          val b = new ChunkBuilder.Long; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadLong, Long.MaxValue, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case JvmType.Float =>
          val b = new ChunkBuilder.Float; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadFloat, Double.MaxValue, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v.toFloat; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case JvmType.Double =>
          val b = new ChunkBuilder.Double; b.sizeHint(hint)
          snapshot = () => b.result().asInstanceOf[Chunk[A]]
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result().asInstanceOf[Chunk[A]])
          else
            bulkPull(ReadDouble, Double.MaxValue, owner).flatMap {
              case (_, true)  => Async.succeed(b.result().asInstanceOf[Chunk[A]])
              case (v, false) =>
                b += v; afterChunkPull(i + 1, limit, upTo, b.result().asInstanceOf[Chunk[A]])(loop(i + 1))
            }
          loop(0)
        case _ =>
          val b = ChunkBuilder.make[A](math.min(hint, 16))
          snapshot = () => b.result()
          def loop(i: Int): Async[Chunk[A]] = if (i >= limit) Async.succeed(b.result())
          else
            bulkPull(ReadRef, EndOfStream, owner).flatMap {
              case (_, true)  => Async.succeed(b.result())
              case (v, false) => b += v.asInstanceOf[A]; afterChunkPull(i + 1, limit, upTo, b.result())(loop(i + 1))
            }
          loop(0)
      }
    }
  }

  private def afterChunkPull[A](i: Int, limit: Int, upTo: Boolean, current: => Chunk[A])(
    next: => Async[Chunk[A]]
  ): Async[Chunk[A]] = {
    val continuation =
      if (i >= limit || !upTo) next
      else if (tryReadableOwned()) next
      else Async.succeed(current)
    continueBulk(i)(continuation)
  }

  private def continueBulk[A](count: Int)(next: => Async[A]): Async[A] =
    if (count == 0 || count % ReadyBudget != 0) next
    else new BulkYield((count / ReadyBudget) % MicrotaskYieldsBeforeMacrotask == 0).flatMap(_ => next)

  private final class BulkYield(forceMacrotask: Boolean) extends Async.Operation[Unit] with Runnable {
    private val completion = new Completer[Unit]
    private var scheduled  = false
    private var cancelled  = false

    def poll(onComplete: Runnable): Async[Unit] = {
      val schedule = synchronized {
        if (scheduled || cancelled) false else { scheduled = true; true }
      }
      if (schedule) Async.schedule(this, forceMacrotask)
      completion.poll(onComplete)
    }

    def run(): Unit = {
      val publish = synchronized(!cancelled)
      if (publish) completion.succeed(())
    }

    protected def cancelOperation(): Async[Unit] = synchronized {
      cancelled = true
      Async.succeed(())
    }
  }

  /**
   * Reader lifecycle operations intentionally kept on the white-box
   * interpreter.
   */
  def isClosed: Async[Boolean]          = control(rootReader.isClosed, QueryClosed)
  def readable(): Async[Boolean]        = control(rootReader.readable(), QueryReadable)
  def setLimit(n: Long): Async[Boolean] = control(rootReader.setLimit(n), MutatingMapsOnly)
  def setRepeat(): Async[Boolean]       = control(
    rootReader.setRepeat(),
    MutatingMapsOnly,
    (repeated: Boolean) => if (repeated) exhausted = false
  )
  def setSkip(n: Long): Async[Boolean] = control(rootReader.setSkip(n), MutatingMapsOnly)
  def skip(n: Long): Async[Unit]       = synchronized {
    nextGeneration += 1L
    var operation: ControlOperation[Unit] = null
    operation = new ControlOperation[Unit](
      nextGeneration,
      () => skipEffect(operation, n),
      (_: Unit) => (),
      SkipControl,
      () => Async.succeed(())
    )
    operation
  }

  private def skipEffect(owner: ControlOperation[_], n: Long): Async[Unit] = {
    def afterPull(left: Long, count: Int): Async[Unit] =
      if (exhausted) Async.succeed(())
      else continueBulk(count)(loop(left - 1L, count))
    def loop(left: Long, count: Int): Async[Unit] =
      if (left <= 0L) Async.succeed(())
      else
        logicalOutputType match {
          case JvmType.Boolean =>
            operation(ReadBoolean, -1, owner).flatMap(_ => afterPull(left, count + 1))
          case JvmType.Byte =>
            operation(ReadByte, -1, owner).flatMap(_ => afterPull(left, count + 1))
          case JvmType.Char =>
            operation(ReadChar, -1, owner).flatMap(_ => afterPull(left, count + 1))
          case JvmType.Short =>
            operation(ReadShort, -1, owner).flatMap(_ => afterPull(left, count + 1))
          case JvmType.Int =>
            operation(ReadInt, Long.MinValue, owner).flatMap(_ => afterPull(left, count + 1))
          case JvmType.Long =>
            operation(ReadLong, Long.MaxValue, owner).flatMap(_ => afterPull(left, count + 1))
          case JvmType.Float =>
            operation(ReadFloat, Double.MaxValue, owner).flatMap(_ => afterPull(left, count + 1))
          case JvmType.Double =>
            operation(ReadDouble, Double.MaxValue, owner).flatMap(_ => afterPull(left, count + 1))
          case _ =>
            operation(ReadRef, EndOfStream, owner).flatMap(_ => afterPull(left, count + 1))
        }
    if (n <= 0L) Async.succeed(()) else loop(n, 0)
  }
  def reset(): Async[Unit] = synchronized {
    nextGeneration += 1L
    var operation: ControlOperation[Unit] = null
    operation = new ControlOperation[Unit](
      nextGeneration,
      () => resetEffect(operation),
      (_: Unit) => (),
      ResetControl,
      () => cancelReset()
    )
    operation
  }

  private def rootReader: Reader.AsyncReader[Any] =
    incomingRef(0).asInstanceOf[Reader.AsyncReader[Any]]

  private def mapsOnly: Boolean = synchronized {
    var i  = 1
    var ok = true
    while (i < StreamState.incomingLen(sealedState) && ok) {
      ok = OpTag.isMap((incomingPrim(i) & 0xff).toInt)
      i += 1
    }
    ok
  }

  private def control[A](
    effect: => Async[A],
    mode: Int,
    onCommit: A => Unit = (_: A) => ()
  ): Async[A] = synchronized {
    nextGeneration += 1L
    new ControlOperation[A](nextGeneration, () => effect, onCommit, mode, () => Async.succeed(()))
  }

  private def cancelReset(): Async[Unit] = {
    synchronized {
      closed = true
      state = sealedState
      outputLane = StreamState.outputLane(sealedState)
      logicalOutputType = sealedLogicalOutputType
    }
    closeRoot()
  }

  private def resetEffect(owner: ControlOperation[_]): Async[Unit] = {
    val (root, nested, priorFailure, priorFailureSet) = synchronized {
      val readers = new scala.collection.mutable.ArrayBuffer[Reader.AsyncReader[Any]]
      var i       = 1
      while (i < StreamState.incomingLen(state)) {
        if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
          val reader = incomingRef(i).asInstanceOf[Reader.AsyncReader[Any]]
          incomingRef(i) = null
          if (reader ne null) readers += reader
        }
        i += 1
      }
      (rootReader, readers, transitionFailure, transitionFailed)
    }
    closeReaders(nested).either.flatMap { nestedResult =>
      val rootReset =
        if (root eq null) Async.fail(new IOException("Reader cannot be reset after its root owner was detached"))
        else root.reset()
      rootReset.either.flatMap { resetResult =>
        val (failure, failureSet) = (nestedResult, resetResult) match {
          case (Right(_), Right(_))           => (null, false)
          case (Left(closeFailure), Right(_)) =>
            (
              if (!priorFailureSet) closeFailure
              else if (priorFailure eq null) null
              else StreamError.attachCleanupReplay(priorFailure, closeFailure),
              true
            )
          case (Right(_), Left(resetFailure)) =>
            (
              if (!priorFailureSet) resetFailure
              else if (priorFailure eq null) null
              else StreamError.attachSuppressedReplay(priorFailure, resetFailure),
              true
            )
          case (Left(closeFailure), Left(resetFailure)) =>
            val first =
              if (!priorFailureSet) closeFailure
              else if (priorFailure eq null) null
              else StreamError.attachCleanupReplay(priorFailure, closeFailure)
            (if (first eq null) null else StreamError.attachSuppressedReplay(first, resetFailure), true)
        }
        if (!failureSet)
          synchronized {
            if ((activeControl ne owner) || (closed && closing))
              Async.fail(new IOException("Reader was closed during reset"))
            else {
              state = sealedState
              outputLane = StreamState.outputLane(sealedState)
              logicalOutputType = sealedLogicalOutputType
              outputRemaining = sealedOutputRemaining
              exhausted = false
              transitionFailure = null
              transitionFailed = false
              transitionTrusted = false
              closed = false
              ownedClose = null
              rootCloseOwner = null
              resetAsyncOperatorState()
              var recoveryIndex = 0
              while (recoveryIndex < asyncRecoveries.length) {
                asyncRecoveries(recoveryIndex).switched = false
                recoveryIndex += 1
              }
              Async.succeed(())
            }
          }
        else
          closeRoot().either.flatMap { closeResult =>
            if (failure ne null) closeResult.left.foreach(cause => StreamError.attachCleanupReplay(failure, cause))
            synchronized {
              if (activeControl eq owner) {
                transitionFailure = failure
                transitionFailed = true
                transitionTrusted = false
                closed = true
              }
            }
            Async.fail(failure)
          }
      }
    }
  }

  private def resetAsyncOperatorState(): Unit = {
    def reset(prim: Array[Long], ref: Array[AnyRef], length: Int): Unit = {
      var i = 0
      while (i < length) {
        val tag = (prim(i) & 0xff).toInt
        if (OpTag.isAsyncMapAccum(tag)) {
          val accum = ref(i).asInstanceOf[AsyncAccum]
          accum.state = accum.initial
          accum.emitInitial = accum.scan
        } else if (OpTag.isAsyncDistinctKey(tag))
          ref(i).asInstanceOf[AsyncDistinctKey].seen = scala.collection.immutable.HashSet.empty[Any]
        i += 1
      }
    }
    reset(incomingPrim, incomingRef, StreamState.incomingLen(sealedState))
    reset(outgoingPrim, outgoingRef, StreamState.outgoingLen(sealedState))
  }

  private def bridgeInput(inType: JvmType): Unit = {
    val sourceType = logicalOutputType
    val sourceLane = SyncInterpreter.laneOf(sourceType)
    val targetLane = SyncInterpreter.laneOf(inType)
    if (sourceLane != targetLane || ((sourceType eq JvmType.Boolean) != (inType eq JvmType.Boolean))) {
      val tag = OpTag.mapTag(sourceLane, SyncInterpreter.outLaneOf(inType)).toLong
      val fn  = SyncInterpreter.logicalBridgeFn(sourceType, inType)
      if (afterPush) {
        val idx = ensureOutgoing()
        outgoingPrim(idx) = tag
        outgoingRef(idx) = fn
        outgoingType(idx) = inType
        state = StreamState.withOutgoingLen(state, idx + 1)
      } else {
        val idx = ensureIncoming()
        incomingPrim(idx) = tag
        incomingRef(idx) = fn
        incomingType(idx) = inType
        state = StreamState.withIncomingLen(state, idx + 1)
      }
      outputLane = targetLane
    }
    logicalOutputType = inType
  }

  private[streams] def addMap[A, B](inType: JvmType, outType: JvmType)(f: A => B): Unit = synchronized {
    bridgeInput(inType)
    val tag = OpTag.mapTag(SyncInterpreter.laneOf(inType), SyncInterpreter.outLaneOf(outType))
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag.toLong
      outgoingRef(idx) = SyncInterpreter.adaptMap(inType, outType, f)
      outgoingType(idx) = outType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag.toLong
      incomingRef(idx) = SyncInterpreter.adaptMap(inType, outType, f)
      incomingType(idx) = outType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = OpTag.storageLaneOfMapTag(tag)
    logicalOutputType = outType
  }

  private[streams] def addAsyncMap[A, B](inType: JvmType, outType: JvmType)(f: A => Async[B]): Unit = synchronized {
    bridgeInput(inType)
    val tag = OpTag.asyncMapTag(SyncInterpreter.laneOf(inType), SyncInterpreter.outLaneOf(outType))
    val fn  = SyncInterpreter.adaptPush(inType, f)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag.toLong
      outgoingRef(idx) = fn
      outgoingType(idx) = outType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag.toLong
      incomingRef(idx) = fn
      incomingType(idx) = outType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = OpTag.storageLaneOfAsyncMapTag(tag)
    logicalOutputType = outType
  }

  private[streams] def addAsyncMapAccum[S, A, B](init: S, inType: JvmType, outType: JvmType)(
    f: (S, A) => Async[(S, B)]
  ): Unit = synchronized {
    bridgeInput(inType)
    val inLane = SyncInterpreter.laneOf(inType)
    val tag    = OpTag.asyncMapAccumTag(inLane, SyncInterpreter.outLaneOf(outType))
    val fn     = adaptAsyncAccumInput(inType, f)
    val accum  = new AsyncAccum(fn, init, init, scan = false)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag.toLong
      outgoingRef(idx) = accum
      outgoingType(idx) = outType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag.toLong
      incomingRef(idx) = accum
      incomingType(idx) = outType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = OpTag.storageLaneOfAsyncMapAccumTag(tag)
    logicalOutputType = outType
  }

  private def markLastAccumAsScan(): Unit = synchronized {
    val accum =
      if (afterPush) outgoingRef(StreamState.outgoingLen(state) - 1)
      else incomingRef(StreamState.incomingLen(state) - 1)
    val current = accum.asInstanceOf[AsyncAccum]
    current.scan = true
    current.emitInitial = true
  }

  private[streams] def addAsyncScan[S, A](init: S, inType: JvmType, stateType: JvmType)(
    f: (S, A) => Async[S]
  ): Unit = {
    addAsyncMapAccum[S, A, S](init, inType, stateType)((state, value) => f(state, value).asInstanceOf[Async[(S, S)]])
    markLastAccumAsScan()
  }

  private[streams] def addFilter[A](inType: JvmType)(f: A => Boolean): Unit = synchronized {
    bridgeInput(inType)
    val tag = OpTag.filterTag(SyncInterpreter.laneOf(inType)).toLong
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag
      outgoingRef(idx) = SyncInterpreter.adaptFilter(inType, f)
      outgoingType(idx) = inType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag
      incomingRef(idx) = SyncInterpreter.adaptFilter(inType, f)
      incomingType(idx) = inType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
  }

  private[streams] def addAsyncFilter[A](inType: JvmType)(f: A => Async[Boolean]): Unit = synchronized {
    bridgeInput(inType)
    val lane = SyncInterpreter.laneOf(inType)
    val tag  = OpTag.asyncFilterTag(lane).toLong
    val fn   = SyncInterpreter.adaptPush(inType, f)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag
      outgoingRef(idx) = fn
      outgoingType(idx) = inType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag
      incomingRef(idx) = fn
      incomingType(idx) = inType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
  }

  private[streams] def addAsyncTakeWhile[A](inType: JvmType)(f: A => Async[Boolean]): Unit = synchronized {
    bridgeInput(inType)
    val lane = SyncInterpreter.laneOf(inType)
    val tag  = OpTag.asyncTakeWhileTag(lane).toLong
    val fn   = SyncInterpreter.adaptPush(inType, f)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag
      outgoingRef(idx) = fn
      outgoingType(idx) = inType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag
      incomingRef(idx) = fn
      incomingType(idx) = inType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
  }

  private[streams] def addAsyncCollect[A, B](inType: JvmType, outType: JvmType)(
    f: A => Async[Option[B]]
  ): Unit = synchronized {
    bridgeInput(inType)
    val inLane = SyncInterpreter.laneOf(inType)
    val tag    = OpTag.asyncCollectTag(inLane, SyncInterpreter.outLaneOf(outType))
    val fn     = SyncInterpreter.adaptPush(inType, f)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag.toLong
      outgoingRef(idx) = fn
      outgoingType(idx) = outType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag.toLong
      incomingRef(idx) = fn
      incomingType(idx) = outType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = OpTag.storageLaneOfAsyncCollectTag(tag)
    logicalOutputType = outType
  }

  private[streams] def addAsyncTap[A](inType: JvmType)(f: A => Async[Unit]): Unit = synchronized {
    bridgeInput(inType)
    val lane = SyncInterpreter.laneOf(inType)
    val tag  = OpTag.asyncTapTag(lane).toLong
    val fn   = SyncInterpreter.adaptPush(inType, f)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag
      outgoingRef(idx) = fn
      outgoingType(idx) = inType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag
      incomingRef(idx) = fn
      incomingType(idx) = inType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
  }

  private[streams] def addAsyncDistinctKey[A, K](inType: JvmType)(f: A => Async[K]): Unit = synchronized {
    bridgeInput(inType)
    val lane     = SyncInterpreter.laneOf(inType)
    val tag      = OpTag.asyncDistinctKeyTag(lane).toLong
    val fn       = SyncInterpreter.adaptPush(inType, f)
    val distinct = new AsyncDistinctKey(fn)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag
      outgoingRef(idx) = distinct
      outgoingType(idx) = inType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag
      incomingRef(idx) = distinct
      incomingType(idx) = inType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
  }

  private[streams] def addPush[A](inType: JvmType, outType: JvmType)(f: A => Any): Unit = synchronized {
    bridgeInput(inType)
    val tag = OpTag.pushTag(SyncInterpreter.laneOf(inType)).toLong
    val fn  = SyncInterpreter.adaptPush(inType, f)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag
      outgoingRef(idx) = fn
      outgoingType(idx) = outType
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag
      incomingRef(idx) = fn
      incomingType(idx) = outType
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = SyncInterpreter.outLaneOf(outType)
    logicalOutputType = outType
    afterPush = true
  }

  private def operation[A](kind: Int, sentinel: A): Async[A] = synchronized {
    nextGeneration += 1L
    new ReadOperation[A](nextGeneration, kind, sentinel, null, null)
  }

  private def operation[A](kind: Int, sentinel: A, owner: ControlOperation[_]): Async[A] = synchronized {
    nextGeneration += 1L
    new ReadOperation[A](nextGeneration, kind, sentinel, owner, null)
  }

  private def foldAsync[A, Z](zero: Z, accumulatorType: JvmType, step: (Z, A) => Async[Z]): Async[Z] = synchronized {
    if (outputRemaining == Long.MaxValue) {
      nextGeneration += 1L
      new ReadOperation[Z](
        nextGeneration,
        ReadRef,
        zero,
        null,
        TerminalFold.make(zero, accumulatorType, step)
      )
    } else {
      val reader = new InterpreterReader(this)
      Sink.foldAsyncReader[A, Z](reader, zero)((acc, value) => StreamError.callbackAsync(step(acc, value)))
    }
  }

  private def bulkPull[A](kind: Int, sentinel: A, owner: ControlOperation[_]): Async[(A, Boolean)] = {
    val operation = synchronized {
      nextGeneration += 1L
      new ReadOperation[A](nextGeneration, kind, sentinel, owner, null)
    }
    operation.map(value => (value, operation.reachedEof))
  }

  private def poll[A](op: ReadOperation[A], observer: Runnable): Async[A] = {
    var invoke                   = false
    var scanInitial: ScanInitial = null
    var leaf: Pollable[Any]      = null
    var operationEpoch           = 0L
    synchronized {
      if (op.terminalSet) return op.terminal
      op.observer = observer
      if (op.state == Fresh) {
        if ((activeControl ne null) && (op.controlOwner ne activeControl)) {
          op.finishFailure(new IllegalStateException(ConcurrentOperationMessage), trusted = false)
          return op.terminal
        }
        if (transitionFailed) {
          op.finishFailure(transitionFailure, transitionTrusted)
          return op.terminal
        }
        if (closed) {
          op.reachedEof = true
          op.finishSuccess(op.terminalResult)
          return op.terminal
        }
        if (exhausted) {
          op.reachedEof = true
          op.finishSuccess(op.terminalResult)
          return op.terminal
        }
        if (outputRemaining <= 0L) {
          exhausted = true
          op.reachedEof = true
          op.finishSuccess(op.terminalResult)
          return op.terminal
        }
        if (active ne null) {
          op.finishFailure(new IllegalStateException(ConcurrentOperationMessage), trusted = false)
          return op.terminal
        }
        epoch += 1L
        operationEpoch = epoch
        active = op
        op.operationEpoch = operationEpoch
        op.restartState = StreamState.withOutputLane(state, outputLane)
        op.restartType = logicalOutputType
        op.restartIncomingLen = StreamState.incomingLen(state)
        val readIdx = StreamState.stageStart(state)
        op.reader = activeReader
        op.readTag = (incomingPrim(readIdx) & 0xff).toInt
        op.sourceType = incomingType(readIdx)
        op.collisionSafe = op.readTag == OpTag.READ_L || op.readTag == OpTag.READ_D
        op.programStart = readIdx + 1
        op.programLength = StreamState.incomingLen(state)
        op.programPrim = incomingPrim
        op.programRef = incomingRef
        op.resultType = logicalOutputType
        op.resultLane = outputLane
        op.state = Invoking
        var out = StreamState.outgoingLen(state) - 1
        while (out >= StreamState.stageEnd(state) && (scanInitial eq null)) {
          val tag = (outgoingPrim(out) & 0xff).toInt
          if (OpTag.isAsyncMapAccum(tag)) {
            val accum = outgoingRef(out).asInstanceOf[AsyncAccum]
            if (accum.emitInitial) {
              scanInitial = new ScanInitial(accum, tag, outgoingType(out), accum.state, op.programLength, out + 1)
            }
          }
          out -= 1
        }
        var ip = op.programLength - 1
        while (ip >= op.programStart && (scanInitial eq null)) {
          val tag = (op.programPrim(ip) & 0xff).toInt
          if (OpTag.isAsyncMapAccum(tag)) {
            val accum = op.programRef(ip).asInstanceOf[AsyncAccum]
            if (accum.emitInitial) {
              scanInitial =
                new ScanInitial(accum, tag, incomingType(ip), accum.state, ip + 1, StreamState.stageEnd(state))
            }
          }
          ip -= 1
        }
        invoke = scanInitial eq null
      } else if (op.state == Pending) {
        operationEpoch = op.operationEpoch
        leaf = op.pending
        op.state = Polling
      } else {
        op.wakePermit = true
        return op
      }
    }
    if (scanInitial ne null) emitScanInitial(op, operationEpoch, scanInitial)
    else if (invoke) {
      val result =
        try invokeRead(op)
        catch { case cause: Throwable => Async.fail(cause) }
      consume(op, operationEpoch, result, null, notifyObserver = false, ReadyBudget)
    } else pollLeaf(op, operationEpoch, leaf, notifyObserver = false, ReadyBudget)
  }

  private def emitScanInitial[A](op: ReadOperation[A], operationEpoch: Long, initial: ScanInitial): Async[A] = {
    val converted =
      try Right(convertAccumValue(initial.tag, initial.outType, initial.value))
      catch { case cause: Throwable => Left(cause) }
    converted match {
      case Left(cause) =>
        if (!failScanInitial(op, operationEpoch, cause)) handoffNoReplacement(op)
        op.resultOrSelf
      case Right(value) =>
        val committed = synchronized {
          if (
            (active ne op) || epoch != operationEpoch || op.state == Cancelling ||
            !initial.accum.emitInitial
          ) false
          else {
            initial.accum.emitInitial = false
            setConvertedAccumValue(op, initial.tag, value)
            op.awaitingScanInitial = true
            op.resumeIncoming = initial.resumeIncoming
            op.resumeOutgoing = initial.resumeOutgoing
            true
          }
        }
        if (committed) completeRead(op, operationEpoch, (), notifyObserver = false, ReadyBudget)
        else {
          handoffNoReplacement(op)
          op.resultOrSelf
        }
    }
  }

  private def failScanInitial(op: ReadOperation[_], operationEpoch: Long, cause: Throwable): Boolean = synchronized {
    if ((active eq op) && epoch == operationEpoch && op.state != Cancelling) {
      val failure = StreamError.callbackFailure(cause)
      active = null
      clearProgram(op)
      transitionFailure = failure
      transitionFailed = true
      transitionTrusted = false
      op.finishFailure(failure, trusted = false)
      true
    } else false
  }

  private def invokeRead(op: ReadOperation[_]): Async[Any] =
    if (
      (op.terminalFold ne null) && op.readTag == OpTag.READ_I &&
      op.reader.isInstanceOf[InterpreterSyncReader]
    ) {
      op.directIntRead = op.reader.asInstanceOf[InterpreterSyncReader].readIntDirect(Long.MinValue)
      DirectIntRead
    } else if (op.readTag == OpTag.READ_L)
      op.reader.readLongsPhysical(op.longScratch, 0, 1).map { n =>
        if (n < 0) EndOfStream else Long.box(op.longScratch(0))
      }
    else if (op.readTag == OpTag.READ_D)
      op.reader.readDoublesPhysical(op.doubleScratch, 0, 1).map { n =>
        if (n < 0) EndOfStream else Double.box(op.doubleScratch(0))
      }
    else
      (op.readTag: @scala.annotation.switch) match {
        case OpTag.READ_B  => op.reader.readBooleanPhysical(-1)
        case OpTag.READ_BY => op.reader.readBytePhysical().map(value => if (value < 0) Long.MinValue else value.toLong)
        case OpTag.READ_C  => op.reader.readCharPhysical(Int.MinValue)
        case OpTag.READ_S  => op.reader.readShortPhysical(Int.MinValue)
        case OpTag.READ_I  => op.reader.readIntPhysical(Long.MinValue)
        case OpTag.READ_F  => op.reader.readFloatPhysical(Double.MaxValue)
        case OpTag.READ_R  => op.reader.read[Any](EndOfStream)
      }

  private def appendRead(reader: Reader.AsyncReader[_]): Unit = {
    val logicalType = reader.jvmType
    appendRead(reader, logicalType)
  }

  private def appendRead(reader: Reader.AsyncReader[_], logicalType: JvmType): Unit = {
    val lane = SyncInterpreter.laneOf(logicalType)
    val idx  = ensureIncoming()
    incomingPrim(idx) = OpTag.readTag(logicalType).toLong
    incomingRef(idx) = reader.asInstanceOf[AnyRef]
    incomingType(idx) = logicalType
    state = StreamState.withIncomingLen(state, idx + 1)
    logicalOutputType = logicalType
    outputLane = lane
  }

  private def seal(): Unit = {
    sealedState = StreamState(
      StreamState.stageStart(state),
      StreamState.incomingLen(state),
      StreamState.stageEnd(state),
      StreamState.outgoingLen(state),
      outputLane
    )
    sealedLogicalOutputType = logicalOutputType
    sealedOutputRemaining = outputRemaining
  }

  private def ensureIncoming(): Int = {
    val len = StreamState.incomingLen(state)
    if (len == StreamState.MaxIndex)
      throw new IllegalStateException(
        s"Stream pipeline too deep: $len incoming operations exceeds the maximum of ${StreamState.MaxIndex}. " +
          "Simplify the stream composition or reduce flatMap nesting depth."
      )
    if (len == incomingPrim.length) growIncoming()
    len
  }

  private def growIncoming(): Unit = {
    val len = incomingPrim.length
    val np  = new Array[Long](len * 2)
    System.arraycopy(incomingPrim, 0, np, 0, len)
    incomingPrim = np
    val nr = new Array[AnyRef](len * 2)
    System.arraycopy(incomingRef, 0, nr, 0, len)
    incomingRef = nr
    val nt = new Array[JvmType](len * 2)
    System.arraycopy(incomingType, 0, nt, 0, len)
    incomingType = nt
    val nst = new Array[StreamState](len * 2)
    System.arraycopy(savedState, 0, nst, 0, len)
    savedState = nst
    val ns = new Array[JvmType](len * 2)
    System.arraycopy(savedOutputType, 0, ns, 0, len)
    savedOutputType = ns
  }

  private def ensureOutgoing(): Int = {
    val len = StreamState.outgoingLen(state)
    ensureOutgoingCapacity(len, 1)
    len
  }

  private def ensureOutgoingCapacity(priorLen: Int, additional: Int): Unit = {
    val required = priorLen + additional
    if (required > StreamState.MaxIndex)
      throw new IllegalStateException(s"Stream pipeline too deep: $required outgoing operations")
    while (required > outgoingPrim.length) {
      val len = outgoingPrim.length
      val np  = new Array[Long](len * 2)
      System.arraycopy(outgoingPrim, 0, np, 0, len)
      outgoingPrim = np
      val nr = new Array[AnyRef](len * 2)
      System.arraycopy(outgoingRef, 0, nr, 0, len)
      outgoingRef = nr
      val nt = new Array[JvmType](len * 2)
      System.arraycopy(outgoingType, 0, nt, 0, len)
      outgoingType = nt
    }
  }

  private def activeReader: Reader.AsyncReader[Any] = {
    val idx = StreamState.stageStart(state)
    incomingRef(idx).asInstanceOf[Reader.AsyncReader[Any]]
  }

  private def pollLeaf[A](
    op: ReadOperation[A],
    operationEpoch: Long,
    leaf: Pollable[Any],
    notifyObserver: Boolean,
    workLeft: Int
  ): Async[A] = {
    val result =
      try leaf.poll(op.waker)
      catch { case cause: Throwable => Async.fail(cause) }
    consume(op, operationEpoch, result, leaf, notifyObserver, workLeft)
  }

  private def consume[A](
    op: ReadOperation[A],
    operationEpoch: Long,
    result: Async[Any],
    polled: Pollable[Any],
    notifyObserver: Boolean,
    workLeft: Int
  ): Async[A] = {
    val terminalFoldStep = op.awaitingTerminalFold
    if (terminalFoldStep) op.terminalFold.observe(result, op)
    else Async.foldStep(result)(op)
    val recoveryCandidate =
      if (
        op.foldKind == FoldFailure &&
        ((op.recovery eq null) || (op.recovery.phase != RecoveryClosing && op.recovery.phase != RecoveryFailed))
      ) {
        val startBoundary =
          if (op.recovery eq null) asyncRecoveries.length - 1
          else op.recovery.boundaryIndex - 1
        classifyRecovery(
          if (op.inCallback) StreamError.callbackFailure(op.foldFailure) else op.foldFailure,
          if (op.inCallback) false else op.foldTrusted,
          startBoundary
        )
      } else null
    var next: Pollable[Any]          = null
    var continue                     = false
    var schedule                     = false
    var forceMacrotask               = false
    var notify: Runnable             = null
    var cancelled                    = false
    var completeReady                = false
    var readyValue: Any              = null
    var invokeErrorMap               = false
    var invokeRecoveryClose          = false
    var recoveryClaim: RecoveryClaim = null
    var continueTerminalFold         = false
    synchronized {
      if ((active ne op) || epoch != operationEpoch || op.state == Cancelling) {
        cancelled = true
      } else {
        op.foldKind match {
          case FoldSuccess =>
            op.pending = null
            op.state = Invoking
            if (terminalFoldStep) {
              op.terminalFold.commit(op)
              op.awaitingTerminalFold = false
              continueTerminalFold = true
            } else {
              readyValue = op.foldValue
              completeReady = true
            }
          case FoldFailure =>
            // Reader failures may carry trusted provenance, but an asynchronous
            // user callback is always an untrusted boundary.
            val callback = op.inCallback
            if ((recoveryCandidate ne null) && !recoveryCandidate.boundary.switched) {
              val slot   = StreamState.stageStart(state)
              val failed = incomingRef(slot).asInstanceOf[Reader.AsyncReader[Any]]
              val prior  = op.recovery
              if ((failed ne null) && (failed eq op.reader)) {
                recoveryCandidate.boundary.switched = true
                incomingRef(slot) = null
                recoveryClaim = new RecoveryClaim(
                  recoveryCandidate.boundary,
                  failed,
                  slot,
                  recoveryCandidate.boundary.expectedType,
                  if (slot == 0) StreamState.empty else savedState(slot),
                  if (slot == 0) logicalOutputType else savedOutputType(slot),
                  recoveryCandidate.boundary.index,
                  recoveryCandidate.trigger,
                  recoveryCandidate.trusted,
                  op
                )
                op.recovery = recoveryClaim
                op.pending = null
                op.state = Invoking
                invokeRecoveryClose = true
              } else if ((failed eq null) && (prior ne null)) {
                recoveryCandidate.boundary.switched = true
                recoveryClaim = new RecoveryClaim(
                  recoveryCandidate.boundary,
                  null,
                  prior.readSlot,
                  prior.expectedType,
                  prior.outerState,
                  prior.outerType,
                  recoveryCandidate.boundary.index,
                  recoveryCandidate.trigger,
                  recoveryCandidate.trusted,
                  op
                )
                op.recovery = recoveryClaim
                op.pending = null
                op.state = Invoking
                invokeRecoveryClose = true
              }
            }
            if (!invokeRecoveryClose)
              op.foldFailure match {
                case error: StreamError
                    if op.foldTrusted && !callback && !error.cleanupFailed && asyncErrorMaps.nonEmpty =>
                  op.pending = null
                  op.awaitingAsyncErrorMap = true
                  op.asyncErrorMapIndex = 0
                  op.asyncErrorMapOriginal = error
                  op.state = Invoking
                  invokeErrorMap = true
                case _ =>
                  val failure = if (callback) StreamError.callbackFailure(op.foldFailure) else op.foldFailure
                  val trusted = op.foldTrusted && !callback
                  active = null
                  op.pending = null
                  clearProgram(op)
                  transitionFailure = failure
                  transitionFailed = true
                  transitionTrusted = trusted
                  op.finishFailure(failure, trusted)
                  if (notifyObserver) notify = op.observer
              }
          case FoldPending =>
            next = op.foldPending
            op.pending = next
            op.state = Pending
            val replacement = (polled eq null) || (next ne polled)
            if (replacement && workLeft > 0) {
              op.state = Polling
              continue = true
            } else if (replacement) {
              op.wakePermit = true
              schedule = claimSchedule(op)
              if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
            } else if (op.wakePermit) {
              schedule = claimSchedule(op)
              if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
            }
        }
      }
    }
    if (cancelled) {
      handoffCancelled(op, polled)
      return op
    }
    if (continueTerminalFold) {
      if (workLeft <= 0 && yieldRead(op, operationEpoch)) return op
      val next =
        try invokeRead(op)
        catch { case cause: Throwable => Async.fail(cause) }
      return consume(op, operationEpoch, next, null, notifyObserver, workLeft - 1)
    }
    if (invokeRecoveryClose) {
      if (recoveryClaim.failed eq null)
        return completeRecoveryClose(op, operationEpoch, Right(()), notifyObserver, workLeft - 1)
      else
        return consume(
          op,
          operationEpoch,
          recoveryClaim.closeOwner.asInstanceOf[Async[Any]],
          null,
          notifyObserver,
          workLeft - 1
        )
    }
    if (invokeErrorMap) {
      val mapped = invokeAsyncErrorMap(op)
      return consume(op, operationEpoch, mapped, null, notifyObserver, workLeft - 1)
    }
    if (completeReady) return completeRead(op, operationEpoch, readyValue, notifyObserver, workLeft)
    if (notify ne null) safelyRun(notify)
    if (continue) pollLeaf(op, operationEpoch, next, notifyObserver, workLeft - 1)
    else {
      if (schedule) Async.schedule(op, forceMacrotask)
      op.resultOrSelf
    }
  }

  private def completeRead[A](
    op: ReadOperation[A],
    operationEpoch: Long,
    initialRaw: Any,
    notifyObserver: Boolean,
    initialWorkLeft: Int
  ): Async[A] = {
    if (
      (op.recovery ne null) &&
      op.recovery.phase == RecoveryClosing
    )
      return completeRecoveryClose(op, operationEpoch, initialRaw, notifyObserver, initialWorkLeft)
    if ((op.recovery ne null) && op.recovery.phase == RecoveryHandling)
      return completeRecoveryHandler(op, operationEpoch, initialRaw, notifyObserver, initialWorkLeft)
    if (op.awaitingAsyncErrorMap)
      return completeAsyncErrorMap(op, operationEpoch, initialRaw, notifyObserver, initialWorkLeft)
    var raw                        = initialRaw
    var workLeft                   = initialWorkLeft
    var rejected                   = false
    var eof                        = false
    var stale                      = false
    var callbackFailure: Throwable = null
    var computed: Any              = null
    var redrive                    = true
    var resumeIncoming             = op.programStart
    var resumeOutgoing             = StreamState.stageEnd(state)
    var decodeRead                 = true
    var resumedFilterRejected      = false
    var terminalCallbackCompleted  = false
    var takeWhileEof               = false
    if (
      op.awaitingAsyncMap || op.awaitingAsyncFilter || op.awaitingAsyncCollect || op.awaitingAsyncTap ||
      op.awaitingAsyncDistinctKey || op.awaitingAsyncAccum || op.awaitingAsyncTakeWhile || op.awaitingScanInitial
    ) {
      if (initialWorkLeft <= 0) {
        var schedule       = false
        var forceMacrotask = false
        var yielded        = false
        synchronized {
          if ((active eq op) && epoch == operationEpoch && op.state != Cancelling) {
            op.asyncReadyValue = raw
            op.yieldedAsyncReady = true
            op.state = Yielding
            schedule = claimSchedule(op)
            yielded = true
            if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
          }
        }
        if (schedule) Async.schedule(op, forceMacrotask)
        if (yielded) return op
      }
      decodeRead = false
      resumeIncoming = op.resumeIncoming
      resumeOutgoing = op.resumeOutgoing
      val beforeCommit = synchronized {
        val hook = beforeAsyncReadyCommitHook
        beforeAsyncReadyCommitHook = null
        hook
      }
      if (beforeCommit ne null) beforeCommit()
      try {
        if (!operationCurrent(op, operationEpoch)) stale = true
        else if (op.awaitingAsyncMap)
          commitAsyncMapResult(op, operationEpoch, op.asyncMapTag, op.asyncMapType, raw)
        else if (op.awaitingScanInitial) ()
        else if (op.awaitingAsyncAccum)
          stale = !commitAsyncAccumResult(op, operationEpoch, raw)
        else if (op.awaitingAsyncFilter) resumedFilterRejected = !raw.asInstanceOf[Boolean]
        else if (op.awaitingAsyncTakeWhile) {
          takeWhileEof = !raw.asInstanceOf[Boolean]
          eof = takeWhileEof
        } else if (op.awaitingAsyncTap) ()
        else if (op.awaitingAsyncDistinctKey) {
          val acceptance = acceptAsyncDistinctKey(op, operationEpoch, op.asyncDistinctKey, raw)
          if (acceptance < 0) stale = true
          else resumedFilterRejected = acceptance == 0
        } else {
          val option = raw.asInstanceOf[Option[Any]]
          if (option eq null) throw new NullPointerException("async COLLECT callback succeeded with null Option")
          option match {
            case Some(value) =>
              commitAsyncCollectResult(op, operationEpoch, op.asyncCollectTag, op.asyncCollectType, value)
            case None => resumedFilterRejected = true
          }
        }
      } catch { case cause: Throwable => callbackFailure = cause }
      op.awaitingAsyncMap = false
      op.awaitingAsyncFilter = false
      op.awaitingAsyncCollect = false
      op.awaitingAsyncTap = false
      op.awaitingAsyncDistinctKey = false
      op.awaitingAsyncAccum = false
      op.awaitingAsyncTakeWhile = false
      op.awaitingScanInitial = false
      op.awaitingTerminalFold = false
    }
    while (redrive) {
      redrive = false
      rejected = resumedFilterRejected
      resumedFilterRejected = false
      val resumedTakeWhileEof = eof
      eof = resumedTakeWhileEof
      try {
        if (decodeRead && (raw.asInstanceOf[AnyRef] eq DirectIntRead.asInstanceOf[AnyRef])) {
          eof = op.directIntRead == Long.MinValue
          op.vi = op.directIntRead.toInt
        } else if (decodeRead && op.collisionSafe && (raw.asInstanceOf[AnyRef] eq EndOfStream)) eof = true
        else if (decodeRead) (op.readTag: @scala.annotation.switch) match {
          case OpTag.READ_B | OpTag.READ_BY | OpTag.READ_C | OpTag.READ_S | OpTag.READ_I =>
            val value = raw.asInstanceOf[java.lang.Number].longValue()
            eof = !op.collisionSafe && (op.readTag match {
              case OpTag.READ_B  => value == -1L
              case OpTag.READ_BY => value == Long.MinValue
              case OpTag.READ_C  => value == Int.MinValue.toLong
              case OpTag.READ_S  => value == Int.MinValue.toLong
              case _             => value == Long.MinValue
            })
            op.vi = if ((op.sourceType eq JvmType.Boolean) && value != 0L) 1 else value.toInt
          case OpTag.READ_L =>
            op.vl = raw.asInstanceOf[java.lang.Number].longValue()
          case OpTag.READ_F =>
            val value = raw.asInstanceOf[java.lang.Number].doubleValue()
            eof = !op.collisionSafe && value == Double.MaxValue; op.vf = value.toFloat
          case OpTag.READ_D =>
            op.vd = raw.asInstanceOf[java.lang.Number].doubleValue()
          case OpTag.READ_R =>
            op.vr = raw.asInstanceOf[AnyRef]; eof = op.vr eq EndOfStream
        }
        decodeRead = true
        if (!eof && !rejected && !terminalCallbackCompleted && callbackFailure == null && !stale) {
          var ip = resumeIncoming
          while (ip < op.programLength && !stale && !rejected) {
            if (!operationCurrent(op, operationEpoch)) stale = true
            else {
              val tag = (op.programPrim(ip) & 0xff).toInt
              val fn  = op.programRef(ip)
              if (OpTag.isAsyncMap(tag)) {
                op.awaitingAsyncMap = true
                op.asyncMapTag = tag
                op.asyncMapType = incomingType(ip)
                op.resumeIncoming = ip + 1
                op.resumeOutgoing = resumeOutgoing
                val result = invokeAsyncMap(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncFilter(tag)) {
                op.awaitingAsyncFilter = true
                op.resumeIncoming = ip + 1
                op.resumeOutgoing = resumeOutgoing
                val result = invokeAsyncFilter(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncTakeWhile(tag)) {
                op.awaitingAsyncTakeWhile = true
                op.resumeIncoming = ip + 1
                op.resumeOutgoing = resumeOutgoing
                val result = invokeAsyncTakeWhile(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncCollect(tag)) {
                op.awaitingAsyncCollect = true
                op.asyncCollectTag = tag
                op.asyncCollectType = incomingType(ip)
                op.resumeIncoming = ip + 1
                op.resumeOutgoing = resumeOutgoing
                val result = invokeAsyncCollect(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncTap(tag)) {
                op.awaitingAsyncTap = true
                op.resumeIncoming = ip + 1
                op.resumeOutgoing = resumeOutgoing
                val result = invokeAsyncTap(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncDistinctKey(tag)) {
                op.awaitingAsyncDistinctKey = true
                op.asyncDistinctKey = fn.asInstanceOf[AsyncDistinctKey]
                op.resumeIncoming = ip + 1
                op.resumeOutgoing = resumeOutgoing
                val result = invokeAsyncDistinctKey(op, tag, op.asyncDistinctKey.fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncMapAccum(tag)) {
                op.awaitingAsyncAccum = true
                op.asyncAccum = fn.asInstanceOf[AsyncAccum]
                op.asyncAccumOld = op.asyncAccum.state
                op.asyncAccumTag = tag
                op.asyncAccumType = incomingType(ip)
                op.resumeIncoming = ip + 1
                op.resumeOutgoing = resumeOutgoing
                val result = invokeAsyncAccum(op, tag)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (tag >= OpTag.PUSH_I && tag <= OpTag.PUSH_R) {
                val targetType = incomingType(ip)
                val inlineInt  = tag == OpTag.PUSH_I && (targetType eq JvmType.Int)
                val produced   = (tag: @scala.annotation.switch) match {
                  case OpTag.PUSH_I =>
                    if (inlineInt) invokeInlineIntPush(op, fn.asInstanceOf[Int => AnyRef])
                    else inlineScalarPush(op, fn.asInstanceOf[Int => AnyRef](op.vi), targetType)
                  case OpTag.PUSH_L => inlineScalarPush(op, fn.asInstanceOf[Long => AnyRef](op.vl), targetType)
                  case OpTag.PUSH_F => inlineScalarPush(op, fn.asInstanceOf[Float => AnyRef](op.vf), targetType)
                  case OpTag.PUSH_D => inlineScalarPush(op, fn.asInstanceOf[Double => AnyRef](op.vd), targetType)
                  case OpTag.PUSH_R => inlineScalarPush(op, fn.asInstanceOf[AnyRef => AnyRef](op.vr), targetType)
                }
                if (!operationCurrent(op, operationEpoch)) stale = true
                else if (produced eq InlineScalarPush) ip = op.programLength
                else {
                  val installed = installPush(op, operationEpoch, produced, targetType, fromOutgoing = false, -1)
                  val invoke    = installed && claimPushInvocation(op, operationEpoch)
                  stale = !invoke
                  if (invoke) {
                    if (workLeft <= 0 && yieldRead(op, operationEpoch)) return op
                    val result = invokeRead(op)
                    return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
                  }
                }
              } else if (tag >= OpTag.FILTER_I && tag <= OpTag.FILTER_R) {
                val accepted = (tag: @scala.annotation.switch) match {
                  case 25 => fn.asInstanceOf[Int => Boolean](op.vi)
                  case 26 => fn.asInstanceOf[Long => Boolean](op.vl)
                  case 27 => fn.asInstanceOf[Float => Boolean](op.vf)
                  case 28 => fn.asInstanceOf[Double => Boolean](op.vd)
                  case 29 => fn.asInstanceOf[AnyRef => Boolean](op.vr)
                }
                stale = !operationCurrent(op, operationEpoch)
                rejected = !accepted && !stale
              } else
                stale = !((tag: @scala.annotation.switch) match {
                  case 0  => commitI(op, operationEpoch, fn.asInstanceOf[Int => Int](op.vi))
                  case 1  => commitL(op, operationEpoch, fn.asInstanceOf[Int => Long](op.vi))
                  case 2  => commitF(op, operationEpoch, fn.asInstanceOf[Int => Float](op.vi))
                  case 3  => commitD(op, operationEpoch, fn.asInstanceOf[Int => Double](op.vi))
                  case 4  => commitR(op, operationEpoch, fn.asInstanceOf[Int => AnyRef](op.vi))
                  case 5  => commitI(op, operationEpoch, fn.asInstanceOf[Long => Int](op.vl))
                  case 6  => commitL(op, operationEpoch, fn.asInstanceOf[Long => Long](op.vl))
                  case 7  => commitF(op, operationEpoch, fn.asInstanceOf[Long => Float](op.vl))
                  case 8  => commitD(op, operationEpoch, fn.asInstanceOf[Long => Double](op.vl))
                  case 9  => commitR(op, operationEpoch, fn.asInstanceOf[Long => AnyRef](op.vl))
                  case 10 => commitI(op, operationEpoch, fn.asInstanceOf[Float => Int](op.vf))
                  case 11 => commitL(op, operationEpoch, fn.asInstanceOf[Float => Long](op.vf))
                  case 12 => commitF(op, operationEpoch, fn.asInstanceOf[Float => Float](op.vf))
                  case 13 => commitD(op, operationEpoch, fn.asInstanceOf[Float => Double](op.vf))
                  case 14 => commitR(op, operationEpoch, fn.asInstanceOf[Float => AnyRef](op.vf))
                  case 15 => commitI(op, operationEpoch, fn.asInstanceOf[Double => Int](op.vd))
                  case 16 => commitL(op, operationEpoch, fn.asInstanceOf[Double => Long](op.vd))
                  case 17 => commitF(op, operationEpoch, fn.asInstanceOf[Double => Float](op.vd))
                  case 18 => commitD(op, operationEpoch, fn.asInstanceOf[Double => Double](op.vd))
                  case 19 => commitR(op, operationEpoch, fn.asInstanceOf[Double => AnyRef](op.vd))
                  case 20 => commitI(op, operationEpoch, fn.asInstanceOf[AnyRef => Int](op.vr))
                  case 21 => commitL(op, operationEpoch, fn.asInstanceOf[AnyRef => Long](op.vr))
                  case 22 => commitF(op, operationEpoch, fn.asInstanceOf[AnyRef => Float](op.vr))
                  case 23 => commitD(op, operationEpoch, fn.asInstanceOf[AnyRef => Double](op.vr))
                  case 24 => commitR(op, operationEpoch, fn.asInstanceOf[AnyRef => AnyRef](op.vr))
                })
            }
            ip += 1
          }
          var out    = resumeOutgoing
          val outLen = StreamState.outgoingLen(state)
          while (out < outLen && !stale && !rejected) {
            if (!operationCurrent(op, operationEpoch)) stale = true
            else {
              val tag = (outgoingPrim(out) & 0xff).toInt
              val fn  = outgoingRef(out)
              if (OpTag.isAsyncMap(tag)) {
                op.awaitingAsyncMap = true
                op.asyncMapTag = tag
                op.asyncMapType = outgoingType(out)
                op.resumeIncoming = op.programLength
                op.resumeOutgoing = out + 1
                val result = invokeAsyncMap(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncFilter(tag)) {
                op.awaitingAsyncFilter = true
                op.resumeIncoming = op.programLength
                op.resumeOutgoing = out + 1
                val result = invokeAsyncFilter(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncTakeWhile(tag)) {
                op.awaitingAsyncTakeWhile = true
                op.resumeIncoming = op.programLength
                op.resumeOutgoing = out + 1
                val result = invokeAsyncTakeWhile(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncCollect(tag)) {
                op.awaitingAsyncCollect = true
                op.asyncCollectTag = tag
                op.asyncCollectType = outgoingType(out)
                op.resumeIncoming = op.programLength
                op.resumeOutgoing = out + 1
                val result = invokeAsyncCollect(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncTap(tag)) {
                op.awaitingAsyncTap = true
                op.resumeIncoming = op.programLength
                op.resumeOutgoing = out + 1
                val result = invokeAsyncTap(op, tag, fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncDistinctKey(tag)) {
                op.awaitingAsyncDistinctKey = true
                op.asyncDistinctKey = fn.asInstanceOf[AsyncDistinctKey]
                op.resumeIncoming = op.programLength
                op.resumeOutgoing = out + 1
                val result = invokeAsyncDistinctKey(op, tag, op.asyncDistinctKey.fn)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (OpTag.isAsyncMapAccum(tag)) {
                op.awaitingAsyncAccum = true
                op.asyncAccum = fn.asInstanceOf[AsyncAccum]
                op.asyncAccumOld = op.asyncAccum.state
                op.asyncAccumTag = tag
                op.asyncAccumType = outgoingType(out)
                op.resumeIncoming = op.programLength
                op.resumeOutgoing = out + 1
                val result = invokeAsyncAccum(op, tag)
                return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
              } else if (tag >= OpTag.PUSH_I && tag <= OpTag.PUSH_R) {
                val targetType = outgoingType(out)
                val inlineInt  = tag == OpTag.PUSH_I && (targetType eq JvmType.Int)
                val produced   = (tag: @scala.annotation.switch) match {
                  case OpTag.PUSH_I =>
                    if (inlineInt) invokeInlineIntPush(op, fn.asInstanceOf[Int => AnyRef])
                    else inlineScalarPush(op, fn.asInstanceOf[Int => AnyRef](op.vi), targetType)
                  case OpTag.PUSH_L => inlineScalarPush(op, fn.asInstanceOf[Long => AnyRef](op.vl), targetType)
                  case OpTag.PUSH_F => inlineScalarPush(op, fn.asInstanceOf[Float => AnyRef](op.vf), targetType)
                  case OpTag.PUSH_D => inlineScalarPush(op, fn.asInstanceOf[Double => AnyRef](op.vd), targetType)
                  case OpTag.PUSH_R => inlineScalarPush(op, fn.asInstanceOf[AnyRef => AnyRef](op.vr), targetType)
                }
                if (!operationCurrent(op, operationEpoch)) stale = true
                else if (produced eq InlineScalarPush) ()
                else {
                  val installed = installPush(op, operationEpoch, produced, targetType, fromOutgoing = true, out)
                  val invoke    = installed && claimPushInvocation(op, operationEpoch)
                  stale = !invoke
                  if (invoke) {
                    if (workLeft <= 0 && yieldRead(op, operationEpoch)) return op
                    val result = invokeRead(op)
                    return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
                  }
                }
              } else if (tag >= OpTag.FILTER_I && tag <= OpTag.FILTER_R) {
                val accepted = (tag: @scala.annotation.switch) match {
                  case OpTag.FILTER_I => fn.asInstanceOf[Int => Boolean](op.vi)
                  case OpTag.FILTER_L => fn.asInstanceOf[Long => Boolean](op.vl)
                  case OpTag.FILTER_F => fn.asInstanceOf[Float => Boolean](op.vf)
                  case OpTag.FILTER_D => fn.asInstanceOf[Double => Boolean](op.vd)
                  case OpTag.FILTER_R => fn.asInstanceOf[AnyRef => Boolean](op.vr)
                }
                stale = !operationCurrent(op, operationEpoch)
                rejected = !accepted && !stale
              } else
                stale = !applyMap(op, operationEpoch, tag, fn)
            }
            out += 1
          }
        }
        if (!stale && !rejected && !terminalCallbackCompleted) {
          if (eof) computed = op.terminalResult
          else if (op.terminalFold ne null) {
            op.awaitingTerminalFold = true
            val result = invokeReadyTerminalFold(op)
            if (result.asInstanceOf[AnyRef] eq null) {
              op.awaitingTerminalFold = false
              terminalCallbackCompleted = true
              workLeft -= 1
            } else return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
          } else computed = scalarResult(op)
        }
      } catch {
        case cause: Throwable =>
          callbackFailure = cause
      }

      if ((rejected || terminalCallbackCompleted) && !stale && callbackFailure == null) {
        if (workLeft <= 0 && yieldRead(op, operationEpoch)) return op
        if (!operationCurrent(op, operationEpoch)) stale = true
        else {
          val result =
            try invokeRead(op)
            catch { case cause: Throwable => Async.fail(cause) }
          Async.foldStep(result)(op)
          op.foldKind match {
            case FoldSuccess =>
              raw = op.foldValue
              resumeIncoming = op.programStart
              resumeOutgoing = StreamState.stageEnd(state)
              terminalCallbackCompleted = false
              workLeft -= 1
              redrive = true
            case _ => return consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
          }
        }
      }
    }

    var notify: Runnable    = null
    var handoffCancellation = false
    if (callbackFailure ne null) {
      if (!operationCurrent(op, operationEpoch)) {
        handoffNoReplacement(op)
        return op
      } else {
        val primary = StreamError.callbackFailure(callbackFailure)
        val cleanup = takeDeferredPushCleanup(op)
        val failed  =
          if (cleanup.asInstanceOf[AnyRef] eq null) Async.fail(primary)
          else AsyncInterpreter.failAfterCleanup(primary, cleanup)
        return consume(op, operationEpoch, failed, null, notifyObserver, workLeft - 1)
      }
    }
    if (eof && !takeWhileEof && !stale && StreamState.stageStart(state) != 0)
      return beginInnerClose(op, operationEpoch, notifyObserver, workLeft)
    synchronized {
      if ((active ne op) || epoch != operationEpoch || op.state == Cancelling) {
        handoffCancellation = op.cancellation ne null
        stale = true
      } else {
        active = null
        clearProgram(op)
        if (eof) {
          exhausted = true
          op.reachedEof = true
        } else if (outputRemaining != Long.MaxValue) {
          outputRemaining -= 1L
          if (outputRemaining == 0L) exhausted = true
        }
        op.finishSuccess(computed.asInstanceOf[A])
        if (notifyObserver) notify = op.observer
      }
    }
    if (stale) {
      if (handoffCancellation) handoffNoReplacement(op)
      return op
    }
    if (notify ne null) safelyRun(notify)
    op.terminal
  }

  private def classifyRecovery(
    initialCause: Throwable,
    initialTrusted: Boolean,
    startBoundary: Int
  ): RecoveryCandidate = {
    var i = startBoundary
    while (i >= 0) {
      val boundary = asyncRecoveries(i)
      if (!boundary.switched) {
        val eligible =
          if (boundary.typed)
            initialTrusted && (initialCause match {
              case error: StreamError => error.isTrusted && !error.cleanupFailed
              case _                  => false
            })
          else
            !initialTrusted && scala.util.control.NonFatal(initialCause) &&
            ((initialCause eq null) || initialCause.getSuppressed.isEmpty) && (initialCause match {
              case error: StreamError => !error.isTrusted && !error.cleanupFailed
              case _                  => true
            })
        if (eligible) {
          return new RecoveryCandidate(boundary, initialCause, initialTrusted)
        }
      }
      i -= 1
    }
    null
  }

  private def completeRecoveryClose[A](
    op: ReadOperation[A],
    operationEpoch: Long,
    raw: Any,
    notifyObserver: Boolean,
    workLeft: Int
  ): Async[A] = {
    val beforeCommit = synchronized {
      val hook = beforeRecoveryCloseCommitHook
      beforeRecoveryCloseCommitHook = null
      hook
    }
    if (beforeCommit ne null) beforeCommit()
    val claim = synchronized {
      val current = op.recovery
      if (
        (current ne null) && (active eq op) && epoch == operationEpoch && op.state != Cancelling &&
        current.phase == RecoveryClosing
      ) current
      else null
    }
    if (claim eq null) {
      handoffNoReplacement(op)
      return op.resultOrSelf
    }
    val closePrimary = claim.trigger
    raw.asInstanceOf[Either[Throwable, Unit]] match {
      case Left(cleanup)
          if (closePrimary eq null) || (cleanup eq null) || !StreamError.ignorableReplay(closePrimary, cleanup) =>
        synchronized {
          if ((active eq op) && epoch == operationEpoch && (op.recovery eq claim) && op.state != Cancelling)
            claim.phase = RecoveryFailed
        }
        val failure =
          if ((closePrimary eq null) || (cleanup eq null)) closePrimary
          else StreamError.attachCleanupReplay(closePrimary, cleanup)
        consume(
          op,
          operationEpoch,
          Async.fail(failure),
          null,
          notifyObserver,
          workLeft - 1
        )
      case _ =>
        val classification =
          if (claim.boundary.typed) Right(true)
          else
            try Right(claim.boundary.defects.isDefinedAt(claim.trigger))
            catch { case cause: Throwable => Left(StreamError.callbackFailure(cause)) }
        classification match {
          case Left(cause) =>
            synchronized {
              if ((active eq op) && epoch == operationEpoch && (op.recovery eq claim) && op.state != Cancelling)
                claim.phase = RecoveryHandling
            }
            return consume(op, operationEpoch, Async.fail(cause), null, notifyObserver, workLeft - 1)
          case Right(false) =>
            synchronized {
              if ((active eq op) && epoch == operationEpoch && (op.recovery eq claim) && op.state != Cancelling)
                claim.phase = RecoveryHandling
            }
            val rejected = Async.fail(claim.trigger)
            return consume(op, operationEpoch, rejected, null, notifyObserver, workLeft - 1)
          case _ => ()
        }
        val invoke = synchronized {
          val current = (active eq op) && epoch == operationEpoch && (op.recovery eq claim) && op.state != Cancelling
          if (current) claim.phase = RecoveryHandling
          current
        }
        if (!invoke) { handoffNoReplacement(op); return op.resultOrSelf }
        val handled =
          try {
            val effect =
              if (claim.boundary.typed)
                claim.boundary.typedHandler(claim.trigger.asInstanceOf[StreamError].value)
              else claim.boundary.defects(claim.trigger)
            if (effect.asInstanceOf[AnyRef] eq null)
              Async.fail(new NullPointerException("async recovery callback returned null"))
            else effect
          } catch { case cause: Throwable => Async.fail(StreamError.callbackFailure(cause)) }
        consume(op, operationEpoch, handled.asInstanceOf[Async[Any]], null, notifyObserver, workLeft - 1)
    }
  }

  private def completeRecoveryHandler[A](
    op: ReadOperation[A],
    operationEpoch: Long,
    raw: Any,
    notifyObserver: Boolean,
    workLeft: Int
  ): Async[A] = {
    val beforeMaterialization = synchronized {
      val hook = beforeRecoveryMaterializationHook
      beforeRecoveryMaterializationHook = null
      hook
    }
    if (beforeMaterialization ne null) beforeMaterialization()
    val claim = synchronized {
      val current = op.recovery
      if (
        (current ne null) && (active eq op) && epoch == operationEpoch && op.state != Cancelling &&
        current.phase == RecoveryHandling
      ) {
        current.phase = RecoveryMaterializing
        op.state = MaterializingRecovery
        current
      } else null
    }
    if (claim eq null) {
      handoffNoReplacement(op)
      return op.resultOrSelf
    }
    val stream =
      if (claim.boundary.typed) raw.asInstanceOf[Stream[_, Any]]
      else {
        val option = raw.asInstanceOf[Option[Stream[_, Any]]]
        if (option eq null) null
        else
          option match {
            case Some(value) => value
            case None        =>
              return consume(op, operationEpoch, Async.fail(claim.trigger), null, notifyObserver, workLeft - 1)
          }
      }
    if (stream.asInstanceOf[AnyRef] eq null)
      return consume(
        op,
        operationEpoch,
        Async.fail(
          StreamError.callbackFailure(new NullPointerException("async recovery callback succeeded with null stream"))
        ),
        null,
        notifyObserver,
        workLeft - 1
      )
    val materialized = AsyncInterpreter.materializeDetached(
      stream,
      claim.expectedType,
      claim.boundary.allowReferenceRecovery
    )
    materialized match {
      case Left(failure) =>
        val primary  = StreamError.callbackFailure(failure.primary)
        val rejected = AsyncInterpreter.failAfterCleanup(primary, failure.cleanup)
        if (operationCurrent(op, operationEpoch))
          consume(op, operationEpoch, rejected, null, notifyObserver, workLeft - 1)
        else {
          handoffClaimedCleanup(op, failure.cleanup)
          op
        }
      case Right(interpreter) =>
        val owner     = new InterpreterReader(interpreter)
        val installed = synchronized {
          if (
            (active eq op) && epoch == operationEpoch && op.state != Cancelling &&
            (op.recovery eq claim) && claim.phase == RecoveryMaterializing && !closed && !closing &&
            (incomingRef(claim.readSlot) eq null)
          ) {
            incomingRef(claim.readSlot) = owner
            claim.phase = RecoveryInstalled
            op.recovery = null
            bindOperation(op)
            true
          } else false
        }
        if (installed) {
          val result = invokeRead(op)
          consume(op, operationEpoch, result, null, notifyObserver, workLeft - 1)
        } else {
          handoffClaimedCleanup(op, owner.close())
          op
        }
    }
  }

  private def invokeAsyncErrorMap(op: ReadOperation[_]): Async[Any] = {
    val result =
      try asyncErrorMaps(op.asyncErrorMapIndex)(op.asyncErrorMapOriginal.value)
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null)
      Async.fail(new NullPointerException("async error-map callback returned null"))
    else result
  }

  private def completeAsyncErrorMap[A](
    op: ReadOperation[A],
    operationEpoch: Long,
    mappedValue: Any,
    notifyObserver: Boolean,
    workLeft: Int
  ): Async[A] = {
    var invokeNext          = false
    var yieldNext           = false
    var schedule            = false
    var forceMacrotask      = false
    var mapped: StreamError = null
    var notify: Runnable    = null
    val beforeCommit        = synchronized {
      val hook = beforeAsyncErrorMapCommitHook
      beforeAsyncErrorMapCommitHook = null
      hook
    }
    if (beforeCommit ne null) beforeCommit()
    synchronized {
      if ((active ne op) || epoch != operationEpoch || op.state == Cancelling) {
        handoffNoReplacement(op)
        return op
      }
      mapped = StreamError.mapped(op.asyncErrorMapOriginal, mappedValue)
      op.asyncErrorMapIndex += 1
      if (op.asyncErrorMapIndex < asyncErrorMaps.length) {
        op.asyncErrorMapOriginal = mapped
        if (workLeft <= 0) {
          op.yieldedErrorMapInvoke = true
          op.state = Yielding
          schedule = claimSchedule(op)
          if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
          yieldNext = true
        } else {
          op.state = Invoking
          invokeNext = true
        }
      } else {
        op.awaitingAsyncErrorMap = false
        active = null
        op.pending = null
        clearProgram(op)
        transitionFailure = mapped
        transitionFailed = true
        transitionTrusted = true
        op.finishFailure(mapped, trusted = true)
        if (notifyObserver) notify = op.observer
      }
    }
    if (yieldNext) {
      if (schedule) Async.schedule(op, forceMacrotask)
      op
    } else if (invokeNext)
      consume(op, operationEpoch, invokeAsyncErrorMap(op), null, notifyObserver, workLeft - 1)
    else {
      if (notify ne null) safelyRun(notify)
      op.terminal
    }
  }

  private def invokeAsyncMap(op: ReadOperation[_], tag: Int, fn: AnyRef): Async[Any] = {
    val inLane = (tag - OpTag.ASYNC_MAP_II) / 5
    val result =
      try
        (inLane: @scala.annotation.switch) match {
          case 0 => fn.asInstanceOf[Int => Async[Any]](op.vi)
          case 1 => fn.asInstanceOf[Long => Async[Any]](op.vl)
          case 2 => fn.asInstanceOf[Float => Async[Any]](op.vf)
          case 3 => fn.asInstanceOf[Double => Async[Any]](op.vd)
          case _ => fn.asInstanceOf[AnyRef => Async[Any]](op.vr)
        }
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null) Async.fail(new NullPointerException("async MAP callback returned null"))
    else result
  }

  private def invokeAsyncFilter(op: ReadOperation[_], tag: Int, fn: AnyRef): Async[Any] = {
    val lane   = tag - OpTag.ASYNC_FILTER_I
    val result =
      try
        (lane: @scala.annotation.switch) match {
          case 0 => fn.asInstanceOf[Int => Async[Boolean]](op.vi)
          case 1 => fn.asInstanceOf[Long => Async[Boolean]](op.vl)
          case 2 => fn.asInstanceOf[Float => Async[Boolean]](op.vf)
          case 3 => fn.asInstanceOf[Double => Async[Boolean]](op.vd)
          case _ => fn.asInstanceOf[AnyRef => Async[Boolean]](op.vr)
        }
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null) Async.fail(new NullPointerException("async FILTER callback returned null"))
    else result.asInstanceOf[Async[Any]]
  }

  private def invokeAsyncTakeWhile(op: ReadOperation[_], tag: Int, fn: AnyRef): Async[Any] = {
    val lane   = tag - OpTag.ASYNC_TAKE_WHILE_I
    val result =
      try
        (lane: @scala.annotation.switch) match {
          case 0 => fn.asInstanceOf[Int => Async[Boolean]](op.vi)
          case 1 => fn.asInstanceOf[Long => Async[Boolean]](op.vl)
          case 2 => fn.asInstanceOf[Float => Async[Boolean]](op.vf)
          case 3 => fn.asInstanceOf[Double => Async[Boolean]](op.vd)
          case _ => fn.asInstanceOf[AnyRef => Async[Boolean]](op.vr)
        }
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null)
      Async.fail(new NullPointerException("async TAKE_WHILE callback returned null"))
    else result.asInstanceOf[Async[Any]]
  }

  private def invokeAsyncCollect(op: ReadOperation[_], tag: Int, fn: AnyRef): Async[Any] = {
    val lane   = (tag - OpTag.ASYNC_COLLECT_II) / 5
    val result =
      try
        (lane: @scala.annotation.switch) match {
          case 0 => fn.asInstanceOf[Int => Async[Option[Any]]](op.vi)
          case 1 => fn.asInstanceOf[Long => Async[Option[Any]]](op.vl)
          case 2 => fn.asInstanceOf[Float => Async[Option[Any]]](op.vf)
          case 3 => fn.asInstanceOf[Double => Async[Option[Any]]](op.vd)
          case _ => fn.asInstanceOf[AnyRef => Async[Option[Any]]](op.vr)
        }
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null)
      Async.fail(new NullPointerException("async COLLECT callback returned null"))
    else result.asInstanceOf[Async[Any]]
  }

  private def invokeAsyncTap(op: ReadOperation[_], tag: Int, fn: AnyRef): Async[Any] = {
    val lane   = tag - OpTag.ASYNC_TAP_I
    val result =
      try
        (lane: @scala.annotation.switch) match {
          case 0 => fn.asInstanceOf[Int => Async[Unit]](op.vi)
          case 1 => fn.asInstanceOf[Long => Async[Unit]](op.vl)
          case 2 => fn.asInstanceOf[Float => Async[Unit]](op.vf)
          case 3 => fn.asInstanceOf[Double => Async[Unit]](op.vd)
          case _ => fn.asInstanceOf[AnyRef => Async[Unit]](op.vr)
        }
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null) Async.fail(new NullPointerException("async TAP callback returned null"))
    else result.asInstanceOf[Async[Any]]
  }

  private def invokeAsyncDistinctKey(op: ReadOperation[_], tag: Int, fn: AnyRef): Async[Any] = {
    val lane   = tag - OpTag.ASYNC_DISTINCT_KEY_I
    val result =
      try
        (lane: @scala.annotation.switch) match {
          case 0 => fn.asInstanceOf[Int => Async[Any]](op.vi)
          case 1 => fn.asInstanceOf[Long => Async[Any]](op.vl)
          case 2 => fn.asInstanceOf[Float => Async[Any]](op.vf)
          case 3 => fn.asInstanceOf[Double => Async[Any]](op.vd)
          case _ => fn.asInstanceOf[AnyRef => Async[Any]](op.vr)
        }
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null)
      Async.fail(new NullPointerException("async DISTINCT_KEY callback returned null"))
    else result
  }

  private def invokeAsyncAccum(op: ReadOperation[_], tag: Int): Async[Any] = {
    val lane   = (tag - OpTag.ASYNC_MAP_ACCUM_II) / 5
    val fn     = op.asyncAccum.fn
    val result =
      try
        (lane: @scala.annotation.switch) match {
          case 0 => fn.asInstanceOf[(Any, Int) => Async[Any]](op.asyncAccumOld, op.vi)
          case 1 => fn.asInstanceOf[(Any, Long) => Async[Any]](op.asyncAccumOld, op.vl)
          case 2 => fn.asInstanceOf[(Any, Float) => Async[Any]](op.asyncAccumOld, op.vf)
          case 3 => fn.asInstanceOf[(Any, Double) => Async[Any]](op.asyncAccumOld, op.vd)
          case _ => fn.asInstanceOf[(Any, AnyRef) => Async[Any]](op.asyncAccumOld, op.vr)
        }
      catch { case cause: Throwable => Async.fail(cause) }
    if (result.asInstanceOf[AnyRef] eq null)
      Async.fail(new NullPointerException("async MAP_ACCUM callback returned null"))
    else result
  }

  private def commitAsyncAccumResult(op: ReadOperation[_], operationEpoch: Long, raw: Any): Boolean = {
    val (nextState, converted) =
      try {
        val (state, value) =
          if (op.asyncAccum.scan) (raw, raw)
          else {
            val tuple = raw.asInstanceOf[Tuple2[Any, Any]]
            if (tuple eq null) throw new NullPointerException("async MAP_ACCUM callback succeeded with null tuple")
            (tuple._1, tuple._2)
          }
        (state, convertAccumValue(op.asyncAccumTag, op.asyncAccumType, value))
      } catch { case cause: Throwable => throw StreamError.callbackFailure(cause) }
    synchronized {
      if ((active ne op) || epoch != operationEpoch || op.state == Cancelling) false
      else {
        op.asyncAccum.state = nextState
        setConvertedAccumValue(op, op.asyncAccumTag, converted)
        true
      }
    }
  }

  private def convertAccumValue(tag: Int, outType: JvmType, value: Any): Any = {
    val lane = OpTag.storageLaneOfAsyncMapAccumTag(tag)
    lane match {
      case 0 =>
        if (outType eq JvmType.Boolean) { if (value.asInstanceOf[Boolean]) 1 else 0 }
        else if (outType eq JvmType.Char) value.asInstanceOf[Char].toInt
        else value.asInstanceOf[java.lang.Number].intValue()
      case 1 => value.asInstanceOf[java.lang.Number].longValue()
      case 2 => value.asInstanceOf[java.lang.Number].floatValue()
      case 3 => value.asInstanceOf[java.lang.Number].doubleValue()
      case _ => value.asInstanceOf[AnyRef]
    }
  }

  private def setConvertedAccumValue(op: ReadOperation[_], tag: Int, converted: Any): Unit =
    OpTag.storageLaneOfAsyncMapAccumTag(tag) match {
      case 0 => op.vi = converted.asInstanceOf[Int]
      case 1 => op.vl = converted.asInstanceOf[Long]
      case 2 => op.vf = converted.asInstanceOf[Float]
      case 3 => op.vd = converted.asInstanceOf[Double]
      case _ => op.vr = converted.asInstanceOf[AnyRef]
    }

  /** -1 stale, 0 duplicate, 1 newly committed. */
  private def acceptAsyncDistinctKey(
    op: ReadOperation[_],
    operationEpoch: Long,
    distinct: AsyncDistinctKey,
    key: Any
  ): Int = {
    val previous  = synchronized(distinct.seen)
    val candidate =
      try previous + key
      catch { case cause: Throwable => throw StreamError.callbackFailure(cause) }
    val added = candidate.size != previous.size
    synchronized {
      if ((active ne op) || epoch != operationEpoch || op.state == Cancelling) -1
      else if (added) {
        distinct.seen = candidate
        1
      } else 0
    }
  }

  private def commitAsyncMapResult(
    op: ReadOperation[_],
    operationEpoch: Long,
    tag: Int,
    outType: JvmType,
    value: Any
  ): Boolean = {
    val lane = OpTag.storageLaneOfAsyncMapTag(tag)
    lane match {
      case 0 =>
        val n = if (outType eq JvmType.Boolean) { if (value.asInstanceOf[Boolean]) 1 else 0 }
        else if (outType eq JvmType.Char) value.asInstanceOf[Char].toInt
        else value.asInstanceOf[java.lang.Number].intValue()
        commitI(op, operationEpoch, n)
      case 1 => commitL(op, operationEpoch, value.asInstanceOf[java.lang.Number].longValue())
      case 2 => commitF(op, operationEpoch, value.asInstanceOf[java.lang.Number].floatValue())
      case 3 => commitD(op, operationEpoch, value.asInstanceOf[java.lang.Number].doubleValue())
      case _ => commitR(op, operationEpoch, value.asInstanceOf[AnyRef])
    }
  }

  private def commitAsyncCollectResult(
    op: ReadOperation[_],
    operationEpoch: Long,
    tag: Int,
    outType: JvmType,
    value: Any
  ): Boolean = {
    val lane = OpTag.storageLaneOfAsyncCollectTag(tag)
    lane match {
      case 0 =>
        val n = if (outType eq JvmType.Boolean) { if (value.asInstanceOf[Boolean]) 1 else 0 }
        else if (outType eq JvmType.Char) value.asInstanceOf[Char].toInt
        else value.asInstanceOf[java.lang.Number].intValue()
        commitI(op, operationEpoch, n)
      case 1 => commitL(op, operationEpoch, value.asInstanceOf[java.lang.Number].longValue())
      case 2 => commitF(op, operationEpoch, value.asInstanceOf[java.lang.Number].floatValue())
      case 3 => commitD(op, operationEpoch, value.asInstanceOf[java.lang.Number].doubleValue())
      case _ => commitR(op, operationEpoch, value.asInstanceOf[AnyRef])
    }
  }

  private def applyMap(op: ReadOperation[_], operationEpoch: Long, tag: Int, fn: AnyRef): Boolean =
    (tag: @scala.annotation.switch) match {
      case 0  => commitI(op, operationEpoch, fn.asInstanceOf[Int => Int](op.vi))
      case 1  => commitL(op, operationEpoch, fn.asInstanceOf[Int => Long](op.vi))
      case 2  => commitF(op, operationEpoch, fn.asInstanceOf[Int => Float](op.vi))
      case 3  => commitD(op, operationEpoch, fn.asInstanceOf[Int => Double](op.vi))
      case 4  => commitR(op, operationEpoch, fn.asInstanceOf[Int => AnyRef](op.vi))
      case 5  => commitI(op, operationEpoch, fn.asInstanceOf[Long => Int](op.vl))
      case 6  => commitL(op, operationEpoch, fn.asInstanceOf[Long => Long](op.vl))
      case 7  => commitF(op, operationEpoch, fn.asInstanceOf[Long => Float](op.vl))
      case 8  => commitD(op, operationEpoch, fn.asInstanceOf[Long => Double](op.vl))
      case 9  => commitR(op, operationEpoch, fn.asInstanceOf[Long => AnyRef](op.vl))
      case 10 => commitI(op, operationEpoch, fn.asInstanceOf[Float => Int](op.vf))
      case 11 => commitL(op, operationEpoch, fn.asInstanceOf[Float => Long](op.vf))
      case 12 => commitF(op, operationEpoch, fn.asInstanceOf[Float => Float](op.vf))
      case 13 => commitD(op, operationEpoch, fn.asInstanceOf[Float => Double](op.vf))
      case 14 => commitR(op, operationEpoch, fn.asInstanceOf[Float => AnyRef](op.vf))
      case 15 => commitI(op, operationEpoch, fn.asInstanceOf[Double => Int](op.vd))
      case 16 => commitL(op, operationEpoch, fn.asInstanceOf[Double => Long](op.vd))
      case 17 => commitF(op, operationEpoch, fn.asInstanceOf[Double => Float](op.vd))
      case 18 => commitD(op, operationEpoch, fn.asInstanceOf[Double => Double](op.vd))
      case 19 => commitR(op, operationEpoch, fn.asInstanceOf[Double => AnyRef](op.vd))
      case 20 => commitI(op, operationEpoch, fn.asInstanceOf[AnyRef => Int](op.vr))
      case 21 => commitL(op, operationEpoch, fn.asInstanceOf[AnyRef => Long](op.vr))
      case 22 => commitF(op, operationEpoch, fn.asInstanceOf[AnyRef => Float](op.vr))
      case 23 => commitD(op, operationEpoch, fn.asInstanceOf[AnyRef => Double](op.vr))
      case 24 => commitR(op, operationEpoch, fn.asInstanceOf[AnyRef => AnyRef](op.vr))
    }

  private def installPush(
    op: ReadOperation[_],
    operationEpoch: Long,
    produced: AnyRef,
    targetType: JvmType,
    fromOutgoing: Boolean,
    outgoingIdx: Int
  ): Boolean = {
    if (produced eq null) throw new NullPointerException("PUSH callback returned null")
    val beforeMaterialization = synchronized {
      val hook = beforePushMaterializationHook
      beforePushMaterializationHook = null
      hook
    }
    if (beforeMaterialization ne null) beforeMaterialization()
    val mayMaterialize = synchronized {
      val current = (active eq op) && epoch == operationEpoch && op.state != Cancelling
      if (current) {
        ensureIncomingCapacity(StreamState.incomingLen(state), 2)
        op.state = MaterializingPush
      }
      current
    }
    if (!mayMaterialize) return false

    AsyncInterpreter.materializeDetached(produced.asInstanceOf[Stream[_, _]]) match {
      case Left(failure) =>
        val primary = StreamError.callbackFailure(failure.primary)
        val current = synchronized {
          val value = (active eq op) && epoch == operationEpoch && op.state == MaterializingPush
          if (value) {
            op.state = Invoking
            op.deferredPushCleanup = failure.cleanup
          }
          value
        }
        if (current) {
          val hook = synchronized {
            val value = pushMaterializationFailureHook
            pushMaterializationFailureHook = null
            value
          }
          if (hook ne null) hook()
          throw primary
        }
        handoffClaimedPushCleanup(op, failure.cleanup)
        false

      case Right(nested) =>
        val owner      = nested.toReader[Any]
        val nestedType = nested.logicalOutputType
        val nestedLane = SyncInterpreter.laneOf(nestedType)
        val targetLane = SyncInterpreter.laneOf(targetType)
        val bridgeFn   =
          if ((nestedType eq targetType) || ((nestedType ne JvmType.Boolean) && nestedLane == targetLane)) null
          else SyncInterpreter.logicalBridgeFn(nestedType, targetType)
        var installed = false
        synchronized {
          if ((active eq op) && epoch == operationEpoch && op.state == MaterializingPush) {
            val priorLen   = StreamState.incomingLen(state)
            val outerState = StreamState.withOutputLane(state, outputLane)
            incomingPrim(priorLen) = OpTag.readTag(nestedType).toLong
            incomingRef(priorLen) = owner
            incomingType(priorLen) = nestedType
            savedState(priorLen) = outerState
            savedOutputType(priorLen) = logicalOutputType
            val innerState = if (fromOutgoing) StreamState.withStageEnd(state, outgoingIdx + 1) else state
            state = StreamState.withStageStart(StreamState.withIncomingLen(innerState, priorLen + 1), priorLen)
            if (bridgeFn ne null) {
              val bridge = priorLen + 1
              incomingPrim(bridge) = OpTag.mapTag(nestedLane, targetLane).toLong
              incomingRef(bridge) = bridgeFn
              incomingType(bridge) = targetType
              state = StreamState.withIncomingLen(state, bridge + 1)
            }
            bindProgram(op)
            op.state = PushInstalled
            installed = true
          }
        }
        if (!installed) handoffClaimedPushCleanup(op, owner.close())
        installed
    }
  }

  private def invokeInlineIntPush(op: ReadOperation[_], fn: Int => AnyRef): AnyRef = {
    val produced = fn(op.vi)
    produced match {
      case singleton: Stream.SingletonInt => op.vi = singleton.value; InlineScalarPush
      case _                              => produced
    }
  }

  private def inlineScalarPush(op: ReadOperation[_], produced: AnyRef, targetType: JvmType): AnyRef =
    produced match {
      case scalar: Stream.ScalarNode if scalar.scalarType eq targetType =>
        targetType match {
          case JvmType.Boolean => op.vi = if (scalar.scalarBoolean) 1 else 0
          case JvmType.Byte    => op.vi = scalar.scalarByte.toInt
          case JvmType.Char    => op.vi = scalar.scalarChar.toInt
          case JvmType.Double  => op.vd = scalar.scalarDouble
          case JvmType.Float   => op.vf = scalar.scalarFloat
          case JvmType.Int     => op.vi = scalar.scalarInt
          case JvmType.Long    => op.vl = scalar.scalarLong
          case JvmType.Short   => op.vi = scalar.scalarShort.toInt
          case _               => op.vr = scalar.scalarRef
        }
        InlineScalarPush
      case _ => produced
    }

  private def invokeReadyTerminalFold(op: ReadOperation[_]): Async[Any] = {
    val result = op.terminalFold.invoke(op)
    op.terminalFold.observe(result, op)
    if (op.foldKind == FoldSuccess) {
      op.terminalFold.commit(op)
      null.asInstanceOf[Async[Any]]
    } else result.asInstanceOf[Async[Any]]
  }

  private def ensureIncomingCapacity(priorLen: Int, additional: Int): Unit = {
    val required = priorLen + additional
    if (required > StreamState.MaxIndex)
      throw new IllegalStateException(
        s"Stream pipeline too deep: $required incoming operations exceeds the maximum of ${StreamState.MaxIndex}. " +
          "Simplify the stream composition or reduce flatMap nesting depth."
      )
    while (required > incomingPrim.length) growIncoming()
  }

  private def handoffClaimedPushCleanup(op: ReadOperation[_], cleanup: => Async[Unit]): Unit = {
    val effect =
      try cleanup.catchAll(cause => Async.fail(StreamError.callbackFailure(cause)))
      catch { case cause: Throwable => Async.fail(StreamError.callbackFailure(cause)) }
    handoffClaimedCleanup(op, effect)
  }

  private def claimPushInvocation(op: ReadOperation[_], operationEpoch: Long): Boolean = synchronized {
    val current = (active eq op) && epoch == operationEpoch && op.state == PushInstalled
    if (current) op.state = Invoking
    current
  }

  private final class InterpreterReader(inner: AsyncInterpreter)
      extends Reader.AsyncReader[Any]
      with AsyncInterpreter.FusibleReader
      with AsyncInterpreter.TerminalDriver {
    def foldAsync[A, Z](zero: Z, accumulatorType: JvmType, step: (Z, A) => Async[Z]): Async[Z] =
      inner.foldAsync(zero, accumulatorType, step)
    def fuseAsyncMap[A, B](inType: JvmType, outType: JvmType, f: A => Async[B]): Boolean =
      inner.tryFuse(_.addAsyncMap[A, B](inType, outType)(f))
    def fuseFilter[A](inType: JvmType, f: A => Boolean): Boolean =
      inner.tryFuse(_.addFilter[A](inType)(f))
    def fuseMap[A, B](inType: JvmType, outType: JvmType, f: A => B): Boolean =
      inner.tryFuse(_.addMap[A, B](inType, outType)(f))
    override def jvmType: JvmType                                                                              = inner.jvmType
    def isClosed: Async[Boolean]                                                                               = inner.isClosed
    def readable(): Async[Boolean]                                                                             = inner.readable()
    def read[A >: Any](sentinel: A): Async[A]                                                                  = inner.read(sentinel)
    override def readN[A >: Any](n: Int): Async[Chunk[A]]                                                      = inner.readN[A](n)
    override def readUpToN[A >: Any](n: Int): Async[Chunk[A]]                                                  = inner.readUpToN[A](n)
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Async[Int]                          = inner.readBoolean(sentinel)
    override def readByte(): Async[Int]                                                                        = inner.readByte()
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Async[Int]                                = inner.readChar(sentinel)
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Async[Int]                              = inner.readShort(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long]                                = inner.readInt(sentinel)
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Async[Long]                              = inner.readLong(sentinel)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Async[Double]                        = inner.readFloat(sentinel)
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Async[Double]                      = inner.readDouble(sentinel)
    override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Any <:< Byte): Async[Int] =
      inner.readBytes(dest, offset, length)
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Any <:< Int): Async[Int] =
      inner.readInts(dest, offset, length)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      inner.readLongs(dest, offset, length)
    override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: Any <:< Float): Async[Int] =
      inner.readFloats(dest, offset, length)
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      inner.readDoubles(dest, offset, length)
    def close(): Async[Unit]                       = inner.closeOwned()
    override def skip(n: Long): Async[Unit]        = inner.skip(n)
    override def setLimit(n: Long): Async[Boolean] = inner.setLimit(n)
    override def setRepeat(): Async[Boolean]       = inner.setRepeat()
    override def setSkip(n: Long): Async[Boolean]  = inner.setSkip(n)
    override def reset(): Async[Unit]              = inner.reset()
  }

  private final class InterpreterSyncReader(source: Reader.SyncReader[Any]) extends Reader.AsyncReader[Any] {
    override def jvmType: JvmType                                                     = source.jvmType
    override private[streams] def tryReadable                                         = synchronized(source.tryReadable)
    def close(): Async[Unit]                                                          = attempt(source.close())
    def isClosed: Async[Boolean]                                                      = attempt(source.isClosed)
    def readable(): Async[Boolean]                                                    = attempt(source.readable())
    def read[A >: Any](sentinel: A): Async[A]                                         = attempt(source.read(sentinel))
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Async[Int] =
      attempt(source.readBooleanPhysical(sentinel))
    override def readByte(): Async[Int] =
      attempt(source.readBytePhysical())
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Async[Int] =
      attempt(source.readCharPhysical(sentinel))
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Async[Int] =
      attempt(source.readShortPhysical(sentinel))
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] =
      attempt(source.readIntPhysical(sentinel))
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Async[Long] =
      attempt(source.readLongPhysical(sentinel))
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Async[Double] =
      attempt(source.readFloatPhysical(sentinel))
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Async[Double] =
      attempt(source.readDoublePhysical(sentinel))
    override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Any <:< Byte): Async[Int] =
      attempt(source.readBytesPhysical(dest, offset, length))
    override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Any <:< Int): Async[Int] =
      attempt(source.readIntsPhysical(dest, offset, length))
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      attempt(source.readLongsPhysical(dest, offset, length))
    override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: Any <:< Float): Async[Int] =
      attempt(source.readFloatsPhysical(dest, offset, length))
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      attempt(source.readDoublesPhysical(dest, offset, length))
    override def reset(): Async[Unit]              = attempt(source.reset())
    override def setLimit(n: Long): Async[Boolean] = attempt(source.setLimit(n))
    override def setRepeat(): Async[Boolean]       = attempt(source.setRepeat())
    override def setSkip(n: Long): Async[Boolean]  = attempt(source.setSkip(n))
    override def skip(n: Long): Async[Unit]        = attempt(source.skip(n))

    def readIntDirect(sentinel: Long): Long = synchronized(source.readIntPhysical(sentinel))

    private def attempt[A](body: => A): Async[A] =
      try Async.succeed(synchronized(body))
      catch {
        case error: StreamError if error.isTrusted => Async.failTrusted(error)
        case cause: Throwable                      => Async.fail(cause)
      }
  }

  private def tryFuse(configure: AsyncInterpreter => Unit): Boolean = synchronized {
    if (
      nextGeneration != 0L || (active ne null) || (activeControl ne null) || transitionFailed || exhausted || closed ||
      closing || (ownedClose.asInstanceOf[AnyRef] ne null)
    ) false
    else {
      val length = if (afterPush) StreamState.outgoingLen(state) else StreamState.incomingLen(state)
      if (length > StreamState.MaxIndex - 2) false
      else {
        if (afterPush) ensureOutgoingCapacity(length, 2)
        else ensureIncomingCapacity(length, 2)
        configure(this)
        seal()
        true
      }
    }
  }

  private[streams] def closeOwned(): Async[Unit] =
    synchronized {
      if (ownedClose.asInstanceOf[AnyRef] ne null) return ownedClose
      ownedClose = new OwnedClose
      ownedClose
    }

  private def closeRoot(): Async[Unit] = synchronized {
    val root = rootReader
    if (root eq null) Async.succeed(())
    else {
      if (rootCloseOwner eq null) rootCloseOwner = new RootClose(root)
      rootCloseOwner
    }
  }

  private final class RootClose(reader: Reader.AsyncReader[Any]) extends Async.Operation[Unit] {
    private val completion                        = new Completer[Unit]
    private var started                           = false
    private var constructingThread: Thread        = null
    private var closeRunning: Async.Running[Unit] = null

    def poll(onComplete: Runnable): Async[Unit] = {
      val (leader, reentrant) = synchronized {
        if (started) {
          val nested = (constructingThread eq Thread.currentThread()) ||
            ((closeRunning ne null) && closeRunning.isDriverThread)
          (false, nested)
        } else {
          started = true
          constructingThread = Thread.currentThread()
          (true, false)
        }
      }
      if (leader) {
        val close =
          try reader.close()
          catch { case cause: Throwable => Async.fail(cause) }
          finally synchronized { constructingThread = null }
        val publish = close.foldCause { cause => completion.fail(cause); () } { _ => completion.succeed(()); () }
        Async.startRegistered(publish)(running => synchronized { closeRunning = running })
      }
      if (reentrant) Async.succeed(()) else completion.poll(onComplete)
    }

    protected def cancelOperation(): Async[Unit] = {
      poll(new Runnable { def run(): Unit = () })
      completion.peek
    }

    def isDriverThread: Boolean = synchronized {
      (constructingThread eq Thread.currentThread()) || ((closeRunning ne null) && closeRunning.isDriverThread)
    }
  }

  private final class ControlOperation[A](
    val generation: Long,
    make: () => Async[A],
    onCommit: A => Unit,
    mode: Int,
    onCancel: () => Async[Unit],
    cancelResult: () => A = null
  ) extends Async.Operation[A] {
    private val completion                       = new Completer[A]
    private var started                          = false
    private var cancelled                        = false
    private var running: Async.Running[_]        = null
    private var cancellation: Async.Cancellation = null
    private var cancellationJoin: Async[Unit]    = null
    private var constructingThread: Thread       = null

    def poll(onComplete: Runnable): Async[A] = {
      val leader = AsyncInterpreter.this.synchronized {
        if (started || cancelled) false
        else {
          started = true
          if (active ne null) completion.fail(new IllegalStateException(ConcurrentOperationMessage))
          else if (activeControl ne null) completion.fail(new IllegalStateException(ConcurrentOperationMessage))
          else if (mode == QueryClosed && (closed || transitionFailed || exhausted))
            completion.succeed(true.asInstanceOf[A])
          else if (mode == QueryReadable && (closed || transitionFailed || exhausted))
            completion.succeed(false.asInstanceOf[A])
          else if (closing) completion.fail(new IOException("Reader close is in progress"))
          else if ((mode == MutatingMapsOnly || mode == SkipControl || mode == BulkControl) && transitionFailed)
            if (transitionTrusted) completion.failTrusted(transitionFailure) else completion.fail(transitionFailure)
          else if ((mode == MutatingMapsOnly || mode == SkipControl) && closed)
            completion.fail(new IOException("Reader is closed"))
          else if (mode == MutatingMapsOnly && !mapsOnly) completion.succeed(false.asInstanceOf[A])
          else activeControl = this
          activeControl eq this
        }
      }
      if (leader) {
        val effect = try {
          AsyncInterpreter.this.synchronized { constructingThread = Thread.currentThread() }
          make()
        } catch { case cause: Throwable => Async.fail(cause) }
        finally AsyncInterpreter.this.synchronized { constructingThread = null }
        val observer = new Runnable {
          def run(): Unit = {
            val current = AsyncInterpreter.this.synchronized(running.asInstanceOf[Async.Running[A]])
            if (current ne null) {
              val result: Async[A] =
                try current.poll(this)
                catch { case cause: Throwable => Async.fail(cause) }
              Async.foldStep(result)(new Async.StepFold[A, Unit] {
                def success(value: A): Unit                         = finishSuccess(value)
                def failure(cause: Throwable): Unit                 = finishFailure(cause, trusted = false)
                override def trustedFailure(cause: Throwable): Unit = finishFailure(cause, trusted = true)
                def pending(pollable: Pollable[A]): Unit            = ()
              })
            }
          }
        }
        Async.startRegistered(effect) { startedRunning =>
          runBeforeControlRegistrationHook()
          val cancelNow = AsyncInterpreter.this.synchronized {
            running = startedRunning
            cancelled
          }
          if (cancelNow) {
            val finalizer =
              try onCancel()
              catch { case cause: Throwable => Async.fail(cause) }
            val cleanup = combineCleanup(Async.cancelWithCleanup(startedRunning), finalizer)
            val owner   = AsyncInterpreter.this.synchronized(cancellation)
            if (owner eq null) Async.startRegistered(cleanup)(_ => ())
            else {
              owner.primary(cleanup)
              owner.noReplacement()
            }
          } else observer.run()
        }
      }
      completion.poll(onComplete)
    }

    private def finishSuccess(value: A): Unit = {
      val publish = AsyncInterpreter.this.synchronized {
        val current =
          !cancelled && (activeControl eq this) &&
            (mode == BulkControl || !closed)
        if (current) {
          onCommit(value)
          activeControl = null
        }
        current
      }
      if (publish) completion.succeed(value)
    }

    private def finishFailure(cause: Throwable, trusted: Boolean): Unit = {
      val publish = AsyncInterpreter.this.synchronized {
        val current =
          !cancelled && (activeControl eq this) && (!closed || (ownedClose.asInstanceOf[AnyRef] eq null))
        if (current) {
          activeControl = null
          if (mode == BulkControl) {
            transitionFailure = cause
            transitionFailed = true
            transitionTrusted = trusted
          }
        }
        current
      }
      if (publish) {
        if (trusted) completion.failTrusted(cause) else completion.fail(cause)
      }
    }

    protected def cancelOperation(): Async[Unit] = {
      val (joined, child) = AsyncInterpreter.this.synchronized {
        if (cancellation ne null) (cancellationJoin, null)
        else {
          cancelled = true
          epoch += 1L
          cancellation = Async.cancellation()
          cancellationJoin = cancellation.effect.foldCause { cause =>
            AsyncInterpreter.this.synchronized {
              if ((activeControl eq this) && mode == BulkControl && !closed) {
                transitionFailure = cause
                transitionFailed = true
                transitionTrusted = false
              }
              if (activeControl eq this) activeControl = null
            }
            completion.fail(cause)
            Left(cause): Either[Throwable, Unit]
          } { _ =>
            AsyncInterpreter.this.synchronized {
              if (activeControl eq this) activeControl = null
            }
            if (cancelResult ne null) completion.succeed(cancelResult())
            Right(()): Either[Throwable, Unit]
          }.flatMap {
            case Left(cause) => Async.fail(cause)
            case Right(_)    => Async.succeed(())
          }
          (cancellationJoin, running)
        }
      }
      if (child ne null) {
        val finalizer =
          try onCancel()
          catch { case cause: Throwable => Async.fail(cause) }
        cancellation.primary(combineCleanup(Async.cancelWithCleanup(child), finalizer))
      }
      if (child ne null) cancellation.noReplacement()
      else if (!started) {
        cancellation.primary(Async.succeed(()))
        cancellation.noReplacement()
      }
      joined
    }

    def isDriverThread: Boolean = AsyncInterpreter.this.synchronized {
      (constructingThread eq Thread.currentThread()) || ((running ne null) && running.isDriverThread)
    }

    def isBulk: Boolean = mode == BulkControl

    def abandonReentrant(): Unit = {
      val child = AsyncInterpreter.this.synchronized {
        cancelled = true
        if (activeControl eq this) activeControl = null
        running
      }
      if (child ne null) {
        val finalizer =
          try onCancel()
          catch { case cause: Throwable => Async.fail(cause) }
        Async.startRegistered(combineCleanup(Async.cancelWithCleanup(child), finalizer))(_ => ())
      }
    }

    def finishAbandoned(closeFailure: Throwable): Unit = {
      val failure = new IOException("Reader was closed during its active lifecycle operation")
      StreamError.attachCleanupReplay(failure, closeFailure)
      completion.fail(failure)
    }
  }

  private final class OwnedClose extends Async.Operation[Unit] {
    private val completion                        = new Completer[Unit]
    private var started                           = false
    private var constructingThread: Thread        = null
    private var closeRunning: Async.Running[Unit] = null

    def poll(onComplete: Runnable): Async[Unit] = {
      var activeReadReentrant                   = false
      var bulkControlReentrant                  = false
      var abandonedControl: ControlOperation[_] = null
      val lifecycleReentrant                    = AsyncInterpreter.this.synchronized {
        ((active ne null) && active.isDriverThread) ||
        ((activeControl ne null) && activeControl.isDriverThread) ||
        ((rootCloseOwner ne null) && rootCloseOwner.isDriverThread)
      }
      val (leader, reentrant) = synchronized {
        if (started) {
          val nested = (constructingThread eq Thread.currentThread()) ||
            ((closeRunning ne null) && closeRunning.isDriverThread)
          (false, nested)
        } else {
          started = true
          constructingThread = Thread.currentThread()
          (true, false)
        }
      }
      if (leader) {
        val (readers, finalizers, initialFailure, initialFailureSet, readCleanup) =
          AsyncInterpreter.this.synchronized {
            closed = true
            closing = true
            epoch += 1L
            activeReadReentrant = (active ne null) && active.isDriverThread
            bulkControlReentrant = (activeControl ne null) && activeControl.isDriverThread && activeControl.isBulk
            val rootCloseReentrant = (rootCloseOwner ne null) && rootCloseOwner.isDriverThread
            val readCleanup        =
              if (active eq null) Async.succeed(())
              else Async.cancelWithCleanup(active.asInstanceOf[Pollable[Any]])
            val controlCleanup =
              if (activeControl eq null) Async.succeed(())
              else if (activeControl.isDriverThread && activeControl.isBulk)
                Async.cancelWithCleanup(activeControl)
              else if (activeControl.isDriverThread || rootCloseReentrant) {
                abandonedControl = activeControl
                activeControl.abandonReentrant()
                Async.succeed(())
              } else Async.cancelWithCleanup(activeControl)
            val cleanup = combineCleanup(readCleanup, controlCleanup)
            val claimed = new scala.collection.mutable.ArrayBuffer[Reader.AsyncReader[Any]]
            var i       = 1
            val len     = StreamState.incomingLen(state)
            while (i < len) {
              if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
                val reader = incomingRef(i).asInstanceOf[Reader.AsyncReader[Any]]
                incomingRef(i) = null
                if (reader ne null) claimed += reader
              }
              i += 1
            }
            val claimedFinalizers = new scala.collection.mutable.ArrayBuffer[() => Async[Unit]](asyncFinalizers.length)
            claimedFinalizers ++= asyncFinalizers
            asyncFinalizers.clear()
            (claimed, claimedFinalizers, if (transitionFailed) transitionFailure else null, transitionFailed, cleanup)
          }
        val initial        = if (initialFailureSet) Async.fail(initialFailure) else Async.succeed(())
        val owners         = combineCleanup(closeRoot(), closeReaders(readers))
        val finalizerClose = closeFinalizers(finalizers)
        val start          = combineCleanup(readCleanup, combineCleanup(initial, combineCleanup(owners, finalizerClose)))
        val publish        = start.foldCause { cause =>
          AsyncInterpreter.this.synchronized { closing = false }
          completion.fail(cause)
          if (abandonedControl ne null) abandonedControl.finishAbandoned(cause)
          ()
        } { _ =>
          AsyncInterpreter.this.synchronized { closing = false }
          completion.succeed(())
          if (abandonedControl ne null) abandonedControl.finishAbandoned(null)
          ()
        }
        val launch = new Runnable {
          def run(): Unit = {
            val register = (running: Async.Running[Unit]) => synchronized { closeRunning = running }
            if (abandonedControl ne null) Async.startRegisteredInline(publish)(register)
            else Async.startRegistered(publish)(register)
          }
        }
        try
          if (bulkControlReentrant) Async.schedule(launch, forceMacrotask = false)
          else launch.run()
        finally synchronized { constructingThread = null }
      }
      if (reentrant || activeReadReentrant || lifecycleReentrant) Async.succeed(())
      else completion.poll(onComplete)
    }

    protected def cancelOperation(): Async[Unit] =
      poll(new Runnable { def run(): Unit = () })
  }

  private def closeReaders(
    readers: scala.collection.mutable.ArrayBuffer[Reader.AsyncReader[Any]]
  ): Async[Unit] =
    if (readers.isEmpty) Async.succeed(())
    else new ReadersClose(readers)

  /**
   * Finalizer thunks are deliberately adapted to the lazy reader-close driver:
   * constructing the driver does not invoke a thunk, each thunk is invoked at
   * most once, and the next thunk is not touched until the previous effect has
   * terminated. This also gives finalizers the same pending replacement, wake,
   * fairness, and reentrant-close behaviour as owned readers.
   */
  private def closeFinalizers(finalizers: scala.collection.mutable.ArrayBuffer[() => Async[Unit]]): Async[Unit] =
    if (finalizers.isEmpty) Async.succeed(())
    else {
      val readers = new scala.collection.mutable.ArrayBuffer[Reader.AsyncReader[Any]](finalizers.length)
      // Materialization peels outer wrappers first. Reverse that registration
      // order so nested finalizers retain the established inner-to-outer
      // stream finalization contract.
      var i = finalizers.length - 1
      while (i >= 0) {
        readers += new FinalizerReader(finalizers(i))
        i -= 1
      }
      closeReaders(readers)
    }

  private final class FinalizerReader(thunk: () => Async[Unit]) extends Reader.AsyncReader[Any] {
    private var closed                      = false
    private var completion: Completer[Unit] = null

    override def jvmType: JvmType             = JvmType.AnyRef
    def read[A >: Any](sentinel: A): Async[A] = Async.succeed(sentinel)
    def readable(): Async[Boolean]            = Async.succeed(false)
    def isClosed: Async[Boolean]              = Async.succeed(synchronized(closed))
    override def reset(): Async[Unit]         = Async.succeed(())

    def close(): Async[Unit] = launderCallbackEffect(Async.deferCancelable(() => claim(), () => ()).flatten)

    private def claim(): Async[Unit] = {
      val (result, leader) = synchronized {
        if (completion eq null) {
          completion = new Completer[Unit]
          closed = true
          (completion, true)
        } else (completion, false)
      }
      if (!leader) result
      else {
        val effect =
          try {
            val value = thunk()
            if (value.asInstanceOf[AnyRef] eq null)
              Async.fail(StreamError.callbackFailure(new NullPointerException("async finalizer returned null")))
            else value.catchAll(cause => Async.fail(StreamError.callbackFailure(cause)))
          } catch { case cause: Throwable => Async.fail(StreamError.callbackFailure(cause)) }
        effect.either.flatMap {
          case Right(_)      => result.succeed(()); Async.succeed(())
          case Left(failure) => result.fail(failure); Async.fail(failure)
        }
      }
    }
  }

  private final class ReadersClose(readers: scala.collection.mutable.ArrayBuffer[Reader.AsyncReader[Any]])
      extends Async.Operation[Unit]
      with Async.StepFold[Any, Unit]
      with Runnable {
    private val completion                 = new Completer[Unit]
    private var index                      = 0
    private var primary: Throwable         = null
    private var failureSet                 = false
    private var pending: Pollable[Any]     = null
    private var started                    = false
    private var running                    = false
    private var scheduled                  = false
    private var finished                   = false
    private var wakePermit                 = false
    private var yieldCount                 = 0
    private var foldKind                   = FoldSuccess
    private var foldFailure: Throwable     = null
    private var foldPending: Pollable[Any] = null

    private val waker: Runnable = new Runnable {
      def run(): Unit = {
        var schedule       = false
        var forceMacrotask = false
        ReadersClose.this.synchronized {
          wakePermit = true
          if (started && !finished && !running && !scheduled) {
            scheduled = true
            yieldCount += 1
            forceMacrotask = yieldCount % MicrotaskYieldsBeforeMacrotask == 0
            schedule = true
          }
        }
        if (schedule) Async.schedule(ReadersClose.this, forceMacrotask)
      }
    }

    def success(value: Any): Unit                       = foldKind = FoldSuccess
    def failure(cause: Throwable): Unit                 = { foldKind = FoldFailure; foldFailure = cause }
    override def trustedFailure(cause: Throwable): Unit = failure(cause)
    def pending(value: Pollable[Any]): Unit             = { foldKind = FoldPending; foldPending = value }

    private[streams] def wakeWithoutSchedulingForTest(): Unit = synchronized {
      wakePermit = true
    }

    def poll(onComplete: Runnable): Async[Unit] = {
      var drive               = false
      var leaf: Pollable[Any] = null
      synchronized {
        if (!started) {
          started = true
          running = true
          drive = true
        } else if (!finished && !running && !scheduled && wakePermit) {
          wakePermit = false
          running = true
          leaf = pending
          drive = true
        }
      }
      if (drive) driveClose(leaf, ReadyBudget)
      completion.poll(onComplete)
    }

    def run(): Unit = {
      var drive               = false
      var leaf: Pollable[Any] = null
      synchronized {
        scheduled = false
        if (!finished && !running) {
          wakePermit = false
          running = true
          leaf = pending
          drive = true
        }
      }
      if (drive) driveClose(leaf, ReadyBudget)
    }

    protected def cancelOperation(): Async[Unit] = {
      poll(new Runnable { def run(): Unit = () })
      completion.peek
    }

    private def driveClose(initial: Pollable[Any], initialWorkLeft: Int): Unit = {
      var leaf     = initial
      var workLeft = initialWorkLeft
      while (true) {
        val result =
          if (leaf eq null)
            try readers(index).close().asInstanceOf[Async[Any]]
            catch { case cause: Throwable => Async.fail(cause) }
          else
            try leaf.poll(waker)
            catch {
              case cause: Throwable =>
                if (readers(index).isInstanceOf[FinalizerReader])
                  Async.fail(StreamError.callbackFailure(cause))
                else Async.fail(cause)
            }
        Async.foldStep(result)(this)
        foldKind match {
          case FoldSuccess =>
            index += 1
            leaf = null
            pending = null
          case FoldFailure =>
            if (!failureSet) {
              primary = foldFailure
              failureSet = true
            } else if ((primary ne null) && (foldFailure ne null))
              primary = StreamError.attachCleanupReplay(primary, foldFailure)
            index += 1
            leaf = null
            pending = null
          case FoldPending =>
            val next        = foldPending
            val replacement = (leaf eq null) || (next ne leaf)
            pending = next
            if (replacement && workLeft > 0) leaf = next
            else {
              synchronized { running = false }
              if (replacement || wakePermit) scheduleClose()
              return
            }
        }
        if (foldKind != FoldPending || (leaf ne null)) {
          if (index == readers.length) {
            val (failure, failed) = synchronized {
              running = false
              finished = true
              (primary, failureSet)
            }
            if (failed) completion.fail(failure) else completion.succeed(())
            return
          }
          workLeft -= 1
          if (workLeft <= 0) {
            synchronized { running = false }
            scheduleClose()
            return
          }
        }
      }
    }

    private def scheduleClose(): Unit = {
      var schedule       = false
      var forceMacrotask = false
      synchronized {
        if (!finished && !scheduled) {
          scheduled = true
          yieldCount += 1
          forceMacrotask = yieldCount % MicrotaskYieldsBeforeMacrotask == 0
          schedule = true
        }
      }
      if (schedule) Async.schedule(this, forceMacrotask)
    }
  }

  private def beginInnerClose[A](
    op: ReadOperation[A],
    operationEpoch: Long,
    notifyObserver: Boolean,
    workLeft: Int
  ): Async[A] = {
    val beforeClose = synchronized {
      val hook = beforeInnerCloseHook
      beforeInnerCloseHook = null
      hook
    }
    if (beforeClose ne null) beforeClose()
    var claim: InnerCloseClaim                = null
    var staleCancellation: Async.Cancellation = null
    synchronized {
      if ((active ne op) || epoch != operationEpoch || op.state == Cancelling)
        staleCancellation = op.cancellation
      else {
        val slot = StreamState.stageStart(state)
        claim = new InnerCloseClaim()
        claim.reset(op.reader, slot, savedState(slot), savedOutputType(slot), op, notifyObserver)
        incomingRef(slot) = null
        incomingPrim(slot) = 0L
        incomingType(slot) = null
        savedState(slot) = StreamState.empty
        savedOutputType(slot) = null
        op.innerClose = claim
        op.pending = null
        op.state = InvokingInnerClose
      }
    }
    if (staleCancellation ne null) { handoffNoReplacement(op); return op }
    val result =
      try claim.reader.close().asInstanceOf[Async[Any]]
      catch { case cause: Throwable => Async.fail(cause) }
    driveInnerClose(claim, result, null, workLeft)
    op.resultOrSelf
  }

  private def driveInnerClose(
    claim: InnerCloseClaim,
    result: Async[Any],
    polled: Pollable[Any],
    workLeft: Int
  ): Unit = {
    Async.foldStep(result)(claim)
    var next: Pollable[Any] = null
    var continue            = false
    var schedule            = false
    var forceMacrotask      = false
    var succeeded           = false
    var failed: Throwable   = null
    var failedSet           = false
    synchronized {
      val op = claim.operation
      if (
        (active ne op) || (op.innerClose ne claim) ||
        (op.state != InvokingInnerClose && op.state != PollingInnerClose)
      ) return
      claim.foldKind match {
        case FoldSuccess => succeeded = true
        case FoldFailure => failed = claim.foldFailure; failedSet = true
        case FoldPending =>
          next = claim.foldPending
          claim.pending = next
          op.state = InvokingInnerClose
          val replacement = (polled eq null) || (next ne polled)
          if (replacement && workLeft > 0) {
            op.state = PollingInnerClose
            claim.pollInFlight = true
            continue = true
          } else if (replacement || claim.wakePermit) {
            claim.wakePermit = true
            schedule = claimCloseSchedule(claim)
            if (schedule) forceMacrotask = claim.operation.nextYieldForcesMacrotask()
          }
      }
    }
    if (succeeded) finishInnerCloseSuccess(claim, workLeft)
    else if (failedSet) finishInnerCloseFailure(claim, failed)
    else if (continue) pollInnerClose(claim, next, workLeft - 1)
    else if (schedule) Async.schedule(claim, forceMacrotask)
  }

  private def pollInnerClose(claim: InnerCloseClaim, leaf: Pollable[Any], workLeft: Int): Unit = {
    val result =
      try leaf.poll(claim.waker)
      catch { case cause: Throwable => Async.fail(cause) }
    synchronized { claim.pollInFlight = false }
    driveInnerClose(claim, result, leaf, workLeft)
  }

  private def finishInnerCloseSuccess(claim: InnerCloseClaim, workLeft: Int): Unit = {
    var redrive: ReadOperation[Any] = null
    var notify: Runnable            = null
    var schedule                    = false
    var forceMacrotask              = false
    synchronized {
      val op = claim.operation
      if ((active ne op) || (op.innerClose ne claim)) return
      state = claim.outerState
      logicalOutputType = claim.outerType
      outputLane = StreamState.outputLane(state)
      if (StreamState.incomingLen(state) < op.restartIncomingLen) {
        op.restartState = state
        op.restartType = logicalOutputType
        op.restartIncomingLen = StreamState.incomingLen(state)
      }
      op.innerClose = null
      op.pending = null
      if (op.closeCancellationRequested) {
        active = null
        clearProgram(op)
        op.finishCancelled()
        notify = op.observer
      } else {
        bindOperation(op)
        if (workLeft > 0) redrive = op.asInstanceOf[ReadOperation[Any]]
        else {
          op.state = Yielding
          schedule = claimSchedule(op)
          if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
        }
      }
    }
    val completion = claim.takeCompletion()
    if (completion ne null) completion.succeed(())
    if (notify ne null) safelyRun(notify)
    if (redrive ne null) {
      val result =
        try invokeRead(redrive)
        catch { case cause: Throwable => Async.fail(cause) }
      consume(redrive, redrive.operationEpoch, result, null, claim.notifyObserver, workLeft - 1)
    } else if (schedule) Async.schedule(claim.operation, forceMacrotask)
  }

  private def finishInnerCloseFailure(claim: InnerCloseClaim, failure: Throwable): Unit = {
    StreamError.attachCleanup(null, failure)
    var notify: Runnable                 = null
    var cancellationCleanup: Async[Unit] = null
    synchronized {
      val op = claim.operation
      if ((active ne op) || (op.innerClose ne claim)) return
      op.innerClose = null
      op.pending = null
      if (op.closeCancellationRequested) {
        state = claim.outerState
        logicalOutputType = claim.outerType
        outputLane = StreamState.outputLane(state)
        state = op.restartState
        logicalOutputType = op.restartType
        outputLane = StreamState.outputLane(state)
        cancellationCleanup = Async.fail(failure)
      } else {
        active = null
        clearProgram(op)
        transitionFailure = failure
        transitionFailed = true
        transitionTrusted = false
        op.finishFailure(failure, trusted = false)
        notify = op.observer
      }
    }
    val completion = claim.takeCompletion()
    if (cancellationCleanup.asInstanceOf[AnyRef] ne null) {
      val op      = claim.operation.asInstanceOf[ReadOperation[Any]]
      val publish = cancellationCleanup.foldCause { cause =>
        finishCancellationFailure(op, cause)
        if (completion ne null) completion.fail(cause)
        ()
      } { _ =>
        finishCancellationFailure(op, failure)
        if (completion ne null) completion.fail(failure)
        ()
      }
      Async.startRegistered(publish)(_ => ())
    } else {
      if (notify ne null) safelyRun(notify)
    }
  }

  private def bindProgram(op: ReadOperation[_]): Unit = {
    val readIdx = StreamState.stageStart(state)
    op.reader = activeReader
    op.readTag = (incomingPrim(readIdx) & 0xff).toInt
    op.sourceType = incomingType(readIdx)
    op.collisionSafe = op.readTag == OpTag.READ_L || op.readTag == OpTag.READ_D
    op.programStart = readIdx + 1
    op.programLength = StreamState.incomingLen(state)
    op.programPrim = incomingPrim
    op.programRef = incomingRef
    op.resultType = logicalOutputType
    op.resultLane = outputLane
  }

  private def bindOperation(op: ReadOperation[_]): Unit = {
    bindProgram(op)
    op.state = Invoking
  }

  private def operationCurrent(op: ReadOperation[_], operationEpoch: Long): Boolean = synchronized {
    (active eq op) && epoch == operationEpoch && op.state != Cancelling
  }

  private def yieldRead(op: ReadOperation[_], operationEpoch: Long): Boolean = {
    var schedule       = false
    var forceMacrotask = false
    val yielded        = synchronized {
      val current = (active eq op) && epoch == operationEpoch && op.state != Cancelling
      if (current) {
        op.state = Yielding
        schedule = claimSchedule(op)
        if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
      }
      current
    }
    if (schedule) Async.schedule(op, forceMacrotask)
    yielded
  }

  private def clearProgram(op: ReadOperation[_]): Unit = {
    op.reader = null
    op.programPrim = null
    op.programRef = null
  }

  private def commitI(op: ReadOperation[_], operationEpoch: Long, value: Int): Boolean = synchronized {
    val current = (active eq op) && epoch == operationEpoch && op.state != Cancelling
    if (current) op.vi = value
    current
  }

  private def commitL(op: ReadOperation[_], operationEpoch: Long, value: Long): Boolean = synchronized {
    val current = (active eq op) && epoch == operationEpoch && op.state != Cancelling
    if (current) op.vl = value
    current
  }

  private def commitF(op: ReadOperation[_], operationEpoch: Long, value: Float): Boolean = synchronized {
    val current = (active eq op) && epoch == operationEpoch && op.state != Cancelling
    if (current) op.vf = value
    current
  }

  private def commitD(op: ReadOperation[_], operationEpoch: Long, value: Double): Boolean = synchronized {
    val current = (active eq op) && epoch == operationEpoch && op.state != Cancelling
    if (current) op.vd = value
    current
  }

  private def commitR(op: ReadOperation[_], operationEpoch: Long, value: AnyRef): Boolean = synchronized {
    val current = (active eq op) && epoch == operationEpoch && op.state != Cancelling
    if (current) op.vr = value
    current
  }

  private def scalarResult(op: ReadOperation[_]): Any = {
    val value: Any = op.resultLane match {
      case SyncInterpreter.LANE_I =>
        op.resultType match {
          case JvmType.Boolean => Boolean.box(op.vi != 0)
          case JvmType.Byte    => Byte.box(op.vi.toByte)
          case JvmType.Char    => Char.box(op.vi.toChar)
          case JvmType.Short   => Short.box(op.vi.toShort)
          case _               => Int.box(op.vi)
        }
      case SyncInterpreter.LANE_L => Long.box(op.vl)
      case SyncInterpreter.LANE_F => Float.box(op.vf)
      case SyncInterpreter.LANE_D => Double.box(op.vd)
      case _                      => op.vr
    }
    (op.kind: @scala.annotation.switch) match {
      case ReadRef     => value
      case ReadBoolean => if (value.asInstanceOf[Boolean]) 1 else 0
      case ReadByte    =>
        if (op.resultType eq JvmType.Boolean) if (op.vi != 0) 1 else 0
        else if (op.resultType eq JvmType.Char) value.asInstanceOf[Character].charValue().toInt & 0xff
        else value.asInstanceOf[java.lang.Number].intValue() & 0xff
      case ReadChar =>
        value match {
          case char: Character            => char.charValue().toInt
          case number: Number             => number.intValue()
          case boolean: java.lang.Boolean => if (boolean.booleanValue()) 1 else 0
        }
      case ReadShort              => value.asInstanceOf[java.lang.Number].intValue()
      case ReadInt | ReadLong     => value.asInstanceOf[java.lang.Number].longValue()
      case ReadFloat | ReadDouble => value.asInstanceOf[java.lang.Number].doubleValue()
    }
  }

  /**
   * A cancellation winner owns the in-flight result. A distinct replacement is
   * synchronously signalled and joined; a terminal or same-pending result
   * closes the replacement side of the handoff.
   */
  private def handoffCancelled(op: ReadOperation[_], polled: Pollable[Any]): Unit = {
    val cancellation = op.cancellation
    if (cancellation ne null) {
      val deferred = takeDeferredPushCleanup(op)
      if (deferred.asInstanceOf[AnyRef] ne null) {
        val lateCleanup =
          if (op.foldKind == FoldPending && ((polled eq null) || (op.foldPending ne polled)))
            Async.cancelWithCleanup(op.foldPending)
          else Async.succeed(())
        cancellation.claimedReplacement(combineCleanup(lateCleanup, deferred))
      } else if (op.foldKind == FoldPending && ((polled eq null) || (op.foldPending ne polled)))
        cancellation.replacement(op.foldPending)
      else cancellation.noReplacement()
    }
  }

  private def takeDeferredPushCleanup(op: ReadOperation[_]): Async[Unit] = synchronized {
    val cleanup = op.deferredPushCleanup
    op.deferredPushCleanup = null
    cleanup
  }

  private def handoffNoReplacement(op: ReadOperation[_]): Unit = {
    val cancellation = synchronized(op.cancellation)
    if (cancellation ne null) {
      val deferred = takeDeferredPushCleanup(op)
      if (deferred.asInstanceOf[AnyRef] eq null) cancellation.noReplacement()
      else cancellation.claimedReplacement(deferred)
    }
  }

  private def handoffClaimedCleanup(op: ReadOperation[_], cleanup: Async[Unit]): Unit = {
    val cancellation = synchronized(op.cancellation)
    if (cancellation ne null) {
      val deferred = takeDeferredPushCleanup(op)
      if (deferred.asInstanceOf[AnyRef] eq null) cancellation.claimedReplacement(cleanup)
      else cancellation.claimedReplacement(combineCleanup(cleanup, deferred))
    }
  }

  private def wake(op: ReadOperation[_], operationEpoch: Long): Unit = {
    var schedule       = false
    var forceMacrotask = false
    synchronized {
      if ((active eq op) && epoch == operationEpoch && op.state != Cancelling && !op.terminalSet) {
        op.wakePermit = true
        if (op.state == Pending) {
          schedule = claimSchedule(op)
          if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
        }
      }
    }
    if (schedule) Async.schedule(op, forceMacrotask)
  }

  private def claimSchedule(op: ReadOperation[_]): Boolean =
    if (op.scheduled) false
    else {
      op.scheduled = true
      true
    }

  private def claimCloseSchedule(claim: InnerCloseClaim): Boolean =
    if (claim.scheduled) false else { claim.scheduled = true; true }

  private def wakeInnerClose(claim: InnerCloseClaim): Unit = {
    var schedule       = false
    var forceMacrotask = false
    synchronized {
      val op = claim.operation
      if (
        (active eq op) && (op.innerClose eq claim) &&
        (op.state == InvokingInnerClose || op.state == PollingInnerClose)
      ) {
        claim.wakePermit = true
        if (op.state == InvokingInnerClose) {
          schedule = claimCloseSchedule(claim)
          if (schedule) forceMacrotask = op.nextYieldForcesMacrotask()
        }
      }
    }
    if (schedule) Async.schedule(claim, forceMacrotask)
  }

  private def resumeInnerClose(claim: InnerCloseClaim): Unit = {
    var leaf: Pollable[Any] = null
    synchronized {
      claim.scheduled = false
      val op = claim.operation
      if ((active ne op) || (op.innerClose ne claim) || op.state != InvokingInnerClose || !claim.wakePermit) return
      claim.wakePermit = false
      claim.pollInFlight = true
      claim.notifyObserver = true
      op.state = PollingInnerClose
      leaf = claim.pending
    }
    pollInnerClose(claim, leaf, ReadyBudget)
  }

  private def resume(op: ReadOperation[_]): Unit = {
    var leaf: Pollable[Any] = null
    var operationEpoch      = 0L
    var cooperativeYield    = false
    var asyncReadyYield     = false
    var errorMapInvokeYield = false
    synchronized {
      op.scheduled = false
      if ((active eq op) && !op.terminalSet && op.state == Yielding && epoch == op.operationEpoch) {
        op.state = Invoking
        operationEpoch = op.operationEpoch
        asyncReadyYield = op.yieldedAsyncReady
        op.yieldedAsyncReady = false
        errorMapInvokeYield = op.yieldedErrorMapInvoke
        op.yieldedErrorMapInvoke = false
        cooperativeYield = true
      } else {
        if (
          (active ne op) || op.terminalSet || op.state != Pending || !op.wakePermit ||
          epoch != op.operationEpoch
        ) return
        op.wakePermit = false
        op.state = Polling
        leaf = op.pending
        operationEpoch = op.operationEpoch
      }
    }
    if (cooperativeYield) {
      if (errorMapInvokeYield)
        consume(
          op.asInstanceOf[ReadOperation[Any]],
          operationEpoch,
          invokeAsyncErrorMap(op),
          null,
          notifyObserver = true,
          ReadyBudget
        )
      else if (asyncReadyYield)
        completeRead(
          op.asInstanceOf[ReadOperation[Any]],
          operationEpoch,
          op.asyncReadyValue,
          notifyObserver = true,
          ReadyBudget
        )
      else {
        val result =
          try invokeRead(op)
          catch { case cause: Throwable => Async.fail(cause) }
        consume(op.asInstanceOf[ReadOperation[Any]], operationEpoch, result, null, notifyObserver = true, ReadyBudget)
      }
    } else pollLeaf(op.asInstanceOf[ReadOperation[Any]], operationEpoch, leaf, notifyObserver = true, ReadyBudget)
    ()
  }

  private def cancel[A](op: ReadOperation[A]): Async[Unit] = {
    var cancellation: Async.Cancellation                                             = null
    var primary: Pollable[Any]                                                       = null
    var pollInFlight                                                                 = false
    var pushedReaders: scala.collection.mutable.ArrayBuffer[Reader.AsyncReader[Any]] = null
    var deferredPushedCleanup                                                        = false
    synchronized {
      if (op.cancellationEffect.asInstanceOf[AnyRef] ne null) return op.cancellationEffect
      if (op.terminalSet) return Async.succeed(())
      if (op.state == Fresh) {
        op.finishCancelled()
        return Async.succeed(())
      }
      if (active ne op) return Async.succeed(())
      if (
        (op.recovery ne null) &&
        (op.recovery.closeOwner ne null) &&
        op.recovery.phase == RecoveryClosing
      ) {
        val claim        = op.recovery
        val closePrimary = claim.trigger
        epoch += 1L
        op.state = Cancelling
        op.scheduled = false
        op.wakePermit = false
        val joined = claim.closeOwner.await.flatMap {
          case Left(cleanup)
              if (closePrimary eq null) || (cleanup eq null) || !StreamError.ignorableReplay(closePrimary, cleanup) =>
            val failure =
              if ((closePrimary eq null) || (cleanup eq null)) closePrimary
              else StreamError.attachCleanupReplay(closePrimary, cleanup)
            Async.fail(finishCancellationFailure(op, failure))
          case _ =>
            finishCancellationSuccess(op)
            Async.succeed(())
        }
        op.cancellationEffect = joined
        claim.closeOwner.poll(new Runnable { def run(): Unit = () })
        return op.cancellationEffect
      }
      if (op.innerClose ne null) {
        epoch += 1L
        op.closeCancellationRequested = true
        op.cancellationEffect = op.innerClose.awaitCompletion()
        return op.cancellationEffect
      }
      epoch += 1L
      op.state = Cancelling
      op.scheduled = false
      op.wakePermit = false
      cancellation = Async.cancellation()
      op.cancellation = cancellation
      pollInFlight = op.isBeingDriven
      primary = op.pending
      val currentLen = StreamState.incomingLen(state)
      if (currentLen > op.restartIncomingLen) {
        pushedReaders = new scala.collection.mutable.ArrayBuffer[Reader.AsyncReader[Any]]
        var i = op.restartIncomingLen
        while (i < currentLen) {
          if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
            val reader = incomingRef(i).asInstanceOf[Reader.AsyncReader[Any]]
            if (reader ne null) pushedReaders += reader
          }
          incomingPrim(i) = 0L
          incomingRef(i) = null
          incomingType(i) = null
          savedState(i) = StreamState.empty
          savedOutputType(i) = null
          i += 1
        }
        state = op.restartState
        logicalOutputType = op.restartType
        outputLane = StreamState.outputLane(state)
      }
      if (pollInFlight && (pushedReaders ne null) && pushedReaders.nonEmpty) {
        val pushedCleanup = closeReaders(pushedReaders)
        op.deferredPushCleanup =
          if (op.deferredPushCleanup.asInstanceOf[AnyRef] eq null) pushedCleanup
          else combineCleanup(op.deferredPushCleanup, pushedCleanup)
        deferredPushedCleanup = true
      }
      val observed = cancellation.effect.map { _ =>
        finishCancellationSuccess(op)
        ()
      }.catchAll { cause =>
        Async.fail(finishCancellationFailure(op, cause))
      }
      op.cancellationEffect = observed
    }
    val primaryCleanup =
      if (primary eq null) Async.succeed(())
      else Async.cancelWithCleanup(primary)
    if (deferredPushedCleanup) cancellation.primary(primaryCleanup)
    else {
      val pushedCleanup: Async[Unit] =
        if ((pushedReaders eq null) || pushedReaders.isEmpty) Async.succeed(())
        else closeReaders(pushedReaders)
      cancellation.primary(combineCleanup(primaryCleanup, pushedCleanup))
    }
    if (!pollInFlight) cancellation.noReplacement()
    op.cancellationEffect
  }

  private def finishCancellationSuccess[A](op: ReadOperation[A]): Unit = {
    var notify: Runnable = null
    synchronized {
      repairCancelledRecovery(op)
      if (active eq op) active = null
      op.pending = null
      clearProgram(op)
      op.finishCancelled()
      notify = op.observer
    }
    if (notify ne null) safelyRun(notify)
  }

  private def finishCancellationFailure[A](op: ReadOperation[A], failure: Throwable): Throwable = {
    var notify: Runnable      = null
    var normalized: Throwable = null
    synchronized {
      repairCancelledRecovery(op)
      normalized =
        if (
          op.awaitingAsyncMap || op.awaitingAsyncFilter || op.awaitingAsyncCollect || op.awaitingAsyncTap ||
          op.awaitingAsyncDistinctKey || op.awaitingAsyncAccum || op.awaitingAsyncTakeWhile ||
          op.awaitingAsyncErrorMap
        )
          StreamError.callbackFailure(failure)
        else failure
      if (active eq op) active = null
      op.pending = null
      clearProgram(op)
      transitionFailure = normalized
      transitionFailed = true
      transitionTrusted = false
      op.finishFailure(normalized, trusted = false)
      notify = op.observer
    }
    if (notify ne null) safelyRun(notify)
    normalized
  }

  private def combineCleanup(first: Async[Unit], second: Async[Unit]): Async[Unit] =
    first.either.flatMap { firstResult =>
      second.either.flatMap {
        case Right(_)            => firstResult.fold(Async.fail, _ => Async.succeed(()))
        case Left(secondFailure) =>
          firstResult match {
            case Right(_)           => Async.fail(secondFailure)
            case Left(firstFailure) =>
              if ((firstFailure eq null) || (secondFailure eq null)) Async.fail(firstFailure)
              else Async.fail(StreamError.attachCleanupReplay(firstFailure, secondFailure))
          }
      }
    }

  private def repairCancelledRecovery(op: ReadOperation[_]): Unit = {
    val claim = op.recovery
    if ((claim ne null) && (incomingRef(claim.readSlot) eq null)) {
      if (claim.readSlot == 0) {
        exhausted = true
        closed = true
      } else {
        incomingPrim(claim.readSlot) = 0L
        incomingType(claim.readSlot) = null
        savedState(claim.readSlot) = StreamState.empty
        savedOutputType(claim.readSlot) = null
        state = claim.outerState
        logicalOutputType = claim.outerType
        outputLane = StreamState.outputLane(state)
      }
      claim.phase = RecoveryCancelled
      op.recovery = null
    }
  }

  /**
   * A terminal fold owns accumulator representation while `ReadOperation`
   * continues to own scheduling, cancellation, and callback lifecycle. This
   * keeps one interpreter terminal path for every source and operation shape
   * without boxing primitive accumulators between ready steps.
   */
  private sealed trait TerminalFold {
    def invoke(op: ReadOperation[_]): Async[Any]
    def observe(result: Async[Any], op: ReadOperation[_]): Unit
    def commit(op: ReadOperation[_]): Unit
    def result: Any
  }

  private object TerminalFold {
    def make[A, Z](zero: Z, accumulatorType: JvmType, step: (Z, A) => Async[Z]): TerminalFold =
      if (accumulatorType eq JvmType.Int)
        new IntTerminalFold(zero.asInstanceOf[Int], step.asInstanceOf[AnyRef])
      else if (accumulatorType eq JvmType.Long)
        new LongTerminalFold(zero.asInstanceOf[Long], step.asInstanceOf[AnyRef])
      else if (accumulatorType eq JvmType.Float)
        new FloatTerminalFold(zero.asInstanceOf[Float], step.asInstanceOf[AnyRef])
      else if (accumulatorType eq JvmType.Double)
        new DoubleTerminalFold(zero.asInstanceOf[Double], step.asInstanceOf[AnyRef])
      else new RefTerminalFold(zero.asInstanceOf[Any], step.asInstanceOf[(Any, Any) => Async[Any]])

    private def input(op: ReadOperation[_]): Any = op.resultLane match {
      case SyncInterpreter.LANE_I =>
        op.resultType match {
          case JvmType.Boolean => op.vi != 0
          case JvmType.Byte    => op.vi.toByte
          case JvmType.Char    => op.vi.toChar
          case JvmType.Short   => op.vi.toShort
          case _               => op.vi
        }
      case SyncInterpreter.LANE_L => op.vl
      case SyncInterpreter.LANE_F => op.vf
      case SyncInterpreter.LANE_D => op.vd
      case _                      => op.vr
    }

    private final class IntTerminalFold(private var acc: Int, step: AnyRef) extends TerminalFold {
      def invoke(op: ReadOperation[_]): Async[Any] = {
        val effect = op.resultLane match {
          case SyncInterpreter.LANE_I =>
            op.resultType match {
              case JvmType.Boolean => step.asInstanceOf[(Int, Boolean) => Async[Int]](acc, op.vi != 0)
              case JvmType.Byte    => step.asInstanceOf[(Int, Byte) => Async[Int]](acc, op.vi.toByte)
              case JvmType.Char    => step.asInstanceOf[(Int, Char) => Async[Int]](acc, op.vi.toChar)
              case JvmType.Short   => step.asInstanceOf[(Int, Short) => Async[Int]](acc, op.vi.toShort)
              case _               => step.asInstanceOf[(Int, Int) => Async[Int]](acc, op.vi)
            }
          case SyncInterpreter.LANE_L => step.asInstanceOf[(Int, Long) => Async[Int]](acc, op.vl)
          case SyncInterpreter.LANE_F => step.asInstanceOf[(Int, Float) => Async[Int]](acc, op.vf)
          case SyncInterpreter.LANE_D => step.asInstanceOf[(Int, Double) => Async[Int]](acc, op.vd)
          case _                      => step.asInstanceOf[(Int, AnyRef) => Async[Int]](acc, op.vr)
        }
        effect.asInstanceOf[Async[Any]]
      }
      def observe(result: Async[Any], op: ReadOperation[_]): Unit =
        Async.foldIntStep(result.asInstanceOf[Async[Int]])(op)
      def commit(op: ReadOperation[_]): Unit = acc = op.foldIntValue
      def result: Any                        = acc
    }

    private final class LongTerminalFold(private var acc: Long, step: AnyRef) extends TerminalFold {
      def invoke(op: ReadOperation[_]): Async[Any] = {
        val effect = op.resultLane match {
          case SyncInterpreter.LANE_I =>
            op.resultType match {
              case JvmType.Boolean => step.asInstanceOf[(Long, Boolean) => Async[Long]](acc, op.vi != 0)
              case JvmType.Byte    => step.asInstanceOf[(Long, Byte) => Async[Long]](acc, op.vi.toByte)
              case JvmType.Char    => step.asInstanceOf[(Long, Char) => Async[Long]](acc, op.vi.toChar)
              case JvmType.Short   => step.asInstanceOf[(Long, Short) => Async[Long]](acc, op.vi.toShort)
              case _               => step.asInstanceOf[(Long, Int) => Async[Long]](acc, op.vi)
            }
          case SyncInterpreter.LANE_L => step.asInstanceOf[(Long, Long) => Async[Long]](acc, op.vl)
          case SyncInterpreter.LANE_F => step.asInstanceOf[(Long, Float) => Async[Long]](acc, op.vf)
          case SyncInterpreter.LANE_D => step.asInstanceOf[(Long, Double) => Async[Long]](acc, op.vd)
          case _                      => step.asInstanceOf[(Long, AnyRef) => Async[Long]](acc, op.vr)
        }
        effect.asInstanceOf[Async[Any]]
      }
      def observe(result: Async[Any], op: ReadOperation[_]): Unit =
        Async.foldLongStep(result.asInstanceOf[Async[Long]])(op)
      def commit(op: ReadOperation[_]): Unit = acc = op.foldLongValue
      def result: Any                        = acc
    }

    private final class FloatTerminalFold(private var acc: Float, step: AnyRef) extends TerminalFold {
      def invoke(op: ReadOperation[_]): Async[Any] = {
        val effect = op.resultLane match {
          case SyncInterpreter.LANE_I =>
            op.resultType match {
              case JvmType.Boolean => step.asInstanceOf[(Float, Boolean) => Async[Float]](acc, op.vi != 0)
              case JvmType.Byte    => step.asInstanceOf[(Float, Byte) => Async[Float]](acc, op.vi.toByte)
              case JvmType.Char    => step.asInstanceOf[(Float, Char) => Async[Float]](acc, op.vi.toChar)
              case JvmType.Short   => step.asInstanceOf[(Float, Short) => Async[Float]](acc, op.vi.toShort)
              case _               => step.asInstanceOf[(Float, Int) => Async[Float]](acc, op.vi)
            }
          case SyncInterpreter.LANE_L => step.asInstanceOf[(Float, Long) => Async[Float]](acc, op.vl)
          case SyncInterpreter.LANE_F => step.asInstanceOf[(Float, Float) => Async[Float]](acc, op.vf)
          case SyncInterpreter.LANE_D => step.asInstanceOf[(Float, Double) => Async[Float]](acc, op.vd)
          case _                      => step.asInstanceOf[(Float, AnyRef) => Async[Float]](acc, op.vr)
        }
        effect.asInstanceOf[Async[Any]]
      }
      def observe(result: Async[Any], op: ReadOperation[_]): Unit =
        Async.foldFloatStep(result.asInstanceOf[Async[Float]])(op)
      def commit(op: ReadOperation[_]): Unit = acc = op.foldFloatValue
      def result: Any                        = acc
    }

    private final class DoubleTerminalFold(private var acc: Double, step: AnyRef) extends TerminalFold {
      def invoke(op: ReadOperation[_]): Async[Any] = {
        val effect = op.resultLane match {
          case SyncInterpreter.LANE_I =>
            op.resultType match {
              case JvmType.Boolean => step.asInstanceOf[(Double, Boolean) => Async[Double]](acc, op.vi != 0)
              case JvmType.Byte    => step.asInstanceOf[(Double, Byte) => Async[Double]](acc, op.vi.toByte)
              case JvmType.Char    => step.asInstanceOf[(Double, Char) => Async[Double]](acc, op.vi.toChar)
              case JvmType.Short   => step.asInstanceOf[(Double, Short) => Async[Double]](acc, op.vi.toShort)
              case _               => step.asInstanceOf[(Double, Int) => Async[Double]](acc, op.vi)
            }
          case SyncInterpreter.LANE_L => step.asInstanceOf[(Double, Long) => Async[Double]](acc, op.vl)
          case SyncInterpreter.LANE_F => step.asInstanceOf[(Double, Float) => Async[Double]](acc, op.vf)
          case SyncInterpreter.LANE_D => step.asInstanceOf[(Double, Double) => Async[Double]](acc, op.vd)
          case _                      => step.asInstanceOf[(Double, AnyRef) => Async[Double]](acc, op.vr)
        }
        effect.asInstanceOf[Async[Any]]
      }
      def observe(result: Async[Any], op: ReadOperation[_]): Unit =
        Async.foldDoubleStep(result.asInstanceOf[Async[Double]])(op)
      def commit(op: ReadOperation[_]): Unit = acc = op.foldDoubleValue
      def result: Any                        = acc
    }

    private final class RefTerminalFold(private var acc: Any, step: (Any, Any) => Async[Any]) extends TerminalFold {
      def invoke(op: ReadOperation[_]): Async[Any]                = step(acc, input(op))
      def observe(result: Async[Any], op: ReadOperation[_]): Unit = Async.foldStep(result)(op)
      def commit(op: ReadOperation[_]): Unit                      = acc = op.foldValue
      def result: Any                                             = acc
    }
  }

  private final class ReadOperation[A](
    val generation: Long,
    val kind: Int,
    val sentinel: A,
    val controlOwner: ControlOperation[_],
    val terminalFold: TerminalFold
  ) extends Async.Operation[A]
      with Async.StepFold[Any, Unit]
      with Async.IntStepFold[Unit]
      with Async.LongStepFold[Unit]
      with Async.FloatStepFold[Unit]
      with Async.DoubleStepFold[Unit]
      with Runnable {
    var state: Int                         = Fresh
    var operationEpoch: Long               = 0L
    var observer: Runnable                 = null
    var pending: Pollable[Any]             = null
    var wakePermit                         = false
    var scheduled                          = false
    var cancellation: Async.Cancellation   = null
    var cancellationEffect: Async[Unit]    = null
    var innerClose: InnerCloseClaim        = null
    var recovery: RecoveryClaim            = null
    var closeCancellationRequested         = false
    var reader: Reader.AsyncReader[Any]    = null
    var readTag: Int                       = 0
    var collisionSafe                      = false
    val longScratch                        = new Array[Long](1)
    val doubleScratch                      = new Array[Double](1)
    var sourceType: JvmType                = JvmType.AnyRef
    var restartState: StreamState          = StreamState.empty
    var restartType: JvmType               = JvmType.AnyRef
    var restartIncomingLen                 = 0
    var deferredPushCleanup: Async[Unit]   = null
    var programStart: Int                  = 0
    var programLength: Int                 = 0
    var programPrim: Array[Long]           = null
    var programRef: Array[AnyRef]          = null
    var resultType: JvmType                = JvmType.AnyRef
    var resultLane: Lane                   = SyncInterpreter.LANE_R
    var awaitingAsyncMap                   = false
    var awaitingAsyncFilter                = false
    var awaitingAsyncCollect               = false
    var awaitingAsyncTap                   = false
    var awaitingAsyncDistinctKey           = false
    var awaitingAsyncAccum                 = false
    var awaitingAsyncTakeWhile             = false
    var awaitingTerminalFold               = false
    var awaitingAsyncErrorMap              = false
    var asyncErrorMapIndex                 = 0
    var asyncErrorMapOriginal: StreamError = null
    var awaitingScanInitial                = false
    var asyncDistinctKey: AsyncDistinctKey = null
    var asyncAccum: AsyncAccum             = null
    var asyncAccumOld: Any                 = null
    var asyncAccumTag                      = 0
    var asyncAccumType: JvmType            = JvmType.AnyRef
    var asyncMapTag                        = 0
    var asyncMapType: JvmType              = JvmType.AnyRef
    var asyncCollectTag                    = 0
    var asyncCollectType: JvmType          = JvmType.AnyRef
    var resumeIncoming                     = 0
    var resumeOutgoing                     = 0
    var yieldedAsyncReady                  = false
    var yieldedErrorMapInvoke              = false
    var asyncReadyValue: Any               = null
    var vi: Int                            = 0
    var vl: Long                           = 0L
    var vf: Float                          = 0f
    var vd: Double                         = 0.0
    var vr: AnyRef                         = null
    var directIntRead: Long                = 0L

    def inCallback: Boolean =
      awaitingAsyncMap || awaitingAsyncFilter || awaitingAsyncCollect || awaitingAsyncTap ||
        awaitingAsyncDistinctKey || awaitingAsyncAccum || awaitingAsyncTakeWhile ||
        awaitingAsyncErrorMap || awaitingTerminalFold || ((recovery ne null) && recovery.phase == RecoveryHandling)

    var foldKind: Int              = FoldSuccess
    var foldValue: Any             = null
    var foldFailure: Throwable     = null
    var foldTrusted                = false
    var foldPending: Pollable[Any] = null
    var foldIntValue: Int          = 0
    var foldLongValue: Long        = 0L
    var foldFloatValue: Float      = 0f
    var foldDoubleValue: Double    = 0.0

    var terminalSet                                                                   = false
    private var terminalValue: Async[A]                                               = null
    private var yieldCount                                                            = 0
    @volatile var reachedEof                                                          = false
    private var additionalDriverThreads: scala.collection.mutable.ArrayBuffer[Thread] = null
    private var driverThread: Thread                                                  = null

    val waker: Runnable = new Runnable {
      def run(): Unit = AsyncInterpreter.this.wake(ReadOperation.this, operationEpoch)
    }

    def poll(onComplete: Runnable): Async[A] = {
      beginDriving()
      try AsyncInterpreter.this.poll(this, onComplete)
      finally if (finishDriving()) handoffNoReplacement(this)
    }
    def run(): Unit = {
      beginDriving()
      try AsyncInterpreter.this.resume(this)
      finally if (finishDriving()) handoffNoReplacement(this)
    }
    protected def cancelOperation(): Async[Unit] = AsyncInterpreter.this.cancel(this)
    def isBeingDriven: Boolean                   = AsyncInterpreter.this.synchronized(driverThread ne null)
    def isDriverThread: Boolean                  = AsyncInterpreter.this.synchronized {
      val current = Thread.currentThread()
      (driverThread eq current) ||
      ((additionalDriverThreads ne null) && additionalDriverThreads.exists(_ eq current))
    }

    def success(value: Any): Unit = {
      foldKind = FoldSuccess
      foldValue = value
    }
    def failure(cause: Throwable): Unit = {
      foldKind = FoldFailure
      foldFailure = cause
      foldTrusted = false
    }
    override def trustedFailure(cause: Throwable): Unit = {
      foldKind = FoldFailure
      foldFailure = cause
      foldTrusted = true
    }
    def pending(value: Pollable[Any]): Unit = {
      foldKind = FoldPending
      foldPending = value
    }
    def successLong(value: Long): Unit = {
      foldKind = FoldSuccess
      foldLongValue = value
    }
    def failureLong(cause: Throwable): Unit                 = failure(cause)
    override def trustedFailureLong(cause: Throwable): Unit = trustedFailure(cause)
    def pendingLong(value: Pollable[Long]): Unit            = pending(value.asInstanceOf[Pollable[Any]])

    def successInt(value: Int): Unit = {
      foldKind = FoldSuccess
      foldIntValue = value
    }
    def failureInt(cause: Throwable): Unit                 = failure(cause)
    override def trustedFailureInt(cause: Throwable): Unit = trustedFailure(cause)
    def pendingInt(value: Pollable[Int]): Unit             = pending(value.asInstanceOf[Pollable[Any]])

    def successFloat(value: Float): Unit = {
      foldKind = FoldSuccess
      foldFloatValue = value
    }
    def failureFloat(cause: Throwable): Unit                 = failure(cause)
    override def trustedFailureFloat(cause: Throwable): Unit = trustedFailure(cause)
    def pendingFloat(value: Pollable[Float]): Unit           = pending(value.asInstanceOf[Pollable[Any]])

    def successDouble(value: Double): Unit = {
      foldKind = FoldSuccess
      foldDoubleValue = value
    }
    def failureDouble(cause: Throwable): Unit                 = failure(cause)
    override def trustedFailureDouble(cause: Throwable): Unit = trustedFailure(cause)
    def pendingDouble(value: Pollable[Double]): Unit          = pending(value.asInstanceOf[Pollable[Any]])

    def terminal: Async[A]     = terminalValue
    def resultOrSelf: Async[A] = AsyncInterpreter.this.synchronized {
      if (terminalSet) terminalValue else this
    }
    def terminalResult: A             = if (terminalFold eq null) sentinel else terminalFold.result.asInstanceOf[A]
    def finishSuccess(value: A): Unit = {
      state = Done
      terminalValue = Async.succeed(value)
      terminalSet = true
    }
    def finishFailure(cause: Throwable, trusted: Boolean): Unit = {
      state = Done
      terminalValue =
        if (trusted) Async.failTrusted(cause).asInstanceOf[Async[A]]
        else Async.fail(cause)
      terminalSet = true
    }
    def finishCancelled(): Unit             = finishSuccess(terminalResult)
    def nextYieldForcesMacrotask(): Boolean = {
      yieldCount += 1
      yieldCount % MicrotaskYieldsBeforeMacrotask == 0
    }

    private def beginDriving(): Unit = AsyncInterpreter.this.synchronized {
      val current = Thread.currentThread()
      if (driverThread eq null) driverThread = current
      else {
        if (additionalDriverThreads eq null)
          additionalDriverThreads = new scala.collection.mutable.ArrayBuffer[Thread](1)
        additionalDriverThreads += current
      }
    }

    private def finishDriving(): Boolean = AsyncInterpreter.this.synchronized {
      val current = Thread.currentThread()
      if (driverThread eq current) {
        if ((additionalDriverThreads eq null) || additionalDriverThreads.isEmpty) driverThread = null
        else {
          driverThread = additionalDriverThreads.remove(additionalDriverThreads.length - 1)
          if (additionalDriverThreads.isEmpty) additionalDriverThreads = null
        }
      } else if (additionalDriverThreads ne null) {
        val index = additionalDriverThreads.lastIndexWhere(_ eq current)
        if (index >= 0) additionalDriverThreads.remove(index)
        if (additionalDriverThreads.isEmpty) additionalDriverThreads = null
      }
      (driverThread eq null) && (cancellation ne null) && state == Cancelling
    }
  }

  private final class AsyncRecovery(
    val index: Int,
    val expectedType: JvmType,
    val allowReferenceRecovery: Boolean,
    val typed: Boolean,
    val defects: PartialFunction[Throwable, Async[Option[Stream[_, Any]]]],
    val typedHandler: Any => Async[Stream[_, Any]]
  ) {
    var switched = false
  }

  private final class RecoveryCandidate(val boundary: AsyncRecovery, val trigger: Throwable, val trusted: Boolean)

  private final class RecoveryClaim(
    val boundary: AsyncRecovery,
    val failed: Reader.AsyncReader[Any],
    val readSlot: Int,
    val expectedType: JvmType,
    val outerState: StreamState,
    val outerType: JvmType,
    val boundaryIndex: Int,
    val trigger: Throwable,
    val trusted: Boolean,
    val operation: ReadOperation[_]
  ) {
    var phase                          = RecoveryClosing
    var closeOwner: RecoveryOwnerClose = if (failed eq null) null else new RecoveryOwnerClose(failed)
  }

  /**
   * A detached reader close is ownership cleanup: cancellation joins it but
   * never cancels it.
   */
  private final class RecoveryOwnerClose(reader: Reader.AsyncReader[Any])
      extends Async.Operation[Either[Throwable, Unit]] {
    private val done    = new Completer[Either[Throwable, Unit]]
    private var started = false

    def await: Async[Either[Throwable, Unit]] = done.peek

    def poll(onComplete: Runnable): Async[Either[Throwable, Unit]] = {
      val leader = synchronized {
        if (started) false
        else { started = true; true }
      }
      if (leader) {
        val close =
          try reader.close()
          catch { case cause: Throwable => Async.fail(cause) }
        val running = Async.startRegistered(close)(_ => ())
        val publish = running.foldCause { cause => done.succeed(Left(cause)); () } { _ => done.succeed(Right(())); () }
        Async.startRegistered(publish)(_ => ())
      }
      val result = done.poll(onComplete)
      if (result.isInstanceOf[Pollable[_]]) this else result
    }

    protected def cancelOperation(): Async[Unit] = {
      poll(new Runnable { def run(): Unit = () })
      done.peek.flatMap {
        case Right(_)    => Async.succeed(())
        case Left(cause) => Async.fail(cause)
      }
    }
  }

  private[streams] def recoveryOwnerCloseForTest(
    reader: Reader.AsyncReader[Any]
  ): Async[Either[Throwable, Unit]] = new RecoveryOwnerClose(reader)

  private var beforeControlRegistrationHook: () => Unit = null

  private[streams] def beforeControlRegistrationForTest(hook: () => Unit): Unit = synchronized {
    beforeControlRegistrationHook = hook
  }

  private[streams] def clearIncomingReaderForTest(index: Int): Unit = synchronized {
    incomingRef(index) = null
  }

  private[streams] def wakeReadersCloseForTest(close: Async[Unit]): Unit = close match {
    case readers: ReadersClose @unchecked => readers.wakeWithoutSchedulingForTest()
    case _                                => ()
  }

  private def runBeforeControlRegistrationHook(): Unit = {
    val hook = synchronized {
      val value = beforeControlRegistrationHook
      beforeControlRegistrationHook = null
      value
    }
    if (hook ne null) hook()
  }

  private final class InnerCloseClaim() extends Async.StepFold[Any, Unit] with Runnable {
    var reader: Reader.AsyncReader[Any]                 = null
    var readSlot: Int                                   = 0
    var outerState: StreamState                         = StreamState.empty
    var outerType: JvmType                              = null
    var operation: ReadOperation[_]                     = null
    var notifyObserver: Boolean                         = false
    private var completion: Completer[Unit]             = null
    var pending: Pollable[Any]                          = null
    var pollInFlight                                    = false
    var wakePermit                                      = false
    var scheduled                                       = false
    var foldKind                                        = FoldSuccess
    var foldFailure: Throwable                          = null
    var foldPending: Pollable[Any]                      = null
    val waker: Runnable                                 = new Runnable { def run(): Unit = wakeInnerClose(InnerCloseClaim.this) }
    def run(): Unit                                     = resumeInnerClose(this)
    def success(value: Any): Unit                       = foldKind = FoldSuccess
    def failure(cause: Throwable): Unit                 = { foldKind = FoldFailure; foldFailure = cause }
    override def trustedFailure(cause: Throwable): Unit = failure(cause)
    def pending(value: Pollable[Any]): Unit             = { foldKind = FoldPending; foldPending = value }

    def awaitCompletion(): Async[Unit] = synchronized {
      if (completion eq null) completion = new Completer[Unit]
      completion.peek
    }

    def reset(
      nextReader: Reader.AsyncReader[Any],
      nextReadSlot: Int,
      nextOuterState: StreamState,
      nextOuterType: JvmType,
      nextOperation: ReadOperation[_],
      nextNotifyObserver: Boolean
    ): Unit = {
      reader = nextReader
      readSlot = nextReadSlot
      outerState = nextOuterState
      outerType = nextOuterType
      operation = nextOperation
      notifyObserver = nextNotifyObserver
      pending = null
      pollInFlight = false
      wakePermit = false
      scheduled = false
      foldKind = FoldSuccess
      foldFailure = null
      foldPending = null
    }

    def takeCompletion(): Completer[Unit] = synchronized {
      val result = completion
      completion = null
      result
    }
  }
}

private[streams] object AsyncInterpreter {
  private val DirectIntRead: Async[Any] = new AnyRef().asInstanceOf[Async[Any]]
  private val InlineScalarPush: AnyRef  = new AnyRef

  private[streams] trait TerminalDriver {
    def foldAsync[A, Z](zero: Z, accumulatorType: JvmType, step: (Z, A) => Async[Z]): Async[Z]
  }

  private[streams] trait FusibleReader {
    def fuseAsyncMap[A, B](inType: JvmType, outType: JvmType, f: A => Async[B]): Boolean
    def fuseFilter[A](inType: JvmType, f: A => Boolean): Boolean
    def fuseMap[A, B](inType: JvmType, outType: JvmType, f: A => B): Boolean
  }

  private def adaptAsyncAccumInput[S, A, B](inType: JvmType, f: (S, A) => Async[(S, B)]): AnyRef =
    if (inType eq JvmType.Boolean)
      ((state: Any, value: Int) => f(state.asInstanceOf[S], (value != 0).asInstanceOf[A])).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Byte)
      ((state: Any, value: Int) => f(state.asInstanceOf[S], value.toByte.asInstanceOf[A])).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Char)
      ((state: Any, value: Int) => f(state.asInstanceOf[S], value.toChar.asInstanceOf[A])).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Short)
      ((state: Any, value: Int) => f(state.asInstanceOf[S], value.toShort.asInstanceOf[A])).asInstanceOf[AnyRef]
    else
      SyncInterpreter.laneOf(inType) match {
        case 0 => ((state: Any, value: Int) => f(state.asInstanceOf[S], value.asInstanceOf[A])).asInstanceOf[AnyRef]
        case 1 => ((state: Any, value: Long) => f(state.asInstanceOf[S], value.asInstanceOf[A])).asInstanceOf[AnyRef]
        case 2 => ((state: Any, value: Float) => f(state.asInstanceOf[S], value.asInstanceOf[A])).asInstanceOf[AnyRef]
        case 3 => ((state: Any, value: Double) => f(state.asInstanceOf[S], value.asInstanceOf[A])).asInstanceOf[AnyRef]
        case _ => ((state: Any, value: AnyRef) => f(state.asInstanceOf[S], value.asInstanceOf[A])).asInstanceOf[AnyRef]
      }

  private[streams] def fuseAsyncMap[A, B](
    source: Reader.AsyncReader[_],
    inType: JvmType,
    outType: JvmType,
    f: A => Async[B]
  ): Reader.AsyncReader[B] = source match {
    case reader: FusibleReader if reader.fuseAsyncMap[A, B](inType, outType, f) =>
      source.asInstanceOf[Reader.AsyncReader[B]]
    case _ => null
  }

  private[streams] def fuseFilter[A](
    source: Reader.AsyncReader[_],
    inType: JvmType,
    f: A => Boolean
  ): Reader.AsyncReader[A] = source match {
    case reader: FusibleReader if reader.fuseFilter[A](inType, f) =>
      source.asInstanceOf[Reader.AsyncReader[A]]
    case _ => null
  }

  private[streams] def fuseMap[A, B](
    source: Reader.AsyncReader[_],
    inType: JvmType,
    outType: JvmType,
    f: A => B
  ): Reader.AsyncReader[B] = source match {
    case reader: FusibleReader if reader.fuseMap[A, B](inType, outType, f) =>
      source.asInstanceOf[Reader.AsyncReader[B]]
    case _ => null
  }

  final val ConcurrentOperationMessage = SyncInterpreter.ConcurrentOperationMessage
  private final val QueryClosed        = 0
  private final val QueryReadable      = 1
  private final val MutatingMapsOnly   = 2
  private final val ResetControl       = 3
  private final val SkipControl        = 4
  private final val BulkControl        = 5

  private[streams] def rejectedMaterializationReaderForTest(
    primary: Throwable,
    cleanup: Async[Unit]
  ): Reader.AsyncReader[Any] =
    new RejectedMaterializationReader(new DetachedFailure(primary, cleanup))

  private[streams] def bulkYieldForTest(forceMacrotask: Boolean): (Pollable[Unit], Runnable) = {
    val interpreter = new AsyncInterpreter(null)
    val operation   = new interpreter.BulkYield(forceMacrotask)
    (operation, operation)
  }

  /**
   * Builds an interpreter around an already-materialized asynchronous reader.
   */
  private[streams] def transform[A](source: Reader.AsyncReader[A])(
    configure: AsyncInterpreter => Unit
  ): AsyncInterpreter = {
    val interpreter     = new AsyncInterpreter(null)
    var sourceInstalled = false
    try {
      interpreter.appendRead(source.asInstanceOf[Reader.AsyncReader[Any]], source.jvmType)
      sourceInstalled = true
      configure(interpreter)
      var i = interpreter.deferred.length - 1
      while (i >= 0) { interpreter.deferred(i)(); i -= 1 }
      interpreter.deferred.clear()
      interpreter.seal()
      interpreter
    } catch {
      case primary: Throwable =>
        val cleanup = launderCallbackEffect(
          Async.reschedule(() => if (sourceInstalled) interpreter.closeOwned() else source.close())
        )
        new AsyncInterpreter(new RejectedMaterializationReader(new DetachedFailure(primary, cleanup)))
    }
  }

  /**
   * Builds an interpreter around a synchronous reader without the allocating
   * general-purpose async adapter.
   */
  private[streams] def transformSync[A](source: Reader.SyncReader[A])(
    configure: AsyncInterpreter => Unit
  ): AsyncInterpreter = {
    val interpreter     = new AsyncInterpreter(null)
    var sourceInstalled = false
    try {
      interpreter.appendRead(
        new interpreter.InterpreterSyncReader(source.asInstanceOf[Reader.SyncReader[Any]]),
        source.jvmType
      )
      sourceInstalled = true
      configure(interpreter)
      var i = interpreter.deferred.length - 1
      while (i >= 0) { interpreter.deferred(i)(); i -= 1 }
      interpreter.deferred.clear()
      interpreter.seal()
      interpreter
    } catch {
      case primary: Throwable =>
        val cleanup = launderCallbackEffect(
          Async.reschedule(() =>
            if (sourceInstalled) interpreter.closeOwned()
            else
              try { source.close(); Async.succeed(()) }
              catch { case cause: Throwable => Async.fail(cause) }
          )
        )
        new AsyncInterpreter(new RejectedMaterializationReader(new DetachedFailure(primary, cleanup)))
    }
  }

  private def materializeDetached(
    stream: Stream[_, _],
    expectedRecoveryType: JvmType = null,
    allowReferenceRecovery: Boolean = false
  ): Either[DetachedFailure, AsyncInterpreter] = {
    val interpreter = new AsyncInterpreter(null)
    try {
      var current: Stream[_, _] = stream
      while (current ne null) {
        current match {
          case _: Stream.MaterializationBoundary =>
            interpreter.deferOwnedReaderRoot(Stream.compileToReader(current))
            current = null
          case _ if interpreter.deferred.length >= MaxDeferredOperators =>
            interpreter.deferOwnedReaderRoot(Stream.compileToReader(current))
            current = null
          case _ => current = current.materializeAsync(interpreter)
        }
      }
      var i = interpreter.deferred.length - 1
      while (i >= 0) { interpreter.deferred(i)(); i -= 1 }
      if (expectedRecoveryType ne null)
        interpreter.normalizeRecoveryOutput(expectedRecoveryType, allowReferenceRecovery)
      interpreter.seal()
      interpreter.deferred.clear()
      interpreter.acquired.clear()
      Right(interpreter)
    } catch {
      case primary: Throwable =>
        var cleanup: Async[Unit] = Async.succeed(())
        var i                    = interpreter.acquired.length - 1
        while (i >= 0) {
          val owner = interpreter.acquired(i)
          val next  = owner match {
            case sync: Reader.SyncReader[_] =>
              try { sync.close(); Async.succeed(()) }
              catch { case cause: Throwable => Async.fail(cause) }
            case async: Reader.AsyncReader[_] => Async.reschedule(() => async.close())
          }
          cleanup = joinCleanup(cleanup, launderCallbackEffect(next))
          i -= 1
        }
        interpreter.incomingPrim = new Array[Long](8)
        interpreter.incomingRef = new Array[AnyRef](8)
        interpreter.incomingType = new Array[JvmType](8)
        interpreter.savedState = new Array[StreamState](8)
        interpreter.savedOutputType = new Array[JvmType](8)
        interpreter.outgoingPrim = new Array[Long](4)
        interpreter.outgoingRef = new Array[AnyRef](4)
        interpreter.outgoingType = new Array[JvmType](4)
        interpreter.state = StreamState.empty
        interpreter.logicalOutputType = JvmType.AnyRef
        interpreter.outputLane = SyncInterpreter.LANE_R
        interpreter.afterPush = false
        interpreter.sealedState = StreamState.empty
        Left(new DetachedFailure(primary, cleanup))
    }
  }

  private[streams] def fromStream(stream: Stream[_, _]): AsyncInterpreter =
    materializeDetached(stream) match {
      case Right(interpreter) => interpreter
      case Left(failure)      => new AsyncInterpreter(new RejectedMaterializationReader(failure))
    }

  private final val MaxDeferredOperators = 8192

  private def joinCleanup(first: Async[Unit], second: => Async[Unit]): Async[Unit] =
    first.either.flatMap {
      case Right(_)      => second
      case Left(primary) =>
        second.either.flatMap {
          case Right(_)        => Async.fail(primary)
          case Left(secondary) =>
            Async.fail(if (primary eq null) null else StreamError.attachCleanupReplay(primary, secondary))
        }
    }

  /**
   * Treats construction failures, failed effects, and defects thrown while
   * polling an untrusted callback effect as the same callback-failure channel.
   * The extra flatten boundary reifies a child poll throw before catchAll sees
   * it, while retaining cancellation ownership of that child.
   */
  private def launderCallbackEffect[A](effect: Async[A]): Async[A] =
    Async
      .deferCancelable(() => effect, () => ())
      .flatten
      .catchAll(cause => Async.fail(StreamError.callbackFailure(cause)))

  private def failAfterCleanup[A](primary: Throwable, cleanup: Async[Unit]): Async[A] =
    cleanup.either.flatMap {
      case Right(_)        => Async.fail(primary)
      case Left(secondary) =>
        Async.fail(if (primary eq null) null else StreamError.attachCleanupReplay(primary, secondary))
    }

  /** White-box construction path; deliberately not wired to public Stream. */
  private[streams] def fromStreamWithAsyncMapForTest[A, B](
    stream: Stream[_, A],
    inType: JvmType,
    outType: JvmType
  )(f: A => Async[B]): AsyncInterpreter = {
    val interpreter = fromStream(stream)
    interpreter.addAsyncMap(inType, outType)(f)
    interpreter.seal()
    interpreter
  }

  /** White-box construction path; deliberately not wired to public Stream. */
  private[streams] def fromStreamWithAsyncCollectForTest[A, B](
    stream: Stream[_, A],
    inType: JvmType,
    outType: JvmType
  )(f: A => Async[Option[B]]): AsyncInterpreter = {
    val interpreter = fromStream(stream)
    interpreter.addAsyncCollect(inType, outType)(f)
    interpreter.seal()
    interpreter
  }

  /** White-box construction path; deliberately not wired to public Stream. */
  private[streams] def fromStreamWithAsyncTapForTest[A](
    stream: Stream[_, A],
    inType: JvmType
  )(f: A => Async[Unit]): AsyncInterpreter = {
    val interpreter = fromStream(stream)
    interpreter.addAsyncTap(inType)(f)
    interpreter.seal()
    interpreter
  }

  /** White-box construction path; deliberately not wired to public Stream. */
  private[streams] def fromStreamWithAsyncTakeWhileForTest[A](
    stream: Stream[_, A],
    inType: JvmType
  )(f: A => Async[Boolean]): AsyncInterpreter = {
    val interpreter = fromStream(stream)
    interpreter.addAsyncTakeWhile(inType)(f)
    interpreter.seal()
    interpreter
  }

  /**
   * White-box construction path for asynchronous scan; deliberately not wired
   * to public Stream.
   */
  private[streams] def fromStreamWithAsyncScanForTest[S, A](
    stream: Stream[_, A],
    init: S,
    inType: JvmType,
    stateType: JvmType
  )(f: (S, A) => Async[S]): AsyncInterpreter = {
    val interpreter = fromStream(stream)
    interpreter.addAsyncScan(init, inType, stateType)(f)
    interpreter.seal()
    interpreter
  }

  /**
   * White-box reader construction path used to verify scan without public API
   * exposure.
   */
  private[streams] def fromReaderWithAsyncScanForTest[S, A](
    reader: Reader.AsyncReader[Any],
    init: S,
    inType: JvmType,
    stateType: JvmType
  )(f: (S, A) => Async[S]): AsyncInterpreter = {
    val interpreter = new AsyncInterpreter(reader)
    interpreter.addAsyncScan(init, inType, stateType)(f)
    interpreter.seal()
    interpreter
  }

  private final class DetachedFailure(val primary: Throwable, val cleanup: Async[Unit])

  private final class RejectedMaterializationReader(failure: DetachedFailure) extends Reader.AsyncReader[Any] {
    private var closed     = false
    private val closeOwner = new Reader.MemoizedClose(
      () => synchronized { closed = true },
      () => failure.cleanup
    )

    override def jvmType: JvmType             = JvmType.AnyRef
    def isClosed: Async[Boolean]              = Async.succeed(synchronized(closed))
    def readable(): Async[Boolean]            = Async.succeed(false)
    def close(): Async[Unit]                  = closeOwner.close()
    def read[A >: Any](sentinel: A): Async[A] =
      failAfterCleanup(failure.primary, closeOwner.close())
  }

  private final class AsyncDistinctKey(val fn: AnyRef) {
    var seen = scala.collection.immutable.HashSet.empty[Any]
  }

  private final class AsyncAccum(val fn: AnyRef, val initial: Any, @volatile var state: Any, var scan: Boolean) {
    var emitInitial = false
  }

  private final class ScanInitial(
    val accum: AsyncAccum,
    val tag: Int,
    val outType: JvmType,
    val value: Any,
    val resumeIncoming: Int,
    val resumeOutgoing: Int
  )

  private final val Fresh                 = 0
  private final val Invoking              = 1
  private final val Polling               = 2
  private final val Pending               = 3
  private final val Cancelling            = 4
  private final val Done                  = 5
  private final val Yielding              = 6
  private final val InvokingInnerClose    = 7
  private final val PollingInnerClose     = 8
  private final val MaterializingPush     = 9
  private final val PushInstalled         = 10
  private final val MaterializingRecovery = 11

  private final val RecoveryClosing       = 0
  private final val RecoveryHandling      = 1
  private final val RecoveryMaterializing = 2
  private final val RecoveryInstalled     = 3
  private final val RecoveryCancelled     = 4
  private final val RecoveryFailed        = 5

  private final val FoldSuccess = 0
  private final val FoldFailure = 1
  private final val FoldPending = 2

  private final val ReadRef     = 0
  private final val ReadBoolean = 1
  private final val ReadByte    = 2
  private final val ReadChar    = 3
  private final val ReadShort   = 4
  private final val ReadInt     = 5
  private final val ReadLong    = 6
  private final val ReadFloat   = 7
  private final val ReadDouble  = 8

  private final val ReadyBudget                    = 128
  private final val MicrotaskYieldsBeforeMacrotask = 16

  private def safelyRun(runnable: Runnable): Unit =
    try runnable.run()
    catch { case _: Throwable => () }
}
