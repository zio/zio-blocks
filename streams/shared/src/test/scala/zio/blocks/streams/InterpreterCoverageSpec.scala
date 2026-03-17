package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal._
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Supplementary Interpreter tests covering gaps in InterpreterSpec:
 *   - All 25 addMap lane crossings (13 previously untested)
 *   - wrapLastRead
 *   - appendRead on a pipeline with existing ops
 */
object InterpreterCoverageSpec extends StreamsBaseSpec {
  import Interpreter._

  def spec = suite("Interpreter coverage")(
    allMapCrossingsSuite,
    wrapLastReadSuite,
    appendReadSuite,
    closeSuppressedSuite
  )

  // ---- drain helpers (same as InterpreterSpec) ----

  private def drainInts(p: Interpreter, sentinel: Long = Long.MinValue): List[Int] = {
    val buf = scala.collection.mutable.ListBuffer[Int]()
    var v   = p.readInt(sentinel)(using unsafeEvidence)
    while (v != sentinel) { buf += v.toInt; v = p.readInt(sentinel)(using unsafeEvidence) }
    buf.toList
  }

  private def drainLongs(p: Interpreter, sentinel: Long = Long.MaxValue): List[Long] = {
    val buf = scala.collection.mutable.ListBuffer[Long]()
    var v   = p.readLong(sentinel)(using unsafeEvidence)
    while (v != sentinel) { buf += v; v = p.readLong(sentinel)(using unsafeEvidence) }
    buf.toList
  }

  private def drainFloats(p: Interpreter, sentinel: Double = Double.MaxValue): List[Float] = {
    val buf = scala.collection.mutable.ListBuffer[Float]()
    var v   = p.readFloat(sentinel)(using unsafeEvidence)
    while (v != sentinel) { buf += v.toFloat; v = p.readFloat(sentinel)(using unsafeEvidence) }
    buf.toList
  }

  private def drainDoubles(p: Interpreter, sentinel: Double = Double.MaxValue): List[Double] = {
    val buf = scala.collection.mutable.ListBuffer[Double]()
    var v   = p.readDouble(sentinel)(using unsafeEvidence)
    while (v != sentinel) { buf += v; v = p.readDouble(sentinel)(using unsafeEvidence) }
    buf.toList
  }

  private def drainGeneric(p: Interpreter): List[Any] = {
    val sentinel: AnyRef = new AnyRef
    val buf              = scala.collection.mutable.ListBuffer[Any]()
    var v                = p.read(sentinel)
    while (v.asInstanceOf[AnyRef] ne sentinel) { buf += v; v = p.read(sentinel) }
    buf.toList
  }

  // =========================================================================
  //  All 25 addMap lane crossings
  // =========================================================================

  val allMapCrossingsSuite = suite("All 25 addMap lane crossings")(
    // ---- Int input (5 outputs) ----
    test("Int->Int (tag 0)") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 10)
      assertTrue(drainInts(p) == List(10, 11, 12))
    },
    test("Int->Long (tag 1)") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong * 100L)
      assertTrue(drainLongs(p) == List(0L, 100L, 200L))
    },
    test("Int->Float (tag 2)") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Float](LANE_I, OUT_F)((_: Int).toFloat + 0.5f)
      assertTrue(drainFloats(p) == List(0.5f, 1.5f, 2.5f))
    },
    test("Int->Double (tag 3)") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Double](LANE_I, OUT_D)((_: Int).toDouble + 0.1)
      assertTrue(drainDoubles(p) == List(0.1, 1.1, 2.1))
    },
    test("Int->AnyRef (tag 4)") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, AnyRef](LANE_I, OUT_R)((i: Int) => s"v$i")
      assertTrue(drainGeneric(p) == List("v0", "v1", "v2"))
    },

    // ---- Long input (5 outputs) ----
    test("Long->Int (tag 5)") {
      val p = Interpreter(Reader.fromChunk(Chunk(10L, 20L, 30L)))
      p.addMap[Long, Int](LANE_L, OUT_I)((_: Long).toInt)
      assertTrue(drainInts(p) == List(10, 20, 30))
    },
    test("Long->Long (tag 6)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 5L)
      assertTrue(drainLongs(p) == List(5L, 10L, 15L))
    },
    test("Long->Float (tag 7)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
      p.addMap[Long, Float](LANE_L, OUT_F)((_: Long).toFloat + 0.5f)
      assertTrue(drainFloats(p) == List(1.5f, 2.5f, 3.5f))
    },
    test("Long->Double (tag 8)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
      p.addMap[Long, Double](LANE_L, OUT_D)((_: Long).toDouble)
      assertTrue(drainDoubles(p) == List(1.0, 2.0, 3.0))
    },
    test("Long->AnyRef (tag 9)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
      p.addMap[Long, AnyRef](LANE_L, OUT_R)((l: Long) => s"L$l")
      assertTrue(drainGeneric(p) == List("L1", "L2", "L3"))
    },

    // ---- Float input (5 outputs) ----
    test("Float->Int (tag 10)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.9f, 2.1f, 3.7f)))
      p.addMap[Float, Int](LANE_F, OUT_I)((_: Float).toInt)
      assertTrue(drainInts(p) == List(1, 2, 3))
    },
    test("Float->Long (tag 11)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
      p.addMap[Float, Long](LANE_F, OUT_L)((_: Float).toLong)
      assertTrue(drainLongs(p) == List(1L, 2L, 3L))
    },
    test("Float->Float (tag 12)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
      p.addMap[Float, Float](LANE_F, OUT_F)((_: Float) * 2.0f)
      assertTrue(drainFloats(p) == List(2.0f, 4.0f, 6.0f))
    },
    test("Float->Double (tag 13)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.5f, 2.5f)))
      p.addMap[Float, Double](LANE_F, OUT_D)((_: Float).toDouble)
      assertTrue(drainDoubles(p) == List(1.5f.toDouble, 2.5f.toDouble))
    },
    test("Float->AnyRef (tag 14)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.5f, 2.5f)))
      p.addMap[Float, AnyRef](LANE_F, OUT_R)((f: Float) => s"F$f")
      assertTrue(drainGeneric(p) == List("F1.5", "F2.5"))
    },

    // ---- Double input (5 outputs) ----
    test("Double->Int (tag 15)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.9, 2.1, 3.7)))
      p.addMap[Double, Int](LANE_D, OUT_I)((_: Double).toInt)
      assertTrue(drainInts(p) == List(1, 2, 3))
    },
    test("Double->Long (tag 16)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0, 2.0, 3.0)))
      p.addMap[Double, Long](LANE_D, OUT_L)((_: Double).toLong)
      assertTrue(drainLongs(p) == List(1L, 2L, 3L))
    },
    test("Double->Float (tag 17)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.5, 2.5)))
      p.addMap[Double, Float](LANE_D, OUT_F)((_: Double).toFloat)
      assertTrue(drainFloats(p) == List(1.5f, 2.5f))
    },
    test("Double->Double (tag 18)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0, 2.0, 3.0)))
      p.addMap[Double, Double](LANE_D, OUT_D)((_: Double) * 2.0)
      assertTrue(drainDoubles(p) == List(2.0, 4.0, 6.0))
    },
    test("Double->AnyRef (tag 19)") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.5, 2.5)))
      p.addMap[Double, AnyRef](LANE_D, OUT_R)((d: Double) => s"D$d")
      assertTrue(drainGeneric(p) == List("D1.5", "D2.5"))
    },

    // ---- AnyRef input (5 outputs) ----
    test("AnyRef->Int (tag 20)") {
      val p = Interpreter(Reader.fromIterable(List("hello", "ab", "x")))
      p.addMap[AnyRef, Int](LANE_R, OUT_I)((s: AnyRef) => s.asInstanceOf[String].length)
      assertTrue(drainInts(p) == List(5, 2, 1))
    },
    test("AnyRef->Long (tag 21)") {
      val p = Interpreter(Reader.fromIterable(List("hello", "ab")))
      p.addMap[AnyRef, Long](LANE_R, OUT_L)((s: AnyRef) => s.asInstanceOf[String].length.toLong)
      assertTrue(drainLongs(p) == List(5L, 2L))
    },
    test("AnyRef->Float (tag 22)") {
      val p = Interpreter(Reader.fromIterable(List("hello", "ab")))
      p.addMap[AnyRef, Float](LANE_R, OUT_F)((s: AnyRef) => s.asInstanceOf[String].length.toFloat)
      assertTrue(drainFloats(p) == List(5.0f, 2.0f))
    },
    test("AnyRef->Double (tag 23)") {
      val p = Interpreter(Reader.fromIterable(List("hello", "ab")))
      p.addMap[AnyRef, Double](LANE_R, OUT_D)((s: AnyRef) => s.asInstanceOf[String].length.toDouble)
      assertTrue(drainDoubles(p) == List(5.0, 2.0))
    },
    test("AnyRef->AnyRef (tag 24)") {
      val p = Interpreter(Reader.fromIterable(List("hello", "world")))
      p.addMap[AnyRef, AnyRef](LANE_R, OUT_R)((s: AnyRef) => s.asInstanceOf[String].toUpperCase)
      assertTrue(drainGeneric(p) == List("HELLO", "WORLD"))
    }
  )

  // =========================================================================
  //  wrapLastRead
  // =========================================================================

  val wrapLastReadSuite = suite("wrapLastRead")(
    test("wrapLastRead transforms the source reader") {
      val p = Interpreter.unsealed(Reader.fromRange(0 until 10))
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
      // Wrap the source reader to limit it to 3 elements
      p.wrapLastRead((r: Reader[Any]) => Reader.fromRange(0 until 3).asInstanceOf[Reader[Any]])
      p.seal()
      val result = drainInts(p)
      // The wrapped reader produces 0,1,2; then map +1 gives 1,2,3
      assertTrue(result == List(1, 2, 3))
    },
    test("wrapLastRead on pipeline with no ops") {
      val p = Interpreter.unsealed(Reader.fromRange(0 until 100))
      p.wrapLastRead((r: Reader[Any]) => Reader.fromRange(0 until 2).asInstanceOf[Reader[Any]])
      p.seal()
      val result = drainInts(p)
      assertTrue(result == List(0, 1))
    },
    test("wrapLastRead targets the last Read op in the pipeline") {
      // Build a pipeline with a source, then wrap it; the wrap should affect the source
      val p = Interpreter.unsealed(Reader.fromRange(0 until 5))
      p.addFilter[Int](LANE_I)((_: Int) % 2 == 0)
      p.wrapLastRead((_: Reader[Any]) => Reader.fromChunk(Chunk(10, 20, 30)).asInstanceOf[Reader[Any]])
      p.seal()
      val result = drainInts(p)
      // New source is [10, 20, 30], filter even -> [10, 20, 30]
      assertTrue(result == List(10, 20, 30))
    }
  )

  // =========================================================================
  //  appendRead on a pipeline with existing ops
  // =========================================================================

  // =========================================================================
  //  close() suppressed exception accumulation
  // =========================================================================

  /** A Reader that throws the given exception on close. */
  private def failingCloseReader(ex: Throwable): Reader[Int] = new Reader[Int] {
    private var _closed                                           = false
    override def jvmType: JvmType                                 = JvmType.Int
    def isClosed: Boolean                                         = _closed
    def read[A1 >: Int](sentinel: A1): A1                         = { _closed = true; sentinel }
    override def readInt(sentinel: Long)(using Int <:< Int): Long = { _closed = true; sentinel }
    def close(): Unit                                             = throw ex
  }

  val closeSuppressedSuite = suite("close() suppressed exceptions")(
    test("single reader close exception is thrown") {
      val ex = new RuntimeException("boom")
      val p  = Interpreter(failingCloseReader(ex))
      // drain so it's exhausted
      drainInts(p)
      val caught = try { p.close(); null }
      catch { case t: Throwable => t }
      assertTrue(caught eq ex) && assertTrue(caught.getSuppressed.isEmpty)
    },
    test("multiple reader close exceptions: first thrown, rest suppressed") {
      val ex1 = new RuntimeException("first")
      val ex2 = new RuntimeException("second")
      val ex3 = new RuntimeException("third")
      // Build pipeline with multiple read slots via unsealed + appendRead
      val p = Interpreter.unsealed(failingCloseReader(ex1))
      p.appendRead(failingCloseReader(ex2))
      p.appendRead(failingCloseReader(ex3))
      p.seal()
      val caught = try { p.close(); null }
      catch { case t: Throwable => t }
      // The interpreter should throw the first error and suppress the rest
      assertTrue(caught eq ex1) &&
      assertTrue(caught.getSuppressed.length == 2) &&
      assertTrue(caught.getSuppressed()(0) eq ex2) &&
      assertTrue(caught.getSuppressed()(1) eq ex3)
    },
    test("close sets closed = true even when exception is thrown") {
      val ex = new RuntimeException("fail")
      val p  = Interpreter(failingCloseReader(ex))
      drainInts(p)
      try p.close()
      catch { case _: Throwable => () }
      assertTrue(p.isClosed)
    },
    test("close with no errors does not throw") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      drainInts(p)
      p.close()
      assertTrue(p.isClosed)
    }
  )

  // =========================================================================
  //  appendRead on a pipeline with existing ops
  // =========================================================================

  val appendReadSuite = suite("appendRead")(
    test("unsealed creates pipeline via appendRead, outputLane is Int") {
      val p = Interpreter.unsealed(Reader.fromRange(0 until 3))
      p.seal()
      assertTrue(p.outputType == JvmType.Int) && {
        val result = drainInts(p)
        assertTrue(result == List(0, 1, 2))
      }
    },
    test("unsealed with Long reader sets outputLane to Long") {
      val p = Interpreter.unsealed(Reader.fromChunk(Chunk(1L, 2L)))
      p.seal()
      assertTrue(p.outputType == JvmType.Long) && {
        val result = drainLongs(p)
        assertTrue(result == List(1L, 2L))
      }
    },
    test("unsealed with Double reader sets outputLane to Double") {
      val p = Interpreter.unsealed(Reader.fromChunk(Chunk(1.0, 2.0, 3.0)))
      p.seal()
      assertTrue(p.outputType == JvmType.Double) && {
        val result = drainDoubles(p)
        assertTrue(result == List(1.0, 2.0, 3.0))
      }
    },
    test("unsealed with Float reader sets outputLane to Float") {
      val p = Interpreter.unsealed(Reader.fromChunk(Chunk(1.0f, 2.0f)))
      p.seal()
      assertTrue(p.outputType == JvmType.Float) && {
        val result = drainFloats(p)
        assertTrue(result == List(1.0f, 2.0f))
      }
    },
    test("unsealed with AnyRef reader sets outputLane to AnyRef") {
      val p = Interpreter.unsealed(Reader.fromIterable(List("a", "b")))
      p.seal()
      assertTrue(p.outputType == JvmType.AnyRef) && {
        val result = drainGeneric(p)
        assertTrue(result == List("a", "b"))
      }
    }
  )
}
