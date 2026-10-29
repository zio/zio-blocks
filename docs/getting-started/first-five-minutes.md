---
id: first-five-minutes
title: "Your First Five Minutes with ZIO Blocks"
sidebar_label: "Your First Five Minutes"
description: "Run one small program that uses three blocks together: Schema decodes JSON, Scope closes a file, and Chunk summarizes the data."
keywords:
  - "Getting Started"
  - "First Program"
  - "Schema"
  - "Scope"
  - "Chunk"
---

In five minutes, we write one small program that uses three blocks together. It reads temperature readings from a file in JSON Lines format (one JSON object per line), decodes each line into a case class, skips the line that is broken, and prints a summary as JSON.

Each block does one job:

- [Schema](./concepts/schema.md) turns JSON into case classes and back.
- [Scope](./concepts/scope.md) opens the file and guarantees that it is closed.
- [Chunk](../reference/chunk.md) holds the decoded readings and computes the summary.

No block needs a runtime, an effect type or any setup: every call on this page is an ordinary method call.

## Add Two Dependencies

The program needs two artifacts; Chunk comes along with Schema. On Scala 2.13 and Scala 3.3 to 3.8, add them to `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema" % "@VERSION@",
  "dev.zio" %% "zio-blocks-scope"  % "@VERSION@"
)
```

On Scala 3.9 and newer, the artifact names start with `zio-blocks-next-` and need `-experimental`; [Installation](./installation.md) covers that case, scala-cli and Scala.js.

## Describe the Data with Schema

We start with two case classes: `Reading` is one line of the file, and `Summary` is the result we print. Giving each one a derived `Schema` is all it takes to read and write it as JSON:

```scala mdoc:silent
import zio.blocks.schema._

final case class Reading(sensor: String, celsius: Double)

object Reading {
  implicit val schema: Schema[Reading] = Schema.derived
}

final case class Summary(readings: Int, averageCelsius: Double, hottestSensor: String)

object Summary {
  implicit val schema: Schema[Summary] = Schema.derived
}
```

With the schema in scope, every `Reading` has a `toJsonString` method, and every `String` has a `fromJson` method that returns either an error or a value:

```scala mdoc
println(Reading("kitchen", 21.5).toJsonString)

"""{"sensor":"garage","celsius":12.0}""".fromJson[Reading]
```

## Read the File with Scope

Now we need a file to read. This block writes four readings to a temporary file; the third one is broken on purpose, because its temperature is a string:

```scala mdoc:silent
import java.nio.file.{Files, Path}

val path: Path = Files.createTempFile("readings", ".jsonl")

Files.writeString(
  path,
  """{"sensor":"kitchen","celsius":21.5}
    |{"sensor":"garage","celsius":12.0}
    |{"sensor":"attic","celsius":"hot"}
    |{"sensor":"office","celsius":23.5}
    |""".stripMargin
)
```

A file reader must be closed after use, even when reading fails. `Scope.global.scoped` opens a region of code; `allocate` registers the reader with that region, and the region closes the reader when it ends. The `$(reader)(...)` call is how we use the reader inside the region: Scope checks at compile time that the reader itself never escapes it, while the list of lines does:

```scala mdoc:silent
import java.io.BufferedReader
import scala.jdk.CollectionConverters._
import zio.blocks.scope._

def readLines(path: Path): List[String] =
  Scope.global.scoped { scope =>
    import scope._
    val reader: $[BufferedReader] = allocate(
      Resource.acquireRelease {
        println("Opened the readings file")
        Files.newBufferedReader(path)
      } { reader =>
        reader.close()
        println("Closed the readings file")
      }
    )
    $(reader)(_.lines().iterator().asScala.toList)
  }
```

When we call it, the file opens, the lines come back, and the file is already closed by the time we hold the result:

```scala mdoc
val lines = readLines(path)
```

## Decode Each Line

Each line decodes to an `Either`. Splitting the results with `List#partitionMap` gives us the errors on one side and the readings on the other:

```scala mdoc:silent
val (errors, parsed) = lines.map(_.fromJson[Reading]).partitionMap(identity)
```

Three lines decode, and the broken one becomes an error:

```scala mdoc
parsed

errors.foreach(error => println(s"Skipped a line: ${error.getMessage}"))
```

The error message names the field that failed and the reason, so a bad line in a large file is easy to find.

## Summarize with Chunk

`Chunk` is the sequence type that all blocks share. It works like a `Vector`, and it stores primitives such as `Double` without boxing them. We compute the summary from a `Chunk` of readings, then print it as JSON with the schema we derived for `Summary`:

```scala mdoc
import zio.blocks.chunk.Chunk

def summarize(readings: Chunk[Reading]): Summary =
  Summary(
    readings = readings.size,
    averageCelsius = readings.map(_.celsius).sum / readings.size,
    hottestSensor = readings.maxBy(_.celsius).sensor
  )

println(summarize(Chunk.fromIterable(parsed)).toJsonString)
```

```scala mdoc:invisible
Files.delete(path)
```

## The Whole Program

Here is the complete program as one file. Run it from the `zio-blocks-examples` directory of the repository with `sbt "gettingStarted/runMain gettingstarted.FirstFiveMinutes"`:

```scala mdoc:embed:zio-blocks-examples/getting-started/src/main/scala/gettingstarted/FirstFiveMinutes.scala
```

## What You Just Used

The program uses three blocks, and each one has a page in this section or in the reference:

| Block  | What it does here                                   | Learn more                                       |
| ------ | --------------------------------------------------- | ------------------------------------------------ |
| Schema | Decodes each JSON line and encodes the summary      | [Schema in a Nutshell](./concepts/schema.md)     |
| Scope  | Opens the file and closes it after reading          | [Scope in a Nutshell](./concepts/scope.md)       |
| Chunk  | Holds the readings and computes the summary         | [Chunk reference](../reference/chunk.md)         |

Next, [How ZIO Blocks Thinks](./how-zio-blocks-thinks.md) explains why the blocks fit together this easily.
