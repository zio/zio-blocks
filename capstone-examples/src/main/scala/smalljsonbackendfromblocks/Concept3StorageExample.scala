package smalljsonbackendfromblocks

/**
 * Build a Small JSON Backend from Blocks — Store Books in SQLite
 *
 * Opens a fresh database file, lets `BookRepo` fill the starter catalog, and
 * reads a page, one book, and a book that was just added.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept3StorageExample"
 */
object Concept3StorageExample {
  def main(args: Array[String]): Unit = {
    val db   = new Db(DbConfig(Demo.tempDbFile()))
    val repo = new BookRepo(db)

    repo.page(Paging(limit = 2, offset = 0)).foreach(println)
    println(repo.find(3))
    println(repo.find(42))
    println(repo.add(NewBook("Scala with Cats", "Noel Welsh", 2017, None, 2, 3500)))

    db.close()
  }
}
