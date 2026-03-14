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
 * Type-safe key for attribute storage. Each key is bound to a specific value
 * type A.
 *
 * AttributeKey instances are created via factory methods on the companion
 * object, which enforce type consistency between key and value.
 *
 * @tparam A
 *   The type of values this key maps to
 */
sealed trait AttributeKey[A] {
  def name: String
  def `type`: AttributeType
}

object AttributeKey {

  /**
   * Creates a typed key for String values.
   */
  def string(name: String): AttributeKey[String] =
    StringKey(name)

  /**
   * Creates a typed key for Boolean values.
   */
  def boolean(name: String): AttributeKey[Boolean] =
    BooleanKey(name)

  /**
   * Creates a typed key for Long values.
   */
  def long(name: String): AttributeKey[Long] =
    LongKey(name)

  /**
   * Creates a typed key for Double values.
   */
  def double(name: String): AttributeKey[Double] =
    DoubleKey(name)

  /**
   * Creates a typed key for Seq[String] values.
   */
  def stringSeq(name: String): AttributeKey[Seq[String]] =
    StringSeqKey(name)

  /**
   * Creates a typed key for Seq[Long] values.
   */
  def longSeq(name: String): AttributeKey[Seq[Long]] =
    LongSeqKey(name)

  /**
   * Creates a typed key for Seq[Double] values.
   */
  def doubleSeq(name: String): AttributeKey[Seq[Double]] =
    DoubleSeqKey(name)

  /**
   * Creates a typed key for Seq[Boolean] values.
   */
  def booleanSeq(name: String): AttributeKey[Seq[Boolean]] =
    BooleanSeqKey(name)

  private final case class StringKey(name: String) extends AttributeKey[String] {
    def `type`: AttributeType = AttributeType.StringType
  }

  private final case class BooleanKey(name: String) extends AttributeKey[Boolean] {
    def `type`: AttributeType = AttributeType.BooleanType
  }

  private final case class LongKey(name: String) extends AttributeKey[Long] {
    def `type`: AttributeType = AttributeType.LongType
  }

  private final case class DoubleKey(name: String) extends AttributeKey[Double] {
    def `type`: AttributeType = AttributeType.DoubleType
  }

  private final case class StringSeqKey(name: String) extends AttributeKey[Seq[String]] {
    def `type`: AttributeType = AttributeType.StringSeqType
  }

  private final case class LongSeqKey(name: String) extends AttributeKey[Seq[Long]] {
    def `type`: AttributeType = AttributeType.LongSeqType
  }

  private final case class DoubleSeqKey(name: String) extends AttributeKey[Seq[Double]] {
    def `type`: AttributeType = AttributeType.DoubleSeqType
  }

  private final case class BooleanSeqKey(name: String) extends AttributeKey[Seq[Boolean]] {
    def `type`: AttributeType = AttributeType.BooleanSeqType
  }
}
