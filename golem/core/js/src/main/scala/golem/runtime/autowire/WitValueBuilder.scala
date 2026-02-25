package golem.runtime.autowire

import golem.data.DataValue._
import golem.data.{DataType, DataValue}

import scala.scalajs.js

private[golem] object WitValueBuilder {
  def build(dataType: DataType, value: DataValue): Either[String, js.Dynamic] = {
    val builder = new Builder
    builder.build(dataType, value).map(_ => builder.result())
  }

  private final class Builder {
    private val nodes = js.Array[js.Dynamic]()

    def result(): js.Dynamic =
      js.Dynamic.literal("nodes" -> nodes)

    def build(dataType: DataType, value: DataValue): Either[String, Int] = {
      val index      = newNode()
      val nodeEither = (dataType, value) match {
        case (DataType.UnitType, NullValue) =>
          Right(js.Dynamic.literal("tag" -> "tuple-value", "val" -> js.Array()))
        case (DataType.StringType, StringValue(v)) =>
          Right(js.Dynamic.literal("tag" -> "prim-string", "val" -> v))
        case (DataType.BoolType, BoolValue(v)) =>
          Right(js.Dynamic.literal("tag" -> "prim-bool", "val" -> v))
        case (DataType.IntType, IntValue(v)) =>
          Right(js.Dynamic.literal("tag" -> "prim-s32", "val" -> v))
        case (DataType.LongType, LongValue(v)) =>
          Right(js.Dynamic.literal("tag" -> "prim-s64", "val" -> v.toDouble))
        case (DataType.DoubleType, DoubleValue(v)) =>
          Right(js.Dynamic.literal("tag" -> "prim-float64", "val" -> v))
        case (DataType.BigDecimalType, BigDecimalValue(v)) =>
          Right(js.Dynamic.literal("tag" -> "prim-string", "val" -> v.toString))
        case (DataType.UUIDType, UUIDValue(v)) =>
          Right(js.Dynamic.literal("tag" -> "prim-string", "val" -> v.toString))
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
                js.Dynamic.literal("tag" -> "option-value", "val" -> child)
              }
            case None =>
              Right(js.Dynamic.literal("tag" -> "option-value", "val" -> js.undefined))
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
                  js.Dynamic.literal("tag" -> "variant-value", "val" -> js.Array(index, child))
                }
              case None =>
                Right(js.Dynamic.literal("tag" -> "variant-value", "val" -> js.Array(index, js.undefined)))
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
    ): Either[String, js.Dynamic] = {
      val indicesEither = values.foldLeft[Either[String, List[Int]]](Right(Nil)) { case (acc, value) =>
        acc.flatMap { collected =>
          build(elementType, value).map(idx => collected :+ idx)
        }
      }

      indicesEither.map { indices =>
        js.Dynamic.literal("tag" -> tag, "val" -> js.Array(indices: _*))
      }
    }

    private def encodeIndexed(pairs: List[(DataValue, DataType)], tag: String): Either[String, js.Dynamic] = {
      val indicesEither = pairs.foldLeft[Either[String, List[Int]]](Right(Nil)) { case (acc, (value, dtype)) =>
        acc.flatMap { collected =>
          build(dtype, value).map(idx => collected :+ idx)
        }
      }

      indicesEither.map { indices =>
        js.Dynamic.literal("tag" -> tag, "val" -> js.Array(indices: _*))
      }
    }

    private def newNode(): Int = {
      val placeholder = js.Dynamic.literal()
      nodes.push(placeholder)
      nodes.length - 1
    }
  }
}
