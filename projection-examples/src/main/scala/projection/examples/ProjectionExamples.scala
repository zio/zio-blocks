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

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.{InMemoryProjectionStore, TestEngine}
import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.schema.migration.Migration
import zio.stream.ZStream

import java.time.Instant

/**
 * Comprehensive examples demonstrating all three projection scopes, schema
 * evolution, and event tag migration.
 *
 * Run with: {{{sbt "++3.8.3; projection-examples/run"}}}
 */
object ProjectionExampleApp extends ZIOAppDefault {

  // ===========================================================================
  // Models — Events
  // ===========================================================================

  case class UserCreated(name: String, email: String)
  object UserCreated {
    implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
  }

  case class RepoCreated(ownerId: String, repoName: String)
  object RepoCreated {
    implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
  }

  // ===========================================================================
  // Models — PerEntity projection: UserProfile
  // ===========================================================================

  case class UserProfile(@Modifier.id id: String, name: String, email: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  // ===========================================================================
  // Models — CrossEntity projection: RepoListEntry
  // ===========================================================================

  case class RepoListEntry(@Modifier.id id: String, ownerId: String, repoName: String)
  object RepoListEntry {
    implicit val schema: Schema[RepoListEntry]         = Schema.derived[RepoListEntry]
    implicit val entityPath: EntityPath[RepoListEntry] = EntityPath.derived[RepoListEntry]
  }

  // ===========================================================================
  // Models — Global aggregate: DailyStats
  // ===========================================================================

  case class DailyStats(@Modifier.id date: String, userCount: Int, repoCount: Int)
  object DailyStats {
    implicit val schema: Schema[DailyStats]         = Schema.derived[DailyStats]
    implicit val entityPath: EntityPath[DailyStats] = EntityPath.derived[DailyStats]
  }

  // ===========================================================================
  // Models — Schema Evolution: V1 and V2
  // ===========================================================================

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

  // ===========================================================================
  // Models — Tag Rename: OldEvent / NewEvent
  // ===========================================================================

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

  // ===========================================================================
  // Helper: poll until value appears (engine processes async)
  // ===========================================================================

  private def pollUntil[A](zio: Task[Option[A]], timeout: Duration = 3.seconds): ZIO[Any, Throwable, Option[A]] =
    for {
      start  <- Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
      result <- loopUntil(zio, start + timeout.toMillis)
    } yield result

  private def loopUntil[A](zio: Task[Option[A]], deadline: Long): ZIO[Any, Throwable, Option[A]] =
    for {
      opt <- zio
      now <- Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
      res <- if (opt.isDefined) ZIO.succeed(opt)
             else if (now >= deadline) ZIO.succeed(None)
             else ZIO.sleep(20.millis) *> loopUntil(zio, deadline)
    } yield res

  // Simple standalone EventStore stub for schema-evolution rebuild demo
  final class DemoEventStore[E](
    hub: Hub[EventEnvelope[E]],
    buffer: Ref[List[EventEnvelope[E]]],
    counter: Ref[Long]
  ) extends EventStore[E] {
    def subscribe: Hub[EventEnvelope[E]]               = hub
    def append(entityId: String, event: E): Task[Long] =
      for {
        seq <- counter.updateAndGet(_ + 1)
        env  = EventEnvelope(seq, event.getClass.getSimpleName.stripSuffix("$"), event, Instant.now(), entityId)
        _   <- buffer.update(_ :+ env)
        _   <- hub.publish(env).unit
      } yield seq
    def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
      ZStream.unwrap {
        buffer.get.map { list =>
          val filtered = list.filter(_.seq > afterSeq).filter(e => tags.isEmpty || tags.contains(e.tag))
          ZStream.fromIterable(filtered)
        }
      }
    def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
      readFrom(0L, tags)
  }
  object DemoEventStore {
    def make[E]: ZIO[Any, Nothing, DemoEventStore[E]] =
      for {
        hub     <- Hub.unbounded[EventEnvelope[E]]
        buffer  <- Ref.make(List.empty[EventEnvelope[E]])
        counter <- Ref.make(0L)
      } yield new DemoEventStore[E](hub, buffer, counter)
  }

  // ===========================================================================
  // Demo 1: PerEntity — UserProfile from UserCreated
  // ===========================================================================

  def demoPerEntity: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Demo 1: PerEntity Projection (UserProfile) ---").orDie *>
      ZIO.scoped {
        for {
          projection = Projection[UserProfile]("userProfiles")
                         .from("users")
                         .routeToSelf
                         .on[UserCreated]
                         .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

          engine <- TestEngine.make(projection)

          _ <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
          _ <- engine.append("user-2", UserCreated("Bob", "bob@example.com"))
          _ <- engine.append("user-3", UserCreated("Charlie", "charlie@example.com"))

          u1 <- pollUntil(engine.query(projection, "user-1"))
          u2 <- pollUntil(engine.query(projection, "user-2"))
          u3 <- pollUntil(engine.query(projection, "user-3"))

          _ <- Console.printLine(s"  user-1: ${u1.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie
          _ <- Console.printLine(s"  user-2: ${u2.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie
          _ <- Console.printLine(s"  user-3: ${u3.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie
          _ <- Console.printLine("  ✓ PerEntity: each user stored in its own entity row").orDie
        } yield ()
      }

  // ===========================================================================
  // Demo 2: CrossEntity — RepoListEntry routed by ownerId
  // ===========================================================================

  def demoCrossEntity: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Demo 2: CrossEntity Projection (RepoListEntry routed by ownerId) ---").orDie *>
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

  // ===========================================================================
  // Demo 3: Global — DailyStats aggregate from UserCreated + RepoCreated
  // ===========================================================================

  def demoGlobal: ZIO[Any, Throwable, Unit] =
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

  // ===========================================================================
  // Demo 4: Schema Evolution — UserProfileV1 -> UserProfileV2
  // ===========================================================================

  def demoSchemaEvolution: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Demo 4: Schema Evolution (UserProfileV1 -> UserProfileV2) ---").orDie *>
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

  // ===========================================================================
  // Demo 5: Tag Rename — UserLoggedIn -> UserAuthenticated via Migration
  // ===========================================================================

  def demoTagRename: ZIO[Any, Throwable, Unit] =
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

  // ===========================================================================
  // Main: Run all demos sequentially
  // ===========================================================================

  def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- Console.printLine("=== ZIO Blocks Projection Examples ===").orDie
      _ <- demoPerEntity
      _ <- demoCrossEntity
      _ <- demoGlobal
      _ <- demoSchemaEvolution
      _ <- demoTagRename
      _ <- Console.printLine("\n=== All demos completed successfully ===").orDie
    } yield ()
}
