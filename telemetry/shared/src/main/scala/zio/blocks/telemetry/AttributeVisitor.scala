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
 * Receives the entries of [[Attributes]] from `Attributes#accept`, one method
 * per value type, with `Long`, `Double` and `Boolean` values unboxed.
 *
 * The four scalar methods are abstract. The `Seq` methods do nothing by
 * default, so a visitor that does not override them skips those entries
 * silently.
 */
trait AttributeVisitor {

  /** Receives a `String` entry. */
  def visitString(key: String, value: String): Unit

  /** Receives a `Long` entry. */
  def visitLong(key: String, value: Long): Unit

  /** Receives a `Double` entry. */
  def visitDouble(key: String, value: Double): Unit

  /** Receives a `Boolean` entry. */
  def visitBoolean(key: String, value: Boolean): Unit

  /** Receives a `Seq[String]` entry; does nothing by default. */
  def visitStringSeq(key: String, value: Seq[String]): Unit = ()

  /** Receives a `Seq[Long]` entry; does nothing by default. */
  def visitLongSeq(key: String, value: Seq[Long]): Unit = ()

  /** Receives a `Seq[Double]` entry; does nothing by default. */
  def visitDoubleSeq(key: String, value: Seq[Double]): Unit = ()

  /** Receives a `Seq[Boolean]` entry; does nothing by default. */
  def visitBooleanSeq(key: String, value: Seq[Boolean]): Unit = ()
}
