package zio.schema.migration

import scala.quoted._
import zio.schema._
import zio.Chunk
import zio.schema.DynamicValue

object GhostMigrationDeriver {
  inline def derive[A, B]: Migration = ${ deriveImpl[A, B] }

  def deriveImpl[A: Type, B: Type](using Quotes): Expr[Migration] = {
    import quotes.reflect._

    // Helper to safely summon schemas or provide a fallback
    def summonSchema(tpe: TypeRepr): Expr[Schema[Any]] = {
      tpe.asType match {
        case '[t] =>
          Expr.summon[Schema[t]] match {
            case Some(s) => '{ $s.asInstanceOf[Schema[Any]] }
            case None    => '{ Schema.dynamicValue.asInstanceOf[Schema[Any]] }
          }
      }
    }

    def buildMigration(fromTpe: TypeRepr, toTpe: TypeRepr, stack: Set[TypeRepr]): Expr[Migration] = {
      val fromDealiased = fromTpe.dealias
      val toDealiased = toTpe.dealias

      // Recursion and Identity Check
      if (fromDealiased =:= toDealiased || stack.exists(_ =:= fromDealiased)) {
        '{ Migration.Identity }
      } else {
        val nextStack = stack + fromDealiased

        // CASE 1: Sealed Traits (Sum Types)
        if (fromDealiased.typeSymbol.flags.is(Flags.Sealed)) {
          val aChildren = fromDealiased.typeSymbol.children
          val bChildren = toDealiased.typeSymbol.children

          val removals = aChildren.filterNot(a => bChildren.exists(_.name == a.name)).map { aSym =>
            '{ Migration.RemoveCase(${Expr(aSym.name)}) }
          }

          val additions = bChildren.filterNot(b => aChildren.exists(_.name == b.name)).map { bSym =>
            '{ Migration.AddCase(${Expr(bSym.name)}) }
          }

          val transformations = aChildren.flatMap { aChild =>
            bChildren.find(_.name == aChild.name).flatMap { bChild =>
              val aChildTpe = fromDealiased.memberType(aChild)
              val bChildTpe = toDealiased.memberType(bChild)
              
              if (aChildTpe =:= bChildTpe) None
              else {
                val childMigration = buildMigration(aChildTpe, bChildTpe, nextStack)
                Some('{ Migration.Node(${Expr(aChild.name)}, Schema.dynamicValue, Schema.dynamicValue, $childMigration) })
              }
            }
          }

          val allSumSteps = removals ++ additions ++ transformations
          if (allSumSteps.isEmpty) '{ Migration.Identity }
          else '{ Migration.Incremental(Chunk.fromIterable(${Expr.ofList(allSumSteps.toList)})) }
        } 
        
        // CASE 2: Case Classes (Product Types)
        else {
          val aFields = fromDealiased.typeSymbol.caseFields
          val bFields = toDealiased.typeSymbol.caseFields

          // 1. Field Removals
          val removals = aFields.filterNot(a => bFields.exists(_.name == a.name)).map { aSym =>
            val aFTpe = fromDealiased.memberType(aSym)
            val schemaA = summonSchema(aFTpe)
            '{ Migration.RemoveField(${Expr(aSym.name)}, $schemaA, DynamicValue.None) }
          }

          // 2. Field Transformations & Nested Nodes
          val transformsAndNodes = bFields.flatMap { bSym =>
            val bFTpe = toDealiased.memberType(bSym).dealias
            val aSymOpt = aFields.find(_.name == bSym.name)

            aSymOpt match {
              case Some(aSym) =>
                val aFTpe = fromDealiased.memberType(aSym).dealias
                if (aFTpe =:= bFTpe) None
                else {
                  val schemaA = summonSchema(aFTpe)
                  val schemaB = summonSchema(bFTpe)

                  // Check if it's a nested structure or collection
                  if ((aFTpe <:< TypeRepr.of[Iterable[?]] && bFTpe <:< TypeRepr.of[Iterable[?]]) || 
                      (aFTpe.typeSymbol.isClassDef && !aFTpe.derivesFrom(Symbol.requiredClass("scala.AnyVal")))) {
                    
                    val nestedMigration = if (aFTpe <:< TypeRepr.of[Iterable[?]]) {
                      val aArg = aFTpe.typeArgs.headOption.getOrElse(TypeRepr.of[Any])
                      val bArg = bFTpe.typeArgs.headOption.getOrElse(TypeRepr.of[Any])
                      buildMigration(aArg, bArg, nextStack)
                    } else {
                      buildMigration(aFTpe, bFTpe, nextStack)
                    }
                    Some('{ Migration.Node(${Expr(bSym.name)}, $schemaA, $schemaB, $nestedMigration) })
                  } else {
                    // Simple Transformation logic
                    val transformFunc = '{ (v: Any) => v } // Simplified for macro stability
                    Some('{ Migration.Transform(${Expr(bSym.name)}, $schemaA, $schemaB, $transformFunc) })
                  }
                }
              case None => None
            }
          }

          // 3. Field Additions with Smart Defaults
          val additions = bFields.filterNot(b => aFields.exists(_.name == b.name)).map { bSym =>
            val bFTpe = toDealiased.memberType(bSym).dealias
            val schemaB = summonSchema(bFTpe)
            
            val zeroValue = 
              if (bFTpe <:< TypeRepr.of[Option[?]]) '{ DynamicValue.Optional(None) }
              else if (bFTpe <:< TypeRepr.of[String]) '{ DynamicValue.Primitive("", Schema.primitive[String]) }
              else if (bFTpe <:< TypeRepr.of[Int]) '{ DynamicValue.Primitive(0, Schema.primitive[Int]) }
              else if (bFTpe <:< TypeRepr.of[Iterable[?]]) '{ DynamicValue.Sequence(Chunk.empty) }
              else '{ DynamicValue.None }

            '{ Migration.AddField(${Expr(bSym.name)}, $schemaB, $zeroValue) }
          }

          val allSteps = removals ++ transformsAndNodes ++ additions
          if (allSteps.isEmpty) '{ Migration.Identity }
          else '{ Migration.Incremental(Chunk.fromIterable(${Expr.ofList(allSteps.toList)})) }
        }
      }
    }

    buildMigration(TypeRepr.of[A], TypeRepr.of[B], Set.empty)
  }
}
