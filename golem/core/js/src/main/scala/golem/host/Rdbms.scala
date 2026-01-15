package golem.host

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facades for the built-in Golem RDBMS host packages.
 *
 * This is exposed as raw host modules to keep the Scala SDK surface small while
 * providing parity with Rust/TS.
 */
object Rdbms {

  @js.native
  @JSImport("golem:rdbms/postgres@0.0.1", JSImport.Namespace)
  private object PostgresModule extends js.Object

  @js.native
  @JSImport("golem:rdbms/mysql@0.0.1", JSImport.Namespace)
  private object MysqlModule extends js.Object

  @js.native
  @JSImport("golem:rdbms/types@0.0.1", JSImport.Namespace)
  private object TypesModule extends js.Object

  def postgresRaw: Any = PostgresModule
  def mysqlRaw: Any    = MysqlModule
  def typesRaw: Any    = TypesModule
}
