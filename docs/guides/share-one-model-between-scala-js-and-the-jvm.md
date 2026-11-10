---
id: share-one-model-between-scala-js-and-the-jvm
title: "Share One Model Between a Scala.js Page and a JVM Server"
description: "Describe a model once with Schema, encode it on a JVM server and decode it in Scala.js, so both sides agree on the JSON."
keywords:
  - "Scala.js"
  - "Shared Model"
  - "JSON Codec"
  - "Schema"
  - "toJsonString"
  - "fromJson"
---

Welcome! This tutorial is for Scala developers who already know the basics of ZIO Blocks and want their server and their browser code to share one data model. You should have finished [Your First Schema](./your-first-schema.md), and it helps to have read [Build a Small JSON Backend from Blocks](./small-json-backend-from-blocks.md). You don't need any prior experience with Scala.js to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- Why a web app that uses one model on both sides needs only one [`Schema`](../reference/schema/schema.md)
- How the JVM server encodes a value to JSON with `toJsonString`
- How the Scala.js client decodes that JSON with `fromJson`, using the same `Schema`
- How a decode error looks when the two sides disagree about the shape of the data

We learn these concepts by building a small to-do list that travels from a server to a client:

1. Why one model matters
2. Describe the model once
3. Encode on the JVM server
4. Decode in Scala.js
5. Read a decode error
6. Run both sides yourself

We recommend reading from top to bottom, because each section uses the model from the one before it.

## 2. Background: Two Runtimes, One Model

A web application usually has two programs. The server runs on the JVM and writes JSON. The client runs in the browser and reads that JSON. The client is often written in Scala too, compiled to JavaScript by Scala.js. Scala.js is a compiler that turns Scala code into JavaScript, so the same Scala source can run on the JVM and in a browser.

Without a shared model, each side has its own case class. The field names and types are written twice, and they drift apart the first time someone renames a field on one side only. ZIO Blocks avoids that drift: the `Schema` for a case class is derived from the case class itself, and the same source file compiles for both platforms. Both sides then encode and decode through that one description.

This tutorial uses Node.js in place of a browser. Node runs the Scala.js output and provides `fetch`, the same HTTP call that browsers use, so the client code is the code a browser would run. We print the decoded list to the console instead of drawing it on a page, which keeps the tutorial free of any DOM library.

## 3. Describe the Model Once

A `Schema` describes the shape of a type, and the rest of this tutorial depends on one shared description. We define two case classes and derive the schema for the outer one:

```scala mdoc:silent
import zio.blocks.schema._

final case class Task(id: Int, title: String, done: Boolean)

final case class TaskList(owner: String, tasks: List[Task])

object TaskList {
  implicit val schema: Schema[TaskList] = Schema.derived
}
```

The code above:

- `Task` is one to-do item with an identifier, a title and a `done` flag.
- `TaskList` holds an owner and a list of tasks. It nests `Task`, so the schema for `Task` comes from the derivation without a second `implicit val`.
- `implicit val schema: Schema[TaskList] = Schema.derived` derives the description at compile time and stores it where the codec lookup finds it.

The same two definitions live in the example project's `shared` folder, in `Model.scala`, next to a small `Sample` object that holds the list the server sends. The build compiles that folder for the JVM and for Scala.js, so the server and the client read one file.

## 4. Encode on the JVM Server

On the server side, a value becomes JSON through the `toJsonString` extension method, which comes from the schema. We create a list and encode it:

```scala mdoc:silent
val list = TaskList("Ada", List(Task(1, "Write the tutorial", done = true), Task(2, "Share one model", done = false)))
```

Next, we encode the list and print the result:

```scala mdoc
println(list.toJsonString)
```

The code above creates a `TaskList` with two tasks, one finished and one still open, and prints its JSON text. Notice that each field name in the output comes from the case class, and that `true` and `false` are written as JSON literals. The server does not write any JSON by hand.

To send this text over HTTP, the server only needs to encode the list and write the bytes to a response. The example project's `Concept4ServeJsonExample` does that with the JDK's built-in HTTP server, which needs no extra dependency. It serves `Sample.list` at `http://localhost:8090/tasks`:

<details>
  <summary>Runnable version: the list served over HTTP on port 8090</summary>

```scala mdoc:embed:zio-blocks-examples/share-one-model/jvm/src/main/scala/shareonemodel/Concept4ServeJsonExample.scala:show-line-numbers
```

</details>

The code above:

- `HttpServer.create(new InetSocketAddress("localhost", 8090), 0)` creates the built-in server on port 8090.
- `server.createContext("/tasks", ...)` registers the handler that runs on each request to `/tasks`.
- `Sample.list.toJsonString` encodes the shared list from the schema, and the handler writes those bytes with status 200 and the `application/json` content type.
- `server.start()` starts serving, and the server runs until you press Ctrl+C.

## 5. Decode in Scala.js

On the client side, the browser code fetches the JSON and decodes it with the same schema. We do not write a second model, so the decoder comes from `TaskList.schema` in the shared folder. The client file is Scala.js only, so mdoc does not compile it here; the run in [Run Both Sides](#7-run-both-sides) is its check. The file is `Concept5DecodeOnScalaJsExample.scala`:

```scala mdoc:embed:zio-blocks-examples/share-one-model/js/src/main/scala/shareonemodel/Concept5DecodeOnScalaJsExample.scala:show-line-numbers
```

The code above:

- `js.Dynamic.global.fetch` calls the `fetch` function that Node and browsers provide. We reach it through `scala.scalajs.js`, so no extra library is needed.
- `r.text()` returns the response body as a string, and the `Thenable` import turns the JavaScript promises into Scala `Future`s.
- `json.fromJson[TaskList]` decodes the text with the shared schema and returns an `Either`, the same call that [Your First Schema](./your-first-schema.md) introduces.
- The `Right` branch prints the owner and each task, with `x` for finished tasks.
- The `Left` branch prints `Decode failed:` and the error message, and `body.failed` prints `Request failed:` when the server cannot be reached.

When you run the client against the server, it prints the same list that the server encoded:

```text
Owner: Ada
[x] 1: Write the tutorial
[ ] 2: Share one model
```

## 6. Read a Decode Error

The client shows what happens when the JSON does not match the shared model. Suppose the server's `done` field is missing from one task. We decode that text on the JVM with the same `TaskList` schema:

```scala mdoc:silent
val missingDone = """{"owner":"Ada","tasks":[{"id":1,"title":"Write the tutorial"}]}""".fromJson[TaskList]
```

Next, we match on the result and print its message:

```scala mdoc
missingDone match {
  case Left(error) => println(error.message)
  case Right(value) => println(s"Decoded: $value")
}
```

The code above decodes a list whose only task lacks `done`. The `Left` branch prints the error message, which names the missing field. The client's `Left` branch in `Concept5DecodeOnScalaJsExample` prints the same message with a `Decode failed:` prefix, so a mismatched server shows up as a readable error in the client instead of a wrong list.

## 7. Run Both Sides

The example project is `zio-blocks-examples/share-one-model`, a cross-built sbt subproject. Its `project/plugins.sbt` adds the two Scala.js plugins that compile the client:

```scala
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.22.0")
```

The example project's `build.sbt` declares the `shareOneModel` cross-project. Its `shared` folder holds the model, its `jvm` folder holds the server, and its `js` folder holds the client. The schema dependency is written with `%%%`, the cross-platform form of `%%`, so it resolves to the Scala.js artifact for the client.

Start the server in one terminal and keep it running:

```bash
cd zio-blocks-examples
sbt "shareOneModelJVM/runMain shareonemodel.Concept4ServeJsonExample"
```

Then run the client in a second terminal. It needs Node.js 18 or newer, because it calls the global `fetch`, and it prints the list:

```bash
cd zio-blocks-examples
sbt "shareOneModelJS/run"
```

All runnable code from this tutorial is in [`zio-blocks-examples/share-one-model`](https://github.com/zio/zio-blocks/tree/main/zio-blocks-examples/share-one-model). The shared model lives in its `shared` folder, the server in `jvm` and the client in `js`.

## What You've Learned

In this tutorial, you learned:

- Why a `Schema` derived once can serve both a JVM server and a Scala.js client
- How `toJsonString` turns a value into JSON on the server
- How `fromJson` turns the same JSON back into a value in Scala.js
- How a decode error reports a missing field, so a mismatch is visible on either side

You now have a working pattern for a shared model. The next step is to apply it to the data your own application exchanges.

## Where to Go Next

- **Ready to build a full backend?** Read [Build a Small JSON Backend from Blocks](./small-json-backend-from-blocks.md), which serves JSON with ZIO Blocks from end to end.
- **Want to control the JSON shape?** Read [Customize JSON Encoding](./customize-json-encoding.md) to rename fields and change how values are written.
- **Want the full API?** Read the reference page for [`Schema`](../reference/schema/schema.md).
