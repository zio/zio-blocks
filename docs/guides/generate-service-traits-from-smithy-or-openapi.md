---
id: generate-service-traits-from-smithy-or-openapi
title: "How to Generate Service Traits from Smithy Operations or OpenAPI Paths"
description: "Map each Smithy operation or OpenAPI path to a method of a Scala service trait, with a sealed error type, using the codegen IR."
keywords:
  - "Code Generation"
  - "Service Traits"
  - "Smithy"
  - "OpenAPI"
  - "ScalaEmitter"
  - "Trait"
---

In this guide, we generate a Scala service trait from a Smithy service and from an OpenAPI document. Each operation becomes one abstract method, each set of errors becomes a sealed type, and the generator writes the result as Scala 3 source. The Smithy walkthrough comes first in full, and the OpenAPI version follows with the places where it differs.

The generators in [Generate Scala Code from a Smithy Model](./generate-scala-code-from-smithy.md) and [Generate Scala Types from an OpenAPI Spec at Build Time](./generate-scala-types-from-openapi.md) stop at data types. This guide adds the step after them: we map operations to methods, and the codegen IR writes the trait.

## The Problem

A service contract lists its operations, the input each one takes, the output it returns and the errors it can fail with. Your Scala code has to repeat that list as a trait, and the trait has to change whenever the contract does.

Written by hand, the trait for two operations of an order service looks like this:

```scala mdoc:compile-only
final case class GetOrderInput(orderId: String)
final case class GetOrderOutput(orderId: String, state: String, trackingNumber: Option[String])
final case class CancelOrderInput(orderId: String, reason: Option[String])
final case class CancelOrderOutput(orderId: String, state: String)

sealed trait OrderError
final case class OrderNotFound(orderId: String) extends OrderError
final case class OrderAlreadyShipped(orderId: String) extends OrderError

trait OrderService {
  def getOrder(input: GetOrderInput): Either[OrderError, GetOrderOutput]
  def cancelOrder(input: CancelOrderInput): Either[OrderError, CancelOrderOutput]
}
```

When the model gains an operation or an error, nothing tells you the trait is stale: the compiler accepts the old version, and the gap shows up later, when a client calls the operation that is missing. In this guide, the model is the only place the operations are written, and the trait is generated from it on every build.

## Prerequisites

Add the codegen and schema artifacts to your build. The generated code uses `derives Schema`, so the output needs the schema module as well as the generator modules:

```scala
// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-smithy"  % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-codegen" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema"  % "@VERSION@"
)
scalacOptions += "-experimental"
```

The generator is ordinary Scala that runs on the JVM at build time, so it needs no service runtime. The examples in this guide are Scala 3 only, because the generated code uses `derives`.

The base imports for the Smithy part go in the first block of the page, and every block after it shares them:

```scala mdoc:silent
import zio.blocks.smithy._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._
```

## The Service Model

A Smithy service is a shape that lists its operations. Each `operation` names an input structure, an output structure and the errors it can return, and an `@error` structure is the shape of one such error. The model below is the same file the examples read, and it is kept as a string here so every block on this page can use it:

```scala mdoc:silent
val orderServiceSmithy: String =
  """$version: "2"

namespace com.acme.orders

/// The operations a client can call on the order service.
service OrderService {
    operations: [GetOrder, CancelOrder]
}

/// Look up one order by its id.
operation GetOrder {
    input: GetOrderInput
    output: GetOrderOutput
    errors: [OrderNotFound]
}

/// Cancel an order that has not shipped yet.
operation CancelOrder {
    input: CancelOrderInput
    output: CancelOrderOutput
    errors: [OrderNotFound, OrderAlreadyShipped]
}

structure GetOrderInput {
    @required
    orderId: String
}

structure GetOrderOutput {
    @required
    orderId: String

    @required
    state: OrderState

    trackingNumber: String
}

structure CancelOrderInput {
    @required
    orderId: String

    reason: String
}

structure CancelOrderOutput {
    @required
    orderId: String

    @required
    state: OrderState
}

/// The order does not exist.
@error("client")
structure OrderNotFound {
    @required
    orderId: String
}

/// The order has shipped, so it can no longer be cancelled.
@error("client")
structure OrderAlreadyShipped {
    @required
    orderId: String
}

/// Where an order is in its life cycle, as the service reports it.
enum OrderState {
    PLACED
    SHIPPED
    CANCELLED
}"""

val model: SmithyModel = SmithyModel.parse(orderServiceSmithy) match {
  case Right(parsed) => parsed
  case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
}
```

`SmithyModel.parse` returns the whole model as a value, so the generator can look up any shape by name. The `service` shape is the entry point, and the rest of this guide walks from it to the operations, their errors and the types they use.

## Read the Operations

The service shape lists its operations by identifier, so the generator first resolves each identifier to an `OperationShape`. A `ServiceShape` holds `operations: List[ShapeId]`, and `SmithyModel#findShape` gives the shape behind a name:

```scala mdoc:silent
def service(model: SmithyModel): ServiceShape =
  model.shapes.collectFirst { case ShapeDefinition(_, s: ServiceShape) => s }.getOrElse {
    throw new IllegalArgumentException("the model has no service shape")
  }

def operation(model: SmithyModel, id: ShapeId): OperationShape =
  model.findShape(id.name).map(_.shape) match {
    case Some(op: OperationShape) => op
    case _                        => throw new IllegalArgumentException(s"Unknown operation: $id")
  }

def operations(model: SmithyModel): List[OperationShape] =
  service(model).operations.map(operation(model, _))
```

Printing each operation shows the three things the generator needs from it: its input, its output and its errors:

```scala mdoc
operations(model).foreach { op =>
  val input  = op.input.map(_.name).getOrElse("none")
  val output = op.output.map(_.name).getOrElse("none")
  val errors = op.errors.map(_.name).mkString(", ")
  println(s"${op.name}: input=$input, output=$output, errors=[$errors]")
}
```

The `OrderNotFound` error appears under both operations. That is why the errors are collected once for the whole service later, rather than per operation.

## Map Smithy Types to Scala Types

Each operation refers to its input and output by name, and those names are structures or enums in the model. We map a member's Smithy target to a `TypeRef`, the codegen description of a Scala type, and a member that is not `@required` becomes an `Option` with a `None` default:

```scala mdoc:silent
def docOf(traits: List[TraitApplication]): Option[String] =
  traits.collectFirst {
    case TraitApplication(ShapeId("smithy.api", "documentation"), Some(NodeValue.String(text))) => text
  }

def typeRefFor(model: SmithyModel, target: ShapeId): TypeRef =
  model.findShape(target.name).map(_.shape) match {
    case Some(_: StructureShape) | Some(_: EnumShape) | Some(_: UnionShape) => TypeRef(target.name)
    case Some(other) =>
      throw new IllegalArgumentException(s"Unsupported shape: ${other.name}")
    case None =>
      target.name match {
        case "String"    => TypeRef.String
        case "Integer"   => TypeRef.Int
        case "Long"      => TypeRef.Long
        case "Boolean"   => TypeRef.Boolean
        case "Timestamp" => TypeRef("java.time.Instant")
        case other       => throw new IllegalArgumentException(s"Unknown shape: $other")
      }
  }

def fieldFor(model: SmithyModel, member: MemberDefinition): Field = {
  val fieldType = typeRefFor(model, member.target)
  if (member.traits.contains(TraitApplication.required)) Field(member.name, fieldType)
  else Field(member.name, TypeRef.optional(fieldType), defaultValue = Some("None"))
}

def caseClassFor(model: SmithyModel, name: String, shape: StructureShape, extendsTypes: List[TypeRef]): CaseClass =
  CaseClass(
    name,
    shape.members.map(fieldFor(model, _)),
    extendsTypes = extendsTypes,
    derives = List("Schema"),
    doc = docOf(shape.traits)
  )

def enumFor(name: String, shape: EnumShape): Enum =
  Enum(name, shape.members.map(member => EnumCase.SimpleCase(member.name)), doc = docOf(shape.traits))
```

The `derives = List("Schema")` argument asks the emitter for a `derives Schema` clause, so each generated type gets its ZIO Blocks schema. The output of one structure shows the result:

```scala mdoc
println(
  ScalaEmitter.emit(
    ScalaFile(
      PackageDecl(model.namespace),
      imports = List(Import.WildcardImport("zio.blocks.schema")),
      types = model.shapes.collect {
        case ShapeDefinition(name, shape: StructureShape) if name == "GetOrderOutput" =>
          caseClassFor(model, name, shape, Nil)
      }
    ),
    EmitterConfig.default
  )
)
```

Optional members come out as `Option` fields with a default, and the `@documentation` text becomes the Scaladoc of the type.

## Map Each Operation to a Method

An operation becomes a method of the service trait. The method takes the operation's input structure as its single parameter, so a new member in the input never changes the signature. Its return type is the output structure, wrapped in `Either` when the operation has errors, and an operation without an output returns `Unit`:

```scala mdoc:silent
def errorTypeName(svc: ServiceShape): String = svc.name.stripSuffix("Service") + "Error"

def methodFor(model: SmithyModel, op: OperationShape): Method = {
  val errorType = errorTypeName(service(model))
  val params    = op.input.toList.map(in => ParamList(List(MethodParam("input", TypeRef(in.name)))))
  val output    = op.output.map(o => TypeRef(o.name)).getOrElse(TypeRef.Unit)
  val returnType =
    if (op.errors.isEmpty) output
    else TypeRef.of("Either", TypeRef(errorType), output)
  Method(
    name = op.name.take(1).toLowerCase + op.name.drop(1),
    params = params,
    returnType = returnType,
    doc = docOf(op.traits)
  )
}

def serviceTrait(model: SmithyModel): Trait = {
  val svc = service(model)
  Trait(
    svc.name,
    members = operations(model).map(op => ObjectMember.DefMember(methodFor(model, op))),
    doc = docOf(svc.traits)
  )
}
```

A `Trait` holds `ObjectMember.DefMember` values, and a `Method` with no body is abstract, so the emitter writes a declaration with no implementation. The service shape's name is the trait's name, and its `@documentation` is the trait's Scaladoc:

```scala mdoc
println(ScalaEmitter.emit(ScalaFile(PackageDecl(model.namespace), types = List(serviceTrait(model))), EmitterConfig.default))
```

The trait is the contract, and the implementation is yours. An application writes a class that extends `OrderService`, and the compiler checks every method against the generated signature, so a missing operation fails the build instead of a request.

## Collect the Errors into One Sealed Type

Smithy errors belong to the service, not to one operation, and an error such as `OrderNotFound` can be shared by several operations. The generator therefore collects every error the service's operations name, and writes one sealed type for them. Each error becomes a case class that extends it:

```scala mdoc:silent
def errorNames(model: SmithyModel): List[String] =
  operations(model).flatMap(_.errors.map(_.name)).distinct

def errorTypes(model: SmithyModel): List[TypeDefinition] = {
  val errorType  = errorTypeName(service(model))
  val sealedType = SealedTrait(errorType, doc = Some("Every error an operation of the service can return."))
  val cases = errorNames(model).map { name =>
    model.findShape(name).map(_.shape) match {
      case Some(shape: StructureShape) =>
        caseClassFor(model, name, shape, List(TypeRef(errorType)))
      case _ => throw new IllegalArgumentException(s"Error is not a structure: $name")
    }
  }
  sealedType :: cases
}
```

`errorNames` uses `distinct`, so `OrderNotFound` gets one case class even though two operations name it. The result is the sealed trait and its cases:

```scala mdoc
println(
  ScalaEmitter.emit(
    ScalaFile(
      PackageDecl(model.namespace),
      imports = List(Import.WildcardImport("zio.blocks.schema")),
      types = errorTypes(model)
    ),
    EmitterConfig.default
  )
)
```

An operation can return only the errors its own signature names, and a single type for the whole service keeps the trait short. The price is that a client matching on `OrderError` also has to handle `OrderAlreadyShipped` for `getOrder`, which can never return it. If you need that precision, write one sealed type per operation, which is what the OpenAPI version below does.

## Generate One File

The data types, the error type and the service trait belong in one source file, so the generator puts them in one `ScalaFile`. The data types are the structures and enums that are not errors:

```scala mdoc:silent
def dataTypes(model: SmithyModel): List[TypeDefinition] = {
  val errors = errorNames(model)
  model.shapes.flatMap {
    case ShapeDefinition(name, _) if errors.contains(name) => None
    case ShapeDefinition(name, shape: StructureShape)      => Some(caseClassFor(model, name, shape, Nil))
    case ShapeDefinition(name, shape: EnumShape)           => Some(enumFor(name, shape))
    case _                                                 => None
  }
}

def scalaFile(model: SmithyModel): ScalaFile =
  ScalaFile(
    PackageDecl(model.namespace),
    imports = List(Import.WildcardImport("zio.blocks.schema")),
    types = (dataTypes(model) ++ errorTypes(model)) :+ serviceTrait(model)
  )
```

Printing the whole file shows the three parts in order: the data types, the error type with its cases, and the service trait last:

```scala mdoc
println(ScalaEmitter.emit(scalaFile(model), EmitterConfig.default))
```

## Putting It Together

The generator reads a model file, maps it with the functions above and writes one file. The complete program below does that, and the only differences from the sections are that it reads the model from `smithy-examples/src/main/smithy/order-service.smithy` and writes the output to a file instead of printing it:

```scala mdoc:embed:smithy-examples/src/main/scala/generateservicetraits/CompleteExample.scala:show-line-numbers
```

Running it writes `OrderService.scala`, and that file has the same contents as the output of the last block.

## Running the Examples

All runnable code from the Smithy part is in [`smithy-examples/src/main/scala/generateservicetraits/`](https://github.com/zio/zio-blocks/tree/main/smithy-examples/src/main/scala/generateservicetraits), and the model is in [`smithy-examples/src/main/smithy/`](https://github.com/zio/zio-blocks/tree/main/smithy-examples/src/main/smithy). Run any file from the `smithy-examples` folder with `sbt "runMain generateservicetraits.<ObjectName>"`, for example `sbt "runMain generateservicetraits.CompleteExample"`.

## Generate Service Traits from OpenAPI Paths

OpenAPI has no service or operation shapes. Each path item holds up to eight operations, one per HTTP method, and each operation has an `operationId`, which names it. The generator reads `get`, `put`, `post`, `delete` and `patch`. Its parameters, its request body and its responses sit inside the operation, and each response is keyed by its status code. The mapping follows the same shape as the Smithy one, with three differences: the path parameters become method parameters, the request body becomes a `body` parameter, and the non-2xx responses become the error type of that one operation.

The spec below is the bookstore's paths, with the schemas reduced to their names. The trait depends only on the paths and the names of the schemas they refer to, so the output is the same as for the full spec in `openapi-examples/src/main/openapi/bookstore-service.json`:

```scala mdoc:silent:reset
import zio.blocks.docs.Doc
import zio.blocks.openapi._
import zio.blocks.schema.json._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val bookstoreServiceJson: String =
  """{
  "openapi": "3.1.0",
  "info": {
    "title": "Bookstore",
    "version": "1.0.0"
  },
  "paths": {
    "/orders/{orderId}": {
      "get": {
        "operationId": "getOrder",
        "summary": "Look up one order by its id.",
        "parameters": [
          {
            "name": "orderId",
            "in": "path",
            "required": true,
            "schema": {
              "type": "string",
              "format": "uuid"
            }
          }
        ],
        "responses": {
          "200": {
            "description": "The order",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/Order"
                }
              }
            }
          },
          "404": {
            "description": "No order has this id",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/ApiError"
                }
              }
            }
          }
        }
      }
    },
    "/orders/{orderId}/cancel": {
      "post": {
        "operationId": "cancelOrder",
        "summary": "Cancel an order that has not shipped yet.",
        "parameters": [
          {
            "name": "orderId",
            "in": "path",
            "required": true,
            "schema": {
              "type": "string",
              "format": "uuid"
            }
          }
        ],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "$ref": "#/components/schemas/CancelRequest"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "The cancelled order",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/Order"
                }
              }
            }
          },
          "404": {
            "description": "No order has this id",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/ApiError"
                }
              }
            }
          },
          "409": {
            "description": "The order has already shipped",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/ApiError"
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "Order": {
        "type": "object"
      },
      "ApiError": {
        "type": "object"
      },
      "CancelRequest": {
        "type": "object"
      }
    }
  }
}"""

val api: OpenAPI = Json.parse(bookstoreServiceJson).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
  case Right(parsed) => parsed
  case Left(error)   => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
}
```

## Read the Paths

The generator walks the paths and collects every operation, paired with the path item it belongs to, because the path item can hold parameters that apply to all its operations:

```scala mdoc:silent
val refPrefix = "#/components/schemas/"

def docOf(text: Option[Doc]): Option[String] = text.map(_.toString.trim)

final case class Endpoint(method: String, path: String, operationId: String, operation: Operation, item: PathItem)

def endpoints(api: OpenAPI): List[Endpoint] =
  api.paths.toList.flatMap(_.paths.toList).flatMap { case (path, item) =>
    List(
      "get"    -> item.get,
      "put"    -> item.put,
      "post"   -> item.post,
      "delete" -> item.delete,
      "patch"  -> item.patch
    ).collect { case (method, Some(op)) =>
      val id = op.operationId.getOrElse(
        throw new IllegalArgumentException(s"$method $path has no operationId")
      )
      Endpoint(method, path, id, op, item)
    }
  }
```

Each `operationId` becomes the method name, so an operation without one cannot be mapped, and the generator stops with an error. The next functions read a method's parameters and its success type from the spec:

```scala mdoc:silent
def componentName(schema: ReferenceOr[SchemaObject]): String = schema match {
  case ReferenceOr.Ref(reference) => reference.$ref.stripPrefix(refPrefix)
  case ReferenceOr.Value(_)       => throw new IllegalArgumentException("inline schemas are not supported: name the schema in components")
}

def jsonSchema(content: Iterable[(String, MediaType)]): Option[ReferenceOr[SchemaObject]] =
  content.collectFirst { case ("application/json", mediaType) => mediaType.schema }.flatten

def pathParams(e: Endpoint): List[MethodParam] =
  (e.item.parameters.toList ++ e.operation.parameters.toList).collect {
    case ReferenceOr.Value(p) if p.in == ParameterLocation.Path => MethodParam(p.name, TypeRef.String)
  }

def bodyParam(e: Endpoint): List[MethodParam] =
  e.operation.requestBody.toList.collect {
    case ReferenceOr.Value(body) =>
      jsonSchema(body.content.toList).map(s => MethodParam("body", TypeRef(componentName(s))))
  }.flatten

def successType(e: Endpoint): TypeRef =
  e.operation.responses.responses.toList.collectFirst {
    case (code, ReferenceOr.Value(response)) if code.startsWith("2") =>
      jsonSchema(response.content.toList).map(s => TypeRef(componentName(s))).getOrElse(TypeRef.Unit)
  }.getOrElse(TypeRef.Unit)
```

Printing the endpoints shows the method, the path, the parameters and the success type each one gets:

```scala mdoc
endpoints(api).foreach { e =>
  val params = pathParams(e).map(p => s"${p.name}: ${p.typeRef.name}")
  val body   = bodyParam(e).map(p => s"${p.name}: ${p.typeRef.name}")
  println(s"${e.method.toUpperCase} ${e.path} -> ${e.operationId}(${(params ++ body).mkString(", ")}): ${successType(e).name}")
}
```

Every path parameter maps to `String` here. The spec's `schema` for `orderId` is a UUID string, and the generator does not read formats for parameters, so a parameter declared as an integer would also come out as `String`. The success type is the schema of the first 2xx response, and an operation whose only response is a non-2xx code gets `Unit`.

## Map Status Codes to a Sealed Error Type per Operation

Each non-2xx response becomes a case of an error type that belongs to its operation. The case is named after the status code, and it carries the response's body when the response has one. Because each operation gets its own type, `getOrder` cannot return the `409` case that only `cancelOrder` declares:

```scala mdoc:silent
def errorType(e: Endpoint): Option[SealedTrait] = {
  val name = e.operationId.take(1).toUpperCase + e.operationId.drop(1) + "Error"
  val cases = e.operation.responses.responses.toList.collect {
    case (code, ReferenceOr.Value(response)) if !code.startsWith("2") =>
      val fields = jsonSchema(response.content.toList)
        .map(s => List(Field("body", TypeRef(componentName(s)))))
        .getOrElse(Nil)
      SealedTraitCase.CaseClassCase(
        CaseClass(s"Status$code", fields, derives = List("Schema"), doc = Some(response.description.toString.trim))
      )
  }
  if (cases.isEmpty) None
  else Some(SealedTrait(name, cases = cases, doc = Some(s"Responses of ${e.operationId} other than success.")))
}
```

A `SealedTrait` with cases emits its cases inside a companion object, so `getOrder`'s cases are written as `GetOrderError.Status404`. The generator returns `None` for an operation with no error responses, and then the method returns its success type without `Either`.

## Generate the Service Trait for the Bookstore

The last functions turn each endpoint into a method and collect the error types and the trait into one file:

```scala mdoc:silent
def method(e: Endpoint): Method = {
  val params     = List(ParamList(pathParams(e) ++ bodyParam(e)))
  val success    = successType(e)
  val returnType = errorType(e) match {
    case Some(err) => TypeRef.of("Either", TypeRef(err.name), success)
    case None      => success
  }
  Method(e.operationId, params = params.filter(_.params.nonEmpty), returnType = returnType, doc = docOf(e.operation.summary))
}

def serviceTrait(api: OpenAPI, packageName: String): ScalaFile = {
  val eps    = endpoints(api)
  val name   = api.info.title.filter(_.isLetterOrDigit) + "Service"
  val errors = eps.flatMap(errorType)
  val service = Trait(
    name,
    members = eps.map(e => ObjectMember.DefMember(method(e))),
    doc = Some(s"Operations of the ${api.info.title} API, one method per operation.")
  )
  ScalaFile(
    PackageDecl(packageName),
    imports = List(Import.WildcardImport("zio.blocks.schema")),
    types = errors :+ service
  )
}
```

The package is passed in, because the trait belongs next to the data types that another generator writes, and it must be the same package for their names to resolve. Printing the file shows the bookstore's trait:

```scala mdoc
println(ScalaEmitter.emit(serviceTrait(api, "com.example.bookstore"), EmitterConfig.default))
```

The trait refers to `Order`, `CancelRequest` and `ApiError`, which come from the data types of the same spec. Generate those with the [OpenAPI data types generator](./generate-scala-types-from-openapi.md) from the same file. Its output derives `Schema` for each case class, which the `Status` cases need, and both outputs compile together in one package.

## What the OpenAPI Mapping Leaves Out

The table lists what the generator reads from a spec, and what it does not.

| Spec element                           | Handled | Behavior                                                    |
| -------------------------------------- | ------- | ----------------------------------------------------------- |
| Path and operation with an `operationId` | Yes   | One method per operation                                    |
| Path parameters                        | Yes     | Each becomes a `String` parameter, whatever its schema says |
| JSON request body with a `$ref`        | Yes     | A `body` parameter of the referenced type                   |
| 2xx response with a JSON `$ref`        | Yes     | The success type, or `Unit` without a body                  |
| Non-2xx responses                      | Yes     | A case per status code in an error type of the operation    |
| Inline schemas in bodies or responses  | No      | Fails the build: name the schema in `components`            |
| `default` response                     | No      | Ignored                                                     |
| `options`, `head` and `trace` operations | No    | Ignored                                                     |
| Query, header and cookie parameters    | No      | Ignored                                                     |
| Security requirements, callbacks       | No      | Ignored                                                     |

## Running the OpenAPI Example

The OpenAPI files are in [`openapi-examples/src/main/scala/generateservicetraits/`](https://github.com/zio/zio-blocks/tree/main/openapi-examples/src/main/scala/generateservicetraits), and the spec is in [`openapi-examples/src/main/openapi/bookstore-service.json`](https://github.com/zio/zio-blocks/tree/main/openapi-examples/src/main/openapi/bookstore-service.json). Run any file from the `openapi-examples` folder with `sbt "runMain generateservicetraits.<ObjectName>"`, for example `sbt "runMain generateservicetraits.CompleteExample"`.

## Going Further

- **Want the data types for the same spec?** [Generate Scala Types from an OpenAPI Spec at Build Time](./generate-scala-types-from-openapi.md) generates the `Order`, `CancelRequest` and `ApiError` types that the trait above refers to.
- **Want the data types for a Smithy model?** [Generate Scala Code from a Smithy Model](./generate-scala-code-from-smithy.md) generates the structures, unions and enums of a Smithy file.
- **Want to know everything the OpenAPI parser reads?** The [openapi module reference](../reference/openapi.md) lists every type of the `OpenAPI` model.
- **Want to know every IR node the generator writes?** The [codegen module reference](../reference/codegen/index.md) covers `Trait`, `Method`, `SealedTrait` and the other types used here.
