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

package structural

import zio.blocks.schema._
import util.ShowExpr.show

/**
 * Structural Types Reference — Product Types
 *
 * Demonstrates converting case classes to structural schemas, and working with
 * anonymous objects that match the structural shape.
 *
 * Run with: sbt "schema-examples/runMain
 * structural.StructuralSimpleProductExample" sbt "schema-examples/runMain
 * structural.StructuralNestedProductExample"
 */

// ──────────────────────────────────────────────────────────────────────────
// Simple Product
// ──────────────────────────────────────────────────────────────────────────

object StructuralSimpleProductExample extends App {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  val nominalSchema: Schema[Person] = Schema.derived[Person]
  val structuralSchema              = nominalSchema.structural

  println("=== Simple Product: Person ===\n")

  // Create a nominal Person instance that matches the structural schema shape
  val person: Person = Person("Alice", 30)

  // Encode to DynamicValue
  val dynamic = structuralSchema.toDynamicValue(person)

  println("Structural schema representation of Person:")
  show(dynamic)

  // Decode back
  val decoded = structuralSchema.fromDynamicValue(dynamic)
  println("\nDecoded result:")
  show(decoded)
}

// ──────────────────────────────────────────────────────────────────────────
// Nested Product
// ──────────────────────────────────────────────────────────────────────────

object StructuralNestedProductExample extends App {

  case class Address(street: String, city: String, zip: Int)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived[Address]
  }

  case class Person(name: String, age: Int, address: Address)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  val nominalSchema: Schema[Person] = Schema.derived[Person]
  val structuralSchema              = nominalSchema.structural

  println("=== Nested Product: Person with Address ===\n")

  val person = Person(
    "Bob",
    25,
    Address("123 Main St", "Springfield", 12345)
  )

  // Encode nested structure
  val dynamic = structuralSchema.toDynamicValue(person)

  println("Structural schema with nested address:")
  show(dynamic)

  // Decode
  val decoded = structuralSchema.fromDynamicValue(dynamic)
  println("\nDecoded nested result:")
  show(decoded)

  // Modify nested address using DynamicOptic
  val updated = dynamic.set(
    DynamicOptic.root.field("address").field("city"),
    DynamicValue.string("New York")
  )

  println("\nAfter modifying city field:")
  show(updated)
}
