package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaError}

/**
 * A single migration step operating at a path. All actions are fully
 * serializable (pure data, no closures). Each action has a structural reverse
 * for best-effort backward migration.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // ─────────────────────────────────────────────────────────────────────────
  // Record actions
  // ─────────────────────────────────────────────────────────────────────────

  /** Add a new field at the given path with a default value. */
  final case class AddField(at: DynamicOptic, default: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  /** Remove a field; defaultForReverse is used when reversing (downgrading). */
  final case class DropField(at: DynamicOptic, defaultForReverse: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /** Rename a field (path points to the field to rename; `to` is the new name). */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction =
      Rename(new DynamicOptic(at.nodes.dropRight(1) :+ DynamicOptic.Node.Field(to)), fieldNameAt(at))
  }

  /** Set the value at the path to the result of the expression (e.g. transform). */
  final case class TransformValue(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform)
  }

  /** Promote optional field to required using a default when missing. */
  final case class Mandate(at: DynamicOptic, default: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /** Make a required field optional (wrap in Option at schema level; value unchanged). */
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, MigrationExpr.Literal(DynamicValue.Null))
  }

  /** Combine multiple source paths into one value. */
  final case class Join(at: DynamicOptic, sourcePaths: Chunk[DynamicOptic], combineOp: CombineOp) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, combineOp.inverse)
  }

  /** Split one value at path into multiple target paths. */
  final case class Split(at: DynamicOptic, targetPaths: Chunk[DynamicOptic], splitOp: SplitOp) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitOp.inverse)
  }

  /** Serializable combine operation for Join. */
  sealed trait CombineOp {
    def inverse: SplitOp
    def eval(root: DynamicValue, paths: Chunk[DynamicOptic]): Either[SchemaError, DynamicValue]
  }
  object CombineOp {
    final case class StringConcat(separator: String) extends CombineOp {
      def inverse: SplitOp = SplitOp.StringSplit(separator)
      def eval(root: DynamicValue, paths: Chunk[DynamicOptic]): Either[SchemaError, DynamicValue] = {
        val parts = Chunk.newBuilder[String]
        var i     = 0
        while (i < paths.length) {
          root.get(paths(i)).one match {
            case Right(p: DynamicValue.Primitive) =>
              p.value match {
                case pv: zio.blocks.schema.PrimitiveValue.String => parts.addOne(pv.value)
                case _ => return Left(SchemaError.message("StringConcat requires string values", paths(i)))
              }
            case Right(_) => return Left(SchemaError.message("StringConcat requires primitive string values", paths(i)))
            case Left(e)  => return Left(e)
          }
          i += 1
        }
        Right(DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(parts.result().mkString(separator))))
      }
    }
  }

  /** Serializable split operation for Split. */
  sealed trait SplitOp {
    def inverse: CombineOp
    def evalSplit(value: DynamicValue): Either[SchemaError, Chunk[DynamicValue]]
  }
  object SplitOp {
    final case class StringSplit(separator: String) extends SplitOp {
      def inverse: CombineOp = CombineOp.StringConcat(separator)
      def evalSplit(value: DynamicValue): Either[SchemaError, Chunk[DynamicValue]] =
        value match {
          case p: DynamicValue.Primitive =>
            p.value match {
              case pv: zio.blocks.schema.PrimitiveValue.String =>
                Right(Chunk.from(pv.value.split(java.util.regex.Pattern.quote(separator)).map(s =>
                  DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(s))
                )))
              case _ => Left(SchemaError.message("StringSplit requires a string value", DynamicOptic.root))
            }
          case _ => Left(SchemaError.message("StringSplit requires a primitive string value", DynamicOptic.root))
        }
    }
  }

  /** Change the type at the path using a converter expression (primitive-to-primitive). */
  final case class ChangeType(at: DynamicOptic, converter: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum actions
  // ─────────────────────────────────────────────────────────────────────────

  /** Rename a variant case. */
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /** Transform the value inside a variant case with a sub-migration. */
  final case class TransformCase(at: DynamicOptic, actions: Chunk[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.map(_.reverse).reverse)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection / map actions
  // ─────────────────────────────────────────────────────────────────────────

  /** Transform each element of a sequence at the path. */
  final case class TransformElements(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform)
  }

  /** Transform each key of a map at the path. */
  final case class TransformKeys(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform)
  }

  /** Transform each value of a map at the path. */
  final case class TransformValues(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform)
  }

  private def fieldNameAt(at: DynamicOptic): String =
    at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                  => "?"
    }
}
