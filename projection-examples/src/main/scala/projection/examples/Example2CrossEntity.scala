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
 *   - `custom`, to return a `ProjectionAction.Upsert` instead of a plain insert
 *   - querying a shard by its routing key
 *
 * Note that the row's ID here is the owner, so a later repository replaces an
 * earlier one from the same owner: alice ends up with only `zio-http`. Give each
 * row its own ID, such as "owner/repo", to keep every repository.
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
                         .custom((e, _) => ProjectionAction.Upsert(RepoListEntry(e.ownerId, e.ownerId, e.repoName)))

          _ <- Console.printLine(s"  Scope: ${projection.scope}").orDie

          engine <- TestEngine.make(projection)

          _ <- engine.append("repo-1", RepoCreated("alice", "zio-blocks"))
          _ <- engine.append("repo-2", RepoCreated("alice", "zio-http"))
          _ <- engine.append("repo-3", RepoCreated("bob", "zio-kafka"))
          _ <- engine.append("repo-4", RepoCreated("charlie", "zio-schema"))

          alice   <- pollUntil(engine.query(projection, "alice"))
          bob     <- pollUntil(engine.query(projection, "bob"))
          charlie <- pollUntil(engine.query(projection, "charlie"))

          _ <- Console.printLine(s"  alice's repos:   ${alice.map(_.repoName).getOrElse("none")}").orDie
          _ <- Console.printLine(s"  bob's repos:     ${bob.map(_.repoName).getOrElse("none")}").orDie
          _ <- Console.printLine(s"  charlie's repos: ${charlie.map(_.repoName).getOrElse("none")}").orDie
          _ <- Console.printLine("  ✓ CrossEntity: repos routed by ownerId to separate shards").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
