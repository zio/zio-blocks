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
 * Scala 2 stub: compile-time validation is implemented in Scala 3 via macro.
 * This delegates to buildPartial so call sites compile on 2.13.
 */
object MigrationCompileTime {
  def build[A, B](b: MigrationBuilder[A, B]): Migration[A, B] =
    b.buildPartial
}
