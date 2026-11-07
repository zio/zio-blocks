/*
 * Small helpers shared by the examples that run on real SQLite files.
 *
 * The files go in the `.projection-examples` folder of the working directory,
 * which git ignores. The one exception is `global/`, because the engine always
 * keeps the store of a global projection there.
 *
 * The SQLite stores do not create the folders their files go in, so `folders`
 * creates them. Anything left over from an earlier run is removed first, so every
 * run starts empty.
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.Schema

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

object Sqlite {

  /** The folder the example files go in. It is ignored by git. */
  val root: String = ".projection-examples"

  /** The path of a file or folder inside [[root]]. */
  def path(name: String): String = s"$root/$name"

  // Temporarily disabled, so that the files can be looked at after a run. When it is `true`, `folders` deletes
  // its folders again when the scope closes.
  private val cleanupAfterRun: Boolean = false

  private def deleteRecursively(folder: Path): Unit =
    if (Files.exists(folder)) {
      val stream = Files.walk(folder)
      try stream.iterator().asScala.toList.reverse.foreach(Files.delete)
      finally stream.close()
    }

  /**
   * Creates empty folders at the given paths, for example
   * `Sqlite.path("events")`.
   */
  def folders(paths: String*): ZIO[Scope, Throwable, Unit] =
    ZIO.foreachDiscard(paths) { name =>
      val folder = Paths.get(name)
      ZIO.attemptBlocking {
        deleteRecursively(folder)
        Files.createDirectories(folder)
      } *> ZIO.addFinalizer(ZIO.attemptBlocking(deleteRecursively(folder)).orDie).when(cleanupAfterRun)
    }

  /**
   * Prepares the store of a global projection. The engine keeps it in
   * `global/<name>.db`, a folder that several examples share, so only that
   * projection's files are removed, not the whole folder.
   */
  def globalFolder(projectionName: String): ZIO[Scope, Throwable, Unit] =
    ZIO.attemptBlocking {
      Files.createDirectories(Paths.get("global"))
      List("", "-wal", "-shm").foreach(suffix => Files.deleteIfExists(Paths.get(s"global/$projectionName.db$suffix")))
    }

  /**
   * An event store kept in its own SQLite file, for example
   * `Sqlite.path("events/users.db")`.
   */
  def eventStore[E: Schema](cache: TransactorCache, file: String): Task[SQLiteEventStore[E]] =
    cache.get(file).flatMap(transactor => SQLiteEventStore.make[E](transactor))

  /** The `.db` files in a folder, sorted by name. */
  def dbFiles(folder: String): Task[List[String]] =
    ZIO.attemptBlocking {
      val stream = Files.list(Paths.get(folder))
      try stream.iterator().asScala.map(_.getFileName.toString).filter(_.endsWith(".db")).toList.sorted
      finally stream.close()
    }
}
