package zio.blocks.scope.internal

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.{::, InStack, Scope, Wire, Wireable}
import scala.quoted.*
import scala.compiletime.summonInline

/**
 * Unified code generation for Wire derivation (shared/unique/wireable).
 *
 * This consolidates the code generation that was previously duplicated across
 * deriveSharedWire, deriveUniqueWire, and WireableMacros.fromImpl.
 */
private[scope] object WireCodeGen {

  /** Wire kind: Shared or Unique */
  enum WireKind {
    case Shared, Unique
  }

  /**
   * Derive a Wire[In, T] from T's constructor.
   *
   * Returns the In type as a TypeRepr alongside the wire expression.
   *
   * All type analysis and code generation happens within the same Quotes
   * context.
   */
  def deriveWire[T: Type](using
    Quotes
  )(
    kind: WireKind
  ): (quotes.reflect.TypeRepr, Expr[Wire[?, T]]) = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    // Validate
    if (!sym.isClassDef) {
      MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abort(MacroCore.ScopeMacroError.NoPrimaryCtor(tpe.show))
    }

    val paramLists: List[List[Symbol]] = ctor.paramSymss

    // Collect dependency types
    val depTypes: List[TypeRepr] = paramLists.flatten.flatMap { param =>
      val paramType     = tpe.memberType(param).dealias.simplified
      val (_, maybeDep) = MacroCore.classifyParam(paramType)
      maybeDep
    }

    // Check for subtype conflicts
    MacroCore.checkSubtypeConflicts(tpe.show, depTypes) match {
      case Some(error) => MacroCore.abort(error)
      case None        => // ok
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val inType          = MacroCore.computeInType(depTypes)

    // Helper to generate argument term - defined inside to share Quotes context
    def generateArgTerm[In: Type](paramType: TypeRepr, scopeExpr: Expr[Scope.Has[In]]): Term =
      if (MacroCore.isScopeType(paramType)) {
        MacroCore.extractScopeHasType(paramType) match {
          case Some(depType) =>
            depType.asType match {
              case '[d] =>
                '{ $scopeExpr.asInstanceOf[Scope.Has[d]] }.asTerm
            }
          case None =>
            '{ $scopeExpr.asInstanceOf[Scope.Any] }.asTerm
        }
      } else {
        paramType.asType match {
          case '[d] =>
            '{
              $scopeExpr.get[d](using
                summonInline[InStack[d, Context[In] :: scala.Any]],
                summonInline[IsNominalType[d]]
              )
            }.asTerm
        }
      }

    // Helper to generate wire body - defined inside to share Quotes context
    def generateWireBody[In: Type](scopeExpr: Expr[Scope.Has[In]]): Expr[Context[T]] = {
      val ctorSym = tpe.typeSymbol.primaryConstructor

      // Generate argument lists preserving param list structure
      val argListTerms: List[List[Term]] = paramLists.map { params =>
        params.map { param =>
          val paramType = tpe.memberType(param).dealias.simplified
          generateArgTerm[In](paramType, scopeExpr)
        }
      }

      // Build constructor call: new T(args1)(args2)...
      val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
      val applied  = argListTerms.foldLeft[Term](ctorTerm) { (fn, args) =>
        Apply(fn, args)
      }

      val instanceExpr = applied.asExprOf[T]

      if (isAutoCloseable) {
        '{
          val instance = $instanceExpr
          $scopeExpr.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      } else {
        '{
          val instance = $instanceExpr
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }

    // Generate the wire expression
    val wireExpr = inType.asType match {
      case '[inTpe] =>
        kind match {
          case WireKind.Shared =>
            '{
              Wire.Shared[inTpe, T] {
                val scope = summon[Scope.Has[inTpe]]
                ${ generateWireBody[inTpe]('{ scope }) }
              }
            }

          case WireKind.Unique =>
            '{
              Wire.Unique[inTpe, T] {
                val scope = summon[Scope.Has[inTpe]]
                ${ generateWireBody[inTpe]('{ scope }) }
              }
            }
        }
    }

    (inType, wireExpr)
  }

  /**
   * Convert a Wire expression to Shared with preserved In type.
   */
  def wireFromWireable[T: Type](using
    Quotes
  )(
    wireableExpr: Expr[Wireable[T]],
    kind: WireKind
  ): Expr[Wire[?, T]] = {
    import quotes.reflect.*

    val wireableTpe = wireableExpr.asTerm.tpe
    val inTypeRepr  = MacroCore.extractWireableInType(wireableTpe)

    inTypeRepr.asType match {
      case '[inType] =>
        kind match {
          case WireKind.Shared =>
            '{ $wireableExpr.wire.shared.asInstanceOf[Wire.Shared[inType, T]] }
          case WireKind.Unique =>
            '{ $wireableExpr.wire.unique.asInstanceOf[Wire.Unique[inType, T]] }
        }
    }
  }

  /**
   * Generate a complete Wireable[T] from T's constructor.
   *
   * All type analysis and code generation happens within the same Quotes
   * context.
   */
  def deriveWireable[T: Type](using Quotes): Expr[Wireable[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (!sym.isClassDef) {
      MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
    }

    val ctor = sym.primaryConstructor
    if (ctor == Symbol.noSymbol) {
      MacroCore.abort(MacroCore.ScopeMacroError.NoPrimaryCtor(tpe.show))
    }

    val paramLists: List[List[Symbol]] = ctor.paramSymss

    // Collect dependency types
    val depTypes: List[TypeRepr] = paramLists.flatten.flatMap { param =>
      val paramType     = tpe.memberType(param).dealias.simplified
      val (_, maybeDep) = MacroCore.classifyParam(paramType)
      maybeDep
    }

    // Check for subtype conflicts
    MacroCore.checkSubtypeConflicts(tpe.show, depTypes) match {
      case Some(error) => MacroCore.abort(error)
      case None        => // ok
    }

    val isAutoCloseable = tpe <:< TypeRepr.of[AutoCloseable]
    val inType          = MacroCore.computeInType(depTypes)

    // Helper to generate argument term - defined inside to share Quotes context
    def generateArgTerm[In: Type](paramType: TypeRepr, scopeExpr: Expr[Scope.Has[In]]): Term =
      if (MacroCore.isScopeType(paramType)) {
        MacroCore.extractScopeHasType(paramType) match {
          case Some(depType) =>
            depType.asType match {
              case '[d] =>
                '{ $scopeExpr.asInstanceOf[Scope.Has[d]] }.asTerm
            }
          case None =>
            '{ $scopeExpr.asInstanceOf[Scope.Any] }.asTerm
        }
      } else {
        paramType.asType match {
          case '[d] =>
            '{
              $scopeExpr.get[d](using
                summonInline[InStack[d, Context[In] :: scala.Any]],
                summonInline[IsNominalType[d]]
              )
            }.asTerm
        }
      }

    // Helper to generate wire body - defined inside to share Quotes context
    def generateWireBody[In: Type](scopeExpr: Expr[Scope.Has[In]]): Expr[Context[T]] = {
      val ctorSym = tpe.typeSymbol.primaryConstructor

      // Generate argument lists preserving param list structure
      val argListTerms: List[List[Term]] = paramLists.map { params =>
        params.map { param =>
          val paramType = tpe.memberType(param).dealias.simplified
          generateArgTerm[In](paramType, scopeExpr)
        }
      }

      // Build constructor call: new T(args1)(args2)...
      val ctorTerm = Select(New(TypeTree.of[T]), ctorSym)
      val applied  = argListTerms.foldLeft[Term](ctorTerm) { (fn, args) =>
        Apply(fn, args)
      }

      val instanceExpr = applied.asExprOf[T]

      if (isAutoCloseable) {
        '{
          val instance = $instanceExpr
          $scopeExpr.defer(instance.asInstanceOf[AutoCloseable].close())
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      } else {
        '{
          val instance = $instanceExpr
          Context[T](instance)(using summonInline[IsNominalType[T]])
        }
      }
    }

    inType.asType match {
      case '[inTpe] =>
        '{
          new Wireable[T] {
            type In = inTpe

            def wire: Wire[inTpe, T] = Wire.Shared[inTpe, T] {
              val scope = summon[Scope.Has[inTpe]]
              ${ generateWireBody[inTpe]('{ scope }) }
            }
          }
        }
    }
  }
}
