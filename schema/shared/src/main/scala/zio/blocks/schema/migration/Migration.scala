package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import zio.blocks.schema.DynamicValue

final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] = {
    // সমস্যা ১: মেথডের নাম সম্ভবত toDynamicValue
    // যদি আপনার Schema.scala-তে নাম অন্য কিছু থাকে, তবে সেই নামটি এখানে দিন
    val dynamicValue: DynamicValue = sourceSchema.toDynamicValue(value)

    // সমস্যা ২: match ব্লকের বদলে flatMap ব্যবহার করা হয়েছে টাইপ সেফটির জন্য
    dynamicMigration.apply(dynamicValue).flatMap { (dynamicResult: DynamicValue) =>
      targetSchema.fromDynamicValue(dynamicResult) match {
        case Left(schemaError) => 
          Left(MigrationError.SchemaMismatch(schemaError.toString))
        case Right(v) => 
          Right(v)
      }
    }
  }

  def ++[C](that: Migration[B, C]): Migration[A, C] = 
    Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  def reverse: Migration[B, A] = 
    Migration(this.dynamicMigration.reverse, targetSchema, sourceSchema)
}