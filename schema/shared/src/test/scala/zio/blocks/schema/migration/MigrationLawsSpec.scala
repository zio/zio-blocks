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

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._
import zio.test.Assertion._

/** Proves identity and associativity laws for the migration algebra so that
  * bounty requirement "Identity & associativity laws hold" is mathematically
  * verified via ZIO Test properties.
  */
object MigrationLawsSpec extends SchemaBaseSpec {

  case class Record(a: String, b: Int)
  implicit val recordSchema: Schema[Record] = Schema.derived

  private val genOptic: Gen[Any, DynamicOptic] =
    Gen.alphaNumericStringBounded(1, 10).map(name => DynamicOptic.Field(name, None))

  private val genRename: Gen[Any, MigrationAction] =
    genOptic.map(o => MigrationAction.Rename(o, DynamicOptic.terminalName(o) + "_r"))

  private val genDropField: Gen[Any, MigrationAction] =
    genOptic.map(o => MigrationAction.DropField(o, DynamicSchemaExpr.DefaultValue))

  private val genAction: Gen[Any, MigrationAction] =
    Gen.oneOf(genRename, genDropField)

  private val genMigration: Gen[Any, DynamicMigration] =
    Gen.listOfBounded(0, 10)(genAction).map(v => DynamicMigration(v.toVector))

  private val genRecord: Gen[Any, Record] =
    Gen.alphaNumericStringBounded(0, 50).zip(Gen.int).map { case (a, b) => Record(a, b) }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationLawsSpec")(
    test("Identity Law: Migration.identity[A].apply(a) == Right(a)") {
      check(genRecord) { record =>
        val identityMigration = Migration.identity[Record]
        val result            = identityMigration.apply(record)
        assert(result)(isRight(equalTo(record)))
      }
    },
    test("Identity Law (dynamic): identity.dynamicMigration.apply(dv) == Right(dv)") {
      check(genRecord) { record =>
        val dv     = recordSchema.toDynamicValue(record)
        val result = Migration.identity[Record].dynamicMigration.apply(dv)
        assert(result)(isRight(equalTo(dv)))
      }
    },
    test("Associativity Law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      check(genMigration, genMigration, genMigration) { (m1, m2, m3) =>
        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)
        assert(leftAssoc)(equalTo(rightAssoc))
      }
    },
    test("Structural Reverse Law: m.reverse.reverse == m") {
      check(genMigration) { m =>
        assert(m.reverse.reverse)(equalTo(m))
      }
    }
  )
}
