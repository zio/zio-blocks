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
 * Discriminator for AttributeKey type safety. Determines the expected value
 * type for an attribute.
 */
sealed trait AttributeType

object AttributeType {
  case object StringType     extends AttributeType
  case object BooleanType    extends AttributeType
  case object LongType       extends AttributeType
  case object DoubleType     extends AttributeType
  case object StringSeqType  extends AttributeType
  case object LongSeqType    extends AttributeType
  case object DoubleSeqType  extends AttributeType
  case object BooleanSeqType extends AttributeType
}
