---
id: config-library-migration
title: "Coming from PureConfig, Typesafe Config or zio-config"
description: "Move a PureConfig, Typesafe Config or zio-config application to ZIO Blocks config while keeping its existing application.conf."
keywords:
  - "Configuration Migration"
  - "HOCON"
  - "PureConfig"
  - "Typesafe Config"
  - "zio-config"
  - "ConfigSource"
---

In this guide, we move a Scala bookstore service that loads its settings with PureConfig, Typesafe Config or zio-config over to the ZIO Blocks `config` module. By the end, the service reads the `application.conf` it already ships with `config-hocon`, ZIO Blocks' own HOCON parser, takes overrides from environment variables, reports every configuration error at once, and no longer depends on Typesafe Config at all.

We take an incremental approach: first we read the unchanged file, then we map its kebab-case keys to our camelCase fields, replace its `${?VAR}` environment substitutions with an environment layer, and check which other HOCON features carry over. Last, we compare error reports and keep the database password out of the logs.

This is a migration recipe, not an introduction. If you have never loaded configuration with ZIO Blocks, work through [Build a Typed Configuration Loader](./typed-config-loader.md) first: it teaches sources, layering, defaults and provenance with the same bookstore, and this guide builds on it.

**What we will cover:**

- Which ZIO Blocks call replaces each PureConfig, Typesafe Config and zio-config call
- Reading an existing `application.conf` with `config-hocon`, from a string or from a file with includes
- Keeping kebab-case keys such as `featured-title` with `KeyFormat.KebabCase`
- Replacing `${?DB_PASSWORD}` with an `EnvSource` layer, and the two environment pitfalls to avoid
- The HOCON features that behave differently, and what to change in the file
- Reading every error at once, and keeping secrets out of logs
- What works differently: no effect system, no reload, and Scala.js support

## The Problem

A configuration loader written against PureConfig, Typesafe Config or zio-config ties the service to Typesafe Config, the Java library that parses HOCON underneath all three. Typesafe Config runs only on the JVM, and its README describes it as feature complete, with changes now rare. The Scala code around it has its own costs:

- **Typesafe Config** reads one key at a time through getters such as `getString` and `getInt`. Every key is spelled by hand, and the first problem throws a `ConfigException`, so a deployment with three mistakes fails three times.
- **PureConfig** derives readers, but its Scala 3 derivation has changed between releases: `derives ConfigReader` needed an extra import until 0.17.7, and field-name hints need the separate `pureconfig-generic-scala3` module. Teams moving to Scala 3 have found the documentation out of step with the code, as in [this Scala Users thread](https://users.scala-lang.org/t/scala-3-migration-pureconfig/12096).
- **zio-config** loads configuration as a ZIO effect, so reading a file needs a ZIO runtime, and its derivation (`zio-config-magnolia`) and HOCON (`zio-config-typesafe`) modules are published for the JVM only.

For example, this is the hand-written Typesafe Config loader for our bookstore (old code, not compiled on this page):

```scala
// Before: Typesafe Config 1.4.9
import com.typesafe.config.{Config, ConfigFactory}
import scala.jdk.CollectionConverters._

def load(root: Config): BookstoreConfig = {
  val c  = root.getConfig("bookstore")
  val db = c.getConfig("db")
  BookstoreConfig(
    name            = c.getString("name"),
    port            = c.getInt("port"),
    featuredTitle   = c.getString("featured-title"),
    db              = DbConfig(db.getString("host"), db.getInt("port"), db.getString("user"), db.getString("password")),
    tags            = c.getStringList("tags").asScala.toList,
    discountPercent = if (c.hasPath("discount-percent")) Some(c.getInt("discount-percent")) else None
  )
}

val config = load(ConfigFactory.load()) // throws ConfigException.Missing on the first missing key
```

Every field is read by name, a renamed field compiles but fails at startup, and the optional discount needs its own `hasPath` check. ZIO Blocks replaces all of this with one call that walks a case class with a derived `Schema`, runs the same way on Scala 2.13, Scala 3 and Scala.js, and parses HOCON without Typesafe Config.

## Prerequisites

### Dependency Changes

Remove the old configuration dependencies:

```scala
// Before: pick the lines your project has
libraryDependencies += "com.github.pureconfig" %% "pureconfig-core"     % "0.17.11" // Scala 3
libraryDependencies += "com.github.pureconfig" %% "pureconfig"          % "0.17.11" // Scala 2.13
libraryDependencies += "com.typesafe"           % "config"              % "1.4.9"
libraryDependencies += "dev.zio"               %% "zio-config"          % "4.1.0"
libraryDependencies += "dev.zio"               %% "zio-config-magnolia" % "4.1.0"
libraryDependencies += "dev.zio"               %% "zio-config-typesafe" % "4.1.0"
```

Add the ZIO Blocks config module and its HOCON adapter:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-config"       % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-config-hocon" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-config"       % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-next-config-hocon" % "@VERSION@"
```

`zio-blocks-config-hocon` depends only on `zio-blocks-config`. Neither module pulls in Typesafe Config or any other third-party library, so once the old lines are gone, `com.typesafe:config` leaves the classpath unless something else still needs it.

### Imports

Three imports cover this guide:

```scala mdoc:silent
import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema
```

`zio.blocks.config._` brings in `Config`, `ConfigSource`, `EnvSource`, `KeyFormat`, `Secret` and `ConfigError`. `zio.blocks.config.hocon._` adds `ConfigSource.fromHocon`, and on the JVM `ConfigSource.fromFile`.

## Mapping Old Calls to ZIO Blocks

Each row is one task the old loader did, and the ZIO Blocks call that does it now:

| Task                             | PureConfig                                        | Typesafe Config                          | zio-config                                  | ZIO Blocks                                                           |
|----------------------------------|---------------------------------------------------|------------------------------------------|---------------------------------------------|----------------------------------------------------------------------|
| Describe the configuration       | `derives ConfigReader`, or `generic.auto._`       | Hand-written getters                     | `deriveConfig[A]`                           | `Schema.derived` for the case class                                  |
| Read `application.conf`          | `ConfigSource.default`                            | `ConfigFactory.load()`                   | `ConfigProvider.fromResourcePath()`         | `ConfigSource.fromFile(path)` (JVM) or `ConfigSource.fromHocon(text, name)` |
| Select the `bookstore` block     | `.at("bookstore")`                                | `getConfig("bookstore")`                 | `.nested("bookstore")`                      | `ConfigSource#prefix("bookstore")`                                   |
| Map `featuredTitle` to `featured-title` | Default field mapping                      | Key spelled in each getter               | `ConfigProvider#kebabCase`                  | `ConfigSource#keyFormat(KeyFormat.KebabCase)`                        |
| Override from the environment    | `${?VAR}` in the file                             | `${?VAR}` in the file                    | `ConfigProvider.envProvider`                | `EnvSource` layered with `ConfigSource#orElse`                       |
| Add a fallback                   | `withFallback`                                    | `withFallback`                           | `orElse`                                    | `ConfigSource#orElse`                                                |
| Load                             | `load[A]`: `Either[ConfigReaderFailures, A]`      | Getters that throw                       | `ZIO.config(config)`: `IO[Config.Error, A]` | `Config.load[A](source)`: `Either[::[ConfigError], A]`               |
| Load or stop                     | `loadOrThrow[A]`                                  | Getters that throw                       | Fail the effect                             | `Config.loadOrThrow[A](source)`                                      |
| Hide a password                  | Your own wrapper type                             | Your own wrapper type                    | `Config.Secret`                             | `Secret`                                                             |
| See where a value came from      | Not available                                     | `ConfigValue#origin`                     | Not available                               | `Config.loadWithProvenance` and `ProvenanceMap#dump`                 |

Two differences run through the whole table. ZIO Blocks never reads `application.conf` from the classpath on its own: we name the file or pass its text. And the order of layers is explicit: `env.orElse(file)` asks `env` first, the way a stack of `withFallback` calls reads in reverse.

## The Application We Are Migrating

The bookstore keeps its settings in `application.conf`, under a `bookstore` block with kebab-case keys. The database password comes from the `DB_PASSWORD` environment variable through an optional substitution:

```text
bookstore {
  name = "Scala Bookstore"
  port = 8080
  featured-title = "Zionomicon"
  discount-percent = 10

  db {
    host = "localhost"
    port = 5432
    user = "bookstore"
    password = ${?DB_PASSWORD}
  }

  tags = ["scala", "functional-programming", "zio"]
}
```

With PureConfig, the loader is a derived reader and one call (old code, not compiled on this page):

```scala
// Before: PureConfig 0.17.11, Scala 3
import pureconfig.*

final case class DbConfig(host: String, port: Int = 5432, user: String, password: String) derives ConfigReader
final case class BookstoreConfig(
  name: String,
  port: Int = 8080,
  featuredTitle: String,
  db: DbConfig,
  tags: List[String],
  discountPercent: Option[Int]
) derives ConfigReader

val result: ConfigReader.Result[BookstoreConfig] = ConfigSource.default.at("bookstore").load[BookstoreConfig]
```

With zio-config, the same load is an effect that runs inside a ZIO application (old code, not compiled on this page):

```scala
// Before: zio-config 4.1.0
import zio.*
import zio.config.magnolia.*
import zio.config.typesafe.*

object Main extends ZIOAppDefault {
  override val bootstrap = Runtime.setConfigProvider(ConfigProvider.fromResourcePath().kebabCase)

  def run = ZIO.config(deriveConfig[BookstoreConfig].nested("bookstore")).flatMap(Console.printLine(_))
}
```

On the ZIO Blocks side, the case classes stay the same as in [Build a Typed Configuration Loader](./typed-config-loader.md). Only two things change: the password becomes a `Secret`, and the reader derivation becomes a `Schema`:

```scala mdoc:silent
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

`implicit val schema` with `Schema.derived` compiles on Scala 2.13 and Scala 3 alike, so a cross-built service keeps one definition. `Schema.derived` also derives the schemas for `DbConfig`, `List[String]` and `Option[Int]`, and `Secret` ships with its own.

We also write a small helper that prints either the loaded configuration or the message of every error:

```scala mdoc:silent
def report[A](result: Either[::[ConfigError], A]): Unit =
  result match {
    case Right(config) => println(config)
    case Left(errors)  => errors.foreach(error => println(error.message))
  }
```

## Reading Your Existing application.conf

`ConfigSource.fromHocon` parses HOCON text with `config-hocon`'s own parser and flattens it into dot-separated keys such as `bookstore.db.host`, the shape every ZIO Blocks source shares. It takes the text and a name for the source, and returns `Either[ConfigError, ConfigSource]`, because the text might not be valid HOCON. A documentation page can't ship a file, so we paste the unchanged `application.conf` into a string:

```scala mdoc:silent
val applicationConf =
  """bookstore {
    |  name = "Scala Bookstore"
    |  port = 8080
    |  featured-title = "Zionomicon"
    |  discount-percent = 10
    |
    |  db {
    |    host = "localhost"
    |    port = 5432
    |    user = "bookstore"
    |    password = ${?DB_PASSWORD}
    |  }
    |
    |  tags = ["scala", "functional-programming", "zio"]
    |}
    |""".stripMargin

val hocon: ConfigSource = ConfigSource.fromHocon(applicationConf, "application.conf") match {
  case Right(source) => source
  case Left(error)   => throw error
}
```

PureConfig's `.at("bookstore")` becomes `ConfigSource#prefix("bookstore")`, which puts `bookstore.` in front of every key the loader asks for. Load the file as it is:

```scala mdoc
report(Config.load[BookstoreConfig](hocon.prefix("bookstore")))
```

The file parses, but the load reports two missing keys, and each one is a migration step of its own:

- `featuredTitle` is missing because the file spells it `featured-title`. ZIO Blocks looks up field names exactly as written in the case class; the next section maps them.
- `db.password` is missing because `${?DB_PASSWORD}` found nothing. We replace it with an environment layer two sections below.

The report leaves out one key: `discount-percent` is missing for the same reason as `featured-title`, but `discountPercent` is an `Option[Int]`, and a missing key for an optional field is `None`, not an error. Until the keys are mapped, every kebab-case key behind an optional field or a field with a default is silently ignored.

## Keeping Your Kebab-Case Keys

PureConfig maps the camelCase field `featuredTitle` to the kebab-case key `featured-title` by default, and zio-config does the same with `ConfigProvider#kebabCase`. In ZIO Blocks, `ConfigSource#keyFormat` wraps a source and rewrites each key the loader asks for into the format the file uses. `KeyFormat.KebabCase` turns `featuredTitle` into `featured-title` and leaves dots alone, so nested keys such as `db.host` keep working:

```scala mdoc:silent
val application: ConfigSource = hocon.keyFormat(KeyFormat.KebabCase).prefix("bookstore")
```

Load again:

```scala mdoc
report(Config.load[BookstoreConfig](application))
```

Only the password is missing now. `featuredTitle` and `discountPercent` both read their kebab-case keys.

The file keeps its kebab-case keys, so it stays readable by the old loader while both run side by side. `ConfigSource#keyFormat` also accepts `KeyFormat.SnakeCase`, `KeyFormat.UpperSnakeCase` and `KeyFormat.CamelCase`, and `ConfigSource#keyMapper` takes a custom `KeyMapper` for any other convention.

## Replacing `${?VAR}` With an Environment Layer

In Typesafe Config, `password = ${?DB_PASSWORD}` first looks for a `DB_PASSWORD` key in the document and then falls back to the environment variable. `config-hocon` resolves substitutions inside the document only: it never reads environment variables, so `${?DB_PASSWORD}` finds nothing and the key disappears. Delete these lines from the file and add the environment as a layer of its own.

`ConfigSource#orElse` stacks two sources: `primary.orElse(fallback)` looks up each key in `primary` and asks `fallback` only for the keys `primary` doesn't have. This page can't set environment variables, so, as in [Build a Typed Configuration Loader](./typed-config-loader.md), a map source named `env` stands in for them:

```scala mdoc:silent
val env = ConfigSource.fromMap(Map("db.password" -> "s3cr3t-from-env", "port" -> "9090"), "env")

val layered = env.orElse(application)
```

Load the layered source:

```scala mdoc
report(Config.load[BookstoreConfig](layered))
```

The password and the port `9090` come from `env`, and every other value from `application.conf`.

In a real service, `EnvSource` replaces the stand-in, and two details decide which variables it reads:

```scala mdoc:silent
val realEnv: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

val production = realEnv.orElse(application)
```

`EnvSource` turns a key into a variable name by replacing dots with underscores and upper-casing the result. With `ConfigSource#prefix("bookstore")`, `db.password` is read from `BOOKSTORE_DB_PASSWORD` and `port` from `BOOKSTORE_PORT`. `KeyFormat.UpperSnakeCase` splits camelCase field names first, so `featuredTitle` is read from `BOOKSTORE_FEATURED_TITLE`.

:::warning
Without `KeyFormat.UpperSnakeCase`, `EnvSource` upper-cases a camelCase field name without splitting it: `featuredTitle` is read from `BOOKSTORE_FEATUREDTITLE`, and an exported `BOOKSTORE_FEATURED_TITLE` is silently ignored. Dotted keys such as `db.password` map correctly either way, so the mistake shows up only for multi-word fields.
:::

:::warning
Always give `EnvSource` a prefix. A bare `EnvSource` answers for every variable in the process, so an unrelated `PORT` or `HOST` set for another program becomes configuration. `ProvenanceMap#dump` over a bare `EnvSource` also prints the **entire process environment**, and it hides only the values whose names contain a sensitive word such as `password`, `secret` or `token`. A `DATABASE_URL` with an embedded password is printed in clear text.
:::

The prefix renames the variables: `DB_PASSWORD` becomes `BOOKSTORE_DB_PASSWORD`. If the deployment can't change its variable names yet, read the few old names into a map source instead of opening up the whole environment:

```scala mdoc:silent
val legacyEnv = ConfigSource.fromMap(sys.env.get("DB_PASSWORD").map(password => "db.password" -> password).toMap, "env")
```

`legacyEnv.orElse(application)` then reads the password from `DB_PASSWORD` and nothing else from the environment.

## Loading the File on the JVM, Includes and All

On the JVM, `ConfigSource.fromFile` reads the file itself and resolves its `include "file"` directives relative to the including file's directory, the way Typesafe Config does for files. It returns `Either[ConfigError, ConfigSource]`, and a missing file is a `Left`, never an exception.

To show an include at work, we write a two-file configuration into a temporary directory. `application.conf` includes `db.conf` inside its `bookstore` block:

```scala mdoc:silent
import java.nio.file.Files

val confDir = Files.createTempDirectory("bookstore")

Files.writeString(
  confDir.resolve("application.conf"),
  """bookstore {
    |  name = "Scala Bookstore"
    |  featured-title = "Zionomicon"
    |  tags = ["scala", "zio"]
    |  include "db.conf"
    |}
    |""".stripMargin
)

Files.writeString(
  confDir.resolve("db.conf"),
  """db {
    |  host = "localhost"
    |  user = "bookstore"
    |  password = "dev-password"
    |}
    |""".stripMargin
)
```

Now we load `application.conf` from disk:

```scala mdoc
ConfigSource.fromFile(confDir.resolve("application.conf").toString) match {
  case Right(file) =>
    println(file.sourceId)
    report(Config.load[BookstoreConfig](file.keyFormat(KeyFormat.KebabCase).prefix("bookstore")))
  case Left(error) =>
    println(error.message)
}
```

The `db` block from `db.conf` lands under `bookstore`, where the include sits, and the source is named `hocon:` followed by the file name.

`ConfigSource.fromFile` also takes `allowedBase`, a directory that the file and its includes must stay inside, and `maxIncludeDepth`, which defaults to `10`. `ConfigSource.fromFileWithLimits` adds `maxFileBytes`, which caps the size of the file and of each include and defaults to 1 MiB. Configuration files are usually small, so the defaults rarely need changing.

:::note
`ConfigFactory.load()` and PureConfig's `ConfigSource.default` find `application.conf` on the classpath. `config-hocon` has no classpath lookup: pass `fromFile` a path on disk, or read a bundled resource yourself, for example with `scala.io.Source.fromResource("application.conf").mkString`, and pass its text to `ConfigSource.fromHocon`. Text passed to `fromHocon` has no directory to resolve includes from, so its `include` lines are dropped. `fromFile` is JVM-only; on Scala.js, `fromHocon` is the entry point.
:::

## Checking Which HOCON Features Carry Over

Most of an `application.conf` parses unchanged: objects with `=`, `:` or no separator, dotted keys such as `a.b.c = 1`, duplicate keys where the last one wins, objects merged key by key, `#` and `//` comments, quoted, unquoted and triple-quoted strings, `${path}` and `${?path}` substitutions that point inside the document, string concatenation such as `${base}"/data"`, `+=` to append to an array, and JSON. A few features behave differently, and a migration needs to check for each of them:

| HOCON in the file                                   | Typesafe Config                                   | `config-hocon`                                                                 | What to change                                                  |
|-----------------------------------------------------|---------------------------------------------------|--------------------------------------------------------------------------------|-----------------------------------------------------------------|
| `${?VAR}` naming an environment variable            | Falls back to the environment                     | Looks inside the document only; the key disappears                             | Delete it and layer `EnvSource`                                 |
| `${VAR}` naming an environment variable             | Falls back to the environment                     | Parse error: unresolved substitution                                           | Delete it and layer `EnvSource`                                 |
| `include "file"`                                    | Supported                                         | Supported by `fromFile`; dropped by `fromHocon`                                | Load the file with `fromFile`                                   |
| `include file(...)`, `classpath(...)`, `url(...)`, `required(...)` | Supported                          | Parse error                                                                    | Use `include "file"`, or load the file and layer it with `orElse` |
| `reference.conf` merged under `application.conf`    | Automatic                                         | Not read                                                                       | Load it and add it with `orElse`, or use case-class defaults    |
| Self-reference such as `path = ${path}":/extra"`    | Supported                                         | Parse error: circular substitution                                             | Write the full value                                            |
| Array concatenation such as `[a] [b]`               | Supported                                         | The value becomes an empty string                                              | Write one array, or use `+=`                                    |
| Durations such as `10s` and sizes such as `512M`    | Parsed by `getDuration` and `getBytes`            | Kept as strings; `java.time.Duration` fields need ISO-8601 (`PT10S`)            | Rewrite durations as ISO-8601; read sizes as numbers            |

Four of these show up on small documents. The helper `keys` parses a document with `fromHocon` and prints the keys it produced, or the parse error:

```scala mdoc:silent
def keys(document: String): Unit =
  ConfigSource.fromHocon(document) match {
    case Right(source) => println(source.all("").map { case (key, value) => s"$key = '${value.value}'" }.toList.sorted)
    case Left(error)   => println(error.message)
  }
```

Now we pass it an environment substitution, a classpath include, an include in a string and an array concatenation:

```scala mdoc
keys("home = ${?HOME}")
keys("""include classpath("reference.conf")""")
keys("""db { include "db.conf" }, name = "Scala Bookstore"""")
keys("tags = [scala] [zio]")
```

The code above:

- `home = ${?HOME}` produces no keys, even though `HOME` is set in almost every environment.
- `include classpath(...)` fails the whole document with a parse error.
- The `include` inside a string is dropped, and so is the `db` block it was the only content of.
- `[scala] [zio]` produces a single key whose value is an empty string, not a two-element list.

The parse error names only the source. To find the line, parse the file with the old loader once, or look for the constructs in the table above.

Durations need the most care, because the file still parses and only the load fails. `config-hocon` keeps `10s` as the string `10s`, and a `java.time.Duration` field is read with `java.time.Duration.parse`, which accepts ISO-8601 only:

```scala mdoc:silent
case class Timeouts(requestTimeout: java.time.Duration)

object Timeouts {
  implicit val schema: Schema[Timeouts] = Schema.derived
}
```

Load it from both spellings:

```scala mdoc
report(Config.load[Timeouts](ConfigSource.fromHocon("requestTimeout = 10s").toOption.get))
report(Config.load[Timeouts](ConfigSource.fromHocon("requestTimeout = PT10S").toOption.get))
```

`10s` is an invalid value, and `PT10S` loads as a ten-second duration.

## Reading Every Error at Once

PureConfig and zio-config already collect errors from every field before they fail, and Typesafe Config getters throw on the first one. `Config.load` collects them too: it checks every field and returns a non-empty list `::[ConfigError]`, one entry per problem.

Break the file in three ways: a word for the port, a word for the discount, and no featured title. The environment stand-in is empty, so the password is missing too:

```scala mdoc:silent
val brokenConf =
  """bookstore {
    |  name = "Scala Bookstore"
    |  port = eighty
    |  discount-percent = "ten"
    |  db { host = "localhost", user = "bookstore" }
    |  tags = ["scala"]
    |}
    |""".stripMargin

val broken = ConfigSource.fromHocon(brokenConf, "application.conf") match {
  case Right(source) => source.keyFormat(KeyFormat.KebabCase).prefix("bookstore")
  case Left(error)   => throw error
}

val emptyEnv = ConfigSource.fromMap(Map.empty, "env")
```

Now we load the broken layers:

```scala mdoc
report(Config.load[BookstoreConfig](emptyEnv.orElse(broken)))
```

The code above reports all four problems from one load:

- Each message names the key in the case class's spelling, such as `discountPercent`, not the file's `discount-percent`.
- An invalid value ends with the reason the conversion failed, here the JDK's `For input string` message.
- The source is the whole layered stack, `env|application.conf`, because the loader asked the stack as a whole. To find the layer that supplied a value that did load, use `Config.loadWithProvenance` from the next section.

The port has a default of `8080`, and it is still reported. A default replaces a missing key, never a broken one, so a typo can't fall back silently.

`Config.loadOrThrow` replaces PureConfig's `loadOrThrow`: it returns the configuration or throws a `ConfigLoadException` whose message is a report of the errors, for services that should stop at startup.

## Keeping Secrets Out of Logs

PureConfig and Typesafe Config leave secret handling to us, usually with a wrapper type whose `toString` hides the value, and zio-config ships `Config.Secret`. ZIO Blocks ships `Secret`: a wrapper around a `String` whose `Secret#toString` is `<secret>`, so printing or logging the configuration never shows the password. `Secret.unwrap` is the only way to read the value.

`Config.loadWithProvenance` loads the configuration and keeps the source stack, so `ProvenanceMap#dump` can print every key, its value and the layer that supplied it:

```scala mdoc
Config.loadWithProvenance[BookstoreConfig](layered) match {
  case Right(provenance) =>
    println(provenance.value)
    println(provenance.dump())
    println(Secret.unwrap(provenance.value.db.password).length)
  case Left(errors) =>
    errors.foreach(error => println(error.message))
}
```

The code above:

- prints the configuration, with the password shown as `<secret>` by `Secret#toString`.
- prints the provenance table. The rows use the case class's key names, each value names its layer, and `db.password` shows `<secret>`.
- prints the length of the real password from `Secret.unwrap`, a value the program can use without ever logging it.

`ProvenanceMap#dump` hides a value because its **key name** contains a sensitive word such as `password`, `secret` or `token`, not because the field is a `Secret`. Give every secret setting a name that says what it is. The [Secrets](../reference/config/config-source.md#secrets) section of the `ConfigSource` reference lists the words.

:::note
When `EnvSource` is wrapped in a key format, as in `production` above, `ProvenanceMap#dump` lists an environment override under the variable's own words: `BOOKSTORE_FEATURED_TITLE` appears as `featured.title`, and the overridden `featuredTitle` row from the file stays in the table. The loaded value is correct. To check one key, ask `ProvenanceMap#provenanceOf("featuredTitle")`, which names the variable that supplied it.
:::

## What Works Differently

A few habits from the old libraries don't carry over:

- **No effect system.** `Config.load` is a plain function call that returns an `Either`. There is no ZIO runtime to set up and no `bootstrap` layer; a ZIO application wraps the call in `ZIO.fromEither` where it needs an effect.
- **No reload or watch.** A load reads the sources once. To pick up a changed file, load again, as in the example below. Runtime switches that change while the service runs are covered by [Flags](../reference/config/flags.md), not by `Config`.
- **Every Scala version and Scala.js.** `zio-blocks-config` and `zio-blocks-config-hocon` are built for Scala 2.13, Scala 3.3 LTS and Scala 3.9, on the JVM and Scala.js. `ConfigSource.fromFile` is JVM-only, and on Scala.js `SysPropSource` is always empty; `ConfigSource.fromHocon` and `EnvSource` work on both platforms.
- **Nothing is read implicitly.** `ConfigFactory.load()` merges system properties, `application.conf` and `reference.conf` on its own. In ZIO Blocks, each layer is a source we name, and the order of `orElse` calls is the order of precedence. `SysPropSource` reads JVM system properties by their dotted names when a `-D` override layer is wanted.

Loading is an ordinary call, so reloading is a function we call again whenever the service wants fresh values:

```scala mdoc
def loadBookstore(): Either[::[ConfigError], BookstoreConfig] = Config.load[BookstoreConfig](layered)

println(loadBookstore().map(_.featuredTitle))
```

## Putting It Together

This example loads the migrated `application.conf` from disk with `ConfigSource.fromFile`, maps its kebab-case keys, layers `BOOKSTORE_*` environment variables over it, and prints the configuration and where each value came from, or every error at once. The file is the PureConfig file without its `${?DB_PASSWORD}` line:

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `config-examples` project, which reads its HOCON files from `config-examples/src/main/resources/configlibrarymigration/`. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/config-examples
```

Each example is self-contained: it declares the bookstore case classes and the helpers it uses, so any file can be copied on its own. Run them in order to follow the guide.

### Reading Your Existing application.conf

This example parses the unchanged file with `ConfigSource.fromHocon` and loads it, reporting the kebab-case key and the missing password.

<details>
  <summary>config-examples/src/main/scala/configlibrarymigration/Step1ReadApplicationConfExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/Step1ReadApplicationConfExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain configlibrarymigration.Step1ReadApplicationConfExample"
```

### Keeping Your Kebab-Case Keys

This example wraps the source with `KeyFormat.KebabCase`, leaving only the password missing.

<details>
  <summary>config-examples/src/main/scala/configlibrarymigration/Step2KebabCaseKeysExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/Step2KebabCaseKeysExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain configlibrarymigration.Step2KebabCaseKeysExample"
```

### Replacing `${?VAR}` With an Environment Layer

This example layers the real environment over the file with `EnvSource`, scoped to `BOOKSTORE_*` variables and split into upper snake case.

<details>
  <summary>config-examples/src/main/scala/configlibrarymigration/Step3EnvironmentLayerExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/Step3EnvironmentLayerExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory, with the password and a featured title in the environment:

```bash
BOOKSTORE_DB_PASSWORD=s3cr3t BOOKSTORE_FEATURED_TITLE="Programming in Scala" sbt "runMain configlibrarymigration.Step3EnvironmentLayerExample"
```

### Loading the File on the JVM, Includes and All

This example reads `split/application.conf` with `ConfigSource.fromFile`, which includes `split/db.conf`, and shows the error for a missing file.

<details>
  <summary>config-examples/src/main/scala/configlibrarymigration/Step4LoadFileWithIncludesExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/Step4LoadFileWithIncludesExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain configlibrarymigration.Step4LoadFileWithIncludesExample"
```

### Checking Which HOCON Features Carry Over

This example parses the HOCON constructs that behave differently from Typesafe Config and loads a duration in both spellings.

<details>
  <summary>config-examples/src/main/scala/configlibrarymigration/Step5HoconCaveatsExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/Step5HoconCaveatsExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain configlibrarymigration.Step5HoconCaveatsExample"
```

### Reading Every Error at Once

This example loads a broken file and prints every error from one load.

<details>
  <summary>config-examples/src/main/scala/configlibrarymigration/Step6ErrorsExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/Step6ErrorsExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain configlibrarymigration.Step6ErrorsExample"
```

### Keeping Secrets Out of Logs

This example prints the configuration and its provenance table with the password redacted, and reads the password with `Secret.unwrap`.

<details>
  <summary>config-examples/src/main/scala/configlibrarymigration/Step7SecretsExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/configlibrarymigration/Step7SecretsExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain configlibrarymigration.Step7SecretsExample"
```

### The Complete Example

The complete example from "Putting It Together" reads the real environment, so the password must be exported. Any other `BOOKSTORE_*` variable overrides the file:

```bash
BOOKSTORE_DB_PASSWORD=s3cr3t sbt "runMain configlibrarymigration.CompleteExample"
BOOKSTORE_DB_PASSWORD=s3cr3t BOOKSTORE_FEATURED_TITLE="Programming in Scala" sbt "runMain configlibrarymigration.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## Going Further

- [Build a Typed Configuration Loader](./typed-config-loader.md) teaches the loader from scratch with the same bookstore: defaults, optional fields, layering and provenance.
- [How to Load Configuration Files on the JVM and in the Browser](./load-configuration-files.md) goes deeper on includes, safe loading and running the same settings in the browser.
- [File Formats](../reference/config/formats.md) documents the HOCON adapter's parameters and failures, next to the YAML and JSON adapters.
- [ConfigSource](../reference/config/config-source.md) covers every source, `ConfigSource#prefix`, key formats and secret redaction.
- [ConfigDecoder](../reference/config/config-decoder.md) lists how fields map to keys for nested records, sealed traits, lists and maps.
- [Configuration Errors](../reference/config/errors.md) describes every `ConfigError` case and how errors combine.
- [Flags](../reference/config/flags.md) and [Rollout](../reference/config/rollout.md) cover feature flags and percentage rollouts, built on the same sources.
- The [Configuration & Feature Flags](../reference/config/index.md) overview shows how the pieces fit, including `Config.wire` for dependency graphs.
