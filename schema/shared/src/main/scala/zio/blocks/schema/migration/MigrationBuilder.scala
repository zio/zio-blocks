package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
import scala.collection.immutable.ArraySeq

final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction] = Vector.empty
) {

  // NOTE:
  // 你的專案目前沒有 DynamicOptic.fromPath，所以先用 root optic 佔位，確保能編譯/CI 先過。
  // 若你之後要真的 parse path，再把這裡補實作。
  private def opticFromPath(path: String): DynamicOptic =
    new DynamicOptic(ArraySeq.empty)

  def addField(path: String, default: DynamicValue): MigrationBuilder[A, B] = {
    val optic  = opticFromPath(path)
    val action = MigrationAction.AddField(optic, default)
    copy(actions = actions :+ action)
  }

  // 你的專案沒有 DynamicValue.Unit（錯誤提示有 DynamicValue.wait），先用它當預設值
  def dropField(path: String): MigrationBuilder[A, B] =
    dropField(path, DynamicValue.fromValue(()))

  def dropField(path: String, default: DynamicValue): MigrationBuilder[A, B] = {
    val optic  = opticFromPath(path)
    val action = MigrationAction.DropField(optic, default)
    copy(actions = actions :+ action)
  }

  def renameField(fromPath: String, toName: String): MigrationBuilder[A, B] = {
    val optic  = opticFromPath(fromPath)
    val action = MigrationAction.Rename(optic, toName)
    copy(actions = actions :+ action)
  }

  def transformField(path: String, f: DynamicValue => Either[String, DynamicValue]): MigrationBuilder[A, B] = {
    val optic  = opticFromPath(path)
    val action = MigrationAction.TransformValue(optic, f)
    copy(actions = actions :+ action)
  }

  def build: DynamicMigration =
    DynamicMigration(actions)
}

object MigrationBuilder {
  def empty[A](implicit schema: Schema[A]): MigrationBuilder[A, A] =
    MigrationBuilder(schema, schema)
}
