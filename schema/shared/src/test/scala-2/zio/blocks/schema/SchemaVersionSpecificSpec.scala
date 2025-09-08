package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaVersionSpecificSpec")(
    test("doesn't generate schema for unsupported classes") {
      typeCheck {
        "Schema.derived[scala.concurrent.duration.Duration]"
      }.map(assert(_)(isLeft(containsString("Cannot find a primary constructor for 'Infinite.this.<local child>'"))))
    },
    test("doesn't generate schema for unsupported collections") {
      typeCheck {
        "Schema.derived[scala.collection.mutable.CollisionProofHashMap[String, Int]]"
      }.map(
        assert(_)(
          isLeft(
            containsString("Cannot derive schema for 'scala.collection.mutable.CollisionProofHashMap[String,Int]'.")
          )
        )
      )
    }
  )
}
