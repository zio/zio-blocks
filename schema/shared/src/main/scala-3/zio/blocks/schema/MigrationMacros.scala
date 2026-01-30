package zio.blocks.schema

import scala.quoted.*

object MigrationMacros {

  /**
   * Extract a `DynamicOptic` from a lambda selector expression at compile time.
   *
   * Supports:
   *   - `_.fieldName` -> `DynamicOptic(Vector(Node.Field("fieldName")))`
   *   - `_.a.b.c` -> `DynamicOptic(Vector(Node.Field("a"), Node.Field("b"),
   *     Node.Field("c")))`
   */
  def extractPath[S: Type](path: Expr[S => Any])(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect.*

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inner)                            => toPathBody(inner)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractNodes(term: Term): List[Expr[DynamicOptic.Node]] = term match {
      case Select(parent, fieldName) =>
        val nameExpr = Expr(fieldName)
        extractNodes(parent) :+ '{ DynamicOptic.Node.Field($nameExpr) }
      case _: Ident =>
        Nil
      case _ =>
        report.errorAndAbort(
          s"Migration selectors support field access only (_.field or _.a.b.c), got '${term.show}'"
        )
    }

    val nodes      = extractNodes(toPathBody(path.asTerm))
    val vectorExpr = Expr.ofSeq(nodes)
    '{ DynamicOptic(Vector($vectorExpr: _*)) }
  }

  def addFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    default: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path     = extractParentPath[B](target)
    val nameExpr = extractLastFieldName[B](target)
    '{ $builder.addField($path, $nameExpr, $default) }
  }

  def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    defaultForReverse: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path     = extractParentPath[A](source)
    val nameExpr = extractLastFieldName[A](source)
    '{ $builder.dropField($path, $nameExpr, $defaultForReverse) }
  }

  def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromName = extractLastFieldName[A](from)
    val toName   = extractLastFieldName[B](to)
    val atPath   = extractParentPath[A](from)
    '{ $builder.renameField($atPath, $fromName, $toName) }
  }

  def mandateImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    default: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = extractPath[A](source)
    '{ $builder.mandate($path, $default) }
  }

  def optionalizeImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = extractPath[A](source)
    '{ $builder.optionalize($path) }
  }

  // Extract the last field name from a selector lambda at compile time.
  private def extractLastFieldName[S: Type](path: Expr[S => Any])(using q: Quotes): Expr[String] = {
    import q.reflect.*

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inner)                            => toPathBody(inner)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def lastField(term: Term): String = term match {
      case Select(_, fieldName) => fieldName
      case _                    => report.errorAndAbort(s"Expected a field selector like _.fieldName, got '${term.show}'")
    }

    Expr(lastField(toPathBody(path.asTerm)))
  }

  // Extract the parent path (all nodes except the last) from a selector lambda.
  private def extractParentPath[S: Type](path: Expr[S => Any])(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect.*

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inner)                            => toPathBody(inner)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractNodes(term: Term): List[Expr[DynamicOptic.Node]] = term match {
      case Select(parent, fieldName) =>
        val nameExpr = Expr(fieldName)
        extractNodes(parent) :+ '{ DynamicOptic.Node.Field($nameExpr) }
      case _: Ident =>
        Nil
      case _ =>
        report.errorAndAbort(
          s"Migration selectors support field access only (_.field or _.a.b.c), got '${term.show}'"
        )
    }

    val allNodes    = extractNodes(toPathBody(path.asTerm))
    val parentNodes = if (allNodes.nonEmpty) allNodes.init else allNodes
    val vectorExpr  = Expr.ofSeq(parentNodes)
    '{ DynamicOptic(Vector($vectorExpr: _*)) }
  }
}
