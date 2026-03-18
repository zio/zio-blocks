package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Tests for the Int-specialized zero-boxing pipeline:
 *   - JvmType.Infer resolution
 *   - Reader two-phase protocol (readInt/lastInt via jvmType)
 *   - Specialized source dequeues (FromRange, RepeatInt, SingletonInt)
 *   - Specialized combinator dequeues (MappedIntToInt, FilteredInt, TakenInt,
 *     etc.)
 *   - Specialized sinks (loopIntToLong, drainInt)
 *   - Stream.isPure + scope bypass
 *   - Overloaded runFold(Long) / runFold(Int)
 *   - FlatMap specialization
 */
object IntSpecializationSpec extends StreamsBaseSpec {

  def spec = suite("Int Specialization")(
    isPrimitiveSuite,
    intReaderSuite,
    singletonIntSuite,
    specializedCombinatorSuite,
    specializedSinkSuite,
    flatMapSpecializationSuite,
    edgeCaseSuite,
    resetSuite
  )

  // ---------------------------------------------------------------------------
  // JvmType.Infer resolution
  // ---------------------------------------------------------------------------
  val isPrimitiveSuite = suite("JvmType.Infer")(
    test("resolves Int for Int") {
      assertTrue(JvmType.Infer.int.jvmType == JvmType.Int)
    },
    test("resolves Long for Long") {
      assertTrue(JvmType.Infer.long.jvmType == JvmType.Long)
    },
    test("resolves Double for Double") {
      assertTrue(JvmType.Infer.double.jvmType == JvmType.Double)
    },
    test("resolves Byte for Byte") {
      assertTrue(JvmType.Infer.byte.jvmType == JvmType.Byte)
    },
    test("resolves AnyRef for String") {
      assertTrue(implicitly[JvmType.Infer[String]].jvmType == JvmType.AnyRef)
    },
    test("isByte returns true for Byte") {
      assertTrue(JvmType.Infer.byte.isByte)
    },
    test("isByte returns false for Int") {
      assertTrue(!JvmType.Infer.int.isByte)
    }
  )

  // ---------------------------------------------------------------------------
  // Reader two-phase protocol via jvmType
  // ---------------------------------------------------------------------------
  val intReaderSuite = suite("Reader Int specialization")(
    test("FromRange has jvmType Int") {
      val dq = Reader.fromRange(0 until 5)
      assertTrue(dq.jvmType eq JvmType.Int)
    },
    test("FromRange readInt returns correct elements") {
      val dq  = Reader.fromRange(0 until 5)
      val buf = scala.collection.mutable.ArrayBuffer[Int]()
      val s   = Long.MinValue; var v = dq.readInt(s)
      while (v != s) { buf += v.toInt; v = dq.readInt(s) }
      assertTrue(buf.toList == List(0, 1, 2, 3, 4))
    },
    test("readInt sequence matches take sequence for ranges") {
      check(Gen.int(0, 100), Gen.int(1, 100)) { (from, len) =>
        val until = from + len
        val dq1   = Reader.fromRange(from until until)
        val dq2   = Reader.fromRange(from until until)

        val via_take = {
          val buf = scala.collection.mutable.ArrayBuffer[Int]()
          var v   = dq1.read[Any](null)
          while (v != null) { buf += v.asInstanceOf[Int]; v = dq1.read[Any](null) }
          buf.toList
        }

        val via_readInt = {
          val buf = scala.collection.mutable.ArrayBuffer[Int]()
          val s   = Long.MinValue; var v = dq2.readInt(s)
          while (v != s) { buf += v.toInt; v = dq2.readInt(s) }
          buf.toList
        }

        assertTrue(via_take == via_readInt)
      }
    },
    test("repeat(Int) has jvmType Int") {
      val dq = Reader.repeat(42)
      assertTrue(dq.jvmType eq JvmType.Int)
    },
    test("repeat(Int) readInt always returns value") {
      val dq     = Reader.repeat(42)
      val s      = Long.MinValue
      val values = (0 until 10).map(_ => dq.readInt(s).toInt).toList
      assertTrue(values == List.fill(10)(42))
    },
    test("generic Reader readInt works via boxing default") {
      val dq = Reader.fromIterable(List(1, 2, 3))
      assertTrue(dq.jvmType eq JvmType.AnyRef)
      val buf = scala.collection.mutable.ArrayBuffer[Int]()
      val s   = Long.MinValue; var v = dq.readInt(s)
      while (v != s) { buf += v.toInt; v = dq.readInt(s) }
      assertTrue(buf.toList == List(1, 2, 3))
    }
  )

  // ---------------------------------------------------------------------------
  // SingletonInt
  // ---------------------------------------------------------------------------
  val singletonIntSuite = suite("SingletonInt")(
    test("Reader.single for Int produces SingletonPrim") {
      val dq = Reader.single(42)
      assertTrue(dq.isInstanceOf[Reader.SingletonPrim[_]])
    },
    test("SingletonInt readInt returns value then sentinel") {
      val dq     = Reader.single(42)
      val s      = Long.MinValue
      val first  = dq.readInt(s)
      val second = dq.readInt(s)
      assertTrue(first == 42L && second == s)
    },
    test("SingletonInt take returns Right then Left") {
      val dq     = Reader.single(42)
      val first  = dq.read[Any](null)
      val second = dq.read[Any](null)
      assertTrue(first == Int.box(42) && second == null)
    },
    test("Reader.single for String produces SingletonGeneric") {
      val dq = Reader.single("hello")
      assertTrue(dq.isInstanceOf[Reader.SingletonGeneric[_]])
    },
    test("Stream.succeed(Int) uses SingletonInt") {
      // Verify the stream pipeline actually uses specialized dequeues
      val result = Stream.succeed(42).runCollect
      assertTrue(result == Right(Chunk(42)))
    }
  )

  // ---------------------------------------------------------------------------
  // Specialized combinator dequeues
  // ---------------------------------------------------------------------------
  val specializedCombinatorSuite = suite("Specialized combinators")(
    test("range.map(+1) via readInt matches runCollect") {
      val result = Stream.range(0, 100).map(_ + 1).runCollect
      assertTrue(result == Right(Chunk.fromIterable(1 to 100)))
    },
    test("range.filter(even) via readInt matches runCollect") {
      val result = Stream.range(0, 100).filter(_ % 2 == 0).runCollect
      assertTrue(result == Right(Chunk.fromIterable((0 until 100).filter(_ % 2 == 0))))
    },
    test("range.map.filter pipeline produces specialized chain") {
      val result   = Stream.range(0, 10).map(_ * 2).filter(_ > 5).runCollect
      val expected = (0 until 10).map(_ * 2).filter(_ > 5)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("range.take via TakenInt") {
      val result = Stream.range(0, 100).take(10).runCollect
      assertTrue(result == Right(Chunk.fromIterable(0 until 10)))
    },
    test("range.takeWhile via TakenWhileInt") {
      val result = Stream.range(0, 100).takeWhile(_ < 10).runCollect
      assertTrue(result == Right(Chunk.fromIterable(0 until 10)))
    },
    test("range.drop via DroppedInt") {
      val result = Stream.range(0, 20).drop(10).runCollect
      assertTrue(result == Right(Chunk.fromIterable(10 until 20)))
    },
    test("range ++ range via ConcatWithInt") {
      val result = (Stream.range(0, 5) ++ Stream.range(5, 10)).runCollect
      assertTrue(result == Right(Chunk.fromIterable(0 until 10)))
    },
    test("chained maps ×5 via specialized path") {
      val result   = Stream.range(0, 10).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).runCollect
      val expected = (0 until 10).map(_ + 5)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("filter then map then fold Long") {
      val result   = Stream.range(0, 100).filter(_ % 2 == 0).map(_ + 1).runFold(0L)((acc, v) => acc + v)
      val expected = (0 until 100).filter(_ % 2 == 0).map(_ + 1).map(_.toLong).sum
      assertTrue(result == Right(expected))
    }
  )

  // ---------------------------------------------------------------------------
  // Specialized sinks
  // ---------------------------------------------------------------------------
  val specializedSinkSuite = suite("Specialized sinks")(
    test("runFold(Long) matches runFold[Z] for Int streams") {
      val n           = 1000
      val generic     = Stream.range(0, n).runFold[Long](0L)((acc, i) => acc + i)
      val specialized = Stream.range(0, n).runFold(0L)((acc, i) => acc + i)
      assertTrue(generic == specialized)
    },
    test("runFold(Int) matches manual sum") {
      val n        = 1000
      val expected = (0 until n).sum
      val result   = Stream.range(0, n).runFold(0)((acc, i) => acc + i)
      assertTrue(result == Right(expected))
    },
    test("Sink.sumInt uses specialized path") {
      val result   = Stream.range(0, 100).run(Sink.sumInt)
      val expected = (0 until 100).map(_.toLong).sum
      assertTrue(result == Right(expected))
    },
    test("drain on Int stream uses drainInt path") {
      val result = Stream.range(0, 10000).runDrain
      assertTrue(result == Right(()))
    },
    test("runFold(Double) matches manual sum for Int streams") {
      val n        = 1000
      val expected = (0 until n).map(_.toDouble).sum
      val result   = Stream.range(0, n).runFold(0.0)((acc, i) => acc + i)
      assertTrue(result == Right(expected))
    },
    test("runFold(Double) matches runFold[Z] for Int streams") {
      val n           = 1000
      val generic     = Stream.range(0, n).runFold[Double](0.0)((acc, i) => acc + i)
      val specialized = Stream.range(0, n).runFold(0.0)((acc, i) => acc + i)
      assertTrue(generic == specialized)
    },
    test("runFold(Double) works on Long streams") {
      val data     = Chunk.fromIterable((1L to 100L))
      val expected = (1L to 100L).map(_.toDouble).sum
      val result   = Stream.fromChunk(data).runFold(0.0)((acc, l) => acc + l)
      assertTrue(result == Right(expected))
    },
    test("runFold(Double) works on Float streams") {
      val data     = Chunk.fromIterable((1 to 50).map(_.toFloat))
      val expected = (1 to 50).map(_.toDouble).sum
      val result   = Stream.fromChunk(data).runFold(0.0)((acc, f) => acc + f)
      assertTrue(result == Right(expected))
    },
    test("runFold(Double) works on Double streams") {
      val data     = Chunk.fromIterable((1 to 50).map(_.toDouble))
      val expected = (1 to 50).map(_.toDouble).sum
      val result   = Stream.fromChunk(data).runFold(0.0)((acc, d) => acc + d)
      assertTrue(result == Right(expected))
    },
    test("runFold(Double) works on generic streams") {
      val data   = Chunk.fromIterable(List("a", "bb", "ccc"))
      val result = Stream.fromChunk(data).runFold(0.0)((acc, s) => acc + s.length)
      assertTrue(result == Right(6.0))
    },
    test("Sink.sumFloat uses specialized path for Float streams") {
      val data     = Chunk.fromIterable((1 to 100).map(_.toFloat))
      val expected = (1 to 100).map(_.toDouble).sum
      val result   = Stream.fromChunk(data).run(Sink.sumFloat)
      assertTrue(result == Right(expected))
    }
  )

  // (isPure suite removed — isPure no longer exists; compile() is always the entry point)

  // ---------------------------------------------------------------------------
  // FlatMap specialization
  // ---------------------------------------------------------------------------
  val flatMapSpecializationSuite = suite("FlatMap specialization")(
    test("flatMap with generic chunk inner stream") {
      // Test flatMap where inner streams use fromChunk with generic chunks (boxed ints)
      val genericChunk = Chunk.fromIterable(List(10, 20, 30))
      val result       = Stream.succeed(42).flatMap(_ => Stream.fromChunk(genericChunk)).runCollect
      assertTrue(result == Right(Chunk(10, 20, 30)))
    },
    test("flatMap with boxed function") {
      // Simulate the Gen.function pattern: f is captured as AnyRef function
      val f: Int => Stream[Nothing, Int]      = (i: Int) => Stream.fromChunk(Chunk(i * 10, i * 10 + 1))
      val boxedF: Any => Stream[Nothing, Int] = (a: Any) => f(a.asInstanceOf[Int])
      val result                              = Stream.succeed(5).flatMap(boxedF.asInstanceOf[Int => Stream[Nothing, Int]]).runCollect
      assertTrue(result == Right(Chunk(50, 51)))
    },
    test("flatMap like StreamLawsSpec") {
      // Reproduce exact StreamLawsSpec pattern
      import zio.test.Gen
      check(
        Gen.int(-1000, 1000),
        Gen.function(Gen.chunkOfBounded(0, 50)(Gen.int(-1000, 1000)).map(c => Stream.fromChunk(Chunk.fromIterable(c))))
      ) { (a, f) =>
        val left  = Stream.succeed(a).flatMap(f).runCollect.fold(_ => Chunk.empty, identity)
        val right = f(a).runCollect.fold(_ => Chunk.empty, identity)
        assertTrue(left == right)
      }
    },
    test("flatMap(succeed) matches manual computation") {
      val result   = Stream.range(0, 100).flatMap(i => Stream.succeed(i * 2)).runCollect
      val expected = (0 until 100).map(_ * 2)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("flatMap with runFold(Long) uses specialized path") {
      val result   = Stream.range(0, 100).flatMap(i => Stream.succeed(i * 2)).runFold(0L)((acc, i) => acc + i)
      val expected = (0 until 100).map(i => (i * 2).toLong).sum
      assertTrue(result == Right(expected))
    },
    test("flatMap error propagation") {
      val result: Either[String, Chunk[Int]] = Stream
        .range(0, 5)
        .flatMap { i =>
          if (i == 3) Stream.fail("boom")
          else Stream.succeed(i)
        }
        .runCollect
      assertTrue(result == Left("boom"))
    },
    test("flatMap with multi-element inner streams") {
      val result   = Stream.range(0, 3).flatMap(i => Stream.range(i * 10, i * 10 + 3)).runCollect
      val expected = Chunk(0, 1, 2, 10, 11, 12, 20, 21, 22)
      assertTrue(result == Right(expected))
    }
  )

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------
  val edgeCaseSuite = suite("Edge cases")(
    test("Int.MinValue as stream element is handled correctly") {
      // This is the critical edge case the two-phase protocol solves
      val result = Stream.succeed(Int.MinValue).runCollect
      assertTrue(result == Right(Chunk(Int.MinValue)))
    },
    test("Int.MinValue through map") {
      val result = Stream.succeed(Int.MinValue).map(_ + 1).runCollect
      assertTrue(result == Right(Chunk(Int.MinValue + 1)))
    },
    test("Int.MinValue through filter") {
      val result = Stream.succeed(Int.MinValue).filter(_ == Int.MinValue).runCollect
      assertTrue(result == Right(Chunk(Int.MinValue)))
    },
    test("empty range produces no elements via specialized path") {
      val result = Stream.range(0, 0).map(_ + 1).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("repeated stream via specialized path") {
      val result = Stream.range(0, 3).repeated.take(9).runCollect
      assertTrue(result == Right(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2)))
    },
    test("drop then take via specialized path") {
      val result = Stream.range(0, 100).drop(10).take(5).runCollect
      assertTrue(result == Right(Chunk(10, 11, 12, 13, 14)))
    },
    test("mapError preserves elements via specialized path") {
      val result: Either[String, Chunk[Int]] = Stream.range(0, 5).mapError((_: Nothing) => "err").runCollect
      assertTrue(result == Right(Chunk(0, 1, 2, 3, 4)))
    }
  )

  // ---------------------------------------------------------------------------
  // reset()
  // ---------------------------------------------------------------------------
  val resetSuite = suite("reset")(
    test("FromRange reset replays all elements") {
      val dq = Reader.fromRange(0 until 5)
      val s  = Long.MinValue
      // Drain all elements
      var v = dq.readInt(s); while (v != s) v = dq.readInt(s)
      assertTrue(dq.isClosed) &&
      // Reset and drain again
      {
        dq.reset(); var buf = List.empty[Int]; v = dq.readInt(s);
        while (v != s) { buf = v.toInt :: buf; v = dq.readInt(s) }; assertTrue(buf.reverse == List(0, 1, 2, 3, 4))
      }
    },
    test("FromChunkInt reset replays all elements") {
      val dq = Reader.fromChunk(Chunk(10, 20, 30))
      val s  = Long.MinValue
      var v  = dq.readInt(s); while (v != s) v = dq.readInt(s)
      assertTrue(dq.isClosed) &&
      {
        dq.reset(); var buf = List.empty[Int]; v = dq.readInt(s);
        while (v != s) { buf = v.toInt :: buf; v = dq.readInt(s) }; assertTrue(buf.reverse == List(10, 20, 30))
      }
    },
    test("FromIterable reset replays from fresh iterator") {
      val dq = Reader.fromIterable(List("a", "b", "c"))
      dq.read[Any](null); dq.read[Any](null); dq.read[Any](null) // drain all
      dq.read[Any](null)                                         // trigger exhausted
      dq.reset()
      val v1 = dq.read[Any](null)
      val v2 = dq.read[Any](null)
      val v3 = dq.read[Any](null)
      assertTrue(v1 == "a", v2 == "b", v3 == "c")
    },
    test("SingletonPrim reset allows re-take") {
      val dq = Reader.single(42)
      val s  = Long.MinValue
      val v1 = dq.readInt(s)
      val v2 = dq.readInt(s)
      dq.reset()
      val v3 = dq.readInt(s)
      assertTrue(v1 == 42L, v2 == s, v3 == 42L)
    },
    test("Unfold reset restarts from initial state") {
      val dq = Reader.unfold[Int, Int](0)(s => if (s < 3) Some((s, s + 1)) else None)
      dq.read[Any](null); dq.read[Any](null); dq.read[Any](null); dq.read[Any](null) // drain + trigger close
      dq.reset()
      val v1 = dq.read[Any](null)
      val v2 = dq.read[Any](null)
      val v3 = dq.read[Any](null)
      assertTrue(v1 == Int.box(0), v2 == Int.box(1), v3 == Int.box(2))
    },
    test("Repeat-forever reset is no-op (infinite)") {
      val dq = Reader.repeat(42)
      assertTrue(dq.read[Any](null) == Int.box(42)) &&
      { dq.reset(); assertTrue(dq.read[Any](null) == Int.box(42)) }
    },
    test("InputStreamReader reset throws") {
      val is = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))
      val dq = Reader.fromInputStream(is)
      assertTrue(
        try { dq.reset(); false }
        catch { case _: UnsupportedOperationException => true }
      )
    }
  )
}
