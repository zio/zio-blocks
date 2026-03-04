package structural

import zio.blocks.schema._
import util.ShowExpr.show

/**
 * Structural Types Reference — Integration
 *
 * Demonstrates how structural types integrate with other ZIO Blocks features,
 * particularly schema evolution (Into macro) and dynamic value manipulation.
 *
 * Run with:
 *   sbt "schema-examples/runMain structural.StructuralIntegrationExample"
 */

object StructuralIntoExample extends App {

  // Two nominally different types with the same shape
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  case class Employee(name: String, age: Int)
  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived[Employee]
  }

  println("=== Structural Types with Into Macro ===\n")

  // Both types have identical structural shape
  val personSchema: Schema[Person] = Schema.derived[Person]
  val employeeSchema: Schema[Employee] = Schema.derived[Employee]

  val personStructural = personSchema.structural
  val employeeStructural = employeeSchema.structural

  println("Both types convert to the same structural shape:")
  show("Person structural type fields")
  val person = Person("Alice", 30)
  val personDynamic = personStructural.toDynamicValue(person)
  show(personDynamic)

  println("\nEmployee structural type fields:")
  show("Employee structural type fields")
  val employee = Employee("Bob", 28)
  val employeeDynamic = employeeStructural.toDynamicValue(employee)
  show(employeeDynamic)

  // Both types share the same structural shape - can be used interchangeably
  println("\n=== Cross-Type Interop via Structural Types ===\n")

  println("Person (nominal) and Employee (nominal) are different types at compile time:")
  println("But they share the same structural representation.")
  println("\nYou can use structural types for duck typing and schema-based interop.")

  // Demonstrate DynamicValue manipulation with structural schemas
  println("\n=== Manipulating Structural Values via DynamicValue ===\n")

  val original = Person("Charlie", 35)
  val originalDynamic = personStructural.toDynamicValue(original)

  println("Original person:")
  show(originalDynamic)

  // Modify age field
  val updated = originalDynamic.set(
    DynamicOptic.root.field("age"),
    DynamicValue.int(36)
  )

  println("\nAfter increasing age by 1:")
  show(updated)

  // Decode back to typed value
  val updatedPerson = personStructural.fromDynamicValue(updated)
  println("\nDecoded updated person:")
  show(updatedPerson)

  // Demonstrate that structural validation ignores nominal type
  println("\n=== Structural Validation Ignores Nominal Type ===\n")

  val anonymousEmployee: { def name: String; def age: Int } = new {
    def name: String = "Diana"
    def age: Int = 27
  }

  // Even though it's an anonymous object, it has the right structural shape
  val anomDynamic = employeeStructural.toDynamicValue(anonymousEmployee)
  println("Anonymous object with employee shape:")
  show(anomDynamic)

  println("\nThis anonymous value can be used wherever an Employee structural type is expected.")
}
