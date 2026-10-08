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
 * Example: a global aggregate projection.
 *
 * A global projection keeps one shared store for the whole projection, which is
 * the place for totals and counters. Here `UserCreated` events from the "users"
 * source and `RepoCreated` events from the "repos" source both feed a single
 * `DailyStats` row.
 *
 * It shows:
 *   - `Projection.global`, with `routeToAll` on each source
 *   - `aggregate` with `FieldUpdate.Increment`, which adds to a counter without
 *     reading the row first
 *   - one projection listening to more than one source
 *
 * Run it from this directory with:
 *   sbt "runMain projection.examples.GlobalAggregateExample"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object GlobalAggregateExample extends ZIOAppDefault {

  case class DailyStats(@Modifier.id date: String, userCount: Int, repoCount: Int)
  object DailyStats {
    implicit val schema: Schema[DailyStats]         = Schema.derived[DailyStats]
    implicit val entityPath: EntityPath[DailyStats] = EntityPath.derived[DailyStats]
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Demo 3: Global Aggregate (DailyStats from users + repos) ---").orDie *>
      ZIO.scoped {
        for {
          dateKey = "2025-08-26"

          projection = Projection
                         .global[DailyStats]("dailyStats")
                         .from("users")
                         .routeToAll
                         .on[UserCreated]
                         .aggregate(FieldUpdate.Increment("user_count", 1L))
                         .from("repos")
                         .routeToAll
                         .on[RepoCreated]
                         .aggregate(FieldUpdate.Increment("repo_count", 1L))

          _ <- Console.printLine(s"  Scope: ${projection.scope}").orDie

          engine <- TestEngine.make(projection)

          _ <- ZIO.foreachDiscard(1 to 5)(i => engine.append(dateKey, UserCreated(s"user-$i", s"u$i@ex.com")))
          _ <- ZIO.foreachDiscard(1 to 3)(i => engine.append(dateKey, RepoCreated("any", s"repo-$i")))

          stats <- pollUntil(
                     engine.query(projection, dateKey).map(_.filter(s => s.userCount == 5 && s.repoCount == 3)),
                     5.seconds
                   )

          _ <- stats match {
                 case Some(s) =>
                   Console.printLine(s"  Date: $dateKey, Users: ${s.userCount}, Repos: ${s.repoCount}").orDie
                 case None =>
                   Console.printLine("  ⚠ Aggregate not yet ready (timeout)").orDie
               }
          _ <- Console.printLine("  ✓ Global: events from multiple sources converge to single aggregate row").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
