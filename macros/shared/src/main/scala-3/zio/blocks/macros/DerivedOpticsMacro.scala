package zio.blocks.macros

import scala.quoted.*
import zio.optics.*

object DerivedOpticsMacro {
  def impl[T: Type](parent: Expr[Any], underscore: Boolean)(using q: Quotes): Expr[Any] = {
    new MacroHelpers[T](using q, Type.of[T]).generate(parent, underscore)
  }
}

class MacroHelpers[T](using val q: Quotes, val t: Type[T]) {
  import q.reflect.*

  def generate(parent: Expr[Any], underscore: Boolean): Expr[Any] = {
    val tpe = TypeRepr.of[T]
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
      tpe: TypeRepr,
      parent: Expr[Any],
      refinements: List[(String, TypeRepr)],
      valDefsGenerator: Symbol => List[ValDef]
  ): Expr[Any] = {

    val refinedType = refinements.foldLeft(TypeRepr.of[Object]) { case (acc, (name, t)) =>
      Refinement(acc, name, t)
    }

    val clsSymbol = Symbol.newClass(
      Symbol.spliceOwner,
      "OpticsImpl",
      List(TypeRepr.of[Object]),
      cls => refinements.map { case (name, t) =>
        Symbol.newVal(cls, name, t, Flags.Override, Symbol.noSymbol)
      },
      None 
    )

    val valDefs = valDefsGenerator(clsSymbol)
    val newCls = ClassDef(clsSymbol, List(TypeTree.of[Object]), valDefs)
    val newInstance = Apply(Select(New(TypeIdent(clsSymbol)), clsSymbol.primaryConstructor), Nil)

    // Caching Logic
    val parentTerm = parent.asTerm
    
    val cacheField = parentTerm.tpe.classSymbol.flatMap(_.fieldMember("_opticsCache") match {
       case s if s == Symbol.noSymbol => None 
       case s => Some(s)
    }).getOrElse {
       report.errorAndAbort(s"Could not find '_opticsCache' in parent object. Ensure it extends DerivedOptics.")
    }

    val cacheSelect = Select(parentTerm, cacheField)

    val block = '{
      if (${cacheSelect.asExprOf[Any]} == null) {
        val impl = ${Block(List(newCls), newInstance).asExpr}
        ${Assign(cacheSelect, 'impl.asTerm).asExpr}
      }
      ${cacheSelect.asExprOf[Any]}
    }

    refinedType.asType match {
      case '[rt] =>
        block.asTerm.tpe.asType match {
          case '[r] => '{ ${block}.asInstanceOf[r & rt] }
        }
    }
  }

  private def generateCaseClassOptics(tpe: TypeRepr, symbol: Symbol, parent: Expr[Any], underscore: Boolean): Expr[Any] = {
    val fields = symbol.caseFields

    val refinements = fields.map { field =>
      val fieldName = field.name
      val accessorName = if (underscore) "_" + fieldName else fieldName
      val fieldTpe = tpe.memberType(field)
      val lensType = TypeRepr.of[Lens].appliedTo(List(tpe, fieldTpe))
      (accessorName, lensType)
    }

    generateImpl(tpe, parent, refinements, clsSymbol =>
      fields.zip(refinements).map { case (field, (name, _)) =>
        val fieldName = field.name
        val fieldTpe = tpe.memberType(field)
        
        val rhs = (tpe.asType, fieldTpe.asType) match {
          case ('[s], '[a]) =>
             '{
               Lens[s, a](
                 (s: s) => Right(${ Select('s.asTerm, field).asExprOf[a] }),
                 (v: a) => (s: s) => Right(${
                   val copyMethod = symbol.methodMember("copy").head
                   val args = symbol.caseFields.map { f =>
                     if f.name == fieldName then 'v.asTerm
                     else Select('s.asTerm, f)
                   }
                   Apply(Select('s.asTerm, copyMethod), args).asExprOf[s]
                 })
               )
             }
        }
        val sym = clsSymbol.declaredField(name)
        ValDef(sym, Some(rhs.asTerm))
      }
    )
  }

  private def generateSealedTraitOptics(tpe: TypeRepr, symbol: Symbol, parent: Expr[Any], underscore: Boolean): Expr[Any] = {
    val children = symbol.children

    val refinements = children.map { child =>
      val childName = child.name
      val accessorName = (if (underscore) "_" else "") + childName.head.toLower + childName.tail
      val childTpe = if (child.isClassConstructor) tpe.memberType(child) else child.typeRef
      val childApplied = childTpe match { case t: TypeRepr => t; case _ => TypeRepr.of[Any] }
      val prismType = TypeRepr.of[Prism].appliedTo(List(tpe, childApplied))
      (accessorName, prismType, child, childApplied)
    }

    generateImpl(tpe, parent, refinements.map(r => (r._1, r._2)), clsSymbol =>
      refinements.map { case (name, _, _, childApplied) =>
        val rhs = (tpe.asType, childApplied.asType) match {
          case ('[s], '[a]) =>
             '{
               Prism[s, a](
                 (s: s) => ${
                    '{ 
                       if (s.isInstanceOf[a]) Right(s.asInstanceOf[a]) 
                       else Left(OpticFailure("Not a match"))
                    }.asExprOf[Either[OpticFailure, a]]
                 },
                 (a: a) => a.asInstanceOf[s]
               )
             }
        }
        val sym = clsSymbol.declaredField(name)
        ValDef(sym, Some(rhs.asTerm))
      }
    )
  }
}