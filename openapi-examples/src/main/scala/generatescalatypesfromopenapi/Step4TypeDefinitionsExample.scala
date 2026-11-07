//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file BookstoreSpec.scala
// Run from this folder:  scala-cli run Step4TypeDefinitionsExample.scala BookstoreSpec.scala
// Or from openapi-examples:  sbt "runMain generatescalatypesfromopenapi.Step4TypeDefinitionsExample"

package generatescalatypesfromopenapi

import zio.blocks.openapi._
import zio.blocks.schema.json._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

import BookstoreSpec.bookstoreJson

/**
 * Generate Scala Types from an OpenAPI Spec — Turn Objects, Enums and oneOf
 * into Scala Types
 *
 * Turns `Book` into a case class, `BookFormat` into an enum and `Payment` into
 * a sealed trait with discriminator annotations, and prints each one.
 */
object Step4TypeDefinitionsExample {
  // Setup from the previous sections: the parsed spec, its components and the type mapping.
  def parseSpec(text: String): OpenAPI =
    Json.parse(text).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
      case Right(api)  => api
      case Left(error) => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
    }

  val api: OpenAPI = parseSpec(bookstoreJson)

  final case class Component(name: String, schema: JsonSchema.Object, discriminator: Option[Discriminator])

  def componentsOf(api: OpenAPI): List[Component] =
    api.components.toList.flatMap(_.schemas.toList).map {
      case (name, ReferenceOr.Value(schemaObject)) =>
        schemaObject.toJsonSchema match {
          case Right(schema: JsonSchema.Object) => Component(name, schema, schemaObject.discriminator)
          case _                                => throw new IllegalArgumentException(s"$name: unsupported schema")
        }
      case (name, ReferenceOr.Ref(_)) =>
        throw new IllegalArgumentException(s"$name: a component that is only a $$ref is not supported")
    }

  val components: List[Component] = componentsOf(api)

  val refPrefix = "#/components/schemas/"

  def refName(schema: JsonSchema): Option[String] = schema match {
    case obj: JsonSchema.Object => obj.$ref.map(_.value.stripPrefix(refPrefix))
    case _                      => None
  }

  def scalarType(jsonType: JsonSchemaType, schema: JsonSchema.Object): TypeRef = jsonType match {
    case JsonSchemaType.String =>
      schema.format match {
        case Some("date")      => TypeRef("java.time.LocalDate")
        case Some("date-time") => TypeRef("java.time.Instant")
        case Some("uuid")      => TypeRef("java.util.UUID")
        case _                 => TypeRef.String
      }
    case JsonSchemaType.Integer =>
      if (schema.format.contains("int32")) TypeRef.Int else TypeRef.Long
    case JsonSchemaType.Number =>
      schema.format match {
        case Some("float")  => TypeRef.Float
        case Some("double") => TypeRef.Double
        case _              => TypeRef.BigDecimal
      }
    case JsonSchemaType.Boolean =>
      TypeRef.Boolean
    case JsonSchemaType.Array if schema.items.isDefined =>
      TypeRef.list(typeRefFor(schema.items.get))
    case JsonSchemaType.Object if schema.properties.isEmpty && schema.additionalProperties.isDefined =>
      TypeRef.map(TypeRef.String, typeRefFor(schema.additionalProperties.get))
    case other =>
      throw new IllegalArgumentException(s"Unsupported inline schema of type ${other.toJsonString}: ${schema.toJson}")
  }

  def typeRefFor(schema: JsonSchema): TypeRef = schema match {
    case obj: JsonSchema.Object if obj.$ref.isDefined =>
      TypeRef(refName(obj).get)
    case obj: JsonSchema.Object =>
      val nullable30 = obj.extensions.get("nullable").contains(Json.True)
      obj.`type` match {
        case Some(SchemaType.Single(jsonType)) if nullable30 =>
          TypeRef.optional(scalarType(jsonType, obj))
        case Some(SchemaType.Single(jsonType)) =>
          scalarType(jsonType, obj)
        case Some(SchemaType.Union(types)) if types.length == 2 && types.contains(JsonSchemaType.Null) =>
          TypeRef.optional(scalarType(types.find(_ != JsonSchemaType.Null).get, obj))
        case _ =>
          throw new IllegalArgumentException(s"Unsupported inline schema: ${obj.toJson}")
      }
    case other =>
      throw new IllegalArgumentException(s"Unsupported inline schema: ${other.toJson}")
  }

  // Turn Objects, Enums and oneOf into Scala Types
  def fieldFor(name: String, schema: JsonSchema, required: Boolean): Field = {
    val fieldType = typeRefFor(schema)
    if (required) Field(name, fieldType)
    else if (fieldType.name == "Option") Field(name, fieldType, defaultValue = Some("None"))
    else Field(name, TypeRef.optional(fieldType), defaultValue = Some("None"))
  }

  def caseClassFor(name: String, schema: JsonSchema.Object, skip: Set[String] = Set.empty): CaseClass = {
    val required   = schema.required.getOrElse(Set.empty[String])
    val properties = schema.properties.map(_.toList).getOrElse(Nil)
    val fields     = properties.collect {
      case (property, propertySchema) if !skip.contains(property) =>
        fieldFor(property, propertySchema, required.contains(property))
    }
    CaseClass(name, fields, doc = schema.description)
  }

  def enumFor(name: String, schema: JsonSchema.Object): Enum = {
    val values = schema.`enum`.toList.flatMap(_.toList).map {
      case Json.String(value) => EnumCase.SimpleCase(value)
      case other              => throw new IllegalArgumentException(s"$name: non-string enum value $other")
    }
    Enum(name, values, doc = schema.description)
  }

  def stringLiteral(value: String): String = "\"" + value + "\""

  def sealedTraitFor(component: Component, byName: Map[String, Component]): SealedTrait = {
    val discriminator = component.discriminator.get
    val wireNames     = discriminator.mapping.toList.map { case (value, ref) => ref.stripPrefix(refPrefix) -> value }.toMap
    val cases         = component.schema.oneOf.get.toList.flatMap(refName).map { caseName =>
      val caseClass = caseClassFor(caseName, byName(caseName).schema, skip = Set(discriminator.propertyName))
      val renamed   = wireNames.get(caseName) match {
        case Some(wireName) =>
          caseClass.copy(annotations = List(Annotation("Modifier.rename", List("name" -> stringLiteral(wireName)))))
        case None => caseClass
      }
      SealedTraitCase.CaseClassCase(renamed)
    }
    SealedTrait(
      component.name,
      cases = cases,
      annotations =
        List(Annotation("Modifier.discriminator", List("name" -> stringLiteral(discriminator.propertyName)))),
      doc = component.schema.description
    )
  }

  val byName: Map[String, Component] = components.map(component => component.name -> component).toMap

  def main(args: Array[String]): Unit = {
    println(ScalaEmitter.emitTypeDefinition(caseClassFor("Book", byName("Book").schema), EmitterConfig.default))
    println(ScalaEmitter.emitTypeDefinition(enumFor("BookFormat", byName("BookFormat").schema), EmitterConfig.default))
    println(ScalaEmitter.emitTypeDefinition(sealedTraitFor(byName("Payment"), byName), EmitterConfig.default))
  }
}
