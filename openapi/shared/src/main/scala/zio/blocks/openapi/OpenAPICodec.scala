/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  Text,
  WikiLink
}
import zio.blocks.docs.{Link => MdLink}
import zio.blocks.schema.Schema
import zio.blocks.schema.json._

object OpenAPICodec {

  // ---------------------------------------------------------------------------
  // Encoder helpers
  // ---------------------------------------------------------------------------

  private[this] def obj(fields: (String, Option[Json])*): Json.Object = {
    val builder = ChunkBuilder.make[(String, Json)](fields.length)
    fields.foreach { case (k, v) => v.foreach(json => builder.addOne((k, json))) }
    new Json.Object(builder.result())
  }

  private[this] def withExtensions(base: Json.Object, extensions: ChunkMap[String, Json]): Json.Object =
    if (extensions.isEmpty) base
    else new Json.Object(base.value.appendedAll(extensions))

  private[this] def field[A](a: A)(implicit enc: JsonCodec[A]): Option[Json] = new Some(enc.encodeValue(a))

  private[this] def optField[A](a: Option[A])(implicit enc: JsonCodec[A]): Option[Json] = a.map(enc.encodeValue)

  private[this] def chunkField[A](a: Chunk[A])(implicit enc: JsonCodec[A]): Option[Json] =
    if (a.isEmpty) None
    else new Some(new Json.Array(a.map(v => enc.encodeValue(v))))

  private[this] def chunkMapField[V](a: ChunkMap[String, V])(implicit enc: JsonCodec[V]): Option[Json] =
    if (a.isEmpty) None
    else new Some(new Json.Object(a.toChunk.map(kv => (kv._1, enc.encodeValue(kv._2)))))

  private[this] def boolField(a: Boolean, default: Boolean = false): Option[Json] =
    if (a == default) None
    else new Some(Json.Boolean(a))

  // ---------------------------------------------------------------------------
  // Decoder helpers
  // ---------------------------------------------------------------------------

  private[this] def getField(jObj: Json.Object, name: String): Option[Json] =
    jObj.value.collectFirst { case kv if kv._1 == name => kv._2 }

  private[this] def reqField[A](jObj: Json.Object, name: String)(implicit dec: JsonCodec[A]): A =
    getField(jObj, name) match {
      case Some(json) => dec.decodeValue(json)
      case _          => throw new JsonCodecError(Nil, s"Missing required field: $name")
    }

  private[this] def optFieldDec[A](jObj: Json.Object, name: String)(implicit
    dec: JsonCodec[A]
  ): Option[A] =
    getField(jObj, name) match {
      case Some(Json.Null) | None => None
      case Some(json)             => new Some(dec.decodeValue(json))
    }

  private[this] def chunkFieldDec[A](jObj: Json.Object, name: String)(implicit dec: JsonCodec[A]): Chunk[A] =
    getField(jObj, name) match {
      case None                  => Chunk.empty
      case Some(arr: Json.Array) => arr.value.map(v => dec.decodeValue(v))
      case _                     => throw new JsonCodecError(Nil, s"Expected Json.Array for field: $name")
    }

  private[this] def chunkMapFieldDec[V](jObj: Json.Object, name: String)(implicit
    dec: JsonCodec[V]
  ): ChunkMap[String, V] =
    getField(jObj, name) match {
      case None                   => ChunkMap.empty
      case Some(obj: Json.Object) =>
        val builder = ChunkMap.newBuilder[String, V]
        obj.value.foreach(kv => builder.addOne((kv._1, dec.decodeValue(kv._2))))
        builder.result()
      case _ => throw new JsonCodecError(Nil, s"Expected Json.Object for field: $name")
    }

  private[this] def boolFieldDec(jObj: Json.Object, name: String, default: Boolean = false): Boolean =
    getField(jObj, name) match {
      case Some(json) => JsonCodec.booleanCodec.decodeValue(json)
      case _          => default
    }

  private[this] def extractExtensions(jObj: Json.Object): ChunkMap[String, Json] = {
    val builder = ChunkMap.newBuilder[String, Json]
    jObj.value.foreach { case (k, v) => if (k.startsWith("x-")) builder += (k -> v) }
    builder.result()
  }

  private[this] def optBoolField(a: Option[Boolean]): Option[Json] = a match {
    case Some(b) => new Some(Json.Boolean(b))
    case _       => None
  }

  private[this] def optBoolFieldDec(jObj: Json.Object, name: String): Option[Boolean] =
    getField(jObj, name) match {
      case Some(json) => new Some(JsonCodec.booleanCodec.decodeValue(json))
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
  private[this] def normalizeDoc(doc: Doc): Doc = Doc(doc.blocks.map(normalizeBlock), doc.metadata)

  private[this] def normalizeBlock(block: Block): Block = block match {
    case Paragraph(content)               => new Paragraph(normalizeInlines(content))
    case Heading(level, content)          => new Heading(level, normalizeInlines(content))
    case BlockQuote(content)              => new BlockQuote(content.map(normalizeBlock))
    case BulletList(items, tight)         => new BulletList(items.map(normalizeListItem), tight)
    case OrderedList(start, items, tight) => new OrderedList(start, items.map(normalizeListItem), tight)
    case ListItem(content, checked)       => new ListItem(content.map(normalizeBlock), checked)
    case Table(header, alignments, rows)  =>
      new Table(normalizeTableRow(header), alignments, rows.map(normalizeTableRow))
    case other => other // CodeBlock, ThematicBreak, HtmlBlock unchanged
  }

  private[this] def normalizeListItem(item: ListItem): ListItem =
    ListItem(item.content.map(normalizeBlock), item.checked)

  private[this] def normalizeTableRow(row: TableRow): TableRow = TableRow(row.cells.map(normalizeInlines))

  private[this] def normalizeInlines(inlines: Chunk[Inline]): Chunk[Inline] = inlines.map(normalizeInline)

  private[this] def normalizeInline(inline: Inline): Inline = inline match {
    // Convert top-level types to Inline.* types
    case t: Text           => new Inline.Text(t.value)
    case c: Code           => new Inline.Code(c.value)
    case e: Emphasis       => new Inline.Emphasis(normalizeInlines(e.content))
    case s: Strong         => new Inline.Strong(normalizeInlines(s.content))
    case st: Strikethrough => new Inline.Strikethrough(normalizeInlines(st.content))
    case l: MdLink         => new Inline.Link(normalizeInlines(l.text), l.url, l.title)
    case l: WikiLink       => new Inline.WikiLink(l.url, l.text)
    case i: Image          => new Inline.Image(i.alt, i.url, i.title)
    case h: HtmlInline     => new Inline.HtmlInline(h.content)
    case SoftBreak         => Inline.SoftBreak
    case HardBreak         => Inline.HardBreak
    case a: Autolink       => new Inline.Autolink(a.url, a.isEmail)
    // Inline.* types are already correct, pass through
    case it: Inline.Text           => it
    case ic: Inline.Code           => ic
    case ie: Inline.Emphasis       => new Inline.Emphasis(normalizeInlines(ie.content))
    case is: Inline.Strong         => new Inline.Strong(normalizeInlines(is.content))
    case ist: Inline.Strikethrough => new Inline.Strikethrough(normalizeInlines(ist.content))
    case il: Inline.Link           => new Inline.Link(normalizeInlines(il.text), il.url, il.title)
    case ii: Inline.Image          => ii
    case ih: Inline.HtmlInline     => ih
    case Inline.SoftBreak          => Inline.SoftBreak
    case Inline.HardBreak          => Inline.HardBreak
    case ia: Inline.Autolink       => ia
  }

  private abstract class JsonASTCodec[A] extends JsonCodec[A] {
    def decodeValue(in: JsonReader): A = ???

    def encodeValue(x: A, out: JsonWriter): Unit = ???
  }

  private[openapi] implicit val jsonCodec: JsonCodec[Json]     = Json.jsonCodec
  private[openapi] implicit val stringCodec: JsonCodec[String] = Schema[String].jsonCodec
  private[openapi] implicit val docCodec: JsonCodec[Doc]       = new JsonASTCodec[Doc] {
    override def decodeValue(json: Json): Doc = json match {
      case str: Json.String =>
        Parser.parse(str.value) match {
          case Right(doc) => normalizeDoc(doc)
          case _          => new Doc(Chunk.single(new Paragraph(Chunk.single(new Inline.Text(str.value)))))
        }
      case _ => error("Expected String for Doc")
    }

    override def encodeValue(x: Doc): Json = new Json.String(Renderer.render(x))
  }
  private[openapi] implicit val contactCodec: JsonCodec[Contact] = new JsonASTCodec[Contact] {
    override def decodeValue(json: Json): Contact = json match {
      case jObj: Json.Object =>
        new Contact(
          optFieldDec[String](jObj, "name"),
          optFieldDec[String](jObj, "url"),
          optFieldDec[String](jObj, "email"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Contact")
    }

    override def encodeValue(x: Contact): Json = withExtensions(
      obj(
        "name"  -> optField(x.name),
        "url"   -> optField(x.url),
        "email" -> optField(x.email)
      ),
      x.extensions
    )
  }
  private[openapi] implicit val licenseCodec: JsonCodec[License] = new JsonASTCodec[License] {
    override def decodeValue(json: Json): License = json match {
      case jObj: Json.Object =>
        new License(
          reqField[String](jObj, "name"),
          optFieldDec[String](jObj, "identifier"),
          optFieldDec[String](jObj, "url"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for License")
    }

    override def encodeValue(x: License): Json = withExtensions(
      obj(
        "name"       -> field(x.name),
        "identifier" -> optField(x.identifier),
        "url"        -> optField(x.url)
      ),
      x.extensions
    )
  }
  private[openapi] implicit val externalDocumentationCodec: JsonCodec[ExternalDocumentation] =
    new JsonASTCodec[ExternalDocumentation] {
      override def decodeValue(json: Json): ExternalDocumentation = json match {
        case jObj: Json.Object =>
          new ExternalDocumentation(
            reqField[String](jObj, "url"),
            optFieldDec[Doc](jObj, "description"),
            extractExtensions(jObj)
          )
        case _ => error("Expected Json.Object for ExternalDocumentation")
      }

      override def encodeValue(x: ExternalDocumentation): Json = withExtensions(
        obj(
          "url"         -> field(x.url),
          "description" -> optField(x.description)
        ),
        x.extensions
      )
    }
  private[openapi] implicit val serverVariableCodec: JsonCodec[ServerVariable] = new JsonASTCodec[ServerVariable] {
    override def decodeValue(json: Json): ServerVariable = json match {
      case jObj: Json.Object =>
        new ServerVariable(
          reqField[String](jObj, "default"),
          chunkFieldDec[String](jObj, "enum"),
          optFieldDec[Doc](jObj, "description"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for ServerVariable")
    }

    override def encodeValue(x: ServerVariable): Json = withExtensions(
      obj(
        "default"     -> field(x.default),
        "enum"        -> chunkField(x.`enum`),
        "description" -> optField(x.description)
      ),
      x.extensions
    )
  }
  private[openapi] implicit val serverCodec: JsonCodec[Server] = new JsonASTCodec[Server] {
    override def decodeValue(json: Json): Server = json match {
      case jObj: Json.Object =>
        new Server(
          reqField[String](jObj, "url"),
          optFieldDec[Doc](jObj, "description"),
          chunkMapFieldDec[ServerVariable](jObj, "variables"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Server")
    }

    override def encodeValue(x: Server): Json = withExtensions(
      obj(
        "url"         -> field(x.url),
        "description" -> optField(x.description),
        "variables"   -> chunkMapField(x.variables)
      ),
      x.extensions
    )
  }
  private[openapi] implicit val tagCodec: JsonCodec[Tag] = new JsonASTCodec[Tag] {
    override def decodeValue(json: Json): Tag = json match {
      case jObj: Json.Object =>
        new Tag(
          reqField[String](jObj, "name"),
          optFieldDec[Doc](jObj, "description"),
          optFieldDec[ExternalDocumentation](jObj, "externalDocs"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Tag")
    }

    override def encodeValue(x: Tag): Json = withExtensions(
      obj(
        "name"         -> field(x.name),
        "description"  -> optField(x.description),
        "externalDocs" -> optField(x.externalDocs)
      ),
      x.extensions
    )
  }
  private[openapi] implicit val infoCodec: JsonCodec[Info] = new JsonASTCodec[Info] { info =>
    override def decodeValue(json: Json): Info = json match {
      case jObj: Json.Object =>
        new Info(
          reqField[String](jObj, "title"),
          reqField[String](jObj, "version"),
          optFieldDec[Doc](jObj, "summary"),
          optFieldDec[Doc](jObj, "description"),
          optFieldDec[String](jObj, "termsOfService"),
          optFieldDec[Contact](jObj, "contact"),
          optFieldDec[License](jObj, "license"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Info")
    }

    override def encodeValue(info: Info): Json = withExtensions(
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
  private[openapi] implicit val xmlCodec: JsonCodec[XML] = new JsonASTCodec[XML] {
    override def decodeValue(json: Json): XML = json match {
      case jObj: Json.Object =>
        new XML(
          optFieldDec[String](jObj, "name"),
          optFieldDec[String](jObj, "namespace"),
          optFieldDec[String](jObj, "prefix"),
          boolFieldDec(jObj, "attribute"),
          boolFieldDec(jObj, "wrapped")
        )
      case _ => error("Expected Json.Object for XML")
    }

    override def encodeValue(x: XML): Json = obj(
      "name"      -> optField(x.name),
      "namespace" -> optField(x.namespace),
      "prefix"    -> optField(x.prefix),
      "attribute" -> boolField(x.attribute),
      "wrapped"   -> boolField(x.wrapped)
    )
  }
  private[openapi] implicit val discriminatorCodec: JsonCodec[Discriminator] = new JsonASTCodec[Discriminator] {
    override def decodeValue(json: Json): Discriminator = json match {
      case jObj: Json.Object =>
        new Discriminator(
          reqField[String](jObj, "propertyName"),
          chunkMapFieldDec[String](jObj, "mapping")
        )
      case _ => error("Expected Json.Object for Discriminator")
    }

    override def encodeValue(x: Discriminator): Json = obj(
      "propertyName" -> field(x.propertyName),
      "mapping"      -> chunkMapField(x.mapping)
    )
  }
  private[openapi] implicit val parameterLocationCodec: JsonCodec[ParameterLocation] =
    new JsonASTCodec[ParameterLocation] {
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

      override def encodeValue(x: ParameterLocation): Json = new Json.String(x match {
        case ParameterLocation.Query  => "query"
        case ParameterLocation.Header => "header"
        case ParameterLocation.Path   => "path"
        case ParameterLocation.Cookie => "cookie"
      })
    }
  private[openapi] implicit val apiKeyLocationCodec: JsonCodec[APIKeyLocation] = new JsonASTCodec[APIKeyLocation] {
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

    override def encodeValue(x: APIKeyLocation): Json = new Json.String(x match {
      case APIKeyLocation.Query  => "query"
      case APIKeyLocation.Header => "header"
      case APIKeyLocation.Cookie => "cookie"
    })
  }
  private[openapi] implicit val referenceCodec: JsonCodec[Reference] = new JsonASTCodec[Reference] {
    override def decodeValue(json: Json): Reference = json match {
      case jObj: Json.Object =>
        new Reference(
          reqField[String](jObj, "$ref"),
          optFieldDec[Doc](jObj, "summary"),
          optFieldDec[Doc](jObj, "description")
        )
      case _ => error("Expected Json.Object for Reference")
    }

    override def encodeValue(ref: Reference): Json = obj(
      "$ref"        -> field(ref.`$ref`),
      "summary"     -> optField(ref.summary),
      "description" -> optField(ref.description)
    )
  }
  implicit def referenceOrCodec[A](implicit codec: JsonCodec[A]): JsonCodec[ReferenceOr[A]] =
    new JsonASTCodec[ReferenceOr[A]] {
      override def decodeValue(json: Json): ReferenceOr[A] = json match {
        case jObj: Json.Object if getField(jObj, "$ref").isDefined =>
          new ReferenceOr.Ref(referenceCodec.decodeValue(json))
        case _ => new ReferenceOr.Value(codec.decodeValue(json))
      }

      override def encodeValue(x: ReferenceOr[A]): Json = x match {
        case r: ReferenceOr.Ref      => referenceCodec.encodeValue(r.reference)
        case v: ReferenceOr.Value[_] => codec.encodeValue(v.value)
      }
    }
  private[openapi] implicit val oauthFlowCodec: JsonCodec[OAuthFlow] = new JsonASTCodec[OAuthFlow] {
    override def decodeValue(json: Json): OAuthFlow = json match {
      case jObj: Json.Object =>
        new OAuthFlow(
          optFieldDec[String](jObj, "authorizationUrl"),
          optFieldDec[String](jObj, "tokenUrl"),
          optFieldDec[String](jObj, "refreshUrl"),
          chunkMapFieldDec[String](jObj, "scopes"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for OAuthFlow")
    }

    override def encodeValue(x: OAuthFlow): Json = withExtensions(
      obj(
        "authorizationUrl" -> optField(x.authorizationUrl),
        "tokenUrl"         -> optField(x.tokenUrl),
        "refreshUrl"       -> optField(x.refreshUrl),
        "scopes"           -> chunkMapField(x.scopes)
      ),
      x.extensions
    )
  }
  private[openapi] implicit val oauthFlowsCodec: JsonCodec[OAuthFlows] = new JsonASTCodec[OAuthFlows] {
    override def decodeValue(json: Json): OAuthFlows = json match {
      case jObj: Json.Object =>
        new OAuthFlows(
          optFieldDec[OAuthFlow](jObj, "implicit"),
          optFieldDec[OAuthFlow](jObj, "password"),
          optFieldDec[OAuthFlow](jObj, "clientCredentials"),
          optFieldDec[OAuthFlow](jObj, "authorizationCode"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for OAuthFlows")
    }

    override def encodeValue(x: OAuthFlows): Json = withExtensions(
      obj(
        "implicit"          -> optField(x.`implicit`),
        "password"          -> optField(x.password),
        "clientCredentials" -> optField(x.clientCredentials),
        "authorizationCode" -> optField(x.authorizationCode)
      ),
      x.extensions
    )
  }
  private[openapi] implicit val securitySchemeCodec: JsonCodec[SecurityScheme] = new JsonASTCodec[SecurityScheme] {
    override def decodeValue(json: Json): SecurityScheme = json match {
      case jObj: Json.Object =>
        reqField[String](jObj, "type") match {
          case "apiKey" =>
            new SecurityScheme.APIKey(
              reqField[String](jObj, "name"),
              reqField[APIKeyLocation](jObj, "in"),
              optFieldDec[Doc](jObj, "description"),
              extractExtensions(jObj)
            )
          case "http" =>
            new SecurityScheme.HTTP(
              reqField[String](jObj, "scheme"),
              optFieldDec[String](jObj, "bearerFormat"),
              optFieldDec[Doc](jObj, "description"),
              extractExtensions(jObj)
            )
          case "oauth2" =>
            new SecurityScheme.OAuth2(
              reqField[OAuthFlows](jObj, "flows"),
              optFieldDec[Doc](jObj, "description"),
              extractExtensions(jObj)
            )
          case "openIdConnect" =>
            new SecurityScheme.OpenIdConnect(
              reqField[String](jObj, "openIdConnectUrl"),
              optFieldDec[Doc](jObj, "description"),
              extractExtensions(jObj)
            )
          case "mutualTLS" =>
            new SecurityScheme.MutualTLS(
              optFieldDec[Doc](jObj, "description"),
              extractExtensions(jObj)
            )
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
  private[openapi] implicit val securityRequirementCodec: JsonCodec[SecurityRequirement] =
    new JsonASTCodec[SecurityRequirement] {
      override def decodeValue(json: Json): SecurityRequirement = json match {
        case jObj: Json.Object =>
          val builder = ChunkMap.newBuilder[String, Chunk[String]]
          jObj.value.foreach { case (k, v) =>
            v match {
              case arr: Json.Array =>
                val scopeBuilder = ChunkBuilder.make[String](arr.value.length)
                arr.value.foreach {
                  case s: Json.String => scopeBuilder.addOne(s.value)
                  case other          => error(s"Expected String in security scopes, got: $other")
                }
                builder.addOne((k, scopeBuilder.result()))
              case _ => error(s"Expected Array for security requirement scopes")
            }
          }
          new SecurityRequirement(builder.result())
        case _ => error("Expected Json.Object for SecurityRequirement")
      }

      override def encodeValue(x: SecurityRequirement): Json = {
        val builder = ChunkBuilder.make[(String, Json)](x.requirements.size)
        x.requirements.foreach { case (name, scopes) =>
          builder.addOne((name, new Json.Array(scopes.map(Json.String(_): Json))))
        }
        new Json.Object(builder.result())
      }
    }
  private[openapi] implicit val schemaObjectCodec: JsonCodec[SchemaObject] = new JsonASTCodec[SchemaObject] {
    override def decodeValue(json: Json): SchemaObject = json match {
      case jObj: Json.Object =>
        new SchemaObject(
          new Json.Object(jObj.value.filter { case (k, _) =>
            k != "discriminator" && k != "xml" && k != "externalDocs" && k != "example" && !k.startsWith("x-")
          }),
          optFieldDec[Discriminator](jObj, "discriminator"),
          optFieldDec[XML](jObj, "xml"),
          optFieldDec[ExternalDocumentation](jObj, "externalDocs"),
          getField(jObj, "example"),
          extractExtensions(jObj)
        )
      case bool: Json.Boolean => new SchemaObject(bool)
      case _                  => error("Expected Json.Object or Json.Boolean for SchemaObject")
    }

    override def encodeValue(x: SchemaObject): Json = x.toJson
  }
  private[openapi] implicit val exampleCodec: JsonCodec[Example] = new JsonASTCodec[Example] {
    override def decodeValue(json: Json): Example = json match {
      case jObj: Json.Object =>
        new Example(
          optFieldDec[Doc](jObj, "summary"),
          optFieldDec[Doc](jObj, "description"),
          optFieldDec[Json](jObj, "value"),
          optFieldDec[String](jObj, "externalValue"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Example")
    }

    override def encodeValue(x: Example): Json = withExtensions(
      obj(
        "summary"       -> optField(x.summary),
        "description"   -> optField(x.description),
        "value"         -> optField(x.value),
        "externalValue" -> optField(x.externalValue)
      ),
      x.extensions
    )
  }
  private[openapi] implicit lazy val linkCodec: JsonCodec[Link] = new JsonASTCodec[Link] {
    override def decodeValue(json: Json): Link = json match {
      case jObj: Json.Object =>
        new Link(
          optFieldDec[String](jObj, "operationRef"),
          optFieldDec[String](jObj, "operationId"),
          chunkMapFieldDec[Json](jObj, "parameters"),
          optFieldDec[Json](jObj, "requestBody"),
          optFieldDec[Doc](jObj, "description"),
          optFieldDec[Server](jObj, "server"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Link")
    }

    override def encodeValue(x: Link): Json = withExtensions(
      obj(
        "operationRef" -> optField(x.operationRef),
        "operationId"  -> optField(x.operationId),
        "parameters"   -> chunkMapField(x.parameters),
        "requestBody"  -> optField(x.requestBody),
        "description"  -> optField(x.description),
        "server"       -> optField(x.server)
      ),
      x.extensions
    )
  }
  private[openapi] implicit lazy val encodingCodec: JsonCodec[Encoding] = new JsonASTCodec[Encoding] {
    override def decodeValue(json: Json): Encoding = json match {
      case jObj: Json.Object =>
        new Encoding(
          optFieldDec[String](jObj, "contentType"),
          chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers"),
          optFieldDec[String](jObj, "style"),
          optBoolFieldDec(jObj, "explode"),
          boolFieldDec(jObj, "allowReserved"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Encoding")
    }

    override def encodeValue(x: Encoding): Json = withExtensions(
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
  private[openapi] implicit lazy val mediaTypeCodec: JsonCodec[MediaType] = new JsonASTCodec[MediaType] {
    override def decodeValue(json: Json): MediaType = json match {
      case jObj: Json.Object =>
        new MediaType(
          optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema"),
          optFieldDec[Json](jObj, "example"),
          chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples"),
          chunkMapFieldDec[Encoding](jObj, "encoding"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for MediaType")
    }

    override def encodeValue(x: MediaType): Json = withExtensions(
      obj(
        "schema"   -> optField(x.schema),
        "example"  -> optField(x.example),
        "examples" -> chunkMapField(x.examples),
        "encoding" -> chunkMapField(x.encoding)
      ),
      x.extensions
    )
  }
  private[openapi] implicit lazy val headerCodec: JsonCodec[Header] = new JsonASTCodec[Header] {
    override def decodeValue(json: Json): Header = json match {
      case jObj: Json.Object =>
        new Header(
          optFieldDec[Doc](jObj, "description"),
          boolFieldDec(jObj, "required"),
          boolFieldDec(jObj, "deprecated"),
          boolFieldDec(jObj, "allowEmptyValue"),
          optFieldDec[String](jObj, "style"),
          optBoolFieldDec(jObj, "explode"),
          optBoolFieldDec(jObj, "allowReserved"),
          optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema"),
          optFieldDec[Json](jObj, "example"),
          chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples"),
          chunkMapFieldDec[MediaType](jObj, "content"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Header")
    }

    override def encodeValue(x: Header): Json = withExtensions(
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
  private[openapi] implicit lazy val parameterCodec: JsonCodec[Parameter] = new JsonASTCodec[Parameter] {
    override def decodeValue(json: Json): Parameter = json match {
      case jObj: Json.Object =>
        new Parameter(
          reqField[String](jObj, "name"),
          reqField[ParameterLocation](jObj, "in"),
          optFieldDec[Doc](jObj, "description"),
          boolFieldDec(jObj, "required"),
          boolFieldDec(jObj, "deprecated"),
          boolFieldDec(jObj, "allowEmptyValue"),
          optFieldDec[String](jObj, "style"),
          optBoolFieldDec(jObj, "explode"),
          optBoolFieldDec(jObj, "allowReserved"),
          optFieldDec[ReferenceOr[SchemaObject]](jObj, "schema"),
          optFieldDec[Json](jObj, "example"),
          chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples"),
          chunkMapFieldDec[MediaType](jObj, "content"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Parameter")
    }

    override def encodeValue(p: Parameter): Json = withExtensions(
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
  private[openapi] implicit lazy val requestBodyCodec: JsonCodec[RequestBody] = new JsonASTCodec[RequestBody] {
    override def decodeValue(json: Json): RequestBody = json match {
      case jObj: Json.Object =>
        new RequestBody(
          chunkMapFieldDec[MediaType](jObj, "content"),
          optFieldDec[Doc](jObj, "description"),
          boolFieldDec(jObj, "required"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for RequestBody")
    }

    override def encodeValue(rb: RequestBody): Json = withExtensions(
      obj(
        "content"     -> chunkMapField(rb.content),
        "description" -> optField(rb.description),
        "required"    -> boolField(rb.required)
      ),
      rb.extensions
    )
  }
  private[openapi] implicit lazy val responseCodec: JsonCodec[Response] = new JsonASTCodec[Response] {
    override def decodeValue(json: Json): Response = json match {
      case jObj: Json.Object =>
        new Response(
          reqField[Doc](jObj, "description"),
          chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers"),
          chunkMapFieldDec[MediaType](jObj, "content"),
          chunkMapFieldDec[ReferenceOr[Link]](jObj, "links"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Response")
    }

    override def encodeValue(x: Response): Json = withExtensions(
      obj(
        "description" -> field(x.description),
        "headers"     -> chunkMapField(x.headers),
        "content"     -> chunkMapField(x.content),
        "links"       -> chunkMapField(x.links)
      ),
      x.extensions
    )
  }
  private[openapi] implicit lazy val responsesCodec: JsonCodec[Responses] = new JsonASTCodec[Responses] {
    private[this] val refOrRespCodec = referenceOrCodec[Response](responseCodec)

    override def decodeValue(json: Json): Responses = json match {
      case jObj: Json.Object =>
        new Responses(
          jObj.value
            .foldLeft(new ChunkMap.ChunkMapBuilder[String, ReferenceOr[Response]]) { case (acc, (k, v)) =>
              if (k != "default" && !k.startsWith("x-")) acc.add(k, refOrRespCodec.decodeValue(v))
              acc
            }
            .result(),
          getField(jObj, "default") match {
            case Some(json) => new Some(refOrRespCodec.decodeValue(json))
            case _          => None
          },
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Responses")
    }

    override def encodeValue(x: Responses): Json = {
      val builder = ChunkBuilder.make[(String, Json)](x.responses.size + x.extensions.size + 2)
      x.responses.foreach { case (code, resp) => builder.addOne((code, refOrRespCodec.encodeValue(resp))) }
      x.default.foreach(d => builder.addOne(("default", refOrRespCodec.encodeValue(d))))
      x.extensions.foreach(kv => builder.addOne(kv))
      new Json.Object(builder.result())
    }
  }
  private[openapi] implicit lazy val callbackCodec: JsonCodec[Callback] = new JsonASTCodec[Callback] {
    private[this] val refOrPathCodec = referenceOrCodec[PathItem](pathItemCodec)

    override def decodeValue(json: Json): Callback = json match {
      case jObj: Json.Object =>
        new Callback(
          jObj.value
            .foldLeft(new ChunkMap.ChunkMapBuilder[String, ReferenceOr[PathItem]]) { case (acc, (k, v)) =>
              if (!k.startsWith("x-")) acc.add(k, refOrPathCodec.decodeValue(v))
              acc
            }
            .result(),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Callback")
    }

    override def encodeValue(x: Callback): Json =
      new Json.Object(
        x.callbacks
          .foldLeft(ChunkBuilder.make[(String, Json)](x.callbacks.size + x.extensions.size)) { case (acc, (k, v)) =>
            acc.addOne((k, refOrPathCodec.encodeValue(v)))
          }
          .addAll(x.extensions)
          .result()
      )
  }
  private[openapi] implicit lazy val operationCodec: JsonCodec[Operation] = new JsonASTCodec[Operation] {
    override def decodeValue(json: Json): Operation = json match {
      case jObj: Json.Object =>
        new Operation(
          getField(jObj, "responses") match {
            case Some(rJson) => responsesCodec.decodeValue(rJson)
            case _           => Responses()
          },
          chunkFieldDec[String](jObj, "tags"),
          optFieldDec[Doc](jObj, "summary"),
          optFieldDec[Doc](jObj, "description"),
          optFieldDec[ExternalDocumentation](jObj, "externalDocs"),
          optFieldDec[String](jObj, "operationId"),
          chunkFieldDec[ReferenceOr[Parameter]](jObj, "parameters"),
          optFieldDec[ReferenceOr[RequestBody]](jObj, "requestBody"),
          chunkMapFieldDec[ReferenceOr[Callback]](jObj, "callbacks"),
          boolFieldDec(jObj, "deprecated"),
          chunkFieldDec[SecurityRequirement](jObj, "security"),
          chunkFieldDec[Server](jObj, "servers"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Operation")
    }

    override def encodeValue(x: Operation): Json = withExtensions(
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
  private[openapi] implicit lazy val pathItemCodec: JsonCodec[PathItem] = new JsonASTCodec[PathItem] {
    override def decodeValue(json: Json): PathItem = json match {
      case jObj: Json.Object =>
        new PathItem(
          optFieldDec[Doc](jObj, "summary"),
          optFieldDec[Doc](jObj, "description"),
          optFieldDec[Operation](jObj, "get"),
          optFieldDec[Operation](jObj, "put"),
          optFieldDec[Operation](jObj, "post"),
          optFieldDec[Operation](jObj, "delete"),
          optFieldDec[Operation](jObj, "options"),
          optFieldDec[Operation](jObj, "head"),
          optFieldDec[Operation](jObj, "patch"),
          optFieldDec[Operation](jObj, "trace"),
          chunkFieldDec[Server](jObj, "servers"),
          chunkFieldDec[ReferenceOr[Parameter]](jObj, "parameters"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for PathItem")
    }

    override def encodeValue(x: PathItem): Json = withExtensions(
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
  private[openapi] implicit lazy val pathsCodec: JsonCodec[Paths] = new JsonASTCodec[Paths] {
    override def decodeValue(json: Json): Paths = json match {
      case jObj: Json.Object =>
        val ext   = extractExtensions(jObj)
        val paths = jObj.value
          .foldLeft(new ChunkMap.ChunkMapBuilder[String, PathItem]) { case (acc, (k, v)) =>
            if (!k.startsWith("x-")) acc.add(k, pathItemCodec.decodeValue(v))
            acc
          }
          .result()
        new Paths(paths, ext)
      case _ => error("Expected Json.Object for Paths")
    }

    override def encodeValue(x: Paths): Json = {
      val pathFields = ChunkBuilder.make[(String, Json)](x.paths.size + x.extensions.size)
      x.paths.foreach { case (k, v) => pathFields.addOne((k, pathItemCodec.encodeValue(v))) }
      new Json.Object(pathFields.addAll(x.extensions).result())
    }
  }
  private[openapi] implicit lazy val componentsCodec: JsonCodec[Components] = new JsonASTCodec[Components] {
    override def decodeValue(json: Json): Components = json match {
      case jObj: Json.Object =>
        new Components(
          chunkMapFieldDec[ReferenceOr[SchemaObject]](jObj, "schemas"),
          chunkMapFieldDec[ReferenceOr[Response]](jObj, "responses"),
          chunkMapFieldDec[ReferenceOr[Parameter]](jObj, "parameters"),
          chunkMapFieldDec[ReferenceOr[Example]](jObj, "examples"),
          chunkMapFieldDec[ReferenceOr[RequestBody]](jObj, "requestBodies"),
          chunkMapFieldDec[ReferenceOr[Header]](jObj, "headers"),
          chunkMapFieldDec[ReferenceOr[SecurityScheme]](jObj, "securitySchemes"),
          chunkMapFieldDec[ReferenceOr[Link]](jObj, "links"),
          chunkMapFieldDec[ReferenceOr[Callback]](jObj, "callbacks"),
          chunkMapFieldDec[ReferenceOr[PathItem]](jObj, "pathItems"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for Components")
    }

    override def encodeValue(x: Components): Json = withExtensions(
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
  implicit lazy val openAPICodec: JsonCodec[OpenAPI] = new JsonASTCodec[OpenAPI] {
    override def decodeValue(json: Json): OpenAPI = json match {
      case jObj: Json.Object =>
        new OpenAPI(
          reqField[String](jObj, "openapi"),
          reqField[Info](jObj, "info"),
          optFieldDec[String](jObj, "jsonSchemaDialect"),
          chunkFieldDec[Server](jObj, "servers"),
          optFieldDec[Paths](jObj, "paths"),
          chunkMapFieldDec[ReferenceOr[PathItem]](jObj, "webhooks"),
          optFieldDec[Components](jObj, "components"),
          chunkFieldDec[SecurityRequirement](jObj, "security"),
          chunkFieldDec[Tag](jObj, "tags"),
          optFieldDec[ExternalDocumentation](jObj, "externalDocs"),
          extractExtensions(jObj)
        )
      case _ => error("Expected Json.Object for OpenAPI")
    }

    override def encodeValue(x: OpenAPI): Json = withExtensions(
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
