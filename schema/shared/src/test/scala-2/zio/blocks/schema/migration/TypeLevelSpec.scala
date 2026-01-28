package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.migration.TypeLevel._

object TypeLevelSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("TypeLevelSpec (Scala 2)")(
    suite("TList basic types")(
      test("TNil is a TList") {
        implicitly[TNil <:< TList]
        assertTrue(true)
      },
      test("TCons is a TList") {
        implicitly[TCons["a", TNil] <:< TList]
        assertTrue(true)
      },
      test(":: alias works") {
        implicitly[("a" :: TNil) =:= TCons["a", TNil]]
        assertTrue(true)
      },
      test("nested cons works") {
        implicitly[("a" :: "b" :: "c" :: TNil) =:= TCons["a", TCons["b", TCons["c", TNil]]]]
        assertTrue(true)
      }
    )
  )
}
