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

package zio.blocks.telemetry

/**
 * Names the value type of an [[AttributeKey]] at run time: one case per type an
 * attribute can hold.
 */
sealed trait AttributeType

/** The eight attribute value types. */
object AttributeType {

  /** `String` values. */
  case object StringType extends AttributeType

  /** `Boolean` values. */
  case object BooleanType extends AttributeType

  /** `Long` values. */
  case object LongType extends AttributeType

  /** `Double` values. */
  case object DoubleType extends AttributeType

  /** `Seq[String]` values. */
  case object StringSeqType extends AttributeType

  /** `Seq[Long]` values. */
  case object LongSeqType extends AttributeType

  /** `Seq[Double]` values. */
  case object DoubleSeqType extends AttributeType

  /** `Seq[Boolean]` values. */
  case object BooleanSeqType extends AttributeType
}
