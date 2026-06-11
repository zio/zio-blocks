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
 * Zero-allocation visitor for iterating over Attributes without boxing
 * primitives.
 *
 * Each `visit*` method is called with the raw unboxed value. Seq variants have
 * default no-op implementations since they are rare.
 */
trait AttributeVisitor {
  def visitString(key: String, value: String): Unit
  def visitLong(key: String, value: Long): Unit
  def visitDouble(key: String, value: Double): Unit
  def visitBoolean(key: String, value: Boolean): Unit
  def visitStringSeq(key: String, value: Seq[String]): Unit   = ()
  def visitLongSeq(key: String, value: Seq[Long]): Unit       = ()
  def visitDoubleSeq(key: String, value: Seq[Double]): Unit   = ()
  def visitBooleanSeq(key: String, value: Seq[Boolean]): Unit = ()
}
