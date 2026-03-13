package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.{DynamicOptic, Schema}

object MigrationMacros {

  // ─────────────────────────────────────────────────────────────────────────
  // Selector macro: extract DynamicOptic from a lambda at compile time
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Extracts a [[DynamicOptic]] from a selector lambda at compile time.
   *
   * Supports field access (`_.field`) and nested field access
   * (`_.outer.inner`). The lambda body is walked at compile time to produce a
   * pure-data path — no runtime schema reflection required.
   *
   * {{{
   * selectorToDynamicOptic[Person](_.name)          // DynamicOptic(.name)
   * selectorToDynamicOptic[Person](_.address.street) // DynamicOptic(.address.street)
   * }}}
   */
  inline def selectorToDynamicOptic[S](inline path: S => Any): DynamicOptic =
    ${ selectorToDynamicOpticImpl[S]('path) }

  private def selectorToDynamicOpticImpl[S: Type](path: Expr[S => Any])(using q: Quotes): Expr[DynamicOptic] =
    extractDynamicOpticExpr(path)

  /**
   * Shared logic: walk a lambda expression and build a `DynamicOptic`
   * expression.
   */
  private def extractDynamicOpticExpr(using q: Quotes)(lambda: Expr[Any]): Expr[DynamicOptic] = {
    import q.reflect.*

    @annotation.tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractNodes(term: Term): List[Expr[DynamicOptic.Node]] = term match {
      case Select(parent, fieldName) =>
        extractNodes(parent) :+ '{ new DynamicOptic.Node.Field(${ Expr(fieldName) }) }
      case _: Ident =>
        Nil // root — the lambda parameter
      case _ =>
        report.errorAndAbort(
          s"Unsupported path element: '${term.show}'. " +
            "Selector macros support field access (_.field) and nested field access (_.outer.inner)."
        )
    }

    val body  = toPathBody(lambda.asTerm)
    val nodes = extractNodes(body)
    if (nodes.isEmpty) '{ DynamicOptic.root }
    else {
      val nodesExpr = Expr.ofSeq(nodes)
      '{ new DynamicOptic(Vector($nodesExpr: _*)) }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Extension methods: selector-based builder API
  // ─────────────────────────────────────────────────────────────────────────

  extension [A, B](builder: MigrationBuilder[A, B]) {

    /**
     * Add a new field identified by a selector on the target type.
     *
     * {{{
     * Migration.newBuilder[PersonV0, PersonV1]
     *   .addField(_.age, 0)
     * }}}
     */
    inline def addField[V](inline target: B => Any, default: V)(using schema: Schema[V]): MigrationBuilder[A, B] =
      ${ addFieldImpl[A, B, V]('builder, 'target, 'default, 'schema) }

    /** Drop a field identified by a selector on the source type. */
    inline def dropField(inline source: A => Any): MigrationBuilder[A, B] =
      ${ dropFieldImpl[A, B]('builder, 'source) }

    /**
     * Rename a field from the source type to the target type using selectors.
     *
     * {{{
     * Migration.newBuilder[PersonV0, PersonV1]
     *   .renameField(_.firstName, _.fullName)
     * }}}
     */
    inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] =
      ${ renameFieldImpl[A, B]('builder, 'from, 'to) }

    /** Transform a field's value using selectors and a pure expression. */
    inline def transformField(
      inline from: A => Any,
      inline to: B => Any,
      transform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      ${ transformFieldImpl[A, B]('builder, 'from, 'to, 'transform) }

    /** Make an optional field mandatory using selectors. */
    inline def mandateField[V](
      inline source: A => Any,
      inline target: B => Any,
      default: V
    )(using schema: Schema[V]): MigrationBuilder[A, B] =
      ${ mandateFieldImpl[A, B, V]('builder, 'source, 'target, 'default, 'schema) }

    /** Make a mandatory field optional using selectors. */
    inline def optionalizeField(
      inline source: A => Any,
      inline target: B => Any
    ): MigrationBuilder[A, B] =
      ${ optionalizeFieldImpl[A, B]('builder, 'source, 'target) }

    /** Change a field's primitive type using selectors. */
    inline def changeFieldType(
      inline source: A => Any,
      inline target: B => Any,
      converter: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      ${ changeFieldTypeImpl[A, B]('builder, 'source, 'target, 'converter) }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Macro implementations
  //
  // Each macro extracts field names from the selector lambdas and delegates
  // to the string-based builder methods, which encode the new action
  // signatures correctly.
  // ─────────────────────────────────────────────────────────────────────────

  private def addFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    default: Expr[V],
    schema: Expr[Schema[V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractDynamicOpticExpr(target)
    '{
      val _b         = $builder
      val (_, _name) = _b.splitOptic($optic)
      _b.addField(_name, $default)(using $schema)
    }
  }

  private def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractDynamicOpticExpr(source)
    '{
      val _b         = $builder
      val (_, _name) = _b.splitOptic($optic)
      _b.dropField(_name)
    }
  }

  private def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromOptic = extractDynamicOpticExpr(from)
    val toOptic   = extractDynamicOpticExpr(to)
    '{
      val _b          = $builder
      val (_, _fromN) = _b.splitOptic($fromOptic)
      val (_, _toN)   = _b.splitOptic($toOptic)
      _b.renameField(_fromN, _toN)
    }
  }

  private def transformFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any],
    transform: Expr[DynamicSchemaExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromOptic = extractDynamicOpticExpr(from)
    val toOptic   = extractDynamicOpticExpr(to)
    '{
      val _b          = $builder
      val (_, _fromN) = _b.splitOptic($fromOptic)
      val (_, _toN)   = _b.splitOptic($toOptic)
      _b.transformField(_fromN, _toN, $transform)
    }
  }

  private def mandateFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    target: Expr[B => Any],
    default: Expr[V],
    schema: Expr[Schema[V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = extractDynamicOpticExpr(source)
    val targetOptic = extractDynamicOpticExpr(target)
    '{
      val _b            = $builder
      val (_, _sourceN) = _b.splitOptic($sourceOptic)
      val (_, _targetN) = _b.splitOptic($targetOptic)
      _b.mandateField(_sourceN, _targetN, $default)(using $schema)
    }
  }

  private def optionalizeFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    target: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = extractDynamicOpticExpr(source)
    val targetOptic = extractDynamicOpticExpr(target)
    '{
      val _b            = $builder
      val (_, _sourceN) = _b.splitOptic($sourceOptic)
      val (_, _targetN) = _b.splitOptic($targetOptic)
      _b.optionalizeField(_sourceN, _targetN)
    }
  }

  private def changeFieldTypeImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    target: Expr[B => Any],
    converter: Expr[DynamicSchemaExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = extractDynamicOpticExpr(source)
    val targetOptic = extractDynamicOpticExpr(target)
    '{
      val _b            = $builder
      val (_, _sourceN) = _b.splitOptic($sourceOptic)
      val (_, _targetN) = _b.splitOptic($targetOptic)
      _b.changeFieldType(_sourceN, _targetN, $converter)
    }
  }
}
