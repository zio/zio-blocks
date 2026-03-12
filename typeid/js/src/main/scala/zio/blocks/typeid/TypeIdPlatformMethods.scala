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
 * JS-specific implementation of TypeId platform methods.
 *
 * Returns None for all reflection operations since JavaScript does not support
 * Java reflection.
 */
private[typeid] object TypeIdPlatformMethods {
  def getClass(id: TypeId[_]): Option[Class[_]] = None

  def construct(@annotation.unused id: TypeId[_], @annotation.unused args: Chunk[AnyRef]): Either[String, Any] =
    Left("Reflective construction is only supported on the JVM platform")
}
