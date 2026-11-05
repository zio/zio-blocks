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

package zio.blocks.combinators

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Rewrites a nested `Either` into one canonical, left-nested shape, and back.
 * `Either[Int, Either[String, Boolean]]` becomes
 * `Either[Either[Int, String], Boolean]`: the alternatives keep their order
 * from left to right, and only the nesting changes. Nested `Either`s on the
 * left side are rewritten the same way.
 *
 * Use it when the same alternatives arrive with different nesting and you need
 * one shape, to encode them or to match on them once. The `Eithers.Eithers`
 * type class does the work, and `Eithers.combine` is the shortcut. Both Scala
 * versions produce the same shape.
 *
 * {{{
 * val failure: Either[Int, Either[String, Boolean]] = Right(Left("timeout"))
 *
 * Eithers.combine(failure) // Left(Right(timeout))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/combinators/ Combinators]]
 */
object Eithers {

  /**
   * The type class behind `Eithers.combine`: `combine` reshapes an
   * `Either[L, R]` into the left-nested `Out`, and `separate` restores the
   * original nesting. The compiler derives an instance for any `L` and `R`.
   *
   * {{{
   * def roundTrip[L, R](e: Either[L, R])(implicit t: Eithers.Eithers[L, R]): Either[L, R] =
   *   t.separate(t.combine(e))
   *
   * val failure: Either[Int, Either[String, Boolean]] = Right(Left("timeout"))
   * roundTrip(failure) // Right(Left(timeout))
   * }}}
   *
   * @tparam L
   *   the left type of the input `Either`
   * @tparam R
   *   the right type of the input `Either`
   */
  trait Eithers[L, R] {

    /**
     * The left-nested form of `Either[L, R]`:
     * `Either[Either[Int, String], Boolean]` for `L = Int` and
     * `R = Either[String, Boolean]`, and `Either[L, R]` itself when neither
     * side is an `Either`.
     */
    type Out

    /**
     * Moves the value of `either` to the same alternative in the left-nested
     * shape: with `R = Either[String, Boolean]`, `Left(1)` becomes
     * `Left(Left(1))`, `Right(Left("timeout"))` becomes `Left(Right(timeout))`,
     * and `Right(Right(true))` becomes `Right(true)`.
     */
    def combine(either: Either[L, R]): Out

    /**
     * Moves a value of the left-nested shape back to the original nesting, so
     * `separate(combine(e))` is `e`.
     */
    def separate(out: Out): Either[L, R]
  }

  /**
   * Holds the derived `Eithers.Eithers` instance; `WithOut` names an instance
   * by its result type.
   */
  object Eithers {

    /**
     * An `Eithers.Eithers` instance whose result type is `O`, for a signature
     * that needs to name the result.
     */
    type WithOut[L, R, O] = Eithers[L, R] { type Out = O }

    /**
     * Derives the instance for `L` and `R` with a macro: `Out` nests every
     * non-`Either` alternative of `Either[L, R]`, from left to right, to the
     * left.
     */
    implicit def combineEither[L, R]: Eithers[L, R] = macro EithersMacros.eithersImpl[L, R]
  }

  /**
   * Reshapes `either` into its left-nested form with the derived
   * `Eithers.Eithers` instance.
   *
   * {{{
   * val ok: Either[Int, Either[String, Boolean]] = Right(Right(true))
   * Eithers.combine(ok) // Right(true)
   * }}}
   */
  def combine[L, R](either: Either[L, R])(implicit c: Eithers[L, R]): c.Out = c.combine(either)

  /** The Scala 2 macro that derives `Eithers.Eithers` instances. */
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

    def eithersImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val lType        = weakTypeOf[L].dealias
      val rType        = weakTypeOf[R].dealias
      val inputEither  = appliedType(typeOf[Either[_, _]].typeConstructor, List(lType, rType))
      val leaves       = collectLeaves(c)(inputEither)
      val outType      = buildLeftNested(c)(leaves)
      val combineBody  = generateCombineBody(c)(lType, rType)
      val separateBody = generateSeparateBody(c)(lType, rType)

      q"""
        new _root_.zio.blocks.combinators.Eithers.Eithers[$lType, $rType] {
          type Out = $outType
          def combine(either: Either[$lType, $rType]): $outType = $combineBody
          def separate(out: $outType): Either[$lType, $rType] = $separateBody
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
        val lLeaves   = collectLeaves(c)(lType)
        val rLeaves   = collectLeaves(c)(rType)
        val allLeaves = lLeaves ++ rLeaves

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
    }

    private def generateSeparateBody(c: whitebox.Context)(
      lType: c.universe.Type,
      rType: c.universe.Type
    ): c.Tree = {
      import c.universe._

      if (!isEither(c)(lType) && !isEither(c)(rType)) {
        q"out"
      } else {
        val lLeaves   = collectLeaves(c)(lType)
        val rLeaves   = collectLeaves(c)(rType)
        val allLeaves = lLeaves ++ rLeaves

        val caseDefs = allLeaves.zipWithIndex.map { case (_, idx) =>
          val pattern = buildLeftNestedPattern(c)(idx, allLeaves.length)

          val isFromLeft = idx < lLeaves.length
          val localIdx   = if (isFromLeft) idx else idx - lLeaves.length

          val result = if (isFromLeft) {
            buildRightNestedResult(c)(lType, localIdx, lLeaves.length, isLeft = true)
          } else {
            buildRightNestedResult(c)(rType, localIdx, rLeaves.length, isLeft = false)
          }

          cq"$pattern => $result"
        }

        q"out match { case ..$caseDefs }"
      }
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

    private def buildLeftNestedPattern(c: whitebox.Context)(
      leafIdx: Int,
      totalLeaves: Int
    ): c.Tree = {
      import c.universe._

      if (totalLeaves == 1) {
        pq"x"
      } else if (leafIdx == totalLeaves - 1) {
        pq"Right(x)"
      } else {
        buildLeftNestedPatternWrapper(c)(leafIdx, totalLeaves)
      }
    }

    private def buildLeftNestedPatternWrapper(c: whitebox.Context)(
      leafIdx: Int,
      totalLeaves: Int
    ): c.Tree = {
      import c.universe._

      def wrap(idx: Int, depth: Int): c.Tree =
        if (depth == 1) {
          if (idx == 0) pq"Left(x)" else pq"Right(x)"
        } else {
          if (idx == depth) {
            pq"Right(x)"
          } else {
            val inner = wrap(idx, depth - 1)
            pq"Left($inner)"
          }
        }

      wrap(leafIdx, totalLeaves - 1)
    }

    private def buildRightNestedResult(c: whitebox.Context)(
      tpe: c.universe.Type,
      leafIdx: Int,
      totalLeaves: Int,
      isLeft: Boolean
    ): c.Tree = {
      import c.universe._

      if (totalLeaves == 1) {
        if (isLeft) q"Left(x)" else q"Right(x)"
      } else {
        val innerResult = buildNestedResult(c)(tpe, leafIdx)
        if (isLeft) q"Left($innerResult)" else q"Right($innerResult)"
      }
    }

    private def buildNestedResult(c: whitebox.Context)(
      tpe: c.universe.Type,
      leafIdx: Int
    ): c.Tree = {
      import c.universe._

      if (!isEither(c)(tpe)) {
        q"x"
      } else {
        val (leftType, rightType) = eitherTypes(c)(tpe)
        val leftLeaves            = collectLeaves(c)(leftType)

        if (leafIdx < leftLeaves.length) {
          val inner = buildNestedResult(c)(leftType, leafIdx)
          q"Left($inner)"
        } else {
          val inner = buildNestedResult(c)(rightType, leafIdx - leftLeaves.length)
          q"Right($inner)"
        }
      }
    }
  }
}
