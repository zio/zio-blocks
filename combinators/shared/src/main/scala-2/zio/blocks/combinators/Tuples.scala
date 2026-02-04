package zio.blocks.combinators

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

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

  trait CombinerLowPriority1 {
    implicit def combineTuple[L, R]: Combiner[L, R] = macro TuplesMacros.combinerImpl[L, R]
  }

  object Separator extends SeparatorLowPriority1 {

    /**
     * Type alias for a Separator with specific left and right types.
     */
    type WithTypes[A, L, R] = Separator[A] { type Left = L; type Right = R }

    implicit def separateTuple[A]: Separator[A] = macro TuplesMacros.separatorImpl[A]
  }

  trait SeparatorLowPriority1 extends SeparatorLowPriority2 {
    implicit def leftUnitSep[A]: Separator.WithTypes[A, Unit, A] =
      new Separator[A] {
        type Left  = Unit
        type Right = A
        def separate(a: A): (Unit, A) = ((), a)
      }

    implicit def rightUnitSep[A]: Separator.WithTypes[A, A, Unit] =
      new Separator[A] {
        type Left  = A
        type Right = Unit
        def separate(a: A): (A, Unit) = (a, ())
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

  private[combinators] object TuplesMacros {

    private def isTuple(c: whitebox.Context)(tpe: c.universe.Type): Boolean = {
      val sym = tpe.typeSymbol
      sym.fullName.startsWith("scala.Tuple") && sym.fullName.matches("scala\\.Tuple[0-9]+")
    }

    private def tupleArity(c: whitebox.Context)(tpe: c.universe.Type): Int = {
      val name = tpe.typeSymbol.fullName
      name.stripPrefix("scala.Tuple").toInt
    }

    private def tupleElements(c: whitebox.Context)(tpe: c.universe.Type): List[c.universe.Type] =
      tpe.dealias.typeArgs

    def combinerImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val lType = weakTypeOf[L].dealias
      val rType = weakTypeOf[R].dealias

      val unitType = typeOf[Unit]

      if (lType =:= unitType) {
        q"""
          new _root_.zio.blocks.combinators.Tuples.Combiner[$lType, $rType] {
            type Out = $rType
            def combine(l: $lType, r: $rType): $rType = r
          }
        """
      } else if (isTuple(c)(lType)) {
        val arity    = tupleArity(c)(lType)
        val elements = tupleElements(c)(lType)

        if (arity >= 22) {
          c.abort(c.enclosingPosition, s"Cannot combine Tuple$arity with another element: would exceed Tuple22 limit")
        }

        val newArity   = arity + 1
        val newTypes   = elements :+ rType
        val outType    = appliedType(symbolOf[(_, _)].owner.info.member(TypeName(s"Tuple$newArity")).asType, newTypes)
        val lAccessors = (1 to arity).map(i => q"l.${TermName(s"_$i")}")
        val allArgs    = lAccessors :+ q"r"

        q"""
          new _root_.zio.blocks.combinators.Tuples.Combiner[$lType, $rType] {
            type Out = $outType
            def combine(l: $lType, r: $rType): $outType = (..$allArgs)
          }
        """
      } else {
        val outType = appliedType(typeOf[(_, _)].typeConstructor, List(lType, rType))
        q"""
          new _root_.zio.blocks.combinators.Tuples.Combiner[$lType, $rType] {
            type Out = $outType
            def combine(l: $lType, r: $rType): $outType = (l, r)
          }
        """
      }
    }

    def separatorImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val aType = weakTypeOf[A].dealias

      if (isTuple(c)(aType)) {
        val arity    = tupleArity(c)(aType)
        val elements = tupleElements(c)(aType)

        if (arity == 2) {
          val leftType  = elements(0)
          val rightType = elements(1)
          q"""
            new _root_.zio.blocks.combinators.Tuples.Separator[$aType] {
              type Left = $leftType
              type Right = $rightType
              def separate(a: $aType): ($leftType, $rightType) = a
            }
          """
        } else {
          val leftTypes  = elements.init
          val rightType  = elements.last
          val leftArity  = arity - 1
          val leftType   = appliedType(symbolOf[(_, _)].owner.info.member(TypeName(s"Tuple$leftArity")).asType, leftTypes)
          val lAccessors = (1 until arity).map(i => q"a.${TermName(s"_$i")}")

          q"""
            new _root_.zio.blocks.combinators.Tuples.Separator[$aType] {
              type Left = $leftType
              type Right = $rightType
              def separate(a: $aType): ($leftType, $rightType) = ((..$lAccessors), a.${TermName(s"_$arity")})
            }
          """
        }
      } else {
        val unitType = typeOf[Unit]
        q"""
          new _root_.zio.blocks.combinators.Tuples.Separator[$aType] {
            type Left = $unitType
            type Right = $aType
            def separate(a: $aType): ($unitType, $aType) = ((), a)
          }
        """
      }
    }
  }
}
