package zio.blocks.scope.internal

import scala.reflect.macros.blackbox

/**
 * Shared macro infrastructure for Scope DI macros (Scala 2 version).
 *
 * Provides common type analysis, dependency extraction, and abort helpers.
 * Error rendering is delegated to the shared ErrorMessages object.
 */
private[scope] object MacroCore {

  // ─────────────────────────────────────────────────────────────────────────
  // Type analysis utilities
  // ─────────────────────────────────────────────────────────────────────────

  /** Check if a type is a Scope type (subtype of Scope) */
  def isScopeType(c: blackbox.Context)(tpe: c.Type): Boolean = {
    import c.universe._
    tpe <:< typeOf[zio.blocks.scope.Scope]
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
   *
   * Returns Some with conflict details if two dependencies have a subtype
   * relationship (but aren't equal).
   */
  @SuppressWarnings(Array("org.wartremover.warts.All"))
  def checkSubtypeConflicts(c: blackbox.Context)(
    depTypes: List[c.Type]
  ): Option[(String, String)] = {
    val conflicts = for {
      (t1, i) <- depTypes.zipWithIndex
      (t2, j) <- depTypes.zipWithIndex
      if i < j
      if !(t1 =:= t2)
      if (t1 <:< t2) || (t2 <:< t1)
    } yield {
      val (sub, sup) = if (t1 <:< t2) (t1, t2) else (t2, t1)
      (sub.toString, sup.toString)
    }

    conflicts.headOption
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Abort helpers (use shared ErrorMessages for rendering)
  // ─────────────────────────────────────────────────────────────────────────

  def abortNotAClass(c: blackbox.Context)(typeName: String): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderNotAClass(typeName, color))
  }

  def abortNoPrimaryCtor(c: blackbox.Context)(typeName: String): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderNoPrimaryCtor(typeName, color))
  }

  def abortSubtypeConflict(c: blackbox.Context)(
    typeName: String,
    subtype: String,
    supertype: String
  ): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderSubtypeConflict(typeName, subtype, supertype, color))
  }

  def abortHasDependencies(c: blackbox.Context)(
    typeName: String,
    dependencies: List[String]
  ): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderHasDependencies(typeName, dependencies, color))
  }

  def abortUnmakeableType(c: blackbox.Context)(
    typeName: String,
    requiredByChain: List[String]
  ): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderUnmakeableType(typeName, requiredByChain, color))
  }

  def abortAbstractType(c: blackbox.Context)(
    typeName: String,
    requiredByChain: List[String]
  ): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderAbstractType(typeName, requiredByChain, color))
  }

  def abortNoCtorForAutoCreate(c: blackbox.Context)(
    typeName: String,
    requiredByChain: List[String]
  ): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderNoCtorForAutoCreate(typeName, requiredByChain, color))
  }

  def abortDependencyCycle(c: blackbox.Context)(cyclePath: List[String]): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderDependencyCycle(cyclePath, color))
  }

  def abortDuplicateProvider(c: blackbox.Context)(
    typeName: String,
    providers: List[ErrorMessages.ProviderInfo]
  ): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderDuplicateProvider(typeName, providers, color))
  }

  def abortDuplicateParamType(c: blackbox.Context)(
    typeName: String,
    paramType: String
  ): Nothing = {
    val color = ErrorMessages.Colors.shouldUseColor
    c.abort(c.enclosingPosition, ErrorMessages.renderDuplicateParamType(typeName, paramType, color))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Warning helpers
  // ─────────────────────────────────────────────────────────────────────────

  def warnLeak(c: blackbox.Context)(
    pos: c.Position,
    sourceCode: String,
    scopeName: String
  ): Unit = {
    val color   = ErrorMessages.Colors.shouldUseColor
    val warning = ErrorMessages.renderLeakWarning(sourceCode, scopeName, color)
    c.warning(pos, warning)
  }
}
