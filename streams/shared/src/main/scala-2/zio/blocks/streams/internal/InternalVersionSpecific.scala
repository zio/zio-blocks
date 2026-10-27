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

package zio.blocks.streams.internal

import zio.blocks.streams.JvmType
import zio.blocks.streams.io.Reader

/**
 * Lane-exact single-element pulls, mixed into the `internal` package object.
 * Each returns `sentinel` at the end; an element equal to `sentinel` is
 * returned as is, so the caller passes on the sentinel of its own pull.
 */
private[streams] trait InternalVersionSpecific {

  /** Pulls an element of the `Int` lane, or of a narrower one, as a `Long`. */
  private[streams] def pullInt[A](reader: Reader.SyncReader[A], sentinel: Long): Long =
    reader.jvmType match {
      case JvmType.Boolean =>
        val value = reader.readBooleanPhysical(-1)
        if (value < 0) sentinel else value.toLong
      case JvmType.Byte =>
        val value = reader.readBytePhysical()
        if (value < 0) sentinel else value.toLong
      case JvmType.Char =>
        val value = reader.readCharPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel else value.toLong
      case JvmType.Short =>
        val value = reader.readShortPhysical(Int.MinValue)
        if (value == Int.MinValue) sentinel else value.toLong
      case _ => reader.readIntPhysical(sentinel)
    }

  /** Pulls an element of the `Long` lane. */
  private[streams] def pullLong[A](reader: Reader.SyncReader[A], sentinel: Long): Long =
    reader.readLongPhysical(sentinel)

  /** Pulls an element of the `Float` lane as a `Double`. */
  private[streams] def pullFloat[A](reader: Reader.SyncReader[A], sentinel: Double): Double =
    reader.readFloatPhysical(sentinel)

  /** Pulls an element of the `Double` lane. */
  private[streams] def pullDouble[A](reader: Reader.SyncReader[A], sentinel: Double): Double =
    reader.readDoublePhysical(sentinel)
}
