package zio.blocks.optics

import zio.test._
import zio.optics._

// --- Test Data Definitions ---

final case class Person(name: String, age: Int)
object Person extends DerivedOptics[Person]

sealed trait Status
object Status extends DerivedOptics[Status] {
  case object Active extends Status
  case object Inactive extends Status
  final case class Pending(reason: String) extends Status
}

// Underscore Variant
final case class Config(optics: String)
object Config extends DerivedOptics_[Config]

// Recursion
final case class Node(value: Int, next: Option[Node])
object Node extends DerivedOptics[Node]

object DerivedOpticsSpec extends ZIOSpecDefault {
  
  def spec = suite("DerivedOptics Spec")(
    
    suite("Case Classes (Lenses)")(
      test("Get field value") {
        val p = Person("Alice", 30)
        // zio-optics: get returns Either[Error, A]
        val lens = Person.optics.name
        assertTrue(lens.get(p) == Right("Alice"))
      },
      test("Replace field value") {
        val p = Person("Alice", 30)
        // zio-optics: set(piece)(whole) - curried, piece first
        val p2 = Person.optics.name.set("Bob")(p)
        assertTrue(p2 == Right(Person("Bob", 30)))
      },
      test("Get age field") {
        val p = Person("Alice", 30)
        val ageLens = Person.optics.age
        assertTrue(ageLens.get(p) == Right(30))
      }
    ),

    suite("Sealed Traits (Prisms)")(
      test("Get matching variant - case object") {
        val s: Status = Status.Active
        val prism = Status.optics.active
        assertTrue(prism.get(s) == Right(Status.Active))
      },
      test("Get non-matching variant") {
        val s: Status = Status.Active
        val prism = Status.optics.inactive
        assertTrue(prism.get(s).isLeft)
      },
      test("Get matching case class variant") {
        val s: Status = Status.Pending("waiting")
        val prism = Status.optics.pending
        assertTrue(prism.get(s) == Right(Status.Pending("waiting")))
      }
    ),

    suite("Structural Types")(
      test("Optics object has correct structural type") {
        // Access fields via structural typing - use inline to preserve type
        val nameLens = Person.optics.name
        val ageLens = Person.optics.age
        val p = Person("Test", 25)
        assertTrue(
          nameLens.get(p) == Right("Test") &&
          ageLens.get(p) == Right(25)
        )
      }
    ),
    
    suite("Edge Cases")(
      test("Underscore variant avoids collision") {
        val c = Config("data")
        // Config.optics._optics should exist (underscore prefix for field 'optics')
        val lens = Config.optics._optics
        assertTrue(lens.get(c) == Right("data"))
      }
    )
  )
}