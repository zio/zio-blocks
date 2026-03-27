package zio.blocks.schema.migration

extension [A, B](builder: MigrationBuilder[A, B]) {
  inline def build: Migration[A, B] =
    ${ MigrationMacros.buildImpl[A, B]('builder) }
}
