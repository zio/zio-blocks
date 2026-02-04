package zio.blocks.combinators

/**
 * Tuple operations: combining values into flat tuples and separating them.
 *
 * The `Tuples` module provides two complementary typeclasses:
 *   - `Combiner[L, R]`: Combines two values into a flattened output
 *   - `Separator[A]`: Separates a combined value back into its parts
 *
 * Key behaviors:
 *   - Unit identity: `combine((), a)` returns `a`
 *   - Tuple flattening: `combine((a, b), c)` returns `(a, b, c)`
 *
 * Scala 2 limitation: Maximum tuple arity is 22.
 *
 * @example
 *   {{{
 * import zio.blocks.combinators.Tuples._
 *
 * val combined: (Int, String, Boolean) = Combiner.combine((1, "a"), true)
 * val (left, right) = Separator.separate(combined)
 *   }}}
 */
object Tuples {

  /**
   * Combines two values into a single output value with tuple flattening.
   *
   * @tparam L
   *   The left input type
   * @tparam R
   *   The right input type
   */
  trait Combiner[L, R] {
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
  }

  /**
   * Separates a combined value back into its constituent parts.
   *
   * @tparam A
   *   The combined input type
   */
  trait Separator[A] {
    type Left
    type Right

    /**
     * Separates a combined value back into its constituent parts.
     *
     * @param a
     *   The combined value
     * @return
     *   A tuple of the original left and right values
     */
    def separate(a: A): (Left, Right)
  }

  object Combiner extends CombinerLowPriority1 {

    /**
     * Type alias for a Combiner with a specific output type.
     */
    type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

    implicit def leftUnit[A]: WithOut[Unit, A, A] =
      new Combiner[Unit, A] {
        type Out = A
        def combine(l: Unit, r: A): A = r
      }

    implicit def rightUnit[A]: WithOut[A, Unit, A] =
      new Combiner[A, Unit] {
        type Out = A
        def combine(l: A, r: Unit): A = l
      }
  }

  trait CombinerLowPriority1 extends CombinerLowPriority2 {
    implicit def combine3[A, B, C]: Combiner.WithOut[(A, B), C, (A, B, C)] =
      new Combiner[(A, B), C] {
        type Out = (A, B, C)
        def combine(l: (A, B), r: C): (A, B, C) = (l._1, l._2, r)
      }

    implicit def combine4[A, B, C, D]: Combiner.WithOut[(A, B, C), D, (A, B, C, D)] =
      new Combiner[(A, B, C), D] {
        type Out = (A, B, C, D)
        def combine(l: (A, B, C), r: D): (A, B, C, D) = (l._1, l._2, l._3, r)
      }

    implicit def combine5[A, B, C, D, E]: Combiner.WithOut[(A, B, C, D), E, (A, B, C, D, E)] =
      new Combiner[(A, B, C, D), E] {
        type Out = (A, B, C, D, E)
        def combine(l: (A, B, C, D), r: E): (A, B, C, D, E) = (l._1, l._2, l._3, l._4, r)
      }

    implicit def combine6[A, B, C, D, E, F]: Combiner.WithOut[(A, B, C, D, E), F, (A, B, C, D, E, F)] =
      new Combiner[(A, B, C, D, E), F] {
        type Out = (A, B, C, D, E, F)
        def combine(l: (A, B, C, D, E), r: F): (A, B, C, D, E, F) = (l._1, l._2, l._3, l._4, l._5, r)
      }

    implicit def combine7[A, B, C, D, E, F, G]: Combiner.WithOut[(A, B, C, D, E, F), G, (A, B, C, D, E, F, G)] =
      new Combiner[(A, B, C, D, E, F), G] {
        type Out = (A, B, C, D, E, F, G)
        def combine(l: (A, B, C, D, E, F), r: G): (A, B, C, D, E, F, G) = (l._1, l._2, l._3, l._4, l._5, l._6, r)
      }

    implicit def combine8[A, B, C, D, E, F, G, H]
      : Combiner.WithOut[(A, B, C, D, E, F, G), H, (A, B, C, D, E, F, G, H)] =
      new Combiner[(A, B, C, D, E, F, G), H] {
        type Out = (A, B, C, D, E, F, G, H)
        def combine(l: (A, B, C, D, E, F, G), r: H): (A, B, C, D, E, F, G, H) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r)
      }

    implicit def combine9[A, B, C, D, E, F, G, H, I]
      : Combiner.WithOut[(A, B, C, D, E, F, G, H), I, (A, B, C, D, E, F, G, H, I)] =
      new Combiner[(A, B, C, D, E, F, G, H), I] {
        type Out = (A, B, C, D, E, F, G, H, I)
        def combine(l: (A, B, C, D, E, F, G, H), r: I): (A, B, C, D, E, F, G, H, I) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r)
      }

    implicit def combine10[A, B, C, D, E, F, G, H, I, J]
      : Combiner.WithOut[(A, B, C, D, E, F, G, H, I), J, (A, B, C, D, E, F, G, H, I, J)] =
      new Combiner[(A, B, C, D, E, F, G, H, I), J] {
        type Out = (A, B, C, D, E, F, G, H, I, J)
        def combine(l: (A, B, C, D, E, F, G, H, I), r: J): (A, B, C, D, E, F, G, H, I, J) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r)
      }

    implicit def combine11[A, B, C, D, E, F, G, H, I, J, K]
      : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J), K, (A, B, C, D, E, F, G, H, I, J, K)] =
      new Combiner[(A, B, C, D, E, F, G, H, I, J), K] {
        type Out = (A, B, C, D, E, F, G, H, I, J, K)
        def combine(l: (A, B, C, D, E, F, G, H, I, J), r: K): (A, B, C, D, E, F, G, H, I, J, K) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, r)
      }

    implicit def combine12[A, B, C, D, E, F, G, H, I, J, K, L]
      : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K), L, (A, B, C, D, E, F, G, H, I, J, K, L)] =
      new Combiner[(A, B, C, D, E, F, G, H, I, J, K), L] {
        type Out = (A, B, C, D, E, F, G, H, I, J, K, L)
        def combine(l: (A, B, C, D, E, F, G, H, I, J, K), r: L): (A, B, C, D, E, F, G, H, I, J, K, L) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, r)
      }

    implicit def combine13[A, B, C, D, E, F, G, H, I, J, K, L, M]
      : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L), M, (A, B, C, D, E, F, G, H, I, J, K, L, M)] =
      new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L), M] {
        type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M)
        def combine(l: (A, B, C, D, E, F, G, H, I, J, K, L), r: M): (A, B, C, D, E, F, G, H, I, J, K, L, M) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, r)
      }

    implicit def combine14[A, B, C, D, E, F, G, H, I, J, K, L, M, N]
      : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M), N, (A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
      new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M), N] {
        type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
        def combine(l: (A, B, C, D, E, F, G, H, I, J, K, L, M), r: N): (A, B, C, D, E, F, G, H, I, J, K, L, M, N) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, r)
      }

    implicit def combine15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]
      : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
      new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O] {
        type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
        def combine(
          l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
          r: O
        ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) =
          (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, l._14, r)
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
      }
  }

  trait CombinerLowPriority2 {
    implicit def combine2[A, B]: Combiner.WithOut[A, B, (A, B)] =
      new Combiner[A, B] {
        type Out = (A, B)
        def combine(l: A, r: B): (A, B) = (l, r)
      }
  }

  object Separator extends SeparatorLowPriority1 {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    implicit def leftUnitSep[A]: WithTypes[A, Unit, A] =
      new Separator[A] {
        type Left  = Unit
        type Right = A
        def separate(a: A): (Unit, A) = ((), a)
      }

    implicit def rightUnitSep[A]: WithTypes[A, A, Unit] =
      new Separator[A] {
        type Left  = A
        type Right = Unit
        def separate(a: A): (A, Unit) = (a, ())
      }
  }

  trait SeparatorLowPriority1 extends SeparatorLowPriority2 {
    implicit def separate3[A, B, C]: Separator.WithTypes[(A, B, C), (A, B), C] =
      new Separator[(A, B, C)] {
        type Left  = (A, B)
        type Right = C
        def separate(a: (A, B, C)): ((A, B), C) = ((a._1, a._2), a._3)
      }

    implicit def separate4[A, B, C, D]: Separator.WithTypes[(A, B, C, D), (A, B, C), D] =
      new Separator[(A, B, C, D)] {
        type Left  = (A, B, C)
        type Right = D
        def separate(a: (A, B, C, D)): ((A, B, C), D) = ((a._1, a._2, a._3), a._4)
      }

    implicit def separate5[A, B, C, D, E]: Separator.WithTypes[(A, B, C, D, E), (A, B, C, D), E] =
      new Separator[(A, B, C, D, E)] {
        type Left  = (A, B, C, D)
        type Right = E
        def separate(a: (A, B, C, D, E)): ((A, B, C, D), E) = ((a._1, a._2, a._3, a._4), a._5)
      }

    implicit def separate6[A, B, C, D, E, F]: Separator.WithTypes[(A, B, C, D, E, F), (A, B, C, D, E), F] =
      new Separator[(A, B, C, D, E, F)] {
        type Left  = (A, B, C, D, E)
        type Right = F
        def separate(a: (A, B, C, D, E, F)): ((A, B, C, D, E), F) = ((a._1, a._2, a._3, a._4, a._5), a._6)
      }

    implicit def separate7[A, B, C, D, E, F, G]: Separator.WithTypes[(A, B, C, D, E, F, G), (A, B, C, D, E, F), G] =
      new Separator[(A, B, C, D, E, F, G)] {
        type Left  = (A, B, C, D, E, F)
        type Right = G
        def separate(a: (A, B, C, D, E, F, G)): ((A, B, C, D, E, F), G) = ((a._1, a._2, a._3, a._4, a._5, a._6), a._7)
      }

    implicit def separate8[A, B, C, D, E, F, G, H]
      : Separator.WithTypes[(A, B, C, D, E, F, G, H), (A, B, C, D, E, F, G), H] =
      new Separator[(A, B, C, D, E, F, G, H)] {
        type Left  = (A, B, C, D, E, F, G)
        type Right = H
        def separate(a: (A, B, C, D, E, F, G, H)): ((A, B, C, D, E, F, G), H) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7), a._8)
      }

    implicit def separate9[A, B, C, D, E, F, G, H, I]
      : Separator.WithTypes[(A, B, C, D, E, F, G, H, I), (A, B, C, D, E, F, G, H), I] =
      new Separator[(A, B, C, D, E, F, G, H, I)] {
        type Left  = (A, B, C, D, E, F, G, H)
        type Right = I
        def separate(a: (A, B, C, D, E, F, G, H, I)): ((A, B, C, D, E, F, G, H), I) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8), a._9)
      }

    implicit def separate10[A, B, C, D, E, F, G, H, I, J]
      : Separator.WithTypes[(A, B, C, D, E, F, G, H, I, J), (A, B, C, D, E, F, G, H, I), J] =
      new Separator[(A, B, C, D, E, F, G, H, I, J)] {
        type Left  = (A, B, C, D, E, F, G, H, I)
        type Right = J
        def separate(a: (A, B, C, D, E, F, G, H, I, J)): ((A, B, C, D, E, F, G, H, I), J) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9), a._10)
      }

    implicit def separate11[A, B, C, D, E, F, G, H, I, J, K]
      : Separator.WithTypes[(A, B, C, D, E, F, G, H, I, J, K), (A, B, C, D, E, F, G, H, I, J), K] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J)
        type Right = K
        def separate(a: (A, B, C, D, E, F, G, H, I, J, K)): ((A, B, C, D, E, F, G, H, I, J), K) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9, a._10), a._11)
      }

    implicit def separate12[A, B, C, D, E, F, G, H, I, J, K, L]
      : Separator.WithTypes[(A, B, C, D, E, F, G, H, I, J, K, L), (A, B, C, D, E, F, G, H, I, J, K), L] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K)
        type Right = L
        def separate(a: (A, B, C, D, E, F, G, H, I, J, K, L)): ((A, B, C, D, E, F, G, H, I, J, K), L) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9, a._10, a._11), a._12)
      }

    implicit def separate13[A, B, C, D, E, F, G, H, I, J, K, L, M]
      : Separator.WithTypes[(A, B, C, D, E, F, G, H, I, J, K, L, M), (A, B, C, D, E, F, G, H, I, J, K, L), M] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L)
        type Right = M
        def separate(a: (A, B, C, D, E, F, G, H, I, J, K, L, M)): ((A, B, C, D, E, F, G, H, I, J, K, L), M) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9, a._10, a._11, a._12), a._13)
      }

    implicit def separate14[A, B, C, D, E, F, G, H, I, J, K, L, M, N]
      : Separator.WithTypes[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), (A, B, C, D, E, F, G, H, I, J, K, L, M), N] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M)
        type Right = N
        def separate(a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N)): ((A, B, C, D, E, F, G, H, I, J, K, L, M), N) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9, a._10, a._11, a._12, a._13), a._14)
      }

    implicit def separate15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
      O
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
        type Right = O
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N), O) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9, a._10, a._11, a._12, a._13, a._14), a._15)
      }

    implicit def separate16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
      P
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
        type Right = P
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), P) =
          ((a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9, a._10, a._11, a._12, a._13, a._14, a._15), a._16)
      }

    implicit def separate17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
      Q
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
        type Right = Q
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Q) = (
          (a._1, a._2, a._3, a._4, a._5, a._6, a._7, a._8, a._9, a._10, a._11, a._12, a._13, a._14, a._15, a._16),
          a._17
        )
      }

    implicit def separate18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
      R
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
        type Right = R
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), R) = (
          (
            a._1,
            a._2,
            a._3,
            a._4,
            a._5,
            a._6,
            a._7,
            a._8,
            a._9,
            a._10,
            a._11,
            a._12,
            a._13,
            a._14,
            a._15,
            a._16,
            a._17
          ),
          a._18
        )
      }

    implicit def separate19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
      S
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
        type Right = S
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), S) = (
          (
            a._1,
            a._2,
            a._3,
            a._4,
            a._5,
            a._6,
            a._7,
            a._8,
            a._9,
            a._10,
            a._11,
            a._12,
            a._13,
            a._14,
            a._15,
            a._16,
            a._17,
            a._18
          ),
          a._19
        )
      }

    implicit def separate20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
      T
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
        type Right = T
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), T) = (
          (
            a._1,
            a._2,
            a._3,
            a._4,
            a._5,
            a._6,
            a._7,
            a._8,
            a._9,
            a._10,
            a._11,
            a._12,
            a._13,
            a._14,
            a._15,
            a._16,
            a._17,
            a._18,
            a._19
          ),
          a._20
        )
      }

    implicit def separate21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
      U
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
        type Right = U
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), U) = (
          (
            a._1,
            a._2,
            a._3,
            a._4,
            a._5,
            a._6,
            a._7,
            a._8,
            a._9,
            a._10,
            a._11,
            a._12,
            a._13,
            a._14,
            a._15,
            a._16,
            a._17,
            a._18,
            a._19,
            a._20
          ),
          a._21
        )
      }

    implicit def separate22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V]: Separator.WithTypes[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V),
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
      V
    ] =
      new Separator[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] {
        type Left  = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
        type Right = V
        def separate(
          a: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
        ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), V) = (
          (
            a._1,
            a._2,
            a._3,
            a._4,
            a._5,
            a._6,
            a._7,
            a._8,
            a._9,
            a._10,
            a._11,
            a._12,
            a._13,
            a._14,
            a._15,
            a._16,
            a._17,
            a._18,
            a._19,
            a._20,
            a._21
          ),
          a._22
        )
      }
  }

  trait SeparatorLowPriority2 {
    implicit def separate2[A, B]: Separator.WithTypes[(A, B), A, B] =
      new Separator[(A, B)] {
        type Left  = A
        type Right = B
        def separate(a: (A, B)): (A, B) = a
      }
  }

  def combine[L, R](l: L, r: R)(implicit c: Combiner[L, R]): c.Out = c.combine(l, r)

  def separate[A](a: A)(implicit s: Separator[A]): (s.Left, s.Right) = s.separate(a)
}
