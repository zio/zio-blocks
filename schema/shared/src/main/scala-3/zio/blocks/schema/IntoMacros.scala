package zio.blocks.schema

import scala.quoted.*
import scala.collection.mutable

object IntoMacros {

  inline def derive[A, B]: Into[A, B] = ${ deriveImpl[A, B] }

  def deriveImpl[A: Type, B: Type](using Quotes): Expr[Into[A, B]] = {
    import quotes.reflect.*

    // 1. Optimize Identity
    if (TypeRepr.of[A] =:= TypeRepr.of[B]) {
      return '{ Into.identity.asInstanceOf[Into[A, B]] }
    }

    // 2. Resolve existing instances
    Expr.summon[Into[A, B]] match {
      case Some(ev) => ev
      case None     => new IntoDeriver[A, B].derive()
    }
  }

  private class IntoDeriver[A: Type, B: Type](using val quotes: Quotes) {
    import quotes.reflect.*

    case class FieldInfo(name: String, tpe: TypeRepr, index: Int) {
      def isOptional: Boolean = tpe.asType match { case '[Option[_]] => true; case _ => false }
    }

    def derive(): Expr[Into[A, B]] = {
      val sourceFields = extractFields(TypeRepr.of[A])
      val targetFields = extractFields(TypeRepr.of[B])

      if (sourceFields.isEmpty || targetFields.isEmpty) {
        report.errorAndAbort(
          s"Derivation only supported for product types (case classes). Failed for ${Type.show[A]} -> ${Type.show[B]}"
        )
      }

      val mappings = computeMappings(sourceFields, targetFields)
      generateBody(mappings)
    }

    private def extractFields(tpe: TypeRepr): List[FieldInfo] = {
      val sym = tpe.typeSymbol
      if (sym.flags.is(Flags.Case) || sym.fullName.startsWith("scala.Tuple")) {
        sym.caseFields.zipWithIndex.map { case (sym, idx) =>
          FieldInfo(sym.name, tpe.memberType(sym), idx)
        }
      } else Nil
    }

    private def computeMappings(
      sourceFields: List[FieldInfo],
      targetFields: List[FieldInfo]
    ): List[(FieldInfo, Expr[A] => Expr[Either[SchemaError, Any]])] = {
      val usedIndices = mutable.Set[Int]()

      targetFields.map { target =>
        // 1. Exact match
        lazy val exact = sourceFields.find(s => s.name == target.name && s.tpe =:= target.tpe && !usedIndices(s.index))
        // 2. Name match (allows type conversion)
        lazy val exactName = sourceFields.find(s => s.name == target.name && !usedIndices(s.index))
        // 3. Unique type match
        lazy val uniqueType = {
          val candidates       = sourceFields.filter(s => s.tpe =:= target.tpe && !usedIndices(s.index))
          val targetCandidates = targetFields.filter(_.tpe =:= target.tpe)
          if (candidates.size == 1 && targetCandidates.size == 1) Some(candidates.head) else None
        }
        // 4. Positional (tuples)
        lazy val positional =
          if (sourceFields.isDefinedAt(target.index) && !usedIndices(target.index)) Some(sourceFields(target.index))
          else None

        val matchFound = exact.orElse(exactName).orElse(uniqueType).orElse(positional)

        matchFound match {
          case Some(source) =>
            usedIndices += source.index
            target -> { (input: Expr[A]) =>
              val access = Select.unique(input.asTerm, source.name).asExpr
              convertField(access, source.tpe, target.tpe)
            }
          case None if target.isOptional =>
            target -> { (_: Expr[A]) => '{ Right(None) } }
          case None =>
            report.errorAndAbort(s"Could not find mapping for field '${target.name}' in ${Type.show[B]}")
        }
      }
    }

    private def convertField(value: Expr[Any], from: TypeRepr, to: TypeRepr): Expr[Either[SchemaError, Any]] =
      (from.asType, to.asType) match {
        case ('[f], '[t]) =>
          Expr.summon[Into[f, t]].orElse {
            if (extractFields(from).nonEmpty && extractFields(to).nonEmpty)
              Some(IntoMacros.deriveImpl[f, t])
            else None
          } match {
            case Some(conv) => '{ $conv.into($value.asInstanceOf[f]) }
            case None       => report.errorAndAbort(s"No Into instance found for ${from.show} => ${to.show}")
          }
        case _ =>
          report.errorAndAbort(s"Type mismatch or unsupported conversion")
      }

    private def generateBody(
      mappings: List[(FieldInfo, Expr[A] => Expr[Either[SchemaError, Any]])]
    ): Expr[Into[A, B]] = {
      val targetFields = mappings.map(_._1)
      val converters   = mappings.map(_._2)

      '{
        new Into[A, B] {
          def into(input: A): Either[SchemaError, B] = {
            // 1. Run all conversions
            val results: List[Either[SchemaError, Any]] = ${
              Expr.ofList(converters.map(c => c('input)))
            }

            // 2. Validate
            val errors = results.collect { case Left(e) => e }

            if (errors.nonEmpty) {
              SchemaError.accumulateErrors(results).asInstanceOf[Left[SchemaError, B]]
            } else {
              // 3. Unbox
              val values = results.map(_.asInstanceOf[Right[Nothing, Any]].value)

              // 4. Construct using a local method to avoid splice scoping issues
              // Wrap in scala.util.Right to match expected type Either[SchemaError, B]
              scala.util.Right(${ generateConstructionBlock('values, targetFields) })
            }
          }
        }
      }
    }

    private def generateConstructionBlock(values: Expr[List[Any]], fields: List[FieldInfo]): Expr[B] = {
      // Define a local method `def construct(args: List[Any]): B` in the generated AST
      val methodType = MethodType(List("args"))(_ => List(TypeRepr.of[List[Any]]), _ => TypeRepr.of[B])
      val methodSym  = Symbol.newMethod(Symbol.spliceOwner, "construct", methodType)

      val methodDef = DefDef(
        methodSym,
        {
          case List(List(argsTerm: Term)) =>
            // This code generates the body of 'construct'
            val sorted          = fields.zipWithIndex.sortBy(_._1.index)
            val constructorArgs = sorted.map { case (field, idx) =>
              // Generate: args(idx).asInstanceOf[FieldType]
              val apply = Select.overloaded(argsTerm, "apply", Nil, List(Literal(IntConstant(idx))))
              Select.unique(apply, "asInstanceOf").appliedToType(field.tpe)
            }
            val targetSym   = TypeRepr.of[B].typeSymbol
            val constructor = targetSym.primaryConstructor

            Some(Apply(Select(New(TypeTree.of[B]), constructor), constructorArgs))
          case _ => Some('{ ??? }.asTerm)
        }
      )

      // Generate: { def construct(...) = ...; construct(values) }
      Block(
        List(methodDef),
        Apply(Ref(methodSym), List(values.asTerm))
      ).asExprOf[B]
    }
  }
}
