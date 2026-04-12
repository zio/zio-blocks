package zio.schema.migration

import zio.schema.Schema
import zio.schema.DynamicValue

/**
 * High-level typed API that wraps the pure structural `DynamicMigration`.
 * This carries the type bindings without polluting the serializable logic.
 */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /** Applies the structural migration, transitioning an A to a B */
  def apply(value: A): Either[MigrationError, B] =
    dynamicMigration.apply(sourceSchema.toDynamic(value)).flatMap { dynamicTarget =>
      targetSchema.fromDynamic(dynamicTarget) match {
        case Left(err) => Left(MigrationError.UnrecoverableParseError(s"Decoding failed: $err"))
        case Right(v)  => Right(v)
      }
    }

  /**
   * Compose migrations sequentially. 
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(
      dynamicMigration = this.dynamicMigration ++ that.dynamicMigration,
      sourceSchema = this.sourceSchema,
      targetSchema = that.targetSchema
    )

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Reverse structural migration. Note: Runtime behavior is best-effort for some complex inversions.
   */
  def reverse: Migration[B, A] =
    Migration(
      dynamicMigration = this.dynamicMigration.reverse,
      sourceSchema = this.targetSchema,
      targetSchema = this.sourceSchema
    )
}

/**
 * MigrationBuilder provides the DSL/fluent syntax for accumulating operations.
 * NOTE: S => A parameters conceptually represent AST paths. In real compilation,
 * these require Scala 3 inline macros to extract `DynamicOptic`. Left as pure-AST 
 * proxies for integration.
 */
class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  // Purely building up the underlying Algebraic Model:
  
  def addField(targetOp: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.AddField(targetOp, default))

  def dropField(sourceOp: DynamicOptic, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.DropField(sourceOp, defaultForReverse))

  def renameField(fromOp: DynamicOptic, toName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Rename(fromOp, toName))

  def mandateField(_: DynamicOptic, targetOp: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Mandate(targetOp, default))

  def optionalizeField(_: DynamicOptic, targetOp: DynamicOptic): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.Optionalize(targetOp))

  def renameCase(_: DynamicOptic, fromTag: String, toTag: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.RenameCase(_, fromTag, toTag))

  def transformCase(atOp: DynamicOptic, caseActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.TransformCase(atOp, caseActions))

  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  def buildPartial: Migration[A, B] = build // Skips compilation validations implemented in macros

  private def copy(actions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions)
}

object Migration {
  /** Creates an identity migration between identical schemas */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration(Vector.empty), schema, schema)

  def newBuilder[A, B](implicit sA: Schema[A], sB: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sA, sB, Vector.empty)
}
