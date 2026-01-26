package zio.blocks.schema.json

import zio.blocks.schema.SchemaError
import java.net.URI

sealed trait JsonSchema {
  def toJson: Json
}

object JsonSchema {
  def fromJson(json: Json): Either[SchemaError, JsonSchema] = ???

  final case class ObjectSchema(
    id: Option[URI] = None,
    schema: Option[URI] = None,
    anchor: Option[String] = None,
    dynamicAnchor: Option[String] = None,
    ref: Option[URI] = None,
    dynamicRef: Option[URI] = None,
    vocabulary: Option[Map[URI, Boolean]] = None,
    defs: Option[Map[String, JsonSchema]] = None,
    comment: Option[String] = None,
    allOf: Option[List[JsonSchema]] = None,
    anyOf: Option[List[JsonSchema]] = None,
    oneOf: Option[List[JsonSchema]] = None,
    not: Option[JsonSchema] = None,
    ifSchema: Option[JsonSchema] = None,
    thenSchema: Option[JsonSchema] = None,
    elseSchema: Option[JsonSchema] = None,
    properties: Option[Map[String, JsonSchema]] = None,
    patternProperties: Option[Map[String, JsonSchema]] = None,
    additionalProperties: Option[JsonSchema] = None,
    propertyNames: Option[JsonSchema] = None,
    prefixItems: Option[List[JsonSchema]] = None,
    items: Option[JsonSchema] = None,
    contains: Option[JsonSchema] = None,
    dependentSchemas: Option[Map[String, JsonSchema]] = None,
    unevaluatedItems: Option[JsonSchema] = None,
    unevaluatedProperties: Option[JsonSchema] = None,
    schemaType: Option[List[JsonType]] = None,
    const: Option[Json] = None,
    enumValues: Option[List[Json]] = None,
    multipleOf: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None,
    minimum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    maxLength: Option[Int] = None,
    minLength: Option[Int] = None,
    pattern: Option[String] = None,
    maxItems: Option[Int] = None,
    minItems: Option[Int] = None,
    uniqueItems: Option[Boolean] = None,
    maxContains: Option[Int] = None,
    minContains: Option[Int] = None,
    maxProperties: Option[Int] = None,
    minProperties: Option[Int] = None,
    required: Option[List[String]] = None,
    dependentRequired: Option[Map[String, List[String]]] = None,
    format: Option[String] = None,
    contentEncoding: Option[String] = None,
    contentMediaType: Option[String] = None,
    contentSchema: Option[JsonSchema] = None,
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[Json] = None,
    deprecated: Option[Boolean] = None,
    readOnly: Option[Boolean] = None,
    writeOnly: Option[Boolean] = None,
    examples: Option[List[Json]] = None,
    extensions: Map[String, Json] = Map.empty
  ) extends JsonSchema {
    def toJson: Json = ???
  }
}
