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

package zio.blocks.schema.json

import zio.blocks.chunk.Chunk

/**
 * The kind of a JSON value: null, boolean, number, string, array or object.
 * [[Json#is]], `Json#select` and the typed `as` and `unwrap` take one, and
 * [[MergeStrategy#recurse]] receives one. Each kind is also a predicate on
 * `Json`, so `selection.filter(JsonType.Number)` keeps the numbers.
 *
 * Each case fixes two types:
 *   - `Type`: its [[Json]] case, such as `Json.Object`;
 *   - `Unwrap`: the Scala value inside, such as `Chunk[(String, Json)]`.
 *
 * The cases print as `<function1>`, so error messages that name a kind are
 * unreadable (a known bug).
 */
sealed trait JsonType extends (Json => Boolean) {

  /** The corresponding [[Json]] subtype for this JSON type. */
  type Type <: Json

  /** The underlying Scala type of the value contained in this JSON type. */
  type Unwrap

  /**
   * The rank of this kind in [[Json#compare]]: null 0, boolean 1, number 2,
   * string 3, array 4, object 5.
   */
  def typeIndex: Int

  /** Returns `true` if `json` is of this kind. */
  override def apply(json: Json): Boolean = json.jsonType == this
}

/** The six kinds of JSON value. */
object JsonType {

  /** The kind of [[Json.Null]]; it unwraps to `()`. */
  case object Null extends JsonType {
    override final type Type   = Json.Null.type
    override final type Unwrap = Unit

    /** Always 0. */
    val typeIndex = 0
  }

  /** The kind of [[Json.Boolean]]; it unwraps to a `Boolean`. */
  case object Boolean extends JsonType {
    override final type Type   = Json.Boolean
    override final type Unwrap = scala.Boolean

    /** Always 1. */
    val typeIndex = 1
  }

  /** The kind of [[Json.Number]]; it unwraps to a `BigDecimal`. */
  case object Number extends JsonType {
    override final type Type   = Json.Number
    override final type Unwrap = BigDecimal

    /** Always 2. */
    val typeIndex = 2
  }

  /** The kind of [[Json.String]]; it unwraps to a `String`. */
  case object String extends JsonType {
    override final type Type   = Json.String
    override final type Unwrap = java.lang.String

    /** Always 3. */
    val typeIndex = 3
  }

  /** The kind of [[Json.Array]]; it unwraps to its elements. */
  case object Array extends JsonType {
    override final type Type   = Json.Array
    override final type Unwrap = Chunk[Json]

    /** Always 4. */
    val typeIndex = 4
  }

  /** The kind of [[Json.Object]]; it unwraps to its fields. */
  case object Object extends JsonType {
    override final type Type   = Json.Object
    override final type Unwrap = Chunk[(java.lang.String, Json)]

    /** Always 5. */
    val typeIndex = 5
  }
}
