---
id: examples
title: "Examples & Patterns"
---

This page shows complete, working examples that demonstrate how golem types work together in realistic scenarios.

## Example 1: Stateful Counter Agent

A simple counter agent with durable state using snapshots:

```scala
import golem.runtime.annotations.{agentDefinition, agentImplementation, description}
import golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import golem.{BaseAgent, AgentCompanion, Snapshotted}
import zio.blocks.schema.Schema
import scala.concurrent.Future

// Define the agent interface
@agentDefinition
trait Counter extends BaseAgent {
  class Id(val name: String)
  
  @description("Increment counter by one")
  def increment(): Future[Int]
  
  @description("Get current value")
  def get(): Future[Int]
  
  @description("Reset to zero")
  def reset(): Future[Unit]
}

// Implement the agent
@agentImplementation()
class CounterImpl(name: String) extends Counter {
  private val state: Snapshotted[Int] = Snapshotted(0)
  
  override def increment(): Future[Int] =
    Future.successful {
      state.update(n => n + 1)
      state.current
    }
  
  override def get(): Future[Int] =
    Future.successful(state.current)
  
  override def reset(): Future[Unit] =
    Future.successful(state.set(0))
}

// Register for client access
object Counter extends AgentCompanion[Counter]

object CounterModule {
  val definition: AgentDefinition[Counter] =
    AgentImplementation.registerClass[Counter, CounterImpl]
}
```

**How it works:**
1. `@agentDefinition` marks Counter as an agent trait
2. Inner `class Id(val name: String)` defines constructor parameters
3. `Snapshotted[Int]` persists the counter value across invocations
4. `@agentImplementation()` decorates the implementation
5. `AgentCompanion[Counter]` provides `.get()` client access
6. `AgentImplementation.registerClass[Counter, CounterImpl]` registers the agent

## Example 2: Multi-Step Order Processing with Transactions

Process orders atomically with automatic compensation on failure:

```scala
import golem.runtime.annotations.{agentDefinition, agentImplementation}
import golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import golem.{BaseAgent, Transactions, Result}
import zio.blocks.schema.Schema
import scala.concurrent.Future

case class OrderItem(sku: String, quantity: Int) derives Schema
case class Order(id: String, items: List[OrderItem]) derives Schema

@agentDefinition
trait OrderProcessor extends BaseAgent {
  def processOrder(order: Order): Future[Result[String, String]]
}

@agentImplementation()
class OrderProcessorImpl() extends OrderProcessor {
  override def processOrder(order: Order): Future[Result[String, String]] = {
    val result = Transactions.infallibleTransaction { tx =>
      // Step 1: Reserve inventory
      val reserveOp = Transactions.operation[Order, String, String](
        ord => {
          println(s"Reserving inventory for ${ord.id}")
          Right(s"reserved-${ord.id}")
        }
      )((ord, _) => {
        println(s"Releasing inventory for ${ord.id}")
        Right(())
      })

      val reservationId = tx.execute(reserveOp, order)

      // Step 2: Process payment
      val paymentOp = Transactions.operation[String, String, String](
        resId => {
          println(s"Processing payment for $resId")
          Right(s"payment-${resId}")
        }
      )((_, paymentId) => {
        println(s"Refunding $paymentId")
        Right(())
      })

      val paymentId = tx.execute(paymentOp, reservationId)

      // Step 3: Create shipment
      val shipmentOp = Transactions.operation[String, String, String](
        payId => {
          println(s"Creating shipment for $payId")
          Right(s"shipment-${payId}")
        }
      )((_, shipId) => {
        println(s"Canceling shipment $shipId")
        Right(())
      })

      tx.execute(shipmentOp, paymentId)
    }

    Future.successful(Result.ok(result))
  }
}

object OrderProcessorModule {
  val definition: AgentDefinition[OrderProcessor] =
    AgentImplementation.registerClass[OrderProcessor, OrderProcessorImpl]
}
```

**How it works:**
1. `Transactions.infallibleTransaction` starts an atomic region
2. Each step declares both execute and compensation logic
3. If any step fails, all previous compensations run in reverse order
4. The transaction retries automatically until all steps succeed
5. `Result.ok()` returns success to the caller

## Example 3: HTTP-Mounted User Agent

Expose agent methods as HTTP endpoints:

```scala
import golem.runtime.annotations.{agentDefinition, agentImplementation, endpoint, description}
import golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import golem.BaseAgent
import zio.blocks.schema.Schema
import scala.concurrent.Future

case class User(id: String, name: String, email: String) derives Schema

@agentDefinition(mount = "/users/{userId}")
trait UserService extends BaseAgent {
  class Id(val userId: String)
  
  @description("Get user profile")
  @endpoint("GET", "/profile")
  def getProfile(): Future[User]
  
  @description("Update user email")
  @endpoint("POST", "/email")
  def updateEmail(newEmail: String): Future[Unit]
}

@agentImplementation()
class UserServiceImpl(userId: String) extends UserService {
  override def getProfile(): Future[User] =
    Future.successful(User(userId, "John Doe", "john@example.com"))
  
  override def updateEmail(newEmail: String): Future[Unit] = {
    println(s"Updating email to $newEmail")
    Future.successful(())
  }
}

object UserServiceModule {
  val definition: AgentDefinition[UserService] =
    AgentImplementation.registerClass[UserService, UserServiceImpl]
}
```

**HTTP Endpoints:**
- `GET /users/user123/profile` → `getProfile()`
- `POST /users/user123/email?newEmail=new@example.com` → `updateEmail("new@example.com")`

Constructor parameters (`userId`) become path variables. Method parameters become query strings.

## Example 4: Agent-to-Agent Communication (RPC)

One agent invoking methods on another:

```scala
import golem.runtime.annotations.{agentDefinition, agentImplementation}
import golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import golem.runtime.rpc.AgentClient
import golem.BaseAgent
import scala.concurrent.Future

// Define a simple service agent
@agentDefinition
trait MathService extends BaseAgent {
  def add(a: Int, b: Int): Future[Int]
}

@agentImplementation()
class MathServiceImpl() extends MathService {
  override def add(a: Int, b: Int): Future[Int] =
    Future.successful(a + b)
}

// Client that calls MathService
@agentDefinition
trait Calculator extends BaseAgent {
  def compute(x: Int, y: Int): Future[Int]
}

@agentImplementation()
class CalculatorImpl() extends Calculator {
  override def compute(x: Int, y: Int): Future[Int] = {
    // Connect to MathService and call add()
    val mathServiceType = AgentClient.agentType[MathService]
    val mathService = AgentClient.connect(mathServiceType, ())
    
    mathService.flatMap(ms => ms.add(x * 2, y * 3))
  }
}

object CalculatorModule {
  val definition: AgentDefinition[Calculator] =
    AgentImplementation.registerClass[Calculator, CalculatorImpl]
}
```

**How it works:**
1. `AgentClient.agentType[MathService]` gets the agent type metadata
2. `AgentClient.connect(type, constructorArgs)` connects to a remote agent instance
3. Once connected, call methods on the agent proxy like normal
4. The framework handles RPC serialization/deserialization transparently

## Example 5: Configuration and Secrets

Agents declaring configuration fields injected at runtime:

```scala
import golem.runtime.annotations.{agentDefinition, agentImplementation}
import golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import golem.config.Secret
import golem.BaseAgent
import scala.concurrent.Future

@agentDefinition
trait DataService extends BaseAgent {
  val databaseUrl: String
  val apiKey: Secret[String]
  val maxConnections: Int = 10  // With default
  
  def query(sql: String): Future[String]
}

@agentImplementation()
class DataServiceImpl(databaseUrl: String, apiKey: Secret[String], maxConnections: Int = 10) extends DataService {
  override def query(sql: String): Future[String] = {
    // Configuration fields are already injected by the runtime
    val dbUrl = databaseUrl
    val key = apiKey.get
    
    // Use dbUrl and key to execute query
    Future.successful(s"Executed: $sql against $dbUrl with $maxConnections connections")
  }
}

object DataServiceModule {
  val definition: AgentDefinition[DataService] =
    AgentImplementation.registerClass[DataService, DataServiceImpl]
}
```

Configuration is provided by the Golem runtime via the manifest at deployment time, allowing different values for each environment (dev/staging/prod) without code changes.

## Common Composition Patterns

### Pattern 1: Request → Process → Response

Simple request-response with a single async operation:

```scala
@golem.runtime.annotations.agentDefinition
trait RequestHandler extends golem.BaseAgent {
  def handle(request: String): scala.concurrent.Future[String]
}
```

### Pattern 2: Stateful Agent with Snapshots

Maintain state across multiple invocations:

```scala
@golem.runtime.annotations.agentImplementation()
class StatefulImpl() extends Stateful {
  private val state: golem.Snapshotted[Map[String, Int]] = golem.Snapshotted(Map.empty)
  // Modify state, it persists automatically
}
```

### Pattern 3: Multi-Step Saga with Compensation

Atomic operations with automatic rollback on failure:

```scala
val result = golem.Transactions.infallibleTransaction { tx =>
  val step1 = golem.Transactions.operation(???)
  val step2 = golem.Transactions.operation(???)
  
  tx.execute(step1, input)
  tx.execute(step2, result1)
}
```

### Pattern 4: HTTP-Mounted Service

Expose agent as REST API:

```scala
@golem.runtime.annotations.agentDefinition(mount = "/api/{resource}")
trait RestService extends golem.BaseAgent {
  class Id(val resource: String)
  def get(): scala.concurrent.Future[String]
}
```

### Pattern 5: Agent-to-Agent RPC

One agent calling another:

```scala
val remoteType = golem.runtime.rpc.AgentClient.agentType[RemoteAgent]
val remote = golem.runtime.rpc.AgentClient.connect(remoteType, constructorArgs)
remote.flatMap(_.someMethod())
```
