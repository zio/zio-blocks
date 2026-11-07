package smalljsonbackendfromblocks

import java.nio.file.Files

/**
 * Build a Small JSON Backend from Blocks — a fresh database file
 *
 * Creates an empty temporary SQLite file, deleted when the JVM exits, so each
 * example run and each test starts from the starter catalog.
 */
object TempDb {
  def file(): String = {
    val path = Files.createTempFile("bookstore", ".db")
    path.toFile.deleteOnExit()
    path.toString
  }
}
