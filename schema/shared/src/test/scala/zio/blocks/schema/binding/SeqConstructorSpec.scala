package zio.blocks.schema.binding

import zio.test._
import zio.test.Assertion._

object SeqConstructorSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("SeqConstructorSpec")(
    suite("automatic growing and trimming of builder buffer when adding values using array constructor")(
      test("object values") {
        val builder = SeqConstructor.arrayConstructor.newObjectBuilder[AnyRef](0)
        Seq("test1", "test2", "test3").foreach(SeqConstructor.arrayConstructor.addObject[AnyRef](builder, _))
        val length = SeqConstructor.arrayConstructor.resultObject[AnyRef](builder).length
        assert(length)(equalTo(3))
      },
      test("boolean values") {
        val builder = SeqConstructor.arrayConstructor.newBooleanBuilder(0)
        Seq(true, false, true).foreach(SeqConstructor.arrayConstructor.addBoolean(builder, _))
        val length = SeqConstructor.arrayConstructor.resultBoolean(builder).length
        assert(length)(equalTo(3))
      },
      test("byte values") {
        val builder = SeqConstructor.arrayConstructor.newByteBuilder(0)
        Seq(1: Byte, 2: Byte, 3: Byte).foreach(SeqConstructor.arrayConstructor.addByte(builder, _))
        val length = SeqConstructor.arrayConstructor.resultByte(builder).length
        assert(length)(equalTo(3))
      },
      test("short values") {
        val builder = SeqConstructor.arrayConstructor.newShortBuilder(0)
        Seq(1: Short, 2: Short, 3: Short).foreach(SeqConstructor.arrayConstructor.addShort(builder, _))
        val length = SeqConstructor.arrayConstructor.resultShort(builder).length
        assert(length)(equalTo(3))
      },
      test("int values") {
        val builder = SeqConstructor.arrayConstructor.newIntBuilder(0)
        Seq(1, 2, 3).foreach(SeqConstructor.arrayConstructor.addInt(builder, _))
        val length = SeqConstructor.arrayConstructor.resultInt(builder).length
        assert(length)(equalTo(3))
      },
      test("long values") {
        val builder = SeqConstructor.arrayConstructor.newLongBuilder(0)
        Seq(1L, 2L, 3L).foreach(SeqConstructor.arrayConstructor.addLong(builder, _))
        val length = SeqConstructor.arrayConstructor.resultLong(builder).length
        assert(length)(equalTo(3))
      },
      test("float values") {
        val builder = SeqConstructor.arrayConstructor.newFloatBuilder(0)
        Seq(1.0f, 2.0f, 3.0f).foreach(SeqConstructor.arrayConstructor.addFloat(builder, _))
        val length = SeqConstructor.arrayConstructor.resultFloat(builder).length
        assert(length)(equalTo(3))
      },
      test("double values") {
        val builder = SeqConstructor.arrayConstructor.newDoubleBuilder(0)
        Seq(1.0, 2.0, 3.0).foreach(SeqConstructor.arrayConstructor.addDouble(builder, _))
        val length = SeqConstructor.arrayConstructor.resultDouble(builder).length
        assert(length)(equalTo(3))
      },
      test("char values") {
        val builder = SeqConstructor.arrayConstructor.newCharBuilder(0)
        Seq('1', '2', '3').foreach(SeqConstructor.arrayConstructor.addChar(builder, _))
        val length = SeqConstructor.arrayConstructor.resultChar(builder).length
        assert(length)(equalTo(3))
      }
    ),
    suite("automatic growing and trimming of builder buffer when adding values using array sequence constructor")(
      test("object values") {
        val builder = SeqConstructor.arraySeqConstructor.newObjectBuilder[AnyRef](0)
        Seq("test1", "test2", "test3").foreach(SeqConstructor.arraySeqConstructor.addObject[AnyRef](builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultObject[AnyRef](builder).length
        assert(length)(equalTo(3))
      },
      test("boolean values") {
        val builder = SeqConstructor.arraySeqConstructor.newBooleanBuilder(0)
        Seq(true, false, true).foreach(SeqConstructor.arraySeqConstructor.addBoolean(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultBoolean(builder).length
        assert(length)(equalTo(3))
      },
      test("byte values") {
        val builder = SeqConstructor.arraySeqConstructor.newByteBuilder(0)
        Seq(1: Byte, 2: Byte, 3: Byte).foreach(SeqConstructor.arraySeqConstructor.addByte(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultByte(builder).length
        assert(length)(equalTo(3))
      },
      test("short values") {
        val builder = SeqConstructor.arraySeqConstructor.newShortBuilder(0)
        Seq(1: Short, 2: Short, 3: Short).foreach(SeqConstructor.arraySeqConstructor.addShort(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultShort(builder).length
        assert(length)(equalTo(3))
      },
      test("int values") {
        val builder = SeqConstructor.arraySeqConstructor.newIntBuilder(0)
        Seq(1, 2, 3).foreach(SeqConstructor.arraySeqConstructor.addInt(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultInt(builder).length
        assert(length)(equalTo(3))
      },
      test("long values") {
        val builder = SeqConstructor.arraySeqConstructor.newLongBuilder(0)
        Seq(1L, 2L, 3L).foreach(SeqConstructor.arraySeqConstructor.addLong(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultLong(builder).length
        assert(length)(equalTo(3))
      },
      test("float values") {
        val builder = SeqConstructor.arraySeqConstructor.newFloatBuilder(0)
        Seq(1.0f, 2.0f, 3.0f).foreach(SeqConstructor.arraySeqConstructor.addFloat(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultFloat(builder).length
        assert(length)(equalTo(3))
      },
      test("double values") {
        val builder = SeqConstructor.arraySeqConstructor.newDoubleBuilder(0)
        Seq(1.0, 2.0, 3.0).foreach(SeqConstructor.arraySeqConstructor.addDouble(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultDouble(builder).length
        assert(length)(equalTo(3))
      },
      test("char values") {
        val builder = SeqConstructor.arraySeqConstructor.newCharBuilder(0)
        Seq('1', '2', '3').foreach(SeqConstructor.arraySeqConstructor.addChar(builder, _))
        val length = SeqConstructor.arraySeqConstructor.resultChar(builder).length
        assert(length)(equalTo(3))
      }
    )
  )
}
