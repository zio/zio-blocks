package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue
import zio.blocks.schema.DynamicValue._
import zio.blocks.schema.DynamicOptic

object PathNavigator {
  def transform(
    current: DynamicValue, 
    nodes: Vector[DynamicOptic.Node]
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    
    if (nodes.isEmpty) f(current)
    else nodes.head match {
      case DynamicOptic.Node.Field(name) =>
        current match {
          case Record(fields) =>
            val index = fields.indexWhere(_._1 == name)
            if (index == -1) Left(MigrationError.FieldNotFound(DynamicOptic(nodes), name))
            else {
              val (key, value) = fields(index)
              transform(value, nodes.tail)(f).map(updated => Record(fields.updated(index, (key, updated))))
            }
          case _ => Left(MigrationError.TypeMismatch(DynamicOptic(nodes), "Record", current.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Elements =>
        current match {
          case Sequence(elements) =>
            val updatedResults = elements.map(e => transform(e, nodes.tail)(f))
            sequenceResults(updatedResults).map(Sequence(_))
          case _ => Left(MigrationError.TypeMismatch(DynamicOptic(nodes), "Sequence", current.getClass.getSimpleName))
        }

      case DynamicOptic.Node.MapKeys =>
        current match {
          case Map(entries) =>
            val updated = entries.map { case (k, v) => (transform(k, nodes.tail)(f).getOrElse(k), v) }
            Right(Map(updated))
          case _ => Left(MigrationError.TypeMismatch(DynamicOptic(nodes), "Map", current.getClass.getSimpleName))
        }

      case DynamicOptic.Node.MapValues =>
        current match {
          case Map(entries) =>
            val updated = entries.map { case (k, v) => (k, transform(v, nodes.tail)(f).getOrElse(v)) }
            Right(Map(updated))
          case _ => Left(MigrationError.TypeMismatch(DynamicOptic(nodes), "Map", current.getClass.getSimpleName))
        }

      case _ => Left(MigrationError.TransformationFailed(DynamicOptic(nodes), "Node type not supported"))
    }
  }

  private def sequenceResults(results: Vector[Either[MigrationError, DynamicValue]]): Either[MigrationError, Vector[DynamicValue]] = {
    results.foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
      case (Right(acc), Right(v)) => Right(acc :+ v)
      case (Left(err), _)         => Left(err)
      case (_, Left(err))         => Left(err)
    }
  }
}