package zio.blocks.schema.migration.demo

import zio.blocks.schema._
import zio.blocks.schema.migration._

/**
 * Demo script for the Schema Migration System.
 * 
 * This demonstrates:
 * 1. Defining old and new schema versions
 * 2. Building a migration using MigrationBuilder
 * 3. Applying migrations to DynamicValue
 * 4. Reversing migrations
 * 5. Migration composition
 * 6. DynamicSchemaExpr transformations
 */
object MigrationDemo extends App {

  // ============================================================
  // STEP 1: Define Schema Versions
  // ============================================================
  
  println("=" * 60)
  println("SCHEMA MIGRATION SYSTEM DEMO")
  println("=" * 60)
  println()
  
  // Version 1: Simple user schema
  case class UserV1(name: String, age: Int)
  object UserV1 { implicit val schema: Schema[UserV1] = Schema.derived }
  
  // Version 2: Added email field
  case class UserV2(name: String, age: Int, email: Option[String])
  object UserV2 { implicit val schema: Schema[UserV2] = Schema.derived }
  
  // Version 3: Renamed 'name' to 'fullName', added 'verified' field
  case class UserV3(fullName: String, age: Int, email: Option[String], verified: Boolean)
  object UserV3 { implicit val schema: Schema[UserV3] = Schema.derived }
  
  println("Defined schema versions:")
  println("  V1: UserV1(name: String, age: Int)")
  println("  V2: UserV2(name: String, age: Int, email: Option[String])")
  println("  V3: UserV3(fullName: String, age: Int, email: Option[String], verified: Boolean)")
  println()
  
  // ============================================================
  // STEP 2: Build Migrations
  // ============================================================
  
  println("=" * 60)
  println("BUILDING MIGRATIONS")
  println("=" * 60)
  println()
  
  // Migration V1 -> V2: Add optional email field
  val v1ToV2: Migration[UserV1, UserV2] = MigrationBuilder[UserV1, UserV2]
    .addField(
      MigrationBuilder.paths.field("email"),
      // Default: None (represented as Unit in DynamicValue for Option)
      DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Unit))
    )
    .buildPartial
  
  println("Migration V1 -> V2: Add email field with default None")
  println(s"  Actions: ${v1ToV2.dynamicMigration.actions}")
  println()
  
  // Migration V2 -> V3: Rename name to fullName, add verified field
  val v2ToV3: Migration[UserV2, UserV3] = MigrationBuilder[UserV2, UserV3]
    .renameField(
      MigrationBuilder.paths.field("name"),
      MigrationBuilder.paths.field("fullName")
    )
    .addField(
      MigrationBuilder.paths.field("verified"),
      DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
    )
    .buildPartial
    
  println("Migration V2 -> V3: Rename 'name' to 'fullName', add 'verified' field")
  println(s"  Actions: ${v2ToV3.dynamicMigration.actions}")
  println()
  
  // ============================================================
  // STEP 3: Apply Migrations
  // ============================================================
  
  println("=" * 60)
  println("APPLYING MIGRATIONS")
  println("=" * 60)
  println()
  
  val userV1 = UserV1("John Doe", 30)
  println(s"Original V1 user: $userV1")
  println()
  
  // Convert to DynamicValue and apply migration
  val dynamicV1 = UserV1.schema.toDynamicValue(userV1)
  println(s"As DynamicValue: $dynamicV1")
  println()
  
  // Apply V1 -> V2 migration
  val dynamicV2Result = v1ToV2.dynamicMigration(dynamicV1)
  println(s"After V1 -> V2 migration: $dynamicV2Result")
  println()
  
  // Apply V2 -> V3 migration  
  dynamicV2Result.foreach { dynamicV2 =>
    val dynamicV3Result = v2ToV3.dynamicMigration(dynamicV2)
    println(s"After V2 -> V3 migration: $dynamicV3Result")
  }
  println()
  
  // ============================================================
  // STEP 4: Compose Migrations
  // ============================================================
  
  println("=" * 60)
  println("COMPOSING MIGRATIONS")
  println("=" * 60)
  println()
  
  // Compose migrations: V1 -> V2 -> V3
  val v1ToV3: Migration[UserV1, UserV3] = v1ToV2 ++ v2ToV3
  
  println("Composed migration V1 -> V3 = V1 -> V2 ++ V2 -> V3")
  println(s"  Total actions: ${v1ToV3.dynamicMigration.actions.length}")
  println()
  
  // Apply composed migration directly
  val directV3Result = v1ToV3.dynamicMigration(dynamicV1)
  println(s"V1 directly to V3: $directV3Result")
  println()
  
  // ============================================================
  // STEP 5: Reverse Migrations
  // ============================================================
  
  println("=" * 60)
  println("REVERSING MIGRATIONS")
  println("=" * 60)
  println()
  
  val v3ToV1 = v1ToV3.reverse
  
  directV3Result.foreach { dynamicV3 =>
    println(s"V3 value: $dynamicV3")
    
    val backToV1 = v3ToV1.dynamicMigration(dynamicV3)
    println(s"Reversed back to V1: $backToV1")
    
    backToV1.foreach { v1Again =>
      println(s"Matches original? ${v1Again == dynamicV1}")
    }
  }
  println()
  
  // ============================================================
  // STEP 6: Transform Values with DynamicSchemaExpr
  // ============================================================
  
  println("=" * 60)
  println("VALUE TRANSFORMATIONS")
  println("=" * 60)
  println()
  
  // Example: Age doubling transformation
  case class AgeRecord(age: Int)
  object AgeRecord { implicit val schema: Schema[AgeRecord] = Schema.derived }
  
  val ageTransformMigration: Migration[AgeRecord, AgeRecord] = MigrationBuilder[AgeRecord, AgeRecord]
    .transformField(
      MigrationBuilder.paths.field("age"),
      // Transform: age * 2
      DynamicSchemaExpr.Arithmetic(
        // TransformValue expressions evaluate on the parent record context,
        // so we reference the field explicitly.
        DynamicSchemaExpr.Path(DynamicOptic.root.field("age")),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
        DynamicSchemaExpr.ArithmeticOperator.Multiply
      ),
      // Reverse: identity for demo
      DynamicSchemaExpr.Path(DynamicOptic.root.field("age"))
    )
    .buildPartial
  
  val ageValue = AgeRecord.schema.toDynamicValue(AgeRecord(25))
  println(s"Original age: $ageValue")
  
  val doubledResult = ageTransformMigration.dynamicMigration(ageValue)
  println(s"After doubling: $doubledResult")
  println()
  
  // ============================================================
  // STEP 7: DynamicSchemaExpr Examples
  // ============================================================
  
  println("=" * 60)
  println("DYNAMICSCHEMAEXPR EXAMPLES")
  println("=" * 60)
  println()
  
  // Literal
  val literal = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
  println(s"Literal: ${literal.eval(DynamicValue.Primitive(PrimitiveValue.Unit))}")
  
  // Path extraction
  val record = DynamicValue.Record(Vector(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
    "age" -> DynamicValue.Primitive(PrimitiveValue.Int(28))
  ))
  val pathExpr = DynamicSchemaExpr.Path(DynamicOptic.root.field("name"))
  println(s"Path extraction from record: ${pathExpr.eval(record)}")
  
  // String concatenation
  val concat = DynamicSchemaExpr.StringConcat(
    DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("Hello, "))),
    DynamicSchemaExpr.Path(DynamicOptic.root.field("name"))
  )
  println(s"String concat: ${concat.eval(record)}")
  
  // Type coercion
  val intValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
  val coerce = DynamicSchemaExpr.CoercePrimitive(
    DynamicSchemaExpr.Literal(intValue),
    "String"
  )
  println(s"Coerce Int to String: ${coerce.eval(DynamicValue.Primitive(PrimitiveValue.Unit))}")
  
  println()
  println("=" * 60)
  println("DEMO COMPLETE")
  println("=" * 60)
}
