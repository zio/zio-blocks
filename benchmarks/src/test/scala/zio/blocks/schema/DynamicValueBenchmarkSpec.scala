package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object DynamicValueBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueBenchmarkSpec")(
    test("has consistent output") {
      assert((new DynamicValueBenchmark).fromDynamicValue)(isRight(equalTo((new DynamicValueBenchmark).a))) &&
      assert((new DynamicValueBenchmark).toDynamicValue)(equalTo((new DynamicValueBenchmark).dv))
    }
  )
}
