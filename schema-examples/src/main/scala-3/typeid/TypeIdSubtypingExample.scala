/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package typeid

import zio.blocks.typeid._
import util.ShowExpr.show

/**
 * TypeId Subtyping Example
 *
 * Demonstrates subtype relationships, checking inheritance, and variance-aware
 * subtyping for applied types.
 *
 * Run with: sbt "schema-examples/runMain typeid.TypeIdSubtypingExample"
 */

object TypeIdSubtypingExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("TypeId Subtyping Example")
  println("═══════════════════════════════════════════════════════════════\n")

  // Define a sealed trait hierarchy
  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal
  case object Bird             extends Animal

  println("--- Direct Inheritance ---\n")

  val dogId    = TypeId.of[Dog]
  val animalId = TypeId.of[Animal]

  // Check if Dog is a subtype of Animal
  println("TypeId.of[Dog].isSubtypeOf(TypeId.of[Animal])")
  show(dogId.isSubtypeOf(animalId))

  // Check if Animal is a supertype of Dog
  println("TypeId.of[Animal].isSupertypeOf(TypeId.of[Dog])")
  show(animalId.isSupertypeOf(dogId))

  // Check equivalence
  println("TypeId.of[Dog].isEquivalentTo(TypeId.of[Dog])")
  show(dogId.isEquivalentTo(dogId))

  println("TypeId.of[Dog].isEquivalentTo(TypeId.of[Animal])")
  show(dogId.isEquivalentTo(animalId))

  println("\n--- Transitive Inheritance ---\n")

  val catId = TypeId.of[Cat]

  // Both Dog and Cat are subtypes of Animal
  println("TypeId.of[Cat].isSubtypeOf(TypeId.of[Animal])")
  show(catId.isSubtypeOf(animalId))

  println("TypeId.of[Dog].isSubtypeOf(TypeId.of[Animal]) && TypeId.of[Cat].isSubtypeOf(TypeId.of[Animal])")
  show(dogId.isSubtypeOf(animalId) && catId.isSubtypeOf(animalId))

  println("\n--- Sealed Trait Cases ---\n")

  val birdId = TypeId.of[Bird.type]

  println("TypeId.of[Bird.type].isSubtypeOf(TypeId.of[Animal])")
  show(birdId.isSubtypeOf(animalId))

  println("\n--- Applied Type Subtyping (Covariance) ---\n")

  // List is covariant in its type parameter
  val listDogId    = TypeId.of[List[Dog]]
  val listAnimalId = TypeId.of[List[Animal]]

  // Due to covariance, List[Dog] is a subtype of List[Animal]
  println("TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Animal]])")
  show(listDogId.isSubtypeOf(listAnimalId))

  println("\n--- Applied Type Non-Subtyping (Invariance) ---\n")

  // Array is invariant in its type parameter (on Scala runtime)
  val arrayDogId    = TypeId.of[Array[Dog]]
  val arrayAnimalId = TypeId.of[Array[Animal]]

  println("TypeId.of[Array[Dog]].isSubtypeOf(TypeId.of[Array[Animal]])")
  show(arrayDogId.isSubtypeOf(arrayAnimalId))

  println("\n--- Type Parameters ---\n")

  // Check the type parameters of a generic type
  println("TypeId.of[List[Dog]].typeArgs")
  show(listDogId.typeArgs)

  println("TypeId.of[Map[String, Animal]].typeArgs")
  show(TypeId.of[Map[String, Animal]].typeArgs)

  println("\n═══════════════════════════════════════════════════════════════")
}
