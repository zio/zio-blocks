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

private[combinators] sealed trait LowerPriorityConcat {
  implicit def disjoint[L, R]: Concat.WithOut[L, R, Either[L, R]] = macro ConcatMacros.disjointImpl[L, R]
}

private[combinators] trait ConcatCompanionPlatform extends LowerPriorityConcat {
  implicit def derive[L, R]: Concat[L, R] = macro ConcatMacros.concatImpl[L, R]
}

private[combinators] object ConcatMacros {

  /**
   * Symbols that, when they appear as the (only) parent of a LUB, do not carry
   * user-meaningful information. When the meaningful-LUB heuristic filters
   * parents of a `RefinedType` LUB, parents whose symbol matches any of these
   * are dropped.
   */
  private val NoiseSymbolFullNames: Set[String] = Set(
    "scala.Any",
    "scala.AnyRef",
    "scala.AnyVal",
    "java.lang.Object",
    "scala.Product",
    "scala.Serializable",
    "java.io.Serializable",
    "java.lang.Comparable"
  )

  def disjointImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe._

    val lType = weakTypeOf[L].dealias
    val rType = weakTypeOf[R].dealias

    if (lType =:= rType || lType <:< rType || rType <:< lType)
      c.abort(c.enclosingPosition, s"Concat.disjoint requires disjoint types but $lType and $rType are related")

    // Also reject whenever the higher-priority `derive` can produce an
    // identity-like Concat via a meaningful common supertype (e.g. Dog & Cat
    // sharing sealed Animal). Otherwise both implicits would type-check and
    // implicit search would report an ambiguity instead of preferring derive.
    if (meaningfulLub(c)(lType, rType).isDefined)
      c.abort(
        c.enclosingPosition,
        s"Concat.disjoint requires disjoint types but $lType and $rType share a meaningful common supertype"
      )

    val outType = appliedType(typeOf[Either[_, _]].typeConstructor, List(lType, rType))

    q"""
      new _root_.zio.blocks.combinators.Concat[$lType, $rType] {
        type Out = $outType
        def isIdentityLike: _root_.scala.Boolean = false
        def left(l: $lType): $outType = _root_.scala.Left(l)
        def right(r: $rType): $outType = _root_.scala.Right(r)
      }
    """
  }

  def concatImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe._

    val lType = weakTypeOf[L].dealias
    val rType = weakTypeOf[R].dealias

    def identityInstance(outType: Type): Tree =
      q"""
        new _root_.zio.blocks.combinators.Concat[$lType, $rType] {
          type Out = $outType
          def isIdentityLike: _root_.scala.Boolean = true
          def left(l: $lType): $outType  = l: $outType
          def right(r: $rType): $outType = r: $outType
        }
      """

    if (lType =:= rType) identityInstance(lType)
    else if (lType <:< rType) identityInstance(rType)
    else if (rType <:< lType) identityInstance(lType)
    else
      meaningfulLub(c)(lType, rType) match {
        case Some(outType) => identityInstance(outType)
        case None          =>
          // Aborting rejects this candidate during implicit search so the
          // lower-priority `disjoint` implicit can be considered. Whether
          // search ultimately succeeds depends on the caller's expected type.
          c.abort(
            c.enclosingPosition,
            s"No unique meaningful common supertype for $lType and $rType; cannot derive identity-like Concat"
          )
      }
  }

  /**
   * Returns the meaningful common supertype of `lType` and `rType`, if any.
   *
   * Scala 2's `lub` often returns refinements such as `Animal with Product with
   * Serializable` for two case classes sharing a sealed parent. This helper
   * strips out "noise" parents (see [[NoiseSymbolFullNames]]) and:
   *
   *   - returns `Some(t)` when exactly one meaningful parent remains;
   *   - returns `None` when zero or multiple meaningful parents remain, leaving
   *     the disambiguation to the caller (which falls back to `Either[L, R]`
   *     via the lower-priority `disjoint` implicit).
   *
   * Returning `None` for the multiple-parent case is intentional: emitting an
   * intersection type like `Animal with Mammal` would not satisfy an explicit
   * request such as `Concat.WithOut[Dog, Cat, Animal]` anyway, so falling
   * through to `Either` keeps the behavior predictable.
   */
  private def meaningfulLub(c: whitebox.Context)(lType: c.Type, rType: c.Type): Option[c.Type] = {
    import c.universe._

    def normalize(t: Type): Type = t.dealias.widen

    def flattenRefinement(t: Type): List[Type] = normalize(t) match {
      case RefinedType(parents, _)      => parents.flatMap(flattenRefinement)
      case AnnotatedType(_, underlying) => flattenRefinement(underlying)
      case other                        => List(other)
    }

    def isNoise(t: Type): Boolean = {
      val sym = normalize(t).typeSymbol
      sym != NoSymbol && NoiseSymbolFullNames.contains(sym.fullName)
    }

    def distinctTypes(types: List[Type]): List[Type] =
      types.foldLeft(List.empty[Type]) { (acc, t) =>
        if (acc.exists(_ =:= t)) acc else acc :+ t
      }

    val rawLub = normalize(lub(List(lType, rType)))

    val candidates = distinctTypes(
      flattenRefinement(rawLub)
        .map(normalize)
        .filterNot(isNoise)
        .filter(t => lType <:< t && rType <:< t)
    )

    val mostSpecific = candidates.filterNot { t =>
      candidates.exists(u => !(u =:= t) && u <:< t)
    }

    mostSpecific match {
      case one :: Nil => Some(one)
      case _          => None
    }
  }

}
