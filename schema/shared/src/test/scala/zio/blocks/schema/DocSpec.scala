package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.docs.{Doc, Paragraph, Inline}
import zio.test.Assertion._
import zio.test._

object DocSpec extends SchemaBaseSpec {

  private def textDoc(s: String): Doc =
    Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))

  def spec: Spec[TestEnvironment, Any] = suite("DocSpec")(
    suite("Doc.empty")(
      test("has consistent equals and hashCode") {
        assertTrue(Doc.empty == Doc.empty) &&
        assert(Doc.empty.hashCode)(equalTo(Doc.empty.hashCode)) &&
        assert(textDoc("text"))(not(equalTo(Doc.empty))) &&
        assert(textDoc("text") ++ textDoc("text2"))(not(equalTo(Doc.empty))) &&
        assert(Doc.empty)(not(equalTo("test": Any)))
      }
    ),
    suite("Doc with content")(
      test("has consistent equals and hashCode") {
        val doc1 = textDoc("text")
        val doc2 = textDoc("text")
        assert(doc1)(equalTo(doc1)) &&
        assert(doc1.hashCode)(equalTo(doc1.hashCode)) &&
        assert(doc2)(equalTo(doc1)) &&
        assert(doc2.hashCode)(equalTo(doc1.hashCode)) &&
        assert(textDoc("text1"))(not(equalTo(doc1))) &&
        assert(textDoc("text1") ++ textDoc("text2"))(not(equalTo(doc1))) &&
        assert(doc1)(not(equalTo("test": Any)))
      }
    ),
    suite("Doc concatenation")(
      test("has consistent equals and hashCode") {
        val doc1       = textDoc("text")
        val doc2       = textDoc("text")
        val docConcat1 = doc1 ++ doc2
        val docConcat2 = doc2 ++ doc1
        val docConcat3 = doc1 ++ (Doc.empty ++ doc2)
        val docConcat4 = doc1 ++ (doc2 ++ Doc.empty)
        val docConcat5 = Doc.empty ++ Doc.empty
        val docConcat6 = doc1 ++ Doc.empty
        val docConcat7 = Doc.empty ++ doc1
        assert(docConcat1)(equalTo(docConcat1)) &&
        assert(docConcat1.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(docConcat2)(equalTo(docConcat1)) &&
        assert(docConcat2.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(docConcat3)(equalTo(docConcat1)) &&
        assert(docConcat3.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(docConcat4)(equalTo(docConcat1)) &&
        assert(docConcat4.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(Doc.empty: Doc)(equalTo(docConcat5)) &&
        assert(docConcat5)(equalTo(Doc.empty: Doc)) &&
        assert(docConcat6)(equalTo(docConcat7)) &&
        assert(docConcat7)(equalTo(docConcat6)) &&
        assert(docConcat7)(equalTo(doc1: Doc)) &&
        assert(doc1: Doc)(equalTo(docConcat7)) &&
        assert(textDoc("text"))(not(equalTo(docConcat1))) &&
        assert(Doc.empty: Doc)(not(equalTo(docConcat1))) &&
        assert(docConcat1)(not(equalTo("test": Any)))
      }
    ),
    suite("Doc round-trip through Schema")(
      test("Doc.empty round-trips through DynamicValue") {
        val schema = Schema[Doc]
        val result = schema.fromDynamicValue(schema.toDynamicValue(Doc.empty))
        assertTrue(result == Right(Doc.empty))
      },
      test("Doc with text content round-trips through DynamicValue") {
        val schema = Schema[Doc]
        val doc    = textDoc("Hello World")
        val result = schema.fromDynamicValue(schema.toDynamicValue(doc))
        assertTrue(result == Right(doc))
      }
    )
  )
}
