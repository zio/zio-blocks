/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}

/**
 * A type-safe migration builder that tracks field handling at compile time.
 *
 * This builder uses phantom type parameters to track which source fields still
 * need to be handled and which target fields still need to be provided. The
 * `build` method can only be called when both type parameters are EmptyTuple,
 * ensuring at compile time that all fields are properly addressed.
 *
 * @tparam A The source type
 * @tparam B The target type
 * @tparam SrcRemaining Tuple of source field names that haven't been handled yet
 * @tparam TgtRemaining Tuple of target field names that haven't been provided yet
 *
 * Example:
 * {{{
 * case class PersonV1(name: String, age: Int)
 * case class PersonV2(fullName: String, age: Int, email: String)
 *
 * val migration = TypedMigrationBuilder[PersonV1, PersonV2]
 *   .renameField(select[PersonV1](_.name), select[PersonV2](_.fullName))
 *   .keepField(select[PersonV1](_.age), select[PersonV2](_.age))
 *   .addField(select[PersonV2](_.email), "unknown@example.com")
 *   .build  // Compiles only if all fields are handled
 * }}}
 */
final class TypedMigrationBuilder[A, B, SrcRemaining <: Tuple, TgtRemaining <: Tuple](
  private[migration] val sourceSchema: Schema[A],
  private[migration] val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  // ===========================================================================
  // Field Rename Operations
  // ===========================================================================

  /**
   * Rename a field from source to target.
   *
   * This removes the source field name from SrcRemaining and the target field
   * name from TgtRemaining, ensuring both are tracked.
   */
  def renameField[F, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F, SrcName],
    to: FieldSelector[B, F, TgtName]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, SrcName],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, TgtName],
    srcRemove: FieldSet.RemoveField[SrcRemaining, SrcName],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, TgtName]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(from.fieldName, to.fieldName)
    )

  // ===========================================================================
  // Keep Field Operations
  // ===========================================================================

  /**
   * Keep a field unchanged (same name in source and target).
   *
   * This marks both the source and target field as handled.
   */
  @SuppressWarnings(Array("unused"))
  def keepField[F, Name <: String](
    from: FieldSelector[A, F, Name],
    @annotation.unused to: FieldSelector[B, F, Name]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.KeepField(from.fieldName)
    )

  /**
   * Keep a field unchanged using just the source selector (infers target has same name).
   */
  def keepField[F, Name <: String](
    field: FieldSelector[A, F, Name]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.KeepField(field.fieldName)
    )

  // ===========================================================================
  // Add Field Operations
  // ===========================================================================

  /**
   * Add a new field to the target with a default value.
   */
  def addField[F, Name <: String](
    field: FieldSelector[B, F, Name],
    default: ResolvedExpr
  )(using
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name]
  ): TypedMigrationBuilder[A, B, SrcRemaining, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, SrcRemaining, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AddField(field.fieldName, default)
    )

  /**
   * Add a new field with a literal string default.
   */
  def addFieldString[Name <: String](
    field: FieldSelector[B, String, Name],
    default: String
  )(using
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name]
  ): TypedMigrationBuilder[A, B, SrcRemaining, tgtRemove.Out] =
    addField(field, ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(default))))

  /**
   * Add a new field with a literal int default.
   */
  def addFieldInt[Name <: String](
    field: FieldSelector[B, Int, Name],
    default: Int
  )(using
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name]
  ): TypedMigrationBuilder[A, B, SrcRemaining, tgtRemove.Out] =
    addField(field, ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(default))))

  /**
   * Add a new field with a literal boolean default.
   */
  def addFieldBoolean[Name <: String](
    field: FieldSelector[B, Boolean, Name],
    default: Boolean
  )(using
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name]
  ): TypedMigrationBuilder[A, B, SrcRemaining, tgtRemove.Out] =
    addField(field, ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(default))))

  // ===========================================================================
  // Drop Field Operations
  // ===========================================================================

  /**
   * Drop a field from the source (not needed in target).
   */
  def dropField[F, Name <: String](
    field: FieldSelector[A, F, Name]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, TgtRemaining] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, TgtRemaining](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(field.fieldName, None)
    )

  /**
   * Drop a field with a default value for reverse migration.
   */
  def dropField[F, Name <: String](
    field: FieldSelector[A, F, Name],
    defaultForReverse: ResolvedExpr
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, TgtRemaining] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, TgtRemaining](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(field.fieldName, Some(defaultForReverse))
    )

  // ===========================================================================
  // Transform Field Operations
  // ===========================================================================

  /**
   * Transform a field's value using a ResolvedExpr.
   */
  def transformField[F, G, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F, SrcName],
    to: FieldSelector[B, G, TgtName],
    transform: ResolvedExpr
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, SrcName],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, TgtName],
    srcRemove: FieldSet.RemoveField[SrcRemaining, SrcName],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, TgtName]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(from.fieldName, to.fieldName) :+
        MigrationAction.TransformField(to.fieldName, transform, None)
    )

  /**
   * Transform a field with explicit reverse transform.
   */
  def transformField[F, G, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F, SrcName],
    to: FieldSelector[B, G, TgtName],
    transform: ResolvedExpr,
    reverseTransform: ResolvedExpr
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, SrcName],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, TgtName],
    srcRemove: FieldSet.RemoveField[SrcRemaining, SrcName],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, TgtName]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(from.fieldName, to.fieldName) :+
        MigrationAction.TransformField(to.fieldName, transform, Some(reverseTransform))
    )

  // ===========================================================================
  // Nested Migration Operations - THE KEY DIFFERENTIATOR FROM PR #882
  // ===========================================================================

  /**
   * Apply nested actions to a field where source and target have the same nested type.
   *
   * The nested builder uses the string-based MigrationBuilder API for flexibility,
   * while the outer builder tracks field handling at compile time.
   *
   * Example:
   * {{{
   *   builder.atField(select[Person](_.address))(
   *     _.renameField("street", "streetName")
   *      .addFieldString("zipCode", "00000")
   *   )
   * }}}
   */
  def atField[F, Name <: String](
    field: FieldSelector[A, F, Name]
  )(
    nested: MigrationBuilder[F, F] => MigrationBuilder[F, F]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name],
    fieldSchema: Schema[F]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val nestedBuilder = nested(MigrationBuilder(fieldSchema, fieldSchema, Vector.empty))
    val nestedActions = nestedBuilder.actions
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AtField(field.fieldName, nestedActions)
    )
  }

  /**
   * Apply nested actions when source and target fields have DIFFERENT types.
   *
   * This is the KEY feature that PR #882 was missing - proper nested migration
   * support between different schema versions.
   *
   * Example:
   * {{{
   *   // Person has address: AddressV1, but target has address: AddressV2
   *   builder.atFieldTransform(
   *     select[PersonV1](_.address),
   *     select[PersonV2](_.address)
   *   )(nestedBuilder =>
   *     nestedBuilder
   *       .renameField("street", "streetName")
   *       .addFieldString("zipCode", "00000")
   *   )
   * }}}
   */
  def atFieldTransform[F1, F2, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F1, SrcName],
    to: FieldSelector[B, F2, TgtName]
  )(
    nested: MigrationBuilder[F1, F2] => MigrationBuilder[F1, F2]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, SrcName],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, TgtName],
    srcRemove: FieldSet.RemoveField[SrcRemaining, SrcName],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, TgtName],
    srcFieldSchema: Schema[F1],
    tgtFieldSchema: Schema[F2]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val nestedBuilder = nested(MigrationBuilder(srcFieldSchema, tgtFieldSchema, Vector.empty))
    val nestedActions = nestedBuilder.actions

    // If field names differ, add a rename action first
    val allActions = if (from.fieldName != to.fieldName) {
      actions :+ MigrationAction.RenameField(from.fieldName, to.fieldName) :+
        MigrationAction.AtField(to.fieldName, nestedActions)
    } else {
      actions :+ MigrationAction.AtField(from.fieldName, nestedActions)
    }

    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      allActions
    )
  }

  /**
   * Apply FULLY TYPE-SAFE nested actions with compile-time field tracking.
   *
   * This macro-based method creates a nested TypedMigrationBuilder with proper
   * type-level field tracking. Both the outer and nested builders track
   * fields at compile time.
   *
   * Example:
   * {{{
   *   builder.atFieldTyped(select[Person](_.address))(nestedBuilder =>
   *     nestedBuilder
   *       .renameField(select(_.street), select(_.streetName))
   *       .keepField(select(_.city))
   *       .build
   *   )
   * }}}
   */
  inline def atFieldTyped[F, Name <: String](
    field: FieldSelector[A, F, Name]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name],
    fieldSchema: Schema[F]
  ): AtFieldTypedBuilder[A, B, F, F, srcRemove.Out, tgtRemove.Out] =
    new AtFieldTypedBuilder[A, B, F, F, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions,
      field.fieldName,
      fieldSchema,
      fieldSchema
    )

  /**
   * Apply FULLY TYPE-SAFE nested actions with compile-time field tracking
   * when source and target nested types differ.
   */
  inline def atFieldTypedTransform[F1, F2, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F1, SrcName],
    to: FieldSelector[B, F2, TgtName]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, SrcName],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, TgtName],
    srcRemove: FieldSet.RemoveField[SrcRemaining, SrcName],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, TgtName],
    srcFieldSchema: Schema[F1],
    tgtFieldSchema: Schema[F2]
  ): AtFieldTypedBuilder[A, B, F1, F2, srcRemove.Out, tgtRemove.Out] = {
    val targetFieldName = if (from.fieldName != to.fieldName) {
      // Add rename action
      to.fieldName
    } else from.fieldName

    val actionsWithRename = if (from.fieldName != to.fieldName) {
      actions :+ MigrationAction.RenameField(from.fieldName, to.fieldName)
    } else actions

    new AtFieldTypedBuilder[A, B, F1, F2, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actionsWithRename,
      targetFieldName,
      srcFieldSchema,
      tgtFieldSchema
    )
  }

  /**
   * Apply nested actions to all elements of a sequence field.
   */
  def atElements[E, Name <: String](
    field: FieldSelector[A, Seq[E], Name]
  )(
    nested: MigrationBuilder[E, E] => MigrationBuilder[E, E]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name],
    elemSchema: Schema[E]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val nestedBuilder = nested(MigrationBuilder(elemSchema, elemSchema, Vector.empty))
    val nestedActions = nestedBuilder.actions
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AtField(field.fieldName, Vector(MigrationAction.AtElements(nestedActions)))
    )
  }

  /**
   * Apply nested actions to map values.
   */
  def atMapValues[K, V, Name <: String](
    field: FieldSelector[A, Map[K, V], Name]
  )(
    nested: MigrationBuilder[V, V] => MigrationBuilder[V, V]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, Name],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, Name],
    srcRemove: FieldSet.RemoveField[SrcRemaining, Name],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, Name],
    valueSchema: Schema[V]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] = {
    val nestedBuilder = nested(MigrationBuilder(valueSchema, valueSchema, Vector.empty))
    val nestedActions = nestedBuilder.actions
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.AtField(field.fieldName, Vector(MigrationAction.AtMapValues(nestedActions)))
    )
  }

  // ===========================================================================
  // Optional Field Operations
  // ===========================================================================

  /**
   * Make an optional field mandatory.
   */
  def mandateField[F, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, Option[F], SrcName],
    to: FieldSelector[B, F, TgtName],
    default: ResolvedExpr
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, SrcName],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, TgtName],
    srcRemove: FieldSet.RemoveField[SrcRemaining, SrcName],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, TgtName]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(from.fieldName, to.fieldName) :+
        MigrationAction.MandateField(to.fieldName, default)
    )

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField[F, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F, SrcName],
    to: FieldSelector[B, Option[F], TgtName]
  )(using
    srcContains: FieldSet.ContainsEvidence[SrcRemaining, SrcName],
    tgtContains: FieldSet.ContainsEvidence[TgtRemaining, TgtName],
    srcRemove: FieldSet.RemoveField[SrcRemaining, SrcName],
    tgtRemove: FieldSet.RemoveField[TgtRemaining, TgtName]
  ): TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out] =
    new TypedMigrationBuilder[A, B, srcRemove.Out, tgtRemove.Out](
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameField(from.fieldName, to.fieldName) :+
        MigrationAction.OptionalizeField(to.fieldName)
    )

  // ===========================================================================
  // Build Methods
  // ===========================================================================

  /**
   * Build the migration with compile-time validation.
   *
   * This method can only be called when:
   * - SrcRemaining is EmptyTuple (all source fields handled)
   * - TgtRemaining is EmptyTuple (all target fields provided)
   *
   * If any fields are not handled, this will fail to compile.
   */
  def build(using
    srcEmpty: SrcRemaining =:= EmptyTuple,
    tgtEmpty: TgtRemaining =:= EmptyTuple
  ): Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build without complete validation (for partial migrations).
   *
   * Use this when you intentionally want to create a migration that
   * doesn't handle all fields. The remaining fields must be handled
   * elsewhere or have default values.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Get the underlying DynamicMigration for introspection.
   */
  def buildDynamic: DynamicMigration =
    DynamicMigration(actions)
}

/**
 * Helper class for type-safe nested field migrations.
 *
 * This enables a fluent API for nested migrations with full compile-time
 * field tracking:
 * {{{
 *   builder.atFieldTyped(select(_.address))
 *     .using(nestedBuilder => nestedBuilder.renameField(...).build)
 * }}}
 */
final class AtFieldTypedBuilder[A, B, F1, F2, OutSrc <: Tuple, OutTgt <: Tuple](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val parentActions: Vector[MigrationAction],
  val fieldName: String,
  val srcFieldSchema: Schema[F1],
  val tgtFieldSchema: Schema[F2]
) {

  /**
   * Apply the nested migration function and complete the nested operation.
   *
   * The nested function receives a TypedMigrationBuilder for the field type
   * and must return a Migration (by calling .build or .buildPartial).
   */
  transparent inline def using(
    nested: Any => Migration[F1, F2]
  ): TypedMigrationBuilder[A, B, OutSrc, OutTgt] =
    ${ AtFieldTypedBuilder.usingImpl[A, B, F1, F2, OutSrc, OutTgt](
      'this,
      'nested,
      'srcFieldSchema,
      'tgtFieldSchema
    ) }

  // Allow building without nested modifications (identity nested migration)
  def identity: TypedMigrationBuilder[A, B, OutSrc, OutTgt] =
    new TypedMigrationBuilder[A, B, OutSrc, OutTgt](
      sourceSchema,
      targetSchema,
      parentActions :+ MigrationAction.AtField(fieldName, Vector.empty)
    )
}

object AtFieldTypedBuilder {
  import scala.quoted.*

  def usingImpl[A: Type, B: Type, F1: Type, F2: Type, OutSrc <: Tuple: Type, OutTgt <: Tuple: Type](
    self: Expr[AtFieldTypedBuilder[A, B, F1, F2, OutSrc, OutTgt]],
    nested: Expr[Any => Migration[F1, F2]],
    srcFieldSchema: Expr[Schema[F1]],
    tgtFieldSchema: Expr[Schema[F2]]
  )(using q: Quotes): Expr[TypedMigrationBuilder[A, B, OutSrc, OutTgt]] = {
    import q.reflect.*

    // Extract field names from nested types
    val f1Tpe = TypeRepr.of[F1].dealias
    val f2Tpe = TypeRepr.of[F2].dealias
    val f1Sym = f1Tpe.typeSymbol
    val f2Sym = f2Tpe.typeSymbol

    val srcFieldNames = if (f1Sym.flags.is(Flags.Case)) f1Sym.caseFields.map(_.name) else Nil
    val tgtFieldNames = if (f2Sym.flags.is(Flags.Case)) f2Sym.caseFields.map(_.name) else Nil

    val srcFieldsType = TypedMigrationBuilder.buildFieldTupleType(srcFieldNames)
    val tgtFieldsType = TypedMigrationBuilder.buildFieldTupleType(tgtFieldNames)

    (srcFieldsType.asType, tgtFieldsType.asType) match {
      case ('[nestedSrcType], '[nestedTgtType]) =>
        '{
          val builder = $self
          // Create the nested TypedMigrationBuilder with proper field tracking
          val nestedBuilder = new TypedMigrationBuilder[F1, F2, nestedSrcType & Tuple, nestedTgtType & Tuple](
            $srcFieldSchema,
            $tgtFieldSchema,
            Vector.empty
          )
          // Apply the user's function
          val migration = $nested(nestedBuilder)
          // Build the result
          new TypedMigrationBuilder[A, B, OutSrc, OutTgt](
            builder.sourceSchema,
            builder.targetSchema,
            builder.parentActions :+ MigrationAction.AtField(builder.fieldName, migration.actions)
          )
        }
    }
  }
}

object TypedMigrationBuilder {

  /**
   * Create a new TypedMigrationBuilder.
   *
   * This is a transparent inline macro that extracts the actual field tuple types
   * from the case classes A and B, enabling compile-time field tracking.
   */
  transparent inline def apply[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Any =
    ${ createBuilderImpl[A, B]('sourceSchema, 'targetSchema) }

  /**
   * Create a builder with explicit field sets (for testing or manual use).
   */
  def withFields[A, B, SrcFields <: Tuple, TgtFields <: Tuple](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): TypedMigrationBuilder[A, B, SrcFields, TgtFields] =
    new TypedMigrationBuilder[A, B, SrcFields, TgtFields](
      sourceSchema,
      targetSchema,
      Vector.empty
    )

  /**
   * Type alias for extracting field names from a type.
   * This is implemented by the macro.
   */
  type ExtractFields[A] <: Tuple

  import scala.quoted.*

  /**
   * Macro to create a TypedMigrationBuilder with properly extracted field types.
   */
  def createBuilderImpl[A: Type, B: Type](
    sourceSchema: Expr[Schema[A]],
    targetSchema: Expr[Schema[B]]
  )(using q: Quotes): Expr[Any] = {
    // Extract field names from type A
    val srcFields = extractFieldNames[A]
    val tgtFields = extractFieldNames[B]

    // Build tuple types for the field names
    val srcFieldsType = buildFieldTupleType(srcFields)
    val tgtFieldsType = buildFieldTupleType(tgtFields)

    (srcFieldsType.asType, tgtFieldsType.asType) match {
      case ('[srcType], '[tgtType]) =>
        '{
          new TypedMigrationBuilder[A, B, srcType & Tuple, tgtType & Tuple](
            $sourceSchema,
            $targetSchema,
            Vector.empty
          )
        }
    }
  }

  /**
   * Extract field names from a case class type.
   */
  private def extractFieldNames[A: Type](using q: Quotes): List[String] = {
    import q.reflect.*

    val tpe = TypeRepr.of[A].dealias
    val sym = tpe.typeSymbol

    if (sym.flags.is(Flags.Case)) {
      sym.caseFields.map(_.name)
    } else {
      report.errorAndAbort(
        s"TypedMigrationBuilder requires case classes. ${Type.show[A]} is not a case class."
      )
    }
  }

  /**
   * Build a tuple type from a list of field names as singleton strings.
   * This is public so NestedFieldExtractor can use it.
   */
  def buildFieldTupleType(names: List[String])(using Quotes): quotes.reflect.TypeRepr = {
    import quotes.reflect.*

    names match {
      case Nil => TypeRepr.of[EmptyTuple]
      case head :: tail =>
        val headType = ConstantType(StringConstant(head))
        val tailType = buildFieldTupleType(tail)
        TypeRepr.of[*:].appliedTo(List(headType, tailType))
    }
  }
}
