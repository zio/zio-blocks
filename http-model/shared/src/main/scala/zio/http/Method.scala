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
 * An HTTP request method. The nine standard methods of RFC 9110 and RFC 5789
 * are case objects in the companion (`Method.GET`, `Method.POST`, ...), each
 * with an `ordinal` from 0 to 8. Two more values serve route matching rather
 * than requests: [[Method.ANY]] matches every method, and [[Method.Methods]] is
 * a set of methods built with `#|`. Both have the ordinal -1. There is no value
 * for a method outside the nine, such as `PROPFIND`.
 *
 * {{{
 * Method.fromString("POST")    // Some(POST)
 * Method.fromString("post")    // None
 * Method.PATCH.ordinal         // 4
 *
 * val writes = Method.POST #| Method.PUT
 * writes.matches(Method.PUT)   // true
 * writes.matches(Method.GET)   // false
 * Method.ANY.matches(Method.GET) // true
 * }}}
 *
 * @param name
 *   the method as written in a request line, such as `GET`. For [[Method.ANY]]
 *   it is `GET`, and for [[Method.Methods]] the sorted names joined with `#|`
 * @param ordinal
 *   0 to 8 for the standard methods, -1 for [[Method.ANY]] and
 *   [[Method.Methods]]
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#method Method]]
 */
sealed abstract class Method(val name: String, val ordinal: Int) {

  /**
   * Combines two methods into one that matches either. Combining with
   * [[Method.ANY]] gives `ANY`, combining a method with itself gives that
   * method, and sets are merged.
   *
   * {{{
   * Method.GET #| Method.HEAD                  // GET#|HEAD
   * (Method.GET #| Method.HEAD) #| Method.GET  // GET#|HEAD
   * Method.GET #| Method.ANY                   // *
   * }}}
   */
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

  /**
   * Whether the two methods overlap: `true` when either is [[Method.ANY]], when
   * a [[Method.Methods]] side contains a method that matches the other side, or
   * when both are the same method. It is symmetric.
   */
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

  /**
   * Returns [[Method.render]] of this method: its name, or `*` for
   * [[Method.ANY]].
   */
  override def toString: String = Method.render(this)
}

/**
 * The standard methods, the [[ANY]] and [[Methods]] values for matching, and
 * parsing.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#predefined-methods Predefined Methods]]
 */
object Method {

  /** `GET`: read a resource. Ordinal 0. */
  case object GET extends Method("GET", 0)

  /** `POST`: send data for the resource to process. Ordinal 1. */
  case object POST extends Method("POST", 1)

  /** `PUT`: create or replace a resource. Ordinal 2. */
  case object PUT extends Method("PUT", 2)

  /** `DELETE`: remove a resource. Ordinal 3. */
  case object DELETE extends Method("DELETE", 3)

  /** `PATCH`: change part of a resource. Ordinal 4. */
  case object PATCH extends Method("PATCH", 4)

  /** `HEAD`: like `GET`, without the response body. Ordinal 5. */
  case object HEAD extends Method("HEAD", 5)

  /**
   * `OPTIONS`: ask which methods and features a resource supports. Ordinal 6.
   */
  case object OPTIONS extends Method("OPTIONS", 6)

  /** `TRACE`: echo the request back, for diagnostics. Ordinal 7. */
  case object TRACE extends Method("TRACE", 7)

  /** `CONNECT`: open a tunnel through a proxy. Ordinal 8. */
  case object CONNECT extends Method("CONNECT", 8)

  /**
   * A wildcard that matches every method, for routes. It renders as `*`, but
   * its `name` is `GET` and its ordinal -1, so do not put it in a request line.
   * It is not in [[values]] and [[fromString]] never returns it.
   */
  case object ANY extends Method("GET", -1)

  /**
   * A set of methods that matches any of them, usually built with `#|`. Its
   * `name` and `toString` are the sorted names joined with `#|`, such as
   * `GET#|POST`, and its ordinal is -1. A set with one method is not equal to
   * that method, though they match each other.
   */
  final case class Methods(methods: Set[Method])
      extends Method(methods.toList.sortBy(Method.render).map(Method.render).mkString("#|"), -1)

  /** The nine standard methods, in ordinal order from `GET` to `CONNECT`. */
  val values: Chunk[Method] = Chunk(GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT)

  /** The nine standard methods, as a set. */
  val standardMethods: Set[Method] = values.iterator.toSet

  private val byName: Map[String, Method] = values.iterator.map(m => m.name -> m).toMap

  /**
   * Returns the standard method named exactly `s`. Matching is case-sensitive
   * and does not trim, so `get`, `" GET"`, `*` and unknown methods give `None`.
   *
   * {{{
   * Method.fromString("DELETE")   // Some(DELETE)
   * Method.fromString("delete")   // None
   * Method.fromString("PROPFIND") // None
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/http-model/model/#parsing-methods Parsing Methods]]
   */
  def fromString(s: String): Option[Method] = byName.get(s)

  /** Returns `*` for [[ANY]] and the `name` of any other method. */
  def render(method: Method): String = method match {
    case ANY => "*"
    case _   => method.name
  }
}
