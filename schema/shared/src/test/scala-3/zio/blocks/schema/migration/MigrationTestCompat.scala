package zio.blocks.schema.migration

// Scala 3: Extension methods are automatically available, no import needed
// Adding import for tests, leads to unused import warning
// Not adding imports means Scala 2 fails to compile
object MigrationTestCompat {
  // This object exists for Scala 2/3 compatibility
  inline def ensureLoaded: Unit = ()
}
