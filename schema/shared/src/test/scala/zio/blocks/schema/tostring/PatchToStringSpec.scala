package zio.blocks.schema.tostring

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.patch._

object PatchToStringSpec extends ZIOSpecDefault {

  final case class Address(street: String, city: String)
  object Address extends CompanionOptics[Address] {
    implicit lazy val schema: Schema[Address] = Schema.derived
    val street: Lens[Address, String]         = optic(_.street)
    val city: Lens[Address, String]           = optic(_.city)
  }

  final case class Person(
    name: String,
    age: Int,
    address: Address,
    tags: Vector[String],
    metadata: Map[String, String],
    scores: List[Int]
  )
  object Person extends CompanionOptics[Person] {
    implicit lazy val schema: Schema[Person]        = Schema.derived
    val name: Lens[Person, String]                  = optic(_.name)
    val age: Lens[Person, Int]                      = optic(_.age)
    val address: Lens[Person, Address]              = optic(_.address)
    val tags: Lens[Person, Vector[String]]          = optic(_.tags)
    val metadata: Lens[Person, Map[String, String]] = optic(_.metadata)
    val scores: Lens[Person, List[Int]]             = optic(_.scores)
  }

  def spec = suite("PatchToStringSpec")(
    test("renders simple set operation") {
      val patch = Patch.set(Person.name, "John")
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .name = "John"
            |}""".stripMargin
      )
    },
    test("renders numeric delta") {
      val patch = Patch.increment(Person.age, 5)
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .age += 5
            |}""".stripMargin
      )
    },
    test("renders nested set operation") {
      val patch = Patch.set(Person.address(Address.street), "123 Main St")
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .address.street = "123 Main St"
            |}""".stripMargin
      )
    },
    test("renders sequence append") {
      implicit val d = Patch.CollectionDummy.ForVector
      val patch      = Patch.append(Person.tags, Vector("new"))
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .tags:
            |    + "new"
            |}""".stripMargin
      )
    },
    test("renders list prepend") {
      implicit val d = Patch.CollectionDummy.ForList
      val patch      = Patch.insertAt(Person.scores, 0, List(100))
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .scores:
            |    + [0: 100]
            |}""".stripMargin
      )
    },
    test("renders map add") {
      val patch = Patch.addKey(Person.metadata, "key", "value")
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .metadata:
            |    + {"key": "value"}
            |}""".stripMargin
      )
    },
    test("renders map remove") {
      val patch = Patch.removeKey(Person.metadata, "key")
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .metadata:
            |    - {"key"}
            |}""".stripMargin
      )
    },
    test("renders composite patch") {
      val p1    = Patch.set(Person.name, "John")
      val p2    = Patch.increment(Person.age, 1)
      val p3    = Patch.set(Person.address(Address.city), "New York")
      val patch = p1 ++ p2 ++ p3

      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .name = "John"
            |  .age += 1
            |  .address.city = "New York"
            |}""".stripMargin
      )
    },
    test("renders negative numeric delta") {
      val patch = Patch.increment(Person.age, -5)
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .age -= 5
            |}""".stripMargin
      )
    },
    test("renders sequence delete") {
      implicit val d = Patch.CollectionDummy.ForVector
      val patch      = Patch.deleteAt(Person.tags, 0, 1)
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .tags:
            |    - [0]
            |}""".stripMargin
      )
    },
    test("renders multiple sequence operations") {
      implicit val d = Patch.CollectionDummy.ForList
      val p1         = Patch.insertAt(Person.scores, 0, List(100))
      val p2         = Patch.deleteAt(Person.scores, 2, 1)
      val patch      = p1 ++ p2
      // Note: Multiple operations on same path create separate sections
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .scores:
            |    + [0: 100]
            |  .scores:
            |    - [2]
            |}""".stripMargin
      )
    },
    test("renders complex multi-field composite") {
      implicit val d = Patch.CollectionDummy.ForVector
      val p1         = Patch.set(Person.name, "Alice")
      val p2         = Patch.increment(Person.age, 2)
      val p3         = Patch.set(Person.address(Address.street), "Broadway")
      val p4         = Patch.append(Person.tags, Vector("verified", "premium"))
      val p5         = Patch.addKey(Person.metadata, "status", "active")
      val patch      = p1 ++ p2 ++ p3 ++ p4 ++ p5

      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .name = "Alice"
            |  .age += 2
            |  .address.street = "Broadway"
            |  .tags:
            |    + "verified"
            |    + "premium"
            |  .metadata:
            |    + {"status": "active"}
            |}""".stripMargin
      )
    },
    test("renders deeply nested patch (3 levels)") {
      // Create a 3-level deep schema structure for testing
      case class DeepLevel3(value: Int)
      object DeepLevel3 extends CompanionOptics[DeepLevel3] {
        implicit lazy val schema: Schema[DeepLevel3] = Schema.derived
        val value: Lens[DeepLevel3, Int]             = optic(_.value)
      }

      case class DeepLevel2(nested: DeepLevel3)
      object DeepLevel2 extends CompanionOptics[DeepLevel2] {
        implicit lazy val schema: Schema[DeepLevel2] = Schema.derived
        val nested: Lens[DeepLevel2, DeepLevel3]     = optic(_.nested)
      }

      case class DeepLevel1(name: String, nested: DeepLevel2)
      object DeepLevel1 extends CompanionOptics[DeepLevel1] {
        implicit lazy val schema: Schema[DeepLevel1] = Schema.derived
        val name: Lens[DeepLevel1, String]           = optic(_.name)
        val nested: Lens[DeepLevel1, DeepLevel2]     = optic(_.nested)
      }

      val patch = Patch.set(DeepLevel1.nested(DeepLevel2.nested(DeepLevel3.value)), 42)

      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .nested.nested.value = 42
            |}""".stripMargin
      )
    },
    test("renders multiple operations at different nesting levels") {
      val p1    = Patch.set(Person.name, "Bob")
      val p2    = Patch.set(Person.address(Address.city), "Boston")
      val p3    = Patch.increment(Person.age, 1)
      val patch = p1 ++ p2 ++ p3

      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .name = "Bob"
            |  .address.city = "Boston"
            |  .age += 1
            |}""".stripMargin
      )
    }
  )
}
