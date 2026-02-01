package golem.tools

import golem.data._
import golem.runtime.{AgentMetadata, MethodMetadata}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SchemaJsonEncoderSpec extends AnyFunSuite with Matchers {

  test("encodes tuple schemas with component elements") {
    val schema = StructuredSchema.Tuple(
      List(
        NamedElementSchema("name", ElementSchema.Component(DataType.StringType)),
        NamedElementSchema("age", ElementSchema.Component(DataType.IntType))
      )
    )
    val encoded = SchemaJsonEncoder.encode(schema)

    encoded.obj("tag").str shouldBe "tuple"
    encoded.obj("val").arr.length shouldBe 2
  }

  test("encodes multimodal schemas with text and binary restrictions") {
    val schema = StructuredSchema.Multimodal(
      List(
        NamedElementSchema("text", ElementSchema.UnstructuredText(Some(List("en", "es")))),
        NamedElementSchema("image", ElementSchema.UnstructuredBinary(None))
      )
    )
    val encoded = SchemaJsonEncoder.encode(schema)

    encoded.obj("tag").str shouldBe "multimodal"
    val elements = encoded.obj("val").arr
    elements.length shouldBe 2
    elements.head.arr(1).obj("tag").str shouldBe "unstructured-text"
    elements(1).arr(1).obj("tag").str shouldBe "unstructured-binary"
  }

  test("encodes agent metadata with methods") {
    val inputSchema  = StructuredSchema.single(ElementSchema.Component(DataType.StringType))
    val outputSchema = StructuredSchema.single(ElementSchema.Component(DataType.BoolType))

    val method = MethodMetadata(
      name = "ping",
      description = Some("check connectivity"),
      prompt = Some("reply true if reachable"),
      mode = None,
      input = inputSchema,
      output = outputSchema
    )
    val metadata = AgentMetadata(
      name = "HealthAgent",
      description = Some("health checks"),
      mode = None,
      methods = List(method),
      constructor = StructuredSchema.Tuple(Nil)
    )

    val encoded = AgentTypeJsonEncoder.encode("HealthAgent", metadata)

    encoded.obj("name").str shouldBe "HealthAgent"
    val methods = encoded.obj("methods").arr
    methods.length shouldBe 1
    methods.head.obj("name").str shouldBe "ping"
    methods.head.obj("prompt").str shouldBe "reply true if reachable"
  }
}
