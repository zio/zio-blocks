package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.migration.TypeLevel._

object TypeLevelSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("TypeLevelSpec (Scala 2)")(
    suite("TList basic types")(
      test("TNil is a TList") {
        val evidence: TNil <:< TList = implicitly[TNil <:< TList]
        assertTrue(evidence != null)
      },
      test("TCons is a TList") {
        val evidence: TCons["a", TNil] <:< TList = implicitly[TCons["a", TNil] <:< TList]
        assertTrue(evidence != null)
      },
      test(":: alias works") {
        val evidence: ("a" :: TNil) =:= TCons["a", TNil] = implicitly[("a" :: TNil) =:= TCons["a", TNil]]
        assertTrue(evidence != null)
      },
      test("nested cons works") {
        val evidence: ("a" :: "b" :: "c" :: TNil) =:= TCons["a", TCons["b", TCons["c", TNil]]] =
          implicitly[("a" :: "b" :: "c" :: TNil) =:= TCons["a", TCons["b", TCons["c", TNil]]]]
        assertTrue(evidence != null)
      },
      test("TCons is covariant in head") {
        val evidence: TCons[String, TNil] <:< TCons[Any, TNil] = implicitly
        assertTrue(evidence != null)
      },
      test("TCons is covariant in tail") {
        val evidence: TCons["a", TNil] <:< TCons["a", TList] = implicitly
        assertTrue(evidence != null)
      },
      test("TList works with tuple types") {
        type FieldPath = (String, String)
        val evidence: (FieldPath :: TNil) <:< TList = implicitly
        assertTrue(evidence != null)
      },
      test("longer lists compile correctly") {
        type LongList = "a" :: "b" :: "c" :: "d" :: "e" :: TNil
        val evidence: LongList <:< TList = implicitly
        assertTrue(evidence != null)
      }
    )
  )
}
