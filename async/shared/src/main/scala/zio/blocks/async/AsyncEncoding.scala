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
 * Type-encoding holder for [[Async]]. The whole reason this trait exists is to
 * give us an abstract type member that can be ascribed to its bare type in the
 * `async` package object, hiding the `= Any` representation behind an
 * opaque-style interface that compiles identically under Scala 2.13 and Scala 3
 * — no opaque types, no version-specific type definitions.
 *
 * The single declared bound — `Async[+A] >: Pollable[A]` — is what lets a
 * [[Pollable]] flow into an `Async[A]` position without a cast at the type
 * level (e.g. `Completer.poll` returns `this`, callers write
 * `val a: Async[Int] = somePollable`). Raw `A` values are NOT a subtype, so
 * callers must enter the encoding through [[Async.succeed]].
 *
 * No methods live here: every operation is either an inline extension on
 * `Async[A]` (see `AsyncSyntaxVersionSpecific`) which folds the encoding via
 * `isInstanceOf[Pollable[?]]`, or a slow-path helper in [[Async.slowPath]].
 */
private[async] abstract class AsyncEncoding {
  type Async[+A] >: Pollable[A]
}

private[async] object AsyncEncoding {

  /**
   * Sentinel for a '''success value''' whose runtime type is itself a
   * [[Pollable]]: disambiguates "pollable-as-`A`" from "pollable-as-suspended
   * computation". Does not extend [[Pollable]] so the `isInstanceOf[Pollable]`
   * fast-path fold is unchanged on the common case (`succeed(42)`,
   * `map(_ + 1)`). Allocated only on the rare `succeed` / `map` /
   * `Completer.succeed` paths that store a [[Pollable]] as the success value.
   *
   * `depth` counts how many times this carrier has been wrapped (each
   * [[Async.succeed]] / [[liftSuccess]] on a pollable or an existing carrier
   * increments it). [[unwrapLayer]] decrements by one when supplying a value to
   * a user callback; at depth `1` the bare [[Pollable]] is exposed.
   */
  final case class WrappedPollable(value: Pollable[?], depth: Int)

  /** First [[Async.succeed]] of a user [[Pollable]] success value. */
  def wrap(p: Pollable[?]): WrappedPollable = WrappedPollable(p, 1)

  /** Nest one more [[Async.succeed]] around an existing carrier. */
  def nest(w: WrappedPollable): WrappedPollable = WrappedPollable(w.value, w.depth + 1)

  /**
   * Peel one wrap layer before a user-defined callback. Depth `1` exposes the
   * bare [[Pollable]]; depth `> 1` returns a shallower [[WrappedPollable]].
   */
  def unwrapLayer(any: Any): Any =
    any match {
      case WrappedPollable(v, d) if d > 1 => WrappedPollable(v, d - 1)
      case WrappedPollable(v, 1)          => v
      case other                          => other
    }

  /**
   * Value supplied to user-defined callbacks (`map` / `flatMap` / `zipWith` /
   * `tap` continuations, slow-path terminal polls, …): one [[unwrapLayer]] so
   * carriers never escape and nested `Async` layers peel one level at a time.
   */
  def deliverSuccess[A](any: Any): A =
    unwrapLayer(any).asInstanceOf[A]

  /**
   * Lift a success value into the [[Async]] encoding. [[Pollable]] values —
   * including [[Failure]] stored as a success value — are wrapped; re-wrapping
   * an existing carrier increments `depth`.
   */

  /** True when `any` is a bare suspended [[Pollable]] (not a ready carrier). */
  def isSuspended(any: Any): Boolean =
    any.isInstanceOf[Pollable[?]] && !any.isInstanceOf[Failure]
  def liftSuccess[A](a: A): Async[A] = {
    val any = a.asInstanceOf[Any]
    any match {
      case w: WrappedPollable               => nest(w).asInstanceOf[Async[A]]
      case p if p.isInstanceOf[Pollable[_]] =>
        wrap(p.asInstanceOf[Pollable[?]]).asInstanceOf[Async[A]]
      case _ => a.asInstanceOf[Async[A]]
    }
  }

  /**
   * The one and only encoding instance. Ascribed to the bare [[AsyncEncoding]]
   * type at the `val` site (in the package object) so the `= Any` rep stays
   * hidden from outside callers.
   */
  val Instance: AsyncEncoding = new AsyncEncoding {
    type Async[+A] = Any
  }
}
