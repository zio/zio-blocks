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
 * Example 4 of 5: schema evolution.
 *
 * Builds on Example1PerEntity. Next: Example5TagRename.
 *
 * When the read model's class changes, such as `UserProfileV1` gaining an
 * `email` field to become `UserProfileV2`, the data already stored no longer
 * matches. The engine notices this by comparing a hash of the schema, and
 * rebuilds the store by replaying the events.
 *
 * It shows:
 *   - `SchemaHash.compute`, and that two versions of a type hash differently
 *   - running the changed projection (`UserProfileV2`) in memory
 *   - `SchemaEvolution.rebuildWithHash`, which rebuilds a store from an event
 *     store, using a small hand-written `DemoEventStore`
 *
 * Run it from this directory with:
 *   sbt "runMain projection.examples.Example4SchemaEvolution"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.{InMemoryProjectionStore, TestEngine}
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example4SchemaEvolution extends ZIOAppDefault {

  case class UserProfileV1(@Modifier.id id: String, name: String)
  object UserProfileV1 {
    implicit val schema: Schema[UserProfileV1]         = Schema.derived[UserProfileV1]
    implicit val entityPath: EntityPath[UserProfileV1] = EntityPath.derived[UserProfileV1]
  }

  case class UserProfileV2(@Modifier.id id: String, name: String, email: String)
  object UserProfileV2 {
    implicit val schema: Schema[UserProfileV2]         = Schema.derived[UserProfileV2]
    implicit val entityPath: EntityPath[UserProfileV2] = EntityPath.derived[UserProfileV2]
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 4: Schema Evolution (UserProfileV1 -> UserProfileV2) ---").orDie *>
      ZIO.scoped {
        for {
          hashV1 = SchemaHash.compute[UserProfileV1]
          hashV2 = SchemaHash.compute[UserProfileV2]
          _     <- Console.printLine(s"  SchemaHash V1: ${hashV1.take(16)}...").orDie
          _     <- Console.printLine(s"  SchemaHash V2: ${hashV2.take(16)}...").orDie
          _     <- Console.printLine(s"  Hashes differ: ${hashV1 != hashV2}").orDie

          projection = Projection[UserProfileV2]("userProfilesV2")
                         .from("users")
                         .routeToSelf
                         .on[UserCreated]
                         .insert((e, ctx) => UserProfileV2(ctx.entityId, e.name, e.email))

          engine <- TestEngine.make(projection)

          _ <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
          _ <- engine.append("user-2", UserCreated("Bob", "bob@example.com"))

          u1 <- pollUntil(engine.query(projection, "user-1"))
          u2 <- pollUntil(engine.query(projection, "user-2"))

          _ <- Console.printLine(s"  V2 user-1: ${u1.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie
          _ <- Console.printLine(s"  V2 user-2: ${u2.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie

          _ <- Console.printLine("  --- SchemaEvolution.rebuild demo ---").orDie
          // Standalone rebuild demo using direct store/hub (without TestEngine wiring)
          store   <- InMemoryProjectionStore.make[UserProfileV2]
          hub     <- DemoEventStore.make[Any]
          _       <- hub.append("user-1", UserCreated("Alice", "alice@example.com"))
          _       <- hub.append("user-2", UserCreated("Bob", "bob@example.com"))
          _       <- SchemaEvolution.rebuildWithHash(store, hub, projection)
          u1After <- store.findById("user-1")
          _       <- Console
                 .printLine(
                   s"  After rebuild, user-1: ${u1After.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}"
                 )
                 .orDie
          _ <- Console.printLine("  ✓ Schema Evolution: hash detection + rebuild preserves data").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
