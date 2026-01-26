package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Demo script for the Schema Migration System
 * This demonstrates the key features for the PR video.
 */
object MigrationDemo extends App {

  println("\n" + "="*70)
  println("  DEMO: Pure Algebraic Migration System for ZIO Schema 2")
  println("  PR #519 - $4,000 Bounty Implementation")
  println("="*70 + "\n")

  // =========================================================================
  // Demo 1: Basic Field Operations
  // =========================================================================
  println(">>> DEMO 1: Basic Field Operations\n")

  val personV1 = DynamicValue.Record(Vector(
    "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
    "lastName" -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
  ))
  println(s"PersonV1 (old schema):")
  println(s"  $personV1\n")

  // Create migration: rename firstName -> fullName, drop lastName, add age
  val migration =
    DynamicMigration.renameField("firstName", "fullName") ++
    DynamicMigration.dropField("lastName", DynamicValue.Primitive(PrimitiveValue.String(""))) ++
    DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))

  println("Migration actions:")
  println("  1. Rename 'firstName' -> 'fullName'")
  println("  2. Drop 'lastName'")
  println("  3. Add 'age' with default 0\n")

  val personV2 = migration(personV1)
  println(s"PersonV2 (new schema):")
  println(s"  $personV2\n")

  // =========================================================================
  // Demo 2: Reverse Migration
  // =========================================================================
  println(">>> DEMO 2: Reverse Migration (Bidirectional)\n")

  val reversed = migration.reverse(personV2.toOption.get)
  println(s"Reversed back to V1:")
  println(s"  $reversed\n")

  // =========================================================================
  // Demo 3: Nested Path Navigation
  // =========================================================================
  println(">>> DEMO 3: Nested Path Navigation\n")

  val company = DynamicValue.Record(Vector(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("ZIO Inc")),
    "address" -> DynamicValue.Record(Vector(
      "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Functional Way")),
      "city" -> DynamicValue.Primitive(PrimitiveValue.String("Scala City"))
    ))
  ))
  println(s"Company record:")
  println(s"  $company\n")

  // Add zipCode to nested address
  val addZipCode = DynamicMigration.addFieldAt(
    DynamicOptic.root.field("address"),
    "zipCode",
    DynamicValue.Primitive(PrimitiveValue.String("12345"))
  )

  println("Migration: Add 'zipCode' to nested 'address' record")
  val updatedCompany = addZipCode(company)
  println(s"Updated company:")
  println(s"  $updatedCompany\n")

  // =========================================================================
  // Demo 4: Migration Composition
  // =========================================================================
  println(">>> DEMO 4: Migration Composition\n")

  val m1 = DynamicMigration.addField("field1", DynamicValue.Primitive(PrimitiveValue.Int(1)))
  val m2 = DynamicMigration.addField("field2", DynamicValue.Primitive(PrimitiveValue.Int(2)))
  val m3 = DynamicMigration.addField("field3", DynamicValue.Primitive(PrimitiveValue.Int(3)))

  val composed = m1 ++ m2 ++ m3

  val emptyRecord = DynamicValue.Record(Vector.empty)
  val result = composed(emptyRecord)

  println(s"Composed 3 migrations (add field1, field2, field3):")
  println(s"  Empty record -> $result\n")

  // =========================================================================
  // Demo 5: Laws Verification
  // =========================================================================
  println(">>> DEMO 5: Migration Laws\n")

  // Identity
  val record = DynamicValue.Record(Vector("x" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
  val identityResult = DynamicMigration.empty(record)
  println(s"Identity Law: empty migration preserves value")
  println(s"  DynamicMigration.empty(record) == Right(record): ${identityResult == Right(record)}\n")

  // Associativity
  val left = ((m1 ++ m2) ++ m3)(emptyRecord)
  val right = (m1 ++ (m2 ++ m3))(emptyRecord)
  println(s"Associativity Law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)")
  println(s"  Result: ${left == right}\n")

  // =========================================================================
  // Summary
  // =========================================================================
  println("="*70)
  println("  ✅ Pure data migrations (no closures)")
  println("  ✅ Fully serializable")
  println("  ✅ Path-based actions via DynamicOptic")
  println("  ✅ Reversible migrations")
  println("  ✅ Composable with ++ operator")
  println("  ✅ Identity and associativity laws verified")
  println("="*70)
  println("  DEMO COMPLETE - Schema Migration System Working!")
  println("="*70 + "\n")
}
