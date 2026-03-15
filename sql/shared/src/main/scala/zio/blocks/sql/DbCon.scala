package zio.blocks.sql

trait DbCon {
  def connection: DbConnection
  def dialect: SqlDialect
  def logger: SqlLogger
}
