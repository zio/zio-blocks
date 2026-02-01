package zio.blocks.combinators

/**
 * Zips two values into a flattened tuple, potentially discarding Unit values.
 *
 * The `Zippable` typeclass is similar to `Combiner` but is unidirectional (no
 * separation). It provides flags to indicate when values are discarded:
 *   - Unit identity: `zip((), a)` returns `a` with `discardsLeft = true`
 *   - Tuple flattening: `zip((a, b), c)` returns `(a, b, c)`
 *
 * Scala 2 limitation: Maximum tuple arity is 22. Scala 3 has no arity limits.
 *
 * @tparam L
 *   The left input type
 * @tparam R
 *   The right input type
 *
 * @example
 *   {{{
 * val result: (Int, String) = Zippable.zip(1, "hello")
 * val discarded: String = Zippable.zip((), "hello") // discardsLeft = true
 *   }}}
 */
sealed trait Zippable[L, R] {
  type Out

  /**
   * Zips two values into a single output value.
   *
   * @param left
   *   The left value
   * @param right
   *   The right value
   * @return
   *   The zipped output
   */
  def zip(left: L, right: R): Out

  /**
   * Indicates whether the left value is discarded (e.g., Unit).
   */
  def discardsLeft: Boolean = false

  /**
   * Indicates whether the right value is discarded (e.g., Unit).
   */
  def discardsRight: Boolean = false
}

object Zippable extends ZippableLowPriority1 {

  /**
   * Type alias for a Zippable with a specific output type.
   */
  type WithOut[L, R, O] = Zippable[L, R] { type Out = O }

  implicit def leftUnit[A]: WithOut[Unit, A, A] =
    new Zippable[Unit, A] {
      type Out = A
      override val discardsLeft: Boolean = true
      def zip(left: Unit, right: A): A   = right
    }

  implicit def rightUnit[A]: WithOut[A, Unit, A] =
    new Zippable[A, Unit] {
      type Out = A
      override val discardsRight: Boolean = true
      def zip(left: A, right: Unit): A    = left
    }
}

trait ZippableLowPriority1 extends ZippableLowPriority2 {
  implicit def zip3[A, B, C]: Zippable.WithOut[(A, B), C, (A, B, C)] =
    new Zippable[(A, B), C] {
      type Out = (A, B, C)
      def zip(left: (A, B), right: C): (A, B, C) = (left._1, left._2, right)
    }

  implicit def zip4[A, B, C, D]: Zippable.WithOut[(A, B, C), D, (A, B, C, D)] =
    new Zippable[(A, B, C), D] {
      type Out = (A, B, C, D)
      def zip(left: (A, B, C), right: D): (A, B, C, D) = (left._1, left._2, left._3, right)
    }

  implicit def zip5[A, B, C, D, E]: Zippable.WithOut[(A, B, C, D), E, (A, B, C, D, E)] =
    new Zippable[(A, B, C, D), E] {
      type Out = (A, B, C, D, E)
      def zip(left: (A, B, C, D), right: E): (A, B, C, D, E) = (left._1, left._2, left._3, left._4, right)
    }

  implicit def zip6[A, B, C, D, E, F]: Zippable.WithOut[(A, B, C, D, E), F, (A, B, C, D, E, F)] =
    new Zippable[(A, B, C, D, E), F] {
      type Out = (A, B, C, D, E, F)
      def zip(left: (A, B, C, D, E), right: F): (A, B, C, D, E, F) =
        (left._1, left._2, left._3, left._4, left._5, right)
    }

  implicit def zip7[A, B, C, D, E, F, G]: Zippable.WithOut[(A, B, C, D, E, F), G, (A, B, C, D, E, F, G)] =
    new Zippable[(A, B, C, D, E, F), G] {
      type Out = (A, B, C, D, E, F, G)
      def zip(left: (A, B, C, D, E, F), right: G): (A, B, C, D, E, F, G) =
        (left._1, left._2, left._3, left._4, left._5, left._6, right)
    }

  implicit def zip8[A, B, C, D, E, F, G, H]: Zippable.WithOut[(A, B, C, D, E, F, G), H, (A, B, C, D, E, F, G, H)] =
    new Zippable[(A, B, C, D, E, F, G), H] {
      type Out = (A, B, C, D, E, F, G, H)
      def zip(left: (A, B, C, D, E, F, G), right: H): (A, B, C, D, E, F, G, H) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, right)
    }

  implicit def zip9[A, B, C, D, E, F, G, H, I]
    : Zippable.WithOut[(A, B, C, D, E, F, G, H), I, (A, B, C, D, E, F, G, H, I)] =
    new Zippable[(A, B, C, D, E, F, G, H), I] {
      type Out = (A, B, C, D, E, F, G, H, I)
      def zip(left: (A, B, C, D, E, F, G, H), right: I): (A, B, C, D, E, F, G, H, I) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, right)
    }

  implicit def zip10[A, B, C, D, E, F, G, H, I, J]
    : Zippable.WithOut[(A, B, C, D, E, F, G, H, I), J, (A, B, C, D, E, F, G, H, I, J)] =
    new Zippable[(A, B, C, D, E, F, G, H, I), J] {
      type Out = (A, B, C, D, E, F, G, H, I, J)
      def zip(left: (A, B, C, D, E, F, G, H, I), right: J): (A, B, C, D, E, F, G, H, I, J) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, left._9, right)
    }

  implicit def zip11[A, B, C, D, E, F, G, H, I, J, K]
    : Zippable.WithOut[(A, B, C, D, E, F, G, H, I, J), K, (A, B, C, D, E, F, G, H, I, J, K)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J), K] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K)
      def zip(left: (A, B, C, D, E, F, G, H, I, J), right: K): (A, B, C, D, E, F, G, H, I, J, K) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, left._9, left._10, right)
    }

  implicit def zip12[A, B, C, D, E, F, G, H, I, J, K, L]
    : Zippable.WithOut[(A, B, C, D, E, F, G, H, I, J, K), L, (A, B, C, D, E, F, G, H, I, J, K, L)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K), L] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L)
      def zip(left: (A, B, C, D, E, F, G, H, I, J, K), right: L): (A, B, C, D, E, F, G, H, I, J, K, L) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, left._9, left._10, left._11, right)
    }

  implicit def zip13[A, B, C, D, E, F, G, H, I, J, K, L, M]
    : Zippable.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L), M, (A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L), M] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M)
      def zip(left: (A, B, C, D, E, F, G, H, I, J, K, L), right: M): (A, B, C, D, E, F, G, H, I, J, K, L, M) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          right
        )
    }

  implicit def zip14[A, B, C, D, E, F, G, H, I, J, K, L, M, N]
    : Zippable.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M), N, (A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M), N] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
      def zip(left: (A, B, C, D, E, F, G, H, I, J, K, L, M), right: N): (A, B, C, D, E, F, G, H, I, J, K, L, M, N) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          right
        )
    }

  implicit def zip15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]
    : Zippable.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
        right: O
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          right
        )
    }

  implicit def zip16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]: Zippable.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
    P,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), P] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
        right: P
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          right
        )
    }

  implicit def zip17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]: Zippable.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
    Q,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Q] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
        right: Q
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          right
        )
    }

  implicit def zip18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]: Zippable.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
    R,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), R] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
        right: R
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) = (
        left._1,
        left._2,
        left._3,
        left._4,
        left._5,
        left._6,
        left._7,
        left._8,
        left._9,
        left._10,
        left._11,
        left._12,
        left._13,
        left._14,
        left._15,
        left._16,
        left._17,
        right
      )
    }

  implicit def zip19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]: Zippable.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
    S,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), S] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
        right: S
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) = (
        left._1,
        left._2,
        left._3,
        left._4,
        left._5,
        left._6,
        left._7,
        left._8,
        left._9,
        left._10,
        left._11,
        left._12,
        left._13,
        left._14,
        left._15,
        left._16,
        left._17,
        left._18,
        right
      )
    }

  implicit def zip20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]: Zippable.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
    T,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), T] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
        right: T
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) = (
        left._1,
        left._2,
        left._3,
        left._4,
        left._5,
        left._6,
        left._7,
        left._8,
        left._9,
        left._10,
        left._11,
        left._12,
        left._13,
        left._14,
        left._15,
        left._16,
        left._17,
        left._18,
        left._19,
        right
      )
    }

  implicit def zip21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]: Zippable.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
    U,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), U] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
        right: U
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) = (
        left._1,
        left._2,
        left._3,
        left._4,
        left._5,
        left._6,
        left._7,
        left._8,
        left._9,
        left._10,
        left._11,
        left._12,
        left._13,
        left._14,
        left._15,
        left._16,
        left._17,
        left._18,
        left._19,
        left._20,
        right
      )
    }

  implicit def zip22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V]: Zippable.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
    V,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), V] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
        right: V
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) = (
        left._1,
        left._2,
        left._3,
        left._4,
        left._5,
        left._6,
        left._7,
        left._8,
        left._9,
        left._10,
        left._11,
        left._12,
        left._13,
        left._14,
        left._15,
        left._16,
        left._17,
        left._18,
        left._19,
        left._20,
        left._21,
        right
      )
    }
}

trait ZippableLowPriority2 {
  implicit def zip2[A, B]: Zippable.WithOut[A, B, (A, B)] =
    new Zippable[A, B] {
      type Out = (A, B)
      def zip(left: A, right: B): (A, B) = (left, right)
    }
}
