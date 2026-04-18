/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
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

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

object Changeset {

  // Phantom type wrapper to preserve literal string types through macro expansion.
  // Direct use of ConstantType with .asType causes literal types to widen to String.
  // By wrapping in FieldName[N], the type argument is preserved when using appliedTo.
  sealed trait FieldName[N <: String & Singleton]

  // Field operations (tracked for validation)
  sealed trait AddField[N <: String & Singleton]
  sealed trait DropField[N <: String & Singleton]
  sealed trait RenameField[From <: String & Singleton, To <: String & Singleton]
  sealed trait TransformField[From <: String & Singleton, To <: String & Singleton]
  sealed trait MandateField[Source <: String & Singleton, Target <: String & Singleton]
  sealed trait OptionalizeField[Source <: String & Singleton, Target <: String & Singleton]
  sealed trait ChangeFieldType[Source <: String & Singleton, Target <: String & Singleton]
  sealed trait MigrateField[Name <: String & Singleton]

  // Case operations (tracked but not field-validated)
  sealed trait RenameCase[From <: String & Singleton, To <: String & Singleton]
  sealed trait TransformCase[CaseName <: String & Singleton]

  // Collection operations (tracked but not field-validated)
  sealed trait TransformElements[FieldName <: String & Singleton]
  sealed trait TransformKeys[FieldName <: String & Singleton]
  sealed trait TransformValues[FieldName <: String & Singleton]
}

final class MigrationBuilder[A, B, Changeset](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  transparent inline def addField(
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.addFieldImpl[A, B, Changeset]('this, 'target, 'default) }

  transparent inline def dropField(
    inline source: A => Any,
    defaultForReverse: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.dropFieldImpl[A, B, Changeset]('this, 'source, 'defaultForReverse) }

  transparent inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ) = ${ MigrationBuilderMacros.renameFieldImpl[A, B, Changeset]('this, 'from, 'to) }

  transparent inline def transformField(
    inline from: A => Any,
    inline to: B => Any,
    transform: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.transformFieldImpl[A, B, Changeset]('this, 'from, 'to, 'transform) }

  transparent inline def mandateField(
    inline source: A => Option[?],
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ) = ${
    MigrationBuilderMacros.mandateFieldImpl[A, B, Changeset]('this, 'source, 'target, 'default)
  }

  transparent inline def optionalizeField(
    inline source: A => Any,
    inline target: B => Option[?]
  ) = ${ MigrationBuilderMacros.optionalizeFieldImpl[A, B, Changeset]('this, 'source, 'target) }

  transparent inline def changeFieldType(
    inline source: A => Any,
    inline target: B => Any,
    converter: SchemaExpr[_, _]
  ) = ${
    MigrationBuilderMacros.changeFieldTypeImpl[A, B, Changeset]('this, 'source, 'target, 'converter)
  }

  transparent inline def renameCase(
    inline from: String,
    inline to: String
  ) = ${ MigrationBuilderMacros.renameCaseImpl[A, B, Changeset]('this, 'from, 'to) }

  transparent inline def transformCase[CaseA, CaseB](
    inline caseName: String
  )(
    caseMigration: MigrationBuilder[CaseA, CaseB, Any] => MigrationBuilder[CaseA, CaseB, ?]
  )(using
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ) = ${
    MigrationBuilderMacros.transformCaseImpl[A, B, CaseA, CaseB, Changeset](
      'this,
      'caseName,
      'caseSourceSchema,
      'caseTargetSchema,
      'caseMigration
    )
  }

  inline def transformElements(
    inline at: A => Iterable[?],
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B, Changeset] = {
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
  ): MigrationBuilder[A, B, Changeset] = {
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
  ): MigrationBuilder[A, B, Changeset] = {
    val path = SelectorMacros.toPath[A, Map[?, ?]](at)
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValues(path, transform.toDynamic)
    )
  }

  transparent inline def migrateField[F1, F2](
    inline selector: A => F1,
    migration: Migration[F1, F2]
  ) = ${
    MigrationBuilderMacros.migrateFieldExplicitImpl[A, B, F1, F2, Changeset](
      'this,
      'selector,
      'migration
    )
  }

  @scala.annotation.targetName("migrateFieldImplicit")
  transparent inline def migrateField[F1, F2](
    inline selector: A => F1
  )(using migration: Migration[F1, F2]) = ${
    MigrationBuilderMacros.migrateFieldImplicitImpl[A, B, F1, F2, Changeset](
      'this,
      'selector,
      'migration
    )
  }

  inline def build(using
    ev: MigrationComplete[A, B, Changeset]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))
}

object MigrationBuilder {
  def apply[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Any] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

trait MigrationComplete[-A, -B, -Changeset]

object MigrationComplete {
  private[migration] def unsafeCreate[A, B, CS]: MigrationComplete[A, B, CS] =
    instance.asInstanceOf[MigrationComplete[A, B, CS]]

  private val instance: MigrationComplete[Any, Any, Any] =
    new MigrationComplete[Any, Any, Any] {}

  inline given derive[A, B, CS]: MigrationComplete[A, B, CS] =
    ${ MigrationValidationMacros.validateMigration[A, B, CS] }
}
