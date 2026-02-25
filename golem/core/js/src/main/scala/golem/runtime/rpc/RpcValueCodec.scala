package golem.runtime.rpc

import golem.data.ElementSchema.{Component, UnstructuredBinary, UnstructuredText}
import golem.data.ElementValue.{
  Component => ComponentValue,
  UnstructuredBinary => UnstructuredBinaryValueElt,
  UnstructuredText => UnstructuredTextValueElt
}
import golem.data.StructuredSchema.{Multimodal, Tuple}
import golem.data._
import golem.runtime.autowire.{WitValueBuilder, WitValueCodec}

import scala.scalajs.js

private[rpc] object RpcValueCodec {
  private val languageType: DataType.StructType =
    DataType.StructType(
      List(DataType.Field("language-code", DataType.StringType, optional = false))
    )

  private val textSourceType: DataType.StructType =
    DataType.StructType(
      List(
        DataType.Field("data", DataType.StringType, optional = false),
        DataType.Field("text-type", DataType.Optional(languageType), optional = false)
      )
    )

  private val textReferenceType: DataType.EnumType =
    DataType.EnumType(
      List(
        DataType.EnumCase("url", Some(DataType.StringType)),
        DataType.EnumCase("inline", Some(textSourceType))
      )
    )

  private val binaryTypeDescriptor: DataType.StructType =
    DataType.StructType(
      List(DataType.Field("mime-type", DataType.StringType, optional = false))
    )

  private val binarySourceType: DataType.StructType =
    DataType.StructType(
      List(
        DataType.Field("data", DataType.BytesType, optional = false),
        DataType.Field("binary-type", binaryTypeDescriptor, optional = false)
      )
    )

  private val binaryReferenceType: DataType.EnumType =
    DataType.EnumType(
      List(
        DataType.EnumCase("url", Some(DataType.StringType)),
        DataType.EnumCase("inline", Some(binarySourceType))
      )
    )

  def encodeArgs[A](value: A)(implicit codec: GolemSchema[A]): Either[String, js.Array[js.Dynamic]] =
    codec.encode(value).flatMap(structuredToParams(codec.schema, _))

  def encodeValue[A](value: A)(implicit codec: GolemSchema[A]): Either[String, js.Dynamic] =
    codec.encode(value).flatMap(structuredToWit(codec.schema, _))

  def decodeValue[A](witValue: js.Dynamic)(implicit codec: GolemSchema[A]): Either[String, A] =
    structuredFromWit(codec.schema, witValue).flatMap(codec.decode)

  private def structuredToParams(
    schema: StructuredSchema,
    value: StructuredValue
  ): Either[String, js.Array[js.Dynamic]] =
    schema match {
      case Tuple(elements) =>
        value match {
          case StructuredValue.Tuple(values) =>
            encodeTupleParams(elements, values)
          case other =>
            Left(s"Structured value mismatch. Expected tuple payload, found: $other")
        }
      case Multimodal(elements) =>
        value match {
          case StructuredValue.Multimodal(entries) =>
            encodeMultimodal(elements, entries).map { witValue =>
              val arr = new js.Array[js.Dynamic]()
              arr.push(witValue)
              arr
            }
          case other =>
            Left(s"Structured value mismatch. Expected multimodal payload, found: $other")
        }
    }

  private def structuredToWit(schema: StructuredSchema, value: StructuredValue): Either[String, js.Dynamic] =
    schema match {
      case Tuple(elements) =>
        value match {
          case StructuredValue.Tuple(values) =>
            encodeTupleAggregate(elements, values)
          case other =>
            Left(s"Structured value mismatch. Expected tuple payload, found: $other")
        }
      case Multimodal(elements) =>
        value match {
          case StructuredValue.Multimodal(entries) =>
            encodeMultimodal(elements, entries)
          case other =>
            Left(s"Structured value mismatch. Expected multimodal payload, found: $other")
        }
    }

  private def structuredFromWit(schema: StructuredSchema, witValue: js.Dynamic): Either[String, StructuredValue] =
    schema match {
      case Tuple(elements) =>
        decodeTuple(elements, witValue).map(StructuredValue.Tuple.apply)
      case Multimodal(elements) =>
        decodeMultimodal(elements, witValue).map(StructuredValue.Multimodal.apply)
    }

  private def encodeTupleParams(
    schemaElements: List[NamedElementSchema],
    values: List[NamedElementValue]
  ): Either[String, js.Array[js.Dynamic]] = {
    val valueMap = values.map(elem => elem.name -> elem.value).toMap
    val array    = new js.Array[js.Dynamic]()
    schemaElements
      .foldLeft[Either[String, Unit]](Right(())) { case (acc, element) =>
        acc.flatMap { _ =>
          valueMap
            .get(element.name)
            .toRight(s"Missing value for element '${element.name}'")
            .flatMap { elementValue =>
              encodeElement(element.schema, elementValue).map(array.push(_))
            }
        }
      }
      .map(_ => array)
  }

  private def encodeTupleAggregate(
    schemaElements: List[NamedElementSchema],
    values: List[NamedElementValue]
  ): Either[String, js.Dynamic] = {
    val valueMap         = values.map(elem => elem.name -> elem.value).toMap
    val dataTypes        = schemaElements.map(elem => elementDataType(elem.schema))
    val dataValuesEither = schemaElements.foldLeft[Either[String, List[DataValue]]](Right(Nil)) { case (acc, element) =>
      acc.flatMap { list =>
        valueMap
          .get(element.name)
          .toRight(s"Missing value for element '${element.name}'")
          .flatMap(elementValueToDataValue(element.schema, _))
          .map(value => list :+ value)
      }
    }
    dataValuesEither.flatMap { dataValues =>
      WitValueBuilder.build(DataType.TupleType(dataTypes), DataValue.TupleValue(dataValues))
    }
  }

  private def encodeMultimodal(
    schemaElements: List[NamedElementSchema],
    values: List[NamedElementValue]
  ): Either[String, js.Dynamic] = {
    val schemaByName  = schemaElements.map(elem => elem.name -> elem.schema).toMap
    val variantType   = multimodalVariantType(schemaElements)
    val entriesEither = values.foldLeft[Either[String, List[DataValue]]](Right(Nil)) { case (acc, namedValue) =>
      acc.flatMap { list =>
        schemaByName
          .get(namedValue.name)
          .toRight(s"Unknown modality '${namedValue.name}'")
          .flatMap(elementValueToDataValue(_, namedValue.value))
          .map(value => list :+ DataValue.EnumValue(namedValue.name, Some(value)))
      }
    }
    entriesEither.flatMap { entries =>
      WitValueBuilder.build(DataType.ListType(variantType), DataValue.ListValue(entries))
    }
  }

  private def decodeTuple(
    schemaElements: List[NamedElementSchema],
    witValue: js.Dynamic
  ): Either[String, List[NamedElementValue]] = {
    val dataType = DataType.TupleType(schemaElements.map(elem => elementDataType(elem.schema)))

    def decodeAsTuple: Either[String, List[NamedElementValue]] =
      WitValueCodec.decode(dataType, witValue).flatMap {
        case DataValue.TupleValue(values) =>
          if (values.length != schemaElements.length)
            Left(s"Tuple arity mismatch. Expected ${schemaElements.length}, found ${values.length}")
          else {
            schemaElements.zip(values).foldLeft[Either[String, List[NamedElementValue]]](Right(Nil)) {
              case (acc, (element, dataValue)) =>
                acc.flatMap { list =>
                  dataValueToElementValue(element.schema, dataValue).map { elementValue =>
                    list :+ NamedElementValue(element.name, elementValue)
                  }
                }
            }
          }
        case other =>
          Left(s"Expected tuple value, found $other")
      }

    def decodeSingleElement(elem: NamedElementSchema): Either[String, List[NamedElementValue]] =
      WitValueCodec
        .decode(elementDataType(elem.schema), witValue)
        .flatMap(dataValueToElementValue(elem.schema, _))
        .map(value => List(NamedElementValue(elem.name, value)))

    schemaElements match {
      case single :: Nil =>
        decodeAsTuple.orElse(decodeSingleElement(single))
      case _ =>
        decodeAsTuple
    }
  }

  private def decodeMultimodal(
    schemaElements: List[NamedElementSchema],
    witValue: js.Dynamic
  ): Either[String, List[NamedElementValue]] = {
    val variantType  = multimodalVariantType(schemaElements)
    val schemaByName = schemaElements.map(elem => elem.name -> elem.schema).toMap
    WitValueCodec.decode(DataType.ListType(variantType), witValue).flatMap {
      case DataValue.ListValue(values) =>
        values.foldLeft[Either[String, List[NamedElementValue]]](Right(Nil)) {
          case (acc, DataValue.EnumValue(name, payload)) =>
            acc.flatMap { list =>
              val converted = for {
                schemaElem   <- schemaByName.get(name).toRight(s"Unknown modality '$name'")
                dataValue    <- payload.toRight(s"Missing payload for modality '$name'")
                elementValue <- dataValueToElementValue(schemaElem, dataValue)
              } yield NamedElementValue(name, elementValue)
              converted.map(elem => list :+ elem)
            }
          case (_, other) =>
            Left(s"Invalid multimodal entry payload: $other")
        }
      case other =>
        Left(s"Expected list payload for multimodal value, found $other")
    }
  }

  private def encodeElement(schema: ElementSchema, value: ElementValue): Either[String, js.Dynamic] =
    elementValueToDataValue(schema, value).flatMap { dataValue =>
      WitValueBuilder.build(elementDataType(schema), dataValue)
    }

  private def elementValueToDataValue(schema: ElementSchema, value: ElementValue): Either[String, DataValue] =
    (schema, value) match {
      case (Component(_), ComponentValue(dataValue)) =>
        Right(dataValue)
      case (UnstructuredText(_), UnstructuredTextValueElt(textValue)) =>
        Right(textValueToDataValue(textValue))
      case (UnstructuredBinary(_), UnstructuredBinaryValueElt(binaryValue)) =>
        Right(binaryValueToDataValue(binaryValue))
      case (expected, found) =>
        Left(s"Element schema/value mismatch. Expected $expected, found $found")
    }

  private def dataValueToElementValue(schema: ElementSchema, dataValue: DataValue): Either[String, ElementValue] =
    schema match {
      case Component(_) =>
        Right(ComponentValue(dataValue))
      case UnstructuredText(_) =>
        dataValueToTextValue(dataValue).map(UnstructuredTextValueElt.apply)
      case UnstructuredBinary(_) =>
        dataValueToBinaryValue(dataValue).map(UnstructuredBinaryValueElt.apply)
    }

  private def elementDataType(schema: ElementSchema): DataType =
    schema match {
      case Component(dataType)   => dataType
      case UnstructuredText(_)   => textReferenceType
      case UnstructuredBinary(_) => binaryReferenceType
    }

  private def multimodalVariantType(elements: List[NamedElementSchema]): DataType.EnumType =
    DataType.EnumType(elements.map(elem => DataType.EnumCase(elem.name, Some(elementDataType(elem.schema)))))

  private def textValueToDataValue(value: UnstructuredTextValue): DataValue =
    value match {
      case UnstructuredTextValue.Inline(data, language) =>
        val languageValue =
          language.map(code => DataValue.StructValue(Map("language-code" -> DataValue.StringValue(code))))
        val sourceValue = DataValue.StructValue(
          Map(
            "data"      -> DataValue.StringValue(data),
            "text-type" -> DataValue.OptionalValue(languageValue)
          )
        )
        DataValue.EnumValue("inline", Some(sourceValue))
      case UnstructuredTextValue.Url(url) =>
        DataValue.EnumValue("url", Some(DataValue.StringValue(url)))
    }

  private def dataValueToTextValue(value: DataValue): Either[String, UnstructuredTextValue] =
    value match {
      case DataValue.EnumValue("url", Some(DataValue.StringValue(url))) =>
        Right(UnstructuredTextValue.Url(url))
      case DataValue.EnumValue("inline", Some(DataValue.StructValue(fields))) =>
        val dataField                         = fields.get("data").collect { case DataValue.StringValue(text) => text }
        val langField: Option[Option[String]] = fields.get("text-type") match {
          case Some(DataValue.OptionalValue(Some(DataValue.StructValue(langFields)))) =>
            Some(langFields.get("language-code").collect { case DataValue.StringValue(code) => code })
          case Some(DataValue.OptionalValue(None)) =>
            Some(None)
          case None =>
            Some(None)
          case _ =>
            None
        }

        (dataField, langField) match {
          case (Some(text), Some(languageOpt)) =>
            Right(UnstructuredTextValue.Inline(text, languageOpt))
          case _ =>
            Left(s"Invalid inline text payload: $fields")
        }
      case other =>
        Left(s"Invalid text reference payload: $other")
    }

  private def binaryValueToDataValue(value: UnstructuredBinaryValue): DataValue =
    value match {
      case UnstructuredBinaryValue.Inline(data, mimeType) =>
        val descriptor  = DataValue.StructValue(Map("mime-type" -> DataValue.StringValue(mimeType)))
        val sourceValue = DataValue.StructValue(
          Map(
            "data"        -> DataValue.BytesValue(data),
            "binary-type" -> descriptor
          )
        )
        DataValue.EnumValue("inline", Some(sourceValue))
      case UnstructuredBinaryValue.Url(url) =>
        DataValue.EnumValue("url", Some(DataValue.StringValue(url)))
    }

  private def dataValueToBinaryValue(value: DataValue): Either[String, UnstructuredBinaryValue] =
    value match {
      case DataValue.EnumValue("url", Some(DataValue.StringValue(url))) =>
        Right(UnstructuredBinaryValue.Url(url))
      case DataValue.EnumValue("inline", Some(DataValue.StructValue(fields))) =>
        val dataField = fields.get("data").collect { case DataValue.BytesValue(bytes) => bytes }
        val mimeField =
          fields
            .get("binary-type")
            .collect { case DataValue.StructValue(desc) => desc }
            .flatMap(_.get("mime-type"))
            .collect { case DataValue.StringValue(mt) => mt }

        (dataField, mimeField) match {
          case (Some(bytes), Some(mt)) =>
            Right(UnstructuredBinaryValue.Inline(bytes, mt))
          case _ =>
            Left(s"Invalid inline binary payload: $fields")
        }
      case other =>
        Left(s"Invalid binary reference payload: $other")
    }
}
