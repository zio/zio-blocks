//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-data-migration:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using file Customers.scala
// Run from this folder:    scala-cli run Step5FailureAndRestartExample.scala
// Or from sql-examples:    sbt "runMain migratetablerowsonline.Step5FailureAndRestartExample"

package migratetablerowsonline

import java.nio.file.Files
import zio.blocks.data.migration.*
import zio.blocks.sql.*

/**
 * How to Migrate Table Rows to a New Version Online — Recover from a Failed
 * Batch
 *
 * Lets a single-word name fail a batch, shows that the batch rolled back and
 * its keys stayed queued, and finishes the migration with a fixed migration.
 */
object Step5FailureAndRestartExample {
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
          CustomerV1(2, "Cher", "cher@example.com"),
          CustomerV1(3, "Alan Turing", "alan@example.com")
        )
      )
    }
    AddNameColumns(transactor).migrate()
    QueueTable.create[Long]("customers_split_q", transactor)
    transactor.transact(QueueTable.enqueue("customers_split_q", customersV1.all.map(_.id)))

    val firstTry = SmallMigrator[CustomerV1, CustomerV2, Long, Long](
      repoV1 = customersV1,
      repoV2 = customersV2,
      migration = splitName,
      queueTable = "customers_split_q",
      batchSize = 2,
      target = TargetStrategy.InPlace
    )

    firstTry.init()
    try println(s"batch: ${firstTry.processBatch()} rows")
    catch { case e: RuntimeException => println(s"batch failed: ${e.getMessage}") }

    transactor.connect {
      val migrated =
        Frag.literal("SELECT COUNT(*) FROM customers WHERE first_name IS NOT NULL").queryOne[Long].getOrElse(0L)
      println(s"rows migrated: $migrated, keys still queued: ${QueueTable.pending("customers_split_q")}")
    }

    val secondTry = SmallMigrator[CustomerV1, CustomerV2, Long, Long](
      repoV1 = customersV1,
      repoV2 = customersV2,
      migration = splitNameSafely,
      queueTable = "customers_split_q",
      batchSize = 2,
      target = TargetStrategy.InPlace
    )

    secondTry.init()
    while (transactor.connect(QueueTable.pending("customers_split_q")) > 0)
      println(s"batch: ${secondTry.processBatch()} rows")
    secondTry.complete()

    transactor.connect(customersV2.all.foreach(println))

    Files.deleteIfExists(dbFile)
  }
}
