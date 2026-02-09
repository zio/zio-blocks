package zio.blocks.scope.internal

import scala.quoted.*

/**
 * Shared macro infrastructure for Scope DI macros.
 *
 * Provides common type analysis, dependency extraction, error rendering, and
 * code generation utilities used by shared[T], unique[T], and injected[T].
 */
private[scope] object MacroCore {

  // ─────────────────────────────────────────────────────────────────────────
  // Data model for parameter classification
  // ─────────────────────────────────────────────────────────────────────────

  /** Classification of a constructor parameter for Wire derivation. */
  sealed trait ParamKind

  object ParamKind {

    /** A regular dependency - the type is extracted and used in the In type. */
    case class ValueDep(typeName: String) extends ParamKind

    /**
     * A Scope.Has[Y] parameter - Y is the dependency, scope is passed narrowed.
     */
    case class ScopeHas(depTypeName: String) extends ParamKind

    /** A Scope.Any parameter - scope is passed but no dependency added. */
    case object ScopeAny extends ParamKind
  }

  /** Result of analyzing a constructor for Wire derivation. */
  final case class CtorAnalysis(
    paramLists: List[List[ParamInfo]],
    depTypes: List[DepInfo],
    isAutoCloseable: Boolean
  )

  /** Information about a single parameter. */
  final case class ParamInfo(
    name: String,
    kind: ParamKind,
    typeReprKey: String
  )

  /** Information about a dependency type. */
  final case class DepInfo(
    typeName: String,
    typeReprKey: String
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Error model
  // ─────────────────────────────────────────────────────────────────────────

  sealed trait ScopeMacroError {
    def render(color: Boolean): String
  }

  object ScopeMacroError {

    final case class NotAClass(typeName: String) extends ScopeMacroError {
      def render(color: Boolean): String =
        ErrorRenderer.renderNotAClass(typeName, color)
    }

    final case class NoPrimaryCtor(typeName: String) extends ScopeMacroError {
      def render(color: Boolean): String =
        ErrorRenderer.renderNoPrimaryCtor(typeName, color)
    }

    final case class SubtypeConflict(
      typeName: String,
      subtype: String,
      supertype: String
    ) extends ScopeMacroError {
      def render(color: Boolean): String =
        ErrorRenderer.renderSubtypeConflict(typeName, subtype, supertype, color)
    }

    final case class MissingDependency(
      requiredBy: String,
      missing: List[String],
      found: List[String],
      stack: List[String],
      dependencyTree: Option[DepNode] = None
    ) extends ScopeMacroError {
      def render(color: Boolean): String =
        ErrorRenderer.renderMissingDependency(requiredBy, missing, found, stack, dependencyTree, color)
    }

    final case class DuplicateProvider(
      typeName: String,
      providers: List[ProviderInfo]
    ) extends ScopeMacroError {
      def render(color: Boolean): String =
        ErrorRenderer.renderDuplicateProvider(typeName, providers, color)
    }

    final case class DependencyCycle(
      path: List[String]
    ) extends ScopeMacroError {
      def render(color: Boolean): String =
        ErrorRenderer.renderDependencyCycle(path, color)
    }
  }

  final case class ProviderInfo(label: String, location: Option[String])

  /** A node in the dependency tree for visualization. */
  final case class DepNode(
    name: String,
    status: DepStatus,
    children: List[DepNode] = Nil
  )

  sealed trait DepStatus
  object DepStatus {
    case object Found   extends DepStatus
    case object Missing extends DepStatus
    case object Pending extends DepStatus // not yet resolved
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type analysis utilities (Scala 3 specific)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Check if a type is a Finalizer type (subtype of Finalizer).
   *
   * Finalizer is the minimal interface for registering cleanup actions.
   * Constructors can take Finalizer as a parameter to register their own
   * cleanup logic.
   */
  def isFinalizerType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe <:< TypeRepr.of[zio.blocks.scope.Finalizer]
  }

  /**
   * Classify a parameter type and extract its dependency if applicable.
   *
   *   - Finalizer → ScopeAny with no dependency (finalizer is passed through)
   *   - Regular type → ValueDep with the type as dependency
   */
  def classifyParam(using
    Quotes
  )(
    paramType: quotes.reflect.TypeRepr
  ): (ParamKind, Option[quotes.reflect.TypeRepr]) =
    if (isFinalizerType(paramType)) {
      (ParamKind.ScopeAny, None)
    } else {
      (ParamKind.ValueDep(paramType.show), Some(paramType))
    }

  /**
   * Check for subtype conflicts in a list of dependency types.
   *
   * If two dependencies have a subtype relationship (but aren't equal), this
   * causes ambiguity in Context and should be reported.
   */
  def checkSubtypeConflicts(using
    Quotes
  )(
    typeName: String,
    depTypes: List[quotes.reflect.TypeRepr]
  ): Option[ScopeMacroError.SubtypeConflict] = {
    val conflicts = for {
      (t1, i) <- depTypes.zipWithIndex
      (t2, j) <- depTypes.zipWithIndex
      if i < j
      if !(t1 =:= t2)
      if (t1 <:< t2) || (t2 <:< t1)
    } yield {
      val (sub, sup) = if (t1 <:< t2) (t1, t2) else (t2, t1)
      ScopeMacroError.SubtypeConflict(typeName, sub.show, sup.show)
    }

    conflicts.headOption
  }

  /**
   * Compute the In type from a list of dependency types.
   *
   * Returns the intersection of all types (A & B & C) or Any if empty.
   */
  def computeInType(using Quotes)(depTypes: List[quotes.reflect.TypeRepr]): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    depTypes.reduceLeftOption(AndType(_, _)).getOrElse(TypeRepr.of[Any])
  }

  /**
   * Flatten an intersection type (A & B & C) into a list of component types.
   *
   * Returns empty list for Any type, or a single-element list for
   * non-intersection types.
   */
  def flattenIntersection(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    def flatten(t: TypeRepr): List[TypeRepr] = t.dealias.simplified match {
      case AndType(left, right)        => flatten(left) ++ flatten(right)
      case t if t =:= TypeRepr.of[Any] => Nil
      case t                           => List(t)
    }

    flatten(tpe)
  }

  /**
   * Analyze a class constructor for Wire derivation.
   *
   * Examines all parameter lists, classifies each parameter, extracts
   * dependency types, and checks for subtype conflicts.
   */
  def analyzeCtor(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Either[ScopeMacroError, CtorAnalysis] = {
    import quotes.reflect.*

    val sym = tpe.typeSymbol
    if (!sym.isClassDef) {
      return Left(ScopeMacroError.NotAClass(tpe.show))
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      return Left(ScopeMacroError.NoPrimaryCtor(tpe.show))
    }

    val paramLists: List[List[Symbol]] = ctor.paramSymss

    // Analyze each parameter
    var allDepTypes    = List.empty[TypeRepr]
    val paramInfoLists = paramLists.map { params =>
      params.map { param =>
        val paramType        = tpe.memberType(param).dealias.simplified
        val (kind, maybeDep) = classifyParam(paramType)
        maybeDep.foreach(dep => allDepTypes = allDepTypes :+ dep)
        ParamInfo(param.name, kind, paramType.show)
      }
    }

    // Check for subtype conflicts
    checkSubtypeConflicts(tpe.show, allDepTypes) match {
      case Some(error) => return Left(error)
      case None        => // ok
    }

    val depInfos        = allDepTypes.map(t => DepInfo(t.show, t.show))
    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]

    Right(CtorAnalysis(paramInfoLists, depInfos, isAutoCloseable))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Color / terminal utilities
  // ─────────────────────────────────────────────────────────────────────────

  object Colors {
    // Check if we should use colors
    def shouldUseColor: Boolean = {
      val noColor     = sys.env.get("NO_COLOR").exists(_.nonEmpty)
      val sbtNoFormat = sys.props.get("sbt.log.noformat").contains("true")
      !noColor && !sbtNoFormat
    }

    // ANSI escape codes
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
  // Error rendering
  // ─────────────────────────────────────────────────────────────────────────

  object ErrorRenderer {
    import Colors.*

    private val lineWidth = 80

    private def header(title: String, color: Boolean): String = {
      val sep = "─" * (lineWidth - title.length - 4)
      s"${gray("──", color)} ${bold(title, color)} ${gray(sep, color)}"
    }

    private def footer(color: Boolean): String =
      gray("─" * lineWidth, color)

    def renderNotAClass(typeName: String, color: Boolean): String =
      s"""${header("Scope Error", color)}
         |
         |  Cannot derive Wire for ${cyan(typeName, color)}: not a class.
         |
         |  ${yellow("Hint:", color)} Use ${cyan("Wire.Shared", color)} / ${cyan("Wire.Unique", color)} directly.
         |
         |${footer(color)}""".stripMargin

    def renderNoPrimaryCtor(typeName: String, color: Boolean): String =
      s"""${header("Scope Error", color)}
         |
         |  ${cyan(typeName, color)} has no primary constructor.
         |
         |  ${yellow("Hint:", color)} Use ${cyan("Wire.Shared", color)} / ${cyan("Wire.Unique", color)} directly
         |        with a custom construction strategy.
         |
         |${footer(color)}""".stripMargin

    def renderSubtypeConflict(
      typeName: String,
      subtype: String,
      supertype: String,
      color: Boolean
    ): String =
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

    def renderMissingDependency(
      requiredBy: String,
      missing: List[String],
      found: List[String],
      stack: List[String],
      dependencyTree: Option[DepNode],
      color: Boolean
    ): String = {
      val missingList = missing.headOption.getOrElse("Unknown")
      val stackViz    = if (stack.nonEmpty) {
        val items = stack.map(s => s"    ${cyan("→", color)} $s")
        s"""
           |  ${bold("Stack:", color)}
           |${items.mkString("\n")}
           |    ${cyan("→", color)} ${gray("(root)", color)}""".stripMargin
      } else ""

      val foundLines   = found.map(f => s"    ${green("✓", color)} $f  ${gray("— found in stack", color)}")
      val missingLines = missing.map(m => s"    ${red("✗", color)} $m  ${gray("— missing", color)}")
      val deps         = (foundLines ++ missingLines).mkString("\n")

      val treeViz = dependencyTree match {
        case Some(root) =>
          val lines = new StringBuilder
          lines.append(s"  ${bold("Dependency Tree:", color)}\n")
          renderDepTree(root, "    ", isLast = true, isRoot = true, missing.toSet, found.toSet, color, lines)
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

      // Build output with proper blank line separation between sections
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

    private def renderDepTree(
      node: DepNode,
      prefix: String,
      isLast: Boolean,
      isRoot: Boolean,
      missing: Set[String],
      found: Set[String],
      color: Boolean,
      sb: StringBuilder
    ): Unit = {
      // Determine the connector for this node
      val connector = if (isRoot) "" else if (isLast) "└── " else "├── "

      // Format the node name with status indicator
      val statusIndicator = node.status match {
        case DepStatus.Found   => s" ${green("✓", color)}"
        case DepStatus.Missing => s" ${red("✗", color)}"
        case DepStatus.Pending => ""
      }

      val nodeName = node.status match {
        case DepStatus.Found   => green(node.name, color)
        case DepStatus.Missing => red(node.name, color)
        case DepStatus.Pending => cyan(node.name, color)
      }

      sb.append(s"$prefix$connector$nodeName$statusIndicator\n")

      // Calculate the prefix for children
      val childPrefix = if (isRoot) prefix else prefix + (if (isLast) "    " else "│   ")

      // Render children
      val children = node.children
      children.zipWithIndex.foreach { case (child, idx) =>
        val childIsLast = idx == children.length - 1
        renderDepTree(child, childPrefix, childIsLast, isRoot = false, missing, found, color, sb)
      }
    }

    def renderDuplicateProvider(
      typeName: String,
      providers: List[ProviderInfo],
      color: Boolean
    ): String = {
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

    def renderDependencyCycle(path: List[String], color: Boolean): String = {
      if (path.isEmpty) {
        return s"""${header("Scope Error", color)}
                  |
                  |  Dependency cycle detected (empty path)
                  |
                  |${footer(color)}""".stripMargin
      }

      // Build ASCII cycle visualization
      val first = path.head
      val rest  = path.tail

      val cycleViz = new StringBuilder
      cycleViz.append(s"    ┌${"─" * (first.length + 4)}┐\n")
      cycleViz.append(s"    │${" " * (first.length + 4)}▼\n")

      var current = first
      for (next <- rest) {
        cycleViz.append(s"    ${cyan(current, color)} ──► ${cyan(next, color)}\n")
        current = next
      }

      cycleViz.append(s"    ▲${" " * (first.length + 3)}│\n")
      cycleViz.append(s"    └${"─" * (first.length + 4)}┘\n")

      s"""${header("Scope Error", color)}
         |
         |  Dependency cycle detected
         |
         |  ${bold("Cycle:", color)}
         |${cycleViz.toString}
         |  ${yellow("Break the cycle by:", color)}
         |    ${gray("•", color)} Introducing an interface/trait
         |    ${gray("•", color)} Using lazy initialization
         |    ${gray("•", color)} Restructuring dependencies
         |
         |${footer(color)}""".stripMargin
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Warning model
  // ─────────────────────────────────────────────────────────────────────────

  sealed trait ScopeMacroWarning {
    def render(color: Boolean): String
  }

  object ScopeMacroWarning {

    final case class LeakWarning(
      sourceCode: String,
      scopeName: String
    ) extends ScopeMacroWarning {
      def render(color: Boolean): String =
        WarningRenderer.renderLeakWarning(sourceCode, scopeName, color)
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Warning rendering
  // ─────────────────────────────────────────────────────────────────────────

  object WarningRenderer {
    import Colors.*

    private val lineWidth = 80

    private def header(title: String, color: Boolean): String = {
      val sep = "─" * (lineWidth - title.length - 4)
      s"${gray("──", color)} ${bold(title, color)} ${gray(sep, color)}"
    }

    private def footer(color: Boolean): String =
      gray("─" * lineWidth, color)

    def renderLeakWarning(sourceCode: String, scopeName: String, color: Boolean): String = {
      // Build the pointer line
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
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Abort helper
  // ─────────────────────────────────────────────────────────────────────────

  def abort(using Quotes)(error: ScopeMacroError): Nothing = {
    import quotes.reflect.*
    val color = Colors.shouldUseColor
    report.errorAndAbort(error.render(color))
  }
}
