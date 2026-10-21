---
id: write-and-export-openapi
title: "How to Write an OpenAPI Document and Export It as JSON or YAML"
description: "Describe a REST API as a typed OpenAPI value, generate its schemas from the same Schema used for JSON, and export it as JSON and YAML."
keywords:
  - "API Documentation"
  - "OpenAPI 3.1"
  - "Swagger UI"
  - "OpenAPI"
  - "OpenAPIGen"
  - "ReferenceOr"
---

In this guide, we will write the OpenAPI 3.1 document for a small bookstore API as a typed Scala value. By the end, you will have one program that builds the document with the `OpenAPI` data model, generates the `Book` schema from the same `Schema` that encodes books as JSON, and prints the document as JSON and as YAML, ready for Swagger UI or a client generator.

The bookstore API has two endpoints:

- `GET /books?limit=` lists books, and
- `GET /books/{id}` returns one book, or `404` when there is no such book.

Every request must carry a bearer token. We start with the schemas of the request and response bodies, then describe the endpoints and the security scheme, and finish by exporting the document in both formats.

## The Problem

An OpenAPI document repeats your data types. The `Book` that your service encodes as JSON also appears in the document as a JSON Schema, and the two are written separately. When a field is renamed or becomes optional, the case class changes and the document doesn't. Swagger UI then shows the old shape, and clients generated from the document send or expect JSON that the service no longer accepts.

A hand-written document is also just text. A misspelled keyword such as `"requried"`, a `$ref` that points to a missing component, or a path parameter without `"required": true` is not an error anywhere until a tool reads the file. Here is what such a document often looks like inside a Scala service:

```scala mdoc:compile-only
case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String])

// Kept in sync with Book by hand. Nothing checks that it still matches.
val bookstoreSpec: String =
  """{
    |  "openapi": "3.1.0",
    |  "info": { "title": "Bookstore API", "version": "1.0.0" },
    |  "paths": {
    |    "/books/{id}": {
    |      "get": {
    |        "parameters": [
    |          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
    |        ],
    |        "responses": {
    |          "200": {
    |            "description": "The book",
    |            "content": {
    |              "application/json": { "schema": { "$ref": "#/components/schemas/Book" } }
    |            }
    |          },
    |          "404": { "description": "No book has this ID" }
    |        }
    |      }
    |    }
    |  },
    |  "components": {
    |    "schemas": {
    |      "Book": {
    |        "type": "object",
    |        "properties": {
    |          "title": { "type": "string" },
    |          "firstAuthor": { "type": "string" },
    |          "year": { "type": "integer" },
    |          "isbn": { "type": ["null", "string"] }
    |        },
    |        "required": ["title", "firstAuthor", "year"]
    |      }
    |    }
    |  }
    |}""".stripMargin
```

This string compiles whatever it contains. It covers one endpoint so far, and the `Book` schema inside it is a second copy of the case class above it.

The ZIO Blocks `openapi` module replaces the string with a typed model of the OpenAPI 3.1 document, and generates the schema of each body type from its `Schema`. The compiler checks the structure of the document, and the body schemas follow the case classes automatically.

## Prerequisites

Add the `openapi` module, and the `schema-yaml` module for the YAML export, to your `build.sbt`:

```scala
// Scala 2.13, and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-openapi"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-yaml" % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-openapi"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-yaml" % "@VERSION@"
)
scalacOptions += "-experimental"
```

Both modules are built for the JVM and Scala.js; use `%%%` instead of `%%` in a Scala.js project. The `openapi` module brings in `schema`, so `Schema` and `Json` are available without another dependency.

You should know how to derive a `Schema` for a case class (see [Your First Schema](./your-first-schema.md)). The component schemas in an OpenAPI 3.1 document are JSON Schemas, and they come from the same derivation as in [How to Generate JSON Schema from a Type and Validate Payloads Against It](./generate-json-schema-and-validate-payloads.md).

:::note
You write the paths, operations and responses of the document yourself. The module does not generate a document from endpoint or route definitions; what it generates for you is the JSON Schema of each request and response body.
:::

Throughout the guide we use these imports:

```scala mdoc:silent
import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs._
import zio.blocks.openapi._
import zio.blocks.openapi.OpenAPICodec._
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, WriterConfig}
import zio.blocks.schema.yaml.Yaml
```

`zio.blocks.docs._` provides the `md"..."` interpolator for descriptions, which OpenAPI treats as Markdown. `OpenAPICodec._` provides `openAPICodec`, the codec that writes the document in the OpenAPI JSON format.

## The Bookstore Types

The API returns books, and an error body when a book is not found. Both are ordinary case classes with a derived `Schema`, the same ones the service would use to encode its JSON responses:

```scala mdoc:silent
final case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String])

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

final case class ApiError(message: String)

object ApiError {
  implicit val schema: Schema[ApiError] = Schema.derived
}

val sampleBooks: List[Book] = List(
  Book("Zionomicon", "John A. De Goes", 2024, None),
  Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601")),
  Book("Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"))
)
```

`sampleBooks` gives the document a realistic example response later on.

## Generating Component Schemas From Your Types

`Schema#toOpenAPISchema` turns a `Schema` into a `SchemaObject`, the OpenAPI type for a schema. A `SchemaObject` wraps the JSON Schema that `Schema#toJsonSchema` derives, so it describes exactly the JSON that the `Book` codec reads and writes:

```scala mdoc
println(Schema[Book].toOpenAPISchema.toJson.print(WriterConfig.withIndentionStep2))
```

The schema follows the codec's rules: `isbn` is an `Option`, so it accepts `null` and is not in `"required"`, and `year` is bounded to the `Int` range. If you add a field to `Book`, the schema changes with it.

A document usually defines each body type once, under `components/schemas`, and points to it with a `$ref` wherever it is used. `Schema#toRefSchema` returns that reference, named after the type, together with the component entry:

```scala mdoc:silent
val (bookRef, _)  = Schema[Book].toRefSchema
val (errorRef, _) = Schema[ApiError].toRefSchema
```

```scala mdoc
println(bookRef)
```

`ReferenceOr[A]` is the type for every place where OpenAPI accepts either a value of `A` or a `$ref` to one: `ReferenceOr.Ref` holds a reference and `ReferenceOr.Value` holds the value itself.

For the `components` section, `OpenAPIGen.schemas` builds the entries for several types at once, keyed by the type's simple name:

```scala mdoc:silent
val componentSchemas: Map[String, SchemaObject] = OpenAPIGen.schemas(Schema[Book], Schema[ApiError])
```

```scala mdoc
println(componentSchemas.keys)
```

:::warning
`OpenAPIGen.schemas` and `toRefSchema` name a component after the type's simple name, without its package. Two types with the same simple name in different packages get the same key, and one silently replaces the other. Use `toRefSchema("CatalogBook")` to choose a name yourself.
:::

`GET /books` returns a list of books. `Schema[List[Book]].toOpenAPISchema` would copy the whole `Book` schema into the response, so we write the small array schema ourselves and point its `items` at the `Book` component. `SchemaObject` takes any JSON Schema as a `Json` value:

```scala mdoc:silent
val bookListSchema: ReferenceOr[SchemaObject] = ReferenceOr.Value(
  SchemaObject(
    jsonSchema = Json.Object(
      "type"  -> Json.String("array"),
      "items" -> Json.Object("$ref" -> Json.String("#/components/schemas/Book"))
    )
  )
)
```

## Describing the Endpoints

An endpoint's inputs are `Parameter` values. `limit` is an optional query parameter, and `id` is part of the path. Each parameter's schema comes from a `Schema` as well; `toInlineSchema` puts the schema in place instead of referencing a component:

```scala mdoc:silent
val limitParam: Parameter = Parameter(
  name = "limit",
  in = ParameterLocation.Query,
  description = Some(md"Maximum number of books to return."),
  schema = Some(Schema[Int].toInlineSchema)
)

val idParam: Parameter = Parameter(
  name = "id",
  in = ParameterLocation.Path,
  required = true,
  description = Some(md"Catalog ID of the book."),
  schema = Some(Schema[String].toInlineSchema)
)
```

:::warning
OpenAPI requires every path parameter to be marked as required, but `Parameter` defaults `required` to `false` for every location and does not check it. Pass `required = true` for each `ParameterLocation.Path` parameter yourself.
:::

A `Response` has a Markdown description and a map from media type to `MediaType`, which holds the body's schema and an optional example. `Json.from` encodes the sample books with their `Schema`, so the example in the document is real output of the `Book` codec:

```scala mdoc:silent
val booksFound: Response = Response(
  description = md"The books in the catalog.",
  content = ChunkMap(
    "application/json" -> MediaType(schema = Some(bookListSchema), example = Some(Json.from(sampleBooks)))
  )
)

val bookFound: Response = Response(
  description = md"The book.",
  content = ChunkMap("application/json" -> MediaType(schema = Some(bookRef)))
)

val bookNotFound: Response = Response(
  description = md"No book has this ID.",
  content = ChunkMap("application/json" -> MediaType(schema = Some(errorRef)))
)
```

`Paths` maps each path to a `PathItem`, which has one optional `Operation` per HTTP method. Status codes are strings, as in the OpenAPI format. A parameter that every method of a path shares, like `id`, goes on the `PathItem` instead of on each operation:

```scala mdoc:silent
val paths: Paths = Paths(
  ChunkMap(
    "/books" -> PathItem(
      get = Some(
        Operation(
          operationId = Some("listBooks"),
          parameters = Chunk(ReferenceOr.Value(limitParam)),
          responses = Responses(ChunkMap("200" -> ReferenceOr.Value(booksFound)))
        )
      )
    ),
    "/books/{id}" -> PathItem(
      parameters = Chunk(ReferenceOr.Value(idParam)),
      get = Some(
        Operation(
          operationId = Some("getBook"),
          responses = Responses(
            ChunkMap(
              "200" -> ReferenceOr.Value(bookFound),
              "404" -> ReferenceOr.Value(bookNotFound)
            )
          )
        )
      )
    )
  )
)
```

The compiler now checks what the hand-written string could not: an `Operation` can only hold `Parameter` and `Response` values, a `ParameterLocation` is one of four cases, and `bookRef` comes from the same `Book` type that the `components` section is built from.

## Requiring a Bearer Token

A security scheme says how clients authenticate. `SecurityScheme.HTTP` with the `bearer` scheme describes an `Authorization: Bearer <token>` header; `bearerFormat` is a hint for readers and tools, here that the token is a JWT. Schemes live in `components`, next to the schemas:

```scala mdoc:silent
val components: Components = Components(
  schemas = ChunkMap.from(componentSchemas.map { case (name, schema) => name -> ReferenceOr.Value(schema) }),
  securitySchemes = ChunkMap(
    "bearerAuth" -> ReferenceOr.Value(SecurityScheme.HTTP(scheme = "bearer", bearerFormat = Some("JWT")))
  )
)
```

Defining a scheme does not apply it. A `SecurityRequirement` names the schemes a request must satisfy, each with the list of scopes it needs; bearer tokens have no scopes in OpenAPI, so the list is empty. On the `OpenAPI` value, the requirement applies to every operation, and an `Operation` can override it with its own `security` field:

```scala mdoc:silent
val requireToken: SecurityRequirement = SecurityRequirement(ChunkMap("bearerAuth" -> Chunk.empty))
```

The document only declares that a token is required. Issuing and checking tokens is the service's job; [How to Issue and Verify JWTs](./issue-and-verify-jwts.md) shows how to do it with the ZIO Blocks `jwt` module.

## Exporting the Document as JSON

The pieces now fit into one `OpenAPI` value. `openapi` is the version of the OpenAPI format, and `Info` is the API's title and version:

```scala mdoc:silent
val api: OpenAPI = OpenAPI(
  openapi = "3.1.0",
  info = Info(title = "Bookstore API", version = "1.0.0"),
  paths = Some(paths),
  components = Some(components),
  security = Chunk(requireToken)
)
```

`openAPICodec.encodeValue` turns the document into a `Json` value in the OpenAPI format, and `Json#print` renders it. `print` alone writes compact JSON on one line; `WriterConfig.withIndentionStep2` indents it for people to read:

```scala mdoc:silent
val json: Json = openAPICodec.encodeValue(api)
```

```scala mdoc
println(json.print(WriterConfig.withIndentionStep2))
```

This is the file that Swagger UI, Redoc or a client generator reads. Three details of the output come from the encoder and the printer:

- Fields you didn't set are left out, so the output contains only what the document says.
- Descriptions are Markdown, and the renderer ends each paragraph with a blank line, so every description ends with `\n\n`. Markdown readers such as Swagger UI ignore the trailing newlines.
- The empty scope list of `bearerAuth` prints with a line of spaces between its brackets. It is still the empty array `[]` to every JSON reader.

:::warning
Always encode an `OpenAPI` value through `Json`, as above. In this release, `openAPICodec.encodeToString(api)` and `openAPICodec.encode(api)` throw `NotImplementedError`, and `openAPICodec.decode` on a `String` returns a `Left`, because the codec implements only the conversion to and from `Json`.
:::

To write the document to a file, print it and save the text with the JDK:

```scala mdoc:compile-only
import java.nio.file.{Files, Path}

Files.writeString(Path.of("openapi.json"), json.print(WriterConfig.withIndentionStep2))
```

Reading a document back works the same way in reverse: `Json.parse` turns the text into `Json`, and `openAPICodec.decode` turns the `Json` into an `OpenAPI` value. Encoding the result again gives exactly the JSON we started from:

```scala mdoc:silent
val jsonText: String = json.print(WriterConfig.withIndentionStep2)

val reloaded: Either[SchemaError, OpenAPI] = Json.parse(jsonText).flatMap(openAPICodec.decode)
```

```scala mdoc
println(reloaded.map(document => openAPICodec.encodeValue(document) == json))
```

:::note
Compare documents by their JSON, as above, not with `==` on `OpenAPI` values. A description written with `md"..."` and the same description decoded from JSON hold the same text in two different classes, so `reloaded == Right(api)` is `false` even though the documents are the same.
:::

## Exporting the Document as YAML

Many teams keep their OpenAPI documents in YAML. `Yaml.fromJson` from the `schema-yaml` module converts the `Json` value into a YAML tree, and `printPretty` renders it as a YAML document:

```scala mdoc:silent
val rawYaml: String = Yaml.fromJson(json).printPretty
```

```scala mdoc
println(rawYaml)
```

Look at the `$ref` lines. The YAML writer leaves `#/components/schemas/Book` unquoted, and in YAML a `#` after a space starts a comment, so other YAML readers see `$ref:` with no value. Swagger UI and client generators would lose every reference. Until the writer quotes these values, quote them yourself after printing. Every `$ref` value in the output starts with `#`, so one replacement fixes them all:

```scala mdoc:silent
def quoteRefs(yaml: String): String =
  yaml.replaceAll("(\\$ref: )(#.*)", "$1\"$2\"")

val yamlText: String = quoteRefs(rawYaml)
```

```scala mdoc
yamlText.linesIterator.filter(_.contains("$ref")).foreach(println)
```

With the values quoted, `yamlText` is the YAML version of the JSON document above.

## Serving the Document to Swagger UI

Swagger UI is a static web page that renders an OpenAPI document as interactive documentation. It is not part of ZIO Blocks: you run it yourself, for example with the official Docker image, and give it the URL of your document. ZIO Blocks has no HTTP server either, so serve the JSON text from the server your service already uses, usually at `/openapi.json`. With the JDK's built-in server, it looks like this:

```scala mdoc:compile-only
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8

val body: Array[Byte] = json.print.getBytes(UTF_8)

val server: HttpServer = HttpServer.create(new InetSocketAddress(8080), 0)
server.createContext(
  "/openapi.json",
  exchange => {
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    // Swagger UI runs on another origin, so the browser needs permission to fetch the document.
    exchange.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    exchange.sendResponseHeaders(200, body.length.toLong)
    exchange.getResponseBody.write(body)
    exchange.close()
  }
)
server.start()
```

Then start Swagger UI and point it at the document:

```bash
docker run -p 8081:8080 -e SWAGGER_JSON_URL=http://localhost:8080/openapi.json swaggerapi/swagger-ui
```

Open `http://localhost:8081` to see both endpoints, the `Book` and `ApiError` schemas, and an **Authorize** button for the bearer token.

## What the Module Does Not Do

- **No generation from endpoints.** You describe paths, operations, parameters and responses yourself. Only the body schemas come from your types.
- **No validation of the document.** The types check the structure, but not the rules between parts: a `$ref` to a missing component, a duplicate `operationId` or a path parameter without `required = true` is accepted. Run an OpenAPI validator on the exported file in CI to catch these.
- **No direct text encoding.** As shown above, convert through `Json` to print or parse a document.

## Putting It Together

This example combines everything from the guide in one program: it generates the component schemas, describes both endpoints and the bearer-token requirement, prints the document as JSON and as YAML with quoted references, and checks that the JSON reads back to the same document:

```scala mdoc:embed:openapi-examples/src/main/scala/writeandexportopenapi/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `openapi-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/openapi-examples
```

### Generate Component Schemas

This example prints the `SchemaObject` derived from `Book`, the `$ref` that `toRefSchema` returns, and the component names from `OpenAPIGen.schemas`.

<details>
  <summary>openapi-examples/src/main/scala/writeandexportopenapi/Step1ComponentSchemasExample.scala</summary>

```scala mdoc:embed:openapi-examples/src/main/scala/writeandexportopenapi/Step1ComponentSchemasExample.scala:show-line-numbers
```

</details>

Run it from the `openapi-examples` directory:

```bash
sbt "runMain writeandexportopenapi.Step1ComponentSchemasExample"
```

### Describe the Endpoints

This example describes `GET /books` and `GET /books/{id}` with their parameters and responses, and prints the `paths` section of the document.

<details>
  <summary>openapi-examples/src/main/scala/writeandexportopenapi/Step2EndpointsExample.scala</summary>

```scala mdoc:embed:openapi-examples/src/main/scala/writeandexportopenapi/Step2EndpointsExample.scala:show-line-numbers
```

</details>

Run it from the `openapi-examples` directory:

```bash
sbt "runMain writeandexportopenapi.Step2EndpointsExample"
```

### Require a Bearer Token

This example adds the `bearerAuth` security scheme to `components`, requires it for every operation, and prints both parts of the document.

<details>
  <summary>openapi-examples/src/main/scala/writeandexportopenapi/Step3BearerTokenExample.scala</summary>

```scala mdoc:embed:openapi-examples/src/main/scala/writeandexportopenapi/Step3BearerTokenExample.scala:show-line-numbers
```

</details>

Run it from the `openapi-examples` directory:

```bash
sbt "runMain writeandexportopenapi.Step3BearerTokenExample"
```

### Export the Document as JSON

This example encodes the whole document with `openAPICodec`, prints it as indented JSON, and reads the text back to check that it gives the same document.

<details>
  <summary>openapi-examples/src/main/scala/writeandexportopenapi/Step4ExportJsonExample.scala</summary>

```scala mdoc:embed:openapi-examples/src/main/scala/writeandexportopenapi/Step4ExportJsonExample.scala:show-line-numbers
```

</details>

Run it from the `openapi-examples` directory:

```bash
sbt "runMain writeandexportopenapi.Step4ExportJsonExample"
```

### Export the Document as YAML

This example converts the document to YAML with `Yaml.fromJson`, quotes the `$ref` values, and prints the result.

<details>
  <summary>openapi-examples/src/main/scala/writeandexportopenapi/Step5ExportYamlExample.scala</summary>

```scala mdoc:embed:openapi-examples/src/main/scala/writeandexportopenapi/Step5ExportYamlExample.scala:show-line-numbers
```

</details>

Run it from the `openapi-examples` directory:

```bash
sbt "runMain writeandexportopenapi.Step5ExportYamlExample"
```

### Serve the Document to Swagger UI

This example serves the document at `http://localhost:8080/openapi.json` with the JDK's built-in HTTP server, fetches it once to show that it works, and keeps serving until you press Enter.

<details>
  <summary>openapi-examples/src/main/scala/writeandexportopenapi/Step6ServeSwaggerUiExample.scala</summary>

```scala mdoc:embed:openapi-examples/src/main/scala/writeandexportopenapi/Step6ServeSwaggerUiExample.scala:show-line-numbers
```

</details>

Run it from the `openapi-examples` directory, then start Swagger UI as shown in [Serving the Document to Swagger UI](#serving-the-document-to-swagger-ui):

```bash
sbt "runMain writeandexportopenapi.Step6ServeSwaggerUiExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything in one program:

```bash
sbt "runMain writeandexportopenapi.CompleteExample"
```

### Compile All Examples

To compile every example in the project without running any of them, run this from the `openapi-examples` directory:

```bash
sbt compile
```

## Going Further

- The [OpenAPI reference](../reference/openapi.md) covers every type of the data model, including request bodies, headers, servers, tags and the other security schemes.
- [How to Generate JSON Schema from a Type and Validate Payloads Against It](./generate-json-schema-and-validate-payloads.md) explains the JSON Schema that each component schema contains, and how to validate payloads against it.
- The [YAML format reference](../reference/schema/built-in-codecs/yaml.md) shows the `Yaml` tree and its other uses.
