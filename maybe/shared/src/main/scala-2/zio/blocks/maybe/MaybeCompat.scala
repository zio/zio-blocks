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

package zio.blocks.maybe

/**
 * Defines the version-specific type aliases of the `zio.blocks.maybe` package;
 * the package object mixes it in.
 */
trait MaybeCompat extends MaybeSyntaxCompat {

  /**
   * An optional value, like `Option`, that is cheaper to hold: on Scala 3 a
   * present value is stored as the value itself, and absence is one shared
   * object. A `Maybe[A]` is either present, holding an `A` (which may be
   * `null`), or absent.
   *
   * Use it for optional fields and results on hot paths, such as an optional
   * port or timeout in a config, where `Option` allocates a `Some` per value.
   * It has the `Option` operations (`map`, `flatMap`, `filter`, `fold`,
   * `getOrElse`, ...), converts to and from `Option`, and works in
   * for-comprehensions.
   *
   * {{{
   * final case class ServerConfig(port: Int, timeoutSeconds: Maybe[Int])
   *
   * val defaults = ServerConfig(8080, Maybe.absent)
   * val custom   = defaults.copy(timeoutSeconds = Maybe.present(5))
   *
   * defaults.timeoutSeconds.getOrElse(30)        // 30
   * custom.timeoutSeconds.map(_ * 1000).toOption // Some(5000)
   * }}}
   *
   * The encoding depends on the Scala version. On Scala 3, `Maybe[A]` is an
   * opaque type for `A | Absent.type | Present[A]`: a present value is stored
   * as it is (on the JVM a primitive as its box), absence is the object
   * `Absent`, and only a value that would otherwise look absent (`null`, an
   * absent `Maybe`, or a `Present`) is wrapped in a `Present`. On Scala 2,
   * `Maybe[A]` is an alias of the sealed trait `MaybeValue[A]`, and every
   * present value is a `MaybeValue.Present`. So `toString` and `==` differ: on
   * Scala 3 `Maybe.present(8080)` prints as `8080` and equals `8080`, on Scala
   * 2 it prints as `Present(8080)`.
   *
   * To match on a `Maybe`, use `fold`, which works on both versions, or match
   * `case Present(v)` and `case Absent` on Scala 3 and
   * `case MaybeValue.Present(v)` and `case MaybeValue.Absent` on Scala 2.
   *
   * @tparam A
   *   the type of the value
   * @see
   *   [[https://zioblocks.com/reference/maybe/ Maybe]]
   */
  type Maybe[+A] = MaybeValue[A]

  /**
   * The type `withFilter` returns: `MaybeValue.MaybeWithFilter` on Scala 2 and
   * `Maybe.WithFilter` on Scala 3.
   */
  type MaybeWithFilter[A] = MaybeValue.MaybeWithFilter[A]
}
