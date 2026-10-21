package writejoinsandinspectgeneratedsql

import zio.blocks.sql.*
import zio.blocks.sql.query.*

object LibraryQueries {
  inline def booksWithFirstAuthors: SqlQuery[Book] =
    SqlQuery.from(Library.books).join(Rel.manyToOne(Library.books, _.firstAuthorId, Library.authors, _.authorId))
}

/**
 * How to Write Joins and Inspect the Generated SQL: Dump the SQL to Files at
 * Compile Time
 *
 * `Dump.dumpQuery` writes the query's SQL to a `.sql` file while this file
 * compiles, when the compiler's JVM has the `zib.sql.dumpDir` system property
 * set. `main` prints the files it produced.
 *
 * Run with: cd sql-examples && sbt -Dzib.sql.dumpDir=target/sql-dumps clean
 * compile "runMain writejoinsandinspectgeneratedsql.JoinsDumpExample"
 */
object JoinsDumpExample {
  // The val's name becomes the file name
  val booksWithFirstAuthors = Dump.dumpQuery(LibraryQueries.booksWithFirstAuthors)

  def main(args: Array[String]): Unit = {
    val dir   = java.nio.file.Paths.get("target/sql-dumps")
    val files =
      if (java.nio.file.Files.isDirectory(dir)) {
        val stream = java.nio.file.Files.list(dir)
        try
          stream.toArray.toList
            .map(_.asInstanceOf[java.nio.file.Path])
            .filter(p =>
              p.getFileName.toString.startsWith("booksWithFirstAuthors") && p.getFileName.toString.endsWith(".sql")
            )
            .sortBy(_.getFileName.toString)
        finally stream.close()
      } else Nil

    if (files.isEmpty)
      println("No dump files found. Recompile with: sbt -Dzib.sql.dumpDir=target/sql-dumps clean compile")
    else
      files.foreach { file =>
        println(s"== ${file.getFileName}")
        println(new String(java.nio.file.Files.readAllBytes(file), "UTF-8").trim)
      }
  }
}
