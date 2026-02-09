package zio.blocks.scope.internal

import scala.reflect.macros.blackbox

/**
 * Shared macro infrastructure for Scope DI macros (Scala 2 version).
 *
 * Provides common type analysis, dependency extraction, error rendering, and
 * code generation utilities used by shared[T], unique[T], and injected[T].
 */
private[scope] object MacroCore {

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
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type analysis utilities
  // ─────────────────────────────────────────────────────────────────────────

  /** Check if a type is a Scope type (subtype of Scope) */
  def isScopeType(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._
    tpe <:< typeOf[zio.blocks.scope.Scope[_, _]]
  }

  /** Check if a type is a Finalizer type (subtype of Finalizer) */
  def isFinalizerType(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._
    tpe <:< typeOf[zio.blocks.scope.Finalizer]
  }

  /**
   * Extract the dependency type from a Scope.Has[Y] type.
   *
   * In the new design, Scope is an HList:
   *   - Scope.::[H, T] is a cons cell with head type H and tail T <: Scope
   *   - Scope.Global is the empty scope
   *   - Scope.Has[T] = Scope.::[T, Scope]
   *
   * Returns Some(H) if this is Scope.::[H, _] with H being a concrete type (not
   * Any/Nothing), otherwise None.
   */
  def extractScopeHasType(c: blackbox.Context)(tpe: c.Type): Option[c.Type] = {
    import c.universe._
    val dealiased = tpe.dealias
    dealiased match {
      case TypeRef(_, sym, List(headType, _)) if sym.name.toString == "$colon$colon" =>
        // Check that this is Scope.:: not List.::
        val owner = sym.owner
        if (owner.fullName == "zio.blocks.scope.Scope") {
          val head = headType.dealias
          if (head =:= typeOf[Any] || head =:= typeOf[Nothing]) None
          else Some(head)
        } else None
      case _ => None
    }
  }

  /**
   * Classify a parameter type and extract its dependency if applicable.
   *
   *   - Finalizer → None (no dependency, passed as finalizer)
   *   - Scope.Has[Y] → Some(Y) as dependency
   *   - Scope.Any-like → None (no dependency)
   *   - Regular type → Some(type) as dependency
   */
  def classifyAndExtractDep(c: blackbox.Context)(paramType: c.Type): Option[c.Type] =
    if (isFinalizerType(c)(paramType)) {
      if (isScopeType(c)(paramType)) extractScopeHasType(c)(paramType)
      else None
    } else {
      Some(paramType)
    }

  /**
   * Check for subtype conflicts in a list of dependency types.
   */
  @SuppressWarnings(Array("org.wartremover.warts.All"))
  def checkSubtypeConflicts(c: blackbox.Context)(
    typeName: String,
    depTypes: List[c.Type]
  ): Option[ScopeMacroError.SubtypeConflict] = {
    val conflicts = for {
      (t1, i) <- depTypes.zipWithIndex
      (t2, j) <- depTypes.zipWithIndex
      if i < j
      if !(t1 =:= t2)
      if (t1 <:< t2) || (t2 <:< t1)
    } yield {
      val (sub, sup) = if (t1 <:< t2) (t1, t2) else (t2, t1)
      ScopeMacroError.SubtypeConflict(typeName, sub.toString, sup.toString)
    }

    conflicts.headOption
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Color / terminal utilities
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
  // Error rendering
  // ─────────────────────────────────────────────────────────────────────────

  object ErrorRenderer {
    import Colors._

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

  }

  // ─────────────────────────────────────────────────────────────────────────
  // Abort helpers
  // ─────────────────────────────────────────────────────────────────────────

  def abort(c: blackbox.Context)(error: ScopeMacroError): Nothing = {
    val color = Colors.shouldUseColor
    c.abort(c.enclosingPosition, error.render(color))
  }

  def abortNotAClass(c: blackbox.Context)(typeName: String): Nothing =
    abort(c)(ScopeMacroError.NotAClass(typeName))

  def abortNoPrimaryCtor(c: blackbox.Context)(typeName: String): Nothing =
    abort(c)(ScopeMacroError.NoPrimaryCtor(typeName))

  def abortSubtypeConflict(c: blackbox.Context)(
    typeName: String,
    subtype: String,
    supertype: String
  ): Nothing =
    abort(c)(ScopeMacroError.SubtypeConflict(typeName, subtype, supertype))

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
    import Colors._

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
}
