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
 * Example: renaming an event.
 *
 * Event tags are stored as strings, so renaming a case, here `UserLoggedIn` to
 * `UserAuthenticated`, leaves old events in the store under the old tag. A
 * `Migration` that renames the case lets `TagResolver` work out the alias, so old
 * events can still be recognised and read.
 *
 * It shows:
 *   - `TagResolver.resolve` building a `TagInfo` from a migration
 *   - `TagInfo` helpers: `currentTags`, `aliases`, `allTags`, `expandRequested`,
 *     `isOldTag` and `currentTagFor`
 *   - a chain of renames (A to B to C), which resolves straight to the final name
 *
 * It does not need an engine, so it prints the results of the calls directly.
 *
 * Run it from this directory with:
 *   sbt "runMain projection.examples.TagRenameExample"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.Migration

object TagRenameExample extends ZIOAppDefault {

  sealed trait LoginEvent
  object LoginEvent {
    case class UserLoggedIn(userId: String, timestamp: String) extends LoginEvent
    object UserLoggedIn {
      implicit val schema: Schema[UserLoggedIn] = Schema.derived[UserLoggedIn]
    }
    case class UserAuthenticated(userId: String, timestamp: String) extends LoginEvent
    object UserAuthenticated {
      implicit val schema: Schema[UserAuthenticated] = Schema.derived[UserAuthenticated]
    }
    implicit val schema: Schema[LoginEvent] = Schema.derived[LoginEvent]
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Demo 5: Tag Rename (UserLoggedIn -> UserAuthenticated) ---").orDie *>
      ZIO.scoped {
        for {
          migration = Migration
                        .newBuilder[LoginEvent, LoginEvent]
                        .renameCase("UserLoggedIn", "UserAuthenticated")
                        .build

          tagInfo = TagResolver.resolve[LoginEvent](migration)

          _ <- Console.printLine(s"  Current tags: ${tagInfo.currentTags.mkString(", ")}").orDie
          _ <- Console.printLine(s"  Aliases:      ${tagInfo.aliases}").orDie
          _ <- Console.printLine(s"  All tags:     ${tagInfo.allTags.mkString(", ")}").orDie

          expanded = tagInfo.expandRequested(Set("UserAuthenticated"))
          _       <- Console.printLine(s"  expandRequested(UserAuthenticated) -> ${expanded.mkString(", ")}").orDie

          _ <- Console.printLine(s"  isOldTag('UserLoggedIn'):      ${tagInfo.isOldTag("UserLoggedIn")}").orDie
          _ <- Console.printLine(s"  isOldTag('UserAuthenticated'): ${tagInfo.isOldTag("UserAuthenticated")}").orDie
          _ <- Console.printLine(s"  currentTagFor('UserLoggedIn'): ${tagInfo.currentTagFor("UserLoggedIn")}").orDie

          migration2 = Migration
                         .newBuilder[LoginEvent, LoginEvent]
                         .renameCase("UserLoggedIn", "UserSignedIn")
                         .renameCase("UserSignedIn", "UserAuthenticated")
                         .build
          tagInfo2 = TagResolver.resolve[LoginEvent](migration2)
          _       <- Console.printLine(s"  Transitive chain: ${tagInfo2.aliases}").orDie
          _       <- Console.printLine(s"  isOldTag('UserLoggedIn'):      ${tagInfo2.isOldTag("UserLoggedIn")}").orDie
          _       <- Console.printLine(s"  currentTagFor('UserLoggedIn'): ${tagInfo2.currentTagFor("UserLoggedIn")}").orDie

          _ <- Console.printLine("  ✓ Tag Rename: migration aliases ensure old events are readable").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
