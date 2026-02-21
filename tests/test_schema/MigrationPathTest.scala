import org.scalatest.funsuite.AnyFunSuite

class MigrationPathTest extends AnyFunSuite {
  test("test_migration_path_with_andThen") {
        val migration1 = new Migration(1)
        val migration2 = new Migration(2)
        val combinedMigration = migration1 andThen migration2

        assert(combinedMigration.getVersion() == 2)
      }
}