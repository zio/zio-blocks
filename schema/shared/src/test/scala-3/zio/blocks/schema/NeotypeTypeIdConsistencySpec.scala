package zio.blocks.schema

import zio.blocks.schema.fixtures.PlayerId
import zio.blocks.typeid.TypeId
import zio.test._

// This spec intentionally does NOT define custom newTypeSchema/subTypeSchema givens,
// so Schema.derived relies on the Schema macro's built-in neotype handling.
object NeotypeTypeIdConsistencySpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("NeotypeTypeIdConsistencySpec")(
    test("Schema-reflected TypeId for List[neotype Subtype] equals directly derived TypeId") {
      case class RoundEnded(results: List[PlayerId])
      val schema       = Schema.derived[RoundEnded]
      val fieldTypeId  = schema.reflect.asRecord.get.fields(0).value.typeId
      val directTypeId = TypeId.derived[List[PlayerId]]
      assertTrue(
        fieldTypeId == directTypeId,
        fieldTypeId.hashCode == directTypeId.hashCode
      )
    }
  )
}
