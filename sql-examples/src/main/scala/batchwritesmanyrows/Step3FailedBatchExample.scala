package batchwritesmanyrows

import scala.util.Try
import zio.blocks.sql.*
import batchwritesmanyrows.Readings.*

/**
 * How to Insert and Update Many Rows in Batches — When One Row Fails
 *
 * Runs insertBatch over five readings whose third row repeats an existing ID.
 * Outside a transaction, the rows before the failure may stay written; inside
 * transact, the whole batch is rolled back.
 *
 * Run with: cd sql-examples && sbt "runMain
 * batchwritesmanyrows.Step3FailedBatchExample"
 */
object Step3FailedBatchExample {

  def main(args: Array[String]): Unit = {
    val transactor = newTransactor()
    transactor.connect {
      createTable()
      repo.insert(sample(3L, 1).head)
    }

    val batch = sample(1L, 5) // IDs 1 to 5; ID 3 already exists

    val outside = Try(transactor.connect(repo.insertBatch(batch)))
    println(s"without transact: ${outside.failed.map(_.getMessage).getOrElse("no error")}")
    transactor.connect(println(s"  readings in table: ${repo.count}"))

    transactor.connect(repo.clear())
    transactor.connect(repo.insert(sample(3L, 1).head))

    val inside = Try(transactor.transact(repo.insertBatch(batch)))
    println(s"with transact:    ${inside.failed.map(_.getMessage).getOrElse("no error")}")
    transactor.connect(println(s"  readings in table: ${repo.count}"))
  }
}
