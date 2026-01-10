package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * Marker trait for errors that can occur during schema migrations.
 * 
 * Migration errors contain precise path information via DynamicOptic
 * to help diagnose exactly where a transformation failed.
 */
sealed trait MigrationError extends Product with Serializable {
  def path: DynamicOptic
  def message: String
  
  final def ++(that: MigrationError): MigrationError =
    (this, that) match {
      case (MigrationError.Multiple(e1), MigrationError.Multiple(e2)) => 
        MigrationError.Multiple(e1 ++ e2)
      case (MigrationError.Multiple(e1), single) => 
        MigrationError.Multiple(e1 :+ single)
      case (single, MigrationError.Multiple(e2)) => 
        MigrationError.Multiple(single +: e2)
      case (s1, s2) => 
        MigrationError.Multiple(Vector(s1, s2))
    }
}

object MigrationError {
  /**
   * Field was missing from the source data structure.
   */
  final case class MissingField(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Missing required field '$fieldName' at path ${path.toString}"
  }
  
  /**
   * Expected type did not match actual type found.
   */
  final case class TypeMismatch(
    path: DynamicOptic,
    expected: String,
    actual: String
  ) extends MigrationError {
    def message: String = s"Type mismatch at ${path.toString}: expected $expected but got $actual"
  }
  
  /**
   * Schema expression evaluation failed.
   */
  final case class EvaluationFailed(
    path: DynamicOptic,
    reason: String
  ) extends MigrationError {
    def message: String = s"Expression evaluation failed at ${path.toString}: $reason"
  }
  
  /**
   * Case was not found in variant.
   */
  final case class UnknownCase(path: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Unknown case '$caseName' at ${path.toString}"
  }
  
  /**
   * Migration action could not be reversed.
   */
  final case class NotReversible(action: String) extends MigrationError {
    def path: DynamicOptic = DynamicOptic.root
    def message: String = s"Migration action '$action' is not reversible"
  }
  
  /**
   * Multiple errors occurred during migration.
   */
  final case class Multiple(errors: Vector[MigrationError]) extends MigrationError {
    def path: DynamicOptic = errors.headOption.fold(DynamicOptic.root)(_.path)
    def message: String = errors.map(_.message).mkString("; ")
  }
}
