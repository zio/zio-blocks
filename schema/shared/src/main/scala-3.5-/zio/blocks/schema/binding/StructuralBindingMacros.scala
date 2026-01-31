package zio.blocks.schema.binding

import scala.quoted.*

/**
 * Version-specific macro support for structural type bindings.
 *
 * This version (Scala 3.3/3.4 LTS) does NOT support deriving Binding instances
 * for structural types because `Symbol.newClass` is marked as @experimental and
 * only available in Scala 3.5+.
 *
 * Users on Scala 3.3 LTS should:
 *   - Use case classes instead of structural types for `Binding.of`
 *   - Upgrade to Scala 3.5+ to use structural types with `Binding.of`
 *   - Use `schema.structural` to get structural schemas from case class schemas
 */
private[binding] object StructuralBindingMacros {

  case class StructuralFieldInfo(name: String, memberTpe: Any, kind: String, index: Int)

  /**
   * Generates a compile-time error explaining that structural types require
   * Scala 3.5+ due to the @experimental Symbol.newClass API.
   */
  def generateAnonymousClassFactory[A: Type](
    fields: Seq[StructuralFieldInfo],
    fail: String => Nothing
  )(using Quotes): Expr[Array[Any] => A] =
    fail(
      s"""Binding.of for structural types requires Scala 3.5+.
         |
         |The macro API `Symbol.newClass` used to generate structural type
         |constructors is marked @experimental and unavailable in Scala 3.3 LTS.
         |
         |Alternatives:
         |  1. Use a case class instead:
         |       case class Person(name: String, age: Int)
         |       Binding.of[Person]  // works on all Scala 3 versions
         |
         |  2. Upgrade to Scala 3.5+ to use structural types:
         |       Binding.of[{ def name: String; def age: Int }]
         |
         |  3. Use Schema[A].structural for structural schema without Binding.of:
         |       given schema: Schema[Person] = Schema.derived
         |       val structuralSchema = schema.structural  // works on all Scala 3 versions""".stripMargin
    )
}
