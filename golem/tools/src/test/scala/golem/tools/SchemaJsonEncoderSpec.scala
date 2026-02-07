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

  test("encodes tuple, list, option, record, and variant component types") {
    val tupleType  = DataType.TupleType(List(DataType.IntType, DataType.StringType))
    val listType   = DataType.ListType(DataType.BoolType)
    val optType    = DataType.Optional(DataType.DoubleType)
    val recordType =
      DataType.StructType(
        List(
          DataType.Field("id", DataType.IntType, optional = false),
          DataType.Field("name", DataType.StringType, optional = false)
        )
      )
    val variantType =
      DataType.EnumType(
        List(
          DataType.EnumCase("On", None),
          DataType.EnumCase("Off", Some(DataType.StringType))
        )
      )

    val schema = StructuredSchema.Tuple(
      List(
        NamedElementSchema("tuple", ElementSchema.Component(tupleType)),
        NamedElementSchema("list", ElementSchema.Component(listType)),
        NamedElementSchema("opt", ElementSchema.Component(optType)),
        NamedElementSchema("record", ElementSchema.Component(recordType)),
        NamedElementSchema("variant", ElementSchema.Component(variantType))
      )
    )

    val encoded = SchemaJsonEncoder.encode(schema)
    val nodes   =
      encoded
        .obj("val")
        .arr
        .flatMap { element =>
          element.arr(1).obj("val").obj("nodes").arr.map(_.obj("tag").str)
        }
        .toSet

    nodes should contain("tuple-type")
    nodes should contain("list-type")
    nodes should contain("option-type")
    nodes should contain("record-type")
    nodes should contain("variant-type")
  }

  test("encodes map types as list of key/value entries") {
    val schema = StructuredSchema.single(
      ElementSchema.Component(DataType.MapType(DataType.StringType))
    )
    val encoded = SchemaJsonEncoder.encode(schema)
    val nodes   = encoded
      .obj("val")
      .arr(0)
      .arr(1)
      .obj("val")
      .obj("nodes")
      .arr
      .map(_.obj("tag").str)
      .toSet

    nodes should contain("list-type")
    nodes should contain("record-type")
  }

  test("encodes unstructured text and binary restrictions") {
    val schema = StructuredSchema.Multimodal(
      List(
        NamedElementSchema("textSome", ElementSchema.UnstructuredText(Some(List("en")))),
        NamedElementSchema("textNone", ElementSchema.UnstructuredText(None)),
        NamedElementSchema("binSome", ElementSchema.UnstructuredBinary(Some(List("image/png")))),
        NamedElementSchema("binNone", ElementSchema.UnstructuredBinary(None))
      )
    )
    val encoded  = SchemaJsonEncoder.encode(schema)
    val elements = encoded.obj("val").arr

    elements(0).arr(1).obj("tag").str shouldBe "unstructured-text"
    elements(0).arr(1).obj("val").obj("tag").str shouldBe "some"
    elements(1).arr(1).obj("val").obj("tag").str shouldBe "none"
    elements(2).arr(1).obj("tag").str shouldBe "unstructured-binary"
    elements(2).arr(1).obj("val").obj("tag").str shouldBe "some"
    elements(3).arr(1).obj("val").obj("tag").str shouldBe "none"
  }

  test("encodes primitive and bytes component types") {
    val schema = StructuredSchema.Tuple(
      List(
        NamedElementSchema("unit", ElementSchema.Component(DataType.UnitType)),
        NamedElementSchema("bool", ElementSchema.Component(DataType.BoolType)),
        NamedElementSchema("string", ElementSchema.Component(DataType.StringType)),
        NamedElementSchema("long", ElementSchema.Component(DataType.LongType)),
        NamedElementSchema("double", ElementSchema.Component(DataType.DoubleType)),
        NamedElementSchema("big", ElementSchema.Component(DataType.BigDecimalType)),
        NamedElementSchema("uuid", ElementSchema.Component(DataType.UUIDType)),
        NamedElementSchema("bytes", ElementSchema.Component(DataType.BytesType))
      )
    )
    val encoded = SchemaJsonEncoder.encode(schema)
    val tags    =
      encoded
        .obj("val")
        .arr
        .flatMap(_.arr(1).obj("val").obj("nodes").arr.map(_.obj("tag").str))
        .toSet

    tags should contain("prim-bool-type")
    tags should contain("prim-string-type")
    tags should contain("prim-s64-type")
    tags should contain("prim-f64-type")
    tags should contain("tuple-type")
    tags should contain("list-type")
  }
}
