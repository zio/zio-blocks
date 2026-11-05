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

package zio.blocks

/**
 * A small asynchronous effect type, `Async[A]`, for Scala 2.13 and Scala 3 on
 * the JVM and Scala.js.
 *
 * Importing this package brings the `Async` type and its operators (`map`,
 * `flatMap`, `catchAll`, `zipWith`, `block`, `start`, ...) into scope, along
 * with the `succeed` and `fail` helpers for an `Async.promise` body and the
 * `when` and `unless` conditionals. Create values with the factories on the
 * `Async` companion: `Async.succeed`, `Async.fail`, `Async.attempt`,
 * `Async.promise` and `Async.start`.
 *
 * {{{
 * val total: Async[Int] = Async.succeed(40).map(_ + 2)
 * total.block // 42
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/async/ Async]]
 */
package object async extends AsyncSyntaxVersionSpecific with AsyncSyntaxPlatformSpecific {

  private[async] val encoding: AsyncEncoding = AsyncEncoding.Instance

  /**
   * A computation that yields an `A` or fails with a `Throwable`: a value that
   * is already there, a failure that has already happened, or a [[Pollable]]
   * that completes later. Use it for asynchronous work that must run on both
   * the JVM and Scala.js without an effect system, such as a callback API, a
   * `Future` or a socket read.
   *
   * `Async` is eager, like `scala.concurrent.Future`: building a value runs its
   * synchronous part at once, and only work that has to wait for something is
   * left pending. A combinator applied to a ready value runs its function on
   * the spot; applied to a pending value, it keeps the function until a driver
   * (`block`, `start`, `toFuture`, ...) settles the value. A ready value is
   * represented by itself, with no wrapper object.
   *
   * A [[Pollable]], and so a [[Completer]], an `Async.Running` or a
   * [[Failure]], can be used wherever an `Async[A]` is expected. A success
   * value that is itself an `Async` is carried as data, one level at a time;
   * `flatten` removes one level.
   *
   * Drive a pending value from one consumer only. Driving the same pending
   * `Async` from two consumers at once, for example `toFuture` and `block`, is
   * undefined and can hang (a known bug); call `start` once and share the
   * `Async.Running` it returns instead.
   *
   * {{{
   * def fetchPrice(symbol: String): Async[BigDecimal] =
   *   Async.attempt(BigDecimal(symbol.length * 10))
   *
   * val quote: Async[String] =
   *   fetchPrice("ZIO")
   *     .zipWith(fetchPrice("SCALA"))(_ + _)
   *     .map(total => "total: " + total)
   *     .catchAll(_ => Async.succeed("no quote"))
   *
   * quote.block // total: 80
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/async/#evaluation-model Evaluation Model]]
   */
  type Async[+A] = encoding.Async[A]
}
