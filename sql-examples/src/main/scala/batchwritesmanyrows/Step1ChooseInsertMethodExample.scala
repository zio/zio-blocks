package batchwritesmanyrows

import zio.blocks.sql.*
import batchwritesmanyrows.Readings.*

/**
 * How to Insert and Update Many Rows in Batches — Choosing an Insert Method
 *
 * Inserts the same five readings with a loop of insert, with insertBatch and
 * with insertAll, and prints what each returns.
 *
 * Run with: cd sql-examples && sbt "runMain
 * batchwritesmanyrows.Step1ChooseInsertMethodExample"
 */
object Step1ChooseInsertMethodExample {

  def main(args: Array[String]): Unit = {
    val transactor = newTransactor()
    transactor.connect(createTable())

    val rows = sample(1L, 5)

    transactor.transact {
      val counts = rows.map(repo.insert)
      println(s"loop of insert:  ${counts.sum} rows affected, ${repo.count} in table")
      repo.clear()
    }

    transactor.transact {
      val affected = repo.insertBatch(rows)
      println(s"insertBatch:     $affected rows affected, ${repo.count} in table")
      repo.clear()
    }

    transactor.transact {
      val ids = repo.insertAll(rows)
      println(s"insertAll:       returned ids ${ids.mkString("[", ", ", "]")}, ${repo.count} in table")
    }
  }
}
