package zio.blocks.schema.migration

import zio.blocks.schema.{ Schema, SchemaExpr }
import zio.blocks.schema.annotation.schema

// ─────────────────────────────────────────────────────────────────────────────
//  Example usage — mirrors the spec's "Typical Workflow"
// ─────────────────────────────────────────────────────────────────────────────

object MigrationExample {

  // ── Step 1: Old schema version as structural type (NO case class needed) ───

  type PersonV0 = { val firstName: String; val lastName: String }
  type PersonV1 = { val name: String; val age: Int }

  // ── Step 2: Current schema as real case class ──────────────────────────────

  @schema
  case class Person(fullName: String, age: Int, country: String)

  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  // Structural schemas (compile-time only, zero runtime overhead)
  implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
  implicit val v1Schema: Schema[PersonV1] = Schema.structural[PersonV1]

  // ── Step 3: Define migrations ──────────────────────────────────────────────

  // V0 → V1: join firstName + lastName into name, add age
  val v0ToV1: Migration[PersonV0, PersonV1] =
    Migration.migrate[PersonV0, PersonV1]
      .joinFields(
        sources  = List(_.firstName, _.lastName),
        target   = _.name,
        combiner = SchemaExpr.map { case Seq(first, last) => s"$first $last" }
      )
      .addField(_.age, SchemaExpr.const(0))
      .build

  // V1 → Current: rename name→fullName, add country
  val v1ToCurrent: Migration[PersonV1, Person] =
    Migration.migrate[PersonV1, Person]
      .renameField(_.name, _.fullName)
      .addField(_.country, SchemaExpr.const("RO"))
      .build

  // V0 → Current (composed):
  val v0ToCurrent: Migration[PersonV0, Person] = v0ToV1 ++ v1ToCurrent

  // ── Step 4: Laws ──────────────────────────────────────────────────────────

  // Identity law: Migration.identity[A].apply(a) == Right(a)
  val identityLaw: Boolean = {
    val id = Migration.identity[Person]
    val p  = Person("Dan", 42, "RO")
    id(p) == Right(p)
  }

  // Associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
  // (checked structurally by comparing action vectors)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Unit tests (zio.blocks test style)
// ─────────────────────────────────────────────────────────────────────────────

object MigrationSpec {

  import DynamicValue.*
  import MigrationAction.*

  // ── DynamicMigration identity ─────────────────────────────────────────────

  def testIdentity(): Unit = {
    val value  = Record(Vector("name" -> Primitive(PrimitiveValue.String("Dan"))))
    val result = DynamicMigration.identity(value)
    assert(result == Right(value), s"Identity failed: $result")
    println("✓ identity law")
  }

  // ── AddField ──────────────────────────────────────────────────────────────

  def testAddField(): Unit = {
    val migration = DynamicMigration(Vector(
      AddField(
        DynamicOptic.field("country"),
        SchemaExpr.constantDynamic(Primitive(PrimitiveValue.String("RO")))
      )
    ))
    val input  = Record(Vector("name" -> Primitive(PrimitiveValue.String("Dan"))))
    val result = migration(input)
    assert(
      result == Right(Record(Vector(
        "name"    -> Primitive(PrimitiveValue.String("Dan")),
        "country" -> Primitive(PrimitiveValue.String("RO"))
      ))),
      s"AddField failed: $result"
    )
    println("✓ AddField")
  }

  // ── DropField ─────────────────────────────────────────────────────────────

  def testDropField(): Unit = {
    val migration = DynamicMigration(Vector(
      DropField(DynamicOptic.field("legacyId"))
    ))
    val input  = Record(Vector(
      "name"     -> Primitive(PrimitiveValue.String("Dan")),
      "legacyId" -> Primitive(PrimitiveValue.Int(99))
    ))
    val result = migration(input)
    assert(
      result == Right(Record(Vector("name" -> Primitive(PrimitiveValue.String("Dan"))))),
      s"DropField failed: $result"
    )
    println("✓ DropField")
  }

  // ── Rename ────────────────────────────────────────────────────────────────

  def testRename(): Unit = {
    val migration = DynamicMigration(Vector(
      Rename(DynamicOptic.field("name"), "fullName")
    ))
    val input  = Record(Vector("name" -> Primitive(PrimitiveValue.String("Dan"))))
    val result = migration(input)
    assert(
      result == Right(Record(Vector("fullName" -> Primitive(PrimitiveValue.String("Dan"))))),
      s"Rename failed: $result"
    )
    println("✓ Rename")
  }

  // ── Reverse ───────────────────────────────────────────────────────────────

  def testReverse(): Unit = {
    val addCountry = DynamicMigration(Vector(
      AddField(
        DynamicOptic.field("country"),
        SchemaExpr.constantDynamic(Primitive(PrimitiveValue.String("RO")))
      )
    ))
    val reversal = addCountry.reverse
    // reverse of AddField is DropField
    assert(
      reversal.actions.head.isInstanceOf[DropField],
      s"Reverse of AddField should be DropField, got: ${reversal.actions}"
    )
    println("✓ reverse structural")
  }

  // ── Composition associativity ─────────────────────────────────────────────

  def testAssociativity(): Unit = {
    val m1 = DynamicMigration(Vector(AddField(DynamicOptic.field("a"), SchemaExpr.DefaultValue)))
    val m2 = DynamicMigration(Vector(Rename(DynamicOptic.field("b"), "c")))
    val m3 = DynamicMigration(Vector(DropField(DynamicOptic.field("d"))))

    val left  = (m1 ++ m2) ++ m3
    val right = m1 ++ (m2 ++ m3)
    assert(left.actions == right.actions, "Associativity violated")
    println("✓ associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)")
  }

  // ── Optionalize / Mandate roundtrip ───────────────────────────────────────

  def testOptionalizeMandateRoundtrip(): Unit = {
    val optic = DynamicOptic.field("email")
    val optionalize = DynamicMigration(Vector(Optionalize(optic)))
    val mandate     = DynamicMigration(Vector(Mandate(optic, SchemaExpr.constantDynamic(
      Primitive(PrimitiveValue.String("fallback@example.com"))
    ))))

    val input = Record(Vector("email" -> Primitive(PrimitiveValue.String("dan@ambiental.ro"))))

    val optResult = optionalize(input)
    assert(
      optResult == Right(Record(Vector("email" -> SomeValue(Primitive(PrimitiveValue.String("dan@ambiental.ro")))))),
      s"Optionalize failed: $optResult"
    )

    val mandateResult = optResult.flatMap(mandate(_))
    assert(
      mandateResult == Right(input),
      s"Mandate roundtrip failed: $mandateResult"
    )

    println("✓ Optionalize → Mandate roundtrip")
  }

  // ── MigrationError: FieldNotFound ─────────────────────────────────────────

  def testFieldNotFound(): Unit = {
    val migration = DynamicMigration(Vector(
      DropField(DynamicOptic.field("nonexistent"))
    ))
    val input  = Record(Vector("name" -> Primitive(PrimitiveValue.String("Dan"))))
    val result = migration(input)
    assert(result.isLeft, s"Expected Left(FieldNotFound), got: $result")
    println("✓ MigrationError.FieldNotFound")
  }

  def runAll(): Unit = {
    testIdentity()
    testAddField()
    testDropField()
    testRename()
    testReverse()
    testAssociativity()
    testOptionalizeMandateRoundtrip()
    testFieldNotFound()
    println("\n✅ All migration tests passed.")
  }
}
