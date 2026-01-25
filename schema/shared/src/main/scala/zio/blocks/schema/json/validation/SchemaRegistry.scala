package zio.blocks.schema.json.validation

import zio.blocks.schema.json.{JsonSchema, JsonSchemaError}

/**
 * Registry for resolving JSON Schema references ($ref).
 *
 * Maintains a collection of schemas indexed by their $id or anchor, allowing
 * resolution of `$ref` pointers during validation.
 */
final case class SchemaRegistry(
  schemas: Map[String, JsonSchema],
  baseUri: Option[String] = None
) {

  /**
   * Resolves a $ref to a JsonSchema.
   *
   * Supports:
   *   - JSON Pointer fragments (#/path/to/def)
   *   - Anchors (#anchor)
   *   - Absolute URIs
   *   - Relative URIs (when baseUri is set)
   */
  def resolve(ref: String, rootSchema: JsonSchema): Either[JsonSchemaError, JsonSchema] =
    // Handle internal references first
    if (ref.startsWith("#/$defs/") || ref.startsWith("#/definitions/")) {
      val defPath = ref.dropWhile(_ != '/').drop(1).split('/').toList
      resolveDefinition(defPath, rootSchema)
    } else if (ref.startsWith("#")) {
      // Anchor reference
      val anchor = ref.drop(1)
      if (anchor.isEmpty) Right(rootSchema)
      else resolveAnchor(anchor)
    } else {
      // External or absolute reference
      schemas.get(ref) match {
        case Some(schema) => Right(schema)
        case None         =>
          // Try with baseUri
          baseUri.flatMap(base => schemas.get(resolveRelativeUri(base, ref))) match {
            case Some(schema) => Right(schema)
            case None         =>
              Left(
                JsonSchemaError(
                  JsonSchemaError.RefNotResolved(zio.blocks.schema.DynamicOptic.root, ref)
                )
              )
          }
      }
    }

  private def resolveDefinition(path: List[String], schema: JsonSchema): Either[JsonSchemaError, JsonSchema] =
    path match {
      case "$defs" :: name :: Nil =>
        schema match {
          case obj: JsonSchema.SchemaObject =>
            obj.`$defs`.flatMap(_.get(name)) match {
              case Some(defSchema) => Right(defSchema)
              case None            =>
                Left(
                  JsonSchemaError(
                    JsonSchemaError.RefNotResolved(
                      zio.blocks.schema.DynamicOptic.root,
                      s"#/$$defs/$name"
                    )
                  )
                )
            }
          case _ =>
            Left(
              JsonSchemaError(
                JsonSchemaError.RefNotResolved(
                  zio.blocks.schema.DynamicOptic.root,
                  s"#/$$defs/$name"
                )
              )
            )
        }
      case "definitions" :: name :: Nil =>
        // Draft 7 compatibility
        resolveDefinition("$defs" :: name :: Nil, schema)
      case _ =>
        Left(
          JsonSchemaError(
            JsonSchemaError.RefNotResolved(
              zio.blocks.schema.DynamicOptic.root,
              path.mkString("/")
            )
          )
        )
    }

  private def resolveAnchor(anchor: String): Either[JsonSchemaError, JsonSchema] =
    schemas.get(s"#$anchor") match {
      case Some(schema) => Right(schema)
      case None         =>
        Left(
          JsonSchemaError(
            JsonSchemaError.RefNotResolved(
              zio.blocks.schema.DynamicOptic.root,
              s"#$anchor"
            )
          )
        )
    }

  private def resolveRelativeUri(base: String, ref: String): String =
    if (ref.contains("://")) ref
    else if (ref.startsWith("/")) {
      val baseScheme = base.takeWhile(_ != ':')
      val baseHost   = base.dropWhile(_ != ':').drop(3).takeWhile(_ != '/')
      s"$baseScheme://$baseHost$ref"
    } else {
      val basePath = base.reverse.dropWhile(_ != '/').reverse
      s"$basePath$ref"
    }

  /**
   * Registers a schema with an id.
   */
  def register(id: String, schema: JsonSchema): SchemaRegistry =
    copy(schemas = schemas + (id -> schema))

  /**
   * Registers multiple schemas.
   */
  def registerAll(entries: (String, JsonSchema)*): SchemaRegistry =
    copy(schemas = schemas ++ entries)

  /**
   * Sets the base URI for resolving relative references.
   */
  def withBaseUri(uri: String): SchemaRegistry =
    copy(baseUri = Some(uri))

  /**
   * Builds a registry from a schema with embedded $id and $defs.
   */
  def indexSchema(schema: JsonSchema): SchemaRegistry = schema match {
    case obj: JsonSchema.SchemaObject =>
      var result = this

      // Index by $id if present
      obj.`$id`.foreach { id =>
        result = result.register(id, schema)
      }

      // Index by $anchor if present
      obj.`$anchor`.foreach { anchor =>
        result = result.register(s"#$anchor", schema)
      }

      // Recursively index $defs
      obj.`$defs`.foreach { defs =>
        defs.foreach { case (_, defSchema) =>
          result = result.indexSchema(defSchema)
        }
      }

      result
    case _ => this
  }
}

object SchemaRegistry {
  val empty: SchemaRegistry = SchemaRegistry(Map.empty, None)

  /**
   * Creates a registry from a root schema, indexing all embedded schemas.
   */
  def from(rootSchema: JsonSchema): SchemaRegistry =
    empty.indexSchema(rootSchema)
}
