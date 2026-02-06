package zio.blocks.scope

import zio.test._
import zio.blocks.scope.internal.MacroCore._

/**
 * Tests for the error rendering functions in MacroCore.
 *
 * These tests verify exact output - no fuzzy matching, no room for bugs to
 * hide.
 */
object ErrorRenderingSpec extends ZIOSpecDefault {

  def spec = suite("ErrorRenderingSpec")(
    suite("NotAClass error")(
      test("exact output for trait") {
        val error    = ScopeMacroError.NotAClass("MyTrait")
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Cannot derive Wire for MyTrait: not a class.
            |
            |  Hint: Provide a Wireable[MyTrait] instance
            |        or use Wire.Shared / Wire.Unique directly.
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      }
    ),
    suite("NoPrimaryCtor error")(
      test("exact output") {
        val error    = ScopeMacroError.NoPrimaryCtor("AbstractService")
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  AbstractService has no primary constructor.
            |
            |  Hint: Provide a Wireable[AbstractService] instance
            |        with a custom construction strategy.
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      }
    ),
    suite("SubtypeConflict error")(
      test("exact output") {
        val error    = ScopeMacroError.SubtypeConflict("BadService", "FileInputStream", "InputStream")
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Dependency type conflict in BadService
            |
            |  FileInputStream is a subtype of InputStream.
            |
            |  When both types are dependencies, Context cannot reliably distinguish
            |  them. The more specific type may be retrieved when the more general
            |  type is requested.
            |
            |  To fix this, wrap one or both types in a distinct wrapper:
            |
            |    case class WrappedInputStream(value: InputStream)
            |    or
            |    opaque type WrappedInputStream = InputStream
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      }
    ),

    suite("DuplicateProvider error")(
      test("exact output with locations") {
        val error = ScopeMacroError.DuplicateProvider(
          "Config",
          List(
            ProviderInfo("shared[Config]", Some("MyApp.scala:15")),
            ProviderInfo("Wire(...)", Some("MyApp.scala:16"))
          )
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Multiple providers for Config
            |
            |  Conflicting wires:
            |    1. shared[Config] at MyApp.scala:15
            |    2. Wire(...) at MyApp.scala:16
            |
            |  Hint: Remove duplicate wires or use distinct wrapper types.
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("exact output without locations") {
        val error = ScopeMacroError.DuplicateProvider(
          "Database",
          List(
            ProviderInfo("shared[Database]", None),
            ProviderInfo("unique[Database]", None)
          )
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Multiple providers for Database
            |
            |  Conflicting wires:
            |    1. shared[Database]
            |    2. unique[Database]
            |
            |  Hint: Remove duplicate wires or use distinct wrapper types.
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      }
    ),
    suite("DependencyCycle error")(
      test("exact output for simple cycle") {
        // ServiceA is 8 chars, so box width = 8 + 4 = 12
        val error    = ScopeMacroError.DependencyCycle(List("ServiceA", "ServiceB", "ServiceA"))
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Dependency cycle detected
            |
            |  Cycle:
            |    ┌────────────┐
            |    │            ▼
            |    ServiceA ──► ServiceB
            |    ServiceB ──► ServiceA
            |    ▲           │
            |    └────────────┘
            |
            |  Break the cycle by:
            |    • Introducing an interface/trait
            |    • Using lazy initialization
            |    • Restructuring dependencies
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("exact output for longer cycle") {
        // A is 1 char, so box width = 1 + 4 = 5
        val error    = ScopeMacroError.DependencyCycle(List("A", "B", "C", "A"))
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Dependency cycle detected
            |
            |  Cycle:
            |    ┌─────┐
            |    │     ▼
            |    A ──► B
            |    B ──► C
            |    C ──► A
            |    ▲    │
            |    └─────┘
            |
            |  Break the cycle by:
            |    • Introducing an interface/trait
            |    • Using lazy initialization
            |    • Restructuring dependencies
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("exact output for empty cycle") {
        val error    = ScopeMacroError.DependencyCycle(Nil)
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Dependency cycle detected (empty path)
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      }
    ),
    suite("MissingDependency error")(
      test("exact output with stack and simple requirements") {
        val error = ScopeMacroError.MissingDependency(
          requiredBy = "UserService",
          missing = List("Cache"),
          found = List("Database"),
          stack = List("Database"),
          dependencyTree = None
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Missing dependency: Cache
            |
            |  Stack:
            |    → Database
            |    → (root)
            |
            |  UserService requires:
            |    ✓ Database  — found in stack
            |    ✗ Cache  — missing
            |
            |  Hint: Either:
            |    • .injected[Cache].injected[UserService]     — Cache visible in stack
            |    • .injected[UserService](shared[Cache])      — Cache as private dependency
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("exact output with no stack") {
        val error = ScopeMacroError.MissingDependency(
          requiredBy = "App",
          missing = List("Config"),
          found = Nil,
          stack = Nil,
          dependencyTree = None
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Missing dependency: Config
            |
            |  App requires:
            |    ✗ Config  — missing
            |
            |  Hint: Either:
            |    • .injected[Config].injected[App]     — Config visible in stack
            |    • .injected[App](shared[Config])      — Config as private dependency
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      }
    ),
    suite("Dependency tree rendering")(
      test("simple tree with one level") {
        val tree = DepNode(
          "App",
          DepStatus.Pending,
          List(
            DepNode("Config", DepStatus.Found),
            DepNode("Database", DepStatus.Missing)
          )
        )
        val error = ScopeMacroError.MissingDependency(
          requiredBy = "App",
          missing = List("Database"),
          found = List("Config"),
          stack = Nil,
          dependencyTree = Some(tree)
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Missing dependency: Database
            |
            |  Dependency Tree:
            |    App
            |    ├── Config ✓
            |    └── Database ✗
            |
            |  App requires:
            |    ✓ Config  — found in stack
            |    ✗ Database  — missing
            |
            |  Hint: Either:
            |    • .injected[Database].injected[App]     — Database visible in stack
            |    • .injected[App](shared[Database])      — Database as private dependency
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("deep tree with multiple levels") {
        val tree = DepNode(
          "App",
          DepStatus.Pending,
          List(
            DepNode("Config", DepStatus.Found),
            DepNode(
              "UserService",
              DepStatus.Pending,
              List(
                DepNode(
                  "Database",
                  DepStatus.Found,
                  List(
                    DepNode("Config", DepStatus.Found)
                  )
                ),
                DepNode("Cache", DepStatus.Missing)
              )
            )
          )
        )
        val error = ScopeMacroError.MissingDependency(
          requiredBy = "UserService",
          missing = List("Cache"),
          found = List("Database", "Config"),
          stack = List("Database"),
          dependencyTree = Some(tree)
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Missing dependency: Cache
            |
            |  Stack:
            |    → Database
            |    → (root)
            |
            |  Dependency Tree:
            |    App
            |    ├── Config ✓
            |    └── UserService
            |        ├── Database ✓
            |        │   └── Config ✓
            |        └── Cache ✗
            |
            |  UserService requires:
            |    ✓ Database  — found in stack
            |    ✓ Config  — found in stack
            |    ✗ Cache  — missing
            |
            |  Hint: Either:
            |    • .injected[Cache].injected[UserService]     — Cache visible in stack
            |    • .injected[UserService](shared[Cache])      — Cache as private dependency
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("complex tree with shared references") {
        val tree = DepNode(
          "db",
          DepStatus.Pending,
          List(
            DepNode(
              "colors",
              DepStatus.Found,
              List(
                DepNode("green", DepStatus.Found),
                DepNode("nongreen", DepStatus.Found)
              )
            ),
            DepNode(
              "person",
              DepStatus.Pending,
              List(
                DepNode(
                  "type",
                  DepStatus.Pending,
                  List(
                    DepNode(
                      "alien",
                      DepStatus.Found,
                      List(DepNode("colors -> db/colors", DepStatus.Found))
                    ),
                    DepNode(
                      "female",
                      DepStatus.Found,
                      List(DepNode("colors -> db/colors", DepStatus.Found))
                    ),
                    DepNode(
                      "male",
                      DepStatus.Missing,
                      List(DepNode("colors -> db/colors", DepStatus.Found))
                    )
                  )
                )
              )
            )
          )
        )
        val error = ScopeMacroError.MissingDependency(
          requiredBy = "male",
          missing = List("male"),
          found = List("colors", "alien", "female"),
          stack = List("person", "type"),
          dependencyTree = Some(tree)
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Missing dependency: male
            |
            |  Stack:
            |    → person
            |    → type
            |    → (root)
            |
            |  Dependency Tree:
            |    db
            |    ├── colors ✓
            |    │   ├── green ✓
            |    │   └── nongreen ✓
            |    └── person
            |        └── type
            |            ├── alien ✓
            |            │   └── colors -> db/colors ✓
            |            ├── female ✓
            |            │   └── colors -> db/colors ✓
            |            └── male ✗
            |                └── colors -> db/colors ✓
            |
            |  male requires:
            |    ✓ colors  — found in stack
            |    ✓ alien  — found in stack
            |    ✓ female  — found in stack
            |    ✗ male  — missing
            |
            |  Hint: Either:
            |    • .injected[male].injected[male]     — male visible in stack
            |    • .injected[male](shared[male])      — male as private dependency
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("tree with only root node") {
        val tree  = DepNode("Service", DepStatus.Missing)
        val error = ScopeMacroError.MissingDependency(
          requiredBy = "App",
          missing = List("Service"),
          found = Nil,
          stack = Nil,
          dependencyTree = Some(tree)
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Missing dependency: Service
            |
            |  Dependency Tree:
            |    Service ✗
            |
            |  App requires:
            |    ✗ Service  — missing
            |
            |  Hint: Either:
            |    • .injected[Service].injected[App]     — Service visible in stack
            |    • .injected[App](shared[Service])      — Service as private dependency
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("wide tree with many siblings") {
        val tree = DepNode(
          "Service",
          DepStatus.Pending,
          List(
            DepNode("A", DepStatus.Found),
            DepNode("B", DepStatus.Found),
            DepNode("C", DepStatus.Missing),
            DepNode("D", DepStatus.Found),
            DepNode("E", DepStatus.Missing)
          )
        )
        val error = ScopeMacroError.MissingDependency(
          requiredBy = "Service",
          missing = List("C", "E"),
          found = List("A", "B", "D"),
          stack = Nil,
          dependencyTree = Some(tree)
        )
        val output   = error.render(color = false)
        val expected =
          """── Scope Error ─────────────────────────────────────────────────────────────────
            |
            |  Missing dependency: C
            |
            |  Dependency Tree:
            |    Service
            |    ├── A ✓
            |    ├── B ✓
            |    ├── C ✗
            |    ├── D ✓
            |    └── E ✗
            |
            |  Service requires:
            |    ✓ A  — found in stack
            |    ✓ B  — found in stack
            |    ✓ D  — found in stack
            |    ✗ C  — missing
            |    ✗ E  — missing
            |
            |  Hint: Either:
            |    • .injected[C].injected[Service]     — C visible in stack
            |    • .injected[Service](shared[C])      — C as private dependency
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      }
    ),
    suite("ANSI color output")(
      test("NotAClass with colors contains ANSI codes") {
        val error  = ScopeMacroError.NotAClass("MyTrait")
        val output = error.render(color = true)
        assertTrue(
          output.contains("\u001b["),
          output.contains("\u001b[0m"),
          output.contains("MyTrait"),
          output.contains("not a class")
        )
      }
    ),
    suite("LeakWarning")(
      test("exact output for simple leak") {
        val warning  = ScopeMacroWarning.LeakWarning("scoped", "parent.Tag")
        val output   = warning.render(color = false)
        val expected =
          """── Scope Warning ───────────────────────────────────────────────────────────────
            |
            |  leak(scoped)
            |       ^
            |       |
            |
            |  Warning: scoped is being leaked from scope parent.Tag.
            |  This may result in undefined behavior.
            |
            |  Hint:
            |     If you know this data type is not resourceful, then add a given ScopeEscape
            |     for it so you do not need to leak it.
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("exact output for chained expression") {
        val warning  = ScopeMacroWarning.LeakWarning("$[Request].body.getInputStream()", "parent.Tag")
        val output   = warning.render(color = false)
        val expected =
          """── Scope Warning ───────────────────────────────────────────────────────────────
            |
            |  leak($[Request].body.getInputStream())
            |       ^
            |       |
            |
            |  Warning: $[Request].body.getInputStream() is being leaked from scope parent.Tag.
            |  This may result in undefined behavior.
            |
            |  Hint:
            |     If you know this data type is not resourceful, then add a given ScopeEscape
            |     for it so you do not need to leak it.
            |
            |────────────────────────────────────────────────────────────────────────────────""".stripMargin
        assertTrue(output == expected)
      },
      test("warning with colors contains ANSI codes") {
        val warning = ScopeMacroWarning.LeakWarning("scoped", "parent.Tag")
        val output  = warning.render(color = true)
        assertTrue(
          output.contains("\u001b["),
          output.contains("\u001b[0m"),
          output.contains("scoped"),
          output.contains("is being leaked")
        )
      }
    )
  )
}
