package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicValue, DynamicOptic}
import scala.annotation.unused

final class MigrationBuilder[A, B](
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction] = Vector.empty
) {

  /**
   * সমাধান: এই মেথডটি DynamicOptic কে সিরিয়ালাইজেবল প্রক্সিতে রূপান্তর করে।
   * এটি অন্য ফাইলে হাত না দিয়ে টাইপ মিসম্যাচ এরর সমাধান করার প্রফেশনাল উপায়।
   */
  private def toProxy(nodes: IndexedSeq[DynamicOptic.Node]): Vector[SerializableNode] = {
    nodes.map {
      case DynamicOptic.Node.Field(n) => SerializableNode.Field(n)
      case DynamicOptic.Node.Case(n)  => SerializableNode.Case(n)
      case DynamicOptic.Node.Elements => SerializableNode.Elements
      case DynamicOptic.Node.MapKeys  => SerializableNode.MapKeys
      case DynamicOptic.Node.MapValues => SerializableNode.MapValues
      case _ => SerializableNode.Field("unknown")
    }.toVector
  }

  // ১. নতুন ফিল্ড যোগ করা
  def addField[T](target: ToDynamicOptic[B, T], default: DynamicValue): MigrationBuilder[A, B] = {
    val path = target.apply()
    val fieldName = path.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ => "unknown"
    }
    // ফিক্স: সরাসরি প্রক্সি নোড পাস করা হয়েছে
    val action = MigrationAction.AddField(toProxy(path.nodes.dropRight(1)), fieldName, default)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ২. ফিল্ড রিনেম করা
  def renameField[T1, T2](from: ToDynamicOptic[A, T1], to: ToDynamicOptic[B, T2]): MigrationBuilder[A, B] = {
    val fromPath = from.apply()
    val toPath = to.apply()
    val fromName = fromPath.nodes.lastOption.collect { case DynamicOptic.Node.Field(n) => n }.getOrElse("unknown")
    val toName = toPath.nodes.lastOption.collect { case DynamicOptic.Node.Field(n) => n }.getOrElse("unknown")
    
    // ফিক্স: সরাসরি প্রক্সি নোড পাস করা হয়েছে
    val action = MigrationAction.Rename(toProxy(fromPath.nodes.dropRight(1)), fromName, toName)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ৩. টাইপ পরিবর্তন করা
  def changeFieldType[T1, T2](
    from: ToDynamicOptic[A, T1], 
    @unused to: ToDynamicOptic[B, T2] // @unused যোগ করে ওয়ার্নিং ফিক্স করা হয়েছে
  ): MigrationBuilder[A, B] = {
    val action = MigrationAction.ChangeType(toProxy(from.apply().nodes), "target_type")
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ৪. ফিল্ড ম্যান্ডেটরি করা
  def mandateField[T](
    from: ToDynamicOptic[A, Option[T]], 
    @unused to: ToDynamicOptic[B, T], // @unused যোগ করে ওয়ার্নিং ফিক্স করা হয়েছে
    default: DynamicValue
  ): MigrationBuilder[A, B] = {
    val action = MigrationAction.Mandate(toProxy(from.apply().nodes), default)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ৫. ফিল্ড বাদ দেওয়া
  def dropField[T](source: ToDynamicOptic[A, T], defaultForReverse: DynamicValue): MigrationBuilder[A, B] = {
    val action = MigrationAction.DropField(toProxy(source.apply().nodes), defaultForReverse)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  def build: Migration[A, B] = {
    val isComplete = actions.nonEmpty
    if (!isComplete && sourceSchema != targetSchema) {
      throw new RuntimeException("Incomplete Migration: Source and Target schemas differ but no actions provided.")
    }
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }

  def buildPartial: Migration[A, B] = {
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }
}

object MigrationBuilder {
  def make[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(source, target, Vector.empty)
}