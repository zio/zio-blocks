---
id: your-first-streaming-pipeline
title: "Your First Streaming Pipeline: Turn a Sales Log into a Report"
description: "Read a bookstore's sales log with a Stream, parse, filter and batch it, sum it with a Sink, and recover from a bad line."
keywords:
  - "Getting Started"
  - "Streaming Pipeline"
  - "Resource Safety"
  - "Stream"
  - "Sink"
  - "catchAll"
---

Welcome! This tutorial is for Scala developers who are new to ZIO Blocks Streams. If you can write a case class, a function and an `Either` pattern match, you have everything you need. You don't need any prior experience with streaming libraries to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- What a `Stream` is, and why nothing happens until you run it
- How to read a file as a stream so that the file is always closed
- How to transform a stream with `Stream#filter`, `Stream#map` and `Stream#grouped`
- How a `Sink` consumes a stream and produces one result
- How a stream fails with a typed error, and how `Stream#catchAll` recovers from it
- How the same stream runs with both a blocking and an asynchronous terminal

We learn these concepts by turning a small bookstore's sales log into a report of copies sold per title, one step at a time:

1. Create and run a stream
2. Read the sales log from a file
3. Keep only the sales with `Stream#filter`
4. Turn each line into a sale with `Stream#map`
5. Batch sales with `Stream#grouped`
6. Add up the sales with a `Sink`
7. Fail on a malformed line
8. Recover with `Stream#catchAll`
9. Run the same pipeline asynchronously

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the streams module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-streams" % "@VERSION@"
```

Scala 3.7 and newer use the `zio-blocks-next-streams` artifact name; the code in this tutorial is the same for every Scala version. On the JVM, the streams module needs JDK 21 or newer.

## 2. Background: A Stream Is a Recipe

Picture the sales log of a bookstore that sells Scala books. Every time the till sells a book, it appends one line to a text file: when the sale happened, which title, and how many copies. At the end of the day, the owner wants to know how many copies of each title were sold.

We could read the whole file into a `List` and work on that. That works for a small file, but a busy store's log can be larger than the memory we have, and we must still remember to close the file when we are done — even when something goes wrong halfway through. A **stream** solves both problems. A [`Stream`](../reference/streams/core/stream.md) is a recipe for producing elements one at a time: open the file, read a line, turn it into a sale, hand it on, read the next line. Running the recipe pulls elements through every step, one by one, so the whole file is never in memory at once. And because the stream itself opens the file, it also knows when to close it. Nothing happens while we write the recipe; the work starts only when we run it with a **terminal** operation such as `Stream#runCollect` or `Stream#run`.

## 3. Create and Run a Stream

A `Stream[E, A]` describes a sequence of elements of type `A` that may fail with an error of type `E`. The simplest way to create one is to list its elements with `Stream(...)`.

Let's create a stream of book titles:

```scala mdoc:silent
import zio.blocks.streams._

val titles = Stream("Zionomicon", "Programming in Scala", "Functional Programming in Scala")
```

The code above:

- `import zio.blocks.streams._` brings `Stream`, `Sink` and their operations into scope.
- `Stream(...)` creates a `Stream[Nothing, String]`. The error type `Nothing` says this stream can never fail.
- `val titles` stores the recipe; it does not run it.

Creating the stream does not produce anything yet; `titles` is only the recipe. To run it, we call a terminal operation. `Stream#runCollect` runs the stream and collects every element:

```scala mdoc
println(titles.runCollect)
```

The code above runs the stream and prints its result. Notice the shape of the output: `Right(...)` appears because every terminal returns an `Either`, where a `Right` means the stream finished without an error and a failure comes back as a `Left`, which we see in section 9. `Chunk(...)` appears because `Stream#runCollect` gathers the elements into a `Chunk`, the immutable sequence type that ZIO Blocks uses throughout, and its `toString` separates the elements with a comma and no space.

## 4. Read the Sales Log from a File

Now let's read real data. First, we write a small sales log to a temporary file, so the example works on any machine:

```scala mdoc:silent
import java.io.BufferedReader
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

val salesLog: Path = Files.createTempFile("sales", ".csv")

Files.writeString(
  salesLog,
  """timestamp,title,quantity
    |2026-10-19T10:15,Zionomicon,2
    |2026-10-19T10:42,Programming in Scala,1
    |2026-10-19T11:05,Functional Programming in Scala,3
    |2026-10-19T12:10,Zionomicon,1
    |""".stripMargin
)
```

The file starts with a header line, followed by one line per sale.

To read it, we need a stream that opens the file, emits its lines, and closes the file afterwards. `Stream.fromAcquireRelease` does exactly that. It takes three parts: `acquire` opens the resource, `release` closes it, and a function receives the open resource and returns the stream that uses it. We print a message in `acquire` and `release` so we can see when they run:

```scala mdoc:silent
val salesLines: Stream[Nothing, String] =
  Stream.fromAcquireRelease(
    acquire = {
      println("Opened the sales log")
      Files.newBufferedReader(salesLog)
    },
    release = (reader: BufferedReader) => {
      reader.close()
      println("Closed the sales log")
    }
  )(reader => Stream.fromIterator(reader.lines().iterator().asScala))
```

The code above:

- `acquire` opens the file with `Files.newBufferedReader`. It runs when the stream starts, not when we define `salesLines`.
- `release` closes the reader. The stream calls it exactly once, after the last line, after a failure, or when we stop reading early.
- `reader => Stream.fromIterator(...)` turns the reader's lines into a `Stream[Nothing, String]`. `reader.lines()` is the JDK's way to read a file line by line, and `asScala` converts its Java iterator into a Scala `Iterator`.

Let's run it. This time we use `Stream#runForeach`, a terminal that calls a function for each element as it arrives:

```scala mdoc
salesLines.runForeach(line => println(line))
```

The code above prints each line as the stream reads it. Notice the order of the output: the file is opened first, then every line is printed, then the file is closed. The `Right(())` at the end is the result of `Stream#runForeach` itself: success, with nothing to collect.

The stream closes the file even when we stop early. `Stream#take` stops after a given number of elements. Here we stop after the first two lines:

```scala mdoc
salesLines.take(2).runForeach(line => println(line))
```

The code above reads only the header and the first sale, and the file is still closed. Notice also that the file was opened a second time: every run of a stream starts the recipe from the beginning.

## 5. Keep Only the Sales with `filter`

The header line is not a sale, so we drop it. `Stream#filter` keeps the elements for which a predicate returns `true` and drops the rest:

```scala mdoc:silent
val header = "timestamp,title,quantity"

val saleLines: Stream[Nothing, String] = salesLines.filter(line => line != header)
```

The code above:

- `header` is the first line of the file, the one we want to drop.
- `salesLines.filter(line => line != header)` builds a new stream that passes every line except the header. `salesLines` itself does not change; each operation returns a new stream.
- `saleLines` has the same type as `salesLines`, `Stream[Nothing, String]`, because `Stream#filter` only removes elements.

Let's run it:

```scala mdoc
saleLines.runForeach(line => println(line))
```

The header is gone; only the four sales are left.

## 6. Turn Each Line into a Sale with `map`

Working with comma-separated text is awkward, so let's give each sale a proper type. `Stream#map` applies a function to every element and emits its result:

```scala mdoc:silent
final case class Sale(title: String, quantity: Int)

def toSale(line: String): Sale = {
  val fields = line.split(",")
  Sale(fields(1), fields(2).toInt)
}

val sales: Stream[Nothing, Sale] = saleLines.map(toSale)
```

The code above:

- `Sale` is an ordinary case class with the two fields our report needs.
- `toSale` splits a line at the commas and builds a `Sale` from the second and third field. It ignores the timestamp.
- `saleLines.map(toSale)` turns the `Stream[Nothing, String]` into a `Stream[Nothing, Sale]`.

Let's run it:

```scala mdoc
sales.runForeach(sale => println(sale))
```

Each line is now a `Sale` value. Notice that `toSale` assumes every line is well formed. We come back to that assumption in section 9.

## 7. Batch Sales with `grouped`

Many systems prefer data in batches rather than one element at a time — a database insert or a call to a remote service is cheaper for ten rows than ten times for one row. `Stream#grouped` collects up to `n` elements into a `Chunk` and emits the chunk:

```scala mdoc
sales.grouped(2).runForeach(batch => println(batch))
```

The code above:

- `sales.grouped(2)` turns our `Stream[Nothing, Sale]` into a `Stream[Nothing, Chunk[Sale]]` with two sales per chunk.
- `Stream#runForeach` prints each chunk as it arrives.
- If the number of sales did not divide evenly, the last chunk would be smaller; a stream never drops elements to fill a batch.

## 8. Add Up the Sales with a `Sink`

So far each terminal has decided what to do with the elements. A [`Sink[E, A, Z]`](../reference/streams/core/sink.md) lets us write that decision as a value of its own: it consumes elements of type `A` and produces one result of type `Z`. `Sink.foldLeft` builds a sink from a starting value and a function that combines the result so far with the next element.

Our report counts copies per title. Since our stream now emits batches, the sink adds up one whole batch at a time:

```scala mdoc:silent
import zio.blocks.chunk.Chunk

val copiesPerTitle: Sink[Nothing, Chunk[Sale], Map[String, Int]] =
  Sink.foldLeft(Map.empty[String, Int]) { (totals: Map[String, Int], batch: Chunk[Sale]) =>
    batch.foldLeft(totals) { (acc, sale) =>
      acc.updated(sale.title, acc.getOrElse(sale.title, 0) + sale.quantity)
    }
  }
```

The code above:

- `Sink[Nothing, Chunk[Sale], Map[String, Int]]` reads as: never fails, consumes `Chunk[Sale]` elements, produces a `Map[String, Int]`.
- `Map.empty[String, Int]` is the starting value: no copies of anything.
- For each batch, `batch.foldLeft(totals)` adds every sale's quantity to the running total for its title.

To run a stream into a sink, we pass the sink to the `run` terminal:

```scala mdoc
println(sales.grouped(2).run(copiesPerTitle))
```

The code above reads the file, drops the header, parses the sales, batches them, and adds them up — in a single pass over the file. Zionomicon sold three copies across two sales; the other titles appear once each.

:::tip
A sink is a reusable value, just like a stream. You can run `copiesPerTitle` against any stream of `Chunk[Sale]`, whether it comes from a file, a list in a test, or a network connection.
:::

## 9. Fail on a Malformed Line

Real logs are messy. Suppose the till crashed while it was writing its last sale, leaving a truncated line at the end of the file. Let's append one:

```scala mdoc:silent
import java.nio.file.StandardOpenOption

Files.writeString(salesLog, "2026-10-19T12:31,Zionom", StandardOpenOption.APPEND)
```

Our `toSale` function would throw an exception on this line. Instead, we want the stream to fail with an error value that says which line was bad. `Stream.fail(error)` creates a stream that fails with `error`, and `Stream#flatMap` replaces each element with a whole stream — here a stream of one `Sale`, or a failed one:

```scala mdoc:silent
final case class MalformedLine(line: String)

def parse(line: String): Stream[MalformedLine, Sale] =
  line.split(",") match {
    case Array(_, title, quantity) if quantity.toIntOption.isDefined =>
      Stream.succeed(Sale(title, quantity.toInt))
    case _ =>
      Stream.fail(MalformedLine(line))
  }

val parsedSales: Stream[MalformedLine, Sale] = saleLines.flatMap(parse)
```

The code above:

- `MalformedLine` is our error type. It is a plain case class, not an exception.
- `parse` returns `Stream.succeed(...)`, a stream of exactly one `Sale`, when the line has three fields and a numeric quantity. Otherwise, it returns `Stream.fail(MalformedLine(line))`.
- `saleLines.flatMap(parse)` runs `parse` for every line and emits whatever the stream for that line emits. Notice the new type: `Stream[MalformedLine, Sale]`. The error type now tells everyone who uses `parsedSales` that it can fail, and with what.

Let's run the report again on the new stream:

```scala mdoc
println(parsedSales.grouped(2).run(copiesPerTitle))
```

The result is now a `Left` holding the `MalformedLine`, with the exact text of the bad line. Notice that the file was still closed: the stream releases its resource on failure, just as it does on success.

## 10. Recover with `catchAll`

A failed report is not very useful to the store owner. `Stream#catchAll` takes a function from the error to a new stream. When the stream fails, `Stream#catchAll` calls this function and continues with the stream it returns. To skip the truncated line, we log it and continue with `Stream.empty`, a stream with no elements:

```scala mdoc:silent
val cleanSales: Stream[Nothing, Sale] =
  parsedSales.catchAll { bad =>
    println(s"Skipping malformed line: ${bad.line}")
    Stream.empty
  }
```

The code above:

- `bad` is the `MalformedLine` value that `parse` passes to `Stream.fail`.
- `Stream.empty` ends the stream cleanly, so the sales read before the bad line are kept.
- The type of `cleanSales` is `Stream[Nothing, Sale]`. We handled the error, so the error type is `Nothing` again, and the compiler knows this stream cannot fail.

Let's run the report one more time:

```scala mdoc
println(cleanSales.grouped(2).run(copiesPerTitle))
```

The report is back to a `Right`, with the same totals as before the crash. Notice the order of the output: the file is closed before `Stream#catchAll` runs, because the failure ends the stream that read the file.

:::caution
A failure ends the stream it happens in. `Stream#catchAll` does not go back and resume reading after the bad line; it continues with the fallback stream instead. Here the truncated line is the last line of the file, so nothing is lost. If a bad line appeared in the middle of the file, the lines after it would not be read.
:::

:::caution
Run a pipeline that uses `Stream#catchAll` from a method such as `def main`, not from the body of an `object ... extends App`. With `Stream#catchAll`, the stream may call your functions on another thread. If they read a `val` of an object that is still being initialized, that thread waits for the initialization to finish while the initialization waits for the stream, and the program hangs. The companion examples use `def main` for this reason.
:::

## 11. Run the Same Pipeline Asynchronously

Every terminal in this tutorial so far — `Stream#runCollect`, `Stream#runForeach` and `Stream#run` — blocks the calling thread until the stream finishes, and exists only on the JVM. Each one has an asynchronous twin ending in `Async`, which also works on Scala.js. `Stream#runAsync` takes the same sink and returns an [`Async[Either[E, Z]]`](../reference/async.md): a value that completes later with the same `Either`:

```scala mdoc:silent
import zio.blocks.async._

val report: Async[Either[Nothing, Map[String, Int]]] = cleanSales.grouped(2).runAsync(copiesPerTitle)
```

The code above:

- `import zio.blocks.async._` brings the `Async` type and its operations into scope.
- `cleanSales.grouped(2).runAsync(copiesPerTitle)` describes the same run as in the previous section, but nothing runs yet: creating `report` does not open the file.
- `report` has the type `Async[Either[Nothing, Map[String, Int]]]`, the same `Either` that the blocking `run` returns, wrapped in `Async`.

To wait for the result at the edge of a JVM program, we call `Async#block`:

```scala mdoc
println(report.block)
```

The code above runs the pipeline and waits for it. The output matches the blocking run line for line. That's the main idea of this tutorial made visible: `cleanSales.grouped(2)` is one description of the work, and the terminal we choose decides how it runs.

:::note
`Async#block` parks the calling thread, so use it only at the edge of a JVM program, such as in `main`. On Scala.js it throws when the value is not yet complete; there you keep the `Async` value and let the host drive it.
:::

## 12. Putting It Together

This example combines all the concepts we've learned — reading the sales log safely, dropping the header with `Stream#filter`, parsing lines into sales with `Stream#flatMap` and `Stream.fail`, recovering from the truncated line with `Stream#catchAll`, batching with `Stream#grouped`, adding up copies per title with a `Sink`, and running the pipeline with both a blocking and an asynchronous terminal:

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/CompleteExample.scala
```

## 13. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `streams-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/streams-examples
```

Run them in order to follow the tutorial step by step.

### Create and Run a Stream

This example creates a stream of book titles and runs it with `runCollect`.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept1CreateAndRunExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept1CreateAndRunExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept1CreateAndRunExample"
```

### Read the Sales Log from a File

This example reads the sales log with `Stream.fromAcquireRelease` and shows that the file is closed after a full run and after an early stop with `Stream#take`.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept2ReadFileExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept2ReadFileExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept2ReadFileExample"
```

### Keep Only the Sales with `filter`

This example drops the header line with `Stream#filter`.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept3FilterExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept3FilterExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept3FilterExample"
```

### Turn Each Line into a Sale with `map`

This example turns each line into a `Sale` with `Stream#map`.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept4MapExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept4MapExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept4MapExample"
```

### Batch Sales with `grouped`

This example batches the sales two at a time with `Stream#grouped`.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept5GroupedExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept5GroupedExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept5GroupedExample"
```

### Add Up the Sales with a `Sink`

This example adds up copies per title with a `Sink.foldLeft` and runs the stream into it with `run`.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept6SinkExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept6SinkExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept6SinkExample"
```

### Fail on a Malformed Line

This example appends a truncated line to the log, parses lines with `Stream#flatMap` and `Stream.fail`, and shows the `Left` that the run returns.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept7FailExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept7FailExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept7FailExample"
```

### Recover with `catchAll`

This example recovers from the truncated line with `Stream#catchAll` and prints the report.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept8CatchAllExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept8CatchAllExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept8CatchAllExample"
```

### Run the Same Pipeline Asynchronously

This example runs the pipeline with `runAsync` and waits for the result with `Async#block`.

<details>
  <summary>streams-examples/src/main/scala/yourfirststreamingpipeline/Concept9AsyncExample.scala</summary>

```scala mdoc:embed:streams-examples/src/main/scala/yourfirststreamingpipeline/Concept9AsyncExample.scala:show-line-numbers
```

</details>

Run it from the `streams-examples` directory:

```bash
sbt "runMain yourfirststreamingpipeline.Concept9AsyncExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from the tutorial in one program:

```bash
sbt "runMain yourfirststreamingpipeline.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 14. What You've Learned

In this tutorial, you learned:

- What a `Stream` is — a recipe for producing elements — and why nothing happens until a terminal such as `Stream#runCollect`, `Stream#runForeach` or `Stream#run` runs it
- How to read a file with `Stream.fromAcquireRelease` so that the file is closed after success, failure, or an early stop
- How to transform a stream with `Stream#filter`, `Stream#map` and `Stream#grouped`
- How a `Sink` built with `Sink.foldLeft` consumes a stream and produces one result
- How `Stream.fail` and `Stream#flatMap` turn a bad line into a typed error, and how `Stream#catchAll` recovers from it
- How the same stream runs with the blocking `Stream#run` and the asynchronous `Stream#runAsync`

You now have a solid foundation in ZIO Blocks Streams. The next step is to explore the full set of constructors, operators and sinks.

## 15. Where to Go Next

- **Want the big picture?** The [Streams overview](../reference/streams/index.md) explains the core mental model and how `Stream`, `Sink` and `Pipeline` fit together.
- **Want to see every operator?** The [Stream](../reference/streams/core/stream.md) reference page documents every constructor, transformation and terminal, including `Stream.fromAcquireRelease`, `Stream#grouped` and `Stream#catchAll`.
- **Need another way to consume a stream?** The [Sink](../reference/streams/core/sink.md) reference page lists every predefined sink and shows how to transform sinks with `Sink#contramap` and `Sink#map`.
- **Want to reuse a chain of transformations?** Read [Pipeline](../reference/streams/core/pipeline.md), which turns a chain like our `Stream#filter` and `Stream#map` into a value you can apply to any stream.
- **Curious about the asynchronous terminals?** Read [Async Execution](../reference/streams/execution-and-compatibility/async-execution.md), and the [Async](../reference/async.md) reference for the `Async` type that `runAsync` returns.
- **New to `Async` itself?** The [Getting Started with Async](./async-getting-started.md) guide introduces it step by step.
