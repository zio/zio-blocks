package zio.blocks.sql

/**
 * Entry point for executing SQL against a database.
 *
 * Both methods acquire a connection, execute the provided body, and close the
 * connection upon return (whether the body succeeds or throws).
 */
trait Transactor {

  /** Opens a connection, executes `f`, and closes the connection on return. */
  def connect[A](f: DbCon ?=> A): A

  /**
   * Opens a connection, disables auto-commit, executes `f`, commits on success,
   * and rolls back on exception. The connection is closed after commit or
   * rollback.
   */
  def transact[A](f: DbTx ?=> A): A
}
