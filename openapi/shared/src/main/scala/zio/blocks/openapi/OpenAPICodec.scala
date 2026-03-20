package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkBuilder, ChunkMap}

import zio.blocks.docs.{
  Autolink,
  Block,
  BulletList,
  BlockQuote,
  Code,
  Doc,
  Emphasis,
  HardBreak,
  Heading,
  HtmlInline,
  Image,
  Inline,
  ListItem,
  OrderedList,
  Paragraph,
  Parser,
  Renderer,
  SoftBreak,
  Strikethrough,
  Strong,
  Table,
  TableRow,
  Text
}
import zio.blocks.docs.{Link => MdLink}
import zio.blocks.schema.Schema
import zio.blocks.schema.json._

object OpenAPICodec {

  // ---------------------------------------------------------------------------
  // Encoder helpers
  // ---------------------------------------------------------------------------

  private def obj(fields: (String, Option[Json])*): Json.Object = {
    val builder = ChunkBuilder.make[(String, Json)](fields.length)
    fields.foreach { case (k, v) => v.foreach(json => builder += (k -> json)) }
    new Json.Object(builder.result())
  }

  private def withExtensions(base: Json.Object, extensions: ChunkMap[String, Json]): Json.Object =
    if (extensions.isEmpty) base
    else new Json.Object(base.value ++ Chunk.from(extensions))

  private def field[A](a: A)(implicit enc: JsonBinaryCodec[A]): Option[Json] = Some(enc.encodeValue(a))

  private def optField[A](a: Option[A])(implicit enc: JsonBinaryCodec[A]): Option[Json] = a.map(enc.encodeValue)

  private def chunkField[A](a: Chunk[A])(implicit enc: JsonBinaryCodec[A]): Option[Json] =
    if (a.isEmpty) None
    else {
      val builder = ChunkBuilder.make[Json](a.length)
      a.foreach(v => builder += enc.encodeValue(v))
      Some(new Json.Array(builder.result()))
    }

  private def chunkMapField[V](a: ChunkMap[String, V])(implicit enc: JsonBinaryCodec[V]): Option[Json] =
    if (a.isEmpty) None
    else Some(new Json.Object(a.toChunk.map(kv => (kv._1, enc.encodeValue(kv._2)))))

  private def boolField(a: Boolean, default: Boolean = false): Option[Json] =
    if (a == default) None
    else Some(Json.Boolean(a))

  // ---------------------------------------------------------------------------
  // Decoder helpers
  // ---------------------------------------------------------------------------

  private def getField(jObj: Json.Object, name: String): Option[Json] =
    jObj.value.collectFirst { case kv if kv._1 == name => kv._2 }

  private def reqField[A](jObj: Json.Object, name: String)(implicit dec: JsonBinaryCodec[A]): A =
    getField(jObj, name) match {
      case Some(json) => dec.decodeValue(json)
      case _          => throw new JsonBinaryCodecError(Nil, s"Missing required field: $name")
    }

  private def optFieldDec[A](jObj: Json.Object, name: String)(implicit
    dec: JsonBinaryCodec[A]
  ): Option[A] =
    getField(jObj, name) match {
      case Some(Json.Null) | None => None
      case Some(json)             => Some(dec.decodeValue(json))
    }

  private def chunkFieldDec[A](jObj: Json.Object, name: String)(implicit
    dec: JsonBinaryCodec[A]
  ): Chunk[A] =
    getField(jObj, name) match {
      case None                  => Chunk.empty
      case Some(arr: Json.Array) =>
        val builder = ChunkBuilder.make[A](arr.value.length)
        arr.value.foreach { v =>
          builder.addOne(dec.decodeValue(v))
        }
        builder.result()
      case Some(_) => throw new JsonBinaryCodecError(Nil, s"Expected Json.Array for field: $name")
    }

  private def chunkMapFieldDec[V](jObj: Json.Object, name: String)(implicit
    dec: JsonBinaryCodec[V]
  ): ChunkMap[String, V] =
    getField(jObj, name) match {
      case None                   => ChunkMap.empty
      case Some(obj: Json.Object) =>
        val builder = ChunkMap.newBuilder[String, V]
        obj.value.foreach { kv =>
          builder.addOne((kv._1, dec.decodeValue(kv._2)))
        }
        builder.result()
      case Some(_) => throw new JsonBinaryCodecError(Nil, s"Expected Json.Object for field: $name")
    }

  private def boolFieldDec(jObj: Json.Object, name: String, default: Boolean = false): Boolean =
    getField(jObj, name) match {
      case Some(json) => JsonBinaryCodec.booleanCodec.decodeValue(json)
      case _          => default
    }

  private def extractExtensions(jObj: Json.Object): ChunkMap[String, Json] = {
    val builder = ChunkMap.newBuilder[String, Json]
    jObj.value.foreach { case (k, v) => if (k.startsWith("x-")) builder += (k -> v) }
    builder.result()
  }

  private def optBoolField(a: Option[Boolean]): Option[Json] = a.map(Json.Boolean(_))

  private def optBoolFieldDec(jObj: Json.Object, name: String): Option[Boolean] =
    getField(jObj, name) match {
      case Some(json) => Some(JsonBinaryCodec.booleanCodec.decodeValue(json))
      case _          => None
    }

  // ---------------------------------------------------------------------------
  // Doc normalization helpers
  // ---------------------------------------------------------------------------

  /**
   * Normalize a Doc by converting all top-level inline types to their Inline.*
   * equivalents.
   *
   * This is necessary because Parser.parse() creates top-level Text, Code, etc.
   * instances, but DocsSchemas expects Inline.Text, Inline.Code, etc.
   */
  private def normalizeDoc(doc: Doc): Doc = Doc(doc.blocks.map(normalizeBlock), doc.metadata)

  private def normalizeBlock(block: Block): Block = block match {
    case Paragraph(content)               => Paragraph(normalizeInlines(content))
    case Heading(level, content)          => Heading(level, normalizeInlines(content))
    case BlockQuote(content)              => BlockQuote(content.map(normalizeBlock))
    case BulletList(items, tight)         => BulletList(items.map(normalizeListItem), tight)
    case OrderedList(start, items, tight) => OrderedList(start, items.map(normalizeListItem), tight)
    case ListItem(content, checked)       => ListItem(content.map(normalizeBlock), checked)
    case Table(header, alignments, rows)  => Table(normalizeTableRow(header), alignments, rows.map(normalizeTableRow))
    case other                            => other // CodeBlock, ThematicBreak, HtmlBlock unchanged
  }

  private def normalizeListItem(item: ListItem): ListItem = ListItem(item.content.map(normalizeBlock), item.checked)

  private def normalizeTableRow(row: TableRow): TableRow = TableRow(row.cells.map(normalizeInlines))

  private def normalizeInlines(inlines: Chunk[Inline]): Chunk[Inline] = inlines.map(normalizeInline)

  private def normalizeInline(inline: Inline): Inline = inline match {
    // Convert top-level types to Inline.* types
    case t: Text           => Inline.Text(t.value)
    case c: Code           => Inline.Code(c.value)
    case e: Emphasis       => Inline.Emphasis(normalizeInlines(e.content))
    case s: Strong         => Inline.Strong(normalizeInlines(s.content))
    case st: Strikethrough => Inline.Strikethrough(normalizeInlines(st.content))
    case l: MdLink         => Inline.Link(normalizeInlines(l.text), l.url, l.title)
    case i: Image          => Inline.Image(i.alt, i.url, i.title)
    case h: HtmlInline     => Inline.HtmlInline(h.content)
    case SoftBreak         => Inline.SoftBreak
    case HardBreak         => Inline.HardBreak
    case a: Autolink       => Inline.Autolink(a.url, a.isEmail)
    // Inline.* types are already correct, pass through
    case it: Inline.Text           => it
    case ic: Inline.Code           => ic
    case ie: Inline.Emphasis       => Inline.Emphasis(normalizeInlines(ie.content))
    case is: Inline.Strong         => Inline.Strong(normalizeInlines(is.content))
    case ist: Inline.Strikethrough => Inline.Strikethrough(normalizeInlines(ist.content))
    case il: Inline.Link           => Inline.Link(normalizeInlines(il.text), il.url, il.title)
    case ii: Inline.Image          => ii
    case ih: Inline.HtmlInline     => ih
    case Inline.SoftBreak          => Inline.SoftBreak
    case Inline.HardBreak          => Inline.HardBreak
    case ia: Inline.Autolink       => ia
  }

  private abstract class JsonASTCodec[A] extends JsonBinaryCodec[A] {
    def decodeValue(in: JsonReader): A = ???

    def encodeValue(x: A, out: JsonWriter): Unit = ???
  }

  implicit val stringCodec: JsonBinaryCodec[String]            = Schema[String].jsonCodec
  implicit val optStringCodec: JsonBinaryCodec[Option[String]] = Schema[Option[String]].jsonCodec
  implicit val docCodec: JsonBinaryCodec[Doc]                  = new JsonASTCodec[Doc] {
    override def decodeValue(json: Json): Doc = json match {
      case str: Json.String =>
        Parser.parse(str.value) match {
          case Right(doc) => normalizeDoc(doc)
          case Left(_)    => Doc(Chunk(Paragraph(Chunk(Inline.Text(str.value)))))
        }
      case _ => error("Expected String for Doc")
    }

    override def encodeValue(x: Doc): Json = Json.String(Renderer.render(x))
  }
  implicit val contactCodec: JsonBinaryCodec[Contact] = new JsonASTCodec[Contact] {
    override def decodeValue(json: Json): Contact = json match {
      case jObj: Json.Object =>
        val name  = optFieldDec[String](jObj, "name")
        val url   = optFieldDec[String](jObj, "url")
        val email = optFieldDec[String](jObj, "email")
        Contact(name, url, email, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Contact")
    }

    override def encodeValue(x: Contact): Json =
      withExtensions(
        obj(
          "name"  -> optField(x.name),
          "url"   -> optField(x.url),
          "email" -> optField(x.email)
        ),
        x.extensions
      )
  }
  implicit val licenseCodec: JsonBinaryCodec[License] = new JsonASTCodec[License] {
    override def decodeValue(json: Json): License = json match {
      case jObj: Json.Object =>
        val name       = reqField[String](jObj, "name")
        val identifier = optFieldDec[String](jObj, "identifier")
        val url        = optFieldDec[String](jObj, "url")
        License(name, identifier, url, extractExtensions(jObj))
      case _ => error("Expected Json.Object for License")
    }

    override def encodeValue(x: License): Json = {
      val identifierOpt = x.identifierOrUrl.flatMap(_.left.toOption)
      val urlOpt        = x.identifierOrUrl.flatMap(_.toOption)
      withExtensions(
        obj(
          "name"       -> field(x.name),
          "identifier" -> optField(identifierOpt),
          "url"        -> optField(urlOpt)
        ),
        x.extensions
      )
    }
  }
  implicit val externalDocumentationCodec: JsonBinaryCodec[ExternalDocumentation] =
    new JsonASTCodec[ExternalDocumentation] {
      override def decodeValue(json: Json): ExternalDocumentation = json match {
        case jObj: Json.Object =>
          val url         = reqField[String](jObj, "url")
          val description = optFieldDec[Doc](jObj, "description")
          ExternalDocumentation(url, description, extractExtensions(jObj))
        case _ => error("Expected Json.Object for ExternalDocumentation")
      }

      override def encodeValue(x: ExternalDocumentation): Json =
        withExtensions(
          obj(
            "url"         -> field(x.url),
            "description" -> optField(x.description)
          ),
          x.extensions
        )
    }
  implicit val serverVariableCodec: JsonBinaryCodec[ServerVariable] = new JsonASTCodec[ServerVariable] {
    override def decodeValue(json: Json): ServerVariable = json match {
      case jObj: Json.Object =>
        val default     = reqField[String](jObj, "default")
        val enumValues  = chunkFieldDec[String](jObj, "enum")
        val description = optFieldDec[Doc](jObj, "description")
        ServerVariable(default, enumValues, description, extractExtensions(jObj))
      case _ => error("Expected Json.Object for ServerVariable")
    }

    override def encodeValue(x: ServerVariable): Json =
      withExtensions(
        obj(
          "default"     -> field(x.default),
          "enum"        -> chunkField(x.`enum`),
          "description" -> optField(x.description)
        ),
        x.extensions
      )
  }
  implicit val serverCodec: JsonBinaryCodec[Server] = new JsonASTCodec[Server] {
    override def decodeValue(json: Json): Server = json match {
      case jObj: Json.Object =>
        val url         = reqField[String](jObj, "url")
        val description = optFieldDec[Doc](jObj, "description")
        val variables   = chunkMapFieldDec[ServerVariable](jObj, "variables")
        Server(url, description, variables, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Server")
    }

    override def encodeValue(x: Server): Json =
      withExtensions(
        obj(
          "url"         -> field(x.url),
          "description" -> optField(x.description),
          "variables"   -> chunkMapField(x.variables)
        ),
        x.extensions
      )
  }
  implicit val tagCodec: JsonBinaryCodec[Tag] = new JsonASTCodec[Tag] {
    override def decodeValue(json: Json): Tag = json match {
      case jObj: Json.Object =>
        val name         = reqField[String](jObj, "name")
        val description  = optFieldDec[Doc](jObj, "description")
        val externalDocs = optFieldDec[ExternalDocumentation](jObj, "externalDocs")
        Tag(name, description, externalDocs, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Tag")
    }

    override def encodeValue(x: Tag): Json =
      withExtensions(
        obj(
          "name"         -> field(x.name),
          "description"  -> optField(x.description),
          "externalDocs" -> optField(x.externalDocs)
        ),
        x.extensions
      )
  }
  implicit val infoCodec: JsonBinaryCodec[Info] = new JsonASTCodec[Info] { info =>
    override def decodeValue(json: Json): Info = json match {
      case jObj: Json.Object =>
        val title          = reqField[String](jObj, "title")
        val version        = reqField[String](jObj, "version")
        val summary        = optFieldDec[Doc](jObj, "summary")
        val description    = optFieldDec[Doc](jObj, "description")
        val termsOfService = optFieldDec[String](jObj, "termsOfService")
        val contact        = optFieldDec[Contact](jObj, "contact")
        val license        = optFieldDec[License](jObj, "license")
        Info(title, version, summary, description, termsOfService, contact, license, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Info")
    }

    override def encodeValue(info: Info): Json =
      withExtensions(
        obj(
          "title"          -> field(info.title),
          "version"        -> field(info.version),
          "summary"        -> optField(info.summary),
          "description"    -> optField(info.description),
          "termsOfService" -> optField(info.termsOfService),
          "contact"        -> optField(info.contact),
          "license"        -> optField(info.license)
        ),
        info.extensions
      )
  }
  implicit val xmlCodec: JsonBinaryCodec[XML] = new JsonASTCodec[XML] {
    override def decodeValue(json: Json): XML = json match {
      case jObj: Json.Object =>
        val name      = optFieldDec[String](jObj, "name")
        val namespace = optFieldDec[String](jObj, "namespace")
        val prefix    = optFieldDec[String](jObj, "prefix")
        val attribute = boolFieldDec(jObj, "attribute")
        val wrapped   = boolFieldDec(jObj, "wrapped")
        XML(name, namespace, prefix, attribute, wrapped)
      case _ => error("Expected Json.Object for XML")
    }

    override def encodeValue(x: XML): Json =
      obj(
        "name"      -> optField(x.name),
        "namespace" -> optField(x.namespace),
        "prefix"    -> optField(x.prefix),
        "attribute" -> boolField(x.attribute),
        "wrapped"   -> boolField(x.wrapped)
      )
  }
  implicit val discriminatorCodec: JsonBinaryCodec[Discriminator] = new JsonASTCodec[Discriminator] {
    override def decodeValue(json: Json): Discriminator = json match {
      case jObj: Json.Object =>
        val propertyName = reqField[String](jObj, "propertyName")
        val mapping      = chunkMapFieldDec[String](jObj, "mapping")
        Discriminator(propertyName, mapping)
      case _ => error("Expected Json.Object for Discriminator")
    }

    override def encodeValue(x: Discriminator): Json =
      obj(
        "propertyName" -> field(x.propertyName),
        "mapping"      -> chunkMapField(x.mapping)
      )
  }
  implicit val parameterLocationCodec: JsonBinaryCodec[ParameterLocation] = new JsonASTCodec[ParameterLocation] {
    override def decodeValue(json: Json): ParameterLocation = json match {
      case str: Json.String =>
        str.value match {
          case "query"  => ParameterLocation.Query
          case "header" => ParameterLocation.Header
          case "path"   => ParameterLocation.Path
          case "cookie" => ParameterLocation.Cookie
          case other    => error(s"Invalid parameter location: $other")
        }
      case _ => error("Expected String for ParameterLocation")
    }

    override def encodeValue(x: ParameterLocation): Json = x match {
      case ParameterLocation.Query  => Json.String("query")
      case ParameterLocation.Header => Json.String("header")
      case ParameterLocation.Path   => Json.String("path")
      case ParameterLocation.Cookie => Json.String("cookie")
    }
  }
  implicit val apiKeyLocationCodec: JsonBinaryCodec[APIKeyLocation] = new JsonASTCodec[APIKeyLocation] {
    override def decodeValue(json: Json): APIKeyLocation = json match {
      case str: Json.String =>
        str.value match {
          case "query"  => APIKeyLocation.Query
          case "header" => APIKeyLocation.Header
          case "cookie" => APIKeyLocation.Cookie
          case other    => error(s"Invalid API key location: $other")
        }
      case _ => error("Expected String for APIKeyLocation")
    }

    override def encodeValue(x: APIKeyLocation): Json = x match {
      case APIKeyLocation.Query  => Json.String("query")
      case APIKeyLocation.Header => Json.String("header")
      case APIKeyLocation.Cookie => Json.String("cookie")
    }
  }
  implicit val referenceCodec: JsonBinaryCodec[Reference] = new JsonASTCodec[Reference] {
    override def decodeValue(json: Json): Reference = json match {
      case jObj: Json.Object =>
        val ref         = reqField[String](jObj, "$ref")
        val summary     = optFieldDec[Doc](jObj, "summary")
        val description = optFieldDec[Doc](jObj, "description")
        Reference(ref, summary, description)
      case _ => error("Expected Json.Object for Reference")
    }

    override def encodeValue(ref: Reference): Json =
      obj(
        "$ref"        -> field(ref.`$ref`),
        "summary"     -> optField(ref.summary),
        "description" -> optField(ref.description)
      )
  }
  implicit def referenceOrCodec[A](implicit codec: JsonBinaryCodec[A]): JsonBinaryCodec[ReferenceOr[A]] =
    new JsonASTCodec[ReferenceOr[A]] {
      override def decodeValue(json: Json): ReferenceOr[A] = json match {
        case jObj: Json.Object if getField(jObj, "$ref").isDefined =>
          ReferenceOr.Ref(referenceCodec.decodeValue(json))
        case _ => ReferenceOr.Value(codec.decodeValue(json))
      }

      override def encodeValue(x: ReferenceOr[A]): Json = x match {
        case ReferenceOr.Ref(reference) => referenceCodec.encodeValue(reference)
        case ReferenceOr.Value(value)   => codec.encodeValue(value)
      }
    }
  implicit val oauthFlowCodec: JsonBinaryCodec[OAuthFlow] = new JsonASTCodec[OAuthFlow] {
    override def decodeValue(json: Json): OAuthFlow = json match {
      case jObj: Json.Object =>
        val authorizationUrl = optFieldDec[String](jObj, "authorizationUrl")
        val tokenUrl         = optFieldDec[String](jObj, "tokenUrl")
        val refreshUrl       = optFieldDec[String](jObj, "refreshUrl")
        val scopes           = chunkMapFieldDec[String](jObj, "scopes")
        OAuthFlow(authorizationUrl, tokenUrl, refreshUrl, scopes, extractExtensions(jObj))
      case _ => error("Expected Json.Object for OAuthFlow")
    }

    override def encodeValue(x: OAuthFlow): Json =
      withExtensions(
        obj(
          "authorizationUrl" -> optField(x.authorizationUrl),
          "tokenUrl"         -> optField(x.tokenUrl),
          "refreshUrl"       -> optField(x.refreshUrl),
          "scopes"           -> chunkMapField(x.scopes)
        ),
        x.extensions
      )
  }
  implicit val oauthFlowsCodec: JsonBinaryCodec[OAuthFlows] = new JsonASTCodec[OAuthFlows] {
    override def decodeValue(json: Json): OAuthFlows = json match {
      case jObj: Json.Object =>
        val impl      = optFieldDec[OAuthFlow](jObj, "implicit")
        val password  = optFieldDec[OAuthFlow](jObj, "password")
        val clientCr  = optFieldDec[OAuthFlow](jObj, "clientCredentials")
        val authzCode = optFieldDec[OAuthFlow](jObj, "authorizationCode")
        OAuthFlows(impl, password, clientCr, authzCode, extractExtensions(jObj))
      case _ => error("Expected Json.Object for OAuthFlows")
    }

    override def encodeValue(x: OAuthFlows): Json =
      withExtensions(
        obj(
          "implicit"          -> optField(x.`implicit`),
          "password"          -> optField(x.password),
          "clientCredentials" -> optField(x.clientCredentials),
          "authorizationCode" -> optField(x.authorizationCode)
        ),
        x.extensions
      )
  }
  implicit val securitySchemeCodec: JsonBinaryCodec[SecurityScheme] = new JsonASTCodec[SecurityScheme] {
    override def decodeValue(json: Json): SecurityScheme = json match {
      case jObj: Json.Object =>
        reqField[String](jObj, "type") match {
          case "apiKey" =>
            val name        = reqField[String](jObj, "name")
            val in          = reqField[APIKeyLocation](jObj, "in")
            val description = optFieldDec[Doc](jObj, "description")
            SecurityScheme.APIKey(name, in, description, extractExtensions(jObj))
          case "http" =>
            val scheme       = reqField[String](jObj, "scheme")
            val bearerFormat = optFieldDec[String](jObj, "bearerFormat")
            val description  = optFieldDec[Doc](jObj, "description")
            SecurityScheme.HTTP(scheme, bearerFormat, description, extractExtensions(jObj))
          case "oauth2" =>
            val flows       = reqField[OAuthFlows](jObj, "flows")
            val description = optFieldDec[Doc](jObj, "description")
            SecurityScheme.OAuth2(flows, description, extractExtensions(jObj))
          case "openIdConnect" =>
            val url         = reqField[String](jObj, "openIdConnectUrl")
            val description = optFieldDec[Doc](jObj, "description")
            SecurityScheme.OpenIdConnect(url, description, extractExtensions(jObj))
          case "mutualTLS" =>
            val description = optFieldDec[Doc](jObj, "description")
            SecurityScheme.MutualTLS(description, extractExtensions(jObj))
          case other => error(s"Unknown security scheme type: $other")
        }
      case _ => error("Expected Json.Object for SecurityScheme")
    }

    override def encodeValue(x: SecurityScheme): Json = x match {
      case s: SecurityScheme.APIKey =>
        withExtensions(
          obj(
            "type"        -> field("apiKey"),
            "name"        -> field(s.name),
            "in"          -> field(s.in),
            "description" -> optField(s.description)
          ),
          s.extensions
        )
      case s: SecurityScheme.HTTP =>
        withExtensions(
          obj(
            "type"         -> field("http"),
            "scheme"       -> field(s.scheme),
            "bearerFormat" -> optField(s.bearerFormat),
            "description"  -> optField(s.description)
          ),
          s.extensions
        )
      case s: SecurityScheme.OAuth2 =>
        withExtensions(
          obj(
            "type"        -> field("oauth2"),
            "flows"       -> field(s.flows),
            "description" -> optField(s.description)
          ),
          s.extensions
        )
      case s: SecurityScheme.OpenIdConnect =>
        withExtensions(
          obj(
            "type"             -> field("openIdConnect"),
            "openIdConnectUrl" -> field(s.openIdConnectUrl),
            "description"      -> optField(s.description)
          ),
          s.extensions
        )
      case s: SecurityScheme.MutualTLS =>
        withExtensions(
          obj(
            "type"        -> field("mutualTLS"),
            "description" -> optField(s.description)
          ),
          s.extensions
        )
    }
  }
  implicit val securityRequirementCodec: JsonBinaryCodec[SecurityRequirement] = new JsonASTCodec[SecurityRequirement] {
    override def decodeValue(json: Json): SecurityRequirement = json match {
      case jObj: Json.Object =>
        val builder = ChunkMap.newBuilder[String, Chunk[String]]
        jObj.value.foreach { case (k, v) =>
          v match {
            case arr: Json.Array =>
              val scopeBuilder = ChunkBuilder.make[String](arr.value.length)
              arr.value.foreach {
                case s: Json.String => scopeBuilder += s.value
                case other          => error(s"Expected String in security scopes, got: $other")
              }
              builder += (k -> scopeBuilder.result())
            case _ => error(s"Expected Array for security requirement scopes")
          }
        }
        SecurityRequirement(builder.result())
      case _ => error("Expected Json.Object for SecurityRequirement")
    }

    override def encodeValue(x: SecurityRequirement): Json = {
      val builder = ChunkBuilder.make[(String, Json)](x.requirements.size)
      x.requirements.foreach { case (name, scopes) =>
        val scopeArray = new Json.Array(scopes.map(Json.String(_): Json))
        builder += (name -> scopeArray)
      }
      new Json.Object(builder.result())
    }
  }
  implicit val schemaObjectCodec: JsonBinaryCodec[SchemaObject] = new JsonASTCodec[SchemaObject] {
    override def decodeValue(json: Json): SchemaObject = json match {
      case jObj: Json.Object =>
        val disc         = optFieldDec[Discriminator](jObj, "discriminator")
        val x            = optFieldDec[XML](jObj, "xml")
        val ed           = optFieldDec[ExternalDocumentation](jObj, "externalDocs")
        val ex           = getField(jObj, "example")
        val ext          = extractExtensions(jObj)
        val schemaFields = jObj.value.filter { case (k, _) =>
          k != "discriminator" && k != "xml" && k != "externalDocs" && k != "example" && !k.startsWith("x-")
        }
        val jsonSchema = new Json.Object(schemaFields)
        SchemaObject(jsonSchema, disc, x, ed, ex, ext)
      case bool: Json.Boolean => SchemaObject(bool)
      case _                  => error("Expected Json.Object or Json.Boolean for SchemaObject")
    }

    override def encodeValue(x: SchemaObject): Json = x.toJson
  }
  implicit val exampleCodec: JsonBinaryCodec[Example] = new JsonASTCodec[Example] {
    override def decodeValue(json: Json): Example = json match {
      case jObj: Json.Object =>
        val summary       = optFieldDec[Doc](jObj, "summary")
        val description   = optFieldDec[Doc](jObj, "description")
        val value         = optFieldDec[Json](jObj, "value")
        val externalValue = optFieldDec[String](jObj, "externalValue")
        Example(summary, description, value, externalValue, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Example")
    }

    override def encodeValue(x: Example): Json =
      withExtensions(
        obj(
          "summary"       -> optField(x.summary),
          "description"   -> optField(x.description),
          "value"         -> optField(x.value),
          "externalValue" -> optField(x.externalValue)
        ),
        x.extensions
      )
  }
  implicit lazy val linkCodec: JsonBinaryCodec[Link] = new JsonASTCodec[Link] {
    override def decodeValue(json: Json): Link = json match {
      case jObj: Json.Object =>
        val operationRef = optFieldDec[String](jObj, "operationRef")
        val operationId  = optFieldDec[String](jObj, "operationId")
        val parameters   = chunkMapFieldDec[Json](jObj, "parameters")
        val requestBody  = optFieldDec[Json](jObj, "requestBody")
        val description  = optFieldDec[Doc](jObj, "description")
        val server       = optFieldDec[Server](jObj, "server")
        Link(operationRef, operationId, parameters, requestBody, description, server, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Link")
    }

    override def encodeValue(x: Link): Json = {
      val operationRefOpt = x.operationRefOrId.flatMap(_.left.toOption)
      val operationIdOpt  = x.operationRefOrId.flatMap(_.toOption)
      withExtensions(
        obj(
          "operationRef" -> optField(operationRefOpt),
          "operationId"  -> optField(operationIdOpt),
          "parameters"   -> chunkMapField(x.parameters),
          "requestBody"  -> optField(x.requestBody),
          "description"  -> optField(x.description),
          "server"       -> optField(x.server)
        ),
        x.extensions
      )
    }
  }
  implicit lazy val encodingCodec: JsonBinaryCodec[Encoding] = new JsonASTCodec[Encoding] {
    override def decodeValue(json: Json): Encoding = json match {
      case jObj: Json.Object =>
        val contentType = optFieldDec[String](jObj, "contentType")
        val headers     =
          chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers")
        val style         = optFieldDec[String](jObj, "style")
        val explode       = optBoolFieldDec(jObj, "explode")
        val allowReserved = boolFieldDec(jObj, "allowReserved")
        Encoding(contentType, headers, style, explode, allowReserved, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Encoding")
    }

    override def encodeValue(x: Encoding): Json =
      withExtensions(
        obj(
          "contentType"   -> optField(x.contentType),
          "headers"       -> chunkMapField(x.headers),
          "style"         -> optField(x.style),
          "explode"       -> optBoolField(x.explode),
          "allowReserved" -> boolField(x.allowReserved)
        ),
        x.extensions
      )
  }
  implicit lazy val mediaTypeCodec: JsonBinaryCodec[MediaType] = new JsonASTCodec[MediaType] {
    override def decodeValue(json: Json): MediaType = json match {
      case jObj: Json.Object =>
        val schema   = optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema")
        val example  = optFieldDec[Json](jObj, "example")
        val examples = chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")
        val encoding = chunkMapFieldDec[Encoding](jObj, "encoding")
        MediaType(schema, example, examples, encoding, extractExtensions(jObj))
      case _ => error("Expected Json.Object for MediaType")
    }

    override def encodeValue(x: MediaType): Json =
      withExtensions(
        obj(
          "schema"   -> optField(x.schema),
          "example"  -> optField(x.example),
          "examples" -> chunkMapField(x.examples),
          "encoding" -> chunkMapField(x.encoding)
        ),
        x.extensions
      )
  }
  implicit lazy val headerCodec: JsonBinaryCodec[Header] = new JsonASTCodec[Header] {
    override def decodeValue(json: Json): Header = json match {
      case jObj: Json.Object =>
        val description     = optFieldDec[Doc](jObj, "description")
        val required        = boolFieldDec(jObj, "required")
        val deprecated      = boolFieldDec(jObj, "deprecated")
        val allowEmptyValue = boolFieldDec(jObj, "allowEmptyValue")
        val style           = optFieldDec[String](jObj, "style")
        val explode         = optBoolFieldDec(jObj, "explode")
        val allowReserved   = optBoolFieldDec(jObj, "allowReserved")
        val schema          = optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema")
        val example         = optFieldDec[Json](jObj, "example")
        val examples        = chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")
        val content         = chunkMapFieldDec[MediaType](jObj, "content")
        Header(
          description,
          required,
          deprecated,
          allowEmptyValue,
          style,
          explode,
          allowReserved,
          schema,
          example,
          examples,
          content,
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Header")
    }

    override def encodeValue(x: Header): Json =
      withExtensions(
        obj(
          "description"     -> optField(x.description),
          "required"        -> boolField(x.required),
          "deprecated"      -> boolField(x.deprecated),
          "allowEmptyValue" -> boolField(x.allowEmptyValue),
          "style"           -> optField(x.style),
          "explode"         -> optBoolField(x.explode),
          "allowReserved"   -> optBoolField(x.allowReserved),
          "schema"          -> optField(x.schema),
          "example"         -> optField(x.example),
          "examples"        -> chunkMapField(x.examples),
          "content"         -> chunkMapField(x.content)
        ),
        x.extensions
      )
  }
  implicit lazy val parameterCodec: JsonBinaryCodec[Parameter] = new JsonASTCodec[Parameter] {
    override def decodeValue(json: Json): Parameter = json match {
      case jObj: Json.Object =>
        val name            = reqField[String](jObj, "name")
        val in              = reqField[ParameterLocation](jObj, "in")
        val description     = optFieldDec[Doc](jObj, "description")
        val required        = boolFieldDec(jObj, "required")
        val deprecated      = boolFieldDec(jObj, "deprecated")
        val allowEmptyValue = boolFieldDec(jObj, "allowEmptyValue")
        val style           = optFieldDec[String](jObj, "style")
        val explode         = optBoolFieldDec(jObj, "explode")
        val allowReserved   = optBoolFieldDec(jObj, "allowReserved")
        val schema          = optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema")
        val example         = optFieldDec[Json](jObj, "example")
        val examples        = chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")
        val content         = chunkMapFieldDec[MediaType](jObj, "content")
        Parameter(
          name,
          in,
          description,
          required,
          deprecated,
          allowEmptyValue,
          style,
          explode,
          allowReserved,
          schema,
          example,
          examples,
          content,
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Parameter")
    }

    override def encodeValue(p: Parameter): Json =
      withExtensions(
        obj(
          "name"            -> field(p.name),
          "in"              -> field(p.in),
          "description"     -> optField(p.description),
          "required"        -> boolField(p.required),
          "deprecated"      -> boolField(p.deprecated),
          "allowEmptyValue" -> boolField(p.allowEmptyValue),
          "style"           -> optField(p.style),
          "explode"         -> optBoolField(p.explode),
          "allowReserved"   -> optBoolField(p.allowReserved),
          "schema"          -> optField(p.schema),
          "example"         -> optField(p.example),
          "examples"        -> chunkMapField(p.examples),
          "content"         -> chunkMapField(p.content)
        ),
        p.extensions
      )
  }
  implicit lazy val requestBodyCodec: JsonBinaryCodec[RequestBody] = new JsonASTCodec[RequestBody] {
    override def decodeValue(json: Json): RequestBody = json match {
      case jObj: Json.Object =>
        val content     = chunkMapFieldDec[MediaType](jObj, "content")
        val description = optFieldDec[Doc](jObj, "description")
        val required    = boolFieldDec(jObj, "required")
        RequestBody(content, description, required, extractExtensions(jObj))
      case _ => error("Expected Json.Object for RequestBody")
    }

    override def encodeValue(rb: RequestBody): Json =
      withExtensions(
        obj(
          "content"     -> chunkMapField(rb.content),
          "description" -> optField(rb.description),
          "required"    -> boolField(rb.required)
        ),
        rb.extensions
      )
  }
  implicit lazy val responseCodec: JsonBinaryCodec[Response] = new JsonASTCodec[Response] {
    override def decodeValue(json: Json): Response = json match {
      case jObj: Json.Object =>
        val description = reqField[Doc](jObj, "description")
        val headers     =
          chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers")
        val content = chunkMapFieldDec[MediaType](jObj, "content")
        val links   = chunkMapFieldDec[ReferenceOr[Link]](jObj, "links")
        Response(description, headers, content, links, extractExtensions(jObj))
      case _ => error("Expected Json.Object for Response")
    }

    override def encodeValue(x: Response): Json =
      withExtensions(
        obj(
          "description" -> field(x.description),
          "headers"     -> chunkMapField(x.headers),
          "content"     -> chunkMapField(x.content),
          "links"       -> chunkMapField(x.links)
        ),
        x.extensions
      )
  }
  implicit lazy val responsesCodec: JsonBinaryCodec[Responses] = new JsonASTCodec[Responses] {
    override def decodeValue(json: Json): Responses = json match {
      case jObj: Json.Object =>
        val refOrRespDec   = referenceOrCodec[Response](responseCodec)
        val ext            = extractExtensions(jObj)
        val defaultOpt     = getField(jObj, "default")
        val responseFields = jObj.value.filter { case (k, _) =>
          k != "default" && !k.startsWith("x-")
        }
        val default = defaultOpt match {
          case Some(json) => Some(refOrRespDec.decodeValue(json))
          case _          => None
        }
        val responses = {
          val builder = ChunkMap.newBuilder[String, ReferenceOr[Response]]
          responseFields.foreach { case (k, v) =>
            builder += (k -> refOrRespDec.decodeValue(v))
          }
          builder.result()
        }
        Responses(responses, default, ext)
      case _ => error("Expected Json.Object for Responses")
    }

    override def encodeValue(x: Responses): Json = {
      val refOrRespEnc = referenceOrCodec[Response](responseCodec)
      val builder      = ChunkBuilder.make[(String, Json)](x.responses.size + 2)
      x.responses.foreach { case (code, resp) =>
        builder += (code -> refOrRespEnc.encodeValue(resp))
      }
      x.default.foreach { d =>
        builder += ("default" -> refOrRespEnc.encodeValue(d))
      }
      x.extensions.foreach { case (k, v) => builder += (k -> v) }
      new Json.Object(builder.result())
    }
  }
  implicit lazy val callbackCodec: JsonBinaryCodec[Callback] = new JsonASTCodec[Callback] {
    override def decodeValue(json: Json): Callback = json match {
      case jObj: Json.Object =>
        val refOrPathDec   = referenceOrCodec[PathItem](pathItemCodec)
        val ext            = extractExtensions(jObj)
        val callbackFields = jObj.value.filter { case (k, _) => !k.startsWith("x-") }
        val builder        = ChunkMap.newBuilder[String, ReferenceOr[PathItem]]
        callbackFields.foreach { case (k, v) =>
          builder += (k -> refOrPathDec.decodeValue(v))
        }
        Callback(builder.result(), ext)
      case _ => error("Expected Json.Object for Callback")
    }

    override def encodeValue(x: Callback): Json = {
      val pathFields = x.callbacks.map { case (k, v) =>
        (k, referenceOrCodec[PathItem](pathItemCodec).encodeValue(v))
      }
      new Json.Object(Chunk.from(pathFields) ++ Chunk.from(x.extensions))
    }
  }
  implicit lazy val operationCodec: JsonBinaryCodec[Operation] = new JsonASTCodec[Operation] {
    override def decodeValue(json: Json): Operation = json match {
      case jObj: Json.Object =>
        val responses =
          getField(jObj, "responses") match {
            case Some(rJson) => responsesCodec.decodeValue(rJson)
            case _           => Responses()
          }
        val tags        = chunkFieldDec[String](jObj, "tags")
        val summary     = optFieldDec[Doc](jObj, "summary")
        val description = optFieldDec[Doc](jObj, "description")
        val extDocs     = optFieldDec[ExternalDocumentation](jObj, "externalDocs")
        val operationId = optFieldDec[String](jObj, "operationId")
        val parameters  = chunkFieldDec[ReferenceOr[Parameter]](jObj, "parameters")(
          referenceOrCodec[Parameter](parameterCodec)
        )
        val requestBody = optFieldDec[ReferenceOr[RequestBody]](jObj, "requestBody")(
          referenceOrCodec[RequestBody](requestBodyCodec)
        )
        val callbacks =
          chunkMapFieldDec[ReferenceOr[Callback]](jObj, "callbacks")(
            referenceOrCodec[Callback](callbackCodec)
          )
        val deprecated = boolFieldDec(jObj, "deprecated")
        val security   = chunkFieldDec[SecurityRequirement](jObj, "security")
        val servers    = chunkFieldDec[Server](jObj, "servers")
        Operation(
          responses,
          tags,
          summary,
          description,
          extDocs,
          operationId,
          parameters,
          requestBody,
          callbacks,
          deprecated,
          security,
          servers,
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Operation")
    }

    override def encodeValue(x: Operation): Json =
      withExtensions(
        obj(
          "responses"    -> field(x.responses),
          "tags"         -> chunkField(x.tags),
          "summary"      -> optField(x.summary),
          "description"  -> optField(x.description),
          "externalDocs" -> optField(x.externalDocs),
          "operationId"  -> optField(x.operationId),
          "parameters"   -> chunkField(x.parameters),
          "requestBody"  -> optField(x.requestBody),
          "callbacks"    -> chunkMapField(x.callbacks),
          "deprecated"   -> boolField(x.deprecated),
          "security"     -> chunkField(x.security),
          "servers"      -> chunkField(x.servers)
        ),
        x.extensions
      )
  }
  implicit lazy val pathItemCodec: JsonBinaryCodec[PathItem] = new JsonASTCodec[PathItem] {
    override def decodeValue(json: Json): PathItem = json match {
      case jObj: Json.Object =>
        val summary     = optFieldDec[Doc](jObj, "summary")
        val description = optFieldDec[Doc](jObj, "description")
        val get         = optFieldDec[Operation](jObj, "get")
        val put         = optFieldDec[Operation](jObj, "put")
        val post        = optFieldDec[Operation](jObj, "post")
        val delete      = optFieldDec[Operation](jObj, "delete")
        val options     = optFieldDec[Operation](jObj, "options")
        val head        = optFieldDec[Operation](jObj, "head")
        val patch       = optFieldDec[Operation](jObj, "patch")
        val trace       = optFieldDec[Operation](jObj, "trace")
        val servers     = chunkFieldDec[Server](jObj, "servers")
        val parameters  = chunkFieldDec[ReferenceOr[Parameter]](jObj, "parameters")(
          referenceOrCodec[Parameter](parameterCodec)
        )
        PathItem(
          summary,
          description,
          get,
          put,
          post,
          delete,
          options,
          head,
          patch,
          trace,
          servers,
          parameters,
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for PathItem")
    }

    override def encodeValue(x: PathItem): Json =
      withExtensions(
        obj(
          "summary"     -> optField(x.summary),
          "description" -> optField(x.description),
          "get"         -> optField(x.get),
          "put"         -> optField(x.put),
          "post"        -> optField(x.post),
          "delete"      -> optField(x.delete),
          "options"     -> optField(x.options),
          "head"        -> optField(x.head),
          "patch"       -> optField(x.patch),
          "trace"       -> optField(x.trace),
          "servers"     -> chunkField(x.servers),
          "parameters"  -> chunkField(x.parameters)
        ),
        x.extensions
      )
  }
  implicit lazy val pathsCodec: JsonBinaryCodec[Paths] = new JsonASTCodec[Paths] {
    override def decodeValue(json: Json): Paths = json match {
      case jObj: Json.Object =>
        val ext        = extractExtensions(jObj)
        val pathFields = jObj.value.filter { case (k, _) => !k.startsWith("x-") }
        val builder    = ChunkMap.newBuilder[String, PathItem]
        pathFields.foreach { case (k, v) =>
          builder += (k -> pathItemCodec.decodeValue(v))
        }
        Paths(builder.result(), ext)
      case _ => error("Expected Json.Object for Paths")
    }

    override def encodeValue(x: Paths): Json = {
      val pathFields = x.paths.map { case (k, v) => (k, pathItemCodec.encodeValue(v)) }
      new Json.Object(Chunk.from(pathFields) ++ Chunk.from(x.extensions))
    }
  }
  implicit lazy val componentsCodec: JsonBinaryCodec[Components] = new JsonASTCodec[Components] {
    override def decodeValue(json: Json): Components = json match {
      case jObj: Json.Object =>
        val schemas         = chunkMapFieldDec[ReferenceOr[SchemaObject]](jObj, "schemas")
        val responses       = chunkMapFieldDec[ReferenceOr[Response]](jObj, "responses")
        val parameters      = chunkMapFieldDec[ReferenceOr[Parameter]](jObj, "parameters")
        val examples        = chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")
        val requestBodies   = chunkMapFieldDec[ReferenceOr[RequestBody]](jObj, "requestBodies")
        val headers         = chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers")
        val securitySchemes = chunkMapFieldDec[ReferenceOr[SecurityScheme]](jObj, "securitySchemes")
        val links           = chunkMapFieldDec[ReferenceOr[Link]](jObj, "links")
        val callbacks       = chunkMapFieldDec[ReferenceOr[Callback]](jObj, "callbacks")
        val pathItems       = chunkMapFieldDec[ReferenceOr[PathItem]](jObj, "pathItems")
        Components(
          schemas,
          responses,
          parameters,
          examples,
          requestBodies,
          headers,
          securitySchemes,
          links,
          callbacks,
          pathItems,
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Components")
    }

    override def encodeValue(x: Components): Json =
      withExtensions(
        obj(
          "schemas"         -> chunkMapField(x.schemas),
          "responses"       -> chunkMapField(x.responses),
          "parameters"      -> chunkMapField(x.parameters),
          "examples"        -> chunkMapField(x.examples),
          "requestBodies"   -> chunkMapField(x.requestBodies),
          "headers"         -> chunkMapField(x.headers),
          "securitySchemes" -> chunkMapField(x.securitySchemes),
          "links"           -> chunkMapField(x.links),
          "callbacks"       -> chunkMapField(x.callbacks),
          "pathItems"       -> chunkMapField(x.pathItems)
        ),
        x.extensions
      )
  }
  implicit lazy val openAPICodec: JsonBinaryCodec[OpenAPI] = new JsonASTCodec[OpenAPI] {
    override def decodeValue(json: Json): OpenAPI = json match {
      case jObj: Json.Object =>
        val openapi           = reqField[String](jObj, "openapi")
        val info              = reqField[Info](jObj, "info")
        val jsonSchemaDialect = optFieldDec[String](jObj, "jsonSchemaDialect")
        val servers           = chunkFieldDec[Server](jObj, "servers")
        val paths             = optFieldDec[Paths](jObj, "paths")
        val webhooks          = chunkMapFieldDec[ReferenceOr[PathItem]](jObj, "webhooks")
        val components        = optFieldDec[Components](jObj, "components")
        val security          = chunkFieldDec[SecurityRequirement](jObj, "security")
        val tags              = chunkFieldDec[Tag](jObj, "tags")
        val externalDocs      = optFieldDec[ExternalDocumentation](jObj, "externalDocs")
        OpenAPI(
          openapi,
          info,
          jsonSchemaDialect,
          servers,
          paths,
          webhooks,
          components,
          security,
          tags,
          externalDocs,
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for OpenAPI")
    }

    override def encodeValue(x: OpenAPI): Json =
      withExtensions(
        obj(
          "openapi"           -> field(x.openapi),
          "info"              -> field(x.info),
          "jsonSchemaDialect" -> optField(x.jsonSchemaDialect),
          "servers"           -> chunkField(x.servers),
          "paths"             -> optField(x.paths),
          "webhooks"          -> chunkMapField(x.webhooks),
          "components"        -> optField(x.components),
          "security"          -> chunkField(x.security),
          "tags"              -> chunkField(x.tags),
          "externalDocs"      -> optField(x.externalDocs)
        ),
        x.extensions
      )
  }
}
