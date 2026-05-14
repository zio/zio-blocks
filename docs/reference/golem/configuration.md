---
id: configuration
title: "Configuration & Secrets"
---

Agents can declare configuration fields that are injected by the Golem runtime. Use `Config` for environment-specific values and `Secret` for sensitive credentials.

The primary configuration API uses `golem.wasi.Config`, which provides synchronous access to configuration values:

```
// Configuration API (golem.wasi.Config)
def get(key: String): Either[ConfigError, Option[String]]

// Secrets are accessed via typed configuration fields
// They are represented as golem.config.Secret[A] values
```

## Overview

Configuration allows agents to access external settings without hardcoding them.

## Declaring Configuration Fields

Agents declare configuration using a separate configuration type that extends `AgentConfig[T]`:

```scala mdoc:passthrough
import golem.runtime.annotations.agentDefinition
import golem.{BaseAgent, Schema}

case class MyConfig(apiKey: String, timeout: Int)

object MyConfig {
  implicit val schema: Schema[MyConfig] = Schema.derived
}

@agentDefinition
trait ConfiguredAgent extends BaseAgent with golem.config.AgentConfig[MyConfig] {
  val config: golem.config.Config[MyConfig]
  
  def getApiKey(): scala.concurrent.Future[String] = 
    scala.concurrent.Future.successful(config.value.apiKey)
  def getTimeout(): scala.concurrent.Future[Int] = 
    scala.concurrent.Future.successful(config.value.timeout)
}
```

The Golem runtime detects configuration via the `AgentConfig[T]` mixin and provides the typed config at runtime.

## Accessing Config at Runtime

Use the `Config` API to access configuration synchronously:

```scala mdoc:passthrough
import golem.wasi.Config
type ConfigError = String

val apiKey: Either[ConfigError, Option[String]] = Config.get("API_KEY")

apiKey match {
  case Right(Some(key)) => println(s"API Key: $key")
  case Right(None) => println("API_KEY not set")
  case Left(error) => println(s"Config error: $error")
}
```

**`get(key)`** — Returns `Either[ConfigError, Option[String]]`:
- Returns `Right(Some(value))` if configured
- Returns `Right(None)` if not set
- Returns `Left(error)` on configuration access failure

## Secrets

Secrets are accessed through the typed configuration system using `golem.config.Secret[A]`:

```scala mdoc:passthrough
import golem.config.Secret

// Secrets are injected by the Golem runtime
val token: Secret[String] = ??? // Injected at agent startup
val tokenValue: String = token.get
```

Secrets are managed securely by the Golem runtime. Always use `Secret[A]` to access sensitive credentials rather than retrieving them through the general `Config` API.

## Configuration Examples

### Database Connection String

```scala mdoc:passthrough
import golem.wasi.Config
import scala.concurrent.Future
type ConfigError = String

// In an agent implementation:
val dbUrl = Config.get("DATABASE_URL")
dbUrl match {
  case Right(Some(url)) => 
    // Use url to connect
    Future.successful("result")
  case Right(None) => 
    Future.failed(new Exception("DATABASE_URL not configured"))
  case Left(error) => 
    Future.failed(new Exception(s"Config error: $error"))
}
```

### Feature Flags

```scala mdoc:passthrough
import golem.wasi.Config
type ConfigError = String

val enableCache: Either[ConfigError, Option[String]] = Config.get("ENABLE_CACHE")
val isEnabled = enableCache match {
  case Right(Some("true")) => true
  case _ => false
}
```

### Timeout Configuration

```scala mdoc:passthrough
import golem.wasi.Config
type ConfigError = String

val timeoutMs: Either[ConfigError, Option[String]] = Config.get("REQUEST_TIMEOUT_MS")
val timeout = timeoutMs match {
  case Right(Some(ms)) => ms.toInt
  case Right(None) => 5000 // default
  case Left(_) => 5000 // default on error
}
```

## Override Configuration

Configuration values can be overridden at deployment time via the Golem manifest:

```yaml
components:
  my-agent:
    templates: scala.js
    config:
      DATABASE_URL: "postgres://prod-db:5432/mydb"
      API_TIMEOUT: "30000"
```

Different environments (dev, staging, prod) have different config overrides without code changes.

## Config Schema Introspection

Configuration schemas are automatically discovered by the Golem macro system from your `AgentConfig[T]` type definition. The `Schema[T]` or `ConfigSchema[T]` derives the available config keys and types, which are used by the Golem CLI and tooling for validation and documentation.

## Typed Configuration

For type-safe configuration, extend `AgentConfig[T]` with a configuration case class:

```scala mdoc:passthrough
import golem.runtime.annotations.agentDefinition
import golem.{BaseAgent, Schema}
import scala.concurrent.Future

case class DatabaseConfig(
  databaseUrl: String,
  timeout: Int,
  enableCache: Boolean = true
)

object DatabaseConfig {
  implicit val schema: Schema[DatabaseConfig] = Schema.derived
}

@agentDefinition
trait TypedConfigAgent extends BaseAgent with golem.config.AgentConfig[DatabaseConfig] {
  val config: golem.config.Config[DatabaseConfig]
  
  def connect(): Future[String] = {
    val cfg = config.value
    Future.successful(s"Connecting to ${cfg.databaseUrl}")
  }
}
```

The Golem macro system automatically discovers your `AgentConfig[T]` mixin and provides the loaded configuration at runtime.

## Error Handling

Configuration access can fail:

```scala mdoc:passthrough
import golem.wasi.Config
type ConfigError = String

val result: Either[ConfigError, Option[String]] = Config.get("API_KEY")
```

Always handle missing configuration gracefully or fail early at agent startup.
