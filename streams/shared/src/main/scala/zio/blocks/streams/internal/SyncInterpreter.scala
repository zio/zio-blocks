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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.{JvmType, Stream => ZStream}
import zio.blocks.streams.io.Reader
import StreamState.{stageStart => ss, incomingLen => il, stageEnd => se, outgoingLen => ol, outputLane => olane}

/**
 * An optimized interpreter that compiles a chain of stream operations (map,
 * filter, flatMap, source reads) into a flat-array representation for efficient
 * dispatch. This is the fallback compilation strategy for deep pipelines
 * (beyond `Stream.DepthCutoff`) and is used by `SyncInterpreter.fromStream`.
 *
 * Internally, each op is a (Long, AnyRef) pair stored at the same index in two
 * parallel arrays. The Long's bottom 8 bits hold the [[OpTag]]; the upper 56
 * bits hold [[StreamState]] for Read/Push ops.
 */
final class SyncInterpreter private[internal] () extends Reader.SyncReader[Any] {
  import SyncInterpreter._

  private var incomingPrim: Array[Long]             = new Array[Long](8)
  private var incomingRef: Array[AnyRef]            = new Array[AnyRef](8)
  private var incomingType: Array[JvmType]          = new Array[JvmType](8)
  private var savedState: Array[StreamState]        = new Array[StreamState](8)
  private var savedOutputType: Array[JvmType]       = new Array[JvmType](8)
  private var outgoingPrim: Array[Long]             = null
  private var outgoingRef: Array[AnyRef]            = null
  private[streams] var state: StreamState           = StreamState.empty
  private[streams] var outputLane: Lane             = 0
  private var logicalOutputType: JvmType            = JvmType.AnyRef
  private var stableOutputType: JvmType             = null
  private var closed: Boolean                       = false
  private var closeFailure: Throwable               = null
  private var transitionFailure: Throwable          = null
  private var pendingCleanupFailure: Throwable      = null
  private var initialState: StreamState             = StreamState.empty
  private var initialOutputType: JvmType            = JvmType.AnyRef
  private var exhausted: Boolean                    = false
  private var resetting: Boolean                    = false
  private var closeRequestedDuringReset: Boolean    = false
  private var activeOperations: Int                 = 0
  private var lifecycleEpoch: Long                  = 0L
  private var activeOperationEpoch: Long            = 0L
  private var activeOperationOwner: Long            = 0L
  private var operationSequence: Long               = 0L
  private var materializationEpoch: Long            = -1L
  private var enclosingInterpreter: SyncInterpreter = null
  private var closing: Boolean                      = false
  private var closePending: Boolean                 = false

  def close(): Unit = {
    if (resetting) {
      closeRequestedDuringReset = true
      return
    }
    if (closing) return
    if (!closed) {
      closed = true
      lifecycleEpoch += 1L
      closePending = true
      if (activeOperations == 0) runCloseTraversal()
    }
    if (closeFailure != null) throw closeFailure
  }

  private def runCloseTraversal(): Unit = {
    closePending = false
    closing = true
    activeOperations += 1
    try {
      var i                     = 0
      var firstError: Throwable = transitionFailure
      if (pendingCleanupFailure ne null) {
        firstError = StreamError.attachCleanupReplay(firstError, pendingCleanupFailure)
        pendingCleanupFailure = null
      }
      // Deliberately re-read incomingLen: a reentrant PUSH compilation may
      // have appended an owned reader before the active operation unwound.
      while (i < incomingLen) {
        if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
          val r = incomingRef(i)
          if (r != null) {
            try r.asInstanceOf[Reader.SyncReader[Any]].close()
            catch {
              case t: Throwable =>
                firstError =
                  if (firstError eq null) t
                  else StreamError.attachCleanupReplay(firstError, t)
            }
            if (i != 0) incomingRef(i) = null
          }
        }
        i += 1
      }
      closeFailure = firstError
    } finally {
      activeOperations -= 1
      closing = false
    }
  }

  def isClosed: Boolean = resetting || transitionFailure != null || closed || exhausted

  override def readable(): Boolean = {
    if (resetting) return false
    val operationEpoch = beginActiveOperation()
    try
      transitionFailure == null && !closed && !exhausted && incomingRef(stageStart)
        .asInstanceOf[Reader.SyncReader[Any]]
        .readable() && !closed
    catch {
      case _ if !operationCurrent(operationEpoch) => false
    } finally endActiveOperation(operationEpoch)
  }

  override def jvmType: JvmType = if (stableOutputType eq null) outputType else stableOutputType

  def outputType: JvmType = if (!closed) logicalOutputType else JvmType.AnyRef

  def read[A1 >: Any](sentinel: A1): A1 = {
    if (resetting) return sentinel
    if (transitionFailure != null) throw transitionFailure
    if (closed || exhausted) return sentinel
    val operationEpoch = beginActiveOperation()
    try {
      var vi: Int = 0; var vl: Long        = 0L; var vf: Float = 0f; var vd: Double = 0.0; var vr: AnyRef = null
      var iPrim   = incomingPrim; var iRef = incomingRef
      while (true) {
        var ip = stageStart; var iLen = incomingLen
        while (ip < iLen) {
          val fn = iRef(ip);
          ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
            case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1       => vl = fn.asInstanceOf[Int => Long](vi);
            case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3     => vd = fn.asInstanceOf[Int => Double](vi);
            case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
            case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6      => vl = fn.asInstanceOf[Long => Long](vl);
            case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8    => vd = fn.asInstanceOf[Long => Double](vl);
            case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
            case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11    => vl = fn.asInstanceOf[Float => Long](vf);
            case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13  => vd = fn.asInstanceOf[Float => Double](vf);
            case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
            case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16   => vl = fn.asInstanceOf[Double => Long](vd);
            case 17 => vf = fn.asInstanceOf[Double => Float](vd); case 18 => vd = fn.asInstanceOf[Double => Double](vd);
            case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
            case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21   => vl = fn.asInstanceOf[AnyRef => Long](vr);
            case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr); case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
            case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
            case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) { ip = stageStart - 1 };
            case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) { ip = stageStart - 1 };
            case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) { ip = stageStart - 1 };
            case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) { ip = stageStart - 1 };
            case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) { ip = stageStart - 1 }
            case 30 =>
              handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 31 =>
              handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 32 =>
              handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 33 =>
              handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 34 =>
              handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 35 =>
              vi = readIntChecked(fn, operationEpoch);
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 40 =>
              vi = readBooleanChecked(fn, operationEpoch);
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 41 =>
              vi = readByteChecked(fn, operationEpoch);
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 42 =>
              vi = readCharChecked(fn, operationEpoch);
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 43 =>
              vi = readShortChecked(fn, operationEpoch);
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 36 =>
              vl = readLongChecked(fn, Long.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 37 =>
              val d = readFloatChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
              else vf = d.toFloat
            case 38 =>
              vd = readDoubleChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 39 =>
              val sv = readRefChecked(fn, operationEpoch);
              if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              } else vr = sv.asInstanceOf[AnyRef]
            case other => throw new IllegalStateException(s"Unknown tag: $other")
          };
          if (!operationCurrent(operationEpoch)) return sentinel
          ip += 1
        }
        var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
        if (oLen > sEnd) {
          val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
          while (j < oLen && emit) {
            val fn = oRef(j);
            ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
              case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1      => vl = fn.asInstanceOf[Int => Long](vi);
              case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3    => vd = fn.asInstanceOf[Int => Double](vi);
              case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
              case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6     => vl = fn.asInstanceOf[Long => Long](vl);
              case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8   => vd = fn.asInstanceOf[Long => Double](vl);
              case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
              case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11   => vl = fn.asInstanceOf[Float => Long](vf);
              case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13 => vd = fn.asInstanceOf[Float => Double](vf);
              case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
              case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16  => vl = fn.asInstanceOf[Double => Long](vd);
              case 17 => vf = fn.asInstanceOf[Double => Float](vd);
              case 18 => vd = fn.asInstanceOf[Double => Double](vd);
              case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
              case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21  => vl = fn.asInstanceOf[AnyRef => Long](vr);
              case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr);
              case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
              case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
              case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) emit = false;
              case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) emit = false;
              case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) emit = false;
              case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) emit = false;
              case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) emit = false
              case 30 =>
                handlePush(fn.asInstanceOf[Int => AnyRef](vi), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 31 =>
                handlePush(fn.asInstanceOf[Long => AnyRef](vl), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 32 =>
                handlePush(fn.asInstanceOf[Float => AnyRef](vf), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 33 =>
                handlePush(fn.asInstanceOf[Double => AnyRef](vd), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 34 =>
                handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false
              case other => throw new IllegalStateException(s"Unknown outgoing tag: $other")
            };
            if (!operationCurrent(operationEpoch)) return sentinel
            j += 1
          }
        }
        if (emit) return ((outputLane: @scala.annotation.switch) match {
          case 0 => boxI(vi, logicalOutputType)
          case 1 => Long.box(vl); case 2 => Float.box(vf); case 3 => Double.box(vd); case _ => vr
        }).asInstanceOf[A1]
      }
      sentinel
    } catch { case cause: Throwable => handlePullFailure(operationEpoch, sentinel, cause) }
    finally endActiveOperation(operationEpoch)
  }

  override def readByte(): Int = {
    val value = readIntStorage(Long.MinValue)
    if (value == Long.MinValue) -1 else value.toInt & 0xff
  }

  override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Int = {
    val v = readIntStorage(sentinel.toLong)
    if (v == sentinel.toLong) sentinel else if (v != 0L) 1 else 0
  }

  override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Int =
    readIntStorage(sentinel.toLong).toInt

  override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Int =
    readIntStorage(sentinel.toLong).toInt

  override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
    lastPrimitiveEof = true
    if (resetting) return sentinel
    if (transitionFailure != null) throw transitionFailure
    if (closed || exhausted) return sentinel
    val operationEpoch = beginActiveOperation()
    try {
      var vi: Int = 0; var vl: Long        = 0L; var vf: Float = 0f; var vd: Double = 0.0; var vr: AnyRef = null
      var iPrim   = incomingPrim; var iRef = incomingRef
      while (true) {
        var ip = stageStart; var iLen = incomingLen
        while (ip < iLen) {
          val fn = iRef(ip);
          ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
            case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1       => vl = fn.asInstanceOf[Int => Long](vi);
            case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3     => vd = fn.asInstanceOf[Int => Double](vi);
            case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
            case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6      => vl = fn.asInstanceOf[Long => Long](vl);
            case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8    => vd = fn.asInstanceOf[Long => Double](vl);
            case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
            case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11    => vl = fn.asInstanceOf[Float => Long](vf);
            case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13  => vd = fn.asInstanceOf[Float => Double](vf);
            case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
            case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16   => vl = fn.asInstanceOf[Double => Long](vd);
            case 17 => vf = fn.asInstanceOf[Double => Float](vd); case 18 => vd = fn.asInstanceOf[Double => Double](vd);
            case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
            case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21   => vl = fn.asInstanceOf[AnyRef => Long](vr);
            case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr); case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
            case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
            case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) { ip = stageStart - 1 };
            case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) { ip = stageStart - 1 };
            case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) { ip = stageStart - 1 };
            case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) { ip = stageStart - 1 };
            case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) { ip = stageStart - 1 }
            case 30 =>
              handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 31 =>
              handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 32 =>
              handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 33 =>
              handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 34 =>
              handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 35 | 40 | 41 | 42 | 43 =>
              vi = ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
                case 35 => readIntChecked(fn, operationEpoch); case 40  => readBooleanChecked(fn, operationEpoch)
                case 41 => readByteChecked(fn, operationEpoch); case 42 => readCharChecked(fn, operationEpoch)
                case 43 => readShortChecked(fn, operationEpoch)
              };
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 36 =>
              vl = readLongChecked(fn, Long.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 37 =>
              val d = readFloatChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
              else vf = d.toFloat
            case 38 =>
              vd = readDoubleChecked(fn, sentinel, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 39 =>
              val sv = readRefChecked(fn, operationEpoch);
              if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              } else vr = sv.asInstanceOf[AnyRef]
            case other => throw new IllegalStateException(s"Unknown tag: $other")
          };
          if (!operationCurrent(operationEpoch)) return sentinel
          ip += 1
        }
        var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
        if (oLen > sEnd) {
          val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
          while (j < oLen && emit) {
            val fn = oRef(j);
            ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
              case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1      => vl = fn.asInstanceOf[Int => Long](vi);
              case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3    => vd = fn.asInstanceOf[Int => Double](vi);
              case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
              case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6     => vl = fn.asInstanceOf[Long => Long](vl);
              case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8   => vd = fn.asInstanceOf[Long => Double](vl);
              case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
              case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11   => vl = fn.asInstanceOf[Float => Long](vf);
              case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13 => vd = fn.asInstanceOf[Float => Double](vf);
              case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
              case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16  => vl = fn.asInstanceOf[Double => Long](vd);
              case 17 => vf = fn.asInstanceOf[Double => Float](vd);
              case 18 => vd = fn.asInstanceOf[Double => Double](vd);
              case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
              case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21  => vl = fn.asInstanceOf[AnyRef => Long](vr);
              case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr);
              case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
              case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
              case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) emit = false;
              case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) emit = false;
              case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) emit = false;
              case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) emit = false;
              case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) emit = false
              case 30 =>
                handlePush(fn.asInstanceOf[Int => AnyRef](vi), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 31 =>
                handlePush(fn.asInstanceOf[Long => AnyRef](vl), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 32 =>
                handlePush(fn.asInstanceOf[Float => AnyRef](vf), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 33 =>
                handlePush(fn.asInstanceOf[Double => AnyRef](vd), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 34 =>
                handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false
              case other => throw new IllegalStateException(s"Unknown outgoing tag: $other")
            };
            if (!operationCurrent(operationEpoch)) return sentinel
            j += 1
          }
        }
        if (emit) { lastPrimitiveEof = false; return vd }
      }
      sentinel
    } catch { case cause: Throwable => handlePullFailureDouble(operationEpoch, sentinel, cause) }
    finally endActiveOperation(operationEpoch)
  }

  override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
    lastPrimitiveEof = true
    if (resetting) return sentinel
    if (transitionFailure != null) throw transitionFailure
    if (closed || exhausted) return sentinel
    val operationEpoch = beginActiveOperation()
    try {
      var vi: Int = 0; var vl: Long        = 0L; var vf: Float = 0f; var vd: Double = 0.0; var vr: AnyRef = null
      var iPrim   = incomingPrim; var iRef = incomingRef
      while (true) {
        var ip = stageStart; var iLen = incomingLen
        while (ip < iLen) {
          val fn = iRef(ip);
          ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
            case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1       => vl = fn.asInstanceOf[Int => Long](vi);
            case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3     => vd = fn.asInstanceOf[Int => Double](vi);
            case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
            case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6      => vl = fn.asInstanceOf[Long => Long](vl);
            case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8    => vd = fn.asInstanceOf[Long => Double](vl);
            case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
            case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11    => vl = fn.asInstanceOf[Float => Long](vf);
            case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13  => vd = fn.asInstanceOf[Float => Double](vf);
            case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
            case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16   => vl = fn.asInstanceOf[Double => Long](vd);
            case 17 => vf = fn.asInstanceOf[Double => Float](vd); case 18 => vd = fn.asInstanceOf[Double => Double](vd);
            case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
            case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21   => vl = fn.asInstanceOf[AnyRef => Long](vr);
            case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr); case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
            case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
            case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) { ip = stageStart - 1 };
            case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) { ip = stageStart - 1 };
            case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) { ip = stageStart - 1 };
            case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) { ip = stageStart - 1 };
            case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) { ip = stageStart - 1 }
            case 30 =>
              handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 31 =>
              handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 32 =>
              handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 33 =>
              handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 34 =>
              handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 35 | 40 | 41 | 42 | 43 =>
              vi = ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
                case 35 => readIntChecked(fn, operationEpoch); case 40  => readBooleanChecked(fn, operationEpoch)
                case 41 => readByteChecked(fn, operationEpoch); case 42 => readCharChecked(fn, operationEpoch)
                case 43 => readShortChecked(fn, operationEpoch)
              };
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 36 =>
              vl = readLongChecked(fn, Long.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 37 =>
              val d = readFloatChecked(fn, sentinel, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
              else vf = d.toFloat
            case 38 =>
              vd = readDoubleChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 39 =>
              val sv = readRefChecked(fn, operationEpoch);
              if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              } else vr = sv.asInstanceOf[AnyRef]
            case other => throw new IllegalStateException(s"Unknown tag: $other")
          };
          if (!operationCurrent(operationEpoch)) return sentinel
          ip += 1
        }
        var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
        if (oLen > sEnd) {
          val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
          while (j < oLen && emit) {
            val fn = oRef(j);
            ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
              case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1      => vl = fn.asInstanceOf[Int => Long](vi);
              case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3    => vd = fn.asInstanceOf[Int => Double](vi);
              case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
              case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6     => vl = fn.asInstanceOf[Long => Long](vl);
              case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8   => vd = fn.asInstanceOf[Long => Double](vl);
              case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
              case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11   => vl = fn.asInstanceOf[Float => Long](vf);
              case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13 => vd = fn.asInstanceOf[Float => Double](vf);
              case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
              case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16  => vl = fn.asInstanceOf[Double => Long](vd);
              case 17 => vf = fn.asInstanceOf[Double => Float](vd);
              case 18 => vd = fn.asInstanceOf[Double => Double](vd);
              case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
              case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21  => vl = fn.asInstanceOf[AnyRef => Long](vr);
              case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr);
              case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
              case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
              case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) emit = false;
              case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) emit = false;
              case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) emit = false;
              case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) emit = false;
              case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) emit = false
              case 30 =>
                handlePush(fn.asInstanceOf[Int => AnyRef](vi), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 31 =>
                handlePush(fn.asInstanceOf[Long => AnyRef](vl), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 32 =>
                handlePush(fn.asInstanceOf[Float => AnyRef](vf), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 33 =>
                handlePush(fn.asInstanceOf[Double => AnyRef](vd), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 34 =>
                handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false
              case other => throw new IllegalStateException(s"Unknown outgoing tag: $other")
            };
            if (!operationCurrent(operationEpoch)) return sentinel
            j += 1
          }
        }
        if (emit) { lastPrimitiveEof = false; return vf.toDouble }
      }
      sentinel
    } catch { case cause: Throwable => handlePullFailureDouble(operationEpoch, sentinel, cause) }
    finally endActiveOperation(operationEpoch)
  }

  override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = readIntStorage(sentinel)

  private def readIntStorage(sentinel: Long): Long = {
    if (resetting) return sentinel
    if (transitionFailure != null) throw transitionFailure
    if (closed || exhausted) return sentinel
    val operationEpoch = beginActiveOperation()
    try {
      var vi: Int = 0; var vl: Long        = 0L; var vf: Float = 0f; var vd: Double = 0.0; var vr: AnyRef = null
      var iPrim   = incomingPrim; var iRef = incomingRef
      while (true) {
        var ip = stageStart; var iLen = incomingLen
        while (ip < iLen) {
          val fn = iRef(ip)
          ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
            case 0  => vi = fn.asInstanceOf[Int => Int](vi)
            case 1  => vl = fn.asInstanceOf[Int => Long](vi)
            case 2  => vf = fn.asInstanceOf[Int => Float](vi)
            case 3  => vd = fn.asInstanceOf[Int => Double](vi)
            case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
            case 5  => vi = fn.asInstanceOf[Long => Int](vl)
            case 6  => vl = fn.asInstanceOf[Long => Long](vl)
            case 7  => vf = fn.asInstanceOf[Long => Float](vl)
            case 8  => vd = fn.asInstanceOf[Long => Double](vl)
            case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
            case 10 => vi = fn.asInstanceOf[Float => Int](vf)
            case 11 => vl = fn.asInstanceOf[Float => Long](vf)
            case 12 => vf = fn.asInstanceOf[Float => Float](vf)
            case 13 => vd = fn.asInstanceOf[Float => Double](vf)
            case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
            case 15 => vi = fn.asInstanceOf[Double => Int](vd)
            case 16 => vl = fn.asInstanceOf[Double => Long](vd)
            case 17 => vf = fn.asInstanceOf[Double => Float](vd)
            case 18 => vd = fn.asInstanceOf[Double => Double](vd)
            case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
            case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr)
            case 21 => vl = fn.asInstanceOf[AnyRef => Long](vr)
            case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr)
            case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr)
            case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
            case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) { ip = stageStart - 1 }
            case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) { ip = stageStart - 1 }
            case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) { ip = stageStart - 1 }
            case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) { ip = stageStart - 1 }
            case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) { ip = stageStart - 1 }
            case 30 =>
              handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 31 =>
              handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 32 =>
              handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 33 =>
              handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 34 =>
              handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 35 | 40 | 41 | 42 | 43 =>
              vi = ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
                case 35 => readIntChecked(fn, operationEpoch); case 40  => readBooleanChecked(fn, operationEpoch)
                case 41 => readByteChecked(fn, operationEpoch); case 42 => readCharChecked(fn, operationEpoch)
                case 43 => readShortChecked(fn, operationEpoch)
              };
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 36 =>
              vl = readLongChecked(fn, Long.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 37 =>
              val d = readFloatChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
              else vf = d.toFloat
            case 38 =>
              vd = readDoubleChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 39 =>
              val sv = readRefChecked(fn, operationEpoch);
              if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              } else vr = sv.asInstanceOf[AnyRef]
            case other => throw new IllegalStateException(s"Unknown tag: $other")
          }
          if (!operationCurrent(operationEpoch)) return sentinel
          ip += 1
        }
        var emit = true
        val oLen = outgoingLen; val sEnd = stageEnd
        if (oLen > sEnd) {
          val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
          while (j < oLen && emit) {
            val fn = oRef(j);
            ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
              case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1      => vl = fn.asInstanceOf[Int => Long](vi);
              case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3    => vd = fn.asInstanceOf[Int => Double](vi);
              case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
              case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6     => vl = fn.asInstanceOf[Long => Long](vl);
              case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8   => vd = fn.asInstanceOf[Long => Double](vl);
              case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
              case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11   => vl = fn.asInstanceOf[Float => Long](vf);
              case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13 => vd = fn.asInstanceOf[Float => Double](vf);
              case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
              case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16  => vl = fn.asInstanceOf[Double => Long](vd);
              case 17 => vf = fn.asInstanceOf[Double => Float](vd);
              case 18 => vd = fn.asInstanceOf[Double => Double](vd);
              case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
              case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21  => vl = fn.asInstanceOf[AnyRef => Long](vr);
              case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr);
              case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
              case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
              case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) emit = false;
              case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) emit = false;
              case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) emit = false;
              case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) emit = false;
              case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) emit = false
              case 30 =>
                handlePush(fn.asInstanceOf[Int => AnyRef](vi), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 31 =>
                handlePush(fn.asInstanceOf[Long => AnyRef](vl), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 32 =>
                handlePush(fn.asInstanceOf[Float => AnyRef](vf), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 33 =>
                handlePush(fn.asInstanceOf[Double => AnyRef](vd), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 34 =>
                handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false
              case other => throw new IllegalStateException(s"Unknown outgoing tag: $other")
            };
            if (!operationCurrent(operationEpoch)) return sentinel
            j += 1
          }
        }
        if (emit) return vi.toLong
      }
      sentinel
    } catch { case cause: Throwable => handlePullFailureLong(operationEpoch, sentinel, cause) }
    finally endActiveOperation(operationEpoch)
  }

  override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
    lastPrimitiveEof = true
    if (resetting) return sentinel
    if (transitionFailure != null) throw transitionFailure
    if (closed || exhausted) return sentinel
    val operationEpoch = beginActiveOperation()
    try {
      var vi: Int = 0; var vl: Long        = 0L; var vf: Float = 0f; var vd: Double = 0.0; var vr: AnyRef = null
      var iPrim   = incomingPrim; var iRef = incomingRef
      while (true) {
        var ip = stageStart; var iLen = incomingLen
        while (ip < iLen) {
          val fn = iRef(ip);
          ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
            case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1       => vl = fn.asInstanceOf[Int => Long](vi);
            case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3     => vd = fn.asInstanceOf[Int => Double](vi);
            case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
            case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6      => vl = fn.asInstanceOf[Long => Long](vl);
            case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8    => vd = fn.asInstanceOf[Long => Double](vl);
            case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
            case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11    => vl = fn.asInstanceOf[Float => Long](vf);
            case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13  => vd = fn.asInstanceOf[Float => Double](vf);
            case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
            case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16   => vl = fn.asInstanceOf[Double => Long](vd);
            case 17 => vf = fn.asInstanceOf[Double => Float](vd); case 18 => vd = fn.asInstanceOf[Double => Double](vd);
            case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
            case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21   => vl = fn.asInstanceOf[AnyRef => Long](vr);
            case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr); case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
            case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
            case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) { ip = stageStart - 1 };
            case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) { ip = stageStart - 1 };
            case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) { ip = stageStart - 1 };
            case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) { ip = stageStart - 1 };
            case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) { ip = stageStart - 1 }
            case 30 =>
              handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 31 =>
              handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 32 =>
              handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 33 =>
              handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen;
            case 34 =>
              handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, -1); iPrim = incomingPrim; iRef = incomingRef;
              ip = stageStart - 1; iLen = incomingLen
            case 35 | 40 | 41 | 42 | 43 =>
              vi = ((iPrim(ip) & 0xff).toInt: @scala.annotation.switch) match {
                case 35 => readIntChecked(fn, operationEpoch); case 40  => readBooleanChecked(fn, operationEpoch)
                case 41 => readByteChecked(fn, operationEpoch); case 42 => readCharChecked(fn, operationEpoch)
                case 43 => readShortChecked(fn, operationEpoch)
              };
              if (lastReadEof) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              }
            case 36 =>
              vl = readLongChecked(fn, Long.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 37 =>
              val d = readFloatChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
              else vf = d.toFloat
            case 38 =>
              vd = readDoubleChecked(fn, Double.MaxValue, operationEpoch);
              if (lastReadEof) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            case 39 =>
              val sv = readRefChecked(fn, operationEpoch);
              if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
                if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
              } else vr = sv.asInstanceOf[AnyRef]
            case other => throw new IllegalStateException(s"Unknown tag: $other")
          };
          if (!operationCurrent(operationEpoch)) return sentinel
          ip += 1
        }
        var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
        if (oLen > sEnd) {
          val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
          while (j < oLen && emit) {
            val fn = oRef(j);
            ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
              case 0  => vi = fn.asInstanceOf[Int => Int](vi); case 1      => vl = fn.asInstanceOf[Int => Long](vi);
              case 2  => vf = fn.asInstanceOf[Int => Float](vi); case 3    => vd = fn.asInstanceOf[Int => Double](vi);
              case 4  => vr = fn.asInstanceOf[Int => AnyRef](vi)
              case 5  => vi = fn.asInstanceOf[Long => Int](vl); case 6     => vl = fn.asInstanceOf[Long => Long](vl);
              case 7  => vf = fn.asInstanceOf[Long => Float](vl); case 8   => vd = fn.asInstanceOf[Long => Double](vl);
              case 9  => vr = fn.asInstanceOf[Long => AnyRef](vl)
              case 10 => vi = fn.asInstanceOf[Float => Int](vf); case 11   => vl = fn.asInstanceOf[Float => Long](vf);
              case 12 => vf = fn.asInstanceOf[Float => Float](vf); case 13 => vd = fn.asInstanceOf[Float => Double](vf);
              case 14 => vr = fn.asInstanceOf[Float => AnyRef](vf)
              case 15 => vi = fn.asInstanceOf[Double => Int](vd); case 16  => vl = fn.asInstanceOf[Double => Long](vd);
              case 17 => vf = fn.asInstanceOf[Double => Float](vd);
              case 18 => vd = fn.asInstanceOf[Double => Double](vd);
              case 19 => vr = fn.asInstanceOf[Double => AnyRef](vd)
              case 20 => vi = fn.asInstanceOf[AnyRef => Int](vr); case 21  => vl = fn.asInstanceOf[AnyRef => Long](vr);
              case 22 => vf = fn.asInstanceOf[AnyRef => Float](vr);
              case 23 => vd = fn.asInstanceOf[AnyRef => Double](vr);
              case 24 => vr = fn.asInstanceOf[AnyRef => AnyRef](vr)
              case 25 => if (!fn.asInstanceOf[Int => Boolean](vi)) emit = false;
              case 26 => if (!fn.asInstanceOf[Long => Boolean](vl)) emit = false;
              case 27 => if (!fn.asInstanceOf[Float => Boolean](vf)) emit = false;
              case 28 => if (!fn.asInstanceOf[Double => Boolean](vd)) emit = false;
              case 29 => if (!fn.asInstanceOf[AnyRef => Boolean](vr)) emit = false
              case 30 =>
                handlePush(fn.asInstanceOf[Int => AnyRef](vi), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 31 =>
                handlePush(fn.asInstanceOf[Long => AnyRef](vl), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 32 =>
                handlePush(fn.asInstanceOf[Float => AnyRef](vf), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 33 =>
                handlePush(fn.asInstanceOf[Double => AnyRef](vd), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false;
              case 34 =>
                handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), true, j); iPrim = incomingPrim; iRef = incomingRef;
                emit = false
              case other => throw new IllegalStateException(s"Unknown outgoing tag: $other")
            };
            if (!operationCurrent(operationEpoch)) return sentinel
            j += 1
          }
        }
        if (emit) { lastPrimitiveEof = false; return vl }
      }
      sentinel
    } catch { case cause: Throwable => handlePullFailureLong(operationEpoch, sentinel, cause) }
    finally endActiveOperation(operationEpoch)
  }

  override def readN[A >: Any](n: Int): Chunk[A] = {
    if (resetting) return super.readN(0)
    replayTransitionFailure()
    if (n <= 0 && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    super.readN(n)
  }

  override def readUpToN[A >: Any](n: Int): Chunk[A] = {
    if (resetting) return super.readUpToN(0)
    replayTransitionFailure()
    if (n <= 0 && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    super.readUpToN(n)
  }

  override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Any <:< Byte): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (resetting) return if (length == 0) 0 else -1
    replayTransitionFailure()
    if (length == 0 && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    super.readBytes(dest, offset, length)
  }

  override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Any <:< Int): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (resetting) return if (length == 0) 0 else -1
    replayTransitionFailure()
    if (length == 0 && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    super.readInts(dest, offset, length)
  }

  override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (resetting) return if (length == 0) 0 else -1
    replayTransitionFailure()
    if (length == 0 && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    if (length == 0) return 0
    var i = 0
    while (i < length) {
      val value = readLongPhysical(0L)
      if (lastPrimitiveEof) return if (i == 0) -1 else i
      dest(offset + i) = value
      i += 1
      if (i < length && (tryReadable ne Reader.Available)) return i
    }
    i
  }

  override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: Any <:< Float): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (resetting) return if (length == 0) 0 else -1
    replayTransitionFailure()
    if (length == 0 && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    if (length == 0) return 0
    var i = 0
    while (i < length) {
      val value = readFloatPhysical(Double.MaxValue)
      if (lastPrimitiveEof) return if (i == 0) -1 else i
      dest(offset + i) = value.toFloat
      i += 1
      if (i < length && (tryReadable ne Reader.Available)) return i
    }
    i
  }

  override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (resetting) return if (length == 0) 0 else -1
    replayTransitionFailure()
    if (length == 0 && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    if (length == 0) return 0
    var i = 0
    while (i < length) {
      val value = readDoublePhysical(0.0)
      if (lastPrimitiveEof) return if (i == 0) -1 else i
      dest(offset + i) = value
      i += 1
      if (i < length && (tryReadable ne Reader.Available)) return i
    }
    i
  }

  override def reset(): Unit = {
    if (resetting || activeOperations != 0)
      throw new IllegalStateException("Cannot reset reader from its active operation")
    val wasClosed = closed
    resetting = true
    closeRequestedDuringReset = false
    lifecycleEpoch += 1L
    closed = true
    try {
      val priorFailure          = transitionFailure
      val len                   = incomingLen; var i = 1
      var firstError: Throwable = priorFailure
      var resetFailed           = false
      while (i < len) {
        if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
          val reader = incomingRef(i)
          if (reader != null) {
            incomingRef(i) = null
            try reader.asInstanceOf[Reader.SyncReader[Any]].close()
            catch {
              case cause: Throwable =>
                if (!StreamError.ignorableReplay(priorFailure, cause)) {
                  resetFailed = true
                  firstError = StreamError.attachCleanupReplay(firstError, cause)
                }
            }
          }
        }
        i += 1
      }
      state = initialState
      outputLane = StreamState.outputLane(initialState)
      logicalOutputType = initialOutputType
      var rootReset = false
      try {
        incomingRef(0).asInstanceOf[Reader.SyncReader[Any]].reset()
        rootReset = true
      } catch {
        case cause: Throwable =>
          resetFailed = true
          firstError = StreamError.attachSuppressedReplay(firstError, cause)
      }
      val closeWon = closeRequestedDuringReset
      if (rootReset && !resetFailed && !closeWon) {
        closed = false
        exhausted = false
        closeFailure = null
        transitionFailure = null
        pendingCleanupFailure = null
      } else {
        val rejection =
          if (closeWon && (firstError eq null)) new java.io.IOException("Reader is closed")
          else null
        val failure                          = if (firstError ne null) firstError else rejection
        val closeNow                         = closeWon || resetFailed
        var rejectionCloseFailure: Throwable = null
        if (closeNow) {
          closed = true
          closeFailure = null
          try incomingRef(0).asInstanceOf[Reader.SyncReader[Any]].close()
          catch {
            case cause: Throwable =>
              rejectionCloseFailure = cause
              StreamError.attachCleanupReplay(failure, cause)
          }
        }
        closed = closeNow || wasClosed
        transitionFailure = firstError
        closeFailure =
          if (closed && (firstError ne null)) firstError
          else rejectionCloseFailure
        throw failure
      }
    } finally resetting = false
  }

  override def setLimit(n: Long): Boolean = {
    if (resetting) return false
    withControlOperation {
      checkControlOpen()
      allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader.SyncReader[Any]].setLimit(n) && !closed
    }
  }

  override def setRepeat(): Boolean = {
    if (resetting) return false
    withControlOperation {
      checkControlOpen()
      val repeated = allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader.SyncReader[Any]].setRepeat()
      if (repeated && !closed) exhausted = false
      repeated && !closed
    }
  }

  override def setSkip(n: Long): Boolean = {
    if (resetting) return false
    withControlOperation {
      checkControlOpen()
      allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader.SyncReader[Any]].setSkip(n) && !closed
    }
  }

  override def skip(n: Long): Unit = {
    if (resetting) return
    replayTransitionFailure()
    if (n <= 0L && activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    Reader.skipViaSentinel(this, n)
  }

  private[streams] def addFilter[A](inType: JvmType)(f: A => Boolean): Unit =
    addAdaptedFilter(inType, adaptFilter(inType, f))

  private[streams] def addAdaptedFilter(inType: JvmType, fn: AnyRef): Unit = {
    alignInputType(inType)
    val inLane = laneOf(inType)
    val tag    = OpTag.filterTag(inLane).toLong
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag; outgoingRef(idx) = fn
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag; incomingRef(idx) = fn
      state = StreamState.withIncomingLen(state, idx + 1)
    }
  }

  private[streams] def addFilter[A](inLane: Lane)(f: A => Boolean): Unit =
    addFilter(elemTypeOfLane(inLane))(f)

  private[streams] def addMap[A, B](inType: JvmType, outType: JvmType)(f: A => B): Unit =
    addAdaptedMap(inType, outType, adaptMap(inType, outType, f))

  private[streams] def addAdaptedMap(inType: JvmType, outType: JvmType, fn: AnyRef): Unit = {
    alignInputType(inType)
    val inLane  = laneOf(inType)
    val outLane = outLaneOf(outType)
    val tag     = OpTag.mapTag(inLane, outLane).toLong
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag; outgoingRef(idx) = fn
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag; incomingRef(idx) = fn
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = OpTag.storageLaneOfMapTag(tag.toInt)
    logicalOutputType = outType
  }

  private[streams] def addMap[A, B](inLane: Lane, outLane: Lane)(f: A => B): Unit =
    addMap(elemTypeOfLane(inLane), elemTypeOfLane(outLane))(f)

  private[streams] def addPush[A](inType: JvmType, outType: JvmType)(f: A => Any): Unit = {
    alignInputType(inType)
    val inLane = laneOf(inType)
    val tag    = OpTag.pushTag(inLane).toLong
    val fn     = adaptPush(inType, f)
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag; outgoingRef(idx) = fn
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag; incomingRef(idx) = fn
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = outLaneOf(outType)
    logicalOutputType = outType
  }

  private[streams] def addPush[A](inLane: Lane)(f: A => Any): Unit =
    addPush(elemTypeOfLane(inLane), elemTypeOfLane(inLane))(f)

  /**
   * Aligns erased/reference sources with the statically known input type of the
   * next operator. The interpreter's op tags describe physical lanes, so
   * allowing an `AnyRef` source followed by an `Int` map would otherwise read
   * into `vr` and invoke the callback with the untouched `vi` scratch value.
   */
  private def alignInputType(expected: JvmType): Unit = {
    val actual = logicalOutputType
    if (actual ne expected) {
      val srcLane = laneOf(actual)
      val dstLane = outLaneOf(expected)
      if (srcLane != dstLane) {
        val tag = OpTag.mapTag(srcLane, dstLane).toLong
        val fn  = logicalBridgeFn(actual, expected)
        if (afterPush) {
          val idx = ensureOutgoing()
          outgoingPrim(idx) = tag; outgoingRef(idx) = fn
          state = StreamState.withOutgoingLen(state, idx + 1)
        } else {
          val idx = ensureIncoming()
          incomingPrim(idx) = tag; incomingRef(idx) = fn
          state = StreamState.withIncomingLen(state, idx + 1)
        }
        outputLane = dstLane
      }
      logicalOutputType = expected
    }
  }

  private[streams] def appendRead(reader: Reader.SyncReader[_]): Unit = {
    ensureMaterializationCurrent()
    val logicalType = reader.jvmType
    ensureMaterializationCurrent()
    appendRead(reader, logicalType)
  }

  private[streams] def appendRead(reader: Reader.SyncReader[_], logicalType: JvmType): Unit = {
    ensureMaterializationCurrent()
    val lane = laneOf(logicalType)
    val idx  = ensureIncoming()
    incomingPrim(idx) = OpTag.readTag(logicalType).toLong
    incomingRef(idx) = reader.asInstanceOf[AnyRef]
    incomingType(idx) = logicalType
    state = StreamState.withIncomingLen(state, idx + 1)
    outputLane = lane
    logicalOutputType = logicalType
  }

  private[streams] def seal(): Unit = {
    initialState = StreamState(stageStart, incomingLen, stageEnd, outgoingLen, outputLane)
    initialOutputType = logicalOutputType
  }

  private[streams] final class WrapTransaction private[SyncInterpreter] () {
    private[SyncInterpreter] var rejectedWrapperClosed = false
    def cleanupConsumedOwner: Boolean                  = rejectedWrapperClosed
  }

  private[streams] def newWrapTransaction(): WrapTransaction = new WrapTransaction

  private[streams] def wrapOutput(
    f: Reader.SyncReader[Any] => Reader.SyncReader[Any],
    transaction: WrapTransaction = new WrapTransaction
  ): Unit = {
    ensureMaterializationCurrent()
    if (stageStart == 0 && incomingLen == 1 && outgoingLen == 0 && OpTag.isRead((incomingPrim(0) & 0xff).toInt))
      wrapLastRead(f, transaction)
    else {
      val inner                           = detachAsReader()
      var wrapped: Reader.SyncReader[Any] = null
      try {
        wrapped = f(inner)
        ensureMaterializationCurrent()
        appendRead(wrapped)
      } catch {
        case cause: Throwable =>
          closeRejectedOwner(if (wrapped ne null) wrapped else inner, cause)
          transaction.rejectedWrapperClosed = true
          throw cause
      }
    }
  }

  private[streams] def wrapLastRead(
    f: Reader.SyncReader[Any] => Reader.SyncReader[Any],
    transaction: WrapTransaction = new WrapTransaction
  ): Unit = {
    ensureMaterializationCurrent()
    var i = incomingLen - 1
    while (i >= 0) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        val oldReader                         = incomingRef(i).asInstanceOf[Reader.SyncReader[Any]]
        var newReader: Reader.SyncReader[Any] = null
        try {
          newReader = f(oldReader)
          ensureMaterializationCurrent()
          val newType = newReader.jvmType
          ensureMaterializationCurrent()
          incomingRef(i) = newReader.asInstanceOf[AnyRef]
          incomingType(i) = newType
          incomingPrim(i) = OpTag.readTag(newType).toLong
          return
        } catch {
          case cause: Throwable =>
            incomingRef(i) = null
            closeRejectedOwner(if (newReader ne null) newReader else oldReader, cause)
            transaction.rejectedWrapperClosed = true
            throw cause
        }
      }
      i -= 1
    }
  }

  /**
   * Transfers the pipeline compiled so far to an owned child reader. Reader
   * wrappers such as `takeWhile` and `repeated` operate on the logical output
   * of the whole preceding pipeline, not on its physical source reader.
   */
  private def detachAsReader(): SyncInterpreter = {
    val inner = new SyncInterpreter()
    inner.incomingPrim = incomingPrim
    inner.incomingRef = incomingRef
    inner.incomingType = incomingType
    inner.savedState = savedState
    inner.savedOutputType = savedOutputType
    inner.outgoingPrim = outgoingPrim
    inner.outgoingRef = outgoingRef
    inner.state = state
    inner.outputLane = outputLane
    inner.logicalOutputType = logicalOutputType
    inner.enclosingInterpreter = this
    inner.seal()

    incomingPrim = new Array[Long](8)
    incomingRef = new Array[AnyRef](8)
    incomingType = new Array[JvmType](8)
    savedState = new Array[StreamState](8)
    savedOutputType = new Array[JvmType](8)
    outgoingPrim = null
    outgoingRef = null
    state = StreamState.empty
    outputLane = LANE_R
    logicalOutputType = JvmType.AnyRef
    inner
  }

  private def afterPush: Boolean = {
    val len = incomingLen
    len > 0 && OpTag.isPush((incomingPrim(len - 1) & 0xff).toInt)
  }

  private def checkControlOpen(): Unit =
    if (transitionFailure ne null) throw transitionFailure
    else if (closed) throw new java.io.IOException("Reader is closed")

  private def replayTransitionFailure(): Unit =
    if (transitionFailure ne null) throw transitionFailure

  private def beginActiveOperation(): Long = {
    if (activeOperations != 0) throw new IllegalStateException(ConcurrentOperationMessage)
    operationSequence += 1L
    activeOperationOwner = operationSequence
    activeOperations += 1
    activeOperationEpoch = lifecycleEpoch
    activeOperationOwner
  }

  private def operationCurrent(owner: Long): Boolean = {
    val local = activeOperationOwner == owner && !closed && activeOperationEpoch == lifecycleEpoch
    if (!local) false else ancestorsCurrent()
  }

  private def ancestorsCurrent(): Boolean = {
    var current = enclosingInterpreter
    while (current ne null) {
      if (
        current.closed ||
        (current.activeOperations != 0 &&
          (current.activeOperationOwner == 0L || current.activeOperationEpoch != current.lifecycleEpoch))
      ) return false
      current = current.enclosingInterpreter
    }
    true
  }

  private def ensureEnclosingOperationCurrent(): Unit = if (!ancestorsCurrent()) throw StaleRead

  private def endActiveOperation(owner: Long): Unit =
    if (activeOperationOwner == owner) {
      activeOperationOwner = 0L
      activeOperations -= 1
      if (activeOperations == 0 && closePending && !closing) {
        runCloseTraversal()
      }
    }

  private def withControlOperation(body: => Boolean): Boolean = {
    val operationEpoch = beginActiveOperation()
    try {
      val result = body
      if (closed || activeOperationEpoch != lifecycleEpoch) throw new java.io.IOException("Reader is closed")
      result
    } catch {
      case _ if closed || activeOperationEpoch != lifecycleEpoch => throw new java.io.IOException("Reader is closed")
    } finally endActiveOperation(operationEpoch)
  }

  private def handlePullFailure[A](operationEpoch: Long, sentinel: A, cause: Throwable): A =
    if (!operationCurrent(operationEpoch)) sentinel
    else {
      if (transitionFailure eq null) transitionFailure = cause
      throw transitionFailure
    }

  private def handlePullFailureLong(operationEpoch: Long, sentinel: Long, cause: Throwable): Long =
    if (!operationCurrent(operationEpoch)) sentinel
    else {
      if (transitionFailure eq null) transitionFailure = cause
      throw transitionFailure
    }

  private def handlePullFailureDouble(operationEpoch: Long, sentinel: Double, cause: Throwable): Double =
    if (!operationCurrent(operationEpoch)) sentinel
    else {
      if (transitionFailure eq null) transitionFailure = cause
      throw transitionFailure
    }

  private def allOpsAreMaps(): Boolean = {
    val len = incomingLen; var i = stageStart + 1
    while (i < len) { if ((incomingPrim(i) & 0xff) >= 25) return false; i += 1 }
    outgoingLen == stageEnd
  }

  private def ensureIncoming(): Int = {
    val len = incomingLen
    if (len >= StreamState.MaxIndex)
      throw new IllegalStateException(
        s"Stream pipeline too deep: $len operations exceeds the maximum of ${StreamState.MaxIndex}. " +
          "Simplify the stream composition or reduce flatMap nesting depth."
      )
    if (len == incomingPrim.length) growIncoming()
    len
  }

  private def ensureOutgoing(): Int = {
    val len = outgoingLen
    if (len >= StreamState.MaxIndex)
      throw new IllegalStateException(
        s"Stream pipeline too deep: $len outgoing operations exceeds the maximum of ${StreamState.MaxIndex}. " +
          "Simplify the stream composition or reduce flatMap nesting depth."
      )
    if (outgoingPrim == null) { outgoingPrim = new Array[Long](4); outgoingRef = new Array[AnyRef](4) }
    else if (len == outgoingPrim.length) growOutgoing()
    len
  }

  private def growIncoming(): Unit = {
    val len = incomingPrim.length
    val np  = new Array[Long](len * 2); System.arraycopy(incomingPrim, 0, np, 0, len); incomingPrim = np
    val nr  = new Array[AnyRef](len * 2); System.arraycopy(incomingRef, 0, nr, 0, len); incomingRef = nr
    val nt  = new Array[JvmType](len * 2); System.arraycopy(incomingType, 0, nt, 0, len); incomingType = nt
    val nss = new Array[StreamState](len * 2); System.arraycopy(savedState, 0, nss, 0, len); savedState = nss
    val ns  = new Array[JvmType](len * 2); System.arraycopy(savedOutputType, 0, ns, 0, len); savedOutputType = ns
  }

  private def growOutgoing(): Unit = {
    val len = outgoingPrim.length
    val np  = new Array[Long](len * 2); System.arraycopy(outgoingPrim, 0, np, 0, len); outgoingPrim = np
    val nr  = new Array[AnyRef](len * 2); System.arraycopy(outgoingRef, 0, nr, 0, len); outgoingRef = nr
  }

  private def handlePush(innerStreamRef: AnyRef, fromOutgoing: Boolean, outgoingIdx: Int): Unit = {
    val operationEpoch = activeOperationEpoch
    if (closed || operationEpoch != lifecycleEpoch) return
    val outerOL    = outputLane
    val outerType  = logicalOutputType
    val savedState = StreamState(stageStart, incomingLen, stageEnd, outgoingLen, outerOL)
    val priorState = state
    val priorIL    = incomingLen
    val priorOL    = outgoingLen
    if (fromOutgoing) state = StreamState.withStageEnd(state, outgoingIdx + 1)
    materializationEpoch = operationEpoch
    try {
      if (innerStreamRef eq null) throw new NullPointerException("PUSH callback returned null")
      innerStreamRef.asInstanceOf[ZStream[Any, Any]].compileInterpreter(this)
      ensureMaterializationCurrent()
      val innerOL   = outputLane
      val innerType = logicalOutputType
      this.savedState(priorIL) = savedState
      savedOutputType(priorIL) = outerType
      state = StreamState.withStageStart(state, priorIL)
      if (innerOL != outerOL) {
        val idx = ensureIncoming()
        incomingPrim(idx) = OpTag.mapTag(innerOL, outerOL).toLong
        incomingRef(idx) = logicalBridgeFn(innerType, outerType)
        incomingType(idx) = outerType
        state = StreamState.withIncomingLen(state, idx + 1)
      }
      ensureMaterializationCurrent()
      outputLane = outerOL
      logicalOutputType = outerType
    } catch {
      case cause: Throwable =>
        val stale = closed || operationEpoch != lifecycleEpoch
        rollbackPush(priorIL, priorOL, priorState, outerOL, outerType, cause)
        if (!stale) throw cause
    } finally materializationEpoch = -1L
  }

  private def rollbackPush(
    priorIL: Int,
    priorOL: Int,
    priorState: StreamState,
    priorLane: Lane,
    priorType: JvmType,
    staleOrFailure: Throwable
  ): Unit = {
    var i          = priorIL
    val appendedIL = incomingLen
    while (i < appendedIL) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        val reader = incomingRef(i)
        incomingRef(i) = null
        if (reader ne null)
          try reader.asInstanceOf[Reader.SyncReader[Any]].close()
          catch {
            case cause: Throwable => recordCleanupFailure(staleOrFailure, cause)
          }
      } else incomingRef(i) = null
      incomingPrim(i) = 0L
      incomingType(i) = null
      savedState(i) = StreamState.empty
      savedOutputType(i) = null
      i += 1
    }
    i = priorOL
    while (i < outgoingLen) {
      outgoingPrim(i) = 0L
      outgoingRef(i) = null
      i += 1
    }
    state = priorState
    outputLane = priorLane
    logicalOutputType = priorType
  }

  private[streams] def ensureMaterializationCurrent(): Unit = {
    ensureEnclosingOperationCurrent()
    if (materializationEpoch >= 0L && (closed || materializationEpoch != lifecycleEpoch))
      throw new StaleMaterialization
  }

  /**
   * The parent guard protects construction. Once a reader is transferred to a
   * concurrent owner, that owner controls its runtime lifetime directly.
   */
  private[streams] def releaseEnclosingGuard(): Unit = enclosingInterpreter = null

  private[streams] val operationGuard: Reader.InterpreterGuard = new Reader.InterpreterGuard {
    def check(): Unit                                                 = ensureReadCurrent(activeOperationOwner)
    def reject(owner: Reader.SyncReader[_], primary: Throwable): Unit = closeRejectedOwner(owner, primary)
    def cleanupFailed(primary: Throwable, cleanup: Throwable): Unit   = recordCleanupFailure(primary, cleanup)
  }

  private[streams] def recordCleanupFailure(primary: Throwable, cleanup: Throwable): Unit = {
    val localStale =
      (materializationEpoch >= 0L && (closed || materializationEpoch != lifecycleEpoch)) ||
        (activeOperations > 0 && (closed || activeOperationEpoch != lifecycleEpoch))
    var staleAncestor: SyncInterpreter = null
    var ancestor                       = enclosingInterpreter
    while (ancestor ne null) {
      if (
        ancestor.closed ||
        (ancestor.materializationEpoch >= 0L && ancestor.materializationEpoch != ancestor.lifecycleEpoch) ||
        (ancestor.activeOperations > 0 && ancestor.activeOperationEpoch != ancestor.lifecycleEpoch)
      ) staleAncestor = ancestor
      ancestor = ancestor.enclosingInterpreter
    }
    if (staleAncestor ne null) staleAncestor.recordPendingCleanup(cleanup)
    else if (localStale) recordPendingCleanup(cleanup)
    else StreamError.attachCleanup(primary, cleanup)
  }

  private def recordPendingCleanup(cleanup: Throwable): Unit = {
    pendingCleanupFailure = StreamError.attachCleanup(pendingCleanupFailure, cleanup)
    closePending = true
  }

  /**
   * Closes a not-yet-installed owner and routes only that close failure to the
   * stale-operation cleanup channel. Callback failures and their suppressed
   * exceptions are deliberately never inspected here.
   */
  private[streams] def closeRejectedOwner(owner: Reader.SyncReader[_], primary: Throwable): Unit =
    try owner.close()
    catch {
      case cleanup: Throwable => recordCleanupFailure(primary, cleanup)
    }

  private[streams] def materializationCheckpoint(): MaterializationCheckpoint =
    MaterializationCheckpoint(state, outputLane, logicalOutputType)

  /**
   * Rolls back readers staged since `checkpoint`, closing children in append
   * order before a resource owner is released by its caller. Cleared slots make
   * a surrounding PUSH rollback exact-once.
   */
  private[streams] def rollbackTo(checkpoint: MaterializationCheckpoint, primary: Throwable): Unit = {
    var i   = il(checkpoint.state)
    val end = incomingLen
    while (i < end) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        val owner = incomingRef(i).asInstanceOf[Reader.SyncReader[_]]
        incomingRef(i) = null
        if (owner ne null) closeRejectedOwner(owner, primary)
      } else incomingRef(i) = null
      incomingPrim(i) = 0L
      incomingType(i) = null
      savedState(i) = StreamState.empty
      savedOutputType(i) = null
      i += 1
    }
    state = checkpoint.state
    outputLane = checkpoint.lane
    logicalOutputType = checkpoint.logicalType
  }

  private def incomingLen: Int = il(state)

  private def outgoingLen: Int = ol(state)

  private def ensureReadCurrent(operationEpoch: Long): Unit =
    if (!operationCurrent(operationEpoch)) throw StaleRead

  private def readBooleanChecked(fn: AnyRef, operationEpoch: Long): Int = {
    val value = fn.asInstanceOf[Reader.SyncReader[Any]].readBooleanPhysical(-1)
    ensureReadCurrent(operationEpoch)
    lastReadEof = value == -1
    if (lastReadEof || value == 0) 0 else 1
  }

  private def readByteChecked(fn: AnyRef, operationEpoch: Long): Int = {
    val value = fn.asInstanceOf[Reader.SyncReader[Any]].readBytePhysical()
    ensureReadCurrent(operationEpoch)
    lastReadEof = value < 0
    if (lastReadEof) 0 else value
  }

  private def readCharChecked(fn: AnyRef, operationEpoch: Long): Int = {
    val value = fn.asInstanceOf[Reader.SyncReader[Any]].readCharPhysical(Int.MinValue)
    ensureReadCurrent(operationEpoch)
    lastReadEof = value == Int.MinValue
    if (lastReadEof) 0 else value
  }

  private def readIntChecked(fn: AnyRef, operationEpoch: Long): Int = {
    val value = fn.asInstanceOf[Reader.SyncReader[Any]].readIntPhysical(Long.MinValue)
    ensureReadCurrent(operationEpoch)
    lastReadEof = value == Long.MinValue
    if (lastReadEof) 0 else value.toInt
  }

  private def readShortChecked(fn: AnyRef, operationEpoch: Long): Int = {
    val value = fn.asInstanceOf[Reader.SyncReader[Any]].readShortPhysical(Int.MinValue)
    ensureReadCurrent(operationEpoch)
    lastReadEof = value == Int.MinValue
    if (lastReadEof) 0 else value
  }

  private var lastReadEof      = false
  private var lastPrimitiveEof = false
  private val oneLong          = new Array[Long](1)
  private val oneDouble        = new Array[Double](1)

  private def readLongChecked(fn: AnyRef, sentinel: Long, operationEpoch: Long): Long = {
    val count = fn.asInstanceOf[Reader.SyncReader[Any]].readLongsPhysical(oneLong, 0, 1)
    ensureReadCurrent(operationEpoch)
    lastReadEof = count < 0
    if (lastReadEof) sentinel else oneLong(0)
  }

  private def readFloatChecked(fn: AnyRef, sentinel: Double, operationEpoch: Long): Double = {
    val value = fn.asInstanceOf[Reader.SyncReader[Any]].readFloatPhysical(sentinel)
    ensureReadCurrent(operationEpoch)
    lastReadEof = value == sentinel
    value
  }

  private def readDoubleChecked(fn: AnyRef, sentinel: Double, operationEpoch: Long): Double = {
    val count = fn.asInstanceOf[Reader.SyncReader[Any]].readDoublesPhysical(oneDouble, 0, 1)
    ensureReadCurrent(operationEpoch)
    lastReadEof = count < 0
    if (lastReadEof) sentinel else oneDouble(0)
  }

  private def readRefChecked(fn: AnyRef, operationEpoch: Long): Any = {
    val value = fn.asInstanceOf[Reader.SyncReader[Any]].read[Any](EndOfStream)
    ensureReadCurrent(operationEpoch)
    value
  }

  private def popAt(readIdx: Int): Boolean = {
    if (stageStart == 0) {
      exhausted = true
      return false
    }
    val exhaustedReader = incomingRef(readIdx).asInstanceOf[Reader.SyncReader[Any]]
    incomingRef(readIdx) = null
    try exhaustedReader.close()
    catch {
      case cause: Throwable =>
        pendingCleanupFailure = StreamError.attachCleanup(pendingCleanupFailure, cause)
        if (closed) return false
        transitionFailure = cause
        throw cause
    }
    if (closed) return false
    val saved = savedState(readIdx)
    savedState(readIdx) = StreamState.empty
    state = StreamState(ss(saved), il(saved), se(saved), ol(saved), olane(saved))
    outputLane = olane(saved)
    logicalOutputType = savedOutputType(readIdx)
    true
  }

  private def stageEnd: Int = se(state)

  private def stageStart: Int = ss(state)
}

private[streams] object SyncInterpreter {
  private[streams] final val ConcurrentOperationMessage = "Cannot perform a reentrant reader operation"
  private[streams] final case class MaterializationCheckpoint(state: StreamState, lane: Lane, logicalType: JvmType)
  private object StaleRead                 extends Throwable with scala.util.control.NoStackTrace
  private final class StaleMaterialization extends Throwable with scala.util.control.NoStackTrace

  private[streams] final val LANE_D: Lane = 3

  private[streams] final val LANE_F: Lane = 2

  private[streams] final val LANE_I: Lane = 0

  private[streams] final val LANE_L: Lane = 1

  private[streams] final val LANE_R: Lane = 4

  private[streams] final val OUT_D = 3

  private[streams] final val OUT_F = 2

  private[streams] final val OUT_I = 0

  private[streams] final val OUT_L = 1

  private[streams] final val OUT_R = 4

  private[streams] def apply(source: Reader.SyncReader[_]): SyncInterpreter = {
    val p = new SyncInterpreter()
    p.appendRead(source)
    p.seal()
    p
  }

  private[streams] def bridgeFn(srcLane: Lane, dstLane: Lane): AnyRef = _bridgeFns(srcLane * 5 + dstLane)

  private[streams] def bridgeTag(srcLane: Lane, dstLane: Lane): Int = _bridgeTags(srcLane * 5 + dstLane)

  private[streams] def adaptFilter[A](inType: JvmType, original: A => Boolean): AnyRef =
    if (inType eq JvmType.Boolean)
      (
        (i: Int) =>
          try original.asInstanceOf[Boolean => Boolean](i != 0)
          catch { case error: StreamError => throw StreamError.untrusted(error) }
      ).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Byte)
      (
        (i: Int) =>
          try original.asInstanceOf[Byte => Boolean](i.toByte)
          catch { case error: StreamError => throw StreamError.untrusted(error) }
      ).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Short)
      (
        (i: Int) =>
          try original.asInstanceOf[Short => Boolean](i.toShort)
          catch { case error: StreamError => throw StreamError.untrusted(error) }
      ).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Char)
      (
        (i: Int) =>
          try original.asInstanceOf[Char => Boolean](i.toChar)
          catch { case error: StreamError => throw StreamError.untrusted(error) }
      ).asInstanceOf[AnyRef]
    else
      laneOf(inType) match {
        case LANE_I =>
          (
            (i: Int) =>
              try original.asInstanceOf[Int => Boolean](i)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case LANE_L =>
          (
            (l: Long) =>
              try original.asInstanceOf[Long => Boolean](l)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case LANE_F =>
          (
            (v: Float) =>
              try original.asInstanceOf[Float => Boolean](v)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case LANE_D =>
          (
            (d: Double) =>
              try original.asInstanceOf[Double => Boolean](d)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case _ =>
          (
            (r: AnyRef) =>
              try original.asInstanceOf[AnyRef => Boolean](r)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
      }

  private[streams] def adaptMap[A, B](inType: JvmType, outType: JvmType, original: A => B): AnyRef = {
    val f: A => B = a =>
      try original(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    val inBoolean  = inType eq JvmType.Boolean
    val outBoolean = outType eq JvmType.Boolean
    val inNarrow   = (inType eq JvmType.Byte) || (inType eq JvmType.Short) || (inType eq JvmType.Char)
    val outNarrow  = (outType eq JvmType.Byte) || (outType eq JvmType.Short) || (outType eq JvmType.Char)
    if (inNarrow) {
      if (inType eq JvmType.Byte) outLaneOf(outType) match {
        case OUT_I => ((i: Int) => unboxI(f.asInstanceOf[Byte => Any](i.toByte), outType)).asInstanceOf[AnyRef]
        case OUT_L =>
          (
            (i: Int) =>
              try original.asInstanceOf[Byte => Long](i.toByte)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case OUT_F => ((i: Int) => f.asInstanceOf[Byte => Float](i.toByte)).asInstanceOf[AnyRef]
        case OUT_D => ((i: Int) => f.asInstanceOf[Byte => Double](i.toByte)).asInstanceOf[AnyRef]
        case _     => ((i: Int) => f.asInstanceOf[Byte => AnyRef](i.toByte)).asInstanceOf[AnyRef]
      }
      else if (inType eq JvmType.Short) outLaneOf(outType) match {
        case OUT_I => ((i: Int) => unboxI(f.asInstanceOf[Short => Any](i.toShort), outType)).asInstanceOf[AnyRef]
        case OUT_L =>
          (
            (i: Int) =>
              try original.asInstanceOf[Short => Long](i.toShort)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case OUT_F => ((i: Int) => f.asInstanceOf[Short => Float](i.toShort)).asInstanceOf[AnyRef]
        case OUT_D => ((i: Int) => f.asInstanceOf[Short => Double](i.toShort)).asInstanceOf[AnyRef]
        case _     => ((i: Int) => f.asInstanceOf[Short => AnyRef](i.toShort)).asInstanceOf[AnyRef]
      }
      else
        outLaneOf(outType) match {
          case OUT_I => ((i: Int) => unboxI(f.asInstanceOf[Char => Any](i.toChar), outType)).asInstanceOf[AnyRef]
          case OUT_L =>
            (
              (i: Int) =>
                try original.asInstanceOf[Char => Long](i.toChar)
                catch { case error: StreamError => throw StreamError.untrusted(error) }
            ).asInstanceOf[AnyRef]
          case OUT_F => ((i: Int) => f.asInstanceOf[Char => Float](i.toChar)).asInstanceOf[AnyRef]
          case OUT_D => ((i: Int) => f.asInstanceOf[Char => Double](i.toChar)).asInstanceOf[AnyRef]
          case _     => ((i: Int) => f.asInstanceOf[Char => AnyRef](i.toChar)).asInstanceOf[AnyRef]
        }
    } else if (outNarrow) {
      laneOf(inType) match {
        case LANE_I => ((i: Int) => unboxI(f.asInstanceOf[Int => Any](i), outType)).asInstanceOf[AnyRef]
        case LANE_L => ((l: Long) => unboxI(f.asInstanceOf[Long => Any](l), outType)).asInstanceOf[AnyRef]
        case LANE_F => ((v: Float) => unboxI(f.asInstanceOf[Float => Any](v), outType)).asInstanceOf[AnyRef]
        case LANE_D => ((d: Double) => unboxI(f.asInstanceOf[Double => Any](d), outType)).asInstanceOf[AnyRef]
        case _      => ((r: AnyRef) => unboxI(f.asInstanceOf[AnyRef => Any](r), outType)).asInstanceOf[AnyRef]
      }
    } else if (!inBoolean && !outBoolean) {
      (laneOf(inType) * 5 + outLaneOf(outType): @scala.annotation.switch) match {
        case 0 =>
          (
            (i: Int) =>
              try original.asInstanceOf[Int => Int](i)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 1 =>
          (
            (i: Int) =>
              try original.asInstanceOf[Int => Long](i)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 2 =>
          (
            (i: Int) =>
              try original.asInstanceOf[Int => Float](i)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 3 =>
          (
            (i: Int) =>
              try original.asInstanceOf[Int => Double](i)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 4 =>
          (
            (i: Int) =>
              try original.asInstanceOf[Int => AnyRef](i)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 5 =>
          (
            (l: Long) =>
              try original.asInstanceOf[Long => Int](l)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 6 =>
          (
            (l: Long) =>
              try original.asInstanceOf[Long => Long](l)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 7 =>
          (
            (l: Long) =>
              try original.asInstanceOf[Long => Float](l)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 8 =>
          (
            (l: Long) =>
              try original.asInstanceOf[Long => Double](l)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 9 =>
          (
            (l: Long) =>
              try original.asInstanceOf[Long => AnyRef](l)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 10 =>
          (
            (v: Float) =>
              try original.asInstanceOf[Float => Int](v)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 11 =>
          (
            (v: Float) =>
              try original.asInstanceOf[Float => Long](v)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 12 =>
          (
            (v: Float) =>
              try original.asInstanceOf[Float => Float](v)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 13 =>
          (
            (v: Float) =>
              try original.asInstanceOf[Float => Double](v)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 14 =>
          (
            (v: Float) =>
              try original.asInstanceOf[Float => AnyRef](v)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 15 =>
          (
            (d: Double) =>
              try original.asInstanceOf[Double => Int](d)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 16 =>
          (
            (d: Double) =>
              try original.asInstanceOf[Double => Long](d)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 17 =>
          (
            (d: Double) =>
              try original.asInstanceOf[Double => Float](d)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 18 =>
          (
            (d: Double) =>
              try original.asInstanceOf[Double => Double](d)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 19 =>
          (
            (d: Double) =>
              try original.asInstanceOf[Double => AnyRef](d)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 20 =>
          (
            (r: AnyRef) =>
              try original.asInstanceOf[AnyRef => Int](r)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 21 =>
          (
            (r: AnyRef) =>
              try original.asInstanceOf[AnyRef => Long](r)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 22 =>
          (
            (r: AnyRef) =>
              try original.asInstanceOf[AnyRef => Float](r)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case 23 =>
          (
            (r: AnyRef) =>
              try original.asInstanceOf[AnyRef => Double](r)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
        case _ =>
          (
            (r: AnyRef) =>
              try original.asInstanceOf[AnyRef => AnyRef](r)
              catch { case error: StreamError => throw StreamError.untrusted(error) }
          ).asInstanceOf[AnyRef]
      }
    } else if (inBoolean && outBoolean) {
      ((i: Int) => if (f.asInstanceOf[Boolean => Boolean](i != 0)) 1 else 0).asInstanceOf[AnyRef]
    } else if (inBoolean) {
      outLaneOf(outType) match {
        case OUT_I => ((i: Int) => f.asInstanceOf[Boolean => Int](i != 0)).asInstanceOf[AnyRef]
        case OUT_L => ((i: Int) => f.asInstanceOf[Boolean => Long](i != 0)).asInstanceOf[AnyRef]
        case OUT_F => ((i: Int) => f.asInstanceOf[Boolean => Float](i != 0)).asInstanceOf[AnyRef]
        case OUT_D => ((i: Int) => f.asInstanceOf[Boolean => Double](i != 0)).asInstanceOf[AnyRef]
        case _     => ((i: Int) => f.asInstanceOf[Boolean => AnyRef](i != 0)).asInstanceOf[AnyRef]
      }
    } else {
      laneOf(inType) match {
        case LANE_I => ((i: Int) => if (f.asInstanceOf[Int => Boolean](i)) 1 else 0).asInstanceOf[AnyRef]
        case LANE_L => ((l: Long) => if (f.asInstanceOf[Long => Boolean](l)) 1 else 0).asInstanceOf[AnyRef]
        case LANE_F => ((v: Float) => if (f.asInstanceOf[Float => Boolean](v)) 1 else 0).asInstanceOf[AnyRef]
        case LANE_D => ((d: Double) => if (f.asInstanceOf[Double => Boolean](d)) 1 else 0).asInstanceOf[AnyRef]
        case _      => ((r: AnyRef) => if (f.asInstanceOf[AnyRef => Boolean](r)) 1 else 0).asInstanceOf[AnyRef]
      }
    }
  }

  private[streams] def adaptPush[A](inType: JvmType, original: A => Any): AnyRef = {
    val f: A => Any = a =>
      try original(a)
      catch { case error: StreamError => throw StreamError.untrusted(error) }
    if (inType eq JvmType.Boolean)
      ((i: Int) => f.asInstanceOf[Boolean => Any](i != 0).asInstanceOf[AnyRef]).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Byte)
      ((i: Int) => f.asInstanceOf[Byte => Any](i.toByte).asInstanceOf[AnyRef]).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Short)
      ((i: Int) => f.asInstanceOf[Short => Any](i.toShort).asInstanceOf[AnyRef]).asInstanceOf[AnyRef]
    else if (inType eq JvmType.Char)
      ((i: Int) => f.asInstanceOf[Char => Any](i.toChar).asInstanceOf[AnyRef]).asInstanceOf[AnyRef]
    else f.asInstanceOf[AnyRef]
  }

  private def boxI(value: Int, logicalType: JvmType): AnyRef =
    if (logicalType eq JvmType.Boolean) Boolean.box(value != 0)
    else if (logicalType eq JvmType.Byte) Byte.box(value.toByte)
    else if (logicalType eq JvmType.Short) Short.box(value.toShort)
    else if (logicalType eq JvmType.Char) Char.box(value.toChar)
    else Int.box(value)

  private def unboxI(value: Any, logicalType: JvmType): Int =
    if (logicalType eq JvmType.Boolean) if (value.asInstanceOf[Boolean]) 1 else 0
    else if (logicalType eq JvmType.Char)
      value match {
        case c: java.lang.Character => c.charValue().toInt
        case n: java.lang.Number    => n.intValue()
      }
    else value.asInstanceOf[java.lang.Number].intValue()

  private[streams] def logicalBridgeFn(srcType: JvmType, dstType: JvmType): AnyRef = {
    val srcLane = laneOf(srcType)
    val dstLane = laneOf(dstType)
    if (dstType eq JvmType.Boolean) srcLane match {
      case LANE_I => ((i: Int) => if (i != 0) 1 else 0).asInstanceOf[AnyRef]
      case LANE_L => ((l: Long) => if (l != 0L) 1 else 0).asInstanceOf[AnyRef]
      case LANE_F => ((f: Float) => if (f != 0f) 1 else 0).asInstanceOf[AnyRef]
      case LANE_D => ((d: Double) => if (d != 0.0) 1 else 0).asInstanceOf[AnyRef]
      case _      => ((r: AnyRef) => if (r.asInstanceOf[Boolean]) 1 else 0).asInstanceOf[AnyRef]
    }
    else if (srcLane == LANE_I && dstLane == LANE_R)
      ((i: Int) => boxI(i, srcType)).asInstanceOf[AnyRef]
    else if (srcLane == dstLane)
      throw new IllegalArgumentException(s"No bridge required from $srcType to $dstType")
    else bridgeFn(srcLane, dstLane)
  }

  private[streams] def normalizeOutput[A](source: Reader.SyncReader[A], outType: JvmType): Reader.SyncReader[A] =
    if (source.jvmType eq outType) source
    else {
      val interpreter = new SyncInterpreter()
      try {
        val sourceType = source.jvmType
        interpreter.appendRead(source)
        val bridge =
          if (laneOf(sourceType) == laneOf(outType)) ((value: Int) => value).asInstanceOf[AnyRef]
          else logicalBridgeFn(sourceType, outType)
        interpreter.addAdaptedMap(sourceType, outType, bridge)
        interpreter.stableOutputType = outType
        interpreter.seal()
        interpreter.asInstanceOf[Reader.SyncReader[A]]
      } catch {
        case cause: Throwable =>
          try interpreter.close()
          catch { case cleanup: Throwable => StreamError.attachCleanupReplay(cause, cleanup) }
          throw cause
      }
    }

  private[streams] def elemTypeOfLane(lane: Lane): JvmType = lane match {
    case LANE_I => JvmType.Int
    case LANE_L => JvmType.Long
    case LANE_F => JvmType.Float
    case LANE_D => JvmType.Double
    case _      => JvmType.AnyRef
  }

  private[streams] def fromStream(stream: zio.blocks.streams.Stream[_, _]): SyncInterpreter = {
    val p = new SyncInterpreter()
    try {
      stream.asInstanceOf[zio.blocks.streams.Stream[Any, Any]].compileInterpreter(p)
      p.seal()
      p
    } catch {
      case cause: Throwable =>
        try p.close()
        catch { case cleanup: Throwable => StreamError.attachCleanupReplay(cause, cleanup) }
        throw cause
    }
  }

  private[streams] def fromStreamGuarded(
    stream: zio.blocks.streams.Stream[_, _],
    parent: SyncInterpreter
  ): SyncInterpreter = {
    val p = new SyncInterpreter()
    p.enclosingInterpreter = parent
    try {
      stream.asInstanceOf[zio.blocks.streams.Stream[Any, Any]].compileInterpreter(p)
      p.seal()
      p
    } catch {
      case cause: Throwable =>
        cause match {
          case StaleRead | _: StaleMaterialization =>
            val suppressed = cause.getSuppressed
            var i          = 0
            while (i < suppressed.length) {
              parent.recordCleanupFailure(cause, suppressed(i))
              i += 1
            }
          case _ => ()
        }
        try p.close()
        catch { case cleanup: Throwable => parent.recordCleanupFailure(cause, cleanup) }
        throw cause
    }
  }

  private[streams] def laneOf(pt: JvmType): Lane = pt match {
    case JvmType.Int | JvmType.Byte | JvmType.Short | JvmType.Char | JvmType.Boolean => LANE_I
    case JvmType.Long                                                                => LANE_L
    case JvmType.Float                                                               => LANE_F
    case JvmType.Double                                                              => LANE_D
    case _                                                                           => LANE_R
  }

  private[streams] def outLaneOf(pt: JvmType): Lane = pt match {
    case JvmType.Int | JvmType.Byte | JvmType.Short | JvmType.Char | JvmType.Boolean => OUT_I
    case JvmType.Long                                                                => OUT_L
    case JvmType.Float                                                               => OUT_F
    case JvmType.Double                                                              => OUT_D
    case _                                                                           => OUT_R
  }

  private[streams] def unsealed(source: Reader.SyncReader[_]): SyncInterpreter = {
    val p = new SyncInterpreter()
    p.appendRead(source)
    p
  }

  private val _bridgeTags = new Array[Int](25)
  private val _bridgeFns  = new Array[AnyRef](25)
  locally {
    def put(src: Int, dst: Int, fn: AnyRef): Unit = {
      _bridgeTags(src * 5 + dst) = OpTag.mapTag(src, dst)
      _bridgeFns(src * 5 + dst) = fn
    }
    put(0, 1, ((i: Int) => i.toLong).asInstanceOf[AnyRef]); put(0, 2, ((i: Int) => i.toFloat).asInstanceOf[AnyRef]);
    put(0, 3, ((i: Int) => i.toDouble).asInstanceOf[AnyRef]);
    put(0, 4, ((i: Int) => Int.box(i): AnyRef).asInstanceOf[AnyRef])
    put(1, 0, ((l: Long) => l.toInt).asInstanceOf[AnyRef]); put(1, 2, ((l: Long) => l.toFloat).asInstanceOf[AnyRef]);
    put(1, 3, ((l: Long) => l.toDouble).asInstanceOf[AnyRef]);
    put(1, 4, ((l: Long) => Long.box(l): AnyRef).asInstanceOf[AnyRef])
    put(2, 0, ((f: Float) => f.toInt).asInstanceOf[AnyRef]); put(2, 1, ((f: Float) => f.toLong).asInstanceOf[AnyRef]);
    put(2, 3, ((f: Float) => f.toDouble).asInstanceOf[AnyRef]);
    put(2, 4, ((f: Float) => Float.box(f): AnyRef).asInstanceOf[AnyRef])
    put(3, 0, ((d: Double) => d.toInt).asInstanceOf[AnyRef]); put(3, 1, ((d: Double) => d.toLong).asInstanceOf[AnyRef]);
    put(3, 2, ((d: Double) => d.toFloat).asInstanceOf[AnyRef]);
    put(3, 4, ((d: Double) => Double.box(d): AnyRef).asInstanceOf[AnyRef])
    put(4, 0, ((a: AnyRef) => a.asInstanceOf[java.lang.Number].intValue(): Int).asInstanceOf[AnyRef]);
    put(4, 1, ((a: AnyRef) => a.asInstanceOf[java.lang.Number].longValue(): Long).asInstanceOf[AnyRef]);
    put(4, 2, ((a: AnyRef) => a.asInstanceOf[java.lang.Number].floatValue(): Float).asInstanceOf[AnyRef]);
    put(4, 3, ((a: AnyRef) => a.asInstanceOf[java.lang.Number].doubleValue(): Double).asInstanceOf[AnyRef])
  }
}
