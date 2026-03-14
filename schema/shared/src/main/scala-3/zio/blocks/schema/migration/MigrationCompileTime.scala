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
 * Scala 3 only: static [[Migration.build]] so the macro receives the full builder
 * expression tree. When you write `val x = chain.build`, the compiler binds the
 * receiver to a temporary and the macro only sees a reference; when you write
 * `Migration.build(chain)`, the macro receives the full `chain` AST and can
 * validate it at compile time.
 */
object MigrationCompileTime {
  inline def build[A, B](inline b: MigrationBuilder[A, B]): Migration[A, B] =
    ${ MigrationMacros.buildImpl[A, B]('b) }
}
