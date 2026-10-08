/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Example 2 of 5: a cross-entity projection.
 *
 * Read Example1PerEntity first. Next: Example3GlobalAggregate.
 *
 * A cross-entity projection groups events by a key read from the event itself,
 * instead of by the ID the event was recorded under. Here `RepoCreated` events
 * are routed by their `ownerId`, so all of one owner's repositories end up in
 * the same shard.
 *
 * A shard is a separate store. With SQLite, which this example uses, each routing
 * key gets its own file next to the main one, for example
 * `repos/repoListEntries_shard_alice.db`. The engine reads rows back by ID with
 * `engine.query`, which looks in the main store and then in the shards. It has
 * no call that lists a shard's rows, so a view such as "all of alice's
 * repositories" is read from her shard file with SQL. The example does that over
 * JDBC.
 *
 * It shows:
 *   - `routedBy`, which makes the projection's scope `CrossEntity`
 *   - `insert`, with a row ID that is unique for each repository. The routing key
 *     only chooses the shard, so if the owner were the row ID, a second repository
 *     from the same owner would clash with the first row, since `insert` fails
 *     when the ID already exists
 *   - an engine with SQLite stores: the shard files that appear, and reading one
 *     of them
 *
 * The SQLite store needs `sqlite-jdbc` on the classpath, and it does not create
 * its folder, so the example creates `repos/` in the working directory and
 * deletes it when it finishes.
 *
 * Run it from this directory with:
 *   sbt "runMain projection.examples.Example2CrossEntity"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import java.nio.file.{Files, Path, Paths}
import java.sql.DriverManager
import scala.jdk.CollectionConverters.*

import Polling.pollUntil

object Example2CrossEntity extends ZIOAppDefault {

  case class RepoListEntry(@Modifier.id id: String, ownerId: String, repoName: String)
  object RepoListEntry {
    implicit val schema: Schema[RepoListEntry]         = Schema.derived[RepoListEntry]
    // The folder is chosen explicitly. Derived from the `id` field, it would be "ids".
    implicit val entityPath: EntityPath[RepoListEntry] = EntityPath.derived[RepoListEntry]("repos")
  }

  private val folder: Path = Paths.get("repos")

  /** Creates the folder the SQLite files go in, and deletes it again when the scope closes. */
  private val folderScoped: ZIO[Scope, Throwable, Unit] =
    ZIO.attempt(Files.createDirectories(folder)).unit *>
      ZIO
        .addFinalizer(ZIO.attemptBlocking {
          val stream = Files.walk(folder)
          try stream.iterator().asScala.toList.reverse.foreach(Files.delete)
          finally stream.close()
        }.orDie)
        .unit

  /** The names of the repositories in one owner's shard file, read with plain SQL. */
  private def reposInShard(owner: String): Task[List[String]] =
    ZIO.attemptBlocking {
      val connection = DriverManager.getConnection(s"jdbc:sqlite:repos/repoListEntries_shard_$owner.db")
      try {
        val rows   = connection.createStatement().executeQuery("SELECT repo_name FROM repo_list_entry ORDER BY repo_name")
        val result = List.newBuilder[String]
        while (rows.next()) result += rows.getString(1)
        result.result()
      } finally connection.close()
    }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 2: CrossEntity Projection (RepoListEntry routed by ownerId) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- folderScoped

          projection = Projection[RepoListEntry]("repoListEntries")
                         .from("repos")
                         .routedBy[RepoCreated](_.ownerId)
                         .on[RepoCreated]
                         .insert((e, _) => RepoListEntry(s"${e.ownerId}/${e.repoName}", e.ownerId, e.repoName))

          _ <- Console.printLine(s"  Scope: ${projection.scope}").orDie

          // TestEngine uses in-memory stores, so the engine is wired by hand to get SQLite files.
          events <- DemoEventStore.make[Any]
          cache  <- TransactorCache.make()
          engine <- ProjectionEngine.makeWithStoresVarargs(cache, Map("repos" -> events))(projection)
          _      <- engine.start

          _ <- events.append("repo-1", RepoCreated("alice", "zio-blocks"))
          _ <- events.append("repo-2", RepoCreated("alice", "zio-http"))
          _ <- events.append("repo-3", RepoCreated("bob", "zio-kafka"))
          _ <- events.append("repo-4", RepoCreated("charlie", "zio-schema"))

          // One row by its ID: the engine looks in the main store and then in the shards.
          zioHttp <- pollUntil(engine.query(projection, "alice/zio-http"))
          _       <- Console.printLine(s"  query alice/zio-http: ${zioHttp.map(_.repoName).getOrElse("not found")}").orDie

          // Wait until every row has been applied, then look at the files.
          _ <- pollUntil(engine.query(projection, "charlie/zio-schema"))

          files <- ZIO.attemptBlocking {
                     val stream = Files.list(folder)
                     try stream.iterator().asScala.map(_.getFileName.toString).filter(_.endsWith(".db")).toList.sorted
                     finally stream.close()
                   }
          _ <- Console.printLine(s"  Files in repos/: ${files.mkString(", ")}").orDie

          // The view of one owner's repositories is the content of that owner's shard file.
          alice   <- reposInShard("alice")
          bob     <- reposInShard("bob")
          charlie <- reposInShard("charlie")
          _       <- Console.printLine(s"  alice's repos:   ${alice.mkString(", ")}").orDie
          _       <- Console.printLine(s"  bob's repos:     ${bob.mkString(", ")}").orDie
          _       <- Console.printLine(s"  charlie's repos: ${charlie.mkString(", ")}").orDie
          _       <- Console.printLine("  ✓ CrossEntity: repos routed by ownerId to one SQLite shard file per owner").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
