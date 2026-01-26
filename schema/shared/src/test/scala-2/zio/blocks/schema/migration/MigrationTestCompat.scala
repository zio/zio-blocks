package zio.blocks.schema.migration

import scala.language.implicitConversions

// Scala 2: Re-export the implicit conversion so it's available via import for test
object MigrationTestCompat {
  // Re-export the implicit conversion from MigrationBuilderSyntax
  implicit def toSyntax[A, B, Handled, Provided](
    builder: MigrationBuilder[A, B, Handled, Provided]
  ): MigrationBuilderSyntax[A, B, Handled, Provided] =
    MigrationBuilderSyntax.toSyntax(builder)

  // Dummy method to make import usage consistent across Scala 2 and 3
  def ensureLoaded: Unit = ()
}
