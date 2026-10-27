---
id: generate-scala-types-from-openapi
title: "How to Generate Scala Types from an OpenAPI Spec at Build Time"
description: "Turn the component schemas of an OpenAPI document into Schema-ready Scala case classes, enums and sealed traits on every sbt build."
keywords:
  - "Code Generation"
  - "OpenAPI"
  - "Source Generator"
  - "ScalaEmitter"
  - "SchemaObject"
---

In this guide, we will write a small generator that reads an OpenAPI 3.1 document for a bookstore API and turns its `components.schemas` into Scala case classes, enums and sealed traits, with a derived `Schema` so they decode the JSON the API sends. By the end, an sbt project regenerates those types from the spec on every compile and decodes the spec's own examples into them, so the types can never drift from the spec.

We'll parse the spec with the [openapi module](../reference/openapi.md), map each schema to the [codegen module](../reference/codegen/index.md)'s description of Scala source, emit that source for Scala 3 and Scala 2, and finally run the generator as an sbt source generator. The guide covers:

- Parsing an OpenAPI document into the typed `OpenAPI` model
- Reading component schemas as typed JSON Schema values
- Mapping JSON Schema types and formats to Scala types
- Turning objects, string enums and `oneOf` with a discriminator into case classes, enums and sealed traits
- Emitting the file for Scala 3 and Scala 2, with schema derivation
- Running the generator on every build
- Reading a YAML spec, and what the generator doesn't cover

This guide follows the same pattern as [Generate Scala Code from a Smithy Model](./generate-scala-code-from-smithy.md), which explains the codegen IR step by step. If you haven't used `CaseClass`, `TypeRef` or `ScalaEmitter` before, read that tutorial first; here we focus on what is specific to OpenAPI.

## The Problem

A partner sends us `bookstore.json`, an OpenAPI 3.1 document describing their bookstore API, and we need Scala types for the JSON it returns. The usual first step is to read the spec and copy each schema into a case class by hand. That works on the day we write it, and then the spec changes: a field becomes required, a payment method is added, an enum gains a value. Nothing in our build notices. The mismatch shows up at runtime, as a decoding error in production.

For example, a hand-written copy of the order schema might look like this:

```scala mdoc:compile-only
import java.time.Instant
import java.util.UUID

// Copied by hand from bookstore.json. Keep in sync!
final case class OrderLine(bookId: String, quantity: Int, unitPrice: BigDecimal)

sealed trait Payment
final case class CardPayment(last4: String)    extends Payment
final case class GiftCardPayment(code: String) extends Payment

final case class Order(
  id: UUID,
  customerId: Long,
  placedAt: Instant,
  lines: List[OrderLine],
  payment: Payment,
  status: String,              // the spec says: one of Placed, Shipped, Delivered, Cancelled
  giftMessage: Option[String]  // optional in the spec
)
```

Every line of this is a decision someone made while reading JSON: which properties are required, what `format: int64` means in Scala, how the `method` property chooses between card and gift-card payments. Multiply it by every schema in a real API, and every release of the spec, and the copy becomes a maintenance job that nobody owns.

In this guide, we make those decisions once, in code. The generator reads the spec, applies the same rules to every schema, and writes the Scala source as part of the build. When the spec changes, the next compile regenerates the types, and every place that uses them either still compiles or points at what changed.

Nothing in ZIO Blocks connects the openapi module to the codegen module: the first parses specs, the second writes Scala source, and the mapping between them is ours to write. That mapping is the generator we build here, and it is short enough to read in one sitting:

```
bookstore.json ──openAPICodec──▶ OpenAPI ──our mapping──▶ ScalaFile ──ScalaEmitter.emit──▶ Bookstore.scala
    (text)                     (schemas)                  (Scala as data)                    (text)
```

The generator handles data types only. It writes no HTTP clients or servers; if you need those, full-featured tools such as [guardrail](https://guardrail.dev/) and [OpenAPI Generator](https://openapi-generator.tech/) generate them from the same document.

## Prerequisites

To follow along, add the openapi and codegen modules to your `build.sbt`. The code we generate also needs the schema module:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-openapi" % "@VERSION@",
  "dev.zio" %% "zio-blocks-codegen" % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema"  % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-openapi" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-codegen" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema"  % "@VERSION@"
)
scalacOptions += "-experimental"
```

The openapi and schema modules are published for the JVM and Scala.js; codegen is published for the JVM only. That is no limit for a build-time generator, which always runs on the JVM inside the build, and the code it writes depends only on the schema module. For scala-cli or cross-building, see [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md).

We also assume you know what a derived `Schema` gives a type; if not, [Your First Schema](./your-first-schema.md) explains it.

## The Bookstore Spec

Our spec describes two kinds of data: a catalog of books and the orders customers place. It is small, but it uses the features most real specs rely on: objects with required and optional properties, arrays, a map, string enums, `$ref`s between schemas, formats such as `int64`, `date-time` and `uuid`, a nullable property, and a `oneOf` with a discriminator that tells card payments from gift-card payments. The `Book` and `Order` schemas also carry an `example` keyword, a sample payload that `SchemaObject#example` exposes and that we decode at the end.

We keep the spec in a string here so every block on this page can use it; the generator in [Putting It Together](#putting-it-together) reads the same text from a `bookstore.json` file:

```scala mdoc:silent
val bookstoreJson: String =
  """{
    |  "openapi": "3.1.0",
    |  "info": { "title": "Bookstore", "version": "1.0.0" },
    |  "paths": {
    |    "/orders/{orderId}": {
    |      "get": {
    |        "operationId": "getOrder",
    |        "parameters": [
    |          { "name": "orderId", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
    |        ],
    |        "responses": {
    |          "200": {
    |            "description": "The order",
    |            "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Order" } } }
    |          }
    |        }
    |      }
    |    }
    |  },
    |  "components": {
    |    "schemas": {
    |      "Book": {
    |        "type": "object",
    |        "description": "A book in the catalog.",
    |        "required": ["id", "title", "authors", "format", "price"],
    |        "properties": {
    |          "id": { "type": "string" },
    |          "title": { "type": "string" },
    |          "authors": { "type": "array", "items": { "$ref": "#/components/schemas/Author" } },
    |          "format": { "$ref": "#/components/schemas/BookFormat" },
    |          "price": { "type": "number" },
    |          "pages": { "type": "integer", "format": "int32" },
    |          "publishedOn": { "type": "string", "format": "date" },
    |          "subtitle": { "type": ["string", "null"] },
    |          "tags": { "type": "array", "items": { "type": "string" } }
    |        },
    |        "example": {
    |          "id": "zionomicon",
    |          "title": "Zionomicon",
    |          "authors": [{ "name": "John A. De Goes" }, { "name": "Adam Fraser" }, { "name": "Milad Khajavi" }],
    |          "format": "Ebook",
    |          "price": 59.99,
    |          "tags": ["zio", "functional-programming"]
    |        }
    |      },
    |      "Author": {
    |        "type": "object",
    |        "required": ["name"],
    |        "properties": {
    |          "name": { "type": "string" },
    |          "website": { "type": "string", "format": "uri" }
    |        }
    |      },
    |      "BookFormat": {
    |        "type": "string",
    |        "description": "How the book is delivered.",
    |        "enum": ["Hardcover", "Paperback", "Ebook"]
    |      },
    |      "Order": {
    |        "type": "object",
    |        "description": "A customer's order.",
    |        "required": ["id", "customerId", "placedAt", "lines", "payment", "status"],
    |        "properties": {
    |          "id": { "type": "string", "format": "uuid" },
    |          "customerId": { "type": "integer", "format": "int64" },
    |          "placedAt": { "type": "string", "format": "date-time" },
    |          "lines": { "type": "array", "items": { "$ref": "#/components/schemas/OrderLine" } },
    |          "payment": { "$ref": "#/components/schemas/Payment" },
    |          "status": { "$ref": "#/components/schemas/OrderStatus" },
    |          "giftMessage": { "type": "string" },
    |          "metadata": { "type": "object", "additionalProperties": { "type": "string" } }
    |        },
    |        "example": {
    |          "id": "3f0c1c5e-8a59-4d55-9b8e-2b2f0b6a4c11",
    |          "customerId": 1001,
    |          "placedAt": "2026-10-27T09:30:00Z",
    |          "lines": [
    |            { "bookId": "zionomicon", "quantity": 1, "unitPrice": 59.99 },
    |            { "bookId": "programming-in-scala-5", "quantity": 2, "unitPrice": 44.95 }
    |          ],
    |          "payment": { "method": "card", "last4": "4242" },
    |          "status": "Placed",
    |          "metadata": { "channel": "web" }
    |        }
    |      },
    |      "OrderLine": {
    |        "type": "object",
    |        "required": ["bookId", "quantity", "unitPrice"],
    |        "properties": {
    |          "bookId": { "type": "string" },
    |          "quantity": { "type": "integer", "format": "int32" },
    |          "unitPrice": { "type": "number" }
    |        }
    |      },
    |      "OrderStatus": {
    |        "type": "string",
    |        "enum": ["Placed", "Shipped", "Delivered", "Cancelled"]
    |      },
    |      "Payment": {
    |        "description": "How the customer paid.",
    |        "oneOf": [
    |          { "$ref": "#/components/schemas/CardPayment" },
    |          { "$ref": "#/components/schemas/GiftCardPayment" }
    |        ],
    |        "discriminator": {
    |          "propertyName": "method",
    |          "mapping": {
    |            "card": "#/components/schemas/CardPayment",
    |            "gift_card": "#/components/schemas/GiftCardPayment"
    |          }
    |        }
    |      },
    |      "CardPayment": {
    |        "type": "object",
    |        "required": ["method", "last4"],
    |        "properties": {
    |          "method": { "type": "string" },
    |          "last4": { "type": "string" }
    |        }
    |      },
    |      "GiftCardPayment": {
    |        "type": "object",
    |        "required": ["method", "code"],
    |        "properties": {
    |          "method": { "type": "string" },
    |          "code": { "type": "string" }
    |        }
    |      }
    |    }
    |  }
    |}""".stripMargin
```

## Parse the Spec

The openapi module models a whole OpenAPI 3.1 document as plain Scala values: `OpenAPI` at the root, with `Info`, `Paths`, `Components` and the rest below it. `OpenAPICodec.openAPICodec` is the codec that reads a document into that model. It works on the `Json` tree, so we parse the text with `Json.parse` first and then decode the tree:

```scala mdoc:silent
import zio.blocks.openapi._
import zio.blocks.schema.json._

def parseSpec(text: String): OpenAPI =
  Json.parse(text).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
    case Right(api)  => api
    case Left(error) => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
  }

val api: OpenAPI = parseSpec(bookstoreJson)
```

Both steps return an `Either`, so a malformed file and a document that isn't OpenAPI fail the same way. A generator runs inside the build, so `parseSpec` turns the failure into an exception, which fails the build with the message. The parsed document gives us the API's name and the names of its component schemas:

```scala mdoc
println(s"${api.info.title} ${api.info.version}")
println(api.components.toList.flatMap(_.schemas.toList).map(_._1).mkString(", "))
```

A document that misses a required field, such as `info`, is rejected with the field's name:

```scala mdoc
println(Json.parse("""{"openapi": "3.1.0"}""").flatMap(json => OpenAPICodec.openAPICodec.decode(json)))
```

:::warning
Decode the `Json` tree, not the text. In 0.0.56, a known bug makes `OpenAPICodec.openAPICodec.decode` on a `String` return a `Left` ("an implementation is missing") and `OpenAPICodec.openAPICodec.encodeToString` throw, because the codec implements only the `Json` tree methods.
:::

## Read the Component Schemas

The types we want live in `api.components.schemas`, a map from each schema's name to a `ReferenceOr[SchemaObject]`. A component is almost always a `ReferenceOr.Value` holding the schema itself; a `ReferenceOr.Ref` would mean the component is only an alias for another one, which our generator doesn't support.

A `SchemaObject` keeps the schema in two parts. The OpenAPI-only keywords get their own fields, such as `SchemaObject#discriminator` and `SchemaObject#example`, and everything else, the JSON Schema 2020-12 part, stays in `SchemaObject#jsonSchema` as raw JSON. `SchemaObject#toJsonSchema` parses that part into the typed `JsonSchema` from the schema module, so we can read `properties`, `required`, `format`, `enum` and `oneOf` as fields instead of digging through JSON.

We collect each component's name, its typed schema and its discriminator into one small case class:

```scala mdoc:silent
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
```

`JsonSchema.Object` is the case of `JsonSchema` that holds keywords; the other two cases, `JsonSchema.True` and `JsonSchema.False`, are the boolean schemas that accept everything or nothing, and never describe a type. Printing each component shows what the generator has to work with:

```scala mdoc
components.foreach { component =>
  val schema = component.schema
  println(
    s"${component.name}: type=${schema.`type`.map(_.toJson.print).getOrElse("-")}, " +
      s"properties=${schema.properties.map(_.toList.map(_._1).mkString("[", ", ", "]")).getOrElse("-")}, " +
      s"required=${schema.required.map(_.toList.sorted.mkString("[", ", ", "]")).getOrElse("-")}"
  )
}
println(components.find(_.name == "Payment").flatMap(_.discriminator))
```

Three things in this output shape the rest of the generator. The properties come out in the order the spec lists them, so the generated fields keep that order too. `required` is a set of names, separate from `properties`, so each property's optionality is a lookup. And `Payment` has no `type` at all: it is a `oneOf`, and its discriminator names the `method` property and maps each value to a schema.

<details>
  <summary>Runnable version, with setup from the previous section: <code>Step2ComponentSchemasExample.scala</code></summary>

```scala mdoc:embed:openapi-examples/src/main/scala/generatescalatypesfromopenapi/Step2ComponentSchemasExample.scala:show-line-numbers
```

</details>

## Map Schema Types to Scala Types

Next, we decide which Scala type each property gets. A property is either a `$ref` to another component, which becomes that component's type by name, or an inline schema with a `type` and an optional `format`. `TypeRef` is codegen's description of a type, such as `TypeRef.Long` or `TypeRef.list(TypeRef("Author"))`.

Our mapping follows the formats the OpenAPI specification defines, and picks a type with a built-in `Schema` for each:

| JSON Schema                               | Scala type                     |
| ----------------------------------------- | ------------------------------ |
| `string`                                  | `String`                       |
| `string`, `format: date`                  | `java.time.LocalDate`          |
| `string`, `format: date-time`             | `java.time.Instant`            |
| `string`, `format: uuid`                  | `java.util.UUID`               |
| `integer`, `format: int32`                | `Int`                          |
| `integer`, `format: int64` or no format   | `Long`                         |
| `number`, `format: float`                 | `Float`                        |
| `number`, `format: double`                | `Double`                       |
| `number` with no format                   | `BigDecimal`                   |
| `boolean`                                 | `Boolean`                      |
| `array` with `items: A`                   | `List[A]`                      |
| `object` with only `additionalProperties` | `Map[String, A]`               |
| `$ref: '#/components/schemas/X'`          | `X`                            |
| `type: [A, "null"]` (OpenAPI 3.1)         | `Option[A]`                    |
| `nullable: true` (OpenAPI 3.0)            | `Option[A]`                    |

Every other format, such as `uri`, `email`, `byte` or `binary`, stays a `String`, which is what the JSON carries.

The two functions call each other, because an array's items and a map's values are schemas too:

```scala mdoc:silent
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

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
```

The code above handles nullability in both spec versions. OpenAPI 3.1 writes a nullable string as `type: ["string", "null"]`, which the typed schema reads as a `SchemaType.Union`. OpenAPI 3.0 writes it as `nullable: true`, a keyword JSON Schema 2020-12 doesn't know, so `JsonSchema` keeps it in `extensions` and `typeRefFor` looks it up there. Any schema the table doesn't cover, such as an inline object with its own properties, stops the generator with the schema in the message, rather than guessing.

To check the mapping, we map the properties of `Book` and `Order` and render each `TypeRef` as source text with `ScalaEmitter.emitTypeRef`:

```scala mdoc
for {
  component           <- components.filter(c => c.name == "Book" || c.name == "Order")
  (property, schema) <- component.schema.properties.toList.flatMap(_.toList)
} println(s"${component.name}.$property: ${ScalaEmitter.emitTypeRef(typeRefFor(schema))}")
```

The same function reads an OpenAPI 3.0 nullable property:

```scala mdoc
println(ScalaEmitter.emitTypeRef(typeRefFor(JsonSchema.parse("""{"type": "string", "nullable": true}""").toOption.get)))
```

:::note
`date-time` maps to `Instant`, not `OffsetDateTime`, because `Instant` always writes its seconds: in 0.0.56 the `OffsetDateTime` codec writes `2026-10-27T09:30:00+03:30` as `"2026-10-27T09:30+03:30"`, which strict RFC 3339 parsers reject. `Instant` decodes a value with any offset, but keeps only the instant, not the offset.
:::

<details>
  <summary>Runnable version, with setup from the previous sections: <code>Step3MapTypesExample.scala</code></summary>

```scala mdoc:embed:openapi-examples/src/main/scala/generatescalatypesfromopenapi/Step3MapTypesExample.scala:show-line-numbers
```

</details>

## Turn Objects, Enums and oneOf into Scala Types

With every property mapped, each component becomes one Scala type. There are three kinds in our spec, and codegen has a type definition for each: an object becomes a `CaseClass`, a string `enum` becomes an `Enum`, and a `oneOf` with a discriminator becomes a `SealedTrait`.

An object's properties become `Field`s. A required property keeps its type. Any other property may be missing from the JSON, so it becomes an `Option` with a default of `None`; when the type is already an `Option` (a nullable property), we don't wrap it twice:

```scala mdoc:silent
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
```

The schema's `description` becomes the type's Scaladoc. An enum value becomes a case name as it is, so `"Ebook"` becomes `Ebook`, and the derived JSON codec writes the case back as the string `"Ebook"`.

The `oneOf` needs more care, because its JSON has a shape of its own. The spec says a payment is one of `CardPayment` or `GiftCardPayment`, and that the `method` property tells them apart: `{"method": "card", "last4": "4242"}` is a card payment. ZIO Blocks' JSON codec writes a sealed trait as `{"CardPayment": {...}}` by default, so the generator has to annotate the generated types to match the spec's shape:

- `@Modifier.discriminator(name = "method")` on the sealed trait makes the codec write and read the case name in a `method` property.
- `@Modifier.rename(name = "card")` on a case makes the codec use the discriminator's mapping key, `card`, instead of the class name. A case with no mapping entry keeps its schema name, which is also what OpenAPI expects.
- The `method` property is left out of each case class: the codec owns that property now, and a field with the same name would clash with it.

Codegen's `Annotation` takes the annotation's name and its arguments as source text, so a string argument needs its quotes:

```scala mdoc:silent
def stringLiteral(value: String): String = "\"" + value + "\""

def sealedTraitFor(component: Component, byName: Map[String, Component]): SealedTrait = {
  val discriminator = component.discriminator.get
  val wireNames     = discriminator.mapping.toList.map { case (value, ref) => ref.stripPrefix(refPrefix) -> value }.toMap
  val cases         = component.schema.oneOf.get.toList.flatMap(refName).map { caseName =>
    val caseClass = caseClassFor(caseName, byName(caseName).schema, skip = Set(discriminator.propertyName))
    val renamed   = wireNames.get(caseName) match {
      case Some(wireName) => caseClass.copy(annotations = List(Annotation("Modifier.rename", List("name" -> stringLiteral(wireName)))))
      case None           => caseClass
    }
    SealedTraitCase.CaseClassCase(renamed)
  }
  SealedTrait(
    component.name,
    cases = cases,
    annotations = List(Annotation("Modifier.discriminator", List("name" -> stringLiteral(discriminator.propertyName)))),
    doc = component.schema.description
  )
}

val byName: Map[String, Component] = components.map(component => component.name -> component).toMap
```

To see one type of each kind as source, we emit them one at a time. `ScalaEmitter.emitTypeDefinition` renders a single type definition with an `EmitterConfig`; `EmitterConfig.default` writes Scala 3:

```scala mdoc
println(ScalaEmitter.emitTypeDefinition(caseClassFor("Book", byName("Book").schema), EmitterConfig.default))
println(ScalaEmitter.emitTypeDefinition(enumFor("BookFormat", byName("BookFormat").schema), EmitterConfig.default))
println(ScalaEmitter.emitTypeDefinition(sealedTraitFor(byName("Payment"), byName), EmitterConfig.default))
```

`Book` has its five required properties as plain fields and the other four as `Option`s, with `subtitle` wrapped once even though it is both optional and nullable. The trailing comma after the last field is Scala 3 style.

<details>
  <summary>Runnable version, with setup from the previous sections: <code>Step4TypeDefinitionsExample.scala</code></summary>

```scala mdoc:embed:openapi-examples/src/main/scala/generatescalatypesfromopenapi/Step4TypeDefinitionsExample.scala:show-line-numbers
```

</details>

## Emit the File for Scala 3 and Scala 2

A `ScalaFile` holds a package declaration, imports and type definitions, and `ScalaEmitter.emit` turns the whole file into source text. OpenAPI has no package names, so the generator takes one as a parameter.

The schemas that a `oneOf` lists are already cases of their sealed trait, so `scalaFileFor` leaves them out of the top level; every other component goes through the function for its kind:

```scala mdoc:silent
def isSealedTrait(component: Component): Boolean =
  component.schema.oneOf.isDefined && component.discriminator.isDefined

def scalaFileFor(api: OpenAPI, packageName: String): ScalaFile = {
  val components = componentsOf(api)
  val byName     = components.map(component => component.name -> component).toMap
  val caseNames  = components.filter(isSealedTrait).flatMap(_.schema.oneOf.get.toList.flatMap(refName)).toSet
  val types      = components.filterNot(component => caseNames.contains(component.name)).map {
    case component if isSealedTrait(component)                     => sealedTraitFor(component, byName)
    case Component(name, schema, _) if schema.`enum`.isDefined     => enumFor(name, schema)
    case Component(name, schema, _) if schema.properties.isDefined => caseClassFor(name, schema)
    case Component(name, schema, _) =>
      throw new IllegalArgumentException(s"$name: unsupported component schema ${schema.toJson}")
  }
  ScalaFile(PackageDecl(packageName), types = types)
}

val bookstoreFile: ScalaFile = scalaFileFor(api, "com.example.bookstore")
```

The same `ScalaFile` emits Scala 3 source with `EmitterConfig.default`:

```scala mdoc
println(ScalaEmitter.emit(bookstoreFile, EmitterConfig.default))
```

And Scala 2 source with `EmitterConfig.scala2`:

```scala mdoc
println(ScalaEmitter.emit(bookstoreFile, EmitterConfig.scala2))
```

The generator doesn't change between the two: only the config does. The Scala 2 version writes each `Enum` as a sealed trait with case objects, and drops the trailing commas.

<details>
  <summary>Runnable version, with setup from the previous sections: <code>Step5EmitBothDialectsExample.scala</code></summary>

```scala mdoc:embed:openapi-examples/src/main/scala/generatescalatypesfromopenapi/Step5EmitBothDialectsExample.scala:show-line-numbers
```

</details>

## Add Schema Derivation

The generated types compile, but they can't read JSON yet. To decode the API's payloads, each type needs a `Schema`, which gives it a JSON codec. We add the derivation as a separate step over the finished `ScalaFile`, so the mapping stays free of it and you can swap in another type class.

On Scala 3, a case class gets `derives Schema`. On Scala 2, it gets a companion object with an `implicit val schema` that calls `Schema.derived`. Both need `import zio.blocks.schema._`, which also brings in the `Modifier` annotations on `Payment`:

```scala mdoc:silent
def withSchemas(file: ScalaFile, scala3: Boolean): ScalaFile = {
  val types = file.types.map {
    case caseClass: CaseClass if scala3 =>
      caseClass.copy(derives = List("Schema"))
    case caseClass: CaseClass =>
      val schema = ObjectMember.ValMember(
        "schema",
        TypeRef.of("Schema", TypeRef(caseClass.name)),
        "Schema.derived",
        isImplicit = true
      )
      caseClass.copy(companion = Some(CompanionObject(List(schema))))
    case other => other
  }
  file.copy(imports = Import.WildcardImport("zio.blocks.schema") :: file.imports, types = types)
}
```

Only the case classes get a `Schema` of their own. Deriving the schema of `Order` also derives the schemas of the types it contains, `Payment` and `OrderStatus` included, so the codec of every top-level payload covers the whole tree. The Scala 3 version of the file is now:

```scala mdoc
println(ScalaEmitter.emit(withSchemas(bookstoreFile, scala3 = true), EmitterConfig.default))
```

On Scala 2, the same step gives `Book` a companion object:

```scala mdoc
println(ScalaEmitter.emitTypeDefinition(withSchemas(bookstoreFile, scala3 = false).types.head, EmitterConfig.scala2))
```

:::warning
Use `EmitterConfig.scala2` for the companion `implicit val`. With `EmitterConfig.default`, the codegen module in 0.0.56 writes it as `given val schema`, which Scala 3 rejects. The generator also can't put `derives` on a sealed trait or an enum yet, because the emitter drops it; that's why `withSchemas` changes the case classes only.
:::

We'll decode the spec's examples with these schemas once sbt compiles the generated file, in [Run the Generator on Every Build](#run-the-generator-on-every-build).

<details>
  <summary>Runnable version, with setup from the previous sections: <code>Step6SchemaDerivationExample.scala</code></summary>

```scala mdoc:embed:openapi-examples/src/main/scala/generatescalatypesfromopenapi/Step6SchemaDerivationExample.scala:show-line-numbers
```

</details>

## Putting It Together

So far the spec comes from a string and the output goes to the console. A real generator reads a spec file and writes a `.scala` file. `CompleteExample` combines the functions from the previous sections into one program, with two differences: it reads the spec from the path in its first argument (by default `src/main/openapi/bookstore.json`), and it writes the Scala 3 source, with schemas, to `Bookstore.scala` in the directory named by its second argument (by default `target/generated-bookstore`):

```scala mdoc:embed:openapi-examples/src/main/scala/generatescalatypesfromopenapi/CompleteExample.scala
```

Running it from the `openapi-examples` folder with `sbt "runMain generatescalatypesfromopenapi.CompleteExample"` prints:

```text
Generated Book, Author, BookFormat, Order, OrderLine, OrderStatus, Payment into target/generated-bookstore/Bookstore.scala
```

## Run the Generator on Every Build

Now we make the generator part of the build, so the types are regenerated, and recompiled, whenever the project compiles. sbt calls this a source generator: a task that writes source files before compilation and returns them, so sbt compiles them with everything else. They go in the `src_managed` folder under `target/`, so the generated code never ends up in version control.

sbt runs its build definition on Scala 2.12, and the openapi and codegen modules aren't published for Scala 2.12, so the generator can't run inside `build.sbt` itself. As in the Smithy tutorial, it stays an ordinary program in one sbt project, and a second project runs it as its source generator. Here is the part of `build.sbt` that does it, adapted from our examples project (which writes its dependencies through a small helper); the root project `openapi-examples` holds `CompleteExample` and depends on the openapi and codegen modules, and the new `bookstore-app` project compiles the result:

```scala
// The project that holds the generator, referred to by its id
val generator = LocalProject("openapi-examples")

lazy val bookstoreApp = (project in file("bookstore-app"))
  .settings(
    run / fork := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-blocks-next-schema"  % "@VERSION@",
      "dev.zio" %% "zio-blocks-next-openapi" % "@VERSION@"
    ),
    // The app reads the spec's examples at run time, so the spec is also on its classpath.
    Compile / unmanagedResourceDirectories += (generator / baseDirectory).value / "src" / "main" / "openapi",
    Compile / sourceGenerators += Def.task {
      val spec      = (generator / baseDirectory).value / "src" / "main" / "openapi" / "bookstore.json"
      val outputDir = (Compile / sourceManaged).value / "openapi"
      val classpath = (generator / Compile / fullClasspath).value.files
      (generator / Compile / runner).value
        .run(
          "generatescalatypesfromopenapi.CompleteExample",
          classpath,
          Seq(spec.getPath, outputDir.getPath),
          streams.value.log
        )
        .get
      (outputDir ** "*.scala").get
    }.taskValue
  )
```

The code above:

- `LocalProject("openapi-examples")` refers to the generator's project by its id; a direct reference to its `lazy val` would make the two project definitions depend on each other.
- `(generator / Compile / fullClasspath)` is the generator's compiled code plus the openapi and codegen jars, and `(generator / Compile / runner)` runs a main class on that classpath.
- The task passes the spec's path and `bookstore-app`'s `src_managed/main/openapi` folder to `CompleteExample`, then returns every `.scala` file the generator wrote there.
- `bookstore-app` depends on the schema module for the generated code, and on the openapi module only because this program reads the spec's examples; it never sees the generator itself.

With that in place, `bookstore-app` can use the generated types like hand-written ones. This program closes the loop: it reads the `example` of `Book` and of `Order` from the spec, decodes each into the generated type, and encodes the order back to JSON:

```scala mdoc:embed:openapi-examples/bookstore-app/src/main/scala/generatescalatypesfromopenapi/app/BookstoreApp.scala
```

Running `sbt bookstoreApp/run` from the `openapi-examples` folder generates `Bookstore.scala`, compiles it together with `BookstoreApp` and prints:

```text
Right(Book(zionomicon,Zionomicon,List(Author(John A. De Goes,None), Author(Adam Fraser,None), Author(Milad Khajavi,None)),Ebook,59.99,None,None,None,Some(List(zio, functional-programming))))
Right(Order(3f0c1c5e-8a59-4d55-9b8e-2b2f0b6a4c11,1001,2026-10-27T09:30:00Z,List(OrderLine(zionomicon,1,59.99), OrderLine(programming-in-scala-5,2,44.95)),CardPayment(4242),Placed,None,Some(Map(channel -> web))))
Right({"id":"3f0c1c5e-8a59-4d55-9b8e-2b2f0b6a4c11","customerId":1001,"placedAt":"2026-10-27T09:30:00Z","lines":[{"bookId":"zionomicon","quantity":1,"unitPrice":59.99},{"bookId":"programming-in-scala-5","quantity":2,"unitPrice":44.95}],"payment":{"method":"card","last4":"4242"},"status":"Placed","metadata":{"channel":"web"}})
```

Both examples decode. The optional properties the book example leaves out are `None`, the `uuid` and `date-time` strings are a `UUID` and an `Instant`, and `{"method": "card", ...}` is a `CardPayment`. Encoding the order gives back the example's JSON, with the payment in the spec's shape. Decoding the examples this way is a cheap contract test: if the spec's authors change a schema without updating its example, or our generator maps a type wrongly, this program fails.

Try making `giftMessage` required in `bookstore.json` and compiling again: the field loses its `Option`, and every place that builds an `Order` without it stops compiling until you update it.

## Read a YAML Spec

Many specs are written in YAML instead of JSON. The openapi module reads JSON only, but the schema-yaml module reads YAML text into a `Yaml` value, and `Yaml#toJson` converts it into the same `Json` tree that `OpenAPICodec.openAPICodec` decodes. Add `zio-blocks-schema-yaml` (`zio-blocks-next-schema-yaml` on Scala 3.9 and newer) to the generator's dependencies.

Only the parsing step changes. Here is the start of our spec in YAML:

```scala mdoc:silent
import zio.blocks.schema.yaml._

val bookstoreYaml: String =
  """openapi: 3.1.0
    |info:
    |  title: Bookstore
    |  version: 1.0.0
    |components:
    |  schemas:
    |    BookFormat:
    |      type: string
    |      description: How the book is delivered.
    |      enum:
    |        - Hardcover
    |        - Paperback
    |        - Ebook
    |    Author:
    |      type: object
    |      required:
    |        - name
    |      properties:
    |        name:
    |          type: string
    |        website:
    |          type: string
    |          format: uri
    |""".stripMargin

def parseYamlSpec(text: String): OpenAPI =
  OpenAPICodec.openAPICodec.decode(YamlReader.read(text).toJson) match {
    case Right(api)  => api
    case Left(error) => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
  }
```

The rest of the generator works on the parsed `OpenAPI` exactly as before:

```scala mdoc
println(ScalaEmitter.emit(scalaFileFor(parseYamlSpec(bookstoreYaml), "com.example.bookstore"), EmitterConfig.default))
```

:::warning
Indent the `- ` items of a list under their key, as above. In 0.0.56, a known bug in `YamlReader` reads a list written at the same indentation as its key (`required:` followed by `- name` on the next line, a style many YAML tools write) as `null`, without an error. The generator then sees, for example, no required properties and makes every field optional.
:::

<details>
  <summary>Runnable version, with the generator from the previous sections: <code>Step8YamlSpecExample.scala</code></summary>

```scala mdoc:embed:openapi-examples/src/main/scala/generatescalatypesfromopenapi/Step8YamlSpecExample.scala:show-line-numbers
```

</details>

## What the Generator Doesn't Cover

The openapi module parses much more of a spec than our generator turns into types. The parser also decodes two large public specs: GitHub's REST API description (OpenAPI 3.1, 975 component schemas) and Stripe's (OpenAPI 3.0, 1538 component schemas), and every component schema in both parses into a typed `JsonSchema`. Our generator, on the other hand, stops at the first schema it doesn't support, which in both specs is an inline object or an `anyOf`.

This table lists what each part handles, so you know where to extend the generator for your own spec:

| Spec feature                                                     | openapi module (parsing)             | Our generator                                   |
| ---------------------------------------------------------------- | ------------------------------------ | ----------------------------------------------- |
| `object` with `properties` and `required`                        | Yes                                  | Case class; optional properties are `Option`    |
| String `enum`                                                    | Yes                                  | `Enum`                                          |
| `oneOf` with a `discriminator` and `mapping`                     | Yes                                  | Sealed trait with `Modifier` annotations        |
| `oneOf` or `anyOf` without a discriminator                       | Yes                                  | Not supported: stops with an error              |
| `allOf` (composition and inheritance)                            | Yes                                  | Not supported: stops with an error              |
| Inline object as a property's schema                             | Yes                                  | Not supported: name it under `components`       |
| A component that is an array, a primitive or a `$ref`            | Yes                                  | Not supported: stops with an error              |
| `$ref` into another file (`other.json#/...`)                     | Kept as the reference text           | Not supported: only `#/components/schemas/`     |
| `type: [A, "null"]` (3.1) and `nullable: true` (3.0)             | Yes; `nullable` in `extensions`      | `Option[A]`                                     |
| Validation keywords (`minLength`, `pattern`, `minimum`, ...)     | Yes                                  | Ignored: types only                             |
| OpenAPI 3.0 boolean `exclusiveMinimum` and `exclusiveMaximum`    | Silently dropped                     | Ignored                                         |
| `default` values                                                 | Yes                                  | Ignored                                         |
| Property `description`                                           | Yes                                  | Dropped: the emitter doesn't render field docs  |
| Paths, operations, parameters and responses                      | Yes                                  | Not generated: data types only                  |
| YAML documents                                                   | Through schema-yaml, see above       | Same as JSON                                    |

Note that the model is OpenAPI 3.1, and the parser doesn't check the `openapi` version field: a 3.0 document decodes, and its schemas are read as JSON Schema 2020-12. The two differ in the keywords above, so a 3.0 spec needs the `nullable` handling from [Map Schema Types to Scala Types](#map-schema-types-to-scala-types), and its exclusive bounds are lost.

## Running the Examples

All runnable code from this guide is in [`openapi-examples/src/main/scala/generatescalatypesfromopenapi/`](https://github.com/zio/zio-blocks/tree/main/openapi-examples/src/main/scala/generatescalatypesfromopenapi), the spec is in [`openapi-examples/src/main/openapi/`](https://github.com/zio/zio-blocks/tree/main/openapi-examples/src/main/openapi), and the app that compiles the generated code is in [`openapi-examples/bookstore-app/`](https://github.com/zio/zio-blocks/tree/main/openapi-examples/bookstore-app). Run any file from the `openapi-examples` folder with `sbt "runMain generatescalatypesfromopenapi.<ObjectName>"`, and the app with `sbt bookstoreApp/run`.

## Going Further

- **Want to go the other way, from Scala types to a spec?** [How to Write an OpenAPI Document and Export It as JSON or YAML](./write-and-export-openapi.md) builds the spec for the same bookstore from `Schema`s.
- **Want to know everything the parser reads?** The [openapi module reference](../reference/openapi.md) lists every type of the `OpenAPI` model.
- **Want to generate more than data types?** The [codegen module reference](../reference/codegen/index.md) covers the whole IR, including objects, methods, opaque types and newtypes, and the [`ScalaEmitter`](../reference/codegen/scala-emitter.md) and [`EmitterConfig`](../reference/codegen/emitter-config.md) pages describe every output option.
- **Want to shape the JSON further?** [How to Customize JSON Field Names, ADT Encoding and Optional Fields](./customize-json-encoding.md) explains the `Modifier` annotations the generator writes, and the others you can add the same way, such as field renames.
- **Generating from Smithy instead?** [Generate Scala Code from a Smithy Model](./generate-scala-code-from-smithy.md) builds the same kind of generator for a Smithy model.
