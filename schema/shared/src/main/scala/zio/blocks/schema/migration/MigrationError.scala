package zio.blocks.schema.migration

import scala.util.control.NoStackTrace
import zio.blocks.schema.DynamicOptic

/**
 * Error type for migration operations. Captures the [[DynamicOptic]] path at
 * which the error occurred, enabling diagnostics such as:
 *
 * {{{
 * "Failed to apply TransformValue at .addresses.each.streetNumber"
 * }}}
 */
final case class MigrationError(message: String, path: DynamicOptic = DynamicOptic.root)
    extends Exception(
      if (path.nodes.isEmpty) message
      else s"$message at: ${path.toScalaString}"
    )
    with NoStackTrace {

  def atField(name: String): MigrationError =
    new MigrationError(message, DynamicOptic.root.field(name)(path))

  def atCase(name: String): MigrationError =
    new MigrationError(message, DynamicOptic.root.caseOf(name)(path))
}
