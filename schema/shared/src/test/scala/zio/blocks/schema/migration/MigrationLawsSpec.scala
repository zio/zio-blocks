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

import zio.blocks.schema._
import zio.test._

object MigrationLawsSpec extends SchemaBaseSpec {

  import MigrationSerializationFixtures._

  private def semanticInverse(value: DynamicValue, migration: DynamicMigration): TestResult =
    assertTrue(migration.apply(value).flatMap(migration.reverse.apply) == Right(value))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationLawsSpec")(
    suite("generatorHealth")(
      test("genDynamicValueDepth3 reaches depth >= 3") {
        check(genDynamicValueDepth3)(value => assertTrue(dynamicValueDepth(value) >= 3))
      } @@ TestAspect.samples(100),
      test("deep generator produces nested record shapes") {
        check(genRecordDynamicValueDepth3)(value => assertTrue(dynamicValueDepth(value) >= 3 && containsRecord(value)))
      } @@ TestAspect.samples(25),
      test("deep generator produces nested sequence shapes") {
        check(genSequenceDynamicValueDepth3)(value =>
          assertTrue(dynamicValueDepth(value) >= 3 && containsSequence(value))
        )
      } @@ TestAspect.samples(25),
      test("deep generator produces nested map shapes") {
        check(genMapDynamicValueDepth3)(value => assertTrue(dynamicValueDepth(value) >= 3 && containsMap(value)))
      } @@ TestAspect.samples(25),
      test("deep generator produces nested variant shapes") {
        check(genVariantDynamicValueDepth3)(value =>
          assertTrue(dynamicValueDepth(value) >= 3 && containsVariant(value))
        )
      } @@ TestAspect.samples(25)
    ),
    suite("identity")(
      test("Migration.identity[DynamicValue] preserves deep values") {
        check(genDynamicValueDepth3)(value => assertTrue(Migration.identity[DynamicValue].apply(value) == Right(value)))
      } @@ TestAspect.samples(100)
    ),
    suite("associativity")(
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) over genDynamicMigration") {
        check(genDynamicMigration, genDynamicMigration, genDynamicMigration) { (m1, m2, m3) =>
          assertTrue(((m1 ++ m2) ++ m3) == (m1 ++ (m2 ++ m3)))
        }
      } @@ TestAspect.samples(100)
    ),
    suite("structuralReverse")(
      test("m.reverse.reverse == m over exhaustive migrations") {
        check(genDynamicMigration)(migration => assertTrue(migration.reverse.reverse == migration))
      } @@ TestAspect.samples(200)
    ),
    suite("semanticInverse")(
      test("reversible witness subset round-trips through reverse") {
        check(genReversibleMigrationWitness) { case (value, migration) =>
          semanticInverse(value, migration)
        }
      } @@ TestAspect.samples(100)
    ),
    suite("emptyIdentity")(
      test("DynamicMigration.empty is identity for ++ on both sides") {
        check(genDynamicMigration) { migration =>
          assertTrue((migration ++ DynamicMigration.empty) == migration) &&
          assertTrue((DynamicMigration.empty ++ migration) == migration)
        }
      } @@ TestAspect.samples(100)
    )
  )
}
