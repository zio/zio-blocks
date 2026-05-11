---
id: configuration
title: "Configuration & Secrets"
---

Agents can declare configuration fields that are injected by the Golem runtime. Use `Config` for environment-specific values and `Secret` for sensitive credentials.

The following are the primary configuration APIs available in agents (pseudocode):

```scala
// Configuration API (golem.wasi.Config)
object Config {
  def get(key: String): Future[Option[String]]
  def getRequired(key: String): Future[String]
}

// Secrets API (golem.wasi.Secret)
object Secret {
  def get(name: String): Future[Option[String]]
}
```

## Overview

Configuration allows agents to access external settings without hardcoding them:

| Pattern | Purpose | Example |
|---------|---------|---------|
| **Config** | Environment variables, deployment settings | Database URL, API timeout, feature flags |
| **Secret** | Sensitive credentials | API keys, passwords, tokens |
| **Override** | Deployment-time customization | Different values for dev/staging/prod |

## Declaring Configuration Fields

Agents declare configuration via an annotation:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent

@agentDefinition
trait ConfiguredAgent extends BaseAgent {
  def getApiKey(): scala.concurrent.Future[String]
  def getTimeout(): scala.concurrent.Future[Int]
}
```

The Golem runtime injects these values at agent startup.

## Accessing Config at Runtime

Use the `Config` API to access configuration:

```scala mdoc:compile-only
import golem.wasi.Config
import scala.concurrent.Future

val apiKey: Future[Option[String]] = Config.get("API_KEY")
val requiredKey: Future[String] = Config.getRequired("API_KEY")
```

**`get(key)`** — Returns `Option[String]`; `None` if not set:
```scala
import golem.wasi.Config

Config.get("DEBUG_MODE") // Future[Option[String]]
```

**`getRequired(key)`** — Returns `String`; fails if not set:
```scala
import golem.wasi.Config

Config.getRequired("DATABASE_URL") // Future[String], throws if missing
```

## Secrets

Access secrets (credentials, API keys) via the `Secret` API:

```scala
import golem.wasi.Secret
import scala.concurrent.Future

val token: Future[Option[String]] = Secret.get("github-token")
```

Secrets are handled like configuration but are managed securely by the Golem runtime. Never hardcode secrets; always retrieve them at runtime.

## Configuration Examples

### Database Connection String

```scala
import golem.wasi.Config
import scala.concurrent.Future

@golem.runtime.annotations.agentImplementation()
class DatabaseAgentImpl() extends DatabaseAgent {
  override def query(sql: String): Future[String] = {
    val dbUrl = Config.getRequired("DATABASE_URL")
    // Use dbUrl to connect
    Future.successful("result")
  }
}
```

### Feature Flags

```scala
import golem.wasi.Config
import scala.concurrent.Future

val enableCache: Future[Option[String]] = Config.get("ENABLE_CACHE")
enableCache.map {
  case Some("true") => true
  case _ => false
}
```

### Timeout Configuration

```scala
import golem.wasi.Config
import scala.concurrent.Future

val timeoutMs: Future[Int] = Config.getRequired("REQUEST_TIMEOUT_MS").map(_.toInt)
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

Introspect agent configuration to discover available settings:

```scala
import golem.config.ConfigIntrospection
import scala.concurrent.Future

val schema = ConfigIntrospection.getSchema()
// Inspect available config keys and types
```

Rarely used directly; mostly for tooling and documentation.

## Typed Configuration

For type-safe configuration, declare config fields in the agent trait:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent

@agentDefinition
trait TypedConfigAgent extends BaseAgent {
  val databaseUrl: String
  val timeout: Int
  val enableCache: Boolean = true  // With default
  
  def connect(): scala.concurrent.Future[String]
}
```

The Golem runtime injects these fields based on configuration.

## Error Handling

Configuration access can fail:

```scala
import golem.wasi.Config
import scala.concurrent.Future

val result: Future[String] = Config.getRequired("REQUIRED_KEY").recover {
  case _ => "default-value"
}
```

Always handle missing configuration gracefully or fail early at agent startup.

## Best Practices

- **Use `getRequired()` for critical settings** — Fail fast if not configured
- **Provide sensible defaults** — Make agents work with minimal configuration
- **Use environment variable conventions** — UPPER_CASE, underscore-separated
- **Document configuration** — List all required and optional keys
- **Treat secrets separately** — Never log secrets or include in error messages
- **Test configuration loading** — Verify agents handle missing/invalid config
- **Use feature flags for gradual rollout** — Toggle features without redeployment
