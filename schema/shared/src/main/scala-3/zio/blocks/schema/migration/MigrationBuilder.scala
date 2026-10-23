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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}

/**
 * Marker types that record, in a [[MigrationBuilder]]'s `Changeset` type, which
 * operations it has added. [[MigrationComplete]] reads them at compile time;
 * they have no values.
 */
object Changeset {

  // Wrapping a name in FieldName[N] keeps its literal type when the macros use
  // appliedType/appliedTo; a bare ConstantType widens to String.
  /**
   * A field the builder handles and provides, recorded by `migrateField` for
   * each nested field of the migrated value. The name is a literal string type.
   */
  sealed trait FieldName[N <: String & Singleton]

  // Field operations (tracked for validation)
  /** Records that `addField` provides the target field `N`. */
  sealed trait AddField[N <: String & Singleton]

  /** Records that `dropField` handles the source field `N`. */
  sealed trait DropField[N <: String & Singleton]

  /**
   * Records that `renameField` handles the source field `From` and provides the
   * target field `To`.
   */
  sealed trait RenameField[From <: String & Singleton, To <: String & Singleton]

  /**
   * Records that `transformField` handles the source field `From` and provides
   * the target field `To`.
   */
  sealed trait TransformField[From <: String & Singleton, To <: String & Singleton]

  /**
   * Records that `mandateField` handles the source field `Source` and provides
   * the target field `Target`.
   */
  sealed trait MandateField[Source <: String & Singleton, Target <: String & Singleton]

  /**
   * Records that `optionalizeField` handles the source field `Source` and
   * provides the target field `Target`.
   */
  sealed trait OptionalizeField[Source <: String & Singleton, Target <: String & Singleton]

  /** Records that `changeFieldType` handles and provides the field `Name`. */
  sealed trait ChangeFieldType[Name <: String & Singleton]

  /** Records that `migrateField` handles and provides the field `Name`. */
  sealed trait MigrateField[Name <: String & Singleton]

  // Case operations (tracked but not field-validated)
  /**
   * Marker for `renameCase`; not part of the completeness check and not added
   * by the builder.
   */
  sealed trait RenameCase[From <: String & Singleton, To <: String & Singleton]

  /**
   * Marker for `transformCase`; not part of the completeness check and not
   * added by the builder.
   */
  sealed trait TransformCase[CaseName <: String & Singleton]

  // Collection operations (tracked but not field-validated)
  /**
   * Marker for `transformElements`; not part of the completeness check and not
   * added by the builder.
   */
  sealed trait TransformElements[FieldName <: String & Singleton]

  /**
   * Marker for `transformKeys`; not part of the completeness check and not
   * added by the builder.
   */
  sealed trait TransformKeys[FieldName <: String & Singleton]

  /**
   * Marker for `transformValues`; not part of the completeness check and not
   * added by the builder.
   */
  sealed trait TransformValues[FieldName <: String & Singleton]
}

/**
 * Builds a [[Migration]] from `A` to `B` one action at a time. Start with
 * [[Migration.newBuilder]], add actions with selector lambdas such as `_.title`
 * or `_.author.name`, and finish with [[build]]; [[Migration]] shows an
 * example. Each method returns a new builder.
 *
 * The record methods take selectors made of field accesses only, and record the
 * fields they touch in the `Changeset` type. [[build]] uses it to check at
 * compile time that the migration is complete: every field of `B` is provided
 * and every field of `A` handled, either by an action or because both types
 * have a field of that name whose types are equal or one a subtype of the
 * other. The case and collection methods are not part of that check. The
 * selector syntax of [[MigrationSelectorSyntax]], such as `_.books.each`, does
 * not compile in any method (see `docs/dev/issues.md`).
 *
 * On Scala 2 the macro methods and `build` expand to code that uses
 * package-private members, so they compile only inside
 * `zio.blocks.schema.migration` (see `docs/dev/issues.md`).
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the target type
 * @tparam Changeset
 *   the record operations added so far, as an intersection of the types in
 *   [[Changeset]]
 */
final class MigrationBuilder[A, B, Changeset](
  /** The schema of the source type. */
  val sourceSchema: Schema[A],
  /** The schema of the target type. */
  val targetSchema: Schema[B],
  private[migration] val actions: Chunk[MigrationAction]
) {

  /**
   * Adds the field that `target` selects in `B`, with the value of `default`; a
   * nested selector such as `_.author.born` adds the field to the nested
   * record. `default` is evaluated against the whole value being migrated, not
   * against the record that gets the field. [[Migration#reverse]] drops the
   * field again.
   */
  transparent inline def addField(
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.addFieldImpl[A, B, Changeset]('this, 'target, 'default) }

  /**
   * Removes the field that `source` selects in `A`. [[Migration#reverse]] adds
   * it back with the value of `defaultForReverse`.
   */
  transparent inline def dropField(
    inline source: A => Any,
    defaultForReverse: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.dropFieldImpl[A, B, Changeset]('this, 'source, 'defaultForReverse) }

  /**
   * Renames the field that `from` selects in `A` to the last field name of
   * `to`, in the same parent record. To change a field's value and its name,
   * call [[changeFieldType]] with the old name first.
   */
  transparent inline def renameField(
    inline from: A => Any,
    inline to: B => Any
  ) = ${ MigrationBuilderMacros.renameFieldImpl[A, B, Changeset]('this, 'from, 'to) }

  /**
   * Replaces the value of the field that `from` selects with the value of
   * `transform`, evaluated against the value it replaces. `from` and `to` must
   * select fields with the same name, or compilation fails.
   * [[Migration#reverse]] fails at this step.
   */
  transparent inline def transformField(
    inline from: A => Any,
    inline to: B => Any,
    transform: SchemaExpr[_, _]
  ) = ${ MigrationBuilderMacros.transformFieldImpl[A, B, Changeset]('this, 'from, 'to, 'transform) }

  /**
   * Turns the `Option` field that `source` selects into a required field, using
   * the value of `default` for a `None`. `source` and `target` must select
   * fields with the same name, or compilation fails. The migration currently
   * fails for every `Some`, and so does its reverse, an `OptionalizeField` (see
   * `docs/dev/issues.md`).
   */
  transparent inline def mandateField(
    inline source: A => Option[?],
    inline target: B => Any,
    default: SchemaExpr[_, _]
  ) = ${
    MigrationBuilderMacros.mandateFieldImpl[A, B, Changeset]('this, 'source, 'target, 'default)
  }

  /**
   * Turns the field that `source` selects into an `Option` field. `source` and
   * `target` must select fields with the same name, or compilation fails. The
   * migration currently fails to decode the result (see `docs/dev/issues.md`).
   */
  transparent inline def optionalizeField(
    inline source: A => Any,
    inline target: B => Option[?]
  ) = ${ MigrationBuilderMacros.optionalizeFieldImpl[A, B, Changeset]('this, 'source, 'target) }

  /**
   * Replaces the value of the field that `source` selects with the value of
   * `converter`, evaluated against the value it replaces. The field counts as
   * both handled and provided, so this fits a field whose type differs between
   * `A` and `B`. Nothing restricts it to primitive types. [[Migration#reverse]]
   * fails at this step.
   */
  transparent inline def changeFieldType(
    inline source: A => Any,
    converter: SchemaExpr[_, _]
  ) = ${
    MigrationBuilderMacros.changeFieldTypeImpl[A, B, Changeset]('this, 'source, 'converter)
  }

  /**
   * Renames the case `from` of `A` to `to`; values in other cases are left as
   * they are. It acts on `A` itself, which must be the sealed trait or enum:
   * for a field of an enum type, build a migration of the enum and apply it
   * with `migrateField`.
   */
  def renameCase(
    from: String,
    to: String
  ): MigrationBuilder[A, B, Changeset] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  /**
   * Applies to values of `A` in the case `caseName` the actions that
   * `caseMigration` adds to an empty builder from `CaseA` to `CaseB`, and
   * renames that case to the name of `CaseB`'s type. Like [[renameCase]], it
   * acts on `A` itself. The inner builder's changes are not checked for
   * completeness.
   */
  def transformCase[CaseA, CaseB](
    caseName: String
  )(
    caseMigration: MigrationBuilder[CaseA, CaseB, Any] => MigrationBuilder[CaseA, CaseB, ?]
  )(using
    caseSourceSchema: Schema[CaseA],
    caseTargetSchema: Schema[CaseB]
  ): MigrationBuilder[A, B, Changeset] = {
    val innerBuilder = new MigrationBuilder[CaseA, CaseB, Any](
      caseSourceSchema,
      caseTargetSchema,
      Chunk.empty
    )
    val builtInner     = caseMigration(innerBuilder)
    val targetCaseName = caseTargetSchema.reflect.typeId.name
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(DynamicOptic.root.caseOf(caseName), targetCaseName, builtInner.actions)
    )
  }

  /**
   * Replaces every element of the collection that `at` selects with the value
   * of `transform`, evaluated against that element.
   */
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

  /**
   * Replaces every key of the map that `at` selects with the value of
   * `transform`, evaluated against that key.
   */
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

  /**
   * Replaces every value of the map that `at` selects with the value of
   * `transform`, evaluated against that value.
   */
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

  /**
   * Runs `migration` on the field that `selector` selects. The field and all
   * its nested fields count as handled and provided, so this fits a field whose
   * type changes between `A` and `B`.
   */
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

  /**
   * Runs the implicit `migration` on the field that `selector` selects, as the
   * overload that takes the migration does.
   */
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

  /**
   * Returns the migration. It compiles only when the actions make the migration
   * complete; otherwise the [[MigrationComplete]] macro fails compilation with
   * the target fields not provided and the source fields not handled.
   */
  inline def build(using
    ev: MigrationComplete[A, B, Changeset]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

}

/** The [[MigrationBuilder]] entry point. */
object MigrationBuilder {

  /**
   * Starts an empty builder from `A` to `B`, as [[Migration.newBuilder]] does.
   */
  def apply[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, Any] =
    new MigrationBuilder(sourceSchema, targetSchema, Chunk.empty)
}

/**
 * Evidence that a builder's `Changeset` makes the migration from `A` to `B`
 * complete. A macro derives it; when fields are missing, compilation fails with
 * a message that lists the target fields not provided and the source fields not
 * handled.
 */
trait MigrationComplete[-A, -B, -Changeset]

/** Derives [[MigrationComplete]]. */
object MigrationComplete {
  private[migration] def unsafeCreate[A, B, CS]: MigrationComplete[A, B, CS] =
    instance.asInstanceOf[MigrationComplete[A, B, CS]]

  private val instance: MigrationComplete[Any, Any, Any] =
    new MigrationComplete[Any, Any, Any] {}

  /**
   * Checks the changeset at compile time, as [[MigrationComplete]] describes.
   */
  inline given derive[A, B, CS]: MigrationComplete[A, B, CS] =
    ${ MigrationValidationMacros.validateMigration[A, B, CS] }
}
