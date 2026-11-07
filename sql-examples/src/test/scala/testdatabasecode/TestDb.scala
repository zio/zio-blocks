package testdatabasecode

import java.nio.file.Files
import zio.blocks.sql.*

/**
 * How to Test Database Code Against SQLite — the test helpers
 *
 * `withTempDb` gives each caller its own SQLite file and deletes it afterwards.
 * `withTestDb` adds the schema and the fixture books on top. `rollingBack`
 * runs a block in a transaction that is always rolled back.
 */
object TestDb {

  val zionomicon         = Book(1L, "Zionomicon", "John A. De Goes", 2024, None, 5)
  val programmingInScala = Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), 3)
  val fpInScala          = Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), 0)

  val fixtures: List[Book] = List(zionomicon, programmingInScala, fpInScala)

  def withTempDb[A](f: JdbcTransactor => A): A = {
    val file = Files.createTempFile("bookshop-test", ".db")
    try f(JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite))
    finally Files.deleteIfExists(file)
  }

  def createSchema()(using DbCon): Unit =
    sql"""CREATE TABLE book (
      id INTEGER PRIMARY KEY,
      title TEXT NOT NULL,
      author TEXT NOT NULL,
      year INTEGER NOT NULL,
      isbn TEXT,
      stock INTEGER NOT NULL
    )""".update

  def resetFixtures()(using DbCon): Unit = {
    Bookshop.books.clear()
    Bookshop.books.insertBatch(fixtures)
  }

  def withTestDb[A](f: JdbcTransactor => A): A =
    withTempDb { transactor =>
      transactor.transact {
        createSchema()
        resetFixtures()
      }
      f(transactor)
    }

  private final class Rollback(val result: Any) extends RuntimeException(null, null, false, false)

  def rollingBack[A](transactor: JdbcTransactor)(f: DbTx ?=> A): A =
    try transactor.transact(throw new Rollback(f))
    catch { case rollback: Rollback => rollback.result.asInstanceOf[A] }
}
