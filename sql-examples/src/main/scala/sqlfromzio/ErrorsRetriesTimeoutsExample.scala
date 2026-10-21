package sqlfromzio

import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*
import org.sqlite.{SQLiteErrorCode, SQLiteException}

final case class DuplicateBook(id: Long) extends Exception(s"Book $id already exists")

/**
 * How to Use the sql Module from a ZIO Application — Handle Errors, Retries and
 * Timeouts the ZIO Way
 *
 * Maps a primary key violation to a domain error, retries a connection that
 * fails twice, compares `timeout` with `disconnect.timeout` on a slow query,
 * and uses `timeoutFail` inside `transactZIO` so a hanging payment rolls back.
 *
 * Run with: cd sql-examples && sbt "runMain
 * sqlfromzio.ErrorsRetriesTimeoutsExample"
 */
object ErrorsRetriesTimeoutsExample extends ZIOAppDefault {

  val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
  dbFile.toFile.deleteOnExit()

  val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
    TransactorZIO.layer(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  def addNew(book: Book): ZIO[BookRepo, Throwable, Unit] =
    ZIO.serviceWithZIO[BookRepo](_.add(book)).catchSome {
      case e: SQLiteException if e.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY =>
        ZIO.fail(DuplicateBook(book.id))
    }

  val attempts = new java.util.concurrent.atomic.AtomicInteger(0)

  val startingUp = new TransactorZIO(
    () =>
      if (attempts.incrementAndGet() <= 2) throw new java.sql.SQLException("Connection refused")
      else java.sql.DriverManager.getConnection(s"jdbc:sqlite:$dbFile"),
    SqlDialect.SQLite
  )

  val slowCount =
    sql"WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n WHERE x < 3000000) SELECT count(*) FROM n"

  def timed[A](effect: Task[A]): Task[(A, Duration)] =
    effect.timed.map(_.swap)

  val hangingPayments: ZLayer[Any, Nothing, Payments] =
    ZLayer.succeed(new Payments {
      def charge(customer: String, copies: Int): IO[PaymentDeclined, Unit] = ZIO.never
    })

  def sellWithin(bookId: Long, copies: Int, customer: String): ZIO[TransactorZIO & Payments, Throwable, Unit] =
    ZIO.serviceWithZIO[TransactorZIO] { transactor =>
      transactor.transactZIO {
        for {
          _ <- ZIO.attemptBlocking(sql"UPDATE book SET stock = stock - $copies WHERE id = $bookId".update)
          _ <- ZIO
                 .serviceWithZIO[Payments](_.charge(customer, copies))
                 .timeoutFail(new java.util.concurrent.TimeoutException("Payment took too long"))(50.millis)
        } yield ()
      }
    }

  val run =
    for {
      _ <- ZIO
             .serviceWithZIO[TransactorZIO](_.connect {
               createBookTable()
               shelf.foreach(books.insert)
             })
             .provide(transactorLayer)
      duplicate <- addNew(Book(1L, "Zionomicon", 2024, 1)).either.provide(BookRepoLive.layer, transactorLayer)
      _         <- ZIO.succeed(println(s"Adding Zionomicon again: $duplicate"))
      _         <- startingUp
             .connect(books.count)
             .retry(Schedule.exponential(10.millis) && Schedule.recurs(5))
             .map(count => println(s"Counted $count books after ${attempts.get} attempts"))
      _ <- ZIO
             .serviceWithZIO[TransactorZIO] { transactor =>
               timed(transactor.connect(slowCount.queryOne[Long]).timeout(10.millis)).map { case (result, elapsed) =>
                 println(s"timeout: result $result")
                 println(s"Waited longer than the timeout: ${elapsed.toMillis > 100}")
               }
             }
             .provide(transactorLayer)
      _ <- ZIO
             .serviceWithZIO[TransactorZIO] { transactor =>
               timed(transactor.connect(slowCount.queryOne[Long]).disconnect.timeout(10.millis)).map {
                 case (result, elapsed) =>
                   println(s"disconnect.timeout: result $result")
                   println(s"Returned before the query finished: ${elapsed.toMillis < 100}")
               }
             }
             .provide(transactorLayer)
      before <- stockOf(2L).provide(transactorLayer)
      result <- sellWithin(2L, 1, "bob").either.provide(transactorLayer, hangingPayments)
      after  <- stockOf(2L).provide(transactorLayer)
      _      <- ZIO.succeed {
             println(s"Sale: $result")
             println(s"Stock before: $before, after: $after")
           }
    } yield ()
}
