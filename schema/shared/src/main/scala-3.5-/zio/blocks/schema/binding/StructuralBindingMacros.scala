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

package zio.blocks.schema.binding

import scala.quoted.*

/**
 * Version-specific macro support for structural type bindings.
 *
 * This version (Scala 3.3/3.4 LTS) does NOT support deriving Binding instances
 * for structural types because `Symbol.newClass` is marked as @experimental and
 * only available in Scala 3.5+.
 *
 * The `zio-blocks-schema` artifact is built with Scala 3.3, so it contains this
 * version whatever Scala version the user compiles with. Users of that artifact
 * should:
 *   - Use case classes instead of structural types for `Binding.of`
 *   - Use the `zio-blocks-next-schema` artifact (Scala 3.9 or later, with
 *     `-experimental`) to use structural types with `Binding.of`; upgrading the
 *     compiler alone does not help
 *   - Use `schema.structural` to get structural schemas from case class schemas
 */
private[binding] object StructuralBindingMacros {

  /**
   * A member of a structural type: its name, its type (a `TypeRepr`, typed
   * `Any`), its register kind and its position.
   */
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
