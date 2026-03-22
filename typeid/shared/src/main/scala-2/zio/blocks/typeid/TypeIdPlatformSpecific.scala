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

package zio.blocks.typeid

import zio.blocks.chunk.Chunk

/**
 * Platform-specific methods for TypeId.
 *
 * On JVM, provides reflection-based capabilities. On JS, returns None for
 * reflection operations.
 */
trait TypeIdPlatformSpecific { self: TypeId[_] =>

  /**
   * Returns the `Class` associated with this TypeId, if available.
   *
   * On the JVM, for nominal types, this returns the `Class` object
   * corresponding to this type's fullName. On JS, always returns `None`.
   *
   * Note: This only works for nominal types (not aliases or opaque types). For
   * generic types, returns the erased class (e.g., `List[Int]` returns
   * `classOf[List[_]]`).
   */
  def clazz: Option[Class[_]]

  /**
   * Constructs an instance of the type represented by this TypeId using the
   * provided arguments.
   *
   * On the JVM, this uses reflection to invoke the primary constructor. For
   * known types (collections, java.time, etc.), optimized construction is used.
   *
   * On JS, always returns `Left` with an error message.
   *
   * @param args
   *   the constructor arguments
   * @return
   *   Right with the constructed instance, or Left with an error message
   */
  def construct(args: Chunk[AnyRef]): Either[String, Any]
}
