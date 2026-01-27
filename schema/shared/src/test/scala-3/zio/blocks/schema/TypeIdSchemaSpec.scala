package zio.blocks.schema

import zio.blocks.schema.json.{JsonTestUtils, JsonBinaryCodecDeriver, JsonBinaryCodec}
import zio.blocks.typeid._
import zio.test._

object TypeIdSchemaSpec extends ZIOSpecDefault {
  import TypeIdSchemas._

  private def deriveCodec[A](implicit schema: Schema[A]): JsonBinaryCodec[A] =
    schema.derive(JsonBinaryCodecDeriver)

  def spec = suite("TypeIdSchemaSpec")(
    suite("JSON round-trip serialization")(
      test("Variance round-trip") {
        // Sealed traits with case objects encode as string discriminators
        val values = List(
          Variance.Invariant     -> """"Invariant"""",
          Variance.Covariant     -> """"Covariant"""",
          Variance.Contravariant -> """"Contravariant""""
        )
        val codec = deriveCodec(varianceSchema)
        values.foldLeft(assertTrue(true)) { case (acc, (value, json)) =>
          acc && JsonTestUtils.roundTrip(value, json, codec)
        }
      },
      test("Kind.Type round-trip") {
        val codec = deriveCodec(kindSchema)
        JsonTestUtils.roundTrip(Kind.Type, """{"Type":{}}""", codec)
      },
      test("Kind.Arrow round-trip") {
        val codec = deriveCodec(kindSchema)
        val arrow = Kind.Arrow(List(Kind.Type), Kind.Type)
        JsonTestUtils.roundTrip(arrow, """{"Arrow":{"params":[{"Type":{}}],"result":{"Type":{}}}}""", codec)
      },
      test("Owner.Root round-trip") {
        val codec = deriveCodec(ownerSchema)
        // Empty list with defaults elided gives empty object
        JsonTestUtils.roundTrip(Owner.Root, """{}""", codec)
      },
      test("Owner with segments round-trip") {
        val codec = deriveCodec(ownerSchema)
        val owner = Owner.pkgs("scala", "collection")
        JsonTestUtils
          .roundTrip(owner, """{"segments":[{"Package":{"name":"scala"}},{"Package":{"name":"collection"}}]}""", codec)
      },
      test("TypeParam round-trip") {
        val codec = deriveCodec(typeParamSchema)
        val tp    = TypeParam("A", 0, Variance.Covariant, TypeBounds.empty, Kind.Type)
        // Default values are elided
        JsonTestUtils.roundTrip(
          tp,
          """{"name":"A","index":0,"variance":"Covariant"}""",
          codec
        )
      },
      test("TypeRepr simple types round-trip") {
        val codec = deriveCodec(typeReprSchema)
        JsonTestUtils.roundTrip[TypeRepr](TypeRepr.AnyType, """{"AnyType":{}}""", codec) &&
        JsonTestUtils.roundTrip[TypeRepr](TypeRepr.NothingType, """{"NothingType":{}}""", codec) &&
        JsonTestUtils.roundTrip[TypeRepr](TypeRepr.UnitType, """{"UnitType":{}}""", codec)
      },
      test("TypeRepr.TypeParamRef round-trip") {
        val codec    = deriveCodec(typeReprSchema)
        val paramRef = TypeRepr.TypeParamRef("T", 0)
        JsonTestUtils.roundTrip(paramRef, """{"TypeParamRef":{"name":"T","index":0}}""", codec)
      },
      test("TypeDefKind.Class round-trip") {
        val codec     = deriveCodec(typeDefKindSchema)
        val classKind = TypeDefKind.Class(isFinal = true, isAbstract = false, isCase = true, isValue = false)
        // Only non-default values are included
        JsonTestUtils.roundTrip(
          classKind,
          """{"Class":{"isFinal":true,"isCase":true}}""",
          codec
        )
      },
      test("TypeDefKind.Enum round-trip") {
        val codec    = deriveCodec(typeDefKindSchema)
        val enumKind = TypeDefKind.Enum(
          cases = List(
            EnumCaseInfo("Red", 0, Nil, isObjectCase = true),
            EnumCaseInfo("Green", 1, Nil, isObjectCase = true)
          )
        )
        // Empty params list is elided
        JsonTestUtils.roundTrip(
          enumKind,
          """{"Enum":{"cases":[{"name":"Red","ordinal":0,"isObjectCase":true},{"name":"Green","ordinal":1,"isObjectCase":true}]}}""",
          codec
        )
      },
      test("Constant round-trip") {
        val codec = deriveCodec(constantSchema)
        JsonTestUtils.roundTrip[Constant](
          Constant.StringConst("hello"),
          """{"StringConst":{"value":"hello"}}""",
          codec
        ) &&
        JsonTestUtils.roundTrip[Constant](
          Constant.IntConst(42),
          """{"IntConst":{"value":42}}""",
          codec
        ) &&
        JsonTestUtils.roundTrip[Constant](
          Constant.BooleanConst(true),
          """{"BooleanConst":{"value":true}}""",
          codec
        )
      },
      test("TypeId round-trip") {
        val codec  = deriveCodec(typeIdWildcardSchema)
        val typeId = TypeId[String](
          DynamicTypeId(
            owner = Owner.pkgs("java", "lang"),
            name = "String",
            typeParams = Nil,
            kind = TypeDefKind.Class(isFinal = true),
            parents = Nil,
            args = Nil,
            annotations = Nil
          )
        )
        // Default values and empty lists are elided
        val expectedJson =
          """{"owner":{"segments":[{"Package":{"name":"java"}},{"Package":{"name":"lang"}}]},"name":"String","kind":{"Class":{"isFinal":true}}}"""
        JsonTestUtils.roundTrip(typeId, expectedJson, codec)
      },
      test("TypeRepr.Ref with TypeId round-trip") {
        val typeReprCodec = deriveCodec(typeReprSchema)
        val typeId        = TypeId[Any](
          DynamicTypeId(
            owner = Owner.pkg("scala"),
            name = "Int",
            typeParams = Nil,
            kind = TypeDefKind.Class(isFinal = true),
            parents = Nil,
            args = Nil,
            annotations = Nil
          )
        )
        val ref = TypeRepr.Ref(typeId.dynamic, Nil)
        // Nested TypeId also has elided defaults
        val expectedJson =
          """{"Ref":{"id":{"owner":{"segments":[{"Package":{"name":"scala"}}]},"name":"Int","kind":{"Class":{"isFinal":true}}}}}"""
        JsonTestUtils.roundTrip(ref, expectedJson, typeReprCodec)
      }
    )
  )
}
