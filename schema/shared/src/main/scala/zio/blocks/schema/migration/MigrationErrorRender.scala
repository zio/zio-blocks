package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.MigrationError

/**
 * MigrationErrorRender provides a human-readable representation of migration
 * errors. It transforms structured error data into descriptive diagnostic
 * messages to assist in debugging.
 */
object MigrationErrorRender {

  /**
   * Translates a MigrationError into a professional error message string.
   */
  def render(error: MigrationError): String = {
    val pathString = renderPath(error.path)

    error match {
      case MigrationError.FieldNotFound(_, field) =>
        s"Field '$field' was not found at path: $pathString"

      case MigrationError.TypeMismatch(_, expected, actual) =>
        s"Type mismatch at path $pathString: expected '$expected', but encountered '$actual'"

      case MigrationError.TransformationFailed(_, action, reason) =>
        s"Failed to apply $action at $pathString. Detail: $reason"

      case MigrationError.CaseNotFound(_, caseName) =>
        s"Enum variant '$caseName' was not found at path: $pathString"

      case MigrationError.DecodingError(_, reason) =>
        s"Final schema decoding failed at path $pathString. Error: $reason"
    }
  }

  /**
   * Generates a dot-notation string representation of the DynamicOptic path.
   * Example: .user.orders.each.when[Active]
   */
  private def renderPath(optic: DynamicOptic): String =
    if (optic.nodes.isEmpty) "root"
    else
      optic.nodes.map {
        case DynamicOptic.Node.Field(name) => s".$name"
        case DynamicOptic.Node.Elements    => ".each"
        case DynamicOptic.Node.Case(name)  => s".when[$name]"
        case _                             => ".?"
      }.mkString
}
