---
id: stream-files-and-sockets
title: "How to Stream Files and Sockets Without Leaking Resources"
description: "Read files, NIO channels and sockets as streams that close their handles on success, failure, early stop and missing input."
keywords:
  - "Resource Safety"
  - "File Streaming"
  - "Sockets"
  - "fromAcquireRelease"
  - "fromResource"
  - "NioStreams"
---

In this guide, we read a Scala bookshop's inventory export with ZIO Blocks Streams so that every file, channel and socket the stream opens is closed again, whether the stream finishes, fails, stops early or never finds its input. By the end, you will have a stream that opens a file only when it runs, a typed error for a missing file, two resources that close in reverse order, a file copy that closes both ends, and a socket read that disconnects when it is done.

We start with one file and one acquire/release pair, then break the stream on purpose in three ways, move to scope `Resource` values, compare the managed and unmanaged I/O constructors, and finish with sinks, sockets and finalizers:

- [Reading a File That Always Closes](#reading-a-file-that-always-closes)
- [Stopping Early Still Closes the File](#stopping-early-still-closes-the-file)
- [Failing Mid-Stream Still Closes the File](#failing-mid-stream-still-closes-the-file)
- [Reporting a Missing File as a Typed Error](#reporting-a-missing-file-as-a-typed-error)
- [Reusing and Composing Scope Resources](#reusing-and-composing-scope-resources)
- [Managed and Unmanaged Handles](#managed-and-unmanaged-handles)
- [Copying a File Through a Sink](#copying-a-file-through-a-sink)
- [Reading from a Socket](#reading-from-a-socket)
- [Cleanup That Is Not a Resource](#cleanup-that-is-not-a-resource)
- [Pulling Elements by Hand](#pulling-elements-by-hand)

## The Problem

A file handle must be closed exactly once, after the last row is read, and also when reading stops early or fails halfway. A lazy sequence makes that hard: the code that opens the file returns before the code that reads it runs, so there is no single place where `try`/`finally` covers both.

Without a streaming library, reading the inventory usually goes one of three ways:

```scala mdoc:compile-only
import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

// Lazy, but nobody ever closes the reader.
def inventoryRowsLeaky(path: Path): Iterator[String] = {
  val reader: BufferedReader = Files.newBufferedReader(path)
  reader.lines().iterator().asScala
}

// Closes the reader, but before the caller has read a single row.
def inventoryRowsClosedTooSoon(path: Path): Iterator[String] = {
  val reader: BufferedReader = Files.newBufferedReader(path)
  try reader.lines().iterator().asScala
  finally reader.close()
}

// Correct, but every caller repeats it, and the rows must not escape the block.
def lowStockTitles(path: Path): List[String] = {
  val reader: BufferedReader = Files.newBufferedReader(path)
  try {
    reader
      .lines()
      .iterator()
      .asScala
      .filter(row => row.split(",")(1).toInt < 5)
      .map(row => row.split(",")(0))
      .toList
  } finally reader.close()
}
```

The first version leaks one file handle per call until the process runs out of them. The second fails on the first read with `IOException: Stream closed`. The third works, but it reads everything into a `List`, and each new question about the inventory needs another copy of the `try`/`finally`. Sockets have the same problem with a connection instead of a file.

In this guide, we let the stream own the handle: it opens the handle when it starts, and closes it when it stops, for whatever reason it stops.

## Prerequisites

Add the streams module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-streams" % "@VERSION@"
```

The streams module depends on the scope module, so `zio.blocks.scope.Resource` and `Scope` are available without another dependency. On the JVM, the streams module needs JDK 21 or newer. `NioStreams`, `NioSinks` and `Stream#start` exist only on the JVM; the other constructors on this page also work on Scala.js.

This guide assumes you have run a [`Stream`](../reference/streams/core/stream.md) before and know that every terminal returns an `Either` with typed failures on the `Left`. The beginner tutorial "Your First Streaming Pipeline" covers that ground. For `Resource` and `Scope`, see [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md).

Most snippets below share these imports:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.streams._

import java.io.BufferedReader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
```

The bookshop exports its inventory as one `title,copies` row per book. We write a copy to a `bookshop` directory under the system's temporary directory so that every snippet runs on any machine:

```scala mdoc:silent
import java.nio.file.Paths

val shop: Path = Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "bookshop"))

val inventory: Path = shop.resolve("inventory.csv")

Files.writeString(
  inventory,
  """Zionomicon,12
    |Programming in Scala,7
    |Functional Programming in Scala,3
    |Scala for the Impatient,0
    |Hands-on Scala Programming,4
    |""".stripMargin
)
```

## Reading a File That Always Closes

`Stream.fromAcquireRelease` takes three parts: `acquire` opens the resource, `release` closes it, and a function receives the open resource and returns the stream that reads it. We print a line in `acquire` and in `release` so that the output shows when each one runs:

```scala mdoc:silent
def inventoryRows(path: Path): Stream[Nothing, String] =
  Stream.fromAcquireRelease(
    acquire = {
      println(s"opened ${path.getFileName}")
      Files.newBufferedReader(path)
    },
    release = (reader: BufferedReader) => {
      reader.close()
      println(s"closed ${path.getFileName}")
    }
  )(reader => Stream.fromIterator(reader.lines().iterator().asScala))
```

`acquire` is a by-name parameter, so calling `inventoryRows` opens nothing. Each run of the stream evaluates `acquire` once, builds the inner stream from `BufferedReader#lines`, and calls `release` exactly once when that run ends.

Running it with `Stream#runForeach` prints each row as it arrives:

```scala mdoc
inventoryRows(inventory).runForeach(row => println(row))
```

The file opens before the first row and closes after the last one. When the resource is an `AutoCloseable`, as a `BufferedReader` is, you can leave `release` out: its default calls `close()`. We keep the explicit `release` on this page only for the printed line.

## Stopping Early Still Closes the File

`Stream#take` stops pulling after a given number of elements. The rest of the file is never read, and the stream still closes it:

```scala mdoc
inventoryRows(inventory).take(2).runForeach(row => println(row))
```

`closed inventory.csv` appears after two rows, while three rows are still unread. The same holds for `Stream#takeWhile`, for `Stream#head`, and for any sink that finishes before the input ends.

## Failing Mid-Stream Still Closes the File

A second export has a damaged row, where the copy count is a word instead of a number:

```scala mdoc:silent
val damaged: Path = shop.resolve("inventory-damaged.csv")

Files.writeString(
  damaged,
  """Zionomicon,12
    |Programming in Scala,seven
    |Functional Programming in Scala,3
    |""".stripMargin
)

final case class Stock(title: String, copies: Int)
```

Parsing with `String#toInt` throws on the damaged row. A thrown exception is a defect: the terminal rethrows it instead of returning a `Left`, and the stream closes the file on the way out:

```scala mdoc
def parseOrThrow(row: String): Stock = {
  val fields = row.split(",")
  Stock(fields(0), fields(1).toInt)
}

try println(inventoryRows(damaged).map(parseOrThrow).runCollect)
catch { case e: NumberFormatException => println(s"thrown: $e") }
```

To make a bad row an expected outcome instead of a crash, we fail the stream with a typed error. `Stream.fail` emits no elements and fails with its argument, and `Stream#flatMap` lets each row choose between a one-element stream and a failed one:

```scala mdoc
final case class BadRow(row: String)

def parse(row: String): Stream[BadRow, Stock] = {
  val fields = row.split(",")
  fields(1).toIntOption match {
    case Some(copies) => Stream.succeed(Stock(fields(0), copies))
    case None         => Stream.fail(BadRow(row))
  }
}

println(inventoryRows(damaged).flatMap(parse).runCollect)
```

The run ends with `Left(BadRow(...))`, and the file is closed before the terminal returns, exactly as on success.

:::warning[Recover on the outer stream]
If you add recovery with `Stream#catchAll`, attach it to the outer stream, as in `inventoryRows(damaged).flatMap(parse).catchAll(...)`, not to the stream that `parse` returns. In 0.0.56, a blocking terminal such as `Stream#runCollect` throws the internal `Stream$AsyncBoundaryRequired$` when `Stream#catchAll` sits inside `Stream#flatMap` and a stateful operator such as `Stream#grouped` follows it.
:::

## Reporting a Missing File as a Typed Error

`acquire` can fail too. When the file does not exist, `Files.newBufferedReader` throws `NoSuchFileException`, and like any exception thrown by `acquire`, it is a defect that the terminal rethrows:

```scala mdoc
val missing: Path = shop.resolve("inventory-2025.csv")

try println(inventoryRows(missing).runCollect)
catch { case e: java.nio.file.NoSuchFileException => println(s"thrown: $e") }
```

`opened inventory-2025.csv` is printed because our `println` runs before `Files.newBufferedReader` throws. No `closed` line follows: nothing was acquired, so there is nothing to release.

A missing input file is often a normal condition, such as an export that has not arrived yet. `Stream.attempt` runs a block and turns a non-fatal exception into a typed `Throwable` error. Opening the file with `Stream.attempt` and handing the open reader to `Stream.fromAcquireRelease` makes the failure a `Left` and keeps the close guarantee once the file is open:

```scala mdoc
def inventoryRowsOrError(path: Path): Stream[Throwable, String] =
  Stream.attempt(Files.newBufferedReader(path)).flatMap { reader =>
    Stream.fromAcquireRelease(reader)(r => Stream.fromIterator(r.lines().iterator().asScala))
  }

println(inventoryRowsOrError(missing).runCollect)
println(inventoryRowsOrError(inventory).take(1).runCollect)
```

The missing file comes back as `Left(java.nio.file.NoSuchFileException: ...)`, and the existing file reads as before. Here `release` is left out, so the default closes the reader.

## Reusing and Composing Scope Resources

A [`Resource`](../reference/resource-management/resource.md) from the scope module describes how to acquire and release a value, independently of any stream. `Stream.fromResource` runs one in a fresh `Scope` for each run of the stream, passes the acquired value to the function, and closes the scope when the run ends. `Resource.acquireRelease` builds a `Resource` from an acquire block and a release function:

```scala mdoc:silent
import zio.blocks.scope.Resource
import java.io.BufferedWriter

def openReader(path: Path): Resource[BufferedReader] =
  Resource.acquireRelease {
    println(s"opened ${path.getFileName}")
    Files.newBufferedReader(path)
  } { reader =>
    reader.close()
    println(s"closed ${path.getFileName}")
  }

def openWriter(path: Path): Resource[BufferedWriter] =
  Resource.acquireRelease {
    println(s"opened ${path.getFileName}")
    Files.newBufferedWriter(path)
  } { writer =>
    writer.close()
    println(s"closed ${path.getFileName}")
  }
```

Reading the inventory through `openReader` behaves like `inventoryRows`:

```scala mdoc
println(
  Stream
    .fromResource(openReader(inventory))(reader => Stream.fromIterator(reader.lines().iterator().asScala))
    .take(1)
    .runCollect
)
```

The difference shows when one stream needs several resources. `Resource#flatMap` acquires the second resource after the first, so a `for` comprehension describes both. Here the stream reads the inventory and writes every title with fewer than five copies to a restock list:

```scala mdoc:silent
val restock: Path = shop.resolve("restock.csv")

val inventoryAndRestock: Resource[(BufferedReader, BufferedWriter)] =
  for {
    reader <- openReader(inventory)
    writer <- openWriter(restock)
  } yield (reader, writer)
```

Running the stream with `Stream#runDrain` writes the list, and the output shows the order in which the two files open and close:

```scala mdoc
println(
  Stream
    .fromResource(inventoryAndRestock) { case (reader, writer) =>
      Stream
        .fromIterator(reader.lines().iterator().asScala)
        .filter(row => row.split(",")(1).toInt < 5)
        .tapEach { row =>
          writer.write(row)
          writer.newLine()
        }
    }
    .runDrain
)

print(Files.readString(restock))
```

The writer opens last and closes first: the scope releases resources in the reverse order of acquisition, so the restock file is flushed and closed before the inventory it was read from. `Resource#zip` combines two resources with the same order.

The two constructors differ in who describes the resource:

| Situation                                                                                  | Use                         |
| ------------------------------------------------------------------------------------------ | --------------------------- |
| One resource, opened and closed right where the stream is built                            | `Stream.fromAcquireRelease` |
| A `Resource` value that already exists, for example one shared with code that uses `Scope` | `Stream.fromResource`       |
| Several resources that must open in order and close in reverse order                       | `Stream.fromResource`       |

## Managed and Unmanaged Handles

The streams module also builds streams from handles you have already opened: `Stream.fromInputStream` and `NioStreams.fromChannel` are **managed**, so the stream closes the handle when the run ends. `Stream.fromInputStreamUnmanaged` and `NioStreams.fromChannelUnmanaged` read the same way but never close it; the caller owns the handle. All four emit `Byte` elements and report read failures as a typed `IOException`.

Unlike `acquire` in `Stream.fromAcquireRelease`, the handle is an ordinary argument, so it is open from the moment the stream is built. A managed stream closes it after the first run, and a second run reads a closed handle. The helper `text` turns the collected bytes back into a `String`:

```scala mdoc
def text(bytes: Chunk[Byte]): String = new String(bytes.toArray, UTF_8)

val managed: Stream[java.io.IOException, Byte] =
  Stream.fromInputStream(Files.newInputStream(inventory))

println(managed.take(10).runCollect.map(text))
println(managed.take(10).runCollect.map(text))
```

The second run fails with `ClosedChannelException`, because the first run closes the handle from `Files.newInputStream`. A managed stream built from a handle is good for one run, and if it never runs, the handle is never closed. To get a stream that opens a fresh handle on every run, open the handle in `acquire` and read it with the unmanaged constructor, so that only `Stream.fromAcquireRelease` closes it:

```scala mdoc
val rerunnable: Stream[java.io.IOException, Byte] =
  Stream.fromAcquireRelease(Files.newInputStream(inventory))(in => Stream.fromInputStreamUnmanaged(in))

println(rerunnable.take(10).runCollect.map(text))
println(rerunnable.take(10).runCollect.map(text))
```

The unmanaged constructors are right when the stream borrows a handle that something else owns: `System.in`, a socket that the program keeps using after the stream ends, or a channel managed by a surrounding `Using` or `Scope`. Here the caller opens a `FileChannel` and keeps it:

```scala mdoc:silent
import java.nio.channels.FileChannel

val channel: FileChannel = FileChannel.open(inventory)
```

Reading ten bytes through the unmanaged constructor leaves the channel open:

```scala mdoc
println(NioStreams.fromChannelUnmanaged(channel).take(10).runCollect.map(text))
println(channel.isOpen)

channel.close()
```

`NioStreams.fromChannel` is the managed counterpart: given the same channel, it would have closed it at the end of the run.

| Constructor                                  | Who closes the handle      | Running the stream twice             |
| -------------------------------------------- | -------------------------- | ------------------------------------ |
| `Stream.fromInputStream(is)`                 | The stream, after each run | The second run reads a closed handle |
| `Stream.fromInputStreamUnmanaged(is)`        | The caller                 | Both runs read the same open handle  |
| `NioStreams.fromChannel(ch)`                 | The stream, after each run | The second run reads a closed handle |
| `NioStreams.fromChannelUnmanaged(ch)`        | The caller                 | Both runs read the same open handle  |
| `Stream.fromAcquireRelease(open)(unmanaged)` | The stream, after each run | Each run opens a new handle          |

`Stream.fromJavaReader` and `Stream.fromJavaReaderUnmanaged` follow the same rule for a `java.io.Reader` of characters.

## Copying a File Through a Sink

On the writing side, `NioSinks.fromChannel` writes every byte of a stream to a `WritableByteChannel` and does not close it; `Sink.fromOutputStream` does the same for an `OutputStream`. The stream owns the input, so the code that runs the stream owns the output. Here `scala.util.Using.resource` closes the backup file even if the copy throws:

```scala mdoc
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import scala.util.Using

val backup: Path = shop.resolve("inventory-backup.csv")

val copied: Either[java.io.IOException, Unit] =
  Using.resource(FileChannel.open(backup, CREATE, TRUNCATE_EXISTING, WRITE)) { out =>
    NioStreams.fromChannel(FileChannel.open(inventory)).run(NioSinks.fromChannel(out))
  }

println(Files.readString(backup) == Files.readString(inventory))
```

`Stream#run` returns the sink's result in a `Right`, here `Right(())`, or the first `IOException` in a `Left`. The input channel is opened inside the `Using` block and run at once, so the managed `NioStreams.fromChannel` closes it after this single run.

## Reading from a Socket

A `SocketChannel` is a `ReadableByteChannel`, so the NIO constructors read sockets the same way they read files. To keep the example self-contained, a small server on the loopback address sends the inventory to every client and hangs up:

```scala mdoc:silent
import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.nio.channels.SocketChannel

val server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
val serverThread = new Thread(() =>
  for (_ <- 1 to 2) {
    val client = server.accept()
    try client.getOutputStream.write(Files.readAllBytes(inventory))
    catch { case _: java.io.IOException => () }
    finally client.close()
  }
)
serverThread.start()

val address = new InetSocketAddress(InetAddress.getLoopbackAddress, server.getLocalPort)
```

`NioStreams.fromChannel(SocketChannel.open(address))` would connect as soon as the stream is built. Connecting in `acquire` instead connects on each run and disconnects when the run ends:

```scala mdoc:silent
val inventoryFeed: Stream[java.io.IOException, Byte] =
  Stream.fromAcquireRelease(
    acquire = {
      val socket = SocketChannel.open(address)
      println("connected")
      socket
    },
    release = (socket: SocketChannel) => {
      socket.close()
      println("disconnected")
    }
  )(socket => NioStreams.fromChannelUnmanaged(socket))
```

The first run reads until the server hangs up, and the second stops after ten bytes:

```scala mdoc
print(inventoryFeed.runCollect.map(text).getOrElse(""))
println(inventoryFeed.take(10).runCollect.map(text))
```

Both runs disconnect, including the one that stops long before the server is done. We stop the server once both clients are served:

```scala mdoc:silent
serverThread.join()
server.close()
```

## Cleanup That Is Not a Resource

Some cleanup has no handle to close: deleting a temporary file, releasing a lock, or logging that a job is done. `Stream#ensuring` runs a block exactly once when a run of the stream ends, after completion, failure or early termination. Here the stream reads the backup made earlier and deletes it when it is done:

```scala mdoc
val fromBackup: Stream[Nothing, String] =
  inventoryRows(backup).ensuring {
    Files.deleteIfExists(backup)
    println("deleted inventory-backup.csv")
  }

println(fromBackup.take(1).runCollect)
println(Files.exists(backup))
```

The reader closes first, because it belongs to the inner stream, and the finalizer runs after it. An exception thrown by the finalizer is a defect. `Stream#ensuringAsync` takes an `Async[Unit]` finalizer for asynchronous runs.

## Pulling Elements by Hand

Sometimes code needs to read a stream piece by piece instead of handing it to a terminal, for example to read a header first and decide what to do with the rest. On the JVM, `Stream#start` opens the stream as a `Reader` that belongs to a `Scope`, and closing the scope closes the reader and every resource behind it:

```scala mdoc
import zio.blocks.scope.Scope

println(
  Scope.global.scoped { implicit scope =>
    import scope._
    val reader = inventoryRows(inventory).start
    $(reader)(r => r.readN[String](2).mkString(" | "))
  }
)
```

`closed inventory.csv` is printed when the `scoped` block ends, not when `Reader#readN` returns. The `$` call is how a scope lets you use a value it owns; the [Scope tutorial](./compile-time-resource-safety-with-scope.md) explains why the reader cannot leave the block.

## Putting It Together

The complete example is a nightly restock job. It downloads the inventory export from a loopback server into a staging file, reads the staging file and writes a restock list through two composed `Resource` values, fails with a typed error on a damaged row, and deletes the staging file with `Stream#ensuring`:

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `streams-examples` project. Clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/streams-examples
```

The examples share the sample inventory files, which live in one file:

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/SampleFiles.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/SampleFiles.scala:show-line-numbers
```

</details>

Each example is an object with a `def main` method rather than `extends App`: on the JVM, a blocking terminal that runs inside an object initializer can deadlock once `Stream#catchAll` is in the pipeline.

### Read a File and Stop Early

This example reads the inventory with `Stream.fromAcquireRelease`, then reads only the first two rows, and prints when the file opens and closes.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step1ReadInventory.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step1ReadInventory.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step1ReadInventory"
```

### Fail Mid-Stream

This example parses the damaged export twice, once with a thrown exception and once with a typed `BadRow` error, and shows the file closed both times.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step2FailMidStream.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step2FailMidStream.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step2FailMidStream"
```

### Report a Missing File

This example reads a file that does not exist, first as a thrown defect and then as a typed error with `Stream.attempt`.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step3MissingFile.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step3MissingFile.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step3MissingFile"
```

### Compose Scope Resources

This example reads the inventory through a `Resource`, then writes the restock list with a reader and a writer composed in a `for` comprehension, and prints the reverse closing order.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step4ComposeResources.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step4ComposeResources.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step4ComposeResources"
```

### Managed and Unmanaged Handles

This example runs a managed `Stream.fromInputStream` twice, a stream that opens a new handle on each run, and an unmanaged `NioStreams.fromChannelUnmanaged` that leaves its channel open.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step5ManagedAndUnmanaged.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step5ManagedAndUnmanaged.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step5ManagedAndUnmanaged"
```

### Copy a File

This example copies the inventory to a backup file with `NioSinks.fromChannel` and closes the backup with `Using.resource`.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step6CopyFile.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step6CopyFile.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step6CopyFile"
```

### Read from a Socket

This example starts a server on the loopback address, reads the inventory from it twice over a `SocketChannel`, and prints when each connection opens and closes.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step7ReadSocket.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step7ReadSocket.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step7ReadSocket"
```

### Finalize and Pull by Hand

This example deletes a staging file with `Stream#ensuring`, then reads two rows by hand with `Stream#start` inside a `Scope`.

<details>
  <summary>streams-examples/src/main/scala/streamfilesandsockets/Step8EnsuringAndStart.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/streamfilesandsockets/Step8EnsuringAndStart.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain streamfilesandsockets.Step8EnsuringAndStart"
```

### The Complete Restock Job

This example runs the nightly restock job from [Putting It Together](#putting-it-together).

```bash
sbt "runMain streamfilesandsockets.CompleteExample"
```

## Going Further

The reference pages cover each constructor in full:

- [`Stream` resource management](../reference/streams/core/stream.md#resource-management) lists `Stream.fromAcquireRelease`, `Stream.fromResource`, `Stream#ensuring` and `Stream#ensuringAsync` with their signatures.
- [`Sink`](../reference/streams/core/sink.md) covers `NioSinks.fromChannel`, `NioSinks.fromByteBuffer` and `Sink.fromOutputStream`.
- [Async Execution](../reference/streams/execution-and-compatibility/async-execution.md) covers `Stream.fromAcquireReleaseAsync`, whose `acquire` and `release` return `Async` values, and the asynchronous NIO readers in `AsyncNioReaders`.
- [`Resource`](../reference/resource-management/resource.md) covers `Resource.fromAutoCloseable`, `Resource#zip` and shared resources.
- The streams [overview](../reference/streams/index.md#resource-management) explains how streams integrate with `Scope`.

Streaming rows from a SQL query uses the same guarantee for a database cursor; the how-to guide on streaming query results covers it.
