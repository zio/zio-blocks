package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Phase 2: Typed Patch[A] Wrapper
 *
 * These tests verify the typed Patch API that wraps DynamicPatch, including the
 * new PRD methods (set, empty) and backward compatibility with existing replace
 * methods.
 */
object TypedPatchSpec extends ZIOSpecDefault {

  // Test data types
  case class Person(name: String, age: Int, address: Address)
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
    val name: Lens[Person, String]      = optic(_.name)
    val age: Lens[Person, Int]          = optic(_.age)
    val address: Lens[Person, Address]  = optic(_.address)
    val street: Lens[Person, String]    = optic(_.address.street)
  }

  case class Address(street: String, city: String, zip: Int)
  object Address extends CompanionOptics[Address] {
    implicit val schema: Schema[Address] = Schema.derived
    val street: Lens[Address, String]    = optic(_.street)
    val city: Lens[Address, String]      = optic(_.city)
    val zip: Lens[Address, Int]          = optic(_.zip)
  }

  sealed trait Animal
  object Animal extends CompanionOptics[Animal] {
    implicit val schema: Schema[Animal]   = Schema.derived
    val dog: Prism[Animal, Dog]           = optic(_.when[Dog])
    val cat: Prism[Animal, Cat]           = optic(_.when[Cat])
    val dogName: Optional[Animal, String] = optic(_.when[Dog].name)
    val catLives: Optional[Animal, Int]   = optic(_.when[Cat].lives)
  }

  case class Dog(name: String, breed: String) extends Animal
  object Dog {
    implicit val schema: Schema[Dog] = Schema.derived
  }

  case class Cat(name: String, lives: Int) extends Animal
  object Cat {
    implicit val schema: Schema[Cat] = Schema.derived
  }

  case class Team(name: String, members: List[Person])
  object Team extends CompanionOptics[Team] {
    implicit val schema: Schema[Team]           = Schema.derived
    val name: Lens[Team, String]                = optic(_.name)
    val members: Lens[Team, List[Person]]       = optic(_.members)
    val allMembers: Traversal[Team, Person]     = optic(_.members.each)
    val allMemberNames: Traversal[Team, String] = optic(_.members.each.name)
  }

  // Sample data
  val sampleAddress     = Address("123 Main St", "NYC", 10001)
  val samplePerson      = Person("Alice", 30, sampleAddress)
  val sampleDog: Animal = Dog("Rex", "German Shepherd")
  val sampleCat: Animal = Cat("Whiskers", 9)
  val sampleTeam        = Team(
    "Engineering",
    List(
      Person("Alice", 30, Address("123 Main St", "NYC", 10001)),
      Person("Bob", 25, Address("456 Oak Ave", "LA", 90001))
    )
  )

  def spec: Spec[TestEnvironment, Any] = suite("TypedPatchSpec")(
    suite("Patch.empty")(
      test("creates an empty patch") {
        val patch = Patch.empty[Person]
        assertTrue(patch.isEmpty)
      },
      test("empty patch is identity") {
        val patch  = Patch.empty[Person]
        val result = patch(samplePerson)
        assertTrue(result == samplePerson)
      },
      test("empty patch composition is identity") {
        val patch1    = Patch.set(Person.name, "Bob")
        val patch2    = Patch.empty[Person]
        val combined1 = patch1 ++ patch2
        val combined2 = patch2 ++ patch1
        assertTrue(
          combined1(samplePerson) == patch1(samplePerson),
          combined2(samplePerson) == patch1(samplePerson)
        )
      }
    ),
    suite("Patch.set with Lens")(
      test("sets field value") {
        val patch  = Patch.set(Person.name, "Bob")
        val result = patch(samplePerson)
        assertTrue(result.name == "Bob")
      },
      test("sets nested field value") {
        val patch  = Patch.set(Person.street, "456 Oak Ave")
        val result = patch(samplePerson)
        assertTrue(result.address.street == "456 Oak Ave")
      },
      test("preserves other fields") {
        val patch  = Patch.set(Person.name, "Bob")
        val result = patch(samplePerson)
        assertTrue(
          result.age == samplePerson.age,
          result.address == samplePerson.address
        )
      }
    ),
    suite("Patch.set with Optional")(
      test("sets optional value when case matches") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch(sampleDog)
        assertTrue(result == Dog("Max", "German Shepherd"))
      },
      test("returns original when case doesn't match") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch(sampleCat)
        // With Lenient mode (used by apply(s)), non-matching case returns original
        assertTrue(result == sampleCat)
      },
      test("applyOption returns None when case doesn't match") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch.applyOption(sampleCat)
        assertTrue(result.isEmpty)
      }
    ),
    suite("Patch.set with Prism")(
      test("replaces variant case") {
        val newDog = Dog("Buddy", "Labrador")
        val patch  = Patch.set(Animal.dog, newDog)
        val result = patch(sampleDog)
        assertTrue(result == newDog)
      },
      test("returns original when case doesn't match (lenient)") {
        val newDog = Dog("Buddy", "Labrador")
        val patch  = Patch.set(Animal.dog, newDog)
        val result = patch(sampleCat)
        // apply(s) uses Lenient mode
        assertTrue(result == sampleCat)
      }
    ),
    suite("Patch.set with Traversal")(
      test("sets all elements") {
        val patch  = Patch.set(Team.allMemberNames, "Anonymous")
        val result = patch(sampleTeam)
        assertTrue(
          result.members.forall(_.name == "Anonymous"),
          result.members.length == 2
        )
      },
      test("handles empty sequence") {
        val emptyTeam = Team("Empty", Nil)
        val patch     = Patch.set(Team.allMemberNames, "Anonymous")
        val result    = patch(emptyTeam)
        assertTrue(result == emptyTeam)
      }
    ),
    suite("Patch composition")(
      test("combines multiple patches") {
        val patch1   = Patch.set(Person.name, "Bob")
        val patch2   = Patch.set(Person.age, 35)
        val combined = patch1 ++ patch2
        val result   = combined(samplePerson)
        assertTrue(
          result.name == "Bob",
          result.age == 35,
          result.address == samplePerson.address
        )
      },
      test("applies patches in order") {
        val patch1   = Patch.set(Person.name, "Bob")
        val patch2   = Patch.set(Person.name, "Charlie")
        val combined = patch1 ++ patch2
        val result   = combined(samplePerson)
        assertTrue(result.name == "Charlie")
      },
      test("composition is associative") {
        val patch1 = Patch.set(Person.name, "Bob")
        val patch2 = Patch.set(Person.age, 35)
        val patch3 = Patch.set(Person.street, "789 Pine Rd")

        val leftAssoc  = (patch1 ++ patch2) ++ patch3
        val rightAssoc = patch1 ++ (patch2 ++ patch3)

        val resultLeft  = leftAssoc(samplePerson)
        val resultRight = rightAssoc(samplePerson)

        assertTrue(resultLeft == resultRight)
      }
    ),
    suite("apply variants")(
      test("apply(s) returns patched value") {
        val patch          = Patch.set(Person.name, "Bob")
        val result: Person = patch(samplePerson)
        assertTrue(result.name == "Bob")
      },
      test("apply(s, mode) with Strict mode") {
        val patch  = Patch.set(Person.name, "Bob")
        val result = patch(samplePerson, PatchMode.Strict)
        assertTrue(result == Right(Person("Bob", 30, sampleAddress)))
      },
      test("applyOption returns Some on success") {
        val patch  = Patch.set(Person.name, "Bob")
        val result = patch.applyOption(samplePerson)
        assertTrue(result == Some(Person("Bob", 30, sampleAddress)))
      },
      test("applyOption returns None on failure") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch.applyOption(sampleCat)
        assertTrue(result.isEmpty)
      },
      test("apply returns Right on success in Strict mode") {
        val patch  = Patch.set(Person.name, "Bob")
        val result = patch.apply(samplePerson, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("apply returns Left with error on failure in Strict mode") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch.apply(sampleCat, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("PatchMode behavior")(
      test("Strict mode fails on case mismatch") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch(sampleCat, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Lenient mode skips failed operations") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch(sampleCat, PatchMode.Lenient)
        assertTrue(result == Right(sampleCat))
      },
      test("Clobber mode overwrites on conflicts") {
        // For Set operations, Clobber behaves like Lenient for navigation failures
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch(sampleCat, PatchMode.Clobber)
        assertTrue(result == Right(sampleCat))
      }
    ),
    suite("backward compatibility - replace methods")(
      test("replace with Lens works") {
        val patch  = Patch.replace(Person.name, "Bob")
        val result = patch(samplePerson)
        assertTrue(result.name == "Bob")
      },
      test("replace with Optional works") {
        val patch  = Patch.replace(Animal.dogName, "Max")
        val result = patch(sampleDog)
        assertTrue(result == Dog("Max", "German Shepherd"))
      },
      test("replace with Prism works") {
        val newDog = Dog("Buddy", "Labrador")
        val patch  = Patch.replace(Animal.dog, newDog)
        val result = patch(sampleDog)
        assertTrue(result == newDog)
      },
      test("replace with Traversal works") {
        val patch  = Patch.replace(Team.allMemberNames, "Anonymous")
        val result = patch(sampleTeam)
        assertTrue(result.members.forall(_.name == "Anonymous"))
      }
    ),
    suite("error messages")(
      test("error contains path information") {
        val patch  = Patch.set(Animal.dogName, "Max")
        val result = patch.apply(sampleCat, PatchMode.Strict)
        result match {
          case Left(error) =>
            assertTrue(error.message.contains("Cat") || error.message.contains("Dog"))
          case Right(_) =>
            assertTrue(false)
        }
      }
    ),
    suite("edge cases")(
      test("handles unicode strings") {
        val patch  = Patch.set(Person.name, "日本語")
        val result = patch(samplePerson)
        assertTrue(result.name == "日本語")
      },
      test("handles empty strings") {
        val patch  = Patch.set(Person.name, "")
        val result = patch(samplePerson)
        assertTrue(result.name == "")
      },
      test("handles very long strings") {
        val longName = "A" * 10000
        val patch    = Patch.set(Person.name, longName)
        val result   = patch(samplePerson)
        assertTrue(result.name == longName)
      },
      test("handles negative numbers") {
        val patch  = Patch.set(Person.age, -5)
        val result = patch(samplePerson)
        assertTrue(result.age == -5)
      },
      test("handles zero") {
        val patch  = Patch.set(Person.age, 0)
        val result = patch(samplePerson)
        assertTrue(result.age == 0)
      }
    )
  )
}
