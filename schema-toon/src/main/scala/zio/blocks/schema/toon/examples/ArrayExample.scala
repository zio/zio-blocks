package zio.blocks.schema.toon.examples

import zio.blocks.schema.Schema
import zio.blocks.schema.toon.{ToonFormat, ToonBinaryCodecDeriver, ArrayFormat}

/**
 * Example demonstrating different array formats in TOON.
 *
 * Run with:
 * `sbt "schema-toon/runMain zio.blocks.schema.toon.examples.ArrayExample"`
 */
object ArrayExample extends App {

  // Simple case class for array of records
  case class Item(id: Int, name: String, price: Double)
  object Item {
    implicit val schema: Schema[Item] = Schema.derived
  }

  val numbers = List(1, 2, 3, 4, 5)
  val strings = List("apple", "banana", "cherry")
  val items   = List(
    Item(1, "Widget", 9.99),
    Item(2, "Gadget", 19.99),
    Item(3, "Gizmo", 29.99)
  )

  println("=== Array Format Examples ===")
  println()

  // 1. Auto format (default)
  println("--- Auto Format (Default) ---")
  println()

  val autoCodec = Schema[List[Int]].derive(ToonFormat.deriver)
  println("List[Int] with Auto:")
  println(autoCodec.encodeToString(numbers))
  println()

  val autoStringCodec = Schema[List[String]].derive(ToonFormat.deriver)
  println("List[String] with Auto:")
  println(autoStringCodec.encodeToString(strings))
  println()

  // 2. Inline format
  println("--- Inline Format ---")
  println()

  val inlineCodec = Schema[List[Int]].derive(
    ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Inline)
  )
  println("List[Int] with Inline:")
  println(inlineCodec.encodeToString(numbers))
  println()

  // 3. List format
  println("--- List Format ---")
  println()

  val listCodec = Schema[List[Item]].derive(
    ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
  )
  println("List[Item] with List format:")
  println(listCodec.encodeToString(items))
  println()

  // 4. Vector and Set
  println("--- Other Collection Types ---")
  println()

  val vectorCodec = Schema[Vector[Int]].derive(ToonFormat.deriver)
  println("Vector[Int]:")
  println(vectorCodec.encodeToString(Vector(10, 20, 30)))
  println()

  val setCodec = Schema[Set[String]].derive(ToonFormat.deriver)
  println("Set[String]:")
  println(setCodec.encodeToString(Set("x", "y", "z")))
  println()

  // 5. Empty arrays
  println("--- Empty Arrays ---")
  println()

  println("Empty List[Int]:")
  println(autoCodec.encodeToString(List.empty))
  println()

  // 6. Nested arrays
  println("--- Nested Arrays ---")
  println()

  val nestedCodec = Schema[List[List[Int]]].derive(ToonFormat.deriver)
  println("List[List[Int]]:")
  println(nestedCodec.encodeToString(List(List(1, 2), List(3, 4, 5))))
}
