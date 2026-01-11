package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/** Tests for pure structural type Schema derivation (JVM only). */
object PureStructuralTypeSpec extends ZIOSpecDefault {

  type PersonLike = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("PureStructuralTypeSpec")(
    test("pure structural type derives schema") {
      val schema = Schema.derived[PersonLike]
      assertTrue(schema != null)
    },
    test("pure structural type schema has correct field names") {
      val schema     = Schema.derived[PersonLike]
      val fieldNames = schema.reflect match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
        case _                            => Set.empty[String]
      }
      assertTrue(fieldNames == Set("name", "age"))
    }
  )
}
