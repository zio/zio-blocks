package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

// Phantom type wrapper to preserve literal string types through macro expansion.
// Direct use of ConstantType with .asType causes literal types to widen to String.
// By wrapping in FieldName[N], the type argument is preserved when using appliedTo.
sealed trait FieldName[N <: String & Singleton]

final class MigrationBuilder[A, B, SourceHandled, TargetProvided](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  transparent inline def addField(
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.addFieldImpl[A, B, SourceHandled, TargetProvided]('this, 'target, 'default) }

  transparent inline def dropField(
    inline source: A => Any,
    defaultForReverse: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.dropFieldImpl[A, B, SourceHandled, TargetProvided]('this, 'source, 'defaultForReverse) }

  transparent inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ) = ${ MigrationBuilderMacros.renameFieldImpl[A, B, SourceHandled, TargetProvided]('this, 'from, 'to) }

  transparent inline def transformField(
    inline from: A => Any,
    inline to: B => Any,
    transform: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.transformFieldImpl[A, B, SourceHandled, TargetProvided]('this, 'from, 'to, 'transform) }

  transparent inline def mandateField(
    inline source: A => Option[?],
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ) = ${
    MigrationBuilderMacros.mandateFieldImpl[A, B, SourceHandled, TargetProvided]('this, 'source, 'target, 'default)
  }

  transparent inline def optionalizeField(
    inline source: A => Any,
    inline target: B => Option[?]
  ) = ${ MigrationBuilderMacros.optionalizeFieldImpl[A, B, SourceHandled, TargetProvided]('this, 'source, 'target) }

  transparent inline def changeFieldType(
    inline source: A => Any,
    inline target: B => Any,
    converter: SchemaExpr[_, _]
  ) = ${
    MigrationBuilderMacros.changeFieldTypeImpl[A, B, SourceHandled, TargetProvided]('this, 'source, 'target, 'converter)
  }

  def renameCase(
    from: String,
    to: String
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  def transformCase[CaseA, CaseB](
    caseName: String
  )(
    caseMigration: MigrationBuilder[CaseA, CaseB, Any, Any] => MigrationBuilder[CaseA, CaseB, ?, ?]
  )(using
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val innerBuilder = new MigrationBuilder[CaseA, CaseB, Any, Any](
      caseSourceSchema,
      caseTargetSchema,
      Vector.empty
    )
    val builtInner = caseMigration(innerBuilder)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(DynamicOptic.root.caseOf(caseName), builtInner.actions)
    )
  }

  inline def transformElements(
    inline at: A => Iterable[?],
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path = SelectorMacros.toPath[A, Iterable[?]](at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformElements(path, transform.toDynamic)
    )
  }

  inline def transformKeys(
    inline at: A => Map[?, ?],
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path = SelectorMacros.toPath[A, Map[?, ?]](at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformKeys(path, transform.toDynamic)
    )
  }

  inline def transformValues(
    inline at: A => Map[?, ?],
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B, SourceHandled, TargetProvided] = {
    val path = SelectorMacros.toPath[A, Map[?, ?]](at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValues(path, transform.toDynamic)
    )
  }

  transparent inline def transformNested[F1, F2](
    inline source: A => F1,
    inline target: B => F2
  )(
    inline nestedMigration: MigrationBuilder[F1, F2, Any, Any] => MigrationBuilder[F1, F2, ?, ?]
  )(using
    nestedSourceSchema: Schema[F1],
    nestedTargetSchema: Schema[F2]
  ) = ${
    MigrationBuilderMacros.transformNestedImpl[A, B, F1, F2, SourceHandled, TargetProvided](
      'this,
      'source,
      'target,
      'nestedMigration,
      'nestedSourceSchema,
      'nestedTargetSchema
    )
  }

  transparent inline def migrateField[F1, F2](
    inline source: A => F1,
    inline target: B => F2,
    migration: Migration[F1, F2]
  ) = ${
    MigrationBuilderMacros.migrateFieldExplicitImpl[A, B, F1, F2, SourceHandled, TargetProvided](
      'this,
      'source,
      'target,
      'migration
    )
  }

  @scala.annotation.targetName("migrateFieldImplicit")
  transparent inline def migrateField[F1, F2](
    inline source: A => F1,
    inline target: B => F2
  )(using migration: Migration[F1, F2]) = ${
    MigrationBuilderMacros.migrateFieldImplicitImpl[A, B, F1, F2, SourceHandled, TargetProvided](
      'this,
      'source,
      'target,
      'migration
    )
  }

  inline def build(using
    ev: MigrationComplete[A, B, SourceHandled, TargetProvided]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))
}

object MigrationBuilder {
  def apply[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Any, Any] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

trait MigrationComplete[-A, -B, -SourceHandled, -TargetProvided]

object MigrationComplete {
  private[migration] def unsafeCreate[A, B, SH, TP]: MigrationComplete[A, B, SH, TP] =
    instance.asInstanceOf[MigrationComplete[A, B, SH, TP]]

  private val instance: MigrationComplete[Any, Any, Any, Any] =
    new MigrationComplete[Any, Any, Any, Any] {}

  inline given derive[A, B, SH, TP]: MigrationComplete[A, B, SH, TP] =
    ${ MigrationValidationMacros.validateMigration[A, B, SH, TP] }
}
