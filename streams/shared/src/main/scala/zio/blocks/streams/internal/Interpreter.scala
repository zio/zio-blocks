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

import zio.blocks.streams.{JvmType, Stream => ZStream}
import zio.blocks.streams.io.Reader
import StreamState.{stageStart => ss, incomingLen => il, stageEnd => se, outgoingLen => ol, outputLane => olane}

/**
 * An optimized interpreter that compiles a chain of stream operations (map,
 * filter, flatMap, source reads) into a flat-array representation for efficient
 * dispatch. This is the fallback compilation strategy for deep pipelines
 * (beyond `Stream.DepthCutoff`) and is used by `Interpreter.fromStream`.
 *
 * Internally, each op is a (Long, AnyRef) pair stored at the same index in two
 * parallel arrays. The Long's bottom 8 bits hold the [[OpTag]]; the upper 56
 * bits hold [[StreamState]] for Read/Push ops.
 */
final class Interpreter private[internal] () extends Reader[Any] {
  import Interpreter._

  private var incomingPrim: Array[Long]   = new Array[Long](8)
  private var incomingRef: Array[AnyRef]  = new Array[AnyRef](8)
  private var outgoingPrim: Array[Long]   = null
  private var outgoingRef: Array[AnyRef]  = null
  private[streams] var state: StreamState = StreamState.empty
  private[streams] var outputLane: Lane   = 0
  private var closed: Boolean             = false
  private var initialState: StreamState   = StreamState.empty

  def close(): Unit = {
    if (closed) return
    val len                   = incomingLen
    val structuralLen         = il(initialState)
    var firstError: Throwable = null
    var i                     = 0
    while (i < len) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        val r = incomingRef(i)
        if (r != null) {
          // Close every reader (structural + transient), accumulating failures
          // losslessly. We catch ALL throwables here (not just `isCatchable`)
          // ON PURPOSE: this is a cleanup loop, so every reader must get a chance
          // to close (no leaks) before any failure propagates. Nothing is
          // swallowed — `firstError` is rethrown below, so a fatal/control
          // throwable still propagates, just after the other readers are closed.
          try r.asInstanceOf[Reader[Any]].close()
          catch { case t: Throwable => firstError = combineFailures(firstError, t) }
        }
      }
      // Only NULL transient runtime readers (those pushed past the sealed
      // structural prefix at indices >= structuralLen). Structural readers must
      // survive close() so a later reset()/`repeated` can rewind the source
      // (`incomingRef(0).reset()`) instead of NPEing on a nulled ref.
      if (i >= structuralLen) incomingRef(i) = null
      i += 1
    }
    state = initialState
    outputLane = StreamState.outputLane(initialState)
    closed = true
    if (firstError != null) throw firstError
  }

  def isClosed: Boolean = closed

  override def jvmType: JvmType = outputType

  def outputType: JvmType = if (!closed) elemTypeOfLane(outputLane) else JvmType.AnyRef

  def read[A1 >: Any](sentinel: A1): A1 = {
    if (closed) { markReadEOF(); return sentinel }
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
            handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 31 =>
            handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 32 =>
            handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 33 =>
            handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 34 =>
            handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 35 =>
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(unsafeEvidence);
            if (sv == Long.MinValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(unsafeEvidence);
            if (longEOF(fn.asInstanceOf[Reader[Any]], vl, Long.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(unsafeEvidence);
            if (d == Double.MaxValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(unsafeEvidence);
            if (doubleEOF(fn.asInstanceOf[Reader[Any]], vd, Double.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vr = sv.asInstanceOf[AnyRef]
          case other => throw new IllegalStateException(s"Unknown tag: $other")
        };
        ip += 1
      }
      var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
      if (oLen > sEnd) {
        val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
        while (j < oLen && emit) {
          val fn = oRef(j);
          ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
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
          j += 1
        }
      }
      if (emit) return ((outputLane: @scala.annotation.switch) match {
        case 0 => vi; case 1 => vl; case 2 => vf; case 3 => vd; case _ => vr
      }).asInstanceOf[A1]
    }
    sentinel
  }

  override def readByte(): Int = {
    val v = read[Any](EndOfStream)
    if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else Reader.anyToLowByte(v)
  }

  override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Double = {
    if (closed) { markReadEOF(); return sentinel }
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
            handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 31 =>
            handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 32 =>
            handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 33 =>
            handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 34 =>
            handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 35 =>
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(unsafeEvidence);
            if (sv == Long.MinValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(unsafeEvidence);
            if (longEOF(fn.asInstanceOf[Reader[Any]], vl, Long.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(unsafeEvidence);
            if (d == Double.MaxValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(sentinel)(unsafeEvidence);
            if (doubleEOF(fn.asInstanceOf[Reader[Any]], vd, sentinel)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vr = sv.asInstanceOf[AnyRef]
          case other => throw new IllegalStateException(s"Unknown tag: $other")
        };
        ip += 1
      }
      var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
      if (oLen > sEnd) {
        val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
        while (j < oLen && emit) {
          val fn = oRef(j);
          ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
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
          j += 1
        }
      }
      if (emit) { markReadValue(); return vd }
    }
    sentinel
  }

  override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Double = {
    if (closed) { markReadEOF(); return sentinel }
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
            handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 31 =>
            handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 32 =>
            handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 33 =>
            handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 34 =>
            handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 35 =>
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(unsafeEvidence);
            if (sv == Long.MinValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(unsafeEvidence);
            if (longEOF(fn.asInstanceOf[Reader[Any]], vl, Long.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(sentinel)(unsafeEvidence);
            if (d == sentinel) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(unsafeEvidence);
            if (doubleEOF(fn.asInstanceOf[Reader[Any]], vd, Double.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vr = sv.asInstanceOf[AnyRef]
          case other => throw new IllegalStateException(s"Unknown tag: $other")
        };
        ip += 1
      }
      var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
      if (oLen > sEnd) {
        val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
        while (j < oLen && emit) {
          val fn = oRef(j);
          ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
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
          j += 1
        }
      }
      // The `Float` lane is sentinel-safe (a widened `Float` can never equal the
      // `Double.MaxValue` sentinel), so consumers disambiguate EOF with a raw
      // `!= sentinel` and never consult `lastReadWasEOF`. Skip the per-element
      // `markReadValue()` write to keep this hot loop lean (see `readInt`).
      if (emit) return vf.toDouble
    }
    sentinel
  }

  // PRECONDITION: only valid when `outputLane == LANE_I` (i.e. `jvmType == Int`).
  // The emit path returns the int register `vi`; calling this on a ref-lane
  // interpreter (e.g. a boxed `Byte` output, `outputLane == LANE_R`) would return
  // the wrong register — silent corruption (BUG-N4). `jvmType` reports `AnyRef`
  // for such output so direct sinks use `read`; delegating wrappers
  // (`Reader.FlatMappedBase`/`ConcatReader`) MUST dispatch on the child's
  // `jvmType` and pull ref-lane children via boxed `read`, never `readInt`.
  override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Long = {
    if (closed) { markReadEOF(); return sentinel }
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
            handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 31 =>
            handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 32 =>
            handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 33 =>
            handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 34 =>
            handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 35 =>
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(unsafeEvidence);
            if (sv == Long.MinValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(unsafeEvidence);
            if (longEOF(fn.asInstanceOf[Reader[Any]], vl, Long.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(unsafeEvidence);
            if (d == Double.MaxValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(unsafeEvidence);
            if (doubleEOF(fn.asInstanceOf[Reader[Any]], vd, Double.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vr = sv.asInstanceOf[AnyRef]
          case other => throw new IllegalStateException(s"Unknown tag: $other")
        }
        ip += 1
      }
      var emit = true
      val oLen = outgoingLen; val sEnd = stageEnd
      if (oLen > sEnd) {
        val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
        while (j < oLen && emit) {
          val fn = oRef(j);
          ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
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
          j += 1
        }
      }
      // The `Int` lane is sentinel-safe (a widened `Int` can never equal the
      // `Long.MinValue` sentinel), so consumers disambiguate EOF with a raw
      // `!= sentinel` and never consult `lastReadWasEOF`. Skipping the
      // per-element `markReadValue()` write here keeps this hot loop lean
      // (interpreter-heavy benchmarks, e.g. mixed pipelines).
      if (emit) return vi.toLong
    }
    sentinel
  }

  override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Long = {
    if (closed) { markReadEOF(); return sentinel }
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
            handlePush(fn.asInstanceOf[Int => AnyRef](vi), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 31 =>
            handlePush(fn.asInstanceOf[Long => AnyRef](vl), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 32 =>
            handlePush(fn.asInstanceOf[Float => AnyRef](vf), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 33 =>
            handlePush(fn.asInstanceOf[Double => AnyRef](vd), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen;
          case 34 =>
            handlePush(fn.asInstanceOf[AnyRef => AnyRef](vr), false, ip); iPrim = incomingPrim; iRef = incomingRef;
            ip = stageStart - 1; iLen = incomingLen
          case 35 =>
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(unsafeEvidence);
            if (sv == Long.MinValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(unsafeEvidence);
            if (longEOF(fn.asInstanceOf[Reader[Any]], vl, Long.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(unsafeEvidence);
            if (d == Double.MaxValue) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(unsafeEvidence);
            if (doubleEOF(fn.asInstanceOf[Reader[Any]], vd, Double.MaxValue)) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) { markReadEOF(); return sentinel }; ip = stageStart - 1; iLen = incomingLen
            } else vr = sv.asInstanceOf[AnyRef]
          case other => throw new IllegalStateException(s"Unknown tag: $other")
        };
        ip += 1
      }
      var emit = true; val oLen = outgoingLen; val sEnd = stageEnd
      if (oLen > sEnd) {
        val oPrim = outgoingPrim; val oRef = outgoingRef; var j = sEnd;
        while (j < oLen && emit) {
          val fn = oRef(j);
          ((oPrim(j) & 0xff).toInt: @scala.annotation.switch) match {
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
          j += 1
        }
      }
      if (emit) { markReadValue(); return vl }
    }
    sentinel
  }

  override def reset(): Unit = {
    val len                   = incomingLen
    val structuralLen         = il(initialState)
    var firstError: Throwable = null
    // Close + null ONLY the transient runtime readers (indices >= structuralLen)
    // pushed by `flatMap` during the previous run; the structural prefix
    // survives. Failures are accumulated losslessly (cleanup loop — catch all,
    // rethrow below, never swallow).
    var i = structuralLen
    while (i < len) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        val r = incomingRef(i)
        if (r != null) {
          try r.asInstanceOf[Reader[Any]].close()
          catch { case t: Throwable => firstError = combineFailures(firstError, t) }
        }
      }
      incomingRef(i) = null
      i += 1
    }
    state = initialState
    outputLane = StreamState.outputLane(initialState)
    closed = false
    markReadValue()
    if (firstError != null) throw firstError
    // Rewind the structural source; for a chained segment this cascades reset()
    // through the upstream segments.
    incomingRef(0).asInstanceOf[Reader[Any]].reset()
  }

  override def setLimit(n: Long): Boolean =
    allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader[Any]].setLimit(n)

  override def setRepeat(): Boolean =
    allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader[Any]].setRepeat()

  override def setSkip(n: Long): Boolean =
    allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader[Any]].setSkip(n)

  override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)

  /**
   * Insert a lane bridge if the value produced by the preceding op
   * (`outputLane`) is not in the lane the next op will read (`inLane`).
   *
   * The static element lane of a downstream op (derived from its Scala type)
   * can differ from the runtime lane a source `Reader` writes: e.g.
   * `fromIterable` of a primitive yields a boxed reader (`jvmType == AnyRef`,
   * `LANE_R`) but a subsequent primitive `map`/`filter`/`flatMap` reads the
   * primitive register. Without this bridge the op would read a stale,
   * never-written primitive register (silent corruption — every element became
   * `0`). Bridging unboxes `LANE_R -> primitive` (and converts between
   * primitives) using the same `_bridgeFns` table that `handlePush` uses at
   * flatMap boundaries.
   *
   * No-op (and no runtime cost) on the common matched-lane path, e.g. a
   * primitive-specialized source feeding a same-lane op.
   */
  private def reconcileLane(inLane: Lane): Unit =
    if (outputLane != inLane) {
      val tag = bridgeTag(outputLane, inLane).toLong
      val fn  = bridgeFn(outputLane, inLane)
      if (afterPush) {
        val idx = ensureOutgoing()
        outgoingPrim(idx) = tag; outgoingRef(idx) = fn
        state = StreamState.withOutgoingLen(state, idx + 1)
      } else {
        val idx = ensureIncoming()
        incomingPrim(idx) = tag; incomingRef(idx) = fn
        state = StreamState.withIncomingLen(state, idx + 1)
      }
      outputLane = inLane
    }

  private[streams] def addFilter[A](inLane: Lane)(f: A => Boolean): Unit = {
    reconcileLane(inLane)
    val tag = OpTag.filterTag(inLane).toLong
    val fn  = f.asInstanceOf[AnyRef]
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

  private[streams] def addMap[A, B](inLane: Lane, outLane: Lane)(f: A => B): Unit = {
    reconcileLane(inLane)
    val tag = OpTag.mapTag(inLane, outLane).toLong
    val fn  = f.asInstanceOf[AnyRef]
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
  }

  // A push (flatMap) op carries TWO lanes: the input lane (`inLane`, packed as
  // the op tag 30..34) and the flatMap's declared OUTPUT lane B (`outLane`,
  // packed into bits 8..15). `handlePush` reads `outLane` back to bridge the
  // inner stream's natural output lane to B — without it a type-changing
  // flatMap throws ClassCastException (AdversarialDeepFlatMapSpec). Setting
  // `outputLane = outLane` here also makes it safe for the segmented compiler to
  // seal a segment immediately after a push (a deep flatMap chain otherwise
  // overflows the 13-bit op-index fields — see compileInterpreterSegmented).
  private[streams] def addPush[A](inLane: Lane, outLane: Lane)(f: A => Any): Unit = {
    reconcileLane(inLane)
    val tag = OpTag.pushTag(inLane).toLong | (outLane.toLong << 8)
    val fn  = f.asInstanceOf[AnyRef]
    if (afterPush) {
      val idx = ensureOutgoing()
      outgoingPrim(idx) = tag; outgoingRef(idx) = fn
      state = StreamState.withOutgoingLen(state, idx + 1)
    } else {
      val idx = ensureIncoming()
      incomingPrim(idx) = tag; incomingRef(idx) = fn
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = outLane
  }

  private[streams] def appendRead(reader: Reader[_]): Unit = {
    val lane = laneOf(reader.jvmType)
    val idx  = ensureIncoming()
    incomingPrim(idx) = OpTag.readTag(lane).toLong
    incomingRef(idx) = reader.asInstanceOf[AnyRef]
    state = StreamState.withIncomingLen(state, idx + 1)
    outputLane = lane
  }

  private[streams] def seal(): Unit =
    initialState = StreamState(stageStart, incomingLen, stageEnd, outgoingLen, outputLane)

  private[streams] def wrapLastRead(f: Reader[Any] => Reader[Any]): Unit = {
    var i = incomingLen - 1
    while (i >= 0) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        val oldReader = incomingRef(i).asInstanceOf[Reader[Any]]
        val newReader = f(oldReader)
        val newLane   = laneOf(newReader.jvmType)
        incomingRef(i) = newReader.asInstanceOf[AnyRef]
        incomingPrim(i) = (incomingPrim(i) & ~0xffL) | OpTag.readTag(newLane).toLong
        // When this read is the segment's most recent op, the segment's output
        // lane IS the read's lane — refresh it if the wrapper changed the lane
        // (e.g. a mixed-lane concat wrapping an Int read into a ref-lane
        // ConcatReader). A stale `outputLane` fabricates a bogus bridge that
        // copies a register the pipeline never wrote, silently corrupting
        // every element (BUG-R5-04). Compile-time only.
        if (i == incomingLen - 1 && outgoingLen == stageEnd) outputLane = newLane
        return
      }
      i -= 1
    }
  }

  private def afterPush: Boolean = {
    val len = incomingLen
    len > 0 && OpTag.isPush((incomingPrim(len - 1) & 0xff).toInt)
  }

  private def allOpsAreMaps(): Boolean = {
    val len = incomingLen; var i = stageStart + 1
    while (i < len) { if ((incomingPrim(i) & 0xff) >= 25) return false; i += 1 }
    outgoingLen == stageEnd
  }

  // The op index is packed into `StreamState`'s 13-bit fields, so a single
  // interpreter segment can address at most `MaxIndex` (8191) ops. The guard is
  // `>=`, not `>`: appending when `len == MaxIndex` would store `MaxIndex + 1`,
  // which does not fit in 13 bits and would SILENTLY WRAP (corrupting the read
  // loop into a hang) rather than fail. Throwing here keeps the failure loud.
  // Normal compilation never reaches this because `Stream.compileInterpreterSegmented`
  // seals at `SegmentBudget` (< MaxIndex) and chains a fresh segment; reaching
  // this guard indicates an un-segmented path (e.g. an extremely deep `flatMap`
  // inner stream compiled inline).
  private def ensureIncoming(): Int = {
    val len = incomingLen
    if (len >= StreamState.MaxIndex)
      throw new IllegalStateException(
        s"Stream interpreter segment too deep: $len operations reaches the maximum of ${StreamState.MaxIndex}. " +
          "Simplify the stream composition or reduce flatMap nesting depth."
      )
    if (len == incomingPrim.length) growIncoming()
    len
  }

  private def ensureOutgoing(): Int = {
    val len = outgoingLen
    if (len >= StreamState.MaxIndex)
      throw new IllegalStateException(
        s"Stream interpreter segment too deep: $len outgoing operations reaches the maximum of ${StreamState.MaxIndex}. " +
          "Simplify the stream composition or reduce flatMap nesting depth."
      )
    if (outgoingPrim == null) {
      // First outgoing op: size from the segmented compiler's hint (deep
      // pipelines otherwise pay O(log n) doubling copies up to thousands of
      // ops), falling back to the small default for shallow pipelines.
      val cap = if (capacityHint > 4) capacityHint else 4
      outgoingPrim = new Array[Long](cap); outgoingRef = new Array[AnyRef](cap)
    } else if (len == outgoingPrim.length) growOutgoing()
    len
  }

  // Capacity hint from `Stream.compileInterpreterSegmented` (number of spine
  // ops remaining for this segment, plus slack). Both op arrays use it lazily:
  // the FIRST growth (or first outgoing append) jumps straight to the hinted
  // capacity instead of doubling step by step — a deep segment (8k ops, or 8k
  // runtime-pushed transient reads in a deep flatMap chain) otherwise pays
  // O(log n) doubling copies, while a segment that stays small never grows and
  // wastes nothing.
  private var capacityHint: Int = 0

  /**
   * Sets the expected op-count hint for this (unsealed) segment. Compile-time
   * only.
   */
  private[streams] def presize(n: Int): Unit =
    capacityHint = math.min(n, StreamState.MaxIndex + 1)

  private def growIncoming(): Unit = {
    val len = incomingPrim.length
    val cap = math.max(len * 2, capacityHint)
    val np  = new Array[Long](cap); System.arraycopy(incomingPrim, 0, np, 0, len); incomingPrim = np
    val nr  = new Array[AnyRef](cap); System.arraycopy(incomingRef, 0, nr, 0, len); incomingRef = nr
  }

  private def growOutgoing(): Unit = {
    val len = outgoingPrim.length
    val cap = math.max(len * 2, capacityHint)
    val np  = new Array[Long](cap); System.arraycopy(outgoingPrim, 0, np, 0, len); outgoingPrim = np
    val nr  = new Array[AnyRef](cap); System.arraycopy(outgoingRef, 0, nr, 0, len); outgoingRef = nr
  }

  private def handlePush(innerStreamRef: AnyRef, fromOutgoing: Boolean, opIdx: Int): Unit = {
    val outerOL = outputLane
    // The flatMap's declared output lane B, packed into the push op's bits 8..15
    // by `addPush`. The inner stream is bridged to THIS lane (not to `outerOL`,
    // the flatMap's INPUT lane A): a type-changing flatMap (e.g. Int -> String)
    // otherwise pushes a B value down a lane typed for A and throws
    // ClassCastException (AdversarialDeepFlatMapSpec).
    val pushOutLane =
      (((if (fromOutgoing) outgoingPrim(opIdx) else incomingPrim(opIdx)) >>> 8) & 0xff).toInt
    val savedState = StreamState(stageStart, incomingLen, stageEnd, outgoingLen, outerOL)
    if (fromOutgoing) state = StreamState.withStageEnd(state, opIdx + 1)
    val priorIL = incomingLen
    innerStreamRef.asInstanceOf[ZStream[Any, Any]].compileInterpreter(this)
    val innerOL = outputLane
    // Store saved state on the new Read op
    incomingPrim(priorIL) = (savedState << 8) | (incomingPrim(priorIL) & 0xff)
    state = StreamState.withStageStart(state, priorIL)
    if (innerOL != pushOutLane) {
      val idx = ensureIncoming()
      incomingPrim(idx) = bridgeTag(innerOL, pushOutLane).toLong
      incomingRef(idx) = bridgeFn(innerOL, pushOutLane)
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = outerOL
  }

  private def incomingLen: Int = il(state)

  private def outgoingLen: Int = ol(state)

  // Pops a finished pushed inner stage (e.g. a `flatMap` inner stream) and
  // restores the saved outer state stored on the read op at `readIdx`. The
  // inner stage occupies incoming indices `[readIdx, incomingLen)`; once the
  // outer state is restored those slots become unreachable, so every Read-tagged
  // reader in that range MUST be closed here — otherwise each inner resource
  // except the last leaks, because the next `handlePush` overwrites the slot
  // without finalizing what it held (BUG-A / AdversarialFlatMapResourceSpec).
  // Close failures are accumulated (first primary, rest suppressed) and rethrown
  // after state is restored, mirroring `close()` and never swallowing an error.
  private def popAt(readIdx: Int): Boolean = {
    if (stageStart == 0) return false
    val saved                 = incomingPrim(readIdx) >>> 8
    val len                   = incomingLen
    var firstError: Throwable = null
    var i                     = readIdx
    while (i < len) {
      val ref = incomingRef(i)
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt) && (ref != null)) {
        try ref.asInstanceOf[Reader[Any]].close()
        catch { case t: Throwable => firstError = combineFailures(firstError, t) }
      }
      // Null every stale ref (readers and bridge fns) so the discarded inner
      // pipeline is not retained and a reader is never closed twice.
      incomingRef(i) = null
      i += 1
    }
    state = StreamState(ss(saved), il(saved), se(saved), ol(saved), olane(saved))
    outputLane = olane(saved)
    if (firstError != null) throw firstError
    true
  }

  private def stageEnd: Int = se(state)

  private def stageStart: Int = ss(state)

  /** Number of incoming-array ops currently compiled into this segment. */
  private[streams] def incomingCount: Int = incomingLen

  /**
   * Number of outgoing-array ops (post-`flatMap`) compiled into this segment.
   */
  private[streams] def outgoingCount: Int = outgoingLen
}

private[streams] object Interpreter {
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

  private[streams] def apply(source: Reader[_]): Interpreter = {
    val p = new Interpreter()
    p.appendRead(source)
    p.seal()
    p
  }

  private[streams] def bridgeFn(srcLane: Lane, dstLane: Lane): AnyRef = _bridgeFns(srcLane * 5 + dstLane)

  private[streams] def bridgeTag(srcLane: Lane, dstLane: Lane): Int = _bridgeTags(srcLane * 5 + dstLane)

  private[streams] def elemTypeOfLane(lane: Lane): JvmType = lane match {
    case LANE_I => JvmType.Int
    case LANE_L => JvmType.Long
    case LANE_F => JvmType.Float
    case LANE_D => JvmType.Double
    case _      => JvmType.AnyRef
  }

  /**
   * Conservative per-segment op budget; segmentation seals and chains a fresh
   * interpreter before this is reached, leaving headroom below `MaxIndex` for
   * multi-op nodes and runtime `flatMap` inner appends.
   */
  private[streams] final val SegmentBudget = 8000

  /** Creates a fresh, unsealed interpreter (used to start a new segment). */
  private[streams] def unsealed(): Interpreter = new Interpreter()

  // Compiles a (possibly very deep) stream into a CHAIN of bounded interpreter
  // segments via `Stream.compileInterpreterSegmented`, returning the final
  // segment. Each segment is itself a `Reader`, so a deep linear pipeline
  // becomes a short chain of flat-loop interpreters — stack-safe at both compile
  // time and run time and not bounded by a single interpreter's `MaxIndex` cap
  // (BUG-C / AdversarialCompileStackSafetySpec).
  private[streams] def fromStream(stream: zio.blocks.streams.Stream[_, _]): Interpreter =
    zio.blocks.streams.Stream.compileInterpreterSegmented(stream.asInstanceOf[zio.blocks.streams.Stream[Any, Any]])

  // ITER-9: `laneOf` must agree with `outLaneOf` for every type, otherwise a
  // value produced into one lane is later read from a different lane, inserting a
  // bogus numeric bridge and throwing ClassCastException. Boolean is the only
  // type where they previously disagreed: `outLaneOf(Boolean) = OUT_R` (map
  // stores a boxed Boolean in the ref lane), so `laneOf(Boolean)` must be LANE_R
  // (read from the ref lane), NOT LANE_I. (Byte/Short/Char are already both R.)
  private[streams] def laneOf(pt: JvmType): Lane = pt match {
    case JvmType.Int    => LANE_I
    case JvmType.Long   => LANE_L
    case JvmType.Float  => LANE_F
    case JvmType.Double => LANE_D
    case _              => LANE_R
  }

  private[streams] def outLaneOf(pt: JvmType): Lane = pt match {
    case JvmType.Int    => OUT_I
    case JvmType.Long   => OUT_L
    case JvmType.Float  => OUT_F
    case JvmType.Double => OUT_D
    case _              => OUT_R
  }

  private[streams] def unsealed(source: Reader[_]): Interpreter = {
    val p = new Interpreter()
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
