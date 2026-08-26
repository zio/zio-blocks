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
import zio.blocks.schema.{Modifier, Schema}
import zio.test._

import java.time.Instant

object ProjectionSpecSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Test models
  // ---------------------------------------------------------------------------

  case class User(
    @Modifier.id id: String,
    name: String,
    email: String,
    firstName: String,
    userCount: Long,
    score: Int
  )
  object User {
    implicit val schema: Schema[User]         = Schema.derived[User]
    implicit val entityPath: EntityPath[User] = EntityPath.derived[User]
  }

  case class Counter(total: Long)
  object Counter {
    implicit val schema: Schema[Counter] = Schema.derived[Counter]
  }

  case class UserCreated(name: String, email: String)
  object UserCreated {
    implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
  }

  case class UserRenamed(newName: String)
  object UserRenamed {
    implicit val schema: Schema[UserRenamed] = Schema.derived[UserRenamed]
  }

  case class UserEmailChanged(newEmail: String)
  object UserEmailChanged {
    implicit val schema: Schema[UserEmailChanged] = Schema.derived[UserEmailChanged]
  }

  case class UserDeleted()
  object UserDeleted {
    implicit val schema: Schema[UserDeleted] = Schema.derived[UserDeleted]
  }

  case class CountInc(by: Long)
  object CountInc {
    implicit val schema: Schema[CountInc] = Schema.derived[CountInc]
  }

  case class RepoCreated(ownerId: String, repoName: String)
  object RepoCreated {
    implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
  }

  case class RepoDeleted(repoName: String)
  object RepoDeleted {
    implicit val schema: Schema[RepoDeleted] = Schema.derived[RepoDeleted]
  }

  private def ctx(entityId: String = "entity-1", seq: Long = 1L): ProjectionContext =
    ProjectionContext(entityId, Instant.parse("2024-01-01T00:00:00Z"), seq)

  def spec: Spec[Any, Any] = suite("ProjectionSpecSpec")(
    suite("Builder construction")(
      test("apply with EntityPath creates PerEntity spec") {
        val s = ProjectionSpec[User]("users")
        assertTrue(s.name == "users", !s.isGlobal, s.entityPath.isDefined, s.bindings.isEmpty)
      },
      test("global factory creates Global spec") {
        val s = ProjectionSpec.global[Counter]("counters")
        assertTrue(s.name == "counters", s.isGlobal, s.entityPath.isEmpty, s.scope == ProjectionScope.Global)
      },
      test("explicit EntityPath apply") {
        val ep = EntityPath[User]("custom", "id")
        val s  = ProjectionSpec[User]("custom", ep)
        assertTrue(s.entityPath.get.basePath == "custom", s.entityPath.get.entityIdField == "id")
      },
      test("toString contains name") {
        val s = ProjectionSpec[User]("my-projection")
        assertTrue(s.toString.contains("my-projection"))
      }
    ),
    suite("Insert handler")(
      test("insert handler dispatches to Insert action") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
        val action = s.handle(UserCreated("Alice", "a@b.com"), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Insert(u: User @unchecked) => u.name == "Alice" && u.email == "a@b.com"
          case _                                           => false
        })
      },
      test("insert uses context entityId") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, c) => User(c.entityId, e.name, e.email, e.name, 0L, 0))
        val c      = ctx("my-id")
        val action = s.handle(UserCreated("Bob", "b@b.com"), c)
        assertTrue(action.exists {
          case ProjectionAction.Insert(u: User @unchecked) => u.id == "my-id"
          case _                                           => false
        })
      },
      test("insert handler stored internally") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
        assertTrue(s.allHandlers.size == 1, s.allHandlers.head.tag.nonEmpty)
      },
      test("multiple insert handlers dispatch by type") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
          .on[UserRenamed]
          .insert((e, _) => User("1", e.newName, "", e.newName, 0L, 0))
        val a1 = s.handle(UserCreated("Alice", "a@b.com"), ctx())
        val a2 = s.handle(UserRenamed("Bob"), ctx())
        assertTrue(
          a1.exists { case ProjectionAction.Insert(u: User @unchecked) => u.name == "Alice"; case _ => false },
          a2.exists { case ProjectionAction.Insert(u: User @unchecked) => u.name == "Bob"; case _ => false }
        )
      }
    ),
    suite("Update handler with selector macro")(
      test("update _.name maps to snake_case name") {
        val s = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .update(_.name)((e, _) => e.newName)
        val action = s.handle(UserRenamed("Charlie"), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) =>
            mods.size == 1 && mods.head
              .isInstanceOf[FieldUpdate.Set] && mods.head.asInstanceOf[FieldUpdate.Set].field == "name" &&
            mods.head.asInstanceOf[FieldUpdate.Set].value == "Charlie"
          case _ => false
        })
      },
      test("update _.firstName maps to first_name") {
        val s = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .update(_.firstName)((e, _) => e.newName)
        val action = s.handle(UserRenamed("Dave"), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) => mods.head.asInstanceOf[FieldUpdate.Set].field == "first_name"
          case _                             => false
        })
      },
      test("update _.userCount maps to user_count") {
        val s = ProjectionSpec[User]("users")
          .on[CountInc]
          .update(_.userCount)((e, _) => e.by)
        val action = s.handle(CountInc(42L), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) => mods.head.asInstanceOf[FieldUpdate.Set].field == "user_count"
          case _                             => false
        })
      },
      test("update _.email maps to email") {
        val s = ProjectionSpec[User]("users")
          .on[UserEmailChanged]
          .update(_.email)((e, _) => e.newEmail)
        val action = s.handle(UserEmailChanged("new@x.com"), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) =>
            val set = mods.head.asInstanceOf[FieldUpdate.Set]
            set.field == "email" && set.value == "new@x.com"
          case _ => false
        })
      },
      test("update with context seq") {
        val s = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .update(_.name)((_, c) => c.seq.toString)
        val action = s.handle(UserRenamed("X"), ctx(seq = 99L))
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) => mods.head.asInstanceOf[FieldUpdate.Set].value == "99"
          case _                             => false
        })
      },
      test("updateField string version") {
        val s = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .updateField("name")((e, _) => e.newName)
        val action = s.handle(UserRenamed("Eve"), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) => mods.head.asInstanceOf[FieldUpdate.Set].field == "name"
          case _                             => false
        })
      }
    ),
    suite("Delete handler")(
      test("delete handler returns Delete sentinel") {
        val s      = ProjectionSpec[User]("users").on[UserDeleted].delete
        val action = s.handle(UserDeleted(), ctx())
        assertTrue(action.contains(ProjectionAction.Delete))
      },
      test("delete handler stored internally") {
        val s = ProjectionSpec[User]("users").on[UserDeleted].delete
        assertTrue(s.allHandlers.size == 1)
      },
      test("delete dispatch no match returns None") {
        val s      = ProjectionSpec[User]("users").on[UserDeleted].delete
        val action = s.handle(UserCreated("A", "a@b.com"), ctx())
        assertTrue(action.isEmpty)
      }
    ),
    suite("Custom handler")(
      test("custom handler returns Upsert") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .custom((e, _) => ProjectionAction.Upsert(User("1", e.name, e.email, e.name, 0L, 0)))
        val action = s.handle(UserCreated("Frank", "f@b.com"), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Upsert(u: User @unchecked) => u.name == "Frank"
          case _                                           => false
        })
      },
      test("custom handler returns Noop") {
        val s      = ProjectionSpec[User]("users").on[UserCreated].custom((_, _) => ProjectionAction.Noop)
        val action = s.handle(UserCreated("G", "g@b.com"), ctx())
        assertTrue(action.contains(ProjectionAction.Noop))
      },
      test("custom handler returns Truncate") {
        val s      = ProjectionSpec[User]("users").on[UserDeleted].custom((_, _) => ProjectionAction.Truncate)
        val action = s.handle(UserDeleted(), ctx())
        assertTrue(action.contains(ProjectionAction.Truncate))
      }
    ),
    suite("Aggregate handler")(
      test("aggregate with FieldUpdate Increment") {
        val s      = ProjectionSpec[User]("users").on[CountInc].aggregate(FieldUpdate.Increment("user_count", 5L))
        val action = s.handle(CountInc(5L), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) => mods.head == FieldUpdate.Increment("user_count", 5L)
          case _                             => false
        })
      },
      test("aggregate with FieldUpdate macro increment") {
        val fu     = FieldUpdate.increment[User](_.userCount, 10L)
        val s      = ProjectionSpec[User]("users").on[CountInc].aggregate(fu)
        val action = s.handle(CountInc(10L), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) => mods.head.asInstanceOf[FieldUpdate.Increment].field == "user_count"
          case _                             => false
        })
      },
      test("aggregate with Chunk of updates") {
        val chunk  = Chunk(FieldUpdate.Set("name", "X"), FieldUpdate.Increment("user_count", 1L))
        val s      = ProjectionSpec[User]("users").on[CountInc].aggregate(chunk)
        val action = s.handle(CountInc(1L), ctx())
        assertTrue(action.exists {
          case ProjectionAction.Update(mods) => mods.size == 2
          case _                             => false
        })
      }
    ),
    suite("Multi-source routing")(
      test("from routedBy extracts routing key") {
        val s = ProjectionSpec[User]("users")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", e.repoName, 0L, 0))
        assertTrue(s.bindings.size == 1, s.bindings.head.sourceName == "repos")
        val key = s.routingKey(RepoCreated("owner-123", "my-repo"), ctx(), "repos")
        assertTrue(key.contains("owner-123"))
      },
      test("from routeToAll routing is RouteToAll and key is None") {
        val s = ProjectionSpec
          .global[Counter]("counters")
          .from("users")
          .routeToAll
          .on[UserCreated]
          .aggregate(FieldUpdate.increment[Counter](_.total, 1L))
        assertTrue(s.bindings.head.routing == RoutingMode.RouteToAll)
        val key = s.routingKey(UserCreated("A", "a@b.com"), ctx(), "users")
        assertTrue(key.isEmpty)
      },
      test("from routeToSelf routing is RouteToSelf and key is entityId") {
        val s = ProjectionSpec[User]("users")
          .from("users")
          .routeToSelf
          .on[UserCreated]
          .insert((e, c) => User(c.entityId, e.name, e.email, e.name, 0L, 0))
        assertTrue(s.bindings.head.routing == RoutingMode.RouteToSelf)
        val key = s.routingKey(UserCreated("A", "a@b.com"), ctx("my-entity"), "users")
        assertTrue(key.contains("my-entity"))
      },
      test("routedByField inline macro extracts key") {
        val s = ProjectionSpec[User]("users")
          .from("repos")
          .routedByField[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", e.repoName, 0L, 0))
        val key = s.routingKey(RepoCreated("field-owner", "r"), ctx(), "repos")
        assertTrue(key.contains("field-owner"))
      },
      test("multi-source groups handlers per source") {
        val s = ProjectionSpec[User]("users")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", e.repoName, 0L, 0))
          .from("users")
          .routeToSelf
          .on[UserCreated]
          .insert((e, _) => User("x", e.name, e.email, e.name, 0L, 0))
        assertTrue(
          s.bindings.size == 2,
          s.bindings.head.sourceName == "repos",
          s.bindings(1).sourceName == "users",
          s.bindings.head.handlers.size == 1,
          s.bindings(1).handlers.size == 1
        )
      },
      test("default routing when no from is RouteToSelf") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
        assertTrue(s.bindings.size == 1, s.bindings.head.sourceName == "_default")
        assertTrue(s.bindings.head.routing == RoutingMode.RouteToSelf)
      },
      test("sourceNames reflects all sources") {
        val s = ProjectionSpec[User]("users")
          .from("a")
          .routeToSelf
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
          .from("b")
          .routeToAll
          .on[UserDeleted]
          .delete
        assertTrue(s.sourceNames == List("a", "b"))
      }
    ),
    suite("Scope inference")(
      test("PerEntity scope for default") {
        val s =
          ProjectionSpec[User]("users").on[UserCreated].insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
        assertTrue(s.scope == ProjectionScope.PerEntity)
      },
      test("CrossEntity scope when routedBy") {
        val s = ProjectionSpec[User]("users")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", e.repoName, 0L, 0))
        assertTrue(s.scope.isInstanceOf[ProjectionScope.CrossEntity])
      },
      test("Global scope for global factory") {
        val s = ProjectionSpec.global[Counter]("counters")
        assertTrue(s.scope == ProjectionScope.Global, s.isGlobal)
      }
    ),
    suite("Selector macro column mapping")(
      test("firstName selector snake_case") {
        val s = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .update(_.firstName)((e, _) => e.newName)
        val action = s.handle(UserRenamed("Y"), ctx())
        action match {
          case Some(ProjectionAction.Update(mods)) =>
            val f = mods.head.asInstanceOf[FieldUpdate.Set].field
            assertTrue(f == "first_name")
          case _ => assertTrue(false)
        }
      },
      test("score selector unchanged") {
        val s = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .update(_.score)((_, _) => 100)
        val action = s.handle(UserRenamed("Z"), ctx())
        action match {
          case Some(ProjectionAction.Update(mods)) =>
            val f = mods.head.asInstanceOf[FieldUpdate.Set].field
            assertTrue(f == "score")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Handler dispatch")(
      test("dispatch matching event type returns action") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
          .on[UserDeleted]
          .delete
        assertTrue(s.dispatch(UserCreated("A", "a@b.com"), ctx()).isDefined)
        assertTrue(s.dispatch(UserDeleted(), ctx()).contains(ProjectionAction.Delete))
      },
      test("dispatch non-matching returns None") {
        val s =
          ProjectionSpec[User]("users").on[UserCreated].insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
        assertTrue(s.dispatch(UserDeleted(), ctx()).isEmpty)
      },
      test("dispatch respects handler order for same type first wins") {
        val s = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
          .on[UserCreated]
          .delete
        // Two handlers for same event type: first should win (our implementation finds first)
        val action = s.dispatch(UserCreated("A", "a@b.com"), ctx())
        assertTrue(action.exists(_.isInstanceOf[ProjectionAction.Insert[?]]))
      },
      test("handle returns None when no handlers") {
        val s = ProjectionSpec[User]("users")
        assertTrue(s.handle(UserCreated("A", "a@b.com"), ctx()).isEmpty)
      },
      test("validate returns empty for valid spec") {
        val s =
          ProjectionSpec[User]("users").on[UserCreated].insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
        assertTrue(s.validate().isEmpty)
      }
    ),
    suite("Internal representation")(
      test("bindings is List[SourceBinding] with correct type") {
        val s: ProjectionSpec[User] = ProjectionSpec[User]("users")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", e.repoName, 0L, 0))
        val bindings = s.bindings
        assertTrue(bindings.isInstanceOf[List[?]])
        assertTrue(bindings.head.isInstanceOf[SourceBinding[?, ?]])
      },
      test("handler tag is non-empty") {
        val s =
          ProjectionSpec[User]("users").on[UserCreated].insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
        assertTrue(s.allHandlers.head.tag == "UserCreated")
      },
      test("allHandlers aggregates across sources") {
        val s = ProjectionSpec[User]("users")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, _) => User(e.ownerId, e.repoName, "", e.repoName, 0L, 0))
          .from("users")
          .routeToSelf
          .on[UserCreated]
          .insert((e, _) => User("1", e.name, e.email, e.name, 0L, 0))
          .on[UserDeleted]
          .delete
        assertTrue(s.allHandlers.size == 3, s.bindings(1).handlers.size == 2)
      }
    )
  )
}
