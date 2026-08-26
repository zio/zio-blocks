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

package zio.blocks.projection

import zio.blocks.schema.{Modifier, Schema}
import zio.test._

object EntityPathSpec extends ZIOSpecDefault {

  case class User(@Modifier.id id: String, name: String)
  case class Order(@Modifier.id id: Long, userId: String, total: Double)
  case class Item(@Modifier.id itemId: Int, name: String, price: Double)
  case class NoIdField(name: String, age: Int)
  case class MultipleIdFields(@Modifier.id primaryId: String, secondaryId: String)
  case class SnakeCaseField(@Modifier.id user_id: String, name: String)
  case class FallbackEntity(id: String, name: String)

  def spec: Spec[Any, Any] = suite("EntityPathSpec")(
    suite("Basic derivation")(
      test("derives basePath from @id field name") {
        implicit val schema: Schema[Order] = Schema.derived[Order]
        val ep                             = EntityPath.derived[Order]
        assertTrue(ep.entityIdField == "id")
      },
      test("derives basePath from field name with Id suffix: itemId → items") {
        implicit val schema: Schema[Item] = Schema.derived[Item]
        val ep                            = EntityPath.derived[Item]
        assertTrue(ep.entityIdField == "itemId")
        assertTrue(ep.basePath == "items")
      },
      test("derives basePath for snake_case field: user_id → users") {
        implicit val schema: Schema[SnakeCaseField] = Schema.derived[SnakeCaseField]
        val ep                                      = EntityPath.derived[SnakeCaseField]
        assertTrue(ep.entityIdField == "user_id")
        assertTrue(ep.basePath == "users")
      }
    ),
    suite("Field selection priority")(
      test("field named id wins as fallback when no @Modifier.id") {
        implicit val schema: Schema[FallbackEntity] = Schema.derived[FallbackEntity]
        val ep                                      = EntityPath.derived[FallbackEntity]
        assertTrue(ep.entityIdField == "id")
      },
      test("@Modifier.id annotation wins over fallback id field") {
        implicit val schema: Schema[MultipleIdFields] = Schema.derived[MultipleIdFields]
        val ep                                        = EntityPath.derived[MultipleIdFields]
        assertTrue(ep.entityIdField == "primaryId")
      }
    ),
    suite("Folder name derivation")(
      test("stripIdSuffix: userId → user") {
        assertTrue(EntityPath.stripIdSuffix("userId") == "user")
      },
      test("stripIdSuffix: id → id (too short to strip)") {
        assertTrue(EntityPath.stripIdSuffix("id") == "id")
      },
      test("pluralize: user → users") {
        assertTrue(EntityPath.pluralize("user") == "users")
      },
      test("pluralize: box → boxes") {
        assertTrue(EntityPath.pluralize("box") == "boxes")
      },
      test("deriveFolderName: itemId → items") {
        assertTrue(EntityPath.deriveFolderName("itemId") == "items")
      },
      test("deriveFolderName: userId → users") {
        assertTrue(EntityPath.deriveFolderName("userId") == "users")
      }
    ),
    suite("Explicit construction")(
      test("EntityPath.apply creates with explicit values") {
        val ep = EntityPath[User]("custom_users", "id")
        assertTrue(ep.basePath == "custom_users")
        assertTrue(ep.entityIdField == "id")
      },
      test("EntityPath.derived with explicit path override") {
        implicit val schema: Schema[User] = Schema.derived[User]
        val ep                            = EntityPath.derived[User]("custom_users")
        assertTrue(ep.basePath == "custom_users")
        assertTrue(ep.entityIdField == "id")
      }
    ),
    suite("Error cases")(
      test("derivation throws RuntimeException for entity without @Modifier.id field") {
        implicit val schema: Schema[NoIdField] = Schema.derived[NoIdField]
        val result                             = scala.util.Try(EntityPath.derived[NoIdField])
        assertTrue(result.isFailure)
        assertTrue(result.failed.get.getMessage.contains("must have @Modifier.id field"))
      }
    ),
    suite("toSnakeCase")(
      test("converts camelCase to snake_case") {
        assertTrue(EntityPath.toSnakeCase("userId") == "user_id")
      },
      test("leaves snake_case unchanged") {
        assertTrue(EntityPath.toSnakeCase("user_id") == "user_id")
      }
    )
  )
}
