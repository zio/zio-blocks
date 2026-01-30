/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk

private[chunk] trait ChunkBuilderPlatformSpecific {
  private[chunk] def arrayEquals(arr1: Array[Byte], arr2: Array[Byte], len: Int): Boolean =
    java.util.Arrays.equals(arr1, 0, len, arr2, 0, len)

  private[chunk] def arrayEquals(arr1: Array[Short], arr2: Array[Short], len: Int): Boolean =
    java.util.Arrays.equals(arr1, 0, len, arr2, 0, len)

  private[chunk] def arrayEquals(arr1: Array[Char], arr2: Array[Char], len: Int): Boolean =
    java.util.Arrays.equals(arr1, 0, len, arr2, 0, len)

  private[chunk] def arrayEquals(arr1: Array[Int], arr2: Array[Int], len: Int): Boolean =
    java.util.Arrays.equals(arr1, 0, len, arr2, 0, len)

  private[chunk] def arrayEquals(arr1: Array[Float], arr2: Array[Float], len: Int): Boolean =
    java.util.Arrays.equals(arr1, 0, len, arr2, 0, len)

  private[chunk] def arrayEquals(arr1: Array[Long], arr2: Array[Long], len: Int): Boolean =
    java.util.Arrays.equals(arr1, 0, len, arr2, 0, len)

  private[chunk] def arrayEquals(arr1: Array[Double], arr2: Array[Double], len: Int): Boolean =
    java.util.Arrays.equals(arr1, 0, len, arr2, 0, len)
}
