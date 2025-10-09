package zio.blocks.schema.binding

import zio.test._
import zio.test.Assertion._

object SeqConstructorVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("SeqConstructorVersionSpecificSpec")(
    suite("automatic growing and trimming of builder buffer when adding values using iarray constructor")(
      test("object values") {
        val builder = SeqConstructor.iArrayConstructor.newObjectBuilder[AnyRef](0)
        Seq("test1", "test2", "test3").foreach(SeqConstructor.iArrayConstructor.addObject[AnyRef](builder, _))
        val length = SeqConstructor.iArrayConstructor.resultObject[AnyRef](builder).length
        assert(length)(equalTo(3))
      },
      test("boolean values") {
        val builder = SeqConstructor.iArrayConstructor.newBooleanBuilder(0)
        Seq(true, false, true).foreach(SeqConstructor.iArrayConstructor.addBoolean(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultBoolean(builder).length
        assert(length)(equalTo(3))
      },
      test("byte values") {
        val builder = SeqConstructor.iArrayConstructor.newByteBuilder(0)
        Seq(1: Byte, 2: Byte, 3: Byte).foreach(SeqConstructor.iArrayConstructor.addByte(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultByte(builder).length
        assert(length)(equalTo(3))
      },
      test("short values") {
        val builder = SeqConstructor.iArrayConstructor.newShortBuilder(0)
        Seq(1: Short, 2: Short, 3: Short).foreach(SeqConstructor.iArrayConstructor.addShort(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultShort(builder).length
        assert(length)(equalTo(3))
      },
      test("int values") {
        val builder = SeqConstructor.iArrayConstructor.newIntBuilder(0)
        Seq(1, 2, 3).foreach(SeqConstructor.iArrayConstructor.addInt(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultInt(builder).length
        assert(length)(equalTo(3))
      },
      test("long values") {
        val builder = SeqConstructor.iArrayConstructor.newLongBuilder(0)
        Seq(1L, 2L, 3L).foreach(SeqConstructor.iArrayConstructor.addLong(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultLong(builder).length
        assert(length)(equalTo(3))
      },
      test("float values") {
        val builder = SeqConstructor.iArrayConstructor.newFloatBuilder(0)
        Seq(1.0f, 2.0f, 3.0f).foreach(SeqConstructor.iArrayConstructor.addFloat(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultFloat(builder).length
        assert(length)(equalTo(3))
      },
      test("double values") {
        val builder = SeqConstructor.iArrayConstructor.newDoubleBuilder(0)
        Seq(1.0, 2.0, 3.0).foreach(SeqConstructor.iArrayConstructor.addDouble(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultDouble(builder).length
        assert(length)(equalTo(3))
      },
      test("char values") {
        val builder = SeqConstructor.iArrayConstructor.newCharBuilder(0)
        Seq('1', '2', '3').foreach(SeqConstructor.iArrayConstructor.addChar(builder, _))
        val length = SeqConstructor.iArrayConstructor.resultChar(builder).length
        assert(length)(equalTo(3))
      }
    )
  )
}
