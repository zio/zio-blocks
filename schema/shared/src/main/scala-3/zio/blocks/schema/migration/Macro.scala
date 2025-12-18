package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.Schema

object Macro {

  inline def toPath[S, A](inline selector: S => A): DynamicOptic =
    ${ toPathImpl('selector) }

  def toPathImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def normalize(term: Term): Term = term match {
      case Inlined(_, _, params) => normalize(params)
      case Block(List(), expr)   => normalize(expr)
      case other                 => other
    }

    def processPath(term: Term): List[Expr[DynamicOptic.Node]] =
      term match {
        case Select(qualifier, name) =>
          processPath(qualifier) :+ '{ DynamicOptic.Node.Field(${ Expr(name) }) }
        case Apply(Select(qualifier, "at"), List(Literal(IntConstant(idx)))) =>
          processPath(qualifier) :+ '{ DynamicOptic.Node.AtIndex(${ Expr(idx) }) }
        case Apply(TypeApply(Select(qualifier, "when"), List(tpe)), _) =>
           val tagName = TypeRepr.of(using tpe.tpe).typeSymbol.name
           processPath(qualifier) :+ '{ DynamicOptic.Node.Case(${ Expr(tagName) }) }
        case Select(qualifier, "each") =>
           processPath(qualifier) :+ '{ DynamicOptic.Node.Elements }
        case Ident(_) =>
           // Base case: the lambda argument (s)
           Nil
        case other =>
          report.errorAndAbort(s"Unsupported selector expression: $other")
      }

    val nodes = normalize(selector.asTerm) match {
      case Lambda(_, body) => processPath(body)
      case other           => report.errorAndAbort(s"Expected a lambda selector, got: $other")
    }

    '{ DynamicOptic(Vector(${ Varargs(nodes) }*)) }
  }

  inline def validateMigration[A, B](inline builder: MigrationBuilder[A, B]): Either[String, Migration[A, B]] =
    ${ validateMigrationImpl('builder) }

  def validateMigrationImpl[A: Type, B: Type](builder: Expr[MigrationBuilder[A, B]])(using Quotes): Expr[Either[String, Migration[A, B]]] = {
    import quotes.reflect.*
    
    // TODO: Implement comprehensive validation:
    // 1. Extract source and target schema structures (fields, types)
    // 2. Extract actions from the builder expression (if possible to analyze statically)
    // 3. Verify that all target fields are populated
    // 4. Verify that transformations are type-compatible
    
    // For now, we return the built migration, trusting the builder
    // This allows us to keep the API signature while incrementally adding logic
    
    '{ Right($builder.buildPartial) }
  }
}
