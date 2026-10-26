---
id: add-zio-blocks-to-your-build
title: "How to Add ZIO Blocks to Your Build"
description: "Pick the ZIO Blocks modules you need and add them to an sbt or scala-cli build, for any Scala version and for Scala.js."
keywords:
  - "Installation"
  - "sbt"
  - "scala-cli"
  - "Scala.js"
  - "Cross-Building"
---

This guide gets ZIO Blocks onto your classpath. You'll pick the modules you need, write the dependency line for your Scala version, and check that it compiles. Each section is a copy-paste recipe.

## The Artifact Name Depends on Your Scala Version

ZIO Blocks publishes every module under two names. The artifacts built with Scala 2.13 and Scala 3.3 LTS are called `zio-blocks-<module>`. The artifacts built with the latest Scala 3 release are called `zio-blocks-next-<module>`:

| Your Scala version | Artifact name                  | Built with |
|--------------------|--------------------------------|------------|
| 2.13.x             | `zio-blocks-<module>`          | 2.13.18    |
| 3.3.x to 3.8.x     | `zio-blocks-<module>`          | 3.3.7 LTS  |
| 3.9.x and newer    | `zio-blocks-next-<module>`     | 3.9.0      |

A Scala 3.9 build can still use `zio-blocks-<module>`, but `zio-blocks-next-<module>` is the build made for it. The reverse does not work: a Scala 3.3 to 3.8 compiler cannot read code compiled by Scala 3.9, so `zio-blocks-next-<module>` fails there with a "Forward incompatible TASTy file" error.

## Prerequisites

- **sbt 1.x** or **scala-cli**.
- **JDK 11+** for Scala 2.13 and Scala 3.3 to 3.8; **JDK 17+** for the `zio-blocks-next-*` artifacts.
- **JDK 21+** on the JVM for `streams`, `telemetry`, and `telemetry-otel`, and for every module built on `streams`: `http-model`, `http-model-schema`, `endpoint`, `datastar`, `http-htmx`, `sql`, `sql-zio`, `data-migration`, and `projection`.
- For Scala.js, **Scala.js 1.22.0+** (the `sbt-scalajs` plugin in `project/plugins.sbt`). Older linkers reject the artifacts with `IRVersionNotSupportedException`.

## Pick Your Modules

Every module lives under the `dev.zio` organization. Replace `<module>` in the artifact name with a value from the first column:

| `<module>`           | What you get                                                        | Platforms | Scala      |
|----------------------|---------------------------------------------------------------------|-----------|------------|
| `schema`             | `Schema` derivation, optics, validation, and the built-in JSON codec | JVM · JS  | 2.13 · 3.x |
| `schema-avro`        | Apache Avro codec                                                   | JVM       | 2.13 · 3.x |
| `schema-bson`        | MongoDB BSON codec                                                  | JVM       | 2.13 · 3.x |
| `schema-csv`         | CSV codec                                                           | JVM · JS  | 2.13 · 3.x |
| `schema-messagepack` | MessagePack codec                                                   | JVM · JS  | 2.13 · 3.x |
| `schema-thrift`      | Apache Thrift codec                                                 | JVM       | 2.13 · 3.x |
| `schema-toon`        | TOON codec                                                          | JVM · JS  | 2.13 · 3.x |
| `schema-xml`         | XML codec                                                           | JVM · JS  | 2.13 · 3.x |
| `schema-yaml`        | YAML codec                                                          | JVM · JS  | 2.13 · 3.x |
| `typeid`             | Compile-time type identity                                          | JVM · JS  | 2.13 · 3.x |
| `chunk`              | Immutable indexed sequences without boxing                          | JVM · JS  | 2.13 · 3.x |
| `maybe`              | Low-allocation optional values                                      | JVM · JS  | 2.13 · 3.x |
| `combinators`        | Tuple, `Either`, and union composition                              | JVM · JS  | 2.13 · 3.x |
| `context`            | Type-indexed heterogeneous collections                              | JVM · JS  | 2.13 · 3.x |
| `scope`              | Compile-time safe resource management and `Wire`                   | JVM · JS  | 2.13 · 3.x |
| `async`              | Asynchronous effect type with direct-style `await`                  | JVM · JS  | 2.13 · 3.x |
| `streams`            | Pull-based streams                                                  | JVM · JS  | 2.13 · 3.x |
| `mux`                | Stream multiplexer for HTTP/2, QUIC, and WebSocket-style protocols  | JVM · JS  | 2.13 · 3.x |
| `ringbuffer`         | Lock-free bounded ring buffers                                      | JVM · JS  | 2.13 · 3.x |
| `config`             | Typed configuration loading                                         | JVM · JS  | 2.13 · 3.x |
| `config-yaml`        | YAML source for `config`                                            | JVM · JS  | 2.13 · 3.x |
| `config-json`        | JSON source for `config`                                            | JVM · JS  | 2.13 · 3.x |
| `config-hocon`       | HOCON source for `config`                                           | JVM · JS  | 2.13 · 3.x |
| `mediatype`          | IANA media types                                                    | JVM · JS  | 2.13 · 3.x |
| `http-model`         | HTTP data model: URLs, headers, requests, responses                 | JVM · JS  | 2.13 · 3.x |
| `http-model-schema`  | Schema-based typed access to the HTTP model                         | JVM · JS  | 2.13 · 3.x |
| `endpoint`           | Typed HTTP endpoint descriptions                                    | JVM · JS  | 2.13 · 3.x |
| `openapi`            | OpenAPI 3.1 generation                                              | JVM · JS  | 2.13 · 3.x |
| `html`               | Type-safe HTML templates                                            | JVM · JS  | 2.13 · 3.x |
| `datastar`           | Datastar attributes, signals, and SSE events                        | JVM · JS  | 3.x        |
| `http-htmx`          | Typed HTMX attributes                                               | JVM · JS  | 3.x        |
| `jwt`                | JWT signing and verification                                        | JVM · JS  | 2.13 · 3.x |
| `markdown`           | GitHub Flavored Markdown parsing and rendering                      | JVM · JS  | 2.13 · 3.x |
| `telemetry`          | Tracing, logging, and metrics                                       | JVM · JS  | 2.13 · 3.x |
| `telemetry-otel`     | OTLP exporters for `telemetry`                                      | JVM       | 2.13 · 3.x |
| `sql`                | Schema-driven SQL and a JDBC transactor                             | JVM · JS  | 3.x        |
| `sql-zio`            | ZIO integration for `sql`                                           | JVM       | 3.x        |
| `data-migration`     | Typed database schema migrations                                    | JVM · JS  | 3.x        |
| `projection`         | Event-sourced projections                                           | JVM       | 3.x        |
| `codegen`            | Scala code generation IR and emitter                                | JVM       | 2.13 · 3.x |
| `smithy`             | Smithy IDL parser and AST                                           | JVM       | 2.13 · 3.x |

Modules pull in the ZIO Blocks modules they build on, so you only list the ones you use directly.

## Add a Dependency With sbt

On Scala 2.13 or Scala 3.3 to 3.8, use the `zio-blocks-` prefix:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

On Scala 3.9 or newer, use the `zio-blocks-next-` prefix and enable experimental features. The `zio-blocks-next-*` artifacts are compiled with `-experimental`, so code that calls them must be too. Without the flag, the compiler reports that a definition "is marked @experimental":

```scala
scalaVersion := "3.9.0"

libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"

scalacOptions += "-experimental"
```

For Scala.js, write `%%%` instead of `%%`:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "@VERSION@"
```

## Cross-Build for Several Scala Versions

When one build compiles for several Scala versions, let sbt pick the prefix and the `-experimental` flag per version. Put this in `build.sbt`:

```scala
crossScalaVersions := Seq("2.13.18", "3.3.7", "3.9.0")
scalaVersion       := crossScalaVersions.value.head

val zioBlocksVersion = "@VERSION@"

// Scala 3.9+ uses the zio-blocks-next-* artifacts; older versions use zio-blocks-*.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 9 => "zio-blocks-next"
    case _                              => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

libraryDependencies ++= Seq(
  zioBlocks("schema").value,
  zioBlocks("streams").value
)

// The zio-blocks-next-* artifacts require -experimental.
scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 9 => Seq("-experimental")
    case _                              => Seq.empty
  }
}
```

In a Scala.js or `crossProject` build, change `%%` to `%%%` inside `zioBlocks`.

## Add a Dependency With scala-cli

:::warning
scala-cli may report that a module "is outdated, update to 0.017". Ignore the hint: `0.017` is a mistyped February release that Maven ranks above the current versions, and following it downgrades you.
:::

On Scala 2.13 or Scala 3.3 to 3.8:

```scala
//> using scala 3.3.7
//> using dep dev.zio::zio-blocks-schema:@VERSION@
```

On Scala 3.9 or newer:

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-schema:@VERSION@
//> using option -experimental
```

For Scala.js, add the platform directive and put `::` before the version as well, the scala-cli equivalent of `%%%`:

```scala
//> using platform scala-js
//> using dep dev.zio::zio-blocks-schema::@VERSION@
```

## Check That It Compiles

Paste this into your project and run it. If it prints JSON, the dependency is on the classpath:

```scala mdoc:silent
import zio.blocks.schema._

case class Book(title: String, author: String)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

Then encode a book to JSON:

```scala mdoc
Book("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi").toJsonString
```

## Things to Know

- **Scala 3 only.** `sql`, `sql-zio`, `data-migration`, `projection`, `datastar`, and `http-htmx` have no Scala 2.13 artifacts.
- **JVM only.** `schema-avro`, `schema-bson`, `schema-thrift`, `codegen`, `smithy`, `telemetry-otel`, `sql-zio`, and `projection` have no Scala.js artifacts. The `sql` module's JDBC classes are also JVM-only.
- **Third-party dependencies.** Most modules depend only on the Scala standard library. The exceptions are:

  | Module                        | Pulls in                                                       |
  |-------------------------------|----------------------------------------------------------------|
  | `schema-avro`                 | `org.apache.avro:avro`                                         |
  | `schema-bson`                 | `org.mongodb:bson`                                             |
  | `schema-thrift`               | `org.apache.thrift:libthrift`, `jakarta.annotation-api`        |
  | `schema` on Scala.js          | `scala-java-time`, `scala-java-time-tzdb`                      |
  | `async` on Scala 3            | `dotty-cps-async`; also reaches you through `streams` and every module built on it |
  | `sql-zio`                     | `zio`                                                          |
  | `projection`                  | `zio`, `zio-streams`                                           |

- **Scala.js and `async` on Scala 3.9.** The `zio-blocks-next-async` artifact uses native JavaScript `async`/`await`. If the Scala.js linker reports that a feature requires ES2017, set the target in your Scala.js project:

  ```scala
  scalaJSLinkerConfig ~= (_.withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES2017)))
  ```

## Going Further

- [Your First Schema](./your-first-schema.md) is the next step once `zio-blocks-schema` is on the classpath.
- The [reference](../reference/index.md) has a page for every module in the table above.
- Each `*-examples/` directory in the [repository](https://github.com/zio/zio-blocks) is a standalone sbt build that depends on the published artifacts; `schema-examples/build.sbt` is a working copy-paste template.
