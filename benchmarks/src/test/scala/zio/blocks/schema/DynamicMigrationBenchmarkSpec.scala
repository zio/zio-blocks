package zio.blocks.schema

import zio.test._

object DynamicMigrationBenchmarkSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationBenchmarkSpec")(
    test("has consistent output") {
      val benchmark = new DynamicMigrationBenchmark()
      benchmark.setup()

      val addResult     = benchmark.addField()
      val renameResult  = benchmark.renameField()
      val composeResult = benchmark.composedMigrationApply()
      val nestedResult  = benchmark.nestedFieldMigration()
      val seqResult     = benchmark.sequenceTransform()
      val reversed      = benchmark.reverseMigration()

      assertTrue(
        addResult.isRight,
        renameResult.isRight,
        composeResult.isRight,
        nestedResult.isRight,
        seqResult.isRight,
        reversed.actions.size == 2
      )
    }
  )
}
