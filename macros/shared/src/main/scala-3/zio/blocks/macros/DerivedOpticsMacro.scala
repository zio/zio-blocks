package zio.blocks.macros

import scala.quoted.*
import zio.optics.*

object DerivedOpticsMacro {
  def impl[T: Type](parent: Expr[Any], underscore: Boolean)(using q: Quotes): Expr[Any] =
    new MacroHelpers[T](using q, Type.of[T]).generate(parent, underscore)
}

class MacroHelpers[T](using val q: Quotes, val t: Type[T]) {
  import q.reflect.*

  def generate(parent: Expr[Any], underscore: Boolean): Expr[Any] = {
    val tpe    = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    if (symbol.flags.is(Flags.Case)) {
      generateCaseClassOptics(tpe, symbol, parent, underscore)
    } else if (symbol.flags.is(Flags.Sealed) || (symbol.flags.is(Flags.Enum) && !symbol.flags.is(Flags.Case))) {
      generateSealedTraitOptics(tpe, symbol, parent, underscore)
    } else {
      report.errorAndAbort(s"DerivedOptics only supports Case Classes, Sealed Traits, or Enums. Found: ${symbol.name}")
    }
  }

  private def generateImpl(
    @scala.annotation.unused tpe: TypeRepr,
    @scala.annotation.unused parent: Expr[Any],
    refinements: List[(String, TypeRepr)],
    valDefsGenerator: Symbol => List[ValDef]
  ): Expr[Any] = {

    val selectableType = TypeRepr.of[scala.reflect.Selectable]
    val refinedType    = refinements.foldLeft(selectableType) { case (acc, (name, t)) =>
      Refinement(acc, name, t)
    }

    val clsSymbol = Symbol.newClass(
      Symbol.spliceOwner,
      "OpticsImpl",
      List(TypeRepr.of[Object], selectableType),
      cls =>
        refinements.map { case (name, t) =>
          Symbol.newVal(cls, name, t, Flags.EmptyFlags, Symbol.noSymbol)
        },
      None
    )

    val valDefs     = valDefsGenerator(clsSymbol)
    val newCls      = ClassDef(clsSymbol, List(TypeTree.of[Object], TypeTree.of[scala.reflect.Selectable]), valDefs)
    val newInstance = Apply(Select(New(TypeIdent(clsSymbol)), clsSymbol.primaryConstructor), Nil)

    // Return the new instance directly
    val instanceExpr = Block(List(newCls), newInstance).asExpr

    refinedType.asType match {
      case '[rt] =>
        '{ ${ instanceExpr }.asInstanceOf[rt] }
    }
  }

  private def generateCaseClassOptics(
    tpe: TypeRepr,
    symbol: Symbol,
    parent: Expr[Any],
    underscore: Boolean
  ): Expr[Any] = {
    val fields = symbol.caseFields

    val refinements = fields.map { field =>
      val fieldName    = field.name
      val accessorName = if (underscore) "_" + fieldName else fieldName
      val fieldTpe     = tpe.memberType(field)
      val lensType     = TypeRepr.of[Lens].appliedTo(List(tpe, fieldTpe))
      (accessorName, lensType)
    }

    generateImpl(
      tpe,
      parent,
      refinements,
      clsSymbol =>
        fields.zip(refinements).map { case (field, (name, _)) =>
          val fieldName = field.name
          val fieldTpe  = tpe.memberType(field)

          val rhs = (tpe.asType, fieldTpe.asType) match {
            case ('[s], '[a]) =>
              '{
                Lens[s, a](
                  (s: s) => Right(${ Select('s.asTerm, field).asExprOf[a] }),
                  (v: a) =>
                    (s: s) =>
                      Right(${
                        val copyMethod = symbol.methodMember("copy").head
                        val args       = symbol.caseFields.map { f =>
                          if f.name == fieldName then 'v.asTerm
                          else Select('s.asTerm, f)
                        }
                        Apply(Select('s.asTerm, copyMethod), args).asExprOf[s]
                      })
                )
              }
            case _ => report.errorAndAbort(s"Unexpected types in lens generation")
          }
          val sym = clsSymbol.declaredField(name)
          ValDef(sym, Some(rhs.asTerm.changeOwner(sym)))
        }
    )
  }

  private def generateSealedTraitOptics(
    tpe: TypeRepr,
    symbol: Symbol,
    parent: Expr[Any],
    underscore: Boolean
  ): Expr[Any] = {
    val children = symbol.children

    val refinements = children.map { child =>
      val childName    = child.name
      val accessorName = (if (underscore) "_" else "") + childName.head.toLower + childName.tail

      // For generic children, we need to apply wildcard type arguments
      val childTpe: TypeRepr = {
        val typeParams = child.typeMembers.filter(_.isTypeParam)
        if (typeParams.isEmpty) {
          // Non-generic: case object or non-parameterized case class
          if (child.flags.is(Flags.Module)) {
            child.typeRef
          } else {
            child.typeRef
          }
        } else {
          // Generic: apply wildcards for each type parameter
          val wildcards = typeParams.map(_ => TypeBounds.empty)
          child.typeRef.appliedTo(wildcards.map(_ => TypeRepr.of[Any]))
        }
      }

      val prismType = TypeRepr.of[Prism].appliedTo(List(tpe, childTpe))
      (accessorName, prismType, child, childTpe)
    }

    generateImpl(
      tpe,
      parent,
      refinements.map(r => (r._1, r._2)),
      clsSymbol =>
        refinements.map { case (name, _, _, childApplied) =>
          val rhs = (tpe.asType, childApplied.asType) match {
            case ('[s], '[a]) =>
              '{
                Prism[s, a](
                  (s: s) =>
                    ${
                      '{
                        if (s.isInstanceOf[a]) Right(s.asInstanceOf[a])
                        else Left(OpticFailure("Not a match"))
                      }.asExprOf[Either[OpticFailure, a]]
                    },
                  (a: a) => Right(a.asInstanceOf[s])
                )
              }
            case _ => report.errorAndAbort(s"Unexpected types in prism generation")
          }
          val sym = clsSymbol.declaredField(name)
          ValDef(sym, Some(rhs.asTerm.changeOwner(sym)))
        }
    )
  }
}
