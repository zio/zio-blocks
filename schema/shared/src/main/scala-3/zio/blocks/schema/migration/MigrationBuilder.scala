package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr, ToStructural}
import zio.blocks.schema.DynamicOptic

import scala.quoted.*
import zio.blocks.schema.migration.MigrationAction.*

/** Macro-backed, typed migration builder (issue #519).
  *
  * Notes:
  *   - User supplies *selectors* (A => Any, B => Any), never optics.
  *   - Selectors are macro-compiled into DynamicOptic paths.
  *   - The resulting migration is pure data: Vector[MigrationAction].
  */
final class MigrationBuilder[A, B](
    val sourceSchema: Schema[A],
    val targetSchema: Schema[B],
    val actions: Vector[MigrationAction]
) { self =>

  // ----------------------------
  // Record operations
  // ----------------------------

  inline def addField(
      inline target: B => Any,
      inline default: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.addFieldImpl[A, B]('self, 'target, 'default) }

  inline def dropField(
      inline source: A => Any,
      inline defaultForReverse: SchemaExpr[B, _] = MigrationSchemaExpr.default
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.dropFieldImpl[A, B](
        'self,
        'source,
        'defaultForReverse
      )
    }

  inline def renameField(
      inline from: A => Any,
      inline to: B => Any
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B]('self, 'from, 'to) }

  inline def transformField(
      inline from: A => Any,
      inline to: B => Any,
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformFieldImpl[A, B](
        'self,
        'from,
        'to,
        'transform
      )
    }

  inline def mandateField(
      inline source: A => Option[?],
      inline target: B => Any,
      inline default: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.mandateFieldImpl[A, B](
        'self,
        'source,
        'target,
        'default
      )
    }

  inline def optionalizeField(
      inline source: A => Any,
      inline target: B => Option[?]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.optionalizeFieldImpl[A, B]('self, 'source, 'target)
    }

  inline def changeFieldType(
      inline source: A => Any,
      inline target: B => Any,
      inline converter: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.changeFieldTypeImpl[A, B](
        'self,
        'source,
        'target,
        'converter
      )
    }

  // ----------------------------
  // Enum operations (limited)
  // ----------------------------

  def renameCase[SumA, SumB](from: String, to: String): MigrationBuilder[A, B] =
    copyAppended(RenameCase(at = DynamicOptic.root, from = from, to = to))

  inline def renameCaseAt[SumA, SumB](
      inline at: A => Any,
      inline from: String,
      inline to: String
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.renameCaseAtImpl[A, B](
        'self,
        'at,
        'from,
        'to
      )
    }

  inline def transformCaseAt[CaseA, CaseB](
      inline at: A => CaseA
  )(
      inline caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[
        CaseA,
        CaseB
      ]
  )(using sa: Schema[CaseA], sb: Schema[CaseB]): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformCaseAtImpl[A, B, CaseA, CaseB](
        'self,
        'at,
        'caseMigration,
        'sa,
        'sb
      )
    }

  inline def transformCase[SumA, CaseA, SumB, CaseB](
      inline caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[
        CaseA,
        CaseB
      ]
  )(using
      sa: Schema[CaseA],
      sb: Schema[CaseB]
  ): MigrationBuilder[A, B] = {
    // case migrations are nested, so we just collect their actions and embed them.
    val nested = caseMigration(
      new MigrationBuilder[CaseA, CaseB](sa, sb, Vector.empty)
    )
    copyAppended(
      TransformCase(at = DynamicOptic.root, actions = nested.actions)
    )
  }

  // ----------------------------
  // Collections / Maps
  // ----------------------------

  inline def transformElements(
      inline at: A => Vector[?],
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformElementsImpl[A, B]('self, 'at, 'transform)
    }

  inline def transformKeys(
      inline at: A => Map[?, ?],
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformKeysImpl[A, B]('self, 'at, 'transform) }

  inline def transformValues(
      inline at: A => Map[?, ?],
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformValuesImpl[A, B]('self, 'at, 'transform)
    }

  // ----------------------------
  // Build
  // ----------------------------

  /** Build migration with validation (shape + constraints). */
  def build(using ToStructural[A], ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    MigrationValidator.validateOrThrow(prog, sourceSchema, targetSchema)
    Migration.fromProgram[A, B](prog)(using
      sourceSchema,
      targetSchema,
      summon[ToStructural[A]],
      summon[ToStructural[B]]
    )
  }

  /** Build migration without validation. */
  def buildPartial(using ToStructural[A], ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    Migration.fromProgram[A, B](prog)(using
      sourceSchema,
      targetSchema,
      summon[ToStructural[A]],
      summon[ToStructural[B]]
    )
  }

  // ----------------------------
  // Internals
  // ----------------------------

  private[migration] def copyAppended(
      action: MigrationAction
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

