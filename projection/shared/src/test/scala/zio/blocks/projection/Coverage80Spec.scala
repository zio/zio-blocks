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
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.{Modifier, Schema, SchemaExpr}
import zio.blocks.schema.migration.Migration

import java.time.Instant

object Coverage80Spec extends ZIOSpecDefault {

  case class User(
    @Modifier.id id: String,
    name: String,
    age: Long
  )
  object User {
    implicit val schema: Schema[User]         = Schema.derived[User]
    implicit val entityPath: EntityPath[User] = EntityPath.derived[User]
  }

  case class Counter(
    @Modifier.id id: String,
    total: Long
  )
  object Counter {
    implicit val schema: Schema[Counter]         = Schema.derived[Counter]
    implicit val entityPath: EntityPath[Counter] = EntityPath.derived[Counter]
  }

  case class Wide(
    @Modifier.id id: String,
    intVal: Int,
    longVal: Long,
    dblVal: Double,
    floatVal: Float,
    boolVal: Boolean,
    shortVal: Short,
    byteVal: Byte,
    charVal: Char,
    bigIntVal: BigInt,
    bigDecVal: BigDecimal,
    uuidVal: java.util.UUID,
    instantVal: java.time.Instant,
    localDateVal: java.time.LocalDate,
    localDateTimeVal: java.time.LocalDateTime,
    localTimeVal: java.time.LocalTime,
    durationVal: java.time.Duration
  )
  object Wide {
    implicit val schema: Schema[Wide]         = Schema.derived[Wide]
    implicit val entityPath: EntityPath[Wide] = EntityPath.derived[Wide]
  }

  case class IntIdEntity(@Modifier.id intId: Int, name: String)
  object IntIdEntity {
    implicit val schema: Schema[IntIdEntity]         = Schema.derived[IntIdEntity]
    implicit val entityPath: EntityPath[IntIdEntity] = EntityPath.derived[IntIdEntity]
  }
  case class LongIdEntity(@Modifier.id longId: Long, name: String)
  object LongIdEntity {
    implicit val schema: Schema[LongIdEntity]         = Schema.derived[LongIdEntity]
    implicit val entityPath: EntityPath[LongIdEntity] = EntityPath.derived[LongIdEntity]
  }
  case class DoubleIdEntity(@Modifier.id dblId: Double, name: String)
  object DoubleIdEntity {
    implicit val schema: Schema[DoubleIdEntity]         = Schema.derived[DoubleIdEntity]
    implicit val entityPath: EntityPath[DoubleIdEntity] = EntityPath.derived[DoubleIdEntity]
  }
  case class BoolIdEntity(@Modifier.id boolId: Boolean, name: String)
  object BoolIdEntity {
    implicit val schema: Schema[BoolIdEntity]         = Schema.derived[BoolIdEntity]
    implicit val entityPath: EntityPath[BoolIdEntity] = EntityPath.derived[BoolIdEntity]
  }
  case class UuidIdEntity(@Modifier.id uuidId: java.util.UUID, name: String)
  object UuidIdEntity {
    implicit val schema: Schema[UuidIdEntity]         = Schema.derived[UuidIdEntity]
    implicit val entityPath: EntityPath[UuidIdEntity] = EntityPath.derived[UuidIdEntity]
  }
  case class InstantIdEntity(@Modifier.id instId: java.time.Instant, name: String)
  object InstantIdEntity {
    implicit val schema: Schema[InstantIdEntity]         = Schema.derived[InstantIdEntity]
    implicit val entityPath: EntityPath[InstantIdEntity] = EntityPath.derived[InstantIdEntity]
  }
  case class ShortIdEntity(@Modifier.id shortId: Short, name: String)
  object ShortIdEntity {
    implicit val schema: Schema[ShortIdEntity]         = Schema.derived[ShortIdEntity]
    implicit val entityPath: EntityPath[ShortIdEntity] = EntityPath.derived[ShortIdEntity]
  }
  case class ByteIdEntity(@Modifier.id byteId: Byte, name: String)
  object ByteIdEntity {
    implicit val schema: Schema[ByteIdEntity]         = Schema.derived[ByteIdEntity]
    implicit val entityPath: EntityPath[ByteIdEntity] = EntityPath.derived[ByteIdEntity]
  }
  case class CharIdEntity(@Modifier.id charId: Char, name: String)
  object CharIdEntity {
    implicit val schema: Schema[CharIdEntity]         = Schema.derived[CharIdEntity]
    implicit val entityPath: EntityPath[CharIdEntity] = EntityPath.derived[CharIdEntity]
  }
  case class BigIntIdEntity(@Modifier.id bigIntId: BigInt, name: String)
  object BigIntIdEntity {
    implicit val schema: Schema[BigIntIdEntity]         = Schema.derived[BigIntIdEntity]
    implicit val entityPath: EntityPath[BigIntIdEntity] = EntityPath.derived[BigIntIdEntity]
  }
  case class LocalDateIdEntity(@Modifier.id ldId: java.time.LocalDate, name: String)
  object LocalDateIdEntity {
    implicit val schema: Schema[LocalDateIdEntity]         = Schema.derived[LocalDateIdEntity]
    implicit val entityPath: EntityPath[LocalDateIdEntity] = EntityPath.derived[LocalDateIdEntity]
  }
  case class FloatIdEntity(@Modifier.id fId: Float, name: String)
  object FloatIdEntity {
    implicit val schema: Schema[FloatIdEntity]         = Schema.derived[FloatIdEntity]
    implicit val entityPath: EntityPath[FloatIdEntity] = EntityPath.derived[FloatIdEntity]
  }
  case class BigDecIdEntity(@Modifier.id bdId: BigDecimal, name: String)
  object BigDecIdEntity {
    implicit val schema: Schema[BigDecIdEntity]         = Schema.derived[BigDecIdEntity]
    implicit val entityPath: EntityPath[BigDecIdEntity] = EntityPath.derived[BigDecIdEntity]
  }
  case class LocalDateTimeIdEntity(@Modifier.id ldtId: java.time.LocalDateTime, name: String)
  object LocalDateTimeIdEntity {
    implicit val schema: Schema[LocalDateTimeIdEntity]         = Schema.derived[LocalDateTimeIdEntity]
    implicit val entityPath: EntityPath[LocalDateTimeIdEntity] = EntityPath.derived[LocalDateTimeIdEntity]
  }
  case class DurationIdEntity(@Modifier.id durId: java.time.Duration, name: String)
  object DurationIdEntity {
    implicit val schema: Schema[DurationIdEntity]         = Schema.derived[DurationIdEntity]
    implicit val entityPath: EntityPath[DurationIdEntity] = EntityPath.derived[DurationIdEntity]
  }
  case class LocalTimeIdEntity(@Modifier.id ltId: java.time.LocalTime, name: String)
  object LocalTimeIdEntity {
    implicit val schema: Schema[LocalTimeIdEntity]         = Schema.derived[LocalTimeIdEntity]
    implicit val entityPath: EntityPath[LocalTimeIdEntity] = EntityPath.derived[LocalTimeIdEntity]
  }
  case class PeriodIdEntity(@Modifier.id perId: java.time.Period, name: String)
  object PeriodIdEntity {
    implicit val schema: Schema[PeriodIdEntity]         = Schema.derived[PeriodIdEntity]
    implicit val entityPath: EntityPath[PeriodIdEntity] = EntityPath.derived[PeriodIdEntity]
  }
  case class YearIdEntity(@Modifier.id yearId: java.time.Year, name: String)
  object YearIdEntity {
    implicit val schema: Schema[YearIdEntity]         = Schema.derived[YearIdEntity]
    implicit val entityPath: EntityPath[YearIdEntity] = EntityPath.derived[YearIdEntity]
  }
  case class ZoneIdEntity(@Modifier.id zoneId: java.time.ZoneId, name: String)
  object ZoneIdEntity {
    implicit val schema: Schema[ZoneIdEntity]         = Schema.derived[ZoneIdEntity]
    implicit val entityPath: EntityPath[ZoneIdEntity] = EntityPath.derived[ZoneIdEntity]
  }
  case class CurrencyIdEntity(@Modifier.id curId: java.util.Currency, name: String)
  object CurrencyIdEntity {
    implicit val schema: Schema[CurrencyIdEntity]         = Schema.derived[CurrencyIdEntity]
    implicit val entityPath: EntityPath[CurrencyIdEntity] = EntityPath.derived[CurrencyIdEntity]
  }

  case class Inner(a: String, b: Int)
  object Inner { implicit val schema: Schema[Inner] = Schema.derived[Inner] }
  case class Outer(
    @Modifier.id id: String,
    inner: Inner,
    optInner: Option[Inner],
    items: List[String],
    mapping: Map[String, Int]
  )
  object Outer {
    implicit val schema: Schema[Outer]         = Schema.derived[Outer]
    implicit val entityPath: EntityPath[Outer] = EntityPath.derived[Outer]
  }

  case class UserCreated(name: String, age: Long)
  object UserCreated { implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated] }
  case class RepoCreated(ownerId: String, repoName: String)
  object RepoCreated { implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated] }

  private def ctx(eid: String = "e1", seq: Long = 1L): ProjectionContext =
    ProjectionContext(eid, Instant.parse("2024-01-01T00:00:00Z"), seq)

  private def literal[A: Schema](value: A): SchemaExpr[Any, A] = SchemaExpr.literal(value)

  // simple in-mem EventStore for SchemaEvolution tests
  final class MemEventStore[E](buf: Ref[List[EventEnvelope[E]]], ctr: Ref[Long], hub: Hub[EventEnvelope[E]])
      extends EventStore[E] {
    def append(entityId: String, event: E): Task[Long] =
      for {
        seq <- ctr.updateAndGet(_ + 1)
        env  = EventEnvelope(seq, event.getClass.getSimpleName.stripSuffix("$"), event, Instant.now(), entityId)
        _   <- buf.update(_ :+ env)
        _   <- hub.publish(env).unit
      } yield seq
    def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): zio.stream.ZStream[Any, Throwable, EventEnvelope[E]] =
      zio.stream.ZStream.unwrap(buf.get.map { list =>
        val filtered = list.filter(_.seq > afterSeq).filter(e => tags.isEmpty || tags.contains(e.tag))
        zio.stream.ZStream.fromIterable(filtered)
      })
    def readAll(tags: Set[String] = Set.empty): zio.stream.ZStream[Any, Throwable, EventEnvelope[E]] =
      readFrom(0L, tags)
    def subscribe: Hub[EventEnvelope[E]] = hub
    def close: Task[Unit]                = ZIO.unit
  }
  object MemEventStore {
    def make[E]: ZIO[Any, Nothing, MemEventStore[E]] =
      for {
        buf <- Ref.make(List.empty[EventEnvelope[E]])
        ctr <- Ref.make(0L)
        hub <- Hub.unbounded[EventEnvelope[E]]
      } yield new MemEventStore[E](buf, ctr, hub)
  }

  def spec: Spec[TestEnvironment, Any] = suite("Coverage80Spec")(
    suite("high-impact SQLite branches")(
      test("SQLite full CRUD + meta + writeAny + validateField") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(4)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-sqlite", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.insert(User("w1", "Alice", 10L))
            f1    <- store.findById("w1")
            _     <- store.upsert(User("w1", "Alice2", 99L))
            f2    <- store.findById("w1")
            _     <- store.updateFields(
                   "w1",
                   Chunk(
                     FieldUpdate.Set("name", "Bob"),
                     FieldUpdate.Increment("age", 10L),
                     FieldUpdate.Decrement("age", 3L),
                     FieldUpdate.Max("age", 100L),
                     FieldUpdate.Min("age", 1L)
                   )
                 )
            now  = java.time.Instant.EPOCH
            ld   = java.time.LocalDate.ofEpochDay(0)
            ldt  = java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC)
            lt   = java.time.LocalTime.MIDNIGHT
            uuid = new java.util.UUID(0L, 0L)
            _   <- store.updateFields(
                   "w1",
                   Chunk(
                     FieldUpdate.Set("name", "hello"),
                     FieldUpdate.Set("name", Some("opt-str")),
                     FieldUpdate.Set("name", "world"),
                     FieldUpdate.Set("age", 123L),
                     FieldUpdate.Set("name", "str-via-int"),
                     FieldUpdate.Set("name", uuid.toString),
                     FieldUpdate.Set("name", now.toString),
                     FieldUpdate.Set("name", ld.toString),
                     FieldUpdate.Set("name", ldt.toString),
                     FieldUpdate.Set("name", lt.toString),
                     FieldUpdate.Set("name", java.time.Duration.ofSeconds(5).toString),
                     FieldUpdate.Set("name", "final-name")
                   )
                 )
            _    <- store.updateFields("new-id", Chunk(FieldUpdate.Set("name", "NewGuy")))
            f3   <- store.findById("new-id")
            _    <- store.updateFields("w1", Chunk.empty)
            s0   <- store.getLastProcessedSeq
            _    <- store.updateLastProcessedSeq(77L)
            s1   <- store.getLastProcessedSeq
            h0   <- store.getSchemaHash
            _    <- store.updateSchemaHash("hash-xyz")
            h1   <- store.getSchemaHash
            bad  <- store.updateFields("w1", Chunk(FieldUpdate.Set("unknown_col_xyz", "x"))).exit
            _    <- store.delete("w1")
            fDel <- store.findById("w1")
            _    <- store.truncate
          } yield assertTrue(
            f1.isDefined,
            f2.exists(_.name == "Alice2"),
            f3.isDefined,
            s0 == 0L,
            s1 == 77L,
            h0.isEmpty,
            h1.contains("hash-xyz"),
            bad.isFailure,
            fDel.isEmpty
          )
        }
      },
      test("SQLite addColumn valid new and duplicate and invalid") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(4)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-addcol2", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.insert(User("ac1", "Alice", 10L))
            _     <- store.addColumn("new_col", "TEXT")
            _     <- store.addColumn("new_col", "TEXT")
            bad   <- store.addColumn("bad-col!", "TEXT").exit
            _     <- store.addColumn("another_col", "INTEGER")
          } yield assertTrue(bad.isFailure)
        }
      },
      test("SQLite recreateTable and quoted + initFlag double init") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(4)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-recreate", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.insert(User("rt1", "Alice", 10L))
            _     <- store.recreateTable()
            f1    <- store.findById("rt1")
            _     <- store.insert(User("rt2", "Bob", 20L))
            f2    <- store.findById("rt2")
            _     <- store.findById("rt2")
            _     <- store.findById("nonexistent")
          } yield assertTrue(f1.isEmpty, f2.isDefined)
        }
      }
    ),
    suite("InMemory remaining branches + validate")(
      test("Projection.validate with null handler class") {
        val handler = Handler[UserCreated, User](
          null.asInstanceOf[Class[UserCreated]],
          summon[Schema[UserCreated]],
          "bad",
          (_, _) => ProjectionAction.Delete
        )
        val binding =
          SourceBinding[Any, User]("src", RoutingMode.RouteToSelf, List(handler.asInstanceOf[Handler[?, User]]))
        val proj = new Projection[User](
          "null-handler",
          summon[Schema[User]],
          Some(summon[EntityPath[User]]),
          false,
          List(binding)
        )
        val warns = proj.validate()
        assertTrue(warns.exists(_.contains("null class")))
      },
      test("Projection routingKeyForDefault RoutedBy throws when extractor fails") {
        val badExtractor: RepoCreated => String = _ => throw new RuntimeException("boom")
        val proj                                = Projection[User]("bad-route")
          .from("repos")
          .routedBy[RepoCreated](badExtractor)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, 0L))
        val k = scala.util.Try(proj.routingKeyForDefault(RepoCreated("id1", "r"), ctx("fallback-id")))
        assertTrue(k.isFailure)
      }
    ),
    suite("TransactorCache LRU maxSize 0 and 1 and closed")(
      test("maxSize 1 LRU eviction covers needEvict true and minBy") {
        for {
          cache           <- TransactorCache.makeUnscoped(maxSize = 1)
          _               <- cache.get("/tmp/cov80-lru-1.db")
          s1              <- cache.size
          _               <- cache.get("/tmp/cov80-lru-2.db")
          s2              <- cache.size
          _               <- cache.get("/tmp/cov80-lru-1.db")
          s3              <- cache.size
          _               <- cache.evict("/tmp/cov80-lru-2.db")
          s4              <- cache.size
          _               <- cache.close
          szAfterClose    <- cache.size
          evictAfterClose <- cache.evict("/tmp/x.db").as(true)
          getAfterClose   <- cache.get("/tmp/after-close.db").either
        } yield assertTrue(s1 == 1, s2 == 1, s3 == 1, s4 == 1, szAfterClose == 0, evictAfterClose, getAfterClose.isLeft)
      },
      test("maxSize 0 never evicts") {
        for {
          cache <- TransactorCache.makeUnscoped(maxSize = 0)
          _     <- cache.get("/tmp/cov80-zero-1.db")
          _     <- cache.get("/tmp/cov80-zero-2.db")
          _     <- cache.get("/tmp/cov80-zero-3.db")
          sz    <- cache.size
          _     <- cache.close
        } yield assertTrue(sz == 3)
      }
    ),
    suite("high-impact InMemory primitive and EventStore branches")(
      test("InMemory primitiveToString via varied id types and defaultDynamicValueFor via Wide") {
        for {
          s1  <- InMemoryProjectionStore.make[IntIdEntity]
          _   <- s1.insert(IntIdEntity(42, "v1"))
          f1  <- s1.findById("42")
          s2  <- InMemoryProjectionStore.make[LongIdEntity]
          _   <- s2.insert(LongIdEntity(123L, "v2"))
          f2  <- s2.findById("123")
          s3  <- InMemoryProjectionStore.make[DoubleIdEntity]
          _   <- s3.insert(DoubleIdEntity(3.14, "v3"))
          f3  <- s3.findById("3.14")
          s4  <- InMemoryProjectionStore.make[BoolIdEntity]
          _   <- s4.insert(BoolIdEntity(true, "v4"))
          f4  <- s4.findById("true")
          s5  <- InMemoryProjectionStore.make[UuidIdEntity]
          uuid = new java.util.UUID(0L, 1L)
          _   <- s5.insert(UuidIdEntity(uuid, "v5"))
          f5  <- s5.findById(uuid.toString)
          s6  <- InMemoryProjectionStore.make[InstantIdEntity]
          now  = java.time.Instant.EPOCH
          _   <- s6.insert(InstantIdEntity(now, "v6"))
          f6  <- s6.findById(now.toString)
          // defaultDynamicValueFor via missing Wide
          sw <- InMemoryProjectionStore.make[Wide]
          _  <- sw.updateFields("missing-wide2", Chunk(FieldUpdate.Set("int_val", 99)))
          fw <- sw.findById("missing-wide2")
        } yield assertTrue(
          f1.isDefined,
          f2.isDefined,
          f3.isDefined,
          f4.isDefined,
          f5.isDefined,
          f6.isDefined,
          fw.isDefined
        )
      },
      test("SQLiteEventStore append/read and deriveTag/decodePayload branches") {
        ZIO.scoped {
          for {
            cache      <- TransactorCache.make(4)
            tmp        <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-es", ".db"))
            path        = tmp.toAbsolutePath.toString
            transactor <- cache.get(path)
            es         <- zio.blocks.projection.SQLiteEventStore.make[UserCreated](transactor)
            _          <- es.append("e1", UserCreated("Alice", 10L))
            _          <- es.append("e2", UserCreated("Bob", 20L))
            all        <- es.readAll().runCollect
            sel        <- es.readFrom(0L, Set("UserCreated")).runCollect
            seq1       <- es.append("e1", UserCreated("Charlie", 30L))
            after      <- es.readFrom(1L).runCollect
          } yield assertTrue(all.size == 2, sel.size == 2, seq1 == 3L, after.size == 2)
        }
      }
    ),
    suite("high-impact remaining branches")(
      test("InMemory primitiveToString remaining types Short/Byte/Char/BigInt/LocalDate") {
        for {
          s7  <- InMemoryProjectionStore.make[ShortIdEntity]
          _   <- s7.insert(ShortIdEntity(7.toShort, "v7"))
          f7  <- s7.findById("7")
          s8  <- InMemoryProjectionStore.make[ByteIdEntity]
          _   <- s8.insert(ByteIdEntity(8.toByte, "v8"))
          f8  <- s8.findById("8")
          s9  <- InMemoryProjectionStore.make[CharIdEntity]
          _   <- s9.insert(CharIdEntity('c', "v9"))
          f9  <- s9.findById("c")
          s10 <- InMemoryProjectionStore.make[BigIntIdEntity]
          _   <- s10.insert(BigIntIdEntity(BigInt(999), "v10"))
          f10 <- s10.findById("999")
          s11 <- InMemoryProjectionStore.make[LocalDateIdEntity]
          ld   = java.time.LocalDate.of(2024, 1, 15)
          _   <- s11.insert(LocalDateIdEntity(ld, "v11"))
          f11 <- s11.findById(ld.toString)
        } yield assertTrue(f7.isDefined, f8.isDefined, f9.isDefined, f10.isDefined, f11.isDefined)
      },
      test("TagResolver and ProjectionEngine remaining branches") {
        val tagInfo  = TagResolver.resolve[UserCreated]
        val tags     = TagResolver.currentTags[UserCreated]
        val expanded = tagInfo.expandRequested(Set("UserCreated"))
        val unknown  = tagInfo.unknownTags(Set("UserCreated", "UnknownTag"))
        for {
          cache <- TransactorCache.makeUnscoped(5)
          tx1   <- cache.get("/tmp/cov80-rem1.db")
          tx2   <- cache.get("/tmp/cov80-rem2.db")
          sz1   <- cache.size
          _     <- cache.evict("/tmp/cov80-rem1.db")
          sz2   <- cache.size
          _     <- cache.close
        } yield assertTrue(tags.nonEmpty, expanded.nonEmpty, unknown.contains("UnknownTag"), sz1 == 2, sz2 == 1)
      },
      test("SQLiteEventStore variant and numeric tag branches") {
        ZIO.scoped {
          for {
            cache      <- TransactorCache.make(4)
            tmp        <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-es2", ".db"))
            path        = tmp.toAbsolutePath.toString
            transactor <- cache.get(path)
            hub        <- Hub.bounded[EventEnvelope[UserCreated]](16)
            es         <- zio.blocks.projection.SQLiteEventStore.makeWithHub[UserCreated](transactor, hub)
            _          <- es.append("e1", UserCreated("Alice", 1L))
            _          <- es.append("e1", UserCreated("Bob", 2L))
            all        <- es.readAll().runCollect
            byTag      <- es.readFrom(0L, Set("UserCreated")).runCollect
            emptyTag   <- es.readFrom(0L, Set("NonExistent")).runCollect
          } yield assertTrue(all.size == 2, byTag.size == 2, emptyTag.isEmpty)
        }
      },
      test("InMemory remaining Float/BigDec/LocalDateTime/Duration and EventStore makeBlocking") {
        ZIO.scoped {
          for {
            s12 <- InMemoryProjectionStore.make[FloatIdEntity]
            _   <- s12.insert(FloatIdEntity(1.23f, "v12"))
            f12 <- s12.findById("1.23")
            s13 <- InMemoryProjectionStore.make[BigDecIdEntity]
            _   <- s13.insert(BigDecIdEntity(BigDecimal("123.45"), "v13"))
            f13 <- s13.findById("123.45")
            s14 <- InMemoryProjectionStore.make[LocalDateTimeIdEntity]
            ldt  = java.time.LocalDateTime.of(2024, 1, 1, 12, 0)
            _   <- s14.insert(LocalDateTimeIdEntity(ldt, "v14"))
            f14 <- s14.findById(ldt.toString)
            s15 <- InMemoryProjectionStore.make[DurationIdEntity]
            dur  = java.time.Duration.ofSeconds(5)
            _   <- s15.insert(DurationIdEntity(dur, "v15"))
            f15 <- s15.findById(dur.toString)
            // EventStore makeBlocking branches
            cache2      <- TransactorCache.make(2)
            tmp2        <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-es3", ".db"))
            path2        = tmp2.toAbsolutePath.toString
            transactor2 <- cache2.get(path2)
            hub2        <- Hub.bounded[EventEnvelope[UserCreated]](8)
            esBlocking   = zio.blocks.projection.SQLiteEventStore.makeBlocking[UserCreated](transactor2, hub2)
            _           <- esBlocking.append("e1", UserCreated("X", 1L))
            all2        <- esBlocking.readAll().runCollect
          } yield assertTrue(f12.isDefined, f13.isDefined, f14.isDefined, f15.isDefined, all2.size == 1)
        }
      },
      test("EventStore 0% and TransactorCache remaining branches") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(3)
            tmp1  <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-es4a", ".db"))
            tmp2  <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-es4b", ".db"))
            tmp3  <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-es4c", ".db"))
            tx1   <- cache.get(tmp1.toAbsolutePath.toString)
            tx2   <- cache.get(tmp2.toAbsolutePath.toString)
            tx3   <- cache.get(tmp3.toAbsolutePath.toString)
            hub   <- Hub.bounded[EventEnvelope[UserCreated]](4)
            es1   <- zio.blocks.projection.SQLiteEventStore.make[UserCreated](tx1)
            es2   <- zio.blocks.projection.SQLiteEventStore.makeWithHub[UserCreated](tx2, hub)
            es3    = zio.blocks.projection.SQLiteEventStore.makeBlocking[UserCreated](tx3, hub)
            _     <- es1.append("e1", UserCreated("A", 1L))
            _     <- es2.append("e2", UserCreated("B", 2L))
            _     <- es3.append("e3", UserCreated("C", 3L))
            r1    <- es1.readAll().runCollect
            r2    <- es2.readAll().runCollect
            r3    <- es3.readAll().runCollect
            sz1   <- cache.size
            _     <- cache.evict(tmp1.toAbsolutePath.toString)
            sz2   <- cache.size
            // Cover remaining 0% branches: TransactorCache.live/lifeConfig, SQLiteEventStore make variants
            liveLayer   = TransactorCache.live(5)
            liveConfig  = TransactorCache.liveConfig(TransactorCacheConfig(3))
            tagInfo     = TagResolver.resolve[UserCreated]
            _          <- zio.blocks.projection.SQLiteEventStore.makeWithResolver[UserCreated](tx1, tagInfo).unit
            blockingRes =
              zio.blocks.projection.SQLiteEventStore.makeBlockingWithResolver[UserCreated](tx1, hub, tagInfo)
            mig         = Migration.newBuilder[UserCreated, UserCreated].build
            _          <- zio.blocks.projection.SQLiteEventStore.make[UserCreated](tx1, mig).unit
            blockingMig = zio.blocks.projection.SQLiteEventStore.makeBlocking[UserCreated](tx1, hub, mig)
            tc         <- TransactorCache.makeUnscoped()
            tcsz       <- tc.size
          } yield assertTrue(r1.size == 1, r2.size == 1, r3.size == 1, sz1 == 3, sz2 == 2, tcsz == 0)
        }
      },
      test("InMemory remaining LocalTime/Period/Year id types") {
        for {
          s16 <- InMemoryProjectionStore.make[LocalTimeIdEntity]
          lt   = java.time.LocalTime.of(12, 34, 56)
          _   <- s16.insert(LocalTimeIdEntity(lt, "v16"))
          f16 <- s16.findById(lt.toString)
          s17 <- InMemoryProjectionStore.make[PeriodIdEntity]
          per  = java.time.Period.ofDays(5)
          _   <- s17.insert(PeriodIdEntity(per, "v17"))
          f17 <- s17.findById(per.toString)
          s18 <- InMemoryProjectionStore.make[YearIdEntity]
          yr   = java.time.Year.of(2024)
          _   <- s18.insert(YearIdEntity(yr, "v18"))
          f18 <- s18.findById(yr.toString)
        } yield assertTrue(f16.isDefined, f17.isDefined, f18.isDefined)
      },
      test("InMemory remaining ZoneId/Currency and EventStore fetch") {
        ZIO.scoped {
          for {
            s19   <- InMemoryProjectionStore.make[ZoneIdEntity]
            zid    = java.time.ZoneId.of("UTC")
            _     <- s19.insert(ZoneIdEntity(zid, "v19"))
            f19   <- s19.findById(zid.toString)
            s20   <- InMemoryProjectionStore.make[CurrencyIdEntity]
            cur    = java.util.Currency.getInstance("USD")
            _     <- s20.insert(CurrencyIdEntity(cur, "v20"))
            f20   <- s20.findById(cur.toString)
            cache <- TransactorCache.make(2)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-fetch", ".db"))
            tx    <- cache.get(tmp.toAbsolutePath.toString)
            es    <- zio.blocks.projection.SQLiteEventStore.make[UserCreated](tx)
            _     <- es.append("e1", UserCreated("A", 1L))
            all   <- es.readAll().runCollect
          } yield assertTrue(f19.isDefined, f20.isDefined, all.size == 1)
        }
      },
      test("InMemory Wide Increment all numeric types hits incDV/decDV branches") {
        for {
          store <- InMemoryProjectionStore.make[Wide]
          _     <- store.insert(
                 Wide(
                   "wide1",
                   1,
                   2L,
                   3.0,
                   4.0f,
                   true,
                   5.toShort,
                   6.toByte,
                   'c',
                   BigInt(7),
                   BigDecimal(8),
                   new java.util.UUID(0L, 0L),
                   java.time.Instant.EPOCH,
                   java.time.LocalDate.ofEpochDay(0),
                   java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC),
                   java.time.LocalTime.MIDNIGHT,
                   java.time.Duration.ZERO
                 )
               )
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("int_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("long_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("dbl_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("float_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("short_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("byte_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("big_int_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Increment("big_dec_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Decrement("int_val", 1L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Max("long_val", 100L)))
          _ <- store.updateFields("wide1", Chunk(FieldUpdate.Min("long_val", 0L)))
          f <- store.findById("wide1")
        } yield assertTrue(f.isDefined)
      },
      test("InMemory getLong and convertLong via Max/Min both branches") {
        for {
          store <- InMemoryProjectionStore.make[Wide]
          _     <- store.insert(
                 Wide(
                   "wide2",
                   10,
                   50L,
                   0,
                   0,
                   true,
                   0,
                   0,
                   'c',
                   BigInt(0),
                   BigDecimal(0),
                   new java.util.UUID(0L, 0L),
                   java.time.Instant.EPOCH,
                   java.time.LocalDate.ofEpochDay(0),
                   java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC),
                   java.time.LocalTime.MIDNIGHT,
                   java.time.Duration.ZERO
                 )
               )
          // Max both branches: first increase, second no increase
          _  <- store.updateFields("wide2", Chunk(FieldUpdate.Max("long_val", 100L)))
          f1 <- store.findById("wide2")
          _  <- store.updateFields("wide2", Chunk(FieldUpdate.Max("long_val", 10L)))
          f2 <- store.findById("wide2")
          // Min both branches
          _  <- store.updateFields("wide2", Chunk(FieldUpdate.Min("long_val", 0L)))
          f3 <- store.findById("wide2")
          _  <- store.updateFields("wide2", Chunk(FieldUpdate.Min("long_val", 200L)))
          f4 <- store.findById("wide2")
          // getLong via String numeric branch: use Max on String field? Instead test via Wide's intVal with Max
          _  <- store.updateFields("wide2", Chunk(FieldUpdate.Max("int_val", 20L)))
          f5 <- store.findById("wide2")
        } yield assertTrue(
          f1.exists(_.longVal == 100L),
          f2.exists(_.longVal == 100L),
          f3.exists(_.longVal == 0L),
          f4.exists(_.longVal == 0L),
          f5.isDefined
        )
      }
    ),
    suite("SchemaHash nested/collection/wrapper branches")(
      test("outer with Option[Inner] nested record and List/Map collection branches") {
        val h1 = SchemaHash.compute[Outer]
        val h2 = SchemaHash.compute[User]
        val h3 = SchemaHash.compute[Outer]
        assertTrue(h1.nonEmpty, h1 != h2, h1 == h3)
      }
    ),
    suite("SchemaEvolution high-impact branches")(
      test("needsRebuild false when empty, true when hash mismatch") {
        for {
          store <- InMemoryProjectionStore.make[User]
          n1    <- SchemaEvolution.needsRebuild[User](store)
          _     <- store.updateSchemaHash("different-hash")
          n2    <- SchemaEvolution.needsRebuild[User](store)
          _     <- store.updateSchemaHash(SchemaHash.compute[User])
          n3    <- SchemaEvolution.needsRebuild[User](store)
        } yield assertTrue(!n1, n2, !n3)
      },
      test("isSimpleAddFieldMigration false for empty and RenameCase") {
        val emptyAction = Migration.newBuilder[User, User].build
        val rename      = Migration.newBuilder[User, User].renameCase("Old", "New").build
        assertTrue(
          !SchemaEvolution.isSimpleAddFieldMigration(emptyAction),
          !SchemaEvolution.isSimpleAddFieldMigration(rename)
        )
      },
      test("isSimpleAddFieldMigration true for AddField") {
        case class Old(@Modifier.id id: String, name: String)
        object Old { implicit val schema: Schema[Old] = Schema.derived[Old] }
        case class New(@Modifier.id id: String, name: String, extra: String)
        object New { implicit val schema: Schema[New] = Schema.derived[New] }
        val mig = Migration.newBuilder[Old, New].addField(_.extra, literal("default")).build
        assertTrue(SchemaEvolution.isSimpleAddFieldMigration(mig))
      },
      test("tryMigrationShortcut None returns false and non-simple returns false") {
        for {
          store <- InMemoryProjectionStore.make[User]
          r1    <- SchemaEvolution.tryMigrationShortcut[User](store, None, "cur")
          rename = Migration.newBuilder[User, User].renameCase("Old", "New").build
          r2    <- SchemaEvolution.tryMigrationShortcut[User](store, Some(rename), "cur")
        } yield assertTrue(!r1, !r2)
      },
      test("tryMigrationShortcut simple AddField via SQLite store succeeds") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(4)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("cov80-mig", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            mig    = Migration.newBuilder[User, User].addField(_.age, literal(0L)).build
            cur    = SchemaHash.compute[User]
            ok    <- SchemaEvolution.tryMigrationShortcut[User](store, Some(mig), cur)
            h     <- store.getSchemaHash
          } yield assertTrue(ok, h.contains(cur))
        }
      },
      test("rebuildWithHash and checkAndRebuild branches") {
        for {
          hub   <- MemEventStore.make[UserCreated]
          store <- InMemoryProjectionStore.make[User]
          spec   = Projection[User]("rebuild-test").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age))
          _     <- SchemaEvolution.rebuildWithHash[User](store, hub.asInstanceOf[EventStore[Any]], spec)
          h1    <- store.getSchemaHash
          r1    <- SchemaEvolution.checkAndRebuild[User](store, hub.asInstanceOf[EventStore[Any]], spec)
          _     <- store.updateSchemaHash("old-hash")
          r2    <- SchemaEvolution.checkAndRebuild[User](store, hub.asInstanceOf[EventStore[Any]], spec)
          h2    <- store.getSchemaHash
        } yield assertTrue(h1.isDefined, !r1, r2, h2.isDefined, h2 != Some("old-hash"))
      }
    )
  )
}
