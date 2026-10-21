---
id: feed-config-into-a-wire-graph
title: "How to Feed Configuration into a Wire Dependency Graph"
description: "Give each service in a Wire graph its own typed, checked settings from one layered ConfigSource, and shut everything down in order."
keywords:
  - "Dependency Injection"
  - "Typed Configuration"
  - "Config.wire"
  - "Resource.from"
  - "ConfigSource"
  - "Scope"
---

In this guide, we build a small bookstore backend from three services, `Db`, `InventoryRepo` and `Catalog`, and let each one receive its own typed settings through the dependency graph. By the end, you will have one `ConfigSource` that layers environment variables over a YAML file over defaults, a graph that hands each service the settings section it asks for, a shutdown that closes the database last, a startup that stops with a full report when a setting is wrong, and tests that need no files or environment variables.

We read one settings section first, then build the whole graph, look at the shutdown order, break a setting on purpose, and finish with tests:

- [Reading One Settings Section](#reading-one-settings-section)
- [Building the Whole Graph](#building-the-whole-graph)
- [Closing Services in Reverse Order](#closing-services-in-reverse-order)
- [Stopping at Startup on a Bad Setting](#stopping-at-startup-on-a-bad-setting)
- [Testing Without Files or Environment Variables](#testing-without-files-or-environment-variables)

## The Problem

A service graph needs every service built after its dependencies, with the right settings, and closed in the opposite order. Writing that by hand mixes three jobs in `main`: reading raw strings, constructing objects in the right order, and closing them in reverse.

Without a configuration library or a dependency-injection library, the bookstore's `main` usually looks like this:

```scala mdoc:compile-only
final case class DbSettings(url: String, user: String, password: String, poolSize: Int)
final case class CatalogSettings(featuredTitle: String, pageSize: Int)

class Db(settings: DbSettings) extends AutoCloseable {
  def close(): Unit = ()
}
class InventoryRepo(db: Db)
class Catalog(repo: InventoryRepo, settings: CatalogSettings) extends AutoCloseable {
  def close(): Unit = ()
}

object Main {
  private def setting(name: String, default: => String): String =
    sys.env.getOrElse(name, default)

  def main(args: Array[String]): Unit = {
    val db = new Db(
      DbSettings(
        setting("DB_URL", "jdbc:postgresql://localhost:5432/bookstore"),
        setting("DB_USER", "bookstore"),
        setting("DB_PASSWORD", sys.error("DB_PASSWORD is not set")),
        setting("DB_POOL_SIZE", "10").toInt
      )
    )
    try {
      val repo    = new InventoryRepo(db)
      val catalog = new Catalog(
        repo,
        CatalogSettings(
          setting("CATALOG_FEATURED_TITLE", "Zionomicon"),
          setting("CATALOG_PAGE_SIZE", "3").toInt
        )
      )
      try println("serving") // the application runs here
      finally catalog.close()
    } finally db.close()
  }
}
```

Each new service adds another hand-placed `try`/`finally`, and moving a constructor call means rechecking the closing order. A `CATALOG_PAGE_SIZE=ten` throws `NumberFormatException: For input string: "ten"`, which names neither the key nor the source, and it throws only after the database is already open, so you find out about the next bad setting on the next restart. Testing `Catalog` means exporting environment variables.

In this guide, the config module turns each settings section into a typed case class with every error reported at once, and the scope module builds the graph from the constructors and closes it in reverse order.

## Prerequisites

Add the config module and its YAML format to your `build.sbt`. The config module depends on the scope and context modules, so `Scope`, `Resource`, `Wire` and `Context` come with it:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-config"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-config-yaml" % "@VERSION@"
)

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-config"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-config-yaml" % "@VERSION@"
)
```

This guide assumes you have loaded a case class from a layered `ConfigSource`, as in [Build a Typed Configuration Loader](./typed-config-loader.md), and that you have built a `Resource` and used it inside `Scope.global.scoped`, as in [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md). The snippets below share these imports:

```scala mdoc:silent
import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.context.Context
import zio.blocks.schema.Schema
import zio.blocks.scope._
```

## The Bookstore's Services and Their Settings

The bookstore has a database pool, a repository on top of it, and a catalog that renders the front page. `Db` and `Catalog` each take a settings case class as a constructor parameter, and each settings class derives a `Schema`, which is all the config module needs to decode it:

```scala mdoc:silent
final case class DbConfig(url: String, user: String, password: Secret, poolSize: Int)
object DbConfig {
  implicit val schema: Schema[DbConfig] = Schema.derived
}

final case class CatalogConfig(featuredTitle: String, pageSize: Int)
object CatalogConfig {
  implicit val schema: Schema[CatalogConfig] = Schema.derived
}

class Db(config: DbConfig) extends AutoCloseable {
  println(s"Db: opening ${config.poolSize} connections to ${config.url} as ${config.user}, password ${config.password}")

  def titles(): List[String] =
    List("Zionomicon", "Programming in Scala", "Functional Programming in Scala")

  def close(): Unit = println("Db: closing the connection pool")
}

class InventoryRepo(db: Db) {
  println("InventoryRepo: ready")

  def inStock(): List[String] = db.titles()
}

class Catalog(repo: InventoryRepo, config: CatalogConfig) extends AutoCloseable {
  println(s"Catalog: featuring ${config.featuredTitle}")

  def frontPage(): String =
    s"Featured: ${config.featuredTitle} | In stock: ${repo.inStock().take(config.pageSize).mkString(", ")}"

  def close(): Unit = println("Catalog: flushing the page cache")
}
```

The services print when they start and stop, so the output below shows the order. The password is a `Secret`, so printing it shows `<secret>` instead of the value.

:::tip[One settings type per service]
The graph finds a constructor's arguments by type. Give each service its own settings case class instead of loose `String` or `Int` parameters: two `String` parameters for a URL and a user name can't be told apart by type. [How to Fix Common Scope and Wire Compile Errors](./fix-scope-and-wire-compile-errors.md) shows what the macro reports for such a constructor.
:::

## Reading One Settings Section

`Config.wire[A](prefix)` turns one settings section into a wire, the scope module's recipe for building a value. Its type, `Wire.Shared[ConfigSource, DbConfig]`, says that it needs a `ConfigSource` and produces a `DbConfig`; `Shared` means the graph builds it once and hands the same value to every service that asks for it. The prefix `"db"` makes the wire read `db.url`, `db.user` and so on. A wire is only a recipe, so we run it by hand with `Wire.Shared#make`, passing a scope and a `Context` that holds the source. The source holds the `db` section:

```scala mdoc:silent
val sectionSource = ConfigSource.fromMap(
  Map(
    "db.url"      -> "jdbc:postgresql://localhost:5432/bookstore",
    "db.user"     -> "bookstore",
    "db.password" -> "change-me",
    "db.poolSize" -> "10"
  ),
  "sections"
)
```

Running the wire prints the decoded section, with the password hidden:

```scala mdoc
Scope.global.scoped { scope =>
  val dbConfig = Config.wire[DbConfig]("db").make(scope, Context[ConfigSource](sectionSource))
  println(dbConfig)
}
```

`Config.wire[A]` without a prefix reads the fields from the top level of the source. Inside a graph, you never call `Wire.Shared#make` yourself: `Resource.from` does it, as the next section shows.

## Building the Whole Graph

A real service reads its settings from several places, so we layer them as [Build a Typed Configuration Loader](./typed-config-loader.md) does: environment variables first, then a YAML file, then defaults. In production, the environment layer is `EnvSource`, keyed to the `BOOKSTORE_` prefix and converted to upper snake case, so `catalog.featuredTitle` is read from `BOOKSTORE_CATALOG_FEATURED_TITLE`:

```scala mdoc:silent
val productionEnv: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")
```

This page must give the same output on every machine, so it stands in a map for the environment, holding one exported variable, `BOOKSTORE_DB_PASSWORD`. The YAML file and the defaults supply the rest:

```scala mdoc:silent
val env = ConfigSource.fromMap(Map("db.password" -> "change-me"), "env")

val bookstoreYaml =
  """db:
    |  url: jdbc:postgresql://localhost:5432/bookstore
    |  user: bookstore
    |catalog:
    |  featuredTitle: Zionomicon
    |""".stripMargin

val yaml = ConfigSource.fromYaml(bookstoreYaml, "bookstore.yaml") match {
  case Right(source) => source
  case Left(error)   => throw error
}

val defaults = ConfigSource.fromMap(Map("db.poolSize" -> "10", "catalog.pageSize" -> "3"), "defaults")

val settings = env.orElse(yaml).orElse(defaults)
```

`Resource.from[Catalog]` builds the whole graph from `Catalog`'s constructor. The wires passed to it supply what the constructors can't: `Wire(source)` hands the graph the `ConfigSource` value, and the two `Config.wire` calls turn it into `DbConfig` and `CatalogConfig`. The macro derives a wire for `Db` and `InventoryRepo` from their constructors:

```scala mdoc:silent
def catalogResource(source: ConfigSource): Resource[Catalog] =
  Resource.from[Catalog](
    Wire(source),
    Config.wire[DbConfig]("db"),
    Config.wire[CatalogConfig]("catalog")
  )
```

Allocating the resource in a scope builds every service, and `$` calls the catalog inside the scope:

```scala mdoc
val frontPage = Scope.global.scoped { scope =>
  import scope._
  val catalog = allocate(catalogResource(settings))
  $(catalog)(_.frontPage())
}

println(frontPage)
```

The password came from the environment stand-in, the URL, user and featured title from YAML, and the pool and page sizes from the defaults. Neither `Db` nor `Catalog` knows where its settings came from.

:::warning[Make the graph a `def`, not a `val`]
A `Resource` built by `Resource.from` is shared: the first allocation builds the services, and closing the last scope that uses them closes them for good. A `val` holding it works once, and a second `Scope.global.scoped` that allocates the same `val` throws `IllegalStateException: Cannot allocate from a destroyed shared resource`. A `def`, as above, builds a fresh resource for each scope.
:::

## Closing Services in Reverse Order

The scope closes what it opened when the `scoped` block ends, in the reverse order of opening. The graph opens `Db` first because everything else depends on it, so `Db` closes last, after `Catalog` has flushed its cache. The output above shows the order; here it is again with the work in the middle:

```scala mdoc
Scope.global.scoped { scope =>
  import scope._
  val catalog = allocate(catalogResource(settings))
  println("Serving: " + $(catalog)(_.frontPage()))
}
```

You write no `close()` calls. The macro registers `close()` for every class that extends `AutoCloseable` and is built from its constructor, as `Db` and `Catalog` are here. `InventoryRepo` holds no resources of its own, so it has nothing to close.

:::warning[Let the macro build anything you need to close]
Build `Db` from its constructor, as above, not with `Wire.Shared.fromFunction`. That function runs your code and registers nothing, so a `Db` built there is never closed. `Config.wire` is built that way, which is fine because a settings case class holds no resources. Also keep `Db` and `Catalog` inside `scoped`: return plain data such as the front-page `String`, never the service, which is closed once the block ends.
:::

## Stopping at Startup on a Bad Setting

`Config.wire` decodes its section while the graph is built, and when anything is missing or malformed it throws `ConfigLoadException` with every error in that section. Here the catalog section has a page size of `two` and no featured title, layered over `sectionSource`, the complete `db` section from [Reading One Settings Section](#reading-one-settings-section):

```scala mdoc:silent
val badCatalog = ConfigSource.fromMap(Map("catalog.pageSize" -> "two"), "bad-catalog").orElse(sectionSource)
```

The scope opens `Db` and `InventoryRepo`, stops at `CatalogConfig`, and closes what it had opened before the exception leaves `scoped`:

```scala mdoc
try {
  Scope.global.scoped { scope =>
    import scope._
    val catalog = allocate(catalogResource(badCatalog))
    $(catalog)(_.frontPage())
  }
} catch {
  case e: ConfigLoadException => println(e.getMessage)
}
```

The application never serves a request with half its settings. When the bad setting is in the `db` section, the graph stops before it opens `Db`, so nothing needs closing.

Two details of the report trip readers up. The keys are relative to the section's prefix: `pageSize` means `catalog.pageSize`, and the report doesn't name the section. The count in the first line also reads `1 error(s)` even when more lines follow, because the errors of one case class are collected into a single composite error; read the lines, not the count.

## Testing Without Files or Environment Variables

The graph depends only on the `ConfigSource` passed to `catalogResource`, so a test passes a `ConfigSource.fromMap` instead of the production layers. A test usually changes one or two settings, so it puts a small map in front of the defaults and leaves the rest alone, as `frontPageWith` does:

```scala mdoc:silent
def frontPageWith(overrides: Map[String, String]): String = {
  val testSettings = ConfigSource.fromMap(overrides, "test").orElse(settings)
  Scope.global.scoped { scope =>
    import scope._
    val catalog = allocate(catalogResource(testSettings))
    $(catalog)(_.frontPage())
  }
}
```

Each call builds and closes its own graph, so tests don't share services:

```scala mdoc
val page = frontPageWith(Map("catalog.featuredTitle" -> "Functional Programming in Scala", "catalog.pageSize" -> "1"))

println(page == "Featured: Functional Programming in Scala | In stock: Zionomicon")
```

In a test suite, `settings` would be a map with every key, so the test reads no file and no environment variable at all.

## Putting It Together

The complete example layers the real environment, a YAML document and defaults, builds the bookstore graph, prints the front page and the shutdown order, and shows the report for a bad setting:

```scala mdoc:embed:config-examples/src/main/scala/feedconfigintoawiregraph/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `config-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/config-examples
```

Each file below matches one section of this guide.

### Reading One Settings Section

<details>
  <summary>config-examples/src/main/scala/feedconfigintoawiregraph/Step1ReadOneSection.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/feedconfigintoawiregraph/Step1ReadOneSection.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain feedconfigintoawiregraph.Step1ReadOneSection"
```

### Building the Whole Graph

<details>
  <summary>config-examples/src/main/scala/feedconfigintoawiregraph/Step2BuildTheGraph.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/feedconfigintoawiregraph/Step2BuildTheGraph.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain feedconfigintoawiregraph.Step2BuildTheGraph"
```

### Closing Services in Reverse Order

<details>
  <summary>config-examples/src/main/scala/feedconfigintoawiregraph/Step3ShutdownOrder.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/feedconfigintoawiregraph/Step3ShutdownOrder.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain feedconfigintoawiregraph.Step3ShutdownOrder"
```

### Stopping at Startup on a Bad Setting

<details>
  <summary>config-examples/src/main/scala/feedconfigintoawiregraph/Step4BadSetting.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/feedconfigintoawiregraph/Step4BadSetting.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain feedconfigintoawiregraph.Step4BadSetting"
```

### Testing Without Files or Environment Variables

<details>
  <summary>config-examples/src/main/scala/feedconfigintoawiregraph/Step5Testing.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/feedconfigintoawiregraph/Step5Testing.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain feedconfigintoawiregraph.Step5Testing"
```

### The Complete Example

<details>
  <summary>config-examples/src/main/scala/feedconfigintoawiregraph/CompleteExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/feedconfigintoawiregraph/CompleteExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory, optionally overriding a setting through the environment:

```bash
BOOKSTORE_CATALOG_FEATURED_TITLE="Programming in Scala" sbt "runMain feedconfigintoawiregraph.CompleteExample"
```

To check that every example compiles without running it, use:

```bash
sbt compile
```

## Going Further

- [Configuration & Feature Flags](../reference/config/index.md#wiring-config-into-a-dependency-graph) covers `Config.wire` alongside `Config.load` and `Config.loadOrThrow`.
- [Wire](../reference/resource-management/wire.md) covers `Wire.shared`, `Wire.unique`, `Wire.Shared.fromFunction` and how `Resource.from` resolves a graph.
- [How to Fix Common Scope and Wire Compile Errors](./fix-scope-and-wire-compile-errors.md) explains each error the graph macro can report, such as a missing dependency or two parameters of the same type.
- [Coming from cats-effect Resource, ZIO Scope/ZLayer, macwire or Using](./resource-and-di-migration.md) maps those libraries onto `Resource`, `Scope` and `Wire`.
- [How to Roll Out a Feature Gradually with Flags](./roll-out-feature-gradually.md) adds settings that change at runtime, which a `Config.wire` value read once at startup can't do.
