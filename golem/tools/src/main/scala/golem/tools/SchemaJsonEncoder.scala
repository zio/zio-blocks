package golem.tools

import golem.data._
import ujson._

object SchemaJsonEncoder {
  def encode(schema: StructuredSchema): Value =
    schema match {
      case StructuredSchema.Tuple(elements) =>
        Obj(
          "tag" -> Str("tuple"),
          "val" -> Arr(elements.map(encodeElement): _*)
        )
      case StructuredSchema.Multimodal(elements) =>
        Obj(
          "tag" -> Str("multimodal"),
          "val" -> Arr(elements.map(encodeElement): _*)
        )
    }

  private def encodeElement(element: NamedElementSchema): Value =
    Arr(Str(element.name), encodeElementSchema(element.schema))

  private def encodeElementSchema(schema: ElementSchema): Value =
    schema match {
      case ElementSchema.Component(dataType) =>
        Obj(
          "tag" -> Str("component-model"),
          "val" -> WitTypeBuilderJson.build(dataType)
        )
      case ElementSchema.UnstructuredText(restrictions) =>
        Obj(
          "tag" -> Str("unstructured-text"),
          "val" -> encodeTextRestrictions(restrictions)
        )
      case ElementSchema.UnstructuredBinary(restrictions) =>
        Obj(
          "tag" -> Str("unstructured-binary"),
          "val" -> encodeBinaryRestrictions(restrictions)
        )
    }

  private def encodeTextRestrictions(restrictions: Option[List[String]]): Value =
    restrictions match {
      case Some(values) =>
        val arr = Arr(values.map(code => Obj("language-code" -> Str(code))): _*)
        Obj("tag" -> Str("some"), "val" -> arr)
      case None =>
        Obj("tag" -> Str("none"))
    }

  private def encodeBinaryRestrictions(restrictions: Option[List[String]]): Value =
    restrictions match {
      case Some(values) =>
        val arr = Arr(values.map(mime => Obj("mime-type" -> Str(mime))): _*)
        Obj("tag" -> Str("some"), "val" -> arr)
      case None =>
        Obj("tag" -> Str("none"))
    }

  private object WitTypeBuilderJson {

    def build(dataType: DataType): Value = {
      val builder = new Builder
      builder.buildNode(dataType)
      builder.result()
    }

    private final class Builder {
      private val nodes = collection.mutable.ArrayBuffer.empty[Value]

      def result(): Value =
        Obj("nodes" -> Arr(nodes.toSeq: _*))

      def buildNode(dataType: DataType): Int = {
        val index       = newNode()
        val node: Value = dataType match {
          case DataType.UnitType =>
            tupleType(Seq.empty)
          case DataType.StringType =>
            tagOnly("prim-string-type")
          case DataType.BoolType =>
            tagOnly("prim-bool-type")
          case DataType.IntType =>
            tagOnly("prim-s32-type")
          case DataType.LongType =>
            tagOnly("prim-s64-type")
          case DataType.DoubleType =>
            tagOnly("prim-f64-type")
          case DataType.BigDecimalType =>
            tagOnly("prim-string-type")
          case DataType.UUIDType =>
            tagOnly("prim-string-type")
          case DataType.BytesType =>
            listType(buildNode(DataType.IntType))
          case DataType.Optional(of) =>
            optionType(buildNode(of))
          case DataType.ListType(of) =>
            listType(buildNode(of))
          case DataType.SetType(of) =>
            listType(buildNode(of))
          case DataType.MapType(valueType) =>
            val entryStruct = DataType.StructType(
              List(
                DataType.Field("key", DataType.StringType, optional = false),
                DataType.Field("value", valueType, optional = false)
              )
            )
            val entryIndex = buildNode(entryStruct)
            listType(entryIndex)
          case DataType.TupleType(elements) =>
            tupleType(elements.map(buildNode))
          case DataType.StructType(fields) =>
            val fieldEntries = fields.map { field =>
              val idx = buildNode(field.dataType)
              Arr(Str(field.name), Num(idx))
            }
            recordType(fieldEntries)
          case DataType.EnumType(cases) =>
            val variantEntries = cases.map { enumCase =>
              val payloadIndex        = enumCase.payload.map(buildNode)
              val payloadValue: Value = payloadIndex match {
                case Some(value) => Num(value)
                case None        => Null
              }
              Arr(Str(enumCase.name), payloadValue)
            }
            variantType(variantEntries)
        }

        nodes(index) = node
        index
      }

      private def tagOnly(tag: String): Value =
        Obj("tag" -> Str(tag))

      private def newNode(): Int = {
        nodes += Obj()
        nodes.length - 1
      }

      private def tupleType(values: Seq[Int]): Value =
        Obj("tag" -> Str("tuple-type"), "val" -> Arr(values.map(Num(_)): _*))

      private def listType(of: Int): Value =
        Obj("tag" -> Str("list-type"), "val" -> Num(of))

      private def optionType(of: Int): Value =
        Obj("tag" -> Str("option-type"), "val" -> Num(of))

      private def recordType(fields: Seq[Value]): Value =
        Obj("tag" -> Str("record-type"), "val" -> Arr(fields: _*))

      private def variantType(entries: Seq[Value]): Value =
        Obj("tag" -> Str("variant-type"), "val" -> Arr(entries: _*))
    }
  }
}
