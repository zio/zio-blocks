package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.macros.MigrationMacros
import scala.annotation.unused

// [CRITICAL FIX] We explicitly import the Migration SchemaExpr (1 arg)
import zio.blocks.schema.migration.SchemaExpr

//@nowarn("msg=unused")
object EnableMacros {
  locally {
    import scala.language.experimental.macros
    val _ = macros
  }
}

abstract class MigrationBuilder[A, B, S <: MigrationState.State](
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private[migration] val actionsVector: Vector[MigrationAction]
) {
  import scala.language.experimental.macros

  def withState[NextS <: MigrationState.State](action: MigrationAction): MigrationBuilder[A, B, NextS] =
    new MigrationBuilder.Impl[A, B, NextS](sourceSchema, targetSchema, actionsVector :+ action)

  private def toOptic(nodes: IndexedSeq[DynamicOptic.Node]): DynamicOptic =
    DynamicOptic(nodes.toVector)

  // =================================================================================
  // MACRO OPERATIONS
  // Type: SchemaExpr[_] (1 argument, from migration package)
  // =================================================================================

  def addField[T](
    target: ToDynamicOptic[B, T],
    default: SchemaExpr[_]
  ): MigrationBuilder[A, B, _] = macro MigrationMacros.addFieldImpl[A, B, S, T]

  def dropField[T](
    source: ToDynamicOptic[A, T],
    defaultForReverse: SchemaExpr[_]
  ): MigrationBuilder[A, B, _] = macro MigrationMacros.dropFieldImpl[A, B, S, T]

  def renameField[T1, T2](
    from: ToDynamicOptic[A, T1],
    to: ToDynamicOptic[B, T2]
  ): MigrationBuilder[A, B, _] = macro MigrationMacros.renameFieldImpl[A, B, S, T1, T2]

  // =================================================================================
  // STANDARD OPERATIONS
  // =================================================================================

  def transformField[T1, T2](
    from: ToDynamicOptic[A, T1],
    @unused to: ToDynamicOptic[B, T2],
    transform: SchemaExpr[_]
  ): MigrationBuilder[A, B, MigrationState.ChangeType[_, _, _, S]] =
    withState[MigrationState.ChangeType[_, _, _, S]](TransformValue(toOptic(from.apply().nodes), transform))

  def mandateField[T](
    source: ToDynamicOptic[A, Option[T]],
    @unused target: ToDynamicOptic[B, T],
    default: SchemaExpr[_]
  ): MigrationBuilder[A, B, MigrationState.MandateField[_, _, S]] =
    withState[MigrationState.MandateField[_, _, S]](Mandate(toOptic(source.apply().nodes), default))

  def optionalizeField[T](
    source: ToDynamicOptic[A, T],
    @unused target: ToDynamicOptic[B, Option[T]]
  ): MigrationBuilder[A, B, MigrationState.OptionalizeField[_, _, S]] =
    withState[MigrationState.OptionalizeField[_, _, S]](Optionalize(toOptic(source.apply().nodes)))

  def changeFieldType[T1, T2](
    source: ToDynamicOptic[A, T1],
    @unused target: ToDynamicOptic[B, T2],
    converter: SchemaExpr[_]
  ): MigrationBuilder[A, B, MigrationState.ChangeType[_, _, _, S]] =
    withState[MigrationState.ChangeType[_, _, _, S]](ChangeType(toOptic(source.apply().nodes), converter))

  // =================================================================================
  // NESTED & COLLECTION OPERATIONS
  // =================================================================================

  def renameCase(from: String, to: String): MigrationBuilder[A, B, S] =
    new MigrationBuilder.Impl[A, B, S](
      sourceSchema,
      targetSchema,
      actionsVector :+ RenameCase(DynamicOptic.root, from, to)
    )

  def transformCase[CaseA, CaseB, SubS <: MigrationState.State](
    selector: ToDynamicOptic[A, CaseA],
    f: MigrationBuilder[CaseA, CaseB, MigrationState.Empty] => MigrationBuilder[CaseA, CaseB, SubS]
  )(implicit sA: Schema[CaseA], sB: Schema[CaseB]): MigrationBuilder[A, B, MigrationState.TransformCase[_, SubS, S]] = {
    val subBuilderStart = MigrationBuilder.make[CaseA, CaseB](sA, sB)
    val subBuilderEnd   = f(subBuilderStart)
    val caseOptic       = toOptic(selector.apply().nodes)
    withState[MigrationState.TransformCase[_, SubS, S]](TransformCase(caseOptic, subBuilderEnd.actionsVector))
  }

  def transformElements[T](
    at: ToDynamicOptic[A, Vector[T]],
    transform: SchemaExpr[_]
  ): MigrationBuilder[A, B, MigrationState.TransformElements[_, _, S]] =
    withState[MigrationState.TransformElements[_, _, S]](TransformElements(toOptic(at.apply().nodes), transform))

  def transformKeys[K, V](
    at: ToDynamicOptic[A, Map[K, V]],
    transform: SchemaExpr[_]
  ): MigrationBuilder[A, B, MigrationState.TransformKeys[_, _, S]] =
    withState[MigrationState.TransformKeys[_, _, S]](TransformKeys(toOptic(at.apply().nodes), transform))

  def transformValues[K, V](
    at: ToDynamicOptic[A, Map[K, V]],
    transform: SchemaExpr[_]
  ): MigrationBuilder[A, B, MigrationState.TransformValues[_, _, S]] =
    withState[MigrationState.TransformValues[_, _, S]](TransformValues(toOptic(at.apply().nodes), transform))

  def build: Migration[A, B] = macro zio.blocks.schema.migration.macros.MigrationMacros.verifyMigrationImpl[A, B, S]

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actionsVector), sourceSchema, targetSchema)
}

object MigrationBuilder {
  private class Impl[A, B, S <: MigrationState.State](
    source: Schema[A],
    target: Schema[B],
    acts: Vector[MigrationAction]
  ) extends MigrationBuilder[A, B, S](source, target, acts)

  def make[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B, MigrationState.Empty] =
    new Impl[A, B, MigrationState.Empty](source, target, Vector.empty)
}
