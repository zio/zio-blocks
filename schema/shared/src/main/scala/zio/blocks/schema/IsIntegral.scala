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

package zio.blocks.schema

/**
 * Evidence that `A` is `Byte`, `Short`, `Int` or `Long`, the types for which
 * the bitwise operators of [[Optic]], such as `&`, `|`, `<<` and `unary_~`,
 * build a [[SchemaExpr]]. It is sealed: those four types are the only
 * instances.
 */
sealed trait IsIntegral[A] {

  /** The schema of `A`. */
  def schema: Schema[A]
}

/** The four instances of [[IsIntegral]]. */
object IsIntegral {

  /** `Byte` is integral. */
  implicit val IsByte: IsIntegral[Byte] = new IsIntegral[Byte] {
    def schema: Schema[Byte] = Schema[Byte]
  }

  /** `Short` is integral. */
  implicit val IsShort: IsIntegral[Short] = new IsIntegral[Short] {
    def schema: Schema[Short] = Schema[Short]
  }

  /** `Int` is integral. */
  implicit val IsInt: IsIntegral[Int] = new IsIntegral[Int] {
    def schema: Schema[Int] = Schema[Int]
  }

  /** `Long` is integral. */
  implicit val IsLong: IsIntegral[Long] = new IsIntegral[Long] {
    def schema: Schema[Long] = Schema[Long]
  }
}
