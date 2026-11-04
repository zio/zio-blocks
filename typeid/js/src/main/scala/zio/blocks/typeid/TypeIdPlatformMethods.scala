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
 * The platform implementation of [[TypeId#clazz]] and [[TypeId#construct]]:
 * reflection on the JVM, nothing on Scala.js.
 */
private[typeid] object TypeIdPlatformMethods {

  /**
   * Returns the runtime class of `id` on the JVM; `None` on Scala.js.
   */
  def getClass(id: TypeId[_]): Option[Class[_]] = None

  /**
   * Creates a value of the type `id` describes from `args` on the JVM; `Left`
   * on Scala.js.
   */
  def construct(@annotation.unused id: TypeId[_], @annotation.unused args: Chunk[AnyRef]): Either[String, Any] =
    Left("Reflective construction is only supported on the JVM platform")
}
