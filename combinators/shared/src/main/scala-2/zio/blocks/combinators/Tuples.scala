package zio.blocks.combinators

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object Tuples {

  trait Tuples[L, R] {
    type Out

    def combine(l: L, r: R): Out

    def separate(out: Out): (L, R)
  }

  object Tuples extends TuplesLowPriority1 {
    type WithOut[L, R, O] = Tuples[L, R] { type Out = O }

    implicit def leftUnit[A]: WithOut[Unit, A, A] =
      new Tuples[Unit, A] {
        type Out = A
        def combine(l: Unit, r: A): A   = r
        def separate(out: A): (Unit, A) = ((), out)
      }

    implicit def rightUnit[A]: WithOut[A, Unit, A] =
      new Tuples[A, Unit] {
        type Out = A
        def combine(l: A, r: Unit): A   = l
        def separate(out: A): (A, Unit) = (out, ())
      }
  }

  trait TuplesLowPriority1 {
    implicit def combineTuple[L, R]: Tuples[L, R] = macro TuplesMacros.tuplesImpl[L, R]
  }

  def combine[L, R](l: L, r: R)(implicit c: Tuples[L, R]): c.Out = c.combine(l, r)

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

    def tuplesImpl[L: c.WeakTypeTag, R: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
      import c.universe._

      val lType = weakTypeOf[L].dealias
      val rType = weakTypeOf[R].dealias

      val unitType = typeOf[Unit]

      if (lType =:= unitType) {
        q"""
          new _root_.zio.blocks.combinators.Tuples.Tuples[$lType, $rType] {
            type Out = $rType
            def combine(l: $lType, r: $rType): $rType = r
            def separate(out: $rType): ($lType, $rType) = ((), out)
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

        val sepLAccessors = (1 to arity).map(i => q"out.${TermName(s"_$i")}")
        val sepR          = q"out.${TermName(s"_$newArity")}"

        q"""
          new _root_.zio.blocks.combinators.Tuples.Tuples[$lType, $rType] {
            type Out = $outType
            def combine(l: $lType, r: $rType): $outType = (..$allArgs)
            def separate(out: $outType): ($lType, $rType) = ((..$sepLAccessors), $sepR)
          }
        """
      } else {
        val outType = appliedType(typeOf[(_, _)].typeConstructor, List(lType, rType))
        q"""
          new _root_.zio.blocks.combinators.Tuples.Tuples[$lType, $rType] {
            type Out = $outType
            def combine(l: $lType, r: $rType): $outType = (l, r)
            def separate(out: $outType): ($lType, $rType) = out
          }
        """
      }
    }
  }
}
