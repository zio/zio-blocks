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

/** Interpreter storage lane index (0=Int, 1=Long, 2=Float, 3=Double, 4=Ref). */
type Lane = Int

/** Packed interpreter state in a single Long (56 bits used). */
type StreamState = Long

/** Operation tag for the Interpreter (8-bit value). */
type OpTag = Int

/**
 * Unsafe evidence for internal use where runtime dispatch (jvmType) guarantees
 * correctness.
 */
private[streams] inline def unsafeEvidence[A, B]: (A <:< B) = <:<.refl.asInstanceOf[A <:< B]

/**
 * Internal sentinel object used instead of `null` to detect end-of-stream in
 * the AnyRef lane. Using a dedicated object instead of `null` allows streams to
 * contain `null` elements without them being confused with end-of-stream.
 */
private[streams] val EndOfStream: AnyRef = new AnyRef {
  override def toString: String = "EndOfStream"
}
