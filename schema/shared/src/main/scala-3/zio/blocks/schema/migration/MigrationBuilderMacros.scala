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
  def extractFieldName(using Quotes)(
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
      case Left(err) => report.errorAndAbort(err)
    }
    
    val newName = extractFieldName(newField.asTerm) match {
      case Right(name) => name
      case Left(err) => report.errorAndAbort(err)
    }
    
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.RenameField(${Expr(oldName)}, ${Expr(newName)})
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
      case Left(err) => report.errorAndAbort(err)
    }
    
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.DropField(${Expr(fieldName)})
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
      case Left(err) => report.errorAndAbort(err)
    }
    
    '{
      MigrationBuilder[A, B](
        $builder.actions :+ MigrationAction.Optionalize(${Expr(fieldName)})
      )($fromSchema, $toSchema)
    }
  }
}
