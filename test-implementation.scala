// This test file demonstrates that the JSON string interpolator:
// 1. Works correctly with stringable types (primitives, temporal, UUID, Currency)
// 2. Works correctly with types that have JsonEncoder[A] implicit instances
// 3. Fails at compile-time with clear error messages for unsupported types

// This file will NOT compile because it contains intentional type errors
// to demonstrate the validation working correctly.

import zio.blocks.schema.json._
import java.time.LocalDate
import java.util.UUID

object ValidationTest {
  def validInterpolations() = {
    // ✅ These WORK - stringable types as keys and values
    val name = "Alice"
    val age = 30
    val active = true
    val balance = 100.50
    val timestamp = LocalDate.now()
    val id = UUID.randomUUID()

    val json1 = json"""{"$name": $age}"""  // String key, Int value ✅
    val json2 = json"""{"key": $active}"""  // String literal key, Boolean value ✅
    val json3 = json"""{"$timestamp": $id}"""  // LocalDate key, UUID value ✅
    val json4 = json"""{"age": $balance}"""  // String literal, BigDecimal ✅
  }

  def invalidInterpolations() = {
    // ❌ These DON'T WORK - unsupported types
    val unsupportedMap = Map("a" -> 1)
    val unsupportedIterable = Iterable(1, 2, 3)
    val unsupportedArray = Array(1, 2)

    // These will fail at compile-time with clear error messages:
    // val json1 = json"""{"key": $unsupportedMap}"""  // ❌ Map has no JsonEncoder[A]
    // val json2 = json"""{"key": $unsupportedIterable}"""  // ❌ Iterable has no JsonEncoder[A]
    // val json3 = json"""{"key": $unsupportedArray}"""  // ❌ Array has no JsonEncoder[A]
    
    // Custom classes also unsupported unless they have JsonEncoder[A] or Schema.derived
    case class Person(name: String, age: Int)
    val person = Person("Alice", 20)
    // val json4 = json"""{"key": $person}"""  // ❌ Person has no JsonEncoder[A]
  }
}
