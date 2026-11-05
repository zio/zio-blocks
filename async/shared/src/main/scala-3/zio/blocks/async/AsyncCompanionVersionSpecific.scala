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

package zio.blocks.async

/**
 * The Scala 3 part of the [[Async]] companion: `promise`, `attempt` and
 * `async`. The `promise` body is a context function, so the `succeed` and
 * `fail` helpers find the [[Completer]] without a binder:
 *
 * {{{
 * Async.promise[Int] { succeed(42) }.block // 42
 * }}}
 */
private[async] trait AsyncCompanionVersionSpecific {

  /**
   * Creates an [[Async]] that a [[Completer]] completes: `body` receives the
   * completer and calls `succeed` or `fail` on it, at once or later from a
   * callback. `body` runs at once, on the calling thread. If it completes the
   * completer before returning, the result is ready; otherwise it stays pending
   * until the completer completes. A throw from `body` escapes at the call
   * site.
   *
   * The body's shape differs by Scala version. On Scala 2 it is a plain
   * function: call the completer (`c => c.succeed(v)`), or mark it implicit to
   * use the `succeed` and `fail` helpers (`implicit c => succeed(v)`); a body
   * that calls the helpers without binding the completer does not compile on
   * Scala 2 (a known bug). On Scala 3 it is a context function: call
   * `succeed(v)` and `fail(t)` directly, or bind the completer with `c ?=>`. On
   * Scala 3 a body written `(c: Completer[A]) => ...` compiles, but its
   * statements never run and the `Async` never completes; the compiler only
   * warns (E190). This is a known bug.
   *
   * Cancelling a run of the result stops the driver but does not reach the
   * callback source, because the `Completer` ignores cancellation (a known
   * bug).
   *
   * {{{
   * // scala-2-only
   * def lookupUser(id: Int)(callback: Either[Throwable, String] => Unit): Unit =
   *   callback(Right("user-" + id))
   *
   * val user: Async[String] = Async.promise[String] { implicit c =>
   *   lookupUser(7) {
   *     case Right(name) => succeed(name)
   *     case Left(error) => fail(error)
   *   }
   * }
   * user.block // user-7
   * }}}
   *
   * {{{
   * // scala-3-only
   * def lookupUser(id: Int)(callback: Either[Throwable, String] => Unit): Unit =
   *   callback(Right("user-" + id))
   *
   * val user: Async[String] = Async.promise[String] {
   *   lookupUser(7) {
   *     case Right(name) => succeed(name)
   *     case Left(error) => fail(error)
   *   }
   * }
   * user.block // user-7
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/async/#callback-bridge Callback Bridge]]
   */
  inline def promise[A](inline body: Completer[A] ?=> Unit): Async[A] =
    Async.promiseInternal[A](c => body(using c))

  /**
   * Runs `body` at once, on the calling thread, and returns its value as a
   * ready [[Async]], or a failed one if it throws. It catches every
   * `Throwable`, fatal errors included. Use it to bring code that throws into a
   * chain that `catchAll` can recover. On Scala 3 it is an `inline def`, so no
   * function object is allocated for `body`.
   *
   * {{{
   * val port: Async[Int] = Async.attempt("8080".toInt)
   * port.block // 8080
   *
   * Async.attempt("80a".toInt).either.block
   * // Left(java.lang.NumberFormatException: For input string: "80a")
   * }}}
   */
  inline def attempt[A](inline body: A): Async[A] =
    try Async.succeed(body)
    catch { case t: Throwable => Async.fail(t) }

  /**
   * Writes asynchronous code in direct style: inside `body`, `fa.await` waits
   * for an `Async` and returns its value, and the value of `body` becomes the
   * result. The block is rewritten at compile time into the `flatMap` chain you
   * would write by hand, so awaits run in source order and no thread blocks. A
   * throw in `body`, or an `await` on a failed `Async`, fails the result.
   * `await` also works inside closures passed to the strict collections, such
   * as `List.map`, which then run one element at a time.
   *
   * Like every `Async`, the block runs at once up to its first `await` on a
   * pending value; awaits on ready values do not stop it. What happens after a
   * pending `await` depends on the platform. On the JVM, on Scala 2, and on
   * Scala.js before Scala 3.8, the rest of the block runs only when a driver
   * (`block`, `start`, `toFuture`, ...) settles the result. On Scala.js with
   * Scala 3.8 or later, the block compiles to a JavaScript async function, so
   * the rest runs by itself once the awaited value completes; linking it needs
   * ES2017 output. The value delivered is the same everywhere.
   *
   * {{{
   * def fetchOrderTotal(orderId: Int): Async[Int] = Async.succeed(orderId * 100)
   * def fetchDiscount(userId: Int): Async[Int] = Async.succeed(userId % 10)
   *
   * val toPay: Async[Int] = Async.async {
   *   val total = fetchOrderTotal(12).await
   *   val discount = fetchDiscount(7).await
   *   total - discount
   * }
   * toPay.block // 1193
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/async/#direct-style Direct Style]]
   */
  transparent inline def async[A](inline body: A): Async[A] =
    ${ zio.blocks.async.internal.AsyncDirect.asyncImpl[A]('body) }
}
