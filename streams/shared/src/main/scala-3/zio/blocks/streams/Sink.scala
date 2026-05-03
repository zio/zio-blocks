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

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.internal.{EndOfStream, Interpreter, StreamError, unsafeEvidence}
import zio.blocks.streams.io.Reader

/**
 * A consumer of elements of type `A` that may fail with `E` and, on completion,
 * produces a result `Z`. Sinks are consumed by passing them to [[Stream.run]]:
 * the stream compiles into a [[io.Reader]] and calls [[drain]] once to produce
 * the final result.
 *
 * @tparam E
 *   Error type.
 * @tparam A
 *   Input element type (contravariant).
 * @tparam Z
 *   Result type.
 */
abstract class Sink[+E, -A, +Z] {

  /**
   * Pre-processes incoming elements with `g` before feeding them to this sink.
   */
  def contramap[A2](g: A2 => A): Sink[E, A2, Z] = new Sink.Contramapped(this, g)

  /** Transforms the result of this sink with `f`. */
  def map[Z2](f: Z => Z2): Sink[E, A, Z2] = new Sink.Mapped(this, f)

  /** Transforms the error channel of this sink with `f`. */
  inline def mapError[E2](f: E => E2): Sink[E2, A, Z] =
    scala.compiletime.summonFrom {
      case _: (E =:= Nothing) => this.asInstanceOf[Sink[E2, A, Z]]
      case _                  => Sink.mkErrorMapped(this, f)
    }

  /**
   * Drains the reader, consuming all elements and producing a result Z.
   */
  private[streams] def drain(reader: Reader[?]): Z
}

/**
 * Companion object for [[Sink]]. Provides factory constructors for common
 * consumers: `fail`, `create`, `drain`, `count`, `collectAll`, `foldLeft`,
 * `foreach`, `head`, `last`, `take`, `exists`, `forall`, `find`,
 * `fromOutputStream`, `fromJavaWriter`, `sumInt`, `sumLong`, `sumFloat`, and
 * `sumDouble`.
 */
object Sink {

  /** A sink that collects all elements into a [[Chunk]]. */
  def collectAll[A]: Sink[Nothing, A, Chunk[A]] =
    new Sink[Nothing, A, Chunk[A]] {
      private[streams] def drain(reader: Reader[?]): Chunk[A] =
        reader.jvmType match {
          case JvmType.Int    => collectAllInt(reader).asInstanceOf[Chunk[A]]
          case JvmType.Long   => collectAllLong(reader).asInstanceOf[Chunk[A]]
          case JvmType.Float  => collectAllFloat(reader).asInstanceOf[Chunk[A]]
          case JvmType.Double => collectAllDouble(reader).asInstanceOf[Chunk[A]]
          case JvmType.Byte   => collectAllByte(reader).asInstanceOf[Chunk[A]]
          case _              =>
            val b = ChunkBuilder.make[A](16)
            var v = reader.read(EndOfStream)
            while (v.asInstanceOf[AnyRef] ne EndOfStream) { b += v.asInstanceOf[A]; v = reader.read(EndOfStream) }
            b.result()
        }
    }

  /** A sink that counts the number of elements consumed. */
  val count: Sink[Nothing, Any, Long] =
    new Sink[Nothing, Any, Long] {
      private[streams] def drain(reader: Reader[?]): Long = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          var n = 0L; val s = Long.MinValue; while (reader.readInt(s)(using unsafeEvidence) != s) n += 1L; n
        } else if (et eq JvmType.Long) {
          var n = 0L; val s = Long.MaxValue; while (reader.readLong(s)(using unsafeEvidence) != s) n += 1L; n
        } else if (et eq JvmType.Float) {
          var n = 0L; val s = Double.MaxValue; while (reader.readFloat(s)(using unsafeEvidence) != s) n += 1L; n
        } else if (et eq JvmType.Double) {
          var n = 0L; val s = Double.MaxValue; while (reader.readDouble(s)(using unsafeEvidence) != s) n += 1L; n
        } else if (et eq JvmType.Byte) {
          var n = 0L; val s = Long.MinValue; while (reader.readInt(s)(using unsafeEvidence) != s) n += 1L; n
        } else { var n = 0L; while (reader.read(EndOfStream).asInstanceOf[AnyRef] ne EndOfStream) n += 1L; n }
      }
    }

  /**
   * Creates a sink from a function that directly consumes a [[Reader]]. Use
   * this when none of the built-in sinks (`foldLeft`, `foreach`, `collectAll`,
   * etc.) meet your needs.
   */
  def create[E, A, Z](f: Reader[A] => Z): Sink[E, A, Z] =
    new Sink[E, A, Z] {
      private[streams] def drain(reader: Reader[?]): Z =
        f(reader.asInstanceOf[Reader[A]])
    }

  /** A sink that consumes all elements and discards them, returning `Unit`. */
  val drain: Sink[Nothing, Any, Unit] =
    new Sink[Nothing, Any, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          val s = Long.MinValue; while (reader.readInt(s)(using unsafeEvidence) != s) ()
        } else if (et eq JvmType.Long) {
          val s = Long.MaxValue; while (reader.readLong(s)(using unsafeEvidence) != s) ()
        } else if (et eq JvmType.Float) {
          val s = Double.MaxValue; while (reader.readFloat(s)(using unsafeEvidence) != s) ()
        } else if (et eq JvmType.Double) {
          val s = Double.MaxValue; while (reader.readDouble(s)(using unsafeEvidence) != s) ()
        } else if (et eq JvmType.Byte) {
          val s = Long.MinValue; while (reader.readInt(s)(using unsafeEvidence) != s) ()
        } else {
          while (reader.read(EndOfStream).asInstanceOf[AnyRef] ne EndOfStream) ()
        }
      }
    }

  /** Returns `true` if any element satisfies `pred`, short-circuiting. */
  def exists[A](pred: A => Boolean): Sink[Nothing, A, Boolean] =
    new Sink[Nothing, A, Boolean] {
      private[streams] def drain(reader: Reader[?]): Boolean = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          val fi = pred.asInstanceOf[Int => Boolean]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { if (fi(v.toInt)) return true; v = reader.readInt(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Long) {
          val fl = pred.asInstanceOf[Long => Boolean]; val s = Long.MaxValue
          var v  = reader.readLong(s)(using unsafeEvidence)
          while (v != s) { if (fl(v)) return true; v = reader.readLong(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Float) {
          val ff = pred.asInstanceOf[Float => Boolean]; val s = Double.MaxValue
          var v  = reader.readFloat(s)(using unsafeEvidence)
          while (v != s) { if (ff(v.toFloat)) return true; v = reader.readFloat(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Double) {
          val fd = pred.asInstanceOf[Double => Boolean]; val s = Double.MaxValue
          var v  = reader.readDouble(s)(using unsafeEvidence)
          while (v != s) { if (fd(v)) return true; v = reader.readDouble(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Byte) {
          val fb = pred.asInstanceOf[Byte => Boolean]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { if (fb(v.toByte)) return true; v = reader.readInt(s)(using unsafeEvidence) }
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            if (pred(v.asInstanceOf[A])) return true
            v = reader.read(EndOfStream)
          }
        }
        false
      }
    }

  /**
   * A sink that immediately fails with error `e` without consuming any
   * elements.
   */
  def fail[E](e: E): Sink[E, Any, Nothing] =
    new Sink[E, Any, Nothing] {
      private[streams] def drain(reader: Reader[?]): Nothing =
        throw new StreamError(e)
    }

  /** Returns the first element satisfying `pred`, or `None`. */
  def find[A](pred: A => Boolean): Sink[Nothing, A, Option[A]] =
    new Sink[Nothing, A, Option[A]] {
      private[streams] def drain(reader: Reader[?]): Option[A] = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          val fi = pred.asInstanceOf[Int => Boolean]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) {
            if (fi(v.toInt)) return Some(v.toInt.asInstanceOf[A]); v = reader.readInt(s)(using unsafeEvidence)
          }
          None
        } else if (et eq JvmType.Long) {
          val fl = pred.asInstanceOf[Long => Boolean]; val s = Long.MaxValue
          var v  = reader.readLong(s)(using unsafeEvidence)
          while (v != s) { if (fl(v)) return Some(v.asInstanceOf[A]); v = reader.readLong(s)(using unsafeEvidence) }
          None
        } else if (et eq JvmType.Float) {
          val ff = pred.asInstanceOf[Float => Boolean]; val s = Double.MaxValue
          var v  = reader.readFloat(s)(using unsafeEvidence)
          while (v != s) {
            if (ff(v.toFloat)) return Some(v.toFloat.asInstanceOf[A]); v = reader.readFloat(s)(using unsafeEvidence)
          }
          None
        } else if (et eq JvmType.Double) {
          val fd = pred.asInstanceOf[Double => Boolean]; val s = Double.MaxValue
          var v  = reader.readDouble(s)(using unsafeEvidence)
          while (v != s) { if (fd(v)) return Some(v.asInstanceOf[A]); v = reader.readDouble(s)(using unsafeEvidence) }
          None
        } else if (et eq JvmType.Byte) {
          val fb = pred.asInstanceOf[Byte => Boolean]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) {
            if (fb(v.toByte)) return Some(v.toByte.asInstanceOf[A]); v = reader.readInt(s)(using unsafeEvidence)
          }
          None
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            val a = v.asInstanceOf[A]
            if (pred(a)) return Some(a)
            v = reader.read(EndOfStream)
          }
          None
        }
      }
    }

  /**
   * Returns `true` if all elements satisfy `pred`, short-circuiting on failure.
   */
  def forall[A](pred: A => Boolean): Sink[Nothing, A, Boolean] =
    new Sink[Nothing, A, Boolean] {
      private[streams] def drain(reader: Reader[?]): Boolean = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          val fi = pred.asInstanceOf[Int => Boolean]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { if (!fi(v.toInt)) return false; v = reader.readInt(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Long) {
          val fl = pred.asInstanceOf[Long => Boolean]; val s = Long.MaxValue
          var v  = reader.readLong(s)(using unsafeEvidence)
          while (v != s) { if (!fl(v)) return false; v = reader.readLong(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Float) {
          val ff = pred.asInstanceOf[Float => Boolean]; val s = Double.MaxValue
          var v  = reader.readFloat(s)(using unsafeEvidence)
          while (v != s) { if (!ff(v.toFloat)) return false; v = reader.readFloat(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Double) {
          val fd = pred.asInstanceOf[Double => Boolean]; val s = Double.MaxValue
          var v  = reader.readDouble(s)(using unsafeEvidence)
          while (v != s) { if (!fd(v)) return false; v = reader.readDouble(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Byte) {
          val fb = pred.asInstanceOf[Byte => Boolean]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { if (!fb(v.toByte)) return false; v = reader.readInt(s)(using unsafeEvidence) }
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            if (!pred(v.asInstanceOf[A])) return false
            v = reader.read(EndOfStream)
          }
        }
        true
      }
    }

  /** A sink that applies `f` to each element for its side-effects. */
  def foreach[A](f: A => Unit): Sink[Nothing, A, Unit] =
    new Sink[Nothing, A, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          val fi = f.asInstanceOf[Int => Unit]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { fi(v.toInt); v = reader.readInt(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Long) {
          val fl = f.asInstanceOf[Long => Unit]; val s = Long.MaxValue; var v = reader.readLong(s)(using unsafeEvidence)
          while (v != s) { fl(v); v = reader.readLong(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Float) {
          val ff = f.asInstanceOf[Float => Unit]; val s = Double.MaxValue;
          var v  = reader.readFloat(s)(using unsafeEvidence)
          while (v != s) { ff(v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Double) {
          val fd = f.asInstanceOf[Double => Unit]; val s = Double.MaxValue;
          var v  = reader.readDouble(s)(using unsafeEvidence)
          while (v != s) { fd(v); v = reader.readDouble(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Byte) {
          val fb = f.asInstanceOf[Byte => Unit]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { fb(v.toByte); v = reader.readInt(s)(using unsafeEvidence) }
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) { f(v.asInstanceOf[A]); v = reader.read(EndOfStream) }
        }
      }
    }

  /** A sink that folds elements with `f` starting from `z`. */
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z): Sink[Nothing, A, Z] =
    new Sink[Nothing, A, Z] {
      private[streams] def drain(reader: Reader[?]): Z = {
        var acc = z
        val et  = reader.jvmType
        if (et eq JvmType.Int) {
          val fi = f.asInstanceOf[(Z, Int) => Z]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { acc = fi(acc, v.toInt); v = reader.readInt(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Long) {
          val fl = f.asInstanceOf[(Z, Long) => Z]; val s = Long.MaxValue;
          var v  = reader.readLong(s)(using unsafeEvidence)
          while (v != s) { acc = fl(acc, v); v = reader.readLong(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Float) {
          val ff = f.asInstanceOf[(Z, Float) => Z]; val s = Double.MaxValue;
          var v  = reader.readFloat(s)(using unsafeEvidence)
          while (v != s) { acc = ff(acc, v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Double) {
          val fd = f.asInstanceOf[(Z, Double) => Z]; val s = Double.MaxValue;
          var v  = reader.readDouble(s)(using unsafeEvidence)
          while (v != s) { acc = fd(acc, v); v = reader.readDouble(s)(using unsafeEvidence) }
        } else if (et eq JvmType.Byte) {
          val fb = f.asInstanceOf[(Z, Byte) => Z]; val s = Long.MinValue
          var v  = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { acc = fb(acc, v.toByte); v = reader.readInt(s)(using unsafeEvidence) }
        } else {
          var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            acc = f(acc, v.asInstanceOf[A]); v = reader.read(EndOfStream)
          }
        }
        acc
      }
    }

  /**
   * A sink that writes all stream chars to a [[java.io.Writer]]. Does ''not''
   * close the writer when the sink completes.
   */
  def fromJavaWriter(w: java.io.Writer): Sink[Nothing, Char, Unit] =
    new Sink[Nothing, Char, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        var c = reader.read(EndOfStream)
        while (c.asInstanceOf[AnyRef] ne EndOfStream) {
          w.write(c.asInstanceOf[Char].toInt); c = reader.read(EndOfStream)
        }
      }
    }

  /**
   * A sink that writes all stream bytes to a [[java.io.OutputStream]]. Does
   * ''not'' close the output stream when the sink completes.
   */
  def fromOutputStream(os: java.io.OutputStream): Sink[Nothing, Byte, Unit] =
    new Sink[Nothing, Byte, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        var b = reader.readByte()
        while (b >= 0) { os.write(b); b = reader.readByte() }
      }
    }

  /**
   * A sink that returns the first element, or `None` if the stream is empty.
   */
  def head[A]: Sink[Nothing, A, Option[A]] =
    new Sink[Nothing, A, Option[A]] {
      private[streams] def drain(reader: Reader[?]): Option[A] = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          val v = reader.readInt(Long.MinValue)(using unsafeEvidence)
          if (v != Long.MinValue) Some(v.toInt.asInstanceOf[A]) else None
        } else if (et eq JvmType.Long) {
          val v = reader.readLong(Long.MaxValue)(using unsafeEvidence)
          if (v != Long.MaxValue) Some(v.asInstanceOf[A]) else None
        } else if (et eq JvmType.Float) {
          val v = reader.readFloat(Double.MaxValue)(using unsafeEvidence)
          if (v != Double.MaxValue) Some(v.toFloat.asInstanceOf[A]) else None
        } else if (et eq JvmType.Double) {
          val v = reader.readDouble(Double.MaxValue)(using unsafeEvidence)
          if (v != Double.MaxValue) Some(v.asInstanceOf[A]) else None
        } else if (et eq JvmType.Byte) {
          val v = reader.readInt(Long.MinValue)(using unsafeEvidence)
          if (v != Long.MinValue) Some(v.toByte.asInstanceOf[A]) else None
        } else {
          val v = reader.read(EndOfStream); if (v.asInstanceOf[AnyRef] ne EndOfStream) Some(v.asInstanceOf[A]) else None
        }
      }
    }

  /** A sink that returns the last element, or `None` if the stream is empty. */
  def last[A]: Sink[Nothing, A, Option[A]] =
    new Sink[Nothing, A, Option[A]] {
      private[streams] def drain(reader: Reader[?]): Option[A] = {
        val et = reader.jvmType
        if (et eq JvmType.Int) {
          val s = Long.MinValue; var hasValue = false; var lastV = 0; var v = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { hasValue = true; lastV = v.toInt; v = reader.readInt(s)(using unsafeEvidence) }
          if (hasValue) Some(lastV.asInstanceOf[A]) else None
        } else if (et eq JvmType.Long) {
          val s = Long.MaxValue; var hasValue = false; var lastV = 0L; var v = reader.readLong(s)(using unsafeEvidence)
          while (v != s) { hasValue = true; lastV = v; v = reader.readLong(s)(using unsafeEvidence) }
          if (hasValue) Some(lastV.asInstanceOf[A]) else None
        } else if (et eq JvmType.Float) {
          val s = Double.MaxValue; var hasValue = false; var lastV = 0f;
          var v = reader.readFloat(s)(using unsafeEvidence)
          while (v != s) { hasValue = true; lastV = v.toFloat; v = reader.readFloat(s)(using unsafeEvidence) }
          if (hasValue) Some(lastV.asInstanceOf[A]) else None
        } else if (et eq JvmType.Double) {
          val s = Double.MaxValue; var hasValue = false; var lastV = 0.0;
          var v = reader.readDouble(s)(using unsafeEvidence)
          while (v != s) { hasValue = true; lastV = v; v = reader.readDouble(s)(using unsafeEvidence) }
          if (hasValue) Some(lastV.asInstanceOf[A]) else None
        } else if (et eq JvmType.Byte) {
          val s = Long.MinValue; var hasValue = false; var lastV: Byte = 0;
          var v = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { hasValue = true; lastV = v.toByte; v = reader.readInt(s)(using unsafeEvidence) }
          if (hasValue) Some(lastV.asInstanceOf[A]) else None
        } else {
          var lastV: Any = null
          var hasValue   = false
          var v          = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) { hasValue = true; lastV = v; v = reader.read(EndOfStream) }
          if (hasValue) Some(lastV.asInstanceOf[A]) else None
        }
      }
    }

  /**
   * Sums Double elements into a Double accumulator. Does not check for
   * overflow.
   */
  val sumDouble: Sink[Nothing, Double, Double] = loopDoubleToDouble(0.0)((acc, a) => acc + a)

  /** Sums Float elements. Accumulates into a Double to reduce rounding loss. */
  val sumFloat: Sink[Nothing, Float, Double] = loopFloatToDouble(0.0)((acc, a) => acc + a.toDouble)

  /** Sums Int elements into a Long accumulator. Does not check for overflow. */
  val sumInt: Sink[Nothing, Int, Long] = loopIntToLong(0L)((acc, a) => acc + a.toLong)

  /**
   * Sums Long elements into a Long accumulator. Does not check for overflow.
   */
  val sumLong: Sink[Nothing, Long, Long] = loopLongToLong(0L)((acc, a) => acc + a)

  /** A sink that collects the first `n` elements into a [[Chunk]]. */
  def take[A](n: Int): Sink[Nothing, A, Chunk[A]] =
    new Sink[Nothing, A, Chunk[A]] {
      private[streams] def drain(reader: Reader[?]): Chunk[A] = {
        val et = reader.jvmType
        if (et eq JvmType.Int) takeInt(reader)
        else if (et eq JvmType.Long) takeLong(reader)
        else if (et eq JvmType.Float) takeFloat(reader)
        else if (et eq JvmType.Double) takeDouble(reader)
        else if (et eq JvmType.Byte) takeByte(reader)
        else takeGeneric(reader)
      }
      private def takeInt(reader: Reader[?]): Chunk[A] = {
        val b = new ChunkBuilder.Int(); var count = 0; val s = Long.MinValue
        while (count < n) {
          val v = reader.readInt(s)(using unsafeEvidence)
          if (v == s) return b.result().asInstanceOf[Chunk[A]]
          b.addOne(v.toInt); count += 1
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeLong(reader: Reader[?]): Chunk[A] = {
        val b = new ChunkBuilder.Long(); var count = 0; val s = Long.MaxValue
        while (count < n) {
          val v = reader.readLong(s)(using unsafeEvidence)
          if (v == s) return b.result().asInstanceOf[Chunk[A]]
          b.addOne(v); count += 1
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeFloat(reader: Reader[?]): Chunk[A] = {
        val b = new ChunkBuilder.Float(); var count = 0; val s = Double.MaxValue
        while (count < n) {
          val v = reader.readFloat(s)(using unsafeEvidence)
          if (v == s) return b.result().asInstanceOf[Chunk[A]]
          b.addOne(v.toFloat); count += 1
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeDouble(reader: Reader[?]): Chunk[A] = {
        val b = new ChunkBuilder.Double(); var count = 0; val s = Double.MaxValue
        while (count < n) {
          val v = reader.readDouble(s)(using unsafeEvidence)
          if (v == s) return b.result().asInstanceOf[Chunk[A]]
          b.addOne(v); count += 1
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeByte(reader: Reader[?]): Chunk[A] = {
        val b = new ChunkBuilder.Byte(); var count = 0; val s = Long.MinValue
        while (count < n) {
          val v = reader.readInt(s)(using unsafeEvidence)
          if (v == s) return b.result().asInstanceOf[Chunk[A]]
          b.addOne(v.toByte); count += 1
        }
        b.result().asInstanceOf[Chunk[A]]
      }
      private def takeGeneric(reader: Reader[?]): Chunk[A] = {
        val buf = ChunkBuilder.make[A](n); var count = 0
        while (count < n) {
          val v = reader.read(EndOfStream)
          if (v.asInstanceOf[AnyRef] eq EndOfStream) return buf.result()
          buf += v.asInstanceOf[A]; count += 1
        }
        buf.result()
      }
    }

  private def collectAllByte(reader: Reader[?]): Chunk[Byte] = {
    val b = new ChunkBuilder.Byte()
    val s = Long.MinValue; var v = reader.readInt(s)(using unsafeEvidence)
    while (v != s) { b.addOne(v.toByte); v = reader.readInt(s)(using unsafeEvidence) }
    b.result()
  }

  private def collectAllDouble(reader: Reader[?]): Chunk[Double] = {
    val b = new ChunkBuilder.Double()
    val s = Double.MaxValue; var v = reader.readDouble(s)(using unsafeEvidence)
    while (v != s) { b.addOne(v); v = reader.readDouble(s)(using unsafeEvidence) }
    b.result()
  }

  private def collectAllFloat(reader: Reader[?]): Chunk[Float] = {
    val b = new ChunkBuilder.Float()
    val s = Double.MaxValue; var v = reader.readFloat(s)(using unsafeEvidence)
    while (v != s) { b.addOne(v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }
    b.result()
  }

  private def collectAllInt(reader: Reader[?]): Chunk[Int] = {
    val b = new ChunkBuilder.Int()
    val s = Long.MinValue
    var v = reader.readInt(s)(using unsafeEvidence)
    while (v != s) { b.addOne(v.toInt); v = reader.readInt(s)(using unsafeEvidence) }
    b.result()
  }

  private def collectAllLong(reader: Reader[?]): Chunk[Long] = {
    val b = new ChunkBuilder.Long()
    val s = Long.MaxValue; var v = reader.readLong(s)(using unsafeEvidence)
    while (v != s) { b.addOne(v); v = reader.readLong(s)(using unsafeEvidence) }
    b.result()
  }

  /**
   * Creates a Sink that folds Double elements into a Double accumulator with no
   * boxing overhead.
   */
  private[streams] def loopDoubleToDouble(
    zero: Double
  )(step: (Double, Double) => Double): Sink[Nothing, Double, Double] =
    new Sink[Nothing, Double, Double] {
      private[streams] def drain(reader: Reader[?]): Double =
        if (reader.jvmType eq JvmType.Double) {
          val s = Double.MaxValue; var acc = zero; var v = reader.readDouble(s)(using unsafeEvidence)
          while (v != s) { acc = step(acc, v); v = reader.readDouble(s)(using unsafeEvidence) }
          acc
        } else {
          var acc = zero; var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            acc = step(acc, v.asInstanceOf[Double]); v = reader.read(EndOfStream)
          }
          acc
        }
    }

  /**
   * Creates a Sink that folds Float elements into a Double accumulator with no
   * boxing overhead.
   */
  private[streams] def loopFloatToDouble(zero: Double)(step: (Double, Float) => Double): Sink[Nothing, Float, Double] =
    new Sink[Nothing, Float, Double] {
      private[streams] def drain(reader: Reader[?]): Double =
        if (reader.jvmType eq JvmType.Float) {
          val s = Double.MaxValue; var acc = zero; var v = reader.readFloat(s)(using unsafeEvidence)
          while (v != s) { acc = step(acc, v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }
          acc
        } else {
          var acc = zero; var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            acc = step(acc, v.asInstanceOf[Float]); v = reader.read(EndOfStream)
          }
          acc
        }
    }

  /**
   * Creates a Sink that folds Int elements into a Long accumulator with no
   * boxing overhead.
   */
  private[streams] def loopIntToLong(zero: Long)(step: (Long, Int) => Long): Sink[Nothing, Int, Long] =
    new Sink[Nothing, Int, Long] {
      private[streams] def drain(reader: Reader[?]): Long =
        if (reader.jvmType eq JvmType.Int) {
          val s = Long.MinValue; var acc = zero; var v = reader.readInt(s)(using unsafeEvidence)
          while (v != s) { acc = step(acc, v.toInt); v = reader.readInt(s)(using unsafeEvidence) }
          acc
        } else {
          var acc = zero; var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            acc = step(acc, v.asInstanceOf[Int]); v = reader.read(EndOfStream)
          }
          acc
        }
    }

  /**
   * Creates a Sink that folds Long elements into a Long accumulator with no
   * boxing overhead.
   */
  private[streams] def loopLongToLong(zero: Long)(step: (Long, Long) => Long): Sink[Nothing, Long, Long] =
    new Sink[Nothing, Long, Long] {
      private[streams] def drain(reader: Reader[?]): Long =
        if (reader.jvmType eq JvmType.Long) {
          val s = Long.MaxValue; var acc = zero; var v = reader.readLong(s)(using unsafeEvidence)
          while (v != s) { acc = step(acc, v); v = reader.readLong(s)(using unsafeEvidence) }
          acc
        } else {
          var acc = zero; var v = reader.read(EndOfStream)
          while (v.asInstanceOf[AnyRef] ne EndOfStream) {
            acc = step(acc, v.asInstanceOf[Long]); v = reader.read(EndOfStream)
          }
          acc
        }
    }

  private[streams] def mkErrorMapped[E, E2, A, Z](self: Sink[E, A, Z], f: E => E2): Sink[E2, A, Z] =
    new ErrorMapped[E, E2, A, Z](self, f)

  /** Sink that pre-processes incoming elements with `g`. */
  private[streams] final class Contramapped[E, A, A2, Z](self: Sink[E, A, Z], g: A2 => A) extends Sink[E, A2, Z] {
    private[streams] def drain(reader: Reader[?]): Z = reader match {
      case p: Interpreter =>
        val inLane = Interpreter.laneOf(p.jvmType)
        p.addMap(inLane, Interpreter.OUT_R)(g)
        self.drain(p)
      case r =>
        val mapped = new Reader.MappedRef(r, g.asInstanceOf[AnyRef], JvmType.AnyRef)
        self.drain(mapped)
    }
  }

  /** Sink that transforms the error channel with `f`. */
  private[streams] final class ErrorMapped[E, E2, A, Z](self: Sink[E, A, Z], f: E => E2) extends Sink[E2, A, Z] {
    private[streams] def drain(reader: Reader[?]): Z =
      try self.drain(reader)
      catch {
        case e: StreamError =>
          throw new StreamError(f(e.value.asInstanceOf[E]))
      }
  }

  /** Specialized fold accumulating into a Double to avoid boxing. */
  private[streams] final class FoldLeftDouble[A](z: Double, f: (Double, A) => Double) extends Sink[Nothing, A, Double] {
    private[streams] def drain(reader: Reader[?]): Double = {
      val et = reader.jvmType
      if (et eq JvmType.Int) foldInt(reader)
      else if (et eq JvmType.Long) foldLong(reader)
      else if (et eq JvmType.Float) foldFloat(reader)
      else if (et eq JvmType.Double) foldDouble(reader)
      else if (et eq JvmType.Byte) foldByte(reader)
      else foldGeneric(reader)
    }
    private def foldInt(reader: Reader[?]): Double = {
      val fi = f.asInstanceOf[(Double, Int) => Double]; var acc = z; val s = Long.MinValue
      var v  = reader.readInt(s)(using unsafeEvidence);
      while (v != s) { acc = fi(acc, v.toInt); v = reader.readInt(s)(using unsafeEvidence) }; acc
    }
    private def foldLong(reader: Reader[?]): Double = {
      val fl = f.asInstanceOf[(Double, Long) => Double]; var acc = z; val s = Long.MaxValue
      var v  = reader.readLong(s)(using unsafeEvidence);
      while (v != s) { acc = fl(acc, v); v = reader.readLong(s)(using unsafeEvidence) }; acc
    }
    private def foldFloat(reader: Reader[?]): Double = {
      val ff = f.asInstanceOf[(Double, Float) => Double]; var acc = z; val s = Double.MaxValue
      var v  = reader.readFloat(s)(using unsafeEvidence);
      while (v != s) { acc = ff(acc, v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }; acc
    }
    private def foldDouble(reader: Reader[?]): Double = {
      val fd = f.asInstanceOf[(Double, Double) => Double]; var acc = z; val s = Double.MaxValue
      var v  = reader.readDouble(s)(using unsafeEvidence);
      while (v != s) { acc = fd(acc, v); v = reader.readDouble(s)(using unsafeEvidence) }; acc
    }
    private def foldByte(reader: Reader[?]): Double = {
      val fb = f.asInstanceOf[(Double, Byte) => Double]; var acc = z; val s = Long.MinValue
      var v  = reader.readInt(s)(using unsafeEvidence);
      while (v != s) { acc = fb(acc, v.toByte); v = reader.readInt(s)(using unsafeEvidence) }; acc
    }
    private def foldGeneric(reader: Reader[?]): Double = {
      var acc = z; var v = reader.read(EndOfStream)
      while (v.asInstanceOf[AnyRef] ne EndOfStream) { acc = f(acc, v.asInstanceOf[A]); v = reader.read(EndOfStream) };
      acc
    }
  }

  /** Specialized fold accumulating into an Int to avoid boxing. */
  private[streams] final class FoldLeftInt[A](z: Int, f: (Int, A) => Int) extends Sink[Nothing, A, Int] {
    private[streams] def drain(reader: Reader[?]): Int = {
      val et = reader.jvmType
      if (et eq JvmType.Int) foldInt(reader)
      else if (et eq JvmType.Long) foldLong(reader)
      else if (et eq JvmType.Float) foldFloat(reader)
      else if (et eq JvmType.Double) foldDouble(reader)
      else if (et eq JvmType.Byte) foldByte(reader)
      else foldGeneric(reader)
    }
    private def foldInt(reader: Reader[?]): Int = {
      val fi = f.asInstanceOf[(Int, Int) => Int]; var acc = z; val s = Long.MinValue
      var v  = reader.readInt(s)(using unsafeEvidence);
      while (v != s) { acc = fi(acc, v.toInt); v = reader.readInt(s)(using unsafeEvidence) }; acc
    }
    private def foldLong(reader: Reader[?]): Int = {
      val fl = f.asInstanceOf[(Int, Long) => Int]; var acc = z; val s = Long.MaxValue
      var v  = reader.readLong(s)(using unsafeEvidence);
      while (v != s) { acc = fl(acc, v); v = reader.readLong(s)(using unsafeEvidence) }; acc
    }
    private def foldFloat(reader: Reader[?]): Int = {
      val ff = f.asInstanceOf[(Int, Float) => Int]; var acc = z; val s = Double.MaxValue
      var v  = reader.readFloat(s)(using unsafeEvidence);
      while (v != s) { acc = ff(acc, v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }; acc
    }
    private def foldDouble(reader: Reader[?]): Int = {
      val fd = f.asInstanceOf[(Int, Double) => Int]; var acc = z; val s = Double.MaxValue
      var v  = reader.readDouble(s)(using unsafeEvidence);
      while (v != s) { acc = fd(acc, v); v = reader.readDouble(s)(using unsafeEvidence) }; acc
    }
    private def foldByte(reader: Reader[?]): Int = {
      val fb = f.asInstanceOf[(Int, Byte) => Int]; var acc = z; val s = Long.MinValue
      var v  = reader.readInt(s)(using unsafeEvidence);
      while (v != s) { acc = fb(acc, v.toByte); v = reader.readInt(s)(using unsafeEvidence) }; acc
    }
    private def foldGeneric(reader: Reader[?]): Int = {
      var acc = z; var v = reader.read(EndOfStream)
      while (v.asInstanceOf[AnyRef] ne EndOfStream) { acc = f(acc, v.asInstanceOf[A]); v = reader.read(EndOfStream) };
      acc
    }
  }

  /** Specialized fold accumulating into a Long to avoid boxing. */
  private[streams] final class FoldLeftLong[A](z: Long, f: (Long, A) => Long) extends Sink[Nothing, A, Long] {
    private[streams] def drain(reader: Reader[?]): Long = {
      val et = reader.jvmType
      if (et eq JvmType.Int) foldInt(reader)
      else if (et eq JvmType.Long) foldLong(reader)
      else if (et eq JvmType.Float) foldFloat(reader)
      else if (et eq JvmType.Double) foldDouble(reader)
      else if (et eq JvmType.Byte) foldByte(reader)
      else foldGeneric(reader)
    }
    private def foldInt(reader: Reader[?]): Long = {
      val fi = f.asInstanceOf[(Long, Int) => Long]; var acc = z; val s = Long.MinValue
      var v  = reader.readInt(s)(using unsafeEvidence);
      while (v != s) { acc = fi(acc, v.toInt); v = reader.readInt(s)(using unsafeEvidence) }; acc
    }
    private def foldLong(reader: Reader[?]): Long = {
      val fl = f.asInstanceOf[(Long, Long) => Long]; var acc = z; val s = Long.MaxValue
      var v  = reader.readLong(s)(using unsafeEvidence);
      while (v != s) { acc = fl(acc, v); v = reader.readLong(s)(using unsafeEvidence) }; acc
    }
    private def foldFloat(reader: Reader[?]): Long = {
      val ff = f.asInstanceOf[(Long, Float) => Long]; var acc = z; val s = Double.MaxValue
      var v  = reader.readFloat(s)(using unsafeEvidence);
      while (v != s) { acc = ff(acc, v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }; acc
    }
    private def foldDouble(reader: Reader[?]): Long = {
      val fd = f.asInstanceOf[(Long, Double) => Long]; var acc = z; val s = Double.MaxValue
      var v  = reader.readDouble(s)(using unsafeEvidence);
      while (v != s) { acc = fd(acc, v); v = reader.readDouble(s)(using unsafeEvidence) }; acc
    }
    private def foldByte(reader: Reader[?]): Long = {
      val fb = f.asInstanceOf[(Long, Byte) => Long]; var acc = z; val s = Long.MinValue
      var v  = reader.readInt(s)(using unsafeEvidence);
      while (v != s) { acc = fb(acc, v.toByte); v = reader.readInt(s)(using unsafeEvidence) }; acc
    }
    private def foldGeneric(reader: Reader[?]): Long = {
      var acc = z; var v = reader.read(EndOfStream)
      while (v.asInstanceOf[AnyRef] ne EndOfStream) { acc = f(acc, v.asInstanceOf[A]); v = reader.read(EndOfStream) };
      acc
    }
  }

  /** Sink that transforms the result with `f`. */
  private[streams] final class Mapped[E, A, Z, Z2](self: Sink[E, A, Z], f: Z => Z2) extends Sink[E, A, Z2] {
    private[streams] def drain(reader: Reader[?]): Z2 = f(self.drain(reader))
  }
}
