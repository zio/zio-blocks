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

import zio.blocks.chunk.Chunk
import zio.test._
import java.time.Instant

object ProjectionActionSpec extends ZIOSpecDefault {

  case class User(firstName: String, lastName: String, userCount: Long, score: Int)

  def spec: Spec[Any, Any] = suite("ProjectionActionSpec")(
    projectionActionSuite,
    fieldUpdateSuite,
    selectorMacroSuite,
    projectionContextSuite
  )

  // =========================================================================
  // ProjectionAction ADT tests
  // =========================================================================
  private val projectionActionSuite = suite("ProjectionAction")(
    suite("Insert")(
      test("Insert carries a value") {
        val action = ProjectionAction.Insert("hello")
        assertTrue(action match { case ProjectionAction.Insert(v) => v == "hello"; case _ => false })
      },
      test("Insert with complex type") {
        case class Foo(name: String, age: Int)
        val action = ProjectionAction.Insert(Foo("Alice", 30))
        assertTrue(action match {
          case ProjectionAction.Insert(foo: Foo @unchecked) => foo.name == "Alice" && foo.age == 30; case _ => false
        })
      },
      test("Insert is covariant") {
        val stringAction: ProjectionAction[String] = ProjectionAction.Insert("hello")
        val anyAction: ProjectionAction[Any]       = stringAction
        assertTrue(anyAction == ProjectionAction.Insert("hello"))
      }
    ),
    suite("Upsert")(
      test("Upsert carries a value") {
        val action = ProjectionAction.Upsert(42)
        assertTrue(action match { case ProjectionAction.Upsert(v) => v == 42; case _ => false })
      },
      test("Upsert is covariant") {
        val intAction: ProjectionAction[Int]  = ProjectionAction.Upsert(99)
        val anyAction: ProjectionAction[Long] = intAction.asInstanceOf[ProjectionAction[Long]]
        assertTrue(anyAction == ProjectionAction.Upsert(99))
      }
    ),
    suite("Update")(
      test("Update carries modifications") {
        val mods = Chunk(
          FieldUpdate.Set("name", "Bob"),
          FieldUpdate.Increment("count", 5L)
        )
        val action = ProjectionAction.Update(mods)
        assertTrue(action match { case ProjectionAction.Update(m) => m.length == 2; case _ => false })
      },
      test("Update with empty modifications") {
        val action = ProjectionAction.Update(Chunk.empty)
        assertTrue(action match { case ProjectionAction.Update(m) => m.isEmpty; case _ => false })
      }
    ),
    suite("Sentinels")(
      test("Delete has no payload") {
        val action: ProjectionAction[Nothing] = ProjectionAction.Delete
        assertTrue(action == ProjectionAction.Delete)
      },
      test("Truncate has no payload") {
        val action: ProjectionAction[Nothing] = ProjectionAction.Truncate
        assertTrue(action == ProjectionAction.Truncate)
      },
      test("Noop has no payload") {
        val action: ProjectionAction[Nothing] = ProjectionAction.Noop
        assertTrue(action == ProjectionAction.Noop)
      },
      test("Sentinel companion aliases") {
        assertTrue(ProjectionAction.delete == ProjectionAction.Delete)
        assertTrue(ProjectionAction.truncate == ProjectionAction.Truncate)
        assertTrue(ProjectionAction.noop == ProjectionAction.Noop)
      }
    ),
    suite("Exhaustive pattern matching")(
      test("pattern match covers all cases") {
        def describe[A](action: ProjectionAction[A]): String = action match {
          case ProjectionAction.Insert(v)    => s"insert:$v"
          case ProjectionAction.Upsert(v)    => s"upsert:$v"
          case ProjectionAction.Update(mods) => s"update:${mods.length}"
          case ProjectionAction.Delete       => "delete"
          case ProjectionAction.Truncate     => "truncate"
          case ProjectionAction.Noop         => "noop"
        }
        assertTrue(describe(ProjectionAction.Insert(1)) == "insert:1")
        assertTrue(describe(ProjectionAction.Upsert("x")) == "upsert:x")
        assertTrue(describe(ProjectionAction.Update(Chunk.empty)) == "update:0")
        assertTrue(describe(ProjectionAction.Delete) == "delete")
        assertTrue(describe(ProjectionAction.Truncate) == "truncate")
        assertTrue(describe(ProjectionAction.Noop) == "noop")
      }
    )
  )

  // =========================================================================
  // FieldUpdate ADT tests
  // =========================================================================
  private val fieldUpdateSuite = suite("FieldUpdate")(
    suite("Construction via string")(
      test("Set with string field") {
        val fu = FieldUpdate.Set("name", "Alice")
        assertTrue(fu match { case FieldUpdate.Set(f, v) => f == "name" && v == "Alice"; case _ => false })
      },
      test("Increment with string field") {
        val fu = FieldUpdate.Increment("count", 1L)
        assertTrue(fu match { case FieldUpdate.Increment(f, b) => f == "count" && b == 1L; case _ => false })
      },
      test("Decrement with string field") {
        val fu = FieldUpdate.Decrement("count", 3L)
        assertTrue(fu match { case FieldUpdate.Decrement(f, b) => f == "count" && b == 3L; case _ => false })
      },
      test("Max with string field") {
        val fu = FieldUpdate.Max("score", 100L)
        assertTrue(fu match { case FieldUpdate.Max(f, v) => f == "score" && v == 100L; case _ => false })
      },
      test("Min with string field") {
        val fu = FieldUpdate.Min("score", 0L)
        assertTrue(fu match { case FieldUpdate.Min(f, v) => f == "score" && v == 0L; case _ => false })
      }
    ),
    suite("Companion factory methods (raw field name)")(
      test("FieldUpdate.apply creates Set") {
        val fu = FieldUpdate("email", "test@example.com")
        assertTrue(fu.isInstanceOf[FieldUpdate.Set])
        assertTrue(fu match { case FieldUpdate.Set(f, _) => f == "email"; case _ => false })
      },
      test("FieldUpdate.increment creates Increment") {
        val fu = FieldUpdate.increment("views", 10L)
        assertTrue(fu match { case FieldUpdate.Increment(f, b) => f == "views" && b == 10L; case _ => false })
      },
      test("FieldUpdate.decrement creates Decrement") {
        val fu = FieldUpdate.decrement("credits", 5L)
        assertTrue(fu match { case FieldUpdate.Decrement(f, b) => f == "credits" && b == 5L; case _ => false })
      },
      test("FieldUpdate.maxValue creates Max") {
        val fu = FieldUpdate.maxValue("temp", 42L)
        assertTrue(fu match { case FieldUpdate.Max(f, v) => f == "temp" && v == 42L; case _ => false })
      },
      test("FieldUpdate.minValue creates Min") {
        val fu = FieldUpdate.minValue("temp", -10L)
        assertTrue(fu match { case FieldUpdate.Min(f, v) => f == "temp" && v == -10L; case _ => false })
      }
    ),
    suite("Equality")(
      test("Set equality") {
        assertTrue(FieldUpdate.Set("a", 1) == FieldUpdate.Set("a", 1))
      },
      test("Increment equality") {
        assertTrue(FieldUpdate.Increment("a", 5L) == FieldUpdate.Increment("a", 5L))
      },
      test("Different types are not equal") {
        assertTrue(FieldUpdate.Set("a", 1) != FieldUpdate.Increment("a", 1L))
      },
      test("Different fields are not equal") {
        assertTrue(FieldUpdate.Set("a", 1) != FieldUpdate.Set("b", 1))
      }
    )
  )

  // =========================================================================
  // Selector macro tests
  // =========================================================================
  private val selectorMacroSuite = suite("Selector macro helpers")(
    test("increment with camelCase selector maps to snake_case") {
      val fu = FieldUpdate.increment[User](_.userCount)
      assertTrue(fu match { case FieldUpdate.Increment(f, b) => f == "user_count" && b == 1L; case _ => false })
    },
    test("increment with custom by value") {
      val fu = FieldUpdate.increment[User](_.userCount, by = 10L)
      assertTrue(fu match { case FieldUpdate.Increment(f, b) => f == "user_count" && b == 10L; case _ => false })
    },
    test("decrement with selector") {
      val fu = FieldUpdate.decrement[User](_.userCount)
      assertTrue(fu match { case FieldUpdate.Decrement(f, b) => f == "user_count" && b == 1L; case _ => false })
    },
    test("setValue with selector") {
      val fu = FieldUpdate.setValue[User](_.firstName, "Alice")
      assertTrue(fu match { case FieldUpdate.Set(f, v) => f == "first_name" && v == "Alice"; case _ => false })
    },
    test("maxValue with selector") {
      val fu = FieldUpdate.maxValue[User](_.score, 100L)
      assertTrue(fu match { case FieldUpdate.Max(f, v) => f == "score" && v == 100L; case _ => false })
    },
    test("minValue with selector") {
      val fu = FieldUpdate.minValue[User](_.score, 0L)
      assertTrue(fu match { case FieldUpdate.Min(f, v) => f == "score" && v == 0L; case _ => false })
    },
    test("multiple word camelCase: firstName → first_name") {
      val fu = FieldUpdate.setValue[User](_.firstName, "Bob")
      assertTrue(fu match { case FieldUpdate.Set(f, _) => f == "first_name"; case _ => false })
    },
    test("single word selector stays unchanged") {
      val fu = FieldUpdate.increment[User](_.score, by = 1L)
      assertTrue(fu match { case FieldUpdate.Increment(f, _) => f == "score"; case _ => false })
    }
  )

  // =========================================================================
  // ProjectionContext tests
  // =========================================================================
  private val projectionContextSuite = suite("ProjectionContext")(
    test("construction with all fields") {
      val ts  = Instant.parse("2024-01-15T10:30:00Z")
      val ctx = ProjectionContext("entity-1", ts, 42L, Some("src-1"))
      assertTrue(ctx.entityId == "entity-1")
      assertTrue(ctx.timestamp == ts)
      assertTrue(ctx.seq == 42L)
      assertTrue(ctx.sourceEntityId == Some("src-1"))
    },
    test("construction without sourceEntityId") {
      val ts  = Instant.now()
      val ctx = ProjectionContext("entity-2", ts, 1L)
      assertTrue(ctx.entityId == "entity-2")
      assertTrue(ctx.sourceEntityId == None)
    },
    test("copy preserves fields") {
      val ts   = Instant.parse("2024-06-01T00:00:00Z")
      val ctx1 = ProjectionContext("e1", ts, 1L, None)
      val ctx2 = ctx1.copy(seq = 2L)
      assertTrue(ctx2.entityId == "e1")
      assertTrue(ctx2.seq == 2L)
    },
    test("equality") {
      val ts = Instant.parse("2024-01-01T00:00:00Z")
      assertTrue(
        ProjectionContext("e1", ts, 1L, None) == ProjectionContext("e1", ts, 1L, None)
      )
    },
    test("inequality on entityId") {
      val ts = Instant.parse("2024-01-01T00:00:00Z")
      assertTrue(
        ProjectionContext("e1", ts, 1L, None) != ProjectionContext("e2", ts, 1L, None)
      )
    }
  )
}
