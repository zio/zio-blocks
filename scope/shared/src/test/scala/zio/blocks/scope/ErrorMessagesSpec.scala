package zio.blocks.scope

import zio.blocks.scope.internal.ErrorMessages
import zio.blocks.scope.internal.ErrorMessages._
import zio.test._

/**
 * Comprehensive tests for the shared error message rendering.
 *
 * Tests verify:
 *   - All error/warning types render correctly
 *   - ASCII diagrams are properly formatted
 *   - Color codes are applied/omitted correctly
 *   - Content includes expected elements (type names, hints, etc.)
 */
object ErrorMessagesSpec extends ZIOSpecDefault {

  def spec = suite("ErrorMessages")(
    warningsSuite,
    typeStructureErrorsSuite,
    resourceFromErrorsSuite,
    wireResolutionErrorsSuite,
    unscopedDerivationErrorsSuite,
    dependencyTreeSuite,
    colorHandlingSuite
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Warnings (W1)
  // ─────────────────────────────────────────────────────────────────────────

  val warningsSuite = suite("Warnings")(
    suite("W1: LeakWarning")(
      test("includes source code and scope name") {
        val msg = ErrorMessages.renderLeakWarning("myResource", "MyScope", color = false)
        assertTrue(
          msg.contains("myResource"),
          msg.contains("MyScope"),
          msg.contains("Scope Warning")
        )
      },
      test("includes hint about ScopeEscape") {
        val msg = ErrorMessages.renderLeakWarning("data", "S", color = false)
        assertTrue(msg.contains("ScopeEscape"))
      },
      test("shows caret pointing at leaked value") {
        val msg = ErrorMessages.renderLeakWarning("x", "S", color = false)
        assertTrue(
          msg.contains("leak(x)"),
          msg.contains("^")
        )
      }
    )
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Type Structure Errors (E1-E3)
  // ─────────────────────────────────────────────────────────────────────────

  val typeStructureErrorsSuite = suite("Type Structure Errors")(
    suite("E1: NotAClass")(
      test("includes type name") {
        val msg = ErrorMessages.renderNotAClass("MyTrait", color = false)
        assertTrue(
          msg.contains("MyTrait"),
          msg.contains("not a class")
        )
      },
      test("suggests using Wire.Shared/Wire.Unique directly") {
        val msg = ErrorMessages.renderNotAClass("SomeTrait", color = false)
        assertTrue(
          msg.contains("Wire.Shared"),
          msg.contains("Wire.Unique")
        )
      },
      test("has Scope Error header") {
        val msg = ErrorMessages.renderNotAClass("T", color = false)
        assertTrue(msg.contains("Scope Error"))
      }
    ),
    suite("E2: NoPrimaryCtor")(
      test("includes type name") {
        val msg = ErrorMessages.renderNoPrimaryCtor("WeirdClass", color = false)
        assertTrue(
          msg.contains("WeirdClass"),
          msg.contains("primary constructor")
        )
      },
      test("suggests custom construction strategy") {
        val msg = ErrorMessages.renderNoPrimaryCtor("X", color = false)
        assertTrue(msg.contains("custom construction"))
      }
    ),
    suite("E3: SubtypeConflict")(
      test("shows both types in conflict") {
        val msg = ErrorMessages.renderSubtypeConflict(
          "MyService",
          "FileInputStream",
          "InputStream",
          color = false
        )
        assertTrue(
          msg.contains("FileInputStream"),
          msg.contains("InputStream"),
          msg.contains("subtype")
        )
      },
      test("includes parent type name") {
        val msg = ErrorMessages.renderSubtypeConflict("Parent", "Child", "Super", color = false)
        assertTrue(msg.contains("Parent"))
      },
      test("suggests wrapping in distinct type") {
        val msg = ErrorMessages.renderSubtypeConflict("S", "A", "B", color = false)
        assertTrue(
          msg.contains("wrap") || msg.contains("wrapper") || msg.contains("distinct"),
          msg.contains("opaque type") || msg.contains("case class")
        )
      }
    )
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Resource.from[T] Errors (E4-E5)
  // ─────────────────────────────────────────────────────────────────────────

  val resourceFromErrorsSuite = suite("Resource.from[T] Errors")(
    suite("E4: HasDependencies")(
      test("lists all dependencies") {
        val msg = ErrorMessages.renderHasDependencies(
          "MyService",
          List("Database", "Config", "Logger"),
          color = false
        )
        assertTrue(
          msg.contains("Database"),
          msg.contains("Config"),
          msg.contains("Logger")
        )
      },
      test("suggests using Resource.from with wires") {
        val msg = ErrorMessages.renderHasDependencies("S", List("Dep"), color = false)
        assertTrue(
          msg.contains("Resource.from[S](wire1, wire2, ...)") ||
            msg.contains("Resource.from[S]") && msg.contains("wire")
        )
      },
      test("shows type name") {
        val msg = ErrorMessages.renderHasDependencies("AppController", List("X"), color = false)
        assertTrue(msg.contains("AppController"))
      }
    ),
    suite("E5: UnsupportedImplicitParam")(
      test("shows problematic param type") {
        val msg = ErrorMessages.renderUnsupportedImplicitParam(
          "Service",
          "ExecutionContext",
          color = false
        )
        assertTrue(
          msg.contains("ExecutionContext"),
          msg.contains("Service")
        )
      },
      test("mentions Finalizer as supported") {
        val msg = ErrorMessages.renderUnsupportedImplicitParam("X", "Y", color = false)
        assertTrue(msg.contains("Finalizer"))
      }
    )
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Wire Resolution Errors (E6-E13)
  // ─────────────────────────────────────────────────────────────────────────

  val wireResolutionErrorsSuite = suite("Wire Resolution Errors")(
    suite("E6: CannotExtractWireTypes")(
      test("shows wire type") {
        val msg = ErrorMessages.renderCannotExtractWireTypes("BadWire[X]", color = false)
        assertTrue(msg.contains("BadWire[X]"))
      },
      test("suggests Wire[In, Out] format") {
        val msg = ErrorMessages.renderCannotExtractWireTypes("?", color = false)
        assertTrue(msg.contains("Wire[In, Out]"))
      }
    ),
    suite("E7: UnmakeableType")(
      test("shows type name") {
        val msg = ErrorMessages.renderUnmakeableType("String", List("MyService"), color = false)
        assertTrue(msg.contains("String"))
      },
      test("shows required-by chain") {
        val msg = ErrorMessages.renderUnmakeableType(
          "Int",
          List("Config", "Database", "App"),
          color = false
        )
        assertTrue(
          msg.contains("Config"),
          msg.contains("Database"),
          msg.contains("App"),
          msg.contains("Required by")
        )
      },
      test("suggests providing Wire(value)") {
        val msg = ErrorMessages.renderUnmakeableType("String", Nil, color = false)
        assertTrue(msg.contains("Wire("))
      }
    ),
    suite("E8: AbstractType")(
      test("shows type name") {
        val msg = ErrorMessages.renderAbstractType("DatabaseTrait", List("Service"), color = false)
        assertTrue(msg.contains("DatabaseTrait"))
      },
      test("suggests concrete implementation") {
        val msg = ErrorMessages.renderAbstractType("Trait", Nil, color = false)
        assertTrue(
          msg.contains("concrete") || msg.contains("ConcreteImpl")
        )
      }
    ),
    suite("E9: NoCtorForAutoCreate")(
      test("shows type name") {
        val msg = ErrorMessages.renderNoCtorForAutoCreate("WeirdType", List("Parent"), color = false)
        assertTrue(msg.contains("WeirdType"))
      },
      test("suggests explicit wire") {
        val msg = ErrorMessages.renderNoCtorForAutoCreate("X", Nil, color = false)
        assertTrue(msg.contains("Wire.Shared"))
      }
    ),
    suite("E10: DependencyCycle")(
      test("shows all types in cycle") {
        val msg = ErrorMessages.renderDependencyCycle(
          List("ServiceA", "ServiceB", "ServiceC", "ServiceA"),
          color = false
        )
        assertTrue(
          msg.contains("ServiceA"),
          msg.contains("ServiceB"),
          msg.contains("ServiceC")
        )
      },
      test("renders ASCII cycle diagram") {
        val msg = ErrorMessages.renderDependencyCycle(
          List("A", "B", "C", "A"),
          color = false
        )
        assertTrue(
          msg.contains("┌"),
          msg.contains("┘"),
          msg.contains("──►") || msg.contains("→")
        )
      },
      test("handles two-element cycle") {
        val msg = ErrorMessages.renderDependencyCycle(List("X", "Y", "X"), color = false)
        assertTrue(
          msg.contains("X"),
          msg.contains("Y"),
          msg.contains("cycle") || msg.contains("Cycle")
        )
      },
      test("handles self-reference cycle") {
        val msg = ErrorMessages.renderDependencyCycle(List("Self", "Self"), color = false)
        assertTrue(msg.contains("Self"))
      },
      test("suggests breaking the cycle") {
        val msg = ErrorMessages.renderDependencyCycle(List("A", "B", "A"), color = false)
        assertTrue(
          msg.contains("interface") || msg.contains("trait") ||
            msg.contains("lazy") || msg.contains("Restructur")
        )
      }
    ),
    suite("E11: DuplicateProvider")(
      test("shows type name") {
        val msg = ErrorMessages.renderDuplicateProvider(
          "Database",
          List(ProviderInfo("PostgresDB", None), ProviderInfo("MySQLDB", None)),
          color = false
        )
        assertTrue(msg.contains("Database"))
      },
      test("lists all providers") {
        val msg = ErrorMessages.renderDuplicateProvider(
          "T",
          List(
            ProviderInfo("Wire1", Some("file.scala:10")),
            ProviderInfo("Wire2", Some("file.scala:20")),
            ProviderInfo("Wire3", None)
          ),
          color = false
        )
        assertTrue(
          msg.contains("Wire1"),
          msg.contains("Wire2"),
          msg.contains("Wire3"),
          msg.contains("file.scala:10")
        )
      },
      test("shows numbered list") {
        val msg = ErrorMessages.renderDuplicateProvider(
          "X",
          List(ProviderInfo("A", None), ProviderInfo("B", None)),
          color = false
        )
        assertTrue(
          msg.contains("1.") || msg.contains("1)"),
          msg.contains("2.") || msg.contains("2)")
        )
      }
    ),
    suite("E12: DuplicateParamType")(
      test("shows type and param type") {
        val msg = ErrorMessages.renderDuplicateParamType("MyClass", "String", color = false)
        assertTrue(
          msg.contains("MyClass"),
          msg.contains("String")
        )
      },
      test("explains Context limitation") {
        val msg = ErrorMessages.renderDuplicateParamType("X", "Y", color = false)
        assertTrue(msg.contains("type-indexed") || msg.contains("Context"))
      },
      test("suggests opaque type wrapper") {
        val msg = ErrorMessages.renderDuplicateParamType("X", "Int", color = false)
        assertTrue(msg.contains("opaque") || msg.contains("wrapper") || msg.contains("case class"))
      }
    ),
    suite("E13: InvalidVarargs")(
      test("shows actual type") {
        val msg = ErrorMessages.renderInvalidVarargs("List[String]", color = false)
        assertTrue(msg.contains("List[String]"))
      },
      test("suggests correct usage") {
        val msg = ErrorMessages.renderInvalidVarargs("?", color = false)
        assertTrue(msg.contains("Resource.from[T]"))
      }
    )
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Unscoped Derivation Errors (E14-E17)
  // ─────────────────────────────────────────────────────────────────────────

  val unscopedDerivationErrorsSuite = suite("Unscoped Derivation Errors")(
    suite("E14: TypeParamNotDeducible")(
      test("shows type parameter and types") {
        val msg = ErrorMessages.renderTypeParamNotDeducible(
          "T",
          "GenericChild[T]",
          "Parent[Int]",
          color = false
        )
        assertTrue(
          msg.contains("T"),
          msg.contains("GenericChild"),
          msg.contains("Parent")
        )
      }
    ),
    suite("E15: SealedNoSubclasses")(
      test("shows type name") {
        val msg = ErrorMessages.renderSealedNoSubclasses("EmptySealed", color = false)
        assertTrue(msg.contains("EmptySealed"))
      },
      test("mentions subclasses") {
        val msg = ErrorMessages.renderSealedNoSubclasses("X", color = false)
        assertTrue(msg.contains("subclass"))
      }
    ),
    suite("E16: NoUnscopedInstance")(
      test("shows all relevant types") {
        val msg = ErrorMessages.renderNoUnscopedInstance(
          "MyADT",
          "java.sql.Connection",
          "field",
          color = false
        )
        assertTrue(
          msg.contains("MyADT"),
          msg.contains("java.sql.Connection"),
          msg.contains("field")
        )
      },
      test("suggests defining Unscoped instance") {
        val msg = ErrorMessages.renderNoUnscopedInstance("X", "Y", "ctx", color = false)
        assertTrue(
          msg.contains("Unscoped[Y]") || msg.contains("given Unscoped")
        )
      }
    ),
    suite("E17: NoPrimaryCtorForUnscoped")(
      test("shows type name") {
        val msg = ErrorMessages.renderNoPrimaryCtorForUnscoped("BadClass", color = false)
        assertTrue(msg.contains("BadClass"))
      },
      test("suggests explicit Unscoped instance") {
        val msg = ErrorMessages.renderNoPrimaryCtorForUnscoped("X", color = false)
        assertTrue(msg.contains("Unscoped"))
      }
    )
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Dependency Tree Visualization
  // ─────────────────────────────────────────────────────────────────────────

  val dependencyTreeSuite = suite("Dependency Tree Visualization")(
    suite("renderMissingDependency")(
      test("shows missing dependency name") {
        val msg = ErrorMessages.renderMissingDependency(
          requiredBy = "MyService",
          missing = List("Database"),
          found = List("Config"),
          stack = Nil,
          dependencyTree = None,
          color = false
        )
        assertTrue(
          msg.contains("Database"),
          msg.contains("missing") || msg.contains("Missing")
        )
      },
      test("shows found dependencies with checkmark indicator") {
        val msg = ErrorMessages.renderMissingDependency(
          requiredBy = "Service",
          missing = List("Missing"),
          found = List("Found1", "Found2"),
          stack = Nil,
          dependencyTree = None,
          color = false
        )
        assertTrue(
          msg.contains("Found1"),
          msg.contains("Found2"),
          msg.contains("✓") || msg.contains("found")
        )
      },
      test("shows missing dependencies with cross indicator") {
        val msg = ErrorMessages.renderMissingDependency(
          requiredBy = "Service",
          missing = List("Missing1", "Missing2"),
          found = Nil,
          stack = Nil,
          dependencyTree = None,
          color = false
        )
        assertTrue(
          msg.contains("Missing1"),
          msg.contains("Missing2"),
          msg.contains("✗") || msg.contains("missing")
        )
      },
      test("shows resolution stack") {
        val msg = ErrorMessages.renderMissingDependency(
          requiredBy = "Leaf",
          missing = List("Config"),
          found = Nil,
          stack = List("App", "Service", "Database"),
          dependencyTree = None,
          color = false
        )
        assertTrue(
          msg.contains("App"),
          msg.contains("Service"),
          msg.contains("Database"),
          msg.contains("Stack") || msg.contains("→")
        )
      },
      test("renders dependency tree with box-drawing characters") {
        val tree = DepNode(
          "App",
          DepStatus.Found,
          List(
            DepNode(
              "Database",
              DepStatus.Found,
              List(DepNode("Config", DepStatus.Found))
            ),
            DepNode(
              "Service",
              DepStatus.Missing,
              List(DepNode("MissingDep", DepStatus.Missing))
            )
          )
        )
        val msg = ErrorMessages.renderMissingDependency(
          requiredBy = "Service",
          missing = List("MissingDep"),
          found = Nil,
          stack = Nil,
          dependencyTree = Some(tree),
          color = false
        )
        assertTrue(
          msg.contains("App"),
          msg.contains("Database"),
          msg.contains("Config"),
          msg.contains("Service"),
          msg.contains("MissingDep"),
          msg.contains("├") || msg.contains("└") || msg.contains("│")
        )
      },
      test("shows actionable hints") {
        val msg = ErrorMessages.renderMissingDependency(
          requiredBy = "Controller",
          missing = List("AuthService"),
          found = Nil,
          stack = Nil,
          dependencyTree = None,
          color = false
        )
        assertTrue(
          msg.contains("Hint") || msg.contains("hint"),
          msg.contains("injected") || msg.contains("shared")
        )
      },
      test("complex tree renders correctly") {
        val tree = DepNode(
          "Application",
          DepStatus.Found,
          List(
            DepNode(
              "HttpServer",
              DepStatus.Found,
              List(
                DepNode("Router", DepStatus.Found),
                DepNode(
                  "Middleware",
                  DepStatus.Found,
                  List(
                    DepNode("AuthMiddleware", DepStatus.Found),
                    DepNode("LoggingMiddleware", DepStatus.Missing)
                  )
                )
              )
            ),
            DepNode(
              "Database",
              DepStatus.Found,
              List(
                DepNode("ConnectionPool", DepStatus.Found),
                DepNode("Migrations", DepStatus.Pending)
              )
            ),
            DepNode("Cache", DepStatus.Missing)
          )
        )
        val msg = ErrorMessages.renderMissingDependency(
          requiredBy = "Application",
          missing = List("LoggingMiddleware", "Cache"),
          found = List("HttpServer", "Database", "Router", "AuthMiddleware", "ConnectionPool"),
          stack = Nil,
          dependencyTree = Some(tree),
          color = false
        )
        assertTrue(
          msg.contains("Application"),
          msg.contains("HttpServer"),
          msg.contains("LoggingMiddleware"),
          msg.contains("Cache"),
          msg.contains("ConnectionPool"),
          msg.contains("✓") || msg.contains("found"),
          msg.contains("✗") || msg.contains("missing")
        )
      }
    )
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Color Handling
  // ─────────────────────────────────────────────────────────────────────────

  val colorHandlingSuite = suite("Color Handling")(
    test("color=false produces no ANSI codes") {
      val msg = ErrorMessages.renderNotAClass("MyType", color = false)
      assertTrue(!msg.contains("\u001b["))
    },
    test("color=true produces ANSI codes") {
      val msg = ErrorMessages.renderNotAClass("MyType", color = true)
      assertTrue(msg.contains("\u001b["))
    },
    test("all error types support color toggle") {
      val noColorMessages = List(
        ErrorMessages.renderLeakWarning("x", "S", color = false),
        ErrorMessages.renderNotAClass("T", color = false),
        ErrorMessages.renderNoPrimaryCtor("T", color = false),
        ErrorMessages.renderSubtypeConflict("T", "A", "B", color = false),
        ErrorMessages.renderHasDependencies("T", List("D"), color = false),
        ErrorMessages.renderUnsupportedImplicitParam("T", "P", color = false),
        ErrorMessages.renderCannotExtractWireTypes("W", color = false),
        ErrorMessages.renderUnmakeableType("T", Nil, color = false),
        ErrorMessages.renderAbstractType("T", Nil, color = false),
        ErrorMessages.renderNoCtorForAutoCreate("T", Nil, color = false),
        ErrorMessages.renderDependencyCycle(List("A", "B", "A"), color = false),
        ErrorMessages.renderDuplicateProvider("T", List(ProviderInfo("P", None)), color = false),
        ErrorMessages.renderDuplicateParamType("T", "P", color = false),
        ErrorMessages.renderInvalidVarargs("V", color = false),
        ErrorMessages.renderTypeParamNotDeducible("P", "C", "T", color = false),
        ErrorMessages.renderSealedNoSubclasses("T", color = false),
        ErrorMessages.renderNoUnscopedInstance("T", "P", "ctx", color = false),
        ErrorMessages.renderNoPrimaryCtorForUnscoped("T", color = false),
        ErrorMessages.renderMissingDependency("R", List("M"), Nil, Nil, None, color = false)
      )
      val colorMessages = List(
        ErrorMessages.renderLeakWarning("x", "S", color = true),
        ErrorMessages.renderNotAClass("T", color = true),
        ErrorMessages.renderNoPrimaryCtor("T", color = true),
        ErrorMessages.renderSubtypeConflict("T", "A", "B", color = true),
        ErrorMessages.renderHasDependencies("T", List("D"), color = true),
        ErrorMessages.renderUnsupportedImplicitParam("T", "P", color = true),
        ErrorMessages.renderCannotExtractWireTypes("W", color = true),
        ErrorMessages.renderUnmakeableType("T", Nil, color = true),
        ErrorMessages.renderAbstractType("T", Nil, color = true),
        ErrorMessages.renderNoCtorForAutoCreate("T", Nil, color = true),
        ErrorMessages.renderDependencyCycle(List("A", "B", "A"), color = true),
        ErrorMessages.renderDuplicateProvider("T", List(ProviderInfo("P", None)), color = true),
        ErrorMessages.renderDuplicateParamType("T", "P", color = true),
        ErrorMessages.renderInvalidVarargs("V", color = true),
        ErrorMessages.renderTypeParamNotDeducible("P", "C", "T", color = true),
        ErrorMessages.renderSealedNoSubclasses("T", color = true),
        ErrorMessages.renderNoUnscopedInstance("T", "P", "ctx", color = true),
        ErrorMessages.renderNoPrimaryCtorForUnscoped("T", color = true),
        ErrorMessages.renderMissingDependency("R", List("M"), Nil, Nil, None, color = true)
      )
      assertTrue(
        noColorMessages.forall(!_.contains("\u001b[")),
        colorMessages.forall(_.contains("\u001b["))
      )
    },
    test("all messages have Scope Error/Warning header") {
      val messages = List(
        ErrorMessages.renderLeakWarning("x", "S", color = false),
        ErrorMessages.renderNotAClass("T", color = false),
        ErrorMessages.renderNoPrimaryCtor("T", color = false),
        ErrorMessages.renderSubtypeConflict("T", "A", "B", color = false),
        ErrorMessages.renderHasDependencies("T", List("D"), color = false),
        ErrorMessages.renderUnsupportedImplicitParam("T", "P", color = false),
        ErrorMessages.renderCannotExtractWireTypes("W", color = false),
        ErrorMessages.renderUnmakeableType("T", Nil, color = false),
        ErrorMessages.renderAbstractType("T", Nil, color = false),
        ErrorMessages.renderNoCtorForAutoCreate("T", Nil, color = false),
        ErrorMessages.renderDependencyCycle(List("A", "B", "A"), color = false),
        ErrorMessages.renderDuplicateProvider("T", List(ProviderInfo("P", None)), color = false),
        ErrorMessages.renderDuplicateParamType("T", "P", color = false),
        ErrorMessages.renderInvalidVarargs("V", color = false),
        ErrorMessages.renderTypeParamNotDeducible("P", "C", "T", color = false),
        ErrorMessages.renderSealedNoSubclasses("T", color = false),
        ErrorMessages.renderNoUnscopedInstance("T", "P", "ctx", color = false),
        ErrorMessages.renderNoPrimaryCtorForUnscoped("T", color = false),
        ErrorMessages.renderMissingDependency("R", List("M"), Nil, Nil, None, color = false)
      )
      assertTrue(messages.forall(m => m.contains("Scope Error") || m.contains("Scope Warning")))
    },
    test("all error messages have footer line") {
      val messages = List(
        ErrorMessages.renderNotAClass("T", color = false),
        ErrorMessages.renderDependencyCycle(List("A", "B", "A"), color = false),
        ErrorMessages.renderMissingDependency("R", List("M"), Nil, Nil, None, color = false)
      )
      assertTrue(messages.forall(m => m.contains("─" * 10)))
    }
  )
}
