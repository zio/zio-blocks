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

package ziosschemamigration.migration

import zio.blocks.schema._

/**
 * Scala 2.13 version-specific fixture support for [[MigrationExample]].
 *
 * The examples-side cross-version fixture split mirrors the test-side
 * convention: Scala 2 uses trait-shaped structural stand-ins and Scala 3 uses
 * anonymous refinement types. This file carries the Scala 2 half.
 *
 * The `StructuralReader` trait below is only used by the shared
 * [[MigrationExample]] in a type-position narrowing of schema-derived rows, so
 * the example source file itself stays free of Scala-3-only anonymous
 * refinement syntax.
 */
object MigrationExampleFixtures {

  /** Trait-shaped structural stand-in used by the shared example. */
  trait StructuralReader {
    def firstName: String
    def lastName: String
  }

  private final case class StructuralReaderRow(firstName: String, lastName: String)

  /**
   * Derives a schema for the record form and casts it to the structural-trait
   * surface.
   */
  val structuralReaderSchema: Schema[StructuralReader] =
    Schema.derived[StructuralReaderRow].asInstanceOf[Schema[StructuralReader]]
}
