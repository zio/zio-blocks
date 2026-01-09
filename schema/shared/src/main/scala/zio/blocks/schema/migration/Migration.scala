package zio.blocks.schema.migration

import zio.schema.Schema

/**
 * The user-facing API for Migrations.
 * It wraps the structural schemas and the pure dynamic migration plan.
 * * @param sourceSchema The schema of the old version (A)
 * @param targetSchema The schema of the new version (B)
 * @param dynamicMigration The pure, serializable migration logic
 */
final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {
  
  // এই মেথডগুলো আমরা Phase 4 এ ইমপ্লিমেন্ট করব
  def apply(value: A): Either[String, B] = ??? 
  def reverse: Migration[B, A] = ???
}

object Migration {
  // বিল্ডার শুরু করার জন্য এন্ট্রি পয়েন্ট
  def newBuilder[A, B](using src: Schema[A], tgt: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(src, tgt, Vector.empty)
}