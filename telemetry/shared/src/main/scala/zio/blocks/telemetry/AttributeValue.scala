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
 * An attribute value of any of the eight types, as a case class to match on.
 * [[Logger]] methods take attributes in this form, and `Attributes#foreach`
 * and `Attributes#toMap` return them in it. It mirrors the scalar and array
 * cases of OpenTelemetry's `AnyValue`.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/common/any-value/ AttributeValue]]
 */
sealed trait AttributeValue

/** The eight cases of [[AttributeValue]]. */
object AttributeValue {

  /** A `String` value. */
  final case class StringValue(value: String) extends AttributeValue

  /** A `Boolean` value. */
  final case class BooleanValue(value: Boolean) extends AttributeValue

  /** A `Long` value. */
  final case class LongValue(value: Long) extends AttributeValue

  /** A `Double` value. */
  final case class DoubleValue(value: Double) extends AttributeValue

  /** A `Seq[String]` value. */
  final case class StringSeqValue(value: Seq[String]) extends AttributeValue

  /** A `Seq[Long]` value. */
  final case class LongSeqValue(value: Seq[Long]) extends AttributeValue

  /** A `Seq[Double]` value. */
  final case class DoubleSeqValue(value: Seq[Double]) extends AttributeValue

  /** A `Seq[Boolean]` value. */
  final case class BooleanSeqValue(value: Seq[Boolean]) extends AttributeValue
}
