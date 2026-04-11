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

/**
 * A compile-time-friendly expression whose meaning depends on the target
 * [[zio.blocks.schema.Schema]] (unlike [[ValueExpr]], which describes pure
 * value-level transforms).
 *
 * Use [[SchemaExpr.DefaultValue]] with [[MigrationBuilder]] DSL methods such as
 * `addField` and `mandate` when the default should be taken from the target
 * schema. At runtime, [[Migration]] resolves these to concrete
 * [[ValueExpr.Constant]] nodes before executing the [[DynamicMigration]].
 */
sealed trait SchemaExpr extends Product with Serializable

object SchemaExpr {

  /**
   * Use the default value registered on the target schema at the action's path.
   */
  case object DefaultValue extends SchemaExpr
}
