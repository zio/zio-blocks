package zio.blocks.combinators

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Either operations: combining values into left-nested canonical form and
 * separating them.
 *
 * The `Eithers` module provides two complementary typeclasses:
 *   - `Combiner[L, R]`: Combines an Either[L, R] into a left-nested canonical
 *     form
 *   - `Separator[A]`: Separates a combined value by peeling the rightmost
 *     alternative
 *
 * Key behaviors:
 *   - Canonical form is left-nested: `Either[Either[Either[A, B], C], D]`
 *   - Combiner takes `Either[L, R]` as input to enable type inference
 *   - Separator peels the rightmost alternative, returning
 *     `Either[Left, Right]`
 *
 * @example
 *   {{{
 * import zio.blocks.combinators.Eithers._
 *
 * // Combine reassociates to left-nested form
 * val combined = Combiner.combine(Right(Right(true)): Either[Int, Either[String, Boolean]])
 * // Result: Right(true): Either[Either[Int, String], Boolean]
 *
 * // Separate peels the rightmost alternative
 * val separated = Separator.separate(Right(true): Either[Either[Int, String], Boolean])
 * // Result: Right(true): Either[Either[Int, String], Boolean]
 *   }}}
 */
object Eithers {

  /**
   * Combines an Either[L, R] into a left-nested canonical form.
   *
   * @tparam L
   *   The left input type
   * @tparam R
   *   The right input type
   */
  trait Combiner[L, R] {
    type Out

    /**
     * Combines an Either[L, R] into left-nested canonical form.
     *
     * @param either
     *   The Either value to canonicalize
     * @return
     *   The left-nested canonical form
     */
    def combine(either: Either[L, R]): Out
  }

  /**
   * Separates a combined value by peeling the rightmost alternative.
   *
   * @tparam A
   *   The combined input type
   */
  trait Separator[A] {
    type Left
    type Right

    /**
     * Separates a combined value by peeling the rightmost alternative.
     *
     * @param a
     *   The combined value
     * @return
     *   Either[Left, Right] where Right is the rightmost alternative
     */
    def separate(a: A): Either[Left, Right]
  }

  object Combiner {

    /**
     * Type alias for a Combiner with a specific output type.
     */
    type WithOut[L, R, O] = Combiner[L, R] { type Out = O }

    /**
     * Macro-based implicit for all Either combinations. Computes the
     * left-nested canonical form at compile time.
     */
    implicit def combineEither[L, R]: Combiner[L, R] = macro EithersMacros.combinerImpl[L, R]
  }

  object Separator {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    implicit def separateEither[A]: Separator[A] = macro EithersMacros.separatorImpl[A]
  }

  def combine[L, R](either: Either[L, R])(implicit c: Combiner[L, R]): c.Out = c.combine(either)
  def separate[A](a: A)(implicit s: Separator[A]): Either[s.Left, s.Right]   = s.separate(a)

  private[combinators] object EithersMacros {

    private def isEither(c: whitebox.Context)(tpe: c.universe.Type): Boolean = {
      val sym = tpe.dealias.typeSymbol
      sym.fullName == "scala.util.Either"
    }

    private def eitherTypes(c: whitebox.Context)(tpe: c.universe.Type): (c.universe.Type, c.universe.Type) = {
      val args = tpe.dealias.typeArgs
      (args(0), args(1))
    }

    private def collectLeaves(c: whitebox.Context)(tpe: c.universe.Type): List[c.universe.Type] =
      if (!isEither(c)(tpe)) {
        List(tpe)
      } else {
        val (left, right) = eitherTypes(c)(tpe)
        collectLeaves(c)(left) ++ collectLeaves(c)(right)
      }

    private def buildLeftNested(c: whitebox.Context)(types: List[c.universe.Type]): c.universe.Type = {
      import c.universe._
      types match {
        case Nil                     => c.abort(c.enclosingPosition, "Cannot build Either from empty list")
        case single :: Nil           => single
        case first :: second :: rest =>
          val initial = appliedType(typeOf[Either[_, _]].typeConstructor, List(first, second))
          rest.foldLeft(initial) { (acc, tpe) =>
            appliedType(typeOf[Either[_, _]].typeConstructor, List(acc, tpe))
          }
      }
    }

    def combinerImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val lType       = weakTypeOf[L].dealias
      val rType       = weakTypeOf[R].dealias
      val inputEither = appliedType(typeOf[Either[_, _]].typeConstructor, List(lType, rType))
      val leaves      = collectLeaves(c)(inputEither)
      val outType     = buildLeftNested(c)(leaves)
      val combineBody = generateCombineBody(c)(lType, rType)

      q"""
        new _root_.zio.blocks.combinators.Eithers.Combiner[$lType, $rType] {
          type Out = $outType
          def combine(either: Either[$lType, $rType]): $outType = $combineBody
        }
      """
    }

    private def generateCombineBody(c: whitebox.Context)(
      lType: c.universe.Type,
      rType: c.universe.Type
    ): c.Tree = {
      import c.universe._

      if (!isEither(c)(lType) && !isEither(c)(rType)) {
        q"either"
      } else {
        generateReassociation(c)(lType, rType)
      }
    }

    private def generateReassociation(c: whitebox.Context)(
      lType: c.universe.Type,
      rType: c.universe.Type
    ): c.Tree = {
      import c.universe._

      val lLeaves   = collectLeaves(c)(lType)
      val rLeaves   = collectLeaves(c)(rType)
      val allLeaves = lLeaves ++ rLeaves
      val cases     = generateCases(c)(lType, rType, allLeaves)

      q"$cases"
    }

    private def generateCases(c: whitebox.Context)(
      lType: c.universe.Type,
      rType: c.universe.Type,
      allLeaves: List[c.universe.Type]
    ): c.Tree = {
      import c.universe._

      val lLeaves = collectLeaves(c)(lType)
      val rLeaves = collectLeaves(c)(rType)

      val caseDefs = allLeaves.zipWithIndex.map { case (_, idx) =>
        val isFromLeft = idx < lLeaves.length
        val localIdx   = if (isFromLeft) idx else idx - lLeaves.length

        val pattern = if (isFromLeft) {
          buildExtractionPattern(c)(lType, localIdx, lLeaves.length, isLeft = true)
        } else {
          buildExtractionPattern(c)(rType, localIdx, rLeaves.length, isLeft = false)
        }

        val result = buildLeftNestedResult(c)(idx, allLeaves.length)

        cq"$pattern => $result"
      }

      q"either match { case ..$caseDefs }"
    }

    private def buildExtractionPattern(c: whitebox.Context)(
      tpe: c.universe.Type,
      leafIdx: Int,
      totalLeaves: Int,
      isLeft: Boolean
    ): c.Tree = {
      import c.universe._

      if (totalLeaves == 1) {
        if (isLeft) pq"Left(x)" else pq"Right(x)"
      } else {
        val innerPattern = buildNestedPattern(c)(tpe, leafIdx)
        if (isLeft) pq"Left($innerPattern)" else pq"Right($innerPattern)"
      }
    }

    private def buildNestedPattern(c: whitebox.Context)(
      tpe: c.universe.Type,
      leafIdx: Int
    ): c.Tree = {
      import c.universe._

      if (!isEither(c)(tpe)) {
        pq"x"
      } else {
        val (leftType, rightType) = eitherTypes(c)(tpe)
        val leftLeaves            = collectLeaves(c)(leftType)

        if (leafIdx < leftLeaves.length) {
          val inner = buildNestedPattern(c)(leftType, leafIdx)
          pq"Left($inner)"
        } else {
          val inner = buildNestedPattern(c)(rightType, leafIdx - leftLeaves.length)
          pq"Right($inner)"
        }
      }
    }

    private def buildLeftNestedResult(c: whitebox.Context)(
      leafIdx: Int,
      totalLeaves: Int
    ): c.Tree = {
      import c.universe._

      if (totalLeaves == 1) {
        q"x"
      } else if (leafIdx == totalLeaves - 1) {
        q"Right(x)"
      } else {
        buildLeftNestedWrapper(c)(leafIdx, totalLeaves)
      }
    }

    private def buildLeftNestedWrapper(c: whitebox.Context)(
      leafIdx: Int,
      totalLeaves: Int
    ): c.Tree = {
      import c.universe._

      def wrap(idx: Int, depth: Int): c.Tree =
        if (depth == 1) {
          if (idx == 0) q"Left(x)" else q"Right(x)"
        } else {
          if (idx == depth) {
            q"Right(x)"
          } else {
            val inner = wrap(idx, depth - 1)
            q"Left($inner)"
          }
        }

      wrap(leafIdx, totalLeaves - 1)
    }

    def separatorImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val aType = weakTypeOf[A].dealias

      if (isEither(c)(aType)) {
        val (lType, rType) = eitherTypes(c)(aType)
        val inputEither    = appliedType(typeOf[Either[_, _]].typeConstructor, List(lType, rType))
        val leaves         = collectLeaves(c)(inputEither)
        val canonicalType  = buildLeftNested(c)(leaves)

        if (isEither(c)(canonicalType)) {
          val (leftType, rightType) = eitherTypes(c)(canonicalType)

          if (!isEither(c)(lType) && !isEither(c)(rType)) {
            // Atomic: no canonicalization needed, identity
            q"""
              new _root_.zio.blocks.combinators.Eithers.Separator[$aType] {
                type Left = $leftType
                type Right = $rightType
                def separate(a: $aType): Either[$leftType, $rightType] = a
              }
            """
          } else {
            // Nested: needs canonicalization via combiner
            val combineBody = generateCombineBody(c)(lType, rType)
            q"""
              new _root_.zio.blocks.combinators.Eithers.Separator[$aType] {
                type Left = $leftType
                type Right = $rightType
                def separate(either: $aType): Either[$leftType, $rightType] = $combineBody.asInstanceOf[Either[$leftType, $rightType]]
              }
            """
          }
        } else {
          c.abort(c.enclosingPosition, s"Cannot separate type: canonical form is not an Either")
        }
      } else {
        c.abort(c.enclosingPosition, s"Cannot separate non-Either type: $aType")
      }
    }
  }
}
