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

package zio.http.schema

import zio.blocks.schema.PrimitiveType

/**
 * Single shared table of human-readable primitive type names used in
 * string-parsing error messages.
 *
 * Previously each string-to-primitive parser carried its own copy of this
 * table; keep new primitive branches here so every error vocabulary stays in
 * sync.
 */
private[schema] object PrimitiveTypeNames {

  def typeName[A](primitiveType: PrimitiveType[A]): String = primitiveType match {
    case _: PrimitiveType.String     => "String"
    case _: PrimitiveType.Int        => "Int"
    case _: PrimitiveType.Long       => "Long"
    case _: PrimitiveType.Boolean    => "Boolean"
    case _: PrimitiveType.Double     => "Double"
    case _: PrimitiveType.Float      => "Float"
    case _: PrimitiveType.Short      => "Short"
    case _: PrimitiveType.Byte       => "Byte"
    case _: PrimitiveType.BigInt     => "BigInt"
    case _: PrimitiveType.BigDecimal => "BigDecimal"
    case _: PrimitiveType.UUID       => "UUID"
    case _: PrimitiveType.Char       => "Char"
    case _                           => "Unknown"
  }
}
