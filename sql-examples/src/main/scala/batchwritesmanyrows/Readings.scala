package batchwritesmanyrows

import java.nio.file.Files
import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * Shared model for How to Insert and Update Many Rows in Batches: a table of
 * sensor readings, a repository over it, and helpers to make sample rows and a
 * fresh SQLite file.
 */
object Readings {

  case class Reading(id: Long, sensorId: Long, celsius: Double, flagged: Boolean)

  object Reading {
    given Schema[Reading] = Schema.derived
  }

  val repo = Repo.derived[Reading, Long]

  /** Readings with IDs from `from` to `from + n - 1`, all unflagged. */
  def sample(from: Long, n: Int): List[Reading] =
    (0 until n).toList.map { i =>
      val id = from + i
      Reading(id, id % 4 + 1, 18.0 + (id % 150) / 10.0, flagged = false)
    }

  /**
   * The table, with the primary key declared; see the known bug note in the
   * guide.
   */
  def createTable()(using DbCon): Unit =
    sql"""CREATE TABLE reading (
      id        INTEGER PRIMARY KEY,
      sensor_id INTEGER NOT NULL,
      celsius   REAL NOT NULL,
      flagged   INTEGER NOT NULL
    )""".update

  /** A new SQLite file, deleted when the JVM exits. */
  def newTransactor(): JdbcTransactor = {
    val dbFile = Files.createTempFile("sensors", ".db")
    dbFile.toFile.deleteOnExit()
    JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
  }
}
