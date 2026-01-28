package zio.blocks.schema

import zio.blocks.typeid._
// TypeId schema instances are now available through Schema companion object
import zio.test._

/**
 * Round-trip serialization tests for TypeId and related types (Scala 3 only).
 * Verifies that TypeId infrastructure can be serialized to DynamicValue and
 * back without loss of information.
 */
object TypeIdSerializationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("TypeIdSerializationSpec")(
    suite("TypeId Round-Trip")(
      test("simple TypeId round-trip through DynamicValue") {
        val typeId  = TypeId.of[String]
        val schema  = Schema.typeIdAnySchema
        val dynamic = schema.toDynamicValue(typeId.asInstanceOf[TypeId[Any]])
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.name == typeId.name) &&
        assertTrue(result.toOption.get.owner == typeId.owner)
      },
      test("generic TypeId round-trip") {
        val typeId  = TypeId.of[List[Int]]
        val schema  = Schema.typeIdAnySchema
        val dynamic = schema.toDynamicValue(typeId.asInstanceOf[TypeId[Any]])
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.name == "List")
      },
      test("nested generic TypeId round-trip") {
        val typeId  = TypeId.of[Map[String, List[Int]]]
        val schema  = Schema.typeIdAnySchema
        val dynamic = schema.toDynamicValue(typeId.asInstanceOf[TypeId[Any]])
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.name == "Map")
      }
    ),
    suite("TypeRepr Round-Trip")(
      test("simple TypeRepr round-trip") {
        val typeRepr = TypeRepr.Ref(TypeId.of[Int].dynamic, Nil)
        val schema   = Schema.typeReprSchema
        val dynamic  = schema.toDynamicValue(typeRepr)
        val result   = schema.fromDynamicValue(dynamic)
        assertTrue(result.isRight)
      },
      test("Union TypeRepr round-trip") {
        val typeRepr = TypeRepr.Union(
          List(
            TypeRepr.Ref(TypeId.of[Int].dynamic, Nil),
            TypeRepr.Ref(TypeId.of[String].dynamic, Nil)
          )
        )
        val schema  = Schema.typeReprSchema
        val dynamic = schema.toDynamicValue(typeRepr)
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result.isRight)
      }
    ),
    suite("Supporting Types Round-Trip")(
      test("Owner round-trip") {
        val owner   = Owner.pkgs("zio", "blocks", "schema")
        val schema  = Schema.ownerSchema
        val dynamic = schema.toDynamicValue(owner)
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result == Right(owner))
      },
      test("Variance round-trip") {
        val variance = Variance.Covariant
        val schema   = Schema.varianceSchema
        val dynamic  = schema.toDynamicValue(variance)
        val result   = schema.fromDynamicValue(dynamic)
        assertTrue(result == Right(variance))
      },
      test("TypeBounds round-trip") {
        val bounds  = TypeBounds(Some(TypeRepr.NothingType), Some(TypeRepr.AnyType))
        val schema  = Schema.typeBoundsSchema
        val dynamic = schema.toDynamicValue(bounds)
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result == Right(bounds))
      },
      test("TypeParam round-trip") {
        val param   = TypeParam.covariant("A", 0)
        val schema  = Schema.typeParamSchema
        val dynamic = schema.toDynamicValue(param)
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result == Right(param))
      }
    )
  )
}
