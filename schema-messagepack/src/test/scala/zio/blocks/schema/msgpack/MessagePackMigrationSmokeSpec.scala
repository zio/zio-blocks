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

package zio.blocks.schema.msgpack

import zio.blocks.schema.Schema
import zio.blocks.schema.migration.{DynamicMigration, MigrationSerializationFixtures}
import zio.test._

/**
 * MessagePack module-local smoke coverage for the migration system.
 *
 * Exercises the curated migration matrix from the shared
 * `MigrationSerializationFixtures` catalog through the official
 * `MessagePackFormat` deriver. Smoke-only by design: three fixtures, one
 * codec derivation, one round-trip assertion per fixture.
 *
 * The shared JSON property suite (`MigrationSerializationSpec`) remains the
 * authoritative exhaustive proof; this spec only proves the derived
 * `Schema[DynamicMigration]` is format-agnostic for the MessagePack codec.
 */
object MessagePackMigrationSmokeSpec extends ZIOSpecDefault {
  import MigrationSerializationFixtures._

  // Derive the MessagePack codec for DynamicMigration exactly once; the
  // hand-rolled `Reflect.Record` / `Reflect.Variant` graph on
  // `Schema[DynamicMigration]` is format-agnostic, so the same derivation
  // serves every fixture.
  private val codec = Schema[DynamicMigration].derive(MessagePackFormat)

  private def roundTrip(m: DynamicMigration): TestResult = {
    val encoded = codec.encode(m)
    val decoded = codec.decode(encoded)
    assertTrue(decoded == Right(m))
  }

  def spec: Spec[Any, Nothing] = suite("MessagePackMigrationSmokeSpec")(
    test("fixture: simpleRenameAndAdd (straightforward rename + add-field)") {
      roundTrip(simpleRenameAndAdd)
    },
    test("fixture: nestedTransformCase (nested TransformCase at depth >= 2)") {
      roundTrip(nestedTransformCase)
    },
    test("fixture: joinSplit3Paths (Join / Split with 3-path vector)") {
      roundTrip(joinSplit3Paths)
    }
  )
}
