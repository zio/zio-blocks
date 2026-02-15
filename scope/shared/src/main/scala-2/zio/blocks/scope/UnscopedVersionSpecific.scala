package zio.blocks.scope

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import zio.blocks.scope.internal.ErrorMessages

private[scope] trait UnscopedVersionSpecific {

  /**
   * Derives [[Unscoped]] for case classes and sealed traits/abstract classes.
   * All fields (for case classes) or subtypes (for sealed hierarchies) must
   * themselves have `Unscoped` instances, verified at compile time via a macro.
   *
   * @tparam A
   *   the case class or sealed trait/abstract class to derive `Unscoped` for
   * @return
   *   an `Unscoped[A]` instance, or a compile error if any constituent type
   *   lacks an `Unscoped` instance
   */
  def derived[A]: Unscoped[A] = macro UnscopedMacros.derivedImpl[A]
}

private[scope] object UnscopedMacros {
  def derivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[Unscoped[A]] = {
    import c.universe._

    val tpe   = weakTypeOf[A].dealias
    val color = ErrorMessages.Colors.shouldUseColor

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def isSealedTraitOrAbstractClass(t: Type): Boolean = t.typeSymbol.isClass && {
      val classSymbol = t.typeSymbol.asClass
      classSymbol.isSealed && (classSymbol.isAbstract || classSymbol.isTrait)
    }

    def isEnumOrModuleValue(t: Type): Boolean = t.typeSymbol.isModuleClass

    def isCaseClass(t: Type): Boolean =
      t.typeSymbol.isClass && t.typeSymbol.asClass.isCaseClass

    def directSubTypes(t: Type): List[Type] = {
      val tpeClass         = t.typeSymbol.asClass
      val tpeTypeArgs      = t.typeArgs.map(_.dealias)
      var tpeParamsAndArgs = Map.empty[String, Type]
      if (tpeTypeArgs.nonEmpty) {
        tpeClass.typeParams.zip(tpeTypeArgs).foreach { case (typeParam, tpeTypeArg) =>
          tpeParamsAndArgs = tpeParamsAndArgs.updated(typeParam.toString, tpeTypeArg)
        }
      }
      tpeClass.knownDirectSubclasses.toList.map { symbol =>
        val classSymbol = symbol.asClass
        var classType   = if (classSymbol.isModuleClass) classSymbol.module.typeSignature else classSymbol.toType
        if (tpeTypeArgs.nonEmpty) {
          val typeParams = classSymbol.typeParams
          if (typeParams.nonEmpty) {
            val childBaseType     = classType.baseType(tpeClass)
            val childBaseTypeArgs = childBaseType.typeArgs.map(_.dealias)
            var childParamsToArgs = Map.empty[String, Type]
            childBaseTypeArgs.zip(tpeTypeArgs).foreach { case (baseArg, parentArg) =>
              baseArg match {
                case TypeRef(_, sym, Nil) if sym.isType =>
                  childParamsToArgs = childParamsToArgs.updated(sym.name.toString, parentArg)
                case _ =>
              }
            }
            classType = classType.substituteTypes(
              typeParams,
              typeParams.map { typeParam =>
                childParamsToArgs.get(typeParam.name.toString) match {
                  case Some(typeArg) => typeArg
                  case _             =>
                    tpeParamsAndArgs.get(typeParam.toString) match {
                      case Some(typeArg) => typeArg
                      case _             =>
                        fail(
                          ErrorMessages.renderTypeParamNotDeducible(
                            typeParam.name.toString,
                            symbol.toString,
                            t.toString,
                            color
                          )
                        )
                    }
                }
              }
            )
          }
        }
        classType
      }
    }

    def primaryConstructor(t: Type): MethodSymbol = t.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(fail(ErrorMessages.renderNoPrimaryCtorForUnscoped(t.toString, color)))

    def getConstructorParams(t: Type): List[Type] = {
      val ctor          = primaryConstructor(t)
      val params        = ctor.paramLists.flatten
      val tpeTypeArgs   = t.typeArgs
      val tpeTypeParams = if (tpeTypeArgs.nonEmpty) t.typeSymbol.asClass.typeParams else Nil
      params.map { param =>
        var fTpe = param.asTerm.typeSignature.dealias
        if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
        fTpe
      }
    }

    def requireUnscopedForType(t: Type, context: String, seen: Set[Type]): Unit = {
      val dealiased = t.dealias

      if (seen.contains(dealiased)) {
        return
      }
      val newSeen = seen + dealiased

      val unscopedTpe = appliedType(typeOf[Unscoped[_]].typeConstructor, dealiased)

      if (c.inferImplicitValue(unscopedTpe, silent = true) != EmptyTree) {
        return
      }

      if (isEnumOrModuleValue(dealiased)) {
        return
      }

      if (isCaseClass(dealiased)) {
        val fieldTypes = getConstructorParams(dealiased)
        fieldTypes.foreach { fieldType =>
          requireUnscopedForType(fieldType, s"field of $dealiased", newSeen)
        }
        return
      }

      if (isSealedTraitOrAbstractClass(dealiased)) {
        val subtypes = directSubTypes(dealiased)
        if (subtypes.isEmpty) {
          fail(ErrorMessages.renderSealedNoSubclasses(dealiased.toString, color))
        }
        subtypes.foreach { subtype =>
          requireUnscopedForType(subtype, s"subtype of $dealiased", newSeen)
        }
        return
      }

      fail(ErrorMessages.renderNoUnscopedInstance(tpe.toString, dealiased.toString, context, color))
    }

    requireUnscopedForType(tpe, "top-level", Set.empty)

    c.Expr[Unscoped[A]](q"new _root_.zio.blocks.scope.Unscoped[$tpe] {}")
  }
}
