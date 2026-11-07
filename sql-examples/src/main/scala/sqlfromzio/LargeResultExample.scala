package sqlfromzio

import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*

/**
 * How to Use the sql Module from a ZIO Application — Read a Large Result Set
 *
 * Folds a chunked query inside `TransactorZIO#connect` and turns the `Either`
 * of the fold into a ZIO failure with `absolve`.
 *
 * Run with: cd sql-examples && sbt "runMain sqlfromzio.LargeResultExample"
 */
object LargeResultExample extends ZIOAppDefault {

  val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
  dbFile.toFile.deleteOnExit()

  val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
    TransactorZIO.layer(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  val run =
    (for {
      _ <- ZIO.serviceWithZIO[TransactorZIO](_.connect {
             createBookTable()
             shelf.foreach(books.insert)
           })
      total <- ZIO.serviceWithZIO[TransactorZIO] { transactor =>
                 transactor.connect {
                   sql"SELECT id, title, year, stock FROM book"
                     .queryChunked[Book](2)
                     .runFold(0)((total, chunk) => total + chunk.foldLeft(0)(_ + _.stock))
                 }.absolve
               }
      _ <- ZIO.succeed(println(s"Total stock on the shelf: $total"))
    } yield ()).provide(transactorLayer)
}
