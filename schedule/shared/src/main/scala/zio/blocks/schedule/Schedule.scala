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

package zio.blocks.schedule

import scala.concurrent.duration.Duration

/**
 * A scheduling decision indicating whether evaluation should continue or
 * terminate.
 */
sealed trait Decision
object Decision {

  /**
   * Continue the schedule after waiting for `delay`.
   *
   * @param delay
   *   the amount of time an interpreter should wait before the next step
   */
  final case class Continue(delay: Duration) extends Decision

  /** The schedule has completed — interpreters should stop evaluating. */
  case object Done extends Decision
}

/**
 * A pure, declarative description of a recurring policy.
 *
 * `Schedule` is an AST only — values describe scheduling behavior but do not
 * execute it. An interpreter walks the nodes and, for each step, produces an
 * output together with a [[Decision]] indicating whether to continue.
 *
 * @tparam In
 *   Input consumed at each step (e.g. errors for retry, successes for repeat).
 * @tparam Out
 *   Output produced at each step (e.g. retry count, computed delay).
 */
sealed trait Schedule[-In, +Out] { self =>

  /** Transforms the output of this schedule. */
  def map[Out2](f: Out => Out2): Schedule[In, Out2] = Schedule.Map(self, f)

  /** Transforms the input of this schedule. */
  def contramap[In2](f: In2 => In): Schedule[In2, Out] = Schedule.Contramap(self, f)

  /** Replaces the output with a constant value. */
  def as[Out2](out2: Out2): Schedule[In, Out2] = map(_ => out2)

  /** Discards the output, producing `Unit`. */
  def unit: Schedule[In, Unit] = as(())

  /** Combines with `that` — both must continue; outputs are paired. */
  def both[In1 <: In, Out2](that: Schedule[In1, Out2]): Schedule[In1, (Out, Out2)] = Schedule.Both(self, that)

  /** Alias for [[both]]. */
  def &&[In1 <: In, Out2](that: Schedule[In1, Out2]): Schedule[In1, (Out, Out2)] = both(that)

  /** Combines with `that` — either may continue; outputs are paired. */
  def either[In1 <: In, Out2](that: Schedule[In1, Out2]): Schedule[In1, (Out, Out2)] = Schedule.Either(self, that)

  /** Alias for [[either]]. */
  def ||[In1 <: In, Out2](that: Schedule[In1, Out2]): Schedule[In1, (Out, Out2)] = either(that)

  /** Feeds the output of `that` into the input of this schedule. */
  def compose[In2](that: Schedule[In2, In]): Schedule[In2, Out] = Schedule.Compose(self, that)

  /** Runs this schedule until done, then switches to `that`. */
  def andThen[In1 <: In, Out2](that: Schedule[In1, Out2]): Schedule[In1, Out2] = Schedule.AndThen(self, that)

  /** Alias for [[both]]. */
  def zip[In1 <: In, Out2](that: Schedule[In1, Out2]): Schedule[In1, (Out, Out2)] = both(that)

  /** Adds a delay derived from the schedule output. */
  def addDelay(f: Out => Duration): Schedule[In, Out] = Schedule.Delayed(self, f)

  /** Applies full-range jitter (0.0 to 1.0) to delays. */
  def jittered: Schedule[In, Out] = jittered(0.0, 1.0)

  /** Applies jitter with custom bounds to delays. */
  def jittered(min: Double, max: Double): Schedule[In, Out] = Schedule.Jittered(self, min, max)

  /** Continues only while the input satisfies `f`. */
  def whileInput[In1 <: In](f: In1 => Boolean): Schedule[In1, Out] = Schedule.WhileInput(self, f)

  /** Continues only while the output satisfies `f`. */
  def whileOutput(f: Out => Boolean): Schedule[In, Out] = Schedule.WhileOutput(self, f)

  /** Revises both the output and the continuation decision after each step. */
  def reconsider[In1 <: In, Out2](f: (In1, Out, Decision) => (Out2, Decision)): Schedule[In1, Out2] =
    Schedule.Reconsider(self, f)
}

object Schedule {

  // --- Primitive case classes (all private[schedule]) ---

  private[schedule] final case class Identity[A]()                    extends Schedule[A, A]
  private[schedule] final case class Succeed[Out](value: Out)         extends Schedule[Any, Out]
  private[schedule] final case class Unfold[S](initial: S, f: S => S) extends Schedule[Any, S]
  private[schedule] final case class Map[In, Out, Out2](schedule: Schedule[In, Out], f: Out => Out2)
      extends Schedule[In, Out2]
  private[schedule] final case class Contramap[In, In2, Out](schedule: Schedule[In, Out], f: In2 => In)
      extends Schedule[In2, Out]
  private[schedule] final case class Both[In, Out1, Out2](left: Schedule[In, Out1], right: Schedule[In, Out2])
      extends Schedule[In, (Out1, Out2)]
  private[schedule] final case class Either[In, Out1, Out2](left: Schedule[In, Out1], right: Schedule[In, Out2])
      extends Schedule[In, (Out1, Out2)]
  private[schedule] final case class Compose[In, Mid, Out](outer: Schedule[Mid, Out], inner: Schedule[In, Mid])
      extends Schedule[In, Out]
  private[schedule] final case class AndThen[In, Out1, Out2](first: Schedule[In, Out1], second: Schedule[In, Out2])
      extends Schedule[In, Out2]
  private[schedule] final case class Delayed[In, Out](schedule: Schedule[In, Out], f: Out => Duration)
      extends Schedule[In, Out]
  private[schedule] final case class Jittered[In, Out](schedule: Schedule[In, Out], min: Double, max: Double)
      extends Schedule[In, Out]
  private[schedule] final case class WhileInput[In, Out](schedule: Schedule[In, Out], f: In => Boolean)
      extends Schedule[In, Out]
  private[schedule] final case class WhileOutput[In, Out](schedule: Schedule[In, Out], f: Out => Boolean)
      extends Schedule[In, Out]
  private[schedule] final case class Reconsider[In, Out, Out2](
    schedule: Schedule[In, Out],
    f: (In, Out, Decision) => (Out2, Decision)
  ) extends Schedule[In, Out2]

  // --- Factory methods ---

  /**
   * Passes input through as output. Identity element for
   * [[Schedule.compose compose]].
   */
  def identity[A]: Schedule[A, A] = Identity()

  /** Always continues, emitting a constant value. */
  def succeed[A](value: A): Schedule[Any, A] = Succeed(value)

  /**
   * Iterates a state function forever: `initial`, `f(initial)`,
   * `f(f(initial))`, etc.
   */
  def unfold[S](initial: S)(f: S => S): Schedule[Any, S] = Unfold(initial, f)

  /** Counts from 0 forever. */
  def forever: Schedule[Any, Long] = unfold(0L)(_ + 1L)

  /** Continues for `n` steps, emitting the step count. */
  def recurs(n: Long): Schedule[Any, Long] = forever.whileOutput(_ < n)

  /** Continues forever with a fixed delay between steps. */
  def spaced(duration: Duration): Schedule[Any, Long] = forever.addDelay(_ => duration)

  /** Exponential backoff: `base * factor^step`. */
  def exponential(base: Duration, factor: Double = 2.0): Schedule[Any, Duration] =
    forever.map(step => base * math.pow(factor, step.toDouble)).addDelay(d => d)

  /** Fibonacci backoff: `one`, `one`, `2*one`, `3*one`, `5*one`, etc. */
  def fibonacci(one: Duration): Schedule[Any, Duration] =
    unfold[(Duration, Duration)]((one, one)) { case (a, b) => (b, a + b) }.map(_._1).addDelay(d => d)
}
