package zio.blocks.schema.binding

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object SeqConstructorVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SeqConstructor (Scala 3)")(
    iArraySuite
  )

  private def iArraySuite = suite("IArray")(
    test("empty IArray") {
      val c      = SeqConstructor.iArrayConstructor
      val result = c.empty[Int]
      assertTrue(result.isEmpty)
    },
    test("IArray add within capacity") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Int](8)
      constructor.addInt(builder, 1)
      constructor.addInt(builder, 2)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1, 2))
    },
    test("IArray resizes when adding more elements than initial capacity") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Int](2)
      for (i <- 1 to 10) constructor.addInt(builder, i)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).toList)
    },
    test("IArray exactly fills capacity - no trim needed") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Int](4)
      constructor.addInt(builder, 1)
      constructor.addInt(builder, 2)
      constructor.addInt(builder, 3)
      constructor.addInt(builder, 4)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1, 2, 3, 4))
    },
    test("IArray with Boolean primitives") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Boolean](4)
      constructor.addBoolean(builder, true)
      constructor.addBoolean(builder, false)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(true, false))
    },
    test("IArray with Byte primitives") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Byte](4)
      constructor.addByte(builder, 1.toByte)
      constructor.addByte(builder, 2.toByte)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.toByte, 2.toByte))
    },
    test("IArray with Short primitives") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Short](4)
      constructor.addShort(builder, 1.toShort)
      constructor.addShort(builder, 2.toShort)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.toShort, 2.toShort))
    },
    test("IArray with Long primitives") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Long](4)
      constructor.addLong(builder, 1L)
      constructor.addLong(builder, 2L)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1L, 2L))
    },
    test("IArray with Float primitives") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Float](4)
      constructor.addFloat(builder, 1.0f)
      constructor.addFloat(builder, 2.0f)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.0f, 2.0f))
    },
    test("IArray with Double primitives") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Double](4)
      constructor.addDouble(builder, 1.0)
      constructor.addDouble(builder, 2.0)
      val result = constructor.result(builder)
      assertTrue(result.toList == List(1.0, 2.0))
    },
    test("IArray with Char primitives") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Char](4)
      constructor.addChar(builder, 'a')
      constructor.addChar(builder, 'b')
      val result = constructor.result(builder)
      assertTrue(result.toList == List('a', 'b'))
    },
    test("IArray Boolean resizes correctly") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Boolean](2)
      for (i <- 1 to 10) constructor.addBoolean(builder, i % 2 == 0)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_ % 2 == 0).toList)
    },
    test("IArray Byte resizes correctly") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Byte](2)
      for (i <- 1 to 10) constructor.addByte(builder, i.toByte)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toByte).toList)
    },
    test("IArray Short resizes correctly") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Short](2)
      for (i <- 1 to 10) constructor.addShort(builder, i.toShort)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toShort).toList)
    },
    test("IArray Long resizes correctly") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Long](2)
      for (i <- 1 to 10) constructor.addLong(builder, i.toLong)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toLong).toList)
    },
    test("IArray Float resizes correctly") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Float](2)
      for (i <- 1 to 10) constructor.addFloat(builder, i.toFloat)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toFloat).toList)
    },
    test("IArray Double resizes correctly") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Double](2)
      for (i <- 1 to 10) constructor.addDouble(builder, i.toDouble)
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(_.toDouble).toList)
    },
    test("IArray Char resizes correctly") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[Char](2)
      for (c <- "abcdefghij") constructor.addChar(builder, c)
      val result = constructor.result(builder)
      assertTrue(result.toList == "abcdefghij".toList)
    },
    test("IArray generic add with reference type") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[String](2)
      constructor.add(builder, "hello")
      constructor.add(builder, "world")
      val result = constructor.result(builder)
      assertTrue(result.toList == List("hello", "world"))
    },
    test("IArray generic add resizes with reference type") {
      val constructor = SeqConstructor.iArrayConstructor
      val builder     = constructor.newBuilder[String](2)
      for (i <- 1 to 10) constructor.add(builder, s"item$i")
      val result = constructor.result(builder)
      assertTrue(result.toList == (1 to 10).map(i => s"item$i").toList)
    },
    test("IArray newBuilder clamps sizeHint 0 to 1") {
      val c = SeqConstructor.iArrayConstructor
      val b = c.newBuilder[Int](0)
      c.addInt(b, 1)
      c.addInt(b, 2)
      val result = c.result(b)
      assertTrue(result.toList == List(1, 2))
    },
    test("IArray newBuilder clamps negative sizeHint to 1") {
      val c = SeqConstructor.iArrayConstructor
      val b = c.newBuilder[Int](-5)
      c.addInt(b, 42)
      val result = c.result(b)
      assertTrue(result.toList == List(42))
    }
  )
}
