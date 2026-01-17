/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema.json

import zio.blocks.schema.SchemaError

/**
 * Represents a JSON Schema for validation.
 *
 * Placeholder - actual implementation TBD.
 */
sealed trait JsonSchema

object JsonSchema {
  private case object EmptySchema extends JsonSchema

  /**
   * An empty schema that accepts all JSON values.
   */
  val any: JsonSchema = EmptySchema

  /**
   * Validates a JSON value against this schema.
   *
   * @param json
   *   The JSON value to validate
   * @param schema
   *   The schema to validate against
   * @return
   *   `None` if valid, `Some(error)` if invalid
   */
  def validate(json: Json, schema: JsonSchema): Option[SchemaError] = None // Placeholder
}
