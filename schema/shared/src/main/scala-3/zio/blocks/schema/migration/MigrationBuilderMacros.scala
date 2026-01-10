package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema._
import zio.blocks.schema.binding._
import MigrationAction.DynamicOpticOps

/**
 * Macros for the MigrationBuilder DSL.
 *
 * These macros extract field names from selector functions at compile time,
 * providing type-safe migration construction with IDE support.
 */
private[migration] object MigrationBuilderMacros {

  /**
   * Extract a DynamicOptic from a selector function.
   *
   * Supports complex patterns:
   *   - Simple field: `_.fieldName` -> `.field("fieldName")`
   *   - Nested field: `_.address.street` -> `.field("address").field("street")`
   *   - Collection: `_.items.each` -> `.field("items").elements`
   *   - Enum case: `_.status.when[Active]` ->
   *     `.field("status").caseOf("Active")`
   */
  def extractDynamicOptic(using
    Quotes
  )(
    selector: quotes.reflect.Term
  ): Either[String, Expr[DynamicOptic]] = {
    import quotes.reflect.*

    def loop(term: Term, acc: Expr[DynamicOptic]): Either[String, Expr[DynamicOptic]] = term match {
      // Base case: identity (end of selector chain)
      case Ident(_) => Right(acc)

      // Field access: _.fieldName or _.a.b.c
      case Select(inner, fieldName) if !Set("each", "eachKey", "eachValue").contains(fieldName) =>
        loop(inner, '{ $acc.field(${ Expr(fieldName) }) })

      // Collection traversal: _.list.each
      case Apply(Select(inner, "each"), Nil) =>
        loop(inner, '{ $acc.elements })

      // Standalone .each on Select (no explicit Apply)
      case Select(inner, "each") =>
        loop(inner, '{ $acc.elements })

      // Map key traversal: _.map.eachKey
      case Apply(Select(inner, "eachKey"), Nil) =>
        loop(inner, '{ $acc.mapKeys })

      case Select(inner, "eachKey") =>
        loop(inner, '{ $acc.mapKeys })

      // Map value traversal: _.map.eachValue
      case Apply(Select(inner, "eachValue"), Nil) =>
        loop(inner, '{ $acc.mapValues })

      case Select(inner, "eachValue") =>
        loop(inner, '{ $acc.mapValues })

      // Enum case selection: _.status.when[CaseName]
      case TypeApply(Select(inner, "when"), List(caseType)) =>
        val caseName = caseType.tpe.typeSymbol.name
        loop(inner, '{ $acc.caseOf(${ Expr(caseName) }) })

      // Apply without recognized method - try to extract inner
      case Apply(inner, _) =>
        loop(inner, acc)

      case other =>
        Left(s"Unsupported selector pattern: ${other.show}")
    }

    selector match {
      // Pattern: x => x.fieldName (simple lambda)
      case Lambda(List(ValDef(_, _, _)), body) =>
        loop(body, '{ DynamicOptic.root })

      // Pattern: _.fieldName (eta-expanded in block)
      case Block(List(), Lambda(List(ValDef(_, _, _)), body)) =>
        loop(body, '{ DynamicOptic.root })

      // Pattern: Inlined expression (from inline def expansion)
      case Inlined(_, _, inner) =>
        extractDynamicOptic(inner)

      // Pattern: Typed expression wrapper
      case Typed(inner, _) =>
        extractDynamicOptic(inner)

      // Pattern: Block with bindings
      case Block(bindings, expr) if bindings.nonEmpty =>
        extractDynamicOptic(expr)

      case other =>
        Left(s"Expected a selector function like '_.fieldName', got: ${other.show}")
    }
  }

  /**
   * Extract just the last field name from a selector (for rename target).
   */
  def extractLastFieldName(using
    Quotes
  )(
    selector: quotes.reflect.Term
  ): Either[String, String] = {
    import quotes.reflect.*

    def findLastField(term: Term): Either[String, String] = term match {
      case Select(_, fieldName) if !Set("each", "eachKey", "eachValue", "when").contains(fieldName) =>
        Right(fieldName)
      case Lambda(_, body) => findLastField(body)
      case Block(_, expr)  => findLastField(expr)
      case _               => Left(s"Could not extract field name from: ${term.show}")
    }

    findLastField(selector)
  }

  /**
   * Legacy: Extract a simple field name (for backward compatibility).
   */
  def extractFieldName(using
    Quotes
  )(
    selector: quotes.reflect.Term
  ): Either[String, String] = {
    import quotes.reflect.*

    selector match {
      // Pattern: x => x.fieldName
      case Lambda(List(ValDef(_, _, _)), Select(Ident(_), fieldName)) =>
        Right(fieldName)

      // Pattern: _.fieldName (eta-expanded)
      case Block(List(), Lambda(List(ValDef(_, _, _)), Select(Ident(_), fieldName))) =>
        Right(fieldName)

      // Pattern: already a Select
      case Select(_, fieldName) =>
        Right(fieldName)

      case other =>
        Left(s"Expected a field selector like '_.fieldName', got: ${other.show}")
    }
  }

  def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    oldField: Expr[A => Any],
    newField: Expr[B => Any],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val oldOptic = extractDynamicOptic(oldField.asTerm) match {
      case Right(optic) => optic
      case Left(err)    => report.errorAndAbort(err)
    }

    val newName = extractLastFieldName(newField.asTerm) match {
      case Right(name) => name
      case Left(err)   => report.errorAndAbort(err)
    }

    // Validate if we can extract a simple field name for validation
    extractFieldName(oldField.asTerm).foreach(validateFieldExistsIfPossible[A])

    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Rename($oldOptic, ${ Expr(newName) })
      )($fromSchema, $toSchema)
    }
  }

  def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }

    // Validate if we can extract a simple field name for validation
    extractFieldName(field.asTerm).foreach(validateFieldExistsIfPossible[A])

    '{
      val fullDefaultOpt = $fromSchema.getDefaultValue
      val default        = fullDefaultOpt.flatMap { fullDefault =>
        val dv = $fromSchema.reflect.toDynamicValue(fullDefault)(Binding.bindingHasBinding)
        DynamicOpticOps($optic).getDV(dv).toOption
      }.orElse {
        $fromSchema
          .get($optic)
          .flatMap(r =>
            r.getDefaultValue.map(v => r.asInstanceOf[Reflect.Bound[Any]].toDynamicValue(v)(Binding.bindingHasBinding))
          )
      }

      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.DropField($optic, default)
      )($fromSchema, $toSchema)
    }
  }

  def optionalizeImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }

    // Validate if we can extract a simple field name for validation
    extractFieldName(field.asTerm).foreach(validateFieldExistsIfPossible[A])

    '{
      val default = $fromSchema.getDefaultValue.flatMap { fullDefault =>
        $optic.getDV($fromSchema.reflect.toDynamicValue(fullDefault)).toOption
      }.orElse {
        $fromSchema
          .get($optic)
          .flatMap(r => r.getDefaultValue.map(v => r.asInstanceOf[Reflect.Bound[Any]].toDynamicValue(v)))
      }
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Optionalize($optic, default)
      )($fromSchema, $toSchema)
    }
  }

  def addFieldImpl[A: Type, B: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    defaultValue: Expr[T],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]],
    defaultSchema: Expr[Schema[T]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val optic = extractDynamicOptic(target.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }

    '{
      val dv = $defaultSchema.toDynamicValue($defaultValue)
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.AddField($optic, dv)
      )($fromSchema, $toSchema)
    }
  }

  def mandateImpl[A: Type, B: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    defaultValue: Expr[T],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]],
    defaultSchema: Expr[Schema[T]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }

    '{
      val dv = $defaultSchema.toDynamicValue($defaultValue)
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Mandate($optic, dv)
      )($fromSchema, $toSchema)
    }
  }

  def transformFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.TransformValue($optic, $transform)
      )($fromSchema, $toSchema)
    }
  }

  def changeFieldTypeImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    converter: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.ChangeType($optic, $converter)
      )($fromSchema, $toSchema)
    }
  }

  def transformElementsImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.TransformElements($optic, $transform)
      )($fromSchema, $toSchema)
    }
  }

  def transformKeysImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.TransformKeys($optic, $transform)
      )($fromSchema, $toSchema)
    }
  }

  def transformValuesImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    field: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val optic = extractDynamicOptic(field.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.TransformValues($optic, $transform)
      )($fromSchema, $toSchema)
    }
  }

  def transformCaseImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    def splitOptic(term: Term): (Expr[DynamicOptic], String) =
      term match {
        case TypeApply(Select(inner, "when"), List(caseType)) =>
          val caseName                        = caseType.tpe.typeSymbol.name
          val parentOptic: Expr[DynamicOptic] = extractDynamicOptic(inner) match {
            case Right(o)  => o
            case Left(err) => report.errorAndAbort(err)
          }
          (parentOptic, caseName)
        case Inlined(_, _, inner) => splitOptic(inner)
        case Lambda(_, body)      => splitOptic(body)
        case Block(_, expr)       => splitOptic(expr)
        case Typed(inner, _)      => splitOptic(inner)
        case _                    =>
          report.errorAndAbort(s"transformCase requires a .when[Case] selector, got: ${term.show}")
      }

    val (atOptic, caseName) = splitOptic(selector.asTerm)

    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.TransformCase($atOptic, ${ Expr(caseName) }, $transform)
      )($fromSchema, $toSchema)
    }
  }

  def joinImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    combiner: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    sources: Expr[Seq[A => Any]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val targetOptic: Expr[DynamicOptic] = extractDynamicOptic(target.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }

    val Varargs(sourceExprs)                  = sources: @unchecked
    val sourceOptics: Seq[Expr[DynamicOptic]] = sourceExprs.map { s =>
      extractDynamicOptic(s.asTerm) match {
        case Right(o)  => o
        case Left(err) => report.errorAndAbort(err)
      }
    }

    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Join($targetOptic, Vector(${ Varargs(sourceOptics) }: _*), $combiner)
      )($fromSchema, $toSchema)
    }
  }

  def splitImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    splitter: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    targets: Expr[Seq[B => Any]],
    fromSchema: Expr[Schema[A]],
    toSchema: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val sourceOptic: Expr[DynamicOptic] = extractDynamicOptic(source.asTerm) match {
      case Right(o)  => o
      case Left(err) => report.errorAndAbort(err)
    }

    val Varargs(targetExprs)                  = targets: @unchecked
    val targetOptics: Seq[Expr[DynamicOptic]] = targetExprs.map { t =>
      extractDynamicOptic(t.asTerm) match {
        case Right(o)  => o
        case Left(err) => report.errorAndAbort(err)
      }
    }

    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Split($sourceOptic, Vector(${ Varargs(targetOptics) }: _*), $splitter)
      )($fromSchema, $toSchema)
    }
  }

  def validateAndBuild[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): Expr[Either[String, Migration[A, B]]] = {
    import quotes.reflect.*

    // Extract compile-time structures for source and target types
    val sourceStructure = extractStructure(TypeRepr.of[A])
    val targetStructure = extractStructure(TypeRepr.of[B])

    // Perform compile-time compatibility check
    validateStructureCompatibility(sourceStructure, targetStructure)

    // Runtime validation is still performed
    '{ $builder.validate.map(_ => $builder.buildPartial) }
  }

  /**
   * Validate that source and target structures are compatible for migration.
   */
  def validateStructureCompatibility(using
    Quotes
  )(
    source: MigrationValidator.SchemaStructure,
    target: MigrationValidator.SchemaStructure
  ): Unit = {
    import quotes.reflect.*
    import MigrationValidator.SchemaStructure._

    (source, target) match {
      case (Record(_), Record(_))                   => // Both records - compatible
      case (Variant(_), Variant(_))                 => // Both variants - compatible
      case (Primitive(s), Primitive(t)) if s == t   => // Same primitive - compatible
      case (Sequence(_), Sequence(_))               => // Both sequences - compatible
      case (MapStructure(_, _), MapStructure(_, _)) => // Both maps - compatible
      case (AnyValue, _) | (_, AnyValue)            => // AnyValue is wildcard - compatible
      case (s, t)                                   =>
        report.error(
          s"Incompatible structure types for migration: source is ${structureName(s)}, target is ${structureName(t)}"
        )
    }
  }

  private def structureName(s: MigrationValidator.SchemaStructure): String = s match {
    case MigrationValidator.SchemaStructure.Record(_)          => "Record"
    case MigrationValidator.SchemaStructure.Variant(_)         => "Variant"
    case MigrationValidator.SchemaStructure.Primitive(t)       => s"Primitive($t)"
    case MigrationValidator.SchemaStructure.Sequence(_)        => "Sequence"
    case MigrationValidator.SchemaStructure.MapStructure(_, _) => "Map"
    case MigrationValidator.SchemaStructure.AnyValue           => "AnyValue"
  }

  def extractStructure(using Quotes)(tpe: quotes.reflect.TypeRepr): MigrationValidator.SchemaStructure = {
    import quotes.reflect.*
    if (tpe <:< TypeRepr.of[Int]) MigrationValidator.SchemaStructure.Primitive("Int")
    else if (tpe <:< TypeRepr.of[String]) MigrationValidator.SchemaStructure.Primitive("String")
    else if (tpe.typeSymbol.isNoSymbol) MigrationValidator.SchemaStructure.AnyValue
    else if (tpe.typeSymbol.isClassDef) {
      val fields = tpe.typeSymbol.caseFields.map { field =>
        field.name -> extractStructure(tpe.memberType(field))
      }.toMap
      if (fields.nonEmpty) MigrationValidator.SchemaStructure.Record(fields)
      else MigrationValidator.SchemaStructure.AnyValue
    } else MigrationValidator.SchemaStructure.AnyValue
  }

  def validateRename[A: Type, B: Type](oldName: String, newName: String)(using Quotes): Unit =
    validateFieldExists[A](oldName)

  def validateFieldExists[T: Type](fieldName: String)(using Quotes): Unit = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    if (tpe.typeSymbol.isClassDef) {
      val fields = tpe.typeSymbol.caseFields.map(_.name)
      if (fields.nonEmpty && !fields.contains(fieldName)) {
        report.error(s"Field '$fieldName' not found in type ${tpe.show}. Valid fields: ${fields.mkString(", ")}")
      }
    }
  }

  /**
   * Validate field exists if possible - used for complex selectors where we
   * only validate if we can extract a simple field name.
   */
  def validateFieldExistsIfPossible[T: Type](fieldName: String)(using Quotes): Unit =
    // Only validate for simple field access patterns
    validateFieldExists[T](fieldName)
}
