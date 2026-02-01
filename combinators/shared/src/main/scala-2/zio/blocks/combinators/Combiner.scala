package zio.blocks.combinators

/**
 * Combines two values into a flattened tuple with bidirectional support.
 *
 * The `Combiner` typeclass enables combining values `L` and `R` into an output
 * type `Out`, with the ability to separate them back. It handles:
 *   - Unit identity: `combine((), a)` returns `a`
 *   - Tuple flattening: `combine((a, b), c)` returns `(a, b, c)`
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
 * val combined: (Int, String, Boolean) = Combiner.combine((1, "a"), true)
 * val (tuple, bool) = Combiner.separate(combined)
 *   }}}
 */
sealed trait Combiner[L, R] {
  type Out

  /**
   * Combines two values into a single output value.
   *
   * @param l
   *   The left value
   * @param r
   *   The right value
   * @return
   *   The combined output
   */
  def combine(l: L, r: R): Out

  /**
   * Separates a combined value back into its constituent parts.
   *
   * @param out
   *   The combined value
   * @return
   *   A tuple of the original left and right values
   */
  def separate(out: Out): (L, R)
}

object Combiner extends CombinerLowPriority1 {

  /**
   * Type alias for a Combiner with a specific output type.
   */
  type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

  implicit def leftUnit[A]: WithOut[Unit, A, A] =
    new Combiner[Unit, A] {
      type Out = A
      def combine(l: Unit, r: A): A   = r
      def separate(out: A): (Unit, A) = ((), out)
    }

  implicit def rightUnit[A]: WithOut[A, Unit, A] =
    new Combiner[A, Unit] {
      type Out = A
      def combine(l: A, r: Unit): A   = l
      def separate(out: A): (A, Unit) = (out, ())
    }
}

trait CombinerLowPriority1 extends CombinerLowPriority2 {
  implicit def combine3[A, B, C]: Combiner.WithOut[(A, B), C, (A, B, C)] =
    new Combiner[(A, B), C] {
      type Out = (A, B, C)
      def combine(l: (A, B), r: C): (A, B, C)   = (l._1, l._2, r)
      def separate(out: (A, B, C)): ((A, B), C) = ((out._1, out._2), out._3)
    }

  implicit def combine4[A, B, C, D]: Combiner.WithOut[(A, B, C), D, (A, B, C, D)] =
    new Combiner[(A, B, C), D] {
      type Out = (A, B, C, D)
      def combine(l: (A, B, C), r: D): (A, B, C, D)   = (l._1, l._2, l._3, r)
      def separate(out: (A, B, C, D)): ((A, B, C), D) = ((out._1, out._2, out._3), out._4)
    }

  implicit def combine5[A, B, C, D, E]: Combiner.WithOut[(A, B, C, D), E, (A, B, C, D, E)] =
    new Combiner[(A, B, C, D), E] {
      type Out = (A, B, C, D, E)
      def combine(l: (A, B, C, D), r: E): (A, B, C, D, E)   = (l._1, l._2, l._3, l._4, r)
      def separate(out: (A, B, C, D, E)): ((A, B, C, D), E) = ((out._1, out._2, out._3, out._4), out._5)
    }

  implicit def combine6[A, B, C, D, E, F]: Combiner.WithOut[(A, B, C, D, E), F, (A, B, C, D, E, F)] =
    new Combiner[(A, B, C, D, E), F] {
      type Out = (A, B, C, D, E, F)
      def combine(l: (A, B, C, D, E), r: F): (A, B, C, D, E, F)   = (l._1, l._2, l._3, l._4, l._5, r)
      def separate(out: (A, B, C, D, E, F)): ((A, B, C, D, E), F) = ((out._1, out._2, out._3, out._4, out._5), out._6)
    }

  implicit def combine7[A, B, C, D, E, F, G]: Combiner.WithOut[(A, B, C, D, E, F), G, (A, B, C, D, E, F, G)] =
    new Combiner[(A, B, C, D, E, F), G] {
      type Out = (A, B, C, D, E, F, G)
      def combine(l: (A, B, C, D, E, F), r: G): (A, B, C, D, E, F, G)   = (l._1, l._2, l._3, l._4, l._5, l._6, r)
      def separate(out: (A, B, C, D, E, F, G)): ((A, B, C, D, E, F), G) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), out._7)
    }

  implicit def combine8[A, B, C, D, E, F, G, H]: Combiner.WithOut[(A, B, C, D, E, F, G), H, (A, B, C, D, E, F, G, H)] =
    new Combiner[(A, B, C, D, E, F, G), H] {
      type Out = (A, B, C, D, E, F, G, H)
      def combine(l: (A, B, C, D, E, F, G), r: H): (A, B, C, D, E, F, G, H) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r)
      def separate(out: (A, B, C, D, E, F, G, H)): ((A, B, C, D, E, F, G), H) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7), out._8)
    }

  implicit def combine9[A, B, C, D, E, F, G, H, I]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H), I, (A, B, C, D, E, F, G, H, I)] =
    new Combiner[(A, B, C, D, E, F, G, H), I] {
      type Out = (A, B, C, D, E, F, G, H, I)
      def combine(l: (A, B, C, D, E, F, G, H), r: I): (A, B, C, D, E, F, G, H, I) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r)
      def separate(out: (A, B, C, D, E, F, G, H, I)): ((A, B, C, D, E, F, G, H), I) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8), out._9)
    }

  implicit def combine10[A, B, C, D, E, F, G, H, I, J]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I), J, (A, B, C, D, E, F, G, H, I, J)] =
    new Combiner[(A, B, C, D, E, F, G, H, I), J] {
      type Out = (A, B, C, D, E, F, G, H, I, J)
      def combine(l: (A, B, C, D, E, F, G, H, I), r: J): (A, B, C, D, E, F, G, H, I, J) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r)
      def separate(out: (A, B, C, D, E, F, G, H, I, J)): ((A, B, C, D, E, F, G, H, I), J) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9), out._10)
    }

  implicit def combine11[A, B, C, D, E, F, G, H, I, J, K]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J), K, (A, B, C, D, E, F, G, H, I, J, K)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J), K] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K)
      def combine(l: (A, B, C, D, E, F, G, H, I, J), r: K): (A, B, C, D, E, F, G, H, I, J, K) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, r)
      def separate(out: (A, B, C, D, E, F, G, H, I, J, K)): ((A, B, C, D, E, F, G, H, I, J), K) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10), out._11)
    }

  implicit def combine12[A, B, C, D, E, F, G, H, I, J, K, L]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K), L, (A, B, C, D, E, F, G, H, I, J, K, L)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K), L] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L)
      def combine(l: (A, B, C, D, E, F, G, H, I, J, K), r: L): (A, B, C, D, E, F, G, H, I, J, K, L) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, r)
      def separate(out: (A, B, C, D, E, F, G, H, I, J, K, L)): ((A, B, C, D, E, F, G, H, I, J, K), L) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11), out._12)
    }

  implicit def combine13[A, B, C, D, E, F, G, H, I, J, K, L, M]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L), M, (A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L), M] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M)
      def combine(l: (A, B, C, D, E, F, G, H, I, J, K, L), r: M): (A, B, C, D, E, F, G, H, I, J, K, L, M) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, r)
      def separate(out: (A, B, C, D, E, F, G, H, I, J, K, L, M)): ((A, B, C, D, E, F, G, H, I, J, K, L), M) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12), out._13)
    }

  implicit def combine14[A, B, C, D, E, F, G, H, I, J, K, L, M, N]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M), N, (A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M), N] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
      def combine(l: (A, B, C, D, E, F, G, H, I, J, K, L, M), r: N): (A, B, C, D, E, F, G, H, I, J, K, L, M, N) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, r)
      def separate(out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N)): ((A, B, C, D, E, F, G, H, I, J, K, L, M), N) = (
        (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12, out._13),
        out._14
      )
    }

  implicit def combine15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
      def combine(l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N), r: O): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, l._14, r)
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N), O) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14
        ),
        out._15
      )
    }

  implicit def combine16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
    P,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), P] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
        r: P
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, l._14, l._15, r)
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), P) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14,
          out._15
        ),
        out._16
      )
    }

  implicit def combine17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
    Q,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Q] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
        r: Q
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, l._14, l._15, l._16, r)
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Q) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14,
          out._15,
          out._16
        ),
        out._17
      )
    }

  implicit def combine18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
    R,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), R] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
        r: R
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) = (
        l._1,
        l._2,
        l._3,
        l._4,
        l._5,
        l._6,
        l._7,
        l._8,
        l._9,
        l._10,
        l._11,
        l._12,
        l._13,
        l._14,
        l._15,
        l._16,
        l._17,
        r
      )
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), R) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14,
          out._15,
          out._16,
          out._17
        ),
        out._18
      )
    }

  implicit def combine19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
    S,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), S] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
        r: S
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) = (
        l._1,
        l._2,
        l._3,
        l._4,
        l._5,
        l._6,
        l._7,
        l._8,
        l._9,
        l._10,
        l._11,
        l._12,
        l._13,
        l._14,
        l._15,
        l._16,
        l._17,
        l._18,
        r
      )
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), S) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14,
          out._15,
          out._16,
          out._17,
          out._18
        ),
        out._19
      )
    }

  implicit def combine20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
    T,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), T] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
        r: T
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) = (
        l._1,
        l._2,
        l._3,
        l._4,
        l._5,
        l._6,
        l._7,
        l._8,
        l._9,
        l._10,
        l._11,
        l._12,
        l._13,
        l._14,
        l._15,
        l._16,
        l._17,
        l._18,
        l._19,
        r
      )
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), T) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14,
          out._15,
          out._16,
          out._17,
          out._18,
          out._19
        ),
        out._20
      )
    }

  implicit def combine21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
    U,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), U] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
        r: U
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) = (
        l._1,
        l._2,
        l._3,
        l._4,
        l._5,
        l._6,
        l._7,
        l._8,
        l._9,
        l._10,
        l._11,
        l._12,
        l._13,
        l._14,
        l._15,
        l._16,
        l._17,
        l._18,
        l._19,
        l._20,
        r
      )
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), U) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14,
          out._15,
          out._16,
          out._17,
          out._18,
          out._19,
          out._20
        ),
        out._21
      )
    }

  implicit def combine22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
    V,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), V] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
        r: V
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) = (
        l._1,
        l._2,
        l._3,
        l._4,
        l._5,
        l._6,
        l._7,
        l._8,
        l._9,
        l._10,
        l._11,
        l._12,
        l._13,
        l._14,
        l._15,
        l._16,
        l._17,
        l._18,
        l._19,
        l._20,
        l._21,
        r
      )
      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), V) = (
        (
          out._1,
          out._2,
          out._3,
          out._4,
          out._5,
          out._6,
          out._7,
          out._8,
          out._9,
          out._10,
          out._11,
          out._12,
          out._13,
          out._14,
          out._15,
          out._16,
          out._17,
          out._18,
          out._19,
          out._20,
          out._21
        ),
        out._22
      )
    }
}

trait CombinerLowPriority2 {
  implicit def combine2[A, B]: Combiner.WithOut[A, B, (A, B)] =
    new Combiner[A, B] {
      type Out = (A, B)
      def combine(l: A, r: B): (A, B)   = (l, r)
      def separate(out: (A, B)): (A, B) = out
    }
}
