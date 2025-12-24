package zio.blocks.macros

import scala.annotation.{experimental, MacroAnnotation}
import scala.quoted.*

@experimental
class GenerateOptics extends MacroAnnotation:
  def transform(using Quotes)(definition: quotes.reflect.Definition, companion: Option[quotes.reflect.Definition]): List[quotes.reflect.Definition] =
    import quotes.reflect.*

    // Diagnostic Log
    println(s"!!! MACRO RUNNING: ${definition.name} !!!")

    definition match
      case cls: ClassDef if cls.symbol.flags.is(Flags.Case) =>
        
        val modSym = companion match {
          case Some(obj) => obj.symbol
          case None =>
             Symbol.newModule(
               Symbol.spliceOwner, 
               cls.name, 
               Flags.EmptyFlags, 
               Flags.EmptyFlags, 
               _ => List(TypeRepr.of[AnyRef]), 
               _ => Nil, 
               Symbol.noSymbol
             )
        }

        val lensDefs = cls.symbol.caseFields.map { field =>
           val fieldName = field.name
           val lensName = fieldName
           val fieldTpe = cls.symbol.typeRef.memberType(field)
           val lensType = TypeRepr.of[zio.optics.Lens].appliedTo(List(cls.symbol.typeRef, fieldTpe))
           
           val lensSym = Symbol.newVal(modSym.moduleClass, lensName, lensType, Flags.EmptyFlags, Symbol.noSymbol)
           
           val rhs = (cls.symbol.typeRef.asType, fieldTpe.asType) match {
             case ('[s], '[a]) =>
                val expr = '{
                  zio.optics.Lens[s, a](
                    (obj: s) => Right(${ Select('obj.asTerm, field).asExprOf[a] }),
                    (v: a) => (obj: s) => Right(${
                      val copyMethod = cls.symbol.methodMember("copy").head
                      val args = cls.symbol.caseFields.map { f =>
                        if f.name == fieldName then 'v.asTerm
                        else Select('obj.asTerm, f)
                      }
                      Apply(Select('obj.asTerm, copyMethod), args).asExprOf[s]
                    })
                  )
                }
                expr.asTerm
             case _ => 
                 report.errorAndAbort(s"Cannot derive type for $fieldName", definition.pos)
           }
           
           // Apply recursive owner fix
           val rhsFixed = fixOwner(rhs, lensSym)

           ValDef(lensSym, Some(rhsFixed))
        }

        companion match {
          case Some(obj) =>
            val objClass = obj.asInstanceOf[ClassDef]
            val newObjClass = ClassDef(obj.symbol.moduleClass, objClass.parents, objClass.body ++ lensDefs)
            List(cls, ValDef(obj.symbol.companionModule, None), newObjClass)
          case None =>
            val modClassDef = ClassDef(modSym.moduleClass, List(TypeTree.of[AnyRef]), lensDefs)
            val modValDef = ValDef(modSym, None)
            List(cls, modValDef, modClassDef)
        }

      case _ =>
        report.errorAndAbort("@GenerateOptics only works on case classes", definition.pos)

  // --- RECURSIVE OWNER FIXER ---
  // Fixes the "unexpected owner" error by properly reparenting the generated tree
  def fixOwner(using Quotes)(tree: quotes.reflect.Term, newOwner: quotes.reflect.Symbol): quotes.reflect.Term = {
    import quotes.reflect.*
    
    tree match {
      case Inlined(_, _, body) => body.changeOwner(newOwner)
      case _ => tree.changeOwner(newOwner)
    }
  }