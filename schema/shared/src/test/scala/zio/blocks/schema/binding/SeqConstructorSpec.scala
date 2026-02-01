package zio.blocks.schema.binding

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object SeqConstructorSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SeqConstructor")(
    specializedAddMethodsSuite,
    arrayResizingSuite,
    primitiveArraySuite,
    otherConstructorsSuite,
    emptyConstructorsSuite,
    sizeHintEdgeCasesSuite,
    noResizeSuite
  )

  private def specializedAddMethodsSuite = suite("specialized add methods")(
    test("addBoolean works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Boolean](2)
      c.addBoolean(builder, true)
      c.addBoolean(builder, false)
      val result = c.result(builder)
      assertTrue(result == List(true, false))
    },
    test("addByte works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Byte](2)
      c.addByte(builder, 1.toByte)
      c.addByte(builder, 2.toByte)
      val result = c.result(builder)
      assertTrue(result == List(1.toByte, 2.toByte))
    },
    test("addShort works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Short](2)
      c.addShort(builder, 1.toShort)
      c.addShort(builder, 2.toShort)
      val result = c.result(builder)
      assertTrue(result == List(1.toShort, 2.toShort))
    },
    test("addInt works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Int](2)
      c.addInt(builder, 1)
      c.addInt(builder, 2)
      val result = c.result(builder)
      assertTrue(result == List(1, 2))
    },
    test("addLong works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Long](2)
      c.addLong(builder, 1L)
      c.addLong(builder, 2L)
      val result = c.result(builder)
      assertTrue(result == List(1L, 2L))
    },
    test("addFloat works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Float](2)
      c.addFloat(builder, 1.0f)
      c.addFloat(builder, 2.0f)
      val result = c.result(builder)
      assertTrue(result == List(1.0f, 2.0f))
    },
    test("addDouble works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Double](2)
      c.addDouble(builder, 1.0)
      c.addDouble(builder, 2.0)
      val result = c.result(builder)
      assertTrue(result == List(1.0, 2.0))
    },
    test("addChar works for List") {
      val c       = SeqConstructor.listConstructor
      val builder = c.newBuilder[Char](2)
      c.addChar(builder, 'a')
      c.addChar(builder, 'b')
      val result = c.result(builder)
      assertTrue(result == List('a', 'b'))
    },
    test("addBoolean works for Chunk") {
      testChunkBoolean(SeqConstructor.chunkConstructor)
    },
    test("addInt works for Chunk") {
      testChunkInt(SeqConstructor.chunkConstructor)
    },
    test("addLong works for Chunk") {
      testChunkLong(SeqConstructor.chunkConstructor)
    },
    test("addDouble works for Chunk") {
      testChunkDouble(SeqConstructor.chunkConstructor)
    }
  )

  private def arrayResizingSuite = suite("array resizing")(
    test("Array resizes when adding more elements than initial capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Int](2)
      for (i <- 1 to 10) constructor.addInt(builder, i)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).toList)
    },
    test("ArraySeq resizes when adding more elements than initial capacity") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Int](2)
      for (i <- 1 to 10) constructor.addInt(builder, i)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).toList)
    },
    test("Array with Boolean primitives resizes correctly") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Boolean](2)
      for (i <- 1 to 10) constructor.addBoolean(builder, i % 2 == 0)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_ % 2 == 0).toList)
    },
    test("Array with Byte primitives resizes correctly") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Byte](2)
      for (i <- 1 to 10) constructor.addByte(builder, i.toByte)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toByte).toList)
    },
    test("Array with Short primitives resizes correctly") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Short](2)
      for (i <- 1 to 10) constructor.addShort(builder, i.toShort)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toShort).toList)
    },
    test("Array with Long primitives resizes correctly") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Long](2)
      for (i <- 1 to 10) constructor.addLong(builder, i.toLong)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toLong).toList)
    },
    test("Array with Float primitives resizes correctly") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Float](2)
      for (i <- 1 to 10) constructor.addFloat(builder, i.toFloat)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toFloat).toList)
    },
    test("Array with Double primitives resizes correctly") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Double](2)
      for (i <- 1 to 10) constructor.addDouble(builder, i.toDouble)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toDouble).toList)
    },
    test("Array with Char primitives resizes correctly") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Char](2)
      for (c <- "abcdefghij") constructor.addChar(builder, c)
      val result = constructor.result(builder)
      assertTrue(result.toList == "abcdefghij".toList)
    },
    test("ArraySeq with Boolean primitives resizes correctly") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Boolean](2)
      for (i <- 1 to 10) constructor.addBoolean(builder, i % 2 == 0)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_ % 2 == 0).toList)
    },
    test("ArraySeq with Byte primitives resizes correctly") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Byte](2)
      for (i <- 1 to 10) constructor.addByte(builder, i.toByte)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toByte).toList)
    },
    test("ArraySeq with Short primitives resizes correctly") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Short](2)
      for (i <- 1 to 10) constructor.addShort(builder, i.toShort)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toShort).toList)
    },
    test("ArraySeq with Long primitives resizes correctly") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Long](2)
      for (i <- 1 to 10) constructor.addLong(builder, i.toLong)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toLong).toList)
    },
    test("ArraySeq with Float primitives resizes correctly") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Float](2)
      for (i <- 1 to 10) constructor.addFloat(builder, i.toFloat)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toFloat).toList)
    },
    test("ArraySeq with Double primitives resizes correctly") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Double](2)
      for (i <- 1 to 10) constructor.addDouble(builder, i.toDouble)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toDouble).toList)
    },
    test("ArraySeq with Char primitives resizes correctly") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Char](2)
      for (c <- "abcdefghij") constructor.addChar(builder, c)
      val result = constructor.result(builder)
      assertTrue(result.toList == "abcdefghij".toList)
    }
  )

  private def primitiveArraySuite = suite("primitive arrays")(
    test("Array constructor creates proper Boolean array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Boolean](2)
      constructor.addBoolean(builder, true)
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Boolean]] && result(0) == true)
    },
    test("Array constructor creates proper Byte array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Byte](2)
      constructor.addByte(builder, 42.toByte)
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Byte]] && result(0) == 42.toByte)
    },
    test("Array constructor creates proper Short array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Short](2)
      constructor.addShort(builder, 42.toShort)
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Short]] && result(0) == 42.toShort)
    },
    test("Array constructor creates proper Int array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Int](2)
      constructor.addInt(builder, 42)
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Int]] && result(0) == 42)
    },
    test("Array constructor creates proper Long array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Long](2)
      constructor.addLong(builder, 42L)
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Long]] && result(0) == 42L)
    },
    test("Array constructor creates proper Float array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Float](2)
      constructor.addFloat(builder, 42.0f)
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Float]] && result(0) == 42.0f)
    },
    test("Array constructor creates proper Double array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Double](2)
      constructor.addDouble(builder, 42.0)
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Double]] && result(0) == 42.0)
    },
    test("Array constructor creates proper Char array") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Char](2)
      constructor.addChar(builder, 'x')
      val result = constructor.result(builder)
      assertTrue(result.isInstanceOf[Array[Char]] && result(0) == 'x')
    },
    test("empty Array") {
      val constructor = SeqConstructor.arrayConstructor
      val result      = constructor.empty[Int]
      assertTrue(result.isEmpty && result.isInstanceOf[Array[Int]])
    },
    test("empty ArraySeq") {
      val constructor = SeqConstructor.arraySeqConstructor
      val result      = constructor.empty[Int]
      assertTrue(result.isEmpty)
    },
    test("generic add method works for Array with reference type") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[String](2)
      constructor.add(builder, "hello")
      constructor.add(builder, "world")
      val result = constructor.result(builder)
      assertTrue(result.toList == List("hello", "world"))
    },
    test("generic add method resizes Array with reference type") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[String](2)
      for (i <- 1 to 10) constructor.add(builder, s"item$i")
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(i => s"item$i").toList)
    }
  )

  private def otherConstructorsSuite = suite("other constructors full workflow")(
    test("Set newBuilder + add + result") {
      val c       = SeqConstructor.setConstructor
      val builder = c.newBuilder[Int](4)
      c.add(builder, 1)
      c.add(builder, 2)
      c.add(builder, 3)
      val result = c.result(builder)
      assertTrue(result == Set(1, 2, 3))
    },
    test("Vector newBuilder + add + result") {
      val c       = SeqConstructor.vectorConstructor
      val builder = c.newBuilder[Int](4)
      c.add(builder, 1)
      c.add(builder, 2)
      c.add(builder, 3)
      val result = c.result(builder)
      assertTrue(result == Vector(1, 2, 3))
    },
    test("IndexedSeq newBuilder + add + result") {
      val c       = SeqConstructor.indexedSeqConstructor
      val builder = c.newBuilder[Int](4)
      c.add(builder, 1)
      c.add(builder, 2)
      c.add(builder, 3)
      val result = c.result(builder)
      assertTrue(result == IndexedSeq(1, 2, 3))
    },
    test("Seq newBuilder + add + result") {
      val c       = SeqConstructor.seqConstructor
      val builder = c.newBuilder[Int](4)
      c.add(builder, 1)
      c.add(builder, 2)
      c.add(builder, 3)
      val result = c.result(builder)
      assertTrue(result == Seq(1, 2, 3))
    }
  )

  private def emptyConstructorsSuite = suite("empty constructors")(
    test("empty Set") {
      val c      = SeqConstructor.setConstructor
      val result = c.empty[String]
      assertTrue(result.isEmpty)
    },
    test("empty List") {
      val c      = SeqConstructor.listConstructor
      val result = c.empty[Int]
      assertTrue(result.isEmpty)
    },
    test("empty Vector") {
      val c      = SeqConstructor.vectorConstructor
      val result = c.empty[Double]
      assertTrue(result.isEmpty)
    },
    test("empty IndexedSeq") {
      val c      = SeqConstructor.indexedSeqConstructor
      val result = c.empty[Long]
      assertTrue(result.isEmpty)
    },
    test("empty Seq") {
      val c      = SeqConstructor.seqConstructor
      val result = c.empty[Short]
      assertTrue(result.isEmpty)
    },
    test("empty Chunk") {
      val c      = SeqConstructor.chunkConstructor
      val result = c.empty[Byte]
      assertTrue(result.isEmpty)
    }
  )

  private def sizeHintEdgeCasesSuite = suite("sizeHint edge cases")(
    test("Array newBuilder clamps sizeHint 0 to 1") {
      val c = SeqConstructor.arrayConstructor
      val b = c.newBuilder[Int](0)
      c.addInt(b, 1)
      c.addInt(b, 2)
      val result = c.result(b)
      assertTrue(result.toList == List(1, 2))
    },
    test("ArraySeq newBuilder clamps sizeHint 0 to 1") {
      val c = SeqConstructor.arraySeqConstructor
      val b = c.newBuilder[Int](0)
      c.addInt(b, 1)
      c.addInt(b, 2)
      val result = c.result(b)
      assertTrue(result.toList == List(1, 2))
    },
    test("Array newBuilder clamps negative sizeHint to 1") {
      val c = SeqConstructor.arrayConstructor
      val b = c.newBuilder[Int](-5)
      c.addInt(b, 42)
      val result = c.result(b)
      assertTrue(result.toList == List(42))
    },
    test("ArraySeq newBuilder clamps negative sizeHint to 1") {
      val c = SeqConstructor.arraySeqConstructor
      val b = c.newBuilder[Int](-5)
      c.addInt(b, 42)
      val result = c.result(b)
      assertTrue(result.toList == List(42))
    }
  )

  private def noResizeSuite = suite("no resize needed")(
    test("Array add within capacity does not resize") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Int](8)
      constructor.addInt(builder, 1)
      constructor.addInt(builder, 2)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1, 2))
    },
    test("ArraySeq add within capacity does not resize") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Int](8)
      constructor.addInt(builder, 1)
      constructor.addInt(builder, 2)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1, 2))
    },
    test("Array exactly fills capacity - no trim needed on result") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Int](4)
      constructor.addInt(builder, 1)
      constructor.addInt(builder, 2)
      constructor.addInt(builder, 3)
      constructor.addInt(builder, 4)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1, 2, 3, 4))
    },
    test("ArraySeq exactly fills capacity - no trim needed on result") {
      val constructor = SeqConstructor.arraySeqConstructor
      val builder     = constructor.newBuilder[Int](4)
      constructor.addInt(builder, 1)
      constructor.addInt(builder, 2)
      constructor.addInt(builder, 3)
      constructor.addInt(builder, 4)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1, 2, 3, 4))
    },
    test("Array Boolean within capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Boolean](4)
      constructor.addBoolean(builder, true)
      constructor.addBoolean(builder, false)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(true, false))
    },
    test("Array Byte within capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Byte](4)
      constructor.addByte(builder, 1.toByte)
      constructor.addByte(builder, 2.toByte)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.toByte, 2.toByte))
    },
    test("Array Short within capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Short](4)
      constructor.addShort(builder, 1.toShort)
      constructor.addShort(builder, 2.toShort)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.toShort, 2.toShort))
    },
    test("Array Long within capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Long](4)
      constructor.addLong(builder, 1L)
      constructor.addLong(builder, 2L)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1L, 2L))
    },
    test("Array Float within capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Float](4)
      constructor.addFloat(builder, 1.0f)
      constructor.addFloat(builder, 2.0f)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.0f, 2.0f))
    },
    test("Array Double within capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Double](4)
      constructor.addDouble(builder, 1.0)
      constructor.addDouble(builder, 2.0)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.0, 2.0))
    },
    test("Array Char within capacity") {
      val constructor = SeqConstructor.arrayConstructor
      val builder     = constructor.newBuilder[Char](4)
      constructor.addChar(builder, 'a')
      constructor.addChar(builder, 'b')
      val result = constructor.result(builder)
      assertTrue(result.toList == List('a', 'b'))
    }
  )

  private def testChunkBoolean(c: SeqConstructor[Chunk]): TestResult = {
    val builder: c.Builder[Boolean] = c.newBuilder[Boolean](2)(scala.reflect.ClassTag.Boolean)
    c.addBoolean(builder, true)
    c.addBoolean(builder, false)
    val result: Chunk[Boolean] = c.result(builder)
    assertTrue(result == Chunk(true, false))
  }

  private def testChunkInt(c: SeqConstructor[Chunk]): TestResult = {
    val builder: c.Builder[Int] = c.newBuilder[Int](2)(scala.reflect.ClassTag.Int)
    c.addInt(builder, 1)
    c.addInt(builder, 2)
    val result: Chunk[Int] = c.result(builder)
    assertTrue(result == Chunk(1, 2))
  }

  private def testChunkLong(c: SeqConstructor[Chunk]): TestResult = {
    val builder: c.Builder[Long] = c.newBuilder[Long](2)(scala.reflect.ClassTag.Long)
    c.addLong(builder, 1L)
    c.addLong(builder, 2L)
    val result: Chunk[Long] = c.result(builder)
    assertTrue(result == Chunk(1L, 2L))
  }

  private def testChunkDouble(c: SeqConstructor[Chunk]): TestResult = {
    val builder: c.Builder[Double] = c.newBuilder[Double](2)(scala.reflect.ClassTag.Double)
    c.addDouble(builder, 1.0)
    c.addDouble(builder, 2.0)
    val result: Chunk[Double] = c.result(builder)
    assertTrue(result == Chunk(1.0, 2.0))
  }
}
