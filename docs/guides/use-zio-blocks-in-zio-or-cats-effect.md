---
id: use-zio-blocks-in-zio-or-cats-effect
title: "How to Use ZIO Blocks in a ZIO or cats-effect Application"
description: "Call ZIO Blocks from a ZIO or cats-effect app: blocking calls, resources, Async and streams, with cancellation and cleanup carried across."
keywords:
  - "Effect System Interop"
  - "cats-effect"
  - "ZIO"
  - "fs2"
  - "ZStream"
  - "Cancellation"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

In this guide, we use ZIO Blocks inside an application that already runs on ZIO 2 or cats-effect 3. By the end, you will have a small orders service that parses JSON orders, stores them in SQLite, authorizes payments through an asynchronous gateway, writes an archive file and streams order events, with every ZIO Blocks call running where your effect system expects it, and with interruption and cleanup working across the boundary.

ZIO Blocks has no effect-system dependency. Its modules are plain Scala values, functions and direct-style code, so "interop" is not one adapter but five cases, and we take them one at a time:

- Pure modules, such as `schema` and its codecs, need no interop at all.
- Blocking calls, such as SQL queries and stream terminals, go on the effect system's blocking pool, and their results join its error channel.
- Resources: a ZIO Blocks `Resource` becomes a ZIO `Scope` or a cats-effect `Resource`, and the other way round.
- `Async` values become `ZIO` or `IO` effects, and the other way round, with cancellation carried in both directions.
- Streams: a ZIO Blocks `Stream` becomes a `ZStream` or an fs2 `Stream`, and the other way round, closed exactly once.

Every section shows a ZIO tab and a cats-effect tab. Only ZIO has library help, the [`sql-zio` module](./sql-from-zio.md). Every other bridge on this page is a few lines of your own code, which you copy into your project.

## The Problem

ZIO Blocks says it needs no effect system. Then the question is where its calls go in an application that has one. Wrapping each call in the first constructor that compiles works on a laptop, and fails under load or on interruption.

For example, here is a first attempt at three operations of an orders service, written for ZIO 2.1.27 (not compiled here):

```scala
def allOrders(transactor: JdbcTransactor): Task[List[Order]] =
  ZIO.succeed(transactor.connect(OrdersDb.all()))

def authorize(gateway: PaymentGateway, order: Order): Task[String] =
  ZIO.fromFuture(implicit ec => gateway.authorize(order).toFuture)

def events(path: Path): Task[List[OrderEvent]] =
  ZIO.succeed(OrderEvents.fromFile(path).runCollect).absolve.map(_.toList)
```

Each line compiles, and each one is wrong in a way no test notices. `allOrders` runs JDBC on ZIO's compute threads, and an `SQLException` becomes a defect instead of a failure. `authorize` loses cancellation: when the fiber is interrupted, the `Future` from `Async#toFuture` can't cancel the payment authorization, so it runs on. `events` reads the whole file into memory on a compute thread, so it is neither a stream nor interruptible. The cats-effect versions with `IO.delay` and `IO.fromFuture` block a compute thread and lose cancellation in the same way.

In this guide, we put each kind of ZIO Blocks call where it belongs, and write one small bridge per direction for the cases where ZIO Blocks and the effect system both manage something: a resource, a running computation or a stream.

## Prerequisites

Add the ZIO Blocks modules this guide uses, the SQLite JDBC driver, and your effect system. The examples are tested with ZIO 2.1.27, cats-effect 3.7.1 and fs2 3.14.0:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema"  % "@VERSION@",
  "dev.zio" %% "zio-blocks-sql"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-streams" % "@VERSION@",
  "dev.zio" %% "zio-blocks-async"   % "@VERSION@",
  "dev.zio" %% "zio-blocks-scope"   % "@VERSION@"
)

// Scala 3.9 and newer: the same modules as zio-blocks-next-*, plus -experimental
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@" // and so on
scalacOptions += "-experimental"

// The SQLite JDBC driver
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"

// For a ZIO application
libraryDependencies ++= Seq(
  "dev.zio" %% "zio"         % "2.1.27",
  "dev.zio" %% "zio-streams" % "2.1.27"
)

// For a cats-effect application
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.7.1",
  "co.fs2"        %% "fs2-core"    % "3.14.0"
)
```

The `sql` module is published for Scala 3 only, so the orders model and the programs that use it are Scala 3. The bridges themselves (the resource, `Async` and stream helpers below) compile on Scala 2.13 and Scala 3. The `streams` module needs JDK 21 or newer at runtime.

This guide assumes you know your effect system, and the basics of [`Async`](../reference/async.md), [`Scope` and `Resource`](../reference/resource-management/scope.md) and [`Stream`](../reference/streams/core/stream.md).

## The Orders Model

The service keeps orders in an `orders` table and reads order events from a JSON-lines file. Everything in this file is ZIO Blocks code, with no effect system in sight: `Order` and `OrderEvent` derive a `Schema` for JSON, `OrdersDb` holds the SQL, and `OrderEvents.fromFile` is a ZIO Blocks `Stream` that opens the file when it runs and prints a line when it closes it:

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Orders.scala
```

`OrdersDb.totals` returns a `Stream` of the order totals from `Frag#queryStream`. Like every `Frag#queryStream` result, it must be consumed inside the `JdbcTransactor#connect` block that opened its connection.

## Call the Pure Modules Directly

`schema`, its codecs, optics, `html` and parsing configuration from a string are pure: they start no threads, do no I/O and hold no resources. Call them from your effect like any other library function. A decode returns `Either[SchemaError, A]`, and `SchemaError` extends `Exception`, so it fits the error channel of `ZIO.fromEither` and of `IO.fromEither` as it is. An encode can't fail, so `ZIO.succeed` and `IO.pure` are enough:

<Tabs groupId="effect-system" defaultValue="zio">
  <TabItem value="zio" label="ZIO">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step1PureZio.scala
```

  </TabItem>
  <TabItem value="cats" label="cats-effect">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step1PureCats.scala
```

  </TabItem>
</Tabs>

Both programs print the same lines. The missing field arrives in the error channel as a typed `SchemaError`:

```text
parsed: Order(1,Ada,4200)
rendered: {"id":1,"customer":"Ada","totalCents":4700}
missing field: Left(missing required field "totalCents" at: .)
```

## Run Blocking Calls on the Blocking Pool

Some ZIO Blocks calls block the calling thread: every JDBC call, every blocking `Stream` terminal on the JVM, and `Async#block`. Run them with `ZIO.attemptBlocking` or `IO.blocking`, so they use the effect system's pool for blocking work instead of the few compute threads its fibers share. Building a `Stream` or composing a `Resource` blocks nothing, because nothing runs until a terminal or a scope runs it:

| ZIO Blocks call                                                   | Blocks the thread?                     | Where to run it                       |
|-------------------------------------------------------------------|----------------------------------------|---------------------------------------|
| `Schema` codecs, optics, `html` rendering                         | No                                     | Anywhere                              |
| Building a `Stream` (`Stream.fromIterable`, `Stream#map`, ...)    | No, it is a description                | Anywhere                              |
| `Transactor#connect`, `Transactor#transact`, `Frag#query`         | Yes, JDBC                              | `ZIO.attemptBlocking`, `IO.blocking`  |
| `Stream#runCollect`, `Stream#runFold`, `Stream#runForeach`        | Yes, on the JVM                        | `ZIO.attemptBlocking`, `IO.blocking`  |
| `Async#block`                                                     | Yes, until the `Async` completes       | `ZIO.attemptBlocking`, `IO.blocking`  |
| `Scope#scoped`, `Scope#allocate`                                  | Runs acquire and release on the caller | Blocking pool if they do I/O          |
| Building an `Async` (`Async.attempt`, `Async#map`)                | Runs its synchronous work at once      | Like the work it does                 |

Errors map in three shapes. A typed `Either` from a decode or from a stream terminal joins the error channel with `ZIO.fromEither` or `ZIO#absolve`, and with `IO.fromEither` or `IO#rethrow`. An exception that a ZIO Blocks call throws, such as an `SQLException`, becomes a failure inside `ZIO.attemptBlocking` and `IO.blocking`. A defect stays a defect.

The orders service stores two orders, lists them, sums their totals with a stream that it consumes inside `JdbcTransactor#connect`, and then inserts a duplicate id. Each blocking call prints the thread it runs on:

<Tabs groupId="effect-system" defaultValue="zio">
  <TabItem value="zio" label="ZIO">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step2BlockingZio.scala
```

  </TabItem>
  <TabItem value="cats" label="cats-effect">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step2BlockingCats.scala
```

  </TabItem>
</Tabs>

The ZIO program prints its compute thread first, then runs every insert on ZIO's blocking pool. The duplicate id fails with the driver's exception in the error channel:

```text
compute work on ZScheduler-Worker-4
insert order 1 on zio-default-blocking-1
insert order 2 on zio-default-blocking-1
orders: List(Order(1,Ada,4200), Order(2,Grace,1999))
revenue: 6199 cents
insert order 1 on zio-default-blocking-1
duplicate id: Left(org.sqlite.SQLiteException)
```

The cats-effect program prints the same results. Its blocking calls run on threads named `io-compute-blocker-<n>`, which is how cats-effect 3 names a compute thread it has handed over to blocking work while a replacement takes its place:

```text
compute work on io-compute-13
insert order 1 on io-compute-blocker-13
insert order 2 on io-compute-blocker-13
orders: List(Order(1,Ada,4200), Order(2,Grace,1999))
revenue: 6199 cents
insert order 1 on io-compute-blocker-13
duplicate id: Left(org.sqlite.SQLiteException)
```

:::tip[On ZIO, `sql-zio` writes the wrappers for you]
The `sql-zio` module's `TransactorZIO` returns ZIO effects that already run on the blocking pool, comes as a `ZLayer`, and runs transactions whose body is a ZIO effect. [How to Use the sql Module from a ZIO Application](./sql-from-zio.md) covers it. There is no cats-effect counterpart, so on cats-effect you write `IO.blocking` as above.
:::

`Frag#queryStream` can't leave its `JdbcTransactor#connect` block: the connection closes when the block returns, and ZIO Blocks has no public way to keep it open for longer. To stream query results through your effect system, consume them inside the block, as `revenue` does, and turn file and socket streams into effect streams with the bridge from [the streams section](#turn-a-zio-blocks-stream-into-a-zstream-or-fs2-stream).

## Lift a ZIO Blocks Resource Into Your Effect System

A ZIO Blocks `Resource` describes how to acquire a value and how to release it, and a `Scope` runs the release when it closes. Your effect system has the same idea, so the bridge hands the lifetime to one of the two. The archive file of placed orders is our resource. It prints a line when it opens and when it closes, so we can count the closes:

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/OrderArchive.scala
```

ZIO Blocks code normally enters a scope with `Scope#scoped`, whose block keeps every resource inside it. An effect system acquires and releases in two separate steps instead, so the bridge opens a child of `Scope.global` with `Scope#open`, allocates the resource in it, and takes the value out with `Scope#leak`. `Scope#leak` is the escape hatch ZIO Blocks provides for interop; it warns at compile time, and `@nowarn` silences that warning here because the caller closes the scope. Closing the child scope runs the resource's finalizers:

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Opened.scala
```

The bridges build on `Opened.resource` for one direction. For the other, they run the effect system's own acquire when ZIO Blocks allocates, and its release when the ZIO Blocks scope closes:

<Tabs groupId="effect-system" defaultValue="zio">
  <TabItem value="zio" label="ZIO">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/ZioResources.scala
```

  </TabItem>
  <TabItem value="cats" label="cats-effect">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/CatsResources.scala
```

  </TabItem>
</Tabs>

`ZioResources.fromResource` returns a `ZIO[Scope, Throwable, A]`, so `ZLayer.scoped` turns it into a layer and `ZIO.scoped` runs it. `CatsResources.fromResource` returns a cats-effect `Resource`, which `Resource#use` runs. In the other direction, `ZioResources.toResource` and `CatsResources.toResource` run your effect system from inside ZIO Blocks code with `Runtime#unsafe` or `Dispatcher#unsafeRunSync`, which blocks the thread, so call ZIO Blocks code that allocates it from `ZIO.attemptBlocking` or `IO.blocking`. A `Dispatcher` is cats-effect's tool for running effects from code that is not itself an effect; it comes from `Dispatcher.parallel[IO]`.

The programs below use the archive four times: once successfully, once with a failing body, once interrupted while in use, and once as a resource your effect system manages and ZIO Blocks code uses. Each use opens and closes the archive exactly once:

```text
-- success, through a ZLayer
archive opened
archive closed
-- failure
archive opened
archive closed
result: Left(payment declined)
-- interruption
archive opened
archive closed
-- a ZIO-managed archive used from ZIO Blocks code
archive opened
archive closed
```

The cats-effect program prints the same lines, with "cancellation" and "cats-effect-managed" in its labels.

<details>
  <summary>Runnable version, the ZIO tab: <code>Step3ResourcesZio.scala</code></summary>

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step3ResourcesZio.scala
```

</details>

<details>
  <summary>Runnable version, the cats-effect tab: <code>Step3ResourcesCats.scala</code></summary>

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step3ResourcesCats.scala
```

</details>

## Bridge `Async` and Your Effect, With Cancellation

`Async` is eager: building one runs its synchronous work at once, like a `Future`, while `ZIO` and `IO` values are descriptions that run each time the runtime executes them. So the bridge into your effect takes the `Async` by name and builds it each time the effect runs. Cancellation must also cross in both directions: interrupting the fiber must cancel the `Async`, and cancelling the `Async` must interrupt the effect.

`Async#toFuture` followed by `ZIO.fromFuture` or `IO.fromFuture` loses cancellation in both directions, because a `Future` can't be cancelled. `Async.fromFuture` and `Async.fromCompletionStage` don't forward cancellation to their source either (a known bug in 0.0.56). The bridges therefore use the `cancellable` helper from [How to Wrap Callback and Future-Based APIs](./wrap-callback-and-future-apis.md#cancel-the-underlying-call-when-the-async-is-cancelled), which builds an `Async` whose cancellation calls a function you supply:

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Cancellable.scala
```

The payment gateway is ZIO Blocks code with a callback API. It authorizes a payment after a delay, and cancelling the `Async` cancels the pending authorization and prints a line:

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/PaymentGateway.scala
```

Into your effect, the bridge starts the `Async` with `Async#start`, reports its outcome through `Async#foldCause` to the effect system's callback, and returns `Async.Running#cancel` as the effect's canceller. Out of your effect, it runs the effect with a cancellable future and hands `cancellable` the function that cancels it:

<Tabs groupId="effect-system" defaultValue="zio">
  <TabItem value="zio" label="ZIO">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/ZioAsync.scala
```

  </TabItem>
  <TabItem value="cats" label="cats-effect">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/CatsAsync.scala
```

  </TabItem>
</Tabs>

The programs below authorize one payment, then authorize again under a 100 ms timeout that is shorter than the gateway's 300 ms delay. In the other direction, ZIO Blocks code blocks on a fraud check written as a ZIO or cats-effect effect, then starts a second check and cancels it after 100 ms. The ZIO program prints:

```text
authorized: auth-1
payment gateway: authorization of order 1 cancelled
with a 100 ms timeout: None
fraud check passed: true
fraud check of order 1 interrupted
after cancelling the Async
```

The timeout interrupts the fiber, and the interruption reaches the gateway, which cancels the authorization. Cancelling the `Async` interrupts the fraud check's fiber, whose `ZIO#onInterrupt` prints its line before the marker printed afterwards. The cats-effect program prints `timed out` from `IO#timeoutTo` instead of `None`, and `cancelled` from `IO#onCancel`.

<details>
  <summary>Runnable version, the ZIO tab: <code>Step4AsyncZio.scala</code></summary>

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step4AsyncZio.scala
```

</details>

<details>
  <summary>Runnable version, the cats-effect tab: <code>Step4AsyncCats.scala</code></summary>

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step4AsyncCats.scala
```

</details>

## Turn a ZIO Blocks Stream Into a ZStream or fs2 Stream

A ZIO Blocks `Stream` runs on the thread that pulls it, and a pull can block. To read one from a `ZStream` or an fs2 stream, we open it once with `Stream#start`, pull chunks of elements on the blocking pool, and close it when the effect stream ends, fails or is interrupted. `BlockingPull` wraps that reader:

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/BlockingPull.scala
```

`Reader.SyncReader#readUpToN` returns at most `max` elements and an empty chunk at the end, so the effect stream asks for one chunk at a time and the ZIO Blocks stream never runs further ahead than that: this is the back-pressure. Two details are workarounds for 0.0.56. A pull throws the stream's typed error wrapped in `StreamError`, a class in the `internal` package, so `BlockingPull#next` unwraps it into a `Left`. And after a failed pull, closing the reader throws the same failure again, although the stream's finalizers have already run, so `BlockingPull#close` ignores close errors then (a known bug in 0.0.56).

The stream bridges use `BlockingPull` in one direction. In the other, each run of the ZIO Blocks stream starts the effect stream behind a bounded buffer, and closing the run interrupts or cancels it. ZIO has `ZStream#toIterator` for this; fs2 has no equivalent, so `CatsStreams.fromFs2` feeds a bounded `Queue` from a background fiber:

<Tabs groupId="effect-system" defaultValue="zio">
  <TabItem value="zio" label="ZIO">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/ZioStreams.scala
```

  </TabItem>
  <TabItem value="cats" label="cats-effect">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/CatsStreams.scala
```

  </TabItem>
</Tabs>

The programs below read the order-events file whole, read only its first event, read a file with a malformed line, and interrupt a stream that never ends. Then they show back-pressure both ways, by printing each element the producer emits while the consumer takes three. The ZIO program prints:

```text
-- whole file
order-events file closed
events: Chunk(placed,paid,shipped)
-- first event only
order-events file closed
events: Chunk(placed)
-- a malformed line
order-events file closed
result: Left(expected '{' at: .)
-- interruption
endless stream closed
-- back-pressure, ZIO Blocks -> ZStream
zio blocks produced 1
zio blocks produced 2
zio blocks produced 3
zio blocks produced 4
zstream took: Chunk(1,2,3)
-- back-pressure, ZStream -> ZIO Blocks
zstream produced 1
zstream produced 2
zstream produced 3
zstream finalized
zio blocks took: Right(Chunk(1,2,3))
```

The file is closed exactly once in each run: after the last event, after `ZStream#take` has what it needs, and after the malformed line, whose `SchemaError` arrives in the `ZStream`'s typed error channel. With a chunk size of 2, the ZIO Blocks stream produces 4 elements for a consumer that takes 3, and no more.

The cats-effect program prints the same lines with `List` in place of `Chunk`, `fs2` in place of `zstream`, and "cancellation" in its label. Its last block shows the bounded queue, of size 2 here, letting the fs2 stream run a few elements ahead before it is cancelled:

```text
-- back-pressure, fs2 -> ZIO Blocks
fs2 produced 1
fs2 produced 2
fs2 produced 3
fs2 produced 4
fs2 produced 5
fs2 produced 6
fs2 finalized
zio blocks took: Right(Chunk(1,2,3))
```

<details>
  <summary>Runnable version, the ZIO tab: <code>Step5StreamsZio.scala</code></summary>

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step5StreamsZio.scala
```

</details>

<details>
  <summary>Runnable version, the cats-effect tab: <code>Step5StreamsCats.scala</code></summary>

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/Step5StreamsCats.scala
```

</details>

## Putting It Together

The complete service uses each case once. It parses three JSON requests (the third has no total), authorizes each payment through the gateway with a one-second timeout, stores the order on the blocking pool, and appends it to the archive, which the program opens as a lifted ZIO Blocks resource for its whole run. Then it sums the revenue with a stream inside `JdbcTransactor#connect`, and streams a shipment events file through `ZStream` or fs2. Unlike the sections, the gateway answers after 50 ms:

<Tabs groupId="effect-system" defaultValue="zio">
  <TabItem value="zio" label="ZIO">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/CompleteZio.scala
```

  </TabItem>
  <TabItem value="cats" label="cats-effect">

```scala mdoc:embed:zio-blocks-examples/effect-integration/src/main/scala/effectintegration/CompleteCats.scala
```

  </TabItem>
</Tabs>

Both programs print the same lines. The archive closes last, when the program's scope or `Resource` ends:

```text
archive opened
order 1 placed (auth-1)
order 2 placed (auth-2)
rejected: missing required field "totalCents" at: .
revenue: 6199 cents
shipped: order 1
shipped: order 2
order-events file closed
archive closed
```

## Running the Examples

All runnable code from this guide is in [`zio-blocks-examples/effect-integration/src/main/scala/effectintegration/`](https://github.com/zio/zio-blocks/tree/main/zio-blocks-examples/effect-integration/src/main/scala/effectintegration). Run any file from that folder with `scala-cli run <File>.scala`, or from `zio-blocks-examples` with `sbt "effectIntegration/runMain effectintegration.<ObjectName>"`.

## Going Further

- [How to Use the sql Module from a ZIO Application](./sql-from-zio.md) covers `TransactorZIO`, its `ZLayer`, and transactions whose body is a ZIO effect.
- [How to Wrap Callback and Future-Based APIs in Async](./wrap-callback-and-future-apis.md) explains the `cancellable` helper and the other ways into `Async`.
- [How to Stream Files and Sockets Without Leaking Resources](./stream-files-and-sockets.md) builds ZIO Blocks streams over files and sockets, which the stream bridge above turns into `ZStream` or fs2 streams.
- [Coming from fs2, ZIO Streams or Ox](./streaming-library-migration.md) and [Coming from cats-effect Resource, ZIO Scope/ZLayer, macwire or Using](./resource-and-di-migration.md) are for moving code off your effect system rather than calling ZIO Blocks from it.
