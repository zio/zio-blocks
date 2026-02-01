package zio.blocks.combinators

import scala.quoted.*

/**
 * Combines two structural types A and B into their intersection type A & B at
 * compile time.
 *
 * The `StructuralCombiner` uses Scala 3's macro system to create a wrapper
 * object that implements both structural types. It performs compile-time
 * validation to ensure there are no conflicting member names between the two
 * types.
 *
 * JVM-only restriction: This combiner uses Java reflection via `Selectable`,
 * which is only available on the JVM platform. It will not compile on Scala.js
 * or Scala Native.
 *
 * @example
 *   {{{
 * type HasName = { def name: String }
 * type HasAge = { def age: Int }
 *
 * val a: HasName = new { def name = "Alice" }
 * val b: HasAge = new { def age = 30 }
 *
 * val combined: HasName & HasAge = StructuralCombiner.combine(a, b)
 * combined.name // "Alice"
 * combined.age  // 30
 *   }}}
 */
object StructuralCombiner {

  /**
   * Combines two structural types into their intersection.
   *
   * Compile-time errors occur if:
   *   - The two types have conflicting member names
   *
   * @tparam A
   *   The first structural type
   * @tparam B
   *   The second structural type
   * @param a
   *   The first value
   * @param b
   *   The second value
   * @return
   *   A value of type A & B that delegates members to a or b
   */
  inline def combine[A, B](a: A, b: B): A & B = ${ combineImpl[A, B]('a, 'b) }

  private def combineImpl[A: Type, B: Type](a: Expr[A], b: Expr[B])(using Quotes): Expr[A & B] = {
    import quotes.reflect.*

    val tpeA = TypeRepr.of[A]
    val tpeB = TypeRepr.of[B]

    val membersA = extractMemberNames(tpeA)
    val membersB = extractMemberNames(tpeB)

    val conflicts = membersA.toSet.intersect(membersB.toSet)

    if (conflicts.nonEmpty) {
      report.errorAndAbort(
        s"StructuralCombiner: Conflicting members found in types A and B: ${conflicts.mkString(", ")}. " +
          "Cannot combine structural types with overlapping member names."
      )
    }

    generateCombinedWrapper[A, B](a, b, membersA, membersB)
  }

  private def extractMemberNames(using Quotes)(tpe: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*

    def collectRefinements(t: TypeRepr, acc: List[String]): List[String] =
      t match {
        case Refinement(parent, name, info) =>
          val shouldInclude = info match {
            case _: TypeBounds => false
            case _             => true
          }
          val newAcc = if (shouldInclude) name :: acc else acc
          collectRefinements(parent, newAcc)

        case AndType(left, right) =>
          collectRefinements(left, collectRefinements(right, acc))

        case _ =>
          acc
      }

    collectRefinements(tpe, Nil)
  }

  private def generateCombinedWrapper[A: Type, B: Type](using
    Quotes
  )(
    a: Expr[A],
    b: Expr[B],
    membersA: List[String],
    membersB: List[String]
  ): Expr[A & B] = {
    val allMembers = membersA ++ membersB

    if (allMembers.isEmpty) {
      '{ $a.asInstanceOf[A & B] }
    } else {
      '{
        val wrapper = new StructuralWrapper($a, $b, ${ Expr(membersA) }, ${ Expr(membersB) })
        wrapper.asInstanceOf[A & B]
      }
    }
  }

  final class StructuralWrapper[A, B](
    val valueA: A,
    val valueB: B,
    val membersA: List[String],
    val membersB: List[String]
  ) extends Selectable {
    def selectDynamic(name: String): Any =
      if (membersA.contains(name))
        invokeReflectively(valueA, name)
      else if (membersB.contains(name))
        invokeReflectively(valueB, name)
      else
        throw new NoSuchMethodException(s"No member named $name")

    private def invokeReflectively(target: Any, methodName: String): Any = {
      val clazz  = target.getClass
      val method = clazz.getMethod(methodName)
      method.invoke(target)
    }
  }
}
