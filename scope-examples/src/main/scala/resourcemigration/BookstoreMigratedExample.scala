package resourcemigration

import zio.blocks.scope._

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

/**
 * The bookstore service graph from the "Coming from cats-effect Resource, ZIO
 * Scope/ZLayer, macwire or Using" guide, fully migrated to ZIO Blocks Scope.
 *
 * `Resource.from[Catalog]` wires the graph from constructors, sharing one `Db`
 * between `InventoryRepo` and `PriceService`. The scope opens a temporary
 * export file as a second resource. When the scope closes, finalizers run in
 * LIFO order: the export file is deleted first, then the price cache is
 * flushed, then the database connection is closed.
 *
 * Run with: sbt "runMain resourcemigration.BookstoreMigratedExample"
 */

final case class DbConfig(url: String)

final class Db(config: DbConfig) extends AutoCloseable {
  println(s"Db: connected to ${config.url}")

  private val stock: Map[String, Int] = Map(
    "Zionomicon"                      -> 3,
    "Programming in Scala"            -> 7,
    "Functional Programming in Scala" -> 0
  )

  private val prices: Map[String, BigDecimal] = Map(
    "Zionomicon"                      -> BigDecimal("49.99"),
    "Programming in Scala"            -> BigDecimal("59.95"),
    "Functional Programming in Scala" -> BigDecimal("44.99")
  )

  def stockOf(title: String): Int        = stock.getOrElse(title, 0)
  def priceOf(title: String): BigDecimal = prices.getOrElse(title, BigDecimal(0))
  def close(): Unit                      = println("Db: connection closed")
}

final class InventoryRepo(db: Db) {
  def inStock(title: String): Int = db.stockOf(title)
}

// The implicit Finalizer lets a service register its own cleanup; the wiring
// macro injects it automatically.
final class PriceService(db: Db)(implicit finalizer: Finalizer) {
  defer(println("PriceService: price cache flushed"))

  def price(title: String): BigDecimal = db.priceOf(title)
}

final class Catalog(inventory: InventoryRepo, prices: PriceService) {
  def describe(title: String): String =
    s"$title: ${inventory.inStock(title)} in stock at $$${prices.price(title)}"
}

final class ExportFile(prefix: String) extends AutoCloseable {
  private val path: Path = Files.createTempFile(prefix, ".csv")
  println("ExportFile: created")

  def write(line: String): Unit = {
    Files.write(path, (line + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND)
    ()
  }

  def lineCount: Int = Files.readAllLines(path).size

  def close(): Unit = {
    Files.deleteIfExists(path)
    println("ExportFile: deleted")
  }
}

object BookstoreMigratedExample {

  val titles: List[String] =
    List("Zionomicon", "Programming in Scala", "Functional Programming in Scala")

  // A `def`, not a `val`: a Resource built from shared wires is single-use once
  // its last scope has closed, so each run builds a fresh graph.
  def catalogResource: Resource[Catalog] =
    Resource.from[Catalog](Wire(DbConfig("jdbc:h2:mem:bookstore")))

  def main(args: Array[String]): Unit = {
    val exported: Int = Scope.global.scoped { scope =>
      import scope._

      val catalog = allocate(catalogResource)
      val file    = allocate(new ExportFile("catalog"))

      titles.foreach { title =>
        $(catalog, file)((c, f) => f.write(c.describe(title)))
      }
      $(file)(_.lineCount)
    }

    println(s"Exported $exported lines")
  }
}
