package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.test._

object CollectionOpsSpec extends ZIOSpecDefault {

  case class TodoList(name: String, items: Vector[String])
  object TodoList extends CompanionOptics[TodoList] {
    implicit val schema: Schema[TodoList]     = Schema.derived
    val name: Lens[TodoList, String]          = optic(_.name)
    val items: Lens[TodoList, Vector[String]] = optic(_.items)
  }

  case class Team(name: String, members: Vector[Person])
  object Team extends CompanionOptics[Team] {
    implicit val schema: Schema[Team]       = Schema.derived
    val name: Lens[Team, String]            = optic(_.name)
    val members: Lens[Team, Vector[Person]] = optic(_.members)
  }

  case class Person(name: String, age: Int)
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
    val name: Lens[Person, String]      = optic(_.name)
    val age: Lens[Person, Int]          = optic(_.age)
  }

  case class Config(settings: Map[String, String])
  object Config extends CompanionOptics[Config] {
    implicit val schema: Schema[Config]             = Schema.derived
    val settings: Lens[Config, Map[String, String]] = optic(_.settings)
  }

  case class NestedConfig(name: String, metadata: Map[String, Metadata])
  object NestedConfig extends CompanionOptics[NestedConfig] {
    implicit val schema: Schema[NestedConfig]               = Schema.derived
    val name: Lens[NestedConfig, String]                    = optic(_.name)
    val metadata: Lens[NestedConfig, Map[String, Metadata]] = optic(_.metadata)
  }

  case class Metadata(version: Int, description: String)
  object Metadata extends CompanionOptics[Metadata] {
    implicit val schema: Schema[Metadata]   = Schema.derived
    val version: Lens[Metadata, Int]        = optic(_.version)
    val description: Lens[Metadata, String] = optic(_.description)
  }

  def spec: Spec[TestEnvironment, Any] = suite("CollectionOpsSpec")(
    sequenceOperationTests,
    mapOperationTests,
    nestedOperationTests,
    edgeCaseTests
  )

  val sequenceOperationTests = suite("Sequence Operations")(
    suite("Patch.append")(
      test("appends elements to end of empty sequence") {
        val list   = TodoList("Tasks", Vector.empty)
        val patch  = Patch.append(TodoList.items, Vector("Buy milk", "Walk dog"))
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Buy milk", "Walk dog"))))
      },
      test("appends elements to end of non-empty sequence") {
        val list   = TodoList("Tasks", Vector("Existing task"))
        val patch  = Patch.append(TodoList.items, Vector("Buy milk", "Walk dog"))
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Existing task", "Buy milk", "Walk dog"))))
      },
      test("appends empty vector does nothing") {
        val list   = TodoList("Tasks", Vector("Task 1"))
        val patch  = Patch.append(TodoList.items, Vector.empty[String])
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(list))
      }
    ),
    suite("Patch.insertAt")(
      test("inserts at beginning of sequence") {
        val list   = TodoList("Tasks", Vector("Task 2", "Task 3"))
        val patch  = Patch.insertAt(TodoList.items, 0, Vector("Task 1"))
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3"))))
      },
      test("inserts in middle of sequence") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 3"))
        val patch  = Patch.insertAt(TodoList.items, 1, Vector("Task 2"))
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3"))))
      },
      test("inserts at end of sequence") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 2"))
        val patch  = Patch.insertAt(TodoList.items, 2, Vector("Task 3"))
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3"))))
      },
      test("fails on out of bounds index in Strict mode") {
        val list   = TodoList("Tasks", Vector("Task 1"))
        val patch  = Patch.insertAt(TodoList.items, 5, Vector("Task 2"))
        val result = patch(list, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("clamps index in Clobber mode") {
        val list   = TodoList("Tasks", Vector("Task 1"))
        val patch  = Patch.insertAt(TodoList.items, 5, Vector("Task 2"))
        val result = patch(list, PatchMode.Clobber)
        // In clobber mode, index should be clamped to the end
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 2"))))
      },
      test("inserts multiple elements") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 4"))
        val patch  = Patch.insertAt(TodoList.items, 1, Vector("Task 2", "Task 3"))
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3", "Task 4"))))
      }
    ),
    suite("Patch.deleteAt")(
      test("deletes single element") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3"))
        val patch  = Patch.deleteAt(TodoList.items, 1, 1)
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 3"))))
      },
      test("deletes multiple elements") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3", "Task 4"))
        val patch  = Patch.deleteAt(TodoList.items, 1, 2)
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 4"))))
      },
      test("deletes from beginning") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3"))
        val patch  = Patch.deleteAt(TodoList.items, 0, 1)
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 2", "Task 3"))))
      },
      test("deletes from end") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3"))
        val patch  = Patch.deleteAt(TodoList.items, 2, 1)
        val result = patch(list, PatchMode.Strict)
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 2"))))
      },
      test("fails on out of bounds range in Strict mode") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 2"))
        val patch  = Patch.deleteAt(TodoList.items, 1, 5)
        val result = patch(list, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("clamps range in Clobber mode") {
        val list   = TodoList("Tasks", Vector("Task 1", "Task 2", "Task 3"))
        val patch  = Patch.deleteAt(TodoList.items, 1, 10)
        val result = patch(list, PatchMode.Clobber)
        // Should delete from index 1 to end
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 1"))))
      }
    ),
    suite("Patch.modifyAt")(
      test("modifies element with Set operation") {
        val team  = Team("Engineering", Vector(Person("Alice", 30), Person("Bob", 25)))
        val patch = Patch.modifyAt(
          Team.members,
          0,
          Patch.set(Person.age, 31)
        )
        val result = patch(team, PatchMode.Strict)
        assertTrue(result == Right(Team("Engineering", Vector(Person("Alice", 31), Person("Bob", 25)))))
      },
      test("modifies element with multiple operations") {
        val team         = Team("Engineering", Vector(Person("Alice", 30), Person("Bob", 25)))
        val elementPatch = Patch.set(Person.name, "Alice Smith") ++ Patch.set(Person.age, 31)
        val patch        = Patch.modifyAt(Team.members, 0, elementPatch)
        val result       = patch(team, PatchMode.Strict)
        assertTrue(result == Right(Team("Engineering", Vector(Person("Alice Smith", 31), Person("Bob", 25)))))
      },
      test("fails on out of bounds index") {
        val team   = Team("Engineering", Vector(Person("Alice", 30)))
        val patch  = Patch.modifyAt(Team.members, 5, Patch.set(Person.age, 31))
        val result = patch(team, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("Sequence composition")(
      test("composes multiple sequence operations") {
        val list  = TodoList("Tasks", Vector("Task 1"))
        val patch = Patch.append(TodoList.items, Vector("Task 2")) ++
          Patch.insertAt(TodoList.items, 0, Vector("Task 0")) ++
          Patch.deleteAt(TodoList.items, 2, 1)
        val result = patch(list, PatchMode.Strict)
        // After append: ["Task 1", "Task 2"]
        // After insert: ["Task 0", "Task 1", "Task 2"]
        // After delete: ["Task 0", "Task 1"]
        assertTrue(result == Right(TodoList("Tasks", Vector("Task 0", "Task 1"))))
      }
    )
  )

  val mapOperationTests = suite("Map Operations")(
    suite("Patch.addKey")(
      test("adds key to empty map") {
        val config = Config(Map.empty)
        val patch  = Patch.addKey(Config.settings, "theme", "dark")
        val result = patch(config, PatchMode.Strict)
        assertTrue(result == Right(Config(Map("theme" -> "dark"))))
      },
      test("adds key to non-empty map") {
        val config = Config(Map("lang" -> "en"))
        val patch  = Patch.addKey(Config.settings, "theme", "dark")
        val result = patch(config, PatchMode.Strict)
        assertTrue(result == Right(Config(Map("lang" -> "en", "theme" -> "dark"))))
      },
      test("fails when key exists in Strict mode") {
        val config = Config(Map("theme" -> "light"))
        val patch  = Patch.addKey(Config.settings, "theme", "dark")
        val result = patch(config, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("overwrites when key exists in Clobber mode") {
        val config = Config(Map("theme" -> "light"))
        val patch  = Patch.addKey(Config.settings, "theme", "dark")
        val result = patch(config, PatchMode.Clobber)
        assertTrue(result == Right(Config(Map("theme" -> "dark"))))
      },
      test("skips when key exists in Lenient mode") {
        val config = Config(Map("theme" -> "light"))
        val patch  = Patch.addKey(Config.settings, "theme", "dark")
        val result = patch(config, PatchMode.Lenient)
        // In lenient mode, the operation is skipped, map unchanged
        assertTrue(result == Right(Config(Map("theme" -> "light"))))
      }
    ),
    suite("Patch.removeKey")(
      test("removes existing key") {
        val config = Config(Map("theme" -> "dark", "lang" -> "en"))
        val patch  = Patch.removeKey(Config.settings, "theme")
        val result = patch(config, PatchMode.Strict)
        assertTrue(result == Right(Config(Map("lang" -> "en"))))
      },
      test("removes only key") {
        val config = Config(Map("theme" -> "dark"))
        val patch  = Patch.removeKey(Config.settings, "theme")
        val result = patch(config, PatchMode.Strict)
        assertTrue(result == Right(Config(Map.empty)))
      },
      test("fails when key missing in Strict mode") {
        val config = Config(Map("lang" -> "en"))
        val patch  = Patch.removeKey(Config.settings, "theme")
        val result = patch(config, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("skips when key missing in Lenient mode") {
        val config = Config(Map("lang" -> "en"))
        val patch  = Patch.removeKey(Config.settings, "theme")
        val result = patch(config, PatchMode.Lenient)
        assertTrue(result == Right(Config(Map("lang" -> "en"))))
      },
      test("does nothing when key missing in Clobber mode") {
        val config = Config(Map("lang" -> "en"))
        val patch  = Patch.removeKey(Config.settings, "theme")
        val result = patch(config, PatchMode.Clobber)
        assertTrue(result == Right(Config(Map("lang" -> "en"))))
      }
    ),
    suite("Patch.modifyKey")(
      test("modifies existing key value") {
        val config = NestedConfig("app", Map("v1" -> Metadata(1, "First")))
        val patch  = Patch.modifyKey(
          NestedConfig.metadata,
          "v1",
          Patch.set(Metadata.version, 2)
        )
        val result = patch(config, PatchMode.Strict)
        assertTrue(result == Right(NestedConfig("app", Map("v1" -> Metadata(2, "First")))))
      },
      test("modifies with multiple operations") {
        val config     = NestedConfig("app", Map("v1" -> Metadata(1, "First")))
        val valuePatch = Patch.set(Metadata.version, 2) ++ Patch.set(Metadata.description, "Second")
        val patch      = Patch.modifyKey(NestedConfig.metadata, "v1", valuePatch)
        val result     = patch(config, PatchMode.Strict)
        assertTrue(result == Right(NestedConfig("app", Map("v1" -> Metadata(2, "Second")))))
      },
      test("fails when key missing") {
        val config = NestedConfig("app", Map("v1" -> Metadata(1, "First")))
        val patch  = Patch.modifyKey(
          NestedConfig.metadata,
          "v2",
          Patch.set(Metadata.version, 2)
        )
        val result = patch(config, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("Map composition")(
      test("composes multiple map operations") {
        val config = Config(Map("lang" -> "en"))
        val patch  = Patch.addKey(Config.settings, "theme", "dark") ++
          Patch.addKey(Config.settings, "font", "mono") ++
          Patch.removeKey(Config.settings, "lang")
        val result = patch(config, PatchMode.Strict)
        assertTrue(result == Right(Config(Map("theme" -> "dark", "font" -> "mono"))))
      }
    )
  )

  val nestedOperationTests = suite("Nested Operations")(
    test("modifyAt on nested structure") {
      val team  = Team("Engineering", Vector(Person("Alice", 30), Person("Bob", 25)))
      val patch = Patch.modifyAt(
        Team.members,
        0,
        Patch.set(Person.name, "Alice Smith")
      )
      val result = patch(team, PatchMode.Strict)
      assertTrue(result == Right(Team("Engineering", Vector(Person("Alice Smith", 30), Person("Bob", 25)))))
    },
    test("modifyKey on nested structure") {
      val config = NestedConfig("app", Map("v1" -> Metadata(1, "First")))
      val patch  = Patch.modifyKey(
        NestedConfig.metadata,
        "v1",
        Patch.set(Metadata.description, "Updated")
      )
      val result = patch(config, PatchMode.Strict)
      assertTrue(result == Right(NestedConfig("app", Map("v1" -> Metadata(1, "Updated")))))
    }
  )

  val edgeCaseTests = suite("Edge Cases")(
    test("empty sequence operations in Lenient mode") {
      val list   = TodoList("Tasks", Vector.empty)
      val patch  = Patch.deleteAt(TodoList.items, 0, 1)
      val result = patch(list, PatchMode.Lenient)
      // Should skip the operation
      assertTrue(result == Right(list))
    },
    test("empty map operations in Lenient mode") {
      val config = Config(Map.empty)
      val patch  = Patch.removeKey(Config.settings, "nonexistent")
      val result = patch(config, PatchMode.Lenient)
      assertTrue(result == Right(config))
    },
    test("unicode strings in sequences") {
      val list   = TodoList("Tasks", Vector("Hello"))
      val patch  = Patch.append(TodoList.items, Vector("こんにちは", "🎉"))
      val result = patch(list, PatchMode.Strict)
      assertTrue(result == Right(TodoList("Tasks", Vector("Hello", "こんにちは", "🎉"))))
    },
    test("unicode keys in maps") {
      val config = Config(Map.empty)
      val patch  = Patch.addKey(Config.settings, "言語", "日本語")
      val result = patch(config, PatchMode.Strict)
      assertTrue(result == Right(Config(Map("言語" -> "日本語"))))
    },
    test("large sequence operations") {
      val largeList = (1 to 1000).map(i => s"Task $i").toVector
      val list      = TodoList("Tasks", largeList)
      val patch     = Patch.append(TodoList.items, Vector("Task 1001"))
      val result    = patch(list, PatchMode.Strict)
      assertTrue(result.isRight && result.exists(_.items.length == 1001))
    },
    test("composition with empty patch") {
      val list     = TodoList("Tasks", Vector("Task 1"))
      val patch1   = Patch.append(TodoList.items, Vector("Task 2"))
      val patch2   = Patch.empty[TodoList]
      val combined = patch1 ++ patch2
      val result   = combined(list, PatchMode.Strict)
      assertTrue(result == Right(TodoList("Tasks", Vector("Task 1", "Task 2"))))
    }
  )
}
