---
id: read-and-write-json-lines
title: "How to Read and Write JSON Lines with Streams"
description: "Read a JSON Lines (NDJSON) file as a stream of typed records, skip or fail on bad lines with their line numbers, and write records back one per line."
keywords:
  - "JSON Lines"
  - "NDJSON"
  - "Streams"
  - "JSON Codec"
  - "Bad Lines"
---

In this guide, we read a sensor log in JSON Lines format, one JSON value per line, with ZIO Blocks Streams. JSON Lines is the usual format for logs, exports and LLM training data, because a writer can append a record without rewriting the file. By the end, you will have a stream that decodes each line into a typed `SensorReading`, two ways to handle a bad line (skip and count it, or fail with its line number), and a writer that puts one record on each line of a new file.

We start with the code a reader writes without a library, then build the stream one section at a time:

- Reading the lines of a file as a stream.
- Decoding each line into a record.
- Skipping bad lines and counting them.
- Failing at the first bad line, with its line number.
- Writing records back, one per line.

## The Problem

A JSON Lines file is easy to produce and easy to get wrong. One truncated line, or one string where a number belongs, can stop an import that otherwise worked. Without a library, a reader usually matches each line against a pattern and turns the captures into values:

```scala mdoc:compile-only
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

def readSensorLog(path: Path): List[(String, Double, Long)] = {
  val pattern = """\{"sensor":"([^"]*)","celsius":([-0-9.]+),"sample":(\d+)\}""".r
  Files.readAllLines(path).asScala.toList.map { case pattern(sensor, celsius, sample) =>
    (sensor, celsius.toDouble, sample.toLong)
  }
}
```

This version has two problems. A line that does not match the pattern throws a `MatchError`, which ends the whole import, and the pattern breaks as soon as a field is added or reordered. Writing has the same weakness: each record is formatted by string interpolation, so a sensor name that contains a quote produces invalid JSON.

In this guide, we let a `Schema` derive the JSON codec, and let the stream decide what happens to each bad line.

## Prerequisites

Add the streams module to your `build.sbt`. The schema module comes with it, so `Schema` and the JSON codec are available too:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "@VERSION@"

// Scala 3.9 and newer, which also needs -experimental
libraryDependencies += "dev.zio" %% "zio-blocks-next-streams" % "@VERSION@"
scalacOptions += "-experimental"
```

[How to Add ZIO Blocks to Your Build](../getting-started/installation.md) explains which artifact name each Scala version uses. The terminals in this guide, such as `Stream#run`, `Stream#runCollect` and `Stream#runForeach`, run on the JVM. On Scala.js, use the `*Async` terminals instead, such as `Stream#runCollectAsync`.

This guide assumes you have run a stream before, as in [Your First Streaming Pipeline](./your-first-streaming-pipeline.md), and that you can derive a schema with `Schema.derived`, as in [Your First Schema](./your-first-schema.md).

The snippets below share these imports:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.streams._

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._
```

## The Core Model

The sensor log holds one reading per line. We model a reading as a case class, and a line that cannot be decoded as a `BadLine` that records where it is and why it failed:

```scala mdoc:silent
final case class SensorReading(sensor: String, celsius: Double, sample: Long)

object SensorReading {
  implicit val schema: Schema[SensorReading] = Schema.derived
}

final case class BadLine(line: Long, text: String, reason: String)
```

`Schema.derived` gives `SensorReading` a JSON codec. The methods `SensorReading#toJsonString` and `String#fromJson` use that default codec, so one line of the file maps to one `SensorReading`.

We write a sample log to a folder under the system's temporary directory, so that every snippet runs on any machine. Lines 3 and 5 are bad: line 3 has a string where `celsius` should be a number, and line 5 is cut off before the value of `sample`:

```scala mdoc:silent
val shop: Path = Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "sensor-log"))

val sensorLog: Path = shop.resolve("sensors.jsonl")

Files.writeString(
  sensorLog,
  """{"sensor":"boiler-7","celsius":21.5,"sample":1}
    |{"sensor":"boiler-7","celsius":22.0,"sample":2}
    |{"sensor":"boiler-7","celsius":"hot","sample":3}
    |{"sensor":"attic-2","celsius":14.25,"sample":4}
    |{"sensor":"attic-2","celsius":14.5,"sample":
    |{"sensor":"cellar-1","celsius":9.0,"sample":6}
    |""".stripMargin
)
```

Printing the file shows what the stream will read:

```scala mdoc
print(Files.readString(sensorLog))
```

## Reading the Lines of a File

Streams has no helper that splits a file into lines, so we use the JDK's `BufferedReader#lines` for the splitting, inside `Stream.fromAcquireRelease`, as the streaming guides do. The stream owns the reader: it opens the file when the stream runs, and closes it when the stream ends, fails or stops early. The default `release` closes any `AutoCloseable`, so we do not pass one. The reader becomes a stream like this:

```scala mdoc:silent
def readLines(path: Path): Stream[Nothing, String] =
  Stream.fromAcquireRelease(Files.newBufferedReader(path))(reader =>
    Stream.fromIterator(reader.lines().iterator().asScala)
  )
```

Running the stream with `Stream#runForeach` prints each line as it arrives:

```scala mdoc
readLines(sensorLog).runForeach(line => println(line))
```

Each line prints on its own line, and the terminal returns `Right(())`, which means the run succeeded and returns no value.

## Decoding Each Line into a Record

Each line is a JSON object, so we decode it with `String#fromJson`, which returns `Either[SchemaError, SensorReading]`. We want to know which line failed, so we number the lines first. `Stream#mapAccum` carries a counter from one element to the next, and the decoded error becomes a `BadLine`:

```scala mdoc:silent:nest
val numbered: Stream[Nothing, (Long, String)] =
  readLines(sensorLog).mapAccum(1L)((n, text) => (n + 1, (n, text)))

val decoded: Stream[Nothing, Either[BadLine, SensorReading]] =
  numbered.map { case (n, text) =>
    text.fromJson[SensorReading].left.map(error => BadLine(n, text, error.getMessage))
  }
```

Running `decoded` shows one result per line:

```scala mdoc
decoded.runForeach(result => println(result))
```

Lines 3 and 5 come out as `Left` values. Each message names the field where decoding stopped, the `celsius` and `sample` fields, and the stream keeps going.

## Skipping Bad Lines and Counting Them

Some imports should keep every good line and report the rest. `Sink.foldLeft` reads the stream once, and its function decides what each result adds to the accumulator. A bad line is logged and counted, and a good reading is kept:

```scala mdoc
final case class Import(readings: Vector[SensorReading], skipped: Int)

decoded.run(
  Sink.foldLeft(Import(Vector.empty, 0)) { (acc, next) =>
    next match {
      case Right(reading) => acc.copy(readings = acc.readings :+ reading)
      case Left(bad)      =>
        println(s"skipped line ${bad.line}: ${bad.reason}")
        acc.copy(skipped = acc.skipped + 1)
    }
  }
).foreach { imported =>
  println(s"kept ${imported.readings.size} readings, skipped ${imported.skipped} lines")
  imported.readings.foreach(println)
}
```

The skipped lines are logged while the stream runs, and the summary comes after. The `Right` from `Stream#run` holds the accumulator, so the code after `foreach` sees the kept readings and the count together.

## Failing at the First Bad Line

Some imports must not continue past a bad line, for example when a later step depends on every record. We turn the decode error into a typed failure with `Stream.fail`, and `Stream#flatMap` replaces each line with either one reading or that failure. The stream's error type is now `BadLine`:

```scala mdoc:silent:nest
val strict: Stream[BadLine, SensorReading] =
  numbered.flatMap { case (n, text) =>
    text.fromJson[SensorReading] match {
      case Right(reading) => Stream.succeed(reading)
      case Left(error)    => Stream.fail(BadLine(n, text, error.getMessage))
    }
  }
```

Running `strict` with `Stream#runCollect` returns the first failure, with its line number:

```scala mdoc
strict.runCollect
```

The readings before line 3 are emitted before the failure, but `Stream#runCollect` returns only the `Left`. A terminal that folds the elements as they arrive, such as the one in the previous section, would see them before the failure.

## Writing One Record Per Line

To write a record, we turn it into its JSON text, add a newline, and send the bytes to the output. `Stream.fromIterable` makes a stream of records, and `Stream#flatMap` turns each record into the bytes of one line. `Sink.fromOutputStream` writes the bytes in order. The sink does not close the output stream, so the code closes it in `finally`:

```scala mdoc:silent:nest
val readings: Seq[SensorReading] = Seq(
  SensorReading("boiler-7", 21.5, 1L),
  SensorReading("attic-2", 14.25, 2L),
  SensorReading("cellar-1", 9.0, 3L)
)

def jsonLine(reading: SensorReading): Array[Byte] =
  (reading.toJsonString + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)

def writeJsonLines(path: Path, records: Seq[SensorReading]): Either[Nothing, Unit] = {
  val out = Files.newOutputStream(path)
  try
    Stream
      .fromIterable(records)
      .flatMap(reading => Stream.fromIterable(jsonLine(reading).toIndexedSeq))
      .run(Sink.fromOutputStream(out))
  finally out.close()
}
```

We write three readings to a new file and print its content:

```scala mdoc
writeJsonLines(shop.resolve("readings.jsonl"), readings)
print(Files.readString(shop.resolve("readings.jsonl")))
```

Each record is one line, ending with a newline, so the file can be appended to and read again with the code above.

:::tip
`Schema` derives the codec that `SensorReading#toJsonString` uses, so the field names in the file are the case class field names. To change them, see [Customize JSON Encoding](./customize-json-encoding.md).
:::

## Putting It Together

The complete example reads the sample log, skips and counts the bad lines, writes the good readings to a new file, and prints that file. It differs from the sections in three ways: it writes to a fresh temporary directory instead of the `sensor-log` folder, it writes `clean.jsonl` instead of `readings.jsonl`, and it deletes its files when it finishes. The complete example is:

```scala mdoc:embed:zio-blocks-examples/json-lines/src/main/scala/readandwritejsonlines/CompleteExample.scala:show-line-numbers
```

Running it prints the skipped lines, then the summary, then the new file:

```text
skipped line 3: illegal number at: .celsius
skipped line 5: unexpected end of input at: .sample
wrote 4 readings, skipped 2 lines
clean.jsonl:
{"sensor":"boiler-7","celsius":21.5,"sample":1}
{"sensor":"boiler-7","celsius":22.0,"sample":2}
{"sensor":"attic-2","celsius":14.25,"sample":4}
{"sensor":"cellar-1","celsius":9.0,"sample":6}
```

## Running the Examples

All runnable code from this guide is in [`zio-blocks-examples/json-lines/src/main/scala/readandwritejsonlines/`](https://github.com/zio/zio-blocks/tree/main/zio-blocks-examples/json-lines/src/main/scala/readandwritejsonlines). Every file uses the shared `Model.scala` in the same folder. Run a file from that folder with `scala-cli run <File>.scala Model.scala`, or from `zio-blocks-examples` with `sbt "jsonLines/runMain readandwritejsonlines.<ObjectName>"`.

## Going Further

- [Stream Files and Sockets Without Leaking Resources](./stream-files-and-sockets.md) covers the resource handling behind `Stream.fromAcquireRelease`, and reading from a socket.
- [Read and Write CSV and TSV Files](./read-and-write-csv-and-tsv-files.md) applies the same bad-row policies to tabular files.
- [Stream reference](../reference/streams/core/stream.md) lists every operator and terminal this guide uses, and [Sink reference](../reference/streams/core/sink.md) covers `Sink.foldLeft` and `Sink.fromOutputStream`.
