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

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}

// ---------------------------------------------------------------------------
// V1 — the legacy model living in production databases right now
// ---------------------------------------------------------------------------
case class UserV1(id: Int, name: String, score: Int, tag: TagV1)
sealed trait TagV1
case object BasicV1   extends TagV1
case object PremiumV1 extends TagV1

object UserV1 { implicit val schema: Schema[UserV1] = Schema.derived }
object TagV1  { implicit val schema: Schema[TagV1] = Schema.derived  }

// ---------------------------------------------------------------------------
// V2 — the target model after the migration
//   • name       → fullName   (field rename)
//   • score: Int → score: Long (type widening)
//   • active: Boolean         (new required field, default = false)
//   • tag: TagV1 → tag: TagV2 (enum case rename: PremiumV1 → Gold)
// ---------------------------------------------------------------------------
case class UserV2(id: Int, fullName: String, score: Long, active: Boolean, tag: TagV2)
sealed trait TagV2
case object BasicV2 extends TagV2
case object Gold    extends TagV2

object UserV2 { implicit val schema: Schema[UserV2] = Schema.derived }
object TagV2  { implicit val schema: Schema[TagV2] = Schema.derived  }

// ---------------------------------------------------------------------------
// The migration definition — compile-time validated by .build
// ---------------------------------------------------------------------------
val userMigration: Migration[UserV1, UserV2] =
  Migration
    .newBuilder[UserV1, UserV2]
    .renameField(_.name, _.fullName)
    .changeFieldType(_.score, _.score, DynamicSchemaExpr.ConvertPrimitive("Int", "Long"))
    .addField(_.active, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
    .renameCase(_.tag, "PremiumV1", "Gold")
    .build

// ---------------------------------------------------------------------------
// Runnable demo — `sbt "schemaJVM/runMain zio.blocks.schema.migration.migrationDemo"`
// ---------------------------------------------------------------------------
@main def migrationDemo(): Unit = {

  println("=== ZIO Blocks Schema Migration Demo (issue #519) ===\n")

  // --- 1. Happy-path: full migration forward --------------------------------
  val legacyUser = UserV1(id = 42, name = "Alice", score = 980, tag = PremiumV1)
  println(s"Source (V1): $legacyUser")

  userMigration.apply(legacyUser) match {
    case Right(v2) => println(s"Target (V2): $v2")
    case Left(err) => println(s"ERROR: $err")
  }

  println()

  // --- 2. Reverse migration (best-effort semantic inverse) ------------------
  val modernUser = UserV2(id = 7, fullName = "Bob", score = 500L, active = true, tag = Gold)
  println(s"Source (V2): $modernUser")

  userMigration.reverse.apply(modernUser) match {
    case Right(v1) => println(s"Reversed to (V1): $v1")
    case Left(err) => println(s"Reverse not lossless (expected): $err")
  }

  println()

  // --- 3. Path-qualified error: applying the migration to broken data --------
  // Manually craft a DynamicValue that has the wrong shape so the interpreter
  // returns a MigrationError with full path context.
  import zio.blocks.chunk.Chunk
  val broken = DynamicValue.Record(
    Chunk(
      "id"   -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
      "name" -> DynamicValue.Primitive(PrimitiveValue.String("Eve")),
      // score is missing — interpreter must report the exact path
      "tag" -> DynamicValue.Variant("BasicV1", DynamicValue.Record(Chunk.empty))
    )
  )
  println("Applying migration to structurally broken DynamicValue:")
  userMigration.dynamicMigration.apply(broken) match {
    case Right(_)  => println("  (unexpectedly succeeded)")
    case Left(err) => println(s"  MigrationError → $err")
  }

  println()

  // --- 4. Algebraic laws: identity & associativity (spot-check) -------------
  val id     = Migration.identity[UserV1]
  val result = id.apply(legacyUser)
  println(s"Identity law: Migration.identity.apply(v1) == Right(v1) → ${result == Right(legacyUser)}")

  val dm1   = userMigration.dynamicMigration
  val dm2   = Migration.identity[UserV1].dynamicMigration
  val left  = (dm1 ++ dm2) ++ dm2
  val right = dm1 ++ (dm2 ++ dm2)
  println(s"Associativity law: (m1++m2)++m3 == m1++(m2++m3) → ${left == right}")

  val revrev = dm1.reverse.reverse
  println(s"Structural reverse law: m.reverse.reverse == m → ${revrev == dm1}")

  println("\n=== Demo complete ===")
}
