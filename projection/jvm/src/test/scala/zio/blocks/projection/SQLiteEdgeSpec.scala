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
import zio.blocks.chunk.Chunk
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.{Modifier, Schema}
import zio.test.*

object SQLiteEdgeSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Entities
  // ---------------------------------------------------------------------------

  // Solo: single-field entity with @id only -> exercises upsert else INSERT OR REPLACE branch (lines 116-117,181)
  case class Solo(@Modifier.id id: String)
  object Solo {
    implicit val schema: Schema[Solo]         = Schema.derived[Solo]
    implicit val entityPath: EntityPath[Solo] = EntityPath.derived[Solo]
  }

  // Nullable entity: Option[String] nullable column via FieldUpdate.Set with None/null -> hits writeAny null branch
  case class OptEntity(
    @Modifier.id id: String,
    optField: Option[String],
    note: String
  )
  object OptEntity {
    implicit val schema: Schema[OptEntity]         = Schema.derived[OptEntity]
    implicit val entityPath: EntityPath[OptEntity] = EntityPath.derived[OptEntity]
  }

  case class Counter(@Modifier.id id: String, score: Long)
  object Counter {
    implicit val schema: Schema[Counter]         = Schema.derived[Counter]
    implicit val entityPath: EntityPath[Counter] = EntityPath.derived[Counter]
  }

  // Diverse entity for InMemory defaultDynamicValueFor + primitive diversity (Char/BigDecimal/UUID etc)
  case class Diverse(
    @Modifier.id id: String,
    charField: Char,
    bigDecField: BigDecimal,
    bigIntField: BigInt,
    uuidField: java.util.UUID,
    instantField: java.time.Instant,
    localDateField: java.time.LocalDate,
    localDateTimeField: java.time.LocalDateTime,
    localTimeField: java.time.LocalTime,
    durationField: java.time.Duration,
    stringField: String
  )
  object Diverse {
    implicit val schema: Schema[Diverse]         = Schema.derived[Diverse]
    implicit val entityPath: EntityPath[Diverse] = EntityPath.derived[Diverse]
  }

  enum MyEnum { case A, B, C }

  case class EnumEntity(@Modifier.id id: String, status: MyEnum)
  object EnumEntity {
    implicit val schema: Schema[EnumEntity]         = Schema.derived[EnumEntity]
    implicit val entityPath: EntityPath[EnumEntity] = EntityPath.derived[EnumEntity]
  }

  // Rare primitiveToString branches: DayOfWeek and Year as id types
  case class DayIdEntity(@Modifier.id dayId: java.time.DayOfWeek, note: String)
  object DayIdEntity {
    implicit val schema: Schema[DayIdEntity]         = Schema.derived[DayIdEntity]
    implicit val entityPath: EntityPath[DayIdEntity] = EntityPath.derived[DayIdEntity]
  }

  case class YearIdEntity(@Modifier.id yearId: java.time.Year, note: String)
  object YearIdEntity {
    implicit val schema: Schema[YearIdEntity]         = Schema.derived[YearIdEntity]
    implicit val entityPath: EntityPath[YearIdEntity] = EntityPath.derived[YearIdEntity]
  }

  case class ZoneOffsetIdEntity(@Modifier.id offId: java.time.ZoneOffset, note: String)
  object ZoneOffsetIdEntity {
    implicit val schema: Schema[ZoneOffsetIdEntity]         = Schema.derived[ZoneOffsetIdEntity]
    implicit val entityPath: EntityPath[ZoneOffsetIdEntity] = EntityPath.derived[ZoneOffsetIdEntity]
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def tempFile(prefix: String = "edge-sqlite"): Task[(String, java.nio.file.Path)] =
    ZIO.attempt {
      val p = java.nio.file.Files.createTempFile(prefix, ".db")
      (p.toAbsolutePath.toString, p)
    }

  private def cleanup(path: String, tmp: java.nio.file.Path): UIO[Unit] =
    ZIO.succeed {
      try java.nio.file.Files.deleteIfExists(tmp)
      catch { case _: Throwable => () }
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path + "-wal"))
      catch { case _: Throwable => () }
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path + "-shm"))
      catch { case _: Throwable => () }
    }

  // ---------------------------------------------------------------------------
  // Spec
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("SQLiteEdgeSpec")(
    test("Solo entity upsert exercises INSERT OR REPLACE else branch") {
      ZIO.scoped {
        for {
          tmp            <- tempFile("solo")
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          store          <- SQLiteProjectionStore.make[Solo](path, cache)
          _              <- store.upsert(Solo("solo1"))
          f1             <- store.findById("solo1")
          _              <- store.upsert(Solo("solo1"))
          f2             <- store.findById("solo1")
          _              <- store.insert(Solo("solo2"))
          f3             <- store.findById("solo2")
          _              <- cleanup(path, tmpPath)
        } yield assertTrue(f1.contains(Solo("solo1")), f2.contains(Solo("solo1")), f3.contains(Solo("solo2")))
      }
    },
    test("validateField negative with unknown column throws IllegalArgumentException") {
      ZIO.scoped {
        for {
          tmp            <- tempFile("validate")
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          store          <- SQLiteProjectionStore.make[Counter](path, cache)
          _              <- store.insert(Counter("c1", 10L))
          result         <- store.updateFields("c1", Chunk(FieldUpdate.Set("unknownCol", 1))).exit
          _              <- cleanup(path, tmpPath)
        } yield assertTrue(
          result.isFailure,
          result match {
            case Exit.Failure(cause) =>
              cause.defects.exists(_.isInstanceOf[IllegalArgumentException]) || cause.failures.nonEmpty
            case _ => false
          }
        )
      }
    },
    test("validateField negative for Increment/Max/Min/Decrement variants") {
      ZIO.scoped {
        for {
          tmp            <- tempFile("validate2")
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          store          <- SQLiteProjectionStore.make[Counter](path, cache)
          _              <- store.insert(Counter("c1", 10L))
          r1             <- store.updateFields("c1", Chunk(FieldUpdate.Increment("badField", 1L))).exit
          r2             <- store.updateFields("c1", Chunk(FieldUpdate.Decrement("badField", 1L))).exit
          r3             <- store.updateFields("c1", Chunk(FieldUpdate.Max("badField", 99L))).exit
          r4             <- store.updateFields("c1", Chunk(FieldUpdate.Min("badField", 1L))).exit
          _              <- cleanup(path, tmpPath)
        } yield assertTrue(r1.isFailure, r2.isFailure, r3.isFailure, r4.isFailure)
      }
    },
    test("addColumn idempotency - second call is no-op and data preserved") {
      ZIO.scoped {
        for {
          tmp            <- tempFile("addcol")
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          store          <- SQLiteProjectionStore.make[Counter](path, cache)
          _              <- store.insert(Counter("c1", 42L))
          _              <- store.addColumn("extra_col", "TEXT")
          _              <- store.addColumn("extra_col", "TEXT")
          _              <- store.addColumn("another_col", "INTEGER")
          _              <- store.addColumn("extra_col", "TEXT")
          f              <- store.findById("c1")
          _              <- cleanup(path, tmpPath)
        } yield assertTrue(f.contains(Counter("c1", 42L)))
      }
    },
    test("Option[String] nullable column via Set None/null hits writeAny null branch") {
      ZIO.scoped {
        for {
          tmp            <- tempFile("nullable")
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          store          <- SQLiteProjectionStore.make[OptEntity](path, cache)
          _              <- store.insert(OptEntity("o1", Some("present"), "note1"))
          f1             <- store.findById("o1")
          _              <- store.updateFields("o1", Chunk(FieldUpdate.Set("opt_field", None)))
          f2             <- store.findById("o1")
          _              <- store.updateFields("o1", Chunk(FieldUpdate.Set("opt_field", null)))
          f3             <- store.findById("o1")
          _              <- store.updateFields("o1", Chunk(FieldUpdate.Set("opt_field", Some("again"))))
          f4             <- store.findById("o1")
          _              <- store.updateFields("o1", Chunk(FieldUpdate.Set("opt_field", None: Option[String])))
          f5             <- store.findById("o1")
          _              <- cleanup(path, tmpPath)
        } yield assertTrue(
          f1.exists(_.optField.contains("present")),
          f2.exists(_.optField.isEmpty),
          f3.exists(_.optField.isEmpty),
          f4.exists(_.optField.contains("again")),
          f5.exists(_.optField.isEmpty)
        )
      }
    },
    test("FieldUpdate Max/Min per variant via updateFields on SQLite store") {
      ZIO.scoped {
        for {
          tmp            <- tempFile("maxmin")
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          store          <- SQLiteProjectionStore.make[Counter](path, cache)
          _              <- store.insert(Counter("c1", 50L))
          _              <- store.updateFields("c1", Chunk(FieldUpdate.Max("score", 100L)))
          r1             <- store.findById("c1")
          _              <- store.updateFields("c1", Chunk(FieldUpdate.Max("score", 10L)))
          r2             <- store.findById("c1")
          _              <- store.updateFields("c1", Chunk(FieldUpdate.Min("score", 10L)))
          r3             <- store.findById("c1")
          _              <- store.updateFields("c1", Chunk(FieldUpdate.Min("score", 99L)))
          r4             <- store.findById("c1")
          _              <- store.updateFields("c1", Chunk(FieldUpdate.Max("score", 100L)))
          r5             <- store.findById("c1")
          _              <- store.updateFields("c1", Chunk(FieldUpdate.Min("score", 10L)))
          r6             <- store.findById("c1")
          // mixed Increment/Decrement still work after Max/Min
          _  <- store.updateFields("c1", Chunk(FieldUpdate.Increment("score", 5L)))
          r7 <- store.findById("c1")
          _  <- store.updateFields("c1", Chunk(FieldUpdate.Decrement("score", 3L)))
          r8 <- store.findById("c1")
          _  <- cleanup(path, tmpPath)
        } yield assertTrue(
          r1.exists(_.score == 100L),
          r2.exists(_.score == 100L),
          r3.exists(_.score == 10L),
          r4.exists(_.score == 10L),
          r5.exists(_.score == 100L),
          r6.exists(_.score == 10L),
          r7.exists(_.score == 15L),
          r8.exists(_.score == 12L)
        )
      }
    },
    test("InMemory primitive diversity - DayOfWeek/Year/ZoneOffset id types via primitiveToString") {
      for {
        s1 <- InMemoryProjectionStore.make[DayIdEntity]
        _  <- s1.insert(DayIdEntity(java.time.DayOfWeek.MONDAY, "m"))
        f1 <- s1.findById("MONDAY")
        s2 <- InMemoryProjectionStore.make[YearIdEntity]
        yr  = java.time.Year.of(2024)
        _  <- s2.insert(YearIdEntity(yr, "y"))
        f2 <- s2.findById(yr.toString)
        s3 <- InMemoryProjectionStore.make[ZoneOffsetIdEntity]
        off = java.time.ZoneOffset.UTC
        _  <- s3.insert(ZoneOffsetIdEntity(off, "o"))
        f3 <- s3.findById(off.toString)
      } yield assertTrue(f1.isDefined, f2.isDefined, f3.isDefined)
    },
    test("InMemory diverse defaults via missing-row updateFields hits defaultDynamicValueFor") {
      for {
        store <- InMemoryProjectionStore.make[Diverse]
        // createDefaultForId via missing id + Set, should default Char/BigDecimal/UUID etc
        _  <- store.updateFields("missing-diverse", Chunk(FieldUpdate.Set("string_field", "hello")))
        f  <- store.findById("missing-diverse")
        _  <- store.updateFields("missing2", Chunk(FieldUpdate.Set("char_field", 'X')))
        f2 <- store.findById("missing2")
        _  <- store.updateFields("missing3", Chunk(FieldUpdate.Set("big_dec_field", BigDecimal("123.45"))))
        f3 <- store.findById("missing3")
      } yield assertTrue(
        f.isDefined,
        f.exists(_.stringField == "hello"),
        f.exists(_.charField == ' '),
        f2.exists(_.charField == 'X'),
        f3.isDefined
      )
    },
    test("InMemory enumeration default and anyToDynamicValue Char/BigDecimal/UUID") {
      for {
        store  <- InMemoryProjectionStore.make[EnumEntity]
        _      <- store.insert(EnumEntity("enum1", MyEnum.A))
        f      <- store.findById("enum1")
        _      <- store.upsert(EnumEntity("enum1", MyEnum.B))
        f2     <- store.findById("enum1")
        dStore <- InMemoryProjectionStore.make[Diverse]
        _      <- dStore.insert(
               Diverse(
                 "d1",
                 'c',
                 BigDecimal(1),
                 BigInt(2),
                 new java.util.UUID(0L, 1L),
                 java.time.Instant.EPOCH,
                 java.time.LocalDate.ofEpochDay(0),
                 java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC),
                 java.time.LocalTime.MIDNIGHT,
                 java.time.Duration.ZERO,
                 "s"
               )
             )
        _ <- dStore.updateFields("d1", Chunk(FieldUpdate.Set("char_field", 'Z')))
        _ <- dStore.updateFields("d1", Chunk(FieldUpdate.Set("big_dec_field", BigDecimal("999.99"))))
        _ <- dStore.updateFields("d1", Chunk(FieldUpdate.Set("uuid_field", new java.util.UUID(1L, 2L))))
        _ <- dStore.updateFields(
               "d1",
               Chunk(FieldUpdate.Set("instant_field", java.time.Instant.parse("2024-01-01T00:00:00Z")))
             )
        f3 <- dStore.findById("d1")
      } yield assertTrue(f.isDefined, f2.isDefined, f3.exists(_.charField == 'Z'))
    }
  )
}
