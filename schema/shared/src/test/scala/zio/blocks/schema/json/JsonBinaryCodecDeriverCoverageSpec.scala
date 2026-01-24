package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object JsonBinaryCodecDeriverCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonBinaryCodecDeriverCoverageSpec")(
    test("exercises JsonBinaryCodecDeriver configuration methods") {
      val base = JsonBinaryCodecDeriver

      val d1  = base.withFieldNameMapper(NameMapper.SnakeCase)
      val d2  = d1.withCaseNameMapper(NameMapper.CamelCase)
      val d3  = d2.withDiscriminatorKind(DiscriminatorKind.Field("type"))
      val d4  = d3.withRejectExtraFields(rejectExtraFields = true)
      val d5  = d4.withEnumValuesAsStrings(enumValuesAsStrings = false)
      val d6  = d5.withTransientNone(transientNone = false)
      val d7  = d6.withRequireOptionFields(requireOptionFields = true)
      val d8  = d7.withTransientEmptyCollection(transientEmptyCollection = false)
      val d9  = d8.withRequireCollectionFields(requireCollectionFields = true)
      val d10 = d9.withTransientDefaultValue(transientDefaultValue = false)
      val d11 = d10.withRequireDefaultValueFields(requireDefaultValueFields = true)
      val d12 = d11.withDiscriminatorKind(DiscriminatorKind.Key)
      val d13 = d12.withDiscriminatorKind(DiscriminatorKind.None)

      assertTrue(
        (d1 ne base) &&
          (d2 ne d1) &&
          (d3 ne d2) &&
          (d4 ne d3) &&
          (d5 ne d4) &&
          (d6 ne d5) &&
          (d7 ne d6) &&
          (d8 ne d7) &&
          (d9 ne d8) &&
          (d10 ne d9) &&
          (d11 ne d10) &&
          (d12 ne d11) &&
          (d13 ne d12)
      )
    }
  )
}
