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
 * Example 5 of 5: renaming an event.
 *
 * Builds on Example4SchemaEvolution. This is the last example.
 *
 * Event tags are stored as strings, so renaming a case, here `UserLoggedIn` to
 * `UserAuthenticated`, leaves old events in the store under the old tag. A
 * `Migration` that renames the case lets `TagResolver` work out the alias, so old
 * events can still be recognised and read.
 *
 * This example runs on a real SQLite event store,
 * `.projection-examples/example5/events/logins.db`, with two versions of the event type in
 * a row. Both are called `LoginEvent`, so they use the same table.
 *   1. `V1.LoginEvent` has the case `UserLoggedIn`. An event is appended with it.
 *   2. `V2.LoginEvent` has the case `UserAuthenticated`. The store is opened again
 *      with a migration that renames the case, and the old event is read back
 *      under its new name.
 *
 * It shows:
 *   - `TagResolver.resolve` building a `TagInfo` from a migration, and its
 *     helpers: `currentTags`, `aliases`, `allTags`, `expandRequested`, `isOldTag`
 *     and `currentTagFor`
 *   - a chain of renames (A to B to C), which resolves straight to the final name
 *   - `SQLiteEventStore.make` with a migration, so that `readAll` returns an old
 *     event with the current tag and decoded as the new case
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example5TagRename"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.Migration

object Example5TagRename extends ZIOAppDefault {

  object V1 {
    sealed trait LoginEvent
    object LoginEvent {
      case class UserLoggedIn(userId: String, timestamp: String) extends LoginEvent
      object UserLoggedIn {
        implicit val schema: Schema[UserLoggedIn] = Schema.derived[UserLoggedIn]
      }
      implicit val schema: Schema[LoginEvent] = Schema.derived[LoginEvent]
    }
  }

  object V2 {
    sealed trait LoginEvent
    object LoginEvent {
      case class UserAuthenticated(userId: String, timestamp: String) extends LoginEvent
      object UserAuthenticated {
        implicit val schema: Schema[UserAuthenticated] = Schema.derived[UserAuthenticated]
      }
      implicit val schema: Schema[LoginEvent] = Schema.derived[LoginEvent]
    }
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 5: Tag Rename (UserLoggedIn -> UserAuthenticated) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.folders(Sqlite.path("example5/events"))

          migration = Migration
                        .newBuilder[V2.LoginEvent, V2.LoginEvent]
                        .renameCase("UserLoggedIn", "UserAuthenticated")
                        .build

          tagInfo = TagResolver.resolve[V2.LoginEvent](migration)

          _ <- Console.printLine(s"  Current tags: ${tagInfo.currentTags.mkString(", ")}").orDie
          _ <- Console.printLine(s"  Aliases:      ${tagInfo.aliases}").orDie
          _ <- Console.printLine(s"  All tags:     ${tagInfo.allTags.mkString(", ")}").orDie

          expanded = tagInfo.expandRequested(Set("UserAuthenticated"))
          _       <- Console.printLine(s"  expandRequested(UserAuthenticated) -> ${expanded.mkString(", ")}").orDie

          _ <- Console.printLine(s"  isOldTag('UserLoggedIn'):      ${tagInfo.isOldTag("UserLoggedIn")}").orDie
          _ <- Console.printLine(s"  isOldTag('UserAuthenticated'): ${tagInfo.isOldTag("UserAuthenticated")}").orDie
          _ <- Console.printLine(s"  currentTagFor('UserLoggedIn'): ${tagInfo.currentTagFor("UserLoggedIn")}").orDie

          migration2 = Migration
                         .newBuilder[V2.LoginEvent, V2.LoginEvent]
                         .renameCase("UserLoggedIn", "UserSignedIn")
                         .renameCase("UserSignedIn", "UserAuthenticated")
                         .build
          tagInfo2 = TagResolver.resolve[V2.LoginEvent](migration2)
          _       <- Console.printLine(s"  Transitive chain: ${tagInfo2.aliases}").orDie

          // 1. The first version writes an event under the tag `UserLoggedIn`.
          _ <- ZIO.scoped {
                 for {
                   cache  <- TransactorCache.make()
                   tx     <- cache.get(Sqlite.path("example5/events/logins.db"))
                   events <- SQLiteEventStore.make[V1.LoginEvent](tx)
                   _      <- events.append("user-1", V1.LoginEvent.UserLoggedIn("user-1", "10:00"))
                 } yield ()
               }

          // 2. The second version opens the same file with the migration, and also appends an event.
          _ <- ZIO.scoped {
                 for {
                   cache  <- TransactorCache.make()
                   tx     <- cache.get(Sqlite.path("example5/events/logins.db"))
                   events <- SQLiteEventStore.make[V2.LoginEvent](tx, migration)
                   _      <- events.append("user-2", V2.LoginEvent.UserAuthenticated("user-2", "11:00"))
                   all    <- events.readAll().runCollect
                   _ <- ZIO.foreachDiscard(all) { env =>
                          Console.printLine(s"  seq ${env.seq}: tag ${env.tag}, ${env.entityId}, ${env.event}").orDie
                        }
                   byNewTag <- events.readAll(Set("UserAuthenticated")).runCollect
                   _        <- Console.printLine(s"  readAll(UserAuthenticated) returns ${byNewTag.size} events").orDie
                 } yield ()
               }

          _ <- Console.printLine("  ✓ Tag Rename: migration aliases ensure old events are readable").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
