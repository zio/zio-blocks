---
id: transactions
title: "Transactions"
---

`Transactions` provides helpers for managing atomic, multi-step operations with automatic rollback. It implements the **saga pattern** — executing a sequence of operations and compensating (rolling back) on failure.

```scala
object Transactions {
  def operation[In, Out, Err](run: In => Either[Err, Out])(
    compensate: (In, Out) => Either[Err, Unit]
  ): Operation[In, Out, Err]

  def infallibleTransaction[A](body: InfallibleTransaction => A): A
  def fallibleTransaction[A, Err](
    body: FallibleTransaction[Err] => Either[Err, A]
  ): Either[TransactionFailure[Err], A]
}
```

## Overview

Transactions ensure that when multiple operations must succeed together, either all succeed or all are compensated (rolled back). ZIO-Golem provides two patterns:

| Pattern | Use Case | Behavior |
|---------|----------|----------|
| **Infallible** | Operations that must eventually succeed | Retries on failure after compensation |
| **Fallible** | Operations that can explicitly fail | Returns error; compensation optional |

## Infallible Transactions

Use `infallibleTransaction` when all operations must eventually succeed. On failure, compensations run automatically and the transaction retries:

```scala
import golem.Transactions
import scala.concurrent.Future

val result = Transactions.infallibleTransaction { tx =>
  // Step 1: Create a user
  val createUserOp = Transactions.operation[Unit, String, String](
    _ => Right("user-123")  // Returns user ID
  )((_, _) => Right(()))      // Compensation (cleanup)

  val userId = tx.execute(createUserOp, ())

  // Step 2: Create an account
  val createAccountOp = Transactions.operation[String, String, String](
    uid => Right(s"account-for-$uid")
  )((_, _) => Right(()))

  val accountId = tx.execute(createAccountOp, userId)

  // If this step fails, steps 2 and 1 compensate in reverse order
  accountId
}
```

The transaction keeps retrying until all operations succeed. Compensation runs in **reverse order** (LIFO).

## Fallible Transactions

Use `fallibleTransaction` when you want explicit error handling. The function returns `Either[TransactionFailure[Err], A]`, wrapping any errors from the body with rollback status:

```scala
import golem.Transactions

val result: Either[Transactions.TransactionFailure[String], String] = Transactions.fallibleTransaction { tx =>
  val step1 = Transactions.operation[Unit, Int, String](
    _ => Right(42)
  )((_, _) => Right(()))

  val value = tx.execute(step1, ())

  val step2 = Transactions.operation[Int, String, String](
    n => if (n > 100) Left("Too large") else Right(s"Value: $n")
  )((n, _) => {
    println(s"Compensating $n")
    Right(())
  })

  tx.execute(step2, value) match {
    case Left(err) => Left(err)
    case Right(msg) => Right(msg)
  }
}

result match {
  case Right(msg) => println(s"Success: $msg")
  case Left(failure) => println(s"Transaction failed: $failure")
}
```

If any operation in the body returns `Left(err)`, the transaction returns `Left(failure)` where `failure` includes both the error and rollback status. Compensation runs automatically.

## Operation Definition

Create an operation with execute and compensation logic:

```scala
import golem.Transactions

val operation = Transactions.operation[Int, String, String](
  // Execute: transform input to output or error
  (input: Int) => {
    if (input < 0) Left("Input must be non-negative")
    else Right(s"Processed: $input")
  }
)(
  // Compensate: rollback given input and successful output
  (input: Int, output: String) => {
    println(s"Undoing: $output")
    Right(())
  }
)
```

The operation encodes three types:
- **`In`** — Input type
- **`Out`** — Output type (returned on success)
- **`Err`** — Error type

## Compensation Logic

Compensation runs **after** the operation succeeds but **before** earlier compensations:

```
Execution:       Op1 ✓ → Op2 ✓ → Op3 ✗ (failure)
                  ↓      ↓       ↓
Compensation:   Op3.compensate ✓ → Op2.compensate ✓ → Op1.compensate ✓
                  ↓
Retry:          All operations execute again
```

Compensation receives both the input and the successful output, allowing context-aware rollback:

```scala
import golem.Transactions

val transferOp = Transactions.operation[String, Long, String](
  from => Right(1000L)  // Debit $1000
)(
  (from, amount) => {
    // Compensation: refund if later steps fail
    println(s"Refunding $amount to $from")
    Right(())
  }
)
```

## Error Handling Strategies

### Strategy 1: Retry Until Success (Infallible)

Use infallible transactions when transient failures are expected:

```scala
import golem.Transactions

val result = Transactions.infallibleTransaction { tx =>
  // Network call (might fail transiently)
  val callApi = Transactions.operation[Unit, String, String](
    _ => ??? // Try to call API
  )((_, _) => Right(())) // No compensation needed

  tx.execute(callApi, ())
}
```

The transaction retries automatically on failure.

### Strategy 2: Fast Fail with Compensation (Fallible)

Use fallible transactions when you want to fail fast but ensure cleanup:

```scala
import golem.Transactions
import scala.concurrent.Future

val result: Either[String, Int] = Transactions.fallibleTransaction { tx =>
  val allocateResource = Transactions.operation[Unit, String, String](
    _ => Right("resource-1")
  )((_, resource) => {
    println(s"Cleaning up $resource")
    Right(())
  })

  tx.execute(allocateResource, ()) match {
    case Left(err) => Left(err)
    case Right(resource) =>
      // Use resource
      if (shouldFail) Left("Operation failed")
      else Right(42)
  }
}
```

## Chaining Operations

Build multi-step workflows by threading operations together:

```scala
import golem.Transactions

val workflow = Transactions.infallibleTransaction { tx =>
  // Step 1: Initialize
  val init = Transactions.operation[Unit, Map[String, Int], String](
    _ => Right(Map.empty)
  )((_, _) => Right(()))
  val state = tx.execute(init, ())

  // Step 2: Update state
  val update = Transactions.operation[Map[String, Int], Map[String, Int], String](
    m => Right(m + ("key" -> 42))
  )((m, _) => Right(()))
  val newState = tx.execute(update, state)

  // Step 3: Finalize
  val finalize = Transactions.operation[Map[String, Int], String, String](
    m => Right(s"Final state: $m")
  )((_, _) => Right(()))
  tx.execute(finalize, newState)
}
```

## Atomicity Guarantees

Transactions provide these guarantees:

| Guarantee | Infallible | Fallible |
|-----------|-----------|----------|
| All-or-nothing | Yes (retries until all succeed) | Only if all operations succeed |
| Compensation order | Reverse (LIFO) | Reverse (LIFO) |
| State preservation | Oplog resets on retry | Oplog reflects compensation |
| Idempotence | Must be idempotent (retried) | Once per invocation |

**Idempotence requirement:** Operations in infallible transactions must be idempotent because they retry. Use request IDs or timestamps to detect duplicates.

## Integration with Guards

Transactions internally use `Guards.markAtomicOperation()` to establish atomic regions and manage oplog state:

```scala
// Behind the scenes, transactions call:
// val guard = Guards.markAtomicOperation()  // Start atomic region
// try {
//   ... execute operations and track compensations ...
// } finally {
//   guard.drop()  // End atomic region
// }
// On failure: compensation runs and then retries (infallible) or returns error (fallible)
```

You don't call `Guards` directly; transactions handle all the coordination automatically.

## Common Patterns

### Pattern 1: Saga with Multiple External Calls

```scala
import golem.Transactions

val saga = Transactions.infallibleTransaction { tx =>
  // Call service A
  val callA = Transactions.operation[Unit, String, String](
    _ => ??? // Try calling service A
  )((_, resultA) => {
    ??? // Undo service A if later steps fail
  })

  // Call service B (uses result from A)
  val callB = Transactions.operation[String, String, String](
    resultA => ??? // Call service B with result from A
  )((resultA, resultB) => {
    ??? // Undo service B
  })

  val a = tx.execute(callA, ())
  tx.execute(callB, a)
}
```

### Pattern 2: Cascading Updates

```scala
import golem.Transactions

val cascade = Transactions.fallibleTransaction { tx =>
  val update1 = Transactions.operation[String, String, String](
    key => Right(key + "-updated")
  )((_, _) => Right(()))

  val update2 = Transactions.operation[String, String, String](
    key => Right(key + "-processed")
  )((_, _) => Right(()))

  val k1 = tx.execute(update1, "record")
  tx.execute(update2, k1)
}
```

## Best Practices

- **Keep operations small** — Easier to reason about and compensate
- **Make operations idempotent** — Infallible transactions retry; ensure idempotence
- **Document compensations clearly** — Future maintainers need to understand rollback logic
- **Test failure paths** — Verify compensation works correctly
- **Use fallible for explicit errors** — Infallible for transient failures
- **Log compensation** — Help debugging in production
