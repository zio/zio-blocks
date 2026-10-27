//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-data-migration:0.0.56
//> using dep org.postgresql:postgresql:42.7.14
//> using file Customers.scala
//> using file ShopDatabase.scala
// Needs PostgreSQL 14 or newer, see the guide's Prerequisites for a Docker command.
// Run from this folder:    scala-cli run CompleteExample.scala
// Or from sql-examples:    sbt "runMain migratetablerowsonline.CompleteExample"

package migratetablerowsonline

import zio.blocks.data.migration.*
import zio.blocks.sql.*

/**
 * How to Migrate Table Rows to a New Version Online — Complete Example
 *
 * Splits the full name of 20,000 PostgreSQL customers into first and last
 * names with LargeMigrator while another thread keeps writing, using the
 * migration that also handles single-word names.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val transactor = shopTransactor()
    given Transactor = transactor
    given Dialect    = Dialect.Postgres

    transactor.transact {
      resetShop()
      createCustomersV1()
      customersV1.insertBatch((1L to 20000L).map(sampleCustomer))
    }

    transactor.transact {
      Frag
        .literal(
          "CREATE TABLE customers_v2 (id BIGINT PRIMARY KEY, first_name TEXT NOT NULL, last_name TEXT NOT NULL, email TEXT NOT NULL)"
        )
        .update
    }
    QueueTable.create[Long]("customers_split_q", transactor)

    val migrator = LargeMigrator[CustomerV1, CustomerV2, Long, Long](
      repoV1 = customersV1,
      repoV2 = customersV2,
      migration = splitNameSafely,
      queueTable = "customers_split_q",
      batchSize = 500,
      target = TargetStrategy.ShadowTable("v2"),
      captureTriggers = true
    )

    migrator.init()

    val queued = transactor.transact {
      Frag
        .literal("INSERT INTO customers_split_q (id) SELECT CAST(id AS TEXT) FROM customers ON CONFLICT DO NOTHING")
        .update
    }
    println(s"queued $queued existing customers")

    val writer = new Thread(() =>
      for (i <- 1L to 100L) {
        transactor.transact {
          customersV1.insert(sampleCustomer(20000L + i))
          customersV1.update(sampleCustomer(i).copy(email = s"customer$i@new-domain.example"))
        }
        Thread.sleep(5)
      }
    )
    writer.start()

    val copied = migrator.run()
    println(s"first pass copied $copied rows")

    writer.join()
    println(s"writer stopped with ${migrator.pendingCount} keys still queued")

    migrator.fence()
    transactor.transact(customersV1.insert(CustomerV1(30001L, "Frances Allen", "frances@example.com")))

    val drained = migrator.drain()
    println(s"drain() applied $drained more rows")

    require(migrator.pendingCount == 0, "a write reached the queue after drain(); stop all writers first")
    migrator.complete()

    transactor.connect {
      val oldCount = Frag.literal("SELECT COUNT(*) FROM customers_old_v2").queryOne[Long].getOrElse(0L)
      println(s"customers: ${customersV2.count} rows, customers_old_v2: $oldCount rows")
      println(s"updated during the copy: ${customersV2.find(1L)}")
      println(s"inserted during the copy: ${customersV2.find(20001L)}")
      println(s"inserted after fence(): ${customersV2.find(30001L)}")
    }

    try transactor.transact(customersV1.insert(CustomerV1(30002L, "John Backus", "john@example.com")))
    catch { case e: java.sql.SQLException => println(s"old code after the swap: ${e.getMessage.linesIterator.next()}") }

    transactor.transact {
      Frag.literal("DROP TABLE customers_old_v2").update
      Frag.literal("DROP TABLE customers_split_q").update
      Frag.literal("DROP FUNCTION customers_split_q_notify()").update
    }
  }
}
