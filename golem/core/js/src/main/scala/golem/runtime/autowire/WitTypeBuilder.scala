package golem.runtime.autowire

import golem.data.DataType
import golem.host.js._

import scala.scalajs.js

private[autowire] object WitTypeBuilder {
  def build(dataType: DataType): JsWitType = {
    val builder = new Builder
    builder.buildNode(dataType)
    builder.result()
  }

  private final class Builder {
    private val nodes = js.Array[JsNamedWitTypeNode]()

    def result(): JsWitType =
      JsWitType(nodes)

    def buildNode(dataType: DataType): Int = {
      val index       = newNode()
      val typeVariant = dataType match {
        case DataType.UnitType =>
          JsWitTypeNode.tupleType(js.Array[JsNodeIndex]())
        case DataType.StringType =>
          JsWitTypeNode.primStringType
        case DataType.BoolType =>
          JsWitTypeNode.primBoolType
        case DataType.IntType =>
          JsWitTypeNode.primS32Type
        case DataType.LongType =>
          JsWitTypeNode.primS64Type
        case DataType.DoubleType =>
          JsWitTypeNode.primF64Type
        case DataType.BigDecimalType =>
          JsWitTypeNode.primStringType
        case DataType.UUIDType =>
          JsWitTypeNode.primStringType
        case DataType.BytesType =>
          JsWitTypeNode.listType(buildNode(DataType.IntType))
        case DataType.Optional(of) =>
          JsWitTypeNode.optionType(buildNode(of))
        case DataType.ListType(of) =>
          JsWitTypeNode.listType(buildNode(of))
        case DataType.SetType(of) =>
          JsWitTypeNode.listType(buildNode(of))
        case DataType.MapType(valueType) =>
          val entryStruct = DataType.StructType(
            List(
              DataType.Field("key", DataType.StringType, optional = false),
              DataType.Field("value", valueType, optional = false)
            )
          )
          val entryIndex = buildNode(entryStruct)
          JsWitTypeNode.listType(entryIndex)
        case DataType.TupleType(elements) =>
          JsWitTypeNode.tupleType(js.Array(elements.map(buildNode): _*))
        case DataType.StructType(fields) =>
          val fieldEntries = js.Array[js.Tuple2[String, JsNodeIndex]]()
          fields.foreach { field =>
            val idx = buildNode(field.dataType)
            fieldEntries.push(js.Tuple2(field.name, idx))
          }
          JsWitTypeNode.recordType(fieldEntries)
        case DataType.EnumType(cases) =>
          val variantEntries = js.Array[js.Tuple2[String, js.UndefOr[JsNodeIndex]]]()
          cases.foreach { enumCase =>
            val payloadIndex = enumCase.payload.map(buildNode)
            variantEntries.push(
              js.Tuple2(enumCase.name, payloadIndex.fold[js.UndefOr[JsNodeIndex]](js.undefined)(identity))
            )
          }
          JsWitTypeNode.variantType(variantEntries)
      }

      nodes(index) = JsNamedWitTypeNode(typeVariant)
      index
    }

    private def newNode(): Int = {
      val placeholder = JsNamedWitTypeNode(JsShape.tagOnly[JsWitTypeNode]("__placeholder"))
      nodes.push(placeholder)
      nodes.length - 1
    }
  }
}
