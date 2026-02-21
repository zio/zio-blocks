package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "database-demo")
@description("Demonstrates typed Postgres and MySQL queries with fully typed parameters and results.")
trait DatabaseDemo extends BaseAgent[String] {

  @description("Run typed Postgres queries with PostgresDbValue params and result reading.")
  def postgresDemo(): Future[String]

  @description("Run typed MySQL queries with MysqlDbValue params and result reading.")
  def mysqlDemo(): Future[String]

  @description("Construct representative values from every major RDBMS type category.")
  def typeShowcase(): Future[String]
}

object DatabaseDemo extends AgentCompanion[DatabaseDemo, String]
