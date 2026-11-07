---
id: load-configuration-files
title: "How to Load Configuration Files on the JVM and in the Browser"
description: "Load HOCON, JSON and YAML configuration files into a typed case class, split them with includes, load them safely, and run the same settings in the browser."
keywords:
  - "Configuration Files"
  - "HOCON"
  - "application.conf"
  - "ConfigSource.fromFile"
  - "Includes and Substitutions"
  - "Scala.js"
---

In this guide, we load the settings of a Scala bookstore service from configuration files into one typed case class. By the end, you will read an `application.conf` from disk, split it into several files with includes, reuse values with substitutions, keep the loader inside one folder and below a size limit, read the same settings from JSON and YAML files, override file values with environment variables and system properties, and load the configuration where there is no filesystem at all: from the classpath and in the browser.

We start with one HOCON file and add one capability per section:

- [Loading application.conf From a File](#loading-applicationconf-from-a-file)
- [Splitting the File With Includes](#splitting-the-file-with-includes)
- [Reusing Values With Substitutions and Overrides](#reusing-values-with-substitutions-and-overrides)
- [Loading Files Safely](#loading-files-safely)
- [Loading JSON and YAML Files](#loading-json-and-yaml-files)
- [Guarding Against Lenient YAML](#guarding-against-lenient-yaml)
- [Overriding File Values From the Environment](#overriding-file-values-from-the-environment)
- [Loading application.conf From the Classpath](#loading-applicationconf-from-the-classpath)
- [Loading the Same Settings in the Browser](#loading-the-same-settings-in-the-browser)

## The Problem

A service keeps its settings in files so that the same build can run with different settings on a laptop, in CI and in production. Reading those files by hand means parsing the format, converting strings to numbers, merging the files of a split configuration, applying overrides from the environment, and reporting what is wrong, for every setting.

Without a configuration library, the bookstore's loader usually reads a flat `.properties` file:

```scala mdoc:compile-only
import java.io.FileInputStream
import java.util.Properties

final case class BookstoreSettings(
  featuredBook: String,
  pageSize: Int,
  dbUrl: String,
  dbUser: String,
  dbPassword: String
)

object LoadSettings {
  def load(path: String): BookstoreSettings = {
    val properties = new Properties()
    val in         = new FileInputStream(path)
    try properties.load(in)
    finally in.close()

    def setting(key: String): String =
      sys.env
        .get(key.toUpperCase.replace('.', '_'))
        .orElse(Option(properties.getProperty(key)))
        .getOrElse(throw new IllegalStateException(s"Missing setting $key in $path"))

    BookstoreSettings(
      featuredBook = setting("catalog.featuredBook"),
      pageSize = setting("catalog.pageSize").toInt,
      dbUrl = setting("db.url"),
      dbUser = setting("db.user"),
      dbPassword = setting("db.password")
    )
  }
}
```

Every key is spelled out twice, once in the file and once in the code. The format has no nesting, so a large file can't be split into a `db` part and a `catalog` part, and nothing stops two teams from defining the same key. A `catalog.pageSize=twenty` throws `NumberFormatException` without naming the key, the loader stops at the first missing setting, it reads a file of any size from any path it is given, and the password is a plain `String` that ends up in the logs as soon as someone prints the settings. On Scala.js, `FileInputStream` doesn't exist, so the browser build needs a second loader.

In this guide, the config module decodes the settings into a case class from its `Schema`, and its format modules read HOCON, JSON and YAML text into a `ConfigSource`, a flat set of dotted keys such as `db.url`. On the JVM, `config-hocon` also reads files itself, resolving includes and enforcing limits.

## Prerequisites

Add the config module and the three format modules to your `build.sbt`. `config-hocon` depends only on `zio-blocks-config`; `config-json` and `config-yaml` also bring in the schema and schema-yaml modules, so add only the formats you read:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-config"       % "@VERSION@",
  "dev.zio" %% "zio-blocks-config-hocon" % "@VERSION@",
  "dev.zio" %% "zio-blocks-config-json"  % "@VERSION@",
  "dev.zio" %% "zio-blocks-config-yaml"  % "@VERSION@"
)

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-config"       % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-config-hocon" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-config-json"  % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-config-yaml"  % "@VERSION@"
)
```

For a Scala.js build, use `%%%` instead of `%%`. [How to Add ZIO Blocks to Your Build](../getting-started/installation.md) explains which artifact names to pick for each Scala version.

This guide assumes you have loaded a case class from a `ConfigSource`, as in [Build a Typed Configuration Loader](./typed-config-loader.md). If you are moving an existing Typesafe Config or PureConfig application, [Coming from PureConfig, Typesafe Config or zio-config](./config-library-migration.md) maps the old calls one by one. The snippets below share these imports:

```scala mdoc:silent
import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.config.json._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema
```

`zio.blocks.config.hocon._` adds `ConfigSource.fromHocon` and, on the JVM, `ConfigSource.fromFile` and `ConfigSource.fromFileWithLimits`. `zio.blocks.config.json._` adds `ConfigSource.fromJson`, and `zio.blocks.config.yaml._` adds `ConfigSource.fromYaml`.

## The Bookstore's Settings

The bookstore has three sections of settings: the HTTP server, the catalog with its featured book and page size, and the database. Each section is a case class that derives a `Schema`, which is all the config module needs to decode it, and the database password is a `Secret`, so printing the settings never shows it:

```scala mdoc:silent
final case class ServerConfig(host: String, port: Int)
object ServerConfig {
  implicit val schema: Schema[ServerConfig] = Schema.derived
}

final case class CatalogConfig(featuredBook: String, pageSize: Int)
object CatalogConfig {
  implicit val schema: Schema[CatalogConfig] = Schema.derived
}

final case class DbConfig(url: String, user: String, password: Secret, poolSize: Int)
object DbConfig {
  implicit val schema: Schema[DbConfig] = Schema.derived
}

final case class BookstoreConfig(server: ServerConfig, catalog: CatalogConfig, db: DbConfig)
object BookstoreConfig {
  implicit val schema: Schema[BookstoreConfig] = Schema.derived
}
```

A nested case class reads a nested section: `BookstoreConfig#catalog` reads the keys under `catalog`, and `CatalogConfig#featuredBook` reads `catalog.featuredBook`. The keys are the field names as written, in camelCase. Files that use kebab-case keys such as `featured-book` need `KeyFormat.KebabCase`, which [Coming from PureConfig, Typesafe Config or zio-config](./config-library-migration.md#keeping-your-kebab-case-keys) shows.

```scala mdoc:invisible
val workDir: Path = Files.createTempDirectory("bookstore").toRealPath()
val confDir: Path = Files.createDirectories(workDir.resolve("conf"))
```

This page writes its configuration files into a temporary folder, `confDir`, inside a temporary work folder, `workDir`. In your application, `confDir` is the folder you ship your configuration in. Two small helpers keep the snippets short: `write` writes a file under `confDir`, and `report` prints an error with the temporary folder's path replaced by `<work>`, so the output reads the same on every machine:

```scala mdoc:silent
def write(name: String, text: String): Path = {
  val file = confDir.resolve(name)
  Files.createDirectories(file.getParent)
  Files.write(file, text.getBytes("UTF-8"))
}

def report(error: ConfigError): Unit =
  println(error.getMessage.replace(workDir.toString, "<work>"))
```

## Loading application.conf From a File

The bookstore starts with one HOCON file, the format of Typesafe Config's `application.conf`:

```scala mdoc:silent
val applicationConf = write(
  "application.conf",
  """server {
    |  host = "0.0.0.0"
    |  port = 8080
    |}
    |catalog {
    |  featuredBook = "Zionomicon"
    |  pageSize = 20
    |}
    |db {
    |  url = "jdbc:postgresql://localhost:5432/bookstore"
    |  user = "bookstore"
    |  password = "dev-password"
    |  poolSize = 10
    |}
    |""".stripMargin
)
```

`ConfigSource.fromFile` reads the file and returns `Either[ConfigError, ConfigSource]`. `Config.load` then decodes the case class from the source and returns `Either` with every decoding error on the left. `decode` chains the two steps, and later sections reuse it for every other way of getting a source:

```scala mdoc
def decode(source: Either[ConfigError, ConfigSource]): Either[List[ConfigError], BookstoreConfig] =
  source.left.map(List(_)).flatMap(Config.load[BookstoreConfig](_))

def loadBookstore(path: Path): Either[List[ConfigError], BookstoreConfig] =
  decode(ConfigSource.fromFile(path.toString))

println(loadBookstore(applicationConf))
```

The password prints as `<secret>`; `Secret.unwrap` returns the real value where the database driver needs it. The source that `ConfigSource.fromFile` returns is named `hocon:` followed by the file name, and that name appears in every error the source produces.

`ConfigSource.fromFile` never throws. A path that doesn't exist, a typo in the file name here, comes back as a `Left`:

```scala mdoc
loadBookstore(confDir.resolve("aplication.conf")).left.foreach(_.foreach(report))
```

`ConfigSource.fromFile` is JVM-only: Scala.js has no filesystem API to read from, so [Loading the Same Settings in the Browser](#loading-the-same-settings-in-the-browser) uses `ConfigSource.fromHocon`, which parses HOCON text on both platforms.

## Splitting the File With Includes

A large `application.conf` is easier to own when each section lives in its own file. `ConfigSource.fromFile` resolves `include "file"` lines relative to the folder of the file that contains them, the way Typesafe Config does for files, and an include inside a block puts the included keys under that block. Here `application.conf` includes `server.conf` at the top level and `db/db.conf` inside its `db` block, and `db/db.conf` in turn includes `pool.conf` from its own folder, `db/`:

```scala mdoc:silent
write(
  "server.conf",
  """server {
    |  host = "0.0.0.0"
    |  port = 8080
    |}
    |""".stripMargin
)

write(
  "db/db.conf",
  """url = "jdbc:postgresql://localhost:5432/bookstore"
    |user = "bookstore"
    |password = "dev-password"
    |include "pool.conf"
    |""".stripMargin
)

write("db/pool.conf", "poolSize = 10\n")

val splitConf = write(
  "split.conf",
  """include "server.conf"
    |catalog {
    |  featuredBook = "Zionomicon"
    |  pageSize = 20
    |}
    |db {
    |  include "db/db.conf"
    |}
    |""".stripMargin
)
```

The split configuration decodes to the same settings as the single file:

```scala mdoc
println(loadBookstore(splitConf))
```

Includes are resolved while the file is parsed, so the included keys behave exactly as if they were written in the including file: later keys override earlier ones, and substitutions can refer to keys from any of the files.

:::warning[A missing include is skipped without an error]
Like Typesafe Config, `ConfigSource.fromFile` silently skips an `include` whose file doesn't exist, so a typo such as `include "db/dbb.conf"` loads without the `db` keys. `config-hocon` 0.0.56 doesn't parse `include required("...")`, nor `file(...)`, `classpath(...)` or `url(...)`: each is a parse error, so only the plain `include "file"` form is available. An absolute path such as `include "/etc/bookstore/db.conf"` is also resolved inside the including file's folder, so it is skipped the same way. Keep every required key free of a default in your case classes: then a skipped include shows up as `Missing required key` errors when the configuration loads, not later in production.
:::

Includes work only when the loader reads the files itself. `ConfigSource.fromHocon`, which takes the text of a file, drops every `include` line without an error, a known bug in 0.0.56. [Loading application.conf From the Classpath](#loading-applicationconf-from-the-classpath) shows how to resolve includes when the text doesn't come from `ConfigSource.fromFile`.

## Reusing Values With Substitutions and Overrides

HOCON lets one value refer to another with `${path}`, and lets a later definition of a key override an earlier one. Both work across included files, which makes a shared `defaults.conf` possible: the application file includes it first and then changes only what differs. Here `defaults.conf` holds the database host, port and name once, and builds the JDBC URL from them with substitutions; `layered.conf` includes it and overrides two values:

```scala mdoc:silent
write(
  "defaults.conf",
  """server {
    |  host = "0.0.0.0"
    |  port = 8080
    |}
    |catalog {
    |  featuredBook = "Programming in Scala"
    |  pageSize = 20
    |}
    |db {
    |  host = "localhost"
    |  port = 5432
    |  name = "bookstore"
    |  url = "jdbc:postgresql://"${db.host}":"${db.port}"/"${db.name}
    |  user = "bookstore"
    |  password = "dev-password"
    |  poolSize = 10
    |}
    |""".stripMargin
)

val layeredConf = write(
  "layered.conf",
  """include "defaults.conf"
    |catalog.featuredBook = "Zionomicon"
    |db.host = "db.internal"
    |""".stripMargin
)
```

Substitutions are resolved after every file is merged, so `db.url` picks up the overridden `db.host`, and the `catalog` block keeps `pageSize` from the defaults because two objects with the same key merge key by key:

```scala mdoc
println(loadBookstore(layeredConf))
```

`${?path}` is the optional form: when `path` is not defined anywhere in the files, a key whose whole value is `${?path}` is left out instead of failing the parse. Inside a concatenation such as `"jdbc:postgresql://localhost:5432/"${?db.name}`, the missing part becomes the text `null` in 0.0.56, a known bug, so avoid `${?path}` inside concatenations.

:::warning[Two substitutions Typesafe Config users rely on]
In 0.0.56, a substitution never falls back to an environment variable: `${DB_PASSWORD}` fails to parse and `${?DB_PASSWORD}` resolves to nothing, even when the variable is set. This is a known bug. Read environment variables with an `EnvSource` layer instead, as [Overriding File Values From the Environment](#overriding-file-values-from-the-environment) shows. A self-reference such as `path = ${path}":/opt/bin"` is also rejected as a circular substitution; `+=`, which appends to a list, works.
:::

## Loading Files Safely

The defaults of `ConfigSource.fromFile` trust the file it is given and every file that file includes. When the path, or the folder it sits in, can be written by someone other than your team, two limits keep the loader from reading what it shouldn't.

### Keeping Includes Inside the Configuration Folder

Without a limit, an include can reach any file the process can read. Here `escape.conf`, in `confDir`, includes `secrets.conf` from the work folder above it:

```scala mdoc:silent
Files.write(workDir.resolve("secrets.conf"), "db.password = \"production-password\"\n".getBytes("UTF-8"))

val escapeConf = write("escape.conf", "include \"../secrets.conf\"\n")
```

By default, `ConfigSource.fromFile` follows the include out of the folder and reads the other file's keys:

```scala mdoc
println(ConfigSource.fromFile(escapeConf.toString).map(_.all("").keys.toList))
```

`allowedBase` names a folder that the file and every include must stay inside. `ConfigSource.fromFile` resolves each path to its canonical form first, so neither `../` nor a symbolic link that points outside can get around it, and the include above now fails the whole load:

```scala mdoc
ConfigSource.fromFile(escapeConf.toString, allowedBase = Some(confDir.toFile)) match {
  case Left(ConfigError.ParseError(_, _, _, Some(cause))) => println(cause.getMessage.replace(workDir.toString, "<work>"))
  case other                                              => println(other)
}
```

The match prints the error's cause because, for an error inside the document, the error's own message ends at `Parse error for key '' (expected valid HOCON) in source 'hocon:escape.conf'`. It leaves out the reason and the line on purpose, so that a cause quoting a line of the file, which may hold a secret, never reaches a log. Print the cause yourself only where that is safe. A root file outside `allowedBase` fails too, and then the message itself says `expected path inside` followed by the folder.

`maxIncludeDepth` limits how deep includes may nest, and defaults to `10`. Two files that include each other exceed it and fail with `Include depth exceeded (10)` instead of looping forever.

### Capping the File Size

`ConfigSource.fromFile` refuses a file larger than 1 MiB, and checks the same limit for every included file, so a huge or endless file never fills the heap. Here a file of about 2 MB is refused:

```scala mdoc:silent
val largeConf = write("large.conf", "# padding\n" * (200 * 1024))
```

The size check runs before the file is parsed:

```scala mdoc
ConfigSource.fromFile(largeConf.toString).left.foreach(report)
```

`ConfigSource.fromFileWithLimits` takes the same parameters as `ConfigSource.fromFile` plus `maxFileBytes`. A service with a large generated configuration raises the limit there:

```scala mdoc
println(ConfigSource.fromFileWithLimits(largeConf.toString, allowedBase = Some(confDir.toFile), maxFileBytes = 4L * 1024 * 1024).map(_.sourceId))
```

A smaller limit than the default also makes sense when the file comes from an upload or a shared volume.

## Loading JSON and YAML Files

`config-json` and `config-yaml` parse text, not files: `ConfigSource.fromJson` and `ConfigSource.fromYaml` take the document as a `String` and a source name for error messages, and return `Either[ConfigError, ConfigSource]`. On the JVM, we read the file ourselves and pass its text and file name:

```scala mdoc:silent
def readConfigFile(path: Path)(parse: (String, String) => Either[ConfigError, ConfigSource]): Either[ConfigError, ConfigSource] =
  parse(new String(Files.readAllBytes(path), "UTF-8"), path.getFileName.toString)

val configJson = write(
  "config.json",
  """{
    |  "server": { "host": "0.0.0.0", "port": 8080 },
    |  "catalog": { "featuredBook": "Zionomicon", "pageSize": 20 },
    |  "db": {
    |    "url": "jdbc:postgresql://localhost:5432/bookstore",
    |    "user": "bookstore",
    |    "password": "dev-password",
    |    "poolSize": 10
    |  }
    |}
    |""".stripMargin
)

val configYaml = write(
  "config.yaml",
  """server:
    |  host: 0.0.0.0
    |  port: 8080
    |catalog:
    |  featuredBook: Zionomicon
    |  pageSize: 20
    |db:
    |  url: jdbc:postgresql://localhost:5432/bookstore
    |  user: bookstore
    |  password: dev-password
    |  poolSize: 10
    |""".stripMargin
)
```

Both files flatten to the same dotted keys as the HOCON file, so the same case class decodes from either:

```scala mdoc
println(decode(readConfigFile(configJson)(ConfigSource.fromJson(_, _))))
println(decode(readConfigFile(configYaml)(ConfigSource.fromYaml(_, _))))
```

Reading the file yourself means the limits of `ConfigSource.fromFile` don't apply: `Files.readAllBytes` throws `NoSuchFileException` for a missing file and reads a file of any size. Check `Files.size` first when the path comes from outside your team.

## Guarding Against Lenient YAML

The YAML reader accepts malformed documents without an error: it stops at the first line it can't read and drops that line and everything after it. Here `pageSize` is missing the space after its colon:

```scala mdoc:silent
val brokenYaml = write(
  "broken.yaml",
  """server:
    |  host: 0.0.0.0
    |  port: 8080
    |catalog:
    |  featuredBook: Zionomicon
    |  pageSize:20
    |db:
    |  url: jdbc:postgresql://localhost:5432/bookstore
    |  user: bookstore
    |  password: dev-password
    |  poolSize: 10
    |""".stripMargin
)
```

Parsing succeeds, and the source ends at `featuredBook`: `pageSize` and the whole `db` section after it are gone:

```scala mdoc
println(readConfigFile(brokenYaml)(ConfigSource.fromYaml(_, _)).map(_.all("").keys.toList.sorted))
```

The typed load is the guard: every field without a default is required, so the dropped keys come back as errors instead of a half-configured service. Keep defaults out of the case class for settings that must come from the file, and supply them through a defaults layer instead, as the next section does:

```scala mdoc
decode(readConfigFile(brokenYaml)(ConfigSource.fromYaml(_, _))).left.foreach(_.foreach(report))
```

The errors name the missing keys, not the line that caused them. In 0.0.56, the YAML reader almost never reports a parse error, and a JSON parse error names neither the reason nor the line, so when a file doesn't load as expected, open it in an editor that validates the format.

## Overriding File Values From the Environment

The file holds the defaults that ship with the service, and each deployment overrides a few of them: the database password always, the page size sometimes. `ConfigSource#orElse` layers two sources: the source on the left wins, and the source on the right fills in every key the left one doesn't have.

In production, the environment layer is `EnvSource`. `KeyFormat.UpperSnakeCase` and the `"bookstore"` prefix make it read `catalog.featuredBook` from the variable `BOOKSTORE_CATALOG_FEATURED_BOOK` and `db.password` from `BOOKSTORE_DB_PASSWORD`. `SysPropSource` reads JVM system properties, so `SysPropSource.prefix("bookstore")` reads `catalog.pageSize` from `-Dbookstore.catalog.pageSize=50`:

```scala mdoc:silent
val productionEnv: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

val systemProperties: ConfigSource = SysPropSource.prefix("bookstore")
```

This page must give the same output on every machine, so it uses a map in place of the environment, holding the two variables a deployment would export. The system property is real:

```scala mdoc:silent
val env = ConfigSource.fromMap(
  Map("db.password" -> "production-password", "catalog.featuredBook" -> "Functional Programming in Scala"),
  "env"
)

System.setProperty("bookstore.catalog.pageSize", "50")

val fileSource = ConfigSource.fromFile(applicationConf.toString) match {
  case Right(source) => source
  case Left(error)   => throw error
}

val settings = systemProperties.orElse(env).orElse(fileSource)
```

System properties win over the environment, and the environment wins over the file. `Config.loadWithProvenance` decodes the same case class and also records where each value came from:

```scala mdoc:silent
val loaded = Config.loadWithProvenance[BookstoreConfig](settings) match {
  case Right(provenance) => provenance
  case Left(errors)      => throw errors.head
}
```

The page size came from the system property, the featured book from the environment, and the port from the file:

```scala mdoc
println(loaded.value)
println(loaded.provenanceOf("catalog.pageSize"))
println(loaded.provenanceOf("catalog.featuredBook"))
println(loaded.provenanceOf("server.port"))
```

```scala mdoc:invisible
System.clearProperty("bookstore.catalog.pageSize")
```

Prefix the environment layer, always. An `EnvSource` without a prefix reads every variable of the process, and `ProvenanceMap#dump` then prints all of them, including tokens that have nothing to do with the bookstore.

:::warning[Provenance shows a secret's value]
`ProvenanceMap#provenanceOf` and `ProvenanceMap#dump` in 0.0.56 print the raw text of a `Secret` field: `provenanceOf("db.password")` shows `production-password`, and `dump()` hides a value only when its key name looks sensitive, as `password` does, whatever the field's type. Don't log the provenance of secret fields, and give them names that contain `password`, `secret`, `token` or `credential`.
:::

[Build a Typed Configuration Loader](./typed-config-loader.md) shows more layering with defaults and provenance, and [How to Feed Configuration into a Wire Dependency Graph](./feed-config-into-a-wire-graph.md) hands each section of the layered source to the service that needs it.

## Loading application.conf From the Classpath

Typesafe Config's `ConfigFactory.load()` finds `application.conf` on the classpath. `config-hocon` has no classpath lookup, so a service that ships its configuration inside its jar reads the resource's text itself. When the configuration is one file, `ConfigSource.fromHocon` parses that text:

```scala mdoc:compile-only
import scala.io.Source

import zio.blocks.config._
import zio.blocks.config.hocon._

def resourceText(name: String): Option[String] =
  Option(getClass.getClassLoader.getResourceAsStream(name)).map { in =>
    try Source.fromInputStream(in, "UTF-8").mkString
    finally in.close()
  }

val fromClasspath: Either[ConfigError, ConfigSource] =
  resourceText("application.conf") match {
    case Some(text) => ConfigSource.fromHocon(text, "classpath:application.conf")
    case None       => Left(ConfigError.ParseError("application.conf", "classpath", "existing resource"))
  }
```

`ConfigSource.fromHocon` drops every `include`, so a split configuration needs `HoconParser.parse`. It takes the text of the root file and a function from an include's name to that file's text, and returns the parsed tree, which `HoconValue.flatten` turns into the flat map that `ConfigSource.fromMap` takes. Passing `resourceText` as that function resolves `include "server.conf"` from the classpath. The function can return the text from anywhere, so the same code also works with files embedded in the program. Here it looks the names up in a map that holds the three files of the split configuration:

```scala mdoc:silent
val bundledFiles: Map[String, String] = Map(
  "application.conf" ->
    """include "server.conf"
      |include "catalog.conf"
      |db {
      |  include "db.conf"
      |}
      |""".stripMargin,
  "server.conf"  -> "server { host = \"0.0.0.0\", port = 8080 }\n",
  "catalog.conf" -> "catalog { featuredBook = \"Zionomicon\", pageSize = 20 }\n",
  "db.conf" ->
    """url = "jdbc:postgresql://localhost:5432/bookstore"
      |user = "bookstore"
      |password = "dev-password"
      |poolSize = 10
      |""".stripMargin
)

def parseBundled(files: Map[String, String], root: String): Either[ConfigError, ConfigSource] =
  HoconParser.parse(files(root), files.get) match {
    case Right(tree) => Right(ConfigSource.fromMap(HoconValue.flatten(tree), s"hocon:$root"))
    case Left(error) => Left(ConfigError.ParseError("", s"hocon:$root", "valid HOCON", Some(error)))
  }
```

The bundled files decode to the same settings as the files on disk:

```scala mdoc
println(decode(parseBundled(bundledFiles, "application.conf")))
```

The include function returns text, not a file, so the parser has no folder to resolve names against: every name is looked up as written, `allowedBase` and the size limit don't apply, and an include that the function can't find is skipped. In 0.0.56, `HoconParser.parse` resolves only one level of includes this way: an `include` inside an included file is skipped without an error. Keep every include in the root file, as `bundledFiles` does.

## Loading the Same Settings in the Browser

A Scala.js application in the browser has no files, no environment variables and no system properties. `ConfigSource.fromHocon`, `ConfigSource.fromJson`, `ConfigSource.fromYaml`, `HoconParser.parse` and `Config.load` work the same way on Scala.js as on the JVM; `ConfigSource.fromFile` and `ConfigSource.fromFileWithLimits` don't exist there. So the browser build gets its configuration as text, in one of two ways.

The simplest is to compile the configuration into the program: put the HOCON text in a `String` constant, or the split files in a map like `bundledFiles`, and parse it with `ConfigSource.fromHocon` or `parseBundled` exactly as above. The settings are then fixed at build time, which suits values such as the catalog's page size.

Settings that change per deployment, such as the API host, come from the server at startup. The page fetches the text with the browser's `fetch`, and parses it once the text arrives. This snippet needs Scala.js, so the build of this page doesn't compile it; it compiles with Scala.js 1.22.0 on Scala 2.13 and Scala 3.9:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._

import zio.blocks.config._
import zio.blocks.config.hocon._

def loadCatalog(): Unit =
  for {
    response <- js.Dynamic.global.fetch("/config/catalog.conf").asInstanceOf[js.Promise[js.Dynamic]]
    text     <- response.text().asInstanceOf[js.Promise[String]]
  } ConfigSource.fromHocon(text, "catalog.conf").flatMap(Config.load[CatalogConfig](_)) match {
    case Right(catalog) => println(s"Featured: ${catalog.featuredBook}")
    case Left(errors)   => println(errors)
  }
```

The two layers that come from the process don't apply in the browser. `SysPropSource` is always empty on Scala.js, and `EnvSource` reads `process.env` under Node.js and is empty in a browser. To override embedded defaults per deployment, layer the fetched source over the embedded one with `ConfigSource#orElse`, the same way the environment layered over the file.

Never put the database password in a browser configuration: everything the page downloads, the user can read. The browser's settings are the public part of the configuration, and the secrets stay in the server's files and environment.

## Putting It Together

The complete example writes a split `application.conf` and a `defaults.conf` into a temporary folder, loads them with `ConfigSource.fromFile` locked to that folder, layers system properties and `BOOKSTORE_*` environment variables over them, prints the settings and where three of them came from, and then shows the errors for a file outside the folder and for a broken YAML file:

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `config-examples` project. The JVM examples write their configuration files into a temporary folder when they start, so they need no setup; the classpath example reads the files in `config-examples/src/main/resources/loadconfigurationfiles/`. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/config-examples
```

Each file below matches one section of this guide. To compile them all without running any, run `sbt compile`.

### Loading application.conf From a File

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step1LoadHoconFile.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step1LoadHoconFile.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain loadconfigurationfiles.Step1LoadHoconFile"
```

### Splitting the File With Includes

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step2Includes.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step2Includes.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain loadconfigurationfiles.Step2Includes"
```

### Reusing Values With Substitutions and Overrides

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step3Substitutions.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step3Substitutions.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain loadconfigurationfiles.Step3Substitutions"
```

### Loading Files Safely

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step4SafeLoading.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step4SafeLoading.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain loadconfigurationfiles.Step4SafeLoading"
```

### Loading JSON and YAML Files

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step5JsonAndYaml.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step5JsonAndYaml.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain loadconfigurationfiles.Step5JsonAndYaml"
```

### Overriding File Values From the Environment

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step6EnvironmentOverrides.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step6EnvironmentOverrides.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory, with an environment variable and a system property set. The build runs each example in a separate JVM, so the system property goes into `run / javaOptions`; a `-D` flag on the `sbt` command line would reach only sbt itself:

```bash
BOOKSTORE_DB_PASSWORD=production-password sbt 'set run / javaOptions += "-Dbookstore.catalog.pageSize=50"' "runMain loadconfigurationfiles.Step6EnvironmentOverrides"
```

### Loading application.conf From the Classpath

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step7Classpath.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step7Classpath.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain loadconfigurationfiles.Step7Classpath"
```

### Loading the Same Settings in the Browser

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/Step8EmbeddedConfig.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/Step8EmbeddedConfig.scala:show-line-numbers
```

</details>

`config-examples` runs on the JVM, so this example runs the platform-independent part, the embedded files and `HoconParser.parse`, on the JVM. Run it from the `config-examples` directory:

```bash
sbt "runMain loadconfigurationfiles.Step8EmbeddedConfig"
```

### The Complete Example

<details>
  <summary>config-examples/src/main/scala/loadconfigurationfiles/CompleteExample.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/loadconfigurationfiles/CompleteExample.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
BOOKSTORE_DB_PASSWORD=production-password sbt "runMain loadconfigurationfiles.CompleteExample"
```

## Going Further

- [Formats](../reference/config/formats.md) lists every HOCON, JSON and YAML feature the format modules support.
- [ConfigSource](../reference/config/config-source.md) covers the other ways to build and combine sources, key formats and provenance.
- [Errors](../reference/config/errors.md) describes every `ConfigError` case and how a failed load is reported.
- [Coming from PureConfig, Typesafe Config or zio-config](./config-library-migration.md) moves an existing Typesafe Config or PureConfig application over, including its kebab-case keys.
- [Build a Small JSON Backend from Blocks](./small-json-backend-from-blocks.md) loads its settings as part of a complete service.
