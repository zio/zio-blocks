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
 * are routed by their `ownerId`, so all of one owner's repositories go to the
 * same shard.
 *
 * It shows:
 *   - `routedBy`, which makes the projection's scope `CrossEntity`
 *   - `insert`, with a row ID that is unique for each repository
 *   - querying a shard by its routing key
 *
 * The routing key only chooses the shard. Each row still needs its own ID, so
 * here it is "owner/repo". If the owner were the row ID, a second repository from
 * the same owner would clash with the first row, since `insert` fails when the ID
 * already exists.
 *
 * Run it from this directory with:
 *   sbt "runMain projection.examples.Example2CrossEntity"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example2CrossEntity extends ZIOAppDefault {

  case class RepoListEntry(@Modifier.id id: String, ownerId: String, repoName: String)
  object RepoListEntry {
    implicit val schema: Schema[RepoListEntry]         = Schema.derived[RepoListEntry]
    implicit val entityPath: EntityPath[RepoListEntry] = EntityPath.derived[RepoListEntry]
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 2: CrossEntity Projection (RepoListEntry routed by ownerId) ---").orDie *>
      ZIO.scoped {
        for {
          projection = Projection[RepoListEntry]("repoListEntries")
                         .from("repos")
                         .routedBy[RepoCreated](_.ownerId)
                         .on[RepoCreated]
                         .insert((e, _) => RepoListEntry(s"${e.ownerId}/${e.repoName}", e.ownerId, e.repoName))

          _ <- Console.printLine(s"  Scope: ${projection.scope}").orDie

          engine <- TestEngine.make(projection)

          _ <- engine.append("repo-1", RepoCreated("alice", "zio-blocks"))
          _ <- engine.append("repo-2", RepoCreated("alice", "zio-http"))
          _ <- engine.append("repo-3", RepoCreated("bob", "zio-kafka"))
          _ <- engine.append("repo-4", RepoCreated("charlie", "zio-schema"))

          alice   <- ZIO.foreach(List("zio-blocks", "zio-http"))(r => pollUntil(engine.query(projection, s"alice/$r")))
          bob     <- ZIO.foreach(List("zio-kafka"))(r => pollUntil(engine.query(projection, s"bob/$r")))
          charlie <- ZIO.foreach(List("zio-schema"))(r => pollUntil(engine.query(projection, s"charlie/$r")))

          _ <- Console.printLine(s"  alice's repos:   ${alice.flatten.map(_.repoName).mkString(", ")}").orDie
          _ <- Console.printLine(s"  bob's repos:     ${bob.flatten.map(_.repoName).mkString(", ")}").orDie
          _ <- Console.printLine(s"  charlie's repos: ${charlie.flatten.map(_.repoName).mkString(", ")}").orDie
          _ <- Console.printLine("  ✓ CrossEntity: repos routed by ownerId to separate shards").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
