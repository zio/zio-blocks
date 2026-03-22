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

package zio.blocks.docs

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait MdInterpolator {

  implicit class MdStringContext(val sc: StringContext) {
    def md(args: Any*): Doc = macro MdMacros.mdImpl
  }
}

private[docs] object MdMacros {

  def mdImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Doc] = {
    import c.universe._

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "Expected string literal parts")
        }
      case _ => c.abort(c.enclosingPosition, "Expected StringContext")
    }

    val testInput = parts.mkString("X")
    Parser.parse(testInput) match {
      case Left(err) => c.abort(c.enclosingPosition, s"Invalid markdown: ${err.message}")
      case Right(_)  =>
    }

    val processedArgs = args.map { argExpr =>
      val argType        = argExpr.actualType.widen
      val toMarkdownTc   = typeOf[ToMarkdown[_]].typeConstructor
      val toMarkdownType = appliedType(toMarkdownTc, argType)
      val instance       = c.inferImplicitValue(toMarkdownType, silent = true)
      if (instance == EmptyTree) {
        c.abort(argExpr.tree.pos, s"No ToMarkdown instance found for type $argType")
      }
      q"$instance.toMarkdown(${argExpr.tree})"
    }

    val scExpr   = c.Expr[StringContext](c.prefix.tree.asInstanceOf[Apply].args.head)
    val argsExpr = c.Expr[Seq[Inline]](q"_root_.scala.Seq(..$processedArgs)")
    reify(MdInterpolatorRuntime.parseAndBuild(scExpr.splice, argsExpr.splice))
  }
}
