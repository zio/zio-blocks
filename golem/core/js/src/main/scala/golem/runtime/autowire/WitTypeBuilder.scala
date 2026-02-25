package golem.runtime.autowire

import golem.data.DataType

import scala.scalajs.js

private[autowire] object WitTypeBuilder {
  def build(dataType: DataType): js.Dynamic = {
    val builder = new Builder
    builder.buildNode(dataType)
    builder.result()
  }

  private final class Builder {
    private val nodes = js.Array[js.Dynamic]()

    def result(): js.Dynamic =
      js.Dynamic.literal("nodes" -> nodes)

    def buildNode(dataType: DataType): Int = {
      val index       = newNode()
      val typeVariant = dataType match {
        case DataType.UnitType =>
          tupleType(js.Array())
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
          listType(buildNode(DataType.IntType)) // bytes represented as list of ints (u8)
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
          tupleType(js.Array(elements.map(buildNode): _*))
        case DataType.StructType(fields) =>
          val fieldEntries = js.Array[js.Any]()
          fields.foreach { field =>
            val idx = buildNode(field.dataType)
            fieldEntries.push(js.Array[Any](field.name, idx))
          }
          recordType(fieldEntries)
        case DataType.EnumType(cases) =>
          val variantEntries = js.Array[js.Any]()
          cases.foreach { enumCase =>
            val payloadIndex      = enumCase.payload.map(buildNode)
            val payloadValue: Any = payloadIndex match {
              case Some(value) => value
              case None        => js.undefined
            }
            variantEntries.push(js.Array[Any](enumCase.name, payloadValue))
          }
          variantType(variantEntries)
      }

      // The JS representation of `wit-type` nodes is a record:
      // { name: undefined | string, owner: undefined | string, type: { tag, val? } }
      // This matches the shape used by the JS SDK and by the hand-written JS example.
      nodes(index) = js.Dynamic.literal(
        "name"  -> (js.undefined: js.UndefOr[String]),
        "owner" -> (js.undefined: js.UndefOr[String]),
        "type"  -> typeVariant
      )
      index
    }

    private def tagOnly(tag: String): js.Dynamic =
      js.Dynamic.literal("tag" -> tag)

    private def newNode(): Int = {
      val placeholder = js.Dynamic.literal()
      nodes.push(placeholder)
      nodes.length - 1
    }

    private def tupleType(values: js.Array[Int]): js.Dynamic =
      js.Dynamic.literal("tag" -> "tuple-type", "val" -> values)

    private def listType(of: Int): js.Dynamic =
      js.Dynamic.literal("tag" -> "list-type", "val" -> of)

    private def optionType(of: Int): js.Dynamic =
      js.Dynamic.literal("tag" -> "option-type", "val" -> of)

    private def recordType(fields: js.Array[js.Any]): js.Dynamic =
      js.Dynamic.literal("tag" -> "record-type", "val" -> fields)

    private def variantType(entries: js.Array[js.Any]): js.Dynamic =
      js.Dynamic.literal("tag" -> "variant-type", "val" -> entries)
  }
}
