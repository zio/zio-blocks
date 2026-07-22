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

import scala.language.reflectiveCalls
import zio.blocks.schema._
import zio.test._

object StructuralMigrationTypeCheckSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("StructuralMigrationTypeCheckSpec")(
    test("build fails to compile when structural target field is missing") {
      typeCheck {
        """
        import scala.language.reflectiveCalls
        import scala.reflect.Selectable.reflectiveSelectable
        import zio.blocks.schema.Schema
        import zio.blocks.schema.migration.Migration

        type PersonV1 = { def name: String }
        type PersonV2 = { def name: String; def age: Int }

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        Migration.newBuilder[PersonV1, PersonV2].build
        """
      }.map(result => assertTrue(result.isLeft))
    }
  )
}
