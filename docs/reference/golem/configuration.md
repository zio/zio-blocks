---
id: configuration
title: "Configuration & Secrets"
---

Agents can declare configuration fields that are injected by the Golem runtime at startup. Configuration values are provided per deployment and can vary across environments (dev, staging, prod).

## Overview

Configuration allows agents to access external settings without hardcoding them:

| Pattern | Purpose | Example |
|---------|---------|---------|
| **Typed config fields** | Environment variables, deployment settings | Database URL, API timeout, feature flags |
| **Secret[T]** | Sensitive credentials with lazy loading | API keys, passwords, tokens |
| **Defaults** | Optional fields with sensible defaults | Optional feature flags |

## Declaring Configuration Fields

Agents declare configuration by adding fields to the trait. The Golem runtime injects these values at agent startup:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent
import scala.concurrent.Future

@agentDefinition
trait ConfiguredAgent extends BaseAgent {
  val apiKey: String
  val databaseUrl: String
  val timeout: Int = 30  // With default value
  
  def process(): Future[String]
}
```

All config fields must have an implicit `Schema[T]` available (derives automatically for standard types).

## Using Configuration in Implementations

Access configuration fields directly in your implementation:

```scala
import golem.runtime.annotations.agentImplementation
import scala.concurrent.Future

@agentImplementation()
class ConfiguredAgentImpl(apiKey: String, databaseUrl: String, timeout: Int = 30) extends ConfiguredAgent {
  override def process(): Future[String] = {
    val result = s"Connected to $databaseUrl with timeout=$timeout"
    Future.successful(result)
  }
}
```

The implementation constructor parameters must match the agent trait's config fields.

## Typed Configuration Example

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent
import scala.concurrent.Future

@agentDefinition
trait DatabaseAgent extends BaseAgent {
  val host: String
  val port: Int
  val username: String
  
  def query(sql: String): Future[String]
}
```

The Golem runtime automatically provides these values based on deployment configuration.

## Configuration with Secrets

Use `Secret[T]` for sensitive values that should be loaded lazily:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent
import golem.config.Secret
import scala.concurrent.Future

@agentDefinition
trait SecureAgent extends BaseAgent {
  val apiKey: Secret[String]
  val databaseUrl: String
  
  def authenticate(): Future[Boolean]
}
```

Access the secret value via `.get`:

```scala
@agentImplementation()
class SecureAgentImpl(apiKey: Secret[String], databaseUrl: String) extends SecureAgent {
  override def authenticate(): Future[Boolean] = {
    val key = apiKey.get
    // Use key for authentication
    Future.successful(true)
  }
}
```

## Override Configuration at Deployment

Configuration values are overridden at deployment time via the Golem manifest:

```yaml
components:
  my-agent:
    templates: scala.js
    config:
      host: "prod-db.example.com"
      port: 5432
      username: "db-user"
      apiKey: "secret-key-from-vault"
```

Different environments (dev, staging, prod) have different config overrides without code changes.

## Feature Flags

Declare feature flags as configuration:

```scala
@agentDefinition
trait FeatureFlagAgent extends BaseAgent {
  val enableNewCache: Boolean = false
  val enableMetrics: Boolean = true
  
  def process(): Future[String]
}
```

Toggle features at deployment time without recompiling code.

## Error Handling

Configuration fields are injected at startup. If a required field is missing:

```scala
@agentDefinition
trait RequiredConfigAgent extends BaseAgent {
  val requiredUrl: String  // Must be provided; no default
  
  def connect(): Future[Unit]
}
```

The Golem runtime will fail agent startup if `requiredUrl` is not provided, failing fast rather than failing at runtime.

## Best Practices

- **Use typed config fields** — Let the compiler and macro system handle schema derivation
- **Provide sensible defaults** — Make agents work with minimal configuration
- **Use environment variable conventions** — Configuration keys typically map to UPPER_CASE names
- **Fail fast on missing required config** — Use fields without defaults for critical settings
- **Treat secrets separately** — Use `Secret[T]` for credentials; never log them
- **Test configuration loading** — Verify agents handle various config values
- **Document configuration requirements** — List all required and optional config keys in your agent trait
