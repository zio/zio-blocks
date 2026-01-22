package zio.blocks.schema.migration

// Scala 3: Extension methods are automatically available, no import needed
object MigrationTestCompat {
  // This object exists for Scala 2/3 compatibility
  // In Scala 3, extension methods work without imports
  // Provide a dummy to avoid "unused import" warning
  inline def ensureLoaded: Unit = ()
}
