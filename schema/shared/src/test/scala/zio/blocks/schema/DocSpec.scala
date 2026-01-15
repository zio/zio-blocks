package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object DocSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DocSpec")(
    suite("Doc.Empty")(
      test("has consistent equals and hashCode") {
        assertTrue(Doc.Empty == Doc.Empty) &&
        assert(Doc.Empty.hashCode)(equalTo(Doc.Empty.hashCode)) &&
        assert(Doc("text"))(not(equalTo(Doc.Empty))) &&
        assert(Doc("text") + Doc("text2"))(not(equalTo(Doc.Empty))) &&
        assert(Doc.Empty)(not(equalTo("test": Any)))
      }
    ),
    suite("Doc.Text")(
      test("has consistent equals and hashCode") {
        val docText1 = Doc.Text("text")
        val docText2 = Doc.Text("text")
        assert(docText1)(equalTo(docText1)) &&
        assert(docText1.hashCode)(equalTo(docText1.hashCode)) &&
        assert(docText2)(equalTo(docText1)) &&
        assert(docText2.hashCode)(equalTo(docText1.hashCode)) &&
        assert(Doc("text1"))(not(equalTo(docText1))) &&
        assert(Doc("text1") + Doc("text2"))(not(equalTo(docText1))) &&
        assert(docText1)(not(equalTo("test": Any)))
      }
    ),
    suite("Doc.Concat")(
      test("has consistent equals and hashCode") {
        val docText1   = Doc.Text("text")
        val docText2   = Doc.Text("text")
        val docConcat1 = Doc.Concat(docText1, docText2)
        val docConcat2 = Doc.Concat(docText2, docText1)
        val docConcat3 = Doc.Concat(docText1, Doc.Concat(Doc.Empty, docText2))
        val docConcat4 = Doc.Concat(docText1, Doc.Concat(docText2, Doc.Empty))
        val docConcat5 = Doc.Concat(Doc.Empty, Doc.Empty)
        val docConcat6 = Doc.Concat(docText1, Doc.Empty)
        val docConcat7 = Doc.Concat(Doc.Empty, docText1)
        assert(docConcat1)(equalTo(docConcat1)) &&
        assert(docConcat1.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(docConcat2)(equalTo(docConcat1)) &&
        assert(docConcat2.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(docConcat3)(equalTo(docConcat1)) &&
        assert(docConcat3.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(docConcat4)(equalTo(docConcat1)) &&
        assert(docConcat4.hashCode)(equalTo(docConcat1.hashCode)) &&
        assert(Doc.Empty: Doc)(equalTo(docConcat5)) &&
        assert(docConcat5)(equalTo(Doc.Empty: Doc)) &&
        assert(docConcat6)(equalTo(docConcat7)) &&
        assert(docConcat7)(equalTo(docConcat6)) &&
        assert(docConcat7)(equalTo(docText1: Doc)) &&
        assert(docText1: Doc)(equalTo(docConcat7)) &&
        assert(Doc("text"))(not(equalTo(docConcat1))) &&
        assert(Doc.Empty: Doc)(not(equalTo(docConcat1))) &&
        assert(docConcat1)(not(equalTo("test": Any)))
      }
    )
  )
}
