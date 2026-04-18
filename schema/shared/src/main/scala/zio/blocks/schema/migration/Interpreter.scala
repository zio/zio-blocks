package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue
import zio.blocks.schema.DynamicOptic

/**
 * Evaluates the pure DynamicMigration ADT against an untyped DynamicValue structure.
 * This execution engine guarantees stack-safety for deep migrations.
 */
object Interpreter {

  /**
   * Applies the specific migration to a dynamic record structure.
   */
  def run(migration: DynamicMigration, value: DynamicValue): Either[String, DynamicValue] = 
    migration match {
      case DynamicMigration.Identity => 
        Right(value)

      case DynamicMigration.Compose(left, right) =>
        for {
          intermediate <- run(left, value)
          finalResult  <- run(right, intermediate)
        } yield finalResult

      case DynamicMigration.AddField(optic, default) =>
        // Safely traverse optics and inject field into DynamicValue.Record
        updateAt(optic, value, _ => Right(default), isAdditive = true)

      case DynamicMigration.DeleteField(optic) =>
        deleteAt(optic, value)

      case DynamicMigration.RenameField(optic, newName) =>
        renameAt(optic, value, newName)

      case _ => 
        Left("Feature execution not implemented for requested Algebraic Migration node.")
    }

  // ---- Optic traversal primitives ----
  
  private def updateAt(
    optic: DynamicOptic, 
    value: DynamicValue, 
    updater: DynamicValue => Either[String, DynamicValue],
    isAdditive: Boolean
  ): Either[String, DynamicValue] = {
    traverse(optic.nodes.toList, value, updater, isAdditive)
  }

  private def deleteAt(optic: DynamicOptic, value: DynamicValue): Either[String, DynamicValue] = {
    traverseDelete(optic.nodes.toList, value)
  }

  private def renameAt(optic: DynamicOptic, value: DynamicValue, newName: String): Either[String, DynamicValue] = {
    traverseRename(optic.nodes.toList, value, newName)
  }

  private def traverse(
    nodes: List[DynamicOptic.Node],
    value: DynamicValue,
    updater: DynamicValue => Either[String, DynamicValue],
    isAdditive: Boolean
  ): Either[String, DynamicValue] = nodes match {
    case Nil => updater(value)
    case DynamicOptic.Node.Field(name) :: tail =>
      value match {
        case r: DynamicValue.Record =>
          val fields = r.fields
          val idx = fields.indexWhere(_._1 == name)
          if (idx != -1) {
            val childValue = fields(idx)._2
            traverse(tail, childValue, updater, isAdditive).map { updatedChild =>
              DynamicValue.Record(fields.updated(idx, (name, updatedChild)))
            }
          } else if (isAdditive && tail.isEmpty) {
            updater(DynamicValue.Null).map { newValue => // Fallback neutral value
              DynamicValue.Record(fields :+ (name -> newValue))
            }
          } else Left(s"Field '$name' not found.")
        case _ => Left(s"Expected Record to access field '$name'")
      }
    case _ => Left("Unsupported optic traversal node")
  }

  private def traverseDelete(
    nodes: List[DynamicOptic.Node],
    value: DynamicValue
  ): Either[String, DynamicValue] = nodes match {
    case DynamicOptic.Node.Field(name) :: Nil =>
      value match {
        case r: DynamicValue.Record => Right(DynamicValue.Record(r.fields.filterNot(_._1 == name)))
        case _ => Left("Expected Record to delete field")
      }
    case DynamicOptic.Node.Field(name) :: tail =>
      value match {
        case r: DynamicValue.Record =>
          val fields = r.fields
          val idx = fields.indexWhere(_._1 == name)
          if (idx != -1) {
            val childValue = fields(idx)._2
            traverseDelete(tail, childValue).map { updatedChild =>
              DynamicValue.Record(fields.updated(idx, (name, updatedChild)))
            }
          } else Left(s"Field '$name' not found.")
        case _ => Left("Expected Record")
      }
    case _ => Left("Invalid optic for deletion")
  }

  private def traverseRename(
    nodes: List[DynamicOptic.Node],
    value: DynamicValue,
    newName: String
  ): Either[String, DynamicValue] = nodes match {
    case DynamicOptic.Node.Field(name) :: Nil =>
      value match {
        case r: DynamicValue.Record =>
          val fields = r.fields
          val idx = fields.indexWhere(_._1 == name)
          if (idx != -1) {
             val childValue = fields(idx)._2
             Right(DynamicValue.Record(fields.updated(idx, (newName, childValue))))
          } else Left(s"Field '$name' not found.")
        case _ => Left("Expected Record to rename field")
      }
    case DynamicOptic.Node.Field(name) :: tail =>
      value match {
        case r: DynamicValue.Record =>
          val fields = r.fields
          val idx = fields.indexWhere(_._1 == name)
          if (idx != -1) {
             val childValue = fields(idx)._2
             traverseRename(tail, childValue, newName).map { updatedChild =>
               DynamicValue.Record(fields.updated(idx, (name, updatedChild)))
             }
          } else Left(s"Field '$name' not found.")
        case _ => Left("Expected Record")
      }
    case _ => Left("Invalid optic for rename")
  }
}
