package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

import scala.quoted._

// A type class to convert a selector function S => A into a DynamicOptic
trait ToDynamicOptic[S, A] {
  def apply(): DynamicOptic
}

object ToDynamicOptic {
  implicit inline def materialize[S, A]: ToDynamicOptic[S, A] =
    ${ ToDynamicOpticMacro.materialize[S, A] }
}

object ToDynamicOpticMacro {
  def materialize[S: Type, A: Type](using Quotes): Expr[ToDynamicOptic[S, A]] = {
    import quotes.reflect._

    def extractPath(term: Term): DynamicOptic = {
      term match {
        case Select(qualifier, name) =>
          val parentOptic = extractPath(qualifier)
          name match {
            case "each"      => parentOptic.elements
            case "eachKey"   => parentOptic.mapKeys
            case "eachValue" => parentOptic.mapValues
            case _           => parentOptic.field(name)
          }
        case Apply(Select(qualifier, "apply"), List(Literal(IntConstant(index)))) =>
          val parentOptic = extractPath(qualifier)
          parentOptic.at(index)
        case Apply(Select(qualifier, "at"), List(Literal(IntConstant(index)))) =>
          val parentOptic = extractPath(qualifier)
          parentOptic.at(index)
        case Apply(Select(qualifier, "field"), List(Literal(StringConstant(name)))) =>
          val parentOptic = extractPath(qualifier)
          parentOptic.field(name)
        case TypeApply(Select(qualifier, "when"), List(AppliedType(_, List(Ident(caseName))))) =>
          val parentOptic = extractPath(qualifier)
          parentOptic.caseOf(caseName)
        case Ident(name) if name == "x" => // Assuming 'x' is the parameter name for the lambda `s => ...`
          DynamicOptic.root
        case _ =>
          report.errorAndAbort(s"Unsupported selector expression: ${term.show}")
      }
    }

    val lambdaTree = Expr.betaReduce('{ (s: S) => ??? }).asTerm // Dummy lambda to get the type of S

    lambdaTree match {
      case Lambda(List(param), body) =>
        val optic = extractPath(body)
        '{
          new ToDynamicOptic[S, A] {
            def apply(): DynamicOptic = ${ Expr(optic) }
          }
        }
      case _ =>
        report.errorAndAbort("Expected a lambda expression for the selector.")
    }
  }
}
