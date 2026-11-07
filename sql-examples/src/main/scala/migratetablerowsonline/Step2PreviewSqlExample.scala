//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-data-migration:0.0.56
//> using dep org.postgresql:postgresql:42.7.14
//> using file Customers.scala
//> using file ShopDatabase.scala
// Run from this folder:    scala-cli run Step2PreviewSqlExample.scala
// Or from sql-examples:    sbt "runMain migratetablerowsonline.Step2PreviewSqlExample"

package migratetablerowsonline

import zio.blocks.data.migration.*
import zio.blocks.sql.*

/**
 * How to Migrate Table Rows to a New Version Online — Preview the SQL Before
 * Running Anything
 *
 * Prints the statements an in-place SQLite migration and a shadow-table
 * PostgreSQL migration would run, without opening a connection.
 */
object Step2PreviewSqlExample {
  def main(args: Array[String]): Unit = {
    val inPlace = {
      given Transactor = JdbcTransactor.fromUrl("jdbc:sqlite:customers.db", SqlDialect.SQLite)
      given Dialect    = Dialect.SQLite
      SmallMigrator[CustomerV1, CustomerV2, Long, Long](
        repoV1 = customersV1,
        repoV2 = customersV2,
        migration = splitName,
        queueTable = "customers_split_q",
        batchSize = 500,
        target = TargetStrategy.InPlace
      )
    }

    inPlace.previewSql().foreach(println)

    val shadow = {
      given Transactor = shopTransactor()
      given Dialect    = Dialect.Postgres
      LargeMigrator[CustomerV1, CustomerV2, Long, Long](
        repoV1 = customersV1,
        repoV2 = customersV2,
        migration = splitName,
        queueTable = "customers_split_q",
        batchSize = 500,
        target = TargetStrategy.ShadowTable("v2"),
        captureTriggers = true
      )
    }

    shadow.previewSql().foreach(println)
  }
}
