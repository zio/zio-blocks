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

import zio.blocks.streams.io.Reader

private[streams] trait InternalVersionSpecific {
  private[streams] inline def unsafeEvidence[A, B]: (A <:< B) = <:<.refl.asInstanceOf[A <:< B]

  private[streams] def pullInt[A](reader: Reader[A], sentinel: Long): Long        = reader.readInt(sentinel)(using unsafeEvidence)
  private[streams] def pullLong[A](reader: Reader[A], sentinel: Long): Long       = reader.readLong(sentinel)(using unsafeEvidence)
  private[streams] def pullFloat[A](reader: Reader[A], sentinel: Double): Double  = reader.readFloat(sentinel)(using unsafeEvidence)
  private[streams] def pullDouble[A](reader: Reader[A], sentinel: Double): Double = reader.readDouble(sentinel)(using unsafeEvidence)
}
