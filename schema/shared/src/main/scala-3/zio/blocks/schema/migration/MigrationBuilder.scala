package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicOptic}
import zio.blocks.schema.migration.MigrationAction.*
import zio.blocks.schema.migration.macros.AccessorMacros
import zio.blocks.schema.migration.macros.MigrationMacros
import zio.blocks.schema.migration.MigrationState
import scala.quoted.*
import scala.annotation.unused

/**
 * MigrationBuilder (Scala 3 - List of Lists Edition)
 */
abstract class MigrationBuilder[A, B, S <: MigrationState.State](
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  private[migration] def withState[NextS <: MigrationState.State](
    action: MigrationAction
  ): MigrationBuilder[A, B, NextS] =
    new MigrationBuilder.Impl[A, B, NextS](sourceSchema, targetSchema, actions :+ action)

  // =================================================================================
  // Record Operations (UPDATED: 'transparent inline' without ': Any')
  // =================================================================================

  // [FIX] Removed ': Any' return type and added 'transparent' to allow type inference of the State
  transparent inline def addField[T](inline target: B => T, default: SchemaExpr[?]) =
    ${ MigrationMacros.addFieldImpl('this, 'target, 'default) }

  transparent inline def dropField[T](inline source: A => T, defaultForReverse: SchemaExpr[?]) =
    ${ MigrationMacros.dropFieldImpl('this, 'source, 'defaultForReverse) }

  transparent inline def renameField[T1, T2](inline from: A => T1, inline to: B => T2) =
    ${ MigrationMacros.renameFieldImpl('this, 'from, 'to) }

  // [FIX] kept the 3-argument fix from previous step
  transparent inline def transformField(inline from: A => Any, @unused inline to: B => Any, transform: SchemaExpr[?]) =
    ${ MigrationMacros.transformFieldImpl('this, 'from, 'transform) }

  // =================================================================================
  // Type Conversion Operations
  // =================================================================================

  transparent inline def mandateField(
    @unused inline source: A => Option[?],
    @unused inline target: B => Any,
    default: SchemaExpr[?]
  ): MigrationBuilder[A, B, MigrationState.MandateField[?, ?, S]] =
    withState[MigrationState.MandateField[?, ?, S]](
      Mandate(AccessorMacros.derive(source).optic, default)
    )

  transparent inline def optionalizeField(
    @unused inline source: A => Any,
    @unused inline target: B => Option[?]
  ): MigrationBuilder[A, B, MigrationState.OptionalizeField[?, ?, S]] =
    withState[MigrationState.OptionalizeField[?, ?, S]](
      Optionalize(AccessorMacros.derive(source).optic)
    )

  transparent inline def changeFieldType(
    @unused inline source: A => Any,
    @unused inline target: B => Any,
    converter: SchemaExpr[?]
  ): MigrationBuilder[A, B, MigrationState.ChangeType[?, ?, ?, S]] =
    withState[MigrationState.ChangeType[?, ?, ?, S]](
      ChangeType(AccessorMacros.derive(source).optic, converter)
    )

  // =================================================================================
  // Enum Operations
  // =================================================================================

  def renameCase(from: String, to: String): MigrationBuilder[A, B, S] =
    new MigrationBuilder.Impl[A, B, S](sourceSchema, targetSchema, actions :+ RenameCase(DynamicOptic.root, from, to))

  transparent inline def transformCase[CaseA, CaseB, SubS <: MigrationState.State](
    inline selector: A => CaseA,
    caseMigration: MigrationBuilder[CaseA, CaseB, MigrationState.Empty] => MigrationBuilder[CaseA, CaseB, SubS]
  )(using sA: Schema[CaseA], sB: Schema[CaseB]): MigrationBuilder[A, B, MigrationState.TransformCase[?, SubS, S]] = {

    val subBuilderStart = MigrationBuilder.make[CaseA, CaseB](using sA, sB)
    val subBuilderEnd   = caseMigration(subBuilderStart)
    val optic           = AccessorMacros.derive(selector).optic

    withState[MigrationState.TransformCase[?, SubS, S]](
      TransformCase(optic, subBuilderEnd.actions)
    )
  }

  // =================================================================================
  // Collection Operations
  // =================================================================================

  transparent inline def transformElements(
    @unused inline at: A => Vector[?],
    transform: SchemaExpr[?]
  ): MigrationBuilder[A, B, MigrationState.TransformElements[?, ?, S]] =
    withState[MigrationState.TransformElements[?, ?, S]](
      TransformElements(AccessorMacros.derive(at).optic, transform)
    )

  transparent inline def transformKeys(
    @unused inline at: A => Map[?, ?],
    transform: SchemaExpr[?]
  ): MigrationBuilder[A, B, MigrationState.TransformKeys[?, ?, S]] =
    withState[MigrationState.TransformKeys[?, ?, S]](
      TransformKeys(AccessorMacros.derive(at).optic, transform)
    )

  transparent inline def transformValues(
    @unused inline at: A => Map[?, ?],
    transform: SchemaExpr[?]
  ): MigrationBuilder[A, B, MigrationState.TransformValues[?, ?, S]] =
    withState[MigrationState.TransformValues[?, ?, S]](
      TransformValues(AccessorMacros.derive(at).optic, transform)
    )

  // =================================================================================
  // Final Assembly
  // =================================================================================

  inline def build: Migration[A, B] =
    ${ MigrationMacros.verifyMigration[A, B, S]('this) }

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder {
  private class Impl[A, B, S <: MigrationState.State](
    source: Schema[A],
    target: Schema[B],
    acts: Vector[MigrationAction]
  ) extends MigrationBuilder[A, B, S](source, target, acts)

  def make[A, B](using source: Schema[A], target: Schema[B]): MigrationBuilder[A, B, MigrationState.Empty] =
    new Impl[A, B, MigrationState.Empty](source, target, Vector.empty)
}
