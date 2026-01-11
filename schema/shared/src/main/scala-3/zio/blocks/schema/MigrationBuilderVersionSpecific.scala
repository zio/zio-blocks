package zio.blocks.schema

trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  inline def addField[T](inline selector: B => T, default: T)(using schema: Schema[T]): MigrationBuilder[A, B] =
    addField(MigrationMacros.fieldName(selector), schema.toDynamicValue(default))

  inline def renameField[T](inline from: A => T, inline to: B => T): MigrationBuilder[A, B] =
    renameField(MigrationMacros.fieldName(from), MigrationMacros.fieldName(to))

  inline def removeField[T](inline selector: A => T): MigrationBuilder[A, B] =
    removeField(MigrationMacros.fieldName(selector))

  inline def renameCase[T](inline from: A => T, inline to: B => T): MigrationBuilder[A, B] =
    renameCase(MigrationMacros.caseName(from), MigrationMacros.caseName(to))
}
