package golem.data.unstructured

import golem.data._
import zio.test._
import zio.test.Assertion._

object UnstructuredSpec extends ZIOSpecDefault {

  sealed trait Lang
  object Lang {
    implicit val allowed: AllowedLanguages[Lang] = new AllowedLanguages[Lang] {
      override val codes: Option[List[String]] = Some(List("en", "es"))
    }
  }

  sealed trait Mime
  object Mime {
    implicit val allowed: AllowedMimeTypes[Mime] = new AllowedMimeTypes[Mime] {
      override val mimeTypes: Option[List[String]] = Some(List("image/png", "image/jpeg"))
    }
  }

  override def spec: Spec[TestEnvironment, Any] =
    suite("UnstructuredSpec")(
      test("text segments encode and decode with language constraints") {
        val schema  = implicitly[GolemSchema[TextSegment[Lang]]]
        val value   = TextSegment.inline[Lang]("hello", Some("en"))
        val encoded = schema.encode(value)
        val decoded = encoded.flatMap(schema.decode)

        assert(decoded)(isRight(equalTo(value))) &&
        assertTrue(schema.schema.isInstanceOf[StructuredSchema.Tuple]) &&
        assertTrue(
          schema.schema
            .asInstanceOf[StructuredSchema.Tuple]
            .elements
            .head
            .schema == ElementSchema.UnstructuredText(Lang.allowed.codes)
        )
      },
      test("text segment url constructor round-trips") {
        val schema  = implicitly[GolemSchema[TextSegment[Lang]]]
        val value   = TextSegment.url[Lang]("https://example.com/text.txt")
        val encoded = schema.encode(value)
        val decoded = encoded.flatMap(schema.decode)

        assert(decoded)(isRight(equalTo(value)))
      },
      test("text segment inline uses default language") {
        val schema  = implicitly[GolemSchema[TextSegment[Lang]]]
        val value   = TextSegment.inline[Lang]("plain")
        val encoded = schema.encode(value)
        val decoded = encoded.flatMap(schema.decode)

        assert(decoded)(isRight(equalTo(value)))
      },
      test("text segments reject component-model elements") {
        val schema = implicitly[GolemSchema[TextSegment[Lang]]]
        val value  = StructuredValue.single(ElementValue.Component(DataValue.StringValue("oops")))

        assert(schema.decode(value))(isLeft)
      },
      test("binary segments encode and decode with MIME constraints") {
        val schema  = implicitly[GolemSchema[BinarySegment[Mime]]]
        val value   = BinarySegment.inline[Mime](Array[Byte](9, 8), "image/png")
        val encoded = schema.encode(value)
        val decoded = encoded.flatMap(schema.decode)

        assert(decoded)(isRight(equalTo(value))) &&
        assertTrue(schema.schema.isInstanceOf[StructuredSchema.Tuple]) &&
        assertTrue(
          schema.schema
            .asInstanceOf[StructuredSchema.Tuple]
            .elements
            .head
            .schema == ElementSchema.UnstructuredBinary(Mime.allowed.mimeTypes)
        )
      },
      test("binary segment url constructor round-trips") {
        val schema  = implicitly[GolemSchema[BinarySegment[Mime]]]
        val value   = BinarySegment.url[Mime]("https://example.com/blob.png")
        val encoded = schema.encode(value)
        val decoded = encoded.flatMap(schema.decode)

        assert(decoded)(isRight(equalTo(value)))
      },
      test("binary segments reject component-model elements") {
        val schema = implicitly[GolemSchema[BinarySegment[Mime]]]
        val value  = StructuredValue.single(ElementValue.Component(DataValue.StringValue("oops")))

        assert(schema.decode(value))(isLeft)
      }
    )
}
