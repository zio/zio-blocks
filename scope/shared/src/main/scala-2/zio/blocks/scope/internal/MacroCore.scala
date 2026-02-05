package zio.blocks.scope.internal

import scala.reflect.macros.blackbox

/**
 * Shared macro infrastructure for Scope DI macros (Scala 2 version).
 *
 * Provides common type analysis, dependency extraction, error rendering, and
 * code generation utilities used by shared[T], unique[T], injected[T], and
 * Wireable.from[T].
 */
private[scope] object MacroCore {

  // ─────────────────────────────────────────────────────────────────────────
  // Type analysis utilities
  // ─────────────────────────────────────────────────────────────────────────

  /** Check if a type is a Scope type (Scope[?]) */
  def isScopeType(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._
    tpe <:< typeOf[zio.blocks.scope.Scope[_]]
  }

  /**
   * Extract the dependency type from a Scope.Has[Y] type.
   *
   * Returns Some(Y) if this is Scope[Context[Y] :: scala.Any] with Y being a
   * concrete type (not Any/Nothing), otherwise None.
   */
  def extractScopeHasType(c: blackbox.Context)(tpe: c.Type): Option[c.Type] = {
    import c.universe._
    val dealiased = tpe.dealias
    dealiased match {
      case TypeRef(_, sym, List(stackType)) if sym.fullName == "zio.blocks.scope.Scope" =>
        stackType.dealias match {
          case TypeRef(_, consSym, List(contextType, _)) if consSym.name.toString == "::" =>
            contextType.dealias match {
              case TypeRef(_, ctxSym, List(innerType)) if ctxSym.fullName == "zio.blocks.context.Context" =>
                val inner = innerType.dealias
                if (inner =:= typeOf[Any] || inner =:= typeOf[Nothing]) None
                else Some(inner)
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  /**
   * Classify a parameter type and extract its dependency if applicable.
   *
   *   - Scope.Has[Y] → Some(Y) as dependency
   *   - Scope.Any-like → None (no dependency)
   *   - Regular type → Some(type) as dependency
   */
  def classifyAndExtractDep(c: blackbox.Context)(paramType: c.Type): Option[c.Type] =
    if (isScopeType(c)(paramType)) {
      extractScopeHasType(c)(paramType)
    } else {
      Some(paramType)
    }

  /**
   * Check for subtype conflicts in a list of dependency types.
   */
  @SuppressWarnings(Array("org.wartremover.warts.All"))
  def checkSubtypeConflicts(c: blackbox.Context)(
    @annotation.unused typeName: String,
    depTypes: List[c.Type]
  ): Option[(c.Type, c.Type)] = {
    val conflicts = for {
      (t1, i) <- depTypes.zipWithIndex
      (t2, j) <- depTypes.zipWithIndex
      if i < j
      if !(t1 =:= t2)
      if (t1 <:< t2) || (t2 <:< t1)
    } yield {
      if (t1 <:< t2) (t1, t2) else (t2, t1)
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
         |  ${yellow("Hint:", color)} Provide a ${cyan(s"Wireable[$typeName]", color)} instance
         |        or use ${cyan("Wire.Shared", color)} / ${cyan("Wire.Unique", color)} directly.
         |
         |${footer(color)}""".stripMargin

    def renderNoPrimaryCtor(typeName: String, color: Boolean): String =
      s"""${header("Scope Error", color)}
         |
         |  ${cyan(typeName, color)} has no primary constructor.
         |
         |  ${yellow("Hint:", color)} Provide a ${cyan(s"Wireable[$typeName]", color)} instance
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
         |
         |${footer(color)}""".stripMargin

    def renderTooManyParams(
      macroName: String,
      typeName: String,
      count: Int,
      maxSupported: Int,
      color: Boolean
    ): String =
      s"""${header("Scope Error", color)}
         |
         |  ${cyan(s"$macroName[$typeName]", color)} has too many constructor parameters.
         |
         |  Found: ${red(count.toString, color)} parameters
         |  Supported: up to ${green(maxSupported.toString, color)} parameters
         |
         |  ${yellow("Hint:", color)} Use ${cyan(s"Wireable.from[$typeName]", color)} for more control,
         |        or restructure to reduce direct dependencies.
         |
         |${footer(color)}""".stripMargin
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Abort helpers
  // ─────────────────────────────────────────────────────────────────────────

  def abortNotAClass(c: blackbox.Context)(typeName: String): Nothing = {
    val color = Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorRenderer.renderNotAClass(typeName, color))
  }

  def abortNoPrimaryCtor(c: blackbox.Context)(typeName: String): Nothing = {
    val color = Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorRenderer.renderNoPrimaryCtor(typeName, color))
  }

  def abortSubtypeConflict(c: blackbox.Context)(
    typeName: String,
    subtype: String,
    supertype: String
  ): Nothing = {
    val color = Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorRenderer.renderSubtypeConflict(typeName, subtype, supertype, color))
  }

  def abortTooManyParams(c: blackbox.Context)(
    macroName: String,
    typeName: String,
    count: Int,
    maxSupported: Int
  ): Nothing = {
    val color = Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorRenderer.renderTooManyParams(macroName, typeName, count, maxSupported, color))
  }
}
