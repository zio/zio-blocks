package zio.blocks.typeid.schema

import zio.test._
import zio.blocks.schema._
import zio.blocks.typeid._

object TypeIdSchemasSpec extends ZIOSpecDefault {
  import TypeIdSchemas._

  def roundtrip[A](value: A)(implicit schema: Schema[A]): Boolean = {
    val dynamic = schema.toDynamicValue(value)
    val result  = schema.fromDynamicValue(dynamic)
    result == Right(value)
  }

  def roundtripTypeId[A](typeId: TypeId[A]): Boolean = {
    val schema  = typeIdSchema.asInstanceOf[Schema[TypeId[A]]]
    val dynamic = schema.toDynamicValue(typeId)
    val result  = schema.fromDynamicValue(dynamic)
    result == Right(typeId)
  }

  def spec = suite("TypeIdSchemasSpec")(
    suite("Variance")(
      test("roundtrip Covariant") {
        val variance: Variance = Variance.Covariant
        assertTrue(roundtrip(variance))
      },
      test("roundtrip Contravariant") {
        val variance: Variance = Variance.Contravariant
        assertTrue(roundtrip(variance))
      },
      test("roundtrip Invariant") {
        val variance: Variance = Variance.Invariant
        assertTrue(roundtrip(variance))
      }
    ),
    suite("Kind")(
      test("roundtrip Type") {
        val kind: Kind = Kind.Type
        assertTrue(roundtrip(kind))
      },
      test("roundtrip Arrow") {
        val kind: Kind = Kind.Arrow(List(Kind.Type), Kind.Type)
        assertTrue(roundtrip(kind))
      }
    ),
    suite("Owner.Segment")(
      test("roundtrip Package") {
        val segment: Owner.Segment = Owner.Package("zio")
        assertTrue(roundtrip(segment))
      },
      test("roundtrip Term") {
        val segment: Owner.Segment = Owner.Term("Schema")
        assertTrue(roundtrip(segment))
      },
      test("roundtrip Type") {
        val segment: Owner.Segment = Owner.Type("TypeId")
        assertTrue(roundtrip(segment))
      }
    ),
    suite("Owner")(
      test("roundtrip empty owner") {
        assertTrue(roundtrip(Owner(Nil)))
      },
      test("roundtrip package path") {
        assertTrue(roundtrip(Owner(List(Owner.Package("zio"), Owner.Package("blocks")))))
      }
    ),
    suite("TypeParam")(
      test("roundtrip simple TypeParam") {
        val typeParam = TypeParam(
          name = "A",
          index = 0,
          variance = Variance.Covariant,
          bounds = TypeBounds(None, None),
          kind = Kind.Type
        )
        assertTrue(roundtrip(typeParam))
      }
    ),
    suite("TypeRepr")(
      test("roundtrip Ref") {
        val typeId             = TypeId.of[String]
        val typeRepr: TypeRepr = TypeRepr.Ref(typeId)
        assertTrue(roundtrip(typeRepr))
      },
      test("roundtrip Applied") {
        val listTypeId         = TypeId.of[List[Int]]
        val typeRepr: TypeRepr = TypeRepr.Ref(listTypeId)
        assertTrue(roundtrip(typeRepr))
      },
      test("roundtrip AnyType") {
        val typeRepr: TypeRepr = TypeRepr.AnyType
        assertTrue(roundtrip(typeRepr))
      },
      test("roundtrip NothingType") {
        val typeRepr: TypeRepr = TypeRepr.NothingType
        assertTrue(roundtrip(typeRepr))
      }
    ),
    suite("TypeDefKind")(
      test("roundtrip Class") {
        val defKind: TypeDefKind = TypeDefKind.Class(
          isFinal = false,
          isAbstract = false,
          isCase = true,
          isValue = false,
          bases = Nil
        )
        assertTrue(roundtrip(defKind))
      },
      test("roundtrip Trait") {
        val defKind: TypeDefKind = TypeDefKind.Trait(
          isSealed = true,
          knownSubtypes = Nil,
          bases = Nil
        )
        assertTrue(roundtrip(defKind))
      },
      test("roundtrip TypeAlias") {
        val defKind: TypeDefKind = TypeDefKind.TypeAlias
        assertTrue(roundtrip(defKind))
      }
    ),
    suite("TypeId")(
      test("roundtrip simple TypeId") {
        val typeId = TypeId.of[String]
        assertTrue(roundtripTypeId(typeId))
      },
      test("roundtrip generic TypeId") {
        val typeId = TypeId.of[List[Int]]
        assertTrue(roundtripTypeId(typeId))
      },
      test("roundtrip Option TypeId") {
        val typeId = TypeId.of[Option[String]]
        assertTrue(roundtripTypeId(typeId))
      }
    )
  )
}
