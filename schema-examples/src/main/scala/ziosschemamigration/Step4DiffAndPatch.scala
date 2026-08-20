package ziosschemamigration

import zio.blocks.schema._
import zio.blocks.schema.patch._

/**
 * Migrating from ZIO Schema to ZIO Blocks Schema — Step 4: Diff and Patch
 *
 * This example demonstrates:
 *   - Computing a diff between two values (schema.diff — same call site)
 *   - Applying a patch (schema.patch — error type changes from String to
 *     SchemaError)
 *   - Creating patches programmatically (new in ZIO Blocks; no equivalent in
 *     ZIO Schema)
 *   - Patch composition with ++
 *   - PatchMode.Strict / Lenient / Clobber
 *
 * Run with: sbt "schema-examples/runMain ziosschemamigration.Step4DiffAndPatch"
 */
object Step4DiffAndPatch extends App {

  // ─────────────────────────────────────────────────────────────────────────
  // Domain type
  // ─────────────────────────────────────────────────────────────────────────

  final case class Person(name: String, age: Int)

  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]

    val nameLens: Lens[Person, String] =
      schema.reflect.asRecord.get.lensByName[String]("name").get
    val ageLens: Lens[Person, Int] =
      schema.reflect.asRecord.get.lensByName[Int]("age").get
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Computing a diff
  //    ZIO Schema: Differ.fromSchema(schema).diff(a, b) or schema.diff(a, b)
  //    ZIO Blocks: schema.diff(a, b)  ← same call site
  // ─────────────────────────────────────────────────────────────────────────

  val alice = Person("Alice", 30)
  val bob   = Person("Bob", 31)

  val patch: Patch[Person] = Person.schema.diff(alice, bob)
  println(s"Diff(alice → bob): $patch")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Applying a patch
  //    ZIO Schema: schema.patch(value, patch) returns Either[String, A]
  //    ZIO Blocks: schema.patch(value, patch) returns Either[SchemaError, A]
  //    Or:         patch.apply(value, PatchMode.Strict)
  // ─────────────────────────────────────────────────────────────────────────

  val applied: Either[SchemaError, Person] = Person.schema.patch(alice, patch)
  println(s"Patch applied:     $applied")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Creating patches programmatically (new in ZIO Blocks)
  //    ZIO Schema has no structured API for this; you had to produce a Patch
  //    only by diffing two values.
  // ─────────────────────────────────────────────────────────────────────────

  val renamePatch: Patch[Person] = Patch.set(Person.nameLens, "Charlie")
  val agePatch: Patch[Person]    = Patch.set(Person.ageLens, 25)

  println(s"\nRename patch:    $renamePatch")
  println(s"Age patch:       $agePatch")

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Composing patches
  // ─────────────────────────────────────────────────────────────────────────

  val combined: Patch[Person] = renamePatch ++ agePatch

  val charlie: Either[SchemaError, Person] = combined.apply(alice, PatchMode.Strict)
  println(s"\nCombined patch result: $charlie")

  // ─────────────────────────────────────────────────────────────────────────
  // 5. PatchMode variants
  //    Strict  — fail if any operation fails
  //    Lenient — skip failed operations, apply the rest
  //    Clobber — allow overwrite on type conflicts
  // ─────────────────────────────────────────────────────────────────────────

  val empty: Patch[Person] = Patch.empty[Person]
  println(s"\nEmpty patch:     $empty")
  println(s"isEmpty:         ${empty.isEmpty}")

  val strictResult  = combined.apply(alice, PatchMode.Strict)
  val lenientResult = combined.apply(alice, PatchMode.Lenient)
  println(s"Strict result:   $strictResult")
  println(s"Lenient result:  $lenientResult")

  // ─────────────────────────────────────────────────────────────────────────
  // 6. DynamicValue-level diff (new in ZIO Blocks)
  //    DynamicValue.diff produces a DynamicPatch without a Schema
  // ─────────────────────────────────────────────────────────────────────────

  val dv1     = Person.schema.toDynamicValue(alice)
  val dv2     = Person.schema.toDynamicValue(bob)
  val dvPatch = dv1.diff(dv2)
  println(s"\nDynamicValue diff: $dvPatch")

  val dvPatched = dvPatch(dv1, PatchMode.Strict)
  println(s"DynamicValue patched: $dvPatched")
}
