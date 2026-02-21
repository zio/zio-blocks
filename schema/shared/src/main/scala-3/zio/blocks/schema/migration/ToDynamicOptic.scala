package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.macros.AccessorMacros

/**
 * ToDynamicOptic (Scala 3 - Fixed Field Name).
 * -------------------------------------------- A Value Class wrapper to
 * transport the extracted path from Macro to Builder. * [FIX DETAILS]
 *   - Renamed field 'value' -> 'optic' to match usage in MigrationBuilder.
 */
final case class ToDynamicOptic[S, A](val optic: DynamicOptic) extends AnyVal {

  /**
   * Helper method to access the optic via function call syntax. Useful if some
   * parts of the code use `accessor()` style.
   */
  def apply(): DynamicOptic = optic
}

object ToDynamicOptic {

  /**
   * The macro entry point.
   */
  inline def derive[S, A](inline selector: S => A): ToDynamicOptic[S, A] =
    AccessorMacros.derive(selector)
}
