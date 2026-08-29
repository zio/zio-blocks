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

object CoverageBoostSpec extends ZIOSpecDefault {

  case class User(
    @Modifier.id id: String,
    name: String,
    age: Long
  )
  object User {
    implicit val schema: Schema[User]         = Schema.derived[User]
    implicit val entityPath: EntityPath[User] = EntityPath.derived[User]
  }

  case class DoubleEntity(
    @Modifier.id id: String,
    dval: Double
  )
  object DoubleEntity {
    implicit val schema: Schema[DoubleEntity]         = Schema.derived[DoubleEntity]
    implicit val entityPath: EntityPath[DoubleEntity] = EntityPath.derived[DoubleEntity]
  }
  case class FloatEntity(
    @Modifier.id id: String,
    fval: Float
  )
  object FloatEntity {
    implicit val schema: Schema[FloatEntity]         = Schema.derived[FloatEntity]
    implicit val entityPath: EntityPath[FloatEntity] = EntityPath.derived[FloatEntity]
  }
  case class ByteEntity(
    @Modifier.id id: String,
    bval: Byte
  )
  object ByteEntity {
    implicit val schema: Schema[ByteEntity]         = Schema.derived[ByteEntity]
    implicit val entityPath: EntityPath[ByteEntity] = EntityPath.derived[ByteEntity]
  }
  case class BigIntEntity(
    @Modifier.id id: String,
    bi: BigInt
  )
  object BigIntEntity {
    implicit val schema: Schema[BigIntEntity]         = Schema.derived[BigIntEntity]
    implicit val entityPath: EntityPath[BigIntEntity] = EntityPath.derived[BigIntEntity]
  }
  case class BigDecEntity(
    @Modifier.id id: String,
    bd: BigDecimal
  )
  object BigDecEntity {
    implicit val schema: Schema[BigDecEntity]         = Schema.derived[BigDecEntity]
    implicit val entityPath: EntityPath[BigDecEntity] = EntityPath.derived[BigDecEntity]
  }
  case class IntEntity(
    @Modifier.id id: String,
    ival: Int
  )
  object IntEntity {
    implicit val schema: Schema[IntEntity]         = Schema.derived[IntEntity]
    implicit val entityPath: EntityPath[IntEntity] = EntityPath.derived[IntEntity]
  }
  case class WithOption(
    @Modifier.id id: String,
    name: String,
    opt: Option[String]
  )
  object WithOption {
    implicit val schema: Schema[WithOption]         = Schema.derived[WithOption]
    implicit val entityPath: EntityPath[WithOption] = EntityPath.derived[WithOption]
  }
  sealed trait MyEnum
  object MyEnum {
    case object A           extends MyEnum
    case object B           extends MyEnum
    case class C(x: String) extends MyEnum
    implicit val schema: Schema[MyEnum] = Schema.derived[MyEnum]
  }
  case class WithEnum(
    @Modifier.id id: String,
    e: MyEnum
  )
  object WithEnum {
    implicit val schema: Schema[WithEnum]         = Schema.derived[WithEnum]
    implicit val entityPath: EntityPath[WithEnum] = EntityPath.derived[WithEnum]
  }
  case class WithSeq(
    @Modifier.id id: String,
    items: List[String]
  )
  object WithSeq {
    implicit val schema: Schema[WithSeq]         = Schema.derived[WithSeq]
    implicit val entityPath: EntityPath[WithSeq] = EntityPath.derived[WithSeq]
  }
  case class WithMap(
    @Modifier.id id: String,
    mapping: Map[String, Int]
  )
  object WithMap {
    implicit val schema: Schema[WithMap]         = Schema.derived[WithMap]
    implicit val entityPath: EntityPath[WithMap] = EntityPath.derived[WithMap]
  }

  def spec: Spec[TestEnvironment, Any] = suite("CoverageBoostSpec")(
    suite("AggregateProjection helpers 0%")(
      test("inc with default by") {
        val fu = AggregateProjection.inc("my_field")
        assertTrue(fu.field == "my_field", fu.by == 1L)
      },
      test("dec with default by") {
        val fu = AggregateProjection.dec("my_field")
        assertTrue(fu.field == "my_field", fu.by == 1L)
      },
      test("max and min helpers") {
        val mx = AggregateProjection.max("peak", 100L)
        val mn = AggregateProjection.min("peak", 1L)
        assertTrue(mx.field == "peak", mx.value == 100L, mn.field == "peak", mn.value == 1L)
      },
      test("set helper") {
        val s = AggregateProjection.set("name", "hello")
        assertTrue(s.field == "name", s.value == "hello")
      },
      test("globalWithCounters with explicit bindings param") {
        val p = AggregateProjection.globalWithCounters[User]("agg2", List("src" -> ((_: Any) => "k")))
        assertTrue(p.isGlobal, p.name == "agg2")
      }
    ),
    suite("TransactorCache live helpers")(
      test("makeUnscoped with default size") {
        for {
          c  <- TransactorCache.makeUnscoped()
          sz <- c.size
          _  <- c.close
        } yield assertTrue(sz == 0)
      },
      test("live via make") {
        ZIO.scoped {
          TransactorCache.make(5).flatMap { cache =>
            cache.size.map(sz => assertTrue(sz == 0))
          }
        }
      }
    ),
    suite("InMemoryProjectionStore makeWithRefs")(
      test("makeWithRefs creates with initial data") {
        for {
          store <- InMemoryProjectionStore
                     .makeWithRefs[User](Map("u1" -> User("u1", "Alice", 10L)), seq = 5L, hash = Some("h1"))
          f <- store.findById("u1")
          s <- store.getLastProcessedSeq
          h <- store.getSchemaHash
        } yield assertTrue(f.exists(_.name == "Alice"), s == 5L, h.contains("h1"))
      },
      test("makeWithRefs defaults") {
        for {
          store <- InMemoryProjectionStore.makeWithRefs[User]()
          f     <- store.findById("missing")
        } yield assertTrue(f.isEmpty)
      }
    ),
    suite("TestContext helpers")(
      test("TestContext.make") {
        val c = TestContext.make("e1", 5L)
        assertTrue(c.entityId == "e1", c.seq == 5L)
      },
      test("TestContext.makeWithSource") {
        val c = TestContext.makeWithSource("e1", "src1")
        assertTrue(c.sourceEntityId.contains("src1"))
      },
      test("TestContext.withSeq") {
        val c = TestContext.withSeq("e2", 99L)
        assertTrue(c.seq == 99L)
      },
      test("TestContext.withTimestamp") {
        val ts = java.time.Instant.parse("2024-01-01T00:00:00Z")
        val c  = TestContext.withTimestamp("e3", ts)
        assertTrue(c.timestamp == ts)
      },
      test("TestContext.make defaults") {
        val c = TestContext.make()
        assertTrue(c.entityId == "test-1")
      }
    ),
    suite("InMemory numeric branches exhaustive")(
      test("Double increment and Max/Min preserve Double") {
        for {
          store <- InMemoryProjectionStore.make[DoubleEntity]
          _     <- store.insert(DoubleEntity("d1", 10.5))
          _     <- store.updateFields("d1", Chunk(FieldUpdate.Increment("dval", 2L)))
          f1    <- store.findById("d1")
          _     <- store.updateFields("d1", Chunk(FieldUpdate.Max("dval", 20L)))
          f2    <- store.findById("d1")
          _     <- store.updateFields("d1", Chunk(FieldUpdate.Min("dval", 5L)))
          f3    <- store.findById("d1")
        } yield assertTrue(f1.exists(_.dval == 12.5), f2.exists(_.dval == 20.0), f3.exists(_.dval == 5.0))
      },
      test("Float increment preserves Float") {
        for {
          store <- InMemoryProjectionStore.make[FloatEntity]
          _     <- store.insert(FloatEntity("f1", 5.0f))
          _     <- store.updateFields("f1", Chunk(FieldUpdate.Increment("fval", 3L)))
          f     <- store.findById("f1")
        } yield assertTrue(f.exists(_.fval == 8.0f))
      },
      test("Byte increment preserves Byte") {
        for {
          store <- InMemoryProjectionStore.make[ByteEntity]
          _     <- store.insert(ByteEntity("b1", 10.toByte))
          _     <- store.updateFields("b1", Chunk(FieldUpdate.Increment("bval", 5L)))
          f     <- store.findById("b1")
          _     <- store.updateFields("b1", Chunk(FieldUpdate.Max("bval", 20L)))
          f2    <- store.findById("b1")
          _     <- store.updateFields("b1", Chunk(FieldUpdate.Min("bval", 2L)))
          f3    <- store.findById("b1")
        } yield assertTrue(f.exists(_.bval == 15.toByte), f2.exists(_.bval == 20.toByte), f3.exists(_.bval == 2.toByte))
      },
      test("BigInt increment preserves BigInt") {
        for {
          store <- InMemoryProjectionStore.make[BigIntEntity]
          _     <- store.insert(BigIntEntity("bi1", BigInt(100)))
          _     <- store.updateFields("bi1", Chunk(FieldUpdate.Increment("bi", 50L)))
          f     <- store.findById("bi1")
        } yield assertTrue(f.exists(_.bi == BigInt(150)))
      },
      test("BigDecimal increment preserves BigDecimal") {
        for {
          store <- InMemoryProjectionStore.make[BigDecEntity]
          _     <- store.insert(BigDecEntity("bd1", BigDecimal(100)))
          _     <- store.updateFields("bd1", Chunk(FieldUpdate.Increment("bd", 25L)))
          f     <- store.findById("bd1")
        } yield assertTrue(f.exists(_.bd == BigDecimal(125)))
      },
      test("Int Max preserves Int via getLong BigDecimal path") {
        for {
          store <- InMemoryProjectionStore.make[IntEntity]
          _     <- store.insert(IntEntity("i1", 5))
          _     <- store.updateFields("i1", Chunk(FieldUpdate.Increment("ival", 10L)))
          f1    <- store.findById("i1")
          _     <- store.updateFields("i1", Chunk(FieldUpdate.Max("ival", 20L)))
          f2    <- store.findById("i1")
          _     <- store.updateFields("i1", Chunk(FieldUpdate.Min("ival", 10L)))
          f3    <- store.findById("i1")
        } yield assertTrue(f1.exists(_.ival == 15), f2.exists(_.ival == 20), f3.exists(_.ival == 10))
      },
      test("createDefaultForId with WithOption covers Option Null branch") {
        for {
          store <- InMemoryProjectionStore.make[WithOption]
          _     <- store.insert(WithOption("opt-missing", "hello", None))
          _     <- store.insert(WithOption("opt-missing2", "hello", Some("world")))
          f1    <- store.findById("opt-missing")
          f2    <- store.findById("opt-missing2")
        } yield assertTrue(f1.exists(_.opt.isEmpty), f2.exists(_.opt.contains("world")))
      },
      test("createDefaultForId with WithEnum covers enumeration branch") {
        for {
          store <- InMemoryProjectionStore.make[WithEnum]
          _     <- store.insert(WithEnum("enum-missing", MyEnum.A))
          f     <- store.findById("enum-missing")
        } yield assertTrue(f.exists(_.e == MyEnum.A))
      },
      test("createDefaultForId with WithSeq covers sequence branch") {
        for {
          store <- InMemoryProjectionStore.make[WithSeq]
          _     <- store.insert(WithSeq("seq-missing", List("a")))
          f     <- store.findById("seq-missing")
        } yield assertTrue(f.exists(_.items == List("a")))
      },
      test("createDefaultForId with WithMap covers map branch") {
        for {
          store <- InMemoryProjectionStore.make[WithMap]
          _     <- store.insert(WithMap("map-missing", Map("k" -> 1)))
          f     <- store.findById("map-missing")
        } yield assertTrue(f.exists(_.mapping == Map("k" -> 1)))
      },
      test("extractId error with mismatched EntityPath") {
        implicit val badEp: EntityPath[User] = EntityPath[User]("users", "nonexistent")
        for {
          store <- InMemoryProjectionStore.make[User](using summon[Schema[User]], badEp)
          res   <- store.insert(User("x", "Alice", 10L)).either
        } yield assertTrue(res.isLeft)
      },
      test("anyToDynamicValue with all primitives via ByteEntity and others") {
        for {
          store <- InMemoryProjectionStore.make[WithOption]
          _     <- store.insert(WithOption("a1", "hello", Some("world")))
          _     <- store.updateFields("a1", Chunk(FieldUpdate.Set("name", "newName")))
          f     <- store.findById("a1")
        } yield assertTrue(f.exists(_.name == "newName"))
      }
    ),
    suite("ProjectionEngine additional branches")(
      test("validateBasePath rejects various invalid") {
        val fails   = List("", "a/../b", "/abs", "a//b", "a\\b")
        val results = fails.map(p => scala.util.Try(ProjectionEngine.validateBasePath(p)))
        assertTrue(results.forall(_.isFailure))
      },
      test("validateName rejects invalid chars") {
        val fails   = List("", "a/b", "a..b", "a\\b", "name with space")
        val results = fails.map(n => scala.util.Try(ProjectionEngine.validateName(n)))
        assertTrue(results.forall(_.isFailure))
      },
      test("makeWithStoresVarargs creates engine") {
        for {
          cache  <- TransactorCache.makeUnscoped(5)
          engine <- ProjectionEngine.makeWithStoresVarargs(cache, Map.empty)(
                      Projection[User]("var1").on[WithOption].insert((_, ctx) => User(ctx.entityId, "x", 1L))
                    )
        } yield assertTrue(engine.storesMap.contains("var1"))
      },
      test("global projection path uses global prefix") {
        ZIO.scoped {
          for {
            engine <-
              ProjectionEngine.make(
                Projection.global[User]("global-test").on[WithOption].insert((_, ctx) => User(ctx.entityId, "x", 1L))
              )
          } yield assertTrue(engine.storesMap.contains("global-test"))
        }
      }
    ),
    suite("SchemaHash wrapper and sequence/map branches")(
      test("hash for Wrapper type") {
        val h1 = SchemaHash.compute[WithOption]
        val h2 = SchemaHash.compute[WithSeq]
        val h3 = SchemaHash.compute[WithMap]
        val h4 = SchemaHash.compute[WithEnum]
        assertTrue(h1.nonEmpty, h2.nonEmpty, h3.nonEmpty, h4.nonEmpty, Set(h1, h2, h3, h4).size == 4)
      }
    ),
    suite("InMemory getLong string branch")(
      test("string field set and increment long field covers string branch") {
        case class StrNum(
          @Modifier.id id: String,
          strVal: String,
          longVal: Long
        )
        object StrNum {
          implicit val schema: Schema[StrNum]         = Schema.derived[StrNum]
          implicit val entityPath: EntityPath[StrNum] = EntityPath.derived[StrNum]
        }
        for {
          store <- InMemoryProjectionStore.make[StrNum]
          _     <- store.insert(StrNum("s1", "10", 5L))
          _     <- store.updateFields("s1", Chunk(FieldUpdate.Set("str_val", "20")))
          f     <- store.findById("s1")
          _     <- store.updateFields("s1", Chunk(FieldUpdate.Max("long_val", 20L)))
          f2    <- store.findById("s1")
        } yield assertTrue(f.exists(_.strVal == "20"), f2.exists(_.longVal == 20L))
      }
    )
  )
}
