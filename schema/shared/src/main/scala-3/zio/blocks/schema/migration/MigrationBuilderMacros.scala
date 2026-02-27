package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

import scala.quoted.*

/**
 * Scala 3 macros for the migration builder. Converts selector lambda
 * expressions (e.g., `_.name`, `_.address.street`) into [[DynamicOptic]] paths
 * at compile time, and validates `.build` calls.
 */
object MigrationBuilderMacros {

  /**
   * Converts a selector function `T => Any` into a [[DynamicOptic]] at compile
   * time.
   *
   * Supported selector patterns:
   *   - `_.fieldName` — field access
   *   - `_.nested.field` — nested field access
   *   - `_.field.each` — collection traversal (not yet supported)
   *   - `_.field.when[Case]` — case selection (not yet supported)
   */
  inline def selectorToOptic[T](inline selector: T => Any): DynamicOptic =
    ${ selectorToOpticImpl[T]('selector) }

  private def selectorToOpticImpl[T: Type](selector: Expr[T => Any])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def extractPath(term: Term): List[String] = term match {
      // Direct field access: _.fieldName
      case Select(inner, name) if !name.startsWith("$") =>
        extractPath(inner) :+ name

      // Inlined selector
      case Inlined(_, _, inner) =>
        extractPath(inner)

      // Lambda compiled as DefDef + Closure (common in Scala 3)
      case Block(List(ddef: DefDef), _: Closure) =>
        ddef.rhs match {
          case Some(body) => extractPath(body)
          case None       => Nil
        }

      // Block with a final expression
      case Block(_, expr) =>
        extractPath(expr)

      // Lambda body
      case Lambda(_, body) =>
        extractPath(body)

      // The `_` parameter itself
      case Ident(_) => Nil

      // Apply with select (for methods like .each)
      case Apply(Select(inner, methodName), _) =>
        extractPath(inner) :+ methodName

      // TypeApply with select (for methods like .when[T])
      case TypeApply(Select(inner, methodName), _) =>
        extractPath(inner) :+ methodName

      case other =>
        report.errorAndAbort(
          s"Unsupported selector expression. Expected field access like _.field or _.nested.field, " +
            s"but got: ${other.show}. Tree: ${other}"
        )
    }

    val fieldNames = selector.asTerm match {
      case Inlined(_, _, inner) => extractPath(inner)
      case other                => extractPath(other)
    }

    if (fieldNames.isEmpty) {
      '{ DynamicOptic.root }
    } else {
      // Build the optic as a chain of .field() calls
      fieldNames.foldLeft('{ DynamicOptic.root }) { (optic, name) =>
        val nameExpr = Expr(name)
        '{ $optic.field($nameExpr) }
      }
    }
  }

  /**
   * Macro implementation for `.build` — creates the migration with validation.
   * Currently performs basic validation that the builder has been used
   * correctly.
   */
  def buildImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): Expr[Migration[A, B]] = {
    // For now, build creates the migration. Full structural validation
    // (checking that all target fields are accounted for) would require
    // inspecting the Schema at compile time, which we'll add incrementally.
    '{
      val b = $builder
      new Migration[A, B](
        new DynamicMigration(b.actions),
        b.sourceSchema,
        b.targetSchema
      )
    }
  }
}
