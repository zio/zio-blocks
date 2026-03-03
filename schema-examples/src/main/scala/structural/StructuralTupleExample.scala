package structural

import zio.blocks.schema._
import util.ShowExpr.show

/**
 * Structural Types Reference — Tuples
 *
 * Demonstrates converting tuples to structural schemas. Tuples are converted
 * to records with positional field names (_1, _2, _3, ...).
 *
 * Run with:
 *   sbt "schema-examples/runMain structural.StructuralTupleExample"
 */

object StructuralTupleExample extends App {

  println("=== Structural Tuples ===\n")

  // Tuple of String, Int, Boolean
  val tupleSchema = Schema.derived[(String, Int, Boolean)]
  val structuralSchema = tupleSchema.structural

  val tuple: (String, Int, Boolean) = ("Alice", 30, true)

  println("Simple tuple (String, Int, Boolean):")
  val dynamic1 = structuralSchema.toDynamicValue(tuple)
  show(dynamic1)

  // Nested tuple
  println("\n=== Nested Tuples ===\n")

  val nestedTupleSchema = Schema.derived[((String, Int), (Double, Boolean))]
  val nestedStructural = nestedTupleSchema.structural

  val nestedTuple: ((String, Int), (Double, Boolean)) = (("Bob", 25), (3.14, false))

  println("Nested tuple ((String, Int), (Double, Boolean)):")
  val dynamic2 = nestedStructural.toDynamicValue(nestedTuple)
  show(dynamic2)

  // Decode back
  val decoded = structuralSchema.fromDynamicValue(dynamic1)
  println("\nDecoded simple tuple:")
  show(decoded)
}
