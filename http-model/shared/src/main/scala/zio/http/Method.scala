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

package zio.http

import zio.blocks.chunk.Chunk

/**
 * HTTP request method as defined by RFC 9110.
 *
 * Each method has a unique `ordinal` for efficient array-based dispatch.
 */
sealed abstract class Method(val name: String, val ordinal: Int) {
  override def toString: String = name
}

object Method {
  case object GET     extends Method("GET", 0)
  case object POST    extends Method("POST", 1)
  case object PUT     extends Method("PUT", 2)
  case object DELETE  extends Method("DELETE", 3)
  case object PATCH   extends Method("PATCH", 4)
  case object HEAD    extends Method("HEAD", 5)
  case object OPTIONS extends Method("OPTIONS", 6)
  case object TRACE   extends Method("TRACE", 7)
  case object CONNECT extends Method("CONNECT", 8)

  val values: Chunk[Method] = Chunk(GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT)

  private val byName: Map[String, Method] = values.iterator.map(m => m.name -> m).toMap

  def fromString(s: String): Option[Method] = byName.get(s)

  def render(method: Method): String = method.name
}
