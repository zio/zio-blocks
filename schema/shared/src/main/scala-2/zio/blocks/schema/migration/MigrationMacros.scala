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

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import zio.blocks.schema.DynamicOptic

object MigrationMacros {

  def select[A](f: A => Any): DynamicOptic = macro selectImpl

  def selectImpl(c: whitebox.Context)(f: c.Tree): c.Tree = {
    import c.universe._
    def extractPath(tree: Tree): List[String] = tree match {
      case Function(_, body)       => extractPath(body)
      case Select(qualifier, name) => extractPath(qualifier) :+ name.toString
      case _                       => Nil
    }
    val fields = extractPath(f)
    if (fields.isEmpty)
      c.abort(c.enclosingPosition, "select: expected a field selector like _.name")
    val root = q"_root_.zio.blocks.schema.DynamicOptic.root"
    fields.foldLeft(root: Tree) { (acc, name) =>
      q"$acc.field($name)"
    }
  }
}
