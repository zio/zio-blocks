package migratefrommagnumordoobie

import zio.blocks.maybe.Maybe
import zio.blocks.schema.Schema
import zio.blocks.sql.query.SqlQuery
import zio.blocks.sql.*
import scala.util.{Failure, Success, Try}

/**
 * Coming from Magnum or doobie — Complete Example
 *
 * The bookshelf after a migration from Magnum or doobie: a derived Repo, a
 * custom column type, a SqlQuery in place of a Magnum Spec, a dynamic search in
 * place of doobie's Fragments, a chunked stream and a transaction that rolls
 * back.
 *
 * Run with: cd sql-examples && sbt "runMain
 * migratefrommagnumordoobie.CompleteExample"
 */
object CompleteExample extends App {

  case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

  object Book {
    given Schema[Book] = Schema.derived
  }

  // Replaces Magnum's DbCodec#biMap and doobie's Meta#timap
  case class Isbn(value: String)

  given DbCodec[Isbn] = DbCodec[String].transform(Isbn(_))(_.value)

  val dbFile = java.nio.file.Files.createTempFile("bookshelf", ".db")
  dbFile.toFile.deleteOnExit()

  // Replaces Magnum's Transactor(dataSource) and doobie's Transactor.fromDriverManager[IO]
  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  val bookTable = Table.derived[Book]

  // Replaces Magnum's Repo[Book, Book, Long]
  val books = Repo.derived[Book, Long]

  // Replaces doobie's ConnectionIO[List[Book]]
  def booksBefore(year: Int)(using DbCon): List[Book] =
    sql"SELECT id, title, author, year, isbn FROM book WHERE year < $year".query[Book]

  // Replaces doobie's Fragments.whereAndOpt
  def search(author: Option[String], before: Option[Int]): Frag = {
    val conditions = author.map(a => sql"author = $a").toList ++ before.map(y => sql"year < $y").toList
    val where      =
      if (conditions.isEmpty) Frag.empty
      else sql" WHERE " ++ conditions.reduce((left, right) => left ++ sql" AND " ++ right)
    sql"SELECT id, title, author, year, isbn FROM book" ++ where ++ sql" ORDER BY year"
  }

  transactor.transact {
    bookTable.createTable(SqlDialect.SQLite).update
    books.insertBatch(
      List(
        Book(1L, "Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, None),
        Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601")),
        Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"))
      )
    )
  }

  transactor.connect {
    println(s"Books on the shelf: ${books.count}")

    // Magnum's findById returned Option; Repo#find returns Maybe
    val found: Maybe[Book] = books.find(1L)
    println(found.fold("No book with id 1")(book => s"Found: ${book.title}"))

    println("Published before 2010:")
    booksBefore(2010).foreach(book => println(s"  ${book.title}"))

    // Replaces Magnum's Spec[Book].where(...).orderBy(...).limit(...)
    val oldest = SqlQuery.from(bookTable).orderBy("year").limit(2)
    println("Two oldest books:")
    oldest.toFrag(SqlDialect.SQLite).query[Book].foreach(book => println(s"  ${book.title}"))

    println("By Martin Odersky:")
    search(Some("Martin Odersky"), None).query[Book].foreach(book => println(s"  ${book.title}"))

    val isbns = sql"SELECT isbn FROM book WHERE isbn IS NOT NULL ORDER BY id".query[Isbn]
    println(s"Known ISBNs: ${isbns.map(_.value).mkString(", ")}")

    // Replaces doobie's .stream and Magnum's .iterator(); consumed inside connect
    val streamed =
      sql"SELECT id, title, author, year, isbn FROM book ORDER BY id"
        .queryChunked[Book](2)
        .runFold(0)((count, chunk) => count + chunk.size)
    println(s"Streamed rows: $streamed")
  }

  // Replaces doobie's program.transact(xa) and Magnum's transact(xa)
  Try {
    transactor.transact {
      books.insert(Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None))
      if (books.count > 3) throw new IllegalStateException("The shelf holds at most 3 books")
    }
  } match {
    case Failure(error) => println(s"Transaction rolled back: ${error.getMessage}")
    case Success(_)     => println("Transaction committed")
  }

  transactor.connect {
    println(s"Books after the rollback: ${books.count}")
  }
}
