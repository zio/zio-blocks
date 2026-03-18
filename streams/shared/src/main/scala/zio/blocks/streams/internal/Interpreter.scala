package zio.blocks.streams.internal

import zio.blocks.streams.{JvmType, Stream => ZStream}
import zio.blocks.streams.internal.unsafeEvidence
import zio.blocks.streams.io.Reader
import StreamState.{stageStart => ss, incomingLen => il, stageEnd => se, outgoingLen => ol, outputLane => olane}

/**
 * An optimized interpreter that compiles a chain of stream operations (map,
 * filter, flatMap, source reads) into a flat-array representation for efficient
 * dispatch. This is the fallback compilation strategy for deep pipelines
 * (beyond `Stream.DepthCutoff`) and is used by [[Interpreter.fromStream]].
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
    val len                   = incomingLen; var i = 0
    var firstError: Throwable = null
    while (i < len) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        val r = incomingRef(i)
        if (r != null) {
          try r.asInstanceOf[Reader[Any]].close()
          catch {
            case t: Throwable =>
              if (firstError == null) firstError = t
              else firstError.addSuppressed(t)
          }
          incomingRef(i) = null
        }
      }
      i += 1
    }
    closed = true
    if (firstError != null) throw firstError
  }

  def isClosed: Boolean = closed

  override def jvmType: JvmType = outputType

  def outputType: JvmType = if (!closed) elemTypeOfLane(outputLane) else JvmType.AnyRef

  def read[A1 >: Any](sentinel: A1): A1 = {
    if (closed) return sentinel
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
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(using unsafeEvidence);
            if (sv == Long.MinValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(using unsafeEvidence);
            if (vl == Long.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(using unsafeEvidence);
            if (d == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(using unsafeEvidence);
            if (vd == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
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
    if (v.asInstanceOf[AnyRef] eq EndOfStream) -1 else (v.asInstanceOf[java.lang.Number].intValue() & 0xff)
  }

  override def readDouble(sentinel: Double)(using Any <:< Double): Double = {
    if (closed) return sentinel
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
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(using unsafeEvidence);
            if (sv == Long.MinValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(using unsafeEvidence);
            if (vl == Long.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(using unsafeEvidence);
            if (d == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(sentinel)(using unsafeEvidence);
            if (vd == sentinel) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
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
      if (emit) return vd
    }
    sentinel
  }

  override def readFloat(sentinel: Double)(using Any <:< Float): Double = {
    if (closed) return sentinel
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
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(using unsafeEvidence);
            if (sv == Long.MinValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(using unsafeEvidence);
            if (vl == Long.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(sentinel)(using unsafeEvidence);
            if (d == sentinel) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(using unsafeEvidence);
            if (vd == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
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
      if (emit) return vf.toDouble
    }
    sentinel
  }

  override def readInt(sentinel: Long)(using Any <:< Int): Long = {
    if (closed) return sentinel
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
          case 35 =>
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(using unsafeEvidence);
            if (sv == Long.MinValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(using unsafeEvidence);
            if (vl == Long.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(using unsafeEvidence);
            if (d == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(using unsafeEvidence);
            if (vd == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
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
      if (emit) return vi.toLong
    }
    sentinel
  }

  override def readLong(sentinel: Long)(using Any <:< Long): Long = {
    if (closed) return sentinel
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
            val sv = fn.asInstanceOf[Reader[Any]].readInt(Long.MinValue)(using unsafeEvidence);
            if (sv == Long.MinValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vi = sv.toInt
          case 36 =>
            vl = fn.asInstanceOf[Reader[Any]].readLong(Long.MaxValue)(using unsafeEvidence);
            if (vl == Long.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 37 =>
            val d = fn.asInstanceOf[Reader[Any]].readFloat(Double.MaxValue)(using unsafeEvidence);
            if (d == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
            else vf = d.toFloat
          case 38 =>
            vd = fn.asInstanceOf[Reader[Any]].readDouble(Double.MaxValue)(using unsafeEvidence);
            if (vd == Double.MaxValue) { if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen }
          case 39 =>
            val sv = fn.asInstanceOf[Reader[Any]].read[Any](EndOfStream);
            if (sv.asInstanceOf[AnyRef] eq EndOfStream) {
              if (!popAt(ip)) return sentinel; ip = stageStart - 1; iLen = incomingLen
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
      if (emit) return vl
    }
    sentinel
  }

  override def reset(): Unit = {
    val len = incomingLen; var i = 1
    while (i < len) {
      if (OpTag.isRead((incomingPrim(i) & 0xff).toInt)) {
        try incomingRef(i).asInstanceOf[Reader[Any]].close()
        catch { case _: Throwable => () }
      }
      i += 1
    }
    state = initialState
    outputLane = StreamState.outputLane(initialState)
    incomingRef(0).asInstanceOf[Reader[Any]].reset()
  }

  override def setLimit(n: Long): Boolean =
    allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader[Any]].setLimit(n)

  override def setRepeat(): Boolean =
    allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader[Any]].setRepeat()

  override def setSkip(n: Long): Boolean =
    allOpsAreMaps() && incomingRef(stageStart).asInstanceOf[Reader[Any]].setSkip(n)

  override def skip(n: Long): Unit = Reader.skipViaSentinel(this, n)

  private[streams] def addFilter[A](inLane: Lane)(f: A => Boolean): Unit = {
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

  private[streams] def addPush[A](inLane: Lane)(f: A => ?): Unit = {
    val tag = OpTag.pushTag(inLane).toLong
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

  private[streams] def appendRead(reader: Reader[?]): Unit = {
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
        return
      }
      i -= 1
    }
  }

  private inline def afterPush: Boolean = {
    val len = incomingLen
    len > 0 && OpTag.isPush((incomingPrim(len - 1) & 0xff).toInt)
  }

  private def allOpsAreMaps(): Boolean = {
    val len = incomingLen; var i = stageStart + 1
    while (i < len) { if ((incomingPrim(i) & 0xff) >= 25) return false; i += 1 }
    outgoingLen == stageEnd
  }

  private def ensureIncoming(): Int = {
    val len = incomingLen
    if (len > StreamState.MaxIndex)
      throw new IllegalStateException(
        s"Stream pipeline too deep: $len operations exceeds the maximum of ${StreamState.MaxIndex}. " +
          "Simplify the stream composition or reduce flatMap nesting depth."
      )
    if (len == incomingPrim.length) growIncoming()
    len
  }

  private def ensureOutgoing(): Int = {
    val len = outgoingLen
    if (len > StreamState.MaxIndex)
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
  }

  private def growOutgoing(): Unit = {
    val len = outgoingPrim.length
    val np  = new Array[Long](len * 2); System.arraycopy(outgoingPrim, 0, np, 0, len); outgoingPrim = np
    val nr  = new Array[AnyRef](len * 2); System.arraycopy(outgoingRef, 0, nr, 0, len); outgoingRef = nr
  }

  private def handlePush(innerStreamRef: AnyRef, fromOutgoing: Boolean, outgoingIdx: Int): Unit = {
    val outerOL    = outputLane
    val savedState = StreamState(stageStart, incomingLen, stageEnd, outgoingLen, outerOL)
    if (fromOutgoing) state = StreamState.withStageEnd(state, outgoingIdx + 1)
    val priorIL = incomingLen
    innerStreamRef.asInstanceOf[ZStream[Any, Any]].compileInterpreter(this)
    val innerOL = outputLane
    // Store saved state on the new Read op
    incomingPrim(priorIL) = (savedState << 8) | (incomingPrim(priorIL) & 0xff)
    state = StreamState.withStageStart(state, priorIL)
    if (innerOL != outerOL) {
      val idx = ensureIncoming()
      incomingPrim(idx) = bridgeTag(innerOL, outerOL).toLong
      incomingRef(idx) = bridgeFn(innerOL, outerOL)
      state = StreamState.withIncomingLen(state, idx + 1)
    }
    outputLane = outerOL
  }

  private inline def incomingLen: Int = il(state)

  private inline def outgoingLen: Int = ol(state)

  private def popAt(readIdx: Int): Boolean = {
    if (stageStart == 0) return false
    val saved = incomingPrim(readIdx) >>> 8
    state = StreamState(ss(saved), il(saved), se(saved), ol(saved), olane(saved))
    outputLane = olane(saved)
    true
  }

  private inline def stageEnd: Int = se(state)

  private inline def stageStart: Int = ss(state)
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

  private[streams] def apply(source: Reader[?]): Interpreter = {
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

  private[streams] def fromStream(stream: zio.blocks.streams.Stream[?, ?]): Interpreter = {
    val p = new Interpreter()
    stream.asInstanceOf[zio.blocks.streams.Stream[Any, Any]].compileInterpreter(p)
    p.seal()
    p
  }

  private[streams] def laneOf(pt: JvmType): Lane = pt match {
    case JvmType.Int | JvmType.Boolean => LANE_I
    case JvmType.Long                  => LANE_L
    case JvmType.Float                 => LANE_F
    case JvmType.Double                => LANE_D
    case _                             => LANE_R
  }

  private[streams] def outLaneOf(pt: JvmType): Lane = pt match {
    case JvmType.Int    => OUT_I
    case JvmType.Long   => OUT_L
    case JvmType.Float  => OUT_F
    case JvmType.Double => OUT_D
    case _              => OUT_R
  }

  private[streams] def unsealed(source: Reader[?]): Interpreter = {
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
