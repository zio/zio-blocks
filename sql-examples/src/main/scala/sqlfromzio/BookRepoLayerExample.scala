package sqlfromzio

import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*

trait BookRepo {
  def all: Task[List[Book]]
  def find(id: Long): Task[Option[Book]]
  def add(book: Book): Task[Unit]
}

final case class BookRepoLive(transactor: TransactorZIO) extends BookRepo {
  def all: Task[List[Book]] =
    transactor.connect(books.all)

  def find(id: Long): Task[Option[Book]] =
    transactor.connect(books.find(id).toOption)

  def add(book: Book): Task[Unit] =
    transactor.transact(books.insert(book)).unit
}

object BookRepoLive {
  val layer: ZLayer[TransactorZIO, Nothing, BookRepo] =
    ZLayer.fromFunction(BookRepoLive(_))
}

/**
 * How to Use the sql Module from a ZIO Application — Provide the Database as a
 * ZLayer
 *
 * Provides a `TransactorZIO` over a temporary SQLite file as a layer, creates
 * the table, and reads the books through a `BookRepo` service.
 *
 * Run with: cd sql-examples && sbt "runMain sqlfromzio.BookRepoLayerExample"
 */
object BookRepoLayerExample extends ZIOAppDefault {

  val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
  dbFile.toFile.deleteOnExit()

  val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
    TransactorZIO.layer(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  val run =
    for {
      count <- ZIO
                 .serviceWithZIO[TransactorZIO] { transactor =>
                   transactor.connect {
                     createBookTable()
                     shelf.foreach(books.insert)
                     books.count
                   }
                 }
                 .provide(transactorLayer)
      _   <- ZIO.succeed(println(s"Seeded $count books"))
      all <- ZIO
               .serviceWithZIO[BookRepo] { repo =>
                 repo.add(Book(4L, "Scala with Cats", 2020, 2)) *> repo.all
               }
               .provide(BookRepoLive.layer, transactorLayer)
      _ <- ZIO.succeed(all.foreach(println))
    } yield ()
}
