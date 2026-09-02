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

package zio.blocks.projection

import zio.*
import zio.test.*
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, Schema}
import zio.blocks.schema.migration.{DynamicMigration, Migration, MigrationAction}
import zio.blocks.schema.json.JsonCodec
import zio.blocks.sql.{DbCon, Transactor, SqlDialect, JdbcTransactor}

object TagResolverSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Test event models
  // ---------------------------------------------------------------------------

  sealed trait AppEvent
  object AppEvent {
    case class UserAuthenticated(id: String) extends AppEvent
    case class OrderCreated(orderId: String) extends AppEvent
    case class UserDeleted(id: String)       extends AppEvent

    implicit val schema: Schema[AppEvent] = Schema.derived[AppEvent]
  }

  sealed trait SimpleEvent
  object SimpleEvent {
    case class Created(name: String) extends SimpleEvent
    case class Updated(name: String) extends SimpleEvent
    implicit val schema: Schema[SimpleEvent] = Schema.derived[SimpleEvent]
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def freshStore(
    tagInfo: TagInfo,
    tmp: java.nio.file.Path
  ): Task[SQLiteEventStore[AppEvent]] =
    for {
      _     <- ZIO.attempt(Class.forName("org.sqlite.JDBC"))
      url    = s"jdbc:sqlite:${tmp.toAbsolutePath.toString}"
      tx     = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      hub   <- Hub.unbounded[EventEnvelope[AppEvent]]
      store <- SQLiteEventStore.makeWithHub[AppEvent](tx, hub, tagInfo)
    } yield store

  private def withStoreTmp(
    tagInfo: TagInfo
  )(f: (SQLiteEventStore[AppEvent], Transactor, java.nio.file.Path) => Task[TestResult]): Task[TestResult] =
    for {
      tmp    <- ZIO.attempt(java.nio.file.Files.createTempFile("tagresolver", ".db"))
      _      <- ZIO.attempt(Class.forName("org.sqlite.JDBC"))
      url     = s"jdbc:sqlite:${tmp.toAbsolutePath.toString}"
      tx      = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      hub    <- Hub.unbounded[EventEnvelope[AppEvent]]
      store  <- SQLiteEventStore.makeWithHub[AppEvent](tx, hub, tagInfo)
      result <- f(store, tx, tmp).ensuring(
                  ZIO.succeed {
                    try java.nio.file.Files.deleteIfExists(tmp)
                    catch { case _: Throwable => () }
                    try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tmp.toString + "-wal"))
                    catch { case _: Throwable => () }
                    try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tmp.toString + "-shm"))
                    catch { case _: Throwable => () }
                  }
                )
    } yield result

  private def rawInsert(
    tx: Transactor,
    tag: String,
    payloadJson: String,
    entityId: String = "e1"
  ): Task[Unit] =
    ZIO.attemptBlocking {
      tx.connect {
        val con  = summon[DbCon]
        val conn = con.connection
        val ps   = conn.prepareStatement("INSERT INTO events (tag, payload, ts, entityId) VALUES (?, ?, ?, ?)")
        try {
          val pw = ps.paramWriter
          pw.setString(1, tag)
          pw.setBytes(2, payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
          pw.setLong(3, java.time.Instant.now().toEpochMilli)
          pw.setString(4, entityId)
          ps.executeUpdate()
          ()
        } finally ps.close()
      }
    }

  // ---------------------------------------------------------------------------
  // Spec
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("TagResolverSpec")(
    test("currentTags extracts variant names") {
      val tags = TagResolver.currentTags[AppEvent]
      assertTrue(tags == Set("UserAuthenticated", "OrderCreated", "UserDeleted"))
    },
    test("no migration yields empty aliases and allTags == current") {
      val info = TagResolver.resolve[AppEvent]
      assertTrue(
        info.aliases.isEmpty,
        info.allTags == info.currentTags,
        info.currentTags == Set("UserAuthenticated", "OrderCreated", "UserDeleted")
      )
    },
    test("empty DynamicMigration yields empty alias map") {
      val info = TagResolver.resolve[AppEvent](Migration.identity[AppEvent])
      assertTrue(info.aliases.isEmpty, info.allTags == info.currentTags)
    },
    test("single RenameCase produces alias map") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info = TagResolver.resolve[AppEvent](m)
      assertTrue(
        info.aliases == Map("UserLoggedIn" -> "UserAuthenticated"),
        info.allTags == Set("UserAuthenticated", "OrderCreated", "UserDeleted", "UserLoggedIn"),
        info.currentTags == Set("UserAuthenticated", "OrderCreated", "UserDeleted")
      )
    },
    test("aliasMapFromDynamic extracts RenameCase") {
      val dm = DynamicMigration(
        MigrationAction.RenameCase(zio.blocks.schema.DynamicOptic.root, "OldTag", "NewTag")
      )
      val map = TagResolver.aliasMapFromDynamic(dm)
      assertTrue(map == Map("OldTag" -> "NewTag"))
    },
    test("transitive chain resolves to final tag") {
      val m = Migration
        .newBuilder[AppEvent, AppEvent]
        .renameCase("UserLoggedIn", "UserSignedIn")
        .renameCase("UserSignedIn", "UserAuthenticated")
        .build
      val info = TagResolver.resolve[AppEvent](m)
      assertTrue(
        info.aliases == Map("UserLoggedIn" -> "UserAuthenticated", "UserSignedIn" -> "UserAuthenticated"),
        info.allTags.contains("UserLoggedIn"),
        info.allTags.contains("UserSignedIn"),
        info.allTags.contains("UserAuthenticated")
      )
    },
    test("allTags unions current and old") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("OldCreated", "OrderCreated").build
      val info = TagResolver.resolve[AppEvent](m)
      assertTrue(info.allTags == Set("UserAuthenticated", "OrderCreated", "UserDeleted", "OldCreated"))
    },
    test("TagInfo expandRequested empty returns allTags") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info = TagResolver.resolve[AppEvent](m)
      assertTrue(info.expandRequested(Set.empty) == info.allTags)
    },
    test("TagInfo expandRequested with current tag includes old aliases") {
      val m        = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info     = TagResolver.resolve[AppEvent](m)
      val expanded = info.expandRequested(Set("UserAuthenticated"))
      assertTrue(expanded.contains("UserAuthenticated"), expanded.contains("UserLoggedIn"))
    },
    test("TagInfo expandRequested with old tag includes current") {
      val m        = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info     = TagResolver.resolve[AppEvent](m)
      val expanded = info.expandRequested(Set("UserLoggedIn"))
      assertTrue(expanded.contains("UserLoggedIn"), expanded.contains("UserAuthenticated"))
    },
    test("unknownTags detection finds unmatched tags") {
      val m       = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info    = TagResolver.resolve[AppEvent](m)
      val dbTags  = Set("UserAuthenticated", "UserLoggedIn", "ZombieEvent", "OrderCreated")
      val unknown = info.unknownTags(dbTags)
      assertTrue(unknown == Set("ZombieEvent"))
    },
    test("unknownTags empty when all known") {
      val info    = TagResolver.resolve[AppEvent]
      val unknown = info.unknownTags(Set("UserAuthenticated", "OrderCreated"))
      assertTrue(unknown.isEmpty)
    },
    test("migrateValue transforms Variant via DynamicMigration") {
      val m        = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info     = TagResolver.resolve[AppEvent](m)
      val oldDv    = DynamicValue.Variant("UserLoggedIn", DynamicValue.Record(Chunk("id" -> DynamicValue.string("alice"))))
      val migrated = info.migrateValue(oldDv)
      assertTrue(
        migrated == Right(
          DynamicValue.Variant("UserAuthenticated", DynamicValue.Record(Chunk("id" -> DynamicValue.string("alice"))))
        )
      )
    },
    test("migrateValue leaves unrelated case unchanged") {
      val m        = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info     = TagResolver.resolve[AppEvent](m)
      val dv       = DynamicValue.Variant("OrderCreated", DynamicValue.Record(Chunk("orderId" -> DynamicValue.string("o1"))))
      val migrated = info.migrateValue(dv)
      assertTrue(migrated == Right(dv))
    },
    test("multiple RenameCase alias map correct") {
      val m = Migration
        .newBuilder[AppEvent, AppEvent]
        .renameCase("OldA", "UserAuthenticated")
        .renameCase("OldB", "OrderCreated")
        .build
      val info = TagResolver.resolve[AppEvent](m)
      assertTrue(
        info.aliases == Map("OldA" -> "UserAuthenticated", "OldB" -> "OrderCreated"),
        info.allTags.contains("OldA"),
        info.allTags.contains("OldB")
      )
    },
    test("TagResolver trait exposes alias and allTags") {
      val m        = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info     = TagResolver.resolve[AppEvent](m)
      val resolver = new TagResolver[AppEvent] { override def tagInfo: TagInfo = info }
      assertTrue(resolver.aliases == Map("UserLoggedIn" -> "UserAuthenticated"), resolver.allTags == info.allTags)
    },
    test("EventStore read with old tag decodes to new type") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info = TagResolver.resolve[AppEvent](m)
      withStoreTmp(info) { (store, tx, _) =>
        for {
          _   <- rawInsert(tx, "UserLoggedIn", """{"UserLoggedIn":{"id":"alice"}}""", "e1")
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.size == 1,
          all.head.tag == "UserAuthenticated",
          all.head.event == AppEvent.UserAuthenticated("alice")
        )
      }
    },
    test("EventStore read with mixed old and new tags returns all") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info = TagResolver.resolve[AppEvent](m)
      withStoreTmp(info) { (store, tx, _) =>
        for {
          _   <- rawInsert(tx, "UserLoggedIn", """{"UserLoggedIn":{"id":"alice"}}""", "e1")
          _   <- rawInsert(tx, "UserAuthenticated", """{"UserAuthenticated":{"id":"bob"}}""", "e2")
          _   <- rawInsert(tx, "OrderCreated", """{"OrderCreated":{"orderId":"o1"}}""", "e3")
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.size == 3,
          all.map(_.event).toSet == Set(
            AppEvent.UserAuthenticated("alice"),
            AppEvent.UserAuthenticated("bob"),
            AppEvent.OrderCreated("o1")
          )
        )
      }
    },
    test("EventStore selective read with current tag includes old-tagged rows") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info = TagResolver.resolve[AppEvent](m)
      withStoreTmp(info) { (store, tx, _) =>
        for {
          _        <- rawInsert(tx, "UserLoggedIn", """{"UserLoggedIn":{"id":"alice"}}""", "e1")
          _        <- rawInsert(tx, "UserAuthenticated", """{"UserAuthenticated":{"id":"bob"}}""", "e2")
          _        <- rawInsert(tx, "OrderCreated", """{"OrderCreated":{"orderId":"o1"}}""", "e3")
          filtered <- store.readAll(Set("UserAuthenticated")).runCollect
        } yield assertTrue(
          filtered.size == 2,
          filtered.forall(_.tag == "UserAuthenticated"),
          filtered.map(_.event).toSet == Set(AppEvent.UserAuthenticated("alice"), AppEvent.UserAuthenticated("bob"))
        )
      }
    },
    test("EventStore readFrom afterSeq with migration expands tags") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info = TagResolver.resolve[AppEvent](m)
      withStoreTmp(info) { (store, tx, _) =>
        for {
          _   <- rawInsert(tx, "UserLoggedIn", """{"UserLoggedIn":{"id":"a1"}}""", "e1")
          _   <- rawInsert(tx, "UserLoggedIn", """{"UserLoggedIn":{"id":"a2"}}""", "e2")
          _   <- rawInsert(tx, "OrderCreated", """{"OrderCreated":{"orderId":"o1"}}""", "e3")
          all <- store.readFrom(1L).runCollect
        } yield assertTrue(all.size == 2, all.head.tag == "UserAuthenticated" || all.head.tag == "OrderCreated")
      }
    },
    test("EventStore validateTags detects unknown tags and logs warning") {
      val info = TagResolver.resolve[AppEvent] // no old aliases, only current
      withStoreTmp(info) { (store, tx, _) =>
        for {
          _       <- rawInsert(tx, "ZombieEvent", """{"ZombieEvent":{}}""", "e1")
          _       <- rawInsert(tx, "UserAuthenticated", """{"UserAuthenticated":{"id":"ok"}}""", "e2")
          unknown <- ZIO.attemptBlocking(store.distinctTagsInDb().diff(info.allTags))
          _       <- store.validateTags()
        } yield assertTrue(unknown == Set("ZombieEvent"))
      }
    },
    test("EventStore allTags includes old names for startup query") {
      val m    = Migration.newBuilder[AppEvent, AppEvent].renameCase("OldCreated", "OrderCreated").build
      val info = TagResolver.resolve[AppEvent](m)
      withStoreTmp(info) { (store, tx, _) =>
        for {
          _   <- rawInsert(tx, "OldCreated", """{"OldCreated":{"orderId":"old1"}}""", "e1")
          all <- store.readAll().runCollect
        } yield assertTrue(all.size == 1, all.head.event == AppEvent.OrderCreated("old1"))
      }
    },
    test("chain of renames decodes oldest tag to newest") {
      val m = Migration
        .newBuilder[AppEvent, AppEvent]
        .renameCase("UserLoggedIn", "UserSignedIn")
        .renameCase("UserSignedIn", "UserAuthenticated")
        .build
      val info = TagResolver.resolve[AppEvent](m)
      withStoreTmp(info) { (store, tx, _) =>
        for {
          _   <- rawInsert(tx, "UserLoggedIn", """{"UserLoggedIn":{"id":"chain"}}""", "e1")
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.size == 1,
          all.head.tag == "UserAuthenticated",
          all.head.event == AppEvent.UserAuthenticated("chain")
        )
      }
    },
    test("TagResolver aliases via object helper") {
      val m     = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val alias = TagResolver.aliases[AppEvent](m)
      assertTrue(alias == Map("UserLoggedIn" -> "UserAuthenticated"))
    },
    test("TagResolver allTags via object helper") {
      val m   = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val all = TagResolver.allTags[AppEvent](m)
      assertTrue(all == Set("UserAuthenticated", "OrderCreated", "UserDeleted", "UserLoggedIn"))
    },
    test("TagResolver unknownTags object helper") {
      val m       = Migration.newBuilder[AppEvent, AppEvent].renameCase("UserLoggedIn", "UserAuthenticated").build
      val info    = TagResolver.resolve[AppEvent](m)
      val unknown = TagResolver.unknownTags(info, Set("UserLoggedIn", "UnknownX"))
      assertTrue(unknown == Set("UnknownX"))
    },
    test("no migration allTags equals current variant names") {
      val info = TagResolver.resolve[SimpleEvent]
      assertTrue(info.allTags == Set("Created", "Updated"))
    }
  )
}
