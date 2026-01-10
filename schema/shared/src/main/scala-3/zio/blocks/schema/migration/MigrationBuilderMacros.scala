package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema._

/**
 * Macros for the MigrationBuilder DSL.
 *
 * These macros extract field names from selector functions at compile time,
 * providing type-safe migration construction with IDE support.
 */
private[migration] object MigrationBuilderMacros {

  /**
   * Extract a field name from a selector function like `_.fieldName`.
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

    val oldName = extractFieldName(oldField.asTerm) match {
      case Right(name) => name
      case Left(err)   => report.errorAndAbort(err)
    }

    val newName = extractFieldName(newField.asTerm) match {
      case Right(name) => name
      case Left(err)   => report.errorAndAbort(err)
    }

    validateRename[A, B](oldName, newName)

    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Rename(DynamicOptic.root.field(${ Expr(oldName) }), ${ Expr(newName) })
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

    val fieldName = extractFieldName(field.asTerm) match {
      case Right(name) => name
      case Left(err)   => report.errorAndAbort(err)
    }

    validateFieldExists[A](fieldName)

    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.DropField(DynamicOptic.root.field(${ Expr(fieldName) }))
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

    val fieldName = extractFieldName(field.asTerm) match {
      case Right(name) => name
      case Left(err)   => report.errorAndAbort(err)
    }

    validateFieldExists[A](fieldName)

    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Optionalize(DynamicOptic.root.field(${ Expr(fieldName) }))
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
}
