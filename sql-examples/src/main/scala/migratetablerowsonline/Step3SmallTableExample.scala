//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-data-migration:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Customers.scala
// Run from this folder:    scala-cli run Step3SmallTableExample.scala
// Or from sql-examples:    sbt "runMain migratetablerowsonline.Step3SmallTableExample"

package migratetablerowsonline

import java.nio.file.Files
import zio.blocks.data.migration.*
import zio.blocks.sql.*

/**
 * How to Migrate Table Rows to a New Version Online — Backfill a Small Table in
 * Place
 *
 * Adds the new columns to a SQLite table, backfills them with SmallMigrator
 * while the old code keeps writing, and drops the old column.
 */
object Step3SmallTableExample {
  def main(args: Array[String]): Unit = {
    val dbFile     = Files.createTempFile("customers", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
    given Transactor = transactor
    given Dialect    = Dialect.SQLite

    transactor.transact {
      createCustomersV1()
      customersV1.insertBatch(
        List(
          CustomerV1(1, "Ada Lovelace", "ada@example.com"),
          CustomerV1(2, "Alan Turing", "alan@example.com"),
          CustomerV1(3, "Grace Brewster Hopper", "grace@example.com")
        )
      )
    }

    AddNameColumns(transactor).migrate()

    QueueTable.create[Long]("customers_split_q", transactor)
    transactor.transact(QueueTable.enqueue("customers_split_q", customersV1.all.map(_.id)))

    val migrator = SmallMigrator[CustomerV1, CustomerV2, Long, Long](
      repoV1 = customersV1,
      repoV2 = customersV2,
      migration = splitName,
      queueTable = "customers_split_q",
      batchSize = 2,
      target = TargetStrategy.InPlace
    )

    migrator.init()
    println(s"first batch: ${migrator.processBatch()} rows")

    transactor.transact {
      customersV1.insert(CustomerV1(4, "Edsger Dijkstra", "edsger@example.com"))
      customersV1.update(CustomerV1(1, "Ada King", "ada@example.com"))
      QueueTable.enqueue("customers_split_q", List(4L, 1L))
    }

    while (transactor.connect(QueueTable.pending("customers_split_q")) > 0)
      println(s"next batch: ${migrator.processBatch()} rows")

    migrator.complete()

    transactor.connect {
      Frag
        .literal("SELECT id, full_name, first_name, last_name FROM customers ORDER BY id")
        .query[(Long, String, String, String)]
        .foreach(println)
    }

    class DropFullName(transactor: Transactor) extends TinyMigrator(transactor) {
      def run()(using DbTx): Unit =
        Frag.literal("ALTER TABLE customers DROP COLUMN full_name").update
    }

    DropFullName(transactor).migrate()

    transactor.connect(customersV2.all.foreach(println))

    Files.deleteIfExists(dbFile)
  }
}
