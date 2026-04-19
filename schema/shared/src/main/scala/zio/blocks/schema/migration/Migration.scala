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

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Type-safe builder API for Schema Migrations.
 * Internally compounds `DynamicMigration` algebra nodes natively using optics.
 */
class Migration[A, B](val untyped: DynamicMigration) {

  def >>> [C](that: Migration[B, C]): Migration[A, C] =
    new Migration(this.untyped >>> that.untyped)

  def addField(
    optic: DynamicOptic,
    defaultValue: DynamicValue
  ): Migration[A, B] = {
    new Migration(this.untyped >>> DynamicMigration.AddField(optic, defaultValue))
  }

  def deleteField(optic: DynamicOptic): Migration[A, B] = {
    new Migration(this.untyped >>> DynamicMigration.DeleteField(optic))
  }
}

object Migration {
  def identity[A]: Migration[A, A] = new Migration(DynamicMigration.Identity)
}
