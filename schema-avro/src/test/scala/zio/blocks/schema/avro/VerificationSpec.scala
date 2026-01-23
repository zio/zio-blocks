package zio.blocks.schema.avro

import zio.test._
import zio.blocks.typeid._

object VerificationSpec extends ZIOSpecDefault {
  // Helpers
  val intId      = TypeId(Owner.parse("scala"), "Int", Nil, TypeDefKind.Class(), Nil)
  val stringId   = TypeId(Owner.parse("java.lang"), "String", Nil, TypeDefKind.Class(), Nil)
  val intType    = TypeRepr.Ref(intId, Nil)
  val stringType = TypeRepr.Ref(stringId, Nil)

  def spec = suite("Final Logical Verification")(
    suite("Canonicalization")(
      test("Union types are order independent") {
        val t1 = TypeRepr.Union(List(intType, stringType))
        val t2 = TypeRepr.Union(List(stringType, intType))
        assertTrue(t1 == t2) &&
        assertTrue(TypeRepr.canonicalize(t1) == TypeRepr.canonicalize(t2))
      },
      test("Intersection types are order independent") {
        val t1 = TypeRepr.Intersection(List(intType, stringType))
        val t2 = TypeRepr.Intersection(List(stringType, intType))
        assertTrue(t1 == t2) &&
        assertTrue(TypeRepr.canonicalize(t1) == TypeRepr.canonicalize(t2))
      }
    ),
    suite("Subtyping")(
      test("Contravariance works") {
        // Consumer[-A]
        val consumerId = TypeId(
          Owner.parse("test"),
          "Consumer",
          List(TypeParam("A", 0, Variance.Contravariant)),
          TypeDefKind.Class(),
          Nil
        )

        // Consumer[Any] <: Consumer[Int] because Int <: Any
        val consumerAny = TypeRepr.Ref(consumerId, List(TypeRepr.AnyType))
        val consumerInt = TypeRepr.Ref(consumerId, List(intType))

        assertTrue(Subtyping.isSubtype(consumerAny, consumerInt))
      },
      test("Covariance works") {
        // Producer[+A]
        val producerId =
          TypeId(Owner.parse("test"), "Producer", List(TypeParam("A", 0, Variance.Covariant)), TypeDefKind.Class(), Nil)

        // Producer[Int] <: Producer[Any]
        val producerAny = TypeRepr.Ref(producerId, List(TypeRepr.AnyType))
        val producerInt = TypeRepr.Ref(producerId, List(intType))

        assertTrue(Subtyping.isSubtype(producerInt, producerAny))
      }
    )
  )
}
