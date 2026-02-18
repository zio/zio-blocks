package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._

/**
 * An untyped, fully serializable migration that operates on [[DynamicValue]].
 * Contains no closures, functions, or runtime reflection -- only pure data.
 *
 * Migrations can be serialized, stored in registries, applied dynamically,
 * inspected, and transformed. They can also be used to generate upgraders,
 * downgraders, SQL DDL/DML, or offline data transforms.
 *
 * @see
 *   [[Migration]] for the typed, schema-aware wrapper
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Apply this migration to a [[DynamicValue]], returning the transformed value
   * or a [[MigrationError]].
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current = value
    var idx     = 0
    val len     = actions.length
    while (idx < len) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(updated) => current = updated
        case left           => return left
      }
      idx += 1
    }
    new Right(current)
  }

  /**
   * Compose two migrations. The result applies this migration first, then
   * `that`.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /**
   * Structural reverse: reverses the action order and inverts each individual
   * action.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverse.map(_.reverse))

  def isEmpty: Boolean = actions.isEmpty

  def size: Int = actions.length

  override def toString: String =
    if (actions.isEmpty) "DynamicMigration {}"
    else {
      val sb = new java.lang.StringBuilder("DynamicMigration {\n")
      actions.foreach { action =>
        sb.append("  ").append(action.toString).append('\n')
      }
      sb.append('}').toString
    }
}

object DynamicMigration {

  /** Empty migration -- identity element for composition. */
  val empty: DynamicMigration = new DynamicMigration(Chunk.empty)

  private[migration] def applyAction(
    dv: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = action match {

    case MigrationAction.AddField(at, default) =>
      dv.insertOrFail(at, default).left.map(e => new MigrationError(e.message, at))

    case MigrationAction.DropField(at, _) =>
      dv.deleteOrFail(at).left.map(e => new MigrationError(e.message, at))

    case MigrationAction.Rename(at, to) =>
      val nodes = at.nodes
      if (nodes.isEmpty)
        Left(new MigrationError("Rename requires a non-empty path"))
      else {
        val parentPath = new DynamicOptic(nodes.dropRight(1))
        (for {
          oldValue <- dv.get(at).one
          deleted  <- dv.deleteOrFail(at)
          result   <- deleted.insertOrFail(parentPath.field(to), oldValue)
        } yield result).left.map(e => new MigrationError(e.message, at))
      }

    case MigrationAction.TransformValue(at, newValue) =>
      dv.modifyOrFail(at) { case _ => newValue }.left.map(e => new MigrationError(e.message, at))

    case MigrationAction.Mandate(at, default) =>
      dv.modifyOrFail(at) {
        case DynamicValue.Null                   => default
        case DynamicValue.Variant("None", _)     => default
        case DynamicValue.Variant("Some", inner) => inner
        case other                               => other
      }.left
        .map(e => new MigrationError(e.message, at))

    case MigrationAction.Optionalize(_) =>
      new Right(dv)

    case MigrationAction.ChangeType(at, converter) =>
      dv.modifyOrFail(at) { case _ => converter }.left.map(e => new MigrationError(e.message, at))

    case MigrationAction.RenameCase(at, from, to) =>
      if (at.nodes.isEmpty) {
        dv match {
          case DynamicValue.Variant(name, value) if name == from =>
            new Right(DynamicValue.Variant(to, value))
          case _ =>
            new Right(dv)
        }
      } else {
        dv.modifyOrFail(at) {
          case DynamicValue.Variant(name, value) if name == from =>
            DynamicValue.Variant(to, value)
        }.left
          .map(e => new MigrationError(e.message, at))
      }

    case MigrationAction.TransformCase(at, nestedActions) =>
      val nested = new DynamicMigration(nestedActions)
      if (at.nodes.isEmpty) {
        dv match {
          case DynamicValue.Variant(caseName, caseValue) =>
            nested(caseValue).map(DynamicValue.Variant(caseName, _))
          case _ =>
            nested(dv)
        }
      } else {
        val pf: PartialFunction[DynamicValue, DynamicValue] = {
          case DynamicValue.Variant(caseName, caseValue) =>
            nested(caseValue) match {
              case Right(transformed) => DynamicValue.Variant(caseName, transformed)
              case Left(err)          => throw err
            }
          case other =>
            nested(other) match {
              case Right(transformed) => transformed
              case Left(err)          => throw err
            }
        }
        try dv.modifyOrFail(at)(pf).left.map(e => new MigrationError(e.message, at))
        catch { case e: MigrationError => Left(e) }
      }

    case MigrationAction.Join(at, _, _) =>
      Left(new MigrationError("Join requires SchemaExpr evaluation (not yet supported)", at))

    case MigrationAction.Split(at, _, _) =>
      Left(new MigrationError("Split requires SchemaExpr evaluation (not yet supported)", at))

    case MigrationAction.TransformElements(at, _) =>
      Left(new MigrationError("TransformElements requires SchemaExpr evaluation (not yet supported)", at))

    case MigrationAction.TransformKeys(at, _) =>
      Left(new MigrationError("TransformKeys requires SchemaExpr evaluation (not yet supported)", at))

    case MigrationAction.TransformValues(at, _) =>
      Left(new MigrationError("TransformValues requires SchemaExpr evaluation (not yet supported)", at))
  }
}
