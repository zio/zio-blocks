package scope.examples

import zio.blocks.scope._

/**
 * Transaction Boundary Example
 *
 * Demonstrates nested scopes and resource-returning methods for database
 * transaction management.
 *
 * Key patterns shown:
 *   - '''Resource-returning methods''': `beginTransaction` returns
 *     `Resource[DbTransaction]`
 *   - '''Nested scopes''': Transactions live in child scopes of the connection
 *   - '''Automatic cleanup''': Uncommitted transactions auto-rollback on scope
 *     exit
 *   - '''LIFO ordering''': Transaction closes before connection
 */
object TransactionBoundaryExample {

  /** Simulates a database connection that can create transactions. */
  class DbConnection(val id: String) extends AutoCloseable {
    println(s"  [DbConnection $id] Opened")

    /**
     * Begins a new transaction.
     *
     * Returns a `Resource[DbTransaction]` that must be allocated in a scope.
     * This ensures the transaction is always properly closed (with rollback if
     * not committed) when the scope exits.
     */
    def beginTransaction(txId: String): Resource[DbTransaction] =
      Resource.acquireRelease {
        new DbTransaction(this, txId)
      } { tx =>
        tx.close()
      }

    def close(): Unit =
      println(s"  [DbConnection $id] Closed")
  }

  /** Simulates an active database transaction. */
  class DbTransaction(val conn: DbConnection, val id: String) extends AutoCloseable {
    private var committed  = false
    private var rolledBack = false
    println(s"    [Tx $id] Started on connection ${conn.id}")

    def execute(sql: String): Int = {
      require(!committed && !rolledBack, s"Transaction $id already completed")
      println(s"    [Tx $id] Execute: $sql")
      sql.hashCode.abs % 100 + 1
    }

    def commit(): Unit = {
      require(!committed && !rolledBack, s"Transaction $id already completed")
      committed = true
      println(s"    [Tx $id] Committed")
    }

    def rollback(): Unit =
      if (!committed && !rolledBack) {
        rolledBack = true
        println(s"    [Tx $id] Rolled back")
      }

    def close(): Unit = {
      if (!committed && !rolledBack) {
        println(s"    [Tx $id] Auto-rollback (not committed)")
        rollback()
      }
      println(s"    [Tx $id] Closed")
    }
  }

  /** Result of transaction operations. */
  case class TxResult(success: Boolean, affectedRows: Int)

  @main def runTransactionBoundaryExample(): Unit = {
    println("=== Transaction Boundary Example ===\n")
    println("Demonstrating Resource-returning beginTransaction method\n")

    Scope.global.scoped { connScope =>
      // Allocate the connection in the outer scope
      val conn = connScope.allocate(Resource.fromAutoCloseable(new DbConnection("db-001")))
      println()

      // Transaction 1: Successful insert
      println("--- Transaction 1: Insert user ---")
      val result1 = connScope.scoped { txScope =>
        (connScope $ conn) { rawConn =>
          // beginTransaction returns Resource[DbTransaction] - must allocate it!
          val tx = txScope.allocate(rawConn.beginTransaction("tx-001"))
          (txScope $ tx) { t =>
            val rows = t.execute("INSERT INTO users VALUES (1, 'Alice')")
            t.commit()
            TxResult(success = true, affectedRows = rows)
          }
        }
      }
      println(s"  Result: $result1\n")

      // Transaction 2: Transfer funds (multiple operations)
      println("--- Transaction 2: Transfer funds ---")
      val result2 = connScope.scoped { txScope =>
        (connScope $ conn) { rawConn =>
          val tx = txScope.allocate(rawConn.beginTransaction("tx-002"))
          (txScope $ tx) { t =>
            val rows1 = t.execute("UPDATE accounts SET balance = balance - 100 WHERE id = 1")
            val rows2 = t.execute("UPDATE accounts SET balance = balance + 100 WHERE id = 2")
            t.commit()
            TxResult(success = true, affectedRows = rows1 + rows2)
          }
        }
      }
      println(s"  Result: $result2\n")

      // Transaction 3: Demonstrates auto-rollback on scope exit without commit
      println("--- Transaction 3: Auto-rollback (no explicit commit) ---")
      val result3 = connScope.scoped { txScope =>
        (connScope $ conn) { rawConn =>
          val tx = txScope.allocate(rawConn.beginTransaction("tx-003"))
          (txScope $ tx) { t =>
            t.execute("DELETE FROM audit_log")
            println("    [App] Not committing - scope exit will trigger auto-rollback...")
            // Returning without commit - the Resource's release will call close(),
            // which detects no commit and triggers rollback automatically
            TxResult(success = false, affectedRows = 0)
          }
        }
      }
      println(s"  Result: $result3\n")

      println("--- All transactions complete, connection still open ---")
      println("--- Exiting connection scope ---")
    }

    println("\n=== Example complete ===")
    println("\nKey insight: beginTransaction() returns Resource[DbTransaction],")
    println("forcing proper scoped allocation and automatic cleanup.")
  }
}
