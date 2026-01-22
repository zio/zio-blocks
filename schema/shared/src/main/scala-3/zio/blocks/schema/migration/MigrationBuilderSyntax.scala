package zio.blocks.schema.migration

import scala.quoted._
import zio.blocks.schema.DynamicOptic

/**
 * Scala 3 extension methods for MigrationBuilder that use macros to extract
 * field names and paths from lambda expressions.
 *
 * This provides a type-safe, IDE-friendly API: builder.addField(_.country,
 * "USA") builder.renameField(_.name, _.fullName) builder.dropField(_.oldField)
 * builder.atPath(_.address).addField(_.zip, "00000")
 */
extension [A, B](builder: MigrationBuilder[A, B]) {

  /**
   * Add a field using a selector and string default value.
   *
   * Example: builder.addField(_.country, "USA")
   */
  inline def addField[F](inline selector: B => F, defaultValue: String): MigrationBuilder[A, B] =
    ${ addFieldStringImpl('builder, 'selector, 'defaultValue) }

  /**
   * Add a field using a selector and int default value.
   *
   * Example: builder.addField(_.age, 0)
   */
  inline def addField[F](inline selector: B => F, defaultValue: Int): MigrationBuilder[A, B] =
    ${ addFieldIntImpl('builder, 'selector, 'defaultValue) }

  /**
   * Add a field using a selector and boolean default value.
   *
   * Example: builder.addField(_.active, true)
   */
  inline def addField[F](inline selector: B => F, defaultValue: Boolean): MigrationBuilder[A, B] =
    ${ addFieldBoolImpl('builder, 'selector, 'defaultValue) }

  /**
   * Drop a field using a selector.
   *
   * Example: builder.dropField(_.oldField)
   */
  inline def dropField[F](inline selector: A => F): MigrationBuilder[A, B] =
    ${ dropFieldImpl('builder, 'selector) }

  /**
   * Rename a field using two selectors.
   *
   * Example: builder.renameField(_.name, _.fullName)
   */
  inline def renameField[F1, F2](inline from: A => F1, inline to: B => F2): MigrationBuilder[A, B] =
    ${ renameFieldImpl('builder, 'from, 'to) }

  /**
   * Make an optional field mandatory with a default value.
   *
   * Example: builder.mandateField(_.email, "default@example.com")
   */
  inline def mandateField[F](inline selector: A => F, defaultValue: String): MigrationBuilder[A, B] =
    ${ mandateFieldImpl('builder, 'selector, 'defaultValue) }

  /**
   * Make a mandatory field optional.
   *
   * Example: builder.optionalizeField(_.middleName)
   */
  inline def optionalizeField[F](inline selector: A => F): MigrationBuilder[A, B] =
    ${ optionalizeFieldImpl('builder, 'selector) }

  /**
   * Navigate to a nested path and apply operations there.
   *
   * Example: builder.atPath(_.address).addField(_.zip, "00000")
   */
  inline def atPath[F](inline selector: A => F): MigrationBuilderAtPath[A, B] =
    ${ atPathImpl('builder, 'selector) }
}

/**
 * A builder scoped to a specific path for nested operations.
 */
final class MigrationBuilderAtPath[A, B](
  val builder: MigrationBuilder[A, B],
  val path: DynamicOptic
) {

  /**
   * Add a field at the current path.
   */
  def addField(fieldName: String, defaultValue: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.AddField(
      path,
      fieldName,
      SchemaExpr.literalString(defaultValue)
    )
    new MigrationBuilder(builder.sourceSchema, builder.targetSchema, builder.actions :+ action)
  }

  /**
   * Drop a field at the current path.
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.DropField(
      path,
      fieldName,
      None
    )
    new MigrationBuilder(builder.sourceSchema, builder.targetSchema, builder.actions :+ action)
  }

  /**
   * Rename a field at the current path.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.Rename(
      path,
      from,
      to
    )
    new MigrationBuilder(builder.sourceSchema, builder.targetSchema, builder.actions :+ action)
  }
}

// ===== Macro Implementations =====

private def addFieldStringImpl[A: Type, B: Type, F: Type](
  builder: Expr[MigrationBuilder[A, B]],
  selector: Expr[B => F],
  defaultValue: Expr[String]
)(using Quotes): Expr[MigrationBuilder[A, B]] = {
  val fieldName = MigrationBuilderMacros.extractFieldName(selector)
  '{
    val action = MigrationAction.AddField(
      DynamicOptic.root,
      $fieldName,
      SchemaExpr.literalString($defaultValue)
    )
    new MigrationBuilder($builder.sourceSchema, $builder.targetSchema, $builder.actions :+ action)
  }
}

private def addFieldIntImpl[A: Type, B: Type, F: Type](
  builder: Expr[MigrationBuilder[A, B]],
  selector: Expr[B => F],
  defaultValue: Expr[Int]
)(using Quotes): Expr[MigrationBuilder[A, B]] = {
  val fieldName = MigrationBuilderMacros.extractFieldName(selector)
  '{
    val action = MigrationAction.AddField(
      DynamicOptic.root,
      $fieldName,
      SchemaExpr.literalInt($defaultValue)
    )
    new MigrationBuilder($builder.sourceSchema, $builder.targetSchema, $builder.actions :+ action)
  }
}

private def addFieldBoolImpl[A: Type, B: Type, F: Type](
  builder: Expr[MigrationBuilder[A, B]],
  selector: Expr[B => F],
  defaultValue: Expr[Boolean]
)(using Quotes): Expr[MigrationBuilder[A, B]] = {
  val fieldName = MigrationBuilderMacros.extractFieldName(selector)
  '{
    val action = MigrationAction.AddField(
      DynamicOptic.root,
      $fieldName,
      SchemaExpr.literalBool($defaultValue)
    )
    new MigrationBuilder($builder.sourceSchema, $builder.targetSchema, $builder.actions :+ action)
  }
}

private def dropFieldImpl[A: Type, B: Type, F: Type](
  builder: Expr[MigrationBuilder[A, B]],
  selector: Expr[A => F]
)(using Quotes): Expr[MigrationBuilder[A, B]] = {
  val fieldName = MigrationBuilderMacros.extractFieldName(selector)
  '{
    val action = MigrationAction.DropField(
      DynamicOptic.root,
      $fieldName,
      None
    )
    new MigrationBuilder($builder.sourceSchema, $builder.targetSchema, $builder.actions :+ action)
  }
}

private def renameFieldImpl[A: Type, B: Type, F1: Type, F2: Type](
  builder: Expr[MigrationBuilder[A, B]],
  from: Expr[A => F1],
  to: Expr[B => F2]
)(using Quotes): Expr[MigrationBuilder[A, B]] = {
  val fromName = MigrationBuilderMacros.extractFieldName(from)
  val toName   = MigrationBuilderMacros.extractFieldName(to)
  '{
    val action = MigrationAction.Rename(
      DynamicOptic.root,
      $fromName,
      $toName
    )
    new MigrationBuilder($builder.sourceSchema, $builder.targetSchema, $builder.actions :+ action)
  }
}

private def mandateFieldImpl[A: Type, B: Type, F: Type](
  builder: Expr[MigrationBuilder[A, B]],
  selector: Expr[A => F],
  defaultValue: Expr[String]
)(using Quotes): Expr[MigrationBuilder[A, B]] = {
  val fieldName = MigrationBuilderMacros.extractFieldName(selector)
  '{
    val action = MigrationAction.Mandate(
      DynamicOptic.root,
      $fieldName,
      SchemaExpr.literalString($defaultValue)
    )
    new MigrationBuilder($builder.sourceSchema, $builder.targetSchema, $builder.actions :+ action)
  }
}

private def optionalizeFieldImpl[A: Type, B: Type, F: Type](
  builder: Expr[MigrationBuilder[A, B]],
  selector: Expr[A => F]
)(using Quotes): Expr[MigrationBuilder[A, B]] = {
  val fieldName = MigrationBuilderMacros.extractFieldName(selector)
  '{
    val action = MigrationAction.Optionalize(
      DynamicOptic.root,
      $fieldName
    )
    new MigrationBuilder($builder.sourceSchema, $builder.targetSchema, $builder.actions :+ action)
  }
}

private def atPathImpl[A: Type, B: Type, F: Type](
  builder: Expr[MigrationBuilder[A, B]],
  selector: Expr[A => F]
)(using Quotes): Expr[MigrationBuilderAtPath[A, B]] = {
  val path = MigrationBuilderMacros.extractPath(selector)
  '{
    new MigrationBuilderAtPath($builder, $path)
  }
}

/**
 * Compatibility object for Scala 2/3 cross-compilation.
 * In Scala 2, this object contains the implicit class.
 * In Scala 3, this object is empty but allows `import MigrationBuilderSyntax._` to work.
 */
object MigrationBuilderSyntax
