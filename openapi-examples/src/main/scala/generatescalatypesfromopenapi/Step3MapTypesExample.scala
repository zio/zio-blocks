//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file BookstoreSpec.scala
// Run from this folder:  scala-cli run Step3MapTypesExample.scala BookstoreSpec.scala
// Or from openapi-examples:  sbt "runMain generatescalatypesfromopenapi.Step3MapTypesExample"

package generatescalatypesfromopenapi

import zio.blocks.openapi._
import zio.blocks.schema.json._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

import BookstoreSpec.bookstoreJson

/**
 * Generate Scala Types from an OpenAPI Spec — Map Schema Types to Scala Types
 *
 * Maps the properties of `Book` and `Order`, and an OpenAPI 3.0 nullable
 * property, to codegen `TypeRef`s and prints them as Scala source.
 */
object Step3MapTypesExample {
  // Setup from "Parse the Spec" and "Read the Component Schemas": the parsed spec and its components.
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

  // Map Schema Types to Scala Types
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

  def main(args: Array[String]): Unit = {
    for {
      component          <- components.filter(c => c.name == "Book" || c.name == "Order")
      (property, schema) <- component.schema.properties.toList.flatMap(_.toList)
    } println(s"${component.name}.$property: ${ScalaEmitter.emitTypeRef(typeRefFor(schema))}")

    println(
      ScalaEmitter.emitTypeRef(typeRefFor(JsonSchema.parse("""{"type": "string", "nullable": true}""").toOption.get))
    )
  }
}
