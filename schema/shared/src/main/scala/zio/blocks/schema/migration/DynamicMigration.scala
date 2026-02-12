package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * Pure Data migration engine. Executes a sequence of MigrationActions on
 * DynamicValues.
 */
final case class DynamicMigration(
  private[migration] actions: Vector[MigrationAction]
) {

  /**
   * Applies this migration to a DynamicValue. Executes actions sequentially,
   * stopping at the first error.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current = value
    val len     = actions.length
    var idx     = 0

    while (idx < len) {
      actions(idx).execute(current) match {
        case Right(newValue) =>
          current = newValue
        case left @ Left(_) =>
          return left
      }
      idx += 1
    }

    Right(current)
  }

  /**
   * Composes this migration with another, executing sequentially. Associativity
   * law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Returns the structural inverse of this migration. Reverses action order and
   * reverses each action. Law: m.reverse.reverse == m
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {

  /**
   * Identity migration - returns value unchanged. Law: identity.apply(v) ==
   * Right(v)
   */
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  implicit lazy val schema: Schema[DynamicMigration] = new Schema(
    new Reflect.Record[Binding, DynamicMigration](
      fields = Chunk.single(
        Reflect
          .Deferred(() => Schema[Vector[MigrationAction]].reflect.asInstanceOf[Reflect.Bound[Any]])
          .asTerm("actions")
      ),
      typeId = TypeId.of[DynamicMigration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                      = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            DynamicMigration(in.getObject(offset).asInstanceOf[Vector[MigrationAction]])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit =
            out.setObject(offset, in.actions)
        }
      ),
      modifiers = Chunk.empty
    )
  )
}
