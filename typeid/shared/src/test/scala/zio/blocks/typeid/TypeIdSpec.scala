package zio.blocks.typeid

import zio.test._

object TypeIdSpec extends ZIOSpecDefault {

  object Aliases {
    type MyInt = Int
  }
  case class Person(name: String, age: Int)

  trait Animal
  trait Dog        extends Animal
  case class Cat() extends Animal

  lazy val intId     = TypeId.of[Int]
  lazy val stringId  = TypeId.of[String]
  lazy val personId  = TypeId.of[Person]
  lazy val myIntId   = TypeId.of[Aliases.MyInt]
  lazy val listIntId = TypeId.of[List[Int]]
  lazy val seqIntId  = TypeId.of[Seq[Int]]
  lazy val dogId     = TypeId.of[Dog]
  lazy val catId     = TypeId.of[Cat]
  lazy val animalId  = TypeId.of[Animal]

  override def spec: Spec[TestEnvironment with zio.Scope, Any] = suite("TypeIdSpec")(
    macroDerivationSuite,
    equalityAndHashingSuite,
    subtypingSuite
  )

  private val macroDerivationSuite = suite("Macro Derivation")(
    nominalTypeTest,
    caseClassTest,
    typeAliasTest
  )

  private def nominalTypeTest = test("nominal type derivation") {
    assertTrue(
      intId.name == "Int",
      intId.owner == Owner.Root / Owner.Package("scala"),
      intId.defKind.isInstanceOf[TypeDefKind.Class],
      intId.defKind.asInstanceOf[TypeDefKind.Class].isValue
    )
  }

  private def caseClassTest = test("case class derivation") {
    assertTrue(
      personId.name == "Person",
      personId.defKind.isInstanceOf[TypeDefKind.Class],
      personId.defKind.asInstanceOf[TypeDefKind.Class].isCase
    )
  }

  private def typeAliasTest = test("type alias derivation") {
    // Type aliases normalize, so after normalization the name becomes the underlying type
    // This is expected behavior - type aliases are transparent
    assertTrue(
      myIntId.isAlias,
      TypeEquality.areEqual(TypeRepr.Ref(myIntId), TypeRepr.Ref(intId)),
      // After normalization, the alias equals its underlying type
      myIntId == intId
    )
  }

  private val equalityAndHashingSuite = suite("Equality and Hashing")(
    nominalEqualityTest,
    appliedEqualityTest,
    mapIdKeyTest,
    setIdMemberTest
  )

  private def nominalEqualityTest = test("structural equality of nominal types") {
    val id1 = intId
    val id2 = TypeId.of[Int] // Small enough
    assertTrue(id1 == id2)
  }

  private def appliedEqualityTest = test("structural equality of applied types") {
    val repr1 = TypeRepr.Applied(TypeRepr.Ref(StandardTypes.IntId), List(TypeRepr.UnitType))
    val repr2 = TypeRepr.Applied(TypeRepr.Ref(StandardTypes.IntId), List(TypeRepr.UnitType))
    assertTrue(TypeEquality.areEqual(repr1, repr2))
  }

  private def mapIdKeyTest = test("TypeId as a Map key") {
    val map = Map(intId -> "success")
    assertTrue(map.get(intId).contains("success"))
  }

  private def setIdMemberTest = test("TypeId as a Set member") {
    val set = Set(stringId)
    assertTrue(set.contains(stringId))
  }

  private val subtypingSuite = suite("Subtyping")(
    anySupertypeTest,
    nothingSubtypeTest,
    listCovarianceTest,
    unappliedTypeConstructorTest,
    inheritanceSubtypingTest,
    customInheritanceTest,
    functionSubtypingTest,
    tupleSubtypingTest,
    setSubtypingTest
  )

  private def anySupertypeTest = test("Any is a supertype of everything") {
    assertTrue(Subtyping.isSubtype(TypeRepr.Ref(StandardTypes.IntId), TypeRepr.AnyType))
  }

  private def nothingSubtypeTest = test("Nothing is a subtype of everything") {
    assertTrue(Subtyping.isSubtype(TypeRepr.NothingType, TypeRepr.Ref(StandardTypes.StringId)))
  }

  private def listCovarianceTest = test("covariance in List") {
    val listInt = TypeRepr.Applied(TypeRepr.Ref(StandardTypes.ListId), List(TypeRepr.Ref(StandardTypes.IntId)))
    val listAny = TypeRepr.Applied(TypeRepr.Ref(StandardTypes.ListId), List(TypeRepr.AnyType))
    assertTrue(Subtyping.isSubtype(listInt, listAny))
  }

  private def unappliedTypeConstructorTest = test("applied type is not a subtype of type constructor") {
    assertTrue(
      !TypeId.of[List[Int]].isSubtypeOf(TypeId.ListTypeId),
      TypeId.of[List[Int]].hashCode != TypeId.ListTypeId.hashCode
    )
  }

  private def inheritanceSubtypingTest = test("inheritance (Standard Library)") {
    // Verify basic subtyping rules work for standard library types
    assertTrue(
      TypeId.of[Int].isSubtypeOf(TypeId.of[Any]),
      TypeId.of[String].isSubtypeOf(TypeId.of[Any]),
      TypeId.of[List[Int]].isSubtypeOf(TypeId.of[Any])
    )
  }

  private def customInheritanceTest = test("inheritance (Custom)") {
    assertTrue(
      Subtyping.isSubtype(TypeRepr.Ref(dogId), TypeRepr.Ref(animalId)),
      Subtyping.isSubtype(TypeRepr.Ref(catId), TypeRepr.Ref(animalId))
    )
  }

  private def functionSubtypingTest = test("function subtyping (variance)") {
    val f1 = TypeRepr.Function(List(TypeRepr.AnyType), TypeRepr.Ref(dogId))
    val f2 = TypeRepr.Function(List(TypeRepr.Ref(dogId)), TypeRepr.Ref(animalId))
    assertTrue(Subtyping.isSubtype(f1, f2))
  }

  private def tupleSubtypingTest = test("tuple subtyping (covariance)") {
    val t1 = TypeRepr.Tuple.named("1" -> TypeRepr.Ref(dogId))
    val t2 = TypeRepr.Tuple.named("1" -> TypeRepr.Ref(animalId))
    assertTrue(Subtyping.isSubtype(t1, t2))
  }

  private def setSubtypingTest = test("Intersection and Union subtyping") {
    val dogAndCat = TypeRepr.Intersection(List(TypeRepr.Ref(dogId), TypeRepr.Ref(catId)))
    val dogOrCat  = TypeRepr.Union(List(TypeRepr.Ref(dogId), TypeRepr.Ref(catId)))
    assertTrue(
      Subtyping.isSubtype(dogAndCat, TypeRepr.Ref(dogId)),
      Subtyping.isSubtype(TypeRepr.Ref(dogId), dogOrCat)
    )
  }
}
