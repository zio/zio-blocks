package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema.{ DynamicOptic, Schema, SchemaExpr }
import MigrationAction.*

// ─────────────────────────────────────────────────────────────────────────────
//  MigrationMacros — Scala 3 macro implementations
//
//  Each macro:
//    1. Inspects the selector expression (e.g. _.address.street)
//    2. Validates it is a supported projection
//    3. Converts it to a DynamicOptic
//    4. Stores the optic in the MigrationAction (never exposed to user)
// ─────────────────────────────────────────────────────────────────────────────

object MigrationMacros {

  // ── Selector → DynamicOptic extraction ────────────────────────────────────

  /**
   * Extract a DynamicOptic from a selector lambda like `_.foo.bar`.
   * Validates that each step is a supported projection:
   *   - field access:       _.foo.bar
   *   - case selection:     _.country.when[UK]
   *   - collection traversal: _.items.each
   */
  def extractOptic[A: Type](selector: Expr[A => Any])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def parseSelector(term: Term): List[String] = term match {
      // _.field
      case Select(inner, fieldName) =>
        parseSelector(inner) :+ fieldName
      // Root lambda param
      case Ident(_) =>
        Nil
      // _.field.when[CaseType] — case selection
      case TypeApply(Select(inner, "when"), _) =>
        parseSelector(inner) :+ "$when"
      // _.field.each — collection traversal
      case Select(inner, "each") =>
        parseSelector(inner) :+ "$each"
      // _.field.key — map key traversal
      case Select(inner, "key") =>
        parseSelector(inner) :+ "$key"
      // _.field.value — map value traversal
      case Select(inner, "value") =>
        parseSelector(inner) :+ "$value"
      case other =>
        report.errorAndAbort(
          s"Unsupported selector expression: ${other.show}. " +
          "Supported: field access (_.foo), case selection (_.when[T]), collection traversal (_.each)"
        )
    }

    selector.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(body))), _)) =>
        val segments = parseSelector(body)
        if (segments.isEmpty)
          report.errorAndAbort("Selector must access at least one field")
        // Build DynamicOptic from path segments
        '{
          DynamicOptic.fromSegments(
            ${ Expr(segments) }
          )
        }
      case other =>
        report.errorAndAbort(
          s"Selector must be a simple lambda expression like `_.field.nested`, got: ${other.show}"
        )
    }
  }

  // ── addField ───────────────────────────────────────────────────────────────

  def addFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => V],
    default: Expr[SchemaExpr[A, V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[B](target)
    '{
      $builder.appendAction(
        AddField($optic, $default.asInstanceOf[SchemaExpr[?]])
      )
    }
  }

  // ── dropField ──────────────────────────────────────────────────────────────

  def dropFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => V],
    defaultForReverse: Expr[SchemaExpr[B, V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](source)
    '{
      $builder.appendAction(
        DropField($optic, $defaultForReverse.asInstanceOf[SchemaExpr[?]])
      )
    }
  }

  // ── renameField ────────────────────────────────────────────────────────────

  def renameFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => V],
    to: Expr[B => V]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val fromOptic = extractOptic[A](from)
    val toOptic   = extractOptic[B](to)
    // Extract just the target field name for the Rename action
    val toName = to.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(Select(_, name)))), _)) => name
      case _ =>
        report.errorAndAbort("renameField `to` selector must be a direct field access: _.fieldName")
    }
    '{
      $builder.appendAction(
        Rename($fromOptic, ${ Expr(toName) })
      )
    }
  }

  // ── transformField ─────────────────────────────────────────────────────────

  def transformFieldImpl[A: Type, B: Type, V: Type, W: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => V],
    to: Expr[B => W],
    transform: Expr[SchemaExpr[A, W]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromOptic = extractOptic[A](from)
    '{
      $builder.appendAction(
        TransformValue($fromOptic, $transform.asInstanceOf[SchemaExpr[?]])
      )
    }
  }

  // ── mandateField ───────────────────────────────────────────────────────────

  def mandateFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Option[V]],
    target: Expr[B => V],
    default: Expr[SchemaExpr[A, V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](source)
    '{
      $builder.appendAction(
        Mandate($optic, $default.asInstanceOf[SchemaExpr[?]])
      )
    }
  }

  // ── optionalizeField ───────────────────────────────────────────────────────

  def optionalizeFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => V],
    target: Expr[B => Option[V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](source)
    '{
      $builder.appendAction(Optionalize($optic))
    }
  }

  // ── changeFieldType ────────────────────────────────────────────────────────

  def changeFieldTypeImpl[A: Type, B: Type, V: Type, W: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => V],
    target: Expr[B => W],
    converter: Expr[SchemaExpr[A, W]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](source)
    '{
      $builder.appendAction(
        ChangeType($optic, $converter.asInstanceOf[SchemaExpr[?]])
      )
    }
  }

  // ── joinFields ─────────────────────────────────────────────────────────────

  def joinFieldsImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    sources: Expr[List[A => Any]],
    target: Expr[B => V],
    combiner: Expr[SchemaExpr[A, V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    // Extract optics for each source selector in the list literal
    val targetOptic = extractOptic[B](target)
    // For sources we require a literal List(...) at compile time
    val sourceOptics = sources.asTerm match {
      case Inlined(_, _, Apply(_, args)) =>
        args.map { arg =>
          extractOptic[A](arg.asExprOf[A => Any])
        }
      case _ =>
        report.errorAndAbort("`sources` must be a literal List of lambda selectors")
    }
    val opticVector = Expr.ofList(sourceOptics)
    '{
      $builder.appendAction(
        Join(
          $targetOptic,
          $opticVector.toVector,
          $combiner.asInstanceOf[SchemaExpr[?]]
        )
      )
    }
  }

  // ── splitField ─────────────────────────────────────────────────────────────

  def splitFieldImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => V],
    targets: Expr[List[B => Any]],
    splitter: Expr[SchemaExpr[A, List[Any]]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val sourceOptic = extractOptic[A](source)
    val targetOptics = targets.asTerm match {
      case Inlined(_, _, Apply(_, args)) =>
        args.map { arg =>
          extractOptic[B](arg.asExprOf[B => Any])
        }
      case _ =>
        report.errorAndAbort("`targets` must be a literal List of lambda selectors")
    }
    val opticVector = Expr.ofList(targetOptics)
    '{
      $builder.appendAction(
        Split(
          $sourceOptic,
          $opticVector.toVector,
          $splitter.asInstanceOf[SchemaExpr[?]]
        )
      )
    }
  }

  // ── renameCase ─────────────────────────────────────────────────────────────

  def renameCaseImpl[A: Type, B: Type, SumA: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => SumA],
    from: Expr[String],
    to: Expr[String]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](at)
    '{
      $builder.appendAction(RenameCase($optic, $from, $to))
    }
  }

  // ── transformCase ──────────────────────────────────────────────────────────

  def transformCaseImpl[A: Type, B: Type, CaseA: Type, CaseB: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => CaseA],
    caseMigration: Expr[MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]],
    caseSourceSchema: Expr[Schema[CaseA]],
    caseTargetSchema: Expr[Schema[CaseB]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](at)
    '{
      val caseBuilder = MigrationBuilder($caseSourceSchema, $caseTargetSchema, Vector.empty)
      val builtCase   = $caseMigration(caseBuilder)
      $builder.appendAction(TransformCase($optic, builtCase.actions))
    }
  }

  // ── transformElements ──────────────────────────────────────────────────────

  def transformElementsImpl[A: Type, B: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Seq[V]],
    transform: Expr[SchemaExpr[A, V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](at)
    '{
      $builder.appendAction(
        TransformElements($optic, $transform.asInstanceOf[SchemaExpr[?]])
      )
    }
  }

  // ── transformKeys ──────────────────────────────────────────────────────────

  def transformKeysImpl[A: Type, B: Type, K: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Map[K, V]],
    transform: Expr[SchemaExpr[A, K]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](at)
    '{
      $builder.appendAction(
        TransformKeys($optic, $transform.asInstanceOf[SchemaExpr[?]])
      )
    }
  }

  // ── transformValues ────────────────────────────────────────────────────────

  def transformValuesImpl[A: Type, B: Type, K: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Map[K, V]],
    transform: Expr[SchemaExpr[A, V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic[A](at)
    '{
      $builder.appendAction(
        TransformValues($optic, $transform.asInstanceOf[SchemaExpr[?]])
      )
    }
  }
}
