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
import zio.blocks.schema.SchemaError
import zio.blocks.schema.json.{Json, JsonDecoder, JsonEncoder}

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

  private def field[A](a: A)(implicit enc: JsonEncoder[A]): Option[Json] = Some(enc.encode(a))

  private def optField[A](a: Option[A])(implicit enc: JsonEncoder[A]): Option[Json] = a.map(enc.encode)

  private def chunkField[A](a: Chunk[A])(implicit enc: JsonEncoder[A]): Option[Json] =
    if (a.isEmpty) None
    else {
      val builder = ChunkBuilder.make[Json](a.length)
      a.foreach(v => builder += enc.encode(v))
      Some(new Json.Array(builder.result()))
    }

  private def chunkMapField[V](a: ChunkMap[String, V])(implicit enc: JsonEncoder[V]): Option[Json] =
    if (a.isEmpty) None else Some(JsonEncoder.mapEncoder(enc).encode(a))

  private def boolField(a: Boolean, default: Boolean = false): Option[Json] =
    if (a == default) None else Some(Json.Boolean(a))

  // ---------------------------------------------------------------------------
  // Decoder helpers
  // ---------------------------------------------------------------------------

  private def getField(jObj: Json.Object, name: String): Option[Json] =
    jObj.value.find(_._1 == name).map(_._2)

  private def reqField[A](jObj: Json.Object, name: String)(implicit dec: JsonDecoder[A]): Either[SchemaError, A] =
    getField(jObj, name) match {
      case Some(json) => dec.decode(json)
      case None       => Left(SchemaError(s"Missing required field: $name"))
    }

  private def optFieldDec[A](jObj: Json.Object, name: String)(implicit
    dec: JsonDecoder[A]
  ): Either[SchemaError, Option[A]] =
    getField(jObj, name) match {
      case Some(Json.Null) | None => Right(None)
      case Some(json)             => dec.decode(json).map(Some(_))
    }

  private def chunkFieldDec[A](jObj: Json.Object, name: String)(implicit
    dec: JsonDecoder[A]
  ): Either[SchemaError, Chunk[A]] =
    getField(jObj, name) match {
      case None                  => Right(Chunk.empty)
      case Some(arr: Json.Array) =>
        val builder                             = ChunkBuilder.make[A](arr.value.length)
        var error: Either[SchemaError, Nothing] = null
        arr.value.foreach { v =>
          if (error == null) {
            dec.decode(v) match {
              case Right(r) => builder += r
              case Left(e)  => error = Left(e)
            }
          }
        }
        if (error != null) error.asInstanceOf[Either[SchemaError, Chunk[A]]]
        else Right(builder.result())
      case Some(json) => JsonDecoder.listDecoder(dec).decode(json).map(l => Chunk.from(l))
    }

  private def chunkMapFieldDec[V](jObj: Json.Object, name: String)(implicit
    dec: JsonDecoder[V]
  ): Either[SchemaError, ChunkMap[String, V]] =
    getField(jObj, name) match {
      case None                   => Right(ChunkMap.empty)
      case Some(obj: Json.Object) =>
        val builder                             = ChunkMap.newBuilder[String, V]
        var error: Either[SchemaError, Nothing] = null
        obj.value.foreach { case (k, v) =>
          if (error == null) {
            dec.decode(v) match {
              case Right(r) => builder += (k -> r)
              case Left(e)  => error = Left(e)
            }
          }
        }
        if (error != null) error.asInstanceOf[Either[SchemaError, ChunkMap[String, V]]]
        else Right(builder.result())
      case Some(_) => Left(SchemaError(s"Expected Object for field: $name"))
    }

  private def boolFieldDec(jObj: Json.Object, name: String, default: Boolean = false): Either[SchemaError, Boolean] =
    getField(jObj, name) match {
      case None       => Right(default)
      case Some(json) => JsonDecoder.booleanDecoder.decode(json)
    }

  private def extractExtensions(jObj: Json.Object): ChunkMap[String, Json] = {
    val builder = ChunkMap.newBuilder[String, Json]
    jObj.value.foreach { case (k, v) => if (k.startsWith("x-")) builder += (k -> v) }
    builder.result()
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
  private def normalizeDoc(doc: Doc): Doc =
    Doc(doc.blocks.map(normalizeBlock), doc.metadata)

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

  private def normalizeListItem(item: ListItem): ListItem =
    ListItem(item.content.map(normalizeBlock), item.checked)

  private def normalizeTableRow(row: TableRow): TableRow =
    TableRow(row.cells.map(normalizeInlines))

  private def normalizeInlines(inlines: Chunk[Inline]): Chunk[Inline] =
    inlines.map(normalizeInline)

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

  // ---------------------------------------------------------------------------
  // Doc
  // ---------------------------------------------------------------------------

  implicit val docJsonEncoder: JsonEncoder[Doc] = JsonEncoder.instance[Doc] { doc =>
    Json.String(Renderer.render(doc))
  }

  implicit val docJsonDecoder: JsonDecoder[Doc] = JsonDecoder.instance[Doc] { json =>
    json match {
      case str: Json.String =>
        Parser.parse(str.value) match {
          case Right(doc) => Right(normalizeDoc(doc))
          case Left(_)    => Right(Doc(Chunk(Paragraph(Chunk(Inline.Text(str.value))))))
        }
      case _ => Left(SchemaError("Expected String for Doc"))
    }
  }

  // ---------------------------------------------------------------------------
  // Contact
  // ---------------------------------------------------------------------------

  implicit val contactJsonEncoder: JsonEncoder[Contact] = JsonEncoder.instance[Contact] { c =>
    withExtensions(
      obj(
        "name"  -> optField(c.name),
        "url"   -> optField(c.url),
        "email" -> optField(c.email)
      ),
      c.extensions
    )
  }

  implicit val contactJsonDecoder: JsonDecoder[Contact] = JsonDecoder.instance[Contact] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          name  <- optFieldDec[String](jObj, "name")
          url   <- optFieldDec[String](jObj, "url")
          email <- optFieldDec[String](jObj, "email")
        } yield Contact(name, url, email, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Contact"))
    }
  }

  // ---------------------------------------------------------------------------
  // License
  // ---------------------------------------------------------------------------

  implicit val licenseJsonEncoder: JsonEncoder[License] = JsonEncoder.instance[License] { l =>
    val identifierOpt = l.identifierOrUrl.flatMap(_.left.toOption)
    val urlOpt        = l.identifierOrUrl.flatMap(_.toOption)
    withExtensions(
      obj(
        "name"       -> field(l.name),
        "identifier" -> optField(identifierOpt),
        "url"        -> optField(urlOpt)
      ),
      l.extensions
    )
  }

  implicit val licenseJsonDecoder: JsonDecoder[License] = JsonDecoder.instance[License] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          name       <- reqField[String](jObj, "name")
          identifier <- optFieldDec[String](jObj, "identifier")
          url        <- optFieldDec[String](jObj, "url")
        } yield License(name, identifier, url, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for License"))
    }
  }

  // ---------------------------------------------------------------------------
  // ExternalDocumentation
  // ---------------------------------------------------------------------------

  implicit val externalDocumentationJsonEncoder: JsonEncoder[ExternalDocumentation] =
    JsonEncoder.instance[ExternalDocumentation] { ed =>
      withExtensions(
        obj(
          "url"         -> field(ed.url),
          "description" -> optField(ed.description)(docJsonEncoder)
        ),
        ed.extensions
      )
    }

  implicit val externalDocumentationJsonDecoder: JsonDecoder[ExternalDocumentation] =
    JsonDecoder.instance[ExternalDocumentation] { json =>
      json match {
        case jObj: Json.Object =>
          for {
            url         <- reqField[String](jObj, "url")
            description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          } yield ExternalDocumentation(url, description, extractExtensions(jObj))
        case _ => Left(SchemaError("Expected Object for ExternalDocumentation"))
      }
    }

  // ---------------------------------------------------------------------------
  // ServerVariable
  // ---------------------------------------------------------------------------

  implicit val serverVariableJsonEncoder: JsonEncoder[ServerVariable] =
    JsonEncoder.instance[ServerVariable] { sv =>
      withExtensions(
        obj(
          "default"     -> field(sv.default),
          "enum"        -> chunkField(sv.`enum`),
          "description" -> optField(sv.description)(docJsonEncoder)
        ),
        sv.extensions
      )
    }

  implicit val serverVariableJsonDecoder: JsonDecoder[ServerVariable] =
    JsonDecoder.instance[ServerVariable] { json =>
      json match {
        case jObj: Json.Object =>
          for {
            default     <- reqField[String](jObj, "default")
            enumValues  <- chunkFieldDec[String](jObj, "enum")
            description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          } yield ServerVariable(default, enumValues, description, extractExtensions(jObj))
        case _ => Left(SchemaError("Expected Object for ServerVariable"))
      }
    }

  // ---------------------------------------------------------------------------
  // Server
  // ---------------------------------------------------------------------------

  implicit val serverJsonEncoder: JsonEncoder[Server] = JsonEncoder.instance[Server] { s =>
    withExtensions(
      obj(
        "url"         -> field(s.url),
        "description" -> optField(s.description)(docJsonEncoder),
        "variables"   -> chunkMapField(s.variables)(serverVariableJsonEncoder)
      ),
      s.extensions
    )
  }

  implicit val serverJsonDecoder: JsonDecoder[Server] = JsonDecoder.instance[Server] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          url         <- reqField[String](jObj, "url")
          description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          variables   <- chunkMapFieldDec[ServerVariable](jObj, "variables")(serverVariableJsonDecoder)
        } yield Server(url, description, variables, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Server"))
    }
  }

  // ---------------------------------------------------------------------------
  // Tag
  // ---------------------------------------------------------------------------

  implicit val tagJsonEncoder: JsonEncoder[Tag] = JsonEncoder.instance[Tag] { t =>
    withExtensions(
      obj(
        "name"         -> field(t.name),
        "description"  -> optField(t.description)(docJsonEncoder),
        "externalDocs" -> optField(t.externalDocs)(externalDocumentationJsonEncoder)
      ),
      t.extensions
    )
  }

  implicit val tagJsonDecoder: JsonDecoder[Tag] = JsonDecoder.instance[Tag] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          name         <- reqField[String](jObj, "name")
          description  <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          externalDocs <- optFieldDec[ExternalDocumentation](jObj, "externalDocs")(externalDocumentationJsonDecoder)
        } yield Tag(name, description, externalDocs, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Tag"))
    }
  }

  // ---------------------------------------------------------------------------
  // Info
  // ---------------------------------------------------------------------------

  implicit val infoJsonEncoder: JsonEncoder[Info] = JsonEncoder.instance[Info] { info =>
    withExtensions(
      obj(
        "title"          -> field(info.title),
        "version"        -> field(info.version),
        "summary"        -> optField(info.summary)(docJsonEncoder),
        "description"    -> optField(info.description)(docJsonEncoder),
        "termsOfService" -> optField(info.termsOfService),
        "contact"        -> optField(info.contact)(contactJsonEncoder),
        "license"        -> optField(info.license)(licenseJsonEncoder)
      ),
      info.extensions
    )
  }

  implicit val infoJsonDecoder: JsonDecoder[Info] = JsonDecoder.instance[Info] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          title          <- reqField[String](jObj, "title")
          version        <- reqField[String](jObj, "version")
          summary        <- optFieldDec[Doc](jObj, "summary")(docJsonDecoder)
          description    <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          termsOfService <- optFieldDec[String](jObj, "termsOfService")
          contact        <- optFieldDec[Contact](jObj, "contact")(contactJsonDecoder)
          license        <- optFieldDec[License](jObj, "license")(licenseJsonDecoder)
        } yield Info(title, version, summary, description, termsOfService, contact, license, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Info"))
    }
  }

  // ---------------------------------------------------------------------------
  // XML
  // ---------------------------------------------------------------------------

  implicit val xmlJsonEncoder: JsonEncoder[XML] = JsonEncoder.instance[XML] { x =>
    obj(
      "name"      -> optField(x.name),
      "namespace" -> optField(x.namespace),
      "prefix"    -> optField(x.prefix),
      "attribute" -> boolField(x.attribute),
      "wrapped"   -> boolField(x.wrapped)
    )
  }

  implicit val xmlJsonDecoder: JsonDecoder[XML] = JsonDecoder.instance[XML] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          name      <- optFieldDec[String](jObj, "name")
          namespace <- optFieldDec[String](jObj, "namespace")
          prefix    <- optFieldDec[String](jObj, "prefix")
          attribute <- boolFieldDec(jObj, "attribute")
          wrapped   <- boolFieldDec(jObj, "wrapped")
        } yield XML(name, namespace, prefix, attribute, wrapped)
      case _ => Left(SchemaError("Expected Object for XML"))
    }
  }

  // ---------------------------------------------------------------------------
  // Discriminator
  // ---------------------------------------------------------------------------

  implicit val discriminatorJsonEncoder: JsonEncoder[Discriminator] = JsonEncoder.instance[Discriminator] { d =>
    obj(
      "propertyName" -> field(d.propertyName),
      "mapping"      -> chunkMapField(d.mapping)
    )
  }

  implicit val discriminatorJsonDecoder: JsonDecoder[Discriminator] = JsonDecoder.instance[Discriminator] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          propertyName <- reqField[String](jObj, "propertyName")
          mapping      <- chunkMapFieldDec[String](jObj, "mapping")
        } yield Discriminator(propertyName, mapping)
      case _ => Left(SchemaError("Expected Object for Discriminator"))
    }
  }

  // ---------------------------------------------------------------------------
  // Optional Boolean helpers
  // ---------------------------------------------------------------------------

  private def optBoolField(a: Option[Boolean]): Option[Json] = a.map(Json.Boolean(_))

  private def optBoolFieldDec(jObj: Json.Object, name: String): Either[SchemaError, Option[Boolean]] =
    getField(jObj, name) match {
      case None       => Right(None)
      case Some(json) => JsonDecoder.booleanDecoder.decode(json).map(Some(_))
    }

  // ---------------------------------------------------------------------------
  // ParameterLocation
  // ---------------------------------------------------------------------------

  implicit val parameterLocationJsonEncoder: JsonEncoder[ParameterLocation] =
    JsonEncoder.instance[ParameterLocation] {
      case ParameterLocation.Query  => Json.String("query")
      case ParameterLocation.Header => Json.String("header")
      case ParameterLocation.Path   => Json.String("path")
      case ParameterLocation.Cookie => Json.String("cookie")
    }

  implicit val parameterLocationJsonDecoder: JsonDecoder[ParameterLocation] =
    JsonDecoder.instance[ParameterLocation] { json =>
      json match {
        case str: Json.String =>
          str.value match {
            case "query"  => Right(ParameterLocation.Query)
            case "header" => Right(ParameterLocation.Header)
            case "path"   => Right(ParameterLocation.Path)
            case "cookie" => Right(ParameterLocation.Cookie)
            case other    => Left(SchemaError(s"Invalid parameter location: $other"))
          }
        case _ => Left(SchemaError("Expected String for ParameterLocation"))
      }
    }

  // ---------------------------------------------------------------------------
  // APIKeyLocation
  // ---------------------------------------------------------------------------

  implicit val apiKeyLocationJsonEncoder: JsonEncoder[APIKeyLocation] =
    JsonEncoder.instance[APIKeyLocation] {
      case APIKeyLocation.Query  => Json.String("query")
      case APIKeyLocation.Header => Json.String("header")
      case APIKeyLocation.Cookie => Json.String("cookie")
    }

  implicit val apiKeyLocationJsonDecoder: JsonDecoder[APIKeyLocation] =
    JsonDecoder.instance[APIKeyLocation] { json =>
      json match {
        case str: Json.String =>
          str.value match {
            case "query"  => Right(APIKeyLocation.Query)
            case "header" => Right(APIKeyLocation.Header)
            case "cookie" => Right(APIKeyLocation.Cookie)
            case other    => Left(SchemaError(s"Invalid API key location: $other"))
          }
        case _ => Left(SchemaError("Expected String for APIKeyLocation"))
      }
    }

  // ---------------------------------------------------------------------------
  // Reference
  // ---------------------------------------------------------------------------

  implicit val referenceJsonEncoder: JsonEncoder[Reference] = JsonEncoder.instance[Reference] { ref =>
    obj(
      "$ref"        -> field(ref.`$ref`),
      "summary"     -> optField(ref.summary)(docJsonEncoder),
      "description" -> optField(ref.description)(docJsonEncoder)
    )
  }

  implicit val referenceJsonDecoder: JsonDecoder[Reference] = JsonDecoder.instance[Reference] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          ref         <- reqField[String](jObj, "$ref")
          summary     <- optFieldDec[Doc](jObj, "summary")(docJsonDecoder)
          description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
        } yield Reference(ref, summary, description)
      case _ => Left(SchemaError("Expected Object for Reference"))
    }
  }

  // ---------------------------------------------------------------------------
  // ReferenceOr[A]
  // ---------------------------------------------------------------------------

  implicit def referenceOrJsonEncoder[A](implicit enc: JsonEncoder[A]): JsonEncoder[ReferenceOr[A]] =
    JsonEncoder.instance[ReferenceOr[A]] {
      case ReferenceOr.Ref(reference) => referenceJsonEncoder.encode(reference)
      case ReferenceOr.Value(value)   => enc.encode(value)
    }

  implicit def referenceOrJsonDecoder[A](implicit dec: JsonDecoder[A]): JsonDecoder[ReferenceOr[A]] =
    JsonDecoder.instance[ReferenceOr[A]] { json =>
      json match {
        case jObj: Json.Object if getField(jObj, "$ref").isDefined =>
          referenceJsonDecoder.decode(json).map(ReferenceOr.Ref(_))
        case _ => dec.decode(json).map(ReferenceOr.Value(_))
      }
    }

  // ---------------------------------------------------------------------------
  // OAuthFlow
  // ---------------------------------------------------------------------------

  implicit val oauthFlowJsonEncoder: JsonEncoder[OAuthFlow] = JsonEncoder.instance[OAuthFlow] { f =>
    withExtensions(
      obj(
        "authorizationUrl" -> optField(f.authorizationUrl),
        "tokenUrl"         -> optField(f.tokenUrl),
        "refreshUrl"       -> optField(f.refreshUrl),
        "scopes"           -> chunkMapField(f.scopes)
      ),
      f.extensions
    )
  }

  implicit val oauthFlowJsonDecoder: JsonDecoder[OAuthFlow] = JsonDecoder.instance[OAuthFlow] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          authorizationUrl <- optFieldDec[String](jObj, "authorizationUrl")
          tokenUrl         <- optFieldDec[String](jObj, "tokenUrl")
          refreshUrl       <- optFieldDec[String](jObj, "refreshUrl")
          scopes           <- chunkMapFieldDec[String](jObj, "scopes")
        } yield OAuthFlow(authorizationUrl, tokenUrl, refreshUrl, scopes, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for OAuthFlow"))
    }
  }

  // ---------------------------------------------------------------------------
  // OAuthFlows
  // ---------------------------------------------------------------------------

  implicit val oauthFlowsJsonEncoder: JsonEncoder[OAuthFlows] = JsonEncoder.instance[OAuthFlows] { fs =>
    withExtensions(
      obj(
        "implicit"          -> optField(fs.`implicit`)(oauthFlowJsonEncoder),
        "password"          -> optField(fs.password)(oauthFlowJsonEncoder),
        "clientCredentials" -> optField(fs.clientCredentials)(oauthFlowJsonEncoder),
        "authorizationCode" -> optField(fs.authorizationCode)(oauthFlowJsonEncoder)
      ),
      fs.extensions
    )
  }

  implicit val oauthFlowsJsonDecoder: JsonDecoder[OAuthFlows] = JsonDecoder.instance[OAuthFlows] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          impl      <- optFieldDec[OAuthFlow](jObj, "implicit")(oauthFlowJsonDecoder)
          password  <- optFieldDec[OAuthFlow](jObj, "password")(oauthFlowJsonDecoder)
          clientCr  <- optFieldDec[OAuthFlow](jObj, "clientCredentials")(oauthFlowJsonDecoder)
          authzCode <- optFieldDec[OAuthFlow](jObj, "authorizationCode")(oauthFlowJsonDecoder)
        } yield OAuthFlows(impl, password, clientCr, authzCode, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for OAuthFlows"))
    }
  }

  // ---------------------------------------------------------------------------
  // SecurityScheme
  // ---------------------------------------------------------------------------

  implicit val securitySchemeJsonEncoder: JsonEncoder[SecurityScheme] =
    JsonEncoder.instance[SecurityScheme] {
      case s: SecurityScheme.APIKey =>
        withExtensions(
          obj(
            "type"        -> field("apiKey"),
            "name"        -> field(s.name),
            "in"          -> field(s.in)(apiKeyLocationJsonEncoder),
            "description" -> optField(s.description)(docJsonEncoder)
          ),
          s.extensions
        )
      case s: SecurityScheme.HTTP =>
        withExtensions(
          obj(
            "type"         -> field("http"),
            "scheme"       -> field(s.scheme),
            "bearerFormat" -> optField(s.bearerFormat),
            "description"  -> optField(s.description)(docJsonEncoder)
          ),
          s.extensions
        )
      case s: SecurityScheme.OAuth2 =>
        withExtensions(
          obj(
            "type"        -> field("oauth2"),
            "flows"       -> field(s.flows)(oauthFlowsJsonEncoder),
            "description" -> optField(s.description)(docJsonEncoder)
          ),
          s.extensions
        )
      case s: SecurityScheme.OpenIdConnect =>
        withExtensions(
          obj(
            "type"             -> field("openIdConnect"),
            "openIdConnectUrl" -> field(s.openIdConnectUrl),
            "description"      -> optField(s.description)(docJsonEncoder)
          ),
          s.extensions
        )
      case s: SecurityScheme.MutualTLS =>
        withExtensions(
          obj(
            "type"        -> field("mutualTLS"),
            "description" -> optField(s.description)(docJsonEncoder)
          ),
          s.extensions
        )
    }

  implicit val securitySchemeJsonDecoder: JsonDecoder[SecurityScheme] =
    JsonDecoder.instance[SecurityScheme] { json =>
      json match {
        case jObj: Json.Object =>
          reqField[String](jObj, "type").flatMap {
            case "apiKey" =>
              for {
                name        <- reqField[String](jObj, "name")
                in          <- reqField[APIKeyLocation](jObj, "in")(apiKeyLocationJsonDecoder)
                description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
              } yield SecurityScheme.APIKey(name, in, description, extractExtensions(jObj))
            case "http" =>
              for {
                scheme       <- reqField[String](jObj, "scheme")
                bearerFormat <- optFieldDec[String](jObj, "bearerFormat")
                description  <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
              } yield SecurityScheme.HTTP(scheme, bearerFormat, description, extractExtensions(jObj))
            case "oauth2" =>
              for {
                flows       <- reqField[OAuthFlows](jObj, "flows")(oauthFlowsJsonDecoder)
                description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
              } yield SecurityScheme.OAuth2(flows, description, extractExtensions(jObj))
            case "openIdConnect" =>
              for {
                url         <- reqField[String](jObj, "openIdConnectUrl")
                description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
              } yield SecurityScheme.OpenIdConnect(url, description, extractExtensions(jObj))
            case "mutualTLS" =>
              for {
                description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
              } yield SecurityScheme.MutualTLS(description, extractExtensions(jObj))
            case other => Left(SchemaError(s"Unknown security scheme type: $other"))
          }
        case _ => Left(SchemaError("Expected Object for SecurityScheme"))
      }
    }

  // ---------------------------------------------------------------------------
  // SecurityRequirement
  // ---------------------------------------------------------------------------

  implicit val securityRequirementJsonEncoder: JsonEncoder[SecurityRequirement] =
    JsonEncoder.instance[SecurityRequirement] { sr =>
      val builder = ChunkBuilder.make[(String, Json)](sr.requirements.size)
      sr.requirements.foreach { case (name, scopes) =>
        val scopeArray = new Json.Array(scopes.map(Json.String(_): Json))
        builder += (name -> scopeArray)
      }
      new Json.Object(builder.result())
    }

  implicit val securityRequirementJsonDecoder: JsonDecoder[SecurityRequirement] =
    JsonDecoder.instance[SecurityRequirement] { json =>
      json match {
        case jObj: Json.Object =>
          val builder                             = ChunkMap.newBuilder[String, Chunk[String]]
          var error: Either[SchemaError, Nothing] = null
          jObj.value.foreach { case (k, v) =>
            if (error == null) {
              v match {
                case arr: Json.Array =>
                  val scopeBuilder = ChunkBuilder.make[String](arr.value.length)
                  arr.value.foreach {
                    case s: Json.String => scopeBuilder += s.value
                    case other          =>
                      if (error == null) error = Left(SchemaError(s"Expected String in security scopes, got: $other"))
                  }
                  if (error == null) builder += (k -> scopeBuilder.result())
                case _ =>
                  error = Left(SchemaError(s"Expected Array for security requirement scopes"))
              }
            }
          }
          if (error != null) error.asInstanceOf[Either[SchemaError, SecurityRequirement]]
          else Right(SecurityRequirement(builder.result()))
        case _ => Left(SchemaError("Expected Object for SecurityRequirement"))
      }
    }

  // ---------------------------------------------------------------------------
  // SchemaObject
  // ---------------------------------------------------------------------------

  implicit val schemaObjectJsonEncoder: JsonEncoder[SchemaObject] = JsonEncoder.instance[SchemaObject] { so =>
    so.toJson
  }

  implicit val schemaObjectJsonDecoder: JsonDecoder[SchemaObject] = JsonDecoder.instance[SchemaObject] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          disc <- optFieldDec[Discriminator](jObj, "discriminator")(discriminatorJsonDecoder)
          x    <- optFieldDec[XML](jObj, "xml")(xmlJsonDecoder)
          ed   <- optFieldDec[ExternalDocumentation](jObj, "externalDocs")(externalDocumentationJsonDecoder)
        } yield {
          val ex           = getField(jObj, "example")
          val ext          = extractExtensions(jObj)
          val schemaFields = jObj.value.filter { case (k, _) =>
            k != "discriminator" && k != "xml" && k != "externalDocs" && k != "example" && !k.startsWith("x-")
          }
          val jsonSchema = new Json.Object(schemaFields)
          SchemaObject(jsonSchema, disc, x, ed, ex, ext)
        }
      case bool: Json.Boolean => Right(SchemaObject(bool))
      case _                  => Left(SchemaError("Expected Object or Boolean for SchemaObject"))
    }
  }

  // ---------------------------------------------------------------------------
  // Example
  // ---------------------------------------------------------------------------

  implicit val exampleJsonEncoder: JsonEncoder[Example] = JsonEncoder.instance[Example] { e =>
    withExtensions(
      obj(
        "summary"       -> optField(e.summary)(docJsonEncoder),
        "description"   -> optField(e.description)(docJsonEncoder),
        "value"         -> optField(e.value)(JsonEncoder.jsonEncoder),
        "externalValue" -> optField(e.externalValue)
      ),
      e.extensions
    )
  }

  implicit val exampleJsonDecoder: JsonDecoder[Example] = JsonDecoder.instance[Example] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          summary       <- optFieldDec[Doc](jObj, "summary")(docJsonDecoder)
          description   <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          value         <- optFieldDec[Json](jObj, "value")(JsonDecoder.jsonDecoder)
          externalValue <- optFieldDec[String](jObj, "externalValue")
        } yield Example(summary, description, value, externalValue, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Example"))
    }
  }

  // ---------------------------------------------------------------------------
  // Link
  // ---------------------------------------------------------------------------

  implicit lazy val linkJsonEncoder: JsonEncoder[Link] = JsonEncoder.instance[Link] { l =>
    val operationRefOpt = l.operationRefOrId.flatMap(_.left.toOption)
    val operationIdOpt  = l.operationRefOrId.flatMap(_.toOption)
    withExtensions(
      obj(
        "operationRef" -> optField(operationRefOpt),
        "operationId"  -> optField(operationIdOpt),
        "parameters"   -> chunkMapField(l.parameters)(JsonEncoder.jsonEncoder),
        "requestBody"  -> optField(l.requestBody)(JsonEncoder.jsonEncoder),
        "description"  -> optField(l.description)(docJsonEncoder),
        "server"       -> optField(l.server)(serverJsonEncoder)
      ),
      l.extensions
    )
  }

  implicit lazy val linkJsonDecoder: JsonDecoder[Link] = JsonDecoder.instance[Link] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          operationRef <- optFieldDec[String](jObj, "operationRef")
          operationId  <- optFieldDec[String](jObj, "operationId")
          parameters   <- chunkMapFieldDec[Json](jObj, "parameters")(JsonDecoder.jsonDecoder)
          requestBody  <- optFieldDec[Json](jObj, "requestBody")(JsonDecoder.jsonDecoder)
          description  <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          server       <- optFieldDec[Server](jObj, "server")(serverJsonDecoder)
        } yield Link(operationRef, operationId, parameters, requestBody, description, server, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Link"))
    }
  }

  // ---------------------------------------------------------------------------
  // Encoding (OpenAPI Encoding, not to be confused with charset encoding)
  // ---------------------------------------------------------------------------

  implicit lazy val encodingJsonEncoder: JsonEncoder[Encoding] = JsonEncoder.instance[Encoding] { e =>
    withExtensions(
      obj(
        "contentType"   -> optField(e.contentType),
        "headers"       -> chunkMapField(e.headers)(referenceOrJsonEncoder[Header](headerJsonEncoder)),
        "style"         -> optField(e.style),
        "explode"       -> optBoolField(e.explode),
        "allowReserved" -> boolField(e.allowReserved)
      ),
      e.extensions
    )
  }

  implicit lazy val encodingJsonDecoder: JsonDecoder[Encoding] = JsonDecoder.instance[Encoding] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          contentType <- optFieldDec[String](jObj, "contentType")
          headers     <-
            chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers")(referenceOrJsonDecoder[Header](headerJsonDecoder))
          style         <- optFieldDec[String](jObj, "style")
          explode       <- optBoolFieldDec(jObj, "explode")
          allowReserved <- boolFieldDec(jObj, "allowReserved")
        } yield Encoding(contentType, headers, style, explode, allowReserved, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Encoding"))
    }
  }

  // ---------------------------------------------------------------------------
  // MediaType
  // ---------------------------------------------------------------------------

  implicit lazy val mediaTypeJsonEncoder: JsonEncoder[MediaType] = JsonEncoder.instance[MediaType] { mt =>
    withExtensions(
      obj(
        "schema"   -> optField(mt.schema)(referenceOrJsonEncoder[SchemaObject](schemaObjectJsonEncoder)),
        "example"  -> optField(mt.example)(JsonEncoder.jsonEncoder),
        "examples" -> chunkMapField(mt.examples)(referenceOrJsonEncoder[Example](exampleJsonEncoder)),
        "encoding" -> chunkMapField(mt.encoding)(encodingJsonEncoder)
      ),
      mt.extensions
    )
  }

  implicit lazy val mediaTypeJsonDecoder: JsonDecoder[MediaType] = JsonDecoder.instance[MediaType] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          schema <- optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema")(
                      referenceOrJsonDecoder[SchemaObject](schemaObjectJsonDecoder)
                    )
          example  <- optFieldDec[Json](jObj, "example")(JsonDecoder.jsonDecoder)
          examples <-
            chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")(
              referenceOrJsonDecoder[Example](exampleJsonDecoder)
            )
          encoding <- chunkMapFieldDec[Encoding](jObj, "encoding")(encodingJsonDecoder)
        } yield MediaType(schema, example, examples, encoding, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for MediaType"))
    }
  }

  // ---------------------------------------------------------------------------
  // Header
  // ---------------------------------------------------------------------------

  implicit lazy val headerJsonEncoder: JsonEncoder[Header] = JsonEncoder.instance[Header] { h =>
    withExtensions(
      obj(
        "description"     -> optField(h.description)(docJsonEncoder),
        "required"        -> boolField(h.required),
        "deprecated"      -> boolField(h.deprecated),
        "allowEmptyValue" -> boolField(h.allowEmptyValue),
        "style"           -> optField(h.style),
        "explode"         -> optBoolField(h.explode),
        "allowReserved"   -> optBoolField(h.allowReserved),
        "schema"          -> optField(h.schema)(referenceOrJsonEncoder[SchemaObject](schemaObjectJsonEncoder)),
        "example"         -> optField(h.example)(JsonEncoder.jsonEncoder),
        "examples"        -> chunkMapField(h.examples)(referenceOrJsonEncoder[Example](exampleJsonEncoder)),
        "content"         -> chunkMapField(h.content)(mediaTypeJsonEncoder)
      ),
      h.extensions
    )
  }

  implicit lazy val headerJsonDecoder: JsonDecoder[Header] = JsonDecoder.instance[Header] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          description     <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          required        <- boolFieldDec(jObj, "required")
          deprecated      <- boolFieldDec(jObj, "deprecated")
          allowEmptyValue <- boolFieldDec(jObj, "allowEmptyValue")
          style           <- optFieldDec[String](jObj, "style")
          explode         <- optBoolFieldDec(jObj, "explode")
          allowReserved   <- optBoolFieldDec(jObj, "allowReserved")
          schema          <- optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema")(
                      referenceOrJsonDecoder[SchemaObject](schemaObjectJsonDecoder)
                    )
          example  <- optFieldDec[Json](jObj, "example")(JsonDecoder.jsonDecoder)
          examples <-
            chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")(
              referenceOrJsonDecoder[Example](exampleJsonDecoder)
            )
          content <- chunkMapFieldDec[MediaType](jObj, "content")(mediaTypeJsonDecoder)
        } yield Header(
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
      case _ => Left(SchemaError("Expected Object for Header"))
    }
  }

  // ---------------------------------------------------------------------------
  // Parameter
  // ---------------------------------------------------------------------------

  implicit lazy val parameterJsonEncoder: JsonEncoder[Parameter] = JsonEncoder.instance[Parameter] { p =>
    withExtensions(
      obj(
        "name"            -> field(p.name),
        "in"              -> field(p.in)(parameterLocationJsonEncoder),
        "description"     -> optField(p.description)(docJsonEncoder),
        "required"        -> boolField(p.required),
        "deprecated"      -> boolField(p.deprecated),
        "allowEmptyValue" -> boolField(p.allowEmptyValue),
        "style"           -> optField(p.style),
        "explode"         -> optBoolField(p.explode),
        "allowReserved"   -> optBoolField(p.allowReserved),
        "schema"          -> optField(p.schema)(referenceOrJsonEncoder[SchemaObject](schemaObjectJsonEncoder)),
        "example"         -> optField(p.example)(JsonEncoder.jsonEncoder),
        "examples"        -> chunkMapField(p.examples)(referenceOrJsonEncoder[Example](exampleJsonEncoder)),
        "content"         -> chunkMapField(p.content)(mediaTypeJsonEncoder)
      ),
      p.extensions
    )
  }

  implicit lazy val parameterJsonDecoder: JsonDecoder[Parameter] = JsonDecoder.instance[Parameter] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          name            <- reqField[String](jObj, "name")
          in              <- reqField[ParameterLocation](jObj, "in")(parameterLocationJsonDecoder)
          description     <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          required        <- boolFieldDec(jObj, "required")
          deprecated      <- boolFieldDec(jObj, "deprecated")
          allowEmptyValue <- boolFieldDec(jObj, "allowEmptyValue")
          style           <- optFieldDec[String](jObj, "style")
          explode         <- optBoolFieldDec(jObj, "explode")
          allowReserved   <- optBoolFieldDec(jObj, "allowReserved")
          schema          <- optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema")(
                      referenceOrJsonDecoder[SchemaObject](schemaObjectJsonDecoder)
                    )
          example  <- optFieldDec[Json](jObj, "example")(JsonDecoder.jsonDecoder)
          examples <-
            chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")(
              referenceOrJsonDecoder[Example](exampleJsonDecoder)
            )
          content <- chunkMapFieldDec[MediaType](jObj, "content")(mediaTypeJsonDecoder)
        } yield Parameter(
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
      case _ => Left(SchemaError("Expected Object for Parameter"))
    }
  }

  // ---------------------------------------------------------------------------
  // RequestBody
  // ---------------------------------------------------------------------------

  implicit lazy val requestBodyJsonEncoder: JsonEncoder[RequestBody] = JsonEncoder.instance[RequestBody] { rb =>
    withExtensions(
      obj(
        "content"     -> chunkMapField(rb.content)(mediaTypeJsonEncoder),
        "description" -> optField(rb.description)(docJsonEncoder),
        "required"    -> boolField(rb.required)
      ),
      rb.extensions
    )
  }

  implicit lazy val requestBodyJsonDecoder: JsonDecoder[RequestBody] = JsonDecoder.instance[RequestBody] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          content     <- chunkMapFieldDec[MediaType](jObj, "content")(mediaTypeJsonDecoder)
          description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          required    <- boolFieldDec(jObj, "required")
        } yield RequestBody(content, description, required, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for RequestBody"))
    }
  }

  // ---------------------------------------------------------------------------
  // Response
  // ---------------------------------------------------------------------------

  implicit lazy val responseJsonEncoder: JsonEncoder[Response] = JsonEncoder.instance[Response] { r =>
    withExtensions(
      obj(
        "description" -> field(r.description)(docJsonEncoder),
        "headers"     -> chunkMapField(r.headers)(referenceOrJsonEncoder[Header](headerJsonEncoder)),
        "content"     -> chunkMapField(r.content)(mediaTypeJsonEncoder),
        "links"       -> chunkMapField(r.links)(referenceOrJsonEncoder[Link](linkJsonEncoder))
      ),
      r.extensions
    )
  }

  implicit lazy val responseJsonDecoder: JsonDecoder[Response] = JsonDecoder.instance[Response] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          description <- reqField[Doc](jObj, "description")(docJsonDecoder)
          headers     <-
            chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers")(referenceOrJsonDecoder[Header](headerJsonDecoder))
          content <- chunkMapFieldDec[MediaType](jObj, "content")(mediaTypeJsonDecoder)
          links   <- chunkMapFieldDec[ReferenceOr[Link]](jObj, "links")(referenceOrJsonDecoder[Link](linkJsonDecoder))
        } yield Response(description, headers, content, links, extractExtensions(jObj))
      case _ => Left(SchemaError("Expected Object for Response"))
    }
  }

  // ---------------------------------------------------------------------------
  // Responses
  // ---------------------------------------------------------------------------

  implicit lazy val responsesJsonEncoder: JsonEncoder[Responses] = JsonEncoder.instance[Responses] { r =>
    val refOrRespEnc = referenceOrJsonEncoder[Response](responseJsonEncoder)
    val builder      = ChunkBuilder.make[(String, Json)](r.responses.size + 2)
    r.responses.foreach { case (code, resp) =>
      builder += (code -> refOrRespEnc.encode(resp))
    }
    r.default.foreach { d =>
      builder += ("default" -> refOrRespEnc.encode(d))
    }
    r.extensions.foreach { case (k, v) => builder += (k -> v) }
    new Json.Object(builder.result())
  }

  implicit lazy val responsesJsonDecoder: JsonDecoder[Responses] = JsonDecoder.instance[Responses] { json =>
    json match {
      case jObj: Json.Object =>
        val refOrRespDec   = referenceOrJsonDecoder[Response](responseJsonDecoder)
        val ext            = extractExtensions(jObj)
        val defaultOpt     = getField(jObj, "default")
        val responseFields = jObj.value.filter { case (k, _) =>
          k != "default" && !k.startsWith("x-")
        }
        for {
          default <- defaultOpt match {
                       case None       => Right(None)
                       case Some(json) => refOrRespDec.decode(json).map(Some(_))
                     }
          responses <- {
            val builder                             = ChunkMap.newBuilder[String, ReferenceOr[Response]]
            var error: Either[SchemaError, Nothing] = null
            responseFields.foreach { case (k, v) =>
              if (error == null) {
                refOrRespDec.decode(v) match {
                  case Right(r) => builder += (k -> r)
                  case Left(e)  => error = Left(e)
                }
              }
            }
            if (error != null) error.asInstanceOf[Either[SchemaError, ChunkMap[String, ReferenceOr[Response]]]]
            else Right(builder.result())
          }
        } yield Responses(responses, default, ext)
      case _ => Left(SchemaError("Expected Object for Responses"))
    }
  }

  // ---------------------------------------------------------------------------
  // Callback (circular: references PathItem)
  // ---------------------------------------------------------------------------

  implicit lazy val callbackJsonEncoder: JsonEncoder[Callback] = JsonEncoder.instance[Callback] { cb =>
    val pathFields = cb.callbacks.map { case (k, v) =>
      (k, referenceOrJsonEncoder[PathItem](pathItemJsonEncoder).encode(v))
    }
    new Json.Object(Chunk.from(pathFields) ++ Chunk.from(cb.extensions))
  }

  implicit lazy val callbackJsonDecoder: JsonDecoder[Callback] = JsonDecoder.instance[Callback] { json =>
    json match {
      case jObj: Json.Object =>
        val refOrPathDec                        = referenceOrJsonDecoder[PathItem](pathItemJsonDecoder)
        val ext                                 = extractExtensions(jObj)
        val callbackFields                      = jObj.value.filter { case (k, _) => !k.startsWith("x-") }
        val builder                             = ChunkMap.newBuilder[String, ReferenceOr[PathItem]]
        var error: Either[SchemaError, Nothing] = null
        callbackFields.foreach { case (k, v) =>
          if (error == null) {
            refOrPathDec.decode(v) match {
              case Right(r) => builder += (k -> r)
              case Left(e)  => error = Left(e)
            }
          }
        }
        if (error != null) error.asInstanceOf[Either[SchemaError, Callback]]
        else Right(Callback(builder.result(), ext))
      case _ => Left(SchemaError("Expected Object for Callback"))
    }
  }

  // ---------------------------------------------------------------------------
  // Operation (circular: references Callback)
  // ---------------------------------------------------------------------------

  implicit lazy val operationJsonEncoder: JsonEncoder[Operation] = JsonEncoder.instance[Operation] { op =>
    withExtensions(
      obj(
        "responses"    -> field(op.responses)(responsesJsonEncoder),
        "tags"         -> chunkField(op.tags),
        "summary"      -> optField(op.summary)(docJsonEncoder),
        "description"  -> optField(op.description)(docJsonEncoder),
        "externalDocs" -> optField(op.externalDocs)(externalDocumentationJsonEncoder),
        "operationId"  -> optField(op.operationId),
        "parameters"   -> chunkField(op.parameters)(referenceOrJsonEncoder[Parameter](parameterJsonEncoder)),
        "requestBody"  -> optField(op.requestBody)(referenceOrJsonEncoder[RequestBody](requestBodyJsonEncoder)),
        "callbacks"    -> chunkMapField(op.callbacks)(referenceOrJsonEncoder[Callback](callbackJsonEncoder)),
        "deprecated"   -> boolField(op.deprecated),
        "security"     -> chunkField(op.security)(securityRequirementJsonEncoder),
        "servers"      -> chunkField(op.servers)(serverJsonEncoder)
      ),
      op.extensions
    )
  }

  implicit lazy val operationJsonDecoder: JsonDecoder[Operation] = JsonDecoder.instance[Operation] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          responses <- {
            getField(jObj, "responses") match {
              case None        => Right(Responses())
              case Some(rJson) => responsesJsonDecoder.decode(rJson)
            }
          }
          tags        <- chunkFieldDec[String](jObj, "tags")
          summary     <- optFieldDec[Doc](jObj, "summary")(docJsonDecoder)
          description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          extDocs     <- optFieldDec[ExternalDocumentation](jObj, "externalDocs")(externalDocumentationJsonDecoder)
          operationId <- optFieldDec[String](jObj, "operationId")
          parameters  <- chunkFieldDec[ReferenceOr[Parameter]](jObj, "parameters")(
                          referenceOrJsonDecoder[Parameter](parameterJsonDecoder)
                        )
          requestBody <- optFieldDec[ReferenceOr[RequestBody]](jObj, "requestBody")(
                           referenceOrJsonDecoder[RequestBody](requestBodyJsonDecoder)
                         )
          callbacks <-
            chunkMapFieldDec[ReferenceOr[Callback]](jObj, "callbacks")(
              referenceOrJsonDecoder[Callback](callbackJsonDecoder)
            )
          deprecated <- boolFieldDec(jObj, "deprecated")
          security   <- chunkFieldDec[SecurityRequirement](jObj, "security")(securityRequirementJsonDecoder)
          servers    <- chunkFieldDec[Server](jObj, "servers")(serverJsonDecoder)
        } yield Operation(
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
      case _ => Left(SchemaError("Expected Object for Operation"))
    }
  }

  // ---------------------------------------------------------------------------
  // PathItem (circular: references Operation)
  // ---------------------------------------------------------------------------

  implicit lazy val pathItemJsonEncoder: JsonEncoder[PathItem] = JsonEncoder.instance[PathItem] { pi =>
    withExtensions(
      obj(
        "summary"     -> optField(pi.summary)(docJsonEncoder),
        "description" -> optField(pi.description)(docJsonEncoder),
        "get"         -> optField(pi.get)(operationJsonEncoder),
        "put"         -> optField(pi.put)(operationJsonEncoder),
        "post"        -> optField(pi.post)(operationJsonEncoder),
        "delete"      -> optField(pi.delete)(operationJsonEncoder),
        "options"     -> optField(pi.options)(operationJsonEncoder),
        "head"        -> optField(pi.head)(operationJsonEncoder),
        "patch"       -> optField(pi.patch)(operationJsonEncoder),
        "trace"       -> optField(pi.trace)(operationJsonEncoder),
        "servers"     -> chunkField(pi.servers)(serverJsonEncoder),
        "parameters"  -> chunkField(pi.parameters)(referenceOrJsonEncoder[Parameter](parameterJsonEncoder))
      ),
      pi.extensions
    )
  }

  implicit lazy val pathItemJsonDecoder: JsonDecoder[PathItem] = JsonDecoder.instance[PathItem] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          summary     <- optFieldDec[Doc](jObj, "summary")(docJsonDecoder)
          description <- optFieldDec[Doc](jObj, "description")(docJsonDecoder)
          get         <- optFieldDec[Operation](jObj, "get")(operationJsonDecoder)
          put         <- optFieldDec[Operation](jObj, "put")(operationJsonDecoder)
          post        <- optFieldDec[Operation](jObj, "post")(operationJsonDecoder)
          delete      <- optFieldDec[Operation](jObj, "delete")(operationJsonDecoder)
          options     <- optFieldDec[Operation](jObj, "options")(operationJsonDecoder)
          head        <- optFieldDec[Operation](jObj, "head")(operationJsonDecoder)
          patch       <- optFieldDec[Operation](jObj, "patch")(operationJsonDecoder)
          trace       <- optFieldDec[Operation](jObj, "trace")(operationJsonDecoder)
          servers     <- chunkFieldDec[Server](jObj, "servers")(serverJsonDecoder)
          parameters  <- chunkFieldDec[ReferenceOr[Parameter]](jObj, "parameters")(
                          referenceOrJsonDecoder[Parameter](parameterJsonDecoder)
                        )
        } yield PathItem(
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
      case _ => Left(SchemaError("Expected Object for PathItem"))
    }
  }

  // ---------------------------------------------------------------------------
  // Paths
  // ---------------------------------------------------------------------------

  implicit lazy val pathsJsonEncoder: JsonEncoder[Paths] = JsonEncoder.instance[Paths] { paths =>
    val pathFields = paths.paths.map { case (k, v) => (k, pathItemJsonEncoder.encode(v)) }
    new Json.Object(Chunk.from(pathFields) ++ Chunk.from(paths.extensions))
  }

  implicit lazy val pathsJsonDecoder: JsonDecoder[Paths] = JsonDecoder.instance[Paths] { json =>
    json match {
      case jObj: Json.Object =>
        val ext                                 = extractExtensions(jObj)
        val pathFields                          = jObj.value.filter { case (k, _) => !k.startsWith("x-") }
        val builder                             = ChunkMap.newBuilder[String, PathItem]
        var error: Either[SchemaError, Nothing] = null
        pathFields.foreach { case (k, v) =>
          if (error == null) {
            pathItemJsonDecoder.decode(v) match {
              case Right(r) => builder += (k -> r)
              case Left(e)  => error = Left(e)
            }
          }
        }
        if (error != null) error.asInstanceOf[Either[SchemaError, Paths]]
        else Right(Paths(builder.result(), ext))
      case _ => Left(SchemaError("Expected Object for Paths"))
    }
  }

  // ---------------------------------------------------------------------------
  // Components
  // ---------------------------------------------------------------------------

  implicit lazy val componentsJsonEncoder: JsonEncoder[Components] = JsonEncoder.instance[Components] { c =>
    withExtensions(
      obj(
        "schemas"         -> chunkMapField(c.schemas)(referenceOrJsonEncoder[SchemaObject](schemaObjectJsonEncoder)),
        "responses"       -> chunkMapField(c.responses)(referenceOrJsonEncoder[Response](responseJsonEncoder)),
        "parameters"      -> chunkMapField(c.parameters)(referenceOrJsonEncoder[Parameter](parameterJsonEncoder)),
        "examples"        -> chunkMapField(c.examples)(referenceOrJsonEncoder[Example](exampleJsonEncoder)),
        "requestBodies"   -> chunkMapField(c.requestBodies)(referenceOrJsonEncoder[RequestBody](requestBodyJsonEncoder)),
        "headers"         -> chunkMapField(c.headers)(referenceOrJsonEncoder[Header](headerJsonEncoder)),
        "securitySchemes" -> chunkMapField(c.securitySchemes)(
          referenceOrJsonEncoder[SecurityScheme](securitySchemeJsonEncoder)
        ),
        "links"     -> chunkMapField(c.links)(referenceOrJsonEncoder[Link](linkJsonEncoder)),
        "callbacks" -> chunkMapField(c.callbacks)(referenceOrJsonEncoder[Callback](callbackJsonEncoder)),
        "pathItems" -> chunkMapField(c.pathItems)(referenceOrJsonEncoder[PathItem](pathItemJsonEncoder))
      ),
      c.extensions
    )
  }

  implicit lazy val componentsJsonDecoder: JsonDecoder[Components] = JsonDecoder.instance[Components] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          schemas <- chunkMapFieldDec[ReferenceOr[SchemaObject]](jObj, "schemas")(
                       referenceOrJsonDecoder[SchemaObject](schemaObjectJsonDecoder)
                     )
          responses <-
            chunkMapFieldDec[ReferenceOr[Response]](jObj, "responses")(
              referenceOrJsonDecoder[Response](responseJsonDecoder)
            )
          parameters <- chunkMapFieldDec[ReferenceOr[Parameter]](jObj, "parameters")(
                          referenceOrJsonDecoder[Parameter](parameterJsonDecoder)
                        )
          examples <-
            chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples")(
              referenceOrJsonDecoder[Example](exampleJsonDecoder)
            )
          requestBodies <- chunkMapFieldDec[ReferenceOr[RequestBody]](jObj, "requestBodies")(
                             referenceOrJsonDecoder[RequestBody](requestBodyJsonDecoder)
                           )
          headers <-
            chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers")(referenceOrJsonDecoder[Header](headerJsonDecoder))
          securitySchemes <- chunkMapFieldDec[ReferenceOr[SecurityScheme]](jObj, "securitySchemes")(
                               referenceOrJsonDecoder[SecurityScheme](securitySchemeJsonDecoder)
                             )
          links     <- chunkMapFieldDec[ReferenceOr[Link]](jObj, "links")(referenceOrJsonDecoder[Link](linkJsonDecoder))
          callbacks <-
            chunkMapFieldDec[ReferenceOr[Callback]](jObj, "callbacks")(
              referenceOrJsonDecoder[Callback](callbackJsonDecoder)
            )
          pathItems <-
            chunkMapFieldDec[ReferenceOr[PathItem]](jObj, "pathItems")(
              referenceOrJsonDecoder[PathItem](pathItemJsonDecoder)
            )
        } yield Components(
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
      case _ => Left(SchemaError("Expected Object for Components"))
    }
  }

  // ---------------------------------------------------------------------------
  // OpenAPI
  // ---------------------------------------------------------------------------

  implicit lazy val openAPIJsonEncoder: JsonEncoder[OpenAPI] = JsonEncoder.instance[OpenAPI] { api =>
    withExtensions(
      obj(
        "openapi"           -> field(api.openapi),
        "info"              -> field(api.info)(infoJsonEncoder),
        "jsonSchemaDialect" -> optField(api.jsonSchemaDialect),
        "servers"           -> chunkField(api.servers)(serverJsonEncoder),
        "paths"             -> optField(api.paths)(pathsJsonEncoder),
        "webhooks"          -> chunkMapField(api.webhooks)(referenceOrJsonEncoder[PathItem](pathItemJsonEncoder)),
        "components"        -> optField(api.components)(componentsJsonEncoder),
        "security"          -> chunkField(api.security)(securityRequirementJsonEncoder),
        "tags"              -> chunkField(api.tags)(tagJsonEncoder),
        "externalDocs"      -> optField(api.externalDocs)(externalDocumentationJsonEncoder)
      ),
      api.extensions
    )
  }

  implicit lazy val openAPIJsonDecoder: JsonDecoder[OpenAPI] = JsonDecoder.instance[OpenAPI] { json =>
    json match {
      case jObj: Json.Object =>
        for {
          openapi           <- reqField[String](jObj, "openapi")
          info              <- reqField[Info](jObj, "info")(infoJsonDecoder)
          jsonSchemaDialect <- optFieldDec[String](jObj, "jsonSchemaDialect")
          servers           <- chunkFieldDec[Server](jObj, "servers")(serverJsonDecoder)
          paths             <- optFieldDec[Paths](jObj, "paths")(pathsJsonDecoder)
          webhooks          <- chunkMapFieldDec[ReferenceOr[PathItem]](jObj, "webhooks")(
                        referenceOrJsonDecoder[PathItem](pathItemJsonDecoder)
                      )
          components   <- optFieldDec[Components](jObj, "components")(componentsJsonDecoder)
          security     <- chunkFieldDec[SecurityRequirement](jObj, "security")(securityRequirementJsonDecoder)
          tags         <- chunkFieldDec[Tag](jObj, "tags")(tagJsonDecoder)
          externalDocs <- optFieldDec[ExternalDocumentation](jObj, "externalDocs")(externalDocumentationJsonDecoder)
        } yield OpenAPI(
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
      case _ => Left(SchemaError("Expected Object for OpenAPI"))
    }
  }
}
