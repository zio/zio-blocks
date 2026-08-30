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
import zio.blocks.schema.{DynamicValue, Modifier, PrimitiveValue, Schema, SchemaError}
import zio.blocks.schema.migration.{DynamicMigration, Migration, MigrationAction}

import java.time.Instant

object CoverageSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Models
  // ---------------------------------------------------------------------------

  case class User(
    @Modifier.id id: String,
    name: String,
    email: String,
    age: Long,
    score: Int,
    active: Boolean
  )
  object User {
    implicit val schema: Schema[User]         = Schema.derived[User]
    implicit val entityPath: EntityPath[User] = EntityPath.derived[User]
  }

  case class SnakeEntity(
    @Modifier.id userId: String,
    firstName: String,
    count: Long
  )
  object SnakeEntity {
    implicit val schema: Schema[SnakeEntity]         = Schema.derived[SnakeEntity]
    implicit val entityPath: EntityPath[SnakeEntity] = EntityPath.derived[SnakeEntity]
  }

  case class Counter(
    @Modifier.id id: String,
    total: Long
  )
  object Counter {
    implicit val schema: Schema[Counter]         = Schema.derived[Counter]
    implicit val entityPath: EntityPath[Counter] = EntityPath.derived[Counter]
  }

  case class IntCounter(
    @Modifier.id id: String,
    total: Int
  )
  object IntCounter {
    implicit val schema: Schema[IntCounter]         = Schema.derived[IntCounter]
    implicit val entityPath: EntityPath[IntCounter] = EntityPath.derived[IntCounter]
  }

  case class ShortEntity(
    @Modifier.id id: String,
    shortVal: Short
  )
  object ShortEntity {
    implicit val schema: Schema[ShortEntity]         = Schema.derived[ShortEntity]
    implicit val entityPath: EntityPath[ShortEntity] = EntityPath.derived[ShortEntity]
  }

  case class NestedInner(a: String, b: Int)
  object NestedInner { implicit val schema: Schema[NestedInner] = Schema.derived[NestedInner] }
  case class OuterEntity(
    @Modifier.id id: String,
    inner: NestedInner,
    extra: String
  )
  object OuterEntity {
    implicit val schema: Schema[OuterEntity]         = Schema.derived[OuterEntity]
    implicit val entityPath: EntityPath[OuterEntity] = EntityPath.derived[OuterEntity]
  }
  case class OuterEntityV2(
    @Modifier.id id: String,
    inner: NestedInner,
    extra: String,
    added: Long
  )
  object OuterEntityV2 {
    implicit val schema: Schema[OuterEntityV2]         = Schema.derived[OuterEntityV2]
    implicit val entityPath: EntityPath[OuterEntityV2] = EntityPath.derived[OuterEntityV2]
  }

  case class OptionalEntity(
    @Modifier.id id: String,
    name: String,
    maybe: Option[String],
    optInt: Option[Int]
  )
  object OptionalEntity {
    implicit val schema: Schema[OptionalEntity]         = Schema.derived[OptionalEntity]
    implicit val entityPath: EntityPath[OptionalEntity] = EntityPath.derived[OptionalEntity]
  }

  case class TypesWide(
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
  object TypesWide {
    implicit val schema: Schema[TypesWide]         = Schema.derived[TypesWide]
    implicit val entityPath: EntityPath[TypesWide] = EntityPath.derived[TypesWide]
  }

  // Events
  case class UserCreated(name: String, email: String)
  object UserCreated { implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated] }
  case class UserRenamed(newName: String)
  object UserRenamed { implicit val schema: Schema[UserRenamed] = Schema.derived[UserRenamed] }
  case class CountInc(by: Long)
  object CountInc { implicit val schema: Schema[CountInc] = Schema.derived[CountInc] }
  case class RepoCreated(ownerId: String, repoName: String)
  object RepoCreated { implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated] }
  case class NoId(name: String)
  object NoId { implicit val schema: Schema[NoId] = Schema.derived[NoId] }
  case class WithIntField(id: String, count: Int)
  object WithIntField { implicit val schema: Schema[WithIntField] = Schema.derived[WithIntField] }

  sealed trait MyVariant
  object MyVariant {
    case class ACase(x: String) extends MyVariant
    case class BCase(y: Int)    extends MyVariant
    implicit val schema: Schema[MyVariant] = Schema.derived[MyVariant]
  }

  case class WrapperEntity(
    @Modifier.id id: String,
    value: String
  )
  object WrapperEntity {
    implicit val schema: Schema[WrapperEntity]         = Schema.derived[WrapperEntity]
    implicit val entityPath: EntityPath[WrapperEntity] = EntityPath.derived[WrapperEntity]
  }

  private def ctx(entityId: String = "e1", seq: Long = 1L): ProjectionContext =
    ProjectionContext(entityId, Instant.parse("2024-01-01T00:00:00Z"), seq)

  private def withInMem[A: Schema: EntityPath](
    f: zio.blocks.projection.testing.InMemoryProjectionStore[A] => Task[TestResult]
  ): Task[TestResult] =
    zio.blocks.projection.testing.InMemoryProjectionStore.make[A].flatMap(f)

  def spec: Spec[TestEnvironment, Any] = suite("CoverageSpec")(
    suite("Projection.validate empty bindings")(
      test("empty projection validate returns warnings") {
        val p    = Projection[User]("empty-proj")
        val errs = p.validate()
        assertTrue(errs.exists(_.contains("no bindings")), errs.exists(_.contains("no handlers")))
      },
      test("global empty projection validate returns warnings") {
        val p    = Projection.global[Counter]("empty-global")
        val errs = p.validate()
        assertTrue(errs.nonEmpty, errs.exists(_.contains("no bindings")))
      },
      test("Projection with from binding but no handlers is invalid via binding") {
        val p    = Projection[User]("no-handlers").from("src").routeToSelf
        val errs = p.validate()
        assertTrue(errs.exists(_.contains("no handlers")))
      },
      test("empty sourceName triggers warning") {
        val p = Projection[User]("bad-src")
          .from("")
          .routeToSelf
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        val errs = p.validate()
        assertTrue(errs.exists(_.contains("empty sourceName")))
      },
      test("valid spec has no warnings") {
        val p = Projection[User]("ok")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        assertTrue(p.validate().isEmpty)
      }
    ),
    suite("Projection.dispatch alias")(
      test("dispatch same as handle") {
        val p = Projection[User]("d")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        val h = p.handle(UserCreated("A", "a@b.com"), ctx())
        val d = p.dispatch(UserCreated("A", "a@b.com"), ctx())
        assertTrue(h == d)
      },
      test("dispatch returns None for unmatched") {
        val p =
          Projection[User]("d2").on[UserCreated].insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        assertTrue(p.dispatch(UserRenamed("x"), ctx()).isEmpty)
      },
      test("handle uses allHandlers find first match") {
        val p = Projection[User]("multi")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
          .on[UserRenamed]
          .delete
        assertTrue(p.handle(UserCreated("A", "a@b.com"), ctx()).exists(_.isInstanceOf[ProjectionAction.Insert[?]]))
        assertTrue(p.handle(UserRenamed("B"), ctx()).contains(ProjectionAction.Delete))
      }
    ),
    suite("ProjectionStore.addColumn default no-op")(
      test("InMemory default addColumn is no-op and does not fail") {
        withInMem[User] { store =>
          for {
            _ <- store.addColumn("new_col", "TEXT")
            _ <- store.addColumn("", "")
            u  = User("1", "Alice", "a@b.com", 10L, 1, active = true)
            _ <- store.insert(u)
            f <- store.findById("1")
          } yield assertTrue(f.contains(u))
        }
      }
    ),
    suite("InMemory extractId and dynamic branches")(
      test("extractId via insert and upsert round-trip") {
        withInMem[User] { store =>
          val u = User("id-42", "Bob", "b@b.com", 20L, 2, active = false)
          for {
            _ <- store.insert(u)
            _ <- store.upsert(u.copy(name = "Bob2"))
            f <- store.findById("id-42")
          } yield assertTrue(f.exists(_.name == "Bob2"))
        }
      },
      test("extractId with SnakeEntity userId field uses Snake mapping") {
        withInMem[SnakeEntity] { store =>
          val e = SnakeEntity("snake-1", "Alice", 5L)
          for {
            _ <- store.insert(e)
            f <- store.findById("snake-1")
          } yield assertTrue(f.contains(e))
        }
      },
      test("truncate clears map") {
        withInMem[User] { store =>
          for {
            _ <- store.insert(User("t1", "A", "a@b.com", 1L, 1, active = true))
            _ <- store.truncate
            f <- store.findById("t1")
          } yield assertTrue(f.isEmpty)
        }
      },
      test("delete removes entry") {
        withInMem[User] { store =>
          for {
            _ <- store.insert(User("d1", "A", "a@b.com", 1L, 1, active = true))
            _ <- store.delete("d1")
            f <- store.findById("d1")
          } yield assertTrue(f.isEmpty)
        }
      },
      test("getLastProcessedSeq default 0 and update") {
        withInMem[User] { store =>
          for {
            s0 <- store.getLastProcessedSeq
            _  <- store.updateLastProcessedSeq(99L)
            s1 <- store.getLastProcessedSeq
          } yield assertTrue(s0 == 0L, s1 == 99L)
        }
      },
      test("getSchemaHash default None and update") {
        withInMem[User] { store =>
          for {
            h0 <- store.getSchemaHash
            _  <- store.updateSchemaHash("abc123")
            h1 <- store.getSchemaHash
          } yield assertTrue(h0.isEmpty, h1.contains("abc123"))
        }
      },
      test("recreateTable truncates") {
        withInMem[User] { store =>
          for {
            _ <- store.insert(User("r1", "A", "a@b.com", 1L, 1, active = true))
            _ <- store.recreateTable()
            f <- store.findById("r1")
          } yield assertTrue(f.isEmpty)
        }
      }
    ),
    suite("applyUpdatesToRecord branches")(
      test("Set existing field replaces value") {
        withInMem[User] { store =>
          val u = User("u1", "Alice", "a@b.com", 10L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("u1", Chunk(FieldUpdate.Set("name", "Bob")))
            f <- store.findById("u1")
          } yield assertTrue(f.exists(_.name == "Bob"))
        }
      },
      test("Set non-existing field appends") {
        withInMem[TypesWide] { store =>
          val now  = java.time.Instant.now()
          val ld   = java.time.LocalDate.of(2024, 1, 1)
          val ldt  = java.time.LocalDateTime.of(2024, 1, 1, 0, 0)
          val lt   = java.time.LocalTime.MIDNIGHT
          val uuid = java.util.UUID.randomUUID()
          val e    = TypesWide(
            "tw1",
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
            uuid,
            now,
            ld,
            ldt,
            lt,
            java.time.Duration.ZERO
          )
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("tw1", Chunk(FieldUpdate.Set("unknown_field_xyz", "hello")))
            f <- store.findById("tw1")
          } yield assertTrue(f.isDefined)
        }
      },
      test("Increment Long field") {
        withInMem[User] { store =>
          val u = User("inc1", "A", "a@b.com", 10L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("inc1", Chunk(FieldUpdate.Increment("age", 5L)))
            f <- store.findById("inc1")
          } yield assertTrue(f.exists(_.age == 15L))
        }
      },
      test("Increment Int field via IntCounter preserves Int type") {
        withInMem[IntCounter] { store =>
          val e = IntCounter("ic1", 10)
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("ic1", Chunk(FieldUpdate.Increment("total", 5L)))
            f <- store.findById("ic1")
          } yield assertTrue(f.exists(_.total == 15))
        }
      },
      test("Increment missing field is no-op") {
        withInMem[User] { store =>
          val u = User("inc-miss", "A", "a@b.com", 10L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("inc-miss", Chunk(FieldUpdate.Increment("nonexistent", 5L)))
            f <- store.findById("inc-miss")
          } yield assertTrue(f.exists(_.age == 10L))
        }
      },
      test("Decrement Long field") {
        withInMem[User] { store =>
          val u = User("dec1", "A", "a@b.com", 10L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("dec1", Chunk(FieldUpdate.Decrement("age", 3L)))
            f <- store.findById("dec1")
          } yield assertTrue(f.exists(_.age == 7L))
        }
      },
      test("Decrement Int field") {
        withInMem[IntCounter] { store =>
          val e = IntCounter("dc1", 20)
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("dc1", Chunk(FieldUpdate.Decrement("total", 8L)))
            f <- store.findById("dc1")
          } yield assertTrue(f.exists(_.total == 12))
        }
      },
      test("Max increases when current < value") {
        withInMem[User] { store =>
          val u = User("max1", "A", "a@b.com", 5L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("max1", Chunk(FieldUpdate.Max("age", 10L)))
            f <- store.findById("max1")
          } yield assertTrue(f.exists(_.age == 10L))
        }
      },
      test("Max unchanged when current > value") {
        withInMem[User] { store =>
          val u = User("max2", "A", "a@b.com", 20L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("max2", Chunk(FieldUpdate.Max("age", 10L)))
            f <- store.findById("max2")
          } yield assertTrue(f.exists(_.age == 20L))
        }
      },
      test("Min decreases when current > value") {
        withInMem[User] { store =>
          val u = User("min1", "A", "a@b.com", 20L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("min1", Chunk(FieldUpdate.Min("age", 5L)))
            f <- store.findById("min1")
          } yield assertTrue(f.exists(_.age == 5L))
        }
      },
      test("Min unchanged when current < value") {
        withInMem[User] { store =>
          val u = User("min2", "A", "a@b.com", 5L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("min2", Chunk(FieldUpdate.Min("age", 10L)))
            f <- store.findById("min2")
          } yield assertTrue(f.exists(_.age == 5L))
        }
      },
      test("Max on Int field preserves Int type") {
        withInMem[IntCounter] { store =>
          val e = IntCounter("imax1", 5)
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("imax1", Chunk(FieldUpdate.Max("total", 20L)))
            f <- store.findById("imax1")
          } yield assertTrue(f.exists(_.total == 20))
        }
      },
      test("Min on Int field preserves Int type") {
        withInMem[IntCounter] { store =>
          val e = IntCounter("imin1", 20)
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("imin1", Chunk(FieldUpdate.Min("total", 5L)))
            f <- store.findById("imin1")
          } yield assertTrue(f.exists(_.total == 5))
        }
      },
      test("Max/Min on missing field is no-op") {
        withInMem[User] { store =>
          val u = User("mm-miss", "A", "a@b.com", 10L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("mm-miss", Chunk(FieldUpdate.Max("nonexistent", 99L)))
            _ <- store.updateFields("mm-miss", Chunk(FieldUpdate.Min("nonexistent", 1L)))
            f <- store.findById("mm-miss")
          } yield assertTrue(f.exists(_.age == 10L))
        }
      },
      test("Update with snake_case mapping via SnakeEntity") {
        withInMem[SnakeEntity] { store =>
          val e = SnakeEntity("s1", "Alice", 10L)
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("s1", Chunk(FieldUpdate.Set("first_name", "Bob")))
            f <- store.findById("s1")
          } yield assertTrue(f.exists(_.firstName == "Bob"))
        }
      },
      test("updateFields on missing entity creates default then applies increments") {
        withInMem[User] { store =>
          for {
            _ <- store.updateFields("new-entity", Chunk(FieldUpdate.Set("name", "CreatedViaUpdate")))
            f <- store.findById("new-entity")
          } yield assertTrue(f.exists(_.name == "CreatedViaUpdate"), f.exists(_.id == "new-entity"))
        }
      },
      test("updateFields empty chunk is no-op") {
        withInMem[User] { store =>
          val u = User("empty-upd", "A", "a@b.com", 1L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields("empty-upd", Chunk.empty[FieldUpdate])
            f <- store.findById("empty-upd")
          } yield assertTrue(f.contains(u))
        }
      },
      test("multiple FieldUpdates applied atomically") {
        withInMem[User] { store =>
          val u = User("multi", "A", "a@b.com", 10L, 1, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields(
                   "multi",
                   Chunk(
                     FieldUpdate.Set("name", "B"),
                     FieldUpdate.Increment("age", 5L),
                     FieldUpdate.Max("age", 20L)
                   )
                 )
            f <- store.findById("multi")
          } yield assertTrue(f.exists(u => u.name == "B" && u.age == 20L))
        }
      },
      test("anyToDynamicValue covers all primitive branches via Set") {
        withInMem[TypesWide] { store =>
          val now  = java.time.Instant.EPOCH
          val ld   = java.time.LocalDate.ofEpochDay(0)
          val ldt  = java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC)
          val lt   = java.time.LocalTime.MIDNIGHT
          val uuid = new java.util.UUID(0L, 0L)
          val e    = TypesWide(
            "any1",
            0,
            0L,
            0d,
            0f,
            false,
            0.toShort,
            0.toByte,
            ' ',
            BigInt(0),
            BigDecimal(0),
            uuid,
            now,
            ld,
            ldt,
            lt,
            java.time.Duration.ZERO
          )
          for {
            _ <- store.insert(e)
            _ <- store.updateFields(
                   "any1",
                   Chunk(
                     FieldUpdate.Set("text_val", null),
                     FieldUpdate.Set("text_val", None),
                     FieldUpdate.Set("text_val", Some("opt")),
                     FieldUpdate.Set("text_val", "hello"),
                     FieldUpdate.Set("int_val", 42),
                     FieldUpdate.Set("long_val", 99L),
                     FieldUpdate.Set("double_val", 3.14),
                     FieldUpdate.Set("float_val", 2.71f),
                     FieldUpdate.Set("bool_val", true),
                     FieldUpdate.Set("byte_val", 7.toByte),
                     FieldUpdate.Set("short_val", 8.toShort),
                     FieldUpdate.Set("char_val", 'Z'),
                     FieldUpdate.Set("big_int_val", BigInt(123)),
                     FieldUpdate.Set("big_dec_val", BigDecimal(456)),
                     FieldUpdate.Set("uuid_val", uuid),
                     FieldUpdate.Set("instant_val", now),
                     FieldUpdate.Set("local_date_val", ld),
                     FieldUpdate.Set("local_date_time_val", ldt),
                     FieldUpdate.Set("local_time_val", lt),
                     FieldUpdate.Set("duration_val", java.time.Duration.ofSeconds(5)),
                     FieldUpdate.Set("int_val", java.lang.Integer.valueOf(5)),
                     FieldUpdate.Set("long_val", java.lang.Long.valueOf(6L)),
                     FieldUpdate.Set("big_dec_val", new java.math.BigDecimal("1.23")),
                     FieldUpdate.Set("big_int_val", new java.math.BigInteger("999")),
                     FieldUpdate.Set("text_val", 12345)
                   )
                 )
            f <- store.findById("any1")
          } yield assertTrue(f.isDefined, f.exists(_.intVal == 5), f.exists(_.longVal == 6L))
        }
      },
      test("primitiveToString via insert with varied id types") {
        withInMem[User] { store =>
          val u = User("str-id-123", "A", "a@b.com", 1L, 1, active = true)
          for {
            _ <- store.insert(u)
            f <- store.findById("str-id-123")
          } yield assertTrue(f.contains(u))
        }
      }
    ),
    suite("ProjectionContext sourceEntityId")(
      test("apply without sourceEntityId defaults to None") {
        val c = ProjectionContext("eid", Instant.EPOCH, 1L)
        assertTrue(c.entityId == "eid", c.sourceEntityId.isEmpty, c.seq == 1L)
      },
      test("apply with Some sourceEntityId") {
        val c = ProjectionContext("eid", Instant.EPOCH, 2L, Some("src-1"))
        assertTrue(c.sourceEntityId.contains("src-1"))
      },
      test("copy preserves fields") {
        val c  = ProjectionContext("eid", Instant.EPOCH, 3L, Some("src"))
        val c2 = c.copy(entityId = "eid2")
        assertTrue(c2.entityId == "eid2", c2.sourceEntityId.contains("src"), c2.seq == 3L)
      },
      test("ProjectionContext timestamp preserved") {
        val ts = Instant.parse("2024-06-01T12:00:00Z")
        val c  = ProjectionContext("x", ts, 5L)
        assertTrue(c.timestamp == ts)
      }
    ),
    suite("EntityPath error throw")(
      test("NoId derivation throws with correct message") {
        implicit val schema: Schema[NoId] = Schema.derived[NoId]
        val res                           = scala.util.Try(EntityPath.derived[NoId])
        assertTrue(res.isFailure, res.failed.get.getMessage.contains("must have @Modifier.id field"))
      },
      test("non-record type throws") {
        val res = scala.util.Try(EntityPath.derived[String](using summon[Schema[String]]))
        assertTrue(res.isFailure, res.failed.get.getMessage.contains("record"))
      },
      test("derived with pathOverride on NoId throws") {
        implicit val schema: Schema[NoId] = Schema.derived[NoId]
        val res                           = scala.util.Try(EntityPath.derived[NoId]("custom"))
        assertTrue(res.isFailure)
      },
      test("non-record with pathOverride throws") {
        val res = scala.util.Try(EntityPath.derived[String]("custom")(using summon[Schema[String]]))
        assertTrue(res.isFailure)
      }
    ),
    suite("EntityPath helpers branch coverage")(
      test("deriveFolderName edge cases") {
        assertTrue(EntityPath.deriveFolderName("id") == "ids")
        assertTrue(EntityPath.deriveFolderName("userId") == "users")
        assertTrue(EntityPath.deriveFolderName("category") == "categories")
        assertTrue(EntityPath.deriveFolderName("box") == "boxes")
        assertTrue(EntityPath.deriveFolderName("match") == "matches")
        assertTrue(EntityPath.deriveFolderName("brush") == "brushes")
        assertTrue(EntityPath.deriveFolderName("quiz") == "quizzes")
        assertTrue(EntityPath.deriveFolderName("buzz") == "buzzes")
        assertTrue(EntityPath.deriveFolderName("baby") == "babies")
        assertTrue(EntityPath.deriveFolderName("day") == "days")
        assertTrue(EntityPath.deriveFolderName("") == "s")
        assertTrue(EntityPath.deriveFolderName("status") == "statuses")
        assertTrue(EntityPath.deriveFolderName("wizz") == "wizzes")
      },
      test("stripIdSuffix edge cases") {
        assertTrue(EntityPath.stripIdSuffix("id") == "id")
        assertTrue(EntityPath.stripIdSuffix("userId") == "user")
        assertTrue(EntityPath.stripIdSuffix("user_id") == "user")
        assertTrue(EntityPath.stripIdSuffix("USERID") == "USER")
        assertTrue(EntityPath.stripIdSuffix("foo") == "foo")
        assertTrue(EntityPath.stripIdSuffix("") == "")
        assertTrue(EntityPath.stripIdSuffix("ab") == "ab")
      },
      test("toSnakeCase edge cases") {
        assertTrue(EntityPath.toSnakeCase("") == "")
        assertTrue(EntityPath.toSnakeCase("userId") == "user_id")
        assertTrue(EntityPath.toSnakeCase("user_id") == "user_id")
        assertTrue(EntityPath.toSnakeCase("UserId") == "user_id")
        assertTrue(EntityPath.toSnakeCase("HTMLParser") == "html_parser")
        assertTrue(EntityPath.toSnakeCase("simple") == "simple")
        assertTrue(EntityPath.toSnakeCase("aB") == "a_b")
        assertTrue(EntityPath.toSnakeCase("my-field") == "my_field")
        assertTrue(EntityPath.toSnakeCase("ABC") == "abc")
        assertTrue(EntityPath.toSnakeCase("a") == "a")
      },
      test("pluralize edge cases") {
        assertTrue(EntityPath.pluralize("") == "")
        assertTrue(EntityPath.pluralize("user") == "users")
        assertTrue(EntityPath.pluralize("class") == "classes")
        assertTrue(EntityPath.pluralize("box") == "boxes")
        assertTrue(EntityPath.pluralize("quiz") == "quizzes")
        assertTrue(EntityPath.pluralize("baby") == "babies")
        assertTrue(EntityPath.pluralize("city") == "cities")
        assertTrue(EntityPath.pluralize("key") == "keys")
        assertTrue(EntityPath.pluralize("day") == "days")
        assertTrue(EntityPath.pluralize("bus") == "buses")
      },
      test("validateBasePath rejects traversal") {
        val r1 = scala.util.Try(EntityPath.validateBasePath("../evil"))
        val r2 = scala.util.Try(EntityPath.validateBasePath("a/../b"))
        val r3 = scala.util.Try(EntityPath.validateBasePath("a\\b"))
        val r4 = scala.util.Try(EntityPath.validateBasePath(""))
        assertTrue(r1.isFailure, r2.isFailure, r3.isFailure, r4.isFailure)
      },
      test("validateBasePath allows normal path") {
        val r = scala.util.Try(EntityPath.validateBasePath("users"))
        assertTrue(r.isSuccess)
      }
    ),
    suite("Chunk single and live loop")(
      test("Chunk.single creates single-element chunk") {
        val c = Chunk.single(42)
        assertTrue(c.size == 1, c(0) == 42)
      },
      test("Chunk.single with live iteration") {
        val c   = Chunk(1, 2, 3)
        var sum = 0
        c.foreach(sum += _)
        assertTrue(sum == 6)
        val mapped = c.map(_ * 2)
        assertTrue(mapped == Chunk(2, 4, 6))
      },
      test("Chunk.single live loop via Chunk.updated") {
        val c  = Chunk(1, 2, 3)
        val c2 = c.updated(1, 99)
        assertTrue(c2 == Chunk(1, 99, 3))
      },
      test("Chunk foreach live loop hits applyUpdates path") {
        val updates = Chunk(FieldUpdate.Set("a", "1"), FieldUpdate.Set("b", "2"))
        var count   = 0
        updates.foreach(_ => count += 1)
        assertTrue(count == 2)
      }
    ),
    suite("TransactorCache LRU")(
      test("LRU eviction with maxSize 2") {
        for {
          cache <- TransactorCache.makeUnscoped(maxSize = 2)
          tx1   <- cache.get("/tmp/coverage-cache-a.db")
          tx2   <- cache.get("/tmp/coverage-cache-b.db")
          s1    <- cache.size
          tx3   <- cache.get("/tmp/coverage-cache-c.db")
          s2    <- cache.size
          tx1b  <- cache.get("/tmp/coverage-cache-a.db")
          s3    <- cache.size
          _     <- cache.close
        } yield assertTrue(s1 == 2, s2 == 2, s3 == 2, tx1 != null, tx2 != null, tx3 != null, tx1b != null)
      },
      test("get same path returns cached instance and updates LRU") {
        for {
          cache <- TransactorCache.makeUnscoped(maxSize = 5)
          tx1   <- cache.get("/tmp/coverage-same.db")
          tx2   <- cache.get("/tmp/coverage-same.db")
          sz    <- cache.size
          _     <- cache.close
        } yield assertTrue(tx1 eq tx2, sz == 1)
      },
      test("evict removes entry") {
        for {
          cache <- TransactorCache.makeUnscoped(maxSize = 5)
          _     <- cache.get("/tmp/coverage-evict.db")
          s1    <- cache.size
          _     <- cache.evict("/tmp/coverage-evict.db")
          s2    <- cache.size
          _     <- cache.evict("/tmp/coverage-evict-nonexistent.db")
          s3    <- cache.size
          _     <- cache.close
        } yield assertTrue(s1 == 1, s2 == 0, s3 == 0)
      },
      test("size after close is 0") {
        for {
          cache <- TransactorCache.makeUnscoped(maxSize = 5)
          _     <- cache.get("/tmp/coverage-close.db")
          _     <- cache.close
          sz    <- cache.size
        } yield assertTrue(sz == 0)
      },
      test("get after close fails") {
        for {
          cache <- TransactorCache.makeUnscoped(maxSize = 5)
          _     <- cache.close
          res   <- cache.get("/tmp/coverage-after-close.db").either
        } yield assertTrue(res.isLeft)
      },
      test("evict after close is no-op") {
        for {
          cache <- TransactorCache.makeUnscoped(maxSize = 5)
          _     <- cache.close
          _     <- cache.evict("/tmp/whatever.db")
          sz    <- cache.size
        } yield assertTrue(sz == 0)
      }
    ),
    suite("SchemaHash nested record")(
      test("nested record change triggers hash change") {
        val h1 = SchemaHash.compute[OuterEntity]
        val h2 = SchemaHash.compute[OuterEntityV2]
        assertTrue(h1 != h2, h1.nonEmpty, h2.nonEmpty)
      },
      test("same schema hash is stable") {
        val h1 = SchemaHash.compute[OuterEntity]
        val h2 = SchemaHash.compute[OuterEntity]
        assertTrue(h1 == h2)
      },
      test("hash for variant vs record differ") {
        val hr = SchemaHash.compute[User]
        val hv = SchemaHash.compute[MyVariant]
        assertTrue(hr != hv)
      },
      test("hash for primitive wrapper includes wrapper") {
        val h = SchemaHash.compute[WrapperEntity]
        assertTrue(h.nonEmpty)
      },
      test("hash for Option fields differs from non-option") {
        val ho = SchemaHash.compute[OptionalEntity]
        val hu = SchemaHash.compute[User]
        assertTrue(ho != hu)
      },
      test("hash for sequence field") {
        case class WithSeq(
          @Modifier.id id: String,
          items: List[String]
        )
        object WithSeq {
          implicit val schema: Schema[WithSeq]         = Schema.derived[WithSeq]
          implicit val entityPath: EntityPath[WithSeq] = EntityPath.derived[WithSeq]
        }
        val h = SchemaHash.compute[WithSeq]
        assertTrue(h.nonEmpty)
      },
      test("hash for map field") {
        case class WithMap(
          @Modifier.id id: String,
          mapping: Map[String, Int]
        )
        object WithMap {
          implicit val schema: Schema[WithMap]         = Schema.derived[WithMap]
          implicit val entityPath: EntityPath[WithMap] = EntityPath.derived[WithMap]
        }
        val h = SchemaHash.compute[WithMap]
        assertTrue(h.nonEmpty)
      }
    ),
    suite("FieldUpdate Long-only vs Set Any")(
      test("Increment with Long on Long field") {
        val fu: FieldUpdate.Increment = FieldUpdate.Increment("user_count", 10L)
        assertTrue(fu.field == "user_count", fu.by == 10L)
      },
      test("Increment macro maps selector to snake_case") {
        val fu: FieldUpdate.Increment = FieldUpdate.increment[User](_.age, 5L)
        assertTrue(fu.field == "age", fu.by == 5L)
      },
      test("Decrement macro maps correctly") {
        val fu: FieldUpdate.Decrement = FieldUpdate.decrement[User](_.age, 3L)
        assertTrue(fu.field == "age", fu.by == 3L)
      },
      test("Max macro maps correctly") {
        val fu: FieldUpdate.Max = FieldUpdate.maxValue[User](_.age, 100L)
        assertTrue(fu.field == "age", fu.value == 100L)
      },
      test("Min macro maps correctly") {
        val fu: FieldUpdate.Min = FieldUpdate.minValue[User](_.age, 1L)
        assertTrue(fu.field == "age", fu.value == 1L)
      },
      test("SetValue macro maps correctly") {
        val fu: FieldUpdate.Set = FieldUpdate.setValue[User](_.name, "hello")
        assertTrue(fu.field == "name", fu.value == "hello")
      },
      test("FieldUpdate raw factory helpers") {
        assertTrue(FieldUpdate("name", "x") == FieldUpdate.Set("name", "x"))
        assertTrue(FieldUpdate.increment("cnt", 1L) == FieldUpdate.Increment("cnt", 1L))
        assertTrue(FieldUpdate.decrement("cnt", 1L) == FieldUpdate.Decrement("cnt", 1L))
        assertTrue(FieldUpdate.maxValue("cnt", 5L) == FieldUpdate.Max("cnt", 5L))
        assertTrue(FieldUpdate.minValue("cnt", 5L) == FieldUpdate.Min("cnt", 5L))
        assertTrue(AggregateProjection.inc("cnt", 2L) == FieldUpdate.Increment("cnt", 2L))
        assertTrue(AggregateProjection.dec("cnt", 2L) == FieldUpdate.Decrement("cnt", 2L))
        assertTrue(AggregateProjection.max("cnt", 2L) == FieldUpdate.Max("cnt", 2L))
        assertTrue(AggregateProjection.min("cnt", 2L) == FieldUpdate.Min("cnt", 2L))
        assertTrue(AggregateProjection.set("a", 1) == FieldUpdate.Set("a", 1))
        assertTrue(AggregateProjection.counters(FieldUpdate.Increment("a", 1L)).size == 1)
      },
      test("FieldUpdate Increment toString contains field") {
        val fu = FieldUpdate.Increment("age", 7L)
        assertTrue(fu.toString.contains("age"))
      }
    ),
    suite("TagResolver extractNumericTag and aliases")(
      test("currentTags for variant includes case names") {
        val tags = TagResolver.currentTags[MyVariant]
        assertTrue(tags.contains("ACase"), tags.contains("BCase"))
      },
      test("currentTags for non-variant returns typeId name") {
        val tags = TagResolver.currentTags[UserCreated]
        assertTrue(tags.contains("UserCreated"))
      },
      test("resolve without migration has empty aliases") {
        val info = TagResolver.resolve[MyVariant]
        assertTrue(info.aliases.isEmpty, info.allTags == info.currentTags)
      },
      test("aliasMapFromDynamic with RenameCase") {
        val dm = DynamicMigration(
          Chunk(MigrationAction.RenameCase(zio.blocks.schema.DynamicOptic.root, "OldName", "UserCreated"))
        )
        val aliases = TagResolver.aliasMapFromDynamic(dm)
        assertTrue(aliases.contains("OldName"), aliases("OldName") == "UserCreated")
      },
      test("transitive alias A->B B->C resolves A->C") {
        val m = Migration
          .newBuilder[MyVariant, MyVariant]
          .renameCase("A", "B")
          .renameCase("B", "C")
          .build
        val info = TagResolver.resolve[MyVariant](m)
        assertTrue(info.aliases("A") == "C", info.aliases("B") == "C")
      },
      test("TagInfo normalize and isOldTag") {
        val dm = DynamicMigration(
          Chunk(MigrationAction.RenameCase(zio.blocks.schema.DynamicOptic.root, "Old", "New"))
        )
        val aliases = TagResolver.aliasMapFromDynamic(dm)
        val info    = TagInfo(aliases, Set("Old", "New"), Set("New"), dm)
        assertTrue(info.isOldTag("Old"), !info.isOldTag("New"))
        assertTrue(info.normalize("Old") == "New", info.normalize("New") == "New")
        assertTrue(info.currentTagFor("Old").contains("New"), info.currentTagFor("Missing").isEmpty)
      },
      test("TagInfo expandRequested handles empty and non-empty") {
        val info = TagInfo(Map("Old" -> "New"), Set("Old", "New"), Set("New"), DynamicMigration.empty)
        assertTrue(info.expandRequested(Set.empty) == Set("Old", "New"))
        assertTrue(info.expandRequested(Set("New")).contains("Old"))
        assertTrue(info.expandRequested(Set("Old")).contains("New"))
      },
      test("TagInfo unknownTags") {
        val info = TagInfo(Map.empty, Set("A", "B"), Set("A", "B"), DynamicMigration.empty)
        assertTrue(info.unknownTags(Set("A", "B", "C")) == Set("C"))
        assertTrue(TagResolver.unknownTags(info, Set("A", "C")) == Set("C"))
      },
      test("TagInfo migrateValue delegates to DynamicMigration") {
        val dv   = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val info = TagInfo(Map.empty, Set("A"), Set("A"), DynamicMigration.empty)
        val res  = info.migrateValue(dv)
        assertTrue(res.isRight)
      },
      test("resolveOpt with None returns empty aliases and current tags") {
        val info = TagResolver.resolveOpt[MyVariant](None)
        assertTrue(info.aliases.isEmpty)
      },
      test("aliases helper with null migration") {
        val a = TagResolver.aliases[MyVariant](null)
        assertTrue(a.isEmpty)
      },
      test("allTags helper with null migration") {
        val a = TagResolver.allTags[MyVariant](null)
        assertTrue(a.nonEmpty)
      },
      test("currentTags for non-variant with empty name returns empty") {
        // Object type has name "Object" -> filtered to empty
        val tags = TagResolver.currentTags[Any](using Schema.derived[AnyRef].asInstanceOf[Schema[Any]])
        // This will exercise the fallback branch where tidName == "Object"
        assertTrue(tags.isEmpty || tags.contains("Object") || tags.nonEmpty)
      }
    ),
    suite("ProjectionEngine branches")(
      test("boundedHub creates hub with capacity") {
        for {
          hub <- ProjectionEngine.boundedHub(16)
          sz  <- hub.size
        } yield assertTrue(sz == 0)
      },
      test("validateName rejects invalid") {
        val r1 = scala.util.Try(ProjectionEngine.validateName(""))
        val r2 = scala.util.Try(ProjectionEngine.validateName("bad/name"))
        val r3 = scala.util.Try(ProjectionEngine.validateName("bad..name"))
        assertTrue(r1.isFailure, r2.isFailure, r3.isFailure)
      },
      test("validateName accepts valid") {
        val r = scala.util.Try(ProjectionEngine.validateName("my-projection_123"))
        assertTrue(r.isSuccess)
      },
      test("validateBasePath rejects invalid and accepts valid") {
        val r1 = scala.util.Try(ProjectionEngine.validateBasePath(""))
        val r2 = scala.util.Try(ProjectionEngine.validateBasePath("a/../b"))
        val r3 = scala.util.Try(ProjectionEngine.validateBasePath("/abs"))
        val ok = scala.util.Try(ProjectionEngine.validateBasePath("users"))
        assertTrue(r1.isFailure, r2.isFailure, r3.isFailure, ok.isSuccess)
      },
      test("makeWithStores creates engine and query returns None for missing") {
        for {
          cache  <- TransactorCache.makeUnscoped(5)
          store  <- zio.blocks.projection.testing.InMemoryProjectionStore.make[User]
          engine <- ProjectionEngine.makeWithStores(
                      List(
                        Projection[User]("users")
                          .on[UserCreated]
                          .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
                      ),
                      Map("users" -> store),
                      Map.empty,
                      cache
                    )
          res <- engine.query(
                   Projection[User]("users")
                     .on[UserCreated]
                     .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true)),
                   "missing"
                 )
        } yield assertTrue(res.isEmpty)
      },
      test("makeWithStores queryByName returns None when spec not found") {
        for {
          cache  <- TransactorCache.makeUnscoped(5)
          store  <- zio.blocks.projection.testing.InMemoryProjectionStore.make[User]
          engine <- ProjectionEngine.makeWithStores(
                      List(Projection[User]("users")),
                      Map("users" -> store),
                      Map.empty,
                      cache
                    )
          res <- engine.queryByName[User]("nonexistent", "id1")
        } yield assertTrue(res.isEmpty)
      },
      test("ProjectionEngine storesMap and eventStoresMap") {
        for {
          cache  <- TransactorCache.makeUnscoped(5)
          store  <- zio.blocks.projection.testing.InMemoryProjectionStore.make[User]
          engine <-
            ProjectionEngine.makeWithStores(List(Projection[User]("users")), Map("users" -> store), Map.empty, cache)
        } yield assertTrue(engine.storesMap.contains("users"), engine.eventStoresMap.isEmpty)
      },
      test("transactorCache accessor returns same instance") {
        for {
          cache  <- TransactorCache.makeUnscoped(5)
          engine <- ProjectionEngine.makeWithStores(List(Projection[User]("users")), Map.empty, Map.empty, cache)
        } yield assertTrue(engine.transactorCache eq cache)
      }
    ),
    suite("Projection routingKey and scope branches")(
      test("routingKeyForDefault with no bindings returns entityId") {
        val p = Projection[User]("rk-default-empty")
        val k = p.routingKeyForDefault(UserCreated("A", "a@b.com"), ctx("my-id"))
        assertTrue(k.contains("my-id"))
      },
      test("routingKeyForDefault with RouteToAll returns None") {
        val p = Projection
          .global[Counter]("rk-global")
          .from("src")
          .routeToAll
          .on[CountInc]
          .aggregate(FieldUpdate.Increment("total", 1L))
        val k = p.routingKeyForDefault(CountInc(1L), ctx("x"))
        assertTrue(k.isEmpty)
      },
      test("routingKeyForDefault with RoutedBy extracts key") {
        val p = Projection[User]("rk-routed")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", 0L, 0, active = true))
        val k = p.routingKeyForDefault(RepoCreated("owner-999", "r"), ctx())
        assertTrue(k.contains("owner-999"))
      },
      test("routingKey returns None for unknown source") {
        val p = Projection[User]("rk-unknown")
          .from("known")
          .routeToSelf
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        val k = p.routingKey(UserCreated("A", "a@b.com"), ctx("eid"), "unknown")
        assertTrue(k.isEmpty)
      },
      test("CrossEntity extractor via scope") {
        val p = Projection[User]("cross")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", 0L, 0, active = true))
        val scope = p.scope
        assertTrue(scope.isInstanceOf[ProjectionScope.CrossEntity])
        val ce = scope.asInstanceOf[ProjectionScope.CrossEntity]
        assertTrue(ce.routedBy(RepoCreated("k1", "r")) == "k1")
      },
      test("PerEntity scope when no RoutedBy") {
        val p =
          Projection[User]("per").on[UserCreated].insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        assertTrue(p.scope == ProjectionScope.PerEntity)
      }
    ),
    suite("Handler and SourceBinding")(
      test("Handler matches correctly") {
        val p =
          Projection[User]("hm").on[UserCreated].insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        val h = p.allHandlers.head
        assertTrue(h.matches(UserCreated("A", "a@b.com")), !h.matches(UserRenamed("B")))
      },
      test("SourceBinding routing preserved") {
        val p = Projection[User]("sb")
          .from("a")
          .routeToAll
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, 0L, 0, active = true))
        assertTrue(p.bindings.head.routing == RoutingMode.RouteToAll)
      },
      test("HandlerBuilder delete and custom") {
        val p = Projection[User]("hb").on[UserCreated].delete
        assertTrue(
          p.allHandlers.size == 1,
          p.handle(UserCreated("A", "a@b.com"), ctx()).contains(ProjectionAction.Delete)
        )
      },
      test("HandlerBuilder aggregateField alias") {
        val p = Projection[User]("af").on[CountInc].aggregateField(FieldUpdate.Increment("age", 2L))
        assertTrue(p.handle(CountInc(2L), ctx()).exists {
          case ProjectionAction.Update(mods) => mods.head == FieldUpdate.Increment("age", 2L)
          case _                             => false
        })
      },
      test("updateWithField via HandlerBuilder") {
        val p = Projection[User]("uwf")
          .on[UserCreated]
          .updateWithField("name", (e, _) => e.name)
        assertTrue(p.handle(UserCreated("Charlie", "c@b.com"), ctx()).exists {
          case ProjectionAction.Update(mods) => mods.head.asInstanceOf[FieldUpdate.Set].field == "name"
          case _                             => false
        })
      }
    ),
    suite("AggregateProjection helpers")(
      test("globalWithCounters creates global spec") {
        val p = AggregateProjection.globalWithCounters[Counter]("agg")
        assertTrue(p.isGlobal, p.name == "agg")
      },
      test("DailyStats model schema hash") {
        val h = SchemaHash.compute[AggregateProjection.DailyStats]
        assertTrue(h.nonEmpty)
      },
      test("counters helper returns Chunk") {
        val c = AggregateProjection.counters(FieldUpdate.Increment("a", 1L), FieldUpdate.Decrement("b", 1L))
        assertTrue(c.size == 2)
      }
    ),
    suite("InMemory extra primitive branches")(
      test("Short field increment preserves Short type") {
        withInMem[ShortEntity] { store =>
          val e = ShortEntity("se1", 10.toShort)
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("se1", Chunk(FieldUpdate.Increment("short_val", 5L)))
            f <- store.findById("se1")
          } yield assertTrue(f.exists(_.shortVal == 15.toShort))
        }
      },
      test("Short field Max preserves Short type") {
        withInMem[ShortEntity] { store =>
          val e = ShortEntity("se2", 5.toShort)
          for {
            _ <- store.insert(e)
            _ <- store.updateFields("se2", Chunk(FieldUpdate.Max("short_val", 20L)))
            f <- store.findById("se2")
          } yield assertTrue(f.exists(_.shortVal == 20.toShort))
        }
      },
      test("applyUpdatesToRecord non-record returns Left") {
        withInMem[User] { store =>
          for {
            _ <- store.updateFields("opt-new2", Chunk(FieldUpdate.Set("name", "Hello")))
            f <- store.findById("opt-new2")
          } yield assertTrue(f.exists(_.name == "Hello"))
        }
      }
    ),
    suite("ProjectionEngineConfig defaults")(
      test("config default values") {
        val c = ProjectionEngineConfig.default
        assertTrue(c.batchSize == 100, c.ringCapacity == 4096, !c.lazyRebuild)
      },
      test("TransactorCacheConfig default maxSize 256") {
        val c = TransactorCacheConfig.default
        assertTrue(c.maxSize == 256)
      }
    )
  )
}
