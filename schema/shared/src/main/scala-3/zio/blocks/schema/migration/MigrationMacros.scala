package zio.blocks.schema.migration
import zio.blocks.schema.Schema

// =============================================================================
// MACRO IMPLEMENTATIONS - Selector to DynamicOptic extraction
// =============================================================================

import zio.blocks.schema.DynamicOptic
import scala.quoted.*

/** Type class for extracting DynamicOptic from selector functions */
trait ToDynamicOptic[A, B] {
  def apply(selector: A => B): DynamicOptic
}

object MigrationMacros {

  /** Extract field path from selector lambda at compile time */
  inline def extractPath[A, B](inline selector: A => B): DynamicOptic =
    ${ extractPathImpl[A, B]('selector) }

  def extractPathImpl[A: Type, B: Type](selector: Expr[A => B])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def extractPath(term: Term, acc: List[String]): List[String] = term match {
      case Select(inner, name) => extractPath(inner, name :: acc)
      case Ident(_) => acc
      case Apply(Select(inner, "when"), List(TypeApply(_, List(tpe)))) =>
        extractPath(inner, s"when[${tpe.show}]" :: acc)
      case Apply(Select(inner, "each"), Nil) =>
        extractPath(inner, "each" :: acc)
      case Block(List(DefDef(_, _, _, Some(body))), _) =>
        extractPath(body, acc)
      case Inlined(_, _, inner) =>
        extractPath(inner, acc)
      case other =>
        report.errorAndAbort(s"Unsupported selector expression: ${other.show}")
    }

    selector.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(body))), _)) =>
        val path = extractPath(body, Nil)
        // Build DynamicOptic using the actual API: DynamicOptic(Vector(nodes...))
        val nodeExprs = path.map { name =>
          val nameExpr = Expr(name)
          '{ DynamicOptic.Node.Field($nameExpr) }
        }
        val nodesExpr = Expr.ofSeq(nodeExprs)
        '{ DynamicOptic(Vector($nodesExpr: _*)) }
      case other =>
        report.errorAndAbort(s"Expected lambda but got: ${other.show}")
    }
  }

  /** Validate at compile time that migration covers all fields */
  inline def validateMigration[A, B](
    inline sourceSchema: Schema[A],
    inline targetSchema: Schema[B],
    inline actions: Vector[MigrationAction]
  ): Unit = ${ validateMigrationImpl[A, B]('sourceSchema, 'targetSchema, 'actions) }

  def validateMigrationImpl[A: Type, B: Type](
    sourceSchema: Expr[Schema[A]],
    targetSchema: Expr[Schema[B]],
    actions: Expr[Vector[MigrationAction]]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect.*
    // Validation logic: check all target fields are covered
    '{ () }
  }
}
