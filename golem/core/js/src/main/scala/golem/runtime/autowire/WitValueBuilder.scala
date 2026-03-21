package golem.runtime.autowire

import golem.data.DataValue._
import golem.data.{DataType, DataValue}
import golem.host.js._

import scala.scalajs.js

private[golem] object WitValueBuilder {
  def build(dataType: DataType, value: DataValue): Either[String, JsWitValue] = {
    val builder = new Builder
    builder.build(dataType, value).map(_ => builder.result())
  }

  private final class Builder {
    private val nodes = js.Array[JsWitNode]()

    def result(): JsWitValue =
      JsWitValue(nodes)

    def build(dataType: DataType, value: DataValue): Either[String, Int] = {
      val index      = newNode()
      val nodeEither = (dataType, value) match {
        case (DataType.UnitType, NullValue) =>
          Right(JsWitNode.tupleValue(js.Array[JsNodeIndex]()))
        case (DataType.StringType, StringValue(v)) =>
          Right(JsWitNode.primString(v))
        case (DataType.BoolType, BoolValue(v)) =>
          Right(JsWitNode.primBool(v))
        case (DataType.IntType, IntValue(v)) =>
          Right(JsWitNode.primS32(v))
        case (DataType.LongType, LongValue(v)) =>
          Right(JsWitNode.primS64(js.BigInt(v.toString)))
        case (DataType.DoubleType, DoubleValue(v)) =>
          Right(JsWitNode.primFloat64(v))
        case (DataType.BigDecimalType, BigDecimalValue(v)) =>
          Right(JsWitNode.primString(v.toString))
        case (DataType.UUIDType, UUIDValue(v)) =>
          Right(JsWitNode.primString(v.toString))
        case (DataType.BytesType, BytesValue(bytes)) =>
          encodeSequence(
            bytes.toList.map(b => IntValue(b & 0xff)),
            DataType.IntType,
            "list-value"
          )
        case (DataType.Optional(of), OptionalValue(maybeValue)) =>
          maybeValue match {
            case Some(inner) =>
              build(of, inner).map { child =>
                JsWitNode.optionValue(child: js.UndefOr[JsNodeIndex])
              }
            case None =>
              Right(JsWitNode.optionValue(js.undefined))
          }
        case (DataType.ListType(of), ListValue(values)) =>
          encodeSequence(values, of, "list-value")
        case (DataType.SetType(of), SetValue(values)) =>
          encodeSequence(values.toList, of, "list-value")
        case (DataType.MapType(valueType), MapValue(entries)) =>
          val entryType = DataType.StructType(
            List(
              DataType.Field("key", DataType.StringType, optional = false),
              DataType.Field("value", valueType, optional = false)
            )
          )
          val entryValues = entries.toList.map { case (k, v) =>
            StructValue(Map("key" -> StringValue(k), "value" -> v))
          }
          encodeSequence(entryValues, entryType, "list-value")
        case (DataType.TupleType(elements), TupleValue(values)) =>
          if (elements.length != values.length)
            Left(s"Tuple arity mismatch. Expected ${elements.length} values.")
          else
            encodeIndexed(values.zip(elements), "tuple-value")
        case (struct: DataType.StructType, StructValue(fields)) =>
          val orderedEither = struct.fields.foldLeft[Either[String, List[(DataValue, DataType)]]](Right(Nil)) {
            case (acc, field) =>
              acc.flatMap { collected =>
                fields.get(field.name) match {
                  case Some(value) =>
                    Right(collected :+ (value -> field.dataType))
                  case None if field.optional =>
                    Right(collected :+ (NullValue -> field.dataType))
                  case None =>
                    Left(s"Missing required field ${field.name}")
                }
              }
          }
          orderedEither.flatMap(values => encodeIndexed(values, "record-value"))
        case (enumType: DataType.EnumType, EnumValue(caseName, payload)) =>
          val index = enumType.cases.indexWhere(_.name == caseName)
          if (index < 0) Left(s"Unknown enum case $caseName")
          else
            payload match {
              case Some(valuePayload) =>
                build(enumType.cases(index).payload.get, valuePayload).map { child =>
                  JsWitNode.variantValue(index, child: js.UndefOr[JsNodeIndex])
                }
              case None =>
                Right(JsWitNode.variantValue(index, js.undefined))
            }
        case other =>
          Left(s"Unsupported value encoding for $other")
      }

      nodeEither.map { node =>
        nodes(index) = node
        index
      }
    }

    private def encodeSequence(
      values: List[DataValue],
      elementType: DataType,
      tag: String
    ): Either[String, JsWitNode] = {
      val indicesEither = values.foldLeft[Either[String, List[Int]]](Right(Nil)) { case (acc, value) =>
        acc.flatMap { collected =>
          build(elementType, value).map(idx => collected :+ idx)
        }
      }

      indicesEither.map { indices =>
        tag match {
          case "list-value"  => JsWitNode.listValue(js.Array(indices: _*))
          case "tuple-value" => JsWitNode.tupleValue(js.Array(indices: _*))
          case _             => JsWitNode.listValue(js.Array(indices: _*))
        }
      }
    }

    private def encodeIndexed(pairs: List[(DataValue, DataType)], tag: String): Either[String, JsWitNode] = {
      val indicesEither = pairs.foldLeft[Either[String, List[Int]]](Right(Nil)) { case (acc, (value, dtype)) =>
        acc.flatMap { collected =>
          build(dtype, value).map(idx => collected :+ idx)
        }
      }

      indicesEither.map { indices =>
        tag match {
          case "record-value" => JsWitNode.recordValue(js.Array(indices: _*))
          case "tuple-value"  => JsWitNode.tupleValue(js.Array(indices: _*))
          case _              => JsWitNode.tupleValue(js.Array(indices: _*))
        }
      }
    }

    private def newNode(): Int = {
      val placeholder = JsShape.tagOnly[JsWitNode]("__placeholder")
      nodes.push(placeholder)
      nodes.length - 1
    }
  }
}
