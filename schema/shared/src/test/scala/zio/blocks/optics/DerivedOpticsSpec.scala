package zio.blocks.optics

import zio.test._
import zio.optics._

// --- Test Data Definitions ---

final case class Person(name: String, age: Int)
object Person extends DerivedOptics[Person]

sealed trait Result[+A]
object Result extends DerivedOptics[Result[_]] {
  final case class Success[A](value: A) extends Result[A]
  object Success extends DerivedOptics[Success[_]]
  
  final case class Failure(error: String) extends Result[Nothing]
  object Failure extends DerivedOptics[Failure]
  
  case object Pending extends Result[Nothing]
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
        assertTrue(Person.optics.name.get(p) == Right("Alice"))
      },
      test("Replace field value") {
        val p = Person("Alice", 30)
        val p2 = Person.optics.name.set(p, "Bob")
        assertTrue(p2 == Right(Person("Bob", 30)))
      },
      test("Composition") {
        // Person.name compose ... (need nested structure for true test)
        assertCompletes
      }
    ),

    suite("Sealed Traits (Prisms)")(
      test("Get matching variant") {
        val r: Result[Int] = Result.Success(42)
        // Note: Scala 2 structural types might need reflection, but here we rely on whitebox macro typing
        val prism = Result.optics.success.asInstanceOf[Prism[Result[Int], Result.Success[Int]]]
        assertTrue(prism.getOption(r) == Some(Result.Success(42)))
      },
      test("Get non-matching variant") {
        val r: Result[Int] = Result.Success(42)
        val prism = Result.optics.failure.asInstanceOf[Prism[Result[Int], Result.Failure]]
        assertTrue(prism.getOption(r) == None)
      }
    ),

    suite("Caching")(
      test("Optics object is cached") {
        val o1 = Person.optics
        val o2 = Person.optics
        assertTrue(o1.eq(o2))
      },
      test("Lens instance is stable") {
        val l1 = Person.optics.name
        val l2 = Person.optics.name
        assertTrue(l1.eq(l2))
      }
    ),
    
    suite("Edge Cases")(
      test("Underscore variant avoids collision") {
        val c = Config("data")
        // Config.optics._optics should exist
        val lens = Config.optics._optics
        assertTrue(lens.get(c) == Right("data"))
      }
    )
  )
}