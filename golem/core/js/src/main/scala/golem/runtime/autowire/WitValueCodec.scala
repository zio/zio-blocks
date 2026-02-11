package golem.runtime.autowire

import golem.data.DataType._
import golem.data.DataValue._
import golem.data.{DataType, DataValue}

import scala.scalajs.js

private[golem] object WitValueCodec {
  def decode(dataType: DataType, witValue: js.Dynamic): Either[String, DataValue] = {
    val nodes = witValue.selectDynamic("nodes").asInstanceOf[js.Array[js.Dynamic]]
    decodeNode(dataType, nodes, 0)
  }

  private def decodeNode(dataType: DataType, nodes: js.Array[js.Dynamic], index: Int): Either[String, DataValue] = {
    if (index < 0 || index >= nodes.length)
      Left(s"Wit node index $index out of bounds")
    else {
      val node = nodes(index)
      val tag  = node.selectDynamic("tag").asInstanceOf[String]

      (dataType, tag) match {
        case (UnitType, "tuple-value") =>
          Right(NullValue)
        case (StringType, "prim-string") =>
          Right(StringValue(node.selectDynamic("val").asInstanceOf[String]))
        case (BoolType, "prim-bool") =>
          Right(BoolValue(node.selectDynamic("val").asInstanceOf[Boolean]))
        case (IntType, "prim-s32") =>
          Right(IntValue(node.selectDynamic("val").asInstanceOf[Int]))
        case (IntType, "prim-float64") =>
          val raw = node.selectDynamic("val").asInstanceOf[Double]
          if (!isWholeNumber(raw))
            Left(s"Non-integral numeric value $raw cannot be decoded as IntType")
          else if (!inIntRange(raw))
            Left(s"Value $raw is out of Int range for IntType")
          else
            Right(IntValue(raw.toInt))
        case (LongType, "prim-s64") =>
          val raw = node.selectDynamic("val").asInstanceOf[Double]
          if (!isWholeNumber(raw))
            Left(s"Non-integral numeric value $raw cannot be decoded as LongType (prim-s64)")
          else if (!inLongRange(raw))
            Left(s"Value $raw is out of Long range for LongType (prim-s64)")
          else
            Right(LongValue(raw.toLong))
        case (LongType, "prim-float64") =>
          val raw = node.selectDynamic("val").asInstanceOf[Double]
          if (!isWholeNumber(raw))
            Left(s"Non-integral numeric value $raw cannot be decoded as LongType (prim-float64)")
          else if (!inLongRange(raw))
            Left(s"Value $raw is out of Long range for LongType (prim-float64)")
          else
            Right(LongValue(raw.toLong))
        case (DoubleType, "prim-float64") =>
          Right(DoubleValue(node.selectDynamic("val").asInstanceOf[Double]))
        case (BigDecimalType, "prim-string") =>
          Right(BigDecimalValue(BigDecimal(node.selectDynamic("val").asInstanceOf[String])))
        case (UUIDType, "prim-string") =>
          Right(UUIDValue(java.util.UUID.fromString(node.selectDynamic("val").asInstanceOf[String])))
        case (BytesType, "list-value") =>
          val refs        = node.selectDynamic("val").asInstanceOf[js.Array[Int]]
          val bytesEither = refs.foldLeft[Either[String, Vector[Byte]]](Right(Vector.empty)) { case (acc, childIdx) =>
            for {
              vec        <- acc
              childValue <- decodeNode(IntType, nodes, childIdx)
              byte       <- childValue match {
                        case IntValue(v) => Right(v.toByte)
                        case other       => Left(s"Expected byte value, found $other")
                      }
            } yield vec :+ byte
          }
          bytesEither.map(vector => BytesValue(vector.toArray))
        case (Optional(of), "option-value") =>
          val ref = node.selectDynamic("val")
          if (js.isUndefined(ref)) Right(OptionalValue(None))
          else
            decodeNode(of, nodes, ref.asInstanceOf[Int]).map(value => OptionalValue(Some(value)))
        case (ListType(of), "list-value") =>
          decodeIndexed(of, nodes, node).map(values => ListValue(values))
        case (SetType(of), "list-value") =>
          decodeIndexed(of, nodes, node).map(values => SetValue(values.toSet))
        case (MapType(valueType), "list-value") =>
          val entryType = StructType(
            List(
              Field("key", StringType, optional = false),
              Field("value", valueType, optional = false)
            )
          )
          decodeIndexed(entryType, nodes, node).flatMap { entries =>
            entries.foldLeft[Either[String, Map[String, DataValue]]](Right(Map.empty)) {
              case (acc, StructValue(fields)) =>
                for {
                  map      <- acc
                  keyValue <- fields.get("key") match {
                                case Some(StringValue(key)) => Right(key)
                                case other                  => Left(s"Expected string map key, found $other")
                              }
                  value <- fields.get("value").toRight("Missing map value field")
                } yield map.updated(keyValue, value)
              case (_, other) =>
                Left(s"Invalid map entry payload: $other")
            }
          }.map(MapValue(_))
        case (TupleType(elements), "tuple-value") =>
          decodeTuple(elements, nodes, node).map(TupleValue(_))
        case (struct: StructType, "record-value") =>
          val refs = node.selectDynamic("val").asInstanceOf[js.Array[Int]]
          if (refs.length != struct.fields.length)
            Left(s"Struct field count mismatch. Expected ${struct.fields.length}, found ${refs.length}")
          else {
            val decoded =
              struct.fields.zipWithIndex.foldLeft[Either[String, Map[String, DataValue]]](Right(Map.empty)) {
                case (acc, (field, idx)) =>
                  acc.flatMap { map =>
                    decodeNode(field.dataType, nodes, refs(idx)).map(value => map.updated(field.name, value))
                  }
              }
            decoded.map(StructValue(_))
          }
        case (enumType: EnumType, "variant-value") =>
          val arr        = node.selectDynamic("val").asInstanceOf[js.Array[Any]]
          val caseIndex  = arr(0).asInstanceOf[Int]
          val maybeValue = arr(1)
          if (caseIndex < 0 || caseIndex >= enumType.cases.length)
            Left(s"Variant index $caseIndex out of range")
          else {
            val selected = enumType.cases(caseIndex)
            if (js.isUndefined(maybeValue))
              Right(EnumValue(selected.name, None))
            else
              selected.payload match {
                case Some(payloadType) =>
                  decodeNode(payloadType, nodes, maybeValue.asInstanceOf[Int])
                    .map(value => EnumValue(selected.name, Some(value)))
                case None =>
                  Left(s"Variant ${selected.name} does not expect payload")
              }
          }
        case other =>
          Left(s"Unsupported decoding for $other with node tag $tag")
      }
    }
  }

  private def decodeIndexed(
    dataType: DataType,
    nodes: js.Array[js.Dynamic],
    node: js.Dynamic
  ): Either[String, List[DataValue]] = {
    val refs = node.selectDynamic("val").asInstanceOf[js.Array[Int]]
    refs.foldLeft[Either[String, List[DataValue]]](Right(Nil)) { case (acc, childIdx) =>
      acc.flatMap { values =>
        decodeNode(dataType, nodes, childIdx).map(value => values :+ value)
      }
    }
  }

  private def isWholeNumber(raw: Double): Boolean =
    !raw.isNaN && !raw.isInfinity && raw.isWhole

  private def inIntRange(raw: Double): Boolean =
    raw >= Int.MinValue.toDouble && raw <= Int.MaxValue.toDouble

  private def inLongRange(raw: Double): Boolean =
    raw >= Long.MinValue.toDouble && raw <= Long.MaxValue.toDouble

  private def decodeTuple(
    elements: List[DataType],
    nodes: js.Array[js.Dynamic],
    node: js.Dynamic
  ): Either[String, List[DataValue]] = {
    val refs = node.selectDynamic("val").asInstanceOf[js.Array[Int]]
    if (refs.length != elements.length)
      Left(s"Tuple size mismatch. Expected ${elements.length}, found ${refs.length}")
    else {
      val initial: Either[String, List[DataValue]] = Right(Nil)
      refs.zip(elements).foldLeft(initial) { case (acc, (ref, dtype)) =>
        acc.flatMap { values =>
          decodeNode(dtype, nodes, ref).map(value => values :+ value)
        }
      }
    }
  }
}
