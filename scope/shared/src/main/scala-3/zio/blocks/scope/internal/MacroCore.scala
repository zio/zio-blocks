package zio.blocks.scope.internal

import scala.quoted.*

/**
 * Shared macro infrastructure for Scope DI macros (Scala 3 version).
 *
 * Provides common type analysis, dependency extraction, and abort helpers.
 * Error rendering is delegated to the shared ErrorMessages object.
 */
private[scope] object MacroCore {

  // ─────────────────────────────────────────────────────────────────────────
  // Type analysis utilities (Scala 3 specific)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Check if a type is a Scope type (subtype of Scope).
   */
  def isScopeType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    tpe <:< TypeRepr.of[zio.blocks.scope.Scope]
  }

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
   *   - Finalizer/Scope → None (passed through, not a dependency)
   *   - Regular type → Some(type) as dependency
   */
  def classifyParam(using
    Quotes
  )(
    paramType: quotes.reflect.TypeRepr
  ): Option[quotes.reflect.TypeRepr] =
    if (isFinalizerType(paramType)) None
    else Some(paramType)

  /**
   * Check for subtype conflicts in a list of dependency types.
   *
   * If two dependencies have a subtype relationship (but aren't equal), this
   * causes ambiguity in Context and should be reported.
   *
   * Returns Some((subtype, supertype)) if a conflict is found.
   */
  def checkSubtypeConflicts(using
    Quotes
  )(
    depTypes: List[quotes.reflect.TypeRepr]
  ): Option[(String, String)] = {
    val conflicts = for {
      (t1, i) <- depTypes.zipWithIndex
      (t2, j) <- depTypes.zipWithIndex
      if i < j
      if !(t1 =:= t2)
      if (t1 <:< t2) || (t2 <:< t1)
    } yield {
      val (sub, sup) = if (t1 <:< t2) (t1, t2) else (t2, t1)
      (sub.show, sup.show)
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

  // ─────────────────────────────────────────────────────────────────────────
  // Abort helpers (use shared ErrorMessages for rendering)
  // ─────────────────────────────────────────────────────────────────────────

  def abortNotAClass(using Quotes)(typeName: String): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderNotAClass(typeName, color))
  }

  def abortNoPrimaryCtor(using Quotes)(typeName: String): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderNoPrimaryCtor(typeName, color))
  }

  def abortSubtypeConflict(using
    Quotes
  )(
    typeName: String,
    subtype: String,
    supertype: String
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderSubtypeConflict(typeName, subtype, supertype, color))
  }

  def abortHasDependencies(using
    Quotes
  )(
    typeName: String,
    dependencies: List[String]
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderHasDependencies(typeName, dependencies, color))
  }

  def abortCannotExtractWireTypes(using Quotes)(wireType: String): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderCannotExtractWireTypes(wireType, color))
  }

  def abortUnmakeableType(using
    Quotes
  )(
    typeName: String,
    requiredByChain: List[String]
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderUnmakeableType(typeName, requiredByChain, color))
  }

  def abortAbstractType(using
    Quotes
  )(
    typeName: String,
    requiredByChain: List[String]
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderAbstractType(typeName, requiredByChain, color))
  }

  def abortNoCtorForAutoCreate(using
    Quotes
  )(
    typeName: String,
    requiredByChain: List[String]
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderNoCtorForAutoCreate(typeName, requiredByChain, color))
  }

  def abortDependencyCycle(using Quotes)(cyclePath: List[String]): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderDependencyCycle(cyclePath, color))
  }

  def abortDuplicateProvider(using
    Quotes
  )(
    typeName: String,
    providers: List[ErrorMessages.ProviderInfo]
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderDuplicateProvider(typeName, providers, color))
  }

  def abortDuplicateParamType(using
    Quotes
  )(
    typeName: String,
    paramType: String
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderDuplicateParamType(typeName, paramType, color))
  }

  def abortInvalidVarargs(using Quotes)(actualType: String): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderInvalidVarargs(actualType, color))
  }

  def abortUnsupportedImplicitParam(using
    Quotes
  )(
    typeName: String,
    paramType: String
  ): Nothing = {
    import quotes.reflect.*
    val color = ErrorMessages.Colors.shouldUseColor
    report.errorAndAbort(ErrorMessages.renderUnsupportedImplicitParam(typeName, paramType, color))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Warning helpers
  // ─────────────────────────────────────────────────────────────────────────

  def warnLeak(using
    Quotes
  )(
    pos: quotes.reflect.Position,
    sourceCode: String,
    scopeName: String
  ): Unit = {
    import quotes.reflect.*
    val color   = ErrorMessages.Colors.shouldUseColor
    val warning = ErrorMessages.renderLeakWarning(sourceCode, scopeName, color)
    report.warning(warning, pos)
  }
}
