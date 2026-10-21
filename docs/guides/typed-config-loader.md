---
id: typed-config-loader
title: "Build a Typed Configuration Loader"
description: "Load a typed case class from YAML, environment overrides and defaults, report every error at once, and see where each value came from."
keywords:
  - "Getting Started"
  - "Typed Configuration"
  - "Layered Configuration"
  - "ConfigSource"
  - "ConfigError"
  - "Secret"
---

Welcome! This tutorial is for Scala developers who want to load their application's settings into a typed value instead of reading strings one key at a time. If you have worked through [Your First Schema](./your-first-schema.md), you have everything you need: we reuse the same idea of deriving a `Schema` for a case class. You don't need any prior experience with configuration libraries to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- How a case class with a derived `Schema` becomes a typed configuration value
- How keys, default values and optional fields decide what a load needs
- How to read configuration from YAML and layer it under environment overrides and over defaults
- How a load reports every error at once instead of stopping at the first one
- How to print where each value came from, with secrets kept out of the output

We learn these concepts by building the configuration loader for a small Scala bookstore service, one step at a time:

1. Describe the configuration as a case class
2. Load it from a map, letting defaults fill the gaps
3. Read the same settings from YAML
4. Layer environment overrides, YAML and defaults
5. Collect every error at once
6. See where each value came from

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the config module and its YAML adapter to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-config"      % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-config-yaml" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-config"      % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-next-config-yaml" % "@VERSION@"
```

Scala 3.7 and newer use the `zio-blocks-next-*` artifact names; the code in this tutorial is the same for every Scala version.

## 2. Background: Configuration Is Just Typed Data

A service reads its settings from several places. A file checked into the repository holds the values every deployment shares, a deployment adds its own file, and the environment supplies passwords and last-minute overrides. Most programs glue these together by hand: read a string, parse it as a number, fall back to another place when it is missing, and throw on the first problem. Each step is one more chance to get a key name or a type wrong.

ZIO Blocks treats configuration as typed data, like any other data with a `Schema`. Every source — a map, a YAML document, the environment — is flattened into the same simple shape: dot-separated keys such as `db.host` pointing to strings. Sources stack on top of each other, so a key missing from one is looked up in the next. One call then walks your case class, reads one key per field, converts each string to the field's type, and either returns the finished value or every problem it found. Along the way it remembers which source answered each key, so "why is the port 9090?" always has an answer.

## 3. Describe the Configuration as a Case Class

Our bookstore service needs a name, a port, the title it features on its home page, a database connection, a list of tags and an optional discount. We describe all of that as ordinary case classes and derive a `Schema` for the top-level one, exactly as we did for `Book` in [Your First Schema](./your-first-schema.md):

```scala mdoc:silent
import zio.blocks.config._
import zio.blocks.schema.Schema

case class DbConfig(host: String, port: Int = 5432, user: String, password: Secret)

case class BookstoreConfig(
  name: String,
  port: Int = 8080,
  featuredTitle: String,
  db: DbConfig,
  tags: List[String],
  discountPercent: Option[Int]
)

object BookstoreConfig {
  implicit val schema: Schema[BookstoreConfig] = Schema.derived
}
```

The code above:

- `import zio.blocks.config._` brings in `Config`, [`ConfigSource`](../reference/config/config-source.md), `Secret` and the rest of the config module.
- `DbConfig` groups the database settings. `port: Int = 5432` gives the database port a default value.
- `password: Secret` stores the password in a `Secret` — a wrapper around a `String` whose `Secret#toString` never shows the value.
- `BookstoreConfig` nests a `DbConfig`, gives its own `port` a default of `8080`, holds a `List[String]` of tags, and makes `discountPercent` an `Option[Int]` because not every deployment runs a discount.
- `implicit val schema` derives the schema at compile time. `Schema.derived` also derives the schemas for `DbConfig`, `List[String]` and `Option[Int]`, and `Secret` already ships with one.

The configuration is a plain value, so we can build one by hand and print it:

```scala mdoc
println(BookstoreConfig("Scala Bookstore", 8080, "Zionomicon", DbConfig("localhost", 5432, "bookstore", Secret("dev-password")), List("scala"), None))
```

The code above prints a hand-built `BookstoreConfig`. Notice that the password appears as `<secret>`: a `Secret` keeps its value out of every log line that prints the configuration. That's all the description the loader needs. Next, we let it build this value for us.

## 4. Load It From a Map, Letting Defaults Fill the Gaps

The loader reads from a `ConfigSource`: a flat set of keys and string values. The simplest one is `ConfigSource.fromMap`, which wraps a `Map[String, String]` and takes a name for the source. The keys follow the shape of the case class: a nested field such as `db.host` is written with a dot, and list elements are numbered from zero, as in `tags.0`.

Let's write the values every deployment of our bookstore shares and call the source `defaults`:

```scala mdoc:silent
val defaultSettings = Map(
  "name"            -> "Scala Bookstore",
  "featuredTitle"   -> "Programming in Scala",
  "db.host"         -> "localhost",
  "db.user"         -> "bookstore",
  "db.password"     -> "dev-password",
  "tags.0"          -> "scala",
  "tags.1"          -> "books",
  "discountPercent" -> "10"
)

val defaults = ConfigSource.fromMap(defaultSettings, "defaults")
```

The code above:

- `defaultSettings` is an ordinary `Map[String, String]`. Every value is a string, even the numbers — converting them is the loader's job.
- `"db.host"` and `"db.password"` fill fields of the nested `DbConfig`, and `"tags.0"` and `"tags.1"` are the two elements of `tags`.
- `ConfigSource.fromMap(defaultSettings, "defaults")` turns the map into a source named `defaults`. The name shows up later in error messages and in the provenance table.

`Config.load` derives a decoder from the schema and runs it. A load can fail, so it returns an `Either`: `Right` with the configuration, or `Left` with a non-empty list of errors. Let's write a small helper that prints either side, and load our defaults:

```scala mdoc:silent
def report(result: Either[::[ConfigError], BookstoreConfig]): Unit =
  result match {
    case Right(config) => println(config)
    case Left(errors)  => errors.foreach(error => println(error.message))
  }
```

Now let's load our defaults with it:

```scala mdoc
report(Config.load[BookstoreConfig](defaults))
```

The code above:

- `Config.load[BookstoreConfig](defaults)` reads one key per field and converts each string to the field's type.
- `report` prints the configuration on `Right`, and the message of each error on `Left`.

Notice the two ports in the output. Our map has no `port` or `db.port` key, so the loader uses the default values from the case classes: `8080` and `5432`. The string `"10"` becomes the number `10`, wrapped in `Some` because `discountPercent` is optional.

What happens when an optional field has no key at all? Let's remove `discountPercent` from the map and load again:

```scala mdoc
report(Config.load[BookstoreConfig](ConfigSource.fromMap(defaultSettings - "discountPercent", "defaults")))
```

The code above loads the same settings without the `discountPercent` key. Notice that the load still succeeds and `discountPercent` is `None`. A missing key is an error only for a field that has neither a default value nor an `Option` type.

## 5. Read the Same Settings From YAML

Deployments usually describe their settings in a file. The `config-yaml` module adds `ConfigSource.fromYaml`, which parses YAML text and flattens it into the same dot-separated keys we wrote by hand: a nested mapping becomes `db.host`, and a sequence becomes `tags.0`, `tags.1` and so on. It takes the YAML as a `String`, so our program reads the file and passes its content in. Here we keep the YAML in a string to make the example self-contained:

```scala mdoc:silent
import zio.blocks.config.yaml._

val bookstoreYaml =
  """name: Functional Bookshelf
    |featuredTitle: Zionomicon
    |db:
    |  host: db.staging.internal
    |  user: bookshelf_app
    |tags:
    |  - scala
    |  - functional-programming
    |  - zio
    |""".stripMargin

val yaml: ConfigSource = ConfigSource.fromYaml(bookstoreYaml, "bookstore.yaml") match {
  case Right(source) => source
  case Left(error)   => throw error
}
```

The code above:

- `import zio.blocks.config.yaml._` adds the `ConfigSource.fromYaml` method.
- `bookstoreYaml` holds the file content: a store name, a featured title, a nested `db` mapping and a sequence of three tags.
- `ConfigSource.fromYaml(bookstoreYaml, "bookstore.yaml")` parses the text and returns `Either[ConfigError, ConfigSource]`, because the text might not be valid YAML. We name the source after the file it would come from.
- `case Left(error) => throw error` stops the program if the YAML doesn't parse. A `ConfigError` is also a `Throwable`, so it can be thrown as it is.

:::tip
To read a real file, pass its content to `ConfigSource.fromYaml`, for example `java.nio.file.Files.readString(java.nio.file.Path.of("bookstore.yaml"))`.
:::

Now we load the configuration from the YAML source alone:

```scala mdoc
report(Config.load[BookstoreConfig](yaml))
```

The load fails, and the message says exactly why: the YAML has no `db.password` key. That is on purpose — a password doesn't belong in a file that is checked into a repository. In the next section, we let another source supply it.

## 6. Layer Environment Overrides, YAML and Defaults

`ConfigSource#orElse` stacks two sources. `primary.orElse(fallback)` looks up each key in `primary` first and asks `fallback` only for keys that `primary` doesn't have. The lookup happens key by key, so one configuration value can mix fields from several sources. Chaining `ConfigSource#orElse` builds a whole stack, with the highest priority on the left.

In production, the top layer is the environment. ZIO Blocks reads it with `EnvSource`, which looks up a key such as `db.password` as an environment variable such as `DB_PASSWORD`. A documentation page can't set environment variables, so we stand in for the environment with a map source named `env` that holds two keys:

```scala mdoc:silent
val env = ConfigSource.fromMap(Map("db.password" -> "s3cr3t-from-env", "port" -> "9090"), "env")

val layered = env.orElse(yaml).orElse(defaults)
```

The code above:

- `env` holds the password and the port, the two values a deployment would export as environment variables.
- `env.orElse(yaml).orElse(defaults)` builds a three-layer stack: environment first, then the YAML file, then the defaults.

Let's load the layered source:

```scala mdoc
report(Config.load[BookstoreConfig](layered))
```

Notice where each field came from. The port `9090` and the password come from `env`. The store name, featured title, database host and user and all three tags come from the YAML file. The YAML has no `discountPercent`, so `Some(10)` comes from the defaults. The database port has no key in any layer, so it falls back to the case-class default of `5432`.

:::note
In a real application, replace the stand-in with `EnvSource` and give it a prefix: `EnvSource.prefix("bookstore").orElse(yaml).orElse(defaults)`. `ConfigSource#prefix` puts `bookstore.` in front of every key, and `EnvSource` turns the result into a variable name by replacing dots with underscores and upper-casing it. So `db.password` is read from `BOOKSTORE_DB_PASSWORD` and `port` from `BOOKSTORE_PORT`, and unrelated variables such as `PATH`, or a `PORT` set for another program, never reach the configuration.
:::

## 7. Collect Every Error at Once

Configuration mistakes rarely come alone. A loader that stops at the first one makes us fix, redeploy and fail again for each mistake. `Config.load` keeps going instead: it checks every field and returns every error it found.

Let's break two things at once. We type the port as a word, and we forget to export the password:

```scala mdoc:silent
val brokenEnv = ConfigSource.fromMap(Map("port" -> "ninety"), "env")

val defaultsWithoutPassword = ConfigSource.fromMap(defaultSettings - "db.password", "defaults")

val brokenLayers = brokenEnv.orElse(yaml).orElse(defaultsWithoutPassword)
```

Let's load the broken layers:

```scala mdoc
report(Config.load[BookstoreConfig](brokenLayers))
```

The code above:

- `brokenEnv` sets `port` to `"ninety"`, which is not an `Int`.
- `defaultsWithoutPassword` is our defaults map without `db.password`, so no layer has a password.
- `Config.load` reports both problems in one result: the invalid port value and the missing password key.

Each message names the key, the problem and the source that was searched. For a layered source, that name is the whole stack, `env|bookstore.yaml|defaults`, because the loader asked the stack as a whole. The invalid value also ends with the reason the conversion failed, here the JDK's own `For input string: "ninety"`.

Notice that the bad port is reported even though `port` has a default value of `8080`. A default replaces a missing key, never a broken one, so a typo can't silently fall back to a default.

Sometimes a program has nothing better to do than stop when its configuration is broken — for example, at startup. `Config.loadOrThrow` returns the configuration directly, or throws a `ConfigLoadException` whose message is a report of the errors. Let's call it on the YAML source from the previous section, which has no password:

```scala mdoc
try Config.loadOrThrow[BookstoreConfig](yaml)
catch { case e: ConfigLoadException => println(e.getMessage) }
```

The code above calls `Config.loadOrThrow` on the YAML source alone and prints the exception's message — the same report a service that fails at startup would log.

## 8. See Where Each Value Came From

When a value surprises us, the first question is "which source set this?". `Config.loadWithProvenance` loads the configuration and also keeps the source stack, so we can ask where each key was found. Its `ProvenanceMap#dump` method prints a table of every key, its value and the source that answered:

```scala mdoc
Config.loadWithProvenance[BookstoreConfig](layered) match {
  case Right(provenance) => println(provenance.dump())
  case Left(errors)      => errors.foreach(error => println(error.message))
}
```

The code above:

- `Config.loadWithProvenance[BookstoreConfig](layered)` returns `Either[::[ConfigError], ProvenanceMap[BookstoreConfig]]`. On success, `provenance.value` holds the loaded configuration.
- `provenance.dump()` builds the table as a `String`, sorted by key.

Notice two things in the table. First, each key names the layer that supplied it: `env`, `bookstore.yaml` or `defaults`. Second, the value of `db.password` is shown as `<secret>`. `ProvenanceMap#dump` hides the value of every key whose name looks sensitive — names containing words such as `password`, `secret` or `token`. Redaction goes by the key name, not by the field's type, so give every secret setting a name that says what it is.

The table lists keys that a source supplied. The database port came from the case-class default, not from a source, so it has no row.

Inside our program, we still need the real password to connect to the database. `Secret.unwrap` returns it, and that is the only way to read it:

```scala mdoc:silent
val config = Config.loadOrThrow[BookstoreConfig](layered)
```

Next, we print the password field and the length of the real password:

```scala mdoc
println(config.db.password)
println(Secret.unwrap(config.db.password).length)
```

The code above loads the layered configuration, prints the password field, which shows `<secret>`, and then prints the length of the real password that `Secret.unwrap` returns — a value our code can use without ever logging the password itself.

## 9. Putting It Together

This example combines all the concepts we've learned — a typed configuration with defaults, an optional field and a secret, a YAML source layered between the real environment and the defaults, error reporting and the provenance table. It reads the environment with `EnvSource.prefix("bookstore")`, as the note in section 6 recommends, so the provenance table lists only the `BOOKSTORE_*` variables:

```scala mdoc:embed:config-examples/src/main/scala/typedconfigloader/CompleteExample.scala
```

## 10. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `config-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/config-examples
```

Run them in order to follow the tutorial step by step.

### Describe the Configuration as a Case Class

This example defines `BookstoreConfig` and `DbConfig`, derives the schema, and prints a hand-built configuration with its password redacted.

<details>
  <summary>config-examples/src/main/scala/typedconfigloader/Concept1DescribeExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/typedconfigloader/Concept1DescribeExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain typedconfigloader.Concept1DescribeExample"
```

### Load It From a Map

This example loads the configuration from a map of defaults and shows how default values and an optional field fill missing keys.

<details>
  <summary>config-examples/src/main/scala/typedconfigloader/Concept2LoadFromMapExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/typedconfigloader/Concept2LoadFromMapExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain typedconfigloader.Concept2LoadFromMapExample"
```

### Read the Same Settings From YAML

This example parses YAML with `ConfigSource.fromYaml` and loads it alone, which reports the missing password.

<details>
  <summary>config-examples/src/main/scala/typedconfigloader/Concept3YamlExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/typedconfigloader/Concept3YamlExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain typedconfigloader.Concept3YamlExample"
```

### Layer Environment Overrides, YAML and Defaults

This example stacks an environment stand-in, the YAML source and the defaults with `ConfigSource#orElse` and loads the result.

<details>
  <summary>config-examples/src/main/scala/typedconfigloader/Concept4LayeringExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/typedconfigloader/Concept4LayeringExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain typedconfigloader.Concept4LayeringExample"
```

### Collect Every Error at Once

This example loads layers with a bad port and no password, prints every error, and then shows the report from `Config.loadOrThrow`.

<details>
  <summary>config-examples/src/main/scala/typedconfigloader/Concept5ErrorsExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/typedconfigloader/Concept5ErrorsExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain typedconfigloader.Concept5ErrorsExample"
```

### See Where Each Value Came From

This example prints the provenance table for the layered configuration and reads the password with `Secret.unwrap`.

<details>
  <summary>config-examples/src/main/scala/typedconfigloader/Concept6ProvenanceExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/typedconfigloader/Concept6ProvenanceExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain typedconfigloader.Concept6ProvenanceExample"
```

### The Complete Example

The complete example from "Putting It Together" reads the real environment with `EnvSource.prefix("bookstore")`, so you can override settings with `BOOKSTORE_*` variables when you run it:

```bash
sbt "runMain typedconfigloader.CompleteExample"
BOOKSTORE_DB_PASSWORD=s3cr3t BOOKSTORE_PORT=9090 sbt "runMain typedconfigloader.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 11. What You've Learned

In this tutorial, you learned:

- How a case class with a derived `Schema` describes a typed configuration, and how `Secret` keeps a password out of printed values
- How `Config.load` reads dot-separated keys from a `ConfigSource`, and how default values and `Option` fields decide which keys are required
- How `ConfigSource.fromYaml` flattens YAML into the same keys, and how `ConfigSource#orElse` layers environment overrides, YAML and defaults key by key
- How `Config.load` returns every error at once, and how `Config.loadOrThrow` turns them into one report at startup
- How `Config.loadWithProvenance` and `ProvenanceMap#dump` show which source supplied each value, with sensitive values redacted

You now have a solid foundation in typed configuration with ZIO Blocks. The next step is to explore the other sources and formats, and to wire configuration into the rest of your application.

## 12. Where to Go Next

- **Want every way to build and combine sources?** Read [Config Source](../reference/config/config-source.md), which covers `EnvSource`, `SysPropSource`, `ConfigSource#prefix` and key-format mapping for environment-style names.
- **Curious how fields map to keys?** [Config Decoder](../reference/config/config-decoder.md) documents the rules for nested records, sealed traits, lists and maps.
- **Want to handle errors by kind?** [Errors](../reference/config/errors.md) lists every `ConfigError` case and how errors are combined.
- **Using JSON or HOCON instead of YAML?** [File Formats](../reference/config/formats.md) covers all three adapters.
- **Wiring configuration into a dependency graph?** `Config.wire[A]` turns a `ConfigSource` into a typed value as a node in a `scope` wire graph; the [Configuration & Feature Flags](../reference/config/index.md) overview shows it in action, and [How to Feed Configuration into a Wire Dependency Graph](./feed-config-into-a-wire-graph.md) walks through it end to end.
- **Need runtime switches?** [Flags](../reference/config/flags.md) and [Rollout](../reference/config/rollout.md) cover feature flags with percentage-based rollouts, built on the same sources.
- **Coming from PureConfig, Typesafe Config or zio-config?** [Coming from PureConfig, Typesafe Config or zio-config](./config-library-migration.md) maps the concepts and APIs you already know onto the ones in this tutorial.
