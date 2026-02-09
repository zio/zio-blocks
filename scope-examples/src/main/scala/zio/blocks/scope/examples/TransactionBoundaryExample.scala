package zio.blocks.scope.examples

import zio.blocks.scope._

/**
 * Transaction Boundary Example
 *
 * Demonstrates nested scopes for database transaction management. The parent
 * scope holds a long-lived database connection, while child scopes create
 * short-lived transactions that must complete (commit or rollback) before
 * returning to the parent.
 *
 * Key concepts demonstrated:
 *   - '''Nested scopes''': `scope.scoped { child => ... }` creates child scopes
 *   - '''Scope hierarchy''': Child scope resources are cleaned up before parent
 *     resources
 *   - '''LIFO cleanup''': Transaction closes before connection (inner before
 *     outer)
 */
object TransactionBoundaryExample {

  /** Simulates a database connection. */
  class DbConnection(val id: String) extends AutoCloseable {
    println(s"  [DbConnection $id] Opened")

    def beginTransaction(txId: String): DbTransaction =
      new DbTransaction(this, txId)

    def close(): Unit =
      println(s"  [DbConnection $id] Closed")
  }

  /** Simulates an active database transaction. */
  class DbTransaction(val conn: DbConnection, val id: String) extends AutoCloseable {
    private var committed  = false
    private var rolledBack = false
    println(s"    [DbTransaction $id] Started on connection ${conn.id}")

    def execute(sql: String): Int = {
      println(s"    [DbTransaction $id] Execute: $sql")
      sql.hashCode.abs % 100 + 1
    }

    def commit(): Unit = {
      committed = true
      println(s"    [DbTransaction $id] Committed")
    }

    def rollback(): Unit = {
      rolledBack = true
      println(s"    [DbTransaction $id] Rolled back")
    }

    def close(): Unit = {
      if !committed && !rolledBack then rollback()
      println(s"    [DbTransaction $id] Closed")
    }
  }

  /** Result of transaction operations. */
  case class TxResult(success: Boolean, affectedRows: Int)

  @main def runTransactionBoundaryExample(): Unit = {
    println("=== Transaction Boundary Example ===\n")

    Scope.global.scoped { connScope =>
      // Allocate the connection in the outer scope
      val conn = connScope.allocate(Resource.fromAutoCloseable(new DbConnection("db-001")))
      println()

      // First transaction in a nested (child) scope
      println("--- Transaction 1: Insert user ---")
      val result1 = connScope.scoped { txScope =>
        // Get the raw connection to create a transaction
        val rawConn = @@.unscoped(conn)
        val tx      = txScope.allocate(Resource.fromAutoCloseable(rawConn.beginTransaction("tx-001")))

        val rows = txScope.$(tx)(_.execute("INSERT INTO users VALUES (1, 'Alice')"))
        txScope.$(tx)(_.commit())

        TxResult(success = true, affectedRows = rows)
      }
      println(s"  Result: $result1\n")

      // Second transaction in another nested scope
      println("--- Transaction 2: Transfer funds ---")
      val result2 = connScope.scoped { txScope =>
        val rawConn = @@.unscoped(conn)
        val tx      = txScope.allocate(Resource.fromAutoCloseable(rawConn.beginTransaction("tx-002")))

        val rows1 = txScope.$(tx)(_.execute("UPDATE accounts SET balance = balance - 100 WHERE id = 1"))
        val rows2 = txScope.$(tx)(_.execute("UPDATE accounts SET balance = balance + 100 WHERE id = 2"))
        txScope.$(tx)(_.commit())

        TxResult(success = true, affectedRows = rows1 + rows2)
      }
      println(s"  Result: $result2\n")

      // Third transaction that fails and rolls back
      println("--- Transaction 3: Failing operation (rollback) ---")
      val result3 = connScope.scoped { txScope =>
        val rawConn = @@.unscoped(conn)
        val tx      = txScope.allocate(Resource.fromAutoCloseable(rawConn.beginTransaction("tx-003")))

        txScope.$(tx)(_.execute("DELETE FROM audit_log"))
        // Simulate failure - don't commit, let the scope close trigger rollback
        println("    [Application] Simulating failure - not committing...")

        TxResult(success = false, affectedRows = 0)
      }
      println(s"  Result: $result3\n")

      println("--- All transactions complete, connection still open ---")
      println("--- Exiting connection scope ---")
    }

    println("\n=== Example complete ===")
  }
}
