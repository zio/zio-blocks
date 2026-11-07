package sqlfromzio

import zio.*
import zio.test.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*
import java.nio.file.Files

object BookRepoSpec extends ZIOSpecDefault {

  val testTransactorLayer: ZLayer[Any, Throwable, TransactorZIO] =
    ZLayer.scoped {
      for {
        file      <- ZIO.acquireRelease(ZIO.attemptBlocking(Files.createTempFile("bookstore-test", ".db")))(file =>
                       ZIO.attemptBlocking(Files.deleteIfExists(file)).orDie
                     )
        transactor = TransactorZIO.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)
        _         <- transactor.connect {
                       createBookTable()
                       shelf.foreach(books.insert)
                     }
      } yield transactor
    }

  def spec: Spec[Any, Throwable] =
    suite("BookRepoSpec")(
      test("starts with the three seeded books") {
        for {
          repo <- ZIO.service[BookRepo]
          all  <- repo.all
        } yield assertTrue(all == shelf)
      },
      test("first test adds a book and sees four books") {
        for {
          repo <- ZIO.service[BookRepo]
          _    <- repo.add(Book(10L, "Scala with Cats", 2020, 2))
          all  <- repo.all
        } yield assertTrue(all.size == 4)
      },
      test("second test adds the same ID and still sees four books") {
        for {
          repo <- ZIO.service[BookRepo]
          _    <- repo.add(Book(10L, "Scala with Cats", 2020, 2))
          all  <- repo.all
        } yield assertTrue(all.size == 4)
      },
      test("find returns a seeded book") {
        for {
          repo  <- ZIO.service[BookRepo]
          found <- repo.find(1L)
        } yield assertTrue(found.map(_.title) == Some("Zionomicon"))
      }
    ).provide(BookRepoLive.layer, testTransactorLayer)
}
