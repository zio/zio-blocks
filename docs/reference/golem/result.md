---
id: result
title: "Result"
---

`Result[Ok, Err]` is a WIT-compatible error type for representing success or failure. It's analogous to Rust's `Result<T, E>` and provides first-class support for error handling in Golem agents. It differs from Scala's `Either[L, R]` in that it's designed for WebAssembly component model serialization.

```scala
object Result {
  type Result[+Ok, +Err] = WitResult[Ok, Err]

  def ok[Ok](value: Ok): Result[Ok, Nothing]
  def err[Err](value: Err): Result[Nothing, Err]
  def fromEither[Err, Ok](either: Either[Err, Ok]): Result[Ok, Err]
  def fromOption[Ok](value: Option[Ok], orElse: => String): Result[Ok, String]
}
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

val failure: Result[Nothing, String] = Result.err("Something went wrong")
val failure2: Result[Int, Exception] = Result.err(new RuntimeException("Error"))
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

Match on success/failure using `WitResult` cases:

```scala
import golem.runtime.wit.WitResult

def process(result: WitResult[Int, String]): String =
  result match {
    case WitResult.Ok(value) => s"Success: $value"
    case WitResult.Err(error) => s"Error: $error"
  }
```

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

```scala
import scala.concurrent.Future
import golem.Result

val calc: Future[Result[Double, String]] = ??? // Call divide

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
