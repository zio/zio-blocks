package zio.blocks.schema.migration

object MigrationBuilderOps {
  implicit class MigrationBuilderSyntax[A, B](
    val builder: MigrationBuilder[A, B]
  ) extends AnyVal {
    // Scala 2: .build falls back to buildPartial (compile-time validation
    // is only available on Scala 3 where inline macros are supported)
    def build: Migration[A, B] = builder.buildPartial
  }
}
