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
  def #|(that: Method): Method =
    if (this == Method.ANY || that == Method.ANY) Method.ANY
    else
      (this, that) match {
        case (Method.Methods(a), Method.Methods(b)) => Method.Methods(a ++ b)
        case (Method.Methods(a), _)                 => Method.Methods(a + that)
        case (_, Method.Methods(b))                 => Method.Methods(b + this)
        case _ if this == that                      => this
        case _                                      => Method.Methods(Set(this, that))
      }

  def matches(that: Method): Boolean =
    if (this == Method.ANY || that == Method.ANY) true
    else
      this match {
        case Method.Methods(methods) => methods.exists(_.matches(that))
        case _                       =>
          that match {
            case Method.Methods(methods) => methods.exists(_.matches(this))
            case _                       => this == that
          }
      }

  override def toString: String = Method.render(this)
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
  case object ANY     extends Method("GET", -1)

  final case class Methods(methods: Set[Method])
      extends Method(methods.toList.sortBy(Method.render).map(Method.render).mkString("#|"), -1)

  val values: Chunk[Method] = Chunk(GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT)

  val standardMethods: Set[Method] = values.iterator.toSet

  private val byName: Map[String, Method] = values.iterator.map(m => m.name -> m).toMap

  def fromString(s: String): Option[Method] = byName.get(s)

  def render(method: Method): String = method match {
    case ANY => "*"
    case _   => method.name
  }
}
