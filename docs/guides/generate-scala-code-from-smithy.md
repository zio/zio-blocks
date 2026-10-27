---
id: generate-scala-code-from-smithy
title: "Generate Scala Code from a Smithy Model"
description: "Build a small generator that parses a Smithy model, maps its shapes to Scala types and emits Schema-ready source for Scala 3 and Scala 2."
keywords:
  - "Code Generation"
  - "Smithy"
  - "Data Contracts"
  - "SmithyModel"
  - "ScalaEmitter"
  - "ScalaFile"
---

Welcome! This tutorial is for Scala developers who know case classes, sealed traits and pattern matching, and who have derived a `Schema` before (if you haven't, read [Your First Schema](./your-first-schema.md) first). You don't need any prior experience with Smithy or with code generation to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- How to parse a Smithy model into a `SmithyModel` and walk its shapes
- How Smithy structures, unions, enums, lists, maps and optional members map to Scala types
- How the codegen module describes Scala source as data, and how `ScalaEmitter` turns that data into Scala 3 or Scala 2 code
- How to make the generated types carry a `Schema`, so they encode to JSON and back
- How to run the generator from sbt, so the generated code is compiled with the rest of your build

We learn these concepts by writing a small generator for an order service's data contract, one step at a time:

1. Parse the model
2. Map Smithy types to Scala types
3. Generate your first case class
4. Turn unions and enums into sealed types
5. Emit a whole file for Scala 3 and Scala 2
6. Add schema derivation
7. Put the generator together
8. Compile the generated code with sbt

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the smithy and codegen modules to your `build.sbt`. The code we generate also needs the schema module:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-smithy"  % "@VERSION@",
  "dev.zio" %% "zio-blocks-codegen" % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema"  % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-smithy"  % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-codegen" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema"  % "@VERSION@"
)
scalacOptions += "-experimental"
```

The smithy and codegen modules are published for the JVM only. The code in this tutorial is compiled with Scala 3; for scala-cli or cross-building, see [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md).

## 2. Background: One Contract, Many Programs

Our order service and the teams that call it agree on the shape of an order: an id, a list of line items, a status and an optional note. That agreement is a data contract. If each team writes its own Scala case classes for it, the copies drift apart: one team adds a field, another renames one, and the mistake only shows up when a message fails to decode in production. Smithy is a language for writing such contracts once, in a `.smithy` file, and code generation turns that file into types, so every program compiles against the same definition.

ZIO Blocks has two modules for this, and they don't know about each other. The [smithy module](../reference/smithy.md) parses a `.smithy` file into a `SmithyModel`, a plain Scala value that lists every shape. The [codegen module](../reference/codegen/index.md) describes Scala source as data — a `ScalaFile` holding `CaseClass`, `SealedTrait` and `Enum` values — and its `ScalaEmitter` turns that data into source text. The glue between them, which decides that a Smithy structure becomes a case class, is code we write ourselves in this tutorial:

```
orders.smithy ──SmithyModel.parse──▶ SmithyModel ──our mapping──▶ ScalaFile ──ScalaEmitter.emit──▶ Orders.scala
   (text)                            (shapes)                     (Scala as data)                 (text)
```

If you need a complete Smithy toolchain, [smithy4s](https://disneystreaming.github.io/smithy4s/) generates data types and services, clients and servers too. This tutorial builds something smaller: a generator you own and can read in one sitting, covering data shapes only, whose output carries ZIO Blocks schemas.

## 3. Parse the Model

Let's start with the contract itself. A Smithy file begins with a `$version` line and a `namespace`, followed by shapes: a `structure` is a record with named members, a `list` and a `map` are collections, a `union` holds exactly one of several members, and an `enum` is a fixed set of names. Members are optional unless they carry the `@required` trait, and a `///` line is a documentation comment.

We keep the model in a string so the tutorial runs anywhere; in section 9 our generator reads the same text from an `orders.smithy` file:

```scala mdoc:silent
import zio.blocks.smithy._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val ordersSmithy: String =
  """$version: "2"
    |
    |namespace com.acme.orders
    |
    |/// An order placed by a customer.
    |structure Order {
    |    @required
    |    id: String
    |
    |    @required
    |    items: LineItems
    |
    |    @required
    |    status: OrderStatus
    |
    |    @required
    |    priority: Priority
    |
    |    /// Free-text instructions from the customer.
    |    note: String
    |
    |    attributes: Attributes
    |}
    |
    |list LineItems {
    |    member: LineItem
    |}
    |
    |map Attributes {
    |    key: String
    |    value: String
    |}
    |
    |/// One product line in an order.
    |structure LineItem {
    |    @required
    |    sku: String
    |
    |    @required
    |    quantity: Integer
    |
    |    @required
    |    unitPrice: BigDecimal
    |}
    |
    |/// Where an order is in its life cycle.
    |union OrderStatus {
    |    placed: Unit
    |    shipped: Shipment
    |    cancelled: Cancellation
    |}
    |
    |structure Shipment {
    |    @required
    |    trackingNumber: String
    |
    |    @required
    |    shippedAt: Timestamp
    |}
    |
    |structure Cancellation {
    |    @required
    |    reason: String
    |}
    |
    |/// How fast the order ships.
    |enum Priority {
    |    STANDARD
    |    EXPRESS
    |}
    |""".stripMargin
```

The three imports bring in the Smithy parser and model (`zio.blocks.smithy`), the codegen data types (`zio.blocks.codegen.ir`) and the emitter (`zio.blocks.codegen.emit`); we use all of them in later sections.

Parsing can fail, so `SmithyModel.parse` returns an `Either[SmithyError, SmithyModel]`. A generator can't do anything useful with a broken contract, so we stop with the parser's message:

```scala mdoc:silent
val model: SmithyModel = SmithyModel.parse(ordersSmithy) match {
  case Right(parsed) => parsed
  case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
}
```

The `SmithyError#formatMessage` method includes the line and column of the problem. For example, a file that forgets its `$version` line fails like this:

```scala mdoc
SmithyModel.parse("namespace com.acme.orders").left.map(_.formatMessage)
```

A parsed model is a list of `ShapeDefinition` values, each holding a shape's name and the shape itself. Let's print every shape and its kind:

```scala mdoc
model.shapes.foreach { definition =>
  println(s"${definition.name}: ${definition.shape.getClass.getSimpleName}")
}
```

Each Smithy shape type has its own Scala class: `StructureShape`, `ListShape`, `MapShape`, `UnionShape`, `EnumShape` and one class per simple type. Now let's look inside `Order`. First we pick its `StructureShape` out of the model:

```scala mdoc:silent
val orderShape: StructureShape =
  model.shapes.collectFirst { case ShapeDefinition("Order", shape: StructureShape) => shape }.get
```

Then we print each member's target — the shape its value has — and whether it is required:

```scala mdoc
orderShape.members.foreach { member =>
  val required = member.traits.contains(TraitApplication.required)
  println(s"${member.name}: target=${member.target}, required=$required")
}
```

The code above:

- `model.shapes.collectFirst { ... }` finds the definition named `Order` and keeps its `StructureShape`.
- `member.target` is a `ShapeId`, a namespace and a name.
- `TraitApplication.required` is the parsed form of `@required`, so `contains` tells us whether a member carries it.

Notice the targets: they print as `#String` and `#LineItems`, with nothing before the `#`. The parser keeps a name exactly as the file spells it, so an unqualified name like `String` gets an empty namespace, whether it means a built-in Smithy type or a shape from our own file. Our generator therefore looks a target up by its name: first among the model's shapes, then among Smithy's built-in types.

## 4. Map Smithy Types to Scala Types

Every member needs a Scala type. In the codegen module a type is a `TypeRef`: a name plus type arguments, so `TypeRef.list(TypeRef.String)` stands for `List[String]`. Smithy's built-in simple types map to these Scala types:

| Smithy type  | Scala type                        |
| ------------ | --------------------------------- |
| `String`     | `String`                          |
| `Boolean`    | `Boolean`                         |
| `Byte`       | `Byte`                            |
| `Short`      | `Short`                           |
| `Integer`    | `Int`                             |
| `Long`       | `Long`                            |
| `Float`      | `Float`                           |
| `Double`     | `Double`                          |
| `BigInteger` | `BigInt`                          |
| `BigDecimal` | `BigDecimal`                      |
| `Timestamp`  | `java.time.Instant`               |
| `Blob`       | `zio.blocks.chunk.Chunk[Byte]`    |
| `Document`   | `zio.blocks.schema.DynamicValue`  |

Every type in the table has a built-in `Schema`, which matters in section 8. We write the last three with their full package names, so the generated file needs no extra imports.

The table becomes a function that returns `None` for a name it doesn't know:

```scala mdoc:silent
def simpleTypeRef(name: String): Option[TypeRef] = name match {
  case "String"     => Some(TypeRef.String)
  case "Boolean"    => Some(TypeRef.Boolean)
  case "Byte"       => Some(TypeRef.Byte)
  case "Short"      => Some(TypeRef.Short)
  case "Integer"    => Some(TypeRef.Int)
  case "Long"       => Some(TypeRef.Long)
  case "Float"      => Some(TypeRef.Float)
  case "Double"     => Some(TypeRef.Double)
  case "BigInteger" => Some(TypeRef.BigInt)
  case "BigDecimal" => Some(TypeRef.BigDecimal)
  case "Timestamp"  => Some(TypeRef("java.time.Instant"))
  case "Blob"       => Some(TypeRef.of("zio.blocks.chunk.Chunk", TypeRef.Byte))
  case "Document"   => Some(TypeRef("zio.blocks.schema.DynamicValue"))
  case _            => None
}
```

Shapes defined in our file need more thought. A list or a map gets no Scala type of its own: a member that targets `LineItems` becomes a `List[LineItem]` directly. Structures, unions and enums become named Scala types, so a member that targets one refers to it by name. We turn any other shape into an error, so the generator never writes code it can't stand behind:

```scala mdoc:silent
def typeRefFor(model: SmithyModel, target: ShapeId): TypeRef =
  model.findShape(target.name).map(_.shape) match {
    case Some(list: ListShape) =>
      TypeRef.list(typeRefFor(model, list.member.target))
    case Some(map: MapShape) =>
      TypeRef.map(typeRefFor(model, map.key.target), typeRefFor(model, map.value.target))
    case Some(_: StructureShape) | Some(_: UnionShape) | Some(_: EnumShape) =>
      TypeRef(target.name)
    case Some(other) =>
      throw new IllegalArgumentException(s"Unsupported shape: ${other.name}")
    case None =>
      simpleTypeRef(target.name).getOrElse(throw new IllegalArgumentException(s"Unknown shape: $target"))
  }
```

The code above:

- `SmithyModel#findShape` looks up a shape of our own file by name, and returns `None` for built-in types such as `String`.
- `ListShape#member` and `MapShape#key`/`MapShape#value` are members too, so `typeRefFor` calls itself on their targets.
- `TypeRef.list` and `TypeRef.map` build `List[...]` and `Map[..., ...]`.
- The last case falls back to the table, and fails loudly on a name it can't map.

To check the mapping, let's render the type of each `Order` member with `ScalaEmitter.emitTypeRef`, which turns a single `TypeRef` into source text:

```scala mdoc
orderShape.members.foreach { member =>
  println(s"${member.name}: ${ScalaEmitter.emitTypeRef(typeRefFor(model, member.target))}")
}
```

The list and the map have disappeared into `List[LineItem]` and `Map[String, String]`. The optional members don't show `Option` yet; that is the job of the next section.

<details>
  <summary>Runnable version, with setup from section 3: <code>Concept4MapTypesExample.scala</code></summary>

```scala mdoc:embed:smithy-examples/src/main/scala/generatescalafromsmithy/Concept4MapTypesExample.scala:show-line-numbers
```

</details>

## 5. Generate Your First Case Class

A Smithy structure becomes a case class. In the codegen module a case class is a `CaseClass` value with a name and a list of `Field`s, and each `Field` has a name, a `TypeRef` and an optional default value. A required member becomes a plain field. Any other member may be missing, so it becomes an `Option` with a default of `None`, which lets callers leave it out.

Let's write the mapping, one small function per idea:

```scala mdoc:silent
def docOf(traits: List[TraitApplication]): Option[String] =
  traits.collectFirst {
    case TraitApplication(ShapeId("smithy.api", "documentation"), Some(NodeValue.String(text))) => text
  }

def fieldFor(model: SmithyModel, member: MemberDefinition): Field = {
  val fieldType = typeRefFor(model, member.target)
  if (member.traits.contains(TraitApplication.required)) Field(member.name, fieldType)
  else Field(member.name, TypeRef.optional(fieldType), defaultValue = Some("None"))
}

def caseClassFor(model: SmithyModel, name: String, shape: StructureShape): CaseClass =
  CaseClass(name, shape.members.map(fieldFor(model, _)), doc = docOf(shape.traits))
```

The code above:

- `docOf` finds the `@documentation` trait. The parser turns every `///` comment into this trait, with the comment's text as a `NodeValue.String`.
- `fieldFor` keeps a required member's type and wraps any other in `TypeRef.optional`, with the default value `None` written as source text.
- `caseClassFor` turns every member into a field and carries the structure's documentation over as a Scaladoc comment.

A `CaseClass` is only data. To see source, we wrap it in a `ScalaFile` with a package and emit it:

```scala mdoc
println(ScalaEmitter.emit(ScalaFile(PackageDecl(model.namespace), types = List(caseClassFor(model, "Order", orderShape)))))
```

That's our first generated type. `ScalaEmitter.emit` takes the `ScalaFile` and an `EmitterConfig`; we leave the config out, so it uses `EmitterConfig.default`, which writes Scala 3 code (the trailing comma after the last field is Scala 3 style). The package name comes straight from the Smithy namespace.

:::note
The `///` comment on the `note` member doesn't reach the generated code: in this release `ScalaEmitter` doesn't render documentation on fields, only on types, so `fieldFor` doesn't copy it.
:::

<details>
  <summary>Runnable version, with setup from sections 3–4: <code>Concept5CaseClassExample.scala</code></summary>

```scala mdoc:embed:smithy-examples/src/main/scala/generatescalafromsmithy/Concept5CaseClassExample.scala:show-line-numbers
```

</details>

## 6. Turn Unions and Enums into Sealed Types

A Smithy union holds exactly one of its members, which is what a Scala sealed trait expresses. Each union member becomes one case of the sealed trait: a member that targets `Unit` carries no data and becomes a case object, and any other member becomes a case class with one `value` field. A Smithy enum is a fixed set of names, so it becomes the codegen `Enum` type.

Let's write both mappings:

```scala mdoc:silent
def sealedTraitFor(model: SmithyModel, name: String, shape: UnionShape): SealedTrait = {
  val cases = shape.members.map { member =>
    val caseName = member.name.capitalize
    if (member.target.name == "Unit") SealedTraitCase.CaseObjectCase(caseName)
    else SealedTraitCase.CaseClassCase(CaseClass(caseName, List(Field("value", typeRefFor(model, member.target)))))
  }
  SealedTrait(name, cases = cases, doc = docOf(shape.traits))
}

def enumFor(name: String, shape: EnumShape): Enum =
  Enum(name, shape.members.map(member => EnumCase.SimpleCase(member.name)), doc = docOf(shape.traits))
```

The code above:

- `member.name.capitalize` turns the Smithy member `shipped` into the Scala case `Shipped`.
- `SealedTraitCase.CaseObjectCase` and `SealedTraitCase.CaseClassCase` are the two kinds of case a `SealedTrait` can have; we don't add `extends OrderStatus` ourselves, because the emitter adds it to every case.
- `EnumCase.SimpleCase` is an enum case without fields, one per Smithy enum member.

Next, we generate both types from the model:

```scala mdoc:silent
val statusTrait: SealedTrait =
  model.shapes.collectFirst { case ShapeDefinition(name, shape: UnionShape) => sealedTraitFor(model, name, shape) }.get

val priorityEnum: Enum =
  model.shapes.collectFirst { case ShapeDefinition(name, shape: EnumShape) => enumFor(name, shape) }.get
```

Then we emit them together in one file:

```scala mdoc
println(ScalaEmitter.emit(ScalaFile(PackageDecl(model.namespace), types = List(statusTrait, priorityEnum))))
```

The emitter places the cases inside the companion object `OrderStatus`, so code that uses them writes `OrderStatus.Shipped(...)`. `Priority` comes out as a Scala 3 `enum`, because we are still emitting with the default, Scala 3 configuration.

<details>
  <summary>Runnable version, with setup from sections 3–5: <code>Concept6SealedTypesExample.scala</code></summary>

```scala mdoc:embed:smithy-examples/src/main/scala/generatescalafromsmithy/Concept6SealedTypesExample.scala:show-line-numbers
```

</details>

## 7. Emit a Whole File for Scala 3 and Scala 2

We now have a mapping for every kind of shape in our contract. One more function walks the whole model and collects the generated types into a `ScalaFile`; lists and maps produce no type of their own, so the `collect` skips them:

```scala mdoc:silent
def scalaFileFor(model: SmithyModel): ScalaFile = {
  val types = model.shapes.collect {
    case ShapeDefinition(name, shape: StructureShape) => caseClassFor(model, name, shape)
    case ShapeDefinition(name, shape: UnionShape)     => sealedTraitFor(model, name, shape)
    case ShapeDefinition(name, shape: EnumShape)      => enumFor(name, shape)
  }
  ScalaFile(PackageDecl(model.namespace), types = types)
}

val ordersFile: ScalaFile = scalaFileFor(model)
```

The code above:

- `model.shapes.collect { ... }` maps each structure, union and enum definition to its codegen type, using the functions from sections 5 and 6.
- Lists and maps match no case, so `collect` drops them.
- `ScalaFile(PackageDecl(model.namespace), types = types)` wraps the types in a file whose package comes from the Smithy namespace.
- `ordersFile` keeps the result, so we can emit it more than once.

Because `ordersFile` describes the code rather than spelling it out, we can emit it more than once. `EmitterConfig.default` writes Scala 3:

```scala mdoc
println(ScalaEmitter.emit(ordersFile, EmitterConfig.default))
```

`EmitterConfig.scala2` writes the same types in Scala 2 syntax:

```scala mdoc
println(ScalaEmitter.emit(ordersFile, EmitterConfig.scala2))
```

Compare the two outputs. The Scala 2 version drops the trailing commas, and `Priority` becomes a sealed trait with one case object per value, because Scala 2 has no `enum`. We didn't change a single line of our mapping: the dialect is a choice we make when we emit, not when we describe the types. Both files compile, with Scala 3.9 and Scala 2.13 respectively.

<details>
  <summary>Runnable version, with setup from sections 3–6: <code>Concept7EmitBothDialectsExample.scala</code></summary>

```scala mdoc:embed:smithy-examples/src/main/scala/generatescalafromsmithy/Concept7EmitBothDialectsExample.scala:show-line-numbers
```

</details>

## 8. Add Schema Derivation

The generated types compile, but our services also need to encode them to JSON and back. In ZIO Blocks that takes a `Schema` for each type, and the compiler can derive one. In Scala 3, a case class asks for it with `derives Schema`. In Scala 2, it gets a companion object holding `implicit val schema: Schema[Order] = Schema.derived`.

Both forms are codegen data too. A `CaseClass` has a `derives` list, which the emitter renders in Scala 3 only, and an optional `CompanionObject` whose members the emitter renders in either dialect. We add the schema after the mapping, as a separate step over the finished `ScalaFile`:

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

The code above:

- `caseClass.copy(derives = List("Schema"))` asks the emitter to write `derives Schema` after the class.
- `ObjectMember.ValMember(..., isImplicit = true)` describes the companion's `implicit val`, and its value is the source text `Schema.derived`.
- `Import.WildcardImport("zio.blocks.schema")` adds the import that brings `Schema` into scope.
- Sealed traits and enums keep their shape (the `case other => other` line).

Only case classes get a schema of their own. `Schema.derived` derives schemas for the types it meets inside a class, so deriving the schema for `Order` also covers its `OrderStatus` field and its `Priority` field, and `Order` is the type our services send.

Let's print the generated `Order` in both dialects, using `ScalaEmitter.emitTypeDefinition`, which emits a single type:

```scala mdoc
println(ScalaEmitter.emitTypeDefinition(withSchemas(ordersFile, scala3 = true).types.head, EmitterConfig.default))
println()
println(ScalaEmitter.emitTypeDefinition(withSchemas(ordersFile, scala3 = false).types.head, EmitterConfig.scala2))
```

The Scala 3 class ends with `derives Schema`, and the Scala 2 class has a companion object with the `implicit val`. The full Scala 2 file also starts with `import zio.blocks.schema._`, while the Scala 3 file uses `import zio.blocks.schema.*`.

:::caution
In this release, only `CaseClass` is a safe place for the schema. The emitter renders `derives` on case classes only, so it ignores the `derives` list of a `SealedTrait` or an `Enum`. Two known bugs in 0.0.56 rule out the companion route for the other types and for Scala 3: the emitter drops the companion object of an `Enum`, and under `EmitterConfig.default` it renders an implicit companion value as `given val`, which Scala 3 rejects. Putting the schema on case classes only, as above, avoids all of them.
:::

<details>
  <summary>Runnable version, with setup from sections 3–7: <code>Concept8SchemaExample.scala</code></summary>

```scala mdoc:embed:smithy-examples/src/main/scala/generatescalafromsmithy/Concept8SchemaExample.scala:show-line-numbers
```

</details>

## 9. Put the Generator Together

So far the model comes from a string and the output goes to the console. A real generator reads a `.smithy` file and writes a `.scala` file. This example combines everything from sections 3 to 8 into one program, with two differences: it reads the model from the path in its first argument (by default `src/main/smithy/orders.smithy`), and it writes the Scala 3 version, with schemas, to `Orders.scala` in the directory named by its second argument (by default `target/generated-orders`):

```scala mdoc:embed:smithy-examples/src/main/scala/generatescalafromsmithy/CompleteExample.scala
```

Running it from the `smithy-examples` folder with `sbt "runMain generatescalafromsmithy.CompleteExample"` prints:

```text
Generated Order, LineItem, OrderStatus, Shipment, Cancellation, Priority into target/generated-orders/Orders.scala
```

## 10. Compile the Generated Code with sbt

The last step is to make the generator part of the build, so the generated code is compiled — and kept up to date — every time the build compiles. sbt calls this a source generator: a task that writes source files before compilation and returns them, so sbt compiles them with everything else. Their usual home is the `src_managed` folder under `target/`, so the generated code never ends up in version control.

sbt 1 runs its own build code on Scala 2.12, and the smithy and codegen modules aren't published for Scala 2.12, so the generator can't run inside the build definition itself. Instead it stays an ordinary program in one sbt project, and a second project runs it as its source generator. Here is the part of `build.sbt` that does it, adapted from our examples project (which writes its dependencies through a small helper); the root project `smithy-examples` holds `CompleteExample` and depends on the smithy and codegen modules, and the new `orders-app` project compiles the result:

```scala
// The project that holds the generator, referred to by its id
val generator = LocalProject("smithy-examples")

lazy val ordersApp = (project in file("orders-app"))
  .settings(
    run / fork := true,
    libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@",
    Compile / sourceGenerators += Def.task {
      val model     = (generator / baseDirectory).value / "src" / "main" / "smithy" / "orders.smithy"
      val outputDir = (Compile / sourceManaged).value / "smithy"
      val classpath = (generator / Compile / fullClasspath).value.files
      (generator / Compile / runner).value
        .run(
          "generatescalafromsmithy.CompleteExample",
          classpath,
          Seq(model.getPath, outputDir.getPath),
          streams.value.log
        )
        .get
      (outputDir ** "*.scala").get
    }.taskValue
  )
```

The code above:

- `LocalProject("smithy-examples")` refers to the generator's project by its id; a direct reference to its `lazy val` would make the two project definitions depend on each other.
- `(generator / Compile / fullClasspath)` is the generator's compiled code plus the smithy and codegen jars, and `(generator / Compile / runner)` runs a main class on that classpath.
- The task passes the model's path and `orders-app`'s `src_managed/main/smithy` folder to `CompleteExample`, then returns every `.scala` file the generator wrote there.
- `orders-app` itself depends only on the schema module: it never sees the generator, only the code it writes.

With that in place, `orders-app` can use the generated types like hand-written ones. This program builds an `Order`, encodes it to JSON with `toJsonString` and decodes it back with `fromJson`:

```scala mdoc:embed:smithy-examples/orders-app/src/main/scala/generatescalafromsmithy/app/OrdersApp.scala
```

The code above:

- `import com.acme.orders._` brings in the generated `Order`, `LineItem`, `OrderStatus`, `Shipment` and `Priority`, which come from the Smithy namespace.
- `Order(...)` builds a value with the generated case class. The `note` field takes `Some(...)` and `attributes` is left at its `None` default.
- `order.toJsonString` and `json.fromJson[Order]` use the derived `Schema` that the generator added.
- The last line compares the decoded value with the original.

Running `sbt ordersApp/run` from the `smithy-examples` folder generates `Orders.scala`, compiles it together with `OrdersApp` and prints:

```text
{"id":"order-1001","items":[{"sku":"MUG-RED","quantity":2,"unitPrice":12.50}],"status":{"Shipped":{"value":{"trackingNumber":"1Z999","shippedAt":"2026-10-27T09:30:00Z"}}},"priority":"EXPRESS","note":"Leave at the door"}
round trip: true
```

The JSON follows the generated types: the union member becomes an object keyed by its case name, the enum value becomes its name, and the missing `attributes` member is left out. Try adding a `@required` member to `Order` in `orders.smithy` and compiling again: the new field appears in the generated class, and every place that builds an `Order` without it fails to compile until you update it.

## 11. Running the Examples

All runnable code from this tutorial is in [`smithy-examples/src/main/scala/generatescalafromsmithy/`](https://github.com/zio/zio-blocks/tree/main/smithy-examples/src/main/scala/generatescalafromsmithy), and the app that compiles the generated code is in [`smithy-examples/orders-app/`](https://github.com/zio/zio-blocks/tree/main/smithy-examples/orders-app). Run any file from the `smithy-examples` folder with `sbt "runMain generatescalafromsmithy.<ObjectName>"`, and the app with `sbt ordersApp/run`.

## 12. What You've Learned

In this tutorial, you learned:

- How to parse a Smithy model with `SmithyModel.parse` and walk its shapes, and why an unqualified target such as `String` has an empty namespace
- How structures, unions, enums, lists, maps, simple types and optional members map to `CaseClass`, `SealedTrait`, `Enum` and `TypeRef` values
- How `ScalaEmitter.emit` turns one `ScalaFile` into Scala 3 or Scala 2 source, depending on the `EmitterConfig`
- How to add `derives Schema` or a companion `implicit val schema` as a separate step, so the generated types encode to JSON and back
- How a source generator in a second sbt project runs the generator and compiles its output

You now own a small, readable code generator. The next step is to extend it for your own contracts: more Smithy traits, more shapes, or a different output style.

## 13. Where to Go Next

- **Want to know everything the parser reads?** The [smithy module reference](../reference/smithy.md) lists every shape and trait type, and shows how to build models in code.
- **Want to generate more than data types?** The [codegen module reference](../reference/codegen/index.md) covers the whole IR, including traits, objects, methods and opaque types, and the [`ScalaEmitter`](../reference/codegen/scala-emitter.md) and [`EmitterConfig`](../reference/codegen/emitter-config.md) pages describe every output option.
- **New to schemas?** [Your First Schema](./your-first-schema.md) explains what the derived `Schema` gives the generated types, from JSON codecs to JSON Schema.
- **Using OpenAPI instead of Smithy?** [How to Generate Scala Types from an OpenAPI Spec at Build Time](./generate-scala-types-from-openapi.md) builds the same kind of generator for the component schemas of an OpenAPI document.
