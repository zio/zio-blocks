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

package zio.blocks.endpoint

/**
 * Defines several endpoints in one block and returns them as a
 * [[scala.NamedTuple]], so each one is reached by name with its full type.
 * Scala 3 only (3.7 and later).
 *
 * Each statement in the block must be one of:
 *   - `val name = Endpoint(...)`, reached as `.name`;
 *   - a bare `Endpoint(...)`, named after its method and path template, such as
 *     `` .`DELETE /notes/{id}` ``, `` .`GET#|POST /notes` `` or
 *     `` .`* /...` ``;
 *   - `prefix / endpoints { ... }`, a nested group reached by the prefix's
 *     literal text or capture name, as in `.admin` or `.tenant`.
 *
 * Automatic names work for a method followed by one literal or capture, or by
 * one literal and then one capture (`GET / "notes" / PathCodec.int("id")`).
 * Other routes stop the compile with a "cannot auto-name" error that asks for a
 * `val`: a known bug. They include two literals (`GET / "notes" / "latest"`),
 * any `~` segment, and a route that starts from a `RoutePattern` value such as
 * `RoutePattern.any`. Two members with the same name, an endpoint that refers
 * to another member of the same block, a `val` that is not an endpoint, and an
 * expression that is neither an endpoint nor a nested group are compile errors
 * too.
 *
 * A prefix, literal or capturing, goes in front of every route in the group; a
 * capture's value comes first in each endpoint's `PathInput`.
 *
 * {{{
 * // scala-3-only
 * import zio.blocks.endpoint.RoutePattern._
 * import zio.http.Method
 *
 * val notes = "api" / endpoints {
 *   val list = Endpoint(Method.GET / "notes")
 *   Endpoint(Method.DELETE / "notes" / PathCodec.int("id"))
 * }
 *
 * notes.list.route.render                   // GET /api/notes
 * notes.`DELETE /notes/{id}`.route.render   // DELETE /api/notes/{id}
 * }}}
 *
 * @param body
 *   the block of endpoint statements
 * @return
 *   a named tuple with one member per statement: an `Endpoint`, or a named
 *   tuple for a nested group
 * @see
 *   [[https://zioblocks.com/reference/endpoint/bulk-creation/ Bulk Endpoint Creation]]
 */
transparent inline def endpoints(inline body: Any): Any =
  ${ EndpointGroupMacro.build('body) }

extension [A](codec: PathCodec[A]) {

  /**
   * Puts this path in front of every route of an `endpoints { ... }` group,
   * nested groups included; same as `codec / endpoints { ... }`. A capture's
   * value comes first in each endpoint's `PathInput`, so an `Int` prefix on a
   * route that captures an `Int` gives `(Int, Int)`. The group must be written
   * inline; a group stored in a `val` first does not compile.
   *
   * {{{
   * // scala-3-only
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.http.Method
   *
   * val byCustomer = PathCodec.int("customerId").nest(endpoints {
   *   val order = Endpoint(Method.GET / "orders" / PathCodec.int("orderId"))
   * })
   *
   * val order: Endpoint[(Int, Int), Unit, Unit, Unit, AuthType.None] = byCustomer.order
   * order.route.render // GET /{customerId}/orders/{orderId}
   * }}}
   *
   * @param nt
   *   the `endpoints { ... }` group, written inline
   * @return
   *   the group with this path in front of every route
   */
  transparent inline def nest[N <: Tuple, V <: Tuple](inline nt: NamedTuple.NamedTuple[N, V]): Any =
    ${ EndpointGroupMacro.prefixGroupCodec('codec, 'nt) }

  /**
   * Same as `nest`: `PathCodec.int("id") / endpoints { ... }`, or
   * `"api" / endpoints { ... }` for a literal prefix.
   */
  transparent inline def /[N <: Tuple, V <: Tuple](inline nt: NamedTuple.NamedTuple[N, V]): Any =
    nest(nt)
}
