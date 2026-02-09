package zio.blocks.scope.internal

/**
 * Shared error and warning message rendering for Scope macros.
 *
 * This object provides beautiful, consistent, cross-platform error messages
 * with ASCII diagrams, color support, and actionable hints.
 *
 * All macro error messages in both Scala 2 and Scala 3 should use this renderer
 * to ensure consistent user experience.
 */
object ErrorMessages {

  // ─────────────────────────────────────────────────────────────────────────
  // Terminal Colors
  // ─────────────────────────────────────────────────────────────────────────

  object Colors {
    def shouldUseColor: Boolean = {
      val noColor     = sys.env.get("NO_COLOR").exists(_.nonEmpty)
      val sbtNoFormat = sys.props.get("sbt.log.noformat").contains("true")
      !noColor && !sbtNoFormat
    }

    val Reset  = "\u001b[0m"
    val Bold   = "\u001b[1m"
    val Red    = "\u001b[31m"
    val Green  = "\u001b[32m"
    val Yellow = "\u001b[33m"
    val Blue   = "\u001b[34m"
    val Cyan   = "\u001b[36m"
    val Gray   = "\u001b[90m"

    def red(s: String, color: Boolean): String    = if (color) s"$Red$s$Reset" else s
    def green(s: String, color: Boolean): String  = if (color) s"$Green$s$Reset" else s
    def yellow(s: String, color: Boolean): String = if (color) s"$Yellow$s$Reset" else s
    def blue(s: String, color: Boolean): String   = if (color) s"$Blue$s$Reset" else s
    def cyan(s: String, color: Boolean): String   = if (color) s"$Cyan$s$Reset" else s
    def gray(s: String, color: Boolean): String   = if (color) s"$Gray$s$Reset" else s
    def bold(s: String, color: Boolean): String   = if (color) s"$Bold$s$Reset" else s
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Dependency Graph Model (for visualization)
  // ─────────────────────────────────────────────────────────────────────────

  /** Status of a dependency in the graph. */
  sealed trait DepStatus
  object DepStatus {
    case object Found   extends DepStatus
    case object Missing extends DepStatus
    case object Pending extends DepStatus
  }

  /** A node in the dependency tree for visualization. */
  final case class DepNode(
    name: String,
    status: DepStatus,
    children: List[DepNode] = Nil
  )

  /** Information about a wire provider for duplicate detection. */
  final case class ProviderInfo(label: String, location: Option[String])

  // ─────────────────────────────────────────────────────────────────────────
  // Common Formatting Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private val lineWidth = 80

  private def header(title: String, color: Boolean): String = {
    import Colors._
    val sep = "─" * (lineWidth - title.length - 4)
    s"${gray("──", color)} ${bold(title, color)} ${gray(sep, color)}"
  }

  private def footer(color: Boolean): String = {
    import Colors._
    gray("─" * lineWidth, color)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Warning Messages
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * W1: Leak warning - value being leaked from scope.
   */
  def renderLeakWarning(sourceCode: String, scopeName: String, color: Boolean): String = {
    import Colors._

    val caretLine   = " " * ("leak(".length) + "^"
    val pointerLine = " " * ("leak(".length) + "|"

    s"""${header("Scope Warning", color)}
       |
       |  leak($sourceCode)
       |  $caretLine
       |  $pointerLine
       |
       |  ${yellow("Warning:", color)} ${cyan(sourceCode, color)} is being leaked from scope ${cyan(scopeName, color)}.
       |  This may result in undefined behavior.
       |
       |  ${yellow("Hint:", color)}
       |     If you know this data type is not resourceful, then add a ${cyan("given ScopeEscape", color)}
       |     for it so you do not need to leak it.
       |
       |${footer(color)}""".stripMargin
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type Structure Errors (E1-E3)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * E1: Not a class - type is trait, abstract, primitive, etc.
   */
  def renderNotAClass(typeName: String, color: Boolean): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Cannot derive Wire for ${cyan(typeName, color)}: not a class.
       |
       |  ${yellow("Hint:", color)} Use ${cyan("Wire.Shared", color)} / ${cyan("Wire.Unique", color)} directly.
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E2: No primary constructor.
   */
  def renderNoPrimaryCtor(typeName: String, color: Boolean): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  ${cyan(typeName, color)} has no primary constructor.
       |
       |  ${yellow("Hint:", color)} Use ${cyan("Wire.Shared", color)} / ${cyan("Wire.Unique", color)} directly
       |        with a custom construction strategy.
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E3: Subtype conflict - two dependencies have subtype relationship.
   */
  def renderSubtypeConflict(
    typeName: String,
    subtype: String,
    supertype: String,
    color: Boolean
  ): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Dependency type conflict in ${cyan(typeName, color)}
       |
       |  ${red(subtype, color)} is a subtype of ${red(supertype, color)}.
       |
       |  When both types are dependencies, Context cannot reliably distinguish
       |  them. The more specific type may be retrieved when the more general
       |  type is requested.
       |
       |  ${yellow("To fix this, wrap one or both types in a distinct wrapper:", color)}
       |
       |    ${cyan(s"case class Wrapped$supertype(value: $supertype)", color)}
       |    ${gray("or", color)}
       |    ${cyan(s"opaque type Wrapped$supertype = $supertype", color)}
       |
       |${footer(color)}""".stripMargin
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Resource.from[T] Errors (E4-E5)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * E4: Has dependencies - Resource.from[T] used but T has dependencies.
   */
  def renderHasDependencies(
    typeName: String,
    dependencies: List[String],
    color: Boolean
  ): String = {
    import Colors._

    val depList = dependencies.map(d => s"    ${gray("•", color)} ${cyan(d, color)}").mkString("\n")

    s"""${header("Scope Error", color)}
       |
       |  ${cyan(s"Resource.from[$typeName]", color)} cannot be derived.
       |
       |  ${cyan(typeName, color)} has dependencies that must be provided:
       |$depList
       |
       |  ${yellow("Hint:", color)} Use ${cyan(s"Resource.from[$typeName](wire1, wire2, ...)", color)}
       |        to provide wires for all dependencies.
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E5: Unsupported implicit parameter type.
   */
  def renderUnsupportedImplicitParam(
    typeName: String,
    paramType: String,
    color: Boolean
  ): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Unsupported implicit parameter in ${cyan(typeName, color)}
       |
       |  Parameter type ${red(paramType, color)} cannot be injected automatically.
       |
       |  ${yellow("Hint:", color)} Only ${cyan("Finalizer", color)} is supported as an implicit parameter.
       |        Use explicit parameters for other dependencies.
       |
       |${footer(color)}""".stripMargin
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Wire Resolution Errors (E6-E13)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * E6: Cannot extract wire types.
   */
  def renderCannotExtractWireTypes(wireType: String, color: Boolean): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Cannot extract types from wire expression.
       |
       |  Wire type: ${cyan(wireType, color)}
       |
       |  ${yellow("Hint:", color)} Ensure the wire has type ${cyan("Wire[In, Out]", color)}.
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E7: Unmakeable type - primitives, collections, functions cannot be
   * auto-created.
   */
  def renderUnmakeableType(
    typeName: String,
    requiredByChain: List[String],
    color: Boolean
  ): String = {
    import Colors._

    val chainDiagram = renderRequiredByChain(requiredByChain, color)

    s"""${header("Scope Error", color)}
       |
       |  Cannot auto-create ${red(typeName, color)}
       |
       |  This type (primitive, collection, or function) cannot be auto-created.
       |
       |$chainDiagram
       |
       |  ${yellow("Fix:", color)} Provide ${cyan("Wire(value)", color)} with the desired value:
       |
       |    ${cyan(s"Resource.from[...](", color)}
       |      ${cyan(s"Wire(...),  // provide a value for $typeName", color)}
       |      ${cyan("...", color)}
       |    ${cyan(")", color)}
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E8: Abstract type cannot be auto-created.
   */
  def renderAbstractType(
    typeName: String,
    requiredByChain: List[String],
    color: Boolean
  ): String = {
    import Colors._

    val chainDiagram = renderRequiredByChain(requiredByChain, color)

    s"""${header("Scope Error", color)}
       |
       |  Cannot auto-create ${red(typeName, color)}
       |
       |  This type is abstract (trait or abstract class).
       |
       |$chainDiagram
       |
       |  ${yellow("Fix:", color)} Provide a wire for a concrete implementation:
       |
       |    ${cyan(s"Resource.from[...](", color)}
       |      ${cyan(s"Wire.shared[ConcreteImpl],  // provides $typeName", color)}
       |      ${cyan("...", color)}
       |    ${cyan(")", color)}
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E9: No constructor for auto-create.
   */
  def renderNoCtorForAutoCreate(
    typeName: String,
    requiredByChain: List[String],
    color: Boolean
  ): String = {
    import Colors._

    val chainDiagram = renderRequiredByChain(requiredByChain, color)

    s"""${header("Scope Error", color)}
       |
       |  Cannot auto-create ${red(typeName, color)}
       |
       |  This type has no primary constructor.
       |
       |$chainDiagram
       |
       |  ${yellow("Fix:", color)} Provide an explicit wire:
       |
       |    ${cyan(s"Resource.from[...](", color)}
       |      ${cyan(s"Wire.Shared[..., $typeName] { (f, ctx) => ... },", color)}
       |      ${cyan("...", color)}
       |    ${cyan(")", color)}
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E10: Dependency cycle detected.
   *
   * Renders an ASCII cycle diagram showing the circular dependency.
   */
  def renderDependencyCycle(cyclePath: List[String], color: Boolean): String = {
    import Colors._

    if (cyclePath.isEmpty) {
      return s"""${header("Scope Error", color)}
                |
                |  Dependency cycle detected (empty path)
                |
                |${footer(color)}""".stripMargin
    }

    val cycleViz = renderCycleDiagram(cyclePath, color)

    s"""${header("Scope Error", color)}
       |
       |  Dependency cycle detected
       |
       |$cycleViz
       |
       |  ${yellow("Break the cycle by:", color)}
       |    ${gray("•", color)} Introducing an interface/trait
       |    ${gray("•", color)} Using lazy initialization
       |    ${gray("•", color)} Restructuring dependencies
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E11: Duplicate provider - multiple wires provide the same type.
   */
  def renderDuplicateProvider(
    typeName: String,
    providers: List[ProviderInfo],
    color: Boolean
  ): String = {
    import Colors._

    val providerList = providers.zipWithIndex.map { case (p, i) =>
      val loc = p.location.map(l => s" ${gray(s"at $l", color)}").getOrElse("")
      s"    ${yellow((i + 1).toString, color)}. ${cyan(p.label, color)}$loc"
    }.mkString("\n")

    s"""${header("Scope Error", color)}
       |
       |  Multiple providers for ${cyan(typeName, color)}
       |
       |  ${bold("Conflicting wires:", color)}
       |$providerList
       |
       |  ${yellow("Hint:", color)} Remove duplicate wires or use distinct wrapper types.
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E12: Duplicate parameter type - constructor has multiple params of same
   * type.
   */
  def renderDuplicateParamType(
    typeName: String,
    paramType: String,
    color: Boolean
  ): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Constructor of ${cyan(typeName, color)} has multiple parameters of type ${red(paramType, color)}
       |
       |  Context is type-indexed and cannot supply distinct values for the same type.
       |
       |  ${yellow("Fix:", color)} Wrap one parameter in an opaque type to distinguish them:
       |
       |    ${cyan(s"opaque type First$paramType = $paramType", color)}
       |    ${gray("or", color)}
       |    ${cyan(s"case class First$paramType(value: $paramType)", color)}
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E13: Invalid varargs - expected varargs of Wire expressions.
   */
  def renderInvalidVarargs(actualType: String, color: Boolean): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Expected varargs of ${cyan("Wire[?, ?]", color)} expressions.
       |
       |  Got: ${red(actualType, color)}
       |
       |  ${yellow("Hint:", color)} Use ${cyan("Resource.from[T](wire1, wire2, ...)", color)}
       |
       |${footer(color)}""".stripMargin
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Unscoped Derivation Errors (E14-E17)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * E14: Type parameter not deducible from parent type arguments.
   */
  def renderTypeParamNotDeducible(
    typeParam: String,
    childType: String,
    parentType: String,
    color: Boolean
  ): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Cannot derive ${cyan("Unscoped", color)} for ${cyan(parentType, color)}
       |
       |  Type parameter ${red(s"'$typeParam'", color)} of ${cyan(s"'$childType'", color)}
       |  cannot be deduced from type arguments of ${cyan(s"'$parentType'", color)}.
       |
       |  ${yellow("Hint:", color)} Provide explicit type arguments or simplify the type hierarchy.
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E15: Sealed type has no known subclasses.
   */
  def renderSealedNoSubclasses(typeName: String, color: Boolean): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Cannot derive ${cyan("Unscoped", color)} for sealed type ${cyan(s"'$typeName'", color)}
       |
       |  No known subclasses found.
       |
       |  ${yellow("Hint:", color)} Ensure the sealed type has at least one concrete subclass
       |        defined in the same file.
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E16: No Unscoped instance found for type.
   */
  def renderNoUnscopedInstance(
    topLevelType: String,
    problemType: String,
    context: String,
    color: Boolean
  ): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Cannot derive ${cyan("Unscoped", color)} for ${cyan(s"'$topLevelType'", color)}
       |
       |  No ${cyan("Unscoped", color)} instance found for $context type ${red(s"'$problemType'", color)}.
       |
       |  Only case classes, sealed traits, case objects, and types with existing
       |  ${cyan("Unscoped", color)} instances are supported.
       |
       |  ${yellow("Hint:", color)} Define an ${cyan(s"Unscoped[$problemType]", color)} instance:
       |
       |    ${cyan(s"given Unscoped[$problemType] = new Unscoped[$problemType] {}", color)}
       |
       |${footer(color)}""".stripMargin
  }

  /**
   * E17: No primary constructor found during Unscoped derivation.
   */
  def renderNoPrimaryCtorForUnscoped(typeName: String, color: Boolean): String = {
    import Colors._

    s"""${header("Scope Error", color)}
       |
       |  Cannot derive ${cyan("Unscoped", color)} for ${cyan(s"'$typeName'", color)}
       |
       |  No primary constructor found.
       |
       |  ${yellow("Hint:", color)} Define an explicit ${cyan("Unscoped", color)} instance:
       |
       |    ${cyan(s"given Unscoped[$typeName] = new Unscoped[$typeName] {}", color)}
       |
       |${footer(color)}""".stripMargin
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Missing Dependency Error (with full dependency tree visualization)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Render a missing dependency error with full dependency tree visualization.
   *
   * This is the most complex error, showing:
   *   - Which dependency is missing
   *   - The resolution stack (how we got here)
   *   - A tree view of all dependencies with their status
   *   - Actionable hints
   */
  def renderMissingDependency(
    requiredBy: String,
    missing: List[String],
    found: List[String],
    stack: List[String],
    dependencyTree: Option[DepNode],
    color: Boolean
  ): String = {
    import Colors._

    val missingList = missing.headOption.getOrElse("Unknown")

    val stackViz = if (stack.nonEmpty) {
      val items = stack.map(s => s"    ${cyan("→", color)} $s")
      s"""
         |  ${bold("Resolution Stack:", color)}
         |${items.mkString("\n")}
         |    ${cyan("→", color)} ${gray("(root)", color)}""".stripMargin
    } else ""

    val foundLines   = found.map(f => s"    ${green("✓", color)} $f  ${gray("— found", color)}")
    val missingLines = missing.map(m => s"    ${red("✗", color)} $m  ${gray("— missing", color)}")
    val deps         = (foundLines ++ missingLines).mkString("\n")

    val treeViz = dependencyTree match {
      case Some(root) =>
        val lines = new StringBuilder
        lines.append(s"  ${bold("Dependency Tree:", color)}\n")
        renderDepTree(root, "    ", isLast = true, isRoot = true, color, lines)
        lines.toString.stripSuffix("\n")
      case None => ""
    }

    val hint = missing.headOption.map { m =>
      s"""  ${yellow("Hint:", color)} Either:
         |    ${gray("•", color)} ${cyan(s".injected[$m].injected[$requiredBy]", color)}     ${gray(
          s"— $m visible in stack",
          color
        )}
         |    ${gray("•", color)} ${cyan(s".injected[$requiredBy](shared[$m])", color)}      ${gray(
          s"— $m as private dependency",
          color
        )}""".stripMargin
    }.getOrElse("")

    val requiresSection = s"  ${bold(s"$requiredBy requires:", color)}\n$deps"

    val sb = new StringBuilder
    sb.append(header("Scope Error", color))
    sb.append("\n\n")
    sb.append(s"  Missing dependency: ${red(missingList, color)}")
    if (stackViz.nonEmpty) {
      sb.append("\n")
      sb.append(stackViz)
    }
    if (treeViz.nonEmpty) {
      sb.append("\n\n")
      sb.append(treeViz)
    }
    sb.append("\n\n")
    sb.append(requiresSection)
    if (hint.nonEmpty) {
      sb.append("\n\n")
      sb.append(hint)
    }
    sb.append("\n\n")
    sb.append(footer(color))
    sb.toString
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ASCII Diagram Helpers
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Render a "required by" chain showing how we got to a missing dependency.
   */
  private def renderRequiredByChain(chain: List[String], color: Boolean): String = {
    import Colors._

    if (chain.isEmpty) return ""

    val items = chain.reverse.zipWithIndex.map { case (item, idx) =>
      val prefix = if (idx == 0) "  " else "    "
      val arrow  = if (idx == chain.length - 1) "└── " else "├── "
      s"$prefix$arrow${cyan(item, color)}"
    }

    s"""  ${bold("Required by:", color)}
       |${items.mkString("\n")}""".stripMargin
  }

  /**
   * Render a cycle diagram for dependency cycles.
   *
   * Example output: ┌─────────┐ │ ▼ A ──► B ──► C ▲ │ └─────────┘
   */
  private def renderCycleDiagram(cyclePath: List[String], color: Boolean): String = {
    import Colors._

    if (cyclePath.size < 2) {
      val single = cyclePath.headOption.getOrElse("?")
      return s"    ${cyan(single, color)} ──► ${cyan(single, color)} ${gray("(self-reference)", color)}"
    }

    // For a cycle A → B → C → A, we draw:
    //   ┌─────────────┐
    //   │             ▼
    //   A ──► B ──► C
    //   ▲             │
    //   └─────────────┘

    val first       = cyclePath.head
    val rest        = cyclePath.tail.dropRight(1)                              // drop the repeated first element
    val allInCycle  = first :: rest
    val arrowChain  = allInCycle.map(s => cyan(s, color)).mkString(" ──► ")
    val chainLength = allInCycle.map(_.length).sum + (allInCycle.size - 1) * 5 // " ──► " = 5 chars

    val topLine    = s"    ┌${"─" * (chainLength - 2)}┐"
    val topArrow   = s"    │${" " * (chainLength - 2)}▼"
    val bottomUp   = s"    ▲${" " * (chainLength - 2)}│"
    val bottomLine = s"    └${"─" * (chainLength - 2)}┘"

    s"""  ${bold("Cycle:", color)}
       |$topLine
       |$topArrow
       |    $arrowChain
       |$bottomUp
       |$bottomLine""".stripMargin
  }

  /**
   * Render a dependency tree with box-drawing characters.
   *
   * Example output: App ├── Database ✓ │ └── Config ✓ └── Service ✗ └── Missing
   * ✗
   */
  private def renderDepTree(
    node: DepNode,
    prefix: String,
    isLast: Boolean,
    isRoot: Boolean,
    color: Boolean,
    sb: StringBuilder
  ): Unit = {
    import Colors._

    val connector = if (isRoot) "" else if (isLast) "└── " else "├── "

    val statusIndicator = node.status match {
      case DepStatus.Found   => s" ${green("✓", color)}"
      case DepStatus.Missing => s" ${red("✗", color)}"
      case DepStatus.Pending => s" ${gray("?", color)}"
    }

    val nodeName = node.status match {
      case DepStatus.Found   => green(node.name, color)
      case DepStatus.Missing => red(node.name, color)
      case DepStatus.Pending => gray(node.name, color)
    }

    sb.append(s"$prefix$connector$nodeName$statusIndicator\n")

    val childPrefix = if (isRoot) prefix else prefix + (if (isLast) "    " else "│   ")

    val children = node.children
    children.zipWithIndex.foreach { case (child, idx) =>
      val childIsLast = idx == children.length - 1
      renderDepTree(child, childPrefix, childIsLast, isRoot = false, color, sb)
    }
  }
}
