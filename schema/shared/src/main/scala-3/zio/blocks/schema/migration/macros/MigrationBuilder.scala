package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicValue, DynamicOptic}
import zio.blocks.schema.migration.macros.AccessorMacros
import scala.annotation.unused

final class MigrationBuilder[A, B](
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction] = Vector.empty
) {

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

  private def extractFieldName(path: DynamicOptic): String =
    path.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _ => "unknown"
    }

  inline def addField[T](inline target: B => T, default: DynamicValue): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(target).apply()
    val proxy = toProxy(path.nodes.dropRight(1))
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(proxy, extractFieldName(path), default))
  }

  inline def renameField[T1, T2](inline from: A => T1, inline to: B => T2): MigrationBuilder[A, B] = {
    val fromPath = AccessorMacros.derive(from).apply()
    val toPath = AccessorMacros.derive(to).apply()
    val proxy = toProxy(fromPath.nodes.dropRight(1))
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Rename(proxy, extractFieldName(fromPath), extractFieldName(toPath)))
  }

  inline def changeFieldType[T1, T2](inline from: A => T1, @unused inline to: B => T2): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(from).apply()
    val proxy = toProxy(path.nodes)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeType(proxy, "target_type"))
  }

  inline def mandateField[T](inline from: A => Option[T], @unused inline to: B => T, default: DynamicValue): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(from).apply()
    val proxy = toProxy(path.nodes)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(proxy, default))
  }

  inline def dropField[T](inline source: A => T, defaultForReverse: DynamicValue): MigrationBuilder[A, B] = {
    val path = AccessorMacros.derive(source).apply()
    val proxy = toProxy(path.nodes)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.DropField(proxy, defaultForReverse))
  }

  // সমাধান: এই প্রাইভেট মেথডটি যুক্ত করা হয়েছে যা JS/Native প্ল্যাটফর্মে ক্র্যাশ হওয়া রোধ করে
  private def ensureCompleteness(): Unit = {
    if (actions.isEmpty && sourceSchema != targetSchema) {
      throw new RuntimeException("Incomplete Migration: Source and Target schemas differ but no actions provided.")
    }
  }

  def build: Migration[A, B] = {
    ensureCompleteness() // ফিক্স: সরাসরি লজিক না লিখে এই মেথডটি কল করা হয়েছে
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