---
id: result
title: "Result"
---

`Result[Ok, Err]` is a WIT-compatible error type for representing success or failure. It's analogous to Rust's `Result<T, E>` and provides first-class support for error handling in Golem agents. It differs from Scala's `Either[L, R]` in that it's designed for WebAssembly component model serialization.

```scala mdoc:invisible
import golem.Result
```

Result[Ok, Err] is a type alias for WitResult[Ok, Err], providing methods to construct, manipulate, and convert results:

```scala mdoc:compile-only
// object Result {
//   def ok[Ok](value: Ok): Result[Ok, Nothing]
//   def err[Err](value: Err): Result[Nothing, Err]
//   def fromEither[Err, Ok](either: Either[Err, Ok]): Result[Ok, Err]
//   def fromOption[Ok](value: Option[Ok], orElse: => String): Result[Ok, String]
// }
```

## Overview

Use `Result` when you want to return either a success value or an error. It's fully serializable through Golem's schema system and integrates with the WIT component model.

```scala
import golem.Result
import scala.concurrent.Future

def divide(a: Int, b: Int): Result[Double, String] =
  if (b == 0) Result.err("Division by zero")
  else Result.ok(a.toDouble / b)
```

## Creating Results

### Success (ok)

```scala
import golem.Result

val success: Result[Int, String] = Result.ok(42)
val success2: Result[String, Nothing] = Result.ok("Hello")
```

### Failure (err)

```scala
import golem.Result
import zio.blocks.schema.Schema

val failure: Result[Nothing, String] = Result.err("Something went wrong")

// Custom error type with schema
case class ApiError(code: Int, message: String) derives Schema
val failure2: Result[Int, ApiError] = Result.err(ApiError(500, "Internal error"))
```

### From Either

Convert Scala's `Either` to `Result`:

```scala
import golem.Result
import scala.util.Try

val either: Either[String, Int] = Right(42)
val result: Result[Int, String] = Result.fromEither(either)

val either2: Either[String, Int] = Left("Error")
val result2: Result[Int, String] = Result.fromEither(either2)
```

### From Option

Convert `Option` to `Result` with a fallback error message:

```scala
import golem.Result

val option: Option[String] = Some("value")
val result: Result[String, String] = Result.fromOption(option, "Not found")

val emptyOption: Option[String] = None
val result2: Result[String, String] = Result.fromOption(emptyOption, "Not found")
```

## Pattern Matching

Use the typed `WitResult` variant with proper extractors:

```scala
import golem.runtime.wit.WitResult

def process(result: WitResult[Int, String]): String =
  result match {
    case WitResult.Ok(value) => s"Success: $value"
    case WitResult.Err(error) => s"Error: $error"
  }
```

The import `import golem.runtime.wit.WitResult` is required to access the proper case classes and extractors.

## Using in Agent Methods

Return `Result` from agent methods to signal success or failure:

```scala
import golem.runtime.annotations.agentDefinition
import golem.{Result, BaseAgent}
import scala.concurrent.Future

@agentDefinition
trait Calculator extends BaseAgent {
  def divide(a: Int, b: Int): Future[Result[Double, String]]
  def sqrt(x: Double): Future[Result[Double, String]]
}
```

Clients receive the result and can act accordingly:

```scala mdoc:invisible
import golem.Result
import scala.concurrent.Future
```

Clients receive the result and can act accordingly:

```scala mdoc:compile-only
val calc: Future[Result[Double, String]] = Future.successful(Result.ok(5.0))

calc.foreach {
  case r if r == Result.ok(5.0) => println("Result is 5.0")
  case _ => println("Result is not 5.0")
}
```

## Error Types

The error type (`Err`) can be any type with a schema:

```scala
import golem.Result
import zio.blocks.schema.Schema

// String errors
val strError: Result[Int, String] = Result.err("Not a number")

// Custom error types
case class ValidationError(field: String, message: String) derives Schema
val customError: Result[Int, ValidationError] =
  Result.err(ValidationError("age", "Must be positive"))

// Enum errors
enum ApiError derives Schema:
  case NotFound
  case Unauthorized
  case InternalError(msg: String)

val enumError: Result[String, ApiError] = Result.err(ApiError.NotFound)
```

## Transforming Results

The underlying `WitResult` provides rich transformation methods:

### Map (transform success value)
```scala
import golem.runtime.wit.WitResult

val result: WitResult[Int, String] = WitResult.ok(42)
val doubled: WitResult[Int, String] = result.map(_ * 2)
```

### MapError (transform error value)
```scala
import golem.runtime.wit.WitResult

val result: WitResult[Int, String] = WitResult.err("failed")
val transformed: WitResult[Int, Int] = result.mapError(_.length)
```

### FlatMap (chain result-producing operations)
```scala
import golem.runtime.wit.WitResult

val result: WitResult[Int, String] = WitResult.ok(10)
val chained: WitResult[String, String] = result.flatMap { n =>
  if (n > 0) WitResult.ok(s"Positive: $n")
  else WitResult.err("Not positive")
}
```

### Fold (pattern match without case syntax)
```scala
import golem.runtime.wit.WitResult

val result: WitResult[Int, String] = WitResult.ok(42)
val message: String = result.fold(
  err => s"Error: $err",
  ok => s"Success: $ok"
)
```

### Tap (inspect without altering)
```scala
import golem.runtime.wit.WitResult

val result: WitResult[Int, String] = WitResult.ok(42)
result.tap(value => println(s"Computed: $value"))
```

### Unwrap (extract with throwing)
```scala
import golem.runtime.wit.WitResult

val result: WitResult[Int, String] = WitResult.ok(42)
val value: Int = result.unwrap() // Throws UnwrapError on error
val error: String = result.unwrapErr() // Throws on success
```

### UnwrapForWit (extract at WIT boundary)

When returning a result across the WIT boundary (from Scala.js back to the host), use `unwrapForWit()`:

```scala mdoc:invisible
import golem.runtime.wit.WitResult
import scala.concurrent.Future
```

When returning a result across the WIT boundary (from Scala.js back to the host), use `unwrapForWit()`:

```scala mdoc:compile-only
def computeResult(): Future[WitResult[Int, String]] = 
  Future.successful(WitResult.Ok(42))

def exportToHost(): Future[Int] = {
  computeResult().map { result =>
    // Unwrap for WIT: throws error payload on failure, returns value on success
    result.unwrapForWit()
  }
}
```

This mirrors the JS SDK behavior where `Result.err` triggers a rejected promise. On the host side, a thrown error payload becomes a failed promise.

## Variance

`Result` is covariant in both type parameters:

```scala
import golem.Result

val result: Result[Int, String] = Result.ok(42)
val widened: Result[Any, Any] = result // Allowed due to covariance
```

## Relation to Other Types

- **`Either[L, R]`** — Scala's standard error type; use `Result.fromEither()` to convert
- **`Option[A]`** — Scala's optional type; use `Result.fromOption()` to convert
- **`WitResult`** — The underlying implementation (rarely used directly)
- **`GolemSchema`** — `Result` types are fully serializable

## Best Practices

- **Use `Result` for domain errors** — When errors are expected part of the API
- **Keep error messages concise** — They're serialized and transmitted
- **Use custom error types** — More informative than strings
- **Provide context in errors** — Include field names, values, constraints
- **Document possible errors** — Help clients understand what can fail
- **Avoid `Result` for unexpected failures** — Use exceptions and HostApi rollback instead
