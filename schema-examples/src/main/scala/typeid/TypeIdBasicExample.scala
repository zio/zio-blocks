package typeid

import zio.blocks.typeid.*
import util.ShowExpr.show

/**
 * TypeId Basic Example
 *
 * Demonstrates deriving TypeIds for case classes, accessing their properties,
 * and using predefined TypeIds for built-in types.
 *
 * Run with: sbt "schema-examples/runMain typeid.TypeIdBasicExample"
 */

object TypeIdBasicExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("TypeId Basic Example")
  println("═══════════════════════════════════════════════════════════════\n")

  // Derive TypeId for a case class
  case class Person(name: String, age: Int)

  // Simple derivation
  val personId = TypeId.of[Person]

  println("--- Deriving TypeId for Person ---\n")

  // Accessing name and fullName
  println("TypeId.of[Person].name")
  show(personId.name)

  // Accessing fully qualified name
  println("TypeId.of[Person].fullName")
  show(personId.fullName)

  // Checking if it's a case class
  println("TypeId.of[Person].isCaseClass")
  show(personId.isCaseClass)

  // Accessing the owner (package/enclosing type)
  println("TypeId.of[Person].owner")
  show(personId.owner)

  println("\n--- Type Classification ---\n")

  // Check the kind (arity)
  println("TypeId.of[Person].arity")
  show(personId.arity)

  // Check it's a proper type
  println("TypeId.of[Person].isProperType")
  show(personId.isProperType)

  // Check it's not a type constructor
  println("TypeId.of[Person].isTypeConstructor")
  show(personId.isTypeConstructor)

  println("\n--- Predefined TypeIds ---\n")

  // Use predefined TypeIds for built-in types
  println("TypeId.int.fullName")
  show(TypeId.int.fullName)

  println("TypeId.string.fullName")
  show(TypeId.string.fullName)

  println("TypeId.list.name")
  show(TypeId.list.name)

  // List is a type constructor (arity = 1)
  println("TypeId.list.arity")
  show(TypeId.list.arity)

  println("TypeId.map.arity")
  show(TypeId.map.arity)

  println("\n--- Classification Predicates ---\n")

  // Check various classification predicates
  println("TypeId.option.isTypeConstructor")
  show(TypeId.option.isTypeConstructor)

  println("TypeId.either.arity")
  show(TypeId.either.arity)

  println("\n--- Implicit Derivation ---\n")

  // Implicit derivation
  val personIdImplicit: TypeId[Person] = implicitly[TypeId[Person]]

  println("implicitly[TypeId[Person]].name")
  show(personIdImplicit.name)

  println("\n═══════════════════════════════════════════════════════════════")

  show(
    TypeId.of[scala.util.Either].isSum
  )

  show(
    TypeId.of[Option[String]].isOption
  )
  show(
    TypeId.of[Either[String, Int]].isEither
  )

  show(
    TypeId.of[Option[String]].isSum
  )

  show(
    TypeId.of[Either[String, Int]].isSum
  )


  show(
    TypeId.of[(Int, String)].isProduct
  )

  show(
    TypeId.of[Tuple2[Int, String]].isProduct
  )

  show(
    TypeId.of[Product2[Int, String]].isProduct
  )

  show(
    TypeId.of[(Int, String, Boolean)].isTuple
  )


  import zio.blocks.typeid._

  sealed trait Animal

  sealed trait Mammal extends Animal

  case class Dog(name: String) extends Mammal

  case class Cat(name: String) extends Mammal

  case class Fish(species: String) extends Animal


  val dogId = TypeId.of[Dog]
  val mammalId = TypeId.of[Mammal]
  val animalId = TypeId.of[Animal]
  val fishId = TypeId.of[Fish]

  // Direct inheritance: Dog extends Mammal
  dogId.isSubtypeOf(mammalId)

  // Transitive inheritance: Dog extends Mammal extends Animal
  dogId.isSubtypeOf(animalId)

  // Not a subtype relationship
  dogId.isSubtypeOf(fishId)
  fishId.isSubtypeOf(mammalId)

  TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Mammal]])
  TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Animal]])


  show(
    TypeId.of[Mammal => String].isSupertypeOf(TypeId.of[Dog => String])
  )



  // A generic parent type with concrete type arguments
  case class StringList() extends scala.collection.mutable.ListBuffer[String]

  // A type with multiple generic parents
  case class Entry[K, V](key: K, value: V) extends scala.collection.Map[K, V] {
    def iterator = Iterator((key, value))

    def get(k: K) = if (k == key) Some(value) else None

    override def -(key: K): collection.Map[K, V] = ???

    override def -(key1: K, key2: K, keys: K*): collection.Map[K, V] = ???
  }


  val stringListId = TypeId.of[StringList]
  // StringList extends ListBuffer[String] - the type argument is captured
  show(
    stringListId.parents
  )

  val entryId = TypeId.of[Entry[String, Int]]
  // Entry[String, Int] extends Map[String, Int] - type arguments are preserved

  show(
    entryId.parents
  )


  import zio.blocks.typeid._

  // A trait can extend multiple traits
  trait Swimmer {
    def swim(): Unit = ()
  }

  trait Flyer {
    def fly(): Unit = ()
  }

  trait Duck extends Swimmer with Flyer

  // A case class can extend a trait
  case class MallardDuck() extends Duck


  show(
    TypeId.of[MallardDuck].parents
  )


  def isPrimitive(id: TypeId[?]): Boolean =
    id.classTag != scala.reflect.ClassTag.AnyRef

  show {
    isPrimitive(TypeId.of[Int])
    isPrimitive(TypeId.of[Double])
    isPrimitive(TypeId.of[Boolean])
    isPrimitive(TypeId.of[String])
    isPrimitive(TypeId.of[List[Int]])
  }


}
