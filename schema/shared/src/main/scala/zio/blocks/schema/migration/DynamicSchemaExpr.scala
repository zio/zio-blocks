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

import zio.blocks.schema.DynamicValue

/**
 * Pure expression core for migration transforms. All transformations are encoded as
 * algebraic data; no closures or runtime functions. Each case embeds `.inverse` to
 * enforce the best-effort semantic inverse law.
 */
sealed trait DynamicSchemaExpr {
  def inverse: DynamicSchemaExpr
}

object DynamicSchemaExpr {
  case object DefaultValue extends DynamicSchemaExpr {
    def inverse: DynamicSchemaExpr = Fail("Cannot invert a default value extraction")
  }

  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    def inverse: DynamicSchemaExpr = Fail("Cannot invert a literal constant")
  }

  /** Primitive type conversion; typeIds are serializable identifiers (e.g. "Int", "Long"). */
  final case class ConvertPrimitive(fromTypeId: String, toTypeId: String) extends DynamicSchemaExpr {
    def inverse: DynamicSchemaExpr = ConvertPrimitive(toTypeId, fromTypeId)
  }

  /** Explicit forward/backward for custom logic; inverse swaps the two. */
  final case class BiTransform(forward: DynamicSchemaExpr, backward: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def inverse: DynamicSchemaExpr = BiTransform(backward, forward)
  }

  final case class Fail(reason: String) extends DynamicSchemaExpr {
    def inverse: DynamicSchemaExpr = this
  }
}
