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

package zio.blocks.schema.migration

import scala.reflect.macros.blackbox

object SelectorMacro {

  def extractPathImpl[S, A](c: blackbox.Context)(selector: c.Expr[S => A]): c.Expr[DynamicOptic] = {
    import c.universe._

    def parseTree(tree: Tree): List[String] = tree match {
      case Function(_, body) =>
        parseTree(body)
      case Select(qualifier, name) =>
        parseTree(qualifier) :+ name.decodedName.toString
      case Apply(
            Select(qualifier, TermName("selectDynamic")),
            List(Literal(Constant(fieldName: String)))
          ) =>
        parseTree(qualifier) :+ fieldName
      case Ident(_) =>
        Nil
      case Typed(expr, _) =>
        parseTree(expr)
      case Block(_, expr) =>
        parseTree(expr)
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported selector syntax: $tree. Must be simple field access (e.g., _.foo.bar)"
        )
    }

    val pathList = parseTree(selector.tree)

    if (pathList.isEmpty) {
      c.abort(c.enclosingPosition, "Selector path cannot be empty")
    }

    def buildOptic(path: List[String]): Tree = path match {
      case head :: Nil =>
        q"zio.blocks.schema.migration.DynamicOptic.Field($head, None)"
      case head :: tail =>
        q"zio.blocks.schema.migration.DynamicOptic.Field($head, Some(${buildOptic(tail)}))"
      case Nil =>
        c.abort(c.enclosingPosition, "Unreachable")
    }

    c.Expr[DynamicOptic](buildOptic(pathList))
  }
}
