/*
 * Example 10 of 10: `routeToAll` and `Projection.global`.
 *
 * Read Example3GlobalAggregate first.
 *
 * `routeToAll` and `Projection.global` are often used together, and it is easy to
 * think that `routeToAll` is what makes a projection global. This example puts
 * them side by side. Both projections count page hits from the same source with
 * the same handler and the same `.from("web").routeToAll`. The only difference
 * is how they are created:
 *   - `Projection[PageHits]("hits_per_entity")` is a normal projection
 *   - `Projection.global[PageHits]("hits_global")` is a global projection
 *
 * The example prints what is different and what is not:
 *   - the scope: `PerEntity` for the first, `Global` for the second
 *   - the folder of the database file: the one of the `EntityPath` for the first,
 *     and `global/` for the second
 *   - the rows: the same in both. `routeToAll` sends every event to the same
 *     store, but the row of an event is still the one with the ID it was
 *     recorded under, so `page-1` and `page-2` are two rows, not one.
 *     To get a single row, record the events under a single ID, as
 *     Example3GlobalAggregate does with the date.
 *
 * This example runs on real SQLite files: the events are kept in
 * `.projection-examples/example10/events/web.db`, the first projection in
 * `.projection-examples/example10/hits/hits_per_entity.db` and the second in
 * `global/hits_global.db`.
 *
 * It shows:
 *   - that `routeToAll` alone leaves the scope `PerEntity`
 *   - that `Projection.global` gives the scope `Global` and the `global/` folder
 *   - that `routeToAll` does not combine rows
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example10RouteToAllAndGlobal"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example10RouteToAllAndGlobal extends ZIOAppDefault {

  case class PageViewed(page: String)
  object PageViewed {
    implicit val schema: Schema[PageViewed] = Schema.derived[PageViewed]
  }

  private val hitsFolder: String = Sqlite.path("example10/hits")

  // The folder is chosen explicitly. Derived from the `id` field, it would be "ids".
  case class PageHits(@Modifier.id id: String, hits: Long)
  object PageHits {
    implicit val schema: Schema[PageHits]         = Schema.derived[PageHits]
    implicit val entityPath: EntityPath[PageHits] = EntityPath.derived[PageHits](hitsFolder)
  }

  private def show(row: Option[PageHits]): String =
    row.map(r => s"${r.hits} hits").getOrElse("no row")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 10: routeToAll and Projection.global (page hits) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.globalFolder("hits_global") *> Sqlite.folders(hitsFolder, Sqlite.path("example10/events"))

          // The same source, routing and handler. Only the constructor differs.
          perEntity = Projection[PageHits]("hits_per_entity")
                        .from("web")
                        .routeToAll
                        .on[PageViewed]
                        .aggregate(FieldUpdate.Increment("hits", 1L))

          global = Projection
                     .global[PageHits]("hits_global")
                     .from("web")
                     .routeToAll
                     .on[PageViewed]
                     .aggregate(FieldUpdate.Increment("hits", 1L))

          _ <- Console.printLine(s"  hits_per_entity scope: ${perEntity.scope}").orDie
          _ <- Console.printLine(s"  hits_global scope:     ${global.scope}").orDie

          cache <- TransactorCache.make()
          web   <- Sqlite.eventStore[PageViewed](cache, Sqlite.path("example10/events/web.db"))
          engine <- ProjectionEngine.makeWithStoresVarargs(
                      cache,
                      Map[String, EventStore[?]]("web" -> web)
                    )(perEntity, global)
          _ <- engine.start

          // Events are recorded under the page, so each page is its own entity.
          _ <- web.append("page-1", PageViewed("/a"))
          _ <- web.append("page-2", PageViewed("/b"))
          _ <- web.append("page-1", PageViewed("/a"))

          // Page 1 is the last row to be complete: it needs both of its events.
          p1 <- pollUntil(engine.query(perEntity, "page-1").map(_.filter(_.hits == 2)))
          g1 <- pollUntil(engine.query(global, "page-1").map(_.filter(_.hits == 2)))
          p2 <- engine.query(perEntity, "page-2")
          g2 <- engine.query(global, "page-2")

          perEntityFiles <- Sqlite.dbFiles(hitsFolder)
          globalFiles    <- Sqlite.dbFiles("global").map(_.filter(_ == "hits_global.db"))
          _ <- Console.printLine(s"  Files in $hitsFolder/: ${perEntityFiles.mkString(", ")}").orDie
          _ <- Console.printLine(s"  Files in global/: ${globalFiles.mkString(", ")}").orDie
          _ <- Console.printLine(s"  hits_per_entity: page-1 ${show(p1)}, page-2 ${show(p2)}").orDie
          _ <- Console.printLine(s"  hits_global:     page-1 ${show(g1)}, page-2 ${show(g2)}").orDie
          _ <- Console
                 .printLine("  ✓ routeToAll: one store, one row per ID. Only Projection.global changes the scope and the folder")
                 .orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
