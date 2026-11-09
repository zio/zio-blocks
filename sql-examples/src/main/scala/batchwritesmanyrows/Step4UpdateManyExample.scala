package batchwritesmanyrows

import zio.blocks.sql.*
import batchwritesmanyrows.Readings.*

/**
 * How to Insert and Update Many Rows in Batches — Updating Many Rows
 *
 * Flags every reading above 18.5 °C with one set-based UPDATE, recalibrates a
 * batch of readings with a loop of Repo#update, and then does the same
 * recalibration with Repo#insertOrUpdateBatch, which sends it as one JDBC
 * batch.
 *
 * Run with: cd sql-examples && sbt "runMain
 * batchwritesmanyrows.Step4UpdateManyExample"
 */
object Step4UpdateManyExample {

  def main(args: Array[String]): Unit = {
    val transactor = newTransactor()
    transactor.connect(createTable())

    transactor.transact(repo.insertBatch(sample(1L, 12)))

    transactor.transact {
      val threshold = 18.5
      val flagged   = sql"UPDATE reading SET flagged = 1 WHERE celsius > $threshold".update
      println(s"set-based UPDATE flagged $flagged readings")
    }

    val recalibrated = transactor.connect(repo.all.take(4)).map(r => r.copy(celsius = r.celsius + 0.5))

    transactor.transact {
      val counts = recalibrated.map(repo.update)
      println(s"loop of update:          ${counts.sum} rows affected")
    }

    val recalibratedAgain = recalibrated.map(r => r.copy(celsius = r.celsius + 0.5)) :+
      Reading(99L, 1L, 21.0, flagged = false)

    transactor.transact {
      val affected = repo.insertOrUpdateBatch(recalibratedAgain)
      println(s"insertOrUpdateBatch:     $affected rows affected")
    }

    transactor.connect {
      println(s"readings in table: ${repo.count}")
      repo.all.take(5).foreach(println)
    }
  }
}
