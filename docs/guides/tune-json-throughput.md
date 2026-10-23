---
id: tune-json-throughput
title: "How to Tune JSON Encoding and Decoding for Throughput"
description: "Decode JSON straight from bytes, buffers and streams, reuse derived codecs, size reader and writer buffers, and measure with JMH."
keywords:
  - "JSON Performance"
  - "Throughput"
  - "JsonCodec"
  - "ReaderConfig"
  - "WriterConfig"
  - "JMH"
---

In this guide, we take a JSON request handler that turns every body into a `String` and back, and make it read and write bytes directly. By the end, you will know which `JsonCodec` entry point to call for an `Array[Byte]`, a `ByteBuffer`, an `InputStream` or an `OutputStream`, how to keep derived codecs from being rebuilt on every call, which `ReaderConfig` and `WriterConfig` settings change speed and memory use (and which ones are safety limits you should leave alone), and how to measure the result with JMH.

We use one running example throughout: a bookshop catalog of a few thousand `Book` records that a service receives and sends as JSON. Each section removes one source of copying or garbage, and the last section shows how to check each change with a benchmark instead of guessing.

This guide covers:

- Decoding from bytes, a `ByteBuffer` and an `InputStream`
- Encoding to bytes, a `ByteBuffer` and an `OutputStream`
- Deriving each codec once and reusing it
- Sizing the reader and writer buffers, and keeping the safety limits
- Measuring with the repository's benchmarks and a minimal JMH harness

## The Problem

Services that move a lot of JSON spend real CPU time and memory on it, and much of that work is avoidable copying. Network libraries hand us bytes and want bytes back, but the most familiar way to call a JSON library is through `String`: decode the body as text, parse the text, render the reply as text, and encode the text as bytes. Each step makes a full copy of the payload, and each copy becomes garbage as soon as the request ends.

For example, a handler that receives a list of books and returns the recent ones often looks like this:

```scala mdoc:compile-only
import zio.blocks.schema._
import java.nio.charset.StandardCharsets.UTF_8

case class Book(id: Long, title: String, authors: List[String], year: Int, tags: List[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

// Called once per request with the raw request body
def recentBooks(requestBody: Array[Byte]): Array[Byte] = {
  val text  = new String(requestBody, UTF_8)
  val books = text.fromJson[List[Book]]
  books match {
    case Right(all)  => all.filter(_.year >= 2020).toJsonString.getBytes(UTF_8)
    case Left(error) => error.getMessage.getBytes(UTF_8)
  }
}
```

This code is correct, and on a small payload nobody notices its cost. At a few hundred kilobytes per request it copies the body into a `String`, has the decoder copy that `String` back into UTF-8 bytes, writes the reply into a buffer, copies the buffer into a `String`, and copies that `String` into the reply bytes. It also derives a new codec for `List[Book]` on both `fromJson` and `toJsonString`, on every request, for a reason we look at in [Derive Each Codec Once and Reuse It](#derive-each-codec-once-and-reuse-it).

In this guide, we keep the same `Schema` and change only how we call its codec: bytes in, bytes out, one codec per type, and buffer sizes that match our payloads.

## Prerequisites

We use the schema module from the [Your First Schema](./your-first-schema.md) tutorial; [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains which artifact each Scala version needs:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

This guide assumes you can derive a schema with `Schema.derived` and round-trip a value through JSON, as shown in [Your First Schema](./your-first-schema.md). Every example imports `zio.blocks.schema._` for `Schema` and `zio.blocks.schema.json._` for `JsonCodec`, `ReaderConfig` and `WriterConfig`. Everything in this guide works on the JVM and on Scala.js, except JMH, which runs only on the JVM.

## The Book Catalog Model

Our catalog holds `Book` records. A bookshop lists many copies of a few real Scala titles, so we generate 4,000 listings by cycling through eight books and giving each listing its own `id`:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._
import java.io.{BufferedOutputStream, ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.{BufferOverflowException, ByteBuffer}
import java.nio.charset.StandardCharsets.UTF_8

case class Book(id: Long, title: String, authors: List[String], year: Int, tags: List[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived

  val codec: JsonCodec[Book]              = schema.jsonCodec
  val catalogCodec: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
}

val classics: List[Book] = List(
  Book(1L, "Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024, List("zio", "effects")),
  Book(2L, "Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008, List("scala")),
  Book(3L, "Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014, List("fp")),
  Book(4L, "Scala for the Impatient", List("Cay S. Horstmann"), 2012, List("scala")),
  Book(5L, "Hands-on Scala Programming", List("Li Haoyi"), 2020, List("scala", "tooling")),
  Book(6L, "Essential Effects", List("Adam Rosien"), 2021, List("cats-effect", "effects")),
  Book(7L, "Practical FP in Scala", List("Gabriel Volpe"), 2020, List("fp", "cats-effect")),
  Book(8L, "Scala with Cats", List("Noel Welsh", "Dave Gurnell"), 2017, List("cats", "fp"))
)

val catalog: List[Book] =
  List.tabulate(4000)(i => classics(i % classics.size).copy(id = i + 1L))

val catalogBytes: Array[Byte] = Book.catalogCodec.encode(catalog)
```

The companion holds two codecs in `val`s, one for a single `Book` and one for the whole catalog. The rest of the guide calls them directly instead of going through `toJsonString` and `fromJson`. The encoded catalog is a few hundred kilobytes, big enough for the costs in this guide to matter:

```scala mdoc
println(catalogBytes.length)
```

## Decode From Bytes, a ByteBuffer or an InputStream

`JsonCodec#decode` has an overload for each common input type. All of them return `Either[SchemaError, A]`, and none of them needs a `String`. Pick the one that matches what your network or storage layer already gives you.

### Decode From an Array of Bytes

`JsonCodec#decode(input: Array[Byte])` is the cheapest entry point. The reader parses the array in place: it borrows our array for the duration of the call instead of copying it into a buffer of its own. `JsonCodec#decode(input: String)`, by contrast, first calls `String#getBytes` with `UTF_8` on the string and then decodes the new array, so text input always costs one extra full copy.

To decode the whole catalog straight from its bytes:

```scala mdoc
println(Book.catalogCodec.decode(catalogBytes).map(_.size))
```

When the JSON sits inside a larger array, such as a message with a length prefix, `JsonCodec#decode(input, from, to)` decodes only the bytes from index `from` up to, but not including, index `to`. There is no need to copy the slice out first. For a frame that starts with a four-byte length:

```scala mdoc:silent
val payload: Array[Byte] = Book.codec.encode(classics.head)
val frame: Array[Byte]   = ByteBuffer.allocate(4 + payload.length).putInt(payload.length).put(payload).array()
val length: Int          = ByteBuffer.wrap(frame).getInt(0)
```

The codec reads the book from the frame without touching the prefix:

```scala mdoc
println(Book.codec.decode(frame, 4, 4 + length))
```

### Decode From a ByteBuffer

`JsonCodec#decode(input: ByteBuffer)` reads the bytes between `ByteBuffer#position` and `ByteBuffer#limit`, and moves the position to the end of what it read. A heap buffer (one with a backing array) is parsed in place, like an array. A direct buffer is copied into the reader's internal buffer in chunks, so it works but costs one copy.

Because the position moves only as far as the value it decoded, one buffer can hold several values back to back. By default the reader rejects anything but whitespace after the value; `ReaderConfig#withCheckForEndOfInput(false)` turns that check off so that we can read one value at a time:

```scala mdoc:silent
val twoBooks: ByteBuffer = ByteBuffer.wrap(Book.codec.encode(classics(0)) ++ Book.codec.encode(classics(1)))

val oneValueAtATime: ReaderConfig = ReaderConfig.withCheckForEndOfInput(false)
```

Each call decodes the next book and leaves the buffer positioned after it:

```scala mdoc
println(Book.codec.decode(twoBooks, oneValueAtATime).map(_.title))
println(Book.codec.decode(twoBooks, oneValueAtATime).map(_.title))
println(twoBooks.remaining())
```

A failed decode also moves the position, to just past the byte where the error was found. If you want to retry or skip the value, or call `ByteBuffer#flip` on the buffer afterwards, save `ByteBuffer#position` before the call and restore it afterwards.

### Decode From an InputStream

`JsonCodec#decode(input: InputStream)` reads the stream in chunks into the reader's internal buffer, which stays at its preferred size (32 KiB by default) for ordinary input. That means a large request body never has to be collected into one array first: the stream goes straight into the decoder. The codec does not close the stream; the code that opened it still owns it.

To decode the catalog from a stream:

```scala mdoc
println(Book.catalogCodec.decode(new ByteArrayInputStream(catalogBytes)).map(_.size))
```

One stream holds one value. The reader fills its buffer with as much input as the stream offers, so after the first value it has already consumed bytes that belong to the next one, and they are gone when the call returns. Even with `ReaderConfig#checkForEndOfInput` turned off, the second read of a stream holding two books fails:

```scala mdoc:silent
val booksStream = new ByteArrayInputStream(Book.codec.encode(classics(0)) ++ Book.codec.encode(classics(1)))
```

The first call returns the first book, and the second finds nothing left to read:

```scala mdoc
println(Book.codec.decode(booksStream, oneValueAtATime).map(_.title))
println(Book.codec.decode(booksStream, oneValueAtATime).map(_.title))
```

To read several values from one source, read it into a `ByteBuffer` and decode from that, as in the previous section, or wrap the stream's values in a JSON array and decode a `List`.

## Encode to Bytes, a ByteBuffer or an OutputStream

Encoding has the same choice of targets. Unlike `JsonCodec#decode`, the `JsonCodec#encode` methods don't return an `Either`: a value the codec can't write, or a target that is too small, throws an exception.

### Encode to an Array of Bytes

`JsonCodec#encode(value)` writes into the writer's internal buffer and returns a copy trimmed to the exact length, so it makes one copy. `JsonCodec#encodeToString` writes the same bytes and then decodes them into a `String`, and calling `String#getBytes` with `UTF_8` on that string copies them once more. When the result goes to the network, encode to bytes:

```scala mdoc
println(Book.catalogCodec.encode(catalog).length)
```

### Encode Into a ByteBuffer

`JsonCodec#encode(value, output: ByteBuffer)` writes at `ByteBuffer#position` and moves the position past the JSON. A heap buffer receives the bytes straight into its backing array; a direct buffer receives them in chunks through the writer's internal buffer. Either way, call `ByteBuffer#flip` before reading the buffer back, or the reader starts at the end of the JSON:

```scala mdoc:silent
val out = ByteBuffer.allocate(1024)
Book.codec.encode(classics.head, out)
```

The position now sits after the JSON, so a reader that starts there finds only the empty rest of the buffer. We decode a `ByteBuffer#duplicate`, which has its own position, to show the mistake without disturbing `out`:

```scala mdoc
println(out.position())
println(Book.codec.decode(out.duplicate()))
```

After `ByteBuffer#flip`, the limit is the end of the JSON and the position is back at the start:

```scala mdoc:silent
out.flip()
```

Now the same buffer decodes to the book:

```scala mdoc
println(Book.codec.decode(out).map(_.title))
```

When the buffer is too small, `JsonCodec#encode` throws `java.nio.BufferOverflowException`. By then it has already written part of the JSON and moved the position past it, so the buffer holds a truncated value:

```scala mdoc:silent
val tooSmall = ByteBuffer.allocate(16)
```

The exception arrives after six bytes are already in the buffer:

```scala mdoc
try Book.codec.encode(classics.head, tooSmall)
catch { case _: BufferOverflowException => println(s"overflow at position ${tooSmall.position()}") }
```

To keep the buffer usable, save its position before encoding and restore it when you catch the exception:

```scala mdoc:silent
def encodeOrRewind(book: Book, buffer: ByteBuffer): Boolean = {
  val start = buffer.position()
  try {
    Book.codec.encode(book, buffer)
    true
  } catch {
    case _: BufferOverflowException =>
      buffer.position(start)
      false
  }
}

val small = ByteBuffer.allocate(16)
```

The failed write now leaves the buffer exactly as it was:

```scala mdoc
println(encodeOrRewind(classics.head, small))
println(small.position())
```

### Encode to an OutputStream

`JsonCodec#encode(value, output: OutputStream)` writes the JSON in chunks through the writer's internal buffer, so a large reply never exists as one array. The codec neither closes nor flushes the stream. If you wrap the stream in a `BufferedOutputStream`, or your server buffers the response body, call `OutputStream#flush` when you are done, or the bytes may never leave the buffer:

```scala mdoc:silent
val sink     = new ByteArrayOutputStream()
val response = new BufferedOutputStream(sink, 1024 * 1024)
```

With a buffer larger than the reply, nothing reaches the underlying stream until we flush:

```scala mdoc
Book.catalogCodec.encode(catalog, response)
println(sink.size())

response.flush()
println(sink.size())
```

## Derive Each Codec Once and Reuse It

Deriving a codec walks the whole schema and builds a tree of codec objects, one per type and field. That is far more work than decoding a small record, so a codec should be built once and reused, not rebuilt per request. Codecs are safe to share between threads: each thread gets its own reader and writer from a per-thread pool.

`Schema#jsonCodec` caches the codec it derives, but only on that `Schema` instance. `toJsonString`, `toJsonBytes` and `fromJson` all go through `Schema#jsonCodec` on whatever schema the compiler finds. For `Book`, that is the `implicit val` in its companion, so the cache works. For `List[Book]`, `Option[Book]`, `Vector[Book]` or `Map[String, Book]`, the compiler builds a new schema from the element's schema at each call site, so every call starts with an empty cache. `Schema#derive` never caches. We can check this by comparing instances:

```scala mdoc:silent
val derivedOnce: JsonCodec[Book]  = Book.schema.derive(JsonFormat)
val derivedTwice: JsonCodec[Book] = Book.schema.derive(JsonFormat)
```

The first comparison below is the cached `Book` codec, the second the summoned `List[Book]` schema, and the third two calls of `Schema#derive`:

```scala mdoc
println(Book.schema.jsonCodec eq Book.schema.jsonCodec)
println(Schema[List[Book]].jsonCodec eq Schema[List[Book]].jsonCodec)
println(derivedOnce eq derivedTwice)
```

The second and third lines print `false`: each of those expressions derives a new codec every time it runs. That is why `fromJson[List[Book]]` and `toJsonString` on a `List[Book]` in [The Problem](#the-problem) pay for two derivations per request.

The fix is to derive each codec you use on a hot path once and keep it in a `val`, as the `Book` companion in [The Book Catalog Model](#the-book-catalog-model) does with `Book.codec` and `Book.catalogCodec`. The same applies to a codec with custom settings from `JsonCodecDeriver`, as in [How to Customize JSON Field Names, ADT Encoding and Optional Fields](./customize-json-encoding.md):

```scala mdoc:silent
object CatalogCodecs {
  val snakeCaseCatalog: JsonCodec[List[Book]] =
    Schema[List[Book]].derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))
}
```

Calling `CatalogCodecs.snakeCaseCatalog` returns the same codec every time; only its first use pays for the derivation.

## Tune ReaderConfig and WriterConfig

Every `JsonCodec#decode` overload has a twin that takes a `ReaderConfig`, and every `JsonCodec#encode` overload has a twin that takes a `WriterConfig`. These two classes control the reader's and writer's buffers and a few output options. They are separate from `JsonCodecDeriver`, which decides the shape of the JSON (field names, discriminators, empty collections) when a codec is derived.

Both classes are immutable. The defaults are the companion objects themselves, so `ReaderConfig` and `WriterConfig` can be passed wherever a config is expected, and each `with...` method returns a new config. Build tuned configs once, in `val`s, next to the codecs that use them. These are all the settings and their defaults:

| Setting                                  | Default             | Applies to                                       | Purpose                                                                    |
| ---------------------------------------- | ------------------- | ------------------------------------------------ | -------------------------------------------------------------------------- |
| `ReaderConfig#preferredBufSize`          | 32768 bytes         | `InputStream` and `ByteBuffer` input             | The size the reader's byte buffer starts at and shrinks back to            |
| `ReaderConfig#maxBufSize`                | 33554432 bytes      | `InputStream` and `ByteBuffer` input             | Safety limit: the largest that buffer may grow                             |
| `ReaderConfig#preferredCharBufSize`      | 4096 chars          | All input                                        | The size the buffer for string values starts at and shrinks back to        |
| `ReaderConfig#maxCharBufSize`            | 4194304 chars       | All input                                        | Safety limit: the longest string value the reader accepts                  |
| `ReaderConfig#checkForEndOfInput`        | `true`              | All input                                        | Rejects anything but whitespace after the value                            |
| `WriterConfig#preferredBufSize`          | 32768 bytes         | All output except a heap `ByteBuffer`            | The size the writer's byte buffer starts at and shrinks back to            |
| `WriterConfig#indentionStep`             | 0                   | All output                                       | Spaces per indentation level; 0 writes compact JSON                        |
| `WriterConfig#escapeUnicode`             | `false`             | All output                                       | Writes every non-ASCII character as an escape sequence                     |

### Size the Writer Buffer for Large Outputs

Each thread keeps one writer with a buffer of `WriterConfig#preferredBufSize` bytes. When a value needs more room, the writer doubles the buffer until the value fits, and when the call ends it replaces the grown buffer with a new one of the preferred size. A service that routinely writes replies larger than 32 KiB therefore allocates and copies several buffers on every call. Raising `WriterConfig#preferredBufSize` to the size of a typical reply lets the buffer stay large:

```scala mdoc:silent
val largeReplies: WriterConfig = WriterConfig.withPreferredBufSize(1024 * 1024)
```

The output is the same; only the buffer handling changes:

```scala mdoc
println(Book.catalogCodec.encode(catalog, largeReplies).sameElements(catalogBytes))
```

The trade-off is memory: every thread that encodes JSON keeps a buffer of that size for as long as it lives. Encoding into a heap `ByteBuffer` ignores this setting, because the writer uses the buffer's own array.

### Size the Reader Buffers for Streams and Long Strings

The reader has two buffers. Its byte buffer is used only for `InputStream` and `ByteBuffer` input; array and `String` input is parsed in place. It grows past `ReaderConfig#preferredBufSize` only when the reader must hold one part of the input whole: a very long number, or a sealed-trait object written with a discriminator field (`DiscriminatorKind.Field`) whose discriminator isn't the first field, because the reader can't pick the case until it has seen the discriminator. Its char buffer holds the string value being decoded and grows past `ReaderConfig#preferredCharBufSize` for any string longer than 4,096 characters, whatever the input type.

As with the writer, a grown buffer is replaced by a preferred-size one after each call. If your documents regularly contain long strings, such as book descriptions of 10,000 characters, raise the preferred char buffer size:

```scala mdoc:silent
val longDescriptions: ReaderConfig = ReaderConfig.withPreferredCharBufSize(16 * 1024)
```

Each setting is validated when you set it. A preferred size can't exceed its maximum, so to raise both, raise the maximum first:

```scala mdoc
scala.util.Try(ReaderConfig.withPreferredBufSize(64 * 1024 * 1024)).failed.foreach(e => println(e.getMessage))

println(ReaderConfig.withMaxBufSize(64 * 1024 * 1024).withPreferredBufSize(64 * 1024 * 1024).preferredBufSize)
```

Use one config per kind of call. The pooled reader and writer resize to the preferred sizes of whichever config the current call passes, so alternating two configs with different sizes on one thread reallocates the buffers on every switch.

### Keep the Safety Limits

`ReaderConfig#maxBufSize` and `ReaderConfig#maxCharBufSize` limit how much memory one request can make the reader allocate. Without them, a client could send a 2 GB string value, or an object whose discriminator field comes after gigabytes of other fields, and the reader would try to buffer all of it. When a limit is hit, `JsonCodec#decode` returns a `Left` instead of allocating more. To see one fire, we decode a book with a 5,000-character title under a 2,048-character limit:

```scala mdoc:silent
val strict: ReaderConfig = ReaderConfig.withPreferredCharBufSize(1024).withMaxCharBufSize(2048)

val longTitle: Array[Byte] = Book.codec.encode(classics.head.copy(title = "Z" * 5000))
```

The reader stops at the title and names the limit:

```scala mdoc
println(Book.codec.decode(longTitle, strict))
```

Raise these limits only for input you trust, and only as far as your largest legitimate value needs; for a public endpoint, lowering them is the safer change. Keep `ReaderConfig#maxBufSize` at 32768 bytes or more, though: each thread's reader starts with a 32 KiB byte buffer, and in 0.0.56 a smaller limit is applied only when an earlier call on the same thread has shrunk that buffer (a known bug), so the same request can pass on one thread and fail on another. `ReaderConfig#maxBufSize` doesn't apply to array or `String` input, which is already fully in memory, so limit the size of request bodies in your server before you read them into an array.

Leave `ReaderConfig#checkForEndOfInput` on for request bodies. It costs little, and it turns a body with trailing garbage, such as two JSON documents pasted together, into an error instead of silently decoding the first one. Turn it off only when you deliberately read several values from one `ByteBuffer`, as shown in [Decode From a ByteBuffer](#decode-from-a-bytebuffer).

### Keep Production Output Compact

`WriterConfig#withIndentionStep` pretty-prints JSON with that many spaces per level, and `WriterConfig#withEscapeUnicode` writes every non-ASCII character as an ASCII escape sequence of at least six bytes, instead of its two to four UTF-8 bytes. Both make the output larger, and every extra byte is written, sent and parsed again on the other side. We can compare the sizes for *Functional Programming in Scala*, whose author list contains a non-ASCII `ú`:

```scala mdoc:silent
val fpInScala = classics(2)
```

The three sizes are the default compact output, two-space indentation and escaped Unicode:

```scala mdoc
println(Book.codec.encode(fpInScala).length)
println(Book.codec.encode(fpInScala, WriterConfig.withIndentionStep(2)).length)
println(Book.codec.encode(fpInScala, WriterConfig.withEscapeUnicode(true)).length)
```

Keep the default compact output for traffic between services. Use indentation for logs, files and debugging, and escaping only when the receiver can't read UTF-8.

## Measure the Difference With JMH

The only reliable way to know whether a change helps is to measure it on your own data and hardware. [JMH](https://github.com/openjdk/jmh), the Java Microbenchmark Harness, runs code long enough for the JIT compiler to settle, repeats it in separate forked JVMs, and reports operations per second with an error margin. A loop timed with `System.nanoTime` does none of that, and its numbers are mostly noise.

### Run the Repository's JSON Benchmarks

The ZIO Blocks repository has a `benchmarks` project with JMH benchmarks for its JSON codec. It runs on the JVM with Scala 3.9.0 only. Four of its benchmarks measure JSON:

| Benchmark                    | What it measures                                                       | Compared with                             |
| ---------------------------- | ---------------------------------------------------------------------- | ----------------------------------------- |
| `JsonListOfRecordsBenchmark` | Reading and writing a list of `size` flat records                      | jsoniter-scala, zio-json, zio-schema-json |
| `JsonNestedRecordsBenchmark` | Reading, writing and failing to read records nested `size` levels deep | Nothing (zio-blocks only)                 |
| `JsonListOfJsonsBenchmark`   | Reading and writing a list of `Json` values                            | zio-json                                  |
| `MaybeBoxingBenchmark`       | Encoding and decoding an `Option[String]` against a `Maybe[String]`    | Nothing (zio-blocks only)                 |

The zio-blocks and jsoniter-scala benchmarks read from and write to `Array[Byte]`; the zio-json and zio-schema-json ones go through a `String`. To run the list-of-records benchmark from a clone of the repository:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
sbt "++3.9.0; benchmarks/Jmh/run -wi 5 -i 5 -f 1 -w 1s -r 1s -p size=1000 zio.blocks.schema.json.JsonListOfRecordsBenchmark"
```

The flags set five warm-up and five measured iterations of one second each, one forked JVM, and a list of 1,000 records (the `size` parameter takes 1, 10, 100, 1000, 10000 or 100000). The forked JVM starts with a 3 GB heap, so close other memory-hungry programs first. Add `-prof gc` to report the bytes allocated per operation next to the throughput; for the changes in this guide, the allocation rate is often the clearer signal.

The repository doesn't publish results, and you shouldn't trust anyone else's: run the benchmarks on the machine and JDK you deploy to.

### Write a Minimal Benchmark for Your Own Types

To measure your own payloads, add the [sbt-jmh](https://github.com/sbt/sbt-jmh) plugin to a separate benchmark project and write a class like the one below. It builds the input once, when JMH creates the state object for each benchmark thread, so each benchmark method measures only the call we care about, and it returns each result so that the JIT compiler can't remove the work. JMH refuses benchmark classes in the default package, so the class lives in a package, next to the `Book` model from [The Book Catalog Model](#the-book-catalog-model). This is a JMH class for an sbt-jmh project, not part of the companion examples:

```scala
package bookshop

import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import zio.blocks.schema._
import zio.blocks.schema.json._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class CatalogBenchmark {
  val catalog: List[Book] =
    List.tabulate(4000)(i => Book(i.toLong, "Zionomicon", List("John A. De Goes"), 2024, List("zio")))
  val bytes: Array[Byte] = Book.catalogCodec.encode(catalog)
  val text: String       = new String(bytes, UTF_8)

  @Benchmark
  def decodeFromBytes(): Either[SchemaError, List[Book]] = Book.catalogCodec.decode(bytes)

  @Benchmark
  def decodeFromString(): Either[SchemaError, List[Book]] = Book.catalogCodec.decode(text)

  @Benchmark
  def encodeToBytes(): Array[Byte] = Book.catalogCodec.encode(catalog)

  @Benchmark
  def encodeThroughString(): Array[Byte] = Book.catalogCodec.encodeToString(catalog).getBytes(UTF_8)
}
```

Run it with `sbt "Jmh/run -prof gc CatalogBenchmark"` from the benchmark project. For a quick experiment without an sbt project, scala-cli runs the same file with `scala-cli --power run --jmh <file> -- -prof gc CatalogBenchmark`. Benchmark one change at a time, against a baseline, and keep the payload close to what your service really sees: the gap between two entry points depends heavily on payload size.

## Putting It Together

The complete example is a catalog endpoint built from every technique in this guide. It keeps its codecs and configs in `val`s, decodes the request body straight from an `InputStream` with tight safety limits, encodes the reply to an `OutputStream` with a writer buffer sized for the reply and flushes it, and reuses one heap `ByteBuffer` for small messages, restoring its position after an overflow:

```scala mdoc:embed:schema-examples/src/main/scala/tunejsonthroughput/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

To compile every example without running one, use `sbt compile`. The section examples share the catalog model in `BookCatalog.scala`; `CompleteExample` defines its own model and runs on its own.

<details>
  <summary>schema-examples/src/main/scala/tunejsonthroughput/BookCatalog.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/tunejsonthroughput/BookCatalog.scala:show-line-numbers
```

</details>

### The String Baseline

This example runs the `String`-based handler from [The Problem](#the-problem) and the byte-based handler that replaces it, and checks that both return the same reply:

<details>
  <summary>schema-examples/src/main/scala/tunejsonthroughput/StringBaselineExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/tunejsonthroughput/StringBaselineExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain tunejsonthroughput.StringBaselineExample"
```

### Decode From Bytes, a ByteBuffer or an InputStream

This example decodes the catalog from an array, a slice of a framed array, a heap and a direct `ByteBuffer` holding two books, and an `InputStream`:

<details>
  <summary>schema-examples/src/main/scala/tunejsonthroughput/DecodeFromBytesExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/tunejsonthroughput/DecodeFromBytesExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain tunejsonthroughput.DecodeFromBytesExample"
```

### Encode to Bytes, a ByteBuffer or an OutputStream

This example encodes to an array, into a `ByteBuffer` (including recovering from an overflow), and to a buffered `OutputStream` that it flushes:

<details>
  <summary>schema-examples/src/main/scala/tunejsonthroughput/EncodeToBytesExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/tunejsonthroughput/EncodeToBytesExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain tunejsonthroughput.EncodeToBytesExample"
```

### Derive Each Codec Once and Reuse It

This example shows which expressions return a cached codec and which derive a new one on every call:

<details>
  <summary>schema-examples/src/main/scala/tunejsonthroughput/ReuseCodecExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/tunejsonthroughput/ReuseCodecExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain tunejsonthroughput.ReuseCodecExample"
```

### Tune ReaderConfig and WriterConfig

This example builds tuned reader and writer configs, shows the validation order for buffer sizes, triggers both safety limits, and compares the size of compact, indented and escaped output:

<details>
  <summary>schema-examples/src/main/scala/tunejsonthroughput/TuneConfigExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/tunejsonthroughput/TuneConfigExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain tunejsonthroughput.TuneConfigExample"
```

### Putting It Together

The complete example combines every technique in one catalog endpoint:

```bash
sbt "runMain tunejsonthroughput.CompleteExample"
```

## Going Further

- [JSON Configuration](../reference/schema/built-in-codecs/json/json-config.md) covers `WriterConfig` and `ReaderConfig` together with `Json#print` and `Json.parse`.
- [Codec](../reference/schema/codec.md) lists every encode and decode overload that `JsonCodec` shares with the other binary formats.
- [How to Customize JSON Field Names, ADT Encoding and Optional Fields](./customize-json-encoding.md) changes the shape of the JSON with `JsonCodecDeriver`; derive those codecs once, as shown above.
- [Coming from circe, zio-json or jsoniter-scala](./json-library-migration.md) moves existing codecs to one `Schema`, which you can then tune with this guide.
