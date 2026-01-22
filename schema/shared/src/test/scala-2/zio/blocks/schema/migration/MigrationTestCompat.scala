package zio.blocks.schema.migration

import scala.language.implicitConversions

// Scala 2: Re-export the implicit conversion so it's available via import
object MigrationTestCompat {
  // Re-export the implicit conversion from MigrationBuilderSyntax
  implicit def toSyntax[A, B](builder: MigrationBuilder[A, B]): MigrationBuilderSyntax[A, B] =
    MigrationBuilderSyntax.toSyntax(builder)

  // Dummy method to make import usage consistent across Scala 2 and 3
  def ensureLoaded: Unit = ()
}
