package smalljsonbackendfromblocks

import zio.blocks.sql._

/**
 * Build a Small JSON Backend from Blocks — the SQLite storage
 *
 * `Db` opens the database file and creates the `book` table. `BookRepo` reads
 * and writes books through a derived repository and fills an empty catalog with
 * three starter books.
 */
class Db(config: DbConfig) extends AutoCloseable {
  val transactor: JdbcTransactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:${config.file}", SqlDialect.SQLite)

  transactor.connect {
    sql"""CREATE TABLE IF NOT EXISTS book (
      id INTEGER PRIMARY KEY,
      title TEXT NOT NULL,
      first_author TEXT NOT NULL,
      year INTEGER NOT NULL,
      isbn TEXT,
      stock INTEGER NOT NULL,
      price_cents INTEGER NOT NULL
    )""".update
  }
  println("Db: opened")

  def close(): Unit = println("Db: closed")
}

class BookRepo(db: Db) {
  private val books = Repo.derived[Book, Long]

  db.transactor.transact {
    if (books.count == 0) books.insertBatch(BookRepo.starterCatalog)
  }

  def page(limit: Int, offset: Int): List[Book] =
    db.transactor.connect {
      sql"""SELECT id, title, first_author, year, isbn, stock, price_cents FROM book
            ORDER BY id LIMIT $limit OFFSET $offset""".query[Book]
    }

  def find(id: Long): Option[Book] =
    db.transactor.connect(books.find(id).toOption)

  def add(book: NewBook): Book =
    db.transactor.transact {
      val id = sql"SELECT COALESCE(MAX(id), 0) + 1 FROM book".queryOne[Long].getOrElse(1L)
      books.insertReturning(
        Book(id, book.title, book.firstAuthor, book.year, book.isbn, book.stock, book.priceCents)
      )
    }
}

object BookRepo {
  // Stock and prices are made up.
  val starterCatalog: List[Book] = List(
    Book(1, "Zionomicon", "John A. De Goes", 2024, None, 3, 4500),
    Book(2, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), 5, 3999),
    Book(3, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), 4, 4499)
  )
}
