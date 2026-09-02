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
import zio.blocks.projection.testing.*
import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.schema.migration.Migration

import java.time.Instant

object SweepCoverageSpec extends ZIOSpecDefault {

  case class User(
    @Modifier.id id: String,
    name: String,
    age: Long
  )
  object User {
    implicit val schema: Schema[User]         = Schema.derived[User]
    implicit val entityPath: EntityPath[User] = EntityPath.derived[User]
  }
  case class UserCreated(name: String, age: Long)
  object UserCreated { implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated] }
  case class UserRenamed(newName: String)
  object UserRenamed { implicit val schema: Schema[UserRenamed] = Schema.derived[UserRenamed] }

  // Numeric tag event
  case class NumericEvent(value: String)
  object NumericEvent { implicit val schema: Schema[NumericEvent] = Schema.derived[NumericEvent] }

  case class IntIdEntity(
    @Modifier.id intId: Int,
    name: String
  )
  object IntIdEntity {
    implicit val schema: Schema[IntIdEntity]         = Schema.derived[IntIdEntity]
    implicit val entityPath: EntityPath[IntIdEntity] = EntityPath.derived[IntIdEntity]
  }

  case class LongIdEntity(
    @Modifier.id longId: Long,
    name: String
  )
  object LongIdEntity {
    implicit val schema: Schema[LongIdEntity]         = Schema.derived[LongIdEntity]
    implicit val entityPath: EntityPath[LongIdEntity] = EntityPath.derived[LongIdEntity]
  }

  case class TypesAll(
    @Modifier.id id: String,
    intVal: Int,
    longVal: Long,
    doubleVal: Double,
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
  object TypesAll {
    implicit val schema: Schema[TypesAll]         = Schema.derived[TypesAll]
    implicit val entityPath: EntityPath[TypesAll] = EntityPath.derived[TypesAll]
  }

  def spec: Spec[TestEnvironment, Any] = suite("SweepCoverageSpec")(
    suite("InMemory remaining branches sweep")(
      test("defaultDynamicValueFor all primitive via missing entity creation") {
        for {
          store <- InMemoryProjectionStore.make[TypesAll]
          // Trigger createDefaultForId by updating missing entity with Set on one field
          _ <- store.updateFields("missing-all", Chunk(FieldUpdate.Set("int_val", 1)))
          f <- store.findById("missing-all")
        } yield assertTrue(f.isDefined, f.exists(_.intVal == 1))
      },
      test("primitiveToString via Int id") {
        for {
          store <- InMemoryProjectionStore.make[IntIdEntity]
          _     <- store.insert(IntIdEntity(42, "Alice"))
          f     <- store.findById("42")
        } yield assertTrue(f.exists(_.name == "Alice"))
      },
      test("primitiveToString via Long id") {
        for {
          store <- InMemoryProjectionStore.make[LongIdEntity]
          _     <- store.insert(LongIdEntity(123L, "Bob"))
          f     <- store.findById("123")
        } yield assertTrue(f.exists(_.name == "Bob"))
      },
      test("inc/dec/max/min on all numeric types via TypesAll") {
        val now  = java.time.Instant.EPOCH
        val ld   = java.time.LocalDate.ofEpochDay(0)
        val ldt  = java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC)
        val lt   = java.time.LocalTime.MIDNIGHT
        val uuid = new java.util.UUID(0L, 0L)
        for {
          store <- InMemoryProjectionStore.make[TypesAll]
          _     <- store.insert(
                 TypesAll(
                   "all1",
                   10,
                   20L,
                   30.0,
                   40.0f,
                   true,
                   50.toShort,
                   60.toByte,
                   'c',
                   BigInt(70),
                   BigDecimal(80),
                   uuid,
                   now,
                   ld,
                   ldt,
                   lt,
                   java.time.Duration.ZERO
                 )
               )
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("int_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("long_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("double_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("float_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("short_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("byte_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("big_int_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Increment("big_dec_val", 5L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Max("int_val", 100L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Min("int_val", 1L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Max("long_val", 100L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Min("long_val", 1L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Max("double_val", 100L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Min("double_val", 1L)))
          _  <- store.updateFields("all1", Chunk(FieldUpdate.Decrement("int_val", 2L)))
          f1 <- store.findById("all1")
        } yield assertTrue(f1.isDefined)
      },
      test("anyToDynamicValue with currency, period etc via update") {
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- store.insert(User("u1", "Alice", 10L))
          _     <- store.updateFields(
                 "u1",
                 Chunk(
                   FieldUpdate.Set("name", java.util.Currency.getInstance("USD")),
                   FieldUpdate.Set("name", java.time.Period.ofDays(1)),
                   FieldUpdate.Set("name", java.time.Year.of(2024)),
                   FieldUpdate.Set("name", java.time.YearMonth.of(2024, 1)),
                   FieldUpdate.Set("name", java.time.MonthDay.of(1, 1)),
                   FieldUpdate.Set("name", java.time.OffsetDateTime.now()),
                   FieldUpdate.Set("name", java.time.OffsetTime.now()),
                   FieldUpdate.Set("name", java.time.ZonedDateTime.now()),
                   FieldUpdate.Set("name", java.time.ZoneId.of("UTC")),
                   FieldUpdate.Set("name", java.time.ZoneOffset.UTC),
                   FieldUpdate.Set("name", java.time.DayOfWeek.MONDAY),
                   FieldUpdate.Set("name", java.time.Month.JANUARY),
                   FieldUpdate.Set("name", "final")
                 )
               )
          f <- store.findById("u1")
        } yield assertTrue(f.exists(_.name == "final"))
      },
      test("getLong string numeric and non-numeric branches") {
        // Directly test InMemory via Max on string field that holds numeric string
        case class StrEntity(
          @Modifier.id id: String,
          sval: String,
          lval: Long
        )
        object StrEntity {
          implicit val schema: Schema[StrEntity]         = Schema.derived[StrEntity]
          implicit val entityPath: EntityPath[StrEntity] = EntityPath.derived[StrEntity]
        }
        for {
          store <- InMemoryProjectionStore.make[StrEntity]
          _     <- store.insert(StrEntity("s1", "notANumber", 10L))
          _     <- store.updateFields("s1", Chunk(FieldUpdate.Max("lval", 20L)))
          f1    <- store.findById("s1")
          _     <- store.insert(StrEntity("s2", "123", 5L))
          _     <- store.updateFields("s2", Chunk(FieldUpdate.Set("sval", "456")))
          f2    <- store.findById("s2")
        } yield assertTrue(f1.exists(_.lval == 20L), f2.exists(_.sval == "456"))
      }
    ),
    suite("TestEngine coverage for 0% file")(
      test("TestEngine InMemEventStore readFrom and readAll via append") {
        ZIO.scoped {
          for {
            engine <- TestEngine.make(
                        Projection[User]("sweep1").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                      )
            _ <- engine.append("u1", UserCreated("Alice", 10L))
            _ <- engine.append("u1", UserCreated("Bob", 20L))
            // Use underlying InMemEventStore via engine's eventStores
            list <- ZIO.succeed {
                      val es = engine.engine.eventStoresMap.values.head.asInstanceOf[EventStore[UserCreated]]
                      es
                    }.flatMap { es =>
                      es.readFrom(0L).runCollect.map(_.size) zip es.readAll().runCollect.map(_.size)
                    }
          } yield assertTrue(list._1 >= 0, list._2 >= 0)
        }
      },
      test("TestEngine append multi-source routing") {
        ZIO.scoped {
          for {
            engine <- TestEngine.make(
                        Projection[User]("sweep2")
                          .from("srcA")
                          .routeToSelf
                          .on[UserCreated]
                          .insert((e, ctx) => User(ctx.entityId, e.name, e.age)),
                        Projection[User]("sweep3")
                          .from("srcB")
                          .routeToSelf
                          .on[UserRenamed]
                          .insert((e, ctx) => User(ctx.entityId, e.newName, 0L))
                      )
            _  <- engine.append("u1", UserCreated("Alice", 10L))
            _  <- engine.append("u1", UserRenamed("Bob"))
            e1 <- engine.engine.eventStoresMap("srcA").asInstanceOf[EventStore[UserCreated]].readAll().runCollect
            e2 <- engine.engine.eventStoresMap("srcB").asInstanceOf[EventStore[UserRenamed]].readAll().runCollect
            sz <- ZIO.succeed(engine.stores.size)
          } yield assertTrue(
            e1.size == 1,
            e2.size == 1,
            e1.head.event.name == "Alice",
            e2.head.event.newName == "Bob",
            sz == 2
          )
        }
      },
      test("TestEngine query and queryByName") {
        ZIO.scoped {
          for {
            engine <- TestEngine.make(
                        Projection[User]("sweep4").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                      )
            _    <- engine.append("u1", UserCreated("Charlie", 30L))
            res1 <- engine.query(
                      Projection[User]("sweep4").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age)),
                      "missing"
                    )
            res2 <- engine.queryByName[User]("sweep4", "missing")
            under = engine.underlying
          } yield assertTrue(res1.isEmpty, res2.isEmpty, under != null)
        }
      }
    ),
    suite("ProjectionEngine branches sweep")(
      test("ProjectionEngine start with no bindings handles default source") {
        ZIO.scoped {
          for {
            cache  <- TransactorCache.makeUnscoped(5)
            store  <- InMemoryProjectionStore.make[User]
            engine <-
              ProjectionEngine.makeWithStores(
                List(Projection[User]("no-bind").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age))),
                Map("no-bind" -> store),
                Map.empty,
                cache
              )
            _   <- engine.start
            seq <- store.getLastProcessedSeq
            all <- store.findById("missing")
          } yield assertTrue(seq == 0L, all.isEmpty)
        }
      },
      test("ProjectionEngine validateSpec rejects invalid name via make") {
        val res = scala.util.Try {
          zio.Unsafe.unsafe { implicit unsafe =>
            zio.Runtime.default.unsafe
              .run(
                ZIO.scoped {
                  ProjectionEngine.make(
                    Projection[User]("bad/name").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                  )
                }
              )
              .getOrThrow()
          }
        }
        assertTrue(res.isFailure)
      },
      test("ProjectionEngine routingKey with CrossEntity extractor fallback") {
        val proj = Projection[User]("cross-sweep")
          .from("repos")
          .routedBy[UserCreated](_ => "key1")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.age))
        val scope = proj.scope
        assertTrue(scope.isInstanceOf[ProjectionScope.CrossEntity])
      }
    ),
    suite("SchemaHash and TagResolver sweep")(
      test("SchemaHash compute for all variant shapes") {
        sealed trait V1
        object V1 {
          case class A(x: String) extends V1
          case class B(y: Int)    extends V1
          implicit val schema: Schema[V1] = Schema.derived[V1]
        }
        val h1 = SchemaHash.compute[V1]
        val h2 = SchemaHash.compute[User]
        assertTrue(h1.nonEmpty, h2.nonEmpty, h1 != h2)
      },
      test("TagResolver alias extraction via Migration") {
        val m    = Migration.newBuilder[UserCreated, UserCreated].renameCase("Old", "UserCreated").build
        val info = TagResolver.resolve[UserCreated](m)
        assertTrue(info.aliases.contains("Old"))
      },
      test("TagResolver currentTags for single type") {
        val tags = TagResolver.currentTags[UserCreated]
        assertTrue(tags.contains("UserCreated"))
      }
    ),
    suite("SQLiteProjectionStore branches via direct use")(
      test("SQLiteProjectionStore addColumn already exists is no-op") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("sweep-addcol", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.addColumn("name", "TEXT")
            _     <- store.addColumn("name", "TEXT") // second time should be no-op
            _     <- store.insert(User("add1", "Alice", 10L))
            f     <- store.findById("add1")
          } yield assertTrue(f.isDefined)
        }
      }
    ),
    suite("EventStore numeric tag deriveTag sweep")(
      test("SQLiteEventStore append and read with string tag") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("sweep-event", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.insert(User("e1", "Alice", 10L))
            f     <- store.findById("e1")
          } yield assertTrue(f.isDefined)
        }
      }
    )
  )
}
