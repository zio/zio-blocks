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

package zio.blocks.otel

/**
 * Represents the value of an attribute. Each variant corresponds to an
 * AttributeType discriminator.
 *
 * This sealed ADT mirrors OpenTelemetry's AnyValue protobuf message, which
 * supports strings, booleans, 64-bit integers, doubles, and arrays of these
 * types.
 */
sealed trait AttributeValue

object AttributeValue {
  final case class StringValue(value: String)           extends AttributeValue
  final case class BooleanValue(value: Boolean)         extends AttributeValue
  final case class LongValue(value: Long)               extends AttributeValue
  final case class DoubleValue(value: Double)           extends AttributeValue
  final case class StringSeqValue(value: Seq[String])   extends AttributeValue
  final case class LongSeqValue(value: Seq[Long])       extends AttributeValue
  final case class DoubleSeqValue(value: Seq[Double])   extends AttributeValue
  final case class BooleanSeqValue(value: Seq[Boolean]) extends AttributeValue
}
